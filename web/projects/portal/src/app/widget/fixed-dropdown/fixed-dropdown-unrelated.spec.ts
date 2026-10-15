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
 * Bug #78604, keeping an outer dropdown open on a click in a dropdown stacked above it must
 * not keep the current dropdown open on a click in an unrelated dropdown below it.
 */

import { Component } from "@angular/core";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { FixedDropdownDirective } from "./fixed-dropdown.directive";

@Component({
   selector: "unrelated-dropdown-host",
   standalone: true,
   imports: [FixedDropdownDirective],
   template: `
     <div class="pinned-toggle" [fixedDropdown]="pinnedMenu" [autoClose]="false"
          [closeOnOutsideClick]="false"></div>
     <div class="menu-toggle" [fixedDropdown]="menu" [autoClose]="false"></div>
     <ng-template #pinnedMenu>
       <div class="pinned-pane"><span class="pinned-label">pinned</span></div>
     </ng-template>
     <ng-template #menu>
       <div class="menu-pane">menu</div>
     </ng-template>
   `
})
class UnrelatedDropdownHost {
}

const tick = (ms: number = 0) => new Promise<void>((resolve) => setTimeout(resolve, ms));

function fire(target: EventTarget, type: string): void {
   target.dispatchEvent(new MouseEvent(type, { bubbles: true, cancelable: true, button: 0 }));
}

describe("FixedDropdownComponent click in an unrelated dropdown (Bug #78604)", () => {
   let fixture: ComponentFixture<UnrelatedDropdownHost>;

   async function open(selector: string): Promise<void> {
      fixture.nativeElement.querySelector(selector).click();
      fixture.detectChanges();
      await tick(10);
      fixture.detectChanges();
   }

   beforeEach(async () => {
      TestBed.configureTestingModule({ imports: [UnrelatedDropdownHost] });
      fixture = TestBed.createComponent(UnrelatedDropdownHost);
      document.body.appendChild(fixture.nativeElement);
      fixture.detectChanges();
      await open(".pinned-toggle");
      await open(".menu-toggle");
      expect(document.body.querySelector(".pinned-pane")).not.toBeNull();
      expect(document.body.querySelector(".menu-pane")).not.toBeNull();
   });

   afterEach(() => {
      fixture.destroy();
      document.body.querySelectorAll("fixed-dropdown").forEach((e) => e.remove());
   });

   for(const timerFirst of [false, true]) {
      const order = timerFirst ? "timer before click" : "click before timer";

      it(`closes the current dropdown on a click in a dropdown below it (${order})`, async () => {
         const target = document.body.querySelector(".pinned-label");

         fire(target, "mousedown");

         if(timerFirst) {
            await tick(10);
         }

         fire(target, "mouseup");
         fire(target, "click");
         await tick(10);

         expect(document.body.querySelector(".menu-pane")).toBeNull();
         expect(document.body.querySelector(".pinned-pane")).not.toBeNull();
      });
   }
});
