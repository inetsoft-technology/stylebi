/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
import { NgZone } from "@angular/core";
import { of, Subscription } from "rxjs";
import { ScheduleTaskChange } from "../../../../../../shared/schedule/model/schedule-task-change";
import { StompClientConnection } from "../../../../../../shared/stomp/stomp-client-connection";
import { StompClientService } from "../../../../../../shared/stomp/stomp-client.service";
import { EmScheduleChangeService } from "./em-schedule-change.service";

/**
 * EmScheduleChangeService - unit tests
 *
 * Risk-first coverage (4 groups, 5 cases):
 *   Group 1 [Risk 3, 2] - constructor initialization and event handling (2 cases)
 *   Group 2 [Risk 2]    - schedule change delivery (1 case)
 *   Group 3 [Risk 2]    - folder change delivery (1 case)
 *   Group 4 [Risk 2]    - ngOnDestroy (1 case)
 *
 * Confirmed bugs (it.failing - remove wrapper once fixed):
 *   - none
 *
 * KEY contracts:
 *   - The service must connect to ../vs-events with the em-client flag enabled.
 *   - The service must subscribe to both schedule-change topics as soon as a connection is available.
 *   - Incoming messages must be re-entered through NgZone before emitting Angular EventEmitter notifications.
 *   - Invalid schedule-change payloads must be ignored instead of crashing the callback.
 *   - ngOnDestroy() must unsubscribe active subscriptions and disconnect the STOMP connection.
 *
 * Design gaps:
 *   - Reconnection behaviour is owned by the shared STOMP layer and is intentionally not retested here.
 *   - There is no explicit test for late or failed connection establishment because this service only reacts once connect() emits.
 */
function makeZone(): NgZone {
   return {
      run: jest.fn((fn: () => any) => fn())
   } as any;
}

function makeConnection() {
   const topicHandlers = new Map<string, (message: any) => void>();
   const subscriptionA = new Subscription();
   const subscriptionB = new Subscription();
   jest.spyOn(subscriptionA, "unsubscribe");
   jest.spyOn(subscriptionB, "unsubscribe");

   const connection = {
      subscribe: jest.fn((topic: string, handler: (message: any) => void) => {
         topicHandlers.set(topic, handler);
         return topicHandlers.size === 1 ? subscriptionA : subscriptionB;
      }),
      disconnect: jest.fn()
   } as unknown as jest.Mocked<StompClientConnection>;

   return { connection, topicHandlers, subscriptionA, subscriptionB };
}

describe("EmScheduleChangeService", () => {
   let zone: NgZone;
   let stompClient: jest.Mocked<StompClientService>;
   let connectionSetup: ReturnType<typeof makeConnection>;
   let service: EmScheduleChangeService;

   beforeEach(() => {
      zone = makeZone();
      connectionSetup = makeConnection();
      stompClient = {
         connect: jest.fn().mockReturnValue(of(connectionSetup.connection))
      } as any;

      service = new EmScheduleChangeService(stompClient, zone);
   });

   // ---------------------------------------------------------------------------
   // Group 1 [Risk 3, 2] - constructor initialization and event handling
   // ---------------------------------------------------------------------------
   describe("constructor", () => {
      it("[Risk 3] should ignore invalid schedule-change payloads without throwing or emitting", () => {
         // Regression-sensitive: malformed server events should be dropped instead of breaking all subsequent callbacks.
         const emitSpy = jest.spyOn(service.onChange, "emit");
         const handler = connectionSetup.topicHandlers.get("/user/em-schedule-changed");

         expect(() => handler({
            frame: {
               body: "not-json"
            }
         })).not.toThrow();

         // (a)
         expect((zone.run as jest.Mock)).toHaveBeenCalledTimes(1);
         // (b)
         expect(emitSpy).not.toHaveBeenCalled();
      });

      it("[Risk 2] should connect to the event endpoint and subscribe to both schedule topics", () => {
         // (a)
         expect(stompClient.connect).toHaveBeenCalledWith("../vs-events", true);
         // (b)
         expect(connectionSetup.connection.subscribe).toHaveBeenCalledWith(
            "/user/em-schedule-changed",
            expect.any(Function)
         );
         // (c)
         expect(connectionSetup.connection.subscribe).toHaveBeenCalledWith(
            "/user/em-schedule-folder-changed",
            expect.any(Function)
         );
      });
   });

   // ---------------------------------------------------------------------------
   // Group 2 [Risk 2] - schedule change delivery
   // ---------------------------------------------------------------------------
   describe("schedule change event", () => {
      it("[Risk 2] should parse the payload inside NgZone and emit onChange", () => {
         const change: ScheduleTaskChange = { name: "task-1", type: "modified" } as any;
         const emitSpy = jest.spyOn(service.onChange, "emit");
         const handler = connectionSetup.topicHandlers.get("/user/em-schedule-changed");

         handler({
            frame: {
               body: JSON.stringify(change)
            }
         });

         // (a)
         expect((zone.run as jest.Mock)).toHaveBeenCalledTimes(1);
         // (b)
         expect(emitSpy).toHaveBeenCalledWith(change);
      });
   });

   // ---------------------------------------------------------------------------
   // Group 3 [Risk 2] - folder change delivery
   // ---------------------------------------------------------------------------
   describe("folder change event", () => {
      it("[Risk 2] should emit onFolderChange inside NgZone when the folder topic fires", () => {
         const emitSpy = jest.spyOn(service.onFolderChange, "emit");
         const handler = connectionSetup.topicHandlers.get("/user/em-schedule-folder-changed");

         handler({
            frame: {
               body: "ignored"
            }
         });

         // (a)
         expect((zone.run as jest.Mock)).toHaveBeenCalledTimes(1);
         // (b)
         expect(emitSpy).toHaveBeenCalledTimes(1);
      });
   });

   // ---------------------------------------------------------------------------
   // Group 4 [Risk 2] - ngOnDestroy
   // ---------------------------------------------------------------------------
   describe("ngOnDestroy", () => {
      it("[Risk 2] should unsubscribe tracked subscriptions and disconnect the STOMP connection", () => {
         service.ngOnDestroy();

         // (a)
         expect(connectionSetup.subscriptionA.unsubscribe).toHaveBeenCalledTimes(1);
         // (b)
         expect(connectionSetup.subscriptionB.unsubscribe).toHaveBeenCalledTimes(1);
         // (c)
         expect(connectionSetup.connection.disconnect).toHaveBeenCalledTimes(1);
         // (d)
         expect(() => service.ngOnDestroy()).not.toThrow();
      });
   });
});
