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

import { defineConfig, mergeConfig } from "vitest/config";
import base from "./vitest-base.config";

/**
 * Vitest configuration for the Testing Library (`*.tl.spec.ts`) targets (`test-tl`).
 *
 * The Angular unit-test builder defaults `isolate` to `false`, so every spec file in a worker
 * shares one module graph and one jsdom window. Several TL specs patch globals (prototypes,
 * `window` properties, localStorage, timers) without fully restoring them, and the next file in
 * the same worker then fails. Which files share a worker depends on the CPU count, so the suite
 * passes on a developer machine and fails on CI.
 *
 * `isolate: true` gives each spec file a fresh environment. It is a stopgap that keeps CI stable;
 * it hides, rather than fixes, specs that leak global state. Keep fixing those specs so that they
 * restore what they change; once the TL suite passes in a shuffled order with `isolate: false`,
 * this override can be reconsidered.
 */
export default mergeConfig(base, defineConfig({
   test: {
      isolate: true,
   },
}));
