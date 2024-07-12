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
import { DataRef } from "../../common/data/data-ref";
import { VariableInfo } from "../../common/data/variable-info";

/**
 * Event for getting available values.
 */
export class GetAvailableValuesEvent {
   /**
    * Creates a new instance of <tt>GetAvailableValuesEvent</tt>.
    *
    */
   constructor(dimension: DataRef, variables: VariableInfo[]) {
      this.dimension = dimension;
      this.variables = variables;
   }

   private dimension: DataRef;
   private variables: VariableInfo[];
}