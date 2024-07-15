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
import { Component, EventEmitter, Input, OnInit, Output } from "@angular/core";
import {
   convertHEXToHSV,
   convertHEXToRGB,
   convertHSVToRGB,
   convertRGBToHEX,
   convertRGBToHSV,
   HSV,
   RGB
} from "./color-utils";

@Component({
   selector: "cp-color-editor-dialog",
   templateUrl: "color-editor-dialog.component.html",
   styleUrls: ["color-editor-dialog.component.scss"]
})
export class ColorEditorDialog implements OnInit {
   @Input() color: string;
   @Output() onCommit: EventEmitter<string> = new EventEmitter<string>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();

   componentSize: number = 0;

   private _red = 0;
   private _green = 0;
   private _blue = 0;
   private _hue = 0;
   private _saturation = 0;
   private _brightness = 0;
   private _hex: string;

   get hue(): number {
      return this._hue;
   }

   set hue(value: number) {
      this._hue = this.getBoundedValue(value, 100);
      this.setRGB();
   }

   get saturation(): number {
      return this._saturation;
   }

   set saturation(value: number) {
      this._saturation = this.getBoundedValue(value, 100);
      this.setRGB();
   }

   get brightness(): number {
      return this._brightness;
   }

   set brightness(value: number) {
      this._brightness = this.getBoundedValue(value, 100);
      this.setRGB();
   }

   get red(): number {
      return this._red;
   }

   set red(value: number) {
      this._red = this.getBoundedValue(value, 255);
      this.setHSV();
      this.setHex();
   }

   get green(): number {
      return this._green;
   }

   set green(value: number) {
      this._green = this.getBoundedValue(value, 255);
      this.setHSV();
      this.setHex();
   }

   get blue(): number {
      return this._blue;
   }

   set blue(value: number) {
      this._blue = this.getBoundedValue(value, 255);
      this.setHSV();
      this.setHex();
   }

   get hex(): string {
      return this._hex;
   }

   set hex(value: string) {
      let color = value || "";

      if(color.length > 6) {
         color = color.substring(0, 6);
      }

      const originalColor = color;

      while(color.length < 6) {
         color = color + "0";
      }

      if(/^[a-fA-F0-9]{6}$/.test(color)) {
         const rgb = convertHEXToRGB(`#${color}`);
         this._red = rgb.red;
         this._green = rgb.green;
         this._blue = rgb.blue;
         this._hex = originalColor;
         this.setHSV();
      }
   }

   ngOnInit(): void {
      this._hex = (this.color || "#000000").substring(1);

      const hsv = convertHEXToHSV(`#${this._hex}`);
      this._hue = hsv.hue;
      this._saturation = hsv.saturation;
      this._brightness = hsv.brightness;

      const rgb = convertHEXToRGB(`#${this._hex}`);
      this._red = rgb.red;
      this._green = rgb.green;
      this._blue = rgb.blue;
   }

   ok(): void {
      let color: string = convertRGBToHEX(new RGB(this._red, this._green, this._blue));
      this.onCommit.emit(color);
   }

   cancel(): void {
      this.onCancel.emit("cancel");
   }

   private getBoundedValue(value: number, max: number): number {
      return Math.max(0, Math.min(value || 0, max));
   }

   private setRGB(): void {
      const rgb = convertHSVToRGB(new HSV(this._hue, this._saturation, this._brightness));
      this._red = rgb.red;
      this._blue = rgb.blue;
      this._green = rgb.green;
      this._hex = convertRGBToHEX(rgb).substring(1);
   }

   private setHSV(): void {
      const hsv = convertRGBToHSV(new RGB(this._red, this._green, this._blue));
      this._hue = hsv.hue;
      this._saturation = hsv.saturation;
      this._brightness = hsv.brightness;
   }

   private setHex(): void {
      this._hex = convertRGBToHEX(new RGB(this._red, this._green, this._blue)).substring(1);
   }
}
