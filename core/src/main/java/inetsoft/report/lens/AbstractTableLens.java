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
package inetsoft.report.lens;

import inetsoft.report.*;
import inetsoft.report.event.TableChangeEvent;
import inetsoft.report.event.TableChangeListener;
import inetsoft.report.internal.Util;
import inetsoft.report.internal.table.TableFormat;
import inetsoft.uql.*;
import inetsoft.uql.util.XIdentifierContainer;
import inetsoft.util.Tool;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.*;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.text.Format;
import java.util.List;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The AbstractTableLens class provides default implementations for most
 * of the methods defined in the TableLens interface. It allows a TableLens
 * object to be created without the need to implement every methods.
 * The AbstractTableLens has three abstract methods that subclass must
 * implement: getRowCount(), getColCount(), and getObject().
 * <p>
 * The default implementation of AbstractTableLens provides a very plain
 * look of the tabular data, without use of any special color, font, or
 * borders. The user of the AbstractTableLens may choose to leave the
 * default setting of the AbstractTableLens, and combine it with a
 * table style class. In this case the new class acts only as the data
 * provider, while the style class provides the visual styling of the
 * table. Alternatively, the user of AbstractTableLens may selectively
 * overriden the methods to define the look and feel directly in the
 * class definition.
 *
 * @version 6.1
 * @author InetSoft Technology Corp
 */
public abstract class AbstractTableLens implements TableLens {
   /**
    * Constructor.
    */
   public AbstractTableLens() {
      super();
      this.identifiers = new XIdentifierContainer(this);
   }

   /**
    * Set the cell value.
    * @param r row number.
    * @param c column number.
    * @param v cell value.
    */
   @Override
   public void setObject(int r, int c, Object v) {
      throw new RuntimeException("Not implemented method: setObject");
   }

   /**
    * Get internal table data descriptor which contains table structural
    * infos.
    * @return table data descriptor
    */
   @Override
   public TableDataDescriptor getDescriptor() {
      if(descriptor == null) {
         descriptor = new DefaultTableDataDescriptor(this);
      }

      return descriptor;
   }

   /**
    * Get drill info with the specified row and col.
    * @return the drill info.
    */
   @Override
   public final XDrillInfo getXDrillInfo(int row, int col) {
      TableDataDescriptor descriptor = getDescriptor();

      if(!descriptor.containsDrill()) {
         return null;
      }

      TableDataPath path = descriptor.getCellDataPath(row, col);
      XMetaInfo minfo = descriptor.getXMetaInfo(path);

      return minfo == null ? null : minfo.getXDrillInfo();
   }

   /**
    * Add table change listener to the filtered table.
    * If the table filter's data changes, a TableChangeEvent will be triggered
    * for the TableChangeListener to process.
    * @param listener the specified TableChangeListener
    */
   @Override
   public void addChangeListener(TableChangeListener listener) {
      clisteners.add(new WeakReference<>(listener));
   }

   /**
    * Remove table change listener from the filtered table.
    * @param listener the specified TableChangeListener to be removed
    */
   @Override
   public void removeChangeListener(TableChangeListener listener) {
      clisteners.removeIf(ref -> ref.get() == listener);
   }

   /**
    * Clone the AbstractTableLens.
    */
   @Override
   public AbstractTableLens clone() {
      try {
         AbstractTableLens table = (AbstractTableLens) super.clone();
         table.event = null;
         table.descriptor = null;
         table.clisteners = new CopyOnWriteArrayList<>();
         table.identifiers = (XIdentifierContainer) identifiers.clone();
         table.identifiers.setTable(table);
         table.setLeftAlign(isLeftAlign);

         return table;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone object", ex);
      }

      return null;
   }

   /**
    * Fire change event when filtered table changed.
    */
   protected void fireChangeEvent() {
      if(isDisableFireEvent()) {
         return;
      }

      // reuse event object for optimization reason
      if(event == null) {
         event = new TableChangeEvent(this);
      }

      try {
         // @by larryl, should clone here in case the listener is changed
         // during change event. But currently we don't and it is expensive.
         List<Reference<TableChangeListener>> vec = clisteners;

         for(Reference<TableChangeListener> ref : vec) {
            TableChangeListener listener = ref.get();

            if(listener != null) {
               listener.tableChanged(event);
            }
         }
      }
      catch(Exception ex) {
         LOG.error("Failed to process change event", ex);
      }
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
      return row < getRowCount();
   }

   /**
    * Return the number of rows on the top of the table to be treated
    * as header rows.
    * @return number of header rows.
    */
   @Override
   public int getHeaderRowCount() {
      return 0;
   }

   /**
    * Return the number of columns on the left of the table to be
    * treated as header columns.
    */
   @Override
   public int getHeaderColCount() {
      return 0;
   }

   /**
    * Return the number of rows on the bottom of the table to be treated
    * as tail rows.
    * @return number of header rows.
    */
   @Override
   public int getTrailerRowCount() {
      return 0;
   }

