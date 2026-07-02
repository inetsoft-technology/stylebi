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
 * PresentationNavViewComponent — Angular Testing Library + MSW
 *
 * Two describe blocks:
 *   1. "PresentationNavViewComponent" — general smoke test (component creation), merged from the
 *      former TestBed-based presentation-nav-view.component.spec.ts.
 *   2. "PresentationNavViewComponent — SECURITY: SecurityMswHandlers personas" — permission
 *      persona coverage.
 *
 * Component: Presentation settings tab bar; calls AuthorizationService.getPermissions(
 * "settings/presentation") and filters `links` down to `visibleLinks` (mechanism B, same
 * tab-filter pattern as ContentSettingsViewComponent). "Organization Settings" (key
 * org-settings) is For-Org-x (siteAdmin only); "Global Settings" (key settings) and "Themes"
 * (key themes) are For-Org-ok. `<a mat-tab-link>` sets role="tab", not "link".
 *
 * Testing strategy: only orgAdmin is tested per item.
 *
 * KEY CONTRACT — the "Global Settings"/"Settings" label rename is NOT purely an
 * isMultiTenant switch: renameOrgSettings() only preserves the "_#(js:Global Settings)" label
 * when "org-settings" is present in the (already permission-filtered) visibleLinks AND
 * isMultiTenant is true. Since orgAdmin never has the org-settings permission, "org-settings"
 * is filtered out before renameOrgSettings() ever runs, so for orgAdmin the tab is ALWAYS
 * relabeled to "_#(js:Settings)", regardless of isMultiTenant. This spec is intentionally
 * asserting the renamed label, not "_#(js:Global Settings)".
 *
 * NOTE on the authz child-key contract: same "layer an authz override on top of
 * SecurityMswHandlers" pattern used in monitoring-sidenav / content-settings-view.
 *
 * NOTE on negative assertions: `visibleLinks` starts as `undefined` (nothing renders) and is
 * only assigned once inside the isEnterprise -> authz -> isMultiTenant callback chain, so
 * (unlike ContentSettingsViewComponent) the initial state here is fail-closed, not fail-open.
 * That makes a blind `waitFor(() => queryByRole(...) === null)` pass vacuously at t=0, before
 * the three-level chain has actually resolved — which both proves nothing and leaves a dangling
 * subscription that fires an NG0205 ("Injector has already been destroyed") after the test's
 * fixture is torn down. Negative cases instead wait for a known-true tab to appear first, the
 * same anchor pattern used in monitoring-sidenav, which is safe here because (unlike
 * ContentSettingsViewComponent) no tab is present before resolution.
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { provideHttpClient } from "@angular/common/http";
import { provideRouter } from "@angular/router";
import { provideNoopAnimations } from "@angular/platform-browser/animations";
import { render, screen, waitFor } from "@testing-library/angular";
import { http, HttpResponse } from "msw";
import { of, config as rxjsConfig } from "rxjs";

import { server } from "@test-mocks/server";
import { SecurityMswHandlers } from "@test-mocks/handlers/security-permission.handlers";
import { PresentationNavViewComponent } from "./presentation-nav-view.component";
import { PageHeaderService } from "../../../page-header/page-header.service";
import { AppInfoService } from "../../../../../../shared/util/app-info.service";

// Templates use the raw "_#(...)" i18n placeholder syntax; no translation pipe runs in this
// test environment, so the accessible name is the untranslated placeholder text.

/** orgAdmin's fixed child-key permission map for the "settings/presentation" path. */
function useOrgAdminChildPermissions(): void {
   server.use(
      http.get("*/api/em/authz", () =>
         HttpResponse.json({
            permissions: {
               "org-settings": false,
               "settings": true,
               "themes": true,
            },
            labels: {},
            multiTenancyHiddenComponents: {},
         })
      )
   );
}

/** [accessible tab name, expected visible under orgAdmin] for all 3 presentation tabs. */
const ORG_ADMIN_ITEM_VISIBILITY: Array<[name: string, expectedVisible: boolean]> = [
   ["_#(js:Organization Settings)", false],
   // Relabeled from "_#(js:Global Settings)" — see the file-level KEY CONTRACT note above.
   ["_#(js:Settings)", true],
   ["_#(js:Themes)", true],
];

