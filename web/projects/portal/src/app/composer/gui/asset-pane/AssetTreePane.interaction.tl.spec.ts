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
 * AssetTreePane — Pass 1: Interaction
 *
 * Risk-first coverage:
 *   Group 1  [Risk 3] — openSheetFromAsset: @Output routing contract for all four asset types
 *   Group 2  [Risk 3] — renameAsset: blocks rename on open assets; reverts label on error response
 *   Group 3  [Risk 3] — nodeDrop: parent-type validation guards + confirm → dispatchChangeAssetEvent
 *   Group 4  [Risk 2] — ngOnInit: tabularDataSourceTypes loaded from HTTP on startup
 *   Group 5  [Risk 2] — getRecentRootFun: Observable<TreeNodeModel[]> maps alias or path-tail
 *   Group 6  [Risk 2] — hasMenu / createActions: action visibility for different entry types
 *   Group 7  [Risk 2] — sendAddFolderEvent: POST to add-folder; surfaces error message from body
 *   Group 8  [Risk 2] — isAssetOpened: true/false based on opendTabs identifier + parent-path match
 *   Group 9  [Risk 2] — nodeDrag: builds drag image labels from path-tail
 *   Group 10 [Risk 1] — ngOnChanges: inactive=true→detach; inactive=false→reattach
 *   Group 11 [Risk 1] — openNodeSheet: deployed=true blocks sheet open
 *   Group 12 [Risk 1] — isRejectNodes: column dragName→true; other dragName→false
 *   Group 13 [Risk 1] — showMessage: calls ComponentTool.showMessageDialog with Error title
 *   Group 14 [Risk 1] — openAssetTreeContextmenu: opens dropdown with correct position
 *
 * Confirmed bugs: none
 *
 * Suspected bugs (header only):
 *   Suspicion A — dispatchRenameAssetEvent: no takeUntilDestroyed guard; a rename HTTP
 *     response that arrives after component teardown still writes to assetTree.selectedNodes.
 *
 * Out of scope this pass (covered in AssetTreePane.risk.tl.spec.ts):
 *   deleteEntries, deleteAssets, deleteRecentAssets, createDeleteViewsheetAction,
 *   createDeleteWorksheetAction, createDeleteFolderAction, createDeleteScriptAction,
 *   dispatchRemoveAssetEvent, moveAssetToRecyclingBin, dispatchChangeAssetEvent, confirm
 */

import { http, HttpResponse } from "msw";
import { waitFor } from "@testing-library/angular";

import { server } from "@test-mocks/server";
import { AssetType } from "../../../../../../shared/data/asset-type";
import { AssetConstants } from "../../../common/data/asset-constants";
import { ComponentTool } from "../../../common/util/component-tool";
import { GuiTool } from "../../../common/util/gui-tool";
import { TreeView } from "../../../widget/tree/tree.component";
import {
   renderComponent,
   makeEntry,
   makeNode,
   MODAL_MOCK,
   DROPDOWN_MOCK,
   DRAG_SERVICE_MOCK,
   RECENT_SERVICE_MOCK,
   recentChange$,
} from "./AssetTreePane.test-helpers";

// ---------------------------------------------------------------------------
// Per-test reset
// ---------------------------------------------------------------------------

beforeEach(() => {
   MODAL_MOCK.open.mockClear();
   DROPDOWN_MOCK.open.mockClear();
   DRAG_SERVICE_MOCK.getDragData.mockReset().mockReturnValue({});
   RECENT_SERVICE_MOCK.addRecentlyViewed.mockClear();
   RECENT_SERVICE_MOCK.removeNonExistItems.mockClear();
   recentChange$.next([]);
});

afterEach(() => vi.restoreAllMocks());

// ---------------------------------------------------------------------------
// Group 1: openSheetFromAsset — @Output routing [Risk 3]
// ---------------------------------------------------------------------------

