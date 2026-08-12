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

   // The key is `listings`. Captured from a running server, which returns 130 of them, each with
   // name/category/iconUrl/keywords. An earlier guess of `dataSourceListings` type-checked, passed
   // its own test, and would have rendered an empty picker against every real deployment.
   it("reads the type picker from the selection-view endpoint", () => {
      const seen: any[] = [];
      service.listings().subscribe((l) => seen.push(...l));

      const req = http.expectOne("../api/portal/data/datasource-selection-view");
      expect(req.request.method).toBe("GET");
      req.flush({
         listings: [
            { name: "Apache Cassandra", category: "Big Data", iconUrl: "/i/c.svg", keywords: [] },
            { name: "MongoDB", category: "Big Data", iconUrl: "/i/m.svg", keywords: [] },
         ],
         categories: ["Big Data"],
      });

      expect(seen.map((l) => l.name)).toEqual(["Apache Cassandra", "MongoDB"]);
      expect(seen[0].category).toBe("Big Data");
   });

   it("passes an empty listings array through -- that is a legitimate answer", () => {
      const seen: any[] = [];
      let errored = false;
      service.listings().subscribe({ next: (l) => seen.push(...l), error: () => errored = true });
      http.expectOne("../api/portal/data/datasource-selection-view").flush({ listings: [] });

      expect(seen).toEqual([]);
      expect(errored).toBe(false);
   });

   // Fail loud rather than render an empty picker: an absent key is a moved contract, and silently
   // showing "no types available" makes a broken deployment look like an empty one.
   it("errors when the payload has no listings key at all", () => {
      let message = "";
      service.listings().subscribe({ error: (e) => message = e.message });
      http.expectOne("../api/portal/data/datasource-selection-view").flush({});

      expect(message).toContain("listings");
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

   // The key is `dataSourceList` -- again captured from a running server, not guessed.
   it("lists existing names so the editor can reject a duplicate", () => {
      let names: string[] = [];
      service.existingNames().subscribe((n) => (names = n));

      const req = http.expectOne("../api/data/datasources/browser");
      req.flush({
         dataSourceList: [
            { name: "inventree", path: "inventree", type: { name: "DATABASE", label: "Database" } },
            { name: "sakila", path: "sakila", type: { name: "DATABASE", label: "Database" } },
         ],
      });

      expect(names).toEqual(["inventree", "sakila"]);
   });

   it("errors when the browser payload has no dataSourceList key", () => {
      let message = "";
      service.existingNames().subscribe({ error: (e) => message = e.message });
      http.expectOne("../api/data/datasources/browser").flush({});

      expect(message).toContain("dataSourceList");
   });
});
