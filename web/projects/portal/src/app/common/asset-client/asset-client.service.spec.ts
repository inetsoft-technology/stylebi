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
import { Subject } from "rxjs";
import { StompClientConnection } from "../../../../../shared/stomp/stomp-client-connection";
import { StompClientService } from "../viewsheet-client";
import { AssetClientService } from "./asset-client.service";

function makeConnection(): jest.Mocked<Pick<StompClientConnection, "subscribe" | "disconnect">> {
   return {
      subscribe: jest.fn().mockImplementation((topic: string, cb: any) => {
         // expose the callback so tests can trigger it
         (makeConnection as any)._handlers = (makeConnection as any)._handlers || {};
         (makeConnection as any)._handlers[topic] = cb;
         return { unsubscribe: jest.fn() };
      }),
      disconnect: jest.fn()
   } as any;
}

function makeStompClientService(conn: any) {
   const connSubject = new Subject<any>();
   return {
      connect: jest.fn().mockReturnValue(connSubject.asObservable()),
      _connSubject: connSubject
   } as any;
}

function makeZone(): NgZone {
   return {
      run: jest.fn((fn: () => any) => fn())
   } as any;
}

describe("AssetClientService", () => {
   let conn: ReturnType<typeof makeConnection>;
   let stompService: ReturnType<typeof makeStompClientService>;
   let zone: NgZone;
   let service: AssetClientService;

   beforeEach(() => {
      conn = makeConnection();
      stompService = makeStompClientService(conn);
      zone = makeZone();
      service = new AssetClientService(stompService, zone);
   });

   // ── assetChanged / onRenameTransformFinished are Observables ──────────────

   it("assetChanged is an Observable", () => {
      expect(typeof service.assetChanged.subscribe).toBe("function");
   });

   it("onRenameTransformFinished is an Observable", () => {
      expect(typeof service.onRenameTransformFinished.subscribe).toBe("function");
   });

   // ── connect() ─────────────────────────────────────────────────────────────

   it("connect() calls stompClient.connect with the vs-events endpoint", () => {
      service.connect();
      expect(stompService.connect).toHaveBeenCalledWith("../vs-events");
   });

   it("connect() subscribes to asset-changed and dependency-changed topics on success", () => {
      service.connect();
      // Simulate successful connection
      stompService._connSubject.next(conn);

      const topics: string[] = conn.subscribe.mock.calls.map((c: any[]) => c[0]);
      expect(topics).toContain("/user/asset-changed");
      expect(topics).toContain("/user/dependency-changed");
   });

   it("connect() does not reconnect if already connecting", () => {
      service.connect();
      service.connect();
      expect(stompService.connect).toHaveBeenCalledTimes(1);
   });

   it("connect() does not reconnect if already connected", () => {
      service.connect();
      stompService._connSubject.next(conn);
      service.connect();
      expect(stompService.connect).toHaveBeenCalledTimes(1);
   });

   // ── disconnect() ──────────────────────────────────────────────────────────

   it("disconnect() calls connection.disconnect()", () => {
      service.connect();
      stompService._connSubject.next(conn);
      service.disconnect();
      expect(conn.disconnect).toHaveBeenCalled();
   });

   it("disconnect() does not throw when not connected", () => {
      expect(() => service.disconnect()).not.toThrow();
   });

   // ── assetChanged event forwarding ─────────────────────────────────────────

   it("assetChanged emits events received from the asset-changed topic", () => {
      const events: any[] = [];
      service.assetChanged.subscribe(e => events.push(e));

      service.connect();
      stompService._connSubject.next(conn);

      // Locate the handler registered for /user/asset-changed
      const assetCall = conn.subscribe.mock.calls.find((c: any[]) =>
         c[0] === "/user/asset-changed"
      );
      const handler = assetCall[1];

      const payload = { name: "Report1", type: 4 };
      handler({ frame: { command: "MESSAGE", headers: {}, body: JSON.stringify(payload) } });

      expect(events).toHaveLength(1);
      expect(events[0]).toEqual(payload);
   });

   it("assetChanged emits null when the message body is empty", () => {
      const events: any[] = [];
      service.assetChanged.subscribe(e => events.push(e));

      service.connect();
      stompService._connSubject.next(conn);

      const assetCall = conn.subscribe.mock.calls.find((c: any[]) =>
         c[0] === "/user/asset-changed"
      );
      const handler = assetCall[1];

      handler({ frame: { command: "MESSAGE", headers: {}, body: "" } });

      expect(events).toHaveLength(1);
      expect(events[0]).toBeNull();
   });
});
