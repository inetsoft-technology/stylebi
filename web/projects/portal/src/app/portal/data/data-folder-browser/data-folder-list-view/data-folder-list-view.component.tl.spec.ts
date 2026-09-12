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
 * DataFolderListViewComponent - single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 2] - assets setter and sort updates: keep-selection handoff, key reset,
 *                      ASC/DESC toggle, sortChanged emit
 *   Group 2 [Risk 2] - open/edit/menu flow: worksheet edit branch, open branch, menu-click
 *                      guard, clickMenu latch reset
 *   Group 3 [Risk 2] - drag/drop contracts: selectionOn vs multiObjectSelectList source,
 *                      folder-only drop target, root drop emit
 *   Group 4 [Risk 1] - selection helpers: updateSelection toggle, updateAssetSelection
 *                      delegation, isSelectedItem guard
 *   Group 5 [Risk 1] - pure display helpers: move guard, parent router params, icon mapping,
 *                      date label delegation
 *
 * Confirmed bugs (it.fails): none
 *
 * Out of scope:
 *   Template CSS/layout combinations and ngb dropdown rendering details.
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { provideRouter } from "@angular/router";
import { render } from "@testing-library/angular";

import { AssetType } from "../../../../../../../shared/data/asset-type";
import { DateTypeFormatter } from "../../../../../../../shared/util/date-type-formatter";
import { SortOptions } from "../../../../../../../shared/util/sort/sort-options";
import { SortTypes } from "../../../../../../../shared/util/sort/sort-types";
import { AssetConstants } from "../../../../common/data/asset-constants";
import { AssetEntryHelper } from "../../../../common/data/asset-entry-helper";
import { WorksheetBrowserInfo } from "../../model/worksheet-browser-info";
import { DataFolderListViewComponent } from "./data-folder-list-view.component";

function makeSortOptions(
   keys: string[] = [],
   type: SortTypes = SortTypes.ASCENDING,
): SortOptions {
   return { keys, type } as SortOptions;
}

function makeAsset(overrides: Partial<WorksheetBrowserInfo> = {}): WorksheetBrowserInfo {
   return {
      name: "Asset",
      path: "/Asset",
      parentPath: "/",
      scope: AssetEntryHelper.GLOBAL_SCOPE,
      type: AssetType.WORKSHEET,
      editable: true,
      deletable: true,
      canWorksheet: true,
      canMaterialize: false,
      materialized: false,
      workSheetType: "",
      createdBy: "admin",
      modifiedDate: 1710000000000,
      dateFormat: "yyyy-MM-dd",
      ...overrides,
   } as WorksheetBrowserInfo;
}

interface RenderOpts {
   assets?: WorksheetBrowserInfo[];
   sortOptions?: SortOptions;
   selectionOn?: boolean;
   selectedItems?: WorksheetBrowserInfo[];
   folderPathLength?: number;
   unauthorizedAccess?: boolean;
}

async function renderComp(opts: RenderOpts = {}) {
   const { fixture } = await render(DataFolderListViewComponent, {
      providers: [provideRouter([])],
      schemas: [NO_ERRORS_SCHEMA],
      componentInputs: {
         assets: opts.assets ?? [],
         sortOptions: opts.sortOptions ?? makeSortOptions(),
         selectionOn: opts.selectionOn ?? false,
         selectedItems: opts.selectedItems ?? [],
         folderPathLength: opts.folderPathLength ?? 0,
         unauthorizedAccess: opts.unauthorizedAccess ?? false,
      },
   });

   return fixture.componentInstance as DataFolderListViewComponent;
}

afterEach(() => {
   vi.restoreAllMocks();
});

