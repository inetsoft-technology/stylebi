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
 * SecurityActionService — scene-layer tests (HTTP calls + side effects)
 *
 * Risk-first coverage (3 groups, 10 cases):
 *   Group 1 [Risk 2]         — getActionTree (1 case)
 *   Group 2 [Risk 3, 3, 3, 2] — getPermissions (4 cases)
 *   Group 3 [Risk 3, 3, 3, 2] — setPermissions (4 cases)
 *
 * Confirmed bugs (it.failing — remove wrapper once fixed):
 *   - none; both getPermissions (line 55) and setPermissions (line 80) now use
 *     `error.error?.type` — the null-body TypeError is already guarded.
 *
 * KEY contracts:
 *   - `*` in path must be encoded as `%2A` before constructing the URI
 *   - On any HTTP error, both getPermissions and setPermissions return emptyModel and open a dialog
 *   - isGrant is always sent as a string query param ("true" / "false")
 *   - InvalidOrgException type uses error.error.message; all other errors use the generic i18n string
 *
 * Design gaps:
 *   - getActionTree has no catchError — errors propagate to callers; no error-path test needed here
 *   - null path is stringified to "null" in the URI (template literal converts null to "null");
 *     whether this is intentional is unclear — skipped, callers appear to guard against null
 */

import { HttpClientTestingModule, HttpTestingController } from "@angular/common/http/testing";
import { TestBed } from "@angular/core/testing";
import { MatDialog } from "@angular/material/dialog";
import { ResourcePermissionModel } from "../resource-permission/resource-permission-model";
import { SecurityActionService } from "./security-action.service";

// ---------------------------------------------------------------------------
// Shared fixture: matches the service's private emptyModel exactly
// ---------------------------------------------------------------------------

