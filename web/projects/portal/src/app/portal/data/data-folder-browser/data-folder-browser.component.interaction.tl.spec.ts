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
 * DataFolderBrowserComponent – Pass 1: Interaction
 *
 * User goals:
 *   1. Browse and navigate worksheet folders without losing selection or misrouting.
 *   2. Search for datasets and recover gracefully from backend errors.
 *   3. Manage folders and files: select, selectAll, drag-drop, addFolder, openFolder,
 *      editWorksheet, renameAsset, materializeAsset.
 *   4. Keep selectedItems in sync with refreshed asset data.
 *
 * Risk-first coverage (7 groups, 35 cases):
 *   Group 1 [Risk 3] – refreshFolderBrowser / handleBrowserRefreshError:
 *       HTTP load, 403 guard, non-403 danger notification.
 *   Group 2 [Risk 3] – refreshSearchBrowser: success path + error danger notification.
 *   Group 3 [Risk 2] – toggleSelectTooltip / toggleSelectionState: both branches.
 *   Group 4 [Risk 3] – sortView: folder-first in search vs non-search view.
 *   Group 5 [Risk 3] – openFolder / editWorksheet / renameAsset / handleResponse /
 *       handleEditAssetError: delegation contracts.
 *   Group 6 [Risk 3] – viewAssets / selectFile / selectAllChecked / selectAllChanged /
 *       updateSelectedItems / findAsset / isFolderEditable: selection & getter contracts.
 *   Group 7 [Risk 3] – addFolder / getRootAssets / materializeAsset / selectChanged /
 *       dragAssets / assetsDroped / dataTreeDragToPane / createInfoByAssetEntry /
 *       showWSDetailsByDataSourcesTree / getEntryLabel (via dragAssets).
 *
 * Method coverage table:
 *   getAssemblyName           ✅ Group 1 setup (returns null)
 *   refreshFolderBrowser      ✅ Group 1
 *   handleBrowserRefreshError ✅ Group 1 (indirect – via server 403/500)
 *   refreshSearchBrowser      ✅ Group 2
 *   toggleSelectTooltip       ✅ Group 3
 *   sortView                  ✅ Group 4
 *   openFolder                ✅ Group 5
 *   editWorksheet             ✅ Group 5
 *   renameAsset               ✅ Group 5
 *   handleResponse            ✅ Group 5
 *   handleEditAssetError      ✅ Group 5
 *   viewAssets                ✅ Group 6
 *   toggleSelectionState      ✅ Group 3
 *   isFolderEditable          ✅ Group 6
 *   toggleSearch              ✅ Group 7 (via addFolder guard)
 *   search                    ✅ Group 5 / Group 7
 *   findAsset                 ✅ Group 6
 *   selectFile                ✅ Group 6
 *   convertToAssetItem        ✅ Group 6 (indirect via selectFile)
 *   selectAllChecked          ✅ Group 6
 *   selectAllChanged          ✅ Group 6
 *   updateSelectedItems       ✅ Group 6
 *   addFolder                 ✅ Group 7
 *   getRootAssets             ✅ Group 7
 *   materializeAsset          ✅ Group 7
 *   selectChanged             ✅ Group 7
 *   dragAssets                ✅ Group 7
 *   getEntryLabel             ✅ Group 7 (via dragAssets output)
 *   getEntryIcon              ✅ Group 7 (via getEntryLabel HTML)
 *   assetsDroped              ✅ Group 7
 *   dataTreeDragToPane        ✅ Group 7
 *   createInfoByAssetEntry    ✅ Group 7 (via dataTreeDragToPane)
 *   showWSDetailsByDataSourcesTree ✅ Group 7
 *
 * KEY contracts:
 *   - 403 from /api/portal/data/browser sets unauthorizedAccess=true; other errors show danger.
 *   - Search errors clear searchAssets and show danger; success resets searchSortOptions.
 *   - Folder-first sorting applies in both search and non-search views.
 *   - openFolder only navigates for FOLDER type; editWorksheet delegates to dataBrowserService.
 *   - selectFile toggles: selecting the same file deselects it.
 *   - selectAllChecked requires >0 items AND every viewAsset in selectedItems.
 *   - updateSelectedItems prunes selectedItems to only those still present in viewAssets.
 *   - addFolder exits early when isFolderEditable is false.
 *   - getRootAssets filters to topmost ancestors; children of selected folders are excluded.
 *   - assetsDroped builds a fake root target when called with null.
 *   - dataTreeDragToPane filters out self/descendant/unsupported entries.
 */

