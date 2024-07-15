/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { Component, DebugElement } from "@angular/core";
import { TestBed } from "@angular/core/testing";
import { By } from "@angular/platform-browser";
import { MouseEventDirective } from "./mouse-event.directive";

@Component({
   template: `
     <div mouseEvent>Has Directive</div>
     <div>No Directive</div>`
})
class TestApp {
}

describe("Mouse Event Directive Tests", () => {
   let testElements: DebugElement[];
   let regularDiv: DebugElement;

   beforeEach(() => {
      const fixture = TestBed.configureTestingModule({
         declarations: [
            TestApp,
            MouseEventDirective
         ]
      }).createComponent(TestApp);

      fixture.detectChanges();
      testElements = fixture.debugElement.queryAll(By.directive(MouseEventDirective));
      regularDiv = fixture.debugElement.query(By.css("div:not([mouseEvent])"));
   });

   it("should have one 'MouseEventDirective' element", () => {
      expect(testElements.length).toBe(1);
   });

   it("should be able to inject 'MouseEventDirective' in first div", () => {
      const mouseEventDirective: MouseEventDirective = testElements[0].injector.get(MouseEventDirective);
      expect(mouseEventDirective).toBeTruthy();
   });

   it("should emit left mouse events from the first div", () => {
      const firstDiv = testElements[0];
      const mouseEventDirective: MouseEventDirective = firstDiv.injector.get(MouseEventDirective);
      const leftMouseDownSpy = jest.spyOn(mouseEventDirective.leftMouseDown, "emit");
      const leftMouseUpSpy = jest.spyOn(mouseEventDirective.leftMouseUp, "emit");
      const mouseEvent: MouseEventInit = {
         button: 0
      };

      triggerEvents(firstDiv, mouseEvent);

      expect(leftMouseDownSpy).toHaveBeenCalled();
      expect(leftMouseDownSpy.mock.calls[leftMouseDownSpy.mock.calls.length - 1][0]).toEqual(mouseEvent);
      expect(leftMouseUpSpy).toHaveBeenCalled();
      expect(leftMouseUpSpy.mock.calls[leftMouseDownSpy.mock.calls.length - 1][0]).toEqual(mouseEvent);
   });

   it("should emit right mouse events from the first div", () => {
      const firstDiv = testElements[0];
      const mouseEventDirective: MouseEventDirective = firstDiv.injector.get(MouseEventDirective);
      const rightMouseDownSpy = jest.spyOn(mouseEventDirective.rightMouseDown, "emit");
      const rightMouseUpSpy = jest.spyOn(mouseEventDirective.rightMouseUp, "emit");
      const mouseEvent = {
         button: 2
      };

      triggerEvents(firstDiv, mouseEvent);
      expect(rightMouseDownSpy).toHaveBeenCalled();
      expect(rightMouseUpSpy).toHaveBeenCalled();
   });

   it("should not be able to inject 'MouseEventDirective' in second div", () => {
      const mouseEventDirective = regularDiv.injector.get(MouseEventDirective, null);
      expect(mouseEventDirective).toBe(null);
   });

   function triggerEvents(element: DebugElement, mouseEvent: MouseEventInit): void {
      element.triggerEventHandler("mousedown", mouseEvent);
      element.triggerEventHandler("mouseup", mouseEvent);
      element.triggerEventHandler("click", mouseEvent);
   }
});
