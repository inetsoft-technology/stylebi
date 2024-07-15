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
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { DataRef } from "../../../common/data/data-ref";
import { XSchema } from "../../../common/data/xschema";
import { TestUtils } from "../../../common/test/test-utils";
import { CalcTableBindingModel } from "../../data/table/calc-table-binding-model";
import { CellBindingInfo } from "../../data/table/cell-binding-info";
import { BindingService } from "../../services/binding.service";
import { VSCalcTableEditorService } from "../../services/table/vs-calc-table-editor.service";
import { AggregateFormula } from "../../util/aggregate-formula";
import { AssetUtil } from "../../util/asset-util";
import { CalcAggregateOption } from "./calc-aggregate-option.component";

describe("Calc Aggregate Option Unit Test", () => {
   let createMockDataRef: (name: string) => DataRef = (name: string) => {
      let ref: DataRef = TestUtils.createMockDataRef(name);
      ref.formulaName = AggregateFormula.SUM.formulaName;
      return ref;
   };
   let dataRef = createMockDataRef("customer_id");
   let createMockCalcTableBindingModel: () => CalcTableBindingModel = () => {
      let bindingModel =  Object.assign({
      }, TestUtils.createMockBaseBindingModel("calctable"));
      let dataRefs = new Array<DataRef>();
      dataRefs.push(dataRef);
      bindingModel.availableFields = dataRefs;
      return bindingModel;
   };

   let cellBinding = new CellBindingInfo();
   let bindingService = { getBindingModel: jest.fn(() => createMockCalcTableBindingModel()),
      isGrayedOutField: jest.fn()};
   let editorService = {
      getCellBinding: jest.fn(() => cellBinding),
      hasRowGroup: jest.fn(),
      hasColGroup: jest.fn(),
      getGroupNum: jest.fn()
   };

   let fixture: ComponentFixture<CalcAggregateOption>;
   let calcAggregateOpts: CalcAggregateOption;
   let element: any;

   beforeEach(() => {
      TestBed.configureTestingModule({
         imports: [FormsModule, ReactiveFormsModule, NgbModule],
         declarations: [CalcAggregateOption],
         providers: [{
            provide: BindingService, useValue: bindingService
         },
         {
            provide: VSCalcTableEditorService, useValue: editorService
         }]
      });
      TestBed.compileComponents;
      fixture = TestBed.createComponent(CalcAggregateOption);
      calcAggregateOpts = <CalcAggregateOption>fixture.componentInstance;
      element = fixture.nativeElement;
   });

   it("Test enabled and disable status of percent combobox on aggregate option pane", () => {
      editorService.hasRowGroup.mockImplementation(() => true);
      editorService.hasColGroup.mockImplementation(() => false);
      editorService.getGroupNum.mockImplementation(() => 1);
      calcAggregateOpts.baseFormula = AggregateFormula.SUM.formulaName;
      calcAggregateOpts.ngOnChanges(null);
      fixture.detectChanges();

      let percentSelect: Element = element.querySelectorAll("div.popup-editor__container select")[2];
      expect(percentSelect.getAttribute("ng-reflect-is-disabled")).toBe("false");

      editorService.hasRowGroup.mockImplementation(() => false);
      editorService.hasColGroup.mockImplementation(() => true);
      editorService.getGroupNum.mockImplementation(() => 1);
      calcAggregateOpts.baseFormula = AggregateFormula.FIRST.formulaName;
      fixture.detectChanges();

      percentSelect = element.querySelectorAll("div.popup-editor__container select")[2];
      expect(percentSelect.getAttribute("ng-reflect-is-disabled")).toBe("true");

      editorService.hasRowGroup.mockImplementation(() => false);
      editorService.hasColGroup.mockImplementation(() => false);
      editorService.getGroupNum.mockImplementation(() => 0);
      fixture.detectChanges();

      percentSelect = element.querySelectorAll("div.popup-editor__container select")[2];
      expect(percentSelect.getAttribute("ng-reflect-is-disabled")).toBe("true");
   });

   it("Test diabled and enabled status of with combobox on aggregate option pane", () => {
      calcAggregateOpts.baseFormula = AggregateFormula.SUM.formulaName;
      calcAggregateOpts.ngOnChanges(null);
      fixture.detectChanges();

      let withSelect: Element = element.querySelectorAll("div.popup-editor__container select")[1];
      expect(withSelect.getAttribute("ng-reflect-is-disabled")).toBe("true");

      calcAggregateOpts.baseFormula = AggregateFormula.FIRST.formulaName;
      fixture.detectChanges();

      withSelect = element.querySelectorAll("div.popup-editor__container select")[1];
      expect(withSelect.getAttribute("ng-reflect-is-disabled")).toBe("false");
   });

   //Bug #17884, remove None in with combobox, as it is necessary to set a secondary column.
   //for Bug#16598, with combobox load empty; Bug #17884, None is missing in with combobox
   it("Test With combobox load", () => {
      calcAggregateOpts.baseFormula = AggregateFormula.CORRELATION.formulaName;
      calcAggregateOpts.ngOnChanges(null);
      fixture.detectChanges();

      let withSelect: Element = element.querySelectorAll("div.popup-editor__container select")[1];
      let withOpts: any = withSelect.querySelectorAll("option");
      let noneItem: Element = Array.prototype.slice.call(withOpts).filter(e => e.textContent == "None")[0];
      expect(withSelect.childElementCount).toEqual(1);
      expect(noneItem).toBeUndefined();

      calcAggregateOpts.secondCol = "city";
      fixture.detectChanges();
      withSelect = element.querySelectorAll("div.popup-editor__container select")[1];
      expect(withSelect.getAttribute("ng-reflect-model")).toEqual("city");
   });

   it("Test Percent combobox load", () => {
      editorService.hasRowGroup.mockImplementation(() => true);
      editorService.hasColGroup.mockImplementation(() => false);
      fixture.detectChanges();

      let percentSelect: Element = element.querySelectorAll("div.popup-editor__container select")[2];
      expect(percentSelect.childElementCount).toEqual(calcAggregateOpts.rowPercent.length);
      expect(percentSelect.lastElementChild.textContent).toEqual(calcAggregateOpts.rowPercent[calcAggregateOpts.rowPercent.length - 1].label);

      editorService.hasRowGroup.mockImplementation(() => false);
      editorService.hasColGroup.mockImplementation(() => true);
      fixture.detectChanges();

      percentSelect = element.querySelectorAll("div.popup-editor__container select")[2];
      expect(percentSelect.childElementCount).toEqual(calcAggregateOpts.colPercent.length);
      expect(percentSelect.lastElementChild.textContent).toEqual(calcAggregateOpts.colPercent[calcAggregateOpts.colPercent.length - 1].label);

      editorService.hasRowGroup.mockImplementation(() => true);
      editorService.hasColGroup.mockImplementation(() => true);
      fixture.detectChanges();

      percentSelect = element.querySelectorAll("div.popup-editor__container select")[2];
      let noneItem = Array.prototype.slice.call(percentSelect.children).filter(e => e.textContent.indexOf("None") != -1 )[0];
      expect(percentSelect.childElementCount).toEqual(calcAggregateOpts.rowColPercent.length);
      expect(percentSelect.lastElementChild.textContent).toEqual(calcAggregateOpts.rowColPercent[calcAggregateOpts.rowColPercent.length - 1].label);
      expect(noneItem).toBeDefined();
   });

   //for Bug #17401, Bug #18481, aggregate combobox load error on boolean and date type.
   it("Test Aggregate combobox load with different data type", () => {
      cellBinding.formula = "";
      calcAggregateOpts.dataRef = TestUtils.createMockDataRef("col1");
      calcAggregateOpts.ngOnChanges(null);
      fixture.detectChanges();

      let aggSelect: Element = element.querySelectorAll("div.popup-editor__container select")[0];
      expect(aggSelect.childElementCount).toEqual(AssetUtil.NUMBER_FORMULAS.length + 1);
      expect(aggSelect.lastElementChild.textContent.trim()).toEqual(AssetUtil.NUMBER_FORMULAS[AssetUtil.NUMBER_FORMULAS.length - 1].label);

      calcAggregateOpts.dataRef.dataType = XSchema.DATE;
      calcAggregateOpts.ngOnChanges(null);
      fixture.detectChanges();

      aggSelect = element.querySelectorAll("div.popup-editor__container select")[0];
      expect(aggSelect.childElementCount).toEqual(AssetUtil.DATE_FORMULAS.length + 1);
      expect(aggSelect.lastElementChild.textContent.trim()).toEqual(AssetUtil.DATE_FORMULAS[AssetUtil.DATE_FORMULAS.length - 1].label);

      calcAggregateOpts.dataRef.dataType = XSchema.BOOLEAN;
      calcAggregateOpts.ngOnChanges(null);
      fixture.detectChanges();

      aggSelect = element.querySelectorAll("div.popup-editor__container select")[0];
      let noneItem = Array.prototype.slice.call(aggSelect.children).filter(e => e.textContent.indexOf("None") != -1)[0];
      expect(aggSelect.childElementCount).toEqual(AssetUtil.BOOL_FORMULAS.length + 2);
      expect(aggSelect.lastElementChild.textContent.trim()).toEqual(AggregateFormula.NTH_MOST_FREQUENT.label);
      expect(noneItem).toBeDefined();
   });
});
