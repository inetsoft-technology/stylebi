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
export class HSV {
   public hue: number; // normalized, 0 - 100
   public saturation: number;
   public brightness: number;

   constructor(hue: number = 0, saturation: number = 0, brightness: number = 0) {
      this.hue = hue;
      this.saturation = saturation;
      this.brightness = brightness;
   }
}

export class RGB {
   public red: number;
   public green: number;
   public blue: number;

   constructor(red: number = 0, green: number = 0, blue: number = 0) {
      this.red = red;
      this.green = green;
      this.blue = blue;
   }
}

export function convertRGBToHSV(rgb: RGB): HSV {
   let r: number = rgb.red / 255;
   let g: number = rgb.green / 255;
   let b: number = rgb.blue / 255;
   let cMax: number = Math.max(r, g, b);
   let cMin: number = Math.min(r, g, b);
   let delta: number = cMax - cMin;
   let h: number = 0;
   let s: number = 0;
   let v: number = cMax;

   if(delta > 0) {
      if(cMax == r) {
         h = ((g - b) / delta) % 6;
      }
      else if(cMax == g) {
         h = ((b - r) / delta) + 2;
      }
      else { // cMax == b
         h = ((r - g) / delta) + 4;
      }

      h *= 60;

      if(h < 0) {
         h += 360;
      }
   }

   if(cMax > 0) {
      s = delta / cMax;
   }

   return new HSV(
      Math.round(h * 100 / 360),
      Math.round(s * 100),
      Math.round(v * 100));
}

export function convertHSVToRGB(hsv: HSV): RGB {
   const hue = hsv.hue * 360 / 100;
   let s: number = hsv.saturation / 100;
   let v: number = hsv.brightness / 100;
   let c = v * s;
   let x = c * (1 - Math.abs((hue / 60) % 2 - 1));
   let m = v - c;
   let r: number = 0;
   let g: number = 0;
   let b: number = 0;

   if(hue < 60) {
      r = c;
      g = x;
   }
   else if(hue < 120) {
      r = x;
      g = c;
   }
   else if(hue < 180) {
      g = c;
      b = x;
   }
   else if(hue < 240) {
      g = x;
      b = c;
   }
   else if(hue < 300) {
      r = x;
      b = c;
   }
   else { // hue >= 300
      r = c;
      b = x;
   }

   return new RGB(
      Math.min(Math.round((r + m) * 255), 255),
      Math.min(Math.round((g + m) * 255), 255),
      Math.min(Math.round((b + m) * 255), 255));
}

export function convertRGBToHEX(rgb: RGB): string {
   if(!rgb) {
      return "#";
   }

   return "#" +
      ("0" + rgb.red.toString(16)).slice(-2) +
      ("0" + rgb.green.toString(16)).slice(-2) +
      ("0" + rgb.blue.toString(16)).slice(-2);
}

export function convertHSVToHEX(hsv: HSV): string {
   return convertRGBToHEX(convertHSVToRGB(hsv));
}

export function convertHEXToRGB(hex: string) {
   hex = getColorHex(hex);
   let rgb: RGB = new RGB();
   rgb.red = parseInt(hex.substring(0, 2), 16);
   rgb.green = parseInt(hex.substring(2, 4), 16);
   rgb.blue = parseInt(hex.substring(4), 16);

   return rgb;
}

export function convertHEXToHSV(hex: string) {
   return convertRGBToHSV(convertHEXToRGB(hex));
}

function getLinearColor(c: number): number {
   return (c <= 0.04045) ? c / 12.92 : Math.pow(((c + 0.055) / 1.055), 2.4);
}

export function getRGBLuminance(rgb: RGB): number {
   let r: number = getLinearColor(rgb.red / 255);
   let g: number = getLinearColor(rgb.green / 255);
   let b: number = getLinearColor(rgb.blue / 255);
   return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

export function getHSVLuminance(hsv: HSV): number {
   return getRGBLuminance(convertHSVToRGB(hsv));
}

export function getColorHex(color: string): string {
   if(color.startsWith("#")) {
      return color.substring(1);
   }
   else if(color.startsWith("0x")) {
      return color.substring(2);
   }

   let colorValue: string = (parseInt(color, 10) & 0xffffff).toString(16);

   while(colorValue.length < 6) {
      colorValue = "0" + colorValue;
   }
   return colorValue;
}
