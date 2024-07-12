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
import { Component, Input, ViewChild } from "@angular/core";
import { LinePropPaneModel } from "../../data/vs/line-prop-pane-model";
import { ComboMode } from "../../../widget/dynamic-combo-box/dynamic-combo-box-model";
import { DynamicComboBox } from "../../../widget/dynamic-combo-box/dynamic-combo-box.component";

@Component({
   selector: "line-prop-pane",
   templateUrl: "line-prop-pane.component.html",
})
export class LinePropPane {
   @Input() model: LinePropPaneModel;
   @Input() variables: string[];
   @Input() vsId: string = null;
   @ViewChild("colorType") colorType: DynamicComboBox;

   fixColor(type: ComboMode): void {
      if(type == ComboMode.VALUE) {
         this.model.color = "Static";

         if(!this.model.colorValue) {
            this.model.colorValue = "#000000";
         }
      }
   }

   colorChange(clr: string): void {
      this.model.color = clr;
   }
}
