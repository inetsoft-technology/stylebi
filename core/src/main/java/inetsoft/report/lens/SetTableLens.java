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
import inetsoft.report.event.TableChangeEvent;
import inetsoft.report.event.TableChangeListener;
import inetsoft.report.filter.BinaryTableFilter;
import inetsoft.report.filter.DefaultTableChangeListener;
import inetsoft.report.internal.Util;
import inetsoft.report.internal.table.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.internal.ColumnIndexMap;
import inetsoft.uql.util.XIdentifierContainer;
import inetsoft.util.ThreadPool;
import inetsoft.util.Tool;
import inetsoft.util.swap.XSwappableObjectList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.Serializable;
import java.text.Format;
import java.util.*;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Set table lens implements most of the functions to do a set operation on
 * one/two tables. The sub class may vary its function in table supply and
 * visitor implementation.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public abstract class SetTableLens
   implements BinaryTableFilter, CancellableTableLens
{
   /**
    * Constructor.
    */
   public SetTableLens() {
      this.identifiers = new XIdentifierContainer(this);
   }

   /**
    * Constructor.
    */
   public SetTableLens(TableLens ltable, TableLens rtable) {
      this();
      setTables(ltable, rtable);
   }

   /**
    * Add table change listener to the filtered table.
    * If the table filter's data changes, a TableChangeEvent will be triggered
    * for the TableChangeListener to process.
    * @param listener the specified TableChangeListener
    */
   @Override
   public void addChangeListener(TableChangeListener listener) {
      clisteners.add(listener);
   }

   /**
    * Remove table change listener from the filtered table.
    * @param listener the specified TableChangeListener to be removed
    */
   @Override
   public void removeChangeListener(TableChangeListener listener) {
      clisteners.remove(listener);
   }

   /**
    * Fire change event when filtered table changed.
    */
   protected void fireChangeEvent() {
      try {
         for(TableChangeListener listener :
            new ArrayList<>(clisteners))
         {
            // reuse event object for optimization reason
            if(event == null) {
               event = new TableChangeEvent(this);
            }

            listener.tableChanged(event);
         }
      }
      catch(Exception ex) {
         LOG.error("Failed to process change event", ex);
      }
   }

   /**
    * Get the left base table lens.
    * @return the left base table lens.
    */
   @Override
   public TableLens getLeftTable() {
      return getTable(0);
   }

   /**
    * Get the right base table lens.
    * @return the right base table lens.
    */
   @Override
   public TableLens getRightTable() {
      return getTable(1);
   }

   /**
    * Gets the specified base table.
    *
    * @param index the index of the table.
    *
    * @return the specified table or <tt>null</tt> if it is not set.
    */
   public TableLens getTable(int index) {
      return index >= tables.size() ? null : tables.get(index);
   }

   /**
    * Gets the number of base tables.
    * @return the table count.
    */
   public int getTableCount() {
      return tables.size();
   }

   /**
    * Get all base tables.
    */
   public TableLens[] getTables() {
      return tables.toArray(new TableLens[0]);
   }

   /**
    * Check if only left data might be contained in the result set.
    * @return <tt>true</tt> if yes, <tt>false</tt>.
    */
   boolean isLeftOnly() {
      return false;
   }

   /**
    * Get table drill info.
    * @param row the row number.
    * @param col the col number.
    */
   @Override
   public XDrillInfo getXDrillInfo(int row, int col) {
      TableDataDescriptor descriptor = getDescriptor();

      if(!descriptor.containsDrill()) {
         return null;
      }

      TableDataPath path = descriptor.getCellDataPath(row, col);
      XMetaInfo minfo = (XMetaInfo) descriptor.getXMetaInfo(path);

      return minfo == null ? null : minfo.getXDrillInfo();
   }

   /**
    * Return the per cell format.
    *
    * @param row row number.
    * @param col column number.
    * @return format for the specified cell.
    */
   @Override
   public Format getDefaultFormat(int row, int col) {
      TableDataDescriptor descriptor = getDescriptor();

      if(!descriptor.containsFormat()) {
         return null;
      }

      TableDataPath path = descriptor.getCellDataPath(row, col);
      XMetaInfo minfo = descriptor.getXMetaInfo(path);
      Format format = null;

      if(minfo != null) {
         XFormatInfo formatInfo = minfo.getXFormatInfo();
         format = formatInfo == null ? null : TableFormat.getFormat(
            formatInfo.getFormat(), formatInfo.getFormatSpec());
      }

      return format;
   }

   /**
    * Check if contains format.
    *
    * @return true if contains format.
    */
   @Override
   public boolean containsFormat() {
      return getDescriptor().containsFormat();
   }

   /**
    * Check if contains drill.
    *
    * @return true if contains drill.
    */
   @Override
   public boolean containsDrill() {
      return getDescriptor().containsDrill();
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

               TableDataDescriptor ldescriptor = tables.get(0).getDescriptor();
               XMetaInfo lminfo = ldescriptor.getXMetaInfo(path);

               if(columnIndexMap == null) {
                  columnIndexMap = new ColumnIndexMap(SetTableLens.this, true);
               }

               int col = Util.findColumn(columnIndexMap, path.getPath()[0], false);

               // merge left table meta info and right table meta info
               XMetaInfo minfo = null;

               for(int i = 1; i < tables.size(); i++) {
                  String header = col < 0 || isLeftOnly() ?
                     null : Util.getHeader(getTable(i), col).toString();

                  TableDataPath opath = header == null ? null :
                     new TableDataPath(-1, path.getType(), path.getDataType(),
                                       new String[] {header});

                  TableDataDescriptor rdescriptor = getTable(i).getDescriptor();
                  XMetaInfo rminfo = opath == null ? null :
                     rdescriptor.getXMetaInfo(opath);

                  if(lminfo != null) {
                     minfo = (XMetaInfo) lminfo.clone();

                     if(rminfo != null) {
                        if(minfo.isXDrillInfoEmpty() &&
                           !rminfo.isXDrillInfoEmpty())
                        {
                           minfo.setXDrillInfo(rminfo.getXDrillInfo());
                        }
                     }
                  }
                  else if(rminfo != null) {
                     minfo = (XMetaInfo) rminfo.clone();
                  }

                  // merge format info, if left format info is not auto created,
                  // apply left format, else if right format info is not auto
                  // created, apply right format, otherwise, if left and right are
                  // both auto created, ignore format
                  // fix bug1261024513242, bug1260943347453
                  mergeXFormat(minfo, lminfo, rminfo);
                  lminfo = rminfo;
               }

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
               boolean result = false;

               for(TableLens table : tables) {
                  if(table.containsFormat()) {
                     result = true;
                     break;
                  }
               }

               return result;
            }

            /**
             * Check if contains drill.
             * @return <tt>true</tt> if contains drill.
             */
            @Override
            public boolean containsDrill() {
               boolean result = false;

               for(TableLens table : tables) {
                  if(table.containsDrill()) {
                     result = true;
                     break;
                  }
               }

               return result;
            }

            /**
             * Merge left and right XMetaInfo's XFormatInfo.
             */
            private void mergeXFormat(XMetaInfo minfo, XMetaInfo linfo,
                                      XMetaInfo rinfo)
            {
               if(minfo == null) {
                  return;
               }

               XFormatInfo finfo = null;

               // left meta info is not auto created format? apply it
               if(linfo != null && !Util.isXFormatInfoReplaceable(linfo)) {
                  finfo = linfo.getXFormatInfo();
               }

               // right meta info is not auto created format? apply it
               if(finfo == null && rinfo != null &&
                  !Util.isXFormatInfoReplaceable(rinfo))
               {
                  finfo = rinfo.getXFormatInfo();
               }

               if(finfo != null) {
                  minfo.setXFormatInfo(finfo);
               }
            }

            private transient ColumnIndexMap columnIndexMap = null;
         };
      }

      return descriptor;
   }

   /**
    * Set the base table lenses.
    * @param ltable the specified left base table lens, <tt>null</tt> is not
    * allowed.
    * @param rtable the specified right base table lens, <tt>null</tt> is
    * allowed.
    */
   public void setTables(TableLens ltable, TableLens rtable) {
      setTable(0, ltable);
      setTable(1, rtable);
   }

   public void setTable(int index, TableLens table) {
      if(tables.size() == index) {
         tables.add(table);
      }
      else {
         tables.set(index, table);
      }

      if(table != null) {
         table.addChangeListener(new DefaultTableChangeListener(this));
         ccount = tables.size() == 1 ?
            table.getColCount() : Math.min(ccount, table.getColCount());
      }

      invalidate();
   }

   /**
    * Cancel the table lens and running queries if supported.
    */
   @Override
   public void cancel() {
      cancelLock.lock();

      try {
         cancelled = !completed;

         if(merged != null) {
            merged.dispose();
            merged = null;
            cancelled = true;
         }

         for(TableLens table : tables) {
            if(table instanceof CancellableTableLens) {
               ((CancellableTableLens) table).cancel();
            }
         }
      }
      finally {
         cancelLock.unlock();
      }
   }

   /**
    * Check the TableLens to see if it is cancelled.
    */
   @Override
   public boolean isCancelled() {
      return cancelled;
   }

   /**
    * Invalidate the table filter forcely, and the table filter will
    * perform filtering calculation to validate itself.
    */
   @Override
   public void invalidate() {
      synchronized(this) {
         if(!validated) {
            return;
         }

         if(merged != null) {
            merged.dispose();
            merged = null;
         }

         if(rows != null) {
            rows.dispose();
            rows = null;
         }

         completed = false;
         validated = false;
         mmap.clear();
      }

      fireChangeEvent();
   }

   /**
    * Create a merged table.
    * @return the created merged table.
    */
   protected MergedTable createMergedTable() throws Exception {
      return new MergedTable();
   }

   /**
    * Validate the set table lens.
    */
   private void validate() throws Exception {
      synchronized(this) {
         if(validated) {
            return;
         }

         rows = new XSwappableObjectList<>(null);
         validated = true;
         merged = createMergedTable();

         for(int i = 0; i < tables.get(0).getHeaderRowCount(); i++) {
            Row row = new Row(0, i);
            addSetRow(row);
         }

         // notify waiting consumers
         notifyAll();
      }

      int[] cols = new int[ccount];

      for(int i = 0; i < cols.length; i++) {
         cols[i] = i;
      }

      // blocked process
      for(int i = 0; i < tables.size(); i++) {
         merged.addTable(tables.get(i), i, cols);
      }

      // concurrent process
      final MergedTable merged2 = merged;
      ThreadPool.addOnDemand(() -> {
         try {
            merged2.accept(getVisitor());
         }
         catch(InterruptedException ex) {
            // ignore it
         }
         catch(Exception ex) {
            LOG.error("Failed to merge tables", ex);
         }

         synchronized(SetTableLens.this) {
            if(rows != null) {
               rows.complete();
            }

            if(!merged2.isDisposed()) {
               merged.dispose();
               merged = null;
               completed = true;
               // notify waiting consumers
               SetTableLens.this.notifyAll();
            }
         }
      });
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
   public synchronized boolean moreRows(int row) {
      try {
         validate();

         while((rows == null || row >= rows.size()) && !completed) {
            try {
               wait(50);
               validate();
            }
            catch(InterruptedException ex) {
               // ignore it
            }
         }

         return rows != null && row < rows.size();
      }
      catch(Exception ex) {
         completed = true;
         LOG.error("Failed to validate rows when checking if row " +
            "is available: " + row, ex);
         return false;
      }
   }

   /**
    * Return the number of rows in the table. The number of rows includes
    * the header rows. If the table is loading in background and loading
    * is not done, return the negative number of loaded rows minus 1.
    * @return number of rows in table.
    */
   @Override
   public synchronized int getRowCount() {
      try {
         validate();

         return completed ? rows.size() : -rows.size() - 1;
      }
      catch(Exception ex) {
         completed = true;
         LOG.error("Failed to validate table rows when getting " +
            "row count", ex);
         return -1;
      }
   }

   /**
    * Return the number of columns in the table. The number of columns
    * includes the header columns.
    * @return number of columns in table.
    */
   @Override
   public int getColCount() {
      return ccount;
   }

   /**
    * Return the number of rows on the top of the table to be treated
    * as header rows.
    * @return number of header rows.  Default is 1.
    */
   @Override
   public int getHeaderRowCount() {
      return tables.get(0).getHeaderRowCount();
   }

   /**
    * Return the number of columns on the left of the table to be
    * treated as header columns.
    */
   @Override
   public int getHeaderColCount() {
      return tables.get(0).getHeaderColCount();
   }

   /**
    * Return the number of rows on the bottom of the table to be treated
    * as trailer rows.
    * @return number of header rows.
    */
   @Override
   public int getTrailerRowCount() {
      return 0;
   }

   /**
    * Return the number of columns on the right of the table to be
    * treated as trailer columns.
    */
   @Override
   public int getTrailerColCount() {
      return 0;
   }

   /**
    * Check if is primitive.
    * @return <tt>true</tt> if is primitive, <tt>false</tt> otherwise.
    */
   @Override
   public final boolean isPrimitive(int col) {
      boolean result = true;

      for(TableLens table : tables) {
         if(!table.isPrimitive(col)) {
            result = false;
            break;
         }
      }

      return result;
   }

   /**
    * Check if the value at one cell is null.
    * @param r the specified row index.
    * @param c column number.
    * @return <tt>true</tt> if null, <tt>false</tt> otherwise.
    */
   @Override
   public final boolean isNull(int r, int c) {
      Row row = getRow(r);
      TableLens table = tables.get(row.getTable());
      return table.isNull(row.getRow(), c);
   }

   /**
    * Return the value at the specified cell.
    * @param r row number.
    * @param c column number.
    * @return the value at the location.
    */
   @Override
   public Object getObject(int r, int c) {
      if(!moreRows(r)) {
         return null;
      }

      Row row = getRow(r);
      TableLens table = tables.get(row.getTable());
      return table.getObject(row.getRow(), c);
   }

   /**
    * Get the double value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the double value in the specified row.
    */
   @Override
   public final double getDouble(int r, int c) {
      Row row = getRow(r);
      TableLens table = tables.get(row.getTable());
      return table.getDouble(row.getRow(), c);
   }

   /**
    * Get the float value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the float value in the specified row.
    */
   @Override
   public final float getFloat(int r, int c) {
      Row row = getRow(r);
      TableLens table = tables.get(row.getTable());
      return table.getFloat(row.getRow(), c);
   }

   /**
    * Get the long value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the long value in the specified row.
    */
   @Override
   public final long getLong(int r, int c) {
      Row row = getRow(r);
      TableLens table = tables.get(row.getTable());
      return table.getLong(row.getRow(), c);
   }

   /**
    * Get the int value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the int value in the specified row.
    */
   @Override
   public final int getInt(int r, int c) {
      Row row = getRow(r);
      TableLens table = tables.get(row.getTable());
      return table.getInt(row.getRow(), c);
   }

   /**
    * Get the short value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the short value in the specified row.
    */
   @Override
   public final short getShort(int r, int c) {
      Row row = getRow(r);
      TableLens table = tables.get(row.getTable());
      return table.getShort(row.getRow(), c);
   }

   /**
    * Get the byte value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the byte value in the specified row.
    */
   @Override
   public final byte getByte(int r, int c) {
      Row row = getRow(r);
      TableLens table = tables.get(row.getTable());
      return table.getByte(row.getRow(), c);
   }

   /**
    * Get the boolean value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the boolean value in the specified row.
    */
   @Override
   public final boolean getBoolean(int r, int c) {
      Row row = getRow(r);
      TableLens table = tables.get(row.getTable());
      return table.getBoolean(row.getRow(), c);
   }

   /**
    * Get the current column content type.
    * @param col column number.
    * @return column type.
    */
   @Override
   public Class<?> getColType(int col) {
      Class<?> clazz = tables.get(0).getColType(col);

      if(tables.size() == 1) {
         return clazz;
      }

      boolean issame = true;
      boolean isnumeric = true;
      Integer weight = NUMERIC_MAP.get(clazz);

      for(int i = 1; i < tables.size(); i++) {
         Class<?> rclazz = tables.get(i).getColType(col);

         if(issame &&
            (clazz != null && !clazz.equals(rclazz)) ||
            (clazz == null && rclazz != null))
         {
            issame = false;
         }
         else if(isnumeric &&
               ((clazz != null && !Number.class.isAssignableFrom(clazz)) ||
               (rclazz != null && !Number.class.isAssignableFrom(rclazz))))
         {
            isnumeric = false;
         }

         if(!(issame || isnumeric)) {
            if(weight == null) {
               clazz = Double.class;
               weight = NUMERIC_MAP.get(clazz);
            }

            Integer rweight = NUMERIC_MAP.get(rclazz);

            if(rweight == null) {
               rclazz = Double.class;
               rweight = NUMERIC_MAP.get(rclazz);
            }

            if(rweight.intValue() > weight.intValue()) {
               clazz = rclazz;
               weight = rweight;
            }
         }
      }

      return clazz;
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
      if(!moreRows(r)) {
         return;
      }

      Row row = getRow(r);
      TableLens table = tables.get(row.getTable());
      table.setObject(row.getRow(), c, v);
   }

   /**
    * Get the current row heights setting. The meaning of row heights
    * depends on the table layout policy setting. If the row height
    * is to be calculated by the ReportSheet based on the content,
    * return -1.
    * @return row height.
    */
   @Override
   public int getRowHeight(int r) {
      if(!moreRows(r)) {
         return -1;
      }

      Row row = getRow(r);
      TableLens table = tables.get(row.getTable());
      return table.getRowHeight(row.getRow());
   }

   /**
    * Get the current column width setting. The meaning of column widths
    * depends on the table layout policy setting. If the column width
    * is to be calculated by the ReportSheet based on the content,
    * return -1. A special value, StyleConstants.REMAINDER, can be returned
    * by this method to indicate that width of this column should be
    * calculated based on the remaining space after all other columns'
    * widths are satisfied. If there are more than one column that return
    * REMAINDER as their widths, the remaining space is distributed
    * evenly among these columns.
    * @return column width.
    */
   @Override
   public int getColWidth(int col) {
      return tables.get(0).getColWidth(col);
   }

   /**
    * Return the color for drawing the row border lines.
    * @param r row number.
    * @param c column number.
    * @return ruling color.
    */
   @Override
   public Color getRowBorderColor(int r, int c) {
      if(!moreRows(r)) {
         return null;
      }

      Row row = getRow(r);
      TableLens table = tables.get(row.getTable());
      return table.getRowBorderColor(row.getRow(), c);
   }

   /**
    * Return the color for drawing the column border lines.
    * @param r row number.
    * @param c column number.
    * @return ruling color.
    */
   @Override
   public Color getColBorderColor(int r, int c) {
      if(!moreRows(r)) {
         return null;
      }

      Row row = getRow(r);
      TableLens table = tables.get(row.getTable());
      return table.getColBorderColor(row.getRow(), c);
   }

   /**
    * Return the style for bottom border of the specified cell. The flag
    * must be one of the style options defined in the StyleConstants
    * class. If the row number is -1, it's checking the outside ruling
    * on the top.
    * @param r row number.
    * @param c column number.
    * @return ruling flag.
    */
   @Override
   public int getRowBorder(int r, int c) {
      if(!moreRows(r)) {
         return -1;
      }

      Row row = getRow(r);
      TableLens table = tables.get(row.getTable());
      return table.getRowBorder(row.getRow(), c);
   }

   /**
    * Return the style for right border of the specified row. The flag
    * must be one of the style options defined in the StyleConstants
    * class. If the column number is -1, it's checking the outside ruling
    * on the left.
    * @param r row number.
    * @param c column number.
    * @return ruling flag.
    */
   @Override
   public int getColBorder(int r, int c) {
      if(!moreRows(r)) {
         return -1;
      }

      Row row = getRow(r);
      TableLens table = tables.get(row.getTable());
      return table.getColBorder(row.getRow(), c);
   }

   /**
    * Return the cell gap space.
    * @param r row number.
    * @param c column number.
    * @return cell gap space.
    */
   @Override
   public Insets getInsets(int r, int c) {
      if(!moreRows(r)) {
         return null;
      }

      Row row = getRow(r);
      TableLens table = tables.get(row.getTable());
      return table.getInsets(row.getRow(), c);
   }

   /**
    * Return the spanning setting for the cell. If the specified cell
    * is not a spanning cell, it returns null. Otherwise it returns
    * a Dimension object with Dimension.width equals to the number
    * of columns and Dimension.height equals to the number of rows
    * of the spanning cell.
    * @param r row number.
    * @param c column number.
    * @return span cell dimension.
    */
   @Override
   public Dimension getSpan(int r, int c) {
      if(!moreRows(r)) {
         return null;
      }

      Row row = getRow(r);
      TableLens table = tables.get(row.getTable());
      return table.getSpan(row.getRow(), c);
   }

   /**
    * Return the per cell alignment.
    * @param r row number.
    * @param c column number.
    * @return cell alignment.
    */
   @Override
   public int getAlignment(int r, int c) {
      if(!moreRows(r)) {
         return -1;
      }

      Row row = getRow(r);
      TableLens table = tables.get(row.getTable());
      return table.getAlignment(row.getRow(), c);
   }

   /**
    * Return the per cell font. Return null to use default font.
    * @param r row number.
    * @param c column number.
    * @return font for the specified cell.
    */
   @Override
   public Font getFont(int r, int c) {
      if(!moreRows(r)) {
         return null;
      }

      Row row = getRow(r);
      TableLens table = tables.get(row.getTable());
      return table.getFont(row.getRow(), c);
   }

   /**
    * Return the per cell line wrap mode. If the line wrap mode is true,
    * lines are wrapped when the text can not fit on one line. Otherwise
    * the wrapping is never done and any overflow text will be truncated.
    * @param r row number.
    * @param c column number.
    * @return true if line wrapping should be done.
    */
   @Override
   public boolean isLineWrap(int r, int c) {
      if(!moreRows(r)) {
         return false;
      }

      Row row = getRow(r);
      TableLens table = tables.get(row.getTable());
      return table.isLineWrap(row.getRow(), c);
   }

   /**
    * Return the per cell foreground color. Return null to use default
    * color.
    * @param r row number.
    * @param c column number.
    * @return foreground color for the specified cell.
    */
   @Override
   public Color getForeground(int r, int c) {
      if(!moreRows(r)) {
         return null;
      }

      Row row = getRow(r);
      TableLens table = tables.get(row.getTable());
      return table.getForeground(row.getRow(), c);
   }

   /**
    * Return the per cell background color. Return null to use default
    * color.
    * @param r row number.
    * @param c column number.
    * @return background color for the specified cell.
    */
   @Override
   public Color getBackground(int r, int c) {
      if(!moreRows(r)) {
         return null;
      }

      Row row = getRow(r);
      TableLens table = tables.get(row.getTable());
      return table.getBackground(row.getRow(), c);
   }

   /**
    * Get the merged table visitor.
    * @return the merged table visitor.
    */
   protected abstract MergedTable.Visitor getVisitor();

   /**
    * Finalize the set table lens.
    */
   @Override
   protected void finalize() throws Throwable {
      super.finalize();
      dispose();
   }

   /**
    * Dispose the set table lens.
    */
   @Override
   public synchronized void dispose() {
      if(merged != null) {
         merged.dispose();
         merged = null;
      }

      if(rows != null) {
         rows.dispose();
         rows = null;
      }

      for(Iterator<TableLens> i = tables.iterator(); i.hasNext();) {
         TableLens table = i.next();
         table.dispose();
         i.remove();
      }
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
      String identifier = identifiers.getColumnIdentifier(col);
      return identifier == null ?
         tables.get(0).getColumnIdentifier(col) : identifier;
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
    * Get the associated row object of a row index.
    * @param row the specified row index.
    * @return the associated row object.
    */
   protected synchronized Row getRow(int row) {
      if(lastIdx == row) {
         return lastRow;
      }

      if(row < 0) {
         return lastRow = new Row(0, lastIdx = row);
      }

      return lastRow = rows.get(lastIdx = row);
   }

   protected boolean isSetRowsInitialized() {
      return rows != null;
   }

   protected void addSetRow(Row row) {
      rows.add(row);
   }

   protected int getSetRowCount() {
      return rows.size();
   }

   /**
    * Row locates a row in a table.
    */
   protected static final class Row implements Serializable {
      public Row(int table, int row) {
         this.table = table;
         this.row = row;
      }

      public boolean isLeft() {
         return table == 0;
      }

      public boolean isRight() {
         return table == 1;
      }

      public int getTable() {
         return table;
      }

      public int getRow() {
         return row;
      }

      public String toString() {
         return "Row:[" + table + "," + row + "]";
      }

      private final int table;
      private final int row;
   }

   private static final Map<Class<?>, Integer> NUMERIC_MAP = new HashMap<>();

   static {
      NUMERIC_MAP.put(Byte.class, Integer.valueOf(1));
      NUMERIC_MAP.put(Short.class, Integer.valueOf(2));
      NUMERIC_MAP.put(Integer.class, Integer.valueOf(3));
      NUMERIC_MAP.put(Long.class, Integer.valueOf(4));
      NUMERIC_MAP.put(Float.class, Integer.valueOf(5));
      NUMERIC_MAP.put(Double.class, Integer.valueOf(6));
   }

   private TableDataDescriptor descriptor;
   private Map<TableDataPath, Object> mmap = new HashMap<>();

   private XIdentifierContainer identifiers = null;
   private List<TableChangeListener> clisteners = new ArrayList<>();
   private transient TableChangeEvent event;

   private XSwappableObjectList<Row> rows; // rows
   private int ccount;                  // column count
   private MergedTable merged;          // temporary merged table
   private final List<TableLens> tables = new ArrayList<>();
   private boolean completed;           // completed flag
   private volatile boolean cancelled;           // cancelled flag
   private final Lock cancelLock = new ReentrantLock();
   private boolean validated = false;   // validated flag

   // optimization
   private transient Row lastRow = null;
   private transient int lastIdx = -1;

   private static final Logger LOG =
      LoggerFactory.getLogger(SetTableLens.class);
}
