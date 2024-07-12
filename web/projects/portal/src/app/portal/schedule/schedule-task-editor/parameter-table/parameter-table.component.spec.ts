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
import { By } from "@angular/platform-browser";
import { NgbModal, NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { ComponentTool } from "../../../../common/util/component-tool";
import { DateValueEditorComponent } from "../../../../widget/date-type-editor/date-value-editor.component";
import { TimeInstantValueEditorComponent } from "../../../../widget/date-type-editor/time-instant-value-editor.component";
import { TimeValueEditorComponent } from "../../../../widget/date-type-editor/time-value-editor.component";
import { TimepickerComponent } from "../../../../widget/date-type-editor/timepicker.component";
import { EnterSubmitDirective } from "../../../../widget/directive/enter-submit.directive";
import { ReplaceAllPipe } from "../../../../widget/pipe/replace-all.pipe";
import { AddParameterDialog } from "../add-parameter-dialog/add-parameter-dialog.component";
import { ParameterTable } from "./parameter-table.component";
import { ValueTypes } from "../../../../vsobjects/model/dynamic-value-model";

describe("Parameter Table Unit Test", () => {
   let fixture: ComponentFixture<ParameterTable>;
   let parameterTable: ParameterTable;

   beforeEach(async(() => {
      TestBed.configureTestingModule({
         imports: [
            FormsModule, ReactiveFormsModule, NgbModule
         ],
         declarations: [
            ReplaceAllPipe, ParameterTable, AddParameterDialog, EnterSubmitDirective,
            DateValueEditorComponent,
            TimeValueEditorComponent, TimeInstantValueEditorComponent, TimepickerComponent,
         ],
         schemas: [ NO_ERRORS_SCHEMA ]
      });
      TestBed.compileComponents();

      fixture = TestBed.createComponent(ParameterTable);
      parameterTable = <ParameterTable>fixture.componentInstance;
      fixture.detectChanges();
   }));

   //Bug #19685 should pop confirm dialog when delete parameter
   //Bug #19894 should disable clear all when no parameter
   it("should show confirm when to delete parameter", () => {
      parameterTable.parameters = [];
      fixture.detectChanges();

      let clearAll = fixture.debugElement.query(By.css("button.clear_all_id")).nativeElement;
      expect(clearAll.hasAttribute("disabled")).toBeTruthy();

      parameterTable.parameters =
         [{name: "a", type: "string", value: {value: "a", type: ValueTypes.VALUE}, array: false}];
      fixture.detectChanges();

      let removeIcon = fixture.debugElement.query(By.css(".trash-icon")).nativeElement;
      let showConfirmDialog = jest.spyOn(ComponentTool, "showConfirmDialog");
      showConfirmDialog.mockImplementation(() => Promise.resolve("ok"));
      removeIcon.click();
      expect(showConfirmDialog).toHaveBeenCalled();

      parameterTable.clearAllParameters();
      expect(showConfirmDialog).toHaveBeenCalledTimes(2);
   });

   //Bug #19872 can edit parameter
   it("check can edit parameter", () => {
      parameterTable.parameters = [
         {name: "a", type: "string", value: {value: "a", type: ValueTypes.VALUE}, array: false},
         {name: "b", type: "string", value: {value: "b", type: ValueTypes.VALUE}, array: false}
       ];
      fixture.detectChanges();

      const ngbService = TestBed.inject(NgbModal);
      const openDialog = jest.spyOn(ngbService, "open");
      openDialog.mockImplementation(() => (<any> { result: Promise.resolve([]) }));

      const editIcon = fixture.nativeElement.querySelectorAll(".edit-icon");
      editIcon[0].click();
      fixture.detectChanges();

      expect(parameterTable.editIndex).toBe(0);
   });

   //Bug #21463 should display timeinstant parameter correctly
   it("should display timeinstant parameter correctly", () => {
      parameterTable.parameters = [
         {
            name: "a",
            type: "timeInstant",
            value: {value: "2018-01-01T11:29:28", type: ValueTypes.VALUE},
            array: false
         }
      ];
      fixture.detectChanges();

      let value = fixture.nativeElement.querySelectorAll("tr td")[1];
      let valueText = value.textContent;
      expect(valueText).toBeTruthy();
      valueText = valueText.replace(/^\s+/, "").replace(/\s+$/, "");
      expect(valueText).toBe("2018-01-01 11:29:28");
   });

   //Bug #21470 should display array parameter correctly
   it("should display array parameter correctly", () => {
      parameterTable.parameters =
         [{name: "a", type: "string", value: {value: "a,b,c", type: ValueTypes.VALUE}, array: true}];
      fixture.detectChanges();

      let value = fixture.nativeElement.querySelectorAll("tr td")[1];
      let type = fixture.nativeElement.querySelectorAll("tr td")[2];
      let valueText = value.textContent;
      expect(valueText).toBeTruthy();
      valueText = valueText.replace(/^\s+/, "").replace(/\s+$/, "");
      expect(valueText).toBe("a,b,c");
      let typeText = type.textContent;
      expect(typeText).toBeTruthy();
      typeText = typeText.replace(/^\s+/, "").replace(/\s+$/, "");
      expect(typeText).toBe("Array");
   });
});
