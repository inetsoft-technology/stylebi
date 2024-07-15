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
package inetsoft.report.composition;

import inetsoft.report.*;
import inetsoft.report.event.TableChangeListener;
import inetsoft.report.filter.SortFilter;
import inetsoft.report.internal.Util;
import inetsoft.uql.*;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.util.Tool;

import java.awt.*;
import java.io.PrintWriter;
import java.text.Format;
import java.util.List;
import java.util.*;

/**
 * FormTableLens, the viewsheet form table lens, it keeps form input data and
 * row state at runtime mode.
 *
 * @version 11.2
 * @author InetSoft Technology Corp
 */
public class FormTableLens implements TableLens, TableFilter {
   /**
    * Constructor.
    * @param base the base TableLens.
    */
   public FormTableLens(TableLens base) {
      this.base = base;
      initTable();
   }

   /**
    * Sort.
    * @param col sort column index.
    * @param order sort order.
    */
   public void sort(int col, int order) {
      this.col = col;
      this.order = order;

      int rcnt = getRowCount();
      rowMap = new int[rcnt];

      for(int i = 0; i < rcnt; i++) {
         rowMap[i] = i;
      }

      if(order == StyleConstants.SORT_ASC ||
         order == StyleConstants.SORT_DESC)
      {
         boolean asc = order == StyleConstants.SORT_ASC;
         SortFilter filter = new SortFilter(this, new int[] {col}, asc);
         filter.moreRows(Integer.MAX_VALUE);

         for(int i = 0; i < rcnt; i++) {
            int bidx = filter.getBaseRowIndex(i);
            rowMap[i] = bidx;
         }
      }
   }

   /**
    * Sort.
    */
   private void sort() {
      sort(this.col, this.order);
   }

   /**
    * Init table.
    */
   private void initTable() {
      if(base == null) {
         return;
      }

      base.moreRows(Integer.MAX_VALUE);
      int baseRowCount = base.getRowCount();
      int baseColCount = base.getColCount();

      rows = new FormTableRow[baseRowCount];
      rowMap = new int[baseRowCount];

      for(int i = 0; i < baseRowCount; i++) {
         Object[] data = new Object[baseColCount];

         for(int j = 0; j < baseColCount; j++) {
            data[j] = base.getObject(i, j);
         }

         rows[i] = new FormTableRow(data, i);
         rowMap[i] = i;
      }
   }

   /**
    * Init table.
    */
   public void init(List<FormTableRow> data) {
      if(base == null) {
         return;
      }

      base.moreRows(Integer.MAX_VALUE);
      int rowCnt = data.size();
      rows = new FormTableRow[rowCnt];
      rowMap = new int[rowCnt];

      for(int i = 0; i < rowCnt; i++) {
         rows[i] = data.get(i);
         rowMap[i] = i;
      }
   }

   /**
    * Insert an empty row above the specified row and change row state to added.
    * @param r row number.
    */
   public void insertRow(int r) {
      if(r == 0) {
         throw new RuntimeException("Insert header cell is not allowed!");
      }

      int rcnt = getRowCount();
      int ccnt = getColCount();
      FormTableRow[] nrows = new FormTableRow[rcnt + 1];
      nrows[r] = new FormTableRow(ccnt, r);
      System.arraycopy(rows, 0, nrows, 0, r);
      System.arraycopy(rows, r, nrows, r + 1, rcnt - r);

      initRow(nrows[r]);
      rows = nrows;
      updateRowMap(r, true);
   }

   // set initial values
   private void initRow(FormTableRow row) {
      int ccnt = getColCount();

      // initialize values
      for(int i = 0; i < ccnt; i++) {
         if(getColumnOption(i) instanceof BooleanColumnOption) {
            row.set(i, Boolean.FALSE);
         }
      }
   }

   /**
    * Update the rowMap array when add a row.
    */
   private void updateRowMap(int r, boolean isInsert) {
      int arrayLength = rowMap.length;
      int[] nrowMap = new int[arrayLength + 1];
      int rowc = isInsert ? r : r + 1;

      System.arraycopy(rowMap, 0, nrowMap, 0, rowc);
      System.arraycopy(rowMap, rowc, nrowMap, rowc + 1, arrayLength - rowc);

      nrowMap[rowc] = rowc;

      for(int i = 0; i < arrayLength + 1; i++) {
         if(i != rowc && nrowMap[i] >= rowc) {
            nrowMap[i]++;
         }
      }

      rowMap = nrowMap;
   }

