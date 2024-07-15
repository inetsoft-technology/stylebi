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
import { Component, Input, Output, EventEmitter } from "@angular/core";

@Component({
   selector: "line-combo-box",
   templateUrl: "line-combo-box.component.html"
})
export class LineComboBox {
   @Input() index: number;
   @Input() line: number;
   @Output() lineChanged: EventEmitter<number> = new EventEmitter<number>();

   changeLine(nline: number) {
      this.lineChanged.emit(nline);
   }

   getTooltip(): string {
      return isNaN(Number(this.index)) ? "" : (this.index + 1) + "";
   }
}
