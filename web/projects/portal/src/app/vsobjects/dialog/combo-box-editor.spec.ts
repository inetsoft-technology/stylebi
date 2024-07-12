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
import { HttpClientTestingModule } from "@angular/common/http/testing";
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { VariableListDialogModel } from "../../widget/dialog/variable-list-dialog/variable-list-dialog-model";
import { DialogService } from "../../widget/slide-out/dialog-service.service";
import { ComboBoxEditorModel } from "../model/combo-box-editor-model";
import { SelectionListDialogModel } from "../model/selection-list-dialog-model";
import { SelectionListEditorModel } from "../model/selection-list-editor-model";
import { ComboBoxEditor } from "./combo-box-editor.component";

describe("Combo Box Editor Test", () => {
   const createModel: () => ComboBoxEditorModel = () => {
      return {
         embedded: true,
         query: false,
         noDefault: true,
         valid: false,
         dataType: "string",
         calendar: true,
         type: "",
         defaultValue: null,
         selectionListDialogModel: <SelectionListDialogModel> {
            selectionListEditorModel: <SelectionListEditorModel> {},
         },
         variableListDialogModel: <VariableListDialogModel> {}
      };
   };

   let fixture: ComponentFixture<ComboBoxEditor>;
   let comboboxEditor: ComboBoxEditor;
   let modalService: any;

   beforeEach(() => {
      modalService = { open: jest.fn() };

      TestBed.configureTestingModule({
         imports: [ReactiveFormsModule, FormsModule, NgbModule, HttpClientTestingModule],
         declarations: [ComboBoxEditor],
         providers: [{provide: DialogService, useValue: modalService}],
         schemas: [NO_ERRORS_SCHEMA]
      }).compileComponents();

      fixture = TestBed.createComponent(ComboBoxEditor);
      comboboxEditor = <ComboBoxEditor>fixture.componentInstance;
      comboboxEditor.model = createModel();
   });

   // Bug #9775 Missing the calendar checkbox
   it("should contain calendar boolean, and has to be disabled for non date types", () => {
      expect(comboboxEditor.model.calendar).toBeDefined();
      expect(comboboxEditor.calendarEnabled()).toBeFalsy();
   });

   //Bug #19096
   it("should disabled query and embedded edit button when embedded and calednar is true", () => {
      comboboxEditor.model.dataType = "date";
      comboboxEditor.model.calendar = true;
      comboboxEditor.model.embedded = true;

      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector("button.embedded-btn_id").disabled).toBeTruthy();
      expect(fixture.nativeElement.querySelector("button.query-btn_id").disabled).toBeTruthy();
   });

});
