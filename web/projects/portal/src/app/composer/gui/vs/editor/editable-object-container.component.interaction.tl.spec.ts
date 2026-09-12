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
 * EditableObjectContainer — Pass 1: Interaction
 *
 * Risk-first coverage:
 *   Group 1  [Risk 3] — calculateZIndex: static method — z-index accumulation,
 *                        resizing-table 9999, dropdown calendar boost
 *   Group 2  [Risk 2] — getMinHeight: VSCalendar dropdown vs non-dropdown;
 *                        bottom-tabs with calendarsShown adds CALENDAR_BODY_HEIGHT
 *   Group 3  [Risk 2] — getTopPosition: normal offset; selectionChild → 0
 *   Group 4  [Risk 2] — isDragBorder*: input-driven flag checks
 *   Group 5  [Risk 2] — @Output emitters: openEditPane, openEmbeddedViewsheet,
 *                        onMaxModeChange, openWizardPane
 *   Group 6  [Risk 2] — isShape / isFormAssembly / isFadeAssembly / isViewsheet / isLocked
 *   Group 7  [Risk 2] — onEnter / onLeave: dropTarget counter
 *   Group 8  [Risk 1] — goToWizardVisible / clickEditButton dispatch routing
 *   Group 9  [Risk 1] — contextMenuOpen / contextMenuClose: state flags
 *   Group 10 [Risk 1] — moveHandle set via vsObjectModel setter (VSObjectMoveHandle.getMoveHandle)
 *   Group 11 [Risk 1] — toolbarForceHidden / onMouseEnter delegation
 *   Group 12 [Risk 1] — popupShowing basic / showSlideout / hasSlideout
 *
 * Out of scope (covered in risk pass):
 *   getMovedPosition, onDragMove, removeCopies, onResizeMove,
 *   onLineDragMove, onLineStartDragMove, onLineEndDragMove
 */

import { EditableObjectContainer } from "./editable-object-container.component";
import { Viewsheet } from "../../../data/vs/viewsheet";
import { VSUtil } from "../../../../vsobjects/util/vs-util";
import { DragBorderType } from "../objects/selection/composer-selection-container-children.component";
import {
   makeComponent,
   makeVsObjectModel,
} from "./editable-object-container.component.test-helpers";

afterEach(() => vi.restoreAllMocks());

// ---------------------------------------------------------------------------
// Group 1: calculateZIndex [Risk 3]
// ---------------------------------------------------------------------------

describe("EditableObjectContainer — calculateZIndex", () => {

   it("should return objectFormat.zIndex when object has no container", () => {
      const vs = new Viewsheet();
      const model = makeVsObjectModel("VSText", "Text1");
      model.objectFormat.zIndex = 42;
      model.container = null;

      expect(EditableObjectContainer.calculateZIndex(model, vs)).toBe(42);
   });

   it("should return 9999 for a table object with resizingCell=true", () => {
      const vs = new Viewsheet();
      const model = makeVsObjectModel("VSTable", "Table1") as any;
      model.objectFormat.zIndex = 10;
      model.resizingCell = true;

      expect(EditableObjectContainer.calculateZIndex(model, vs)).toBe(9999);
   });

   it("should accumulate z-index up the container chain", () => {
      const vs = new Viewsheet();
      const child = makeVsObjectModel("VSText", "Child") as any;
      child.objectFormat.zIndex = 5;
      child.container = "Parent";

      const parent = makeVsObjectModel("VSGroupContainer", "Parent") as any;
      parent.objectFormat.zIndex = 10;
      parent.container = null;

      vs.vsObjects.push(parent);

      const result = EditableObjectContainer.calculateZIndex(child, vs);
      expect(result).toBeGreaterThan(child.objectFormat.zIndex);
   });

   it("should add 100000 boost for objects with dropdown=true and not hidden", () => {
      const vs = new Viewsheet();
      const combo = makeVsObjectModel("VSComboBox", "Combo1") as any;
      combo.objectFormat.zIndex = 3;
      combo.container = null;
      // 'dropdown' is the property checked by calculateZIndex (not 'showDropdown')
      combo.dropdown = true;
      // hidden is unset → SelectionBaseController.isHidden returns false → boost applies

      const result = EditableObjectContainer.calculateZIndex(combo, vs);
      expect(result).toBeGreaterThanOrEqual(100000);
   });
});

// ---------------------------------------------------------------------------
// Group 2: getMinHeight [Risk 2]
// ---------------------------------------------------------------------------

