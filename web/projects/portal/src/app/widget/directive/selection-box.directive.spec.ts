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
import "@angular/compiler";
import { SelectionBoxDirective } from "./selection-box.directive";

describe("SelectionBoxDirective", () => {
   function classesOf(selectionBoxClass?: string): string[] {
      const added: string[] = [];
      const renderer: any = {
         createElement: () => ({}),
         addClass: (_el: any, name: string) => added.push(name),
         setStyle: () => {},
         appendChild: () => {},
      };
      const domService: any = { requestWrite: (fn: () => void) => fn() };
      const directive = new SelectionBoxDirective(null, { nativeElement: {} }, renderer, domService);

      if(selectionBoxClass !== undefined) {
         directive.selectionBoxClass = selectionBoxClass;
      }

      directive.ngOnInit();
      return added;
   }

   it("defaultClassUnchanged", () => {
      expect(classesOf()).toEqual(["selection-box"]);
   });

   // the composer's authoring panes pass a second class for the two-tone marquee (PR8)
   // the dashboard pane bans "layout-pane" so a drag inside the layout pane draws only the layout pane's
   // own marquee (PR8): behaviour, with a real DOM and the directive's own window listeners
   describe("banned selector", () => {
      function setup() {
         const host = document.createElement("div");
         const pane = document.createElement("layout-pane");
         const inPane = document.createElement("span");
         const free = document.createElement("span");
         pane.appendChild(inPane);
         host.append(pane, free);
         document.body.appendChild(host);
         const listeners: { [event: string]: (e: any) => void } = {};
         const renderer: any = {
            createElement: (tag: string) => document.createElement(tag),
            addClass: (el: HTMLElement, name: string) => el.classList.add(name),
            setStyle: (el: HTMLElement, prop: string, value: string) => (el.style as any)[prop] = value,
            appendChild: (parent: HTMLElement, child: HTMLElement) => parent.appendChild(child),
            removeChild: () => {},
            listen: (_target: string, event: string, fn: (e: any) => void) => {
               listeners[event] = fn;
               return () => delete listeners[event];
            },
         };
         const zone: any = { runOutsideAngular: (fn: () => void) => fn() };
         const domService: any = { requestWrite: (fn: () => void) => fn(), requestRead: (fn: () => void) => fn(), cancelAnimationFrame: () => {} };
         const directive = new SelectionBoxDirective(zone, { nativeElement: host }, renderer, domService);
         directive.selectionBoxBannedSelector = "[data-is-object-editor].active, .line.handle, layout-pane";
         directive.ngOnInit();
         const boxes: any[] = [];
         directive.onSelectionBox.subscribe((e: any) => boxes.push(e));
         const box = host.querySelector(".selection-box") as HTMLElement;
         return { directive, inPane, free, listeners, boxes, box };
      }

      const at = (target: HTMLElement, x: number, y: number): any =>
         ({ target, button: 0, pageX: x, clientX: x, clientY: y, stopPropagation: () => {}, cancelable: false });

      afterEach(() => document.body.innerHTML = "");

      it("aMousedownInsideABannedElementDrawsNoMarquee", () => {
         const t = setup();
         t.directive.mouseDown(at(t.inPane, 10, 10));
         expect(t.listeners["mousemove"]).toBeUndefined();
         expect(t.box.style.display).toBe("none");
         expect(t.boxes).toEqual([]);
      });

      it("aMousedownElsewhereOnTheHostStillDrawsAndEmits", () => {
         const t = setup();
         t.directive.mouseDown(at(t.free, 10, 10));
         t.listeners["mousemove"](at(t.free, 60, 60));
         expect(t.box.style.display).toBe("block");
         t.listeners["mouseup"](at(t.free, 60, 60));
         expect(t.boxes.length).toBe(1);
      });
   });

   it("addsEveryClassInSelectionBoxClass", () => {
      expect(classesOf("selection-box composer-selection-box")).toEqual(["selection-box", "composer-selection-box"]);
      expect(classesOf("  selection-box   composer-selection-box ")).toEqual(["selection-box", "composer-selection-box"]);
   });
});
