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
 * ChartFieldmc — Pass 3: Display
 *
 * Risk-first coverage:
 *   Group 1 [Risk 2]  — cellLabel: view / oriFullName / name fallback branches
 *   Group 2 [Risk 2]  — cellValue: null, caption, columnValue, dynamic, view fallback
 *   Group 3 [Risk 2]  — imgOpacity / isRefConvertEnabled: geo vs axis convert visibility
 *   Group 4 [Risk 2]  — isSortSupported: funnel last-y, geofields, default axis
 *   Group 5 [Risk 3]  — isOuterDimRef: xfields/yfields outer-dim and stock/candle edges
 *   Group 6 [Risk 3]  — isVisibleChartTypeButton: multistyle, discrete, dual-measure Y-source
 *   Group 7 [Risk 2]  — geoBtnVisible: geo chart axis measures vs geofield dimensions
 *   Group 8 [Risk 1]  — convertBtnTitle / getTitle / getFieldClassType: i18n label derivation
 *   Group 9 [Risk 2]  — isEditMeasure: cube measure SQL Server gate
 *   Group 10 [Risk 2] — strippedDrillmemberVariables / isChartRefMeasure: list filtering
 *   Group 11 [Risk 2] — isSecondaryAxisSupported: separated binding and Y-field placement
 *   Group 12 [Risk 1]  — binding/chart getters: bindingModel, params, chartType, multiStyles
 *
 * HTTP: no HTTP — binding editor local state only
 *
 * Out of scope this pass: field setter, changeColumnValue, openChange, processChange,
 *   changeAggregateFormula, changeChartRef, convert, dragStart
 *   (covered in chart-fieldmc.component.interaction.tl.spec.ts)
 */

import userEvent from "@testing-library/user-event";
import { GraphTypes } from "../../../../common/graph-types";
import { DataRefType } from "../../../../common/data/data-ref-type";
import { TestUtils } from "../../../../common/test/test-utils";
import { ChartAggregateRef } from "../../../data/chart/chart-aggregate-ref";
import {
   bindingServiceMock,
   chartFieldCombo,
   chartFieldComboLabel,
   chartFieldComboValue,
   chartFieldEditIcon,
   chartTypeButton,
   createChartModel,
   renderChartFieldmc,
   uiContextMock,
} from "./chart-fieldmc.component.test-helpers";

afterEach(() => vi.restoreAllMocks());

async function openDimensionEditor(container: HTMLElement) {
   const icon = chartFieldEditIcon(container);
   if(icon) {
      await userEvent.click(icon);
   }
}

