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
 * VirtualScrollTreeDatasource — Bug A: scroll event triggers Change Detection inside Zone
 * Issue #75489 
 * @confirmed-bug Bug-A  virtual-scroll-tree-datasource.ts:91 
 *   registerScrollContainer() calls element.addEventListener("scroll", ...) directly without
 *   ngZone.runOutsideAngular(). When the caller (tree.component.ts:960) invokes this method
 *   inside the Angular Zone, Zone.js wraps the scroll callback to run in the Angular Zone,
 *   causing every scroll event to fire a global CD cycle (onMicrotaskEmpty).
 *
 *   Affected scope: Composer Toolbox/Binding Tree — the only context where useVirtualScroll=true
 *   and the VS canvas coexist in the same component tree. At 20–30 scrolls/sec the extra CD
 *   consumes 3–5 frame budgets per second, causing visible jank.
 *
 *   Fix:
 *     ngZone.runOutsideAngular(() => {
 *       element.addEventListener("scroll", e => {
 *         ngZone.run(() => { this.dispatcher.next(this.dispatcher.value); });
 *         if(e.target instanceof HTMLElement) {
 *           ngZone.run(() => { this.scrollTop.next(e.target.scrollTop); });
 *         }
 *       });
 *     });
 *
 *   it.fails convention:
 *     - While the bug exists: the inner expect fails → it.fails is marked ✅ (expected failure)
 *     - After the fix: the inner expect passes → it.fails is marked ❌ (remove .fails)
 *
 * No HTTP calls (no MSW needed). VirtualScrollTreeDatasource is a plain class;
 * Zone.js behaviour is verified by constructing NgZone directly without render().
 */

import { NgZone } from "@angular/core";
import { VirtualScrollTreeDatasource } from "./virtual-scroll-tree-datasource";

// ── Shared utilities ──────────────────────────────────────────────────────────

/** Creates an isolated NgZone and returns a CD-trigger counter. */
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

afterEach(() => vi.restoreAllMocks());

// ── Baseline: verify the measurement mechanism is correct ─────────────────────

describe("VirtualScrollTreeDatasource — Baseline: measurement mechanism verification", () => {

   it("scroll listener registered outside zone.run() does not increment ngZone.onMicrotaskEmpty", () => {
      const { getCount, reset } = makeZoneAndCounter();
      const el = document.createElement("div");

      // Registered outside zone.run() — callback runs in the root Zone, not inside ngZone
      el.addEventListener("scroll", () => {});
      reset();

      el.dispatchEvent(new Event("scroll", { bubbles: true }));

      expect(getCount()).toBe(0);
   });

   it("listener registered inside zone.run() increments onMicrotaskEmpty after firing (proves measurement is valid)", () => {
      const { zone, getCount, reset } = makeZoneAndCounter();
      const el = document.createElement("div");

      zone.run(() => {
         el.addEventListener("scroll", () => { /* noop in-zone */ });
      });
      reset();

      el.dispatchEvent(new Event("scroll"));

      // In-zone handler fires → checkStable() → onMicrotaskEmpty triggers ≥ 1 time
      expect(getCount()).toBeGreaterThan(0);
   });
});

// ── Bug A: registerScrollContainer registers inside Zone → scroll triggers CD ─

describe("VirtualScrollTreeDatasource — Bug A: registerScrollContainer registers scroll listener inside Zone (virtual-scroll-tree-datasource.ts:91)", () => {

   // 🐛 single scroll triggers 1 CD cycle (should be 0)
   it.fails("single scroll must not trigger Zone CD (remove .fails after fix)", () => {
      const { zone, getCount, reset } = makeZoneAndCounter();
      const ds = new VirtualScrollTreeDatasource();
      const el = document.createElement("div");

      // tree.component.ts:960 calls registerScrollContainer inside Zone
      zone.run(() => { ds.registerScrollContainer(el, zone); });
      reset(); // reset counter to exclude CD triggered during setup

      el.dispatchEvent(new Event("scroll", { bubbles: true }));

      // Before fix: handler inside Zone → getCount() = 1 → expect fails → it.fails ✅
      // After fix:  use runOutsideAngular → getCount() = 0 → expect passes → it.fails ❌ remove it
      expect(getCount()).toBe(0);
   });

   // 🐛 10 rapid scrolls accumulate 10 CD cycles (quantifies the 20–30 scrolls/sec scenario)
   it.fails("10 scroll events must not accumulate Zone CD cycles (remove .fails after fix)", () => {
      const { zone, getCount, reset } = makeZoneAndCounter();
      const ds = new VirtualScrollTreeDatasource();
      const el = document.createElement("div");

      zone.run(() => { ds.registerScrollContainer(el, zone); });
      reset();

      for(let i = 0; i < 10; i++) {
         el.dispatchEvent(new Event("scroll"));
      }

      // Before fix: getCount() = 10 (one CD per scroll)
      // After fix:  getCount() = 0
      expect(getCount()).toBe(0);
   });

   // ✅ Regression guard: scroll must still trigger dispatcher emit after fix (data flow must not break)
   it("scroll still causes dispatcher to emit current value (data flow regression guard)", () => {
      const zone = new NgZone({ enableLongStackTrace: false });
      const ds = new VirtualScrollTreeDatasource();
      const el = document.createElement("div");

      const emits: unknown[] = [];
      zone.run(() => {
         ds.registerScrollContainer(el, zone).subscribe(val => emits.push(val));
      });

      const beforeCount = emits.length; // BehaviorSubject emits initial [] on subscribe
      el.dispatchEvent(new Event("scroll"));

      // scroll triggers dispatcher.next(currentValue) → produces another emit
      // must pass both before and after fix (fix changes Zone wrapping only, not emit behavior)
      expect(emits.length).toBeGreaterThan(beforeCount);
   });

   // ✅ Regression guard: multiple datasource instances must not interfere with each other
   it("multiple datasource instances on the same element each emit independently", () => {
      const zone = new NgZone({ enableLongStackTrace: false });
      const ds1 = new VirtualScrollTreeDatasource();
      const ds2 = new VirtualScrollTreeDatasource();
      const el = document.createElement("div");

      const emits1: unknown[] = [];
      const emits2: unknown[] = [];

      zone.run(() => {
         ds1.registerScrollContainer(el, zone).subscribe(v => emits1.push(v));
         ds2.registerScrollContainer(el, zone).subscribe(v => emits2.push(v));
      });

      const before1 = emits1.length;
      const before2 = emits2.length;

      el.dispatchEvent(new Event("scroll"));

      expect(emits1.length).toBeGreaterThan(before1);
      expect(emits2.length).toBeGreaterThan(before2);
   });
});
