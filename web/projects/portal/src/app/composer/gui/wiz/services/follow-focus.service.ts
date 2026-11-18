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
import { Observable, Subject } from "rxjs";
import { take } from "rxjs/operators";
import { StompClientConnection } from "../../../../../../../shared/stomp/stomp-client-connection";
import { ViewsheetClientService } from "../../../../common/viewsheet-client";
import { EditorContext } from "../editor-context";

/**
 * Per-runtime Follow Focus state: whether the human has opted in, and the client's own shadow
 * of "which panes are currently pushed," most-recently-opened last.
 *
 * Deliberately per-`runtimeId`, not a single global flag -- see the design spec's question 1
 * ("per-session ... rather than a sticky global preference"). Never persisted (no storage
 * read/write anywhere in this service): a fresh page load or a fresh pairing starts back at
 * `enabled: false`, by design.
 */
interface FollowFocusState {
   enabled: boolean;
   stack: EditorContext[];
   /** The connection the flag was last sent over, so unticking elsewhere can turn it off here. */
   socketConnection?: ViewsheetClientService;
}

/**
 * Client-side half of Follow Focus (spec: `2026-08-19-follow-focus-pane-targeting-design.md`;
 * plan: `2026-08-19-follow-focus-pane-targeting-implementation.md`, Phase 2).
 *
 * Holds the opt-in toggle and talks to the Phase 1 STOMP endpoints that retarget an
 * *already-live* pairing session in place. This service never mints, joins, or detaches a
 * session itself -- `ConnectToClaudeComponent` still owns that lifecycle -- it only asks the
 * server to move an existing session's target, which is a strictly narrower operation.
 *
 * A single root-provided instance spans every `ScriptPane` / `FormulaEditorDialog` /
 * `ConnectToClaudeComponent` on the page, keyed internally by `runtimeId`, because the toggle
 * (surfaced on the toolbar's `ConnectToClaudeComponent`) and the push/pop calls (fired from the
 * script pane / formula editor host components) live on different component instances that do
 * not otherwise share state.
 *
 * Wire contract assumed against the Phase 1 plan (Phase 1 lands in a sibling worktree on
 * `feat/follow-focus-pane-targeting-java`, not present here):
 * - toggle: send `{runtimeId, enabled}` to `/events/wiz/pairing/follow-focus`, fire-and-forget
 *   (mirrors `detach`'s shape, per plan Phase 1 task 4).
 * - push: send `{runtimeId, editorContext}` to `/events/wiz/pairing/retarget`, fire-and-forget
 *   (mirrors `MintRequest`'s shape, per plan Phase 1 task 5).
 * - pop: send `{runtimeId}` to `/events/wiz/pairing/pop-focus`, fire-and-forget. The plan names
 *   the toggle and retarget destinations literally but only describes the pop endpoint's payload
 *   shape, not its literal path -- `/wiz/pairing/pop-focus` is this file's own choice, picked to
 *   mirror `follow-focus`'s kebab-case naming. Confirm this string against whatever
 *   `SheetPairingController` actually ships once Phase 1 lands; a mismatch here is a naming
 *   typo to fix, not a sign the contract itself is wrong.
 */
@Injectable({
   providedIn: "root"
})
export class FollowFocusService {
   private state = new Map<string, FollowFocusState>();
   private followTabs = false;
   private errorSubject = new Subject<string>();

   /**
    * Surfaces a client-side stack-integrity problem (plan Phase 2 task 4), intended for a host
    * UI -- `ConnectToClaudeComponent`'s indicator -- to show rather than let pass silently.
    *
    * <p><b>Not wired up to any UI yet.</b> Nothing in this codebase currently subscribes to this
    * Observable, so a mismatch reaches only the `console.error` this service also logs, not any
    * visible surface. Flagged by code review on stylebi#4683 so this doesn't read as
    * already-done; wiring a subscriber into `ConnectToClaudeComponent`'s indicator is a
    * follow-up, not part of this PR's Phase 3 scope.
    */
   get errors(): Observable<string> {
      return this.errorSubject.asObservable();
   }

