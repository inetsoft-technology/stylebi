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
package inetsoft.report.internal.table;

import inetsoft.report.*;
import inetsoft.report.lens.AbstractTableLens;
import inetsoft.report.lens.DefaultTableDataDescriptor;
import inetsoft.uql.XMetaInfo;
import inetsoft.uql.XTable;
import inetsoft.util.Tool;

import java.util.*;

/**
 * Table lens for concatenating multiple table lenses into one. All table lenses
 * must have same number of columns. This class ignores all non-data related
 * information.
 *
 * @version 7.0
 * @author InetSoft Technology Corp
 */
public class ConcatTableLens extends AbstractTableLens implements CancellableTableLens {
   /**
    * Create a concatenation of two tables.
    */
   public ConcatTableLens(TableLens... tables) {
      setTables(tables);
   }

   /**
    * Set the tables to be combined.
    */
   public void setTables(TableLens... tables) {
      this.tables = tables;

      boolean negative = false;
      rowcnts = new int[tables.length];
      rowcnt = tables[0].getHeaderRowCount();

      for(int i = 0; i < rowcnts.length; i++) {
         rowcnts[i] = tables[i].getRowCount();
         rowcnt += rowcnts[i] - tables[i].getHeaderRowCount();
         negative = negative || rowcnts[i] < 0;
      }

      if(negative) {
         rowcnt = -1;
      }
   }

   /**
    * Get the contained tables.
    */
   public TableLens[] getTables() {
      return tables;
   }

   /**
    * Add a table to the list.
    */
   public void addTable(TableLens table) {
      TableLens[] arr = new TableLens[tables.length + 1];

      System.arraycopy(tables, 0, arr, 0, tables.length);
      arr[arr.length - 1] = table;

      setTables(arr);
   }

   /**
    * Return the number of rows in the table. The number of rows includes
    * the header rows. If the table is loading in background and loading
    * is not done, return the negative number of loaded rows minus 1.
    * @return number of rows in table.
    */
   @Override
   public int getRowCount() {
      if(rowcnt > 0) {
         return rowcnt;
      }

      boolean negative = false;
      rowcnt = tables[0].getHeaderRowCount();

      for(int i = 0; i < rowcnts.length; i++) {
         int cnt = tables[i].getRowCount() - tables[i].getHeaderRowCount();

         if(cnt < 0) {
            negative = true;
            cnt = -cnt;
         }

         rowcnt += cnt;
      }

      if(negative) {
         rowcnt = -rowcnt;
      }

      return rowcnt;
   }

   /**
    * Return the number of columns in the table. The number of columns
    * includes the header columns.
    * @return number of columns in table.
    */
   @Override
   public int getColCount() {
      return tables[0].getColCount();
   }

   /**
    * Check if there are more rows. The row index is the row that will be
    * accessed. This method must block until the row is available, or
    * return false if the row does not exist in the table. This method is
    * used to iterate through the table, and allow partial table to be
    * accessed in report processing.
    * @param row row number.
    * @return true if the row exists, or false if no more rows.
    */
   @Override
   public boolean moreRows(int row) {
      TableLoc loc = findTable(row);

      return (loc == null) ? false : loc.getTable().moreRows(loc.getRow());
   }

   /**
    * Return the number of rows on the top of the table to be treated
    * as header rows.
    * @return number of header rows.
    */
   @Override
   public int getHeaderRowCount() {
      return tables[0].getHeaderRowCount();
   }

   /**
    * Return the number of columns on the left of the table to be
    * treated as header columns.
    */
   @Override
   public int getHeaderColCount() {
      return tables[0].getHeaderColCount();
   }

   /**
    * Return the number of rows on the bottom of the table to be treated
    * as tail rows.
    * @return number of header rows.
    */
   @Override
   public int getTrailerRowCount() {
      return tables[tables.length - 1].getTrailerRowCount();
   }

   /**
    * Return the number of columns on the right of the table to be
    * treated as tail columns.
    */
   @Override
   public int getTrailerColCount() {
      return tables[0].getTrailerColCount();
   }

   /**
    * Return the value at the specified cell.
    * @param r row number.
    * @param c column number.
    * @return the value at the location.
    */
   @Override
   public Object getObject(int r, int c) {
      TableLoc loc = findTable(r);

      return (loc == null) ? null : loc.getTable().getObject(loc.getRow(), c);
   }

