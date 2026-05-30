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
 * SsoSettingsPageComponent - Testing Library style
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] - reset/apply settings: server model must replace route model, sort roles,
 *                      invert custom useJavaClass from useInlineGroovy, and rebuild SAML validators.
 *   Group 2 [Risk 3] - submit(): active SSO type controls which model branch is posted.
 *   Group 3 [Risk 2] - user actions: provider change and clear-roles must mark the page dirty.
 *
 * Confirmed bugs (it.failing - remove wrapper once fixed):
 *   (none currently identified)
 *
 * KEY contracts:
 *   - reset() GETs ../api/sso/settings and passes the response through applySSOSettings().
 *   - SAML required fields are: spEntityId, assertionUrl, idpEntityId, idpSignOnUrl,
 *     idpLogoutUrl, and idpPublicKey.
 *   - customModel.useJavaClass is derived as !customModel.useInlineGroovy.
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { provideHttpClient } from "@angular/common/http";
import { ActivatedRoute } from "@angular/router";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { MatButtonModule } from "@angular/material/button";
import { MatCardModule } from "@angular/material/card";
import { MatCheckboxModule } from "@angular/material/checkbox";
import { MatFormFieldModule } from "@angular/material/form-field";
import { MatIconModule } from "@angular/material/icon";
import { MatInputModule } from "@angular/material/input";
import { MatSelectModule } from "@angular/material/select";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { render, waitFor } from "@testing-library/angular";
import { http, HttpResponse } from "msw";
import { of } from "rxjs";

import { server } from "../../../../../../../../mocks/server";
import { PageHeaderService } from "../../../../page-header/page-header.service";
import { TopScrollService } from "../../../../top-scroll/top-scroll.service";
import {
   CustomSSOAttributesModel,
   OpenIdAttributesModel,
   SamlAttributesModel,
   SSOSettingsModel,
} from "../sso-settings-model";
import { SsoSettingsPageComponent, SSOType } from "./sso-settings-page.component";

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

const makeSamlModel = (overrides: Partial<SamlAttributesModel> = {}): SamlAttributesModel => ({
   spEntityId: "sp-entity",
   assertionUrl: "https://sp.example/assert",
   idpEntityId: "idp-entity",
   idpSignOnUrl: "https://idp.example/login",
   idpLogoutUrl: "https://idp.example/logout",
   idpPublicKey: "public-key",
   roleClaim: "roles",
   groupClaim: "groups",
   orgIDClaim: "org",
   ...overrides,
});

const makeOpenIdModel = (overrides: Partial<OpenIdAttributesModel> = {}): OpenIdAttributesModel => ({
   secretId: "secret-id",
   clientId: "client-id",
   clientSecret: "client-secret",
   scopes: "openid profile",
   issuer: "https://issuer.example",
   audience: "stylebi",
   tokenEndpoint: "https://issuer.example/token",
   authorizationEndpoint: "https://issuer.example/auth",
   jwksUri: "https://issuer.example/jwks",
   jwkCertificate: "",
   nameClaim: "name",
   roleClaim: "roles",
   groupClaim: "groups",
   orgIDClaim: "org",
   openIdPropertyProvider: "provider",
   openIdPostprocessor: "postprocessor",
   ...overrides,
});

const makeCustomModel = (
   overrides: Partial<CustomSSOAttributesModel> = {},
): CustomSSOAttributesModel => ({
   useJavaClass: true,
   javaClassName: "com.example.CustomSso",
   useInlineGroovy: false,
   inlineGroovyClass: "",
   ...overrides,
});

const makeSettingsModel = (
   overrides: Partial<SSOSettingsModel> = {},
): SSOSettingsModel => ({
   samlAttributesModel: makeSamlModel(),
   openIdAttributesModel: makeOpenIdModel(),
   customAttributesModel: makeCustomModel(),
   roles: [
      { name: "z-role", label: "Z Role" },
      { name: "a-role", label: "A Role" },
   ],
   selectedRoles: ["z-role"],
   activeFilterType: SSOType.SAML,
   logoutUrl: "https://app.example/logout",
   logoutPath: "/signed-out",
   fallbackLogin: true,
   ...overrides,
});

interface RenderOpts {
   routeModel?: SSOSettingsModel;
   resetModel?: SSOSettingsModel;
   multiTenant?: boolean;
   cloudSecrets?: boolean;
}

