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
import { BaseTableEvent } from "./base-table-event";

/**
 * Pass the selected cells to the server. Jackson can't properly serialize the es6 map
 * type so convert Map<number, number[]> to an object. On the server it gets converted
 * back to a Map<Integer, int[]>
 */
export class FlyoverEvent extends BaseTableEvent {
   private selectedCells: { [prop: number]: number[] } = {};

   constructor(assemblyName: string, selectedCells: Map<number, number[]>) {
      super(assemblyName);

      selectedCells.forEach((value, key) => {
         this.selectedCells[key] = value;
      });
   }
}
