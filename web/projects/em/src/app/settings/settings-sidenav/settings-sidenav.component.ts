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
import { BreakpointObserver } from "@angular/cdk/layout";
import { AfterViewInit, Component, NgZone, OnDestroy, OnInit, ViewChild } from "@angular/core";
import { MatSidenav } from "@angular/material/sidenav";
import { Router } from "@angular/router";
import { Subject } from "rxjs";
import { takeUntil } from "rxjs/operators";
import { AuthorizationService } from "../../authorization/authorization.service";
import { Secured } from "../../secured";

const SMALL_WIDTH_BREAKPOINT = 720;

@Secured({
   route: "/settings",
   label: "Settings"
})
@Component({
   selector: "em-settings-sidenav",
   templateUrl: "./settings-sidenav.component.html",
   styleUrls: ["./settings-sidenav.component.scss"]
})
export class SettingsSidenavComponent implements OnInit, AfterViewInit, OnDestroy {
   @ViewChild(MatSidenav, { static: true }) sidenav: MatSidenav;
   searchText: string;
   generalVisible = false;
   securityVisible = false;
   contentVisible = false;
   scheduleVisible = false;
   presentationVisible = false;
   loggingVisible = false;
   propertiesVisible = false;
   loading = true;

   private destroy$ = new Subject<void>();

   constructor(private router: Router, zone: NgZone, private authzService: AuthorizationService,
               private breakpointObserver: BreakpointObserver)
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

      this.authzService.getPermissions("settings").subscribe((p) => {
         this.generalVisible = p.permissions.general;
         this.securityVisible = p.permissions.security;
         this.contentVisible = p.permissions.content;
         this.scheduleVisible = p.permissions.schedule;
         this.presentationVisible = p.permissions.presentation;
         this.loggingVisible = p.permissions.logging;
         this.propertiesVisible = p.permissions.properties;
      });
   }

   ngAfterViewInit() {
      setTimeout(() => {
         this.loading = false;
      });
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
      this.router.navigate(["/settings/search"], extras);
   }
}