   /**
    * Return the number of columns on the right of the table to be
    * treated as tail columns.
    */
   @Override
   public int getTrailerColCount() {
      return 0;
   }

   /**
    * Get the current row heights setting. The meaning of row heights
    * depends on the table layout policy setting. If the row height
    * is to be calculated by the ReportSheet based on the content,
    * return -1.
    * @return row height.
    */
   @Override
   public int getRowHeight(int row) {
      return -1;
   }

   /**
    * Get the current column width setting. The meaning of column widths
    * depends on the table layout policy setting. If the column width
    * is to be calculated by the ReportSheet based on the content,
    * return -1.
    * @return column width.
    */
   @Override
   public int getColWidth(int col) {
      return -1;
   }

   /**
    * Get the current column content type.
    * @param col column number.
    * @return column type.
    */
   @Override
   public synchronized Class<?> getColType(int col) {
      Class type = columnTypes.get(col);

      if(type == null) {
         int nrows = getRowCount();

         // if the table is not fully loaded, don't wait for more rows
         // otherwise it may create a deadlock
         if(nrows < 0) {
            // @by stephenwebster, For Bug #3344
            // While streaming MV data, table data is added block by block,
            // @see XMapTaskPool.StreamHandler.run().
            // MVQuery creates the initial table based on the first block, and
            // sets the XMetaInfo. If the first block is less then the minimum rows
            // then a deadlock would occur since moreRows would be waiting for
            // the table to be completed, and the next block would be waiting
            // for the first block to be added. Thus, the table could not reach
            // its completed state.  Use the current number of available
            // rows to determine column type instead, so the first result can
            // be added and the next block can be added.
            nrows = Math.min(5000, -(nrows + 1) - 1);
         }
         else {
            nrows = 5000;
         }

         // unknow column type, the first table is empty (in mv streaming) and
         // we can't wait for it (see comments above)
         /* PagedTableLens will return col type (getColType) directly now without
         going into this method. so table created for MV streaming should no longer
         reach this point.
         if(nrows == 0 && getRowCount() < 0) {
            return String.class;
         }
         */

         type = Util.getColType(this, col, null, nrows);
         columnTypes.put(col, type);
      }

      return type;
   }

   /**
    * Return the color for drawing the row border lines.
    * @param r row number.
    * @param c column number.
    * @return ruling color.
    */
   @Override
   public Color getRowBorderColor(int r, int c) {
      return Color.black;
   }

   /**
    * Return the color for drawing the column border lines.
    * @param r row number.
    * @param c column number.
    * @return ruling color.
    */
   @Override
   public Color getColBorderColor(int r, int c) {
      return Color.black;
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
      return THIN_LINE;
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
      return THIN_LINE;
   }

   /**
    * Return the cell gap space.
    * @param r row number.
    * @param c column number.
    * @return cell gap space.
    */
   @Override
   public Insets getInsets(int r, int c) {
      return null;
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
      return null;
   }

   /**
    * Return the per cell alignment.
    * @param r row number.
    * @param c column number.
    * @return cell alignment.
    */
   @Override
   public int getAlignment(int r, int c) {
      if(!isLeftAlign && Tool.isNumberClass(getColType(c))) {
         return H_RIGHT | V_CENTER;
      }

      return H_LEFT | V_CENTER;
   }

   /**
    * Return the per cell user alignment.
    * @param r row number.
    * @param c column number.
    * @return cell alignment.
    * @hidden
    */
   protected int getUserAlignment(int r, int c) {
      return -1;
   }

   /**
    * Return the per cell font. Return null to use default font.
    * @param r row number.
    * @param c column number.
    * @return font for the specified cell.
    */
   @Override
   public Font getFont(int r, int c) {
      return null;
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
      return true;
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
      return null;
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
      return null;
   }

   /**
    * Return the per cell format.
    * @param row row number.
    * @param col column number.
    * @return format for the specified cell.
    */
   @Override
   public final Format getDefaultFormat(int row, int col) {
      return getDefaultFormat(getDescriptor().getCellDataPath(row, col));
   }

   public Format getDefaultFormat(TableDataPath path) {
      TableDataDescriptor descriptor = getDescriptor();

      if(!descriptor.containsFormat() && Util.getNestedTable(this, CalcTableLens.class) == null) {
         return null;
      }

      if(path == null) {
         return null;
      }

      XMetaInfo minfo = descriptor.getXMetaInfo(path);
      Format format = null;

      if(minfo != null) {
         XFormatInfo finfo = minfo.getXFormatInfo();
         format = finfo == null ? null : TableFormat.getFormat(
            finfo.getFormat(), finfo.getFormatSpec(), local);
      }

      return format;
   }

   /**
    * Set the locale.
    */
   public void setLocal(Locale loc) {
      this.local = loc;
   }

   /**
    * Check if contains format.
    * @return true if contains format.
    */
   @Override
   public final boolean containsFormat() {
      return getDescriptor().containsFormat();
   }

   /**
    * Check if contains drill.
    * @return true if contains drill.
    */
   @Override
   public final boolean containsDrill() {
      return getDescriptor().containsDrill();
   }

