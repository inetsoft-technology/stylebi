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
import { EventEmitter } from "@angular/core";
import { AsyncSubject, Observable, of as observableOf, Subject } from "rxjs";
import { SsoHeartbeatService } from "../sso/sso-heartbeat.service";
import { LogoutService } from "../util/logout.service";
import { StompClientChannel } from "./stomp-client-channel";
import { StompClientConnection } from "./stomp-client-connection";

const SockJS = require("sockjs-client");
declare const window: any;

/**
 * Reactive wrapper for the StompJS client.
 */
export class StompClient {
   private client: Stomp.Client;
   private clientSubject = new Subject<Stomp.Client>();
   private clientChannel = new StompClientChannel(this.clientSubject);
   private referenceCount = 0;
   private connected = false;
   private heartbeatTimeoutId: any = null;
   private reconnectCnt = 0;
   private pendingConnections: Subject<StompClientConnection>[] = [];
   private heartbeat: EventEmitter<any> = new EventEmitter<any>();
   private emClient: boolean = false;

   public reloadOnFailure: boolean;

   constructor(private endpoint: string, private onDisconnect: (endpoint: string) => any,
               private ssoHeartbeatService: SsoHeartbeatService,
               private logoutService: LogoutService, emClient: boolean, private baseHref: string,
               private customElement: boolean)
   {
      this.emClient = emClient;
      this.client = this.createStompClient();
      this.client.connect({},
         () => {
            this.attachOnClose();
            this.clientSubject.next(this.client);
            this.pendingConnections.forEach((sub) => {
               sub.next(this.createConnection());
               sub.complete();
            });
            this.connected = true;
            this.pendingConnections = null;
         },
         (error: any) => {
            if(this.pendingConnections || (this.customElement && this.connected)) {
               console.error("Disconnected from server: ", error);

               if(this.pendingConnections != null) {
                  this.pendingConnections.forEach((sub) => {
                     sub.error(error);
                  });
               }

               this.connected = false;
               this.pendingConnections = [];
               this.onDisconnect(this.endpoint);
               this.clientSubject.next(null);
               this.clientSubject.complete();
            }
            // this could be called after the connect callback has been received if the
            // connection was unexpectedly closed from the server side
            else if(this.connected) {
               this.reconnect();
            }
         });
   }

   private resetHeartbeatTimer() {
      clearTimeout(this.heartbeatTimeoutId);
      this.heartbeatTimeoutId = setTimeout(() => {
         this.heartbeat.emit({});
         this.resetHeartbeatTimer();
      }, 25000);
   }

   private createStompClient(): Stomp.Client {
      /* use this to simulate a downgraded socket connection
      const transports = ["xhr-streaming"];
      const socket: any = new SockJS(resolveURL(this.endpoint), null, {transports});
       */
      const socket: any = new SockJS(this.resolveURL(this.endpoint));
      const client = Stomp.over(socket);
      // TODO find where to change server message size limit
      // If a message above 64kb is sent, the socket connection closes stating message size
      // too large, stomp default frame size is 16kb
      client.maxWebSocketFrameSize = 64 * 1024;
      client.debug = null; // comment this out to trace the messages
      this.resetHeartbeatTimer();
      return client;
   }

   private attachOnClose(): void {
      // StompJS replaces the onclose of the websocket in its connect() method. We need to monkey
      // patch it in the connect callback so that we can trap close events caused by a session
      // timeout.
      const onclose = this.client.ws.onclose;
      this.client.ws.onclose = (event) => {
         if(!this.customElement) {
            if(event?.code === 4001) {
               // logged out by a different window
               this.logoutService.logout(true, this.emClient);
            }
            else if(event?.code === 4002) {
               // session timeout with security enabled
               this.logoutService.sessionExpired();
            }
         }

         if(onclose) {
            onclose.apply(this.client.ws, event);
         }
      };
   }

   private reconnect(): void {
      this.reconnectCnt++;
      this.clientSubject.next(null);
      this.client = this.createStompClient();
      this.client.connect({},
         () => {
            this.reconnectCnt = 0;
            this.attachOnClose();
            this.clientSubject.next(this.client);
         },
         (error: any) => {
            if(this.reconnectCnt > 30) {
               if(this.reloadOnFailure) {
                  console.error("Failed to reconnect to server, reloading: ", error);
                  window.location.reload(true);
               }

               console.error("Failed to reconnect to server: ", error);
               this.connected = false;
               this.pendingConnections = [];
               this.onDisconnect(this.endpoint);
               this.clientSubject.complete();
            }
            else {
               setTimeout(() => this.reconnect(), 10000);
            }
         });
   }

   connect(): Observable<StompClientConnection> {
      this.referenceCount = this.referenceCount + 1;

      if(this.connected) {
         return observableOf(this.createConnection());
      }
      else {
         const subject: AsyncSubject<StompClientConnection> =
            new AsyncSubject<StompClientConnection>();
         this.pendingConnections.push(subject);
         return subject.asObservable();
      }
   }

   private onConnectionDisconnect(): void {
      this.referenceCount = this.referenceCount - 1;

      if(this.referenceCount == 0) {
         this.connected = false;
         this.client.disconnect(() => {});
         this.client = null;
         this.clientSubject.next(null);
         this.clientSubject.complete();
         this.onDisconnect(this.endpoint);
      }
   }

   private createConnection(): StompClientConnection {
      return new StompClientConnection(
         this.clientChannel, this.heartbeat, () => this.onConnectionDisconnect(),
         this.ssoHeartbeatService);
   }

   resolveURL(url: string): string {
      return this.baseHref + "/" + url;
   }
}
