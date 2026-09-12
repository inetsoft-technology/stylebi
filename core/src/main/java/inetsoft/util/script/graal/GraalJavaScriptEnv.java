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
package inetsoft.util.script.graal;

import inetsoft.report.script.viewsheet.ViewsheetScope;
import inetsoft.util.audit.ExecutionBreakDownRecord;
import inetsoft.util.profile.ProfileUtils;
import inetsoft.util.script.FormulaContext;
import inetsoft.util.script.ScriptEnv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Hashtable;

/**
 * GraalJS-based ScriptEnv implementation. Replaces JavaScriptEnv (Rhino).
 * Delegates to {@link GraalJavaScriptEngine} and pushes/pops
 * {@link FormulaContext} scope around each exec call.
 *
 * <p>Note: getSuggestion(Exception, String, Scriptable) accepts the Rhino type
 * for now because the ScriptEnv interface still declares it (Task 5.2 flips
 * both the interface and this method together).
 */
public class GraalJavaScriptEnv implements ScriptEnv {
   /**
    * Create a GraalJS-backed script environment.
    */
   public GraalJavaScriptEnv() {
   }

   /**
    * Reset the scripting environment.
    */
   @Override
   public synchronized void reset() {
      if(engine != null) {
         try {
            engine.init(vars);
         }
         catch(Exception ex) {
            LOG.error("Failed to reset GraalJavaScriptEngine", ex);
            // init(vars) closes the old Context before building the replacement,
            // so a failure here leaves engine referencing a closed Context that
            // init() (a no-op while engine != null) would never rebuild. Drop
            // the engine so the next compile()/exec() rebuilds it from scratch
            // rather than poisoning this (now long-lived, per-thread) env.
            engine = null;
         }
      }
   }

   /**
    * Set the parent scripting environment of this script engine. All objects
    * in the parent engine are accessible in this environment.
    * Not yet wired — no-op until Task 5.3 wires scope chaining.
    */
   @Override
   public synchronized void setParent(Object scope) {
      init();
      // TODO wired in Task 5.3: scope chaining via ScriptScope.getParentScope()
   }

   /**
    * {@inheritDoc}
    * Not yet wired — no-op until Task 5.3.
    */
   @Override
   public void addTopLevelParentScope(Object child) {
      init();
      // TODO wired in Task 5.3: scope chaining via ScriptScope.getParentScope()
   }

   /**
    * Compile a script into a script object.
    */
   @Override
   public synchronized Object compile(String cmd) throws Exception {
      return compile(cmd, false);
   }

   /**
    * Compile a script into a script object.
    */
   @Override
   public synchronized Object compile(String cmd, boolean fieldOnly) throws Exception {
      init();
      return engine.compile(cmd, fieldOnly);
   }

   /**
    * Execute a script.
    *
    * @param script script object.
    * @param scope  scope this script should execute in.
    * @param rscope the top report/viewsheet scope.
    * @param target the target object (report/vs) for profiling.
    */
   @Override
   public Object exec(Object script, Object scope, Object rscope, Object target)
      throws Exception
   {
      // for Feature #26586, add javascript execution time record for current report.
      return ProfileUtils.addExecutionBreakDownRecord(target,
         ExecutionBreakDownRecord.JAVASCRIPT_PROCESSING_CYCLE, args -> {
            return doExec(script, scope, rscope);
         });
   }

   /**
    * Execute a script with FormulaContext scope push/pop.
    */
   private Object doExec(Object script, Object scope, Object rscope) throws Exception {
      init();

      ScriptScope scriptScope = (scope instanceof ScriptScope) ? (ScriptScope) scope : null;

      if(scriptScope != null) {
         FormulaContext.pushScope(scriptScope);
      }

      try {
         return engine.exec(script, scope, rscope);
      }
      finally {
         if(scriptScope != null) {
            FormulaContext.popScope();
         }
      }
   }

   /**
    * Compile a script as a function. This is only used to check a function
    * script syntax.
    */
   @Override
   public synchronized void checkFunction(String name, String cmd) throws Exception {
      init();
      engine.checkFunction(name, cmd);
   }

   /**
    * Get all property ids of an element.
    * TODO wired in Task 5.3 — returns engine global member keys for now.
    */
   @Override
   public Object[] getIds(Object id, Object scope, boolean parent) {
      init();
      // TODO wired in Task 5.3: full scope-chain traversal
      return engine.getMemberKeys();
   }

   /**
    * Get all property display names of an element.
    * TODO wired in Task 5.3.
    */
   @Override
   public Object[] getDisplayNames(Object id, Object scope, boolean parent) {
      return getIds(id, scope, parent);
   }

   /**
    * Get all property names of an element.
    * TODO wired in Task 5.3.
    */
   @Override
   public Object[] getNames(Object id, Object scope, boolean parent) {
      return getIds(id, scope, parent);
   }

