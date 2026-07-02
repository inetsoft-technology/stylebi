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
 * MonitoringSidenavComponent — Angular Testing Library + MSW
 *
 * Two describe blocks:
 *   1. "MonitoringSidenavComponent" — general smoke test (component creation).
 *   2. "MonitoringSidenavComponent — SECURITY: SecurityMswHandlers personas" — permission
 *      persona coverage.
 *
 * Component: EM sidebar/nav-menu; calls AuthorizationService.getPermissions("monitoring") and
 * shows/hides menu items (<a mat-list-item>) based on the response. Seven items total:
 * Summary/Cluster/Log/Cache are For-Org-x (siteAdmin only); Viewsheets/Queries/Users are
 * For-Org-ok (both siteAdmin and orgAdmin).
 *
 * Testing strategy: siteAdmin is intentionally NOT tested per item here. Proving "permission=true
 * renders the link visible" is generic Angular `@if` binding behavior, not gatekeeping logic —
 * it is already exercised by the For-Org-ok rows (queries/viewsheets/users=true) inside the
 * orgAdmin table below. orgAdmin is the actual boundary: a single persona whose permission map
 * mixes true and false values proves the binding works in both directions, so one parameterized
 * table covering all 7 items under orgAdmin is sufficient without a separate siteAdmin pass.
 *
 *   asOrgAdmin:
 *     orgAdmin item-visibility boundary — it.each over all 7 monitoring items, asserting
 *       Summary/Cluster/Log/Cache hidden and Viewsheets/Queries/Users visible.
 *     navbar isOrgAdminOnly=true — reflected on the component.
 *   asViewer:
 *     no admin entries rendered.
 *   asAnonymous:
 *     authz 401 handled gracefully (no crash, no visible menu items).
 *
 * NOTE on the authz child-key contract: the real backend (/api/em/authz) returns permissions
 * keyed by *child component name* (e.g. { cache: true, queries: true }), which is what
 * MonitoringSidenavComponent.ngOnInit() reads (p.permissions.cache, p.permissions.queries, ...).
 * SecurityMswHandlers.asOrgAdmin() keys its authz response by the *requested path* itself
 * ({ [path]: true }) — a deliberately simple, persona-level mock; since MonitoringSidenavComponent
 * requests the parent path ("monitoring"), that generic response has none of the child keys this
 * component actually reads. To exercise the real child-key contract this spec layers one
 * additional server.use() authz override (same "useInitXMarker()" composition pattern already
 * used in security-settings-page.component.tl.spec.ts) on top of SecurityMswHandlers, which still
 * supplies the persona's navbar handlers verbatim.
 */

import { NO_ERRORS_SCHEMA, Component } from "@angular/core";
import { provideHttpClient } from "@angular/common/http";
import { provideRouter } from "@angular/router";
import { provideNoopAnimations } from "@angular/platform-browser/animations";
import { render, screen, waitFor } from "@testing-library/angular";
import { http, HttpResponse } from "msw";
import { of, config as rxjsConfig } from "rxjs";

import { server } from "@test-mocks/server";
import { SecurityMswHandlers } from "@test-mocks/handlers/security-permission.handlers";
import { MonitoringSidenavComponent } from "./monitoring-sidenav.component";
import { PageHeaderComponent } from "../../page-header/page-header.component";
import { AppInfoService } from "../../../../../shared/util/app-info.service";

@Component({ selector: "em-page-header", template: "", standalone: true })
class MockPageHeaderComponent {}

// Templates use the raw "_#(...)" i18n placeholder syntax; no translation pipe runs in
// this test environment, so the accessible name is the untranslated placeholder text.

/** monitoring/queries — For-Org-ok item used as a resolution anchor for negative assertions. */
const QUERIES_LINK_NAME = "_#(Queries)";

/**
 * orgAdmin's fixed child-key permission map for the "monitoring" path, per
 * SITE_ADMIN_ONLY_PATHS in security-permission.handlers.ts: Summary/Cluster/Log/Cache are
 * For-Org-x (denied); Viewsheets/Queries/Users are For-Org-ok (allowed). This is the same for
 * every case in the describe block below — only which item a given case asserts on varies.
 */
