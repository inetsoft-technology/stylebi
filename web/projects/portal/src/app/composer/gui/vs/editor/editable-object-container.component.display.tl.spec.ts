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
 * EditableObjectContainer — Pass 3: Display
 *
 * dispatch≥3: label/icon/conditional display / boundary inputs
 *
 *   Group 1  — popupShowing: dataTipName truthy → true; adhocFilterShowing → true; both false → false
 *   Group 2  — showEdit: empty VSText with no drag / no changedByScript → true; various false cases
 *   Group 3  — showLayoutOptionDialog (private): VSTab always true;
 *              DIMENSION_FOLDER with non-container → true; table data on non-VSTable → true;
 *              VSCalendar with non-date dtype → true; table/chart without cube column → true
 *   Group 4  — showSlideout: delegates to dialogService.showSlideoutFor with viewsheet and vsObject
 *   Group 5  — onTitleResizeMove / onTitleResizeEnd: boundary heights (0, negative)
 */

import { EditableObjectContainer } from "./editable-object-container.component";
import {
   makeComponent,
   makeVsObjectModel,
} from "./editable-object-container.component.test-helpers";

afterEach(() => vi.restoreAllMocks());

// ---------------------------------------------------------------------------
// Group 1: popupShowing [Risk 2]
// ---------------------------------------------------------------------------

