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
import { StyleConstants } from "../../common/util/style-constants";
import { NgFor } from "@angular/common";
import { FormsModule } from "@angular/forms";
import { CustomSelectOption, CustomSelectComponent } from "../../widget/custom-select/custom-select.component";


@Component({
    selector: "alignment-combo",
    templateUrl: "alignment-combo.component.html",
    standalone: true,
    imports: [FormsModule, NgFor, CustomSelectComponent]
})
export class AlignmentCombo {
   @Input() horizontal: boolean;
   @Input() alignment: number;
   @Output() alignmentChanged: EventEmitter<number> = new EventEmitter<number>();

   alignmentChange() {
      this.alignmentChanged.emit(this.alignment);
   }

   get alignOptions(): CustomSelectOption<number>[] {
      if(this.horizontal) {
         return [
            {label: "_#(js:Auto)", value: 0},
            {label: "_#(js:Left)", value: StyleConstants.H_LEFT},
            {label: "_#(js:Center)", value: StyleConstants.H_CENTER},
            {label: "_#(js:Right)", value: StyleConstants.H_RIGHT},
         ];
      }
      else {
         return [
            {label: "_#(js:Auto)", value: 0},
            {label: "_#(js:Top)", value: StyleConstants.V_TOP},
            {label: "_#(js:Middle)", value: StyleConstants.V_CENTER},
            {label: "_#(js:Bottom)", value: StyleConstants.V_BOTTOM},
         ];
      }
   }
}
