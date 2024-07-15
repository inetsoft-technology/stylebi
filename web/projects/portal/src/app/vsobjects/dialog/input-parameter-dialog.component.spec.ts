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
import { CommonModule } from "@angular/common";
import { ChangeDetectorRef, NO_ERRORS_SCHEMA } from "@angular/core";
import { ComponentFixture, inject, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { By } from "@angular/platform-browser";
import { NgbDateParserFormatter, NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { XSchema } from "../../common/data/xschema";
import { TestUtils } from "../../common/test/test-utils";
import { EnterSubmitDirective } from "../../widget/directive/enter-submit.directive";
import { ModalHeaderComponent } from "../../widget/modal-header/modal-header.component";
import { InputParameterDialogModel } from "../model/input-parameter-dialog-model";
import { InputParameterDialog } from "./input-parameter-dialog.component";

let createModel: () => InputParameterDialogModel = () => {
   return {
      name: "",
      type: "string",
      value: "",
      valueSource: "field"
   };
};

describe("input parameter dialog component unit case", () => {
   let fixture: ComponentFixture<InputParameterDialog>;
   let inputParameterDialog: InputParameterDialog;

   beforeEach(() => {
      TestBed.configureTestingModule({
         imports: [ReactiveFormsModule, FormsModule, NgbModule],
         declarations: [InputParameterDialog, EnterSubmitDirective, ModalHeaderComponent],
         schemas: [NO_ERRORS_SCHEMA]
      }).compileComponents();

      fixture = TestBed.createComponent(InputParameterDialog);
      inputParameterDialog = <InputParameterDialog>fixture.componentInstance;
   });

   //Bug #19479
   it("check default status", () => {
      inputParameterDialog.model = createModel();
      fixture.detectChanges();
      let errorMsg: HTMLElement = fixture.debugElement.query(By.css("span.invalid-feedback")).nativeElement;
      expect(TestUtils.toString(errorMsg.textContent.trim())).toBe("parameter.name.emptyValid");
      expect(fixture.debugElement.query(By.css("input.field_id")).nativeElement.checked).toBeTruthy();
   });

   //Bug #19636
   //Bug #20918 should control special character for parameter name
   it("parameter name check", () => {
      inputParameterDialog.model = createModel();
      fixture.detectChanges();
      let name: HTMLInputElement = fixture.debugElement.query(By.css("input[name='name']")).nativeElement;
      name.value = "aa_bb";
      name.dispatchEvent(new Event("input"));
      fixture.detectChanges();
      expect(fixture.debugElement.query(By.css("span.invalid-feedback"))).toBeNull();

      name.value = "^!%";
      name.dispatchEvent(new Event("input"));
      fixture.detectChanges();
      expect(TestUtils.toString(fixture.debugElement.query(By.css("span.invalid-feedback")).nativeElement.textContent)).toBe(
         "parameter.name.characterValid");
   });

   //Bug #21452 should commit right model info
   it("should commit right info", () => {
      let model = createModel();
      model.name = "para1";
      inputParameterDialog.fields = [TestUtils.createMockColumnRef("city")];
      inputParameterDialog.model = model;

      fixture.detectChanges();
      inputParameterDialog.ok();
      fixture.detectChanges();
      expect(inputParameterDialog.model.value).toBe("city");
   });
});