   /**
    * Append an empty row below the specfied row and change row state to added.
    * @param r row number.
    */
   public void appendRow(int r) {
      int rcnt = getRowCount();
      int ccnt = getColCount();
      FormTableRow[] nrows = new FormTableRow[rcnt + 1];
      nrows[r + 1] = new FormTableRow(ccnt, r + 1);
      System.arraycopy(rows, 0, nrows, 0, r + 1);
      System.arraycopy(rows, r + 1, nrows, r + 2, rcnt - r - 1);
      rows = nrows;
      initRow(nrows[r + 1]);
      updateRowMap(r, false);
   }

   /**
    * Delete the specified row.
    * @param r row number.
    */
   public void deleteRow(int r) {
      if(r >= rows.length) {
         return;
      }

      r = rowMap[r];
      FormTableRow row = rows[r];
      int state = row.getRowState();

      if(state == FormTableRow.OLD || state == FormTableRow.CHANGED) {
         drows.add(row);
         row.delete();
      }

      int rcnt = getRowCount();
      FormTableRow[] nrows = new FormTableRow[rcnt - 1];
      System.arraycopy(rows, 0, nrows, 0, r);
      System.arraycopy(rows, r + 1, nrows, r, rcnt - r - 1);
      rows = nrows;
      sort();
   }

   /**
    * Add deleted the row.
    */
   public void addDeleteRows(List<FormTableRow> rows) {
      drows.addAll(rows);
   }

   /**
    * Get all rows.
    */
   public FormTableRow[] rows() {
      return rows;
   }

   /**
    * Get rows by row state.
    * @param state row state.
    */
   public FormTableRow[] rows(int state) {
      List<FormTableRow> list;

      if(state == FormTableRow.DELETED) {
         list = drows;
      }
      else {
         list = new ArrayList<>();

         if(rows != null) {   // @by: ChrisSpagnoli feature1380819006300 2014-10-14
            for(int i = 0; i < rows.length; i++) {
               FormTableRow row = rows[i];

               if(state == row.getRowState()) {
                  list.add(row);
               }
            }
         }
      }

      return list.toArray(new FormTableRow[0]);
   }

   public boolean isChanged() {
      return rows(FormTableRow.ADDED).length != 0 || rows(FormTableRow.CHANGED).length != 0 ||
         rows(FormTableRow.DELETED).length != 0;
   }

   /**
    * Remove delete row.
    */
   public void removeDeletedRow(FormTableRow row) {
      drows.remove(row);
   }

   /**
    * Get row by row index.
    * @param r row number.
    */
   public FormTableRow row(int r) {
      return row0(r);
   }

   /**
    * Get row by row index.
    * @param r row number.
    */
   private FormTableRow row0(int r) {
      if(rowMap == null || r >= rowMap.length) {
         return new FormTableRow(0);
      }

      r = rowMap[r];
      return rows[r];
   }

   /**
    * Get recent changed cells by script.
    */
   public Cell[] getChangedCells() {
      return ccells.toArray(new Cell[0]);
   }

   /**
    * Add changed cell.
    */
   public void addChangedCell(int r, int c) {
      ccells.add(new Cell(r, c, getObject(r, c)));
   }

   /**
    * Clear changed celss.
    */
   public void clearChangedCells() {
      ccells.clear();
   }

   /**
    * Check if there are more rows. The row index is the row that will be
    * accessed. This method must block until the row is available, or
    * return false if the row does not exist in the table. This method is
    * used to iterate through the table, and allow partial table to be
    * accessed in report processing.
    * @param row row number. If EOT is passed in, this method should wait
    * until the table is fully loaded.
    * @return true if the row exists, or false if no more rows.
    */
   @Override
   public boolean moreRows(int row) {
      return row < getRowCount();
   }

