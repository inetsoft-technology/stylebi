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
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { By } from "@angular/platform-browser";
import { NgbModule } from "@ng-bootstrap/ng-bootstrap";
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
         imports: [ReactiveFormsModule, FormsModule, NgbModule, HttpClientTestingModule],
         declarations: [InputParameterDialog, EnterSubmitDirective, ModalHeaderComponent],
         schemas: [NO_ERRORS_SCHEMA]
      }).compileComponents();

      fixture = TestBed.createComponent(InputParameterDialog);
      inputParameterDialog = <InputParameterDialog>fixture.componentInstance;
   });

   //Bug #19479
   it("check default status", () => {
      inputParameterDialog.fields = [TestUtils.createMockColumnRef("city")];
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

   //Bug #74148 should default to constant when fields are empty
   it("should default to constant valueSource when no fields", () => {
      inputParameterDialog.fields = [];
      inputParameterDialog.selectEdit = false;
      inputParameterDialog.model = createModel();
      fixture.detectChanges();
      expect(inputParameterDialog.model.valueSource).toBe("constant");
      let fieldRadio = fixture.debugElement.query(By.css("input.field_id")).nativeElement;
      expect(fieldRadio.disabled).toBeTruthy();
   });

   //Bug #74148 should reset to constant when editing a field param with no fields
   it("should reset valueSource to constant when editing with no fields", () => {
      let model = createModel();
      model.name = "para1";
      model.valueSource = "field";
      inputParameterDialog.fields = [];
      inputParameterDialog.selectEdit = true;
      inputParameterDialog.model = model;
      fixture.detectChanges();
      expect(inputParameterDialog.model.valueSource).toBe("constant");
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