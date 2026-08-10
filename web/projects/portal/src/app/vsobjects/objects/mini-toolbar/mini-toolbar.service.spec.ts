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
import { isAnchoredAssemblyType } from "./mini-toolbar.service";

// The rollout boundary, asserted explicitly rather than left implied. Each family slice moves types
// from the second test to the first; the last one deletes the predicate entirely and the gate
// becomes the only condition.
describe("isAnchoredAssemblyType", () => {
   it("anchors the chart pilot and the table family", () => {
      expect(isAnchoredAssemblyType("VSChart")).toBe(true);
      expect(isAnchoredAssemblyType("VSTable")).toBe(true);
      expect(isAnchoredAssemblyType("VSCrosstab")).toBe(true);
      expect(isAnchoredAssemblyType("VSCalcTable")).toBe(true);
   });

   it("matches case-insensitively, as the Tool.equalsIgnoreCase calls it replaces did", () => {
      expect(isAnchoredAssemblyType("vschart")).toBe(true);
      expect(isAnchoredAssemblyType("VSCHART")).toBe(true);
      expect(isAnchoredAssemblyType("vstable")).toBe(true);
   });

   it("does not anchor the types whose rollout slices have not landed", () => {
      expect(isAnchoredAssemblyType("VSCalendar")).toBe(false);
      expect(isAnchoredAssemblyType("VSSelectionList")).toBe(false);
      expect(isAnchoredAssemblyType("VSSelectionTree")).toBe(false);
      expect(isAnchoredAssemblyType("VSSelectionContainer")).toBe(false);
   });

   it("never anchors the range slider, which is excluded from the rollout permanently", () => {
      // Case 4: alone among the eight it declares no titleVisible, so it has no lane to anchor into.
      expect(isAnchoredAssemblyType("VSRangeSlider")).toBe(false);
   });

   it("does not throw on a missing objectType", () => {
      expect(isAnchoredAssemblyType(null)).toBe(false);
      expect(isAnchoredAssemblyType(undefined)).toBe(false);
      expect(isAnchoredAssemblyType("")).toBe(false);
   });
});
