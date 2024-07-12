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
package inetsoft.util.script;

import inetsoft.report.script.viewsheet.ViewsheetScope;
import inetsoft.util.audit.ExecutionBreakDownRecord;
import inetsoft.util.profile.ProfileUtils;
import org.mozilla.javascript.Script;
import org.mozilla.javascript.Scriptable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Hashtable;
import java.util.regex.Pattern;

/**
 * Encapsulate a script engine.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class JavaScriptEnv implements ScriptEnv {
   /**
    * Create a script engine. The setReport() method must be called
    * before it's used.
    */
   public JavaScriptEnv() {
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
            LOG.error("Failed to reset JavaScript Engine", ex);
         }
      }
   }

   /**
    * Set the parent scripting environment of this script engine. All objects
    * in the parent engine are accessible in this environment.
    */
   @Override
   public synchronized void setParent(Object scope) {
      init();
      engine.setParent((Scriptable) scope);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void addTopLevelParentScope(Object child) {
      init();
      engine.addTopLevelParentScope((Scriptable) child);
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
    * @param script script object.
    * @param scope scope this script should execute in.
    * @param target report/viewsheet scope.
    */
   @Override
   public Object exec(Object script, Object scope, Object rscope, Object target) throws Exception {
      // for Feature #26586, add javascript execution time record for current report.
      return ProfileUtils.addExecutionBreakDownRecord(target,
         ExecutionBreakDownRecord.JAVASCRIPT_PROCESSING_CYCLE, args -> {
            return doExec(script, scope, rscope);
         });
   }

   /**
    * Execute a script.
    * @param script script object.
    * @param scope scope this script should execute in.
    */
   private Object doExec(Object script, Object scope, Object rscope) throws Exception {
      init();

      Scriptable scriptable = (scope instanceof Scriptable) ? (Scriptable) scope : null;

      if(scriptable != null) {
         FormulaContext.pushScope(scriptable);
      }

      try {
         return engine.exec((Script) script, scope, (Scriptable) rscope);
      }
      finally {
         if(scriptable != null) {
            FormulaContext.popScope();
         }
      }
   }

   /**
    * Compile a script as a function. This is only used to check a function
    * script syntax.
    */
   @Override
   public synchronized void checkFunction(String name, String cmd)
      throws Exception
   {
      init();
      engine.checkFunction(name, cmd);
   }

   /**
    * Get all property ids of an element. If the element does not exist
    * in the scope, get all ids in the report.
    * @param id element id.
    * @param scope script scope.
    * @param parent true to include all ids in the parents.
    * @return a list of object ids in the scope. Function objects are
    * added '()' at the end.
    */
   @Override
   public Object[] getIds(Object id, Object scope, boolean parent) {
      init();
      return engine.getIds(id, (Scriptable) scope, parent);
   }

   /**
    * Get all property display names of an element for property tree. If the
    * element does not exist in the scope, get all display names in the report.
    * @param id element id.
    * @param scope script scope.
    * @param parent true to include all display names in the parents.
    * @return a list of object display names in the scope. Function objects are
    * added '()' at the end.
    */
   @Override
   public Object[] getDisplayNames(Object id, Object scope, boolean parent) {
      init();
      return engine.getDisplayNames(id, (Scriptable) scope, parent);
   }

   /**
    * Get all property names of an element for property tree. If the
    * element does not exist in the scope, get all names in the report.
    * @param id element id.
    * @param scope script scope.
    * @param parent true to include all names in the parents.
    * @return a list of object names in the scope. Function objects are
    * added '()' at the end.
    */
   @Override
   public Object[] getNames(Object id, Object scope, boolean parent) {
      init();
      return engine.getNames(id, (Scriptable) scope, parent);
   }

   /**
    * Find the scope of the specified object.
    */
   @Override
   public Object getScope(Object id, Object scope) {
      init();

      return engine.getScriptable(id, (Scriptable) scope);
   }

   /**
    * Define a variable in the report scope.
    * @param name variable name.
    * @param obj variable value.
    */
   @Override
   public void put(String name, Object obj) {
      init();
      vars.put(name, obj);
      engine.put(name, obj);
   }

   /**
    * Get the variable set using put().
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
   public String getSuggestion(Exception ex, String fieldName, Scriptable scope) {
      return getSuggestion0(ex, fieldName, scope);
   }

   /**
    * Get a suggested fix for a script error.
    * @return suggestion text, or null if no suggestion is available.
    */
   @Override
   public String getSuggestion(Exception ex, String fieldName) {
      return getSuggestion0(ex, fieldName, null);
   }

   public String getSuggestion0(Exception ex, String fieldName, Scriptable scope) {
      String msg = ex.getMessage();

      if(msg == null) {
         return null;
      }

      if(msg.contains("Cannot add a property to a sealed object")) {
         return "Use 'var' to declare the variable";
      }
      else if(msg.contains("undefined is not a function")) {
         return "Check if function name is correct, and whether it's global or object method";
      }
      else if(msg.contains("undefined value has no properties")) {
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
            LOG.error("Failed to init JavaScript Engine", e);
         }
      }
   }

   /**
    * Create a script engine instance.
    */
   protected JavaScriptEngine createScriptEngine() {
      return new JavaScriptEngine();
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

   protected JavaScriptEngine engine;
   protected Hashtable vars = new Hashtable();
   protected boolean sql;

   private static Pattern pattern =
      Pattern.compile("[\\s\\S]*[\"]([^\"]+)[\"] is not defined[\\s\\S]*");
   private static final Logger LOG =
      LoggerFactory.getLogger(JavaScriptEnv.class);
}
