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
import { Component, EventEmitter, Input, Output } from "@angular/core";
import { GraphTypes } from "../../../../common/graph-types";
import { ChartAggregateRef } from "../../../data/chart/chart-aggregate-ref";
import { ChartBindingModel } from "../../../data/chart/chart-binding-model";
import { GraphUtil } from "../../../util/graph-util";
import { CombinedSizePane } from "./combined-size-pane.component";
import { BindingSizePane } from "./binding-size-pane.component";
import { StaticSizePane } from "./static-size-pane.component";
import { LinearTexturePane } from "./linear-texture-pane.component";
import { LinearLinePane } from "./linear-line-pane.component";
import { StaticLinePane } from "./static-line-pane.component";
import { StaticTexturePane } from "./static-texture-pane.component";
import { LinearShapePane } from "./linear-shape-pane.component";
import { CategoricalShapePane } from "./categorical-shape-pane.component";
import { CombinedShapePane } from "./combined-shape-pane.component";
import { StaticShapePane } from "./static-shape-pane.component";
import { LinearColorPane } from "./linear-color-pane.component";
import { CategoricalColorPane } from "./categorical-color-pane.component";
import { CombinedColorPane } from "./combined-color-pane.component";
import { StaticColorPane } from "./static-color-pane.component";
import { NgIf, NgSwitch, NgSwitchCase } from "@angular/common";

@Component({
    selector: "visual-dropdown-pane",
    templateUrl: "visual-dropdown-pane.component.html",
    standalone: true,
    imports: [NgIf, NgSwitch, NgSwitchCase, StaticColorPane, CombinedColorPane, CategoricalColorPane, LinearColorPane, StaticShapePane, CombinedShapePane, CategoricalShapePane, LinearShapePane, StaticTexturePane, StaticLinePane, LinearLinePane, LinearTexturePane, StaticSizePane, BindingSizePane, CombinedSizePane]
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
