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
import { BOX_EDGES, EdgeFractions, markEdgeFractions } from "./mark-edges";

// jsdom has no SVG geometry, so each mark is a stand-in with the calls the walk makes. Every
// transform is the identity: the walk's results are fractions of the mark's own box.
class FakePoint {
   constructor(public x = 0, public y = 0) {
   }

   matrixTransform(): FakePoint {
      return this;
   }
}

const IDENTITY: any = { inverse: () => IDENTITY, multiply: () => IDENTITY };

function shape(inFill: (x: number, y: number) => boolean): any {
   return { getCTM: () => IDENTITY, isPointInFill: (p: FakePoint) => inFill(p.x, p.y) };
}

function mark(box: { x: number, y: number, width: number, height: number },
              shapes: any[]): Element
{
   return {
      getBBox: () => box,
      getCTM: () => IDENTITY,
      querySelectorAll: () => shapes
   } as unknown as Element;
}

describe("markEdgeFractions", () => {
   beforeEach(() => vi.stubGlobal("DOMPoint", FakePoint));
   afterEach(() => vi.unstubAllGlobals());

   function expectEdges(actual: EdgeFractions, expected: Partial<EdgeFractions>) {
      for(const edge of Object.keys(expected) as (keyof EdgeFractions)[]) {
         expect(actual[edge]).toBeCloseTo(expected[edge], 2);
      }
   }

   it("finds a circle's edges on the lines through its centre", () => {
      const circle = shape((x, y) => (x - 10) ** 2 + (y - 10) ** 2 <= 100);
      const box = { x: 0, y: 0, width: 20, height: 20 };
      expectEdges(markEdgeFractions(mark(box, [circle]), 0.5, 0.5),
                  { top: 0, bottom: 1, left: 0, right: 1 });
   });

   // A quarter disc centred on its box's bottom-left corner. The renderer's middle for it sits at
   // (40, 60); the box's top edge there is empty, and the arc crosses 8.35px lower.
   it("stops at a wedge's arc rather than its empty box corner", () => {
      const wedge = shape((x, y) => x >= 0 && y <= 100 && x ** 2 + (100 - y) ** 2 <= 10000);
      const box = { x: 0, y: 0, width: 100, height: 100 };
      expectEdges(markEdgeFractions(mark(box, [wedge]), 0.4, 0.6),
                  { top: 0.0835, bottom: 1, left: 0, right: 0.9165 });
   });

   it("counts every shape the mark is drawn with", () => {
      const upper = shape((x, y) => y >= 0 && y <= 50);
      const lower = shape((x, y) => y >= 50 && y <= 100);
      const box = { x: 0, y: 0, width: 10, height: 100 };
      expectEdges(markEdgeFractions(mark(box, [upper, lower]), 0.5, 0.5), { top: 0, bottom: 1 });
   });

   it("works in the mark's user space and answers in fractions of its box", () => {
      const bar = shape((x, y) => x >= 200 && x <= 240 && y >= 50 && y <= 250);
      const box = { x: 200, y: 50, width: 40, height: 200 };
      expectEdges(markEdgeFractions(mark(box, [bar]), 0.5, 0.5),
                  { top: 0, bottom: 1, left: 0, right: 1 });
   });

   it("falls back to the box's edges when the middle misses the mark", () => {
      const ring = shape((x, y) => {
         const d = (x - 50) ** 2 + (y - 50) ** 2;
         return d >= 1600 && d <= 2500;
      });
      expect(markEdgeFractions(mark({ x: 0, y: 0, width: 100, height: 100 }, [ring]), 0.5, 0.5))
         .toEqual(BOX_EDGES);
   });

   it("falls back to the box's edges when nothing in the mark can be hit-tested", () => {
      expect(markEdgeFractions(mark({ x: 0, y: 0, width: 10, height: 10 }, [{}]), 0.5, 0.5))
         .toEqual(BOX_EDGES);
   });

   it("falls back to the box's edges when the browser has no SVG geometry", () => {
      const g = document.createElementNS("http://www.w3.org/2000/svg", "g");
      expect(markEdgeFractions(g, 0.5, 0.5)).toEqual(BOX_EDGES);
   });

   it("falls back to the box's edges for a mark with no area", () => {
      const line = shape(() => true);
      expect(markEdgeFractions(mark({ x: 0, y: 0, width: 10, height: 0 }, [line]), 0.5, 0.5))
         .toEqual(BOX_EDGES);
   });
});
