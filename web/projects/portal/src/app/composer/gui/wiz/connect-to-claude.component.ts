/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
import { Component, Input, NgZone, OnChanges, OnDestroy, OnInit, SimpleChanges } from "@angular/core";
import { NgIf } from "@angular/common";
import { ClipboardModule } from "ngx-clipboard";
import { Subscription } from "rxjs";
import { take } from "rxjs/operators";
import { StompClientConnection } from "../../../../../../shared/stomp/stomp-client-connection";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { EditorContext } from "./editor-context";
import { FollowFocusService } from "./services/follow-focus.service";
import { FormsModule } from "@angular/forms";

@Component({
   selector: "wiz-connect-to-claude",
   templateUrl: "./connect-to-claude.component.html",
   standalone: true,
   imports: [NgIf, ClipboardModule, FormsModule]
})
export class ConnectToClaudeComponent implements OnInit, OnChanges, OnDestroy {
   @Input() runtimeId!: string;
   @Input() sheetType!: "WORKSHEET" | "VIEWSHEET";
   @Input() socketConnection!: ViewsheetClientService;
   /** Scopes the pairing session to a single script/formula location rather
    *  than the whole sheet. Omitted from the mint payload entirely (not sent
    *  as null) when absent, e.g. a whole-sheet toolbar mint. */
   @Input() editorContext?: EditorContext;

   code: string | null = null;
   loading = false;
   error: string | null = null;
   copied = false;

   /**
    * An agent has redeemed a code minted from this exact component instance.
    *
    * One-shot by design: this says a pairing happened, not that one is still live. Tracking
    * liveness would mean reacting to a TTL that expires with no server-side event, which the
    * design spec records as deliberately out of scope.
    */
   connected = false;

   private mintSubscription: Subscription | null = null;
   private joinedSubscription: Subscription | null = null;
   /**
    * The outer `whenConnected()` subscription that `ngOnInit` uses to obtain the STOMP
    * connection. `take(1)` normally completes it on its own, but a component created before the
    * socket connects and destroyed before it does would otherwise leave this callback pending --
    * it would later open `joinedSubscription` on behalf of a component that no longer exists.
    */
   private connectSubscription: Subscription | null = null;
   /**
    * The `editorContext` that was actually SENT with the mint that produced `code` -- captured
    * when the code comes back, and what `detach()` sends.
    *
    * Whole-branch review finding 2: `detach()` used to send `this.editorContext`, the CURRENT
    * value, and the server matches a session by record equality on that field. Several hosts
    * derive `editorContext` from mutable state -- `viewsheet-script-pane` follows the
    * onInit/onLoad radio, `formula-editor-dialog` includes the editable `formulaName` -- so a
    * user who pairs on Init, clicks Load to compare, then cancels detached `viewsheetOnLoad`,
    * matched nothing, and left the `viewsheetOnInit` session live for its full TTL with the
    * editor gone. `socketClosed` does not help: the composer socket is still up.
    *
    * Null until a code has been minted; `detach()` then falls back to the current value, which is
    * correct because with no minted code there is no session of ours to end.
    */
   private mintedEditorContext: EditorContext | null = null;

   /**
    * The session's LIVE target, tracked only on the whole-sheet (toolbar) instance --
    * {@code mintedEditorContext === null} -- since that is the only instance Follow Focus ever
    * retargets (a session must opt in, and only a whole-sheet session is ever pushed onto a
    * pane; see the design spec). {@code null} means "whole sheet," the same meaning
    * {@code editorContext}/{@code mintedEditorContext} give it elsewhere in this component.
    *
    * Set from the initial join notice and from every subsequent `focusChanged` notice
    * ({@link FollowFocusService}'s push/pop) -- see {@link #onJoined}.
    */
   currentTarget: EditorContext | null = null;

   /**
    * Whether this instance has an outstanding mint of its own.
    *
    * `mintedEditorContext === null` cannot answer that: it means both "I minted a whole-sheet
    * code" and "I never minted anything". Since a toolbar notice carries `editorContext: null`,
    * without this flag any instance that never minted -- a formula editor opened while a toolbar
    * code was still outstanding -- would match that notice and claim a pairing that was not its
    * own.
    */
   private hasMinted = false;

   constructor(private zone: NgZone, private followFocusService: FollowFocusService) {}

