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
import { afterAll, afterEach, beforeAll, beforeEach } from "vitest";
import { server } from "@test-mocks/server";
import { GuiTool } from "./app/common/util/gui-tool";
import { clearStoredCondition } from "./app/common/util/schedule-condition.util";

// Buffer only — do not rely on this instead of syncResolve / no setTimeout(0).
vi.setConfig({ testTimeout: 15000 });

beforeAll(() => server.listen({ onUnhandledRequest: "error" }));

beforeEach(() => {
   // Prevent fake timers left by other suites from blocking setTimeout-based waits.
   vi.useRealTimers();
   // Real isTouchDevice() uses setTimeout + Subject.toPromise(); under a loaded
   // Vitest worker that leaves Zone busy and inflates async TL tests.
   vi.spyOn(GuiTool, "isTouchDevice").mockImplementation(() => Promise.resolve(false));
});

afterEach(() => {
   server.resetHandlers();
   clearStoredCondition();
   vi.useRealTimers();
});

afterAll(() => server.close());
