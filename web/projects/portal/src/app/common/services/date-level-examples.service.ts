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
import { Injectable } from "@angular/core";
import { HttpClient, HttpParams } from "@angular/common/http";
import { Observable } from "rxjs";
import { shareReplay, tap } from "rxjs/operators";

export const DATE_LEVEL_EXAMPLES_URI = "../api/date-level-examples";

@Injectable({
   providedIn: "root"
})
export class DateLevelExamplesService {
   // Cached by (dataType, dateLevels) since the result is a pure function of those inputs.
   // Prevents duplicate/uncancelled requests piling up when a component re-issues the same
   // request every time it is (re)created, e.g. on every dropdown/dialog open.
   private readonly cache = new Map<string, Observable<Object>>();

   constructor(private http: HttpClient) {
   }

   loadDateLevelExamples(dateLevels: string[], dataType: string): Observable<Object> {
      const key = dataType + ":" + dateLevels.join(",");
      let cached = this.cache.get(key);

      if(!cached) {
         let params = new HttpParams().set("dataType", dataType);
         cached = this.http.post(DATE_LEVEL_EXAMPLES_URI, dateLevels, {params})
            .pipe(
               // don't let a transient failure be cached forever; allow retry on next call
               tap({ error: () => this.cache.delete(key) }),
               shareReplay(1)
            );
         this.cache.set(key, cached);
      }

      return cached;
   }
}

