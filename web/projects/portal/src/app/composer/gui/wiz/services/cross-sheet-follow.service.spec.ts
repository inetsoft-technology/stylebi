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

/*
 * NOTE ON RUNNING THIS FILE: this worktree has no `node_modules` installed (no TS test harness
 * available here -- see the original PSP-028 fixer's own note), so this spec could not actually
 * be executed as part of writing it. It follows this codebase's existing Vitest + Angular
 * `TestBed` conventions (see e.g. `connect-to-claude.component.spec.ts`,
 * `stomp-client-connection.spec.ts`) and should run once dependencies are available via:
 *   cd web && npx vitest run projects/portal/src/app/composer/gui/wiz/services/cross-sheet-follow.service.spec.ts
 * (or the project's normal `npm run test:portal`).
 */

import { HttpClient } from "@angular/common/http";
import { TestBed } from "@angular/core/testing";
import { of, Subscription } from "rxjs";
import { StompClientService } from "../../../../../../../shared/stomp/stomp-client.service";
import { CrossSheetFollowService } from "./cross-sheet-follow.service";

/**
 * Emulates {@link StompClientChannel}'s REAL multiplexing behaviour (see its own doc / this
 * service's own `pendingRequests` doc): every subscriber on a destination is registered in a
 * shared set, and every message sent to that destination is delivered to ALL of them, in the
 * same dispatch, until they unsubscribe -- there is no per-message correlation id anywhere in
 * the wire protocol this service uses. A naive mock that hands each `conn.subscribe(...)` call
 * its own private, uncorrelated handler would NOT reproduce PSP-028's follow-up bug at all -- it
 * has to fan every reply out to every currently-subscribed handler, exactly like the real channel
 * does, for these tests to mean anything.
 */
function makeMultiplexingConnection() {
   const handlers = new Map<string, Set<(msg: any) => void>>();
   const sent: Array<{ destination: string; body: string }> = [];

   const connection = {
      subscribe: (destination: string, next: (msg: any) => void): Subscription => {
         let set = handlers.get(destination);

         if(!set) {
            set = new Set();
            handlers.set(destination, set);
         }

         set.add(next);

         return new Subscription(() => set!.delete(next));
      },
      send: (destination: string, _headers: any, body: string): void => {
         sent.push({ destination, body });
      }
   };

   function deliver(destination: string, body: string): void {
      const set = handlers.get(destination);

      if(!set) {
         return;
      }

      // Snapshot before dispatching: rxjs's real Subject iterates its CURRENT observer list for
      // one `next()` call, so a handler that unsubscribes itself mid-dispatch must not affect
      // whether ITS OWN OWN call still receives this same message, matching real Subject.next
      // semantics (and this service's own `sub.unsubscribe()`-first-thing pattern, pre-fix).
      Array.from(set).forEach((handler) => handler({ frame: { body } }));
   }

   return { connection, sent, deliver };
}

describe("CrossSheetFollowService", () => {
   let service: CrossSheetFollowService;
   let mockHttp: { get: ReturnType<typeof vi.fn> };
   let mockStompClient: { connect: ReturnType<typeof vi.fn> };
   let conn: ReturnType<typeof makeMultiplexingConnection>;

   const DESTINATION = "/user/commands/wiz/pairing/cross-sheet-follow";

   beforeEach(() => {
      conn = makeMultiplexingConnection();
      mockHttp = { get: vi.fn(() => of({ enabled: false })) };
      mockStompClient = { connect: vi.fn(() => of(conn.connection)) };

      TestBed.configureTestingModule({
         providers: [
            CrossSheetFollowService,
            { provide: HttpClient, useValue: mockHttp },
            { provide: StompClientService, useValue: mockStompClient }
         ]
      });

      service = TestBed.inject(CrossSheetFollowService);
   });

   it("ends up OFF, not stuck ON, when an ON call is immediately followed by an OFF call before " +
      "either reply arrives (PSP-028 follow-up / claude-review finding on PR #5281)", () => {
      service.setEnabled(true);
      service.setEnabled(false);

      expect(conn.sent.length).toBe(2);
      expect(service.pending).toBe(true);

      // Replies arrive in the order the requests were sent -- the one ordering guarantee a
      // single STOMP/WebSocket connection actually gives (see this service's own doc).
      conn.deliver(DESTINATION, JSON.stringify({ ok: true }));
      conn.deliver(DESTINATION, JSON.stringify({ ok: true }));

      // Pre-fix, both replies were multiplexed to whichever subscription(s) were still open:
      // the FIRST reply fired both the ON call's and the OFF call's callbacks at once (each
      // applying its own requested value), and the OFF call's real reply then found no
      // subscriber left and was silently dropped -- leaving `enabled` stuck on whatever the
      // callback ordering happened to produce, not on the actually-last-requested value.
      expect(service.isEnabled()).toBe(false);
      expect(service.pending).toBe(false);
   });

   it("settles on the true last request across a rapid on/off/on sequence", () => {
      service.setEnabled(true);
      service.setEnabled(false);
      service.setEnabled(true);

      expect(conn.sent.length).toBe(3);

      conn.deliver(DESTINATION, JSON.stringify({ ok: true }));
      conn.deliver(DESTINATION, JSON.stringify({ ok: true }));
      conn.deliver(DESTINATION, JSON.stringify({ ok: true }));

      expect(service.isEnabled()).toBe(true);
      expect(service.pending).toBe(false);
   });

   it("keeps an earlier call's own eventual outcome from leaking onto a later, still-pending call", () => {
      service.setEnabled(true);
      service.setEnabled(false);

      // Only the FIRST (ON) call's reply has arrived -- the second (OFF) call must still be
      // reported pending, and must not have been resolved by the first reply.
      conn.deliver(DESTINATION, JSON.stringify({ ok: true }));

      expect(service.isEnabled()).toBe(true);
      expect(service.pending).toBe(true);

      // The second call's own reply now arrives and reports a server-side rejection.
      conn.deliver(DESTINATION, JSON.stringify({ ok: false, error: "no session" }));

      expect(service.isEnabled()).toBe(true); // unchanged: the OFF request was rejected
      expect(service.pending).toBe(false);
   });

   it("pending is false once no call is in flight, and true only while one is", () => {
      expect(service.pending).toBe(false);

      service.setEnabled(true);
      expect(service.pending).toBe(true);

      conn.deliver(DESTINATION, JSON.stringify({ ok: true }));
      expect(service.pending).toBe(false);
   });
});
