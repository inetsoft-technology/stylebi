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
import { Component } from "@angular/core";
import * as V from "../../../../common/data/visual-frame-model";
import { AestheticIconCell } from "./aesthetic-icon-cell";

@Component({
   selector: "shape-cell",
   templateUrl: "shape-cell.component.html"
})
export class ShapeCell extends AestheticIconCell {
   get isStaticShape(): boolean {
      return this.frameModel ?
         this.frameModel.clazz == (new V.StaticShapeModel()).clazz : false;
   }

   get isStaticTexture(): boolean {
      return this.frameModel ?
         this.frameModel.clazz == (new V.StaticTextureModel()).clazz : false;
   }

   get isStaticLine(): boolean {
      return this.frameModel ?
         this.frameModel.clazz == (new V.StaticLineModel()).clazz : false;
   }

   get isLinearShape(): boolean {
      if(!this.frameModel) {
         return false;
      }

      return this.frameModel.clazz == (new V.CategoricalShapeModel()).clazz ||
         this.frameModel.clazz == (new V.FillShapeModel()).clazz ||
         this.frameModel.clazz == (new V.OrientationShapeModel()).clazz ||
         this.frameModel.clazz == (new V.PolygonShapeModel()).clazz ||
         this.frameModel.clazz == (new V.TriangleShapeModel()).clazz;
   }

   get isLinearTexture(): boolean {
      if(!this.frameModel) {
         return false;
      }

      return this.frameModel.clazz == (new V.CategoricalTextureModel()).clazz ||
         this.frameModel.clazz == (new V.GridTextureModel()).clazz ||
         this.frameModel.clazz == (new V.LeftTiltTextureModel()).clazz ||
         this.frameModel.clazz == (new V.OrientationTextureModel()).clazz ||
         this.frameModel.clazz == (new V.RightTiltTextureModel()).clazz;
   }

   get isLinearLine(): boolean {
      if(!this.frameModel) {
         return false;
      }

      return this.frameModel.clazz == (new V.CategoricalLineModel()).clazz ||
         this.frameModel.clazz == (new V.LinearLineModel()).clazz;
   }
}
