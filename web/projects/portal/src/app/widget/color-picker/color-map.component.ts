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
import { HSV, convertHSVToHEX } from "./color-utils";

@Component({
   selector: "cp-color-map",
   templateUrl: "color-map.component.html",
   styleUrls: ["color-map.component.scss"]
})
export class ColorMap {
   @Input() width: number;
   @Input() height: number;
   private hsv: HSV = new HSV(0, 0, 0);

   get hue(): number {
      return this.hsv.hue;
   }

   @Input()
   set hue(value: number) {
      this.hsv.hue = value;
   }

   get saturation(): number {
      return this.hsv.saturation;
   }

   @Input()
   set saturation(value: number) {
      this.hsv.saturation = value;
   }

   @Output() saturationChanged: EventEmitter<number> = new EventEmitter<number>();

   get brightness(): number {
      return this.hsv.brightness;
   }

   @Input()
   set brightness(value: number) {
      this.hsv.brightness = value;
   }

   @Output() brightnessChanged: EventEmitter<number> = new EventEmitter<number>();

   get hueBackground(): string {
      let hsv: HSV = new HSV(this.hsv.hue, 100, 100);
      return convertHSVToHEX(hsv);
   }

   get indicatorTop(): string {
      return (100 - this.hsv.brightness) + "%";
   }

   get indicatorLeft(): string {
      return this.hsv.saturation + "%";
   }

   setColorPosition(event: any): void {
      this.hsv.brightness = Math.round(100 * (this.height - event.offsetY) / this.height);
      this.hsv.saturation = Math.round(100 * event.offsetX / this.width);
      this.brightnessChanged.emit(this.hsv.brightness);
      this.saturationChanged.emit(this.hsv.saturation);
   }
}
