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
import { Observable ,  Subject } from "rxjs";
import { StompClientService } from "../../common/viewsheet-client";
import { StompClientConnection } from "../../../../../shared/stomp/stomp-client-connection";

@Injectable()
export class ComposerClientService {
   private connection: StompClientConnection;
   private connecting: boolean = false;
   private editComposerAssetSubject: Subject<any> = new Subject<any>();

   /**
    * Creates a new instance of <tt>ComposerClientService</tt>.
    */
   constructor(private client: StompClientService, private zone: NgZone) {
   }

   /**
    * Connects to the server.
    */
   public connect(): void {
      if(!this.connecting && !this.connection) {
         this.client.connect("../vs-events").subscribe(
            (connection) => {
               this.connecting = false;
               this.connection = connection;
               this.subscribe();
            },
            (error: any) => {
               this.connecting = false;
               console.error("Failed to connect to server: ", error);
               this.editComposerAssetSubject.error(error);
            }
         );
      }
   }

   /**
    * Subscribes the to the composer-client topic.
    */
   private subscribe(): void {
      this.connection.subscribe("/user/composer-client", (message) => {
         this.zone.run(() => {
            let event: any = JSON.parse(message.frame.body);
            this.editComposerAssetSubject.next(event);
         });
      });
   }

   private unsubscribe(): void {
      this.connection.send("/user/composer-client/leave", null, null);
   }

   /**
    * Disconnects from the server.
    */
   public disconnect(): void {
      if(this.editComposerAssetSubject) {
         this.editComposerAssetSubject.complete();
         this.editComposerAssetSubject = null;
      }

      if(this.connection) {
         this.unsubscribe();
         this.connection.disconnect();
         this.connection = null;
      }
   }

   /**
    * The observable from which the edit composer asset events are received.
    */
   public get editAsset(): Observable<any> {
      return this.editComposerAssetSubject.asObservable();
   }
}
