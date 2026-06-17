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


import inetsoft.util.script.FunctionObject2;
import inetsoft.util.script.graal.ScriptScope;
import org.mozilla.javascript.UniqueTag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;

/**
 * A base class scriptable that implements methods for adding properties using
 * reflection.
 */
public abstract class PropertyScriptable implements ScriptScope, Cloneable {
   /**
    * Get the object for getting and setting properties.
    */
   protected abstract Object getObject();

   /**
    * Add a property to this scriptable.
    * @param name property name.
    * @param getter getter method for retrieving the property value.
    * @param setter setter method for changing  the property value.
    * @para type property type.
    * @param cls the class of the element object.
    */
   protected void addProperty(String name, String getter, String setter,
			      Class type, Class cls) {
      try {
         propmap.put(name, new PropertyDescriptor(cls, getter, setter, type));
      }
      catch(Throwable e) {
         LOG.error("Failed to add property: " + name, e);
      }
   }

   /**
    * Add a property to this scriptable.
    * @param name property name.
    * @param getter getter method for retrieving the property value.
    * @param setter setter method for changing  the property value.
    * @para type property type.
    * @param cls the class of the element object.
    * @param obj the target object to invoke setter/getter.
    */
   protected void addProperty(String name, String getter, String setter,
                              Class type, Class cls, Object obj) {
      try {
         propmap.put(name, new PropertyDescriptor(cls, getter, setter, type, obj));
      }
      catch(Throwable e) {
         LOG.error("Failed to add property " + name + " to object " + obj, e);
      }
   }

   public void addFunctionProperty(Class cls, String name, Class ...params) {
      // NOTE (Feature #75423): FunctionObject2 is a Rhino FunctionObject and is
      // replaced by the native-binding mechanism in Milestone 4. PropertyScriptable
      // no longer implements Rhino's Scriptable, so 'this' can no longer be passed
      // as the Rhino scope; pass null until the M4 native-binding cutover rewires it.
      addProperty(name, new FunctionObject2(null, cls, name, params));
   }

   /**
    * Add a property to this scriptable.
    * @name property name.
    * @param obj value as a String, Boolean, Number, or Scriptable.
    */
   protected void addProperty(String name, Object obj) {
      validate();
      propmap.put(name, obj);
   }

   /**
    * Check if the named property exists.
    */
   @Override
   public boolean hasMember(String id) {
      return propmap.containsKey(id);
   }

   /**
    * Get a property value.
    */
   @Override
   public Object getMember(String id) {
      Object val = null;

      if(propmap.containsKey(id)) {
         try {
            val = propmap.get(id);

            if(UniqueTag.NULL_VALUE.equals(val)) {
               return null;
            }

            if(val instanceof PropertyDescriptor) {
               PropertyDescriptor desc = (PropertyDescriptor) val;

               return desc.get(getObject());
            }

            return val;
         }
         catch(Exception e) {
            LOG.error("Get property failed: " + id, e);
         }
      }

      return null;
   }

   /**
    * Set the property value.
    */
   @Override
   public void putMember(String id, Object value) {
      try {
         Object val = propmap.get(id);

         if(val instanceof PropertyDescriptor) {
            PropertyDescriptor desc = (PropertyDescriptor) val;

            desc.set(getObject(), value);
         }
         else {
            validate();

            if(value != null) {
               propmap.put(id, value);
            }
            else {
               propmap.put(id, UniqueTag.NULL_VALUE);
            }
         }
      }
      catch(Exception e) {
         LOG.error("Set property failed: " + id, e);
      }
   }

   /**
    * Get the type of a named property from the object.
    */
   public Class getType(String name) {
      Object val = propmap.get(name);

      if(val instanceof PropertyDescriptor) {
         PropertyDescriptor desc = (PropertyDescriptor) val;

         return desc.getType();
      }

      return null;
   }

   /**
    * Get the names of all properties in this scope.
    */
   @Override
   public Object[] getMemberKeys() {
      return propmap.keySet().toArray(new Object[0]);
   }

   /**
    * Removes a property from this object.
    */
   @Override
   public boolean removeMember(String name) {
      return propmap.remove(name) != null;
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
    * If this scriptable shares an object map, make a copy of it so the
    * property can be modified.
    */
   protected void validate() {
      if(dirty) {
         propmap = new ConcurrentHashMap(propmap);
         dirty = false;
      }
   }

   /**
    * Make a copy of the scriptable.
    */
   @Override
   public Object clone() {
      try {
         PropertyScriptable obj = (PropertyScriptable) super.clone();
         obj.dirty = true;
         return obj;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone object", ex);
      }

      return null;
   }

   private ScriptScope parent;
   protected ConcurrentHashMap propmap = new ConcurrentHashMap();
   private boolean dirty = false; // true if a copy sharing propmap

   private static final Logger LOG =
      LoggerFactory.getLogger(PropertyScriptable.class);
}
