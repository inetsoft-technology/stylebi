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
package inetsoft.report.script;

import inetsoft.report.LibManager;
import inetsoft.util.Cleaner;
import inetsoft.util.script.TimeoutContext;
import org.mozilla.javascript.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A scriptable holding the lib script functions.
 *
 * @version 11.5
 * @author InetSoft Technology Corp
 */
public class LibScriptable extends ScriptableObject {
   /**
    * Get a per scope (report) scriptable.
    */
   public LibScriptable(Scriptable funcScope) {
      this.funcScope = funcScope;
      loadScripts();

      LibManager manager = LibManager.getManager();
      ChangeListener listener = new ChangeListener(this);
      manager.addActionListener(listener);
      Cleaner.add(new LibScriptableReference(this, manager, listener));
   }

   private synchronized void reloadScripts() {
      Set<String> ofuncs = new HashSet<>(funcs.keySet());
      funcs = new HashMap<>();

      loadScripts();

      // remove the functions that no longer exist
      ofuncs.removeAll(funcs.keySet());
      deleteScripts(ofuncs);
   }

   /**
    * Delete all functions.
    */
   private void deleteScripts(Set<String> funcs) {
      for(String func : funcs) {
         delete(func);
      }

      funcs.clear();
   }

   /**
    * Add all functions.
    */
   private void loadScripts() {
      LibManager mgr = LibManager.getManager();
      Enumeration names = mgr.getScripts();

      while(names.hasMoreElements()) {
         String fname = (String) names.nextElement();
         String source = mgr.getScript(fname);
         funcs.put(fname, source);
      }
   }

   @Override
   public Object get(String name, Scriptable scope) {
      if(funcs.containsKey(name)) {
         Object func = funcs.get(name);

         if(func instanceof String) {
            try {
               func = compileFunction(name, (String) func);
               funcs.put(name, func);
            }
            catch(Exception ex) {
               LOG.warn(ex.getMessage(), ex);
            }
         }

         func = funcs.get(name);

         // simulate dynamic scope
         if(func instanceof Function) {
            ((Function) func).setParentScope(scope);
         }

         return func;
      }

      return super.get(name, scope);
   }

   /**
    * Add one function to the scope.
    * @param fname the specified function name.
    * @param source the specified source code.
    */
   private Object compileFunction(String fname, String source) {
      if(source == null || source.length() == 0) {
         return null;
      }

      Context cx = TimeoutContext.enter();

      try {
         Scriptable funcScope = this.funcScope != null ? this.funcScope : this;
         return cx.compileFunction(funcScope, source, "<" + fname + ">", 1, null);
      }
      finally {
         Context.exit();
      }
   }

   @Override
   public String getClassName() {
      return "Lib";
   }

   private Scriptable funcScope;
   private Map<String, Object> funcs = new ConcurrentHashMap<>();

   private static final Logger LOG = LoggerFactory.getLogger(LibScriptable.class);

   private static final class ChangeListener implements ActionListener {
      ChangeListener(LibScriptable lib) {
         this.lib = new WeakReference<>(lib);
      }

      @Override
      public void actionPerformed(ActionEvent e) {
         LibScriptable sobj = lib.get();

         if(sobj != null) {
            sobj.reloadScripts();
         }
      }

      private final WeakReference<LibScriptable> lib;
   }

   private static final class LibScriptableReference extends Cleaner.Reference<LibScriptable> {
      LibScriptableReference(LibScriptable referent, LibManager manager, ChangeListener listener) {
         super(referent);
         this.manager = new WeakReference<>(manager);
         this.listener = listener;
      }

      @Override
      public void close() {
         LibManager lm = manager.get();

         if(lm != null) {
            lm.removeActionListener(listener);
         }
      }

      private final WeakReference<LibManager> manager;
      private final ChangeListener listener;
   }
}
