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
import { DateTipHelper } from "./date-tip-helper";

describe("DateTipHelper stacking", () => {
   // Mirrors .fixed-dropdown in $stacking-order (scss/internal/_directives.scss).
   const FIXED_DROPDOWN_Z = 999900;

   it("orders scrim below source below content", () => {
      expect(DateTipHelper.getPopUpBackgroundZIndex())
         .toBeLessThan(DateTipHelper.getPopUpSourceZIndex());
      expect(DateTipHelper.getPopUpSourceZIndex())
         .toBeLessThan(DateTipHelper.getPopUpContentZIndex(0));
   });

   it("keeps pop content below the dropdown layer for any natural z-index", () => {
      for(const natural of [0, 1, 5, 100, 999, 100000, 1000000000]) {
         expect(DateTipHelper.getPopUpContentZIndex(natural)).toBeLessThan(FIXED_DROPDOWN_Z);
      }
   });

   it("preserves relative order between two boosted objects below the cap", () => {
      expect(DateTipHelper.getPopUpContentZIndex(1))
         .toBeLessThan(DateTipHelper.getPopUpContentZIndex(2));
   });

   it("treats a missing natural z-index as zero", () => {
      expect(DateTipHelper.getPopUpContentZIndex(undefined))
         .toBe(DateTipHelper.getPopUpContentZIndex(0));
   });
});

describe("DateTipHelper scrim colour", () => {
   beforeEach(() => document.body.classList.remove("viz-modern"));
   afterEach(() => document.body.classList.remove("viz-modern"));

   it("uses the legacy 0.2 scrim when the modern gate is off", () => {
      expect(DateTipHelper.popDimColor).toBe("rgba(0, 0, 0, 0.2)");
   });

   it("uses the shipped 0.3 overlay scrim when the modern gate is on", () => {
      document.body.classList.add("viz-modern");
      expect(DateTipHelper.popDimColor).toBe("rgba(0, 0, 0, 0.3)");
   });
});
