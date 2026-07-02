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
 * DimensionEditor — single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — ngOnInit: disables timeSeries when unsupported; loads date level examples
 *   Group 2 [Risk 3] — dateLevelChange: clears manualOrder on specific sort change
 *   Group 3 [Risk 2] — dateLevel getter: variable, expression, and value paths
 *   Group 4 [Risk 2] — applyClick: emits apply and resets manual sort when empty
 *   Group 5 [Risk 2] — timeSeriesSupported: true/false by field type and date level
 *   Group 6 [Risk 2] — isTimeVisible: false for waterfall chart
 *   Group 7 [Risk 2] — isOtherSupported / isRankingSupported: chart-type gates
 *   Group 8 [Risk 2] — bindingModel setter: clears timeSeries when time not visible
 *
 * Old spec ported (Risk 2):
 *   Bug #20091: dateLevel getter preserves variable value $(RadioButton1)
 *   Bug #19825: dateLevel getter preserves expression value ={'1'}
 *
 * Out of scope:
 *   Sort combobox disabled DOM state — delegates to SortOption child component.
 *   DynamicComboBox level dropdown items — external widget; covered by dateLevel getter logic.
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { render } from "@testing-library/angular";
import { of } from "rxjs";
import { XSchema } from "../../../../common/data/xschema";
import { GraphTypes } from "../../../../common/graph-types";
import { UIContextService } from "../../../../common/services/ui-context.service";
import { DateLevelExamplesService } from "../../../../common/services/date-level-examples.service";
import { TestUtils } from "../../../../common/test/test-utils";
import { StyleConstants } from "../../../../common/util/style-constants";
import { XConstants } from "../../../../common/util/xconstants";
import { ChartBindingModel } from "../../../data/chart/chart-binding-model";
import { ChartDimensionRef } from "../../../data/chart/chart-dimension-ref";
import { BindingService } from "../../../services/binding.service";
import { DimensionEditor } from "./dimension-editor.component";

const bindingServiceMock = {
   assemblyName: "Chart1",
   getURLParams: vi.fn(),
};
const uiContextMock = { isAdhoc: vi.fn().mockReturnValue(false) };
const examplesServiceMock = {
   loadDateLevelExamples: vi.fn().mockReturnValue(of({ dateLevelExamples: [] })),
};

function createDateDimension(name = "orderdate"): ChartDimensionRef {
   const dim = TestUtils.createMockChartDimensionRef(name);
   dim.dataType = XSchema.DATE;
   dim.dateLevel = XConstants.YEAR_DATE_GROUP + "";
   dim.timeSeries = false;
   return dim;
}

function createBindingModel(): ChartBindingModel {
   const model = TestUtils.createMockChartBindingModel();
   model.chartType = GraphTypes.CHART_BAR;
   model.rtchartType = GraphTypes.CHART_BAR;
   return model;
}

async function renderComponent(props: Record<string, any> = {}) {
   const dimension: ChartDimensionRef = props.dimension ?? createDateDimension();
   const bindingModel: ChartBindingModel = props.bindingModel ?? createBindingModel();

   const { fixture } = await render(DimensionEditor, {
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
         { provide: BindingService, useValue: bindingServiceMock },
         { provide: UIContextService, useValue: uiContextMock },
         { provide: DateLevelExamplesService, useValue: examplesServiceMock },
      ],
      componentProperties: {
         dimension,
         bindingModel,
         fieldType: "xfields",
         isOuterDimRef: false,
         sortSupported: true,
         vsId: "chart-test",
         ...props,
      },
   });
   fixture.detectChanges();
   await fixture.whenStable();
   return { fixture, comp: fixture.componentInstance as DimensionEditor, dimension, bindingModel };
}

beforeEach(() => {
   examplesServiceMock.loadDateLevelExamples.mockReturnValue(of({ dateLevelExamples: [] }));
});

afterEach(() => vi.restoreAllMocks());

// ---------------------------------------------------------------------------
// Group 1: ngOnInit [Risk 3]
// ---------------------------------------------------------------------------

