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
import { Component, EventEmitter, Input, Output } from "@angular/core";
import { AbstractCombinedPane } from "./abstract-combined-pane";

@Component({
   selector: "combined-shape-pane",
   templateUrl: "combined-shape-pane.component.html",
   styleUrls: ["combined-visual-pane.scss"]
})
export class CombinedShapePane extends AbstractCombinedPane {
   @Input() isLineType: boolean = false;
   @Input() isTextureType: boolean = false;
   @Output() shapeChanged = new EventEmitter<void>();

   changeLine(nline: number, idx: number): void {
      if(this.frameInfos) {
         this.frameInfos[idx].frame.line = nline;
         this.shapeChanged.emit();
      }
   }

   changeShape(nshape: string, idx: number): void {
      if(this.frameInfos) {
         this.frameInfos[idx].frame.shape = nshape;
         this.shapeChanged.emit();
      }
   }

   changeTexture(ntexture: number, idx: number): void {
      if(this.frameInfos) {
         this.frameInfos[idx].frame.texture = ntexture;
         this.shapeChanged.emit();
      }
   }
}
