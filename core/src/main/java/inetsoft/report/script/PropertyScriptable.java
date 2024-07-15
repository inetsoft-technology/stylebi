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
import org.mozilla.javascript.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;

/**
 * A base class scriptable that implements methods for adding properties using
 * reflection.
 */
public abstract class PropertyScriptable implements Scriptable, Cloneable {
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

   protected void addFunctionProperty(Class cls, String name, Class ...params) {
      addProperty(name, new FunctionObject2(this, cls, name, params));
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
   public boolean has(String id, Scriptable start) {
      return propmap.containsKey(id);
   }

   /**
    * Index property is not supported.
    */
   @Override
   public boolean has(int index, Scriptable start) {
      return false;
   }

   /**
    * Get a property value.
    */
   @Override
   public Object get(String id, Scriptable start) {
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

      return NOT_FOUND;
   }

   /**
    * Index property is not found.
    */
   @Override
   public Object get(int index, Scriptable start) {
      return Undefined.instance;
   }

   /**
    * Set the property value.
    */
   @Override
   public void put(String id, Scriptable start, Object value) {
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
    * Index property is not supported.
    */
   @Override
   public void put(int index, Scriptable start, Object value) {
   }

   /**
    * Get the type of a named property from the object.
    */
   public Class getType(String name, Scriptable start) {
      Object val = propmap.get(name);

      if(val instanceof PropertyDescriptor) {
         PropertyDescriptor desc = (PropertyDescriptor) val;

         return desc.getType();
      }

      return null;
   }

   @Override
   public Object getDefaultValue(Class hint) {
      if(hint == ScriptRuntime.BooleanClass) {
         return Boolean.TRUE;
      }
      else if(hint == ScriptRuntime.NumberClass) {
         return ScriptRuntime.NaNobj;
      }

      return this;
   }

   /**
    * Get the names of all properties in this scope.
    */
   @Override
   public Object[] getIds() {
      return propmap.keySet().toArray(new Object[0]);
   }

   @Override
   public boolean hasInstance(Scriptable value) {
      return false;
   }

   /**
    * Removes a property from this object.
    */
   @Override
   public void delete(String name) {
      propmap.remove(name);
   }

   /**
    * Removes a property from this object.
    */
   @Override
   public void delete(int index) {
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
    * Set the prototype of the object.
    */
   @Override
   public void setPrototype(Scriptable prototype) {
      this.prototype = prototype;
   }

   /**
    * Get the prototype of the object.
    */
   @Override
   public Scriptable getPrototype() {
      return prototype;
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

   private Scriptable prototype, parent;
   protected ConcurrentHashMap propmap = new ConcurrentHashMap();
   private boolean dirty = false; // true if a copy sharing propmap

   private static final Logger LOG =
      LoggerFactory.getLogger(PropertyScriptable.class);
}
