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
 * DataFolderBrowserComponent – Pass 2: Risk
 *
 * Covers async/destructive paths not in Pass 1:
 *   processMessageCommand, addFolderConfig, moveSelectedConfig,
 *   newWorksheetDisabled, newWorksheet, isSelectionDeletable, isSelectionEditable,
 *   moveDisable, deleteSelected, moveAsset, deleteAsset, handleDeleteError,
 *   refreshBrowserContent, clearSearch, moveSelected, moveAssets, moveAssets0
 *
 * KEY contracts:
 *   - processMessageCommand INFO → info notification; other → delegates to processMessageCommand0.
 *   - addFolderConfig/moveSelectedConfig return the expected shape of string constants.
 *   - newWorksheetDisabled: true when !isFolderEditable OR !worksheetAccess.
 *   - newWorksheet: disabled → no call; empty path → fake folder id; has path → last folder's id.
 *   - isSelectionDeletable/isSelectionEditable: any non-deletable/non-editable item → false.
 *   - moveDisable: true when empty selection or not all deletable/editable.
 *   - deleteSelected: empty → no HTTP; has items → removeableStatuses → confirm → removeAll.
 *   - deleteSelected: folder dependencies change the prompt message; cancel aborts removeAll.
 *   - handleDeleteError: status 450 → error.error; other → generic folder/dataset message.
 *   - deleteAsset: delegates to dataBrowserService.deleteAsset.
 *   - refreshBrowserContent: non-search → refreshFolderBrowser only; search → both.
 *   - clearSearch: resets searchQuery and searchView, navigates without query param.
 *   - moveAsset: opens MoveAssetDialogComponent with correct path/scope properties.
 *   - moveSelected: calls moveAssets(this.selectedItems).
 *   - moveAssets: no root assets → no dialog; has root → opens MoveAssetDialogComponent.
 *   - moveAssets with target: calls moveAction directly (no dialog), POSTs to move URIs.
 *   - moveAssets0: filters by type/parent/scope, shows confirm, calls moveAssets per scope.
 */

import { waitFor } from "@testing-library/angular";
import { http, HttpResponse } from "msw";
import { AssetType } from "../../../../../../shared/data/asset-type";
import { AssetEntryHelper } from "../../../common/data/asset-entry-helper";
import { ComponentTool } from "../../../common/util/component-tool";
import { server } from "@test-mocks/server";
import {
   clearMocks,
   DATA_BROWSER_MOCK,
   makeFolder,
   makeInfo,
   renderComponent,
} from "./data-folder-browser.test-helpers";

beforeEach(() => clearMocks());

afterEach(() => {
   vi.restoreAllMocks();
});

// ===========================================================================
// Group 1 – processMessageCommand [Risk 2]
// ===========================================================================

describe("DataFolderBrowserComponent – processMessageCommand [Group 1, Risk 2]", () => {
   it("should call dataNotifications.notifications.info when command type is INFO", async () => {
      const { comp } = await renderComponent();
      const infoSpy = (comp as any).dataNotifications.notifications.info;

      (comp as any).processMessageCommand({ message: "Hello", type: "INFO" });

      expect(infoSpy).toHaveBeenCalledWith("Hello");
   });

   it("should NOT call info notification when command type is not INFO", async () => {
      const { comp } = await renderComponent();
      const infoSpy = (comp as any).dataNotifications.notifications.info;

      // non-INFO type delegates to processMessageCommand0 (base class)
      (comp as any).processMessageCommand({ message: "Something", type: "CONFIRM" });

      expect(infoSpy).not.toHaveBeenCalled();
   });
});

// ===========================================================================
// Group 2 – addFolderConfig / moveSelectedConfig [Risk 1]
// ===========================================================================

