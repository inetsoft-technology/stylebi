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
package inetsoft.util.script;

import org.mozilla.javascript.Scriptable;

/**
 * Encapsulate a script engine.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public interface ScriptEnv {
   /**
    * Id for the onInit scope. It is not reset by a report.
    */
   public static final String INIT_SCOPE = "__INIT_SCOPE__";

   /**
    * Reset the scripting environment. This must be called if new elements
    * are added or existing elements are removed.
    */
   public abstract void reset();

   /**
    * Set whether is for sql only.
    */
   public abstract void setSQL(boolean sql);

   /**
    * check if is for sql only.
    */
   public abstract boolean isSQL();

   /**
    * Set the parent scripting scope of this script engine. All objects
    * in the parent engine are accessible in this environment.
    */
   public abstract void setParent(Object scope);

   /**
    * Sets the parent scope of <tt>child</tt> to the top-level scope.
    *
    * @param child the scriptable to be modified.
    */
   public abstract void addTopLevelParentScope(Object child);

   /**
    * Compile a script into a script object.
    */
   public abstract Object compile(String cmd) throws Exception;

   /**
    * Compile a script into a script object.
    */
   public abstract Object compile(String cmd, boolean fieldOnly)
      throws Exception;

   /**
    * Execute a script.
    * @param script script object.
    * @param scope scope this script should execute in.
    * @param rscope the top report/viewsheet scope.
    * @param target the target object(report/vs) to running the script.
    */
   public abstract Object exec(Object script, Object scope, Object rscope, Object target)
      throws Exception;

   /**
    * Compile a script as a function. This is only used to check a function
    * script syntax.
    */
   public abstract void checkFunction(String name, String cmd) throws Exception;

   /**
    * Get all property ids of an element. If the element does not exist
    * in the scope, get all ids in the report.
    * @param id element id.
    * @param scope script scope.
    * @param parent true to include all ids in the parents.
    */
   public abstract Object[] getIds(Object id, Object scope, boolean parent);

   /**
    * Get all property display names of an element for property tree. If the
    * element does not exist in the scope, get all display names in the report.
    * @param id element id.
    * @param scope script scope.
    * @param parent true to include all display names in the parents.
    * @return a list of object display names in the scope. Function objects are
    * added '()' at the end.
    */
   public Object[] getDisplayNames(Object id, Object scope, boolean parent);

   /**
    * Get all property names of an element for property tree. If the
    * element does not exist in the scope, get all names in the report.
    * @param id element id.
    * @param scope script scope.
    * @param parent true to include all names in the parents.
    * @return a list of object names in the scope. Function objects are
    * added '()' at the end.
    */
   public Object[] getNames(Object id, Object scope, boolean parent);

   /**
    * Find the scope of the specified object.
    */
   public abstract Object getScope(Object id, Object scope);

   /**
    * Get the variable set using put().
    * @param name variable name.
    */
   public abstract Object get(String name);

   /**
    * Define a variable in the report scope.
    * @param name variable name.
    * @param obj variable value.
    */
   public abstract void put(String name, Object obj);

   /**
    * Remove a variable from the scripting environment.
    * @param name variable name.
    */
   public abstract void remove(String name);

   /**
    * Get a suggested fix for a script error.
    * @return suggestion text, or null if no suggestion is available.
    */
   public String getSuggestion(Exception ex, String fieldName);

   /**
    * Get a suggested fix for a script error.
    * @return suggestion text, or null if no suggestion is available.
    */
   public String getSuggestion(Exception ex, String field, Scriptable scope);

   /**
    * Initialize the script engine.
    */
   public void init();
}
