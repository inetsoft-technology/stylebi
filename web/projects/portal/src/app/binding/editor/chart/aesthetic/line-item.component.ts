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
import { ChartConfig } from "../../../../common/util/chart-config";

@Component({
   selector: "line-item",
   template: "<i [ngClass]='iconSource'></i>"
})
export class LineItem {
   @Input() line: number;
   @Input() isLongerImage: boolean = false;
   lines: number[] = ChartConfig.M_LINE_STYLES;
   iconNames: string[] = [
      "shape-line-dash0-icon",
      "shape-line-dash1-icon",
      "shape-line-dash2-icon",
      "shape-line-dash3-icon",
      "shape-line-dash4-icon"];

   get iconSource(): string {
      let idx: number = this.lines.indexOf(this.line);
      let iconName: string = idx != -1 ? this.iconNames[idx] : null;

      return iconName + " icon-size-medium";
   }
}
