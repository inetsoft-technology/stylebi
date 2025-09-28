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

import { CalcTableLayout } from "../../../common/data/tablelayout/calc-table-layout";
import { CellBindingInfo } from "../../data/table/cell-binding-info";

const enum CalcTableBindingType {
   GROUP = 1,
   DETAIL = 2,
   SUMMARY = 3
}

const enum CalcTableSymbols {
   GROUP_SYMBOL = "\u039E",
   SUMMARY_SYMBOL = "\u2211"
}

export function getCalcTableBindingContext(layout: CalcTableLayout,
                                    cellBindings: { [key: string]: CellBindingInfo }): string
{
   if(!layout?.tableRows?.length || !cellBindings) {
      return "";
   }

   return layout.tableRows
      .flatMap(row => row?.tableCells ?? [])
      .filter(cell => cell?.text)
      .map(cell => {
         const cellPath = cell.cellPath.path[0];
         const cellBinding = cellBindings[cellPath];
         const rowColGroupDesc = this.getRowColGroupDesc(cellBinding);

         let text = cell.text;
         let result = "";

         if(cell.bindingType === CalcTableBindingType.GROUP) {
            text = text.substring(text.indexOf(CalcTableSymbols.GROUP_SYMBOL) + 1, text.length - 1);
            result = `${cellPath}: group(${text})`;
         }
         else if(cell.bindingType === CalcTableBindingType.SUMMARY) {
            const formula = cellBinding?.formula || "";
            text = text.substring(text.indexOf(CalcTableSymbols.SUMMARY_SYMBOL) + 1, text.length - 1);
            result = `${cellPath}: ${formula}(${text})`;
         }
         else {
            result = `${cellPath}: ${text}`;
         }

         return result + " " + rowColGroupDesc;
      })
      .join("\n");
}

function getRowColGroupDesc(cellInfo: CellBindingInfo): string {
   if(!cellInfo) {
      return "";
   }

   let rowColGroupDesc = "";

   if(cellInfo.rowGroup) {
      const rowGroup = cellInfo.rowGroup === "(default)" ? "default" : cellInfo.rowGroup;
      rowColGroupDesc += `${rowGroup}(row group) `;
   }

   if(cellInfo.colGroup) {
      const colGroup = cellInfo.colGroup === "(default)" ? "default" : cellInfo.colGroup;
      rowColGroupDesc += `${colGroup}(column group)`;
   }

   return rowColGroupDesc;
}