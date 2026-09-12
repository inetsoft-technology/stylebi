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
 * AssetItemListViewComponent — single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — dragAsset: selectionOn mode uses selectedItems; selectionOn=false uses
 *                       multiObjectSelectList; dragSupportFunc filters the final emit list
 *   Group 2 [Risk 3] — selectAll: toggles all ↔ none; includes fetchChildrenFunc children
 *   Group 3 [Risk 2] — updateSortOptions: toggle ASC↔DESC for existing key;
 *                       reset to [newKey]/ASCENDING for new key; emits sortChanged
 *   Group 4 [Risk 2] — updateSelection: add item when absent; remove via splice when present;
 *                       always emits onSelectedChanged
 *   Group 5 [Risk 2] — selectAllChecked getter: true only when all assets are in selectedItems
 *   Group 6 [Risk 1] — toggleItem / isToggled: add then remove
 *   Group 7 [Risk 1] — getToggleIcon: no fetchChildrenFunc → ""; toggled → caret-down;
 *                       not toggled → caret-right
 *   Group 8 [Risk 1] — hasChildren / getChildren: no func → []; with func → delegates
 *   Group 9 [Risk 1] — clickItem: emit flag controls EventEmitter
 *   Group 10 [Risk 1] — updateAssetSelection / isSelectedItem: early-return guards
 *   Group 11 [Risk 1] — supportDrag: dragSupport + optional dragSupportFunc
 *
 * Out of scope:
 *   assets setter side-effect (multiObjectSelectList.setObjectsKeepSelection) — private
 *     implementation detail; verified transitively via dragAsset selectionOn=false path.
 *   dropAssets — trivially delegates to EventEmitter.emit.
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { provideRouter } from "@angular/router";
import { render } from "@testing-library/angular";

import { AssetItemListViewComponent, ListColumn } from "./asset-item-list-view.component";
import { AssetItem } from "../model/datasources/database/asset-item";
import { SortOptions } from "../../../../../../shared/util/sort/sort-options";
import { SortTypes } from "../../../../../../shared/util/sort/sort-types";

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

function makeAsset(name: string): AssetItem {
   return { name } as unknown as AssetItem;
}

function makeSortOptions(keys: string[] = [], type: SortTypes = SortTypes.ASCENDING): SortOptions {
   return { keys, type } as SortOptions;
}

interface RenderOpts {
   assets?: AssetItem[];
   columns?: ListColumn[];
   sortOptions?: SortOptions;
   selectionOn?: boolean;
   selectedItems?: AssetItem[];
   fetchChildrenFunc?: (item: AssetItem) => AssetItem[];
   dragSupport?: boolean;
   dragSupportFunc?: (item: AssetItem) => boolean;
}

async function renderComp(inputs: RenderOpts = {}) {
   const { fixture } = await render(AssetItemListViewComponent, {
      providers: [provideRouter([])],
      schemas: [NO_ERRORS_SCHEMA],
      componentInputs: {
         assets: [],
         columns: [],
         sortOptions: makeSortOptions(),
         selectionOn: false,
         selectedItems: [],
         dragSupport: false,
         ...inputs,
      },
   });
   return fixture.componentInstance as AssetItemListViewComponent;
}

afterEach(() => vi.restoreAllMocks());

// ---------------------------------------------------------------------------
// Group 1 — dragAsset
// ---------------------------------------------------------------------------

