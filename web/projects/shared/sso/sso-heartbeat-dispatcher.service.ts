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
import { HttpClient } from "@angular/common/http";
import { Injectable, OnDestroy } from "@angular/core";
import { interval, Subject } from "rxjs";
import { takeUntil, throttle } from "rxjs/operators";
import { SsoHeartbeatModel } from "./sso-heartbeat-model";
import { SsoHeartbeatService } from "./sso-heartbeat.service";

@Injectable({
   providedIn: "root"
})
export class SsoHeartbeatDispatcherService implements OnDestroy{

   private heartbeatUrl;
   private destroy$ = new Subject<void>();

   constructor(private http: HttpClient, private service: SsoHeartbeatService) {
      this.http.get<SsoHeartbeatModel>("../api/sso-heartbeat-model").subscribe(model => {
         this.heartbeatUrl = model.url;
      });
   }

   ngOnDestroy(): void {
      this.destroy$.next();
      this.destroy$.unsubscribe();
   }

   dispatch(): void {
      this.service.heartbeats
         .pipe(
            takeUntil(this.destroy$),
            throttle(() => interval(30000))
         )
         .subscribe(() => this.sendHeartbeat());
   }

   private sendHeartbeat(): void {
      if(this.heartbeatUrl) {
         this.http.get(this.heartbeatUrl, { withCredentials: true }).subscribe(
            () => {},
            error => console.warn("Failed to send SSO heartbeat.\n", error)
         );
      }
   }
}
