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
 * ScheduleTaskListComponent — Testing Library style
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — getTaskName: 4-branch task name construction (static method)
 *   Group 2 [Risk 3] — isToggleTasksEnabledDisabled: button guard (5 boundary conditions)
 *   Group 3 [Risk 2] — canEditTask: internal vs normal task permission asymmetry
 *   Group 4 [Risk 3] — mergeChange: real-time task lifecycle (ADDED / UPDATED / REMOVED)
 *   Group 5 [Risk 2] — updateTaskList: WEEK / DAY distribution filtering
 *   Group 6 [Risk 2] — showWeekDistribution: full filter-state reset
 *   Group 7 [Risk 2] — getDistributionDayLabel: index-to-weekday boundary
 *   Group 8 [Risk 2] — timeChronological: form group cross-field validator
 *   Group 9 [Risk 2] — isAllSelected: page-scoped checkbox logic
 *
 * Confirmed bugs (it.failing — remove wrapper once fixed):
 *
 *   Bug A — mergeChange REMOVED lookup fails for SYSTEM_USER tasks (Group 4):
 *     getTaskName() builds the server key as "INETSOFT_SYSTEM~;~orgID__taskName" (double-underscore).
 *     mergeChange() REMOVED lookup builds "convertToKey(owner):taskName" = "INETSOFT_SYSTEM~;~orgID:taskName" (colon).
 *     These never match, so SYSTEM_USER REMOVED change events silently fail to remove the task.
 *
 * KEY contracts:
 *   KEY_DELIMITER = "~;~" separates owner name from orgID in all composed keys.
 *   Internal tasks bypass owner-prefix logic and canDelete checks.
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { ReactiveFormsModule, UntypedFormGroup } from "@angular/forms";
import { HttpClientModule } from "@angular/common/http";
import { RouterTestingModule } from "@angular/router/testing";
import { render } from "@testing-library/angular";
import { http, HttpResponse as MswHttpResponse } from "msw";
import { MatDialog } from "@angular/material/dialog";
import { MatPaginator } from "@angular/material/paginator";
import { MatSnackBar } from "@angular/material/snack-bar";
import { MatBottomSheet } from "@angular/material/bottom-sheet";
import { MatMenuModule } from "@angular/material/menu";
import { DomSanitizer } from "@angular/platform-browser";
import { ErrorStateMatcher } from "@angular/material/core";
import { Subject, EMPTY, of } from "rxjs";
import { ActivatedRoute } from "@angular/router";

import { it } from "@jest/globals";
import { server } from "../../../../../../../mocks/server";
import { ScheduleTaskListComponent, DistributionType } from "./schedule-task-list.component";
import { PageHeaderService } from "../../../page-header/page-header.service";
import { ScheduleUsersService } from "../../../../../../shared/schedule/schedule-users.service";
import { DownloadService } from "../../../../../../shared/download/download.service";
import { ScheduleTaskModel, TaskDistribution } from "../../../../../../shared/schedule/model/schedule-task-model";
import { ScheduleTaskChange } from "../../../../../../shared/schedule/model/schedule-task-change";
import { IdentityId } from "../../security/users/identity-id";
import { StompClientService } from "../../../../../../shared/stomp/stomp-client.service";

// ---------------------------------------------------------------------------
// Constants mirrored from component (file-private there)
// ---------------------------------------------------------------------------

const KEY_DELIMITER = "~;~";
const SYSTEM_USER   = "INETSOFT_SYSTEM";
const ASSET_FILE_BACKUP          = "__asset file backup__";
const BALANCE_TASKS              = "__balance tasks__";
const UPDATE_ASSETS_DEPENDENCIES = "__update assets dependencies__";

// ---------------------------------------------------------------------------
// Shared fixtures
// ---------------------------------------------------------------------------

const ALICE_OWNER: IdentityId  = { name: "alice", orgID: "org1" };
const SYSTEM_OWNER: IdentityId = { name: SYSTEM_USER, orgID: "org1" };

function makeTask(name: string, overrides: Partial<ScheduleTaskModel> = {}): ScheduleTaskModel {
   return {
      name,
      label: name,
      description: "",
      owner: ALICE_OWNER,
      path: "/",
      schedule: "",
      editable: true,
      removable: true,
      canDelete: true,
      enabled: true,
      distribution: { days: [] },
      ...overrides,
   };
}

