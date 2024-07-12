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
package inetsoft.report.lens;

import inetsoft.report.*;
import inetsoft.report.internal.Util;
import inetsoft.report.internal.table.BinaryTableDataPath;
import inetsoft.report.internal.table.CancellableTableLens;
import inetsoft.uql.XMetaInfo;
import inetsoft.uql.XTable;
import inetsoft.util.Tool;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * TableLens that receives the joined rows from the scanner threads.
 */
abstract class JoinTable extends PagedTableLens {
   /**
    * Creates a new instance of JoinTable.
    *
    * @param leftTable            the left-hand table to join.
    * @param rightTable           the right-hand table to join.
    * @param leftCols             the indices of the join columns in the left-hand table.
    * @param rightCols            the indices of the join columns in the right-hand
    *                             table.
    * @param joinType             the type of join to use.
    * @param includeRightJoinCols <code>true</code> if the join columns from
    *                             the right-hand table should be included in
    *                             the joined table.
    */
   public JoinTable(TableLens leftTable, TableLens rightTable,
                    int[] leftCols, int[] rightCols, int joinType,
                    boolean includeRightJoinCols, int maxRows)
   {
      setTypes(new Class[]{ Integer.class, Integer.class });

      // sanity check
      if(leftCols.length != rightCols.length) {
         LOG.error("Join columns don't match");
         complete();
         return;
      }

      // sanity check
      if(leftCols.length == 0) {
         LOG.warn("No join columns specified");
         complete();
         return;
      }

      // sanity check
      for(int leftCol : leftCols) {
         if(leftCol >= leftTable.getColCount()) {
            LOG.error("Invalid join column in left-hand table");
            complete();
            return;
         }
      }

      // sanity check
      for(int rightCol : rightCols) {
         if(rightCol >= rightTable.getColCount()) {
            LOG.error("Invalid join column in right-hand table");
            complete();
            return;
         }
      }

      try {
         this.leftTable = leftTable;
         this.rightTable = rightTable;
         this.joinType = joinType;
         this.includeRightJoinCols = includeRightJoinCols;
         this.rightCols = rightCols;
         this.csensitive = Tool.isCaseSensitive();
         this.maxRows = maxRows;

         int headerRows = Math.min(leftTable.getHeaderRowCount(),
                                   rightTable.getHeaderRowCount());

         for(int i = 0; i < headerRows && leftTable.moreRows(i) && rightTable.moreRows(i); i++) {
            addRow(i, i);
            dataRowCnt++;
         }

         flushPending();
         this.headerRowCount = headerRows;
      }
      catch(Error | RuntimeException exc) {
         complete();
         throw exc;
      }
      catch(Exception exc) {
         complete();
         throw new RuntimeException(exc);
      }

      int rcols = rightTable.getColCount();
      rightColMap = new int[includeRightJoinCols ? rcols : rcols - rightCols.length];

      for(int i = 0, c = 0; c < rcols; c++) {
         if(!includeRightJoinCols) {
            boolean ignore = false;

            for(int rightCol : rightCols) {
               if(c == rightCol) {
                  ignore = true;
                  break;
               }
            }

            if(!ignore) {
               rightColMap[i++] = c;
            }
         }
         else {
            rightColMap[i++] = c;
         }
      }

      leftTable.moreRows(10000);
      rightTable.moreRows(10000);
   }

   /**
    * Return the number of rows on the top of the table to be treated
    * as header rows.
    *
    * @return number of header rows.
    */
   @Override
   public int getHeaderRowCount() {
      return headerRowCount;
   }

   /**
    * Cancel the lens and running queries if supported
    */
   public void cancel() {
      cancelled = cancelJoin();

      if(leftTable instanceof CancellableTableLens) {
         ((CancellableTableLens) leftTable).cancel();
         cancelled = ((CancellableTableLens) leftTable).isCancelled();
      }

      if(rightTable instanceof CancellableTableLens) {
         ((CancellableTableLens) rightTable).cancel();
         cancelled = ((CancellableTableLens) rightTable).isCancelled();
      }

      if(cancelled) {
         complete();
      }
   }

   /**
    * Check the TableLens to see if it is cancelled.
    */
   public boolean isCancelled() {
      return cancelled;
   }

   protected abstract boolean cancelJoin();

   /**
    * Adds a row to this table that is a join between the specified rows of
    * the left- and right-hand tables. If -1 is passed for either parameter,
    * the columns for that table will be filled with null. This is used for
    * outer joins.
    *
    * @param leftRow  the index of the row in the left-hand table.
    * @param rightRow the index of the row in the right-hand table.
    */
   public synchronized void addRow(int leftRow, int rightRow) {
      pendingRows.add(new int[]{ leftRow, rightRow });

      if(pendingRows.size() == 0x1fff * 20) {
         flushPending();
      }
   }

   protected synchronized void flushPending() {
      // sort the row index so the base table rows are in order. this may reduce
      // the swapping when rows are random
      pendingRows.sort(
         Comparator.comparingInt((int[] v) -> v[0])
            .thenComparingInt(v -> v[1]));

      for(int[] row : pendingRows) {
         super.addRow(new Object[]{ row[0], row[1] });
      }

      pendingRows.clear();
   }

   /**
    * Get the base table to delegate calls.
    */
   public TableRef getTableRef(int row, int col) {
      int lcols = leftTable.getColCount();

      if(col < lcols) {
         int row0 = getInt(row, 0);

         if(row0 < 0) {
            return new TableRef(dummy, row0, col);
         }

         return new TableRef(leftTable, row0, col);
      }
      else {
         int row0 = getInt(row, 1);

         if(row0 < 0) {
            return new TableRef(dummy, row0, col);
         }

         return new TableRef(rightTable, row0, rightColMap[col - lcols]);
      }
   }

