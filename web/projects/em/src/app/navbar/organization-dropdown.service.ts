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
import { Injectable, OnDestroy } from "@angular/core";
import { Subject, Subscription } from "rxjs";
import { map } from "rxjs/operators";
import { CurrentUser } from "../../../../portal/src/app/portal/current-user";
import { StompClientConnection } from "../../../../shared/stomp/stomp-client-connection";
import { StompClientService } from "../../../../shared/stomp/stomp-client.service";
import {
   SecurityProviderStatus,
   SecurityProviderStatusList
} from "../settings/security/security-provider/security-provider-model/security-provider-status-list";

@Injectable({
   providedIn: "root"
})
export class OrganizationDropdownService implements OnDestroy  {
   private provider: string;
   private refreshSubject: Subject<any>;
   private connection: StompClientConnection;
   private subscription = new Subscription();
   public authenticationProviders: string[];
   currentUser: CurrentUser;

   constructor(private http: HttpClient, private stompClient: StompClientService) {
      this.refreshSubject = new Subject<any>();

      this.stompClient.connect("../vs-events", true).subscribe(connection => {
         this.connection = connection;

         this.subscription.add(connection.subscribe("/user/security/providers-change",
            (message) => this.loadAuthenticationProviders()));
      });

      this.http.get<CurrentUser>("../api/em/security/get-current-user").subscribe(userModel => {
         this.currentUser = userModel;
         this.loadAuthenticationProviders();
      });
   }

   get loginUserName(): string {
      return this.currentUser?.name?.name;
   }

   get loginUserOrgID(): string {
      return this.currentUser?.name?.orgID;
   }

   get loginUserOrgName(): string {
      return this.currentUser?.org;
   }

   isSystemAdmin(): boolean {
      return this.currentUser?.isSysAdmin;
   }

   private loadAuthenticationProviders(): void {
      this.http.get<SecurityProviderStatusList>("../api/em/security/configured-authentication-providers")
         .pipe(
            map((list: SecurityProviderStatusList) => list.providers.map(p => p.name))
         )
         .subscribe((providers: string[]) => {
            this.authenticationProviders = providers;

            if(providers && providers.length > 0) {
               const provider = this.provider;

               if(provider == null && !this.isSystemAdmin()) {
                  this.http.get<SecurityProviderStatus>("../api/em/security/get-current-authentication-provider")
                     .subscribe(securityProvider => {
                        let currprovider = securityProvider != null ? securityProvider.name : "";

                        if(providers.includes(currprovider)) {
                           this.refresh(currprovider, false);
                        }
                        else {
                           this.refresh(providers[0], false);
                        }
                     });
               }
               else {
                  if(!provider) {
                     this.refresh(providers[0], false);
                  }
                  else if(providers.includes(provider)) {
                     this.refresh(provider, false);
                  }
                  else {
                     this.refresh(providers[0], true);
                  }
               }
            }
         });
   }

   public get onRefresh(): Subject<any> {
      return this.refreshSubject;
   }

   refreshProviders(): void {
      this.loadAuthenticationProviders();
   }

   public refresh(provider?: string, providerChanged?: boolean) {
      this.provider = provider;
      this.refreshSubject.next({provider : provider, providerChanged: providerChanged});
   }

   public setProvider(providerName: string): void {
      this.provider = providerName;
   }

   public getProvider(): string {
      return this.provider;
   }

   ngOnDestroy(): void {
      if(!!this.refreshSubject) {
         this.refreshSubject.unsubscribe();
         this.refreshSubject = null;
      }
   }
}