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
import { MapToIterable } from "./map-to-iterable.pipe";

describe("MapToIterable", () => {
   let pipe: MapToIterable;

   beforeEach(() => {
      pipe = new MapToIterable();
   });

   it("should return null for null input", () => {
      expect(pipe.transform(null, [])).toBeNull();
   });

   it("should convert a Map to an array of {key, value} pairs", () => {
      const map = new Map<string, number>();
      map.set("a", 1);
      map.set("b", 2);
      const result = pipe.transform(map, []);
      expect(result).toContainEqual({ key: "a", value: 1 });
      expect(result).toContainEqual({ key: "b", value: 2 });
      expect(result.length).toBe(2);
   });

   it("should convert a plain object to an array of {key, value} pairs", () => {
      const obj = { x: 10, y: 20 };
      const result = pipe.transform(obj, []);
      expect(result).toContainEqual({ key: "x", value: 10 });
      expect(result).toContainEqual({ key: "y", value: 20 });
      expect(result.length).toBe(2);
   });

   it("should return empty array for empty Map", () => {
      expect(pipe.transform(new Map(), [])).toEqual([]);
   });

   it("should return empty array for empty object", () => {
      expect(pipe.transform({}, [])).toEqual([]);
   });

   it("should not include inherited properties for plain objects", () => {
      const obj = Object.create({ inherited: true });
      obj.own = "value";
      const result = pipe.transform(obj, []);
      expect(result).toEqual([{ key: "own", value: "value" }]);
   });
});
