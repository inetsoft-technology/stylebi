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
package inetsoft.uql.util;

import inetsoft.report.TableDataDescriptor;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.*;
import inetsoft.uql.table.*;
import inetsoft.util.ExecutionMap;
import inetsoft.util.ThreadPool;
import inetsoft.web.admin.monitoring.MonitorLevelService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Table used to convert a data tree to a table. If the data tree is
 * already a table, the table is used directly, else if the data tree
 * is a sequence, each item in the sequence is transformed into a row,
 * otherwise the tree is converted into a one row table, with the columns
 * from each branch of the tree.
 *
 * @version 6.1
 * @author InetSoft Technology Corp
 */
public class XNodeTable implements XTable {
   /**
    * Create an empty XNodeTable.
    */
   public XNodeTable() {
      super();
      identifiers = new XIdentifierContainer(this);
   }

   /**
    * Create a table from a data tree.
    */
   public XNodeTable(XNode root) {
      this();

      setNode(root);
   }

   /**
    * Set the data tree used to create a table.
    */
   public void setNode(XNode root) {
      cancelled = false;
      this.root = root;

      if(root instanceof CompositeTableNode) {
         this.delegate = ((CompositeTableNode) root).getTable();
      }
      else if(root instanceof XTableTableNode) {
         this.delegate = ((XTableTableNode) root).getXTable();
      }
      else {
         this.delegate = new XSwappableTable2(root);
      }
   }

   /**
    * Get the data tree.
    */
   public XNode getNode() {
      return root;
   }

   /**
    * Cancel the table and running queries if supported.
    */
   public void cancel() {
      // @by larryl, if this table is shared by more than one user, don't
      // cancel the processing
      if(refCnt > 1) {
         return;
      }

      // only mark as cancelled if not completed.
      cancelled = delegate.getRowCount() >= 0;

      if(root instanceof XTableNode) {
         ((XTableNode) root).cancel();
      }

      // @by larryl, if the table is cancelled after the table is completed,
      // the cancel is really a no-op. Since the data caching does not allow
      // cancelled table to be shared, we check here to make sure the
      // cancelled flag is really meaningful
      if(delegate instanceof XSwappableTable) {
         cancelled = !((XSwappableTable) delegate).isCompleted();
         ((XSwappableTable) delegate).complete();
      }
   }

   /**
    * Check the table to see if it is cancelled.
    */
   public synchronized boolean isCancelled() {
      return cancelled;
   }

   /**
    * Make sure the temp files are removed.
    */
   @Override
   public void finalize() {
      dispose();
   }

   /**
    * Dispose the node table.
    */
   @Override
   public synchronized void dispose() {
      if(disposed) {
         return;
      }

      disposed = true;

      if(delegate instanceof XSwappableTable) {
         delegate.dispose();
      }
   }

   /**
    * Get the contained table.
    *
    * @return the contained table
    */
   public XTable getTable() {
      return delegate;
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
      return delegate.moreRows(row);
   }

   /**
    * Return the number of rows in the table. The number of rows includes
    * the header rows. If the table is loading in background and loading
    * is not done, return the negative number of loaded rows minus 1.
    * @return number of rows in table.
    */
   @Override
   public int getRowCount() {
      return delegate.getRowCount();
   }

   /**
    * Return the number of columns in the table. The number of columns
    * includes the header columns.
    * @return number of columns in table.
    */
   @Override
   public int getColCount() {
      return delegate.getColCount();
   }

   /**
    * Return the number of rows on the top of the table to be treated
    * as header rows.
    * @return number of header rows.  Default is 1.
    */
   @Override
   public int getHeaderRowCount() {
      return delegate.getHeaderRowCount();
   }

   /**
    * Return the number of columns on the left of the table to be
    * treated as header columns.
    */
   @Override
   public int getHeaderColCount() {
      return delegate.getHeaderColCount();
   }

