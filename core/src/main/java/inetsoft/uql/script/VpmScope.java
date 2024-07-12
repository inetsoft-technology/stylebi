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
package inetsoft.uql.script;

import inetsoft.report.LibManager;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.util.XUtil;
import inetsoft.util.ConfigurationContext;
import inetsoft.util.Tool;
import inetsoft.util.script.*;
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
public class VpmScope extends ScriptableObject {
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
      Context cx = Context.enter();
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

      cx = Context.enter();

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
      Scriptable root = (Scriptable) cx.initStandardObjects(new RootScope());

      LibManager mgr = LibManager.getManager();
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
         FunctionObject func = new FunctionObject2(this, getClass(), "runQuery",
                                                   String.class, Object.class);
         put("runQuery", this, func);
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
      put("parameter", this, new VariableScriptable(this.vars));
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

      // put("roles", this, new StringArray("role", XUtil.getUserRoles(user)));
      // put("groups", this, new StringArray("group", XUtil.getUserGroups(user)));
      put("roles", this, XUtil.getUserRoleNames(user));
      put("groups", this, XUtil.getUserGroups(user));

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
    * @param start the specified scriptable to start.
    */
   @Override
   public boolean has(String id, Scriptable start) {
      return "user".equals(id) || super.has(id, start);
   }

   /**
    * Get the value of a property.
    * @param id the specified property.
    * @param start the specified scriptable to start.
    */
   @Override
   public Object get(String id, Scriptable start) {
      if(id.equals("user")) {
         return user == null ? null : XUtil.getUserName(user);
      }

      Object val = super.get(id, start);

      // Bug #61669, use NativeArray instead of NativeJavaArray
      // same as what we do in inetsoft.uql.script.VariableScriptable.get()
      // to make sure that includes() function works properly
      if(val instanceof Object[]) {
         NativeArray arr = new NativeArray((Object[]) val);
         ScriptRuntime.setBuiltinProtoAndParent(arr, getParentScope(),
                                                TopLevel.Builtins.Array);
         return arr;
      }

      return val;
   }

   /**
    * Get the name of this scriptable.
    * @return the name of this scriptable.
    */
   @Override
   public String getClassName() {
      return "VpmScope";
   }

   /**
    * Clone the scriptable.
    * @return the cloned scriptable.
    */
   @Override
   public Object clone() {
      try {
         VpmScope obj = (VpmScope) super.clone();
         return obj;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone object", ex);
      }

      return null;
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

   private static final String ROOT_KEY = VpmScope.class.getName() + ".rootScope";
   private static final ReadWriteLock ROOT_LOCK = new ReentrantReadWriteLock(true);

   private static final Logger LOG =
      LoggerFactory.getLogger(VpmScope.class);
}
