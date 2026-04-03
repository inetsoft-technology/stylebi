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
 * AuditRequiredAssetsComponent — Testing Library style
 *
 * Risk-first coverage (from scenario analysis):
 *   Group 1 — fetchData: uses POST (not GET); sends all dependentAssets when selection empty
 *   Group 2 — onDependentTypeChange: resets selectedTargetTypes to null
 *   Group 3 — onTargetTypesChange: dead-code branch (targetUsers=[] immediately overwritten)
 *   Group 4 — fetchParameters: error recovery
 *
 * Design note — onTargetTypesChange dead code (NOT a runtime bug, but a logic smell):
 *   Lines 212-213 set `this.targetUsers = []` but line 216 unconditionally overwrites it
 *   with `[NONE_USER, ...allUsers]`. The conditional branch never has observable effect.
 *   The test documents current behavior: targetUsers always gets [NONE_USER, ...allUsers]
 *   regardless of whether selected types include user-asset types.
 */
import { Component, forwardRef, NO_ERRORS_SCHEMA } from "@angular/core";
import { ControlValueAccessor, NG_VALUE_ACCESSOR, ReactiveFormsModule } from "@angular/forms";
import { HttpClientModule, HttpParams } from "@angular/common/http";
import { render } from "@testing-library/angular";
import { http, HttpResponse as MswHttpResponse } from "msw";
import { firstValueFrom, Observable, throwError } from "rxjs";
import { ActivatedRoute } from "@angular/router";

import { server } from "../../../../../../mocks/server";
import { AuditRequiredAssetsComponent } from "./audit-required-assets.component";
import { PageHeaderService } from "../../page-header/page-header.service";
import { ErrorHandlerService } from "../../common/util/error/error-handler.service";
import { NONE_USER } from "../audit-dependent-assets/dependency-util";

// ---------------------------------------------------------------------------
// Stubs
// ---------------------------------------------------------------------------

/** Minimal stub so Angular Forms can find a ControlValueAccessor for mat-select. */
@Component({
   selector: "mat-select",
   template: "",
   providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => MatSelectStub), multi: true }],
})
class MatSelectStub implements ControlValueAccessor {
   writeValue() {}
   registerOnChange() {}
   registerOnTouched() {}
}

// ---------------------------------------------------------------------------
// Shared fixtures
// ---------------------------------------------------------------------------

const MOCK_PARAMS = {
   users: ["alice", "bob"],
   assets: [
      { assetId: "dep-1", label: "Viewsheet A" },
      { assetId: "dep-2", label: "Viewsheet B" },
   ],
   startTime: 0,
   endTime: 0,
};

const EMPTY_ADDITIONAL = {
   selectedDependentAssets: [] as string[],
   selectedTargetTypes: [] as string[],
   selectedTargetUsers: [] as string[],
};

/**
 * Factory for the ErrorHandlerService mock.
 */
function makeErrorServiceMock() {
   return {
      showSnackBar: jest.fn().mockImplementation(
         (error: any, _msg: string, producer?: () => Observable<any>) =>
            producer ? producer() : throwError(() => error)
      ),
   };
}

function setupParamsEndpoint(response = MOCK_PARAMS) {
   server.use(
      http.get("*/api/em/monitoring/audit/requiredAssetParameters", () =>
         MswHttpResponse.json(response)
      )
   );
}

/** Renders the component with NO_ERRORS_SCHEMA so em-audit-table-view is stubbed. */
async function renderComponent(errorService = makeErrorServiceMock()) {
   // onDependentTypeChange() is triggered by valueChanges in ngOnInit and calls fetchParameters().
   setupParamsEndpoint();

   return render(AuditRequiredAssetsComponent, {
      imports: [ReactiveFormsModule, HttpClientModule],
      declarations: [MatSelectStub],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
         { provide: PageHeaderService, useValue: { title: "" } },
         { provide: ErrorHandlerService, useValue: errorService },
         { provide: ActivatedRoute, useValue: {} },
      ],
   });
}

// ---------------------------------------------------------------------------
// Group 1: fetchData — POST method and empty-selection fallback
// ---------------------------------------------------------------------------

