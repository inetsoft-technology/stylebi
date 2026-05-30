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
import { afterAll, afterEach, beforeAll } from "vitest";
import { TestBed } from "@angular/core/testing";
import { provideNgReflectAttributes } from "@angular/core";
import { server } from "../../../mocks/server";

// Mock ResizeObserver for components that use the resize-event shared library.
// jsdom does not provide ResizeObserver.
(globalThis as any).ResizeObserver = class ResizeObserver {
   observe() {}
   unobserve() {}
   disconnect() {}
};

// Angular 20 stopped producing the legacy `ng-reflect-*` attributes by default.
// Many existing tests assert against those attributes, so we register the
// `provideNgReflectAttributes()` EnvironmentProviders into every TestBed module
// configuration to opt back into the legacy behavior in dev/test mode only.
const _originalConfigureTestingModule = TestBed.configureTestingModule.bind(TestBed);
TestBed.configureTestingModule = function(moduleDef: any) {
   moduleDef = moduleDef ?? {};
   moduleDef.providers = [provideNgReflectAttributes(), ...(moduleDef.providers ?? [])];
   return _originalConfigureTestingModule(moduleDef);
} as typeof TestBed.configureTestingModule;

// MSW request mocking server. Lifecycle hooks installed at the module level so
// they apply to every test file that includes this setup.
beforeAll(() => server.listen({ onUnhandledRequest: "error" }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());
