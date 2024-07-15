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
import { Component, EventEmitter, Input, Output } from "@angular/core";
import { GraphTypes } from "../../../../common/graph-types";
import { ChartAggregateRef } from "../../../data/chart/chart-aggregate-ref";
import { ChartBindingModel } from "../../../data/chart/chart-binding-model";
import { GraphUtil } from "../../../util/graph-util";

@Component({
   selector: "visual-dropdown-pane",
   templateUrl: "visual-dropdown-pane.component.html"
})
export class VisualDropdownPane {
   @Input() aggr: ChartAggregateRef;
   @Input() frameModels: any;
   @Input() dropDownType: string;
   @Input() bindingModel: ChartBindingModel;
   @Output() onChangeColorFrame: EventEmitter<any> = new EventEmitter<any>();
   @Output() apply: EventEmitter<any> = new EventEmitter<any>();

   changeColorFrame(model: any) {
      this.onChangeColorFrame.emit(model);
   }

   get nilSupported(): boolean {
      return GraphUtil.isNilSupported(this.aggr, this.bindingModel);
   }

   applyClick(val: boolean) {
      this.apply.emit(val);
   }

   isInterval(): boolean {
      return GraphTypes.isInterval(this.aggr ? this.aggr.rtchartType : this.bindingModel.chartType);
   }
}
