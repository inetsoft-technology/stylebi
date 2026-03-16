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
import { ChartTool } from "./chart-tool";

/** Minimal canvas path recording context for testing draw calls. */
function makeCtx() {
   const calls: string[] = [];
   return {
      calls,
      moveTo:    (x: number, y: number)                               => calls.push(`moveTo(${x},${y})`),
      lineTo:    (x: number, y: number)                               => calls.push(`lineTo(${x},${y})`),
      arcTo:     (x1: number, y1: number, x2: number, y2: number, r: number) => calls.push(`arcTo(${x1},${y1},${x2},${y2},${r})`),
      closePath: ()                                                    => calls.push("closePath()"),
   } as unknown as CanvasRenderingContext2D & { calls: string[] };
}

describe("ChartTool.drawRoundedBar", () => {

   describe("direction=undefined (round all corners equally)", () => {
      it("calls moveTo, four arcTo for corners, and closePath", () => {
         const ctx = makeCtx();
         ChartTool.drawRoundedBar(ctx, 0, 0, 100, 40, 0.3, undefined);
         const calls = ctx.calls;
         expect(calls[0]).toMatch(/^moveTo/);
         expect(calls.filter(c => c.startsWith("arcTo")).length).toBe(4);
         expect(calls[calls.length - 1]).toBe("closePath()");
      });

      it("uses arc = min(radiusFraction * min(w,h), min(w,h)/2)", () => {
         // w=100, h=40: shortDim=40, radiusFraction*shortDim = 12, shortDim/2 = 20 → arc=12
         const ctx = makeCtx();
         ChartTool.drawRoundedBar(ctx, 0, 0, 100, 40, 0.3, undefined);
         const arcCalls = ctx.calls.filter(c => c.startsWith("arcTo"));
         arcCalls.forEach(c => {
            const r = parseFloat(c.split(",")[4]);
            expect(r).toBeCloseTo(12);
         });
      });

      it("caps arc at shortDim/2 when radiusFraction is very large", () => {
         // w=200, h=20: shortDim=20, min(1.0*20, 20/2) = min(20, 10) = 10
         const ctx = makeCtx();
         ChartTool.drawRoundedBar(ctx, 0, 0, 200, 20, 1.0, undefined);
         const arcCalls = ctx.calls.filter(c => c.startsWith("arcTo"));
         arcCalls.forEach(c => {
            const r = parseFloat(c.split(",")[4]);
            expect(r).toBeCloseTo(10); // shortDim/2 = 10
         });
      });

      it("horizontal bar: arc scales with radiusFraction, not capped at h/2 always", () => {
         // Wide horizontal bar: w=200, h=20. shortDim=20.
         // radiusFraction=0.1: arc = min(0.1*20, 10) = min(2, 10) = 2
         // radiusFraction=0.4: arc = min(0.4*20, 10) = min(8, 10) = 8
         // Without the fix both would clamp to 10 (h/2), losing the fraction distinction.
         const ctx1 = makeCtx();
         const ctx2 = makeCtx();
         ChartTool.drawRoundedBar(ctx1, 0, 0, 200, 20, 0.1, undefined);
         ChartTool.drawRoundedBar(ctx2, 0, 0, 200, 20, 0.4, undefined);
         const arc1 = parseFloat(ctx1.calls.filter(c => c.startsWith("arcTo"))[0].split(",")[4]);
         const arc2 = parseFloat(ctx2.calls.filter(c => c.startsWith("arcTo"))[0].split(",")[4]);
         expect(arc1).toBeCloseTo(2);
         expect(arc2).toBeCloseTo(8);
         expect(arc1).toBeLessThan(arc2);
      });
   });

   describe("direction=0 (up — round top corners, sharp base)", () => {
      it("calls exactly two arcTo", () => {
         const ctx = makeCtx();
         ChartTool.drawRoundedBar(ctx, 0, 0, 100, 50, 0.2, 0);
         expect(ctx.calls.filter(c => c.startsWith("arcTo")).length).toBe(2);
      });

      it("starts at (x+arc, y) — top-left of bar", () => {
         const ctx = makeCtx();
         // arc = min(0.2*100, 50/2) = min(20, 25) = 20
         ChartTool.drawRoundedBar(ctx, 0, 0, 100, 50, 0.2, 0);
         expect(ctx.calls[0]).toBe("moveTo(20,0)");
      });

      it("ends with closePath", () => {
         const ctx = makeCtx();
         ChartTool.drawRoundedBar(ctx, 0, 0, 100, 50, 0.2, 0);
         expect(ctx.calls[ctx.calls.length - 1]).toBe("closePath()");
      });
   });

   describe("direction=1 (down — round bottom corners, sharp base)", () => {
      it("calls exactly two arcTo", () => {
         const ctx = makeCtx();
         ChartTool.drawRoundedBar(ctx, 0, 0, 100, 50, 0.2, 1);
         expect(ctx.calls.filter(c => c.startsWith("arcTo")).length).toBe(2);
      });

      it("starts at (x, y) — top-left (base corner, sharp)", () => {
         const ctx = makeCtx();
         ChartTool.drawRoundedBar(ctx, 5, 10, 100, 50, 0.2, 1);
         expect(ctx.calls[0]).toBe("moveTo(5,10)");
      });
   });

   describe("direction=2 (right — round right corners)", () => {
      it("calls exactly two arcTo", () => {
         const ctx = makeCtx();
         ChartTool.drawRoundedBar(ctx, 0, 0, 80, 40, 0.2, 2);
         expect(ctx.calls.filter(c => c.startsWith("arcTo")).length).toBe(2);
      });

      it("uses h (not w) as the arc-fraction base for horizontal bars", () => {
         // horizontal: arc = min(radiusFraction * h, min(w,h)/2) = min(0.2*40, 20) = min(8, 20) = 8
         const ctx = makeCtx();
         ChartTool.drawRoundedBar(ctx, 0, 0, 80, 40, 0.2, 2);
         const arcCalls = ctx.calls.filter(c => c.startsWith("arcTo"));
         arcCalls.forEach(c => {
            const r = parseFloat(c.split(",")[4]);
            expect(r).toBeCloseTo(8);
         });
      });
   });

   describe("direction=3 (left — round left corners)", () => {
      it("calls exactly two arcTo", () => {
         const ctx = makeCtx();
         ChartTool.drawRoundedBar(ctx, 0, 0, 80, 40, 0.2, 3);
         expect(ctx.calls.filter(c => c.startsWith("arcTo")).length).toBe(2);
      });

      it("caps arc at min(w,h)/2 when radiusFraction * h is very large", () => {
         // arc = min(1.0 * 40, min(80,40)/2) = min(40, 20) = 20
         const ctx = makeCtx();
         ChartTool.drawRoundedBar(ctx, 0, 0, 80, 40, 1.0, 3);
         const arcCalls = ctx.calls.filter(c => c.startsWith("arcTo"));
         arcCalls.forEach(c => {
            const r = parseFloat(c.split(",")[4]);
            expect(r).toBeCloseTo(20);
         });
      });
   });

   describe("offset coordinates", () => {
      it("all coordinates shift correctly when bar is not at origin", () => {
         const ctxOrigin = makeCtx();
         const ctxOffset = makeCtx();
         const dx = 50, dy = 30;
         ChartTool.drawRoundedBar(ctxOrigin, 0,  0,  100, 50, 0.2, 0);
         ChartTool.drawRoundedBar(ctxOffset, dx, dy, 100, 50, 0.2, 0);

         // Every numeric coordinate in the offset calls should be exactly dx or dy greater
         ctxOrigin.calls.forEach((originCall, i) => {
            const offsetCall = ctxOffset.calls[i];
            if(originCall === "closePath()") {
               expect(offsetCall).toBe("closePath()");
               return;
            }
            const fn = originCall.split("(")[0];
            expect(offsetCall.startsWith(fn)).toBe(true);
            const originNums = originCall.match(/-?\d+(\.\d+)?/g)!.map(Number);
            const offsetNums = offsetCall.match(/-?\d+(\.\d+)?/g)!.map(Number);
            // arcTo has 5 args: x1,y1,x2,y2,r — x/y positions alternate by pair
            if(fn === "arcTo") {
               expect(offsetNums[0]).toBeCloseTo(originNums[0] + dx);
               expect(offsetNums[1]).toBeCloseTo(originNums[1] + dy);
               expect(offsetNums[2]).toBeCloseTo(originNums[2] + dx);
               expect(offsetNums[3]).toBeCloseTo(originNums[3] + dy);
               expect(offsetNums[4]).toBeCloseTo(originNums[4]); // radius unchanged
            }
            else {
               expect(offsetNums[0]).toBeCloseTo(originNums[0] + dx);
               expect(offsetNums[1]).toBeCloseTo(originNums[1] + dy);
            }
         });
      });
   });
});
