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
package inetsoft.uql.table;

import inetsoft.report.TableDataDescriptor;
import inetsoft.report.TableDataPath;
import inetsoft.report.internal.Util;
import inetsoft.report.lens.DefaultTableDataDescriptor;
import inetsoft.uql.XMetaInfo;
import inetsoft.uql.XTable;
import inetsoft.uql.asset.ConfirmException;
import inetsoft.uql.asset.internal.ColumnIndexMap;
import inetsoft.uql.avro.AvroXTableSerializer;
import inetsoft.uql.util.XIdentifierContainer;
import inetsoft.uql.util.XUtil;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.util.swap.XSwappableMonitor;
import inetsoft.util.swap.XSwapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.*;

/**
 * XSwappableTable provides the ability to cache table data in file system,
 * which has a mechanism to swap data between file system and memory. It is most
 * likely used for large-scale data.
 *
 * @version 9.1
 * @author InetSoft Technology Corp
 */
public class XSwappableTable implements XTable, Externalizable {
   /**
    * Constuctor.
    */
   public XSwappableTable() {
      super();

      creators = new XTableColumnCreator[0];
      this.tables = new XTableFragment[10];
      this.monitor = XSwapper.getMonitor();
      this.pos = 0;

      if(monitor != null) {
         isCountHM = monitor.isLevelQualified(XSwappableMonitor.HITS);
      }
   }

   /**
    * Create specified number of columns using object column creator.
    * @param dynamicType true to find the type of the column from inserted rows.
    */
   public XSwappableTable(int columnCount, boolean dynamicType) {
      this(createCreators(columnCount, dynamicType));
   }

   private static XTableColumnCreator[] createCreators(int columnCount, boolean dynamicType) {
      XTableColumnCreator[] creators = new XTableColumnCreator[columnCount];

      for(int i = 0; i < columnCount; i++) {
         creators[i] = XObjectColumn.getCreator();
         creators[i].setDynamic(dynamicType);
      }

      return creators;
   }

   /**
    * Contructor.
    */
   public XSwappableTable(XTableColumnCreator[] creators) {
      this();

      init(creators);
   }

   /**
    * Create table with columns of specified types.
    */
   public XSwappableTable(Class<?>[] colTypes) {
      this(Arrays.stream(colTypes).map(XObjectColumn::getCreator)
              .toArray(XTableColumnCreator[]::new));
   }

   /**
    * Initialize this swappable table.
    */
   public final void init(XTableColumnCreator[] creators) {
      this.creators = creators == null ? new XTableColumnCreator[0] : creators;
      this.identifiers = new XIdentifierContainer(this);
   }

   /**
    * Create a table fragment.
    * @return the created table fragment.
    */
   private XTableFragment createFragment() {
      XTableColumn[] columns = new XTableColumn[creators.length];

      for(int i = 0; i < columns.length; i++) {
         columns[i] = creators[i].createColumn((char) 128, (char) (MASK + 1));
      }

      return new XTableFragment(columns);
   }

   /**
    * Init table fragments.
    */
   public void initFragments(XTableFragment[] tables, Object[] headers, int count, String[] paths) {
      this.headers = headers;
      this.tables = tables;
      this.count = count;
      this.paths = paths;
      complete();
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
      if(!completed && row >= count) {
         try {
            rlock.lock();

            while(row >= count && !completed) {
               try {
                  rlockCond.await(10, TimeUnit.SECONDS);
               }
               catch(Exception ex) {
                  // ignore it
               }
            }
         }
         finally {
            rlock.unlock();
         }
      }

      return row < count;
   }

   /**
    * Get the applied max rows.
    * @return the applied max rows.
    */
   public int getAppliedMaxRows() {
      return 0;
   }

   /**
    * Check if a table is a result of timeout.
    */
   public boolean isTimeoutTable() {
      return false;
   }

   /**
    * Return the number of rows in the table. The number of rows includes
    * the header rows.
    * @return number of rows in table.
    */
   @Override
   public final int getRowCount() {
      return completed ? count : -count - 1;
   }