function makeDayDistribution(weekdayIndex: number): TaskDistribution {
   return { days: [{ index: weekdayIndex, hardCount: 1, softCount: 0, children: [] }] };
}

// ---------------------------------------------------------------------------
// MSW helpers
// ---------------------------------------------------------------------------

function setupDefaultEndpoints() {
   server.use(
      http.get("*/api/em/schedule/change-show-type", () =>
         MswHttpResponse.json(false)
      ),
      http.get("*/api/em/schedule/folder/checkRootPermission", () =>
         MswHttpResponse.json(true)
      ),
      http.post("*/api/em/schedule/scheduled-tasks", () =>
         MswHttpResponse.json({
            tasks: [],
            timeZone: "UTC",
            timeZoneId: "UTC",
            timeZoneOffset: 0,
            dateTimeFormat: "yyyy-MM-dd HH:mm:ss",
            showOwners: false,
         })
      ),
      http.get("*/api/em/schedule/distribution/chart", () =>
         MswHttpResponse.json({ values: [], image: "data:image/png;base64,abc" })
      ),
   );
}

// ---------------------------------------------------------------------------
// Render helper
// ---------------------------------------------------------------------------

async function renderComponent() {
   setupDefaultEndpoints();

   const result = await render(ScheduleTaskListComponent, {
      imports: [ReactiveFormsModule, HttpClientModule, RouterTestingModule, MatMenuModule],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
         { provide: PageHeaderService, useValue: { title: "", currentOrgId: null } },
         { provide: MatDialog, useValue: { open: jest.fn().mockReturnValue({ afterClosed: () => of(false) }) } },
         { provide: MatSnackBar, useValue: { open: jest.fn() } },
         { provide: MatBottomSheet, useValue: { open: jest.fn() } },
         { provide: ScheduleUsersService, useValue: { getEmailUsers: () => of([]), getEmailGroups: () => of([]) } },
         { provide: DownloadService, useValue: { download: jest.fn() } },
         { provide: ActivatedRoute, useValue: { queryParamMap: of({ get: () => null }) } },
         { provide: DomSanitizer, useValue: { bypassSecurityTrustResourceUrl: (url: string) => url } },
         { provide: ErrorStateMatcher, useValue: { isErrorState: () => false } },
         { provide: StompClientService, useValue: { connect: () => EMPTY } },
      ],
   });

   await result.fixture.whenStable();
   return { ...result, comp: result.fixture.componentInstance };
}

// ---------------------------------------------------------------------------
// Group 1 [Risk 3] — getTaskName: 4-branch key construction (static, no render needed)
// ---------------------------------------------------------------------------

describe("ScheduleTaskListComponent — getTaskName: 4-branch task name construction", () => {

   // 🔁 Regression-sensitive: internal tasks must never gain an owner prefix; they are
   // matched by exact name across the application.
   it("should return the task name as-is for internal tasks", () => {
      const task = makeTask(ASSET_FILE_BACKUP);
      expect(ScheduleTaskListComponent.getTaskName(task)).toBe(ASSET_FILE_BACKUP);
   });

   it("should return balance-tasks internal name unchanged", () => {
      const task = makeTask(BALANCE_TASKS);
      expect(ScheduleTaskListComponent.getTaskName(task)).toBe(BALANCE_TASKS);
   });

   // 🔁 Regression-sensitive: SYSTEM_USER tasks use double-underscore (__) as separator,
   // NOT the colon used for normal users. mergeChange must mirror this exactly.
   it("should build SYSTEM_USER key with double-underscore separator", () => {
      const task = makeTask("myTask", { owner: SYSTEM_OWNER });
      const key = ScheduleTaskListComponent.getTaskName(task);
      expect(key).toBe(`${SYSTEM_USER}${KEY_DELIMITER}org1__myTask`);
   });

   // 🔁 Regression-sensitive: normal user tasks use colon (:) as separator.
   it("should build normal-user key with colon separator when task name is not prefixed", () => {
      const task = makeTask("myReport", { owner: ALICE_OWNER });
      const key = ScheduleTaskListComponent.getTaskName(task);
      expect(key).toBe(`alice${KEY_DELIMITER}org1:myReport`);
   });

   // Branch 4: task name already starts with owner name → return as-is.
   it("should return the task name unchanged when it already starts with the owner name", () => {
      const alreadyPrefixed = `alice${KEY_DELIMITER}org1:myReport`;
      const task = makeTask(alreadyPrefixed, { owner: ALICE_OWNER });
      expect(ScheduleTaskListComponent.getTaskName(task)).toBe(alreadyPrefixed);
   });

   // Branch 4 (MV Task): MV Task names must not be owner-prefixed.
   it("should return MV Task name unchanged without owner prefix", () => {
      const task = makeTask("MV Task: SomeMV", { owner: ALICE_OWNER });
      expect(ScheduleTaskListComponent.getTaskName(task)).toBe("MV Task: SomeMV");
   });

});