   /**
    * Dispose the table to clear up temporary resources.
    */
   @Override
   public void dispose() {
      for(int i = 0; i < tables.length; i++) {
         tables[i].dispose();
      }

      mmap.clear();
   }

   /**
    * Find the table that contains the numbered row.
    */
   private TableLoc findTable(int r) {
      for(int i = 0; i < rowcnts.length; i++) {
         int headerR = (i == 0) ? 0 : tables[i].getHeaderRowCount();

         if(r + headerR < rowcnts[i]) {
            return new TableLoc(tables[i], r + headerR);
         }

         if(rowcnts[i] < 0) {
            if(tables[i].moreRows(r + headerR)) {
               return new TableLoc(tables[i], r + headerR);
            }

            // if the moreRows failed, the rowCount must be positive now
            rowcnts[i] = tables[i].getRowCount();
         }

         if(i == 0) {
            r -= rowcnts[i];
         }
         else {
            r -= rowcnts[i] - 1; // header cells not included
         }
      }

      return null; // if not found
   }

   /**
    * Get internal table data descriptor which contains table structural
    * infos.
    * @return table data descriptor.
    */
   @Override
   public TableDataDescriptor getDescriptor() {
      if(descriptor == null) {
         descriptor = new DefaultTableDataDescriptor(this) {
            /**
             * Get meta info of a specified table data path.
             * @param path the specified table data path.
             * @return meta info of the table data path.
             */
            @Override
            public XMetaInfo getXMetaInfo(TableDataPath path) {
               if(!path.isCell()) {
                  return null;
               }

               Object obj = mmap.get(path);

               if(obj instanceof XMetaInfo) {
                  return (XMetaInfo) obj;
               }
               else if(obj != null) {
                  return null;
               }

               TableDataDescriptor descriptor = tables[0].getDescriptor();
               XMetaInfo minfo = descriptor.getXMetaInfo(path);
               mmap.put(path, minfo == null ? Tool.NULL : (Object) minfo);

               return minfo;
            }

            @Override
            public List<TableDataPath> getXMetaInfoPaths() {
               List<TableDataPath> list = new ArrayList<>();

               if(!mmap.isEmpty()) {
                  list.addAll(mmap.keySet());
               }

               return list;
            }

            /**
             * Check if contains format.
             * @return true if contains format.
             */
            @Override
            public boolean containsFormat() {
               return tables[0].containsFormat();
            }

            /**
             * Check if contains drill.
             * @return <tt>true</tt> if contains drill.
             */
            @Override
            public boolean containsDrill() {
               return tables[0].containsDrill();
            }
         };
      }

      return descriptor;
   }

   @Override
   public void cancel() {
      for(TableLens tbl : tables) {
         if(tbl instanceof CancellableTableLens) {
            ((CancellableTableLens) tbl).cancel();
         }
      }
   }

   @Override
   public boolean isCancelled() {
      return Arrays.stream(tables)
         .anyMatch(a -> a instanceof CancellableTableLens && ((CancellableTableLens) a).isCancelled());
   }

   /**
    * Record the table and the row index in the table.
    */
   static class TableLoc {
      public TableLoc(TableLens table, int row) {
         this.table = table;
         this.row = row;
      }

      public TableLens getTable() {
         return table;
      }

      public int getRow() {
         return row;
      }

      private TableLens table;
      private int row;
   }

   /**
    * @return the report/vs name which this filter was created for,
    * and will be used when insert audit record.
    */
   @Override
   public String getReportName() {
      Object value = getReportProperty(XTable.REPORT_NAME);
      return value == null ? null : value + "";
   }

   /**
    * @return the report type which this filter was created for:
    * ExecutionBreakDownRecord.OBJECT_TYPE_REPORT or
    * ExecutionBreakDownRecord.OBJECT_TYPE_VIEWSHEET
    */
   @Override
   public String getReportType() {
      Object value = getReportProperty(XTable.REPORT_TYPE);
      return value == null ? null : value + "";
   }

   public Object getReportProperty(String key) {
      Object value = super.getProperty(key);

      if(value == null && tables != null) {
         for(int i = 0; i < tables.length; i++) {
            value = tables[i].getProperty(key);

            if(value != null) {
               break;
            }
         }
      }

      return value;
   }

   private TableLens[] tables = {};
   private int[] rowcnts = {}; // number of rows in each table lens
   private int rowcnt = 0; // total number of rows
   private Hashtable mmap = new Hashtable(); // meta info table
}
