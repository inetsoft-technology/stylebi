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
import { Component, EventEmitter, Input, Output } from "@angular/core";
import { NgbDropdown } from "@ng-bootstrap/ng-bootstrap";
import { ColorEditor } from "./color-editor.component";
import { ColorPalette } from "./color-classes";
import { DefaultPalette } from "./default-palette";

@Component({
   selector: "color-dropdown",
   templateUrl: "color-dropdown.component.html",
   styleUrls: ["color-dropdown.component.scss"]
})
export class ColorDropdown {
   @Input() color: string;
   @Input() transEnabled: boolean = false;
   @Input() isBg: boolean = false;
   @Input() enabled: boolean = true;
   @Input() label: string = null;
   @Input() chart: boolean = true;
   @Input() isTableStyle: boolean = false;
   @Output() colorChange = new EventEmitter<string>();
   static STATIC: string = "Static";

   changeColor(color: string) {
      this.color = color;
      this.colorChange.emit(this.color);
   }

   getPalette(): ColorPalette {
      if(!this.isBg && this.transEnabled) {
         return DefaultPalette.fgWithTransparent;
      }
      else if(!this.isBg && !this.transEnabled) {
         if(this.chart) {
            return DefaultPalette.chart;
         }
         else {
            return DefaultPalette.palette;
         }
      }
      else if(this.isBg && this.transEnabled) {
         return DefaultPalette.bgWithTransparent;
      }
      else if(this.isBg && !this.transEnabled) {
         return DefaultPalette.bgWithNoTransparent;
      }

      return null;
   }
}
