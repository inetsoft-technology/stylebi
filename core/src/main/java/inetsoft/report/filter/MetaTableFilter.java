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
package inetsoft.report.filter;

import inetsoft.report.TableFilter;
import inetsoft.report.TableLens;
import inetsoft.report.lens.AbstractTableLens;

import java.awt.*;

public class MetaTableFilter extends AbstractTableLens implements TableFilter {
   public MetaTableFilter(TableLens table) {
      setTable(table);
   }

   @Override
   public int getBaseColIndex(int col) {
      return col;
   }

   @Override
   public int getBaseRowIndex(int row) {
      return row;
   }

   @Override
   public TableLens getTable() {
      return table;
   }

   @Override
   public void invalidate() {
      // do nothing
   }

   @Override
   public boolean moreRows(int row) {
      return table.moreRows(row);
   }

   @Override
   public void setTable(TableLens table) {
      this.table = table;
   }

   @Override
   public int getColCount() {
      return table.getColCount();
   }

   @Override
   public Object getObject(int r, int c) {
      return table.getObject(r, c);
   }

   @Override
   public int getRowCount() {
      return table.getRowCount();
   }

   @Override
   public Class getColType(int col) {
      return table.getColType(col);
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
      return table.getTrailerRowCount();
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
   public void dispose() {
      table.dispose();
   }

   /**
    * @return the report/vs name which this filter was created for,
    * and will be used when insert audit record.
    */
   @Override
   public String getReportName() {
      String name = super.getReportName();
      return name != null ? name : table == null ? null : table.getReportName();
   }

   /**
    * @return the report type which this filter was created for:
    * ExecutionBreakDownRecord.OBJECT_TYPE_REPORT or
    * ExecutionBreakDownRecord.OBJECT_TYPE_VIEWSHEET
    */
   @Override
   public String getReportType() {
      String type = super.getReportType();
      return type != null ? type : table == null ? null : table.getReportType();
   }

   private TableLens table;
}