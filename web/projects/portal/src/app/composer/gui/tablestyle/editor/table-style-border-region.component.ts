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
import {Component, EventEmitter, Input, Output} from "@angular/core";

@Component({
   selector: "table-style-border-region",
   templateUrl: "table-style-border-region.component.html",
   styleUrls: ["table-style-preview-pane.component.scss"]
})
export class TableStyleBorderRegionComponent {
   @Input() regionName: string;
   @Input() hoverRegion: string;
   @Input() selectedRegion: string;
   @Output() onRegionHover = new EventEmitter<string>();
   @Output() onUnHoverRegion = new EventEmitter();
   @Output() onRegionSelect = new EventEmitter<string>();

   isHoverRegion(current: string) {
      return this.hoverRegion == current;
   }

   isSelectRegion(current: string) {
      return this.selectedRegion == current && this.hoverRegion != "";
   }
}