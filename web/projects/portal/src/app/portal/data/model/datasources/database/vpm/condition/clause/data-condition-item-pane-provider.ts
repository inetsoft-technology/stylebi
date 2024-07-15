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
import { ClauseModel } from "./clause-model";
import { OperationModel } from "./operation-model";
import { HttpClient, HttpParams } from "@angular/common/http";
import { ClauseValueTypes } from "./clause-value-types";
import { XSchema } from "../../../../../../../../common/data/xschema";
import { ClauseOperationSymbols } from "./clause-operation-symbols";
import { ClauseValueModel } from "./clause-value-model";
import { VPMBrowserDataCommand } from "../vpm-browser-data-command";
import { Observable, of } from "rxjs";

const VPM_BROWSER_DATA_URI: string = "../api/data/vpm/browserData";
const QUERY_COLUMN_BROWSE_URI: string = "../api/data/datasource/query/condition/field/browserData";

/**
 * DataConditionItemPaneProvider
 *
 * Provides the necessary data for the VPMConditionItemPane component.
 *
 */
export class DataConditionItemPaneProvider {
   constructor(private httpClient: HttpClient,
               private databaseName: string,
               private partitionName: string,
               private operations: OperationModel[],
               private sessionOperations: OperationModel[],
               private runtimeId?: string,
               private wsQuery?: boolean,
               private subQuery?: boolean)
   {
   }

   get datasource(): string {
      return this.databaseName;
   }

   get isWSQuery(): boolean {
      return this.wsQuery;
   }

   /**
    * Gets the available condition operations
    */
   getConditionOperations(condition: ClauseModel): OperationModel[] {
      if(condition.value1.type == ClauseValueTypes.SESSION_DATA ||
         condition.value2.type == ClauseValueTypes.SESSION_DATA)
      {
         if(condition.value2.expression == "$(_USER_)" ||
            condition.value1.expression == "$(_USER_)" && this.wsQuery)
         {
            return [this.sessionOperations[0]];
         }
         else if(condition.value1.expression == "$(_USER_)" &&
            condition.value2.type != ClauseValueTypes.SESSION_DATA ||
            condition.value2.expression == "$(_USER_)" &&
            condition.value1.type != ClauseValueTypes.SESSION_DATA)
         {
            return this.sessionOperations;
         }
         else {
            return [this.sessionOperations[1]];
         }
      }
      else {
         return this.operations;
      }
   }

   /**
    * Gets the available condition value types
    */
   getConditionValueTypes(condition: ClauseModel, valuePosition: number): string[] {
      let valueTypes: string[] = [
         ClauseValueTypes.FIELD, ClauseValueTypes.EXPRESSION, ClauseValueTypes.VALUE,
         ClauseValueTypes.VARIABLE
      ];

      if(!this.subQuery) {
         valueTypes.push(ClauseValueTypes.SUBQUERY);
      }

      const firstValue: ClauseValueModel = valuePosition == 1 ? condition.value2 : condition.value1;
      const value3: ClauseValueModel = condition.value3;

      if(valuePosition != 3 &&
         ((firstValue.type == ClauseValueTypes.FIELD && !!firstValue.field &&
            firstValue.field.type == XSchema.STRING) ||
         (condition.operation.symbol == ClauseOperationSymbols.BETWEEN &&
            value3.type == ClauseValueTypes.FIELD && !!value3.field &&
            value3.field.type == XSchema.STRING) ||
         (firstValue.type != ClauseValueTypes.FIELD && value3.type != ClauseValueTypes.FIELD)))
      {
         if(this.isWSQuery) {
            if(condition.operation.symbol == ClauseOperationSymbols.EQUAL_TO ||
               condition.operation.symbol == ClauseOperationSymbols.IN)
            {
               valueTypes.push(ClauseValueTypes.SESSION_DATA);
            }
         }
         else {
            valueTypes.push(ClauseValueTypes.SESSION_DATA);
         }
      }

      return valueTypes;
   }

   /**
    * Gets the value field type that the condition editor should be editing, string for default.
    */
   getValueFieldType(condition: ClauseModel, valuePosition: number): string {
      const valueArray: ClauseValueModel[] = this.getOtherValuesArray(condition, valuePosition);
      const clauseValue: ClauseValueModel =
         valueArray.find(value => value.type == ClauseValueTypes.FIELD && !!value.field);

      if(!!clauseValue && clauseValue.field.type != null) {
         return clauseValue.field.type;
      }
      else {
         return XSchema.STRING;
      }
   }

   /**
    * Fetches the available data for the selected field
    */
   getData(condition: ClauseModel, valuePosition: number): Observable<string[]> {
      let conditionValue: ClauseValueModel;

      if(valuePosition == 1) {
         conditionValue = condition.value2.type == ClauseValueTypes.FIELD ? condition.value2 :
            condition.value3;
      }
      else if(valuePosition == 2) {
         conditionValue = condition.value1.type == ClauseValueTypes.FIELD ? condition.value1 :
            condition.value3;
      }
      else {
         conditionValue = condition.value1.type == ClauseValueTypes.FIELD ? condition.value1 :
            condition.value2;
      }

      if(!!conditionValue.field) {
         if(!this.isWSQuery) {
            return this.getVPMColumnData(conditionValue);
         }
         else {
            return this.getQueryColumnData(conditionValue);
         }
      }
      else {
         return of([]);
      }
   }

   getVPMColumnData(conditionValue: ClauseValueModel): Observable<string[]> {
      const command: VPMBrowserDataCommand = new VPMBrowserDataCommand(this.databaseName,
         this.partitionName, conditionValue.field.tableName, conditionValue.field.columnName,
         conditionValue.field.type);
      return this.httpClient.post<string[]>(VPM_BROWSER_DATA_URI, command);
   }

   getQueryColumnData(conditionValue: ClauseValueModel): Observable<string[]> {
      let tableName = conditionValue.field.physicalTableName;
      tableName = tableName ? tableName : conditionValue.field.tableName;

      // If table name is null, that means the column is an expression.
      if(!!tableName) {
         const params = new HttpParams()
            .set("runtimeId", this.runtimeId)
            .set("tableName", tableName)
            .set("columnName", conditionValue.field.columnName)
            .set("columnType", conditionValue.field.type);
         return this.httpClient.get<string[]>(QUERY_COLUMN_BROWSE_URI, {params: params});
      }
      else {
         return of([]);
      }
   }

   /**
    * Check if browse data is enabled for the given value position.
    * @param condition     the condition being edited
    * @param valuePosition the position of the value being checked
    * @returns {boolean}   true if browse data is enabled
    */
   isBrowseDataEnabled(condition: ClauseModel, valuePosition: number): boolean {
      const valueArray: ClauseValueModel[] = this.getOtherValuesArray(condition, valuePosition);

      // Browse data is enabled if any of the other visible values are currently type field
      return valueArray.some(value => value.type == ClauseValueTypes.FIELD);
   }

   getOtherValuesArray(condition: ClauseModel, valuePosition: number): ClauseValueModel[] {
      let valueArray: ClauseValueModel[] = [];

      if(valuePosition == 1) {
         valueArray.push(condition.value2, condition.value3);

         if(condition.operation.symbol == ClauseOperationSymbols.BETWEEN) {
            valueArray.push(condition.value3);
         }
      }
      else if(valuePosition == 2) {
         valueArray.push(condition.value1, condition.value3);

         if(condition.operation.symbol == ClauseOperationSymbols.BETWEEN) {
            valueArray.push(condition.value3);
         }
      }
      else {
         valueArray.push(condition.value1, condition.value2);
      }

      return valueArray;
   }
}
