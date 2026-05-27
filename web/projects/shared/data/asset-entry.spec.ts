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
import { AssetType } from "./asset-type";
import { createAssetEntry } from "./asset-entry";

describe("createAssetEntry", () => {
   it("parses a global viewsheet asset ID", () => {
      // format: scope^type^user^path
      const entry = createAssetEntry("1^128^__NULL__^My Reports/Sales Dashboard");
      expect(entry).not.toBeNull();
      expect(entry.scope).toBe(1);
      expect(entry.type).toBe(AssetType.VIEWSHEET);
      expect(entry.user).toBeNull();                          // __NULL__ → null
      expect(entry.path).toBe("My Reports/Sales Dashboard");
      expect(entry.identifier).toBe("1^128^__NULL__^My Reports/Sales Dashboard");
      expect(entry.alias).toBeNull();
      expect(entry.properties).toEqual({});
   });

   it("parses a user-scoped worksheet asset ID", () => {
      const entry = createAssetEntry("4^2^alice^My Worksheets/Budget");
      expect(entry).not.toBeNull();
      expect(entry.scope).toBe(4);
      expect(entry.type).toBe(AssetType.WORKSHEET);
      expect(entry.user).toBe("alice");
      expect(entry.path).toBe("My Worksheets/Budget");
   });

   it("parses an asset ID with an organization segment", () => {
      const entry = createAssetEntry("1^128^__NULL__^Reports/MyVS^my-org");
      expect(entry).not.toBeNull();
      expect(entry.organization).toBe("my-org");
      expect(entry.path).toBe("Reports/MyVS");
   });

   it("parses a schedule task asset ID", () => {
      const entry = createAssetEntry("1^5^__NULL__^Daily Export");
      expect(entry).not.toBeNull();
      expect(entry.type).toBe(AssetType.SCHEDULE_TASK);
      expect(entry.path).toBe("Daily Export");
   });

   it("returns null for malformed asset ID (missing segments)", () => {
      expect(createAssetEntry("1^128")).toBeNull();
      expect(createAssetEntry("notanassetid")).toBeNull();
      expect(createAssetEntry("")).toBeNull();
   });

   it("returns null for null/undefined input", () => {
      expect(createAssetEntry(null)).toBeNull();
      expect(createAssetEntry(undefined)).toBeNull();
   });

   it("preserves the original identifier string", () => {
      const id = "1^128^__NULL__^folder/report";
      const entry = createAssetEntry(id);
      expect(entry.identifier).toBe(id);
   });
});
