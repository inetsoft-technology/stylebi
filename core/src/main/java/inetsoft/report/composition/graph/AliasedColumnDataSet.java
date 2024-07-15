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
package inetsoft.report.composition.graph;

import inetsoft.graph.EGraph;
import inetsoft.graph.coord.Coordinate;
import inetsoft.graph.data.*;

import java.util.*;

/**
 * DataSet wrapper class for aliasing columns.
 * Mapped columns are treated as if they are the
 * same column as an already existing column in the DataSet.  All requests for
 * the mapped column will be redirected to the existing column.
 *
 * E.g:
 * AlisedColumnDataSet ds;
 * ds.map("aliasedName", "originalColumn");
 *
 * // returns same as ds.indexOfHeader("originalName");
 * ds.indexOfHeader("aliasedName");
 */
public class AliasedColumnDataSet implements DataSetFilter {
   /**
    * Creates a new AliasedColumnDataSet with no mappings.
    * @param data Wrapped DataSet
    */
   public AliasedColumnDataSet(DataSet data) {
      this(data, new HashMap<>());
   }

   /**
    * Creates a new AliasedColumnDataSet with the specified mappings.
    * @param data Wrapped DataSet
    * @param columnMap column names to map
    */
   public AliasedColumnDataSet(DataSet data, Map<String, String> columnMap) {
      this.columnMappings = columnMap;
      this.names = new ArrayList<>(columnMap.keySet());
      this.base = data;
   }

   /**
    * Returns a list of the aliased column names.
    */
   public List<String> getNames() {
      return Collections.unmodifiableList(names);
   }

   /**
    * Creates a new mapping.
    * @param colName The aliased column name.
    * @param mappedColName The mapped column in the wrapped dataset.
    */
   public void mapColumn(String colName, String mappedColName) {
      columnMappings.put(colName, mappedColName);
      names.add(colName);
   }

   /**
    * Returns the index of a mapped column in the original DataSet. If a
    * column name that is not mapped is passed in, returns the index of that
    * column by delegating to the wrapped DataSet.
    * @param col The column name.
    * @return idx of the mapped column,or -1 if not found.
    */
   private int getMappedColumnIdx(String col) {
      String mappedColumn = columnMappings.get(col);

      return mappedColumn == null ? base.indexOfHeader(col) :
         base.indexOfHeader(mappedColumn);
   }

   /**
    * Return the data at the specified cell.
    *
    * @param colName the specified column name.
    * @param row the specified row index.
    * @return the data at the specified cell.
    */
   @Override
   public Object getData(String colName, int row) {
      int col = getMappedColumnIdx(colName);

      return base.getData(col, row);
   }

   /**
    * Return the data at the specified cell.
    *
    * @param col the specified column index.
    * @param row the specified row index.
    * @return the data at the specified cell.
    */
   @Override
   public Object getData(int col, int row) {
      if(col >= base.getColCount()) {
         String mappedCol = names.get(col - base.getColCount());
         int mappedColIdx = getMappedColumnIdx(mappedCol);

         return base.getData(mappedColIdx, row);
      }

      return base.getData(col, row);
   }

   /**
    * Return the column header at the specified column.
    *
    * @param col the specified column index.
    * @return the column header at the specified column.
    */
   @Override
   public String getHeader(int col) {
      return base.getHeader(col);
   }

   /**
    * Get the data type of the column.
    */
   @Override
   public Class getType(String col) {
      String realCol = columnMappings.get(col);

      return realCol == null ? base.getType(col) : base.getType(realCol);
   }

   /**
    * Get the index of the specified header.
    *
    * @param col the specified column header.
    */
   @Override
   public int indexOfHeader(String col) {
      return getMappedColumnIdx(col);
   }

   /**
    * Get the comparer to sort data at the specified column.
    *
    * @param col the specified column.
    */
   @Override
   public Comparator getComparator(String col) {
      return base.getComparator(col);
   }

   /**
    * Check if the column is measure.
    * The designation of measure may impact the default scale created by
    * plotter.
    *
    * @param col the specified column name.
    * @return <tt>true</true> if is measure, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isMeasure(String col) {
      String mappedCol = columnMappings.get(col);

      return  mappedCol == null ?
         base.isMeasure(col) : base.isMeasure(mappedCol);
   }

   /**
    * Return the number of rows in the data set.
    */
   @Override
   public int getRowCount() {
      return base.getRowCount();
   }

