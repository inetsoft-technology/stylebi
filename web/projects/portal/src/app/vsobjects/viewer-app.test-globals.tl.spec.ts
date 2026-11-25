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

import { afterEach, describe, expect, it, vi } from "vitest";

// Specs in one worker share the jsdom window (isolate is off), and some of them call
// vi.unstubAllGlobals(). The globals the viewer-app specs rely on must survive that,
// whatever order the specs run in.
describe("viewer-app test globals", () => {
   afterEach(() => vi.unstubAllGlobals());

   it("keeps IntersectionObserver and globalPostParams after vi.unstubAllGlobals()", () => {
      vi.stubGlobal("innerWidth", 460);
      vi.unstubAllGlobals();

      expect(typeof (globalThis as any).IntersectionObserver).toBe("function");
      expect((globalThis as any).globalPostParams).toBeNull();
   });
});