// ---------------------------------------------------------------------------
// Group 2 [Risk 3] — isToggleTasksEnabledDisabled: button guard (5 boundary conditions)
// ---------------------------------------------------------------------------

describe("ScheduleTaskListComponent — isToggleTasksEnabledDisabled: button guard", () => {

   // 🔁 Regression-sensitive: an empty selection must always disable the toggle button.
   it("should return true (disabled) when selection is empty", async () => {
      const { comp } = await renderComponent();
      comp.selection.clear();
      expect(comp.isToggleTasksEnabledDisabled()).toBe(true);
   });

   // 🔁 Regression-sensitive: mixed enabled/disabled states across selection must disable toggle.
   it("should return true (disabled) when selected tasks have mixed enabled states", async () => {
      const { comp } = await renderComponent();
      const t1 = makeTask("t1", { enabled: true });
      const t2 = makeTask("t2", { enabled: false });
      comp.selection.select(t1, t2);
      expect(comp.isToggleTasksEnabledDisabled()).toBe(true);
   });

   // 🔁 Regression-sensitive: a non-editable task in the selection must disable the toggle.
   it("should return true (disabled) when any selected task is not editable", async () => {
      const { comp } = await renderComponent();
      const t1 = makeTask("t1", { editable: true, canDelete: true });
      const t2 = makeTask("t2", { editable: false, canDelete: true });
      comp.selection.select(t1, t2);
      expect(comp.isToggleTasksEnabledDisabled()).toBe(true);
   });

   // 🔁 Regression-sensitive: normal (non-internal) tasks need canDelete=true to enable toggle.
   it("should return true (disabled) when a normal task lacks canDelete", async () => {
      const { comp } = await renderComponent();
      const t = makeTask("normalTask", { editable: true, canDelete: false });
      comp.selection.select(t);
      expect(comp.isToggleTasksEnabledDisabled()).toBe(true);
   });

   // 🔁 Regression-sensitive: internal tasks bypass the canDelete check —
   // enabled + editable is sufficient regardless of canDelete.
   it("should return false (enabled) for a single internal task that is editable with canDelete=false", async () => {
      const { comp } = await renderComponent();
      const t = makeTask(ASSET_FILE_BACKUP, { editable: true, canDelete: false, enabled: true });
      comp.selection.select(t);
      expect(comp.isToggleTasksEnabledDisabled()).toBe(false);
   });

   // Happy: all same enabled state, editable, canDelete → enabled.
   it("should return false (enabled) when all selected tasks are uniform and editable with canDelete", async () => {
      const { comp } = await renderComponent();
      const t1 = makeTask("t1", { enabled: true, editable: true, canDelete: true });
      const t2 = makeTask("t2", { enabled: true, editable: true, canDelete: true });
      comp.selection.select(t1, t2);
      expect(comp.isToggleTasksEnabledDisabled()).toBe(false);
   });

});

// ---------------------------------------------------------------------------
// Group 3 [Risk 2] — canEditTask: internal vs normal task permission asymmetry
// ---------------------------------------------------------------------------

describe("ScheduleTaskListComponent — canEditTask: internal vs normal task permission", () => {

   // 🔁 Regression-sensitive: internal tasks only need editable=true; canDelete is irrelevant.
   // If this asymmetry is removed, internal task actions will incorrectly be blocked.
   it("should allow editing an internal task when editable=true even if canDelete=false", async () => {
      const { comp } = await renderComponent();
      const task = makeTask(ASSET_FILE_BACKUP, { editable: true, canDelete: false });
      expect(comp.canEditTask(task)).toBe(true);
   });

   it("should deny editing an internal task when editable=false", async () => {
      const { comp } = await renderComponent();
      const task = makeTask(BALANCE_TASKS, { editable: false, canDelete: true });
      expect(comp.canEditTask(task)).toBe(false);
   });

   // 🔁 Regression-sensitive: normal tasks require BOTH editable AND canDelete.
   // Granting canDelete-only or editable-only must not unlock the edit action.
   it("should deny editing a normal task when editable=true but canDelete=false", async () => {
      const { comp } = await renderComponent();
      const task = makeTask("myReport", { editable: true, canDelete: false });
      expect(comp.canEditTask(task)).toBe(false);
   });

   it("should allow editing a normal task when both editable=true and canDelete=true", async () => {
      const { comp } = await renderComponent();
      const task = makeTask("myReport", { editable: true, canDelete: true });
      expect(comp.canEditTask(task)).toBe(true);
   });

});

