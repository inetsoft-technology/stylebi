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
import { ConditionItemPaneProvider } from "../../../common/data/condition/condition-item-pane-provider";
import { Observable } from "rxjs";
import { AssetEntry } from "../../../../../../shared/data/asset-entry";
import { AbstractDataRef } from "../../../common/data/abstract-data-ref";
import { TreeNodeModel } from "../../tree/tree-node-model";
import { SqlQueryDialogModel } from "../../../composer/data/ws/sql-query-dialog-model";

export interface SqlQueryDialogController {
   dataSource: string;
   CONTROLLER_SOCKET: string;
   CONTROLLER_MODEL: string;
   sqlConditionProvider: ConditionItemPaneProvider;
   subQuery: boolean;

   getSQLString<T>(model: any): Observable<T>;
   getSqlParseResult<T>(str: any): Observable<T>;
   getTableColumns<T>(table: AssetEntry): Observable<T>;
   getDataSourceTree(node?: any, columnLevel?: boolean): Observable<TreeNodeModel>;
   setModel(modelValue: SqlQueryDialogModel): void;
   getModel(): Observable<SqlQueryDialogModel>;
   setConditionFields(conditionFields: AbstractDataRef[]);
}
