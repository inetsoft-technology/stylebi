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

import { defineConfig } from "vitest/config";
import * as path from "node:path";

/**
 * Workspace-level Vitest base configuration.
 *
 * Consumed by the `@angular/build:unit-test` builder (runner: vitest) via the
 * `runnerConfig: true` option. The builder constructs its own Vitest projects
 * config; only the `test` options below that the builder permits (environment,
 * setupFiles, alias, etc.) are merged in.
 *
 * The Angular builder warns/strips the following keys if present:
 *   test.projects, test.include, test.watch, test.reporters
 * Reporters are configured via angular.json instead.
 */
export default defineConfig({
   resolve: {
      // Prefer the "browser" condition so packages with both Node and browser
      // entry points (e.g. sockjs-client's `debug` dependency, MSW v2 fetch
      // interceptor) resolve to their browser builds. Without "browser",
      // sockjs-client pulls in Node's `debug/src/node.js` which calls
      // `tty.isatty(process.stderr.fd)` and crashes under jsdom because
      // `process.stderr.fd` is `undefined`.
      conditions: ["browser", "es2020", "es2015", "module", "default"],
      alias: [
         {
            find: "@test-mocks",
            replacement: path.resolve(__dirname, "mocks"),
         },
         // ckeditor5 is ESM-only. While Vitest handles ESM natively, the package
         // pulls in browser-only dependencies (canvas, etc.) that fail under jsdom.
         // Stub the import out for the test environment.
         {
            find: /^ckeditor5(\/.*)?$/,
            replacement: path.resolve(__dirname, "__mocks__/ckeditor5.js"),
         },
      ],
   },
   test: {
      environment: "jsdom",
      // jest-canvas-mock replacement.
      // Listed first so canvas patches are installed before any test code runs.
      setupFiles: ["vitest-canvas-mock"],
      // Spec files that contain only it.skip tests (all tests disabled) should not
      // cause a build failure. These files document known-broken tests.
      passWithNoTests: true,
   },
});
