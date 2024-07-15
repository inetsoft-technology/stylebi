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
import {
   StaticLineModel,
   StaticShapeModel,
   StaticTextureModel,
   VisualFrameModel
} from "../../../../common/data/visual-frame-model";
import { DndService } from "../../../../common/dnd/dnd.service";
import { GraphTypes } from "../../../../common/graph-types";
import { StyleConstants } from "../../../../common/util/style-constants";
import { Tool } from "../../../../../../../shared/util/tool";
import { AestheticInfo } from "../../../data/chart/aesthetic-info";
import { ChartAggregateRef } from "../../../data/chart/chart-aggregate-ref";
import { ChartEditorService } from "../../../services/chart/chart-editor.service";
import { GraphUtil } from "../../../util/graph-util";
import { AestheticFieldMc } from "./aesthetic-field-mc";
import { UIContextService } from "../../../../common/services/ui-context.service";

@Component({
   selector: "shape-field-mc",
   templateUrl: "shape-field-mc.component.html",
   styleUrls: ["aesthetic-field-mc.scss"],
})
export class ShapeFieldMc extends AestheticFieldMc {
   constructor(editorService: ChartEditorService, dservice: DndService,
               protected uiContextService: UIContextService)
   {
      super(editorService, dservice, uiContextService);
   }

   protected getField(): AestheticInfo {
      if(this.aggr) {
         return this.aggr.shapeField;
      }

      return this.bindingModel ? this.bindingModel.shapeField : null;
   }

   get isLineType(): boolean {
      return GraphTypes.supportsLine(this.getChartType(), this.bindingModel);
   }

   get isTextureType(): boolean {
      return GraphTypes.supportsTexture(this.getChartType()) && !this.isMilestoneField;
   }

   get isMilestoneField(): boolean {
      return GraphTypes.isGantt(this.getChartType()) &&
         Tool.isEquals(this.aggr, this.bindingModel?.milestoneField);
   }

   protected getFrames(): VisualFrameModel[] {
      let _frames: VisualFrameModel[] = [];

      if(!this.bindingModel || !this._isEnabled) {
         return _frames;
      }

      let shapeField: AestheticInfo = this.field;

      if(shapeField) {
         _frames.push(shapeField.frame);
         return _frames;
      }

      if(this.aggr) {
         if(!this.isMixedField() && !this.isMixedChartType()) {
            if(this.isLineType && !this.aggr.lineFrame) {
               let lmodel: StaticLineModel = new StaticLineModel();
               lmodel.line = StyleConstants.THIN_LINE;
               this.aggr.lineFrame = lmodel;
            }
            else if(this.isTextureType && !this.aggr.textureFrame) {
               let tmodel: StaticTextureModel = new StaticTextureModel();
               tmodel.texture = StyleConstants.PATTERN_NONE;
               this.aggr.textureFrame = tmodel;
            }
            else if(!this.aggr.shapeFrame) {
               let smodel: StaticShapeModel = new StaticShapeModel();
               smodel.shape = StyleConstants.FILLED_CIRCLE + "";
               this.aggr.shapeFrame = smodel;
            }

            _frames.push(this.isLineType ? this.aggr.lineFrame : this.isTextureType ?
               this.aggr.textureFrame : this.aggr.shapeFrame);
         }

         if(GraphUtil.isWaterfall(this.bindingModel)) {
            _frames.push(this.aggr.summaryTextureFrame);
         }

         return _frames;
      }

      if(GraphTypes.isRadar(this.bindingModel.chartType) &&
        !(this.bindingModel.pointLine && GraphTypes.CHART_RADAR === this.bindingModel.chartType))
      {
         _frames.push(this.bindingModel.lineFrame);
         return _frames;
      }

      if(!GraphTypes.isMap(this.bindingModel.chartType)) {
         let arr: any[] = GraphUtil.getVisualFrames("shapeFrame", this.bindingModel);

         for(let item of arr) {
            _frames.push(<VisualFrameModel> item.frame);
         }
      }

      if(!GraphUtil.isWaterfall(this.bindingModel) || _frames.length == 0) {
         if(_frames.length == 0 && this.getBindingRefs().length > 0) {
            _frames.push(this.isLineType ? this.bindingModel.lineFrame :
               this.isTextureType ? this.bindingModel.textureFrame :
               this.bindingModel.shapeFrame);
         }
      }

      return _frames;
   }

   protected isMixed(): boolean {
      let frameFunc: string = "shapeFrame";

      if(this.isLineType) {
         frameFunc = "lineFrame";
      }
      else if(this.isTextureType) {
         frameFunc = "textureFrame";
      }

      return GraphUtil.isAllChartAggregate(this.aggr) &&
         (GraphTypes.isGantt(this.getChartType()) && !!this.bindingModel.milestoneField ||
            super.isMixedValue("rtchartType") ||
         super.isMixedValue("shapeField") || super.isMixedValue(frameFunc));
   }

   isMixedChartType(): boolean {
      return GraphUtil.isAllChartAggregate(this.aggr) && this.isMixedValue("rtchartType");
   }

   protected getEditPaneId(): string {
      if(!this.isEditEnabled() || !this.isEnabled() || !this.bindingModel) {
         return "";
      }

      if(this.field) {
         if(!this.field.dataInfo.measure ||
            this.field.dataInfo.classType == "aggregate" &&
            (<ChartAggregateRef> this.field.dataInfo).discrete)
         {
            return "CategoricalShape";
         }
         else {
            return this.isLineType ? "LinearLine" :
               this.isTextureType ? "LinearTexture" : "LinearShape";
         }
      }

      if(this.frames.length < 2) {
         return this.isLineType ? "StaticLine" :
               this.isTextureType ? "StaticTexture" : "StaticShape";
      }

      return "CombinedShape";
   }

   setAestheticRef(ref: AestheticInfo) {
      if(this.aggr) {
         this.aggr.shapeField = ref;
      }
      else {
         this.bindingModel.shapeField = ref;
      }
   }

   syncAllChartAggregateInfo(): void {
      if(!this.isEditEnabled) {
         return;
      }

      let aggrs: ChartAggregateRef[] = GraphUtil.getAestheticAggregateRefs(this.bindingModel);

      for(let aggr0 of aggrs) {
         aggr0.shapeField = Tool.clone(this.aggr.shapeField);

         if(this.isLineType) {
            aggr0.lineFrame = Tool.clone(this.aggr.lineFrame);
         }
         else if(this.isTextureType) {
            aggr0.textureFrame = Tool.clone(this.aggr.textureFrame);
            aggr0.summaryTextureFrame = Tool.clone(this.aggr.summaryTextureFrame);
         }
         else {
            aggr0.shapeFrame = Tool.clone(this.aggr.shapeFrame);
         }
      }
   }

   getFieldType(): string {
      return "shape";
   }

   protected isEditEnabled(): boolean {
      if(!super.isEditEnabled()) {
         return false;
      }

      if(!GraphUtil.isAllChartAggregate(this.aggr)) {
         return true;
      }

      return !this.isMixedValue("rtchartType") &&
         !(GraphTypes.isGantt(this.getChartType()) && this.bindingModel.milestoneField);
   }

   get nilSupported(): boolean {
      return GraphUtil.isNilSupported(this.aggr, this.bindingModel);
   }

   openChanged(open: boolean): void {
      if(!this._isEditEnabled) {
         return;
      }

      super.openChanged(open);
   }
}
