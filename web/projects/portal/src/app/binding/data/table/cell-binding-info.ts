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
import { OrderModel } from "./order-model";
import { TopNModel } from "./topn-model";

export class CellBindingInfo {
   /**
    * Cell binding: default group binding in a cell.
    */
   public static get DEFAULT_GROUP(): string {
      return "(default)";
   }

   /**
    * No expansion.
    */
   public static get EXPAND_NONE(): number {
      return 0;
   }

   /**
    * Expand horizontal.
    */
   public static get EXPAND_H(): number {
      return 1;
   }

   /**
    * Expand vertical.
    */
   public static get EXPAND_V(): number {
      return 2;
   }

   /**
    * Cell binding: group binding in a cell.
    */
   public static get GROUP(): number {
      return 1;
   }

   /**
    * Cell binding: Bind to a column in the data table lens.
    */
   public static get BIND_TEXT(): number {
      return 1;
   }

   /**
    * Cell binding: Bind to a column in the data table lens.
    */
   public static get BIND_COLUMN(): number {
      return 2;
   }

   /**
    * Cell binding: Bind to a formula.
    */
   public static get BIND_FORMULA(): number {
      return 3;
   }

   /**
    * Cell binding: normal binding in a cell.
    */
   public static get DETAIL(): number {
      return 2;
   }

   /**
    * Cell binding: summary binding in a cell.
    */
   public static get SUMMARY(): number {
      return 3;
   }

   type: number;
   btype: number;
   name: string;
   expansion: number;
   mergeCells: boolean;
   mergeRowGroup: string;
   mergeColGroup: string;
   rowGroup: string;
   colGroup: string;
   value: string;
   formula: string;
   order: OrderModel;
   topn: TopNModel;
   timeSeries: boolean;
   runtimeName: string;
}
