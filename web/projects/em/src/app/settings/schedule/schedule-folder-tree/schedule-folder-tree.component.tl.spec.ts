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
 * ScheduleFolderTreeComponent — Testing Library style
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — isDescendant: startsWith false positive for sibling paths sharing a prefix
 *   Group 2 [Risk 3] — editTaskFolder: rename path uses indexOf (first "/") not lastIndexOf (it.failing — confirmed bug)
 *   Group 3 [Risk 2] — editFolderEnabled: root-path guard and empty-selection guard
 *   Group 4 [Risk 2] — contextMenuClick: node selection toggle behavior
 *   Group 5 [Risk 2] — excludeCurrentPath: recursive path-based child removal
 *
 * Confirmed bugs (it.failing — remove wrapper once fixed):
 *
 *   Bug A — editTaskFolder computes wrong new path for deeply nested folders (Group 2):
 *     After renaming, code does `newPath.indexOf("/")` to find the parent directory separator.
 *     For a path like "a/b/c", indexOf("/") returns 1 (the FIRST slash), so the computed
 *     parent becomes "a/" instead of "a/b/". Result: safeRefreshTree is called with "a/renamed"
 *     instead of "a/b/renamed", navigating to the wrong folder after the rename.
 *     Fix: replace `indexOf("/")` with `lastIndexOf("/")`.
 *     Covered by failing case:
 *     `should navigate to a/b/renamed when renaming a folder three levels deep`.
 *     Note: didn't reproduce the bug in manual testing.
 *
 * KEY contracts:
 *   - Tree paths are relative (no leading "/"), except the root which uses exactly "/".
 *   - isDescendant guards against moving a folder into its own subtree.
 *   - excludeCurrentPath is called BEFORE opening the move dialog to hide current paths.
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { HttpClientModule } from "@angular/common/http";
import { FlatTreeControl } from "@angular/cdk/tree";
import { MatTreeFlatDataSource, MatTreeFlattener } from "@angular/material/tree";
import { MatDialog } from "@angular/material/dialog";
import { render, waitFor } from "@testing-library/angular";
import { http, HttpResponse as MswHttpResponse } from "msw";
import { EMPTY, of, Subject } from "rxjs";

import { it } from "@jest/globals"; // must be import, or it.failing didn't work
import { server } from "../../../../../../../mocks/server";
import { ScheduleFolderTreeComponent } from "./schedule-folder-tree.component";
import { EmScheduleChangeService } from "../schedule-task-list/em-schedule-change.service";
import { ScheduleTaskDragService } from "../schedule-task-list/schedule-task-drag.service";
import { StompClientService } from "../../../../../../shared/stomp/stomp-client.service";
import { RepositoryFlatNode, RepositoryTreeNode } from "../../content/repository/repository-tree-node";
import { RepositoryEntryType } from "../../../../../../shared/data/repository-entry-type.enum";

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

function makeTreeNode(path: string, label: string, children: RepositoryTreeNode[] = []): RepositoryTreeNode {
   return {
      label,
      path,
      owner: { name: "admin", orgID: "host_org" },
      type: RepositoryEntryType.SCHEDULE_TASK_FOLDER,
      readOnly: false,
      builtIn: false,
      description: "",
      icon: "folder-icon",
      visible: true,
      children,
   };
}

function makeRepositoryFlatNode(path: string, label: string, level = 0): RepositoryFlatNode {
   return new RepositoryFlatNode(label, level, true, makeTreeNode(path, label), false, true, null, () => "folder-icon");
}

// ---------------------------------------------------------------------------
// Render helper
// ---------------------------------------------------------------------------