   isEnabled(runtimeId: string): boolean {
      return !!runtimeId && !!this.state.get(runtimeId)?.enabled;
   }

   /**
    * Whether the Composer should switch to the tab of the sheet an agent just edited (Bug #80379).
    *
    * Composer-wide, unlike {@link isEnabled}: the checkbox is one control shown on whichever sheet
    * is focused, so a per-sheet flag would follow the agent from sheet A to B and then stop,
    * because B's own flag was never ticked. Set by the same checkbox as the per-runtime flag
    * (see {@link setEnabled}); browser-side only, since moving the user's own tab widens nothing
    * the agent is authorised to do, so the server need not know. Never persisted, like the rest
    * of Follow Focus.
    */
   isFollowTabsEnabled(): boolean {
      return this.followTabs;
   }

   /**
    * Turns Follow Focus on/off for this runtime and tells the server. Off is always safe to
    * request (a session that already has no pushed focus simply has nothing to pop later); on
    * only takes effect for the *next* pane opened -- an already-open pane does not retroactively
    * push (spec test 3).
    */
   setEnabled(runtimeId: string, socketConnection: ViewsheetClientService, enabled: boolean): void {
      if(!runtimeId || !socketConnection) {
         return;
      }

      const own = this.stateFor(runtimeId);
      own.enabled = enabled;
      own.socketConnection = socketConnection;
      // the same checkbox also turns tab following on/off for the whole Composer
      this.followTabs = enabled;

      if(!enabled) {
         // One box, so unticking it anywhere is an opt-out everywhere: switch off every other
         // sheet that was ticked (client flag and server session flag), or the box would read off
         // while another sheet's script/formula panes still retarget the agent (Bug #80379).
         this.state.forEach((other, otherId) => {
            if(otherId !== runtimeId && other.enabled) {
               other.enabled = false;
               this.sendEnabled(otherId, other.socketConnection, false);
            }
         });
      }

      this.sendEnabled(runtimeId, socketConnection, enabled);
   }

   private sendEnabled(runtimeId: string, socketConnection: ViewsheetClientService,
                       enabled: boolean): void
   {
      if(!socketConnection) {
         return;
      }

      socketConnection.whenConnected().pipe(take(1)).subscribe((conn: StompClientConnection) => {
         conn.send("/events/wiz/pairing/follow-focus", {},
                   JSON.stringify({ runtimeId, enabled }));
      });
   }

   /**
    * Pushes the session's target to `editorContext`, if Follow Focus is enabled for this
    * runtime. A no-op (never sends anything) when it is not -- opening a pane with Follow Focus
    * off must behave exactly as it does today (spec design question 2/3).
    *
    * Returns whether a push was actually issued, so the caller (a host component's lifecycle
    * hook) knows whether it owes a matching `popFocus` on close.
    */
   pushFocus(runtimeId: string, socketConnection: ViewsheetClientService,
             editorContext: EditorContext): boolean
   {
      if(!runtimeId || !socketConnection || !editorContext || !this.isEnabled(runtimeId)) {
         return false;
      }

      this.stateFor(runtimeId).stack.push(editorContext);

      socketConnection.whenConnected().pipe(take(1)).subscribe((conn: StompClientConnection) => {
         conn.send("/events/wiz/pairing/retarget", {},
                   JSON.stringify({ runtimeId, editorContext }));
      });

      return true;
   }

   /**
    * Tells the server the user flipped the radio of the dialog a session is paired from
    * (Script/onClick, onInit/onRefresh), so the session moves from `from` to `to` with it: the
    * paired location, `status` and the script tools' default target then name the script the user
    * is looking at. Fire-and-forget like the other pane events; the server refuses anything that
    * is not a switch between the two scripts of one dialog, and a call that finds no session at
    * `from` (another instance already moved it, or nothing is paired) changes nothing.
    *
    * A Follow Focus frame that holds `from` is rewritten to `to`, so the close-time integrity
    * check of `popFocus` still matches the location the pane is on now.
    */
   switchPane(runtimeId: string, socketConnection: ViewsheetClientService,
              from: EditorContext, to: EditorContext): void
   {
      if(!runtimeId || !socketConnection || !from || !to) {
         return;
      }

      const stack = this.state.get(runtimeId)?.stack;
      const index = stack ? stack.findIndex(frame => this.sameEditorContext(frame, from)) : -1;

      if(stack && index >= 0) {
         stack[index] = to;
      }

      socketConnection.whenConnected().pipe(take(1)).subscribe((conn: StompClientConnection) => {
         conn.send("/events/wiz/pairing/pane-switch", {},
                   JSON.stringify({ runtimeId, from, to }));
      });
   }

