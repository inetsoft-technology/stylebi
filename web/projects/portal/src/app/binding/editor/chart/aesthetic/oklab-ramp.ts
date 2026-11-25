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

/**
 * The smooth Custom ramp, as the chart draws it: a straight line between the two colors in OKLab,
 * brought into sRGB by reducing chroma only. It mirrors inetsoft.graph.internal.OKLab and
 * GradientColorFrame.smoothColor, so the strip in the picker matches the chart and its legend.
 */
export interface OklabStop {
   color: string;
   offset: number;
}

type Rgb = [number, number, number];

function toLinear(v: number): number {
   const c = v / 255;
   return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
}

function fromLinear(c: number): number {
   const v = c <= 0.0031308 ? 12.92 * c : 1.055 * Math.pow(c, 1 / 2.4) - 0.055;
   return Math.max(0, Math.min(255, Math.round(v * 255)));
}

function parseHex(hex: string): Rgb {
   const n = parseInt(hex.replace("#", ""), 16);
   return [(n >> 16) & 0xFF, (n >> 8) & 0xFF, n & 0xFF];
}

function toHex(rgb: Rgb): string {
   return "#" + rgb.map(v => v.toString(16).padStart(2, "0")).join("");
}

function toLab([r8, g8, b8]: Rgb): Rgb {
   const r = toLinear(r8);
   const g = toLinear(g8);
   const b = toLinear(b8);
   const l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
   const m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
   const s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);

   return [
      0.2104542553 * l + 0.793617785 * m - 0.0040720468 * s,
      1.9779984951 * l - 2.428592205 * m + 0.4505937099 * s,
      0.0259040371 * l + 0.7827717662 * m - 0.808675766 * s
   ];
}

function linearRgb(L: number, a: number, b: number): Rgb {
   const l = Math.pow(L + 0.3963377774 * a + 0.2158037573 * b, 3);
   const m = Math.pow(L - 0.1055613458 * a - 0.0638541728 * b, 3);
   const s = Math.pow(L - 0.0894841775 * a - 1.291485548 * b, 3);

   return [
      4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
      -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
      -0.0041960863 * l - 0.7034186147 * m + 1.707614701 * s
   ];
}

function inGamut(L: number, c: number, hue: number): boolean {
   const rad = hue * Math.PI / 180;
   return linearRgb(L, c * Math.cos(rad), c * Math.sin(rad))
      .every(v => v >= -1e-6 && v <= 1 + 1e-6);
}

function labToHex(L: number, a: number, b: number): string {
   const chroma = Math.hypot(a, b);
   const hue = Math.atan2(b, a) * 180 / Math.PI;
   let c = chroma;

   if(!inGamut(L, chroma, hue)) {
      let lo = 0;
      let hi = chroma;

      for(let i = 0; i < 40; i++) {
         const mid = (lo + hi) / 2;

         if(inGamut(L, mid, hue)) {
            lo = mid;
         }
         else {
            hi = mid;
         }
      }

      c = lo;
   }

   const rad = hue * Math.PI / 180;
   const rgb = linearRgb(L, c * Math.cos(rad), c * Math.sin(rad));
   return toHex([fromLinear(rgb[0]), fromLinear(rgb[1]), fromLinear(rgb[2])]);
}

/**
 * Stops for an SVG or CSS gradient from one color to another, evenly spaced, so that the
 * gradient's own sRGB blend between neighbouring stops is too short to show.
 */
export function oklabStops(from: string, to: string, count: number = 9): OklabStop[] {
   const a = toLab(parseHex(from));
   const b = toLab(parseHex(to));
   const stops: OklabStop[] = [];

   for(let i = 0; i < count; i++) {
      const t = i / (count - 1);

      if(i === 0 || i === count - 1) {
         stops.push({ color: i === 0 ? from : to, offset: t });
      }
      else {
         stops.push({
            color: labToHex(a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t,
                            a[2] + (b[2] - a[2]) * t),
            offset: t
         });
      }
   }

   return stops;
}
