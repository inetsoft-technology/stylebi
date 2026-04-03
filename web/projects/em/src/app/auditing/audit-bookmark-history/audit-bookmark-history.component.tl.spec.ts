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
 * AuditBookmarkHistoryComponent — Testing Library style
 *
 * Risk-first coverage (from scenario analysis):
 *   Group 1 — fetchParameters: API state binding + error recovery
 *   Group 2 — fetchData: HTTP param construction + dashboard encoding + naming asymmetry
 *   Group 3 — getActionsTypeLabel: action type display label mapping
 *   Group 4 — Design gap: organizationId column renderer vs displayedColumns
 *
 * Dashboard encoding contract (intentional, NOT a bug):
 *   fetchData applies encodeURIComponent() to dashboard names before HttpParams.append().
 *   Angular then encodes the "%" character a second time (double-encoding). The server
 *   performs two URL-decode passes on the dashboards parameter to recover the original name.
 *   Other filter params (users, actions, hosts) are NOT pre-encoded.
 *
 * Naming asymmetry in fetchData (easy to get wrong):
 *   The form control is named "selectedActionTypes" but the HTTP query parameter appended
 *   to the request is "actions" (not "actionTypes"). Tests verify the server param name.
 */
import { Component, forwardRef, NO_ERRORS_SCHEMA } from "@angular/core";
import { ControlValueAccessor, NG_VALUE_ACCESSOR, ReactiveFormsModule } from "@angular/forms";
import { HttpClientModule, HttpParams } from "@angular/common/http";
import { render } from "@testing-library/angular";
import { http, HttpResponse as MswHttpResponse } from "msw";
import { firstValueFrom, Observable, throwError } from "rxjs";
import { ActivatedRoute } from "@angular/router";

import { server } from "../../../../../../mocks/server";
import { AuditBookmarkHistoryComponent } from "./audit-bookmark-history.component";
import { PageHeaderService } from "../../page-header/page-header.service";
import { ErrorHandlerService } from "../../common/util/error/error-handler.service";
import { BookmarkHistoryParameters } from "./bookmark-history";

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

/** Minimal BookmarkHistoryParameters returned by the parameters API. */
const MOCK_PARAMS: BookmarkHistoryParameters = {
   users: ["alice", "bob"],
   actionTypes: ["modify", "access", "delete"],
   hosts: ["server-01"],
   dashboards: ["Sales/Monthly", "HR/Overview"],
   organizations: [],
   systemAdministrator: true,
   startTime: 1_700_000_000_000,
   endTime:   1_700_086_400_000,
};