describe("Group 1 - assets setter and sort updates", () => {
   it("should pass the new asset list to multiObjectSelectList when assets input changes", async () => {
      const comp = await renderComp();
      const replacement = [makeAsset({ name: "A" }), makeAsset({ name: "B", path: "/B" })];
      const keepSelectionSpy = vi.spyOn(
         (comp as any).multiObjectSelectList,
         "setObjectsKeepSelection",
      );

      comp.assets = replacement;

      expect(keepSelectionSpy).toHaveBeenCalledWith(replacement);
      expect(comp.assets).toEqual(replacement);
   });

   it("should toggle ASCENDING to DESCENDING when updating the active sort key", async () => {
      const comp = await renderComp({
         sortOptions: makeSortOptions(["name"], SortTypes.ASCENDING),
      });
      const emitSpy = vi.spyOn(comp.sortChanged, "emit");

      comp.updateSortOptions("name");

      expect(comp.sortOptions.type).toBe(SortTypes.DESCENDING);
      expect(emitSpy).toHaveBeenCalledOnce();
   });

   it("should reset sort to the new key and ASCENDING when the key changes", async () => {
      const comp = await renderComp({
         sortOptions: makeSortOptions(["modifiedDate"], SortTypes.DESCENDING),
      });

      comp.updateSortOptions("createdBy");

      expect(comp.sortOptions.keys).toEqual(["createdBy"]);
      expect(comp.sortOptions.type).toBe(SortTypes.ASCENDING);
   });
});

describe("Group 2 - open/edit/menu flow", () => {
   it("should emit editWorksheet for editable worksheet assets", async () => {
      const comp = await renderComp();
      const asset = makeAsset({ type: AssetType.WORKSHEET, editable: true });
      const editSpy = vi.spyOn(comp.editWorksheet, "emit");
      const openSpy = vi.spyOn(comp.openAsset, "emit");

      comp.selectAsset(asset, {});

      expect(editSpy).toHaveBeenCalledWith(asset);
      expect(openSpy).not.toHaveBeenCalled();
   });

   it("should emit openAsset for non-editable worksheets and folders", async () => {
      const comp = await renderComp();
      const nonEditableSheet = makeAsset({ editable: false });
      const folder = makeAsset({ type: AssetType.FOLDER, path: "/Folder" });
      const openSpy = vi.spyOn(comp.openAsset, "emit");

      comp.selectAsset(nonEditableSheet, {});
      comp.selectAsset(folder, {});

      expect(openSpy).toHaveBeenNthCalledWith(1, nonEditableSheet);
      expect(openSpy).toHaveBeenNthCalledWith(2, folder);
   });

   it("should swallow one selectAsset click after clickMenu sets the menu latch", async () => {
      const comp = await renderComp();
      const asset = makeAsset();
      const openSpy = vi.spyOn(comp.openAsset, "emit");

      comp.clickMenu();
      comp.selectAsset(asset, {});

      expect(openSpy).not.toHaveBeenCalled();
      expect(comp.isMenuClick).toBe(false);
   });
});

describe("Group 3 - drag/drop contracts", () => {
   it("should drag selectedItems when selectionOn is true and the asset is already selected", async () => {
      const assetA = makeAsset({ name: "A", path: "/A" });
      const assetB = makeAsset({ name: "B", path: "/B" });
      const comp = await renderComp({
         selectionOn: true,
         selectedItems: [assetA, assetB],
      });
      const emitSpy = vi.spyOn(comp.dragAssets, "emit");

      comp.dragAsset("event", assetA);

      expect(emitSpy).toHaveBeenCalledWith({
         event: "event",
         data: [assetA, assetB],
      });
   });

   it("should drag the clicked asset only when selectionOn is false and private selection misses it", async () => {
      const assetA = makeAsset({ name: "A", path: "/A" });
      const assetB = makeAsset({ name: "B", path: "/B" });
      const comp = await renderComp();
      const emitSpy = vi.spyOn(comp.dragAssets, "emit");
      vi.spyOn((comp as any).multiObjectSelectList, "getSelectedObjects").mockReturnValue([assetA]);

      comp.dragAsset("event", assetB);

      expect(emitSpy).toHaveBeenCalledWith({
         event: "event",
         data: [assetB],
      });
   });

   it("should stop propagation and emit the folder target on a valid drop", async () => {
      const comp = await renderComp();
      const folder = makeAsset({ type: AssetType.FOLDER, path: "/Folder" });
      const stopPropagation = vi.fn();
      const emitSpy = vi.spyOn(comp.assetsDroped, "emit");

      comp.dropAssets(folder, { stopPropagation } as unknown as DragEvent);

      expect(stopPropagation).toHaveBeenCalledOnce();
      expect(emitSpy).toHaveBeenCalledWith(folder);
   });

   it("should ignore drops on non-folder assets", async () => {
      const comp = await renderComp();
      const sheet = makeAsset({ type: AssetType.WORKSHEET });
      const stopPropagation = vi.fn();
      const emitSpy = vi.spyOn(comp.assetsDroped, "emit");

      comp.dropAssets(sheet, { stopPropagation } as unknown as DragEvent);

      expect(stopPropagation).not.toHaveBeenCalled();
      expect(emitSpy).not.toHaveBeenCalled();
   });
});

