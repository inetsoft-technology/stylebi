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
 * ScheduleTaskEditorPageComponent — Testing Library style
 *
 * Risk-first coverage:
 *   Group 1  [Risk 3]  — save(): taskChanged reset before HTTP response (it.failing — confirmed Bug A)
 *   Group 2  [Risk 2]  — valid getter: all sub-validators must pass simultaneously
 *   Group 3  [Risk 2]  — addAction / appendAction: new action always starts invalid
 *   Group 4  [Risk 2]  — copyCondition / copyAction: nested COPY_OF_PREFIX stripping
 *   Group 5  [Risk 2]  — canDeleteConditions / canDeleteActions: last-item protection
 *   Group 6  [Risk 2]  — scheduleSaveGuard: internalTask disabled form — form.value includes disabled control value (guard is safe)
 *   Group 7  [Risk 3]  — appendCondition: crash when timeZoneOptions is empty or null (it.failing — confirmed Bug C)
 *   Group 8  [Risk 2]  — model.timeZone: client-locale derivation via toLocaleDateString().slice(4)
 *   Group 9  [Risk 2]  — appendCondition: new condition always defaults to timeZoneOptions[0] (client local TZ)
 *   Group 10 [Risk 2]  — save(): orgId from PageHeaderService included in request payload
 *   Group 11 [Risk 2]  — save(): selectedConditionIndex not clamped after updateLists (it.failing — confirmed Bug D)
 *
 * Confirmed bugs (it.failing — remove wrapper once fixed):
 *
 *   Bug A — save() resets taskChanged before HTTP response (Group 1):
 *     `this.taskChanged = false` runs synchronously BEFORE the HTTP response arrives.
 *     On save failure, taskChanged stays false, permanently disabling the Save button.
 *     Fix: move `this.taskChanged = false` inside the subscribe success callback.
 *
 *   Bug C — appendCondition: crash on empty/null timeZoneOptions (Group 7):
 *     appendCondition() reads `this.model.timeZoneOptions[0].timeZoneId` without
 *     guarding against an empty or null array. Results in a TypeError crash when the
 *     server returns no timezone options or the array is cleared after load.
 *     Fix: guard with a null/empty check before accessing index 0.
 *
 *   Bug D — selectedConditionIndex not clamped after save with fewer conditions (Group 11):
 *     After save, updateLists() rebuilds conditionItems from the server response but does
 *     not clamp selectedConditionIndex. If the server returns fewer conditions than the
 *     current index, the condition editor shows an empty placeholder instead of the
 *     first available condition.
 *     Fix: after updateLists(), clamp selectedConditionIndex to conditionItems.length - 1.
 *
 * KEY contracts:
 *   - `valid` = `(form.disabled || form.valid) && taskChanged && conditionsValid && actionsValid && optionsValid`
 *   - Every new action added via addAction() starts with item.valid=false (actionsValid=false).
 *   - COPY_OF_PREFIX is "_#(js:Copy of) "; repeated copies strip all existing prefixes before adding one.
 *   - canDelete* = selectedIndex >= 0 AND list.length > 1 (last item cannot be deleted).
 *   - appendCondition() always assigns timeZoneOptions[0] as the new condition's timezone.
 *   - save() payload always includes orgId from PageHeaderService.currentOrgId (may be null).
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { provideHttpClient } from "@angular/common/http";
import { ReactiveFormsModule } from "@angular/forms";
import { MatDialog } from "@angular/material/dialog";
import { MatSnackBar } from "@angular/material/snack-bar";
import { ActivatedRoute, Router } from "@angular/router";
import { render } from "@testing-library/angular";
import { http, HttpResponse as MswHttpResponse } from "msw";
import { NEVER, of, throwError } from "rxjs";

import { it } from "@jest/globals";
import { server } from "../../../../../../../mocks/server";
import { ScheduleTaskEditorPageComponent, TaskItem } from "./schedule-task-editor-page.component";
import { ScheduleTaskEditorDataService } from "./schedule-task-editor-data.service";
import { PageHeaderService } from "../../../page-header/page-header.service";
import { TimeZoneService } from "../../../../../../shared/schedule/time-zone.service";
import { ScheduleTaskNamesService } from "../../../../../../shared/schedule/schedule-task-names.service";
import { ScheduleTaskDialogModel } from "../../../../../../shared/schedule/model/schedule-task-dialog-model";
import { TimeConditionModel } from "../../../../../../shared/schedule/model/time-condition-model";

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

