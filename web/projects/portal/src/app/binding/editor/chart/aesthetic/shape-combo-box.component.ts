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
import { Component, Input, Output, EventEmitter, ViewChild } from "@angular/core";
import { FixedDropdownDirective } from "../../../../widget/fixed-dropdown/fixed-dropdown.directive";

@Component({
   selector: "shape-combo-box",
   templateUrl: "shape-combo-box.component.html"
})
export class ShapeComboBox {
   @Input() shapeStr: string;
   @Input() index: number;
   @Output() shapeChanged: EventEmitter<string> = new EventEmitter<string>();
   @ViewChild(FixedDropdownDirective) dropdown: FixedDropdownDirective;

   changeShape(nshape: string) {
      this.shapeChanged.emit(nshape);

      if(this.dropdown) {
         this.dropdown.close();
      }
   }

   getTitle() {
      if(this.index != null) {
         return this.index + 1 + "";
      }

      return "";
   }
}
