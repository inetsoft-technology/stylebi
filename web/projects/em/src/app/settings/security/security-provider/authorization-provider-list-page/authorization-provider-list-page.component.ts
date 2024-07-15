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
import {catchError, concatMap, map, takeUntil} from "rxjs/operators";
import {DateTypeFormatter} from "../../../../../../../shared/util/date-type-formatter";
import {MessageDialog, MessageDialogType} from "../../../../common/util/message-dialog";
import {ContextHelp} from "../../../../context-help";
import {Searchable} from "../../../../searchable";
import {ProviderListReorderModel} from "../security-provider-model/provider-list-reorder-model";
import {
  SecurityProviderStatus,
  SecurityProviderStatusList
} from "../security-provider-model/security-provider-status-list";
import {Tool} from "../../../../../../../shared/util/tool";

@Searchable({
   title: "Authorization Providers",
   route: "/settings/security/provider",
   keywords: ["em.security.authorization", "em.security.provider", "em.security.list"]
})
@ContextHelp({
   route: "/settings/security/provider",
   link: "EMSettingsSecurityProvider"
})
@Component({
   selector: "em-authorization-provider-list-page",
   templateUrl: "./authorization-provider-list-page.component.html",
   styleUrls: ["./authorization-provider-list-page.component.scss"]
})
export class AuthorizationProviderListPageComponent implements OnInit, OnDestroy {
   title: string = "_#(js:Authorization Providers)";
   authorizationProviders: SecurityProviderStatus[] = [];
   selected: string = "";
   private destroy$ = new Subject<void>();

   constructor(private http: HttpClient, private router: Router, private dialogService: MatDialog,
               private snackBar: MatSnackBar)
   {
   }

   ngOnInit() {
      const uri = "../api/em/security/configured-authorization-providers";
      const providers$ = this.http.get<SecurityProviderStatusList>(uri);
      timer(0, 5000)
         .pipe(
            concatMap(() => providers$),
            catchError(error => this.handleGetProvidersError(error)),
            takeUntil(this.destroy$),
            map(list => list.providers.map(p => this.formatCacheAgeLabel(p)))
         )
         .subscribe(providers => this.authorizationProviders = providers);
   }

   ngOnDestroy(): void {
      this.destroy$.next();
      this.destroy$.unsubscribe();
   }

   showProviderDetails(extras?: NavigationExtras) {
      this.router.navigate(["/settings/security/provider/show-authorization-provider"], extras);
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
      let reorderModel: ProviderListReorderModel = {
         source: source,
         destination: destination
      };

      this.http.post("../api/em/security/reorder-authorization-providers", reorderModel)
         .pipe(catchError(error => this.handleReorderError(error, source, destination)))
         .subscribe(() => {
            if(source > -1 && source < this.authorizationProviders.length &&
               destination > -1 && destination < this.authorizationProviders.length) {

               let provider = this.authorizationProviders[source];
               this.authorizationProviders.splice(source, 1);
               this.authorizationProviders.splice(destination, 0, provider);
            }
         });
   }

   removeProvider(index: number) {
      this.dialogService.open(MessageDialog, {
         width: "350px",
         data: {
            title: "_#(js:Confirm)",
            content: "_#(js:em.security.provider.confirmDelete)",
            type: MessageDialogType.DELETE
         }
      }).afterClosed().subscribe((result) => {
         if(result) {
            this.http.delete("../api/em/security/remove-authorization-provider/" + index)
               .pipe(catchError(error => this.handleRemoveProviderError(error, this.authorizationProviders[index].name)))
               .subscribe(() => {
                  if(index > -1) {
                     this.authorizationProviders.splice(index, 1);
                  }
               });
         }
      });
   }

   clearProviderCache(index: number): void {
      const uri = `../api/em/security/clear-authorization-provider/${index}`;
      this.http.get<SecurityProviderStatus>(uri)
         .pipe(catchError(error => this.handleClearCacheError(error)))
         .subscribe(status => this.authorizationProviders[index] = status);
   }

   copyProvider(index: number): void {
      let authorizationProvider = this.authorizationProviders[index];
      const uri = `../api/em/security/copy-authorization-provider/${authorizationProvider.name}`;

      this.http.get<SecurityProviderStatus>(uri)
         .pipe(catchError(error => this.handleClearCacheError(error)))
         .subscribe(status => this.authorizationProviders.push(status));
   }

   private handleGetProvidersError(error: HttpErrorResponse): Observable<SecurityProviderStatusList> {
      this.snackBar.open("_#(js:em.security.provider.listError)", null, {
         duration: Tool.SNACKBAR_DURATION
      });
      console.error("Failed to get list of authorization providers: ", error);
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

   private formatCacheAgeLabel(provider: SecurityProviderStatus): SecurityProviderStatus {
      provider.cacheAgeLabel = DateTypeFormatter.formatDuration(provider.cacheAge, "H:mm:ss");
      return provider;
   }
}
