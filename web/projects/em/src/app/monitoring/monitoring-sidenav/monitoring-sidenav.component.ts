/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { BreakpointObserver } from "@angular/cdk/layout";
import { Component, NgZone, OnDestroy, OnInit, ViewChild } from "@angular/core";
import { MatSidenav } from "@angular/material/sidenav";
import { Router } from "@angular/router";
import { Subject, Subscription } from "rxjs";
import { takeUntil } from "rxjs/operators";
import { AppInfoService } from "../../../../../shared/util/app-info.service";
import { AuthorizationService } from "../../authorization/authorization.service";
import { PageHeaderService } from "../../page-header/page-header.service";
import { Secured } from "../../secured";

const SMALL_WIDTH_BREAKPOINT = 720;

@Secured({
   route: "/monitoring",
   label: "Monitoring"
})
@Component({
   selector: "em-monitoring-sidenav",
   templateUrl: "./monitoring-sidenav.component.html",
   styleUrls: ["./monitoring-sidenav.component.scss"]
})
export class MonitoringSidenavComponent implements OnInit, OnDestroy {
   @ViewChild(MatSidenav, { static: true }) sidenav: MatSidenav;
   private subscriptions = new Subscription();
   searchText: string;
   summaryVisible = false;
   viewsheetsVisible = false;
   queriesVisible = false;
   cacheVisible = false;
   usersVisible = false;
   clusterVisible = false;
   logVisible = false;
   isEnterprise: boolean;

   private destroy$ = new Subject<void>();

   get title(): string {
      return this.pageTitle.title;
   }

   constructor(private router: Router, private pageTitle: PageHeaderService, zone: NgZone,
               private authzService: AuthorizationService,
               private breakpointObserver: BreakpointObserver,
               private appInfoService: AppInfoService)
   {
   }

   ngOnInit() {
      this.router.events
         .pipe(takeUntil(this.destroy$))
         .subscribe(() => {
            if(this.isScreenSmall()) {
               this.sidenav.close();
            }
         });
      this.authzService.getPermissions("monitoring").subscribe((p) => {
         this.summaryVisible = p.permissions.summary;
         this.viewsheetsVisible = p.permissions.viewsheets;
         this.queriesVisible = p.permissions.queries;
         this.cacheVisible = p.permissions.cache;
         this.usersVisible = p.permissions.users;
         this.clusterVisible = p.permissions.cluster;
         this.logVisible = p.permissions.log;
      });

      this.subscriptions.add(this.appInfoService.isEnterprise().subscribe((isEnterprise) => {
         this.isEnterprise = isEnterprise;
      }));
   }

   ngOnDestroy(): void {
      this.destroy$.next();
      this.destroy$.unsubscribe();
   }

   isScreenSmall(): boolean {
      return this.breakpointObserver.isMatched(`(max-width: ${SMALL_WIDTH_BREAKPOINT}px)`);
   }

   search(): void {
      const extras = {
         queryParams: { search: this.searchText }
      };
      this.router.navigate(["/monitoring/search"], extras);
      this.searchText = "";
   }
}
