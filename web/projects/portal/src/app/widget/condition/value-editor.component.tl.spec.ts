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
 * ValueEditor — single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — ngOnChanges: default date/boolean emission
 *   Group 2 [Risk 2] — browseData subscribe, getBrowseDataList mapping
 *   Group 3 [Risk 2] — selectValues / isSelected date transform, isBrowseEnabled guards
 *
 * Direct instantiation — child value editors not rendered.
 */

import { of, throwError } from "rxjs";
import { BrowseDataModel } from "../../common/data/browse-data-model";
import { ConditionOperation } from "../../common/data/condition/condition-operation";
import { DataRef } from "../../common/data/data-ref";
import { XSchema } from "../../common/data/xschema";
import { ValueEditor } from "./value-editor.component";

function createEditor() {
   return new ValueEditor();
}

function fieldChanges(comp: ValueEditor, attribute: string) {
   comp.ngOnChanges({
      field: {
         previousValue: null,
         currentValue: { attribute, fakeNone: false, classType: "ColumnRef" } as DataRef,
         firstChange: true,
         isFirstChange: () => true,
      },
   });
}

describe("ValueEditor — ngOnChanges defaults [Group 1, Risk 3]", () => {

   it("should emit default date value when type is DATE and value is null", () => {
      const comp = createEditor();
      comp.type = XSchema.DATE;
      comp.operation = ConditionOperation.EQUAL_TO;
      comp.value = null;
      const emitSpy = vi.spyOn(comp.valueChange, "emit");

      comp.ngOnChanges({
         type: {
            previousValue: null,
            currentValue: XSchema.DATE,
            firstChange: true,
            isFirstChange: () => true,
         },
      });

      expect(emitSpy).toHaveBeenCalled();
      expect(String(emitSpy.mock.calls[0][0])).toMatch(/\d/);
   });

   it("should not emit default date for DATE_IN operation", () => {
      const comp = createEditor();
      comp.type = XSchema.DATE;
      comp.operation = ConditionOperation.DATE_IN;
      comp.value = null;
      const emitSpy = vi.spyOn(comp.valueChange, "emit");

      comp.ngOnChanges({
         type: {
            previousValue: null,
            currentValue: XSchema.DATE,
            firstChange: true,
            isFirstChange: () => true,
         },
         operation: {
            previousValue: null,
            currentValue: ConditionOperation.DATE_IN,
            firstChange: true,
            isFirstChange: () => true,
         },
      });

      expect(emitSpy).not.toHaveBeenCalled();
   });

   it("should emit false when boolean type has null value", () => {
      const comp = createEditor();
      comp.type = XSchema.BOOLEAN;
      comp.value = null;
      const emitSpy = vi.spyOn(comp.valueChange, "emit");

      comp.ngOnChanges({
         type: {
            previousValue: null,
            currentValue: XSchema.BOOLEAN,
            firstChange: true,
            isFirstChange: () => true,
         },
      });

      expect(emitSpy).toHaveBeenCalledWith("false");
   });
});

describe("ValueEditor — browse data [Group 2, Risk 2]", () => {

   it("should map browse model values and labels into dataList", () => {
      const comp = createEditor();
      comp.dataFunction = () => of({
         values: ["v1", "v2"],
         labels: ["L1", "L2"],
         dataTruncated: true,
      } as BrowseDataModel);
      const openSpy = vi.spyOn(comp.openBrowse, "emit");

      comp.browseData();

      expect(openSpy).toHaveBeenCalled();
      expect(comp.dataList).toEqual([
         { label: "L1", value: "v1" },
         { label: "L2", value: "v2" },
      ]);
      expect(comp.dataListTruncated).toBe(true);
      expect(comp.loadingDataList).toBe(false);
   });

   it("should set error flag when browse request fails", () => {
      const comp = createEditor();
      comp.dataFunction = () => throwError(() => new Error("fail"));

      comp.browseData();

      expect(comp.error).toBe(true);
      expect(comp.loadingDataList).toBe(false);
   });

   it("should return empty list from getBrowseDataList when model has no values", () => {
      const comp = createEditor();

      expect(comp.getBrowseDataList(null)).toEqual([]);
      expect(comp.getBrowseDataList({ values: [] } as BrowseDataModel)).toEqual([]);
   });
});

describe("ValueEditor — selection and browse guards [Group 3, Risk 2]", () => {

   it("should emit valueChange when browse item is selected", () => {
      const comp = createEditor();
      const emitSpy = vi.spyOn(comp.valueChange, "emit");

      comp.selectData({ label: "East", value: "E" });

      expect(comp.value).toBe("E");
      expect(emitSpy).toHaveBeenCalledWith("E");
   });

   it("should toggle multi-select values and strip field entries", () => {
      const comp = createEditor();
      comp.values = [{ classType: "ColumnRef", attribute: "f1" }];
      const emitSpy = vi.spyOn(comp.valueChanges, "emit");
      const checked = { target: { checked: true, value: "v2" } };

      comp.selectValues(checked);

      expect(comp.values).toEqual(["v2"]);
      expect(emitSpy).toHaveBeenCalledWith(["v2"]);
   });

   it("should match date values after stripping format wrapper in isSelected", () => {
      const comp = createEditor();
      comp.type = XSchema.DATE;
      comp.values = ["{d '2024-01-15'}"];

      expect(comp.isSelected("2024-01-15")).toBe(true);
      expect(comp.isSelected("2024-01-16")).toBe(false);
   });

   it("should disable browse for CalculateRef fields", () => {
      const comp = createEditor();
      comp.field = { classType: "CalculateRef", fakeNone: false } as DataRef;
      comp.enableBrowseData = true;
      comp.dataList = [{ label: "a", value: "a" }];
      comp.type = XSchema.STRING;
      comp.operation = ConditionOperation.EQUAL_TO;

      expect(comp.isBrowseEnabled()).toBe(false);
   });

   it("should reset dataList when field attribute changes in one-of mode", () => {
      const comp = createEditor();
      comp.isOneOf = true;
      comp.dataFunction = () => of({ values: [] } as BrowseDataModel);
      comp.dataList = [{ label: "x", value: "x" }];
      fieldChanges(comp, "colA");

      comp.ngOnChanges({
         field: {
            previousValue: { attribute: "colA" } as DataRef,
            currentValue: { attribute: "colB" } as DataRef,
            firstChange: false,
            isFirstChange: () => false,
         },
      });

      expect(comp.dataList).toEqual([]);
   });
});
