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
 * TableFieldmc — Pass 1: Interaction
 *
 * Risk-first coverage:
 *   Group 1 [Risk 2] — field setter: clones _originalField for rollback
 *   Group 2 [Risk 2] — changeColumnValue / changeAllRows: column and all-rows mutations
 *   Group 3 [Risk 3] — toggled(false): date-comparison confirm vs processChange
 *   Group 4 [Risk 2] — processChange: bindingUpdated + setBindingModel when changed
 *   Group 5 [Risk 2] — dragStart: TableTransfer payload
 *   Group 6 [Risk 1] — getSourceAttr / getAllRows / tableBindingModel getters
 *
 * HTTP: no HTTP — table binding editor local state only
 *
 * Out of scope this pass: cellValue, tooltip, showFieldOption, isEditEnable, isOuterDimRef,
 *   getAggregateFullName, syncAgg (covered in table-fieldmc.component.display.tl.spec.ts)
 */

import { ComponentTool } from "../../../common/util/component-tool";
import { BDimensionRef } from "../../data/b-dimension-ref";
import { Tool } from "../../../../../../shared/util/tool";
import { TestUtils } from "../../../common/test/test-utils";
import { TableTransfer } from "../../../common/data/dnd-transfer";
import {
   bindingServiceMock,
   createCrosstabModel,
   dcServiceMock,
   dndServiceMock,
   editorServiceMock,
   renderTableFieldmc,
} from "./table-fieldmc.component.test-helpers";

afterEach(() => vi.restoreAllMocks());

describe("TableFieldmc — Pass 1: Interaction", () => {

   describe("Group 1 — field setter [Risk 2]", () => {
      it("should clone assigned field into _originalField", async () => {
         const field = TestUtils.createMockBDimensionRef("state");
         const { comp } = await renderTableFieldmc();

         comp.field = field;
         field.name = "changed";

         expect((comp as any)._originalField.name).toBe("state");
      });
   });

   describe("Group 2 — changeColumnValue / changeAllRows [Risk 2]", () => {
      it("should skip empty dynamic values", async () => {
         const field = TestUtils.createMockBDimensionRef("state");
         field.columnValue = "state";
         const { comp } = await renderTableFieldmc({ field, fieldType: "details" });

         comp.changeColumnValue("$");

         expect(editorServiceMock.setBindingModel).not.toHaveBeenCalled();
      });

      it("should update column value and persist binding", async () => {
         const field = TestUtils.createMockBDimensionRef("state");
         field.columnValue = "state";
         const { comp } = await renderTableFieldmc({ field, fieldType: "details" });

         comp.changeColumnValue("region");

         expect((comp.field as BDimensionRef).columnValue).toBe("region");
         expect(editorServiceMock.setBindingModel).toHaveBeenCalled();
      });

      it("should add source to allRows when enabling all-row join", async () => {
         const field = TestUtils.createMockBDimensionRef("state");
         field.dataRefModel.entity = "Orders";
         const { comp, model } = await renderTableFieldmc({ field, fieldType: "details" });
         model.allRows = [];

         comp.changeAllRows(true);

         expect(model.allRows).toContain("Orders");
      });
   });

   describe("Group 3 — toggled [Risk 3]", () => {
      it("should call processChange when date comparison is not affected", async () => {
         const field = TestUtils.createMockBDimensionRef("state");
         const { comp } = await renderTableFieldmc({ field, fieldType: "groups" });
         const processSpy = vi.spyOn(comp, "processChange");

         comp.toggled(false);

         expect(processSpy).toHaveBeenCalled();
      });

      // 🔁 Regression-sensitive: crosstab date-comparison cancel must restore original field
      it("should restore field when date comparison confirm is cancelled", async () => {
         const field = TestUtils.createMockBDimensionRef("state");
         const crosstabModel = createCrosstabModel();
         crosstabModel.hasDateComparison = true;
         bindingServiceMock.objectType = "VSCrosstab";
         const { comp } = await renderTableFieldmc({
            _model: crosstabModel,
            field,
            fieldType: "rows",
         });
         dcServiceMock.checkBindingField.mockReturnValue(true);
         const confirmSpy = vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("cancel");
         comp.field = field;
         field.name = "modified";

         comp.toggled(false);
         await Promise.resolve();

         expect(confirmSpy).toHaveBeenCalled();
         expect(field.name).toBe("state");
      });
   });

   describe("Group 4 — processChange [Risk 2]", () => {
      it("should persist binding when model changed", async () => {
         const field = TestUtils.createMockBAggregateRef("sales");
         field.formula = "Sum";
         const { comp, model } = await renderTableFieldmc({ field, fieldType: "aggregates" });
         model.aggregates = [field];
         (comp as any).obindingModel = "{}";

         comp.processChange();

         expect(editorServiceMock.setBindingModel).toHaveBeenCalled();
      });
   });

   describe("Group 5 — dragStart [Risk 2]", () => {
      it("should set TableTransfer with field type and index", async () => {
         const field = TestUtils.createMockBDimensionRef("state");
         const { comp } = await renderTableFieldmc({ field, fieldType: "groups", dragIndex: 2 });
         const setTransferSpy = vi.spyOn(Tool, "setTransferData").mockImplementation(() => {});

         comp.dragStart({ dataTransfer: {} });

         const transfer = setTransferSpy.mock.calls[0][1].dragSource;
         expect(transfer).toBeInstanceOf(TableTransfer);
         expect(transfer.dragIndex).toBe(2);
         expect(dndServiceMock.setDragStartStyle).toHaveBeenCalled();
      });
   });

   describe("Group 6 — getters [Risk 1]", () => {
      it("should expose source attr, all rows, and table binding model", async () => {
         const { comp, model } = await renderTableFieldmc();

         expect(comp.getSourceAttr()).toBe(model.source);
         expect(comp.getAllRows()).toBe(model.allRows);
         expect(comp.tableBindingModel).toBe(model);
      });
   });
});