describe("DataFolderBrowserComponent – config getters [Group 2, Risk 1]", () => {
   it("addFolderConfig should return expected string constants", async () => {
      const { comp } = await renderComponent();
      const config = comp.addFolderConfig;

      expect(config.addFolderSuccess).toBe("_#(js:data.datasets.addFolderSuccess)");
      expect(config.addFolderError).toBe("_#(js:data.datasets.addFolderError)");
      expect(config.newfolder).toBe("_#(js:New Folder)");
      expect(config.folderName).toBe("_#(js:Folder Name)");
      expect(config.folderNameRequired).toBe("_#(js:data.datasets.folderNameRequired)");
   });

   it("moveSelectedConfig should return correct assetType and URIs", async () => {
      const { comp } = await renderComponent();
      const config = comp.moveSelectedConfig;

      expect(config.assetType).toBe(AssetType.WORKSHEET);
      expect(config.moveFoldersURI).toBe("../api/data/folders/moveFolders");
      expect(config.moveAssetsURI).toBe("../api/data/datasets/moveDatasets");
      expect(config.moveItemsSuccess).toBe("_#(js:data.datasets.moveItemsSuccess)");
   });
});

// ===========================================================================
// Group 3 – newWorksheetDisabled / newWorksheet [Risk 2]
// ===========================================================================

describe("DataFolderBrowserComponent – newWorksheetDisabled / newWorksheet [Group 3, Risk 2]", () => {
   it("newWorksheetDisabled should be true when worksheetAccess is false", async () => {
      const { comp } = await renderComponent();
      comp.worksheetAccess = false;
      comp.currentFolderPath = [];

      expect(comp.newWorksheetDisabled).toBe(true);
   });

   it("newWorksheetDisabled should be true when current folder is not editable", async () => {
      const { comp } = await renderComponent();
      comp.worksheetAccess = true;
      comp.currentFolderPath = [makeFolder({ editable: false })];

      expect(comp.newWorksheetDisabled).toBe(true);
   });

   it("newWorksheetDisabled should be false when worksheetAccess=true and isFolderEditable=true", async () => {
      const { comp } = await renderComponent();
      comp.worksheetAccess = true;
      comp.currentFolderPath = [];

      expect(comp.newWorksheetDisabled).toBe(false);
   });

   it("newWorksheet should not call dataBrowserService when disabled", async () => {
      const { comp } = await renderComponent();
      comp.worksheetAccess = false;

      comp.newWorksheet();

      expect(DATA_BROWSER_MOCK.newWorksheet).not.toHaveBeenCalled();
   });

   it("newWorksheet should call dataBrowserService.newWorksheet with fake id when currentFolderPath is empty", async () => {
      const { comp } = await renderComponent();
      comp.worksheetAccess = true;
      comp.currentFolderPath = [];
      comp.currentFolderPathString = "myFolder";

      comp.newWorksheet();

      expect(DATA_BROWSER_MOCK.newWorksheet).toHaveBeenCalledWith("1^1^__NULL__^myFolder");
   });

   it("newWorksheet should call dataBrowserService.newWorksheet with last folder id when currentFolderPath is set", async () => {
      const { comp } = await renderComponent();
      comp.worksheetAccess = true;
      comp.currentFolderPath = [makeFolder({ id: "folder-id-123" })];

      comp.newWorksheet();

      expect(DATA_BROWSER_MOCK.newWorksheet).toHaveBeenCalledWith("folder-id-123");
   });
});

// ===========================================================================
// Group 4 – isSelectionDeletable / isSelectionEditable / moveDisable [Risk 2]
// ===========================================================================

describe("DataFolderBrowserComponent – isSelectionDeletable / isSelectionEditable / moveDisable [Group 4, Risk 2]", () => {
   it("isSelectionDeletable should return true when all items are deletable", async () => {
      const { comp } = await renderComponent();
      comp.selectedItems = [makeInfo({ deletable: true }), makeInfo({ path: "p2", deletable: true })];

      expect(comp.isSelectionDeletable()).toBe(true);
   });

   it("isSelectionDeletable should return false when any item is not deletable", async () => {
      const { comp } = await renderComponent();
      comp.selectedItems = [makeInfo({ deletable: true }), makeInfo({ path: "p2", deletable: false })];

      expect(comp.isSelectionDeletable()).toBe(false);
   });

   it("isSelectionEditable should return true when all items are editable", async () => {
      const { comp } = await renderComponent();
      comp.selectedItems = [makeInfo({ editable: true })];

      expect(comp.isSelectionEditable()).toBe(true);
   });

   it("isSelectionEditable should return false when any item is not editable", async () => {
      const { comp } = await renderComponent();
      comp.selectedItems = [makeInfo({ editable: true }), makeInfo({ path: "p2", editable: false })];

      expect(comp.isSelectionEditable()).toBe(false);
   });

   it("moveDisable should be true when selectedItems is empty", async () => {
      const { comp } = await renderComponent();
      comp.selectedItems = [];

      expect(comp.moveDisable).toBe(true);
   });

   it("moveDisable should be false when selectedItems has deletable and editable items", async () => {
      const { comp } = await renderComponent();
      comp.selectedItems = [makeInfo({ deletable: true, editable: true })];

      expect(comp.moveDisable).toBe(false);
   });

   it("moveDisable should be true when selectedItems has items that are not deletable", async () => {
      const { comp } = await renderComponent();
      comp.selectedItems = [makeInfo({ deletable: false, editable: true })];

      expect(comp.moveDisable).toBe(true);
   });

   it("moveDisable should be true when selectedItems has items that are not editable", async () => {
      const { comp } = await renderComponent();
      comp.selectedItems = [makeInfo({ deletable: true, editable: false })];

      expect(comp.moveDisable).toBe(true);
   });
});

