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
import { Component, Input, Output } from "@angular/core";
import { ChartConfig } from "../../../../common/util/chart-config";

@Component({
   selector: "texture-item",
   template: "<i [ngClass] = \"iconSource + ' align-middle'\"></i>"
})
export class TextureItem {
   @Input() texture: number;
   @Input() isLongerImage: boolean = true;
   textures: number[] = ChartConfig.TEXTURE_STYLES;
   iconsAll: string[] = [
       "shape-pattern-solid-icon", "shape-pattern-R60-thin-dense-icon",
       "shape-pattern-cross-thin-dense-icon", "shape-pattern-L30-thin-sparse-icon",
       "shape-pattern-L30-thick-dense-icon", "shape-pattern-R30-thin-sparse-icon",
       "shape-pattern-cross-thick-sparse-icon", "shape-pattern-R30-thin-dense-icon",
       "shape-pattern-L30-thick-sparse-icon", "shape-pattern-L60-thin-sparse-icon",
       "shape-pattern-R60-thick-dense-icon", "shape-pattern-R60-thin-sparse-icon",
       "shape-pattern-L60-thick-dense-icon", "shape-pattern-L30-thin-dense-icon",
       "shape-pattern-cross-thick-dense-icon", "shape-pattern-R30-thick-dense-icon",
       "shape-pattern-R30-thick-sparse-icon", "shape-pattern-cross-thin-sparse-icon",
       "shape-pattern-L60-thick-sparse-icon", "shape-pattern-L60-thin-dense-icon",
       "shape-pattern-R60-thick-sparse-icon"
   ];

   get iconSource(): string {
      let iconSrc: string;

      if(this.texture == -1) {
         iconSrc = "shape-pattern-solid-icon";
      }
      else {
         for(let i = 0; i < this.textures.length; i++) {
            if(this.textures[i] == this.texture) {
               iconSrc = this.iconsAll[i];
               break;
            }
         }
      }

      return iconSrc + " icon-size-medium";
   }
}
