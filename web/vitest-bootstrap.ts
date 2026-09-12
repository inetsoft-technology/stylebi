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
 * Test environment bootstrap that runs before any test modules are loaded.
 *
 * 1. The `debug` package (used transitively by sockjs-client and others)
 *    selects its Node entry point when running under Node.js, then calls
 *    `tty.isatty(process.stderr.fd)`. Vitest replaces `process.stderr` with a
 *    writable stream that does not expose `.fd`, so this crashes at import
 *    time. Setting `process.browser = true` makes `debug` take its browser
 *    branch which uses `console.log` instead of TTY detection.
 *
 * 2. Angular's `waitForAsync()` / `fakeAsync()` require the test runner to be
 *    patched into zone.js so each test executes inside a `ProxyZone`. The
 *    Jasmine and Mocha patches ship with `zone.js/testing`, but the Vitest
 *    patch lives in a separate plugin and must be imported explicitly.
 */
(globalThis as any).process ??= {};
(globalThis as any).process.browser = true;

// CodeMirror measures text with Range geometry APIs. jsdom exposes Range but
// does not implement these layout methods, so provide stable no-op geometry.
function _emptyDOMRect(): DOMRect {
   return typeof DOMRect === "function"
      ? new DOMRect(0, 0, 0, 0)
      : {
         x: 0,
         y: 0,
         width: 0,
         height: 0,
         top: 0,
         right: 0,
         bottom: 0,
         left: 0,
         toJSON() {
            return this;
         },
      } as DOMRect;
}

function _emptyDOMRectList(): DOMRectList {
   const rects = [] as unknown as DOMRectList & { item(index: number): DOMRect | null };
   rects.item = (index: number) => rects[index] ?? null;

   return rects;
}

const _Range = (globalThis as any).Range;
if (_Range?.prototype) {
   _Range.prototype.getBoundingClientRect ??= () => _emptyDOMRect();
   _Range.prototype.getClientRects ??= () => _emptyDOMRectList();
}

if (typeof window !== "undefined") {
   window.focus = () => {};
}


// The zone.js vitest patch wraps `globalThis.vitest.it`, `.describe`, etc.,
// to run each test inside a `ProxyZone`. The patch is keyed on
// `context["vitest"]`, so we expose the Vitest test API as `globalThis.vitest`
// before importing the patch.
import * as vitestApi from "vitest";
(globalThis as any).vitest = vitestApi;

import "zone.js/plugins/vitest-patch";

// Verify that the patch wrapped Vitest's test hooks. If `globalThis.describe`
// is not the wrapper, log a diagnostic so test failures are easier to triage.
const _Zone = (globalThis as any).Zone;
if (_Zone && !(globalThis as any)["__zone_symbol__describe"]) {
   // eslint-disable-next-line no-console
   console.warn(
      "[vitest-bootstrap] zone.js vitest patch did not wrap describe/it; " +
      "tests using waitForAsync()/fakeAsync() will fail with 'Expected to be running in ProxyZone'.",
   );
}

function _patchVitestFactoryMethod(
   patchedFn: any,
   originalFn: any,
   factoryName: string,
): void {
   const originalFactory = originalFn?.[factoryName];

   if (typeof originalFactory !== "function") {
      return;
   }

   patchedFn[factoryName] = originalFactory.bind(originalFn);
}

// Zone.js wraps `it`/`test` but only re-attaches a fixed list of static methods.
// Vitest 4 has additional statics such as `fails`; copy missing statics from the
// original pre-patch function stored under Zone.__symbol__(name).
if (_Zone) {
   for (const fnName of ["it", "test", "describe", "suite"]) {
      const patchedFn = (globalThis as any)[fnName];
      const originalFn = (globalThis as any)[_Zone.__symbol__(fnName)];

      if (patchedFn && originalFn && patchedFn !== originalFn) {
         for (const key of Object.getOwnPropertyNames(originalFn)) {
            if (!(key in patchedFn)) {
               const desc = Object.getOwnPropertyDescriptor(originalFn, key);

               if (desc) {
                  Object.defineProperty(patchedFn, key, desc);
               }
            }
         }

         for (const factoryName of ["each", "for"]) {
            _patchVitestFactoryMethod(patchedFn, originalFn, factoryName);
         }
      }
   }
}

// The Angular CLI's vitest unit-test builder initializes TestBed with
// `errorOnUnknownElements: true` and `errorOnUnknownProperties: true`. These
// flags did not exist (or were `false` by default) under @angular-builders/jest,
// and many existing specs rely on the looser behavior. Reinitialize the
// testing environment with the legacy defaults so the migration doesn't
// require touching every spec's TestBed.configureTestingModule call.
import { TestBed } from "@angular/core/testing";
import { BrowserTestingModule, platformBrowserTesting } from "@angular/platform-browser/testing";

TestBed.resetTestEnvironment();
TestBed.initTestEnvironment([BrowserTestingModule], platformBrowserTesting(), {
   errorOnUnknownElements: false,
   errorOnUnknownProperties: false,
});
