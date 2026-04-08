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
import { StompClientService } from "../viewsheet-client";
import { DebounceService } from "../../widget/services/debounce.service";
import { RepositoryClientService } from "./repository-client.service";

function makeStompService() {
   const connSubject = new Subject<any>();
   return {
      connect: jest.fn().mockReturnValue(connSubject.asObservable()),
      _connSubject: connSubject
   } as any;
}

function makeConnection() {
   return {
      subscribe: jest.fn().mockReturnValue({ unsubscribe: jest.fn() }),
      disconnect: jest.fn()
   } as any;
}

function makeZone(): NgZone {
   return { run: jest.fn((fn: () => any) => fn()) } as any;
}

function makeDebounce(): jest.Mocked<DebounceService> {
   return { debounce: jest.fn() } as any;
}

describe("RepositoryClientService", () => {
   let stompService: ReturnType<typeof makeStompService>;
   let conn: ReturnType<typeof makeConnection>;
   let zone: NgZone;
   let debounce: ReturnType<typeof makeDebounce>;
   let service: RepositoryClientService;

   beforeEach(() => {
      stompService = makeStompService();
      conn = makeConnection();
      zone = makeZone();
      debounce = makeDebounce();
      service = new RepositoryClientService(stompService, zone, debounce);
   });

   // ── Observables ────────────────────────────────────────────────────────────

   it("repositoryChanged is an Observable", () => {
      expect(typeof service.repositoryChanged.subscribe).toBe("function");
   });

   it("dataChanged is an Observable", () => {
      expect(typeof service.dataChanged.subscribe).toBe("function");
   });

   // ── connect() ─────────────────────────────────────────────────────────────

   it("connect() calls stompService.connect with vs-events endpoint", () => {
      service.connect();
      expect(stompService.connect).toHaveBeenCalledWith("../vs-events");
   });

   it("connect() subscribes to repository-changed and data-changed topics", () => {
      service.connect();
      stompService._connSubject.next(conn);

      const topics: string[] = conn.subscribe.mock.calls.map((c: any[]) => c[0]);
      expect(topics).toContain("/user/repository-changed");
      expect(topics).toContain("/user/data-changed");
   });

   it("connect() does not reconnect after a successful connection", () => {
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

   it("disconnect() completes the repositoryChanged observable", () => {
      let completed = false;
      service.repositoryChanged.subscribe({ complete: () => { completed = true; } });
      service.disconnect();
      expect(completed).toBe(true);
   });

   it("disconnect() completes the dataChanged observable", () => {
      let completed = false;
      service.dataChanged.subscribe({ complete: () => { completed = true; } });
      service.disconnect();
      expect(completed).toBe(true);
   });

   // ── event forwarding ──────────────────────────────────────────────────────

   it("repositoryChanged emits events from /user/repository-changed", () => {
      const received: any[] = [];
      service.repositoryChanged.subscribe(e => received.push(e));

      service.connect();
      stompService._connSubject.next(conn);

      const call = conn.subscribe.mock.calls.find((c: any[]) =>
         c[0] === "/user/repository-changed"
      );
      const handler = call[1];
      const payload = { path: "/Reports/Sales" };
      handler({ frame: { body: JSON.stringify(payload) } });

      expect(received[0]).toEqual(payload);
   });

   it("repositoryChanged emits null when message body is empty", () => {
      const received: any[] = [];
      service.repositoryChanged.subscribe(e => received.push(e));

      service.connect();
      stompService._connSubject.next(conn);

      const call = conn.subscribe.mock.calls.find((c: any[]) =>
         c[0] === "/user/repository-changed"
      );
      call[1]({ frame: { body: "" } });

      expect(received[0]).toBeNull();
   });

   it("dataChanged emits events from /user/data-changed", () => {
      const received: any[] = [];
      service.dataChanged.subscribe(e => received.push(e));

      service.connect();
      stompService._connSubject.next(conn);

      const call = conn.subscribe.mock.calls.find((c: any[]) =>
         c[0] === "/user/data-changed"
      );
      call[1]({ frame: { body: JSON.stringify({ source: "orders" }) } });

      expect(received[0]).toEqual({ source: "orders" });
   });
});
