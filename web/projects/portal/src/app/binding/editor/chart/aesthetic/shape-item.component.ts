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
import { Component, Input } from "@angular/core";
import { ChartConfig } from "../../../../common/util/chart-config";
import { StyleConstants } from "../../../../common/util/style-constants";

@Component({
   selector: "shape-item",
   template: `<img *ngIf="shapeClass == null && shapeSource != null && !builtin"
                   [src]='shapeSource'/>
   <i *ngIf="shapeClass == null && shapeSource != null && builtin" [ngClass]='shapeSourceIcon'></i>
   <i *ngIf="shapeClass != null" [ngClass]='shapeClass'></i>`
})
export class ShapeItem {
   @Input() shapeStr: string;
   @Input() isBiggerImage: boolean = false;
   iconNames: string[] = [
      "shape-circle-icon",
      "shape-triangle-icon",
      "shape-square-icon",
      "shape-plus-icon",
      "shape-asterisk-icon",
      "shape-diamond-icon",
      "shape-cross-icon",
      "shape-filled-circle-icon",
      "shape-filled-triangle-icon",
      "shape-filled-square-icon",
      "shape-filled-diamond-icon",
      "shape-V-icon",
      "shape-L-icon",
      "shape-V-rotate-icon",
      "shape-vertical-bar-icon",
      "shape-horizontal-bar-icon"
   ];

   builtinShapeSourceIcons = {
      "100ArrowDown.svg": "shape-arrow-down-bold-icon",
      "101ArrowUp.svg": "shape-arrow-up-bold-icon",
      "102Check.svg": "shape-check-icon",
      "103Cancel.svg": "shape-cancel-icon",
      "104Exclamation.svg": "shape-exclamation-icon",
      "105Flag.svg": "shape-flag-icon",
      "106Light.svg": "shape-light-icon",
      "107Star.svg": "shape-star-icon",
      "108No.svg": "shape-no-icon",
      "109Man.svg": "shape-man-icon",
      "110Woman.svg": "shape-woman-icon",
      "111FaceHappy.svg": "shape-face-happy-icon",
      "112FaceSad.svg": "shape-face-sad-icon",
      "113Face.svg": "shape-face-icon",
      "114ArrowUperRight.svg": "shape-arrow-upper-right-icon",
      "115ArrowLowerRight.svg": "shape-arrow-lower-right-icon"
   };

   get shapeClass(): string {
      if(this.shapeStr == StyleConstants.NIL + "") {
         return "";
      }

      if(!this.shapeStr) {
         return null;
      }

      try {
         let idx = parseInt(this.shapeStr, 10);
         idx = idx - StyleConstants.CIRCLE;

         if(idx >= 0 && idx < 16) {
            return this.iconNames[idx];
         }

         //if it is not a valid source string and is not in range, then provide a default value
         if(/^[0-9]+$/.test(this.shapeStr)) {
            return this.iconNames[0];
         }
      }
      catch(ex) {
         // ignored
      }

      return null;
   }

   get shapeSource(): string {
      return ChartConfig.getShapeSource(this.shapeStr, this.isBiggerImage);
   }

   get shapeSourceIcon(): string {
      return this.builtinShapeSourceIcons[this.shapeStr];
   }

   get builtin(): boolean {
      return ChartConfig.IMAGE_SHAPES.indexOf(this.shapeStr) >= 0;
   }
}
