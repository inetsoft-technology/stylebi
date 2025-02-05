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
import { DebugElement, NO_ERRORS_SCHEMA } from "@angular/core";
import { async, ComponentFixture, TestBed } from "@angular/core/testing";
import { FormGroup, FormsModule, ReactiveFormsModule } from "@angular/forms";
import { By } from "@angular/platform-browser";
import { NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { DropDownTestModule } from "../../../common/test/test-module";
import { TestUtils } from "../../../common/test/test-utils";
import { MessageDialog } from "../../../widget/dialog/message-dialog/message-dialog.component";
import { NewAggrDialog } from "../../../widget/dialog/new-aggr-dialog/new-aggr-dialog.component";
import { ScriptPane } from "../../../widget/dialog/script-pane/script-pane.component";
import { DynamicComboBox } from "../../../widget/dynamic-combo-box/dynamic-combo-box.component";
import { FixedDropdownDirective } from "../../../widget/fixed-dropdown/fixed-dropdown.directive";
import { FormulaEditorDialog } from "../../../widget/formula-editor/formula-editor-dialog.component";
import { TreeNodeComponent } from "../../../widget/tree/tree-node.component";
import { TreeSearchPipe } from "../../../widget/tree/tree-search.pipe";
import { TreeComponent } from "../../../widget/tree/tree.component";
import { NumberRangePaneModel } from "../../data/vs/number-range-pane-model";
import { NumberRangePane } from "./number-range-pane.component";

// Default values taken from NumberRangePane when property dialog is opened on a new gauge with no data
const createModel: () => NumberRangePaneModel = () => {
   return {
      min: "0",
      max: "100",
      minorIncrement: "6.25",
      majorIncrement: "25.0"
   };
};

describe("Number Range Pane Tests", () => {

   let fixture: ComponentFixture<NumberRangePane>;
   let numberRangeInputs: DebugElement[];
   let el: HTMLInputElement;

   beforeEach(async(() => {
      TestBed.configureTestingModule({
         imports: [
            NgbModule, ReactiveFormsModule, FormsModule, DropDownTestModule
         ],
         declarations: [NumberRangePane, FormulaEditorDialog,
            NewAggrDialog, MessageDialog, ScriptPane, TreeComponent,
            TreeNodeComponent, TreeSearchPipe, FixedDropdownDirective, DynamicComboBox
         ],
         schemas: [ NO_ERRORS_SCHEMA ]
      });

      fixture = TestBed.createComponent(NumberRangePane);
      fixture.componentInstance.model = createModel();
      fixture.componentInstance.form = new FormGroup({});
      fixture.detectChanges();
   }));

   it("should have an error when min input value is set larger than max input value", () => {
      numberRangeInputs = fixture.debugElement.queryAll(By.css(".dynamic-combo-box-body input"));
      el = numberRangeInputs[0].nativeElement;
      el.value = "1000";
      el.dispatchEvent(new Event("change"));
      fixture.detectChanges();

      fixture.whenStable().then(() => {
         const element = fixture.debugElement.query(By.css("div.alert-danger"));
         expect(element).toBeTruthy();
         expect(TestUtils.toString(element.nativeElement.textContent)).toBe("viewer.viewsheet.numberRange.maxMinWarning");
      });
   });
});
