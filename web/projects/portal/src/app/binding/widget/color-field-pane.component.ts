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
import {
   Component,
   EventEmitter,
   Input,
   Output,
} from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";

import { ColorPane } from "../../widget/color-picker/cp-color-pane.component";
import { ColorPalette } from "../../widget/color-picker/color-classes";
import { DefaultPalette } from "../../widget/color-picker/default-palette";

@Component({
   selector: "color-field-pane",
   templateUrl: "color-field-pane.component.html",
   styleUrls: ["./color-field-pane.component.scss"]
})
export class ColorFieldPane {
   @Input() selectedColor: string = "#518db9";
   @Input() clearEnabled: boolean = false;
   @Output() colorChanged: EventEmitter<string> = new EventEmitter<string>();
   @Output() colorCleared: EventEmitter<string> = new EventEmitter<string>();
   palette: ColorPalette = DefaultPalette.chart;

   selectColor(c: string) {
      this.selectedColor = c;
      this.colorChanged.emit(c);
   }

   clearColor(c: string) {
      this.selectedColor = c;
      this.colorCleared.emit(c);
   }
}
