/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { HttpClientTestingModule } from "@angular/common/http/testing";
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { of as observableOf } from "rxjs";
import { ColorFieldPane } from "../../binding/widget/color-field-pane.component";
import { GraphTypes } from "../../common/graph-types";
import { DropDownTestModule } from "../../common/test/test-module";
import { TestUtils } from "../../common/test/test-utils";
import { BindingAlignmentPane } from "../../format/objects/binding-alignment-pane.component";
import { BindingBorderPane } from "../../format/objects/binding-border-pane.component";
import { BorderStylePane } from "../../format/objects/border-style-pane.component";
import { ComboBox } from "../../format/objects/combo-box.component";
import { FormattingPane } from "../../format/objects/formatting-pane.component";
import { ChartRegion } from "../../graph/model/chart-region";
import { ColorDropdown } from "../../widget/color-picker/color-dropdown.component";
import { ColorEditor } from "../../widget/color-picker/color-editor.component";
import { ColorPicker } from "../../widget/color-picker/color-picker.component";
import { ColorPane } from "../../widget/color-picker/cp-color-pane.component";
import { RecentColorService } from "../../widget/color-picker/recent-color.service";
import { FixedDropdownDirective } from "../../widget/fixed-dropdown/fixed-dropdown.directive";
import { DropdownView } from "../../widget/dropdown-view/dropdown-view.component";
import { DynamicComboBox } from "../../widget/dynamic-combo-box/dynamic-combo-box.component";
import { FontPane } from "../../widget/font-pane/font-pane.component";
import { AlphaDropdown } from "../../widget/format/alpha-dropdown.component";
import { FormatCSSPane } from "../../widget/format/format-css-pane.component";
import { DebounceService } from "../../widget/services/debounce.service";
import { FontService } from "../../widget/services/font.service";
import { ModelService } from "../../widget/services/model.service";
import { VSImageModel } from "../model/output/vs-image-model";
import { VSChartModel } from "../model/vs-chart-model";
import { VSFormatsPane } from "./vs-formats-pane.component";

