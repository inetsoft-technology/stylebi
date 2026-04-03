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
 * AuditLogonHistoryComponent — Testing Library style
 *
 * Risk-first coverage (from scenario analysis):
 *   Group 1 — fetchParameters: API state binding + error recovery
 *   Group 2 — fetchData: HTTP param construction
 *   Group 3 — getRoleLabel: global-role suffix stripping
 *   Group 4 — Design gap: organizationId column renderer vs displayedColumns
 *
 * getRoleLabel suffix contract:
 *   StyleBI global roles are stored as "roleName___GLOBAL__" (11-char suffix).
 *   getRoleLabel strips exactly 11 characters from the end when the role ends
 *   with "__GLOBAL__". Other roles are returned unchanged.
 */
import { Component, forwardRef, NO_ERRORS_SCHEMA } from "@angular/core";
import { ControlValueAccessor, NG_VALUE_ACCESSOR, ReactiveFormsModule } from "@angular/forms";
import { HttpClientModule, HttpParams } from "@angular/common/http";
import { render } from "@testing-library/angular";
import { http, HttpResponse as MswHttpResponse } from "msw";
import { firstValueFrom, Observable, throwError } from "rxjs";
import { ActivatedRoute } from "@angular/router";

import { server } from "../../../../../../mocks/server";
import { AuditLogonHistoryComponent } from "./audit-logon-history.component";
import { PageHeaderService } from "../../page-header/page-header.service";
import { ErrorHandlerService } from "../../common/util/error/error-handler.service";
import { LogonHistoryParameters } from "./logon-history";

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

/** Minimal LogonHistoryParameters returned by the parameters API. */
const MOCK_PARAMS: LogonHistoryParameters = {
   users: ["alice", "bob"],
   groups: ["admins", "analysts"],
   roles: ["Admin___GLOBAL__", "Viewer"],
   organizations: [],
   systemAdministrator: true,
   startTime: 1_700_000_000_000,
   endTime:   1_700_086_400_000,
};