async function renderComponent() {
   // Seed the folder-get endpoint with a root node so refreshTree's data[0] access doesn't crash
   const rootNode = makeTreeNode("/", "Root");
   server.use(
      http.get("*/api/em/schedule/folder/get", () =>
         MswHttpResponse.json({ nodes: [rootNode] })
      )
   );

   const treeControl = new FlatTreeControl<RepositoryFlatNode>(
      n => n.level,
      n => n.expandable,
   );
   const treeFlattener = new MatTreeFlattener<RepositoryTreeNode, RepositoryFlatNode>(
      (node, level) => new RepositoryFlatNode(node.label, level, !!node.children?.length, node, false, true, null, () => "folder-icon"),
      n => n.level,
      n => n.expandable,
      node => of(node.children || []),
   );
   const treeSource = new MatTreeFlatDataSource(treeControl, treeFlattener);

   const dialogMock = { open: jest.fn().mockReturnValue({ afterClosed: () => of(false) }) };
   const changeService = { onChange: new Subject(), onFolderChange: new Subject() };
   const dragService  = { get: jest.fn(() => null), reset: jest.fn(), put: jest.fn() };

   const result = await render(ScheduleFolderTreeComponent, {
      imports: [HttpClientModule],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
         { provide: MatDialog,              useValue: dialogMock },
         { provide: EmScheduleChangeService, useValue: changeService },
         { provide: ScheduleTaskDragService, useValue: dragService },
         { provide: StompClientService,      useValue: { connect: () => EMPTY } },
      ],
      componentProperties: { treeControl, treeSource, treeFlattener },
   });

   await result.fixture.whenStable();

   return {
      ...result,
      comp: result.fixture.componentInstance,
      dialogMock,
      changeService,
      dragService,
      treeControl,
      treeSource,
   };
}

// ---------------------------------------------------------------------------
// Group 1 [Risk 3] — isDescendant: startsWith false positive
// ---------------------------------------------------------------------------

describe("ScheduleFolderTreeComponent — isDescendant: startsWith false positive", () => {

   // 🔁 Regression-sensitive: isDescendant must distinguish "folder1" from "folder10".
   // startsWith("folder1") returns true for "folder10/sub", which is NOT a descendant.
   // This causes moveTaskFolder to incorrectly skip a valid move operation.
   // Note: In current UI flows, users may mostly move tasks (not folders), so this may not
   // be consistently user-visible right now. Keep this case as an implementation-level guard.
   it.failing("should return false when searchNode path shares only a string prefix, not a real ancestor relationship", async () => {
      const { comp } = await renderComponent();

      const parent     = makeTreeNode("folder1", "folder1");
      const notChild   = makeTreeNode("folder10/sub", "sub");

      // "folder10/sub".startsWith("folder1") is true but "folder10" is NOT inside "folder1"
      const result = (comp as any).isDescendant([parent], notChild);
      expect(result).toBe(false);
   });

   // 🔁 Regression-sensitive: a genuine child must still be detected as a descendant.
   it("should return true when searchNode path genuinely starts with parent path plus a slash", async () => {
      const { comp } = await renderComponent();

      const parent = makeTreeNode("folder1", "folder1");
      const child  = makeTreeNode("folder1/sub", "sub");

      expect((comp as any).isDescendant([parent], child)).toBe(true);
   });

   // Boundary: empty parents array → never a descendant
   it("should return false when the parents array is empty", async () => {
      const { comp } = await renderComponent();

      const child = makeTreeNode("folder1/sub", "sub");
      expect((comp as any).isDescendant([], child)).toBe(false);
   });

   // Boundary: null searchNode → no crash, returns false
   it("should return false when searchNode is null", async () => {
      const { comp } = await renderComponent();

      const parent = makeTreeNode("folder1", "folder1");
      expect((comp as any).isDescendant([parent], null)).toBe(false);
   });

});

// ---------------------------------------------------------------------------
// Group 2 [Risk 3] — editTaskFolder: rename path computation (confirmed bug)
// ---------------------------------------------------------------------------

