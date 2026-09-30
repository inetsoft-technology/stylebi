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
import { type Mocked } from "vitest";
import { NgZone } from "@angular/core";
import { Subject, Subscription } from "rxjs";
import { StompClientService } from ".";
import { ViewsheetClientService } from "./viewsheet-client.service";

function makeStompClientService(): Mocked<StompClientService> {
   return {
      connect: vi.fn().mockReturnValue(new Subject()),
      whenDisconnected: vi.fn().mockReturnValue(new Subject()),
      reconnectError: vi.fn().mockReturnValue(new Subject()),
      reloadOnFailure: false
   } as any;
}

function makeZone(): NgZone {
   return {
      run: vi.fn((fn: () => any) => fn()),
      runOutsideAngular: vi.fn((fn: () => any) => fn())
   } as any;
}

describe("ViewsheetClientService", () => {
   let service: ViewsheetClientService;
   let mockClient: ReturnType<typeof makeStompClientService>;

   beforeEach(() => {
      mockClient = makeStompClientService();
      service = new ViewsheetClientService(mockClient, makeZone());
   });

   // ── runtimeId ─────────────────────────────────────────────────────────────

   it("runtimeId defaults to undefined", () => {
      expect(service.runtimeId).toBeUndefined();
   });

   it("runtimeId setter/getter round-trip", () => {
      service.runtimeId = "vs-runtime-123";
      expect(service.runtimeId).toBe("vs-runtime-123");
   });

   // ── lastModified ──────────────────────────────────────────────────────────

   it("lastModified defaults to -1", () => {
      expect(service.lastModified).toBe(-1);
   });

   it("lastModified setter/getter round-trip", () => {
      service.lastModified = 1700000000000;
      expect(service.lastModified).toBe(1700000000000);
   });

   // ── focusedLayoutName / isLayoutFocused ───────────────────────────────────

   it("focusedLayoutName defaults to 'Master'", () => {
      expect(service.focusedLayoutName).toBe("Master");
   });

   it("isLayoutFocused is false when focusedLayoutName is 'Master'", () => {
      expect(service.isLayoutFocused).toBe(false);
   });

   it("isLayoutFocused is true when focusedLayoutName is not 'Master'", () => {
      service.focusedLayoutName = "Layout1";
      expect(service.isLayoutFocused).toBe(true);
   });

   it("focusedLayoutName setter/getter round-trip", () => {
      service.focusedLayoutName = "PrintLayout";
      expect(service.focusedLayoutName).toBe("PrintLayout");
   });

   // ── clientId ──────────────────────────────────────────────────────────────

   it("clientId is a non-empty string", () => {
      expect(service.clientId).toBeTruthy();
      expect(typeof service.clientId).toBe("string");
   });

   it("each service instance has a unique clientId", () => {
      const other = new ViewsheetClientService(mockClient, makeZone());
      expect(service.clientId).not.toBe(other.clientId);
   });

   // ── whenConnected / connectionError Observables ────────────────────────────

   it("whenConnected() returns an Observable", () => {
      expect(typeof service.whenConnected().subscribe).toBe("function");
   });

   it("connectionError() returns an Observable", () => {
      expect(typeof service.connectionError().subscribe).toBe("function");
   });

   // ── sendEvent ─────────────────────────────────────────────────────────────

   it("sendEvent() does not throw when called before connecting", () => {
      expect(() => service.sendEvent("/events/open", { type: "open" } as any)).not.toThrow();
   });

   it("sendEvent() does not throw with no event body", () => {
      expect(() => service.sendEvent("/events/ping")).not.toThrow();
   });

   // ── beforeDestroy / ngOnDestroy ────────────────────────────────────────────

   it("ngOnDestroy does not throw", () => {
      expect(() => service.ngOnDestroy()).not.toThrow();
   });

   it("beforeDestroy cleanup is called during ngOnDestroy", () => {
      const cleanup = vi.fn();
      service.beforeDestroy = cleanup;
      service.ngOnDestroy();
      expect(cleanup).toHaveBeenCalledTimes(1);
   });

   it("beforeDestroy is only called once even if ngOnDestroy is called twice", () => {
      const cleanup = vi.fn();
      service.beforeDestroy = cleanup;
      service.ngOnDestroy();
      service.ngOnDestroy();
      expect(cleanup).toHaveBeenCalledTimes(1);
   });

   it("destroyDelayTime setter accepts a positive value without throwing", () => {
      expect(() => { service.destroyDelayTime = 500; }).not.toThrow();
   });

   // ── onHeartbeat / onRenameTransformFinished / onTransformFinished ──────────

   it("onHeartbeat is an Observable", () => {
      expect(typeof service.onHeartbeat.subscribe).toBe("function");
   });

   it("onRenameTransformFinished is an Observable", () => {
      expect(typeof service.onRenameTransformFinished.subscribe).toBe("function");
   });

   it("onTransformFinished is an Observable", () => {
      expect(typeof service.onTransformFinished.subscribe).toBe("function");
   });
});

