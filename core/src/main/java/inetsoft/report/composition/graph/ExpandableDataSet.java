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
package inetsoft.report.composition.graph;

import inetsoft.graph.data.*;
import inetsoft.util.OrderedMap;

import java.util.*;

/**
 * ExpandableDataSet supports adding more columns with static values.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class ExpandableDataSet extends AbstractDataSetFilter {
   /**
    * Create an instance of ExpandableDataSet.
    * @param base the base data set.
    */
   public ExpandableDataSet(DataSet base) {
      super(base);

      this.brow = base.getRowCount();

      // @by ChrisSpagnoli bug1429507986738.reopen#2 2015-4-29
      // Use the Unprojected row count, to avoid duplicating the projection.
      if(base instanceof AbstractDataSet) {
         this.brow = ((AbstractDataSet) base).getRowCountUnprojected();
      }

      this.bcol = base.getColCount();
      map = new OrderedMap<>();
   }

   /**
    * Clear the calculated colum and row values.
    */
   @Override
   public synchronized void removeCalcValues() {
      super.removeCalcValues();
      this.brow = getDataSet().getRowCount();

      // @by ChrisSpagnoli bug1429507986738.reopen#23 2015-4-29
      // Use the Unprojected row count, to avoid duplicating the projection.
      if(getDataSet() instanceof AbstractDataSet) {
         this.brow = ((AbstractDataSet)getDataSet()).getRowCountUnprojected();
      }

      this.bcol = getDataSet().getColCount();
   }

   /**
    * Add a dimension column.
    * @param header the column header name.
    * @param val the static value.
    */
   public void addDimension(String header, Object val) {
      map.put(header, val);
      measures.put(header, Boolean.FALSE);
   }

   /**
    * Remove a dimension column.
    * @param header the column header name.
    */
   public void removeDimension(String header) {
      map.remove(header);
      measures.remove(header);
   }

   /**
    * Add a measure column.
    * @param header the column header name.
    * @param val the static value.
    */
   public void addMeasure(String header, Object val) {
      map.put(header, val);
      measures.put(header, Boolean.TRUE);
   }

   /**
    * Remove a measure column.
    * @param header the column measure name.
    */
   public void removeMeasure(String header) {
      map.remove(header);
      measures.remove(header);
   }

   /**
    * Get the base row index on its base data set of the specified row.
    * @param r the specified row index.
    * @return the base row index on base data set, -1 if no base row.
    */
   @Override
   public int getBaseRow(int r) {
      return r;
   }

   /**
    * Get the base column index on its base data set of the specified column.
    * @param c the specified column index.
    * @return the base column index on base data set, -1 if no base column.
    */
   @Override
   public int getBaseCol(int c) {
      if(c < bcol) {
         return c;
      }

      return -1;
   }

   /**
    * Return the data at the specified cell.
    * @param col the specified column name.
    * @param row the specified row index.
    * @return the data at the specified cell.
    */
   @Override
   public Object getData(String col, int row) {
      if(!map.containsKey(col)) {
         return getDataSet().getData(col, row);
      }

      return map.get(col);
   }

   /**
    * Return the data at the specified cell.
    * @param col the specified column index.
    * @param row the specified row index.
    * @return the data at the specified cell.
    */
   @Override
   public Object getData(int col, int row) {
      if(col < bcol) {
         return getDataSet().getData(col, row);
      }

      col = col - bcol;
      return col < map.size() ? map.getValue(col) : null;
   }

   /**
    * Return the column header at the specified column.
    * @param col the specified column index.
    * @return the column header at the specified column.
    */
   @Override
   public String getHeader0(int col) {
      if(col < bcol) {
         return getDataSet().getHeader(col);
      }

      col = col - bcol;
      return map.getKey(col);
   }

   /**
    * Get the data type of the column.
    */
   @Override
   public Class<?> getType0(String col) {
      if(!map.containsKey(col)) {
         return getDataSet().getType(col);
      }

      Object val = map.get(col);
      return val == null ? String.class : val.getClass();
   }

   @Override
   protected int indexOfHeader0(String col, boolean all) {
      if(!map.containsKey(col)) {
         if(getDataSet() instanceof AbstractDataSet) {
            return ((AbstractDataSet) getDataSet()).indexOfHeader(col, all);
         }
         else {
            return getDataSet().indexOfHeader(col);
         }
      }

      int index = map.indexOf(col);
      return index < 0 ? index : index + bcol;
   }

   /**
    * Get the comparer to sort data at the specified column.
    * @param col the specified column.
    * @return the comparer to sort data at the specified column.
    */
   @Override
   public Comparator<?> getComparator0(String col) {
      if(!map.containsKey(col)) {
         return getDataSet().getComparator(col);
      }

      return null;
   }

   /**
    * Check if the column is measure.
    * @param col the specified column name.
    * @return <tt>true</true> if is measure, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isMeasure0(String col) {
      if(!map.containsKey(col)) {
         return getDataSet().isMeasure(col);
      }

      return measures.get(col);
   }

   /*
    * Check if the column is dimension.
    * @param col the specified column name.
    * @return <tt>true</true> if is dimension, <tt>false</tt> otherwise.
    */
   /*
   public boolean isDimension0(String col) {
      if(!map.containsKey(col)) {
         return base.isDimension(col);
      }

      return !measures.get(col);
   }
   */

   /**
    * Return the number of rows in the chart lens.
    * @return number of rows in the chart lens.
    */
   @Override
   public int getRowCount0() {
      return brow;
   }

   @Override
   protected int getRowCountUnprojected0() {
      return getRowCount0();
   }

   /**
    * Return the number of columns in the chart lens.
    * @return number of columns in the chart lens.
    */
   @Override
   public int getColCount0() {
      return bcol + map.size();
   }

   /**
    * Set the order of values in the column. This can be used in javascript
    * to set the explicit ordering of values.
    */
   public void setOrder(String col, Object[] list) {
      if(getDataSet() instanceof VSDataSet) {
         ((VSDataSet) getDataSet()).setOrder(col, list);
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public ExpandableDataSet clone(boolean shallow) {
      // need to share the base for discrete column. (53449)
      ExpandableDataSet data = (ExpandableDataSet) super.clone(false);

      data.map = new OrderedMap<>(this.map);
      data.measures = new HashMap<>(this.measures);

      return data;
   }

   private int bcol;
   private int brow;
   private OrderedMap<String, Object> map;
   private Map<String, Boolean> measures = new HashMap<>();
}