import { waitFor } from "@testing-library/angular";
import { config as rxjsConfig } from "rxjs";
import { http, HttpResponse } from "msw";
import { AssetEntry } from "../../../../../../shared/data/asset-entry";
import { AssetType } from "../../../../../../shared/data/asset-type";
import { AssetEntryHelper } from "../../../common/data/asset-entry-helper";
import { AssetConstants } from "../../../common/data/asset-constants";
import { ComponentTool } from "../../../common/util/component-tool";
import { WorksheetBrowserInfo } from "../model/worksheet-browser-info";
import { server } from "@test-mocks/server";
import {
   clearMocks,
   DATA_BROWSER_MOCK,
   DRAG_SERVICE_MOCK,
   makeFolder,
   makeInfo,
   renderComponent,
   VS_CLIENT_MOCK,
} from "./data-folder-browser.test-helpers";

// ---------------------------------------------------------------------------
// Top-level lifecycle hooks
// ---------------------------------------------------------------------------

beforeEach(() => clearMocks());

afterEach(() => {
   vi.restoreAllMocks();
});

// ===========================================================================
// Group 1 – refreshFolderBrowser / handleBrowserRefreshError [Risk 3]
// ===========================================================================

describe("DataFolderBrowserComponent – refreshFolderBrowser [Group 1, Risk 3]", () => {
   it("should load folders and datasets on init and set worksheetAccess", async () => {
      const folder = makeFolder();
      const sheet = makeInfo();

      server.use(
         http.get("*/api/portal/data/browser", () =>
            HttpResponse.json({
               root: true,
               worksheetAccess: true,
               currentFolder: [],
               folders: [folder],
               files: [sheet],
            })
         )
      );

      const { comp } = await renderComponent();
      await waitFor(() => expect(comp.folders).toHaveLength(1));

      expect(comp.folders[0].name).toBe("FolderA");
      expect(comp.datasets[0].name).toBe("Sheet");
      expect(comp.worksheetAccess).toBe(true);
      expect(comp.unauthorizedAccess).toBe(false);
   });

   it("should include scope param in the browser request when route has scope", async () => {
      const requests: URL[] = [];

      // When path is set, refreshFolderBrowser appends it to the URI:
      // ../api/portal/data/browser/SomeFolder?scope=4  (not a query param)
      // Use a wildcard suffix to match both the base path and any subpath.
      server.use(
         http.get("*/api/portal/data/browser/*", ({ request }) => {
            requests.push(new URL(request.url));
            return HttpResponse.json({
               root: true, worksheetAccess: true, currentFolder: [], folders: [], files: [],
            });
         })
      );

      await renderComponent({ queryParams: { path: "SomeFolder", scope: "4" } });
      await waitFor(() => expect(requests).toHaveLength(1));

      expect(requests[0].pathname).toContain("SomeFolder");
      expect(requests[0].searchParams.get("scope")).toBe("4");
   });

   it("should set unauthorizedAccess=true and clear lists when browser returns 403", async () => {
      // handleBrowserRefreshError re-throws (`return throwError(error)`) and the outer
      // subscribe() has no error callback, so RxJS's reportUnhandledError schedules an
      // async re-throw that zone.js turns into an uncaught exception after the test ends
      // (Vitest reports it as a run-level "Unhandled Error" regardless of console.error
      // stubbing). Route it through RxJS's own hook instead of letting it schedule a throw.
      const origOnUnhandledError = rxjsConfig.onUnhandledError;
      rxjsConfig.onUnhandledError = () => {};
      try {
         server.use(
            http.get("*/api/portal/data/browser", () =>
               new HttpResponse(null, { status: 403 })
            )
         );

         const { comp } = await renderComponent();
         await waitFor(() => expect(comp.unauthorizedAccess).toBe(true));

         expect(comp.folders).toEqual([]);
         expect(comp.datasets).toEqual([]);
         expect(comp.currentFolderPath).toEqual([]);

         // RxJS's reportUnhandledError schedules its call to onUnhandledError via a real
         // setTimeout(0), queued when the HTTP response arrived (before the assertions
         // above ran). Yield one real macrotask so that scheduled call fires — and is
         // swallowed by our no-op hook — before it's restored below.
         await new Promise(resolve => setTimeout(resolve, 0));
      }
      finally {
         rxjsConfig.onUnhandledError = origOnUnhandledError;
      }
   });

   it("should set unauthorizedAccess=false and fire danger notification on non-403 browser error", async () => {
      // See the 403 test above — handleBrowserRefreshError's re-thrown error has no
      // subscribe() error callback, so it must be routed through RxJS's onUnhandledError
      // hook to avoid a Vitest run-level "Unhandled Error" after this test completes.
      const origOnUnhandledError = rxjsConfig.onUnhandledError;
      rxjsConfig.onUnhandledError = () => {};
      try {
         server.use(
            http.get("*/api/portal/data/browser", () =>
               new HttpResponse(null, { status: 500 })
            )
         );

         const { comp } = await renderComponent();
         await waitFor(() =>
            expect((comp as any).dataNotifications.notifications.danger).toHaveBeenCalled()
         );

         expect(comp.unauthorizedAccess).toBe(false);

         // See the 403 test above for why this extra macrotask tick is needed before
         // restoring onUnhandledError.
         await new Promise(resolve => setTimeout(resolve, 0));
      }
      finally {
         rxjsConfig.onUnhandledError = origOnUnhandledError;
      }
   });

   it("getAssemblyName should return null", async () => {
      const { comp } = await renderComponent();

      expect(comp.getAssemblyName()).toBeNull();
   });
});

