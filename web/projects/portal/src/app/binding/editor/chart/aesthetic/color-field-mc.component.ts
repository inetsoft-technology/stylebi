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
import { Component } from "@angular/core";
import { ColorFrameModel, StaticColorModel, VisualFrameModel } from "../../../../common/data/visual-frame-model";
import { DndService } from "../../../../common/dnd/dnd.service";
import { Tool } from "../../../../../../../shared/util/tool";
import { AestheticInfo } from "../../../data/chart/aesthetic-info";
import { ChartAggregateRef } from "../../../data/chart/chart-aggregate-ref";
import { ChartEditorService } from "../../../services/chart/chart-editor.service";
import { GraphUtil } from "../../../util/graph-util";
import { GraphTypes } from "../../../../common/graph-types";
import { AestheticFieldMc } from "./aesthetic-field-mc";
import { UIContextService } from "../../../../common/services/ui-context.service";

@Component({
   selector: "color-field-mc",
   templateUrl: "color-field-mc.component.html",
   styleUrls: ["aesthetic-field-mc.scss"],
})
export class ColorFieldMc extends AestheticFieldMc {
   constructor(editorService: ChartEditorService, dservice: DndService,
               protected uiContextService: UIContextService)
   {
      super(editorService, dservice, uiContextService);
   }

   protected getField(): AestheticInfo {
      if(this.aggr) {
         return this.aggr.colorField;
      }

      return this.bindingModel ? this.bindingModel[this.targetField || "colorField"] : null;
   }

   protected getFrames(): VisualFrameModel[] {
      let _frames: ColorFrameModel[] = [];

      if(!this.bindingModel) {
         return _frames;
      }

      let colorField: AestheticInfo = this.field;

      if(colorField) {
         _frames.push(colorField.frame);
         return _frames;
      }

      if(this.aggr) {
         if(!this.isMixedField()) {
            if(!this.aggr.colorFrame) {
               this.aggr.colorFrame = new StaticColorModel();
               (<StaticColorModel> this.aggr.colorFrame).color = "";
            }

            _frames.push(this.aggr.colorFrame);
         }

         if(GraphUtil.isWaterfall(this.bindingModel)) {
            _frames.push(this.aggr.summaryColorFrame);
         }

         return _frames;
      }

      if(GraphTypes.isRadar(this.bindingModel.chartType) ||
         GraphTypes.isContour(this.getChartType()))
      {
         _frames.push(this.bindingModel.colorFrame);
         return _frames;
      }

      let arr: any[] = GraphUtil.getVisualFrames("colorFrame", this.bindingModel);

      for(let item of arr) {
         _frames.push(<ColorFrameModel> item.frame);
      }

      if(!GraphUtil.isWaterfall(this.bindingModel) || _frames.length == 0) {
         if(_frames.length == 0 && this.getBindingRefs().length > 0) {
            _frames.push(this.bindingModel[this.targetFrame || "colorFrame"]);
         }
      }

      return _frames;
   }

   changeColorFrame(frame: any) {
      if(this.field) {
         this.field.frame = frame;
      }
      else {
         this.bindingModel[this.targetFrame || "colorFrame"] = frame;
      }

      this.frames = this.getFrames();
      this.submitIfChanged();
   }

   protected isMixed(): boolean {
      return GraphUtil.isAllChartAggregate(this.aggr) &&
         (super.isMixedValue("colorField") || super.isMixedValue("colorFrame"));
   }

   protected getEditPaneId(): string {
      if(!this.isEditEnabled() || !this.isEnabled() || !this.bindingModel) {
         return "";
      }

      if(GraphTypes.isContour(this.getChartType())) {
         return "LinearColor";
      }

      if(this.field) {
         if(!this.field.dataInfo.measure ||
            this.field.dataInfo.classType == "aggregate" &&
            (<ChartAggregateRef> this.field.dataInfo).discrete)
         {
            return "CategoricalColor";
         }
         else {
            return "LinearColor";
         }
      }

      return this.frames.length > 1 ? "CombinedColor" : "StaticColor";
   }

   setAestheticRef(ref: AestheticInfo) {
      if(this.aggr) {
         this.aggr.colorField = ref;
      }
      else {
         this.bindingModel[this.targetField || "colorField"] = ref;
      }
   }

   syncAllChartAggregateInfo(): void {
      if(!this.isEditEnabled) {
         return;
      }

      let aggrs: ChartAggregateRef[] = GraphUtil.getAestheticAggregateRefs(this.bindingModel);

      for(let aggr0 of aggrs) {
         aggr0.colorField = Tool.clone(this.aggr.colorField);
         aggr0.colorFrame = Tool.clone(this.aggr.colorFrame);
         aggr0.summaryColorFrame = Tool.clone(this.aggr.summaryColorFrame);
      }
   }

   getFieldType(): string {
      return "color";
   }

   isPrimaryField(): boolean {
      return GraphTypes.isPie(this.getChartType());
   }

   protected isDropPaneAccept(): boolean {
      if(GraphTypes.isContour(this.getChartType())) {
         return false;
      }

      return super.isDropPaneAccept();
   }

   getHint(): String {
      return this.isContour() ? "Edit Color" : null;
   }

   reset(): void {
      // force submit. (57425)
      this.oframes = "";
   }

   isContour(): boolean {
      return GraphTypes.isContour(this.getChartType());
   }
}
