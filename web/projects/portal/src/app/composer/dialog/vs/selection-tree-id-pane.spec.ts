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
import { ChangeDetectorRef, Component, NO_ERRORS_SCHEMA, ViewChild } from "@angular/core";
import { async, ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { By } from "@angular/platform-browser";
import { NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { DropDownTestModule } from "../../../common/test/test-module";
import { MessageDialog } from "../../../widget/dialog/message-dialog/message-dialog.component";
import { NewAggrDialog } from "../../../widget/dialog/new-aggr-dialog/new-aggr-dialog.component";
import { ScriptPane } from "../../../widget/dialog/script-pane/script-pane.component";
import { FixedDropdownDirective } from "../../../widget/fixed-dropdown/fixed-dropdown.directive";
import { ComboMode } from "../../../widget/dynamic-combo-box/dynamic-combo-box-model";
import { FormulaEditorDialog } from "../../../widget/formula-editor/formula-editor-dialog.component";
import { DragService } from "../../../widget/services/drag.service";
import { TreeNodeComponent } from "../../../widget/tree/tree-node.component";
import { TreeSearchPipe } from "../../../widget/tree/tree-search.pipe";
import { TreeComponent } from "../../../widget/tree/tree.component";
import { SelectionTreePaneModel } from "../../data/vs/selection-tree-pane-model";
import { SelectionTreeIdPane } from "./selection-tree-id-pane.component";

@Component({
   selector: "test-app",
   template: `<selection-tree-id-pane [model]="model" [targetIdTree]="targetIdTree"
              [localRefs]="localRefs" [variableValues]="variableValues">
                 </selection-tree-id-pane>`
})
class TestApp {
   @ViewChild(SelectionTreeIdPane, {static: true}) selectionTreeIdPane: SelectionTreeIdPane;
   // SelectionTreePaneModel
   model = createModel();
   // Tree Node Model
   targetIdTree = {
      children: [{
         // Logic Model Folder
         label: "Logic Model",
         type: "folder",
         data: null,
         children: [{
            // Logic Model Table
            label: "Logic Model Table",
            type: "table",
            data: {
               properties: {
                  assembly: "Logic Model"
               }
            }
         }]
      },
         {
            // Worksheet Table
            label: "Table",
            type: "table",
            data: {
               properties: {
                  source: "Worksheet"
               }
            }         }]
   };
   localRefs = [];
   variableValues = [""];
}

let createModel: () => SelectionTreePaneModel = () => {
   return {
      selectionMeasurePaneModel: null,
      selectedTable: "",
      additionalTables: [],
      selectedColumns: [],
      targetTree: null,
      mode: 2,
      selectChildren: false,
      parentId: "",
      id: "",
      label: "",
      parentIdRef: null,
      idRef: null,
      labelRef: null
   };
};

describe("Selection Tree Id Pane Test", () => {
   let changeDetectorRef: any;
   let dragService: any;

   let fixture: ComponentFixture<TestApp>;
   let idPane: SelectionTreeIdPane;

   beforeEach(async(() => {
      changeDetectorRef = { detectChanges: jest.fn() };
      dragService = { reset: jest.fn(), put: jest.fn() };

      TestBed.configureTestingModule({
         imports: [
            NgbModule, ReactiveFormsModule, FormsModule, DropDownTestModule
         ],
         declarations: [
            TestApp, SelectionTreeIdPane, TreeComponent, TreeNodeComponent,
            TreeSearchPipe, FormulaEditorDialog, ScriptPane,
            NewAggrDialog, MessageDialog, FixedDropdownDirective
         ],
         providers: [
            {provide: ChangeDetectorRef, useValue: changeDetectorRef},
            {provide: DragService, useValue: dragService}
         ],
         schemas: [ NO_ERRORS_SCHEMA ]
      }).compileComponents();

      fixture = TestBed.createComponent(TestApp);
   }));

   it("nodes with different data should be handled appropriately", () => {
      idPane = <SelectionTreeIdPane>fixture.componentInstance.selectionTreeIdPane;
      fixture.detectChanges();
      // Logic Model Folder
      idPane.selectIdTable(idPane.targetIdTree.children[0]);
      expect(idPane.model.selectedTable).toBe("Logic Model");

      // Table in Logic Model
      idPane.selectIdTable(idPane.targetIdTree.children[0].children[0]);
      expect(idPane.model.selectedTable).toBe("Logic Model");

      // Table in Worksheet
      idPane.selectIdTable(idPane.targetIdTree.children[1]);
      expect(idPane.model.selectedTable).toBe("Table");
   });

   //Bug #19081 should keep the selected variable
   //Bug #19702 and Bug #19932
   xit("should keep selected variable on parent/child id", () => {
      fixture.componentInstance.model.parentId = "${var1}";
      fixture.componentInstance.model.id = "${var2}";
      fixture.componentInstance.model.label = "${var3}";
      fixture.detectChanges();
      idPane = <SelectionTreeIdPane>fixture.componentInstance.selectionTreeIdPane;
      idPane.variableValues = ["${var1}", "${var2}", "${var3}"];
      fixture.detectChanges();

      let element = fixture.debugElement.query(By.css(".local-parent-id dynamic-combo-box select")).nativeElement;
      expect(element).toBeTruthy();
      expect(element.getAttribute("title")).toBe("${var1}");

      element = fixture.debugElement.query(By.css(".local-id dynamic-combo-box select")).nativeElement;
      expect(element).toBeTruthy();
      expect(element.getAttribute("title")).toBe("${var2}");

      element = fixture.debugElement.query(By.css(".local-label dynamic-combo-box select")).nativeElement;
      expect(element).toBeTruthy();
      expect(element.getAttribute("title")).toBe("${var3}");
   });

   //Bug #19715, //Bug #20014
   xit("should keep value type status", () => {
      fixture.componentInstance.model.parentId = "${var1}";
      fixture.componentInstance.model.id = "${var2}";
      fixture.componentInstance.model.label = "${var3}";
      fixture.detectChanges();
      idPane = <SelectionTreeIdPane>fixture.componentInstance.selectionTreeIdPane;
      idPane.variableValues = ["${var1}", "${var2}", "${var3}"];
      fixture.detectChanges();

      idPane.selectParentIdType(ComboMode.VALUE);
      fixture.detectChanges();
      expect(idPane.localParentId).toBe("(none)");
      expect(fixture.componentInstance.model.parentId).toBeNull();
      let parentID = fixture.debugElement.query(By.css(".local-parent-id .dynamic-combo-box-body .display-style")).nativeElement;
      expect((parentID.textContent).trim()).toBe("(none)");
   });
});
