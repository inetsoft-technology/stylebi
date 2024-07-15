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
import { VSObjectEvent } from "./vs-object-event";

/**
 * Event used to perform column related actions.
 */
export class FilterChartEvent extends VSObjectEvent {
   /**
    * The index of the column.
    */
   public columnName: string;

   /**
    * Filter is for legend item.
    */
   public legend: boolean;

   /**
    * The top offset position of the filter relative to the chart.
    */
   public top: number;

   /**
    * The left offset position of the filter relative to the chart.
    */
   public left: number;

   /**
    * If the selected area is a dimension.
    */
   public dimension: boolean;

   /**
    * Creates a new instance of <tt>FilterChartEvent</tt>.
    *
    * @param objectName the name of the object.
    * @param columnName the name of the column.
    * @param top        the top offset for the filter.
    * @param left       the left offset for the filter.
    * @param dimension  whether the selected area is a dimension.
    */
   constructor(objectName: string, columnName: string, top: number, left: number,
               dimension: boolean)
   {
      super(objectName);
      this.columnName = columnName;
      this.top = top;
      this.left = left;
      this.dimension = dimension;
   }
}