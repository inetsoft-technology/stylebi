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
import { Input, Directive } from "@angular/core";
import {
   StaticLineModel,
   StaticShapeModel,
   StaticTextureModel,
   VisualFrameModel
} from "../../../../common/data/visual-frame-model";

@Directive()
export class AestheticIconCell {
   @Input() frameModel: VisualFrameModel;
   @Input() isMixed: boolean = false;
   @Input() cellWidth: number = 20;
   @Input() cellHeight: number = 20;

   get shapeModel(): StaticShapeModel {
      return this.frameModel as StaticShapeModel;
   }

   get textureModel(): StaticTextureModel {
      return this.frameModel as StaticTextureModel;
   }

   get lineModel(): StaticLineModel {
      return this.frameModel as StaticLineModel;
   }
}