/** Additional form values representing an empty filter selection. */
const EMPTY_ADDITIONAL = {
   selectedUsers:       [] as string[],
   selectedActionTypes: [] as string[],
   selectedDashboards:  [] as string[],
   selectedHosts:       [] as string[],
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
   return render(AuditBookmarkHistoryComponent, {
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

describe("AuditBookmarkHistoryComponent — fetchParameters", () => {

   // P0 / Happy
   // tap() side effects must assign users, actionTypes, dashboards, hosts, systemAdministrator.
   it("should populate users, actionTypes, dashboards, hosts, and systemAdministrator from API response", async () => {
      server.use(
         http.get("*/api/em/monitoring/audit/bookmarkHistoryParameters", () =>
            MswHttpResponse.json(MOCK_PARAMS)
         )
      );

      const { fixture } = await renderComponent();
      const comp = fixture.componentInstance;

      await firstValueFrom(comp.fetchParameters());

      expect(comp.users).toEqual(MOCK_PARAMS.users);
      expect(comp.actionTypes).toEqual(MOCK_PARAMS.actionTypes);
      expect(comp.dashboards).toEqual(MOCK_PARAMS.dashboards);
      expect(comp.hosts).toEqual(MOCK_PARAMS.hosts);
      expect(comp.systemAdministrator).toBe(true);
   });

   // P0 / Error
   // The catchError fallback includes systemAdministrator: false and all empty arrays,
   // so all fields must be reset to safe empty values after a server error.
   it("should call errorService.showSnackBar and populate empty fallback state on API error", async () => {
      const errorService = makeErrorServiceMock();
      const { fixture } = await renderComponent(errorService);

      server.use(
         http.get("*/api/em/monitoring/audit/bookmarkHistoryParameters", () =>
            new MswHttpResponse(null, { status: 500 })
         )
      );

      const comp = fixture.componentInstance;

      await firstValueFrom(comp.fetchParameters());

      expect(errorService.showSnackBar).toHaveBeenCalledTimes(1);
      expect(comp.users).toEqual([]);
      expect(comp.actionTypes).toEqual([]);
      expect(comp.dashboards).toEqual([]);
      expect(comp.hosts).toEqual([]);
      expect(comp.systemAdministrator).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 2: fetchData — HTTP param construction
// ---------------------------------------------------------------------------

describe("AuditBookmarkHistoryComponent — fetchData", () => {

   // P0 / Error
   // catchError must call showSnackBar and return a safe empty list.
   it("should call errorService.showSnackBar and return empty rows on data API error", async () => {
      const errorService = makeErrorServiceMock();
      const { fixture } = await renderComponent(errorService);


      server.use(
         http.get("*/api/em/monitoring/audit/bookmarkHistory", () =>
            new MswHttpResponse(null, { status: 503 })
         )
      );

      const result = await firstValueFrom(
         fixture.componentInstance.fetchData(new HttpParams(), EMPTY_ADDITIONAL)
      );

      expect(errorService.showSnackBar).toHaveBeenCalledTimes(1);
      expect(result).toEqual({ totalRowCount: 0, rows: [] });
   });

   // P1 / Happy — dashboard encoding contract
   // fetchData applies encodeURIComponent() to dashboard names before HttpParams.append().
   // Angular then encodes "%" a second time, so MSW sees the single-encoded value.
   // URL.searchParams.get() decodes once, giving back the encodeURIComponent result.
   it("should apply encodeURIComponent to dashboard names (single-encoded value reaches server for double-decode)", async () => {
      let receivedDashboard: string | null = null;

      server.use(
         http.get("*/api/em/monitoring/audit/bookmarkHistory", ({ request }) => {
            receivedDashboard = new URL(request.url).searchParams.get("dashboards");
            return MswHttpResponse.json({ totalRowCount: 0, rows: [] });
         })
      );

      const { fixture } = await renderComponent();
      await firstValueFrom(fixture.componentInstance.fetchData(new HttpParams(), {
         ...EMPTY_ADDITIONAL,
         selectedDashboards: ["Reports/Monthly"],
      }));

      // encodeURIComponent("Reports/Monthly") → "Reports%2FMonthly"
      // HttpParams encodes "%" → "%25", so URL contains "Reports%252FMonthly"
      // URL.searchParams.get() decodes once → "Reports%2FMonthly"
      expect(receivedDashboard).toBe("Reports%2FMonthly");
   });

   // P1 / Happy — naming asymmetry: form control "selectedActionTypes" → HTTP param "actions"
   // The form control and the HTTP query param use different names. This is the highest-risk
   // naming asymmetry in the component; getting it wrong silently drops the filter.
   it("should send selected action types using the HTTP param name 'actions' (not 'actionTypes')", async () => {
      const receivedActions: string[] = [];
      let hasActionTypes = false;

      server.use(
         http.get("*/api/em/monitoring/audit/bookmarkHistory", ({ request }) => {
            const url = new URL(request.url);
            url.searchParams.forEach((value, key) => {
               if(key === "actions") receivedActions.push(value);
               if(key === "actionTypes") hasActionTypes = true;
            });
            return MswHttpResponse.json({ totalRowCount: 0, rows: [] });
         })
      );

      const { fixture } = await renderComponent();
      await firstValueFrom(fixture.componentInstance.fetchData(new HttpParams(), {
         ...EMPTY_ADDITIONAL,
         selectedActionTypes: ["modify", "delete"],
      }));

      expect(receivedActions).toEqual(["modify", "delete"]);
      expect(hasActionTypes).toBe(false);
   });

   // P1 / Boundary
   // When all selections are empty, no filter params should appear in the request URL.
   it("should not append users, actions, dashboards, or hosts params when all selections are empty", async () => {
      let capturedUrl: URL | null = null;

      server.use(
         http.get("*/api/em/monitoring/audit/bookmarkHistory", ({ request }) => {
            capturedUrl = new URL(request.url);
            return MswHttpResponse.json({ totalRowCount: 0, rows: [] });
         })
      );

      const { fixture } = await renderComponent();
      await firstValueFrom(
         fixture.componentInstance.fetchData(new HttpParams(), EMPTY_ADDITIONAL)
      );

      expect(capturedUrl!.searchParams.has("users")).toBe(false);
      expect(capturedUrl!.searchParams.has("actions")).toBe(false);
      expect(capturedUrl!.searchParams.has("dashboards")).toBe(false);
      expect(capturedUrl!.searchParams.has("hosts")).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 3: getActionsTypeLabel — action type display label mapping
// ---------------------------------------------------------------------------

describe("AuditBookmarkHistoryComponent — getActionsTypeLabel", () => {

   // P2 / Happy — all 5 known values map to i18n keys
   it("should map all 5 known action types to their i18n keys", async () => {
      const { fixture } = await renderComponent();
      const comp = fixture.componentInstance;

      expect(comp.getActionsTypeLabel("modify")).toBe("_#(js:Modify)");
      expect(comp.getActionsTypeLabel("access")).toBe("_#(js:Access)");
      expect(comp.getActionsTypeLabel("delete")).toBe("_#(js:Delete)");
      expect(comp.getActionsTypeLabel("rename")).toBe("_#(js:Rename)");
      expect(comp.getActionsTypeLabel("create")).toBe("_#(js:Create)");
   });

   // P2 / Happy — unknown values returned as-is (passthrough fallback)
   it("should return unknown action type values as-is", async () => {
      const { fixture } = await renderComponent();
      const comp = fixture.componentInstance;

      expect(comp.getActionsTypeLabel("custom-action")).toBe("custom-action");
      expect(comp.getActionsTypeLabel("")).toBe("");
   });
});

// ---------------------------------------------------------------------------
// Group 4: Design gap — organizationId column renderer vs displayedColumns
// ---------------------------------------------------------------------------

describe("AuditBookmarkHistoryComponent — column configuration", () => {

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
