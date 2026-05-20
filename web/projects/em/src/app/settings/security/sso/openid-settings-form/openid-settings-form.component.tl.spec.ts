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
 * OpenidSettingsFormComponent - Testing Library style
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] - model input/output: form state must round-trip without transient UI
 *                      fields and without emitting after destroy.
 *   Group 2 [Risk 3] - loadDiscovery(): discovery response must populate endpoints and
 *                      supported autocomplete data; failures must show an error dialog.
 *   Group 3 [Risk 2] - scope editing: chip and autocomplete paths must update scopes and
 *                      clear their input layer.
 *
 * Confirmed bugs (it.failing - remove wrapper once fixed):
 *   (none currently identified)
 *
 * KEY contracts:
 *   - model getter omits discoveryUrl and scopeInput.
 *   - scopes are stored as a space-delimited string in OpenIdAttributesModel.
 *   - discovery uses fetch directly and runs Angular updates through NgZone.
 */

import { CommonModule } from "@angular/common";
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { MatDialog } from "@angular/material/dialog";
import { ReactiveFormsModule } from "@angular/forms";
import { render, waitFor } from "@testing-library/angular";

import { OpenIdAttributesModel } from "../sso-settings-model";
import { OpenidSettingsFormComponent } from "./openid-settings-form.component";

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

