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
import { FavoritesService } from "./favorites.service";

describe("FavoritesService", () => {
   let service: FavoritesService;
   let httpMock: HttpTestingController;

   beforeEach(() => {
      TestBed.configureTestingModule({
         imports: [HttpClientTestingModule],
         providers: [FavoritesService]
      });
      service = TestBed.inject(FavoritesService);
      httpMock = TestBed.inject(HttpTestingController);
      // The constructor fires the initial GET immediately — flush it in each test
      // to control the starting state (or call httpMock.expectOne() in each test body).
   });

   afterEach(() => {
      httpMock.verify();
   });

   it("should initialize favorites from API on first access", () => {
      const initialFavorites = [{ path: "/reports/sales", label: "Sales Report" }];
      let result: any;

      service.favorites.subscribe(favs => result = favs);

      const req = httpMock.expectOne("../api/em/favorites");
      expect(req.request.method).toBe("GET");
      req.flush({ favorites: initialFavorites });

      expect(result).toEqual(initialFavorites);
   });

   it("should add a new favorite", () => {
      const initialFavorites = [{ path: "/reports/sales", label: "Sales Report" }];
      httpMock.expectOne("../api/em/favorites").flush({ favorites: initialFavorites });

      service.addFavorite("/reports/revenue", "Revenue Report");

      const putReq = httpMock.expectOne("../api/em/favorites");
      expect(putReq.request.method).toBe("PUT");
      expect(putReq.request.body.favorites).toContainEqual({ path: "/reports/revenue", label: "Revenue Report" });
      expect(putReq.request.body.favorites).toContainEqual({ path: "/reports/sales", label: "Sales Report" });
      putReq.flush({});
   });

   it("should update the label of an existing favorite", () => {
      const initialFavorites = [{ path: "/reports/sales", label: "Old Label" }];
      httpMock.expectOne("../api/em/favorites").flush({ favorites: initialFavorites });

      service.addFavorite("/reports/sales", "New Label");

      const putReq = httpMock.expectOne("../api/em/favorites");
      const body = putReq.request.body.favorites;
      expect(body.length).toBe(1);
      expect(body[0].label).toBe("New Label");
      putReq.flush({});
   });

   it("should remove a favorite by path", () => {
      const initialFavorites = [
         { path: "/reports/sales", label: "Sales Report" },
         { path: "/reports/revenue", label: "Revenue Report" }
      ];
      httpMock.expectOne("../api/em/favorites").flush({ favorites: initialFavorites });

      service.removeFavorite("/reports/sales");

      const putReq = httpMock.expectOne("../api/em/favorites");
      expect(putReq.request.method).toBe("PUT");
      const body = putReq.request.body.favorites;
      expect(body.every((f: any) => f.path !== "/reports/sales")).toBe(true);
      expect(body).toContainEqual({ path: "/reports/revenue", label: "Revenue Report" });
      putReq.flush({});
   });

   it("should return true for isFavorite when path exists", () => {
      const initialFavorites = [{ path: "/reports/sales", label: "Sales Report" }];
      httpMock.expectOne("../api/em/favorites").flush({ favorites: initialFavorites });

      let result: boolean | undefined;
      service.isFavorite("/reports/sales").subscribe(r => result = r);

      expect(result).toBe(true);
   });

   it("should return false for isFavorite when path is absent", () => {
      const initialFavorites = [{ path: "/reports/sales", label: "Sales Report" }];
      httpMock.expectOne("../api/em/favorites").flush({ favorites: initialFavorites });

      let result: boolean | undefined;
      service.isFavorite("/reports/other").subscribe(r => result = r);

      expect(result).toBe(false);
   });
});
