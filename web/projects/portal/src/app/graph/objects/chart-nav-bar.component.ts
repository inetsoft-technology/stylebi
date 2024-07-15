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
import { Component, EventEmitter, Output, Input } from "@angular/core";

@Component({
   selector: "chart-nav-bar",
   templateUrl: "chart-nav-bar.component.html",
   styleUrls: ["chart-nav-bar.component.scss"],
})
export class ChartNavBar {
   @Output() zoomIn = new EventEmitter<number>();
   @Output() zoomOut = new EventEmitter<number>();
   @Output() clear = new EventEmitter<any>();
   @Output() panMode = new EventEmitter<boolean>();
   _panMode = false;

   togglePan() {
      this.panMode.emit(this._panMode = !this._panMode);
   }
}