function makeModel(overrides: Partial<OpenIdAttributesModel> = {}): OpenIdAttributesModel {
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

// ---------------------------------------------------------------------------
// Render helper
// ---------------------------------------------------------------------------

async function renderComponent() {
   const dialogSpy = { open: jest.fn() };
   const result = await render(OpenidSettingsFormComponent, {
      imports: [CommonModule, ReactiveFormsModule],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [{ provide: MatDialog, useValue: dialogSpy }],
   });

   result.fixture.detectChanges();
   await result.fixture.whenStable();

   return {
      ...result,
      comp: result.fixture.componentInstance as OpenidSettingsFormComponent,
      dialogSpy,
   };
}

// ---------------------------------------------------------------------------
// Group 1 [Risk 3] - model input/output
// ---------------------------------------------------------------------------

describe("OpenidSettingsFormComponent - model input/output contract", () => {

   // Regression-sensitive: discoveryUrl and scopeInput are UI-only controls. If they leak into
   // the model getter, submit() posts fields the backend contract does not accept.
   it("should round-trip model fields while omitting discoveryUrl and scopeInput", async () => {
      const { comp } = await renderComponent();

      comp.model = makeModel({ scopes: "openid email profile" });
      comp.form.get("discoveryUrl").setValue("https://issuer.example/.well-known/openid-configuration");
      comp.form.get("scopeInput").setValue("temporary");

      expect(comp.selectedScopes).toEqual(["openid", "email", "profile"]);
      expect(comp.model.scopes).toBe("openid email profile");
      expect((comp.model as any).discoveryUrl).toBeUndefined();
      expect((comp.model as any).scopeInput).toBeUndefined();
   });

   // Regression-sensitive: parent pages enable Apply through modelChange; losing this async
   // emission makes OIDC edits appear in the form but never become saveable.
   it("should emit modelChange with the current model when a persisted form field changes", async () => {
      const { comp } = await renderComponent();
      comp.model = makeModel();
      const emitted: OpenIdAttributesModel[] = [];
      comp.modelChange.subscribe(model => emitted.push(model));

      comp.form.get("clientId").setValue("edited-client");

      await waitFor(() => expect(emitted.length).toBeGreaterThan(0));
      expect(emitted[emitted.length - 1].clientId).toBe("edited-client");
      expect(emitted[emitted.length - 1].scopes).toBe("openid profile");
   });

   // Regression-sensitive: valueChanges subscriptions use takeUntil(destroy$). If cleanup is
   // broken, destroyed forms can still mark the parent page dirty after navigation.
   it("should not emit modelChange after the component is destroyed", async () => {
      const { comp, fixture } = await renderComponent();
      comp.model = makeModel();
      const emitted: OpenIdAttributesModel[] = [];
      comp.modelChange.subscribe(model => emitted.push(model));

      fixture.destroy();
      comp.form.get("clientId").setValue("after-destroy");
      await Promise.resolve();

      expect(emitted).toHaveLength(0);
   });
});

// ---------------------------------------------------------------------------
// Group 2 [Risk 3] - loadDiscovery()
// ---------------------------------------------------------------------------

describe("OpenidSettingsFormComponent - loadDiscovery(): fetch and apply", () => {

   // Regression-sensitive: discovery is the bulk-fill path for critical OIDC endpoints. Missing
   // one endpoint silently posts an incomplete provider configuration.
   it("should fetch discovery data, populate endpoints, and update supported scopes/claims", async () => {
      const fetchSpy = jest.spyOn(global, "fetch").mockResolvedValue({
         json: () => Promise.resolve({
            issuer: "https://issuer.example",
            authorization_endpoint: "https://issuer.example/auth",
            token_endpoint: "https://issuer.example/token",
            jwks_uri: "https://issuer.example/jwks",
            scopes_supported: ["openid", "email"],
            claims_supported: ["name", "roles"],
         }),
      } as unknown as Response);
      const { comp } = await renderComponent();
      comp.form.get("discoveryUrl").setValue("https://issuer.example/.well-known/openid-configuration");

      comp.loadDiscovery();

      await waitFor(() =>
         expect(comp.form.get("issuer").value).toBe("https://issuer.example"),
      );
      expect(fetchSpy).toHaveBeenCalledWith("https://issuer.example/.well-known/openid-configuration");
      expect(comp.form.get("authorizationEndpoint").value).toBe("https://issuer.example/auth");
      expect(comp.form.get("tokenEndpoint").value).toBe("https://issuer.example/token");
      expect(comp.form.get("jwksUri").value).toBe("https://issuer.example/jwks");
      expect(comp.supportedScopes).toEqual(["openid", "email"]);
      expect(comp.supportedClaims).toEqual(["name", "roles"]);
      expect(comp.disableDiscoveryApply).toBe(true);

      fetchSpy.mockRestore();
   });

   // Regression-sensitive: network/CORS failures must surface as the specific discovery error,
   // not as a silent no-op that leaves users debugging blank endpoint fields.
   it("should open the discovery error dialog when fetch fails", async () => {
      const fetchSpy = jest.spyOn(global, "fetch").mockRejectedValue(new Error("cors"));
      const { comp, dialogSpy } = await renderComponent();
      comp.form.get("discoveryUrl").setValue("https://issuer.example/.well-known/openid-configuration");

      comp.loadDiscovery();

      await waitFor(() => expect(dialogSpy.open).toHaveBeenCalledWith(
         expect.anything(),
         expect.objectContaining({
            data: expect.objectContaining({
               title: "_#(js:Error)",
               content: "_#(js:openid.discovery.error)",
            }),
         }),
      ));

      fetchSpy.mockRestore();
   });

   // Regression-sensitive: editing the URL after a failed or completed discovery must re-enable
   // Apply so the user can retry without reloading the page.
   it("should re-enable discovery apply when discovery input changes", async () => {
      const { comp } = await renderComponent();
      comp.disableDiscoveryApply = true;

      comp.enableDiscoveryApply();

      expect(comp.disableDiscoveryApply).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 3 [Risk 2] - scope editing
// ---------------------------------------------------------------------------

describe("OpenidSettingsFormComponent - scope editing paths", () => {

   // Regression-sensitive: free-text chip entry must trim and clear both the chip input and the
   // reactive control, otherwise the next Enter can duplicate stale text.
   it("should add a trimmed scope from chip input, clear the input, and emit modelChange", async () => {
      const { comp } = await renderComponent();
      comp.model = makeModel({ scopes: "openid" });
      const chipInput = { clear: jest.fn() };
      const emitted: OpenIdAttributesModel[] = [];
      comp.modelChange.subscribe(model => emitted.push(model));

      comp.addScope({ value: " email ", chipInput } as any);

      expect(comp.selectedScopes).toEqual(["openid", "email"]);
      expect(chipInput.clear).toHaveBeenCalledTimes(1);
      expect(comp.form.get("scopeInput").value).toBeNull();
      expect(emitted[0].scopes).toBe("openid email");
   });

   // Regression-sensitive: autocomplete selection is a separate UI path and must clear the
   // native input as well as the form control.
   it("should add a scope from autocomplete and clear the native input", async () => {
      const { comp } = await renderComponent();
      comp.model = makeModel({ scopes: "openid" });
      comp.scopeInput = { nativeElement: { value: "ema" } } as any;

      comp.scopeSelected({ option: { viewValue: "email" } } as any);

      expect(comp.selectedScopes).toEqual(["openid", "email"]);
      expect(comp.scopeInput.nativeElement.value).toBe("");
      expect(comp.form.get("scopeInput").value).toBeNull();
   });

   // Regression-sensitive: claim autocomplete must remain case-insensitive and substring-based,
   // matching the scope filter behavior users see from discovery metadata.
   it("should filter supported claims case-insensitively", async () => {
      const { comp } = await renderComponent();
      const emissions: string[][] = [];
      comp.supportedClaims = ["email", "roles", "organization"];
      const sub = comp.filteredRoleClaims.subscribe(values => emissions.push(values));

      comp.form.get("roleClaim").setValue("RO");

      await waitFor(() => expect(emissions[emissions.length - 1]).toEqual(["roles"]));
      sub.unsubscribe();
   });
});
