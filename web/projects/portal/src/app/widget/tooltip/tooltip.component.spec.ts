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
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { TooltipComponent } from "./tooltip.component";
import { TailSide } from "./tooltip-tail-placement";

describe("TooltipComponent chrome", () => {
   let fixture: ComponentFixture<TooltipComponent>;
   let comp: TooltipComponent;

   beforeEach(async() => {
      await TestBed.configureTestingModule({ imports: [TooltipComponent] }).compileComponents();
      fixture = TestBed.createComponent(TooltipComponent);
      comp = fixture.componentInstance;
      comp.content = "hello";
      comp.tooltipCSS = "widget__card-tooltip";
   });

   function render(tailSide: TailSide | null, tailOffset = 50): HTMLElement {
      comp.tailSide = tailSide;
      comp.tailOffset = tailOffset;
      comp.boxSize = tailSide ? { width: 200, height: 100 } : null;
      comp.updateView();
      return fixture.nativeElement;
   }

   it("renders no chrome when there is no tail", () => {
      expect(render(null).querySelector(".tooltip-chrome")).toBeNull();
   });

   it("leaves the box skin untouched when there is no tail", () => {
      const box = render(null).querySelector(".widget__card-tooltip");
      expect(box.classList.contains("widget__card-tooltip--tailed")).toBe(false);
   });

   it("renders the chrome as a sibling of the box, never inside it", () => {
      const el = render("bottom");
      expect(el.querySelector(".tooltip-container > .tooltip-chrome")).not.toBeNull();
      expect(el.querySelector(".widget__card-tooltip .tooltip-chrome")).toBeNull();
   });

   it("sizes the chrome to clear the tail and offsets it over the box", () => {
      const svg = render("bottom").querySelector(".tooltip-chrome") as SVGElement;
      expect(svg.getAttribute("width")).toBe("216");
      expect(svg.getAttribute("height")).toBe("116");
      expect((svg as unknown as HTMLElement).style.left).toBe("-5px");
      expect((svg as unknown as HTMLElement).style.top).toBe("-5px");
   });

   it("draws a background rect, an open border path and a tail path", () => {
      const el = render("bottom");
      expect(el.querySelector(".tooltip-chrome__bg").getAttribute("rx")).toBe("8");
      expect(el.querySelector(".tooltip-chrome__border").getAttribute("d")).toContain("Q");
      expect(el.querySelector(".tooltip-chrome__tail").getAttribute("d"))
         .toBe("M51.3,108 L58,116 L64.7,108");
   });

   it("marks the box as tailed so the skin drops its own background and border", () => {
      const box = render("bottom").querySelector(".widget__card-tooltip");
      expect(box.classList.contains("widget__card-tooltip--tailed")).toBe(true);
   });

   it("moves the tail to the matching edge for each side", () => {
      expect(render("top").querySelector(".tooltip-chrome__tail").getAttribute("d"))
         .toBe("M64.7,8 L58,0 L51.3,8");
      expect(render("left").querySelector(".tooltip-chrome__tail").getAttribute("d"))
         .toBe("M8,64.7 L0,58 L8,51.3");
      expect(render("right").querySelector(".tooltip-chrome__tail").getAttribute("d"))
         .toBe("M208,51.3 L216,58 L208,64.7");
   });
});
