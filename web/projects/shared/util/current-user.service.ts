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
import { Observable, Subject } from "rxjs";
import { shareReplay, startWith, switchMap } from "rxjs/operators";
import { CurrentUser } from "../../portal/src/app/portal/current-user";

@Injectable({
   providedIn: "root"
})
export class CurrentUserService {
   private emReload$ = new Subject<void>();
   private portalReload$ = new Subject<void>();

   private emCurrentUser$: Observable<CurrentUser> = this.emReload$.pipe(
      startWith(undefined as void),
      switchMap(() => this.http.get<CurrentUser>("../api/em/security/get-current-user")),
      shareReplay({ bufferSize: 1, refCount: false })
   );

   private portalCurrentUser$: Observable<CurrentUser> = this.portalReload$.pipe(
      startWith(undefined as void),
      switchMap(() => this.http.get<CurrentUser>("../api/portal/get-current-user")),
      shareReplay({ bufferSize: 1, refCount: false })
   );

   constructor(private http: HttpClient) {}

   getEmCurrentUser(): Observable<CurrentUser> {
      return this.emCurrentUser$;
   }

   getPortalCurrentUser(): Observable<CurrentUser> {
      return this.portalCurrentUser$;
   }

   reloadEmCurrentUser(): void {
      this.emReload$.next();
   }

   reloadPortalCurrentUser(): void {
      this.portalReload$.next();
   }
}
