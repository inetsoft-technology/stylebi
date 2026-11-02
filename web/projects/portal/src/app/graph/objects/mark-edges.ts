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

/** Where a mark's outline crosses the lines through its middle, as fractions of its box. */
export interface EdgeFractions {
   top: number;
   bottom: number;
   left: number;
   right: number;
}

export const BOX_EDGES: EdgeFractions = Object.freeze({ top: 0, bottom: 1, left: 0, right: 1 });

/** Even steps along each ray before bisecting the one that left the mark. */
const RAY_STEPS = 48;
const RAY_BISECTIONS = 6;

type HitTest = (x: number, y: number) => boolean;

/**
 * Where the mark's outline crosses the vertical and horizontal lines through the point at
 * (fx, fy) of its box. Walks out from that point against the mark's own shapes, because a box
 * edge can sit well off the mark: a wedge's box corner is empty. Falls back to the box's edges
 * when the browser cannot hit-test the shapes or the point misses them.
 *
 * Works in the mark's own user space and answers in fractions of its box, as the renderer's
 * data-anchor does, so the result holds under the scale and translate its tile applies.
 */
export function markEdgeFractions(mark: Element, fx: number, fy: number): EdgeFractions {
   const graphic = mark as SVGGraphicsElement;

   if(typeof DOMPoint === "undefined" || typeof graphic.getBBox !== "function" ||
      typeof graphic.getCTM !== "function")
   {
      return BOX_EDGES;
   }

   const box = graphic.getBBox();
   const markCtm = graphic.getCTM();

   if(!markCtm || !(box.width > 0) || !(box.height > 0)) {
      return BOX_EDGES;
   }

   const tests = [mark, ...Array.from(mark.querySelectorAll("*"))]
      .map(shape => hitTest(shape, markCtm))
      .filter(test => !!test);
   const inside = (x: number, y: number) => tests.some(test => test(x, y));
   const x = box.x + fx * box.width;
   const y = box.y + fy * box.height;

   if(!inside(x, y)) {
      return BOX_EDGES;
   }

   const up = reach(d => inside(x, y - d), y - box.y);
   const down = reach(d => inside(x, y + d), box.y + box.height - y);
   const back = reach(d => inside(x - d, y), x - box.x);
   const ahead = reach(d => inside(x + d, y), box.x + box.width - x);

   return {
      top: (y - up - box.y) / box.height,
      bottom: (y + down - box.y) / box.height,
      left: (x - back - box.x) / box.width,
      right: (x + ahead - box.x) / box.width
   };
}

/** Tests a point in the mark's user space against one shape's fill; null if it is not a shape. */
function hitTest(shape: Element, markCtm: DOMMatrix): HitTest | null {
   const geometry = shape as SVGGeometryElement;

   if(typeof geometry.isPointInFill !== "function" || typeof geometry.getCTM !== "function") {
      return null;
   }

   const ctm = geometry.getCTM();

   if(!ctm) {
      return null;
   }

   const toShape = ctm.inverse().multiply(markCtm);
   return (x, y) => geometry.isPointInFill(new DOMPoint(x, y).matrixTransform(toShape));
}

/** How far a ray from inside the mark runs before it first leaves it, at most max. */
function reach(inside: (d: number) => boolean, max: number): number {
   for(let i = 1; i <= RAY_STEPS; i++) {
      const d = max * i / RAY_STEPS;

      if(!inside(d)) {
         let lo = max * (i - 1) / RAY_STEPS;
         let hi = d;

         for(let j = 0; j < RAY_BISECTIONS; j++) {
            const mid = (lo + hi) / 2;

            if(inside(mid)) {
               lo = mid;
            }
            else {
               hi = mid;
            }
         }

         return (lo + hi) / 2;
      }
   }

   return max;
}