   /**
    * Return the number of rows on the bottom of the table to be treated
    * as trailer rows.
    * @return number of header rows.
    */
   @Override
   public int getTrailerRowCount() {
      return delegate.getTrailerRowCount();
   }

   /**
    * Return the number of columns on the right of the table to be
    * treated as trailer columns.
    */
   @Override
   public int getTrailerColCount() {
      return delegate.getTrailerColCount();
   }

   /**
    * Check if is primitive.
    * @return <tt>true</tt> if is primitive, <tt>false</tt> otherwise.
    */
   @Override
   public final boolean isPrimitive(int col) {
      return delegate.isPrimitive(col);
   }

   /**
    * Check if the value at one cell is null.
    * @param r the specified row index.
    * @param c column number.
    * @return <tt>true</tt> if null, <tt>false</tt> otherwise.
    */
   @Override
   public final boolean isNull(int r, int c) {
      return delegate.isNull(r, c);
   }

   /**
    * Return the value at the specified cell.
    * @param r row number.
    * @param c column number.
    * @return the value at the location.
    */
   @Override
   public Object getObject(int r, int c) {
      return delegate.getObject(r, c);
   }

   /**
    * Get the double value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the double value in the specified row.
    */
   @Override
   public final double getDouble(int r, int c) {
      return delegate.getDouble(r, c);
   }

   /**
    * Get the float value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the float value in the specified row.
    */
   @Override
   public final float getFloat(int r, int c) {
      return delegate.getFloat(r, c);
   }

   /**
    * Get the long value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the long value in the specified row.
    */
   @Override
   public final long getLong(int r, int c) {
      return delegate.getLong(r, c);
   }

   /**
    * Get the int value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the int value in the specified row.
    */
   @Override
   public final int getInt(int r, int c) {
      return delegate.getInt(r, c);
   }

   /**
    * Get the short value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the short value in the specified row.
    */
   @Override
   public final short getShort(int r, int c) {
      return delegate.getShort(r, c);
   }

   /**
    * Get the byte value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the byte value in the specified row.
    */
   @Override
   public final byte getByte(int r, int c) {
      return delegate.getByte(r, c);
   }

   /**
    * Get the boolean value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the boolean value in the specified row.
    */
   @Override
   public final boolean getBoolean(int r, int c) {
      return delegate.getBoolean(r, c);
   }

   /**
    * Set the cell value. For table filters, the setObject() call should
    * be forwarded to the base table if possible. An implementation should
    * throw a runtime exception if this method is not supported. In that
    * case, data in a table can not be modified in scripts.
    * @param r row number.
    * @param c column number.
    * @param v cell value.
    */
   @Override
   public void setObject(int r, int c, Object v) {
      throw new RuntimeException("Not implemented method: setObject");
   }

   /**
    * Get the current column content type.
    * @param col column number.
    * @return column type.
    */
   @Override
   public Class<?> getColType(int col){
      return delegate.getColType(col);
   }

   /**
    * Mark this table as been shared.
    */
   public void setShared() {
      refCnt++;
   }

   /**
    * Get the column identifier of a column.
    * @param col the specified column index.
    * @return the column indentifier of the column. The identifier might be
    * different from the column name, for it may contain more locating
    * information than the column name.
    */
   @Override
   public String getColumnIdentifier(int col) {
      return identifiers.getColumnIdentifier(col);
   }

   /**
    * Set the column identifier of a column.
    * @param col the specified column index.
    * @param identifier the column indentifier of the column. The identifier
    * might be different from the column name, for it may contain more
    * locating information than the column name.
    */
   @Override
   public void setColumnIdentifier(int col, String identifier) {
      identifiers.setColumnIdentifier(col, identifier);
   }

   /**
    * Get internal table data descriptor which contains table structural
    * infos.
    * @return table data descriptor.
    */
   @Override
   public TableDataDescriptor getDescriptor() {
      return delegate.getDescriptor();
   }

