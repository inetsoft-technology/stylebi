/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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
import { HttpClient, HttpErrorResponse } from "@angular/common/http";
import { Component, NgZone, OnDestroy, OnInit } from "@angular/core";
import { ActivatedRoute, Router } from "@angular/router";
import { Subscription, throwError, timer } from "rxjs";
import { mergeMap, retryWhen } from "rxjs/operators";

@Component({
   selector: "reload-page",
   templateUrl: "reload-page.component.html",
   styleUrls: ["./reload-page.component.scss"]
})

export class ReloadPageComponent implements OnInit, OnDestroy {
   private pingSubscription: Subscription;
   private readonly pingUrl = "../ping";
   private readonly pingInterval = 5000;
   protected redirectTo: string;

   constructor(private route: ActivatedRoute, private router: Router,
               private http: HttpClient, private zone: NgZone)
   {
      this.route.queryParamMap.subscribe(params => {
         this.redirectTo = decodeURIComponent(params.get('redirectTo'));
         console.log(this.redirectTo);
      });
   }

   ngOnInit(): void {
      this.pingSubscription = this.http.get(this.pingUrl, { responseType: "text" }).pipe(
         retryWhen(errors =>
            errors.pipe(
               mergeMap((error: HttpErrorResponse) => {
                  if (error.status === 502 || error.status === 503) {
                     return timer(this.pingInterval);
                  }

                  return throwError(error);
               })
            )
         ))
         .subscribe({
            next: () => {
               this.router.navigate([this.redirectTo]);
            }
         });
   }

   ngOnDestroy(): void {
      this.pingSubscription?.unsubscribe();
   }
}
