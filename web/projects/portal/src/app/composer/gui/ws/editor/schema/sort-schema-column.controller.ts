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
import { ColumnRef } from "../../../../../binding/data/column-ref";
import { AbstractTableAssembly } from "../../../../data/ws/abstract-table-assembly";
import { TableAssemblyOperator } from "../../../../data/ws/table-assembly-operator";

export interface TableColumnsPair {
   table: AbstractTableAssembly;
   columns?: ColumnRef[];
}

function getHeader(ref: ColumnRef): string {
   return ref.alias ? ref.alias : ref.dataRefModel.attribute;
}

function compareColumnRefs(a: ColumnRef, b: ColumnRef): number {
   return getHeader(a).localeCompare(getHeader(b));
}

function isOutOnForeignTable(operatorGroups: TableAssemblyOperator[][],
                             rtable: string, op: TableAssemblyOperator): TableAssemblyOperator[]
{
   return operatorGroups.find((group) => {
      return !!group.find((operatorInGroup) => {
         return operatorInGroup.ltable === rtable &&
            ColumnRef.equalName(operatorInGroup.lref, op.rref);
      });
   });
}

/** Apply ordered_columns order to processed_columns */
function apply_column_order(ordered_columns: ColumnRef[],
                            remaining_columns: ColumnRef[],
                            processed_columns: ColumnRef[]): void
{
   if(remaining_columns.length > 0) {
      ordered_columns.forEach((oCol) => {
         let index = remaining_columns.findIndex((rCol) => ColumnRef.equalName(oCol, rCol));

         if(index >= 0) {
            processed_columns.push(remaining_columns[index]);
            remaining_columns.splice(index, 1);
         }
      });
   }
}

function sort_table_columns(nodes: NodeSchemaTable[],
                                     operatorGroups: TableAssemblyOperator[][],
                                     source_columns_field: string,
                                     processed_columns_field: string = null): void
{
   nodes.forEach((node) => {
      operatorGroups.forEach((group) => {
         let ltable = group[0].ltable;
         let rtable = group[0].rtable;

         if(ltable === node.table.name) {
            let target = nodes.find((n) => n.table.name === rtable);
            let source_columns: ColumnRef[] = <ColumnRef[]> node[source_columns_field];
            let ordered_columns: ColumnRef[] = [];
            let i: number;
            let j: number;

            for(i = 0; i < source_columns.length; i++) {
               let ref = source_columns[i];

               for(j = 0; j < group.length; j++) {
                  if(ColumnRef.equalName(ref, group[j].lref)) {
                     ordered_columns.push(group[j].rref);
                  }
               }
            }

            if(ordered_columns.length > 0) {
               apply_column_order(ordered_columns, target.in_out, target.processed_in_out);
               apply_column_order(ordered_columns, target.in_only, target.processed_in_only);

               if(!!processed_columns_field) {
                  let processed_columns: ColumnRef[] = <ColumnRef[]> node[processed_columns_field];

                  while(source_columns.length > 0) {
                     processed_columns.push(source_columns.shift());
                  }
               }
            }
         }
      });
   });
}

function addRef(ref: ColumnRef, list: ColumnRef[]): void {
   if(!list.find((r) => ColumnRef.equalName(r, ref))) {
      list.push(ref);
   }
}

function getRef(ref: ColumnRef, list: ColumnRef[]): ColumnRef {
   return list.find((r) => ColumnRef.equalName(r, ref));
}

function sortNodesByColumns(sort_nodes: NodeSchemaTable[]): void {
   sort_nodes.sort((a, b) => {
      let result = (a.in_out.length + a.in_only.length) -
         (b.in_out.length + b.in_only.length);

      if(result == 0) {
         result = -((a.in_out.length + a.out_only.length) -
         (b.in_out.length + b.out_only.length));
      }

      return result;
   });
}

function sortRemainingColumns(sort_nodes: NodeSchemaTable[],
                              operatorGroups: TableAssemblyOperator[][]): void
{
   sort_table_columns(sort_nodes, operatorGroups, "out_only");
   sort_table_columns(sort_nodes, operatorGroups, "processed_in_out");
   sort_table_columns(sort_nodes, operatorGroups, "in_out", "processed_in_out");
}