describe("ScheduleFolderTreeComponent — editTaskFolder: rename path computation", () => {

   // 🔁 Regression-sensitive: renaming a top-level folder must produce the new folder name.
   it("should navigate to the renamed folder name for a top-level rename", async () => {
      const { comp } = await renderComponent();
      const safeRefreshSpy = jest.spyOn(comp as any, "safeRefreshTree").mockImplementation(() => {});

      server.use(
         http.post("*/api/em/schedule/folder/editModel", () =>
            MswHttpResponse.json({ oldPath: "reports", folderName: "reports", securityEnabled: false })
         ),
         http.post("*/api/em/schedule/rename-folder", () =>
            MswHttpResponse.json({})
         ),
      );
      // Dialog returns new folder name "renamed"
      comp.dialog.open = jest.fn().mockReturnValue({
         afterClosed: () => of({ oldPath: "reports", folderName: "renamed" }),
      });

      comp.editTaskFolder(makeTreeNode("reports", "reports"));
      await waitFor(() => expect(safeRefreshSpy).toHaveBeenCalled());
      const [, selectedPath] = safeRefreshSpy.mock.calls[0] as any[];
      expect(selectedPath).toBe("renamed");
   });

   // 🔁 Regression-sensitive: renaming a first-level folder should produce "parent/renamed".
   it("should navigate to parent/renamed for a first-level rename", async () => {
      const { comp } = await renderComponent();
      const safeRefreshSpy = jest.spyOn(comp as any, "safeRefreshTree").mockImplementation(() => {});

      server.use(
         http.post("*/api/em/schedule/folder/editModel", () =>
            MswHttpResponse.json({ oldPath: "parent/child", folderName: "child", securityEnabled: false })
         ),
         http.post("*/api/em/schedule/rename-folder", () =>
            MswHttpResponse.json({})
         ),
      );
      comp.dialog.open = jest.fn().mockReturnValue({
         afterClosed: () => of({ oldPath: "parent/child", folderName: "renamed" }),
      });

      comp.editTaskFolder(makeTreeNode("parent/child", "child"));
      await waitFor(() => expect(safeRefreshSpy).toHaveBeenCalled());

      const [, selectedPath] = safeRefreshSpy.mock.calls[0] as any[];
      expect(selectedPath).toBe("parent/renamed");
   });

   // 🔁 Regression-sensitive (implementation risk):
   //   For oldPath "a/b/c", indexOf("/") = 1 → intermediate newPath becomes "a/renamed"
   //   instead of "a/b/renamed". The robust implementation should use lastIndexOf("/")
   //   to derive the immediate parent directory.
   //   Note: this may not be consistently user-visible in manual testing because the
   //   backend rename result and full tree refresh can still end up showing the correct
   //   final path. Keep this case as a safety net for path-computation regressions.
   //   Issue #74505 
   it.failing("should navigate to a/b/renamed when renaming a folder three levels deep", async () => {
      const { comp } = await renderComponent();
      const safeRefreshSpy = jest.spyOn(comp as any, "safeRefreshTree").mockImplementation(() => {});
      let renameCalled = false;

      server.use(
         http.post("*/api/em/schedule/folder/editModel", () =>
            MswHttpResponse.json({ oldPath: "a/b/c", folderName: "c", securityEnabled: false })
         ),
         http.post("*/api/em/schedule/rename-folder", () => {
            renameCalled = true;
            return MswHttpResponse.json({});
         }),
      );
      comp.dialog.open = jest.fn().mockReturnValue({
         afterClosed: () => of({ oldPath: "a/b/c", folderName: "renamed" }),
      });

      comp.editTaskFolder(makeTreeNode("a/b/c", "c"));
      await waitFor(() => expect(safeRefreshSpy).toHaveBeenCalled());

      const [, selectedPath] = safeRefreshSpy.mock.calls[0] as any[];
      expect(renameCalled).toBe(true);
      // Correct: "a/b/renamed" — but current code produces "a/renamed"
      expect(selectedPath).toBe("a/b/renamed");
   });

});

// ---------------------------------------------------------------------------
// Group 3 [Risk 2] — editFolderEnabled: root-path and empty-selection guard
// ---------------------------------------------------------------------------

describe("ScheduleFolderTreeComponent — editFolderEnabled: selection guard", () => {

   // 🔁 Regression-sensitive: the root folder ("/") must never be editable —
   // a refactor that removes this guard would expose a destructive rename on root.
   it("should return false when the root node ('/') is selected", async () => {
      const { comp } = await renderComponent();

      comp.selectedNodes = [makeRepositoryFlatNode("/", "Root")];
      expect(comp.editFolderEnabled()).toBe(false);
   });

   // Happy: a non-root folder should be editable when selected.
   it("should return true when a non-root node is selected", async () => {
      const { comp } = await renderComponent();

      comp.selectedNodes = [makeRepositoryFlatNode("reports", "Reports")];
      expect(comp.editFolderEnabled()).toBe(true);
   });

   // Boundary: no selection → cannot edit.
   it("should return false when selectedNodes is empty", async () => {
      const { comp } = await renderComponent();

      comp.selectedNodes = [];
      expect(comp.editFolderEnabled()).toBe(false);
   });

   // 🔁 Regression-sensitive (content semantics): warning is expected, but generic single-delete
   // content (`em.schedule.delete.confirm`) is misleading when deleting a folder that contains
   // child folders (even if they are empty). Keep as failing until folder-aware copy is used.
   // Bug #74506
   it.failing("should use folder-aware warning content instead of generic delete.confirm when deleting a folder with child folders", async () => {
      const { comp } = await renderComponent();
      const dialogOpenSpy = jest.spyOn(comp.dialog, "open").mockReturnValue({
         afterClosed: () => of(false),
      } as any);

      const child = makeTreeNode("parent/child", "child");
      const parent = makeRepositoryFlatNode("parent", "parent");
      parent.data.children = [child];
      comp.selectedNodes = [parent];

      comp.removeTasks(parent.data);

      const [, cfg] = dialogOpenSpy.mock.calls[0] as any[];
      expect(cfg?.data?.content).not.toBe("_#(js:em.schedule.delete.confirm)");
   });

});

