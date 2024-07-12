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
package inetsoft.report.filter;

import inetsoft.report.*;
import inetsoft.report.internal.Util;
import inetsoft.report.internal.table.CancellableTableLens;
import inetsoft.report.lens.AbstractTableLens;
import inetsoft.uql.XMetaInfo;
import inetsoft.uql.asset.internal.ColumnIndexMap;
import inetsoft.util.Tool;

import java.awt.*;
import java.util.List;
import java.util.*;

/**
 * This filter adds one table level summarization row (grand total) without
 * the need to perform grouping. The summarization is done through
 * formula objects.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class TableSummaryFilter extends AbstractTableLens
   implements TableFilter, Cloneable, CancellableTableLens
{
   /**
    * Create table level summarization. The Formula array must be the same
    * size as the number of columns in the table. For columns that does
    * not have summarization, a null value can be supplied at the position.
    * @param table base table.
    * @param label summary row label. Placed at the first column of the
    * summary row.
    * @param calc summarization formula.
    */
   public TableSummaryFilter(TableLens table, String label, Formula[] calc) {
      setTable(table);
      this.calc = calc;
      this.label = label;

      if(calc[0] != null) {
         this.label = null;
      }
   }

   /**
    * Create table level summarization. The Formula array must be the same
    * size as the summary column array. For columns that does
    * not have summarization, a null value can be supplied at the position.
    * @param table base table.
    * @param label summary row label. Placed at the first column of the
    * summary row.
    * @param sumcols summary columns.
    * @param calc summarization formula.
    */
   public TableSummaryFilter(TableLens table, String label, int[] sumcols,
                             Formula[] calc)
   {
      // map the table so the grouped columns are at the left
      int[] map = new int[table.getColCount()];

      for(int i = 0; i < map.length; i++) {
         map[i] = i;
      }

      int[] sumcnts = new int[map.length]; // # of occurence on summary list
      int[] sums = new int[map.length]; // orig sum col -> new col in map

      // map the summary cols, extend map if multiple summary on one column
      if(sumcols != null) {
         for(int i = 0; i < sumcols.length; i++) {
            // if the column is on the summary list already, add a new column
            // to the table to summarize the same column as a new column
            if(sumcnts[sumcols[i]] > 0) {
               int[] nmap = new int[map.length + 1];

               System.arraycopy(map, 0, nmap, 0, map.length);
               nmap[map.length] = sums[sumcols[i]];
               sumcnts[sumcols[i]]++;
               sumcols[i] = map.length;
               map = nmap;
               continue;
            }

            // find the summary column in the new ordering
            for(int j = 0; j < map.length; j++) {
               if(map[j] == sumcols[i]) {
                  sums[sumcols[i]] = sumcols[i];
                  sumcnts[sumcols[i]]++;
                  sumcols[i] = j;
                  break;
               }
            }
         }
      }

      setTable(new ColumnMapFilter(table, map));
      this.label = label;
      this.calc = new Formula[this.table.getColCount()];

      for(int i = 0; i < sumcols.length; i++) {
         this.calc[sumcols[i]] = calc[i];
      }

      if(this.calc[0] != null) {
         this.label = null;
      }
   }

   /**
    * Create table level summarization. One column is chosen for
    * summarization.
    * @param table base table.
    * @param label summary row label.
    * @param col summarization column index.
    * @param calc summarization formula.
    */
   public TableSummaryFilter(TableLens table, String label, int col,
      Formula calc)
   {
      setTable(table);
      this.label = label;
      this.calc = new Formula[table.getColCount()];

      if(col == 0) {
         this.label = null;
      }

      for(int i = 0; i < this.calc.length; i++) {
         this.calc[i] = (i == col) ? calc : null;
      }
   }

   /**
    * Get the base table row index corresponding to the filtered table.
    * If the row does not exist in the base table, it returns -1.
    * @param row row index in the filtered table.
    * @return corresponding row index in the base table.
    */
   @Override
   public int getBaseRowIndex(int row) {
      if(row < table.getHeaderRowCount()) {
         return row;
      }
      else if(!sonly && row < table.getRowCount()) {
         return row;
      }

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
      return col;
   }

   /**
    * Return the number of rows on the bottom of the table to be treated
    * as trailer rows.
    * @return number of header rows.
    */
   @Override
   public int getTrailerRowCount() {
      return 1;
   }

   /**
    * Set the font for the summary row.
    * @param font new font.
    */
   public void setSummaryFont(Font font) {
      sumFont = font;
   }

   /**
    * Get the summary row font. If the font is null, the row uses the
    * same font as the last row in the original table.
    * @return summary row font.
    */
   public Font getSummaryFont() {
      return sumFont;
   }

   /**
    * Invalidate the table filter forcely, and the table filter will perform
    * filtering calculation to validate itself.
    */
   @Override
   public void invalidate() {
      if(calc != null) {
         for(int i = 0; i < calc.length; i++) {
            if(calc[i] != null) {
               calc[i].reset();
            }
         }
      }

      sum = null;
      srow = Integer.MIN_VALUE;
      mmap.clear();
      fireChangeEvent();
   }

   /**
    * Check if summary only.
    * @return <tt>true</tt> if summary only.
    */
   public boolean isSummaryOnly() {
      return sonly;
   }

   /**
    * Set the summary only flag.
    * @param only <tt>true</tt> if summary only.
    */
   public void setSummaryOnly(boolean only) {
      this.sonly = only;
   }

   /**
    * Get the original table of this filter.
    */
   @Override
   public TableLens getTable() {
      return table;
   }

   /**
    * Set the base table of this filter.
    */
   @Override
   public void setTable(TableLens table) {
      this.table = table;
      invalidate();
      this.table.addChangeListener(new DefaultTableChangeListener(this));
   }

   /**
    * Set the default result option of this filter.
    * @param def <tt>true</tt> to use the default value of a formula if no
    * result, <tt>false</tt> to just return null.
    */
   public void setDefaultResult(boolean def) {
      this.def = def;
   }

   /**
    * Get the default result option of this filter.
    * @return <tt>true</tt> to use the default value of a formula if no
    * result, <tt>false</tt> to just return null.
    */
   public boolean isDefaultResult() {
      return def;
   }

   /**
    * Get internal table data descriptor which contains table structural
    * infos.
    * @return table data descriptor.
    */
   @Override
   public TableDataDescriptor getDescriptor() {
      if(tdescriptor == null) {
         tdescriptor = new TableSummaryFilterDataDescriptor();
      }

      return tdescriptor;
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
      if(cancelled) {
         return false;
      }

      if(row < table.getHeaderRowCount()) {
         return true;
      }

      boolean more = sonly?
         table.moreRows(Integer.MAX_VALUE) :
         table.moreRows(row);

      if(!more) {
         int max = getMax();

         // reached end of table, calculate summary
         if(row == max && sum == null) {
            FormulaAgent[] fagents = new FormulaAgent[table.getColCount()];

            for(int i = 0; i < fagents.length; i++) {
               fagents[i] = FormulaAgent.getAgent(table.getColType(i),
                                                  table.isPrimitive(i));
            }

            for(int i = table.getHeaderRowCount(); table.moreRows(i); i++) {
               if(cancelled) {
                  return false;
               }

               for(int j = 0; j < calc.length; j++) {
                  if(calc[j] != null) {
                     if(calc[j] instanceof Formula2) {
                        int[] cols = ((Formula2) calc[j]).getSecondaryColumns();
                        Object[] data = new Object[cols.length + 1];
                        data[0] = table.getObject(i, j);

                        for(int k = 0; k < cols.length; k++) {
                           data[k + 1] = table.getObject(i, cols[k]);
                        }

                        calc[j].addValue(data);
                     }
                     else {
                        fagents[j].add(calc[j], table, i, j);
                     }
                  }
               }
            }

            sum = new Object[calc.length];

            if(label != null) {
               sum[0] = label;
            }

            for(int i = 0; i < calc.length; i++) {
               if(calc[i] != null) {
                  sum[i] = calc[i].getResult();

                  if(def && (sum[i] == null ||
                     (sum[i] instanceof Double && ((Double) sum[i]).isNaN())))
                  {
                     sum[i] = 0;
                  }
               }
            }

            srow = max;
         }

         return more || super.moreRows(row);
      }

      return more;
   }

   /**
    * Return the number of rows in the table. The number of rows includes
    * the header rows.
    * @return number of rows in table.
    */
   @Override
   public int getRowCount() {
      int max = getMax();
      return max + 1;
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
    * Get the current column content type.
    * @param col column number.
    * @return column type.
    */
   @Override
   public Class getColType(int col) {
      if(sonly) {
         return super.getColType(col);
      }

      return table.getColType(col);
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
    * Get the current row heights setting. The meaning of row heights
    * depends on the table layout policy setting. If the row height
    * is to be calculated by the ReportSheet based on the content,
    * return -1.
    * @return row height.
    */
   @Override
   public int getRowHeight(int row) {
      return (row != srow) ? table.getRowHeight(row) : -1;
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
      return table.getColWidth(col);
   }

   /**
    * Return the color for drawing the row border lines.
    * @param r row number.
    * @param c column number.
    * @return ruling color.
    */
   @Override
   public Color getRowBorderColor(int r, int c) {
      return (r != srow) ? table.getRowBorderColor(r, c) : Color.black;
   }

   /**
    * Return the color for drawing the column border lines.
    * @param r row number.
    * @param c column number.
    * @return ruling color.
    */
   @Override
   public Color getColBorderColor(int r, int c) {
      return (r != srow) ? table.getColBorderColor(r, c) : Color.black;
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
      return (r != srow) ? table.getRowBorder(r, c) : StyleConstants.THIN_LINE;
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
      return (r != srow) ? table.getColBorder(r, c) :
         table.getColBorder(r - 1, c);
   }

   /**
    * Return the cell gap space.
    * @param r row number.
    * @param c column number.
    * @return cell gap space.
    */
   @Override
   public Insets getInsets(int r, int c) {
      return (r != srow) ? table.getInsets(r, c) : null;
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
      return (r != srow) ? table.getSpan(r, c) : null;
   }

   /**
    * Return the per cell alignment.
    * @param r row number.
    * @param c column number.
    * @return cell alignment.
    */
   @Override
   public int getAlignment(int r, int c) {
      return (r != srow) ? table.getAlignment(r, c) :
         table.getAlignment(r - 1, c);
   }

   /**
    * Return the per cell font. Return null to use default font.
    * @param r row number.
    * @param c column number.
    * @return font for the specified cell.
    */
   @Override
   public Font getFont(int r, int c) {
      return (r != srow) ? table.getFont(r, c) :
         ((sumFont != null) ? sumFont : table.getFont(r - 1, c));
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
      return (r != srow) ? table.isLineWrap(r, c) : table.isLineWrap(r - 1, c);
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
      return (r != srow) ? table.getForeground(r, c) :
         table.getForeground(r - 1, c);
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
      return (r != srow) ? table.getBackground(r, c) :
         table.getBackground(r - 1, c);
   }

   /**
    * Check if the location is display summary label.
    * @hidden
    */
   public boolean isSummaryLabelLocation(int r, int c) {
      if(label != null && c == 0 && calc[c] == null && r == getMax()) {
         return true;
      }

      return false;
   }

   /**
    * Return the value at the specified cell.
    * @param r row number.
    * @param c column number.
    * @return the value at the location.
    */
   @Override
   public Object getObject(int r, int c) {
      if((sonly && r < table.getHeaderRowCount()) ||
         (!sonly && table.moreRows(r)))
      {
         return table.getObject(r, c);
      }

      if(sum == null) {
         moreRows(r);
      }

      Object[] sum = this.sum;
      return sum != null ? sum[c] : null;
   }

   /**
    * Set the cell value.
    * @param r row number.
    * @param c column number.
    * @param v cell value.
    */
   @Override
   public void setObject(int r, int c, Object v) {
      if((sonly && r < table.getHeaderRowCount()) ||
         (!sonly && table.moreRows(r)))
      {
         table.setObject(r, c, v);
      }
      else {
         if(sum == null) {
            moreRows(r);
         }

         sum[c] = v;
         fireChangeEvent();
      }
   }

   /**
    * Get the formula of the column.
    * @param col column number.
    * @return the formula of the grand.
    */
   public Formula getGrandFormula(int col) {
      return calc[col];
   }

   /**
    * If the column is a summary column.
    * @param col column number.
    */
   public boolean isSummaryCol(int col) {
      if(calc[col] != null) {
         return true;
      }

      return false;
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
      String identifier = super.getColumnIdentifier(col);
      col = getBaseColIndex(col);

      return identifier == null ? table.getColumnIdentifier(col) : identifier;
   }

   /**
    * Get the max row number.
    */
   private int getMax() {
      return sonly ? table.getHeaderRowCount() : table.getRowCount();
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

   @Override
   public void cancel() {
      // not complete
      if(srow == Integer.MIN_VALUE) {
         cancelled = true;
      }

      final CancellableTableLens cancelTable = (CancellableTableLens) Util.getNestedTable(
         table, CancellableTableLens.class);

      if(cancelTable != null) {
         cancelTable.cancel();
         cancelled = cancelled || cancelTable.isCancelled();
      }
   }

   @Override
   public boolean isCancelled() {
      return cancelled;
   }

   /**
    * TableSummaryFilter data descriptor.
    */
   class TableSummaryFilterDataDescriptor implements TableDataDescriptor {
      /**
       * Get table data path of a specified table column.
       * @param col the specified table column
       * @return table data path of the table column
       */
      @Override
      public TableDataPath getColDataPath(int col) {
         String header =
            Util.getHeader(TableSummaryFilter.this, col).toString();
         return new TableDataPath(header);
      }

      /**
       * Get table data path of a specified table row.
       * @param row the specified table row
       * @return table data path of the table row
       */
      @Override
      public TableDataPath getRowDataPath(int row) {
         int type = TableDataPath.GRAND_TOTAL;

         if(row < TableSummaryFilter.this.getHeaderRowCount()) {
            type = TableDataPath.HEADER;
         }
         else if(TableSummaryFilter.this.moreRows(row + 1)) {
            type = TableDataPath.DETAIL;
         }

         return new TableDataPath(-1, type);
      }

      /**
       * Get table data path of a specified table cell.
       * @param row the specified table cell row
       * @param col the specified table cell col
       * @return table data path of the table cell
       */
      @Override
      public TableDataPath getCellDataPath(int row, int col) {
         String header =
            Util.getHeader(TableSummaryFilter.this, col).toString();
         int type = TableDataPath.GRAND_TOTAL;
         Object val = getObject(row, col);
         Class<?> cls = val == null ? null : val.getClass();
         String dtype = Util.getDataType(cls);

         if(row < TableSummaryFilter.this.getHeaderRowCount()) {
            type = TableDataPath.HEADER;
         }
         else if(TableSummaryFilter.this.moreRows(row + 1)) {
            type = TableDataPath.DETAIL;
         }

         return new TableDataPath(-1, type, dtype, new String[] {header});
      }

      /**
       * Check if a column belongs to a table data path.
       * @param col the specified table col
       * @param path the specified table data path
       * @return true if the col belongs to the table data path, false otherwise
       */
      @Override
      public boolean isColDataPath(int col, TableDataPath path) {
         String header =
            Util.getHeader(TableSummaryFilter.this, col).toString();
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

         if(row < TableSummaryFilter.this.getHeaderRowCount()) {
            return type == TableDataPath.HEADER;
         }
         else if(TableSummaryFilter.this.moreRows(row + 1)) {
            return type == TableDataPath.DETAIL;
         }
         else {
            return type == TableDataPath.GRAND_TOTAL;
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

         if(row < TableSummaryFilter.this.getHeaderRowCount()) {
            return type == TableDataPath.HEADER;
         }
         else if(TableSummaryFilter.this.moreRows(row + 1)) {
            return type == TableDataPath.DETAIL;
         }
         else {
            return type == TableDataPath.GRAND_TOTAL;
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
         String[] pathes = path.getPath();

         if(pathes.length == 0 || !pathes[0].equals(
            Util.getHeader(TableSummaryFilter.this, col).toString()))
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
         return TABLE_SUMMARY_TABLE;
      }

      /**
       * Get table xmeta info.
       * @param path the specified table data path
       * @return meta info of the table data path
       */
      @Override
      public XMetaInfo getXMetaInfo(TableDataPath path) {
         if(!path.isCell()) {
            return null;
         }

         Object obj = mmap.get(path);

         if(obj instanceof XMetaInfo) {
            return (XMetaInfo) obj;
         }
         else if(obj != null) {
            return null;
         }

         int type = path.getType();
         String dtype = path.getDataType();
         String header = path.getPath()[0];
         TableDataPath opath = path;
         Formula aggr = null;

         if(type == TableDataPath.GRAND_TOTAL) {
            if(columnIndexMap == null) {
               columnIndexMap = new ColumnIndexMap(TableSummaryFilter.this, true);
            }

            int col = Util.findColumn(columnIndexMap, header, false);

            if(col >= 0 && isSummaryCol(col)) {
               aggr = calc[col];
               opath = new TableDataPath(-1,
                  TableDataPath.DETAIL, dtype, new String[] {header});
            }
            else {
               opath = null;
            }
         }

         TableDataDescriptor desc = table.getDescriptor();
         XMetaInfo minfo = opath == null ? null : desc.getXMetaInfo(opath);

         // do not apply auto drill on aggregated column
         if(aggr != null && minfo != null) {
            minfo = (XMetaInfo) minfo.clone();
            minfo.setXDrillInfo(null);
         }

         Util.removeIncompatibleMetaInfo(minfo, aggr);
         mmap.put(path, minfo == null ? Tool.NULL : minfo);

         return minfo;
      }

      @Override
      public List<TableDataPath> getXMetaInfoPaths() {
         List<TableDataPath> list = new ArrayList<>();

         if(!mmap.isEmpty()) {
            list.addAll(mmap.keySet());
         }

         return list;
      }

      /**
       * Check if contains format.
       * @return true if contains format
       */
      @Override
      public boolean containsFormat() {
         return table.containsFormat();
      }

      /**
       * Check if contains drill.
       * @return <tt>true</tt> if contains drill
       */
      @Override
      public boolean containsDrill() {
         return table.containsDrill();
      }

      private transient ColumnIndexMap columnIndexMap = null;
   }

   private TableLens table;
   private Formula[] calc;
   private Object[] sum;
   private int srow;
   private String label;
   private boolean def;
   private boolean sonly;
   private Font sumFont; // summary row font
   private TableDataDescriptor tdescriptor;
   private Hashtable mmap = new Hashtable(); // xmeta info
   private boolean cancelled = false;
}
