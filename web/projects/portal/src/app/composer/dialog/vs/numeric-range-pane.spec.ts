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
import { NgModule, NO_ERRORS_SCHEMA } from "@angular/core";
import { async, ComponentFixture, TestBed } from "@angular/core/testing";
import { FormGroup, FormsModule, ReactiveFormsModule } from "@angular/forms";
import { NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { TestUtils } from "../../../common/test/test-utils";
import { MessageDialog } from "../../../widget/dialog/message-dialog/message-dialog.component";
import { NewAggrDialog } from "../../../widget/dialog/new-aggr-dialog/new-aggr-dialog.component";
import { ScriptPane } from "../../../widget/dialog/script-pane/script-pane.component";
import { FixedDropdownDirective } from "../../../widget/fixed-dropdown/fixed-dropdown.directive";
import { ComboMode } from "../../../widget/dynamic-combo-box/dynamic-combo-box-model";
import { DropdownStackService } from "../../../widget/fixed-dropdown/dropdown-stack.service";
import { FixedDropdownContextmenuComponent } from "../../../widget/fixed-dropdown/fixed-dropdown-contextmenu.component";
import { FixedDropdownComponent } from "../../../widget/fixed-dropdown/fixed-dropdown.component";
import { FixedDropdownService } from "../../../widget/fixed-dropdown/fixed-dropdown.service";
import { FormulaEditorDialog } from "../../../widget/formula-editor/formula-editor-dialog.component";
import { TreeNodeComponent } from "../../../widget/tree/tree-node.component";
import { TreeSearchPipe } from "../../../widget/tree/tree-search.pipe";
import { TreeComponent } from "../../../widget/tree/tree.component";
import { NumericRangePaneModel } from "../../data/vs/numeric-range-pane-model";
import { NumericRangePane } from "./numeric-range-pane.component";


// Default values taken from NumberRangePane when property dialog is opened on a new gauge with no data
const createModel: () => NumericRangePaneModel = () => {
   return {
      minimum: "0",
      maximum: "100",
      increment: "20"
   };
};

@NgModule({
   declarations: [
      FixedDropdownComponent,
      FixedDropdownContextmenuComponent
   ],
   entryComponents: [
      FixedDropdownComponent,
      FixedDropdownContextmenuComponent
   ]
})
class TestModule {
}

describe("Numeric Range Pane Tests", () => {

   let fixture: ComponentFixture<NumericRangePane>;
   let element: any;

   beforeEach(async(() => {
      TestBed.configureTestingModule({
         imports: [
            NgbModule, ReactiveFormsModule, FormsModule, TestModule
         ],
         declarations: [
            NumericRangePane, FormulaEditorDialog,
            NewAggrDialog, MessageDialog, ScriptPane, TreeComponent,
            TreeNodeComponent, TreeSearchPipe, FixedDropdownDirective
         ],
         providers: [FixedDropdownService, DropdownStackService],
         schemas: [ NO_ERRORS_SCHEMA ]
      });

      fixture = TestBed.createComponent(NumericRangePane);
      fixture.componentInstance.model = createModel();
      fixture.componentInstance.form = new FormGroup({});
      element = fixture.nativeElement;
      fixture.detectChanges();
   }));

   //Bug #18094 Input '0' in the slider 'Increment' pop up error dialog exist issue.
   xit("should Test input message", () => {
      let numericRangeInputs: any = element.querySelectorAll(".dynamic-combo-box-body input");
      let numberRange: HTMLInputElement = numericRangeInputs[2];
      numberRange.value = "0";
      numberRange.dispatchEvent(new Event("change"));
      fixture.detectChanges();

      fixture.whenStable().then(() => {
         const errorMsg = element.querySelector("div.alert-danger");
         expect(errorMsg).toBeTruthy();
         expect(TestUtils.toString(errorMsg.textContent)).toBe("viewer.viewsheet.numberRange.notzeroWarning");
      });
   });

   //value: 0, variable: 1, expression: 2
   //the dyanimc combox index, begin with 0
   function changeValueType(valueIndex, num) {
      let typeToggles = element.querySelectorAll("button.type-toggle");
      typeToggles[num].click();

      let fixs = document.getElementsByTagName("fixed-dropdown");
      let temp = fixs[0].querySelectorAll("a");
      temp[valueIndex].click();
   }

   //Bug #19110 expression should apply rightly for number input
   //Bug #19869
   xit("expression should apply rightly for number", () => {
      changeValueType(2, 0);
      fixture.detectChanges();
      let elems = element.querySelectorAll(".dynamic-combo-box-body input");
      elems[0].value = "=10";
      elems[0].dispatchEvent(new Event("change"));
      fixture.detectChanges();

      changeValueType(2, 1);
      fixture.detectChanges();
      elems = element.querySelectorAll(".dynamic-combo-box-body input");
      elems[1].value = "=20";
      elems[1].dispatchEvent(new Event("change"));
      fixture.detectChanges();

      changeValueType(2, 2);
      fixture.detectChanges();

      fixture.componentInstance.incrementType = ComboMode.EXPRESSION;
      elems = element.querySelectorAll(".dynamic-combo-box-body input");
      elems[2].value = "=30";
      elems[2].dispatchEvent(new Event("change"));
      fixture.detectChanges();
      expect(element.querySelector(".alert-danger")).toBeNull();
   });
});
