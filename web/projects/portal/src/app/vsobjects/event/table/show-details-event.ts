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
import { BaseTableEvent } from "./base-table-event";
import { SortInfo } from "../../objects/table/sort-info";
import { DetailDndInfo } from "../../objects/table/detail-dnd-info";
import { FormatInfoModel } from "../../../common/data/format-info-model";

/**
 * Pass the selected cells to the server. Jackson can't properly serialize the es6 map
 * type so convert Map<number, number[]> to an object. On the server it gets converted
 * back to a Map<Integer, int[]>
 */
export class ShowDetailsEvent extends BaseTableEvent {
   private selectedCells: { [prop: number]: number[] } = {};

   constructor(assemblyName: string, selectedHeaders: Map<number, number[]>,
               selectedCells: Map<number, number[]>, private sortInfo: SortInfo,
               private format: FormatInfoModel, private column: number[],
               private worksheetId: string, private detailStyle: string = null,
               private dndInfo: DetailDndInfo = null, private newColName: string = null,
               private toggleHide: boolean = false)
   {
      super(assemblyName);

      if(selectedHeaders) {
         selectedHeaders.forEach((value, key) => {
            this.selectedCells[key] = value;
         });
      }

      if(selectedCells) {
         selectedCells.forEach((value, key) => {
            this.selectedCells[key] = value;
         });
      }
   }
}