describe("Group 1 — dragAsset: selectionOn mode vs multiObjectSelectList mode", () => {
   // 🔁 Regression-sensitive: selectionOn controls which selection source is used for drag;
   //    swapping the branches silently drags the wrong item set.
   it("should emit selectedItems when selectionOn=true and dragged asset is in selectedItems", async () => {
      const assetA = makeAsset("A");
      const assetB = makeAsset("B");
      const comp = await renderComp({ selectionOn: true, dragSupport: true, assets: [assetA, assetB] });
      comp.selectedItems = [assetA, assetB];
      const emitSpy = vi.spyOn(comp.dragAssets, "emit");

      comp.dragAsset({}, assetA);

      expect(emitSpy).toHaveBeenCalledWith({ event: {}, data: [assetA, assetB] });
   });

   it("should emit [asset] when selectionOn=true and dragged asset is NOT in selectedItems", async () => {
      const assetA = makeAsset("A");
      const assetB = makeAsset("B");
      const comp = await renderComp({ selectionOn: true, dragSupport: true, assets: [assetA, assetB] });
      comp.selectedItems = [assetA];
      const emitSpy = vi.spyOn(comp.dragAssets, "emit");

      comp.dragAsset({}, assetB);

      expect(emitSpy).toHaveBeenCalledWith({ event: {}, data: [assetB] });
   });

   it("should apply dragSupportFunc filter to the emitted data", async () => {
      const assetA = makeAsset("A");
      const assetB = makeAsset("B");
      const comp = await renderComp({
         selectionOn: true,
         dragSupport: true,
         assets: [assetA, assetB],
         dragSupportFunc: (item: AssetItem) => (item as any).name !== "B",
      });
      comp.selectedItems = [assetA, assetB];
      const emitSpy = vi.spyOn(comp.dragAssets, "emit");

      comp.dragAsset({}, assetA);

      expect(emitSpy).toHaveBeenCalledWith({ event: {}, data: [assetA] });
   });

   it("should use multiObjectSelectList selection when selectionOn=false", async () => {
      // WHY private bypass: multiObjectSelectList is a private field with no public accessor;
      //   the only way to test the selectionOn=false branch is to spy on the private instance.
      const assetA = makeAsset("A");
      const comp = await renderComp({ dragSupport: true });
      vi.spyOn((comp as any).multiObjectSelectList, "getSelectedObjects").mockReturnValue([assetA]);
      const emitSpy = vi.spyOn(comp.dragAssets, "emit");

      comp.dragAsset({}, assetA);

      expect(emitSpy).toHaveBeenCalledWith({ event: {}, data: [assetA] });
   });

   it("should emit [asset] when selectionOn=false and asset is not in multiObjectSelectList selection", async () => {
      // WHY private bypass: same as above — no public accessor for multiObjectSelectList.
      const assetA = makeAsset("A");
      const assetB = makeAsset("B");
      const comp = await renderComp({ dragSupport: true });
      vi.spyOn((comp as any).multiObjectSelectList, "getSelectedObjects").mockReturnValue([assetA]);
      const emitSpy = vi.spyOn(comp.dragAssets, "emit");

      comp.dragAsset({}, assetB);

      expect(emitSpy).toHaveBeenCalledWith({ event: {}, data: [assetB] });
   });
});

// ---------------------------------------------------------------------------
// Group 2 — selectAll
// ---------------------------------------------------------------------------

describe("Group 2 — selectAll: toggles all ↔ none; includes fetchChildrenFunc children", () => {
   // 🔁 Regression-sensitive: selectAll must recursively include children when fetchChildrenFunc
   //    is provided; omitting them silently excludes child items from bulk operations.
   it("should populate selectedItems with all assets when selectAllChecked=false", async () => {
      const assetA = makeAsset("A");
      const assetB = makeAsset("B");
      const comp = await renderComp({ assets: [assetA, assetB] });
      const emitSpy = vi.spyOn(comp.onSelectedChanged, "emit");

      comp.selectAll();

      expect(comp.selectedItems).toEqual([assetA, assetB]);
      expect(emitSpy).toHaveBeenCalledWith([assetA, assetB]);
   });

   it("should clear selectedItems when selectAllChecked=true (all already selected)", async () => {
      const assetA = makeAsset("A");
      const comp = await renderComp({ assets: [assetA] });
      comp.selectedItems = [assetA];
      const emitSpy = vi.spyOn(comp.onSelectedChanged, "emit");

      comp.selectAll();

      expect(comp.selectedItems).toEqual([]);
      expect(emitSpy).toHaveBeenCalledWith([]);
   });

   it("should include children from fetchChildrenFunc when selecting all", async () => {
      const parent = makeAsset("parent");
      const child = makeAsset("child");
      const comp = await renderComp({
         assets: [parent],
         fetchChildrenFunc: () => [child],
      });
      const emitSpy = vi.spyOn(comp.onSelectedChanged, "emit");

      comp.selectAll();

      expect(comp.selectedItems).toContain(parent);
      expect(comp.selectedItems).toContain(child);
      expect(emitSpy).toHaveBeenCalledWith([parent, child]);
   });
});

// ---------------------------------------------------------------------------
// Group 3 — updateSortOptions
// ---------------------------------------------------------------------------

