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
import { Component, EventEmitter, Input, Output, OnInit, TemplateRef,
         ViewChild } from "@angular/core";
import { NgbModal, NgbModalOptions } from "@ng-bootstrap/ng-bootstrap";
import { ColorPalette, RecentColorPalette } from "./color-classes";
import { DefaultPalette } from "./default-palette";
import { RecentColorService } from "./recent-color.service";
import { getColorHex } from "./color-utils";
import { FixedDropdownDirective } from "../fixed-dropdown/fixed-dropdown.directive";

@Component({
   selector: "gradient-color-item",
   templateUrl: "gradient-color-item.component.html",
   styleUrls: ["gradient-color-item.component.scss"]
})
export class GradientColorItem {
   @Input() height: number = 10;
   @Input() width: number = 10;
   @Input() background: string;
   @Input() disabled: boolean = false;
   @Output() gradientColorChanged: EventEmitter<string> = new EventEmitter<string>();
   @ViewChild(FixedDropdownDirective) dropdown: FixedDropdownDirective;

   selectColor(color: string) {
      this.background = color;
      this.dropdown.close();
      this.gradientColorChanged.emit(color);
   }
}
