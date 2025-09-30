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

import { CalcTableCell } from "../../../common/data/tablelayout/calc-table-cell";
import { CalcTableLayout } from "../../../common/data/tablelayout/calc-table-layout";
import { StyleConstants } from "../../../common/util/style-constants";
import { XConstants } from "../../../common/util/xconstants";
import { CellBindingInfo } from "../../data/table/cell-binding-info";
import { getGroupOptionLabel, getOrderDirection, removeNullProps } from "./binding-tool";
import { BindingType, CalcTableBindingField, ExpansionType } from "./types/binding-fields";

export function getCalcTableBindings(layout: CalcTableLayout,
                                     cellBindings: { [key: string]: CellBindingInfo },
                                     aggregates: string[]): CalcTableBindingField[]
{
   if(!layout?.tableRows?.length || !cellBindings) {
      return null;
   }

   const cells = layout.tableRows
      .flatMap(row => row?.tableCells ?? [])
      .filter(cell => cell?.text)
      .map(cell => {
         const cellPath = cell.cellPath.path[0];
         const cellBinding = cellBindings[cellPath];
         const rowGroup = cellBinding?.rowGroup || "";
         const colGroup = cellBinding?.colGroup || "";
         const bindingType = getCellBindingType(cellBinding);

         const cellField: CalcTableBindingField = {
            cell_name: cellBinding.runtimeName,
            cell_path: cellPath,
            binding_type: bindingType,
            row_group: rowGroup,
            column_group: colGroup,
         } as CalcTableBindingField;

         if(cellBinding?.expansion !== CellBindingInfo.EXPAND_NONE) {
            cellField.expansion = cellBinding.expansion === CellBindingInfo.EXPAND_H ?
               ExpansionType.HORIZONTAL : ExpansionType.VERTICAL;
         }

         if(bindingType === BindingType.GROUP) {
            cellField.field_name = cellBinding.value;

            if(cellBinding.order?.option !== XConstants.NONE_DATE_GROUP) {
               cellField.group = getGroupOptionLabel(cellBinding.order.option);
            }

            if(cellBinding.order?.type !== XConstants.SORT_NONE) {
               cellField.sort = {
                  direction: getOrderDirection(cellBinding.order.type),
                  by_measure: cellBinding.order.sortCol !== -1 ?
                     aggregates[cellBinding.order.sortCol] : null,
               }
            }

            if(cellBinding.topn?.type !== StyleConstants.NONE) {
               cellField.topn = {
                  enabled: true,
                  n: String(cellBinding.topn.topn),
                  by_measure: aggregates[cellBinding.topn.sumCol],
                  reverse: cellBinding.topn.type === StyleConstants.BOTTOM_N,
               };
            }
         }
         else if(bindingType === BindingType.AGGREGATE) {
            cellField.field_name = cellBinding.value;
            cellField.aggregation = cellBinding.formula;
         }
         else if(bindingType === BindingType.EXPRESSION) {
            cellField.expression = cellBinding.value;
         }
         else {
            cellField.cell_value = cellBinding.value;
         }

         return cellField;
      });

   return removeNullProps(cells);
}

