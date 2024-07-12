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
import { Component, NgZone, OnDestroy, OnInit } from "@angular/core";
import { DomSanitizer, SafeResourceUrl } from "@angular/platform-browser";
import {
   ActivatedRoute,
   NavigationEnd,
   NavigationExtras,
   Router,
   RouterEvent
} from "@angular/router";
import { filter, map } from "rxjs/operators";
import { fromEvent, Subscription } from "rxjs";
import { RepositoryTreeService } from "../../../widget/repository-tree/repository-tree.service";
import { HideNavService } from "../../services/hide-nav.service";

@Component({
   selector: "p-portal-report",
   templateUrl: "./portal-report.component.html",
   styleUrls: ["./portal-report.component.scss"]
})
export class PortalReportComponent implements OnInit, OnDestroy {
   contentSource: SafeResourceUrl;
   public reportID = "";
   public reportName;
   private subscriptions = new Subscription();

   constructor(private repositoryTreeService: RepositoryTreeService,
               private sanitationService: DomSanitizer, private router: Router,
               private route: ActivatedRoute, private zone: NgZone,
               private hideNavService: HideNavService)
   {
      this.subscriptions.add(
         this.router.events.subscribe((event: RouterEvent) => {
            this.setContentSource();

            if(event instanceof NavigationEnd) {
               this.reportID = "";
            }
         })
      );
   }

   ngOnInit(): void {
      this.setContentSource();

      this.subscriptions.add(
         fromEvent(window, "message")
            .pipe(
               filter((event: MessageEvent) => event.origin === location.origin),
               map((event: MessageEvent) => event.data)
            )
            .subscribe((data: any) => {
               this.returnToPrevious(data);
               this.messageLinkEventHandle(data);
               this.receiveReportID(data);
            })
      );
   }

   setContentSource() {
      this.subscriptions.add(
         this.route.data.subscribe(
            (data) => {
               this.zone.run(() => {
                  this.contentSource = <SafeResourceUrl> data.contentSource;
                  this.reportName = data.repositoryEntry.name;
               });
            }
         )
      );
   }

   private isViewsheetURLEvent(data: any): boolean {
      if(data.messageType === "return") {
         let eventUrl = data.previousUrl;
         return !!data.previousUrl && (eventUrl.indexOf("app/viewer/view/") >= 0 ||
                                       eventUrl.indexOf("report/vs/view/") >= 0);
      }

      return false;
   }

   openViewsheetURL(prevURL: string): string {
      if(prevURL.indexOf("portal/tab/report/vs/view") >= 0) {
         return prevURL;
      }

      let runtimePath = prevURL.substring(prevURL.indexOf("app/viewer/view/") +
                                          "app/viewer/view/".length);
      return `portal/tab/report/vs/view/${runtimePath}`;
   }

   private returnToPrevious(data: any): void {
      let url = data.previousUrl;
      let isViewsheet = this.isViewsheetURLEvent(data);
      const extras = this.hideNavService.appendParameter({});

      if(isViewsheet) {
         this.router.navigate([this.openViewsheetURL(url)], extras as NavigationExtras);
      }
   }

   private messageLinkEventHandle(data: any): void {
      let params = data.params;

      if(!!data.data && !!params) {
         //test if window has a parent window to avoid infinite loop
         if(window !== window.parent) {
            window.parent.postMessage(data, location.origin);
         }
      }
   }

   /**
    * Get reportID from toolbar.js when opening report scoped composer
    */
   private receiveReportID(data: {portalReportID: string}): void {
      this.reportID = data.portalReportID;
   }

   ngOnDestroy(): void {
      if(this.subscriptions) {
         this.subscriptions.unsubscribe();
      }
   }
}
