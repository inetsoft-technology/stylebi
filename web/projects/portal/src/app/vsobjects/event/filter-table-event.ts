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
import { TableCellEvent } from "./table-cell-event";

/**
 * Event used to add a table filter on viewsheet.
 */
export class FilterTableEvent extends TableCellEvent {
   /**
    * The top offset position of the filter relative to the table.
    */
   public top: number;

   /**
    * The left offset position of the filter relative to the table.
    */
   public left: number;

   /**
    * Creates a new instance of <tt>FilterTableEvent</tt>.
    *
    * @param objectName the name of the object.
    * @param index      the column index.
    * @param top        the top position for the filter.
    * @param left       the left position for the filter.
    */
   constructor(objectName: string, index: number, top: number, left: number)
   {
      super(objectName, index);
      this.top = top;
      this.left = left;
   }
}