describe("EditableObjectContainer — getMinHeight", () => {

   it("should return objectFormat.height for a non-VSCalendar object", () => {
      const { comp } = makeComponent();
      comp.vsObject.objectFormat.height = 162;

      expect(comp.getMinHeight()).toBe(162);
   });

   it("should return titleFormat.height for a VSCalendar dropdown (non-bottom-tabs)", () => {
      const { comp } = makeComponent();
      const cal = makeVsObjectModel("VSCalendar", "Cal1") as any;
      cal.objectFormat.height = 200;
      cal.titleFormat = { height: 20 };
      cal.dropdownCalendar = true;
      cal.calendarsShown = false;

      comp.viewsheet = new Viewsheet();
      comp.vsObjectModel = cal;

      expect(comp.getMinHeight()).toBe(20);
   });

   it("should return titleFormat.height + CALENDAR_BODY_HEIGHT for dropdown in bottom-tabs with calendarsShown", () => {
      const vs = new Viewsheet();
      const tab = makeVsObjectModel("VSTab", "Tab1") as any;
      tab.objectFormat = { ...tab.objectFormat, zIndex: 1 };
      tab.bottomTabs = true;
      tab.absoluteName = "Tab1";
      vs.vsObjects.push(tab);

      const cal = makeVsObjectModel("VSCalendar", "Cal1") as any;
      cal.objectFormat.height = 200;
      cal.titleFormat = { height: 20 };
      cal.dropdownCalendar = true;
      cal.calendarsShown = true;
      cal.container = "Tab1";

      const { comp } = makeComponent({ vs });
      comp.vsObjectModel = cal;

      expect(comp.getMinHeight()).toBe(20 + VSUtil.CALENDAR_BODY_HEIGHT);
   });
});

// ---------------------------------------------------------------------------
// Group 3: getTopPosition [Risk 2]
// ---------------------------------------------------------------------------

describe("EditableObjectContainer — getTopPosition", () => {

   it("should return objectFormat.top minus selectionBorderOffset", () => {
      const { comp } = makeComponent();
      comp.vsObject.objectFormat.top = 300;
      comp.selectionBorderOffset = 5;

      expect(comp.getTopPosition()).toBe(295);
   });

   it("should return 0 when selectionChildModel is set", () => {
      const { comp } = makeComponent();
      comp.vsObject.objectFormat.top = 300;
      comp.selectionBorderOffset = 5;
      (comp as any).selectionChildModel = { index: 0, container: "Parent" };

      expect(comp.getTopPosition()).toBe(0);
   });
});

// ---------------------------------------------------------------------------
// Group 4: isDragBorder* [Risk 2]
// ---------------------------------------------------------------------------

