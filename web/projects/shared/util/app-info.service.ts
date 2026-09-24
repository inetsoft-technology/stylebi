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
import { BehaviorSubject, Observable, of } from "rxjs";
import { catchError, shareReplay } from "rxjs/operators";
import { Injectable } from "@angular/core";
import { HttpClient } from "@angular/common/http";
import { IdentityId } from "../../em/src/app/settings/security/users/identity-id";
import { CommonKVModel } from "../../portal/src/app/common/data/common-kv-model";

@Injectable({
   providedIn: "root"
})
export class AppInfoService {
   /**
    * Returned by getCurrentOrgInfo() if "../api/org/info" fails (network error, session expiry,
    * 5xx, etc.), so the stream still emits a non-null value instead of erroring out - a
    * subscriber waiting for the value (e.g. via take(1) on getCurrentOrgInfo()) would otherwise
    * never receive one. key/value are null so truthy checks on the key (e.g. ShareService's
    * sharedGlobal) still safely fall back to "unknown org".
    */
   static readonly UNKNOWN_ORG_INFO: CommonKVModel<string, string> = { key: null, value: null };

   private ldapProviderUsed = new BehaviorSubject<boolean>(false);
   private readonly enterprise$ = this.httpClient.get<boolean>("../api/enterprise").pipe(
      shareReplay({ bufferSize: 1, refCount: true })
   );

   constructor(private httpClient: HttpClient) {
   }

   isEnterprise(): Observable<boolean> {
      return this.enterprise$;
   }

   isLdapProviderUsed(): Observable<boolean> {
      return this.ldapProviderUsed.asObservable();
   }

   setLdapProviderUsed(value: boolean): void {
      this.ldapProviderUsed.next(value);
   }

   loadCurrentOrgInfo(): Observable<CommonKVModel<string, string>> {
      return this.httpClient.get<CommonKVModel<string, string>>("../api/org/info");
   }

   /**
    * Issues a fresh "../api/org/info" request on every subscription rather than caching the
    * result for the service's lifetime, so callers always see the org the session is currently
    * in instead of whatever it was the first time this was ever called (bug #76996). Guaranteed
    * to eventually emit - never wedges forever - since the request failure path is caught and
    * falls back to UNKNOWN_ORG_INFO.
    */
   getCurrentOrgInfo(): Observable<CommonKVModel<string, string>> {
      return this.loadCurrentOrgInfo().pipe(
         catchError(() => of(AppInfoService.UNKNOWN_ORG_INFO))
      );
   }

   getAllOrgnanizations(): Observable<string[]> {
      return this.httpClient.get<string[]>("../api/organizations");
   }
}
