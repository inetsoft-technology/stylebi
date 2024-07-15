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
import { NgModule } from "@angular/core";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { XSchema } from "../../common/data/xschema";
import { DateValueEditorComponent } from "../date-type-editor/date-value-editor.component";
import { TimeInstantValueEditorComponent } from "../date-type-editor/time-instant-value-editor.component";
import { TimeValueEditorComponent } from "../date-type-editor/time-value-editor.component";
import { BooleanValueEditor } from "./boolean-value-editor.component";
import { DateInValueEditor } from "./date-in-value-editor.component";
import { NumberValueEditor } from "./number-value-editor.component";
import { StringValueEditor } from "./string-value-editor.component";
import { ValueEditor } from "./value-editor.component";
import { FixedDropdownDirective } from "../fixed-dropdown/fixed-dropdown.directive";
import { TimepickerComponent } from "../date-type-editor/timepicker.component";
import { CharValueEditor } from "./char-value-editor.component";
import { DropDownTestModule } from "../../common/test/test-module";

describe("Value Editor Component Unit Case", () => {
   let fixture: ComponentFixture<ValueEditor>;
   let valueEditor: ValueEditor;

   beforeEach(() => {
      TestBed.configureTestingModule({
         imports: [
            ReactiveFormsModule, FormsModule, NgbModule, DropDownTestModule
         ],
         declarations: [
            ValueEditor, StringValueEditor, NumberValueEditor, BooleanValueEditor,
            DateValueEditorComponent, TimeValueEditorComponent, DateInValueEditor,
            TimeInstantValueEditorComponent, CharValueEditor, TimepickerComponent,
            FixedDropdownDirective
         ]
      });

      TestBed.compileComponents();
      fixture = TestBed.createComponent(ValueEditor);
      valueEditor = fixture.componentInstance;
      fixture.detectChanges();
   });

   //Bug #19808 should load correct editor for char type
   it("should load rightly string, number, boolean, date, char editor", () => {
      let editor: HTMLElement;

      valueEditor.type = XSchema.STRING;
      fixture.detectChanges();
      editor = fixture.nativeElement.querySelector("string-value-editor");
      expect(editor).not.toBe(null);

      valueEditor.type = XSchema.BOOLEAN;
      fixture.detectChanges();
      editor = fixture.nativeElement.querySelector("boolean-value-editor");
      expect(editor).not.toBe(null);

      valueEditor.type = XSchema.BYTE;
      fixture.detectChanges();
      editor = fixture.nativeElement.querySelector("number-value-editor");
      expect(editor).not.toBe(null);

      valueEditor.type = XSchema.TIME_INSTANT;
      fixture.detectChanges();
      editor = fixture.nativeElement.querySelector("time-instant-value-editor");
      expect(editor).not.toBe(null);

      valueEditor.type = XSchema.CHAR;
      fixture.detectChanges();
      editor = fixture.nativeElement.querySelector("char-value-editor");
      expect(editor).not.toBe(null);
   });

   //bug #18629 Browse data icon should be hidden on calculated field
   it("Browse data icon status", () => {
      valueEditor.type = XSchema.STRING;
      valueEditor.field = {
         name: "aa",
         dataType: "string",
         classType: "CalculateRef"
      };
      valueEditor.operation = 1;
      valueEditor.dataList = [];
      fixture.detectChanges();

      let browseBtn = fixture.nativeElement.querySelector("button i.value-list-icon");
      expect(browseBtn).toBe(null);

      valueEditor.field = {
         name: "aa",
         dataType: "string",
         classType: "BaseField"
      };
      fixture.detectChanges();

      browseBtn = fixture.nativeElement.querySelector("button i.value-list-icon");
      expect(browseBtn).not.toBe(null);
   });
});