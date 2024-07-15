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
import { Component, DebugElement, NO_ERRORS_SCHEMA, ViewChild } from "@angular/core";
import { async, ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { By } from "@angular/platform-browser";
import { NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { of as observableOf } from "rxjs";
import { DropDownTestModule } from "../../../common/test/test-module";
import { TestUtils } from "../../../common/test/test-utils";
import { DataTreeValidatorService } from "../../../vsobjects/dialog/data-tree-validator.service";
import { MessageDialog } from "../../../widget/dialog/message-dialog/message-dialog.component";
import { NewAggrDialog } from "../../../widget/dialog/new-aggr-dialog/new-aggr-dialog.component";
import { ScriptPane } from "../../../widget/dialog/script-pane/script-pane.component";
import { FixedDropdownDirective } from "../../../widget/fixed-dropdown/fixed-dropdown.directive";
import { ComboMode } from "../../../widget/dynamic-combo-box/dynamic-combo-box-model";
import { FormulaEditorDialog } from "../../../widget/formula-editor/formula-editor-dialog.component";
import { DragService } from "../../../widget/services/drag.service";
import { TreeDropdownComponent } from "../../../widget/tree/tree-dropdown.component";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";
import { TreeNodeComponent } from "../../../widget/tree/tree-node.component";
import { TreeSearchPipe } from "../../../widget/tree/tree-search.pipe";
import { TreeComponent } from "../../../widget/tree/tree.component";
import { DataOutputPaneModel } from "../../data/vs/data-output-pane-model";
import { DataOutputPane } from "./data-output-pane.component";

const createModel: () => DataOutputPaneModel = () => {
   return {
      column: "",
      aggregate: "",
      with: "",
      num: "",
      table: "",
      columnType: "",
      magnitude: 0,
      targetTree: null,
      tableType: ""
   };
};

const createTableTreeNodeModel: () => TreeNodeModel = () => {
   return {
      children: [{
            children: [],
            data: "Query1",
            label: "Query1",
            leaf: true,
            type: "table"
         }],
         leaf: false,
   };
};

function setTargetTreeValues(dataOutputPaneModel, treeNodeModel): DataOutputPaneModel {
   dataOutputPaneModel.targetTree = treeNodeModel;
   return dataOutputPaneModel;
}


function setMockTreeValues(dataOutputPaneModel): DataOutputPaneModel {
   dataOutputPaneModel.table = "mockData";
   dataOutputPaneModel.targetTree = {
      children: [{
         data: "mockCubes",
         type: "cube",
         children: [{
            data: "mockData",
            type: "cube",
            label: "mockLabel"
         } ]
      } ]
   };
   return dataOutputPaneModel;
}

@Component({
   selector: "test-app",
   template: `<data-output-pane [model]="mockModel">
                 </data-output-pane>`
})
class TestApp {
   @ViewChild(DataOutputPane, {static: true}) dataOutputPane: DataOutputPane;
   mockModel = setMockTreeValues(createModel());
}

describe("Data Output Pane Test", () => {
   let dataOutputPane: DataOutputPane;
   let testApp: TestApp;
   let httpService: any;
   let dragService: any;
   let treeService: any;

   let fixture: ComponentFixture<TestApp>;
   let dataOutputPaneModel = createModel();
   let de: DebugElement;
   let el: HTMLElement;

   beforeEach(async(() => {
      httpService = { get: jest.fn() };
      dragService = { reset: jest.fn(), put: jest.fn() };
      treeService = { validateTreeNode: jest.fn() };

      dataOutputPane = new DataOutputPane(httpService);
      dataOutputPane.model = dataOutputPaneModel;
      dataOutputPane.runtimeId = "Viewsheet1";
      dataOutputPane.variableValues = [];

      TestBed.configureTestingModule({
         imports: [
            ReactiveFormsModule, FormsModule, NgbModule, DropDownTestModule,
            HttpClientTestingModule
         ],
         declarations: [
            TestApp, DataOutputPane, TreeDropdownComponent,
            TreeComponent, TreeNodeComponent,
            FormulaEditorDialog, ScriptPane, NewAggrDialog, MessageDialog,
            TreeSearchPipe, FixedDropdownDirective
         ],
         providers: [
            {provide: DragService, useValue: dragService},
            {provide: DataTreeValidatorService, useValue: treeService}
         ],
         schemas: [ NO_ERRORS_SCHEMA ]
      }).compileComponents();

      fixture = TestBed.createComponent(TestApp);
      testApp = <TestApp>fixture.componentInstance;
   }));

   it("should get columns when switching table", () => {
      let treeTable: TreeNodeModel = {
         data: "TableData",
         type: "table"
      };
      httpService.get.mockImplementation(() => observableOf([]));

      let node: TreeNodeModel = treeTable;
      dataOutputPane.selectTarget(node);

      expect(httpService.get).toHaveBeenCalled();
      expect(httpService.get.mock.calls[0][0]).toEqual("../vs/dataOutput/table/columns");
      expect(httpService.get.mock.calls[0][1]).toBeTruthy();
   });

   //Bug #20842
   it("should load column when select diff type table", () => {
      let dataOutputModel = createModel();
      let selectedTree: TreeNodeModel = {
         children: [{
            children: [],
            data: "state",
            label: "state",
            leaf: true,
            type: "physical-table"
            }
         ]
      };
      dataOutputModel.table = "state";
      dataOutputModel.targetTree = selectedTree;
      dataOutputPane.model = dataOutputModel;
      httpService.get.mockImplementation(() => observableOf([]));

      dataOutputPane.selectTarget(selectedTree.children[0]);
      expect(httpService.get).toHaveBeenCalled();

      selectedTree.children[0].type = "logical";
      dataOutputPane.selectTarget(selectedTree.children[0]);
      expect(httpService.get).toHaveBeenCalled();

      selectedTree.children[0].type = "query";
      dataOutputPane.selectTarget(selectedTree.children[0]);
      expect(httpService.get).toHaveBeenCalled();
      expect(httpService.get.mock.calls[0][0]).toEqual("../vs/dataOutput/table/columns");
      expect(httpService.get.mock.calls[0][1]).toBeTruthy();
   });

   it("should display the node label and not the node data", async(() => {
      fixture.detectChanges();

      de = fixture.debugElement.query(By.css("tree-dropdown"));
      el = <HTMLElement>de.nativeElement;
      expect(fixture.componentInstance.dataOutputPane.currentLabel).toContain("mockLabel");
   }));

   //Bug #17272 should disable aggreget combox and value type icon
   it("should disable dynimac combox when no table selected", () => {
      let treeTable: TreeNodeModel = {
         children: [],
         data: "",
         type: "",
         label: ""
      };
      testApp.mockModel = setTargetTreeValues(createModel(), treeTable);
      fixture.detectChanges();
      let fieldset = fixture.nativeElement.querySelector(".column_aggregate_field_id");
      expect(fieldset.disabled).toBeTruthy();
   });

   //Bug #18006 should load right aggregate type when select different column
   it("should load right aggregate type when select different column", () => {
      let dataOutputModel = createModel();
      dataOutputModel.table = "state";
      dataOutputModel.targetTree = {
         children: [{
            children: [],
            data: "state",
            label: "state",
            leaf: true,
            type: "query"
            }
         ]
      };
      testApp.mockModel = dataOutputModel;
      testApp.dataOutputPane.model = dataOutputModel;
      testApp.dataOutputPane.columnValues = [{value: "id", label: "id", tooltip: "id"},
                                    {value: "state", label: "state", tooltip: "state"}];
      testApp.dataOutputPane.withColumnValues = testApp.dataOutputPane.columnValues;
      testApp.dataOutputPane.columns = [
         {name: "id", type: "integer", label: "id", tooltip: "id"},
         {name: "state", type: "string", label: "state", tooltip: "state"}
      ];

      testApp.dataOutputPane.selectColumn("id");
      expect(testApp.dataOutputPane.model.aggregate).toEqual("Sum");

      testApp.dataOutputPane.selectColumn("state");
      expect(testApp.dataOutputPane.model.aggregate).toEqual("Count");
      expect(testApp.dataOutputPane.model.with).toBeNull();

      testApp.dataOutputPane.selectAgg("Correlation");
      expect(testApp.dataOutputPane.model.with).toEqual("id");
   });

   //Bug #20032, Bug #20035, Bug #20102, Bug #20101
   xit("should return rightly status when change value type", () => {
      let dataOutputModel = createModel();
      dataOutputModel.aggregate = "Count";
      dataOutputModel.column = "${var1}";
      dataOutputModel.columnType = "string";
      dataOutputModel.table = "Query1";
      dataOutputModel.targetTree = createTableTreeNodeModel();
      testApp.mockModel = dataOutputModel;
      //testApp.dataOutputPane.model = dataOutputModel;
      testApp.dataOutputPane.variableValues = ["${var1}", "${var2}", "${var3}"];
      testApp.dataOutputPane.columnValues = [
         {value: "id", label: "id", tooltip: "id"},
         {value: "state", label: "state", tooltip: "state"}];
      testApp.dataOutputPane.withColumnValues = testApp.dataOutputPane.columnValues;
      testApp.dataOutputPane.columns = [
         {name: "id", type: "integer", label: "id", tooltip: "id"},
         {name: "state", type: "string", label: "state", tooltip: "state"}
      ];
      fixture.detectChanges();

      //Bug #20032
      testApp.dataOutputPane.selectColType(ComboMode.VALUE);
      fixture.detectChanges();
      let colInput = fixture.nativeElement.querySelector("#column");
      expect(colInput.getAttribute("ng-reflect-value")).toBe("id");
      expect(fixture.nativeElement.querySelector("#with").getAttribute("ng-reflect-disable")).toBe("true");

      //Bug #20035
      let typeToggle = fixture.nativeElement.querySelector("#aggregate button.type-toggle");
      typeToggle.click();
      TestUtils.changeDynamicComboValueType(2);
      fixture.detectChanges();
      let aggInput = fixture.nativeElement.querySelector("#aggregate input");
      expect(aggInput.getAttribute("ng-reflect-model")).toBe("=");
      expect(fixture.nativeElement.querySelector("#with").getAttribute("ng-reflect-disable")).toBe("false");

      //Bug #20102
      typeToggle.click();
      TestUtils.changeDynamicComboValueType(1);
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector("#with").getAttribute("ng-reflect-disable")).toBe("false");

      //Bug #20101
      testApp.dataOutputPane.selectAgg("${var2}");
      fixture.detectChanges();
      expect(testApp.mockModel.aggregate).toBe("${var2}");
   });
});
