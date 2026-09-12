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
 * SecuritySettingsPageComponent — Angular Testing Library + MSW
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3]  — toggleEnterpriseToggle: request body + HTTP error handling
 *   Group 2 [Risk 3]  — toggleEnterpriseToggle warning path preserves server state
 *   Group 3 [Risk 2]  — toggleSelfSignupEnabled: no in-flight lock allows concurrent POST requests
 *   Group 4 [Risk 2]  — ldapProviderUsed: late appInfoService emission overwrites get-multi-tenancy response
 *   Group 5 [Risk 2]  — ngOnInit: security and multi-tenancy state loaded correctly from server
 *   Group 6 [Risk 2]  — toggleSecurityEnabled: loadScheduleUsers called only on non-warning path
 *
 * KEY contracts: event.warning of null, undefined, or "" all route to the success path.
 *   toggleEnterpriseToggle calls refreshContent() on both success and warning paths.
 *   toggleSecurityEnabled calls refreshContent() only on the warning (error) path.
 *   MSW default handlers (em.handlers.ts) return security=off, multi-tenancy=off, self-signup=off.
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { provideHttpClient } from "@angular/common/http";
import { MatDialog } from "@angular/material/dialog";
import { MatSlideToggleChange } from "@angular/material/slide-toggle";
import { render, screen, waitFor } from "@testing-library/angular";
import { http, HttpResponse as MswHttpResponse } from "msw";
import { Subject, of, config as rxjsConfig } from "rxjs";

import { server } from "@test-mocks/server";
import { SecurityMswHandlers } from "@test-mocks/handlers/security-permission.handlers";
import { AppInfoService } from "../../../../../../shared/util/app-info.service";
import { ScheduleUsersService } from "../../../../../../shared/schedule/schedule-users.service";
import { AuthorizationService } from "../../../authorization/authorization.service";
import { PageHeaderService } from "../../../page-header/page-header.service";
import { OrganizationDropdownService } from "../../../navbar/organization-dropdown.service";
import { SecurityBusyService } from "../users/security-busy.service";
import { SecurityEnabledEvent } from "./security-enabled-event";
import { SecuritySettingsPageComponent } from "./security-settings-page.component";

// ─── Test helpers ────────────────────────────────────────────────────────────

function makeToggle(checked: boolean): MatSlideToggleChange {
   return { checked, source: null } as MatSlideToggleChange;
}

const BASE_EVENT: SecurityEnabledEvent = {
   enable: false, toggleDisabled: false, ldapProviderUsed: false
};

const DEFAULT_PERMISSIONS = {
   permissions: { provider: false, users: false, actions: false, sso: false, googleSignIn: false }
};

/** Distinct passOrgIdAs from MSW default ("domain") — confirms get-multi-tenancy ngOnInit finished. */
const INIT_PASS_ORG_ID_AS = "path";

function useInitMultiTenancyMarker(): void {
   server.use(
      http.get("*/api/em/security/get-multi-tenancy", () =>
         MswHttpResponse.json({
            enable: false, toggleDisabled: false, ldapProviderUsed: false,
            passOrgIdAs: INIT_PASS_ORG_ID_AS
         })
      )
   );
}

function useInitSecurityMarker(): void {
   server.use(
      http.get("*/api/em/security/get-enable-security", () =>
         MswHttpResponse.json({ enable: false, toggleDisabled: true, ldapProviderUsed: false })
      )
   );
}

function useInitSelfSignupMarker(): void {
   server.use(
      http.get("*/api/em/security/get-enable-self-signup", () =>
         MswHttpResponse.json({ enable: true, toggleDisabled: false, ldapProviderUsed: false })
      )
   );
}

async function waitForMultiTenancyInit(component: SecuritySettingsPageComponent): Promise<void> {
   await waitFor(() => expect(component.passOrgIdAs).toBe(INIT_PASS_ORG_ID_AS));
}

async function waitForSecurityInit(component: SecuritySettingsPageComponent): Promise<void> {
   await waitFor(() => expect(component.securityToggleDisabled).toBe(true));
}

async function waitForSelfSignupInit(component: SecuritySettingsPageComponent): Promise<void> {
   await waitFor(() => expect(component.selfSignupEnabled).toBe(true));
}

interface RenderOptions {
   ldapProviderUsed$?: Subject<boolean>;
}