   /**
    * Tells the server what a pane shows that its editorContext does not say and the user can
    * change after pairing (the calculated field dialog's SQL/Script switch), for the exact
    * context a session is bound to: the one a code was minted with, or the one `pushFocus` moved
    * a toolbar session to. The server keeps it per (socket, runtime, context), so the order
    * against the mint or retarget does not matter. Lives here because both paths that bind a
    * session to a pane send it through the same connection.
    */
   reportPaneState(runtimeId: string, socketConnection: ViewsheetClientService,
                   editorContext: EditorContext, sql: boolean, aggregate: boolean = false): void
   {
      if(!runtimeId || !socketConnection || !editorContext) {
         return;
      }

      socketConnection.whenConnected().pipe(take(1)).subscribe((conn: StompClientConnection) => {
         conn.send("/events/wiz/pairing/pane-state", {},
                   JSON.stringify({ runtimeId, editorContext, sql, aggregate }));
      });
   }

   /**
    * Pops the session's target back to whatever it was before the matching `pushFocus`. Callers
    * pass the `editorContext` they *expect* to be the current top of the stack -- i.e. the exact
    * location their own `pushFocus` call pushed -- purely as a client-side integrity check
    * (plan Phase 2 task 4): the server's own stack (Phase 1) is the actual authority and pops
    * unconditionally on `runtimeId` alone, so this call is always sent regardless of whether the
    * check passes. A mismatch never changes what gets sent; it only means something upstream of
    * this call sequenced pushes/pops incorrectly, which is a bug to surface loudly, not to guess
    * around by silently reordering or skipping the pop.
    *
    * Must be called even if Follow Focus has since been toggled off mid-session (spec test 3):
    * an already-pushed pane still owes its pop when it closes. Only call this when the matching
    * `pushFocus` actually returned `true` -- a pane that never pushed owes no pop, and calling
    * this unconditionally from `ngOnDestroy` would send a spurious pop for every pane close.
    */
   popFocus(runtimeId: string, socketConnection: ViewsheetClientService,
            editorContext: EditorContext): void
   {
      if(!runtimeId || !socketConnection) {
         return;
      }

      const stack = this.state.get(runtimeId)?.stack;
      const top = stack && stack.length > 0 ? stack[stack.length - 1] : null;

      if(!this.sameEditorContext(top, editorContext)) {
         const message = `Follow Focus close mismatch for runtime ${runtimeId}: expected to be` +
            ` closing ${JSON.stringify(top)} but got ${JSON.stringify(editorContext)}.`;
         // eslint-disable-next-line no-console
         console.error(message);
         this.errorSubject.next(message);
      }

      if(stack && stack.length > 0) {
         stack.pop();
      }

      socketConnection.whenConnected().pipe(take(1)).subscribe((conn: StompClientConnection) => {
         conn.send("/events/wiz/pairing/pop-focus", {}, JSON.stringify({ runtimeId }));
      });
   }

   private stateFor(runtimeId: string): FollowFocusState {
      let state = this.state.get(runtimeId);

      if(!state) {
         state = { enabled: false, stack: [] };
         this.state.set(runtimeId, state);
      }

      return state;
   }

   /**
    * Same absent-vs-null-tolerant comparison `ConnectToClaudeComponent` uses for the joined
    * notice -- kept as an independent copy rather than a shared import so this service has no
    * compile-time dependency on that component (only the reverse should ever be true).
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
}
