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
import { StompClientService } from ".";
import { ViewsheetClientService } from "./viewsheet-client.service";

function makeStompClientService(): jest.Mocked<StompClientService> {
   return {
      connect: jest.fn().mockReturnValue(new Subject()),
      whenDisconnected: jest.fn().mockReturnValue(new Subject()),
      reconnectError: jest.fn().mockReturnValue(new Subject()),
      reloadOnFailure: false
   } as any;
}

function makeZone(): NgZone {
   return {
      run: jest.fn((fn: () => any) => fn()),
      runOutsideAngular: jest.fn((fn: () => any) => fn())
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
      const cleanup = jest.fn();
      service.beforeDestroy = cleanup;
      service.ngOnDestroy();
      expect(cleanup).toHaveBeenCalledTimes(1);
   });

   it("beforeDestroy is only called once even if ngOnDestroy is called twice", () => {
      const cleanup = jest.fn();
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
