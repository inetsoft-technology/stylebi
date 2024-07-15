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
import { Injectable, NgZone } from "@angular/core";
import { Observable, Subject } from "rxjs";
import { StompClientConnection } from "../../../../../shared/stomp/stomp-client-connection";
import { StompClientService } from "../viewsheet-client";
import { AssetChangeEvent } from "./asset-change-event";
import { RenameEventModel } from "../viewsheet-client/rename-event-model";

@Injectable()
export class AssetClientService {
   private connection: StompClientConnection;
   private connecting: boolean = false;
   private readonly assetChangedSubject = new Subject<AssetChangeEvent>();
   private renameTransformFinishedSubject = new Subject<RenameEventModel>();

   /**
    * Creates a new instance of <tt>AssetClient</tt>.
    */
   constructor(private client: StompClientService, private zone: NgZone) {
   }

   /**
    * Connects to the server.
    */
   public connect(): void {
      if(!this.connecting && !this.connection) {
         this.connecting = true;

         this.client.connect("../vs-events").subscribe(
            (connection) => {
               this.connecting = false;
               this.connection = connection;
               this.subscribe();
            },
            (error: any) => {
               this.connecting = false;
               console.error("Failed to connect to server: ", error);
               this.assetChangedSubject.error(error);
            }
         );
      }
   }

   /**
    * Subscribes the to the asset-changed topic.
    */
   private subscribe(): void {
      this.connection.subscribe("/user/asset-changed", (message) => {
         const event: AssetChangeEvent = JSON.parse(message.frame.body);

         this.zone.run(() => {
            this.assetChangedSubject.next(event);
         });
      });

      this.connection.subscribe("/user/dependency-changed", (message) => {
         const event: RenameEventModel = JSON.parse(message.frame.body);

         this.zone.run(() => {
            this.renameTransformFinishedSubject.next(event);
         });
      });
   }

   /**
    * Disconnects from the server.
    */
   public disconnect(): void {
      if(this.connection) {
         this.connection.disconnect();
         this.connection = null;
      }
   }

   public get onRenameTransformFinished(): Observable<any> {
      return this.renameTransformFinishedSubject.asObservable();
   }

   /**
    * The observable from which the asset changed events are received.
    */
   public get assetChanged(): Observable<AssetChangeEvent> {
      return this.assetChangedSubject.asObservable();
   }
}
