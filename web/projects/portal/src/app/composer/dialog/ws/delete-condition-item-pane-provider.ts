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
import { HttpClient } from "@angular/common/http";
import { Condition } from "../../../common/data/condition/condition";
import { ConditionOperation } from "../../../common/data/condition/condition-operation";
import { ConditionValueType } from "../../../common/data/condition/condition-value-type";
import { ExpressionType } from "../../../common/data/condition/expression-type";
import { XSchema } from "../../../common/data/xschema";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";
import { AssemblyConditionItemPaneProvider } from "./assembly-condition-item-pane-provider";

export class DeleteConditionItemPaneProvider extends AssemblyConditionItemPaneProvider {
   constructor(http: HttpClient, runtimeId: string, assemblyName: string) {
      super(http, runtimeId, assemblyName);

      this.scriptDefinitions = JSON.parse(`{
               "MV": {
                  "LastUpdateTime": {},
                  "MaxValue": {},
                  "MinValue": {}
               }
            }`);
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
            }
            else if(condition.field.dataType == XSchema.TIME) {
               operations.push(ConditionOperation.EQUAL_TO);
               operations.push(ConditionOperation.ONE_OF);
               operations.push(ConditionOperation.LESS_THAN);
               operations.push(ConditionOperation.GREATER_THAN);
               operations.push(ConditionOperation.BETWEEN);
               operations.push(ConditionOperation.NULL);
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
      }

      return operations;
   }

   getConditionValueTypes(condition: Condition): ConditionValueType[] {
      let valueTypes: ConditionValueType[] = [];

      if(condition != null) {
         valueTypes.push(ConditionValueType.VALUE);
         valueTypes.push(ConditionValueType.EXPRESSION);
      }

      return valueTypes;
   }

   getExpressionTypes(condition: Condition): ExpressionType[] {
      return [ExpressionType.JS];
   }

   isNegationAllowed(condition: Condition): boolean {
      return true;
   }

   protected getFieldTreeModel(): TreeNodeModel {
      return <TreeNodeModel> {
         label: "_#(js:MV)",
         data: "MV",
         leaf: false,
         children: [
            <TreeNodeModel> {
               label: "_#(js:LastUpdateTime)",
               data: "MV.LastUpdateTime",
               leaf: true
            },
            <TreeNodeModel> {
               label: "_#(js:MaxValue)",
               data: "MV.MaxValue",
               leaf: true
            },
            <TreeNodeModel> {
               label: "_#(js:MinValue)",
               data: "MV.MinValue",
               leaf: true
            }
         ]
      };
   }

   protected getVariableTreeModel(): TreeNodeModel {
      return null;
   }
}
