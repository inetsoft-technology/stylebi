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
 * BatchActionEditorComponent — Testing Library style
 *
 * Risk-first coverage:
 *   Group 1 [Risk 2]  — ngOnInit(): scheduled-tasks fetch gated by originalTaskName presence
 *   Group 2 [Risk 2]  — modelValid getter: complex AND/OR condition boundary cases
 *   Group 3 [Risk 2]  — fetchParameters(): loadingParameterNames reset via finalize()
 *   Group 4 [Risk 2]  — editQuery() / editEmbedded(): dialog result updates actionModel + fires modelChanged
 *   Group 5 [Risk 2]  — actionModel setter: fetchParameters() skipped when task name is unchanged
 *
 * KEY contracts:
 *   - ngOnInit() fetches scheduled-tasks only when originalTaskName is non-empty.
 *   - modelValid = taskName != null AND ((queryEnabled && queryEntry && queryParameters.length > 0)
 *     OR (embeddedEnabled && embeddedParameters.length > 0)).
 *   - fetchParameters() sets loadingParameterNames=true before HTTP and resets it to false via finalize.
 *   - editQuery/editEmbedded: null result from dialog → no model update, no modelChanged emission.
 *   - actionModel setter: only calls fetchParameters() when selectedTaskName actually changes.
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { provideHttpClient } from "@angular/common/http";
import { MatDialog } from "@angular/material/dialog";
import { render, waitFor } from "@testing-library/angular";
import { http, HttpResponse } from "msw";
import { of } from "rxjs";

import { server } from "../../../../../../../../mocks/server";
import { BatchActionEditorComponent } from "./batch-action-editor.component";
import { BatchActionModel } from "../../../../../../../shared/schedule/model/batch-action-model";
import { TaskActionPaneModel } from "../../../../../../../shared/schedule/model/task-action-pane-model";

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

function makeActionModel(overrides: Partial<BatchActionModel> = {}): BatchActionModel {
   return {
      taskName: "TaskA",
      queryEntry: null as any,
      queryParameters: [],
      embeddedParameters: [],
      queryEnabled: false,
      embeddedEnabled: false,
      actionType: "BatchAction",
      actionClass: "BatchActionModel",
      ...overrides,
   } as BatchActionModel;
}

function makeTaskModel(overrides: Partial<TaskActionPaneModel> = {}): TaskActionPaneModel {
   return {
      securityEnabled: false,
      emailButtonVisible: false,
      administrator: false,
      ...overrides,
   } as TaskActionPaneModel;
}

const SCHEDULED_TASKS_URL = "*/api/em/schedule/batch-action/scheduled-tasks*";
const PARAMETERS_URL = "*/api/em/schedule/batch-action/parameters*";

// ---------------------------------------------------------------------------
// Render helper
// ---------------------------------------------------------------------------

// beforeEach installs fallback handlers. Test-specific server.use() calls are placed AFTER these
// in the LIFO stack, so test handlers take precedence while every render is covered by defaults.
beforeEach(() => {
   server.use(
      http.get(SCHEDULED_TASKS_URL, () => HttpResponse.json({ tasks: [] })),
      http.get(PARAMETERS_URL, () => HttpResponse.json({ parameterNames: [] })),
   );
});

async function renderComp(opts: {
   originalTaskName?: string;
   actionModel?: BatchActionModel;
   model?: TaskActionPaneModel;
   dialogClosesWith?: unknown;
} = {}) {

   const dialogMock = {
      open: jest.fn().mockReturnValue({
         afterClosed: () => of(opts.dialogClosesWith !== undefined ? opts.dialogClosesWith : null),
      }),
   };

   const result = await render(BatchActionEditorComponent, {
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
         provideHttpClient(),
         { provide: MatDialog, useValue: dialogMock },
      ],
      componentProperties: {
         originalTaskName: opts.originalTaskName ?? "ParentTask",
         model: opts.model ?? makeTaskModel(),
         actionModel: opts.actionModel ?? makeActionModel(),
      },
   });

   result.fixture.detectChanges();
   await result.fixture.whenStable();

   const comp = result.fixture.componentInstance as BatchActionEditorComponent;
   return { comp, dialogMock };
}

// ════════════════════════════════════════════════════════════════════════════
// Group 1 [Risk 2] — ngOnInit(): task list fetch gated by originalTaskName
// ════════════════════════════════════════════════════════════════════════════

