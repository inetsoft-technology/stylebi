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
import { EventEmitter, Injectable, NgZone, OnDestroy } from "@angular/core";
import { Observable, Subject } from "rxjs";
import { StompClientConnection } from "../../../../../../../shared/stomp/stomp-client-connection";
import { StompClientService } from "../../../../../../../shared/stomp/stomp-client.service";

@Injectable()
export class MVChangeService implements OnDestroy {
  private connection: StompClientConnection;
  private connecting: boolean = false;
  private _changed = new Subject<void>();

  constructor(private client: StompClientService, private zone: NgZone) {
    this.connectSocket();
  }

   ngOnDestroy() {
      this._changed.unsubscribe();
      this.disconnectSocket();
   }

   private connectSocket(): void {
      if(!this.connecting && !this.connection) {
         this.client.connect("../vs-events").subscribe(
            (connection) => {
               this.connecting = false;
               this.connection = connection;
               this.subscribeSocket();
            },
            (error: any) => {
               this.connecting = false;
               console.error("Failed to connect to server: ", error);
            }
         );
      }
   }

   private subscribeSocket(): void {
      this.connection.subscribe("/user/em-mv-changed", () => {
         this.zone.run(() => this._changed.next());
      });
   }

   private disconnectSocket(): void {
      if(this.connection) {
         this.connection.disconnect();
         this.connection = null;
      }
   }

   get mvChanged(): Observable<void> {
      return this._changed.asObservable();
   }
}