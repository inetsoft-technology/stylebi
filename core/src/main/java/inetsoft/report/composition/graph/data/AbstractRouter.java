/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.report.composition.graph.data;

import inetsoft.report.composition.graph.Router;

import java.util.*;

/**
 * A map for data compare, it is used for data calculation,
 * like Change, RunningTotal or Moving.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public abstract class AbstractRouter implements Router {
   /**
    * Default constructor.
    */
   public AbstractRouter() {
      super();
   }

   /**
    * Constructor.
    */
   public AbstractRouter(Comparator comp) {
      super();
      this.comp = comp;
   }

   /**
    * Get first value on the scale.
    */
   @Override
   public Object getFirst() {
      return getValues() == null || getValues().length <= 0 ? null :
                                                              getValues()[0];
   }

   /**
    * Get last value on the scale.
    */
   @Override
   public Object getLast() {
      return getValues() == null || getValues().length <= 0 ?
         null : getValues()[getValues().length - 1];
   }

   /**
    * Get index of a value in the mapper.
    */
   @Override
   public int getIndex(Object val) {
      return search(val);
   }

   /**
    * Get a value on scale that diff with current value.
    * @param val the given value.
    * @param diff before or after position of given value.
    */
   @Override
   public Object getValue(Object val, int diff) {
      int pos = search(val);

      if(pos < 0) {
         return INVALID;
      }

      if(pos + diff < 0 || pos + diff >= getValues().length) {
         return NOT_EXIST;
      }

      return getValues()[pos + diff];
   }

   /**
    * Get all previous values.
    */
   @Override
   public Object[] getAllPrevious(Object val) {
      int pos = search(val);

      if(pos <= 0) {
         return new Object[0];
      }

      Object[] vals = new Object[pos];
      System.arraycopy(getValues(), 0, vals, 0, pos);
      return vals;
   }

   /**
    * Search a value.
    */
   private final int search(Object val) {
      if(getValues() == null || getValues().length <= 0) {
         return -1;
      }

      if(lastIndex != -1) {
         // try last position
         if(equals(val, getValues()[lastIndex])) {
            return lastIndex;
         }

         // try next one
         if(lastIndex < getValues().length - 1) {
            if(equals(val, getValues()[lastIndex + 1])) {
               lastIndex = lastIndex + 1;
               return lastIndex;
            }
         }

         // try previous one
         if(lastIndex > 0) {
            if(equals(val, getValues()[lastIndex - 1])) {
               lastIndex = lastIndex - 1;
               return lastIndex;
            }
         }
      }

      // no comparator?
      if(comp == null) {
         for(int i = 0; i < getValues().length; i++) {
            if(equals(val, getValues()[i])) {
               lastIndex = i;
               return lastIndex;
            }
         }

         return -1;
      }

      int pos = Arrays.binarySearch(getValues(), val, comp);
      lastIndex = pos >= 0 ? pos : lastIndex;
      return pos;
   }

   private static final boolean equals(Object val1, Object val2) {
      if(val1 == null || val2 == null) {
         return val1 == val2;
      }

      if(val1.getClass() != val2.getClass() && val1 instanceof Date && val2 instanceof Date) {
         return ((Date) val1).getTime() == ((Date) val2).getTime();
      }

      return val1.equals(val2);
   }

   protected Comparator comp;
   private transient int lastIndex = -1;
}
