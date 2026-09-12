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
 * Pass 1 — Interaction tests for ScheduleTaskListComponent (portal).
 *
 * Default MSW state after renderScheduleTaskList():
 *   securityEnabled = false  (em.handlers: GET get-enable-security → { enable: false })
 *   showTasksAsList = true   (portal.handlers: GET change-show-type → true)
 *   noRootPermission = false (portal.handlers: GET checkRootPermission → true)
 *   tasks = []               (portal.handlers: POST scheduledTasks → empty list)
 *   selectedNodes = []       (no tree loaded because showTasksAsList=true)
 *
 * Groups:
 *   1  initTaskList              — 5 tests
 *   2  changeSortType            — 2 tests
 *   3  selectTask                — 2 tests
 *   4  selectAll                 — 3 tests
 *   5  selectAllChecked getter   — 3 tests
 *   6  removeEnable              — 4 tests
 *   7  getTaskName               — 4 tests
 *   8  isToggleTasksEnabledDisabled — 4 tests
 *   9  getTaskOwnerLabel         — 3 tests
 *   10 isCreateTaskEnabled       — 4 tests
 *   11 keepExpandedNodes         — 4 tests
 *   12 createActions             — 2 tests
 *   13 hasMenu                   — 1 test
 *   14 currentFolder getter      — 3 tests
 *   15 getMovedPaths (private)   — 3 tests
 *   16 getMutiEditPath (private) — 2 tests
 *   17 loadTasks (HTTP)          — 2 tests
 *   18 handleError (HTTP)        — 2 tests
 */

import { waitFor } from "@testing-library/angular";
import { http, HttpResponse } from "msw";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { ScheduleFolderTreeAction } from "../../../../../../em/src/app/settings/schedule/schedule-folder-tree/schedule-folder-tree-action";
import { KEY_DELIMITER } from "../../../../../../em/src/app/settings/security/users/identity-id";
import { MessageDialog } from "../../../widget/dialog/message-dialog/message-dialog.component";
import { server } from "@test-mocks/server";
import {
   lastRenderedFixture,
   makeTask,
   makeTaskList,
   makeTreeNode,
   MODAL_MOCK,
   renderScheduleTaskList,
   resetMocks,
} from "./schedule-task-list.test-helpers";

