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
 * AuditInactiveResourceComponent — Testing Library style
 *
 * Risk-first coverage (from scenario analysis):
 *   Group 1 — fetchParameters: API state binding, conditional maxDuration update, error recovery
 *   Group 2 — getAssetTypeLabel(): case-insensitive mapping + null safety
 *   Group 3 — maxStartDuration / minEndDuration getters: form-backed values
 *   Group 4 — fetchData: error recovery
 *   Group 5 — Design gap: organizationId column renderer vs getDisplayedColumns()
 *
 * Conditional maxDuration update contract:
 *   fetchParameters only overwrites this.maxDuration (and the form control) when BOTH:
 *     (a) the API value is strictly greater than the current this.maxDuration, AND
 *     (b) the form control value equals this.maxDuration (i.e. the user has NOT manually changed it).
 *   This prevents a server-pushed default from clobbering a user's custom duration entry.
 */
import { Component, forwardRef, NO_ERRORS_SCHEMA } from "@angular/core";
import { ControlValueAccessor, NG_VALUE_ACCESSOR, ReactiveFormsModule } from "@angular/forms";
import { HttpClientModule, HttpParams } from "@angular/common/http";
import { render } from "@testing-library/angular";
import { http, HttpResponse as MswHttpResponse } from "msw";
import { firstValueFrom, Observable, throwError } from "rxjs";
import { ActivatedRoute } from "@angular/router";

import { server } from "../../../../../../mocks/server";
import { AuditInactiveResourceComponent } from "./audit-inactive-resource.component";
import { PageHeaderService } from "../../page-header/page-header.service";
import { ErrorHandlerService } from "../../common/util/error/error-handler.service";
import { InactiveResourceParameters } from "./inactive-resource";

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

/** Minimal InactiveResourceParameters returned by the parameters API. */
const MOCK_PARAMS = {
   objectTypes: [{ value: "DASHBOARD", label: "Dashboard" }],
   hosts: ["srv-01"],
   organizationNames: ["Acme"],
   organizationFilter: true,
   maxDuration: 90,
   startTime: 0,
   endTime: 0,
};

