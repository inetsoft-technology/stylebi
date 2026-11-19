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

/**
 * WizDraggableButtonDirective — drag, click and persistence contract of the floating Wiz button.
 *
 * jsdom has no layout, so getBoundingClientRect is stubbed: a 48x48 button at (left, top) that
 * follows the inline style the directive writes, and a portal toolbar whose bottom edge is 50px.
 */

import { Component } from "@angular/core";
import { TestBed } from "@angular/core/testing";
import { WIZ_BUTTON_POSITION_KEY, WizDraggableButtonDirective } from "./wiz-draggable-button.directive";

@Component({
   imports: [WizDraggableButtonDirective],
   template: `<a id="btn" href="https://wiz.example.com/" wizDraggableButton>wiz</a>`
})
class HostComponent {
}

function pointer(type: string, x: number, y: number, id = 1): Event {
   const event = new MouseEvent(type, { clientX: x, clientY: y, button: 0, bubbles: true, cancelable: true });
   Object.defineProperty(event, "pointerId", { value: id });
   Object.defineProperty(event, "pointerType", { value: "mouse" });
   return event;
}

describe("WizDraggableButtonDirective", () => {
   let button: HTMLAnchorElement;
   let clicks: number;
   let toolbar: HTMLElement;

   function create(): void {
      // The button's box follows whatever inline position the directive applied. Installed before
      // the first change detection because the directive measures the host in ngAfterViewInit.
      vi.spyOn(HTMLElement.prototype, "getBoundingClientRect").mockImplementation(function (this: HTMLElement) {
         if(this === toolbar) {
            return { bottom: 50 } as DOMRect;
         }

         const left = parseFloat(this.style.left) || 900;
         const top = parseFloat(this.style.top) || 72;
         return { left, top, right: left + 48, bottom: top + 48, width: 48, height: 48,
                  x: left, y: top, toJSON: () => ({}) } as DOMRect;
      });
      const fixture = TestBed.createComponent(HostComponent);
      document.body.appendChild(fixture.nativeElement);
      fixture.detectChanges();
      button = fixture.nativeElement.querySelector("#btn");
      button.setPointerCapture = vi.fn();
      button.releasePointerCapture = vi.fn();
      // Counts only clicks the directive let through (it cancels the one that ends a drag).
      button.addEventListener("click", (e) => { if(!e.defaultPrevented) { clicks++; } e.preventDefault(); });
   }

   beforeEach(() => {
      clicks = 0;
      localStorage.clear();
      Object.defineProperty(window, "innerWidth", { configurable: true, value: 1000 });
      Object.defineProperty(window, "innerHeight", { configurable: true, value: 700 });
      toolbar = document.createElement("header");
      toolbar.className = "portal-navbar";
      document.body.appendChild(toolbar);
   });

   afterEach(() => {
      toolbar.remove();
      document.body.innerHTML = "";
      vi.restoreAllMocks();
   });

   it("starts below the portal toolbar, at the right edge the stylesheet anchors", () => {
      create();

      expect(button.style.top).toBe("66px");
      expect(button.style.left).toBe("");
   });

   it("falls back to a fixed offset when there is no toolbar", () => {
      toolbar.remove();
      create();

      expect(button.style.top).toBe("72px");
   });

   it("moves with the pointer and remembers where it was dropped", () => {
      create();

      button.dispatchEvent(pointer("pointerdown", 920, 90));
      button.dispatchEvent(pointer("pointermove", 820, 190));
      button.dispatchEvent(pointer("pointerup", 820, 190));

      expect(button.style.left).toBe("800px");
      expect(button.style.top).toBe("166px");
      expect(JSON.parse(localStorage.getItem(WIZ_BUTTON_POSITION_KEY))).toEqual({ left: 800, top: 166 });
      expect(button.classList.contains("dragging")).toBe(false);
   });

   it("does not follow the pointer until it has moved past the drag threshold", () => {
      create();

      button.dispatchEvent(pointer("pointerdown", 920, 90));
      button.dispatchEvent(pointer("pointermove", 922, 91));
      button.dispatchEvent(pointer("pointerup", 922, 91));

      expect(button.style.left).toBe("");
      expect(localStorage.getItem(WIZ_BUTTON_POSITION_KEY)).toBeNull();
   });

   it("keeps a plain press-and-release a normal click", () => {
      create();

      button.dispatchEvent(pointer("pointerdown", 920, 90));
      button.dispatchEvent(pointer("pointerup", 920, 90));
      button.click();

      expect(clicks).toBe(1);
   });

   it("swallows the click that ends a drag so the link does not open", () => {
      create();
      const seen: boolean[] = [];
      button.addEventListener("click", (e) => seen.push(e.defaultPrevented));

      button.dispatchEvent(pointer("pointerdown", 920, 90));
      button.dispatchEvent(pointer("pointermove", 700, 300));
      button.dispatchEvent(pointer("pointerup", 700, 300));
      const dragClick = new MouseEvent("click", { bubbles: true, cancelable: true });
      button.dispatchEvent(dragClick);

      expect(dragClick.defaultPrevented).toBe(true);
      expect(clicks).toBe(0);

      // The next ordinary click works again.
      button.click();
      expect(clicks).toBe(1);
   });

   it("cannot be dragged out of the viewport", () => {
      create();

      button.dispatchEvent(pointer("pointerdown", 920, 90));
      button.dispatchEvent(pointer("pointermove", -5000, -5000));
      button.dispatchEvent(pointer("pointerup", -5000, -5000));
      expect([button.style.left, button.style.top]).toEqual(["8px", "8px"]);

      button.dispatchEvent(pointer("pointerdown", 20, 20));
      button.dispatchEvent(pointer("pointermove", 9000, 9000));
      button.dispatchEvent(pointer("pointerup", 9000, 9000));
      expect([button.style.left, button.style.top]).toEqual(["944px", "644px"]);
   });

   it("restores a remembered position and clamps it into a smaller window", () => {
      localStorage.setItem(WIZ_BUTTON_POSITION_KEY, JSON.stringify({ left: 5000, top: 300 }));
      create();

      expect(button.style.left).toBe("944px");
      expect(button.style.top).toBe("300px");

      Object.defineProperty(window, "innerWidth", { configurable: true, value: 600 });
      window.dispatchEvent(new Event("resize"));

      expect(button.style.left).toBe("544px");
   });

   it("ignores a damaged stored position", () => {
      localStorage.setItem(WIZ_BUTTON_POSITION_KEY, "{not json");
      create();

      expect(button.style.top).toBe("66px");
   });

   it("ignores a second pointer while one is dragging", () => {
      create();

      button.dispatchEvent(pointer("pointerdown", 920, 90, 1));
      button.dispatchEvent(pointer("pointerdown", 100, 100, 2));
      button.dispatchEvent(pointer("pointermove", 100, 400, 2));
      button.dispatchEvent(pointer("pointerup", 100, 400, 2));

      expect(button.style.left).toBe("");
   });

   it("cancels the browser's own link drag", () => {
      create();
      const event = new Event("dragstart", { bubbles: true, cancelable: true });

      button.dispatchEvent(event);

      expect(event.defaultPrevented).toBe(true);
   });
});
