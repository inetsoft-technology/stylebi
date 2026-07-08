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
 * ChartFieldmc — Pass 1: Interaction
 *
 * Risk-first coverage:
 *   Group 1 [Risk 2]  — field setter: clones _originalField for date-comparison rollback
 *   Group 2 [Risk 3]  — changeColumnValue: empty-dynamic guard, caption vs dynamic paths, aesthetic emit
 *   Group 3 [Risk 3]  — openChange(false): manual-order reset, date-comparison confirm/cancel, processChange
 *   Group 4 [Risk 3]  — processChange: binding sync via changeChartRef or onChangeAesthetic
 *   Group 5 [Risk 2]  — changeAggregateFormula: discrete none → Sum promotion
 *   Group 6 [Risk 2]  — changeChartRef: delegates field + fieldType to ChartEditorService
 *   Group 7 [Risk 3]  — convert: GraphUtil.convertChartRef + onConvert emit for both directions
 *   Group 8 [Risk 2]  — dragStart: ChartTransfer vs ChartAestheticTransfer by fieldType
 *
 * HTTP: no HTTP — binding editor local state only
 *
 * Out of scope this pass: cellLabel, cellValue, imgOpacity, isRefConvertEnabled,
 *   isSortSupported, isOuterDimRef, isVisibleChartTypeButton, geoBtnVisible, convertBtnTitle,
 *   getTitle, getFieldClassType, isEditMeasure, isChartRefMeasure, strippedDrillmemberVariables,
 *   isSecondaryAxisSupported, binding/chart getters
 *   (covered in chart-fieldmc.component.display.tl.spec.ts)
 */

import { ComponentTool } from "../../../../common/util/component-tool";
import { StyleConstants } from "../../../../common/util/style-constants";
import { Tool } from "../../../../../../../shared/util/tool";
import { TestUtils } from "../../../../common/test/test-utils";
import { ChartAestheticTransfer, ChartTransfer } from "../../../data/chart/chart-transfer";
import { GraphUtil } from "../../../util/graph-util";
import {
   chartFieldComboLabel,
   chartFieldComboValue,
   dcServiceMock,
   dndServiceMock,
   editorServiceMock,
   renderChartFieldmc,
} from "./chart-fieldmc.component.test-helpers";

afterEach(() => vi.restoreAllMocks());

