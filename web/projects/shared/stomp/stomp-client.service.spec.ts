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
import { of } from "rxjs";
import * as stompClientModule from "./stomp-client";
import { StompClientService } from "./stomp-client.service";

// setupFilesAfterEnv eagerly loads StompClientService (and its StompClient import) before
// vi.mock hoisting can intercept it. Use vi.spyOn on the shared exports object instead,
// which mutates the same reference that StompClientService already holds.

function makeService() {
   const zone = { runOutsideAngular: vi.fn((fn: () => any) => fn()) } as any;
   const ssoHeartbeat = {} as any;
   const logout = {} as any;
   const baseHref = { getBaseHref: vi.fn().mockReturnValue("/") } as any;
   return new StompClientService(zone, ssoHeartbeat, logout, baseHref);
}

describe("StompClientService", () => {
   let service: StompClientService;

   beforeEach(() => {
      vi.spyOn(stompClientModule, "StompClient" as any).mockImplementation(() => ({
         connect: vi.fn().mockReturnValue(of({ transport: "websocket" })),
         reloadOnFailure: false
      }));
      service = makeService();
   });

   afterEach(() => {
      vi.restoreAllMocks();
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
      expect(stompClientModule.StompClient).toHaveBeenCalledTimes(1);
   });

   it("connect() creates separate clients for different endpoints", () => {
      service.connect("../vs-events");
      service.connect("../repo-events");

      expect(stompClientModule.StompClient).toHaveBeenCalledTimes(2);
   });

   // ── reloadOnFailure ────────────────────────────────────────────────────────

   it("reloadOnFailure setter does not throw when no clients exist", () => {
      expect(() => { service.reloadOnFailure = true; }).not.toThrow();
   });

   it("reloadOnFailure setter propagates to all connected clients", () => {
      const connection = service.connect("../vs-events");
      connection.subscribe();

      const mockInstance = (stompClientModule.StompClient as Mock).mock.results[0].value;

      service.reloadOnFailure = true;

      expect(mockInstance.reloadOnFailure).toBe(true);
   });
});
