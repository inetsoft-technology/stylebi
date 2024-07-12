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
import { Injectable, NgZone } from "@angular/core";
import { BehaviorSubject, Observable, Subscription } from "rxjs";
import { map, shareReplay, switchMap } from "rxjs/operators";
import { StompClientConnection } from "../../../../shared/stomp/stomp-client-connection";
import { StompClientService } from "../../../../shared/stomp/stomp-client.service";

@Injectable({
   providedIn: "root"
})
export class MonitoringDataService {
   // TODO replace with reactive version in all usages
   private _cluster: string; // current selected cluster node
   private _cluster$ = new BehaviorSubject<string>("");
   private connection: Observable<StompClientConnection>;

   constructor(stompClientService: StompClientService,
               private zone: NgZone)
   {
      this.connection = stompClientService.connect("../vs-events").pipe(shareReplay(1));
   }

   public connect<T>(endpoint: string, topic: string = "monitoring", replay: boolean = false): Observable<any> {
      return new Observable((observer) => {
         const subscription = new Subscription();

         this.connection.subscribe((connection) => {
            subscription.add(
               connection.subscribe("/user/" + topic + "/" + endpoint, (message) => {
                  let body = JSON.parse(message.frame.body);
                  this.zone.run(() => {
                     observer.next(body);
                  });
               }, undefined, undefined, replay)
            );
         });

         return subscription;
      });
   }

   public sendMessage(endpoint: string, body: any = null): void {
      if(body != null) {
         body = JSON.stringify(body);
      }

      this.connection.subscribe((connection) => {
         connection.send("/user/" + endpoint, null, body);
      });
   }

   public refresh(): void {
      this.sendMessage("monitoring/refresh");
   }

   set cluster(cluster: string) {
      this._cluster = cluster;
      this._cluster$.next(cluster);
   }

   get cluster(): string {
      return this._cluster;
   }

   get nonNullCluster(): string {
      return this._cluster || "";
   }

   getClusterAddress(): Observable<string> {
      return this._cluster$.asObservable().pipe(map(this.mapAddressToPath));
   }

   private mapAddressToPath(address): string {
      return address ? "/" + address : "";
   }

   /**
    * Subscribe to endpoint with cluster address appended if applicable
    */
   public getMonitoringData(endpoint: string): Observable<any> {
      return this.getClusterAddress().pipe(
         switchMap((address) => this.connect(endpoint + address, "monitoring", true))
      );
   }
}
