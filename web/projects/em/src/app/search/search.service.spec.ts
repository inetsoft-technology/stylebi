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
import { HttpClientTestingModule, HttpTestingController } from "@angular/common/http/testing";
import { TestBed } from "@angular/core/testing";
import { SearchService } from "./search.service";

describe("SearchService", () => {
   let service: SearchService;
   let httpMock: HttpTestingController;

   beforeEach(() => {
      TestBed.configureTestingModule({
         imports: [HttpClientTestingModule],
         providers: [SearchService]
      });
      service = TestBed.inject(SearchService);
      httpMock = TestBed.inject(HttpTestingController);
   });

   afterEach(() => {
      httpMock.verify();
   });

   it("should call the correct endpoint with a query parameter", () => {
      service.search("revenue").subscribe();

      const req = httpMock.expectOne(r => r.url === "../api/em/search");
      expect(req.request.method).toBe("GET");
      expect(req.request.params.get("q")).toBe("revenue");
      req.flush({ results: [] });
   });

   it("should extract the results array from the response wrapper", () => {
      const mockResults = [
         { path: "/reports/sales", label: "Sales Report", type: "Viewsheet" },
         { path: "/reports/revenue", label: "Revenue Report", type: "Viewsheet" }
      ];
      let actual: any;

      service.search("revenue").subscribe(r => actual = r);

      httpMock.expectOne(r => r.url === "../api/em/search").flush({ results: mockResults });

      expect(actual).toEqual(mockResults);
   });

   it("should return an empty array when the API returns empty results", () => {
      let actual: any;

      service.search("notfound").subscribe(r => actual = r);

      httpMock.expectOne(r => r.url === "../api/em/search").flush({ results: [] });

      expect(actual).toEqual([]);
   });
});
