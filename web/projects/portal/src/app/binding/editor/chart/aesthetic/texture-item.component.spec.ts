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
import { TestBed } from "@angular/core/testing";
import { ChartConfig } from "../../../../common/util/chart-config";
import { TEXTURE_PATTERNS } from "./texture-patterns";
import { TextureItem } from "./texture-item.component";

describe("TextureItem", () => {
   function render(texture: number): HTMLElement {
      const fixture = TestBed.createComponent(TextureItem);
      fixture.componentInstance.texture = texture;
      fixture.detectChanges();
      return fixture.nativeElement;
   }

   beforeEach(() => TestBed.configureTestingModule({}));

   it("has a design for each of the 20 patterns", () => {
      // the solid fill (-1) plus the patterns 0 to 19
      expect(ChartConfig.TEXTURE_STYLES.length).toBe(TEXTURE_PATTERNS.length + 1);
      expect(ChartConfig.TEXTURE_STYLES[0]).toBe(-1);
   });

   it("draws the solid fill as one square in a frame", () => {
      const el = render(-1);

      expect(el.querySelectorAll("g").length).toBe(0);
      // the fill and the frame
      expect(el.querySelectorAll("rect").length).toBe(2);
      expect(el.querySelector("rect[width='16']")).not.toBeNull();
   });

   it("draws every pattern differently, with no diagonal line", () => {
      const seen = new Set<string>();

      for(const style of ChartConfig.TEXTURE_STYLES.slice(1)) {
         const el = render(style);

         expect(el.querySelectorAll("g").length).toBeGreaterThan(0);
         expect(el.querySelector("line")).toBeNull();
         // the stripes of the old patterns were lines at an angle; nothing is rotated
         expect(el.innerHTML).not.toContain("rotate");
         // svg paint references (url(#id)) break under a <base href> in some browsers
         expect(el.innerHTML).not.toContain("url(");

         const html = el.querySelector("svg svg").innerHTML.replace(/<!--.*?-->/g, "");
         expect(seen.has(html)).toBe(false);
         seen.add(html);
      }

      expect(seen.size).toBe(TEXTURE_PATTERNS.length);
   });

   it("starts the tiles at the bottom-left corner of the swatch", () => {
      for(let i = 0; i < TEXTURE_PATTERNS.length; i++) {
         const el = render(i);
         const tiles = Array.from(el.querySelectorAll("g")).map(g => {
            const m = /translate\(([-\d.e]+) ([-\d.e]+)\) scale\(([\d.]+)\)/
               .exec(g.getAttribute("transform"));
            return { x: Number(m[1]), y: Number(m[2]), scale: Number(m[3]) };
         });
         const scale = tiles[0].scale;
         const h = TEXTURE_PATTERNS[i].h * scale;

         // the lowest row of tiles ends at the bottom of the 16px swatch, the first column
         // starts at its left, and the whole swatch is covered
         expect(Math.max(...tiles.map(t => t.y)) + h).toBeCloseTo(16, 5);
         expect(Math.min(...tiles.map(t => t.x))).toBe(0);
         expect(Math.min(...tiles.map(t => t.y))).toBeLessThanOrEqual(0.01);
      }
   });

   it("shows at least two tiles across, so a large tile is not cut off", () => {
      for(let i = 0; i < TEXTURE_PATTERNS.length; i++) {
         const el = render(i);
         const xs = new Set(Array.from(el.querySelectorAll("g"))
            .map(g => /translate\(([-\d.e]+)/.exec(g.getAttribute("transform"))[1]));

         expect(xs.size).toBeGreaterThanOrEqual(2);
      }
   });

   it("treats an unknown texture as the solid fill", () => {
      expect(render(999).querySelectorAll("g").length).toBe(0);
   });
});
