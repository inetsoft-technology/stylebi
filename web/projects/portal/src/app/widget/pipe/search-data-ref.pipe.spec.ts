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
import { SearchDataRefPipe } from "./search-data-ref.pipe";
import { DataRef } from "../../common/data/data-ref";

const ref = (view: string): DataRef => ({ view } as DataRef);

describe("SearchDataRefPipe", () => {
   let pipe: SearchDataRefPipe;

   beforeEach(() => {
      pipe = new SearchDataRefPipe();
   });

   it("should return all refs when input is empty", () => {
      const refs = [ref("City"), ref("State"), ref("Country")];
      expect(pipe.transform(refs, "")).toEqual(refs);
   });

   it("should return all refs when input is null", () => {
      const refs = [ref("City"), ref("State")];
      expect(pipe.transform(refs, null)).toEqual(refs);
   });

   it("should filter refs by case-insensitive match", () => {
      const refs = [ref("City"), ref("State"), ref("Country")];
      expect(pipe.transform(refs, "city")).toEqual([ref("City")]);
   });

   it("should match partial strings", () => {
      const refs = [ref("OrderDate"), ref("OrderID"), ref("Product")];
      const result = pipe.transform(refs, "order");
      expect(result.length).toBe(2);
      expect(result.map(r => r.view)).toContain("OrderDate");
      expect(result.map(r => r.view)).toContain("OrderID");
   });

   it("should return empty array when nothing matches", () => {
      const refs = [ref("City"), ref("State")];
      expect(pipe.transform(refs, "xyz")).toEqual([]);
   });

   it("should be case-insensitive for uppercase input", () => {
      const refs = [ref("region"), ref("state")];
      expect(pipe.transform(refs, "REGION")).toEqual([ref("region")]);
   });
});
