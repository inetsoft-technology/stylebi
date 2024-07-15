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
import { Component, ViewChild } from "@angular/core";
import { async, ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule } from "@angular/forms";
import { By } from "@angular/platform-browser";
import { NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { AggregateRef } from "../../../common/data/aggregate-ref";
import { Rectangle } from "../../../common/data/rectangle";
import { CalcTableCell } from "../../../common/data/tablelayout/calc-table-cell";
import { CalcTableLayout } from "../../../common/data/tablelayout/calc-table-layout";
import { CalcTableRow } from "../../../common/data/tablelayout/calc-table-row";
import { XSchema } from "../../../common/data/xschema";
import { TestUtils } from "../../../common/test/test-utils";
import { StyleConstants } from "../../../common/util/style-constants";
import { XConstants } from "../../../common/util/xconstants";
import { CellBindingInfo } from "../../data/table/cell-binding-info";
import { OrderModel } from "../../data/table/order-model";
import { TopNModel } from "../../data/table/topn-model";
import { VSCalcTableEditorService } from "../../services/table/vs-calc-table-editor.service";
import { CalcGroupOption } from "./calc-group-option.component";
import { DateLevelExamplesService } from "../../../common/services/date-level-examples.service";
import { of as observableOf } from "rxjs";

@Component({
   selector: "test-app",
   template: `
     <calc-group-option [cellBinding]="cellBinding"
                        [aggregates]="aggregates" [field]="field"></calc-group-option>`
})
class TestApp {
   @ViewChild(CalcGroupOption, {static: false}) calcGroupOption: CalcGroupOption;

   cellBinding = <CellBindingInfo> {
      order: createOrderModel(),
      topn: createMockTopNModel()
   };
   field = TestUtils.createMockDataRef("state");
   aggregates = () => [createMockAggregateRef("id")];
}

const BESIC_SORTOPTION: any[] = ["None", "Ascending", "Descending"];
const AGG_SORTOPTION: any[] = ["common.widget.SortOption.byAsc",
   "common.widget.SortOption.byDesc"];
const DATE_OPTIONS: any[] = ["Year", "Quarter", "Month", "Week", "Day", "Quarter of Year", "Month of Year", "Week of Year", "Day of Month", "Day of Week", "None"];
const TIME_OPTIONS: any[] = ["Hour", "Minute", "Second", "Hour of Day", "Minute of Hour", "Second of Minute", "None"];
const DATETIME_OPTIONS: any[] = ["Year", "Quarter", "Month", "Week", "Day", "Hour", "Minute", "Second", "Quarter of Year",
   "Month of Year", "Week of Year", "Day of Month", "Day of Week", "Hour of Day",
   "Minute of Hour", "Second of Minute", "None"];

let createMockCalcTableLayout: () => CalcTableLayout = () => {
   return {
      tableRows: [createMockCalcTableRow()],
      tableColumns: [],
      selectedCells: [],
      selectedRect: new Rectangle(0, 0, 0, 0),
      selectedRegions: []
   };
};
let createMockCalcTableRow: () => CalcTableRow = () => {
   return {
      text: "",
      height: 20,
      row: 0,
      format: null,
      tableCells: [createMockCalcTableCell(0, "↓[Ξstate]", 1)],
      cellPath: null,
   };
};
let createMockCalcTableCell: (col: number, text: string, type: number) => CalcTableCell = (col: number, text: string, type: number) => {
   return {
      row: 1,
      col: col,
      vsFormat: TestUtils.createMockVSFormatModel(),
      text: text,
      span: null,
      baseInfo: null,
      cellPath: null,
      bindingType: type,
   };
};
let createMockTopNModel: () => TopNModel = () => {
   return {
      type: 0,
      topn: 3,
      others: false,
      sumCol: 0,
      sumColValue: ""
   };
};
let createOrderModel: () => OrderModel = () => {
   return {
      type: 1,
      sortCol: 0,
      sortValue: "",
      others: false,
      option: 0,
      interval: 0,
      manualOrder: [],
      info: {
         conditions: [],
         groups: [],
         name: null,
         type: 0
      }
   };
};
let createMockAggregateRef: (name: string) => AggregateRef = (name: string) => {
   return Object.assign({
      ref: TestUtils.createMockDataRef(name),
      ref2: null,
      formulaName: "Sum",
      percentage: false
   }, TestUtils.createMockDataRef(name));
};

describe("Calc Group Option Unit Test", () => {
   let calcTableLayout: CalcTableLayout = createMockCalcTableLayout();
   let editorService: any = {
      getTableLayout: jest.fn(),
      namedGroups: null
   };
   let dateLevelExamplesService = {loadDateLevelExamples: jest.fn(() => observableOf())};
   let fixture: ComponentFixture<CalcGroupOption>;
   let comp: CalcGroupOption;

   beforeEach(async(() => {
      editorService.getTableLayout.mockImplementation(() => calcTableLayout);

      TestBed.configureTestingModule({
         imports: [
            FormsModule,
            NgbModule,
            HttpClientTestingModule
         ],
         declarations: [
            CalcGroupOption, TestApp
         ], // declare the test component
         providers: [
            {provide: VSCalcTableEditorService, useValue: editorService},
            {provide: DateLevelExamplesService, useValue: dateLevelExamplesService},
         ]
      }).compileComponents();  // compile template and css
   }));

   //for Bug#16695, ranking select should be disabled if no summary column,#Bug #10445, sort combobox load error
   it("Test sort select data display when there is no summary column", () => {
      fixture = TestBed.createComponent(CalcGroupOption);
      comp = <CalcGroupOption>fixture.componentInstance;
      comp.cellBinding = <CellBindingInfo> {
         order: createOrderModel(),
         topn: createMockTopNModel()
      };
      comp.field = TestUtils.createMockDataRef("state");
      comp.aggregates = () => [];
      fixture.detectChanges();

      let sort = fixture.debugElement.queryAll(By.css(".popup-editor__container select"))[0].queryAll(By.css("option"));
      fixture.detectChanges();

      let rankingSelect: Element = fixture.nativeElement.querySelectorAll("select")[1];
      expect(sort.map(item => TestUtils.toString(item.nativeElement.textContent.trim()))).toEqual(BESIC_SORTOPTION);
      expect(rankingSelect.getAttribute("ng-reflect-is-disabled")).toEqual("true");
   });

   //Bug #17175: sort combobox load error if there is summary column
   it("Test sort select data display when there is summary column", () => {
      calcTableLayout.tableRows[0].tableCells = [createMockCalcTableCell(0, "↓[Ξstate]", 1), createMockCalcTableCell(1, "[∑customer_id]", 3)];
      fixture = TestBed.createComponent(CalcGroupOption);
      comp = <CalcGroupOption>fixture.componentInstance;
      comp.cellBinding = <CellBindingInfo> {
         order: createOrderModel(),
         topn: createMockTopNModel()
      };
      comp.field = TestUtils.createMockDataRef("state");
      comp.aggregates = () => [createMockAggregateRef("customer_id")];
      comp.ngOnChanges({});
      fixture.detectChanges();

      let sort = fixture.debugElement.queryAll(By.css(".popup-editor__container select"))[0].queryAll(By.css("option"));
      fixture.detectChanges();

      expect(sort.map(item => TestUtils.toString(item.nativeElement.textContent.trim()))).toEqual(BESIC_SORTOPTION.concat(AGG_SORTOPTION));
   });

   //ranking Input, Of combobox should be disabled if ranking is None
   it("Test ranking Input, ranking Of and Group All other status", () => {
      calcTableLayout.tableRows[0].tableCells = [createMockCalcTableCell(0, "↓[Ξstate]", 1), createMockCalcTableCell(1, "[∑customer_id]", 3)];
      fixture = TestBed.createComponent(CalcGroupOption);
      comp = <CalcGroupOption>fixture.componentInstance;
      comp.cellBinding = <CellBindingInfo> {
         order: createOrderModel(),
         topn: createMockTopNModel()
      };
      comp.field = TestUtils.createMockDataRef("state");
      comp.cellBinding.timeSeries = false;
      comp.aggregates = () => [createMockAggregateRef("customer_id")];
      comp.ngOnChanges({});
      fixture.detectChanges();

      let rankingSelect: HTMLElement = fixture.nativeElement.querySelector("div.popup-editor__container .ranking_label_id select");
      let topN: HTMLElement = fixture.nativeElement.querySelector("div.popup-editor__container .topn_label_id > input");
      let ofSelect: HTMLElement = fixture.nativeElement.querySelector(".of_label_id > select");
      let groupOther: HTMLElement = fixture.nativeElement.querySelector("div.popup-editor__container .group_other_id input[type=checkbox]");
      expect(rankingSelect.getAttribute("ng-reflect-is-disabled")).toEqual("false");
      expect(topN.getAttribute("ng-reflect-is-disabled")).toEqual("true");
      expect(ofSelect.getAttribute("ng-reflect-is-disabled")).toEqual("true");
      expect(groupOther).toBeNull();

      comp.topN.type = StyleConstants.TOP_N;
      fixture.detectChanges();
      fixture.whenStable().then(() => {
         topN = fixture.nativeElement.querySelector("div.popup-editor__container .topn_label_id > input");
         ofSelect = fixture.nativeElement.querySelector(".of_label_id > select");
         groupOther = fixture.nativeElement.querySelector("div.popup-editor__container .group_other_id input[type=checkbox]");
         expect(topN.getAttribute("ng-reflect-is-disabled")).toEqual("false");
         expect(ofSelect.getAttribute("ng-reflect-is-disabled")).toEqual("false");
         expect(groupOther).not.toBeNull();
      });
   });

   //for Bug #17284, Of combobox load empty; Bug #17359, Of combobox not show selected value.
   it("Test Ranking Of and Ranking Input value load", () => {
      calcTableLayout.tableRows[0].tableCells = [createMockCalcTableCell(0, "↓[Ξstate]", 1), createMockCalcTableCell(1, "[∑customer_id]", 3)];
      fixture = TestBed.createComponent(CalcGroupOption);
      comp = <CalcGroupOption>fixture.componentInstance;
      comp.cellBinding = <CellBindingInfo> {
         order: createOrderModel(),
         topn: createMockTopNModel()
      };
      comp.topN.type = StyleConstants.TOP_N;
      comp.field = TestUtils.createMockDataRef("state");
      let agg: AggregateRef = createMockAggregateRef("customer_id");
      agg.view = "Sum(customer_id)";
      comp.aggregates = () => [agg];
      comp.ngOnChanges({});
      fixture.detectChanges();

      fixture.whenStable().then(() => {
         let ofSelect: Element = fixture.nativeElement.querySelector(".of_label_id > select");
         let ofSelectedOpt: Element = ofSelect.querySelector("option:checked");
         let rankingInput: HTMLInputElement = fixture.nativeElement.querySelector("div.popup-editor__container .topn_label_id > input");
         expect(ofSelectedOpt.textContent.trim()).toEqual("Sum(customer_id)");
         expect(ofSelect.childElementCount).toEqual(1);
         expect(rankingInput.value).toEqual("3");
      });
   });

   //for bug#16612, name group edit button should be disabled if no name group selected.
   it("Test Name Group Edit button status", () => {
      calcTableLayout.tableRows[0].tableCells = [createMockCalcTableCell(0, "↓[Ξstate]", 1), createMockCalcTableCell(1, "[∑customer_id]", 3)];
      fixture = TestBed.createComponent(CalcGroupOption);
      comp = <CalcGroupOption>fixture.componentInstance;
      comp.cellBinding = <CellBindingInfo> {
         order: createOrderModel(),
         topn: createMockTopNModel()
      };
      comp.field = TestUtils.createMockDataRef("state");
      comp.aggregates = () => [createMockAggregateRef("customer_id")];
      comp.ngOnChanges({});
      fixture.detectChanges();

      let nameGroupEditButton: Element = fixture.nativeElement.querySelector("div.popup-editor__container button.form-control");
      expect(nameGroupEditButton.attributes["disabled"].value).toBe("");

      comp.order.info.name = "g1";
      fixture.detectChanges();

      fixture.whenStable().then(() => {
         nameGroupEditButton = fixture.nativeElement.querySelector("div.popup-editor__container button");
         expect(nameGroupEditButton.hasAttribute("disabled")).toBe(false);
      });
   });

   xit("Test date level select combobox load", () => {
      fixture = TestBed.createComponent(CalcGroupOption);
      comp = <CalcGroupOption>fixture.componentInstance;
      comp.cellBinding = <CellBindingInfo> {
         order: createOrderModel(),
         topn: createMockTopNModel()
      };
      comp.field = TestUtils.createMockDataRef("orderdate");
      comp.field.dataType = XSchema.DATE;
      comp.aggregates = () => [];
      fixture.detectChanges();

      let fixedDropdown = document.getElementsByTagName("fixed-dropdown")[0];
      let dateLevelOpts: any = fixedDropdown.querySelectorAll("div.dropdown-container .dropdown-item");
      expect(dateLevelOpts.map(item => TestUtils.toString(item.nativeElement.textContent.trim()))).toEqual(DATE_OPTIONS);

      let dateInput: Element = fixture.nativeElement.querySelector("div.popup-editor__container .periods_label_id > input");
      expect(dateInput).not.toBeNull();

      let spans = fixture.debugElement.queryAll(By.css(".level_label_id span"));
      expect(spans.map(item => TestUtils.toString(item.nativeElement.textContent.trim()))).toContain("Level");

      comp.field.dataType = XSchema.TIME;
      fixture.detectChanges();

      dateLevelOpts = fixture.debugElement.queryAll(By.css("div.popup-editor__container .level_label_id select > option"));
      dateInput = fixture.nativeElement.querySelector("div.popup-editor__container .periods_label_id > input");
      expect(dateLevelOpts.map(item => TestUtils.toString(item.nativeElement.textContent.trim()))).toEqual(TIME_OPTIONS);
      expect(dateInput).not.toBeNull();

      comp.field.dataType = XSchema.TIME_INSTANT;
      fixture.detectChanges();

      dateLevelOpts = fixture.debugElement.queryAll(By.css("div.popup-editor__container .level_label_id select > option"));
      dateInput = fixture.nativeElement.querySelector("div.popup-editor__container .periods_label_id > input");
      expect(dateLevelOpts.map(item => TestUtils.toString(item.nativeElement.textContent.trim()))).toEqual(DATETIME_OPTIONS);
      expect(dateInput).not.toBeNull();
   });

   //for Bug #17360, summarize combobox load empty
   it("Test summarize combobox status and load", () => {
      calcTableLayout.tableRows[0].tableCells = [createMockCalcTableCell(0, "↓[Ξstate]", 1), createMockCalcTableCell(1, "[∑customer_id]", 3)];
      fixture = TestBed.createComponent(CalcGroupOption);
      comp = <CalcGroupOption>fixture.componentInstance;
      comp.cellBinding = <CellBindingInfo> {
         order: createOrderModel(),
         topn: createMockTopNModel()
      };
      comp.field = TestUtils.createMockDataRef("state");
      comp.aggregates = () => [createMockAggregateRef("customer_id")];
      comp.order.type = XConstants.SORT_VALUE_ASC;
      comp.ngOnChanges({});
      fixture.detectChanges();

      fixture.whenStable().then(() => {
         let summarizedSelect: Element = fixture.nativeElement.querySelector("div.popup-editor__container .by_label_id select");
         expect(summarizedSelect).toBeDefined();
         expect(summarizedSelect.childElementCount).toEqual(1);
      });
   });

   // for Bug #17900, date inputfield should not accept 0
   it("Test date inputField valid check on calc group pane", () => {
      fixture = TestBed.createComponent(CalcGroupOption);
      comp = <CalcGroupOption>fixture.componentInstance;
      comp.cellBinding = <CellBindingInfo> {
         order: createOrderModel(),
         topn: createMockTopNModel()
      };
      comp.order.option = XConstants.YEAR_DATE_GROUP;
      comp.field = TestUtils.createMockDataRef("orderdate");
      comp.field.dataType = XSchema.DATE;
      comp.cellBinding.timeSeries = false;
      comp.aggregates = () => [];
      fixture.detectChanges();

      let dateInput: HTMLInputElement = fixture.nativeElement.querySelector("div.popup-editor__container .periods_label_id > input");
      dateInput.value = "0";
      dateInput.dispatchEvent(new Event("input"));
      fixture.detectChanges();
      let errorMsg: Element = fixture.nativeElement.querySelector("div.popup-editor__container .alert-danger");
      expect(TestUtils.toString(errorMsg.textContent.trim())).toEqual("viewer.dialog.calcTableAdvance.periodsPositiveNumber");
   });

   //for Bug #18744, 'null' item is missing from named group
   // bad test, use debug element or snapshot to test dom
   // it("'null' item is missing from named group", () => {
   //    fixture = TestBed.createComponent(CalcGroupOption);
   //    comp = <CalcGroupOption>fixture.componentInstance;
   //    comp.cellBinding = <CellBindingInfo> {
   //       order: createOrderModel(),
   //       topn: createMockTopNModel()
   //    };
   //    comp.field = TestUtils.createMockDataRef("state");
   //    comp.aggregates = () => [];
   //    fixture.detectChanges();
   //
   //    let namedGroupCombo = fixture.debugElement.queryAll(By.css(".popup-editor__container .namedGroup_label_id select > option"));
   //    expect(namedGroupCombo.length).toEqual(2);
   //    expect(namedGroupCombo.map(item => TestUtils.toString(item.nativeElement.textContent.trim()))).toEqual(["", "Customize"]);
   // });

   //for Bug #19422, Bug #18663 should get correct sortValue when changing binding
   it("Should get correct sortValue when changing binding", () => {
      fixture = TestBed.createComponent(CalcGroupOption);
      comp = <CalcGroupOption>fixture.componentInstance;
      comp.cellBinding = <CellBindingInfo> {
         order: createOrderModel(),
         topn: createMockTopNModel()
      };
      comp.field = TestUtils.createMockDataRef("state");
      let idAgg: AggregateRef = createMockAggregateRef("id");
      idAgg.view = "Sum(id)";
      comp.aggregates = () => [idAgg];
      comp.order.type = StyleConstants.SORT_VALUE_ASC;
      comp.ngOnChanges({});
      fixture.detectChanges();

      expect(comp.order.sortValue).toEqual("Sum(id)");
      expect(comp.order.sortCol).toEqual(0);

      let ordernoAgg: AggregateRef = createMockAggregateRef("orderno");
      ordernoAgg.view = "Sum(orderno)";
      comp.aggregates = () => [idAgg, ordernoAgg];
      comp.ngOnChanges({});
      fixture.detectChanges();

      expect(comp.order.sortValue).toEqual("Sum(id)");
      expect(comp.order.sortCol).toEqual(0);

      comp.aggregates = () => [];
      comp.ngOnChanges({});
      fixture.detectChanges();

      expect(comp.order.sortValue).toBeNull();
      expect(comp.order.sortCol).toEqual(-1);
   });

   //for Bug #20099 should disabled name group edit button when select ng
   it("Should disabled name group edit button when select build in name group", () => {
      fixture = TestBed.createComponent(CalcGroupOption);
      comp = <CalcGroupOption>fixture.componentInstance;
      comp.cellBinding = <CellBindingInfo> {order: createOrderModel()};
      comp.order.info.name = "stringNG";
      comp.field = {};
      editorService = fixture.debugElement.injector.get(VSCalcTableEditorService);
      editorService.namedGroups = ["stringNG"];

      fixture.detectChanges();
      expect(comp.isDisabledNamedGroup()).toBeTruthy();
   });

   //for Bug #20308, date grouping interval enabled status
   it("Date grouping interval status should be right", () => {
      fixture = TestBed.createComponent(CalcGroupOption);
      comp = <CalcGroupOption>fixture.componentInstance;
      comp.cellBinding = <CellBindingInfo> {
         order: createOrderModel(),
         topn: createMockTopNModel(),
         timeSeries: false
      };
      comp.field = TestUtils.createMockDataRef("orderdate");
      comp.field.dataType = XSchema.DATE;
      comp.order.option = XConstants.QUARTER_OF_YEAR_DATE_GROUP;
      comp.aggregates = () => [];
      fixture.detectChanges();
      let periodInput: HTMLInputElement = fixture.nativeElement.querySelector(".periods_label_id input");
      expect(periodInput.getAttribute("ng-reflect-is-disabled")).toEqual("true");
   });

   //for Bug #20239, Bug #20130
   it("named group should be kept", () => {
      let fixture1 = TestBed.createComponent(TestApp);
      fixture1.detectChanges();

      fixture1.whenStable().then(() => {
         let calcGroupOptoin = fixture1.componentInstance.calcGroupOption;
         editorService = fixture1.debugElement.injector.get(VSCalcTableEditorService);
         editorService.namedGroups = ["stringNG"];
         calcGroupOptoin.changeNamedGroup("stringNG");
         fixture1.detectChanges();
         expect(fixture1.componentInstance.cellBinding.order.info.name).toEqual("stringNG");

         //Bug #20130
         calcGroupOptoin.changeNamedGroup("");
         fixture1.detectChanges();
         expect(fixture1.componentInstance.cellBinding.order.info).toBeNull();
      });
   });
});
