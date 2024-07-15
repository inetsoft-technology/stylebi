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
package inetsoft.report;

import inetsoft.report.event.TableChangeListener;
import inetsoft.uql.XDrillInfo;
import inetsoft.uql.XTable;
import inetsoft.util.SparseMatrix;

import java.awt.*;
import java.text.Format;

/**
 * TableLens defines a view into a table. It contains functions to access
 * the contents of the table, as well as the attributes of the table.
 * The TableLens is used to create a table in a document. There are
 * builtin table lens classes that can be used to create a TableLens
 * object from other data source, such as a Swing JTable or a JDBC query.
 * Under most circumstance, the user should not need to implement the
 * TableLens directly to create a table.
 * <p>
 * If the builtin table lens (in inetsoft.report.lens package) does not
 * satisfy the need of an application, a program can create a customized
 * TableLens to tailor to its need. There are several classes that
 * can be used as the starting point for this. They are designed to
 * make defining a custom table lens easier.
 * <p>
 * The AbstractTableLens provides a default value to most of the methods
 * in the TableLens, except the getRowCount(), getColCount(), and
 * getObject() methods. A table lens class can be easily created by
 * extending the AbstractTableLens to provide a view into a different
 * data source. The other methods can be overridden to provide a more
 * customized view. Alternative, a pre-built or custom built style class
 * can be used to provide a table style. The styles are cover in more
 * details later. e.g.<pre>
 *   TableLens table = new AbstractTableLens() {
 *      public int getRowCount() {
 *         return data.length;
 *      }
 *
 *      public int getColCount() {
 *         return data[0].length;
 *      }
 *
 *      public Object getObject(int r, int c) {
 *         return data[r][c];
 *      }
 *
 *      private String[][] data = {
 *        {"Sales", "Profit", "Domestic", "International"},
 *        {"1,000", "200", "800", "200"}
 *      }
 *   };
 * </pre>
 * <p>
 * The DefaultTableLens provides a concrete class that can be instantiated
 * without any additional coding. The user of the class is responsible
 * to populate the contents in the DefaultTableLens by setting the
 * appropriate attributes and data. Since the StyleReport is based on
 * the MVC design pattern, we strongly encourage the use of table lens
 * as a view into other data source, instead of creating a duplicated
 * copy, which in less efficient in storage and flexibility.
 * <p>
 * Another class, AttributeTableLens, is similar to the AbstractTableLens.
 * But instead of having no internal storage, it has storage for the
 * table attributes so the user of this class (or it's derived class)
 * can set the attributes by calling the setter methods. The attribute
 * table lens and abstract table lens can be combined to create a
 * table lens into a data source and which users can change the attributes
 * by calling the setter methods. e.g.<pre>
 *    TableLens lens = ...;
 *    AbstractTableLens table = new AbstractTableLens(lens);
 *    ...
 *    table.setFormat(1, NumberFormat.getCurrencyInstance());
 * </pre>
 * <p>
 * If a table is populated in background, the data can be used to process
 * a report before all data is loaded in the table lens. To facilitate
 * streaming of data, the table lens can be supplied to a report before
 * it's fully loaded. The table lens must conform to the following
 * conventions:<p><pre>
 * 1. The getRowCount() method should return negative number of loaded rows
 *    minus 1, before the table is fully loaded. It should not block.
 * 2. The moreRows() method must be implemented to check if there are more
 *    rows available. It should block in the row specified by row index is
 *    not loaded.
 * 3. If getObject() is called on a row that is not loaded, it should block
 *    until the row is loaded.
 * </pre>
 * <p>
 * The TableLens interface is also used to create visual styles of
 * tables independent of the data in the table. There are over 80
 * builtin style classes in the inetsoft.report.style package. A style
 * can be combined with a table lens to create a visual presentation
 * of the table where the attributes (color, font, border ...) are
 * defined in the style, and the data is defined in the table lens.
 * <pre>
 *    TableLens lens = new inetsoft.report.lens.swing.JTableLens(jTable1);
 *    report.addTable(new Grid4(lens)); // attach a style, Grid4
 * </pre>
 * <p>
 * Any styles can be created and reused as the builtin styles. Refer
 * to the inetsoft.report.style.TableStyle class on how to create a
 * table style class.
 *
 * @version 6.1
 * @author InetSoft Technology Corp
 */