   /**
    * Clone the object.
    * @return the cloned object;
    */
   @Override
   public Object clone() {
      try {
         XNodeTable table = (XNodeTable) super.clone();
         table.identifiers = (XIdentifierContainer) identifiers.clone();
         table.identifiers.setTable(table);
         return table;
      }
      catch(Exception ex) {
         return null;
      }
   }

   final class XSwappableTable2 extends XSwappableTable implements RowCounter {
      public XSwappableTable2(XNode root) {
         super();

         String prop = SreeEnv.getProperty("replet.streaming");
         boolean bgproc = prop.equalsIgnoreCase("true");

         try {
            level = MonitorLevelService.getMonitorLevel();
         }
         catch(Exception ex) {
            // should not be happen
         }

         if(root instanceof XTableNode) {
            final XTableNode table = (XTableNode) root;
            ncol = table.getColCount();
            types = new Class[ncol];
            querytimeout = table.isTimeoutTable();

            for(int i = 0; i < types.length; i++) {
               types[i] = table.getType(i);

               if(types[i] == null) {
                  types[i] = String.class;
               }
            }

            this.tableNode = table;
            init(table.getColumnCreators());

            if(bgproc) {
               ThreadPool.addOnDemand(() -> load(table));
            }
            else {
               load(table);
            }
         }
         else {
            if(!(root instanceof XSequenceNode)) {
               XSequenceNode seq = new XSequenceNode();

               seq.addChild(root);
               root = seq;
            }

            // root is a sequence node now
            if(bgproc) {
               final XNode root2 = root;
               ThreadPool.addOnDemand(() -> load(root2));
            }
            else {
               load(root);
            }
         }
      }

      /**
       * Check if there are more rows.
       */
      @Override
      public boolean moreRows(int row) {
         if(cancelled) {
            return row < Math.abs(this.getRowCount());
         }
         else {
            if(loadDataException != null) {
               try {
                  throw new RuntimeException(loadDataException.getMessage(), loadDataException);
               }
               finally {
                  loadDataException = null;
               }
            }

            return super.moreRows(row);
         }
      }

      /**
       * Get the applied max rows.
       * @return the applied max rows.
       */
      @Override
      public int getAppliedMaxRows() {
         return amax;
      }

      /**
       * Check if a table is a result of timeout.
       */
      @Override
      public boolean isTimeoutTable() {
         return querytimeout;
      }

      /**
       * Get the current column content type.
       * @param col column number.
       * @return column type.
       */
      @Override
      public Class<?> getColType(int col) {
         return types[col];
      }

      @Override
      public void complete() {
         super.complete();
         removeQueryInfo(queryId);
      }

      /**
       * Load a table node into table.
       */
      public void load(XTableNode table) {
         try {
            queryId = addQueryInfo();
            load0(table);
         }
         catch(Exception ex) {
            cancelled = true;
            LOG.debug("Failed to load XTableNode", ex);
         }
      }

      /**
       * Load table.
       */
      private void load0(XTableNode table) {
         int count = table.getColCount();
         Object[] row = new Object[count];

         LOG.debug("Start loading query result");

         for(int i = 0; i < row.length; i++) {
            row[i] = table.getName(i);
         }

         try {
            rlock.lock();
            addRow(row);
         }
         finally {
            rlock.unlock();
         }

         // @by stephenwebster, For Bug #10385
         // If load data is not triggered, a report with tables exceeding the
         // maximum connection pool size will cause the server to run out of
         // connections.
         loadData();
      }

      private void loadData() {
         if(dataLoaded) {
            return;
         }

         dataLoaded = true;
         String prop = SreeEnv.getProperty("replet.streaming");
         boolean bgproc = prop.equalsIgnoreCase("true");

         if(bgproc) {
            ThreadPool.addOnDemand(() -> {
               try {
                  loadTable0();
               }
               catch(ClassCastException ex) {
                  loadDataException = ex;
               }
               catch(Exception ex) {
                  cancelled = true;
                  loadDataException = ex;
                  LOG.debug("Failed to load XTableNode", ex);
               }
               finally {
                  complete();
               }
            });
         }
         else {
            try {
               loadTable0();
            }
            finally {
               complete();
            }
         }
      }

