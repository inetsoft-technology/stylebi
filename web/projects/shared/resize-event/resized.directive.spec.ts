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
import { ResizedDirective } from "./resized.directive";
import { ResizedEvent } from "./resized.event";

// Capture the ResizeObserver callback so we can invoke it manually in tests.
let capturedCallback: ResizeObserverCallback | null = null;
let mockObserve: jest.Mock;
let mockDisconnect: jest.Mock;

class MockResizeObserver {
   constructor(callback: ResizeObserverCallback) {
      capturedCallback = callback;
   }
   observe = mockObserve;
   disconnect = mockDisconnect;
   unobserve = jest.fn();
}

@Component({
   template: `<div resized (resized)="onResized($event)">content</div>`
})
class TestHostComponent {
   lastEvent: ResizedEvent | null = null;
   onResized(event: ResizedEvent) { this.lastEvent = event; }
}

describe("ResizedDirective", () => {
   let fixture: ComponentFixture<TestHostComponent>;
   let host: TestHostComponent;

   beforeEach(() => {
      mockObserve = jest.fn();
      mockDisconnect = jest.fn();
      capturedCallback = null;

      // Replace browser ResizeObserver with our mock
      (global as any).ResizeObserver = MockResizeObserver;

      TestBed.configureTestingModule({
         declarations: [ResizedDirective, TestHostComponent]
      }).compileComponents();

      fixture = TestBed.createComponent(TestHostComponent);
      host = fixture.componentInstance;
      fixture.detectChanges();
   });

   afterEach(() => {
      delete (global as any).ResizeObserver;
   });

   it("should call observe on the host element during ngOnInit", () => {
      const hostEl = fixture.nativeElement.querySelector("div");
      expect(mockObserve).toHaveBeenCalledWith(hostEl);
   });

   it("should call disconnect on the observer during ngOnDestroy", () => {
      fixture.destroy();
      expect(mockDisconnect).toHaveBeenCalled();
   });

   it("should emit ResizedEvent when the ResizeObserver fires", () => {
      expect(capturedCallback).not.toBeNull();

      const newRect = { width: 200, height: 100 } as DOMRectReadOnly;
      const entry = { contentRect: newRect } as ResizeObserverEntry;

      // Simulate a resize observation
      capturedCallback([entry], null as any);
      fixture.detectChanges();

      expect(host.lastEvent).not.toBeNull();
      expect(host.lastEvent.newRect).toBe(newRect);
   });

   it("isFirst should be true on the first resize event", () => {
      const newRect = { width: 200, height: 100 } as DOMRectReadOnly;
      const entry = { contentRect: newRect } as ResizeObserverEntry;

      capturedCallback([entry], null as any);

      expect(host.lastEvent.isFirst).toBe(true);
   });

   it("isFirst should be false on subsequent resize events", () => {
      const rect1 = { width: 200, height: 100 } as DOMRectReadOnly;
      const rect2 = { width: 300, height: 150 } as DOMRectReadOnly;

      capturedCallback([{ contentRect: rect1 } as ResizeObserverEntry], null as any);
      capturedCallback([{ contentRect: rect2 } as ResizeObserverEntry], null as any);

      expect(host.lastEvent.isFirst).toBe(false);
      expect(host.lastEvent.oldRect).toBe(rect1);
      expect(host.lastEvent.newRect).toBe(rect2);
   });
});