   /**
    * Return the number of rows in the table. The number of rows includes
    * the header rows. If the table is loading in background and loading
    * is not done, return the negative number of loaded rows minus 1.
    * @return number of rows in table.
    */
   @Override
   public int getRowCount() {
      return rows == null ? 0 : rows.length;
   }

   /**
    * Return the number of columns in the table. The number of columns
    * includes the header columns.
    * @return number of columns in table.
    */
   @Override
   public int getColCount() {
      return base != null ? base.getColCount() : 0;
   }

   /**
    * Return the number of rows on the top of the table to be treated
    * as header rows.
    * @return number of header rows.  Default is 1.
    */
   @Override
   public int getHeaderRowCount() {
      return base != null ? base.getHeaderRowCount() : 0;
   }

   /**
    * Return the number of columns on the left of the table to be
    * treated as header columns.
    */
   @Override
   public int getHeaderColCount() {
      return base != null ? base.getHeaderColCount() : 0;
   }

   /**
    * Return the number of rows on the bottom of the table to be treated
    * as trailer rows.
    * @return number of header rows.
    */
   @Override
   public int getTrailerRowCount() {
      return base != null ? base.getTrailerRowCount() : 0;
   }

   /**
    * Return the number of columns on the right of the table to be
    * treated as trailer columns.
    */
   @Override
   public int getTrailerColCount() {
      return base != null ? base.getTrailerColCount() : 0;
   }

   /**
    * Check if the value at one cell is null.
    * @param r the specified row index.
    * @param c column number.
    * @return <tt>true</tt> if null, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isNull(int r, int c) {
      return getObject(r, c) == null;
   }

   /**
    * Return the value at the specified cell.
    * @param r row number.
    * @param c column number.
    * @return the value at the location.
    */
   @Override
   public Object getObject(int r, int c) {
      if(rowMap == null || r >= rowMap.length) {
         return "";
      }

      r = rowMap[r];
      return rows[r].get(c);
   }

   /**
    * Return the label at the specified cell.
    * @param r row number.
    * @param c column number.
    * @return the label at the location.
    */
   public String getLabel(int r, int c) {
      if(rowMap == null || r >= rowMap.length) {
         return "";
      }

      r = rowMap[r];
      return rows[r].getLabel(c);
   }

   /**
    * Get the double value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the double value in the specified row.
    */
   @Override
   public double getDouble(int r, int c) {
      return r == 0 || getObject(r, c) == null ?
         0D : Double.parseDouble(getObject(r, c).toString());
   }

   /**
    * Get the float value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the float value in the specified row.
    */
   @Override
   public float getFloat(int r, int c) {
      return r == 0 || getObject(r, c) == null ?
         0F : Float.parseFloat(getObject(r, c).toString());
   }

   /**
    * Get the long value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the long value in the specified row.
    */
   @Override
   public long getLong(int r, int c) {
      return r == 0 || getObject(r, c) == null ?
         0L : Long.parseLong(getObject(r, c).toString());
   }

   /**
    * Get the int value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the int value in the specified row.
    */
   @Override
   public int getInt(int r, int c) {
      return r == 0 || getObject(r, c) == null ?
         0 : Integer.parseInt(getObject(r, c).toString());
   }

   /**
    * Get the short value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the short value in the specified row.
    */
   @Override
   public short getShort(int r, int c) {
      return r == 0 || getObject(r, c) == null ?
         0 : Short.parseShort(getObject(r, c).toString());
   }

   /**
    * Get the byte value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the byte value in the specified row.
    */
   @Override
   public byte getByte(int r, int c) {
      return r == 0 || getObject(r, c) == null ?
         0 : Byte.parseByte(getObject(r, c).toString());
   }

   /**
    * Get the boolean value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the boolean value in the specified row.
    */
   @Override
   public boolean getBoolean(int r, int c) {
      return r != 0 && getObject(r, c) != null && Boolean.parseBoolean(getObject(r, c).toString());
   }

   /**
    * Set the cell value, if the row state is old, change row state to changed.
    * @param r row number.
    * @param c column number.
    * @param v cell value.
    */
   @Override
   public void setObject(int r, int c, Object v) {
      r = rowMap[r];
      rows[r].set(c, v);
   }

