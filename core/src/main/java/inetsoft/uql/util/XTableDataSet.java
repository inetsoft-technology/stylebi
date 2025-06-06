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
package inetsoft.uql.util;

import inetsoft.graph.data.*;
import inetsoft.report.TableFilter;
import inetsoft.report.TableLens;
import inetsoft.report.filter.DefaultComparer;
import inetsoft.report.lens.AttributeTableLens;
import inetsoft.uql.XTable;
import inetsoft.uql.viewsheet.graph.VSFieldValue;
import inetsoft.util.SparseMatrix;
import inetsoft.util.Tool;

import java.text.Format;
import java.util.*;

/**
 * XTableDataSet, the data set generated from an XTable object.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class XTableDataSet extends AbstractDataSet implements AttributeDataSet {
   /**
    * Create an instance of XTableDataSet.
    */
   public XTableDataSet(XTable data) {
      super();

      data.moreRows(XTable.EOT);
      this.data = data;
      this.hcount = data.getHeaderRowCount();
      this.rcount = data.getRowCount() - hcount;

      if(data instanceof AbstractDataSet) {
         this.rcountu = Math.max(0, ((AbstractDataSet)data).getRowCountUnprojected() - hcount);
      }
      else {
         this.rcountu = this.rcount;
      }

      this.ccount = data.getColCount();
      this.hmap = new HashMap<>();
      this.headers = new String[ccount];
      this.dimension = new boolean[ccount];
      this.comparer = new Comparator[ccount];

      for(int i = 0; i < ccount; i++) {
         String header = XUtil.getHeader(data, i).toString();
         hmap.put(header, i);
         headers[i] = header;
         dimension[i] = data.getColType(i) == null ||
            !Number.class.isAssignableFrom(data.getColType(i));

         // for dimension, sort values in ascending order by default
         if(dimension[i]) {
            comparer[i] = new DefaultComparer();
         }
      }
   }

   /**
    * Get the base XTable object.
    * @return the base XTable object.
    */
   public XTable getTable() {
      return data;
   }

   @Override
   protected int indexOfHeader0(String col, boolean all) {
      Integer val = hmap.get(col);
      return val == null ? -1 : val;
   }

   /**
    * Return the data at the specified cell.
    * @param col the specified column index.
    * @param row the specified row index.
    */
   @Override
   protected Object getData0(int col, int row) {
      return data.getObject(row + hcount, col);
   }

   /**
    * Get the field value used for analysis.
    */
   public VSFieldValue getFieldValue(String col, int row) {
      Object obj = getData(col, row);
      String text = Tool.getDataString(obj);
      VSFieldValue pair = new VSFieldValue(col, text);
      return pair;
   }

   /**
    * Get the comparer to sort data at the specified column.
    * @param col the specified column.
    * @return the comparer to sort data at the specified column.
    */
   @Override
   protected Comparator getComparator0(String col) {
      int cidx = indexOfHeader(col);
      return cidx >= 0 ? comparer[cidx] : null;
   }

   /**
    * Set the comparer to sort data at the specified column.
    * @param col the specified column.
    * @param comp the comparer to sort data at the specified column.
    */
   public void setComparator(String col, Comparator comp) {
      int cidx = indexOfHeader(col);

      if(cidx >= 0) {
         comparer[cidx] = comp;
      }
   }

   /**
    * Return the header at the specified column.
    * @param col the specified column index.
    * @return the header at the specified column.
    */
   @Override
   protected String getHeader0(int col) {
      return headers[col];
   }

   /**
    * Get the data type of the column.
    */
   @Override
   protected Class<?> getType0(String col) {
      int cidx = indexOfHeader(col);
      return data.getColType(cidx);
   }

   /**
    * Check if the column is measure.
    * @param col the specified column name.
    * @return <tt>true</true> if is measure, <tt>false</tt> otherwise.
    */
   @Override
   protected boolean isMeasure0(String col) {
      int cidx = indexOfHeader(col);
      return !dimension[cidx];
   }

   /**
    * Check if the column is an aggreagte column.
    * @param col the specified column name.
    * @return <tt>true</true> if is, <tt>false</tt> otherwise.
    */
   public boolean isAggregate(String col) {
      return false;
   }

   /**
    * Return the number of rows in the chart lens.
    * The number of rows includes the header rows.
    * @return number of rows in the chart lens.
    */
   @Override
   protected int getRowCount0() {
      return rcount;
   }

   /**
    * Return the number of un-projected rows in the chart lens.
    * The number of un-projected rows includes the header rows.
    * @return number of un-projected rows in the chart lens.
    */
   @Override
   protected int getRowCountUnprojected0() {
      return rcountu;
   }

   /**
    * Return the number of columns in the chart lens.
    * @return number of columns in the chart lens.
    */
   @Override
   protected int getColCount0() {
      return ccount;
   }

   @Override
   public HRef getHyperlink(int col, int row) {
      if(links != null) {
         Object link = links.get(row, col);

         if(link instanceof HRef) {
            return (HRef) link;
         }
      }

      if(data instanceof AttributeTableLens) {
         return ((AttributeTableLens) data).getHyperlink(row, col);
      }

      return null;
   }

   @Override
   public void setHyperlink(int col, int row, HRef link) {
      if(links == null) {
         links = new SparseMatrix();
      }

      links.set(row, col, link);
   }

   @Override
   public Format getFormat(int col, int row) {
      if(data instanceof AttributeTableLens) {
         return ((AttributeTableLens) data).getFormat(row, col);
      }

      return null;
   }

   @Override
   public Object clone() {
      XTableDataSet obj = (XTableDataSet) super.clone();

      obj.comparer = this.comparer.clone();

      return obj;
   }

   @Override
   protected boolean isDynamicColumns() {
      if(data != null) { // use the correct variable
         TableLens temp = (TableLens) data;
         while(temp instanceof TableFilter) {
            if(data.isDynamicColumns()) {
               return true;
            }
            temp = ((TableFilter) temp).getTable();
         }
         if(temp != null) {
            return temp.isDynamicColumns();
         }
      }
      return false;
   }

   private final XTable data; // table data
   private final int hcount; // header row count
   private final int rcount; // row count
   private final int rcountu; // row count unprojected
   private final int ccount; // col count
   private final Map<String, Integer> hmap; // header map, header -> idx
   private final String[] headers; // headers
   private final boolean[] dimension; // dimension or measure flag
   private Comparator[] comparer; // comparer
   private SparseMatrix links;
}
