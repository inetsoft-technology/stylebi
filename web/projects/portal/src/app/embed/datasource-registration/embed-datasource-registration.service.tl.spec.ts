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

import { TestBed } from "@angular/core/testing";
import { provideHttpClient } from "@angular/common/http";
import { provideHttpClientTesting, HttpTestingController } from "@angular/common/http/testing";
import { EmbedDatasourceRegistrationService } from "./embed-datasource-registration.service";

describe("EmbedDatasourceRegistrationService", () => {
   let service: EmbedDatasourceRegistrationService;
   let http: HttpTestingController;

   beforeEach(() => {
      TestBed.configureTestingModule({
         providers: [
            EmbedDatasourceRegistrationService,
            provideHttpClient(),
            provideHttpClientTesting(),
         ],
      });
      service = TestBed.inject(EmbedDatasourceRegistrationService);
      http = TestBed.inject(HttpTestingController);
   });

   afterEach(() => http.verify());

   it("reads the type picker from the selection-view endpoint", () => {
      const seen: any[] = [];
      service.listings().subscribe((l) => seen.push(...l));

      const req = http.expectOne("../api/portal/data/datasource-selection-view");
      expect(req.request.method).toBe("GET");
      req.flush({ dataSourceListings: [{ name: "Apache Cassandra" }, { name: "MongoDB" }] });

      expect(seen.map((l) => l.name)).toEqual(["Apache Cassandra", "MongoDB"]);
   });

   it("tolerates a selection-view payload with no listings rather than throwing", () => {
      const seen: any[] = [];
      service.listings().subscribe((l) => seen.push(...l));
      http.expectOne("../api/portal/data/datasource-selection-view").flush({});
      expect(seen).toEqual([]);
   });

   // The seed path is listing/{name}. refreshView returns tabularView:null for a NEW datasource --
   // it refreshes an existing view rather than creating one -- so seeding from it renders an empty
   // form. This was measured against a live deployment, not assumed.
   it("seeds a new datasource from listing/{name}, URL-encoding the name", () => {
      let got: any = null;
      service.seedFromListing("Apache Cassandra").subscribe((d) => (got = d));

      const req = http.expectOne("../api/portal/data/datasources/listing/Apache%20Cassandra");
      expect(req.request.method).toBe("GET");
      req.flush({ name: "", type: "Cassandra", tabularView: { views: [] } });

      expect(got.type).toBe("Cassandra");
   });

   it("posts the whole definition to refreshView for dependent-field refresh", () => {
      const ds = { name: "x", type: "Cassandra", tabularView: { views: [] } } as any;
      service.refreshView(ds).subscribe();

      const req = http.expectOne("../api/portal/data/datasources/refreshView");
      expect(req.request.method).toBe("POST");
      expect(req.request.body).toEqual(ds);
      req.flush(ds);
   });

   it("saves a NEW datasource with POST to the collection", () => {
      const ds = { name: "new-one", type: "Cassandra" } as any;
      service.save(ds).subscribe();

      const req = http.expectOne("../api/portal/data/datasources");
      expect(req.request.method).toBe("POST");
      req.flush({});
   });

   it("lists existing names so the editor can reject a duplicate", () => {
      let names: string[] = [];
      service.existingNames().subscribe((n) => (names = n));

      const req = http.expectOne("../api/data/datasources/browser");
      req.flush({ files: [{ name: "olist" }, { name: "sakila" }] });

      expect(names).toEqual(["olist", "sakila"]);
   });
});
