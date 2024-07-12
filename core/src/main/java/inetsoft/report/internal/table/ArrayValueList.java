/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.report.internal.table;

/**
 * Value list for cell expansion.
 */
public class ArrayValueList extends ValueList {
   /**
    * Create an array based list.
    */
   public ArrayValueList(Object[] arr) {
      this.arr = arr;
   }
   
   /**
    * Get the number of items in the list.
    */
   @Override
   public int size() {
      return arr.length;
   }

   /**
    * Get the specified item from the list.
    */
   @Override
   public Object get(int i) {
      return arr[i];
   }

   /**
    * Get all values as an array.
    */
   @Override
   public Object[] getValues() {
      return arr;
   }
   
   /**
    * Set the length of the list.
    */
   @Override
   public void setSize(int size) {
      Object[] narr = new Object[size];

      System.arraycopy(arr, 0, narr, 0, Math.min(arr.length, narr.length));
      arr = narr;
   }
   
   private Object[] arr;
}
