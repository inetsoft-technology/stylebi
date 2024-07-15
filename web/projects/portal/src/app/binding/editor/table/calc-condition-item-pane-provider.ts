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
import { HttpClient, HttpHeaders, HttpParams } from "@angular/common/http";
import { Observable, of as observableOf } from "rxjs";
import { BrowseDataModel } from "../../../common/data/browse-data-model";
import { AssetConditionItemPaneProvider } from "../../../common/data/condition/asset-condition-item-pane-provider";
import { Condition } from "../../../common/data/condition/condition";
import { ConditionOperation } from "../../../common/data/condition/condition-operation";
import { ConditionValueType } from "../../../common/data/condition/condition-value-type";
import { ExpressionType } from "../../../common/data/condition/expression-type";
import { ExpressionValue } from "../../../common/data/condition/expression-value";
import { XSchema } from "../../../common/data/xschema";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";

export class CalcConditionItemPaneProvider extends AssetConditionItemPaneProvider {
   constructor(private http: HttpClient, private runtimeId: string,
               private tableName: string, private assemblyName: string,
               private variableNames: string[])
   {
      super();
      this.variableNames = this.trimVariables(variableNames);
   }

   getConditionOperations(condition: Condition): ConditionOperation[] {
      let operations: ConditionOperation[] = [];

      if(condition != null) {
         if(condition.field != null) {
            if(condition.field.dataType == XSchema.BOOLEAN) {
               operations.push(ConditionOperation.EQUAL_TO);
            }
            else if(condition.field.dataType == XSchema.DATE ||
               condition.field.dataType == XSchema.TIME_INSTANT)
            {
               operations.push(ConditionOperation.EQUAL_TO);
               operations.push(ConditionOperation.ONE_OF);
            }
            else if(condition.field.dataType == XSchema.TIME) {
               operations.push(ConditionOperation.EQUAL_TO);
               operations.push(ConditionOperation.ONE_OF);
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
      }

      return operations;
   }

   getExpressionTypes(condition: Condition): ExpressionType[] {
      return [];
   }

   isNegationAllowed(condition: Condition): boolean {
      return false;
   }

   getData(condition: Condition): Observable<BrowseDataModel> {
      const headers = new HttpHeaders({
         "Content-Type": "application/json"
      });
      const params = new HttpParams()
         .set("runtimeId", this.runtimeId)
         .set("tableName", this.tableName)
         .set("assemblyName", this.assemblyName);
      const uri = "../api/composer/vs/vs-condition-dialog/browse-data";
      return this.http.post<BrowseDataModel>(uri, condition.field, {headers, params});
   }

   getVariables(condition: Condition): Observable<any[]> {
      return observableOf(this.variableNames);
   }

   getColumnTree(value: ExpressionValue): Observable<TreeNodeModel> {
      return null;
   }

   getConditionValueTypes(condition: Condition): ConditionValueType[] {
      let valueTypes: ConditionValueType[] = [];

      if(condition != null) {
         valueTypes.push(ConditionValueType.VALUE);
         valueTypes.push(ConditionValueType.VARIABLE);

         if((condition.field == null || condition.field.dataType == XSchema.STRING) &&
            (condition.operation == ConditionOperation.EQUAL_TO ||
               condition.operation == ConditionOperation.ONE_OF))
         {
            valueTypes.push(ConditionValueType.SESSION_DATA);
         }
      }

      return valueTypes;
   }
}
