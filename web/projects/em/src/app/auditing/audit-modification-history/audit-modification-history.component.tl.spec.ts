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
 * AuditModificationHistoryComponent — Testing Library style
 *
 * Risk-first coverage (from scenario analysis):
 *   Group 1 — fetchParameters: API state binding + error recovery
 *   Group 2 — fetchData: HTTP param construction + naming asymmetry
 *   Group 3 — getModifyStatusLabel / getAssetTypeLabel: display label mapping
 *
 * fetchParameters fallback:
 *   The catchError fallback sets organizationFilter: true (not false). This means
 *   the component shows the organization filter panel by default even after an error.
 *
 * Naming asymmetry in fetchData:
 *   Form control "selectedStatuses" → HTTP query param "modifyStatuses" (not "statuses").
 *   Getting this wrong silently drops the status filter from the request.
 */
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { ReactiveFormsModule } from "@angular/forms";
import { HttpClientModule, HttpParams } from "@angular/common/http";
import { render } from "@testing-library/angular";
import { http, HttpResponse as MswHttpResponse } from "msw";
import { firstValueFrom } from "rxjs";
import { ActivatedRoute } from "@angular/router";
import { MatSelectStub, makeErrorServiceMock } from "../testing/audit-test-utils";

import { server } from "../../../../../../mocks/server";
import { AuditModificationHistoryComponent } from "./audit-modification-history.component";
import { PageHeaderService } from "../../page-header/page-header.service";
import { ErrorHandlerService } from "../../common/util/error/error-handler.service";
import { ModificationHistoryParameters } from "./modification-history";

// ---------------------------------------------------------------------------
// Shared fixtures
// ---------------------------------------------------------------------------

/** Minimal ModificationHistoryParameters returned by the parameters API. */
const MOCK_PARAMS: ModificationHistoryParameters = {
   users: ["alice", "bob"],
   objectTypes: ["dashboard", "viewsheet"],
   hosts: ["server-01"],
   organizationFilter: true,
   organizationNames: ["Acme Corp", "Beta Ltd"],
   modifyStatuses: ["success", "failure"],
   startTime: 1_700_000_000_000,
   endTime:   1_700_086_400_000,
};

/** Additional form values representing an empty filter selection. */
const EMPTY_ADDITIONAL = {
   selectedUsers:         [] as string[],
   selectedTypes:         [] as string[],
   selectedHosts:         [] as string[],
   selectedOrganizations: [] as string[],
   selectedStatuses:      [] as string[],
};