public interface TableLens extends XTable, StyleConstants {
   /**
    * A constant representing a break border constant. This value
    * can be OR'ed with the border style to insert a page break
    * in to a table. The break is only recognized when returned
    * as the row border of the first column cells.
    */
   public static final int BREAK_BORDER = 0x1000000;

   /**
    * A special object value marking a value that does not exist in table.
    */
   public static final Object NULL = SparseMatrix.NULL;

   /**
    * Get internal table data descriptor which contains table structural
    * infos.
    * @return table data descriptor.
    */
   @Override
   public TableDataDescriptor getDescriptor();

   /**
    * Add table change listener to the filtered table.
    * If the table filter's data changes, a TableChangeEvent will be triggered
    * for the TableChangeListener to process.
    * @param listener the specified TableChangeListener
    */
   public void addChangeListener(TableChangeListener listener);

   /**
    * Remove table change listener from the filtered table.
    * @param listener the specified TableChangeListener to be removed
    */
   public void removeChangeListener(TableChangeListener listener);

   /**
    * Get the current row heights setting. The meaning of row heights
    * depends on the table layout policy setting. If the row height
    * is to be calculated by the ReportSheet based on the content,
    * return -1.
    * @return row height.
    */
   public int getRowHeight(int row);

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
   public int getColWidth(int col);

   /**
    * Return the color for drawing the row border lines.
    * @param r row number.
    * @param c column number.
    * @return ruling color.
    */
   public Color getRowBorderColor(int r, int c);

   /**
    * Return the color for drawing the column border lines.
    * @param r row number.
    * @param c column number.
    * @return ruling color.
    */
   public Color getColBorderColor(int r, int c);

   /**
    * Return the style for bottom border of the specified cell. The flag
    * must be one of the style options defined in the StyleConstants
    * class. If the row number is -1, it's checking the outside ruling
    * on the top.
    * @param r row number.
    * @param c column number.
    * @return ruling flag.
    */
   public int getRowBorder(int r, int c);

   /**
    * Return the style for right border of the specified row. The flag
    * must be one of the style options defined in the StyleConstants
    * class. If the column number is -1, it's checking the outside ruling
    * on the left.
    * @param r row number.
    * @param c column number.
    * @return ruling flag.
    */
   public int getColBorder(int r, int c);

   /**
    * Return the cell gap space.
    * @param r row number.
    * @param c column number.
    * @return cell gap space.
    */
   public Insets getInsets(int r, int c);

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
   public Dimension getSpan(int r, int c);

   /**
    * Return the per cell alignment.
    * @param r row number.
    * @param c column number.
    * @return cell alignment.
    */
   public int getAlignment(int r, int c);

   /**
    * Return the per cell font. Return null to use default font.
    * @param r row number.
    * @param c column number.
    * @return font for the specified cell.
    */
   public Font getFont(int r, int c);

   /**
    * Return the per cell line wrap mode. If the line wrap mode is true,
    * lines are wrapped when the text can not fit on one line. Otherwise
    * the wrapping is never done and any overflow text will be truncated.
    * @param r row number.
    * @param c column number.
    * @return true if line wrapping should be done.
    */
   public boolean isLineWrap(int r, int c);

   /**
    * Return the per cell foreground color. Return null to use default
    * color.
    * @param r row number.
    * @param c column number.
    * @return foreground color for the specified cell.
    */
   public Color getForeground(int r, int c);

   /**
    * Return the per cell background color. Return null to use default
    * color.
    * @param r row number.
    * @param c column number.
    * @return background color for the specified cell.
    */
   public Color getBackground(int r, int c);

   public default int getAlpha(int r, int c) {
      return -1;
   }

   /**
    * Return the per cell background color. Return null to use default
    * color.
    * @param r row number.
    * @param c column number.
    * @param spanRow row index of the specified span
    * @return background color for the specified cell.
    */
   public default Color getBackground(int r, int c, int spanRow) {
      return getBackground(r, c);
   }

   /**
    * Return the per cell format.
    * @param row row number.
    * @param col column number.
    * @return format for the specified cell.
    */
   public Format getDefaultFormat(int row, int col);

   /**
    * Return the per cell drill info.
    * @param r row number.
    * @param c column number.
    * @return drill info for the specified cell.
    */
   public XDrillInfo getXDrillInfo(int r, int c);

   /**
    * Check if contains format.
    * @return true if contains format.
    */
   public boolean containsFormat();

   /**
    * Check if contains drill.
    * @return true if contains drill.
    */
   public boolean containsDrill();
}
