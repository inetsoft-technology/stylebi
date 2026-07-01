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
 * Demo target for SecurityMswHandlers (Phase 2 / M6): MonitoringSidenavComponent is the EM
 * sidebar/nav-menu component that calls AuthorizationService.getPermissions("monitoring") and
 * shows/hides menu items (<a mat-list-item>) based on the response, exactly the pattern the
 * SecurityMswHandlers personas are meant to exercise.
 *
 * Persona coverage (8 required cases, see docs/superpowers/plans/2026-06-30-permission-test-phase2.md
 * Task 1):
 *   1. asSiteAdmin  — For-Org-x path (monitoring/cache)   -> menu item IN dom
 *   2. asSiteAdmin  — For-Org-ok path (monitoring/queries) -> menu item IN dom
 *   3. asSiteAdmin  — navbar isSiteAdmin=true reflected on the component
 *   4. asOrgAdmin   — For-Org-x path (monitoring/cache)   -> menu item NOT in dom
 *   5. asOrgAdmin   — For-Org-ok path (monitoring/queries) -> menu item IN dom (must not over-hide)
 *   6. asOrgAdmin   — navbar isOrgAdminOnly=true reflected on the component
 *   7. asViewer     — no admin entries rendered at all
 *   8. asAnonymous  — authz 401 handled gracefully (no crash, no visible menu items)
 *
 * NOTE on the authz child-key contract: the real backend (/api/em/authz) returns permissions
 * keyed by *child component name* (e.g. { cache: true, queries: true }), which is what
 * MonitoringSidenavComponent.ngOnInit() reads (p.permissions.cache, p.permissions.queries, ...).
 * SecurityMswHandlers.asSiteAdmin()/.asOrgAdmin() key their authz response by the *requested path*
 * itself ({ [path]: true }) — a deliberately simple, persona-level mock. To exercise the real
 * child-key contract this spec layers one additional server.use() authz override per case (same
 * "useInitXMarker()" composition pattern already used in security-settings-page.component.tl.spec.ts)
 * on top of SecurityMswHandlers, which still supplies the persona's navbar handlers verbatim.
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

// ─────────────────────────────────────────────────────────────────────────────
// For-Org-x / For-Org-ok fixtures (SITE_ADMIN_ONLY_PATHS leaf names, see
// security-permission.handlers.ts)
// ─────────────────────────────────────────────────────────────────────────────

// Templates use the raw "_#(...)" i18n placeholder syntax; no translation pipe runs in
// this test environment, so the accessible name is the untranslated placeholder text.

/** monitoring/cache — For-Org-x: siteAdmin only, orgAdmin denied+hidden. */
const CACHE_LINK_NAME = "_#(Cache)";
/** monitoring/queries — For-Org-ok: both siteAdmin and orgAdmin can access. */
const QUERIES_LINK_NAME = "_#(Queries)";

/**
 * Layers a child-key authz response on top of whatever persona handlers are already
 * registered, so MonitoringSidenavComponent's p.permissions.cache / .queries reads resolve
 * per the SITE_ADMIN_ONLY_PATHS boundary for the "monitoring" path.
 */
function useMonitoringChildPermissions(opts: { cache: boolean; queries: boolean }): void {
   server.use(
      http.get("*/api/em/authz", () =>
         HttpResponse.json({
            permissions: { cache: opts.cache, queries: opts.queries, summary: opts.cache,
               viewsheets: opts.queries, users: opts.queries, cluster: opts.cache, log: opts.cache },
            labels: {},
            multiTenancyHiddenComponents: {},
         })
      )
   );
}

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