/** A known For-Org-ok tab used as a resolution anchor for negative assertions. */
const THEMES_TAB_NAME = "_#(js:Themes)";

async function renderComponent() {
   const appInfoMock = { isEnterprise: () => of(true) };

   return render(PresentationNavViewComponent, {
      providers: [
         provideHttpClient(),
         provideRouter([]),
         provideNoopAnimations(),
         { provide: PageHeaderService, useValue: { title: "" } },
         { provide: AppInfoService, useValue: appInfoMock },
      ],
      schemas: [NO_ERRORS_SCHEMA],
   });
}

// ─────────────────────────────────────────────────────────────────────────────
// General (non-security) smoke test — merged from the former
// presentation-nav-view.component.spec.ts (TestBed-based "should create" test).
// ─────────────────────────────────────────────────────────────────────────────

describe("PresentationNavViewComponent", () => {
   it("should create", async () => {
      const { fixture } = await renderComponent();

      expect(fixture.componentInstance).toBeTruthy();

      // Wait for the isEnterprise -> authz -> isMultiTenant chain to fully settle before the
      // test ends: an unresolved subscription that fires after this fixture is torn down
      // throws NG0205 ("Injector has already been destroyed") during a later test.
      await waitFor(() => expect(fixture.componentInstance.visibleLinks).toBeDefined());
   });
});

// ─────────────────────────────────────────────────────────────────────────────
// SECURITY: SecurityMswHandlers persona coverage.
// ─────────────────────────────────────────────────────────────────────────────

describe("PresentationNavViewComponent — SECURITY: SecurityMswHandlers personas", () => {

   describe("asOrgAdmin — item visibility boundary", () => {
      it.each(ORG_ADMIN_ITEM_VISIBILITY)("%s should be visible=%s", async (name, expectedVisible) => {
         server.use(...SecurityMswHandlers.asOrgAdmin());
         useOrgAdminChildPermissions();

         await renderComponent();

         if(expectedVisible) {
            expect(await screen.findByRole("tab", { name })).toBeTruthy();
         }
         else {
            // Wait for a known-visible tab to resolve first, confirming the full
            // isEnterprise -> authz -> isMultiTenant chain has settled, before asserting this
            // tab's continued absence.
            await screen.findByRole("tab", { name: THEMES_TAB_NAME });
            expect(screen.queryByRole("tab", { name })).toBeNull();
         }
      });
   });

   // asViewer — empty permissions map filters out every tab (visibleLinks becomes []).
   it("viewer sees no presentation tabs", async () => {
      server.use(...SecurityMswHandlers.asViewer());

      const { fixture } = await renderComponent();
      // Wait for the full chain to settle (visibleLinks becomes a real, empty array) rather
      // than just checking tab count, which would pass vacuously before resolution too — same
      // dangling-subscription hazard as the smoke test above.
      await waitFor(() => expect(fixture.componentInstance.visibleLinks).toBeDefined());

      expect(screen.queryAllByRole("tab")).toHaveLength(0);
   });

   // asAnonymous — authz 401; component must not crash. visibleLinks stays undefined (its
   // initial value), so no tabs ever render — fail-closed, unlike ContentSettingsViewComponent.
   it("anonymous authz 401 is handled without crashing and renders no tabs", async () => {
      server.use(...SecurityMswHandlers.asAnonymous());

      const capturedErrors: unknown[] = [];
      const previousOnUnhandledError = rxjsConfig.onUnhandledError;
      rxjsConfig.onUnhandledError = (err) => capturedErrors.push(err);

      try {
         await renderComponent();
         await waitFor(() => expect(capturedErrors.length).toBeGreaterThan(0));
      }
      finally {
         rxjsConfig.onUnhandledError = previousOnUnhandledError;
      }

      expect(screen.queryAllByRole("tab")).toHaveLength(0);
      expect(String((capturedErrors[0] as any)?.status)).toBe("401");
   });
});
