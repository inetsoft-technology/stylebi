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
import { ViewsheetEvent } from "../../common/viewsheet-client/index";
import { SourceInfo } from "../data/source-info";

/**
 * Event for common parameters for ConvertTableRefEvent
 */
export class ConvertTableRefEvent implements ViewsheetEvent {
   /**
    * Creates a new instance of <tt>ConvertTableRefEvent</tt>
    *
    * @param name the name of the chart
    * @param refName the name of the ref wanted to convert
    * @param changeType the conversion type
    */
   constructor(public name: string, public refNames: string[], public convertType: number,
      public source: SourceInfo, public sourceChange: boolean, public table: string,
      public confirmed: boolean)
   {
   }
}
