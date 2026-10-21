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

// records draw calls rather than pixels, which is what the assertions are about. the canvas
// itself is a real element because drawRegions reads its computed style.
function fakeContext(canvas: HTMLCanvasElement = document.createElement("canvas")): any {
   const calls: string[] = [];
   // the stroke style and dash in force at each stroke() call
   const strokes: {style: string, dash: number[]}[] = [];
   let strokeStyle = "";
   let dash: number[] = [];
   canvas.getBoundingClientRect = () => ({width: 100, height: 100} as DOMRect);

   return {
      calls,
      strokes,
      canvas,
      beginPath: () => calls.push("beginPath"),
      moveTo: () => {}, lineTo: () => {}, closePath: () => {},
      setTransform: () => {}, transform: () => {}, save: () => {}, restore: () => {},
      scale: () => {}, translate: () => {}, clearRect: () => {}, clip: () => {},
      quadraticCurveTo: () => {}, bezierCurveTo: () => {}, arc: () => {}, arcTo: () => {},
      ellipse: () => {}, rect: () => {},
      setLineDash: (segments: number[]) => dash = [...segments],
      fill: () => calls.push("fill"),
      stroke: () => {
         calls.push("stroke");
         strokes.push({style: strokeStyle, dash});
      },
      set fillStyle(v: string) {},
      set strokeStyle(v: string) { strokeStyle = v; },
      get strokeStyle(): string { return strokeStyle; },
      set lineWidth(v: number) {}
   };
}

const region: any = {
   index: 0, valIdx: 0, segTypes: [[1, 2]], pts: [[[[0, 0], [10, 0], [10, 10], [0, 10]]]]
};

describe("drawRegions chrome/data fill split", () => {
   it("strokes without filling for a chrome area", () => {
      const ctx = fakeContext();
      ChartTool.drawRegions(ctx, [region], 0, 0, 1, undefined, undefined, false, "left_y_axis", true);
      expect(ctx.calls).toContain("stroke");
      expect(ctx.calls).not.toContain("fill");
   });

   it("fills and strokes for the plot area", () => {
      const ctx = fakeContext();
      ChartTool.drawRegions(ctx, [region], 0, 0, 1, undefined, undefined, false, "plot_area", true);
      expect(ctx.calls).toContain("fill");
      expect(ctx.calls).toContain("stroke");
   });

   it("keeps the fill for a chrome area when the gate is off", () => {
      const ctx = fakeContext();
      ChartTool.drawRegions(ctx, [region], 0, 0, 1, undefined, undefined, false, "left_y_axis", false);
      expect(ctx.calls).toContain("fill");
   });
});

describe("drawRegions chrome selection outline", () => {
   // a token value no default resolves to, so only a read of the token can produce it
   const TOKEN = "#123456";
   let host: HTMLElement;

   // a canvas inside an assembly wrapper that defines the selection-border token
   function tokenCanvas(): HTMLCanvasElement {
      host = document.createElement("div");
      host.style.setProperty("--inet-viz-selected-border", TOKEN);
      const canvas = document.createElement("canvas");
      host.appendChild(canvas);
      document.body.appendChild(host);
      return canvas;
   }

   afterEach(() => host?.remove());

   it("draws a chrome area dashed in the selection-border token", () => {
      const ctx = fakeContext(tokenCanvas());
      ChartTool.drawRegions(ctx, [region], 0, 0, 1, undefined, undefined, false, "legend_content", true);
      // 6/4 is Chromium's dash for a 2px dashed outline, the composer's outer selection
      expect(ctx.strokes).toEqual([{style: TOKEN, dash: [6, 4]}]);
   });

   it("keeps a data selection solid and off the selection-border token", () => {
      const ctx = fakeContext(tokenCanvas());
      ChartTool.drawRegions(ctx, [region], 0, 0, 1, undefined, undefined, false, "plot_area", true);
      expect(ctx.strokes.length).toBe(1);
      expect(ctx.strokes[0].dash).toEqual([]);
      expect(ctx.strokes[0].style).not.toBe(TOKEN);
   });

   it("keeps a chrome area solid when the gate is off", () => {
      const ctx = fakeContext(tokenCanvas());
      ChartTool.drawRegions(ctx, [region], 0, 0, 1, undefined, undefined, false, "legend_content", false);
      expect(ctx.strokes.length).toBe(1);
      expect(ctx.strokes[0].dash).toEqual([]);
      expect(ctx.strokes[0].style).not.toBe(TOKEN);
   });

   it("keeps the canvas stroke colour when no selection-border token resolves", () => {
      const ctx = fakeContext();
      ChartTool.drawRegions(ctx, [region], 0, 0, 1, undefined, undefined, false, "legend_content", true);
      // no stylesheet is loaded, so the canvas rule's colour is drawRegions' own default
      expect(ctx.strokes).toEqual([{style: "#dc581e", dash: [6, 4]}]);
   });
});
