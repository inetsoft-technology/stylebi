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
import { Component, OnDestroy, OnInit } from "@angular/core";
import { ActivatedRoute, Router } from "@angular/router";
import { map, withLatestFrom } from "rxjs/operators";
import { Subscription } from "rxjs";
import { PortalTabsService } from "../../services/portal-tabs.service";
import { DashboardTabModel } from "../dashboard-tab-model";
import { DashboardService } from "../dashboard.service";

@Component({
   selector: "p-dashboard-landing",
   templateUrl: "./dashboard-landing.component.html",
   styleUrls: ["./dashboard-landing.component.scss"]
})
export class DashboardLandingComponent implements OnInit, OnDestroy {
   model: DashboardTabModel;
   reportTabEnabled = false;
   private routeSubscription: Subscription;

   constructor(private dashboardService: DashboardService, private router: Router,
               private route: ActivatedRoute, portalTabsService: PortalTabsService)
   {
      portalTabsService.getPortalTabs().subscribe((tabs) => {
         this.reportTabEnabled = !!tabs.find(tab => tab.name === "Report");
      });
   }

   ngOnInit(): void {
      this.routeSubscription = this.route.data.pipe(
         map((data) => data.dashboardTabModel), withLatestFrom(this.route.queryParamMap)
      )
      .subscribe(
         ([model, params]) => {
            this.model = model;

            if(params.get("notLoadDashboard") !== "true" &&
               model && model.dashboards.length > 0)
            {
               const dashboard = model.dashboards[0];
               const values = {};

               for(let i = 0; i < params.keys.length; i++) {
                  values[params.keys[i]] = params.get(params.keys[i]);
               }

               this.router.navigate([`/portal/tab/dashboard/vs/dashboard/${dashboard.name}`],
                  {queryParams: values});
            }
         }
      );
   }

   ngOnDestroy(): void {
      if(this.routeSubscription) {
         this.routeSubscription.unsubscribe();
      }
   }

   newDashboard(): void {
      this.dashboardService.newDashboard.emit(true);
   }

   navigateToReportTab(): void {
      this.router.navigate(["/portal/tab/report"]);
   }
}