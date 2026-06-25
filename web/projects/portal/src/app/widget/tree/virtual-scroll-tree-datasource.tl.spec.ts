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
 * VirtualScrollTreeDatasource — full unit suite.
 *
 * 1. Zone isolation — scroll listener is wrapped in runOutsideAngular (#75489)
 * 2. Dispatcher Observable — emit on scroll / refresh
 * 3. scrollTop tracking
 * 4. Cleanup and listener lifecycle
 * 5. restoreScrollTop
 * 6. inViewport / nodeVisible
 *
 * No HTTP calls; no MSW needed.
 * VirtualScrollTreeDatasource is a plain class; real NgZone instances are used
 * where CD-cycle counting matters; a lightweight mock zone is used to assert
 * that runOutsideAngular() is called.
 */

import { afterEach, describe, expect, it, vi } from "vitest";
import { NgZone } from "@angular/core";
import { VirtualScrollTreeDatasource } from "./virtual-scroll-tree-datasource";
import { TreeNodeModel } from "./tree-node-model";

// ── Utilities ─────────────────────────────────────────────────────────────────

/** Real NgZone paired with an onMicrotaskEmpty counter for CD-cycle tests. */
function makeZoneAndCounter() {
   const zone = new NgZone({ enableLongStackTrace: false });
   let _count = 0;
   zone.onMicrotaskEmpty.subscribe(() => _count++);
   return {
      zone,
      getCount: () => _count,
      reset:    () => { _count = 0; },
   };
}

/**
 * Minimal NgZone mock that records how many times runOutsideAngular() is called.
 * Used only to assert the API is invoked — not for CD-cycle measurement.
 */
function makeMockZone() {
   let _outsideCount = 0;
   const mock = {
      runOutsideAngular<T>(fn: () => T): T { _outsideCount++; return fn(); },
      run<T>(fn: () => T): T { return fn(); },
      outsideCount: () => _outsideCount,
   };
   return mock as unknown as NgZone & { outsideCount(): number };
}

function makeNode(label: string, parent?: TreeNodeModel): TreeNodeModel {
   return { label, parent } as TreeNodeModel;
}

afterEach(() => vi.restoreAllMocks());

// ── 1a. Baseline: measurement mechanism ───────────────────────────────────────
//
//   These two tests verify the CD-counting technique itself before using it.

describe("VirtualScrollTreeDatasource — Zone CD baseline: measurement mechanism", () => {

   it("scroll listener registered outside zone.run() does not increment onMicrotaskEmpty", () => {
      const { getCount, reset } = makeZoneAndCounter();
      const el = document.createElement("div");

      el.addEventListener("scroll", () => {});
      reset();
      el.dispatchEvent(new Event("scroll", { bubbles: true }));

      expect(getCount()).toBe(0);
   });

   it("listener registered inside zone.run() increments onMicrotaskEmpty (proves measurement is valid)", () => {
      const { zone, getCount, reset } = makeZoneAndCounter();
      const el = document.createElement("div");

      zone.run(() => { el.addEventListener("scroll", () => {}); });
      reset();
      el.dispatchEvent(new Event("scroll"));

      expect(getCount()).toBeGreaterThan(0);
   });
});

// ── 1b. Zone isolation: runOutsideAngular (#75489) ────────────────────────────
//
//   Fix: virtual-scroll-tree-datasource.ts:106 — registerScrollContainer()
//   wraps element.addEventListener in ngZone.runOutsideAngular() so that
//   scroll events do not trigger global Change Detection cycles.
//
//   Affected context: Composer Toolbox/Binding Tree (useVirtualScroll=true).
//   At 20-30 scrolls/sec, in-zone handlers consumed 3-5 frame budgets/sec.

describe("VirtualScrollTreeDatasource — Zone isolation: registerScrollContainer uses runOutsideAngular (#75489)", () => {

   it("registerScrollContainer() calls runOutsideAngular() exactly once", () => {
      const zone = makeMockZone();
      const ds   = new VirtualScrollTreeDatasource();
      const el   = document.createElement("div");

      ds.registerScrollContainer(el, zone as unknown as NgZone);

      expect(zone.outsideCount()).toBe(1);
   });

   it("single scroll event does not trigger Zone CD cycle", () => {
      const { zone, getCount, reset } = makeZoneAndCounter();
      const ds = new VirtualScrollTreeDatasource();
      const el = document.createElement("div");

      // Simulate tree.component.ts:960 — registerScrollContainer called inside zone
      zone.run(() => { ds.registerScrollContainer(el, zone); });
      reset();

      el.dispatchEvent(new Event("scroll", { bubbles: true }));

      expect(getCount()).toBe(0);
   });

   it("10 rapid scroll events do not accumulate Zone CD cycles", () => {
      const { zone, getCount, reset } = makeZoneAndCounter();
      const ds = new VirtualScrollTreeDatasource();
      const el = document.createElement("div");

      zone.run(() => { ds.registerScrollContainer(el, zone); });
      reset();

      for(let i = 0; i < 10; i++) {
         el.dispatchEvent(new Event("scroll"));
      }

      expect(getCount()).toBe(0);
   });
});

// ── 2. Dispatcher: emit on scroll and refresh ─────────────────────────────────

describe("VirtualScrollTreeDatasource — dispatcher: emit on scroll and refresh", () => {

   it("scroll causes dispatcher to emit current value (data flow regression guard)", () => {
      const zone = new NgZone({ enableLongStackTrace: false });
      const ds   = new VirtualScrollTreeDatasource();
      const el   = document.createElement("div");
      const emits: unknown[] = [];

      ds.registerScrollContainer(el, zone).subscribe(v => emits.push(v));
      const before = emits.length; // BehaviorSubject emits [] immediately on subscribe

      el.dispatchEvent(new Event("scroll"));

      expect(emits.length).toBeGreaterThan(before);
   });

   it("refresh() updates dispatcher with the provided items", () => {
      const zone  = new NgZone({ enableLongStackTrace: false });
      const ds    = new VirtualScrollTreeDatasource();
      const el    = document.createElement("div");
      const items = [makeNode("a"), makeNode("b")];
      const received: unknown[][] = [];

      ds.registerScrollContainer(el, zone).subscribe(v => received.push(v));
      ds.refresh(items);

      expect(received[received.length - 1]).toBe(items);
   });

   it("multiple datasource instances on the same element each emit independently", () => {
      const zone = new NgZone({ enableLongStackTrace: false });
      const ds1  = new VirtualScrollTreeDatasource();
      const ds2  = new VirtualScrollTreeDatasource();
      const el   = document.createElement("div");
      const emits1: unknown[] = [];
      const emits2: unknown[] = [];

      ds1.registerScrollContainer(el, zone).subscribe(v => emits1.push(v));
      ds2.registerScrollContainer(el, zone).subscribe(v => emits2.push(v));

      const b1 = emits1.length;
      const b2 = emits2.length;

      el.dispatchEvent(new Event("scroll"));

      expect(emits1.length).toBeGreaterThan(b1);
      expect(emits2.length).toBeGreaterThan(b2);
   });
});

// ── 3. scrollTop tracking ─────────────────────────────────────────────────────

describe("VirtualScrollTreeDatasource — scrollTop tracking", () => {

   it("initial scrollTop value is 0", () => {
      expect(new VirtualScrollTreeDatasource().scrollTop.value).toBe(0);
   });

   it("scrollTop updates when the scroll event target is an HTMLElement", () => {
      const zone      = new NgZone({ enableLongStackTrace: false });
      const ds        = new VirtualScrollTreeDatasource();
      const container = document.createElement("div");

      ds.registerScrollContainer(container, zone);

      Object.defineProperty(container, "scrollTop", { value: 42, writable: true, configurable: true });
      container.dispatchEvent(new Event("scroll"));

      expect(ds.scrollTop.value).toBe(42);
   });
});

// ── 4. Cleanup and listener lifecycle ────────────────────────────────────────

describe("VirtualScrollTreeDatasource — cleanup and listener lifecycle", () => {

   it("cleanup() stops further scroll emits", () => {
      const zone  = new NgZone({ enableLongStackTrace: false });
      const ds    = new VirtualScrollTreeDatasource();
      const el    = document.createElement("div");
      const emits: unknown[] = [];

      ds.registerScrollContainer(el, zone).subscribe(v => emits.push(v));
      ds.cleanup();
      const countAfterCleanup = emits.length;

      el.dispatchEvent(new Event("scroll"));

      expect(emits.length).toBe(countAfterCleanup);
   });

   it("cleanup() is idempotent — calling twice does not throw", () => {
      const zone = new NgZone({ enableLongStackTrace: false });
      const ds   = new VirtualScrollTreeDatasource();
      const el   = document.createElement("div");

      ds.registerScrollContainer(el, zone);

      expect(() => { ds.cleanup(); ds.cleanup(); }).not.toThrow();
   });

   it("second registerScrollContainer() removes the previous element's listener", () => {
      const zone = new NgZone({ enableLongStackTrace: false });
      const ds   = new VirtualScrollTreeDatasource();
      const el1  = document.createElement("div");
      const el2  = document.createElement("div");
      const emits: unknown[] = [];

      ds.registerScrollContainer(el1, zone);                              // first registration
      ds.registerScrollContainer(el2, zone).subscribe(v => emits.push(v)); // replaces first

      const before = emits.length;

      el1.dispatchEvent(new Event("scroll")); // old listener was removed — must not emit
      expect(emits.length).toBe(before);

      el2.dispatchEvent(new Event("scroll")); // new listener is active — must emit
      expect(emits.length).toBeGreaterThan(before);
   });
});

// ── 5. restoreScrollTop ───────────────────────────────────────────────────────

describe("VirtualScrollTreeDatasource — restoreScrollTop", () => {

   it("transiently increments then restores the scrollTop value", () => {
      const ds = new VirtualScrollTreeDatasource();
      ds.scrollTop.next(100);

      const values: number[] = [];
      ds.scrollTop.subscribe(v => values.push(v)); // BehaviorSubject emits 100 immediately

      ds.restoreScrollTop();

      expect(values).toEqual([100, 101, 100]);
   });
});

// ── 6. inViewport / nodeVisible ───────────────────────────────────────────────

describe("VirtualScrollTreeDatasource — inViewport / nodeVisible", () => {

   it("inViewport returns false for a node not in the virtual scroll window", () => {
      const ds = new VirtualScrollTreeDatasource();
      expect(ds.inViewport(makeNode("a"))).toBe(false);
   });

   it("inViewport returns true for a node included in fireVirtualScroll", () => {
      const ds   = new VirtualScrollTreeDatasource();
      const node = makeNode("a");
      ds.fireVirtualScroll([node]);
      expect(ds.inViewport(node)).toBe(true);
   });

   it("nodeVisible returns false when node is neither in viewport nor a parent of viewport nodes", () => {
      const ds = new VirtualScrollTreeDatasource();
      expect(ds.nodeVisible(makeNode("x"))).toBe(false);
   });

   it("nodeVisible returns true for the parent of a viewport node", () => {
      const ds     = new VirtualScrollTreeDatasource();
      const parent = makeNode("parent");
      const child  = makeNode("child", parent);
      ds.fireVirtualScroll([child]);
      expect(ds.nodeVisible(parent)).toBe(true);
   });
});
