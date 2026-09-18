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
import { ComposerRecentService } from "./composer-recent.service";
import { AssetEntry } from "../../../../../shared/data/asset-entry";

describe("ComposerRecentService", () => {
   let service: ComposerRecentService;

   beforeEach(() => {
      service = new ComposerRecentService({} as any);
      // Bypass currentUser's LocalStorage round trip -- tests only care about the in-memory list.
      (service as any)._recentlyViewed = [{ path: "Existing/Sheet" } as AssetEntry];
      (service as any)._currentUser = "alice";
      vi.spyOn(service as any, "storeItems").mockImplementation(() => {});
   });

   describe("addRecentlyViewed", () => {
      // 🔁 Regression: Bug #76738. create_worksheet's unsaved blank worksheet has no real asset
      // path, so createAssetEntry(null) hands this a null entry. Before the fix, entry.path threw
      // here once recentlyViewed was non-empty (true for any session with prior activity), and
      // that exception propagated out of composer-main.component.ts's editAsset subscription
      // callback -- skipping the openWorksheet(...) call right after it, so the new tab silently
      // never opened.
      it("does not throw when entry is null, and leaves the list unchanged", () => {
         expect(() => service.addRecentlyViewed(null)).not.toThrow();
         expect(service.recentlyViewed).toEqual([{ path: "Existing/Sheet" }]);
      });

      it("still adds a real entry to the front of the list", () => {
         service.addRecentlyViewed({ path: "New/Sheet" } as AssetEntry);

         expect(service.recentlyViewed[0]).toEqual({ path: "New/Sheet" });
      });

      it("replaces an existing entry with the same path instead of duplicating it", () => {
         service.addRecentlyViewed({ path: "Existing/Sheet", alias: "renamed" } as AssetEntry);

         expect(service.recentlyViewed).toEqual([{ path: "Existing/Sheet", alias: "renamed" }]);
      });
   });
});