   /**
    * Return the number of columns in the data set.
    */
   @Override
   public int getColCount() {
      return base.getColCount();
   }

   @Override
   public void prepareCalc(String dim, int[] rows, boolean calcMeasures) {
      String mappedCol = null;
      if(dim != null) {
         mappedCol = columnMappings.get(dim);
      }

      if(mappedCol == null) {
         base.prepareCalc(dim, rows, calcMeasures);
      }
      else {
         base.prepareCalc(mappedCol, rows, calcMeasures);
      }
   }

   /**
    * Initialize any data for this graph.
    * @param graph the (innermost) egraph that will plot this dataset.
    * @param coord the (innermost) coordiante that will plot this dataset.
    */
   @Override
   public void prepareGraph(EGraph graph, Coordinate coord, DataSet dataset) {
      base.prepareGraph(graph, coord, dataset);
   }

   /**
    * Add a calculated columns.
    */
   @Override
   public void addCalcColumn(CalcColumn col) {
      base.addCalcColumn(col);
   }

   /**
    * Get the calculated columns.
    */
   @Override
   public List<CalcColumn> getCalcColumns() {
      return base.getCalcColumns();
   }

   /**
    * Remove all calculated columns.
    */
   @Override
   public void removeCalcColumns() {
      base.removeCalcColumns();
   }

   /**
    * Add a calculated rows.
    */
   @Override
   public void addCalcRow(CalcRow col) {
      base.addCalcRow(col);
   }

   /**
    * Get the calculated rows.
    */
   @Override
   public List<CalcRow> getCalcRows() {
      return base.getCalcRows();
   }

   /**
    * Remove all calculated rows.
    */
   @Override
   public void removeCalcRows() {
      base.removeCalcRows();
   }

   /**
    * Remove calc rows with the specified type.
    */
   @Override
   public void removeCalcRows(Class cls) {
      base.removeCalcRows(cls);
   }

   /**
    * Clear the calculated column and row values.
    */
   @Override
   public void removeCalcValues() {
      base.removeCalcValues();
   }

   @Override
   public void removeCalcColValues() {
      base.removeCalcColValues();
   }

   @Override
   public void removeCalcRowValues() {
      base.removeCalcRowValues();
   }

   /**
    * Get the base data set.
    */
   @Override
   public DataSet getDataSet() {
      return base;
   }

   /**
    * Get the root data set.
    */
   @Override
   public DataSet getRootDataSet() {
      DataSet dset = this;

      while(dset instanceof DataSetFilter) {
         DataSetFilter filter = (DataSetFilter) dset;
         dset = filter.getDataSet();
      }

      return dset;
   }

   /**
    * Get the base row index on its base data set of the specified row.
    *
    * @param r the specified row index.
    * @return the base row index on base data set, -1 if no base row.
    */
   @Override
   public int getBaseRow(int r) {
      return r;
   }

   /**
    * Get the root row index on its root data set of the specified row.
    *
    * @param r the specified row index.
    * @return the root row index on root data set, -1 if no root row.
    */
   @Override
   public int getRootRow(int r) {
      return r;
   }

   /**
    * Get the base column index on its base data set of the specified column.
    *
    * @param c the specified column index.
    * @return the base column index on base data set, -1 if no base column.
    */
   @Override
   public int getBaseCol(int c) {
      return c;
   }

   /**
    * Get the root column index on its root data set of the specified column.
    *
    * @param c the specified column index.
    * @return the root column index on root data set, -1 if no root column.
    */
   @Override
   public int getRootCol(int c) {
      return c;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public Object clone() {
      return clone(false);
   }

   @Override
   public DataSet clone(boolean shallow) {
      try {
         AliasedColumnDataSet obj = (AliasedColumnDataSet) super.clone();

         if(!shallow) {
            obj.base = (DataSet) this.base.clone();
         }

         obj.names = new ArrayList<>(this.names);
         obj.columnMappings = new HashMap<>(this.columnMappings);

         return obj;
      }
      catch(CloneNotSupportedException e) {

      }

      return null;
   }

   private Map<String, String> columnMappings;
   private List<String> names;
   private DataSet base;
}
