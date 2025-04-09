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
import {
   Component,
   EventEmitter,
   Input, NgZone,
   OnDestroy,
   OnInit,
   Output,
} from "@angular/core";
import { Router } from "@angular/router";
import { BehaviorSubject, Subscription } from "rxjs";
import { debounceTime, distinctUntilChanged } from "rxjs/operators";
import { ScheduleUsersService } from "../../../../shared/schedule/schedule-users.service";
import { AppInfoService } from "../../../../shared/util/app-info.service";
import { OrganizationDropdownService } from "../navbar/organization-dropdown.service";
import { EmPageHeaderModel } from "./em-page-header-model";
import { PageHeaderService } from "./page-header.service";

@Component({
   selector: "em-page-header",
   templateUrl: "./page-header.component.html",
   styleUrls: ["./page-header.component.scss"]
})
export class PageHeaderComponent implements OnInit, OnDestroy {
   @Input() title: string;
   @Output() toggleSidenav = new EventEmitter<void>();
   model: EmPageHeaderModel;
   isEnterprise: boolean;
   searchOpen = false;
   searchResults: { id: string, name: string }[] = [];
   private subscriptions = new Subscription();
   private refreshSubscription: Subscription;
   private currentProvider: string;
   private searchQuery$ = new BehaviorSubject<string>("");

   get searchQuery(): string {
      return this.searchQuery$.value;
   }

   set searchQuery(value: string) {
      this.searchQuery$.next(value ? value.trim() : "");
   }

   constructor(private pageTitle: PageHeaderService,
               private orgDropdownService: OrganizationDropdownService,
               private router: Router,
               private http: HttpClient,
               private appInfoService: AppInfoService,
               private usersService: ScheduleUsersService,
               private ngZone: NgZone)
   {
   }

   ngOnInit() {
      this.refreshModel(this.currentProvider);

      if(this.refreshSubscription == null) {
         this.refreshSubscription = this.orgDropdownService.onRefresh.subscribe((res) => {
            this.currentProvider = res.provider;
            this.refreshModel(this.currentProvider, res.providerChanged);
         });
      }

      this.subscriptions.add(this.appInfoService.isEnterprise().subscribe((isEnterprise) => {
         this.isEnterprise = isEnterprise;
      }));

      this.subscriptions.add(
         this.searchQuery$
            .pipe(
               distinctUntilChanged(),
               debounceTime(500)
            )
            .subscribe(value => {
               if(value) {
                  this.filterOrgs(value);
               }
               else {
                  this.initSearchResults();
               }
            })
      );
   }

   private refreshModel(currentProvider: string, providerChanged?: boolean): void {
      const params = new HttpParams()
         .set("provider", !!currentProvider ? currentProvider : "")
         .set("providerChanged", !!providerChanged ? providerChanged : "false");
      let oldOrg = this.model != null ? this.model.currOrgID : null;

      this.http.get("../api/em/pageheader/get-pageheader-model", { params })
         .subscribe((result: EmPageHeaderModel) => {
            this.model = result;
            this.currentProvider = result.providerName;

            if(oldOrg != null && this.model != null && this.model.currOrgID != oldOrg) {
               let currRoute = this.router.url;
               this.routeToPath(currRoute);
               this.usersService.loadScheduleUsers();
            }

            this.initSearchResults();
         });
   }

   ngOnDestroy(): void {
      if(!!this.refreshSubscription) {
         this.refreshSubscription.unsubscribe();
         this.refreshSubscription = null;
      }

      if(!!this.searchQuery$) {
         this.searchQuery$.complete();
      }

      if(!!this.subscriptions) {
         this.subscriptions.unsubscribe();
      }
   }

   public getTitle() {
      return this.pageTitle.title;
   }

   changeOrg(){
      this.http.post("../api/em/pageheader/organization", this.model)
         .subscribe(() => {
            let currRoute = this.router.url;
            this.routeToPath(currRoute);
            this.usersService.loadScheduleUsers();
         });
   }

   private routeToPath(currRoute: string) {
      // Refresh data in current route
      let index = currRoute.indexOf("#");
      let fragment = index == -1 ? "" : currRoute.substring(index + 1);
      currRoute = index == -1 ? currRoute : currRoute.substring(0, index);

      this.ngZone.run(() => {
         this.router.navigateByUrl("/", { skipLocationChange: true }).then(() => {
            if(index != -1) {
               this.ngZone.run(() => {
                  this.router.navigate([currRoute], {fragment: fragment, replaceUrl: true});

               });
            }
            else {
               this.ngZone.run(() => {
                  this.router.navigate([currRoute]);
               });
            }
         });
      });
   }

   public showOrgs() {
      return this.pageTitle.orgVisible;
   }

   get orgSelectVisible() {
      return this.model?.isMultiTenant &&
         !!this.model?.currOrgID && this.showOrgs() && this.isEnterprise;
   }

   filterOrgs(name: string): void {
      if(!this.model || !this.model.orgs?.length) {
         return;
      }

      let orgs = this.getOrgIdsAndNames();
      this.searchResults = orgs.filter(org => org.name.toLowerCase().includes(name.toLowerCase()));
   }

   initSearchResults(): void {
      this.searchResults = this.getOrgIdsAndNames();
   }

   getOrgIdsAndNames(): { id: string, name: string }[] {
      let ids: string[] = this.model?.orgIDs;
      let names: string[] = this.model?.orgs;
      let result = [];

      if(ids && names) {
         ids.forEach((id, index) => {
            result.push({ id: id, name: names[index] });
         });
      }

      return result;
   }

   toggleSearch(): void {
      this.searchOpen = !this.searchOpen;
   }

   isSelected(name: string): boolean {
      let org = this.getOrgIdsAndNames().find(org => org.name === name);
      return !!org && org.id === this.model.currOrgID;
   }

   onSelectOrg(event: any): void {
      let orgName = event.option.value;
      let orgId = this.searchResults.find(org => org.name === orgName)?.id;

      if(orgId && orgId != this.model.currOrgID) {
         this.model.currOrgID = orgId;
         this.changeOrg();
      }

      this.searchOpen = false;
      this.searchQuery = "";
   }
}