   /**
    * Set the cell label, if the row state is old, change row state to changed.
    * @param r row number.
    * @param c column number.
    * @param label cell label.
    */
   public void setLabel(int r, int c, String label) {
      r = rowMap[r];
      rows[r].setLabel(c, label);
   }

   /**
    * Get the current column content type.
    * @param col column number.
    * @return column type.
    */
   @Override
   public Class getColType(int col) {
      return base != null ? base.getColType(col) : String.class;
   }

   /**
    * Check if is primitive.
    * @return <tt>true</tt> if is primitive, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isPrimitive(int col) {
      return base != null && base.isPrimitive(col);
   }

   /**
    * Dispose the table to clear up temporary resources.
    */
   @Override
   public void dispose() {
      rows = null;
      rowMap = null;
      drows.clear();
      drows = null;
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
      return base != null ? base.getColumnIdentifier(col) : "";
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
      if(base != null) {
         base.setColumnIdentifier(col, identifier);
      }
   }

   /**
    * Get internal table data descriptor which contains table structural infos.
    * @return table data descriptor.
    */
   @Override
   public TableDataDescriptor getDescriptor() {
      if(descriptor == null) {
         descriptor = new FormTableDescriptor();
      }

      return descriptor;
   }

   /**
    * Add table change listener to the filtered table.
    * If the table filter's data changes, a TableChangeEvent will be triggered
    * for the TableChangeListener to process.
    * @param listener the specified TableChangeListener
    */
   @Override
   public void addChangeListener(TableChangeListener listener) {
      if(base != null) {
         base.addChangeListener(listener);
      }
   }

   /**
    * Remove table change listener from the filtered table.
    * @param listener the specified TableChangeListener to be removed
    */
   @Override
   public void removeChangeListener(TableChangeListener listener) {
      if(base != null) {
         base.removeChangeListener(listener);
      }
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
      // by viewsheet grid height
      return -1;
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
      return base != null ? base.getColWidth(col) : 0;
   }

   /**
    * Return the color for drawing the row border lines.
    * @param r row number.
    * @param c column number.
    * @return ruling color.
    */
   @Override
   public Color getRowBorderColor(int r, int c) {
      return base != null ? base.getRowBorderColor(r, c) : new Color(0, 0, 0);
   }

   /**
    * Return the color for drawing the column border lines.
    * @param r row number.
    * @param c column number.
    * @return ruling color.
    */
   @Override
   public Color getColBorderColor(int r, int c) {
      return base != null ? base.getColBorderColor(r, c) : new Color(0, 0, 0);
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
      return THIN_LINE;
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
      return THIN_LINE;
   }

   /**
    * Return the cell gap space.
    * @param r row number.
    * @param c column number.
    * @return cell gap space.
    */
   @Override
   public Insets getInsets(int r, int c) {
      return null;
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
      return null;
   }

   /**
    * Return the per cell alignment.
    * @param r row number.
    * @param c column number.
    * @return cell alignment.
    */
   @Override
   public int getAlignment(int r, int c) {
      return -1;
   }

   /**
    * Return the per cell font. Return null to use default font.
    * @param r row number.
    * @param c column number.
    * @return font for the specified cell.
    */
   @Override
   public Font getFont(int r, int c) {
      return null;
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
      return true;
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
      return null;
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
      return null;
   }

   /**
    * Return the per cell format.
    * @param row row number.
    * @param col column number.
    * @return format for the specified cell.
    */
   @Override
   public Format getDefaultFormat(int row, int col) {
      return null;
   }

   /**
    * Return the per cell drill info.
    * @param r row number.
    * @param c column number.
    * @return drill info for the specified cell.
    */
   @Override
   public XDrillInfo getXDrillInfo(int r, int c) {
      // no drill for FormTableLens
      return null;
   }

   /**
    * Check if contains format.
    * @return true if contains format.
    */
   @Override
   public boolean containsFormat() {
      return base != null && base.containsFormat();
   }

   /**
    * Check if contains drill.
    * @return true if contains drill.
    */
   @Override
   public boolean containsDrill() {
      // no drill  for FormTableLens
      return false;
   }

