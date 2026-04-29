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
import { Component, Input, OnDestroy, OnInit } from "@angular/core";
import { DomSanitizer, SafeResourceUrl } from "@angular/platform-browser";
import { ActivatedRoute, Router } from "@angular/router";
import { map, withLatestFrom } from "rxjs/operators";
import { Subscription } from "rxjs";

@Component({
   selector: "welcome-page",
   templateUrl: "./welcome-page.component.html",
   styleUrls: ["./welcome-page.component.scss"]
})
export class WelcomePageComponent implements OnInit, OnDestroy {
   @Input() welcomePageUri: string;
   contentSource: SafeResourceUrl;
   showWelcome = false;
   licensedComponentMsg: string;
   private routeSubscription: Subscription;

   constructor(private route: ActivatedRoute, private sanitationService: DomSanitizer,
               private router: Router) {
      this.routeSubscription = route.parent.data.pipe(
         map((data) => {
            this.welcomePageUri = data.reportTabModel.welcomePageUri;
            this.licensedComponentMsg = data.reportTabModel.licensedComponentMsg;
            return !!data.reportTabModel && !data.reportTabModel.showRepositoryAsList;
         }),
         withLatestFrom(route.queryParamMap),
      ).subscribe(
         ([showWelcome, params]) =>
            this.showWelcome = showWelcome && params.get("hideWelcomePage") !== "true"
      );
   }

   ngOnInit(): void {
      if(this.welcomePageUri) {
         this.contentSource = this.sanitationService
            .bypassSecurityTrustResourceUrl(this.welcomePageUri);
      }
   }

   ngOnDestroy(): void {
      if(this.routeSubscription) {
         this.routeSubscription.unsubscribe();
      }
   }

   navigateToScheduleTab(): void {
      this.router.navigate(["/portal/tab/schedule"]);
   }

   navigateToRepository(): void {
      this.router.navigate(["/portal/tab/report"], { queryParams: { hideWelcomePage: "true" } });
   }

   navigateToPinboard(): void {
      this.router.navigate(["/portal/tab/dashboard"]);
   }
   
   navigateToDataSources(): void {
      this.router.navigate(["/portal/tab/data/datasources"]);
   }

   navigateToWorksheets(): void {
      this.router.navigate(["/portal/tab/data/folder"]);
   }
}