describe("DimensionEditor — ngOnInit", () => {
   it("should disable timeSeries on init when time series is not supported", async () => {
      const dimension = createDateDimension();
      dimension.timeSeries = true;
      const { comp } = await renderComponent({
         dimension,
         fieldType: "path",
      });

      expect(comp.dimension.timeSeries).toBe(false);
   });

   it("should load date level examples on init", async () => {
      const dimension = createDateDimension();
      await renderComponent({ dimension });

      expect(examplesServiceMock.loadDateLevelExamples).toHaveBeenCalledWith(
         expect.arrayContaining([XConstants.YEAR_DATE_GROUP + ""]),
         XSchema.DATE,
      );
   });
});

// ---------------------------------------------------------------------------
// Group 2: dateLevelChange [Risk 3]
// ---------------------------------------------------------------------------

describe("DimensionEditor — dateLevelChange", () => {
   it("should clear manualOrder and downgrade specific sort when date level changes", async () => {
      const dimension = createDateDimension();
      dimension.order = StyleConstants.SORT_SPECIFIC;
      dimension.manualOrder = ["A", "B"];
      const { comp } = await renderComponent({ dimension });

      comp.dateLevelChange(XConstants.MONTH_DATE_GROUP + "");

      expect(dimension.manualOrder).toBeNull();
      expect(dimension.order).toBe(StyleConstants.SORT_ASC);
      expect(dimension.dateLevel).toBe(XConstants.MONTH_DATE_GROUP + "");
   });

   it("should preserve old timeSeries when date level change keeps time series supported", async () => {
      const dimension = createDateDimension();
      dimension.timeSeries = true;
      const { comp } = await renderComponent({ dimension });
      (comp as any).oldTimeSeries = true;

      comp.dateLevelChange(XConstants.MONTH_DATE_GROUP + "");

      expect(dimension.timeSeries).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 3: dateLevel getter [Risk 2]
// ---------------------------------------------------------------------------

describe("DimensionEditor — dateLevel getter", () => {
   // 🔁 Regression-sensitive (Bug #20091): variable date levels must pass through unchanged.
   it("should return variable date level unchanged", async () => {
      const dimension = createDateDimension();
      dimension.dateLevel = "$(RadioButton1)";
      const { comp } = await renderComponent({ dimension });

      expect(comp.dateLevel).toBe("$(RadioButton1)");
   });

   // 🔁 Regression-sensitive (Bug #19825): expression date levels must pass through unchanged.
   it("should return expression date level unchanged", async () => {
      const dimension = createDateDimension();
      dimension.dateLevel = "={'1'}";
      const { comp } = await renderComponent({ dimension });

      expect(comp.dateLevel).toBe("={'1'}");
   });

   it("should fall back to first option when stored value is not in options", async () => {
      const dimension = createDateDimension();
      dimension.dateLevel = "999";
      const { comp } = await renderComponent({ dimension });

      expect(comp.dateLevel).toBe(comp.dateLevelOpts[0].value);
   });

   it("should return stored value when it exists in date level options", async () => {
      const dimension = createDateDimension();
      dimension.dateLevel = XConstants.MONTH_DATE_GROUP + "";
      const { comp } = await renderComponent({ dimension });

      expect(comp.dateLevel).toBe(XConstants.MONTH_DATE_GROUP + "");
   });
});

// ---------------------------------------------------------------------------
// Group 4: applyClick [Risk 2]
// ---------------------------------------------------------------------------

describe("DimensionEditor — applyClick", () => {
   it("should emit apply and reset manual sort when manual order is empty", async () => {
      const dimension = createDateDimension();
      dimension.order = StyleConstants.SORT_SPECIFIC;
      dimension.specificOrderType = "manual";
      dimension.manualOrder = [];
      const { comp } = await renderComponent({ dimension });
      const emitted: boolean[] = [];
      comp.apply.subscribe(v => emitted.push(v));

      comp.applyClick();

      expect(emitted).toEqual([false]);
      expect(dimension.order).toBe(StyleConstants.SORT_NONE);
   });

   it("should clear timeSeries on apply when time is not visible", async () => {
      const dimension = createDateDimension();
      dimension.timeSeries = true;
      const bindingModel = createBindingModel();
      bindingModel.chartType = GraphTypes.CHART_WATERFALL;
      bindingModel.rtchartType = GraphTypes.CHART_WATERFALL;
      const { comp } = await renderComponent({ dimension, bindingModel });

      comp.applyClick();

      expect(dimension.timeSeries).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 5: timeSeriesSupported [Risk 2]
// ---------------------------------------------------------------------------

describe("DimensionEditor — timeSeriesSupported", () => {
   it("should return true for date dimension on xfields with datetime level", async () => {
      const dimension = createDateDimension();
      dimension.dateLevel = XConstants.DAY_DATE_GROUP + "";
      const { comp } = await renderComponent({ dimension, fieldType: "xfields" });

      expect(comp.timeSeriesSupported()).toBe(true);
   });

   it("should return false for path field type", async () => {
      const dimension = createDateDimension();
      const { comp } = await renderComponent({ dimension, fieldType: "path" });

      expect(comp.timeSeriesSupported()).toBe(false);
   });

   it("should return false when date level is none", async () => {
      const dimension = createDateDimension();
      dimension.dateLevel = XConstants.NONE_DATE_GROUP + "";
      const { comp } = await renderComponent({ dimension, fieldType: "xfields" });

      expect(comp.timeSeriesSupported()).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 6: isTimeVisible [Risk 2]
// ---------------------------------------------------------------------------

describe("DimensionEditor — isTimeVisible", () => {
   it("should return false for waterfall chart type", async () => {
      const bindingModel = createBindingModel();
      bindingModel.chartType = GraphTypes.CHART_WATERFALL;
      bindingModel.rtchartType = GraphTypes.CHART_WATERFALL;
      const { comp } = await renderComponent({ bindingModel });

      expect(comp.isTimeVisible()).toBe(false);
   });

   it("should return true for standard bar chart", async () => {
      const { comp } = await renderComponent();

      expect(comp.isTimeVisible()).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 7: isOtherSupported / isRankingSupported [Risk 2]
// ---------------------------------------------------------------------------

describe("DimensionEditor — isOtherSupported and isRankingSupported", () => {
   it("should return false from isOtherSupported for outer dimension ref", async () => {
      const { comp } = await renderComponent({ isOuterDimRef: true });

      expect(comp.isOtherSupported()).toBe(false);
   });

   it("should return false from isOtherSupported for relation chart type", async () => {
      const bindingModel = createBindingModel();
      bindingModel.chartType = GraphTypes.CHART_TREE;
      bindingModel.rtchartType = GraphTypes.CHART_TREE;
      const { comp } = await renderComponent({ bindingModel });

      expect(comp.isOtherSupported()).toBe(false);
   });

   it("should return true from isOtherSupported for standard bar chart", async () => {
      const { comp } = await renderComponent();

      expect(comp.isOtherSupported()).toBe(true);
   });

   it("should return false from isRankingSupported for boxplot chart", async () => {
      const bindingModel = createBindingModel();
      bindingModel.chartType = GraphTypes.CHART_BOXPLOT;
      bindingModel.rtchartType = GraphTypes.CHART_BOXPLOT;
      const { comp } = await renderComponent({ bindingModel });

      expect(comp.isRankingSupported()).toBe(false);
   });

   it("should return true from isRankingSupported for standard bar chart", async () => {
      const { comp } = await renderComponent();

      expect(comp.isRankingSupported()).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 8: bindingModel setter [Risk 2]
// ---------------------------------------------------------------------------

describe("DimensionEditor — bindingModel setter", () => {
   it("should clear timeSeries when binding model chart type hides time series", async () => {
      const dimension = createDateDimension();
      dimension.timeSeries = true;
      const bindingModel = createBindingModel();
      bindingModel.chartType = GraphTypes.CHART_WATERFALL;
      bindingModel.rtchartType = GraphTypes.CHART_WATERFALL;
      const { comp } = await renderComponent({ dimension, bindingModel });

      comp.bindingModel = bindingModel;

      expect(dimension.timeSeries).toBe(false);
   });
});
