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

/**
 * Bug #78604, nested fixed dropdowns: an option click in an autoClose dropdown opened from
 * inside another dropdown must not close the outer one, whichever runs first: the click or
 * the timer that FixedDropdownComponent defers its outside-mousedown check to.
 *
 * Real FixedDropdownDirective/Service/Component/DropdownStackService, real document events.
 * Chromium can dispatch a fast click before that timer; jsdom never does by itself, so each
 * test imposes the event order explicitly.
 */

import { Component } from "@angular/core";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { FixedDropdownDirective } from "./fixed-dropdown.directive";

@Component({
   selector: "nested-dropdown-host",
   standalone: true,
   imports: [FixedDropdownDirective],
   template: `
     <div class="outside">outside</div>
     <div class="outer-toggle" [fixedDropdown]="outerMenu" [autoClose]="false"></div>
     <ng-template #outerMenu>
       <div class="outer-pane">
         <span class="outer-label">label</span>
         <div class="middle-toggle" [fixedDropdown]="middleMenu" [autoClose]="false"></div>
         <div class="inner-toggle" [fixedDropdown]="innerMenu" [autoClose]="true"></div>
       </div>
     </ng-template>
     <ng-template #middleMenu>
       <div class="middle-pane">
         <div class="inner-toggle" [fixedDropdown]="innerMenu" [autoClose]="true"></div>
       </div>
     </ng-template>
     <ng-template #innerMenu>
       <div class="inner-pane">
         @for (v of values; track v) {
           <div class="option" (click)="selected = v">{{v}}</div>
         }
       </div>
     </ng-template>
   `
})
class NestedDropdownHost {
   values = [1, 2, 3];
   selected: number = null;
}

const tick = (ms: number = 0) => new Promise<void>((resolve) => setTimeout(resolve, ms));

function fire(target: EventTarget, type: string): void {
   target.dispatchEvent(new MouseEvent(type, { bubbles: true, cancelable: true, button: 0 }));
}

/**
 * Click with the timer deferred by the mousedown running first (Firefox, slow press) or
 * after the click (Chromium fast click).
 */
async function clickOn(target: EventTarget, timerFirst: boolean): Promise<void> {
   fire(target, "mousedown");

   if(timerFirst) {
      await tick(10);
   }

   fire(target, "mouseup");
   fire(target, "click");
   await tick(10);
}

function openDropdowns(): number {
   return document.body.querySelectorAll("fixed-dropdown").length;
}

function last(selector: string): HTMLElement {
   const elements = document.body.querySelectorAll<HTMLElement>(selector);
   return elements[elements.length - 1];
}

describe("FixedDropdownComponent nested dropdowns (Bug #78604)", () => {
   let fixture: ComponentFixture<NestedDropdownHost>;

   // open a dropdown from its toggle and wait until its document listeners are registered
   async function open(selector: string): Promise<void> {
      last(selector).click();
      fixture.detectChanges();
      await tick(10);
      fixture.detectChanges();
   }

   beforeEach(async () => {
      TestBed.configureTestingModule({ imports: [NestedDropdownHost] });
      fixture = TestBed.createComponent(NestedDropdownHost);
      document.body.appendChild(fixture.nativeElement);
      fixture.detectChanges();
      await open(".outer-toggle");
      expect(openDropdowns()).toBe(1);
   });

   afterEach(() => {
      fixture.destroy();
      document.body.querySelectorAll("fixed-dropdown").forEach((e) => e.remove());
   });

   for(const timerFirst of [false, true]) {
      const order = timerFirst ? "timer before click" : "click before timer";

      it(`keeps the outer dropdown open on an inner option click (${order})`, async () => {
         await open(".outer-pane .inner-toggle");
         expect(openDropdowns()).toBe(2);

         await clickOn(last(".inner-pane .option"), timerFirst);

         expect(fixture.componentInstance.selected).toBe(3);
         expect(openDropdowns()).toBe(1);
         expect(document.body.querySelector(".outer-pane")).not.toBeNull();
      });

      it(`keeps the outer two of three stacked dropdowns open (${order})`, async () => {
         await open(".outer-pane .middle-toggle");
         await open(".middle-pane .inner-toggle");
         expect(openDropdowns()).toBe(3);

         await clickOn(last(".inner-pane .option"), timerFirst);

         expect(fixture.componentInstance.selected).toBe(3);
         expect(openDropdowns()).toBe(2);
         expect(document.body.querySelector(".middle-pane")).not.toBeNull();
      });

      it(`closes only the inner dropdown on a click in the outer one (${order})`, async () => {
         await open(".outer-pane .inner-toggle");

         await clickOn(document.body.querySelector(".outer-label"), timerFirst);

         expect(openDropdowns()).toBe(1);
         expect(document.body.querySelector(".inner-pane")).toBeNull();
      });

      it(`closes every dropdown on an outside click (${order})`, async () => {
         await open(".outer-pane .inner-toggle");

         await clickOn(fixture.nativeElement.querySelector(".outside"), timerFirst);

         expect(openDropdowns()).toBe(0);
      });

      it(`closes a single dropdown on an outside click (${order})`, async () => {
         await clickOn(fixture.nativeElement.querySelector(".outside"), timerFirst);

         expect(openDropdowns()).toBe(0);
      });
   }

   it("closes the dropdowns on Escape", async () => {
      await open(".outer-pane .inner-toggle");

      document.dispatchEvent(new KeyboardEvent("keyup", { key: "Escape", bubbles: true }));
      await tick(10);

      expect(openDropdowns()).toBe(0);
   });
});
