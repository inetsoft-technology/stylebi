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
import { Component, EventEmitter, Input, OnChanges, OnInit, Output, SimpleChanges } from "@angular/core";
import { DateTypeFormatter } from "../../../../../shared/util/date-type-formatter";
import { TimeInstant } from "../../common/data/time-instant";
import { ComboMode, ValueMode } from "../dynamic-combo-box/dynamic-combo-box-model";
import { TreeNodeModel } from "../tree/tree-node-model";
import { DynamicValueModel, ValueTypes } from "../../vsobjects/model/dynamic-value-model";
import { XSchema } from "../../common/data/xschema";
import { FormulaEditorDialogModel } from "../formula-editor/formula-editor-dialog-model";

@Component({
   selector: "dynamic-value-editor",
   templateUrl: "./dynamic-value-editor.component.html",
   styleUrls: ["./dynamic-value-editor.component.scss"]
})
export class DynamicValueEditorComponent implements OnInit, OnChanges {
   @Input() type: string = XSchema.DATE;
   @Input() valueModel: DynamicValueModel;
   @Input() disable: boolean = false;
   @Input() variableValues: string[] = [];
   @Input() columnTreeRoot: TreeNodeModel = null;
   @Input() functionTreeRoot: TreeNodeModel = null;
   @Input() operatorTreeRoot: TreeNodeModel = null;
   @Input() scriptDefinitions: any = null;
   @Input() isInterval: boolean = false;
   @Input() today: boolean = false;
   @Input() defaultValue: string;
   @Input() editable: boolean = true;
   @Input() label: string;
   @Input() forceToDefault: boolean = false;
   @Input() invalid: boolean;
   @Input() supportVariable: boolean = true;
   @Input() task: boolean = false;
   @Input() expressionSubmitCallback: (_?: FormulaEditorDialogModel) => Promise<boolean> =
      () => Promise.resolve(true);
   @Output() onValueModelChange = new EventEmitter();
   public XSchema = XSchema;
   public ComboMode = ComboMode;
   dateTime: TimeInstant;
   readonly TIME_INSTANT_FORMAT: string = "YYYY-MM-DD HH:mm:ss";
   booleanValues: any[] = [
      {value: true, label: "_#(js:True)"},
      {value: false, label: "_#(js:False)"}];

   constructor() {
   }

   ngOnInit(): void {
      const validDate = !!this.valueModel.value &&
         !!DateTypeFormatter.formatStr(this.valueModel.value, this.format);
      let date;

      if(validDate) {
         date = this.valueModel.value;
      }
      else {
         if(this.defaultValue && !!DateTypeFormatter.formatStr(this.defaultValue, this.format)) {
            date = this.defaultValue;
         }
         else {
            date = DateTypeFormatter.currentTimeInstantInFormat(this.format);
         }
      }

      this.dateTime = DateTypeFormatter.toTimeInstant(date, this.format);

      if(this.isInterval && !validDate && this.valueModel.type == ValueTypes.VALUE) {
         this.valueModel.value = date;
      }
   }

   ngOnChanges(changes: SimpleChanges) {
      if(changes.today?.currentValue && this.valueModel.type == ValueTypes.VALUE) {
         this.valueModel.value = DateTypeFormatter.currentTimeInstantInFormat(this.format);
         this.dateTime = DateTypeFormatter.toTimeInstant(this.valueModel.value, this.format);
      }

      if((changes.defaultValue?.currentValue && this.forceToDefault ||
          changes.forceToDefault && changes.forceToDefault.currentValue) && this.valueModel.type == ValueTypes.VALUE)
      {
         if(!!DateTypeFormatter.formatStr(this.defaultValue, this.format)) {
            this.valueModel.value = this.defaultValue;
         }
         else {
            this.valueModel.value = DateTypeFormatter.currentTimeInstantInFormat(this.format);
         }

         this.dateTime = DateTypeFormatter.toTimeInstant(this.valueModel.value, this.format);
      }
   }

   get mode(): ValueMode {
      return XSchema.isNumericType(this.type) ? ValueMode.NUMBER : ValueMode.TEXT;
   }

   get isCalendarDisable(): boolean {
      return this.disable || this.valueModel.type == ValueTypes.EXPRESSION ||
         this.valueModel.type == ValueTypes.VARIABLE;
   }

   get isDate() {
      return XSchema.isDateType(this.type);
   }

   getType(): number {
      return this.getDateValueTypeNumber(this.valueModel.type);
   }

   updateValue(value: any): void {
      this.valueModel.value = value;
      this.onValueModelChange.emit();
   }

   updateType(type: number): void {
      this.valueModel.type = this.getDateValueTypeStr(type);
      this.onValueModelChange.emit();
   }

   dateChange(date: string): void {
      this.valueModel.value = date;
      this.onValueModelChange.emit();
   }

   getDateValueTypeNumber(type: string): number {
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

   getDateValueTypeStr(type: number): string {
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

   get format(): string {
      return this.type == XSchema.DATE ? DateTypeFormatter.ISO_8601_DATE_FORMAT : this.type == XSchema.TIME_INSTANT
         ? this.TIME_INSTANT_FORMAT : DateTypeFormatter.ISO_8601_TIME_FORMAT;
   }

   getPromptString() {
      let promptString: string = "_#(js:Value)";

      if(this.getType() != ComboMode.VALUE) {
         return promptString;
      }

      if(this.type == XSchema.DATE) {
         promptString = "yyyy-mm-dd";
      }
      else if(this.type == XSchema.TIME) {
         promptString = "hh:mm:ss";
      }
      else if(this.type == XSchema.TIME_INSTANT) {
         promptString = "yyyy-mm-dd hh:mm:ss";
      }

      return promptString;
   }

   isCalendarVisible(): boolean {
      if(this.valueModel.type == ValueTypes.EXPRESSION ||
         this.valueModel.type == ValueTypes.VARIABLE)
      {
         return false;
      }

      return XSchema.isDateType(this.type);
   }
}
