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
import { HttpClient } from "@angular/common/http";
import { Condition } from "../../../common/data/condition/condition";
import { ConditionOperation } from "../../../common/data/condition/condition-operation";
import { ConditionValueType } from "../../../common/data/condition/condition-value-type";
import { ExpressionType } from "../../../common/data/condition/expression-type";
import { XSchema } from "../../../common/data/xschema";
import { AssemblyConditionItemPaneProvider } from "./assembly-condition-item-pane-provider";

export class SimpleConditionItemPaneProvider extends AssemblyConditionItemPaneProvider {
   constructor(http: HttpClient, runtimeId: string, assemblyName: string) {
      super(http, runtimeId, assemblyName);
   }

   getConditionOperations(condition: Condition): ConditionOperation[] {
      let operations: ConditionOperation[] = [];

      if(condition != null) {
         if(condition.field != null) {
            if(condition.field.dataType == XSchema.BOOLEAN) {
               operations.push(ConditionOperation.EQUAL_TO);
               operations.push(ConditionOperation.NULL);
            }
            else if(condition.field.dataType == XSchema.DATE ||
               condition.field.dataType == XSchema.TIME_INSTANT)
            {
               operations.push(ConditionOperation.EQUAL_TO);
               operations.push(ConditionOperation.DATE_IN);
               operations.push(ConditionOperation.ONE_OF);
               operations.push(ConditionOperation.LESS_THAN);
               operations.push(ConditionOperation.GREATER_THAN);
               operations.push(ConditionOperation.BETWEEN);
               operations.push(ConditionOperation.NULL);
               operations.push(ConditionOperation.TOP_N);
               operations.push(ConditionOperation.BOTTOM_N);
            }
            else if(condition.field.dataType == XSchema.TIME) {
               operations.push(ConditionOperation.EQUAL_TO);
               operations.push(ConditionOperation.ONE_OF);
               operations.push(ConditionOperation.LESS_THAN);
               operations.push(ConditionOperation.GREATER_THAN);
               operations.push(ConditionOperation.BETWEEN);
               operations.push(ConditionOperation.NULL);
               operations.push(ConditionOperation.TOP_N);
               operations.push(ConditionOperation.BOTTOM_N);
            }
         }

         if(condition.values[0] != null &&
            condition.values[0].type == ConditionValueType.SESSION_DATA)
         {
            if(condition.values[0].value == "$(_ROLES_)" ||
               condition.values[0].value == "$(_GROUPS_)")
            {
               operations.push(ConditionOperation.ONE_OF);
            }
            else {
               operations.push(ConditionOperation.EQUAL_TO);
            }
         }
      }

      if(operations.length == 0) {
         operations.push(ConditionOperation.EQUAL_TO);
         operations.push(ConditionOperation.ONE_OF);
         operations.push(ConditionOperation.LESS_THAN);
         operations.push(ConditionOperation.GREATER_THAN);
         operations.push(ConditionOperation.BETWEEN);
         operations.push(ConditionOperation.STARTING_WITH);
         operations.push(ConditionOperation.CONTAINS);
         operations.push(ConditionOperation.LIKE);
         operations.push(ConditionOperation.NULL);
         operations.push(ConditionOperation.TOP_N);
         operations.push(ConditionOperation.BOTTOM_N);
      }

      return operations;
   }

   getConditionValueTypes(condition: Condition): ConditionValueType[] {
      let valueTypes: ConditionValueType[] = [];

      if(condition != null) {
         if(condition.operation == ConditionOperation.TOP_N ||
            condition.operation == ConditionOperation.BOTTOM_N)
         {
            valueTypes.push(ConditionValueType.VALUE);
            valueTypes.push(ConditionValueType.VARIABLE);
         }
         else if(condition.operation == ConditionOperation.BETWEEN) {
            valueTypes.push(ConditionValueType.VALUE);
            valueTypes.push(ConditionValueType.VARIABLE);
            valueTypes.push(ConditionValueType.FIELD);
            valueTypes.push(ConditionValueType.EXPRESSION);
         }
         else if(condition.operation == ConditionOperation.STARTING_WITH ||
            condition.operation == ConditionOperation.CONTAINS ||
            condition.operation == ConditionOperation.LIKE)
         {
            valueTypes.push(ConditionValueType.VALUE);
            valueTypes.push(ConditionValueType.VARIABLE);
            valueTypes.push(ConditionValueType.EXPRESSION);
         }
         else {
            valueTypes.push(ConditionValueType.VALUE);
            valueTypes.push(ConditionValueType.VARIABLE);
            valueTypes.push(ConditionValueType.EXPRESSION);

            if(condition.operation != ConditionOperation.DATE_IN) {
               valueTypes.push(ConditionValueType.SUBQUERY);
               valueTypes.push(ConditionValueType.FIELD);
            }
         }

         if((condition.field == null || condition.field.dataType == XSchema.STRING) &&
            (condition.operation == ConditionOperation.EQUAL_TO ||
               condition.operation == ConditionOperation.ONE_OF))
         {
            valueTypes.push(ConditionValueType.SESSION_DATA);
         }
      }

      return valueTypes;
   }

   getExpressionTypes(condition: Condition): ExpressionType[] {
      return [ExpressionType.SQL, ExpressionType.JS];
   }

   isNegationAllowed(condition: Condition): boolean {
      return !(condition != null && (condition.operation == ConditionOperation.TOP_N ||
         condition.operation == ConditionOperation.BOTTOM_N));
   }
}
