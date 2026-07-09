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
 * TableFieldmc — Pass 3: Display
 *
 * Risk-first coverage:
 *   Group 1 [Risk 2] — cellValue / tooltip: caption, dynamic, and implyDynamic branches
 *   Group 2 [Risk 2] — showFieldOption / isAllRows: join-source guards and all-row detection
 *   Group 3 [Risk 2] — isEditEnable: details/cube/SQL Server gates
 *   Group 4 [Risk 2] — isOuterDimRef / getSource / getIndex: outer dimension and source resolution
 *   Group 5 [Risk 1] — getFieldClassType / getTitle / getFieldName: label derivation
 *
 * HTTP: no HTTP — table binding editor local state only
 *
 * Out of scope this pass: field setter, changeColumnValue, toggled, processChange, dragStart,
 *   openFieldOption dialog template (covered in table-fieldmc.component.interaction.tl.spec.ts)
 */

import { ComboMode } from "../../../widget/dynamic-combo-box/dynamic-combo-box-model";
import { TestUtils } from "../../../common/test/test-utils";
import {
   bindingServiceMock,
   createCrosstabModel,
   createTableBindingModel,
   renderTableFieldmc,
   uiContextMock,
} from "./table-fieldmc.component.test-helpers";

afterEach(() => vi.restoreAllMocks());

describe("TableFieldmc — Pass 3: Display", () => {

   describe("Group 1 — cellValue / tooltip [Risk 2]", () => {
      it("should prefer caption for non-dynamic dimension values", async () => {
         const field = TestUtils.createMockBDimensionRef("state");
         field.caption = "State Caption";
         field.columnValue = "state";
         const { comp } = await renderTableFieldmc({ field, fieldType: "groups" });

         expect(comp.cellValue).toBe("State Caption");
      });

      it("should return view in tooltip when set", async () => {
         const field = TestUtils.createMockBDimensionRef("state");
         field.view = "State View";
         const { comp } = await renderTableFieldmc({ field });

         expect(comp.tooltip).toBe("State View");
      });

      it("should use column value for implyDynamic expression combo", async () => {
         const field = TestUtils.createMockBDimensionRef("state");
         field.comboType = ComboMode.EXPRESSION;
         field.columnValue = "${var1}";
         const { comp } = await renderTableFieldmc({ field, implyDynamic: true, fieldType: "groups" });

         expect(comp.cellValue).toBe("${var1}");
      });
   });

   describe("Group 2 — showFieldOption / isAllRows [Risk 2]", () => {
      it("should hide field option when source lacks join support", async () => {
         const model = createTableBindingModel();
         model.source = { source: "Orders", type: 0, supportFullOutJoin: false } as any;
         const field = TestUtils.createMockBDimensionRef("state");
         const { comp } = await renderTableFieldmc({ _model: model, field });

         expect(comp.showFieldOption()).toBe(false);
      });

      it("should detect all-rows mode from source index", async () => {
         const field = TestUtils.createMockBDimensionRef("state");
         field.dataRefModel.entity = "Orders";
         const model = createTableBindingModel();
         model.allRows = ["Orders"];
         const { comp } = await renderTableFieldmc({ _model: model, field });

         expect(comp.isAllRows).toBe(true);
         expect(comp.getIndex("Orders")).toBe(0);
      });
   });

   describe("Group 3 — isEditEnable [Risk 2]", () => {
      it("should disable edit for details fields", async () => {
         const field = TestUtils.createMockBDimensionRef("state");
         const { comp } = await renderTableFieldmc({ field, fieldType: "details" });

         expect(comp.isEditEnable()).toBe(false);
      });

      it("should disable cube aggregates when not SQL Server", async () => {
         const model = createTableBindingModel();
         model.source = {
            source: "___inetsoft_cube_test",
            type: 1,
         } as any;
         const field = TestUtils.createMockBAggregateRef("sales");
         const { comp } = await renderTableFieldmc({ _model: model, field, fieldType: "aggregates" });

         expect(comp.isEditEnable()).toBe(false);
      });

      it("should allow cube aggregates on SQL Server", async () => {
         uiContextMock.isSqlServer.mockReturnValue(true);
         const model = createTableBindingModel();
         model.source = {
            source: "___inetsoft_cube_test",
            type: 1,
            sqlServer: true,
         } as any;
         const field = TestUtils.createMockBAggregateRef("sales");
         const { comp } = await renderTableFieldmc({ _model: model, field, fieldType: "aggregates" });

         expect(comp.isEditEnable()).toBe(true);
      });
   });

   describe("Group 4 — isOuterDimRef / getSource [Risk 2]", () => {
      it("should treat non-last row dimension as outer", async () => {
         const model = createCrosstabModel();
         const dim1 = TestUtils.createMockBDimensionRef("state");
         const dim2 = TestUtils.createMockBDimensionRef("city");
         model.rows = [dim1, dim2];
         const { comp } = await renderTableFieldmc({ _model: model, field: dim1, fieldType: "rows" });

         expect(comp.isOuterDimRef()).toBe(true);
      });

      it("should resolve source from entity or binding source", async () => {
         const field = TestUtils.createMockBDimensionRef("state");
         field.dataRefModel.entity = "Orders";
         const { comp } = await renderTableFieldmc({ field });

         expect(comp.getSource()).toBe("Orders");
      });
   });

   describe("Group 5 — getFieldClassType / getTitle / getFieldName [Risk 1]", () => {
      it("should classify dimension fields", async () => {
         const dim = TestUtils.createMockBDimensionRef("state");
         const { comp } = await renderTableFieldmc({ field: dim });

         expect(comp.getFieldClassType()).toBe("Dimension");
         expect(comp.getTitle()).toBe("_#(js:Edit Dimension)");
      });

      it("should classify aggregate fields", async () => {
         const agg = TestUtils.createMockBAggregateRef("sales");
         const { comp } = await renderTableFieldmc({ field: agg });

         expect(comp.getFieldClassType()).toBe("Measure");
         expect(comp.getTitle()).toBe("_#(js:Edit Measure)");
      });

      it("should return field view as field name", async () => {
         const field = TestUtils.createMockBDimensionRef("state");
         field.view = "State Label";
         const { comp } = await renderTableFieldmc({ field });

         expect(comp.getFieldName()).toBe("State Label");
      });
   });
});