describe("AssetTreePane — openSheetFromAsset @Output routing", () => {

   // 🔁 Regression-sensitive: changing the type→output routing silently breaks sheet navigation
   it("should emit onOpenSheet with type=worksheet for a WORKSHEET entry", async () => {
      const comp = await renderComponent();
      const spy = vi.fn();
      comp.onOpenSheet.subscribe(spy);

      comp.openSheetFromAsset([makeEntry(AssetType.WORKSHEET, { identifier: "ws-id" })]);

      expect(spy).toHaveBeenCalledWith({ type: "worksheet", assetId: "ws-id" });
   });

   it("should emit onOpenSheet with type=viewsheet and meta=false by default", async () => {
      const comp = await renderComponent();
      const spy = vi.fn();
      comp.onOpenSheet.subscribe(spy);

      comp.openSheetFromAsset([makeEntry(AssetType.VIEWSHEET, { identifier: "vs-id" })]);

      expect(spy).toHaveBeenCalledWith({ type: "viewsheet", assetId: "vs-id", meta: false });
   });

   it("should emit onOpenSheet with meta=true when meta flag is passed for a VIEWSHEET", async () => {
      const comp = await renderComponent();
      const spy = vi.fn();
      comp.onOpenSheet.subscribe(spy);

      comp.openSheetFromAsset([makeEntry(AssetType.VIEWSHEET, { identifier: "vs-id" })], true);

      expect(spy).toHaveBeenCalledWith({ type: "viewsheet", assetId: "vs-id", meta: true });
   });

   it("should emit onOpenLibraryAsset with type=script for a SCRIPT entry", async () => {
      const comp = await renderComponent();
      const spy = vi.fn();
      comp.onOpenLibraryAsset.subscribe(spy);

      comp.openSheetFromAsset([makeEntry(AssetType.SCRIPT, { identifier: "script-id" })]);

      expect(spy).toHaveBeenCalledWith({ type: "script", assetId: "script-id" });
   });

   it("should emit onOpenLibraryAsset with styleId for a TABLE_STYLE entry", async () => {
      const comp = await renderComponent();
      const spy = vi.fn();
      comp.onOpenLibraryAsset.subscribe(spy);
      const entry = makeEntry(AssetType.TABLE_STYLE, {
         identifier: "ts-id",
         properties: { styleID: "sty-42" },
      });

      comp.openSheetFromAsset([entry]);

      expect(spy).toHaveBeenCalledWith({ type: "tableStyle", assetId: "ts-id", styleId: "sty-42" });
   });

   // 🔁 Regression-sensitive: every opened asset must appear in recently-viewed history
   it("should call addRecentlyViewed once per entry opened", async () => {
      const comp = await renderComponent();
      comp.openSheetFromAsset([
         makeEntry(AssetType.WORKSHEET, { identifier: "ws-1" }),
         makeEntry(AssetType.VIEWSHEET, { identifier: "vs-1" }),
      ]);
      expect(RECENT_SERVICE_MOCK.addRecentlyViewed).toHaveBeenCalledTimes(2);
   });

   it("should call assetTree.selectNodes([]) after processing entries", async () => {
      const comp = await renderComponent();
      comp.openSheetFromAsset([makeEntry(AssetType.WORKSHEET)]);
      expect((comp as any).assetTree.selectNodes).toHaveBeenCalledWith([]);
   });
});

// ---------------------------------------------------------------------------
// Group 2: renameAsset + dispatchRenameAssetEvent [Risk 3]
// ---------------------------------------------------------------------------

