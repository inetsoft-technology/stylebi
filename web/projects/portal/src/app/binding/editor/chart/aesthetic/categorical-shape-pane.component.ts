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
import { Component, Input, ViewChild, Output, EventEmitter } from "@angular/core";
import { CategoricalFramePane } from "./categorical-frame-pane";
import { CategoricalLineModel, CategoricalShapeModel, CategoricalTextureModel, ShapeFrameModel,
         VisualFrameModel } from "../../../../common/data/visual-frame-model";
import { GraphUtil } from "../../../util/graph-util";
import { Tool } from "../../../../../../../shared/util/tool";
import { ChartConfig } from "../../../../common/util/chart-config";

@Component({
   selector: "categorical-shape-pane",
   templateUrl: "categorical-shape-pane.component.html",
   styleUrls: ["categorical-pane.scss"]
})
export class CategoricalShapePane extends CategoricalFramePane {
   @Input() frameModel: ShapeFrameModel;
   @Output() apply: EventEmitter<any> = new EventEmitter<any>();

   get shapeModel(): CategoricalShapeModel {
      return this.frameModel as CategoricalShapeModel;
   }

   get lineModel(): CategoricalLineModel {
      return this.frameModel as CategoricalLineModel;
   }

   get textureModel(): CategoricalTextureModel {
      return this.frameModel as CategoricalTextureModel;
   }

   reset() {
      if(this.type == GraphUtil.CHART_SHAPE) {
         (<CategoricalShapeModel> this.frameModel).shapes =
            ChartConfig.SHAPE_STYLES.concat(ChartConfig.IMAGE_SHAPES);
      }
      else if(this.type == GraphUtil.CHART_LINE) {
         (<CategoricalLineModel> this.frameModel).lines =
            ChartConfig.M_LINE_STYLES.concat(ChartConfig.M_LINE_STYLES);
      }
      else if(this.type == GraphUtil.CHART_TEXTURE) {
         (<CategoricalTextureModel> this.frameModel).textures =
            ChartConfig.TEXTURE_STYLES.slice(1);
      }
   }

   getNumItems(): number {
      if(!this.frameModel) {
         return 0;
      }

      if(this.type == GraphUtil.CHART_SHAPE) {
         let shapes: string[] = (<CategoricalShapeModel> this.frameModel).shapes;
         return shapes ? shapes.length : 0;
      }
      else if(this.type == GraphUtil.CHART_LINE) {
         let lines = (<CategoricalLineModel> this.frameModel).lines;
         return lines ? lines.length : 0;
      }
      else if(this.type == GraphUtil.CHART_TEXTURE) {
         let textures = (<CategoricalTextureModel> this.frameModel).textures;
         return textures ? textures.length : 0;
      }

      return 0;
   }

   get type(): string {
      if(!this.frameModel) {
         return "";
      }

      if(this.frameModel.clazz.indexOf("CategoricalShapeModel") != -1) {
         return GraphUtil.CHART_SHAPE;
      }
      else if(this.frameModel.clazz.indexOf("CategoricalLineModel") != -1) {
         return GraphUtil.CHART_LINE;
      }
      else if(this.frameModel.clazz.indexOf("CategoricalTextureModel") != -1) {
         return GraphUtil.CHART_TEXTURE;
      }

      return "";
   }

   changeShape(nvalue: any, idx: number) {
      if(!this.frameModel) {
         return;
      }

      if(this.type == GraphUtil.CHART_SHAPE) {
         (<CategoricalShapeModel> this.frameModel).shapes[idx] = nvalue;
      }
      else if(this.type == GraphUtil.CHART_LINE) {
         (<CategoricalLineModel> this.frameModel).lines[idx] = nvalue;
      }
      else if(this.type == GraphUtil.CHART_TEXTURE) {
         (<CategoricalTextureModel> this.frameModel).textures[idx] = nvalue;
      }
   }

   getFrame(): VisualFrameModel {
      return this.frameModel;
   }

   applyClick() {
      this.apply.emit(false);
   }
}
