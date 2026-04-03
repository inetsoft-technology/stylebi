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
 * AuditDependentAssetsComponent — Testing Library style
 *
 * Risk-first coverage (from scenario analysis):
 *   Group 1 — onTargetTypeChange: USER_ASSET_TYPES gate for targetUsers expansion
 *   Group 2 — onDependentTypesChange: dependentUsers reset logic
 *   Group 3 — fetchData: fallback to all targetAssets when selectedTargetAssets is empty
 *   Group 4 — fetchParameters: reads form state to build HTTP params
 */
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { ReactiveFormsModule } from "@angular/forms";
import { HttpClientModule, HttpParams } from "@angular/common/http";
import { render } from "@testing-library/angular";
import { http, HttpResponse as MswHttpResponse } from "msw";
import { firstValueFrom } from "rxjs";
import { ActivatedRoute } from "@angular/router";
import { MatSelectStub, makeErrorServiceMock } from "../testing/audit-test-utils";

import { it } from "@jest/globals";
import { server } from "../../../../../../mocks/server";
import { AuditDependentAssetsComponent } from "./audit-dependent-assets.component";
import { PageHeaderService } from "../../page-header/page-header.service";
import { ErrorHandlerService } from "../../common/util/error/error-handler.service";
import { NONE_USER } from "./dependency-util";

// ---------------------------------------------------------------------------
// Shared fixtures
// ---------------------------------------------------------------------------

/** Parameters response: users and assets loaded into the component. */
const MOCK_PARAMS = {
   users: ["alice", "bob"],
   assets: [
      { assetId: "asset-1", label: "Report A" },
      { assetId: "asset-2", label: "Report B" },
   ],
   startTime: 0,
   endTime: 0,
};

const EMPTY_ADDITIONAL = {
   selectedTargetAssets: [] as string[],
   selectedDependentTypes: [] as string[],
   selectedDependentUsers: [] as string[],
};


/**
 * Sets up the parameters endpoint before rendering so that
 * onTargetTypeChange() (triggered during ngOnInit's valueChanges subscription) succeeds.
 */
function setupParamsEndpoint(response = MOCK_PARAMS) {
   server.use(
      http.get("*/api/em/monitoring/audit/dependentAssetParameters", () =>
         MswHttpResponse.json(response)
      )
   );
}

