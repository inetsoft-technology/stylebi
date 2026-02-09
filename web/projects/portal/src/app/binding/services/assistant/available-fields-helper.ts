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

import { DataRef } from "../../../common/data/data-ref";
import { SourceTable, SourceTableColumn } from "../../data/binding-model";
import { Field } from "./types/field";
import { Table } from "./types/table";

export function getAvailableFields(tables: SourceTable[], availableFields: DataRef[]): string {
   if(!availableFields || availableFields.length == 0) {
      return getTableFields(tables);
   }

   let fields: Field[] = [];

   availableFields.forEach(fld => {
      let is_calc = false;
      let expression = "";

      if(fld.classType === "CalculateRef" && fld.expression && (<any> fld).dataRefModel?.exp) {
         is_calc = true;
         expression = (<any> fld).dataRefModel.exp;
      }

      fields.push({
         field_name: fld.name,
         data_type: fld.dataType,
         description: fld.description,
         is_calcfield: is_calc,
         calc_expression: expression
      });
   });

   return JSON.stringify(fields);
}

export function getTableFields(sourceTables: SourceTable[]): string {
   if(!sourceTables || sourceTables.length == 0) {
      return "";
   }

   let tables: Table[] = [];

   sourceTables.forEach(table => {
      tables.push({
         table_name: table.name,
         columns: getTableColumns(table.columns)
      });
   });

   return JSON.stringify(tables);
}

export function getTableColumns(columns: SourceTableColumn[]): Field[] {
   let fields: Field[] = [];

   if(!columns || columns.length == 0) {
      return fields;
   }

   columns.forEach(column => {
      fields.push({
         field_name: column.name,
         data_type: column.dataType,
         is_calcfield: false, // for source table column will not include calc columns
         description: column.description
      });
   });

   return fields;
}