async function renderComponent(opts: RenderOpts = {}) {
   const routeModel = opts.routeModel ?? makeSettingsModel();
   const resetModel = opts.resetModel ?? routeModel;
   const scrollSpy = { scroll: vi.fn() };
   const pageHeaderSpy = { title: "" };

   server.use(
      http.get("*/api/em/navbar/isMultiTenant", () =>
         HttpResponse.json(opts.multiTenant ?? false),
      ),
      http.get("*/api/em/security/isCloudSecrets", () =>
         HttpResponse.json(opts.cloudSecrets ?? false),
      ),
      http.get("*/api/sso/settings", () => HttpResponse.json(resetModel)),
   );

   const result = await render(SsoSettingsPageComponent, {
      imports: [
         FormsModule,
         ReactiveFormsModule,
         NoopAnimationsModule,
         MatButtonModule,
         MatCardModule,
         MatCheckboxModule,
         MatFormFieldModule,
         MatIconModule,
         MatInputModule,
         MatSelectModule,
      ],
      providers: [
         provideHttpClient(),
         { provide: ActivatedRoute, useValue: { data: of({ model: routeModel }) } },
         { provide: TopScrollService, useValue: scrollSpy },
         { provide: PageHeaderService, useValue: pageHeaderSpy },
      ],
      schemas: [NO_ERRORS_SCHEMA],
   });

   const comp = result.fixture.componentInstance as SsoSettingsPageComponent;
   await waitFor(() => expect(comp.selection).toBe(resetModel.activeFilterType));
   await waitFor(() => expect(comp.samlForm).toBeTruthy());

   return { ...result, comp, scrollSpy, pageHeaderSpy };
}

// ---------------------------------------------------------------------------
// Group 1 [Risk 3] - reset/apply settings
// ---------------------------------------------------------------------------

