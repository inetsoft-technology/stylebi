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
import { ColorFieldPane } from "../../../widget/color-field-pane.component";
import { FixedDropdownDirective } from "../../../../widget/fixed-dropdown/fixed-dropdown.directive";

@Component({
   selector: "static-color-editor",
   templateUrl: "static-color-editor.component.html",
   styleUrls: ["static-color-editor.component.scss", "combined-visual-pane.scss"],
})
export class StaticColorEditor {
   @Input() aggrName: string;
   @Input() color: string;
   @Input() index: number;
   @Input() isDisabled: boolean = false;
   @Input() leftGroupButton: boolean = false;
   @Input() rightGroupButton: boolean = false;
   @Output() colorChanged: EventEmitter<string> = new EventEmitter<string>();
   @ViewChild(FixedDropdownDirective) dropdown: FixedDropdownDirective;

   changeColor(ncolor: string) {
      this.colorChanged.emit(ncolor);

      if(this.dropdown) {
         this.dropdown.close();
      }
   }

   getTooltip(index: number): string {
      return isNaN(index) ? "" : (index + 1) + "";
   }
}
