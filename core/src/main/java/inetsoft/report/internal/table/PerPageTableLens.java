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

import inetsoft.report.TableLens;
import inetsoft.report.lens.AttributeTableLens;
import inetsoft.util.SparseMatrix;

import java.awt.*;

/**
 * This table lens is used to wrap around a table lens in a table paintable.
 * Any changes to the table headers after the paintable is created would not
 * be reflected here. This is used to make the table printing consistent with
 * section.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public final class PerPageTableLens extends AttributeTableLens {
   /**
    * Constructor.
    */
   public PerPageTableLens(TableLens table) {
      super(table);
      this.table = table;
      headers = new Object[table.getHeaderRowCount()][table.getColCount()];

      for(int i = 0; i < headers.length; i++) {
         for(int j = 0; j < headers[i].length; j++) {
            headers[i][j] = table.getObject(i, j);
         }
      }
   }

   /**
    * Set the cell value.
    * @param r row number.
    * @param c column number.
    * @param v cell value.
    */
   @Override
   public void setObject(int r, int c, Object v) {
      if(r < headers.length) {
         headers[r][c] = v;
      }
      else {
         if(cache == null) {
            cache = new SparseMatrix();
         }

         cache.set(r, c, v);
      }
   }

   /**
    * Get the cell value.
    */
   @Override
   public Object getObject(int r, int c) {
      if(r < 0) {
         return null;
      }

      if(r < headers.length) {
         return headers[r][c];
      }

      Object val = (cache != null) ? cache.get(r, c) : SparseMatrix.NULL;
      return (val == SparseMatrix.NULL) ? table.getObject(r, c) : val;
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
      return table.moreRows(row);
   }

   /**
    * Get the number of rows in this table.
    */
   @Override
   public int getRowCount() {
      return table.getRowCount();
   }

   /**
    * Get the number of columns in this table.
    */
   @Override
   public int getColCount() {
      return table.getColCount();
   }

   /**
    * Return the number of rows on the top of the table to be treated
    * as header rows.
    * @return number of header rows.
    */
   @Override
   public int getHeaderRowCount() {
      return headers.length;
   }

   /**
    * Return the number of columns on the left of the table to be
    * treated as header columns.
    */
   @Override
   public int getHeaderColCount() {
      return table.getHeaderColCount();
   }

   /**
    * Return the number of rows on the bottom of the table to be treated
    * as tail rows.
    * @return number of header rows.
    */
   @Override
   public int getTrailerRowCount() {
      return table.getTrailerRowCount();
   }

   /**
    * Return the number of columns on the right of the table to be
    * treated as tail columns.
    */
   @Override
   public int getTrailerColCount() {
      return table.getTrailerColCount();
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
      return table.getRowHeight(row);
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
      return table.getColWidth(col);
   }

   /**
    * Get the current column content type.
    * @param col column number.
    * @return column type.
    */
   @Override
   public Class getColType(int col) {
      return table.getColType(col);
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
      return table.getSpan(r, c);
   }

   /**
    * Set the per cell alignment.
    * @param r row number.
    * @param c column number.
    */
   @Override
   public void setAlignment(int r, int c, int align) {
      FormatInfo info = getFormatInfo(r, c, true);

      if(info != null) {
         info.align = align;
      }
   }

   /**
    * Return the per cell alignment.
    * @param r row number.
    * @param c column number.
    * @return cell alignment.
    */
   @Override
   public int getAlignment(int r, int c) {
      FormatInfo info = formatCache == null ? null : getFormatInfo(r, c, false);
      return info != null ? info.align : table.getAlignment(r, c);
   }

   /**
    * Set the per cell font.
    * @param r row number.
    * @param c column number.
    */
   @Override
   public void setFont(int r, int c, Font font) {
      FormatInfo info = getFormatInfo(r, c, true);

      if(info != null) {
         info.font = font;
      }
   }

   /**
    * Return the per cell font. Return null to use default font.
    * @param r row number.
    * @param c column number.
    * @return font for the specified cell.
    */
   @Override
   public Font getFont(int r, int c) {
      FormatInfo info = formatCache == null ? null : getFormatInfo(r, c, false);
      return info != null ? info.font : table.getFont(r, c);
   }

   /**
    * Set the per cell line wrap mode. If the line wrap mode is true,
    * lines are wrapped when the text can not fit on one line. Otherwise
    * the wrapping is never done and any overflow text will be truncated.
    * @param r row number.
    * @param c column number.
    */
   @Override
   public void setLineWrap(int r, int c, boolean wrap) {
      FormatInfo info = getFormatInfo(r, c, true);

      if(info != null) {
         info.wrap = wrap;
      }
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
      FormatInfo info = formatCache == null ? null : getFormatInfo(r, c, false);
      return info != null ? info.wrap : table.isLineWrap(r, c);
   }

   /**
    * Set the per cell foreground color. Return null to use default color.
    * @param r row number.
    * @param c column number.
    */
   @Override
   public void setForeground(int r, int c, Color color) {
      FormatInfo info = getFormatInfo(r, c, true);

      if(info != null) {
         info.fcolor = color;
      }
   }

   /**
    * Return the per cell foreground color. Return null to use default color.
    * @param r row number.
    * @param c column number.
    * @return foreground color for the specified cell.
    */
   @Override
   public Color getForeground(int r, int c) {
      FormatInfo info = formatCache == null ? null : getFormatInfo(r, c, false);
      return info != null ? info.fcolor : table.getForeground(r, c);
   }

   /**
    * Set the per cell background color. Return null to use default color.
    * @param r row number.
    * @param c column number.
    */
   @Override
   public void setBackground(int r, int c, Color color) {
      FormatInfo info = getFormatInfo(r, c, true);

      if(info != null) {
         info.bcolor = color;
      }
   }

   /**
    * Return the per cell background color. Return null to use default color.
    * @param r row number.
    * @param c column number.
    * @return background color for the specified cell.
    */
   @Override
   public Color getBackground(int r, int c) {
      FormatInfo info = formatCache == null ? null : getFormatInfo(r, c, false);
      return info != null ? info.bcolor : table.getBackground(r, c);
   }

   /**
    * Dispose the table to clear up temporary resources.
    */
   @Override
   public void dispose() {
      table.dispose();
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
      return table.getColumnIdentifier(col);
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
      table.setColumnIdentifier(col, identifier);
   }

   /**
    * Get the format info at given cell, if not exist, create it.
    */
   private FormatInfo getFormatInfo(int r, int c, boolean create) {
      Object obj = formatCache == null ? null : formatCache.get(r, c);

      if(obj == null || obj == SparseMatrix.NULL) {
         if(create) {
            if(formatCache == null) {
               formatCache = new SparseMatrix();
            }

            FormatInfo info = new FormatInfo();
            formatCache.set(r, c, info);
            return info;
         }

         return null;
      }

      return (FormatInfo) obj;
   }

   private final class FormatInfo {
      private int align;
      private Font font;
      private boolean wrap;
      private Color fcolor;
      private Color bcolor;
   }

   private TableLens table;
   private Object[][] headers;
   private SparseMatrix formatCache;
}
