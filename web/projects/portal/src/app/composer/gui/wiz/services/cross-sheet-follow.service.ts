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
import { take } from "rxjs/operators";
import { StompClientConnection } from "../../../../../../../shared/stomp/stomp-client-connection";
import { ViewsheetClientService } from "../../../../common/viewsheet-client";

/**
 * Client-side half of Lane C / D9 (cross-sheet-follow) — design doc
 * `docs/superpowers/plans/2026-09-14-portal-session-pairing-design.md` section 7.
 *
 * <p>Mirrors {@link FollowFocusService}'s shape (opt-in toggle + fire-and-forget STOMP sends over
 * an already-live socket connection), but deliberately as **a single global flag, not one keyed
 * per `runtimeId`**: Follow Focus answers "should THIS runtime's pane movements be tracked",
 * which only ever makes sense per already-paired runtime; cross-sheet-follow answers "should the
 * agent's session move to wherever the human is currently looking, across ANY runtime" — a
 * property of the one directly-established (portal) session a browser tab can have live at a
 * time (design section 2's own assumption: at most one portal-derived session per socket), not of
 * any particular runtime. Section 7.7 item 4 explicitly leaves multiple browser tabs under the
 * same portal login unaddressed; this service inherits that same single-flag limitation.
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

   isEnabled(): boolean {
      return this.enabled;
   }

   /**
    * Turns cross-sheet-follow on/off and tells the server, over whichever socket connection the
    * toggle itself was flipped from (the connection actually in use by the directly-established
    * session the toggle handler on the server resolves by socket alone — see
    * `SheetSessionService.setCrossSheetFollow`'s own javadoc).
    */
   setEnabled(socketConnection: ViewsheetClientService, enabled: boolean): void {
      this.enabled = enabled;

      if(!socketConnection) {
         return;
      }

      socketConnection.whenConnected().pipe(take(1)).subscribe((conn: StompClientConnection) => {
         conn.send("/events/wiz/pairing/cross-sheet-follow", {}, JSON.stringify({ enabled }));
      });
   }

   /**
    * Reports the newly-focused sheet to the server -- a no-op (never sends anything) when
    * cross-sheet-follow is not enabled, so opening/switching sheets behaves exactly as it does
    * today for every session that never opted in (design section 7.2's own restriction).
    */
   reportCurrentFocus(runtimeId: string, sheetType: "WORKSHEET" | "VIEWSHEET",
                       socketConnection: ViewsheetClientService): void
   {
      if(!this.enabled || !runtimeId || !socketConnection) {
         return;
      }

      socketConnection.whenConnected().pipe(take(1)).subscribe((conn: StompClientConnection) => {
         conn.send("/events/wiz/pairing/current-focus", {},
                   JSON.stringify({ runtimeId, sheetType }));
      });
   }
}
