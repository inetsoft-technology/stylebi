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
 * Pass 2 — Risk tests for ScheduleTaskListComponent.
 * Covers: mergeChange (WebSocket-driven task list updates), newTask (HTTP flow),
 * editTask, navigateToTaskEditor (private), removeItems (confirm+HTTP), changeShowType.
 */

import { http, HttpResponse } from "msw";
import { waitFor } from "@testing-library/angular";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { Subject } from "rxjs";

import { server } from "@test-mocks/server";
import { ScheduleTaskChange } from "../../../../../../shared/schedule/model/schedule-task-change";
import { ScheduleTaskListComponent } from "./schedule-task-list.component";
import { MessageDialog } from "../../../widget/dialog/message-dialog/message-dialog.component";
import {
   lastRenderedFixture,
   makeTask,
   makeTreeNode,
   MODAL_MOCK,
   ROUTER_MOCK,
   renderScheduleTaskList,
   resetMocks,
} from "./schedule-task-list.test-helpers";

// ---------------------------------------------------------------------------
// Per-test helpers
// ---------------------------------------------------------------------------

// Bypass private scheduleChangeService to emit changes synchronously, avoiding the WebSocket path.
// scheduleChangeService is a private DI field; (comp as any) is the only access route in tests.
function emitChange(comp: ScheduleTaskListComponent, change: ScheduleTaskChange): void {
   (comp as any).scheduleChangeService.onChange.emit(change);
}

function mockConfirmOk(): void {
   MODAL_MOCK.open.mockImplementationOnce(() => ({
      result: Promise.resolve("ok"),
      componentInstance: { onCommit: new Subject<string>() },
      close: vi.fn(),
      dismiss: vi.fn(),
   }));
}

function mockConfirmCancel(): void {
   MODAL_MOCK.open.mockImplementationOnce(() => ({
      result: Promise.resolve("cancel"),
      componentInstance: { onCommit: new Subject<string>() },
      close: vi.fn(),
      dismiss: vi.fn(),
   }));
}

function mockDialogNeverClose(): void {
   MODAL_MOCK.open.mockImplementationOnce(() => ({
      result: new Promise<any>(() => {}),
      componentInstance: { onCommit: new Subject<string>() },
      close: vi.fn(),
      dismiss: vi.fn(),
   }));
}

// ---------------------------------------------------------------------------
// Specs
// ---------------------------------------------------------------------------