export function getCalcTableRetrievalScriptContext(layout: CalcTableLayout,
                                                   cellBindings: { [p: string]: CellBindingInfo },
                                                   cellRowCol: { rowNumber: number, colNumber: number })
   : Record<string, string>
{
   if(!layout?.tableRows?.length || !cellBindings || !cellRowCol) {
      return null;
   }

   const { rowNumber, colNumber } = cellRowCol;
   const groupCellsSet = new Set<string>();
   const aggregateCellsSet = new Set<string>();
   const groupPositions: { row: number; col: number }[] = [];

   // Step 1: collect all cells into a flat array
   const allCells: {
      cell: any;
      binding: CellBindingInfo;
      bindingType: string;
   }[] = [];

   for(let i = 0; i <= rowNumber; i++) {
      const row = layout.tableRows[i];

      for(let j = 0; j <= colNumber; j++) {
         const cell = row.tableCells[j];
         const cellPath = cell.cellPath.path[0];
         const cellBinding = cellBindings[cellPath];
         const bindingType = getCellBindingType(cellBinding);

         allCells.push({ cell, binding: cellBinding, bindingType });

         if(cell.row === rowNumber && cell.col === colNumber) {
            break;
         }
      }
   }

   // Traverse all cells to collect group cells and record positions
   for(const { cell, binding, bindingType } of allCells) {
      if(cell.row === rowNumber && cell.col === colNumber) {
         continue;
      }

      if((cell.row === rowNumber || cell.col === colNumber) &&
         (bindingType === BindingType.GROUP ||
            (bindingType === BindingType.EXPRESSION && cell.bindingType === CellBindingInfo.GROUP)))
      {
         groupCellsSet.add(`$${binding.runtimeName}`);
         groupPositions.push({ row: cell.row, col: cell.col });
      }
   }

   // Traverse all cells to collect aggregate cells
   for(const { cell, binding, bindingType } of allCells) {
      if(cell.row === rowNumber && cell.col === colNumber) {
         continue;
      }

      if((bindingType === BindingType.AGGREGATE ||
            (bindingType === BindingType.EXPRESSION && cell.bindingType === CellBindingInfo.SUMMARY)) &&
         groupPositions.some(pos => cell.row === pos.row || cell.col === pos.col))
      {
         aggregateCellsSet.add(`$${binding.runtimeName}`);
      }
   }

   return {
      groupCells: Array.from(groupCellsSet).join(", "),
      aggregateCells: Array.from(aggregateCellsSet).join(", "),
   };
}

export function getCalcTableScriptContext(layout: CalcTableLayout,
                                          cellBindings: { [key: string]: CellBindingInfo },
                                          aggregates: string[],
                                          cellRowCol: { rowNumber: number, colNumber: number}): string
{
   if(!layout?.tableRows?.length || !cellBindings || !cellRowCol) {
      return null;
   }

   const { rowNumber, colNumber } = cellRowCol;
   const bindings = getCalcTableBindings(layout, cellBindings, aggregates);
   const filterdBindings = bindings
      .filter(field => {
         const cellPath = field.cell_path
            .slice(field.cell_path.indexOf("[") + 1, field.cell_path.indexOf("]"))
            .split(",");
         const row = parseInt(cellPath[0], 10);
         const col = parseInt(cellPath[1], 10);

         return field.binding_type !== BindingType.NORMAL_TEXT &&
            row <= rowNumber && col <= colNumber &&
            field.cell_path !== `Cell [${rowNumber},${colNumber}]`;
      })
      .map(field => {
         field.cell_name = "$" + field.cell_name;
         return field;
      });

   return JSON.stringify(filterdBindings);
}

export function getLastFormulaCellRowCol(cellBindings: { [p: string]: CellBindingInfo },
                                         selectedCells: CalcTableCell[])
{
   if(!selectedCells || selectedCells.length === 0) {
      return null;
   }

   let lastFormulaCell: CalcTableCell;

   for(let i = selectedCells.length - 1; i >= 0; i--) {
      const cell = selectedCells[i];
      const cellPath = cell.cellPath.path[0];
      const cellBinding = cellBindings[cellPath];
      const bindingType = getCellBindingType(cellBinding);

      if(bindingType === BindingType.EXPRESSION) {
         lastFormulaCell = cell;
         break;
      }
   }

   if(!lastFormulaCell) {
      return null;
   }

   return {
      rowNumber: lastFormulaCell.row,
      colNumber: lastFormulaCell.col
   };
}

function getCellBindingType(cellBinding: CellBindingInfo): string {
   const type = cellBinding?.type;
   const btype = cellBinding?.btype;

   if(type === CellBindingInfo.BIND_TEXT) {
      return BindingType.NORMAL_TEXT;
   }
   else if(type === CellBindingInfo.BIND_FORMULA) {
      return BindingType.EXPRESSION;
   }
   else if(btype === CellBindingInfo.GROUP) {
      return BindingType.GROUP;
   }
   else if(btype === CellBindingInfo.SUMMARY) {
      return BindingType.AGGREGATE;
   }

   return "";
}