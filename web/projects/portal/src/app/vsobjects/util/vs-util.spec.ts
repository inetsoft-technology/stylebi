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
import { VSUtil } from "./vs-util";

describe("VSUtil.computeBottomTabSelectionTop", () => {
   const tabTop = 500;
   const titleHeight = 20;
   const bodyHeight = 100;

   it("collapsed without search bar: shifts up by titleHeight only", () => {
      expect(VSUtil.computeBottomTabSelectionTop(tabTop, titleHeight, false, bodyHeight, false))
         .toBe(tabTop - titleHeight);
   });

   it("collapsed with search bar: shifts up by titleHeight + searchBar so search clears the tab strip", () => {
      expect(VSUtil.computeBottomTabSelectionTop(tabTop, titleHeight, false, bodyHeight, true))
         .toBe(tabTop - titleHeight - titleHeight);
   });

   it("expanded without search bar: shifts up by titleHeight + bodyHeight", () => {
      expect(VSUtil.computeBottomTabSelectionTop(tabTop, titleHeight, true, bodyHeight, false))
         .toBe(tabTop - titleHeight - bodyHeight);
   });

   it("expanded with search bar: shifts up by titleHeight + bodyHeight + searchBar", () => {
      expect(VSUtil.computeBottomTabSelectionTop(tabTop, titleHeight, true, bodyHeight, true))
         .toBe(tabTop - titleHeight - bodyHeight - titleHeight);
   });
});
