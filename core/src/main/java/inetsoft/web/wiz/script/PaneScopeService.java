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
package inetsoft.web.wiz.script;

import inetsoft.web.wiz.pairing.JoinSession;
import inetsoft.web.wiz.pairing.PairingException;

/**
 * Enforces which script kinds may be acted on from a whole-sheet ("Connect to Claude" toolbar)
 * session, versus which require a session paired from that expression's own script pane or
 * formula editor.
 *
 * <p>Two independent gates, both applied by {@link #check}:</p>
 * <ul>
 *   <li><b>Per-kind addressability</b> — {@link ScriptTarget.Kind#requiresPaneSession()}. A
 *       calculated field (and, once G2 Task 8 lands, a worksheet expression/condition column) has
 *       no name or identity at the whole-sheet level, so it can never be served from a
 *       whole-sheet session — strict posture or not.</li>
 *   <li><b>The opt-in strict posture</b> ({@code wiz.agent.script.require-script-pane}). Off by
 *       default. When on, it additionally refuses the four viewsheet-level kinds
 *       ({@code viewsheetOnInit}/{@code viewsheetOnLoad}/{@code assemblyMain}/
 *       {@code assemblyOnClick}) too, requiring every action to come from a pane-scoped
 *       session.</li>
 * </ul>
 *
 * <p>{@code strict} is supplied by the caller rather than read here — the controller reads the
 * {@code wiz.agent.script.require-script-pane} property fresh from {@code SreeEnv} on every
 * request (mirroring {@link inetsoft.web.wiz.pairing.SheetAgentFeature}), so an administrator
 * flipping the posture takes effect immediately, and this class stays a plain, easily-tested
 * function of its inputs rather than a stateful singleton with its own environment dependency.</p>
 */
public class PaneScopeService {

   public PaneScopeService(boolean strict) {
      this.strict = strict;
   }

   /**
    * Checks whether {@code session} may act on {@code target}.
    *
    * @return {@code true} always — the failure path throws rather than returning {@code false};
    *         the boolean return exists only so a caller can fold this into a fluent chain.
    * @throws PairingException naming the reason an expression-level kind was refused (what to do
    *                          about it — open its own editor and reconnect), or, in strict
    *                          posture, that the posture itself is what refused a whole-sheet
    *                          session. Never names the property itself as the expression-level
    *                          reason: a user (or an LLM agent relaying the message) can act on a
    *                          reason but not on a property name.
    */
   public boolean check(JoinSession session, ScriptTarget target) throws PairingException {
      boolean paneScoped = session != null && session.editorContext() != null;

      if(target.kind().requiresPaneSession() && !paneScoped) {
         throw new PairingException(reasonFor(target.kind()));
      }

      if(strict && !paneScoped) {
         throw new PairingException(
            "This deployment requires every script action to come from a session paired at " +
            "that script's own pane or formula editor (" + STRICT_FLAG + "); this session is " +
            "bound to the whole viewsheet. Open '" + target.kind().wireName() + "' in its own " +
            "editor and click Connect Agent.");
      }

      return true;
   }

   /**
    * Explains WHY {@code kind} needs its own editor session, in terms of what the kind IS, never
    * by naming the enforcing policy or property.
    */
   private static String reasonFor(ScriptTarget.Kind kind) {
      return switch(kind) {
         case CALC_FIELD -> "a calculated field has no name at sheet level — open its formula " +
            "editor and click Connect Agent.";
         case WORKSHEET_EXPRESSION -> "a worksheet expression column has no name at sheet " +
            "level — open its expression editor and click Connect Agent.";
         case WORKSHEET_CONDITION -> "a worksheet condition has no name at sheet level — open " +
            "its condition editor and click Connect Agent.";
         default -> "'" + kind.wireName() + "' has no identity at sheet level — open it in its " +
            "own editor and click Connect Agent.";
      };
   }

   /** Off-by-default property name; see the class javadoc. Read by the controller, not here. */
   public static final String STRICT_FLAG = "wiz.agent.script.require-script-pane";

   private final boolean strict;
}
