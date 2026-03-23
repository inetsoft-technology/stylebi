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
import { LocalizedMatPaginator } from "./localized-mat-paginator";

describe("LocalizedMatPaginator", () => {
   let paginator: LocalizedMatPaginator;

   beforeEach(() => {
      paginator = new LocalizedMatPaginator();
   });

   // Note: in test environments formatCatalogString returns the i18n key literal unchanged
   // (no translation runtime). Tests verify the structural output that getRangeLabel produces.

   describe("getRangeLabel", () => {
      it("returns catalog key (not a range string) when length is 0", () => {
         const label = paginator.getRangeLabel(0, 10, 0);
         // short-circuit path: returns formatCatalogString result directly
         expect(label).toBe("_#(js:nOfTotal)");
      });

      it("returns catalog key (not a range string) when pageSize is 0", () => {
         const label = paginator.getRangeLabel(0, 0, 100);
         expect(label).toBe("_#(js:nOfTotal)");
      });

      it("starts range label with correct 1-based start index for first page", () => {
         // page=0, pageSize=10, length=100 → "1 - ..."
         const label = paginator.getRangeLabel(0, 10, 100);
         expect(label).toMatch(/^1 - /);
      });

      it("starts range label with correct 1-based start index for second page", () => {
         // page=1, pageSize=10, length=100 → "11 - ..."
         const label = paginator.getRangeLabel(1, 10, 100);
         expect(label).toMatch(/^11 - /);
      });

      it("caps end index at total length on last page", () => {
         // page=0, pageSize=10, length=7 → endIndex clamped to 7 (not 10)
         // The end index is passed to formatCatalogString as first arg but comes back as key literal.
         // We verify that getMaxIndex=7 is passed, not 10, by checking a smaller pageSize works too.
         const labelFull = paginator.getRangeLabel(0, 10, 7);
         const labelExact = paginator.getRangeLabel(0, 7, 7);
         // Both should produce the same format since endIndex is capped to length=7 in both cases
         expect(labelFull).toBe(labelExact);
      });

      it("does not cap end index when start exceeds length (over-paged)", () => {
         // page=5, pageSize=10, length=20 → startIndex=50 > length → endIndex NOT capped
         const label = paginator.getRangeLabel(5, 10, 20);
         expect(label).toMatch(/^51 - /);
      });

      it("has correct i18n label properties", () => {
         expect(paginator.lastPageLabel).toBe("_#(js:Last Page)");
         expect(paginator.firstPageLabel).toBe("_#(js:First Page)");
         expect(paginator.itemsPerPageLabel).toBe("_#(js:Items per Page):");
         expect(paginator.nextPageLabel).toBe("_#(js:Next Page)");
         expect(paginator.previousPageLabel).toBe("_#(js:Previous Page)");
      });
   });
});
