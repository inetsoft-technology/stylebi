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
import {
   buildChromePaths, computeTailPlacement, TailAnchor, TAIL_HALF_WIDTH, tailRadius, TailSide,
   TOOLTIP_INSET
} from "./tooltip-tail-placement";

// Host 206x106 => inner box 200x100 once the 3px inset is removed.
const HOST = { hostWidth: 206, hostHeight: 106 };

// Every geometry expectation below is a gate-off one (8px corners), so a viz-shell class
// leaked by another spec would break them as unreadable path mismatches.
beforeEach(() => document.body.classList.remove("viz-shell"));
afterEach(() => document.body.classList.remove("viz-shell"));
const CONTAINER = { x: 0, y: 0, width: 1000, height: 600 };

function vertical(x: number, y: number, container = CONTAINER) {
   return verticalAt({ x, y }, container);
}

function verticalAt(anchor: TailAnchor, container = CONTAINER) {
   return computeTailPlacement({ anchor, ...HOST, container, axis: "vertical" });
}

describe("computeTailPlacement (vertical axis)", () => {
   it("places the box above the anchor when there is room, tail on the bottom edge", () => {
      const p = vertical(500, 400);
      // box bottom sits TAIL_LENGTH above the anchor: 400 - 12 - 100 = 288, less the inset.
      expect(p).toEqual({
         x: 400 - TOOLTIP_INSET, y: 288 - TOOLTIP_INSET, tailSide: "bottom", tailOffset: 100
      });
   });

   it("flips below the anchor when there is not enough room above, tail on the top edge", () => {
      const p = vertical(500, 50);
      expect(p.tailSide).toBe("top");
      expect(p.y).toBe(62 - TOOLTIP_INSET);
   });

   it("biases sideways away from the nearer edge when placed below", () => {
      // belowOffset = clamp(200 * 0.18, 24, 56) = 36; more room to the right, so shift right.
      expect(vertical(500, 50).x).toBe(436 - TOOLTIP_INSET);
      // nearer the right edge, so shift left instead.
      expect(vertical(900, 50).x).toBe(764 - TOOLTIP_INSET);
   });

   it("keeps the tail over the anchor when the box is clamped by the container edge", () => {
      const p = vertical(960, 400);
      // box clamped to the right edge, so the tail slides off-centre to stay on the anchor.
      expect(p.x).toBe(797 - TOOLTIP_INSET);
      expect(p.tailOffset).toBeCloseTo(163, 9);
   });

   it("hooks the tip past the last opening the corner leaves room for", () => {
      const p = vertical(995, 400);
      // 995 - 797 = 198. The opening stops at 200 - 8 - 6.5 = 185.5 and the tip hooks
      // 1.8 half-widths past it, so it lands 0.8px short of the anchor.
      expect(p.tailOffset).toBeCloseTo(197.2, 9);
   });

   it("returns null when the container cannot hold the box clear of the anchor", () => {
      expect(vertical(60, 60, { x: 0, y: 0, width: 1000, height: 150 })).toBeNull();
   });

   it("returns null when the box is larger than the container", () => {
      expect(vertical(500, 400, { x: 0, y: 0, width: 100, height: 600 })).toBeNull();
   });

   it("reaches 1px into the mark's top edge when the box goes above", () => {
      const p = verticalAt({ x: 500, y: 400, top: 380, bottom: 420 });
      // tip at 381; 381 - 12 - 100 = 269: the box clears the mark, not just its middle.
      expect(p).toEqual({
         x: 400 - TOOLTIP_INSET, y: 269 - TOOLTIP_INSET, tailSide: "bottom", tailOffset: 100
      });
   });

   it("reaches 1px into the mark's bottom edge when the box goes below", () => {
      const p = verticalAt({ x: 500, y: 60, top: 40, bottom: 80 });
      // tip at 79
      expect(p.tailSide).toBe("top");
      expect(p.y).toBe(91 - TOOLTIP_INSET);
   });

   it("never reaches past the middle of a mark thinner than the overlap", () => {
      const p = verticalAt({ x: 500, y: 400, top: 399.5, bottom: 400.5 });
      expect(p.y).toBe(288 - TOOLTIP_INSET);
   });

   it("measures the room above from the mark's top edge, not its middle", () => {
      // The middle would leave 127px above, enough for the box; the top edge leaves 98px.
      const p = verticalAt({ x: 500, y: 130, top: 100, bottom: 160 });
      expect(p.tailSide).toBe("top");
      expect(p.y).toBe(171 - TOOLTIP_INSET);
   });

   it("falls back to the middle when the mark leaves no room past either edge", () => {
      // A bar from y=20 to y=580 in a 600px window: no edge can clear the box.
      const p = verticalAt({ x: 500, y: 300, top: 20, bottom: 580 });
      expect(p).toEqual({
         x: 400 - TOOLTIP_INSET, y: 188 - TOOLTIP_INSET, tailSide: "bottom", tailOffset: 100
      });
   });
});

