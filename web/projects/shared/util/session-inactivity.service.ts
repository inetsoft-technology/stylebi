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
import { HttpClient } from "@angular/common/http";
import { Injectable, OnDestroy } from "@angular/core";
import { fromEvent, merge, Observable, Subject } from "rxjs";
import { debounceTime, takeUntil } from "rxjs/operators";
import { SessionExpirationModel } from "./model/session-expiration-model";

/**
 * A service that detects inactivity by tracking mouse clicks and mouse wheel events. Can be used
 * on pages where session won't expire due to periodic updates that keep refreshing the session.
 */
@Injectable() // not root so that it can be disposed properly once not needed
export class SessionInactivityService implements OnDestroy {
   private inactivityWarningTime = 90000; // time before warning about inactivity
   private inactivity$ = new Subject<number>();
   private activity$ = new Subject<void>();
   private destroy$ = new Subject<void>();
   private timeout: any;

   constructor(private http: HttpClient) {
      this.trackInactivity();
   }

   private trackInactivity() {
      this.http.get<SessionExpirationModel>("../api/session/session-timeout")
         .subscribe((data) => {
            this.startTimer(data.sessionTimeout);

            const mouseDown$ = fromEvent(document, "mousedown");
            const wheel$ = fromEvent(document, "wheel");
            merge(mouseDown$, wheel$)
               .pipe(
                  debounceTime(200),
                  takeUntil(this.destroy$)
               )
               .subscribe(() => {
                  this.activity$.next();
                  this.startTimer(data.sessionTimeout);
               });
         });
   }

   private startTimer(sessionTimeout: number) {
      if(this.timeout != null) {
         clearTimeout(this.timeout);
      }

      const timeoutDuration = sessionTimeout - this.inactivityWarningTime;
      this.timeout = setTimeout(() => {
         this.inactivity$.next(this.inactivityWarningTime);
      }, timeoutDuration);
   }

   public onInactivity(): Observable<number> {
      return this.inactivity$.asObservable();
   }

   public onActivity(): Observable<void> {
      return this.activity$.asObservable();
   }

   ngOnDestroy(): void {
      this.destroy$.next();

      if(this.timeout != null) {
         clearTimeout(this.timeout);
      }
   }
}