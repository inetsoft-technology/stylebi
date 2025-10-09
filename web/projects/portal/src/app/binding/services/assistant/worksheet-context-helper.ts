/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

import { AbstractTableAssembly } from "../../../composer/data/ws/abstract-table-assembly";
import { ColumnInfo } from "../../../composer/data/ws/column-info";
import { CompositeTableAssembly } from "../../../composer/data/ws/composite-table-assembly";
import { Worksheet } from "../../../composer/data/ws/worksheet";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";
import { AggregateInfo } from "../../data/aggregate-info";
import { getGroupOptionLabel, getOrderDirection, removeNullProps } from "./binding-tool";
import {
   WSInfo,
   WSTableInfo,
   WSColumnInfo,
   WSScriptField,
   WSGroupRef,
   WSAggregateRef
} from "./types/ws-info";

export function getWorksheetContext(ws: Worksheet): string {
   if(!ws || !ws.tables || ws.tables.length === 0) {
      return "";
   }

   let wsInfo: WSInfo = {};
   let tableInfos: WSTableInfo[] = [];

   ws.tables.forEach(table => {
      tableInfos.push(getWSTableInfo(table));
   });

   wsInfo.tables = tableInfos;
   return JSON.stringify(removeNullProps(wsInfo));
}

export function getWSTableInfo(table: AbstractTableAssembly): WSTableInfo {
   let wsTableInfo: WSTableInfo = { crosstab: table.crosstab };
   wsTableInfo.columns = convertTableColumns(table.colInfos);
   fixAggregateInfo(wsTableInfo, table.aggregateInfo);

   if(table instanceof CompositeTableAssembly) {
      wsTableInfo.subtables = (table as CompositeTableAssembly).subtables;
   }

   return wsTableInfo;
}

export function convertTableColumns(cols: ColumnInfo[]): WSColumnInfo[] {
   if(!cols || cols.length == 0) {
      return null;
   }

   let columnInfos: WSColumnInfo[] = [];

   for(let i = 0; i < cols.length; i++) {
      let columnInfo = convertColumnInfo(cols[i]);

      if(columnInfo) {
         columnInfos.push(columnInfo);
      }
   }

   if(columnInfos.length == 0) {
      return null;
   }

   return columnInfos;
}

export function fixAggregateInfo(tableInfo: WSTableInfo, aggregateInfo: AggregateInfo):void {
   if(!aggregateInfo) {
      return;
   }

   if(aggregateInfo.groups != null) {
      let groups: WSGroupRef[] = [];

      for(let group of aggregateInfo.groups) {
         let groupRef: WSGroupRef = {
            column_name: group.name,
            data_type: group.dataType,
            base_column: group.ref.name,
            group_level: getGroupOptionLabel(group.dgroup)
         }

         groups.push(groupRef);
      }
   }

   if(aggregateInfo.aggregates != null) {
      let aggregateRefs: WSAggregateRef[] = [];

      for(let agg of aggregateInfo.aggregates) {
         let aggRef: WSAggregateRef = {
            column_name: agg.name,
            data_type: agg.dataType,
            base_columns: agg.ref2 == null ? [agg.ref.name] : [agg.ref.name, agg.ref2.name],
            formula: agg.formulaName
         }

         aggregateRefs.push(aggRef);
      }
   }
}

export function convertColumnInfo(col: ColumnInfo): WSColumnInfo {
   if(!col) {
      return null;
   }

   let colInfo: WSColumnInfo = {
      column_name: col.name,
      data_type: col.ref == null ? "" : col.ref.dataType,
      group: col.group,
      aggregate: col.aggregate,
      sortType: getOrderDirection(col.sortType),
      description: col.ref == null ? "" : col.ref.description
   };

   return colInfo;
}

export function getWorksheetScriptContext(fields: TreeNodeModel[]): string {
   if(!fields || fields.length === 0) {
      return "";
   }

   let fieldsInfo: WSScriptField[] = [];

   fields.forEach(field => {
      let fieldInfo: WSScriptField = { field_name: `field['${field.data}']`}
      fieldsInfo.push(fieldInfo);
   });

   return JSON.stringify(removeNullProps(fieldsInfo));
}