describe("ScheduleTaskListComponent — interaction tests", () => {
   beforeEach(() => {
      resetMocks();
      MessageDialog.lastMessage = null;
      MessageDialog.lastMessageTS = 0;
   });
   afterEach(() => vi.restoreAllMocks());

   // Destroy each fixture before MSW's global afterEach resets handlers.
   // This ensures ngOnDestroy unsubscribes from all HTTP observables before
   // MSW can cause them to error, preventing uncaught background exceptions.
   afterEach(() => {
      lastRenderedFixture?.destroy();
   });

   // ─────────────────────────────────────────────────────────────────
   // 1. initTaskList
   // ─────────────────────────────────────────────────────────────────

   describe("initTaskList", () => {
      it("populates tasks from the list", async () => {
         const { comp } = await renderScheduleTaskList();
         const task1 = makeTask({ name: "Task A" });
         const task2 = makeTask({ name: "Task B" });

         comp.initTaskList(makeTaskList([task1, task2]));

         expect(comp.tasks).toHaveLength(2);
         expect(comp.tasks[0].name).toBe("Task A");
      });

      it("sets originalOrder to the initial task name array", async () => {
         const { comp } = await renderScheduleTaskList();
         const taskA = makeTask({ name: "Task A" });
         const taskB = makeTask({ name: "Task B" });

         comp.initTaskList(makeTaskList([taskA, taskB]));

         expect(comp.originalOrder).toEqual(["Task A", "Task B"]);
      });

      it("sets showOwners and dateFormat from the list", async () => {
         const { comp } = await renderScheduleTaskList();
         comp.initTaskList(
            makeTaskList([], { showOwners: true, dateTimeFormat: "MM/dd/yyyy" })
         );

         expect(comp.showOwners).toBe(true);
         expect(comp.dateFormat).toBe("MM/dd/yyyy");
      });

      it("prepends owner key to task name when securityEnabled=false and name is not prefixed", async () => {
         const { comp } = await renderScheduleTaskList();
         // securityEnabled is already false from default handler
         const task = makeTask({
            name: "My Task",
            owner: { name: "admin", orgID: "host_org" },
         });

         comp.initTaskList(makeTaskList([task]));

         // convertTaskOwner({name:"admin",...}) = convertToKey({name:"admin",orgID:"host_org"})
         //   = "admin~;~host_org"
         expect(comp.tasks[0].name).toBe(`admin${KEY_DELIMITER}host_org:My Task`);
      });

      it("does not modify task names when securityEnabled=true", async () => {
         const { comp } = await renderScheduleTaskList();
         comp.securityEnabled = true;
         const task = makeTask({
            name: "My Task",
            owner: { name: "admin", orgID: "host_org" },
         });

         comp.initTaskList(makeTaskList([task]));

         expect(comp.tasks[0].name).toBe("My Task");
      });
   });

   // ─────────────────────────────────────────────────────────────────
   // 2. changeSortType
   // ─────────────────────────────────────────────────────────────────

   describe("changeSortType", () => {
      it("sets sortType without reordering tasks when value is non-empty", async () => {
         const taskA = makeTask({ name: "Task A" });
         const taskB = makeTask({ name: "Task B" });
         const { comp } = await renderScheduleTaskList();
         comp.tasks = [taskB, taskA]; // reversed

         comp.changeSortType("label");

         expect(comp.sortType).toBe("label");
         expect(comp.tasks[0].name).toBe("Task B"); // unchanged
      });

      it("restores original insertion order when sortType is empty string", async () => {
         const taskA = makeTask({ name: "Task A" });
         const taskB = makeTask({ name: "Task B" });
         const { comp } = await renderScheduleTaskList();
         comp.tasks = [taskB, taskA]; // reversed
         comp.originalOrder = ["Task A", "Task B"]; // original

         comp.changeSortType("");

         expect(comp.tasks[0].name).toBe("Task A");
         expect(comp.tasks[1].name).toBe("Task B");
      });
   });

   // ─────────────────────────────────────────────────────────────────
   // 3. selectTask
   // ─────────────────────────────────────────────────────────────────

   describe("selectTask", () => {
      it("adds task name to selectedItems when not already selected", async () => {
         const task = makeTask({ name: "Task A" });
         const { comp } = await renderScheduleTaskList();
         comp.tasks = [task];

         comp.selectTask(task);

         expect(comp.selectedItems).toContain("Task A");
      });

      it("removes task name from selectedItems when already selected", async () => {
         const task = makeTask({ name: "Task A" });
         const { comp } = await renderScheduleTaskList();
         comp.tasks = [task];
         comp.selectedItems = ["Task A"];

         comp.selectTask(task);

         expect(comp.selectedItems).not.toContain("Task A");
      });
   });

   // ─────────────────────────────────────────────────────────────────
   // 4. selectAll
   // ─────────────────────────────────────────────────────────────────

   describe("selectAll", () => {
      it("checked=true: selects all task names", async () => {
         const { comp } = await renderScheduleTaskList();
         comp.tasks = [makeTask({ name: "A" }), makeTask({ name: "B" })];

         comp.selectAll(true);

         expect(comp.selectedItems).toEqual(["A", "B"]);
      });

      it("checked=true: sets _selectAllChecked to true", async () => {
         const { comp } = await renderScheduleTaskList();
         comp.tasks = [makeTask({ name: "A" })];

         comp.selectAll(true);

         expect((comp as any)._selectAllChecked).toBe(true);
      });

      it("checked=false: clears selectedItems", async () => {
         const { comp } = await renderScheduleTaskList();
         comp.tasks = [makeTask({ name: "A" }), makeTask({ name: "B" })];
         comp.selectedItems = ["A", "B"];

         comp.selectAll(false);

         expect(comp.selectedItems).toHaveLength(0);
      });
   });

   // ─────────────────────────────────────────────────────────────────
   // 5. selectAllChecked getter
   // ─────────────────────────────────────────────────────────────────

   describe("selectAllChecked getter", () => {
      it("returns false when _selectAllChecked is false", async () => {
         const { comp } = await renderScheduleTaskList();
         comp.tasks = [makeTask({ name: "A" })];
         comp.selectedItems = ["A"];
         // Bypass selectAll() which also modifies selectedItems — set flag directly to isolate getter logic.
         (comp as any)._selectAllChecked = false;

         expect(comp.selectAllChecked).toBe(false);
      });

      it("returns false when count differs from tasks.length", async () => {
         const { comp } = await renderScheduleTaskList();
         comp.tasks = [makeTask({ name: "A" }), makeTask({ name: "B" })];
         comp.selectedItems = ["A"]; // only 1 of 2
         // Bypass selectAll() which also modifies selectedItems — set flag directly to isolate getter logic.
         (comp as any)._selectAllChecked = true;

         expect(comp.selectAllChecked).toBe(false);
      });

      it("returns true when _selectAllChecked=true and counts match", async () => {
         const { comp } = await renderScheduleTaskList();
         comp.tasks = [makeTask({ name: "A" }), makeTask({ name: "B" })];
         comp.selectedItems = ["A", "B"];
         // Bypass selectAll() which also modifies selectedItems — set flag directly to isolate getter logic.
         (comp as any)._selectAllChecked = true;

         expect(comp.selectAllChecked).toBe(true);
      });
   });

   // ─────────────────────────────────────────────────────────────────
   // 6. removeEnable
   // ─────────────────────────────────────────────────────────────────

   describe("removeEnable", () => {
      it("returns false when selectedItems is empty", async () => {
         const { comp } = await renderScheduleTaskList();
         comp.tasks = [makeTask({ name: "A" })];
         comp.selectedItems = [];

         expect(comp.removeEnable()).toBe(false);
      });

      it("returns false when a selected task has canDelete=false", async () => {
         const { comp } = await renderScheduleTaskList();
         comp.tasks = [makeTask({ name: "A", canDelete: false, removable: true })];
         comp.selectedItems = ["A"];

         expect(comp.removeEnable()).toBe(false);
      });

      it("returns false when a selected task has removable=false", async () => {
         const { comp } = await renderScheduleTaskList();
         comp.tasks = [makeTask({ name: "A", canDelete: true, removable: false })];
         comp.selectedItems = ["A"];

         expect(comp.removeEnable()).toBe(false);
      });

      it("returns true when all selected tasks have canDelete=true and removable=true", async () => {
         const { comp } = await renderScheduleTaskList();
         comp.tasks = [
            makeTask({ name: "A", canDelete: true, removable: true }),
            makeTask({ name: "B", canDelete: true, removable: true }),
         ];
         comp.selectedItems = ["A", "B"];

         expect(comp.removeEnable()).toBe(true);
      });
   });

   // ─────────────────────────────────────────────────────────────────
   // 7. getTaskName
   // ─────────────────────────────────────────────────────────────────

   describe("getTaskName", () => {
      it("returns task.name when owner is null", async () => {
         const { comp } = await renderScheduleTaskList();
         const task = makeTask({ name: "My Task", owner: null });

         expect(comp.getTaskName(task)).toBe("My Task");
      });

      it("returns SYSTEM_USER~;~orgID__taskName for INETSOFT_SYSTEM owner", async () => {
         const { comp } = await renderScheduleTaskList();
         const task = makeTask({
            name: "Sys Task",
            owner: { name: "INETSOFT_SYSTEM", orgID: "host_org" },
         });

         expect(comp.getTaskName(task)).toBe(
            `INETSOFT_SYSTEM${KEY_DELIMITER}host_org__Sys Task`
         );
      });

      it("returns ownerName~;~orgID:taskName when task.name does not start with owner.name", async () => {
         const { comp } = await renderScheduleTaskList();
         const task = makeTask({
            name: "My Task",
            owner: { name: "john", orgID: "host_org" },
         });

         expect(comp.getTaskName(task)).toBe(
            `john${KEY_DELIMITER}host_org:My Task`
         );
      });

      it("returns task.name unchanged when task.name already starts with owner.name", async () => {
         const { comp } = await renderScheduleTaskList();
         const task = makeTask({
            name: "john:My Task",
            owner: { name: "john", orgID: "host_org" },
         });

         expect(comp.getTaskName(task)).toBe("john:My Task");
      });
   });

   // ─────────────────────────────────────────────────────────────────
   // 8. isToggleTasksEnabledDisabled
   // ─────────────────────────────────────────────────────────────────

   describe("isToggleTasksEnabledDisabled", () => {
      it("returns false when task is editable, removable, and canDelete", async () => {
         const { comp } = await renderScheduleTaskList();
         const task = makeTask({ editable: true, removable: true, canDelete: true });

         expect(comp.isToggleTasksEnabledDisabled(task)).toBe(false);
      });

      it("returns true when task.editable is false", async () => {
         const { comp } = await renderScheduleTaskList();
         const task = makeTask({ editable: false, removable: true, canDelete: true });

         expect(comp.isToggleTasksEnabledDisabled(task)).toBe(true);
      });

      it("returns true when task.removable is false", async () => {
         const { comp } = await renderScheduleTaskList();
         const task = makeTask({ editable: true, removable: false, canDelete: true });

         expect(comp.isToggleTasksEnabledDisabled(task)).toBe(true);
      });

      it("returns true when task.canDelete is false", async () => {
         const { comp } = await renderScheduleTaskList();
         const task = makeTask({ editable: true, removable: true, canDelete: false });

         expect(comp.isToggleTasksEnabledDisabled(task)).toBe(true);
      });
   });

   // ─────────────────────────────────────────────────────────────────
   // 9. getTaskOwnerLabel
   // ─────────────────────────────────────────────────────────────────

   describe("getTaskOwnerLabel", () => {
      it("returns ownerAlias when present", async () => {
         const { comp } = await renderScheduleTaskList();
         const task = makeTask({
            owner: { name: "john", orgID: "host_org" },
            ownerAlias: "John Doe",
         });

         expect(comp.getTaskOwnerLabel(task)).toBe("John Doe");
      });

      it("returns owner.name when ownerAlias is absent", async () => {
         const { comp } = await renderScheduleTaskList();
         const task = makeTask({ owner: { name: "john", orgID: "host_org" } });

         expect(comp.getTaskOwnerLabel(task)).toBe("john");
      });

      it("returns empty string when both ownerAlias and owner.name are falsy", async () => {
         const { comp } = await renderScheduleTaskList();
         const task = makeTask({
            owner: { name: "", orgID: "host_org" },
            ownerAlias: "",
         });

         expect(comp.getTaskOwnerLabel(task)).toBe("");
      });
   });

   // ─────────────────────────────────────────────────────────────────
   // 10. isCreateTaskEnabled
   // ─────────────────────────────────────────────────────────────────

   describe("isCreateTaskEnabled", () => {
      it("returns true when there are no selected nodes and noRootPermission=false", async () => {
         const { comp } = await renderScheduleTaskList();
         comp.selectedNodes = [];
         comp.noRootPermission = false;

         expect(comp.isCreateTaskEnabled()).toBe(true);
      });

      it("returns false when there are no selected nodes and noRootPermission=true", async () => {
         const { comp } = await renderScheduleTaskList();
         comp.selectedNodes = [];
         comp.noRootPermission = true;

         expect(comp.isCreateTaskEnabled()).toBe(false);
      });

      it("returns true when selected node has READ property set to 'true'", async () => {
         const { comp } = await renderScheduleTaskList();
         const node = makeTreeNode("Root", "/", {
            data: {
               scope: 0, type: 1, user: null, path: "/",
               alias: null, identifier: "/", organization: "host_org",
               properties: { [ScheduleFolderTreeAction.READ]: "true" },
            },
         });
         comp.selectedNodes = [node];

         expect(comp.isCreateTaskEnabled()).toBe(true);
      });

      it("returns false when selected node does not have the READ property", async () => {
         const { comp } = await renderScheduleTaskList();
         const node = makeTreeNode("Root", "/", {
            data: {
               scope: 0, type: 1, user: null, path: "/",
               alias: null, identifier: "/", organization: "host_org",
               properties: {},
            },
         });
         comp.selectedNodes = [node];

         expect(comp.isCreateTaskEnabled()).toBe(false);
      });
   });

   // ─────────────────────────────────────────────────────────────────
   // 11. keepExpandedNodes
   // ─────────────────────────────────────────────────────────────────

   describe("keepExpandedNodes", () => {
      it("no-op when the old node is null", async () => {
         const { comp } = await renderScheduleTaskList();
         const newRoot = makeTreeNode("Root", "/", { children: [] });

         // Should not throw
         expect(() => comp.keepExpandedNodes(null, newRoot)).not.toThrow();
      });

      it("no-op when the old node is a leaf", async () => {
         const { comp } = await renderScheduleTaskList();
         const oldLeaf = makeTreeNode("Leaf", "leaf", { leaf: true, children: [] });
         const newRoot = makeTreeNode("Root", "/", { children: [] });

         expect(() => comp.keepExpandedNodes(oldLeaf, newRoot)).not.toThrow();
      });

      it("propagates expanded=true from old child to matching child in new root", async () => {
         const { comp } = await renderScheduleTaskList();
         const oldRoot = makeTreeNode("Root", "/", {
            children: [
               makeTreeNode("Folder1", "Folder1", { expanded: true, children: [] }),
               makeTreeNode("Folder2", "Folder2", { expanded: false, children: [] }),
            ],
         });
         const newRoot = makeTreeNode("Root", "/", {
            children: [
               makeTreeNode("Folder1", "Folder1", { expanded: false, children: [] }),
               makeTreeNode("Folder2", "Folder2", { expanded: false, children: [] }),
            ],
         });

         comp.keepExpandedNodes(oldRoot, newRoot);

         expect(newRoot.children[0].expanded).toBe(true);
         expect(newRoot.children[1].expanded).toBe(false);
      });

      it("does not propagate expanded=false children", async () => {
         const { comp } = await renderScheduleTaskList();
         const oldRoot = makeTreeNode("Root", "/", {
            children: [
               makeTreeNode("Folder1", "Folder1", { expanded: false, children: [] }),
            ],
         });
         const newRoot = makeTreeNode("Root", "/", {
            children: [
               makeTreeNode("Folder1", "Folder1", { expanded: false, children: [] }),
            ],
         });

         comp.keepExpandedNodes(oldRoot, newRoot);

         expect(newRoot.children[0].expanded).toBe(false);
      });
   });

   // ─────────────────────────────────────────────────────────────────
   // 12. createActions
   // ─────────────────────────────────────────────────────────────────

   describe("createActions", () => {
      it("root node ('/'): returns 1 action group with 1 action (New Folder)", async () => {
         const { comp } = await renderScheduleTaskList();
         const rootNode = makeTreeNode("Root", "/");

         const groups = (comp as any).createActions(rootNode, [rootNode]);

         expect(groups).toHaveLength(1);
         expect(groups[0].actions).toHaveLength(1);
         expect(groups[0].actions[0].label()).toBe("_#(js:New Folder)");
      });

      it("non-root node: returns 1 action group with 3 actions", async () => {
         const { comp } = await renderScheduleTaskList();
         const subNode = makeTreeNode("SubFolder", "SubFolder");

         const groups = (comp as any).createActions(subNode, [subNode]);

         expect(groups).toHaveLength(1);
         expect(groups[0].actions).toHaveLength(3);
      });
   });

   // ─────────────────────────────────────────────────────────────────
   // 13. hasMenu
   // ─────────────────────────────────────────────────────────────────

   describe("hasMenu", () => {
      it("returns true because all folder actions have visible()=true", async () => {
         const { comp } = await renderScheduleTaskList();
         const node = makeTreeNode("Root", "/");

         expect(comp.hasMenu(node)).toBe(true);
      });
   });

   // ─────────────────────────────────────────────────────────────────
   // 14. currentFolder getter
   // ─────────────────────────────────────────────────────────────────

   describe("currentFolder getter", () => {
      it("returns null when showTasksAsList=true regardless of selectedNodes", async () => {
         const { comp } = await renderScheduleTaskList();
         // Set explicitly — don't rely on async HTTP having run yet.
         comp.showTasksAsList = true;
         comp.selectedNodes = [makeTreeNode("Root", "/")];

         expect(comp.currentFolder).toBeNull();
      });

      it("returns null when showTasksAsList=false and selectedNodes is empty", async () => {
         const { comp } = await renderScheduleTaskList();
         comp.showTasksAsList = false;
         comp.selectedNodes = [];

         expect(comp.currentFolder).toBeNull();
      });

      it("returns the first selected node's data entry when showTasksAsList=false", async () => {
         const { comp } = await renderScheduleTaskList();
         const node = makeTreeNode("Root", "/");
         comp.showTasksAsList = false;
         comp.selectedNodes = [node];

         expect(comp.currentFolder).toBe(node.data);
      });
   });

   // ─────────────────────────────────────────────────────────────────
   // 15. getMovedPaths (private)
   // ─────────────────────────────────────────────────────────────────

   describe("getMovedPaths (private)", () => {
      it("returns basenames joined with non-root target path", async () => {
         const { comp } = await renderScheduleTaskList();

         const result = (comp as any).getMovedPaths(["parent/childA", "parent/childB"], "target");

         expect(result).toEqual(["target/childA", "target/childB"]);
      });

      it("returns just basenames when target is '/'", async () => {
         const { comp } = await renderScheduleTaskList();

         const result = (comp as any).getMovedPaths(["folder/sub"], "/");

         expect(result).toEqual(["sub"]);
      });

      it("returns empty array when movePaths is null", async () => {
         const { comp } = await renderScheduleTaskList();

         const result = (comp as any).getMovedPaths(null, "target");

         expect(result).toEqual([]);
      });
   });

   // ─────────────────────────────────────────────────────────────────
   // 16. getMutiEditPath (private)
   // ─────────────────────────────────────────────────────────────────

   describe("getMutiEditPath (private)", () => {
      it("returns all selected node paths when editNodePath is among them", async () => {
         const { comp } = await renderScheduleTaskList();
         comp.selectedNodes = [
            makeTreeNode("F1", "folder/one"),
            makeTreeNode("F2", "folder/two"),
         ];

         const result = (comp as any).getMutiEditPath("folder/one");

         expect(result).toEqual(["folder/one", "folder/two"]);
      });

      it("returns [editNodePath] when editNodePath is not among selected nodes", async () => {
         const { comp } = await renderScheduleTaskList();
         comp.selectedNodes = [makeTreeNode("F1", "folder/one")];

         const result = (comp as any).getMutiEditPath("folder/other");

         expect(result).toEqual(["folder/other"]);
      });
   });

   // ─────────────────────────────────────────────────────────────────
   // 17. loadTasks (HTTP)
   // ─────────────────────────────────────────────────────────────────

   describe("loadTasks (HTTP)", () => {
      it("populates tasks from the POST response", async () => {
         const { comp } = await renderScheduleTaskList();
         const task1 = makeTask({ name: "Task One" });
         const task2 = makeTask({ name: "Task Two" });

         server.use(
            http.post("*/api/portal/scheduledTasks", () =>
               HttpResponse.json(makeTaskList([task1, task2]))
            )
         );
         comp.loadTasks();

         await waitFor(() => expect(comp.tasks).toHaveLength(2));
         expect(comp.tasks[0].name).toBe("Task One");
      });

      it("sets originalOrder to the task names from the response", async () => {
         const { comp } = await renderScheduleTaskList();
         const task1 = makeTask({ name: "Alpha" });
         const task2 = makeTask({ name: "Beta" });

         server.use(
            http.post("*/api/portal/scheduledTasks", () =>
               HttpResponse.json(makeTaskList([task1, task2]))
            )
         );
         comp.loadTasks();

         await waitFor(() => expect(comp.originalOrder).toEqual(["Alpha", "Beta"]));
      });
   });

   // ─────────────────────────────────────────────────────────────────
   // 18. handleError (direct invocation — avoids JSDOM statusText quirks)
   //
   // JSDOM does not always propagate the HTTP response statusText through
   // the XHR/fetch bridge, so testing via MSW is unreliable for the
   // "forbidden" branch.  Calling handleError directly with a crafted
   // HttpErrorResponse-shaped object is the stable alternative.
   // ─────────────────────────────────────────────────────────────────

   describe("handleError (direct)", () => {
      it("opens a message dialog when statusText is 'Forbidden'", async () => {
         const { comp } = await renderScheduleTaskList();
         MODAL_MOCK.open.mockClear();

         (comp as any).handleError({ statusText: "Forbidden", status: 403 });

         expect(MODAL_MOCK.open).toHaveBeenCalledWith(MessageDialog, expect.any(Object));
      });

      it("opens a dialog via showHttpError for non-forbidden errors", async () => {
         const { comp } = await renderScheduleTaskList();
         MODAL_MOCK.open.mockClear();

         // showHttpError accesses error.error (the parsed response body).
         // Provide a message-bearing object so the method does not crash.
         (comp as any).handleError({
            statusText: "Internal Server Error",
            status: 500,
            error: { message: "Something went wrong on the server" },
         });

         expect(MODAL_MOCK.open).toHaveBeenCalledWith(MessageDialog, expect.any(Object));
      });
   });
});
