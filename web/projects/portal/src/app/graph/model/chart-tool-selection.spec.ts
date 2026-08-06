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
import { ChartAreaName } from "./chart-area-name";
import { ChartTool } from "./chart-tool";

describe("ChartTool.isChromeArea", () => {
   const CHROME: ChartAreaName[] = [
      "bottom_x_axis", "top_x_axis", "left_y_axis", "right_y_axis",
      "legend_content", "legend_title", "x_title", "x2_title", "y_title", "y2_title"
   ];

   it("classifies all ten chrome areas as chrome", () => {
      for(const area of CHROME) {
         expect(ChartTool.isChromeArea(area)).toBe(true);
      }
   });

   it("classifies the plot area as data, not chrome", () => {
      expect(ChartTool.isChromeArea("plot_area")).toBe(false);
   });

   it("treats an unknown or absent area as data, so an un-migrated caller keeps its fill", () => {
      expect(ChartTool.isChromeArea(null)).toBe(false);
      expect(ChartTool.isChromeArea(undefined)).toBe(false);
   });
});

describe("drawRegions chrome/data fill split", () => {
   // records draw calls rather than pixels, which is what the assertion is about. the canvas
   // itself is a real element because drawRegions reads its computed style.
   function fakeContext(): any {
      const calls: string[] = [];
      const canvas = document.createElement("canvas");
      canvas.getBoundingClientRect = () => ({width: 100, height: 100} as DOMRect);

      return {
         calls,
         canvas,
         beginPath: () => calls.push("beginPath"),
         moveTo: () => {}, lineTo: () => {}, closePath: () => {},
         setTransform: () => {}, transform: () => {}, save: () => {}, restore: () => {},
         scale: () => {}, translate: () => {}, clearRect: () => {}, clip: () => {},
         quadraticCurveTo: () => {}, bezierCurveTo: () => {}, arc: () => {}, arcTo: () => {},
         ellipse: () => {}, rect: () => {},
         fill: () => calls.push("fill"),
         stroke: () => calls.push("stroke"),
         set fillStyle(v: string) {}, set strokeStyle(v: string) {}, set lineWidth(v: number) {}
      };
   }

   const region: any = {
      index: 0, valIdx: 0, segTypes: [[1, 2]], pts: [[[[0, 0], [10, 0], [10, 10], [0, 10]]]]
   };

   beforeEach(() => document.body.classList.add("viz-modern"));
   afterEach(() => document.body.classList.remove("viz-modern"));

   it("strokes without filling for a chrome area", () => {
      const ctx = fakeContext();
      ChartTool.drawRegions(ctx, [region], 0, 0, 1, undefined, undefined, false, "left_y_axis");
      expect(ctx.calls).toContain("stroke");
      expect(ctx.calls).not.toContain("fill");
   });

   it("fills and strokes for the plot area", () => {
      const ctx = fakeContext();
      ChartTool.drawRegions(ctx, [region], 0, 0, 1, undefined, undefined, false, "plot_area");
      expect(ctx.calls).toContain("fill");
      expect(ctx.calls).toContain("stroke");
   });

   it("keeps the fill for a chrome area when the gate is off", () => {
      document.body.classList.remove("viz-modern");
      const ctx = fakeContext();
      ChartTool.drawRegions(ctx, [region], 0, 0, 1, undefined, undefined, false, "left_y_axis");
      expect(ctx.calls).toContain("fill");
   });
});