// ===========================================================================
// Group 5 – deleteSelected [Risk 3]
// ===========================================================================

describe("DataFolderBrowserComponent – deleteSelected [Group 5, Risk 3]", () => {
   it("should not call removeableStatuses when selectedItems is empty", async () => {
      const requests: any[] = [];
      server.use(
         http.post("*/api/data/removeableStatuses", async ({ request }) => {
            requests.push(await request.json());
            return HttpResponse.json({ folderDependencies: [], datasetDependencies: [] });
         })
      );
      const { comp } = await renderComponent();
      comp.selectedItems = [];

      comp.deleteSelected();

      expect(requests).toHaveLength(0);
   });

   it("should POST to removeableStatuses when items are selected and show confirm dialog", async () => {
      server.use(
         http.post("*/api/data/removeableStatuses", () =>
            HttpResponse.json({ folderDependencies: [], datasetDependencies: [] })
         )
      );
      const confirmSpy = vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("cancel");
      try {
         const { comp } = await renderComponent();
         comp.selectedItems = [makeInfo()];

         comp.deleteSelected();

         await waitFor(() => expect(confirmSpy).toHaveBeenCalled());
      }
      finally {
         confirmSpy.mockRestore();
      }
   });

   it("should POST to removeAll and show success notification when user confirms", async () => {
      server.use(
         http.post("*/api/data/removeableStatuses", () =>
            HttpResponse.json({ folderDependencies: [], datasetDependencies: [] })
         ),
         http.post("*/api/data/removeAll", () => new HttpResponse(null, { status: 200 })),
         http.get("*/api/portal/data/browser", () =>
            HttpResponse.json({ root: true, worksheetAccess: true, currentFolder: [], folders: [], files: [] })
         )
      );
      const confirmSpy = vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("ok");
      try {
         const { comp } = await renderComponent();
         comp.selectedItems = [makeInfo()];
         const successSpy = (comp as any).dataNotifications.notifications.success;

         comp.deleteSelected();

         await waitFor(() => expect(successSpy).toHaveBeenCalledWith("_#(js:data.datasets.deleteItemsSuccess)"));
      }
      finally {
         confirmSpy.mockRestore();
      }
   });

   it("should use folder-specific prompt when folderDependencies are present", async () => {
      server.use(
         http.post("*/api/data/removeableStatuses", () =>
            HttpResponse.json({ folderDependencies: ["dep1"], datasetDependencies: [] })
         )
      );
      const confirmSpy = vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("cancel");
      try {
         const { comp } = await renderComponent();
         comp.selectedItems = [makeFolder()];

         comp.deleteSelected();

         await waitFor(() => expect(confirmSpy).toHaveBeenCalled());
         expect(confirmSpy.mock.calls[0][2]).toContain("_#(js:data.datasets.deleteFoldersDependencyError)");
      }
      finally {
         confirmSpy.mockRestore();
      }
   });

   it("should show danger notification when removeableStatuses request fails", async () => {
      server.use(
         http.post("*/api/data/removeableStatuses", () => new HttpResponse(null, { status: 500 }))
      );
      const { comp } = await renderComponent();
      comp.selectedItems = [makeInfo()];
      const dangerSpy = (comp as any).dataNotifications.notifications.danger;

      comp.deleteSelected();

      await waitFor(() => expect(dangerSpy).toHaveBeenCalledWith("_#(js:data.datasets.deleteItemsError)"));
   });
});

