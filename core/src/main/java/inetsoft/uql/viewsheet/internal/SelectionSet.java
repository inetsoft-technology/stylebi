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
package inetsoft.uql.viewsheet.internal;

import inetsoft.util.Tool;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

import java.util.Collection;
import java.util.Objects;

/**
 * SelectionSet, compares objects using value instead of equals when required.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class SelectionSet extends ObjectOpenHashSet<Object> {
   /**
    * Normalize an object.
    * @param obj the specified object.
    * return the normalized result.
    */
   public static Object normalize(Object obj) {
      obj = Tool.normalize(obj);

      // if a tuple contains a single value, treat it as the same as the object
      if(obj instanceof Tuple) {
         Tuple tuple = (Tuple) obj;

         if(tuple.size() == 1) {
            obj = tuple.get(0);
         }
      }

      return obj;
   }

   /**
    * Constructor.
    */
   public SelectionSet() {
      super();
   }

   /**
    * Constructor.
    */
   public SelectionSet(Collection<Object> c) {
      super();
      this.addAll(c);
   }

   /**
    * Check if contains another object.
    * @param obj the specified object.
    * @return <tt>true</tt> if contains the object, <tt>false</tt> otherwise.
    */
   @Override
   public boolean contains(Object obj) {
      obj = normalize(obj);
      Class cls = obj == null ? null : obj.getClass();

      if(dtype != null && cls != null && !cls.equals(dtype) &&
         !dtype.equals(Tuple.class) && !cls.equals(Tuple.class))
      {
         obj = Tool.getData(dtype, obj);
      }

      return super.contains(obj);
   }

   /**
    * Add an object.
    * @param obj the specified object to add.
    * @return <tt>true</tt> if the object is not contained, <tt>false</tt>
    * otherwise.
    */
   @Override
   public boolean add(Object obj) {
      obj = normalize(obj);

      if(dtype == null && obj != null) {
         dtype = obj.getClass();
      }

      return super.add(obj);
   }

   /**
    * Remove an object.
    * @param obj the specified object to remove.
    * @return <tt>true</tt> if removed, <tt>false</tt> otherwise.
    */
   @Override
   public boolean remove(Object obj) {
      return super.remove(normalize(obj));
   }

   /**
    * This class implements equals and hashCode so an array can be used in a
    * hash set.
    */
   public static class Tuple {
      /**
       * Create a tuple for the items in array from 0 to (length - 1).
       */
      public Tuple(Object[] arr, int length) {
         this.arr = arr;
         this.length = Math.min(arr.length, length);
      }

      /**
       * Check if two tuples contains the same items.
       */
      public boolean equals(Object obj) {
         if(!(obj instanceof Tuple)) {
            return false;
         }

         Tuple t2 = (Tuple) obj;
         Object[] arr2 = t2.arr;

         if(length != t2.length) {
            return false;
         }

         for(int i = length - 1; i >= 0; i--) {
            if(!Objects.equals(arr[i], arr2[i])) {
               return false;
            }
         }

         return true;
      }

      /**
       * Get a hashcode from the tuple items.
       */
      public int hashCode() {
         if(hash == 0) {
            for(int i = 0; i < length; i++) {
               if(arr[i] != null) {
                  hash = hash * 31 + arr[i].hashCode();
               }
            }
         }

         return hash;
      }

      /**
       * Get the item at the position.
       */
      public Object get(int idx) {
         return arr[idx];
      }

      /**
       * Get the number of items.
       */
      public int size() {
         return length;
      }

      public String toString() {
         return "Tuple[" + length + ": " + Tool.arrayToString(arr) + "]";
      }

      private Object[] arr;
      private int length;
      private int hash = 0;
   }

   private Class dtype = null;
}