   /**
    * Get the original table of this filter.
    */
   @Override
   public TableLens getTable() {
      return base;
   }

   /**
    * Set the base table of this filter.
    */
   @Override
   public void setTable(TableLens table) {
      this.base = table;
   }

   /**
    * Check if the table is edit table.
    */
   public boolean isEdit() {
      return edit;
   }

   /**
    * Set the table isEdit.
    */
   public void setEdit(boolean edit) {
      this.edit = edit;
   }

   /**
    * Get column selection.
    * @return column selection.
    */
   public ColumnSelection getColumnSelection() {
      return columns;
   }

   /**
    * Set column selection.
    */
   public void setColumnSelection(ColumnSelection columns) {
      this.columns = columns;
   }

   public FormRef getFormRef(int index) {
      return columns == null ? null : (FormRef) columns.getAttribute(index);
   }

   public ColumnOption getColumnOption(int index) {
      FormRef ref = columns == null ? null :
         (FormRef) columns.getAttribute(index);
      return ref == null ? new TextColumnOption() : ref.getOption();
   }

   public ColumnOption getVisibleColumnOption(int index) {
      ColumnSelection visibleColumns = getVisibleColumns();
      FormRef ref = visibleColumns == null ? null : (FormRef) visibleColumns.getAttribute(index);
      return ref == null ? new TextColumnOption() : ref.getOption();
   }

   /**
    * Invalidate the table filter. When the filter is used, the underlying data
    * will be filtered again. This function is called if the base data has
    * changed.
    */
   @Override
   public void invalidate() {
      // never reexecute
   }

