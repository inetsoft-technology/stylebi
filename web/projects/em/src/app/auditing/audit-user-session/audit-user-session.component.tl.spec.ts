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
 * AuditUserSessionComponent — Testing Library style
 *
 * Risk-first coverage (from scenario analysis):
 *   Group 1 — fetchParameters: API state binding, maxDuration logic, error recovery
 *   Group 2 — fetchData: HTTP param construction, duration falsy guard
 *   Group 3 — maxStartDuration / minEndDuration: slider bound computed properties
 *   Group 4 — Design gap: organizationId column renderer vs displayedColumns
 *
 * maxDuration update logic in fetchParameters:
 *   The component only updates this.maxDuration (and the form control) when two
 *   conditions are BOTH true:
 *     1. params.maxDuration > this.maxDuration (API value exceeds current default)
 *     2. this.form.get("maxDuration").value == this.maxDuration (form not yet changed)
 *   If the user has already moved the slider, the API value is ignored.
 *
 * fetchData duration params:
 *   minDuration and maxDuration are only appended when the form value is truthy
 *   (non-zero). This means duration 0 is treated as "no filter" and omitted.
 */
import { Component, forwardRef, NO_ERRORS_SCHEMA } from "@angular/core";
import { ControlValueAccessor, NG_VALUE_ACCESSOR, ReactiveFormsModule } from "@angular/forms";
import { HttpClientModule, HttpParams } from "@angular/common/http";
import { render } from "@testing-library/angular";
import { http, HttpResponse as MswHttpResponse } from "msw";
import { firstValueFrom, Observable, throwError } from "rxjs";
import { ActivatedRoute } from "@angular/router";

import { server } from "../../../../../../mocks/server";
import { AuditUserSessionComponent } from "./audit-user-session.component";
import { PageHeaderService } from "../../page-header/page-header.service";
import { ErrorHandlerService } from "../../common/util/error/error-handler.service";
import { UserSessionParameters } from "./user-session";

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

/** Minimal UserSessionParameters returned by the parameters API. */
const MOCK_PARAMS: UserSessionParameters = {
   users: ["alice", "bob"],
   organizations: [],
   minDuration: 0,
   maxDuration: 1200,
   systemAdministrator: true,
   startTime: 1_700_000_000_000,
   endTime:   1_700_086_400_000,
};

/** Additional form values representing an empty / default filter selection. */
const EMPTY_ADDITIONAL = {
   selectedUsers:        [] as string[],
   selectedOrganizations: [] as string[],
   minDuration: 0,
   maxDuration: 0,
};

/**
 * Factory for the ErrorHandlerService mock.
 * When a resultProducer is supplied it is called and its Observable is returned;
 * otherwise the error is re-thrown.
 */
function makeErrorServiceMock() {
   return {
      showSnackBar: jest.fn().mockImplementation(
         (error: any, _msg: string, producer?: () => Observable<any>) =>
            producer ? producer() : throwError(() => error)
      ),
   };
}

