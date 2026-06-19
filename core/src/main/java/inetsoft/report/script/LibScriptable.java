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
package inetsoft.report.script;

import inetsoft.report.LibManager;
import inetsoft.report.LibManagerProvider;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.util.Cleaner;
import inetsoft.util.Tool;
import inetsoft.util.script.graal.ScriptScope;
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
public class LibScriptable implements ScriptScope {
   /**
    * Get a per scope (report) scriptable.
    */
   public LibScriptable(ScriptScope funcScope) {
      this.funcScope = funcScope;
      loadScripts();

      LibManager manager = LibManagerProvider.getInstance().getManager();
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
         removeMember(func);
      }

      funcs.clear();
   }

   /**
    * Add all functions.
    */
   private void loadScripts() {
      LibManager mgr = LibManagerProvider.getInstance().getManager();
      Enumeration names = mgr.getScripts();

      while(names.hasMoreElements()) {
         String fname = (String) names.nextElement();
         String source = mgr.getScript(fname);
         funcs.put(fname, source);
      }
   }

   @Override
   public Object getMember(String name) {
      // Library script functions are now callable: GraalJavaScriptEngine.initScope
      // installs each library function's source as a global function definition
      // at engine init, so any script/formula can call it by name. This scriptable
      // is retained only for member enumeration (autocomplete via getMemberKeys);
      // getMember returns the function source string.
      return funcs.get(name);
   }

   @Override
   public boolean hasMember(String name) {
      return funcs.containsKey(name);
   }

   @Override
   public void putMember(String name, Object value) {
      funcs.put(name, value);
   }

   @Override
   public boolean removeMember(String name) {
      return funcs.remove(name) != null;
   }

   @Override
   public Object[] getMemberKeys() {
      return funcs.keySet().toArray(new Object[0]);
   }

   public String getClassName() {
      return "Lib";
   }

   /**
    * Get all library function sources keyed by name.
    */
   public Map<String, Object> getFunctions() {
      return funcs;
   }

   private ScriptScope funcScope;
   private Map<String, Object> funcs = new ConcurrentHashMap<>();

   private static final Logger LOG = LoggerFactory.getLogger(LibScriptable.class);

   private static final class ChangeListener implements ActionListener {
      ChangeListener(LibScriptable lib) {
         this.lib = new WeakReference<>(lib);
      }

      @Override
      public void actionPerformed(ActionEvent e) {
         if(e instanceof inetsoft.report.ActionEvent event) {
            String orgID = event.getOrgID();

            if(Tool.equals(orgID, OrganizationManager.getInstance().getCurrentOrgID())) {
               LibScriptable sobj = lib.get();

               if(sobj != null) {
                  sobj.reloadScripts();
               }
            }
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
