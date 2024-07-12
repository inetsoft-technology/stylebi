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
import { Component, EventEmitter, Input, Output, ViewChild } from "@angular/core";
import { FixedDropdownDirective } from "../fixed-dropdown/fixed-dropdown.directive";

@Component({
   selector: "dropdown-view",
   templateUrl: "dropdown-view.component.html",
   styleUrls: ["dropdown-view.component.scss"]
})
export class DropdownView {
   @Input() label: string;
   @Input() isOk: boolean = false;
   @Input() disabled: boolean = false;
   @Input() zIndex: number;
   @Output() onToggle = new EventEmitter<string>();
   @Output() closed = new EventEmitter<boolean>();
   @ViewChild(FixedDropdownDirective) dropdown: FixedDropdownDirective;

   public close(): void {
      this.dropdown.close();
   }

   public toggled(open: boolean): void {
      this.onToggle.emit(open.toString());

      if(!open) {
         this.closed.emit(true);
      }
   }
}
