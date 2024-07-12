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
import { Component, Input, Output, EventEmitter } from "@angular/core";
import { StyleConstants } from "../../common/util/style-constants";


@Component({
   selector: "alignment-combo",
   templateUrl: "alignment-combo.component.html"
})
export class AlignmentCombo {
   @Input() horizontal: boolean;
   @Input() alignment: number;
   @Output() alignmentChanged: EventEmitter<number> = new EventEmitter<number>();

   alignmentChange() {
      this.alignmentChanged.emit(this.alignment);
   }

   get alignOptions(): { label: string; data: number; }[] {
      if(this.horizontal) {
         return [
            {label: "_#(js:Auto)", data: 0},
            {label: "_#(js:Left)", data: StyleConstants.H_LEFT},
            {label: "_#(js:Center)", data: StyleConstants.H_CENTER},
            {label: "_#(js:Right)", data: StyleConstants.H_RIGHT},
         ];
      }else {
         return [
            {label: "_#(js:Auto)", data: 0},
            {label: "_#(js:Top)", data: StyleConstants.V_TOP},
            {label: "_#(js:Middle)", data: StyleConstants.V_CENTER},
            {label: "_#(js:Bottom)", data: StyleConstants.V_BOTTOM},
         ];
      }
   }
}