   /**
    * Dispose the table to clear up temporary resources.
    */
   @Override
   public void dispose() {
      // do nothing
   }

   /**
    * Get the column identifier of a column.
    * @param col the specified column index.
    * @return the column identifier of the column. The identifier might be
    * different from the column name, for it may contain more locating
    * information than the column name.
    */
   @Override
   public String getColumnIdentifier(int col) {
      return col >= 0 ? identifiers.getColumnIdentifier(col) : null;
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
    * Find the column by identifier.
    */
   public final int findColumnByIdentifier(String identifier) {
      return identifiers.indexOfColumnIdentifier(identifier);
   }

   /**
    * Check if the value at one cell is null.
    * @param r the specified row index.
    * @param c column number.
    * @return <tt>true</tt> if null, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isNull(int r, int c) {
      return getObject(r, c) == null;
   }

   /**
    * Check if is primitive.
    * @return <tt>true</tt> if is primitive, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isPrimitive(int col) {
      return false;
   }

   /**
    * Get the double value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the double value in the specified row.
    */
   @Override
   public double getDouble(int r, int c) {
      Object val = getObject(r, c);
      return (val instanceof Number) ? ((Number) val).doubleValue() : Tool.NULL_DOUBLE;
   }

   /**
    * Get the float value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the float value in the specified row.
    */
   @Override
   public float getFloat(int r, int c) {
      Object val = getObject(r, c);
      return (val instanceof Number) ? ((Number) val).floatValue() : Tool.NULL_FLOAT;
   }

   /**
    * Get the long value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the long value in the specified row.
    */
   @Override
   public long getLong(int r, int c) {
      Object val = getObject(r, c);
      return (val instanceof Number) ? ((Number) val).longValue() : Tool.NULL_LONG;
   }

   /**
    * Get the int value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the int value in the specified row.
    */
   @Override
   public int getInt(int r, int c) {
      Object val = getObject(r, c);
      return (val instanceof Number) ? ((Number) val).intValue() : Tool.NULL_INTEGER;
   }

   /**
    * Get the short value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the short value in the specified row.
    */
   @Override
   public short getShort(int r, int c) {
      Object val = getObject(r, c);
      return (val instanceof Number) ? ((Number) val).shortValue() : Tool.NULL_SHORT;
   }

   /**
    * Get the byte value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the byte value in the specified row.
    */
   @Override
   public byte getByte(int r, int c) {
      Object val = getObject(r, c);
      return (val instanceof Number) ? ((Number) val).byteValue() : Tool.NULL_BYTE;
   }

   /**
    * Get the boolean value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the boolean value in the specified row.
    */
   @Override
   public boolean getBoolean(int r, int c) {
      Object val = getObject(r, c);
      return (val instanceof Number) && ((Number) val).doubleValue() != 0;
   }

   /**
    * Set is left align true or false.
    */
   public void setLeftAlign(boolean isLeft) {
      isLeftAlign = isLeft;
   }

   /**
    * Get the value of a property.
    * @param key the specified property name.
    * @return the value of the property.
    */
   @Override
   public Object getProperty(String key) {
      return prop != null ? prop.get(key) : null;
   }

   /**
    * Set the value a property.
    * @param key the property name.
    * @param value the property value, null to remove the property.
    */
   @Override
   public void setProperty(String key, Object value) {
      if(value != null) {
         synchronized(this) {
            if(prop == null) {
               prop = Object2ObjectMaps.synchronize(new Object2ObjectOpenHashMap<>());
            }
         }

         prop.put(key, value);
      }
      else if(prop != null) {
         prop.remove(key);
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
    * Whether table is snapshot.
    * @return
    */
   public boolean isSnapshot() {
      return false;
   }

   /**
    * Set if need to disabled fireevent.
    */
   @Override
   public void setDisableFireEvent(boolean disableFireEvent) {
      this.disabledFireEvent = disableFireEvent;

      if(this instanceof TableFilter) {
         TableFilter filter = (TableFilter) this;
         TableLens table = filter.getTable();

         if(table != null) {
            table.setDisableFireEvent(disableFireEvent);
         }
      }
   }

   /**
    * Check if fire event is disabled.
    */
   @Override
   public boolean isDisableFireEvent() {
      return disabledFireEvent;
   }

   @Serial
   private void readObject(ObjectInputStream in) throws ClassNotFoundException, IOException {
      in.defaultReadObject();
      clisteners = new CopyOnWriteArrayList<>();
   }

   protected boolean isLeftAlign = false;
   protected boolean disabledFireEvent = false;
   protected TableDataDescriptor descriptor;
   private XIdentifierContainer identifiers;
   protected Map<Integer, Class<?>> columnTypes = new HashMap<>();
   private transient List<Reference<TableChangeListener>> clisteners = new CopyOnWriteArrayList<>();
   private transient TableChangeEvent event;
   private Locale local = Locale.getDefault();
   private Map<String, Object> prop = null;;
   private static final Logger LOG =
      LoggerFactory.getLogger(AbstractTableLens.class);
}
