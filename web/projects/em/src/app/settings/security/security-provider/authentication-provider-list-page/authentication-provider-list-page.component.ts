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
import {HttpClient, HttpErrorResponse} from "@angular/common/http";
import {Component, OnDestroy, OnInit} from "@angular/core";
import {MatDialog} from "@angular/material/dialog";
import {MatSnackBar} from "@angular/material/snack-bar";
import {NavigationExtras, Router} from "@angular/router";
import {Observable, Subject, throwError, timer} from "rxjs";
import { AppInfoService } from "../../../../../../../shared/util/app-info.service";
import { OrganizationDropdownService } from "../../../../navbar/organization-dropdown.service";
import {catchError, concatMap, map, takeUntil} from "rxjs/operators";
import {DateTypeFormatter} from "../../../../../../../shared/util/date-type-formatter";
import {MessageDialog, MessageDialogType} from "../../../../common/util/message-dialog";
import {Searchable} from "../../../../searchable";
import {ProviderListReorderModel} from "../security-provider-model/provider-list-reorder-model";
import {
  SecurityProviderStatus,
  SecurityProviderStatusList
} from "../security-provider-model/security-provider-status-list";
import {Tool} from "../../../../../../../shared/util/tool";

@Searchable({
   title: "Authentication Providers",
   route: "/settings/security/provider",
   keywords: ["em.security.authentication", "em.security.provider", "em.security.list"]
})
@Component({
   selector: "em-authentication-provider-list-page",
   templateUrl: "./authentication-provider-list-page.component.html",
   styleUrls: ["./authentication-provider-list-page.component.scss"]
})
export class AuthenticationProviderViewComponent implements OnInit, OnDestroy {
   title: string = "_#(js:Authentication Providers)";
   authenticationProviders: SecurityProviderStatus[];

   private currentProvider: string = "";
   private destroy$ = new Subject<void>();

   constructor(private http: HttpClient, private router: Router, private dialogService: MatDialog,
               private snackBar: MatSnackBar, private orgDropdownService: OrganizationDropdownService,
               private appInfoService: AppInfoService)
   {
   }

   ngOnInit() {
      const uri = "../api/em/security/configured-authentication-providers";
      const providers$ = this.http.get<SecurityProviderStatusList>(uri);
      timer(0, 5000)
         .pipe(
            concatMap(() => providers$),
            catchError(error => this.handleGetProvidersError(error)),
            takeUntil(this.destroy$),
            map(list => list.providers.map(p => this.formatCacheAgeLabel(p)))
         )
         .subscribe(providers => {
            this.authenticationProviders = providers;
            this.appInfoService.setLdapProviderUsed(!!providers.find(p => p.ldap));
         });

      this.http.get<SecurityProviderStatus>("../api/em/security/get-current-authentication-provider")
         .subscribe(securityProvider => this.currentProvider = securityProvider != null ? securityProvider.name : "");
   }

   ngOnDestroy(): void {
      this.destroy$.next();
      this.destroy$.unsubscribe();
   }

   showProviderDetails(extras?: NavigationExtras) {
      this.router.navigate(["/settings/security/provider/show-authentication-provider"], extras);
   }

   editProvider(providerName: string) {
      const extras: NavigationExtras = {
         queryParams: {
            providerName: providerName
         }
      };

      this.showProviderDetails(extras);
   }

   moveProviderUp(index: number) {
      this.reorder([index, index - 1]);
   }

   moveProviderDown(index: number) {
      this.reorder([index, index + 1]);
   }


   reorder(event: number[]): void {
      const source = event[0];
      const destination = event[1];
      let reorderModel: ProviderListReorderModel = {source: source, destination: destination};

      this.http.post("../api/em/security/reorder-authentication-providers", reorderModel)
         .pipe(catchError(error => this.handleReorderError(error, source, destination)))
         .subscribe(() => {
            if(source > -1 && source < this.authenticationProviders.length &&
               destination > -1 && destination < this.authenticationProviders.length) {

               let provider = this.authenticationProviders[source];
               this.authenticationProviders.splice(source, 1);
               this.authenticationProviders.splice(destination, 0, provider);

               this.orgDropdownService.refreshProviders();
            }
         });
   }

