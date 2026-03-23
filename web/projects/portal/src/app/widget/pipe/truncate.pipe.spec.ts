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
import { TruncatePipe } from "./truncate.pipe";

describe("TruncatePipe", () => {
   let pipe: TruncatePipe;

   beforeEach(() => {
      pipe = new TruncatePipe();
   });

   it("should return value unchanged when shorter than default limit", () => {
      const short = "hello";
      expect(pipe.transform(short)).toBe(short);
   });

   it("should truncate with '...' when value exceeds default limit of 100", () => {
      const long = "a".repeat(101);
      const result = pipe.transform(long);
      expect(result).toBe("a".repeat(100) + "...");
   });

   it("should return value unchanged when exactly at default limit", () => {
      const exact = "a".repeat(100);
      expect(pipe.transform(exact)).toBe(exact);
   });

   it("should truncate to custom limit", () => {
      expect(pipe.transform("abcdef", "3")).toBe("abc...");
   });

   it("should use custom trail string", () => {
      expect(pipe.transform("abcdef", "3", "…")).toBe("abc…");
   });

   it("should apply both custom limit and custom trail", () => {
      expect(pipe.transform("hello world", "5", " [more]")).toBe("hello [more]");
   });

   it("should return value unchanged when under custom limit", () => {
      expect(pipe.transform("hi", "10")).toBe("hi");
   });
});
