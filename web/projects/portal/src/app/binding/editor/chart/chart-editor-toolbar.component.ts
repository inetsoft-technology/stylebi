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
import { Component, Input } from "@angular/core";
import { ChartBindingModel } from "../../data/chart/chart-binding-model";
import { ChartEditorService } from "../../services/chart/chart-editor.service";
import { GraphTypes } from "../../../common/graph-types";

@Component({
   selector: "chart-editor-toolbar",
   templateUrl: "chart-editor-toolbar.component.html",
   styleUrls: ["chart-editor-toolbar.component.scss"]
})
export class ChartEditorToolbar {
   @Input() bindingModel: ChartBindingModel;

   constructor(protected editorService: ChartEditorService) {
   }

   getSeparatedIcon(): string {
      return this.bindingModel.separated ?
         "multi-chart-icon" : "single-chart-icon";
   }

   getSeparatedTitle(): string {
      return this.bindingModel.separated ?
         "_#(js:Switch to Single Graph)" : "_#(js:Switch to Separate Graph)";
   }

   isSeparateDisabled(): boolean {
      return GraphTypes.isMergedGraphType(this.bindingModel.chartType);
   }

   changeSeparateStatus(): void {
      if(!this.isSeparateDisabled()) {
         this.editorService.changeSeparateStatus(this.bindingModel);
      }
   }

   isSwapVisible(): boolean {
      let type: number = this.bindingModel.rtchartType;

      return !GraphTypes.isRadar(type) && !GraphTypes.isStock(type) &&
         !GraphTypes.isCandle(type) && !GraphTypes.isMekko(type) &&
         !GraphTypes.isFunnel(type);
   }

   isSwapEnabled(): boolean {
      if(!GraphTypes.isGeo(this.bindingModel.rtchartType)) {
         return true;
      }

      for(let xfield of this.bindingModel.xfields) {
         if(!xfield.measure) {
            return true;
         }
      }

      for(let yfield of this.bindingModel.yfields) {
         if(!yfield.measure) {
            return true;
         }
      }

      return false;
   }

   swapXYBinding(): void {
      if(this.isSwapEnabled()) {
         this.editorService.swapXYBinding();
      }
   }
}
