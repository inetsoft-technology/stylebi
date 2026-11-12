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

import inetsoft.util.script.ScriptEnv;
import inetsoft.util.script.ScriptEnvRepository;
import inetsoft.web.wiz.pairing.EditorContext;
import inetsoft.web.wiz.pairing.PairingException;
import inetsoft.web.wiz.script.model.ScriptInfo;
import inetsoft.web.wiz.script.model.ScriptTargetInfo;

/**
 * The synthetic, draft-only target of a {@code vsConditionValue} pane session
 * ({@link ScriptTarget.Kind#VS_CONDITION_VALUE}).
 *
 * <p>The value lives only in the user's open formula dialog, so there is nothing on the runtime
 * viewsheet to enumerate, read or write: the target is derived from the session's own grant, the
 * read is an empty pending draft, and the syntax check never executes the expression. Static and
 * pure so it needs no Spring bean (the deployed instance runs AOT: a new bean or constructor
 * change needs the {@code server} module rebuilt).
 */
final class VsConditionValueTargets {
   private VsConditionValueTargets() {
   }

   /** The one target a pane-scoped {@code vsConditionValue} session lists, or null if unparsable. */
   static ScriptTargetInfo targetInfo(EditorContext grant) {
      ScriptTarget target;

      try {
         target = ScriptTarget.of(ScriptTarget.Kind.VS_CONDITION_VALUE, grant.assembly(),
                                  grant.name());
      }
      catch(PairingException ex) {
         return null;
      }

      String owner = "'" + target.assemblyName() + "'";
      EditorContext.VsConditionScope scope = EditorContext.parseVsConditionName(target.name());
      String what = scope.highlight() ?
         "highlight '" + scope.highlightName() + "' condition" : "filter condition";

      return new ScriptTargetInfo(
         target.id(), target.kind().wireName(), target.assemblyName(), target.name(), null, null,
         "Expression value of the " + what + " on " + owner + " (draft-only)",
         "each time " + owner + " is refreshed, once the user applies the condition",
         false, true, "vsConditionValue:" + target.assemblyName() + ":" + target.name(),
         "viewsheet", null, null, Boolean.TRUE);
   }

   /** The empty, pending read: the dialog's text is not on the server. */
   static ScriptInfo read(ScriptTarget target) {
      return new ScriptInfo(target.toString(), "", true, Boolean.TRUE, note(target, false));
   }

   static String note(ScriptTarget target, boolean write) {
      EditorContext.VsConditionScope scope = EditorContext.parseVsConditionName(target.name());
      boolean highlight = scope != null && scope.highlight();
      String owner = "'" + target.assemblyName() + "'";
      String where = highlight ? "highlight '" + scope.highlightName() + "' of " + owner :
         "filter condition of " + owner;
      String dialog = highlight ? "Highlight dialog" : "Conditions dialog";

      if(write) {
         return "Pushed to the open formula dialog for the " + where + " as a draft. Nothing is " +
            "applied to the viewsheet: the user must click OK in the formula dialog, confirm the " +
            "condition and click OK/Apply in the " + dialog + "; Cancel anywhere discards it. " +
            "The server cannot see whether the dialog showed it: if the user had typed, the " +
            "draft waits behind the dialog's reload prompt. Do not retry or read back to " +
            "verify; after the user applies it, verify with " +
            (highlight ? "list_highlights" : "get_condition") + " from a whole-sheet session " +
            "(connect_sheet with a toolbar code).";
      }

      return "Draft-only location: the expression of ONE condition value in the user's open " +
         dialog + " (" + where + "). The dialog's current text lives " +
         "only in the browser, so the server cannot return it; the empty text is NOT an empty " +
         "expression and NOT a failed update_script. Nothing is applied to the viewsheet until " +
         "the user clicks OK in this formula dialog, confirms the condition and clicks " +
         "OK/Apply in the " + dialog + ". To see conditions already applied, use " +
         "get_condition / list_highlights from a whole-sheet session (connect_sheet with a " +
         "toolbar code).";
   }

   /**
    * The parse error of {@code text} as a viewsheet condition script, or null when it parses or
    * the engine is not initialised. Parses only: the expression is never executed.
    */
   static String syntaxProblem(String text) {
      return syntaxProblem(ScriptEnvRepository.getScriptEnv(), text);
   }

   /** Package-private so a test can supply an engine; the real one is not available to unit tests. */
   static String syntaxProblem(ScriptEnv env, String text) {
      try {
         if(env != null) {
            env.checkFunction("viewsheet condition", text);
         }

         return null;
      }
      catch(IllegalStateException ex) {
         return null;
      }
      catch(Exception ex) {
         return ex.getMessage() == null ? ex.toString() : ex.getMessage();
      }
   }
}
