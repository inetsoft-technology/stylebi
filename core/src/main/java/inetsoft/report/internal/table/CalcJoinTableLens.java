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
package inetsoft.report.internal.table;

import inetsoft.report.TableDataDescriptor;
import inetsoft.report.TableLens;
import inetsoft.report.event.TableChangeListener;
import inetsoft.uql.XDrillInfo;
import inetsoft.uql.XTable;

import java.awt.*;
import java.text.Format;
import java.util.Properties;

/**
 * Table lens that holds the table lens objects for any additional sources bound
 * to a calc table.
 *
 * @author InetSoft Technology
 * @since  11.1
 */
public class CalcJoinTableLens implements TableLens {
   /**
    * Creates a new instance of <tt>CalcJoinTableLens</tt>.
    *
    * @param table  the main table.
    * @param tables the additional tables.
    */
   public CalcJoinTableLens(TableLens table, TableLens[] tables) {
      this.table = table;
      this.tables = tables;
   }

   public TableLens[] getTables() {
      return tables;
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
      return table.getObject(r, c);
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
      return table.getByte(r, c);
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
   public Class getColType(int col) {
      return table.getColType(col);
   }

   @Override
   public boolean isPrimitive(int col) {
      return table.isPrimitive(col);
   }

   @Override
   public void dispose() {
      table.dispose();
   }

   @Override
   public String getColumnIdentifier(int col) {
      return table.getColumnIdentifier(col);
   }

   @Override
   public TableDataDescriptor getDescriptor() {
      return table.getDescriptor();
   }

   @Override
   public void addChangeListener(TableChangeListener listener) {
      table.addChangeListener(listener);
   }

   @Override
   public void setColumnIdentifier(int col, String identifier) {
      table.setColumnIdentifier(col, identifier);
   }

   @Override
   public void removeChangeListener(TableChangeListener listener) {
      table.removeChangeListener(listener);
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

   @Override
   public Format getDefaultFormat(int row, int col) {
      return table.getDefaultFormat(row, col);
   }

   @Override
   public XDrillInfo getXDrillInfo(int r, int c) {
      return table.getXDrillInfo(r, c);
   }

   @Override
   public boolean containsFormat() {
      return table.containsFormat();
   }

   @Override
   public boolean containsDrill() {
      return table.containsDrill();
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
      Object value = getReportProperty(XTable.REPORT_NAME);
      return value == null ? null : value + "";
   }

   /**
    * @return the report type which this filter was created for:
    * ExecutionBreakDownRecord.OBJECT_TYPE_REPORT or
    * ExecutionBreakDownRecord.OBJECT_TYPE_VIEWSHEET
    */
   @Override
   public String getReportType() {
      Object value = getReportProperty(XTable.REPORT_TYPE);
      return value == null ? null : value + "";
   }

   public Object getReportProperty(String key) {
      Object value = getProperty(key);
      value = value != null ? value : table != null ? table.getProperty(key) : null;

      if(value == null && tables != null) {
         for(int i = 0; i < tables.length; i++) {
            value = tables[i].getProperty(key);

            if(value != null) {
               break;
            }
         }
      }

      return value;
   }

   private final TableLens table;
   private final TableLens[] tables;
   private Properties prop = new Properties(); // properties
}
