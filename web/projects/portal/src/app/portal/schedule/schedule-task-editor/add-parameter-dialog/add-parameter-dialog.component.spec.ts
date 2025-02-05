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
import { HttpClientTestingModule } from "@angular/common/http/testing";
import { Component, ViewChild } from "@angular/core";
import { async, ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { By } from "@angular/platform-browser";
import { NgbModal, NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { AddParameterDialogModel } from "../../../../../../../shared/schedule/model/add-parameter-dialog-model";
import { TestUtils } from "../../../../common/test/test-utils";
import { ComponentTool } from "../../../../common/util/component-tool";
import { DateValueEditorComponent } from "../../../../widget/date-type-editor/date-value-editor.component";
import { TimeInstantValueEditorComponent } from "../../../../widget/date-type-editor/time-instant-value-editor.component";
import { TimeValueEditorComponent } from "../../../../widget/date-type-editor/time-value-editor.component";
import { TimepickerComponent } from "../../../../widget/date-type-editor/timepicker.component";
import { EnterSubmitDirective } from "../../../../widget/directive/enter-submit.directive";
import { AddParameterDialog } from "./add-parameter-dialog.component";
import { ValueTypes } from "../../../../vsobjects/model/dynamic-value-model";
import {FeatureFlagsService} from "../../../../../../../shared/feature-flags/feature-flags.service";

const createModel: () => AddParameterDialogModel = () => {
   return {
      name: "a",
      type: "string",
      value: {value: "a", type: ValueTypes.VALUE},
      array: false
   };
};

@Component({
   selector: "test-app",
   template: `<add-parameter-dialog [parameterNames]="parameterNames" [index]="index"
      [parameters]="parameters"></add-parameter-dialog>`
})
class TestApp {
   @ViewChild(AddParameterDialog, {static: true}) addParaDialog: AddParameterDialog;
   model = createModel();
   parameters: AddParameterDialogModel[];
   index: number;
   parameterNames: string[];
}

describe("Add Parameter Dialog Unit Test", () => {
   let ngbService = { open: jest.fn() };
   let featureFlagsService = { isFeatureEnabled: jest.fn() };
   let fixture: ComponentFixture<TestApp>;
   let addParaDialog: AddParameterDialog;

   beforeEach(async(() => {
      TestBed.configureTestingModule({
         imports: [
            FormsModule, ReactiveFormsModule, NgbModule, HttpClientTestingModule
         ],
         declarations: [
            TestApp, AddParameterDialog, EnterSubmitDirective, TimepickerComponent,
            DateValueEditorComponent, TimeValueEditorComponent, TimeInstantValueEditorComponent
         ],
         providers: [
            { provide: NgbModal, useValue: ngbService },
            { provide: FeatureFlagsService, useValue: featureFlagsService }
         ]
      });
      TestBed.compileComponents();

      fixture = TestBed.createComponent(TestApp);
      addParaDialog = <AddParameterDialog>fixture.componentInstance.addParaDialog;
      addParaDialog.model = createModel();
   }));

   //Bug #19684 should pop confirm dialog when create duplicate parameter
   it("should pop confirm dialog when create duplicate parameter", () => {
      fixture.componentInstance.index = -1;
      fixture.componentInstance.parameters =
         [{name: "a", value: {value: "a", type: ValueTypes.VALUE}, array: false, type: "string"}];
      fixture.detectChanges();

      let name = fixture.debugElement.query(By.css("input[formControlName=name]")).nativeElement;
      name.value = "a";
      name.dispatchEvent(new Event("input"));
      fixture.detectChanges();

      let value = fixture.debugElement.query(By.css("input[formControlName=value]")).nativeElement;
      value.value = "a";
      value.dispatchEvent(new Event("input"));
      fixture.detectChanges();

      let okBtn = fixture.debugElement.query(By.css("button.btn.btn-primary")).nativeElement;
      let showConfirmDialog = jest.spyOn(ComponentTool, "showConfirmDialog");
      showConfirmDialog.mockImplementation(() => Promise.resolve("ok"));
      okBtn.click();
      expect(showConfirmDialog).toHaveBeenCalled();
   });

   //Bug #19719 should can create parameter
   //Bug #19531 can add parameter
   //Bug #21496 should allow parameter names to start with numbers
   //Bug #21470 should can create array parameter
   it("check can create parameter", () => { // broken test
      fixture.componentInstance.index = -1;
      fixture.componentInstance.parameters =
         [{name: "a", value: {value: "a", type: ValueTypes.VALUE}, array: false, type: "string"}];
      fixture.detectChanges();

      //Bug #19898 show correct dialog title
      let dialogTitle = fixture.debugElement.query(By.css("modal-header")).nativeElement;
      expect(TestUtils.toString(dialogTitle.getAttribute("title").trim())).toBe("Add Parameter");

      //Bug #20918 should control special character for parameter name
      let name = fixture.debugElement.query(By.css("input[formControlName=name]")).nativeElement;
      name.value = "^!%";
      name.dispatchEvent(new Event("input"));
      fixture.detectChanges();

      let warnings = fixture.debugElement.query(By.css("span.invalid-feedback")).nativeElement;
      expect(warnings).not.toBeNull();

      name.value = "2test";
      name.dispatchEvent(new Event("input"));
      fixture.detectChanges();

      warnings = fixture.debugElement.query(By.css("div.alert.alert-danger"));
      expect(warnings).toBeNull();

      let arrayChk = fixture.debugElement.query(By.css("input[type=checkbox]")).nativeElement;
      let values = fixture.nativeElement.querySelectorAll("input[type=text]")[1];

      arrayChk.click();
      values.value = "a,b,c";
      values.dispatchEvent(new Event("input"));
      fixture.detectChanges();

      let okBtn = fixture.debugElement.query(By.css("button.btn.btn-primary")).nativeElement;
      okBtn.click();
      fixture.detectChanges();

      expect(fixture.componentInstance.parameters.length).toBe(2);
      expect(fixture.componentInstance.parameters[0].name).toBe("a");
      expect(fixture.componentInstance.parameters[1].name).toBe("2test");
      expect(fixture.componentInstance.parameters[1].array).toBe(true);
      expect(fixture.componentInstance.parameters[1].value.value).toBe("a,b,c");
   });

   //Bug #19872 can edit parameter
   it("check can edit parameter", () => {
      fixture.componentInstance.index = 0;
      fixture.componentInstance.parameters =
         [{name: "a", value: {value: "a", type: ValueTypes.VALUE}, array: false, type: "string"}];
      fixture.detectChanges();

      //Bug #19898 show correct dialog title
      let dialogTitle = fixture.debugElement.query(By.css("modal-header")).nativeElement;
      expect(TestUtils.toString(dialogTitle.getAttribute("title").trim())).toBe("Edit Parameter");

      let name = fixture.debugElement.query(By.css("input[formControlName=name]")).nativeElement;
      name.value = "a1";
      name.dispatchEvent(new Event("input"));
      fixture.detectChanges();

      let okBtn = fixture.debugElement.query(By.css("button.btn.btn-primary")).nativeElement;
      okBtn.click();
      fixture.detectChanges();

      expect(fixture.componentInstance.parameters.length).toBe(1);
      expect(fixture.componentInstance.parameters[0].name).toBe("a1");
   });

   //Bug #21445 can create time parameter
   xit("create time parameters", () => {
      fixture.componentInstance.index = -1;
      fixture.componentInstance.parameters = [];
      fixture.detectChanges();

      let type = fixture.debugElement.query(By.css("select")).nativeElement;
      type.value = "time";
      type.dispatchEvent(new Event("change"));
      fixture.detectChanges();

      let name = fixture.debugElement.query(By.css("input[formControlName=name]")).nativeElement;
      name.value = "test";
      name.dispatchEvent(new Event("input"));
      fixture.componentInstance.addParaDialog.changeValue("11:11:23");
      fixture.detectChanges();

      let okBtn = fixture.debugElement.query(By.css("button.btn.btn-primary")).nativeElement;
      okBtn.click();
      fixture.detectChanges();

      expect(fixture.componentInstance.parameters[0].name).toBe("test");
      expect(fixture.componentInstance.parameters[0].type).toBe("time");
      expect(fixture.componentInstance.parameters[0].value).toBe("11:11:23");
   });
});