   /**
    * Find the scope of the specified object.
    * TODO wired in Task 5.3.
    */
   @Override
   public Object getScope(Object id, Object scope) {
      init();
      // TODO wired in Task 5.3: scope-chain lookup
      return null;
   }

   /**
    * Define a variable in the report scope.
    *
    * @param name variable name.
    * @param obj  variable value.
    */
   // FIX C: synchronized to close the reset()/put() race on the engine reference
   @Override
   public synchronized void put(String name, Object obj) {
      init();
      vars.put(name, obj);
      engine.put(name, obj);
   }

   /**
    * Get the variable set using put().
    *
    * @param name variable name.
    */
   @Override
   public Object get(String name) {
      Object val = null;

      if(engine != null) {
         val = engine.get(name);
      }

      return (val == null) ? vars.get(name) : val;
   }

   /**
    * Remove a variable from the scripting environment.
    *
    * @param name variable name.
    */
   @Override
   public void remove(String name) {
      vars.remove(name);

      synchronized(this) {
         if(engine != null) {
            engine.remove(name);
         }
      }
   }

   @Override
   public String getSuggestion(Exception ex, String fieldName, ScriptScope scope) {
      return getSuggestion0(ex, fieldName, scope);
   }

   /**
    * Get a suggested fix for a script error.
    *
    * @return suggestion text, or null if no suggestion is available.
    */
   @Override
   public String getSuggestion(Exception ex, String fieldName) {
      return getSuggestion0(ex, fieldName, null);
   }

   /**
    * Copied verbatim from JavaScriptEnv.getSuggestion0 (message-match strings
    * updated in Task 6.5).
    */
   public String getSuggestion0(Exception ex, String fieldName, ScriptScope scope) {
      String msg = ex.getMessage();

      if(msg == null) {
         return null;
      }

      // Each branch ORs the original Rhino phrasing with the GraalJS phrasing
      // (captured empirically in Task 6.5) so the matcher is robust to both engines.
      if(msg.contains("Cannot add a property to a sealed object") ||
         // GraalJS: "TypeError: Cannot add property <x>, object is not extensible"
         msg.contains("Cannot add property") ||
         msg.contains("not extensible"))
      {
         return "Use 'var' to declare the variable";
      }
      else if(msg.contains("undefined is not a function") ||
              // GraalJS: e.g. "TypeError: 5 is not a function"
              msg.contains("is not a function"))
      {
         return "Check if function name is correct, and whether it's global or object method";
      }
      else if(msg.contains("undefined value has no properties") ||
              // GraalJS: e.g. "TypeError: Cannot read property 'foo' of undefined"
              msg.contains("Cannot read") || msg.contains("of undefined"))
      {
         return "Check if the object you are trying to reference exists";
      }
      else if(msg.contains("is not defined")) {
         String str = "Check if the variable is defined, or use 'var' to declare the variable.";

         if(fieldName != null) {
            str += " Field/row values are referenced using field['columnName'].";
         }
         else if(scope instanceof ViewsheetScope) {
            StringBuffer stringBuffer =
               new StringBuffer("\nIf you are trying to use a data field called 'field'");
            stringBuffer.append(", please change to ['table name']['field'].");
            str += stringBuffer.toString();
         }

         return str;
      }

      return null;
   }

   /**
    * Initialize the script engine.
    */
   @Override
   public synchronized void init() {
      if(engine == null) {
         engine = createScriptEngine();

         try {
            engine.setSQL(sql);
            engine.init(vars);
         }
         catch(Exception e) {
            LOG.error("Failed to init GraalJavaScriptEngine", e);
            // init(vars) may close/replace the Context; a failure here leaves
            // engine referencing a broken/closed Context that this method (a
            // no-op while engine != null) would never rebuild. Drop it so the
            // next compile()/exec() rebuilds from scratch rather than poisoning
            // this (now long-lived, per-thread) env. Mirrors the reset() fix.
            engine = null;
         }
      }
   }

   /**
    * Create a script engine instance.
    */
   protected GraalJavaScriptEngine createScriptEngine() {
      return new GraalJavaScriptEngine();
   }

   /**
    * Set whether is for sql only.
    */
   @Override
   public void setSQL(boolean sql) {
      this.sql = sql;

      if(engine != null) {
         engine.setSQL(sql);
      }
   }

   /**
    * check if is for sql only.
    */
   @Override
   public boolean isSQL() {
      return sql;
   }

   // FIX C: volatile so unsynchronized readers (e.g. get()) see the latest value
   protected volatile GraalJavaScriptEngine engine;
   protected Hashtable vars = new Hashtable();
   protected boolean sql;

   private static final Logger LOG = LoggerFactory.getLogger(GraalJavaScriptEnv.class);
}
