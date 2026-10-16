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
 * Bug #78604, the texture and line combo boxes open an autoClose dropdown from inside the
 * Edit Shape popup (shape-field-mc: fixed dropdown with autoClose=false). Picking a texture
 * or line must not close the popup, also when Chromium dispatches the click before the
 * deferred outside-mousedown check of the popup.
 */

import { Component } from "@angular/core";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { FixedDropdownDirective } from "../../../../widget/fixed-dropdown/fixed-dropdown.directive";
import { CombinedShapePane } from "./combined-shape-pane.component";
import { LineComboBox } from "./line-combo-box.component";
import { TextureComboBox } from "./texture-combo-box.component";

@Component({
   selector: "edit-shape-host",
   standalone: true,
   imports: [FixedDropdownDirective, TextureComboBox, LineComboBox, CombinedShapePane],
   template: `
     <div class="outside">outside</div>
     <div class="popup-toggle" [fixedDropdown]="popup" [autoClose]="false" [zIndex]="10000">
     </div>
     <ng-template #popup>
       <div class="popup">
         <texture-combo-box [texture]="texture" (textureChanged)="texture = $event">
         </texture-combo-box>
         <line-combo-box [line]="line" (lineChanged)="line = $event"></line-combo-box>
         <combined-shape-pane class="combined-texture" [frameInfos]="textureFrames"
                              [isTextureType]="true"></combined-shape-pane>
         <combined-shape-pane class="combined-line" [frameInfos]="lineFrames"
                              [isLineType]="true"></combined-shape-pane>
       </div>
     </ng-template>
   `
})
class EditShapeHost {
   texture: number = null;
   line: number = null;
   textureFrames = [{ name: "Total", frame: { texture: null } }];
   lineFrames = [{ name: "Total", frame: { line: null } }];
}

const tick = (ms: number = 0) => new Promise<void>((resolve) => setTimeout(resolve, ms));

function fire(target: EventTarget, type: string): void {
   target.dispatchEvent(new MouseEvent(type, { bubbles: true, cancelable: true, button: 0 }));
}

function last(selector: string): HTMLElement {
   const elements = document.body.querySelectorAll<HTMLElement>(selector);
   return elements[elements.length - 1];
}

function openDropdowns(): number {
   return document.body.querySelectorAll("fixed-dropdown").length;
}

describe("Edit Shape popup with nested texture/line combo boxes (Bug #78604)", () => {
   let fixture: ComponentFixture<EditShapeHost>;

   async function open(toggle: HTMLElement): Promise<void> {
      toggle.click();
      fixture.detectChanges();
      await tick(10);
      fixture.detectChanges();
   }

   beforeEach(async () => {
      TestBed.configureTestingModule({ imports: [EditShapeHost] });
      fixture = TestBed.createComponent(EditShapeHost);
      document.body.appendChild(fixture.nativeElement);
      fixture.detectChanges();
      await open(fixture.nativeElement.querySelector(".popup-toggle"));
      expect(openDropdowns()).toBe(1);
   });

   afterEach(() => {
      fixture.destroy();
      document.body.querySelectorAll("fixed-dropdown").forEach((e) => e.remove());
   });

   const cases: { name: string, toggle: string, option: string, value: (h: EditShapeHost) => any }[] = [
      {
         name: "texture-combo-box",
         toggle: ".popup > texture-combo-box .dropdown-toggle",
         option: ".texture-pane > div",
         value: (h) => h.texture
      },
      {
         name: "line-combo-box",
         toggle: ".popup > line-combo-box .dropdown-toggle",
         option: ".line-pane",
         value: (h) => h.line
      },
      {
         name: "combined-shape-pane static-texture-editor",
         toggle: ".combined-texture static-texture-editor .dropdown-toggle",
         option: ".texture-pane > div",
         value: (h) => h.textureFrames[0].frame.texture
      },
      {
         name: "combined-shape-pane static-line-editor",
         toggle: ".combined-line static-line-editor .dropdown-toggle",
         option: ".line-pane",
         value: (h) => h.lineFrames[0].frame.line
      }
   ];

   for(const c of cases) {
      for(const timerFirst of [false, true]) {
         const order = timerFirst ? "timer before click" : "click before timer";

         it(`${c.name}: picking a value keeps the popup open (${order})`, async () => {
            await open(last(c.toggle));
            expect(openDropdowns()).toBe(2);
            const option = last(c.option);

            fire(option, "mousedown");

            if(timerFirst) {
               await tick(10);
            }

            fire(option, "mouseup");
            fire(option, "click");
            await tick(10);

            expect(c.value(fixture.componentInstance)).not.toBeNull();
            expect(openDropdowns()).toBe(1);
            expect(document.body.querySelector(".popup")).not.toBeNull();
         });
      }
   }

   it("an outside click still closes the popup", async () => {
      const outside = fixture.nativeElement.querySelector(".outside");

      fire(outside, "mousedown");
      fire(outside, "mouseup");
      fire(outside, "click");
      await tick(10);

      expect(openDropdowns()).toBe(0);
   });
});
