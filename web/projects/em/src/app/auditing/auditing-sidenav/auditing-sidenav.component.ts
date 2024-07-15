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
import { HttpClient } from "@angular/common/http";
import { Component, OnDestroy, OnInit, ViewChild } from "@angular/core";
import { MatSidenav } from "@angular/material/sidenav";
import { Router } from "@angular/router";
import { Subject } from "rxjs";
import { mergeMap, takeUntil, tap } from "rxjs/operators";
import { AuthorizationService } from "../../authorization/authorization.service";
import { LogViewLinks } from "../../monitoring/log/log-view-links";
import { PageHeaderService } from "../../page-header/page-header.service";
import { Secured } from "../../secured";

const SMALL_WIDTH_BREAKPOINT = 720;

@Secured({
   route: "/auditing",
   label: "Auditing"
})
@Component({
   selector: "em-auditing-sidenav",
   templateUrl: "./auditing-sidenav.component.html",
   styleUrls: ["./auditing-sidenav.component.scss"]
})
export class AuditingSidenavComponent implements OnInit, OnDestroy {
   @ViewChild(MatSidenav, {static: true}) sidenav: MatSidenav;
   inactiveResourceVisible = false;
   inactiveUserVisible = false;
   identityInfoVisible = false;
   logonErrorVisible = false;
   logonHistoryVisible = false;
   modificationHistoryVisible = false;
   userSessionVisible = false;
   dependentAssetsVisible = false;
   requiredAssetsVisible = false;
   exportHistoryVisible = false;
   scheduleHistoryVisible = false;
   bookmarkHistoryVisible = false;
   fluentdLogging = false;

   private destroy$ = new Subject<void>();

   get title(): string {
      return this.pageTitle.title;
   }

   constructor(private router: Router, private pageTitle: PageHeaderService,
               private breakpointObserver: BreakpointObserver, private http: HttpClient,
               private authzService: AuthorizationService)
   {
   }

   ngOnInit(): void {
      this.router.events
         .pipe(takeUntil(this.destroy$))
         .subscribe(() => {
            if(this.isScreenSmall()) {
               this.sidenav.close();
            }
         });

      this.http.get<LogViewLinks>("../api/em/monitoring/audit/links")
         .pipe(
            tap(links => this.fluentdLogging = links.fluentdLogging),
         )

      this.authzService.getPermissions("auditing")
         .subscribe(p => {
            this.inactiveResourceVisible = p.permissions["inactive-resource"];
            this.inactiveUserVisible = p.permissions["inactive-user"];
            this.identityInfoVisible = p.permissions["identity-info"];
            this.logonErrorVisible = p.permissions["logon-error"];
            this.logonHistoryVisible = p.permissions["logon-history"];
            this.modificationHistoryVisible = p.permissions["modification-history"];
            this.userSessionVisible = p.permissions["user-session"];
            this.dependentAssetsVisible = p.permissions["dependent-assets"];
            this.requiredAssetsVisible = p.permissions["required-assets"];
            this.exportHistoryVisible = p.permissions["export-history"];
            this.scheduleHistoryVisible = p.permissions["schedule-history"];
            this.bookmarkHistoryVisible = p.permissions["bookmark-history"];
         });
   }

   ngOnDestroy(): void {
      this.destroy$.next();
      this.destroy$.unsubscribe();
   }

   isScreenSmall(): boolean {
      return this.breakpointObserver.isMatched(`(max-width: ${SMALL_WIDTH_BREAKPOINT}px)`);
   }
}
