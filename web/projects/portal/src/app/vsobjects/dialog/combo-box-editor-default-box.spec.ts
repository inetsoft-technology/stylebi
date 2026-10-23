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
import { NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { VariableListDialogModel } from "../../widget/dialog/variable-list-dialog/variable-list-dialog-model";
import { DialogService } from "../../widget/slide-out/dialog-service.service";
import { ComboBoxEditorModel } from "../model/combo-box-editor-model";
import { ComboBoxDefaultValueListModel } from "../model/combo-box-queryList-model";
import { SelectionListDialogModel } from "../model/selection-list-dialog-model";
import { SelectionListEditorModel } from "../model/selection-list-editor-model";
import { ComboBoxEditor } from "./combo-box-editor.component";

// Bug #78523: the >500 virtual-scroll default value box was blank, and exactly 500
// values rendered no default value control at all.
describe("Combo Box Editor default value box (Bug #78523)", () => {
   let fixture: ComponentFixture<ComboBoxEditor>;
   let editor: ComboBoxEditor;

   const list = (n: number): ComboBoxDefaultValueListModel[] =>
      Array.from({length: n}, (_, i) => ({label: "L" + i, value: "v" + i, formatValue: "F" + i}));

   const render = (n: number, defaultValue: any) => {
      editor.model = <ComboBoxEditorModel> {
         embedded: false, query: true, noDefault: false, valid: true, dataType: "string",
         calendar: false, type: "", defaultValue,
         selectionListDialogModel: <SelectionListDialogModel> {
            selectionListEditorModel: <SelectionListEditorModel> {}
         },
         variableListDialogModel: <VariableListDialogModel> {}
      };
      editor.showCalendar = true;
      editor.valueList = list(n);
      fixture.detectChanges();
   };

   const select = () => fixture.nativeElement.querySelector("select.defaultvalue");
   const box = () => fixture.nativeElement.querySelector(".virtual-scroll-combo-box");

   beforeEach(() => {
      TestBed.configureTestingModule({
         imports: [ReactiveFormsModule, FormsModule, NgbModule, HttpClientTestingModule, ComboBoxEditor],
         providers: [{provide: DialogService, useValue: {open: vi.fn()}}],
         schemas: [NO_ERRORS_SCHEMA]
      }).compileComponents();

      fixture = TestBed.createComponent(ComboBoxEditor);
      editor = fixture.componentInstance;
   });

   it("renders the select for 499 and 500 values and no virtual box", () => {
      for(const n of [499, 500]) {
         render(n, "v3");
         expect(select()).not.toBeNull();
         expect(box()).toBeNull();
      }
   });

   it("renders exactly one control at 500 (gap closed)", () => {
      render(500, "v3");
      expect(select()).not.toBeNull();
      expect(box()).toBeNull();
   });

   it("renders the virtual box showing the selected entry text for 501 and 600 values", () => {
      for(const n of [501, 600]) {
         render(n, "v3");
         expect(select()).toBeNull();
         expect(box().textContent.trim()).toBe("F3");
      }
   });

   it("shows the raw default value when it is not in the list", () => {
      render(600, "zzz");
      expect(box().textContent.trim()).toBe("zzz");
   });

   it("shows the displayed text (label | format) when Label != Value", () => {
      render(501, "v7");
      editor.valueList.forEach(e => e.formatValue = e.label + " | " + e.formatValue);
      fixture.detectChanges();
      expect(box().textContent.trim()).toBe("L7 | F7");
   });

   it("tolerates an undefined valueList and null default", () => {
      render(10, null);
      editor.valueList = undefined;
      expect(editor.defaultValueText).toBe("");
      editor.model.defaultValue = "x";
      expect(editor.defaultValueText).toBe("x");
   });
});
