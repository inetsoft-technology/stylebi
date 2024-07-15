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
import { VSObjectModel } from "./vs-object-model";
import { VSFormatModel } from "./vs-format-model";
import { VSAnnotationModel } from "./annotation/vs-annotation-model";
import { SortInfo } from "../objects/table/sort-info";
import { TableDataPath } from "../../common/data/table-data-path";

export interface BaseTableModel extends VSObjectModel {
   colWidths: number[];
   headerRowPositions: number[];
   dataRowPositions: number[];
   rowCount: number;
   colCount: number;
   dataRowCount: number;
   dataColCount: number;
   scrollHeight: number;
   headerRowCount: number;
   headerColCount: number;
   headerRowHeights: number[];
   dataRowHeight: number;
   wrapped: boolean; //whether there is any word wrap in data cells
   metadata: boolean;
   limitMessage?: string;
   title: string;
   titleFormat: VSFormatModel;
   titleVisible: boolean;
   shrink: boolean; // shrink to fit
   explicitTableWidth?: boolean;
   rowHeights?: Map<number, number>;
   editedByWizard: boolean;

   // annotations for different parts of the table
   leftTopAnnotations?: VSAnnotationModel[];
   leftBottomAnnotations?: VSAnnotationModel[];
   rightTopAnnotations?: VSAnnotationModel[];
   rightBottomAnnotations?: VSAnnotationModel[];

   // Flyover Information
   hasFlyover: boolean;
   isFlyOnClick: boolean;

   // Cell selection (map row -> col[])
   selectedData?: Map<number, number[]>;
   selectedHeaders?: Map<number, number[]>;
   titleSelected?: boolean;
   lastSelected: {row: number, column: number};

   // Sort info, used for hyperlinks and sort actions
   sortInfo: SortInfo;

   firstSelectedRow: number;
   firstSelectedColumn: number;

   enableAdhoc: boolean;
   enableAdvancedFeatures: boolean;

   highlightedCells?: TableDataPath[];
   isHighlightCopied: boolean;
   runtimeDataRowCount?: number;
   multiSelect?: boolean;
   empty?: boolean;
   objectHeight?: number;
   editing?: boolean;
   maxMode?: boolean;
   resizingCell?: boolean;
   maxModeOriginalWidth?: number;
}
