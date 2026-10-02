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
import { Subject } from "rxjs";
import { StompClient } from "./stomp-client";

// Bug #77220: the websocket close code decides what the page does when the HTTP session ends.
describe("StompClient close codes", () => {
   let ws: any;
   let logoutService: any;
   let reload: any;
   let originalLocation: Location;

   function createClient(customElement: boolean = false): StompClient {
      return new StompClient("../vs-events", vi.fn(), vi.fn(), {} as any, logoutService,
                             false, "", customElement, new Subject<void>());
   }

   function close(code: number): void {
      ws.onclose({ code });
   }

   beforeEach(() => {
      ws = { onclose: vi.fn() };
      const stompClient: any = {
         ws,
         connect: vi.fn((headers: any, onConnect: () => void) => onConnect())
      };
      (globalThis as any).Stomp = { over: vi.fn(() => stompClient) };
      logoutService = { logout: vi.fn(), sessionExpired: vi.fn() };
      reload = vi.fn();
      originalLocation = window.location;
      Object.defineProperty(window, "location", {
         configurable: true,
         value: { ...originalLocation, href: originalLocation.href, reload }
      });
      window.sessionStorage.clear();
   });

   afterEach(() => {
      Object.defineProperty(window, "location", { configurable: true, value: originalLocation });
      delete (globalThis as any).Stomp;
      window.sessionStorage.clear();
   });

   it("redirects to the logout page on 4001", () => {
      createClient();
      close(4001);
      expect(logoutService.logout).toHaveBeenCalledWith(true, false);
      expect(reload).not.toHaveBeenCalled();
   });

   it("redirects to the session expired page on 4002", () => {
      createClient();
      close(4002);
      expect(logoutService.sessionExpired).toHaveBeenCalled();
      expect(reload).not.toHaveBeenCalled();
   });

   it("reloads the page on 4003 so a new guest session is created", async () => {
      vi.resetModules();
      const { StompClient: FreshStompClient } = await import("./stomp-client");
      new FreshStompClient("../vs-events", vi.fn(), vi.fn(), {} as any, logoutService,
                           false, "", false, new Subject<void>());
      close(4003);
      close(4003);
      expect(reload).toHaveBeenCalledTimes(1);
      expect(logoutService.logout).not.toHaveBeenCalled();
      expect(logoutService.sessionExpired).not.toHaveBeenCalled();
   });

   it("does not reload on 4003 again right after a guest reload", async () => {
      vi.resetModules();
      window.sessionStorage.setItem("inetsoftGuestSessionReload", `${Date.now()}`);
      const { StompClient: FreshStompClient } = await import("./stomp-client");
      new FreshStompClient("../vs-events", vi.fn(), vi.fn(), {} as any, logoutService,
                           false, "", false, new Subject<void>());
      close(4003);
      expect(reload).not.toHaveBeenCalled();
   });

   it("does not reload an embedding host page on 4003", () => {
      createClient(true);
      close(4003);
      expect(reload).not.toHaveBeenCalled();
   });

   it("does not reload on a normal close", () => {
      createClient();
      close(1000);
      expect(reload).not.toHaveBeenCalled();
      expect(logoutService.logout).not.toHaveBeenCalled();
      expect(logoutService.sessionExpired).not.toHaveBeenCalled();
   });
});
