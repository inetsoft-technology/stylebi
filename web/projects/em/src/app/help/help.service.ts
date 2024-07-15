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
import { HttpClient } from "@angular/common/http";
import { Injectable } from "@angular/core";
import { Observable, of as observableOf } from "rxjs";
import { HelpLinks } from "./help-links";
import { tap } from "rxjs/operators";

@Injectable({
   providedIn: "root"
})
export class HelpService {
   private _helpUrl: string;

   constructor(private http: HttpClient) {
   }

   getHelpLinks(): Observable<HelpLinks> {
      return this.http.get<HelpLinks>("../api/em/help-links");
   }

   getHelpUrl(): Observable<string> {
      if(!!this._helpUrl) {
         return observableOf(this._helpUrl);
      }

      return this.http.get<string>("../api/em/help-url").pipe(
         tap((data) => this._helpUrl = data)
      );
   }
}
