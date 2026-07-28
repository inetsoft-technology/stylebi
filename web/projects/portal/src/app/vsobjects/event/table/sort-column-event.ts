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
import { BaseTableEvent, BaseTableEventBuilder } from "./base-table-event";

/**
 * Event used to sort a table column.
 * Plain class (not namespace) so ESM circular init cannot leave BaseTableEvent
 * undefined at `class extends` evaluation time.
 */
export class SortColumnEvent extends BaseTableEvent {
   constructor(assemblyName: string,
               private row: number, private col: number,
               private colName: string, private multi: boolean,
               private detail: boolean = false)
   {
      super(assemblyName);
   }

   public static builder(assemblyName: string): SortColumnEventBuilder {
      return new SortColumnEventBuilder(assemblyName);
   }
}

class SortColumnEventBuilder extends BaseTableEventBuilder {
   private _row: number;
   private _col: number;
   private _colName: string;
   private _multi: boolean;
   private _detail: boolean;

   constructor(assemblyName: string) {
      super(assemblyName);
   }

   public row(value: number): this {
      this._row = value;
      return this;
   }

   public col(value: number): this {
      this._col = value;
      return this;
   }

   public colName(value: string): this {
      this._colName = value;
      return this;
   }

   public multi(value: boolean): this {
      this._multi = value;
      return this;
   }

   public detail(value: boolean): this {
      this._detail = value;
      return this;
   }

   public build(): SortColumnEvent {
      return new SortColumnEvent(
         this.assemblyName, this._row, this._col, this._colName, this._multi, this._detail);
   }
}