describe("BatchActionEditorComponent — ngOnInit(): scheduled-tasks fetch", () => {

   // 🔁 Regression-sensitive: ngOnInit() must fetch the task list when originalTaskName is set
   // so the task dropdown is populated with candidate tasks.
   it("should populate tasks list after ngOnInit when originalTaskName is provided", async () => {
      server.use(
         http.get(SCHEDULED_TASKS_URL, () =>
            HttpResponse.json({ tasks: [{ name: "TaskA" }, { name: "TaskB" }] })
         )
      );

      const { comp } = await renderComp({ originalTaskName: "ParentTask" });

      await waitFor(() => expect(comp.tasks).toBeDefined());
      expect(comp.tasks.length).toBe(2);
   });

   // Risk Point/Contract: when originalTaskName is absent, no HTTP request is made and
   // tasks remains undefined (no dropdown to populate).
   it("should leave tasks undefined when originalTaskName is not provided", async () => {
      const fetchSpy = jest.fn();
      server.use(http.get(SCHEDULED_TASKS_URL, () => { fetchSpy(); return HttpResponse.json({ tasks: [] }); }));

      const { comp } = await renderComp({ originalTaskName: "" });

      await waitFor(() => expect(comp.loadingParameterNames).toBe(false));
      expect(fetchSpy).not.toHaveBeenCalled();
      expect(comp.tasks).toBeUndefined();
   });

});

// ════════════════════════════════════════════════════════════════════════════
// Group 2 [Risk 2] — modelValid getter: AND/OR boundary cases
// ════════════════════════════════════════════════════════════════════════════

describe("BatchActionEditorComponent — modelValid getter: boundary conditions", () => {

   // Risk Point/Contract: taskName=null → always invalid, regardless of query/embedded config.
   it("should be false when taskName is null", async () => {
      const { comp } = await renderComp({
         actionModel: makeActionModel({
            taskName: null as any,
            queryEnabled: true,
            queryEntry: { path: "q" } as any,
            queryParameters: [{ name: "p" } as any],
         }),
      });

      expect(comp.modelValid).toBe(false);
   });

   // 🔁 Regression-sensitive: queryEnabled path requires queryEntry AND queryParameters.length > 0.
   // A non-null queryEntry with an empty queryParameters array is still invalid.
   it("should be false when queryEnabled=true but queryParameters is empty", async () => {
      const { comp } = await renderComp({
         actionModel: makeActionModel({
            taskName: "TaskA",
            queryEnabled: true,
            queryEntry: { path: "q" } as any,
            queryParameters: [],
            embeddedEnabled: false,
         }),
      });

      expect(comp.modelValid).toBe(false);
   });

   // Happy: embeddedEnabled path with at least one embedded parameter set → valid.
   it("should be true when embeddedEnabled=true and embeddedParameters has one entry", async () => {
      const { comp } = await renderComp({
         actionModel: makeActionModel({
            taskName: "TaskA",
            embeddedEnabled: true,
            embeddedParameters: [[{ name: "p", type: "string" } as any]],
            queryEnabled: false,
         }),
      });

      expect(comp.modelValid).toBe(true);
   });

   // Happy: queryEnabled path fully configured → valid.
   it("should be true when queryEnabled=true with queryEntry and non-empty queryParameters", async () => {
      const { comp } = await renderComp({
         actionModel: makeActionModel({
            taskName: "TaskA",
            queryEnabled: true,
            queryEntry: { path: "q" } as any,
            queryParameters: [{ name: "p" } as any],
            embeddedEnabled: false,
         }),
      });

      expect(comp.modelValid).toBe(true);
   });

});

// ════════════════════════════════════════════════════════════════════════════
// Group 3 [Risk 2] — fetchParameters(): loadingParameterNames via finalize
// ════════════════════════════════════════════════════════════════════════════

describe("BatchActionEditorComponent — fetchParameters(): loading flag lifecycle", () => {

   // 🔁 Regression-sensitive: loadingParameterNames must be reset to false after the HTTP call
   // completes (success or error) via finalize(). If not reset, the loading indicator spins forever.
   it("should reset loadingParameterNames to false after parameters are fetched", async () => {
      server.use(
         http.get(PARAMETERS_URL, () =>
            HttpResponse.json({ parameterNames: ["param1", "param2"] })
         )
      );

      const { comp } = await renderComp({ actionModel: makeActionModel({ taskName: "TaskA" }) });

      comp.fetchParameters();

      await waitFor(() => expect(comp.loadingParameterNames).toBe(false));
      expect(comp.parameterNames).toEqual(["param1", "param2"]);
   });

   // Risk Point/Contract: fetchParameters() is a no-op when selectedTaskName is falsy —
   // it must not start a loading state for an undefined task.
   it("should not set loadingParameterNames to true when selectedTaskName is empty", async () => {
      const { comp } = await renderComp();
      // Wait for any initial fetch triggered by actionModel setter to complete
      await waitFor(() => expect(comp.loadingParameterNames).toBe(false));

      comp.selectedTaskName = "";
      comp.fetchParameters();

      // loadingParameterNames must remain false (not flip to true for an empty task name)
      expect(comp.loadingParameterNames).toBe(false);
   });

});

