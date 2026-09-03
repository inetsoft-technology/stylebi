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
import inetsoft.report.script.viewsheet.VSAScriptable;
import inetsoft.report.script.viewsheet.ViewsheetScope;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.web.wiz.pairing.PairingException;
import inetsoft.web.wiz.script.model.ScriptError;
import inetsoft.web.wiz.script.model.ScriptExecResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
            "to execute it for real.", null, null);
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
            "Call run_script_live again with confirmed=true to proceed.", null, null);
      }

      return execute(rvs, target);
   }

   private ScriptExecResult execute(RuntimeViewsheet rvs, ScriptTarget target) throws PairingException {
      String text = readService.read(rvs, target).text();

      if(text == null || text.isEmpty()) {
         return new ScriptExecResult(true, null, null, List.of(), false, null,
            "No script at " + target + " — nothing to execute.", null);
      }

      ViewsheetSandbox box = rvs.getViewsheetSandbox().orElse(null);

      if(box == null) {
         throw new PairingException("Viewsheet sandbox not available for this runtime");
      }

      ViewsheetScope scope = box.getScope();
      String assemblyName = switch(target.location()) {
         case VS_INIT, VS_LOAD -> null;
         case ASSEMBLY, ASSEMBLY_ONCLICK -> target.assemblyName();
         // Permanent, not a placeholder: a calculated field is an expression evaluated per row,
         // not a runnable script, so there is no "wire it up later" for this case the way there
         // is for write/setEnabled elsewhere.
         case CALC_FIELD -> throw new PairingException(
            "A calculated field is an expression evaluated per row, not a runnable script. " +
            "Use read_script/update_script on it instead.");
         // Unreachable in practice -- readService.read(rvs, target) above already throws for
         // these two (ScriptReadService only serves RuntimeViewsheet locations; a worksheet
         // expression/condition column is read/written through WorksheetScriptService instead).
         // Handled explicitly anyway: this is a switch EXPRESSION, so the compiler -- not just
         // this method's runtime behavior -- must stay exhaustive over every Location.
         case WORKSHEET_EXPRESSION, WORKSHEET_CONDITION, WORKSHEET_CONDITION_VALUE -> throw new PairingException(
            "A worksheet expression/condition column is not a runnable viewsheet script. " +
            "Use worksheet-chat's edit_expression/edit_condition tools on it instead.");
      };

      // ASSEMBLY/ASSEMBLY_ONCLICK touch exactly the one named assembly. VS_INIT/VS_LOAD run with
      // assemblyName == null and can write to any assembly in the sheet by name, so every
      // assembly's scriptable is tracked -- each was already eagerly created and cached in the
      // scope's own propmap when the sandbox initialized (ViewsheetScope#addProperties0), so
      // this is a lookup, not a fresh instantiation with its own side effects.
      Map<String, VSAScriptable> trackedScriptables = trackedScriptables(target, assemblyName, rvs, scope);

      for(VSAScriptable scriptable : trackedScriptables.values()) {
         scriptable.resetUnrecognizedWrites();
         scriptable.resetRejectedWrites();
      }

      try {
         Object value = scope.execute(text, assemblyName);
         List<String> unrecognized = new ArrayList<>();
         List<String> rejected = new ArrayList<>();

         for(Map.Entry<String, VSAScriptable> entry : trackedScriptables.entrySet()) {
            for(String name : entry.getValue().getUnrecognizedWrites()) {
               // ASSEMBLY/ASSEMBLY_ONCLICK already name the one assembly in the message below,
               // so the bare property name is unambiguous there. VS_INIT/VS_LOAD can touch
               // several different assemblies, so qualify with which one to avoid conflating
               // e.g. two different assemblies' own unrecognized "label".
               unrecognized.add(assemblyName != null ? name : entry.getKey() + "." + name);
            }

            // Recognized properties (e.g. position/size) whose setter declined to apply the
            // value -- distinct from "unrecognized" above, which never even matched propmap.
            for(String name : entry.getValue().getRejectedWrites()) {
               rejected.add(assemblyName != null ? name : entry.getKey() + "." + name);
            }
         }

         List<String> notApplied = new ArrayList<>();
         notApplied.addAll(unrecognized);
         notApplied.addAll(rejected);

         List<String> changed = notApplied.isEmpty() ? List.of(target.toString()) : List.of();
         String summary;

         if(notApplied.isEmpty()) {
            summary = "Executed " + target + ".";
         }
         else {
            List<String> clauses = new ArrayList<>();

            if(!unrecognized.isEmpty()) {
               clauses.add("assigned " + unrecognized + " which " +
                  (unrecognized.size() == 1 ? "is not a recognized scriptable property" :
                     "are not recognized scriptable properties") +
                  (assemblyName != null ? " for this assembly type (" +
                     assemblyTypeName(rvs, assemblyName) + ")" : "") + " — the value " +
                  (unrecognized.size() == 1 ? "was" : "were") + " NOT applied; " +
                  (unrecognized.size() == 1 ? "it" : "they") + " only became an ad hoc script " +
                  "variable nothing else reads");
            }

            if(!rejected.isEmpty()) {
               clauses.add("assigned " + rejected + ", " +
                  (rejected.size() == 1 ? "a recognized property whose value was" :
                     "recognized properties whose values were") + " NOT applied because the " +
                  "connected session is a Composer design-mode session, not a live viewer " +
                  "runtime");
            }

            summary = "Executed " + target + ", but " + String.join("; and ", clauses) +
               ". Call get_script_context for " +
               (assemblyName != null ? assemblyName + "'s" : "the named assembly's") +
               " real scriptable properties, or use get_assembly_properties/update_binding if " +
               "you meant a different, non-scripted property.";
         }

         return new ScriptExecResult(true, stringify(value), null, changed, false, null, summary,
            notApplied.isEmpty() ? null : notApplied);
      }
      catch(Exception ex) {
         return new ScriptExecResult(false, null, toScriptError(ex), null, false, null, null, null);
      }
   }

   /**
    * Which {@code VSAScriptable}(s) to reset/check {@code getUnrecognizedWrites()} on around
    * this execution. Empty for a location with no per-assembly scriptable to instrument
    * (e.g. an already-thrown location never reaches here).
    */
   private static Map<String, VSAScriptable> trackedScriptables(
      ScriptTarget target, String assemblyName, RuntimeViewsheet rvs, ViewsheetScope scope)
   {
      if(target.location() == ScriptTarget.Location.ASSEMBLY ||
         target.location() == ScriptTarget.Location.ASSEMBLY_ONCLICK)
      {
         VSAScriptable scriptable = scope.getVSAScriptable(assemblyName);
         return scriptable != null ? Map.of(assemblyName, scriptable) : Map.of();
      }

      if(target.location() == ScriptTarget.Location.VS_INIT ||
         target.location() == ScriptTarget.Location.VS_LOAD)
      {
         Map<String, VSAScriptable> tracked = new LinkedHashMap<>();

         for(Assembly assembly : rvs.getViewsheet().getAssemblies()) {
            VSAScriptable scriptable = scope.getVSAScriptable(assembly.getName());

            if(scriptable != null) {
               tracked.put(assembly.getName(), scriptable);
            }
         }

         return tracked;
      }

      return Map.of();
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

   /**
    * Best-effort assembly type name (e.g. "SubmitVSAssembly") for the unrecognized-property
    * message. Falls back to the assembly name itself if the assembly can't be resolved (should
    * not happen for a target that just executed successfully, but this message is diagnostic
    * text, not load-bearing behavior).
    */
   private static String assemblyTypeName(RuntimeViewsheet rvs, String assemblyName) {
      VSAssembly assembly = rvs.getViewsheet().getAssembly(assemblyName);
      return assembly != null ? assembly.getClass().getSimpleName() : assemblyName;
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