describe("ChartFieldmc — Pass 1: Interaction", () => {

   describe("Group 1 — field setter [Risk 2]", () => {
      // 🔁 Regression-sensitive: _originalField must be a clone so date-comparison cancel can restore
      it("should clone the assigned field into _originalField", async () => {
         const field = TestUtils.createMockChartDimensionRef("region");
         const { fixture, container } = await renderChartFieldmc();

         fixture.componentInstance.field = field;
         field.name = "changed";
         fixture.detectChanges();

         expect(chartFieldComboValue(container)).toBe("region");
      });
   });

   describe("Group 2 — changeColumnValue [Risk 3]", () => {
      // 🔁 Regression-sensitive: empty dynamic "$" must not mutate columnValue or trigger binding update
      it("should skip empty dynamic values without calling changeChartRef", async () => {
         const field = TestUtils.createMockChartDimensionRef("state");
         const { fixture, container } = await renderChartFieldmc({ field, fieldType: "xfields" });

         fixture.componentInstance.changeColumnValue("$");

         expect(editorServiceMock.changeChartRef).not.toHaveBeenCalled();
         expect(chartFieldComboValue(container)).not.toBe("$");
      });

      it("should use caption for non-dynamic values when caption is set", async () => {
         const field = TestUtils.createMockChartDimensionRef("state");
         field.caption = "State Label";
         const { fixture } = await renderChartFieldmc({ field, fieldType: "xfields" });

         fixture.componentInstance.changeColumnValue("state");

         expect(field.columnValue).toBe("State Label");
         expect(editorServiceMock.changeChartRef).toHaveBeenCalledWith(field, "xfields");
      });

      it("should store dynamic values and call changeChartRef for axis fields", async () => {
         const field = TestUtils.createMockChartDimensionRef("state");
         const { fixture } = await renderChartFieldmc({ field, fieldType: "xfields" });

         fixture.componentInstance.changeColumnValue("${var1}");

         expect(field.columnValue).toBe("${var1}");
         expect(editorServiceMock.changeChartRef).toHaveBeenCalledWith(field, "xfields");
      });

      it("should emit onChangeAesthetic for all-chart aggregate fields", async () => {
         const field = TestUtils.createMockChartDimensionRef("city");
         const allAggr = TestUtils.createMockChartAggregateRef("sales");
         allAggr.classType = "allaggregate";
         const { fixture } = await renderChartFieldmc({
            field,
            fieldType: "color",
            currentAggr: allAggr,
         });
         const aestheticSpy = vi.spyOn(fixture.componentInstance.onChangeAesthetic, "emit");

         fixture.componentInstance.changeColumnValue("${var1}");

         expect(field.columnValue).toBe("${var1}");
         expect(aestheticSpy).toHaveBeenCalled();
         expect(editorServiceMock.changeChartRef).not.toHaveBeenCalled();
      });
   });

   describe("Group 3 — openChange [Risk 3]", () => {
      it("should reset manual specific order when manualOrder is empty", async () => {
         const field = TestUtils.createMockChartDimensionRef("state");
         field.order = StyleConstants.SORT_SPECIFIC;
         field.specificOrderType = "manual";
         field.manualOrder = [];
         const { fixture } = await renderChartFieldmc({ field, fieldType: "xfields" });

         fixture.componentInstance.openChange(false);

         expect(field.order).toBe(StyleConstants.SORT_NONE);
         expect(fixture.componentInstance.dropdown.close).toHaveBeenCalled();
      });

      it("should call processChange when date comparison is not affected", async () => {
         const field = TestUtils.createMockChartDimensionRef("state");
         field.specificOrderType = "manual";
         const { fixture, model } = await renderChartFieldmc({ field, fieldType: "xfields" });
         const agg = model.yfields[0] as any;
         agg.discrete = true;
         agg.formula = "none";
         const processChangeSpy = vi.spyOn(fixture.componentInstance, "processChange");

         fixture.componentInstance.openChange(false);

         expect(processChangeSpy).toHaveBeenCalled();
         expect(field.specificOrderType).toBeUndefined();
      });

      // 🔁 Regression-sensitive: confirm dialog accept must commit binding; cancel must restore _originalField
      it("should process change when date comparison confirm is accepted", async () => {
         const field = TestUtils.createMockChartDimensionRef("state");
         const { fixture, model } = await renderChartFieldmc({ field, fieldType: "xfields" });
         model.hasDateComparison = true;
         dcServiceMock.checkBindingField.mockReturnValue(true);
         vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("ok");
         const processChangeSpy = vi.spyOn(fixture.componentInstance, "processChange");
         field.name = "modified";

         fixture.componentInstance.openChange(false);
         await Promise.resolve();

         expect(ComponentTool.showConfirmDialog).toHaveBeenCalled();
         expect(processChangeSpy).toHaveBeenCalled();
      });

      it("should restore the original field when date comparison confirm is cancelled", async () => {
         const field = TestUtils.createMockChartDimensionRef("state");
         const { fixture, model, container } = await renderChartFieldmc({ field, fieldType: "xfields" });
         model.hasDateComparison = true;
         dcServiceMock.checkBindingField.mockReturnValue(true);
         vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("cancel");
         const processChangeSpy = vi.spyOn(fixture.componentInstance, "processChange");
         fixture.componentInstance.field = field;
         field.name = "modified";

         fixture.componentInstance.openChange(false);
         await Promise.resolve();

         expect(processChangeSpy).not.toHaveBeenCalled();
         expect(chartFieldComboLabel(container)).toBe("state");
      });
   });

   describe("Group 4 — processChange [Risk 3]", () => {
      it("should update binding and call changeChartRef when binding changed", async () => {
         const field = TestUtils.createMockChartAggregateRef("sales");
         field.discrete = true;
         field.formula = "none";
         const { fixture, model } = await renderChartFieldmc({
            field,
            fieldType: "yfields",
         });
         model.yfields = [field];

         fixture.componentInstance.processChange();

         expect(field.formula).toBe("Sum");
         expect(editorServiceMock.changeChartRef).toHaveBeenCalledWith(field, "yfields");
      });

      it("should emit onChangeAesthetic when all-chart aggregate binding changed", async () => {
         const field = TestUtils.createMockChartDimensionRef("city");
         const allAggr = TestUtils.createMockChartAggregateRef("sales");
         allAggr.classType = "allaggregate";
         allAggr.discrete = true;
         allAggr.formula = "none";
         const { fixture } = await renderChartFieldmc({
            field,
            fieldType: "color",
            currentAggr: allAggr,
         });
         const aestheticSpy = vi.spyOn(fixture.componentInstance.onChangeAesthetic, "emit");
         (fixture.componentInstance as any).obindingModel = "{}";

         fixture.componentInstance.processChange();

         expect(aestheticSpy).toHaveBeenCalled();
         expect(editorServiceMock.changeChartRef).not.toHaveBeenCalled();
      });
   });

   describe("Group 5 — changeAggregateFormula [Risk 2]", () => {
      it("should change discrete aggregate formula from none to Sum", async () => {
         const field = TestUtils.createMockChartAggregateRef("sales");
         field.discrete = true;
         field.formula = "none";
         const { fixture } = await renderChartFieldmc({ field, fieldType: "yfields" });

         fixture.componentInstance.changeAggregateFormula();

         expect(field.formula).toBe("Sum");
      });
   });

   describe("Group 6 — changeChartRef [Risk 2]", () => {
      it("should delegate to ChartEditorService with field and fieldType", async () => {
         const field = TestUtils.createMockChartDimensionRef("state");
         const { fixture } = await renderChartFieldmc({ field, fieldType: "xfields" });

         fixture.componentInstance.changeChartRef();

         expect(editorServiceMock.changeChartRef).toHaveBeenCalledWith(field, "xfields");
      });
   });

   describe("Group 7 — convert [Risk 3]", () => {
      it("should convert dimension to measure and emit onConvert", async () => {
         const field = TestUtils.createMockChartDimensionRef("state");
         const allAggr = TestUtils.createMockChartAggregateRef("sales");
         const { fixture } = await renderChartFieldmc({
            field,
            fieldType: "xfields",
            currentAggr: allAggr,
         });
         const convertSpy = vi.spyOn(GraphUtil, "convertChartRef");
         const emitSpy = vi.spyOn(fixture.componentInstance.onConvert, "emit");

         fixture.componentInstance.convert();

         expect(convertSpy).toHaveBeenCalled();
         expect(emitSpy).toHaveBeenCalledWith({
            name: field.dataRefModel.name,
            type: fixture.componentInstance.CONVERT_TO_MEASURE,
         });
      });

      it("should convert measure to dimension and emit onConvert", async () => {
         const field = TestUtils.createMockChartAggregateRef("sales");
         const allAggr = TestUtils.createMockChartAggregateRef("total");
         const { fixture } = await renderChartFieldmc({
            field,
            fieldType: "yfields",
            currentAggr: allAggr,
         });
         const emitSpy = vi.spyOn(fixture.componentInstance.onConvert, "emit");

         fixture.componentInstance.convert();

         expect(emitSpy).toHaveBeenCalledWith({
            name: field.dataRefModel.name,
            type: fixture.componentInstance.CONVERT_TO_DIMENSION,
         });
      });
   });

   describe("Group 8 — dragStart [Risk 2]", () => {
      it("should return early when field is missing", async () => {
         const { fixture } = await renderChartFieldmc({ fieldType: "xfields" });
         const setTransferSpy = vi.spyOn(Tool, "setTransferData");

         fixture.componentInstance.dragStart({ dataTransfer: {} });

         expect(setTransferSpy).not.toHaveBeenCalled();
      });

      it("should return early when drag type is unsupported", async () => {
         const field = TestUtils.createMockChartDimensionRef("state");
         const { fixture } = await renderChartFieldmc({ field, fieldType: "geofields" });
         const setTransferSpy = vi.spyOn(Tool, "setTransferData");

         fixture.componentInstance.dragStart({ dataTransfer: {} });

         expect(setTransferSpy).not.toHaveBeenCalled();
      });

      it("should set ChartTransfer for axis field types", async () => {
         const field = TestUtils.createMockChartDimensionRef("state");
         field.columnValue = "state";
         const { fixture } = await renderChartFieldmc({ field, fieldType: "xfields" });
         const setTransferSpy = vi.spyOn(Tool, "setTransferData").mockImplementation(() => {});

         fixture.componentInstance.dragStart({ dataTransfer: {} });

         expect(setTransferSpy).toHaveBeenCalled();
         const transfer = setTransferSpy.mock.calls[0][1].dragSource;
         expect(transfer).toBeInstanceOf(ChartTransfer);
         expect(transfer.refs).toEqual([field]);
         expect(dndServiceMock.setDragStartStyle).toHaveBeenCalledWith(
            expect.anything(),
            "state"
         );
      });

      it("should set ChartAestheticTransfer for aesthetic field types", async () => {
         const field = TestUtils.createMockChartDimensionRef("city");
         field.columnValue = "city";
         const allAggr = TestUtils.createMockChartAggregateRef("sales");
         const { fixture } = await renderChartFieldmc({
            field,
            fieldType: "color",
            currentAggr: allAggr,
            targetField: "colorField",
         });
         const setTransferSpy = vi.spyOn(Tool, "setTransferData").mockImplementation(() => {});

         fixture.componentInstance.dragStart({ dataTransfer: {} });

         const transfer = setTransferSpy.mock.calls[0][1].dragSource;
         expect(transfer).toBeInstanceOf(ChartAestheticTransfer);
         expect((transfer as ChartAestheticTransfer).targetField).toBe("colorField");
      });
   });
});
