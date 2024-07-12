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
package inetsoft.report.filter;

import inetsoft.uql.XConstants;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.util.swap.*;

import java.util.Arrays;
import java.util.Vector;

/**
 * Calculate the value at the specified percentile.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class PthPercentileFormula implements Formula, java.io.Serializable {
   /**
    * Create a formula to get the value at the specified percentile.
    */
   public PthPercentileFormula(int percentile) {
      this.percentile = Math.min(percentile, 100);
      this.percentile = Math.max(this.percentile, 0);
   }

   // Needed to avoid Kyro error "Class cannot be created (missing no-arg constructor):"
   public PthPercentileFormula() {
   }

   /**
    * Reset the formula to start over.
    */
   @Override
   public void reset() {
      vs = null;
      dlist = null;
      flist = null;
      llist = null;
      ilist = null;
      slist = null;
   }

   /**
    * Add a double value to the formula.
    */
   @Override
   public void addValue(double v) {
      if(v == Tool.NULL_DOUBLE) {
         return;
      }

      if(dlist == null) {
         dlist = new XDoubleList();
      }

      dlist.add(v);
   }

   /**
    * Add double values to the formula.
    */
   @Override
   public void addValue(double[] v) {
      if(v[0] == Tool.NULL_DOUBLE) {
         return;
      }

      if(dlist == null) {
         dlist = new XDoubleList();
      }

      dlist.add(v[0]);
   }

   /**
    * Add a float value to the formula.
    */
   @Override
   public void addValue(float v) {
      if(v == Tool.NULL_FLOAT) {
         return;
      }

      if(flist == null) {
         flist = new XFloatList();
      }

      flist.add(v);
   }

   /**
    * Add a long value to the formula.
    */
   @Override
   public void addValue(long v) {
      if(v == Tool.NULL_LONG) {
         return;
      }

      if(llist == null) {
         llist = new XLongList();
      }

      llist.add(v);
   }

   /**
    * Add an int value to the formula.
    */
   @Override
   public void addValue(int v) {
      if(v == Tool.NULL_INTEGER) {
         return;
      }

      if(ilist == null) {
         ilist = new XIntList();
      }

      ilist.add(v);
   }

   /**
    * Add a short value to the formula.
    */
   @Override
   public void addValue(short v) {
      if(v == Tool.NULL_SHORT) {
         return;
      }

      if(slist == null) {
         slist = new XShortList();
      }

      slist.add(v);
   }

   /**
    * Add a value to the formula.
    */
   @Override
   public void addValue(Object v) {
      if(v == null) {
         return;
      }

      if(vs == null) {
         vs = new Vector<>();
      }

      vs.addElement(v);
   }

   /**
    * Set the default result option of this formula.
    * @param def <tt>true</tt> to use the default value of a formula if no
    * result, <tt>false</tt> to just return null.
    */
   @Override
   public void setDefaultResult(boolean def) {
      this.def = def;
   }

   /**
    * Get the default result option of this formula.
    * @return <tt>true</tt> to use the default value of a formula if no
    * result, <tt>false</tt> to just return null.
    */
   @Override
   public boolean isDefaultResult() {
      return def;
   }

   /**
    * Get the formula result.
    */
   @Override
   public Object getResult() {
      if(vs == null && dlist == null && flist == null && llist == null &&
         ilist == null && slist == null)
      {
         return def ? (double) 0 : null;
      }

      if(vs != null) {
         try {
            Object[] arr = new Object[vs.size()];
            vs.copyInto(arr);
            Tool.qsort(arr, 0, arr.length - 1, true, new NumericComparer());
            int pos = (int) Math.round(percentile / 100.0 * (arr.length - 1));
            return arr[pos];
         }
         catch(Exception e) {
         }

         return null;
      }
      else if(dlist != null) {
         double[] arr = dlist.getArray();
         int count = dlist.size();
         Arrays.sort(arr, 0, count);
         int pos = (int) Math.round(percentile / 100.0 * (count - 1));
         return arr[pos];
      }
      else if(flist != null) {
         float[] arr = flist.getArray();
         int count = flist.size();
         Arrays.sort(arr, 0, count);
         int pos = (int) Math.round(percentile / 100.0 * (count - 1));
         return arr[pos];
      }
      else if(llist != null) {
         long[] arr = llist.getArray();
         int count = llist.size();
         Arrays.sort(arr, 0, count);
         int pos = (int) Math.round(percentile / 100.0 * (count - 1));
         return arr[pos];
      }
      else if(ilist != null) {
         int[] arr = ilist.getArray();
         int count = ilist.size();
         Arrays.sort(arr, 0, count);
         int pos = (int) Math.round(percentile / 100.0 * (count - 1));
         return arr[pos];
      }
      else {
         short[] arr = slist.getArray();
         int count = slist.size();
         Arrays.sort(arr, 0, count);
         int pos = (int) Math.round(percentile / 100.0 * (count - 1));
         return arr[pos];
      }
   }

   /**
    * Get the formula double result.
    */
   @Override
   public double getDoubleResult() {
      if(dlist != null) {
         double[] arr = dlist.getArray();
         int count = dlist.size();
         Arrays.sort(arr, 0, count);
         int pos = (int) Math.round(percentile / 100.0 * (count - 1));
         return arr[pos];
      }

      return 0;
   }

   /**
    * Check if the result is null.
    */
   @Override
   public boolean isNull() {
      return dlist == null;
   }

   @Override
   public Object clone() {
      PthPercentileFormula ppf = new PthPercentileFormula(percentile);
      ppf.def = def;

      if(vs != null) {
         for(int i = 0; i < vs.size(); i++) {
            ppf.addValue(vs.elementAt(i));
         }
      }

      return ppf;
   }

   /**
    * Get formula display name.
    */
   @Override
   public String getDisplayName() {
      return Catalog.getCatalog().getString("PthPercentileFormula");
   }

   /**
    * Get formula name.
    */
   @Override
   public String getName() {
      return XConstants.PTHPERCENTILE_FORMULA;
   }

   private Vector<Object> vs = null;
   private XDoubleList dlist;
   private XFloatList flist;
   private XLongList llist;
   private XIntList ilist;
   private XShortList slist;
   private int percentile = 50;
   private boolean def;
}
