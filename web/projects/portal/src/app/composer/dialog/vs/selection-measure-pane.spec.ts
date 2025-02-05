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
import { HttpClientTestingModule, HttpTestingController } from "@angular/common/http/testing";
import { ChangeDetectorRef, NO_ERRORS_SCHEMA } from "@angular/core";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { AggregateFormula } from "../../../binding/util/aggregate-formula";
import { DropDownTestModule } from "../../../common/test/test-module";
import { NewAggrDialog } from "../../../widget/dialog/new-aggr-dialog/new-aggr-dialog.component";
import { ScriptPane } from "../../../widget/dialog/script-pane/script-pane.component";
import { DynamicComboBox } from "../../../widget/dynamic-combo-box/dynamic-combo-box.component";
import { FixedDropdownDirective } from "../../../widget/fixed-dropdown/fixed-dropdown.directive";
import { FormulaEditorDialog } from "../../../widget/formula-editor/formula-editor-dialog.component";
import { DragService } from "../../../widget/services/drag.service";
import { TreeNodeComponent } from "../../../widget/tree/tree-node.component";
import { TreeSearchPipe } from "../../../widget/tree/tree-search.pipe";
import { TreeComponent } from "../../../widget/tree/tree.component";
import { SelectionMeasurePaneModel } from "../../data/vs/selection-measure-pane-model";
import { SelectionMeasurePane } from "./selection-measure-pane.component";

describe("Selection Measure Pane Test", () => {
   const createModel: () => SelectionMeasurePaneModel = () => {
      return {
         measure: "mockMeasure",
         formula: AggregateFormula.COUNT_ALL.formulaName,
         showText: true,
         showBar: true
      };
   };

   let fixture: ComponentFixture<SelectionMeasurePane>;
   let measurePane: SelectionMeasurePane;
   let dragService: any;
   let changeDetectorRef: any;
   let httpClient: HttpClient;
   let httpTestingController: HttpTestingController;

   beforeEach(() => {
      dragService = {};
      changeDetectorRef = {};

      TestBed.configureTestingModule({
         imports: [
            NgbModule, ReactiveFormsModule, FormsModule, DropDownTestModule,
            HttpClientTestingModule
         ],
         declarations: [
            SelectionMeasurePane, FormulaEditorDialog,
            ScriptPane, NewAggrDialog, TreeComponent, TreeNodeComponent, TreeSearchPipe,
            FixedDropdownDirective, DynamicComboBox
         ],
         providers: [
            {provide: ChangeDetectorRef, useValue: changeDetectorRef},
            {provide: DragService, useValue: dragService}
         ],
         schemas: [ NO_ERRORS_SCHEMA ]
      }).compileComponents();

      fixture = TestBed.createComponent(SelectionMeasurePane);
      fixture.componentInstance.model = createModel();
      fixture.detectChanges();

      measurePane = fixture.componentInstance;
      measurePane.model = createModel();
      measurePane.runtimeId = "Viewsheet1";
      measurePane.variableValues = [];
      measurePane.tableNames = ["__inetsoft_cube_TableName"];

      httpClient = TestBed.inject(HttpClient);
      httpTestingController = TestBed.inject(HttpTestingController);
   });

   // Bug #9800 Ensure that the call to get columns is correct and has the runtimeId
   it("should get columns, and properly disable aggregates dynamic combo box", () => {
      measurePane.getMeasures();

      const requests = httpTestingController.match((request) => {
         return request.url === "../vs/dataOutput/selection/columns" && request.params.has("runtimeId");
      });
      expect(requests.length).toBe(1);
      requests.forEach(request => request.flush({}));
      expect(measurePane.isAggregateDisabled()).toBeTruthy();
   });

   it("localMeasure should be defined after onInit", () => {
      const model = createModel();
      model.measure = "$(myVariable)";
      fixture = TestBed.createComponent(SelectionMeasurePane);
      fixture.componentInstance.model = model;
      fixture.componentInstance.variableValues = [];
      fixture.detectChanges();

      expect(fixture.componentInstance.localMeasure).toBeDefined();
      expect(fixture.componentInstance.localFormula).toBeDefined();
   });

   //Bug #19954 should keep expression seletec
   it("should keep measure when select expression", () => {
      let model = createModel();
      model.measure = "city";
      fixture = TestBed.createComponent(SelectionMeasurePane);
      fixture.componentInstance.model = model;

      fixture.componentInstance.localMeasure = "city";
      fixture.detectChanges();

      let typeToggles = fixture.nativeElement.querySelectorAll("button.type-toggle");
      typeToggles[0].click();
      let fixs = document.getElementsByTagName("fixed-dropdown");
      let temp = fixs[0].querySelectorAll("a");
      temp[2].click();
      fixture.detectChanges();

      let measureInput = fixture.nativeElement.querySelector("#measure input");
      expect(measureInput.getAttribute("ng-reflect-model")).toBe("=");
   });
});
