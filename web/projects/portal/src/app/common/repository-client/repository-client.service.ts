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
import { StompClientService } from "../viewsheet-client";
import { StompClientConnection } from "../../../../../shared/stomp/stomp-client-connection";
import {
   DebounceCallback,
   DebounceService
} from "../../widget/services/debounce.service";

@Injectable()
export class RepositoryClientService {
   private connection: StompClientConnection;
   private connecting = false;
   private repositoryChanged$ = new Subject<any>();
   private dataChanged$ = new Subject<any>();

   /**
    * Creates a new instance of <tt>RepositoryClientService</tt>.
    */
   constructor(private client: StompClientService, private zone: NgZone,
               private debounceService: DebounceService)
   {
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
               this.repositoryChanged$.error(error);
               this.dataChanged$.error(error);
            }
         );
      }
   }

   /**
    * Subscribes the to the repository-changed topic.
    */
   private subscribe(): void {
      this.connection.subscribe("/user/repository-changed", (message) => {
         const event: any = JSON.parse(message.frame.body);
         this.zone.run(() => this.repositoryChanged$.next(event));
      });
      this.connection.subscribe("/user/data-changed", (message) => {
         const event: any = !!message.frame.body ? JSON.parse(message.frame.body) : null;
         this.zone.run(() => this.dataChanged$.next(event));
      });
   }

   /**
    * Disconnects from the server.
    */
   public disconnect(): void {
      if(this.repositoryChanged$) {
         this.repositoryChanged$.complete();
         this.repositoryChanged$ = null;
      }

      if(this.dataChanged$) {
         this.dataChanged$.complete();
         this.dataChanged$ = null;
      }

      if(this.connection) {
         this.connection.disconnect();
         this.connection = null;
      }
   }

   /**
    * The observable from which the repository change events are received.
    */
   public get repositoryChanged(): Observable<any> {
      return this.repositoryChanged$.asObservable();
   }

   public get dataChanged(): Observable<any> {
      return this.dataChanged$.asObservable();
   }
}