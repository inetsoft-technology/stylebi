/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
 * SecurityProviderService — scene-layer tests (HTTP calls + side effects)
 *
 * Risk-first coverage (4 groups, 13 cases):
 *   Group 1 [Risk 3, 3, 2] — updateAuthorizationProvider (3 cases)
 *   Group 2 [Risk 3, 3, 2] — updateAuthenticationProvider (3 cases)
 *   Group 3 [Risk 2, 2]    — testConnection / testDatabaseConnection (2 cases)
 *   Group 4 [Risk 3, 2, 2, 2, 2] — getAdminRoles (5 cases)
 *
 * Confirmed bugs (it.failing — remove wrapper once fixed):
 *   - none
 *
 * KEY contracts:
 *   - updateAuthorizationProvider/updateAuthenticationProvider use ADD URL when name is falsy,
 *     EDIT URL + name when truthy
 *   - catchError on update methods calls errorService.showSnackBar and does NOT trigger navigation
 *   - getAdminRoles catchError returns {ids: []} so the dialog always opens (graceful degradation)
 *   - getAdminRoles dialog dismiss (Cancel → null, Escape/backdrop → undefined) is filtered out:
 *     no emission, the observable just completes, so callers leave the roles field unchanged
 *
 * Design gaps:
 *   - triggerXxx void methods chain multiple dialogs and are skipped; better covered by
 *     component integration tests
 */

import { type Mock } from "vitest";
import { HttpClientTestingModule, HttpTestingController } from "@angular/common/http/testing";
import { TestBed } from "@angular/core/testing";
import { UntypedFormControl, UntypedFormGroup } from "@angular/forms";
import { MatBottomSheet } from "@angular/material/bottom-sheet";
import { MatDialog } from "@angular/material/dialog";
import { Router } from "@angular/router";
import { EMPTY, NEVER, of } from "rxjs";
import { ErrorHandlerService } from "../../../common/util/error/error-handler.service";
import { OrganizationDropdownService } from "../../../navbar/organization-dropdown.service";
import { SecurityProviderService } from "./security-provider.service";
import { SecurityProviderType } from "./security-provider-model/security-provider-type.enum";

// ---------------------------------------------------------------------------
// Form builder helpers
// ---------------------------------------------------------------------------

function makeFileAuthzForm(name = "MyProvider"): UntypedFormGroup {
   return new UntypedFormGroup({
      providerName: new UntypedFormControl(name),
      providerType: new UntypedFormControl(SecurityProviderType.FILE)
   });
}