/** Renders the component with NO_ERRORS_SCHEMA so em-audit-table-view is stubbed. */
async function renderComponent(errorService = makeErrorServiceMock()) {
   return render(AuditModificationHistoryComponent, {
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

describe("AuditModificationHistoryComponent — fetchParameters", () => {

   // P0 / Happy
   // tap() side effects must assign all six fields from the API response.
   it("should populate users, objectTypes, hosts, organizationFilter, organizationNames, and modifyStatuses from API response", async () => {
      server.use(
         http.get("*/api/em/monitoring/audit/modificationHistoryParameters", () =>
            MswHttpResponse.json(MOCK_PARAMS)
         )
      );

      const { fixture } = await renderComponent();
      const comp = fixture.componentInstance;

      await firstValueFrom(comp.fetchParameters());

      expect(comp.users).toEqual(MOCK_PARAMS.users);
      expect(comp.objectTypes).toEqual(MOCK_PARAMS.objectTypes);
      expect(comp.hosts).toEqual(MOCK_PARAMS.hosts);
      expect(comp.organizationFilter).toBe(true);
      expect(comp.organizationNames).toEqual(MOCK_PARAMS.organizationNames);
      expect(comp.modifyStatuses).toEqual(MOCK_PARAMS.modifyStatuses);
   });

   // P0 / Error
   // The catchError fallback sets organizationFilter: true (not false) so the organization
   // panel remains visible after an error. All list fields reset to empty arrays.
   it("should call errorService.showSnackBar and populate empty fallback state with organizationFilter=true on API error", async () => {
      const errorService = makeErrorServiceMock();
      const { fixture } = await renderComponent(errorService);

      server.use(
         http.get("*/api/em/monitoring/audit/modificationHistoryParameters", () =>
            new MswHttpResponse(null, { status: 500 })
         )
      );

      const comp = fixture.componentInstance;

      await firstValueFrom(comp.fetchParameters());

      expect(errorService.showSnackBar).toHaveBeenCalledTimes(1);
      expect(comp.users).toEqual([]);
      expect(comp.objectTypes).toEqual([]);
      expect(comp.hosts).toEqual([]);
      expect(comp.organizationFilter).toBe(true);
      expect(comp.organizationNames).toEqual([]);
      expect(comp.modifyStatuses).toEqual([]);
   });
});

// ---------------------------------------------------------------------------
// Group 2: fetchData — HTTP param construction
// ---------------------------------------------------------------------------

describe("AuditModificationHistoryComponent — fetchData", () => {

   // P0 / Error
   // catchError must call showSnackBar and return a safe empty list.
   it("should call errorService.showSnackBar and return empty rows on data API error", async () => {
      const errorService = makeErrorServiceMock();
      const { fixture } = await renderComponent(errorService);


      server.use(
         http.get("*/api/em/monitoring/audit/modificationHistory", () =>
            new MswHttpResponse(null, { status: 503 })
         )
      );

      const result = await firstValueFrom(
         fixture.componentInstance.fetchData(new HttpParams(), EMPTY_ADDITIONAL)
      );

      expect(errorService.showSnackBar).toHaveBeenCalledTimes(1);
      expect(result).toEqual({ totalRowCount: 0, rows: [] });
   });

   // P1 / Happy — naming asymmetry: "selectedStatuses" form control → "modifyStatuses" HTTP param
   // Using "statuses" or "selectedStatuses" as the param name would silently drop the filter.
   it("should send selected statuses using the HTTP param name 'modifyStatuses' (not 'statuses')", async () => {
      const receivedModifyStatuses: string[] = [];
      let hasStatuses = false;

      server.use(
         http.get("*/api/em/monitoring/audit/modificationHistory", ({ request }) => {
            const url = new URL(request.url);
            url.searchParams.forEach((value, key) => {
               if(key === "modifyStatuses") receivedModifyStatuses.push(value);
               if(key === "statuses" || key === "selectedStatuses") hasStatuses = true;
            });
            return MswHttpResponse.json({ totalRowCount: 0, rows: [] });
         })
      );

      const { fixture } = await renderComponent();
      await firstValueFrom(fixture.componentInstance.fetchData(new HttpParams(), {
         ...EMPTY_ADDITIONAL,
         selectedStatuses: ["success", "failure"],
      }));

      expect(receivedModifyStatuses).toEqual(["success", "failure"]);
      expect(hasStatuses).toBe(false);
   });

   // P1 / Boundary
   // When all selections are empty, no filter params should appear in the request URL.
   it("should not append users, types, hosts, organizations, or modifyStatuses params when all selections are empty", async () => {
      let capturedUrl: URL | null = null;

      server.use(
         http.get("*/api/em/monitoring/audit/modificationHistory", ({ request }) => {
            capturedUrl = new URL(request.url);
            return MswHttpResponse.json({ totalRowCount: 0, rows: [] });
         })
      );

      const { fixture } = await renderComponent();
      await firstValueFrom(
         fixture.componentInstance.fetchData(new HttpParams(), EMPTY_ADDITIONAL)
      );

      expect(capturedUrl!.searchParams.has("users")).toBe(false);
      expect(capturedUrl!.searchParams.has("types")).toBe(false);
      expect(capturedUrl!.searchParams.has("hosts")).toBe(false);
      expect(capturedUrl!.searchParams.has("organizations")).toBe(false);
      expect(capturedUrl!.searchParams.has("modifyStatuses")).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 3: getModifyStatusLabel / getAssetTypeLabel — display label mapping
// ---------------------------------------------------------------------------

describe("AuditModificationHistoryComponent — getModifyStatusLabel", () => {

   // P2 / Happy
   // "success" and "failure" must map to i18n keys; anything else passes through.
   it("should map 'success' and 'failure' to i18n keys and return other values as-is", async () => {
      const { fixture } = await renderComponent();
      const comp = fixture.componentInstance;

      expect(comp.getModifyStatusLabel("success")).toBe("_#(js:Success)");
      expect(comp.getModifyStatusLabel("failure")).toBe("_#(js:Failure)");
      expect(comp.getModifyStatusLabel("pending")).toBe("pending");
   });
});

describe("AuditModificationHistoryComponent — getAssetTypeLabel", () => {

   // P2 / Happy
   // "dashboard" must map to its i18n key; unknown values must pass through safely.
   it("should map 'dashboard' to its i18n key and return unknown values as-is", async () => {
      const { fixture } = await renderComponent();
      const comp = fixture.componentInstance;

      expect(comp.getAssetTypeLabel("dashboard")).toBe("_#(js:Dashboard)");
      expect(comp.getAssetTypeLabel("viewsheet")).toBe("_#(js:Viewsheet)");
      expect(comp.getAssetTypeLabel("unknown-type")).toBe("unknown-type");
   });
});