describe("Group 3 — updateSortOptions: sort direction and key management", () => {
   // 🔁 Regression-sensitive: direction toggling applies only to the current active key;
   //    a new key must always reset to ASCENDING regardless of the previous direction.
   it("should toggle ASC→DESC when key is already in sortOptions.keys", async () => {
      const comp = await renderComp({ sortOptions: makeSortOptions(["name"], SortTypes.ASCENDING) });
      const emitSpy = vi.spyOn(comp.sortChanged, "emit");

      comp.updateSortOptions("name");

      expect(comp.sortOptions.type).toBe(SortTypes.DESCENDING);
      expect(emitSpy).toHaveBeenCalled();
   });

   it("should toggle DESC→ASC when key is in sortOptions.keys and type is DESCENDING", async () => {
      const comp = await renderComp({ sortOptions: makeSortOptions(["name"], SortTypes.DESCENDING) });

      comp.updateSortOptions("name");

      expect(comp.sortOptions.type).toBe(SortTypes.ASCENDING);
   });

   it("should set keys=[newKey] and type=ASCENDING when key is not in sortOptions", async () => {
      const comp = await renderComp({ sortOptions: makeSortOptions(["name"], SortTypes.DESCENDING) });

      comp.updateSortOptions("date");

      expect(comp.sortOptions.keys).toEqual(["date"]);
      expect(comp.sortOptions.type).toBe(SortTypes.ASCENDING);
   });
});

// ---------------------------------------------------------------------------
// Group 4 — updateSelection
// ---------------------------------------------------------------------------

describe("Group 4 — updateSelection: toggles item presence in selectedItems", () => {
   it("should add item to selectedItems when not already present", async () => {
      const assetA = makeAsset("A");
      const comp = await renderComp();
      const emitSpy = vi.spyOn(comp.onSelectedChanged, "emit");

      comp.updateSelection(assetA);

      expect(comp.selectedItems).toContain(assetA);
      expect(emitSpy).toHaveBeenCalledWith([assetA]);
   });

   it("should remove item from selectedItems via splice when already present", async () => {
      const assetA = makeAsset("A");
      const assetB = makeAsset("B");
      const comp = await renderComp();
      comp.selectedItems = [assetA, assetB];
      const emitSpy = vi.spyOn(comp.onSelectedChanged, "emit");

      comp.updateSelection(assetA);

      expect(comp.selectedItems).not.toContain(assetA);
      expect(comp.selectedItems).toContain(assetB);
      expect(emitSpy).toHaveBeenCalledWith([assetB]);
   });
});

// ---------------------------------------------------------------------------
// Group 5 — selectAllChecked
// ---------------------------------------------------------------------------

