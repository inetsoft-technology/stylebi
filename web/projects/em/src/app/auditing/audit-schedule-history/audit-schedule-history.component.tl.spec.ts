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
 * AuditScheduleHistoryComponent — Testing Library style
 *
 * Risk-first coverage (from scenario analysis):
 *   Group 1 — fetchParameters: API state binding + error recovery
 *   Group 2 — fetchData: HTTP param construction
 *   Group 3 — clearFolders / clearTasks: filter mutual exclusion
 *
 * Encoding contract for task params (intentional, NOT a bug):
 *   fetchData applies encodeURIComponent() to task assetIds before passing them to
 *   HttpParams.append(). Angular's HttpParams serializer then applies its own encoding,
 *   producing double-encoded values in the URL (e.g. ":" → "%3A" → "%253A").
 *
 *   This is intentional: the server performs two URL-decode passes on the tasks parameter,
 *   restoring the original assetId. Verified via DevTools Network tab — the URL shows
 *   double-encoding fingerprints (%253A, %2526, %252B, %253D), and the filter returns
 *   correctly scoped results.
 *
 *   Other filter params (users, folders, hosts, organizations) are NOT pre-encoded because
 *   the server only decodes them once.
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
import { AuditScheduleHistoryComponent } from "./audit-schedule-history.component";
import { PageHeaderService } from "../../page-header/page-header.service";
import { ErrorHandlerService } from "../../common/util/error/error-handler.service";
import { ScheduleHistoryParameters } from "./schedule-history";

// ---------------------------------------------------------------------------
// Shared fixtures
// ---------------------------------------------------------------------------

/** Minimal ScheduleHistoryParameters returned by the parameters API. */
const MOCK_PARAMS = {
   tasks: [{ label: "Daily Report", assetId: "daily-report-id" }],
   users: ["alice", "bob"],
   hosts: ["server-01"],
   folders: ["Finance", "HR"],
   organizationNames: ["Acme Corp"],
   organizationFilter: true,
   startTime: 1_700_000_000_000,
   endTime:   1_700_086_400_000,
};

/** Additional form values that represent an empty filter selection. */
const EMPTY_ADDITIONAL = {
   selectedTasks:     [] as string[],
   selectedUsers:     [] as string[],
   selectedFolders:   [] as string[],
   selectedHosts:     [] as string[],
   selectOrganization: [] as string[],   // intentional: component reads additional.selectOrganization (no "d")
};