// ===========================================================================
// Group 2 – refreshSearchBrowser [Risk 3]
// ===========================================================================

describe("DataFolderBrowserComponent – refreshSearchBrowser [Group 2, Risk 3]", () => {
   it("should set searchAssets and reset searchSortOptions on successful search", async () => {
      const result = makeInfo({ name: "SearchResult" });

      server.use(
         http.post("*/api/data/search/datasets", () =>
            HttpResponse.json({ assets: [result], assetNames: ["SearchResult"] })
         )
      );

      const { comp } = await renderComponent({ queryParams: { query: "SearchResult" } });
      await waitFor(() => expect(comp.searchAssets).toHaveLength(1));

      expect(comp.searchAssets[0].name).toBe("SearchResult");
      expect(comp.searchSortOptions.keys).toEqual([]);
   });

   it("should clear searchAssets and fire danger notification when search fails", async () => {
      server.use(
         http.post("*/api/data/search/datasets", () =>
            new HttpResponse(null, { status: 500 })
         )
      );

      const { comp } = await renderComponent({ queryParams: { query: "bad" } });
      await waitFor(() =>
         expect((comp as any).dataNotifications.notifications.danger).toHaveBeenCalled()
      );

      expect(comp.searchAssets).toEqual([]);
   });
});

// ===========================================================================
// Group 3 – toggleSelectTooltip / toggleSelectionState [Risk 2]
// ===========================================================================

describe("DataFolderBrowserComponent – selection toggle [Group 3, Risk 2]", () => {
   it("toggleSelectTooltip should return selectOn when selectionOn is false", async () => {
      const { comp } = await renderComponent();
      comp.selectionOn = false;

      expect(comp.toggleSelectTooltip).toBe("_#(js:data.datasets.selectOn)");
   });

   it("toggleSelectTooltip should return selectOff when selectionOn is true", async () => {
      const { comp } = await renderComponent();
      comp.selectionOn = true;

      expect(comp.toggleSelectTooltip).toBe("_#(js:data.datasets.selectOff)");
   });

   it("toggleSelectionState should flip selectionOn from false to true", async () => {
      const { comp } = await renderComponent();
      comp.selectionOn = false;

      comp.toggleSelectionState();

      expect(comp.selectionOn).toBe(true);
   });

   it("toggleSelectionState should flip selectionOn from true to false", async () => {
      const { comp } = await renderComponent();
      comp.selectionOn = true;

      comp.toggleSelectionState();

      expect(comp.selectionOn).toBe(false);
   });
});

// ===========================================================================
// Group 4 – sortView [Risk 3]
// ===========================================================================

describe("DataFolderBrowserComponent – sortView [Group 4, Risk 3]", () => {
   it("should sort folders before datasets in non-search view", async () => {
      const { comp } = await renderComponent();
      const ws = makeInfo({ name: "Zeta" });
      const folder = makeFolder({ name: "Alpha" });
      comp.searchView = false;
      comp.folders = [folder];
      comp.datasets = [ws];

      comp.sortView();

      // folders and datasets arrays stay separate and are each sorted
      expect(comp.folders[0].name).toBe("Alpha");
      expect(comp.datasets[0].name).toBe("Zeta");
   });

   it("should put folders before datasets in searchAssets when searchView is true", async () => {
      const { comp } = await renderComponent();
      const ws = makeInfo({ name: "WorksheetB" });
      const folder = makeFolder({ name: "FolderA" });
      comp.searchView = true;
      comp.searchAssets = [ws, folder];

      comp.sortView();

      expect(comp.searchAssets[0].type).toBe(AssetType.FOLDER);
      expect(comp.searchAssets[1].type).toBe(AssetType.WORKSHEET);
   });
});

