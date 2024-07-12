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
import { SqlQueryDialogController } from "../../../../../../../widget/dialog/sql-query-dialog/sql-query-dialog-controller";
import { Observable, of } from "rxjs";
import { TreeNodeModel } from "../../../../../../../widget/tree/tree-node-model";
import { AssetEntry } from "../../../../../../../../../../shared/data/asset-entry";
import { AbstractDataRef } from "../../../../../../../common/data/abstract-data-ref";
import { HttpClient, HttpParams } from "@angular/common/http";
import { VpmSqlConditionItemPaneProvider } from "./vpm-sql-condition-item-pane-provider";
import { SqlQueryDialogModel } from "../../../../../../../composer/data/ws/sql-query-dialog-model";

const CONTROLLER_SQL_STRING = "../api/composer/ws/sql-query-dialog/get-sql-string";
const CONTROLLER_TABLE_COLUMNS = "../api/data/vpm/sql-query-dialog/table-columns";
const CONTROLLER_DATA_SOURCE_TREE = "../api/data/vpm/sql-query-dialog/data-source-tree";
const CONTROLLER_SQL_PARSE_RESULT = "../api/composer/ws/sql-query-dialog/get-sql-parse-result";

export class VpmSqlQueryController implements SqlQueryDialogController {
   CONTROLLER_MODEL: string = "../api/data/condition/subquery/sql-query-dialog-model";
   CONTROLLER_SOCKET: string;
   sqlConditionProvider: VpmSqlConditionItemPaneProvider;
   private _dataSource: string;
   private _modelValue: SqlQueryDialogModel;
   private _subQuery: boolean;

   get dataSource(): string {
      return this._dataSource;
   }

   set dataSource(dataSource: string) {
      this._dataSource = dataSource;
      this.sqlConditionProvider.dataSource = dataSource;
   }

   get subQuery(): boolean {
      return this._subQuery;
   }

   set subQuery(subQuery: boolean) {
      this._subQuery = subQuery;
   }

   constructor(private http: HttpClient) {
      this.sqlConditionProvider = new VpmSqlConditionItemPaneProvider(http);
   }

   setModel(modelValue: SqlQueryDialogModel) {
      this._modelValue = modelValue;
   }

   getDataSourceTree(node?: any, columnLevel?: boolean): Observable<TreeNodeModel> {
      let parameters = new HttpParams().set("dataSource", this.dataSource);

      return this.http.post<TreeNodeModel>(CONTROLLER_DATA_SOURCE_TREE, node, {params: parameters});
   }

   getModel(): Observable<SqlQueryDialogModel> {
      if(this._modelValue) {
         return of(this._modelValue);
      }

      const params = new HttpParams()
         .set("dataSource", this.dataSource);

      return this.http.get<SqlQueryDialogModel>(this.CONTROLLER_MODEL, {params: params});
   }

   getSQLString<T>(model: any): Observable<T> {
      return this.http.post<T>(CONTROLLER_SQL_STRING, model);
   }

   getTableColumns<T>(table: AssetEntry): Observable<T> {
      return this.http.post<T>(CONTROLLER_TABLE_COLUMNS, table);
   }

   setConditionFields(conditionFields: AbstractDataRef[]) {
   }

   getSqlParseResult<T>(str: any): Observable<T> {
      return this.http.post<T>(CONTROLLER_SQL_PARSE_RESULT, str);
   }
}