function makeDialogModel(overrides: Partial<ScheduleTaskDialogModel> = {}): ScheduleTaskDialogModel {
   return {
      name: "TestTask",
      label: "Test Task",
      internalTask: false,
      timeZone: "EST",
      timeZoneOptions: [{ timeZoneId: "America/New_York", label: "EST" }],
      taskConditionPaneModel: {
         conditions: [{ label: "Cond 1", conditionType: "TimeCondition" } as any],
         timeZoneOffset: 0
      },
      taskActionPaneModel: {
         actions: [{ label: "Action 1", actionType: "ViewsheetAction", actionClass: "GeneralActionModel" }]
      },
      taskOptionsPaneModel: {} as any,
      ...overrides,
   } as any;
}

// ---------------------------------------------------------------------------
// Render helper
// ---------------------------------------------------------------------------

async function renderComponent(opts: {
   orgId?: string | null;
   model?: ScheduleTaskDialogModel;
} = {}) {
   const model = opts.model ?? makeDialogModel();

   server.use(
      http.get("*/api/em/schedule/edit", () => MswHttpResponse.json(model)),
   );

   const dialogMock = { open: jest.fn().mockReturnValue({ afterClosed: () => of(false) }) };
   const snackBarMock = { open: jest.fn() };
   const routerMock = { navigate: jest.fn() };

   const result = await render(ScheduleTaskEditorPageComponent, {
      imports: [ReactiveFormsModule],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
         provideHttpClient(),
         { provide: MatDialog,    useValue: dialogMock },
         { provide: MatSnackBar,  useValue: snackBarMock },
         { provide: Router,       useValue: routerMock },
         {
            provide: ActivatedRoute,
            useValue: { params: of({ task: "TestTask" }), snapshot: { queryParams: {} } }
         },
         {
            provide: PageHeaderService,
            useValue: { title: "", currentOrgId: opts.orgId !== undefined ? opts.orgId : "org1" }
         },
         { provide: TimeZoneService, useValue: {
            updateTimeZoneOptions: jest.fn((tzOpts: any) => tzOpts)
         }},
         { provide: ScheduleTaskNamesService, useValue: { loadScheduleTaskNames: jest.fn() } },
         ScheduleTaskEditorDataService,
      ],
   });

   await result.fixture.whenStable();
   // Allow MSW HTTP response (async) to be processed by Angular
   await new Promise(r => setTimeout(r, 50));
   await result.fixture.whenStable();

   return { ...result, comp: result.fixture.componentInstance, dialogMock, snackBarMock, routerMock };
}

// ---------------------------------------------------------------------------
// Group 1 [Risk 3] — save(): taskChanged reset before HTTP response
// ---------------------------------------------------------------------------

describe("ScheduleTaskEditorPageComponent — save(): taskChanged timing", () => {

   // 🔁 Regression-sensitive: after a successful save, taskChanged must be false —
   // the task is now in sync with the server.
   it("should set taskChanged=false after a successful save", async () => {
      const { comp } = await renderComponent();

      server.use(
         http.post("*/api/em/schedule/task/save", () => MswHttpResponse.json(makeDialogModel()))
      );
      comp.taskChanged = true;

      comp.save();
      await new Promise(r => setTimeout(r, 50));

      expect(comp.taskChanged).toBe(false);
   });

   // 🔁 Regression-sensitive (Bug A — confirmed):
   //   `taskChanged = false` is set synchronously BEFORE the HTTP response arrives.
   //   Even before any failure occurs, `taskChanged` is already false — retry is impossible.
   //   Using NEVER so the observable never resolves/rejects (no unhandled rejection noise).
   //  Issue #74514
   it.failing("should keep taskChanged=true after a failed save so the user can retry", async () => {
      const { comp } = await renderComponent();

      // NEVER: observable that neither emits nor errors — isolates the synchronous bug
      jest.spyOn(comp["dataService"], "saveTask").mockReturnValue(NEVER);

      comp.taskChanged = true;
      comp.save(); // synchronously sets taskChanged=false (the bug)

      // After triggering save (even before any response), taskChanged must stay true
      // so the user can still retry — this fails because of the synchronous reset
      expect(comp.taskChanged).toBe(true);
   });

   // Boundary: valid getter reflects taskChanged immediately after reset.
   it("should set valid=false immediately when taskChanged is false", async () => {
      const { comp } = await renderComponent();

      comp.taskChanged = false;
      comp.form.controls["taskName"].setValue("ValidName");

      expect(comp.valid).toBe(false);
      expect(comp.taskChanged).toBe(false); // taskChanged is the cause
      expect(comp.form.valid).toBe(true);   // form is not the cause
   });

});