describe("EditableObjectContainer — popupShowing getter", () => {

   it("returns true when dataTipService.dataTipName is set", () => {
      const { comp, mocks } = makeComponent();
      mocks.dataTipService.dataTipName = "someTip";
      expect(comp.popupShowing).toBe(true);
   });

   it("returns true when adhocFilterService.adhocFilterShowing is true", () => {
      const { comp, mocks } = makeComponent();
      mocks.adhocFilterService.adhocFilterShowing = true;
      expect(comp.popupShowing).toBe(true);
   });

   it("returns false when both dataTipName is null and adhocFilterShowing is false", () => {
      const { comp, mocks } = makeComponent();
      mocks.dataTipService.dataTipName = null;
      mocks.adhocFilterService.adhocFilterShowing = false;
      expect(comp.popupShowing).toBe(false);
   });

   it("treats empty string dataTipName as falsy (false)", () => {
      const { comp, mocks } = makeComponent();
      mocks.dataTipService.dataTipName = "";
      mocks.adhocFilterService.adhocFilterShowing = false;
      expect(comp.popupShowing).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 2: showEdit getter [Risk 2]
// ---------------------------------------------------------------------------

describe("EditableObjectContainer — showEdit getter", () => {

   it("returns true for an empty VSText with no drag and no changedByScript", () => {
      const { comp, mocks } = makeComponent();
      const model = makeVsObjectModel("VSText", "T1") as any;
      model.empty = true;
      model.changedByScript = false;
      comp.viewsheet = comp.viewsheet;
      comp.vsObjectModel = model;
      mocks.dragService.currentlyDragging = false;

      expect(comp.showEdit).toBe(true);
   });

   it("returns false when empty is false", () => {
      const { comp, mocks } = makeComponent();
      const model = makeVsObjectModel("VSText", "T1") as any;
      model.empty = false;
      model.changedByScript = false;
      comp.vsObjectModel = model;
      mocks.dragService.currentlyDragging = false;

      expect(comp.showEdit).toBe(false);
   });

   it("returns false when dragService.currentlyDragging is true", () => {
      const { comp, mocks } = makeComponent();
      const model = makeVsObjectModel("VSText", "T1") as any;
      model.empty = true;
      model.changedByScript = false;
      comp.vsObjectModel = model;
      mocks.dragService.currentlyDragging = true;

      expect(comp.showEdit).toBe(false);
   });

   it("returns false when changedByScript is true", () => {
      const { comp, mocks } = makeComponent();
      const model = makeVsObjectModel("VSText", "T1") as any;
      model.empty = true;
      model.changedByScript = true;
      comp.vsObjectModel = model;
      mocks.dragService.currentlyDragging = false;

      expect(comp.showEdit).toBe(false);
   });

   it("returns false for empty VSChart when objectComponent.emptyChart is false", () => {
      const { comp, mocks } = makeComponent();
      const model = makeVsObjectModel("VSChart", "Ch1") as any;
      model.empty = true;
      model.changedByScript = false;
      comp.vsObjectModel = model;
      mocks.dragService.currentlyDragging = false;
      // objectComponent.emptyChart not set → undefined → falsy
      (comp as any).objectComponent = { resized: vi.fn(), emptyChart: false };

      expect(comp.showEdit).toBe(false);
   });

   it("returns true for empty VSChart when objectComponent.emptyChart is true", () => {
      const { comp, mocks } = makeComponent();
      const model = makeVsObjectModel("VSChart", "Ch1") as any;
      model.empty = true;
      model.changedByScript = false;
      comp.vsObjectModel = model;
      mocks.dragService.currentlyDragging = false;
      (comp as any).objectComponent = { resized: vi.fn(), emptyChart: true };

      expect(comp.showEdit).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 3: showLayoutOptionDialog (private) [Risk 3]
// ---------------------------------------------------------------------------

describe("EditableObjectContainer — showLayoutOptionDialog (private)", () => {

   function call(comp: EditableObjectContainer, objectType: string, entryProps: any, data: any) {
      comp.vsObject.objectType = objectType as any;
      const entry = { properties: entryProps };
      return (comp as any).showLayoutOptionDialog(entry, data);
   }

   it("returns true for VSTab regardless of entry/data", () => {
      const { comp } = makeComponent();
      expect(call(comp, "VSTab", {}, {})).toBe(true);
   });

   it("returns true when DIMENSION_FOLDER + non-container type", () => {
      const { comp } = makeComponent();
      expect(call(comp, "VSText", { DIMENSION_FOLDER: true }, {})).toBe(true);
   });

   it("returns false when DIMENSION_FOLDER + VSSelectionContainer", () => {
      const { comp } = makeComponent();
      expect(call(comp, "VSSelectionContainer", { DIMENSION_FOLDER: true }, {})).toBe(false);
   });

   it("returns true when data.table on a non-VSTable object", () => {
      const { comp } = makeComponent();
      expect(call(comp, "VSText", {}, { table: "some_table" })).toBe(true);
   });

   it("returns true when data.table on VSTable (no cube.column.type → last condition fires)", () => {
      // Even though the table-drop condition is blocked for VSTable,
      // the !cube.column.type && VSTable condition still fires → true
      const { comp } = makeComponent();
      expect(call(comp, "VSTable", {}, { table: "some_table" })).toBe(true);
   });

   it("returns false for VSTable when cube.column.type is set and no other condition applies", () => {
      const { comp } = makeComponent();
      // cube.column.type set → !cube.column.type is false; no other condition fires
      expect(call(comp, "VSTable", { "cube.column.type": "2" }, { table: "some_table" })).toBe(false);
   });

   it("returns true for VSCalendar with non-date dtype", () => {
      const { comp } = makeComponent();
      expect(call(comp, "VSCalendar", { dtype: "integer" }, {})).toBe(true);
   });

   it("returns false for VSCalendar with dtype=date", () => {
      const { comp } = makeComponent();
      expect(call(comp, "VSCalendar", { dtype: "date" }, {})).toBe(false);
   });

   it("returns true for VSTable without cube.column.type", () => {
      const { comp } = makeComponent();
      expect(call(comp, "VSTable", {}, {})).toBe(true);
   });

   it("returns true for VSChart without cube.column.type", () => {
      const { comp } = makeComponent();
      expect(call(comp, "VSChart", {}, {})).toBe(true);
   });

   it("returns false for VSText without matching conditions", () => {
      const { comp } = makeComponent();
      expect(call(comp, "VSText", { "cube.column.type": "1" }, {})).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 4: showSlideout [Risk 1]
// ---------------------------------------------------------------------------

describe("EditableObjectContainer — showSlideout", () => {

   it("calls dialogService.showSlideoutFor with vsObject.absoluteName", () => {
      const { comp, mocks } = makeComponent();
      comp.showSlideout();

      expect(mocks.dialogService.showSlideoutFor).toHaveBeenCalledWith(
         comp.vsObject.absoluteName,
      );
   });
});

// ---------------------------------------------------------------------------
// Group 5: onTitleResizeMove / onTitleResizeEnd boundary heights [Risk 1]
// ---------------------------------------------------------------------------

describe("EditableObjectContainer — onTitleResizeMove / onTitleResizeEnd boundary heights", () => {

   it("onTitleResizeMove with height=0 still delegates to adjustTitleHeight", () => {
      const { comp, mocks } = makeComponent();
      comp.onTitleResizeMove(0);
      expect(mocks.composerObjectService.adjustTitleHeight)
         .toHaveBeenCalledWith(comp.vsObject, 0);
   });

   it("onTitleResizeMove with negative height delegates unchanged", () => {
      const { comp, mocks } = makeComponent();
      comp.onTitleResizeMove(-5);
      expect(mocks.composerObjectService.adjustTitleHeight)
         .toHaveBeenCalledWith(comp.vsObject, -5);
   });

   it("onTitleResizeEnd always calls resizeObjectTitle regardless of object state", () => {
      const { comp, mocks } = makeComponent();
      comp.onTitleResizeEnd();
      expect(mocks.composerObjectService.resizeObjectTitle)
         .toHaveBeenCalledWith(comp.viewsheet, comp.vsObject);
   });
});
