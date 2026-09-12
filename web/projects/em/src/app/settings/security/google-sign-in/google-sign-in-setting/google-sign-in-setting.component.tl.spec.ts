/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

/**
 * GoogleSignInSettingComponent - Testing Library style
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] - model/change tracking: route data must create a clean baseline and
 *                      nested OpenID edits must mark the page dirty.
 *   Group 2 [Risk 3] - submit()/reset(): current model must be posted, then the server model
 *                      must replace local dirty state.
 *   Group 3 [Risk 2] - ngOnDestroy: route data subscription must be cleaned up on destroy.
 *
 * Confirmed bugs (it.failing - remove wrapper once fixed):
 *   (none currently identified)
 *
 * KEY contracts:
 *   - submit() POSTs to ../api/em/security/googleSignIn and then calls reset().
 *   - reset() GETs ../api/em/security/googleSignIn and clears changed through resetModel().
 *   - toggle() and modelChanged() both update changed via deep equality against omodel.
 */

import { provideHttpClient } from "@angular/common/http";
import { CommonModule } from "@angular/common";
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { ActivatedRoute } from "@angular/router";
import { render, waitFor } from "@testing-library/angular";
import { http, HttpResponse } from "msw";
import { Observable, of, Subject } from "rxjs";

import { server } from "@test-mocks/server";
import { PageHeaderService } from "../../../../page-header/page-header.service";
import { OpenIdAttributesModel } from "../../sso/sso-settings-model";
import { GoogleSignInModel } from "./google-sign-in-model";
import { GoogleSignInSettingComponent } from "./google-sign-in-setting.component";

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

function makeOpenIdModel(overrides: Partial<OpenIdAttributesModel> = {}): OpenIdAttributesModel {
   return {
      secretId: "secret-id",
      clientId: "client-id",
      clientSecret: "client-secret",
      scopes: "openid profile",
      issuer: "https://issuer.example",
      audience: "stylebi",
      tokenEndpoint: "https://issuer.example/token",
      authorizationEndpoint: "https://issuer.example/auth",
      jwksUri: "https://issuer.example/jwks",
      jwkCertificate: "certificate",
      nameClaim: "name",
      roleClaim: "roles",
      groupClaim: "groups",
      orgIDClaim: "org",
      openIdPropertyProvider: "provider",
      openIdPostprocessor: "postprocessor",
      ...overrides,
   };
}

function makeModel(overrides: Partial<GoogleSignInModel> = {}): GoogleSignInModel {
   return {
      enable: false,
      cloudSecrets: false,
      openIdAttributesModel: makeOpenIdModel(),
      ...overrides,
   };
}

interface RenderOpts {
   routeModel?: GoogleSignInModel;
   resetModel?: GoogleSignInModel;
   routeData$?: Observable<Record<"model", GoogleSignInModel>>;
}

async function renderComponent(opts: RenderOpts = {}) {
   const routeModel = opts.routeModel ?? makeModel();
   const resetModel = opts.resetModel ?? routeModel;
   const pageHeaderSpy = { title: "" };
   const routeData$ = opts.routeData$ ?? of({ model: routeModel });

   server.use(
      http.get("*/api/em/security/googleSignIn", () => HttpResponse.json(resetModel)),
   );

   const result = await render(GoogleSignInSettingComponent, {
      imports: [CommonModule],
      providers: [
         provideHttpClient(),
         { provide: ActivatedRoute, useValue: { data: routeData$ } },
         { provide: PageHeaderService, useValue: pageHeaderSpy },
      ],
      schemas: [NO_ERRORS_SCHEMA],
   });

   const comp = result.fixture.componentInstance as GoogleSignInSettingComponent;
   result.fixture.detectChanges();
   await result.fixture.whenStable();

   if(!opts.routeData$) {
      await waitFor(() => expect(comp.model).toBe(routeModel));
   }

   return { ...result, comp, pageHeaderSpy };
}

// ---------------------------------------------------------------------------
// Group 1 [Risk 3] - model/change tracking
// ---------------------------------------------------------------------------

