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
package inetsoft.uql.serverfile;

import inetsoft.uql.*;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.uql.table.*;
import inetsoft.uql.tabular.*;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Runtime support for local data files.
 */
public class ServerFileRuntime extends TabularRuntime {
   public XTableNode runQuery(TabularQuery query, VariableTable params) {
      ServerFileQuery sfQuery = (ServerFileQuery) query;
      File dataFile = sfQuery.getFileFolder();

      List<File> files;
      boolean isScheduler = false;
      try{
         isScheduler = Boolean.TRUE.equals(params.get("__is_scheduler__"));
      }
      catch (Exception ex) {
         LOG.warn("Cannot check Sceduler: "+ex);
      }
      if(isScheduler && !dataFile.exists()) {
            throw new RuntimeException("Error Accessing Data File, Failing Scheduled Action");
      }

      if(dataFile.isDirectory()) {
         files = ServerFileUtil.getFileList(dataFile, null);
      }
      else {
         files = new ArrayList<>();
         files.add(dataFile);
      }

      String[] originalHeaders = null;

      if(sfQuery.isUnpivotData()) {
         XTypeNode header = null;
         File file = ServerFileUtil.getFirstFile(dataFile);

         if(file != null) {
            if(ServerFileUtil.isExcel(file.getAbsolutePath())) {
               String sheet =
                  sfQuery.getExcelSheet() == null ? "Sheet1" : sfQuery.getExcelSheet();
               header = ServerFileUtil.getExcelHeader(file, sfQuery.getEncoding(),
                  sheet, sfQuery.isFirstRowHeader());
            }
            else {
               String delimiter;

               if(sfQuery.isTab()) {
                  delimiter = "\t";
               }
               else {
                  delimiter = sfQuery.getDelimiter();
               }

               header = ServerFileUtil.getTextHeader(file, sfQuery.getEncoding(),
                  delimiter, sfQuery.isRemoveQuotation(), sfQuery.isFirstRowHeader());
            }

            originalHeaders = new String[header.getChildCount()];

            for(int i = 0; i < header.getChildCount(); i++) {
               originalHeaders[i] = header.getChild(i).getName();
            }
         }
      }

      return new ServerFileTableNode(files, sfQuery, originalHeaders);
   }

   @Override
   public void testDataSource(TabularDataSource ds, VariableTable params)
      throws Exception
   {
      ServerFileDataSource source = (ServerFileDataSource) ds;

      if(!source.getFile().exists()) {
         throw new FileNotFoundException();
      }
   }

   private final class ServerFileTableNode extends XTableNode {
      public ServerFileTableNode(List<File> files, ServerFileQuery sfQuery,
         String[] originalHeaders)
      {
         this.files = files;
         this.sfQuery = sfQuery;
         this.columnDefAll = sfQuery.getColumns();
         this.columnDefSelected = new ArrayList<>();

         for(int i = 0; i < columnDefAll.length; i++) {
            if(columnDefAll[i].isSelected()) {
               columnDefSelected.add(columnDefAll[i]);
            }
         }

         this.columns = columnDefSelected.size();
         this.isUnpivot = sfQuery.isUnpivotData();
         this.fileIndex = 0;
         this.table = new XSwappableTable();
         //headers columns cant be greater than column count - 1
         this.headers = new String[columns];
         this.firstRowHeader = sfQuery.isFirstRowHeader();
         this.originalHeaders = originalHeaders;

         if(isUnpivot) {
            this.headerColumnCount = Math.max(1, Math.min(sfQuery.getHeaderColumnCount(),
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

               while(!table.isCompleted() && fileIndex < files.size() && !rowAdded && !cancelled) {
                  try {
                     File file = files.get(fileIndex);
                     XTableNode node;

                     if(ServerFileUtil.isExcel(file.getAbsolutePath())) {
                        //Excel file
                        node = ServerFileUtil.readExcel(
                           file, sfQuery, this.columnDefAll);
                     }
                     else {
                        //text file
                        node = ServerFileUtil.readText(
                           file, this.sfQuery, this.columnDefAll);
                     }

                     fileIndex++;
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
            int maxRows = sfQuery.getMaxRows();
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
               (sfQuery.getMaxRows() <= 0 || getRowCount() <= sfQuery.getMaxRows()) && !cancelled)
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

                  if(sfQuery.getMaxRows() > 0 && getRowCount() > sfQuery.getMaxRows()) {
                     break;
                  }
               }
            }
         }

         return currentRow < getRowCount();
      }

      private ServerFileQuery sfQuery;
      private XSwappableTable table;
      private final XTableColumnCreator[] creators;
      private int currentRow = 0;
      private final Lock lock = new ReentrantLock();

      private final List<File> files;
      private final int columns;
      private final boolean firstRowHeader;
      private final boolean isUnpivot;
      private int headerColumnCount;
      private int fileIndex;
      private String[] headers;
      private String[] originalHeaders;
      private ColumnDefinition[] columnDefAll;
      private List<ColumnDefinition> columnDefSelected;
      private boolean cancelled;
   }

   private static final Logger LOG = LoggerFactory.getLogger(ServerFileRuntime.class.getName());
}