// ---------------------------------------------------------------------------
// Group 2 [Risk 2] — valid getter: composite sub-validator contract
// ---------------------------------------------------------------------------

describe("ScheduleTaskEditorPageComponent — valid getter: all sub-validators", () => {

   // 🔁 Regression-sensitive: all four sub-validators must pass simultaneously for valid=true.
   it("should return true only when taskChanged, form valid, and all items valid", async () => {
      const { comp } = await renderComponent();

      comp.taskChanged = true;
      comp.form.controls["taskName"].setValue("ValidName");
      comp.conditionItems = [new TaskItem("c1", "Cond"), Object.assign(new TaskItem("c2", "Cond 2"), { valid: true })];
      comp.actionItems  = [new TaskItem("a1", "Act"),  Object.assign(new TaskItem("a2", "Act 2"),  { valid: true })];

      expect(comp.valid).toBe(true);
   });

   // 🔁 Regression-sensitive: one invalid action item must block valid even when everything else is fine.
   it("should return false when any action item is invalid", async () => {
      const { comp } = await renderComponent();

      comp.taskChanged = true;
      comp.form.controls["taskName"].setValue("ValidName");
      comp.actionItems = [Object.assign(new TaskItem("a1", "Act"), { valid: false })];

      expect(comp.valid).toBe(false);
      expect(comp.actionsValid).toBe(false);  // actionsValid is the cause
      expect(comp.conditionsValid).toBe(true); // not caused by conditions
   });

});

// ---------------------------------------------------------------------------
// Group 3 [Risk 2] — addAction: new action starts invalid
// ---------------------------------------------------------------------------

describe("ScheduleTaskEditorPageComponent — addAction: new item starts invalid", () => {

   // 🔁 Regression-sensitive: addAction() appends a TaskItem with valid=false,
   // so actionsValid immediately becomes false and the Save button is blocked
   // until the user configures the new action.
   it("should set actionsValid=false and set selectedActionIndex to the new item after addAction", async () => {
      const { comp } = await renderComponent();

      const prevLength = comp.actionItems.length;
      comp.addAction();

      expect(comp.actionItems).toHaveLength(prevLength + 1);
      expect(comp.actionItems[comp.selectedActionIndex].valid).toBe(false);
      expect(comp.actionsValid).toBe(false); // new item is the cause
   });

   // Happy: once the new action is configured (valid=true via onActionChanged), actionsValid recovers.
   it("should set actionsValid=true after the new action reports valid state", async () => {
      const { comp } = await renderComponent();

      comp.addAction();
      const newIndex = comp.selectedActionIndex;
      comp.actionItems[newIndex].valid = true;

      expect(comp.actionsValid).toBe(true);
   });

});

// ---------------------------------------------------------------------------
// Group 4 [Risk 2] — copyCondition / copyAction: COPY_OF_PREFIX stripping
// ---------------------------------------------------------------------------

