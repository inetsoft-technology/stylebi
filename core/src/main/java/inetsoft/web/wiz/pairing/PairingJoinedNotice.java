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
 * Tells the browser that minted a pairing code that an agent has redeemed it.
 *
 * <p>Pairing is split across two processes: the browser mints the code, and the agent joins over
 * HTTP. Nothing else closes that loop, so without this notice the Composer cannot know the code it
 * displayed was ever used, and the only evidence pairing worked is the agent saying so.
 *
 * <p>Carries exactly the three fields the client needs to decide whether a notice is addressed to
 * it. The destination is per socket session — not per sheet and not per pane — while
 * {@code ConnectToClaudeComponent} is reused by the composer toolbar, the viewsheet script pane and
 * the formula editor dialog — several instances can be alive at once on one page. Both
 * {@code runtimeId} and {@code editorContext} are therefore load-bearing filters, not diagnostics.
 *
 * @param editorContext the paired location, or {@code null} for a whole-sheet ("Connect to Claude"
 *                      toolbar) pairing. {@code null} is the normal case, never an error.
 * @param focusChanged  {@code false} for the original "an agent just joined" event this record
 *                      was created for; {@code true} when a session already known to be joined
 *                      had its target MOVED by Follow Focus ({@code SheetSessionService.retarget}/
 *                      {@code popFocus}), not by a fresh mint-and-join. The client's connected
 *                      indicator uses this to tell "someone else's pane just got paired" (which
 *                      must not light up an unrelated toolbar instance -- see
 *                      {@code ConnectToClaudeComponent}'s exact-editorContext-match filter) apart
 *                      from "my own already-connected session's live target changed" (which the
 *                      toolbar instance DOES want to track, by {@code runtimeId} alone, regardless
 *                      of what the new {@code editorContext} is).
 */
public record PairingJoinedNotice(String runtimeId, SheetType sheetType,
                                  EditorContext editorContext, boolean focusChanged) {

   /**
    * Back-compat constructor predating {@code focusChanged} (Follow Focus) -- defaults it to
    * {@code false}, i.e. the original "an agent joined" event shape every existing caller and
    * test constructs.
    */
   public PairingJoinedNotice(String runtimeId, SheetType sheetType, EditorContext editorContext) {
      this(runtimeId, sheetType, editorContext, false);
   }
}
