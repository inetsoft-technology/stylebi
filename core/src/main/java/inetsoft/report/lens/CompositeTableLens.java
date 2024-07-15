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
package inetsoft.report.lens;

import inetsoft.report.*;
import inetsoft.report.filter.DefaultTableChangeListener;
import inetsoft.uql.XTable;

import java.awt.*;

/**
 * CompositeTableLens can be used to combine two tables into one table.
 * This is useful when two tables need to be printed side by side. A
 * gap space can be specified to get added between the two tables.
 * <p>
 * This class is used in StyleSheet to print two tables side by side.
 * For TabularSheet, tables can be placed in two parallel columns to
 * print tables side by side. It is normally not necessary to use
 * CompositeTableLens.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class CompositeTableLens extends AbstractTableLens
   implements TableFilter {

   /**
    * Create a composite table from two tables.
    * @param table1 left side table.
    * @param table2 right side table.
    */
   public CompositeTableLens(TableLens table1, TableLens table2) {
      setTable(table1, table2);
   }

   /**
    * Get the original table of this filter.
    */
   @Override
   public TableLens getTable() {
      return this;
   }

   /**
    * Set the base table of this filter.
    */
   @Override
   public void setTable(TableLens table) {
      // do nothing
   }

   /**
    * Set the table tables of the composite table lens.
    * @param table1 base table lens 1
    * @param table2 base table lens 2
    */
   public void setTable(TableLens table1, TableLens table2) {
      this.table[0] = table1;
      this.table[1] = table2;
      invalidate();
      this.table[0].addChangeListener(new DefaultTableChangeListener(this));
      this.table[1].addChangeListener(new DefaultTableChangeListener(this));
   }

   /**
    * Invalidate the table filter forcely, and the table filter will perform
    * filtering calculation to validate itself.
    */
   @Override
   public void invalidate() {
      fireChangeEvent();
   }

   /**
    * Get the base table row index corresponding to the filtered table.
    * If the row does not exist in the base table, it returns -1.
    * @param row row index in the filtered table.
    * @return corresponding row index in the base table.
    */
   @Override
   public int getBaseRowIndex(int row) {
      return row;
   }

   /**
    * Get the base table column index corresponding to the filtered table.
    * If the column does not exist in the base table, it returns -1.
    * @param col column index in  the filtered table.
    * @return corresponding column index in the bast table.
    */
   @Override
   public int getBaseColIndex(int col) {
      return col;
   }

   /**
    * Set the gap space between the two table.
    * @param gap gap space between tables.
    */
   public void setGap(int gap) {
      this.gap = gap;
   }

   /**
    * Get the gap space between tables.
    * @return gap space.
    */
   public int getGap() {
      return gap;
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
      return table[0].moreRows(row) || table[1].moreRows(row);
   }

   /**
    * Return the number of rows in the table. The number of rows includes
    * the header rows.
    * @return number of rows in table.
    */
   @Override
   public int getRowCount() {
      int cnt1 = table[0].getRowCount();
      int cnt2 = table[1].getRowCount();

      if(cnt1 < 0 || cnt2 < 0) {
         return -1;
      }

      return Math.max(cnt1, cnt2);
   }

   /**
    * Return the number of columns in the table. The number of columns
    * includes the header columns.
    * @return number of columns in table.
    */
   @Override
   public int getColCount() {
      return table[0].getColCount() + table[1].getColCount() +
         ((gap > 0) ? 1 : 0);
   }

   /**
    * Return the number of rows on the top of the table to be treated
    * as header rows.
    * @return number of header rows.
    */
   @Override
   public int getHeaderRowCount() {
      return Math.min(table[0].getHeaderRowCount(),
         table[1].getHeaderRowCount());
   }

   /**
    * Return the number of columns on the left of the table to be
    * treated as header columns.
    */
   @Override
   public int getHeaderColCount() {
      return table[0].getHeaderRowCount();
   }

   /**
    * Return the number of rows on the bottom of the table to be treated
    * as tail rows.
    * @return number of tail rows.
    */
   @Override
   public int getTrailerRowCount() {
      return Math.min(table[0].getTrailerRowCount(),
         table[1].getTrailerRowCount());
   }

   /**
    * Return the number of columns on the right of the table to be
    * treated as tail columns.
    */
   @Override
   public int getTrailerColCount() {
      return table[1].getTrailerRowCount();
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
      int rh1 = (row < table[0].getRowCount()) ?
         table[0].getRowHeight(row) :
         -1;
      int rh2 = (row < table[1].getRowCount()) ?
         table[1].getRowHeight(row) :
         -1;

      return Math.max(rh1, rh2);
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
      if(col < table[0].getColCount()) {
         return table[0].getColWidth(col);
      }

      col -= table[0].getColCount();

      if(col == 0 && gap > 0) {
         return gap;
      }

      return table[1].getColWidth(col - ((gap > 0) ? 1 : 0));
   }

   /**
    * Get the current column content type.
    * @param col column number.
    * @return column type.
    */
   @Override
   public Class getColType(int col) {
      if(col < table[0].getColCount()) {
         return table[0].getColType(col);
      }

      col -= table[0].getColCount();

      if(col == 0 && gap > 0) {
         return String.class;
      }

      return table[1].getColType(col - ((gap > 0) ? 1 : 0));
   }

   /**
    * Return the color for drawing the row border lines.
    * @param r row number.
    * @param c column number.
    * @return ruling color.
    */
   @Override
   public Color getRowBorderColor(int r, int c) {
      Index idx = getIndex(r, c, new Point(0, 1));

      if(idx == null) {
         return null;
      }

      return table[idx.table].getRowBorderColor(idx.row, idx.col);
   }

   /**
    * Return the color for drawing the column border lines.
    * @param r row number.
    * @param c column number.
    * @return ruling color.
    */
   @Override
   public Color getColBorderColor(int r, int c) {
      Index idx = getIndex(r, c, new Point(1, 0));

      if(idx == null) {
         return null;
      }

      return table[idx.table].getColBorderColor(idx.row, idx.col);
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
      Index idx = getIndex(r, c, new Point(0, 1));

      if(idx == null) {
         return StyleConstants.NONE;
      }

      return table[idx.table].getRowBorder(idx.row, idx.col);
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
      Index idx = getIndex(r, c, new Point(1, 0));

      if(idx == null) {
         return StyleConstants.NONE;
      }

      return table[idx.table].getColBorder(idx.row, idx.col);
   }

   /**
    * Return the cell gap space.
    * @param r row number.
    * @param c column number.
    * @return cell gap space.
    */
   @Override
   public Insets getInsets(int r, int c) {
      Index idx = getIndex(r, c, new Point(0, 0));

      if(idx == null) {
         return null;
      }

      return table[idx.table].getInsets(idx.row, idx.col);
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
      Index idx = getIndex(r, c, new Point(0, 0));

      if(idx == null) {
         return null;
      }

      return table[idx.table].getSpan(idx.row, idx.col);
   }

   /**
    * Return the per cell alignment.
    * @param r row number.
    * @param c column number.
    * @return cell alignment.
    */
   @Override
   public int getAlignment(int r, int c) {
      Index idx = getIndex(r, c, new Point(0, 0));

      if(idx == null) {
         return StyleConstants.H_CENTER;
      }

      return table[idx.table].getAlignment(idx.row, idx.col);
   }

   /**
    * Return the per cell font. Return null to use default font.
    * @param r row number.
    * @param c column number.
    * @return font for the specified cell.
    */
   @Override
   public Font getFont(int r, int c) {
      Index idx = getIndex(r, c, new Point(0, 0));

      if(idx == null) {
         return null;
      }

      return table[idx.table].getFont(idx.row, idx.col);
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
      Index idx = getIndex(r, c, new Point(0, 0));

      if(idx == null) {
         return false;
      }

      return table[idx.table].isLineWrap(idx.row, idx.col);
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
      Index idx = getIndex(r, c, new Point(0, 0));

      if(idx == null) {
         return null;
      }

      return table[idx.table].getForeground(idx.row, idx.col);
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
      Index idx = getIndex(r, c, new Point(0, 0));

      if(idx == null) {
         return null;
      }

      return table[idx.table].getBackground(idx.row, idx.col);
   }

   @Override
   public int getAlpha(int r, int c) {
      Index idx = getIndex(r, c, new Point(0, 0));

      if(idx == null) {
         return -1;
      }

      return table[idx.table].getAlpha(idx.row, idx.col);
   }

   /**
    * Return the value at the specified cell.
    * @param r row number.
    * @param c column number.
    * @return the value at the location.
    */
   @Override
   public Object getObject(int r, int c) {
      Index idx = getIndex(r, c, new Point(0, 0));

      if(idx == null) {
         return null;
      }

      return table[idx.table].getObject(idx.row, idx.col);
   }

   /**
    * Set the cell value.
    * @param r row number.
    * @param c column number.
    * @param v cell value.
    */
   @Override
   public void setObject(int r, int c, Object v) {
      Index idx = getIndex(r, c, new Point(0, 0));

      if(idx != null) {
         table[idx.table].setObject(idx.row, idx.col, v);
      }
   }

   /**
    * Dispose the table to clear up temporary resources.
    */
   @Override
   public void dispose() {
      if(table[0] != null) {
         table[0].dispose();
      }

      if(table[1] != null) {
         table[1].dispose();
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
      String identifier = super.getColumnIdentifier(col);
      TableLens temp = col < table[0].getColCount() ? table[0] : table[1];
      col = col < table[0].getColCount() ? col : col - table[0].getColCount();

      return identifier == null ? temp.getColumnIdentifier(col) : identifier;
   }

   /**
    * border is a Point object, where Point.x is the number of columns
    * allowed right of a table. It's set to 1 when dealing with column
    * borders, and 0 otherwise. The Point.y is the same for row borders.
    */
   Index getIndex(int row, int col, Point border) {
      Index idx = new Index();

      if(col < table[0].getColCount()) {
         idx.table = 0;
         idx.col = col;
      }
      else {
         col -= table[0].getColCount() + ((gap > 0) ? 1 : 0);

         if(col + border.x >= 0) {
            idx.table = 1;
            idx.col = col;
         }
         else {
            return null;
         }
      }

      if(table[idx.table].moreRows(row)) {
         idx.row = row;
         return idx;
      }

      return null;
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
      Object value = super.getProperty(key);

      if(value == null && table != null) {
         for(int i = 0; i < table.length; i++) {
            value = table[i].getProperty(key);

            if(value != null) {
               break;
            }
         }
      }

      return value;
   }

   /**
    * The index into a table.
    */
   static class Index {
      int table;
      int row;
      int col;
      public String toString() {
         return "Index[" + table + " @ " + row + "," + col + "]";
      }
   }

   private TableLens[] table = new TableLens[2];
   private int gap = 10;
}
