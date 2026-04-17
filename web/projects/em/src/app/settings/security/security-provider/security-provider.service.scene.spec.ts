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
 * Risk-first coverage (4 groups, 11 cases):
 *   Group 1 [Risk 3, 3, 2] — updateAuthorizationProvider (3 cases)
 *   Group 2 [Risk 3, 3, 2] — updateAuthenticationProvider (3 cases)
 *   Group 3 [Risk 2, 2]    — testConnection / testDatabaseConnection (2 cases)
 *   Group 4 [Risk 3, 2, 2] — getAdminRoles (3 cases)
 *
 * Confirmed bugs (it.failing — remove wrapper once fixed):
 *   - none
 *
 * KEY contracts:
 *   - updateAuthorizationProvider/updateAuthenticationProvider use ADD URL when name is falsy,
 *     EDIT URL + name when truthy
 *   - catchError on update methods calls errorService.showSnackBar and does NOT trigger navigation
 *   - getAdminRoles catchError returns {ids: []} so the dialog always opens (graceful degradation)
 *   - getAdminRoles dialog cancel (null afterClosed result) → NEVER (pipeline stops, no emission)
 *
 * Design gaps:
 *   - triggerXxx void methods chain multiple dialogs and are skipped; better covered by
 *     component integration tests
 */

import { HttpClientTestingModule, HttpTestingController } from "@angular/common/http/testing";
import { TestBed } from "@angular/core/testing";
import { UntypedFormControl, UntypedFormGroup } from "@angular/forms";
import { MatBottomSheet } from "@angular/material/bottom-sheet";
import { MatDialog } from "@angular/material/dialog";
import { Router } from "@angular/router";
import { EMPTY, NEVER, of } from "rxjs";
import { ErrorHandlerService } from "../../../common/util/error/error-handler.service";
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
   let routerSpy: { navigate: jest.Mock };
   let dialogSpy: { open: jest.Mock };
   let bottomSheetSpy: { open: jest.Mock };
   let errorServiceSpy: { showSnackBar: jest.Mock };

   beforeEach(() => {
      routerSpy = { navigate: jest.fn() };
      dialogSpy = { open: jest.fn() };
      bottomSheetSpy = { open: jest.fn() };
      errorServiceSpy = { showSnackBar: jest.fn() };

      TestBed.configureTestingModule({
         imports: [HttpClientTestingModule],
         providers: [
            SecurityProviderService,
            { provide: Router, useValue: routerSpy },
            { provide: MatDialog, useValue: dialogSpy },
            { provide: MatBottomSheet, useValue: bottomSheetSpy },
            { provide: ErrorHandlerService, useValue: errorServiceSpy }
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
   // Group 4 [Risk 3, 2, 2] — getAdminRoles
   // ---------------------------------------------------------------------------
   describe("getAdminRoles", () => {
      it("[Risk 3] should open dialog with empty allRoles when the roles HTTP call fails", () => {
         // 🔁 Regression-sensitive: removing catchError would swallow the failure and skip the dialog
         //    entirely, so the user would never see the admin roles picker on network errors
         dialogSpy.open.mockReturnValue({ afterClosed: () => NEVER });

         service.getAdminRoles("admin", makeFileAuthnForm(), true).subscribe();

         const req = httpMock.expectOne("../api/em/security/get-roles");
         req.flush("Error", { status: 500, statusText: "Internal Server Error" });

         expect(dialogSpy.open).toHaveBeenCalledWith(
            expect.anything(),
            expect.objectContaining({ data: expect.objectContaining({ allRoles: [] }) })
         );
      });

      it("[Risk 2] should not emit when dialog is dismissed without a selection (null)", () => {
         // 🔁 Regression-sensitive: cancel returns NEVER (not EMPTY/complete); a refactor that switches
         //    to EMPTY would cause the downstream map to run with undefined and silently clear the roles field
         dialogSpy.open.mockReturnValue({ afterClosed: () => of(null) });

         let emitted = false;
         service.getAdminRoles("admin", makeFileAuthnForm(), true).subscribe(() => { emitted = true; });

         const req = httpMock.expectOne("../api/em/security/get-roles");
         req.flush({ ids: [], type: 0 });

         expect(emitted).toBe(false);
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
