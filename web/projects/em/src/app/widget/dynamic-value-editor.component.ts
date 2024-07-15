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
import { Component, EventEmitter, Input, Output } from "@angular/core";
import { DynamicValueModel, ValueTypes } from "../../../../portal/src/app/vsobjects/model/dynamic-value-model";
import { XSchema } from "../../../../portal/src/app/common/data/xschema";
import { ComboMode, ValueMode } from "../../../../portal/src/app/widget/dynamic-combo-box/dynamic-combo-box-model";
import { ScriptTreeDataSource } from "./script-tree-data-source";
import { UntypedFormGroup } from "@angular/forms";
import { FormulaEditorDialogModel } from "../../../../portal/src/app/widget/formula-editor/formula-editor-dialog-model";
import { DateTypeFormatter } from "../../../../shared/util/date-type-formatter";

@Component({
   selector: "em-dynamic-value-editor",
   templateUrl: "./dynamic-value-editor.component.html",
   styleUrls: ["./dynamic-value-editor.component.scss"]
})
export class DynamicValueEditorComponent {
   public XSchema = XSchema;
   @Input() isArray: boolean;
   @Input() type: string = XSchema.DATE;
   @Input() valueModel: DynamicValueModel;
   @Input() columnTreeRoot: ScriptTreeDataSource;
   @Input() form: UntypedFormGroup;
   @Input() task: boolean = false;
   @Input() expressionSubmitCallback: (_?: FormulaEditorDialogModel) => Promise<boolean> =
      () => Promise.resolve(true);
   @Output() onValueModelChange: EventEmitter<any> = new EventEmitter<any>();
   readonly TIME_INSTANT_FORMAT: string = "YYYY-MM-DD HH:mm:ss";
   booleanValues: any[] = [
      {value: true, label: "_#(js:True)"},
      {value: false, label: "_#(js:False)"}];

   get mode(): ValueMode {
      return XSchema.isNumericType(this.type) ? ValueMode.NUMBER : ValueMode.TEXT;
   }

   getType(): number {
      return this.getValueTypeNumber(this.valueModel.type);
   }

   get format(): string {
      return this.type == XSchema.DATE ? DateTypeFormatter.ISO_8601_DATE_FORMAT : this.type == XSchema.TIME_INSTANT
         ? this.TIME_INSTANT_FORMAT : DateTypeFormatter.ISO_8601_TIME_FORMAT;
   }

   updateValue(value: any): void {
      this.valueModel.value = value;
      this.onValueModelChange.emit();
   }

   updateType(type: number): void {
      this.valueModel.type = this.getValueTypeStr(type);
      this.onValueModelChange.emit();
   }

   getValueTypeStr(type: number): string {
      if(type == ComboMode.VALUE) {
         return ValueTypes.VALUE;
      }
      else if(type == ComboMode.VARIABLE) {
         return ValueTypes.VARIABLE;
      }
      else if(type == ComboMode.EXPRESSION) {
         return ValueTypes.EXPRESSION;
      }
      else {
         return ValueTypes.VALUE;
      }
   }

   getValueTypeNumber(type: string): number {
      if(type == ValueTypes.VALUE) {
         return ComboMode.VALUE;
      }
      if(type == ValueTypes.VARIABLE) {
         return ComboMode.VARIABLE;
      }
      else if(type == ValueTypes.EXPRESSION) {
         return ComboMode.EXPRESSION;
      }
      else {
         return ComboMode.VALUE;
      }
   }

   getValues() {
      return this.type == XSchema.BOOLEAN && !this.isArray ? this.booleanValues: [];
   }
}
