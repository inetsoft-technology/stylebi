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
import { BaseTableEvent, BaseTableEventBuilder } from "./base-table-event";

/**
 * Event used to initiate a drill
 */
export class DrillEvent extends BaseTableEvent { // eslint-disable-line @typescript-eslint/no-shadow
   constructor(assemblyName: string, private row: number,
               private col: number, private drillOp: string,
               private direction: string, private field: string,
               private drillAll = false)
   {
      super(assemblyName);
   }

   public static builder(assemblyName: string): DrillEventBuilder {
      return new DrillEventBuilder(assemblyName);
   }
}

class DrillEventBuilder extends BaseTableEventBuilder {
   private _row: number;
   private _col: number;
   private _drillOp: string;
   private _direction: string;
   private _field: string;
   private _drillAll = false;

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

   public drillOp(value: string): this {
      this._drillOp = value;
      return this;
   }

   public direction(value: string): this {
      this._direction = value;
      return this;
   }

   public field(value: string): this {
      this._field = value;
      return this;
   }

   public drillAll(drillAll: boolean): this {
      this._drillAll = drillAll;
      return this;
   }

   public build(): DrillEvent {
      return new DrillEvent(this.assemblyName, this._row, this._col, this._drillOp,
         this._direction, this._field, this._drillAll);
   }
}
