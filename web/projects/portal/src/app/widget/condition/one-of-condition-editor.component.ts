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
import {
   Component,
   EventEmitter,
   Input,
   OnChanges,
   OnInit,
   Output,
   SimpleChanges
} from "@angular/core";
import { Observable } from "rxjs";
import { Tool } from "../../../../../shared/util/tool";
import { SourceInfo } from "../../binding/data/source-info";
import { BrowseDataModel } from "../../common/data/browse-data-model";
import { ConditionValue } from "../../common/data/condition/condition-value";
import { ConditionValueType } from "../../common/data/condition/condition-value-type";
import { ExpressionType } from "../../common/data/condition/expression-type";
import { ExpressionValue } from "../../common/data/condition/expression-value";
import { SubqueryTable } from "../../common/data/condition/subquery-table";
import { DataRef } from "../../common/data/data-ref";
import { XSchema } from "../../common/data/xschema";
import { TreeNodeModel } from "../tree/tree-node-model";
import { ConditionFieldComboModel } from "./condition-field-combo-model";

@Component({
   selector: "one-of-condition-editor",
   templateUrl: "one-of-condition-editor.component.html",
   styleUrls: ["one-of-condition-editor.component.scss"]
})
export class OneOfConditionEditor implements OnInit, OnChanges {
   public XSchema = XSchema;
   public ConditionValueType = ConditionValueType;
   @Input() dataFunction: () => Observable<BrowseDataModel>;
   @Input() vsId: string;
   @Input() variablesFunction: () => Observable<any[]>;
   @Input() columnTreeFunction: (value: ExpressionValue) => Observable<TreeNodeModel>;
   @Input() scriptDefinitionsFunction: (value: ExpressionValue) => Observable<TreeNodeModel>;
   @Input() expressionTypes: ExpressionType[];
   @Input() valueTypes: ConditionValueType[];
   @Input() subqueryTables: SubqueryTable[];
   @Input() fieldsModel: ConditionFieldComboModel;
   @Input() grayedOutFields: DataRef[];
   @Input() field: DataRef;
   @Input() table: string;
   @Input() showUseList: boolean;
   @Input() values: ConditionValue[];
   @Input() source: SourceInfo;
   @Input() isVSContext = true;
   @Input() enableBrowseData: boolean = true;
   @Input() showOriginalName: boolean = false;
   @Output() valuesChange: EventEmitter<ConditionValue[]> = new EventEmitter<ConditionValue[]>();
   value: ConditionValue;
   selectValues: ConditionValue[] = [];
   selectedIndex: number;

   ngOnInit() {
      this.initValue();
   }

   ngOnChanges(changes: SimpleChanges) {
      if(changes["values"]) {
         this.initValue();
      }
   }

   private initValue() {
      if(this.values[0] && (this.isSpecialType(this.values[0].type) ||
         this.values[0].type === ConditionValueType.EXPRESSION))
      {
         this.value = <ConditionValue> {
            value: Tool.clone(this.values[0].value),
            type: this.values[0].type,
            choiceQuery: this.values[0].choiceQuery
         };
      }
      else if(this.value == null || (this.value.type != ConditionValueType.VALUE && this.values[0])) {
         // set the type of the current editor to the last value that was added
         this.value = {
            value: this.getDefaultValue(),
            type: ConditionValueType.VALUE,
            choiceQuery: this.values && this.values.length > 0 ?
               this.values[this.values.length - 1].choiceQuery : null
         };
      }
   }

   private getDefaultValue(): any {
      if(this.value?.type == ConditionValueType.EXPRESSION) {
         return null;
      }

      return XSchema.isStringType(this.field?.dataType) ? "" : null;
   }

   isSelected(val: ConditionValue): boolean {
      return this.selectValues.some(v => Tool.isEquals(v.value, val.value));
   }

