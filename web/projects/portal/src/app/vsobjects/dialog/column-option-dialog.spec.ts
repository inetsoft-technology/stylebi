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
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { FormGroup, FormsModule, ReactiveFormsModule } from "@angular/forms";
import { By } from "@angular/platform-browser";
import { NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { EnterSubmitDirective } from "../../widget/directive/enter-submit.directive";
import { ColumnOptionDialogModel } from "../model/column-option-dialog-model";
import { ColumnOptionDialog } from "./column-option-dialog.component";
import { ComboBoxEditor } from "./combo-box-editor.component";
import { DateEditor } from "./date-editor.component";
import { FloatEditor } from "./float-editor.component";
import { IntegerEditor } from "./integer-editor.component";
import { TextEditor } from "./text-editor.component";

describe("Column option dialog Test", () => {
   const createModel: () => ColumnOptionDialogModel = () => {
      return {
         enableColumnEditing: true,
         inputControl: "",
         editor: null,
         comboBoxBlankEditor: null
      };
   };

   let fixture: ComponentFixture<ColumnOptionDialog>;
   let columnOptionDialog: ColumnOptionDialog;

   beforeEach(() => {
      TestBed.configureTestingModule({
         imports: [
            FormsModule, ReactiveFormsModule, NgbModule
         ],
         declarations: [
            ColumnOptionDialog, TextEditor, DateEditor, ComboBoxEditor,
            IntegerEditor, FloatEditor, EnterSubmitDirective
         ],
         schemas: [NO_ERRORS_SCHEMA]
      });
      TestBed.compileComponents();
   });

   //bug #18565, input valid check for date editor
   it("input valid check for date editor", () => { // broken
      fixture = TestBed.createComponent(ColumnOptionDialog);
      columnOptionDialog = <ColumnOptionDialog> fixture.componentInstance;
      columnOptionDialog.model = createModel();
      columnOptionDialog.model.inputControl = "Date";
      columnOptionDialog.form = new FormGroup({});
      fixture.detectChanges();

      let minDate = fixture.debugElement.query(By.css("input[ng-reflect-name=min]")).nativeElement;
      let maxDate = fixture.debugElement.query(By.css("input[ng-reflect-name=max]")).nativeElement;

      minDate.value = "2017-09-23";
      minDate.dispatchEvent(new Event("input"));
      maxDate.value = "2017-08-23";
      maxDate.dispatchEvent(new Event("input"));
      fixture.detectChanges();

      let okBtn = fixture.debugElement.query(By.css("button.btn.btn-primary")).nativeElement;
      let warning = fixture.debugElement.query(By.css("div.alert.alert-danger")).nativeElement;

      expect(okBtn.hasAttribute("disabled")).toBeTruthy();
      expect(warning.textContent).toContain(
         "_#(viewer.formEditor.minMaxValid) ");

      maxDate.value = "2017-10-23";
      maxDate.dispatchEvent(new Event("input"));
      fixture.detectChanges();

      okBtn = fixture.debugElement.query(By.css("button.btn.btn-primary")).nativeElement;
      warning = fixture.debugElement.query(By.css("div.alert.alert-danger"));
      expect(okBtn.hasAttribute("disabled")).toBeFalsy();
      expect(warning).toBeNull();
   });
});