   /**
    * Whether the Follow Focus toggle should render at all -- only once connected, and only on
    * the whole-sheet (toolbar) instance. A pane's own {@code ConnectToClaudeComponent} has
    * nothing meaningful to toggle: Follow Focus opts in the OUTER session, not the pane session
    * itself (see the design spec's "what this changes" section).
    */
   get followFocusToggleVisible(): boolean {
      return this.connected && !this.editorContext;
   }

   get followFocusEnabled(): boolean {
      return this.followFocusService.isEnabled(this.runtimeId);
   }

   toggleFollowFocus(event: Event): void {
      const enabled = (event.target as HTMLInputElement).checked;
      this.followFocusService.setEnabled(this.runtimeId, this.socketConnection, enabled);
   }

   /**
    * Human-readable label for {@link currentTarget}, e.g. "Whole sheet" or "Chart1 → onClick" --
    * the exact shape the design spec's indicator calls for.
    */
   get currentTargetLabel(): string {
      if(!this.currentTarget) {
         return "Whole sheet";
      }

      return this.currentTarget.assembly
         ? `${this.currentTarget.assembly} → ${this.currentTarget.kind}`
         : this.currentTarget.kind;
   }

   /**
    * Opens the standing subscription for join notices.
    *
    * Standing, not per-mint: the agent may redeem the code seconds or minutes after it is
    * displayed, long after the mint round-trip has completed and unsubscribed itself.
    */
   ngOnInit(): void {
      if(!this.socketConnection) {
         return;
      }

      this.connectSubscription = this.socketConnection.whenConnected().pipe(take(1)).subscribe(
         (conn: StompClientConnection) => {
            this.joinedSubscription = conn.subscribe(
               "/user/commands/wiz/pairing/joined", (msg: any) => this.onJoined(msg));
         });
   }

   ngOnChanges(changes: SimpleChanges): void {
      if(changes["runtimeId"] && !changes["runtimeId"].firstChange) {
         this.code = null;
         this.error = null;
         this.loading = false;
         this.copied = false;
         this.connected = false;
         this.mintedEditorContext = null;
         this.hasMinted = false;
         this.currentTarget = null;
         if(this.mintSubscription) {
            this.mintSubscription.unsubscribe();
            this.mintSubscription = null;
         }
      }
   }

   requestCode(): void {
      this.loading = true;
      this.code = null;
      this.error = null;
      this.copied = false;
      this.connected = false;
      this.hasMinted = false;
      this.currentTarget = null;

      // Read ONCE, here, and carry this exact value through to the response handler: the getters
      // that supply it are live, so re-reading it later (in detach, or even in this same
      // subscribe callback) can yield a context the server never saw. See mintedEditorContext.
      const requestedContext = this.editorContext;

      this.socketConnection.whenConnected().pipe(take(1)).subscribe((conn: StompClientConnection) => {
         const sub = conn.subscribe("/user/commands/wiz/pairing/mint", (msg: any) => {
            sub.unsubscribe();
            this.mintSubscription = null;
            this.zone.run(() => {
               this.loading = false;

               try {
                  const body = JSON.parse(msg.frame.body);

                  if(body.code) {
                     this.code = body.code;
                     this.mintedEditorContext = requestedContext ?? null;
                     this.hasMinted = true;
                  }
                  else {
                     this.error = body.error ?? "Failed to generate pairing code";
                  }
               }
               catch(e) {
                  this.error = "Failed to generate pairing code";
               }
            });
         });
         this.mintSubscription = sub;

         const payload: any = { runtimeId: this.runtimeId, sheetType: this.sheetType };

         if(requestedContext) {
            payload.editorContext = requestedContext;
         }

         conn.send("/events/wiz/pairing/mint", {}, JSON.stringify(payload));
      });
   }

