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
import { XConstants } from "../../../common/util/xconstants";

export class WorksheetTableOperator {
   /**
    * Join operation.
    */
   public static JOIN: number = XConstants.JOIN;
      /**
       * Inner join operation.
       */
   public static INNER_JOIN: number = XConstants.INNER_JOIN;
      /**
       * Left join operation.
       */
   public static LEFT_JOIN: number = XConstants.LEFT_JOIN;
      /**
       * Right join operation.
       */
   public static RIGHT_JOIN: number = XConstants.RIGHT_JOIN;
      /**
       * Full join operation.
       */
   public static FULL_JOIN: number = XConstants.FULL_JOIN;
      /**
       * Not equal join operation.
       */
   public static NOT_EQUAL_JOIN: number = XConstants.NOT_EQUAL_JOIN;
      /**
       * Greater join operation.
       */
   public static GREATER_JOIN: number = XConstants.GREATER_JOIN;
      /**
       * Greater equal join operation.
       */
   public static GREATER_EQUAL_JOIN: number = XConstants.GREATER_EQUAL_JOIN;
      /**
       * Less join operation.
       */
   public static LESS_JOIN: number = XConstants.LESS_JOIN;
      /**
       * Less equal join operation.
       */
   public static LESS_EQUAL_JOIN: number = XConstants.LESS_EQUAL_JOIN;
      /**
       * Merge join operation.
       */
   public static MERGE_JOIN: number = 1 << 7 | XConstants.JOIN;
      /**
       * Cross join operation.
       */
   public static CROSS_JOIN: number = 1 << 8 | XConstants.JOIN;
      /**
       * Concatenation operation.
       */
   public static CONCATENATION: number = 1 << 9;
      /**
       * Union operation.
       */
   public static UNION: number = 1 << 10 | WorksheetTableOperator.CONCATENATION;
      /**
       * Intersect operation.
       */
   public static INTERSECT: number = 1 << 11 | WorksheetTableOperator.CONCATENATION;
      /**
       * Minus operation.
       */
   public static MINUS: number = 1 << 12 | WorksheetTableOperator.CONCATENATION;
}