// ---------------------------------------------------------------------------
// Group 4 [Risk 3] — mergeChange: real-time task lifecycle (ADDED / UPDATED / REMOVED)
// ---------------------------------------------------------------------------

describe("ScheduleTaskListComponent — mergeChange: real-time task lifecycle", () => {

   // 🔁 Regression-sensitive: ADDED events must append the new task to the list.
   it("should push a new task onto the list for an ADDED change event", async () => {
      const { comp, fixture } = await renderComponent();
      // Enable showTasksAsList so ADDED tasks are accepted regardless of folder
      comp.showTasksAsList = true;
      (comp as any).setTasks([]);

      const newTask = makeTask("brandNew");
      const change: ScheduleTaskChange = { name: "brandNew", task: newTask, type: "ADDED" };
      (comp as any).mergeChange(change);
      await fixture.whenStable();

      expect(comp.tasks.some(t => t.name === "brandNew")).toBe(true);
   });

   // 🔁 Regression-sensitive: UPDATED events must replace the matching task in-place.
   it("should replace an existing task in-place for an UPDATED change event", async () => {
      const { comp, fixture } = await renderComponent();
      const original = makeTask("taskA", { label: "Original" });
      (comp as any).setTasks([original]);

      const updated = makeTask("taskA", { label: "Updated" });
      const change: ScheduleTaskChange = { name: "taskA", task: updated, type: "UPDATED" };
      (comp as any).mergeChange(change);
      await fixture.whenStable();

      const found = comp.tasks.find(t => t.name === "taskA");
      expect(found?.label).toBe("Updated");
   });

   // 🔁 Regression-sensitive: REMOVED events must splice the task from the list by key match.
   // mergeChange builds the lookup key as `convertToKey(owner) + ":" + name`, which for a
   // normal user resolves to `alice~;~org1:taskA`. This must match the change.name produced
   // by getTaskName() for a normal user.
   it("should remove a normal-user task from the list for a REMOVED change event", async () => {
      const { comp, fixture } = await renderComponent();
      const task = makeTask("taskA", { owner: ALICE_OWNER });
      (comp as any).setTasks([task]);

      // getTaskName() → "alice~;~org1:taskA" (matches mergeChange lookup)
      const removeName = `alice${KEY_DELIMITER}org1:taskA`;
      const change: ScheduleTaskChange = { name: removeName, task: null, type: "REMOVED" };
      (comp as any).mergeChange(change);
      await fixture.whenStable();

      expect(comp.tasks.find(t => t.name === "taskA")).toBeUndefined();
   });

   // 🔁 Regression-sensitive (Bug A — fixed):
   //   mergeChange now uses getTaskName(t) for the REMOVED lookup, so SYSTEM_USER tasks
   //   correctly match "INETSOFT_SYSTEM~;~org1__taskA" (double-underscore) instead of
   //   the old hardcoded colon separator "INETSOFT_SYSTEM~;~org1:taskA".
   //   Issue #74503
   it("should remove a SYSTEM_USER task from the list for a REMOVED change event (Bug A)", async () => {
      const { comp, fixture } = await renderComponent();
      const task = makeTask("taskA", { owner: SYSTEM_OWNER });
      (comp as any).setTasks([task]);

      // The correct server-side key as produced by getTaskName():
      const removeName = `${SYSTEM_USER}${KEY_DELIMITER}org1__taskA`;
      const change: ScheduleTaskChange = { name: removeName, task: null, type: "REMOVED" };
      (comp as any).mergeChange(change);
      await fixture.whenStable();

      // Currently fails because mergeChange looks for "INETSOFT_SYSTEM~;~org1:taskA" (colon)
      expect(comp.tasks.find(t => t.name === "taskA")).toBeUndefined();
   });

});

