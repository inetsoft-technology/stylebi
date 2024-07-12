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
import { async, ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { Observable, of as observableOf } from "rxjs";
import { DateValueEditorComponent } from "../../../widget/date-type-editor/date-value-editor.component";
import { TimeInstantValueEditorComponent } from "../../../widget/date-type-editor/time-instant-value-editor.component";
import { TimeValueEditorComponent } from "../../../widget/date-type-editor/time-value-editor.component";
import { TimepickerComponent } from "../../../widget/date-type-editor/timepicker.component";
import { MessageDialog } from "../../../widget/dialog/message-dialog/message-dialog.component";
import { VariableListDialog } from "../../../widget/dialog/variable-list-dialog/variable-list-dialog.component";
import { VariableListEditor } from "../../../widget/dialog/variable-list-dialog/variable-list-editor/variable-list-editor.component";
import { VariableValueEditor } from "../../../widget/dialog/variable-list-dialog/variable-value-editor/variable-value-editor.component";
import { EnterSubmitDirective } from "../../../widget/directive/enter-submit.directive";
import { LargeFormFieldComponent } from "../../../widget/large-form-field/large-form-field.component";
import { ModelService } from "../../../widget/services/model.service";
import { Worksheet } from "../../data/ws/worksheet";
import { VariableAssemblyDialog } from "./variable-assembly-dialog.component";
import { VariableTableListDialog } from "./variable-table-list-dialog.component";
import { ConditionValueTypePipe } from "../../../widget/condition/condition-value-type.pipe";

const createMockModel: () => Observable<any> = () => {
   return observableOf({
      oldName: "MockVariableName",
      label: "MockLabel",
      type: "String",
      defaultValue: "",
      selectionList: "None",
      displayStyle: "Combo_Box",
      none: true,
      variableListDialogModel: {
         labels: [],
         values: [],
         variableListEditorModel: {}
      },
      variableTableListDialogModel: {}
   });
};

function isDisabled(element: any) {
   let result = false;

   if(!!element) {
      const disabled = element.getAttribute("disabled");
      result = (!!disabled || disabled === "");
   }

   return result;
}

describe("VariableAssemblyDialog Integration Test", () => {
   let modelService: any;
   let fixture: ComponentFixture<VariableAssemblyDialog>;

   beforeEach(async(() => {
      modelService = {
         getModel: jest.fn(() => createMockModel()),
         sendModel: jest.fn()
      };

      TestBed.configureTestingModule({
         imports: [
            FormsModule, ReactiveFormsModule, NgbModule
         ],
         declarations: [
            VariableAssemblyDialog, VariableValueEditor, VariableListDialog,
            VariableTableListDialog, VariableListEditor, MessageDialog, DateValueEditorComponent,
            TimeValueEditorComponent, TimeInstantValueEditorComponent, EnterSubmitDirective,
            LargeFormFieldComponent, TimepickerComponent, ConditionValueTypePipe
         ],
         providers: [
            {
               provide: ModelService,
               useValue: modelService
            }
         ],
         schemas: [ NO_ERRORS_SCHEMA ]
      });
      TestBed.compileComponents();
   }));

   it("should create the component", () => {
      fixture = TestBed.createComponent(VariableAssemblyDialog);
      fixture.componentInstance.worksheet = {runtimeId: "", variables: [], assemblyNames: () => []} as Worksheet;
      fixture.detectChanges();
      expect(fixture.componentInstance).toBeTruthy();

      // Check that the name is MockVariableName
      let element = fixture.nativeElement;
      let name = element.querySelector("#name").value;
      expect(name).toBe(fixture.componentInstance.model.oldName);

      // Click on the none radio button to ensure that the display style options
      // are disabled
      element.querySelector("input[type='radio'][formControlName='selectionList'][value='none']").click();
      fixture.detectChanges();

      // Assert that the display style options are disabled
      let comboRadioButton = element.querySelector("input[type='radio'][formControlName='displayStyle'][ng-reflect-value='1']");
      expect(comboRadioButton.disabled).toBeTruthy();

      // Check the embedded option and assert that the display style options
      // are now enabled
      element.querySelector("input[type='radio'][formControlName='selectionList'][value='embedded']").click();
      fixture.detectChanges();
      expect(comboRadioButton.disabled).toBeFalsy();
   });

   it("should clear default value when none is selected", () => {
      fixture = TestBed.createComponent(VariableAssemblyDialog);
      fixture.componentInstance.worksheet = {runtimeId: "", variables: [], assemblyNames: () => []} as Worksheet;
      fixture.detectChanges();

      let noneControl = fixture.componentInstance.form.get("none");
      let defaultValueControl = fixture.componentInstance.form.get("defaultValue");

      noneControl.patchValue(false);
      defaultValueControl.patchValue("default value");
      fixture.detectChanges();

      let element = fixture.nativeElement;
      element.querySelector("[formControlName=none]").click();
      fixture.detectChanges();

      expect(noneControl.value).toBeTruthy();
      expect(defaultValueControl.value).toBeFalsy();
   });

   //Bug #20319 should allow some characters for variable
   it("should allow some characters for variable", () => {
      fixture = TestBed.createComponent(VariableAssemblyDialog);
      fixture.componentInstance.worksheet = {runtimeId: "", variables: [], assemblyNames: () => []} as Worksheet;
      fixture.detectChanges();

      let name = fixture.nativeElement.querySelector("input#name");
      name.value = "&$@+";
      name.dispatchEvent(new Event("input"));
      fixture.detectChanges();

      let warnings = fixture.nativeElement.querySelector("span.invalid-feedback");
      expect(warnings).toBeNull();

      name.value = "a%";
      name.dispatchEvent(new Event("input"));
      fixture.detectChanges();

      warnings = fixture.nativeElement.querySelector("span.invalid-feedback");
      expect(warnings).not.toBeNull();
   });
});