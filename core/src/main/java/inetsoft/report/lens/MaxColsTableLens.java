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

import java.awt.*;

/**
 * Max rows table lens returns limited table cols.
 *
 * @version 14.0
 * @author InetSoft Technology Corp
 */
public class MaxColsTableLens extends AbstractTableLens implements TableFilter {
   /**
    * Constructor.
    */
   public MaxColsTableLens(TableLens table, int max) {
      this.table = table;
      this.max = max;
   }

   public TableLens getTable() {
      return table;
   }

   @Override
   public void setTable(TableLens table) {
      this.table = table;
   }

   @Override
   public void invalidate() {
      // do nothing.
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
   public Dimension getSpan(int r, int c) {
      Dimension dim = table.getSpan(r, c);

      if(dim != null && c + dim.width > max) {
         dim.width = Math.max(1, max - c);
      }

      return dim;
   }

   @Override
   public int getRowCount() {
      return table.getRowCount();
   }

   @Override
   public int getColCount() {
      return max;
   }

   @Override
   public Object getObject(int r, int c) {
      if(c > max) {
         return null;
      }

      return table.getObject(r, c);
   }

   /**
    * Return the per cell alignment.
    * @param r row number.
    * @param c column number.
    * @return cell alignment.
    */
   @Override
   public int getAlignment(int r, int c) {
      return table.getAlignment(r, c);
   }

   /**
    * Return the per cell font. Return null to use default font.
    * @param r row number.
    * @param c column number.
    * @return font for the specified cell.
    */
   @Override
   public Font getFont(int r, int c) {
      return table.getFont(r, c);
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
      return table.isLineWrap(r, c);
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
      return table.getForeground(r, c);
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
      return table.getBackground(r, c);
   }

   /**
    * Return the color for drawing the row border lines.
    * @param r row number.
    * @param c column number.
    * @return ruling color.
    */
   @Override
   public Color getRowBorderColor(int r, int c) {
      return table.getRowBorderColor(r, c);
   }

   /**
    * Return the color for drawing the column border lines.
    * @param r row number.
    * @param c column number.
    * @return ruling color.
    */
   @Override
   public Color getColBorderColor(int r, int c) {
      return table.getColBorderColor(r, c);
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
      return table.getRowBorder(r, c);
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
      return table.getColBorder(r, c);
   }

   /**
    * Return the cell gap space.
    * @param r row number.
    * @param c column number.
    * @return cell gap space.
    */
   @Override
   public Insets getInsets(int r, int c) {
      return table.getInsets(r, c);
   }

   /**
    * Return the number of rows on the top of the table to be treated
    * as header rows.
    * @return number of header rows.  Default is 1.
    */
   @Override
   public int getHeaderRowCount() {
      return table.getHeaderRowCount();
   }

   /**
    * Return the number of columns on the left of the table to be
    * treated as header columns.
    */
   @Override
   public int getHeaderColCount() {
      return table.getHeaderColCount();
   }

   @Override
   public TableDataDescriptor getDescriptor() {
      return table.getDescriptor();
   }

   private TableLens table;
   private int max = 0;
}
