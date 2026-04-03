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
 * AuditExportHistoryComponent — Testing Library style
 *
 * Risk-first coverage (from scenario analysis):
 *   Group 1 — fetchParameters: API state binding + error recovery
 *   Group 2 — fetchData: HTTP param construction + folder encoding contract
 *   Group 3 — getTypeLabel: type display label mapping
 *   Group 4 — Design gap: organizationId column renderer vs displayedColumns
 *
 * Folder encoding contract (intentional, NOT a bug):
 *   fetchData applies encodeURIComponent() to folder paths before passing them to
 *   HttpParams.append(). Angular's HttpParams then encodes the result a second time,
 *   producing double-encoded values in the URL (e.g. "/" → "%2F" → "%252F").
 *
 *   This is intentional: the server performs two URL-decode passes on the folders
 *   parameter, restoring the original path. Other filter params (users, hosts) are
 *   NOT pre-encoded because the server only decodes them once.
 *
 *   MSW intercepts after Angular's HttpParams serialization. The value MSW sees
 *   is what the server receives as raw input to its first decode pass.
 */
import { Component, forwardRef, NO_ERRORS_SCHEMA } from "@angular/core";
import { ControlValueAccessor, NG_VALUE_ACCESSOR, ReactiveFormsModule } from "@angular/forms";
import { HttpClientModule, HttpParams } from "@angular/common/http";
import { render } from "@testing-library/angular";
import { http, HttpResponse as MswHttpResponse } from "msw";
import { firstValueFrom, Observable, throwError } from "rxjs";
import { ActivatedRoute } from "@angular/router";

import { server } from "../../../../../../mocks/server";
import { AuditExportHistoryComponent } from "./audit-export-history.component";
import { PageHeaderService } from "../../page-header/page-header.service";
import { ErrorHandlerService } from "../../common/util/error/error-handler.service";
import { ExportHistoryParameters } from "./export-history";

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

/** Minimal ExportHistoryParameters returned by the parameters API. */
const MOCK_PARAMS: ExportHistoryParameters = {
   objectTypes: ["dashboard", "viewsheet"],
   users: ["alice", "bob"],
   hosts: ["server-01"],
   folders: ["Finance", "HR/Reports"],
   organizations: [],
   systemAdministrator: true,
   startTime: 1_700_000_000_000,
   endTime:   1_700_086_400_000,
};