// ---------------------------------------------------------------------------
// Group 4 [Risk 2] — contextMenuClick: node selection toggle
// ---------------------------------------------------------------------------

describe("ScheduleFolderTreeComponent — contextMenuClick: selection behavior", () => {

   // 🔁 Regression-sensitive: right-clicking an already-selected node must leave selectedNodes unchanged.
   it("should not change selection when the right-clicked node is already selected", async () => {
      const { comp } = await renderComponent();

      const node = makeRepositoryFlatNode("reports", "Reports");
      comp.selectedNodes = [node];

      comp.contextMenuClick(node);

      expect(comp.selectedNodes).toHaveLength(1);
      expect(comp.selectedNodes[0]).toBe(node);
   });

   // 🔁 Regression-sensitive: right-clicking an unselected node must switch the selection to that node.
   it("should select the right-clicked node when it is not in the current selection", async () => {
      const { comp } = await renderComponent();

      const selected   = makeRepositoryFlatNode("reports", "Reports");
      const unselected = makeRepositoryFlatNode("archive", "Archive");
      comp.selectedNodes = [selected];

      comp.contextMenuClick(unselected);

      expect(comp.selectedNodes).toHaveLength(1);
      expect(comp.selectedNodes[0].data.path).toBe("archive");
   });

   // Boundary: null node must not crash (guard at top of method).
   it("should be a no-op when node is null", async () => {
      const { comp } = await renderComponent();

      const node = makeRepositoryFlatNode("reports", "Reports");
      comp.selectedNodes = [node];

      expect(() => comp.contextMenuClick(null)).not.toThrow();
      expect(comp.selectedNodes).toHaveLength(1);
   });

});

// ---------------------------------------------------------------------------
// Group 5 [Risk 2] — excludeCurrentPath: recursive child removal
// ---------------------------------------------------------------------------

describe("ScheduleFolderTreeComponent — excludeCurrentPath: recursive removal", () => {

   // 🔁 Regression-sensitive: direct children whose paths match must be spliced out.
   it("should remove direct children whose paths are in the originalPaths list", async () => {
      const { comp } = await renderComponent();

      const child1 = makeTreeNode("parent/child1", "child1");
      const child2 = makeTreeNode("parent/child2", "child2");
      const parent = makeTreeNode("parent", "parent", [child1, child2]);

      comp.excludeCurrentPath(parent, ["parent/child1"]);

      expect(parent.children).toHaveLength(1);
      expect(parent.children[0].path).toBe("parent/child2");
   });

   // 🔁 Regression-sensitive: nested grandchildren must also be removed recursively.
   it("should recursively remove grandchildren whose paths are in the originalPaths list", async () => {
      const { comp } = await renderComponent();

      const grandchild = makeTreeNode("parent/child/grand", "grand");
      const child = makeTreeNode("parent/child", "child", [grandchild]);
      const parent = makeTreeNode("parent", "parent", [child]);

      comp.excludeCurrentPath(parent, ["parent/child/grand"]);

      expect(child.children).toHaveLength(0);
   });

   // Boundary: parent with no children is a no-op.
   it("should return without error when parent has no children", async () => {
      const { comp } = await renderComponent();

      const parent = makeTreeNode("parent", "parent", []);
      expect(() => comp.excludeCurrentPath(parent, ["parent/x"])).not.toThrow();
   });

});
