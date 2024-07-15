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

import org.mozilla.javascript.*;

import java.util.ArrayList;
import java.util.List;

/**
 * String array scriptable, supports to query/modify/delete a string array.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class StringArray implements Scriptable, Wrapper {
   /**
    * Constructor.
    */
   public StringArray(String name, String[] arr) {
      this.name = name;
      this.list = new ArrayList();

      for(int i = 0; i < arr.length; i++) {
         list.add(arr[i]);
      }
   }

   /**
    * Get the name of the set of objects implemented by this Java class.
    */
   @Override
   public String getClassName() {
      return name;
   }

   /**
    * Get a named property from the object.
    */
   @Override
   public Object get(String name, Scriptable start) {
      if(name.equals("length")) {
         return Integer.valueOf(list.size());
      }

      return Undefined.instance;
   }

   /**
    * Get a property from the object selected by an integral index.
    */
   @Override
   public Object get(int index, Scriptable start) {
      if(index >= 0 && index < list.size()) {
         return list.get(index);
      }

      return Undefined.instance;
   }

   /**
    * Indicate whether or not a named property is defined in an object.
    */
   @Override
   public boolean has(String name, Scriptable start) {
      if(name.equals("length")) {
         return true;
      }

      return false;
   }

   /**
    * Indicate whether or not an indexed property is defined in an object.
    */
   @Override
   public boolean has(int index, Scriptable start) {
      if(index >= 0 && index < list.size()) {
         return true;
      }

      return false;
   }

   /**
    * Set a named property in this object.
    */
   @Override
   public void put(String name, Scriptable start, Object value) {
      if(name.equals("length") && value instanceof Number) {
         int length = ((Number) value).intValue();

         if(length < 0) {
            return;
         }

         int size = list.size();

         for(int i = length; i < size; i++) {
            list.remove(i);
         }

         for(int i = size; i < length; i++) {
            list.add(null);
         }
      }
   }

   /**
    * Set an indexed property in this object.
    */
   @Override
   public void put(int index, Scriptable start, Object value) {
      if(index >= 0 && index < list.size() &&
         (value == null || value instanceof String))
      {
         list.set(index, value);
      }
   }

   /**
    * Remove a property from this object.
    */
   @Override
   public void delete(String name) {
      // do nothing
   }

   /**
    * Remove a property from this object.
    */
   @Override
   public void delete(int index) {
      if(index >= 0 && index < list.size()) {
         list.remove(index);
      }
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
      Object[] ids = new Object[list.size() + 1];

      for(int i = 0; i < list.size(); i++) {
         ids[i] = Integer.valueOf(i);
      }

      ids[list.size()] = "length";

      return ids;
   }

   /**
    * Get the default value of the object with a given hint.
    */
   @Override
   public Object getDefaultValue(Class hint) {
      return list;
   }

   /**
    * Implement the instanceof operator.
    */
   @Override
   public boolean hasInstance(Scriptable instance) {
      return false;
   }

   /**
    * Unwrap the object by returning the wrapped value.
    * @return the wrapped value.
    */
   @Override
   public Object unwrap() {
      String[] arr = new String[list.size()];
      list.toArray(arr);
      return arr;
   }

   /**
    * Convert to string form.
    */
   public String toString() {
      return list.toString();
   }

   private String name;
   private List list;
   private Scriptable parent;
   private Scriptable prototype;
}
