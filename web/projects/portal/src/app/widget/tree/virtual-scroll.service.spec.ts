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
 * VirtualScrollService — Zone CD regression tests (Bug #75489)
 *
 * Verifies that registerScrollContainer() registers the scroll listener outside
 * the Angular Zone so that scroll events do not trigger zone-based Change Detection.
 *
 * KEY contract:
 *   - runOutsideAngular() is called exactly once (for addEventListener)
 *   - ngZone.run() is never called from within the scroll handler
 *   - scrollTop and dispatcher still emit on each scroll event (functional correctness)
 */

import { NgZone } from "@angular/core";
import { describe, it, expect, beforeEach, vi } from "vitest";
import { VirtualScrollService } from "./virtual-scroll.service";

function makeMockZone(): NgZone {
   return {
      runOutsideAngular: vi.fn((fn: () => any) => fn()),
      run: vi.fn((fn: () => any) => fn()),
   } as unknown as NgZone;
}

function runCount(zone: NgZone): number {
   return (zone.run as ReturnType<typeof vi.fn>).mock.calls.length;
}

function outsideCount(zone: NgZone): number {
   return (zone.runOutsideAngular as ReturnType<typeof vi.fn>).mock.calls.length;
}

describe("VirtualScrollService.registerScrollContainer()", () => {
   let service: VirtualScrollService;
   let el: HTMLElement;
   let zone: NgZone;

   beforeEach(() => {
      zone = makeMockZone();
      service = new VirtualScrollService(zone);
      el = document.createElement("div");
   });

   it("registers the scroll listener outside the Angular Zone", () => {
      service.registerScrollContainer(el);
      expect(outsideCount(zone)).toBe(1);
   });

   it("single scroll event must not call ngZone.run() — no zone re-entry on scroll", () => {
      service.registerScrollContainer(el);
      el.dispatchEvent(new Event("scroll"));
      expect(runCount(zone)).toBe(0);
   });

   it("10 scroll events must not accumulate ngZone.run() calls", () => {
      service.registerScrollContainer(el);
      for(let i = 0; i < 10; i++) {
         el.dispatchEvent(new Event("scroll"));
      }
      expect(runCount(zone)).toBe(0);
   });

   it("dispatcher still emits on each scroll event (functional correctness)", () => {
      let emitCount = 0;
      service.registerScrollContainer(el).subscribe(() => emitCount++);
      el.dispatchEvent(new Event("scroll"));
      el.dispatchEvent(new Event("scroll"));
      // 1 initial BehaviorSubject emission + 2 scroll events
      expect(emitCount).toBe(3);
   });

   it("scrollTop emits on each scroll event (functional correctness)", () => {
      const scrollTops: number[] = [];
      service.scrollTop.subscribe(v => scrollTops.push(v));
      service.registerScrollContainer(el);
      el.dispatchEvent(new Event("scroll"));
      // Note: in JSDOM, scrollTop is always 0 — this verifies emission count, not actual scroll position.
      // BehaviorSubject emits 0 on subscribe, then emits e.target.scrollTop on scroll
      expect(scrollTops.length).toBe(2);
   });

   it("re-registering removes the previous scroll listener", () => {
      service.registerScrollContainer(el);
      const el2 = document.createElement("div");
      const obs = service.registerScrollContainer(el2);
      let emitCount = 0;
      obs.subscribe(() => emitCount++); // receives initial BehaviorSubject emit
      emitCount = 0; // reset after initial emit
      el.dispatchEvent(new Event("scroll")); // old element — listener should be gone
      expect(emitCount).toBe(0);
   });
});
