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
import { LogViewLinksService } from "./log-view-links.service";

const URL = "../api/em/monitoring/log/links";

describe("LogViewLinksService", () => {
   let service: LogViewLinksService;
   let http: HttpTestingController;

   beforeEach(() => {
      TestBed.configureTestingModule({imports: [HttpClientTestingModule]});
      service = TestBed.inject(LogViewLinksService);
      http = TestBed.inject(HttpTestingController);
   });

   it("hands the links fetched by the route guard to the page without a second request", () => {
      const links = {fluentdLogging: false, logViewUrl: null};
      let guardResult: any;
      let pageResult: any;

      service.fetchLinks().subscribe(l => guardResult = l);
      http.expectOne(URL).flush(links);
      service.getLinks().subscribe(l => pageResult = l);

      http.expectNone(URL);
      expect(guardResult).toEqual(links);
      expect(pageResult).toEqual(links);
      http.verify();
   });

   it("requests the links itself when the guard did not fetch them, and only hands over once", () => {
      const links = {fluentdLogging: true, logViewUrl: "http://logs"};
      let result: any;

      service.fetchLinks().subscribe();
      http.expectOne(URL).flush(links);
      service.getLinks().subscribe();
      service.getLinks().subscribe(l => result = l);

      http.expectOne(URL).flush(links);
      expect(result).toEqual(links);
      http.verify();
   });
});