   removeProvider(index: number) {
      let content: string = "_#(js:em.security.provider.confirmDelete)";
      let current: boolean = false;

      if(this.currentProvider === this.authenticationProviders[index].name) {
         current = true;
         content += "\n\n_#(js:em.security.provider.currentDelete)";
      }

      this.dialogService.open(MessageDialog, {
         width: "350px",
         data: {
            title: "_#(js:Confirm)",
            content: content,
            type: MessageDialogType.DELETE
         }
      }).afterClosed().subscribe((result) => {
         if(result) {
            this.http.delete("../api/em/security/remove-authentication-provider/" + index)
               .pipe(catchError(error => this.handleRemoveProviderError(error, this.authenticationProviders[index].name)))
               .subscribe(() => {
                  if(index > -1) {
                     this.authenticationProviders.splice(index, 1);
                  }

                  if(current) {
                     window.open("../logout?fromEm=true", "_self");
                  }
               });
         }
      });
   }

   clearProviderCache(index: number): void {
      const uri = `../api/em/security/clear-authentication-provider/${index}`;
      this.http.get<SecurityProviderStatus>(uri)
         .pipe(catchError(error => this.handleClearCacheError(error)))
         .subscribe(status => this.authenticationProviders[index] = status);
   }

   copyProvider(index: number) {
      let authenticationProvider = this.authenticationProviders[index];
      const uri = `../api/em/security/copy-authentication-provider/${authenticationProvider.name}`;

      this.http.get<SecurityProviderStatus>(uri)
         .pipe(catchError(error => this.handleCopyProviderError(error)))
         .subscribe(status => {
            if(this.authenticationProviders.findIndex(p => p.name == status.name) == -1) {
               this.authenticationProviders.push(status);
            }
         });
   }

   private handleGetProvidersError(error: HttpErrorResponse): Observable<SecurityProviderStatusList> {
      this.snackBar.open("_#(js:em.security.provider.listError)", null, {
         duration: Tool.SNACKBAR_DURATION
      });
      console.error("Failed to get list of authentication providers: ", error);
      return throwError(error);
   }

   private handleReorderError(error: HttpErrorResponse, source: number, destination: number): Observable<any> {
      this.snackBar.open("_#(js:em.security.provider.reorderError)", null, {
         duration: Tool.SNACKBAR_DURATION
      });
      console.error(`Failed to move provider from ${source} to ${destination}: `, error);
      return throwError(error);
   }

   private handleRemoveProviderError(error: HttpErrorResponse, name: string): Observable<any> {
      this.snackBar.open("_#(js:em.security.provider.deleteError)", null, {
         duration: Tool.SNACKBAR_DURATION
      });
      console.error(`Failed to remove provider "${name}": `, error);
      return throwError(error);
   }

   private handleClearCacheError(error: HttpErrorResponse): Observable<SecurityProviderStatus> {
      this.snackBar.open(error.message, null, {
         duration: Tool.SNACKBAR_DURATION
      });
      console.error("Failed to clear provider cache: ", error);
      return throwError(error);
   }

   private handleCopyProviderError(error: HttpErrorResponse): Observable<SecurityProviderStatus> {
      this.snackBar.open("_#(js:em.security.provider.copyError)", null, {
         duration: Tool.SNACKBAR_DURATION
      });
      console.error("Failed to copy provider: ", error);
      return throwError(error);
   }

   private formatCacheAgeLabel(provider: SecurityProviderStatus): SecurityProviderStatus {
      provider.cacheAgeLabel = DateTypeFormatter.formatDuration(provider.cacheAge, "H:mm:ss");
      return provider;
   }
}
