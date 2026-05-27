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
import { AssetType, getTypeForId, getTypeId } from "./asset-type";

describe("asset-type", () => {
   describe("getTypeForId", () => {
      it("returns correct type for known IDs", () => {
         expect(getTypeForId(0)).toBe(AssetType.UNKNOWN);
         expect(getTypeForId(1)).toBe(AssetType.FOLDER);
         expect(getTypeForId(2)).toBe(AssetType.WORKSHEET);
         expect(getTypeForId(128)).toBe(AssetType.VIEWSHEET);
         expect(getTypeForId(5)).toBe(AssetType.SCHEDULE_TASK);
         expect(getTypeForId(6)).toBe(AssetType.SCHEDULE_TASK_FOLDER);
         expect(getTypeForId(69)).toBe(AssetType.DATA_SOURCE);
         expect(getTypeForId(37)).toBe(AssetType.QUERY);
         expect(getTypeForId(101)).toBe(AssetType.LOGIC_MODEL);
         expect(getTypeForId(1048576)).toBe(AssetType.SCRIPT);
         expect(getTypeForId(524288)).toBe(AssetType.TABLE_STYLE);
      });

      it("returns null for unknown ID", () => {
         expect(getTypeForId(9999)).toBeNull();
         expect(getTypeForId(-1)).toBeNull();
      });
   });

   describe("getTypeId", () => {
      it("returns correct ID for known types", () => {
         expect(getTypeId(AssetType.UNKNOWN)).toBe(0);
         expect(getTypeId(AssetType.FOLDER)).toBe(1);
         expect(getTypeId(AssetType.WORKSHEET)).toBe(2);
         expect(getTypeId(AssetType.VIEWSHEET)).toBe(128);
         expect(getTypeId(AssetType.SCHEDULE_TASK)).toBe(5);
         expect(getTypeId(AssetType.SCHEDULE_TASK_FOLDER)).toBe(6);
         expect(getTypeId(AssetType.DATA_SOURCE)).toBe(69);
      });

      it("returns undefined for unknown type", () => {
         expect(getTypeId("NOT_A_TYPE" as AssetType)).toBeUndefined();
      });

      it("is inverse of getTypeForId for all mapped types", () => {
         const knownIds = [0, 1, 2, 3, 4, 5, 6, 10, 12, 21, 37, 69, 101, 128];
         for(const id of knownIds) {
            const type = getTypeForId(id);
            expect(getTypeId(type)).toBe(id);
         }
      });
   });
});
