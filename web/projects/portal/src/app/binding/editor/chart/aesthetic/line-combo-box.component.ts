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
import { Component, Input, Output, EventEmitter } from "@angular/core";
import { StaticLinePane } from "./static-line-pane.component";
import { LineItem } from "./line-item.component";
import { FixedDropdownDirective } from "../../../../widget/fixed-dropdown/fixed-dropdown.directive";

@Component({
    selector: "line-combo-box",
    templateUrl: "line-combo-box.component.html",
    styleUrls: ["visual-combo-box-trigger.scss"],

    imports: [FixedDropdownDirective, LineItem, StaticLinePane]
})
export class LineComboBox {
   @Input() index: number;
   @Input() line: number;
   @Output() lineChanged: EventEmitter<number> = new EventEmitter<number>();
   open: boolean = false;

   changeLine(nline: number) {
      this.lineChanged.emit(nline);
   }

   getTooltip(): string {
      return isNaN(Number(this.index)) ? "" : (this.index + 1) + "";
   }
}