// ===========================================================================
// Group 6 – handleDeleteError / deleteAsset [Risk 2]
// ===========================================================================

describe("DataFolderBrowserComponent – handleDeleteError / deleteAsset [Group 6, Risk 2]", () => {
   it("handleDeleteError with status 450 should show error.error as danger notification", async () => {
      const { comp } = await renderComponent();
      const dangerSpy = (comp as any).dataNotifications.notifications.danger;
      const error = { status: 450, error: "Custom 450 message" } as any;

      try {
         (comp as any).handleDeleteError(error, false).subscribe({ error: () => {} });
      }
      catch { /* throwError may throw synchronously */ }

      expect(dangerSpy).toHaveBeenCalledWith("Custom 450 message");
   });

   it("handleDeleteError with non-450 status and isFolder=true should show folder error", async () => {
      const { comp } = await renderComponent();
      const dangerSpy = (comp as any).dataNotifications.notifications.danger;
      const error = { status: 500, error: "Server error" } as any;

      try {
         (comp as any).handleDeleteError(error, true).subscribe({ error: () => {} });
      }
      catch { /* throwError may throw synchronously */ }

      expect(dangerSpy).toHaveBeenCalledWith("_#(js:data.datasets.deleteFolderError)");
   });

   it("deleteAsset should delegate to dataBrowserService.deleteAsset", async () => {
      const { comp } = await renderComponent();
      const asset = makeInfo();

      comp.deleteAsset(asset);

      expect(DATA_BROWSER_MOCK.deleteAsset).toHaveBeenCalledWith(
         asset,
         expect.any(Function),
         expect.any(Function)
      );
   });
});

// ===========================================================================
// Group 7 – refreshBrowserContent [Risk 2]
// ===========================================================================

describe("DataFolderBrowserComponent – refreshBrowserContent [Group 7, Risk 2]", () => {
   it("should only call refreshFolderBrowser when searchView is false", async () => {
      const { comp } = await renderComponent();
      comp.searchView = false;
      const folderSpy = vi.spyOn(comp as any, "refreshFolderBrowser").mockImplementation(() => {});
      const searchSpy = vi.spyOn(comp as any, "refreshSearchBrowser").mockImplementation(() => {});
      try {
         (comp as any).refreshBrowserContent("myPath");

         expect(folderSpy).toHaveBeenCalledWith("myPath");
         expect(searchSpy).not.toHaveBeenCalled();
      }
      finally {
         folderSpy.mockRestore();
         searchSpy.mockRestore();
      }
   });

   it("should call both refreshFolderBrowser and refreshSearchBrowser when searchView is true", async () => {
      const { comp } = await renderComponent({ queryParams: { query: "test" } });
      // searchView is set by ngOnInit based on route params; set it directly for this test
      comp.searchView = true;
      comp.currentSearchQuery = "myQuery";
      const folderSpy = vi.spyOn(comp as any, "refreshFolderBrowser").mockImplementation(() => {});
      const searchSpy = vi.spyOn(comp as any, "refreshSearchBrowser").mockImplementation(() => {});
      try {
         (comp as any).refreshBrowserContent("myPath");

         expect(folderSpy).toHaveBeenCalledWith("myPath");
         expect(searchSpy).toHaveBeenCalled();
      }
      finally {
         folderSpy.mockRestore();
         searchSpy.mockRestore();
      }
   });
});

// ===========================================================================
// Group 8 – clearSearch [Risk 2]
// ===========================================================================

