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
import { EventEmitter } from "@angular/core";
import { Subscription } from "rxjs";
import { SsoHeartbeatService } from "../sso/sso-heartbeat.service";
import { StompClientChannel } from "./stomp-client-channel";
import { StompMessage } from "./stomp-message";

/**
 * Class that encapsulates an active reference to a StompJS client.
 */
export class StompClientConnection {
   private subscriptions: Subscription[]  = [];

   constructor(private client: StompClientChannel, private heartbeat: EventEmitter<any>,
               private onDisconnect: () => any, private ssoHeartbeatService: SsoHeartbeatService)
   {
   }

   get onHeartbeat(): EventEmitter<any> {
      return this.heartbeat;
   }

   get transport(): string {
      return this.client.transport;
   }

   subscribe(destination: string, next?: (value: StompMessage) => void,
             error?: (error: any) => void, complete?: () => void, replay: boolean = false): Subscription
   {
      const subscription =
         this.client.subscribe(destination, next, error, complete, replay);
      subscription.add(() => {
         this.subscriptions.filter((sub) => sub != subscription);
      });
      this.subscriptions.push(subscription);
      return subscription;
   }

   send(destination: string, headers: any, body: string): void {
      this.ssoHeartbeat(destination, body);
      this.client.send(destination, headers, body);
   }

   disconnect(): void {
      this.subscriptions.forEach((sub) => sub.unsubscribe());
      this.subscriptions = [];
      this.onDisconnect();
   }

   private ssoHeartbeat(destination: string, body: string): void {
      let dispatch = true;

      if(destination === "/events/composer/touch-asset") {
         const {wallboard, design, changed, update} = JSON.parse(body);
         dispatch = wallboard || design || changed || update;
      }

      if(dispatch) {
         this.ssoHeartbeatService.heartbeat();
      }
   }
}