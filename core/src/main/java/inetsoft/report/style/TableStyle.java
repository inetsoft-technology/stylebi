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
package inetsoft.report.style;

import inetsoft.report.*;
import inetsoft.report.lens.*;

import java.awt.*;
import java.util.Hashtable;

/**
 * The TableStyle class is the common base class of all table style
 * classes. It provides services to allow users of the style classes
 * to control which part of the styling is controlled by the style
 * class, and which is controlled by the original table lens class.
 * By controlling the combination of the styling attributes to apply,
 * There could be multiple uses for each style class.
 * <p>
 * It's very easy to create a new table style. To create a new style,
 * first extend the TableStyle class to create a new class. By convention,
 * there should be two constructors: one takes no argument, and one takes
 * a TableLens object as the argument. The TableLens should be passed to
 * the TableStyle class constructor, e.g.<p><pre>
 *    public class Grid1 extends TableStyle {
 *       public Grid1() {
 *       }
 *
 *       public Grid1(TableLens table) {
 *          super(table);
 *       }
 * </pre><p>
 * Next an inner class should be created to define the styling of the
 * table. This can be done by extending the TableStyle.Transparent class.
 * The TableStyle.Transparent class implements the TableLens interface,
 * and provides a default implementation to each method in the
 * interface by passing the call directly to and from the underlying
 * table.
 * <p>
 * In the inner class, pick which style attributes that need to be changed,
 * and implement the methods for the attributes. If no method is overriden,
 * it's same as no style is added. All attributes are retrieved directly
 * from the underlying table.
 * <p>
 * Once the class is created, we need to override the createStyle()
 * method in the TableStyle to return the new class when a create style
 * request is received, e.g.<pre><p>
 *    protected TableLens createStyle(TableLens tbl) {
      return new Style();
 *    }
 *
 *    class Style extends Transparent {
 *       ...
 *    }
 * </pre><p>
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class TableStyle extends AttributeTableLens {
   /**
    * Create an empty table style. The setTable() method must be called
    * before this style can be used. Otherwise there is no table to
    * decorate.
    */
   protected TableStyle() {
      setTable(new DefaultTableLens(2, 1));
   }

   /**
    * Create a table style to decorate the table.
    * @param table table lens.
    */
   public TableStyle(TableLens table) {
      setTable(table);
   }

   /**
    * Combine a table lens and a style lens into a table with the specified
    * style.
    */
   public TableStyle(TableLens table, TableLens style) {
      setTable(table);
      this.xstyle = style;
   }

   /**
    * Create a table style from a specified table style class name
    * @param className table style class name
    * @return table style
    */
   public static TableStyle createTableStyle(String className) {
      TableStyle tableStyle = null;

      try {
         tableStyle = (TableStyle) Class.forName(
            className != null ?
               className : "inetsoft.report.style.TableStyle").newInstance();
      }
      catch(Exception e) {
      }

      return tableStyle;
   }

   /**
    * Get the name of this style. The names of the built-in styles are the
    * same as the class names. The names of XTableStyle are stored in the
    * style specification file.
    * @return style name;
    */
   public String getName() {
      String name = getClass().getName();
      int pos = name.lastIndexOf(".");

      return (pos > 0) ? name.substring(pos + 1) : name;
   }

   /**
    * Get the id of this style. Built-in styles are no longer available.
    * @return style id;
    */
   public String getID() {
      return getName();
   }

   /**
    * Create a table style for the specified table.
    * @param table table lens.
    * @return the style lens to be used with the table.
    */
   protected TableLens createStyle(TableLens table) {
      return new Transparent();
   }

   /**
    * Set the table to be decorated.
    * @param table the table to decorate.
    */
   @Override
   public void setTable(TableLens table) {
      // re-init objects
      if(this.table != table) {
         super.setTable(table);
         this.xstyle = createStyle(table);
      }
   }

   /**
    * Check if this row should be formatted as a title row.
    * @param row row index.
    * @return true if the row should be formatted as header.
    */
   protected boolean isHeaderRowFormat(int row) {
      return isFormatFirstRow() && row >= 0 && row < getHeaderRowCount();
   }

   /**
    * Check if this col should be formatted as a title column.
    * @param col col index.
    * @return true if the col should be formatted as header.
    */
   protected boolean isHeaderColFormat(int col) {
      return isFormatFirstCol() && col >= 0 && col < getHeaderColCount();
   }

   /**
    * Check if this col should be formatted as a trailer column.
    * @param col col index.
    * @return true if the col should be formatted as trailer.
    */
   protected boolean isTrailerRowFormat(int row) {
      return (row < getRowCount()) &&
         (row >= (getRowCount() - getTrailerRowCount()));
   }

   /**
    * Check if this col should be formatted as a trailer column.
    * @param col col index.
    * @return true if the col should be formatted as trailer.
    */
   protected boolean isTrailerColFormat(int col) {
      return (col < getColCount()) &&
         (col >= (getColCount() - getTrailerColCount()));
   }

   /**
    * Get the last row of the header rows
    * @return the last row of the header rows
    */
   protected int getHeaderInnerRow() {
      return getHeaderRowCount() - 1;
   }

   /**
    * Get the last column of the header columns
    * @return the last column of the header columns
    */
   protected int getHeaderInnerCol() {
      return getHeaderColCount() - 1;
   }

   /**
    * Get the prev row of the trailer rows
    * @return the prev row of the trailer rows
    */
   protected int getTrailerInnerRow() {
      return getRowCount() - getTrailerRowCount() - 1;
   }

   /**
    * Get the prev column of the trailer columns
    * @return the prev column of the trailer columns
    */
   protected int getTrailerInnerCol() {
      return getColCount() - getTrailerColCount() - 1;
   }

   /**
    * Return the number of rows on the top of the table to be treated
    * as header rows.
    * @return number of header rows.
    */
   @Override
   public int getHeaderRowCount() {
      if(headerRF) {
         return xstyle.getHeaderRowCount();
      }
      else {
         return table.getHeaderRowCount();
      }
   }

   /**
    * Set the number of header rows.
    * @param headerRow number of header rows.
    */

   /**
    * Return the number of columns on the left of the table to be
    * treated as header columns.
    */
   @Override
   public int getHeaderColCount() {
      return headerCF ?
         xstyle.getHeaderColCount() :
         table.getHeaderColCount();
   }

   /**
    * Return the number of rows on the bottom of the table to be treated
    * as tail rows.
    * @return number of header rows.
    */
   @Override
   public int getTrailerRowCount() {
      return tailRF ?
         xstyle.getTrailerRowCount() :
         table.getTrailerRowCount();
   }

   /**
    * Return the number of columns on the right of the table to be
    * treated as tail columns.
    */
   @Override
   public int getTrailerColCount() {
      return tailCF ?
         xstyle.getTrailerColCount() :
         table.getTrailerColCount();
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
      return heightF ? xstyle.getRowHeight(row) : table.getRowHeight(row);
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
      return widthF ? xstyle.getColWidth(col) : table.getColWidth(col);
   }

   /**
    * Return the color for drawing the row border lines.
    * @param r row number.
    * @param c column number.
    * @return ruling color.
    */
   @Override
   public Color getRowBorderColor(int r, int c) {
      return rowBorderCF ?
         xstyle.getRowBorderColor(r, c) :
         table.getRowBorderColor(r, c);
   }

   /**
    * Return the color for drawing the column border lines.
    * @param r row number.
    * @param c column number.
    * @return ruling color.
    */
   @Override
   public Color getColBorderColor(int r, int c) {
      return colBorderCF ?
         xstyle.getColBorderColor(r, c) :
         table.getColBorderColor(r, c);
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
      int border = table.getRowBorder(r, c);

      if(rowBorderF) {
         int border2 = xstyle.getRowBorder(r, c);

         border = ((border & BREAK_BORDER) != 0) ?
            (border2 | BREAK_BORDER) :
            border2;
      }

      return border;
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
      return colBorderF ?
         xstyle.getColBorder(r, c) :
         table.getColBorder(r, c);
   }

   /**
    * Return the cell gap space.
    * @param r row number.
    * @param c column number.
    * @return cell gap space.
    */
   @Override
   public Insets getInsets(int r, int c) {
      return insetsF ? xstyle.getInsets(r, c) : table.getInsets(r, c);
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
      return spanF ? xstyle.getSpan(r, c) : table.getSpan(r, c);
   }

   /**
    * Return the per cell alignment.
    * @param r row number.
    * @param c column number.
    * @return cell alignment.
    */
   @Override
   public int getAlignment(int r, int c) {
      return alignF ? xstyle.getAlignment(r, c) : table.getAlignment(r, c);
   }

   /**
    * Return the per cell font. Return null to use default font.
    * @param r row number.
    * @param c column number.
    * @return font for the specified cell.
    */
   @Override
   public Font getFont(int r, int c) {
      return fontF ? xstyle.getFont(r, c) : table.getFont(r, c);
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
      return wrapF ? xstyle.isLineWrap(r, c) : table.isLineWrap(r, c);
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
      return foregroundF ?
         xstyle.getForeground(r, c) :
         table.getForeground(r, c);
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
      return backgroundF ?
         xstyle.getBackground(r, c) :
         table.getBackground(r, c);
   }

   /**
    * Return the per cell background color. Return null to use default
    * color.
    * @param r row number.
    * @param c column number.
    * @param spanRow row index of the specified span
    * @return background color for the specified cell.
    */
   @Override
   public Color getBackground(int r, int c, int spanRow) {
      return backgroundF ?
         xstyle.getBackground(r, c, spanRow) :
         table.getBackground(r, c);
   }

   /**
    * Return the value at the specified cell.
    * @param r row number.
    * @param c column number.
    * @return the value at the location.
    */
   @Override
   public Object getObject(int r, int c) {
      return presenterF ? xstyle.getObject(r, c) : table.getObject(r, c);
   }

   /**
    * Set the cell value.
    * @param r row number.
    * @param c column number.
    * @param v cell value.
    */
   @Override
   public void setObject(int r, int c, Object v) {
      if(presenterF) {
         xstyle.setObject(r, c, v);
      }
      else {
         table.setObject(r, c, v);
      }
   }

   /**
    * Set whether the header row count should be retrieved from the style
    * or taken from the table.
    * @param headerR true to get the header row count from the style.
    */
   public void setApplyHeaderRowCount(boolean headerR) {
      this.headerRF = headerR;
   }

   /**
    * Check if the header row count should be taken from the style.
    * @return true if the header row count is from the style.
    */
   public boolean isApplyHeaderRowCount() {
      return headerRF;
   }

   /**
    * Set whether to the header column count should be retrieved from
    * the style or the table.
    * @param headerC true to get the header column count from style.
    */
   public void setApplyHeaderColCount(boolean headerC) {
      this.headerCF = headerC;
   }

   /**
    * Check if the header column count should be taken from the style.
    * @return true if the header column count is from the style.
    */
   public boolean isApplyHeaderColCount() {
      return headerCF;
   }

   /**
    * Set whether the tail row count should be retrieved from the style
    * or taken from the table.
    * @param tailR true to get the tail row count from the style.
    */
   public void setApplyTailRowCount(boolean tailR) {
      this.tailRF = tailR;
   }

   /**
    * Check if the tail row count should be taken from the style.
    * @return true if the tail row count is from the style.
    */
   public boolean isApplyTailRowCount() {
      return tailRF;
   }

   /**
    * Set whether to the tail column count should be retrieved from
    * the style or the table.
    * @param tailC true to get the tail column count from style.
    */
   public void setApplyTailColCount(boolean tailC) {
      this.tailCF = tailC;
   }

   /**
    * Check if the tail column count should be taken from the style.
    * @return true if the tail column count is from the style.
    */
   public boolean isTailHeaderColCount() {
      return tailCF;
   }

   /**
    * Set whether the column width attribute should be determined by
    * the style.
    * @param colWidth true to get the column width from the style.
    */
   public void setApplyColWidth(boolean colWidth) {
      widthF = colWidth;
   }

   /**
    * Check if the column width is determined by the style.
    * @return true if the column width is from the style.
    */
   public boolean isApplyColWidth() {
      return widthF;
   }

   /**
    * Set whether the row height attribute should be determined by
    * the style.
    * @param rowHeight true to get the row height from the style.
    */
   public void setApplyRowHeight(boolean rowHeight) {
      heightF = rowHeight;
   }

   /**
    * Check if the row height is determined by the style.
    * @return true if the row height is from the style.
    */
   public boolean isApplyRowHeight() {
      return heightF;
   }

   /**
    * Set whether the row border color style should be applied.
    * @param borderC true to apply row border color.
    */
   public void setApplyRowBorderColor(boolean borderC) {
      this.rowBorderCF = borderC;
   }

   /**
    * Check if the row border color style is applied.
    * @return true if row border color style is applied.
    */
   public boolean isApplyRowBorderColor() {
      return rowBorderCF;
   }

   /**
    * Set whether the column border color style should be applied.
    * @param borderC true to apply column border color.
    */
   public void setApplyColBorderColor(boolean borderC) {
      this.colBorderCF = borderC;
   }

   /**
    * Check if the column border color style is applied.
    * @return true if column border color style is applied.
    */
   public boolean isApplyColBorderColor() {
      return colBorderCF;
   }

   /**
    * Set whether the row border style should be applied.
    * @param rowBorder true to apply row border style.
    */
   public void setApplyRowBorder(boolean rowBorder) {
      this.rowBorderF = rowBorder;
   }

   /**
    * Check if the row border style is applied.
    * @return true if the row border style is applied.
    */
   public boolean isApplyRowBorder() {
      return rowBorderF;
   }

   /**
    * Set whether the column border style should be applied.
    * @param colBorder true to apply column border style.
    */
   public void setApplyColBorder(boolean colBorder) {
      this.colBorderF = colBorder;
   }

   /**
    * Check if the column border style is applied.
    * @return true if the columnborder style is applied.
    */
   public boolean isApplyColBorder() {
      return colBorderF;
   }

   /**
    * Set whether the cell insets style should be applied.
    * @param insets true to apply cell insets style.
    */
   public void setApplyInsets(boolean insets) {
      this.insetsF = insets;
   }

   /**
    * Check if the cell insets style is applied.
    * @return true if cell insets is applied.
    */
   public boolean isApplyInsets() {
      return insetsF;
   }

   /**
    * Set whether the cell spanning style should be applied.
    * @param span true to apply cell spanning style.
    */
   public void setApplySpan(boolean span) {
      this.spanF = span;
   }

   /**
    * Check if the cell spanning style is applied.
    * @return true if the cell spanning is applied.
    */
   public boolean isApplySpan() {
      return spanF;
   }

   /**
    * Set whether the cell alignment style should be applied.
    * @param align true to apply cell alignment style.
    */
   public void setApplyAlignment(boolean align) {
      this.alignF = align;
   }

   /**
    * Check if the cell alignment is applied.
    * @return true if the alignment style is applied.
    */
   public boolean isApplyAlignment() {
      return alignF;
   }

   /**
    * Set whether the font style should be applied.
    * @param font true to apply font style.
    */
   public void setApplyFont(boolean font) {
      this.fontF = font;
   }

   /**
    * Check if font style is applied.
    * @return true if font is applied.
    */
   public boolean isApplyFont() {
      return fontF;
   }

   /**
    * Set whether the line wrap style should be applied.
    * @param wrap true to apply line wrap style.
    */
   public void setApplyLineWrap(boolean wrap) {
      this.wrapF = wrap;
   }

   /**
    * Check if line wrap style is applied.
    * @return true if line wrap is applied.
    */
   public boolean isApplyLineWrap() {
      return wrapF;
   }

   /**
    * Set whether the foreground style should be applied.
    * @param foreground true to apply foreground.
    */
   public void setApplyForeground(boolean foreground) {
      this.foregroundF = foreground;
   }

   /**
    * Check if the foreground style is applied.
    * @return true if foreground is applied.
    */
   public boolean isApplyForeground() {
      return foregroundF;
   }

   /**
    * Set whether the background style should be applied.
    * @param background true to apply background.
    */
   public void setApplyBackground(boolean background) {
      this.backgroundF = background;
   }

   /**
    * Check if the background style is applied.
    * @return if background is applied.
    */
   public boolean isApplyBackground() {
      return backgroundF;
   }

   /**
    * Set whether the presenter style should be applied.
    * @param presenter true to apply presenters.
    */
   public void setApplyPresenter(boolean presenter) {
      presenterF = presenter;
   }

   /**
    * Check if the presenter style should be applied.
    * @return true if presenter is applied.
    */
   public boolean isApplyPresenter() {
      return presenterF;
   }

   /**
    * Set whether to apply special format to the first row.
    * @param firstR true to apply special format.
    */
   public void setFormatFirstRow(boolean firstR) {
      firstRow = Boolean.valueOf(firstR);
   }

   /**
    * Check if special format is applied to the first row.
    * @return true if special format is applied.
    */
   public boolean isFormatFirstRow() {
      return (firstRow == null) ?
         (table.getHeaderRowCount() >= 1) :
         firstRow.booleanValue();
   }

   /**
    * Set whether to apply special format to the first column.
    * @param firstC true to apply special format.
    */
   public void setFormatFirstCol(boolean firstC) {
      firstCol = Boolean.valueOf(firstC);
   }

   /**
    * Check if special format is applied to the first column.
    * @return true if special format is applied.
    */
   public boolean isFormatFirstCol() {
      return (firstCol == null) ?
         (table.getHeaderColCount() >= 1) :
         firstCol.booleanValue();
   }

   /**
    * Set whether to apply special format to the last row.
    * @param lastR true to apply special format.
    */
   public void setFormatLastRow(boolean lastR) {
      lastRow = lastR;
   }

   /**
    * Check if special format is applied to the last row.
    * @return true if special format is applied.
    */
   public boolean isFormatLastRow() {
      return lastRow;
   }

   /**
    * Set whether to apply special format to the last column.
    * @param lastC true to apply special format.
    */
   public void setFormatLastCol(boolean lastC) {
      lastCol = lastC;
   }

   /**
    * Check if special format is applied to the last column.
    * @return true if special format is applied.
    */
   public boolean isFormatLastCol() {
      return lastCol;
   }

   /**
    * Get a font with the new specified style.
    * @param font original font.
    * @param style font style.
    * @return font with the new style.
    */
   public Font createFont(Font font, int style) {
      if(font == null) {
         font = defFont;
      }

      Font fn = (Font) fnmap.get(font);

      if(fn != null && fn.getStyle() == style) {
         return fn;
      }

      fn = (font instanceof StyleFont) ?
         (new StyleFont(font.getName(),
         style | ((StyleFont) font).getStyle() & StyleFont.STYLE_FONT_MASK,
         font.getSize(), ((StyleFont) font).getUnderlineStyle())) :
         (new Font(font.getName(), style, font.getSize()));

      fnmap.put(font, fn);
      return fn;
   }

   /**
    * Get a font with the new specified style.
    * @param font original font.
    * @param style font style.
    * @param size font size.
    * @return font with the new style.
    */
   public Font createFont(Font font, int style, int size) {
      if(font == null) {
         font = defFont;
      }

      return (font instanceof StyleFont) ?
         (new StyleFont(font.getName(),
         style | ((StyleFont) font).getStyle() & StyleFont.STYLE_FONT_MASK,
         size, ((StyleFont) font).getUnderlineStyle())) :
         (new Font(font.getName(), style, size));
   }

   /**
    * Clone the table style.
    */
   @Override
   public TableStyle clone() {
      try {
         TableStyle ts = (TableStyle) super.clone();

         ts.table = null; // force re-init
         ts.setTable(table); // otherwise style points to the old super
         return ts;
      }
      catch(Exception e) {
      }

      return null;
   }

   /**
    * This is the default implementation of a style. It does not modify
    * any of the attributes and just pass them through.
    */
   protected class Transparent extends AbstractTableLens {
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
       * Return the number of rows in the table. The number of rows includes
       * the header rows.
       * @return number of rows in table.
       */
      @Override
      public int getRowCount() {
         return table.getRowCount();
      }

      /**
       * Return the number of columns in the table. The number of columns
       * includes the header columns.
       * @return number of columns in table.
       */
      @Override
      public int getColCount() {
         return table.getColCount();
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
       * Check if the value at one cell is null.
       * @param r the specified row index.
       * @param c column number.
       * @return <tt>true</tt> if null, <tt>false</tt> otherwise.
       */
      @Override
      public final boolean isNull(int r, int c) {
         return table.isNull(r, c);
      }

      /**
       * Return the value at the specified cell.
       * @param r row number.
       * @param c column number.
       * @return the value at the location.
       */
      @Override
      public Object getObject(int r, int c) {
         return table.getObject(r, c);
      }

      /**
       * Get the double value in one row.
       * @param r the specified row index.
       * @param c column number.
       * @return the double value in the specified row.
       */
      @Override
      public final double getDouble(int r, int c) {
         return table.getDouble(r, c);
      }

      /**
       * Get the float value in one row.
       * @param r the specified row index.
       * @param c column number.
       * @return the float value in the specified row.
       */
      @Override
      public final float getFloat(int r, int c) {
         return table.getFloat(r, c);
      }

      /**
       * Get the long value in one row.
       * @param r the specified row index.
       * @param c column number.
       * @return the long value in the specified row.
       */
      @Override
      public final long getLong(int r, int c) {
         return table.getLong(r, c);
      }

      /**
       * Get the int value in one row.
       * @param r the specified row index.
       * @param c column number.
       * @return the int value in the specified row.
       */
      @Override
      public final int getInt(int r, int c) {
         return table.getInt(r, c);
      }

      /**
       * Get the short value in one row.
       * @param r the specified row index.
       * @param c column number.
       * @return the short value in the specified row.
       */
      @Override
      public final short getShort(int r, int c) {
         return table.getShort(r, c);
      }

      /**
       * Get the byte value in one row.
       * @param r the specified row index.
       * @param c column number.
       * @return the byte value in the specified row.
       */
      @Override
      public final byte getByte(int r, int c) {
         return table.getByte(r, c);
      }

      /**
       * Get the boolean value in one row.
       * @param r the specified row index.
       * @param c column number.
       * @return the boolean value in the specified row.
       */
      @Override
      public final boolean getBoolean(int r, int c) {
         return table.getBoolean(r, c);
      }

      /**
       * Check if is primitive.
       * @return <tt>true</tt> if is primitive, <tt>false</tt> otherwise.
       */
      @Override
      public final boolean isPrimitive(int col) {
         return table.isPrimitive(col);
      }

      /**
       * Set the cell value.
       * @param r row number.
       * @param c column number.
       * @param v cell value.
       */
      @Override
      public void setObject(int r, int c, Object v) {
         table.setObject(r, c, v);
      }

      /**
       * Return the number of rows on the top of the table to be treated
       * as header rows.
       * @return number of header rows.
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
       * Return the index of the last row in the table.
       */
      protected int lastRow() {
         return table.getRowCount() - 1;
      }

      /**
       * Return the index of the last column in the table.
       */
      protected int lastCol() {
         return table.getColCount() - 1;
      }

      /**
       * Get internal table data descriptor which contains table structural
       * infos.
       * @return table data descriptor.
       */
      @Override
      public TableDataDescriptor getDescriptor() {
         return table.getDescriptor();
      }
   }

   /**
    * Apply default style option.
    */
   public void applyDefaultStyle() {
      setApplyRowBorderColor(true);
      setApplyColBorderColor(true);
      setApplyRowBorder(true);
      setApplyColBorder(true);
      setApplyAlignment(false);
      setApplyFont(true);
      setApplyForeground(true);
      setApplyBackground(true);
      setFormatFirstRow(true);
      setFormatFirstCol(true);
      setFormatLastRow(true);
      setFormatLastCol(true);
   }

   protected Font defFont = inetsoft.report.internal.Util.DEFAULT_FONT;

   private TableLens xstyle;		// style lens with style attributes
   boolean headerRF = true;	// header row
   boolean headerCF = true;	// header column
   boolean tailRF = true;	// tail row
   boolean tailCF = true;	// tail column
   boolean widthF = false;	// column width
   boolean heightF = false;	// row height
   boolean rowBorderCF = true;	// border color
   boolean colBorderCF = true;	// border color
   boolean rowBorderF = true;	// row border
   boolean colBorderF = true;	// column border
   boolean insetsF = false;	// cell insets
   boolean spanF = false;	// cell span
   boolean alignF = false;	// alignment
   boolean fontF = true;	// font
   boolean wrapF = false;	// line wrap
   boolean foregroundF = true;	// cell foreground
   boolean backgroundF = true;	// cell background
   boolean presenterF = true;	// cell presenter
   Boolean firstRow = Boolean.TRUE;	// apply to first row
   Boolean firstCol = null;	// apply to first column
   boolean lastRow = false;	// apply to last row
   boolean lastCol = false;	// apply to last column
   Hashtable fnmap = new Hashtable(); // font to new font
}