describe("GoogleSignInSettingComponent - model and dirty-state tracking", () => {

   // Regression-sensitive: omodel must be a deep clone. If it aliases model, nested OpenID
   // edits look clean and Apply stays disabled.
   it("should initialize from route data, set the title, and mark nested OpenID edits dirty", async () => {
      const routeModel = makeModel({
         openIdAttributesModel: makeOpenIdModel({ clientId: "route-client" }),
      });
      const { comp, pageHeaderSpy } = await renderComponent({ routeModel });

      expect(pageHeaderSpy.title).toBe("_#(js:Security Settings:Sign In With Google)");
      expect(comp.omodel).toEqual(routeModel);
      expect(comp.omodel).not.toBe(comp.model);

      comp.modelChanged(makeOpenIdModel({ clientId: "edited-client" }));

      expect(comp.omodel.openIdAttributesModel.clientId).toBe("route-client");
      expect(comp.model.openIdAttributesModel.clientId).toBe("edited-client");
      expect(comp.changed).toBe(true);
   });

   // Regression-sensitive: toggling back to the original value must clear dirty state so
   // Apply/Reset do not remain enabled after a no-op edit.
   it("should update enable and dirty state when toggled on and back off", async () => {
      const { comp } = await renderComponent({ routeModel: makeModel({ enable: false }) });

      comp.toggle();
      expect(comp.model.enable).toBe(true);
      expect(comp.changed).toBe(true);

      comp.toggle();
      expect(comp.model.enable).toBe(false);
      expect(comp.changed).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 2 [Risk 3] - submit()/reset()
// ---------------------------------------------------------------------------

describe("GoogleSignInSettingComponent - submit and reset HTTP flow", () => {

   // Regression-sensitive: reset is the server-authoritative rollback path; it must replace
   // local dirty edits and clear changed.
   it("should replace the local model with the GET response and clear changed on reset", async () => {
      const routeModel = makeModel({
         enable: true,
         openIdAttributesModel: makeOpenIdModel({ clientId: "route-client" }),
      });
      const resetModel = makeModel({
         enable: false,
         openIdAttributesModel: makeOpenIdModel({ clientId: "server-client" }),
      });
      const { comp } = await renderComponent({ routeModel, resetModel });
      comp.modelChanged(makeOpenIdModel({ clientId: "dirty-client" }));
      expect(comp.changed).toBe(true);

      comp.reset();

      await waitFor(() => expect(comp.model.openIdAttributesModel.clientId).toBe("server-client"));
      expect(comp.model.enable).toBe(false);
      expect(comp.changed).toBe(false);
      expect(comp.omodel).toEqual(resetModel);
      expect(comp.omodel).not.toBe(comp.model);
   });

   // Regression-sensitive: submit must POST the edited model, not the original route model,
   // and then reset from the server so generated/normalized fields are reflected locally.
   it("should post the current model and then reset from the server model", async () => {
      let capturedBody: GoogleSignInModel | null = null;
      const routeModel = makeModel({ enable: false });
      const resetModel = makeModel({
         enable: true,
         openIdAttributesModel: makeOpenIdModel({ clientId: "server-client" }),
      });
      server.use(
         http.post("*/api/em/security/googleSignIn", async ({ request }) => {
            capturedBody = await request.json() as GoogleSignInModel;
            return HttpResponse.json({});
         }),
      );
      const { comp } = await renderComponent({ routeModel, resetModel });

      comp.toggle();
      comp.modelChanged(makeOpenIdModel({ clientId: "posted-client" }));
      comp.submit();

      await waitFor(() => expect(capturedBody).not.toBeNull());
      expect(capturedBody.enable).toBe(true);
      expect(capturedBody.openIdAttributesModel.clientId).toBe("posted-client");
      await waitFor(() => expect(comp.model.openIdAttributesModel.clientId).toBe("server-client"));
      expect(comp.changed).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 3 [Risk 2] - ngOnDestroy
// ---------------------------------------------------------------------------

describe("GoogleSignInSettingComponent - route subscription lifecycle", () => {

   // Regression-sensitive: destroyed settings pages must not be mutated by late resolver
   // emissions after navigation.
   it("should unsubscribe from route data on destroy", async () => {
      const routeData$ = new Subject<Record<"model", GoogleSignInModel>>();
      const { comp, fixture } = await renderComponent({ routeData$ });
      const firstModel = makeModel({
         openIdAttributesModel: makeOpenIdModel({ clientId: "first-client" }),
      });
      const lateModel = makeModel({
         openIdAttributesModel: makeOpenIdModel({ clientId: "late-client" }),
      });

      routeData$.next({ model: firstModel });
      await waitFor(() => expect(comp.model).toBe(firstModel));

      fixture.destroy();
      routeData$.next({ model: lateModel });

      expect(comp.model).toBe(firstModel);
      expect(comp.model.openIdAttributesModel.clientId).toBe("first-client");
   });
});
