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
package inetsoft.graph.data;

import com.inetsoft.build.tern.*;
import inetsoft.graph.internal.GTool;
import inetsoft.graph.internal.ManualOrderComparer;

import java.io.Serializable;
import java.util.*;

/**
 * This class implements dataset interface and provide a in-memory
 * implementation of datasets.
 *
 * @version 10, 3/7/2008
 * @author InetSoft Technology Corp
 */
@TernClass
public class DefaultDataSet extends AbstractDataSet {
   /**
    * Create a dataset.
    * @param data0 data in rows and columns. The first row is the column
    * headers.
    */
   @TernConstructor
   public DefaultDataSet(Object[][] data0) {
      if(data0.length == 0) {
         return;
      }

      int nrow = data0.length - 1;
      int ncol = data0[0].length;

      data = new Object[ncol][nrow];
      headers = new String[ncol];

      // in case values are from JS
      for(int i = 0; i < data0.length; i++) {
         for(int j = 0; j < data0[i].length; j++) {
            data0[i][j] = GTool.unwrap(data0[i][j]);
         }
      }

      for(int i = 0; i < ncol; i++) {
         data[i] = new Object[nrow];

         for(int r = 0; r < nrow; r++) {
            data[i][r] = data0[r + 1][i];
         }

         headers[i] = data0[0][i] + "";

         ColInfo info = new ColInfo();
         info.setIndex(i);

         if(data[i].length > 0 && data[i][0] instanceof Number) {
            info.setMeasure(true);
         }

         colmap.put(headers[i], info);
      }
   }

   /**
    * Return the data at the specified cell.
    * @param col the specified column index.
    * @param row the specified row index.
    */
   @Override
   protected Object getData0(int col, int row) {
      return data[col][row];
   }

   /**
    * Return the column header at the specified column.
    * @param col the specified column index.
    * @return the column header at the specified column.
    */
   @Override
   protected String getHeader0(int col) {
      return headers[col];
   }

   /**
    * Get the data type of the column.
    */
   @Override
   protected Class getType0(String col) {
      int idx = indexOfHeader(col);

      if(idx < 0) {
         return String.class;
      }

      for(int i = 0; i < data[idx].length; i++) {
         if(data[idx][i] != null) {
            return data[idx][i].getClass();
         }
      }

      return String.class;
   }

   @Override
   protected int indexOfHeader0(String col, boolean all) {
      ColInfo info = colmap.get(col);
      return (info != null) ? info.getIndex() : -1;
   }

   /**
    * Get the comparer to sort data at the specified column.
    * @param col the specified column.
    * @return the comparer to sort data at the specified column.
    */
   @Override
   protected Comparator getComparator0(String col) {
      ColInfo info = colmap.get(col);
      return (info != null) ? info.getComparator() : null;
   }

   /**
    * Set the order of values in the column. This can be used in javascript
    * to set the explicit ordering of values.
    */
   @TernMethod
   public void setOrder(String col, Object[] list) {
      ColInfo info = colmap.get(col);

      if(info != null) {
         ManualOrderComparer comp = new ManualOrderComparer(list);

         if(info.getComparator() != null) {
            comp.setDefaultComparator(info.getComparator());
         }

         info.setComparator(comp);
      }
   }

   /**
    * Set the comparer to sort data at the specified column.
    * @param col the specified column.
    * @param comp the comparer to sort data at the specified column.
    */
   public void setComparator(String col, Comparator comp) {
      ColInfo info = colmap.get(col);
      info.setComparator(comp);
   }

   /**
    * Check if the column is measure.
    * @param col the specified column name.
    * @return <tt>true</true> if is measure, <tt>false</tt> otherwise.
    */
   @Override
   protected boolean isMeasure0(String col) {
      ColInfo info = colmap.get(col);
      return (info != null) && info.isMeasure();
   }

   /**
    * Return the number of rows in the chart lens.
    * @return number of rows in the chart lens.
    */
   @Override
   protected int getRowCount0() {
      return (data.length > 0) ? data[0].length : 0;
   }

   /**
    * Return the number of rows in the chart lens.
    * Projected / Un-projected does not differ for DefaultDataSet.
    * @return number of rows in the chart lens.
    */
   @Override
   protected int getRowCountUnprojected0() {
      return getRowCount0();
   }

   /**
    * Return the number of columns in the chart lens.
    * @return number of columns in the chart lens.
    */
   @Override
   protected int getColCount0() {
      return data.length;
   }

   /**
    * Column info.
    */
   private static class ColInfo implements Serializable {
      public void setMeasure(boolean flag) {
         measure = flag;
      }

      public boolean isMeasure() {
         return measure;
      }

      public void setIndex(int idx) {
         this.idx = idx;
      }

      public int getIndex() {
         return idx;
      }

      public void setComparator(Comparator comp) {
         this.comp = comp;
      }

      public Comparator getComparator() {
         return comp;
      }

      private boolean measure = false;
      private int idx;
      private Comparator comp;
   }

   /**
    * Clone the defualt data set.
    */
   @Override
   public Object clone() {
      DefaultDataSet ds = (DefaultDataSet) super.clone();

      if(data != null) {
         Object[][] ndata = data.clone();

         for(int i = 0; i < ndata.length; i++) {
            ndata[i] = data[i].clone();

            for(int j = 0; j < ndata[i].length; j++) {
               ndata[i][j] = data[i][j];
            }
         }

         ds.data = ndata;
      }

      return ds;
   }

   private Object[][] data = {}; // col, row
   private String[] headers = {};
   private Map<String, ColInfo> colmap = new HashMap<>();
}
