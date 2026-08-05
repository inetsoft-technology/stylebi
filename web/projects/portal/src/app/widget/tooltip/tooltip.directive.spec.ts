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
import { Component } from "@angular/core";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { TooltipDirective } from "./tooltip.directive";

@Component({
   standalone: true,
   imports: [TooltipDirective],
   template: `<div [wTooltip]="'hover text'" [showTail]="showTail" [tailAxis]="'vertical'"
                   [tailAnchor]="anchor" [followCursor]="true" [waitTime]="0"></div>`
})
class HostComponent {
   showTail = true;
   mark: { x: number, y: number } | null = { x: 510, y: 410 };
   anchor = () => this.mark;
}

function rect(left: number, top: number, width: number, height: number): DOMRect {
   return {
      left, top, width, height, right: left + width, bottom: top + height,
      x: left, y: top, toJSON: () => ({})
   } as DOMRect;
}

describe("TooltipDirective tail placement", () => {
   let fixture: ComponentFixture<HostComponent>;
   let host: HTMLElement;

   beforeEach(async() => {
      // jsdom has no layout; place the container at 1000x600 and the tooltip host at 206x106.
      vi.spyOn(Element.prototype, "getBoundingClientRect").mockImplementation(function(this: Element) {
         return this.tagName.toLowerCase() === "w-tooltip" ? rect(0, 0, 206, 106) : rect(0, 0, 1000, 600);
      });

      await TestBed.configureTestingModule({ imports: [HostComponent] }).compileComponents();
      fixture = TestBed.createComponent(HostComponent);
      fixture.detectChanges();
      host = fixture.nativeElement.querySelector("div");
   });

   afterEach(() => {
      document.querySelectorAll("w-tooltip").forEach(e => e.remove());
      vi.restoreAllMocks();
   });

   function hover(clientX: number, clientY: number): HTMLElement {
      host.dispatchEvent(new MouseEvent("mouseenter"));
      host.dispatchEvent(new MouseEvent("mousemove", { clientX, clientY }));
      return document.querySelector("w-tooltip");
   }

   it("places the tooltip from the mark's middle, not the cursor", () => {
      // Cursor is at (120, 120) but the mark's middle is (510, 410); the box goes above it.
      const tip = hover(120, 120);
      expect(tip.style.top).toBe("299px");
      expect(tip.style.left).toBe("407px");
   });

   it("renders a tail on the edge facing the anchor", () => {
      expect(hover(120, 120).querySelector(".tooltip-chrome__tail")).not.toBeNull();
   });

   it("does not reposition while the mark is unchanged", () => {
      const tip = hover(120, 120);
      const top = tip.style.top;
      host.dispatchEvent(new MouseEvent("mousemove", { clientX: 300, clientY: 300 }));
      expect(tip.style.top).toBe(top);
   });

   it("repositions when the mark changes", () => {
      const tip = hover(120, 120);
      const top = tip.style.top;
      fixture.componentInstance.mark = { x: 510, y: 50 };
      host.dispatchEvent(new MouseEvent("mousemove", { clientX: 121, clientY: 121 }));
      expect(tip.style.top).not.toBe(top);
   });

   // A wide donut wedge's middle is its polar mid-point, supplied by the renderer. It sits on
   // the wedge, unlike the bounding-box centre, which lands in the hole.
   it("places the tooltip at a wedge's polar mid-point", () => {
      fixture.componentInstance.mark = { x: 280, y: 430 };
      const tip = hover(300, 400);
      expect(tip.style.top).toBe("319px");
      expect(tip.style.left).toBe("177px");
   });

   // With snap-to-nearest on a stacked bar the highlight spans the column, but the tip
   // describes one segment, so the anchor is that segment's middle rather than the column's.
   it("places the tooltip at a stacked segment's middle", () => {
      fixture.componentInstance.mark = { x: 219, y: 200 };
      const tip = hover(195, 173);
      expect(tip.style.top).toBe("89px");
      expect(tip.style.left).toBe("116px");
   });

   it("falls back to cursor placement with no tail when there is no anchor", () => {
      fixture.componentInstance.mark = null;
      const tip = hover(120, 120);
      expect(tip.style.left).toBe("135px");
      expect(tip.style.top).toBe("135px");
      expect(tip.querySelector(".tooltip-chrome")).toBeNull();
   });

   it("uses cursor placement unchanged when showTail is off", () => {
      fixture.componentInstance.showTail = false;
      fixture.detectChanges();
      const tip = hover(120, 120);
      expect(tip.style.left).toBe("135px");
      expect(tip.style.top).toBe("135px");
      expect(tip.querySelector(".tooltip-chrome")).toBeNull();
   });
});
