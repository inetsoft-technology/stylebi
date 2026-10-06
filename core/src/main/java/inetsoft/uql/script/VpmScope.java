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
package inetsoft.uql.script;

import inetsoft.uql.VariableTable;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.erm.vpm.VirtualPrivateModel;
import inetsoft.uql.util.XUtil;
import inetsoft.util.script.*;
import inetsoft.util.script.graal.ScriptScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Principal;
import java.util.*;

/**
 * VpmScope, the scriptable object to execute vpm script.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class VpmScope implements ScriptScope {
   /**
    * Execute the script.
    * @param statement the specified script statement.
    * @param scope the specified scope.
    * @return the executed result.
    */
   public static Object execute(String statement, VpmScope scope)
      throws Exception
   {
      Object script = null;
      // Bug #75669: reset the referenced-variable tracking so it only reflects access
      // made by this script execution (the setUp putMember(...) calls in
      // VpmCondition.evaluate / HiddenColumns.getHiddenColumns happen before this call
      // and must not count).
      scope.usedVars.clear();
      ScriptEnv senv = ScriptEnvRepository.getScriptEnv();
      senv.init();
      senv.put("vpm", new inetsoft.util.script.graal.ScopeProxy(scope));

      // Bug #77615, a scope member is found before a global, so the built-in hasTable would
      // hide a script library function of the same name and change what an existing script
      // calling it does. The library function takes precedence.
      if(senv.get(HAS_TABLE) != null) {
         scope.removeMember(HAS_TABLE);
      }

      // compile the script statement
      try {
         script = senv.compile(statement);
      }
      catch(Exception ex) {
         String suggestion = senv.getSuggestion(ex, null, scope);

         if(suggestion != null) {
            LOG.error(String.format(
               "Script failed: %s\n%sTo fix: %s",
               ex.getMessage(), XUtil.numbering(statement), suggestion));
         }
         else {
            LOG.error(String.format(
               "Script failed: %s\n%s",
               ex.getMessage(), XUtil.numbering(statement)));
         }

         throw ex;
      }

      // a vpm script is written by an administrator, so it runs unrestricted even when a
      // query runs it while a restricted end-user formula runs (bug #77396)
      boolean restricted = FormulaContext.isRestricted();

      // execute the script object
      try {
         FormulaContext.setRestricted(false);
         return senv.exec(script, scope, null, null);
      }
      catch(Exception ex) {
         LOG.error(String.format(
            "Script failed: %s\n%s",
            ex.getMessage(), XUtil.numbering(statement)));
         throw ex;
      }
      finally {
         FormulaContext.setRestricted(restricted);
      }
   }

   /**
    * Constructor.
    */
   public VpmScope() {
      super();
      // runQuery is exposed via the runQuery() instance method, installed as a
      // ScriptFunction so it is callable from scripts under GraalJS.
      members.put("runQuery", new inetsoft.util.script.graal.ScriptFunction(
         this, getClass(), "runQuery", String.class, Object.class));
      members.put(HAS_TABLE, new inetsoft.util.script.graal.ScriptFunction(
         this, getClass(), HAS_TABLE, String.class));
   }

   /**
    * Execute a query.
    * @param name query name.
    * @param val query parameters as an array of pairs. Each pair is an
    * array of name and value.
    */
   public Object runQuery(String name, Object val) {
      return XUtil.runQuery(name, val, getUser(), null);
   }

   /**
    * Set the tables of the query, available to the script as the <code>tables</code> array
    * and tested by {@link #hasTable(String)}. The names are as the query stores them, so a
    * parsed sql text has the helper's identifier quotes (<tt>"sa"."t"</tt> on PostgreSQL)
    * while a query built from a model does not (<tt>sa.t</tt>).
    * @param tables the tables of the query.
    */
   public void setTables(String[] tables) {
      this.tables = tables == null ? null : tables.clone();
      members.put("tables", new StringArray("table", tables));
   }

   /**
    * Check if the query reads a table. Bug #77615, the <code>tables</code> array has the
    * names as the query stores them, quoted or not depending on how the query was built, so
    * a script comparing the names as written misses a quoted table. The names are compared
    * by {@link VirtualPrivateModel#isSameTable(String, String)}, so identifier quotes and
    * case are ignored, and a name with fewer segments matches the trailing segments of the
    * other one, e.g. <tt>hasTable('sa.t')</tt> and <tt>hasTable('t')</tt> are both true for
    * <tt>"sa"."t"</tt>, but <tt>hasTable('sb.t')</tt> is not. So a query table stored without
    * a schema matches the name in any schema: <tt>hasTable('sa.t')</tt> and
    * <tt>hasTable('sb.t')</tt> are both true for <tt>t</tt>.
    * <p>
    * The tables are those {@link #setTables(String[]) set} on this scope: for the trigger and
    * hidden columns scripts the tables of the query and its sub queries, for a condition
    * script the tables of the query or sub query the condition is added to. Changing the
    * <code>tables</code> array in the script does not change the result.
    * <p>
    * A script library function named <tt>hasTable</tt> takes precedence, the built-in is not
    * available to the script when the library defines one.
    * @param name the table name, quoted or not.
    * @return <tt>true</tt> if the query reads the table, <tt>false</tt> if not or if the
    * name is null or empty.
    */
   public boolean hasTable(String name) {
      if(tables == null || name == null || name.trim().isEmpty()) {
         return false;
      }

      for(String table : tables) {
         if(VirtualPrivateModel.isSameTable(table, name)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Set the parameters.
    * @param vars the specified variable table.
    */
   public void setVariableTable(VariableTable vars) {
      this.vars = vars;
      members.put("parameter", new VariableScriptable(this.vars));
   }

   /**
    * Get the parameters.
    * @return the parameters.
    */
   public VariableTable getVariableTable() {
      return vars;
   }

   /**
    * Set the user of the scriptable.
    * @param user the specified user.
    */
   public void setUser(Principal user) {
      this.user = user;

      members.put("roles", XUtil.getUserRoleNames(user));
      members.put("groups", XUtil.getUserGroups(user));

      // @by stephenwebster, For bug1413383077871
      // Make sure to copy the user's parameters into the variable table
      // so they are available for access in the VPM script.
      // It is important to note that this is highly dependent on order of
      // execution.  If setVariableTable is called after setUser, this has no effect.
      // It looks like in the few places a VPMScope is instantiated, the order is ok.
      // This order could be encapulated in another constructor, but for now it seems safe.
      if(user instanceof XPrincipal) {
         if(this.vars == null) {
            this.vars = new VariableTable();
         }

         this.vars.copyParameters((XPrincipal) user);
      }
   }

   /**
    * Ge the user of the scriptable.
    * @return the user.
    */
   public Principal getUser() {
      return user;
   }

   /**
    * Check if has a property.
    * @param id the specified property.
    */
   @Override
   public boolean hasMember(String id) {
      return "user".equals(id) || members.containsKey(id);
   }

   /**
    * Get the value of a property.
    * @param id the specified property.
    */
   @Override
   public Object getMember(String id) {
      if(id.equals("user")) {
         return user == null ? null : XUtil.getUserName(user);
      }

      // Bug #75669: record that the script referenced this variable. Under Rhino a VPM
      // trigger script activated its output simply by referencing (or assigning) the
      // relevant variable (e.g. `condition`, `hiddenColumns`), relying on that value
      // becoming the script's completion value. GraalJS follows current ECMAScript
      // completion-value semantics (e.g. a trailing if(false) yields undefined,
      // clobbering a loop's completion value), so the reference alone may no longer
      // surface as the result; callers fall back to the referenced variable's value when
      // it was used. See VpmCondition.evaluate() and HiddenColumns.getHiddenColumns().
      usedVars.add(id);

      // the ScopeProxy/HostAccess layer now handles array wrapping
      return members.get(id);
   }

   /**
    * Set a named property in this object.
    */
   @Override
   public void putMember(String id, Object value) {
      // Bug #75669: assigning a variable in the script also counts as using it.
      usedVars.add(id);

      members.put(id, value);
   }

   /**
    * Determine whether the most recently executed script referenced or assigned the
    * given variable. Reset at the start of each {@link #execute}.
    */
   public boolean isVariableUsed(String name) {
      return usedVars.contains(name);
   }

   /**
    * Determine whether the most recently executed script referenced or assigned the
    * <code>condition</code> variable. Reset at the start of each {@link #execute}.
    */
   public boolean isConditionUsed() {
      return isVariableUsed(CONDITION);
   }

   /**
    * Remove a named property from this object.
    */
   @Override
   public boolean removeMember(String id) {
      return members.remove(id) != null;
   }

   /**
    * Get an array of property ids.
    */
   @Override
   public Object[] getMemberKeys() {
      return members.keySet().toArray();
   }

   /**
    * Get the parent scope of the object.
    */
   @Override
   public ScriptScope getParentScope() {
      return parent;
   }

   /**
    * Set the parent scope of the object.
    */
   public void setParentScope(ScriptScope parent) {
      this.parent = parent;
   }

   /**
    * Get the name of this scriptable.
    * @return the name of this scriptable.
    */
   public String getClassName() {
      return "VpmScope";
   }

   private Principal user;
   private VariableTable vars;
   private String[] tables;
   private ScriptScope parent;
   private final Set<String> usedVars = new HashSet<>();
   private final Map<String, Object> members = new LinkedHashMap<>();

   private static final String CONDITION = "condition";
   private static final String HAS_TABLE = "hasTable";

   private static final Logger LOG =
      LoggerFactory.getLogger(VpmScope.class);
}