async function renderComponent(opts: RenderOptions = {}) {
   const ldapProviderUsed$ = opts.ldapProviderUsed$ ?? new Subject<boolean>();
   const dialogMock = { open: vi.fn() };
   const scheduleUsersMock = { loadScheduleUsers: vi.fn() };
   const orgDropdownMock = { refreshProviders: vi.fn() };
   const authzMock = {
      getPermissions: vi.fn().mockReturnValue(of(DEFAULT_PERMISSIONS))
   };
   const appInfoMock = {
      isEnterprise: vi.fn().mockReturnValue(of(false)),
      isLdapProviderUsed: vi.fn().mockReturnValue(ldapProviderUsed$),
      setLdapProviderUsed: vi.fn()
   };
   const orgBusyMock = { orgLoading$: new Subject<boolean>() };

   const result = await render(SecuritySettingsPageComponent, {
      providers: [
         provideHttpClient(),
         { provide: MatDialog, useValue: dialogMock },
         { provide: ScheduleUsersService, useValue: scheduleUsersMock },
         { provide: OrganizationDropdownService, useValue: orgDropdownMock },
         { provide: AuthorizationService, useValue: authzMock },
         { provide: AppInfoService, useValue: appInfoMock },
         { provide: SecurityBusyService, useValue: orgBusyMock },
         { provide: PageHeaderService, useValue: { title: "" } }
      ],
      schemas: [NO_ERRORS_SCHEMA]
   });

   const component = result.fixture.componentInstance;

   return {
      component,
      fixture: result.fixture,
      dialogMock,
      scheduleUsersMock,
      orgDropdownMock,
      ldapProviderUsed$,
      appInfoMock
   };
}

// ─────────────────────────────────────────────────────────────────────────────

