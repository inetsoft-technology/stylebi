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

import java.io.Serializable;

/**
 * For some attributes which cannot use DynamicValue to hold the dynamic value
 * in some assembly info.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public class ClazzHolder<T> implements Cloneable, Serializable {
   /**
    * Get the dynamic value.
    * @return the dynamic value object.
    */
   public T getDValue() {
      return dvalue;
   }

   /**
    * Get the runtime value object.
    * @return the runtime value object.
    */
   public T getRValue() {
      return rvalue != null ? rvalue : dvalue;
   }

   /**
    * Set the dynamic value.
    * @param value the dynamic value object.
    */
   public void setDValue(T value) {
      dvalue = value;
   }

   /**
    * Set the runtime value object.
    * @param value the runtime value object.
    */
   public void setRValue(T value) {
      rvalue = value;
   }

   /**
    * Indicates whether some other object is "equal to" this one.
    * @return true if this object is the same as the obj argument,
    *          false otherwise.
    */
   @Override
   public boolean equals(Object obj) {
      if(!(obj instanceof ClazzHolder)) {
         return false;
      }

      ClazzHolder<?> holder = (ClazzHolder<?>) obj;

      return Tool.equals(dvalue, holder.dvalue);
   }

   /**
    * Calculate the hashcode of the dynamic value.
    */
   @Override
   public int hashCode() {
      int hash = 0;

      if(dvalue != null) {
         hash += dvalue.hashCode();
      }

      return hash;
   }

   /**
    * Returns a string representation of the object.
    * @return a string representation of the object.
    */
   @Override
   public String toString() {
      return "" + dvalue + ", " + rvalue;
   }

   /**
    * Creates and returns a copy of this object.
    * @return a clone of this instance.
    */
   @Override
   @SuppressWarnings("unchecked")
   public ClazzHolder<T> clone() {
     try {
         ClazzHolder<T> holder = (ClazzHolder<T>) super.clone();
         holder.dvalue = (T) Tool.clone(dvalue);
         holder.rvalue = (T) Tool.clone(rvalue);
         return holder;
      }
      catch(Exception ex) {
         //ignore exception
      }

      return null;
   }

   private T dvalue;
   private T rvalue;
}
