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
import inetsoft.report.script.viewsheet.*;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.web.wiz.pairing.PairingException;
import inetsoft.web.wiz.script.model.*;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Executes scripts against a joined viewsheet in either dry-run (no persistent
 * side effects) or live (real side effects on the shared runtime) mode.
 *
 * <h2>Dry-run</h2>
 * <p>Uses a snapshot-then-restore approach around the assembly's
 * {@link VSAssemblyInfo}. The script is executed via
 * {@link inetsoft.report.script.viewsheet.ViewsheetScope#executeStructured} (the
 * non-throwing, structured path) and the assembly state is restored from the
 * snapshot afterwards so the shared runtime is not mutated.</p>
 *
 * <p>Fidelity note: the script runs in the live {@link ViewsheetScope} so it
 * sees the current data, variables, and other assemblies. Only the state that
 * the script writes to the target assembly's {@link VSAssemblyInfo} is captured
 * and restored — other assemblies are not snapshotted. For scripts that mutate
 * multiple assemblies this is a partial isolation, but it is sufficient for the
 * agent's edit→dry-run→confirm loop.</p>
 *
 * <h2>Live-run</h2>
 * <p>Delegates to {@link ViewsheetSandbox#executeScript} on the shared runtime.
 * The browser UI is refreshed by the caller (the controller) after a successful
 * live-run via {@link inetsoft.web.wiz.pairing.SheetAgentBroadcastService}.</p>
 *
 * <h2>Destructive-global guardrail</h2>
 * <p>Before a live-run the script text is scanned for calls to globals with
 * real side-effects ({@code saveWorksheet}, {@code runQuery}, {@code setCellValue},
 * {@code refreshData}). When any are found the result carries
 * {@code requiresConfirmation=true} so the agent layer can ask the user before
 * re-calling with explicit confirmation; the script is NOT silently blocked.</p>
 */
@Service
public class ScriptExecuteService {

   private static final Set<String> DESTRUCTIVE_GLOBALS = Set.of(
      "saveWorksheet", "runQuery", "setCellValue", "refreshData");

   // =========================================================================
   // Dry-run
   // =========================================================================

   /**
    * Execute the script at {@code target} in dry-run mode: the script runs in
    * the live scope but the target assembly's state is snapshotted and restored
    * so the shared runtime is not persistently mutated.
    *
    * @param rvs    the joined runtime viewsheet
    * @param target the script location to execute
    * @return a {@link ScriptExecResult} with {@code ok}, an optional
    *         {@link ScriptError} (with suggestion), and a list of assembly names
    *         that changed during the dry execution
    * @throws PairingException if the sandbox or scope is unavailable
    */
   public ScriptExecResult dryRun(RuntimeViewsheet rvs, ScriptTarget target)
      throws PairingException
   {
      ViewsheetSandbox box = requireSandbox(rvs);
      ViewsheetScope scope = box.getScope();
      Viewsheet vs = rvs.getViewsheet();
      String script = resolveScriptText(vs, target);

      if(script == null || script.isEmpty()) {
         return ScriptExecResult.dryRunSuccess(null, Collections.emptyList());
      }

      if(target.location() == ScriptLocation.ASSEMBLY ||
         target.location() == ScriptLocation.ASSEMBLY_ONCLICK)
      {
         return dryRunAssembly(vs, scope, target, script);
      }

      // vs-init / vs-load: no assembly state to snapshot; just run and report result
      return dryRunViewsheetLevel(scope, target, script);
   }

   // =========================================================================
   // Live-run
   // =========================================================================

   /**
    * Execute the script at {@code target} on the shared runtime (live).
    * Side effects are real and visible to the browser user.
    *
    * <p>If the script contains destructive globals and
    * {@code confirmed} is {@code false}, returns a result with
    * {@code requiresConfirmation=true} and does NOT execute. The agent layer
    * should surface this flag to the user and call again with
    * {@code confirmed=true} once they approve.</p>
    *
    * @param rvs       the joined runtime viewsheet
    * @param target    the script location to execute
    * @param confirmed {@code true} when the user has explicitly confirmed that
    *                  destructive globals should be allowed to run
    * @return a {@link ScriptExecResult}; caller is responsible for triggering
    *         the browser broadcast after a successful live-run
    * @throws PairingException if the sandbox or scope is unavailable
    */
   public ScriptExecResult runLive(RuntimeViewsheet rvs, ScriptTarget target, boolean confirmed)
      throws PairingException
   {
      ViewsheetSandbox box = requireSandbox(rvs);
      Viewsheet vs = rvs.getViewsheet();
      String script = resolveScriptText(vs, target);

      if(script == null || script.isEmpty()) {
         return ScriptExecResult.success(null);
      }

      // Guardrail: detect destructive globals before running
      if(!confirmed) {
         List<String> detected = detectDestructiveGlobals(script);

         if(!detected.isEmpty()) {
            return ScriptExecResult.liveRunNeedsConfirmation(detected);
         }
      }

      if(target.location() == ScriptLocation.ASSEMBLY) {
         return runLiveAssembly(box, vs, target);
      }

      // vs-init / vs-load / onClick: fall back to scope-based execution
      ViewsheetScope scope = box.getScope();
      ScriptExecResult result = scope.executeStructured(script, null);

      if(!result.ok()) {
         return result;
      }

      return ScriptExecResult.success(result.value());
   }

   // =========================================================================
   // Private — dry-run helpers
   // =========================================================================

   private ScriptExecResult dryRunAssembly(Viewsheet vs, ViewsheetScope scope,
                                            ScriptTarget target, String script)
   {
      Assembly a = vs.getAssembly(target.assemblyName());

      if(!(a instanceof VSAssembly vsa)) {
         return ScriptExecResult.dryRunFailure(
            new ScriptError("Assembly not found: " + target.assemblyName(), null));
      }

      VSAssemblyInfo vinfo = vsa.getVSAssemblyInfo();

      // Two clones: one to compute the hint (gets mutated by copyInfo), one to restore.
      VSAssemblyInfo snapshot = (VSAssemblyInfo) vinfo.clone();
      VSAssemblyInfo restorePoint = (VSAssemblyInfo) vinfo.clone();

      // Resolve the scriptable scope for this assembly.
      VSAScriptable scriptable = scope.getVSAScriptable(target.assemblyName());

      ScriptExecResult result;

      try {
         result = scope.executeStructured(script, scriptable);
      }
      catch(Exception ex) {
         // Unexpected — restore and surface as failure.
         vinfo.copyInfo(restorePoint);
         return ScriptExecResult.dryRunFailure(new ScriptError(ex.getMessage(), null));
      }

      List<String> changed = new ArrayList<>();

      if(result.ok()) {
         // Detect what changed: snapshot.copyInfo(vinfo) computes the diff
         // and returns a non-zero hint when vinfo differs from snapshot's original values.
         int hint = snapshot.copyInfo(vinfo);

         if(hint != VSAssembly.NONE_CHANGED) {
            changed.add(target.assemblyName());
         }
      }

      // Always restore the original state — dry-run must not mutate the shared runtime.
      vinfo.copyInfo(restorePoint);

      if(!result.ok()) {
         return ScriptExecResult.dryRunFailure(result.error());
      }

      return ScriptExecResult.dryRunSuccess(result.value(), changed);
   }

   private ScriptExecResult dryRunViewsheetLevel(ViewsheetScope scope,
                                                   ScriptTarget target, String script)
   {
      // vs-init/vs-load scripts modify the viewsheet-level scope; restoring the full
      // viewsheet state after execution is not attempted here (partial isolation).
      // The compile + syntax check still catches the majority of errors.
      ScriptExecResult result = scope.executeStructured(script, null);

      if(!result.ok()) {
         return ScriptExecResult.dryRunFailure(result.error());
      }

      return ScriptExecResult.dryRunSuccess(result.value(), Collections.emptyList());
   }

   // =========================================================================
   // Private — live-run helpers
   // =========================================================================

   private ScriptExecResult runLiveAssembly(ViewsheetSandbox box, Viewsheet vs,
                                              ScriptTarget target)
      throws PairingException
   {
      Assembly a = vs.getAssembly(target.assemblyName());

      if(!(a instanceof VSAssembly vsa)) {
         return ScriptExecResult.failure(
            new ScriptError("Assembly not found: " + target.assemblyName(), null));
      }

      try {
         box.executeScript(vsa);
         return ScriptExecResult.success(null);
      }
      catch(Exception ex) {
         return ScriptExecResult.failure(new ScriptError(ex.getMessage(), null));
      }
   }

   // =========================================================================
   // Private — utilities
   // =========================================================================

   private ViewsheetSandbox requireSandbox(RuntimeViewsheet rvs) throws PairingException {
      return rvs.getViewsheetSandbox()
         .orElseThrow(() -> new PairingException("No ViewsheetSandbox available for session"));
   }

   /**
    * Returns the script text for the given target, or {@code null} if no script is set.
    */
   private String resolveScriptText(Viewsheet vs, ScriptTarget target) {
      return switch(target.location()) {
         case VS_INIT -> vs.getViewsheetInfo().getOnInit();
         case VS_LOAD -> vs.getViewsheetInfo().getOnLoad();
         case ASSEMBLY -> {
            Assembly a = vs.getAssembly(target.assemblyName());
            yield a instanceof VSAssembly vsa ? vsa.getVSAssemblyInfo().getScript() : null;
         }
         case ASSEMBLY_ONCLICK -> {
            Assembly a = vs.getAssembly(target.assemblyName());

            if(!(a instanceof VSAssembly vsa)) {
               yield null;
            }

            VSAssemblyInfo info = vsa.getVSAssemblyInfo();
            yield extractOnClick(info);
         }
      };
   }

   private String extractOnClick(VSAssemblyInfo info) {
      if(info instanceof inetsoft.uql.viewsheet.internal.ClickableOutputVSAssemblyInfo co) {
         return co.getOnClick();
      }

      if(info instanceof inetsoft.uql.viewsheet.internal.ClickableInputVSAssemblyInfo ci) {
         return ci.getOnClick();
      }

      return null;
   }

   /**
    * Scans the script text for calls to known destructive globals.
    *
    * @return a list of detected destructive global names; empty when none found
    */
   List<String> detectDestructiveGlobals(String script) {
      if(script == null || script.isEmpty()) {
         return Collections.emptyList();
      }

      List<String> found = new ArrayList<>();

      for(String name : DESTRUCTIVE_GLOBALS) {
         if(script.contains(name)) {
            found.add(name);
         }
      }

      return found;
   }
}
