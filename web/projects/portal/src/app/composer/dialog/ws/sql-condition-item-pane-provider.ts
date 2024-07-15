/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
import { ColumnRef } from "../../../binding/data/column-ref";
import { BrowseDataModel } from "../../../common/data/browse-data-model";
import { AssetConditionItemPaneProvider } from "../../../common/data/condition/asset-condition-item-pane-provider";
import { Condition } from "../../../common/data/condition/condition";
import { ConditionOperation } from "../../../common/data/condition/condition-operation";
import { ConditionValueType } from "../../../common/data/condition/condition-value-type";
import { ExpressionType } from "../../../common/data/condition/expression-type";
import { ExpressionValue } from "../../../common/data/condition/expression-value";
import { DataRef } from "../../../common/data/data-ref";
import { XSchema } from "../../../common/data/xschema";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";
import { HttpClient, HttpHeaders, HttpParams } from "@angular/common/http";
import { Observable, of as observableOf } from "rxjs";

export class SQLConditionItemPaneProvider extends AssetConditionItemPaneProvider {
   private _variableNames: string[];
   private _fields: DataRef[];
   private _runtimeId: string;
   private _dataSource: string;
   private readonly CONTROLLER_BROWSE_DATA = "../api/composer/ws/sql-query-dialog/browse-data";

   constructor(protected http: HttpClient) {
      super();
   }

   set variableNames(variableNames: string[]) {
      this._variableNames = variableNames;
   }

   set fields(fields: DataRef[]) {
      this._fields = fields;
   }

   set runtimeid(runtimeId: string) {
      this._runtimeId = runtimeId;
   }

   set dataSource(dataSource: string) {
      this._dataSource = dataSource;
   }

   get dataSource(): string {
      return this._dataSource;
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
            valueTypes.push(ConditionValueType.FIELD);
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
      return [ExpressionType.SQL];
   }

   isNegationAllowed(condition: Condition): boolean {
      return true;
   }

   getData(condition: Condition): Observable<BrowseDataModel> {
      let ref: ColumnRef = {
         classType: "ColumnRef",
         dataRefModel: condition.field,
         visible: true,
         width: 1,
         sql: true,
         alias: null,
         valid: true,
         dataType: XSchema.STRING,
         description: ""
      };

      const headers = new HttpHeaders({
         "Content-Type": "application/json"
      });

      const params = new HttpParams()
         .set("runtimeId", this._runtimeId)
         .set("dataSource", this._dataSource);

      return this.http.post<BrowseDataModel>(this.CONTROLLER_BROWSE_DATA, ref, {headers, params});
   }

   getVariables(condition: Condition): Observable<any[]> {
      return observableOf(this._variableNames);
   }

   getColumnTree(value: ExpressionValue): Observable<TreeNodeModel> {
      return observableOf(this.getColumnTreeModel());
   }

   private getColumnTreeModel(): TreeNodeModel {
      let root: TreeNodeModel = {children: []};
      root.children.push(this.getFieldTreeModel());
      let variableTreeModel = this.getVariableTreeModel();

      if(!!variableTreeModel) {
         root.children.push(variableTreeModel);
      }

      return root;
   }

   private getFieldTreeModel(): TreeNodeModel {
      const fieldTreeNodes: TreeNodeModel[] = [];

      for(let field of this._fields) {
         fieldTreeNodes.push(<TreeNodeModel> {
            label: field.name ? field.name : field.view,
            data: field.name ? field.name : field.view,
            icon: "column-icon",
            leaf: true
         });
      }

      return <TreeNodeModel> {
         label: "Fields",
         children: fieldTreeNodes,
         icon: "data-table-icon",
         leaf: false
      };
   }

   private getVariableTreeModel(): TreeNodeModel {
      const variableTreeNodes: TreeNodeModel[] = [];

      if(!this._variableNames || this._variableNames.length == 0) {
         return null;
      }

      for(let variable of this._variableNames) {
         variableTreeNodes.push(<TreeNodeModel> {
            label: variable,
            data: "parameter." + variable,
            icon: "variable-icon",
            leaf: true,
         });
      }

      return <TreeNodeModel> {
         label: "Variables",
         children: variableTreeNodes,
         leaf: false
      };
   }
}
