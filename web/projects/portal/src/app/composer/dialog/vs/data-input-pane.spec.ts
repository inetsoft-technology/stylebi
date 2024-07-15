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
import { HttpClient } from "@angular/common/http";
import {
   HttpClientTestingModule,
   HttpTestingController
} from "@angular/common/http/testing";
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { By } from "@angular/platform-browser";
import { NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { DropDownTestModule } from "../../../common/test/test-module";
import { TestUtils } from "../../../common/test/test-utils";
import { DataTreeValidatorService } from "../../../vsobjects/dialog/data-tree-validator.service";
import { FixedDropdownDirective } from "../../../widget/fixed-dropdown/fixed-dropdown.directive";
import { DynamicComboBox } from "../../../widget/dynamic-combo-box/dynamic-combo-box.component";
import { TooltipDirective } from "../../../widget/tooltip/tooltip.directive";
import { TreeDropdownComponent } from "../../../widget/tree/tree-dropdown.component";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";
import { TreeNodeComponent } from "../../../widget/tree/tree-node.component";
import { TreeSearchPipe } from "../../../widget/tree/tree-search.pipe";
import { TreeComponent } from "../../../widget/tree/tree.component";
import { DataInputPaneModel } from "../../data/vs/data-input-pane-model";
import { DataInputPane } from "./data-input-pane.component";
import { EnterClickDirective } from "../../../widget/directive/enter-click.directive";

let createTargetTree: () => TreeNodeModel = () => {
   return {
         children: [{
            children: [],
            data: "",
            label: "None",
            type: "none",
            leaf: true
         }, {
            children: [{
               children: [],
               data: "Query1",
               label: "Query1",
               leaf: true,
               type: "table"
            }],
            data: "Tables",
            type: "folder",
            label: "Tables"
         }, {
            children: [],
            data: "Variables",
            label: "Variables",
            type: "folder",
            leaf: false
         }],
         leaf: false,
   };
};

describe("Data Input Pane Test", () => {
   const createModel: () => DataInputPaneModel = () => {
      return {
         table: "table",
         tableLabel: "table",
         rowValue: "",
         columnValue: "column",
         defaultValue: "",
         targetTree: null,
         variable: false,
         writeBackDirectly: false
      };
   };

   const createTreeNodeModel: () => TreeNodeModel = () => {
      return {
         data: "TableName",
         type: "table"
      };
   };

   let fixture: ComponentFixture<DataInputPane>;
   let dataInputPane: DataInputPane;
   let httpClient: HttpClient;
   let httpTestingController: HttpTestingController;
   let treeService: any;

   beforeEach(() => {
      treeService = { validateTreeNode: jest.fn() };
      TestBed.configureTestingModule({
         imports: [DropDownTestModule, ReactiveFormsModule, FormsModule, NgbModule, HttpClientTestingModule],
         declarations: [
            DataInputPane, DynamicComboBox, TreeComponent, TreeNodeComponent,
            TreeDropdownComponent, FixedDropdownDirective, TreeSearchPipe,
            TooltipDirective, EnterClickDirective
         ],
         providers: [
            {provide: DataTreeValidatorService, useValue: treeService}
         ],
         schemas: [NO_ERRORS_SCHEMA]
      }).compileComponents();

      fixture = TestBed.createComponent(DataInputPane);

      dataInputPane = fixture.componentInstance;
      dataInputPane.model = createModel();
      dataInputPane.runtimeId = "Viewsheet1";
      dataInputPane.variableValues = [];

      httpClient = TestBed.inject(HttpClient);
      httpTestingController = TestBed.inject(HttpTestingController);
   });

   // Bug #9867 make sure that columns and rows get properly fetched.
   it("should get columns when switching table", () => {
      dataInputPane.selectTarget(createTreeNodeModel());

      let request = httpTestingController.expectOne("../vs/dataInput/columns/Viewsheet1/TableName");
      request.flush({columnlist: []});

      request = httpTestingController.expectOne("../vs/dataInput/rows/Viewsheet1/TableName/column");
      request.flush({});

      httpTestingController.verify();
   });

   it("should get rows when switching columns", () => {
      const columnName: string = "column1";
      dataInputPane.selectColumn(columnName);

      const request = httpTestingController.expectOne("../vs/dataInput/rows/Viewsheet1/table/" + columnName);
      request.flush({});
      httpTestingController.verify();
   });

   //Bug #18165 should disable column and row when select variable
   it("should disable column and row comobox on variable", () => {
      let node: TreeNodeModel = createTreeNodeModel();
      node.data = "var1";
      node.type = "variable";

      dataInputPane.selectTarget(node);
      expect(dataInputPane.disableRowCol()).toBe(true);
   });

   //Bug #19833
   it("should input page number on popup table page", (done) => {
      dataInputPane = <DataInputPane>fixture.componentInstance;
      dataInputPane.model = createModel();
      dataInputPane.model.rowValue = "1";
      dataInputPane.popupTable = {
         columnHeaders: ["col1", "col2", "col3"],
         numPages: 2,
         numRows: 11,
         page: null,
         pageData: [
            ["a", "b", "c"], ["a1", "b1", "c1"], ["a2", "b2", "c2"],
            ["a3", "b3", "c3"], ["a3", "b3", "c3"], ["a3", "b3", "c3"],
            ["a3", "b3", "c3"], ["a3", "b3", "c3"], ["a3", "b3", "c3"],
            ["al", "bl", "cl"], ["a4", "b4", "c4"]],
         rowData: [
            ["a", "b", "c"], ["a1", "b1", "c1"], ["a2", "b2", "c2"],
            ["a3", "b3", "c3"], ["a3", "b3", "c3"], ["a3", "b3", "c3"],
            ["a3", "b3", "c3"], ["a3", "b3", "c3"], ["a3", "b3", "c3"],
            ["al", "bl", "cl"], ["a4", "b4", "c4"]],
         tableName: "table"
      };
      fixture.detectChanges();
      fixture.debugElement.query(By.css("span.edit-icon")).nativeElement.click();
      fixture.detectChanges();

      fixture.whenStable().then(() => {
         let fixedDropdown = document.getElementsByTagName("fixed-dropdown")[0];
         let pageInput = fixedDropdown.getElementsByTagName("input")[0];
         let beforeBtns = fixedDropdown.getElementsByTagName("button");
         expect(beforeBtns[0].disabled).toBeTruthy();
         expect(beforeBtns[1].disabled).toBeTruthy();

         pageInput.value = "2";
         fixture.componentInstance.updatePopupTablePage(new KeyboardEvent("keydown", {key: "enter"}), 2);
         fixture.detectChanges();
         expect(pageInput.getAttribute("ng-reflect-model")).toBe("2");
         expect(beforeBtns[0].disabled).toBeFalsy();
         expect(beforeBtns[1].disabled).toBeFalsy();
         let id = fixedDropdown.getElementsByTagName("td")[4].textContent;
         expect(id.trim()).toBe("11");
         done();
      });
   });

   it("row should update when change to expression", () => {
      let model = createModel();
      model.columnValue = "city";
      model.rowValue = "1";
      model.table = "Query1";
      model.tableLabel = "Query1";
      model.targetTree = createTargetTree();
      fixture.componentInstance.model = model;
      fixture.componentInstance.selectedRow = "1: city";
      fixture.detectChanges();

      let typeToggles = fixture.nativeElement.querySelectorAll("button.type-toggle");
      typeToggles[1].click();
      TestUtils.changeDynamicComboValueType(2);
      fixture.detectChanges();
      let rowInput = fixture.debugElement.query(By.css(".row_id .dynamic-combo-box-body input")).nativeElement;
      expect(rowInput.getAttribute("ng-reflect-model")).toEqual("=");
   });

   //Bug #20083
   // bad test, don't test external components and use debug elements to test dom when necessary
   // it("row and column should return default value when change variale to value mode", () => {
   //    let model = createModel();
   //    model.columnValue = "${var1}";
   //    model.rowValue = "1";
   //    model.table = "Query1";
   //    model.tableLabel = "Query1";
   //    model.targetTree = createTargetTree();
   //    fixture.componentInstance.model = model;
   //    fixture.componentInstance.selectedRow = "1:col1";
   //    fixture.componentInstance.variableValues = ["${var1}", "${var2}"];
   //    fixture.componentInstance.columns = [
   //       {value: "col1", label: "col1", tooltip: ""},
   //       {value: "col2", label: "col2", tooltip: ""},
   //       {value: "col2", label: "col2", tooltip: ""}];
   //    fixture.detectChanges();
   //
   //    let typeToggles = fixture.nativeElement.querySelectorAll("button.type-toggle");
   //    typeToggles[0].click();
   //    TestUtils.changeDynamicComboValueType(0);
   //    fixture.detectChanges();
   //
   //    let columnInput = TestUtils.getDynamicComboDiv(fixture.debugElement.query(By.css(".column_id")).nativeElement);
   //    let rowInput = TestUtils.getDynamicComboDiv(fixture.debugElement.query(By.css(".row_id")).nativeElement);
   //    expect((columnInput.textContent).trim()).toBe("col1");
   //    expect((rowInput.textContent).trim()).toBe("1:col1");
   // });
});