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
import { animate, AnimationEvent, state, style, transition, trigger } from "@angular/animations";
import { HttpClient, HttpErrorResponse } from "@angular/common/http";
import { Component, EventEmitter, Input, OnDestroy, OnInit, Output } from "@angular/core";
import { MatDialog } from "@angular/material/dialog";
import { MatSnackBar } from "@angular/material/snack-bar";
import { NavigationEnd, Router } from "@angular/router";
import { Observable, Subject, Subscription, throwError } from "rxjs";
import { catchError, concatMap, filter, map, takeUntil, tap } from "rxjs/operators";
import { ScheduleUsersService } from "../../../../shared/schedule/schedule-users.service";
import { AppInfoService } from "../../../../shared/util/app-info.service";
import { LogoutService } from "../../../../shared/util/logout.service";
import { Tool } from "../../../../shared/util/tool";
import { ComponentPermissions } from "../authorization/component-permissions";
import { Favorite } from "../favorites/favorite";
import { FavoritesService } from "../favorites/favorites.service";
import { HelpLink } from "../help/help-link";
import { HelpLinks } from "../help/help-links";
import { HelpService } from "../help/help.service";
import { PageHeaderService } from "../page-header/page-header.service";
import { EmNavbarModel } from "./em-navbar-model";
import { OrganizationDropdownService } from "./organization-dropdown.service";
import { SendNotificationDialogComponent } from "./send-notification-dialog.component";

@Component({
   selector: "em-navbar",
   templateUrl: "./navbar.component.html",
   styleUrls: ["./navbar.component.scss"],
   animations: [
      trigger("scrollUpDown", [
         state("scrollDown", style({
            marginTop: "-110px"
         })),
         state("scrollUp", style({
            marginTop: "0px"
         })),
         transition("scrollUp => scrollDown", [
            animate("0.5s")
         ]),
         transition("scrollDown => scrollUp", [
            animate("0.5s")
         ])
      ])
   ]
})
export class NavbarComponent implements OnInit, OnDestroy {
   @Input()
   set permissions(p: ComponentPermissions) {
      this._permissions = p;
      this.monitoringVisible = !!p && p.permissions.monitoring;
      this.settingsVisible = !!p && p.permissions.settings;
      this.notifyVisible = !!p && p.permissions.notification;
   }

   get permissions(): ComponentPermissions {
      return this._permissions;
   }

   @Input()
   set name(n: string) {
      this._name = n;
   }

   get name(): string {
      return this._name;
   }

   @Input()
   get scrollDirection(): "up" | "down" {
      return this.scrollState === "scrollDown" ? "down" : "up";
   }

   set scrollDirection(value: "up" | "down") {
      this.scrollState = value === "down" ? "scrollDown" : "scrollUp";
   }

   @Output() transitioning = new EventEmitter<boolean>();
   @Output() visibilityChanged = new EventEmitter<boolean>();

   favorites: Observable<Favorite[]>;
   _name: string;
   favoriteLabel = "_#(js:Add Bookmark)";
   favoriteIcon = "star-outline-icon";
   monitoringVisible: boolean = false;
   settingsVisible: boolean = false;
   notifyVisible = false;
   disabled: boolean = false;
   logoSrc = "../portal/logo";
   helpLink: HelpLink;
   model: EmNavbarModel;
   scrollState = "scrollUp";
   enterprise: boolean = false;

   private _permissions: ComponentPermissions;
   private currentRoute: string;
   private destroy$ = new Subject<void>();
   private favorite = false;
   private helpLinks: HelpLinks;
   private defaultHelpLink: HelpLink;
   private refreshSubscription: Subscription;

   readonly fragmentLabelMap = new Map<string, string>([
      ["data-source", "_#(js:Data Source)"],
      ["database", "_#(js:Database)"],
      ["data-space", "_#(js:Data Space)"],
      ["server", "_#(js:Cluster)"],
      ["license", "_#(js:License)"],
      ["localization", "_#(js:Localization)"],
      ["mv", "_#(js:Materialized Views)"],
      ["audit", "_#(js:Audit)"],
      ["printers", "_#(js:Printers)"],
      ["cache", "_#(js:Caching)"],
      ["disk-quota", "_#(js:Disk Quota)"],
      ["email", "_#(js:Email)"],
      ["performance", "_#(js:Performance)"],
      ["general-format", "_#(js:General Format)"],
      ["look-and-feel", "_#(js:Look and Feel)"],
      ["welcome-page", "_#(js:Welcome Page)"],
      ["login-banner", "_#(js:Login Banner)"],
      ["portal-integration", "_#(js:Portal Integration)"],
      ["pdf", "_#(js:PDF Settings)"],
      ["font-mapping", "_#(js:Font Mapping)"],
      ["time-settings", "_#(js:Time Settings)"],
      ["export-menu", "_#(js:Export Menu)"],
      ["dashboard-settings", "_#(js:Dashboard Settings)"],
      ["viewsheet-toolbar", "_#(js:Viewsheet Toolbar)"],
      ["sharing", "_#(js:Social Sharing)"],
      ["adhoc-settings", "_#(js:Adhoc Settings)"]
   ]);