describe("DataFolderBrowserComponent – clearSearch [Group 8, Risk 2]", () => {
   it("should reset searchQuery to null and searchView to false", async () => {
      const { comp } = await renderComponent();
      comp.searchQuery = "someQuery";
      comp.searchView = true;

      comp.clearSearch();

      expect(comp.searchQuery).toBeNull();
      expect(comp.searchView).toBe(false);
   });

   it("should navigate without query param when currentFolderPathString is empty", async () => {
      const { comp, router, route } = await renderComponent();
      comp.currentFolderPathString = "";
      comp.currentFolderPathScope = "1";

      comp.clearSearch();

      expect(router.navigate).toHaveBeenCalledWith(
         ["folder"],
         expect.objectContaining({
            queryParams: { scope: "1" },
            relativeTo: route.parent,
         })
      );
   });

   it("should navigate with path and scope when currentFolderPathString is set", async () => {
      const { comp, router, route } = await renderComponent();
      comp.currentFolderPathString = "FolderA";
      comp.currentFolderPathScope = "1";

      comp.clearSearch();

      expect(router.navigate).toHaveBeenCalledWith(
         ["folder"],
         expect.objectContaining({
            queryParams: { path: "FolderA", scope: "1" },
            relativeTo: route.parent,
         })
      );
   });
});

// ===========================================================================
// Group 9 – moveAsset [Risk 3]
// ===========================================================================

describe("DataFolderBrowserComponent – moveAsset [Group 9, Risk 3]", () => {
   it("should open MoveAssetDialogComponent via ComponentTool.showDialog", async () => {
      const capturedProps: any = {};
      const showDialogSpy = vi.spyOn(ComponentTool, "showDialog").mockImplementation(
         (_modal: any, _type: any, _onCommit: any) => {
            return capturedProps as any;
         }
      );
      const { comp } = await renderComponent();
      comp.currentFolderPath = [];
      comp.currentFolderPathString = "parentFolder";
      comp.currentFolderPathScope = String(AssetEntryHelper.GLOBAL_SCOPE);
      const asset = makeInfo({ path: "parentFolder/Sheet", scope: AssetEntryHelper.GLOBAL_SCOPE });

      try {
         comp.moveAsset(asset);
         expect(showDialogSpy).toHaveBeenCalled();
         expect(capturedProps.originalPaths).toEqual(["parentFolder/Sheet"]);
         expect(capturedProps.items).toEqual([asset]);
         expect(capturedProps.parentPath).toBe("parentFolder");
         expect(capturedProps.parentScope).toBe(AssetEntryHelper.GLOBAL_SCOPE);
      }
      finally {
         showDialogSpy.mockRestore();
      }
   });

   it("moveAsset commit should POST to move URI and show success notification", async () => {
      let capturedCommit: ((result: [string, number]) => void) | null = null;
      const showDialogSpy = vi.spyOn(ComponentTool, "showDialog").mockImplementation(
         (_modal: any, _type: any, onCommit: (result: [string, number]) => void) => {
            capturedCommit = onCommit;
            return {} as any;
         }
      );
      server.use(
         http.post("*/api/data/datasets/move", () => new HttpResponse(null, { status: 200 })),
         http.get("*/api/portal/data/browser", () =>
            HttpResponse.json({ root: true, worksheetAccess: true, currentFolder: [], folders: [], files: [] })
         )
      );
      const { comp } = await renderComponent();
      comp.currentFolderPath = [];
      comp.currentFolderPathString = "parentFolder";
      comp.currentFolderPathScope = String(AssetEntryHelper.GLOBAL_SCOPE);
      const asset = makeInfo({ path: "parentFolder/Sheet", scope: AssetEntryHelper.GLOBAL_SCOPE });
      const successSpy = (comp as any).dataNotifications.notifications.success;

      try {
         comp.moveAsset(asset);
         expect(capturedCommit).not.toBeNull();
         capturedCommit(["targetFolder", AssetEntryHelper.GLOBAL_SCOPE]);

         await waitFor(() => expect(successSpy).toHaveBeenCalled());
      }
      finally {
         showDialogSpy.mockRestore();
      }
   });
});

// ===========================================================================
// Group 10 – moveSelected / moveAssets / moveAssets0 [Risk 3]
// ===========================================================================

