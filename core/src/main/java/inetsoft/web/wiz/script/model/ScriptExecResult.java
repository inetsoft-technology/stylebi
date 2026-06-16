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
package inetsoft.web.wiz.script.model;

import java.util.List;

/**
 * Structured result from a script execution.
 *
 * <p>Used by both {@link inetsoft.report.script.viewsheet.ViewsheetScope#executeStructured}
 * (simple execute) and {@link inetsoft.web.wiz.script.ScriptExecuteService} (dry-run and
 * live-run).</p>
 *
 * <p>On success: {@code ok=true}, {@code value} carries the script return value
 * (may be {@code null}), {@code error} is {@code null}.</p>
 *
 * <p>On failure: {@code ok=false}, {@code error} carries the message and Rhino suggestion,
 * {@code value} is {@code null}.</p>
 *
 * <p>{@code changed} is populated by dry-run: a list of assembly names whose
 * in-memory state would have changed had this been a live execution. It is
 * {@code null} for plain executions that do not track assembly changes.</p>
 *
 * <p>{@code requiresConfirmation} is set by live-run when the script references
 * destructive globals (e.g. {@code saveWorksheet}, {@code runQuery}); the agent
 * should ask the user before re-calling with confirmation.</p>
 *
 * @param ok                   {@code true} if the script compiled and ran without errors
 * @param value                the return value (may be {@code null}); valid only when {@code ok}
 * @param error                structured error info; non-{@code null} only when {@code !ok}
 * @param changed              assembly names that changed (dry-run only); may be {@code null}
 * @param requiresConfirmation {@code true} when the script contains destructive globals
 *                             (live-run only); {@code false} otherwise
 */
public record ScriptExecResult(
   boolean ok,
   Object value,
   ScriptError error,
   List<String> changed,
   boolean requiresConfirmation)
{

   /** Convenience factory for a successful execution (no change tracking). */
   public static ScriptExecResult success(Object value) {
      return new ScriptExecResult(true, value, null, null, false);
   }

   /** Convenience factory for a failed execution. */
   public static ScriptExecResult failure(ScriptError error) {
      return new ScriptExecResult(false, null, error, null, false);
   }

   /** Convenience factory for a successful dry-run with changed-assembly list. */
   public static ScriptExecResult dryRunSuccess(Object value, List<String> changed) {
      return new ScriptExecResult(true, value, null, changed, false);
   }

   /** Convenience factory for a failed dry-run. */
   public static ScriptExecResult dryRunFailure(ScriptError error) {
      return new ScriptExecResult(false, null, error, null, false);
   }

   /** Convenience factory for a live-run result that requires confirmation. */
   public static ScriptExecResult liveRunNeedsConfirmation(List<String> destructiveGlobals) {
      return new ScriptExecResult(false, null,
         new ScriptError("Script contains destructive globals: " + destructiveGlobals +
            ". Confirm with the user before proceeding.", null),
         null, true);
   }
}
