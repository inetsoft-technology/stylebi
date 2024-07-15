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
import { Component, EventEmitter, Input, Output } from "@angular/core";
import { TableStyleUtil } from "../../../../common/util/table-style-util";
import { CSSTableStyleModel } from "../../../data/tablestyle/css/css-table-style-model";

@Component({
   selector: "table-style-preview-pane",
   templateUrl: "table-style-preview-pane.component.html",
   styleUrls: ["table-style-preview-pane.component.scss"]
})
export class TableStylePreviewPaneComponent {
   @Input() selectedRegion: string;
   @Input() cssTableStyle: CSSTableStyleModel;
   @Output() onSelectRegion: EventEmitter<string> = new EventEmitter<string>();
   protected readonly TableStyleUtil = TableStyleUtil;
   tableData: any[];
   hoverRegion: string = "";

   highlightRegion(target: string) {
      this.hoverRegion = target;
   }

   unHighlightRegion() {
      this.hoverRegion = "";
   }

   isHoverRegion(current: string) {
      return this.hoverRegion == current;
   }

   isSelectRegion(current: string) {
      return this.selectedRegion == current && this.hoverRegion != "";
   }

   selectRegion(selectRegion: string) {
      this.onSelectRegion.emit(selectRegion);
   }
}