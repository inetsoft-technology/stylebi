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
// must stay before the @testing-library/angular import; see the cleanup comment below
import "./vitest-setup-tl-capture";
import "@testing-library/angular";
import { afterAll, afterEach, beforeAll, vi } from "vitest";
import { server } from "@test-mocks/server";

// With isolate off, a worker runs about 140 TL files in one process and its memory grows from
// about 200 MB to 1.7 GB. On the 4 vCPU CI runner, which also runs the Java tests, the garbage
// collector then pauses the worker for seconds, and even tests that take a few milliseconds
// locally hit the 5 s default (a pinned 4-CPU local run with the CPUs saturated still passes).
// Until the remaining growth is found, give TL tests more time.
vi.setConfig({ testTimeout: 15000 });

beforeAll(() => server.listen({ onUnhandledRequest: "error" }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

// @testing-library/angular registers its auto cleanup with the global afterEach at its module top
// level, and a module is evaluated only once per worker when isolate is off, so only the first
// spec file got it. Every later fixture then stayed in its module-level set and was never
// destroyed by it. The cleanup function is not exported, so vitest-setup-tl-capture.ts captures
// it when the module is first evaluated (the imports above), and it is registered here for every
// spec file.
const atlCleanup: { fn?: () => void, globalAfterEach?: any } = (globalThis as any).__tlAtlCleanup;

if(atlCleanup.globalAfterEach) {
   (globalThis as any).afterEach = atlCleanup.globalAfterEach;
   delete atlCleanup.globalAfterEach;
}

if(!atlCleanup.fn) {
   throw new Error("Could not capture the @testing-library/angular cleanup; " +
      "it was loaded before vitest-setup-tl.ts");
}

afterEach(() => atlCleanup.fn());

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
