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
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { async, ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { ConditionValueType } from "../../common/data/condition/condition-value-type";
import { ConditionEditor } from "./condition-editor.component";
import { ConditionValueTypePipe } from "./condition-value-type.pipe";
import { ConditionValuePipe } from "./condition-value.pipe";
import { ExpressionEditor } from "./expression-editor.component";
import { FieldEditor } from "./field-editor.component";
import { NumberValueEditor } from "./number-value-editor.component";
import { OneOfConditionEditor } from "./one-of-condition-editor.component";
import { SessionDataEditor } from "./session-data-editor.component";
import { SubqueryEditor } from "./subquery-editor.component";
import { TopNEditor } from "./top-n-editor.component";
import { ValueEditor } from "./value-editor.component";
import { VariableEditor } from "./variable-editor.component";

describe("One Of Condition Editor Unit Test", () => {
   let fixture: ComponentFixture<OneOfConditionEditor>;
   let oneOfEditor: OneOfConditionEditor;

   beforeEach(async(() => {
      TestBed.configureTestingModule({
         imports: [
            FormsModule, ReactiveFormsModule, NgbModule
         ],
         declarations: [
            OneOfConditionEditor, ConditionEditor, TopNEditor, ValueEditor,
            VariableEditor, ExpressionEditor, FieldEditor, SubqueryEditor,
            SessionDataEditor, NumberValueEditor, ConditionValueTypePipe,
            ConditionValuePipe
         ],
         schemas: [ NO_ERRORS_SCHEMA ]
      });
      TestBed.compileComponents();

      fixture = TestBed.createComponent(OneOfConditionEditor);
      oneOfEditor = <OneOfConditionEditor>fixture.componentInstance;
   }));

   //Bug #18994 delete should disabled when no item
   it("delete should disabled when no item", () => {
      oneOfEditor.valueTypes = [ConditionValueType.VALUE, ConditionValueType.VARIABLE,
         ConditionValueType.EXPRESSION, ConditionValueType.FIELD,
         ConditionValueType.SESSION_DATA];
      oneOfEditor.values = [{value: "CO", type: ConditionValueType.VALUE},
         {value: "CT", type: ConditionValueType.VALUE}];
      oneOfEditor.value = {value: null, type: ConditionValueType.VALUE};
      fixture.detectChanges();

      let valueList = fixture.nativeElement.querySelectorAll("div.sm-selectable-list div");
      expect(valueList.length).toBe(2);
      valueList[0].click();
      fixture.detectChanges();

      let delBtn = fixture.nativeElement.querySelector("button.delete_id");
      delBtn.click();
      fixture.detectChanges();

      valueList = fixture.nativeElement.querySelectorAll("div.sm-selectable-list div");
      expect(valueList.length).toBe(1);
      valueList[0].click();
      fixture.detectChanges();

      delBtn = fixture.nativeElement.querySelector("button.delete_id");
      delBtn.click();
      fixture.detectChanges();

      valueList = fixture.nativeElement.querySelector("div.sm-selectable-list div");
      delBtn = fixture.nativeElement.querySelector("button.delete_id");
      expect(valueList).toBeNull();
      expect(delBtn.hasAttribute("disabled")).toBeTruthy();
   });
});