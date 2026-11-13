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
 * VSPane registers its own ViewsheetClientService with the SheetClientRegistry (bug #80459
 * follow-up) so a formula dialog opened through NgbModal, which only sees the root injector, can
 * find the client for a runtime id and pair against it. Registered as a VIEWSHEET at init,
 * unregistered at destroy.
 */
import { TestBed } from "@angular/core/testing";
import { SheetClientRegistry } from "../../../../common/viewsheet-client/sheet-client-registry.service";
import { makeMocks, renderComponent } from "./viewsheet-pane.component.test-helpers";

afterEach(() => vi.restoreAllMocks());

describe("VSPane - sheet client registry", () => {
   it("registers its client as a VIEWSHEET at init", async () => {
      const mocks = makeMocks();
      await renderComponent(mocks);
      (mocks.vsClient as any).runtimeId = "rt-1";

      const entry = TestBed.inject(SheetClientRegistry).lookup("rt-1");

      expect(entry?.client).toBe(mocks.vsClient);
      expect(entry?.sheetType).toBe("VIEWSHEET");
   });

   it("follows the client when its runtime id changes after registration", async () => {
      const mocks = makeMocks();
      await renderComponent(mocks);
      (mocks.vsClient as any).runtimeId = "rt-1";
      (mocks.vsClient as any).runtimeId = "rt-2";

      const registry = TestBed.inject(SheetClientRegistry);

      expect(registry.lookup("rt-2")?.client).toBe(mocks.vsClient);
      expect(registry.lookup("rt-1")).toBeNull();
   });

   it("unregisters its client at destroy", async () => {
      const mocks = makeMocks();
      const { fixture } = await renderComponent(mocks);
      (mocks.vsClient as any).runtimeId = "rt-1";
      const registry = TestBed.inject(SheetClientRegistry);
      expect(registry.lookup("rt-1")).not.toBeNull();

      fixture.destroy();

      expect(registry.lookup("rt-1")).toBeNull();
   });
});
