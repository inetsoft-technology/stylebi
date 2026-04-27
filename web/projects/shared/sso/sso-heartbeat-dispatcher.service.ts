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
import { EMPTY, interval, Observable, of, Subject } from "rxjs";
import { catchError, map, shareReplay, switchMap, takeUntil, throttle } from "rxjs/operators";
import { SsoHeartbeatModel } from "./sso-heartbeat-model";
import { SsoHeartbeatService } from "./sso-heartbeat.service";

@Injectable({
   providedIn: "root"
})
export class SsoHeartbeatDispatcherService implements OnDestroy {

   private readonly heartbeatUrl$: Observable<string | null> =
      this.http.get<SsoHeartbeatModel>("../api/sso-heartbeat-model").pipe(
         map(m => m.url),
         catchError(() => of(null as string | null)),
         shareReplay(1)
      );

   private destroy$ = new Subject<void>();

   constructor(private http: HttpClient, private service: SsoHeartbeatService) {
   }

   ngOnDestroy(): void {
      this.destroy$.next();
      this.destroy$.unsubscribe();
   }

   dispatch(): void {
      this.service.heartbeats.pipe(
         takeUntil(this.destroy$),
         throttle(() => interval(30000)),
         switchMap(() => this.heartbeatUrl$)
      ).subscribe(url => this.sendHeartbeat(url));
   }

   private sendHeartbeat(url: string | null): void {
      if(url) {
         this.http.get(url, { withCredentials: true }).pipe(
            catchError(err => { console.error("Failed to send SSO heartbeat.\n", err); return EMPTY; })
         ).subscribe();
      }
   }
}
