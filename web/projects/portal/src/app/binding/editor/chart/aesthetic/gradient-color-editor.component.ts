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
import { GradientColorModel } from "../../../../common/data/visual-frame-model";

@Component({
   selector: "gradient-color-editor",
   templateUrl: "gradient-color-editor.component.html",
   styleUrls: ["color-editor.scss"]
})

export class GradientColorEditor {
   @Input() frame: GradientColorModel;
   @Input() isDisabled: boolean;
   @Output() colorChanged: EventEmitter<any> = new EventEmitter<any>();

   get fromColor() {
      return this.frame.fromColor != null ? this.frame.fromColor :
         this.frame.cssFromColor != null ? this.frame.cssFromColor :
            this.frame.defaultFromColor;
   }

   get toColor() {
      return this.frame.toColor != null ? this.frame.toColor :
         this.frame.cssToColor != null ? this.frame.cssToColor :
            this.frame.defaultToColor;
   }

   changeColor(ncolor: string, isFromColor: boolean) {
      if(this.frame && isFromColor) {
         this.frame.fromColor = ncolor;
      }
      else if(this.frame) {
         this.frame.toColor = ncolor;
      }

      this.colorChanged.emit();
   }
}