      // Load from table node
      private void loadTable0() {
         try {
            int result = 0;
            XTableNode table = this.tableNode;
            int count = table.getColCount();
            Object[] row = new Object[count];

            for(int i = 0; i < count; i++) {
               setXMetaInfo(table.getName(i), table.getXMetaInfo(i));
            }

            while(!cancelled && table.next()) {
               for(int i = 0; i < count; i++) {
                  Object obj = table.getObject(i);
                  row[i] = obj;
               }

               addRow(row);

               if(level > 1 && (result = super.count % 100) == 0) {
                  countExecutedRows(100);
               }
            }

            amax = table.getAppliedMaxRows();

            table.close();

            if(level > 1 && result != 0) {
               countExecutedRows(result);
            }
         }
         finally {
            complete();
            LOG.debug("Query data finished loading: {}", getRowCount());
         }
      }

      /**
       * Load a table node into table.
       */
      public void load(XNode table) {
         try {
            queryId = addQueryInfo();
            load0(table);
         }
         catch(Exception ex) {
            cancelled = true;
            LOG.debug("Failed to load XNode data", ex);
         }
         finally {
            complete();
         }
      }

      /**
       * Load a tree into table.
       */
      private void load0(XNode root) {
         Object[] header = null;
         int result = 0;
         dataLoaded = true;

         for(int i = 0; i < root.getChildCount() && !cancelled; i++) {
            XNode child = root.getChild(i);

            if(header == null) {
               ncol = child.getChildCount();
               XTableColumnCreator[] creators = new XTableColumnCreator[ncol];

               for(int j = 0; j < creators.length; j++) {
                  creators[j] = XObjectColumn.getCreator();
               }

               init(creators);

               header = new Object[ncol];

               for(int j = 0; j < header.length; j++) {
                  header[j] = child.getChild(j).getName();
               }

               addRow(header);

               // get type info
               types = new Class[ncol];

               for(int k = 0; k < types.length; k++) {
                  types[k] = String.class;
               }
            }

            Object[] row = new Object[ncol];

            for(int j = 0; j < row.length && j < child.getChildCount(); j++) {
               XNode col = child.getChild(j);

               if((col instanceof XSequenceNode) && col.getChildCount() > 0) {
                  col = col.getChild(0);
               }

               row[j] = col.getValue();
            }

            addRow(row);

            if(level > 1 && (result = super.count % 100) == 0) {
               countExecutedRows(100);
            }
         }

         if(root != null) {
            root.removeAllChildren();
         }

         if(root instanceof XSequenceNode) {
            amax = ((XSequenceNode) root).getAppliedMaxRows();
         }

         if(level > 1 && result != 0) {
            countExecutedRows(result);
         }
      }

      /**
       * Copy the query info from the static query map in session manager into
       * the current query map.
       */
      private String addQueryInfo() {
         if(level <= 0) {
            return null;
         }

         String queryId = root == null ? null :
            (String) root.getAttribute("queryId");

         if(queryId == null) {
            return null;
         }

         QueryInfo info = XUtil.queryMap.get(queryId);

         if(info != null) {
            QueryInfo info2 = (QueryInfo) info.clone();
            info2.setThreadId("Thread" + Thread.currentThread().getId());
            info2.setQueryManager(info.getQueryManager());

            // if count row
            if(level > 1) {
               info2.setRowCounter(this);
            }

            queryMap.put(queryId, info2);
            emap.addObject(queryId);
            fireQueryExecutionEvent(queryId, true);
         }

         return queryId;
      }

      /**
       * Remove the query info from the current static query map.
       */
      private void removeQueryInfo(String queryId) {
         QueryInfo info = XNodeTable.removeQueryInfo(queryId);

         if(info != null) {
            info.setRowCounter(null);
         }
      }

