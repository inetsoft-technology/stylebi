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
import { HyperlinkModel } from "../../common/data/hyperlink-model";
import { ViewsheetCommand } from "../../common/viewsheet-client";
import { BaseTableCellModel } from "../model/base-table-cell-model";

/**
 * Command used to instruct the client to add or update an assembly object.
 */
export interface LoadTableDataCommand extends ViewsheetCommand {
   /**
    * The table cells to display. This contains cell data and
    * the format model
    */
   tableCells: BaseTableCellModel[][];
   tableHeaderCells: BaseTableCellModel[][];
   prototypeCache: {
      [key: number]: {
         [p in keyof BaseTableCellModel]: BaseTableCellModel[p];
      };
   };

   /**
    * The first row to load
    */
   start: number;

   /**
    * The last row to load
    */
   end: number;

   runtimeRowHeaderCount: number;
   runtimeColHeaderCount: number;
   runtimeDataRowCount: number;
   colWidths: number[];
   rowCount: number;
   colCount: number;
   dataRowCount: number;
   headerRowCount: number;
   headerColCount: number;
   headerRowHeights: number[];
   dataRowHeight: number;
   headerRowPositions: number[];
   dataRowPositions: number[];
   scrollHeight: number;
   wrapped: boolean;
   formChanged: boolean;
   limitMessage: string;
   rowHyperlinks: HyperlinkModel[];
}
