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
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { LargeFormFieldComponent } from "../../../large-form-field/large-form-field.component";
import { VariableListEditor } from "./variable-list-editor.component";

describe("variable list editor unit case", () => {
   let fixture: ComponentFixture<VariableListEditor>;
   let variableEditor: VariableListEditor;

   beforeEach(() => {
      TestBed.configureTestingModule({
         imports: [ReactiveFormsModule, FormsModule, NgbModule],
         declarations: [VariableListEditor, LargeFormFieldComponent],
         providers: []
      }).compileComponents();

      fixture = TestBed.createComponent(VariableListEditor);
      variableEditor = <VariableListEditor>fixture.componentInstance;
   });

   //Bug #20063
   it("the vatiable list staus check", () => {
      variableEditor.dataType = "string";
      variableEditor.variableList = [
         {label: "a", value: "a1", valid: true},
         {label: "b", value: "b1", valid: true},
         {label: "c", value: "c1", valid: true}];

      fixture.detectChanges();
      let buttons = fixture.nativeElement.querySelectorAll("button");
      let add = buttons[0];
      let del = buttons[1];
      let clear = buttons[2];
      let moveUp = buttons[3];
      let moveDown = buttons[4];
      expect(add.disabled).toBeFalsy();
      expect(del.disabled).toBeTruthy();
      expect(clear.disabled).toBeFalsy();
      expect(moveUp.disabled).toBeTruthy();
      expect(moveDown.disabled).toBeTruthy();

      variableEditor.selectedIndex = 0;
      fixture.detectChanges();
      expect(del.disabled).toBeFalsy();
      expect(moveDown.disabled).toBeFalsy();

      variableEditor.selectedIndex = 1;
      fixture.detectChanges();
      expect(moveUp.disabled).toBeFalsy();

      variableEditor.selectedIndex = 2;
      fixture.detectChanges();
      expect(moveDown.disabled).toBeTruthy();

      variableEditor.deleteRow();
      fixture.detectChanges();
      expect(variableEditor.selectedIndex).toBe(1);
   });

   //Bug #20594
   it("time input check", () => {
      variableEditor.dataType = "time";
      variableEditor.variableList = [];
      variableEditor.addRow();
      fixture.detectChanges();

      let valueInput: HTMLInputElement = fixture.nativeElement.querySelectorAll("input")[1];
      expect(valueInput.getAttribute("placeholder")).toBe("HH:mm:ss");
      valueInput.value = "10:10:10";
      valueInput.dispatchEvent(new Event("input"));
      fixture.detectChanges();
      expect(variableEditor.variableList[0].valid).toBeTruthy();
      expect(variableEditor.variableList[0].value).toBe("10:10:10");

      valueInput.value = "10:10:10 AM";
      valueInput.dispatchEvent(new Event("input"));
      fixture.detectChanges();
      expect(variableEditor.variableList[0].valid).toBeFalsy();
      expect(document.querySelector(".alert-danger")).toBeDefined();
   });

   //Bug #20594
   it("date input check", () => {
      variableEditor.dataType = "timeInstant";
      variableEditor.variableList = [];
      variableEditor.addRow();
      fixture.detectChanges();

      let valueInput: HTMLInputElement = fixture.nativeElement.querySelectorAll("input")[1];
      expect(valueInput.getAttribute("placeholder")).toBe("yyyy-MM-dd HH:mm:ss");
      valueInput.value = "2017-12-27 10:10:10";
      valueInput.dispatchEvent(new Event("input"));
      fixture.detectChanges();
      expect(variableEditor.variableList[0].valid).toBeTruthy();
      expect(variableEditor.variableList[0].value).toBe("2017-12-27 10:10:10");

      valueInput.value = "2017-12-27";
      valueInput.dispatchEvent(new Event("input"));
      fixture.detectChanges();
      expect(variableEditor.variableList[0].valid).toBeFalsy();
      expect(document.querySelector(".alert-danger")).toBeDefined();
   });
});