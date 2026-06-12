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
 * AssetTreePane — Pass 2: Risk (async/destructive)
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — deleteEntries (@HostListener): inactive guard; QUERY_SCOPE filter; routes to deleteAssets
 *   Group 2 [Risk 3] — deleteAssets: folder vs non-folder confirm message; onTabClose for open tabs
 *   Group 3 [Risk 3] — dispatchRemoveAssetEvent: HTTP POST; surfaces non-CONFIRM error; re-confirms on CONFIRM
 *   Group 4 [Risk 3] — dispatchChangeAssetEvent: blocks open TABLE_STYLE; POSTs change-asset; re-confirms on CONFIRM
 *   Group 5 [Risk 2] — confirm: ok→onOk; cancel→onCancel; rejection→onCancel
 *   Group 6 [Risk 1] — deleteRecentAssets: delegates to composerRecentService.removeRecentlyViewed
 *
 * Confirmed bugs: none
 *
 * Suspected bugs (header only):
 *   Suspicion A — dispatchRemoveAssetEvent: no takeUntilDestroyed; stale remove callback fires
 *     after component destruction, potentially modifying a dead component's tree state.
 *   Suspicion B — moveAssetToRecyclingBin: CONFIRM with null message silently recurses without
 *     showing the user any prompt; hard to distinguish from a successful no-confirm delete.
 *
 * Out of scope this pass (covered in AssetTreePane.interaction.tl.spec.ts):
 *   openSheetFromAsset, renameAsset, dispatchRenameAssetEvent, nodeDrop, sendAddFolderEvent,
 *   hasMenu, createActions, isAssetOpened, nodeDrag, isRejectNodes, showMessage,
 *   openAssetTreeContextmenu, getRecentRootFun, ngOnChanges
 */

import { http, HttpResponse } from "msw";
import { waitFor } from "@testing-library/angular";

import { server } from "@test-mocks/server";
import { AssetType } from "../../../../../../shared/data/asset-type";
import { AssetConstants } from "../../../common/data/asset-constants";
import { ComponentTool } from "../../../common/util/component-tool";
import {
   renderComponent,
   makeEntry,
   makeNode,
   MODAL_MOCK,
   RECENT_SERVICE_MOCK,
   recentChange$,
} from "./AssetTreePane.test-helpers";

// ---------------------------------------------------------------------------
// Per-test reset
// ---------------------------------------------------------------------------

beforeEach(() => {
   MODAL_MOCK.open.mockClear();
   RECENT_SERVICE_MOCK.addRecentlyViewed.mockClear();
   RECENT_SERVICE_MOCK.removeRecentlyViewed.mockClear();
   recentChange$.next([]);
});

afterEach(() => vi.restoreAllMocks());

// ---------------------------------------------------------------------------
// Group 1: deleteEntries (@HostListener keyup.delete) [Risk 3]
// ---------------------------------------------------------------------------

describe("AssetTreePane — deleteEntries", () => {

   // 🔁 Regression-sensitive: inactive pane must never trigger deletes on keypress
   it("should not delete when component is inactive", async () => {
      const comp = await renderComponent();
      comp.inactive = true;
      comp.selectedNodes = [makeNode(makeEntry(AssetType.WORKSHEET))];
      vi.spyOn(ComponentTool, "showMessageDialog");

      comp.deleteEntries();

      expect(ComponentTool.showMessageDialog).not.toHaveBeenCalled();
   });

   it("should not delete when selectedNodes is empty", async () => {
      const comp = await renderComponent();
      comp.inactive = false;
      comp.selectedNodes = [];
      vi.spyOn(ComponentTool, "showMessageDialog");

      comp.deleteEntries();

      expect(ComponentTool.showMessageDialog).not.toHaveBeenCalled();
   });

   // 🔁 Regression-sensitive: QUERY_SCOPE entries must be stripped before delete confirmation
   it("should strip QUERY_SCOPE entries and not call deleteAssets when all entries are queries", async () => {
      const comp = await renderComponent();
      comp.inactive = false;
      comp.selectedNodes = [
         makeNode(makeEntry(AssetType.QUERY, { scope: AssetConstants.QUERY_SCOPE })),
      ];
      vi.spyOn(ComponentTool, "showMessageDialog");

      comp.deleteEntries();

      expect(ComponentTool.showMessageDialog).not.toHaveBeenCalled();
   });

   it("should call deleteAssets with non-QUERY entries when mixed selection", async () => {
      vi.spyOn(ComponentTool, "showMessageDialog").mockResolvedValue("cancel");
      const comp = await renderComponent();
      comp.inactive = false;
      const wsEntry = makeEntry(AssetType.WORKSHEET);
      const queryEntry = makeEntry(AssetType.QUERY, { scope: AssetConstants.QUERY_SCOPE });
      comp.selectedNodes = [makeNode(wsEntry), makeNode(queryEntry)];

      comp.deleteEntries();

      await waitFor(() =>
         expect(ComponentTool.showMessageDialog).toHaveBeenCalledWith(
            expect.anything(),
            "_#(js:Confirm)",
            "_#(js:common.tree.deleteSelected)",
            expect.anything(),
            expect.anything(),
         ),
      );
   });
});

