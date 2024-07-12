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
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { DropDownTestModule } from "../../common/test/test-module";
import { NewAggrDialog } from "../dialog/new-aggr-dialog/new-aggr-dialog.component";
import { ScriptPane } from "../dialog/script-pane/script-pane.component";
import { FixedDropdownDirective } from "../fixed-dropdown/fixed-dropdown.directive";
import { ComboMode, DynamicComboBoxModel, ValueMode } from "./dynamic-combo-box-model";
import { DynamicComboBox } from "./dynamic-combo-box.component";
import { FormulaEditorDialog } from "../formula-editor/formula-editor-dialog.component";
import { TreeNodeComponent } from "../tree/tree-node.component";
import { TreeSearchPipe } from "../tree/tree-search.pipe";
import { TreeComponent } from "../tree/tree.component";

let createModel: () => DynamicComboBoxModel = () => {
   return {
      type: ComboMode.VALUE,
      mode: ValueMode.TEXT,
      value: "",
      values: [],
      variables: [],
      editable: false
   };
};

describe("Dynamic combo box Unit Case: ", () => {
   let fixture: ComponentFixture<DynamicComboBox>;
   let dynamicComboBox: DynamicComboBox;
   let dynamicComboBoxModel: DynamicComboBoxModel;

   beforeEach(() => {
      TestBed.configureTestingModule({
         imports: [
            ReactiveFormsModule, FormsModule, NgbModule, DropDownTestModule
         ],
         declarations: [
            DynamicComboBox, TreeComponent, FormulaEditorDialog, NewAggrDialog,
            TreeNodeComponent, ScriptPane, TreeSearchPipe, FixedDropdownDirective
         ],
         schemas: [ NO_ERRORS_SCHEMA ]
      });
      TestBed.compileComponents();

      fixture = TestBed.createComponent(DynamicComboBox);
      dynamicComboBox = <DynamicComboBox>fixture.componentInstance;
      dynamicComboBoxModel = createModel();
   });

   //Bug #17341  and Bug #17765 disbale variable when no variable list and can not select disabled variable irem
   it("The variable item should be disable when no variable in vs", () => {
      dynamicComboBox.variables = [];
      fixture.detectChanges();
      const toggle = fixture.nativeElement.querySelector(".type-toggle");
      toggle.dispatchEvent(new Event("click"));
      fixture.detectChanges();

      //Bug #17341
      expect(dynamicComboBox.isVariableEnabled()).toBe(false);
      //Bug #17765
      expect(dynamicComboBox.selectType(new MouseEvent("click"), ComboMode.VARIABLE)).toBeUndefined();
   });

   //Bug #19027 should keep variable type
   it("should keep variable selected status when variable is selected", () => {
      dynamicComboBox.variables = ["$(var1)", "$(var2)", "$(var3)"];
      dynamicComboBox.value = "$(var1)";
      dynamicComboBox.ngOnInit();
      expect(dynamicComboBox.type).toBe(1);
   });
});