/** Renders the component with NO_ERRORS_SCHEMA so em-audit-table-view is stubbed. */
async function renderComponent(errorService = makeErrorServiceMock()) {
   return render(AuditScheduleHistoryComponent, {
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
// Group 1: fetchParameters — API state binding
// ---------------------------------------------------------------------------

describe("AuditScheduleHistoryComponent — fetchParameters", () => {

   // P0 / Happy
   // fetchParameters is an arrow function that delegates to the child component via @Input.
   // The tap() operator updates component fields as a side effect of the Observable pipeline.
   // All six fields must be bound; missing any one would leave a dropdown empty.
   it("should populate tasks, users, hosts, folders, organizationNames, and organizationFilter from API response", async () => {
      server.use(
         http.get("*/api/em/monitoring/audit/scheduleHistoryParameters", () =>
            MswHttpResponse.json(MOCK_PARAMS)
         )
      );

      const { fixture } = await renderComponent();
      const comp = fixture.componentInstance;

      // Subscribed by the caller (child component). Calling it directly here exercises
      // the arrow function's 'this' context and the tap() side effects.
      await firstValueFrom(comp.fetchParameters());

      expect(comp.tasks).toEqual(MOCK_PARAMS.tasks);
      expect(comp.users).toEqual(MOCK_PARAMS.users);
      expect(comp.hosts).toEqual(MOCK_PARAMS.hosts);
      expect(comp.folders).toEqual(MOCK_PARAMS.folders);
      expect(comp.organizationNames).toEqual(MOCK_PARAMS.organizationNames);
      expect(comp.organizationFilter).toBe(true);
   });

   // P0 / Error
   // errorService.showSnackBar is called with a resultProducer that returns of(fallback).
   // The catchError then feeds the fallback through tap(), resetting all lists to empty.
   // If the fallback shape is wrong the child component will receive undefined fields and crash.
   it("should call errorService.showSnackBar and populate empty fallback state on API error", async () => {
      const errorService = makeErrorServiceMock();
      const { fixture } = await renderComponent(errorService);

      server.use(
         http.get("*/api/em/monitoring/audit/scheduleHistoryParameters", () =>
            new MswHttpResponse(null, { status: 500 })
         )
      );

      const comp = fixture.componentInstance;

      await firstValueFrom(comp.fetchParameters());

      // Error handler must be notified
      expect(errorService.showSnackBar).toHaveBeenCalledTimes(1);

      // Fallback state: all lists empty, organizationFilter defaults to true
      expect(comp.tasks).toEqual([]);
      expect(comp.users).toEqual([]);
      expect(comp.hosts).toEqual([]);
      expect(comp.folders).toEqual([]);
      expect(comp.organizationNames).toEqual([]);
      expect(comp.organizationFilter).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 2: fetchData — HTTP param construction
// ---------------------------------------------------------------------------

describe("AuditScheduleHistoryComponent — fetchData", () => {

   // P1 / Happy — encoding contract for task params
   // fetchData applies encodeURIComponent() to each task assetId before HttpParams.append().
   // Angular's HttpParams then encodes the result a second time, producing double-encoded values
   // in the URL. The server performs two URL-decode passes on the tasks param to recover the
   // original assetId. Other filter params (users, folders, hosts, orgs) use single encoding
   // because the server only decodes them once.
   //
   // Verified via DevTools: selecting assetId "admin:Task1&Tas+=k" produced
   //   tasks=admin%253ATask1%2526Tas%252B%253Dk  in the URL.
   // The server's two decode passes restored "admin:Task1&Tas+=k" and the filter returned
   // correctly scoped results.
   //
   // MSW intercepts after Angular's HttpParams serialization but before the server's decoding.
   // The value MSW sees is what the server receives as raw input to its first decode pass.
   it("should apply encodeURIComponent to task assetIds (single-encoded value reaches server for double-decode)", async () => {
      let receivedTask: string | null = null;

      server.use(
         http.get("*/api/em/monitoring/audit/scheduleHistory", ({ request }) => {
            receivedTask = new URL(request.url).searchParams.get("tasks");
            return MswHttpResponse.json({ totalRowCount: 0, rows: [] });
         })
      );

      const { fixture } = await renderComponent();
      await firstValueFrom(fixture.componentInstance.fetchData(new HttpParams(), {
         ...EMPTY_ADDITIONAL,
         selectedTasks: ["admin:ab&c+d=e 中文"],
      }));

      // encodeURIComponent("admin:ab&c+d=e 中文") → "admin%3Aab%26c%2Bd%3De%20%E4%B8%AD%E6%96%87"
      // HttpParams.append double-encodes % → URL contains %253A etc.
      // searchParams.get() URL-decodes once, so MSW receives the encodeURIComponent output.
      // The server decodes this once more to recover the original string.
      expect(receivedTask).toBe("admin%3Aab%26c%2Bd%3De%20%E4%B8%AD%E6%96%87");
   });

   // P0 / Error
   // fetchData's catchError must call errorService.showSnackBar and return a safe empty list.
   // Without this, a data fetch failure would leave the table in an indefinitely loading state.
   it("should call errorService.showSnackBar and return empty rows on data API error", async () => {
      const errorService = makeErrorServiceMock();
      const { fixture } = await renderComponent(errorService);


      server.use(
         http.get("*/api/em/monitoring/audit/scheduleHistory", () =>
            new MswHttpResponse(null, { status: 503 })
         )
      );

      const result = await firstValueFrom(
         fixture.componentInstance.fetchData(new HttpParams(), EMPTY_ADDITIONAL)
      );

      expect(errorService.showSnackBar).toHaveBeenCalledTimes(1);
      expect(result).toEqual({ totalRowCount: 0, rows: [] });
   });

   // P1 / Happy
   // HttpParams.append() must be used (not set()) so each value becomes a separate param entry.
   // Using set() would overwrite — only the last value would be sent to the server.
   it("should append each selected user as a separate query param entry", async () => {
      const receivedUsers: string[] = [];

      server.use(
         http.get("*/api/em/monitoring/audit/scheduleHistory", ({ request }) => {
            const url = new URL(request.url);
            url.searchParams.forEach((value, key) => {
               if(key === "users") receivedUsers.push(value);
            });
            return MswHttpResponse.json({ totalRowCount: 0, rows: [] });
         })
      );

      const { fixture } = await renderComponent();
      await firstValueFrom(fixture.componentInstance.fetchData(new HttpParams(), {
         ...EMPTY_ADDITIONAL,
         selectedUsers: ["alice", "bob"],
      }));

      expect(receivedUsers).toEqual(["alice", "bob"]);
   });

   // P1 / Boundary
   // When all filter selections are empty, fetchData must not append any filter params.
   // Spurious empty params (e.g. "tasks=") could confuse the backend query builder.
   it("should not append tasks, users, folders, hosts, or organizations params when all selections are empty", async () => {
      let capturedUrl: URL | null = null;

      server.use(
         http.get("*/api/em/monitoring/audit/scheduleHistory", ({ request }) => {
            capturedUrl = new URL(request.url);
            return MswHttpResponse.json({ totalRowCount: 0, rows: [] });
         })
      );

      const { fixture } = await renderComponent();
      await firstValueFrom(
         fixture.componentInstance.fetchData(new HttpParams(), EMPTY_ADDITIONAL)
      );

      expect(capturedUrl!.searchParams.has("tasks")).toBe(false);
      expect(capturedUrl!.searchParams.has("users")).toBe(false);
      expect(capturedUrl!.searchParams.has("folders")).toBe(false);
      expect(capturedUrl!.searchParams.has("hosts")).toBe(false);
      expect(capturedUrl!.searchParams.has("organizations")).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 3: clearFolders / clearTasks — filter mutual exclusion
// ---------------------------------------------------------------------------

describe("AuditScheduleHistoryComponent — clearFolders / clearTasks", () => {

   // P1 / Happy
   // Template: Tasks select → (selectionChange)="clearFolders($event)"
   // Selecting a task must clear the folder filter so the two don't conflict.
   it("should clear selectedFolders when a task is selected", async () => {
      const { fixture } = await renderComponent();
      const comp = fixture.componentInstance;

      comp.form.get("selectedFolders")!.setValue(["Finance"]);
      comp.clearFolders({ value: ["daily-report-id"] });

      expect(comp.form.get("selectedFolders")!.value).toEqual([]);
   });

   // P1 / Happy — cross-path symmetry (Technique 2)
   // Template: Users select ALSO triggers clearFolders, not just the Tasks select.
   // Both paths must enforce the same mutual-exclusion rule.
   it("should clear selectedFolders when a user is selected", async () => {
      const { fixture } = await renderComponent();
      const comp = fixture.componentInstance;

      comp.form.get("selectedFolders")!.setValue(["HR"]);
      comp.clearFolders({ value: ["alice"] });

      expect(comp.form.get("selectedFolders")!.value).toEqual([]);
   });

   // P1 / Happy
   // Template: Folder select → (selectionChange)="clearTasks($event)"
   // Selecting a folder must clear the task filter.
   it("should clear selectedTasks when a folder is selected", async () => {
      const { fixture } = await renderComponent();
      const comp = fixture.componentInstance;

      comp.form.get("selectedTasks")!.setValue(["daily-report-id"]);
      comp.clearTasks({ value: ["Finance"] });

      expect(comp.form.get("selectedTasks")!.value).toEqual([]);
   });

   // P1 / Boundary
   // clearFolders guard: if(evt.value != null && evt.value.length > 0)
   // Deselecting all tasks produces evt.value = []. This must NOT wipe the folder filter,
   // since the user did not select a conflicting task — they removed all task selections.
   it("should NOT clear selectedFolders when evt.value is an empty array", async () => {
      const { fixture } = await renderComponent();
      const comp = fixture.componentInstance;

      comp.form.get("selectedFolders")!.setValue(["Finance"]);
      comp.clearFolders({ value: [] });

      expect(comp.form.get("selectedFolders")!.value).toEqual(["Finance"]);
   });
});