describe("computeTailPlacement (horizontal axis)", () => {
   function horizontal(x: number, y: number, container = CONTAINER) {
      return horizontalAt({ x, y }, container);
   }

   function horizontalAt(anchor: TailAnchor, container = CONTAINER) {
      return computeTailPlacement({ anchor, ...HOST, container, axis: "horizontal" });
   }

   it("places the box right of the anchor when the right side has more room", () => {
      const p = horizontal(200, 300);
      expect(p).toEqual({
         x: 212 - TOOLTIP_INSET, y: 250 - TOOLTIP_INSET, tailSide: "left", tailOffset: 50
      });
   });

   it("places the box left of the anchor when the left side has more room", () => {
      const p = horizontal(800, 300);
      // box right edge sits TAIL_LENGTH left of the anchor: 800 - 12 - 200 = 588.
      expect(p.tailSide).toBe("right");
      expect(p.x).toBe(588 - TOOLTIP_INSET);
   });

   it("centres the box on the anchor vertically", () => {
      expect(horizontal(200, 300).y).toBe(250 - TOOLTIP_INSET);
   });

   it("keeps the tail over the anchor when the box is clamped by the container top", () => {
      const p = horizontal(200, 20);
      expect(p.y).toBe(3 - TOOLTIP_INSET);
      expect(p.tailOffset).toBeCloseTo(17, 9);
   });

   it("hooks the tip past the last opening the corner leaves room for", () => {
      const p = horizontal(200, 4);
      // The opening stops at 8 + 6.5 = 14.5 and the tip hooks 11.7px above it.
      expect(p.tailOffset).toBeCloseTo(2.8, 9);
   });

   it("returns null when the container is too narrow to clear the anchor", () => {
      expect(horizontal(120, 300, { x: 0, y: 0, width: 210, height: 600 })).toBeNull();
   });

   it("reaches 1px into the mark's right edge when the box goes right", () => {
      const p = horizontalAt({ x: 200, y: 300, left: 180, right: 220 });
      // tip at 219
      expect(p.tailSide).toBe("left");
      expect(p.x).toBe(231 - TOOLTIP_INSET);
   });

   it("reaches 1px into the mark's left edge when the box goes left", () => {
      const p = horizontalAt({ x: 800, y: 300, left: 780, right: 820 });
      // tip at 781; 781 - 12 - 200 = 569
      expect(p.tailSide).toBe("right");
      expect(p.x).toBe(569 - TOOLTIP_INSET);
   });
});

