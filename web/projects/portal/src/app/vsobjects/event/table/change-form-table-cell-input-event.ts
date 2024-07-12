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

/**
 * Event used to change a form cell input.
 */
export class ChangeFormTableCellInput extends BaseTableEvent {
   private col: number;
   private row: number;
   private data: string;
   private start: number;

   constructor(assemblyName: string, row: number, col: number, data: string,
               start: number)
   {
      super(assemblyName);

      this.row = row;
      this.col = col;
      this.data = data;
      this.start = start;
   }
}