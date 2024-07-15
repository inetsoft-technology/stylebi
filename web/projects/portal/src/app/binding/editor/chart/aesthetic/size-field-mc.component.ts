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
import { Component, OnChanges, SimpleChanges } from "@angular/core";
import { ChartRef } from "../../../../common/data/chart-ref";
import { StaticSizeModel, VisualFrameModel } from "../../../../common/data/visual-frame-model";
import { DndService } from "../../../../common/dnd/dnd.service";
import { GraphTypes } from "../../../../common/graph-types";
import { UIContextService } from "../../../../common/services/ui-context.service";
import { Tool } from "../../../../../../../shared/util/tool";
import { AestheticInfo } from "../../../data/chart/aesthetic-info";
import { ChartAggregateRef } from "../../../data/chart/chart-aggregate-ref";
import { ChartEditorService } from "../../../services/chart/chart-editor.service";
import { GraphUtil } from "../../../util/graph-util";
import { AestheticFieldMc } from "./aesthetic-field-mc";

@Component({
   selector: "size-field-mc",
   templateUrl: "size-field-mc.component.html",
   styleUrls: ["aesthetic-field-mc.scss"]
})
export class SizeFieldMc extends AestheticFieldMc implements OnChanges {
   chartRefs: ChartRef[];

   constructor(editorService: ChartEditorService, dservice: DndService,
               protected uiContextService: UIContextService)
   {
      super(editorService, dservice, uiContextService);
      this.chartRefs = GraphUtil.getModelRefs(this.editorService.bindingModel);
   }

   ngOnChanges(changes: SimpleChanges) {
      super.ngOnChanges(changes);
      this.chartRefs = GraphUtil.getModelRefs(this.editorService.bindingModel);
   }

   protected getField(): AestheticInfo {
      let aesInfo: AestheticInfo = null;

      if(this.aggr) {
         aesInfo = this.aggr.sizeField;
      }
      else if(this.bindingModel) {
         aesInfo = this.bindingModel[this.targetField || "sizeField"];
      }

      return aesInfo;
   }

   protected isMixed(): boolean {
      return GraphUtil.isAllChartAggregate(this.aggr) &&
         (super.isMixedValue("sizeField") || super.isMixedValue("sizeFrame"));
   }

   protected getFrames(): VisualFrameModel[] {
      let _frames: VisualFrameModel[] = [];

      if(!this.bindingModel) {
         return _frames;
      }

      if(this.field) {
         _frames.push(this.field.frame);
      }
      else if(this.aggr) {
         // add sizeframe for allAggregate when mixed static sizeframe,
         // to support edit size for allAggregate.
         if(!this.isMixedField() && (!this.aggr.sizeFrame || this.isMixedValue("sizeFrame"))) {
            let smodel: StaticSizeModel = new StaticSizeModel();
            smodel.size = 1;
            this.aggr.sizeFrame = smodel;
         }

         _frames.push(this.aggr.sizeFrame);
      }
      else {
         if(GraphTypes.isRadar(this.bindingModel.chartType) ||
            GraphTypes.isContour(this.bindingModel.chartType))
         {
            _frames.push(this.bindingModel.sizeFrame);
            return _frames;
         }

         let arr: any[] = GraphUtil.getVisualFrames("sizeFrame", this.bindingModel);

         for(let item of arr) {
            _frames.push(<VisualFrameModel> item.frame);
         }

         if(!GraphUtil.isWaterfall(this.bindingModel) || _frames.length == 0) {
            if(_frames.length == 0 && this.getBindingRefs().length > 0) {
               _frames.push(this.bindingModel[this.targetFrame || "sizeFrame"]);
            }
         }
      }

      return _frames;
   }

   isFrameEditEnabled(): boolean {
      if(!super.isEditEnabled()) {
         return false;
      }

      if(GraphTypes.isTreemap(this.bindingModel.chartType)) {
         if(this.field == null || this.field.frame == null ||
            this.field.frame.clazz == "inetsoft.web.binding.model.graph.aesthetic.LinearSizeModel" ||
            this.field.frame.clazz == "inetsoft.web.binding.model.graph.aesthetic.StaticSizeModel")
         {
            return false;
         }
      }

      return true;
   }

   protected getEditPaneId(): string {
      if(!this.isEditEnabled() || !this.isEnabled() || !this.bindingModel) {
         return "";
      }

      if(this.field != null) {
         return "BindingSize";
      }
      else if(this.bindingModel.multiStyles && !this.frames || this.frames.length < 2) {
         return "StaticSize";
      }

      return "CombinedSize";
   }

   setAestheticRef(ref: AestheticInfo) {
      if(this.aggr) {
         this.aggr.sizeField = ref;
      }
      else {
         this.bindingModel[this.targetField || "sizeField"] = ref;
      }
   }

   syncAllChartAggregateInfo(): void {
      if(!this.isEditEnabled) {
         return;
      }

      let aggrs: ChartAggregateRef[] = GraphUtil.getAestheticAggregateRefs(this.bindingModel);

      for(let aggr0 of aggrs) {
         aggr0.sizeField = Tool.clone(this.aggr.sizeField);
         aggr0.sizeFrame = Tool.clone(this.aggr.sizeFrame);
      }
   }

   getFieldType(): string {
      return "size";
   }

   isInterval(): boolean {
      return GraphTypes.isInterval(this.aggr ? this.aggr.rtchartType : this.bindingModel.chartType);
   }

   getHint(): string {
      return this.isInterval() ? "_#(js:Interval Column)" : null;
   }

   isTreemap(): boolean {
      return GraphTypes.isTreemap(this.bindingModel.chartType);
   }
}