describe("VS Formats Pane Unit case", () => {
   let changeDetectorRef: any;
   let fontService: any;
   let fixture: ComponentFixture<VSFormatsPane>;
   let vsFormatsPane: VSFormatsPane;
   let modalService: any;
   let modelService: any;
   let debounceService: any;

   beforeEach(() => {
      changeDetectorRef = { detach: jest.fn(), reattach: jest.fn() };
      fontService = { getAllFonts: jest.fn() };
      modalService = { open: jest.fn() };
      modelService = { getModel: jest.fn() };
      debounceService = {
         debounce: jest.fn((key, fn, delay, args) => fn(...args)),
         cancel: jest.fn()
      };

      TestBed.configureTestingModule({
         imports: [ReactiveFormsModule, FormsModule, NgbModule, DropDownTestModule,
            HttpClientTestingModule],
         declarations: [VSFormatsPane, DropdownView, DynamicComboBox, AlphaDropdown, ColorEditor, ColorPicker, ColorPane,
                        FontPane, FormattingPane, ColorDropdown,
                        ColorFieldPane, ColorPane, BindingAlignmentPane, BindingBorderPane,
                        FormatCSSPane, ComboBox, BorderStylePane, FixedDropdownDirective],
         providers: [
            {provide: FontService, useValue: fontService},
            RecentColorService,
            {provide: ModelService, useValue: modelService},
            {provide: DebounceService, useValue: debounceService}
         ],
         schemas: [NO_ERRORS_SCHEMA]
      });
      TestBed.compileComponents();

      fixture = TestBed.createComponent(VSFormatsPane);
      vsFormatsPane = <VSFormatsPane>fixture.componentInstance;

      vsFormatsPane._format = TestUtils.createMockFromatInfo();
      fontService.getAllFonts.mockImplementation(() => observableOf([{}]));
   });

   //Bug #16685, Bug #16689 check the aligment combox status
   //Bug #18597, BUg #18664 color,border, aligment status
   //Bug #18060, Bug #18342 for wrap text on diff path
   //Bug #18678 target label and plot label
   //Bug #18790 element vo
   //Bug #18794 e2e case covered
   //Bug #19411
   //Note: alpha dropdown and color picker all is enable
   it("check format status on chart diff path of bar chart", () => {
      let chart1: VSChartModel = TestUtils.createMockVSChartModel("chart1");
      chart1.chartSelection = {
         chartObject: null,
         regions: null
      };

      //selected whole chart
      vsFormatsPane._focusedAssemblies = [chart1];
      vsFormatsPane.updateProperties();
      vsFormatsPane._format.halignmentEnabled = true;
      expect(vsFormatsPane.fontDisabled).toBeFalsy();
      expect(vsFormatsPane.formatDisabled).toBeFalsy();
      expect(vsFormatsPane.dynamicColorDisabled).toBeFalsy();
      expect(vsFormatsPane.alignDisabled).toBeFalsy();
      expect(vsFormatsPane.wrapTextDisabled).toBeTruthy();
      expect(vsFormatsPane.borderDisabled).toBeFalsy();
      expect(vsFormatsPane.cssDisabled).toBeFalsy();
      expect(vsFormatsPane.showPresenter()).toBeFalsy();

      //select y1|y2 title
      vsFormatsPane._format.valignmentEnabled = true;
      vsFormatsPane._format.halignmentEnabled = false;
      let regions1: ChartRegion = TestUtils.createMockChartRegion();
      chart1.regionMetaDictionary = [{areaType: "title"}];
      let chartObject = TestUtils.createMockChartObject("y_title");  //or y2_title
      chart1.chartSelection.regions = [regions1];
      chart1.chartSelection.chartObject = chartObject;
      vsFormatsPane._focusedAssemblies = [chart1];
      vsFormatsPane.updateProperties();
      expect(vsFormatsPane.fontDisabled).toBeFalsy();
      expect(vsFormatsPane.formatDisabled).toBeFalsy();
      expect(vsFormatsPane.dynamicColorDisabled).toBeTruthy();
      expect(vsFormatsPane.alignDisabled).toBeFalsy();
      expect(vsFormatsPane.isHAlignmentEnabled()).toBeFalsy();
      expect(vsFormatsPane.isVAlignmentEnabled()).toBeTruthy();
      expect(vsFormatsPane.wrapTextDisabled).toBeTruthy();
      expect(vsFormatsPane.borderDisabled).toBeTruthy();
      expect(vsFormatsPane.cssDisabled).toBeFalsy();
      expect(vsFormatsPane.showPresenter()).toBeFalsy();

      //select x|x2 title
      vsFormatsPane._format.halignmentEnabled = true;
      vsFormatsPane._format.valignmentEnabled = false;
      chart1.regionMetaDictionary = [{areaType: "title"}];
      chartObject = TestUtils.createMockChartObject("x_title"); //x2_title
      chart1.chartSelection.regions = [regions1];
      chart1.chartSelection.chartObject = chartObject;
      vsFormatsPane._focusedAssemblies = [chart1];
      vsFormatsPane.updateProperties();
      expect(vsFormatsPane.isHAlignmentEnabled()).toBeTruthy();
      expect(vsFormatsPane.isVAlignmentEnabled()).toBeFalsy();

      //select x1 axis
      vsFormatsPane._format.halignmentEnabled = false;
      vsFormatsPane._format.valignmentEnabled = false;
      chart1.regionMetaDictionary = [{areaType: "axis"}];
      chartObject = TestUtils.createMockChartObject("bottom_x_axis");
      chart1.chartSelection.regions = [regions1];
      chart1.chartSelection.chartObject = chartObject;
      vsFormatsPane._focusedAssemblies = [chart1];
      vsFormatsPane.updateProperties();
      expect(vsFormatsPane.alignDisabled).toBeTruthy();
      expect(vsFormatsPane.borderDisabled).toBeTruthy();
      expect(vsFormatsPane.cssDisabled).toBeFalsy();

      //select x2 aixs
      //Bug #19411
      vsFormatsPane._format.halignmentEnabled = true;
      vsFormatsPane._format.valignmentEnabled = false;
      chart1.regionMetaDictionary = [{areaType: "axis"}];
      chartObject = TestUtils.createMockChartObject("top_x_axis");
      chart1.chartSelection.regions = [regions1];
      chart1.chartSelection.chartObject = chartObject;
      vsFormatsPane._focusedAssemblies = [chart1];
      vsFormatsPane.updateProperties();
      expect(vsFormatsPane.alignDisabled).toBeFalsy();
      expect(vsFormatsPane.isHAlignmentEnabled()).toBeTruthy();
      expect(vsFormatsPane.isVAlignmentEnabled()).toBeFalsy();

      //select y1|y2 axis
      vsFormatsPane._format.halignmentEnabled = false;
      vsFormatsPane._format.valignmentEnabled = false;
      chart1.regionMetaDictionary = [{areaType: "axis"}];
      chartObject = TestUtils.createMockChartObject("left_y_axis"); //right_y_axis
      chart1.chartSelection.regions = [regions1];
      chart1.chartSelection.chartObject = chartObject;
      vsFormatsPane._focusedAssemblies = [chart1];
      vsFormatsPane.updateProperties();
      expect(vsFormatsPane.alignDisabled).toBeTruthy();

      //select plot label and Text binding TODO Bug #18678
      vsFormatsPane._format.halignmentEnabled = true;
      vsFormatsPane._format.valignmentEnabled = false;
      chart1.regionMetaDictionary = [{areaType: "text"}];
      chartObject = TestUtils.createMockChartObject("plot_area"); //legend_content
      chart1.chartSelection.regions = [regions1];
      chart1.chartSelection.chartObject = chartObject;
      vsFormatsPane._focusedAssemblies = [chart1];
      vsFormatsPane.updateProperties();
      expect(vsFormatsPane.dynamicColorDisabled).toBeTruthy();
      expect(vsFormatsPane.alignDisabled).toBeFalsy();
      expect(vsFormatsPane.isHAlignmentEnabled()).toBeTruthy();
      expect(vsFormatsPane.isVAlignmentEnabled()).toBeFalsy();

      //select legend title and legend content
      vsFormatsPane._format.halignmentEnabled = true;
      vsFormatsPane._format.valignmentEnabled = false;
      chart1.regionMetaDictionary = [{areaType: "legend"}];
      chartObject = TestUtils.createMockChartObject("legend_title"); //legend_content
      chart1.chartSelection.regions = [regions1];
      chart1.chartSelection.chartObject = chartObject;
      vsFormatsPane._focusedAssemblies = [chart1];
      vsFormatsPane.updateProperties();
      expect(vsFormatsPane.alignDisabled).toBeFalsy();
      expect(vsFormatsPane.isHAlignmentEnabled()).toBeTruthy();
      expect(vsFormatsPane.isVAlignmentEnabled()).toBeFalsy();

      //select element vo Bug #18790
      vsFormatsPane._format.halignmentEnabled = false;
      vsFormatsPane._format.valignmentEnabled = false;
      chart1.regionMetaDictionary = [{areaType: "vo"}];
      chartObject = TestUtils.createMockChartObject("plot_area");
      chart1.chartSelection.regions = [regions1];
      chart1.chartSelection.chartObject = chartObject;
      vsFormatsPane._focusedAssemblies = [chart1];
      vsFormatsPane.updateProperties();
      expect(vsFormatsPane.colorDisabled).toBeTruthy();
      expect(vsFormatsPane.alignDisabled).toBeTruthy();

      //select target text label
      vsFormatsPane._format.halignmentEnabled = false;
      vsFormatsPane._format.valignmentEnabled = false;
      chart1.regionMetaDictionary = [{areaType: "label"}];
      chartObject = TestUtils.createMockChartObject("plot_area");
      chart1.chartSelection.regions = [regions1];
      chart1.chartSelection.chartObject = chartObject;
      vsFormatsPane._focusedAssemblies = [chart1];
      vsFormatsPane.updateProperties();
      expect(vsFormatsPane.formatDisabled).toBeFalsy();
      expect(vsFormatsPane.colorDisabled).toBeFalsy();
      expect(vsFormatsPane.alignDisabled).toBeTruthy();
   });


   //Bug #6061
   //BUg #18706 unselect assembly, format pane is disable
   //Bug #18879 select embedded vs format pane is disable
   //Bug #18902 select element vo
   it("The format pane should be disabled", () => {
      let emvs1 = TestUtils.createMockVSViewsheetModel("vs1");
      let list1 = TestUtils.createMockVSSelectionListModel("list");
      let region1 = TestUtils.createMockselectedRegion();
      region1.path[0] = "Measure Bar";
      list1.selectedRegions = [region1];
      vsFormatsPane._focusedAssemblies = [list1];
      vsFormatsPane.updateProperties();
      expect(vsFormatsPane.formatDisabled).toBeFalsy();

      //Bug #18879 embedded vs
      vsFormatsPane._focusedAssemblies = [emvs1];
      vsFormatsPane.updateProperties();
      expect(vsFormatsPane.formatDisabled).toBeTruthy();

      //Bug 18920 element vo
      let chart1: VSChartModel = TestUtils.createMockVSChartModel("chart1");
      chart1.chartSelection = {
         chartObject: null,
         regions: null
      };
      chart1.regionMetaDictionary = [{areaType: "vo"}];
      chart1.chartSelection.regions = [TestUtils.createMockChartRegion()];
      chart1.chartSelection.chartObject = TestUtils.createMockChartObject("plot_area");
      vsFormatsPane._focusedAssemblies = [chart1];
      vsFormatsPane.updateProperties();
      expect(vsFormatsPane.formatDisabled).toBeFalsy();
   });

   //Bug #17959 check css status
   //Bug #18587 check precenter status
   it("check format status on table diff data path", () => {
      let table = TestUtils.createMockVSTableModel("table");

      //select whole table
      table.titleSelected = false;
      table.selectedData = null;
      table.selectedHeaders = null;
      table.selectedRegions = null;
      table.firstSelectedColumn = -1;
      table.firstSelectedRow = -1;
      vsFormatsPane._focusedAssemblies = [table];

      vsFormatsPane.updateProperties();
      expect(vsFormatsPane.cssDisabled).toBeFalsy();
      expect(vsFormatsPane.showPresenter()).toBeFalsy();

      //select table title
      table.titleSelected = true;
      vsFormatsPane._focusedAssemblies = [table];
      vsFormatsPane.updateProperties();
      expect(vsFormatsPane.cssDisabled).toBeFalsy();
      expect(vsFormatsPane.showPresenter()).toBeFalsy();

      //select table header cell
      table.titleSelected = false;
      table.selectedHeaders = new Map<number, number[]>();
      table.selectedHeaders.set(0, [1]);
      vsFormatsPane._focusedAssemblies = [table];
      vsFormatsPane.updateProperties();
      expect(vsFormatsPane.cssDisabled).toBeTruthy();
      expect(vsFormatsPane.showPresenter()).toBeFalsy();

      //select table detail cell
      table.titleSelected = false;
      table.selectedHeaders = null;
      table.selectedData = new Map<number, number[]>();
      table.selectedData.set(1, [2]);
      vsFormatsPane._focusedAssemblies = [table];
      vsFormatsPane.updateProperties();
      expect(vsFormatsPane.cssDisabled).toBeTruthy();
      expect(vsFormatsPane.showPresenter()).toBeFalsy();
   });

   //Bug #17959 should enable css when select calc table object or title
   //Bug #18666 css status
   //Bug #18662 presenter status
   it("check format pane status on calc table different data path", () => {
      let calc = TestUtils.createMockVSCalcTableModel("calc");

      //select calc table object
      calc.titleSelected = false;
      calc.selectedHeaders = null;
      calc.selectedData = null;
      calc.firstSelectedColumn = -1;
      calc.firstSelectedRow = -1;
      vsFormatsPane._focusedAssemblies = [calc];
      vsFormatsPane.updateProperties();
      expect(vsFormatsPane.cssDisabled).toBeFalsy();
      expect(vsFormatsPane.showPresenter()).toBeFalsy();

      //select calc table title
      calc.titleSelected = true;
      vsFormatsPane._focusedAssemblies = [calc];
      vsFormatsPane.updateProperties();
      expect(vsFormatsPane.cssDisabled).toBeFalsy();
      expect(vsFormatsPane.showPresenter()).toBeFalsy();

      //select calc table cell
      calc.titleSelected = false;
      calc.selectedData = new Map<number, number[]>();
      calc.selectedData.set(2, [1]);
      calc.selectedHeaders = null;
      calc.firstSelectedRow = 1;
      vsFormatsPane._focusedAssemblies = [calc];
      vsFormatsPane.updateProperties();
      expect(vsFormatsPane.cssDisabled).toBeTruthy();
      expect(vsFormatsPane.showPresenter()).toBeTruthy();
   });

   //Bug #17959 should enable css when select crosstab table object or title
   //Bug #18589, Bug #18660
   it("check formats status on crosstab table diff data path", () => {
      let crosstab = TestUtils.createMockVSCrosstabModel("crosstab");

      //whole crosstab
      crosstab.titleSelected = false;
      crosstab.selectedHeaders = null;
      crosstab.selectedData = null;
      crosstab.firstSelectedColumn = -1;
      crosstab.firstSelectedRow = -1;
      vsFormatsPane._focusedAssemblies = [crosstab];
      vsFormatsPane.updateProperties();
      expect(vsFormatsPane.cssDisabled).toBeFalsy();
      expect(vsFormatsPane.showPresenter()).toBeFalsy();

      //select crosstab title
      crosstab.titleSelected = true;
      vsFormatsPane._focusedAssemblies = [crosstab];
      vsFormatsPane.updateProperties();
      expect(vsFormatsPane.cssDisabled).toBeFalsy();
      expect(vsFormatsPane.showPresenter()).toBeFalsy();

      //select crosstab header cell
      crosstab.titleSelected = false;
      crosstab.selectedData = null;
      crosstab.selectedHeaders = new Map<number, number[]>();
      crosstab.selectedHeaders.set(0, [1]);
      crosstab.firstSelectedColumn = 0;
      crosstab.firstSelectedRow = 0;
      vsFormatsPane._focusedAssemblies = [crosstab];
      vsFormatsPane.updateProperties();
      expect(vsFormatsPane.cssDisabled).toBeTruthy();
      expect(vsFormatsPane.showPresenter()).toBeTruthy();

      //select crosstab data cell
      crosstab.selectedData = new Map<number, number[]>();
      crosstab.selectedData.set(1, [1]);
      crosstab.selectedHeaders = null;
      vsFormatsPane._focusedAssemblies = [crosstab];
      vsFormatsPane.updateProperties();
      expect(vsFormatsPane.cssDisabled).toBeTruthy();
      expect(vsFormatsPane.showPresenter()).toBeTruthy();
   });

   //Bug #17965 only enable color and css on fomrat when select selection list bar
   //Bug #18704 and Bug #18705 color and css status on select  list bar
   //Bug #20302 bar should return default color
   it("check format status  when select selection list", () => {
      let formats = TestUtils.createMockVSObjectFormatInfoModel();
      formats.color = "-11432519";
      formats.colorType = "Static";
      let list = TestUtils.createMockVSSelectionListModel("list");
      list.showBar = true;
      let region = TestUtils.createMockselectedRegion();
      region.path = ["Measure Bar"];
      list.selectedRegions = [region];
      vsFormatsPane._format = formats;
      vsFormatsPane._focusedAssemblies = [list];
      vsFormatsPane.updateProperties();
      expect(vsFormatsPane.dynamicColorDisabled).toBeFalsy();
      expect(vsFormatsPane.fontDisabled).toBeTruthy();
      expect(vsFormatsPane.formattingDisabled).toBeTruthy();
      expect(vsFormatsPane.alignDisabled).toBeTruthy();
      expect(vsFormatsPane.wrapTextDisabled).toBeTruthy();
      expect(vsFormatsPane.borderDisabled).toBeTruthy();
      expect(vsFormatsPane.cssDisabled).toBeFalsy();

      //Bug #20302
      fixture.detectChanges();
      let colorPickers = fixture.nativeElement.querySelectorAll(".color-picker-swatch");
      expect(colorPickers[0].style["background-color"]).toBe("rgb(81, 141, 185)");
   });

   //Bug #18345 disable color and backgroud when select axis in radar chart
   //Bug #19119 disable border and aligment on radar chart
   //Bug #19959 enable aligment when select x2 title
   it("check formats status for radar charts", () => {
      let chart = TestUtils.createMockVSChartModel("chart");
      chart.chartType = GraphTypes.CHART_RADAR;
      chart.titleSelected = false;
      chart.chartSelection = {
         chartObject: null,
         regions: null
      };
      let chartObject = TestUtils.createMockChartObject("plot_area");
      let region1 = TestUtils.createMockChartRegion();
      chart.regionMetaDictionary = [{areaType: "axis"}];
      chart.chartSelection.chartObject = chartObject;
      chart.chartSelection.regions = [region1];
      vsFormatsPane._focusedAssemblies = [chart];

      vsFormatsPane._format.halignmentEnabled = false;
      vsFormatsPane._format.valignmentEnabled = false;
      vsFormatsPane.updateProperties();
      expect(vsFormatsPane.dynamicColorDisabled).toBeTruthy();
      expect(vsFormatsPane.alignDisabled).toBeTruthy();
      expect(vsFormatsPane.borderDisabled).toBeTruthy();

      chartObject = TestUtils.createMockChartObject("x2_title");
      region1 = TestUtils.createMockChartRegion();
      chart.regionMetaDictionary = [{areaType: "x2_title"}];
      chart.chartSelection.chartObject = chartObject;
      chart.chartSelection.regions = [region1];
      vsFormatsPane._focusedAssemblies = [chart];

      vsFormatsPane._format.halignmentEnabled = true;
      vsFormatsPane._format.valignmentEnabled = false;
      vsFormatsPane.updateProperties();
      expect(vsFormatsPane.alignDisabled).toBeFalsy();
   });

   //Bug #18582 textinput format status
   it("check formats status for textinput", () => {
      let textinput = TestUtils.createMockVSTextInputModel("textinput1");
      vsFormatsPane._focusedAssemblies = [textinput];
      vsFormatsPane.updateProperties();
      expect(vsFormatsPane.dynamicColorDisabled).toBeFalsy();
      expect(vsFormatsPane.fontDisabled).toBeFalsy();
      expect(vsFormatsPane.formattingDisabled).toBeTruthy();
      expect(vsFormatsPane.alignDisabled).toBeFalsy();
      expect(vsFormatsPane.wrapTextDisabled).toBeTruthy();
      expect(vsFormatsPane.borderDisabled).toBeFalsy();
      expect(vsFormatsPane.cssDisabled).toBeFalsy();
   });

   //Bug #18855 format should be rightly on viewer side
   //Bug #19820 aligment status should be rightly
   it("should be right format attribute on viewer side", () => {
      let vsformats = new VSFormatsPane(changeDetectorRef, fontService, modelService, modalService,
         debounceService);
      vsformats.viewer = true;
      let chart1: VSChartModel = TestUtils.createMockVSChartModel("chart1");
      chart1.chartSelection = {
         chartObject: null,
         regions: null
      };

      //selected whole chart
      vsformats._focusedAssemblies = [chart1];
      vsformats.updateProperties();
      expect(vsformats.cssDisabled).toBeTruthy();

      vsFormatsPane._format.halignmentEnabled = false;
      vsFormatsPane._format.valignmentEnabled = false;
      let regions1: ChartRegion = TestUtils.createMockChartRegion();
      chart1.regionMetaDictionary = [{areaType: "text"}];
      let chartObject = TestUtils.createMockChartObject("plot_area");
      chart1.chartSelection.chartObject = chartObject;
      chart1.chartSelection.regions = [regions1];
      vsFormatsPane._focusedAssemblies = [chart1];
      vsFormatsPane.updateProperties();
      expect(vsFormatsPane.alignDisabled).toBeTruthy();

   });

   //Bug #18442 font color should disabled for image
   it("font color should disabled for image", () => {
      let vsformats = new VSFormatsPane(changeDetectorRef, fontService, modelService, modalService,
         debounceService);
      let image1: VSImageModel = TestUtils.createMockVSImageModel("image1");

      vsformats._focusedAssemblies = [image1];
      vsformats.updateProperties();
      expect(vsformats.colorDisabled).toBeTruthy();
   });

   //Bug #20164, Bug #20127,  should disable color, border dynamic combox on text format
   //Bug #20187 should enable aligment on text format
   it("should disable color|border dynamic combox on text format", () => {
      let vsObjectFormat = TestUtils.createMockVSObjectFormatInfoModel();
      vsObjectFormat.dynamicColorDisabled = true;
      vsObjectFormat.borderDisabled = true;
      vsObjectFormat.alignEnabled = true;

      let chart1: VSChartModel = TestUtils.createMockVSChartModel("chart1");
      chart1.chartSelection = {
         chartObject: null,
         regions: null
      };
      vsFormatsPane.vsObjectFormat = vsObjectFormat;
      vsFormatsPane._focusedAssemblies = [chart1];
      vsFormatsPane.updateProperties();
      fixture.detectChanges();

      let types = fixture.nativeElement.querySelectorAll(".form-floating");
      //color
      expect(types[1].getAttribute("class")).toContain("disabled");
      expect(types[2].getAttribute("class")).toContain("disabled");
      //border
      expect(types[5].getAttribute("class")).toContain("disabled");
      //aligment
      expect(types[4].getAttribute("class")).not.toContain("disabled");

   });
});