/** Additional form values representing an empty filter selection. */
const EMPTY_ADDITIONAL = {
   selectedUsers:   [] as string[],
   selectedHosts:   [] as string[],
   selectedFolders: [] as string[],
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
   return render(AuditExportHistoryComponent, {
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

describe("AuditExportHistoryComponent — fetchParameters", () => {

   // P0 / Happy
   // tap() side effects must assign objectTypes, users, hosts, folders, and systemAdministrator.
   it("should populate objectTypes, users, hosts, folders, and systemAdministrator from API response", async () => {
      server.use(
         http.get("*/api/em/monitoring/audit/exportHistoryParameters", () =>
            MswHttpResponse.json(MOCK_PARAMS)
         )
      );

      const { fixture } = await renderComponent();
      const comp = fixture.componentInstance;

      await firstValueFrom(comp.fetchParameters());

      expect(comp.objectTypes).toEqual(MOCK_PARAMS.objectTypes);
      expect(comp.users).toEqual(MOCK_PARAMS.users);
      expect(comp.hosts).toEqual(MOCK_PARAMS.hosts);
      expect(comp.folders).toEqual(MOCK_PARAMS.folders);
      expect(comp.systemAdministrator).toBe(true);
   });

   // P0 / Error
   // The catchError fallback includes systemAdministrator: false and all empty arrays,
   // so all fields must be reset to safe empty values after a server error.
   it("should call errorService.showSnackBar and populate empty fallback state on API error", async () => {
      const errorService = makeErrorServiceMock();
      const { fixture } = await renderComponent(errorService);

      server.use(
         http.get("*/api/em/monitoring/audit/exportHistoryParameters", () =>
            new MswHttpResponse(null, { status: 500 })
         )
      );

      const comp = fixture.componentInstance;

      await firstValueFrom(comp.fetchParameters());

      expect(errorService.showSnackBar).toHaveBeenCalledTimes(1);
      expect(comp.objectTypes).toEqual([]);
      expect(comp.users).toEqual([]);
      expect(comp.hosts).toEqual([]);
      expect(comp.folders).toEqual([]);
      expect(comp.systemAdministrator).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 2: fetchData — HTTP param construction
// ---------------------------------------------------------------------------

describe("AuditExportHistoryComponent — fetchData", () => {

   // P0 / Error
   // catchError must call showSnackBar and return a safe empty list.
   it("should call errorService.showSnackBar and return empty rows on data API error", async () => {
      const errorService = makeErrorServiceMock();
      const { fixture } = await renderComponent(errorService);


      server.use(
         http.get("*/api/em/monitoring/audit/exportHistory", () =>
            new MswHttpResponse(null, { status: 503 })
         )
      );

      const result = await firstValueFrom(
         fixture.componentInstance.fetchData(new HttpParams(), EMPTY_ADDITIONAL)
      );

      expect(errorService.showSnackBar).toHaveBeenCalledTimes(1);
      expect(result).toEqual({ totalRowCount: 0, rows: [] });
   });

   // P1 / Happy — folder encoding contract
   // fetchData applies encodeURIComponent() to folder paths before HttpParams.append().
   // Angular then encodes the "%" character a second time, so MSW sees the single-encoded
   // value (e.g. "/" → "%2F"). The server decodes it once to recover "Finance%2FQ1",
   // then decodes again to get the original "Finance/Q1".
   it("should apply encodeURIComponent to folder paths (single-encoded value reaches server for double-decode)", async () => {
      let receivedFolder: string | null = null;

      server.use(
         http.get("*/api/em/monitoring/audit/exportHistory", ({ request }) => {
            receivedFolder = new URL(request.url).searchParams.get("folders");
            return MswHttpResponse.json({ totalRowCount: 0, rows: [] });
         })
      );

      const { fixture } = await renderComponent();
      await firstValueFrom(fixture.componentInstance.fetchData(new HttpParams(), {
         ...EMPTY_ADDITIONAL,
         selectedFolders: ["Finance/Q1"],
      }));

      // MSW sees the single-encoded value (encodeURIComponent applied, then HttpParams
      // encodes the "%" to "%25", so "/" → "%2F" → "%252F"). URL.searchParams.get()
      // decodes once, giving back the encodeURIComponent result: "Finance%2FQ1".
      expect(receivedFolder).toBe("Finance%2FQ1");
   });

   // P1 / Boundary
   // When all selections are empty, no filter params should appear in the request URL.
   it("should not append users, hosts, or folders params when all selections are empty", async () => {
      let capturedUrl: URL | null = null;

      server.use(
         http.get("*/api/em/monitoring/audit/exportHistory", ({ request }) => {
            capturedUrl = new URL(request.url);
            return MswHttpResponse.json({ totalRowCount: 0, rows: [] });
         })
      );

      const { fixture } = await renderComponent();
      await firstValueFrom(
         fixture.componentInstance.fetchData(new HttpParams(), EMPTY_ADDITIONAL)
      );

      expect(capturedUrl!.searchParams.has("users")).toBe(false);
      expect(capturedUrl!.searchParams.has("hosts")).toBe(false);
      expect(capturedUrl!.searchParams.has("folders")).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 3: getTypeLabel — type display label mapping
// ---------------------------------------------------------------------------

describe("AuditExportHistoryComponent — getTypeLabel", () => {

   // P2 / Happy
   // "dashboard" must be mapped to the capitalised form; all other values pass through.
   it("should map 'dashboard' to 'Dashboard' and return other values as-is", async () => {
      const { fixture } = await renderComponent();
      const comp = fixture.componentInstance;

      expect(comp.getTypeLabel("dashboard")).toBe("Dashboard");
      expect(comp.getTypeLabel("viewsheet")).toBe("viewsheet");
      expect(comp.getTypeLabel("report")).toBe("report");
   });
});

// ---------------------------------------------------------------------------
// Group 4: Design gap — organizationId column renderer vs displayedColumns
// ---------------------------------------------------------------------------

describe("AuditExportHistoryComponent — column configuration", () => {

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
