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
import { ChartConfig } from "../../../../common/util/chart-config";
import { StaticLineModel } from "../../../../common/data/visual-frame-model";
import { StyleConstants } from "../../../../common/util/style-constants";

@Component({
   selector: "static-line-pane",
   templateUrl: "static-line-pane.component.html",
   styleUrls: ["static-line-pane.component.scss"]
})
export class StaticLinePane {
   @Input() line: number;
   @Input() frameModel: StaticLineModel;
   @Output() lineChanged: EventEmitter<number> = new EventEmitter<number>();

   readonly lineStyleCssClasses = {
      [StyleConstants.THIN_LINE]: "line-style-THIN_LINE",
      [StyleConstants.DOT_LINE]: "line-style-DOT_LINE",
      [StyleConstants.DASH_LINE]: "line-style-DASH_LINE",
      [StyleConstants.MEDIUM_DASH]: "line-style-MEDIUM_DASH",
      [StyleConstants.LARGE_DASH]: "line-style-LARGE_DASH"
   };

   get lines(): number[] {
      return ChartConfig.M_LINE_STYLES;
   }

   get selectedLine(): number {
      if(this.frameModel) {
         return this.frameModel.line;
      }

      return this.line;
   }

   selectLine(nline: number) {
      if(this.frameModel) {
         this.frameModel.line = nline;
      }

      this.line = nline;
      this.lineChanged.emit(nline);
   }
}
