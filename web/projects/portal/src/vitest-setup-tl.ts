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
import { afterAll, afterEach, beforeAll } from "vitest";
import { server } from "@test-mocks/server";

beforeAll(() => server.listen({ onUnhandledRequest: "error" }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

// Spec files in a worker share one jsdom window, so a value one file leaves in web storage (e.g.
// the schedule condition history written by SimpleScheduleDialog.ok()) is read by the next file.
// Give every spec file empty storage, as it would get in a fresh environment.
afterAll(() => {
   localStorage.clear();
   sessionStorage.clear();
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
