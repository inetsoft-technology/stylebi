/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.report.script.formula;

import inetsoft.report.script.TableRow;
import inetsoft.report.script.TableRowScope;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.util.script.*;
import inetsoft.util.script.graal.ScriptScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Hashtable;
import java.util.Objects;

/**
 * Provide interface for executing formula scripts.
 *
 * @version 8.0, 7/27/2005
 * @author InetSoft Technology Corp
 */
public class FormulaEvaluator {
   /**
    * Execute a script in the given scope. If scope is not specified, the
    * current formula scope is used.
    */
   public static Object exec(String expr, ScriptScope scope,
                             String subname, ScriptScope subscope) {
      if(scope == null) {
         scope = FormulaContext.getScope();
      }

      // when no outer scope is available (e.g. CALC aggregate functions invoked
      // outside of a report/viewsheet script context), evaluate the expression
      // using only the row scope so the row columns can still be resolved. (#75423)
      if(scope == null) {
         ScriptScope execScope = subscope;

         if(subscope instanceof TableRow) {
            TableRowScope tableRowScope = new TableRowScope((TableRow) subscope, null);
            tableRowScope.setBuiltinDate(expr.contains("new Date("));
            execScope = tableRowScope;
         }

         return exec(expr, execScope);
      }

      Object ofield = scope.getMember(subname);
      boolean hadField = scope.hasMember(subname);

      try {
         scope.putMember(subname, subscope);
         ScriptScope execScope = subscope;

         if(subscope instanceof TableRow) {
            TableRowScope tableRowScope = new TableRowScope((TableRow) subscope, null);
            tableRowScope.setBuiltinDate(expr.contains("new Date("));
            execScope = tableRowScope;
         }

         if(execScope instanceof TableRowScope) {
            ((TableRowScope) execScope).setParentScope(scope);
         }

         return exec(expr, execScope);
      }
      finally {
         if(hadField) {
            scope.putMember(subname, ofield);
         }
         else {
            scope.removeMember(subname);
         }
      }
   }

   /**
    * Execute a script in the given scope. If scope is not specified, the
    * current formula scope is used.
    */
   public static Object exec(String expr, ScriptScope scope) {
      if(scope == null) {
         scope = FormulaContext.getScope();
      }

      try {
         // Reuse a warm, per-thread ScriptEnv instead of creating a fresh one
         // on every call. Under GraalJS an uninitialized env builds a complete
         // Context on first exec (Truffle language init, reflected globals,
         // library-function parsing), so newing one per calc-formula evaluation
         // dominated calc-table/chart render time. GraalJavaScriptEnv holds one
         // Context guarded by a ReentrantLock, so a per-thread env avoids the
         // rebuild without serializing evaluations across threads; exec() swaps
         // the global scope per call, so reuse across different scopes is safe.
         ScriptEnv senv = ENV.get();

         // A reused env keeps its GraalJS Context across evaluations, so any
         // top-level var/function a formula declares hoists onto the shared
         // globalThis (#75596) and library functions are installed per the
         // org that first initialized it (LibManagerProvider.getManager(orgID)).
         // Reset the env when the executing organization changes so neither
         // declared globals nor org-scoped library functions leak across
         // tenants on a pooled thread. reset() is a no-op on a not-yet-inited
         // env, and only rebuilds on an actual tenant handoff (rare), not per
         // evaluation.
         //
         // Only reconcile at the outermost exec on this thread (depth == 0).
         // exec() is reentrant — a formula/condition can trigger a nested
         // FormulaEvaluator.exec (e.g. NamedCellRange resolving a "="-prefixed
         // reference) while the outer call is still inside context.eval on this
         // same thread. Resetting there would close the Context the outer eval
         // is running in and rebuild the engine's scopeProxy/context, which the
         // outer call restores in its finally. The org cannot change between an
         // outer and nested call on one thread during normal rendering, so
         // deferring the check to the outermost frame loses nothing.
         if(senv != null && DEPTH.get() == 0) {
            String org = currentOrgID();

            if(!Objects.equals(org, ENV_ORG.get())) {
               senv.reset();
               ENV_ORG.set(org);
            }
         }

         Object script = scriptcache.get(expr);

         if(script == null) {
            if(scriptcache.size() > 100) {
               scriptcache.clear();
            }

            script = senv.compile(expr);
            scriptcache.put(expr, script);
         }

         DEPTH.set(DEPTH.get() + 1);

         try {
            Object rc = senv.exec(script, scope, scope, null);
            return JavaScriptEngine.unwrap(rc);
         }
         finally {
            DEPTH.set(DEPTH.get() - 1);
         }
      }
      catch(Exception ex) {
         LOG.error("Failed to execute formula script: " + expr, ex);
      }

      return null;
   }

   /**
    * The current organization id, or null if it can't be determined (e.g. a
    * thread with no associated principal). Used only as a change key to decide
    * when the per-thread env must be reset, so a null is a safe sentinel.
    */
   private static String currentOrgID() {
      try {
         return OrganizationManager.getInstance().getCurrentOrgID();
      }
      catch(Throwable ex) {
         return null;
      }
   }

   private static Hashtable scriptcache = new Hashtable(); // expr -> script
   // one warm ScriptEnv per thread, reused across evaluations
   private static final ThreadLocal<ScriptEnv> ENV =
      ThreadLocal.withInitial(ScriptEnvRepository::getScriptEnv);
   // org id the per-thread env was last initialized for (reset on change)
   private static final ThreadLocal<String> ENV_ORG = new ThreadLocal<>();
   // reentrancy depth of exec() on the current thread; the env is only
   // reconciled/reset at the outermost frame (depth 0)
   private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);
   private static final Logger LOG =
      LoggerFactory.getLogger(FormulaEvaluator.class);
}
