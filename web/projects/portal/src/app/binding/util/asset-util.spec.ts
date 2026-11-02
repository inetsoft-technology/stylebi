/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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
import { AssetUtil } from "./asset-util";

describe("AssetUtil.isMergeable", () => {
   // [physical table type, inline view type]: the two producers use different vocabularies
   it.each([
      ["Double", "bigdecimal"],
      ["Double", "double"],
      ["Date", "timeInstant"],
      ["TimeInstant", "timeInstant"],
      ["Integer", "integer"],
      ["Long", "long"],
      ["Integer", "biginteger"],
      ["String", "string"],
      ["Date", "date"],
      ["Time", "time"],
      ["Date", "time"],
   ])("accepts %s with %s in both orders", (a, b) => {
      expect(AssetUtil.isMergeable(a, b)).toBe(true);
      expect(AssetUtil.isMergeable(b, a)).toBe(true);
   });

   it.each([
      ["String", "bigdecimal"],
      ["String", "Integer"],
      ["String", "timeInstant"],
      ["Double", "timeInstant"],
      ["bigdecimal", "date"],
      ["Integer", "Date"],
      ["Boolean", "string"],
      ["Boolean", "integer"],
   ])("rejects %s with %s in both orders", (a, b) => {
      expect(AssetUtil.isMergeable(a, b)).toBe(false);
      expect(AssetUtil.isMergeable(b, a)).toBe(false);
   });

   it("handles null types without throwing", () => {
      expect(AssetUtil.isMergeable(null, "string")).toBe(false);
      expect(AssetUtil.isMergeable(null, null)).toBe(false);
   });
});

describe("AssetUtil type predicates", () => {
   it("recognises timeInstant in any casing as a date type", () => {
      expect(AssetUtil.isDateType("timeInstant")).toBe(true);
      expect(AssetUtil.isDateType("TimeInstant")).toBe(true);
      expect(AssetUtil.isDateType("Date")).toBe(true);
      expect(AssetUtil.isDateType("string")).toBe(false);
   });

   it("recognises bigdecimal and biginteger as number types", () => {
      expect(AssetUtil.isNumberType("bigdecimal")).toBe(true);
      expect(AssetUtil.isNumberType("BigInteger")).toBe(true);
      expect(AssetUtil.isNumberType("string")).toBe(false);
   });
});