   /**
    * Get the base table row index corresponding to the filtered table.
    * If the row does not exist in the base table, it returns -1.
    * @param r row index in the filtered table.
    * @return corresponding row index in the base table.
    */
   @Override
   public int getBaseRowIndex(int r) {
      return row0(r).getBaseRowIndex();
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
    * Get row state.
    * @param key row number or row state.
    */
   public int getRowState(int key) {
      return key < 0 ? key : row0(key).getRowState();
   }

   /**
    * Return row foreground color.
    * @param key row number or row state.
    */
   public Color getForeground(int key) {
      int state = getRowState(key);

      if(state == FormTableRow.ADDED) {
         return ADDED_FOREGROUND;
      }
      else if(state == FormTableRow.CHANGED) {
         return CHANGED_FOREGROUND;
      }

      return null;
   }

   /**
    * Return row background color.
    * @param key row number or row state.
    */
   public Color getBackground(int key) {
      int state = getRowState(key);

      if(state == FormTableRow.ADDED) {
         return ADDED_BACKGROUND;
      }
      else if(state == FormTableRow.CHANGED) {
         return CHANGED_BACKGROUND;
      }

      return null;
   }

   /**
    * Return the per cell background alpha.
    * @param key row number or row state.
    */
   public int getAlpha(int key) {
      return -1;
   }

    /**
    * Get the value of a property.
    * @param key the specified property name.
    * @return the value of the property.
    */
   @Override
   public Object getProperty(String key) {
      return prop.get(key);
   }

   /**
    * Set the value a property.
    * @param key the property name.
    * @param value the property value, null to remove the property.
    */
   @Override
   public void setProperty(String key, Object value) {
      if(value == null) {
         prop.remove(key);
      }
      else {
         prop.put(key, value);
      }
   }

   /**
    * @return the report/vs name which this filter was created for,
    * and will be used when insert audit record.
    */
   @Override
   public String getReportName() {
      Object name = getProperty(XTable.REPORT_NAME);
      String reportName = null;

      if(name != null) {
         reportName = name + "";
      }
      else if(base != null) {
         reportName = base.getReportName();
      }

      return reportName;
   }

   /**
    * @return the report type which this filter was created for:
    * ExecutionBreakDownRecord.OBJECT_TYPE_REPORT or
    * ExecutionBreakDownRecord.OBJECT_TYPE_VIEWSHEET
    */
   @Override
   public String getReportType() {
      Object type = getProperty(XTable.REPORT_TYPE);
      String reportType = null;

      if(type != null) {
         reportType = type + "";
      }
      else if(base != null) {
         reportType = base.getReportType();
      }

      return reportType;
   }

   /**
    * Tablelens cell.
    */
   public static class Cell {

      /**
       * Default Constructor
       *
       */
      public Cell() {

      }

      /**
       * Constructure.
       */
      public Cell(int row, int col, Object value) {
         this.row = row;
         this.col = col;
         this.value = value;
      }

      /**
       * Get row index.
       */
      public int getRow() {
         return row;
      }

      /**
       * Get column index;
       */
      public int getColumn() {
         return col;
      }

      /**
       * Get cell value.
       */
      public Object getValue() {
         return value;
      }

      /**
       * To string.
       */
      public String toString() {
         return "[" + row + "," + col + "," + value.toString() + "]";
      }

      /**
       * Write xml.
       */
      public void writeXML(PrintWriter writer) {
         writer.print("<cell row=\"" + row + "\"" + " col=\"" + col + "\">");
         writer.println("<text>");
         writer.print("<![CDATA[" + value.toString() + "]]>");
         writer.println("</text>");
         writer.println("</cell>");
      }

      private int row;
      private int col;
      private Object value;
   }

   /**
    * FormTable data descriptor.
    */
   private class FormTableDescriptor implements TableDataDescriptor {
      /**
       * Get meta info of a specified table data path.
       * @param path the specified table data path
       * @return meta info of the table data path
       */
      @Override
      public XMetaInfo getXMetaInfo(TableDataPath path) {
         if(base == null) {
            return new XMetaInfo();
         }

         TableDataDescriptor baseDescriptor = base.getDescriptor();
         return baseDescriptor.getXMetaInfo(path);
      }

      @Override
      public List<TableDataPath> getXMetaInfoPaths() {
         return base.getDescriptor().getXMetaInfoPaths();
      }

      /**
       * Check if contains format.
       * @return true if contains format
       */
      @Override
      public boolean containsFormat() {
         return base != null && base.containsFormat();
      }

      /**
       * Check if contains drill.
       * @return <tt>true</tt> if contains drill
       */
      @Override
      public boolean containsDrill() {
         return base != null && base.containsDrill();
      }

      /**
       * Get table data path of a specified table column.
       * @param col the specified table column
       * @return table data path of the table column
       */
      @Override
      public TableDataPath getColDataPath(int col) {
         String header = Util.getHeader(FormTableLens.this, col).toString();
         return new TableDataPath(header);
      }

      /**
       * Get table data path of a specified table row.
       * @param row the specified table row
       * @return table data path of the table row
       */
      @Override
      public TableDataPath getRowDataPath(int row) {
         int brow = getBaseRowIndex(row);

         if(brow < 0 || base == null) {
            return new TableDataPath(-1, TableDataPath.DETAIL);
         }

         return base.getDescriptor().getRowDataPath(brow);
      }

      /**
       * Get table data path of a specified table cell.
       * @param row the specified table cell row
       * @param col the specified table cell col
       * @return table data path of the table cell
       */
      @Override
      public TableDataPath getCellDataPath(int row, int col) {
         int brow = getBaseRowIndex(row);

         if(brow < 0 || base == null) {
            String header = Util.getHeader(FormTableLens.this, col).toString();
            Object val = FormTableLens.this.getObject(row, col);
            Class cls = val == null ? null : val.getClass();
            String dtype = Util.getDataType(cls);

            return new TableDataPath(-1, TableDataPath.DETAIL, dtype,
                                     new String[] {header});
         }

         TableDataPath cellDataPath = base.getDescriptor().getCellDataPath(brow, col);

         if(cellDataPath != null) {
            String[] path = cellDataPath.getPath();

            if(path.length == 1) {
               FormRef formRef = getFormRef(col);
               String alias = formRef.getAlias();

               if(!Tool.isEmptyString(alias) && Tool.equals(formRef.getAttribute(), path[0])) {
                  cellDataPath = (TableDataPath) cellDataPath.clone(new String[]{ alias });
               }
            }
         }

         return cellDataPath;
      }

      /**
       * Check if a column belongs to a table data path.
       * @param col the specified table col
       * @param path the specified table data path
       * @return true if the col belongs to the table data path, false otherwise
       */
      @Override
      public boolean isColDataPath(int col, TableDataPath path) {
         String header = Util.getHeader(FormTableLens.this, col).toString();
         return header.equals(path.getPath()[0]);
      }

      /**
       * Check if a row belongs to a table data path.
       * @param row the specified table row
       * @param path the specified table data path
       * @return true if the row belongs to the table data path, false otherwise
       */
      @Override
      public boolean isRowDataPath(int row, TableDataPath path) {
         int type = path.getType();

         if(base == null) {
            return false;
         }

         if(row < base.getHeaderRowCount()) {
            return type == TableDataPath.HEADER;
         }
         else if(base.moreRows(row + base.getTrailerRowCount())) {
            return type == TableDataPath.DETAIL;
         }
         else {
            return type == TableDataPath.TRAILER;
         }
      }

      /**
       * Check if a cell belongs to a table data path in a loose way.
       * Note: when cheking, path in the table data path will be ignored.
       * @param row the specified table cell row
       * @param col the specified table cell col
       * @param path the specified table data path
       * @return true if the cell belongs to the table data path,
       * false otherwise
       */
      @Override
      public boolean isCellDataPathType(int row, int col, TableDataPath path) {
         int type = path.getType();

         if(base == null) {
            return false;
         }

         if(row < base.getHeaderRowCount()) {
            return type == TableDataPath.HEADER;
         }
         else if(base.moreRows(row + base.getTrailerRowCount())) {
            return type == TableDataPath.DETAIL;
         }
         else {
            return type == TableDataPath.TRAILER;
         }
      }

      /**
       * Check if a cell belongs to a table data path.
       * @param row the specified table cell row
       * @param col the specified table cell col
       * @param path the specified table data path
       * false if ignore path in the table data path
       * @return true if the cell belongs to the table data path,
       * false otherwise
       */
      @Override
      public boolean isCellDataPath(int row, int col, TableDataPath path) {
         if(!path.getPath()[0].
            equals(Util.getHeader(FormTableLens.this, col).toString()))
         {
            return false;
         }

         return isCellDataPathType(row, col, path);
      }

      /**
       * Get level of a specified table row, which is required for nested table.
       * The default value is <tt>-1</tt>.
       * @param row the specified table row
       * @return level of the table row
       */
      @Override
      public int getRowLevel(int row) {
         return -1;
      }

      /**
       * Get table type which is one of the table types defined in table data
       * descriptor like <tt>NORMAL_TABLE</tt>, <tt>CROSSTAB_TABLE</tt>, etc.
       * @return table type
       */
      @Override
      public int getType() {
         return NORMAL_TABLE;
      }
   }

   /**
    * Get visible column selection.
    */
   public ColumnSelection getVisibleColumns() {
      ColumnSelection columnselection = (ColumnSelection) columns.clone();
      ColumnSelection selection = new ColumnSelection();

      for(int i = 0; i < columnselection.getAttributeCount(); i++) {
         DataRef ref = columnselection.getAttribute(i);

         if(ref instanceof ColumnRef) {
            if(!((ColumnRef)ref).isVisible()) {
               continue;
            }
         }

         selection.addAttribute(ref);
      }

      return selection;
   }

   private static final Color ADDED_FOREGROUND = new Color(0, 0, 0);
   private static final Color CHANGED_FOREGROUND = new Color(0, 0, 0);
   private static final Color ADDED_BACKGROUND = new Color(204, 255, 255);
   private static final Color CHANGED_BACKGROUND = new Color(255, 255, 204);
   private TableLens base;
   private FormTableRow[] rows;
   private List<FormTableRow> drows = new ArrayList<>(); // deleted rows
   private List<Cell> ccells = new ArrayList<>(); // changed cells
   private boolean edit = false;
   private int[] rowMap; // index->new row index   value->base row index
   private int order;    // sort order
   private int col;      // sort column
   private TableDataDescriptor descriptor;
   private ColumnSelection columns;
   private Properties prop = new Properties(); // properties
}
