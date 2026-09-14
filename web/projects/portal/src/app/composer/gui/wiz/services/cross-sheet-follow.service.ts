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
import { Injectable } from "@angular/core";
import { StompClientConnection } from "../../../../../../../shared/stomp/stomp-client-connection";
import { StompClientService } from "../../../../../../../shared/stomp/stomp-client.service";

/**
 * Client-side half of Lane C / D9 (cross-sheet-follow) — design doc
 * `docs/superpowers/plans/2026-09-14-portal-session-pairing-design.md` section 7.
 *
 * <p>Mirrors {@link FollowFocusService}'s shape (opt-in toggle + fire-and-forget STOMP sends), but
 * deliberately as **a single global flag, not one keyed per `runtimeId`**: Follow Focus answers
 * "should THIS runtime's pane movements be tracked", which only ever makes sense per already-paired
 * runtime; cross-sheet-follow answers "should the agent's session move to wherever the human is
 * currently looking, across ANY runtime" — a property of the one directly-established (portal)
 * session a browser tab can have live at a time (design section 2's own assumption: at most one
 * portal-derived session per socket), not of any particular runtime. Section 7.7 item 4 explicitly
 * leaves multiple browser tabs under the same portal login unaddressed; this service inherits that
 * same single-flag limitation.
 *
 * <p><b>Owns its OWN STOMP connection, unlike {@link FollowFocusService}</b> (which is always
 * handed a caller's `ViewsheetClientService`, since it only ever acts within an open Composer
 * pane/sheet). This service's toggle is meant to be flippable from the PORTAL SHELL, outside
 * Composer entirely — Lane B's `portal-agent-notice` component/service, mounted in
 * `app.component.ts` alongside `<notifications>` (see this lane's own build log, item 10), where
 * no `ViewsheetClientService` (itself scoped to a single open runtime viewsheet) exists to borrow.
 * Connecting to `"../vs-events"` directly via the injected {@link StompClientService}, exactly
 * mirroring `AppComponent`'s own already-verified precedent (see `04-build-B.md`'s DI-scope spike:
 * `StompClient.connect` is reference-counted per endpoint, so an independent caller here reuses
 * whichever connection is already open rather than opening a second WebSocket) is what lets both
 * the toggle (fired from the portal shell) and the current-focus report (fired from inside
 * Composer, see `reportCurrentFocus`) share one physical connection with everything else on the
 * page, with neither needing the other's component-scoped service.
 *
 * <p>Two responsibilities:
 * - the toggle itself (`setEnabled`), sent to `/wiz/pairing/cross-sheet-follow` (see
 *   `SheetPairingController.crossSheetFollowViaSocket`) — this lane's own toggle UI surface lives
 *   on Lane B's `portal-agent-notice` component/service (see `04-build-C.md`'s own note on why,
 *   not on this file's caller).
 * - the current-focus report (`reportCurrentFocus`), sent to `/wiz/pairing/current-focus` (see
 *   `SheetPairingController.currentFocusViaSocket`) whenever `composer-main.component.ts`'s
 *   `updateFocusedSheet` chokepoint fires — a no-op unless this service is currently enabled, so
 *   the overwhelming majority of Composer users who never touch portal-level pairing see zero
 *   added STOMP traffic from this feature at all.
 *
 * <p>Never persisted (no storage read/write anywhere in this service, same as
 * {@link FollowFocusService}): a fresh page load starts back at disabled, matching the server's
 * own `crossSheetFollowEnabled` default of `false` until explicitly re-toggled.
 */
@Injectable({
   providedIn: "root"
})
export class CrossSheetFollowService {
   private enabled = false;
   private connection: StompClientConnection | null = null;

   constructor(private stompClient: StompClientService) {}

   isEnabled(): boolean {
      return this.enabled;
   }

   /**
    * Turns cross-sheet-follow on/off and tells the server. Callable from anywhere in the portal
    * (the toggle's own UI surface, per this lane's design, lives outside Composer entirely) --
    * unlike {@link FollowFocusService#setEnabled}, this takes no `socketConnection` parameter,
    * since it manages its own (see this class's own doc).
    */
   setEnabled(enabled: boolean): void {
      this.enabled = enabled;

      this.withConnection((conn: StompClientConnection) => {
         conn.send("/events/wiz/pairing/cross-sheet-follow", {}, JSON.stringify({ enabled }));
      });
   }

   /**
    * Reports the newly-focused sheet to the server -- a no-op (never sends anything, never even
    * opens a connection) when cross-sheet-follow is not enabled, so opening/switching sheets
    * behaves exactly as it does today for every session that never opted in (design section 7.2's
    * own restriction).
    */
   reportCurrentFocus(runtimeId: string, sheetType: "WORKSHEET" | "VIEWSHEET"): void {
      if(!this.enabled || !runtimeId) {
         return;
      }

      this.withConnection((conn: StompClientConnection) => {
         conn.send("/events/wiz/pairing/current-focus", {},
                   JSON.stringify({ runtimeId, sheetType }));
      });
   }

   /**
    * Lazily connects to the shared `"../vs-events"` endpoint (same literal `AppComponent` and
    * `ComposerClientService` already use) and caches the resulting connection for every
    * subsequent call -- a genuine cache, not a per-call `whenConnected()` wait, since this
    * service, unlike `FollowFocusService`, is never handed an already-connected instance by its
    * caller and would otherwise reopen the connect race on every single toggle/report.
    */
   private withConnection(fn: (conn: StompClientConnection) => void): void {
      if(this.connection) {
         fn(this.connection);
         return;
      }

      this.stompClient.connect("../vs-events").subscribe((conn: StompClientConnection) => {
         this.connection = conn;
         fn(conn);
      });
   }
}
