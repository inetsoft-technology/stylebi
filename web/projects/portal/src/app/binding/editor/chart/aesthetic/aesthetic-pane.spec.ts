/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
import { HttpClientTestingModule } from "@angular/common/http/testing";
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { async, ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { By } from "@angular/platform-browser";
import { NgbModal, NgbModule } from "@ng-bootstrap/ng-bootstrap";
import * as V from "../../../../common/data/visual-frame-model";
import { DndService } from "../../../../common/dnd/dnd.service";
import { GraphTypes } from "../../../../common/graph-types";
import { UIContextService } from "../../../../common/services/ui-context.service";
import { DropDownTestModule } from "../../../../common/test/test-module";
import { TestUtils } from "../../../../common/test/test-utils";
import { StyleConstants } from "../../../../common/util/style-constants";
import { ColorPane } from "../../../../widget/color-picker/cp-color-pane.component";
import { RecentColorService } from "../../../../widget/color-picker/recent-color.service";
import { FixedDropdownDirective } from "../../../../widget/fixed-dropdown/fixed-dropdown.directive";
import { AestheticInfo } from "../../../data/chart/aesthetic-info";
import { AllChartAggregateRef } from "../../../data/chart/all-chart-aggregate-ref";
import { ChartAggregateRef } from "../../../data/chart/chart-aggregate-ref";
import { ChartBindingModel } from "../../../data/chart/chart-binding-model";
import { ChartEditorService } from "../../../services/chart/chart-editor.service";
import { ColorFieldPane } from "../../../widget/color-field-pane.component";
import { AestheticPane } from "./aesthetic-pane.component";
import { ChartAestheticMc } from "./chart-aesthetic-mc.component";
import { ColorCell } from "./color-cell.component";
import { ColorFieldMc } from "./color-field-mc.component";
import { CombinedShapePane } from "./combined-shape-pane.component";
import { LineItem } from "./line-item.component";
import { LinearShapePane } from "./linear-shape-pane.component";
import { ShapeCell } from "./shape-cell.component";
import { ShapeFieldMc } from "./shape-field-mc.component";
import { ShapeItem } from "./shape-item.component";
import { SizeCell } from "./size-cell.component";
import { SizeFieldMc } from "./size-field-mc.component";
import { StaticColorEditor } from "./static-color-editor.component";
import { StaticColorPane } from "./static-color-pane.component";
import { StaticTextureEditor } from "./static-texture-editor.component";
import { StaticTexturePane } from "./static-texture-pane.component";
import { TextFieldMc } from "./text-field-mc.component";
import { TextureItem } from "./texture-item.component";

xdescribe("Aesthetic Pane Unit Test", () => {
   let createMockChartAggregateRef: (name?: string) => ChartAggregateRef = (name?: string) => {
      let aggRef = TestUtils.createMockChartAggregateRef(name);
      aggRef.formula = "Sum";
      aggRef.view = aggRef.formula + "(" + name + ")";
      return aggRef;
   };
   let createMockAllChartAggregateRef: () => AllChartAggregateRef = () => {
      let allAggRef = createMockChartAggregateRef();
      allAggRef.aggregated = false;
      allAggRef.classType = "allaggregate";
      allAggRef.fullName = "(all)";
      allAggRef.view = "(all)";

      return Object.assign({
         visualPaneStatus: {
            textFieldEditable: true
         }
      }, allAggRef);
   };
   let createMockAestheticInfo: (name?: string, frameModel?: V.VisualFrameModel) => AestheticInfo = (name?: string, frameModel?: V.VisualFrameModel) => {
      let aesInfo = TestUtils.createMockAestheticInfo(name);
      aesInfo.dataInfo = TestUtils.createMockChartDimensionRef(name);
      aesInfo.frame = frameModel;
      return aesInfo;
   };

   let mockStaticTextureModel: (field?: string) => V.StaticTextureModel = (field?: string) => {
      return Object.assign({
         clazz: "inetsoft.web.binding.model.graph.aesthetic.StaticTextureModel",
         texture: StyleConstants.PATTERN_NONE
      }, TestUtils.createMockVisualFrameModel(field));
   };
   let mockStaticColorModel: (field?: string) => V.StaticColorModel = (field?: string) => {
      return Object.assign({
         clazz: "inetsoft.web.binding.model.graph.aesthetic.StaticColorModel",
         color: "#518db9",
         cssColor: "",
         defaultColor: "#518db9"
      }, TestUtils.createMockVisualFrameModel(field));
   };
   let mockStaticSizeModel: (field?: string) => V.StaticSizeModel = (field?: string) => {
      return Object.assign({
         clazz: "inetsoft.web.binding.model.graph.aesthetic.StaticSizeModel",
         size: 1,
         largest: 30,
         smallest: 1
      }, TestUtils.createMockVisualFrameModel(field));
   };
   let mockStaticLineModel: (field?: string) => V.StaticLineModel = (field?: string) => {
      return Object.assign({
         clazz: "inetsoft.web.binding.model.graph.aesthetic.StaticLineModel",
         line: StyleConstants.THIN_LINE,
      }, TestUtils.createMockVisualFrameModel(field));
   };
   let mockCategoricalTextureModel: (field?: string) => V.CategoricalTextureModel = (field?: string) => {
      return Object.assign({
         clazz: "inetsoft.web.binding.model.graph.aesthetic.CategoricalTextureModel",
         textures: [0, 1, 2],
      }, TestUtils.createMockVisualFrameModel(field));
   };

   const editorService = {
      changeChartAesthetic: jest.fn(),
      getDNDType: jest.fn(),
      convert: jest.fn(),
      isDropPaneAccept: jest.fn()
   };
   const uiContextService = { isVS: jest.fn() };
   const dservice = {
      processOnDrop: jest.fn(),
      setDragOverStyle: jest.fn()
   };
   let modalService = { open: jest.fn() };
   let recentColorService = { colorSelected: jest.fn() };

   let fixture: ComponentFixture<AestheticPane>;
   let aestheticPane: AestheticPane;
   let bindingModel: ChartBindingModel;

   beforeEach(async(() => {
      TestBed.configureTestingModule({
         imports: [
            FormsModule, ReactiveFormsModule, NgbModule, HttpClientTestingModule, DropDownTestModule
         ],
         declarations: [
            AestheticPane, ColorFieldMc, ColorCell, ShapeFieldMc, ShapeCell, SizeFieldMc, SizeCell, CombinedShapePane, TextureItem, LineItem, ShapeItem, ChartAestheticMc, StaticTextureEditor, StaticTexturePane, StaticColorPane, StaticColorEditor, LinearShapePane, ColorFieldPane, ColorPane, TextFieldMc, FixedDropdownDirective
         ],
         providers: [
            { provide: ChartEditorService, useValue: editorService },
            { provide: UIContextService, useValue: uiContextService},
            { provide: DndService, useValue: dservice },
            { provide: NgbModal, useValue: modalService },
            { provide: RecentColorService, useValue: recentColorService }
         ],
         schemas: [NO_ERRORS_SCHEMA]
      });
      TestBed.compileComponents();
      bindingModel = TestUtils.createMockChartBindingModel();
   }));

   //for Bug #18221, click color/shape/size icon, edit pane should pop up
   it("can not open dropdown menu on visual pane", () => {
      bindingModel.xfields.push(TestUtils.createMockChartDimensionRef("state"));
      bindingModel.yfields.push(TestUtils.createMockChartAggregateRef("customer_id"));
      fixture = TestBed.createComponent(AestheticPane);
      aestheticPane = <AestheticPane>fixture.componentInstance;
      aestheticPane.assemblyName = "Chart1";
      aestheticPane.objectType = "chart";
      aestheticPane.bindingModel = bindingModel;
      fixture.detectChanges();

      let colorCell = fixture.debugElement.query(By.css(".aesthetic-pane color-field-mc div.visual-edit-icon")).nativeElement;
      colorCell.click();
      fixture.detectChanges();
      let fixDropdown = document.getElementsByTagName("fixed-dropdown")[0];
      expect(fixDropdown).not.toBeNull();
      let shapeCell = fixture.debugElement.query(By.css(".aesthetic-pane shape-field-mc div.visual-cell")).nativeElement;
      shapeCell.click();
      fixture.detectChanges();
      let shapeFixDropdown = document.getElementsByTagName("fixed-dropdown")[0];
      expect(shapeFixDropdown).not.toBeNull();

      let sizeCell = fixture.debugElement.query(By.css(".aesthetic-pane size-field-mc div.visual-cell")).nativeElement;
      sizeCell.click();
      fixture.detectChanges();
      let sizeFixDropdown = document.getElementsByTagName("fixed-dropdown")[0];
      expect(sizeFixDropdown).not.toBeNull();
   });

   //for Bug #19029
   it("should not load shape field of measure column on all aggregate pane", () => {
      let aggRef1 = createMockChartAggregateRef("customer_id");
      let aggRef2 = createMockChartAggregateRef("product_id");
      aggRef1.chartType = GraphTypes.CHART_BAR;
      aggRef1.shapeField = createMockAestheticInfo("orderdate", mockCategoricalTextureModel("orderdate"));
      aggRef2.chartType = GraphTypes.CHART_LINE;
      aggRef2.lineFrame = mockStaticLineModel();
      bindingModel.yfields.push(aggRef1, aggRef2);
      bindingModel.xfields.push(TestUtils.createMockChartDimensionRef("state"));
      bindingModel.allChartAggregate = createMockAllChartAggregateRef();
      bindingModel.rtchartType = 1;
      bindingModel.multiStyles = true;

      fixture = TestBed.createComponent(AestheticPane);
      aestheticPane = <AestheticPane>fixture.componentInstance;
      aestheticPane.assemblyName = "Chart1";
      aestheticPane.objectType = "chart";
      aestheticPane.bindingModel = bindingModel;
      aestheticPane.chartModel = TestUtils.createMockVSChartModel("vsChart1");
      fixture.detectChanges();

      let shapeCellIcon = fixture.debugElement.query(By.css(".shape_field_id .visual-edit-icon i")).nativeElement;
      let shapeField = fixture.debugElement.query(By.css(".shape_field_id .visual-field chart-fieldmc")).nativeElement;
      expect(shapeCellIcon.getAttribute("class")).toContain("icon-disabled");
      expect(shapeField).toBeNull();
   });

   //Bug #19543
   it("color cell icon should be enabled on multi style chart", () => {
      let aggRef1 = createMockChartAggregateRef("customer_id");
      let aggRef2 = createMockChartAggregateRef("product_id");
      aggRef1.chartType = GraphTypes.CHART_BAR;
      aggRef1.colorFrame = mockStaticColorModel();
      aggRef2.chartType = GraphTypes.CHART_LINE;
      aggRef2.colorFrame = mockStaticColorModel();
      bindingModel.yfields.push(aggRef1, aggRef2);
      bindingModel.xfields.push(TestUtils.createMockChartDimensionRef("state"));
      bindingModel.allChartAggregate = createMockAllChartAggregateRef();
      bindingModel.allChartAggregate.colorField = createMockAestheticInfo("Employee", mockStaticColorModel("Employee"));
      bindingModel.rtchartType = 1;
      bindingModel.multiStyles = true;

      fixture = TestBed.createComponent(AestheticPane);
      aestheticPane = <AestheticPane>fixture.componentInstance;
      aestheticPane.assemblyName = "Chart1";
      aestheticPane.objectType = "chart";
      aestheticPane.bindingModel = bindingModel;
      aestheticPane.chartModel = TestUtils.createMockVSChartModel("vsChart1");
      fixture.detectChanges();

      let colorCellIcon = fixture.debugElement.query(By.css(".color_field_id .visual-cell-container .visual-cell")).nativeElement;
      expect(colorCellIcon).not.toBeNull();
   });

   //Bug #19249
   it("should update labels when delete aggregate filed from binding", () => {
      bindingModel.rtchartType = 1;
      bindingModel.multiStyles = true;
      bindingModel.allChartAggregate = createMockAllChartAggregateRef();
      bindingModel.xfields.push(TestUtils.createMockChartDimensionRef("state"));
      bindingModel.yfields.push(createMockChartAggregateRef("id1"), createMockChartAggregateRef("id2"), createMockChartAggregateRef("id3"));

      fixture = TestBed.createComponent(AestheticPane);
      aestheticPane = <AestheticPane>fixture.componentInstance;
      aestheticPane.assemblyName = "Chart1";
      aestheticPane.objectType = "chart";
      aestheticPane.bindingModel = bindingModel;
      aestheticPane.chartModel = TestUtils.createMockVSChartModel("vsChart1");
      fixture.detectChanges();

      let label = fixture.debugElement.query(By.css(".aggregate-trigger div")).nativeElement;
      let toggle = fixture.debugElement.query(By.css(".chevron-circle-arrow-right-icon")).nativeElement;
      toggle.click();
      fixture.detectChanges();
      expect(label.textContent).toBe("Sum(id1)");
      toggle.click();
      fixture.detectChanges();
      expect(label.textContent).toBe("Sum(id2)");
      toggle.click();
      fixture.detectChanges();
      expect(label.textContent).toBe("Sum(id3)");
      fixture.debugElement.query(By.css(".aggregate-trigger")).nativeElement.click();
      fixture.detectChanges();
      let menus = fixture.nativeElement.querySelectorAll(".dropdown-menu li");
      expect(menus.length).toBe(4);
   });

   //Bug #19033, should not display discrete measure column in visiual pane
   it("should not display discrete measure column in visiual pane", () => {
      let agg = createMockChartAggregateRef("orderno");
      bindingModel.allChartAggregate = createMockAllChartAggregateRef();
      bindingModel.xfields.push(TestUtils.createMockChartDimensionRef("state"));
      bindingModel.yfields.push(agg, createMockChartAggregateRef("id"));
      bindingModel.rtchartType = 1;
      bindingModel.multiStyles = true;

      fixture = TestBed.createComponent(AestheticPane);
      aestheticPane = <AestheticPane>fixture.componentInstance;
      aestheticPane.assemblyName = "Chart1";
      aestheticPane.objectType = "chart";
      aestheticPane.bindingModel = bindingModel;
      fixture.detectChanges();

      let aggValues = fixture.debugElement.queryAll(By.css(".aesthetic-pane ul li"));
      expect(aggValues.map(item => item.nativeElement.textContent.trim())).toEqual(["(all)", "Sum(orderno)", "Sum(id)"]);

      agg.discrete = true;
      fixture.detectChanges();

      aggValues = fixture.debugElement.queryAll(By.css(".aesthetic-pane ul li"));
      expect(aggValues.map(item => item.nativeElement.textContent.trim())).toEqual(["(all)", "Sum(id)"]);
   });

   //Bug #19574, edit color icon should be disabled
   it("edit color icon should be disabled when remove color field", () => {
      bindingModel.colorField = createMockAestheticInfo("orderdate", mockStaticColorModel("orderdate"));
      fixture = TestBed.createComponent(AestheticPane);
      aestheticPane = <AestheticPane>fixture.componentInstance;
      aestheticPane.bindingModel = bindingModel;
      aestheticPane.assemblyName = "Chart1";
      aestheticPane.objectType = "chart";
      aestheticPane.chartModel = TestUtils.createMockVSChartModel("chart1");
      fixture.detectChanges();

      let colorCellIcon: HTMLElement = fixture.debugElement.query(By.css(".color_field_id color-cell")).nativeElement;
      expect(colorCellIcon).not.toBeNull();

      bindingModel.colorField = null;
      fixture.detectChanges();
      let colorFieldMc: ColorFieldMc = fixture.debugElement.query(By.directive(ColorFieldMc)).componentInstance;
      colorFieldMc.ngOnChanges(null);
      fixture.detectChanges();

      let colorEditIcon = fixture.debugElement.query(By.css(".color_field_id .visual-edit-icon")).nativeElement;
      expect(colorEditIcon.getAttribute("class")).toContain("icon-disabled");
   });

   //Bug #19085, shape cell icon load error on waterfall chart
   it("shape cell icon load error on waterfall chart", (done) => {
      let agg = createMockChartAggregateRef("id");
      agg.chartType = GraphTypes.CHART_WATERFALL;
      agg.rtchartType = GraphTypes.CHART_WATERFALL;
      agg.colorFrame = mockStaticColorModel();
      agg.textureFrame = mockStaticTextureModel();
      let allAgg = createMockAllChartAggregateRef();
      allAgg.rtchartType = GraphTypes.CHART_WATERFALL;
      allAgg.colorFrame = mockStaticColorModel();
      allAgg.summaryColorFrame = mockStaticColorModel();
      allAgg.summaryColorFrame.summary = true;
      allAgg.textureFrame = mockStaticTextureModel();
      allAgg.summaryTextureFrame = mockStaticTextureModel();
      allAgg.summaryTextureFrame.summary = true;
      bindingModel.yfields.push(agg);
      bindingModel.xfields.push(TestUtils.createMockChartDimensionRef("state"));
      bindingModel.allChartAggregate = allAgg;
      bindingModel.multiStyles = true;
      bindingModel.waterfall = true;
      bindingModel.chartType = 0;
      bindingModel.rtchartType = 1;

      fixture = TestBed.createComponent(AestheticPane);
      aestheticPane = <AestheticPane>fixture.componentInstance;
      aestheticPane.bindingModel = bindingModel;
      aestheticPane.assemblyName = "Chart1";
      aestheticPane.objectType = "chart";
      aestheticPane.chartModel = TestUtils.createMockVSChartModel("chart1");
      fixture.detectChanges();

      let colorCells = fixture.nativeElement.querySelectorAll(".color_field_id .visual-cell color-cell");
      let shapeCells = fixture.nativeElement.querySelectorAll(".shape_field_id .visual-cell shape-cell");
      expect(colorCells.length).toEqual(2);
      expect(shapeCells.length).toEqual(2);

      let editIcon = fixture.nativeElement.querySelectorAll(".shape_field_id .visual-cell")[0];
      editIcon.click();
      fixture.detectChanges();

      fixture.whenStable().then(() => {
         let fixedDropDown = document.getElementsByTagName("fixed-dropdown")[0];
         let menuItems = fixedDropDown.getElementsByTagName("static-texture-editor");
         expect(menuItems.length).toEqual(2);

         done();
      });
   });

   //for Bug #19170
   it("should not display size cell on empty chart", () => {
      fixture = TestBed.createComponent(AestheticPane);
      aestheticPane = <AestheticPane>fixture.componentInstance;
      aestheticPane.assemblyName = "Chart1";
      aestheticPane.objectType = "chart";
      aestheticPane.bindingModel = bindingModel;
      fixture.detectChanges();

      let sizeCell = fixture.nativeElement.querySelectorAll(".size_field_id size-cell");
      expect(sizeCell.length).toEqual(0);
   });

   //for Bug #19609, Bug #19607 edit shape icon should be enabled
   it("edit shape icon should be enabled", () => {
      let allAgg = createMockAllChartAggregateRef();
      let aggRef1 = createMockChartAggregateRef("id");
      let aggRef2 = createMockChartAggregateRef("orderno");
      aggRef1.chartType = GraphTypes.CHART_BAR;
      aggRef1.rtchartType = GraphTypes.CHART_BAR;
      aggRef1.textureFrame = mockStaticTextureModel();
      aggRef2.chartType = GraphTypes.CHART_AREA;
      aggRef2.rtchartType = GraphTypes.CHART_AREA;
      aggRef2.lineFrame = mockStaticLineModel();
      allAgg.textureFrame = mockStaticTextureModel();
      bindingModel.yfields = [aggRef1, aggRef2];
      bindingModel.xfields = [TestUtils.createMockChartDimensionRef("state")];
      bindingModel.allChartAggregate = allAgg;
      bindingModel.multiStyles = true;
      bindingModel.chartType = 0;
      bindingModel.rtchartType = 1;

      fixture = TestBed.createComponent(AestheticPane);
      aestheticPane = <AestheticPane>fixture.componentInstance;
      aestheticPane.bindingModel = bindingModel;
      aestheticPane.assemblyName = "Chart1";
      aestheticPane.objectType = "chart";
      aestheticPane.chartModel = TestUtils.createMockVSChartModel("chart1");
      fixture.detectChanges();

      //Bug #19609
      let editShapeIcon: Element = fixture.debugElement.query(By.css(".shape_field_id .visual-edit-icon i")).nativeElement;
      expect(editShapeIcon.getAttribute("class")).toContain("icon-disabled");

      //Bug #19607
      allAgg.shapeField = createMockAestheticInfo("orderdate", mockCategoricalTextureModel("orderdate"));
      fixture.detectChanges();

      editShapeIcon = fixture.debugElement.query(By.css(".shape_field_id .visual-edit-icon i")).nativeElement;
      expect(editShapeIcon.getAttribute("class")).toContain("icon-disabled");

      let toggle = fixture.debugElement.query(By.css(".chevron-circle-arrow-right-icon")).nativeElement;
      toggle.click();
      toggle.click();
      fixture.detectChanges();
      let shapeCell = fixture.debugElement.query(By.css(".shape_field_id .visual-cell-container .visual-cell")).nativeElement;
      let lineItem: HTMLElement = fixture.debugElement.query(By.css(".shape_field_id shape-cell line-item")).nativeElement;
      expect(shapeCell).not.toBeNull();
      expect(lineItem).not.toBeNull();
   });

   //for Bug #19185, Bug #21174, Bug #21465 shape cell should not display, edit size should be disabled, edit text should be disabled
   it("visual pane status on stock chart", () => {
      bindingModel.chartType = GraphTypes.CHART_STOCK;
      bindingModel.rtchartType = GraphTypes.CHART_STOCK;
      bindingModel.xfields = [TestUtils.createMockChartDimensionRef("state")];
      bindingModel.highField = createMockChartAggregateRef("id1");
      bindingModel.closeField = createMockChartAggregateRef("id2");
      bindingModel.lowField = createMockChartAggregateRef("id3");
      bindingModel.colorFrame = mockStaticColorModel();
      bindingModel.sizeFrame = mockStaticSizeModel();

      fixture = TestBed.createComponent(AestheticPane);
      aestheticPane = <AestheticPane>fixture.componentInstance;
      aestheticPane.bindingModel = bindingModel;
      aestheticPane.chartModel = TestUtils.createMockVSChartModel("chart1");
      aestheticPane.chartModel.chartType = GraphTypes.CHART_STOCK;
      aestheticPane.assemblyName = "Chart1";
      aestheticPane.objectType = "chart";
      fixture.detectChanges();

      //Bug #19185
      let shapeEditIcon: HTMLElement = fixture.debugElement.query(By.css(".shape_field_id .visual-edit-icon i")).nativeElement;
      let sizeEditIcon: HTMLElement = fixture.debugElement.query(By.css(".size_field_id .visual-edit-icon i")).nativeElement;
      expect(shapeEditIcon.getAttribute("class")).toContain("icon-disabled");
      expect(sizeEditIcon.getAttribute("class")).toContain("icon-disabled");

      //Bug #21174, Bug #21465
      let textEditIcon: HTMLElement = fixture.debugElement.query(By.css(".text_field_id .visual-edit-icon i")).nativeElement;
      expect(textEditIcon.getAttribute("class")).toContain("icon-disabled");
   });

    //Bug #19112
   it("should enable edit shape icon when discrete is true", () => {
      bindingModel.xfields = [TestUtils.createMockChartDimensionRef("state")];
      let agg1 = TestUtils.createMockChartAggregateRef("id1");
      let agg2 = TestUtils.createMockChartAggregateRef("id2");
      let allAgg = createMockAllChartAggregateRef();
      agg1.chartType = GraphTypes.CHART_BAR;
      agg1.rtchartType = GraphTypes.CHART_BAR;
      agg2.chartType = GraphTypes.CHART_LINE;
      agg2.rtchartType = GraphTypes.CHART_LINE;
      bindingModel.yfields = [agg1, agg2];
      bindingModel.multiStyles = true;
      bindingModel.chartType = 0;
      bindingModel.allChartAggregate = allAgg;

      fixture = TestBed.createComponent(AestheticPane);
      aestheticPane = <AestheticPane>fixture.componentInstance;
      aestheticPane.bindingModel = bindingModel;
      aestheticPane.chartModel = TestUtils.createMockVSChartModel("chart1");
      fixture.detectChanges();

      let editShapeIcon: HTMLElement = fixture.debugElement.query(By.css(".shape_field_id .visual-edit-icon i")).nativeElement;
      expect(editShapeIcon.getAttribute("class")).toContain("icon-disabled");

      agg2.discrete = true;
      fixture = TestBed.createComponent(AestheticPane);
      aestheticPane = <AestheticPane>fixture.componentInstance;
      aestheticPane.bindingModel = bindingModel;
      aestheticPane.chartModel = TestUtils.createMockVSChartModel("chart1");
      fixture.detectChanges();
      let shapeCell: HTMLElement = fixture.debugElement.query(By.css(".shape_field_id .visual-cell-container .visual-cell")).nativeElement;
      expect(shapeCell).not.toBeNull();
   });

   //for Bug #19666
   it("edit color icon should be disabled", () => {
      bindingModel.xfields = [TestUtils.createMockChartDimensionRef("state")];
      let agg1 = createMockChartAggregateRef("id1");
      let agg2 = createMockChartAggregateRef("id2");
      let allAgg = createMockAllChartAggregateRef();
      allAgg.colorField = createMockAestheticInfo("reseller", mockStaticColorModel("reseller"));
      allAgg.colorField.dataInfo.columnValue = "={reseller}";
      agg1.chartType = GraphTypes.CHART_BAR;
      agg1.rtchartType = GraphTypes.CHART_BAR;
      agg1.colorField = createMockAestheticInfo("reseller", mockStaticColorModel("reseller"));
      agg2.chartType = GraphTypes.CHART_LINE;
      agg2.rtchartType = GraphTypes.CHART_LINE;
      bindingModel.allChartAggregate = allAgg;
      bindingModel.yfields = [agg1, agg2];
      bindingModel.multiStyles = true;
      bindingModel.rtchartType = 1;

      fixture = TestBed.createComponent(AestheticPane);
      aestheticPane = <AestheticPane>fixture.componentInstance;
      aestheticPane.bindingModel = bindingModel;
      aestheticPane.chartModel = TestUtils.createMockVSChartModel("chart1");
      fixture.detectChanges();

      let colorEditIcon: HTMLElement = fixture.debugElement.query(By.css(".color_field_id .visual-edit-icon")).nativeElement;
      expect(colorEditIcon.getAttribute("class")).toContain("icon-disabled");
   });

   //for Bug #19787
   it("size icon should be changed when size frame changed", () => {
      bindingModel.xfields = [TestUtils.createMockChartDimensionRef("state")];
      let agg1 = createMockChartAggregateRef("id1");
      let agg2 = createMockChartAggregateRef("id2");
      let allAgg = createMockAllChartAggregateRef();
      let sizeModel = mockStaticSizeModel();
      sizeModel.size = 25;
      agg1.sizeFrame = sizeModel;
      agg2.sizeFrame = mockStaticSizeModel();
      allAgg.sizeFrame = mockStaticSizeModel();
      bindingModel.yfields = [agg1, agg2];
      bindingModel.allChartAggregate = allAgg;
      bindingModel.multiStyles = true;
      bindingModel.rtchartType = 1;

      fixture = TestBed.createComponent(AestheticPane);
      aestheticPane = <AestheticPane>fixture.componentInstance;
      aestheticPane.bindingModel = bindingModel;
      aestheticPane.chartModel = TestUtils.createMockVSChartModel("chart1");
      aestheticPane._aggrIndex = 1;
      fixture.detectChanges();

      let sizeFieldMc: SizeFieldMc = fixture.debugElement.query(By.directive(SizeFieldMc)).componentInstance;
      fixture.detectChanges();

      expect(sizeFieldMc._isMixed).toBeFalsy();
   });

   //Bug #20921
   it("test color frame and shape frame on waterfall chart", (done) => {
      let agg1 = createMockChartAggregateRef("id1");
      let agg2 = TestUtils.createMockChartAggregateRef("id2");
      let agg3 = createMockChartAggregateRef("id3");
      let colorFrame = mockStaticColorModel();
      let shapeFrame = mockStaticTextureModel();
      agg1.discrete = true;
      agg2.discrete = true;
      agg3.colorFrame = colorFrame;
      agg3.summaryColorFrame = colorFrame;
      agg3.summaryColorFrame.summary = true;
      agg3.textureFrame = shapeFrame;
      agg3.summaryTextureFrame = shapeFrame;
      agg3.summaryTextureFrame.summary = true;
      bindingModel.xfields = [agg1];
      bindingModel.yfields = [agg2, agg3];
      bindingModel.waterfall = true;
      bindingModel.chartType = GraphTypes.CHART_WATERFALL;
      bindingModel.rtchartType = GraphTypes.CHART_WATERFALL;

      fixture = TestBed.createComponent(AestheticPane);
      aestheticPane = <AestheticPane>fixture.componentInstance;
      aestheticPane.bindingModel = bindingModel;
      aestheticPane.chartModel = TestUtils.createMockVSChartModel("chart1");
      fixture.detectChanges();

      let colorCells: any = fixture.nativeElement.querySelectorAll(".color_field_id .visual-cell color-cell");
      let shapeCells: any = fixture.nativeElement.querySelectorAll(".shape_field_id .visual-cell shape-cell");
      expect(colorCells.length).toEqual(2);
      expect(shapeCells.length).toEqual(2);

      let shapeEditIcon = fixture.nativeElement.querySelectorAll(".shape_field_id .visual-cell-container .visual-cell")[0];
      shapeEditIcon.click();
      fixture.detectChanges();

      fixture.whenStable().then(() => {
         let fixedDropDown = document.getElementsByTagName("fixed-dropdown")[0];
         let shapeItems = fixedDropDown.getElementsByTagName("static-texture-editor");
         expect(shapeItems.length).toEqual(2);

         done();
      });
   });

   //Bug #20801
   it("color pane should be auto closed", (done) => {
      let aggr = createMockChartAggregateRef("id");
      aggr.colorFrame = mockStaticColorModel();
      bindingModel.yfields = [aggr];
      bindingModel.xfields = [TestUtils.createMockChartDimensionRef("state")];

      fixture = TestBed.createComponent(AestheticPane);
      aestheticPane = <AestheticPane>fixture.componentInstance;
      aestheticPane.bindingModel = bindingModel;
      aestheticPane.chartModel = TestUtils.createMockVSChartModel("chart1");
      aestheticPane.assemblyName = "Chart1";
      aestheticPane.objectType = "chart";
      fixture.detectChanges();

      let colorFieldMc: ColorFieldMc = fixture.debugElement.query(By.directive(ColorFieldMc)).componentInstance;
      let openChanged = jest.spyOn(colorFieldMc, "openChanged");
      fixture.detectChanges();

      let colorEditIcon: HTMLElement = fixture.debugElement.query(By.css(".color_field_id .visual-edit-icon")).nativeElement;
      colorEditIcon.click();
      fixture.detectChanges();

      fixture.whenStable().then(() => {
         let fixedDropdown = document.getElementsByTagName("fixed-dropdown")[0];
         let colorBtn: any = fixedDropdown.querySelector(".color-picker-palette button[style='background-color: rgb(98, 166, 64);']");
         colorBtn.dispatchEvent(new Event("mousedown"));
         fixture.detectChanges();

         fixture.whenStable().then(() => {
            expect(openChanged).toHaveBeenCalledWith(false);

            done();
         });
      });
   });

   //Bug #21290
   it("color and size frame is error when there is multi aggregate on map chart", () => {
      let aggr1 = TestUtils.createMockChartAggregateRef("id1");
      let aggr2 = TestUtils.createMockChartAggregateRef("id2");
      let dim1 = TestUtils.createMockChartDimensionRef("state");
      let colorFrame = mockStaticColorModel();
      aggr1.colorFrame = mockStaticColorModel();
      aggr1.sizeFrame = mockStaticSizeModel();
      aggr2.colorFrame = mockStaticColorModel();
      aggr2.sizeFrame = mockStaticSizeModel();
      dim1.classType = "geo";
      colorFrame.color = "#fbd29a";
      bindingModel.xfields = [aggr1, aggr2];
      bindingModel.geoFields = [dim1];
      bindingModel.colorFrame = colorFrame;
      bindingModel.sizeFrame = mockStaticSizeModel();
      bindingModel.chartType = GraphTypes.CHART_MAP;
      bindingModel.rtchartType = GraphTypes.CHART_MAP;

      fixture = TestBed.createComponent(AestheticPane);
      aestheticPane = <AestheticPane>fixture.componentInstance;
      aestheticPane.bindingModel = bindingModel;
      aestheticPane.chartModel = TestUtils.createMockVSChartModel("chart1");
      aestheticPane.assemblyName = "Chart1";
      aestheticPane.objectType = "chart";
      fixture.detectChanges();

      let colorCells: any = fixture.nativeElement.querySelectorAll(".color_field_id .visual-edit-icon i");
      let sizeCells: any = fixture.nativeElement.querySelectorAll(".size_field_id .visual-cell size-cell");
      expect(colorCells.length).toEqual(1);
      expect(sizeCells.length).toEqual(1);
   });

   //Bug #21320
   it("shape pane should be auto closed", (done) => {
      let shapefield = TestUtils.createMockAestheticInfo();
      shapefield.frame = Object.assign({
         clazz: "inetsoft.web.binding.model.graph.aesthetic.FillShapeModel",
      }, TestUtils.createMockVisualFrameModel());
      shapefield.dataInfo = createMockChartAggregateRef("id2");
      bindingModel.shapeField = shapefield;
      bindingModel.yfields = [createMockChartAggregateRef("id")];
      bindingModel.xfields = [TestUtils.createMockChartDimensionRef("state")];
      bindingModel.chartType = GraphTypes.CHART_POINT;
      bindingModel.rtchartType = GraphTypes.CHART_POINT;

      fixture = TestBed.createComponent(AestheticPane);
      aestheticPane = <AestheticPane>fixture.componentInstance;
      aestheticPane.bindingModel = bindingModel;
      aestheticPane.chartModel = TestUtils.createMockVSChartModel("chart1");
      aestheticPane.assemblyName = "Chart1";
      aestheticPane.objectType = "chart";
      fixture.detectChanges();

      let shapeFieldMc: ShapeFieldMc = fixture.debugElement.query(By.directive(ShapeFieldMc)).componentInstance;
      let openChanged = jest.spyOn(shapeFieldMc, "openChanged");
      fixture.detectChanges();

      let shapeEditIcon: HTMLElement = fixture.debugElement.query(By.css(".shape_field_id .visual-cell-container .visual-cell")).nativeElement;
      shapeEditIcon.click();
      fixture.detectChanges();

      fixture.whenStable().then(() => {
         let fixedDropdown = document.getElementsByTagName("fixed-dropdown")[0];
         let shapeItem: any = fixedDropdown.querySelectorAll("linear-shape-pane input[type=radio]")[1];
         shapeItem.checked = "true";
         shapeItem.dispatchEvent(new Event("change"));
         fixture.detectChanges();

         fixture.whenStable().then(() => {
            expect(openChanged).toHaveBeenCalledWith(false);

            done();
         });
      });
   });

   //Bug #21510
   it("shape edit icon should be enabled", () => {
      let aggr1 = createMockChartAggregateRef("id1");
      let aggr2 = createMockChartAggregateRef("id2");
      let allAgg = createMockAllChartAggregateRef();
      let colorFrame = Object.assign({
         clazz: "inetsoft.web.binding.model.graph.aesthetic.CategoricalColorModel",
         colorMaps: [],
         colors: ["#518db9", "#b9dbf4", "#62a640", "#ade095"],
         cssColors: null,
         dateFormat: null,
         defaultColors: ["#518db9", "#b9dbf4", "#62a640", "#ade095"]
      }, TestUtils.createMockVisualFrameModel("city"));
      aggr1.colorField = createMockAestheticInfo("city", colorFrame);
      aggr1.colorFrame = mockStaticColorModel();
      aggr1.chartType = GraphTypes.CHART_AUTO;
      aggr1.rtchartType = GraphTypes.CHART_BAR;
      aggr2.colorFrame = mockStaticColorModel();
      aggr2.chartType = GraphTypes.CHART_AUTO;
      aggr2.rtchartType = GraphTypes.CHART_BAR;
      allAgg.textureFrame = mockStaticTextureModel();
      bindingModel.yfields = [aggr1, aggr2];
      bindingModel.xfields = [TestUtils.createMockChartDimensionRef("state")];
      bindingModel.allChartAggregate = allAgg;
      bindingModel.multiStyles = true;
      bindingModel.chartType = GraphTypes.CHART_AUTO;
      bindingModel.rtchartType = GraphTypes.CHART_BAR;

      fixture = TestBed.createComponent(AestheticPane);
      aestheticPane = <AestheticPane>fixture.componentInstance;
      aestheticPane.bindingModel = bindingModel;
      aestheticPane.chartModel = TestUtils.createMockVSChartModel("chart1");
      aestheticPane.assemblyName = "Chart1";
      aestheticPane.objectType = "chart";
      fixture.detectChanges();

      let shapeCell: HTMLElement = fixture.debugElement.query(By.css(".shape_field_id .visual-cell-container .visual-cell")).nativeElement;
      expect(shapeCell).not.toBeNull();
   });
});