describe("EditableObjectContainer — isDragBorder flags", () => {

   it("isDragBorderTop returns true only for DragBorderType.ABOVE", () => {
      const { comp } = makeComponent();
      comp.dragOverBorder = DragBorderType.ABOVE;
      expect(comp.isDragBorderTop()).toBe(true);
      comp.dragOverBorder = DragBorderType.BELOW;
      expect(comp.isDragBorderTop()).toBe(false);
   });

   it("isDragBorderBottom returns true only for DragBorderType.BELOW", () => {
      const { comp } = makeComponent();
      comp.dragOverBorder = DragBorderType.BELOW;
      expect(comp.isDragBorderBottom()).toBe(true);
      comp.dragOverBorder = DragBorderType.NONE;
      expect(comp.isDragBorderBottom()).toBe(false);
   });

   it("isDragBorderAll returns true only for DragBorderType.ALL", () => {
      const { comp } = makeComponent();
      comp.dragOverBorder = DragBorderType.ALL;
      expect(comp.isDragBorderAll()).toBe(true);
      comp.dragOverBorder = DragBorderType.ABOVE;
      expect(comp.isDragBorderAll()).toBe(false);
   });

   it("isDragBorder returns true for ABOVE, BELOW, ALL; false for NONE", () => {
      const { comp } = makeComponent();
      comp.dragOverBorder = DragBorderType.NONE;
      expect(comp.isDragBorder()).toBe(false);
      comp.dragOverBorder = DragBorderType.ABOVE;
      expect(comp.isDragBorder()).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 5: @Output emitters [Risk 2]
// ---------------------------------------------------------------------------

describe("EditableObjectContainer — @Output emitters", () => {

   it("openEditPane emits onOpenEditPane with vsObject", () => {
      const { comp } = makeComponent();
      const spy = vi.fn();
      comp.onOpenEditPane.subscribe(spy);

      comp.openEditPane();

      expect(spy).toHaveBeenCalledTimes(1);
      expect(spy).toHaveBeenCalledWith(comp.vsObject);
   });

   it("openEmbeddedViewsheet emits onOpenEmbeddedViewsheet with assetId", () => {
      const { comp } = makeComponent();
      const spy = vi.fn();
      comp.onOpenEmbeddedViewsheet.subscribe(spy);

      comp.openEmbeddedViewsheet("asset-123");

      expect(spy).toHaveBeenCalledWith("asset-123");
   });

   it("onMaxModeChange passes event through maxModeChange output", () => {
      const { comp } = makeComponent();
      const spy = vi.fn();
      comp.maxModeChange.subscribe(spy);
      const evt = { assembly: "Text1", maxMode: true };

      comp.onMaxModeChange(evt);

      expect(spy).toHaveBeenCalledWith(evt);
   });

   it("openWizardPane emits onOpenWizardPane with model containing objectModel", () => {
      const { comp } = makeComponent();
      comp.viewsheet.runtimeId = "vs-rt";
      comp.viewsheet.linkUri = "/link";
      const spy = vi.fn();
      comp.onOpenWizardPane.subscribe(spy);

      comp.openWizardPane();

      expect(spy).toHaveBeenCalledTimes(1);
      const model = spy.mock.calls[0][0];
      expect(model.objectModel).toBe(comp.vsObject);
   });
});

// ---------------------------------------------------------------------------
// Group 6: isShape / isFormAssembly / isFadeAssembly / isViewsheet / isLocked [Risk 2]
// ---------------------------------------------------------------------------

describe("EditableObjectContainer — object-type flag checks", () => {

   it("isShape returns true for VSOval, VSRectangle, VSLine", () => {
      const { comp } = makeComponent();
      for(const type of ["VSOval", "VSRectangle", "VSLine"] as const) {
         comp.vsObject.objectType = type;
         expect(comp.isShape()).toBe(true);
      }
   });

   it("isShape returns false for VSText", () => {
      const { comp } = makeComponent();
      comp.vsObject.objectType = "VSText";
      expect(comp.isShape()).toBe(false);
   });

   it("isFormAssembly returns true for VSCheckBox", () => {
      const { comp } = makeComponent();
      comp.vsObject.objectType = "VSCheckBox";
      expect(comp.isFormAssembly()).toBe(true);
   });

   it("isFormAssembly returns false for VSText", () => {
      const { comp } = makeComponent();
      comp.vsObject.objectType = "VSText";
      expect(comp.isFormAssembly()).toBe(false);
   });

   it("isViewsheet returns true for VSViewsheet", () => {
      const { comp } = makeComponent();
      comp.vsObject.objectType = "VSViewsheet";
      expect(comp.isViewsheet()).toBe(true);
   });

   it("isFadeAssembly returns true when object is not active and no selectionChild", () => {
      const { comp } = makeComponent();
      comp.vsObject.active = false;
      (comp as any).selectionChildModel = null;
      expect(comp.isFadeAssembly()).toBe(true);
   });

   it("isFadeAssembly returns false when object is active", () => {
      const { comp } = makeComponent();
      comp.vsObject.active = true;
      expect(comp.isFadeAssembly()).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 7: onEnter / onLeave [Risk 1]
// ---------------------------------------------------------------------------

describe("EditableObjectContainer — dropTarget counter (onEnter/onLeave)", () => {

   it("onEnter increments the dropTarget counter", () => {
      const { comp } = makeComponent();
      expect(comp.dropTarget).toBe(0);
      comp.onEnter({ preventDefault: vi.fn() } as any);
      expect(comp.dropTarget).toBe(1);
   });

   it("onLeave decrements the dropTarget counter", () => {
      const { comp } = makeComponent();
      comp.dropTarget = 2;
      comp.onLeave({ preventDefault: vi.fn() } as any);
      expect(comp.dropTarget).toBe(1);
   });
});

// ---------------------------------------------------------------------------
// Group 8: goToWizardVisible / clickEditButton routing [Risk 1]
// ---------------------------------------------------------------------------

describe("EditableObjectContainer — goToWizardVisible / clickEditButton", () => {

   it("goToWizardVisible is false when viewsheet has no baseEntry", () => {
      const { comp } = makeComponent();
      comp.viewsheet.baseEntry = null;
      expect(comp.goToWizardVisible).toBe(false);
   });

   it("goToWizardVisible is true when viewsheet has baseEntry (cube source)", () => {
      const { comp } = makeComponent();
      (comp.viewsheet as any).baseEntry = { identifier: "cube" };
      expect(comp.goToWizardVisible).toBe(true);
   });

   it("clickEditButton opens edit pane for non-chart objects", () => {
      const { comp } = makeComponent();
      const editSpy = vi.fn();
      comp.onOpenEditPane.subscribe(editSpy);
      comp.vsObject.objectType = "VSText";

      comp.clickEditButton();

      expect(editSpy).toHaveBeenCalled();
   });

   it("clickEditButton opens edit pane for chart when no baseEntry", () => {
      const { comp } = makeComponent();
      comp.vsObject.objectType = "VSChart";
      comp.viewsheet.baseEntry = null;
      const editSpy = vi.fn();
      comp.onOpenEditPane.subscribe(editSpy);

      comp.clickEditButton();

      expect(editSpy).toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 9: contextMenuOpen / contextMenuClose [Risk 1]
// ---------------------------------------------------------------------------

describe("EditableObjectContainer — contextMenuOpen / contextMenuClose", () => {

   it("contextMenuClose sets contextMenuVisible to false and calls hiddenUnfreeze", () => {
      const { comp, mocks } = makeComponent();
      (comp as any).contextMenuVisible = true;

      comp.contextMenuClose();

      expect(comp.contextMenuVisible).toBe(false);
      expect(mocks.miniToolbarService.hiddenUnfreeze).toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 10: moveHandle set via vsObjectModel setter [Risk 1]
// ---------------------------------------------------------------------------

describe("EditableObjectContainer — moveHandle (vsObjectModel setter)", () => {

   it("sets moveHandle to .text-content for VSText", () => {
      const { comp } = makeComponent();
      comp.vsObjectModel = makeVsObjectModel("VSText", "T1");
      expect(comp.moveHandle).toBe(".text-content");
   });

   it("sets moveHandle to .line-resize-container for VSLine", () => {
      const { comp } = makeComponent();
      comp.vsObjectModel = makeVsObjectModel("VSLine", "L1");
      expect(comp.moveHandle).toBe(".line-resize-container");
   });

   it("sets moveHandle to .title-move-zone for VSTable", () => {
      const { comp } = makeComponent();
      comp.vsObjectModel = makeVsObjectModel("VSTable", "Tbl1");
      expect(comp.moveHandle).toBe(".title-move-zone");
   });

   it("sets moveHandle to null for VSComboBox", () => {
      const { comp } = makeComponent();
      comp.vsObjectModel = makeVsObjectModel("VSComboBox", "Cb1");
      expect(comp.moveHandle).toBeNull();
   });
});

// ---------------------------------------------------------------------------
// Group 11: toolbarForceHidden / onMouseEnter delegation [Risk 1]
// ---------------------------------------------------------------------------

describe("EditableObjectContainer — service delegation", () => {

   it("toolbarForceHidden delegates to miniToolbarService.isMiniToolbarHidden", () => {
      const { comp, mocks } = makeComponent();
      mocks.miniToolbarService.isMiniToolbarHidden.mockReturnValue(true);

      expect(comp.toolbarForceHidden()).toBe(true);
      expect(mocks.miniToolbarService.isMiniToolbarHidden)
         .toHaveBeenCalledWith(comp.vsObject.absoluteName);
   });

   it("onMouseEnter delegates to miniToolbarService.handleMouseEnter", () => {
      const { comp, mocks } = makeComponent();
      const evt = { type: "mouseenter" };

      comp.onMouseEnter(evt);

      expect(mocks.miniToolbarService.handleMouseEnter)
         .toHaveBeenCalledWith(comp.vsObject.absoluteName, evt);
   });

   it("onTitleResizeMove delegates to composerObjectService.adjustTitleHeight", () => {
      const { comp, mocks } = makeComponent();
      comp.onTitleResizeMove(40);
      expect(mocks.composerObjectService.adjustTitleHeight)
         .toHaveBeenCalledWith(comp.vsObject, 40);
   });

   it("onTitleResizeEnd delegates to composerObjectService.resizeObjectTitle", () => {
      const { comp, mocks } = makeComponent();
      comp.onTitleResizeEnd();
      expect(mocks.composerObjectService.resizeObjectTitle)
         .toHaveBeenCalledWith(comp.viewsheet, comp.vsObject);
   });
});

// ---------------------------------------------------------------------------
// Group 12: hasSlideout / showSlideout [Risk 1]
// ---------------------------------------------------------------------------

describe("EditableObjectContainer — hasSlideout / showSlideout", () => {

   it("hasSlideout delegates to dialogService.hasSlideout", () => {
      const { comp, mocks } = makeComponent();
      mocks.dialogService.hasSlideout.mockReturnValue(true);
      expect(comp.hasSlideout()).toBe(true);
   });

   it("showSlideout delegates to dialogService.showSlideoutFor", () => {
      const { comp, mocks } = makeComponent();
      comp.showSlideout();
      expect(mocks.dialogService.showSlideoutFor).toHaveBeenCalled();
   });
});