describe("ChartFieldmc — Pass 3: Display", () => {

   describe("Group 1 — cellLabel [Risk 2]", () => {
      it("should return view when field view is set", async () => {
         const field = TestUtils.createMockChartAggregateRef("sales");
         field.view = "Sum(Sales)";
         const { container } = await renderChartFieldmc({ field });

         expect(chartFieldComboLabel(container)).toBe("Sum(Sales)");
      });

      it("should return oriFullName for measure fields without view", async () => {
         const field = TestUtils.createMockChartAggregateRef("sales");
         field.view = null;
         field.oriFullName = "Sum(sales)";
         const { container } = await renderChartFieldmc({ field });

         expect(chartFieldComboLabel(container)).toBe("Sum(sales)");
      });

      it("should return name for dimension fields without view", async () => {
         const field = TestUtils.createMockChartDimensionRef("state");
         field.view = null;
         const { container } = await renderChartFieldmc({ field });

         expect(chartFieldComboLabel(container)).toBe("state");
      });
   });

   describe("Group 2 — cellValue [Risk 2]", () => {
      it("should return empty string when field is null", async () => {
         const { container } = await renderChartFieldmc();

         expect(chartFieldCombo(container)).toBeFalsy();
      });

      it("should prefer caption for non-dynamic column values", async () => {
         const field = TestUtils.createMockChartDimensionRef("state");
         field.caption = "State Caption";
         field.columnValue = "state";
         const { container } = await renderChartFieldmc({ field });

         expect(chartFieldComboValue(container)).toBe("State Caption");
      });

      it("should use columnValue when caption is absent", async () => {
         const field = TestUtils.createMockChartDimensionRef("state");
         field.caption = null;
         field.columnValue = "state";
         const { container } = await renderChartFieldmc({ field });

         expect(chartFieldComboValue(container)).toBe("state");
      });

      // 🔁 Regression-sensitive: dynamic columnValue must bypass caption even when caption is set
      it("should use dynamic columnValue instead of caption", async () => {
         const field = TestUtils.createMockChartDimensionRef("state");
         field.caption = "State Caption";
         field.columnValue = "${var1}";
         const { container } = await renderChartFieldmc({ field });

         expect(chartFieldComboValue(container)).toBe("${var1}");
      });

      it("should fall back to view when column value is empty", async () => {
         const field = TestUtils.createMockChartDimensionRef("state");
         field.caption = null;
         field.columnValue = "";
         field.view = "State View";
         const { container } = await renderChartFieldmc({ field });

         expect(chartFieldComboValue(container)).toBe("State View");
      });
   });

   describe("Group 3 — imgOpacity / isRefConvertEnabled [Risk 2]", () => {
      it("should return reduced opacity for geofields", async () => {
         const field = TestUtils.createMockChartDimensionRef("state");
         const { comp } = await renderChartFieldmc({ field, fieldType: "geofields" });

         expect(comp.imgOpacity()).toBe(0.5);
      });

      it("should return full opacity when ref convert is enabled", async () => {
         const field = TestUtils.createMockChartDimensionRef("state");
         field.refConvertEnabled = true;
         const { comp, model } = await renderChartFieldmc({ field, fieldType: "yfields" });
         model.chartType = GraphTypes.CHART_BAR;

         expect(comp.imgOpacity()).toBe(1);
      });

      it("should disable convert for geofields", async () => {
         const field = TestUtils.createMockChartDimensionRef("lat");
         const { comp } = await renderChartFieldmc({ field, fieldType: "geofields" });

         expect(comp.isRefConvertEnabled(field, "geofields")).toBe(false);
      });

      it("should enable convert for yfields on bar charts", async () => {
         const field = TestUtils.createMockChartDimensionRef("state");
         const { comp, model } = await renderChartFieldmc({ field, fieldType: "yfields" });
         model.chartType = GraphTypes.CHART_BAR;

         expect(comp.isRefConvertEnabled(field, "yfields")).toBe(true);
      });
   });

   describe("Group 4 — isSortSupported [Risk 2]", () => {
      it("should disable sort for the last funnel y field", async () => {
         const model = createChartModel();
         model.chartType = GraphTypes.CHART_FUNNEL;
         model.yfields = [
            TestUtils.createMockChartDimensionRef("stage1"),
            TestUtils.createMockChartDimensionRef("stage2"),
         ];
         const field = model.yfields[1];
         const { container } = await renderChartFieldmc({
            _model: model,
            field,
            fieldType: "yfields",
            index: 1,
         });
         await openDimensionEditor(container);

         const editor = document.querySelector("dimension-editor");
         expect(editor?.getAttribute("ng-reflect-sort-supported")).toBe("false");
      });

      it("should disable sort for geofields", async () => {
         const field = TestUtils.createMockChartDimensionRef("lat");
         const { comp } = await renderChartFieldmc({ field, fieldType: "geofields" });

         expect(comp.isSortSupported()).toBe(false);
      });

      it("should enable sort for non-funnel axis fields", async () => {
         const field = TestUtils.createMockChartDimensionRef("state");
         const { container } = await renderChartFieldmc({ field, fieldType: "xfields", index: 0 });
         await openDimensionEditor(container);

         const editor = document.querySelector("dimension-editor");
         expect(editor?.getAttribute("ng-reflect-sort-supported")).toBe("true");
      });
   });

   describe("Group 5 — isOuterDimRef [Risk 3]", () => {
      it("should return true for non-last xfield dimensions", async () => {
         const model = createChartModel();
         const dim1 = TestUtils.createMockChartDimensionRef("state");
         const dim2 = TestUtils.createMockChartDimensionRef("city");
         model.xfields = [dim1, dim2];
         const { container } = await renderChartFieldmc({
            _model: model,
            field: dim1,
            fieldType: "xfields",
         });
         await openDimensionEditor(container);

         const editor = document.querySelector("dimension-editor");
         expect(editor?.getAttribute("ng-reflect-is-outer-dim-ref")).toBe("true");
      });

      it("should return false for the last xfield dimension", async () => {
         const model = createChartModel();
         const dim1 = TestUtils.createMockChartDimensionRef("state");
         const dim2 = TestUtils.createMockChartDimensionRef("city");
         model.xfields = [dim1, dim2];
         const { container } = await renderChartFieldmc({
            _model: model,
            field: dim2,
            fieldType: "xfields",
         });
         await openDimensionEditor(container);

         const editor = document.querySelector("dimension-editor");
         expect(editor?.getAttribute("ng-reflect-is-outer-dim-ref")).toBe("false");
      });

      it("should treat stock chart y dimensions as outer", async () => {
         const model = createChartModel();
         model.chartType = GraphTypes.CHART_STOCK;
         const dim = TestUtils.createMockChartDimensionRef("date");
         model.xfields = [];
         model.yfields = [dim];
         const { container } = await renderChartFieldmc({
            _model: model,
            field: dim,
            fieldType: "yfields",
         });
         await openDimensionEditor(container);

         const editor = document.querySelector("dimension-editor");
         expect(editor?.getAttribute("ng-reflect-is-outer-dim-ref")).toBe("true");
      });

      it("should treat candle chart y dimensions as outer", async () => {
         const model = createChartModel();
         model.chartType = GraphTypes.CHART_CANDLE;
         const dim = TestUtils.createMockChartDimensionRef("date");
         model.xfields = [];
         model.yfields = [dim];
         const { container } = await renderChartFieldmc({
            _model: model,
            field: dim,
            fieldType: "yfields",
         });
         await openDimensionEditor(container);

         const editor = document.querySelector("dimension-editor");
         expect(editor?.getAttribute("ng-reflect-is-outer-dim-ref")).toBe("true");
      });
   });

   describe("Group 6 — isVisibleChartTypeButton [Risk 3]", () => {
      it("should return false for non axis measure fields", async () => {
         const field = TestUtils.createMockChartAggregateRef("sales");
         const { container, model } = await renderChartFieldmc({
            field,
            fieldType: "groupfields",
         });
         model.multiStyles = true;

         expect(chartTypeButton(container)).toBeFalsy();
      });

      it("should return true for multistyle measure axis fields", async () => {
         const field = TestUtils.createMockChartAggregateRef("sales");
         const { comp, model } = await renderChartFieldmc({
            field,
            fieldType: "yfields",
         });
         model.multiStyles = true;

         expect(comp.isVisibleChartTypeButton()).toBe(true);
      });

      it("should return false when discrete is checked", async () => {
         const field = TestUtils.createMockChartAggregateRef("sales");
         field.discrete = true;
         const { container, model } = await renderChartFieldmc({
            field,
            fieldType: "yfields",
         });
         model.multiStyles = true;

         expect(chartTypeButton(container)).toBeFalsy();
      });

      it("should return false when multistyle is disabled", async () => {
         const field = TestUtils.createMockChartAggregateRef("sales");
         const { container, model } = await renderChartFieldmc({
            field,
            fieldType: "yfields",
         });
         model.multiStyles = false;

         expect(chartTypeButton(container)).toBeFalsy();
      });

      // 🔁 Regression-sensitive: dual-measure charts require original.source == "Y" for chart-type button
      it("should require original Y source when both axes contain measures", async () => {
         const xMeasure = TestUtils.createMockChartAggregateRef("qty");
         const yMeasure = TestUtils.createMockChartAggregateRef("sales");
         yMeasure.original = { source: "Y", index: 0, aggregateDesc: null };
         const model = createChartModel();
         model.multiStyles = true;
         model.xfields = [xMeasure];
         model.yfields = [yMeasure];
         const { container } = await renderChartFieldmc({
            _model: model,
            field: yMeasure,
            fieldType: "yfields",
         });

         expect(chartTypeButton(container)).toBeTruthy();
      });

      it("should hide chart type button when dual-measure field original source is X", async () => {
         const xMeasure = TestUtils.createMockChartAggregateRef("qty");
         const yMeasure = TestUtils.createMockChartAggregateRef("sales");
         yMeasure.original = { source: "X", index: 0, aggregateDesc: null };
         const model = createChartModel();
         model.multiStyles = true;
         model.xfields = [xMeasure];
         model.yfields = [yMeasure];
         const { container } = await renderChartFieldmc({
            _model: model,
            field: yMeasure,
            fieldType: "yfields",
         });

         expect(chartTypeButton(container)).toBeFalsy();
      });
   });

   describe("Group 7 — geoBtnVisible [Risk 2]", () => {
      it("should show geo button on axis measures for geo charts", async () => {
         const field = TestUtils.createMockChartAggregateRef("sales");
         const { container, model } = await renderChartFieldmc({
            field,
            fieldType: "yfields",
         });
         model.chartType = GraphTypes.CHART_MAP;

         expect(chartFieldEditIcon(container)).toBeTruthy();
      });

      it("should show geo button on geofields for dimensions", async () => {
         const field = TestUtils.createMockChartDimensionRef("lat");
         const { container } = await renderChartFieldmc({ field, fieldType: "geofields" });

         expect(chartFieldCombo(container)).toBeTruthy();
      });

      it("should hide geo button on non-geo axis measures", async () => {
         const field = TestUtils.createMockChartAggregateRef("sales");
         const { container, model } = await renderChartFieldmc({
            field,
            fieldType: "yfields",
         });
         model.chartType = GraphTypes.CHART_BAR;

         expect(chartFieldEditIcon(container)).toBeTruthy();
         expect(chartTypeButton(container)).toBeFalsy();
      });
   });

   describe("Group 8 — convertBtnTitle / getTitle / getFieldClassType [Risk 1]", () => {
      it("should prompt convert to measure for dimensions", async () => {
         const field = TestUtils.createMockChartDimensionRef("state");
         const { container } = await renderChartFieldmc({ field });

         expect(chartFieldEditIcon(container)?.classList.contains("dimension-setting-icon")).toBe(true);
      });

      it("should prompt convert to dimension for measures", async () => {
         const field = TestUtils.createMockChartAggregateRef("sales");
         const { container } = await renderChartFieldmc({ field });

         expect(chartFieldEditIcon(container)?.classList.contains("measure-setting-icon")).toBe(true);
      });

      it("should return edit dimension title for dimensions", async () => {
         const field = TestUtils.createMockChartDimensionRef("state");
         const { container } = await renderChartFieldmc({ field });

         expect(chartFieldEditIcon(container)?.getAttribute("title")).toBe("_#(js:Edit Dimension)");
      });

      it("should return edit measure title for measures", async () => {
         const field = TestUtils.createMockChartAggregateRef("sales");
         const { container } = await renderChartFieldmc({ field });

         expect(chartFieldEditIcon(container)?.getAttribute("title")).toBe("_#(js:Edit Measure)");
      });

      it("should classify dimensions correctly", async () => {
         const field = TestUtils.createMockChartDimensionRef("state");
         const { container } = await renderChartFieldmc({ field });

         expect(chartFieldEditIcon(container)?.classList.contains("dimension-setting-icon")).toBe(true);
      });

      it("should classify measures correctly", async () => {
         const field = TestUtils.createMockChartAggregateRef("sales");
         const { container } = await renderChartFieldmc({ field });

         expect(chartFieldEditIcon(container)?.classList.contains("measure-setting-icon")).toBe(true);
      });
   });

   describe("Group 9 — isEditMeasure [Risk 2]", () => {
      it("should allow editing when field is missing or not a measure", async () => {
         const { container } = await renderChartFieldmc();

         expect(chartFieldCombo(container)).toBeFalsy();
      });

      it("should allow editing cube measures on SQL Server", async () => {
         const field = TestUtils.createMockChartAggregateRef("sales");
         field.refType = DataRefType.CUBE_MEASURE;
         const { comp, model } = await renderChartFieldmc({ field });
         model.source = { ...model.source, sqlServer: true };

         expect(comp.isEditMeasure()).toBe(true);
      });

      it("should block editing cube measures when not SQL Server", async () => {
         const field = TestUtils.createMockChartAggregateRef("sales");
         field.refType = DataRefType.CUBE_MEASURE;
         const { container, model } = await renderChartFieldmc({ field });
         model.source = { ...model.source, sqlServer: false };

         expect(chartFieldEditIcon(container)).toBeFalsy();
      });
   });

   describe("Group 10 — strippedDrillmemberVariables / isChartRefMeasure [Risk 2]", () => {
      it("should filter out drillMember variables", async () => {
         const field = TestUtils.createMockChartDimensionRef("state");
         const { container } = await renderChartFieldmc({ field });
         bindingServiceMock.variableValues = [
            "var1",
            "region.drillMember",
            "var2",
         ];
         await openDimensionEditor(container);

         const editor = document.querySelector("dimension-editor");
         expect(editor?.getAttribute("ng-reflect-variables")).toBe("var1,var2");
      });

      it("should detect measure refs in a field list", async () => {
         const xMeasure = TestUtils.createMockChartAggregateRef("qty");
         const yMeasure = TestUtils.createMockChartAggregateRef("sales");
         yMeasure.original = { source: "Y", index: 0, aggregateDesc: null };
         const model = createChartModel();
         model.multiStyles = true;
         model.xfields = [xMeasure];
         model.yfields = [yMeasure];
         const { container } = await renderChartFieldmc({
            _model: model,
            field: yMeasure,
            fieldType: "yfields",
         });

         expect(chartTypeButton(container)).toBeTruthy();
      });
   });

   describe("Group 11 — isSecondaryAxisSupported [Risk 2]", () => {
      it("should return true for Y-field measures on supported chart types", async () => {
         const model = createChartModel();
         model.separated = false;
         model.rtchartType = GraphTypes.CHART_BAR;
         const agg = TestUtils.createMockChartAggregateRef("sales");
         model.yfields = [agg];
         const { container } = await renderChartFieldmc({
            _model: model,
            field: agg,
            fieldType: "yfields",
         });
         await userEvent.click(chartFieldEditIcon(container)!);

         const editor = document.querySelector("aggregate-editor");
         expect(editor?.getAttribute("ng-reflect-is-secondary-axis-supported")).toBe("true");
      });

      it("should return false when binding is separated", async () => {
         const model = createChartModel();
         model.separated = true;
         model.rtchartType = GraphTypes.CHART_BAR;
         const agg = TestUtils.createMockChartAggregateRef("sales");
         model.yfields = [agg];
         const { container } = await renderChartFieldmc({
            _model: model,
            field: agg,
            fieldType: "yfields",
         });
         await userEvent.click(chartFieldEditIcon(container)!);

         const editor = document.querySelector("aggregate-editor");
         expect(editor?.getAttribute("ng-reflect-is-secondary-axis-supported")).toBe("false");
      });
   });

   describe("Group 12 — binding/chart getters [Risk 1]", () => {
      it("should expose binding model, params, and aggregate field", async () => {
         const field = TestUtils.createMockChartAggregateRef("sales") as ChartAggregateRef;
         const allAggr = TestUtils.createMockChartAggregateRef("total");
         const { comp, model } = await renderChartFieldmc({
            field,
            currentAggr: allAggr,
         });
         model.multiStyles = true;
         model.stackMeasures = true;
         model.chartType = GraphTypes.CHART_LINE;
         allAggr.chartType = GraphTypes.CHART_BAR;

         expect(comp.multiStyles).toBe(true);
         expect(comp.stackMeasures).toBe(true);
         expect(comp.chartType).toBe(GraphTypes.CHART_BAR);
      });

      it("should fall back to binding chart type when currentAggr is absent", async () => {
         const field = TestUtils.createMockChartAggregateRef("sales");
         const { comp, model } = await renderChartFieldmc({ field });
         model.multiStyles = true;
         model.chartType = GraphTypes.CHART_PIE;

         expect(comp.chartType).toBe(GraphTypes.CHART_PIE);
      });
   });
});
