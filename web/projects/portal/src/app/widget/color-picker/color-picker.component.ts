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

@Component({
   selector: "cp-color-picker",
   templateUrl: "color-picker.component.html",
   styleUrls: ["color-picker.component.scss"]
})
export class ColorPicker {
   @Input() color: string = "#000000";
   @Input() display: boolean = true;
   @Input() palette: ColorPalette = DefaultPalette.chart;
   @Input() recent: RecentColorPalette = null;
   @Input() enabled: boolean = true;
   @Input() label: string = null;
   @Input() isTableStyle: boolean = false;
   @Output() colorChanged: EventEmitter<string> = new EventEmitter<string>();
   @ViewChild(FixedDropdownDirective) dropdown: FixedDropdownDirective;

   selectColor(value: string): void {
      this.color = value;
      this.dropdown.close();
      this.colorChanged.emit(value);
   }

   toggleDropdown() {
      this.dropdown.toggleDropdown(null);
   }

   dialogOpened(opened: boolean) {
      if(this.dropdown) {
         this.dropdown.closeOnOutsideClick = !opened;
      }
   }

   get colorString(): string {
      let str: string = this.color;

      if(str && str.charAt(0) != "#") {
         let radix = str.startsWith("0x") ? 16 : 10;
         str = "000000" + (parseInt(str, radix) & 0xffffff).toString(16);
         str = "#" + str.substring(str.length - 6);
      }

      return str;
   }
}
