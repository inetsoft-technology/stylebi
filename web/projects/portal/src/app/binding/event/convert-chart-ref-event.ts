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
import { ViewsheetEvent } from "../../common/viewsheet-client/index";
import { ChartBindingModel } from "../data/chart/chart-binding-model";
import { Tool } from "../../../../../shared/util/tool";

/**
 * Event for common parameters for ConvertChartRefEvent
 */
export class ConvertChartRefEvent implements ViewsheetEvent {
   /**
    * Creates a new instance of <tt>ConvertChartRefEvent</tt>
    *
    * @param name the name of the chart
    * @param refName the name of the ref wanted to convert
    * @param changeType the conversion type
    */
   constructor(public name: string, public refNames: string[], public changeType: number,
      public table: string, public confirmed: boolean, binding: ChartBindingModel)
   {
      this.binding = Tool.shallowClone(binding);
      this.binding.availableFields = [];
   }

   binding: any;
}
