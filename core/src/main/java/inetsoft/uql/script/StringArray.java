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

import inetsoft.util.script.graal.ScriptArrayScope;
import inetsoft.util.script.graal.ScriptScope;

import java.util.ArrayList;
import java.util.List;

/**
 * String array scriptable, supports to query/modify/delete a string array.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class StringArray implements ScriptArrayScope {
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
   public String getClassName() {
      return name;
   }

   /**
    * Get a named property from the object.
    */
   @Override
   public Object getMember(String name) {
      if(name.equals("length")) {
         return Integer.valueOf(list.size());
      }

      return null;
   }

   /**
    * Get a property from the object selected by an integral index.
    */
   @Override
   public Object getArrayElement(long index) {
      if(index >= 0 && index < list.size()) {
         return list.get((int) index);
      }

      return null;
   }

   /**
    * Get the number of indexed elements.
    */
   @Override
   public long getArraySize() {
      return list.size();
   }

   /**
    * Set an indexed property in this object. Ported from the Rhino
    * {@code put(int, Scriptable, Object)}: only set when the index is in
    * range and the value is null or a String. (#75423)
    */
   @Override
   public void setArrayElement(long index, Object value) {
      if(index >= 0 && index < list.size() &&
         (value == null || value instanceof String))
      {
         list.set((int) index, value);
      }
   }

   /**
    * Indicate whether or not a named property is defined in an object.
    */
   @Override
   public boolean hasMember(String name) {
      if(name.equals("length")) {
         return true;
      }

      return false;
   }

   /**
    * Set a named property in this object.
    */
   @Override
   public void putMember(String name, Object value) {
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
    * Remove a property from this object.
    */
   @Override
   public boolean removeMember(String name) {
      // do nothing
      return false;
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
      Object[] ids = new Object[list.size() + 1];

      for(int i = 0; i < list.size(); i++) {
         ids[i] = Integer.valueOf(i);
      }

      ids[list.size()] = "length";

      return ids;
   }

   /**
    * Unwrap the object by returning the wrapped value.
    * @return the wrapped value.
    */
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
   private ScriptScope parent;
}