// ---------------------------------------------------------------------------
// Group 5 [Risk 2] — updateTaskList: WEEK / DAY distribution filtering
// ---------------------------------------------------------------------------

describe("ScheduleTaskListComponent — updateTaskList: WEEK / DAY distribution filtering", () => {

   // 🔁 Regression-sensitive: WEEK mode must expose all tasks regardless of distribution data.
   it("should show all tasks in WEEK distribution mode", async () => {
      const { comp, fixture } = await renderComponent();
      const tasks = [
         makeTask("t1", { distribution: { days: [] } }),
         makeTask("t2", { distribution: makeDayDistribution(3) }),
      ];
      (comp as any).setTasks(tasks);
      comp.distributionType = DistributionType.WEEK;
      (comp as any).updateTaskList();
      await fixture.whenStable();

      expect(comp.dataSource.data.length).toBe(2);
   });

   // 🔁 Regression-sensitive: DAY mode must filter to tasks whose distribution.days
   // contains an entry whose index matches distributionWeekday.
   it("should show only tasks matching distributionWeekday in DAY distribution mode", async () => {
      const { comp, fixture } = await renderComponent();
      const tasks = [
         makeTask("wednesday", { distribution: makeDayDistribution(4) }),
         makeTask("friday",    { distribution: makeDayDistribution(6) }),
         makeTask("nodist",    { distribution: { days: [] } }),
      ];
      (comp as any).setTasks(tasks);
      comp.distributionType = DistributionType.DAY;
      comp.distributionWeekday = 4;
      (comp as any).updateTaskList();
      await fixture.whenStable();

      expect(comp.dataSource.data.length).toBe(1);
      expect(comp.dataSource.data[0].name).toBe("wednesday");
   });

});

// ---------------------------------------------------------------------------
// Group 6 [Risk 2] — showWeekDistribution: full filter-state reset
// ---------------------------------------------------------------------------

describe("ScheduleTaskListComponent — showWeekDistribution: full filter-state reset", () => {

   // 🔁 Regression-sensitive: showWeekDistribution() must reset ALL distribution state fields
   // so that DAY/HOUR filters do not bleed into the WEEK view.
   it("should reset distributionType to WEEK and clear weekday, hour, minute state", async () => {
      const { comp, fixture } = await renderComponent();

      // Put component into a DAY/HOUR state first
      comp.distributionType = DistributionType.HOUR;
      comp.distributionWeekday = 3;
      comp.distributionHour = 10;
      comp.distributionMinute = 30;

      comp.showWeekDistribution();
      await fixture.whenStable();

      expect(comp.distributionType).toBe(DistributionType.WEEK);
      expect(comp.distributionWeekday).toBe(0);
      expect(comp.distributionHour).toBe(-1);
      expect(comp.distributionMinute).toBe(-1);
   });

});

// ---------------------------------------------------------------------------
// Group 7 [Risk 2] — getDistributionDayLabel: index-to-weekday boundary
// ---------------------------------------------------------------------------

describe("ScheduleTaskListComponent — getDistributionDayLabel: index-to-weekday boundary", () => {

   // 🔁 Regression-sensitive: index 1 = Sunday (first day in the backend's 1-based mapping).
   it("should return Sunday label for index 1", async () => {
      const { comp } = await renderComponent();
      comp.distributionWeekday = 1;
      expect(comp.getDistributionDayLabel()).toBe("_#(js:Sunday)");
   });

   it("should return Saturday label for index 7", async () => {
      const { comp } = await renderComponent();
      comp.distributionWeekday = 7;
      expect(comp.getDistributionDayLabel()).toBe("_#(js:Saturday)");
   });

   // Boundary: index 0 is the default/unset state — must return null (no label).
   it("should return null for default index 0 (no weekday selected)", async () => {
      const { comp } = await renderComponent();
      comp.distributionWeekday = 0;
      expect(comp.getDistributionDayLabel()).toBeNull();
   });

   it("should return Monday label for index 2", async () => {
      const { comp } = await renderComponent();
      comp.distributionWeekday = 2;
      expect(comp.getDistributionDayLabel()).toBe("_#(js:Monday)");
   });

});

// ---------------------------------------------------------------------------
// Group 8 [Risk 2] — timeChronological: form group cross-field validator
// ---------------------------------------------------------------------------

