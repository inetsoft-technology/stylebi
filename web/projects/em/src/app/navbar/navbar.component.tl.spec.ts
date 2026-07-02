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
 * NavbarComponent — Angular Testing Library + MSW
 *
 * Component: EM top-level nav bar (Monitoring/Settings/Auditing/Notification icons). Unlike the
 * sidenav/tab-filter components in this test suite, NavbarComponent does NOT call
 * AuthorizationService.getPermissions() itself — its `permissions: ComponentPermissions` is a
 * plain @Input() supplied by a parent shell component that owns the HTTP call. So this spec sets
 * the input directly via componentInputs rather than going through SecurityMswHandlers/MSW for
 * the permission flags themselves; MSW is only needed for the component's own unrelated direct
 * HTTP call (get-navbar-model).
 *
 * notifyVisible (Notification icon) is For-Org-x (siteAdmin only); monitoringVisible/
 * settingsVisible/auditingVisible are For-Org-ok. Testing strategy: only the orgAdmin-shaped
 * input is exercised — one parameterized table whose rows mix false/true proves the `@if`
 * binding in both directions, same rationale as the other Task 1/M6 specs.
 *
 * The Notification link has no `routerLink`/`href` (it's a (click) handler), so `MatIconAnchor`
 * gives it role="button", not role="link" like the routerLink-based icons.
 *
 * No viewer/anonymous 401 case here: those personas simulate AuthorizationService HTTP
 * responses, and this component has no such call to simulate against. An empty-permissions
 * input (equivalent in shape to what asViewer()/asAnonymous() would produce upstream) is
 * covered by the "no permissions" case below instead.
 *
 * Heavy unrelated dependencies (FavoritesService, OrganizationDropdownService, LogoutService,
 * AiAssistantService, MatDialog, MatSnackBar) are mocked to isolate the test to the
 * permissions -> flag -> DOM binding; HelpService is left real since its one HTTP call
 * (GET em/help-links) already has a default MSW handler.
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { provideHttpClient } from "@angular/common/http";
import { provideRouter } from "@angular/router";
import { provideNoopAnimations } from "@angular/platform-browser/animations";
import { MatDialog } from "@angular/material/dialog";
import { MatSnackBar } from "@angular/material/snack-bar";
import { render, screen } from "@testing-library/angular";
import { http, HttpResponse } from "msw";
import { of } from "rxjs";

import { server } from "@test-mocks/server";
import { NavbarComponent } from "./navbar.component";
import { ComponentPermissions } from "../authorization/component-permissions";
import { EmNavbarModel } from "./em-navbar-model";
import { FavoritesService } from "../favorites/favorites.service";
import { OrganizationDropdownService } from "./organization-dropdown.service";
import { AiAssistantService } from "../../../../shared/ai-assistant/ai-assistant.service";
import { AppInfoService } from "../../../../shared/util/app-info.service";
import { LogoutService } from "../../../../shared/util/logout.service";

// Templates use the raw "_#(...)" i18n placeholder syntax; no translation pipe runs in this
// test environment, so the accessible name is the untranslated placeholder text.

const NAVBAR_MODEL: EmNavbarModel = {
   logoutUrl: "", customLogo: false, enterprise: true,
   elasticLicenseExhausted: false, homeLink: "", aiAssistantVisible: false,
};

function makePermissions(overrides: { [name: string]: boolean }): ComponentPermissions {
   return { permissions: overrides, labels: {}, multiTenancyHiddenComponents: {} };
}

/** orgAdmin-shaped permissions: Notification is For-Org-x; the rest are For-Org-ok. */
const ORG_ADMIN_PERMISSIONS = makePermissions({
   notification: false, monitoring: true, settings: true, auditing: true,
});

/** [accessible name, role, expected visible under the orgAdmin-shaped input]. */
const ORG_ADMIN_ITEM_VISIBILITY: Array<[name: string, role: string, expectedVisible: boolean]> = [
   ["_#(Send Notification)", "button", false],
   ["_#(Settings)", "link", true],
   ["_#(Monitoring)", "link", true],
];

async function renderComponent(permissions: ComponentPermissions) {
   server.use(
      http.get("*/api/em/navbar/get-navbar-model", () => HttpResponse.json(NAVBAR_MODEL))
   );

   return render(NavbarComponent, {
      componentInputs: { permissions },
      providers: [
         provideHttpClient(),
         provideRouter([]),
         provideNoopAnimations(),
         { provide: FavoritesService, useValue: { favorites: of([]), isFavorite: () => of(false) } },
         { provide: OrganizationDropdownService, useValue: {} },
         { provide: AiAssistantService, useValue: { aiAssistantVisible: false } },
         { provide: AppInfoService, useValue: { isEnterprise: () => of(true) } },
         { provide: LogoutService, useValue: { setFromEm: vi.fn(), setLogoutUrl: vi.fn(), inGracePeriod: of(false) } },
         { provide: MatDialog, useValue: { open: vi.fn() } },
         { provide: MatSnackBar, useValue: { open: vi.fn() } },
      ],
      schemas: [NO_ERRORS_SCHEMA],
   });
}

describe("NavbarComponent — SECURITY: permission input visibility boundary", () => {

   it.each(ORG_ADMIN_ITEM_VISIBILITY)("%s (role=%s) should be visible=%s", async (name, role, expectedVisible) => {
      await renderComponent(ORG_ADMIN_PERMISSIONS);

      if(expectedVisible) {
         expect(await screen.findByRole(role, { name })).toBeTruthy();
      }
      else {
         // Wait for a known-visible icon to resolve first, confirming the input has been
         // processed, before asserting this icon's continued absence.
         await screen.findByRole("link", { name: "_#(Settings)" });
         expect(screen.queryByRole(role, { name })).toBeNull();
      }
   });

   // No permissions at all (shape equivalent to what asViewer()/asAnonymous() would
   // produce upstream) — none of the permission-gated icons render.
   it("no permissions renders none of the permission-gated icons", async () => {
      await renderComponent(makePermissions({}));

      expect(screen.queryByRole("link", { name: "_#(Settings)" })).toBeNull();
      expect(screen.queryByRole("link", { name: "_#(Monitoring)" })).toBeNull();
      expect(screen.queryByRole("button", { name: "_#(Send Notification)" })).toBeNull();
   });
});