   /**
    * Return the number of columns in the table. The number of columns
    * includes the header columns.
    * @return number of columns in table.
    */
   @Override
   public final int getColCount() {
      return creators.length;
   }

   /**
    * Return the number of rows on the top of the table to be treated
    * as header rows.
    * @return number of header rows.
    */
   @Override
   public final int getHeaderRowCount() {
      return 1;
   }

   /**
    * Return the number of columns on the left of the table to be
    * treated as header columns.
    * @return number of header columns.
    */
   @Override
   public final int getHeaderColCount() {
      return 0;
   }

   /**
    * Return the number of rows on the bottom of the table to be treated
    * as trailer rows.
    * @return number of trailer rows.
    */
   @Override
   public final int getTrailerRowCount() {
      return 0;
   }

   /**
    * Return the number of columns on the right of the table to be
    * treated as trailer columns.
    * @return number of trailer columns.
    */
   @Override
   public final int getTrailerColCount() {
      return 0;
   }

   /**
    * Check if the value at one cell is null.
    * @param r the specified row index.
    * @param c column number.
    * @return <tt>true</tt> if null, <tt>false</tt> otherwise.
    */
   @Override
   public final boolean isNull(int r, int c) {
      if(r == 0) {
         return headers[c] == null;
      }
      else {
         int r2 = r - 1;
         int tidx = r2 >> BITS;
         int ridx = r2 & MASK;
         XTableFragment[] tables = this.tables;

         if(tables == null) {
            try {
               rlock.lock();
               tables = this.tables;
            }
            finally {
               rlock.unlock();
            }
         }

         return tables[tidx].isNull(ridx, c);
      }
   }

   /**
    * Return the value at the specified cell.
    * @param r row number.
    * @param c column number.
    * @return the value at the location.
    */
   @Override
   public final Object getObject(final int r, int c) {
      if(r == 0) {
         if(headers != null && c >= headers.length) {
            throw new IllegalArgumentException(
               "Column index out of bounds: " +  c + " >= " + headers.length + " cols: " +
                  getColCount());
         }

         return headers == null ? null : headers[c];
      }
      else {
         int r2 = r - 1;
         int tidx = r2 >> BITS;
         int ridx = r2 & MASK;
         XTableFragment[] tables = this.tables;

         if(tables == null) {
            try {
               rlock.lock();
               tables = this.tables;
            }
            finally {
               rlock.unlock();
            }
         }

         try {
            if(isCountHM) {
               countHitsMisses(r, tidx, tables[tidx]);
            }

            if(tables.length == 0) {
               return null;
            }

            return tables[tidx].getObject(ridx, c);
         }
         catch(IndexOutOfBoundsException ex) {
            if(tidx >= tables.length) {
               LOG.error("Table index out of bounds: " + tidx + " of " + tables.length +
                         " row: " + r + " rows: " + count + " completed: " + completed +
                         " datapaths: " + (paths != null ? Arrays.toString(paths) : "(null)") +
                         " more: " + moreRows(r) + " created at: " + ts, ex);
            }
            else if(c >= tables[tidx].getColumns().length) {
               LOG.error("Column index out of bounds: " + c +
                         " of " + tables[tidx].getColumns().length +
                         " datapaths: " + (paths != null ? Arrays.toString(paths) : "(null)") +
                         " row: " + r + " completed: " + completed + " more: " + moreRows(r) +
                         " created at: " + ts, ex);
            }
            else {
               XTableColumn col = tables[tidx].getColumns()[c];
               LOG.error("Table row index out of bounds: " + r + " of " + count + " frag: " + tidx +
                         " column: " + col.getClass() + " with length: " + col.length() +
                         " in memory: " + col.getInMemoryLength() + " completed: " + completed +
                         " snapshot: " + tables[tidx].getSnapshotPath() +
                         " more: " + moreRows(r) + " paths: " + Arrays.toString(paths) +
                         " swapinfo: " + col.getSwapLog() +
                         " created at: " + ts, ex);

               if(!tables[tidx].isDataPathFileExist()) {
                  Tool.addUserMessage(
                     Catalog.getCatalog().getString("common.table.data.file.missing"),
                     ConfirmException.ERROR);
               }
            }

            throw ex;
         }
         catch(NullPointerException ex) {
            // could be disposed.
            if(tables == null || tables[tidx] == null) {
               throw new RuntimeException("Table has been disposed.");
            }

            throw ex;
         }
      }
   }