describe("SecuritySettingsPageComponent", () => {

   // ─── Group 1 [Risk 3] ─────────────────────────────────────────────────────
   // toggleEnterpriseToggle: request body must send multiTenancyToggleDisabled, not securityToggleDisabled
   describe("Group 1 — toggleEnterpriseToggle: request body field contract", () => {

      // 🔁 Regression-sensitive: toggleDisabled in the POST body must reflect the
      // multi-tenancy toggle's lock state (true, set at method entry), not the security
      // toggle's state. When both flags diverge the backend receives incorrect data.
      it("should send multiTenancyToggleDisabled (true) not securityToggleDisabled (false) in request body", async () => {
         let capturedBody: SecurityEnabledEvent | null = null;
         server.use(
            http.post("*/api/em/security/set-multi-tenancy", async ({ request }) => {
               capturedBody = await request.json() as SecurityEnabledEvent;
               return MswHttpResponse.json({ ...BASE_EVENT, enable: true });
            })
         );

         const { component } = await renderComponent();

         // securityToggleDisabled stays false; multiTenancyToggleDisabled is set to true by the method
         component.securityToggleDisabled = false;
         component.toggleEnterpriseToggle(makeToggle(true));

         await waitFor(() => expect(capturedBody).not.toBeNull());
         // multiTenancyToggleDisabled was set to true at method entry — that value must be sent
         expect(capturedBody!.toggleDisabled).toBe(true);
      });

      // 🔁 Regression-sensitive: multiTenancyToggleDisabled must release after success;
      // if finalize is removed the toggle stays locked forever
      it("should re-enable multiTenancyToggleDisabled via finalize after successful response", async () => {
         server.use(
            http.post("*/api/em/security/set-multi-tenancy", () =>
               MswHttpResponse.json({ ...BASE_EVENT, enable: true })
            )
         );

         const { component } = await renderComponent();
         component.toggleEnterpriseToggle(makeToggle(true));
         expect(component.multiTenancyToggleDisabled).toBe(true);

         await waitFor(() => expect(component.multiTenancyToggleDisabled).toBe(false));
      });

      it("should call orgDropdownService.refreshProviders on the success path", async () => {
         server.use(
            http.post("*/api/em/security/set-multi-tenancy", () =>
               MswHttpResponse.json({ ...BASE_EVENT, enable: true })
            )
         );

         const { component, orgDropdownMock } = await renderComponent();
         component.toggleEnterpriseToggle(makeToggle(true));

         // Wait until refreshProviders has been called AND refreshContent() has run
         // (isRefreshed=false). Confirming both together means refreshContent's 50ms
         // timer hasn't fired yet, so we can drain it in the next waitFor.
         await waitFor(() => {
            expect(orgDropdownMock.refreshProviders).toHaveBeenCalledTimes(1);
            expect(component.isRefreshed).toBe(false);
         });
         // Drain the 50ms refreshContent timer so it fires here, not during teardown.
         await waitFor(() => expect(component.isRefreshed).toBe(true));
      });

      it("should NOT call orgDropdownService.refreshProviders on the warning path", async () => {
         server.use(
            http.post("*/api/em/security/set-multi-tenancy", () =>
               MswHttpResponse.json({ ...BASE_EVENT, warning: "cannot disable" })
            )
         );

         const { component, orgDropdownMock } = await renderComponent();
         component.toggleEnterpriseToggle(makeToggle(true));

         await waitFor(() => expect(component.multiTenancyToggleDisabled).toBe(false));
         expect(orgDropdownMock.refreshProviders).not.toHaveBeenCalled();
      });

      // Regression-sensitive: POST errors must be handled so jsdom does not report an uncaught
      // HttpErrorResponse, while finalize() still releases multiTenancyToggleDisabled.
      it("should re-enable multiTenancyToggleDisabled via finalize even after HTTP error", async () => {
         const uncaughtErrors: string[] = [];
         const errorListener = (event: ErrorEvent) => {
            uncaughtErrors.push(event.message ?? String(event.error));
         };
         window.addEventListener("error", errorListener);

         server.use(
            http.post("*/api/em/security/set-multi-tenancy", () =>
               new MswHttpResponse(null, { status: 500 })
            )
         );

         const { component } = await renderComponent();
         component.toggleEnterpriseToggle(makeToggle(true));
         expect(component.multiTenancyToggleDisabled).toBe(true);

         await waitFor(() => expect(component.multiTenancyToggleDisabled).toBe(false));
         // Let jsdom surface the async uncaught HttpErrorResponse from subscribe (no error callback).
         await new Promise(resolve => setTimeout(resolve, 50));

         window.removeEventListener("error", errorListener);
         expect(uncaughtErrors).toEqual([]);
      });
   });

   // ─── Group 2 [Risk 3] ─────────────────────────────────────────────────────
   // toggleEnterpriseToggle error path: multiTenancyEnabled must not be hardcoded on warning
   describe("Group 2 — toggleEnterpriseToggle: multiTenancyEnabled state after warning response", () => {

      // 🔁 Regression-sensitive: UI must preserve pre-toggle state when server rejects the change.
      // passOrgIdAs:"path" in the GET override is used as a synchronization signal: waiting for
      // it confirms the ngOnInit GET has completed, preventing the GET response from arriving
      // after the POST and accidentally overwriting multiTenancyEnabled back to false.
      it("should keep multiTenancyEnabled=false when warning fires and pre-toggle state was false", async () => {
         server.use(
            http.get("*/api/em/security/get-multi-tenancy", () =>
               MswHttpResponse.json({ ...BASE_EVENT, enable: false, passOrgIdAs: "path" })
            ),
            http.post("*/api/em/security/set-multi-tenancy", () =>
               MswHttpResponse.json({ ...BASE_EVENT, enable: false, warning: "ldap not compatible" })
            )
         );

         const { component } = await renderComponent();

         // Wait for the ngOnInit GET to finish before firing the toggle, so the GET response
         // cannot arrive after the POST and reset multiTenancyEnabled back to false.
         await waitFor(() => expect(component.passOrgIdAs).toBe("path"));

         component.multiTenancyEnabled = false;
         component.toggleEnterpriseToggle(makeToggle(true)); // attempt to enable; server rejects

         await waitFor(() => expect(component.multiTenancyToggleDisabled).toBe(false));
         // server rejected the change — must stay false, not be hardcoded to true
         expect(component.multiTenancyEnabled).toBe(false);
      });

      it("should open an error dialog with the warning content", async () => {
         server.use(
            http.post("*/api/em/security/set-multi-tenancy", () =>
               MswHttpResponse.json({ ...BASE_EVENT, warning: "some server error" })
            )
         );

         const { component, dialogMock } = await renderComponent();
         component.toggleEnterpriseToggle(makeToggle(true));

         await waitFor(() => expect(dialogMock.open).toHaveBeenCalledTimes(1));
         const dialogData = dialogMock.open.mock.calls[0][1].data;
         expect(dialogData.content).toBe("some server error");
      });

      it("should treat empty-string warning as success (no dialog, multiTenancyEnabled from event.enable)", async () => {
         useInitMultiTenancyMarker();
         server.use(
            http.post("*/api/em/security/set-multi-tenancy", () =>
               MswHttpResponse.json({ ...BASE_EVENT, enable: true, warning: "" })
            )
         );

         const { component, dialogMock } = await renderComponent();
         await waitForMultiTenancyInit(component);
         component.multiTenancyEnabled = false;
         component.toggleEnterpriseToggle(makeToggle(true));

         await waitFor(() => expect(component.multiTenancyEnabled).toBe(true));
         expect(dialogMock.open).not.toHaveBeenCalled();
      });

      it("should set multiTenancyEnabled from event.enable on null warning (success path)", async () => {
         useInitMultiTenancyMarker();
         server.use(
            http.post("*/api/em/security/set-multi-tenancy", () =>
               MswHttpResponse.json({ ...BASE_EVENT, enable: true })
            )
         );

         const { component } = await renderComponent();
         await waitForMultiTenancyInit(component);
         component.multiTenancyEnabled = false;
         component.toggleEnterpriseToggle(makeToggle(true));

         await waitFor(() => expect(component.multiTenancyEnabled).toBe(true));
      });
   });

   // ─── Group 3 [Risk 2] ─────────────────────────────────────────────────────
   // toggleSelfSignupEnabled: no in-flight lock allows concurrent POST requests
   describe("Group 3 — toggleSelfSignupEnabled: in-flight behavior", () => {

      it("should update selfSignupEnabled from server response", async () => {
         useInitSelfSignupMarker();
         server.use(
            http.post("*/api/em/security/set-enable-self-signup", () =>
               MswHttpResponse.json({ ...BASE_EVENT, enable: true })
            )
         );

         const { component } = await renderComponent();
         await waitForSelfSignupInit(component);
         component.selfSignupEnabled = false;
         component.toggleSelfSignupEnabled(makeToggle(true));

         await waitFor(() => expect(component.selfSignupEnabled).toBe(true));
      });

      // 🔁 Regression-sensitive: a second toggle before the first request completes fires a
      // duplicate POST — the component has no guard. If a guard is added later it must be tested.
      it("should allow a second POST while first is still in flight (no lock guard)", async () => {
         const receivedRequests: Request[] = [];
         server.use(
            http.post("*/api/em/security/set-enable-self-signup", ({ request }) => {
               receivedRequests.push(request);
               return MswHttpResponse.json(BASE_EVENT);
            })
         );

         const { component } = await renderComponent();
         component.toggleSelfSignupEnabled(makeToggle(true));
         component.toggleSelfSignupEnabled(makeToggle(false)); // rapid second toggle

         await waitFor(() => expect(receivedRequests.length).toBe(2));
      });
   });

   // ─── Group 4 [Risk 2] ─────────────────────────────────────────────────────
   // ldapProviderUsed: two sources write the same field — race condition
   describe("Group 4 — ldapProviderUsed: HTTP response vs appInfoService subscription", () => {

      it("should set ldapProviderUsed from get-multi-tenancy HTTP response", async () => {
         server.use(
            http.get("*/api/em/security/get-multi-tenancy", () =>
               MswHttpResponse.json({ ...BASE_EVENT, ldapProviderUsed: true, passOrgIdAs: "domain" })
            )
         );

         const { component } = await renderComponent();
         await waitFor(() => expect(component.ldapProviderUsed).toBe(true));
      });

      // 🔁 Regression-sensitive: a post-init emission from the shared appInfoService service
      // silently overwrites the value that the get-multi-tenancy response just set.
      // This can flip the multi-tenancy toggle's disabled state unexpectedly.
      it("should allow a late appInfoService emission to overwrite ldapProviderUsed from HTTP", async () => {
         server.use(
            http.get("*/api/em/security/get-multi-tenancy", () =>
               MswHttpResponse.json({ ...BASE_EVENT, ldapProviderUsed: true, passOrgIdAs: "domain" })
            )
         );

         const ldapProviderUsed$ = new Subject<boolean>();
         const { component } = await renderComponent({ ldapProviderUsed$ });

         await waitFor(() => expect(component.ldapProviderUsed).toBe(true));

         // another component calls appInfoService.setLdapProviderUsed(false) — late emission
         ldapProviderUsed$.next(false);
         expect(component.ldapProviderUsed).toBe(false); // HTTP value silently lost
      });
   });

   // ─── Group 5 [Risk 2] ─────────────────────────────────────────────────────
   // ngOnInit: initial toggle and feature state loaded from server
   describe("Group 5 — ngOnInit: initial state loaded from server", () => {

      // 🔁 Regression-sensitive: security toggle must reflect server state on init;
      // if the default false is shown when server says true, the admin sees stale state
      it("should set securityEnabled and securityToggleDisabled from get-enable-security response", async () => {
         server.use(
            http.get("*/api/em/security/get-enable-security", () =>
               MswHttpResponse.json({ enable: true, toggleDisabled: true, ldapProviderUsed: false })
            )
         );

         const { component } = await renderComponent();
         await waitFor(() => {
            expect(component.securityEnabled).toBe(true);
            expect(component.securityToggleDisabled).toBe(true);
         });
      });

      it("should set multiTenancyEnabled, passOrgIdAs, and cloudPlatform from get-multi-tenancy response", async () => {
         server.use(
            http.get("*/api/em/security/get-multi-tenancy", () =>
               MswHttpResponse.json({
                  enable: true, toggleDisabled: false, ldapProviderUsed: false,
                  passOrgIdAs: "path", cloudPlatform: true
               })
            )
         );

         const { component } = await renderComponent();
         await waitFor(() => {
            expect(component.multiTenancyEnabled).toBe(true);
            expect(component.passOrgIdAs).toBe("path");
            expect(component.cloudPlatform).toBe(true);
         });
      });

      it("should set selfSignupEnabled from get-enable-self-signup response", async () => {
         server.use(
            http.get("*/api/em/security/get-enable-self-signup", () =>
               MswHttpResponse.json({ enable: true, toggleDisabled: false, ldapProviderUsed: false })
            )
         );

         const { component } = await renderComponent();
         await waitFor(() => expect(component.selfSignupEnabled).toBe(true));
      });
   });

   // ─── Group 6 [Risk 2] ─────────────────────────────────────────────────────
   // toggleSecurityEnabled: loadScheduleUsers conditional on non-warning path
   describe("Group 6 — toggleSecurityEnabled: conditional loadScheduleUsers", () => {

      // 🔁 Regression-sensitive: loadScheduleUsers must be called after every successful security
      // toggle so the scheduler's user list stays in sync; missing this call causes stale data
      it("should call loadScheduleUsers and update securityEnabled on success (no warning)", async () => {
         useInitSecurityMarker();
         server.use(
            http.post("*/api/em/security/set-enable-security", () =>
               MswHttpResponse.json({ ...BASE_EVENT, enable: true })
            )
         );

         const { component, scheduleUsersMock } = await renderComponent();
         await waitForSecurityInit(component);
         component.toggleSecurityEnabled(makeToggle(true));

         await waitFor(() => {
            expect(scheduleUsersMock.loadScheduleUsers).toHaveBeenCalledTimes(1);
            expect(component.securityEnabled).toBe(true);
         });
      });

      it("should open error dialog and NOT call loadScheduleUsers when response has a warning", async () => {
         server.use(
            http.post("*/api/em/security/set-enable-security", () =>
               MswHttpResponse.json({ ...BASE_EVENT, enable: false, warning: "permission denied" })
            )
         );

         const { component, dialogMock, scheduleUsersMock } = await renderComponent();
         component.toggleSecurityEnabled(makeToggle(true));

         await waitFor(() => expect(dialogMock.open).toHaveBeenCalledTimes(1));
         expect(scheduleUsersMock.loadScheduleUsers).not.toHaveBeenCalled();
         expect(component.securityEnabled).toBe(false);
      });
   });
});

