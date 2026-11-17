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
package inetsoft.web.wiz.worksheet;

import inetsoft.web.wiz.pairing.EditorContext;
import inetsoft.web.wiz.pairing.PairingException;
import inetsoft.web.wiz.script.ScriptTarget;
import inetsoft.web.wiz.script.model.ScriptInfo;
import inetsoft.web.wiz.script.model.ScriptTargetInfo;

/**
 * The synthetic, draft-only targets of the two worksheet dialog kinds
 * ({@link ScriptTarget.Kind#WORKSHEET_VARIABLE_DEFAULT} and
 * {@link ScriptTarget.Kind#WORKSHEET_SQL_QUERY}), the worksheet counterpart of the viewsheet
 * package's {@code DynamicValueTargets}. The value lives in a dialog the user has open, so the
 * listed target is derived from the session's own grant and the notes say what an agent can and
 * cannot see. Static and pure so it needs no Spring bean (the deployed instance runs AOT).
 */
final class WorksheetDraftTargets {
   private WorksheetDraftTargets() {
   }

   /** The one target a pane-scoped session at either kind lists, or null if the grant is malformed. */
   static ScriptTargetInfo targetInfo(EditorContext grant) {
      ScriptTarget.Kind kind = kindOf(grant);

      if(kind == null) {
         return null;
      }

      ScriptTarget target;

      try {
         target = ScriptTarget.of(kind, grant.assembly(), grant.name());
      }
      catch(PairingException ex) {
         return null;
      }

      String assembly = target.assemblyName();
      boolean sql = kind == ScriptTarget.Kind.WORKSHEET_SQL_QUERY;
      String label = sql
         ? "SQL of query table '" + assembly + "' (draft-only: update_script pushes into the " +
           "user's open Database Query dialog; free-form SQL; read_script returns the SQL last " +
           "applied to the table, not text typed in the open dialog and not yet applied with OK)"
         : "Expression of the default value of worksheet variable '" + assembly + "' " +
           "(draft-only: the value is in the user's open Variable Properties dialog)";
      String runsWhen = sql
         ? "each time '" + assembly + "' is queried, once the user clicks OK"
         : "when the variable's value is requested, once the user applies the dialog";

      return new ScriptTargetInfo(
         target.id(), kind.wireName(), assembly, null, null, null, label, runsWhen,
         false, true, kind.wireName() + ":" + assembly, "worksheet", null, null, Boolean.TRUE);
   }

   /** The empty, pending read of a variable default: the dialog's text is not on the server. */
   static ScriptInfo readVariable(ScriptTarget target, boolean pendingVariable) {
      return new ScriptInfo(target.toString(), "", true, Boolean.TRUE,
                            variableReadNote(target, pendingVariable));
   }

   /** The read of a query that does not exist yet: nothing is applied, so the text is empty. */
   static ScriptInfo readPendingSql(ScriptTarget target) {
      return new ScriptInfo(target.toString(), "", true, Boolean.TRUE,
         "NEW query table '" + target.assemblyName() + "': it does not exist until the user " +
         "clicks OK in the Database Query dialog, so no SQL is applied yet and the text is " +
         "empty. The empty text is NOT empty SQL and NOT a failed update_script. Text typed in " +
         "the open dialog and not yet applied (OK) is not visible here either.");
   }

   /**
    * The read of an existing query table: its APPLIED SQL. {@code pending} is false because the
    * text is real; a push is still a draft.
    */
   static ScriptInfo readAppliedSql(ScriptTarget target, String sql) {
      return new ScriptInfo(target.toString(), sql == null ? "" : sql, true, Boolean.FALSE,
         "This is the SQL last applied to query table '" + target.assemblyName() + "' (pending " +
         "is false: the text is real, read from the worksheet). Text typed in the open dialog " +
         "and not yet applied (OK) is not visible here. update_script pushes a DRAFT into the " +
         "open Database Query dialog: nothing is applied until the user clicks OK, so read this " +
         "again after OK to see the change.");
   }

   static String variableReadNote(ScriptTarget target, boolean pendingVariable) {
      String variable = pendingVariable
         ? "the NEW variable '" + target.assemblyName() + "', which does not exist until OK"
         : "worksheet variable '" + target.assemblyName() + "'";

      return "Draft-only location: the Expression default value of " + variable + " in the " +
         "user's open Variable Properties dialog (JavaScript). The server cannot see the dialog " +
         "text; the empty text is NOT an empty expression and NOT a failed update_script. " +
         "Nothing is applied until the user clicks OK in the formula dialog and then OK in " +
         "Variable Properties. To read what is applied, use read_worksheet_model from a toolbar " +
         "session.";
   }

   static String variableWriteNote(ScriptTarget target, boolean pendingVariable,
                                   boolean strippedEquals)
   {
      String variable = pendingVariable
         ? "the NEW variable '" + target.assemblyName() + "'"
         : "worksheet variable '" + target.assemblyName() + "'";

      return "Pushed to the open formula dialog for the default-value expression of " + variable +
         " as a draft. Nothing is applied: the user must click OK in the formula dialog and then " +
         "OK in Variable Properties; Cancel anywhere discards it" +
         (pendingVariable ? " (and the variable does not exist until then)" : "") + ". The " +
         "server cannot see whether the dialog showed it: if the user had typed, the draft " +
         "waits behind the dialog's reload prompt. Do not retry or read back to verify; after " +
         "the user applies it, read_worksheet_model shows the value." +
         (strippedEquals
            ? " A leading '=' was removed: the dialog's editor holds the expression without it."
            : "");
   }

   static String sqlWriteNote(ScriptTarget target) {
      return "Pushed to the open Database Query dialog as a draft of the SQL for '" +
         target.assemblyName() + "'. Nothing is applied until the user clicks OK; Cancel " +
         "discards it. If the user had typed in the SQL box, the draft waits behind the " +
         "dialog's reload prompt. The user still has to press Parse Now / OK as after typing. " +
         "The query runs verbatim against the data source with the user's own permissions. " +
         "read_script returns the SQL last applied, so it shows this text only after OK.";
   }

   private static ScriptTarget.Kind kindOf(EditorContext grant) {
      if(grant == null) {
         return null;
      }

      if(EditorContext.WORKSHEET_VARIABLE_DEFAULT.equals(grant.kind())) {
         return ScriptTarget.Kind.WORKSHEET_VARIABLE_DEFAULT;
      }

      if(EditorContext.WORKSHEET_SQL_QUERY.equals(grant.kind())) {
         return ScriptTarget.Kind.WORKSHEET_SQL_QUERY;
      }

      return null;
   }
}