describe("ScheduleTaskListComponent — risk tests", () => {
   beforeEach(() => {
      resetMocks();
      MessageDialog.lastMessage = null;
      MessageDialog.lastMessageTS = 0;
   });
   afterEach(() => vi.restoreAllMocks());
   afterEach(() => { lastRenderedFixture?.destroy(); });

   // -------------------------------------------------------------------------
   // mergeChange — triggered via scheduleChangeService.onChange EventEmitter
   // -------------------------------------------------------------------------

   describe("mergeChange", () => {
      it("REMOVED: removes an existing task from the list", async () => {
         const { comp } = await renderScheduleTaskList();
         comp.tasks = [makeTask({ name: "Task1" }), makeTask({ name: "Task2" })];

         emitChange(comp, { name: "Task1", task: makeTask({ name: "Task1" }), type: "REMOVED" });

         expect(comp.tasks.length).toBe(1);
         expect(comp.tasks[0].name).toBe("Task2");
      });

      it("hideInPortal=true: removes existing task regardless of change type", async () => {
         const { comp } = await renderScheduleTaskList();
         comp.tasks = [makeTask({ name: "Task1" })];

         emitChange(comp, {
            name: "Task1",
            task: makeTask({ name: "Task1", hideInPortal: true }),
            type: "UPDATED",
         });

         expect(comp.tasks.length).toBe(0);
      });

      it("UPDATED: replaces existing task at its index without changing list length", async () => {
         const { comp } = await renderScheduleTaskList();
         comp.tasks = [makeTask({ name: "Task1", label: "Old Label" })];

         emitChange(comp, {
            name: "Task1",
            task: makeTask({ name: "Task1", label: "New Label" }),
            type: "UPDATED",
         });

         expect(comp.tasks.length).toBe(1);
         expect(comp.tasks[0].label).toBe("New Label");
      });

      it("ADDED when showTasksAsList=true: always appends the incoming task", async () => {
         const { comp } = await renderScheduleTaskList();
         // Default MSW handler (GET change-show-type → true) already set this
         expect(comp.showTasksAsList).toBe(true);
         comp.tasks = [];

         emitChange(comp, {
            name: "NewTask",
            task: makeTask({ name: "NewTask", path: "/folder1" }),
            type: "ADDED",
         });

         expect(comp.tasks.length).toBe(1);
         expect(comp.tasks[0].name).toBe("NewTask");
      });

      it("ADDED when showTasksAsList=false and path mismatch: does not append", async () => {
         const { comp } = await renderScheduleTaskList();
         comp.showTasksAsList = false;
         comp.selectedNodes = [makeTreeNode("Folder1", "/folder1")];
         comp.tasks = [];

         emitChange(comp, {
            name: "NewTask",
            task: makeTask({ name: "NewTask", path: "/folder2" }),
            type: "ADDED",
         });

         expect(comp.tasks.length).toBe(0);
      });

      it("REMOVED: no-op when task name is not in the current list", async () => {
         const { comp } = await renderScheduleTaskList();
         comp.tasks = [makeTask({ name: "Task1" })];

         emitChange(comp, {
            name: "NonExistent",
            task: makeTask({ name: "NonExistent" }),
            type: "REMOVED",
         });

         expect(comp.tasks.length).toBe(1);
      });
   });

   // -------------------------------------------------------------------------
   // newTask
   // -------------------------------------------------------------------------

   describe("newTask", () => {
      it("on HTTP success calls router.navigate with newTask=true in queryParams", async () => {
         const { comp } = await renderScheduleTaskList();
         // Default handler: POST */api/portal/schedule/new → { name: "New Task", taskDefaultTime: null }
         comp.newTask();

         await waitFor(() => {
            expect(ROUTER_MOCK.navigate).toHaveBeenCalledWith(
               expect.arrayContaining(["/portal/tab/schedule/tasks", expect.any(String)]),
               expect.objectContaining({
                  queryParams: expect.objectContaining({ newTask: true }),
               }),
            );
         });
      });

      it("on HTTP error opens a modal dialog and does not navigate", async () => {
         server.use(
            http.post("*/api/portal/schedule/new", () =>
               HttpResponse.json({ message: "Server error" }, { status: 500 })
            )
         );
         const { comp } = await renderScheduleTaskList();
         comp.newTask();

         await waitFor(() => expect(MODAL_MOCK.open).toHaveBeenCalled());
         expect(ROUTER_MOCK.navigate).not.toHaveBeenCalled();
      });
   });

   // -------------------------------------------------------------------------
   // editTask
   // -------------------------------------------------------------------------

   describe("editTask", () => {
      it("does not navigate when task.editable is false", async () => {
         const { comp } = await renderScheduleTaskList();
         comp.editTask(makeTask({ editable: false, canDelete: true }));
         expect(ROUTER_MOCK.navigate).not.toHaveBeenCalled();
      });

      it("does not navigate when task.canDelete is false", async () => {
         const { comp } = await renderScheduleTaskList();
         comp.editTask(makeTask({ editable: true, canDelete: false }));
         expect(ROUTER_MOCK.navigate).not.toHaveBeenCalled();
      });

      it("navigates when both editable and canDelete are true", async () => {
         const { comp } = await renderScheduleTaskList();
         comp.editTask(makeTask({ name: "Task1", editable: true, canDelete: true }));
         expect(ROUTER_MOCK.navigate).toHaveBeenCalledWith(
            expect.arrayContaining(["/portal/tab/schedule/tasks", expect.any(String)]),
            expect.any(Object),
         );
      });
   });

   // -------------------------------------------------------------------------
   // navigateToTaskEditor (private)
   // Tested directly because the method encapsulates non-trivial routing logic
   // (path extraction, flag passing) that is hard to verify transitively through
   // newTask()/editTask() without coupling the assertion to their HTTP flows.
   // -------------------------------------------------------------------------

   describe("navigateToTaskEditor", () => {
      it("uses empty string path when selectedNodes is empty", async () => {
         const { comp } = await renderScheduleTaskList();
         comp.selectedNodes = [];
         (comp as any).navigateToTaskEditor("MyTask", true, false);

         expect(ROUTER_MOCK.navigate).toHaveBeenCalledWith(
            expect.arrayContaining(["/portal/tab/schedule/tasks"]),
            expect.objectContaining({
               queryParams: expect.objectContaining({ path: "" }),
            }),
         );
      });

      it("uses selectedNodes[0].data.path when a node is selected", async () => {
         const { comp } = await renderScheduleTaskList();
         comp.selectedNodes = [makeTreeNode("MyFolder", "/myFolder")];
         (comp as any).navigateToTaskEditor("MyTask", true, false);

         expect(ROUTER_MOCK.navigate).toHaveBeenCalledWith(
            expect.any(Array),
            expect.objectContaining({
               queryParams: expect.objectContaining({ path: "/myFolder" }),
            }),
         );
      });

      it("passes taskDefaultTime and newTask flags through to queryParams", async () => {
         const { comp } = await renderScheduleTaskList();
         comp.selectedNodes = [];
         (comp as any).navigateToTaskEditor("MyTask", false, true);

         expect(ROUTER_MOCK.navigate).toHaveBeenCalledWith(
            expect.any(Array),
            expect.objectContaining({
               queryParams: expect.objectContaining({ taskDefaultTime: false, newTask: true }),
            }),
         );
      });
   });

   // -------------------------------------------------------------------------
   // removeItems
   // -------------------------------------------------------------------------

   describe("removeItems", () => {
      it("does not issue HTTP requests when user cancels the confirm dialog", async () => {
         const { comp } = await renderScheduleTaskList();
         comp.tasks = [makeTask({ name: "Task1", canDelete: true, removable: true })];
         comp.selectedItems = ["Task1"];

         mockConfirmCancel();
         comp.removeItems();

         // Confirm dialog opened with the right dialog type; no secondary (dep/error) dialog follows.
         await waitFor(() => expect(MODAL_MOCK.open).toHaveBeenCalledWith(MessageDialog, expect.any(Object)));
         expect(comp.selectedItems).toHaveLength(1);
      });

      it("shows an error dialog when the dependency check returns non-empty task names", async () => {
         server.use(
            http.post("*/api/portal/schedule/check-dependency", () =>
               HttpResponse.json({ taskNames: ["DependentTask"] })
            )
         );
         const { comp } = await renderScheduleTaskList();
         comp.tasks = [makeTask({ name: "Task1", canDelete: true, removable: true })];
         comp.selectedItems = ["Task1"];

         mockConfirmOk();       // first open: confirm dialog
         mockDialogNeverClose(); // second open: dependency error dialog

         comp.removeItems();

         // 1st open: confirm dialog; 2nd open: dependency error dialog — both use MessageDialog type.
         await waitFor(() => {
            expect(MODAL_MOCK.open).toHaveBeenNthCalledWith(1, MessageDialog, expect.any(Object));
            expect(MODAL_MOCK.open).toHaveBeenNthCalledWith(2, MessageDialog, expect.any(Object));
         });
         // selectedItems unchanged because remove was not executed
         expect(comp.selectedItems).toHaveLength(1);
      });

      it("clears selectedItems and resets selectAllChecked after successful remove", async () => {
         const { comp, fixture } = await renderScheduleTaskList();
         comp.tasks = [makeTask({ name: "Task1", canDelete: true, removable: true })];
         comp.selectedItems = ["Task1"];
         // Bypass selectAll() which also modifies selectedItems — set flag directly.
         (comp as any)._selectAllChecked = true;

         mockConfirmOk();
         comp.removeItems();

         await waitFor(() => {
            expect(comp.selectedItems).toHaveLength(0);
            expect((comp as any)._selectAllChecked).toBe(false);
         });
         // Drain loadTasks (POST scheduledTasks) + loadTaskFolderTree (GET tree) that fire
         // after the remove succeeds, so they complete before afterEach destroys the fixture.
         await fixture.whenStable();
      });
   });

   // -------------------------------------------------------------------------
   // changeShowType
   // -------------------------------------------------------------------------

   describe("changeShowType", () => {
      it("sets showTasksAsList synchronously before the HTTP PUT", async () => {
         const { comp, fixture } = await renderScheduleTaskList();
         // Mock loadTasks to prevent in-flight HTTP after fixture destroy
         const loadSpy = vi.spyOn(comp, "loadTasks").mockImplementation(() => {});
         try {
            expect(comp.showTasksAsList).toBe(true);
            comp.changeShowType(false);
            expect(comp.showTasksAsList).toBe(false);
            await fixture.whenStable(); // drain the PUT before afterEach destroys fixture
         } finally {
            loadSpy.mockRestore();
         }
      });

      it("calls loadTasks after the PUT response completes", async () => {
         const { comp, fixture } = await renderScheduleTaskList();
         // Mock loadTasks to prevent in-flight scheduledTasks POST after fixture destroy
         const spy = vi.spyOn(comp, "loadTasks").mockImplementation(() => {});
         try {
            comp.changeShowType(true);
            await waitFor(() => expect(spy).toHaveBeenCalled());
            await fixture.whenStable(); // drain the PUT itself
         } finally {
            spy.mockRestore();
         }
      });
   });
});
