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
import { SheetClientRegistry } from "./sheet-client-registry.service";

function client(runtimeId: string | null): any {
   return { runtimeId };
}

describe("SheetClientRegistry", () => {
   let registry: SheetClientRegistry;

   beforeEach(() => {
      registry = new SheetClientRegistry();
   });

   it("finds a registered client by its runtime id and returns the sheet type", () => {
      const c = client("rt-1");
      registry.register(c, "VIEWSHEET");

      const found = registry.lookup("rt-1");
      expect(found.client).toBe(c);
      expect(found.sheetType).toBe("VIEWSHEET");
   });

   it("returns null for an unknown id", () => {
      registry.register(client("rt-1"), "VIEWSHEET");
      expect(registry.lookup("rt-2")).toBeNull();
   });

   it.each([[null], [undefined], [""]])("returns null for the id %p", (id) => {
      registry.register(client(null), "VIEWSHEET");
      registry.register(client(""), "VIEWSHEET");
      expect(registry.lookup(id as any)).toBeNull();
   });

   it("matches on the client's CURRENT runtime id, not the id at registration", () => {
      const c = client("old");
      registry.register(c, "VIEWSHEET");
      c.runtimeId = "new";

      expect(registry.lookup("new")?.client).toBe(c);
      expect(registry.lookup("old")).toBeNull();
   });

   it("finds a client whose id was assigned after registration", () => {
      const c = client(null);
      registry.register(c, "VIEWSHEET");
      c.runtimeId = "late";
      expect(registry.lookup("late")?.client).toBe(c);
   });

   it("returns null and warns once when two clients own the same id", () => {
      const warn = vi.spyOn(console, "warn").mockImplementation(() => {});
      registry.register(client("rt-1"), "VIEWSHEET");
      registry.register(client("rt-1"), "VIEWSHEET");

      expect(registry.lookup("rt-1")).toBeNull();
      expect(warn).toHaveBeenCalledTimes(1);
      expect(warn.mock.calls[0][0]).toContain("rt-1");
      expect(warn.mock.calls[0][0]).toContain("2");
      warn.mockRestore();
   });

   it("heals once the duplicate unregisters", () => {
      vi.spyOn(console, "warn").mockImplementation(() => {});
      const a = client("rt-1");
      const b = client("rt-1");
      registry.register(a, "VIEWSHEET");
      const unregisterB = registry.register(b, "VIEWSHEET");
      expect(registry.lookup("rt-1")).toBeNull();

      unregisterB();
      expect(registry.lookup("rt-1")?.client).toBe(a);
   });

   it("register is idempotent for the same client object", () => {
      const c = client("rt-1");
      registry.register(c, "VIEWSHEET");
      registry.register(c, "VIEWSHEET");
      expect(registry.lookup("rt-1")?.client).toBe(c);
   });

   it("the returned function unregisters, and calling it twice is a no-op", () => {
      const c = client("rt-1");
      const other = client("rt-2");
      const unregister = registry.register(c, "VIEWSHEET");
      registry.register(other, "VIEWSHEET");

      unregister();
      unregister();

      expect(registry.lookup("rt-1")).toBeNull();
      expect(registry.lookup("rt-2")?.client).toBe(other);
   });

   it("a stale unregister does not remove a client registered again afterwards", () => {
      const c = client("rt-1");
      const first = registry.register(c, "VIEWSHEET");
      first();
      registry.register(c, "VIEWSHEET");
      first();
      expect(registry.lookup("rt-1")?.client).toBe(c);
   });
});