/** Renders the component with NO_ERRORS_SCHEMA so em-audit-table-view is stubbed. */
async function renderComponent(errorService = makeErrorServiceMock()) {
   return render(AuditUserSessionComponent, {
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
// Group 1: fetchParameters — API state binding
// ---------------------------------------------------------------------------

describe("AuditUserSessionComponent — fetchParameters", () => {

   // P0 / Happy
   // tap() must assign users and systemAdministrator from the API response.
   it("should populate users and systemAdministrator from API response", async () => {
      server.use(
         http.get("*/api/em/monitoring/audit/userSessionParameters", () =>
            MswHttpResponse.json(MOCK_PARAMS)
         )
      );

      const { fixture } = await renderComponent();
      const comp = fixture.componentInstance;

      await firstValueFrom(comp.fetchParameters());

      expect(comp.users).toEqual(MOCK_PARAMS.users);
      expect(comp.systemAdministrator).toBe(true);
   });

   // P0 / Happy — maxDuration update when API > default AND form at default
   // When the API returns a maxDuration larger than the initialised 600, and the
   // user has not changed the form control, both this.maxDuration and the form
   // control must be updated to the API value.
   it("should update maxDuration when API returns value greater than 600 and form is still at default", async () => {
      server.use(
         http.get("*/api/em/monitoring/audit/userSessionParameters", () =>
            MswHttpResponse.json({ ...MOCK_PARAMS, maxDuration: 1200 })
         )
      );

      const { fixture } = await renderComponent();
      const comp = fixture.componentInstance;

      // Precondition: form and component field at default value
      expect(comp.maxDuration).toBe(600);
      expect(comp.form.get("maxDuration")!.value).toBe(600);

      await firstValueFrom(comp.fetchParameters());

      expect(comp.maxDuration).toBe(1200);
      expect(comp.form.get("maxDuration")!.value).toBe(1200);
   });

   // P0 / Happy — maxDuration NOT updated when form has been changed by user
   // If the user has already adjusted the slider (form value != component default),
   // the API value must be ignored to avoid overwriting the user's choice.
   it("should NOT update maxDuration when the form control has been changed by the user", async () => {
      server.use(
         http.get("*/api/em/monitoring/audit/userSessionParameters", () =>
            MswHttpResponse.json({ ...MOCK_PARAMS, maxDuration: 1200 })
         )
      );

      const { fixture } = await renderComponent();
      const comp = fixture.componentInstance;

      // Simulate user adjusting the slider before parameters are fetched
      comp.form.get("maxDuration")!.setValue(800, { emitEvent: false });

      await firstValueFrom(comp.fetchParameters());

      // Component field must remain at default; form control must remain at user's value
      expect(comp.maxDuration).toBe(600);
      expect(comp.form.get("maxDuration")!.value).toBe(800);
   });

   // P0 / Error
   // The catchError fallback (maxDuration: 0) does not exceed the default 600,
   // so the maxDuration update branch is not triggered. users and systemAdministrator
   // must be reset to empty/false.
   it("should call errorService.showSnackBar and populate empty fallback state on API error", async () => {
      const errorService = makeErrorServiceMock();
      const { fixture } = await renderComponent(errorService);

      server.use(
         http.get("*/api/em/monitoring/audit/userSessionParameters", () =>
            new MswHttpResponse(null, { status: 500 })
         )
      );

      const comp = fixture.componentInstance;

      await firstValueFrom(comp.fetchParameters());

      expect(errorService.showSnackBar).toHaveBeenCalledTimes(1);
      expect(comp.users).toEqual([]);
      expect(comp.systemAdministrator).toBe(false);
      // maxDuration should remain at the initialised default (fallback returns 0, not > 600)
      expect(comp.maxDuration).toBe(600);
   });
});

// ---------------------------------------------------------------------------
// Group 2: fetchData — HTTP param construction
// ---------------------------------------------------------------------------

describe("AuditUserSessionComponent — fetchData", () => {

   // P0 / Error
   // catchError must call showSnackBar and return a safe empty list.
   it("should call errorService.showSnackBar and return empty rows on data API error", async () => {
      const errorService = makeErrorServiceMock();
      const { fixture } = await renderComponent(errorService);


      server.use(
         http.get("*/api/em/monitoring/audit/userSessions", () =>
            new MswHttpResponse(null, { status: 503 })
         )
      );

      const result = await firstValueFrom(
         fixture.componentInstance.fetchData(new HttpParams(), EMPTY_ADDITIONAL)
      );

      expect(errorService.showSnackBar).toHaveBeenCalledTimes(1);
      expect(result).toEqual({ totalRowCount: 0, rows: [] });
   });

   // P1 / Boundary — duration params omitted when values are 0 (falsy guard)
   // The implementation uses `if(!!duration && duration)` which treats 0 as falsy.
   // When both duration form values are 0, no duration params should be sent.
   it("should omit minDuration and maxDuration params when form values are 0", async () => {
      let capturedUrl: URL | null = null;

      server.use(
         http.get("*/api/em/monitoring/audit/userSessions", ({ request }) => {
            capturedUrl = new URL(request.url);
            return MswHttpResponse.json({ totalRowCount: 0, rows: [] });
         })
      );

      const { fixture } = await renderComponent();
      await firstValueFrom(fixture.componentInstance.fetchData(new HttpParams(), {
         ...EMPTY_ADDITIONAL,
         minDuration: 0,
         maxDuration: 0,
      }));

      expect(capturedUrl!.searchParams.has("minDuration")).toBe(false);
      expect(capturedUrl!.searchParams.has("maxDuration")).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 3: maxStartDuration / minEndDuration — slider bound computed properties
// ---------------------------------------------------------------------------

describe("AuditUserSessionComponent — slider bound getters", () => {

   // P1 / Happy — maxStartDuration expands the min-duration slider's upper bound
   // maxStartDuration = Math.max(formValue, this.maxDuration).
   // When the form's maxDuration control has a larger value than the component field,
   // the getter returns the form value so the min-duration slider can go up to it.
   it("maxStartDuration should return Math.max(form maxDuration value, this.maxDuration)", async () => {
      const { fixture } = await renderComponent();
      const comp = fixture.componentInstance;

      // Default: form=600, comp.maxDuration=600 → Math.max(600,600) = 600
      expect(comp.maxStartDuration).toBe(600);

      // Form value raised above component field → getter returns form value
      comp.form.get("maxDuration")!.setValue(900, { emitEvent: false });
      expect(comp.maxStartDuration).toBe(900);
   });

   // P1 / Happy — minEndDuration contracts the max-duration slider's lower bound
   // minEndDuration = Math.min(formValue, this.minDuration).
   // When the form's minDuration control is 0 and component minDuration is 0,
   // the lower bound is also 0. If the form value drops below the component field
   // (which starts at 0 and cannot go lower), the getter returns the form value.
   it("minEndDuration should return Math.min(form minDuration value, this.minDuration)", async () => {
      const { fixture } = await renderComponent();
      const comp = fixture.componentInstance;

      // Default: form=0, comp.minDuration=0 → Math.min(0,0) = 0
      expect(comp.minEndDuration).toBe(0);

      // If minDuration is raised on the form, Math.min(form, comp.minDuration=0) = 0
      comp.form.get("minDuration")!.setValue(100, { emitEvent: false });
      expect(comp.minEndDuration).toBe(0);
   });
});

// ---------------------------------------------------------------------------
// Group 4: Design gap — organizationId column renderer vs displayedColumns
// ---------------------------------------------------------------------------

describe("AuditUserSessionComponent — column configuration", () => {

   // P2 / Design gap
   // columnRenderers includes an 'organizationId' entry but _displayedColumns does not.
   // The displayedColumns getter always returns the static array, so the organization
   // ID column is never rendered even when multi-tenancy is active.
   it("should have an organizationId entry in columnRenderers but not in displayedColumns", async () => {
      const { fixture } = await renderComponent();
      const comp = fixture.componentInstance;

      const rendererNames = comp.columnRenderers.map(r => r.name);
      expect(rendererNames).toContain("organizationId");
      expect(comp.displayedColumns).not.toContain("organizationId");
   });
});
