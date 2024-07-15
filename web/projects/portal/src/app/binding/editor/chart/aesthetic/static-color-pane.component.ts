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
import { ColorFieldPane } from "../../../widget/color-field-pane.component";
import { StaticColorModel } from "../../../../common/data/visual-frame-model";

@Component({
   selector: "static-color-pane",
   templateUrl: "static-color-pane.component.html"
})
export class StaticColorPane {
   @Input() frame: StaticColorModel;
   @Output() colorChanged: EventEmitter<string> = new EventEmitter<string>();

   changeColor(color: string) {
      this.frame.changed = true;
      this.frame.color = color;
      this.colorChanged.emit(color);
   }

   clearColor(color: string) {
      this.frame.changed = false;
      this.frame.color = color;
      this.colorChanged.emit(color);
   }
}