describe("DataFolderBrowserComponent – moveSelected / moveAssets / moveAssets0 [Group 10, Risk 3]", () => {
   it("moveSelected should call moveAssets with the current selectedItems", async () => {
      const { comp } = await renderComponent();
      const ws = makeInfo();
      comp.selectedItems = [ws];
      const moveAssetsSpy = vi.spyOn(comp, "moveAssets").mockImplementation(() => {});

      comp.moveSelected();

      expect(moveAssetsSpy).toHaveBeenCalledWith([ws]);
   });

   it("moveAssets should not open a dialog when no root assets remain after filtering", async () => {
      const showDialogSpy = vi.spyOn(ComponentTool, "showDialog").mockReturnValue({} as any);
      const { comp } = await renderComponent();
      // empty assets → getRootAssets returns []
      comp.currentFolderPath = [];
      comp.currentFolderPathString = "";

      try {
         comp.moveAssets([]);
         expect(showDialogSpy).not.toHaveBeenCalled();
      }
      finally {
         showDialogSpy.mockRestore();
      }
   });

   it("moveAssets without target should open MoveAssetDialogComponent", async () => {
      const capturedProps: any = {};
      const showDialogSpy = vi.spyOn(ComponentTool, "showDialog").mockImplementation(
         (_modal: any, _type: any, _onCommit: any) => {
            return capturedProps as any;
         }
      );
      const { comp } = await renderComponent();
      const ws = makeInfo({ path: "old/Sheet", scope: AssetEntryHelper.GLOBAL_SCOPE });
      // currentFolderPath must be non-empty: moveAssets accesses currentFolderPath[length-2]
      // which crashes when length===0. With length===1 it falls back to FAKE_ROOT_PATH.
      comp.currentFolderPath = [makeFolder({ path: "old" })];
      comp.currentFolderPathString = "old";
      comp.currentFolderPathScope = String(AssetEntryHelper.GLOBAL_SCOPE);

      try {
         comp.moveAssets([ws]);
         expect(showDialogSpy).toHaveBeenCalled();
         expect(capturedProps.multi).toBe(true);
         expect(capturedProps.items).toContain(ws);
         expect(capturedProps.parentPath).toBe("old");
      }
      finally {
         showDialogSpy.mockRestore();
      }
   });

   it("moveAssets with target should POST to moveDatasets and show success notification", async () => {
      server.use(
         http.post("*/api/data/datasets/moveDatasets", () => new HttpResponse(null, { status: 200 })),
         http.get("*/api/portal/data/browser", () =>
            HttpResponse.json({ root: true, worksheetAccess: true, currentFolder: [], folders: [], files: [] })
         )
      );
      const { comp } = await renderComponent();
      const ws = makeInfo({ path: "oldFolder/Sheet", scope: AssetEntryHelper.GLOBAL_SCOPE });
      const target = makeFolder({ path: "newFolder", scope: AssetEntryHelper.GLOBAL_SCOPE });
      // currentFolderPath must be non-empty (same reason as the no-target test above).
      comp.currentFolderPath = [makeFolder({ path: "oldFolder" })];
      comp.currentFolderPathString = "oldFolder";
      comp.currentFolderPathScope = String(AssetEntryHelper.GLOBAL_SCOPE);
      const successSpy = (comp as any).dataNotifications.notifications.success;

      comp.moveAssets([ws], target);

      await waitFor(() => expect(successSpy).toHaveBeenCalledWith("_#(js:data.datasets.moveItemsSuccess)"));
   });

   it("moveAssets0 should show confirm dialog when assets pass the filter", async () => {
      const confirmSpy = vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("cancel");
      try {
         const { comp } = await renderComponent();
         // asset in different parent folder → passes filter
         const target = makeFolder({ path: "newFolder", scope: AssetEntryHelper.GLOBAL_SCOPE });
         const asset = makeInfo({ path: "oldFolder/Sheet", scope: AssetEntryHelper.GLOBAL_SCOPE });

         (comp as any).moveAssets0([asset], target);

         await waitFor(() => expect(confirmSpy).toHaveBeenCalled());
      }
      finally {
         confirmSpy.mockRestore();
      }
   });

   it("moveAssets0 should filter out assets already in the target folder", async () => {
      const confirmSpy = vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("cancel");
      try {
         const { comp } = await renderComponent();
         // asset whose parent IS the target → filtered out → no confirm dialog
         const target = makeFolder({ path: "targetFolder", scope: AssetEntryHelper.GLOBAL_SCOPE });
         const asset = makeInfo({ path: "targetFolder/Sheet", scope: AssetEntryHelper.GLOBAL_SCOPE });

         (comp as any).moveAssets0([asset], target);

         expect(confirmSpy).not.toHaveBeenCalled();
      }
      finally {
         confirmSpy.mockRestore();
      }
   });
});
