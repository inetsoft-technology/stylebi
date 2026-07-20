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

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.script.viewsheet.ViewsheetScope;
import inetsoft.web.wiz.pairing.PairingException;
import inetsoft.web.wiz.script.model.ScriptError;
import inetsoft.web.wiz.script.model.ScriptExecResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Executes the script currently saved at a {@link ScriptTarget} (per {@code execute}/
 * {@code execute-live}'s wiz-services contract — they carry only {@code {target}}, not ad hoc
 * script text; the text must already have been written via {@code update_script}).
 *
 * <p><b>Fidelity limit:</b> there is no cheap, faithful clone of a {@link RuntimeViewsheet} to
 * dry-run against in isolation (unlike the worksheet plugin's structural edits, which mutate a
 * plain in-memory model). Both {@link #dryRun} and {@link #runLive} therefore execute against the
 * SAME live {@link ViewsheetScope} — {@code dryRun} does not guarantee zero side effects if the
 * script itself mutates state (e.g. a chart's binding). This is a documented simplification for
 * this slice, not a security boundary.</p>
 */
@Service
public class ScriptExecuteService {

   // Every entry here must be confirmed against ViewsheetScope.addFunctions()'s actual
   // propmap.put(...) registrations — do not add a name without finding its registration there.
   // Excludes toList/isCancelled/delayVisibility (pure reads/no-ops, not destructive).
   private static final List<String> DESTRUCTIVE_GLOBALS = List.of(
      "saveWorksheet", "runQuery", "setCellValue", "refreshData",
      "createConnection", "appendRow", "addImage"
   );

   @Autowired
   public ScriptExecuteService(ScriptReadService readService) {
      this.readService = readService;
   }

   /**
    * Validates + best-effort evaluates the script currently saved at {@code target}.
    *
    * <p>Because there is no isolated clone to run against (see the class javadoc), a script
    * that references a destructive global is NOT executed here at all — {@code execute} has no
    * {@code confirmed} parameter on the wiz-services contract, so there is no way for a caller
    * to accept the risk through this endpoint. Refuse and point at {@code run_script_live}
    * instead, which does have the confirmation gate.</p>
    */
   public ScriptExecResult dryRun(RuntimeViewsheet rvs, ScriptTarget target) throws PairingException {
      String text = readService.read(rvs, target).text();
      String destructive = firstDestructiveGlobal(text);

      if(destructive != null) {
         return new ScriptExecResult(false, null, null, null, true,
            "Script references \"" + destructive + "\", which can change stored/live data. " +
            "Dry-run cannot safely preview this — call run_script_live with confirmed=true " +
            "to execute it for real.", null);
      }

      return execute(rvs, target);
   }

   /**
    * Runs the script currently saved at {@code target}. If the script text references a
    * destructive global and {@code confirmed} is not {@code true}, execution is skipped and
    * {@code requiresConfirmation} is returned instead (per the plan's "surface the risk, don't
    * silently block" rule).
    */
   public ScriptExecResult runLive(RuntimeViewsheet rvs, ScriptTarget target, boolean confirmed)
      throws PairingException
   {
      String text = readService.read(rvs, target).text();
      String destructive = firstDestructiveGlobal(text);

      if(destructive != null && !confirmed) {
         return new ScriptExecResult(false, null, null, null, true,
            "Script references \"" + destructive + "\", which can change stored/live data. " +
            "Call run_script_live again with confirmed=true to proceed.", null);
      }

      return execute(rvs, target);
   }

   private ScriptExecResult execute(RuntimeViewsheet rvs, ScriptTarget target) throws PairingException {
      String text = readService.read(rvs, target).text();

      if(text == null || text.isEmpty()) {
         return new ScriptExecResult(true, null, null, List.of(), false, null,
            "No script at " + target + " — nothing to execute.");
      }

      ViewsheetSandbox box = rvs.getViewsheetSandbox().orElse(null);

      if(box == null) {
         throw new PairingException("Viewsheet sandbox not available for this runtime");
      }

      ViewsheetScope scope = box.getScope();
      String assemblyName = switch(target.location()) {
         case VS_INIT, VS_LOAD -> null;
         case ASSEMBLY, ASSEMBLY_ONCLICK -> target.assemblyName();
      };

      try {
         Object value = scope.execute(text, assemblyName);
         return new ScriptExecResult(true, stringify(value), null, List.of(target.toString()),
            false, null, "Executed " + target + ".");
      }
      catch(Exception ex) {
         return new ScriptExecResult(false, null, toScriptError(ex), null, false, null, null);
      }
   }

   /**
    * {@code ViewsheetScope.execute}'s message already embeds the Rhino/GraalJS suggestion text
    * (see {@code senv.getSuggestion} in that method) — there is no separately-accessible
    * suggestion/line without reaching into private engine state, so both are folded into
    * {@code message} for this slice.
    */
   private static ScriptError toScriptError(Exception ex) {
      return new ScriptError(ex.getMessage(), null, null);
   }

   private static Object stringify(Object value) {
      if(value == null || value instanceof String || value instanceof Number
         || value instanceof Boolean)
      {
         return value;
      }

      // Polyglot/host objects (e.g. GraalJS wrapper types) may not be Jackson-serializable —
      // fall back to a safe string representation rather than risking a 500 on the response.
      return String.valueOf(value);
   }

   private static String firstDestructiveGlobal(String scriptText) {
      if(scriptText == null || scriptText.isEmpty()) {
         return null;
      }

      for(String name : DESTRUCTIVE_GLOBALS) {
         Pattern p = Pattern.compile("\\b" + Pattern.quote(name) + "\\s*\\(");
         Matcher m = p.matcher(scriptText);

         if(m.find()) {
            return name;
         }
      }

      return null;
   }

   private final ScriptReadService readService;
}