/** Additional form values representing an empty filter selection. */
const EMPTY_ADDITIONAL = {
   selectedUsers:  [] as string[],
   selectedGroups: [] as string[],
   selectedRoles:  [] as string[],
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
   return render(AuditLogonHistoryComponent, {
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

describe("AuditLogonHistoryComponent — fetchParameters", () => {

   // P0 / Happy
   // tap() side effects must assign users, groups, roles, and systemAdministrator.
   it("should populate users, groups, roles, and systemAdministrator from API response", async () => {
      server.use(
         http.get("*/api/em/monitoring/audit/logonHistoryParameters", () =>
            MswHttpResponse.json(MOCK_PARAMS)
         )
      );

      const { fixture } = await renderComponent();
      const comp = fixture.componentInstance;

      await firstValueFrom(comp.fetchParameters());

      expect(comp.users).toEqual(MOCK_PARAMS.users);
      expect(comp.groups).toEqual(MOCK_PARAMS.groups);
      expect(comp.roles).toEqual(MOCK_PARAMS.roles);
      expect(comp.systemAdministrator).toBe(true);
   });

   // P0 / Error
   // The catchError fallback includes systemAdministrator: false so all fields
   // must be reset to safe empty values after a server error.
   it("should call errorService.showSnackBar and populate empty fallback state on API error", async () => {
      const errorService = makeErrorServiceMock();
      const { fixture } = await renderComponent(errorService);

      server.use(
         http.get("*/api/em/monitoring/audit/logonHistoryParameters", () =>
            new MswHttpResponse(null, { status: 500 })
         )
      );

      const comp = fixture.componentInstance;

      await firstValueFrom(comp.fetchParameters());

      expect(errorService.showSnackBar).toHaveBeenCalledTimes(1);
      expect(comp.users).toEqual([]);
      expect(comp.groups).toEqual([]);
      expect(comp.roles).toEqual([]);
      expect(comp.systemAdministrator).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 2: fetchData — HTTP param construction
// ---------------------------------------------------------------------------

describe("AuditLogonHistoryComponent — fetchData", () => {

   // P0 / Error
   // catchError must call showSnackBar and return a safe empty list.
   it("should call errorService.showSnackBar and return empty rows on data API error", async () => {
      const errorService = makeErrorServiceMock();
      const { fixture } = await renderComponent(errorService);


      server.use(
         http.get("*/api/em/monitoring/audit/logonHistory", () =>
            new MswHttpResponse(null, { status: 503 })
         )
      );

      const result = await firstValueFrom(
         fixture.componentInstance.fetchData(new HttpParams(), EMPTY_ADDITIONAL)
      );

      expect(errorService.showSnackBar).toHaveBeenCalledTimes(1);
      expect(result).toEqual({ totalRowCount: 0, rows: [] });
   });

   // P1 / Boundary
   // When all filter selections are empty, fetchData must not append any filter params.
   it("should not append users, groups, or roles params when all selections are empty", async () => {
      let capturedUrl: URL | null = null;

      server.use(
         http.get("*/api/em/monitoring/audit/logonHistory", ({ request }) => {
            capturedUrl = new URL(request.url);
            return MswHttpResponse.json({ totalRowCount: 0, rows: [] });
         })
      );

      const { fixture } = await renderComponent();
      await firstValueFrom(
         fixture.componentInstance.fetchData(new HttpParams(), EMPTY_ADDITIONAL)
      );

      expect(capturedUrl!.searchParams.has("users")).toBe(false);
      expect(capturedUrl!.searchParams.has("groups")).toBe(false);
      expect(capturedUrl!.searchParams.has("roles")).toBe(false);
   });

   // P1 / Happy
   // HttpParams.append() must be used so multiple groups produce separate param entries.
   it("should append each selected group as a separate query param entry", async () => {
      const receivedGroups: string[] = [];

      server.use(
         http.get("*/api/em/monitoring/audit/logonHistory", ({ request }) => {
            const url = new URL(request.url);
            url.searchParams.forEach((value, key) => {
               if(key === "groups") receivedGroups.push(value);
            });
            return MswHttpResponse.json({ totalRowCount: 0, rows: [] });
         })
      );

      const { fixture } = await renderComponent();
      await firstValueFrom(fixture.componentInstance.fetchData(new HttpParams(), {
         ...EMPTY_ADDITIONAL,
         selectedGroups: ["admins", "analysts"],
      }));

      expect(receivedGroups).toEqual(["admins", "analysts"]);
   });
});

// ---------------------------------------------------------------------------
// Group 3: getRoleLabel — global-role suffix stripping
// ---------------------------------------------------------------------------

describe("AuditLogonHistoryComponent — getRoleLabel", () => {

   // P1 / Happy
   // StyleBI global roles end with "___GLOBAL__" (11 characters). getRoleLabel must
   // strip exactly those 11 characters so the UI shows only the human-readable name.
   // e.g. "Admin___GLOBAL__" (16 chars) → "Admin" (5 chars, substring 0..5)
   it("should strip the 11-char ___GLOBAL__ suffix from global roles", async () => {
      const { fixture } = await renderComponent();
      const comp = fixture.componentInstance;

      expect(comp.getRoleLabel("Admin___GLOBAL__")).toBe("Admin");
      expect(comp.getRoleLabel("Viewer___GLOBAL__")).toBe("Viewer");
   });

   // P1 / Happy — non-global roles returned unchanged
   it("should return non-global roles unchanged", async () => {
      const { fixture } = await renderComponent();
      const comp = fixture.componentInstance;

      expect(comp.getRoleLabel("Viewer")).toBe("Viewer");
      expect(comp.getRoleLabel("PowerUser")).toBe("PowerUser");
   });
});

// ---------------------------------------------------------------------------
// Group 4: Design gap — organizationId column renderer vs displayedColumns
// ---------------------------------------------------------------------------

describe("AuditLogonHistoryComponent — column configuration", () => {

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
