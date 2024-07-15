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
import {
   Component,
   EventEmitter,
   Input,
   OnChanges,
   Output,
   SimpleChanges,
   ViewChild
} from "@angular/core";
import { Observable } from "rxjs";
import { Tool } from "../../../../../shared/util/tool";
import { SourceInfo } from "../../binding/data/source-info";
import { BrowseDataModel } from "../../common/data/browse-data-model";
import { ConditionOperation } from "../../common/data/condition/condition-operation";
import { ConditionValue } from "../../common/data/condition/condition-value";
import { ConditionValueType } from "../../common/data/condition/condition-value-type";
import { ExpressionType } from "../../common/data/condition/expression-type";
import { ExpressionValue } from "../../common/data/condition/expression-value";
import { SubqueryTable } from "../../common/data/condition/subquery-table";
import { SubqueryValue } from "../../common/data/condition/subquery-value";
import { DataRef } from "../../common/data/data-ref";
import { XSchema } from "../../common/data/xschema";
import { TreeNodeModel } from "../tree/tree-node-model";
import { ConditionFieldComboModel } from "./condition-field-combo-model";
import { FixedDropdownDirective } from "../fixed-dropdown/fixed-dropdown.directive";
import { ValueEditor } from "./value-editor.component";

@Component({
   selector: "condition-editor",
   templateUrl: "condition-editor.component.html",
   styleUrls: ["condition-editor.component.scss"]
})
export class ConditionEditor implements OnChanges {
   public XSchema = XSchema;
   public ConditionValueType = ConditionValueType;
   public ConditionOperation = ConditionOperation;
   @Input() operation: ConditionOperation;
   @Input() source: SourceInfo;
   @Input() vsId: string;
   @Input() showUseList: boolean;
   @Input() dataFunction: () => Observable<BrowseDataModel>;
   @Input() variablesFunction: () => Observable<any[]>;
   @Input() columnTreeFunction: (value: ExpressionValue) => Observable<TreeNodeModel>;
   @Input() scriptDefinitionsFunction: (value: ExpressionValue) => Observable<any>;
   @Input() expressionTypes: ExpressionType[];
   @Input() valueTypes: ConditionValueType[];
   @Input() subqueryTables: SubqueryTable[];
   @Input() fieldsModel: ConditionFieldComboModel;
   @Input() grayedOutFields: DataRef[];
   @Input() field: DataRef;
   @Input() table: string;
   @Input() value: ConditionValue;
   @Input() values: ConditionValue[] = [];
   @Input() isOneOf: boolean = false;
   @Input() enableBrowseData: boolean = true;
   @Input() isVSContext = true;
   @Input() isHighlight: boolean = false;
   @Input() showOriginalName: boolean = false;
   @Output() valueChange = new EventEmitter<ConditionValue>();
   @Output() valueChanges = new EventEmitter<ConditionValue[]>();
   @Output() addValue = new EventEmitter<any>();
   @ViewChild(FixedDropdownDirective) fieldsDropdown: FixedDropdownDirective;
   @ViewChild(ValueEditor) valueEditor: ValueEditor;

   getSelectValues(): any {
      return this.values.length == 0 ? [] : this.values.map(v => v.value);
   }

   ngOnChanges(changes: SimpleChanges): void {
      if(changes["field"]) {
         if(this.value && this.value.type == ConditionValueType.SESSION_DATA &&
            this.field && this.field.dataType != "string")
         {
            this.value = null;
         }
      }

      if(this.value == null) {
         this.value = <ConditionValue> {
            value: "",
            type: this.valueTypes[0],
            choiceQuery: null
         };
      }
   }

   openChange(open: boolean) {
      if(this.valueEditor) {
         this.valueEditor.hideBrowse();
      }
   }

   selectType(type: ConditionValueType): void {
      if(type != this.value.type) {
         let value = null;

         if(type === ConditionValueType.FIELD && !!this.fieldsModel.list &&
            !!this.fieldsModel.list[0])
         {
            value = this.fieldsModel.list[0];
         }
         else if(type === ConditionValueType.VARIABLE) {
            value = "$()";
         }
         else if(this.expressionTypes != null && type === ConditionValueType.EXPRESSION) {
            value = <ExpressionValue> {
               expression: null,
               type: this.expressionTypes[0]
            };
         }
         else if(type === ConditionValueType.SUBQUERY) {
            value = <SubqueryValue> {
               query: "",
               attribute: null,
               subAttribute: null,
               mainAttribute: null
            };
         }

         this.value = <ConditionValue> {
            value: value,
            type: type,
            choiceQuery: null
         };

         this.valueChanged();
      }
   }

   valueChanged(): void {
      this.valueChange.emit(this.value);
   }

   conditionValueChanged(val: any) {
      if(!this.value.value && (val === null || typeof val === "undefined" || val === "")) {
         return;
      }

      this.value.value = val;
      this.valueChanged();
   }

   conditionValuesChanged(val: any[]) {
      this.values = val.map(v => {
         const value = this.values.find(oldValue => {
            const innerValue = oldValue.value;
            return innerValue == v || innerValue != null && innerValue.value === v;
         });

         return <ConditionValue>{
            value: v,
            type: value != null ? value.type : ConditionValueType.VALUE,
            choiceQuery: null
         };
      });

      this.valueChanges.emit(this.values);
   }

   updateChoiceQuery(useList: boolean): void {
      if(useList && this.source && this.source.type != 6) {
         this.value.choiceQuery = this.getChoiceQuery();
      }
      else if(useList && this.source == null) {
         const fieldname = this.field.name;

         if(fieldname.indexOf("]") < 0 && this.table) {
            this.value.choiceQuery = `${this.table}]:[${this.field.name}`;
         }
         else {
            this.value.choiceQuery = this.field.name;
         }
      }
      else {
         this.value.choiceQuery = null;
      }

      this.valueChanged();
   }

   private getChoiceQuery(): string {
      let source: SourceInfo = Tool.clone(this.source);
      let field: DataRef = Tool.clone(this.field);

      if(source != null && field.name != null) {
         if(source.type === SourceInfo.MODEL) {
            let query = source.source + "::" + source.prefix;
            let column = field.entity + "::" + field.attribute;

            return "[" + query + "].[" + column + "]";
         }
         else if(source.type === SourceInfo.ASSET) {
            return "[" + source.source + "]^[" + field.name + "]";
         }
         else {
            return "[" + source.source + "].[" + field.name + "]";
         }
      }

      return "";
   }

   closeDropDown() {
      this.fieldsDropdown.close();
   }
}
