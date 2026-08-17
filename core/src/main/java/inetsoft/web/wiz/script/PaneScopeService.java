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

import inetsoft.web.wiz.pairing.EditorContext;
import inetsoft.web.wiz.pairing.JoinSession;
import inetsoft.web.wiz.pairing.PairingException;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Enforces which script kinds may be acted on from a whole-sheet ("Connect to Claude" toolbar)
 * session, versus which require a session paired from that expression's own script pane or
 * formula editor -- and, for a pane-scoped session, that it only ever touches the one location
 * its grant names.
 *
 * <p>Three gates, all applied by {@link #check}:</p>
 * <ul>
 *   <li><b>Per-kind addressability</b> — {@link ScriptTarget.Kind#requiresPaneSession()}. A
 *       calculated field (and, once G2 Task 8 lands, a worksheet expression/condition column) has
 *       no name or identity at the whole-sheet level, so it can never be served from a
 *       whole-sheet session — strict posture or not.</li>
 *   <li><b>Grant scope, unconditional</b> — {@link #matchesGrant}. A pane-scoped session's
 *       {@link JoinSession#editorContext()} names exactly one location (plus its dialog sibling,
 *       see that method); any target outside it is refused. This is the scope of the grant
 *       itself, not a policy — it applies whether or not the strict posture below is enabled, and
 *       it is checked whether the target is expression-level or viewsheet-level. Weakening this
 *       when strict posture is off would mean a session minted for one chart's script could edit
 *       another chart's, which defeats the reason pane scoping exists at all.</li>
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
    *                          about it — open its own editor and reconnect), naming the location
    *                          a pane-scoped session tried to reach outside its grant, or, in
    *                          strict posture, that the posture itself is what refused a
    *                          whole-sheet session. Never names the property itself as the
    *                          expression-level reason: a user (or an LLM agent relaying the
    *                          message) can act on a reason but not on a property name.
    */
   public boolean check(JoinSession session, ScriptTarget target) throws PairingException {
      boolean paneScoped = session != null && session.editorContext() != null;

      if(target.kind().requiresPaneSession() && !paneScoped) {
         throw new PairingException(reasonFor(target.kind()));
      }

      if(paneScoped && !matchesGrant(session.editorContext(), target)) {
         throw new PairingException(outOfGrantReason(session.editorContext(), target));
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
    * Whether {@code target} is inside the location {@code grant} names — its own location, or
    * that location's <b>dialog sibling</b>.
    *
    * <p>Ruling (G2 Task 7): a grant for {@code assemblyMain} on an assembly also covers
    * {@code assemblyOnClick} on that SAME assembly, and vice versa, but never a different
    * assembly and never a different kind family. Reason: {@code ClickableScriptPane} — the one
    * dialog Composer opens for a clickable assembly — hosts BOTH scripts side by side behind a
    * single radio toggle ("Script" / "onClick"), sharing one {@code editorContext}. Its own
    * {@code editorContext} getter is hardcoded to {@code assemblyOnClick} regardless of which
    * radio is selected (clickable-script-pane.component.ts:56-58), so a mint from that dialog
    * does not reliably name which of the two scripts the user is actually looking at. Refusing
    * the sibling would make the "wrong" radio position (relative to whatever the dialog happened
    * to mint) unreachable from the very session the user just opened for that exact assembly —
    * a false refusal in the one case pane scoping exists to serve, for no security benefit: both
    * scripts are already inside the boundary ("this location", the assembly's own script surface)
    * the spec draws around a single dialog, as distinct from "this sheet". A calc field's formula
    * editor has no such sibling — it is single-purpose — so {@code calcField} matches only its
    * own (table, name), never another field on the same table.
    */
   static boolean matchesGrant(EditorContext grant, ScriptTarget target) {
      if(grant == null) {
         return true;
      }

      ScriptTarget.Kind grantedKind;

      try {
         grantedKind = ScriptTarget.Kind.fromWire(grant.kind());
      }
      catch(PairingException ex) {
         // A grant naming a kind this server no longer recognizes matches nothing -- fail
         // closed, not open.
         return false;
      }

      if(!sameDialogFamily(grantedKind, target.kind())) {
         return false;
      }

      if(grantedKind == ScriptTarget.Kind.CALC_FIELD) {
         // EditorContext accepts the table name in either `table` or (mirroring ScriptTarget's
         // own assemblyName() alias) `assembly` -- see EditorContext's javadoc.
         String grantedTable = grant.table() != null ? grant.table() : grant.assembly();
         return Objects.equals(grantedTable, target.assemblyName()) &&
            Objects.equals(grant.name(), target.name());
      }

      if(ASSEMBLY_SCRIPT_FAMILY.contains(grantedKind)) {
         return Objects.equals(grant.assembly(), target.assemblyName());
      }

      // viewsheetOnInit/viewsheetOnLoad (and, once servable, worksheetExpression/
      // worksheetCondition) carry no assembly/name -- sameDialogFamily already required an exact
      // kind match, which is the whole identity for these.
      return true;
   }

   /**
    * Whether {@code granted} and {@code target} are the same dialog's kind, or two kinds the same
    * dialog hosts as siblings (see {@link #matchesGrant}'s javadoc). Every other pair — including
    * two DIFFERENT expression-level kinds, e.g. {@code calcField} vs {@code worksheetExpression}
    * — is a different dialog entirely and never matches here.
    */
   private static boolean sameDialogFamily(ScriptTarget.Kind granted, ScriptTarget.Kind target) {
      if(granted == target) {
         return true;
      }

      return ASSEMBLY_SCRIPT_FAMILY.contains(granted) && ASSEMBLY_SCRIPT_FAMILY.contains(target);
   }

   /**
    * The only reason for the whole-sheet ("Connect to Claude") session at {@code grant}, in terms
    * a user can act on -- names the location the session IS paired to, not the one it refused.
    */
   private static String outOfGrantReason(EditorContext grant, ScriptTarget target) {
      return "This session is paired to " + grantDescription(grant) + "; it may not act on " +
         targetDescription(target) + ". Open that location in its own editor and click Connect " +
         "Agent to act on it.";
   }

   private static String grantDescription(EditorContext grant) {
      try {
         ScriptTarget.Kind grantedKind = ScriptTarget.Kind.fromWire(grant.kind());

         return switch(grantedKind) {
            case CALC_FIELD -> "calculated field '" +
               (grant.table() != null ? grant.table() : grant.assembly()) + "." + grant.name() +
               "'";
            case ASSEMBLY_MAIN, ASSEMBLY_ON_CLICK -> "the script of '" + grant.assembly() + "'";
            default -> "'" + grantedKind.wireName() + "'";
         };
      }
      catch(PairingException ex) {
         return "'" + grant.kind() + "'";
      }
   }

   private static String targetDescription(ScriptTarget target) {
      return switch(target.kind()) {
         case CALC_FIELD ->
            "calculated field '" + target.assemblyName() + "." + target.name() + "'";
         case ASSEMBLY_MAIN, ASSEMBLY_ON_CLICK ->
            "'" + target.kind().wireName() + "' on '" + target.assemblyName() + "'";
         default -> "'" + target.kind().wireName() + "'";
      };
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

   /** The dialog-sibling family {@link #matchesGrant} treats as one grant; see its javadoc. */
   private static final Set<ScriptTarget.Kind> ASSEMBLY_SCRIPT_FAMILY =
      EnumSet.of(ScriptTarget.Kind.ASSEMBLY_MAIN, ScriptTarget.Kind.ASSEMBLY_ON_CLICK);

   private final boolean strict;
}
