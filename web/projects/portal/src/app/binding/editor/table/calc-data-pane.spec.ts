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
import { ElementRef, NO_ERRORS_SCHEMA, Renderer2 } from "@angular/core";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { NgbModal, NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { TestUtils } from "../../../common/test/test-utils";
import { ComponentTool } from "../../../common/util/component-tool";
import { CellBindingInfo } from "../../data/table/cell-binding-info";
import { BindingService } from "../../services/binding.service";
import { VSCalcTableEditorService } from "../../services/table/vs-calc-table-editor.service";
import { CalcDataPane } from "./calc-data-pane.component";

describe("Calc Data Pane Unit Test", () => {
   let createMockCellBindingInfo: (name?: string) => CellBindingInfo = (name?: string) => {
      let cellBinding = TestUtils.createMockCellBindingInfo(name);
      if(!name) {
         cellBinding.type = 1;
         cellBinding.btype = 2;
         cellBinding.expansion = 0;
      }
      return cellBinding;
   };
   let editorService: any;
   let dialogService: any;
   let bindingService: any;
   let elemRef: any;
   let render: any;
   let changeDetectorRef: any;

   let fixture: ComponentFixture<CalcDataPane>;
   let calcDataPane: CalcDataPane;

   beforeEach(() => {
      editorService = {
         getAggregates: jest.fn(),
         getCellBinding: jest.fn(),
         getCellNames: jest.fn(),
         getCellScript: jest.fn(),
         setCellBinding: jest.fn(),
         getSelectCells: jest.fn(),
         getCellNamesWithDefaults: jest.fn(),
         changeColumnValue: jest.fn()
      };
      bindingService = { isGrayedOutField: jest.fn() };
      dialogService = { open: jest.fn() };
      elemRef = {};
      render = {};
      changeDetectorRef = { detectChanges: jest.fn() };
   });

   function configureTestEnv(): void {
      TestBed.configureTestingModule({
         imports: [
            FormsModule, ReactiveFormsModule, NgbModule
         ],
         declarations: [ CalcDataPane ],
         providers: [
            { provide: VSCalcTableEditorService, useValue: editorService },
            { provide: BindingService, useValue: bindingService },
            { provide: NgbModal, useValue: dialogService },
            { provide: ElementRef, useValue: elemRef },
            { provide: Renderer2, useValue: render }
         ],
         schemas: [NO_ERRORS_SCHEMA]
      }).compileComponents();
   }
   //for Bug #19835
   it("should set right cell type", () => {
      let cellBinding = TestUtils.createMockCellBindingInfo("state");
      editorService.getCellBinding.mockImplementation(() => cellBinding);
      calcDataPane = new CalcDataPane(editorService, dialogService, bindingService, changeDetectorRef);
      calcDataPane.changeCellType("3");
      expect(calcDataPane.cellBinding.type).toEqual(3);
   });

   //for Bug #20138, Bug #20248
   it("should pop up warning for duplicate cell name", () => {
      let showMessageDialog = jest.spyOn(ComponentTool, "showMessageDialog");
      showMessageDialog.mockImplementation(() => Promise.resolve("ok"));
      editorService.getCellNames.mockImplementation(() => ["state", "id"]);
      calcDataPane = new CalcDataPane(editorService, dialogService, bindingService, changeDetectorRef);
      calcDataPane.setCellName("state");

      expect(showMessageDialog).toHaveBeenCalled();
      expect(showMessageDialog.mock.calls[0][2]).toEqual("_#(js:common.duplicateName)");
   });

   //Bug #20121, Bug #20830
   it("edit icon should be gray when it is not support to edit", () => {
      editorService.getCellBinding.mockImplementation(() => createMockCellBindingInfo());
      configureTestEnv();
      fixture = TestBed.createComponent(CalcDataPane);
      calcDataPane = <CalcDataPane>fixture.componentInstance;
      calcDataPane.bindingModel = TestUtils.createMockCalcTableBindingModel();
      fixture.detectChanges();

      let groupEditIcon: Element = fixture.nativeElement.querySelector(".group_edit_id i.edit-icon");
      let summaryEditIcon: Element = fixture.nativeElement.querySelector(".summary_edit_id i.edit-icon");
      expect(groupEditIcon.getAttribute("class")).toContain("icon-disabled");
      expect(summaryEditIcon.getAttribute("class")).toContain("icon-disabled");

      //Bug #20830
      editorService.getSelectCells.mockImplementation(() => [TestUtils.createMockCalcTableCell()]);
      fixture.detectChanges();

      let expandCell: HTMLInputElement = fixture.nativeElement.querySelector(".form-row-checkbox input[type=checkbox]");
      let formulaRadio: HTMLInputElement = fixture.nativeElement.querySelector(".fromula_id input[type=radio]");
      let textRadio: HTMLInputElement = fixture.nativeElement.querySelector(".text_id input[type=radio]");
      let textInput: HTMLInputElement = fixture.nativeElement.querySelector(".text_id input[type=text]");
      let cellName: HTMLInputElement = fixture.nativeElement.querySelector(".cellName_id input");
      expect(expandCell.disabled).toBeFalsy();
      expect(formulaRadio.disabled).toBeFalsy();
      expect(textRadio.checked).toBeTruthy();
      expect(textInput.attributes["ng-reflect-is-disabled"].value).toEqual("false");
      expect(cellName.disabled).toBeFalsy();

      let dataRef1 = TestUtils.createMockDataRef("col1");
      let cellBinding = createMockCellBindingInfo("col1");
      calcDataPane.bindingModel.availableFields = [dataRef1];
      editorService.getCellBinding.mockImplementation(() => cellBinding);
      editorService.getSelectCells.mockImplementation(() => [TestUtils.createMockCalcTableCell("col1")]);
      editorService.getCellNamesWithDefaults.mockImplementation(() => [
         { label: "(none)", value: null },
         { label: "(default)", value: "(default)" },
         { label: "col1", value: "col1" }
      ]);
      fixture.detectChanges();
      groupEditIcon = fixture.nativeElement.querySelector(".group_edit_id i.edit-icon");
      expect(groupEditIcon.getAttribute("class")).not.toContain("icon-disabled");

      cellBinding.btype = 3;
      fixture.detectChanges();
      summaryEditIcon = fixture.nativeElement.querySelector(".summary_edit_id i.edit-icon");
      expect(summaryEditIcon.getAttribute("class")).not.toContain("icon-disabled");
   });

   //Bug #20875
   it("summary and group should be disabled if binding calcfield", () => {
      let cellBinding = createMockCellBindingInfo("CalcField1");
      cellBinding.type = 2;
      cellBinding.btype = 3;
      editorService.getCellBinding.mockImplementation(() => cellBinding);
      configureTestEnv();
      fixture = TestBed.createComponent(CalcDataPane);
      calcDataPane = <CalcDataPane>fixture.componentInstance;
      let bindingModel = TestUtils.createMockCalcTableBindingModel();
      let calcField = TestUtils.createMockDataRef("CalcField1");
      calcField.classType = "CalculateRef";
      bindingModel.availableFields = [calcField];
      calcDataPane.bindingModel = bindingModel;
      fixture.detectChanges();

      let groupCheckbox: Element = fixture.nativeElement.querySelector(".group_check_id input[type=checkbox]");
      let summaryCheckbox: Element = fixture.nativeElement.querySelector(".summary_check_id input[type=checkbox]");
      expect(groupCheckbox.attributes["ng-reflect-is-disabled"].value).toEqual("true");
      expect(summaryCheckbox.attributes["ng-reflect-is-disabled"].value).toEqual("true");
   });

   //Bug #20843, Bug #20845
   it("should not reset expand cell when change cell binging", () => {
      let cellBinding = createMockCellBindingInfo("state");
      cellBinding.expansion = 1;
      editorService.getCellBinding.mockImplementation(() => cellBinding);
      editorService.getSelectCells.mockImplementation(() => [TestUtils.createMockCalcTableCell("state")]);
      editorService.getCellNames.mockImplementation(() => ["state"]);
      configureTestEnv();
      fixture = TestBed.createComponent(CalcDataPane);
      calcDataPane = <CalcDataPane>fixture.componentInstance;
      let bindingModel = TestUtils.createMockCalcTableBindingModel();
      bindingModel.availableFields = [TestUtils.createMockDataRef("state"), TestUtils.createMockDataRef("city")];
      calcDataPane.bindingModel = bindingModel;
      fixture.detectChanges();

      let HRadio: HTMLInputElement = fixture.nativeElement.querySelectorAll(".form-row-checkbox input[type=radio]")[0];
      expect(HRadio.checked).toBeTruthy();

      calcDataPane.columnValue = "city";
      fixture.detectChanges();
      HRadio = fixture.nativeElement.querySelectorAll(".form-row-checkbox input[type=radio]")[0];
      expect(HRadio.checked).toBeTruthy();

      //Bug #20845
      let textCell = createMockCellBindingInfo();
      textCell.type = 1;
      textCell.btype = 2;
      textCell.expansion = 0;
      textCell.value = "Cell";
      editorService.getCellBinding.mockImplementation(() => textCell);
      let calcTextCell = TestUtils.createMockCalcTableCell("Cell");
      calcTextCell.bindingType = 2;
      editorService.getSelectCells.mockImplementation(() => [calcTextCell]);
      editorService.getCellNames.mockImplementation(() => ["state"]);
      fixture.detectChanges();

      calcDataPane.columnValue = "state";
      fixture.detectChanges();

      let colGroup: HTMLElement = fixture.nativeElement.querySelector(".col_group_id select");
      expect(colGroup.attributes["ng-reflect-model"].value).toEqual("(default)");
   });
});
