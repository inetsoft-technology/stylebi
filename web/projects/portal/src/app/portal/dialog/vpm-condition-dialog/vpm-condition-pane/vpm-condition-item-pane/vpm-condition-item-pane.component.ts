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
   OnInit,
   Output,
   SimpleChange
} from "@angular/core";
import { ClauseModel } from "../../../../data/model/datasources/database/vpm/condition/clause/clause-model";
import { ClauseValueModel } from "../../../../data/model/datasources/database/vpm/condition/clause/clause-value-model";
import { DataConditionItemPaneProvider } from "../../../../data/model/datasources/database/vpm/condition/clause/data-condition-item-pane-provider";
import { VPMColumnModel } from "../../../../data/model/datasources/database/vpm/condition/vpm-column-model";
import { OperationModel } from "../../../../data/model/datasources/database/vpm/condition/clause/operation-model";
import { ClauseOperationSymbols } from "../../../../data/model/datasources/database/vpm/condition/clause/clause-operation-symbols";
import { Observable } from "rxjs";
import { ClauseValueTypes } from "../../../../data/model/datasources/database/vpm/condition/clause/clause-value-types";
import { ConditionOperation } from "../../../../../common/data/condition/condition-operation";
import { Tool } from "../../../../../../../../shared/util/tool";
import { XSchema } from "../../../../../common/data/xschema";

@Component({
   selector: "vpm-condition-item-pane",
   templateUrl: "vpm-condition-item-pane.component.html",
   styleUrls: ["vpm-condition-item-pane.component.scss"]
})
export class VPMConditionItemPane implements OnInit, OnChanges {
   @Input() condition: ClauseModel;
   @Input() provider: DataConditionItemPaneProvider;
   @Input() fields: VPMColumnModel[] = [];
   @Input() datasource: string;
   @Output() conditionChange: EventEmitter<ClauseModel> = new EventEmitter<ClauseModel>();
   private oldCondition: ClauseModel;
   public ConditionOperation = ConditionOperation;
   operations: OperationModel[];
   valueTypes1: string[];
   valueTypes2: string[];
   valueTypes3: string[];

   dataFunction1 = () => {
      return this.getData(1);
   };
   dataFunction2 = () => {
      return this.getData(2);
   };
   dataFunction3 = () => {
      return this.getData(3);
   };
   ClauseOperationSymbols = ClauseOperationSymbols;

   ngOnInit(): void {
      this.updateCondition();
   }

   ngOnChanges(changes: { [propertyName: string]: SimpleChange }): void {
      if(changes.hasOwnProperty("condition") || changes.hasOwnProperty("fields")) {
         if(!!this.condition) {
            this.refreshField(this.condition.value1);
            this.refreshField(this.condition.value2);
            this.refreshField(this.condition.value3);
         }
      }

      this.updateCondition();
   }

   // find field by expression.
   refreshField(value: ClauseValueModel): void {
      if(!!value && value.type == ClauseValueTypes.FIELD && !value.field &&
         !!value.expression && !!this.fields)
      {
         const matchingRef = this.fields.find((field) => {
            return field.name === value.expression;
         });

         if(!!matchingRef) {
            value.field = matchingRef;
         }
      }
   }

   supportEqualsOperation(): boolean {
      let symbol = this.condition.operation?.symbol;
      return symbol == ">" || symbol == "<" || symbol == "<=" || symbol == ">=";
   }

   get equalsOperation(): boolean {
      let symbol = this.condition.operation?.symbol;
      return symbol == "<=" || symbol == ">=";
   }

   equalsChanged(val: boolean): void {
      if(!!val) {
         if(this.condition.operation?.symbol == ">") {
            this.condition.operation.symbol = ">=";
         }

         if(this.condition.operation?.symbol == "<") {
            this.condition.operation.symbol = "<=";
         }
      }
      else {
         if(this.condition.operation?.symbol == ">=") {
            this.condition.operation.symbol = ">";
         }

         if(this.condition.operation?.symbol == "<=") {
            this.condition.operation.symbol = "<";
         }
      }

      this.conditionChange.emit(this.condition);
   }

   trinaryConditionChanged(values: ClauseValueModel[]): void {
      if(!!values && values.length == 2) {
         this.condition.value2 = values[0];
         this.condition.value3 = values[1];
         this.changeConditionValue();
      }
   }

   changeField(val: ClauseValueModel): void {
      if(!Tool.isEquals(val, this.condition.value1) && this.operations?.length > 0) {
         this.condition.operation = this.operations[0];
      }

      this.condition.value1 = val;
      this.updateCondition(true);

      if(!!this.condition.value2) {
         this.resetCondition(this.valueTypes2, this.condition.value2);
      }

      if(!!this.condition.value3) {
         this.resetCondition(this.valueTypes3, this.condition.value3);
      }

      this.conditionChange.emit(this.condition);
   }

   changeConditionValue(): void {
      if(!!this.condition.value1 && this.condition.value1.type == ClauseValueTypes.VALUE) {
         this.updateCondition(true);
      }
      else {
         this.updateCondition();
      }

      this.resetCondition(this.valueTypes1, this.condition.value1);
      this.conditionChange.emit(this.condition);
   }

   /**
    * Called when condition is changed. Refresh the current operations and valuetypes.
    */
   updateCondition(fieldChanged: boolean = false): void {
      this.operations = this.provider.getConditionOperations(this.condition);

      if(this.condition != null && this.operations.length > 0) {
         let operation: OperationModel = this.operations
            .find(op => op.symbol == this.getOperationSymbol(this.condition.operation.symbol));

         if(!!operation) {
            operation = Tool.clone(operation);
            operation.symbol = this.condition.operation.symbol;
         }

         this.condition.operation = !!operation ? operation : this.operations[0];
      }

      this.valueTypes1 = this.provider.getConditionValueTypes(this.condition, 1);
      this.valueTypes2 = this.provider.getConditionValueTypes(this.condition, 2);
      this.valueTypes3 = this.provider.getConditionValueTypes(this.condition, 3);

      if(fieldChanged) {
         this.updateConditionValue();
      }

      this.oldCondition = Tool.clone(this.condition);
   }

