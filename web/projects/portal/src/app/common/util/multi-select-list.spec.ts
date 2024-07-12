/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { Range } from "../data/range";
import { MultiSelectList } from "./multi-select-list";

describe("MultiSelectList", () => {
   let emptyList: MultiSelectList;
   let populatedList: MultiSelectList;
   const populatedListSize = 5;

   beforeEach(() => {
      emptyList = new MultiSelectList();
      populatedList = new MultiSelectList(populatedListSize);
   });

   it("List sizes", () => {
      expect(emptyList.size()).toBe(0);
      expect(populatedList.size()).toBe(populatedListSize);
   });

   it("select a single item", () => {
      populatedList.select(0);
      expect(populatedList.isSelected(0)).toBeTruthy();
   });

   it("hasSelection", () => {
      expect(populatedList.hasSelection()).toBeFalsy();
      populatedList.select(0);
      expect(populatedList.hasSelection()).toBeTruthy();
   });

   it("modifying operation with index out of bounds throws error", () => {
      expect(() => populatedList.select(-1)).toThrow();
      expect(() => populatedList.ctrlSelect(-1)).toThrow();
      expect(() => populatedList.shiftSelect(-1)).toThrow();
      expect(() => populatedList.deselect(-1)).toThrow();

      expect(() => populatedList.select(populatedListSize)).toThrow();
      expect(() => populatedList.ctrlSelect(populatedListSize)).toThrow();
      expect(() => populatedList.shiftSelect(populatedListSize)).toThrow();
      expect(() => populatedList.deselect(populatedListSize)).toThrow();
   });

   it("clearing list", () => {
      populatedList.select(0);
      expect(populatedList.isSelected(0)).toBeTruthy();

      populatedList.clear();
      expect(populatedList.isSelected(0)).toBeFalsy();
   });

   it("ctrl select", () => {
      populatedList.ctrlSelect(0);
      expect(populatedList.getSelectedIndices()).toEqual([0]);
      populatedList.ctrlSelect(1);
      expect(populatedList.getSelectedIndices()).toEqual([0, 1]);

      populatedList.ctrlSelect(0);
      expect(populatedList.getSelectedIndices()).toEqual([1]);
   });

   it("getSelectedIndices", () => {
      populatedList.select(1);
      populatedList.shiftSelect(3);
      expect(populatedList.getSelectedIndices()).toEqual([1, 2, 3]);
   });

   it("shift select", () => {
      populatedList.shiftSelect(2);
      expect(populatedList.isSelected(2)).toBeTruthy();

      populatedList.shiftSelect(0);
      expect(populatedList.getSelectedIndices()).toEqual([0, 1, 2]);

      populatedList.shiftSelect(4);
      expect(populatedList.getSelectedIndices()).toEqual([2, 3, 4]);

      populatedList.ctrlSelect(2);
      populatedList.shiftSelect(0);
      expect(populatedList.getSelectedIndices()).toEqual([0]);
   });

   it("setSize", () => {
      populatedList.select(0);
      populatedList.shiftSelect(4);
      const newSize = populatedListSize * 2;

      populatedList.setSize(newSize);
      expect(populatedList.size()).toBe(newSize);
      expect(populatedList.hasSelection()).toBeFalsy();
   });

   it("negative setSize throws", () => {
      expect(() => populatedList.setSize(-1)).toThrow();
   });

   it("select with basic mouse event", () => {
      const event = new MouseEvent("");

      populatedList.select(0);
      populatedList.selectWithEvent(2, event);
      expect(populatedList.getSelectedIndices()).toEqual([2]);
   });

   it("select with ctrl mouse event", () => {
      const event = new MouseEvent("", {ctrlKey: true});

      populatedList.select(0);
      populatedList.selectWithEvent(2, event);
      expect(populatedList.getSelectedIndices()).toEqual([0, 2]);

      populatedList.selectWithEvent(2, event);
      expect(populatedList.getSelectedIndices()).toEqual([0]);
   });

   it("select with shift mouse event", () => {
      const event = new MouseEvent("", {shiftKey: true});

      populatedList.selectWithEvent(0, event);
      expect(populatedList.getSelectedIndices()).toEqual([0]);
      populatedList.selectWithEvent(2, event);
      expect(populatedList.getSelectedIndices()).toEqual([0, 1, 2]);
   });

   it("select range", () => {
      const basicRange = new Range(1, 3);

      populatedList.selectRange(basicRange);
      expect(populatedList.getSelectedIndices()).toEqual([1, 2, 3]);
      populatedList.shiftSelect(4);
      expect(populatedList.getSelectedIndices()).toEqual([1, 2, 3, 4]);
   });
});