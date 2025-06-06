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
package inetsoft.report.lens.xnode;

import inetsoft.mv.DFWrapper;
import inetsoft.report.*;
import inetsoft.report.internal.Util;
import inetsoft.report.internal.table.CachedTableLens;
import inetsoft.report.internal.table.CancellableTableLens;
import inetsoft.report.lens.AbstractTableLens;
import inetsoft.report.lens.DefaultTableDataDescriptor;
import inetsoft.uql.*;
import inetsoft.uql.asset.internal.ColumnIndexMap;
import inetsoft.uql.jdbc.JDBCTableNode;
import inetsoft.uql.table.XSwappableTable;
import inetsoft.uql.util.XNodeTable;
import inetsoft.uql.util.XTableTableNode;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;

/**
 * Table lens used to convert a data tree to a table. If the data tree is
 * already a table, the table is used directly. Otherwise, if the data
 * tree is a sequence, each item in the sequence is transformed into a row.
 * Otherwise, the tree is converted into a one row table, with the
 * columns from each branch of the tree.
 * <p>
 * Note: only after setNode is called could we access it as a table lens.
 *
 * @version 9.1
 * @author InetSoft Technology Corp
 */
public class XNodeTableLens extends AbstractTableLens
   implements CancellableTableLens, CachedTableLens, DFWrapper
{
   /**
    * Create an empty XNodeTableLens.
    */
   public XNodeTableLens() {
      delegate = new XNodeTable();
   }

   /**
    * Create a table from a data tree.
    */
   public XNodeTableLens(XNode root) {
      delegate = new XNodeTable();
      setNode(root);
   }

   /**
    * Set the data tree used to create a table.
    */
   public void setNode(XNode root) {
      delegate.setNode(root);
      this.table = delegate.getTable();
      this.descriptor = null;
      init();
   }

   /**
    * Cancel the lens and running queries if supported
    */
   @Override
   public void cancel() {
      delegate.cancel();
   }

   /**
    * Check the XNodeTableLens to see if it is cancelled.
    */
   @Override
   public boolean isCancelled() {
      return delegate.isCancelled();
   }

   /**
    * Check if this table lens is valid for use.
    * @return <tt>true</tt> if valid for use, <tt>false</tt> otherwise.
    */
   public boolean isValid() {
      return true;
   }

   /**
    * Clear all cached data.
    */
   @Override
   public void clearCache() {
      // do nothing
   }

   /**
    * Dispose the table lens.
    */
   @Override
   public void dispose() {
      // XNodeTable.dispose() is called by its finalizer. since it can be shared through
      // various paths, it's safer to just let finalizer disposing it. (59688)
      //delegate.dispose();
   }

   /**
    * Check if there are more rows. The row index is the row that will be
    * accessed. This method must block until the row is available, or
    * return false if the row does not exist in the table. This method is
    * used to iterate through the table, and allow partial table to be
    * accessed in report processing.
    * @param row row number. If EOT is passed in, this method should wait
    * until the table is fully loaded.
    * @return true if the row exists, or false if no more rows.
    */
   @Override
   public boolean moreRows(int row) {
      XNode root = delegate.getNode();
      boolean result = table.moreRows(row);

      if(root instanceof JDBCTableNode) {
         Object joinMaxRows = root.getAttribute("join.table.maxrows");

         if(joinMaxRows instanceof Integer) {
            int maxRows = (int) joinMaxRows;
            int rowCount = getRowCount();

            if(rowCount < 0) {
               rowCount = (-1 * rowCount) + 1;
            }

            if(maxRows > 0 && rowCount >= maxRows) {
               String message = Catalog.getCatalog().getString("join.table.limited", maxRows);
               boolean messageExist = Tool.existUserMessage(message);
               Tool.addUserMessage(message);

               if(!messageExist) {
                  LOG.warn(message);
               }
            }
         }
      }

      return result;
   }

   /**
    * Return the number of rows in the table. The number of rows includes
    * the header rows. If the table is loading in background and loading
    * is not done, return the negative number of loaded rows minus 1.
    * @return number of rows in table.
    */
   @Override
   public int getRowCount() {
      return table.getRowCount();
   }

   /**
    * Return the number of columns in the table. The number of columns
    * includes the header columns.
    * @return number of columns in table.
    */
   @Override
   public int getColCount() {
      return table.getColCount();
   }

   /**
    * Return the number of rows on the top of the table to be treated
    * as header rows.
    * @return number of header rows.  Default is 1.
    */
   @Override
   public int getHeaderRowCount() {
      return table.getHeaderRowCount();
   }

   /**
    * Return the number of columns on the left of the table to be
    * treated as header columns.
    */
   @Override
   public int getHeaderColCount() {
      return table.getHeaderColCount();
   }

   /**
    * Return the number of rows on the bottom of the table to be treated
    * as trailer rows.
    * @return number of header rows.
    */
   @Override
   public int getTrailerRowCount() {
      return table.getTrailerRowCount();
   }

   /**
    * Return the number of columns on the right of the table to be
    * treated as trailer columns.
    */
   @Override
   public int getTrailerColCount() {
      return table.getTrailerColCount();
   }

   @Override
   public Class getColType(int col) {
      return table.getColType(col);
   }

   /**
    * Check if is primitive.
    * @return <tt>true</tt> if is primitive, <tt>false</tt> otherwise.
    */
   @Override
   public final boolean isPrimitive(int col) {
      return table.isPrimitive(col);
   }

   /**
    * Check if the value at one cell is null.
    * @param r the specified row index.
    * @param c column number.
    * @return <tt>true</tt> if null, <tt>false</tt> otherwise.
    */
   @Override
   public final boolean isNull(int r, int c) {
      Object val = (matrix == null) ? SparseMatrix.NULL : matrix.get(r, c);
      return (val == SparseMatrix.NULL) ? table.isNull(r, c) : val == null;
   }

   /**
    * Return the value at the specified cell.
    * @param r row number.
    * @param c column number.
    * @return the value at the location.
    */
   @Override
   public final Object getObject(int r, int c) {
      Object val = (matrix == null) ? SparseMatrix.NULL : matrix.get(r, c);
      return (val == SparseMatrix.NULL) ? table.getObject(r, c) : val;
   }

   /**
    * Get the double value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the double value in the specified row.
    */
   @Override
   public final double getDouble(int r, int c) {
      Object val = (matrix == null) ? SparseMatrix.NULL : matrix.get(r, c);
      return (val == SparseMatrix.NULL) ? table.getDouble(r, c) : toDouble(val);
   }

   /**
    * Get the float value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the float value in the specified row.
    */
   @Override
   public final float getFloat(int r, int c) {
      Object val = (matrix == null) ? SparseMatrix.NULL : matrix.get(r, c);
      return (val == SparseMatrix.NULL) ? table.getFloat(r, c) : toFloat(val);
   }

   /**
    * Get the long value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the long value in the specified row.
    */
   @Override
   public final long getLong(int r, int c) {
      Object val = (matrix == null) ? SparseMatrix.NULL : matrix.get(r, c);
      return (val == SparseMatrix.NULL) ? table.getLong(r, c) : toLong(val);
   }

   /**
    * Get the int value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the int value in the specified row.
    */
   @Override
   public final int getInt(int r, int c) {
      Object val = (matrix == null) ? SparseMatrix.NULL : matrix.get(r, c);
      return (val == SparseMatrix.NULL) ? table.getInt(r, c) : toInt(val);
   }

   /**
    * Get the short value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the short value in the specified row.
    */
   @Override
   public final short getShort(int r, int c) {
      Object val = (matrix == null) ? SparseMatrix.NULL : matrix.get(r, c);
      return (val == SparseMatrix.NULL) ? table.getShort(r, c) : toShort(val);
   }

   /**
    * Get the byte value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the byte value in the specified row.
    */
   @Override
   public final byte getByte(int r, int c) {
      Object val = (matrix == null) ? SparseMatrix.NULL : matrix.get(r, c);
      return (val == SparseMatrix.NULL) ? table.getByte(r, c) : toByte(val);
   }

   /**
    * Get the boolean value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the boolean value in the specified row.
    */
   @Override
   public final boolean getBoolean(int r, int c) {
      Object val = (matrix == null) ? SparseMatrix.NULL : matrix.get(r, c);
      return (val == SparseMatrix.NULL) ? table.getBoolean(r, c) :
         toBoolean(val);
   }

   /**
    * Set the cell value.
    * @param r row number.
    * @param c column number.
    * @param v cell value.
    */
   @Override
   public void setObject(int r, int c, Object v) {
      if(matrix == null) {
         matrix = new SparseMatrix();
      }

      if(r == 0) {
         hmodified = true;
      }

      matrix.set(r, c, v);
      fireChangeEvent();
   }

   /**
    * Make a clone of the table with shared data. Each cloned copy does not
    * shared data modified through setObject().
    */
   public XNodeTableLens cloneShared() {
      XNodeTableLens obj = new XNodeTableLens();

      obj.delegate = delegate;
      obj.table = table;
      obj.init();
      // don't copy the matrix which is set through setObject()

      // increment ref count, used by XNodeTable to see if cancel is ok
      delegate.setShared();

      if(maxRowHintMap != null) {
         obj.maxRowHintMap = (HashMap) maxRowHintMap.clone();
      }

      return obj;
   }

   /**
    * Initialize column identifiers.
    */
   private void init() {
      // set identifiers to prevent setObject to cause a column to be ignored
      if(table != null && table.moreRows(0)) {
         for(int i = 0; i < table.getColCount(); i++) {
            Object hdr = table.getObject(0, i);

            if(hdr != null) {
               setColumnIdentifier(i, hdr.toString());
            }
         }
      }
   }

   /**
    * Get internal table data descriptor which contains table structural
    * infos.
    * @return table data descriptor.
    */
   @Override
   public TableDataDescriptor getDescriptor() {
      if(descriptor == null) {
         descriptor = new TableDataDescriptor2(this);
      }

      return descriptor;
   }

   /**
    * Table data descriptor.
    */
   private final class TableDataDescriptor2 extends DefaultTableDataDescriptor {
      public TableDataDescriptor2(XTable table) {
         super(table);

         this.desc = XNodeTableLens.this.table.getDescriptor();
      }

      /**
       * Get meta info of a specified table data path.
       * @param path the specified table data path.
       * @return meta info of the table data path.
       */
      @Override
      public final XMetaInfo getXMetaInfo(TableDataPath path) {
         if(hmodified) {
            if(!path.isCell()) {
               return null;
            }

            String header = path.getPath()[0];

            if(columnIndexMap == null) {
               columnIndexMap = new ColumnIndexMap(XNodeTableLens.this, true);
            }

            int idx = Util.findColumn(columnIndexMap, header, false);

            if(idx < 0) {
               return null;
            }

            header = Util.getHeader(XNodeTableLens.this.table, idx).toString();
            path = new TableDataPath(path.getLevel(), path.getType(),
               path.getDataType(), new String[] {header});
         }

         return desc.getXMetaInfo(path);
      }

      @Override
      public List<TableDataPath> getXMetaInfoPaths() {
         return desc.getXMetaInfoPaths();
      }

      /**
       * Check if contains format.
       * @return true if contains format.
       */
      @Override
      public final boolean containsFormat() {
         return desc.containsFormat();
      }

      /**
       * Check if contains drill.
       * @return true if contains drill.
       */
      @Override
      public final boolean containsDrill() {
         return desc.containsDrill();
      }

      /**
       * Get the column header for data path.
       */
      @Override
      protected Object getHeader(int col) {
         return Util.getHeader(table, col);
      }

      private TableDataDescriptor desc;
      private transient ColumnIndexMap columnIndexMap = null;
   }

   /**
    * Get the applied max rows.
    * @return the applied max rows.
    */
   public int getAppliedMaxRows() {
      return (table instanceof XSwappableTable) ?
         ((XSwappableTable) table).getAppliedMaxRows() : 0;
   }

   /**
    * Check if a table is a result of timeout.
    */
   public boolean isTimeoutTable() {
      return (table instanceof XSwappableTable) ?
         ((XSwappableTable) table).isTimeoutTable() :
         false;
   }

   /**
    * Get the double value.
    */
   private static final double toDouble(Object val) {
      if(val instanceof Number) {
         return ((Number) val).doubleValue();
      }

      return 0D;
   }

   /**
    * Get the float value.
    */
   private static final float toFloat(Object val) {
      if(val instanceof Number) {
         return ((Number) val).floatValue();
      }

      return 0F;
   }

   /**
    * Get the long value.
    */
   private static final long toLong(Object val) {
      if(val instanceof Number) {
         return ((Number) val).longValue();
      }

      return 0L;
   }

   /**
    * Get the int value.
    */
   private static final int toInt(Object val) {
      if(val instanceof Number) {
         return ((Number) val).intValue();
      }

      return 0;
   }

   /**
    * Get the short value.
    */
   private static final short toShort(Object val) {
      if(val instanceof Number) {
         return ((Number) val).shortValue();
      }

      return 0;
   }

   /**
    * Get the byte value.
    */
   private static final byte toByte(Object val) {
      if(val instanceof Number) {
         return ((Number) val).byteValue();
      }

      return 0;
   }

   /**
    * Get the boolean value.
    */
   private static final boolean toBoolean(Object val) {
      if(val instanceof Boolean) {
         return ((Boolean) val).booleanValue();
      }

      return false;
   }

   /**
    * Get swappable table.
    */
   public XSwappableTable getSwappableTable() {
      return (table instanceof XSwappableTable) ? (XSwappableTable) table: null;
   }

   @Override
   public long dataId() {
      DFWrapper wrapper = getDFWrapper();
      return (wrapper != null) ? wrapper.dataId() : 0;
   }

   /**
    * RDD delegate methods.
    */
   @Override
   public Object getDF() {
      DFWrapper wrapper = getDFWrapper();
      return (wrapper != null) ? wrapper.getDF() : null;
   }

   /**
    * RDD delegate methods.
    */
   @Override
   public Object getRDD() {
      DFWrapper wrapper = getDFWrapper();
      return (wrapper != null) ? wrapper.getRDD() : null;
   }

   /**
    * RDD delegate methods.
    */
   @Override
   public DFWrapper getBaseDFWrapper() {
      return getDFWrapper();
   }

   /**
    * RDD delegate methods.
    */
   @Override
   public String[] getHeaders() {
      DFWrapper wrapper = getDFWrapper();
      return (wrapper != null) ? wrapper.getHeaders() : null;
   }

   /**
    * RDD delegate methods.
    */
   @Override
   public void setXMetaInfos(XSwappableTable lens) {
      DFWrapper wrapper = getDFWrapper();

      if(wrapper != null) {
         wrapper.setXMetaInfos(lens);
      }
   }

   /**
    * RDD delegate methods.
    */
   @Override
   public void completed() {
      DFWrapper wrapper = getDFWrapper();

      if(wrapper != null) {
         wrapper.completed();
      }
   }

   @Override
   public void setColumnIdentifier(int col, String identifier) {
      super.setColumnIdentifier(col, identifier);
      DFWrapper df = getDFWrapper();

      if(df instanceof AbstractTableLens) {
         ((AbstractTableLens) df).setColumnIdentifier(col, identifier);
      }
   }

   // get the DFWrapper nested in this XNode
   private DFWrapper getDFWrapper() {
      XNode root = delegate.getNode();

      if(root instanceof XTableTableNode) {
         XTable tbl = ((XTableTableNode) root).getXTable();

         if(tbl instanceof DFWrapper) {
            return (DFWrapper) tbl;
         }
      }

      return null;
   }

   /**
    * @return the report/vs name which this filter was created for,
    * and will be used when insert audit record.
    */
   @Override
   public String getReportName() {
      String name = super.getReportName();
      return name != null ? name : table == null ? null : table.getReportName();
   }

   /**
    * @return the report type which this filter was created for:
    * ExecutionBreakDownRecord.OBJECT_TYPE_REPORT or
    * ExecutionBreakDownRecord.OBJECT_TYPE_VIEWSHEET
    */
   @Override
   public String getReportType() {
      String type = super.getReportType();
      return type != null ? type : table == null ? null : table.getReportType();
   }

   @Override
   public boolean isDynamicColumns() {
      return delegate.getNode() != null && "true".equals(delegate.getNode().getAttribute("dynamicColumns"));
   }

   public String toString() {
      return super.toString() + "[" + table + "]";
   }

   /**
    * @return if the running jdbc query has aggregate and applied max row limit,
    * then we cannot compare the table row count with max row limit directly,
    * because the max row limit is applied for the aggregate based data,
    *
    * and we cannot know if the table applied max row limit (the real row count in db
    * may not larger than the max row limit). so just remember this limit here, and
    * use this to prompt a proper message for the target assembly in VSEventUtil.addWarningText().
    */
   public HashMap getMaxRowHintMap() {
      return maxRowHintMap;
   }

   public void setMaxRowHintMap(HashMap map) {
      this.maxRowHintMap = map;
   }

   protected XTable table = null;
   private XNodeTable delegate = null;
   private SparseMatrix matrix = null;
   private boolean hmodified = false;
   private HashMap maxRowHintMap;

   private transient TableDataDescriptor descriptor;
   private static final Logger LOG = LoggerFactory.getLogger(XNodeTableLens.class);
}