   // clear Value type expression if field changed.
   private updateConditionValue(): void {
      if(!this.condition || !this.oldCondition) {
         return;
      }

      if(!!this.condition.value1?.field && !Tool.isEquals(this.condition.value1?.field,
         this.oldCondition.value1?.field))
      {
         if(!!this.condition.value2 && this.condition.value2.type == ClauseValueTypes.VALUE) {
            this.condition.value2.expression = null;
         }

         if(!!this.condition.value3 && this.condition.value3.type == ClauseValueTypes.VALUE) {
            this.condition.value3.expression = null;
         }
      }
      else if(!!this.condition.value2?.field && !Tool.isEquals(this.condition.value2?.field,
         this.oldCondition.value2?.field))
      {
         if(!!this.condition.value1 && this.condition.value1.type == ClauseValueTypes.VALUE) {
            this.condition.value1.expression = null;
         }
      }
   }

   resetCondition(valueTypes: string[], valueModel: ClauseValueModel) {
      let find = !!valueTypes && valueTypes.some((type) => type == ClauseValueTypes.SESSION_DATA);

      if(valueModel.type == ClauseValueTypes.SESSION_DATA && !find) {
         valueModel.type = ClauseValueTypes.VALUE;
         valueModel.expression = undefined;
         valueModel.field = null;
      }
   }

   /**
    * Check if current operation is a unary expression.
    * @returns {boolean}   true if it is a unary operator
    */
   get isUnaryOperation(): boolean {
      return this.isUnaryOperationLogic(this.condition.operation);
   }

   isUnaryOperationLogic(operation: OperationModel): boolean {
      const symbol: string = operation.symbol;
      return symbol == ClauseOperationSymbols.EXISTS || symbol == ClauseOperationSymbols.UNIQUE ||
         symbol == ClauseOperationSymbols.IS_NULL;
   }

   /**
    * Get the browse data for the value at the given position.
    * @param position   the position of the value to get browse data for
    * @returns {Observable<string[]>}  observable containing the browse data
    */
   getData(position: number): Observable<string[]> {
      return this.provider.getData(this.condition, position);
   }

   /**
    * Check if browse data is enabled for the value at the given position.
    * @param position   the position of the value to check
    * @returns {boolean}   true if browse data is enabled
    */
   isBrowseDataEnabled(position: number): boolean {
      return this.provider.isBrowseDataEnabled(this.condition, position);
   }

   /**
    * Get the value field type for the value at the given position.
    * @param position   the position of the value to get the value field type for.
    * @returns {string} the value field type
    */
   getValueFieldType(position: number): string {
      return this.provider.getValueFieldType(this.condition, position);
   }

   get showDefDateValue(): boolean {
      if(!this.condition) {
         return false;
      }

      return (!!this.condition.value1 && this.isDateType(this.condition.value1.field)) ||
         (!!this.condition.value1 && this.isDateType(this.condition.value2.field)) ||
         (!!this.condition.value3 && this.isDateType(this.condition.value3.field));
   }

   get optSymbol() {
      let opt: OperationModel = this.condition.operation;
      return this.getOperationSymbol(opt.symbol);
   }

   set optSymbol(symbol: string) {
      this.condition.operation = Tool.clone(this.operations.find((opt) => opt.symbol == symbol));
   }

   getOperationSymbol(symbol: string) {
      return symbol == ">=" ? ">" : symbol == "<=" ? "<" : symbol;
   }

   optionChange(event: any) {
      if(event && this.isUnaryOperationLogic(event)) {
         this.condition.value2.expression = undefined;
      }

      this.updateExpression();
      this.updateCondition();
      this.conditionChange.emit(this.condition);
   }

   /**
    * Whether filed is date type.
    * @param field
    */
   private isDateType(field: VPMColumnModel): boolean {
      if(!field) {
         return false;
      }

      return field.type == "timeInstant" || field.type == "date" || field.type == "time";
   }

   get leftOneOf(): boolean {
      return this.condition?.operation?.symbol == ClauseOperationSymbols.IN &&
         this.condition.value1?.type == ClauseValueTypes.VALUE;
   }

   private updateExpression() {
      if(this.condition.value1.type == ClauseValueTypes.VALUE) {
         this.condition.value1.expression = this.getExpressionValue(this.condition.value1.expression);
      }

      if(this.condition.value2.type == ClauseValueTypes.VALUE) {
         this.condition.value2.expression = this.getExpressionValue(this.condition.value2.expression);
      }

      if(this.condition.value3.type == ClauseValueTypes.VALUE) {
         this.condition.value3.expression = this.getExpressionValue(this.condition.value3.expression);
      }
   }

   private getExpressionValue(expression: string): string {
      if(!expression) {
         return null;
      }

      if(this.condition?.operation?.symbol == ClauseOperationSymbols.IN
         && !expression?.startsWith("("))
      {
         expression = "(" + expression + ")";
      }
      else if(this.condition?.operation?.symbol != ClauseOperationSymbols.IN
         && expression?.startsWith("("))
      {
         expression = expression.substring(1, expression.length - 1);
         expression = expression.split(",").length > 0 ?
            expression.split(",")[0] : expression;
      }

      return expression;
   }
}