      private int ncol;
      private int amax;
      private int level; // monitor level
      private boolean querytimeout = false;
      private boolean dataLoaded = false;
      private XTableNode tableNode;
   }

   private void writeObject(ObjectOutputStream stream) throws IOException {
      stream.defaultWriteObject();
   }

   /**
    * Count the executed rows.
    */
   private synchronized void countExecutedRows(int count) {
      executedRows += count;
   }

   /**
    * Reset the number of rows to zero.
    */
   public static synchronized void resetExecutedRows() {
      executedRows = 0;
   }

   /**
    * Gets the number of executed rows.
    */
   public static synchronized int getExecutedRows() {
      return executedRows;
   }

   /**
    * Get the ids of all executing queries during a sample period.
    */
   public static List getExecutingQueries() {
      return emap.getObjects();
   }

   /**
    * Remove the query info from the current static query map. Mark the query
    * as a completed query.
    */
   public static QueryInfo removeQueryInfo(String queryId) {
      QueryInfo info = null;

      if(queryId != null) {
         info = queryMap.remove(queryId);
         emap.setCompleted(queryId);
         fireQueryExecutionEvent(queryId, false);
      }

      return info;
   }

   /**
    * Get the value of a property.
    * @param key the specified property name.
    * @return the value of the property.
    */
   @Override
   public Object getProperty(String key) {
      return prop.get(key);
   }

   /**
    * Set the value a property.
    * @param key the property name.
    * @param value the property value, null to remove the property.
    */
   @Override
   public void setProperty(String key, Object value) {
      if(value == null) {
         prop.remove(key);
      }
      else {
         prop.put(key, value);
      }
   }

   /**
    * @return the report/vs name which this filter was created for,
    * and will be used when insert audit record.
    */
   @Override
   public String getReportName() {
      Object name = getProperty(XTable.REPORT_NAME);
      return name == null ? null : name + "";
   }

   /**
    * @return the report type which this filter was created for:
    * ExecutionBreakDownRecord.OBJECT_TYPE_REPORT or
    * ExecutionBreakDownRecord.OBJECT_TYPE_VIEWSHEET
    */
   @Override
   public String getReportType() {
      Object type = getProperty(XTable.REPORT_TYPE);
      return type == null ? null : type + "";
   }

   public static void addQueryExecutionListener(QueryExecutionListener l) {
      synchronized(queryExecutionListeners) {
         queryExecutionListeners.add(l);
      }
   }

   public static void removeQueryExecutionListener(QueryExecutionListener l) {
      synchronized(queryExecutionListeners) {
         queryExecutionListeners.remove(l);
      }
   }

   private static void fireQueryExecutionEvent(String queryId, boolean started) {
      QueryExecutionEvent event = new QueryExecutionEvent(XNodeTable.class, queryId);
      List<QueryExecutionListener> queryExecutionListeners;

      synchronized(XNodeTable.queryExecutionListeners) {
         queryExecutionListeners = new ArrayList<>(XNodeTable.queryExecutionListeners);
      }

      for(QueryExecutionListener l : queryExecutionListeners) {
         if(started) {
            l.queryExecutionStarted(event);
         }
         else {
            l.queryExecutionFinished(event);
         }
      }
   }

   private static final transient ExecutionMap emap = new ExecutionMap();
   public static final transient Map<String, QueryInfo> queryMap = new ConcurrentHashMap<>();
   private static int executedRows = 0;

   private XTable delegate;
   private boolean cancelled = false;
   private Exception loadDataException = null;
   private boolean disposed = false;
   private XNode root;
   private Class<?>[] types;
   private int refCnt = 0; // reference count
   private XIdentifierContainer identifiers = null;
   private Properties prop = new Properties(); // properties
   private String queryId = null;
   private static final Set<QueryExecutionListener> queryExecutionListeners = new HashSet<>();

   private static final Logger LOG =
      LoggerFactory.getLogger(XNodeTable.class);
}
