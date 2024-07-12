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
import inetsoft.report.filter.DefaultTableChangeListener;
import inetsoft.report.internal.Util;
import inetsoft.uql.XMetaInfo;

import java.awt.*;
import java.util.List;

/**
 * The RotatedTableLens transforms a table by rotating the table. The
 * rows in the original table becomes columns in the transforms table,
 * and columns becomes rows.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class RotatedTableLens extends AbstractTableLens implements TableFilter {
   /**
    * Constructor.
    */
   public RotatedTableLens() {
      super();
   }

   /**
    * Construct a rotated table from the original table.
    * @param table original table.
    */
   public RotatedTableLens(TableLens table) {
      this(table, -1);
   }

   /**
    * Construct a rotated table from the original table.
    * @param table original table.
    * @param hrows the specified header rows.
    */
   public RotatedTableLens(TableLens table, int hrows) {
      this();

      setTable(table);
      this.hrows = hrows;
   }

   /**
    * Get the original table of this filter.
    */
   @Override
   public TableLens getTable() {
      return table;
   }

   /**
    * Get the table data descritor.
    */
   @Override
   public TableDataDescriptor getDescriptor() {
      if(table == null) {
         return super.getDescriptor();
      }

      if(descriptor == null) {
         final TableDataDescriptor dataDescriptor = table.getDescriptor();
         descriptor = new TableDataDescriptor() {
            @Override
            public TableDataPath getColDataPath(int col) {
               return dataDescriptor.getRowDataPath(col);
            }

            @Override
            public TableDataPath getRowDataPath(int row) {
               return dataDescriptor.getColDataPath(row);
            }

            @Override
            public TableDataPath getCellDataPath(int row, int col) {
               return dataDescriptor.getCellDataPath(col, row);
            }

            @Override
            public boolean isColDataPath(int col, TableDataPath path) {
               return dataDescriptor.isRowDataPath(col, path);
            }

            @Override
            public boolean isRowDataPath(int row, TableDataPath path) {
               return dataDescriptor.isColDataPath(row, path);
            }

            @Override
            public boolean isCellDataPathType(int row, int col,
                                              TableDataPath path) {
               return dataDescriptor.isCellDataPathType(col, row, path);
            }

            @Override
            public boolean isCellDataPath(int row, int col, TableDataPath path)
            {
               return dataDescriptor.isCellDataPath(col, row, path);
            }

            @Override
            public int getRowLevel(int row) {
               return -1;
            }

            @Override
            public int getType() {
               return dataDescriptor.getType();
            }

            @Override
            public XMetaInfo getXMetaInfo(TableDataPath path) {
               return null;
            }

            @Override
            public List<TableDataPath> getXMetaInfoPaths() {
               return null;
            }

            @Override
            public boolean containsFormat() {
               return false;
            }

            @Override
            public boolean containsDrill() {
               return false;
            }
         };
      }

      return descriptor;
   }

   /**
    * Set the base table of this filter.
    */
   @Override
   public void setTable(TableLens table) {
      this.table = table;
      table.moreRows(TableLens.EOT);
      ncols = table.getRowCount();

      if(maxcols > 0) {
         ncols = Math.min(ncols, maxcols);
      }

      invalidate();
      this.descriptor = null;
      this.table.addChangeListener(new DefaultTableChangeListener(this));
   }

   /**
    * Set the maximum number of columns allowed in this table.
    */
   public void setMaxColumns(int maxcols) {
      this.maxcols = maxcols;

      if(maxcols > 0 && ncols > maxcols) {
         ncols = maxcols;
      }
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
      return -1;
   }

   /**
    * Get the base table column index corresponding to the filtered table.
    * If the column does not exist in the base table, it returns -1.
    * @param col column index in  the filtered table.
    * @return corresponding column index in the bast table.
    */
   @Override
   public int getBaseColIndex(int col) {
      return -1;
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
      table.moreRows(EOT);
      return row < getRowCount();
   }

   /**
    * Return the number of rows in the table. The number of rows includes
    * the header rows.
    * @return number of rows in table.
    */
   @Override
   public int getRowCount() {
      return table.getColCount();
   }

   /**
    * Return the number of columns in the table. The number of columns
    * includes the header columns.
    * @return number of columns in table.
    */
   @Override
   public int getColCount() {
      return ncols;
   }

   /**
    * Get the current column content type.
    * @param col column number.
    * @return column type.
    */
   @Override
   public Class getColType(int col) {
      // rotated table might have mixed data in a column, shouldn't use
      // the first row. if mixed, defaults to string
      return Util.getColType(this, col, String.class, 100);
   }

   /**
    * Return the number of rows on the top of the table to be treated
    * as header rows.
    * @return number of header rows.
    */
   @Override
   public int getHeaderRowCount() {
      return hrows < 0 ?
         table.getHeaderColCount() :
         Math.min(hrows, table.getColCount());
   }

   /**
    * Return the number of columns on the left of the table to be
    * treated as header columns.
    */
   @Override
   public int getHeaderColCount() {
      return table.getHeaderRowCount();
   }

   /**
    * Return the number of rows on the bottom of the table to be treated
    * as tail rows.
    * @return number of header rows.
    */
   @Override
   public int getTrailerRowCount() {
      return table.getTrailerColCount();
   }

   /**
    * Return the number of columns on the right of the table to be
    * treated as tail columns.
    */
   @Override
   public int getTrailerColCount() {
      return table.getTrailerRowCount();
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
      return -1;
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
      return -1;
   }

   /**
    * Return the color for drawing the row border lines.
    * @param r row number.
    * @param c column number.
    * @return ruling color.
    */
   @Override
   public Color getRowBorderColor(int r, int c) {
      return table.getColBorderColor(c, r);
   }

   /**
    * Return the color for drawing the column border lines.
    * @param r row number.
    * @param c column number.
    * @return ruling color.
    */
   @Override
   public Color getColBorderColor(int r, int c) {
      return table.getRowBorderColor(c, r);
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
      return table.getColBorder(c, r);
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
      return table.getRowBorder(c, r);
   }

   /**
    * Return the cell gap space.
    * @param r row number.
    * @param c column number.
    * @return cell gap space.
    */
   @Override
   public Insets getInsets(int r, int c) {
      return table.getInsets(c, r);
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
      Dimension d = table.getSpan(c, r);

      return (d == null) ? d : new Dimension(d.height, d.width);
   }

   /**
    * Return the per cell alignment.
    * @param r row number.
    * @param c column number.
    * @return cell alignment.
    */
   @Override
   public int getAlignment(int r, int c) {
      return table.getAlignment(c, r);
   }

   /**
    * Return the per cell font. Return null to use default font.
    * @param r row number.
    * @param c column number.
    * @return font for the specified cell.
    */
   @Override
   public Font getFont(int r, int c) {
      return table.getFont(c, r);
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
      return table.isLineWrap(c, r);
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
      return table.getForeground(c, r);
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
      return table.getBackground(c, r);
   }

   /**
    * Return the value at the specified cell.
    * @param r row number.
    * @param c column number.
    * @return the value at the location.
    */
   @Override
   public Object getObject(int r, int c) {
      Object obj = table.getObject(c, r);

      if(r == 0 && obj == null) {
         return "Column [" + c + "]";
      }

      return obj;
   }

   /**
    * Set a cell value.
    * @param r row index.
    * @param c column index.
    * @param val cell value.
    */
   @Override
   public void setObject(int r, int c, Object val) {
      table.setObject(c, r, val);
   }

   /**
    * Dispose the table to clear up temporary resources.
    */
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

   private TableDataDescriptor descriptor;
   private int hrows = -1;
   private int ncols = 0;
   private int maxcols = 0;
   private TableLens table = null;
}
