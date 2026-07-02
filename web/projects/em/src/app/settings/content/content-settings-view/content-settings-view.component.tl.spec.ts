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
 * ContentSettingsViewComponent — Angular Testing Library + MSW
 *
 * Component: Content settings tab bar; calls AuthorizationService.getPermissions("settings/content")
 * and filters `links` down to `visibleLinks` (mechanism B — tab-link filter, same pattern as
 * PresentationNavViewComponent). Four tabs: Drivers and Plugins/Data Space are For-Org-x
 * (siteAdmin only); Materialized Views/Repository are For-Org-ok (both siteAdmin and orgAdmin).
 * `<a mat-tab-link>` sets role="tab" (ARIA tabs pattern), not "link".
 *
 * Testing strategy: only orgAdmin is tested per item — one parameterized table whose
 * rows mix For-Org-x/For-Org-ok proves the filter binding in both directions.
 *
 * NOTE on the authz child-key contract: SecurityMswHandlers.asOrgAdmin() keys its authz response
 * by the requested path itself; since ContentSettingsViewComponent requests the parent path
 * ("settings/content"), this spec layers a child-key authz override on top, same as the
 * monitoring-sidenav spec.
 *
 * NOTE on negative assertions: `visibleLinks` is initialized to the unfiltered `links` array
 * (all 4 tabs), so a For-Org-x tab is present in the DOM before the authz response resolves.
 * Negative cases must therefore poll with `waitFor` until the tab disappears rather than using
 * a "wait for a known-visible tab first" anchor — a For-Org-ok tab is visible from the very
 * first render regardless of whether the authz response has resolved yet, so it cannot be used
 * to detect that resolution has happened.
 *
 * NOTE on the anonymous (401) fallback: because `visibleLinks` only changes inside the
 * subscribe's next callback (no error callback is registered), a 401 leaves `visibleLinks` at
 * its initial value — i.e. all four tabs stay visible. This is a fail-open default, unlike the
 * sidenav components (whose per-item `xVisible` fields default to false), and is asserted here
 * as existing behavior, not changed.
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { provideHttpClient } from "@angular/common/http";
import { provideRouter } from "@angular/router";
import { provideNoopAnimations } from "@angular/platform-browser/animations";
import { render, screen, waitFor } from "@testing-library/angular";
import { http, HttpResponse } from "msw";
import { config as rxjsConfig } from "rxjs";

import { server } from "@test-mocks/server";
import { SecurityMswHandlers } from "@test-mocks/handlers/security-permission.handlers";
import { ContentSettingsViewComponent } from "./content-settings-view.component";
import { PageHeaderService } from "../../../page-header/page-header.service";

// Templates use the raw "_#(...)" i18n placeholder syntax; no translation pipe runs in this
// test environment, so the accessible name is the untranslated placeholder text.

/** orgAdmin's fixed child-key permission map for the "settings/content" path. */
function useOrgAdminChildPermissions(): void {
   server.use(
      http.get("*/api/em/authz", () =>
         HttpResponse.json({
            permissions: {
               "drivers-and-plugins": false,
               "data-space": false,
               "materialized-views": true,
               "repository": true,
            },
            labels: {},
            multiTenancyHiddenComponents: {},
         })
      )
   );
}

/** [accessible tab name, expected visible under orgAdmin] for all 4 content tabs. */
const ORG_ADMIN_ITEM_VISIBILITY: Array<[name: string, expectedVisible: boolean]> = [
   ["_#(js:Drivers and Plugins)", false],
   ["_#(js:Data Space)", false],
   ["_#(js:Materialized Views)", true],
   ["_#(js:Repository)", true],
];

async function renderComponent() {
   return render(ContentSettingsViewComponent, {
      providers: [
         provideHttpClient(),
         provideRouter([]),
         provideNoopAnimations(),
         { provide: PageHeaderService, useValue: { title: "" } },
      ],
      schemas: [NO_ERRORS_SCHEMA],
   });
}

describe("ContentSettingsViewComponent — SECURITY: SecurityMswHandlers personas", () => {

   describe("asOrgAdmin — item visibility boundary", () => {
      it.each(ORG_ADMIN_ITEM_VISIBILITY)("%s should be visible=%s", async (name, expectedVisible) => {
         server.use(...SecurityMswHandlers.asOrgAdmin());
         useOrgAdminChildPermissions();

         await renderComponent();

         if(expectedVisible) {
            expect(await screen.findByRole("tab", { name })).toBeTruthy();
         }
         else {
            // Poll until the tab disappears — it is present in the unfiltered initial state, so
            // a one-shot check right after render would pass trivially before the filter runs.
            await waitFor(() => expect(screen.queryByRole("tab", { name })).toBeNull());
         }
      });
   });

   // asViewer — empty permissions map filters out every tab.
   it("viewer sees no content tabs", async () => {
      server.use(...SecurityMswHandlers.asViewer());

      await renderComponent();
      await waitFor(() => expect(screen.queryAllByRole("tab")).toHaveLength(0));
   });

   // asAnonymous — authz 401; component must not crash. Unlike the flag-driven sidenav
   // components, this component's fail-open default means all four tabs remain visible (see
   // the file-level NOTE above) — the assertion here is that this stays the observed behavior.
   it("anonymous authz 401 is handled without crashing and leaves tabs at their fail-open default", async () => {
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

      expect(screen.getAllByRole("tab")).toHaveLength(4);
      expect(String((capturedErrors[0] as any)?.status)).toBe("401");
   });
});
