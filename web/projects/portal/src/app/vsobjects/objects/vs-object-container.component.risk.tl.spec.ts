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
 * VSObjectContainer — Pass 2: Risk / Async
 *
 * Risk-first coverage:
 *   Group 1   isAssemblyVisible — all condition branches (composer, viewer, dataTip, container)
 *   Group 2   isMiniToolbarVisible — all objectType branches and guard conditions
 *   Group 3   isMaxModeHidden — maxMode search, annotation container walk, dataTip guards
 *   Group 4   isSelected / isFocused — array membership and name comparison
 *   Group 5   scrollViewport setter — only triggers update when rect actually changes
 *   Group 6   viewer getter — combines context.viewer and context.preview
 *   Group 7   popupShowing / isPopupShowing — data tip and pop component coordination
 *   Group 8   isFilterInMaxModeView — adhocFilter + max mode check
 */

import {
   makeComponent,
   makeContextProvider,
   makeDataTipService,
   makeVSObject,
   makeVsInfo,
   makeObjectFormat,
} from "./vs-object-container.component.test-helpers";

// ---------------------------------------------------------------------------
// Group 1 — isAssemblyVisible
// ---------------------------------------------------------------------------

describe("Group 1 — isAssemblyVisible: all branches", () => {
   it("should return true in composer context regardless of model.visible (not viewer/preview)", () => {
      const context = makeContextProvider(); // viewer=false, preview=false
      const { comp } = makeComponent({ context });
      const obj = makeVSObject({ visible: false });
      expect(comp.isAssemblyVisible(obj)).toBe(true);
   });

   it("should return true in viewer context when model.visible=true and no container", () => {
      const context = { viewer: true, preview: false, binding: false };
      const { comp } = makeComponent({ context });
      const obj = makeVSObject({ visible: true, container: null });
      expect(comp.isAssemblyVisible(obj)).toBe(true);
   });

   it("should return false in viewer when model.visible=false and no container and no dataTip", () => {
      const context = { viewer: true, preview: false, binding: false };
      const { comp } = makeComponent({ context });
      const obj = makeVSObject({ visible: false, container: null });
      expect(comp.isAssemblyVisible(obj)).toBe(false);
   });

   it("should return true in viewer when dataTipService.isDataTipVisible returns true for absoluteName", () => {
      const context = { viewer: true, preview: false, binding: false };
      const dataTipSvc = makeDataTipService({
         isDataTipVisible: vi.fn((name: string) => name === "Tip1"),
         dataTipName: "Tip1",
      });
      const { comp } = makeComponent({ context, dataTipSvc: dataTipSvc as any });
      const obj = makeVSObject({ visible: false, absoluteName: "Tip1" });
      expect(comp.isAssemblyVisible(obj)).toBe(true);
   });

   it("should return true in viewer when container dataTip is visible", () => {
      const context = { viewer: true, preview: false, binding: false };
      const dataTipSvc = makeDataTipService({
         isDataTipVisible: vi.fn((name: string) => name === "ParentTip"),
         dataTipName: "ParentTip",
      });
      const { comp } = makeComponent({ context, dataTipSvc: dataTipSvc as any });
      const obj = makeVSObject({ visible: false, container: "ParentTip" });
      expect(comp.isAssemblyVisible(obj)).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 2 — isMiniToolbarVisible
// ---------------------------------------------------------------------------

describe("Group 2 — isMiniToolbarVisible: objectType and guard conditions", () => {
   it("should return false for VSSelectionContainer containerType", () => {
      const { comp } = makeComponent();
      const obj = makeVSObject({ containerType: "VSSelectionContainer", objectType: "VSSelectionList" });
      expect(comp.isMiniToolbarVisible(obj)).toBe(false);
   });

   it("should return false for embeddedVS=true when not viewer", () => {
      const context = makeContextProvider(); // viewer=false
      const { comp } = makeComponent({ context, embeddedVS: true });
      const obj = makeVSObject({ objectType: "VSChart" });
      expect(comp.isMiniToolbarVisible(obj)).toBe(false);
   });

   it("should return true for VSChart objectType when not embedded or special conditions", () => {
      const { comp } = makeComponent();
      const obj = makeVSObject({ objectType: "VSChart" });
      expect(comp.isMiniToolbarVisible(obj)).toBe(true);
   });

   it("should return true for VSTable objectType", () => {
      const { comp } = makeComponent();
      const obj = makeVSObject({ objectType: "VSTable" });
      expect(comp.isMiniToolbarVisible(obj)).toBe(true);
   });

   it("should return false for VSText (not in the allowed list)", () => {
      const { comp } = makeComponent();
      const obj = makeVSObject({ objectType: "VSText" });
      expect(comp.isMiniToolbarVisible(obj)).toBe(false);
   });

   it("should return true for VSRangeSlider", () => {
      const { comp } = makeComponent();
      const obj = makeVSObject({ objectType: "VSRangeSlider" });
      expect(comp.isMiniToolbarVisible(obj)).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 3 — isMaxModeHidden
// ---------------------------------------------------------------------------

describe("Group 3 — isMaxModeHidden: complex max mode logic", () => {
   it("should return false when vsInfo is null", () => {
      const { comp } = makeComponent();
      comp.vsInfo = null;
      expect(comp.isMaxModeHidden(makeVSObject())).toBe(false);
   });

   it("should return false when model.sheetMaxMode=false", () => {
      const { comp } = makeComponent();
      const obj = makeVSObject({ sheetMaxMode: false });
      expect(comp.isMaxModeHidden(obj)).toBe(false);
   });

   it("should return false when no object in vsObjects has maxMode=true", () => {
      const { comp } = makeComponent();
      const obj = makeVSObject({ sheetMaxMode: true });
      comp.vsInfo = makeVsInfo([obj]);
      expect(comp.isMaxModeHidden(obj)).toBe(false);
   });

   it("should return false for the object that IS in max mode (absoluteName === maxObj.absoluteName)", () => {
      const { comp } = makeComponent();
      const maxObj = makeVSObject({ absoluteName: "Chart1", objectType: "VSChart", sheetMaxMode: true });
      (maxObj as any).maxMode = true; // maxMode is per-subtype (VSChartModel etc.), not on VSObjectModel base
      comp.vsInfo = makeVsInfo([maxObj]);
      expect(comp.isMaxModeHidden(maxObj)).toBe(false);
   });

   it("should return true for a non-max-mode object when another VSChart is in max mode", () => {
      const { comp } = makeComponent();
      const maxChart = makeVSObject({ absoluteName: "Chart1", objectType: "VSChart" });
      (maxChart as any).maxMode = true; // maxMode is per-subtype (VSChartModel etc.), not on VSObjectModel base
      const otherObj = makeVSObject({ absoluteName: "Table1", objectType: "VSTable", sheetMaxMode: true });
      comp.vsInfo = makeVsInfo([maxChart, otherObj]);
      expect(comp.isMaxModeHidden(otherObj)).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 4 — isSelected / isFocused
// ---------------------------------------------------------------------------

describe("Group 4 — isSelected / isFocused: array membership and name comparison", () => {
   it("should return true when index is in selectedAssemblies", () => {
      const { comp } = makeComponent({ selectedAssemblies: [0, 2] });
      expect(comp.isSelected(0)).toBe(true);
      expect(comp.isSelected(2)).toBe(true);
   });

   it("should return false when index is not in selectedAssemblies", () => {
      const { comp } = makeComponent({ selectedAssemblies: [0] });
      expect(comp.isSelected(1)).toBe(false);
   });

   it("should return false when selectedAssemblies is empty", () => {
      const { comp } = makeComponent({ selectedAssemblies: [] });
      expect(comp.isSelected(0)).toBe(false);
   });

   it("should return true when focusedObject absoluteName matches", () => {
      const { comp } = makeComponent();
      comp.focusedObject = makeVSObject({ absoluteName: "Chart1" });
      const obj = makeVSObject({ absoluteName: "Chart1" });
      expect(comp.isFocused(obj)).toBe(true);
   });

   it("should return false when focusedObject absoluteName differs", () => {
      const { comp } = makeComponent();
      comp.focusedObject = makeVSObject({ absoluteName: "Chart1" });
      const obj = makeVSObject({ absoluteName: "Table1" });
      expect(comp.isFocused(obj)).toBe(false);
   });

   it("should return false when no focusedObject", () => {
      const { comp } = makeComponent();
      comp.focusedObject = null;
      expect(comp.isFocused(makeVSObject())).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 5 — scrollViewport setter
// ---------------------------------------------------------------------------

describe("Group 5 — scrollViewport setter: only triggers updateRendered on change", () => {
   it("should store the viewport value when set", () => {
      const { comp } = makeComponent();
      const rect = { top: 0, left: 0, width: 800, height: 600 };
      comp.scrollViewport = rect as any;
      expect(comp.scrollViewport).toEqual(rect);
   });

   it("should not update when new rect is identical to existing", () => {
      const { comp } = makeComponent();
      const rect = { top: 10, left: 0, width: 800, height: 600 };
      comp.scrollViewport = rect as any;
      // Set again with identical values — should not trigger another update
      const spy = vi.spyOn(comp as any, "updateRendered");
      comp.scrollViewport = { ...rect } as any;
      expect(spy).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 6 — viewer getter
// ---------------------------------------------------------------------------

describe("Group 6 — viewer getter: returns true when context.viewer OR context.preview", () => {
   it("should return true when context.viewer=true", () => {
      const { comp } = makeComponent({ context: { viewer: true, preview: false, binding: false } });
      expect(comp.viewer).toBe(true);
   });

   it("should return true when context.preview=true", () => {
      const { comp } = makeComponent({ context: { viewer: false, preview: true, binding: false } });
      expect(comp.viewer).toBe(true);
   });

   it("should return false when both viewer and preview are false", () => {
      const { comp } = makeComponent({ context: makeContextProvider() });
      expect(comp.viewer).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 7 — popupShowing / isPopupShowing
// ---------------------------------------------------------------------------

describe("Group 7 — popupShowing / isPopupShowing: data tip and pop component", () => {
   it("should return false for popupShowing when no data tip and no pop component and embeddedVS=false", () => {
      const { comp } = makeComponent({ embeddedVS: false });
      expect(comp.popupShowing).toBe(false);
   });

   it("should return false for popupShowing when embeddedVS=true (top-level only)", () => {
      const { comp } = makeComponent({ embeddedVS: true });
      expect(comp.popupShowing).toBe(false);
   });

   it("should return true for isPopupShowing when dataTip matches and dataTip is visible", () => {
      const dataTipSvc = makeDataTipService({
         isDataTipVisible: vi.fn().mockReturnValue(true),
         dataTipName: "TipX",
      });
      const { comp } = makeComponent({ dataTipSvc: dataTipSvc as any });
      const obj = makeVSObject({ absoluteName: "TipX" });
      (obj as any).dataTip = "TipX";
      expect(comp.isPopupShowing(obj)).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 8 — isFilterInMaxModeView
// ---------------------------------------------------------------------------

describe("Group 8 — isFilterInMaxModeView: adhocFilter in max mode context", () => {
   it("should return false when context is not viewer", () => {
      const { comp } = makeComponent({ context: makeContextProvider() });
      const obj = makeVSObject();
      (obj as any).adhocFilter = true;
      expect(comp.isFilterInMaxModeView(obj)).toBe(false);
   });

   it("should return false when model has no adhocFilter", () => {
      const context = { viewer: true, preview: false, binding: false };
      const { comp } = makeComponent({ context });
      const obj = makeVSObject();
      expect(comp.isFilterInMaxModeView(obj)).toBe(false);
   });

   it("should return true when viewer=true and adhocFilter=true and vsInfo has a maxMode object", () => {
      const context = { viewer: true, preview: false, binding: false };
      const maxObj = makeVSObject({ absoluteName: "Chart1", objectType: "VSChart" });
      (maxObj as any).maxMode = true; // maxMode is per-subtype (VSChartModel etc.), not on VSObjectModel base
      const filterObj = makeVSObject({ absoluteName: "Filter1" });
      (filterObj as any).adhocFilter = true;
      const { comp } = makeComponent({ context, vsInfo: makeVsInfo([maxObj, filterObj]) });
      expect(comp.isFilterInMaxModeView(filterObj)).toBe(true);
   });
});
