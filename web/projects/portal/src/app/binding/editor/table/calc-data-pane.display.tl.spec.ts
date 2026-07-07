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
 * CalcDataPane — Pass 3: Display
 *
 * Risk-first coverage:
 *   Group 1 [Risk 2] — formulaValue / columnValue / textValue: binding-type branches
 *   Group 2 [Risk 2] — field / isCalcField / isGroupAggregateDisabled: field guards
 *   Group 3 [Risk 2] — expansionModel / isGroup / isSum: btype and expansion getters
 *   Group 4 [Risk 2] — cellBindingEnabled / cellSelected / cellGroupEnabled / isGrayedOut
 *   Group 5 [Risk 2] — checkDeleteKey: Delete and Ctrl+C/V/X/D stopPropagation paths
 *
 * HTTP: no HTTP — calc table editor local state only
 *
 * Out of scope this pass: bindingModel setter, columnValue setter, changeCellType, toggled
 *   (covered in calc-data-pane.interaction.tl.spec.ts)
 */

import { TestUtils } from "../../../common/test/test-utils";
import { CellBindingInfo } from "../../data/table/cell-binding-info";
import { DataRefType } from "../../../common/data/data-ref-type";
import {
   bindingServiceMock,
   createCellBinding,
   editorServiceMock,
   renderCalcDataPane,
} from "./calc-data-pane.component.test-helpers";

afterEach(() => vi.restoreAllMocks());

describe("CalcDataPane — Pass 3: Display", () => {

   describe("Group 1 — formulaValue / columnValue / textValue [Risk 2]", () => {
      it("should return cell script preview for column bindings", async () => {
         editorServiceMock.cellScript = "cell['state']";
         const { comp } = await renderCalcDataPane();

         expect(comp.formulaValue).toBe("cell['state']");
      });

      it("should return formula value for formula bindings", async () => {
         const cellBinding = createCellBinding();
         cellBinding.type = CellBindingInfo.BIND_FORMULA;
         cellBinding.value = "sum(field)";
         const { comp } = await renderCalcDataPane({ _cellBinding: cellBinding });

         expect(comp.formulaValue).toBe("sum(field)");
      });

      it("should return null formulaValue for text bindings", async () => {
         const cellBinding = createCellBinding();
         cellBinding.type = CellBindingInfo.BIND_TEXT;
         const { comp } = await renderCalcDataPane({ _cellBinding: cellBinding });

         expect(comp.formulaValue).toBeNull();
      });

      it("should return column value for column bindings", async () => {
         const columnBinding = createCellBinding();
         columnBinding.value = "state";
         const { comp } = await renderCalcDataPane({ _cellBinding: columnBinding });

         expect(comp.columnValue).toBe("state");
      });

      it("should return text value for text bindings", async () => {
         const textBinding = createCellBinding();
         textBinding.type = CellBindingInfo.BIND_TEXT;
         textBinding.value = "Label";
         const { comp } = await renderCalcDataPane({ _cellBinding: textBinding });

         expect(comp.textValue).toBe("Label");
      });
   });

   describe("Group 2 — field / isCalcField / isGroupAggregateDisabled [Risk 2]", () => {
      it("should expose selected field for column bindings", async () => {
         const { comp } = await renderCalcDataPane();

         expect(comp.field?.name).toBe("state");
      });

      it("should detect calculate fields", async () => {
         const field = TestUtils.createMockDataRef("Calc1");
         field.classType = "CalculateRef";
         const model = TestUtils.createMockCalcTableBindingModel();
         model.availableFields = [field];
         const cellBinding = createCellBinding();
         cellBinding.value = "Calc1";
         const { comp } = await renderCalcDataPane({ _model: model, _cellBinding: cellBinding });

         expect(comp.isCalcField()).toBe(true);
      });

      it("should disable group/summary for agg-calc fields", async () => {
         const field = TestUtils.createMockDataRef("Calc1");
         field.classType = "CalculateRef";
         field.refType = DataRefType.AGG_CALC;
         const model = TestUtils.createMockCalcTableBindingModel();
         model.availableFields = [field];
         const cellBinding = createCellBinding();
         cellBinding.value = "Calc1";
         editorServiceMock.getSelectCells.mockReturnValue([TestUtils.createMockCalcTableCell()]);
         const { comp } = await renderCalcDataPane({ _model: model, _cellBinding: cellBinding });

         expect(comp.isGroupAggregateDisabled()).toBe(true);
      });
   });

   describe("Group 3 — expansionModel / isGroup / isSum [Risk 2]", () => {
      it("should return true expansionModel when expansion is set", async () => {
         const cellBinding = createCellBinding();
         cellBinding.expansion = CellBindingInfo.EXPAND_V;
         const { comp } = await renderCalcDataPane({ _cellBinding: cellBinding });

         expect(comp.expansionModel).toBe(true);
      });

      it("should detect group btype", async () => {
         const groupBinding = createCellBinding();
         groupBinding.btype = CellBindingInfo.GROUP;
         const { comp } = await renderCalcDataPane({ _cellBinding: groupBinding });

         expect(comp.isGroup).toBe(true);
         expect(comp.isSum).toBe(false);
      });

      it("should detect summary btype", async () => {
         const sumBinding = createCellBinding();
         sumBinding.btype = CellBindingInfo.SUMMARY;
         const { comp } = await renderCalcDataPane({ _cellBinding: sumBinding });

         expect(comp.isSum).toBe(true);
      });
   });

   describe("Group 4 — cellBindingEnabled / cellSelected / isGrayedOut [Risk 2]", () => {
      it("should enable binding when cell names exist and cell is selected", async () => {
         editorServiceMock.getCellNamesWithDefaults.mockReturnValue([
            { label: "A1", value: "A1" },
         ]);
         editorServiceMock.getSelectCells.mockReturnValue([TestUtils.createMockCalcTableCell()]);
         const { comp } = await renderCalcDataPane();
         comp.getCellNames();

         expect(comp.cellBindingEnabled).toBe(true);
         expect(comp.cellSelected).toBe(true);
         expect(comp.cellGroupEnabled).toBe(true);
      });

      it("should delegate grayed-out check to binding service", async () => {
         bindingServiceMock.isGrayedOutField.mockReturnValue(true);
         const { comp } = await renderCalcDataPane();

         expect(comp.isGrayedOut("state")).toBe(true);
      });
   });

   describe("Group 5 — checkDeleteKey [Risk 2]", () => {
      it("should stop propagation for Delete key", async () => {
         const { comp } = await renderCalcDataPane();
         const event = { keyCode: 46, ctrlKey: false, stopPropagation: vi.fn() } as any;

         comp.checkDeleteKey(event);

         expect(event.stopPropagation).toHaveBeenCalled();
      });

      it("should stop propagation for Ctrl+C/V/X/D shortcuts", async () => {
         const { comp } = await renderCalcDataPane();
         const event = { keyCode: 67, ctrlKey: true, stopPropagation: vi.fn() } as any;

         comp.checkDeleteKey(event);

         expect(event.stopPropagation).toHaveBeenCalled();
      });

      it("should not stop propagation for unhandled keys", async () => {
         const { comp } = await renderCalcDataPane();
         const event = { keyCode: 65, ctrlKey: false, stopPropagation: vi.fn() } as any;

         comp.checkDeleteKey(event);

         expect(event.stopPropagation).not.toHaveBeenCalled();
      });
   });
});