// ════════════════════════════════════════════════════════════════════════════
// Group 4 [Risk 2] — editQuery() / editEmbedded(): dialog result handling
// ════════════════════════════════════════════════════════════════════════════

describe("BatchActionEditorComponent — editQuery() / editEmbedded(): dialog result", () => {

   // 🔁 Regression-sensitive: a truthy dialog result must update actionModel and fire modelChanged.
   // A null/undefined result (cancel) must leave actionModel untouched.
   it("should update queryEntry and queryParameters and emit when editQuery dialog closes with result", async () => {
      const dialogResult = {
         queryEntry: { path: "newEntry" },
         queryParameters: [{ name: "p1" }],
      };

      const { comp, dialogMock } = await renderComp({
         dialogClosesWith: dialogResult,
         actionModel: makeActionModel({ taskName: "TaskA" }),
      });

      const emitted: any[] = [];
      comp.modelChanged.subscribe(v => emitted.push(v));

      comp.editQuery();

      await waitFor(() => expect(emitted.length).toBeGreaterThan(0));
      expect(comp.actionModel.queryEntry).toEqual({ path: "newEntry" });
      expect(comp.actionModel.queryParameters).toEqual([{ name: "p1" }]);
   });

   // Risk Point/Contract: null dialog result (user cancelled) must leave actionModel unchanged
   // and must NOT fire modelChanged.
   it("should not update actionModel or emit when editEmbedded dialog closes with null", async () => {
      const { comp, dialogMock } = await renderComp({
         dialogClosesWith: null,
         actionModel: makeActionModel({ taskName: "TaskA", embeddedParameters: [[{ name: "orig" } as any]] }),
      });

      const emitted: any[] = [];
      comp.modelChanged.subscribe(v => emitted.push(v));

      comp.editEmbedded();

      await waitFor(() => expect(dialogMock.open).toHaveBeenCalled());
      expect(emitted.length).toBe(0);
      expect(comp.actionModel.embeddedParameters).toEqual([[{ name: "orig" }]]);
   });

});

// ════════════════════════════════════════════════════════════════════════════
// Group 5 [Risk 2] — actionModel setter: fetchParameters() only on name change
// ════════════════════════════════════════════════════════════════════════════

describe("BatchActionEditorComponent — actionModel setter: conditional fetchParameters", () => {

   // 🔁 Regression-sensitive: setting actionModel with a DIFFERENT task name must trigger
   // fetchParameters() so the parameter list refreshes for the newly selected task.
   it("should call fetchParameters when actionModel is set with a new task name", async () => {
      server.use(
         http.get(PARAMETERS_URL, () => HttpResponse.json({ parameterNames: ["newParam"] }))
      );

      const { comp } = await renderComp({
         actionModel: makeActionModel({ taskName: "OldTask" }),
      });
      comp.selectedTaskName = "OldTask";

      comp.actionModel = makeActionModel({ taskName: "NewTask" });

      await waitFor(() => expect(comp.parameterNames).toEqual(["newParam"]));
   });

   // Risk Point/Contract: setting actionModel with the SAME task name must NOT re-trigger
   // fetchParameters() — prevents redundant HTTP calls and parameter list flickering.
   // Note: the actionModel setter fires once during render (selectedTaskName goes from
   // undefined → "TaskA"), triggering one initial fetch. We wait for that to settle,
   // then reassign with the same name and verify no additional fetch occurs.
   it("should not fetch parameters when actionModel is set with the same task name", async () => {
      const fetchSpy = jest.fn();
      server.use(http.get(PARAMETERS_URL, () => { fetchSpy(); return HttpResponse.json({ parameterNames: [] }); }));

      const { comp } = await renderComp({
         actionModel: makeActionModel({ taskName: "TaskA" }),
      });
      // Wait for the initial fetch triggered by actionModel setter (undefined → "TaskA") to settle
      await waitFor(() => expect(comp.loadingParameterNames).toBe(false));
      fetchSpy.mockClear(); // clear the initial fetch count

      comp.actionModel = makeActionModel({ taskName: "TaskA" }); // same name → no new fetch

      await waitFor(() => {
         expect(comp.loadingParameterNames).toBe(false);
         expect(fetchSpy).not.toHaveBeenCalled();
      });
   });

});