describe("ScheduleTaskEditorPageComponent — copyCondition: label prefix handling", () => {

   // 🔁 Regression-sensitive: copying a condition must prepend COPY_OF_PREFIX to the base label.
   it("should prepend COPY_OF_PREFIX to the condition label on copy", async () => {
      const { comp } = await renderComponent();

      comp.selectedConditionIndex = 0;
      comp.conditionItems[0].label = "Morning Run";
      comp.model.taskConditionPaneModel.conditions[0].label = "Morning Run";

      comp.copyCondition();

      const copiedLabel = comp.conditionItems[comp.selectedConditionIndex].label;
      expect(copiedLabel).toMatch(/^_#\(js:Copy of\) Morning Run/);
   });

   // 🔁 Regression-sensitive: "Copy of Copy of X" must not double-nest the prefix —
   // the while-loop strips all existing prefixes before adding one fresh prefix.
   it("should strip nested COPY_OF_PREFIX before adding one when copying a copy", async () => {
      const { comp } = await renderComponent();

      const prefix = "_#(js:Copy of) ";
      comp.selectedConditionIndex = 0;
      const nestedLabel = `${prefix}${prefix}Original`;
      comp.conditionItems[0].label = nestedLabel;
      comp.model.taskConditionPaneModel.conditions[0] = { label: nestedLabel } as any;

      comp.copyCondition();

      const copiedLabel = comp.conditionItems[comp.selectedConditionIndex].label;
      expect(copiedLabel).toBe(`${prefix}Original`);
   });

});

// ---------------------------------------------------------------------------
// Group 5 [Risk 2] — canDeleteConditions / canDeleteActions
// ---------------------------------------------------------------------------

describe("ScheduleTaskEditorPageComponent — canDelete: last-item protection", () => {

   // 🔁 Regression-sensitive: the last condition must never be deletable — the UI
   // disables the delete button when canDeleteConditions=false.
   it("should return false for canDeleteConditions when only one condition exists", async () => {
      const { comp } = await renderComponent();

      comp.conditionItems = [new TaskItem("c1", "Only Condition")];
      comp.selectedConditionIndex = 0;

      expect(comp.canDeleteConditions).toBe(false);
   });

   // Happy: two or more conditions with a valid selection → delete is allowed.
   it("should return true for canDeleteConditions when multiple conditions exist and one is selected", async () => {
      const { comp } = await renderComponent();

      comp.conditionItems = [new TaskItem("c1", "Cond 1"), new TaskItem("c2", "Cond 2")];
      comp.selectedConditionIndex = 0;

      expect(comp.canDeleteConditions).toBe(true);
   });

});

// ---------------------------------------------------------------------------
// Group 6 [Risk 2] — scheduleSaveGuard: internalTask form.value and guard safety
// ---------------------------------------------------------------------------

describe("ScheduleTaskEditorPageComponent — scheduleSaveGuard: internalTask disabled form", () => {

   // Happy: regular (non-internal) task — form enabled, form.value["taskName"] equals model.label.
   it("should have form.value.taskName equal to model.label for a regular task", async () => {
      const { comp } = await renderComponent();

      expect(comp.form.value["taskName"]).toBe(comp.model.label);
   });

   // 🔁 Regression-sensitive: Angular 17's FormGroup.value includes disabled controls' values
   //   (unlike older Angular which excluded them). The guard reads form.value["taskName"] and
   //   compares to model.label — for internalTask these must still match so no false positive fires.
   it("should have form.value.taskName equal to model.label for internalTask (disabled control value is still included)", async () => {
      const internalModel = makeDialogModel({ internalTask: true, name: "SystemTask", label: "System Task" });
      const { comp } = await renderComponent({ model: internalModel });

      // Angular 17: FormGroup.value includes the value of disabled controls.
      // form.value["taskName"] === model.label → guard condition is false → no false positive.
      expect(comp.form.value["taskName"]).toBe(comp.model.label);
   });

   // Boundary: confirm the taskName control is actually disabled for internalTask,
   // so future Angular upgrades that change form.value behavior will surface here first.
   it("should disable the taskName control for internalTask", async () => {
      const internalModel = makeDialogModel({ internalTask: true, name: "SystemTask", label: "System Task" });
      const { comp } = await renderComponent({ model: internalModel });

      expect(comp.form.controls["taskName"].disabled).toBe(true);
   });

});

// ---------------------------------------------------------------------------
// Group 7 [Risk 3] — appendCondition: crash when timeZoneOptions is empty (Bug C)
// ---------------------------------------------------------------------------

describe("ScheduleTaskEditorPageComponent — appendCondition: timeZoneOptions empty crash", () => {

   // 🔁 Regression-sensitive (Bug C — confirmed):
   //   appendCondition() accesses timeZoneOptions[0].timeZoneId without a null/empty guard.
   //   Results in TypeError: Cannot read properties of undefined.
   // Note: In normal UI flow, timeZoneOptions is expected to be populated by the backend and the
   // timezone selector won't appear "empty". However, the API contract allows timeZoneOptions to
   // be missing/null (TS: optional; backend DTO: @Nullable) and the model is mutable, so we must
   // defensively handle []/null to avoid runtime crashes.
   it.failing("should not throw when addCondition is called with an empty timeZoneOptions array", async () => {
      const { comp } = await renderComponent();
      comp.model.timeZoneOptions = [];

      // Bug: timeZoneOptions[0] is undefined → TypeError
      expect(() => comp.addCondition()).not.toThrow();
   });

   // Boundary: same crash path when timeZoneOptions itself is null.
   it.failing("should not throw when addCondition is called with null timeZoneOptions", async () => {
      const { comp } = await renderComponent();
      (comp.model as any).timeZoneOptions = null;

      expect(() => comp.addCondition()).not.toThrow();
   });

});

// ---------------------------------------------------------------------------
// Group 8 [Risk 2] — model.timeZone: client-locale derivation via slice(4)
// ---------------------------------------------------------------------------

describe("ScheduleTaskEditorPageComponent — model.timeZone: toLocaleDateString().slice(4)", () => {

   afterEach(() => jest.restoreAllMocks());

   // Happy: standard 'DD, TimezoneName' format (en-US style) — slice(4) removes "DD, " correctly.
   it("should extract the timezone name by removing the first 4 chars for 'DD, TimezoneName' format", async () => {
      jest.spyOn(Date.prototype, "toLocaleDateString").mockImplementation(
         function(_locales: any, options: any) {
            return (options?.timeZoneName === "long") ? "09, Eastern Standard Time" : "01/01/2024";
         }
      );

      const { comp } = await renderComponent();

      expect(comp.model.timeZone).toBe("Eastern Standard Time");
   });

   // 🔁 Regression-sensitive: slice(4) assumes a fixed 4-char "DD, " prefix.
   //   When the locale uses a single-space separator ("DD TimezoneName"), the first character
   //   of the timezone name is silently dropped.
   it.failing("should extract the full timezone name when the day-to-timezone separator is only one char", async () => {
      jest.spyOn(Date.prototype, "toLocaleDateString").mockImplementation(
         function(_locales: any, options: any) {
            // Single-space separator: "09 Central European Time"
            // slice(4) → "entral European Time" (missing leading "C")
            return (options?.timeZoneName === "long") ? "09 Central European Time" : "01/01/2024";
         }
      );

      const { comp } = await renderComponent();

      // Fails: actual value is "entral European Time"
      expect(comp.model.timeZone).toBe("Central European Time");
   });

});

// ---------------------------------------------------------------------------
// Group 9 [Risk 2] — appendCondition: new condition defaults to timeZoneOptions[0]
// ---------------------------------------------------------------------------

describe("ScheduleTaskEditorPageComponent — appendCondition: new condition inherits client local TZ", () => {

   // 🔁 Regression-sensitive: new conditions must carry timeZoneOptions[0] as their timezone.
   //   TimeZoneService.updateTimeZoneOptions inserts the client local timezone at index 0.
   //   If this contract changes, newly added conditions silently run in the wrong timezone.
   it("should assign timeZoneOptions[0] as the default timezone for a newly added condition", async () => {
      const { comp } = await renderComponent();

      const prevCount = comp.model.taskConditionPaneModel.conditions.length;
      comp.addCondition();

      const newCond = comp.model.taskConditionPaneModel.conditions[prevCount] as TimeConditionModel;
      // timeZoneOptions[0] is "America/New_York" from fixture;
      // in production it is the client local TZ injected by TimeZoneService.updateTimeZoneOptions
      expect(newCond.timeZone).toBe(comp.model.timeZoneOptions[0].timeZoneId);
      expect(newCond.timeZoneLabel).toBe(comp.model.timeZoneOptions[0].label);
   });

});

// ---------------------------------------------------------------------------
// Group 10 [Risk 2] — save(): orgId in request payload
// ---------------------------------------------------------------------------

describe("ScheduleTaskEditorPageComponent — save(): orgId in request payload", () => {

   // 🔁 Regression-sensitive: orgId must flow from PageHeaderService into the save payload
   //   so the server stores the task under the correct organization in multi-tenant mode.
   it("should include PageHeaderService.currentOrgId in the save request body", async () => {
      let capturedBody: any = null;
      server.use(
         http.post("*/api/em/schedule/task/save", async ({ request }) => {
            capturedBody = await request.json();
            return MswHttpResponse.json(makeDialogModel());
         })
      );

      const { comp } = await renderComponent();
      comp.taskChanged = true;
      comp.save();
      await new Promise(r => setTimeout(r, 50));

      expect(capturedBody.orgId).toBe("org1");
   });

   // Boundary: null orgId (non-multi-tenant / global context) must be serialized as null, not omitted.
   // Risk Point: if null is silently dropped, the server may assign the wrong org context.
   it("should pass null orgId in the save payload when PageHeaderService.currentOrgId is null", async () => {
      let capturedBody: any = null;
      server.use(
         http.post("*/api/em/schedule/task/save", async ({ request }) => {
            capturedBody = await request.json();
            return MswHttpResponse.json(makeDialogModel());
         })
      );

      const { comp } = await renderComponent({ orgId: null });
      comp.taskChanged = true;
      comp.save();
      await new Promise(r => setTimeout(r, 50));

      expect(capturedBody.orgId).toBeNull();
   });

});

// ---------------------------------------------------------------------------
// Group 11 [Risk 2] — save(): selectedConditionIndex after updateLists
// ---------------------------------------------------------------------------

describe("ScheduleTaskEditorPageComponent — save(): selectedConditionIndex after updateLists", () => {

   // Happy: same condition count returned by server — selected index stays valid and the
   // condition editor continues to show the previously selected condition.
   it("should preserve a valid selectedConditionIndex when the server returns the same number of conditions", async () => {
      const { comp } = await renderComponent();

      comp.addCondition();
      comp.selectedConditionIndex = 1;

      server.use(
         http.post("*/api/em/schedule/task/save", () => MswHttpResponse.json(makeDialogModel({
            taskConditionPaneModel: {
               conditions: [
                  { label: "Cond 1", conditionType: "TimeCondition" } as any,
                  { label: "New Condition", conditionType: "TimeCondition" } as any,
               ],
               timeZoneOffset: 0
            } as any
         })))
      );

      comp.taskChanged = true;
      comp.save();
      await new Promise(r => setTimeout(r, 50));

      expect(comp.selectedConditionIndex).toBe(1);
      expect(comp.conditionItems[comp.selectedConditionIndex]).toBeDefined();
   });

   // 🔁 Regression-sensitive (Bug D — confirmed):
   //   updateLists() rebuilds conditionItems from the server response but does not clamp
   //   selectedConditionIndex. When the server returns fewer conditions, the index falls
   //   past the end of conditionItems, leaving the condition editor in a blank placeholder state.
   it.failing("should clamp selectedConditionIndex to the last valid index when the server returns fewer conditions", async () => {
      const { comp } = await renderComponent();

      // Build 3 conditions and select the last one (index 2)
      comp.addCondition();
      comp.addCondition();
      comp.selectedConditionIndex = 2;

      // Server returns only the original 1 condition
      server.use(
         http.post("*/api/em/schedule/task/save", () => MswHttpResponse.json(makeDialogModel()))
      );

      comp.taskChanged = true;
      comp.save();
      await new Promise(r => setTimeout(r, 50));

      // Bug: selectedConditionIndex stays at 2; conditionItems.length is now 1
      expect(comp.selectedConditionIndex).toBeLessThan(comp.conditionItems.length);
   });

});
