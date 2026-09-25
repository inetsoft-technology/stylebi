/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
import { contentHeight, contentWidth } from "./table-content-rect";

describe("contentWidth", () => {
   // The 99s are decoys: they prove the vertical edges are never read, which a rect-shaped
   // assertion over four distinct values could only catch as a transposition.
   it("should take only the horizontal edges", () => {
      expect(contentWidth(400, { top: 99, left: 2, bottom: 99, right: 8 })).toBe(390);
   });

   it("should return the card width unchanged when there is no padding", () => {
      expect(contentWidth(400, null)).toBe(400);
   });

   // A zero padding is a different path from a null one: the object is truthy, so the
   // subtraction runs rather than being skipped.
   it("should return the card width unchanged for a zero padding", () => {
      expect(contentWidth(400, { top: 0, left: 0, bottom: 0, right: 0 })).toBe(400);
   });

   it("should clamp at zero", () => {
      expect(contentWidth(10, { top: 0, left: 12, bottom: 0, right: 12 })).toBe(0);
   });
});

describe("contentHeight", () => {
   it("should take only the vertical edges", () => {
      expect(contentHeight(300, { top: 1, left: 99, bottom: 4, right: 99 })).toBe(295);
   });

   it("should return the card height unchanged when there is no padding", () => {
      expect(contentHeight(300, null)).toBe(300);
   });

   it("should return the card height unchanged for a zero padding", () => {
      expect(contentHeight(300, { top: 0, left: 0, bottom: 0, right: 0 })).toBe(300);
   });

   it("should clamp at zero", () => {
      expect(contentHeight(10, { top: 12, left: 0, bottom: 12, right: 0 })).toBe(0);
   });
});

// BaseTable.getPadding() hands every caller one shared frozen ZERO_PADDING, so a function that
// wrote to its padding argument would corrupt that single object for the whole application.
describe("padding argument", () => {
   it("should not be mutated by either accessor", () => {
      const inset = { top: 12, left: 10, bottom: 14, right: 6 };

      contentWidth(400, inset);
      contentHeight(300, inset);

      expect(inset).toEqual({ top: 12, left: 10, bottom: 14, right: 6 });
   });
});
