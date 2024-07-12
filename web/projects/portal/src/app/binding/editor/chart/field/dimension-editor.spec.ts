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
import { HttpParams, HttpResponse } from "@angular/common/http";
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { async, ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { NgbModal, NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { of as observableOf } from "rxjs";
import { XSchema } from "../../../../common/data/xschema";
import { GraphTypes } from "../../../../common/graph-types";
import { UIContextService } from "../../../../common/services/ui-context.service";
import { DropDownTestModule } from "../../../../common/test/test-module";
import { TestUtils } from "../../../../common/test/test-utils";
import { StyleConstants } from "../../../../common/util/style-constants";
import { XConstants } from "../../../../common/util/xconstants";
import { FixedDropdownDirective } from "../../../../widget/fixed-dropdown/fixed-dropdown.directive";
import { DynamicComboBox } from "../../../../widget/dynamic-combo-box/dynamic-combo-box.component";
import { ModelService } from "../../../../widget/services/model.service";
import { ChartBindingModel } from "../../../data/chart/chart-binding-model";
import { ChartDimensionRef } from "../../../data/chart/chart-dimension-ref";
import { BindingService } from "../../../services/binding.service";
import { SortOption } from "../../sort-option.component";
import { DimensionEditor } from "./dimension-editor.component";
import { DateLevelExamplesService } from "../../../../common/services/date-level-examples.service";
import { FeatureFlagsService } from "../../../../../../../shared/feature-flags/feature-flags.service";

describe("Dimension Editor Unit Test", () => {
   let chartBindingModel: ChartBindingModel;
   let mockDateDimensionRef: (name?: string) => ChartDimensionRef = (name?: string) => {
      let dimRef = TestUtils.createMockChartDimensionRef(name);
      dimRef.dataType = XSchema.DATE;
      return dimRef;
   };

   let bindingService = { getURLParams: jest.fn(() => new HttpParams()) };
   let modelService = {
      getModel: jest.fn(() => observableOf([])),
      putModel: jest.fn(() => observableOf(new HttpResponse({body: null})))
   };
   let uiContextService = { isAdhoc: jest.fn() };
   let examplesService = { loadDateLevelExamples: jest.fn(() => observableOf())};
   let featureFlagsService = { isFeatureEnabled: jest.fn() };

   let fixture: ComponentFixture<DimensionEditor>;
   let dimensionEditor: DimensionEditor;

   beforeEach(async(() => {
      TestBed.configureTestingModule({
         imports: [
            FormsModule, ReactiveFormsModule, NgbModule, DropDownTestModule
         ],
         declarations: [
            DimensionEditor, SortOption, DynamicComboBox, FixedDropdownDirective
         ],
         providers: [
            { provide: ModelService, useValue: modelService },
            { provide: BindingService, useValue: bindingService },
            { provide: UIContextService, useValue: uiContextService },
            { provide: DateLevelExamplesService, useValue: examplesService},
            { provide: FeatureFlagsService, useValue: featureFlagsService },
            NgbModal
         ],
         schemas: [NO_ERRORS_SCHEMA]
      });
      TestBed.compileComponents();
   }));

   //for Bug #16072, Bug #19552 Ranking should be disable only one col in G pane.
   it("Sort combobox should be disable in G pane", () => {
      uiContextService.isAdhoc.mockImplementation(() => false);
      fixture = TestBed.createComponent(DimensionEditor);
      dimensionEditor = <DimensionEditor>fixture.componentInstance;
      let dimRef = TestUtils.createMockChartDimensionRef("city");
      dimensionEditor.dimension = dimRef;
      chartBindingModel = TestUtils.createMockChartBindingModel();
      chartBindingModel.geoFields = [dimRef];
      dimensionEditor.bindingModel = chartBindingModel;
      dimensionEditor.sortSupported = false;
      dimensionEditor.fieldType = "geofields";
      dimensionEditor.vsId = "chart-15042540419170";
      fixture.detectChanges();

      let sortCombo: Element = fixture.nativeElement.querySelector(".popup-editor__container .sort_label_id select");
      expect(sortCombo.attributes["ng-reflect-is-disabled"].value).toEqual("true");

      dimensionEditor.dimension.dataType = XSchema.DATE;
      dimensionEditor.fieldType = "path";
      dimensionEditor.sortSupported = true;
      fixture.detectChanges();

      sortCombo = fixture.nativeElement.querySelector(".popup-editor__container .sort_label_id select");
      let timeSeries: HTMLInputElement = fixture.nativeElement.querySelector(".time_series_id");
      expect(sortCombo.attributes["ng-reflect-is-disabled"].value).toEqual("false");
      expect(timeSeries.attributes["ng-reflect-is-disabled"].value).toEqual("true");
   });

   //Bug #16925
   it("'As time series' status not right", () => {
      uiContextService.isAdhoc.mockImplementation(() => false);
      let chartDim = mockDateDimensionRef("date1");
      chartDim.dateLevel = XConstants.YEAR_DATE_GROUP + "";
      fixture = TestBed.createComponent(DimensionEditor);
      dimensionEditor = <DimensionEditor>fixture.componentInstance;
      dimensionEditor.dimension = chartDim;
      chartBindingModel = TestUtils.createMockChartBindingModel();
      chartBindingModel.xfields = [chartDim];
      dimensionEditor.bindingModel = chartBindingModel;
      dimensionEditor.isOuterDimRef = false;
      dimensionEditor.fieldType = "xfields";
      fixture.detectChanges();

      let asTimeCheckbox: HTMLInputElement = fixture.nativeElement.querySelector(".popup-editor__container .time_series_id");
      expect(asTimeCheckbox).not.toBeNull();
      expect(asTimeCheckbox.attributes["ng-reflect-is-disabled"].value).toEqual("false");

      dimensionEditor.timeSeries = true;
      fixture.detectChanges();

      asTimeCheckbox = fixture.nativeElement.querySelector(".popup-editor__container .time_series_id");
      expect(asTimeCheckbox.attributes["ng-reflect-model"].value).toEqual("true");
   });

   //Bug #16925
   it("'As time series' should not show up on special chart", () => {
      uiContextService.isAdhoc.mockImplementation(() => false);
      let chartDim = mockDateDimensionRef("orderdate");
      chartDim.dateLevel = XConstants.YEAR_DATE_GROUP + "";
      let aestheticInfo = TestUtils.createMockAestheticInfo("orderdate");
      aestheticInfo.dataInfo = chartDim;
      chartBindingModel = TestUtils.createMockChartBindingModel();
      chartBindingModel.colorField = aestheticInfo;
      chartBindingModel.chartType = GraphTypes.CHART_WATERFALL;
      chartBindingModel.rtchartType = GraphTypes.CHART_WATERFALL;
      fixture = TestBed.createComponent(DimensionEditor);
      dimensionEditor = <DimensionEditor>fixture.componentInstance;
      dimensionEditor.dimension = chartDim;
      dimensionEditor.bindingModel = chartBindingModel;
      dimensionEditor.fieldType = "color";
      fixture.detectChanges();

      let asTimeLabel: Element = fixture.nativeElement.querySelector(".popup-editor__container .time_series_id");
      expect(asTimeLabel).toBeNull();

     chartBindingModel.chartType = GraphTypes.CHART_CANDLE;
     chartBindingModel.rtchartType = GraphTypes.CHART_CANDLE;
     chartBindingModel.textField = aestheticInfo;
     dimensionEditor.fieldType = "text";
     fixture.detectChanges();
     asTimeLabel = fixture.nativeElement.querySelector(".popup-editor__container .time_series_id");
     expect(asTimeLabel).not.toBeNull();

     chartBindingModel.chartType = GraphTypes.CHART_PIE;
     chartBindingModel.rtchartType = GraphTypes.CHART_PIE;
     chartBindingModel.sizeField = aestheticInfo;
     dimensionEditor.fieldType = "size";
     fixture.detectChanges();
     asTimeLabel = fixture.nativeElement.querySelector(".popup-editor__container .time_series_id");
     expect(asTimeLabel).toBeNull();
   });

   //for Bug #19825 Time type level combobox load error
   // bad test, don't test external components and use debug elements to test dom when necessary
   // it("Test date type level combobox load", (done) => {
   //    uiContextService.isAdhoc.mockImplementation(() => false);
   //    let chartDim = mockDateDimensionRef("date1");
   //    fixture = TestBed.createComponent(DimensionEditor);
   //    dimensionEditor = <DimensionEditor>fixture.componentInstance;
   //    dimensionEditor.dimension = chartDim;
   //    chartBindingModel = TestUtils.createMockChartBindingModel();
   //    chartBindingModel.xfields = [chartDim];
   //    dimensionEditor.bindingModel = chartBindingModel;
   //    dimensionEditor.fieldType = "xfields";
   //    fixture.detectChanges();
   //
   //    let levelCombo: HTMLElement = TestUtils.getDynamicComboDiv(fixture.nativeElement.querySelector(".level_id"));
   //    levelCombo.click();
   //    fixture.detectChanges();
   //
   //    fixture.whenStable().then(() => {
   //       let fixedDropdown = document.getElementsByTagName("fixed-dropdown")[0];
   //       let levelItems = fixedDropdown.querySelectorAll("div.dropdown-container .dropdown-item");
   //       let noneItem = Array.prototype.slice.call(levelItems).filter(e => e.textContent == "None")[0];
   //       expect(levelItems.length).toEqual(11);
   //       expect(TestUtils.toString(levelItems[0].textContent.trim())).toEqual("Year");
   //       expect(noneItem).not.toBeNull();
   //
   //       done();
   //    });
   // });

   //for Bug #16180, 'Hour' should not missing from time level combobox
   // bad test, don't test external components and use debug elements to test dom when necessary
   // it("'Hour' should not missing from time level combobox", (done) => {
   //    uiContextService.isAdhoc.mockImplementation(() => false);
   //    let dimRef = TestUtils.createMockChartDimensionRef("time1");
   //    dimRef.dataType = XSchema.TIME;
   //    fixture = TestBed.createComponent(DimensionEditor);
   //    dimensionEditor = <DimensionEditor>fixture.componentInstance;
   //    dimensionEditor.dimension = dimRef;
   //    chartBindingModel = TestUtils.createMockChartBindingModel();
   //    chartBindingModel.xfields = [dimRef];
   //    dimensionEditor.bindingModel = chartBindingModel;
   //    dimensionEditor.fieldType = "xfields";
   //    fixture.detectChanges();
   //
   //    let levelCombo: HTMLElement = TestUtils.getDynamicComboDiv(fixture.nativeElement.querySelector(".level_id"));
   //    levelCombo.click();
   //    fixture.detectChanges();
   //
   //    fixture.whenStable().then(() => {
   //       let fixedDropdown = document.getElementsByTagName("fixed-dropdown")[0];
   //       let levelItems = fixedDropdown.querySelectorAll("div.dropdown-container .dropdown-item");
   //       let hourItem = Array.prototype.slice.call(levelItems).filter(e => e.textContent == "Hour")[0];
   //       expect(levelItems.length).toEqual(5);
   //       expect(TestUtils.toString(levelItems[levelItems.length - 1].textContent.trim())).toEqual("None");
   //       expect(hourItem).not.toBeNull();
   //
   //       done();
   //    });
   // });

   it("Test sort status when date group as time series", () => {
      uiContextService.isAdhoc.mockImplementation(() => false);
      let dimRef = mockDateDimensionRef("orderdate");
      let agg: any = [{
         label: "Sum(id)",
         value: "Sum(id)"
      }];
      dimRef.sortOptionModel.aggregateRefs = agg;
      dimRef.timeSeries = true;
      dimRef.dateLevel = XConstants.YEAR_DATE_GROUP + "";
      fixture = TestBed.createComponent(DimensionEditor);
      dimensionEditor = <DimensionEditor>fixture.componentInstance;
      dimensionEditor.dimension = dimRef;
      chartBindingModel = TestUtils.createMockChartBindingModel();
      chartBindingModel.xfields = [dimRef];
      chartBindingModel.yfields = [TestUtils.createMockChartAggregateRef("id")];
      dimensionEditor.bindingModel = chartBindingModel;
      dimensionEditor.sortSupported = true;
      dimensionEditor.fieldType = "xfields";
      fixture.detectChanges();

      let sortSelect: Element = fixture.nativeElement.querySelector(".popup-editor__container .sort_label_id select");
      expect(sortSelect.getAttribute("ng-reflect-is-disabled")).toBe("true");

      dimensionEditor.dimension.order = StyleConstants.SORT_VALUE_ASC;
      fixture.detectChanges();
      let sortSummaryCombo: Element = fixture.nativeElement.querySelector(".popup-editor__container .sort_by_id dynamic-combo-box");
      expect(sortSummaryCombo.getAttribute("ng-reflect-disable")).toEqual("true");

      dimensionEditor.dimension.timeSeries = false;
      fixture.detectChanges();

      sortSelect = fixture.nativeElement.querySelector(".popup-editor__container .sort_label_id select");
      sortSummaryCombo = fixture.nativeElement.querySelector(".popup-editor__container .sort_by_id dynamic-combo-box");
      expect(sortSelect.getAttribute("ng-reflect-is-disabled")).toEqual("false");
      expect(sortSummaryCombo.getAttribute("ng-reflect-disable")).toEqual("false");
   });

   //Bug #19273, group all other should be enabled. no longer valid, fix was incorrect
   xit("Group all other checkbox should be enabled", () => {
      uiContextService.isAdhoc.mockImplementation(() => false);
      let dimRef = TestUtils.createMockChartDimensionRef("instock");
      let agg: any = [{
         label: "Sum(id)",
         value: "Sum(id)"
      }];
      dimRef.sortOptionModel.aggregateRefs = agg;
      dimRef.rankingOption = StyleConstants.TOP_N + "";
      chartBindingModel = TestUtils.createMockChartBindingModel();
      chartBindingModel.xfields = [TestUtils.createMockChartDimensionRef("state")];
      chartBindingModel.yfields = [TestUtils.createMockChartAggregateRef("id")];
      chartBindingModel.groupFields = [dimRef];
      fixture = TestBed.createComponent(DimensionEditor);
      dimensionEditor = <DimensionEditor>fixture.componentInstance;
      dimensionEditor.dimension = dimRef;
      dimensionEditor.bindingModel = chartBindingModel;
      dimensionEditor.fieldType = "groupfields";
      fixture.detectChanges();

      let groupOther: HTMLInputElement = fixture.nativeElement.querySelector(".group_other_id input[type=checkbox]");
      expect(groupOther.getAttribute("ng-reflect-is-disabled")).toEqual("false");

      chartBindingModel.pathField = dimRef;
      dimensionEditor.fieldType = "path";
      fixture.detectChanges();
      groupOther = fixture.nativeElement.querySelector(".group_other_id input[type=checkbox]");
      expect(groupOther.getAttribute("ng-reflect-is-disabled")).toEqual("false");
   });

   //for Bug #19825, Bug #19909, Bug #20091
   // bad test, don't test external components and use debug elements to test dom when necessary
   // it("Should load default value in level combobox", () => {
   //    uiContextService.isAdhoc.mockImplementation(() => false);
   //    let dimRef = mockDateDimensionRef("date1");
   //    chartBindingModel = TestUtils.createMockChartBindingModel();
   //    chartBindingModel.xfields = [dimRef];
   //    fixture = TestBed.createComponent(DimensionEditor);
   //    dimensionEditor = <DimensionEditor>fixture.componentInstance;
   //    dimensionEditor.dimension = dimRef;
   //    dimensionEditor.dimension.dateLevel = null;
   //    dimensionEditor.bindingModel = chartBindingModel;
   //    dimensionEditor.fieldType = "xfields";
   //    fixture.detectChanges();
   //
   //    //Bug #19825
   //    const levelCombo: HTMLElement = TestUtils.getDynamicComboDiv(fixture.nativeElement.querySelector(".level_id"));
   //    expect(TestUtils.toString(levelCombo.textContent.trim())).toEqual("Year");
   // });

   //for Bug #19825, Bug #19909, Bug #20091
   // bad test, don't test external components and use debug elements to test dom when necessary
   // it("Should load default time value in level combobox", () => {
   //    uiContextService.isAdhoc.mockImplementation(() => false);
   //    let dimRef = mockDateDimensionRef("date1");
   //    chartBindingModel = TestUtils.createMockChartBindingModel();
   //    chartBindingModel.xfields = [dimRef];
   //    fixture = TestBed.createComponent(DimensionEditor);
   //    dimensionEditor = <DimensionEditor>fixture.componentInstance;
   //    dimensionEditor.dimension = dimRef;
   //    dimensionEditor.dimension.dateLevel = null;
   //    dimensionEditor.dimension.dataType = XSchema.TIME;
   //    dimensionEditor.bindingModel = chartBindingModel;
   //    dimensionEditor.fieldType = "xfields";
   //    fixture.detectChanges();
   //
   //    // Bug #19909
   //    const levelCombo: HTMLElement = TestUtils.getDynamicComboDiv(fixture.nativeElement.querySelector(".level_id"));
   //    expect(TestUtils.toString(levelCombo.textContent.trim())).toEqual("Hour");
   // });

   //for Bug #19825, Bug #19909, Bug #20091
   // bad test, don't test external components and use debug elements to test dom when necessary
   // it("Should load default time instant value in level combobox", () => {
   //    uiContextService.isAdhoc.mockImplementation(() => false);
   //    let dimRef = mockDateDimensionRef("date1");
   //    chartBindingModel = TestUtils.createMockChartBindingModel();
   //    chartBindingModel.xfields = [dimRef];
   //    fixture = TestBed.createComponent(DimensionEditor);
   //    dimensionEditor = <DimensionEditor>fixture.componentInstance;
   //    dimensionEditor.dimension = dimRef;
   //    dimensionEditor.dimension.dateLevel = null;
   //    dimensionEditor.dimension.dataType = XSchema.TIME_INSTANT;
   //    dimensionEditor.bindingModel = chartBindingModel;
   //    dimensionEditor.fieldType = "xfields";
   //    fixture.detectChanges();
   //
   //    const levelCombo: HTMLElement = TestUtils.getDynamicComboDiv(fixture.nativeElement.querySelector(".level_id"));
   //    expect(TestUtils.toString(levelCombo.textContent.trim())).toEqual("Year");
   // });

   //for Bug #19825, Bug #19909, Bug #20091
   it("Should load default variable value in level combobox", () => {
      uiContextService.isAdhoc.mockImplementation(() => false);
      let dimRef = mockDateDimensionRef("date1");
      chartBindingModel = TestUtils.createMockChartBindingModel();
      chartBindingModel.xfields = [dimRef];
      fixture = TestBed.createComponent(DimensionEditor);
      dimensionEditor = <DimensionEditor>fixture.componentInstance;
      dimensionEditor.dimension = dimRef;
      dimensionEditor.dimension.dateLevel = "$(RadioButton1)";
      dimensionEditor.bindingModel = chartBindingModel;
      dimensionEditor.fieldType = "xfields";
      fixture.detectChanges();

      //Bug #20091
      expect(dimensionEditor.dateLevel).toEqual("$(RadioButton1)");
   });

   //for Bug #19825, Bug #19909, Bug #20091
   it("Should load default expression value in level combobox", () => {
      uiContextService.isAdhoc.mockImplementation(() => false);
      let dimRef = mockDateDimensionRef("date1");
      chartBindingModel = TestUtils.createMockChartBindingModel();
      chartBindingModel.xfields = [dimRef];
      fixture = TestBed.createComponent(DimensionEditor);
      dimensionEditor = <DimensionEditor>fixture.componentInstance;
      dimensionEditor.dimension = dimRef;
      dimensionEditor.dimension.dateLevel = "={'1'}";
      dimensionEditor.bindingModel = chartBindingModel;
      dimensionEditor.fieldType = "xfields";
      fixture.detectChanges();

      expect(dimensionEditor.dateLevel).toEqual("={'1'}");
   });
});
