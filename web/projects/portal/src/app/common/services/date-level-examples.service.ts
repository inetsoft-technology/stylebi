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
import { Injectable } from "@angular/core";
import { HttpClient, HttpParams } from "@angular/common/http";
import { Observable } from "rxjs";

export const DATE_LEVEL_EXAMPLES_URI = "../api/date-level-examples";

@Injectable({
   providedIn: "root"
})
export class DateLevelExamplesService {
   constructor(private http: HttpClient) {
   }

   loadDateLevelExamples(dateLevels: string[], dataType: string): Observable<Object> {
      let params = new HttpParams().set("dataType", dataType);
      return this.http.post(DATE_LEVEL_EXAMPLES_URI, dateLevels, {params});
   }
}

