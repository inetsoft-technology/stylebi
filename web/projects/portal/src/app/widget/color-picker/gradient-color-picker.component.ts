/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
import { Component, EventEmitter, Input, Output, TemplateRef, ViewChild,
         ElementRef } from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { ColorPalette, RecentColorPalette } from "./color-classes";
import { DefaultPalette } from "./default-palette";
import { RecentColorService } from "./recent-color.service";
import { ColorPane } from "./cp-color-pane.component";
import { DropdownRef } from "../fixed-dropdown/fixed-dropdown-ref";
import { DropdownOptions } from "../fixed-dropdown/dropdown-options";
import { FixedDropdownDirective } from "../fixed-dropdown/fixed-dropdown.directive";
import { GradientColor } from "../../common/data/base-format-model";

@Component({
   selector: "gradient-color-picker",
   templateUrl: "gradient-color-picker.component.html",
   styleUrls: ["gradient-color-picker.component.scss"]
})
export class GradientColorPicker {
   @Input() gradientColor: GradientColor;
   @Input() display: boolean = true;
   @Input() enabled: boolean = true;
   @Input() label: string = null;
   @Output() gradientColorChanged: EventEmitter<GradientColor> = new EventEmitter<GradientColor>();
   @ViewChild(FixedDropdownDirective) dropdown: FixedDropdownDirective;

   selectColor(value: GradientColor): void {
      this.gradientColor = value;
      this.gradientColorChanged.emit(value);
   }

   get background(): string {
      if(!this.gradientColor || !this.gradientColor.colors) {
         return null;
      }

      if(this.gradientColor.colors.length == 1) {
         return this.gradientColor.colors[0].color;
      }

      let bg: string = this.gradientColor.direction == "radial" ? "radial-gradient(circle ," :
         "linear-gradient(90deg,";

      this.gradientColor.colors.forEach((color) => {
         bg += (color.color + " " + color.offset + "%,");
      });

      bg = bg.slice(0, bg.length - 1);

      return bg + ")";
   }
}
