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
package inetsoft.uql.util.filereader;

import inetsoft.uql.*;
import inetsoft.uql.table.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Table node implementation that handles reading a flat text file.
 *
 * @author InetSoft Technology
 * @since  11.0
 */
abstract class TextTableNode extends XTableNode {
   /**
    * Creates a new instance of <tt>TextTableNode</tt>.
    *
    * @param reader      the <tt>Reader</tt> from which to read the data.
    * @param query       the query definition.
    * @param columnCount the number of columns in the raw data.
    * @param maxRows     the maximum number of rows to read.
    * @param delimiter   an input delimiter if query is null
    *
    * @throws Exception if the table could not be initialized.
    */
   public TextTableNode(BufferedReader reader, XQuery query,
                        int columnCount, int maxRows, boolean firstRowHeader,
                        String delimiter, boolean removeQuote)
           throws Exception
   {
      this.reader = reader;
      this.query = query;
      this.columnCount = columnCount;
      this.maxRows = maxRows;
      this.table = new XSwappableTable();
      this.firstRowHeader = firstRowHeader;
      this.delimiter = delimiter;
      this.removeQuote = removeQuote;

      creators = new XTableColumnCreator[columnCount];

      for(int i = 0; i < creators.length; i++) {
         creators[i] = XStringColumn.getCreator();
         creators[i].setDynamic(false);
      }

      table.init(creators);

      if(firstRowHeader) {
         readRow();
         currentRow = 0;
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public XTableColumnCreator getColumnCreator(int col) {
      return creators[col];
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public XTableColumnCreator[] getColumnCreators() {
      return creators;
   }

   /**
    * Disposes of the resources held by this table.
    *
    * @param disposeTable <tt>true</tt> to dispose of the table.
    */
   private void dispose(boolean disposeTable) {
      lock.lock();

      try {
         if(reader != null) {
            try {
               reader.close();
            }
            catch(Throwable exc) {
               LOG.warn("Failed to close data stream", exc);
            }
            finally {
               reader = null;
            }
         }

         if(!table.isCompleted()) {
            table.complete();
         }

         if(disposeTable && !table.isDisposed()) {
            table.dispose();
         }
      }
      finally {
         lock.unlock();
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void cancel() {
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
    * {@inheritDoc}
    */
   @Override
   public int getAppliedMaxRows() {
      return appliedMaxRows;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public int getColCount() {
      return columnCount;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public String getName(int col) {
      if(firstRowHeader) {
         return (String) table.getObject(0, col);
      }

      return "column" + col;
   }

   /**
    * {@inheritDoc}
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
    * {@inheritDoc}
    */
   @Override
   public Class<?> getType(int col) {
      return String.class;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public XMetaInfo getXMetaInfo(int col) {
      return null;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public boolean isRewindable() {
      return true;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public boolean next() {
      lock.lock();

      try {
         if(++currentRow < getRowCount()) {
            return true;
         }
         else if(!table.isCompleted()) {
            try {
               return readRow();
            }
            catch(Throwable exc) {
               LOG.error("Failed to read data row", exc);
               dispose(false);
            }
         }

         return false;
      }
      finally {
         lock.unlock();
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public boolean rewind() {
      lock.lock();

      try {
         currentRow = firstRowHeader ? 0 : -1;
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
    * Trim the white spaces at the end of the string.
    */
   private String rightTrim(String str) {
      if(str == null || str.length() == 0) {
         return str;
      }

      int len = str.length();
      int idx = len - 1;

      while (idx >= 0 && str.charAt(idx) <= ' ') {
         idx--;
     }

      return str.substring(0, idx + 1);
   }

   /**
    * Reads the next row from the file.
    *
    * @return the next row.
    *
    * @throws Exception if the row could not be read.
    */
   private boolean readRow() throws Exception {
      if(table.isDisposed()) {
         throw new IllegalStateException("Table is closed");
      }

      if(table.isCompleted()) {
         throw new IllegalStateException("Reading is complete");
      }

      Object[] row = null;
      String line = null;

      while((line = readLine()) != null) {
         if(!isCSV()) {
            line = rightTrim(line);

            if(line.length() > 0) {
               if(maxRows > 0 && getRowCount() >= maxRows) {
                  appliedMaxRows = maxRows;
               }
               else {
                  row = new Object[columnCount];
                  String[] srow = parseRow(line, query, this.delimiter, this.removeQuote);
                  int ncols = Math.min(srow.length, row.length);
                  System.arraycopy(srow, 0, row, 0, ncols);
               }

               break;
            }
         }
         else {
            if(maxRows > 0 && getRowCount() >= maxRows) {
               appliedMaxRows = maxRows;
               break;
            }

            int ncol = table.getColCount();

            String[] lines = splitLine(line, delimiter);
            row = new Object[ncol];
            ncol = Math.min(ncol, lines.length);

            for(int i = 0; i < ncol; i++) {
               String trimmed = lines[i].trim();
               trimmed = trimmed.replaceAll("\n", "\\\\n");
               row[i] = trimmed.startsWith("\"") && trimmed.endsWith("\"") && isCSV()
                  // space ignored if values are quoted
                  ? (removeQuote ? TextUtil.stripOffQuote(trimmed) : trimmed)
                  // space preserved if values are not quoted
                  : TextUtil.cleanUpString(lines[i]);
            }

            break;
         }
      }

      if(row == null) {
         dispose(false);
      }
      else {
         table.addRow(row);
      }

      return row != null;
   }

   /**
    * Split the line.
    * @param line the specified String.
    * @param delim the delimiter.
    * @return the name of the asset event.
    */
   private String[] splitLine(String line, String delim) {
      return TextUtil.split(line, delim, true, false, true);
   }

   /**
    * Read a line.
    */
   protected String readLine() throws Exception {
      return reader.readLine();
   }

   /**
    * If is csv file.
    */
   protected boolean isCSV() {
      return query == null ? ",".equals(delimiter) : "true".equals(query.getProperty("isCSV"));
   }

   /**
    * Parses a single row of data.
    *
    * @param line  the line to parse.
    * @param query the query definition.
    *
    * @return the field values.
    *
    * @throws Exception if the row could not be parsed.
    */
   protected abstract String[] parseRow(String line, XQuery query, String delimiter, boolean removeQuote)
      throws Exception;

   protected BufferedReader reader = null;
   private final transient XQuery query;
   private final int columnCount;
   private final XSwappableTable table;
   private final XTableColumnCreator[] creators;
   private int currentRow = -1;
   private final Lock lock = new ReentrantLock();
   private int appliedMaxRows = 0;
   private int maxRows = Integer.MAX_VALUE;
   private boolean firstRowHeader;
   private String delimiter;
   private boolean removeQuote;

   private static final Logger LOG =
      LoggerFactory.getLogger(TextTableNode.class);
}