/** Additional form values that represent an empty/default filter selection. */
const EMPTY_ADDITIONAL = {
   selectedTypes: [] as string[],
   selectedHosts: [] as string[],
   minDuration: 0,
   maxDuration: 30,
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
 * Also registers handlers for the two ngOnInit GET calls so they resolve cleanly.
 */
async function renderComponent(errorService = makeErrorServiceMock()) {
   // ngOnInit fires two GETs; return safe defaults so they resolve without side effects.
   server.use(
      http.get("*/api/em/security/users/get-all-organization-names/", () =>
         MswHttpResponse.json([])
      ),
      http.get("*/api/em/navbar/isSiteAdmin", () =>
         MswHttpResponse.json(false)
      )
   );

   return render(AuditInactiveResourceComponent, {
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
// Group 1: fetchParameters — API state binding + conditional maxDuration + error recovery
// ---------------------------------------------------------------------------

describe("AuditInactiveResourceComponent — fetchParameters", () => {

   // P0 / Happy
   // fetchParameters is an arrow function; the tap() operator updates objectTypes, hosts,
   // organizationNames, and organizationFilter as side effects of the Observable pipeline.
   it("should populate objectTypes, hosts, organizationNames, and organizationFilter from API response", async () => {
      server.use(
         http.get("*/api/em/monitoring/audit/inactiveResourceParameters", () =>
            MswHttpResponse.json(MOCK_PARAMS)
         )
      );

      const { fixture } = await renderComponent();
      const comp = fixture.componentInstance;

      await firstValueFrom(comp.fetchParameters());

      expect(comp.objectTypes).toEqual(MOCK_PARAMS.objectTypes);
      expect(comp.hosts).toEqual(MOCK_PARAMS.hosts);
      expect(comp.organizationNames).toEqual(MOCK_PARAMS.organizationNames);
      expect(comp.organizationFilter).toBe(true);
   });

   // P1 / Happy — conditional maxDuration update
   // When the API returns maxDuration (90) > component.maxDuration (30) AND the form control
   // still equals the component field (meaning the user has not manually changed it),
   // both this.maxDuration and the form control should be updated to 90.
   it("should update maxDuration and the form control when API value is larger and form has not been manually changed", async () => {
      server.use(
         http.get("*/api/em/monitoring/audit/inactiveResourceParameters", () =>
            MswHttpResponse.json(MOCK_PARAMS)
         )
      );

      const { fixture } = await renderComponent();
      const comp = fixture.componentInstance;

      // Default state: this.maxDuration === 30, form.maxDuration === 30 (both at default)
      expect(comp.maxDuration).toBe(30);
      expect(comp.form.get("maxDuration")!.value).toBe(30);

      await firstValueFrom(comp.fetchParameters());

      // API returned 90 > 30, and form was at default — both must be updated
      expect(comp.maxDuration).toBe(90);
      expect(comp.form.get("maxDuration")!.value).toBe(90);
   });

   // P1 / Boundary — conditional maxDuration update NOT applied when form was manually changed
   // If the user has set the form control to a value different from this.maxDuration (e.g. 50),
   // the API-pushed default must NOT overwrite the user's explicit choice.
   it("should NOT update maxDuration when form maxDuration has been manually changed", async () => {
      server.use(
         http.get("*/api/em/monitoring/audit/inactiveResourceParameters", () =>
            MswHttpResponse.json(MOCK_PARAMS)
         )
      );

      const { fixture } = await renderComponent();
      const comp = fixture.componentInstance;

      // Simulate user manually typing 50 into the maxDuration field.
      // Now form.maxDuration (50) != this.maxDuration (30), so the guard condition fails.
      comp.form.get("maxDuration")!.setValue(50);

      await firstValueFrom(comp.fetchParameters());

      // this.maxDuration must remain 30; form must remain 50 (user's value preserved)
      expect(comp.maxDuration).toBe(30);
      expect(comp.form.get("maxDuration")!.value).toBe(50);
   });

   // P0 / Error
   // errorService.showSnackBar is called with a resultProducer that returns of(fallback).
   // The catchError feeds the fallback through tap(), resetting all lists to empty.
   it("should call errorService.showSnackBar and set empty fallback state on API error", async () => {
      const errorService = makeErrorServiceMock();
      const { fixture } = await renderComponent(errorService);

      server.use(
         http.get("*/api/em/monitoring/audit/inactiveResourceParameters", () =>
            new MswHttpResponse(null, { status: 500 })
         )
      );

      const comp = fixture.componentInstance;

      await firstValueFrom(comp.fetchParameters());

      // Error handler must be notified
      expect(errorService.showSnackBar).toHaveBeenCalledTimes(1);

      // Fallback state: all lists empty, organizationFilter defaults to true
      expect(comp.objectTypes).toEqual([]);
      expect(comp.hosts).toEqual([]);
      expect(comp.organizationNames).toEqual([]);
      expect(comp.organizationFilter).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 2: getAssetTypeLabel() — case-insensitive mapping + null safety
// ---------------------------------------------------------------------------

describe("AuditInactiveResourceComponent — getAssetTypeLabel()", () => {

   // P1 / Happy
   // The method normalizes via value?.toLowerCase() before comparing, so "DASHBOARD",
   // "Dashboard", and "dashboard" must all map to the same i18n token.
   it("should map 'DASHBOARD' case-insensitively to the Dashboard label", async () => {
      const { fixture } = await renderComponent();
      const comp = fixture.componentInstance;

      expect(comp.getAssetTypeLabel("DASHBOARD")).toBe("_#(js:Dashboard)");
      expect(comp.getAssetTypeLabel("Dashboard")).toBe("_#(js:Dashboard)");
      expect(comp.getAssetTypeLabel("dashboard")).toBe("_#(js:Dashboard)");
   });

   // P1 / Boundary — null safety via optional chaining
   // value?.toLowerCase() produces undefined for null, which does not match any branch,
   // so the else clause returns the original value (null). The ?. guard prevents a
   // TypeError that would crash the column renderer when a row has a null objectType.
   it("should return null safely when value is null (no TypeError thrown)", async () => {
      const { fixture } = await renderComponent();
      const comp = fixture.componentInstance;

      // Cast to any to bypass TypeScript's strict null check — runtime behaviour is what matters.
      expect(comp.getAssetTypeLabel(null as any)).toBeNull();
   });
});

// ---------------------------------------------------------------------------
// Group 3: maxStartDuration / minEndDuration getters — form-backed values
// ---------------------------------------------------------------------------

describe("AuditInactiveResourceComponent — duration getters", () => {

   // P1
   // maxStartDuration reads from the form control, NOT from this.maxDuration.
   // This is intentional: the slider upper bound for the start-time range follows
   // whatever the user typed into the maxDuration field.
   it("should return the form's maxDuration value from maxStartDuration getter", async () => {
      const { fixture } = await renderComponent();
      const comp = fixture.componentInstance;

      comp.form.get("maxDuration")!.setValue(60);

      expect(comp.maxStartDuration).toBe(60);
   });

   // P1
   // minEndDuration reads from the form control, NOT from this.minDuration.
   // Note: this differs from audit-inactive-user which uses Math.min. Here the getter
   // simply returns the form value (or 0 if falsy).
   it("should return the form's minDuration value from minEndDuration getter", async () => {
      const { fixture } = await renderComponent();
      const comp = fixture.componentInstance;

      comp.form.get("minDuration")!.setValue(15);

      expect(comp.minEndDuration).toBe(15);
   });
});

// ---------------------------------------------------------------------------
// Group 4: fetchData — error recovery
// ---------------------------------------------------------------------------

describe("AuditInactiveResourceComponent — fetchData", () => {

   // P0 / Error
   // fetchData's catchError must call errorService.showSnackBar and return a safe empty list.
   // Without this, a data fetch failure would leave the table in an indefinitely loading state.
   it("should call errorService.showSnackBar and return empty rows on data API error", async () => {
      const errorService = makeErrorServiceMock();
      const { fixture } = await renderComponent(errorService);


      server.use(
         http.get("*/api/em/monitoring/audit/inactiveResource", () =>
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
// Group 5: Design gap — organizationId column renderer vs getDisplayedColumns()
// ---------------------------------------------------------------------------

describe("AuditInactiveResourceComponent — column configuration", () => {

   // P2 / Design gap
   // columnRenderers includes an 'organizationId' entry (line 73) but the displayedColumns
   // array does not (it contains only: objectName, objectType, lastAccessTime, duration, server).
   // getDisplayedColumns() returns this array directly, so the organization ID column is never
   // rendered in the table even when organizationFilter is true.
   //
   // This test documents the current state. If the column is intentionally hidden it is
   // expected to pass indefinitely. If it should be shown when organizationFilter is active,
   // this is a bug and the component needs a dynamic getDisplayedColumns() that inserts
   // 'organizationId' conditionally.
   it("should have an organizationId entry in columnRenderers but not in getDisplayedColumns()", async () => {
      const { fixture } = await renderComponent();
      const comp = fixture.componentInstance;

      const rendererNames = comp.columnRenderers.map(r => r.name);
      expect(rendererNames).toContain("organizationId");
      expect(comp.getDisplayedColumns()).not.toContain("organizationId");
   });
});
