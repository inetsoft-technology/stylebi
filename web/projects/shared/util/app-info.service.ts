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
import { BehaviorSubject, Observable } from "rxjs";
import { filter, shareReplay } from "rxjs/operators";
import { Injectable, OnDestroy } from "@angular/core";
import { HttpClient } from "@angular/common/http";
import { IdentityId } from "../../em/src/app/settings/security/users/identity-id";
import { CommonKVModel } from "../../portal/src/app/common/data/common-kv-model";

@Injectable({
   providedIn: "root"
})
export class AppInfoService implements OnDestroy {
   private ldapProviderUsed = new BehaviorSubject<boolean>(false);
   currentOrgInfo = new BehaviorSubject<CommonKVModel<string, string>>(null);
   private readonly enterprise$ = this.httpClient.get<boolean>("../api/enterprise").pipe(
      shareReplay({ bufferSize: 1, refCount: true })
   );

   constructor(private httpClient: HttpClient) {
      this.loadCurrentOrgInfo().subscribe((orgInfo) => {
         this.currentOrgInfo.next(orgInfo)
      });
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
    * currentOrgInfo is seeded with null and only updated once the async
    * "../api/org/info" request resolves. Filter out that seed so a subscriber can't
    * mistake "not loaded yet" for a real, empty org-info value; a subscriber that needs to
    * wait for the real value (e.g. via take(1)) gets it, instead of racing ahead on null.
    */
   getCurrentOrgInfo(): Observable<CommonKVModel<string, string>> {
      return this.currentOrgInfo.pipe(filter(orgInfo => orgInfo != null));
   }

   getAllOrgnanizations(): Observable<string[]> {
      return this.httpClient.get<string[]>("../api/organizations");
   }

   ngOnDestroy() {
      this.currentOrgInfo.complete();
   }
}
