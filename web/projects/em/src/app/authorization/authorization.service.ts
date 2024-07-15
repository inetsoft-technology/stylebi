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
import { HttpClient, HttpParams } from "@angular/common/http";
import { Injectable } from "@angular/core";
import { Observable, of as observableOf } from "rxjs";
import { tap } from "rxjs/operators";
import { ComponentPermissions } from "./component-permissions";

@Injectable({
   providedIn: "root"
})
export class AuthorizationService {
   private cache = new Map<String, ComponentPermissions>();

   constructor(private http: HttpClient) {
   }

   /**
    * Gets the permissions for all the child components for the component at the specified path.
    *
    * @param path the path to the component. For most components, this is the same as the
    *        router path.
    * @param useCache a flag indicating whether to use a cached value if available (true) or to
    *        force fetching a fresh value from the server.
    *
    * @returns the permissions.
    */
   getPermissions(path: string, useCache: boolean = true): Observable<ComponentPermissions> {
      if(!path) {
         path = "";
      }
      else if(path.length > 0 && path[0] === "/") {
         path = path.substring(1);
      }

      if(useCache && this.cache.has(path)) {
         return observableOf(this.cache.get(path));
      }

      const uri = `../api/em/authz`;
      const params = new HttpParams().set("path", path);
      return this.http.get<ComponentPermissions>(uri, {params}).pipe(
         tap(permissions => this.cache.set(path, permissions))
      );
   }
}
