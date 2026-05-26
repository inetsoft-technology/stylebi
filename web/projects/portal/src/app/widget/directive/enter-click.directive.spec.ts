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
import { Component } from "@angular/core";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { EnterClickDirective } from "./enter-click.directive";

@Component({
   template: `<button enterClick (click)="onClick($event)">Click Me</button>`
})
class TestHostComponent {
   lastEvent: MouseEvent = null;
   onClick(event: MouseEvent) { this.lastEvent = event; }
}

@Component({
   template: `<button [hasKeys]="true" enterClick (click)="onClick($event)">Click Me</button>`
})
class TestHostWithKeysComponent {
   lastEvent: MouseEvent = null;
   onClick(event: MouseEvent) { this.lastEvent = event; }
}

describe("EnterClickDirective", () => {
   it("should trigger click on Enter keydown (keyCode 13)", () => {
      TestBed.configureTestingModule({
         declarations: [EnterClickDirective, TestHostComponent]
      });
      const fixture: ComponentFixture<TestHostComponent> = TestBed.createComponent(TestHostComponent);
      fixture.detectChanges();
      const button: HTMLButtonElement = fixture.nativeElement.querySelector("button");
      const clickSpy = jest.spyOn(button, "click");
      const event = new KeyboardEvent("keydown", { keyCode: 13, bubbles: true } as any);
      button.dispatchEvent(event);
      expect(clickSpy).toHaveBeenCalled();
   });

   it("should not trigger click on non-Enter keydown", () => {
      TestBed.configureTestingModule({
         declarations: [EnterClickDirective, TestHostComponent]
      });
      const fixture: ComponentFixture<TestHostComponent> = TestBed.createComponent(TestHostComponent);
      fixture.detectChanges();
      const button: HTMLButtonElement = fixture.nativeElement.querySelector("button");
      const clickSpy = jest.spyOn(button, "click");
      const event = new KeyboardEvent("keydown", { keyCode: 32, bubbles: true } as any);
      button.dispatchEvent(event);
      expect(clickSpy).not.toHaveBeenCalled();
   });

   it("should dispatch MouseEvent with modifier keys when hasKeys is true", () => {
      TestBed.configureTestingModule({
         declarations: [EnterClickDirective, TestHostWithKeysComponent]
      });
      const fixture: ComponentFixture<TestHostWithKeysComponent> = TestBed.createComponent(TestHostWithKeysComponent);
      fixture.detectChanges();
      const button: HTMLButtonElement = fixture.nativeElement.querySelector("button");
      const dispatchSpy = jest.spyOn(button, "dispatchEvent");
      const event = new KeyboardEvent("keydown", { keyCode: 13, shiftKey: true, bubbles: true } as any);
      button.dispatchEvent(event);
      const dispatched = dispatchSpy.mock.calls.find(
         ([e]) => e instanceof MouseEvent && (e as MouseEvent).type === "click"
      );
      expect(dispatched).toBeDefined();
      expect((dispatched[0] as MouseEvent).shiftKey).toBe(true);
   });
});