// Bug #77292: a close requested while the open is in flight (runtime id not known yet) must be
// sent once SetRuntimeIdCommand arrives, even though the service has been destroyed.
describe("ViewsheetClientService deferred close", () => {
   const OPEN = "/events/open";
   const CLOSE = "/events/composer/viewsheet/close";
   const RID = "Examples/Sales Summary-128";

   class FakeConnection {
      listeners: {destination: string, next: (message: any) => void, sub: Subscription}[] = [];
      sent: {destination: string, headers: any, body: string}[] = [];
      disconnects = 0;
      // true if the /user/commands STOMP subscription was released other than by disconnect()
      commandsTopicDropped = false;
      onHeartbeat = new Subject<any>();
      private disconnecting = false;

      constructor(public transport: string = "websocket") {
      }

      subscribe(destination: string, next: (message: any) => void): Subscription {
         const entry = {destination, next, sub: null as Subscription};
         entry.sub = new Subscription(() => {
            this.listeners = this.listeners.filter((l) => l !== entry);

            if(!this.disconnecting && destination === "/user/commands" &&
               !this.listeners.some((l) => l.destination === destination))
            {
               this.commandsTopicDropped = true;
            }
         });
         this.listeners.push(entry);
         return entry.sub;
      }

      send(destination: string, headers: any, body: string): void {
         this.sent.push({destination, headers: {...headers}, body});
      }

      disconnect(): void {
         this.disconnecting = true;
         [...this.listeners].forEach((l) => l.sub.unsubscribe());
         this.disconnecting = false;
         this.disconnects++;
      }

      deliver(destination: string, headers: any, body: any): void {
         [...this.listeners]
            .filter((l) => l.destination === destination)
            .forEach((l) => l.next({frame: {command: "MESSAGE", headers, body: JSON.stringify(body)}}));
      }

      sentTo(destination: string) {
         return this.sent.filter((f) => f.destination === destination);
      }
   }

   let stomp: any;
   let connect$: Subject<any>;
   let disconnected$: Subject<void>;
   let reconnectError$: Subject<string>;
   let service: ViewsheetClientService;
   let connection: FakeConnection;

   function connectWith(conn: FakeConnection): void {
      connection = conn;
      service.connect(true);
      connect$.next(conn);
   }

   function command(type: string, body: any, clientId: string = service.clientId): void {
      connection.deliver("/user/commands", {inetsoftClientId: clientId, commandType: type}, body);
   }

   beforeEach(() => {
      connect$ = new Subject<any>();
      disconnected$ = new Subject<void>();
      reconnectError$ = new Subject<string>();
      stomp = {
         connect: vi.fn().mockReturnValue(connect$),
         whenDisconnected: vi.fn().mockReturnValue(disconnected$),
         reconnectError: vi.fn().mockReturnValue(reconnectError$)
      };
      service = new ViewsheetClientService(stomp, makeZone());
      service.beforeDestroy = () => service.closeWhenRuntimeIdKnown(CLOSE);
   });

   afterEach(() => {
      vi.useRealTimers();
   });

   it("sends the close with the runtime id once SetRuntimeIdCommand arrives after destroy", () => {
      connectWith(new FakeConnection());
      const delivered = vi.fn();
      service.commands.subscribe(delivered);
      service.sendOpenEvent(OPEN, {} as any);
      expect(connection.sentTo(OPEN).length).toBe(1);

      service.ngOnDestroy();

      // nothing sent or released yet: the runtime id is not known
      expect(connection.sentTo(CLOSE).length).toBe(0);
      expect(connection.disconnects).toBe(0);
      expect(connection.commandsTopicDropped).toBe(false);

      // a command for another client is ignored
      command("SetRuntimeIdCommand", {runtimeId: "other-1"}, "another-client");
      expect(connection.sentTo(CLOSE).length).toBe(0);

      command("SetRuntimeIdCommand", {runtimeId: RID});

      const close = connection.sentTo(CLOSE);
      expect(close.length).toBe(1);
      expect(close[0].headers.sheetRuntimeId).toBe(RID);
      expect(close[0].headers.inetsoftClientId).toBe(service.clientId);
      expect(close[0].body).toBe("{}");
      expect(connection.disconnects).toBe(1);
      expect(connection.listeners.length).toBe(0);
      // the destroyed subscribers never see the command
      expect(delivered).not.toHaveBeenCalled();

      // no event is sent after destroy, and a late duplicate command changes nothing
      service.sendEvent(OPEN, {} as any);
      command("SetRuntimeIdCommand", {runtimeId: RID});
      expect(connection.sentTo(OPEN).length).toBe(1);
      expect(connection.sentTo(CLOSE).length).toBe(1);
      expect(connection.disconnects).toBe(1);
   });

   it("releases the connection without a close after the timeout", () => {
      vi.useFakeTimers();
      connectWith(new FakeConnection());
      service.sendOpenEvent(OPEN, {} as any);
      service.ngOnDestroy();

      vi.advanceTimersByTime(ViewsheetClientService.DEFERRED_CLOSE_TIMEOUT - 1);
      expect(connection.disconnects).toBe(0);

      vi.advanceTimersByTime(1);
      expect(connection.disconnects).toBe(1);
      expect(connection.sentTo(CLOSE).length).toBe(0);
      expect(connection.listeners.length).toBe(0);

      // a runtime id arriving later is no longer observed
      command("SetRuntimeIdCommand", {runtimeId: RID});
      expect(connection.sentTo(CLOSE).length).toBe(0);
   });

   it("releases the connection without a close on EmbedErrorCommand", () => {
      vi.useFakeTimers();
      connectWith(new FakeConnection());
      service.sendOpenEvent(OPEN, {} as any);
      service.ngOnDestroy();

      command("EmbedErrorCommand", {message: "failed"});

      expect(connection.disconnects).toBe(1);
      expect(connection.sentTo(CLOSE).length).toBe(0);
      vi.advanceTimersByTime(ViewsheetClientService.DEFERRED_CLOSE_TIMEOUT);
      expect(connection.disconnects).toBe(1);
   });

   it("does not defer when EmbedErrorCommand arrived before destroy", () => {
      vi.useFakeTimers();
      connectWith(new FakeConnection());
      service.sendOpenEvent(OPEN, {} as any);

      command("EmbedErrorCommand", {message: "failed"});
      service.ngOnDestroy();

      // released at once, not after the timeout
      expect(connection.disconnects).toBe(1);
      expect(connection.sentTo(CLOSE).length).toBe(0);
      expect(connection.listeners.length).toBe(0);
      vi.advanceTimersByTime(ViewsheetClientService.DEFERRED_CLOSE_TIMEOUT);
      expect(connection.disconnects).toBe(1);
   });

   it("still defers when only another client's EmbedErrorCommand arrived before destroy", () => {
      connectWith(new FakeConnection());
      service.sendOpenEvent(OPEN, {} as any);

      command("EmbedErrorCommand", {message: "failed"}, "another-client");
      service.ngOnDestroy();

      expect(connection.disconnects).toBe(0);
      command("SetRuntimeIdCommand", {runtimeId: RID});
      expect(connection.sentTo(CLOSE)[0].headers.sheetRuntimeId).toBe(RID);
      expect(connection.disconnects).toBe(1);
   });

   it("starts the deferred close timer outside the Angular zone", () => {
      const zone = makeZone();
      service = new ViewsheetClientService(stomp, zone);
      service.beforeDestroy = () => service.closeWhenRuntimeIdKnown(CLOSE);
      connectWith(new FakeConnection());
      service.sendOpenEvent(OPEN, {} as any);

      service.ngOnDestroy();

      expect(zone.runOutsideAngular).toHaveBeenCalledTimes(1);
      disconnected$.next();
      expect(connection.disconnects).toBe(1);
   });

   it("releases the connection without a close when the socket disconnects", () => {
      connectWith(new FakeConnection());
      service.sendOpenEvent(OPEN, {} as any);
      service.ngOnDestroy();

      disconnected$.next();

      expect(connection.disconnects).toBe(1);
      expect(connection.sentTo(CLOSE).length).toBe(0);
   });

   it("releases the connection without a close on a reconnect error", () => {
      connectWith(new FakeConnection());
      service.sendOpenEvent(OPEN, {} as any);
      service.ngOnDestroy();

      reconnectError$.next("failed");

      expect(connection.disconnects).toBe(1);
      expect(connection.sentTo(CLOSE).length).toBe(0);
   });

   it("closes immediately, as before, when the runtime id is already known", () => {
      connectWith(new FakeConnection());
      service.sendOpenEvent(OPEN, {} as any);
      command("SetRuntimeIdCommand", {runtimeId: RID});
      service.runtimeId = RID; // what processSetRuntimeIdCommand() does

      service.ngOnDestroy();

      const close = connection.sentTo(CLOSE);
      expect(close.length).toBe(1);
      expect(close[0].headers.sheetRuntimeId).toBe(RID);
      expect(connection.disconnects).toBe(1);
      expect(connection.listeners.length).toBe(0);
   });

   it("uses the runtime id of a SetRuntimeIdCommand still queued for async delivery", () => {
      // non-websocket transport: commands are delivered in a later macrotask
      connectWith(new FakeConnection("xhr-streaming"));
      service.commands.subscribe(() => {});
      service.sendOpenEvent(OPEN, {} as any);
      command("SetRuntimeIdCommand", {runtimeId: RID});
      expect(service.runtimeId).toBeUndefined();

      service.ngOnDestroy();

      const close = connection.sentTo(CLOSE);
      expect(close.length).toBe(1);
      expect(close[0].headers.sheetRuntimeId).toBe(RID);
      expect(connection.disconnects).toBe(1);
   });

   it("sends neither open nor close and disconnects when the open was never sent", () => {
      connectWith(new FakeConnection());

      service.ngOnDestroy();

      expect(connection.sent.length).toBe(0);
      expect(connection.disconnects).toBe(1);
      expect(connection.listeners.length).toBe(0);
   });

   it("does not defer when the open is still queued before the connection", () => {
      service.sendOpenEvent(OPEN, {} as any);

      expect(() => service.ngOnDestroy()).not.toThrow();
      // never connected, so there is nothing to send on or to keep
      expect(stomp.connect).not.toHaveBeenCalled();
   });

   it("leaves sendEvent-based closes unchanged for services that do not opt in", () => {
      const other = new ViewsheetClientService(stomp, makeZone());
      other.beforeDestroy = () => other.sendEvent(CLOSE);
      connection = new FakeConnection();
      other.connect();
      connect$.next(connection);
      other.sendEvent(OPEN, {} as any);

      other.ngOnDestroy();

      const close = connection.sentTo(CLOSE);
      expect(close.length).toBe(1);
      expect(close[0].headers.sheetRuntimeId).toBeUndefined();
      expect(connection.disconnects).toBe(1);
      expect(connection.listeners.length).toBe(0);
   });
});
