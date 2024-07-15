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
import { Component, Output, EventEmitter } from "@angular/core";
import { DefaultPalette } from "../../../../widget/color-picker/default-palette";
import { AbstractCombinedPane } from "./abstract-combined-pane";

@Component({
   selector: "combined-color-pane",
   templateUrl: "combined-color-pane.component.html",
   styleUrls: ["combined-visual-pane.scss"],
})
export class CombinedColorPane extends AbstractCombinedPane {
   @Output() colorChanged: EventEmitter<string> = new EventEmitter<string>();

   changeColor(ncolor: string, idx: number) {
      if(this.frameInfos) {
         this.frameInfos[idx].frame.color = ncolor;
         this.colorChanged.emit(ncolor);
      }
   }

   reset() {
      const allColors: string[] = DefaultPalette.chart.flat();

      for(var i = 0; i < this.frameInfos.length; i++) {
         this.frameInfos[i].frame.changed = false;

         if(this.frameInfos[i].summary && this.frameInfos[i].frame.defaultColor) {
            this.frameInfos[i].frame.color = this.frameInfos[i].frame.defaultColor;
         }
         else {
            this.frameInfos[i].frame.color = allColors[i % allColors.length];
         }
      }
   }
}
