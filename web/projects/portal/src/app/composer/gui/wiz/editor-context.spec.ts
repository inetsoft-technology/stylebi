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

import { dynamicValueLabel } from "./editor-context";

describe("dynamicValueLabel", () => {
   it("shows a bare name as the property key", () => {
      expect(dynamicValueLabel("visible")).toBe("visible");
      expect(dynamicValueLabel("rangeValues[0]")).toBe("rangeValues[0]");
   });

   it("shows a namespaced name as the namespace and the key", () => {
      expect(dynamicValueLabel("dateComparison:periodsBack")).toBe("dateComparison periodsBack");
      expect(dynamicValueLabel("source:parentIdColumn")).toBe("source parentIdColumn");
      expect(dynamicValueLabel("chartRegion:legend/0/title"))
         .toBe("chartRegion legend/0/title");
   });

   it("splits only at the first colon", () => {
      expect(dynamicValueLabel("binding:a[0].sort.sortByField"))
         .toBe("binding a[0].sort.sortByField");
   });

   it("tolerates a missing name", () => {
      expect(dynamicValueLabel(undefined)).toBe("");
      expect(dynamicValueLabel(null)).toBe("");
   });
});