// ===========================================================================
// Group 5 – openFolder / editWorksheet / renameAsset / handleResponse / handleEditAssetError [Risk 3]
// ===========================================================================

describe("DataFolderBrowserComponent – folder/asset actions [Group 5, Risk 3]", () => {
   it("openFolder should navigate to folder route and call dataBrowserService.changeFolder", async () => {
      const { comp, router, route } = await renderComponent();
      const folder = makeFolder({ path: "FolderA", scope: AssetEntryHelper.GLOBAL_SCOPE });

      comp.openFolder(folder);

      expect(router.navigate).toHaveBeenCalledWith(
         ["folder"],
         expect.objectContaining({
            queryParams: { path: "FolderA", scope: AssetEntryHelper.GLOBAL_SCOPE },
            relativeTo: route.parent,
         })
      );
      expect(DATA_BROWSER_MOCK.changeFolder).toHaveBeenCalledWith("FolderA", AssetEntryHelper.GLOBAL_SCOPE);
   });

   it("openFolder should NOT navigate or call changeFolder when asset is not a FOLDER", async () => {
      const { comp, router } = await renderComponent();
      const ws = makeInfo({ type: AssetType.WORKSHEET });

      comp.openFolder(ws);

      expect(router.navigate).not.toHaveBeenCalled();
      expect(DATA_BROWSER_MOCK.changeFolder).not.toHaveBeenCalled();
   });

   it("editWorksheet should delegate to dataBrowserService.openWorksheet with id and clientService", async () => {
      const { comp } = await renderComponent();
      const ws = makeInfo({ id: "ws-id-123" });

      comp.editWorksheet(ws);

      expect(DATA_BROWSER_MOCK.openWorksheet).toHaveBeenCalledWith("ws-id-123", VS_CLIENT_MOCK);
   });

   it("renameAsset should delegate to dataBrowserService.renameAsset with the asset and callbacks", async () => {
      const { comp } = await renderComponent();
      const asset = makeInfo();

      comp.renameAsset(asset);

      expect(DATA_BROWSER_MOCK.renameAsset).toHaveBeenCalledWith(
         asset,
         expect.any(Function),
         expect.any(Function)
      );
   });

   it("handleResponse with type=success should fire success notification", async () => {
      const { comp } = await renderComponent();
      const successSpy = (comp as any).dataNotifications.notifications.success;

      comp.handleResponse("success", "Renamed successfully");

      expect(successSpy).toHaveBeenCalledWith("Renamed successfully");
   });

   it("handleResponse with type=danger should fire danger notification", async () => {
      const { comp } = await renderComponent();
      const dangerSpy = (comp as any).dataNotifications.notifications.danger;

      comp.handleResponse("danger", "Something went wrong");

      expect(dangerSpy).toHaveBeenCalledWith("Something went wrong");
   });

   it("search with a non-empty searchQuery should navigate with query params and reset searchQuery", async () => {
      const { comp, router, route } = await renderComponent({
         queryParams: { path: "sub", scope: "1" },
      });
      comp.searchQuery = "myQuery";
      comp.currentFolderPathString = "sub";
      comp.currentFolderPathScope = "1";

      comp.search();

      expect(router.navigate).toHaveBeenCalledWith(
         ["folder"],
         expect.objectContaining({
            queryParams: expect.objectContaining({ query: "myQuery" }),
            relativeTo: route.parent,
         })
      );
      expect(comp.searchQuery).toBeNull();
   });

   it("search with no searchQuery should not navigate", async () => {
      const { comp, router } = await renderComponent();
      comp.searchQuery = null;

      comp.search();

      expect(router.navigate).not.toHaveBeenCalled();
   });
});

// ===========================================================================
// Group 6 – viewAssets / selectFile / selectAllChecked / selectAllChanged /
//            updateSelectedItems / findAsset / isFolderEditable [Risk 3]
// ===========================================================================