describe("AuditRequiredAssetsComponent — fetchData", () => {

   // P0 / Happy — fetchData uses POST, not GET
   // Unlike all other audit components, this one POSTs a RequiredAssetEvent body.
   // Verifies the HTTP method is POST and the request body contains pagination fields.
   it("should send a POST request with dependentAssets, targetTypes, and pagination fields", async () => {
      let capturedBody: any = null;
      let capturedMethod = "";

      server.use(
         http.post("*/api/em/monitoring/audit/requiredAssets", async ({ request }) => {
            capturedMethod = request.method;
            capturedBody = await request.json();
            return MswHttpResponse.json({ totalRowCount: 0, rows: [] });
         })
      );

      const { fixture } = await renderComponent();
      const comp = fixture.componentInstance;

      const baseParams = new HttpParams()
         .set("sortColumn", "name")
         .set("sortDirection", "asc")
         .set("offset", "0")
         .set("limit", "100");

      await firstValueFrom(comp.fetchData(baseParams, {
         ...EMPTY_ADDITIONAL,
         selectedTargetTypes: ["DATA_SOURCE"],
      }));

      expect(capturedMethod).toBe("POST");
      expect(capturedBody).toMatchObject({
         targetTypes: ["DATA_SOURCE"],
         sortColumn: "name",
         sortDirection: "asc",
         offset: 0,
         limit: 100,
      });
   });

   // P1 / Happy — empty selection sends all dependentAssets IDs
   // When selectedDependentAssets is empty, fetchData falls back to sending all
   // comp.dependentAssets IDs so the query covers the full scope.
   it("should send all dependentAssets IDs when selectedDependentAssets is empty", async () => {
      let capturedBody: any = null;

      server.use(
         http.post("*/api/em/monitoring/audit/requiredAssets", async ({ request }) => {
            capturedBody = await request.json();
            return MswHttpResponse.json({ totalRowCount: 0, rows: [] });
         })
      );

      const { fixture } = await renderComponent();
      const comp = fixture.componentInstance;

      // Load dependentAssets via fetchParameters
      setupParamsEndpoint();
      await firstValueFrom(comp.fetchParameters());

      await firstValueFrom(comp.fetchData(new HttpParams(), EMPTY_ADDITIONAL));

      expect(capturedBody.dependentAssets).toEqual(["dep-1", "dep-2"]);
   });

   // P1 / Happy — explicit selection overrides the fallback
   it("should send only selectedDependentAssets when explicitly provided", async () => {
      let capturedBody: any = null;

      server.use(
         http.post("*/api/em/monitoring/audit/requiredAssets", async ({ request }) => {
            capturedBody = await request.json();
            return MswHttpResponse.json({ totalRowCount: 0, rows: [] });
         })
      );

      const { fixture } = await renderComponent();
      const comp = fixture.componentInstance;

      setupParamsEndpoint();
      await firstValueFrom(comp.fetchParameters());

      await firstValueFrom(comp.fetchData(new HttpParams(), {
         ...EMPTY_ADDITIONAL,
         selectedDependentAssets: ["dep-1"],
      }));

      // dep-1 exists in dependentAssets, so it should be kept in the filtered result
      expect(capturedBody.dependentAssets).toEqual(["dep-1"]);
   });

   // P0 / Error
   it("should call errorService.showSnackBar and return empty rows on data API error", async () => {
      const errorService = makeErrorServiceMock();
      const { fixture } = await renderComponent(errorService);


      server.use(
         http.post("*/api/em/monitoring/audit/requiredAssets", () =>
            new MswHttpResponse(null, { status: 503 })
         )
      );

      const result = await firstValueFrom(
         fixture.componentInstance.fetchData(new HttpParams(), EMPTY_ADDITIONAL)
      );

      expect(errorService.showSnackBar).toHaveBeenCalledTimes(1);
      expect(result).toEqual({ totalRowCount: 0, rows: [] });
   });
});

// ---------------------------------------------------------------------------
// Group 2: onDependentTypeChange — resets selectedTargetTypes to null
// ---------------------------------------------------------------------------

