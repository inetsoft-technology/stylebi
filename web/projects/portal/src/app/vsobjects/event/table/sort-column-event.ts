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
import {
   BaseTableEvent, BaseTableEventBuilder,
} from "./base-table-event";

export namespace SortColumnEvent {
   class SortColumnEvent extends BaseTableEvent { // eslint-disable-line @typescript-eslint/no-shadow
      constructor(assemblyName: string,
                  private row: number, private col: number,
                  private colName: string, private multi: boolean,
                  private detail: boolean = false)
      {
         super(assemblyName);
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

   export function builder(assemblyName: string): SortColumnEventBuilder {
      return new SortColumnEventBuilder(assemblyName);
   }
}