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
import { Component, Input } from "@angular/core";
import { ChartBindingModel } from "../../data/chart/chart-binding-model";
import { GraphTypes } from "../../../common/graph-types";

@Component({
   selector: "chart-high-low-pane",
   templateUrl: "chart-high-low-pane.component.html",
   styleUrls: ["aesthetic/aesthetic-pane.scss"]
})
export class ChartHighLowPane {
   @Input() bindingModel: ChartBindingModel;
   @Input() grayedOutValues: string[] = [];

   isCandle(): boolean {
      return GraphTypes.isCandle(this.bindingModel.chartType) ||
         GraphTypes.isStock(this.bindingModel.chartType);
   }

   isPath(): boolean {
      return !this.isCandle() && !this.isRelation() && this.bindingModel.supportsPathField;
   }

   isRelation(): boolean {
      return GraphTypes.isRelation(this.bindingModel.chartType);
   }

   isGantt(): boolean {
      return GraphTypes.isGantt(this.bindingModel.chartType);
   }
}