   selectValue(i: number, event: MouseEvent) {
      this.selectedIndex = i;
      let oldValues: ConditionValue[] = Tool.clone(this.values);
      this.value = Tool.clone(oldValues[this.selectedIndex]);

      if(this.selectValues.length == 0) {
         this.selectValues.push(oldValues[this.selectedIndex]);
      }

      if(event.ctrlKey) {
         this.selectValues.push(oldValues[this.selectedIndex]);
      }
      else if(event.shiftKey) {
         let index = this.values.findIndex((v) => {
            return this.selectValues[this.selectValues.length - 1].value == v.value;
         });

         this.selectValues =
            this.selectValues.concat(this.values.slice(index + 1, this.selectedIndex + 1));
      }
      else {
         this.selectValues = [Tool.clone(this.values)[this.selectedIndex]];
      }
   }

   add(): void {
      if(this.value != null) {
         let exists: boolean = false;

         // field should be only one value
         /*
         if(this.values.find(v => v.type == ConditionValueType.FIELD)) {
            this.values = [];
         }
         */
         // don't allow mixed type
         this.values = this.values.filter(v => v.value.classType == this.value.value.classType);

         for(let i = 0; i < this.values.length; i++) {
            if(Tool.isEquals(this.value, this.values[i])) {
               exists = true;
               break;
            }
         }

         if(!exists) {
            this.selectedIndex = this.values.push(this.value) - 1;
            this.selectValues = [];
            this.selectValues.push(this.value);
            this.valuesChange.emit(this.values);
         }

         if(XSchema.isDateType(this.field.dataType)) {
            this.value = Tool.clone(this.value);
         }
         else {
            this.value = {
               value: this.getDefaultValue(),
               type: this.value.type,
               choiceQuery: this.value.choiceQuery
            };
         }
      }
   }

   remove(): void {
      if(this.selectedIndex != null) {
         this.selectValues.forEach((s) => {
            let index = this.values.findIndex((v) => {
               return Tool.isEquals(s.value, v.value);
            });

            if(index != -1) {
               this.values.splice(index, 1);
            }
         });

         this.valuesChange.emit(this.values);
         this.selectedIndex = this.values.length > 0 ? 0 : -1;
         this.selectValues = this.values.length > 0 ? [this.values[0]] : [];
         this.value = this.values.length > 0 ? Tool.clone(this.values[0]) : this.value;

         if(this.values.length == 0) {
            this.value.value = this.getDefaultValue();
         }
      }
   }

   modify(): void {
      if(this.selectedIndex != null) {
         let exists: boolean = this.values.filter(v => v.value == this.value.value).length > 0;

         if(exists) {
            return;
         }

         this.values[this.selectedIndex] = Tool.clone(this.value);
         this.valuesChange.emit(this.values);
      }
   }

   valueChanged(): void {
      let isSpecialType: boolean = this.isSpecialType(this.value.type);

      // if the editor type is special type then propagate the value change
      if(isSpecialType) {
         this.values = [this.value];
         this.valuesChange.emit(this.values);
      }
      // if switching from a special type to some other then clear the values
      else if(!isSpecialType && this.values[0] &&
         this.isSpecialType(this.values[0].type))
      {
         this.values = [];
         this.valuesChange.emit(this.values);
      }
   }

   valueChanges(event: any): void {
      this.values = event;

      if(this.values.length == 0) {
         this.value.value = null;
         this.selectedIndex = -1;
      }

      this.valuesChange.emit(this.values);
   }

   isSpecialType(type: ConditionValueType): boolean {
      return type === ConditionValueType.VARIABLE ||
         type === ConditionValueType.SUBQUERY ||
         type === ConditionValueType.SESSION_DATA;
   }

   isValueListVisible(type: ConditionValueType): boolean {
      return type === ConditionValueType.VALUE ||
         type === ConditionValueType.EXPRESSION ||
         type === ConditionValueType.FIELD;
   }

   isEmpty(conditionValue: ConditionValue): boolean {
      let value = conditionValue.value;

      if(value == null) {
         return true;
      }

      if(conditionValue.type == ConditionValueType.EXPRESSION) {
         return value.expression == null || Tool.isEmpty(value.expression + "");
      }

      return Tool.isEmpty(value + "");
   }

   /*
   isFieldSelected(): boolean {
      return this.selectedIndex >= 0 &&
         this.values[this.selectedIndex].type == ConditionValueType.FIELD;
   }
   */
}
