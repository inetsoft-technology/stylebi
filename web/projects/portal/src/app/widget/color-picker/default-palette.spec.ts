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
import { DefaultPalette } from "./default-palette";

// VSChartPaletteDefaults.MODERN_HEAD
const MODERN_HEAD = ["#0490ff", "#ff5a35", "#241c4f", "#03d9b3", "#9a2ddc", "#ffb020", "#e5197e",
                     "#8ed604"];

describe("DefaultPalette picker grids", () => {
   const grids = [DefaultPalette.palette, DefaultPalette.fgWithTransparent,
                  DefaultPalette.bgWithTransparent, DefaultPalette.bgWithNoTransparent];

   it("every grid is 5 rows of 8 cells", () => {
      for(const grid of grids) {
         expect(grid.length).toBe(5);
         grid.forEach((row) => expect(row.length).toBe(8));
      }
   });

   it("row 2 is the Modern palette's eight colors", () => {
      expect(DefaultPalette.palette[1]).toEqual(MODERN_HEAD);
      expect(DefaultPalette.fgWithTransparent[1]).toEqual(MODERN_HEAD);
   });

   it("has no repeated color, and offers white and black", () => {
      const cells = DefaultPalette.palette.flat();

      expect(new Set(cells).size).toBe(cells.length);
      expect(cells).toContain("#ffffff");
      expect(cells).toContain("#000000");
   });

   it("the transparent grids start with a no-color cell and keep white and black", () => {
      for(const grid of [DefaultPalette.fgWithTransparent, DefaultPalette.bgWithTransparent]) {
         expect(grid[0][0]).toBe("");
         expect(grid[0]).toContain("#ffffff");
         expect(grid[0]).toContain("#000000");
         expect(grid.flat().filter((c) => c === "").length).toBe(1);
      }
   });

   it("the non-transparent grids have no empty cell", () => {
      expect(DefaultPalette.palette.flat()).not.toContain("");
      expect(DefaultPalette.bgWithNoTransparent.flat()).not.toContain("");
   });

   it("the chart grid is still the legacy palette", () => {
      expect(DefaultPalette.chart[0][0]).toBe("#518db9");
   });
});
