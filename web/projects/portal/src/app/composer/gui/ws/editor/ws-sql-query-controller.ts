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
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { ComponentTool } from "../../../../common/util/component-tool";
import { SqlQueryDialogController } from "../../../../widget/dialog/sql-query-dialog/sql-query-dialog-controller";
import { ConditionItemPaneProvider } from "../../../../common/data/condition/condition-item-pane-provider";
import { HttpClient, HttpErrorResponse, HttpHeaders, HttpParams } from "@angular/common/http";
import { Observable, of } from "rxjs";
import { AssetEntry } from "../../../../../../../shared/data/asset-entry";
import { TreeNodeModel } from "../../../../widget/tree/tree-node-model";
import { SQLConditionItemPaneProvider } from "../../../dialog/ws/sql-condition-item-pane-provider";
import { catchError, tap } from "rxjs/operators";
import { AbstractDataRef } from "../../../../common/data/abstract-data-ref";
import { ModelService } from "../../../../widget/services/model.service";
import { SqlQueryDialogModel } from "../../../data/ws/sql-query-dialog-model";

const CONTROLLER_SQL_STRING = "../api/composer/ws/sql-query-dialog/get-sql-string";
const CONTROLLER_SQL_PARSE_RESULT = "../api/composer/ws/sql-query-dialog/get-sql-parse-result";
const CONTROLLER_TABLE_COLUMNS = "../api/composer/ws/sql-query-dialog/table-columns";
const CONTROLLER_DATA_SOURCE_TREE = "../api/data/datasource/query/data-source-tree";
const CONTROLLER_SUBQUERY_MODEL = "../api/data/condition/subquery/sql-query-dialog-model";

export class WsSqlQueryController implements SqlQueryDialogController {
   tableName: string;
   private _CONTROLLER_SOCKET = "/events/ws/dialog/sql-query-dialog-model";
   private _CONTROLLER_MODEL = "../api/composer/ws/sql-query-dialog-model";
   private readonly _sqlConditionProvider: SQLConditionItemPaneProvider;
   readonly headers: HttpHeaders;
   private _runtimeId: string;
   private _dataSource: string;
   private _modelValue: SqlQueryDialogModel;
   private _subQuery: boolean;

   get dataSource() {
      return this._dataSource;
   }

   set dataSource(dataSource: string) {
      this._dataSource = dataSource;
      this._sqlConditionProvider.dataSource = dataSource;
   }
   get runtimeId(): string {
      return this._runtimeId;
   }

   set runtimeId(id: string) {
      this._runtimeId = id;
      this._sqlConditionProvider.runtimeid = this.runtimeId;
   }

   get CONTROLLER_SOCKET() {
      return this._CONTROLLER_SOCKET;
   }

   get CONTROLLER_MODEL() {
      return this._CONTROLLER_MODEL;
   }

   get sqlConditionProvider(): ConditionItemPaneProvider {
      return this._sqlConditionProvider;
   }

   get subQuery(): boolean {
      return this._subQuery;
   }

   set subQuery(subQuery: boolean) {
      this._subQuery = subQuery;
   }

   constructor(private http: HttpClient, private modelService: ModelService,
               private modalService: NgbModal)
   {
      this.headers = new HttpHeaders({
         "Content-Type": "application/json"
      });

      this._sqlConditionProvider = new SQLConditionItemPaneProvider(this.http);
   }

   setModel(modelValue: SqlQueryDialogModel) {
      this._modelValue = modelValue;
   }

   getModel(): Observable<SqlQueryDialogModel> {
      if(this._modelValue) {
         return of(this._modelValue);
      }

      let url = !this.subQuery ? this.CONTROLLER_MODEL : CONTROLLER_SUBQUERY_MODEL;
      let params = new HttpParams().set("dataSource", this.dataSource);

      if(!this.subQuery) {
         params = params
            .set("runtimeId", this.runtimeId);

         if(this.tableName) {
            params = params.set("tableName", this.tableName);
         }
      }

      return this.modelService.getModel<SqlQueryDialogModel>(url, params)
         .pipe(
            catchError((error: HttpErrorResponse) => {
               let message = "_#(js:em.data.databases.error)";

               if(error.status === 504) {
                  message = "_#(js:em.data.databases.error.gatewayTimeout)";
               }

               const cloudError: string = "_#(js:em.datasource.cloudError)";

               if(!!cloudError && !!cloudError.length && cloudError !== "null") {
                  message += "\n\n" + cloudError;
               }

               ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)", message).then();

               return of(null);
            }),
            tap(model => this._sqlConditionProvider.variableNames = model?.variableNames));
   }

   getDataSourceTree(node?: any, columnLevel?: boolean): Observable<TreeNodeModel> {
      const params = new HttpParams()
         .set("dataSource", this.dataSource)
         .set("columnLevel", columnLevel);
      const options = {headers: this.headers, params: params};

      return this.http.post<TreeNodeModel>(CONTROLLER_DATA_SOURCE_TREE, node, options);
   }

   getSQLString<T>(model: any): Observable<T> {
      return this.http.post<T>(CONTROLLER_SQL_STRING, model);
   }

   getSqlParseResult<T>(str: any): Observable<T> {
      return this.http.post<T>(CONTROLLER_SQL_PARSE_RESULT, str);
   }

   getTableColumns<T>(table: AssetEntry): Observable<T> {
      return this.http.post<T>(CONTROLLER_TABLE_COLUMNS, table);
   }

   setConditionFields(conditionFields: AbstractDataRef[]) {
      this._sqlConditionProvider.fields = conditionFields;
   }
}