function createPreprocessedColumnRef(ref: ColumnRef,
                                     all_out_on_foreign_table_columns: ColumnRef[]): PreprocessedColumnRef
{
   return {
      column: ref,
      out_on_foreign_table: !!getRef(ref, all_out_on_foreign_table_columns)
   };
}

function sortPreprocessedColumnRefs(a: PreprocessedColumnRef, b: PreprocessedColumnRef): number {
   if(a.out_on_foreign_table && !b.out_on_foreign_table) {
      return -1;
   }

   if(!a.out_on_foreign_table && b.out_on_foreign_table) {
      return 1;
   }

   return compareColumnRefs(a.column, b.column);
}

function create_sort_node(table: AbstractTableAssembly,
                          operatorGroups: TableAssemblyOperator[][]): NodeSchemaTable
{
   let all_out_columns: ColumnRef[] = [];
   let all_in_columns: ColumnRef[] = [];
   let all_out_on_foreign_table_columns: ColumnRef[] = [];

   operatorGroups.forEach((group) => {
      // table pairs are same in a group
      let ltable = group[0] && group[0].ltable;
      let rtable = group[0] && group[0].rtable;

      if(ltable === table.name) {
         group.forEach((op) => {
            if(isOutOnForeignTable(operatorGroups, rtable, op)) {
               addRef(op.lref, all_out_on_foreign_table_columns);
            }

            addRef(op.lref, all_out_columns);
         });
      }
      else if(rtable === table.name) {
         group.forEach((op) => {
            addRef(op.rref, all_in_columns);
         });
      }
   });

   let pre_out_only: PreprocessedColumnRef[] = [];
   let pre_in_out: PreprocessedColumnRef[] = [];
   let in_only: ColumnRef[] = [];
   let non_join: ColumnRef[] = [];

   table.info.publicSelection.forEach((ref) => {
      if(getRef(ref, all_out_columns) && getRef(ref, all_in_columns)) {
         pre_in_out.push(createPreprocessedColumnRef(ref, all_out_on_foreign_table_columns));
      }
      else if(getRef(ref, all_out_columns)) {
         pre_out_only.push(createPreprocessedColumnRef(ref, all_out_on_foreign_table_columns));
      }
      else if(getRef(ref, all_in_columns)) {
         in_only.push(ref);
      }
      else {
         non_join.push(ref);
      }
   });

   pre_out_only.sort(sortPreprocessedColumnRefs);
   pre_in_out.sort(sortPreprocessedColumnRefs);
   let out_only: ColumnRef[] = pre_out_only.map((value) => value.column);
   let in_out: ColumnRef[] = pre_in_out.map((value) => value.column);

   non_join.sort(compareColumnRefs);

   return {
      table,
      out_only,
      in_out,
      in_only,
      non_join,
      processed_in_out: [],
      processed_in_only: []
   };
}

/** Reorders table columns according to https://gist.github.com/jshobe-inetsoft/0702a0bab44d4167d9fa60b5bbcc5286 */
export function reorderColumns(tables: AbstractTableAssembly[],
                               operatorGroups: TableAssemblyOperator[][]): TableColumnsPair[]
{
   let tiPairs: TableColumnsPair[] = [];
   let sort_nodes: NodeSchemaTable[] = tables.map((table) => create_sort_node(table, operatorGroups));
   sortNodesByColumns(sort_nodes);
   sortRemainingColumns(sort_nodes, operatorGroups);

   sort_nodes.forEach((node) => {
      tiPairs.push({
         table: node.table,
         columns: node.out_only.concat(node.processed_in_out, node.processed_in_only, node.non_join)
      });
   });

   return tiPairs;
}

interface NodeSchemaTable {
   table: AbstractTableAssembly;
   out_only: ColumnRef[];
   in_out: ColumnRef[];
   in_only: ColumnRef[];
   non_join: ColumnRef[];
   processed_in_out: ColumnRef[];
   processed_in_only: ColumnRef[];
}

interface PreprocessedColumnRef {
   column: ColumnRef;
   out_on_foreign_table: boolean;
}
