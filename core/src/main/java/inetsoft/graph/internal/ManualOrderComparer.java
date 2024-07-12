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
package inetsoft.graph.internal;

import inetsoft.report.Comparer;
import inetsoft.util.DefaultComparator;
import inetsoft.util.Tool;

import java.io.Serializable;
import java.util.*;

/**
 * ManualOrderComparer compares objects in manual oder.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public class ManualOrderComparer implements Comparator, Serializable, Comparer {
   /**
    * Constructor.
    * @param dtype the specified data type.
    * @param list the specified order list (string values).
    */
   public ManualOrderComparer(String dtype, List list) {
      super();

      // convert string to object
      List nlist = new ArrayList();

      for(int i = 0; list != null && i < list.size(); i++) {
         Object obj = list.get(i);

         if(obj instanceof String) {
            obj = Tool.getData(dtype, obj);
         }

         nlist.add(obj);
      }

      this.dtype = dtype;
      this.list = nlist;
   }

   /**
    * Constructor.
    */
   public ManualOrderComparer(Object[] vals) {
      super();

      // convert string to object
      this.list = new ArrayList();

      for(Object val : vals) {
         list.add(GTool.unwrap(val));
      }
   }

   /**
    * This method should return > 0 if v1 is greater than v2, 0 if
    * v1 is equal to v2, or < 0 if v1 is less than v2.
    * It must handle null values for the comparison values.
    * @param v1 comparison value.
    * @param v2 comparison value.
    * @return < 0, 0, or > 0 for v1<v2, v1==v2, or v1>v2.
    */
   @Override
   public int compare(Object v1, Object v2) {
      v1 = "".equals(v1) ? null : v1;
      v2 = "".equals(v2) ? null : v2;
      int idx1 = indexOfValue(v1);
      int idx2 = indexOfValue(v2);

      if(idx1 >= 0 && idx2 >= 0) {
         int val = idx1 - idx2;
         return val == 0 ? 0 : (val < 0 ? -1 : 1);
      }
      else if(idx1 >= 0) {
         return -1;
      }
      else if(idx2 >= 0) {
         return 1;
      }
      else {
         return comp.compare(v1, v2);
      }
   }

   /**
    * Get proper data for comparation.
    */
   protected Object getData(Object obj) {
      return obj;
   }

   /**
    * Get the index of the specified object in order list.
    * @param obj the specified object.
    * @return the index of the specified object in order list.
    */
   private int indexOfValue(Object obj) {
      int length = list.size();

      for(int i = 0; i < length; i++) {
         Object tobj = getData(list.get(i));

         if(Tool.equals(tobj, obj) ||
            // boolean true == 1. (50096)
            Objects.equals(Tool.getData(dtype, tobj), Tool.getData(dtype, obj)))
         {
            return i;
         }
      }

      return -1;
   }

   /**
    * This method should return > 0 if v1 is greater than v2, 0 if
    * v1 is equal to v2, or < 0 if v1 is less than v2.
    * It must handle null values for the comparison values.
    * @param v1 comparison value.
    * @param v2 comparison value.
    * @return < 0, 0, or > 0 for v1<v2, v1==v2, or v1>v2.
    */
   public int compare(double v1, double v2) {
      if(v1 == Tool.NULL_DOUBLE || v2 == Tool.NULL_DOUBLE) {
         return v1 == v2 ? 0 : (v1 == Tool.NULL_DOUBLE ? -1 : 1);
      }

      double val = v1 - v2;

      if(val < NEGATIVE_DOUBLE_ERROR) {
         return -1;
      }
      else if(val > POSITIVE_DOUBLE_ERROR) {
         return 1;
      }
      else {
         return 0;
      }
   }

   /**
    * This method should return > 0 if v1 is greater than v2, 0 if
    * v1 is equal to v2, or < 0 if v1 is less than v2.
    * It must handle null values for the comparison values.
    * @param v1 comparison value.
    * @param v2 comparison value.
    * @return < 0, 0, or > 0 for v1<v2, v1==v2, or v1>v2.
    */
   public int compare(float v1, float v2) {
      if(v1 == Tool.NULL_FLOAT || v2 == Tool.NULL_FLOAT) {
         return v1 == v2 ? 0 : (v1 == Tool.NULL_FLOAT ? -1 : 1);
      }

      float val = v1 - v2;

      if(val < NEGATIVE_FLOAT_ERROR) {
         return -1;
      }
      else if(val > POSITIVE_FLOAT_ERROR) {
         return 1;
      }
      else {
         return 0;
      }
   }

   /**
    * This method should return > 0 if v1 is greater than v2, 0 if
    * v1 is equal to v2, or < 0 if v1 is less than v2.
    * It must handle null values for the comparison values.
    * @param v1 comparison value.
    * @param v2 comparison value.
    * @return < 0, 0, or > 0 for v1<v2, v1==v2, or v1>v2.
    */
   public int compare(long v1, long v2) {
      return (int) (v1 - v2);
   }

   /**
    * This method should return > 0 if v1 is greater than v2, 0 if
    * v1 is equal to v2, or < 0 if v1 is less than v2.
    * It must handle null values for the comparison values.
    * @param v1 comparison value.
    * @param v2 comparison value.
    * @return < 0, 0, or > 0 for v1<v2, v1==v2, or v1>v2.
    */
   public int compare(int v1, int v2) {
      return v1 - v2;
   }

   /**
    * This method should return > 0 if v1 is greater than v2, 0 if
    * v1 is equal to v2, or < 0 if v1 is less than v2.
    * It must handle null values for the comparison values.
    * @param v1 comparison value.
    * @param v2 comparison value.
    * @return < 0, 0, or > 0 for v1<v2, v1==v2, or v1>v2.
    */
   public int compare(short v1, short v2) {
      return v1 - v2;
   }

   /**
    * Set the comparator that is used to compare values that are not on the
    * manual order list.
    */
   public void setDefaultComparator(Comparator comp) {
      if(comp != null) {
	 this.comp = comp;
      }
   }

   /**
    * Get the comparator that is used to compare values that are not on the
    * manual order list.
    */
   public Comparator getDefaultComparator() {
      return comp;
   }

   private static final double NEGATIVE_DOUBLE_ERROR = -0.000001d;
   private static final double POSITIVE_DOUBLE_ERROR = 0.000001d;
   private static final float NEGATIVE_FLOAT_ERROR = -0.000001f;
   private static final float POSITIVE_FLOAT_ERROR = 0.000001f;
   private Comparator comp = new DefaultComparator();
   private String dtype; // data type
   private List list; // order list (object values)
}
