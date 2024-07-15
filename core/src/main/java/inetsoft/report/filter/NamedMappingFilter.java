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
package inetsoft.report.filter;

import inetsoft.report.*;
import inetsoft.report.internal.table.CachedTableLens;
import inetsoft.report.internal.table.TableFormat;
import inetsoft.report.lens.AbstractTableLens;
import inetsoft.uql.XFormatInfo;
import inetsoft.uql.XMetaInfo;
import inetsoft.uql.util.XUtil;
import inetsoft.util.*;

import java.awt.*;
import java.text.Format;
import java.text.SimpleDateFormat;

/**
 * Used to apply namedgroup.
 */
public class NamedMappingFilter extends AbstractTableLens implements TableFilter,
   Cloneable, CachedTableLens
{
   public NamedMappingFilter(TableLens table) {
      super();
      setTable(table);
   }

   @Override
   public TableLens getTable() {
      return table;
   }

   @Override
   public void setTable(TableLens table) {
      this.table = table;
      this.ordermap = new SortOrder[table.getColCount()];
      invalidate();
      this.table.addChangeListener(new DefaultTableChangeListener(this));
   }

   /**
    * Set the specific group order information.
    * @param col group columns.
    * @param order group order.
    */
   public void setGroupOrder(int col, SortOrder order) {
      ordermap[col] = order;
   }

   /**
    * Get the specific group order information.
    * @param col group columns.
    */
   public SortOrder getGroupOrder(int col) {
      return ordermap[col];
   }

   /**
    * Invalidate the table filter forcely, and the table filter will perform
    * filtering calculation to validate itself.
    */
   @Override
   public synchronized void invalidate() {
      clearCache();
      fireChangeEvent();
   }

   @Override
   public int getBaseRowIndex(int row) {
      return row;
   }

   @Override
   public int getBaseColIndex(int col) {
      return col;
   }

   @Override
   public void clearCache() {
      if(cellValues != null) {
         cellValues.clear();
      }
   }

   @Override
   public boolean moreRows(int row) {
      return table.moreRows(row);
   }

   @Override
   public int getRowCount() {
      return table.getRowCount();
   }

   @Override
   public int getColCount() {
      return table.getColCount();
   }

   @Override
   public int getHeaderRowCount() {
      return table.getHeaderRowCount();
   }

   @Override
   public int getHeaderColCount() {
      return table.getHeaderColCount();
   }

   @Override
   public int getTrailerRowCount() {
      return table.getTrailerRowCount();
   }

   @Override
   public int getTrailerColCount() {
      return table.getTrailerColCount();
   }

   @Override
   public boolean isNull(int r, int c) {
      return table.isNull(r, c);
   }

   @Override
   public Object getObject(int r, int c) {
      Object val = table.getObject(r, c);
      SortOrder order = getGroupOrder(c);
      Format[] formats = new Format[getColCount()];
      boolean[] processed = new boolean[getColCount()];
      boolean isDateColumn = Tool.isDateClass(table.getColType(c));

      if(r >= getHeaderRowCount() && order != null && order.isSpecific()) {
         if(cellValues == null) {
            cellValues = new SparseMatrix();
         }

         Object cache = cellValues.get(r, c);

         if(cache != NULL) {
            return cache;
         }

         int grp = order.findGroup(new Object[] {val});

         // find named group
         if(grp != -1) {
            val = order.getGroupName(grp);
         }
         // doesn't find named group, but should use others label
         else if(order.getOthers() == SortOrder.GROUP_OTHERS) {
            val = Catalog.getCatalog().getString("Others");
         }
         else {
            if(val != null && isDateColumn) {
               order.compare(null, val);

               if(order.getGroupDate() != null) {
                  val = order.getGroupDate();
               }

               val = format(formats, processed, val, c);
            }

            // convert to string, make sure all datas in the column is same type
            val = val == null ? null : Tool.toString(val);
         }

         cellValues.set(r, c, val);
      }

      return val;
   }

   /**
    * Format a value.
    */
   private Object format(Format[] formats, boolean[] processed, Object val, int c) {
      try {
         if(!processed[c]) {
            processed[c] = true;

            if(minfos == null) {
               createXMetaInfo();
            }

            XMetaInfo info = minfos[c];

            if(info != null) {
               XFormatInfo fmt = info.getXFormatInfo();

               if(fmt != null) {
                  formats[c] = TableFormat.getFormat(fmt.getFormat(), fmt.getFormatSpec());
               }
            }
         }

         if(formats[c] != null) {
            val = XUtil.format(formats[c], val);
         }
      }
      catch(Exception ex) {
         // ignore it
      }

      return val;
   }

   /**
    * Create the meta info.
    */
   private void createXMetaInfo() {
      boolean[] dcol = new boolean[table.getColCount()];

      for(int i = 0; i < dcol.length; i++) {
         dcol[i] = Tool.isDateClass(table.getColType(i));
      }

      minfos = new XMetaInfo[table.getColCount()];

      for(int col = 0; col < minfos.length; col++) {
         SortOrder order = getGroupOrder(col);

         if(order == null) {
            minfos[col] = null;
            continue;
         }

         int level = order.getOption();

         // such as Month of Year also create format info, but if it is
         // aggregate column, will be removed later in getXMetaInfo
         // fix bug1261032688280
         // not date type, not part date level?
         if(!dcol[col] && !(Tool.isNumberClass(table.getColType(col)) &&
            (level & SortOrder.PART_DATE_GROUP) != 0))
         {
            minfos[col] = null;
            continue;
         }

         XMetaInfo minfo = new XMetaInfo();

         if(XUtil.getDefaultDateFormat(level) == null) {
            minfo.setXFormatInfo(new XFormatInfo());
            minfos[col] = minfo;
            continue;
         }

         String dtype = Tool.getDataType(table.getColType(col));
         SimpleDateFormat dfmt = XUtil.getDefaultDateFormat(level, dtype);

         if(dfmt != null) {
            String fmt = dfmt.toPattern();
            minfo.setXFormatInfo(new XFormatInfo(TableFormat.DATE_FORMAT, fmt));
            minfo.setProperty("autoCreatedFormat", "true");
            mexisting = true;
            minfos[col] = minfo;
         }
      }
   }

   @Override
   public double getDouble(int r, int c) {
      return table.getDouble(r, c);
   }

   @Override
   public float getFloat(int r, int c) {
      return table.getFloat(r, c);
   }

   @Override
   public long getLong(int r, int c) {
      return table.getLong(r, c);
   }

   @Override
   public int getInt(int r, int c) {
      return table.getInt(r, c);
   }

   @Override
   public short getShort(int r, int c) {
      return table.getShort(r, c);
   }

   @Override
   public byte getByte(int r, int c) {
      return 0;
   }

   @Override
   public boolean getBoolean(int r, int c) {
      return table.getBoolean(r, c);
   }

   @Override
   public void setObject(int r, int c, Object v) {
      table.setObject(r, c, v);
   }

   @Override
   public Class<?> getColType(int col) {
      SortOrder order = getGroupOrder(col);

      if(order != null && order.isSpecific()) {
         return String.class;
      }

      return table.getColType(col);
   }

   @Override
   public boolean isPrimitive(int col) {
      return table.isPrimitive(col);
   }

   @Override
   public void dispose() {
      table.dispose();

      if(cellValues != null) {
         cellValues.clear();
      }
   }

   @Override
   public String getColumnIdentifier(int col) {
      return table.getColumnIdentifier(col);
   }

   @Override
   public void setColumnIdentifier(int col, String identifier) {
      table.setColumnIdentifier(col, identifier);
   }

   @Override
   public TableDataDescriptor getDescriptor() {
      return table.getDescriptor();
   }

   @Override
   public int getRowHeight(int row) {
      return table.getRowHeight(row);
   }

   @Override
   public int getColWidth(int col) {
      return table.getColWidth(col);
   }

   @Override
   public Color getRowBorderColor(int r, int c) {
      return table.getRowBorderColor(r, c);
   }

   @Override
   public Color getColBorderColor(int r, int c) {
      return table.getColBorderColor(r, c);
   }

   @Override
   public int getRowBorder(int r, int c) {
      return table.getRowBorder(r, c);
   }

   @Override
   public int getColBorder(int r, int c) {
      return table.getColBorder(r, c);
   }

   @Override
   public Insets getInsets(int r, int c) {
      return table.getInsets(r, c);
   }

   @Override
   public Dimension getSpan(int r, int c) {
      return table.getSpan(r, c);
   }

   @Override
   public int getAlignment(int r, int c) {
      return table.getAlignment(r, c);
   }

   @Override
   public Font getFont(int r, int c) {
      return table.getFont(r, c);
   }

   @Override
   public boolean isLineWrap(int r, int c) {
      return table.isLineWrap(r, c);
   }

   @Override
   public Color getForeground(int r, int c) {
      return table.getForeground(r, c);
   }

   @Override
   public Color getBackground(int r, int c) {
      return table.getBackground(r, c);
   }

   private TableLens table;
   private SortOrder[] ordermap = null;
   private XMetaInfo[] minfos;
   private boolean mexisting;
   private transient SparseMatrix cellValues;
}