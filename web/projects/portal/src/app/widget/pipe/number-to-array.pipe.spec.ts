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
import { NumberToArrayPipe } from "./number-to-array.pipe";

describe("NumberToArrayPipe", () => {
   let pipe: NumberToArrayPipe;

   beforeEach(() => {
      pipe = new NumberToArrayPipe();
   });

   it("should return empty array for 0", () => {
      expect(pipe.transform(0)).toEqual([]);
   });

   it("should return [0, 1, 2] for 3", () => {
      expect(pipe.transform(3)).toEqual([0, 1, 2]);
   });

   it("should return indices starting from 0", () => {
      const result = pipe.transform(5);
      expect(result).toEqual([0, 1, 2, 3, 4]);
   });

   it("should return array with correct length", () => {
      expect(pipe.transform(10).length).toBe(10);
   });

   it("should throw RangeError for negative values", () => {
      expect(() => pipe.transform(-1)).toThrow(RangeError);
   });
});
