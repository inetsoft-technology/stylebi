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
import { oklabStops } from "./oklab-ramp";

function lightness(hex: string): number {
   const n = parseInt(hex.replace("#", ""), 16);
   const lin = (v: number) => {
      const c = v / 255;
      return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
   };
   const r = lin((n >> 16) & 0xFF);
   const g = lin((n >> 8) & 0xFF);
   const b = lin(n & 0xFF);
   const l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
   const m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
   const s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);
   return 0.2104542553 * l + 0.793617785 * m - 0.0040720468 * s;
}

describe("oklabStops", () => {
   it("should start and end on the given colors exactly", () => {
      const stops = oklabStops("#ff99cc", "#008000");

      expect(stops[0]).toEqual({ color: "#ff99cc", offset: 0 });
      expect(stops[stops.length - 1]).toEqual({ color: "#008000", offset: 1 });
   });

   it("should return the requested number of evenly spaced stops", () => {
      const stops = oklabStops("#2b6cb0", "#f08a24", 5);

      expect(stops.map(s => s.offset)).toEqual([0, 0.25, 0.5, 0.75, 1]);
   });

   it("should change lightness evenly from one end to the other", () => {
      const stops = oklabStops("#ff99cc", "#008000", 11);
      const from = lightness("#ff99cc");
      const to = lightness("#008000");

      stops.forEach(s => {
         expect(Math.abs(lightness(s.color) - (from + (to - from) * s.offset))).toBeLessThan(0.01);
      });
   });

   it("should differ from the plain sRGB midpoint", () => {
      const mid = oklabStops("#ff99cc", "#008000", 3)[1].color;

      expect(mid).not.toBe("#7f8c66");
   });
});