function useOrgAdminChildPermissions(): void {
   server.use(
      http.get("*/api/em/authz", () =>
         HttpResponse.json({
            permissions: {
               summary: false, cluster: false, log: false, cache: false,
               viewsheets: true, queries: true, users: true,
            },
            labels: {},
            multiTenancyHiddenComponents: {},
         })
      )
   );
}

/** [accessible link name, expected visible under orgAdmin] for all 7 monitoring items. */
const ORG_ADMIN_ITEM_VISIBILITY: Array<[name: string, expectedVisible: boolean]> = [
   ["_#(Summary)", false],
   ["_#(Cluster)", false],
   ["_#(Logs)", false],
   ["_#(Cache)", false],
   ["_#(Viewsheets)", true],
   [QUERIES_LINK_NAME, true],
   ["_#(Users)", true],
];

async function renderComponent() {
   const appInfoMock = { isEnterprise: () => of(true) };

   return render(MonitoringSidenavComponent, {
      importOverrides: [
         { replace: PageHeaderComponent, with: MockPageHeaderComponent },
      ],
      providers: [
         provideHttpClient(),
         provideRouter([]),
         provideNoopAnimations(),
         { provide: AppInfoService, useValue: appInfoMock },
      ],
      schemas: [NO_ERRORS_SCHEMA],
   });
}

// ─────────────────────────────────────────────────────────────────────────────
// General (non-security) smoke test — merged from the former
// monitoring-sidenav.component.spec.ts (TestBed-based "should create" test).
// ─────────────────────────────────────────────────────────────────────────────

describe("MonitoringSidenavComponent", () => {

   // Basic creation smoke test. Relies on the default em.handlers.ts authz/navbar responses
   // (no persona override) — this block intentionally contains no security-persona coverage;
   // see the "SECURITY:" describe block below for that.
   it("should create", async () => {
      const { fixture } = await renderComponent();

      expect(fixture.componentInstance).toBeTruthy();
   });
});

// ─────────────────────────────────────────────────────────────────────────────
// SECURITY: SecurityMswHandlers persona coverage (Phase 2 / M6 demo target).
// ─────────────────────────────────────────────────────────────────────────────

describe("MonitoringSidenavComponent — SECURITY: SecurityMswHandlers personas", () => {

   // orgAdmin item-visibility boundary: one parameterized test covers all 7 items, proving the
   // For-Org-x/For-Org-ok split in both directions (hidden AND visible) under a single persona.
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
            await screen.findByRole("link", { name: QUERIES_LINK_NAME });
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
      // Sanity check that the component still renders correctly alongside the navbar flag;
      // full item-by-item coverage is the "item visibility boundary" block above.
      expect(await screen.findByRole("link", { name: QUERIES_LINK_NAME })).toBeTruthy();
   });

   // asViewer — no admin menu entries at all. asViewer() already returns an empty permissions
   // map for /api/em/authz, so no additional child-key override is needed here.
   it("viewer sees no admin menu entries", async () => {
      server.use(...SecurityMswHandlers.asViewer());

      await renderComponent();
      // Give the authz subscription a chance to resolve before asserting continued absence.
      await waitFor(() => expect(screen.queryByRole("link")).toBeNull());

      expect(screen.queryAllByRole("link")).toHaveLength(0);
   });

   // asAnonymous — authz returns 401; component must not crash and must show no menu items.
   //
   // MonitoringSidenavComponent.ngOnInit() subscribes to getPermissions() with no error
   // callback, so RxJS reports the 401 via its internal reportUnhandledError path (rxjs
   // config.onUnhandledError) rather than throwing synchronously — a pre-existing gap in the
   // component, not introduced by this test. That path bypasses both Angular's ErrorHandler
   // and jsdom's window error/unhandledrejection events, so it is captured here via the
   // documented rxjs testing hook instead, and restored immediately after the assertions.
   // "Not crashing" here means: no menu items render, and the only error rxjs reports is the
   // expected 401 — nothing else (e.g. a TypeError from a bad template binding).
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
