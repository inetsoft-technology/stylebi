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
import { AggregateInfo } from "../../../binding/data/aggregate-info";
import { ColumnInfo } from "./column-info";
import { WSAssembly } from "./ws-assembly";
import { WSTableAssemblyInfo } from "./ws-table-assembly-info";

export type TableAssemblyClass =
   "BoundTableAssembly" |
   "ComposedTableAssembly" |
   "CompositeTableAssembly" |
   "ConcatenatedTableAssembly" |
   "CubeTableAssembly" |
   "DataTableAssembly" |
   "EmbeddedTableAssembly" |
   "MergeJoinTableAssembly" |
   "MirrorTableAssembly" |
   "PhysicalBoundTableAssembly" |
   "QueryBoundTableAssembly" |
   "RelationalJoinTableAssembly" |
   "RotatedTableAssembly" |
   "SnapshotEmbeddedTableAssembly" |
   "SQLBoundTableAssembly" |
   "TabularTableAssembly" |
   "UnpivotTableAssembly";

export type WSTableMode = "default" | "full" | "live" | "detail" | "edit";

export type WSTableButton = WSTableMode | "preview" | "exit-preview" | "condition" |
   "has-condition" | "group" | "sort" | "reorder-columns" | "edit-query" | "expression" |
   "import-data-file" | "export" | "mirror-auto-update-enabled" |
   "mirror-auto-update-disabled" | "run-query" | "stop-query" | "search-toggle" |
   "wrap-column-headers" | "visible";

export interface WSTableAssembly extends WSAssembly {
   totalRows: number;
   rowsCompleted: boolean;
   info: WSTableAssemblyInfo;
   colInfos: ColumnInfo[];
   exceededMaximum: string;
   tableClassType: TableAssemblyClass;
   columnTypeEnabled?: boolean;
   hasMaxRow: boolean;
   crosstab: boolean;
   aggregateInfo: AggregateInfo;
}
