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
 * The binding pane registers its own client in the SheetClientRegistry (bug #80459, PR C) so a
 * binding editor field can pair against the BINDING runtime. It never does so in the viewer, and
 * it unregisters when the pane goes away.
 */
import { TestBed } from "@angular/core/testing";
import { SheetClientRegistry } from "../../common/viewsheet-client/sheet-client-registry.service";
import { CLIENT_SERVICE_MOCK, renderComponent, resetMocks } from "./vs-binding-pane.test-fixtures";

beforeEach(() => resetMocks());

describe("VSBindingPane registers its client for the binding runtime", () => {
   it("registers as a VIEWSHEET client once the binding runtime id is known", async () => {
      await renderComponent();

      const entry = TestBed.inject(SheetClientRegistry).lookup("rt-test");
      expect(entry?.client).toBe(CLIENT_SERVICE_MOCK);
      expect(entry?.sheetType).toBe("VIEWSHEET");
   });

   it("does not register in the viewer", async () => {
      await renderComponent({ viewer: true });

      expect(TestBed.inject(SheetClientRegistry).lookup("rt-test")).toBeNull();
   });

   it("unregisters when the pane is destroyed", async () => {
      const { fixture } = await renderComponent();
      const registry = TestBed.inject(SheetClientRegistry);
      expect(registry.lookup("rt-test")).not.toBeNull();

      fixture.destroy();

      expect(registry.lookup("rt-test")).toBeNull();
   });
});
