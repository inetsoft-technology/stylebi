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
import { HttpClient } from "@angular/common/http";
import { Injectable } from "@angular/core";
import { Observable } from "rxjs";
import { shareReplay } from "rxjs/operators";
import { CurrentUser } from "../../portal/src/app/portal/current-user";

@Injectable({
   providedIn: "root"
})
export class CurrentUserService {
   private emCurrentUser$: Observable<CurrentUser> =
      this.http.get<CurrentUser>("../api/em/security/get-current-user").pipe(
         shareReplay({ bufferSize: 1, refCount: true })
      );

   private portalCurrentUser$: Observable<CurrentUser> =
      this.http.get<CurrentUser>("../api/portal/get-current-user").pipe(
         shareReplay({ bufferSize: 1, refCount: true })
      );

   constructor(private http: HttpClient) {}

   getEmCurrentUser(): Observable<CurrentUser> {
      return this.emCurrentUser$;
   }

   getPortalCurrentUser(): Observable<CurrentUser> {
      return this.portalCurrentUser$;
   }
}