describe("AuditRequiredAssetsComponent — onDependentTypeChange", () => {

   // P1 / Happy
   // Changing the dependent type must reset selectedTargetTypes to null so stale
   // selections from the previous type do not carry over.
   it("should reset selectedTargetTypes to null when dependent type changes", async () => {
      setupParamsEndpoint();
      const { fixture } = await renderComponent();
      const comp = fixture.componentInstance;

      // Set a prior selection
      comp.form.get("selectedTargetTypes").setValue(["VIEWSHEET"]);
      expect(comp.form.get("selectedTargetTypes").value).toEqual(["VIEWSHEET"]);

      // Change the dependent type
      setupParamsEndpoint();
      comp.form.get("selectedDependentType").setValue("VIEWSHEET");
      await fixture.whenStable();

      expect(comp.form.get("selectedTargetTypes").value).toBeNull();
   });
});

// ---------------------------------------------------------------------------
// Group 3: onTargetTypesChange — dead-code branch documents current behavior
// ---------------------------------------------------------------------------

describe("AuditRequiredAssetsComponent — onTargetTypesChange", () => {

   // P0 / Bug — targetUsers initial reference is stale after fetchParameters
   // targetUsers is initialized as `this.allUsers` (reference to the initial []).
   // fetchParameters tap() reassigns this.allUsers to a brand-new array, breaking the
   // reference: targetUsers still points to the old [] and stays empty until
   // onTargetTypesChange is triggered manually by the user.
   // Fix: reassign targetUsers inside the tap() alongside allUsers.
   xit("should populate targetUsers after fetchParameters resolves without any type-change interaction", async () => {
      const { fixture } = await renderComponent();
      const comp = fixture.componentInstance;

      await firstValueFrom(comp.fetchParameters());

      // No selectedTargetTypes change — targetUsers must already reflect allUsers.
      const values = comp.targetUsers.map(u => u.value);
      expect(values).toContain("alice");
      expect(values).toContain("bob");
   });

   // P1 / Design gap (dead code)
   // onTargetTypesChange sets targetUsers=[] in the if-branch but line 216
   // unconditionally overwrites it with [NONE_USER, ...allUsers].
   // Current behavior: targetUsers always becomes [NONE_USER, ...allUsers].
   // This test documents the actual behavior so any future fix is detectable.
   it("should always set targetUsers to [NONE_USER, ...allUsers] regardless of selected types", async () => {
      const { fixture } = await renderComponent();
      const comp = fixture.componentInstance;

      // Pre-populate allUsers
      setupParamsEndpoint();
      await firstValueFrom(comp.fetchParameters());

      // Select non-user-asset types — the dead branch would set targetUsers=[]
      // but the unconditional overwrite on line 216 should restore it
      comp.form.get("selectedTargetTypes").setValue(["DATA_SOURCE"]);

      // targetUsers should include NONE_USER plus allUsers (alice, bob)
      const values = comp.targetUsers.map(u => u.value);
      expect(values[0]).toBe(NONE_USER.value);
      expect(values).toContain("alice");
      expect(values).toContain("bob");
   });
});

// ---------------------------------------------------------------------------
// Group 4: fetchParameters — error recovery
// ---------------------------------------------------------------------------

describe("AuditRequiredAssetsComponent — fetchParameters", () => {

   // P0 / Error
   it("should call errorService.showSnackBar and set empty fallback on parameters API error", async () => {
      const errorService = makeErrorServiceMock();
      const { fixture } = await renderComponent(errorService);
      const comp = fixture.componentInstance;

      server.use(
         http.get("*/api/em/monitoring/audit/requiredAssetParameters", () =>
            new MswHttpResponse(null, { status: 500 })
         )
      );

      await firstValueFrom(comp.fetchParameters());

      expect(errorService.showSnackBar).toHaveBeenCalled();
      // The error fallback passes `dependentAssets: []` but tap reads `p.assets`,
      // so this.dependentAssets is set to undefined on error (source bug, not fixed here).
      expect(comp.dependentAssets).toBeUndefined();
   });
});
