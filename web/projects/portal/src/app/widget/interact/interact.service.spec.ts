/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
import { Point } from "../../common/data/point";
import { InteractService } from "./interact.service";

// Bug #76370: the composer highlight ring became an outline (PR #4930), so composer objects no
// longer have a 2px border to compensate for. A plain point (toolbox drop / paste) must snap to
// the grid exactly, and a draggable is offset only by its own resizeAdjustment.
describe("InteractService — snap to grid", () => {
   function makeService(options: any = {}): InteractService {
      const zone: any = { runOutsideAngular: (fn: () => any) => fn() };
      const service = new InteractService(zone, {} as any, {} as any);
      service.setContainer({
         element: { nativeElement: document.createElement("div") },
         snapToGrid: true,
         snapToGuides: false,
         snapGridSize: 20,
         snapGuideOffset: 0,
         ...options
      } as any);
      return service;
   }

   // a composer object editor whose box is at the given page bounds
   function makeDraggable(resizeAdjustment: number,
                          bounds = { left: 0, top: 0, width: 0, height: 0 }): any
   {
      const element = document.createElement("div");
      element.className = "object-editor outline-highlight";
      const rect = { ...bounds, right: bounds.left + bounds.width,
                     bottom: bounds.top + bounds.height };
      vi.spyOn(element, "getClientRects").mockReturnValue([rect] as any);
      vi.spyOn(element, "getBoundingClientRect").mockReturnValue(rect as any);
      return { element: { nativeElement: element }, resizeAdjustment };
   }

   // x/y are what interact passes: the element's start top-left plus the pointer delta
   function snapDraggable(service: InteractService, draggable: any, x: number, y: number,
                          edges: any = null): any
   {
      const config = (service as any).createSnapConfig([draggable]);
      return config.targets[0](x, y, { prepared: { edges } });
   }

   afterEach(() => vi.restoreAllMocks());

   it("snaps a plain point to the exact grid", () => {
      const p = makeService().snap(new Point(137, 138));
      expect(p.x).toBe(140);
      expect(p.y).toBe(140);
   });

   it("snaps a draggable with resizeAdjustment 0 to the exact grid", () => {
      const p = snapDraggable(makeService(), makeDraggable(0), 137, 138);
      expect(p.x).toBe(140);
      expect(p.y).toBe(140);
   });

   it("offsets a draggable by its resizeAdjustment (default 2)", () => {
      const p = snapDraggable(makeService(), makeDraggable(2), 137, 138);
      expect(p.x).toBe(138);
      expect(p.y).toBe(138);
   });

   it("snaps a drag at the canvas origin to 0,0, never negative", () => {
      const p = snapDraggable(makeService(), makeDraggable(0), -3, -4);
      expect(p.x).toBe(0);
      expect(p.y).toBe(0);
   });

   it("snaps the right/bottom edges of a resize to the exact grid", () => {
      const bounds = { left: 100, top: 100, width: 183, height: 121 };
      const p = snapDraggable(makeService(), makeDraggable(0, bounds), 100 + 190, 100 + 150,
                              { right: true, bottom: true });
      // interact moves the right/bottom edge by the same delta as the returned top-left
      expect(p.x + bounds.width).toBe(480);
      expect(p.y + bounds.height).toBe(380);
   });

   it("snaps the left/top edges of a resize to the exact grid", () => {
      const bounds = { left: 100, top: 100, width: 183, height: 121 };
      const p = snapDraggable(makeService(), makeDraggable(0, bounds), 100 - 23, 100 - 41,
                              { left: true, top: true });
      expect(p.x).toBe(80);
      expect(p.y).toBe(60);
   });

   it("snaps to an object guide exactly when the grid is off", () => {
      const service = makeService({
         snapToGrid: false,
         snapToGuides: true,
         snapGuideRange: 10,
         snapVerticalGuides: [298],
         snapHorizontalGuides: [238]
      });
      const p = snapDraggable(service, makeDraggable(0, { left: 0, top: 0, width: 50, height: 50 }),
                              293, 235);
      expect(p.x).toBe(298);
      expect(p.y).toBe(238);
   });

   function guideService(vertical: number[], horizontal: number[]): InteractService {
      return makeService({
         snapToGrid: false,
         snapToGuides: true,
         snapGuideRange: 10,
         snapVerticalGuides: vertical,
         snapHorizontalGuides: horizontal
      });
   }

   it("snaps the right/bottom edges of a move exactly onto object guides", () => {
      const bounds = { left: 100, top: 100, width: 50, height: 40 };
      const p = snapDraggable(guideService([348], [278]), makeDraggable(0, bounds), 297, 236);
      expect(p.x + bounds.width).toBe(348);
      expect(p.y + bounds.height).toBe(278);
   });

   it("snaps the center of a move exactly onto object center guides", () => {
      const bounds = { left: 100, top: 100, width: 50, height: 40 };
      // center guides are passed as negative values
      const p = snapDraggable(guideService([-323], [-258]), makeDraggable(0, bounds), 297, 236);
      expect(p.x + bounds.width / 2).toBe(323);
      expect(p.y + bounds.height / 2).toBe(258);
   });

   it("snaps the right/bottom edges of a resize exactly onto object guides", () => {
      const bounds = { left: 100, top: 100, width: 183, height: 121 };
      const p = snapDraggable(guideService([477], [373]), makeDraggable(0, bounds), 292, 250,
                              { right: true, bottom: true });
      expect(p.x + bounds.width).toBe(477);
      expect(p.y + bounds.height).toBe(373);
   });
});
