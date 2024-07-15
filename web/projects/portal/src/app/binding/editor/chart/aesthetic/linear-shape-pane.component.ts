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
import { Component, Input, Output, EventEmitter } from "@angular/core";
import { ShapeFrameModel, FillShapeModel, TriangleShapeModel, PolygonShapeModel,
         OrientationShapeModel } from "../../../../common/data/visual-frame-model";

@Component({
   selector: "linear-shape-pane",
   templateUrl: "linear-shape-pane.component.html",
   styleUrls: ["linear-shape-pane.component.scss"]
})
export class LinearShapePane {
   @Input() frameModel: ShapeFrameModel;
   @Output() frameModelChange: EventEmitter<any> = new EventEmitter<any>();

   get fillClazz(): string {
      return (new FillShapeModel()).clazz;
   }

   get triangleClazz(): string {
      return (new TriangleShapeModel()).clazz;
   }

   get polygonClazz(): string {
      return (new PolygonShapeModel()).clazz;
   }

   get orientationClazz(): string {
      return (new OrientationShapeModel()).clazz;
   }

   changeFrame(clz: string) {
      this.frameModel.clazz = clz;
      this.frameModelChange.emit(this.frameModel);
   }
}
