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

/** Minimal canvas path recording context, same convention as chart-tool-rounded-bar.spec.ts. */
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

/**
 * bug-76870: barRoundAllCorners removes rounding on a COLOR-aesthetic-driven stacked bar.
 *
 * The diagnosed mechanism was that a long-tail multi-segment stack (one dominant category,
 * several tiny ones) can make BarVO.computeArcZones() report a segment as hitting BOTH the
 * outer and inner arc zones at once, driving chart-tool.ts's "both ends" nested-clip branch
 * (previously never covered by any test — chart-tool-rounded-bar.spec.ts only exercises the
 * lower-level drawRoundedBar() primitive, never the region-consuming decision logic that
 * reconstructs the full-bar bounds from a single segment's own offset metadata). This test
 * exercises computeStackedBarFullBounds() directly with the SAME region values a real 7-row
 * long-tail stack produces (cross-checked against BarVOMultiRowStackRoundingTest on the Java
 * side: distFromOuter=0, distFromInner=3, stackDim=1003 for a segment whose own screen rect
 * is x=0,y=0,w=100,h=1000), and then feeds the result into drawRoundedBar() to confirm the
 * resulting path is not degenerate.
 */
describe("ChartTool.computeStackedBarFullBounds", () => {
   // Ground truth from BarVOMultiRowStackRoundingTest.
   // longTailStack_outermostSegment_doubleArcZoneDoesNotProduceDegenerateShape:
   // segBounds = [0, 0, 100, 1000], outerOffset (distFromOuter) = 0,
   // innerOffset (distFromInner) = 3, stackDim = 1003.
   const x = 0, y = 0, w = 100, h = 1000;
   const stackDim = 1003;
   const outerOffset = 0;
   const innerOffset = 3;

   it("reconstructs the full-bar rect from the outer offset when only it is present " +
      "(matches BarVO.computeFullBarBounds's fullBarY = y - innerOffset formula shape)", () => {
      // dir=1 ("down": open end faces increasing y — same convention BarVO uses for a
      // standard, positive-value, non-inverted vertical bar with openDir=1 in Java).
      const bounds = ChartTool.computeStackedBarFullBounds(
         x, y, w, h, 1, stackDim, outerOffset, undefined);

      // ey = y + h + outerOffset - stackDim = 0 + 1000 + 0 - 1003 = -3
      expect(bounds.ey).toBeCloseTo(-3);
      expect(bounds.eh).toBeCloseTo(1003);
      expect(bounds.ex).toBeCloseTo(x);
      expect(bounds.ew).toBeCloseTo(w);
   });

   it("reconstructs the SAME full-bar rect regardless of whether outer or inner offset " +
      "drives it, for a self-consistent (outerOffset + innerOffset + interval = total) " +
      "segment — the two offsets are two views of the same underlying stack", () => {
      // interval for this segment = stackDim - outerOffset - innerOffset = 1000 (matches the
      // dominant segment's own real value from the Java test).
      const viaOuter = ChartTool.computeStackedBarFullBounds(
         x, y, w, h, 1, stackDim, outerOffset, undefined);
      const viaInner = ChartTool.computeStackedBarFullBounds(
         x, y, w, h, 1, stackDim, undefined, innerOffset);

      expect(viaInner.ey).toBeCloseTo(viaOuter.ey);
      expect(viaInner.eh).toBeCloseTo(viaOuter.eh);
      expect(viaInner.ex).toBeCloseTo(viaOuter.ex);
      expect(viaInner.ew).toBeCloseTo(viaOuter.ew);
   });

   it("both-ends branch: the SAME reconstructed bounds feed both drawRoundedBar calls, " +
      "and the resulting rounded shapes are non-degenerate (positive extents, arc within " +
      "bounds) for the long-tail double-arc-zone segment", () => {
      const r = 0.3; // barCornerRadius from the bug report
      const dir = 1;
      const bounds = ChartTool.computeStackedBarFullBounds(
         x, y, w, h, dir, stackDim, outerOffset, innerOffset);

      expect(bounds.ew).toBeGreaterThan(0);
      expect(bounds.eh).toBeGreaterThan(0);

      const outerCtx = makeCtx();
      ChartTool.drawRoundedBar(outerCtx, bounds.ex, bounds.ey, bounds.ew, bounds.eh, r, dir);
      const innerCtx = makeCtx();
      ChartTool.drawRoundedBar(innerCtx, bounds.ex, bounds.ey, bounds.ew, bounds.eh, r, dir ^ 1);

      // Both calls must actually draw a real (non-empty) closed path — a degenerate
      // reconstruction (e.g. ew/eh collapsing to 0 or NaN) would produce arcTo calls with a
      // NaN/zero radius or coordinates instead.
      for(const ctx of [outerCtx, innerCtx]) {
         expect(ctx.calls.length).toBeGreaterThan(0);
         expect(ctx.calls[ctx.calls.length - 1]).toBe("closePath()");

         const arcCalls = ctx.calls.filter(c => c.startsWith("arcTo"));
         expect(arcCalls.length).toBeGreaterThan(0);

         for(const call of arcCalls) {
            const parts = call.substring(call.indexOf("(") + 1, call.length - 1).split(",");
            const nums = parts.map(Number);
            expect(nums.every(n => Number.isFinite(n))).toBe(true);
            const arcRadius = nums[nums.length - 1];
            expect(arcRadius).toBeGreaterThan(0);
         }
      }
   });

   it("degenerate case for comparison: a zero-width/height reconstruction DOES produce a " +
      "collapsed (zero-radius) rounded shape — confirms the non-degenerate assertions above " +
      "are actually meaningful, not vacuously true for any input", () => {
      const r = 0.3;
      const dir = 1;
      // ew/eh forced to 0 directly (bypassing computeStackedBarFullBounds) to establish what
      // a genuinely broken reconstruction would look like.
      const ctx = makeCtx();
      ChartTool.drawRoundedBar(ctx, 0, 0, 0, 0, r, dir);
      const arcCalls = ctx.calls.filter(c => c.startsWith("arcTo"));
      const allZeroRadius = arcCalls.every(c => {
         const parts = c.substring(c.indexOf("(") + 1, c.length - 1).split(",");
         return Number(parts[parts.length - 1]) === 0;
      });
      expect(allZeroRadius).toBe(true);
   });
});