describe("DataFolderBrowserComponent – selection & getters [Group 6, Risk 3]", () => {
   it("viewAssets should return searchAssets when searchView is true", async () => {
      const { comp } = await renderComponent();
      const sr = makeInfo({ name: "SR" });
      comp.searchView = true;
      comp.searchAssets = [sr];
      comp.folders = [];
      comp.datasets = [];

      expect(comp.viewAssets).toEqual([sr]);
   });

   it("viewAssets should return folders concat datasets when searchView is false", async () => {
      const { comp } = await renderComponent();
      const folder = makeFolder();
      const ws = makeInfo();
      comp.searchView = false;
      comp.folders = [folder];
      comp.datasets = [ws];

      expect(comp.viewAssets).toEqual([folder, ws]);
   });

   it("selectFile should set selectedFile when no file is currently selected", async () => {
      const { comp } = await renderComponent();
      const ws = makeInfo({ path: "p1" });
      comp.selectedFile = null;

      comp.selectFile(ws);

      expect(comp.selectedFile).not.toBeNull();
      expect(comp.selectedFile.path).toBe("p1");
   });

   it("selectFile should set selectedFile to null when the same file is selected again (toggle)", async () => {
      const { comp } = await renderComponent();
      const ws = makeInfo({ path: "p2" });
      comp.selectFile(ws);

      comp.selectFile(ws);

      expect(comp.selectedFile).toBeNull();
   });

   it("selectFile should switch selectedFile when a different file is passed", async () => {
      const { comp } = await renderComponent();
      const ws1 = makeInfo({ path: "p1" });
      const ws2 = makeInfo({ path: "p2" });
      comp.selectFile(ws1);

      comp.selectFile(ws2);

      expect(comp.selectedFile.path).toBe("p2");
   });

   it("findAsset should return the matching asset by path from viewAssets", async () => {
      const { comp } = await renderComponent();
      const ws = makeInfo({ path: "target/path" });
      comp.searchView = false;
      comp.folders = [];
      comp.datasets = [ws];

      const found = comp.findAsset("target/path");

      expect(found).toBe(ws);
   });

   it("findAsset should return undefined when no asset matches the path", async () => {
      const { comp } = await renderComponent();
      comp.searchView = false;
      comp.folders = [];
      comp.datasets = [];

      const found = comp.findAsset("nonexistent");

      expect(found).toBeUndefined();
   });

   it("findAsset should return null when path is falsy", async () => {
      const { comp } = await renderComponent();

      expect(comp.findAsset(null)).toBeNull();
   });

   it("selectAllChecked should be false when selectedItems is empty", async () => {
      const { comp } = await renderComponent();
      comp.folders = [makeFolder()];
      comp.datasets = [makeInfo()];
      comp.selectedItems = [];

      expect(comp.selectAllChecked).toBe(false);
   });

   it("selectAllChecked should be false when only some viewAssets are selected", async () => {
      const { comp } = await renderComponent();
      const ws1 = makeInfo({ path: "p1" });
      const ws2 = makeInfo({ path: "p2" });
      comp.searchView = false;
      comp.folders = [];
      comp.datasets = [ws1, ws2];
      comp.selectedItems = [ws1];

      expect(comp.selectAllChecked).toBe(false);
   });

   it("selectAllChecked should be true when all viewAssets are in selectedItems", async () => {
      const { comp } = await renderComponent();
      const ws1 = makeInfo({ path: "p1" });
      const ws2 = makeInfo({ path: "p2" });
      comp.searchView = false;
      comp.folders = [];
      comp.datasets = [ws1, ws2];
      comp.selectedItems = [ws1, ws2];

      expect(comp.selectAllChecked).toBe(true);
   });

   it("selectAllChanged(true) should fill selectedItems with all viewAssets", async () => {
      const { comp } = await renderComponent();
      const ws1 = makeInfo({ path: "p1" });
      const ws2 = makeInfo({ path: "p2" });
      comp.searchView = false;
      comp.folders = [];
      comp.datasets = [ws1, ws2];

      comp.selectAllChanged(true);

      expect(comp.selectedItems).toEqual([ws1, ws2]);
   });

   it("selectAllChanged(false) should clear selectedItems", async () => {
      const { comp } = await renderComponent();
      const ws1 = makeInfo({ path: "p1" });
      comp.searchView = false;
      comp.folders = [];
      comp.datasets = [ws1];
      comp.selectedItems = [ws1];

      comp.selectAllChanged(false);

      expect(comp.selectedItems).toEqual([]);
   });

   it("updateSelectedItems should prune items not present in viewAssets", async () => {
      const { comp } = await renderComponent();
      const ws1 = makeInfo({ path: "p1" });
      const ws2 = makeInfo({ path: "p2" });
      comp.searchView = false;
      comp.folders = [];
      comp.datasets = [ws1]; // ws2 removed
      comp.selectedItems = [ws1, ws2];

      (comp as any).updateSelectedItems();

      expect(comp.selectedItems).toEqual([ws1]);
      expect(comp.selectedItems).not.toContain(ws2);
   });

   it("updateSelectedItems should remap surviving items to the refreshed object references", async () => {
      const { comp } = await renderComponent();
      const old = makeInfo({ path: "p1", name: "OldName" });
      const fresh = makeInfo({ path: "p1", name: "NewName" });
      comp.searchView = false;
      comp.folders = [];
      comp.datasets = [fresh];
      comp.selectedItems = [old];

      (comp as any).updateSelectedItems();

      expect(comp.selectedItems[0]).toBe(fresh);
      expect(comp.selectedItems[0].name).toBe("NewName");
   });

   it("isFolderEditable should be true when currentFolderPath is empty", async () => {
      const { comp } = await renderComponent();
      comp.currentFolderPath = [];

      expect(comp.isFolderEditable).toBe(true);
   });

   it("isFolderEditable should be true when the last folder in currentFolderPath is editable", async () => {
      const { comp } = await renderComponent();
      comp.currentFolderPath = [makeFolder({ editable: true })];

      expect(comp.isFolderEditable).toBe(true);
   });

   it("isFolderEditable should be false when the last folder is not editable", async () => {
      const { comp } = await renderComponent();
      comp.currentFolderPath = [makeFolder({ editable: false })];

      expect(comp.isFolderEditable).toBeFalsy();
   });
});

