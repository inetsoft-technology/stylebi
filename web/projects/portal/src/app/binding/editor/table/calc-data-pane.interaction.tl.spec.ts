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
 * CalcDataPane — Pass 1: Interaction
 *
 * Risk-first coverage:
 *   Group 1 [Risk 2] — bindingModel setter: valueList populated from availableFields
 *   Group 2 [Risk 3] — columnValue setter: empty clears binding; non-empty delegates to editor
 *   Group 3 [Risk 2] — setGroupType / setSumType / setExpansion*: btype and expansion mutations
 *   Group 4 [Risk 3] — changeCellType: formula loads script; text clears value
 *   Group 5 [Risk 2] — setCellName: duplicate name blocked via message dialog
 *   Group 6 [Risk 2] — toggled / toggleDropdown: deferred setCellBinding on close
 *
 * HTTP: no HTTP — calc table editor local state only
 *
 * Out of scope this pass: formulaValue getter, boolean getters, checkDeleteKey
 *   (covered in calc-data-pane.component.display.tl.spec.ts)
 *
 * Old spec ported:
 *   Bug #19835 changeCellType, #20138/#20248 duplicate name, #20843/#20845 expansion on column change
 */

import { ComponentTool } from "../../../common/util/component-tool";
import { XSchema } from "../../../common/data/xschema";
import { XConstants } from "../../../common/util/xconstants";
import { TestUtils } from "../../../common/test/test-utils";
import { CellBindingInfo } from "../../data/table/cell-binding-info";
import { DataRefType } from "../../../common/data/data-ref-type";
import {
   editorServiceMock,
   renderCalcDataPane,
} from "./calc-data-pane.component.test-helpers";

afterEach(() => vi.restoreAllMocks());

describe("CalcDataPane — Pass 1: Interaction", () => {

   describe("Group 1 — bindingModel setter [Risk 2]", () => {
      it("should populate valueList from available fields", async () => {
         const { comp } = await renderCalcDataPane();

         expect(comp.valueList.length).toBeGreaterThan(1);
         expect(comp.valueList.some(v => v.value === "state")).toBe(true);
      });
   });

   describe("Group 2 — columnValue setter [Risk 3]", () => {
      it("should clear binding when column value is empty", async () => {
         const { comp, cellBinding } = await renderCalcDataPane();

         comp.columnValue = "";

         expect(cellBinding.type).toBe(CellBindingInfo.BIND_TEXT);
         expect(cellBinding.value).toBeNull();
         expect(editorServiceMock.setCellBinding).toHaveBeenCalled();
      });

      it("should delegate non-empty column changes to editor service", async () => {
         const { comp } = await renderCalcDataPane();

         comp.columnValue = "sales";

         expect(editorServiceMock.changeColumnValue).toHaveBeenCalledWith("sales");
      });
   });

   describe("Group 3 — setGroupType / setSumType / setExpansion [Risk 2]", () => {
      it("should set group btype and date order for date fields", async () => {
         const field = TestUtils.createMockDataRef("orderDate");
         field.dataType = XSchema.DATE;
         const model = TestUtils.createMockCalcTableBindingModel();
         model.availableFields = [field];
         const cellBinding = TestUtils.createMockCellBindingInfo("orderDate");
         cellBinding.type = CellBindingInfo.BIND_COLUMN;
         cellBinding.value = "orderDate";
         cellBinding.order = { option: 0, interval: 1 } as any;
         const { comp } = await renderCalcDataPane({ _model: model, _cellBinding: cellBinding });

         comp.setGroupType({ target: { checked: true } });

         expect(cellBinding.btype).toBe(CellBindingInfo.GROUP);
         expect(cellBinding.order.option).toBe(XConstants.YEAR_DATE_GROUP);
      });

      it("should set summary formula and clear expansion when checked", async () => {
         const { comp, cellBinding } = await renderCalcDataPane();
         cellBinding.expansion = CellBindingInfo.EXPAND_V;

         comp.setSumType({ target: { checked: true } });

         expect(cellBinding.btype).toBe(CellBindingInfo.SUMMARY);
         expect(cellBinding.expansion).toBe(CellBindingInfo.EXPAND_NONE);
         expect(cellBinding.formula).toBeTruthy();
      });

      it("should update expansion from setExpansionValue", async () => {
         const { comp, cellBinding } = await renderCalcDataPane();

         comp.setExpansionValue({ target: { checked: true } });

         expect(cellBinding.expansion).toBe(CellBindingInfo.DETAIL);
      });
   });

   describe("Group 4 — changeCellType [Risk 3]", () => {
      // 🔁 Regression-sensitive (Bug #19835): formula type must load cell script
      it("should switch to formula binding and load script", async () => {
         const { comp, cellBinding } = await renderCalcDataPane();

         comp.changeCellType(String(CellBindingInfo.BIND_FORMULA));

         expect(cellBinding.type).toBe(CellBindingInfo.BIND_FORMULA);
         expect(editorServiceMock.loadCellScript).toHaveBeenCalled();
      });

      it("should clear value when switching to text binding", async () => {
         const { comp, cellBinding } = await renderCalcDataPane();

         comp.changeCellType(String(CellBindingInfo.BIND_TEXT));

         expect(cellBinding.type).toBe(CellBindingInfo.BIND_TEXT);
         expect(cellBinding.value).toBe("");
      });
   });

   describe("Group 5 — setCellName [Risk 2]", () => {
      // 🔁 Regression-sensitive (Bug #20138/#20248): duplicate cell names must warn
      it("should show duplicate name dialog for existing cell names", async () => {
         editorServiceMock.getCellNames.mockReturnValue(["state", "city"]);
         vi.spyOn(ComponentTool, "showMessageDialog").mockResolvedValue("ok");
         const { comp } = await renderCalcDataPane();

         comp.setCellName("state");

         expect(ComponentTool.showMessageDialog).toHaveBeenCalledWith(
            expect.anything(),
            "_#(js:Error)",
            "_#(js:common.duplicateName)"
         );
      });
   });

   describe("Group 6 — toggled / toggleDropdown [Risk 2]", () => {
      it("should defer setCellBinding when dropdown closes", async () => {
         vi.useFakeTimers();
         const { comp } = await renderCalcDataPane();
         comp.dropDownIndex = 0;

         comp.toggled(false);
         vi.runAllTimers();

         expect(editorServiceMock.setCellBinding).toHaveBeenCalled();
         vi.useRealTimers();
      });

      it("should store dropdown index from toggleDropdown", async () => {
         const { comp } = await renderCalcDataPane();

         comp.toggleDropdown(2);

         expect(comp.dropDownIndex).toBe(2);
      });
   });
});
