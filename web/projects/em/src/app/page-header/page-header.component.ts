/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { HttpClient, HttpParams } from "@angular/common/http";
import { Component, EventEmitter, Input, OnDestroy, OnInit, Output } from "@angular/core";
import { Router } from "@angular/router";
import { Subscription } from "rxjs";
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
   private subscriptions = new Subscription();
   private refreshSubscription: Subscription;
   private currentProvider: string;

   constructor(private pageTitle: PageHeaderService,
               private orgDropdownService: OrganizationDropdownService,
               private router: Router,
               private http: HttpClient,
               private appInfoService: AppInfoService,
               private usersService: ScheduleUsersService)
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
   }

   private refreshModel(currentProvider: string, providerChanged?: boolean): void {
      const params = new HttpParams()
         .set("provider", !!currentProvider ? currentProvider : "")
         .set("providerChanged", !!providerChanged ? providerChanged : "false");

      this.http.get("../api/em/pageheader/get-pageheader-model", { params })
         .subscribe((result: EmPageHeaderModel) => {
            this.model = result;
            this.currentProvider = result.providerName;
         });
   }

   ngOnDestroy(): void {
      if(!!this.refreshSubscription) {
         this.refreshSubscription.unsubscribe();
         this.refreshSubscription = null;
      }
   }

   public getTitle() {
      return this.pageTitle.title;
   }

   changeOrg(){
      this.http.post("../api/em/pageheader/organization", this.model)
         .subscribe(() => {
            // Refresh data in current route
            let currRoute = this.router.url;
            this.router.navigateByUrl("/", { skipLocationChange: true }).then(() => {
               this.router.navigate([currRoute]);
            });
            this.usersService.loadScheduleUsers();
         });
   }

   public showOrgs() {
      return this.pageTitle.orgVisible;
   }
}