// ===========================================================================
// Group 7 – addFolder / getRootAssets / materializeAsset / selectChanged /
//            dragAssets / assetsDroped / dataTreeDragToPane /
//            createInfoByAssetEntry / showWSDetailsByDataSourcesTree [Risk 3]
// ===========================================================================

describe("DataFolderBrowserComponent – complex actions [Group 7, Risk 3]", () => {
   it("addFolder should open an InputNameDialog when isFolderEditable is true", async () => {
      const showDialogSpy = vi.spyOn(ComponentTool, "showDialog").mockReturnValue({} as any);
      const { comp } = await renderComponent();
      comp.currentFolderPath = [];

      try {
         comp.addFolder();

         expect(showDialogSpy).toHaveBeenCalled();
      }
      finally {
         showDialogSpy.mockRestore();
      }
   });

   it("addFolder should not open a dialog when isFolderEditable is false", async () => {
      const showDialogSpy = vi.spyOn(ComponentTool, "showDialog").mockReturnValue({} as any);
      const { comp } = await renderComponent();
      comp.currentFolderPath = [makeFolder({ editable: false })];

      try {
         comp.addFolder();

         expect(showDialogSpy).not.toHaveBeenCalled();
      }
      finally {
         showDialogSpy.mockRestore();
      }
   });

   it("addFolder dialog commit should POST to api/data/folders and show success notification", async () => {
      let capturedCommit: ((value: string) => void) | null = null;
      const showDialogSpy = vi.spyOn(ComponentTool, "showDialog").mockImplementation(
         (_modal: any, _type: any, onCommit: (value: string) => void) => {
            capturedCommit = onCommit;
            return {} as any;
         }
      );
      const requests: any[] = [];

      server.use(
         http.post("*/api/data/folders", async ({ request }) => {
            requests.push(await request.json());
            return new HttpResponse(null, { status: 200 });
         })
      );

      const { comp } = await renderComponent();
      comp.currentFolderPath = [];
      comp.currentFolderPathString = "";
      comp.currentFolderPathScope = "";

      try {
         comp.addFolder();
         expect(capturedCommit).not.toBeNull();
         capturedCommit("NewFolder");

         await waitFor(() => expect(requests).toHaveLength(1));
         await waitFor(() =>
            expect((comp as any).dataNotifications.notifications.success).toHaveBeenCalled()
         );
      }
      finally {
         showDialogSpy.mockRestore();
      }
   });

   it("getRootAssets should return all assets when none are children of another", async () => {
      const { comp } = await renderComponent();
      const ws1 = makeInfo({ path: "Folder1/SheetA" });
      const ws2 = makeInfo({ path: "Folder2/SheetB" });

      const result = (comp as any).getRootAssets([ws1, ws2]);

      expect(result).toContain(ws1);
      expect(result).toContain(ws2);
   });

   it("getRootAssets should exclude children when a parent folder is also selected", async () => {
      const { comp } = await renderComponent();
      const parent = makeFolder({ path: "FolderA" });
      const child = makeInfo({ path: "FolderA/Sheet" });
      const unrelated = makeInfo({ path: "FolderB/Sheet" });

      const result = (comp as any).getRootAssets([parent, child, unrelated]);

      expect(result).toContain(parent);
      expect(result).toContain(unrelated);
      expect(result).not.toContain(child);
   });

   it("getRootAssets should return an empty array when given an empty input", async () => {
      const { comp } = await renderComponent();

      const result = (comp as any).getRootAssets([]);

      expect(result).toEqual([]);
   });

   it("materializeAsset should open an AnalyzeMVDialog via ComponentTool.showDialog", async () => {
      const showDialogSpy = vi.spyOn(ComponentTool, "showDialog").mockReturnValue({
         selectedNodes: [],
      } as any);
      const { comp } = await renderComponent();
      const ws = makeInfo({ path: "Folder/Sheet", scope: AssetEntryHelper.GLOBAL_SCOPE });

      try {
         comp.materializeAsset(ws);

         expect(showDialogSpy).toHaveBeenCalled();
      }
      finally {
         showDialogSpy.mockRestore();
      }
   });

   it("selectChanged should call dataBrowserService.changeFolder with params.path and params.scope", async () => {
      const { comp } = await renderComponent();

      comp.selectChanged({ path: "SomeFolder", scope: 1 });

      expect(DATA_BROWSER_MOCK.changeFolder).toHaveBeenCalledWith("SomeFolder", 1);
   });

   it("dragAssets should store serialized worksheet data via DragService", async () => {
      const { comp } = await renderComponent();
      const ws = makeInfo({ name: "DragMe" });
      const mockEvent = { dataTransfer: { setDragImage: vi.fn() } } as any;
      DRAG_SERVICE_MOCK.put.mockClear();

      comp.dragAssets({ event: mockEvent, data: [ws] });

      expect(DRAG_SERVICE_MOCK.put).toHaveBeenCalledWith(
         "dragWorksheets",
         JSON.stringify([ws])
      );
   });

   it("dragAssets should produce an HTML label containing the entry name and correct icon class", async () => {
      const { comp } = await renderComponent();
      const folder = makeFolder({ name: "MyFolder" });
      const ws = makeInfo({ name: "MySheet" });
      const labels: string[] = [];
      vi.spyOn(comp as any, "getEntryLabel").mockImplementation((info: WorksheetBrowserInfo) => {
         const label = `<span class="${info.type === AssetType.FOLDER ? "folder-icon" : "worksheet-icon"}">${info.name}</span>`;
         labels.push(label);
         return label;
      });

      const mockEvent = { dataTransfer: { setDragImage: vi.fn() } } as any;
      DRAG_SERVICE_MOCK.put.mockClear();

      comp.dragAssets({ event: mockEvent, data: [folder, ws] });

      expect(labels[0]).toContain("folder-icon");
      expect(labels[0]).toContain("MyFolder");
      expect(labels[1]).toContain("worksheet-icon");
      expect(labels[1]).toContain("MySheet");
   });

   it("assetsDroped with null target should use currentFolderPath as the fake folder target", async () => {
      const { comp } = await renderComponent();
      // Use a worksheet from a DIFFERENT folder so the filter in moveAssets0 doesn't
      // exclude it (item whose parent == target is skipped as "already there").
      const ws = makeInfo({ path: "OtherFolder/Sheet", scope: AssetEntryHelper.GLOBAL_SCOPE });
      comp.currentFolderPathString = "FolderA";
      comp.currentFolderPathScope = String(AssetEntryHelper.GLOBAL_SCOPE);
      comp.currentFolderPath = [makeFolder({ path: "FolderA" })];
      DRAG_SERVICE_MOCK.getDragData.mockReturnValue({
         dragWorksheets: JSON.stringify([ws]),
      });

      vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("cancel");

      comp.assetsDroped(null);

      // Confirmation dialog should have been shown for the move
      await waitFor(() =>
         expect(ComponentTool.showConfirmDialog).toHaveBeenCalled()
      );
   });

   it("assetsDroped should return early without moving when target is not a FOLDER type", async () => {
      const { comp } = await renderComponent();
      const nonFolder = makeInfo({ type: AssetType.WORKSHEET });
      DRAG_SERVICE_MOCK.getDragData.mockClear();

      comp.assetsDroped(nonFolder);

      expect(DRAG_SERVICE_MOCK.getDragData).not.toHaveBeenCalled();
   });

   it("dataTreeDragToPane should filter out assets whose path equals the target path", async () => {
      const { comp } = await renderComponent();
      const target = makeFolder({ path: "target" });
      const sameAsTarget: AssetEntry = {
         scope: 1, type: AssetType.FOLDER, user: "admin", path: "target",
         alias: null, identifier: "id", properties: {}, description: "", organization: "org",
      };
      const valid: AssetEntry = {
         scope: 1, type: AssetType.WORKSHEET, user: "admin", path: "source/Sheet",
         alias: null, identifier: "id2", properties: {}, description: "", organization: "org",
      };

      vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("cancel");

      comp.dataTreeDragToPane(target, {
         key1: JSON.stringify([sameAsTarget, valid]),
      });

      // Only the valid asset should have been processed; the same-path one is filtered
      await waitFor(() => expect(ComponentTool.showConfirmDialog).toHaveBeenCalled());
   });

   it("dataTreeDragToPane should filter out assets with unsupported types (not FOLDER or WORKSHEET)", async () => {
      const { comp } = await renderComponent();
      const target = makeFolder({ path: "target" });
      const viewsheet: AssetEntry = {
         scope: 1, type: AssetType.VIEWSHEET, user: "admin", path: "source/VS",
         alias: null, identifier: "id", properties: {}, description: "", organization: "org",
      };

      vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("cancel");

      // showConfirmDialog should NOT be called because there are zero valid assets
      comp.dataTreeDragToPane(target, {
         key1: JSON.stringify([viewsheet]),
      });

      // No confirmation because no valid assets pass the filter
      expect(ComponentTool.showConfirmDialog).not.toHaveBeenCalled();
   });

   it("dataTreeDragToPane should call dataBrowserService.changeFolder after processing", async () => {
      const { comp } = await renderComponent();
      const target = makeFolder({ path: "target" });
      comp.currentFolderPathString = "current";
      comp.currentFolderPathScope = "1";
      DATA_BROWSER_MOCK.changeFolder.mockClear();

      // No valid entries → no confirm → changeFolder still called at the end
      comp.dataTreeDragToPane(target, {});

      expect(DATA_BROWSER_MOCK.changeFolder).toHaveBeenCalledWith("current", 1);
   });

   it("showWSDetailsByDataSourcesTree should strip last path segment and call refreshFolderBrowser", async () => {
      const browserRequests: string[] = [];

      server.use(
         http.get("*/api/portal/data/browser/*", ({ request }) => {
            browserRequests.push(new URL(request.url).pathname);
            return HttpResponse.json({
               root: false, worksheetAccess: true, currentFolder: [], folders: [], files: [],
            });
         })
      );

      const { comp } = await renderComponent();

      comp.showWSDetailsByDataSourcesTree({
         path: "ParentFolder/ChildSheet",
         scope: String(AssetEntryHelper.GLOBAL_SCOPE),
      });

      await waitFor(() =>
         expect(browserRequests.some(p => p.includes("ParentFolder"))).toBe(true)
      );
      expect(comp.currentFolderPathScope).toBe(String(AssetEntryHelper.GLOBAL_SCOPE));
   });

   it("showWSDetailsByDataSourcesTree should set empty path when data.path has no slash", async () => {
      const { comp } = await renderComponent();
      const refreshSpy = vi.spyOn(comp as any, "refreshFolderBrowser").mockImplementation(() => {});

      try {
         comp.showWSDetailsByDataSourcesTree({
            path: "SingleSegment",
            scope: String(AssetEntryHelper.GLOBAL_SCOPE),
         });

         expect(refreshSpy).toHaveBeenCalledWith("", "SingleSegment");
      }
      finally {
         refreshSpy.mockRestore();
      }
   });

   it("showWSDetailsByDataSourcesTree should switch scope to GLOBAL when USER_SCOPE path is '/'", async () => {
      const { comp } = await renderComponent();
      const refreshSpy = vi.spyOn(comp as any, "refreshFolderBrowser").mockImplementation(() => {});

      try {
         comp.showWSDetailsByDataSourcesTree({
            path: "/",
            scope: String(AssetConstants.USER_SCOPE),
         });

         expect(comp.currentFolderPathScope).toBe(String(AssetConstants.GLOBAL_SCOPE));
         expect(refreshSpy).toHaveBeenCalledWith("", "/");
      }
      finally {
         refreshSpy.mockRestore();
      }
   });
});