function makeFileAuthnForm(name = "MyProvider", oldName = ""): UntypedFormGroup {
   return new UntypedFormGroup({
      providerName: new UntypedFormControl(name),
      oldName: new UntypedFormControl(oldName),
      providerType: new UntypedFormControl(SecurityProviderType.FILE)
   });
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe("SecurityProviderService — scene layer", () => {
   let service: SecurityProviderService;
   let httpMock: HttpTestingController;
   let routerSpy: { navigate: Mock };
   let dialogSpy: { open: Mock };
   let bottomSheetSpy: { open: Mock };
   let errorServiceSpy: { showSnackBar: Mock };
   let orgDropdownSpy: { refreshProviders: Mock };

   beforeEach(() => {
      routerSpy = { navigate: vi.fn() };
      dialogSpy = { open: vi.fn() };
      bottomSheetSpy = { open: vi.fn() };
      errorServiceSpy = { showSnackBar: vi.fn() };
      // stubbed out: the real service opens a STOMP connection and pulls in
      // SsoHeartbeatService, none of which this scene exercises
      orgDropdownSpy = { refreshProviders: vi.fn() };

      TestBed.configureTestingModule({
         imports: [HttpClientTestingModule],
         providers: [
            SecurityProviderService,
            { provide: Router, useValue: routerSpy },
            { provide: MatDialog, useValue: dialogSpy },
            { provide: MatBottomSheet, useValue: bottomSheetSpy },
            { provide: ErrorHandlerService, useValue: errorServiceSpy },
            { provide: OrganizationDropdownService, useValue: orgDropdownSpy }
         ]
      });

      service = TestBed.inject(SecurityProviderService);
      httpMock = TestBed.inject(HttpTestingController);
   });

   afterEach(() => {
      httpMock.verify();
   });

   // ---------------------------------------------------------------------------
   // Group 1 [Risk 3, 3, 2] — updateAuthorizationProvider
   // ---------------------------------------------------------------------------
   describe("updateAuthorizationProvider", () => {
      it("[Risk 3] should POST to EDIT URL with name appended when name is non-empty", () => {
         // 🔁 Regression-sensitive: name-routing is the primary condition separating ADD vs EDIT;
         //    swapping the branch silently sends edits to the wrong endpoint
         service.updateAuthorizationProvider("existingProvider", makeFileAuthzForm());

         const req = httpMock.expectOne("../api/em/security/edit-authorization-provider/existingProvider");
         expect(req.request.method).toBe("POST"); // (a)
         expect(req.request.body).toBeTruthy();
         expect(req.request.body.providerName).toBe("MyProvider");
         req.flush({});

         expect(routerSpy.navigate).toHaveBeenCalledWith(["/settings/security/provider"]); // (b)
      });

      it("[Risk 3] should call showSnackBar and NOT navigate when POST returns an error status", () => {
         // 🔁 Regression-sensitive: if catchError is removed, errors propagate silently and navigation
         //    still fires, leaving the provider in an inconsistent saved state
         errorServiceSpy.showSnackBar.mockReturnValue(EMPTY);

         service.updateAuthorizationProvider("", makeFileAuthzForm());

         const req = httpMock.expectOne("../api/em/security/add-authorization-provider");
         req.flush("Server Error", { status: 500, statusText: "Server Error" });

         expect(errorServiceSpy.showSnackBar).toHaveBeenCalled(); // (a)
         expect(routerSpy.navigate).not.toHaveBeenCalled(); // (b)
      });

      it("[Risk 2] should POST to ADD URL and navigate to list view when name is empty", () => {
         service.updateAuthorizationProvider("", makeFileAuthzForm());

         const req = httpMock.expectOne("../api/em/security/add-authorization-provider");
         req.flush({});

         expect(routerSpy.navigate).toHaveBeenCalledWith(["/settings/security/provider"]);
      });
   });

   // ---------------------------------------------------------------------------
   // Group 2 [Risk 3, 3, 2] — updateAuthenticationProvider
   // ---------------------------------------------------------------------------
   describe("updateAuthenticationProvider", () => {
      it("[Risk 3] should POST to EDIT URL with name appended when name is non-empty", () => {
         // 🔁 Regression-sensitive: same name-routing logic as authorization; the two update methods
         //    share the same conditional pattern and must both use the correct URL constants
         service.updateAuthenticationProvider("existingProvider", makeFileAuthnForm());

         const req = httpMock.expectOne("../api/em/security/edit-authentication-provider/existingProvider");
         expect(req.request.method).toBe("POST"); // (a)
         expect(req.request.body).toBeTruthy();
         expect(req.request.body.providerName).toBe("MyProvider");
         req.flush({});

         expect(routerSpy.navigate).toHaveBeenCalledWith(["/settings/security/provider"]); // (b)
      });

      it("[Risk 3] should call showSnackBar and NOT navigate when POST returns an error status", () => {
         // 🔁 Regression-sensitive: catchError must surface the error and block navigation;
         //    same risk as authorization provider — both pipe through the same error pattern
         errorServiceSpy.showSnackBar.mockReturnValue(EMPTY);

         service.updateAuthenticationProvider("", makeFileAuthnForm());

         const req = httpMock.expectOne("../api/em/security/add-authentication-provider");
         req.flush("Server Error", { status: 500, statusText: "Server Error" });

         expect(errorServiceSpy.showSnackBar).toHaveBeenCalled(); // (a)
         expect(routerSpy.navigate).not.toHaveBeenCalled(); // (b)
      });

      it("[Risk 2] should POST to ADD URL and navigate to list view when name is empty", () => {
         service.updateAuthenticationProvider("", makeFileAuthnForm());

         const req = httpMock.expectOne("../api/em/security/add-authentication-provider");
         req.flush({});

         expect(routerSpy.navigate).toHaveBeenCalledWith(["/settings/security/provider"]);
      });

      // 🔁 Regression-sensitive: the Users tab and page header read the provider list cached
      //    on OrganizationDropdownService for the whole browser session. Without this refresh an
      //    added or renamed provider is invisible there, and a stale cached name stays selected —
      //    get-security-tree-root then fails on a null provider until the user logs out and in.
      it("[Risk 3] should refresh the cached provider list on success but not on error", () => {
         service.updateAuthenticationProvider("", makeFileAuthnForm());
         httpMock.expectOne("../api/em/security/add-authentication-provider").flush({});
         expect(orgDropdownSpy.refreshProviders).toHaveBeenCalledTimes(1);

         errorServiceSpy.showSnackBar.mockReturnValue(EMPTY);
         service.updateAuthenticationProvider("existingProvider", makeFileAuthnForm());
         httpMock.expectOne("../api/em/security/edit-authentication-provider/existingProvider")
            .flush("Server Error", { status: 500, statusText: "Server Error" });
         expect(orgDropdownSpy.refreshProviders).toHaveBeenCalledTimes(1);
      });
   });

   // ---------------------------------------------------------------------------
   // Group 3 [Risk 2, 2] — testConnection / testDatabaseConnection
   // ---------------------------------------------------------------------------
   describe("testConnection / testDatabaseConnection", () => {
      it("[Risk 2] testConnection should POST to get-connection-status endpoint", () => {
         service.testConnection(makeFileAuthnForm()).subscribe();

         const req = httpMock.expectOne("../api/em/security/get-connection-status");
         expect(req.request.method).toBe("POST");
         req.flush({ connected: true });
      });

      it("[Risk 2] testDatabaseConnection should POST to get-database-connection-status endpoint", () => {
         service.testDatabaseConnection(makeFileAuthnForm()).subscribe();

         const req = httpMock.expectOne("../api/em/security/get-database-connection-status");
         expect(req.request.method).toBe("POST");
         req.flush({ connected: true });
      });
   });

   // ---------------------------------------------------------------------------
   // Bug #79235 — a failed preview query comes back as an error and must be shown
   // ---------------------------------------------------------------------------
   describe("preview getters", () => {
      const previews: [string, () => any, string][] = [
         ["getUsers", () => service.getUsers(makeFileAuthnForm()), "../api/em/security/get-users"],
         ["getGroups", () => service.getGroups(makeFileAuthnForm()), "../api/em/security/get-groups"],
         ["getRoles", () => service.getRoles(makeFileAuthnForm()), "../api/em/security/get-roles"],
         ["getOrganizations", () => service.getOrganizations(makeFileAuthnForm()),
            "../api/em/security/get-organizations"],
         ["getAllUserRoles", () => service.getAllUserRoles(makeFileAuthnForm()),
            "../api/em/security/get-user-roles"],
         ["getUserEmails", () => service.getUserEmails(makeFileAuthnForm(), uid),
            "../api/em/security/get-user-emails/"],
         ["getUserRoles", () => service.getUserRoles(makeFileAuthnForm(), uid),
            "../api/em/security/get-roles/"],
         ["getGroupUsers", () => service.getGroupUsers(makeFileAuthnForm(), uid),
            "../api/em/security/group/users/"],
         ["getOrganizationMembers", () => service.getOrganizationMembers(makeFileAuthnForm(), "org1"),
            "../api/em/security/organization-members/"]
      ];
      const uid = { name: "u1", orgID: "host-org" } as any;

      for(const [name, call, url] of previews) {
         it(`[Risk 3] ${name} should show the error and emit nothing when the request fails`, () => {
            errorServiceSpy.showSnackBar.mockImplementation((e, m, producer) => producer());
            const next = vi.fn();
            let completed = false;

            call().subscribe({ next, complete: () => completed = true });
            httpMock.expectOne(r => r.url.startsWith(url))
               .flush("failed", { status: 500, statusText: "Internal Server Error" });

            expect(errorServiceSpy.showSnackBar).toHaveBeenCalled();
            expect(next).not.toHaveBeenCalled();
            expect(completed).toBe(true);
         });

         it(`[Risk 2] ${name} should emit the result and not show an error on success`, () => {
            const next = vi.fn();

            call().subscribe(next);
            httpMock.expectOne(r => r.url.startsWith(url)).flush({ ids: [], list: [], type: 0 });

            expect(next).toHaveBeenCalledTimes(1);
            expect(errorServiceSpy.showSnackBar).not.toHaveBeenCalled();
         });
      }
   });

   // ---------------------------------------------------------------------------
   // Group 4 [Risk 3, 2, 2] — getAdminRoles
   // ---------------------------------------------------------------------------
   describe("getAdminRoles", () => {
      it("[Risk 3] should open dialog with empty allRoles when the roles HTTP call fails", () => {
         // 🔁 Regression-sensitive: removing catchError would swallow the failure and skip the dialog
         //    entirely, so the user would never see the admin roles picker on network errors
         dialogSpy.open.mockReturnValue({ afterClosed: () => NEVER });
         errorServiceSpy.showSnackBar.mockImplementation((e, m, producer) => producer());

         service.getAdminRoles("admin", makeFileAuthnForm(), true).subscribe();

         const req = httpMock.expectOne("../api/em/security/get-roles");
         req.flush("Error", { status: 500, statusText: "Internal Server Error" });

         expect(dialogSpy.open).toHaveBeenCalledWith(
            expect.anything(),
            expect.objectContaining({ data: expect.objectContaining({ allRoles: [] }) })
         );
         // Bug #79235: the failure reason is shown, not swallowed
         expect(errorServiceSpy.showSnackBar).toHaveBeenCalled();
      });

      // 🔁 Regression-sensitive (Bug #78720): the dialog's Cancel button closes with null, while
      //    MatDialog's own Escape and backdrop closes give undefined. Both must be dropped before the
      //    downstream map, which would turn them into "" and silently clear the admin roles field.
      //    The observable completes without emitting, so callers' take(1) subscriptions end cleanly.
      it.each([
         ["Cancel (null)", null],
         ["Escape / backdrop (undefined)", undefined]
      ])("[Risk 2] should not emit and should complete when dialog is dismissed by %s", (_label, result) => {
         dialogSpy.open.mockReturnValue({ afterClosed: () => of(result) });

         let emitted = false;
         let completed = false;
         service.getAdminRoles("admin", makeFileAuthnForm(), true).subscribe({
            next: () => { emitted = true; },
            complete: () => { completed = true; }
         });

         const req = httpMock.expectOne("../api/em/security/get-roles");
         req.flush({ ids: [], type: 0 });

         expect(emitted).toBe(false);
         expect(completed).toBe(true);
      });

      it("[Risk 2] should emit an empty string when the user removes every role and clicks OK", () => {
         // A deliberate clear-all closes with [] (not null/undefined) and must still clear the field
         dialogSpy.open.mockReturnValue({ afterClosed: () => of([]) });

         let result: string | undefined;
         service.getAdminRoles("admin", makeFileAuthnForm(), true).subscribe(value => { result = value; });

         const req = httpMock.expectOne("../api/em/security/get-roles");
         req.flush({ ids: [], type: 0 });

         expect(result).toBe("");
      });

      it("[Risk 2] should emit the role list joined with ', ' when dialog returns a non-null array", () => {
         dialogSpy.open.mockReturnValue({ afterClosed: () => of(["admin", "superuser"]) });

         let result: string | undefined;
         service.getAdminRoles("admin", makeFileAuthnForm(), true).subscribe(value => { result = value; });

         const req = httpMock.expectOne("../api/em/security/get-roles");
         req.flush({ ids: [], type: 0 });

         expect(result).toBe("admin, superuser");
      });
   });
});
