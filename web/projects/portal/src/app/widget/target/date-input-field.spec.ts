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
import { DynamicComboBox } from "../dynamic-combo-box/dynamic-combo-box.component";
import { TreeNodeComponent } from "../tree/tree-node.component";
import { TreeSearchPipe } from "../tree/tree-search.pipe";
import { TreeComponent } from "../tree/tree.component";
import { DateInputField } from "./date-input-field.component";
import { TargetComboBox } from "./target-combo-box.component";

describe("Date Input Field Unit Case: ", () =>  {
   let fixture: ComponentFixture<DateInputField>;
   let dateInputField: DateInputField;

   beforeEach(() => {
      TestBed.configureTestingModule({
         imports: [
            DropDownTestModule, ReactiveFormsModule, FormsModule, NgbModule
         ],
         declarations: [
            DateInputField, DynamicComboBox, TargetComboBox, FixedDropdownDirective, TreeComponent,
            TreeSearchPipe, TreeNodeComponent
         ],
         providers: [NgbModal],
         schemas: [NO_ERRORS_SCHEMA]
      }).compileComponents();

      fixture = TestBed.createComponent(DateInputField);
   });

   //Bug #18980, Bug #19235, Bug #23005 year value change and reload
   it("year type value input and reload", (done) => {
      dateInputField = <DateInputField>fixture.componentInstance;
      //year reload
      dateInputField.value = "2015";
      fixture.detectChanges();
      expect(dateInputField.values).toEqual(["2015"]);

      //year change
      dateInputField.formulaSupported = true;
      fixture.detectChanges();

      let dateInput: HTMLInputElement = fixture.debugElement.query(By.css("dynamic-combo-box input")).nativeElement;
      dateInput.value = "2014";
      dateInput.dispatchEvent(new Event("input"));
      fixture.detectChanges();

      dateInput.click();
      fixture.detectChanges();
      fixture.whenStable().then(() => {
         let fixedDropdown = document.getElementsByTagName("fixed-dropdown")[0];
         let dropdownItems: any = fixedDropdown.querySelectorAll(".dropdown-item-label label:nth-of-type(1)");
         expect(dropdownItems[0].textContent.trim()).toEqual("2014");
         //Bug #23005
         expect(dropdownItems.length).toEqual(3);
         expect(TestUtils.toString(dropdownItems[dropdownItems.length - 1].textContent.trim())).toEqual("Maximum");

         done();
      });
   });

   //Bug #19238 and  Bug #19458 month value change and reload
   it("month type input and reload", (done) => {
      dateInputField = <DateInputField>fixture.componentInstance;
      //month reload
      dateInputField.value = "2015-10";
      fixture.detectChanges();
      expect(dateInputField.values).toEqual(["2015-10"]);

      let dateInput: HTMLInputElement = fixture.debugElement.query(By.css("dynamic-combo-box input")).nativeElement;
      //month change
      dateInput.value = "2014-09";
      dateInput.dispatchEvent(new Event("input"));
      fixture.detectChanges();

      dateInput.click();
      fixture.detectChanges();
      fixture.whenStable().then(() => {
         let fixedDropdown = document.getElementsByTagName("fixed-dropdown")[0];
         let label = fixedDropdown.querySelector("label").textContent;
         expect(label).toBeTruthy();
         label = label.replace(/^\s+/, "").replace(/\s+$/, "");
         expect(label).toBe("2014-09");

         done();
      });
   });

   //Bug #19549
   it("day input and reload", (done) => {
      dateInputField = <DateInputField>fixture.componentInstance;
      //hour reload
      dateInputField.value = "2015-10-17";
      fixture.detectChanges();
      expect(dateInputField.values).toEqual(["2015-10-17"]);

      let dateInput: HTMLInputElement = fixture.debugElement.query(By.css("dynamic-combo-box input")).nativeElement;
      dateInput.value = "2014-10-17";
      dateInput.dispatchEvent(new Event("input"));
      fixture.detectChanges();

      dateInput.click();
      fixture.detectChanges();
      fixture.whenStable().then(() => {
         let fixedDropdown = document.getElementsByTagName("fixed-dropdown")[0];
         let label = fixedDropdown.querySelector("label").textContent;
         expect(label).toBeTruthy();
         label = label.replace(/^\s+/, "").replace(/\s+$/, "");
         expect(label).toBe("2014-10-17");

         done();
      });
   });

   //Bug #19549
   // bad test, don't test other components or access dom from outside the test fixture
   // it("hour input and reload", (done) => {
   //    dateInputField = <DateInputField>fixture.componentInstance;
   //    dateInputField.value = "2015-10-17 12";
   //    fixture.detectChanges();
   //    expect(dateInputField.values).toEqual(["2015-10-17 12"]);
   //
   //    let dateInput: HTMLInputElement = fixture.debugElement.query(By.css("dynamic-combo-box input")).nativeElement;
   //    dateInput.value = "2014-10-17 12";
   //    dateInput.dispatchEvent(new Event("input"));
   //    fixture.detectChanges();
   //
   //    dateInput.click();
   //    fixture.detectChanges();
   //    fixture.whenStable().then(() => {
   //       let fixedDropdown = document.getElementsByTagName("fixed-dropdown")[0];
   //       let label = fixedDropdown.querySelector("label").textContent;
   //       expect(label).toBeTruthy();
   //       label = label.replace(/^\s+/, "").replace(/\s+$/, "");
   //       expect(label).toBe("2014-10-17 12");
   //
   //       done();
   //    });
   // });

   //Bug #19549
   // bad test, don't test other components or access dom from outside the test fixture
   // it("minte input and reload", (done) => {
   //    dateInputField = <DateInputField>fixture.componentInstance;
   //    dateInputField.value = "2015-10-17 12:30";
   //    fixture.detectChanges();
   //    expect(dateInputField.values).toEqual(["2015-10-17 12:30"]);
   //
   //    let dateInput: HTMLInputElement = fixture.debugElement.query(By.css("dynamic-combo-box input")).nativeElement;
   //    dateInput.value = "2014-10-17 12:40";
   //    dateInput.dispatchEvent(new Event("input"));
   //    fixture.detectChanges();
   //
   //    dateInput.click();
   //    fixture.detectChanges();
   //    fixture.whenStable().then(() => {
   //       let fixedDropdown = document.getElementsByTagName("fixed-dropdown")[0];
   //       let label = fixedDropdown.querySelector("label").textContent;
   //       expect(label).toBeTruthy();
   //       label = label.replace(/^\s+/, "").replace(/\s+$/, "");
   //       expect(label).toBe("2014-10-17 12:40");
   //
   //       done();
   //    });
   // });

   //Bug #19299
   it("should not pop up error when clear date value", () => { // broken test
      dateInputField = <DateInputField>fixture.componentInstance;
      fixture.detectChanges();
      let dateInput: HTMLInputElement = fixture.debugElement.query(By.css("dynamic-combo-box input")).nativeElement;
      dateInput.value = "2014";
      dateInput.dispatchEvent(new Event("change"));
      fixture.detectChanges();
      dateInput.value = "";
      dateInput.dispatchEvent(new Event("change"));
      fixture.detectChanges();

      let alert = fixture.debugElement.query(By.css(".alert-danger"));
      expect(alert).toBeNull();
   });

   //Bug #21247 should not pop up waring when use variable as value
   it("should not pop warning when use variable as value", () => { // broken test
      dateInputField = <DateInputField>fixture.componentInstance;
      dateInputField.value = "${var1}";
      dateInputField.variables = ["${var1}"];
      fixture.detectChanges();

      let alert = fixture.debugElement.query(By.css(".alert-danger"));
      expect(alert).toBeNull();
   });
});
