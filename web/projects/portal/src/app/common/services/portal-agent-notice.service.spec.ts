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
import { of } from "rxjs";
import { PortalAgentNoticeService } from "./portal-agent-notice.service";

describe("PortalAgentNoticeService", () => {
   let service: PortalAgentNoticeService;
   let mockConnection: { subscribe: ReturnType<typeof vi.fn> };
   let mockStompClient: { connect: ReturnType<typeof vi.fn> };
   let mockZone: { run: (fn: () => void) => void };

   function notice(body: any): any {
      return { frame: { body: JSON.stringify(body) } };
   }

   function joinedHandler(): (msg: any) => void {
      const call = mockConnection.subscribe.mock.calls
         .filter((c: any[]) => c[0] === "/user/composer-client").pop();
      expect(call).toBeTruthy();
      return call[1];
   }

   beforeEach(() => {
      mockConnection = { subscribe: vi.fn() };
      mockStompClient = { connect: vi.fn(() => of(mockConnection)) };
      mockZone = { run: (fn: () => void) => fn() };
      service = new PortalAgentNoticeService(mockStompClient as any, mockZone as any);
   });

   describe("portalSessionActive", () => {
      it("is false before connect() has even been called", () => {
         expect(service.portalSessionActive).toBe(false);
      });

      it("is still false right after connect(), before any notice arrives", () => {
         service.connect();

         expect(service.portalSessionActive).toBe(false);
      });

      it("becomes true on the first assetOpened notice", () => {
         service.connect();
         joinedHandler()(notice({ assetId: "1^128^__NULL__^ws1", viewsheet: false }));

         expect(service.portalSessionActive).toBe(true);
      });

      // portal-session-pairing Lane C's own toggle needs a PERSISTENT signal (design section
      // 7.4) -- unlike `notice`, which auto-dismisses, this must never flip back to false once a
      // portal session has been observed, no matter what a later notice looks like.
      it("never clears again once a notice has set it, regardless of later notice shape", () => {
         service.connect();
         const handler = joinedHandler();

         handler(notice({ assetId: "1^128^__NULL__^ws1", viewsheet: false }));
         expect(service.portalSessionActive).toBe(true);

         handler(notice({ assetId: null, viewsheet: true }));
         expect(service.portalSessionActive).toBe(true);
      });
   });

   describe("assetOpened", () => {
      it("still emits the parsed command as before, unaffected by portalSessionActive", () => {
         const commands: any[] = [];
         service.assetOpened.subscribe(c => commands.push(c));
         service.connect();

         joinedHandler()(notice({ assetId: "1^128^__NULL__^ws1", viewsheet: false }));

         expect(commands).toEqual([{ assetId: "1^128^__NULL__^ws1", viewsheet: false }]);
      });
   });
});
