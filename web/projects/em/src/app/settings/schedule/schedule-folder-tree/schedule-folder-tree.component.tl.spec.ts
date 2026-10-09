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
 *   Group 1 [Risk 3] — isDescendant: startsWith false positive for sibling paths sharing a prefix (Bug #77705, fixed)
 *   Group 2 [Risk 3] — editTaskFolder: rename path uses indexOf (first "/") not lastIndexOf (it.failing — confirmed bug)
 *   Group 3 [Risk 2] — editFolderEnabled: root-path guard and empty-selection guard
 *   Group 4 [Risk 2] — contextMenuClick: node selection toggle behavior
 *   Group 5 [Risk 2] — excludeCurrentPath: recursive path-based child removal
 *   Group 7 [Risk 2] — newTaskFolder / editTaskFolder: refused folder calls are surfaced (Bug #78136)
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

import { config as rxjsConfig, EMPTY, of, Subject } from "rxjs";
import { server } from "@test-mocks/server";
import { ScheduleFolderTreeComponent } from "./schedule-folder-tree.component";
import { EmScheduleChangeService } from "../schedule-task-list/em-schedule-change.service";
import { ScheduleTaskDragService } from "../schedule-task-list/schedule-task-drag.service";
import { StompClientService } from "../../../../../../shared/stomp/stomp-client.service";
import { Tool } from "../../../../../../shared/util/tool";
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

   const dialogMock = { open: vi.fn().mockReturnValue({ afterClosed: () => of(false) }) };
   const changeService = { onChange: new Subject(), onFolderChange: new Subject() };
   const dragService  = { get: vi.fn(() => null), reset: vi.fn(), put: vi.fn() };

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

   // Bug #77705: isDescendant must distinguish "folder1" from "folder10".
   // A naive startsWith("folder1") returns true for "folder10/sub", which is NOT a real descendant.
   // moveTaskFolder then skipped the drag of a folder onto a sibling such as "folder10".
   it("should return false when searchNode path shares only a string prefix, not a real ancestor relationship", async () => {
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

   // Bug #77705: the folder itself still counts, so a drop onto itself stays blocked
   it("should return true when searchNode is the parent itself", async () => {
      const { comp } = await renderComponent();

      const parent = makeTreeNode("folder1", "folder1");
      expect((comp as any).isDescendant([parent], makeTreeNode("folder1", "folder1"))).toBe(true);
   });

   // Bug #77705: a drag of F onto its sibling Fx is moved; onto F itself or F/G it is not
   it("should move a folder onto a sibling sharing its prefix but not onto itself or a subfolder", async () => {
      const bodies: any[] = [];
      server.use(
         http.post("*/api/em/schedule/check-folder", async ({ request }) => {
            bodies.push(await request.json());
            return MswHttpResponse.json(true);
         })
      );
      const { comp } = await renderComponent();
      const f = makeRepositoryFlatNode("F", "F", 1);
      const drop = (target: RepositoryFlatNode) => {
         comp.selectedNodes = [f];
         comp.moveTaskFolder(target);
      };

      drop(makeRepositoryFlatNode("F", "F", 1));
      drop(makeRepositoryFlatNode("F/G", "G", 2));
      drop(makeRepositoryFlatNode("Fx", "Fx", 1));

      await waitFor(() => expect(bodies.length).toBe(1));
      expect(bodies[0].target.path).toBe("Fx");
      expect(bodies[0].folders).toEqual(["F"]);
   });

   // Bug #77705: the same for nested folders, a/F onto a/Fx is moved; onto a/F or a/F/G it is not
   it("should move a nested folder onto a nested sibling sharing its prefix but not onto itself or a subfolder", async () => {
      const bodies: any[] = [];
      server.use(
         http.post("*/api/em/schedule/check-folder", async ({ request }) => {
            bodies.push(await request.json());
            return MswHttpResponse.json(true);
         })
      );
      const { comp } = await renderComponent();
      const f = makeRepositoryFlatNode("a/F", "F", 2);
      const drop = (target: RepositoryFlatNode) => {
         comp.selectedNodes = [f];
         comp.moveTaskFolder(target);
      };

      drop(makeRepositoryFlatNode("a/F", "F", 2));
      drop(makeRepositoryFlatNode("a/F/G", "G", 3));
      drop(makeRepositoryFlatNode("a/Fx", "Fx", 2));

      await waitFor(() => expect(bodies.length).toBe(1));
      expect(bodies[0].target.path).toBe("a/Fx");
      expect(bodies[0].folders).toEqual(["a/F"]);
   });

   // Bug #77705: a multi-select of F and its sibling Fx keeps both folders
   it("should keep a selected sibling sharing the prefix of another selected folder", async () => {
      const bodies: any[] = [];
      server.use(
         http.post("*/api/em/schedule/check-folder", async ({ request }) => {
            bodies.push(await request.json());
            return MswHttpResponse.json(true);
         })
      );
      const { comp } = await renderComponent();
      comp.selectedNodes = [makeRepositoryFlatNode("F", "F", 1), makeRepositoryFlatNode("Fx", "Fx", 1),
                            makeRepositoryFlatNode("F/G", "G", 2)];
      comp.moveTaskFolder(makeRepositoryFlatNode("Y", "Y", 1));

      await waitFor(() => expect(bodies.length).toBe(1));
      expect([...bodies[0].folders].sort()).toEqual(["F", "Fx"]);
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
      const safeRefreshSpy = vi.spyOn(comp as any, "safeRefreshTree").mockImplementation(() => {});

      server.use(
         http.post("*/api/em/schedule/folder/editModel", () =>
            MswHttpResponse.json({ oldPath: "reports", folderName: "reports", securityEnabled: false })
         ),
         http.post("*/api/em/schedule/rename-folder", () =>
            MswHttpResponse.json({})
         ),
      );
      // Dialog returns new folder name "renamed"
      comp.dialog.open = vi.fn().mockReturnValue({
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
      const safeRefreshSpy = vi.spyOn(comp as any, "safeRefreshTree").mockImplementation(() => {});

      server.use(
         http.post("*/api/em/schedule/folder/editModel", () =>
            MswHttpResponse.json({ oldPath: "parent/child", folderName: "child", securityEnabled: false })
         ),
         http.post("*/api/em/schedule/rename-folder", () =>
            MswHttpResponse.json({})
         ),
      );
      comp.dialog.open = vi.fn().mockReturnValue({
         afterClosed: () => of({ oldPath: "parent/child", folderName: "renamed" }),
      });

      comp.editTaskFolder(makeTreeNode("parent/child", "child"));
      await waitFor(() => expect(safeRefreshSpy).toHaveBeenCalled());

      const [, selectedPath] = safeRefreshSpy.mock.calls[0] as any[];
      expect(selectedPath).toBe("parent/renamed");
   });

   // 🔁 Regression-sensitive (implementation risk):
   //   For oldPath "a/b/c", lastIndexOf("/") correctly finds the immediate parent
   //   so newPath becomes "a/b/renamed" instead of "a/renamed" (indexOf bug — fixed).
   //   Note: this may not be consistently user-visible in manual testing because the
   //   backend rename result and full tree refresh can still end up showing the correct
   //   final path. Keep this case as a safety net for path-computation regressions.
   //   Issue #74505
   it("should navigate to a/b/renamed when renaming a folder three levels deep", async () => {
      const { comp } = await renderComponent();
      const safeRefreshSpy = vi.spyOn(comp as any, "safeRefreshTree").mockImplementation(() => {});
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
      comp.dialog.open = vi.fn().mockReturnValue({
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
   it.fails("should use folder-aware warning content instead of generic delete.confirm when deleting a folder with child folders", async () => {
      const { comp } = await renderComponent();
      const dialogOpenSpy = vi.spyOn(comp.dialog, "open").mockReturnValue({
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

// ---------------------------------------------------------------------------
// Group 6 [Risk 3] — removeTasks: folder dependency check (Bug #78002)
// ---------------------------------------------------------------------------

describe("ScheduleFolderTreeComponent — removeTasks: folder dependency check", () => {

   async function deleteFolder(dependencies: string[]) {
      let removeBodies: any[] = [];
      let checkCalled = false;
      server.use(
         http.post("*/api/em/schedule/folder/check-dependency", () => {
            checkCalled = true;
            // server returns a TaskListModel object, never a bare array
            return MswHttpResponse.json({ taskNames: dependencies });
         }),
         http.post("*/api/em/schedule/folder/remove", async ({ request }) => {
            removeBodies.push(await request.json());
            return MswHttpResponse.json({});
         }),
      );

      const { comp } = await renderComponent();
      vi.spyOn(comp as any, "safeRefreshTree").mockImplementation(() => {});
      // confirm dialog answers OK, every later dialog is just recorded
      const dialogOpen = vi.fn().mockReturnValue({ afterClosed: () => of(true) });
      comp.dialog.open = dialogOpen;

      const folder = makeRepositoryFlatNode("f1", "f1");
      comp.selectedNodes = [folder];
      comp.removeTasks(folder.data);

      await waitFor(() => expect(checkCalled).toBe(true));
      return { dialogOpen, removeBodies: () => removeBodies };
   }

   // Bug #78002: a folder whose tasks have dependents shows the dependency message and removes nothing
   it("should show the dependency dialog and not remove the folder when tasks have dependents", async () => {
      const formatSpy = vi.spyOn(Tool, "formatCatalogString");
      const { dialogOpen, removeBodies } = await deleteFolder(["B"]);

      await waitFor(() => expect(dialogOpen).toHaveBeenCalledTimes(2));
      const [, cfg] = dialogOpen.mock.calls[1] as any[];
      expect(cfg.data.title).toBe("_#(js:em.schedule.dependenciesFound)");
      // the catalog is not loaded in tests, so check the dependent names passed to the message format
      expect(formatSpy).toHaveBeenCalledWith("_#(js:em.schedule.task.removeDependency)", ["B"]);
      expect(cfg.data.content).toBe(formatSpy.mock.results.at(-1).value);

      // give a stray remove request the chance to arrive before asserting it never did
      await new Promise(resolve => setTimeout(resolve, 50));
      expect(removeBodies()).toEqual([]);
   });

   // Bug #78002: no dependents -> the folder is removed and no dependency dialog is shown
   it("should remove the folder when there are no dependents", async () => {
      const { dialogOpen, removeBodies } = await deleteFolder([]);

      await waitFor(() => expect(removeBodies().length).toBe(1));
      expect(removeBodies()[0].taskNames).toEqual(["f1"]);
      expect(dialogOpen).toHaveBeenCalledTimes(1);
   });

});

// ---------------------------------------------------------------------------
// Group 7 [Risk 2] — refused folder calls are surfaced (Bug #78136)
// ---------------------------------------------------------------------------
describe("ScheduleFolderTreeComponent — refused folder calls are surfaced (Bug #78136)", () => {

   // handleError emits errorResponse and rethrows, the rethrown error reaches rxjs's unhandled
   // error hook, which is captured here so it doesn't fail the run
   async function withCapturedErrors(run: (captured: unknown[]) => Promise<void>) {
      const captured: unknown[] = [];
      const previousOnUnhandledError = rxjsConfig.onUnhandledError;
      rxjsConfig.onUnhandledError = (err) => captured.push(err);

      try {
         await run(captured);
      }
      finally {
         rxjsConfig.onUnhandledError = previousOnUnhandledError;
      }
   }

   function denied() {
      return MswHttpResponse.json(
         { type: "SecurityException", message: "denied" }, { status: 403 });
   }

   async function newFolder(hint: () => any) {
      let addCalls = 0;
      server.use(
         http.post("*/api/em/schedule/add/checkDuplicate", hint),
         http.post("*/api/em/schedule/folder/add", () => {
            addCalls++;
            return MswHttpResponse.json({});
         }),
      );

      const { comp } = await renderComponent();
      vi.spyOn(comp as any, "safeRefreshTree").mockImplementation(() => {});
      comp.selectedNodes = [makeRepositoryFlatNode("f1", "f1")];
      const dialogOpen = vi.fn().mockReturnValue({ afterClosed: () => of({ folderName: "new" }) });
      comp.dialog.open = dialogOpen;
      const errors: any[] = [];
      comp.errorResponse.subscribe(e => errors.push(e));

      comp.newTaskFolder(makeTreeNode("f1", "f1"));
      return { errors, dialogOpen, addCalls: () => addCalls };
   }

   // a duplicate hint refused without write permission on the parent folder (#77811) shows the
   // error instead of nothing, and the folder isn't added
   it("should surface a refused duplicate hint and not add the folder", async () => {
      await withCapturedErrors(async () => {
         const { errors, dialogOpen, addCalls } = await newFolder(denied);

         await waitFor(() => expect(errors.length).toBe(1));
         expect(errors[0].status).toBe(403);
         expect(errors[0].error.message).toBe("denied");
         await new Promise(resolve => setTimeout(resolve, 50));
         expect(addCalls()).toBe(0);
         // only the new folder dialog, no duplicate-name dialog
         expect(dialogOpen).toHaveBeenCalledTimes(1);
      });
   });

   // control: an allowed hint that isn't a duplicate still adds the folder
   it("should add the folder when the hint allows it", async () => {
      const { errors, addCalls } = await newFolder(() => MswHttpResponse.json(false));

      await waitFor(() => expect(addCalls()).toBe(1));
      expect(errors).toEqual([]);
   });

   // the edit model refused without permission on the folder (#77906) shows the error and
   // doesn't open the edit dialog
   it("should surface a refused edit model and not open the edit dialog", async () => {
      await withCapturedErrors(async () => {
         server.use(http.post("*/api/em/schedule/folder/editModel", denied));
         const { comp } = await renderComponent();
         const dialogOpen = vi.fn().mockReturnValue({ afterClosed: () => of(false) });
         comp.dialog.open = dialogOpen;
         const errors: any[] = [];
         comp.errorResponse.subscribe(e => errors.push(e));

         comp.editTaskFolder(makeTreeNode("f1", "f1"));

         await waitFor(() => expect(errors.length).toBe(1));
         expect(errors[0].status).toBe(403);
         expect(dialogOpen).not.toHaveBeenCalled();
      });
   });
});
