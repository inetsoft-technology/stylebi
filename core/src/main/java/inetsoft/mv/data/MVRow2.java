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
package inetsoft.mv.data;


import inetsoft.util.Tool;

import java.sql.Time;
import java.sql.Timestamp;
import java.util.Comparator;

/**
 * Row2, extends Row, use groups2 instead groups. It is used to store
 * row data in Object form instead of dimension (index) and measure (double).
 *
 * @author InetSoft Technology
 * @since  11.4
 */
public class MVRow2 extends MVRow {
    /**
    * Create an instance of row.
    */
   public MVRow2() {
      super();
   }

   /**
    * Create an instance of row.
    */
   public MVRow2(Object[] groups, double[] aggregates) {
      super(null, aggregates);
      this.groups2 = groups;
   }

   /**
    * Create an instance of row.
    */
   public MVRow2(Object[] groups, Object[] aggregates) {
      this.groups2 = groups;
      this.aggregates2 = aggregates;
   }

   /**
    * Check if equals another object.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof MVRow2)) {
         return false;
      }

      MVRow2 row = (MVRow2) obj;

      for(int i = groups2.length - 1; i >= 0; i--) {
         if(!Tool.equals(groups2[i], row.groups2[i])) {
            return false;
         }
      }

      return true;
   }

   /**
    * Get the hash code value.
    */
   public int hashCode() {
      if(hash == 0) {
         hash = 1;

         for(Object elem : groups2) {
            if(elem != null) {
               hash ^= elem.hashCode() + 0x9e3779b9 + (hash << 6) + (hash >> 2);
            }
         }
      }

      return hash;
   }

   /**
    * Add measure values.
    */
   public void add(Object[] objs) {
      if(infos != null) {
         for(FormulaInfo info : infos) {
            info.addValue(objs);
         }
      }
   }

   /**
    * Clone this row.
    */
   @Override
   public Object clone() {
      try {
         return super.clone();
      }
      catch(Exception ex) {
         // ignore it
      }

      return null;
   }

   /**
    * Get the string representation.
    */
   public String toString() {
      StringBuilder sb = new StringBuilder("Row2[");

      for(int i = 0; i < groups2.length; i++) {
         if(i > 0) {
            sb.append(',');
         }

         sb.append(groups2[i]);
      }

      if(aggregates2 != null) {
         sb.append(": ");

         for(int i = 0; i < aggregates2.length; i++) {
            if(i > 0) {
               sb.append(',');
            }

            sb.append(aggregates2[i]);
         }
      }

      if(infos != null) {
         sb.append(": ");

         for(int i = 0; i < infos.length; i++) {
            if(i > 0) {
               sb.append(',');
            }

            sb.append(infos[i] + "=" + getObject(i));
         }
      }

      sb.append(']');
      return sb.toString();
   }

   /**
    * Get the object values in the row.
    */
   Object[] getObjects(int ccnt, int dcnt, int mcnt, int[] dictIdxes,
                       Class[] types, boolean[] intcols, boolean[] tscols)

   {
      Object[] objs = new Object[ccnt];

      for(int j = 0; j < dcnt; j++) {
         if(dictIdxes[j] == -1) {
            objs[j] = getValueFromDim(types[j], intcols[j], tscols[j], (Long) groups2[j]);
         }
         else {
            objs[j] = groups2[j];
         }
      }

      // have measure? impossible detail
      if(mcnt > 0) {
         Object[] arr = new Object[mcnt];
         getObject(arr, mcnt);

         for(int j = 0; j < mcnt; j++) {
            if(arr[j] instanceof Number) {
               if(dictIdxes[j + dcnt] == -1) {
                  arr[j] = getValueFromMeasure(
                     types[j + dcnt], ((Number) arr[j]).doubleValue(), false);
               }
            }
         }

         System.arraycopy(arr, 0, objs, dcnt, mcnt);
      }

      return objs;
   }

   /**
    * Get the real value from the dim (indexed) value.
    */
   private Object getValueFromDim(Class type, boolean intcol, boolean tscol, long dim) {
      if(intcol) {
         return dim == Tool.NULL_LONG ? null : dim;
      }

      double val = Double.longBitsToDouble(dim);
      return getValueFromMeasure(type, val, tscol);
   }

   /**
    * Get the real value from the dim (indexed) value.
    */
   private Object getValueFromMeasure(Class type, double val, boolean tscol) {
      // @by davyc, see my comment in MVDoubleColumn.getDimValue
      if(val == Tool.NULL_DOUBLE) {
         return null;
      }

      if(type == Double.class) {
         return val;
      }
      else if(type == Timestamp.class || tscol) {
         return new Timestamp((long) val);
      }
      else if(type == Time.class) {
         return new Time((long) val);
      }
      else if(type == java.sql.Date.class) {
         return new java.sql.Date((long) val);
      }
      else if(type == java.util.Date.class) {
         return new java.util.Date((long) val);
      }
      else if(type != null && Number.class.isAssignableFrom(type)) {
         // should keep the correct type. (50301)
         if(type == Integer.class) {
            return (int) val;
         }
         else if(type == Long.class) {
            return (long) val;
         }
         else if(type == Short.class) {
            return (short) val;
         }
         else if(type == Byte.class) {
            return (byte) val;
         }
      }

      return val;
   }

   /**
    * Compare this row with another row.
    */
   public static class RowComparator implements Comparator<MVRow> {
      public RowComparator(boolean[] orders) {
         this.orders = orders;
      }

      @Override
      public int compare(MVRow brow1, MVRow brow2) {
         MVRow2 row1 = (MVRow2) brow1;
         MVRow2 row2 = (MVRow2) brow2;
         int gcnt = row1.groups2.length;

         for(int i = 0; i < gcnt; i++) {
            long rc = Tool.compare(row1.groups2[i], row2.groups2[i], true, true);

            if(rc != 0) {
               // make sure the result is not out of integer bounds
               rc = rc > 0 ? 1 : -1;
               return (int) (orders[i] ? rc : -rc);
            }
         }

         return 0;
      }

      private boolean[] orders;
   }

   Object[] groups2;
   Object[] aggregates2;
}
