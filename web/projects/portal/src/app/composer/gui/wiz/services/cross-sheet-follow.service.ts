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
import { HttpClient } from "@angular/common/http";
import { Injectable, NgZone } from "@angular/core";
import { Observable, Subject, Subscription } from "rxjs";
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
 * <p>Not persisted client-side (no storage read/write anywhere in this service) -- but, since
 * PSP-028, this service's constructor DOES seed its own `enabled` flag from whatever a directly-
 * established session for this identity already has server-side ({@code GET
 * /api/wiz/pairing/cross-sheet-follow}, mirroring {@code WizService}'s own identical
 * constructor-time-GET convention for `/api/wiz/pairing/feature`). This is load-bearing, not
 * cosmetic: the portal shell and the Composer app are two separate Angular bootstraps (see this
 * class's own doc above), each with its OWN root-scoped instance of this
 * `providedIn: "root"` service. Without this seed, toggling the checkbox on in the portal shell's
 * instance would have no way to ever reach the Composer app's own separate instance, whose
 * `reportCurrentFocus` is the one that actually matters for sending focus reports -- exactly the
 * gap PSP-028's tester report reproduced (toggle appeared to succeed in the portal tab, then
 * switching sheets in the Composer app never retargeted the agent's session).
 */
@Injectable({
   providedIn: "root"
})
export class CrossSheetFollowService {
   private enabled = false;
   private connection: StompClientConnection | null = null;
   private errorSubject = new Subject<string>();

   /**
    * FIFO queue of `enabled` values sent to the server but not yet confirmed, in send order.
    *
    * Needed because {@link StompClientChannel#subscribe} multiplexes every subscriber on a
    * destination through one shared `Subject` with no per-message correlation id (see its own
    * doc): opening a SECOND subscription on `/user/commands/wiz/pairing/cross-sheet-follow` for
    * an overlapping {@link #setEnabled} call meant the first reply that arrived fired BOTH
    * subscriptions' callbacks at once (each `sub.unsubscribe()`-ing itself and applying its OWN
    * requested value as if it were that reply's outcome), after which the second call's real
    * reply arrived to no subscriber left and was silently dropped -- leaving {@link #enabled}
    * stuck on whichever call's callback happened to run last, not on the server's actual final
    * state. A single standing subscription plus this queue instead pairs the Nth reply on this
    * destination with the Nth request sent, relying only on the same in-order delivery guarantee
    * a single STOMP/WebSocket connection already gives every other request/reply pair in this
    * codebase (see `ConnectToClaudeComponent`'s mint/joined handling) -- not on any new server
    * contract; the server's `CrossSheetFollowResponse` still carries no id to correlate by.
    */
   private pendingRequests: boolean[] = [];
   private replySubscription: Subscription | null = null;

   constructor(private stompClient: StompClientService, private http: HttpClient,
               private zone: NgZone)
   {
      this.http.get<{enabled: boolean}>("../api/wiz/pairing/cross-sheet-follow").subscribe({
         next: (res) => this.enabled = res.enabled,
         error: () => this.enabled = false
      });
   }

   isEnabled(): boolean {
      return this.enabled;
   }

   /**
    * Surfaces a server-side rejection of {@link #setEnabled} (e.g. no directly-established
    * session held for this connection) for a host UI to show -- mirrors
    * {@link FollowFocusService#errors}'s shape.
    */
   get errors(): Observable<string> {
      return this.errorSubject.asObservable();
   }

   /**
    * Whether a {@link #setEnabled} call is still waiting on the server. A host UI (the portal
    * shell's checkbox, see `portal-agent-notice.component`) binds this to `[disabled]` so a
    * second toggle cannot fire while one is in flight -- defense-in-depth for the common case;
    * {@link #pendingRequests}'s queue is what actually keeps an overlapping call from corrupting
    * another's result if one slips through anyway.
    */
   get pending(): boolean {
      return this.pendingRequests.length > 0;
   }

   /**
    * Turns cross-sheet-follow on/off and tells the server. Callable from anywhere in the portal
    * (the toggle's own UI surface, per this lane's design, lives outside Composer entirely) --
    * unlike {@link FollowFocusService#setEnabled}, this takes no `socketConnection` parameter,
    * since it manages its own (see this class's own doc).
    *
    * <p>Does NOT flip {@link #enabled} until the server confirms success (PSP-028 / PSP-025's own
    * established principle: a toggle must never look like it succeeded when it didn't). Waits for
    * the {@code CrossSheetFollowResponse} on {@code /user/commands/wiz/pairing/cross-sheet-follow}
    * before updating local state, exactly mirroring `ConnectToClaudeComponent`'s own
    * subscribe-then-send pattern for the sibling `/user/commands/wiz/pairing/mint` reply.
    */
   setEnabled(enabled: boolean): void {
      this.withConnection((conn: StompClientConnection) => {
         this.pendingRequests.push(enabled);

         if(!this.replySubscription) {
            this.replySubscription = conn.subscribe(
               "/user/commands/wiz/pairing/cross-sheet-follow", (msg: any) => this.onReply(msg));
         }

         conn.send("/events/wiz/pairing/cross-sheet-follow", {}, JSON.stringify({ enabled }));
      });
   }

   /**
    * Handles one reply on the shared `/user/commands/wiz/pairing/cross-sheet-follow` destination
    * -- see {@link #pendingRequests}'s doc for why this pops the OLDEST still-pending request
    * rather than trusting whichever `setEnabled` call happens to still be subscribed.
    */
   private onReply(msg: any): void {
      if(this.pendingRequests.length === 0) {
         // A reply with nothing recorded as pending should not happen given the FIFO invariant
         // above; ignore rather than apply an unknown value.
         return;
      }

      const requestedEnabled = this.pendingRequests.shift()!;

      this.zone.run(() => {
         let body: any;

         try {
            body = JSON.parse(msg.frame.body);
         }
         catch(e) {
            this.reportFailure(requestedEnabled, "could not parse server response");
            this.teardownReplySubscriptionIfIdle();
            return;
         }

         if(body.ok) {
            this.enabled = requestedEnabled;
         }
         else {
            this.reportFailure(requestedEnabled, body.error ?? "unknown error");
         }

         this.teardownReplySubscriptionIfIdle();
      });
   }

   private teardownReplySubscriptionIfIdle(): void {
      if(this.pendingRequests.length === 0 && this.replySubscription) {
         this.replySubscription.unsubscribe();
         this.replySubscription = null;
      }
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

   private reportFailure(enabled: boolean, reason: string): void {
      const message = `Cross-sheet-follow ${enabled ? "enable" : "disable"} failed: ${reason}`;
      // eslint-disable-next-line no-console
      console.error(message);
      this.errorSubject.next(message);
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