   /**
    * Get the double value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the double value in the specified row.
    */
   @Override
   public final double getDouble(int r, int c) {
      if(r == 0) {
         return 0D;
      }
      else {
         int r2 = r - 1;
         int tidx = r2 >> BITS;
         int ridx = r2 & MASK;
         XTableFragment[] tables = this.tables;

         if(tables == null) {
            try {
               rlock.lock();
               tables = this.tables;
            }
            finally {
               rlock.unlock();
            }
         }

         if(isCountHM) {
            countHitsMisses(r, tidx, tables[tidx]);
         }

         return tables[tidx].getDouble(ridx, c);
      }
   }

   /**
    * Get the float value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the float value in the specified row.
    */
   @Override
   public final float getFloat(int r, int c) {
      if(r == 0) {
         return 0F;
      }
      else {
         int r2 = r - 1;
         int tidx = r2 >> BITS;
         int ridx = r2 & MASK;
         XTableFragment[] tables = this.tables;

         if(tables == null) {
            try {
               rlock.lock();
               tables = this.tables;
            }
            finally {
               rlock.unlock();
            }
         }

         if(isCountHM) {
            countHitsMisses(r, tidx, tables[tidx]);
         }

         return tables[tidx].getFloat(ridx, c);
      }
   }

   /**
    * Get the long value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the long value in the specified row.
    */
   @Override
   public final long getLong(int r, int c) {
      if(r == 0) {
         return 0L;
      }
      else {
         int r2 = r - 1;
         int tidx = r2 >> BITS;
         int ridx = r2 & MASK;
         XTableFragment[] tables = this.tables;

         if(tables == null) {
            try {
               rlock.lock();
               tables = this.tables;
            }
            finally {
               rlock.unlock();
            }
         }

         if(isCountHM) {
            countHitsMisses(r, tidx, tables[tidx]);
         }

         return tables[tidx].getLong(ridx, c);
      }
   }

   /**
    * Get the int value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the int value in the specified row.
    */
   @Override
   public final int getInt(int r, int c) {
      if(r == 0) {
         return 0;
      }
      else {
         int r2 = r - 1;
         int tidx = r2 >> BITS;
         int ridx = r2 & MASK;
         XTableFragment[] tables = this.tables;

         if(tables == null) {
            try {
               rlock.lock();
               tables = this.tables;
            }
            finally {
               rlock.unlock();
            }
         }

         if(isCountHM) {
            countHitsMisses(r, tidx, tables[tidx]);
         }

         return tables[tidx].getInt(ridx, c);
      }
   }

   /**
    * Get the short value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the short value in the specified row.
    */
   @Override
   public final short getShort(int r, int c) {
      if(r == 0) {
         return 0;
      }
      else {
         int r2 = r - 1;
         int tidx = r2 >> BITS;
         int ridx = r2 & MASK;
         XTableFragment[] tables = this.tables;

         if(tables == null) {
            try {
               rlock.lock();
               tables = this.tables;
            }
            finally {
               rlock.unlock();
            }
         }

         if(isCountHM) {
            countHitsMisses(r, tidx, tables[tidx]);
         }

         return tables[tidx].getShort(ridx, c);
      }
   }

   /**
    * Get the byte value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the byte value in the specified row.
    */
   @Override
   public final byte getByte(int r, int c) {
      if(r == 0) {
         return 0;
      }
      else {
         int r2 = r - 1;
         int tidx = r2 >> BITS;
         int ridx = r2 & MASK;
         XTableFragment[] tables = this.tables;

         if(tables == null) {
            try {
               rlock.lock();
               tables = this.tables;
            }
            finally {
               rlock.unlock();
            }
         }

         if(isCountHM) {
            countHitsMisses(r, tidx, tables[tidx]);
         }

         return tables[tidx].getByte(ridx, c);
      }
   }

