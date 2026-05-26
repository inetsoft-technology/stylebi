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
import { EventEmitter } from "@angular/core";
import { Subscription } from "rxjs";
import { SsoHeartbeatService } from "../sso/sso-heartbeat.service";
import { StompClientConnection } from "./stomp-client-connection";

function makeChannel() {
   return {
      subscribe: jest.fn().mockReturnValue(new Subscription()),
      send: jest.fn(),
      transport: "websocket",
      hasConnection: jest.fn().mockReturnValue(true)
   };
}

function makeConnection(emClient = false) {
   const channel = makeChannel();
   const heartbeat = new EventEmitter<any>();
   const onDisconnect = jest.fn();
   const ssoService = new SsoHeartbeatService();

   const connection = new StompClientConnection(
      channel as any, heartbeat, onDisconnect, ssoService, emClient
   );

   return { connection, channel, onDisconnect, ssoService };
}

describe("StompClientConnection", () => {
   describe("send", () => {
      it("passes message through to the channel", () => {
         const { connection, channel } = makeConnection();
         connection.send("/topic/test", null, "{}");
         expect(channel.send).toHaveBeenCalledWith("/topic/test", null, "{}");
      });

      it("injects emClient header when emClient flag is true", () => {
         const { connection, channel } = makeConnection(true);
         connection.send("/topic/test", null, "{}");
         expect(channel.send).toHaveBeenCalledWith(
            "/topic/test",
            expect.objectContaining({ emClient: "true" }),
            "{}"
         );
      });

      it("does not add emClient header when emClient flag is false", () => {
         const { connection, channel } = makeConnection(false);
         connection.send("/topic/test", null, "{}");
         const [, headers] = channel.send.mock.calls[0];
         expect(headers).toBeNull();
      });

      it("creates headers object when emClient=true and headers are null", () => {
         const { connection, channel } = makeConnection(true);
         connection.send("/topic/test", null, "{}");
         const [, headers] = channel.send.mock.calls[0];
         expect(headers).toEqual({ emClient: "true" });
      });
   });

   describe("ssoHeartbeat behaviour via send", () => {
      it("dispatches heartbeat for normal destinations", () => {
         const { connection, ssoService } = makeConnection();
         const heartbeatSpy = jest.spyOn(ssoService, "heartbeat");

         connection.send("/topic/some-event", null, "{}");

         expect(heartbeatSpy).toHaveBeenCalledTimes(1);
      });

      it("dispatches heartbeat for touch-asset when design flag is true", () => {
         const { connection, ssoService } = makeConnection();
         const heartbeatSpy = jest.spyOn(ssoService, "heartbeat");
         const body = JSON.stringify({ wallboard: false, design: true, changed: false, update: false });

         connection.send("/events/composer/touch-asset", null, body);

         expect(heartbeatSpy).toHaveBeenCalledTimes(1);
      });

      it("dispatches heartbeat for touch-asset when changed flag is true", () => {
         const { connection, ssoService } = makeConnection();
         const heartbeatSpy = jest.spyOn(ssoService, "heartbeat");
         const body = JSON.stringify({ wallboard: false, design: false, changed: true, update: false });

         connection.send("/events/composer/touch-asset", null, body);

         expect(heartbeatSpy).toHaveBeenCalledTimes(1);
      });

      it("suppresses heartbeat for touch-asset when all flags are false", () => {
         const { connection, ssoService } = makeConnection();
         const heartbeatSpy = jest.spyOn(ssoService, "heartbeat");
         const body = JSON.stringify({ wallboard: false, design: false, changed: false, update: false });

         connection.send("/events/composer/touch-asset", null, body);

         expect(heartbeatSpy).not.toHaveBeenCalled();
      });

      it("dispatches heartbeat for touch-asset when wallboard flag is true", () => {
         const { connection, ssoService } = makeConnection();
         const heartbeatSpy = jest.spyOn(ssoService, "heartbeat");
         const body = JSON.stringify({ wallboard: true, design: false, changed: false, update: false });

         connection.send("/events/composer/touch-asset", null, body);

         expect(heartbeatSpy).toHaveBeenCalledTimes(1);
      });

      it("dispatches heartbeat for touch-asset when update flag is true", () => {
         const { connection, ssoService } = makeConnection();
         const heartbeatSpy = jest.spyOn(ssoService, "heartbeat");
         const body = JSON.stringify({ wallboard: false, design: false, changed: false, update: true });

         connection.send("/events/composer/touch-asset", null, body);

         expect(heartbeatSpy).toHaveBeenCalledTimes(1);
      });
   });

   describe("disconnect", () => {
      it("calls onDisconnect callback", () => {
         const { connection, onDisconnect } = makeConnection();
         connection.disconnect();
         expect(onDisconnect).toHaveBeenCalledTimes(1);
      });

      it("unsubscribes all active subscriptions", () => {
         const { connection, channel } = makeConnection();
         const sub1 = new Subscription();
         const sub2 = new Subscription();
         jest.spyOn(sub1, "unsubscribe");
         jest.spyOn(sub2, "unsubscribe");
         channel.subscribe.mockReturnValueOnce(sub1).mockReturnValueOnce(sub2);

         connection.subscribe("/topic/a");
         connection.subscribe("/topic/b");
         connection.disconnect();

         expect(sub1.unsubscribe).toHaveBeenCalled();
         expect(sub2.unsubscribe).toHaveBeenCalled();
      });
   });

   describe("onHeartbeat", () => {
      it("exposes the heartbeat EventEmitter", () => {
         const { connection, channel } = makeConnection();
         const heartbeat = new EventEmitter();
         const onDisconnect = jest.fn();
         const ssoService = new SsoHeartbeatService();
         const conn = new StompClientConnection(channel as any, heartbeat, onDisconnect, ssoService, false);

         expect(conn.onHeartbeat).toBe(heartbeat);
      });
   });
});
