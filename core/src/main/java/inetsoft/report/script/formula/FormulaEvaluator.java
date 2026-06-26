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
import inetsoft.util.script.*;
import inetsoft.util.script.graal.ScriptScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Hashtable;

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
         ScriptEnv senv = ScriptEnvRepository.getScriptEnv();
         Object script = scriptcache.get(expr);

         if(script == null) {
            if(scriptcache.size() > 100) {
               scriptcache.clear();
            }

            script = senv.compile(expr);
            scriptcache.put(expr, script);
         }

         Object rc = senv.exec(script, scope, scope, null);
         return JavaScriptEngine.unwrap(rc);
      }
      catch(Exception ex) {
         LOG.error("Failed to execute formula script: " + expr, ex);
      }

      return null;
   }

   private static Hashtable scriptcache = new Hashtable(); // expr -> script
   private static final Logger LOG =
      LoggerFactory.getLogger(FormulaEvaluator.class);
}
