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
import inetsoft.util.script.JavaScriptEngine;
import inetsoft.util.script.graal.ScriptScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * This class maps a VariableTable to a JavaScript object.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class VariableScriptable implements ScriptScope {
   /**
    * Constructor.
    */
   public VariableScriptable(VariableTable vars) {
      this.vars = vars;
   }

   /**
    * Get the name of the set of objects implemented by this Java class.
    */
   public String getClassName() {
      return "Variable";
   }

   /**
    * Get a named property from the object.
    */
   @Override
   public Object getMember(String name) {
      try {
         Object val = vars.get(name);

         if(val != null) {
            // the ScopeProxy/HostAccess layer now handles wrapping
            return val;
         }

         if(name.equals("length")) {
            return vars.size();
         }

         if(name.equals("parameterNames")) {
            List<String> res = new ArrayList<>();
            Enumeration names = vars.keys();

            while(names.hasMoreElements()) {
               res.add((String) names.nextElement());
            }

            return res.toArray(new String[0]);
         }
      }
      catch(Exception e) {
         LOG.error("Failed to get property " + name, e);
      }

      // do not return undefined for undefined is an object. In java script,
      // undefined == null, but in java, undefined != null, so here we return
      // null for both java logic and java script logic to work properly
      return null;
   }

   /**
    * Indicate whether or not a named property is defined in an object.
    */
   @Override
   public boolean hasMember(String name) {
      try {
         return vars.get(name) != null ||
            name.equals("length") || name.equals("parameterNames");
      }
      catch(Exception ignore) {
      }

      return false;
   }

   /**
    * Set a named property in this object.
    */
   @Override
   public void putMember(String name, Object value) {
      vars.put(name, JavaScriptEngine.unwrap(value));
      vars.setAsIs(name, true);
   }

   /**
    * Remove a property from this object.
    */
   @Override
   public boolean removeMember(String name) {
      boolean had = false;

      try {
         had = vars.get(name) != null;
      }
      catch(Exception ignore) {
      }

      vars.remove(name);
      return had;
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
    * Get an array of property ids.
    */
   @Override
   public Object[] getMemberKeys() {
      Set<Object> vec = new LinkedHashSet<>();
      Enumeration keys = vars.keys();

      while(keys.hasMoreElements()) {
         vec.add(keys.nextElement());
      }

      vec.add("length");
      vec.add("parameterNames");

      //fix bug1294356587071, because '_USER_' and '_ROLES_' are build-in
      //variables, so put them in the vec
      if(!vec.contains("_USER_")) {
         vec.add("_USER_");
      }

      if(!vec.contains("_ROLES_")) {
         vec.add("_ROLES_");
      }

      if(!vec.contains("_GROUPS_")) {
         vec.add("_GROUPS_");
      }

      if(!vec.contains("__principal__")) {
         vec.add("__principal__");
      }

      return vec.toArray(new Object[0]);
   }

   /**
    * Expose wrapped variable table.
    */
   public Object unwrap() {
      return vars;
   }

   /**
    * Convert to string form. Scripts sometimes concatenate a scope object
    * (e.g. {@code thisParameter} on an embedded viewsheet, or the top-level
    * {@code parameter} global) directly instead of dereferencing a specific
    * variable name; list the variable names/values rather than falling back
    * to Java's default {@code Object.toString()}.
    */
   public String toString() {
      StringBuilder sb = new StringBuilder();
      Enumeration names = vars.keys();

      while(names.hasMoreElements()) {
         String name = (String) names.nextElement();

         try {
            Object val = vars.get(name);

            if(sb.length() > 0) {
               sb.append(", ");
            }

            sb.append(name).append('=').append(val);
         }
         catch(Exception ignore) {
         }
      }

      return sb.toString();
   }

   private ScriptScope parent;
   private VariableTable vars;

   private static final Logger LOG = LoggerFactory.getLogger(VariableScriptable.class);
}
