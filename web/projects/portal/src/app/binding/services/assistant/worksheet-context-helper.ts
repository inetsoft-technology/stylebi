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

import { Worksheet } from "../../../composer/data/ws/worksheet";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";

export function getWorksheetContext(ws: Worksheet): string {
   if(!ws || !ws.tables || ws.tables.length === 0) {
      return "";
   }

   let context = "";

   ws.tables.forEach(table => {
      if((<any> table).subtables) {
         context += `${table.name}: join of ${(<any> table).subtables.join(",")}\n`;
      }
      else {
         context += `${table.name}:\n`;

         table.colInfos.forEach(colInfo => {
            context += `  ${colInfo.name}: ${colInfo.ref.dataType}\n`;
         });
      }
   });

   return context;
}

export function getWorksheetScriptContext(fields: TreeNodeModel[]): string {
   if(!fields || fields.length === 0) {
      return "";
   }

   return fields.map(field => `field['${field.data}']`).join("\n")
}