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
import { HttpClient, HttpParams } from "@angular/common/http";
import { Injectable } from "@angular/core";
import { Observable } from "rxjs";
import { map } from "rxjs/operators";
import { SearchResult, SearchResultList } from "./search-result";

@Injectable({
   providedIn: "root"
})
export class SearchService {
   constructor(private http: HttpClient) {
   }

   search(query: string): Observable<SearchResult[]> {
      const uri = "../api/em/search";
      const params = new HttpParams().set("q", query);
      const options = {params};
      return this.http.get<SearchResultList>(uri, options).pipe(
         map(list => list.results)
      );
   }
}
