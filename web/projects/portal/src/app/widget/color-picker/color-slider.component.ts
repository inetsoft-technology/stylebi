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
import { Component, EventEmitter, Input, Output } from "@angular/core";

@Component({
   selector: "cp-color-slider",
   templateUrl: "color-slider.component.html",
   styleUrls: ["color-slider.component.scss"]
})
export class ColorSlider {
   @Input() height: number;
   @Input() hue: number;
   @Output() hueChanged: EventEmitter<number> = new EventEmitter<number>();

   get indicatorTop(): string {
      return this.hue + "%";
   }

   setHuePosition(event: any): void {
      const hue: number = Math.round(100 * event.offsetY / this.height);
      this.hueChanged.emit(hue);
   }
}
