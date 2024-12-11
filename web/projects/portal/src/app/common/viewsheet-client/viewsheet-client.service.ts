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
import { Injectable, NgZone, OnDestroy } from "@angular/core";
import { Observable, ReplaySubject, Subject, Subscription } from "rxjs";
import { StompClientConnection } from "../../../../../shared/stomp/stomp-client-connection";
import { StompClientService } from "../../../../../shared/stomp/stomp-client.service";
import { Tool } from "../../../../../shared/util/tool";
import { AsyncQueuedSubject } from "../util/async-queued-subject";
import { QueuedSubject } from "../util/queued-subject";
import { ViewsheetCommand } from "./viewsheet-command";
import { ViewsheetCommandMessage } from "./viewsheet-command-message";
import { ViewsheetEvent } from "./viewsheet-event";
import { ViewsheetEventMessage } from "./viewsheet-event-message";
import { RenameEventModel } from "./rename-event-model";
import { TransformFinishedEventModel } from "./transform-finished-event-model";

/**
 * Class that handles communication with the server via a WebSocket. The communication
 * is scoped to a single runtime viewsheet instance.
 */
@Injectable({
   providedIn: "root"
})
export class ViewsheetClientService implements OnDestroy {
   private connection: StompClientConnection;
   private connecting: boolean = false;
   private connected = new ReplaySubject<StompClientConnection>(1);
   private connectionErrorSubject = new ReplaySubject<string>(1);
   private commandSubject: AsyncQueuedSubject<ViewsheetCommandMessage>;
   private eventSubject: QueuedSubject<ViewsheetEventMessage>;
   private renameTransformFinishedSubject = new Subject<RenameEventModel>();
   private transformFinishedSubject = new Subject<TransformFinishedEventModel>();
   private heartbeatSubject: Subject<any>;
   private heartbeatSubscription: Subscription;
   private renameTransformSubscription: Subscription;
   private transformFinishedSubscription: Subscription;
   private _clientId: string;
   private _runtimeId: string;
   private _lastModified: number = -1;
   private _focusedLayoutName: string = "Master";
   private _beforeDestroy: () => void;
   private _destroyDelayTime: number = 0;

   /**
    * Creates a new instance of <tt>ViewsheetClient</tt>.
    */
   constructor(private client: StompClientService, private zone: NgZone) {
      this.commandSubject = new AsyncQueuedSubject<ViewsheetCommandMessage>();
      this.eventSubject = new QueuedSubject<ViewsheetEventMessage>();
      this.heartbeatSubject = new Subject<any>();
      this.renameTransformFinishedSubject = new Subject<RenameEventModel>();
      this._clientId = Tool.generateRandomUUID();
   }

   /**
    * Pass in function for any cleanup needed to be done before disconnecting.
    * @param {() => void} cleanup
    */
   set beforeDestroy(cleanup: () => void) {
      this._beforeDestroy = cleanup;
   }

   set destroyDelayTime(delayTime: number) {
      this._destroyDelayTime = delayTime;
   }

   /**
    * Connects to the server.
    */
   public connect(customElement: boolean = false): void {
      if(!this.connecting && !this.connection) {
         this.connecting = true;
         this.client.whenDisconnected().subscribe(() => {
            this.connectionErrorSubject.next("Client disconnected!");
         });
         this.client.connect("../vs-events", false, customElement).subscribe(
            (connection) => {
               this.commandSubject.forceAsync = connection.transport !== "websocket";
               this.connecting = false;
               this.connection = connection;
               this.subscribe();
               this.connectionErrorSubject.next(null);
               this.connected.next(connection);
            },
            (error: any) => {
               this.connecting = false;
               this.connectionErrorSubject.next("Client disconnected!");
               console.error("Failed to connect to server: ", error);
            });
      }
   }

   public whenConnected(): Observable<StompClientConnection> {
      return this.connected.asObservable();
   }

   public connectionError(): Observable<string> {
      return this.connectionErrorSubject.asObservable();
   }

