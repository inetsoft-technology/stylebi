/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.uql.avro;

import inetsoft.report.TableDataDescriptor;
import inetsoft.uql.XTable;

import java.io.*;

/**
 * Supports serializing a table using the avro format
 */
public class AvroXTable implements XTable, Externalizable {
   public AvroXTable() {
   }

   public AvroXTable(XTable table) {
      this.table = table;
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
   public Class<?> getColType(int col) {
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
   public void setColumnIdentifier(int col, String identifier) {
      table.setColumnIdentifier(col, identifier);
   }

   @Override
   public TableDataDescriptor getDescriptor() {
      return table.getDescriptor();
   }

   @Override
   public String getReportType() {
      return table.getReportType();
   }

   @Override
   public String getReportName() {
      return table.getReportName();
   }

   @Override
   public void setProperty(String key, Object value) {
      table.setProperty(key, value);
   }

   @Override
   public Object getProperty(String key) {
      return table.getProperty(key);
   }

   @Override
   public boolean isDisableFireEvent() {
      return table.isDisableFireEvent();
   }

   @Override
   public void setDisableFireEvent(boolean disableFireEvent) {
      table.setDisableFireEvent(disableFireEvent);
   }

   @Override
   public void writeExternal(ObjectOutput out) throws IOException {
      AvroXTableSerializer.writeTable(out, table);
   }

   @Override
   public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
      this.table = AvroXTableSerializer.readTable(in);
   }

   private transient XTable table;
}
