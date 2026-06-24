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
 * along with this program, if not, see <https://www.gnu.org/licenses/>.
 */
import { VSSelection } from "./vs-selection.component";

// trackByIdx is a pure method — test it directly on the prototype.
const trackByIdx = VSSelection.prototype.trackByIdx.bind({});

describe("VSSelection.trackByIdx", () => {
   describe("performance - CD count", () => {
      it("should return a stable value key for a SelectionValueModel item", () => {
         const item = { value: "California", label: "California", state: 1 };
         expect(trackByIdx(0, item)).toBe("California");
         expect(trackByIdx(3, item)).toBe("California");
      });

      it("should return the same key regardless of position index", () => {
         const item = { value: "East", label: "East", state: 2 };
         const key0 = trackByIdx(0, item);
         const key7 = trackByIdx(7, item);
         expect(key0).toBe(key7);
      });

      it("should use item[0].value for an array row (outer loop)", () => {
         const row = [
            { value: "North", label: "North", state: 0 },
            { value: "South", label: "South", state: 0 },
         ];
         expect(trackByIdx(0, row)).toBe("North");
         expect(trackByIdx(5, row)).toBe("North");
      });

      it("should fall back to index for null item", () => {
         expect(trackByIdx(4, null)).toBe(4);
      });

      it("should fall back to index for undefined item", () => {
         expect(trackByIdx(2, undefined)).toBe(2);
      });

      it("should fall back to index for item with no value property", () => {
         expect(trackByIdx(1, {})).toBe(1);
      });

      it("should fall back to index for array row whose first element is null", () => {
         expect(trackByIdx(3, [null])).toBe(3);
      });

      it("should fall back to index for item with null value property", () => {
         expect(trackByIdx(2, { value: null, label: "Something" })).toBe(2);
      });

      it("should return empty string as the key when value is empty string", () => {
         expect(trackByIdx(0, { value: "", label: "Blank" })).toBe("");
      });

      it("should return different keys for items with different values", () => {
         const itemA = { value: "Alpha", label: "Alpha", state: 0 };
         const itemB = { value: "Beta", label: "Beta", state: 0 };
         expect(trackByIdx(0, itemA)).not.toBe(trackByIdx(0, itemB));
      });
   });
});
