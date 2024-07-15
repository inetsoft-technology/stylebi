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
import { Injectable, OnDestroy } from "@angular/core";
import { NavigationEnd, Router } from "@angular/router";
import { BehaviorSubject ,  Observable ,  Subscription } from "rxjs";
import { filter, map } from "rxjs/operators";
import { RepositoryEntry } from "../../../../../shared/data/repository-entry";
import { RepositoryTreeService } from "../../widget/repository-tree/repository-tree.service";
import { Title } from "@angular/platform-browser";

interface RouteInfo {
   currentUrl: string;
   dashboard: string;
   dashboardUrl: string;
   repositoryEntry: RepositoryEntry;
   repositoryUrl: string;
   pageTitle: string;
}

@Injectable()
export class CurrentRouteService implements OnDestroy {
   private _currentUrl = new BehaviorSubject<string>(null);
   private _dashboard = new BehaviorSubject<string>(null);
   private _dashboardUrl = new BehaviorSubject<string>(null);
   private _repositoryEntry = new BehaviorSubject<RepositoryEntry>(null);
   private _repositoryUrl = new BehaviorSubject<string>(null);
   private subscription: Subscription;

   get currentUrl(): Observable<string> {
      return this._currentUrl.asObservable();
   }

   get dashboard(): Observable<string> {
      return this._dashboard.asObservable();
   }

   get dashboardUrl(): Observable<string> {
      return this._dashboardUrl.asObservable();
   }

   get repositoryEntry(): Observable<RepositoryEntry> {
      return this._repositoryEntry.asObservable();
   }

   get repositoryUrl(): Observable<string> {
      return this._repositoryUrl.asObservable();
   }

   constructor(router: Router, private repositoryTreeService: RepositoryTreeService,
               private titleService: Title)
   {
      this._currentUrl.next(router.url);

      this.subscription = router.events.pipe(
         filter((event) => event instanceof NavigationEnd),
         map((event: NavigationEnd) => event.urlAfterRedirects),
         map((url) => this.getRouteInfo(url))
      ).subscribe(
         (info) => {
            if(info.pageTitle != null) {
               titleService.setTitle(info.pageTitle);
            }

            this._currentUrl.next(info.currentUrl);
            this._dashboard.next(info.dashboard);
            this._dashboardUrl.next(info.dashboardUrl);
            this._repositoryEntry.next(info.repositoryEntry);
            this._repositoryUrl.next(info.repositoryUrl);
         }
      );
   }

   ngOnDestroy(): void {
      if(this.subscription) {
         this.subscription.unsubscribe();
         this.subscription = null;
      }

      this._currentUrl.complete();
      this._dashboard.complete();
      this._dashboardUrl.complete();
      this._repositoryEntry.complete();
      this._repositoryUrl.complete();
   }

   private getRouteInfo(url: string): RouteInfo {
      const currentUrl = url;
      let dashboard: string = null;
      let dashboardUrl: string = null;
      let repositoryEntry: RepositoryEntry = null;
      let repositoryUrl: string = null;
      let pageTitle: string = null;

      if(url.startsWith("/portal/tab/dashboard/vs/dashboard")) {
         dashboardUrl = url.substring(24);
         const qIndex = url.indexOf("?");

         if(qIndex < 0) {
            dashboard = decodeURIComponent(url.substring(35));
         }
         else {
            dashboard = decodeURIComponent(url.substring(35, qIndex));
         }

         pageTitle = "_#(js:Dashboard)";
      }
      else if(url.startsWith("/portal/tab/dashboard/vs/view")) {
         dashboardUrl = url.substring(24);
         const qIndex = url.indexOf("?");

         if(qIndex < 0) {
            dashboard = decodeURIComponent(url.substring(30));
         }
         else {
            dashboard = decodeURIComponent(url.substring(30, qIndex));
         }
      }
      else if(url.startsWith("/portal/tab/dashboard")) {
         pageTitle = "_#(js:Dashboard)";
      }
      else if(url.startsWith("/portal/tab/report/")) {
         repositoryUrl = url.substring(18);
         repositoryEntry = this.repositoryTreeService.getRouteUrlEntry(url.substring(19));

         if(!!repositoryEntry) {
            pageTitle = repositoryEntry.name;
         }
      }
      else if(url.startsWith("/portal/tab/report")) {
         pageTitle = "_#(js:Repository)";
      }
      else if(url.startsWith("/portal/tab/schedule")) {
         pageTitle = "_#(js:Schedule)";
      }
      else if(url.startsWith("/portal/tab/data")) {
         pageTitle = "_#(js:Data)";
      }

      return { currentUrl, dashboard, dashboardUrl, repositoryEntry, repositoryUrl, pageTitle };
   }
}
