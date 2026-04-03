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
 * AuditIdentityInfoComponent — Testing Library style
 *
 * Risk-first coverage (from scenario analysis):
 *   Group 1 — fetchParameters: API state binding + error recovery
 *   Group 2 — getTypes(): organizationFilter-controlled type list
 *   Group 3 — onOrganizationChange: selectedTypes cleared on org selection
 *   Group 4 — fetchData: HTTP param construction + error recovery
 *   Group 5 — Design gap: organizationId column renderer vs displayedColumns
 */
import { Component, forwardRef, NO_ERRORS_SCHEMA } from "@angular/core";
import { ControlValueAccessor, NG_VALUE_ACCESSOR, ReactiveFormsModule } from "@angular/forms";
import { HttpClientModule, HttpParams } from "@angular/common/http";
import { render } from "@testing-library/angular";
import { http, HttpResponse as MswHttpResponse } from "msw";
import { firstValueFrom, Observable, throwError } from "rxjs";
import { ActivatedRoute } from "@angular/router";

import { server } from "../../../../../../mocks/server";
import { AuditIdentityInfoComponent } from "./audit-identity-info.component";
import { PageHeaderService } from "../../page-header/page-header.service";
import { ErrorHandlerService } from "../../common/util/error/error-handler.service";
import { IdentityInfoParameters } from "./identity-info";

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

/** Minimal IdentityInfoParameters returned by the parameters API. */
const MOCK_PARAMS: IdentityInfoParameters = {
   hosts: ["srv-01"],
   organizations: [{ name: "org1", orgID: "acme" }],
   organizationFilter: true,
   systemAdministrator: false,
   startTime: 0,
   endTime: 0,
};

/** Additional form values that represent an empty filter selection. */
const EMPTY_ADDITIONAL = {
   selectedTypes: [] as string[],
   selectedActions: [] as string[],
   selectedStates: [] as string[],
   selectedHosts: [] as string[],
   selectedOrganizations: [] as string[],
};

/**
 * Factory for the ErrorHandlerService mock.
 * Mirrors the real implementation: when a resultProducer is supplied it is called
 * and its Observable is returned; otherwise the error is re-thrown.
 */
function makeErrorServiceMock() {
   return {
      showSnackBar: jest.fn().mockImplementation(
         (error: any, _msg: string, producer?: () => Observable<any>) =>
            producer ? producer() : throwError(() => error)
      ),
   };
}

/**
 * Renders the component with NO_ERRORS_SCHEMA so child components are stubbed.
 * Also registers the org-names handler so ngOnInit does not leave a dangling request.
 */
async function renderComponent(errorService = makeErrorServiceMock()) {
   // ngOnInit fires GET for org-names; return an empty list so it resolves cleanly.
   server.use(
      http.get("*/api/em/security/users/get-all-organization-names/", () =>
         MswHttpResponse.json([])
      )
   );

   return render(AuditIdentityInfoComponent, {
      imports: [ReactiveFormsModule, HttpClientModule],
      declarations: [MatSelectStub],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
         // PageHeaderService.title setter calls Title.setTitle internally.
         // Use a plain value object to avoid pulling in BrowserModule.
         { provide: PageHeaderService, useValue: { title: "" } },
         { provide: ErrorHandlerService, useValue: errorService },
         // ActivatedRoute is injected in the constructor but never accessed in any method.
         { provide: ActivatedRoute, useValue: {} },
      ],
   });
}

// ---------------------------------------------------------------------------
// Group 1: fetchParameters — API state binding + error recovery
// ---------------------------------------------------------------------------

