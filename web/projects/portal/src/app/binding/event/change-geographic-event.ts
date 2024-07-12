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
import { ViewsheetEvent } from "../../common/viewsheet-client";
import { ChartBindingModel } from "../data/chart/chart-binding-model";
import { Tool } from "../../../../../shared/util/tool";

/**
 * Event for common parameters for ChangeGeographicEvent
 */
export class ChangeGeographicEvent implements ViewsheetEvent {
   /**
    * Creates a new instance of <tt>ChangeGeographicEvent</tt>
    */
   constructor(public name: string, public refName: string, public isDim: boolean,
      public type: string, binding: ChartBindingModel, public table: string,
      public confirmed: boolean)
   {
      this.binding = Tool.shallowClone(binding);
      this.binding.availableFields = [];
   }

   binding: any;
}