describe("buildChromePaths", () => {
   /** Every coordinate pair in a path, in order. */
   function points(path: string): [number, number][] {
      return (path.match(/-?[\d.]+,-?[\d.]+/g) ?? [])
         .map(pair => pair.split(",").map(Number) as [number, number]);
   }

   it("sizes the viewport to clear the tail on every side", () => {
      const c = buildChromePaths(200, 100, "bottom", 50);
      expect(c).toMatchObject({ width: 224, height: 124, radius: 8 });
   });

   it("traces the box and a hooked tail as one closed outline (bottom)", () => {
      // Box at (12, 12)-(212, 112). Tip at 50 sweeps toward the left corner, so the opening's
      // centre sits 1.8 half-widths to its right: 61.7, opening 55.2-68.2. The near leg bends
      // out of (67.2, 112) and the far leg sweeps in from (80.2, 112) to the tip at (62, 124).
      expect(buildChromePaths(200, 100, "bottom", 50).outlinePath).toBe(
         "M67.2,112 L20,112 Q12,112 12,104 L12,20 Q12,12 20,12 L204,12 Q212,12 212,20" +
         " L212,104 Q212,112 204,112 L80.2,112 Q74.87,120.04 62,124 Q67.2,121 67.2,112 Z");
   });

   it("closes every side's outline in one path with four corners and two tail legs", () => {
      for(const side of ["top", "bottom", "left", "right"] as TailSide[]) {
         const path = buildChromePaths(200, 100, side, 50).outlinePath;
         expect(path.match(/M/g)?.length).toBe(1);
         expect(path.match(/Q/g)?.length).toBe(6);
         expect(path.endsWith(" Z")).toBe(true);
      }
   });

   it("lands the tip at the offset, TAIL_LENGTH out from the tailed edge", () => {
      const tips: Record<TailSide, string> = {
         top: "62,0", bottom: "62,124", left: "0,62", right: "224,62"
      };

      for(const side of ["top", "bottom", "left", "right"] as TailSide[]) {
         expect(buildChromePaths(200, 100, side, 50).outlinePath).toContain(` ${tips[side]} Q`);
      }
   });

   it("hooks the tip toward the nearer corner", () => {
      // The opening's two ends are the path's first point and the point the tail starts from.
      for(const [tip, left] of [[50, false], [150, true]] as [number, boolean][]) {
         const all = points(buildChromePaths(200, 100, "bottom", tip).outlinePath);
         const opening = [all[0], all[all.length - 5]];

         for(const [x, y] of opening) {
            expect(y).toBe(112);
            expect(x < 12 + tip).toBe(left);
         }
      }
   });

   // An edge shorter than 2 * (radius + half-width) cannot seat the opening between the corner
   // arcs. The radius has to give way, or the opening lands inside the arcs and the outline
   // doubles back on itself.
   it("keeps the tail opening clear of the corner arcs on a short edge", () => {
      for(const edge of [4, 10, 20, 25, 28, 29, 60, 200]) {
         const halfWidth = Math.min(TAIL_HALF_WIDTH, edge / 2);

         for(const side of ["top", "bottom", "left", "right"] as TailSide[]) {
            const horizontal = side === "top" || side === "bottom";
            const c = buildChromePaths(horizontal ? edge : 200, horizontal ? 100 : edge,
                                       side, edge / 2);

            expect(c.radius).toBeGreaterThanOrEqual(0);
            expect(c.radius + halfWidth).toBeLessThanOrEqual(edge / 2 + 1e-9);
            expect(c.outlinePath.match(/Q/g)?.length).toBe(6);
         }
      }
   });

   it("leaves the radius alone once the edge can seat the opening", () => {
      // 2 * (8 + 6.5) = 29 is the first edge that keeps the full radius
      expect(buildChromePaths(29, 100, "top", 14.5).radius).toBe(8);
      expect(buildChromePaths(200, 29, "left", 14.5).radius).toBe(8);
   });

   it("draws the tip where the placement put it", () => {
      const p = vertical(995, 400);
      const c = buildChromePaths(200, 100, p.tailSide, p.tailOffset);
      expect(c.outlinePath).toContain(" 209.2,124 Q");
   });
});

describe("tailRadius (shell state)", () => {
   it("returns the legacy 8px radius when the gate is off", () => {
      expect(tailRadius()).toBe(8);
   });

   it("returns the shipped 6px card radius when the gate is on", () => {
      document.body.classList.add("viz-shell");
      expect(tailRadius()).toBe(6);
   });

   it("shrinks buildChromePaths' corner radius under the gate, tail geometry unchanged", () => {
      document.body.classList.add("viz-shell");
      const c = buildChromePaths(200, 100, "bottom", 50);
      expect(c.radius).toBe(6);
      expect(c.outlinePath).toBe(
         "M67.2,112 L18,112 Q12,112 12,106 L12,18 Q12,12 18,12 L206,12 Q212,12 212,18" +
         " L212,106 Q212,112 206,112 L80.2,112 Q74.87,120.04 62,124 Q67.2,121 67.2,112 Z");
   });
});
