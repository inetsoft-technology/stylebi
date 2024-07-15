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
package inetsoft.graph.data;

import inetsoft.graph.EGraph;
import inetsoft.graph.coord.Coordinate;
import inetsoft.util.SparseMatrix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.Format;
import java.util.*;

/**
 * AbstractDataSetFilter implements common functions of DataSetFilter.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public abstract class AbstractDataSetFilter extends AbstractDataSet
   implements DataSetFilter, AttributeDataSet
{
   /**
    * Create an instance of AbstractDataSetFilter.
    */
   public AbstractDataSetFilter(DataSet data) {
      super();

      this.data = data;
      this.attr = data instanceof AttributeDataSet ? (AttributeDataSet) data : null;
   }

   /**
    * Clear the calculated colum and row values.
    */
   @Override
   public synchronized void removeCalcValues() {
      super.removeCalcValues();
      data.removeCalcValues();
   }

   @Override
   public void removeCalcColValues() {
      super.removeCalcColValues();
      data.removeCalcColValues();
   }

   @Override
   public synchronized void removeCalcRowValues() {
      super.removeCalcRowValues();
      data.removeCalcRowValues();
   }

   /**
    * Get the calculated columns.
    */
   @Override
   public List<CalcColumn> getCalcColumns(boolean selfOnly) {
      // first add base data calc columns, then add self
      List<CalcColumn> list = selfOnly ? new ArrayList<>() : data.getCalcColumns();
      List<CalcColumn> scalcs = super.getCalcColumns(true);

      for(CalcColumn col : scalcs) {
         if(!list.contains(col)) {
            list.add(col);
         }
      }

      return list;
   }

   /**
    * Get the calculated rows.
    */
   @Override
   public List<CalcRow> getCalcRows() {
      List<CalcRow> list = super.getCalcRows();

      for(CalcRow row : data.getCalcRows()) {
         if(!list.contains(row)) {
            list.add(row);
         }
      }

      return list;
   }

   /**
    * Initialize any data for this graph.
    * @param graph the (innermost) egraph that will plot this dataset.
    * @param coord the (innermost) coordiante that will plot this dataset.
    */
   @Override
   public void prepareGraph(EGraph graph, Coordinate coord, DataSet dataset) {
      data.prepareGraph(graph, coord, dataset);
   }

   /**
    * Get the root data set.
    */
   @Override
   public DataSet getRootDataSet() {
      DataSet dset = data;

      if(data instanceof DataSetFilter) {
         DataSetFilter filter = (DataSetFilter) data;
         return filter.getRootDataSet();
      }

      return dset;
   }

   /**
    * Get the root row index on its root data set of the specified row.
    * @param r the specified row index.
    * @return the root row index on root data set, -1 if no root row.
    */
   @Override
   public int getRootRow(int r) {
      r = getBaseRow(r);

      if(r < 0) {
         return r;
      }

      if(data instanceof DataSetFilter) {
         return ((DataSetFilter) data).getRootRow(r);
      }

      return r;
   }

   /**
    * Get the root column index on its root data set of the specified column.
    * @param c the specified column index.
    * @return the root column index on root data set, -1 if no root column.
    */
   @Override
   public int getRootCol(int c) {
      DataSet dset = this;

      while(dset instanceof DataSetFilter) {
         DataSetFilter filter = (DataSetFilter) dset;
         c = filter.getBaseCol(c);

         if(c < 0) {
            return c;
         }

         dset = filter.getDataSet();
      }

      return c;
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
      return c < getColCount0() ? c : -1;
   }

   /**
    * Get the hyperlink at the specified cell.
    * @param col the specified column index.
    * @param row the specified row index.
    * @return the link at the specified cell.
    */
   @Override
   public HRef getHyperlink(int col, int row) {
      if(links != null) {
         Object link = links.get(row, col);

         if(link instanceof HRef) {
            return (HRef) link;
         }
      }

      if(attr == null) {
         return null;
      }

      col = getBaseCol(col);
      row = getBaseRow(row);
      return col >= 0 && row >= 0 ? attr.getHyperlink(col, row) : null;
   }

   @Override
   public void setHyperlink(int col, int row, HRef link) {
      if(links == null) {
         links = new SparseMatrix();
      }

      links.set(row, col, link);
   }

   /**
    * Get the hyperlink at the specified cell.
    * @param col the specified column name.
    * @param row the specified row index.
    * @return the link at the specified cell.
    */
   @Override
   public HRef getHyperlink(String col, int row) {
      int cidx = indexOfHeader(col);
      return getHyperlink(cidx, row);
   }

   /**
    * Get the drill hyperlinks at the specified cell.
    * @param col the specified column index.
    * @param row the specified row index.
    * @return the link at the specified cell.
    */
   @Override
   public HRef[] getDrillHyperlinks(int col, int row) {
      if(attr == null) {
         return new HRef[0];
      }

      col = getBaseCol(col);
      row = getBaseRow(row);
      return col >= 0 && row >= 0 ?
         attr.getDrillHyperlinks(col, row) : new HRef[0];
   }

   /**
    * Get the drill hyperlinks at the specified cell.
    * @param col the specified column name.
    * @param row the specified row index.
    * @return the link at the specified cell.
    */
   @Override
   public HRef[] getDrillHyperlinks(String col, int row) {
      int cidx = indexOfHeader(col);
      return getDrillHyperlinks(cidx, row);
   }

   /**
    * Get the per cell format.
    * @param row row number.
    * @param col column number.
    * @return format for the specified cell.
    */
   @Override
   public Format getFormat(int col, int row) {
      if(attr == null) {
         return null;
      }

      // calc column? use original column's format
      col = convertToField(col);
      col = getBaseCol(col);
      row = getBaseRow(row);
      return col >= 0 && row >= 0 ? attr.getFormat(col, row) : null;
   }

   /**
    * Convert a column index to real field index if it is a calc column field
    * index.
    */
   protected int convertToField(int col) {
      while(col >= getColCount0()) {
         int ocol = col;
         col = col - getColCount0();
         List<CalcColumn> calcs = getCalcColumns(true);

         // out of bounds
         if(col >= calcs.size()) {
            return -1;
         }

         String field = calcs.get(col).getField();
         col = indexOfHeader(field);

         // infinite loop
         if(col == ocol) {
            return -1;
         }
      }

      return col;
   }

   /**
    * Get the per cell format.
    * @param col the specified column name.
    * @param row the specified row index.
    * @return format for the specified cell.
    */
   @Override
   public Format getFormat(String col, int row) {
      int cidx = indexOfHeader(col);
      int ridx = getBaseRow(row);

      if(cidx < 0 || ridx < 0) {
         return null;
      }

      // call base's getFormat(String, col) instead of int column index since
      // DataSet (VSDataSet) may have special logic to handle strings that would be missing
      // if called with an index. (61292)
      return attr != null ? attr.getFormat(col, ridx) : getFormat(cidx, row);
   }

   /**
    * Get the base data set.
    * @return the base data set.
    */
   @Override
   public DataSet getDataSet() {
      return data;
   }

   /**
    * Get the comparer to sort data at the specified column.
    * @param col the specified column.
    * @return the comparer to sort data at the specified column.
    */
   @Override
   protected Comparator getComparator0(String col) {
      return DataSetComparator.getComparator(data.getComparator(col), this);
   }

   /**
    * Return the data at the specified cell.
    * @param col the specified column index.
    * @param row the specified row index.
    */
   @Override
   protected Object getData0(int col, int row) {
      return data.getData(getBaseCol(col), getBaseRow(row));
   }

   /**
    * Return the number of rows in the chart lens.
    */
   @Override
   protected int getRowCount0() {
      return data.getRowCount();
   }

   /**
    * Return the number of un-projected rows in the chart lens.
    */
   @Override
   protected int getRowCountUnprojected0() {
      if(data instanceof AbstractDataSet) {
         return ((AbstractDataSet) data).getRowCountUnprojected();
      }

      return getRowCount0();
   }

   /**
    * Return the number of columns in the data set without the calculated
    * column.
    */
   @Override
   protected int getColCount0() {
      return data.getColCount();
   }

   @Override
   protected int indexOfHeader0(String col, boolean all) {
      if(data instanceof AbstractDataSet) {
         return data.indexOfHeader(col, all);
      }

      return data.indexOfHeader(col);
   }

   /**
    * Return the column header at the specified column.
    * @param col the specified column index.
    */
   @Override
   protected String getHeader0(int col) {
      return data.getHeader(getBaseCol(col));
   }

   /**
    * Get the data type of the column.
    */
   @Override
   protected Class getType0(String col) {
      return data.getType(col);
   }

   /**
    * Check if the column is measure.
    * @param col the specified column name.
    */
   @Override
   protected boolean isMeasure0(String col) {
      return data.isMeasure(col);
   }

   @Override
   public DataSet clone(boolean shallow) {
      AbstractDataSetFilter obj = (AbstractDataSetFilter) super.clone(shallow);

      if(!shallow) {
         obj.data = (DataSet) this.data.clone();
      }

      obj.attr = obj.data instanceof AttributeDataSet ? (AttributeDataSet) obj.data : null;
      return obj;
   }

   // @by ChrisSpagnoli bug1422606337989 2015-2-2
   // Avoid duplicating "project forward", which is already in inner data sets
   @Override
   public void setRowsProjectedForward(final int rows) {
      // @by ChrisSpagnoli bug1430357109900 2015-5-6
      // Pass the set action down to the inner data set
      if(data instanceof AbstractDataSet &&
         ((AbstractDataSet) data).getRowsProjectedForward() != rows) // avoid cycle
      {
         ((AbstractDataSet) data).setRowsProjectedForward(rows);
      }

      // avoid cycle (49349)
      //super.setRowsProjectedForward(0);
      rowsProjectedForward = 0;
   }

   // @by ChrisSpagnoli bug1423535634052 2015-2-11
   // Expose the "project forward" from the inner data set
   @Override
   public int getRowsProjectedForward() {
      if(data instanceof AbstractDataSet) {
         return ((AbstractDataSet) data).getRowsProjectedForward();
      }

      return super.getRowsProjectedForward();
   }

   @Override
   public void setProjectColumn(String col) {
      if(data instanceof AbstractDataSet) {
         ((AbstractDataSet) data).setProjectColumn(col);
      }
   }

   @Override
   public String getProjectColumn() {
      return data instanceof AbstractDataSet ? ((AbstractDataSet) data).getProjectColumn() : null;
   }

   @Override
   public void dispose() {
      if(data != null) {
         data.dispose();
      }
   }

   @Override
   public boolean isDisposed() {
      return data != null && data.isDisposed();
   }

   private DataSet data;
   private AttributeDataSet attr;
   private SparseMatrix links;
}
