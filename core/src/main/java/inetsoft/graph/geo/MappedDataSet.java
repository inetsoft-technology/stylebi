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
package inetsoft.graph.geo;

import inetsoft.graph.data.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This dataset adds columns by mapping a row to a new value. It can be used
 * to add a map feature ID column using a name matcher.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public class MappedDataSet extends AbstractDataSetFilter {
   /**
    * Creates a new instance of <tt>GeoDataSet</tt>.
    *
    * @param data          the source data set.
    * @param names  the area feature geocoder.
    * @param matchers the point feature geocoder.
    */
   public MappedDataSet(DataSet data, String[] names, NameMatcher[] matchers) {
      super(data);

      this.names = names;
      this.matchers = matchers;
      columnCount = data.getColCount();
   }

   /**
    * Get the name matchers for mapping a value to feature ID.
    */
   public NameMatcher[] getNameMatchers() {
      return matchers;
   }

   /**
    * Get the name of the columns to map.
    */
   public String[] getNames() {
      return names;
   }

   // DataSet methods

   /**
    * Return the data at the specified cell.
    * @param col the specified column index.
    * @param row the specified row index.
    * @return the data at the specified cell.
    */
   @Override
   protected Object getData0(int col, int row) {
      if(col < columnCount) {
         return getDataSet().getData(col, row);
      }

      return matchers[col - columnCount].getFeatureId(getDataSet(), row);
   }

   /**
    * Return the column header at the specified column.
    * @param col the specified column index.
    * @return the column header at the specified column.
    */
   @Override
   protected String getHeader0(int col) {
      return (col < columnCount) ?
         getDataSet().getHeader(col) : names[col - columnCount];
   }

   /**
    * Get the data type of the column.
    */
   @Override
   protected Class getType0(String col) {
      for(int i = 0; i < names.length; i++) {
         if(names[i].equals(col)) {
            return String.class;
         }
      }

      return getDataSet().getType(col);
   }

   @Override
   protected int indexOfHeader0(String col, boolean all) {
      for(int i = 0; i < names.length; i++) {
         if(names[i].equals(col)) {
            return i + columnCount;
         }
      }

      if(getDataSet() instanceof AbstractDataSet) {
         return ((AbstractDataSet) getDataSet()).indexOfHeader(col, all);
      }

      return getDataSet().indexOfHeader(col);
   }

   /**
    * Return the number of rows in the chart lens.
    * @return number of rows in the chart lens.
    */
   @Override
   protected int getRowCount0() {
      return getDataSet().getRowCount();
   }

   /**
    * Return the number of columns in the chart lens.
    * @return number of columns in the chart lens.
    */
   @Override
   protected int getColCount0() {
      return columnCount + names.length;
   }

   /**
    * Check if the column is measure.
    * @param col the specified column name.
    */
   @Override
   protected boolean isMeasure0(String col) {
      int idx = indexOfHeader0(col);

      if(idx < 0) {
         LOG.warn("Column not found: " + col);
         throw new RuntimeException("Column not found: " + col);
      }

      return (idx < columnCount) ? getDataSet().isMeasure(col) : false;
   }

   /**
    * Get the base column index on its base data set of the specified column.
    * @param c the specified column index.
    * @return the base column index on base data set, -1 if no base column.
    */
   @Override
   public int getBaseCol(int c) {
      return (c < columnCount) ? c : -1;
   }

   /**
    * Clear the calculated colum and row values.
    */
   @Override
   public synchronized void removeCalcValues() {
      super.removeCalcValues();
      columnCount = getDataSet().getColCount();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public Object clone() {
      MappedDataSet obj = (MappedDataSet) super.clone();
      obj.names = this.names.clone();
      obj.matchers = this.matchers.clone();

      return obj;
   }

   private int columnCount = 0;
   private String[] names = {};
   private NameMatcher[] matchers;
   private static final Logger LOG = LoggerFactory.getLogger(MappedDataSet.class);
}
