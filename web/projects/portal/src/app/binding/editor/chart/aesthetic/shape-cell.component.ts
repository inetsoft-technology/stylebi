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
import { NgTemplateOutlet } from "@angular/common";
import * as V from "../../../../common/data/visual-frame-model";
import { AestheticIconCell } from "./aesthetic-icon-cell";
import { LineItem } from "./line-item.component";
import { TextureItem } from "./texture-item.component";
import { ShapeItem } from "./shape-item.component";


@Component({
    selector: "shape-cell",
    templateUrl: "shape-cell.component.html",
    imports: [ShapeItem, TextureItem, LineItem, NgTemplateOutlet],
    // The icon font puts the built-in shapes about 1px above the middle of their 20px box (the
    // ink has 0.5px above and 1.5px below), so in the tile they sat high. Move them down 1px. The
    // static line and texture frames are drawn by the same font and sat 1px high in the same way
    // (4px above the frame, 6px below, measured in the tile).
    styles: [
       ".shape-cell__glyph { display: block; }",
       ":host ::ng-deep shape-item > i, :host ::ng-deep line-item > i, " +
          ":host ::ng-deep texture-item > i { position: relative; top: 1px; }"
    ]
})
export class ShapeCell extends AestheticIconCell {
   /**
    * Names the state the glyph stands for, so a from-data or mixed tile is not just a generic
    * picture: "Edit Shape (Shape from data)". Null for a frame this tile does not recognise.
    */
   get tooltip(): string {
      let state: string = null;

      if(this.isStaticShape) {
         state = this.isMixed ? "_#(js:Mixed shapes)" : "_#(js:Static shape)";
      }
      else if(this.isLinearShape) {
         state = "_#(js:Shape from data)";
      }
      else if(this.isStaticTexture) {
         state = this.isMixed ? "_#(js:Mixed textures)" : "_#(js:Static texture)";
      }
      else if(this.isLinearTexture) {
         state = "_#(js:Texture from data)";
      }
      else if(this.isStaticLine) {
         state = this.isMixed ? "_#(js:Mixed lines)" : "_#(js:Static line)";
      }
      else if(this.isLinearLine) {
         state = "_#(js:Line from data)";
      }

      return state ? "_#(js:Edit Shape) (" + state + ")" : null;
   }

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
