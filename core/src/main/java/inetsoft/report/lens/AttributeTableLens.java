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
import inetsoft.report.filter.*;
import inetsoft.report.internal.Util;
import inetsoft.report.painter.PresenterPainter;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.lang.reflect.Method;
import java.text.*;
import java.util.*;

/**
 * The AttributeTableLens is a decorator table. It can be used with
 * another table to allow users to change the attributes of the table
 * by calling the attribute setter methods on the AttributeTableLens.
 * <p>
 * There are four levels of setting in the AttributeTableLens. An attribute
 * can be changed at table level, in which case the attribute is applied to
 * all cells in the table. An attribute can also be set at per row or per
 * column level. The finest level of control allows an attribute being
 * set at per cell level. For each attribute, there are four attribute
 * setter methods corresponding to the four levels described above.
 * <p>
 * AttributeTableLens is the base class of most other table lens classes.
 * Its methods can be called to change the table attributes.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class AttributeTableLens extends AbstractTableLens
   implements DataTableLens, TableFilter, Cloneable
{
   /**
    * The setTable() method must be called before this table can be used.
    */
   public AttributeTableLens() {
      super();
   }

   /**
    * Create an attribute table with the specified table as the base table.
    * @param table base table.
    */
   public AttributeTableLens(TableLens table) {
      this();
      setTable(table);
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
    * Set the base table to be used with the attribute table table.
    * @param table base table.
    */
   @Override
   public void setTable(TableLens table) {
      if(this.table != null) {
         this.table.removeChangeListener(changeListener);
      }

      this.table = table;
      this.attritable = table instanceof AttributeTableLens ? (AttributeTableLens) table : null;
      invalidate();

      if(this.table != null) {
         this.table.addChangeListener(changeListener);
      }
   }

   /**
    * Get the base table lens.
    * @return base table.
    */
   @Override
   public TableLens getTable() {
      return table;
   }

   /**
    * Get internal table data descriptor which contains table structural
    * infos.
    * @return table data descriptor.
    */
   @Override
   public TableDataDescriptor getDescriptor() {
      return table == null ? super.getDescriptor() : table.getDescriptor();
   }

   /**
    * Invalidate the table filter forcely, and the table filter will perform
    * filtering calculation to validate itself.
    */
   @Override
   public void invalidate() {
      cache = null;
      cpresenters = null;
      rcnt = -1;
      ccnt = -1;
      fireChangeEvent();
   }

   /**
    * Set the table column header.
    * @param col column index.
    * @param hdr column header value.
    */
   public void setColHeader(int col, Object hdr) {
      set(colHeaders, col, hdr);
   }

   /**
    * Get column header.
    * @param col column index.
    * @return column header or null if column header is not set.
    */
   public Object getColHeader(int col) {
      return get(colHeaders, col);
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
      return table != null && table.moreRows(row);
   }

   /**
    * Return the number of rows in the table. The number of rows includes
    * the header rows.
    * @return number of rows in table.
    */
   @Override
   public int getRowCount() {
      if(rcnt >= 0) {
         return rcnt;
      }

      return rcnt = (table == null ? 0 : table.getRowCount());
   }

   /**
    * Set the number of rows.
    * @param rows number of rows.
    */
   public void setRowCount(int rows) {
      try {
         rcnt = -1;
         Method mtd = table.getClass().getMethod("setRowCount", new Class[] {int.class });
         mtd.invoke(table, new Object[] { rows });
      }
      catch(Exception e) {
      }
   }

   /**
    * Return the number of columns in the table. The number of columns
    * includes the header columns.
    * @return number of columns in table.
    */
   @Override
   public int getColCount() {
      if(ccnt >= 0) {
         return ccnt;
      }

      return ccnt = (table == null ? 0 : table.getColCount());
   }

   /**
    * Set the number of columns.
    * @param cols number of columns.
    */
   public void setColCount(int cols) {
      try {
         ccnt = -1;
         Method mtd = table.getClass().getMethod("setColCount",
            new Class[] {int.class });

         mtd.invoke(table, new Object[] { cols });
      }
      catch(Exception e) {
      }
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
    * Return the data at the specified cell.
    * @param r row number.
    * @param c column number.
    * @return the data at the specified cell.
    */
   public Object getData(int r, int c) {
      Object val = (cache != null) ? cache.get(r, c) : SparseMatrix.NULL;

      // @by larryl, explicitly set value should have the highest priority
      if(val != SparseMatrix.NULL) {
         return val;
      }

      int r2 = getBaseRowIndex(r);
      int c2 = getBaseColIndex(c);

      if(r2 >= 0 && c2 >= 0) {
         return attritable != null ?
            attritable.getData(r2, c2) : table.getObject(r2, c2);
      }
      else {
         return getObject(r, c);
      }
   }

   /**
    * Return the data at the specified cell.
    * @param r row number.
    * @param c column number.
    * @param val cell value.
    */
   public void setData(int r, int c, Object val) {
      int r2 = getBaseRowIndex(r);
      int c2 = getBaseColIndex(c);

      if(r2 >= 0 && c2 >= 0) {
         if(attritable != null) {
            attritable.setData(r2, c2, val);
         }
         else {
            // @by larryl, if the table doesn't support setObject, set in this
            // attribute table
            try {
               table.setObject(r2, c2, val);
            }
            catch(RuntimeException ex) {
               LOG.debug("Failed to set table value [" + r + "," + c +
                  "]: " + val, ex);
               setObject(r, c, val);
            }
         }
      }
      else {
         setObject(r, c, val);
      }
   }

   /**
    * Return the value at the specified cell.
    * @param r row number.
    * @param c column number.
    * @return the value at the location.
    */
   @Override
   public Object getObject(int r, int c) {
      Object val = (cache != null) ? cache.get(r, c) : SparseMatrix.NULL;
      return (val == SparseMatrix.NULL) ? getObject0(r, c) : format(r, c, val);
   }

   /**
    * Set a cell value.
    * @param r row index.
    * @param c column index.
    * @param val cell value.
    */
   @Override
   public void setObject(int r, int c, Object val) {
      if(cache == null) {
         cache = new SparseMatrix();
      }

      cache.set(r, c, val);
      fireChangeEvent();
   }

   /**
    * Return the value at the specified cell.
    * @param r row number.
    * @param c column number.
    * @return the value at the location.
    */
   public Object getObject0(int r, int c) {
      Object obj = null;

      if(r == 0) {
         obj = getColHeader(c);
      }

      if(obj == null) {
         obj = table.getObject(r, c);
      }

      return format(r, c, obj);
   }

   /**
    * Format or add presenter to an object.
    */
   protected Object format(int r, int c, Object obj) {
      // @by larryl, allow null value to be formatted and checked with presenter
      if(check) {
         Object obj2 = obj;
         Format fm = getCellFormat(r, c);

         // @by yuz, fix customer bug bug1193242964985 prior to bug1187161786546,
         // allow null value to be formatted by MessageFormat
         if(fm != null && (obj != null || inetsoft.util.MessageFormat.isMessageFormat(fm))) {
            synchronized(fm) {
               try {
                  if((fm instanceof NumberFormat) && !(obj instanceof Number)) {
                     try {
                        obj2 = fm.format(Double.valueOf(obj.toString()));
                     }
                     catch(NumberFormatException fmt) {
                     }
                  }
                  // if date format, only format date, otherwise a number
                  // (e.g. 1) will be formatted into date format by java
                  else if(fm instanceof DateFormat) {
                     if(obj instanceof Date || obj instanceof Number) {
                        // don't apply DateFormat to 0 when fillBlankWithZero is set
                        if(!(Boolean.TRUE.equals(getProperty("fillBlankWithZero")) &&
                           obj instanceof Double && obj.equals(0d)))
                        {
                           obj2 = fm.format(obj);
                        }
                     }

                     if(obj instanceof DCMergeDatesCell) {
                        ((DCMergeDatesCell) obj).setFormat(fm);
                     }
                  }
                  else if(inetsoft.util.MessageFormat.isMessageFormat(fm)) {
                     // fix bug1363324002207
                     if(obj == null) {
                        obj = "";
                     }

                     Object[] arr = obj instanceof Object[] ? (Object[]) obj : new Object[] {obj};
                     obj2 = fm.format(arr);
                  }
                  else {
                     obj2 = fm.format(obj);
                  }
               }
               catch(Exception e) {
                  LOG.debug("Failed to format value \"" + obj + "\" with formatter: " + fm, e);
               }
            }
         }

         Presenter p = getPresenter(r, c);

         if(p != null && p.isPresenterOf(obj)) {
            return new PresenterPainter((p.isRawDataRequired() ? obj : obj2), p);
         }

         if(p == null) {
            // optimize
            p = getCachedColPresenter(c);

            if(p != null) {
               setPresenter(r, c, p);

               if(p.isPresenterOf(obj)) {
                  return new PresenterPainter((p.isRawDataRequired() ? obj : obj2), p);
               }
            }
         }

         return obj2;
      }

      return obj;
   }

   private Presenter getCachedColPresenter(int c) {
      if(cpresenters == null) {
         boolean more = moreRows(0);
         int ccnt = table == null ? 0 : table.getColCount();
         cpresenters = new Presenter[ccnt];

         for(int i = 0; i < ccnt; i++) {
            Object obj = more ? table.getObject(0, i) : null;
            String header = obj == null ? null : obj.toString();
            cpresenters[i] = getPresenter(header, i);
         }
      }

      return c >= cpresenters.length ? null : cpresenters[c];
   }

   /**
    * Return the number of rows on the top of the table to be treated
    * as header rows.
    * @return number of header rows.
    */
   @Override
   public int getHeaderRowCount() {
      return (headerRow == null) ? table.getHeaderRowCount() : headerRow;
   }

   /**
    * Set the number of header rows.
    * @param headerRow number of header rows.
    */
   public void setHeaderRowCount(int headerRow) {
      if(attritable != null) {
         attritable.setHeaderRowCount(headerRow);
         this.headerRow = null;
      }
      else {
         this.headerRow = headerRow;
      }
   }

   /**
    * Return the number of columns on the left of the table to be
    * treated as header columns.
    */
   @Override
   public int getHeaderColCount() {
      // @by billh, header col count should not be greater than col count
      return (headerCol == null) ?
         Math.min(table.getColCount(), table.getHeaderColCount()) :
         Math.min(table.getColCount(), headerCol);
   }

   /**
    * Set the number of header columns.
    * @param headerCol number of header columns.
    */
   public void setHeaderColCount(int headerCol) {
      if(attritable != null) {
         attritable.setHeaderColCount(headerCol);
         this.headerCol = null;
      }
      else {
         this.headerCol = headerCol;
      }
   }

   /**
    * Return the number of rows on the bottom of the table to be treated
    * as tail rows.
    * @return number of tail rows.
    */
   @Override
   public int getTrailerRowCount() {
      return (tailRow == null) ? table.getTrailerRowCount() : tailRow;
   }

   /**
    * Set the number of tail rows.
    * @param tailRow number of tail rows.
    */
   public void setTrailerRowCount(int tailRow) {
      this.tailRow = tailRow;
   }

   /**
    * Return the number of columns on the right of the table to be
    * treated as tail columns.
    */
   @Override
   public int getTrailerColCount() {
      return (tailCol == null) ? table.getTrailerColCount() : tailCol;
   }

   /**
    * Set the number of tail columns.
    * @param tailCol number of tail columns.
    */
   public void setTrailerColCount(int tailCol) {
      this.tailCol = tailCol;
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
      Integer h = (Integer) get(rowHeights, row);

      if(h != null) {
         return h;
      }

      return (rowHeight == null) ? (autorow ? -1 : table.getRowHeight(row)) : rowHeight;
   }

   /**
    * Get the row height explicitly set on this table.
    */
   Integer getRowHeight0(int row) {
      return (Integer) get(rowHeights, row);
   }

   /**
    * If autosize is enabled, the base table row height is ignored. The
    * ReportSheet will calculate the height base on the row contents.
    * @param auto true to enable autosize.
    */
   public void setRowAutoSize(boolean auto) {
      autorow = auto;
   }

   /**
    * Set the row height of the table. All rows are set to the same height.
    * If the height is 0, row height is reset and RowAutoSize property
    * controls how the row height is calculated.
    * @param h minimum row height.
    */
   public void setRowHeight(int h) {
      rowHeight = (h > 0) ? h : null;
   }

   /**
    * Set the row height of a row in the table.
    * @param r row number.
    * @param h minimum row height.
    */
   public void setRowHeight(int r, int h) {
      set(rowHeights, r, h);
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
      Integer w = (Integer) get(colWidths, col);

      if(w != null) {
         return w;
      }

      return autocol ? -1 : table.getColWidth(col);
   }

   /**
    * If autosize is enabled, the base table column width is ignored. The
    * ReportSheet will calculate the width base on the column contents.
    * @param auto true to enable autosize.
    */
   public void setColAutoSize(boolean auto) {
      autocol = auto;
   }

   /**
    * Set the width of the column in pixels. This overrides auto size
    * setting for the column width. The StyleConstants.REMAINDER can
    * be passed in as the width. For the meaning of the constant,
    * refer to the documentation on the TableLens.getColWidth() method.
    * @param col column index.
    * @param width column width.
    */
   public void setColWidth(int col, int width) {
      set(colWidths, col, width);
   }

   /**
    * Return the color for drawing the row border lines.
    * @param r row number.
    * @param c column number.
    * @return ruling color.
    */
   @Override
   public Color getRowBorderColor(int r, int c) {
      Color color = rowborderCmap == null ? null :
         (Color) get(rowborderCmap, r + 1, c + 1);

      if(color != null) {
         return color;
      }

      color = tableRowBorderColor;
      return (color == null) ? table.getRowBorderColor(r, c) : color;
   }

   /**
    * Set the table level row border color.
    * @param color row border color.
    */
   public void setRowBorderColor(Color color) {
      tableRowBorderColor = color;
   }

   /**
    * Set the color for the row border.
    * @param r row number.
    * @param color border color.
    */
   public void setRowBorderColor(int r, Color color) {
      if(rowborderCmap == null) {
         rowborderCmap = new SparseIndexedMatrix();
      }

      rowborderCmap.setRow(r + 1, color);
   }

   /**
    * Set the color for the row border at the specified cell. This overrides
    * the border color setting on the entire row.
    * @param r row number.
    * @param c column number.
    * @param color border color.
    */
   public void setRowBorderColor(int r, int c, Color color) {
      if(rowborderCmap == null) {
         rowborderCmap = new SparseIndexedMatrix();
      }

      rowborderCmap.set(r + 1, c + 1, color);
   }

   /**
    * Return the color for drawing the column border lines.
    * @param r row number.
    * @param c column number.
    * @return ruling color.
    */
   @Override
   public Color getColBorderColor(int r, int c) {
      Color color = colborderCmap == null ? null :
         (Color) get(colborderCmap, r + 1, c + 1);

      if(color != null) {
         return color;
      }

      color = tableColBorderColor;

      return (color == null) ? table.getColBorderColor(r, c) : color;
   }

   /**
    * Set the table level column border color.
    * @param color column border color.
    */
   public void setColBorderColor(Color color) {
      tableColBorderColor = color;
   }

   /**
    * Set the color for the column border.
    * @param c column number.
    * @param color border color.
    */
   public void setColBorderColor(int c, Color color) {
      if(colborderCmap == null) {
         colborderCmap = new SparseIndexedMatrix();
      }

      colborderCmap.setColumn(c + 1, color);
   }

   /**
    * Set the color for the column border.
    * @param r row number.
    * @param c column number.
    * @param color border color.
    */
   public void setColBorderColor(int r, int c, Color color) {
      if(colborderCmap == null) {
         colborderCmap = new SparseIndexedMatrix();
      }

      colborderCmap.set(r + 1, c + 1, color);
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
      Integer style = rowbordermap == null ? null :
         (Integer) get(rowbordermap, r + 1, c + 1);

      if(style != null) {
         return style;
      }

      style = tableRowBorder;
      return (style == null) ? table.getRowBorder(r, c) : style;
   }

   /**
    * Set the table level row border.
    * @param style row border style.
    */
   public void setRowBorder(int style) {
      tableRowBorder = style;
   }

   /**
    * Set the style of the row border.
    * @param r row number.
    * @param style border style.
    */
   public void setRowBorder(int r, int style) {
      if(rowbordermap == null) {
         rowbordermap = new SparseIndexedMatrix();
      }

      rowbordermap.setRow(r + 1, style);
   }

   /**
    * Set the style of the row border.
    * @param r row number.
    * @param style border style.
    */
   public void setRowBorder(int r, int c, int style) {
      if(rowbordermap == null) {
         rowbordermap = new SparseIndexedMatrix();
      }

      rowbordermap.set(r + 1, c + 1, style);
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
      Integer style = colbordermap == null ? null :
         (Integer) get(colbordermap, r + 1, c + 1);

      if(style != null) {
         return style;
      }

      style = tableColBorder;

      return (style == null) ? table.getColBorder(r, c) : style;
   }

   /**
    * Set the table level column border.
    * @param style column border style.
    */
   public void setColBorder(int style) {
      tableColBorder = style;
   }

   /**
    * Set the style of the column border.
    * @param c column number.
    * @param style border style.
    */
   public void setColBorder(int c, int style) {
      if(colbordermap == null) {
         colbordermap = new SparseIndexedMatrix();
      }

      colbordermap.setColumn(c + 1, style);
   }

   /**
    * Set the style of the column border.
    * @param r row number.
    * @param c column number.
    * @param style border style.
    */
   public void setColBorder(int r, int c, int style) {
      if(colbordermap == null) {
         colbordermap = new SparseIndexedMatrix();
      }

      colbordermap.set(r + 1, c + 1, style);
   }

   /**
    * Return the cell gap space.
    * @param r row number.
    * @param c col number.
    * @return cell gap space.
    */
   @Override
   public Insets getInsets(int r, int c) {
      Insets gap = insetsmap == null ? null : (Insets) get(insetsmap, r, c);

      if(gap != null) {
         return gap;
      }

      gap = tableGap;

      return (gap == null) ? table.getInsets(r, c) : gap;
   }

   /**
    * Set the table level cell padding.
    * @param insets cell padding.
    */
   public void setInsets(Insets insets) {
      tableGap = insets;
   }

   /**
    * Set the cell insets for the cell.
    * @param r row number.
    * @param c column number.
    * @param insets cell insets.
    */
   public void setInsets(int r, int c, Insets insets) {
      if(insetsmap == null) {
         insetsmap = new SparseIndexedMatrix();
      }

      insetsmap.set(r, c, insets);
   }

   /**
    * Set the cell insets for the row.
    * @param r row number.
    * @param insets cell insets.
    */
   public void setRowInsets(int r, Insets insets) {
      if(insetsmap == null) {
         insetsmap = new SparseIndexedMatrix();
      }

      insetsmap.setRow(r, insets);
   }

   /**
    * Set the cell insets for the col.
    * @param c col number.
    * @param insets cell insets.
    */
   public void setColInsets(int c, Insets insets) {
      if(insetsmap == null) {
         insetsmap = new SparseIndexedMatrix();
      }

      insetsmap.setColumn(c, insets);
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
      Dimension span = spanmap == null ? null : (Dimension) get(spanmap, r, c);
      return (span == null) ? table.getSpan(r, c) : span;
   }

   /**
    * Return the span setting for the cell that is set explicitly using
    * setSpan().
    * @param r row number.
    * @param c column number.
    * @return span cell dimension.
    */
   final Dimension getSpan0(int r, int c) {
      return (Dimension) get(spanmap, r, c);
   }

   /**
    * Set the span dimension of a cell.
    * @param r row number.
    * @param c column number.
    * @param span span dimension.
    */
   public void setSpan(int r, int c, Dimension span) {
      if(spanmap == null) {
         spanmap = new SparseIndexedMatrix();
      }

      spanmap.set(r, c, span);
   }

   /**
    * Remove all span setting.
    */
   public void removeAllSpans() {
      spanmap = null;
   }

   public void clearRowHeights() {
      rowHeights.clear();

      AttributeTableLens tbl = (AttributeTableLens)
         Util.getNestedTable(getTable(), AttributeTableLens.class);

      if(tbl != null) {
         tbl.clearRowHeights();
      }
   }

   /**
    * @hidden
    * Return the user alignment for the row.
    * @param r row number.
    * @param c col number.
    * @return cell alignment.
    */
   @Override
   protected int getUserAlignment(int r, int c) {
      Integer align = alignmap == null ? null : (Integer) get(alignmap, r, c);

      if(align != null) {
         return align;
      }

      return super.getUserAlignment(r, c);
   }

   /**
    * Return the alignment for the row.
    * @param r row number.
    * @param c col number.
    * @return cell alignment.
    */
   @Override
   public int getAlignment(int r, int c) {
      Integer align = alignmap == null ? null : (Integer) get(alignmap, r, c);

      if(align != null) {
         return align;
      }

      align = tableAlign;

      return (align == null) ? table.getAlignment(r, c) : align;
   }

   /**
    * Set the table level cell alignment.
    * @param align alignment.
    */
   public void setAlignment(int align) {
      tableAlign = align;
   }

   /**
    * Set the cell alignment.
    * @param r row number.
    * @param c column number.
    * @param align alignment flagn, pass -1 to clear the setting.
    */
   public void setAlignment(int r, int c, int align) {
      Integer val = (align == -1) ? null : align;

      if(alignmap == null) {
         alignmap = new SparseIndexedMatrix();
      }

      alignmap.set(r, c, val);
   }

   /**
    * Set the row cell alignment.
    * @param r row number.
    * @param align alignment flag, pass -1 to clear the setting.
    */
   public void setRowAlignment(int r, int align) {
      Integer val = (align == -1) ? null : align;

      if(alignmap == null) {
         alignmap = new SparseIndexedMatrix();
      }

      alignmap.setRow(r, val);
   }

   /**
    * Set the col cell alignment.
    * @param c col number.
    * @param align alignment flag, pass -1 to clear the setting.
    */
   public void setColAlignment(int c, int align) {
      Integer val = (align == -1) ? null : align;

      if(alignmap == null) {
         alignmap = new SparseIndexedMatrix();
      }

      alignmap.setColumn(c, val);
   }

   /**
    * Return the per row font. Return null to use default font.
    * @param r row number.
    * @param c column number.
    * @return font for the specified row.
    */
   @Override
   public Font getFont(int r, int c) {
      Font font = fontmap == null ? null : (Font) get(fontmap, r, c);

      if(font == null) {
         font = tableFont;
      }

      return (font == null) ? table.getFont(r, c) : font;
   }

   /**
    * Set the font for the cell.
    * @param r row number.
    * @param c column number.
    * @param font row font.
    */
   public void setFont(int r, int c, Font font) {
      if(fontmap == null) {
         fontmap = new SparseIndexedMatrix();
      }

      fontmap.set(r, c, font);
   }

   /**
    * Set the default font for the table.
    * @param font table font.
    */
   public void setFont(Font font) {
      this.tableFont = font;
   }

   /**
    * Set the font for the row.
    * @param r row number.
    * @param font row font.
    */
   public void setRowFont(int r, Font font) {
      if(fontmap == null) {
         fontmap = new SparseIndexedMatrix();
      }

      fontmap.setRow(r, font);
   }

   /**
    * Set the font for the col.
    * @param c col number.
    * @param font col font.
    */
   public void setColFont(int c, Font font) {
      if(fontmap == null) {
         fontmap = new SparseIndexedMatrix();
      }

      fontmap.setColumn(c, font);
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
      Boolean lw = wrapmap == null ? null : (Boolean) get(wrapmap, r, c);

      if(lw != null) {
         return lw;
      }

      lw = tableWrap;

      return (lw == null) ? table.isLineWrap(r, c) : lw;
   }

   /**
    * Set the table line wrap setting.
    * @param wrap true to allow lines to wrap.
    */
   public void setLineWrap(boolean wrap) {
      tableWrap = wrap;
   }

   /**
    * Set the line wrap for the cell.
    * @param r row number.
    * @param c column number.
    * @param wrap row wrap.
    */
   public void setLineWrap(int r, int c, boolean wrap) {
      if(wrapmap == null) {
         wrapmap = new SparseIndexedMatrix();
      }

      wrapmap.set(r, c, wrap);
   }

   /**
    * Set the line wrap for the row.
    * @param r row number.
    * @param wrap row wrap.
    */
   public void setRowLineWrap(int r, boolean wrap) {
      if(wrapmap == null) {
         wrapmap = new SparseIndexedMatrix();
      }

      wrapmap.setRow(r, wrap);
   }

   /**
    * Set the line wrap for the col.
    * @param c col number.
    * @param wrap col wrap.
    */
   public void setColLineWrap(int c, boolean wrap) {
      if(wrapmap == null) {
         wrapmap = new SparseIndexedMatrix();
      }

      wrapmap.setColumn(c, wrap);
   }

   /**
    * Set the line wrap for the col.
    * @param c col number.
    * @param zero col suppress if zero.
    */
   public void setColSuppressIfZero(int c, boolean zero) {
      set(colzero, c, zero);
   }

   /**
    * Return the per col suppress if zero.
    * @param c column number.
    * @return true if col suppress if zero.
    */
   public boolean isColSuppressIfZero(int c) {
      Boolean lw = colzero == null ? null : (Boolean) get(colzero, c);

      if(lw != null) {
         return lw;
      }

      return false;
   }

   /**
    * Return the per cell suppress if zero mode.
    * @param r row number
    * @param c column number
    * @return true when suppress if zero should be done
    */
   public boolean isSuppressIfZero(int r, int c) {
      boolean sup = attritable == null ?
         false : attritable.isSuppressIfZero(r, c);
      return sup || isColSuppressIfZero(c);
   }

   /**
    * Set the line wrap for the col.
    * @param c col number.
    * @param dup col suppress if duplicate.
    */
   public void setColSuppressIfDuplicate(int c, boolean dup) {
      set(coldup, c, dup);
   }

   /**
    * Return the per col suppress if duplicate.
    * @param c column number.
    * @return true if col suppress if duplicate.
    */
   public boolean isColSuppressIfDuplicate(int c) {
      Boolean lw = coldup == null ? null : (Boolean) get(coldup, c);

      if(lw != null) {
         return lw;
      }

      return false;
   }

   /**
    * Return the per cell suppress if duplicate mode.
    * @param r row number
    * @param c column number
    * @return true when suppress if duplicate should be done
    */
   public boolean isSuppressIfDuplicate(int r, int c) {
      boolean sup = attritable == null ?
         false : attritable.isSuppressIfDuplicate(r, c);
      return sup || isColSuppressIfDuplicate(c);
   }

   /**
    * Return the foreground color for the specified row. Return null to
    * use default color.
    * @param r row number.
    * @param c col number.
    * @return foreground color for the specified row.
    */
   @Override
   public Color getForeground(int r, int c) {
      Color clr = foregroundmap == null ? null :
         (Color) get(foregroundmap, r, c);

      return (clr == null) ? table.getForeground(r, c) : clr;
   }

   /**
    * Set the foreground color for the specified cell.
    * @param r row number.
    * @param c column number.
    * @param color foreground color.
    */
   public void setForeground(int r, int c, Color color) {
      if(foregroundmap == null) {
         foregroundmap = new SparseIndexedMatrix();
      }

      foregroundmap.set(r, c, color);
   }

   /**
    * Set the foreground color for the specified row.
    * @param r row number.
    * @param color foreground color.
    */
   public void setRowForeground(int r, Color color) {
      if(foregroundmap == null) {
         foregroundmap = new SparseIndexedMatrix();
      }

      foregroundmap.setRow(r, color);
   }

   /**
    * Set the foreground color for the specified col.
    * @param c col number.
    * @param color foreground color.
    */
   public void setColForeground(int c, Color color) {
      if(foregroundmap == null) {
         foregroundmap = new SparseIndexedMatrix();
      }

      foregroundmap.setColumn(c, color);
   }

   /**
    * Return the background color for the specified row. Return null to
    * use default color.
    * @param r row number.
    * @param c col number.
    * @return background color for the specified row.
    */
   @Override
   public Color getBackground(int r, int c) {
      // Bug #56824, don't apply bg to the col headers
      if(table instanceof  CrossTabFilter && !((CrossTabFilter) table).isKeepColumnHeaders() &&
         r < table.getHeaderRowCount() && c < table.getHeaderColCount())
      {
         return null;
      }

      Color clr = backgroundmap == null ? null :
         (Color) get(backgroundmap, r, c);

      return (clr == null) ? table.getBackground(r, c) : clr;
   }

   public int getAlpha(int r, int c) {
      return table.getAlpha(r, c);
   }

   /**
    * Return the background color for the specified row. Return null to
    * use default color.
    * @param r row number.
    * @param c col number.
    * @param spanRow row index of the specified span
    * @return background color for the specified row.
    */
   @Override
   public Color getBackground(int r, int c, int spanRow) {
      Color clr = backgroundmap == null ? null :
         (Color) get(backgroundmap, r, c);

      return (clr == null) ? table.getBackground(r, c, spanRow) : clr;
   }

   /**
    * Set the background color for the specified cell.
    * @param r row number.
    * @param c column number.
    * @param color background color.
    */
   public void setBackground(int r, int c, Color color) {
      if(backgroundmap == null) {
         backgroundmap = new SparseIndexedMatrix();
      }

      backgroundmap.set(r, c, color);
   }

   /**
    * Set the background color for the specified row.
    * @param r row number.
    * @param color background color.
    */
   public void setRowBackground(int r, Color color) {
      if(backgroundmap == null) {
         backgroundmap = new SparseIndexedMatrix();
      }

      backgroundmap.setRow(r, color);
   }

   /**
    * Set the background color for the specified col.
    * @param c col number.
    * @param color background color.
    */
   public void setColBackground(int c, Color color) {
      if(backgroundmap == null) {
         backgroundmap = new SparseIndexedMatrix();
      }

      backgroundmap.setColumn(c, color);
   }

   /**
    * Get the presenter for the specified cell.
    * @param row row number
    * @param col column number.
    * @return presenter for this cell.
    */
   public Presenter getPresenter(int row, int col) {
      return (Presenter) get(presentermap, row, col);
   }

   /**
    * Get the presenter for the specified column.
    * @param col column cell presenter.
    * @return presenter for this column.
    */
   public Presenter getPresenter(int col) {
      return (presentermap != null) ?
         (Presenter) presentermap.getColumn(col) : null;
   }

   /**
    * Get the presenter for the specified column.
    * @param header name of the cell presenter.
    * @return presenter for this column.
    */
   public Presenter getPresenter(String header, int col) {
      if(col >= 0 && presentermap != null) {
         return (Presenter) presentermap.getColumn(col);
      }

      return presenterColMap != null ? presenterColMap.get(header) : null;
   }

   /**
    * Set the presenter for the specified column. The column is identified
    * by the header. If the header does not exist in the table, a
    * NoSuchElementException is thrown.
    * @param header column header.
    * @param p presenter.
    */
   public void setPresenter(String header, Presenter p) {
      int col = findColumn(header);

      if(col >= 0) {
         setPresenter(col, p);
      }
      else {
         if(presenterColMap == null) {
            presenterColMap = new HashMap<>();
         }

         presenterColMap.put(header, p);
         check = true;
         fireChangeEvent();
      }
   }

   /**
    * Set the presenter for the specified cell.
    * @param r row number.
    * @param c column number.
    * @param presenter cell presenter.
    */
   public void setPresenter(int r, int c, Presenter presenter) {
      if(presentermap == null) {
         presentermap = new SparseIndexedMatrix();
      }

      presentermap.set(r, c, presenter);
      check = true;
      fireChangeEvent();
   }

   /**
    * Set the presenter for the specified column. The Presenter is used
    * by all cells in the column for printing.
    * @param col column number.
    * @param p presenter.
    */
   public void setPresenter(int col, Presenter p) {
      if(presentermap == null) {
         presentermap = new SparseIndexedMatrix();
      }

      presentermap.setColumn(col, p);
      check = true;
      fireChangeEvent();
   }

   /**
    * Set the presenter for the specified row. The Presenter is used
    * by all cells in the row for printing. This override the presenter
    * setting for the columns.
    * @param row row number.
    * @param p presenter.
    */
   public void setRowPresenter(int row, Presenter p) {
      if(presentermap == null) {
         presentermap = new SparseIndexedMatrix();
      }

      presentermap.setRow(row, p);
      check = true;
      fireChangeEvent();
   }

   /**
    * Get the format for the specified cell.
    * @param row row number.
    * @param col column number.
    * @return column format.
    */
   public Format getFormat(int row, int col) {
      return getFormat(row, col, false);
   }

   /**
    * Get the format for the specified cell.
    * @param row row number.
    * @param col column number.
    * @return column format.
    */
   public Format getFormat(int row, int col, boolean cellOnly) {
      Format format = getCellFormat(row, col, cellOnly);

      if(format == null && attritable != null) {
         format = attritable.getFormat(row, col, cellOnly);
      }

      return format;
   }

   /**
    * Get the format defined in this table lens.
    */
   public Format getCellFormat(int row, int col) {
      return getCellFormat(row, col, false);
   }

   /**
    * Get the format defined in this table lens.
    */
   public Format getCellFormat(int row, int col, boolean cellOnly) {
      if(cellOnly) {
         return (Format) (formatmap == null ? null : formatmap.get(row, col));
      }

      Format format = null;
      format = (Format) get(formatmap, row, col);

      if(format == null) {
         format = getFormat(col);
      }

      return format;
   }

   /**
    * Get the format associated with the column.
    * @param col column number.
    * @return column format.
    */
   public Format getFormat(int col) {
      Format format = null;

      if(formatmap != null) {
         format = (Format) formatmap.getColumn(col);
      }

      return format;
   }

   /**
    * Get the format associated with a row.
    * @param row row index.
    * @return row format.
    */
   public Format getRowFormat(int row) {
      Format format = null;

      if(formatmap != null) {
         format = (Format) formatmap.getRow(row);
      }

      return format;
   }

   /**
    * Set the format for the specified cell.
    * @param r row number.
    * @param c column number.
    * @param format cell format.
    */
   public void setFormat(int r, int c, Format format) {
      if(formatmap == null) {
         formatmap = new SparseIndexedMatrix();
      }

      formatmap.set(r, c, format);
      check = true;
      fireChangeEvent();
   }

   /**
    * Set the format for the specified column. The column is identified
    * by the header. If the header does not exist in the table, a
    * NoSuchElementException is thrown.
    * call is quitely ignored.
    * @param header column header.
    * @param p format.
    */
   public void setFormat(String header, Format p) {
      int col = findColumn(header);

      if(col >= 0) {
         setFormat(col, p);
      }
      else {
         throw new NoSuchElementException(header);
      }
   }

   /**
    * Set the format for the specified column. The format is used to
    * convert the cell objects to their string representation for printing.
    * @param col column number.
    * @param p format.
    */
   public void setFormat(int col, Format p) {
      if(formatmap == null) {
         formatmap = new SparseIndexedMatrix();
      }

      formatmap.setColumn(col, p);
      check = true;
      fireChangeEvent();
   }

   /**
    * Set the format for the specified column. The format is used to
    * convert the cell objects to their string representation for printing.
    * @param row row number.
    * @param p format.
    */
   public void setRowFormat(int row, Format p) {
      if(formatmap == null) {
         formatmap = new SparseIndexedMatrix();
      }

      formatmap.setRow(row, p);
      check = true;
      fireChangeEvent();
   }

   /**
    * Find the column with the specified header.
    * @return column index or -1 if not found.
    */
   public int findColumn(String header) {
      if(moreRows(0)) {
         for(int i = 0; i < getColCount(); i++) {
            // shouldn't use presenter here in case it's set
            Object hdr = table.getObject(0, i);

            if(hdr != null && header.equals(hdr)) {
               return i;
            }
         }
      }

      if(header.startsWith("Column [")) {
         try {
            int start = header.indexOf("Column [");
            String index = header.substring(start, header.indexOf(']', start));

            return Integer.parseInt(index);
         }
         catch(Throwable e) {
         }
      }

      return -1;
   }

   /**
    * Set the hyperlink for the specified cell.
    * @param r row number.
    * @param c column number.
    * @param link the specified hyperlink.
    */
   public void setHyperlink(int r, int c, Hyperlink.Ref link) {
      if(linkmap == null) {
         linkmap = new SparseIndexedMatrix();
      }

      linkmap.set(r, c, link);
   }

   /**
    * Check if contains hyperlink definitation.
    */
   public boolean containsLink() {
      if(linkmap != null && !linkmap.isEmpty()) {
         return true;
      }

      return attritable == null ? false : attritable.containsLink();
   }

   /**
    * Get hyperlink of a table cell.
    * @param r the specified row
    * @param c the specified col
    */
   public Hyperlink.Ref getHyperlink(int r, int c) {
      Object link = (linkmap == null || linkmap.isEmpty()) ?
         null : linkmap.get(r, c);
      return link != null ? (Hyperlink.Ref) link :
         (attritable == null ? null : attritable.getHyperlink(r, c));
   }

   /**
    * Get the object in a vector. Ensures the size of the vector is at
    * least as large as the index.
    * @param v vector.
    * @param idx element index.
    * @return object the the position in the vector.
    */
   private Object get(VectorHolder v, int idx) {
      if(v.vector == null || v.vector.size() <= idx) {
         return null;
      }

      return v.vector.elementAt(idx);
   }

   /**
    * Set the value in a vector. Ensures the size of the vector is large
    * enough to hold the value at the index.
    * @param v vector.
    * @param idx element index.
    * @param obj new value.
    */
   private void set(VectorHolder v, int idx, Object obj) {
      synchronized(v) {
         if(obj == null && (v.vector == null || v.vector.size() <= idx)) {
            return;
         }
         else if(v.vector == null) {
            v.vector = new Vector();
         }

         if(v.vector.size() <= idx) {
            v.vector.setSize(idx + 1);
         }

         v.vector.setElementAt(obj, idx);
      }
   }

   /**
    * Get a value from a matrix in cell, row, column order.
    */
   private Object get(SparseIndexedMatrix map, int r, int c) {
      if(map == null || map.isEmpty()) {
         return null;
      }

      Object obj = map.get(r, c);

      if(obj != null) {
         return obj;
      }

      obj = map.getRow(r);

      if(obj != null) {
         return obj;
      }

      return map.getColumn(c);
   }

   static class VectorHolder implements java.io.Serializable, Cloneable {
      public void insert(int idx, int n) {
         if(vector == null || idx >= vector.size()) {
            return;
         }

         for(int i = 0; i < n; i++) {
            vector.insertElementAt(null, idx);
         }
      }

      public void remove(int idx, int n) {
         if(vector == null || idx >= vector.size()) {
            return;
         }

         if(idx + n > vector.size()) {
            n = vector.size() - idx;
         }

         for(int i = 0; i < n; i++) {
            vector.removeElementAt(idx);
         }
      }

      @Override
      public Object clone() {
         VectorHolder holder = new VectorHolder();

         if(vector != null) {
            holder.vector = Tool.deepCloneCollection(vector);
         }

         return holder;
      }

      public void clear() {
         vector = null;
      }

      public String toString() {
         return vector + "";
      }

      private Vector vector;
   }

   /**
    * Make a copy of this table. The table data is shared and the attributes
    * are not copied. This only works to make a copy of the data and share
    * the data to avoid keeping data in two places. If any attributes are
    * changed, they are lost and need to be applied on the new copy.
    */
   @Override
   public AttributeTableLens clone() {
      try {
         AttributeTableLens attr = (AttributeTableLens) super.clone();
         attr.cache = null;

         if(changeListener != null) {
            attr.changeListener = new DefaultTableChangeListener(attr);
         }

         cloneAttributes(attr);

         return attr;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone object", ex);
      }

      return null;
   }


   /**
    * Make copy of the attributes to a new attribute table lens.
    */
   public void cloneAttributes(AttributeTableLens attr) {
      try {
         if(colWidths != null) {
            attr.colWidths = (VectorHolder) colWidths.clone();
         }

         if(foregroundmap != null) {
            attr.foregroundmap = (SparseIndexedMatrix) foregroundmap.clone();
         }

         if(backgroundmap != null) {
            attr.backgroundmap = (SparseIndexedMatrix) backgroundmap.clone();
         }

         if(colbordermap != null) {
            attr.colbordermap = (SparseIndexedMatrix) colbordermap.clone();
         }

         if(rowbordermap != null) {
            attr.rowbordermap = (SparseIndexedMatrix) rowbordermap.clone();
         }

         if(rowborderCmap != null) {
            attr.rowborderCmap = (SparseIndexedMatrix) rowborderCmap.clone();
         }

         if(colborderCmap != null) {
            attr.colborderCmap = (SparseIndexedMatrix) colborderCmap.clone();
         }

         if(alignmap != null) {
            attr.alignmap = (SparseIndexedMatrix) alignmap.clone();
         }

         if(fontmap != null) {
            attr.fontmap = (SparseIndexedMatrix) fontmap.clone();
         }

         if(colzero != null) {
            attr.colzero = (VectorHolder) colzero.clone();
         }

         if(coldup != null) {
            attr.coldup = (VectorHolder) coldup.clone();
         }

         if(wrapmap != null) {
            attr.wrapmap = (SparseIndexedMatrix) wrapmap.clone();
         }

         if(spanmap != null) {
            attr.spanmap = (SparseIndexedMatrix) spanmap.clone();
         }

         if(insetsmap != null) {
            attr.insetsmap = (SparseIndexedMatrix) insetsmap.clone();
         }

         if(presentermap != null) {
            attr.presentermap = (SparseIndexedMatrix) presentermap.clone();
         }

         if(formatmap != null) {
            attr.formatmap = (SparseIndexedMatrix) formatmap.clone();
         }

         if(linkmap != null) {
            attr.linkmap = (SparseIndexedMatrix) linkmap.clone();
         }

         if(rowHeights != null) {
            attr.rowHeights = (VectorHolder) rowHeights.clone();
         }

         if(colHeaders != null) {
            attr.colHeaders = (VectorHolder) colHeaders.clone();
         }

         if(presenterColMap != null) {
            attr.presenterColMap = (HashMap) presenterColMap.clone();
         }

         // @by stephenwebster, re-fix bug1417056939537
         // any independent call to copy this attribute
         // table will not use the applied presenter or format without copying
         // this flag.  e.g. in script.
         attr.check = this.check;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone attributes", ex);
      }
   }

   /**
    * Dispose the table to clear up temporary resources.
    */
   @Override
   public void dispose() {
      if(table != null) {
         table.dispose();
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
      col = getBaseColIndex(col);

      return identifier == null ? table.getColumnIdentifier(col) : identifier;
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

   protected TableLens table; // base table
   protected AttributeTableLens attritable; // base table if is attribute table
   protected SparseMatrix cache = null; // data cache
   protected boolean check = false; // true to check for Presenters and Format
   private transient DefaultTableChangeListener changeListener =
      new DefaultTableChangeListener(this);
   private boolean autorow;
   private boolean autocol; // return -1 for height/width to force calc
   private Integer headerRow = null; // number of header row
   private Integer headerCol = null; // number of header col
   private Integer tailRow = null; // number of tail row
   private Integer tailCol = null; // number of tail col

   private VectorHolder colzero = new VectorHolder(); // col zero
   private VectorHolder coldup = new VectorHolder(); // col dup
   private VectorHolder colHeaders = new VectorHolder(); // hold vector header

   protected SparseIndexedMatrix colbordermap;
   protected SparseIndexedMatrix rowbordermap;
   protected SparseIndexedMatrix rowborderCmap;
   protected SparseIndexedMatrix colborderCmap;
   protected SparseIndexedMatrix foregroundmap;
   protected SparseIndexedMatrix backgroundmap;
   protected SparseIndexedMatrix alignmap;
   protected SparseIndexedMatrix wrapmap;
   protected SparseIndexedMatrix insetsmap;
   protected SparseIndexedMatrix presentermap;
   protected SparseIndexedMatrix linkmap;
   protected SparseIndexedMatrix fontmap;
   VectorHolder colWidths = new VectorHolder(); // column width setting
   VectorHolder rowHeights = new VectorHolder(); // explicit row height
   SparseIndexedMatrix spanmap; // used in DefaultTableLens
   private SparseIndexedMatrix formatmap;
   private Font tableFont = null; // default font
   private Boolean tableWrap = null; // table level wrapping
   private Integer rowHeight = null; // explicit row height
   private Color tableRowBorderColor = null; // table row border color
   private Color tableColBorderColor = null; // table column border color
   private Integer tableRowBorder = null; // table row border
   private Integer tableColBorder = null; // table column border
   private Insets tableGap = null;  // table cell padding
   private Integer tableAlign = null; // table cell alignment
   private int rcnt;
   private int ccnt;
   private HashMap<String, Presenter> presenterColMap;
   private Presenter[] cpresenters;

   private static final Logger LOG =
      LoggerFactory.getLogger(AttributeTableLens.class);
}
