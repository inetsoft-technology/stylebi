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
import { VSObjectEvent } from "./vs-object-event";

/**
 * Event used to perform column related actions.
 */
export class TableCellEvent extends VSObjectEvent {
   /**
    * The index of the column.
    */
   public col: number;

   /**
    * The index of the row.
    */
   public row: number;

   /**
    * Creates a new instance of <tt>TableCellEvent</tt>.
    *
    * @param objectName the name of the object.
    * @param col        the column index.
    */
   constructor(objectName: string, col: number, row?: number)
   {
      super(objectName);
      this.col = col;
      this.row = row;
   }
}