// ---------------------------------------------------------------------------
// Group 2: deleteAssets [Risk 3]
// ---------------------------------------------------------------------------

describe("AssetTreePane — deleteAssets", () => {

   // 🔁 Regression-sensitive: folder message differs from asset message — wrong message confuses user
   it("should show folder-specific message when any entry is a folder", async () => {
      vi.spyOn(ComponentTool, "showMessageDialog").mockResolvedValue("cancel");
      const comp = await renderComponent();
      const folderEntry = makeEntry(AssetType.FOLDER, { folder: true });

      (comp as any).deleteAssets([folderEntry]);

      await waitFor(() =>
         expect(ComponentTool.showMessageDialog).toHaveBeenCalledWith(
            expect.anything(),
            "_#(js:Confirm)",
            "_#(js:common.tree.removeFolder)",
            expect.anything(),
            expect.anything(),
         ),
      );
   });

   it("should show non-folder message when no entry is a folder", async () => {
      vi.spyOn(ComponentTool, "showMessageDialog").mockResolvedValue("cancel");
      const comp = await renderComponent();
      const entry = makeEntry(AssetType.VIEWSHEET);

      (comp as any).deleteAssets([entry]);

      await waitFor(() =>
         expect(ComponentTool.showMessageDialog).toHaveBeenCalledWith(
            expect.anything(),
            "_#(js:Confirm)",
            "_#(js:common.tree.deleteSelected)",
            expect.anything(),
            expect.anything(),
         ),
      );
   });

   // 🔁 Regression-sensitive: open library asset tabs must be closed before the delete request
   it("should emit onTabClose for each open library tab matching the deleted entry", async () => {
      vi.spyOn(ComponentTool, "showMessageDialog").mockResolvedValue("ok");
      server.use(
         http.post("*/api/composer/asset-tree/remove-asset", () =>
            new HttpResponse(null, { status: 200 }),
         ),
      );

      const comp = await renderComponent();
      const entry = makeEntry(AssetType.TABLE_STYLE, { identifier: "ts-open" });
      const tab = { type: "tableStyle", asset: { id: "ts-open", label: "ts" } } as any;
      comp.opendTabs = [tab];
      (comp as any).assetTree.findAssetTreeNodeParentFromIdentifier.mockReturnValue({
         data: { path: "User Workspace/TABLE_STYLE" },
      });

      const tabCloseSpy = vi.fn();
      comp.onTabClose.subscribe(tabCloseSpy);

      (comp as any).deleteAssets([entry]);

      await waitFor(() => expect(tabCloseSpy).toHaveBeenCalledWith(tab));
   });

   it("should NOT dispatch remove-asset when user clicks cancel", async () => {
      vi.spyOn(ComponentTool, "showMessageDialog").mockResolvedValue("cancel");
      let removeCalled = false;
      server.use(
         http.post("*/api/composer/asset-tree/remove-asset", () => {
            removeCalled = true;
            return new HttpResponse(null, { status: 200 });
         }),
      );

      const comp = await renderComponent();
      (comp as any).deleteAssets([makeEntry(AssetType.VIEWSHEET)]);

      await waitFor(() =>
         expect(ComponentTool.showMessageDialog).toHaveBeenCalled(),
      );
      // Allow time for any errant HTTP call to complete
      await new Promise(r => setTimeout(r, 20));
      expect(removeCalled).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 3: dispatchRemoveAssetEvent [Risk 3]
// ---------------------------------------------------------------------------

describe("AssetTreePane — dispatchRemoveAssetEvent", () => {

   it("should POST to remove-asset with the entry and confirmed=false by default", async () => {
      let body: any;
      server.use(
         http.post("*/api/composer/asset-tree/remove-asset", async ({ request }) => {
            body = await request.json();
            return new HttpResponse(null, { status: 200 });
         }),
      );

      const comp = await renderComponent();
      const entry = makeEntry(AssetType.VIEWSHEET, { identifier: "vs-del" });

      (comp as any).dispatchRemoveAssetEvent(entry);

      await waitFor(() => expect(body).toBeDefined());
      expect(body.confirmed).toBe(false);
   });

   // 🔁 Regression-sensitive: non-CONFIRM error must surface to user; silent swallow = orphaned tree node
   it("should call showMessage when response body has type ERROR", async () => {
      server.use(
         http.post("*/api/composer/asset-tree/remove-asset", () =>
            HttpResponse.json({ type: "ERROR", message: "Remove failed: asset in use" }),
         ),
      );

      const comp = await renderComponent();
      vi.spyOn(ComponentTool, "showMessageDialog").mockResolvedValue("ok");

      (comp as any).dispatchRemoveAssetEvent(makeEntry(AssetType.VIEWSHEET));

      await waitFor(() =>
         expect(ComponentTool.showMessageDialog).toHaveBeenCalledWith(
            expect.anything(),
            "_#(js:Error)",
            "Remove failed: asset in use",
            expect.anything(),
            expect.anything(),
         ),
      );
   });

   // 🔁 Regression-sensitive: CONFIRM response must show user the confirm dialog and retry with confirmed=true
   it("should show confirm dialog and re-POST with confirmed=true on CONFIRM response", async () => {
      let callCount = 0;
      let lastConfirmed: boolean;

      server.use(
         http.post("*/api/composer/asset-tree/remove-asset", async ({ request }) => {
            const b = await request.json() as any;
            lastConfirmed = b.confirmed;
            callCount++;
            if(callCount === 1) {
               return HttpResponse.json({ type: "CONFIRM", message: "Asset has dependents, proceed?" });
            }
            return new HttpResponse(null, { status: 200 });
         }),
      );

      vi.spyOn(ComponentTool, "showMessageDialog").mockResolvedValue("ok");

      const comp = await renderComponent();
      (comp as any).dispatchRemoveAssetEvent(makeEntry(AssetType.VIEWSHEET));

      await waitFor(() => expect(callCount).toBe(2));
      expect(lastConfirmed).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 4: dispatchChangeAssetEvent [Risk 3]
// ---------------------------------------------------------------------------

describe("AssetTreePane — dispatchChangeAssetEvent", () => {

   function makeTargetNode(entry: any): any {
      return {
         ...makeNode(entry),
         loading: false,
         expanded: false,
         leaf: false,
         children: [],
      };
   }

   // 🔁 Regression-sensitive: open TABLE_STYLE must block the move operation entirely
   it("should show warning and abort when a TABLE_STYLE entry is currently open", async () => {
      vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue(false as any);
      let changeCalled = false;
      server.use(
         http.post("*/api/composer/asset-tree/change-asset", () => {
            changeCalled = true;
            return new HttpResponse(null, { status: 200 });
         }),
      );

      const comp = await renderComponent();
      const entry = makeEntry(AssetType.TABLE_STYLE, { identifier: "ts-open" });
      const parentEntry = makeEntry(AssetType.TABLE_STYLE_FOLDER, { folder: true });
      const targetNode = makeTargetNode(parentEntry);

      comp.opendTabs = [{ type: "tableStyle", asset: { id: "ts-open", label: "ts" } } as any];
      (comp as any).assetTree.findAssetTreeNodeParentFromIdentifier.mockReturnValue({
         data: { path: "styles" },
      });

      (comp as any).dispatchChangeAssetEvent(targetNode, parentEntry, [entry]);

      await waitFor(() => expect(ComponentTool.showConfirmDialog).toHaveBeenCalled());
      await new Promise(r => setTimeout(r, 20));
      expect(changeCalled).toBe(false);
   });

   it("should POST to change-asset when no conflicting open TABLE_STYLE entry", async () => {
      let body: any;
      server.use(
         http.post("*/api/composer/asset-tree/change-asset", async ({ request }) => {
            body = await request.json();
            return new HttpResponse(null, { status: 200 });
         }),
      );

      vi.spyOn(ComponentTool, "showConfirmDialog");

      const comp = await renderComponent();
      const entry = makeEntry(AssetType.WORKSHEET);
      const parentEntry = makeEntry(AssetType.FOLDER, { folder: true });
      const targetNode = makeTargetNode(parentEntry);
      comp.opendTabs = [];

      (comp as any).dispatchChangeAssetEvent(targetNode, parentEntry, [entry]);

      await waitFor(() => expect(body).toBeDefined());
   });

   // 🔁 Regression-sensitive: CONFIRM response must prompt user and retry with confirmed=true
   it("should show confirm dialog and re-POST with confirmed=true on CONFIRM response", async () => {
      let callCount = 0;
      let lastBody: any;

      server.use(
         http.post("*/api/composer/asset-tree/change-asset", async ({ request }) => {
            lastBody = await request.json() as any;
            callCount++;
            if(callCount === 1) {
               return HttpResponse.json({ type: "CONFIRM", message: "Asset has open references?" });
            }
            return new HttpResponse(null, { status: 200 });
         }),
      );

      vi.spyOn(ComponentTool, "showMessageDialog").mockResolvedValue("ok");

      const comp = await renderComponent();
      const entry = makeEntry(AssetType.WORKSHEET);
      const parentEntry = makeEntry(AssetType.FOLDER, { folder: true });
      const targetNode = makeTargetNode(parentEntry);
      comp.opendTabs = [];

      (comp as any).dispatchChangeAssetEvent(targetNode, parentEntry, [entry]);

      await waitFor(() => expect(callCount).toBe(2));
      expect(lastBody.confirmed).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 5: confirm [Risk 2]
// ---------------------------------------------------------------------------

describe("AssetTreePane — confirm", () => {

   // 🔁 Regression-sensitive: onOk must fire on "ok"; a swap causes destructive operations to cancel
   it("should call onOk when showMessageDialog resolves with ok", async () => {
      vi.spyOn(ComponentTool, "showMessageDialog").mockResolvedValue("ok");
      const comp = await renderComponent();
      const onOk = vi.fn();
      const onCancel = vi.fn();

      comp.confirm("Proceed?", onOk, onCancel);

      await waitFor(() => expect(onOk).toHaveBeenCalled());
      expect(onCancel).not.toHaveBeenCalled();
   });

   it("should call onCancel when showMessageDialog resolves with cancel", async () => {
      vi.spyOn(ComponentTool, "showMessageDialog").mockResolvedValue("cancel");
      const comp = await renderComponent();
      const onOk = vi.fn();
      const onCancel = vi.fn();

      comp.confirm("Proceed?", onOk, onCancel);

      await waitFor(() => expect(onCancel).toHaveBeenCalled());
      expect(onOk).not.toHaveBeenCalled();
   });

   it("should call onCancel when the dialog is dismissed (promise rejected)", async () => {
      vi.spyOn(ComponentTool, "showMessageDialog").mockRejectedValue(new Error("dismissed"));
      const comp = await renderComponent();
      const onOk = vi.fn();
      const onCancel = vi.fn();

      comp.confirm("Proceed?", onOk, onCancel);

      await waitFor(() => expect(onCancel).toHaveBeenCalled());
      expect(onOk).not.toHaveBeenCalled();
   });

   it("should not throw when onCancel is null and dialog is dismissed", async () => {
      vi.spyOn(ComponentTool, "showMessageDialog").mockRejectedValue(new Error("dismissed"));
      const comp = await renderComponent();
      const onOk = vi.fn();

      await expect(async () => {
         comp.confirm("Proceed?", onOk);
         await new Promise(r => setTimeout(r, 20));
      }).not.toThrow();
   });
});

// ---------------------------------------------------------------------------
// Group 6: deleteRecentAssets [Risk 1]
// ---------------------------------------------------------------------------

describe("AssetTreePane — deleteRecentAssets", () => {

   it("should call composerRecentService.removeRecentlyViewed with the given entries", async () => {
      const comp = await renderComponent();
      const entries = [makeEntry(AssetType.VIEWSHEET)];

      (comp as any).deleteRecentAssets(entries);

      expect(RECENT_SERVICE_MOCK.removeRecentlyViewed).toHaveBeenCalledWith(entries);
   });
});