   // Get the num of columns in joined table
   public int getJoinColCount() {
      if(joinColCnt != null) {
         return joinColCnt;
      }

      if(leftTable == null || rightTable == null) {
         return 0;
      }

      int lcols = leftTable.getColCount();
      int rcols = rightTable.getColCount();
      return joinColCnt = lcols + rcols - (includeRightJoinCols ? 0 : rightCols.length);
   }

   @Override
   public Class<?> getColType(int col) {
      int leftColCnt = leftTable.getColCount();

      if(col < leftColCnt) {
         return leftTable.getColType(col);
      }

      return rightTable.getColType(rightColMap[col - leftColCnt]);
   }

   TableDataDescriptor createDescriptor(JoinTableLens parent) {
      return new DefaultTableDataDescriptor(parent) {
         /**
          * Get meta info of a specified table data path.
          *
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

            if(leftTable == null || rightTable == null) {
               return null;
            }

            TableDataDescriptor descriptor;
            BinaryTableDataPath binaryPath = path instanceof BinaryTableDataPath ?
               (BinaryTableDataPath) path : null;
            TableDataPath opath;
            String header = path.getPath()[0];
            int cidx = path.getColIndex();
            int lcount = leftTable.getColCount();

            if(cidx >= 0 && cidx < lcount) {
               descriptor = leftTable.getDescriptor();
            }
            else if(cidx >= 0) {
               descriptor = rightTable.getDescriptor();
            }
            else if(containsColumn(leftTable, header) &&
               (binaryPath == null || !binaryPath.isRightTable()))
            {
               descriptor = leftTable.getDescriptor();
            }
            else if(containsColumn(rightTable, header)) {
               descriptor = rightTable.getDescriptor();
            }
            else {
               mmap.put(path, Tool.NULL);
               return null;
            }

            opath = new TableDataPath(path.getLevel(), path.getType(),
                                      path.getDataType(), new String[] {header});
            XMetaInfo minfo = descriptor.getXMetaInfo(opath);
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
          *
          * @return true if contains format.
          */
         @Override
         public boolean containsFormat() {
            return (leftTable != null && leftTable.containsFormat()) ||
               (rightTable != null && rightTable.containsFormat());
         }

         /**
          * Check if contains drill.
          *
          * @return <tt>true</tt> if contains drill.
          */
         @Override
         public boolean containsDrill() {
            return (leftTable != null && leftTable.containsDrill()) ||
               (rightTable != null && rightTable.containsDrill());
         }

         @Override
         public TableDataPath getCellDataPath(int row, int col) {
            BinaryTableDataPath path =
               new BinaryTableDataPath(super.getCellDataPath(row, col));
            path.setRightTable(col >= leftTable.getColCount());

            return path;
         }
      };
   }

   /**
    * Check if contains a column.
    */
   private boolean containsColumn(XTable table, String header) {
      if(table == null) {
         return false;
      }

      for(int i = 0; i < table.getColCount(); i++) {
         String col = Util.getHeader(table, i).toString();

         if(col.equals(header)) {
            return true;
         }
      }

      return false;
   }

   public int getMaxRows() {
      return maxRows;
   }

   public boolean isMaxAlert() {
      return maxAlert;
   }

   public void setMaxAlert(boolean maxAlert) {
      this.maxAlert = maxAlert;
   }

   void clearMetadata() {
      mmap.clear();
   }

   public int getJoinType() {
      return joinType;
   }

   public TableLens getLeftTable() {
      return leftTable;
   }

   public TableLens getRightTable() {
      return rightTable;
   }

   protected boolean isCaseSensitive() {
      return csensitive;
   }

   protected boolean checkMaxRows() {
      if(++dataRowCnt >= maxRows) {
         maxAlert = true;
         return true;
      }

      return false;
   }

   protected Object normalizeKeyValue(Object value) {
      if(value == null) {
         return null;
      }
      else if(value instanceof String) {
         value = ((String) value).trim();

         if(!csensitive) {
            value = ((String) value).toLowerCase();
         }
      }
      else if(value instanceof Number) {
         value = ((Number) value).doubleValue();
      }
      else if(value instanceof Date) {
         value = ((Date) value).getTime() / 1000;
      }
      else if(!(value instanceof Object[])) {
         value = value.toString().trim();
      }

      return value;
   }

   private final TableLens dummy = new AbstractTableLens() {
      public int getRowCount() {
         return JoinTable.this.getRowCount();
      }

      public int getColCount() {
         return JoinTable.this.getColCount();
      }

      public Object getObject(int r, int c) {
         return null;
      }
   };

   /**
    * A reference to a table to delegate method calls.
    */
   static class TableRef {
      public TableRef(TableLens table, int row, int col) {
         this.table = table;
         this.row = row;
         this.col = col;
      }

      public TableLens table;
      public int row;
      public int col;
   }

   private TableLens leftTable;
   private TableLens rightTable;
   private int joinType;
   private boolean includeRightJoinCols;
   private int[] rightCols;
   private boolean cancelled = false;
   private int headerRowCount = 0;
   // map from right table col index (in join table) to base right table col
   private int[] rightColMap;
   private final List<int[]> pendingRows = new ObjectArrayList<>();
   private final HashMap<TableDataPath, Object> mmap = new HashMap<>();
   private transient int dataRowCnt = 0;
   private transient int maxRows = Integer.MAX_VALUE;
   private transient boolean maxAlert = false;
   private transient boolean csensitive;
   private transient Integer joinColCnt;
   private static final Logger LOG = LoggerFactory.getLogger(JoinTable.class);
}