   /**
    * Accepts a join notice only when this instance minted the code AND it is for this sheet AND
    * this location.
    *
    * The destination is per socket session, not per sheet and not per pane, so every instance on
    * the page receives every notice. This component is reused by the composer toolbar, the
    * viewsheet script pane and the formula editor dialog, so more than one instance is routinely
    * alive at once. Dropping any of these filters makes an instance announce a pairing that did
    * not happen to it — a false statement, which is worse than the missing indicator this whole
    * change exists to fix.
    */
   private onJoined(msg: any): void {
      let body: any;

      try {
         body = JSON.parse(msg.frame.body);
      }
      catch(e) {
         return;
      }

      if(!this.hasMinted || body.runtimeId !== this.runtimeId) {
         return;
      }

      /*
       * Follow Focus retargeted/popped an already-connected session -- a DIFFERENT event from a
       * fresh mint being redeemed, and one the strict editorContext match below cannot handle:
       * the whole point of a retarget is that editorContext no longer matches what THIS instance
       * originally minted. Only the whole-sheet (toolbar) instance tracks this live, since that
       * is the only kind of session Follow Focus ever retargets (see currentTarget's doc).
       */
      if(body.focusChanged) {
         if(this.mintedEditorContext !== null) {
            return;
         }

         this.zone.run(() => {
            this.currentTarget = body.editorContext ?? null;
         });
         return;
      }

      if(!this.sameEditorContext(body.editorContext, this.mintedEditorContext)) {
         return;
      }

      this.zone.run(() => {
         this.connected = true;
         // The code is single-use and has now been used. Leaving it on screen invites an attempt
         // to redeem it again.
         this.code = null;
         this.error = null;

         // currentTarget is meaningful only on the whole-sheet (toolbar) instance -- a pane
         // instance's own join already IS its target, with no further movement to track.
         if(this.mintedEditorContext === null) {
            this.currentTarget = body.editorContext ?? null;
         }
      });
   }

   /**
    * Compares two editor contexts, treating an absent field and an explicit null as equal.
    *
    * Required, not defensive: the server sends a Java record, and Jackson writes its unset
    * components as explicit nulls, while the browser omits those keys from the mint payload
    * entirely. A structural comparison (JSON.stringify, or === per key) would therefore never
    * match a pane pairing, and the indicator would never appear for one.
    */
   private sameEditorContext(a: EditorContext | null | undefined,
                             b: EditorContext | null | undefined): boolean {
      if(!a && !b) {
         return true;
      }

      if(!a || !b) {
         return false;
      }

      return a.kind === b.kind &&
         (a.assembly ?? null) === (b.assembly ?? null) &&
         (a.name ?? null) === (b.name ?? null) &&
         (a.table ?? null) === (b.table ?? null);
   }

   onCopySuccess(): void {
      this.copied = true;
      setTimeout(() => {
         this.zone.run(() => {
            this.copied = false;
         });
      }, 2000);
   }

   onCopyError(): void {
      this.error = "Could not copy to clipboard — please copy the code manually.";
   }

   /**
    * Ends any pane-scoped session paired from this exact location. Called explicitly by the
    * script pane / formula editor that hosts this component from their own `ngOnDestroy`, so a
    * pane session dies with its editor -- a user who pairs then cancels or closes the dialog
    * does not leave a live write handle behind.
    *
    * A whole-sheet ("Connect to Claude" toolbar) mint has no `editorContext` and must never
    * reach this endpoint -- its session is TTL-only by design, so this is a no-op there.
    */
   detach(): void {
      // The context the session was MINTED with, not whatever the host's live getter says now --
      // see mintedEditorContext. Falls back to the current value only when nothing was ever
      // minted from this component.
      const context = this.mintedEditorContext ?? this.editorContext;

      if(!context || !this.socketConnection) {
         return;
      }

      this.socketConnection.whenConnected().pipe(take(1)).subscribe((conn: StompClientConnection) => {
         conn.send("/events/wiz/pairing/detach", {},
                   JSON.stringify({ editorContext: context }));
      });
   }

   ngOnDestroy(): void {
      if(this.mintSubscription) {
         this.mintSubscription.unsubscribe();
         this.mintSubscription = null;
      }

      // Releases the outer whenConnected() wait itself, not just what it produces -- otherwise a
      // component destroyed before the socket connects still has its callback fire later and open
      // joinedSubscription on behalf of a component that is already gone.
      if(this.connectSubscription) {
         this.connectSubscription.unsubscribe();
         this.connectSubscription = null;
      }

      // The formula editor dialog creates and destroys this component repeatedly; a standing
      // subscription that outlives its component is a leak.
      if(this.joinedSubscription) {
         this.joinedSubscription.unsubscribe();
         this.joinedSubscription = null;
      }
   }
}