const emptyModel: ResourcePermissionModel = {
   displayActions: [],
   hasOrgEdited: false,
   securityEnabled: false,
   requiresBoth: false,
   permissions: [],
   derivePermissionLabel: "",
   grantReadToAllVisible: false,
   changed: false
};

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe("SecurityActionService — scene layer", () => {
   let service: SecurityActionService;
   let httpMock: HttpTestingController;
   let dialogSpy: { open: jest.Mock };

   beforeEach(() => {
      dialogSpy = { open: jest.fn() };

      TestBed.configureTestingModule({
         imports: [HttpClientTestingModule],
         providers: [
            SecurityActionService,
            { provide: MatDialog, useValue: dialogSpy }
         ]
      });

      service = TestBed.inject(SecurityActionService);
      httpMock = TestBed.inject(HttpTestingController);
   });

   afterEach(() => {
      httpMock.verify();
   });

   // ---------------------------------------------------------------------------
   // Group 1 [Risk 2] — getActionTree
   // ---------------------------------------------------------------------------
   describe("getActionTree", () => {
      it("[Risk 2] should GET the action tree from the correct endpoint", () => {
         service.getActionTree().subscribe();

         const req = httpMock.expectOne("../api/em/security/actions");
         expect(req.request.method).toBe("GET");
         req.flush({ label: "root", folder: true, grant: false, actions: [], children: [] });
      });
   });

   // ---------------------------------------------------------------------------
   // Group 2 [Risk 3, 3, 2] + Bug — getPermissions
   // ---------------------------------------------------------------------------
   describe("getPermissions", () => {
      it("[Risk 3] should encode `*` as `%2A` in the URI and pass isGrant as a query param", () => {
         // 🔁 Regression-sensitive: `*` is a reserved character in URL paths; without encoding
         //    the server may reject the request or misroute it to a wildcard endpoint
         service.getPermissions("DASHBOARD", "folder/dashboard*", true).subscribe();

         const req = httpMock.expectOne(
            r => r.url === "../api/em/security/actions/DASHBOARD/folder/dashboard%2A" &&
                 r.params.get("isGrant") === "true"
         );
         expect(req.request.method).toBe("GET"); // (a) method
         req.flush(emptyModel);
      });

      it("[Risk 3] error: InvalidOrgException uses error.error.message; other errors use generic i18n string; dialog opened and emptyModel returned in both cases", () => {
         // 🔁 Regression-sensitive: content branch is security-sensitive — wrong message leaks or
         //    hides org-validation feedback; emptyModel fallback prevents a broken UI state

         // (a) InvalidOrgException → specific message from error body
         let result1: ResourcePermissionModel | undefined;
         service.getPermissions("DASHBOARD", "path", false).subscribe(v => { result1 = v; });
         const req1 = httpMock.expectOne("../api/em/security/actions/DASHBOARD/path?isGrant=false");
         req1.flush(
            { type: "InvalidOrgException", message: "Org is not valid" },
            { status: 403, statusText: "Forbidden" }
         );

         expect(dialogSpy.open).toHaveBeenCalledWith(
            expect.anything(),
            expect.objectContaining({ data: expect.objectContaining({ content: "Org is not valid" }) })
         ); // (a1) uses error body message
         expect(result1).toEqual(emptyModel); // (a2) graceful fallback

         dialogSpy.open.mockClear();

         // (b) any other error type → generic i18n string
         let result2: ResourcePermissionModel | undefined;
         service.getPermissions("DASHBOARD", "path", false).subscribe(v => { result2 = v; });
         const req2 = httpMock.expectOne("../api/em/security/actions/DASHBOARD/path?isGrant=false");
         req2.flush({ type: "SomeOtherError" }, { status: 500, statusText: "Server Error" });

         expect(dialogSpy.open).toHaveBeenCalledWith(
            expect.anything(),
            expect.objectContaining({
               data: expect.objectContaining({
                  content: "_#(js:em.security.orgAdmin.identityPermissionDenied)"
               })
            })
         ); // (b1) generic fallback message
         expect(result2).toEqual(emptyModel); // (b2) graceful fallback
      });

      it("[Risk 3] error.error is null → emptyModel returned without throwing (bug already fixed with `?.`)", () => {
         // 🔁 Regression-sensitive: getPermissions uses error.error?.type (optional chaining, line 55);
         //    removing the `?` would reintroduce the TypeError on null-body errors
         let result: ResourcePermissionModel | undefined;
         service.getPermissions("DASHBOARD", "path", false).subscribe(v => { result = v; });

         const req = httpMock.expectOne("../api/em/security/actions/DASHBOARD/path?isGrant=false");
         req.flush(null, { status: 500, statusText: "Internal Server Error" });

         expect(result).toEqual(emptyModel); // recovers gracefully; dialog shows generic i18n message
      });

      it("[Risk 2] should return the server response when the request succeeds", () => {
         const serverModel: ResourcePermissionModel = { ...emptyModel, securityEnabled: true };

         let result: ResourcePermissionModel | undefined;
         service.getPermissions("DASHBOARD", "path", true).subscribe(v => { result = v; });

         const req = httpMock.expectOne("../api/em/security/actions/DASHBOARD/path?isGrant=true");
         req.flush(serverModel);

         expect(result).toEqual(serverModel);
      });
   });

   // ---------------------------------------------------------------------------
   // Group 3 [Risk 3, 3, 2] + Bug — setPermissions
   // ---------------------------------------------------------------------------
   describe("setPermissions", () => {
      it("[Risk 3] should POST to the correct URI with the permissions body and isGrant param; `*` encoded as `%2A`", () => {
         // 🔁 Regression-sensitive: body must reach the server intact — missing or mangled body
         //    silently clears all permissions on the server side with no visible error
         const payload: ResourcePermissionModel = { ...emptyModel, securityEnabled: true };

         service.setPermissions("DASHBOARD", "folder/dashboard*", true, payload).subscribe();

         const req = httpMock.expectOne(
            r => r.url === "../api/em/security/actions/DASHBOARD/folder/dashboard%2A" &&
                 r.params.get("isGrant") === "true"
         );
         expect(req.request.method).toBe("POST"); // (a) method
         expect(req.request.body).toEqual(payload); // (b) body passed through unchanged
         req.flush(payload);
      });

      it("[Risk 3] error: InvalidOrgException uses error.error.message; other errors use generic i18n string; dialog opened and emptyModel returned in both cases", () => {
         // 🔁 Regression-sensitive: same catchError pattern as getPermissions — both branches must
         //    be present; losing the generic-string branch silently exposes raw error internals

         // (a) InvalidOrgException → specific message
         let result1: ResourcePermissionModel | undefined;
         service.setPermissions("DASHBOARD", "path", false, emptyModel).subscribe(v => { result1 = v; });
         const req1 = httpMock.expectOne("../api/em/security/actions/DASHBOARD/path?isGrant=false");
         req1.flush(
            { type: "InvalidOrgException", message: "Org invalid" },
            { status: 403, statusText: "Forbidden" }
         );

         expect(dialogSpy.open).toHaveBeenCalledWith(
            expect.anything(),
            expect.objectContaining({ data: expect.objectContaining({ content: "Org invalid" }) })
         ); // (a1)
         expect(result1).toEqual(emptyModel); // (a2)

         dialogSpy.open.mockClear();

         // (b) generic error
         let result2: ResourcePermissionModel | undefined;
         service.setPermissions("DASHBOARD", "path", false, emptyModel).subscribe(v => { result2 = v; });
         const req2 = httpMock.expectOne("../api/em/security/actions/DASHBOARD/path?isGrant=false");
         req2.flush({ type: "AnotherError" }, { status: 500, statusText: "Server Error" });

         expect(dialogSpy.open).toHaveBeenCalledWith(
            expect.anything(),
            expect.objectContaining({
               data: expect.objectContaining({
                  content: "_#(js:em.security.orgAdmin.identityPermissionDenied)"
               })
            })
         ); // (b1)
         expect(result2).toEqual(emptyModel); // (b2)
      });

      it("[Risk 3] error.error is null → emptyModel returned without throwing (bug already fixed with `?.`)", () => {
         // 🔁 Regression-sensitive: setPermissions uses error.error?.type (line 80);
         //    removing the `?` would reintroduce the TypeError on null-body errors
         let result: ResourcePermissionModel | undefined;
         service.setPermissions("DASHBOARD", "path", false, emptyModel).subscribe(v => { result = v; });

         const req = httpMock.expectOne("../api/em/security/actions/DASHBOARD/path?isGrant=false");
         req.flush(null, { status: 500, statusText: "Internal Server Error" });

         expect(result).toEqual(emptyModel); // recovers gracefully; dialog shows generic i18n message
      });

      it("[Risk 2] should return the server response when the POST succeeds", () => {
         const savedModel: ResourcePermissionModel = { ...emptyModel, securityEnabled: true };

         let result: ResourcePermissionModel | undefined;
         service.setPermissions("DASHBOARD", "path", false, emptyModel).subscribe(v => { result = v; });

         const req = httpMock.expectOne("../api/em/security/actions/DASHBOARD/path?isGrant=false");
         req.flush(savedModel);

         expect(result).toEqual(savedModel);
      });
   });
});
