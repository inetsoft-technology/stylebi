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
import { map } from "rxjs/operators";
import { BrowseDataModel } from "../../common/data/browse-data-model";
import { AssetConditionItemPaneProvider } from "../../common/data/condition/asset-condition-item-pane-provider";
import { Condition } from "../../common/data/condition/condition";
import { ConditionOperation } from "../../common/data/condition/condition-operation";
import { ConditionValueType } from "../../common/data/condition/condition-value-type";
import { ExpressionType } from "../../common/data/condition/expression-type";
import { ExpressionValue } from "../../common/data/condition/expression-value";
import { DataRef } from "../../common/data/data-ref";
import { XSchema } from "../../common/data/xschema";
import { ScriptPaneTreeModel } from "../../widget/dialog/script-pane/script-pane-tree-model";
import { TreeNodeModel } from "../../widget/tree/tree-node-model";

export class VSConditionItemPaneProvider extends AssetConditionItemPaneProvider {
   private _grayedOutFields: DataRef[] = [];
   private DATE_ITEMS: string[] = ["_BEGINNING_OF_MONTH",
            "_END_OF_MONTH", "_BEGINNING_OF_QUARTER", "_END_OF_QUARTER",
            "_BEGINNING_OF_YEAR", "_END_OF_YEAR", "_BEGINNING_OF_WEEK",
            "_END_OF_WEEK", "_TODAY"];

   constructor(private http: HttpClient, private runtimeId: string,
               private assemblyName: string, private tableName: string,
               private variableNames: string[], private isHighlight?: boolean,
               private nonsupportBrowseFiles?: string[])
   {
      super();
      this.variableNames = this.trimVariables(variableNames);
   }

   set grayedOutFields(value: DataRef[]) {
      this._grayedOutFields = value;
   }

   getGrayedOutFields(): DataRef[] {
      return this._grayedOutFields;
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
         if(condition.operation == ConditionOperation.BETWEEN) {
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
      return [ExpressionType.JS, ExpressionType.SQL];
   }

   isNegationAllowed(condition: Condition): boolean {
      return true;
   }

   isBrowseDataEnabled(condition: Condition): boolean {
      if(this.tableName == null) {
         return false;
      }

      let fld: string = condition.field?.attribute;

      if(!!this.nonsupportBrowseFiles && this.nonsupportBrowseFiles.includes(fld)) {
         return false;
      }

      return true;
   }

   getData(condition: Condition): Observable<BrowseDataModel> {
      if(condition.operation == ConditionOperation.DATE_IN) {
         const params = new HttpParams().set("runtimeId", this.runtimeId);
         return this.http.get<BrowseDataModel>(
           "../api/composer/vs/vs-condition-dialog/date-ranges", {params});
      }
      else {
         const headers = new HttpHeaders({
            "Content-Type": "application/json"
         });

         let params = new HttpParams()
            .set("runtimeId", this.runtimeId)
            .set("tableName", this.tableName)
            .set("assemblyName", this.assemblyName);

         if(this.isHighlight) {
            params = params.set("highlight", this.isHighlight + "");
         }

         const options = { headers, params };

         return this.http.post<BrowseDataModel>("../api/composer/vs/vs-condition-dialog/browse-data",
            condition.field, options);
      }
   }

   getVariables(condition: Condition): Observable<any[]> {
      if(condition != null && condition.field != null &&
         (condition.field.dataType == XSchema.DATE ||
         condition.field.dataType == XSchema.TIME_INSTANT))
      {
         return observableOf(this.DATE_ITEMS.concat(this.variableNames));
      }

      return observableOf(this.variableNames);
   }

   getColumnTree(value: ExpressionValue): Observable<TreeNodeModel> {
      const uri: string = "../api/vsscriptable/scriptTree";
      let params = new HttpParams()
         .set("vsId", this.runtimeId)
         .set("tableName", this.tableName)
         .set("isCondition", "true");

      if(this.assemblyName) {
         params = params.set("assemblyName", this.assemblyName);
      }

      return this.http.get<ScriptPaneTreeModel>(uri, {params}).pipe(
         map(model => model.columnTree)
      );
   }

   getScriptDefinitions(value: ExpressionValue): Observable<any> {
      const uri: string = "../api/vsscriptable/scriptDefinition";
      let params = new HttpParams()
         .set("vsId", this.runtimeId)
         .set("tableName", this.tableName)
         .set("isCondition", "true");

      if(this.assemblyName) {
         params = params.set("assemblyName", this.assemblyName);
      }

      return this.http.get(uri, {params});
   }
}
