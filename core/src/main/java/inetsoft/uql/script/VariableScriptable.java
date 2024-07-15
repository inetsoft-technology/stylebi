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
import inetsoft.util.script.ScriptUtil;
import org.mozilla.javascript.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * This class maps a VariableTable to a JavaScript object.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class VariableScriptable implements Scriptable, Wrapper {
   /**
    * Constructor.
    */
   public VariableScriptable(VariableTable vars) {
      this.vars = vars;
   }

   /**
    * Get the name of the set of objects implemented by this Java class.
    */
   @Override
   public String getClassName() {
      return "Variable";
   }

   /**
    * Get a named property from the object.
    */
   @Override
   public Object get(String name, Scriptable start) {
      try {
         Object val = vars.get(name);

         if(val != null) {
            // @by stephenwebster, For bug1431461209990
            // Prevent Rhino warnings.
            // RHINO USAGE WARNING: Missed Context.javaToJS() conversion:
            // I reviewed the Rhino source and this will basically wrap any
            // non String, Number, Boolean into a Scriptable (NativeJavaObject).
            // From what I can see all the methods and properties of the java
            // objects will be available in script, so it should be safe to do.
            // @by stephenwebster, For bug1434039282803
            // safe guard against null parent scope since Context.javaToJS will
            // throw a NullPointer
            try {
               // NativeJavaArray's concat() doesn't work in 1.7.10.
               // this can be removed if it's fixed in later versions.
               if(val instanceof Object[]) {
                  return ScriptUtil.getNativeArray((Object[]) val, getParentScope());
               }

               return Context.javaToJS(val, getParentScope());
            }
            catch(Throwable ex) {
               return val;
            }
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
         LOG.error("Failed to get property " + name + " from " + start, e);
      }

      // do not return undefined for undefined is an object. In java script,
      // undefined == null, but in java, undefined != null, so here we return
      // null for both java logic and java script logic to work properly
      return null;
   }

   /**
    * Get a property from the object selected by an integral index.
    */
   @Override
   public Object get(int index, Scriptable start) {
      return Undefined.instance;
   }

   /**
    * Indicate whether or not a named property is defined in an object.
    */
   @Override
   public boolean has(String name, Scriptable start) {
      try {
         return vars.get(name) != null ||
            name.equals("length") || name.equals("parameterNames");
      }
      catch(Exception ignore) {
      }

      return false;
   }

   /**
    * Indicate whether or not an indexed  property is defined in an object.
    */
   @Override
   public boolean has(int index, Scriptable start) {
      return false;
   }

   /**
    * Set a named property in this object.
    */
   @Override
   public void put(String name, Scriptable start, Object value) {
      vars.put(name, JavaScriptEngine.unwrap(value));
      vars.setAsIs(name, true);
   }

   /**
    * Set an indexed property in this object.
    */
   @Override
   public void put(int index, Scriptable start, Object value) {
      // do nothing
   }

   /**
    * Remove a property from this object.
    */
   @Override
   public void delete(String name) {
      vars.remove(name);
   }

   /**
    * Remove a property from this object.
    */
   @Override
   public void delete(int index) {
      // do nothing
   }

   /**
    * Get the prototype of the object.
    */
   @Override
   public Scriptable getPrototype() {
      return prototype;
   }

   /**
    * Set the prototype of the object.
    */
   @Override
   public void setPrototype(Scriptable prototype) {
      this.prototype = prototype;
   }

   /**
    * Get the parent scope of the object.
    */
   @Override
   public Scriptable getParentScope() {
      return parent;
   }

   /**
    * Set the parent scope of the object.
    */
   @Override
   public void setParentScope(Scriptable parent) {
      this.parent = parent;
   }

   /**
    * Get an array of property ids.
    */
   @Override
   public Object[] getIds() {
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
    * Get the default value of the object with a given hint.
    */
   @Override
   public Object getDefaultValue(Class hint) {
      return vars.toString();
   }

   /**
    * Implement the instanceof operator.
    */
   @Override
   public boolean hasInstance(Scriptable instance) {
      return false;
   }

   /**
    * Expose wrapped variable table.
    */
   @Override
   public Object unwrap() {
      return vars;
   }

   /**
    * Convert to string form.
    */
   public String toString() {
      return vars.toString();
   }

   private Scriptable parent;
   private Scriptable prototype;
   private VariableTable vars;

   private static final Logger LOG = LoggerFactory.getLogger(VariableScriptable.class);
}
