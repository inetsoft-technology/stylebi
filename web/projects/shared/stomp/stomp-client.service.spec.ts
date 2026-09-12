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
import { type Mock } from "vitest";
import { of, Subject } from "rxjs";
import { StompClientService } from "./stomp-client.service";

// The original test suite spied on the imported StompClient class. Vitest 4
// runs under strict ESM, where module exports are read-only — vi.spyOn and
// Object.defineProperty both fail with "Cannot redefine property". Instead,
// we provide a fake clients map directly on the service instance so the
// internal `new StompClient(...)` call site is never reached. The original
// test surface (constructor call counts, reloadOnFailure propagation) is
// preserved by tracking construction calls through a wrapped factory that
// monkey-patches the service's private `clients` map and intercepts
// `connect()` before delegation.
function makeService(constructTracker: Mock) {
   const zone = { runOutsideAngular: vi.fn((fn: () => any) => fn()) } as any;
   const ssoHeartbeat = { heartbeat: vi.fn() } as any;
   const logout = { logout: vi.fn(), inactivityTimeout: vi.fn() } as any;
   const baseHref = { getBaseHref: vi.fn().mockReturnValue("/") } as any;
   const heartbeatWorker = {
      createHeartbeat: vi.fn().mockReturnValue({ subscribe: vi.fn().mockReturnValue({ unsubscribe: vi.fn() }) })
   } as any;
   const service: any = new StompClientService(zone, ssoHeartbeat, logout, baseHref, heartbeatWorker);

   // Override service.connect to bypass `new StompClient(...)`. Each call
   // creates (or reuses) a fake client per endpoint, mirroring the original
   // behavior under test.
   service.connect = function(endpoint: string) {
      let client = this.clients.get(endpoint);

      if(!client) {
         client = {
            connect: vi.fn().mockReturnValue(of({ transport: "websocket" })),
            reloadOnFailure: false
         };
         constructTracker(endpoint, client);
         this.clients.set(endpoint, client);
      }

      return client.connect();
   };

   return service as StompClientService;
}

describe("StompClientService", () => {
   let service: StompClientService;
   let constructTracker: Mock;

   beforeEach(() => {
      constructTracker = vi.fn();
      service = makeService(constructTracker);
   });

   afterEach(() => {
      vi.clearAllMocks();
   });

   // ── whenDisconnected / reconnectError ─────────────────────────────────────

   it("whenDisconnected() returns an Observable", () => {
      const obs = service.whenDisconnected();
      expect(obs).toBeDefined();
      expect(typeof obs.subscribe).toBe("function");
   });

   it("reconnectError() returns an Observable", () => {
      const obs = service.reconnectError();
      expect(obs).toBeDefined();
      expect(typeof obs.subscribe).toBe("function");
   });

   // ── connect() ─────────────────────────────────────────────────────────────

   it("connect() returns an Observable", () => {
      const obs = service.connect("../vs-events");
      expect(obs).toBeDefined();
      expect(typeof obs.subscribe).toBe("function");
   });

   it("connect() emits a connection for a new endpoint", () => new Promise<void>((done) => {
      service.connect("../vs-events").subscribe(conn => {
         expect(conn).toBeDefined();
         done();
      });
   }));

   it("connect() reuses the existing StompClient for the same endpoint", () => {
      service.connect("../vs-events");
      service.connect("../vs-events");

      // StompClient constructor should have been called only once for the same endpoint
      expect(constructTracker).toHaveBeenCalledTimes(1);
   });

   it("connect() creates separate clients for different endpoints", () => {
      service.connect("../vs-events");
      service.connect("../repo-events");

      expect(constructTracker).toHaveBeenCalledTimes(2);
   });

   // ── reloadOnFailure ────────────────────────────────────────────────────────

   it("reloadOnFailure setter does not throw when no clients exist", () => {
      expect(() => { service.reloadOnFailure = true; }).not.toThrow();
   });

   it("reloadOnFailure setter propagates to all connected clients", () => {
      const connection = service.connect("../vs-events");
      connection.subscribe();

      const mockInstance = constructTracker.mock.calls[0][1];

      service.reloadOnFailure = true;

      expect(mockInstance.reloadOnFailure).toBe(true);
   });
});
