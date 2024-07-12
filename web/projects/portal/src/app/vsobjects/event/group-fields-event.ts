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
 * Event used to group table columns/rows.
 */
export class GroupFieldsEvent extends TableCellEvent {
   /**
    * The new name of the group.
    */
   private groupName: string;

   /**
    * The old name of the group if being renamed.
    */
   public prevGroupName: string;

   /**
    * The cell labels.
    */
   private labels: string[];

   /**
    * The name of the column.
    */
   private columnName: string;

   /**
    * Filter is for legend item.
    */
   public legend: boolean;

   /**
    * Filter is for legend item.
    */
   public axis: boolean;

   /**
    * Creates a new instance of <tt>TableCellEvent</tt>.
    *
    * @param objectName the name of the object.
    * @param col        the column index.
    */
   constructor(objectName: string, row: number, col: number, columnName: string,
               labels: string[], groupName: string)
   {
      super(objectName, col);
      this.groupName = groupName;
      this.row = row;
      this.col = col;
      this.labels = labels;
      this.columnName = columnName;
   }
}