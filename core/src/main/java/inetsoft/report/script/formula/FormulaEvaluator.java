/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.report.script.formula;

import inetsoft.report.script.TableRow;
import inetsoft.report.script.TableRowScope;
import inetsoft.util.script.*;
import org.mozilla.javascript.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringReader;
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
   public static Object exec(String expr, Scriptable scope,
                             String subname, Scriptable subscope) {
      if(scope == null) {
         scope = FormulaContext.getScope();
      }

      Object ofield = scope.get(subname, scope);

      try {
         scope.put(subname, scope, subscope);
         Scriptable execScope = subscope;

         if(subscope instanceof TableRow) {
            TableRowScope tableRowScope = new TableRowScope((TableRow) subscope, null);
            tableRowScope.setBuiltinDate(expr.contains("new Date("));
            execScope = tableRowScope;
         }

         execScope.setParentScope(scope);
         return exec(expr, execScope);
      }
      finally {
         if(ofield != null) {
            scope.put(subname, scope, ofield);
         }
         else {
            scope.delete(subname);
         }
      }
   }

   /**
    * Execute a script in the given scope. If scope is not specified, the
    * current formula scope is used.
    */
   public static Object exec(String expr, Scriptable scope) {
      if(scope == null) {
         scope = FormulaContext.getScope();
      }

      Script script = getScript(expr, scope);
      Context cx = TimeoutContext.enter();
      TimeoutContext.startClock();

      try {
         Object rc = script.exec(cx, scope);

         rc = JavaScriptEngine.unwrap(rc);
         return rc;
      }
      catch(Exception ex) {
         LOG.error("Failed to execute formula script: " + expr, ex);
      }
      finally {
         cx.exit();
      }

      return null;
   }

   /**
    * Get a compiled script for an expression.
    */
   private static Script getScript(String expr, Scriptable scope) {
      Script script = (Script) scriptcache.get(expr);

      if(script == null) {
         Context cx = TimeoutContext.enter();

         if(scriptcache.size() > 100) {
            scriptcache.clear();
         }

         try {
            script = cx.compileReader(scope, new StringReader(expr),
                                      "<expression>", 1, null);
            scriptcache.put(expr, script);
         }
         catch(Exception ex) {
            LOG.error("Syntax error: " + expr);
            throw new RuntimeException("Compilation failed: " + ex);
         }
         finally {
            Context.exit();
         }
      }

      return script;
   }

   private static Hashtable scriptcache = new Hashtable(); // expr -> script
   private static final Logger LOG =
      LoggerFactory.getLogger(FormulaEvaluator.class);
}