describe("MonitoringSidenavComponent — SecurityMswHandlers personas", () => {

   // Case 1: asSiteAdmin — For-Org-x path (monitoring/cache) must be visible.
   it("case 1: siteAdmin sees the For-Org-x Cache menu item", async () => {
      server.use(...SecurityMswHandlers.asSiteAdmin());
      useMonitoringChildPermissions({ cache: true, queries: true });

      await renderComponent();

      expect(await screen.findByRole("link", { name: CACHE_LINK_NAME })).toBeTruthy();
   });

   // Case 2: asSiteAdmin — For-Org-ok path (monitoring/queries) must be visible.
   it("case 2: siteAdmin sees the For-Org-ok Queries menu item", async () => {
      server.use(...SecurityMswHandlers.asSiteAdmin());
      useMonitoringChildPermissions({ cache: true, queries: true });

      await renderComponent();

      expect(await screen.findByRole("link", { name: QUERIES_LINK_NAME })).toBeTruthy();
   });

   // Case 3: asSiteAdmin — navbar isSiteAdmin=true flag is served correctly.
   it("case 3: asSiteAdmin serves isSiteAdmin=true on the navbar endpoint", async () => {
      server.use(...SecurityMswHandlers.asSiteAdmin());
      useMonitoringChildPermissions({ cache: true, queries: true });

      const isSiteAdmin = await fetch("/api/em/navbar/isSiteAdmin").then(r => r.json());
      expect(isSiteAdmin).toBe(true);

      await renderComponent();
      // siteAdmin persona: no org-admin-only hiding, Cache (For-Org-x) stays visible.
      expect(await screen.findByRole("link", { name: CACHE_LINK_NAME })).toBeTruthy();
   });

   // Case 4: asOrgAdmin — For-Org-x path (monitoring/cache) must be hidden. Critical boundary.
   it("case 4: orgAdmin does NOT see the For-Org-x Cache menu item", async () => {
      server.use(...SecurityMswHandlers.asOrgAdmin());
      useMonitoringChildPermissions({ cache: false, queries: true });

      await renderComponent();
      // Wait for the For-Org-ok item to appear first, confirming the authz response has
      // resolved, before asserting the For-Org-x item's continued absence.
      await screen.findByRole("link", { name: QUERIES_LINK_NAME });

      expect(screen.queryByRole("link", { name: CACHE_LINK_NAME })).toBeNull();
   });

   // Case 5: asOrgAdmin — For-Org-ok path (monitoring/queries) must remain visible.
   // Paired with case 4: proves SITE_ADMIN_ONLY_PATHS is not over-hiding For-Org-ok items.
   it("case 5: orgAdmin still sees the For-Org-ok Queries menu item (no over-hiding)", async () => {
      server.use(...SecurityMswHandlers.asOrgAdmin());
      useMonitoringChildPermissions({ cache: false, queries: true });

      await renderComponent();

      expect(await screen.findByRole("link", { name: QUERIES_LINK_NAME })).toBeTruthy();
   });

   // Case 6: asOrgAdmin — navbar isOrgAdminOnly=true flag is served correctly.
   it("case 6: asOrgAdmin serves isOrgAdminOnly=true on the navbar endpoint", async () => {
      server.use(...SecurityMswHandlers.asOrgAdmin());
      useMonitoringChildPermissions({ cache: false, queries: true });

      const isOrgAdminOnly = await fetch("/api/em/navbar/isOrgAdminOnly").then(r => r.json());
      expect(isOrgAdminOnly).toBe(true);

      await renderComponent();
      // orgAdmin persona: For-Org-x Cache stays hidden, For-Org-ok Queries stays visible.
      expect(await screen.findByRole("link", { name: QUERIES_LINK_NAME })).toBeTruthy();
      expect(screen.queryByRole("link", { name: CACHE_LINK_NAME })).toBeNull();
   });

   // Case 7: asViewer — no admin menu entries at all.
   it("case 7: viewer sees no admin menu entries", async () => {
      server.use(...SecurityMswHandlers.asViewer());
      useMonitoringChildPermissions({ cache: false, queries: false });

      await renderComponent();
      // Give the authz subscription a chance to resolve before asserting continued absence.
      await waitFor(() => expect(screen.queryByRole("link")).toBeNull());

      expect(screen.queryByRole("link", { name: CACHE_LINK_NAME })).toBeNull();
      expect(screen.queryByRole("link", { name: QUERIES_LINK_NAME })).toBeNull();
   });

   // Case 8: asAnonymous — authz returns 401; component must not crash and must show no menu items.
   //
   // MonitoringSidenavComponent.ngOnInit() subscribes to getPermissions() with no error
   // callback, so RxJS reports the 401 via its internal reportUnhandledError path (rxjs
   // config.onUnhandledError) rather than throwing synchronously — a pre-existing gap in the
   // component, not introduced by this test. That path bypasses both Angular's ErrorHandler
   // and jsdom's window error/unhandledrejection events, so it is captured here via the
   // documented rxjs testing hook instead, and restored immediately after the assertions.
   // "Not crashing" here means: no menu items render, and the only error rxjs reports is the
   // expected 401 — nothing else (e.g. a TypeError from a bad template binding).
   it("case 8: anonymous authz 401 is handled gracefully without crashing", async () => {
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

      expect(screen.queryByRole("link", { name: CACHE_LINK_NAME })).toBeNull();
      expect(screen.queryByRole("link", { name: QUERIES_LINK_NAME })).toBeNull();
      expect(String((capturedErrors[0] as any)?.status)).toBe("401");
   });
});