describe("ScheduleTaskListComponent — timeChronological: form group cross-field validator", () => {

   // 🔁 Regression-sensitive: start time strictly before end time must be valid (null).
   it("should return null (valid) when startTime is before endTime", async () => {
      const { comp } = await renderComponent();
      comp.redistributeForm.get("startTime").setValue("08:00:00");
      comp.redistributeForm.get("endTime").setValue("17:00:00");

      const result = (comp as any).timeChronological(comp.redistributeForm);
      expect(result).toBeNull();
   });

   // 🔁 Regression-sensitive: startDate >= endDate must produce the timeChronological error.
   // This prevents tasks being scheduled to run for zero or negative duration.
   it("should return { timeChronological: true } when startTime equals endTime", async () => {
      const { comp } = await renderComponent();
      comp.redistributeForm.get("startTime").setValue("09:00:00");
      comp.redistributeForm.get("endTime").setValue("09:00:00");

      const result = (comp as any).timeChronological(comp.redistributeForm);
      expect(result).toEqual({ timeChronological: true });
   });

   it("should return { timeChronological: true } when startTime is after endTime", async () => {
      const { comp } = await renderComponent();
      comp.redistributeForm.get("startTime").setValue("18:00:00");
      comp.redistributeForm.get("endTime").setValue("08:00:00");

      const result = (comp as any).timeChronological(comp.redistributeForm);
      expect(result).toEqual({ timeChronological: true });
   });

   // Null group guard: validator must not throw when called with null.
   it("should return null when the form group argument is null", async () => {
      const { comp } = await renderComponent();
      const result = (comp as any).timeChronological(null as unknown as UntypedFormGroup);
      expect(result).toBeNull();
   });

});

// ---------------------------------------------------------------------------
// Group 9 [Risk 2] — isAllSelected: page-scoped checkbox logic
// ---------------------------------------------------------------------------

describe("ScheduleTaskListComponent — isAllSelected: page-scoped checkbox logic", () => {

   // 🔁 Regression-sensitive: isAllSelected checks only the current PAGE of filteredData,
   // not the entire task list. This boundary must hold after pagination refactors.
   it("should return true when all tasks on the current page are in the selection", async () => {
      const { comp } = await renderComponent();
      const tasks = [makeTask("t1"), makeTask("t2"), makeTask("t3")];

      // Mock paginator with pageIndex=0, pageSize=10 (all tasks fit on one page)
      comp.paginator = { pageIndex: 0, pageSize: 10 } as unknown as MatPaginator;
      comp.dataSource.data = tasks;

      // Select all tasks
      comp.selection.select(...tasks);

      expect(comp.isAllSelected()).toBe(true);
   });

   // 🔁 Regression-sensitive: if even one page item is missing from selection, return false.
   it("should return false when at least one task on the current page is not selected", async () => {
      const { comp } = await renderComponent();
      const tasks = [makeTask("t1"), makeTask("t2"), makeTask("t3")];

      comp.paginator = { pageIndex: 0, pageSize: 10 } as unknown as MatPaginator;
      comp.dataSource.data = tasks;

      // Select only two of three
      comp.selection.select(tasks[0], tasks[1]);

      expect(comp.isAllSelected()).toBe(false);
   });

   // Boundary: empty selection must return false immediately (short-circuit guard).
   it("should return false when selection is empty", async () => {
      const { comp } = await renderComponent();
      const tasks = [makeTask("t1")];

      comp.paginator = { pageIndex: 0, pageSize: 10 } as unknown as MatPaginator;
      comp.dataSource.data = tasks;
      comp.selection.clear();

      expect(comp.isAllSelected()).toBe(false);
   });

   // Page boundary: tasks on page 1 (index=1) must not affect isAllSelected for page 0.
   it("should only consider page-0 items when paginator is on page 0 with pageSize=2", async () => {
      const { comp } = await renderComponent();
      const tasks = [makeTask("p0t1"), makeTask("p0t2"), makeTask("p1t1")];

      comp.paginator = { pageIndex: 0, pageSize: 2 } as unknown as MatPaginator;
      comp.dataSource.data = tasks;

      // Select only the first-page tasks
      comp.selection.select(tasks[0], tasks[1]);

      // Page 0 contains tasks[0] and tasks[1] — both selected → true
      expect(comp.isAllSelected()).toBe(true);
   });

});