   /**
    * Get the boolean value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the boolean value in the specified row.
    */
   @Override
   public final boolean getBoolean(int r, int c) {
      if(r == 0) {
         return false;
      }
      else {
         int r2 = r - 1;
         int tidx = r2 >> BITS;
         int ridx = r2 & MASK;
         XTableFragment[] tables = this.tables;

         if(tables == null) {
            try {
               rlock.lock();
               tables = this.tables;
            }
            finally {
               rlock.unlock();
            }
         }

         if(isCountHM) {
            countHitsMisses(r, tidx, tables[tidx]);
         }

         return tables[tidx].getBoolean(ridx, c);
      }
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
   public final void setObject(int r, int c, Object v) {
      if(r == 0 && headers != null && c < headers.length) {
         headers[c] = v;
         return;
      }

      throw new RuntimeException("Unsupported method called!");
   }

   /**
    * Get the current column content type.
    * @param col column number.
    * @return column type.
    */
   @Override
   public Class<?> getColType(int col) {
      Class<?> cls = null;

      if(col < creators.length) {
         cls = creators[col].getColType();
      }

      if(cls == null) {
         cls = Util.getColType(this, col, String.class, 1000, true);
      }

      return cls != null ? cls : String.class;
   }

   /**
    * Check if is primitive.
    * @return <tt>true</tt> if is primitive, <tt>false</tt> otherwise.
    */
   @Override
   public final boolean isPrimitive(int col) {
      if(pos == 0) {
         return false;
      }

      XTableFragment[] tables = this.tables;

      if(tables == null) {
         try {
            rlock.lock();
            tables = this.tables;
         }
         finally {
            rlock.unlock();
         }
      }

      return tables[0].isPrimitive(col);
   }

   /**
    * Add a new row to the table.
    * @param row the specified row object.
    */
   public final void addRow(Object[] row) {
      if(count == 0) {
         if(row.length < getColCount()) {
            headers = new Object[getColCount()];
            System.arraycopy(row, 0, headers, 0, row.length);

            for(int i = row.length; i < headers.length; i++) {
               headers[i] = "col" + i;
            }
         }
         else {
            headers = row.clone();
         }
      }
      else {
         if(((count - 1) & MASK) == 0) {
            if(table != null) {
               table.complete();
               table = null;
            }

            try {
               rlock.lock();
               table = createFragment();

               if(!objectPooled) {
                  table.removeObjectPool();
               }

               ensureCapacity(table);
            }
            finally {
               rlock.unlock();
            }
         }

         for(int i = 0; i < row.length; i++) {
            XTableColumnCreator creator = table.addObject(i, row[i]);

            if(creator != null) {
               creators[i] = creator;
            }
         }
      }

      if((count & STAGE_MASK) == 0) {
         if((count & 0x7ff) == 0 && count > 0) {
            XSwapper.getSwapper().waitForMemory();
         }

         try {
            rlock.lock();
            rlockCond.signalAll();

            if((count & 0xffff) == 0 && count > 0) {
               STAGE_MASK = 0x7ff; // avoid refrequent lock/unlock
            }
         }
         finally {
            rlock.unlock();
         }
      }

      count++;
   }

   /**
    * Complete this table. The method MUST be called after all rows added.
    */
   public void complete() {
      try {
         rlock.lock();

         if(completed) {
            return;
         }

         if(table != null) {
            table.complete();
         }

         completed = true;
         rlockCond.signalAll();
      }
      finally {
         rlock.unlock();
      }
   }

   /**
    * Check if the table has been completed.
    * @return <tt>true</tt> if completed, <tt>false</tt> otherwise.
    */
   public final boolean isCompleted() {
      return completed;
   }

   /**
    * Dispose the paged table.
    */
   @Override
   public final void dispose() {
      try {
         rlock.lock();

         if(disposed) {
            return;
         }

         disposed = true;

         if(tables != null) {
            for(int i = 0; i < pos; i++) {
               tables[i].dispose();
               tables[i] = null;
            }

            tables = null;
         }
      }
      finally {
         rlock.unlock();
      }

      if(mmap != null) {
         mmap.clear();
         mmap = null;
      }
   }

   /**
    * Check if the table has being disposed.
    * @return <tt>true</tt> if disposed, <tt>false</tt> otherwise.
    */
   public final boolean isDisposed() {
      return disposed;
   }

   /**
    * whether the maximum row or maximum column limit is exceeded
    */
   public boolean isExceedLimit() {
      return exceedLimit;
   }

   /**
    * Set the maximum row or maximum column limit to be exceeded
    */
   public void setExceedLimit(boolean exceedLimit) {
      this.exceedLimit = exceedLimit;
   }

   /**
    * whether the maximum row or maximum column limit is exceeded
    */
   public boolean isTextExceedLimit() {
      return textExceedLimit;
   }

   /**
    * Set the maximum row or maximum column limit to be exceeded
    */
   public void setTextExceedLimit(boolean exceedLimit) {
      this.textExceedLimit = exceedLimit;
   }

   /**
    * Finalize the object.
    */
   @Override
   protected final void finalize() throws Throwable {
      dispose();
      super.finalize();
   }

   /**
    * Get the column identifier of a column.
    * @param col the specified column index.
    * @return the column indentifier of the column. The identifier might be
    * different from the column name, for it may contain more locating
    * information than the column name.
    */
   @Override
   public final String getColumnIdentifier(int col) {
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
   public final void setColumnIdentifier(int col, String identifier) {
      identifiers.setColumnIdentifier(col, identifier);
   }

   /**
    * Find the column index by column header.
    * @return column index or -1 if not found.
    */
   public int findColumn(String header) {
      if(columnIndexMap == null) {
         columnIndexMap = new ColumnIndexMap(this, true);
      }

      return Util.findColumn(columnIndexMap, header, false);
   }

   /**
    * Set the meta info.
    * @param header the column header.
    * @param minfo the meta info.
    */
   public final void setXMetaInfo(String header, XMetaInfo minfo) {
      int col = findColumn(header);
      Class<?> type = String.class;

      if(col < 0) {
         LOG.warn("Column not found: " + header);
      }
      else {
         type = getColType(col);
      }

      TableDataPath path = new TableDataPath(-1, TableDataPath.DETAIL,
         Util.getDataType(type), new String[] {header});

      if(minfo == null) {
         if(mmap != null) {
            mmap.remove(path);
         }
      }
      else {
         if(mmap == null) {
            mmap = new HashMap<>();
         }

         mmap.put(path, minfo);
      }
   }

   /**
    * Get internal table data descriptor which contains table structural
    * infos.
    * @return table data descriptor.
    */
   @Override
   public final TableDataDescriptor getDescriptor() {
      if(descriptor == null) {
         descriptor = new TableDataDescriptor2(this);
      }

      return descriptor;
   }

   /**
    * Ensure capacity.
    */
   private void ensureCapacity(XTableFragment ntable) {
      if(pos == tables.length) {
         int nsize = (int) (tables.length * 1.5);
         XTableFragment[] otables = tables;
         XTableFragment[] ntables = new XTableFragment[nsize];
         System.arraycopy(otables, 0, ntables, 0, otables.length);
         ntables[pos++] = ntable;
         tables = ntables;
      }
      else {
         tables[pos++] = ntable;
      }
   }

   /**
    * Count the number of hits and misses.
    */
   private void countHitsMisses(int r, int tableIdx, XTableFragment table) {
      if(r == lastRow) {
         return;
      }

      lastRow = r;

      if(!table.isValid()) {
         monitor.countMisses(XSwappableMonitor.DATA, 1);
         lastTable.set(tableIdx);
      }
      else if(lastTable.getAndSet(tableIdx) != tableIdx) {
         monitor.countHits(XSwappableMonitor.DATA, 1);
      }
   }

   /**
    * Write table to swap file and return swapped files list.
    */
   public List<File> getFilesList() {
      try {
         rlock.lock();

         while(!completed) {
            try {
               rlockCond.await(10, TimeUnit.SECONDS);
            }
            catch(Exception ex) {
               // ignore it
            }
         }
      }
      finally {
         rlock.unlock();
      }

      List<File> list = new ArrayList<>();

      for(XTableFragment table : tables) {
         if(table != null) {
            table.swap(true);
            list.addAll(table.getFiles());
         }
      }

      return list;
   }

   /**
    * Get table fragment prefixes.
    */
   public String[] getPrefixes() {
      try {
         rlock.lock();

         while(!completed) {
            try {
               rlockCond.await(10, TimeUnit.SECONDS);
            }
            catch(Exception ex) {
               // ignore it
            }
         }
      }
      finally {
         rlock.unlock();
      }

      List<String> list = new ArrayList<>();

      for(XTableFragment table: tables) {
         if(table != null) {
            list.add(table.getPrefix());
         }
      }

      String[] arr = new String[list.size()];
      list.toArray(arr);

      return arr;
   }

   /**
    * Get creators.
    */
   public XTableColumnCreator[] getCreators() {
      return creators;
   }

   /**
    * Get table fragments.
    */
   public XTableFragment[] getTables() {
       return tables;
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

   /**
    * Set whether internal cache should be used to share objects.
    */
   public void setObjectPooled(boolean objectPooled) {
      this.objectPooled = objectPooled;
   }

   /**
    * Check whether internal cache is used to share objects.
    */
   public boolean isObjectPooled() {
      return objectPooled;
   }

   private boolean allTableDataFileMissing() {
      return Arrays.stream(this.tables).allMatch(table -> !table.isDataPathFileExist());
   }

   @Override
   public void writeExternal(ObjectOutput out) throws IOException {
      AvroXTableSerializer.writeTable(out, this);
   }

   @Override
   public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
      AvroXTableSerializer.readTable(in, this);
   }

   /**
    * Table data descriptor.
    */
   private final class TableDataDescriptor2 extends DefaultTableDataDescriptor {
      public TableDataDescriptor2(XTable table) {
         super(table);
      }

      /**
       * Get meta info of a specified table data path.
       * @param path the specified table data path.
       * @return meta info of the table data path.
       */
      @Override
      public final XMetaInfo getXMetaInfo(TableDataPath path) {
         if(mmap == null || path == null || !path.isCell()) {
            return null;
         }

         return mmap.get(path);
      }

      /**
       * Check if contains format.
       * @return true if contains format.
       */
      @Override
      public final boolean containsFormat() {
         if(cformat == 0) {
            cformat = XUtil.containsFormat(mmap) ? CONTAINED : NOT_CONTAINED;
         }

         return cformat == CONTAINED;
      }

      /**
       * Check if contains drill.
       * @return true if contains drill.
       */
      @Override
      public final boolean containsDrill() {
         if(cdrill == 0) {
            cdrill = XUtil.containsDrill(mmap) ? CONTAINED : NOT_CONTAINED;
         }

         return cdrill == CONTAINED;
      }

      private static final int NOT_CONTAINED = 1;
      private static final int CONTAINED = 2;
      private int cformat = 0;
      private int cdrill = 0;
   }

   protected final Lock rlock = new ReentrantLock(); // table row lock
   private final Condition rlockCond = rlock.newCondition();

   private static final int BITS = 13;
   private static final int MASK = 0x1fff;

   private XTableFragment[] tables; // tables
   private final AtomicInteger lastTable = new AtomicInteger(-1);
   private int pos; // next position
   private XTableColumnCreator[] creators; // table column creators
   private Object[] headers; // header row
   private Map<TableDataPath, XMetaInfo> mmap = null; // meta info table
   private boolean completed = false; // data fully loaded
   private boolean disposed = false; // table disposed
   private String[] paths;
   private XIdentifierContainer identifiers = null; // identifier container
   protected int count; // table count
   private int lastRow = -1;
   private boolean exceedLimit = false;
   private boolean textExceedLimit = false;
   private TableDataDescriptor descriptor; // table data descriptor
   private final Properties prop = new Properties(); // properties
   private transient XTableFragment table; // current table fragment
   private final transient XSwappableMonitor monitor;
   private transient boolean isCountHM;
   private transient ColumnIndexMap columnIndexMap = null;
   private transient int STAGE_MASK = 0xff;
   private boolean objectPooled = true;
   private Date ts = new Date();
   private static final Logger LOG = LoggerFactory.getLogger(XSwappableTable.class);
}
