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

// TL (testing-library) test setup for the portal project. Starts the MSW server
// lifecycle so all *.tl.spec.ts files in portal can intercept HTTP requests.
import { getTestBed } from "@angular/core/testing";
import { afterAll, afterEach, beforeAll, vi } from "vitest";
import { server } from "@test-mocks/server";

// With isolate off, a worker runs about 140 TL files in one process. Its retained heap grew from
// about 150 MB to 1.4 GB, mostly from the testing-library fixtures fixed below; it still grows
// to about 0.8 GB (intervals and smaller leaks). On the 4 vCPU CI runner, which also runs the
// Java tests, the garbage collector then pauses the worker for seconds, and tests that take a few
// milliseconds locally hit the 5 s default. Give TL tests more time.
vi.setConfig({ testTimeout: 15000 });

beforeAll(() => server.listen({ onUnhandledRequest: "error" }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

// @testing-library/angular keeps every rendered fixture in a module-level set and registers its
// cleanup with the global afterEach when the module is first evaluated. With isolate off that
// happens once per worker, so only the first spec file got the cleanup; the set then kept every
// later fixture, with its destroyed component tree and DOM, and worker memory grew by more than
// 1 GB. The cleanup is not exported, so record it when the library registers it through the
// global afterEach (during the first spec file that imports it, which is the instance the specs
// use) and register it for every spec file.
const atlCleanup: { fn?: () => void, wrapped?: boolean } = (globalThis as any).__tlAtlCleanup ??= {};

if(!atlCleanup.wrapped) {
   atlCleanup.wrapped = true;
   const globalAfterEach = (globalThis as any).afterEach;

   (globalThis as any).afterEach = function(fn: any, ...rest: any[]) {
      if(!atlCleanup.fn && typeof fn === "function" && /\bcleanup\d*\(\)/.test(String(fn))) {
         atlCleanup.fn = fn;
      }

      return globalAfterEach.call(this, fn, ...rest);
   };
}

afterEach(() => atlCleanup.fn?.());

// Spec files in a worker share one jsdom window, so a value one file leaves in web storage (e.g.
// the schedule condition history written by SimpleScheduleDialog.ok()) is read by the next file.
// Give every spec file empty storage, as it would get in a fresh environment.
afterAll(() => {
   localStorage.clear();
   sessionStorage.clear();
});

// GuiTool.setDragImage() adds a document "dragover" listener that only a "dragend" removes. A
// test that starts a drag without ending it (often with a DomService mock that has no
// requestRead) leaves that listener on the shared document, and any later dragover then throws.
// End every such drag after each test.
afterEach(() => {
   document.dispatchEvent(new Event("dragend"));
});

// Overriding a component, directive or pipe (render()'s componentImports, importOverrides and
// componentProviders all do) makes TestBed recompile it with JIT, which replaces its static ɵfac
// with a getter-only property. TestBed's reset restores ɵcmp but not ɵfac, so a subclass that is
// first loaded by a later spec file in the same worker fails at import: its static
// `ɵfac = ...` cannot be assigned over the inherited getter. Restore ɵfac after every test.
const FACTORY_DEF = "ɵfac";
const factoryDefs: Map<any, PropertyDescriptor | undefined> =
   (globalThis as any).__tlFactoryDefs ??= new Map();
const testBed: any = getTestBed();

if(!testBed.__tlFactoryDefsPatched) {
   testBed.__tlFactoryDefsPatched = true;

   for(const method of ["overrideComponent", "overrideDirective", "overridePipe"]) {
      const original = testBed[method];

      testBed[method] = function(type: any, ...args: any[]) {
         if(!factoryDefs.has(type)) {
            factoryDefs.set(type, Object.getOwnPropertyDescriptor(type, FACTORY_DEF));
         }

         return original.call(this, type, ...args);
      };
   }
}

afterEach(() => {
   factoryDefs.forEach((descriptor, type) => {
      if(descriptor) {
         Object.defineProperty(type, FACTORY_DEF, descriptor);
      }
      else {
         delete type[FACTORY_DEF];
      }
   });

   factoryDefs.clear();
});
