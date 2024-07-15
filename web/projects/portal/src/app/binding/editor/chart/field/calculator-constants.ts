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
export class CalculatorConstants {
   /**
    * Calculator inner dimension tag for chart.
    */
   public static INNER_DIMENSION: string = "";
   /**
    * Calculator row inner dimension tag for crosstab.
    */
   public static ROW_INNER_DIMENSION: string = "0";
   /**
    * Calculator column inner dimension tag for crosstab.
    */
   public static COLUMN_INNER_DIMENSION: string = "1";
   /**
    * Key in calculator dims map for percent levels.
    */
   public static PERCENT_LEVEL_TAG: string = "percent_level";
   /**
    * Key in calculator dims map for percent dims.
    */
   public static PERCENT_DIMS_TAG: string = "percent_dims";
   /**
    * Key in calculator dims map for valueof dims.
    */
   public static VALUE_OF_TAG: string = "valueof";
   /**
    * Key in calculator dims map for breakby dims.
    */
   public static BREAK_BY_TAG: string = "breakby";
   /**
    * Key in calculator dims map for moving dims.
    */
   public static MOVING_TAG: string = "moving";


}