describe("SsoSettingsPageComponent - reset/apply settings", () => {

   // Regression-sensitive: constructor first applies route data, then the multi-tenant
   // lookup calls reset(); the final visible state must come from the settings GET.
   it("should apply the reset model, sort roles, derive custom mode, and load platform flags", async () => {
      const routeModel = makeSettingsModel({
         activeFilterType: SSOType.NONE,
         roles: [{ name: "route-role", label: "Route Role" }],
      });
      const resetModel = makeSettingsModel({
         activeFilterType: SSOType.CUSTOM,
         customAttributesModel: makeCustomModel({
            useJavaClass: true,
            useInlineGroovy: true,
            inlineGroovyClass: "return user;",
         }),
         roles: [
            { name: "z-role", label: "Z Role" },
            { name: "a-role", label: "A Role" },
         ],
         selectedRoles: ["a-role"],
      });

      const { comp } = await renderComponent({
         routeModel,
         resetModel,
         multiTenant: true,
         cloudSecrets: true,
      });

      expect(comp.selection).toBe(SSOType.CUSTOM);
      expect(comp.isMultiTenant).toBe(true);
      expect(comp.cloudSecrets).toBe(true);
      expect(comp.roles.map(role => role.name)).toEqual(["a-role", "z-role"]);
      expect(comp.selectedRoles).toEqual(["a-role"]);
      expect(comp.customModel.useInlineGroovy).toBe(true);
      expect(comp.customModel.useJavaClass).toBe(false);
      expect(comp.changed).toBe(false);
   });

   // Regression-sensitive: required SAML controls gate Apply in the template; optional
   // claims must not inherit the required validator accidentally.
   it("should rebuild SAML validators and mark the page dirty when the SAML form changes", async () => {
      const { comp } = await renderComponent();

      comp.changed = false;
      comp.samlForm.controls["spEntityId"].setValue("");
      comp.samlForm.controls["roleClaim"].setValue("");

      expect(comp.samlForm.controls["spEntityId"].errors?.["required"]).toBeTruthy();
      expect(comp.samlForm.controls["roleClaim"].errors?.["required"]).toBeFalsy();
      expect(comp.changed).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 2 [Risk 3] - submit()
// ---------------------------------------------------------------------------

describe("SsoSettingsPageComponent - submit(): payload branch selection", () => {

   // Regression-sensitive: SAML submit must include the current reactive-form value plus
   // all cross-type settings. A stale form snapshot silently posts the previous IdP config.
   it("should post the current SAML form values and common settings", async () => {
      let capturedBody: any = null;
      server.use(
         http.post("*/api/sso/settings", async ({ request }) => {
            capturedBody = await request.json();
            return HttpResponse.json({});
         }),
      );

      const { comp } = await renderComponent();
      comp.selection = SSOType.SAML;
      comp.samlForm.patchValue({ spEntityId: "edited-sp", roleClaim: "edited-role" });
      comp.selectedRoles = ["a-role"];
      comp.logoutUrl = "https://edited.example/logout";
      comp.logoutPath = "/edited";
      comp.fallbackLogin = false;
      comp.changed = true;

      comp.submit();

      await waitFor(() => expect(capturedBody).not.toBeNull());
      expect(capturedBody).toEqual(expect.objectContaining({
         activeFilterType: SSOType.SAML,
         selectedRoles: ["a-role"],
         logoutUrl: "https://edited.example/logout",
         logoutPath: "/edited",
         fallbackLogin: false,
      }));
      expect(capturedBody.samlAttributesModel.spEntityId).toBe("edited-sp");
      expect(capturedBody.samlAttributesModel.roleClaim).toBe("edited-role");
      expect(capturedBody.openIdAttributesModel).toBeUndefined();
      expect(capturedBody.customAttributesModel).toBeUndefined();
      expect(comp.changed).toBe(false);
   });

   // Regression-sensitive: the active SSO type is the server-side discriminator.
   // Posting a sibling branch can enable the wrong authentication filter.
   it("should post only the active branch for NONE, OPENID, and CUSTOM selections", async () => {
      const capturedBodies: any[] = [];
      server.use(
         http.post("*/api/sso/settings", async ({ request }) => {
            capturedBodies.push(await request.json());
            return HttpResponse.json({});
         }),
      );

      const { comp } = await renderComponent();

      comp.selection = SSOType.NONE;
      comp.submit();

      comp.selection = SSOType.OPENID;
      comp.submit();

      comp.selection = SSOType.CUSTOM;
      comp.submit();

      await waitFor(() => expect(capturedBodies).toHaveLength(3));

      expect(capturedBodies[0]).toEqual(expect.objectContaining({ activeFilterType: SSOType.NONE }));
      expect(capturedBodies[0].samlAttributesModel).toBeUndefined();
      expect(capturedBodies[0].openIdAttributesModel).toBeUndefined();
      expect(capturedBodies[0].customAttributesModel).toBeUndefined();

      expect(capturedBodies[1].activeFilterType).toBe(SSOType.OPENID);
      expect(capturedBodies[1].openIdAttributesModel).toEqual(comp.openIdModel);
      expect(capturedBodies[1].samlAttributesModel).toBeUndefined();
      expect(capturedBodies[1].customAttributesModel).toBeUndefined();

      expect(capturedBodies[2].activeFilterType).toBe(SSOType.CUSTOM);
      expect(capturedBodies[2].customAttributesModel).toEqual(comp.customModel);
      expect(capturedBodies[2].samlAttributesModel).toBeUndefined();
      expect(capturedBodies[2].openIdAttributesModel).toBeUndefined();
   });
});

// ---------------------------------------------------------------------------
// Group 3 [Risk 2] - user actions
// ---------------------------------------------------------------------------

describe("SsoSettingsPageComponent - user action state changes", () => {

   // Regression-sensitive: switching provider clears logout fields; leaving stale logout
   // data attached to a different SSO provider would submit an inconsistent config.
   it("should scroll up, clear logout fields, and mark changed when provider selection changes", async () => {
      const { comp, scrollSpy } = await renderComponent();
      comp.logoutUrl = "https://old.example/logout";
      comp.logoutPath = "/old";
      comp.changed = false;

      comp.changeSelection();

      expect(scrollSpy.scroll).toHaveBeenCalledWith("up");
      expect(comp.logoutUrl).toBe("");
      expect(comp.logoutPath).toBe("");
      expect(comp.changed).toBe(true);
   });

   // Regression-sensitive: clearRoles must call deselect on every selected MatOption and
   // mark the page dirty; otherwise Apply stays disabled after the visible role list changes.
   it("should deselect all role options and mark changed when roles are cleared", async () => {
      const { comp } = await renderComponent();
      const first = { deselect: vi.fn() };
      const second = { deselect: vi.fn() };
      comp.roleSelectionRef = { options: [first, second] } as any;
      comp.changed = false;

      comp.clearRoles();

      expect(first.deselect).toHaveBeenCalledTimes(1);
      expect(second.deselect).toHaveBeenCalledTimes(1);
      expect(comp.changed).toBe(true);
   });
});
