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
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { By } from "@angular/platform-browser";
import { NgbModal, NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { DropDownTestModule } from "../../common/test/test-module";
import { TestUtils } from "../../common/test/test-utils";
import { FixedDropdownDirective } from "../fixed-dropdown/fixed-dropdown.directive";
import { ComboMode } from "../dynamic-combo-box/dynamic-combo-box-model";
import { TreeNodeComponent } from "../tree/tree-node.component";
import { TreeSearchPipe } from "../tree/tree-search.pipe";
import { TreeComponent } from "../tree/tree.component";
import { TargetComboBox } from "./target-combo-box.component";
import { ValueInputField } from "./value-input-field.component";

describe("ValueInputField Unit Test", () => {
   let valueInputField: ValueInputField;
   let fixture: ComponentFixture<ValueInputField>;

   beforeEach(() => {
      TestBed.configureTestingModule({
         imports: [
            DropDownTestModule, ReactiveFormsModule, FormsModule, NgbModule
         ],
         declarations: [
            ValueInputField, TargetComboBox, FixedDropdownDirective,
            TreeComponent, TreeSearchPipe, TreeNodeComponent
         ],
         providers: [ NgbModal ],
         schemas: [NO_ERRORS_SCHEMA]
      }).compileComponents();

      fixture = TestBed.createComponent(ValueInputField);
      valueInputField = <ValueInputField>fixture.componentInstance;
      fixture.detectChanges();
   });

   // Bug #10191 value should be editable if it is not a selected formula
   it("should be an editable value", () => {
      valueInputField.value = "100";
      let editable: boolean = valueInputField.isValueEditable();

      expect(editable).toBeTruthy();
   });

   it("should not be an editable value", () => {
      valueInputField.value = valueInputField.formulas[0].value;
      let editable: boolean = valueInputField.isValueEditable();

      expect(editable).toBeFalsy();
   });

   // Bug #10200 target value should be 'Max' for Maximum
   it("should have correct Maximum formula value", () => {
      let valueChange: any = { emit: jest.fn() };
      valueInputField.valueChange = valueChange;
      valueInputField.onValueChange(valueInputField.formulas[2].value);

      expect(valueChange.emit).toHaveBeenCalled();
      expect(valueChange.emit.mock.calls[0][0]).toEqual("Max");
   });

   // Bug #10200 target value should be 'Min' for Minimum
   it("should have correct Minimum formula value", () => {
      let valueChange: any = { emit: jest.fn() };

      valueInputField.valueChange = valueChange;
      valueInputField.onValueChange(valueInputField.formulas[1].value);

      expect(valueChange.emit).toHaveBeenCalled();
      expect(valueChange.emit.mock.calls[0][0]).toEqual("Min");
   });

   //Bug #18985
   xit("value input check", (done) => {
      let valueInput: HTMLInputElement = fixture.debugElement.query(By.css("dynamic-combo-box input")).nativeElement;
      valueInput.value = "==[";
      valueInput.dispatchEvent(new Event("change"));
      fixture.detectChanges();

      valueInput = fixture.debugElement.query(By.css("dynamic-combo-box input")).nativeElement;
      expect(valueInput.attributes["ng-reflect-model"].value).toEqual("");

      valueInput.click();
      fixture.detectChanges();
      fixture.whenStable().then(() => {
         let fixedDropdown = document.getElementsByTagName("fixed-dropdown")[0];
         expect(TestUtils.toString(fixedDropdown.querySelector("label").textContent.trim())).toBe("Enter a Value");

         done();
      });
   });

   //Bug #19114
   xit("the value should be clear when from expression to variable", (done) => {
      valueInputField.valueType = ComboMode.EXPRESSION;
      valueInputField.value = "=40";
      fixture.detectChanges();

      let valueInput: HTMLInputElement = fixture.debugElement.query(By.css("dynamic-combo-box input")).nativeElement;
      expect(valueInput.attributes["ng-reflect-model"].value).toEqual("=40");

      valueInputField.valueType = ComboMode.VALUE;
      valueInputField.formulaSupported = true;
      valueInputField.updateValues();
      fixture.detectChanges();

      valueInput = fixture.debugElement.query(By.css("dynamic-combo-box input")).nativeElement;
      valueInput.click();
      fixture.detectChanges();
      fixture.whenStable().then(() => {
         let fixedDropdown = document.getElementsByTagName("fixed-dropdown")[0];
         let items: any = fixedDropdown.querySelectorAll("a label");
         let itemValues: any[] = [];
         items.forEach((item: HTMLElement) => {
            itemValues.push(TestUtils.toString(item.textContent.trim()));
         });
         expect(itemValues).toEqual(["Enter a Value", "Average", "Minimum", "Maximum", "Median", "Sum"]);
         expect(itemValues).not.toContain("=40");

         done();
      });
   });
});