describe("Group 4 - selection helpers", () => {
   it("should remove an already selected item and add a missing one", async () => {
      const assetA = makeAsset({ name: "A", path: "/A" });
      const assetB = makeAsset({ name: "B", path: "/B" });
      const comp = await renderComp({ selectedItems: [assetA] });

      comp.updateSelection(assetA);
      expect(comp.selectedItems).toEqual([]);

      comp.updateSelection(assetB);
      expect(comp.selectedItems).toEqual([assetB]);
   });

   it("should delegate updateAssetSelection to multiObjectSelectList only when selectionOn is false", async () => {
      const comp = await renderComp({ selectionOn: false });
      const asset = makeAsset();
      const selectSpy = vi.spyOn(
         (comp as any).multiObjectSelectList,
         "selectWithEvent",
      ).mockImplementation(() => {});
      const event = new MouseEvent("click");

      comp.updateAssetSelection(asset, event);

      expect(selectSpy).toHaveBeenCalledWith(asset, event);
   });

   it("should return false from isSelectedItem when selectionOn is true", async () => {
      const comp = await renderComp({ selectionOn: true });
      vi.spyOn((comp as any).multiObjectSelectList, "isSelected").mockReturnValue(true);

      expect(comp.isSelectedItem(makeAsset())).toBe(false);
   });
});

describe("Group 5 - pure display helpers", () => {
   it("should only allow moving editable and deletable assets when the folder path is not root", async () => {
      const movable = makeAsset({ editable: true, deletable: true });
      const locked = makeAsset({ editable: false, deletable: true });
      const comp = await renderComp({ folderPathLength: 2 });

      expect(comp.canMoveAsset(movable)).toBe(true);
      expect(comp.canMoveAsset(locked)).toBe(false);
   });

   it("should strip the private worksheet prefix from parent router params", async () => {
      const comp = await renderComp();
      const params = comp.getParentRouterLinkParams(
         "_#(js:User Worksheet)/Reports",
         AssetEntryHelper.USER_SCOPE,
      );

      expect(params).toEqual({ path: "Reports", scope: String(AssetEntryHelper.USER_SCOPE) });
   });

   it("should return scope-only router params when parentPath is empty", async () => {
      const comp = await renderComp();

      expect(comp.getParentRouterLinkParams("", AssetEntryHelper.GLOBAL_SCOPE)).toEqual({
         scope: AssetEntryHelper.GLOBAL_SCOPE,
      });
   });

   it("should map materialized, folder, grouped, variable, condition, date-range, and default icons", async () => {
      const comp = await renderComp();

      expect(comp.getIcon(makeAsset({ materialized: true }))).toBe("materialized-worksheet-icon");
      expect(comp.getIcon(makeAsset({ type: AssetType.FOLDER }))).toBe("folder-icon");
      expect(comp.getIcon(makeAsset({ workSheetType: AssetConstants.NAMED_GROUP_ASSET }))).toBe(" grouping-icon");
      expect(comp.getIcon(makeAsset({ workSheetType: AssetConstants.VARIABLE_ASSET }))).toBe(" variable-icon");
      expect(comp.getIcon(makeAsset({ workSheetType: AssetConstants.CONDITION_ASSET }))).toBe(" condition-icon");
      expect(comp.getIcon(makeAsset({ workSheetType: AssetConstants.DATE_RANGE_ASSET }))).toBe(" date-range-icon");
      expect(comp.getIcon(makeAsset({ workSheetType: -1 }))).toBe(" worksheet-icon");
   });

   it("should delegate date formatting to DateTypeFormatter.getLocalTime", async () => {
      const comp = await renderComp();
      const formatterSpy = vi.spyOn(DateTypeFormatter, "getLocalTime").mockReturnValue("2026-06-29");

      expect(comp.getDateLabel(1710000000000, "yyyy-MM-dd")).toBe("2026-06-29");
      expect(formatterSpy).toHaveBeenCalledWith(1710000000000, "yyyy-MM-dd");
   });
});
