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
import { Component, Input, ViewChild , OnInit } from "@angular/core";
import { FillPropPaneModel } from "../../data/vs/fill-prop-pane-model";
import { ComboMode } from "../../../widget/dynamic-combo-box/dynamic-combo-box-model";
import { DynamicComboBox } from "../../../widget/dynamic-combo-box/dynamic-combo-box.component";
import { DefaultPalette } from "../../../widget/color-picker/default-palette";
import { VSShape } from "../../../vsobjects/objects/shape/vs-shape";
import { Tool } from "../../../../../../shared/util/tool";

@Component({
   selector: "fill-prop-pane",
   templateUrl: "fill-prop-pane.component.html",
})
export class FillPropPane implements OnInit {
   @Input() model: FillPropPaneModel;
   @Input() variables: string[];
   @Input() vsId: string = null;
   alphaInvalid: boolean = false;
   fillColors = DefaultPalette.bgWithTransparent;
   @ViewChild("colorType") colorType: DynamicComboBox;

   ngOnInit() {
      if(!this.model.gradientColor) {
         this.model.gradientColor = {
            direction: "linear",
            colors: [],
            angle: VSShape.GRADIENTCOLOR_DEFAULT_ANGLE,
            apply: false
         };
      }
   }

   fixColor(type: ComboMode): void {
      if(type == ComboMode.VALUE) {
         this.model.color = "Static";

         if(!this.model.colorValue) {
            this.model.colorValue = "#ffffff";
         }
      }
      else {
         this.model.gradientColor.apply = false;
      }
   }

   colorChange(clr: string): void {
      this.model.color = clr;
   }

   changeAlphaWarning(event){
      this.alphaInvalid = event;
   }

   get gradientEnabled(): boolean {
      return !Tool.isDynamic(this.model.color);
   }
}