   /**
    * Subscribes the to the events channel.
    */
   private subscribe(): void {
      this.connection.subscribe("/user/commands", (message) => {
         const headers = message.frame.headers;
         // broadcast messages won't have the client ID, so accept those without a client ID, but a
         // matching runtime ID
         if(headers &&
            (!headers["inetsoftClientId"] && headers["sheetRuntimeId"] === this._runtimeId ||
            headers["inetsoftClientId"] === this._clientId))
         {
            this.processCommand(headers, message);
         }
      });

      this.renameTransformSubscription = this.connection.subscribe(
         "/user/dependency-changed", (message) =>
      {
         const event: RenameEventModel = JSON.parse(message.frame.body);

         this.zone.run(() => {
            this.renameTransformFinishedSubject.next(event);
         });
      });

      this.transformFinishedSubscription = this.connection.subscribe(
         "/user/transform-finished", (message) =>
      {
         const event: TransformFinishedEventModel = JSON.parse(message.frame.body);

         this.zone.run(() => {
            this.transformFinishedSubject.next(event);
         });
      });


      if(this.eventSubject) {
         this.eventSubject.subscribe((message: ViewsheetEventMessage) => {
            this.connection.send(message.destination, this.getHeaders(), message.body);
         });
      }

      this.heartbeatSubscription = this.connection.onHeartbeat.subscribe(
         (value) => this.heartbeatSubject.next(value),
         (error) => this.heartbeatSubject.error(error),
         () => this.heartbeatSubject.complete());
   }

   /**
    * Subscribe to the refresh topic by runtimeid. When a message is published on this
    * topic, notify the subscribers
    */
   public connectRefresh(runtimeId: string): Subscription {
      return this.connection.subscribe("/user/topic/refresh-host/" + runtimeId, (message) => {
         const headers = message.frame.headers;

         // only process refresh from other viewsheets
         if(headers && headers.inetsoftClientId && headers.inetsoftClientId !== this.clientId) {
            this.processCommand(headers, message);
         }
      });
   }

   ngOnDestroy(): void {
      if(this._destroyDelayTime > 0) {
         setTimeout(() => this.doDestroy(), this._destroyDelayTime);
      }
      else {
         this.doDestroy();
      }
   }

   private doDestroy(): void {
      if(this._beforeDestroy) {
         this._beforeDestroy();
         this._beforeDestroy = null;
      }

      if(this.commandSubject) {
         this.commandSubject.complete();
         this.commandSubject = null;
      }

      if(this.eventSubject) {
         this.eventSubject.complete();
         this.eventSubject = null;
      }

      if(this.connection) {
         this.connection.disconnect();
         this.connection = null;
      }

      if(this.heartbeatSubscription) {
         this.heartbeatSubscription.unsubscribe();
         this.heartbeatSubscription = null;
      }

      if(this.renameTransformSubscription) {
         this.renameTransformSubscription.unsubscribe();
         this.renameTransformSubscription = null;
      }
   }

   /**
    * The observable from which the viewsheet commands are received.
    */
   public get commands(): Observable<ViewsheetCommandMessage> {
      return this.commandSubject;
   }

   /**
    * Sends an event to the server.
    *
    * @param destination the destination for the event.
    * @param event       the event to send.
    */
   public sendEvent(destination: string, event?: ViewsheetEvent): void {
      let dto: {[name: string]: string} = {};

      for(let property in event) {
         if(event.hasOwnProperty(property)) {
            let value: any = event[property];

            if((typeof value) !== "function") {
               dto[property] = value;
            }
         }
      }

      if(this.eventSubject) {
         this.eventSubject.next(new ViewsheetEventMessage(destination, JSON.stringify(dto)));
      }
   }

   public get onHeartbeat(): Observable<any> {
      return this.heartbeatSubject.asObservable();
   }

   public get onRenameTransformFinished(): Observable<any> {
      return this.renameTransformFinishedSubject.asObservable();
   }

   public get onTransformFinished(): Observable<any> {
      return this.transformFinishedSubject.asObservable();
   }

   get runtimeId(): string {
      return this._runtimeId;
   }

   set runtimeId(value: string) {
      this._runtimeId = value;
   }

   get lastModified(): number {
      return this._lastModified;
   }

   set lastModified(lastModified: number) {
      this._lastModified = lastModified;
   }

   get focusedLayoutName(): string {
      return this._focusedLayoutName;
   }

   set focusedLayoutName(_focusedLayoutName: string) {
      this._focusedLayoutName = _focusedLayoutName;
   }

   get isLayoutFocused(): boolean {
      return this.focusedLayoutName !== "Master";
   }

   /**
    * Return the clientId that this service instance uses to filter its commands. We will
    * send this to the server and use it to send commands to this service instance.
    */
   get clientId(): string {
      return this._clientId;
   }

   private getHeaders(): any {
      const headers: any = {
         inetsoftClientId: this._clientId
      };

      if(this._runtimeId) {
         headers.sheetRuntimeId = this._runtimeId;
      }

      headers.focusedLayoutName = this._focusedLayoutName;

      return headers;
   }

   private processCommand(headers, message) {
      if(headers["sheetLastModified"]) {
         this._lastModified = headers["sheetLastModified"];
      }

      this.commandSubject.next(new ViewsheetCommandMessage(
         headers["assemblyName"],
         headers["commandType"],
         <ViewsheetCommand> JSON.parse(message.frame.body)));
   }
}
