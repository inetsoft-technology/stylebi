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
package inetsoft.report.internal.table;

import inetsoft.util.Tool;

import java.io.Serializable;

/**
 * Value list for cell expansion.
 */
public abstract class ValueList implements Serializable {
   /**
    * Get the number of items in the list.
    */
   public abstract int size();

   /**
    * Set the length of the list.
    */
   public abstract void setSize(int size);

   /**
    * Get the specified item from the list.
    */
   public abstract Object get(int i);

   /**
    * Get all values as an array.
    */
   public abstract Object[] getValues();

   /**
    * Get a scope for the 'field' variable for the specified value.
    */
   public Object getScope(int idx) {
      return null;
   }

   /**
    * Check if the lists contain the same values.
    */
   public boolean equals(Object obj) {
      try {
         ValueList list = (ValueList) obj;

         if(size() != list.size()) {
            return false;
         }

         for(int i = 0; i < size(); i++) {
            if(!Tool.equals(get(i), list.get(i))) {
               return false;
            }
         }

         return true;
      }
      catch(Exception ex) {
         return false;
      }
   }

   /**
    * Show values as a comma delimited string.
    */
   public String toString() {
      StringBuilder buf = new StringBuilder();

      for(int i = 0; i < size(); i++) {
         if(i > 0) {
            buf.append(",");
         }

         buf.append(get(i));
      }

      return buf.toString();
   }
}
