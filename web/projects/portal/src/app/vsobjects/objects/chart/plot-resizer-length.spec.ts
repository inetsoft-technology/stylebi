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
import { plotResizerLength } from "./plot-resizer-length";

describe("plotResizerLength", () => {
   it("caps at 150px on a wide plot", () => {
      expect(plotResizerLength(1000)).toBe(150);
      expect(plotResizerLength(250)).toBe(150);
   });

   it("takes 60% of the edge once 60% is under the cap", () => {
      expect(plotResizerLength(200)).toBe(120);
      expect(plotResizerLength(150)).toBe(90);
   });

   it("returns the 80px floor exactly at the boundary", () => {
      // 80 / 0.6 = 133.33, so 134 is the first edge that still clears the floor
      expect(plotResizerLength(134)).toBeCloseTo(80.4, 1);
   });

   it("returns null below the floor, so the slider does not render", () => {
      expect(plotResizerLength(133)).toBeNull();
      expect(plotResizerLength(60)).toBeNull();
      expect(plotResizerLength(0)).toBeNull();
   });

   it("returns null for a missing or nonsensical edge", () => {
      expect(plotResizerLength(undefined)).toBeNull();
      expect(plotResizerLength(null)).toBeNull();
      expect(plotResizerLength(-10)).toBeNull();
   });
});
