/*
 * inetsoft-onedrive - StyleBI is a business intelligence web application.
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
package inetsoft.uql.onedrive;

import com.microsoft.graph.authentication.IAuthenticationProvider;
import com.microsoft.graph.requests.*;
import inetsoft.uql.*;
import inetsoft.uql.schema.*;
import inetsoft.uql.table.*;
import inetsoft.uql.tabular.*;
import inetsoft.uql.tabular.ColumnDefinition;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;
import java.util.*;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

public class OneDriveRuntime extends TabularRuntime {
   @Override
   public XTableNode runQuery(TabularQuery query, VariableTable params) {
      OneDriveQuery oneDriveQuery = (OneDriveQuery) query;

      if(oneDriveQuery.getTempFile() == null) {
         oneDriveQuery.loadFile();
      }

      File dataFile = oneDriveQuery.getTempFile();
      String[] originalHeaders = null;

      if(oneDriveQuery.isUnpivotData()) {
         XTypeNode header = null;
         File file = dataFile;

         if(file != null) {
            if(OneDriveFileUtil.isExcel(oneDriveQuery.getPath())) {
               String sheet =
                  oneDriveQuery.getExcelSheet() == null ? "Sheet1" : oneDriveQuery.getExcelSheet();
               header = OneDriveFileUtil.getExcelHeader(file, oneDriveQuery.getPath(), oneDriveQuery.getEncoding(),
                                                      sheet, oneDriveQuery.isFirstRowHeader());
            }
            else {
               String delimiter;

               if(oneDriveQuery.isTab()) {
                  delimiter = "\t";
               }
               else {
                  delimiter = oneDriveQuery.getDelimiter();
               }

               header = OneDriveFileUtil.getTextHeader(file, oneDriveQuery.getPath(), oneDriveQuery.getEncoding(),
                                                     delimiter, oneDriveQuery.isRemoveQuotation(), oneDriveQuery.isFirstRowHeader());
            }

            originalHeaders = new String[header.getChildCount()];

            for(int i = 0; i < header.getChildCount(); i++) {
               originalHeaders[i] = header.getChild(i).getName();
            }
         }
      }

      return new OneDriveFileTableNode(oneDriveQuery, originalHeaders);
   }

   @Override
   public void testDataSource(TabularDataSource dataSource, VariableTable params) {
      OneDriveDataSource ds = (OneDriveDataSource) dataSource;

      withClassLoader(() -> {
         GraphServiceClient client = getClient(ds);
         client.me().drive().buildRequest().get();
         return null;
      });
   }

   public static InputStream getFile(OneDriveQuery query) {
      String path = query.getPath();
      OneDriveDataSource ds = (OneDriveDataSource) query.getDataSource();
      GraphServiceClient client = getClient(ds);
      return client.me().drive().root().itemWithPath(path).content().buildRequest().get();
   }

   public static String getFileURL(OneDriveQuery query) {
      String path = query.getPath();
      OneDriveDataSource ds = (OneDriveDataSource) query.getDataSource();
      GraphServiceClient client = getClient(ds);
      return client.me().drive().root().itemWithPath(path).content().buildRequest().getRequestUrl().toString();
   }

   private static GraphServiceClient getClient(OneDriveDataSource dataSource) {
      return GraphServiceClient
         .builder()
         .authenticationProvider(getAuthentication(dataSource))
         .buildClient();
   }

   private static IAuthenticationProvider getAuthentication(OneDriveDataSource dataSource) {
      return new OneDriveAuthenticator(dataSource);
   }

   private static <T> T withClassLoader(Supplier<T> fn) {
      ClassLoader loader = Thread.currentThread().getContextClassLoader();
      Thread.currentThread().setContextClassLoader(OneDriveRuntime.class.getClassLoader());

      try {
         return fn.get();
      }
      finally {
         Thread.currentThread().setContextClassLoader(loader);
      }
   }

   private final class OneDriveFileTableNode extends XTableNode {
      public OneDriveFileTableNode(OneDriveQuery oneDriveQuery,
                                 String[] originalHeaders)
      {
         this.oneDriveQuery = oneDriveQuery;
         this.columnDefAll = oneDriveQuery.getColumns();
         this.columnDefSelected = new ArrayList<>();

         for(int i = 0; i < columnDefAll.length; i++) {
            if(columnDefAll[i].isSelected()) {
               columnDefSelected.add(columnDefAll[i]);
            }
         }

         this.columns = columnDefSelected.size();
         this.isUnpivot = oneDriveQuery.isUnpivotData();
         this.fileIndex = 0;
         this.table = new XSwappableTable();
         //headers columns cant be greater than column count - 1
         this.headers = new String[columns];
         this.firstRowHeader = oneDriveQuery.isFirstRowHeader();
         this.originalHeaders = originalHeaders;

         if(isUnpivot) {
            this.headerColumnCount = Math.max(1, Math.min(oneDriveQuery.getHeaderColumnCount(),
                                                          originalHeaders.length - 1));
         }

         this.creators = new XTableColumnCreator[columns];

         for(int i = 0; i < creators.length; i++) {
            creators[i] = XObjectColumn.getCreator(columnDefSelected.get(i).getType().type());
            creators[i].setDynamic(false);
         }

         this.table.init(creators);

         //set column headers for any blank values
         for(int i = 0; i < columns; i++) {
            String cdAlias = columnDefSelected.get(i).getAlias();
            this.headers[i] = cdAlias == null || cdAlias.isEmpty() ?
               columnDefSelected.get(i).getName() : cdAlias;

            if(this.headers[i] == null) {
               this.headers[i] = "";
            }
         }

         this.table.addRow(this.headers);
      }

      /**
       * Disposes of the resources held by this node.
       *
       * @param disposeTable <tt>true</tt> to dispose of the data table.
       */
      private void dispose(boolean disposeTable) {
         lock.lock();

         try {

            if(!table.isCompleted()) {
               table.complete();
            }

            if(disposeTable) {
               if(!table.isDisposed()) {
                  table.dispose();
               }
            }
         }
         finally {
            lock.unlock();
         }
      }

      /**
       * Get a column creator.
       */
      @Override
      public XTableColumnCreator getColumnCreator(int col) {
         return creators[col];
      }

      /**
       * Get the column creators.
       */
      @Override
      public XTableColumnCreator[] getColumnCreators() {
         return creators;
      }

      /**
       * {@inheritDoc}
       */
      @Override
      public void cancel() {
         cancelled = true;
         dispose(true);
         super.cancel();
      }

      /**
       * {@inheritDoc}
       */
      @Override
      public void close() {
         dispose(true);
         super.close();
      }

      /**
       * Get a column count.
       */
      @Override
      public int getColCount() {
         return columns;
      }

      /**
       * Get a column name.
       */
      @Override
      public String getName(int col) {
         if(table.getObject(0, col) instanceof Double) {
            return table.getObject(0, col).toString();
         }

         return (String) table.getObject(0, col);
      }

      /**
       * Get a column value.
       */
      @Override
      public Object getObject(int col) {
         lock.lock();

         try {
            if(currentRow < 0) {
               throw new IllegalStateException(
                  "Before the beginning of the table");
            }

            if(currentRow >= getRowCount()) {
               throw new IllegalStateException("Past the end of the table");
            }

            return table.getObject(currentRow, col);
         }
         finally {
            lock.unlock();
         }
      }

      /**
       * Get a column type.
       */
      @Override
      public Class<?> getType(int col) {
         if(this.columnDefSelected.get(col) != null) {
            DataType dt = this.columnDefSelected.get(col).getType();
            return Tool.getDataClass(dt.type());
         }

         return null;
      }

      /**
       * Get a column xmetainfo.
       */
      @Override
      public XMetaInfo getXMetaInfo(int col) {
         return null;
      }

      /**
       * Check the node is rewindable.
       */
      @Override
      public boolean isRewindable() {
         return true;
      }

      /**
       * Find next cursor.
       */
      @Override
      public boolean next() {
         lock.lock();

         try {
            if(++currentRow < getRowCount()) {
               return true;
            }
            else {
               boolean rowAdded = false;

               while(!table.isCompleted() && !fileRead && !rowAdded && !cancelled) {
                  try {
                     if(oneDriveQuery.getTempFile() == null) {
                        oneDriveQuery.getTempFile();
                     }

                     File file = oneDriveQuery.getTempFile();
                     XTableNode node;

                     if(OneDriveFileUtil.isExcel(oneDriveQuery.getPath())) {
                        //Excel file
                        node = OneDriveFileUtil.readExcel(
                           file, oneDriveQuery, this.columnDefAll);
                     }
                     else {
                        //text file
                        node = OneDriveFileUtil.readText(
                           file, this.oneDriveQuery, this.columnDefAll);
                     }

                     fileRead = true;
                     rowAdded = nextBatch(node, this.isUnpivot);
                  }
                  catch(Throwable exc) {
                     LOG.error("Failed to execute query batch", exc);
                     dispose(false);
                  }
               }

               return rowAdded;
            }
         }
         finally {
            lock.unlock();
         }
      }

      /**
       * Rewind the cursor.
       */
      @Override
      public boolean rewind() {
         lock.lock();

         try {
            currentRow = 0;
            return true;
         }
         finally {
            lock.unlock();
         }
      }

      /**
       * Gets the actual number of rows currently in the table.
       *
       * @return the number of rows.
       */
      private int getRowCount() {
         int nrows = table.getRowCount();

         if(nrows < 0) {
            nrows = (-1 * nrows) - 1;
         }

         return nrows;
      }

      /**
       * Gets the next batch of query results.
       *
       * @return <tt>true</tt> if more results remain.
       * @throws Exception the query failed.
       */
      private boolean nextBatch(XTableNode node, boolean unpivot) throws Exception {
         if(node == null) {
            return false;
         }

         int ncol = node.getColCount();

         if(!unpivot) {
            int maxRows = oneDriveQuery.getMaxRows();
            int cnt = 0;
            while(node.next() && (maxRows <= 0 || getRowCount() <= maxRows) && !cancelled) {
               Object[] row = new Object[ncol];
               cnt++;

               for(int i = 0; i < ncol; i++) {
                  row[i] = node.getObject(i);
               }

               Object[] actualRow = new Object[this.columns];
               int j = 0;

               for(int i = 0; i < columnDefAll.length && i < row.length; i++) {
                  if(columnDefAll[i].isSelected()) {
                     actualRow[j] = Tool
                        .getData(columnDefAll[i].getType().type(), row[i]);
                     j++;
                  }
               }

               this.table.addRow(actualRow);
            }
         }
         else {
            while(node.next() &&
               (oneDriveQuery.getMaxRows() <= 0 || getRowCount() <= oneDriveQuery.getMaxRows()) && !cancelled)
            {
               Object[] row = new Object[ncol];

               for(int i = 0; i < ncol; i++) {
                  row[i] = node.getObject(i);
               }

               for(int c = headerColumnCount; c < row.length; c++) {
                  Object[] unpivotedRow = new Object[headerColumnCount + 2];
                  System.arraycopy(row, 0, unpivotedRow, 0, headerColumnCount);
                  unpivotedRow[headerColumnCount] =
                     c < originalHeaders.length ? originalHeaders[c] : null;
                  unpivotedRow[headerColumnCount + 1] = row[c];

                  Object[] unpivotedRowSelected = new Object[columnDefSelected.size()];
                  int index = 0;

                  for(int j = 0; j < columnDefAll.length && j < unpivotedRow.length; j++) {
                     ColumnDefinition column = columnDefAll[j];

                     if(column.isSelected()) {
                        unpivotedRowSelected[index] = Tool.getData(
                           column.getType().type(), unpivotedRow[j]);
                        index++;
                     }
                  }

                  this.table.addRow(unpivotedRowSelected);

                  if(oneDriveQuery.getMaxRows() > 0 && getRowCount() > oneDriveQuery.getMaxRows()) {
                     break;
                  }
               }
            }
         }

         return currentRow < getRowCount();
      }

      private OneDriveQuery oneDriveQuery;
      private XSwappableTable table;
      private final XTableColumnCreator[] creators;
      private int currentRow = 0;
      private final Lock lock = new ReentrantLock();

      private final int columns;
      private final boolean firstRowHeader;
      private final boolean isUnpivot;
      private int headerColumnCount;
      private int fileIndex;
      private boolean fileRead = false;
      private String[] headers;
      private String[] originalHeaders;
      private ColumnDefinition[] columnDefAll;
      private List<ColumnDefinition> columnDefSelected;
      private boolean cancelled;
   }

   private static final Logger LOG = LoggerFactory.getLogger(OneDriveRuntime.class);
}
