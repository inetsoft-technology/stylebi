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
import { buildChromePaths, computeTailPlacement, TOOLTIP_INSET } from "./tooltip-tail-placement";

// Host 206x106 => inner box 200x100 once the 3px inset is removed.
const HOST = { hostWidth: 206, hostHeight: 106 };
const CONTAINER = { x: 0, y: 0, width: 1000, height: 600 };

function vertical(x: number, y: number, container = CONTAINER) {
   return computeTailPlacement({ anchor: { x, y }, ...HOST, container, axis: "vertical" });
}

describe("computeTailPlacement (vertical axis)", () => {
   it("places the box above the anchor when there is room, tail on the bottom edge", () => {
      const p = vertical(500, 400);
      // box bottom sits TAIL_LENGTH above the anchor: 400 - 8 - 100 = 292, less the inset.
      expect(p).toEqual({
         x: 400 - TOOLTIP_INSET, y: 292 - TOOLTIP_INSET, tailSide: "bottom", tailOffset: 100
      });
   });

   it("flips below the anchor when there is not enough room above, tail on the top edge", () => {
      const p = vertical(500, 50);
      expect(p.tailSide).toBe("top");
      expect(p.y).toBe(58 - TOOLTIP_INSET);
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
      expect(p.tailOffset).toBe(163);
   });

   it("clamps the tail clear of the rounded corners", () => {
      const p = vertical(995, 400);
      // 995 - 797 = 198 would overrun the corner; clamped to 200 - 8 - 6.7.
      expect(p.tailOffset).toBeCloseTo(185.3, 5);
   });

   it("returns null when the container cannot hold the box clear of the anchor", () => {
      expect(vertical(60, 60, { x: 0, y: 0, width: 1000, height: 150 })).toBeNull();
   });

   it("returns null when the box is larger than the container", () => {
      expect(vertical(500, 400, { x: 0, y: 0, width: 100, height: 600 })).toBeNull();
   });
});

describe("computeTailPlacement (horizontal axis)", () => {
   function horizontal(x: number, y: number, container = CONTAINER) {
      return computeTailPlacement({ anchor: { x, y }, ...HOST, container, axis: "horizontal" });
   }

   it("places the box right of the anchor when the right side has more room", () => {
      const p = horizontal(200, 300);
      expect(p).toEqual({
         x: 208 - TOOLTIP_INSET, y: 250 - TOOLTIP_INSET, tailSide: "left", tailOffset: 50
      });
   });

   it("places the box left of the anchor when the left side has more room", () => {
      const p = horizontal(800, 300);
      // box right edge sits TAIL_GAP left of the anchor: 800 - 8 - 200 = 592.
      expect(p.tailSide).toBe("right");
      expect(p.x).toBe(592 - TOOLTIP_INSET);
   });

   it("centres the box on the anchor vertically", () => {
      expect(horizontal(200, 300).y).toBe(250 - TOOLTIP_INSET);
   });

   it("keeps the tail over the anchor when the box is clamped by the container top", () => {
      const p = horizontal(200, 20);
      expect(p.y).toBe(3 - TOOLTIP_INSET);
      expect(p.tailOffset).toBe(17);
   });

   it("clamps the tail clear of the rounded corners", () => {
      const p = horizontal(200, 4);
      expect(p.tailOffset).toBeCloseTo(14.7, 5);
   });

   it("returns null when the container is too narrow to clear the anchor", () => {
      expect(horizontal(120, 300, { x: 0, y: 0, width: 210, height: 600 })).toBeNull();
   });
});

describe("buildChromePaths", () => {
   it("sizes the viewport to clear the tail on every side", () => {
      const c = buildChromePaths(200, 100, "bottom", 50);
      expect(c).toMatchObject({
         width: 216, height: 116, boxX: 8, boxY: 8, boxWidth: 200, boxHeight: 100, radius: 8
      });
   });

   it("opens the border at the tail and closes it with the tail path (bottom)", () => {
      const c = buildChromePaths(200, 100, "bottom", 50);
      expect(c.borderPath).toBe("M64.7,108 L200,108 Q208,108 208,100 L208,16 Q208,8 200,8 L16,8 Q8,8 8,16 L8,100 Q8,108 16,108 L51.3,108");
      expect(c.tailPath).toBe("M51.3,108 L58,116 L64.7,108");
   });

   it("opens the border at the tail and closes it with the tail path (top)", () => {
      const c = buildChromePaths(200, 100, "top", 50);
      expect(c.borderPath).toBe("M51.3,8 L16,8 Q8,8 8,16 L8,100 Q8,108 16,108 L200,108 Q208,108 208,100 L208,16 Q208,8 200,8 L64.7,8");
      expect(c.tailPath).toBe("M64.7,8 L58,0 L51.3,8");
   });

   it("puts the tail on the left edge with a vertical offset", () => {
      const c = buildChromePaths(200, 100, "left", 50);
      expect(c.borderPath).toBe("M8,51.3 L8,16 Q8,8 16,8 L200,8 Q208,8 208,16 L208,100 Q208,108 200,108 L16,108 Q8,108 8,100 L8,64.7");
      expect(c.tailPath).toBe("M8,64.7 L0,58 L8,51.3");
   });

   it("puts the tail on the right edge with a vertical offset", () => {
      const c = buildChromePaths(200, 100, "right", 50);
      expect(c.borderPath).toBe("M208,64.7 L208,100 Q208,108 200,108 L16,108 Q8,108 8,100 L8,16 Q8,8 16,8 L200,8 Q208,8 208,16 L208,51.3");
      expect(c.tailPath).toBe("M208,51.3 L216,58 L208,64.7");
   });

   it("traces all four rounded corners regardless of side", () => {
      for(const side of ["top", "bottom", "left", "right"] as const) {
         const c = buildChromePaths(200, 100, side, 50);
         expect(c.borderPath.match(/Q/g)?.length).toBe(4);
      }
   });
});
