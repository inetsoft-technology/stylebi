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
import { QueryConditionPaneModel } from "./query-condition-pane-model";
import { QueryFieldPaneModel } from "./query-field-pane-model";
import { QueryGroupingPaneModel } from "./query-grouping-pane-model";
import { QueryLinkPaneModel } from "./query-link-pane-model";
import { QuerySortPaneModel } from "./query-sort-pane-model";
import { FreeFormSqlPaneModel } from "./free-form-sql-pane/free-form-sql-pane-model";

export class AdvancedSqlQueryModel {
   name: string;
   sqlEdited: boolean;
   linkPaneModel: QueryLinkPaneModel;
   fieldPaneModel: QueryFieldPaneModel;
   conditionPaneModel: QueryConditionPaneModel;
   sortPaneModel: QuerySortPaneModel;
   freeFormSQLPaneModel: FreeFormSqlPaneModel;
   groupingPaneModel: QueryGroupingPaneModel;
}