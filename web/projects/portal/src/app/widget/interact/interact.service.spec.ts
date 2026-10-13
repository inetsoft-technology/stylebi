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
   function makeService(): InteractService {
      const zone: any = { runOutsideAngular: (fn: () => any) => fn() };
      const service = new InteractService(zone, {} as any, {} as any);
      service.setContainer({
         element: { nativeElement: document.createElement("div") },
         snapToGrid: true,
         snapToGuides: false,
         snapGridSize: 20
      } as any);
      return service;
   }

   function makeDraggable(resizeAdjustment: number): any {
      return { element: { nativeElement: document.createElement("div") }, resizeAdjustment };
   }

   function snapDraggable(service: InteractService, draggable: any, x: number, y: number): any {
      const config = (service as any).createSnapConfig([draggable]);
      return config.targets[0](x, y, { prepared: { edges: null } });
   }

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
});