describe("Group 5 — selectAllChecked getter: reflects selection completeness", () => {
   it("should return true when all assets are in selectedItems", async () => {
      const assetA = makeAsset("A");
      const assetB = makeAsset("B");
      const comp = await renderComp({ assets: [assetA, assetB] });
      comp.selectedItems = [assetA, assetB];

      expect(comp.selectAllChecked).toBe(true);
   });

   it("should return false when only some assets are selected", async () => {
      const assetA = makeAsset("A");
      const assetB = makeAsset("B");
      const comp = await renderComp({ assets: [assetA, assetB] });
      comp.selectedItems = [assetA];

      expect(comp.selectAllChecked).toBe(false);
   });

   it("should return false when selectedItems is empty", async () => {
      const assetA = makeAsset("A");
      const comp = await renderComp({ assets: [assetA] });

      expect(comp.selectAllChecked).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 6 — toggleItem / isToggled
// ---------------------------------------------------------------------------

describe("Group 6 — toggleItem/isToggled: add then remove from toggleItems", () => {
   it("should add item to toggleItems when not currently toggled", async () => {
      const asset = makeAsset("X");
      const comp = await renderComp();

      comp.toggleItem(asset);

      expect(comp.isToggled(asset)).toBe(true);
   });

   it("should remove item from toggleItems when already toggled", async () => {
      const asset = makeAsset("X");
      const comp = await renderComp();
      comp.toggleItems = [asset];

      comp.toggleItem(asset);

      expect(comp.isToggled(asset)).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 7 — getToggleIcon
// ---------------------------------------------------------------------------

describe("Group 7 — getToggleIcon: icon string based on toggle state", () => {
   it("should return '' when fetchChildrenFunc is not set", async () => {
      const comp = await renderComp();

      expect(comp.getToggleIcon(makeAsset("X"))).toBe("");
   });

   it("should return caret-down-icon when item is toggled", async () => {
      const asset = makeAsset("X");
      const comp = await renderComp({ fetchChildrenFunc: () => [] });
      comp.toggleItems = [asset];

      expect(comp.getToggleIcon(asset)).toBe("caret-down-icon icon-lg");
   });

   it("should return caret-right-icon when item is not toggled", async () => {
      const comp = await renderComp({ fetchChildrenFunc: () => [] });

      expect(comp.getToggleIcon(makeAsset("X"))).toBe("caret-right-icon icon-lg");
   });
});

// ---------------------------------------------------------------------------
// Group 8 — hasChildren / getChildren
// ---------------------------------------------------------------------------

describe("Group 8 — hasChildren/getChildren: fetchChildrenFunc delegation", () => {
   it("should return [] from getChildren when fetchChildrenFunc is not set", async () => {
      const comp = await renderComp();

      expect(comp.getChildren(makeAsset("X"))).toEqual([]);
   });

   it("should delegate to fetchChildrenFunc from getChildren", async () => {
      const child = makeAsset("child");
      const comp = await renderComp({ fetchChildrenFunc: () => [child] });

      expect(comp.getChildren(makeAsset("parent"))).toEqual([child]);
   });

   it("should return true from hasChildren when fetchChildrenFunc returns non-empty array", async () => {
      const comp = await renderComp({ fetchChildrenFunc: () => [makeAsset("child")] });

      expect(comp.hasChildren(makeAsset("parent"))).toBe(true);
   });

   it("should return false from hasChildren when fetchChildrenFunc returns empty array", async () => {
      const comp = await renderComp({ fetchChildrenFunc: () => [] });

      expect(comp.hasChildren(makeAsset("parent"))).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 9 — clickItem
// ---------------------------------------------------------------------------

describe("Group 9 — clickItem: emit controlled by boolean flag", () => {
   it("should emit onClickItem when emit=true", async () => {
      const asset = makeAsset("X");
      const comp = await renderComp();
      const emitSpy = vi.spyOn(comp.onClickItem, "emit");

      comp.clickItem(asset, true);

      expect(emitSpy).toHaveBeenCalledWith(asset);
   });

   it("should NOT emit onClickItem when emit=false", async () => {
      const asset = makeAsset("X");
      const comp = await renderComp();
      const emitSpy = vi.spyOn(comp.onClickItem, "emit");

      comp.clickItem(asset, false);

      expect(emitSpy).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 10 — updateAssetSelection / isSelectedItem guards
// ---------------------------------------------------------------------------

// WHY private bypass: the guard's only observable effect is suppressing
//   multiObjectSelectList.selectWithEvent; multiObjectSelectList is private
//   with no public accessor, so spying on it is the only way to verify the guard.
describe("Group 10 — updateAssetSelection/isSelectedItem: early-return guards", () => {
   it("should not delegate to multiObjectSelectList when selectionOn=true", async () => {
      const comp = await renderComp({ selectionOn: true, dragSupport: true });
      const selectSpy = vi.spyOn((comp as any).multiObjectSelectList, "selectWithEvent");

      comp.updateAssetSelection(makeAsset("X"), new MouseEvent("click"));

      expect(selectSpy).not.toHaveBeenCalled();
   });

   it("should not delegate to multiObjectSelectList when dragSupport=false", async () => {
      const comp = await renderComp({ selectionOn: false, dragSupport: false });
      const selectSpy = vi.spyOn((comp as any).multiObjectSelectList, "selectWithEvent");

      comp.updateAssetSelection(makeAsset("X"), new MouseEvent("click"));

      expect(selectSpy).not.toHaveBeenCalled();
   });

   it("should return false from isSelectedItem when selectionOn=true", async () => {
      const comp = await renderComp({ selectionOn: true, dragSupport: true });

      expect(comp.isSelectedItem(makeAsset("X"))).toBe(false);
   });

   it("should return false from isSelectedItem when dragSupport=false", async () => {
      const comp = await renderComp({ selectionOn: false, dragSupport: false });

      expect(comp.isSelectedItem(makeAsset("X"))).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 11 — supportDrag
// ---------------------------------------------------------------------------

describe("Group 11 — supportDrag: dragSupport combined with optional dragSupportFunc", () => {
   it("should return false when dragSupport=false regardless of dragSupportFunc", async () => {
      const comp = await renderComp({ dragSupport: false, dragSupportFunc: () => true });

      expect(comp.supportDrag(makeAsset("X"))).toBe(false);
   });

   it("should return true when dragSupport=true and no dragSupportFunc is set", async () => {
      const comp = await renderComp({ dragSupport: true });

      expect(comp.supportDrag(makeAsset("X"))).toBe(true);
   });

   it("should return dragSupportFunc result when dragSupport=true and func is set", async () => {
      const comp = await renderComp({
         dragSupport: true,
         dragSupportFunc: (item: AssetItem) => (item as any).name === "allowed",
      });

      expect(comp.supportDrag(makeAsset("allowed"))).toBe(true);
      expect(comp.supportDrag(makeAsset("denied"))).toBe(false);
   });
});
