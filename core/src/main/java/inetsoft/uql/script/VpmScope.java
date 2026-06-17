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

import inetsoft.report.LibManager;
import inetsoft.report.LibManagerProvider;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.util.XUtil;
import inetsoft.util.ConfigurationContext;
import inetsoft.util.script.*;
import inetsoft.util.script.graal.ScriptScope;
// NOTE (Feature #75423): the static root-scope plumbing below (Context,
// Scriptable, Function, FunctionObject, ScriptableObject root, standard
// objects) is Rhino script-engine substrate, replaced by the native-binding
// mechanism at the Milestone 4 cutover. Only the instance scope surface has
// been converted to ScriptScope; the static execute/getRoot/createRoot logic
// is preserved as-is.
import org.mozilla.javascript.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Principal;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

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
      ScriptEnv senv = ScriptEnvRepository.getScriptEnv();
      Context cx = SecureClassShutter.createSecureContext();
      Scriptable root = getRoot();
      root.put("vpm", root, scope);
      scope.setParentScope(root);

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
      finally {
         cx.exit();
      }

      cx = SecureClassShutter.createSecureContext();

      // execute the script object
      try {
         return senv.exec(script, scope, null, null);
      }
      catch(Exception ex) {
         LOG.error(String.format(
            "Script failed: %s\n%s",
            ex.getMessage(), XUtil.numbering(statement)));
         throw ex;
      }
      finally {
         cx.exit();
      }
   }

   /**
    * Get the root scope.
    * @return the root scope.
    */
   @SuppressWarnings("unchecked")
   private static Scriptable getRoot() {
      Scriptable root = null;
      ROOT_LOCK.readLock().lock();

      try {
         root = ConfigurationContext.getContext().get(ROOT_KEY);
      }
      finally {
         ROOT_LOCK.readLock().unlock();
      }

      if(root == null) {
         ROOT_LOCK.writeLock().lock();

         try {
            root = ConfigurationContext.getContext().get(ROOT_KEY);

            if(root == null) {
               root = createRoot();
               ConfigurationContext.getContext().put(ROOT_KEY, root);
            }
         }
         finally {
            ROOT_LOCK.writeLock().unlock();
         }
      }

      return root;
   }

   /**
    * Create a root scope.
    * @return the created root scope.
    */
   private static Scriptable createRoot() {
      Context cx = Context.getCurrentContext();
      Scriptable root = cx.initStandardObjects(new RootScope());

      LibManager mgr = LibManagerProvider.getInstance().getManager();
      Enumeration names = mgr.getScripts();

      while(names.hasMoreElements()) {
         String fname = (String) names.nextElement();
         String source = mgr.getScript(fname);

         try {
            addFunction(fname, source, root);
         }
         catch(Exception ex) {
            LOG.warn("Failed to add function " + fname + ": " +
                        source, ex);
         }
      }

      // get a script env, and let global function available
      ScriptEnv senv = ScriptEnvRepository.getScriptEnv();
      senv.init();
      senv.put("vpm", root);

      return root;
   }

   /**
    * Clear the root scope.
    */
   private static void clearRoot() {
      ROOT_LOCK.writeLock().lock();

      try {
         ConfigurationContext.getContext().remove(ROOT_KEY);
      }
      finally {
         ROOT_LOCK.writeLock().unlock();
      }
   }

   /**
    * Add one function to the scope.
    * @param fname the specified function name.
    * @param source the specified source code.
    * @param script the specified script object to add function on.
    */
   private static void addFunction(String fname, String source,
                                   Scriptable script)
      throws Exception
   {
      if(source == null || source.length() == 0) {
         return;
      }

      Context cx = TimeoutContext.enter();
      Function function =
         cx.compileFunction(script, source, "<" + fname + ">", 1, null);

      if(function != null) {
         script.put(fname, script, function);
      }

      Context.exit();
   }

   /**
    * Constructor.
    */
   public VpmScope() {
      super();

      try {
         // NOTE (Feature #75423): FunctionObject2 is a Rhino FunctionObject,
         // replaced by the native-binding mechanism at the Milestone 4 cutover.
         // VpmScope no longer extends Rhino's ScriptableObject, so 'this' can no
         // longer be passed as the Rhino scope; pass null until the M4 cutover.
         FunctionObject func = new FunctionObject2(null, getClass(), "runQuery",
                                                   String.class, Object.class);
         members.put("runQuery", func);
      }
      catch(Exception ex) {
         LOG.error("Failed to register functions", ex);
      }
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

      // the ScopeProxy/HostAccess layer now handles array wrapping
      return members.get(id);
   }

   /**
    * Set a named property in this object.
    */
   @Override
   public void putMember(String id, Object value) {
      members.put(id, value);
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

   /**
    * The top-most scope shared by vpm scopes.
    */
   private static class RootScope extends ScriptableObject {
      @Override
      public String getClassName() {
         return "RootScope";
      }

      @Override
      public Object[] getIds() {
         return getAllIds();
      }
   }

   private static Map roots = new HashMap();
   private Principal user;
   private VariableTable vars;
   private ScriptScope parent;
   private final Map<String, Object> members = new LinkedHashMap<>();

   private static final String ROOT_KEY = VpmScope.class.getName() + ".rootScope";
   private static final ReadWriteLock ROOT_LOCK = new ReentrantReadWriteLock(true);

   private static final Logger LOG =
      LoggerFactory.getLogger(VpmScope.class);
}
