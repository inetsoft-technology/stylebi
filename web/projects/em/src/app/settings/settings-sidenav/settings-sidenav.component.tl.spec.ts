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
 * SettingsSidenavComponent — Angular Testing Library + MSW
 *
 * Two describe blocks:
 *   1. "SettingsSidenavComponent" — general smoke test (component creation), merged from the
 *      former TestBed-based settings-sidenav.component.spec.ts.
 *   2. "SettingsSidenavComponent — SECURITY: SecurityMswHandlers personas" — permission
 *      persona coverage.
 *
 * Component: EM sidebar/nav-menu; calls AuthorizationService.getPermissions("settings") and
 * shows/hides menu items (<a mat-list-item>) based on the response — same mechanism (A) and
 * same shape as MonitoringSidenavComponent. Seven items total: General/Logging/All Properties
 * are For-Org-x (siteAdmin only); Security/Content/Schedule/Presentation are For-Org-ok (both
 * siteAdmin and orgAdmin). "Logging" and "All Properties" are two independent menu items
 * (`loggingVisible`/`propertiesVisible`), not one.
 *
 * Testing strategy: only orgAdmin is tested per item, same rationale as
 * monitoring-sidenav — one parameterized table whose rows mix For-Org-x/For-Org-ok proves the
 * `@if` binding in both directions without a separate siteAdmin pass.
 *
 * NOTE on the authz child-key contract: same "layer a child-key authz override on top of
 * SecurityMswHandlers" pattern used in monitoring-sidenav, since this component also requests
 * the parent path ("settings") rather than per-item paths.
 */

import { NO_ERRORS_SCHEMA, Component } from "@angular/core";
import { provideHttpClient } from "@angular/common/http";
import { provideRouter } from "@angular/router";
import { provideNoopAnimations } from "@angular/platform-browser/animations";
import { render, screen, waitFor } from "@testing-library/angular";
import { http, HttpResponse } from "msw";
import { config as rxjsConfig } from "rxjs";

import { server } from "@test-mocks/server";
import { SecurityMswHandlers } from "@test-mocks/handlers/security-permission.handlers";
import { SettingsSidenavComponent } from "./settings-sidenav.component";
import { PageHeaderComponent } from "../../page-header/page-header.component";

@Component({ selector: "em-page-header", template: "", standalone: true })
class MockPageHeaderComponent {}

// Templates use the raw "_#(...)" i18n placeholder syntax; no translation pipe runs in this
// test environment, so the accessible name is the untranslated placeholder text.

/** orgAdmin's fixed child-key permission map for the "settings" path. */
function useOrgAdminChildPermissions(): void {
   server.use(
      http.get("*/api/em/authz", () =>
         HttpResponse.json({
            permissions: {
               general: false, logging: false, properties: false,
               security: true, content: true, schedule: true, presentation: true,
            },
            labels: {},
            multiTenancyHiddenComponents: {},
         })
      )
   );
}

/** [accessible link name, expected visible under orgAdmin] for all 7 settings items. */
const ORG_ADMIN_ITEM_VISIBILITY: Array<[name: string, expectedVisible: boolean]> = [
   ["_#(General)", false],
   ["_#(Logging)", false],
   ["_#(All Properties)", false],
   ["_#(Security)", true],
   ["_#(Content)", true],
   ["_#(Schedule)", true],
   ["_#(Presentation)", true],
];

/** A known For-Org-ok item used as a resolution anchor for negative assertions. */
const CONTENT_LINK_NAME = "_#(Content)";

async function renderComponent() {
   return render(SettingsSidenavComponent, {
      importOverrides: [
         { replace: PageHeaderComponent, with: MockPageHeaderComponent },
      ],
      providers: [
         provideHttpClient(),
         provideRouter([]),
         provideNoopAnimations(),
      ],
      schemas: [NO_ERRORS_SCHEMA],
   });
}

// ─────────────────────────────────────────────────────────────────────────────
// General (non-security) smoke test — merged from the former
// settings-sidenav.component.spec.ts (TestBed-based "should create" test).
// ─────────────────────────────────────────────────────────────────────────────

describe("SettingsSidenavComponent", () => {
   it("should create", async () => {
      const { fixture } = await renderComponent();

      expect(fixture.componentInstance).toBeTruthy();
   });
});

// ─────────────────────────────────────────────────────────────────────────────
// SECURITY: SecurityMswHandlers persona coverage.
// ─────────────────────────────────────────────────────────────────────────────

describe("SettingsSidenavComponent — SECURITY: SecurityMswHandlers personas", () => {

   describe("asOrgAdmin — item visibility boundary", () => {
      it.each(ORG_ADMIN_ITEM_VISIBILITY)("%s should be visible=%s", async (name, expectedVisible) => {
         server.use(...SecurityMswHandlers.asOrgAdmin());
         useOrgAdminChildPermissions();

         await renderComponent();

         if(expectedVisible) {
            expect(await screen.findByRole("link", { name })).toBeTruthy();
         }
         else {
            // Wait for a known-visible item to resolve first, confirming the authz response has
            // settled, before asserting this item's continued absence.
            await screen.findByRole("link", { name: CONTENT_LINK_NAME });
            expect(screen.queryByRole("link", { name })).toBeNull();
         }
      });
   });

   // asOrgAdmin — navbar isOrgAdminOnly=true flag is served correctly.
   it("asOrgAdmin serves isOrgAdminOnly=true on the navbar endpoint", async () => {
      server.use(...SecurityMswHandlers.asOrgAdmin());
      useOrgAdminChildPermissions();

      const isOrgAdminOnly = await fetch("/api/em/navbar/isOrgAdminOnly").then(r => r.json());
      expect(isOrgAdminOnly).toBe(true);

      await renderComponent();
      expect(await screen.findByRole("link", { name: CONTENT_LINK_NAME })).toBeTruthy();
   });

   // asViewer — no admin menu entries at all.
   it("viewer sees no admin menu entries", async () => {
      server.use(...SecurityMswHandlers.asViewer());

      await renderComponent();
      await waitFor(() => expect(screen.queryByRole("link")).toBeNull());

      expect(screen.queryAllByRole("link")).toHaveLength(0);
   });

   // asAnonymous — authz returns 401; component must not crash and must show no menu items.
   // Same rxjs unhandled-error capture pattern as monitoring-sidenav: ngOnInit subscribes with
   // no error callback, so the 401 surfaces via rxjs's onUnhandledError path.
   it("anonymous authz 401 is handled gracefully without crashing", async () => {
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

      expect(screen.queryAllByRole("link")).toHaveLength(0);
      expect(String((capturedErrors[0] as any)?.status)).toBe("401");
   });
});
