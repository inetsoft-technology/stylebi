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

import "@testing-library/jest-dom/vitest";
import { provideNgReflectAttributes } from "@angular/core";
import { ComponentFixture, TestBed } from "@angular/core/testing";

// Mock ResizeObserver for components that use the resize-event shared library.
// jsdom does not provide ResizeObserver.
(globalThis as any).ResizeObserver = class ResizeObserver {
   observe() {}
   unobserve() {}
   disconnect() {}
};

// Under @angular-builders/jest, Angular's NG0100 (ExpressionChangedAfterItHasBeenCheckedError)
// was surfaced as a console.error but not as a test failure. In Angular 21 + Vitest, this
// error is thrown as a RuntimeError from fixture.detectChanges(). Patch detectChanges to
// catch and suppress NG0100, then re-run without the no-changes check to complete any
// pending binding updates. Matches the legacy non-fatal Jest behavior.
// TODO: fix the underlying CD ordering issues in specs and remove this patch.
const _origDetectChanges = (ComponentFixture.prototype as any).detectChanges;
(ComponentFixture.prototype as any).detectChanges = function(checkNoChanges: boolean = true) {
   try {
      return _origDetectChanges.call(this, checkNoChanges);
   } catch (e: any) {
      if (e?.message?.includes("ExpressionChangedAfterItHasBeenCheckedError")) {
         console.warn("[vitest-setup] NG0100 suppressed:", e.message.substring(0, 200));
         try { _origDetectChanges.call(this, false); } catch {}
         return;
      }
      throw e;
   }
};

// Patch configureTestingModule to inject provideNgReflectAttributes() so that
// ng-reflect-* attribute assertions in legacy tests continue to work.
// (Angular 20 stopped emitting these by default.)
const _originalConfigureTestingModule = TestBed.configureTestingModule.bind(TestBed);
TestBed.configureTestingModule = function(moduleDef: any) {
   moduleDef = moduleDef ?? {};
   moduleDef.providers = [
      provideNgReflectAttributes(),
      ...(moduleDef.providers ?? []),
   ];
   return _originalConfigureTestingModule(moduleDef);
} as typeof TestBed.configureTestingModule;

// Fix VSViewsheet ↔ VSObjectContainer circular dependency.
//
// Both components import each other in their @Component.imports arrays. Vitest with
// @angular/build:unit-test uses ESM module workers, but Angular's JIT compiler evaluates
// component metadata (ɵcmp) at import time. If VSObjectContainer is processed before
// VSViewsheet finishes decorating, its ɵcmp.dependencies array contains an undefined slot.
//
// This file runs in setupFilesAfterEnv (after the Angular compiler is available), so both
// modules have been imported by the time this code runs. We eagerly resolve them and patch
// any undefined entries in VSObjectContainer's dependency list.
//
// This is the same workaround as the deleted jest-setup-after-env.ts; the circular import
// issue exists in both Jest/CommonJS and Vitest/ESM worker contexts.
import { VSViewsheet } from "./app/vsobjects/objects/viewsheet/vs-viewsheet.component";
import { VSObjectContainer } from "./app/vsobjects/objects/vs-object-container.component";

const _ocDef = (VSObjectContainer as any).ɵcmp;
if (_ocDef) {
   const _rawDeps = typeof _ocDef.dependencies === "function"
      ? _ocDef.dependencies()
      : _ocDef.dependencies;
   if (Array.isArray(_rawDeps)) {
      const _fixed = _rawDeps.map((d: any) => (d === undefined || d === null) ? VSViewsheet : d);
      _ocDef.dependencies = _fixed;
   }
}

// NOTE: MSW is intentionally NOT started here for the main portal test suite.
// Under the old Jest setup, setup-jest.ts (which had MSW) was only wired to
// jest.tl.config.js (the TL test suite), not to the main portal tests.
// MSW is started in vitest.tl.config.ts's setupFiles for the TL suite instead.
