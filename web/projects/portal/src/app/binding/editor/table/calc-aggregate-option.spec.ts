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
import { By } from "@angular/platform-browser";
import { NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { DataRef } from "../../../common/data/data-ref";
import { XSchema } from "../../../common/data/xschema";
import { TestUtils } from "../../../common/test/test-utils";
import { CalcTableBindingModel } from "../../data/table/calc-table-binding-model";
import { CellBindingInfo } from "../../data/table/cell-binding-info";
import { BindingService } from "../../services/binding.service";
import { VSCalcTableEditorService } from "../../services/table/vs-calc-table-editor.service";
import { CustomSelectModule } from "../../../widget/custom-select/custom-select.module";
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
         imports: [FormsModule, ReactiveFormsModule, NgbModule, CustomSelectModule],
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

      let percentSelect: Element = element.querySelectorAll("div.popup-editor__container custom-select")[2];
      expect(percentSelect.classList.contains("is-disabled")).toBe(false);

      editorService.hasRowGroup.mockImplementation(() => false);
      editorService.hasColGroup.mockImplementation(() => true);
      editorService.getGroupNum.mockImplementation(() => 1);
      calcAggregateOpts.baseFormula = AggregateFormula.FIRST.formulaName;
      fixture.detectChanges();

      percentSelect = element.querySelectorAll("div.popup-editor__container custom-select")[2];
      expect(percentSelect.classList.contains("is-disabled")).toBe(true);

      editorService.hasRowGroup.mockImplementation(() => false);
      editorService.hasColGroup.mockImplementation(() => false);
      editorService.getGroupNum.mockImplementation(() => 0);
      fixture.detectChanges();

      percentSelect = element.querySelectorAll("div.popup-editor__container custom-select")[2];
      expect(percentSelect.classList.contains("is-disabled")).toBe(true);
   });

   it("Test diabled and enabled status of with combobox on aggregate option pane", () => {
      calcAggregateOpts.baseFormula = AggregateFormula.SUM.formulaName;
      calcAggregateOpts.ngOnChanges(null);
      fixture.detectChanges();

      let withSelect: Element = element.querySelectorAll("div.popup-editor__container custom-select")[1];
      expect(withSelect.classList.contains("is-disabled")).toBe(true);

      calcAggregateOpts.baseFormula = AggregateFormula.FIRST.formulaName;
      fixture.detectChanges();

      withSelect = element.querySelectorAll("div.popup-editor__container custom-select")[1];
      expect(withSelect.classList.contains("is-disabled")).toBe(false);
   });

   //Bug #17884, remove None in with combobox, as it is necessary to set a secondary column.
   //for Bug#16598, with combobox load empty; Bug #17884, None is missing in with combobox
   it("Test With combobox load", () => {
      calcAggregateOpts.baseFormula = AggregateFormula.CORRELATION.formulaName;
      calcAggregateOpts.ngOnChanges(null);
      fixture.detectChanges();

      let withSelects = fixture.debugElement.queryAll(By.css("div.popup-editor__container custom-select"));
      let withOptions = (withSelects[1]?.componentInstance as any)?.options ?? [];
      let noneItem = withOptions.filter((o: any) => o.label === "None")[0];
      expect(withOptions.length).toEqual(1);
      expect(noneItem).toBeUndefined();

      calcAggregateOpts.secondCol = "city";
      fixture.detectChanges();
      const withSelectEl = fixture.debugElement.queryAll(By.css("div.popup-editor__container custom-select"))[1];
      expect(withSelectEl.nativeElement.getAttribute("ng-reflect-model")).toEqual("city");
   });

   it("Test Percent combobox load", () => {
      editorService.hasRowGroup.mockImplementation(() => true);
      editorService.hasColGroup.mockImplementation(() => false);
      fixture.detectChanges();

      let percentSelects = fixture.debugElement.queryAll(By.css("div.popup-editor__container custom-select"));
      let percentOptions = (percentSelects[2]?.componentInstance as any)?.options ?? [];
      expect(percentOptions.length).toEqual(calcAggregateOpts.rowPercent.length);
      expect(percentOptions[percentOptions.length - 1].label).toEqual(calcAggregateOpts.rowPercent[calcAggregateOpts.rowPercent.length - 1].label);

      editorService.hasRowGroup.mockImplementation(() => false);
      editorService.hasColGroup.mockImplementation(() => true);
      fixture.detectChanges();

      percentSelects = fixture.debugElement.queryAll(By.css("div.popup-editor__container custom-select"));
      percentOptions = (percentSelects[2]?.componentInstance as any)?.options ?? [];
      expect(percentOptions.length).toEqual(calcAggregateOpts.colPercent.length);
      expect(percentOptions[percentOptions.length - 1].label).toEqual(calcAggregateOpts.colPercent[calcAggregateOpts.colPercent.length - 1].label);

      editorService.hasRowGroup.mockImplementation(() => true);
      editorService.hasColGroup.mockImplementation(() => true);
      fixture.detectChanges();

      percentSelects = fixture.debugElement.queryAll(By.css("div.popup-editor__container custom-select"));
      percentOptions = (percentSelects[2]?.componentInstance as any)?.options ?? [];
      let noneItem = percentOptions.filter((o: any) => o.label.indexOf("None") !== -1)[0];
      expect(percentOptions.length).toEqual(calcAggregateOpts.rowColPercent.length);
      expect(percentOptions[percentOptions.length - 1].label).toEqual(calcAggregateOpts.rowColPercent[calcAggregateOpts.rowColPercent.length - 1].label);
      expect(noneItem).toBeDefined();
   });

   //for Bug #17401, Bug #18481, aggregate combobox load error on boolean and date type.
   it("Test Aggregate combobox load with different data type", () => {
      cellBinding.formula = "";
      calcAggregateOpts.dataRef = TestUtils.createMockDataRef("col1");
      calcAggregateOpts.ngOnChanges(null);
      fixture.detectChanges();

      let aggSelects = fixture.debugElement.queryAll(By.css("div.popup-editor__container custom-select"));
      let aggOptions = (aggSelects[0]?.componentInstance as any)?.options ?? [];
      expect(aggOptions.length).toEqual(AssetUtil.NUMBER_FORMULAS.length + 1);
      expect(aggOptions[aggOptions.length - 1].label.trim()).toEqual(AssetUtil.NUMBER_FORMULAS[AssetUtil.NUMBER_FORMULAS.length - 1].label);

      calcAggregateOpts.dataRef.dataType = XSchema.DATE;
      calcAggregateOpts.ngOnChanges(null);
      fixture.detectChanges();

      aggSelects = fixture.debugElement.queryAll(By.css("div.popup-editor__container custom-select"));
      aggOptions = (aggSelects[0]?.componentInstance as any)?.options ?? [];
      expect(aggOptions.length).toEqual(AssetUtil.DATE_FORMULAS.length + 1);
      expect(aggOptions[aggOptions.length - 1].label.trim()).toEqual(AssetUtil.DATE_FORMULAS[AssetUtil.DATE_FORMULAS.length - 1].label);

      calcAggregateOpts.dataRef.dataType = XSchema.BOOLEAN;
      calcAggregateOpts.ngOnChanges(null);
      fixture.detectChanges();

      aggSelects = fixture.debugElement.queryAll(By.css("div.popup-editor__container custom-select"));
      aggOptions = (aggSelects[0]?.componentInstance as any)?.options ?? [];
      let noneItem = aggOptions.filter((o: any) => o.label.indexOf("None") !== -1)[0];
      expect(aggOptions.length).toEqual(AssetUtil.BOOL_FORMULAS.length + 2);
      expect(aggOptions[aggOptions.length - 1].label.trim()).toEqual(AggregateFormula.NTH_MOST_FREQUENT.label);
      expect(noneItem).toBeDefined();
   });
});