   constructor(private favoritesService: FavoritesService,
               private helpService: HelpService,
               private pageTitleService: PageHeaderService,
               private orgDropdownService: OrganizationDropdownService,
               private router: Router, private http: HttpClient,
               private snackBar: MatSnackBar, private dialog: MatDialog,
               private logoutService: LogoutService,
               private appInfoService: AppInfoService)
   {
      logoutService.setFromEm(true);
      appInfoService.isEnterprise().subscribe(info => this.enterprise = info);
   }

   ngOnInit() {
      this.favorites = this.favoritesService.favorites;
      this.router.events.pipe(
         filter((event) => (event instanceof NavigationEnd)),
         map((event: NavigationEnd) => event.urlAfterRedirects),
         concatMap((url) => this.favoritesService.isFavorite(url).pipe(
            map((f) => <any> {path: url, favorite: f})
         )),
         takeUntil(this.destroy$)
      ).subscribe(
         (route) => {
            this.setFavorite(route.path, route.favorite);
         }
      );

      if(this.helpLinks == null) {
         this.helpService.getHelpLinks()
            .pipe(
               catchError(error => this.handleHelpLinkError(error)),
               tap(links => this.helpLinks = links),
               tap(links => this.defaultHelpLink = links.links.find(l => l.route == ""))
            )
            .subscribe(() => this.updateHelpLink());
      }

      this.http.get("../api/em/navbar/get-navbar-model").subscribe((result: EmNavbarModel) => {
         this.model = result;
         this.logoutService.setLogoutUrl(this.model.logoutUrl);
      });
   }

   ngOnDestroy(): void {
      this.destroy$.next();
      this.destroy$.unsubscribe();
   }

   onScrollUpDownStart(event: AnimationEvent): void {
      if(event.fromState === "scrollUp" && event.toState === "scrollDown") {
         this.transitioning.emit(true);
      }
   }

   onScrollUpDownDone(event: AnimationEvent): void {
      if(event.fromState === "scrollUp" && event.toState === "scrollDown") {
         this.transitioning.emit(false);
         this.visibilityChanged.emit(false);
      }
      else if(event.fromState === "scrollDown" && event.toState === "scrollUp") {
         this.visibilityChanged.emit(true);
      }
   }

   toggleFavorite(): void {
      if(this.favorite) {
         this.favoritesService.removeFavorite(this.currentRoute);
         this.setFavorite(this.currentRoute, false);
      }
      else {
         this.favoritesService.addFavorite(this.currentRoute, this.getFavoriteTitle());
         this.setFavorite(this.currentRoute, true);
      }
   }

   showNotifyDialog(): void {
      this.dialog.open(SendNotificationDialogComponent, { width: "350px", disableClose: true })
         .afterClosed().subscribe((message: string) => {
            if(message) {
               this.http.post("../api/em/notify", {message})
                  .subscribe(() => {});
            }
         });
   }

   private setFavorite(route: string, favorite: boolean): void {
      this.currentRoute = route;
      this.favorite = favorite;

      this.disabled = this.currentRoute.startsWith("/settings/search") || this.currentRoute.startsWith("/monitoring/search");
      this.favoriteIcon = this.disabled ? "star-outline-icon" :
         favorite ? "star-icon" : "star-outline-icon";

      this.updateHelpLink();
   }

   private updateHelpLink(): void {
      this.helpLink = null;

      if(this.currentRoute && this.helpLinks) {
         this.helpLink = this.helpLinks.links.find((link) => {
            if(link.route[link.route.length - 1] === "*") {
               return this.currentRoute.startsWith(link.route.substring(0, link.route.length - 1));
            }
            else {
               return link.route === this.currentRoute ||
                  link.route === this.currentRoute.substring(0, this.currentRoute.indexOf("?"));
            }
         });

         if(!this.helpLink) {
            this.helpLink = this.defaultHelpLink;
         }
      }
   }

   private handleHelpLinkError(error: HttpErrorResponse): Observable<HelpLinks> {
      this.snackBar.open("_#(js:em.helpLinks.error)", null, { duration: Tool.SNACKBAR_DURATION });
      console.error("Failed to load the context help links: ", error);
      return throwError(error);
   }

   navigateToFavorite(route: string) {
      this.router.navigateByUrl(route);
   }

   logout(): void {
      this.logoutService.logout();
   }

   getFavoriteTitle(): string {
      const fragment = this.router.parseUrl(this.currentRoute).fragment;
      const fragmentLabel = this.fragmentLabelMap.get(fragment);
      let pageTitle = this.pageTitleService.bodyTitleString;
      pageTitle = pageTitle.substring(0, pageTitle.indexOf("|") - 1);
      return !!fragmentLabel ? pageTitle + ": " + fragmentLabel : pageTitle;
   }
}