describe("AssetTreePane — renameAsset", () => {

   // 🔁 Regression-sensitive: renaming an open sheet causes unsaved-changes corruption
   it("should show error and NOT refresh view when the asset is currently open", async () => {
      const comp = await renderComponent();
      const entry = makeEntry(AssetType.WORKSHEET, { identifier: "open-ws", path: "ws" });
      const node = makeNode(entry, { label: "OldName" });
      comp.opendTabs = [{ type: "worksheet", asset: { id: "open-ws", label: "ws" } } as any];
      (comp as any).assetTree.findAssetTreeNodeParentFromIdentifier.mockReturnValue({
         data: { path: "ws" },
      });
      const msgSpy = vi.spyOn(ComponentTool, "showMessageDialog").mockResolvedValue("ok");

      (comp as any).renameAsset(entry, "NewName", node);

      expect(msgSpy).toHaveBeenCalledWith(
         expect.anything(),
         "_#(js:Error)",
         "_#(js:common.tree.renameForbidden)",
         expect.anything(),
         expect.anything(),
      );
      expect((comp as any).assetTree.refreshView).not.toHaveBeenCalled();
   });

   it("should update node.label optimistically and POST to rename-asset when asset is not open", async () => {
      const entry = makeEntry(AssetType.WORKSHEET, { identifier: "ws-rename", path: "myws" });
      const node = makeNode(entry, { label: "OldName" });
      let capturedBody: any;

      server.use(
         http.post("*/api/composer/asset-tree/rename-asset", async ({ request }) => {
            capturedBody = await request.json();
            return new HttpResponse(null, { status: 200 });
         }),
      );

      const comp = await renderComponent();
      comp.opendTabs = [];

      (comp as any).renameAsset(entry, "NewName", node);

      await waitFor(() => expect((comp as any).assetTree.selectedNodes).toEqual([]));
      expect(node.label).toBe("NewName");
   });

   // 🔁 Regression-sensitive: label must revert so the tree shows the real server state
   it("should revert node.label when rename-asset returns a non-CONFIRM error", async () => {
      server.use(
         http.post("*/api/composer/asset-tree/rename-asset", () =>
            HttpResponse.json({ type: "ERROR", message: "Name already exists" }),
         ),
      );

      const comp = await renderComponent();
      comp.opendTabs = [];
      const entry = makeEntry(AssetType.WORKSHEET, { identifier: "ws-err", path: "myws" });
      const node = makeNode(entry, { label: "OldName" });
      vi.spyOn(ComponentTool, "showMessageDialog").mockResolvedValue("ok");

      (comp as any).renameAsset(entry, "NewName", node);

      await waitFor(() => expect(node.label).toBe("OldName"));
      expect(node.loading).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 3: nodeDrop — parent-type validation [Risk 3]
// ---------------------------------------------------------------------------

describe("AssetTreePane — nodeDrop", () => {

   function makeDropEvent(node: any): any {
      return { node };
   }

   // 🔁 Regression-sensitive: non-folder parent must silently abort (no confirm dialog)
   it("should return without calling confirm when parent type is not a valid folder", async () => {
      vi.spyOn(ComponentTool, "showMessageDialog").mockResolvedValue("ok");
      const comp = await renderComponent();
      const nonFolderEntry = makeEntry(AssetType.WORKSHEET);
      (comp as any).assetTree.getParentNode.mockReturnValue(null);

      comp.nodeDrop(makeDropEvent(makeNode(nonFolderEntry)));

      expect(ComponentTool.showMessageDialog).not.toHaveBeenCalled();
   });

   // 🔁 Regression-sensitive: worksheet cannot be moved into a REPOSITORY_FOLDER (viewsheet-only)
   it("should return without calling confirm when entry type mismatches REPOSITORY_FOLDER", async () => {
      vi.spyOn(ComponentTool, "showMessageDialog").mockResolvedValue("ok");
      const comp = await renderComponent();

      const folderEntry = makeEntry(AssetType.REPOSITORY_FOLDER, { folder: true });
      const wsEntry = makeEntry(AssetType.WORKSHEET);
      DRAG_SERVICE_MOCK.getDragData.mockReturnValue({
         worksheet: JSON.stringify([wsEntry]),
      });
      (comp as any).assetTree.getNodeByData.mockReturnValue({ data: wsEntry });

      comp.nodeDrop(makeDropEvent(makeNode(folderEntry)));

      expect(ComponentTool.showMessageDialog).not.toHaveBeenCalled();
   });

   it("should call confirm when a WORKSHEET is dropped onto a FOLDER parent", async () => {
      vi.spyOn(ComponentTool, "showMessageDialog").mockResolvedValue("cancel");
      const comp = await renderComponent();

      const folderEntry = makeEntry(AssetType.FOLDER, { folder: true });
      const wsEntry = makeEntry(AssetType.WORKSHEET);
      DRAG_SERVICE_MOCK.getDragData.mockReturnValue({
         worksheet: JSON.stringify([wsEntry]),
      });
      (comp as any).assetTree.getNodeByData.mockReturnValue({ data: wsEntry });

      comp.nodeDrop(makeDropEvent(makeNode(folderEntry)));

      await waitFor(() =>
         expect(ComponentTool.showMessageDialog).toHaveBeenCalledWith(
            expect.anything(),
            "_#(js:Confirm)",
            "_#(js:em.reports.drag.confirm)",
            expect.anything(),
            expect.anything(),
         ),
      );
   });

   it("should not call confirm when drag entries are not from this tree", async () => {
      vi.spyOn(ComponentTool, "showMessageDialog").mockResolvedValue("ok");
      const comp = await renderComponent();

      const folderEntry = makeEntry(AssetType.FOLDER, { folder: true });
      const wsEntry = makeEntry(AssetType.WORKSHEET);
      DRAG_SERVICE_MOCK.getDragData.mockReturnValue({
         worksheet: JSON.stringify([wsEntry]),
      });
      // getNodeByData returns null → entry not from this tree
      (comp as any).assetTree.getNodeByData.mockReturnValue(null);

      comp.nodeDrop(makeDropEvent(makeNode(folderEntry)));

      expect(ComponentTool.showMessageDialog).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 4: ngOnInit — tabularDataSourceTypes [Risk 2]
// ---------------------------------------------------------------------------

describe("AssetTreePane — ngOnInit", () => {

   it("should load tabularDataSourceTypes from HTTP", async () => {
      server.use(
         http.get("*/api/composer/tabularDataSourceTypes", () =>
            HttpResponse.json([{ name: "jdbc", label: "JDBC" }]),
         ),
      );
      const comp = await renderComponent();
      expect(comp.tabularDataSourceTypes).toEqual([{ name: "jdbc", label: "JDBC" }]);
   });

   it("should set tabularDataSourceTypes to [] when server returns empty array", async () => {
      const comp = await renderComponent();
      expect(comp.tabularDataSourceTypes).toEqual([]);
   });
});

// ---------------------------------------------------------------------------
// Group 5: getRecentRootFun [Risk 2]
// ---------------------------------------------------------------------------

describe("AssetTreePane — getRecentRootFun", () => {

   // 🔁 Regression-sensitive: alias must take precedence over path-tail
   it("should use alias as label when alias is present on the entry", async () => {
      const comp = await renderComponent();
      recentChange$.next([{
         alias: "My Alias",
         path: "User Workspace/Reports/Sheet",
         type: AssetType.VIEWSHEET,
         identifier: "vs-1",
      }]);

      let nodes: any[];
      comp.getRecentRootFun()().subscribe(n => (nodes = n));

      expect(nodes[0].label).toBe("My Alias");
      expect(nodes[0].treeView).toBe(TreeView.RECENT_VIEW);
      expect(nodes[0].leaf).toBe(true);
   });

   it("should use the last path segment as label when alias is null", async () => {
      const comp = await renderComponent();
      recentChange$.next([{
         alias: null,
         path: "User Workspace/Reports/MySheet",
         type: AssetType.WORKSHEET,
         identifier: "ws-1",
      }]);

      let nodes: any[];
      comp.getRecentRootFun()().subscribe(n => (nodes = n));

      expect(nodes[0].label).toBe("MySheet");
   });

   it("should call removeNonExistItems each time the factory function is invoked", async () => {
      const comp = await renderComponent();
      recentChange$.next([]);

      comp.getRecentRootFun()().subscribe();

      expect(RECENT_SERVICE_MOCK.removeNonExistItems).toHaveBeenCalledTimes(1);
   });

   it("should return empty array when data is empty", async () => {
      const comp = await renderComponent();
      recentChange$.next([]);

      let nodes: any[];
      comp.getRecentRootFun()().subscribe(n => (nodes = n));

      expect(nodes).toEqual([]);
   });
});

// ---------------------------------------------------------------------------
// Group 6: hasMenu / createActions — action visibility [Risk 2]
// ---------------------------------------------------------------------------

describe("AssetTreePane — hasMenu / createActions", () => {

   // 🔁 Regression-sensitive: missing context menu for valid entries breaks UX
   it("should return true for a VIEWSHEET entry (open/rename/delete/meta actions)", async () => {
      const comp = await renderComponent();
      expect(comp.hasMenu(makeNode(makeEntry(AssetType.VIEWSHEET)))).toBe(true);
   });

   // 🔁 Regression-sensitive: QUERY_SCOPE entries must never produce a context menu
   it("should return false for a QUERY_SCOPE entry", async () => {
      const comp = await renderComponent();
      const entry = makeEntry(AssetType.QUERY, { scope: AssetConstants.QUERY_SCOPE });
      expect(comp.hasMenu(makeNode(entry))).toBe(false);
   });

   it("should return false when node data is null", async () => {
      const comp = await renderComponent();
      expect(comp.hasMenu(makeNode(null))).toBe(false);
   });

   it("should include new-query action for a jdbc data source with sqlEnabled=true", async () => {
      server.use(
         http.get("*/api/composer/tabularDataSourceTypes", () => HttpResponse.json([])),
      );
      const comp = await renderComponent();
      const dsEntry = makeEntry(AssetType.DATA_SOURCE, {
         properties: { "datasource.type": "jdbc", "sqlEnabled": "true" },
      });
      const node = makeNode(dsEntry);
      const actions = (comp as any).createActions([null, node, [node]]);
      const newQueryAction = actions[0]?.actions.find((a: any) => a.id() === "asset-tree new-query");
      expect(newQueryAction).toBeDefined();
   });

   it("should return true for a FOLDER entry (new-folder/rename/delete actions visible)", async () => {
      const comp = await renderComponent();
      // AssetEntryHelper.isFolder(entry) checks entry.folder === true
      const folderEntry = makeEntry(AssetType.FOLDER, { folder: true });
      const node = makeNode(folderEntry);
      const actions = (comp as any).createActions([null, node, [node]]);
      expect(actions[0].visible).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 7: sendAddFolderEvent — HTTP POST [Risk 2]
// ---------------------------------------------------------------------------

describe("AssetTreePane — sendAddFolderEvent", () => {

   it("should POST to add-folder and not show any message when body is null", async () => {
      let called = false;
      server.use(
         http.post("*/api/composer/asset-tree/add-folder", () => {
            called = true;
            return new HttpResponse(null, { status: 200 });
         }),
      );
      const comp = await renderComponent();
      const msgSpy = vi.spyOn(ComponentTool, "showMessageDialog");

      (comp as any).sendAddFolderEvent(makeEntry(AssetType.FOLDER, { path: "User Workspace" }), "NewFolder");

      await waitFor(() => expect(called).toBe(true));
      expect(msgSpy).not.toHaveBeenCalled();
   });

   // 🔁 Regression-sensitive: server error messages (e.g. duplicate name) must surface to user
   it("should call showMessageDialog with the server error message when body is non-null", async () => {
      server.use(
         http.post("*/api/composer/asset-tree/add-folder", () =>
            HttpResponse.json({ type: "ERROR", message: "Duplicate folder name" }),
         ),
      );
      const comp = await renderComponent();
      vi.spyOn(ComponentTool, "showMessageDialog").mockResolvedValue("ok");

      (comp as any).sendAddFolderEvent(makeEntry(AssetType.FOLDER, { path: "User Workspace" }), "Dup");

      await waitFor(() =>
         expect(ComponentTool.showMessageDialog).toHaveBeenCalledWith(
            expect.anything(),
            "_#(js:Error)",
            "Duplicate folder name",
            expect.anything(),
            expect.anything(),
         ),
      );
   });
});

// ---------------------------------------------------------------------------
// Group 8: isAssetOpened [Risk 2]
// ---------------------------------------------------------------------------

describe("AssetTreePane — isAssetOpened", () => {

   // 🔁 Regression-sensitive: wrong result allows rename/delete of an open asset
   it("should return true when a tab identifier matches the entry identifier", async () => {
      const comp = await renderComponent();
      const entry = makeEntry(AssetType.VIEWSHEET, { identifier: "vs-open", path: "my/vs" });
      comp.opendTabs = [{ type: "viewsheet", asset: { id: "vs-open", label: "vs" } } as any];
      (comp as any).assetTree.findAssetTreeNodeParentFromIdentifier.mockReturnValue({
         data: { path: "my" },
      });

      expect((comp as any).isAssetOpened(entry)).toBe(true);
   });

   it("should return false when no tab identifier matches and no parent path overlap", async () => {
      const comp = await renderComponent();
      const entry = makeEntry(AssetType.VIEWSHEET, { identifier: "vs-closed", path: "other/vs" });
      comp.opendTabs = [{ type: "viewsheet", asset: { id: "different-id", label: "x" } } as any];
      (comp as any).assetTree.findAssetTreeNodeParentFromIdentifier.mockReturnValue(null);

      expect((comp as any).isAssetOpened(entry)).toBe(false);
   });

   it("should return false when opendTabs is empty", async () => {
      const comp = await renderComponent();
      const entry = makeEntry(AssetType.VIEWSHEET, { identifier: "vs-1" });
      comp.opendTabs = [];

      expect((comp as any).isAssetOpened(entry)).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 9: nodeDrag — drag image [Risk 2]
// ---------------------------------------------------------------------------

describe("AssetTreePane — nodeDrag", () => {

   it("should call GuiTool.createDragImage with path-tail labels for worksheet entries", async () => {
      const comp = await renderComponent();
      const createSpy = vi.spyOn(GuiTool, "createDragImage").mockReturnValue(document.createElement("div"));
      vi.spyOn(GuiTool, "setDragImage").mockResolvedValue();

      const event: any = {
         dataTransfer: {
            getData: vi.fn().mockReturnValue(
               JSON.stringify({ worksheet: [{ path: "User Workspace/Reports/MySheet" }] }),
            ),
         },
      };

      comp.nodeDrag(event);

      expect(createSpy).toHaveBeenCalledWith(["MySheet"]);
   });

   it("should use path as label when path has no slash", async () => {
      const comp = await renderComponent();
      const createSpy = vi.spyOn(GuiTool, "createDragImage").mockReturnValue(document.createElement("div"));
      vi.spyOn(GuiTool, "setDragImage").mockResolvedValue();

      const event: any = {
         dataTransfer: {
            getData: vi.fn().mockReturnValue(
               JSON.stringify({ viewsheet: [{ path: "NoSlashSheet" }] }),
            ),
         },
      };

      comp.nodeDrag(event);

      expect(createSpy).toHaveBeenCalledWith(["NoSlashSheet"]);
   });

   it("should NOT call createDragImage when textData is empty", async () => {
      const comp = await renderComponent();
      const createSpy = vi.spyOn(GuiTool, "createDragImage");

      comp.nodeDrag({ dataTransfer: { getData: vi.fn().mockReturnValue("") } } as any);

      expect(createSpy).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 10: ngOnChanges — inactive flag [Risk 1]
// ---------------------------------------------------------------------------

describe("AssetTreePane — ngOnChanges inactive", () => {

   it("should call changeDetector.detach() when inactive is true", async () => {
      const comp = await renderComponent();
      const spy = vi.spyOn((comp as any).changeDetector, "detach");
      comp.inactive = true;
      comp.ngOnChanges({} as any);
      expect(spy).toHaveBeenCalledTimes(1);
   });

   it("should call changeDetector.reattach() when inactive is false", async () => {
      const comp = await renderComponent();
      const spy = vi.spyOn((comp as any).changeDetector, "reattach");
      comp.inactive = false;
      comp.ngOnChanges({} as any);
      expect(spy).toHaveBeenCalledTimes(1);
   });
});

// ---------------------------------------------------------------------------
// Group 11: openNodeSheet — deployed guard [Risk 1]
// ---------------------------------------------------------------------------

describe("AssetTreePane — openNodeSheet", () => {

   it("should emit onOpenSheet when deployed is false", async () => {
      const comp = await renderComponent();
      comp.deployed = false;
      const spy = vi.fn();
      comp.onOpenSheet.subscribe(spy);
      const entry = makeEntry(AssetType.WORKSHEET, { identifier: "ws-test" });

      comp.openNodeSheet(makeNode(entry));

      expect(spy).toHaveBeenCalledWith({ type: "worksheet", assetId: "ws-test" });
   });

   // 🔁 Regression-sensitive: deployed mode must prevent all sheet navigation
   it("should NOT emit onOpenSheet when deployed is true", async () => {
      const comp = await renderComponent();
      comp.deployed = true;
      const spy = vi.fn();
      comp.onOpenSheet.subscribe(spy);

      comp.openNodeSheet(makeNode(makeEntry(AssetType.WORKSHEET)));

      expect(spy).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 12: isRejectNodes [Risk 1]
// ---------------------------------------------------------------------------

describe("AssetTreePane — isRejectNodes", () => {

   it("should return true when any node has dragName=column", async () => {
      const comp = await renderComponent();
      expect(
         comp.isRejectNodes([{ dragName: "column" } as any, { dragName: "worksheet" } as any]),
      ).toBe(true);
   });

   it("should return false when no node has dragName=column", async () => {
      const comp = await renderComponent();
      expect(comp.isRejectNodes([{ dragName: "worksheet" } as any])).toBe(false);
   });

   it("should return false for an empty nodes array", async () => {
      const comp = await renderComponent();
      expect(comp.isRejectNodes([])).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 13: showMessage [Risk 1]
// ---------------------------------------------------------------------------

describe("AssetTreePane — showMessage", () => {

   it("should call ComponentTool.showMessageDialog with Error title and the provided message", async () => {
      const comp = await renderComponent();
      const spy = vi.spyOn(ComponentTool, "showMessageDialog").mockResolvedValue("ok");

      comp.showMessage("Something went wrong");

      expect(spy).toHaveBeenCalledWith(
         expect.anything(),
         "_#(js:Error)",
         "Something went wrong",
         { ok: "_#(js:OK)" },
         expect.anything(),
      );
   });
});

// ---------------------------------------------------------------------------
// Group 14: openAssetTreeContextmenu [Risk 1]
// ---------------------------------------------------------------------------

describe("AssetTreePane — openAssetTreeContextmenu", () => {

   it("should open dropdown at the mouse-event coordinates", async () => {
      const comp = await renderComponent();
      const entry = makeEntry(AssetType.VIEWSHEET);
      const node = makeNode(entry);
      const mouseEvent = { clientX: 150, clientY: 250 } as MouseEvent;

      comp.openAssetTreeContextmenu([mouseEvent, node, [node]]);

      expect(DROPDOWN_MOCK.open).toHaveBeenCalledWith(
         expect.anything(),
         expect.objectContaining({ position: { x: 150, y: 250 }, contextmenu: true }),
      );
   });
});
