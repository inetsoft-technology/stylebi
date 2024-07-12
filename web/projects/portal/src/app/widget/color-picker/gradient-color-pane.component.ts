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
import {
   Component,
   EventEmitter,
   Input,
   Output,
   OnInit,
   TemplateRef,
   ViewChild
} from "@angular/core";
import { AssemblyActionGroup } from "../../common/action/assembly-action-group";
import { AssemblyAction } from "../../common/action/assembly-action";
import { FixedDropdownDirective } from "../fixed-dropdown/fixed-dropdown.directive";
import { GradientColor, ColorStop} from "../../common/data/base-format-model";
import { VSShape } from "../../vsobjects/objects/shape/vs-shape";

@Component({
   selector: "gradient-color-pane",
   templateUrl: "gradient-color-pane.component.html",
   styleUrls: ["gradient-color-pane.component.scss"]
})
export class GradientColorPane implements OnInit {
   @Input() gradientColor: GradientColor;
   @Input() angleIncrement: number = 45;
   @Output() gradientColorChanged: EventEmitter<GradientColor> = new EventEmitter<GradientColor>();
   @ViewChild(FixedDropdownDirective) dropdown: FixedDropdownDirective;
   startDrageLeft: number;
   editItem: ColorStop;
   selectedIndex: number;
   drage: boolean = false;
   supportedAngles: number[] = [];
   clearAction: AssemblyAction = {
      id: () => "delete",
      label: () => "_#(js:Delete)",
      icon: () => "remove-icon",
      enabled: () => true,
      visible: () => true,
      action: () => this.deleteColor()
   };
   actions: AssemblyActionGroup[] = [new AssemblyActionGroup([this.clearAction])];

   ngOnInit(): void {
      for(let angle = 0; angle < 360; angle += this.angleIncrement) {
         this.supportedAngles.push(angle);
      }
   }

   get colors(): ColorStop[] {
      if(!this.gradientColor) {
         this.gradientColor = {
            apply: false,
            direction: "",
            angle: VSShape.GRADIENTCOLOR_DEFAULT_ANGLE,
            colors: []
         };
      }

      return this.gradientColor.colors;
   }

   addColor() {
      this.gradientColor.colors.push({
         color: "#ffffff",
         offset: 50
      });

      this.sortColor();
   }

   selectColor(color: string, colorStop: ColorStop) {
      colorStop.color = color;
      this.gradientColorChanged.emit(this.gradientColor);
   }

   get background(): string {
      if(this.colors.length == 1 ) {
         return this.colors[0].color;
      }

      let bg: string = this.gradientColor.direction == "radial" ? "radial-gradient(circle ," :
         "linear-gradient(90deg,";

      this.colors.forEach((color) => {
         bg += (color.color + " " + color.offset + "%,");
      });

      bg = bg.slice(0, bg.length - 1);

      return bg + ")";
   }

   startDrage(event: any, colorStop: ColorStop, index: number) {
      if(event.button == 0) {
         this.editItem = colorStop;
         this.drage = false;
         this.startDrageLeft = event.clientX;
      }
      else if(event.button == 2){
         this.selectedIndex = index;
      }
   }

   move(event: MouseEvent) {
      if(this.editItem) {
         let offset = this.editItem.offset + (event.clientX - this.startDrageLeft) / 300 * 100;
         offset = offset > 100 ? 100 : offset;
         offset = offset < 0 ? 0 : offset;
         this.editItem.offset = offset;
         this.sortColor();
         this.drage = true;
         this.startDrageLeft = event.clientX;
      }
   }

   sortColor() {
      this.gradientColor.colors.sort((value1, value2) => {
         return (value1.offset - value2.offset - 3);
      });
   }

   endDrag() {
      this.editItem = null;
   }

   deleteColor() {
      this.gradientColor.colors.splice(this.selectedIndex, 1);
   }
}
