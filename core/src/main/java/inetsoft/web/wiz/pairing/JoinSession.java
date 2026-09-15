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
package inetsoft.web.wiz.pairing;

/**
 * A reusable session opened after a successful pairing join.
 * Edits reuse this; the code stays single-use.
 *
 * @param runtimeId the runtime this session is attached to, or {@code null} for a session opened
 *                      with no runtime yet (a portal-level session, established directly at login
 *                      rather than by pairing to an already-open Composer pane) -- see
 *                      {@link #isAttached()}
 * @param sheetType the attached runtime's sheet type, or {@code null} in lockstep with a
 *                      {@code null} {@code runtimeId}
 * @param editorContext the script/formula location this session is scoped to -- carried over
 *                      from the {@link PairingGrant} that opened it at mint time, or moved by
 *                      {@code SheetSessionService.retarget}/{@code popFocus} (Follow Focus)
 *                      thereafter -- or {@code null} for a whole-sheet ("Connect to Claude"
 *                      toolbar) session
 * @param followFocusEnabled whether this session has opted in to Follow Focus -- see
 *                      {@code SheetSessionService.setFollowFocus}. {@code false} until a session
 *                      explicitly turns it on; {@code SheetSessionService.retarget} refuses to
 *                      move a session's target while this is {@code false}, independent of
 *                      whatever the Angular UI shows. Never {@code true} at construction --
 *                      opting in is always a separate, later act.
 * @param establishedDirectly whether this session was created via a login-triggered direct
 *                      establish (no pairing code, no runtime yet) rather than by joining a
 *                      {@link PairingGrant}. Recorded once at {@code open()} time and never
 *                      changed thereafter; used to look up an existing directly-established
 *                      session for an identity before minting a duplicate. {@code false} for
 *                      every session opened via the ordinary pairing-code join path.
 * @param crossSheetFollowEnabled whether this session has opted in to cross-sheet-follow --
 *                      see {@code SheetSessionService.syncToCurrentFocus}. {@code false} until a
 *                      session explicitly turns it on, and only settable on a session with
 *                      {@code establishedDirectly == true} -- {@code SheetSessionService}'s toggle
 *                      handler refuses to enable this on a pane-scoped session. Never {@code true}
 *                      at construction -- opting in is always a separate, later act, mirroring
 *                      {@code followFocusEnabled}'s own precedent exactly. Distinct from
 *                      {@code followFocusEnabled}: that flag retargets a session's
 *                      {@code editorContext} within the one {@code runtimeId} it is already bound
 *                      to; this flag retargets {@code runtimeId}/{@code sheetType} themselves,
 *                      between entirely different runtimes/assets.
 */
public record JoinSession(String sessionToken, String runtimeId, String ownerIdentity,
                          SheetType sheetType, long lastAccess, long ttlMillis,
                          ConnectionMode connectionMode, String socketSessionId,
                          String socketUserName, EditorContext editorContext,
                          boolean followFocusEnabled, boolean establishedDirectly,
                          boolean crossSheetFollowEnabled) {

   /**
    * Back-compat constructor predating {@code crossSheetFollowEnabled} -- defaults it to
    * {@code false}, the same default {@code SheetSessionService.open} relies on for every session
    * that has not explicitly opted in. Kept so the wide set of hand-built {@code new
    * JoinSession(...)} fixtures across the worksheet/viewsheet/script/binding test packages --
    * none of which exercise cross-sheet-follow -- do not all need updating for one new field on a
    * record none of them otherwise touch.
    */
   public JoinSession(String sessionToken, String runtimeId, String ownerIdentity,
                      SheetType sheetType, long lastAccess, long ttlMillis,
                      ConnectionMode connectionMode, String socketSessionId,
                      String socketUserName, EditorContext editorContext,
                      boolean followFocusEnabled, boolean establishedDirectly)
   {
      this(sessionToken, runtimeId, ownerIdentity, sheetType, lastAccess, ttlMillis,
           connectionMode, socketSessionId, socketUserName, editorContext, followFocusEnabled,
           establishedDirectly, false);
   }

   /**
    * Back-compat constructor predating {@code establishedDirectly} -- defaults it to
    * {@code false}, the same default {@code SheetSessionService.open} relies on for the ordinary
    * pairing-code join path. Kept so the wide set of hand-built {@code new JoinSession(...)}
    * fixtures across the worksheet/viewsheet/script/binding test packages -- none of which
    * exercise direct establishment -- do not all need updating for one new field on a record none
    * of them otherwise touch.
    */
   public JoinSession(String sessionToken, String runtimeId, String ownerIdentity,
                      SheetType sheetType, long lastAccess, long ttlMillis,
                      ConnectionMode connectionMode, String socketSessionId,
                      String socketUserName, EditorContext editorContext,
                      boolean followFocusEnabled)
   {
      this(sessionToken, runtimeId, ownerIdentity, sheetType, lastAccess, ttlMillis,
           connectionMode, socketSessionId, socketUserName, editorContext, followFocusEnabled,
           false, false);
   }

   /**
    * Back-compat constructor predating {@code followFocusEnabled} (Follow Focus) -- defaults it
    * to {@code false}, the same default {@code SheetSessionService.open} relies on. Kept so the
    * wide set of hand-built {@code new JoinSession(...)} fixtures across the
    * worksheet/viewsheet/script/binding test packages -- none of which exercise Follow Focus --
    * do not all need updating for one new field on a record none of them otherwise touch.
    */
   public JoinSession(String sessionToken, String runtimeId, String ownerIdentity,
                      SheetType sheetType, long lastAccess, long ttlMillis,
                      ConnectionMode connectionMode, String socketSessionId,
                      String socketUserName, EditorContext editorContext)
   {
      this(sessionToken, runtimeId, ownerIdentity, sheetType, lastAccess, ttlMillis,
           connectionMode, socketSessionId, socketUserName, editorContext, false, false);
   }

   public boolean isExpired(long now) { return now - lastAccess > ttlMillis; }

   /** {@code true} iff this session has a runtime attached -- {@code false} for an unattached,
    * directly-established (portal) session with no {@code runtimeId} yet. */
   public boolean isAttached() { return runtimeId != null; }

   /** Forward-compat slot: PAIRED = browser owns + agent joins; AGENT_OWNED reserved for future viz. */
   public enum ConnectionMode { PAIRED }
}