/** Renders the component with NO_ERRORS_SCHEMA so em-audit-table-view is stubbed. */
async function renderComponent(errorService = makeErrorServiceMock()) {
   // ngOnInit fires onTargetTypeChange() via valueChanges, which calls fetchParameters().
   // Set up the handler before rendering.
   setupParamsEndpoint();

   return render(AuditDependentAssetsComponent, {
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
// Group 1: onTargetTypeChange — USER_ASSET_TYPES gate for targetUsers
// ---------------------------------------------------------------------------

describe("AuditDependentAssetsComponent — onTargetTypeChange", () => {

   // P1 / Happy
   // USER_ASSET_TYPES = Set{"VIEWSHEET","WORKSHEET"}.
   // Selecting VIEWSHEET (a user asset type) must populate targetUsers with allUsers.
   // allUsers is set by fetchParameters tap() which maps users to {value,label} entries.
   it("should expand targetUsers to include allUsers when target type is VIEWSHEET", async () => {
      const { fixture } = await renderComponent();
      const comp = fixture.componentInstance;

      // Load allUsers first via fetchParameters
      await firstValueFrom(comp.fetchParameters());

      // Switch target type to VIEWSHEET (a USER_ASSET_TYPE)
      setupParamsEndpoint();
      comp.form.get("selectedTargetType").setValue("VIEWSHEET");
      // Allow any async subscription work to settle
      await fixture.whenStable();

      // targetUsers must include more than just NONE_USER
      const values = comp.targetUsers.map(u => u.value);
      expect(values).toContain("alice");
      expect(values).toContain("bob");
   });

   // P1 / Happy
   // Non-user asset type (DATA_SOURCE) must collapse targetUsers to [NONE_USER] only.
   it("should collapse targetUsers to [NONE_USER] when target type is DATA_SOURCE", async () => {
      const { fixture } = await renderComponent();
      const comp = fixture.componentInstance;

      // Load users first, then switch to a non-user type
      await firstValueFrom(comp.fetchParameters());
      setupParamsEndpoint();
      comp.form.get("selectedTargetType").setValue("DATA_SOURCE");
      await fixture.whenStable();

      expect(comp.targetUsers).toEqual([NONE_USER]);
   });
});

// ---------------------------------------------------------------------------
// Group 2: onDependentTypesChange — dependentUsers reset logic
// ---------------------------------------------------------------------------

describe("AuditDependentAssetsComponent — onDependentTypesChange", () => {

   // P1 / Happy
   // Selecting only non-user dependent types (e.g. DATA_SOURCE) should set
   // dependentUsers to [NONE_USER] because none of them are in USER_ASSET_TYPES.
   it("should set dependentUsers to [NONE_USER] when no selected dependent type is a user-asset type", async () => {
      const { fixture } = await renderComponent();
      const comp = fixture.componentInstance;

      comp.form.get("selectedDependentTypes").setValue(["DATA_SOURCE"]);

      expect(comp.dependentUsers).toEqual([NONE_USER]);
   });

   // P0 / Bug #74453 — dependentUsers initial reference is stale after fetchParameters
   // dependentUsers is initialized as `this.allUsers` (reference to the initial []).
   // fetchParameters tap() reassigns this.allUsers to a brand-new array, breaking the
   // reference: dependentUsers still points to the old [] and stays empty until
   // onDependentTypesChange is triggered manually by the user.
   // Fix: reassign dependentUsers inside the tap() alongside allUsers.
   it.failing("should populate dependentUsers after fetchParameters resolves without any type-change interaction", async () => {
      const { fixture } = await renderComponent();
      const comp = fixture.componentInstance;

      await firstValueFrom(comp.fetchParameters());

      // No selectedDependentTypes change — dependentUsers must already reflect allUsers.
      const values = comp.dependentUsers.map(u => u.value);
      expect(values).toContain("alice");
      expect(values).toContain("bob");
   });

   // P1 / Happy
   // Selecting a user-asset type (VIEWSHEET) should restore dependentUsers to allUsers.
   it("should restore dependentUsers to allUsers when a user-asset dependent type is selected", async () => {
      const { fixture } = await renderComponent();
      const comp = fixture.componentInstance;

      // Pre-populate allUsers
      await firstValueFrom(comp.fetchParameters());

      // First collapse to NONE_USER
      comp.form.get("selectedDependentTypes").setValue(["DATA_SOURCE"]);
      expect(comp.dependentUsers).toEqual([NONE_USER]);

      // Now select VIEWSHEET → should restore allUsers
      comp.form.get("selectedDependentTypes").setValue(["VIEWSHEET"]);

      const values = comp.dependentUsers.map(u => u.value);
      expect(values).toContain("alice");
   });
});

// ---------------------------------------------------------------------------
// Group 3: fetchData — fallback to all targetAssets when selection is empty
// ---------------------------------------------------------------------------

describe("AuditDependentAssetsComponent — fetchData", () => {

   // P1 / Happy — empty selection sends all targetAssets
   // When selectedTargetAssets is empty, fetchData must append all this.targetAssets
   // as individual targetAssets params to cover the full scope.
   it("should send all targetAssets as params when selectedTargetAssets is empty", async () => {
      const receivedAssets: string[] = [];

      server.use(
         http.get("*/api/em/monitoring/audit/dependentAssets", ({ request }) => {
            new URL(request.url).searchParams.forEach((value, key) => {
               if(key === "targetAssets") receivedAssets.push(value);
            });
            return MswHttpResponse.json({ totalRowCount: 0, rows: [] });
         })
      );

      const { fixture } = await renderComponent();
      const comp = fixture.componentInstance;

      // Populate comp.targetAssets via fetchParameters
      setupParamsEndpoint();
      await firstValueFrom(comp.fetchParameters());

      await firstValueFrom(comp.fetchData(new HttpParams(), EMPTY_ADDITIONAL));

      expect(receivedAssets).toEqual(["asset-1", "asset-2"]);
   });

   // P1 / Happy — explicit selection overrides the fallback
   // When selectedTargetAssets is non-empty, only those assets should be sent.
   it("should send only selectedTargetAssets when they are explicitly provided", async () => {
      const receivedAssets: string[] = [];

      server.use(
         http.get("*/api/em/monitoring/audit/dependentAssets", ({ request }) => {
            new URL(request.url).searchParams.forEach((value, key) => {
               if(key === "targetAssets") receivedAssets.push(value);
            });
            return MswHttpResponse.json({ totalRowCount: 0, rows: [] });
         })
      );

      const { fixture } = await renderComponent();
      const comp = fixture.componentInstance;

      await firstValueFrom(comp.fetchData(new HttpParams(), {
         ...EMPTY_ADDITIONAL,
         selectedTargetAssets: ["asset-1"],
      }));

      expect(receivedAssets).toEqual(["asset-1"]);
   });

   // P0 / Error
   it("should call errorService.showSnackBar and return empty rows on data API error", async () => {
      const errorService = makeErrorServiceMock();
      const { fixture } = await renderComponent(errorService);


      server.use(
         http.get("*/api/em/monitoring/audit/dependentAssets", () =>
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
// Group 4: fetchParameters — reads form state to build HTTP params
// ---------------------------------------------------------------------------

describe("AuditDependentAssetsComponent — fetchParameters", () => {

   // P1 / Happy
   // fetchParameters reads selectedTargetType and selectedTargetUser from form,
   // then sends them as query params to the API.
   it("should include type and user query params derived from the current form values", async () => {
      let capturedUrl: URL | null = null;

      server.use(
         http.get("*/api/em/monitoring/audit/dependentAssetParameters", ({ request }) => {
            capturedUrl = new URL(request.url);
            return MswHttpResponse.json(MOCK_PARAMS);
         })
      );

      const { fixture } = await renderComponent();
      const comp = fixture.componentInstance;

      comp.form.get("selectedTargetType").setValue("WORKSHEET");
      comp.form.get("selectedTargetUser").setValue("alice");

      setupParamsEndpoint();
      await firstValueFrom(comp.fetchParameters());

      // Re-check with a fresh capture
      server.use(
         http.get("*/api/em/monitoring/audit/dependentAssetParameters", ({ request }) => {
            capturedUrl = new URL(request.url);
            return MswHttpResponse.json(MOCK_PARAMS);
         })
      );
      await firstValueFrom(comp.fetchParameters());

      expect(capturedUrl!.searchParams.get("type")).toBe("WORKSHEET");
      expect(capturedUrl!.searchParams.get("user")).toBe("alice");
   });

   // P0 / Error
   it("should call errorService.showSnackBar and set empty fallback on parameters API error", async () => {
      const errorService = makeErrorServiceMock();
      const { fixture } = await renderComponent(errorService);
      const comp = fixture.componentInstance;

      // Register the 500 handler AFTER renderComponent so it takes precedence over
      // the success handler that setupParamsEndpoint() registered inside renderComponent.
      server.use(
         http.get("*/api/em/monitoring/audit/dependentAssetParameters", () =>
            new MswHttpResponse(null, { status: 500 })
         )
      );

      await firstValueFrom(comp.fetchParameters());

      expect(errorService.showSnackBar).toHaveBeenCalled();
      expect(comp.targetAssets).toEqual([]);
   });
});
