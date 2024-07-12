/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { XSchema } from "../../../../common/data/xschema";
import { TestUtils } from "../../../../common/test/test-utils";
import { ChartAggregateRef } from "../../../data/chart/chart-aggregate-ref";
import { ChartBindingModel } from "../../../data/chart/chart-binding-model";
import { ChartFieldmc } from "./chart-fieldmc.component";

let createChartModel: () => ChartBindingModel = () => {
   let model = TestUtils.createMockChartBindingModel();
   model.xfields = [TestUtils.createMockChartDimensionRef("state")];
   model.yfields = [TestUtils.createMockChartAggregateRef("id")];
   return model;
};

describe("chart fieldmc component unit case", () => {
   let bindingService: any;
   let elem: any;
   let editorService: any;
   let dndService: any;
   let renderer: any;
   let uiContextService: any;
   let modalService: any;
   let dcService: any;
   let featureFlagsService: any;

   let chartFieldmc: ChartFieldmc;
   let model: ChartBindingModel;

   beforeEach(() => {
      bindingService = { getURLParams: jest.fn() };
      editorService = {
         bindingModel: null,
         changeChartRef: jest.fn(),
         getDNDType: jest.fn(),
         setBindingModel: jest.fn()
      };
      dndService = { setDragStartStyle: jest.fn() };
      uiContextService = {};
      elem = {};
      renderer = {};
      modalService = { open: jest.fn() };
      dcService = {};
      featureFlagsService = { isFeatureEnabled: jest.fn() };

      chartFieldmc = new ChartFieldmc(bindingService, editorService, dndService,
         modalService, dcService, renderer, featureFlagsService);
   });

   //Bug #19209
   it("only x/y filed show chart type button when multi style is true", () => {
      model = createChartModel();
      model.multiStyles = true;
      editorService.bindingModel = model;
      chartFieldmc.fieldType = "groupfields";
      expect(chartFieldmc.isVisibleChartTypeButton()).toBeFalsy();
   });

   //Bug #19201
   it("should keep variable selected on color field", () => {
      let colorfield = TestUtils.createMockChartDimensionRef("city");
      let colorFrame = Object.assign({
         clazz: "inetsoft.web.binding.model.graph.aesthetic.StaticColorModel",
      }, TestUtils.createMockVisualFrameModel("city"));
      model = createChartModel();
      model.multiStyles = true;
      model.colorField = {
         dataInfo: colorfield,
         frame: colorFrame,
         fullName: "city"
      };
      editorService.bindingModel = model;

      chartFieldmc.field = colorfield;
      chartFieldmc.fieldType = "color";
      let aggRef = TestUtils.createMockChartAggregateRef();
      aggRef.classType = "allaggregate";
      chartFieldmc.currentAggr = aggRef;
      chartFieldmc.currentAggr.colorField = {
         dataInfo: colorfield,
         frame: colorFrame,
         fullName: "city"
      };

      chartFieldmc.changeColumnValue("${var1}");

      expect(model.colorField.dataInfo.columnValue).toBe("${var1}");
      expect(chartFieldmc.field.columnValue).toBe("${var1}");
   });

   //Bug #19033, chart style button should be invisible when discrete is checked on
   //Bug #19343,chart style button should be invisible when dimension in y fields
   it("chart style button should be invisible when Discrete is checked on", () => {
      model = createChartModel();
      model.multiStyles = true;
      editorService.bindingModel = model;
      let agg: ChartAggregateRef = TestUtils.createMockChartAggregateRef("id");
      agg.discrete = true;
      chartFieldmc.field = agg;
      chartFieldmc.fieldType = "yfields";

      expect(chartFieldmc.isVisibleChartTypeButton()).toBeFalsy();

      //Bug #19343
      let dim = TestUtils.createMockChartDimensionRef("state");
      chartFieldmc.field = dim;
      chartFieldmc.fieldType = "yfields";

      expect(chartFieldmc.isVisibleChartTypeButton()).toBeFalsy();
   });

   //for Bug #19285
   it("should not change chartRef if the value is null", () => {
      let changeChartRef = jest.spyOn(chartFieldmc, "changeChartRef");
      let chartDim = TestUtils.createMockChartDimensionRef("state");
      chartDim.columnValue = "state";
      model = TestUtils.createMockChartBindingModel();
      model.xfields = [chartDim];
      editorService.bindingModel = model;
      chartFieldmc.field = chartDim;
      chartFieldmc.fieldType = "xfields";
      chartFieldmc.changeColumnValue(null);

      expect(changeChartRef).not.toHaveBeenCalled();
   });

   //Bug #21437, Bug #21282
   it("should display right tooltip", () => {
      //Bug #21437
      let aggr = TestUtils.createMockChartAggregateRef("customer_id");
      aggr.discrete = true;
      aggr.formula = "Sum";
      aggr.columnValue = "customer_id";
      aggr.fullName = "discrete_Sum(customer_id)";
      aggr.oriFullName = "Sum(customer_id)";
      aggr.oriView = "Sum(customer_id)";
      aggr.view = "Sum(customer_id)";
      chartFieldmc.field = aggr;

      expect(chartFieldmc.cellLabel).toEqual("Sum(customer_id)");

      //Bug #21282
      let aggr2 = TestUtils.createMockChartAggregateRef("orderno");
      aggr2.discrete = false;
      aggr2.formula = "Sum";
      aggr2.columnValue = "orderno";
      aggr2.fullName = "orderno";
      aggr2.oriFullName = "orderno";
      aggr2.oriView = "Sum(orderno)";
      aggr2.view = "Sum(orderno)";
      chartFieldmc.field = aggr2;

      expect(chartFieldmc.cellLabel).toEqual("Sum(orderno)");
   });
});
