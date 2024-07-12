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
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { async, ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { By } from "@angular/platform-browser";
import { NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { UIContextService } from "../../../common/services/ui-context.service";
import { DropDownTestModule } from "../../../common/test/test-module";
import { TestUtils } from "../../../common/test/test-utils";
import { FixedDropdownDirective } from "../../../widget/fixed-dropdown/fixed-dropdown.directive";
import { DynamicComboBox } from "../../../widget/dynamic-combo-box/dynamic-combo-box.component";
import { BAggregateRef } from "../../data/b-aggregate-ref";
import { BindingService } from "../../services/binding.service";
import { AggregateFormula } from "../../util/aggregate-formula";
import { FormulaOption } from "../formula-option.component";
import { AggregateOption } from "./aggregate-option.component";

describe("Aggregate Option Unit Test", () => {
   let createMockAggregateRef: (name: string) => BAggregateRef = (name: string) => {
      let aggr: BAggregateRef = TestUtils.createMockBAggregateRef(name);
      aggr.formula = AggregateFormula.SUM.formulaName;
      return aggr;
   };
   let uiContextService: any;
   let bindingService: any;

   beforeEach(async(() => {
      uiContextService = { isAdhoc: jest.fn() };
      bindingService = { assemblyName: null };
      TestBed.configureTestingModule({
         imports: [
            FormsModule, ReactiveFormsModule, NgbModule, DropDownTestModule
         ],
         declarations: [
            AggregateOption, FormulaOption, DynamicComboBox, FixedDropdownDirective
         ],
         providers: [
            { provide: UIContextService, useValue: uiContextService },
            { provide: BindingService, useValue: bindingService }
         ],
         schemas: [NO_ERRORS_SCHEMA]
      })
      .compileComponents();
   }));

   //for Bug #17907, percent combobox not visible when select Count aggregate
   it("Percent combobox should visible when select Count aggregate", () => {
      let fixture: ComponentFixture<AggregateOption> = TestBed.createComponent(AggregateOption);
      let aggOptions: AggregateOption = <AggregateOption>fixture.componentInstance;
      uiContextService.isAdhoc.mockImplementation(() => false);
      aggOptions.aggregate =  createMockAggregateRef("customer_id");
      aggOptions.groupNum = 1;
      fixture.detectChanges();

      let formulaOption: FormulaOption = fixture.debugElement.query(By.directive(FormulaOption)).componentInstance;
      formulaOption.changeFormulaValue(AggregateFormula.COUNT_ALL.formulaName);
      fixture.detectChanges();

      fixture.whenStable().then(() => {
         let percentLbl: Element = fixture.nativeElement.querySelectorAll(".aggregatePane .percentage_label_id");
         expect(percentLbl).not.toBeNull();
      });
   });

   // for Bug #17907, with combobox not visible when select weighted average aggregate
   it("With combobox should visible when select Weighted Average aggreate", () => {
      let agg = createMockAggregateRef("customer_id");
      let fixture: ComponentFixture<AggregateOption> = TestBed.createComponent(AggregateOption);
      let aggOptions: AggregateOption = <AggregateOption>fixture.componentInstance;
      uiContextService.isAdhoc.mockImplementation(() => true);
      aggOptions.aggregate = agg;
      aggOptions.groupNum = 1;
      fixture.detectChanges();

      let formulaOption: FormulaOption = fixture.debugElement.query(By.directive(FormulaOption)).componentInstance;
      formulaOption.availableFields = [TestUtils.createMockDataRef("state"), agg];
      formulaOption.changeFormulaValue(AggregateFormula.WEIGHTED_AVG.formulaName);
      fixture.detectChanges();

      fixture.whenStable().then(() => {
         let withLbl: Element = fixture.nativeElement.querySelectorAll(".aggregatePane .with_label_id label");
         expect(withLbl).not.toBeNull();
      });
   });

   // for Bug #16248, Bug #18922 aggregate combobox load error
   // bad test, don't test external components and use debug elements to test dom when necessary
   // it("Test aggregate combobox load in viewsheet aggregate option pane", (done) => {
   //    let fixture: ComponentFixture<AggregateOption> = TestBed.createComponent(AggregateOption);
   //    let aggOptions: AggregateOption = <AggregateOption>fixture.componentInstance;
   //    uiContextService.isAdhoc.mockImplementation(() => false);
   //    aggOptions.aggregate = createMockAggregateRef("customer_id");
   //    aggOptions.groupNum = 1;
   //    fixture.detectChanges();
   //
   //    let formulaList = aggOptions.formulaObjs;
   //    expect(formulaList.length).not.toEqual(0);
   //
   //    let aggCombo: HTMLElement = TestUtils.getDynamicComboDiv(fixture.nativeElement.querySelector(".aggregate_id"));
   //    aggCombo.click();
   //    fixture.detectChanges();
   //
   //    fixture.whenStable().then(() => {
   //       let fixedDropdown = document.getElementsByTagName("fixed-dropdown")[0];
   //       let items: any = fixedDropdown.querySelectorAll("div.dropdown-container .dropdown-item");
   //       expect(items.length).toEqual(formulaList.length);
   //       expect(items[items.length - 1].textContent.trim()).toEqual(formulaList[formulaList.length - 1].label);
   //       // items.forEach((item: HTMLElement) => {
   //       //    expect(item.textContent.trim()).not.toEqual(AggregateFormula.NTH_LARGEST.label, "adhoc formula option item should not load in vs aggregate option");
   //       // });
   //
   //       done();
   //    });
   // });

   //for Bug #17513, Bug #18922 some option missing in aggregate options.
   // bad test, don't test external components and use debug elements to test dom when necessary
   // it("Test aggregate combobox load in adhoc aggregate option pane", (done) => {
   //    let fixture: ComponentFixture<AggregateOption> = TestBed.createComponent(AggregateOption);
   //    let aggOptions: AggregateOption = <AggregateOption>fixture.componentInstance;
   //    uiContextService.isAdhoc.mockImplementation(() => true);
   //    aggOptions.aggregate = createMockAggregateRef("customer_id");
   //    aggOptions.groupNum = 1;
   //    fixture.detectChanges();
   //
   //    let formulaList = aggOptions.formulaObjs;
   //    expect(formulaList.length).not.toEqual(0);
   //
   //    let aggCombo: HTMLElement = TestUtils.getDynamicComboDiv(fixture.nativeElement.querySelector(".aggregate_id"));
   //    aggCombo.click();
   //    fixture.detectChanges();
   //
   //    fixture.whenStable().then(() => {
   //       let fixedDropdown = document.getElementsByTagName("fixed-dropdown")[0];
   //       let aggItems: any = fixedDropdown.querySelectorAll("div.dropdown-container .dropdown-item");
   //       expect(aggItems.length).toEqual(formulaList.length);
   //       expect(aggItems[aggItems.length - 1].textContent.trim()).toEqual(formulaList[formulaList.length - 1].label);
   //       let itemValues = [];
   //       aggItems.forEach((item: HTMLElement) => {
   //          itemValues.push(TestUtils.toString(item.textContent.trim()));
   //       });
   //       expect(itemValues).toContain("NthLargest");
   //       expect(itemValues).toContain("NthMostFrequent");
   //       expect(itemValues).toContain("NthSmallest");
   //
   //       done();
   //    });
   // });

   //for Bug #16298, Bug #17771, Bug #10639, Bug #16958, with combobox is empty by default, Bug #17947, remove None in with combobox.
   // needs to be re-written if this test is valuable
   // it("Test With combobox load in aggregate option pane", (done) => {
   //    let agg = createMockAggregateRef("customer_id");
   //    let dataRef = TestUtils.createMockDataRef("state");
   //    let fixture: ComponentFixture<AggregateOption> = TestBed.createComponent(AggregateOption);
   //    let aggOptions: AggregateOption = <AggregateOption>fixture.componentInstance;
   //    uiContextService.isAdhoc.mockImplementation(() => false);
   //    agg.secondaryColumn = dataRef;
   //    aggOptions.aggregate = agg;
   //    aggOptions.availableFields = [agg, dataRef];
   //    aggOptions.groupNum = 1;
   //    fixture.detectChanges();
   //
   //    let formulaOption: FormulaOption = fixture.debugElement.query(By.directive(FormulaOption)).componentInstance;
   //    aggOptions.aggregate = agg;
   //    formulaOption.availableFields = [agg, dataRef];
   //    formulaOption.changeFormulaValue(AggregateFormula.COVARIANCE.formulaName);
   //    fixture.detectChanges();
   //
   //    let withCombo: HTMLElement = TestUtils.getDynamicComboDiv(fixture.nativeElement.querySelector(".with_label_id"));
   //    expect(withCombo.textContent.trim()).toEqual("customer_id");
   //
   //    withCombo.click();
   //    fixture.detectChanges();
   //
   //    fixture.whenStable().then(() => {
   //       let fixedDropdown = document.getElementsByTagName("fixed-dropdown")[0];
   //       let withItems: any = fixedDropdown.querySelectorAll("div.dropdown-container .dropdown-item");
   //       expect(withItems.length).toEqual(2);
   //       withItems.forEach((item: HTMLElement) => {
   //          expect(item.textContent.trim()).not.toEqual("None");
   //       });
   //
   //       done();
   //    });
   // });

   //forBug #17754, Bug #19503, Bug #10634 percent combobox dispaly as empty if no group column.
   // bad test, don't test external components and use debug elements to test dom when necessary
   // it("Test percent combobox load and disabled status", () => {
   //    let fixture: ComponentFixture<AggregateOption> = TestBed.createComponent(AggregateOption);
   //    let aggOptions: AggregateOption = <AggregateOption>fixture.componentInstance;
   //    let aggRef = createMockAggregateRef("customer_id");
   //    aggRef.percentage = null;
   //    aggOptions.aggregate = aggRef;
   //    aggOptions.groupNum = 0;
   //    fixture.detectChanges();
   //
   //    let percentCombo: HTMLElement = TestUtils.getDynamicComboDiv(fixture.nativeElement.querySelector(".percentage_label_id"));
   //    expect(percentCombo.getAttribute("ng-reflect-disabled")).toBe("true", "percent combobox is not disabled when there is no group column");
   //    expect(TestUtils.toString(percentCombo.textContent.trim())).toEqual("None", "percent combobox is empty");
   // });
   //
   // it("Test percent combobox load when there is group", (done) => {
   //    let fixture: ComponentFixture<AggregateOption> = TestBed.createComponent(AggregateOption);
   //    let aggOptions: AggregateOption = <AggregateOption>fixture.componentInstance;
   //    let aggRef = createMockAggregateRef("customer_id");
   //    aggRef.percentage = null;
   //    aggOptions.aggregate = aggRef;
   //    aggOptions.groupNum = 1;
   //    fixture.detectChanges();
   //
   //    let percentCombo: HTMLElement = TestUtils.getDynamicComboDiv(fixture.nativeElement.querySelector(".percentage_label_id"));
   //    percentCombo.click();
   //    fixture.detectChanges();
   //
   //    fixture.whenStable().then(() => {
   //       let fixedDropdown = document.getElementsByTagName("fixed-dropdown")[0];
   //       let percentItems: any = fixedDropdown.querySelectorAll("div.dropdown-container .dropdown-item");
   //       expect(percentItems.length).toEqual(2, "percent combobox content length is not right if there is one group column");
   //       expect(TestUtils.toString(percentItems[percentItems.length - 1].textContent.trim())).toEqual("GrandTotal", "the last percent option content is not right");
   //
   //       done();
   //    });
   // });

   // bad test, don't test external components and use debug elements to test dom when necessary
   // it("Test percent combobox load when there is group", (done) => {
   //    let fixture: ComponentFixture<AggregateOption> = TestBed.createComponent(AggregateOption);
   //    let aggOptions: AggregateOption = <AggregateOption>fixture.componentInstance;
   //    let aggRef = createMockAggregateRef("customer_id");
   //    aggRef.percentage = null;
   //    aggOptions.aggregate = aggRef;
   //    aggOptions.groupNum = 1;
   //    fixture.detectChanges();
   //
   //    let percentCombo: HTMLElement = TestUtils.getDynamicComboDiv(fixture.nativeElement.querySelector(".percentage_label_id"));
   //    percentCombo.click();
   //    fixture.detectChanges();
   //
   //    fixture.whenStable().then(() => {
   //       let fixedDropdown = document.getElementsByTagName("fixed-dropdown")[0];
   //       let percentItems: any = fixedDropdown.querySelectorAll("div.dropdown-container .dropdown-item");
   //       expect(percentItems.length).toEqual(2);
   //       expect(TestUtils.toString(percentItems[percentItems.length - 1].textContent.trim())).toEqual("GrandTotal");
   //
   //       done();
   //    });
   // });

   // bad test, don't test external components and use debug elements to test dom when necessary
   // it("Test percent combobox load when there is group", (done) => {
   //    let fixture: ComponentFixture<AggregateOption> = TestBed.createComponent(AggregateOption);
   //    let aggOptions: AggregateOption = <AggregateOption>fixture.componentInstance;
   //    let aggRef = createMockAggregateRef("customer_id");
   //    aggRef.percentage = null;
   //    aggOptions.aggregate = aggRef;
   //    aggOptions.groupNum = 2;
   //    fixture.detectChanges();
   //
   //    let percentCombo: HTMLElement = TestUtils.getDynamicComboDiv(fixture.nativeElement.querySelector(".percentage_label_id"));
   //    percentCombo.click();
   //    fixture.detectChanges();
   //
   //    fixture.whenStable().then(() => {
   //       let fixedDropdown = document.getElementsByTagName("fixed-dropdown")[0];
   //       let percentItems: any = fixedDropdown.querySelectorAll("div.dropdown-container .dropdown-item");
   //       expect(percentItems.length).toEqual(3);
   //       expect(TestUtils.toString(percentItems[1].textContent.trim())).toEqual("Group");
   //
   //       done();
   //    });
   // });

   // for Bug #10644, Bug #17797 The 'N/P' field load error default value
   it("Test N/P field input value", () => {
      let agg = createMockAggregateRef("customer_id");
      let fixture: ComponentFixture<AggregateOption> = TestBed.createComponent(AggregateOption);
      let aggOptions: AggregateOption = <AggregateOption>fixture.componentInstance;
      uiContextService.isAdhoc.mockImplementation(() => true);
      aggOptions.aggregate = agg;
      aggOptions.groupNum = 1;
      fixture.detectChanges();

      let formulaOption: FormulaOption = fixture.debugElement.query(By.directive(FormulaOption)).componentInstance;
      formulaOption.changeFormulaValue(AggregateFormula.NTH_MOST_FREQUENT.formulaName);
      aggOptions.aggregate.num = 1;
      fixture.detectChanges();

      // let npInput: HTMLInputElement = fixture.nativeElement.querySelector(".aggregatePane input[type=number]");
      // expect(npInput.getAttribute("ng-reflect-model")).toEqual("1");
      //
      // formulaOption.changeFormulaValue(AggregateFormula.PTH_PERCENTILE.formulaName);
      // fixture.detectChanges();
      // npInput = fixture.nativeElement.querySelector(".aggregatePane input[type=number]");
      // expect(npInput.getAttribute("ng-reflect-model")).toEqual("1");
      //
      // aggOptions.aggregate.num = 0;
      // fixture.detectChanges();
      // let errorMsg: Element = fixture.nativeElement.querySelector(".aggregatePane .alert-danger");
      // expect(TestUtils.toString(errorMsg.textContent.trim()))
      //    .toEqual("There will be a default value 1 for formula parameter!");
      //
      // aggOptions.aggregate.num = -1;
      // fixture.detectChanges();
      // errorMsg = fixture.nativeElement.querySelector(".aggregatePane .alert-danger");
      // expect(TestUtils.toString(errorMsg.textContent.trim()))
      //    .toEqual("There will be a default value 1 for formula parameter!");
   });

   //for Bug #19288, aggregate combobox should be disabled when using calcField
   it("Aggregate combobox should be disabled when using calcField", () => {
      let fixture: ComponentFixture<AggregateOption> = TestBed.createComponent(AggregateOption);
      let aggOptions: AggregateOption = <AggregateOption>fixture.componentInstance;
      let aggRef = TestUtils.createMockBAggregateRef("CalcField1");
      aggRef.formulaOptionModel = {
         aggregateStatus: true
      };
      uiContextService.isAdhoc.mockImplementation(() => false);
      aggOptions.groupNum = 1;
      aggOptions.aggregate = aggRef;
      aggOptions.vsId = "crosstabCalcField-15107291924830";
      fixture.detectChanges();

      let aggCombo: Element = fixture.nativeElement.querySelector(".aggregate_id dynamic-combo-box");
      expect(aggCombo.getAttribute("ng-reflect-disable")).toEqual("true");
   });

   //for Bug #19194, Bug #19503 percent should be visible when using expression
   // bad test, don't test external components and use debug elements to test dom when necessary
   // it("Percent should be visible when using expression", () => {
   //    let fixture: ComponentFixture<AggregateOption> = TestBed.createComponent(AggregateOption);
   //    let aggOptions: AggregateOption = <AggregateOption>fixture.componentInstance;
   //    uiContextService.isAdhoc.mockImplementation(() => false);
   //    let aggRef = TestUtils.createMockBAggregateRef("id");
   //    aggRef.formula = "={'Sum'}";
   //    aggRef.percentage = null;
   //    aggOptions.groupNum = 1;
   //    aggOptions.aggregate = aggRef;
   //    aggOptions.vsId = "crosstabCalcField-15107291924830";
   //    fixture.detectChanges();
   //
   //    let percentCombo: HTMLElement = TestUtils.getDynamicComboDiv(fixture.nativeElement.querySelector(".percentage_label_id"));
   //    expect(percentCombo).not.toBeNull("percent combobox should be visible when using expression formula value");
   //    expect(TestUtils.toString(percentCombo.textContent.trim())).toEqual("None", "percent combobox is empty");
   // });
});
