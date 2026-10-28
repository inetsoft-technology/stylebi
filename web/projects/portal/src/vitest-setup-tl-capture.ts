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

// Imported by vitest-setup-tl.ts right before @testing-library/angular. While that module is
// evaluated, the global afterEach only records the callback it registers (its auto cleanup), so
// vitest-setup-tl.ts can register the cleanup for every spec file. See vitest-setup-tl.ts.
const capture: any = (globalThis as any).__tlAtlCleanup ??= {};

capture.globalAfterEach = (globalThis as any).afterEach;
(globalThis as any).afterEach = (fn: () => void) => capture.fn = fn;
