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
import { ColorDropdown } from "./color-dropdown.component";
import { DefaultPalette } from "./default-palette";

// The chart branch of getPalette() is only reachable when !isBg && !transEnabled. Several
// callers rely on that ordering rather than passing [chart] explicitly - notably the Format
// pane's Value Fill picker (transEnabled) and its border picker (chart default true). Pin the
// whole truth table so the branch order cannot be changed silently.
describe("ColorDropdown palette resolution", () => {
   function dropdown(isBg: boolean, transEnabled: boolean, chart: boolean): ColorDropdown {
      const cd = new ColorDropdown();
      cd.isBg = isBg;
      cd.transEnabled = transEnabled;
      cd.chart = chart;
      return cd;
   }

   it("defaults chart to true", () => {
      expect(new ColorDropdown().chart).toBe(true);
   });

   it("returns fgWithTransparent for foreground with transparency, ignoring chart", () => {
      expect(dropdown(false, true, true).getPalette()).toBe(DefaultPalette.fgWithTransparent);
      expect(dropdown(false, true, false).getPalette()).toBe(DefaultPalette.fgWithTransparent);
   });

   it("returns the chart grid for opaque foreground with chart true", () => {
      expect(dropdown(false, false, true).getPalette()).toBe(DefaultPalette.chart);
   });

   it("returns the generic grid for opaque foreground with chart false", () => {
      expect(dropdown(false, false, false).getPalette()).toBe(DefaultPalette.palette);
   });

   it("returns background grids regardless of chart", () => {
      expect(dropdown(true, true, true).getPalette()).toBe(DefaultPalette.bgWithTransparent);
      expect(dropdown(true, true, false).getPalette()).toBe(DefaultPalette.bgWithTransparent);
      expect(dropdown(true, false, true).getPalette()).toBe(DefaultPalette.bgWithNoTransparent);
      expect(dropdown(true, false, false).getPalette()).toBe(DefaultPalette.bgWithNoTransparent);
   });
});
