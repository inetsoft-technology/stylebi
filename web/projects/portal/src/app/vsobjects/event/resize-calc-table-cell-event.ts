/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
import { TableCellEvent } from "./table-cell-event";

/**
 * Event used to resize a calc table cell in vs binding.
 */
export class ResizeCalcTableCellEvent extends TableCellEvent {
   /**
    * The new width of the cell.
    */
   public width: number;

   /**
    * The new height of the cell.
    */
   public height: number;

   /**
    * The new resize operator type.
    */
   public op: string;

   /**
    * Creates a new instance of <tt>ResizeTableColumnEvent</tt>.
    *
    * @param objectName the name of the object.
    * @param row      the row index.
    * @param col      the col index.
    * @param width      the new width.
    * @param height      the new height.
    * @param op      the operator type.
    */
   constructor(objectName: string, row: number, col: number, width: number,
               height: number, op: string)
   {
      super(objectName, col, row);
      this.width = width;
      this.height = height;
      this.op = op;
   }
}