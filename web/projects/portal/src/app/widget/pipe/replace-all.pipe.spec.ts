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
import { ReplaceAllPipe } from "./replace-all.pipe";

describe("ReplaceAllPipe", () => {
   let pipe: ReplaceAllPipe;

   beforeEach(() => {
      pipe = new ReplaceAllPipe();
   });

   it("should replace a single occurrence", () => {
      expect(pipe.transform("hello world", "world", "there")).toBe("hello there");
   });

   it("should replace all occurrences globally", () => {
      expect(pipe.transform("aaa", "a", "b")).toBe("bbb");
   });

   it("should return null when value is null", () => {
      expect(pipe.transform(null, "x", "y")).toBeNull();
   });

   it("should return empty string when value is empty string", () => {
      expect(pipe.transform("", "x", "y")).toBe("");
   });

   it("should support regex patterns", () => {
      expect(pipe.transform("foo123bar456", "\\d+", "#")).toBe("foo#bar#");
   });

   it("should replace with empty string when input is empty", () => {
      expect(pipe.transform("remove me", "remove me", "")).toBe("");
   });

   // The implementation always uses the "gm" flags: g (global) + m (multiline).
   // The m flag makes ^ match at the start of each line, not just the start of the string.
   it("should apply multiline flag so ^ matches the start of every line", () => {
      const multiline = "line1\nline2\nline3";
      expect(pipe.transform(multiline, "^line", "row")).toBe("row1\nrow2\nrow3");
   });
});
