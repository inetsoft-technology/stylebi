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
import { LineEndStyles } from "./line-arrow-type-dropdown.enum";

@Component({
   selector: "line-arrow-type-dropdown",
   templateUrl: "line-arrow-type-dropdown.component.html",
   styleUrls: ["line-arrow-type-dropdown.component.scss"]
})
export class LineArrowTypeDropdown {
   @Input() style: number;
   @Input() color: string;
   @Output() styleChange: EventEmitter<number> = new EventEmitter<number>();
   LineEndStyles = LineEndStyles;
   lineStyles: string[] = [
      LineEndStyles[LineEndStyles.NONE],
      LineEndStyles[LineEndStyles.SOLID_ARROW],
      LineEndStyles[LineEndStyles.OPEN_ARROW],
      LineEndStyles[LineEndStyles.CLOSED_ARROW]
   ];

   choose(lineStyle: string): void {
      this.style = LineEndStyles[lineStyle];
      this.styleChange.emit(this.style);
   }
}
