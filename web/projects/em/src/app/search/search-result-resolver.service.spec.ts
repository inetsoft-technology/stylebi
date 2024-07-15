/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { inject, TestBed } from "@angular/core/testing";
import { of as observableOf } from "rxjs";
import { SearchResultResolver } from "./search-result-resolver.service";
import { SearchService } from "./search.service";

describe("SearchResultResolver", () => {
   beforeEach(() => {
      const service = { search: jest.fn(() => observableOf()) };
      TestBed.configureTestingModule({
         providers: [
            SearchResultResolver,
            { provide: SearchService, useValue: service }
         ]
      });
   });

   it("should be created", inject([SearchResultResolver], (service: SearchResultResolver) => {
      expect(service).toBeTruthy();
   }));
});
