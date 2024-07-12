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
package inetsoft.report.internal.table;

import inetsoft.report.internal.ObjectCache;
import inetsoft.report.lens.AbstractTableLens;

import java.awt.*;

/**
 * The RegionTableLens class records data for a region in a table.
 *
 * @version 9.1
 * @author InetSoft Technology Corp
 */
public class RegionTableLens extends AbstractTableLens
   implements java.io.Serializable
{
   /**
    * Create a table width specified number of rows and columns.
    * @param rows number of rows.
    * @param cols number of columns.
    */
   public RegionTableLens(int rows, int cols, int hrow, int hcol,
      Rectangle region)
   {
      this.nrow = rows;
      this.ncol = cols;
      this.hrow = hrow;
      this.hcol = hcol;
      this.region = region;

      cells =
         new TableCellInfo[region.height + hrow + 3][region.width + hcol + 3];
      values =
         createCellArray(region.height + hrow + 3, region.width + hcol + 3);
      rowHeights = new short[region.height + hrow + 3];
      colWidths = new int[region.width + hcol + 3];
   }

   /**
    * Create an array to hold the cell data.
    */
   protected Object[][] createCellArray(int rows, int cols) {
      return new Object[rows][cols];
   }

   /**
    * Get the cell info of a cell.
    */
   public TableCellInfo getTableCellInfo(int r, int c) {
      return cells[rowN(r)][colN(c)];
   }

   /**
    * Set the cell info of a cell.
    */
   public void setTableCellInfo(int r, int c, TableCellInfo info) {
      cells[rowN(r)][colN(c)] = info;
   }

   /**
    * Return the number of rows in the table. The number of rows includes
    * the header rows.
    * @return number of rows in table.
    */
   @Override
   public int getRowCount() {
      return nrow;
   }

   /**
    * Return the number of columns in the table. The number of columns
    * includes the header columns.
    * @return number of columns in table.
    */
   @Override
   public int getColCount() {
      return ncol;
   }

   /**
    * Return the number of rows on the top of the table to be treated
    * as header rows.
    * @return number of header rows.
    */
   @Override
   public int getHeaderRowCount() {
      return hrow;
   }

   /**
    * Return the number of columns on the left of the table to be
    * treated as header columns.
    */
   @Override
   public int getHeaderColCount() {
      return hcol;
   }

   /**
    * Return the number of rows on the bottom of the table to be treated
    * as tail rows.
    * @return number of header rows.
    */
   @Override
   public int getTrailerRowCount() {
      return trailerRow;
   }

   public void setTrailerRowCount(int nrow) {
      trailerRow = nrow;
   }

   /**
    * Return the number of columns on the right of the table to be
    * treated as tail columns.
    */
   @Override
   public int getTrailerColCount() {
      return trailerCol;
   }

   public void setTrailerColCount(int ncol) {
      trailerCol = ncol;
   }

   public void setRowHeight(int row, int w) {
      row = rowN(row);

      // ignore rows outside of the range
      if(row < 0) {
         return;
      }

      rowHeights[row] = (short) w;
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
      return rowHeights[rowN(row)];
   }

   public void setColWidth(int col, int w) {
      colWidths[colN(col)] = w;
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
      return colWidths[colN(col)];
   }

   public void setRowBorderColor(int r, int c, Color border) {
      r = rowN(r);
      c = colN(c);

      // ignore rows outside of the range
      if(r < 0 || c < 0) {
         return;
      }

      if(cells[r][c] == null) {
         cells[r][c] = new TableCellInfo();
      }

      cells[r][c].rowBorderC = border;
   }

   /**
    * Return the color for drawing the row border lines.
    * @param r row number.
    * @param c column number.
    * @return ruling color.
    */
   @Override
   public Color getRowBorderColor(int r, int c) {
      r = rowN(r);
      c = colN(c);

      if(cells[r][c] == null) {
         return null;
      }

      return cells[r][c].rowBorderC;
   }

   public void setColBorderColor(int r, int c, Color border) {
      r = rowN(r);
      c = colN(c);

      // ignore rows outside of the range
      if(r < 0 || c < 0) {
         return;
      }

      if(cells[r][c] == null) {
         cells[r][c] = new TableCellInfo();
      }

      cells[r][c].colBorderC = border;
   }

   /**
    * Return the color for drawing the column border lines.
    * @param r row number.
    * @param c column number.
    * @return ruling color.
    */
   @Override
   public Color getColBorderColor(int r, int c) {
      r = rowN(r);
      c = colN(c);

      if(cells[r][c] == null) {
         return null;
      }

      return cells[r][c].colBorderC;
   }

   public void setRowBorder(int r, int c, int border) {
      r = rowN(r);
      c = colN(c);

      // ignore rows outside of the range
      if(r < 0 || c < 0) {
         return;
      }

      if(cells[r][c] == null) {
         cells[r][c] = new TableCellInfo();
      }

      cells[r][c].rowBorder = border;
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
      r = rowN(r);
      c = colN(c);

      if(cells[r][c] == null) {
         return 0;
      }

      return cells[r][c].rowBorder;
   }

   public void setColBorder(int r, int c, int border) {
      r = rowN(r);
      c = colN(c);

      // ignore rows outside of the range
      if(r < 0 || c < 0) {
         return;
      }

      if(cells[r][c] == null) {
         cells[r][c] = new TableCellInfo();
      }

      cells[r][c].colBorder = border;
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
      r = rowN(r);
      c = colN(c);

      if(cells[r][c] == null) {
         return 0;
      }

      return cells[r][c].colBorder;
   }

   public void setInsets(int r, int c, Insets insets) {
      r = rowN(r);
      c = colN(c);

      // ignore rows outside of the range
      if(r < 0 || c < 0) {
         return;
      }

      if(cells[r][c] == null) {
         cells[r][c] = new TableCellInfo();
      }

      cells[r][c].insets = insets;
   }

   /**
    * Return the cell gap space.
    * @param r row number.
    * @param c column number.
    * @return cell gap space.
    */
   @Override
   public Insets getInsets(int r, int c) {
      r = rowN(r);
      c = colN(c);

      if(cells[r][c] == null) {
         return null;
      }

      return cells[r][c].insets;
   }

   public void setSpan(int r, int c, Dimension span) {
      r = rowN(r);
      c = colN(c);

      // ignore rows outside of the range
      if(r < 0 || c < 0) {
         return;
      }

      if(cells[r][c] == null) {
         cells[r][c] = new TableCellInfo();
      }

      cells[r][c].span = span;
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
      r = rowN(r);
      c = colN(c);

      if(cells[r][c] == null) {
         return null;
      }

      return cells[r][c].span;
   }

   public void setAlignment(int r, int c, int align) {
      r = rowN(r);
      c = colN(c);

      // ignore rows outside of the range
      if(r < 0 || c < 0) {
         return;
      }

      if(cells[r][c] == null) {
         cells[r][c] = new TableCellInfo();
      }

      cells[r][c].align = align;
   }

   /**
    * Return the per cell alignment.
    * @param r row number.
    * @param c column number.
    * @return cell alignment.
    */
   @Override
   public int getAlignment(int r, int c) {
      r = rowN(r);
      c = colN(c);

      if(cells[r][c] == null) {
         return 0;
      }

      return cells[r][c].align;
   }

   public void setFont(int r, int c, Font font) {
      r = rowN(r);
      c = colN(c);

      // ignore rows outside of the range
      if(r < 0 || c < 0) {
         return;
      }

      if(cells[r][c] == null) {
         cells[r][c] = new TableCellInfo();
      }

      cells[r][c].font = font;
   }

   /**
    * Return the per cell font. Return null to use default font.
    * @param r row number.
    * @param c column number.
    * @return font for the specified cell.
    */
   @Override
   public Font getFont(int r, int c) {
      r = rowN(r);
      c = colN(c);

      if(cells[r][c] == null) {
         return null;
      }

      return cells[r][c].font;
   }

   public void setLineWrap(int r, int c, boolean wrap) {
      r = rowN(r);
      c = colN(c);

      // ignore rows outside of the range
      if(r < 0 || c < 0) {
         return;
      }

      if(cells[r][c] == null) {
         cells[r][c] = new TableCellInfo();
      }

      cells[r][c].wrap = wrap;
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
      r = rowN(r);
      c = colN(c);

      if(cells[r][c] == null) {
         return false;
      }

      return cells[r][c].wrap;
   }

   public void setForeground(int r, int c, Color bg) {
      r = rowN(r);
      c = colN(c);

      // ignore rows outside of the range
      if(r < 0 || c < 0) {
         return;
      }

      if(cells[r][c] == null) {
         cells[r][c] = new TableCellInfo();
      }

      cells[r][c].foreground = bg;
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
      r = rowN(r);
      c = colN(c);

      if(cells[r][c] == null) {
         return null;
      }

      return cells[r][c].foreground;
   }

   public void setBackground(int r, int c, Color bg) {
      r = rowN(r);
      c = colN(c);

      // ignore rows outside of the range
      if(r < 0 || c < 0) {
         return;
      }

      if(cells[r][c] == null) {
         cells[r][c] = new TableCellInfo();
      }

      cells[r][c].background = bg;
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
      r = rowN(r);
      c = colN(c);

      if(cells[r][c] == null) {
         return null;
      }

      return cells[r][c].background;
   }

   /**
    * Set the cell value.
    * @param r row number.
    * @param c column number.
    * @param v cell value.
    */
   @Override
   public void setObject(int r, int c, Object v) {
      r = rowN(r);
      c = colN(c);

      // ignore rows outside of the range
      if(r < 0 || c < 0) {
         return;
      }

      values[r][c] = v;
      fireChangeEvent();
   }

   /**
    * Return the value at the specified cell.
    * @param r row number.
    * @param c column number.
    * @return the value at the location.
    */
   @Override
   public Object getObject(int r, int c) {
      return values[rowN(r)][colN(c)];
   }

   /**
    * Called when the whole table is finished.
    */
   public void complete() {
      for(int i = 0; i < cells.length; i++) {
         for(int j = 0; j < cells[i].length; j++) {
            completeCell0(i, j);
         }
      }

      ObjectCache.clear();
   }

   /**
    * Called when the cell is done and will not be modified.
    */
   public void completeCell(int r, int c) {
      completeCell0(rowN(r), colN(c));
   }

   public void completeCell0(int r, int c) {
      if(cells[r][c] != null) {
         cells[r][c] = (TableCellInfo) ObjectCache.get(cells[r][c]);
      }
   }

   int rowN(int row) {
      row = ((row < hrow + 1) ? row : (row - region.y + hrow + 1)) + 1;
      return Math.max(0, Math.min(row, hrow + region.height + 2));
   }

   int colN(int col) {
      col = ((col < hcol + 1) ? col: (col - region.x + hcol) + 1) + 1;
      return Math.max(0, Math.min(col, hcol + region.width + 2));
   }

   private TableCellInfo[][] cells;
   private Object[][] values;
   private short[] rowHeights;
   private int[] colWidths;
   private int nrow, ncol;
   private int hrow, hcol;
   private Rectangle region;
   private int trailerRow, trailerCol;
}
