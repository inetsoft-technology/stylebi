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
 */
public record JoinSession(String sessionToken, String runtimeId, String ownerIdentity,
                          SheetType sheetType, long lastAccess, long ttlMillis,
                          ConnectionMode connectionMode, String socketSessionId,
                          String socketUserName, EditorContext editorContext,
                          boolean followFocusEnabled) {

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
           connectionMode, socketSessionId, socketUserName, editorContext, false);
   }

   public boolean isExpired(long now) { return now - lastAccess > ttlMillis; }

   /** Forward-compat slot: PAIRED = browser owns + agent joins; AGENT_OWNED reserved for future viz. */
   public enum ConnectionMode { PAIRED }
}
