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
   // the [x, y, w, h] of each rect() call
   const rects: number[][] = [];
   let strokeStyle = "";
   let dash: number[] = [];
   canvas.getBoundingClientRect = () => ({width: 100, height: 100} as DOMRect);

   return {
      calls,
      strokes,
      rects,
      canvas,
      beginPath: () => calls.push("beginPath"),
      moveTo: () => {}, lineTo: () => {}, closePath: () => {},
      setTransform: () => {}, transform: () => {}, save: () => {}, restore: () => {},
      scale: () => {}, translate: () => {}, clearRect: () => {}, clip: () => {},
      quadraticCurveTo: () => {}, bezierCurveTo: () => {}, arc: () => {}, arcTo: () => {},
      ellipse: () => {}, rect: (x: number, y: number, w: number, h: number) => rects.push([x, y, w, h]),
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
      host.style.setProperty("--inet-viz-assembly-outline", TOKEN);
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

// the 2px stroke is centred on the rect's edges, so an edge on the canvas edge loses its outer half
describe("drawRegions chrome selection on the canvas edge", () => {
   function rectRegion(x: number, y: number, w: number, h: number): any {
      return {index: 0, valIdx: 0, segTypes: [[ChartTool.RECT]], pts: [[[[x, y], [w, h]]]]};
   }

   let host: HTMLElement;

   afterEach(() => host?.remove());

   function sizedContext(width: number, height: number): any {
      const ctx = fakeContext();
      ctx.canvas.getBoundingClientRect = () =>
         ({left: 0, top: 0, right: width, bottom: height, width, height} as DOMRect);
      return ctx;
   }

   // drawRegions reads window.devicePixelRatio
   function withDevicePixelRatio(dpr: number, draw: () => void): void {
      const own = Object.getOwnPropertyDescriptor(window, "devicePixelRatio");
      Object.defineProperty(window, "devicePixelRatio", {value: dpr, configurable: true});

      try {
         draw();
      }
      finally {
         if(own) {
            Object.defineProperty(window, "devicePixelRatio", own);
         }
         else {
            delete (window as any).devicePixelRatio;
         }
      }
   }

   // a canvas in a clipping host that shows only its rows from top down, as legend content does
   function hostedContext(width: number, height: number, top: number): any {
      const ctx = sizedContext(width, height);
      host = document.createElement("div");
      host.style.overflow = "hidden";
      host.getBoundingClientRect = () =>
         ({left: 0, top, right: width, bottom: height, width, height: height - top} as DOMRect);
      host.appendChild(ctx.canvas);
      document.body.appendChild(host);
      return ctx;
   }

   it("keeps every edge of a legend title selection whole on its canvas", () => {
      // the title region fills its canvas, whose width is the region's truncated to whole px
      const ctx = sizedContext(406, 18);
      ChartTool.drawRegions(ctx, [rectRegion(0, 0, 406.775, 17)], 0, 0, 1,
         undefined, undefined, false, "legend_title", true);
      expect(ctx.rects).toEqual([[1, 1, 404, 16]]);
   });

   it("measures the canvas edge in region units under a viewsheet scale and offset", () => {
      // at scale 2 a 200x100 css canvas holds 100x50 region units, starting at the offset
      const ctx = sizedContext(200, 100);
      ChartTool.drawRegions(ctx, [rectRegion(2, 2, 100, 30)], 2, 2, 2,
         undefined, undefined, false, "legend_title", true);
      expect(ctx.rects).toEqual([[3, 3, 98, 29]]);
   });

   it("keeps the right edge on the canvas bitmap at a fractional device pixel ratio", () => {
      // 401 css px at 1.25 is 501.25 device px; the bitmap keeps 501, so it ends at 400.8
      const ctx = sizedContext(401, 18);
      withDevicePixelRatio(1.25, () => ChartTool.drawRegions(ctx, [rectRegion(0, 0, 401.8, 17)],
         0, 0, 1, undefined, undefined, false, "legend_title", true));
      const [x, y, w, h] = ctx.rects[0];

      expect(x).toBeCloseTo(1);
      expect(y).toBeCloseTo(1);
      expect(x + w).toBeCloseTo(399.8);
      expect(y + h).toBeCloseTo(16.6);
   });

   it("leaves a chrome selection that already fits inside the canvas where it is", () => {
      const ctx = sizedContext(100, 100);
      ChartTool.drawRegions(ctx, [rectRegion(10, 5, 50, 8)], 0, 0, 1,
         undefined, undefined, false, "legend_content", true);
      expect(ctx.rects).toEqual([[10, 5, 50, 8]]);
   });

   it("keeps the top edge of a legend item whole under its host's clip", () => {
      // the content canvas starts at the legend origin; its host shows it from the content top
      const ctx = hostedContext(331, 39, 21);
      ChartTool.drawRegions(ctx, [rectRegion(2, 21, 80, 14)], 0, 0, 1,
         undefined, undefined, false, "legend_content", true);
      expect(ctx.rects).toEqual([[2, 22, 80, 13]]);
   });

   it("leaves a legend item scrolled under its host's top cut", () => {
      const ctx = hostedContext(331, 39, 21);
      ChartTool.drawRegions(ctx, [rectRegion(2, 15, 80, 14)], 0, 0, 1,
         undefined, undefined, false, "legend_content", true);
      expect(ctx.rects).toEqual([[2, 15, 80, 14]]);
   });

   it("leaves an edge that runs past the canvas cut", () => {
      const ctx = sizedContext(100, 100);
      ChartTool.drawRegions(ctx, [rectRegion(-10, 5, 50, 8)], 0, 0, 1,
         undefined, undefined, false, "legend_content", true);
      expect(ctx.rects).toEqual([[-10, 5, 50, 8]]);
   });

   it("leaves a chrome selection outside the canvas where it is", () => {
      const ctx = sizedContext(100, 100);
      ChartTool.drawRegions(ctx, [rectRegion(150, 10, 20, 5)], 0, 0, 1,
         undefined, undefined, false, "legend_content", true);
      expect(ctx.rects).toEqual([[150, 10, 20, 5]]);
   });

   it("keeps a legacy legend title selection where it was", () => {
      const ctx = sizedContext(406, 18);
      ChartTool.drawRegions(ctx, [rectRegion(0, 0, 406.775, 17)], 0, 0, 1,
         undefined, undefined, false, "legend_title", false);
      expect(ctx.rects).toEqual([[0, 0, 406.775, 17]]);
   });

   it("keeps a data selection on the canvas edge where it is", () => {
      const ctx = sizedContext(100, 100);
      ChartTool.drawRegions(ctx, [rectRegion(0, 0, 100, 100)], 0, 0, 1,
         undefined, undefined, false, "plot_area", true);
      expect(ctx.rects).toEqual([[0, 0, 100, 100]]);
   });
});