// ─────────────────────────────────────────────────────────────────────────────
// SECURITY: SecurityMswHandlers persona coverage (Task 1/M6 follow-up).
//
// Groups 1-6 above mock AuthorizationService directly (authzMock in renderComponent()),
// bypassing HTTP entirely — correct for testing toggle/HTTP-request behavior unrelated to
// permissions. This block instead leaves AuthorizationService un-mocked so /api/em/authz flows
// through MSW/SecurityMswHandlers, to test the actual permission -> tab visibility binding
// (mechanism A, same `@if (xVisible)` pattern as monitoring-sidenav/settings-sidenav).
//
// The tab nav only renders at all when `securityEnabled && isRefreshed` (security-settings-page
// .component.html L73) — the default MSW handler returns enable:false for get-enable-security,
// so every case here overrides it to true; otherwise all 5 tabs would be absent regardless of
// permissions, making the assertions vacuous.
//
// Providers/SSO/Sign In With Google are For-Org-x; Actions/Users are For-Org-ok. Testing
// strategy: only orgAdmin is tested per item, same rationale as the other Task 1/M6 specs.
// ─────────────────────────────────────────────────────────────────────────────

describe("SecuritySettingsPageComponent — SECURITY: SecurityMswHandlers personas", () => {

   function useSecurityEnabled(): void {
      server.use(
         http.get("*/api/em/security/get-enable-security", () =>
            MswHttpResponse.json({ enable: true, toggleDisabled: false, ldapProviderUsed: false })
         )
      );
   }

   /** [accessible tab name, expected visible under orgAdmin] for all 5 security tabs. */
   const ORG_ADMIN_ITEM_VISIBILITY: Array<[name: string, expectedVisible: boolean]> = [
      ["_#(Security Providers)", false],
      ["_#(em.security.sso)", false],
      ["_#(Sign In With Google)", false],
      ["_#(Actions)", true],
      ["_#(Users)", true],
   ];

   /** A known For-Org-ok tab used as a resolution anchor for negative assertions. */
   const USERS_TAB_NAME = "_#(Users)";

   /**
    * Unlike renderComponent() above, this does NOT provide AuthorizationService — it stays the
    * real root-provided singleton so /api/em/authz goes through MSW.
    */
   async function renderComponentAsPersona() {
      const dialogMock = { open: vi.fn() };
      const scheduleUsersMock = { loadScheduleUsers: vi.fn() };
      const orgDropdownMock = { refreshProviders: vi.fn() };
      const appInfoMock = {
         isEnterprise: vi.fn().mockReturnValue(of(false)),
         isLdapProviderUsed: vi.fn().mockReturnValue(of(false)),
         setLdapProviderUsed: vi.fn()
      };
      const orgBusyMock = { orgLoading$: new Subject<boolean>() };

      return render(SecuritySettingsPageComponent, {
         providers: [
            provideHttpClient(),
            { provide: MatDialog, useValue: dialogMock },
            { provide: ScheduleUsersService, useValue: scheduleUsersMock },
            { provide: OrganizationDropdownService, useValue: orgDropdownMock },
            { provide: AppInfoService, useValue: appInfoMock },
            { provide: SecurityBusyService, useValue: orgBusyMock },
            { provide: PageHeaderService, useValue: { title: "" } }
         ],
         schemas: [NO_ERRORS_SCHEMA]
      });
   }

   describe("asOrgAdmin — item visibility boundary", () => {
      it.each(ORG_ADMIN_ITEM_VISIBILITY)("%s should be visible=%s", async (name, expectedVisible) => {
         server.use(...SecurityMswHandlers.asOrgAdmin());
         useSecurityEnabled();

         await renderComponentAsPersona();

         if(expectedVisible) {
            expect(await screen.findByRole("tab", { name })).toBeTruthy();
         }
         else {
            // Wait for a known-visible tab to resolve first, confirming the authz response has
            // settled, before asserting this tab's continued absence.
            await screen.findByRole("tab", { name: USERS_TAB_NAME });
            expect(screen.queryByRole("tab", { name })).toBeNull();
         }
      });
   });

   // asViewer — empty permissions map filters out every tab. securityEnabled is forced true so
   // the absence is proven to come from the permission filter, not from the tab nav being
   // hidden entirely.
   it("viewer sees no security tabs", async () => {
      server.use(...SecurityMswHandlers.asViewer());
      useSecurityEnabled();

      await renderComponentAsPersona();
      await waitFor(() => expect(screen.queryAllByRole("tab")).toHaveLength(0));
   });

   // asAnonymous — authz 401; component must not crash. Same rxjs unhandled-error capture
   // pattern as monitoring-sidenav: ngOnInit subscribes with no error callback.
   it("anonymous authz 401 is handled without crashing and renders no tabs", async () => {
      server.use(...SecurityMswHandlers.asAnonymous());
      useSecurityEnabled();

      const capturedErrors: unknown[] = [];
      const previousOnUnhandledError = rxjsConfig.onUnhandledError;
      rxjsConfig.onUnhandledError = (err) => capturedErrors.push(err);

      try {
         await renderComponentAsPersona();
         await waitFor(() => expect(capturedErrors.length).toBeGreaterThan(0));
      }
      finally {
         rxjsConfig.onUnhandledError = previousOnUnhandledError;
      }

      expect(screen.queryAllByRole("tab")).toHaveLength(0);
      expect(String((capturedErrors[0] as any)?.status)).toBe("401");
   });
});
