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
import { Component, Input, OnChanges, SimpleChanges } from "@angular/core";
import { DefaultPalette } from "../../../widget/color-picker/default-palette";
import { ValueMode } from "../../../widget/dynamic-combo-box/dynamic-combo-box-model";
import { RangePaneModel } from "../../data/vs/range-pane-model";
import { RangePaneValueModel } from "../../data/vs/range-pane-value-model";

@Component({
   selector: "range-pane",
   templateUrl: "range-pane.component.html",
})
export class RangePane implements OnChanges {
   @Input() model: RangePaneModel;
   @Input() variables: string[];
   @Input() vsId: string = null;
   @Input() mode: ValueMode = ValueMode.TEXT;
   @Input() targetSupported: boolean = false;
   @Input() gradientSupported;
   @Input() numRangesToDisplay;
   values: RangePaneValueModel[];
   palette = DefaultPalette.bgWithTransparent;

   ngOnChanges(changes: SimpleChanges): void {
      if(changes.hasOwnProperty("model")) {
         if(this.model != null) {
            this.values = [];
            const numRangesToDisplay =
               Math.min(this.numRangesToDisplay, this.model.rangeValues.length) - 1;

            for(let i = numRangesToDisplay; i >= 0; i--) {
               this.values.push(new RangePaneValueModel(this.model, i));
            }
         }
         else {
            this.values = null;
         }
      }
   }

   getIndex(index: number): string {
      return (index + 1) + "";
   }
}