describe("AuditIdentityInfoComponent — fetchParameters", () => {

   // P0 / Happy
   // fetchParameters is an arrow function; the tap() operator updates organizations,
   // organizationFilter, and hosts as side effects of the Observable pipeline.
   // The types field is static and is NOT updated by the API response.
   it("should populate organizations, organizationFilter, and hosts from API response", async () => {
      server.use(
         http.get("*/api/em/monitoring/audit/identityInfoParameters", () =>
            MswHttpResponse.json(MOCK_PARAMS)
         )
      );

      const { fixture } = await renderComponent();
      const comp = fixture.componentInstance;

      await firstValueFrom(comp.fetchParameters());

      expect(comp.organizations).toEqual(MOCK_PARAMS.organizations);
      expect(comp.organizationFilter).toBe(true);
      expect(comp.hosts).toEqual(MOCK_PARAMS.hosts);
   });

   // P0 / Error
   // errorService.showSnackBar is called with a resultProducer that returns of(fallback).
   // The catchError feeds the fallback through tap(), resetting all lists to empty.
   it("should call errorService.showSnackBar and set empty fallback state on API error", async () => {
      const errorService = makeErrorServiceMock();
      const { fixture } = await renderComponent(errorService);

      server.use(
         http.get("*/api/em/monitoring/audit/identityInfoParameters", () =>
            new MswHttpResponse(null, { status: 500 })
         )
      );

      const comp = fixture.componentInstance;

      await firstValueFrom(comp.fetchParameters());

      // Error handler must be notified
      expect(errorService.showSnackBar).toHaveBeenCalledTimes(1);

      // Fallback state: all lists empty, organizationFilter defaults to true
      expect(comp.organizations).toEqual([]);
      expect(comp.hosts).toEqual([]);
      expect(comp.organizationFilter).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 2: getTypes() — organizationFilter-controlled type list
// ---------------------------------------------------------------------------

describe("AuditIdentityInfoComponent — getTypes()", () => {

   // P1 / Happy
   // When organizationFilter is true, getTypes() returns all 4 types including "o" (Organization).
   // The Organization type is only meaningful when multi-tenancy / org filtering is active.
   it("should return 4 types (u, g, r, o) when organizationFilter is true", async () => {
      const { fixture } = await renderComponent();
      const comp = fixture.componentInstance;

      comp.organizationFilter = true;

      expect(comp.getTypes()).toEqual(["u", "g", "r", "o"]);
   });

   // P1 / Happy
   // When organizationFilter is false (single-tenant mode), getTypes() omits "o" (Organization)
   // because org-level identity records do not exist.
   it("should return 3 types (u, g, r) when organizationFilter is false", async () => {
      const { fixture } = await renderComponent();
      const comp = fixture.componentInstance;

      comp.organizationFilter = false;

      expect(comp.getTypes()).toEqual(["u", "g", "r"]);
   });
});

// ---------------------------------------------------------------------------
// Group 3: onOrganizationChange — selectedTypes cleared on org selection
// ---------------------------------------------------------------------------

describe("AuditIdentityInfoComponent — onOrganizationChange", () => {

   // P1 / Happy
   // onOrganizationChange is private and is triggered by the selectedOrganizations valueChanges
   // subscription set up in ngOnInit. When a non-null/non-empty org value is set, the private
   // handler clears selectedTypes to null to avoid stale filter combinations.
   it("should set selectedTypes to null when selectedOrganizations is changed to a non-null value", async () => {
      const { fixture } = await renderComponent();
      const comp = fixture.componentInstance;

      // Pre-set a types selection to verify it is cleared
      comp.form.get("selectedTypes")!.setValue(["u", "g"]);

      // Trigger the subscription by changing the selectedOrganizations form control
      comp.form.get("selectedOrganizations")!.setValue(["acme"]);

      expect(comp.form.get("selectedTypes")!.value).toBeNull();
   });
});

// ---------------------------------------------------------------------------
// Group 4: fetchData — HTTP param construction + error recovery
// ---------------------------------------------------------------------------

describe("AuditIdentityInfoComponent — fetchData", () => {

   // P1 / Happy
   // HttpParams.append() must be used (not set()) so each type value becomes a separate
   // query param entry. Using set() would overwrite — only the last value would reach the server.
   it("should append each selected type as a separate query param entry", async () => {
      const receivedTypes: string[] = [];

      server.use(
         http.get("*/api/em/monitoring/audit/identityInfo", ({ request }) => {
            const url = new URL(request.url);
            url.searchParams.forEach((value, key) => {
               if(key === "types") receivedTypes.push(value);
            });
            return MswHttpResponse.json({ totalRowCount: 0, rows: [] });
         })
      );

      const { fixture } = await renderComponent();
      await firstValueFrom(fixture.componentInstance.fetchData(new HttpParams(), {
         ...EMPTY_ADDITIONAL,
         selectedTypes: ["u", "g"],
      }));

      expect(receivedTypes).toEqual(["u", "g"]);
   });

   // P0 / Error
   // fetchData's catchError must call errorService.showSnackBar and return a safe empty list.
   // Without this, a data fetch failure would leave the table in an indefinitely loading state.
   it("should call errorService.showSnackBar and return empty rows on data API error", async () => {
      const errorService = makeErrorServiceMock();
      const { fixture } = await renderComponent(errorService);


      server.use(
         http.get("*/api/em/monitoring/audit/identityInfo", () =>
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
// Group 5: Design gap — organizationId column renderer vs displayedColumns
// ---------------------------------------------------------------------------

describe("AuditIdentityInfoComponent — column configuration", () => {

   // P2 / Design gap
   // columnRenderers includes an 'organizationId' entry but _displayedColumns does not
   // (it contains only 7 entries: name, type, actionType, actionTime, actionDescription,
   // state, server). The displayedColumns getter returns _displayedColumns directly, so
   // the organization ID column is never rendered in the table even when organizationFilter
   // is true.
   //
   // This test documents the current state. If the column is intentionally hidden it is
   // expected to pass indefinitely. If it should be shown when organizationFilter is active,
   // this is a bug and the component needs a dynamic displayedColumns getter.
   it("should have an organizationId entry in columnRenderers but not in displayedColumns getter", async () => {
      const { fixture } = await renderComponent();
      const comp = fixture.componentInstance;

      const rendererNames = comp.columnRenderers.map(r => r.name);
      expect(rendererNames).toContain("organizationId");
      expect(comp.displayedColumns).not.toContain("organizationId");
   });
});
