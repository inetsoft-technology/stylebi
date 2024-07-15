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
package inetsoft.report.filter;

import inetsoft.graph.data.CalcColumn;
import inetsoft.report.*;
import inetsoft.report.composition.graph.calc.AbstractColumn;
import inetsoft.report.composition.graph.calc.PercentColumn;
import inetsoft.report.internal.TimeSeriesUtil;
import inetsoft.report.internal.Util;
import inetsoft.report.internal.binding.CrosstabSortInfo;
import inetsoft.report.internal.table.TableFormat;
import inetsoft.report.lens.AbstractTableLens;
import inetsoft.report.lens.HeaderRowTableLens;
import inetsoft.report.style.TableStyle;
import inetsoft.uql.*;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.asset.internal.ColumnIndexMap;
import inetsoft.uql.util.XUtil;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.*;
import inetsoft.util.audit.ExecutionBreakDownRecord;
import inetsoft.util.profile.ProfileUtils;
import inetsoft.util.swap.XIntList;

import java.awt.*;
import java.io.Serializable;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Create a crosstab table. A crosstab table uses one column as the
 * row header, and one column as the column header. The data at the
 * cross section of rows and columns are extracted from the original
 * table. If a formula is supplied, the data item is an accumulation of
 * the data for the row/column header. If there are more than one
 * row with the row/column header value, the data is extracted from the
 * rows and added to the formula to obtain the final value. If no
 * formula is supplied, the data cell at the crosstab is the value
 * or the row with the row/column header. If more than one row have
 * the same row/column, the last row value is used.
 * <p>
 * For example, a table containing:<pre>
 * R1     C1      5
 * R1     C1      4
 * ...
 * </pre>
 * With the first column used as the row header, and second column as
 * the column header, and the third column as the data column, a cross
 * tab table containing the following will be created: <pre><p>
 *        C1
 * R1     4
 * </pre><p>
 * If a SumFormula is supplied, the result table will be:<pre><p>
 *        C1
 * R1     9
 * </pre>
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class CrossTabFilter extends AbstractTableLens
   implements CrossFilter, CalcFilter, Cloneable, RowLimitableTable
{
   /**
    * Create a crosstab table with the specified row, column, and data
    * columns. An optional formula can be supplied.
    * @param table base table.
    * @param rowh row header column.
    * @param colh column header column.
    * @param dcol data column.
    * @param sum formula or null.
    */
   public CrossTabFilter(TableLens table, int rowh, int colh, int dcol, Formula sum) {
      this(table, new int[] {rowh}, new int[] {colh}, new int[] {dcol},
           new Formula[] {sum});
   }

   /**
    * Create a crosstab table with the specified row, column, and data
    * columns. An optional formula can be supplied.
    * @param table base table.
    * @param rowh row header column.
    * @param colh column header column.
    * @param dcol data column.
    * @param sum formula or null.
    */
   public CrossTabFilter(TableLens table, int[] rowh, int colh, int dcol, Formula sum) {
      this(table, rowh, new int[] {colh}, new int[] {dcol}, new Formula[] {sum});
   }

   /**
    * Create a crosstab table with the specified row, column, and data
    * columns. An optional formula can be supplied.
    * @param table base table.
    * @param rowh row header column.
    * @param colh column header column.
    * @param dcol data column.
    * @param sum formula or null.
    */
   public CrossTabFilter(TableLens table, int rowh, int[] colh, int dcol, Formula sum) {
      this(table, new int[] {rowh}, colh, new int[] {dcol}, new Formula[] {sum});
   }

   /**
    * Create a crosstab table with the specified row, column, and data
    * columns. An optional formula can be supplied.
    * @param table base table.
    * @param rowh row header column.
    * @param colh column header column.
    * @param dcol data column.
    * @param sum formula or null.
    */
   public CrossTabFilter(TableLens table, int[] rowh, int[] colh, int dcol, Formula sum) {
      this(table, rowh, colh, new int[] {dcol}, new Formula[] {sum});
   }

   /**
    * Create a crosstab table with the specified row, column, and data
    * columns. An optional formula can be supplied.
    * @param table base table.
    * @param rowh row header column.
    * @param colh column header column.
    * @param dcol data column.
    * @param sum formula or null.
    */
   public CrossTabFilter(TableLens table, int rowh, int colh, int[] dcol, Formula[] sum) {
      this(table, new int[] {rowh}, new int[] {colh}, dcol, sum);
   }

   /**
    * Create a crosstab table with the specified row, column, and data
    * columns. An optional formula can be supplied.
    * @param table base table.
    * @param rowh row header column.
    * @param colh column header column.
    * @param dcol data column.
    * @param sum formula or null.
    */
   public CrossTabFilter(TableLens table, int[] rowh, int colh, int[] dcol, Formula[] sum) {
      this(table, rowh, new int[] {colh}, dcol, sum);
   }

   /**
    * Create a crosstab table with the specified row, column, and data
    * columns. An optional formula can be supplied.
    * @param table base table.
    * @param rowh row header column.
    * @param colh column header column.
    * @param dcol data column.
    * @param sum formula or null.
    */
   public CrossTabFilter(TableLens table, int rowh, int[] colh, int[] dcol, Formula[] sum) {
      this(table, new int[] {rowh}, colh, dcol, sum);
   }

   /**
    * Create a crosstab table with the specified row, column, and data
    * columns. An optional formula can be supplied.
    * @param table base table.
    * @param rowh row header column.
    * @param colh column header column.
    * @param dcol data column.
    * @param sum formula or null.
    */
   public CrossTabFilter(TableLens table, int[] rowh, int[] colh, int[] dcol, Formula[] sum) {
      this(table, rowh, colh, dcol, sum, false, false,
         XConstants.NONE_DATE_GROUP, XConstants.NONE_DATE_GROUP);
   }

   /**
    * Create a crosstab table with the specified row, column, and data
    * columns. An optional formula can be supplied.
    * @param table base table.
    * @param rowh row header column.
    * @param colh column header column.
    * @param dcol data column.
    * @param sum formula or null.
    * @param rowTimeSeries if apply timeseries for inner row header.
    * @param colTimeSeries if apply timeseries for inner col header.
    * @param rowTimeseriesLevel timeseries data level of row.
    * @param colTimeseriesLevel timeseries data level of col.
    */
   public CrossTabFilter(TableLens table, int[] rowh, int[] colh, int[] dcol, Formula[] sum,
                         boolean rowTimeSeries, boolean colTimeSeries,
                         int rowTimeseriesLevel, int colTimeseriesLevel)
   {
      this(table, rowh, colh, dcol, sum, rowTimeSeries, colTimeSeries,
         rowTimeseriesLevel, colTimeseriesLevel, true);
   }

   /**
    * Create a crosstab table with the specified row, column, and data
    * columns. An optional formula can be supplied.
    * @param table base table.
    * @param rowh row header column.
    * @param colh column header column.
    * @param dcol data column.
    * @param sum formula or null.
    * @param rowTimeSeries if apply timeseries for inner row header.
    * @param colTimeSeries if apply timeseries for inner col header.
    * @param rowTimeseriesLevel timeseries data level of row.
    * @param colTimeseriesLevel timeseries data level of col.
    * @param mergeSpan  if merge span when display the table.
    */
   public CrossTabFilter(TableLens table, int[] rowh, int[] colh, int[] dcol, Formula[] sum,
                         boolean rowTimeSeries, boolean colTimeSeries,
                         int rowTimeseriesLevel, int colTimeseriesLevel, boolean mergeSpan)
   {
      this.sum = sum;

      if(table instanceof HeaderRowTableLens) {
         table = ((HeaderRowTableLens) table).getTable();
      }

      setTable(table);

      this.rowh = rowh.length == 0 ? new int[] {-1} : rowh;
      this.colh = colh.length == 0 ? new int[] {-1} : colh;
      this.dcol = dcol;
      this.rowTimeSeries = rowTimeSeries;
      this.colTimeSeries = colTimeSeries;
      this.rowTimeseriesLevel = rowTimeseriesLevel;
      this.colTimeseriesLevel = colTimeseriesLevel;
      this.mergeSpan = mergeSpan;

      // @by yuz, check duptimes for row header, column header, data column
      // dcol:0-99, rowh:100-199, colh:200-299
      // @by davyc, row header(s) and column header(s) process together,
      //  share 100-299, row header(s) from 100-100 + row header(s) length,
      //  column header(s) from 100 + row header(s) length-299, to make sure
      //  all column name in column header(s) and row header(s) distinct
      // fix bug1257246285346
      this.duptimes = new int[300];

      int[] cnts = new int[table.getColCount()];
      boolean dup = false;

      // check dcol dup times first
      for(int i = 0; i < dcol.length; i++) {
         // calc header will be post processed in getHeader function
         duptimes[i] = cnts[dcol[i]];
         cnts[dcol[i]]++;
         dup = dup || duptimes[i] > 0;
      }

      // try to modified the dcol index with header on base tablelens if need
      if(dup) {
         for(int i = 0; i < dcol.length; i++) {
            if(duptimes[i] > 0) {
               Object hdr = table.getObject(0, dcol[i]);
               hdr = (hdr == null) ? "" : hdr.toString();
               hdr = Util.getDupHeader(hdr, duptimes[i]);
               int index = Util.findColumn(columnIndexMap, hdr);

               if(index >= 0) {
                  dcol[i] = index;
               }
            }
         }
      }

      // recheck dcol dup times first after modified
      cnts = new int[table.getColCount()];
      dup = false;

      // check dcol dup times first
      for(int i = 0; i < dcol.length; i++) {
         // do not calculate dup times when the column is vs CalcField
         if(sum != null && i < sum.length && sum[i] instanceof CalcFieldFormula) {
            continue;
         }

         duptimes[i] = cnts[dcol[i]];
         cnts[dcol[i]]++;

         dup = dup || duptimes[i] > 0;
      }

      // check rowh dup times second
      cnts = new int[table.getColCount()];

      for(int i = 0; i < rowh.length; i++) {
         duptimes[i + 100] = cnts[rowh[i]];
         cnts[rowh[i]]++;

         dup = dup || duptimes[i + 100] > 0;
      }

      // row header(s) and column header(s) share 100-299
      /*
      // check colh dup times
      cnts = new int[table.getColCount()];
      */

      for(int i = 0; i < colh.length; i++) {
         duptimes[i + this.rowh.length + 100] = cnts[colh[i]];
         cnts[colh[i]]++;

         dup = dup || duptimes[i + this.rowh.length + 100] > 0;
      }

      // @by larryl, if no duplicate header, skip the checking later
      if(!dup) {
         duptimes = null;
      }

      this.rcount = this.rowh.length;
      this.ccount = this.colh.length;

      this.rowtopns = new InnerTopNInfo[rcount];
      this.coltopns = new InnerTopNInfo[ccount];
      this.rowSortByVals = new InnerTopNInfo[rcount];
      this.colSortByVals = new InnerTopNInfo[ccount];
      this.dcomparers = new Comparer[this.dcol.length];

      Arrays.fill(dcomparers, defComparer);

      // @by billh, the most detailed row header's suppress group total
      // is always false, and the most detailed col header's suppress
      // group total is always false too
      supRowGroupTotal = new boolean[rcount - 1];
      supColGroupTotal = new boolean[ccount - 1];

      for(int i = 0; i < supRowGroupTotal.length; i++) {
         setSuppressRowGroupTotal(true, i);
      }

      for(int i = 0; i < supColGroupTotal.length; i++) {
         setSuppressColumnGroupTotal(true, i);
      }

      Arrays.fill(supRowGroupTotal, true);
      Arrays.fill(supColGroupTotal, true);
   }

   /**
    * Get the row header count.
    */
   @Override
   public int getRowHeaderCount() {
      return rowh == null ? 0 : rowh.length;
   }

   /**
    * Get the column header count.
    */
   @Override
   public int getColHeaderCount() {
      return colh == null ? 0 : colh.length;
   }

   /**
    * Set the base table to be used with the attribute table table.
    * <p>
    * Note: override super.setTable not to cache data.
    *
    * @param table the base table
    */
   @Override
   public void setTable(TableLens table) {
      this.table = table;

      if(table != null) {
         columnIndexMap = new ColumnIndexMap(table, true);
      }
      else {
         columnIndexMap = null;
      }

      invalidate();
      this.table.addChangeListener(new DefaultTableChangeListener(this));
   }

   /**
    * Get internal table data descriptor which contains table structural
    * infos.
    * @return table data descriptor.
    */
   @Override
   public TableDataDescriptor getDescriptor() {
      if(cdescriptor == null) {
         cdescriptor = new CrosstabDataDescriptor(this);
      }

      return cdescriptor;
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
    * Return the number of rows in the table. The number of rows includes
    * the header rows.
    * @return number of rows in table.
    */
   @Override
   public int getRowCount() {
      checkInit();
      return data.length;
   }

   /**
    * Return the number of columns in the table. The number of columns
    * includes the header columns.
    * @return number of columns in table.
    */
   @Override
   public int getColCount() {
      checkInit();
      return data[0].length;
   }

   /**
    * Get the span contains all cells.
    * @hidden
    */
   public Rectangle getVSSpan(int r, int c) {
      Pair p = new Pair(r, c);
      return vsspanmap.get(p);
   }

   /**
    * Create span for viewsheet to paint.
    * @hidden
    */
   public void createVSSpan() {
      if(!sideBySide || getDataColCount() <= 1) {
         return;
      }

      int cheaders = colh == null || colh.length == 1 && colh[0] == -1 ?
         0 : colh.length;

      // @by davyc, make sure the logic match flex side
      // @see Crosstable.as mergedColumnRequired function
      if(cheaders <= 0) {
         return;
      }

      int hrow = getHeaderRowCount();

      for(int i = 0; i < hrow; i++) {
         for(int j = 0; j < getColCount(); j++) {
            Dimension span = getSpan(i, j);
            Pair p = new Pair(i, j);
            Rectangle rect;

            if(j <= getRowHeaderCount() && span != null) {
               rect = new Rectangle(0, 0, span.width, span.height);
               vsspanmap.put(p, rect);
               continue;
            }

            if(j == 0) {
               int r = findSpanIndex(i, j, true);

               if(r == -1) {
                  rect = new Rectangle(0, 0, 1, 1);
               }
               else {
                  span = getSpan(r, j);
                  rect = new Rectangle(0, r - i, span.width, span.height);
               }

               vsspanmap.put(p, rect);
            }
            else {
               int c = findSpanIndex(i, j, false);

               if(c == -1) {
                  rect = new Rectangle(0, 0, 1, 1);
               }
               else {
                  span = getSpan(i, c);
                  rect = new Rectangle(c - j, 0, span.width, span.height);
               }

               vsspanmap.put(p, rect);
            }
         }
      }
   }

   /**
    * Find a not null span index in span map.
    */
   private int findSpanIndex(int r, int c, boolean isVertical) {
      int rt = -1;
      int start = isVertical ? r : c;

      for(int i = start; i >= 0; i--) {
         Dimension span = isVertical ? getSpan(i, c) : getSpan(r, i);

         if(span == null) {
            continue;
         }

         int length = isVertical ? span.height : span.width;

         if(start - i <= length - 1) {
            rt = i;
         }
      }

      return rt;
   }

   /**
    * Return the number of rows on the top of the table to be treated
    * as header rows.
    * @return number of header rows.
    */
   @Override
   public final int getHeaderRowCount() {
      checkInit();
      return ccount + hrowmore;
   }

   /**
    * Return the number of columns on the left of the table to be
    * treated as header columns.
    */
   @Override
   public final int getHeaderColCount() {
      checkInit();
      return rcount + hcolmore;
   }

   /**
    * Return the number one or more data columns
    */
   @Override
   public final int getDataColCount() {
      return dcol == null ? 0 : dcol.length;
   }

   /**
    * Return the number of rows on the bottom of the table to be treated
    * as tail rows.
    * @return number of tail rows.
    */
   @Override
   public int getTrailerRowCount() {
      checkInit();
      return isSuppressRowGrandTotal() ? 0 : (sideBySide ? 1 : dcol.length);
   }

   /**
    * Return the number of columns on the right of the table to be
    * treated as tail columns.
    */
   @Override
   public int getTrailerColCount() {
      checkInit();
      return isSuppressColumnGrandTotal() ? 0 :
         (sideBySide ? dcol.length : 1);
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
      if(grid != null) {
         return grid.getBorderColor(getRowBorderType(r, c));
      }

      return table.getRowBorderColor(getStyleRow(r), getStyleCol(c));
   }

   /**
    * Return the color for drawing the column border lines.
    * @param r row number.
    * @param c column number.
    * @return ruling color.
    */
   @Override
   public Color getColBorderColor(int r, int c) {
      if(grid != null) {
         return grid.getBorderColor(getColBorderType(r, c));
      }

      return table.getColBorderColor(getStyleRow(r), getStyleCol(c));
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
      if(r < getHeaderRowCount() - 1 && c < getHeaderColCount() && !keepheader) {
         return StyleConstants.NONE;
      }

      if(grid != null) {
         return grid.getBorder(getRowBorderType(r, c));
      }

      return table.getRowBorder(getStyleRow(r), getStyleCol(c));
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
      if(c < getHeaderColCount() - 1 && r < getHeaderRowCount() && !keepheader) {
         return StyleConstants.NONE;
      }

      if(grid != null) {
         return grid.getBorder(getColBorderType(r, c));
      }

      return table.getColBorder(getStyleRow(r), getStyleCol(c));
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
    * Check if a specified row and column is total cell.
    */
   @Override
   public boolean isTotalCell(int r, int c) {
      return totalPos.get(r, c) == Boolean.TRUE;
   }

   /**
    * Check if a specified row is a total row.
    */
   @Override
   public boolean isTotalRow(int r) {
      if(r < getHeaderRowCount()) {
         return false;
      }

      Tuple tuple = getRowTuple(r);
      return tuple != null && tuple.size() < getRowHeaderCount();
   }

   /**
    * Check if a specified row is a total row.
    */
   @Override
   public boolean isTotalCol(int c) {
      if(c < getHeaderColCount()) {
         return false;
      }

      Tuple tuple = getColTuple(c);
      return tuple != null && tuple.size() < getColHeaderCount();
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
      return getSpan(r, c, !mergeSpan);
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
   public Dimension getSpan(int r, int c, boolean ignoreSpan) {
      int headerrowcount = getHeaderRowCount();

      if(repeatRowLabels && c < rcount && r >= headerrowcount) {
         int rpos = rowinc == 0 ? -1 : (r - headerrowcount) / rowinc;
         Tuple rtuple = (rpos < 0 || rpos >= rvec.size())
            ? null : rvec.get(rpos);

         // @by yuz, don't repeat TOTAL rows even repeatRowLabels is true
         if(rtuple == null || rtuple.size() >= rcount || c < rtuple.size()) {
            return null;
         }
      }

      Pair p = new Pair(r, c);

      if(!ignoreSpan || isGrandTotalCell(r, c) || isTotalCell(r, c) || isCornerCell(r, c)) {
         return spanmap.get(p);
      }

      return new Dimension(1, 1);
   }

   /**
    * @param r row number.
    * @param c column number.
    * @return if the target cell is a corner cell.
    */
   @Override
   public boolean isCornerCell(int r, int c) {
      return c < getHeaderColCount() && r < getHeaderRowCount();
   }

   /**
    * Set span.
    * @hidden
    */
   public void setSpan(int r, int c, Dimension span) {
      if(span == null) {
         spanmap.remove(new Pair(r, c));
      }
      else {
         spanmap.put(new Pair(r, c), span);
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
      if(r < getHeaderRowCount()) {
         return StyleConstants.H_CENTER;
      }

      if(c < getHeaderColCount()) {
         return StyleConstants.H_CENTER;
      }

      return StyleConstants.H_RIGHT;
   }

   /**
    * Return the per cell font. Return null to use default font.
    * @param r row number.
    * @param c column number.
    * @return font for the specified cell.
    */
   @Override
   public Font getFont(int r, int c) {
      checkInit();

      // apply foreground
      return table.getFont(getStyleRow(r), getStyleCol(c));
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
      checkInit();

      if(r == 0 && c == 0) {
         return null;
      }

      return table.getForeground(getStyleRow(r), getStyleCol(c));
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
      checkInit();

      if(r == 0 && c == 0) {
         return null;
      }

      return table.getBackground(getStyleRow(r), getStyleCol(c));
   }

   /**
    * Return the value at the specified cell.
    * @param r row number.
    * @param c column number.
    * @return the value at the location.
    */
   @Override
   public Object getObject(int r, int c) {
      checkInit();

      if(r == 0 && c >= getHeaderColCount() && c < data[r].length && data[r][c] == null ||
         c == 0 && r >= getHeaderRowCount() && r < data.length && data[r][c] == null)
      {
         return getGrandTotalLabel();
      }

      if(r < data.length && c < data[r].length) {
         return data[r][c];
      }

      return null;
   }

   /**
    * @param  row the row number.
    * @param  col the col number.
    * @return if the target data is a filled date for timeseries.
    */
   public boolean isFilledDate(int row, int col) {
      checkInit();

      if(!isHeaderCell(row, col)) {
         return false;
      }

      if(isRowHeaderCell(row, col) && row == ccount - 1 &&
         isColTimeSeries() && colTimeseriesLevel != XConstants.NONE_DATE_GROUP)
      {
         List<Object> list = new ArrayList<>();

         for(int i = 0; i < ccount; i++) {
            list.add(getObject(i, col));
         }

         Object[] arr = list.toArray();

         for(Tuple t : ocvec) {
            if(Tool.equalsContent(t.getRow(), arr)) {
               return false;
            }
         }

         return true;
      }

      if(isColHeaderCell(row, col) && col == rcount - 1 &&
         isRowTimeSeries() && rowTimeseriesLevel != XConstants.NONE_DATE_GROUP)
      {
         List<Object> list = new ArrayList<>();

         for(int i = 0; i < rcount; i++) {
            list.add(getObject(row, i));
         }

         Object[] arr = list.toArray();

         for(Tuple t : orvec) {
            if(Tool.equalsContent(t.getRow(), arr)) {
               return false;
            }
         }

         return true;
      }

      return false;
   }

   /**
    * Set the cell value.
    * @param r row number.
    * @param c column number.
    * @param v cell value.
    */
   @Override
   public void setObject(int r, int c, Object v) {
      checkInit();

      if(r == 0 && c >= getHeaderColCount() && data[r][c] == null ||
         c == 0 && r >= getHeaderRowCount() && data[r][c] == null)
      {
         totalLabel = v == null ? null : v.toString();
      }
      else {
         data[r][c] = v;
      }

      fireChangeEvent();
   }

   /**
    * Get grand total label.
    */
   @Override
   public String getGrandTotalLabel() {
      return totalLabel == null ?
         null : Catalog.getCatalog().getString(totalLabel);
   }

   /**
    * Get the row border type of a cell (defined in CrosstabGrid).
    */
   private int getRowBorderType(int r, int c) {
      // is cells or column label
      if(c >= getHeaderColCount()) {
         // is cell
         if(r >= getHeaderRowCount()) {
            if(r == getRowCount() - 1) {
               return CrosstabGrid.CELLS_BOTTOM_BORDER;
            }

            return CrosstabGrid.CELLS_HORIZONTAL_LINES;
         }

         // column label
         if(r == -1) {
            return CrosstabGrid.COLUMN_LABEL_TOP_BORDER;
         }
         else if(r == getHeaderRowCount() - 1) {
            return CrosstabGrid.COLUMN_LABEL_BOTTOM_BORDER;
         }
         else {
            return CrosstabGrid.COLUMN_LABEL_HORIZONTAL_LINES;
         }
      }

      // is row label
      if(r < getHeaderRowCount()) {
         return CrosstabGrid.ROW_LABEL_TOP_BORDER;
      }
      else if(r == getRowCount() - 1) {
         return CrosstabGrid.ROW_LABEL_BOTTOM_BORDER;
      }
      else {
         return CrosstabGrid.ROW_LABEL_HORIZONTAL_LINES;
      }
   }

   /**
    * Get the column border type of a cell (defined in CrosstabGrid).
    */
   private int getColBorderType(int r, int c) {
      // is cells or row label
      if(r >= getHeaderRowCount()) {
         // is cell
         if(c >= getHeaderColCount()) {
            if(c == getColCount() - 1) {
               return CrosstabGrid.CELLS_RIGHT_BORDER;
            }

            return CrosstabGrid.CELLS_VERTICAL_LINES;
         }

         // row label
         if(c == -1) {
            return CrosstabGrid.ROW_LABEL_LEFT_BORDER;
         }
         else if(c == getHeaderColCount() - 1) {
            return CrosstabGrid.ROW_LABEL_RIGHT_BORDER;
         }
         else {
            return CrosstabGrid.ROW_LABEL_VERTICAL_LINES;
         }
      }

      // is column label
      if(c < getHeaderColCount()) {
         return CrosstabGrid.COLUMN_LABEL_LEFT_BORDER;
      }
      else if(c == getColCount() - 1) {
         return CrosstabGrid.COLUMN_LABEL_RIGHT_BORDER;
      }
      else {
         return CrosstabGrid.COLUMN_LABEL_VERTICAL_LINES;
      }
   }

   /**
    * Set the crosstab grid type.
    */
   public void setGrid(CrosstabGrid grid) {
      this.grid = grid;
   }

   /**
    * Get the crosstab grid type.
    */
   public CrosstabGrid getGrid() {
      return grid;
   }

   /**
    * Set the label string for total columns and rows.
    */
   @Override
   public void setTotalLabel(String label) {
      if(label != null) {
         totalLabel = label;
      }
   }

   /**
    * Get the label string for total columns and rows.
    */
   public String getTotalLabel() {
      return totalLabel;
   }

   /**
    * Set the row header comparer used for comparison of row header values.
    */
   public void setRowHeaderComparer(Comparer comp) {
      rowComparer = new Comparer[] {comp};
   }

   /**
    * Set the row header comparer used for comparison of row header values.
    */
   public void setRowHeaderComparer(int hdridx, Comparer comp) {
      if(hdridx >= rowComparer.length) {
         Comparer[] narr = new Comparer[hdridx + 1];
         System.arraycopy(rowComparer, 0, narr, 0, rowComparer.length);
         rowComparer = narr;
      }

      rowComparer[hdridx] = comp;
      setDefaultComparer(rowComparer);
   }

   /**
    * Get the comparer used for comparison of row header values.
    */
   @Override
   public Comparer[] getRowComparers() {
      return rowComparer;
   }

   /**
    * Get the comparer used for comparison of column header values.
    */
   @Override
   public Comparer[] getColComparers() {
      return colComparer;
   }

   /**
    * Get the comparer used for comparison of row header values.
    */
   public Comparer getRowComparer() {
      return rowComparer[0];
   }

   /**
    * Get the comparer used for comparison of row header values.
    */
   public Comparer getRowComparer(int hdridx) {
      return rowComparer[hdridx % rowComparer.length];
   }

   /**
    * Set the column header comparer used for comparison of column
    * header values.
    */
   public void setColHeaderComparer(Comparer comp) {
      colComparer = new Comparer[] {comp};
   }

   /**
    * Set the column header comparer used for comparison of column
    * header values.
    */
   public void setColHeaderComparer(int hdridx, Comparer comp) {
      if(hdridx >= colComparer.length) {
         Comparer[] narr = new Comparer[hdridx + 1];
         System.arraycopy(colComparer, 0, narr, 0, colComparer.length);
         colComparer = narr;
      }

      colComparer[hdridx] = comp;
      setDefaultComparer(colComparer);
   }

   /**
    * Get the column comparer used for comparison of column header values.
    */
   public Comparer getColComparer() {
      return colComparer[0];
   }

   /**
    * Get the comparer used for comparison of column header values.
    */
   public Comparer getColComparer(int hdridx) {
      return colComparer[hdridx % colComparer.length];
   }

   /**
    * Set the null comparers to the default comparer.
    */
   private void setDefaultComparer(Comparer[] comps) {
      for(int i = 0; i < comps.length; i++) {
         if(comps[i] == null) {
            comps[i] = defComparer;
         }
      }
   }

   /**
    * Set the comparer for a data column.
    *
    * @param col the data column index in data columns, not data column index
    * in base table
    * @param comp the comparer
    */
   public void setDataComparer(int col, Comparer comp) {
      if(col < 0 || col >= dcol.length) {
         return;
      }

      if(comp != null) { // set new
         dcomparers[col] = comp;
      }
      else { // remove old
         dcomparers[col] = defComparer;
      }
   }

   /**
    * Get the comparer for a data column.
    *
    * @param col the data column index in data columns, not data column index
    * in base table
    * @return the comparer or <code>null</code> if out of range
    */
   public Comparer getDataComparer(int col) {
      if(col < 0 || col >= dcol.length) {
         return null;
      }

      return dcomparers[col];
   }

   /**
    * Set a sort info.
    */
   @Override
   public void setSortInfo(CrosstabSortInfo sinfo) {
      this.sinfo = sinfo;
      invalidate();
   }

   /**
    * Set row header topn.
    *
    * @param rcol the row header index in row headers, not row header's column
    * index in base table
    * @param dcol the data column index in data columns, not data column index
    * in base table
    * @param topn the topn value
    * @param reverse true if should reverse
    */
   public void setRowTopN(int rcol, int dcol, int topn, boolean reverse, boolean others) {
      if(rcol >= 0 && rcol < rowtopns.length && dcol >= 0 &&
         dcol < this.dcol.length && topn >= 1)
      {
         InnerTopNInfo info = new InnerTopNInfo(dcol, topn, reverse, others);
         rowtopns[rcol] = info;
      }
   }

   /**
    * Set col header topn.
    *
    * @param ccol the col header index in col headers, not col header's column
    * index in base table
    * @param dcol the data column index in data columns, not data column index
    * in base table
    * @param topn the topn value
    * @param reverse true if should reverse
    */
   public void setColTopN(int ccol, int dcol, int topn, boolean reverse, boolean others) {
      if(ccol >= 0 && ccol < coltopns.length && dcol >= 0 &&
         dcol < this.dcol.length && topn >= 1)
      {
         InnerTopNInfo info = new InnerTopNInfo(dcol, topn, reverse, others);
         coltopns[ccol] = info;
      }
   }

   /**
    * Set row header sort by value info.
    * @param ccol the col header index in col headers, not col header's column
    * index in base table
    * @param dcol the data column index in data columns, not data column index
    * in base table
    * @param reverse true if should reverse
    */
   public void setRowSortByValInfo(int ccol, int dcol, boolean reverse) {
      if(ccol >= 0 && ccol < rowSortByVals.length && dcol >= 0 &&
         dcol < this.dcol.length)
      {
         InnerTopNInfo info = new InnerTopNInfo(dcol, Integer.MAX_VALUE,
            reverse, false);
         rowSortByVals[ccol] = info;
      }
   }

   /**
    * Set col header sort by value info.
    * @param ccol the col header index in col headers, not col header's column
    * index in base table
    * @param dcol the data column index in data columns, not data column index
    * in base table
    * @param reverse true if should reverse
    */
   public void setColSortByValInfo(int ccol, int dcol, boolean reverse) {
      if(ccol >= 0 && ccol < colSortByVals.length && dcol >= 0 &&
         dcol < this.dcol.length)
      {
         InnerTopNInfo info = new InnerTopNInfo(dcol, table.getRowCount(), reverse, false);
         colSortByVals[ccol] = info;
      }
   }

   /**
    * Set the global topN. The topN defined through this method is applied
    * across all cells at table level (instead of within each group).
    * @param dcol the aggregate column to compare values.
    * @param n the top N.
    * @param topn true to get top and false to get bottom values.
    */
   public void setTopNAggregateInfo(int dcol, int n, boolean topn) {
      topNAggregateComparator = null;
      this.topNAggregateCol = dcol;
      this.topNAggregateN = n;

      if(topNAggregateCol >= 0 && topNAggregateN > 0) {
         topNAggregateComparator = new SectionComparer(topNAggregateCol, !topn);
      }

      invalidate();
   }

   /**
    * Remove row header topn.
    *
    * @param rcol the row header index in row headers, not row header's column
    * index in base table
    */
   public void removeRowTopN(int rcol) {
      if(rcol >= 0 && rcol < rowtopns.length) {
         rowtopns[rcol] = null;
      }
   }

   /**
    * Remove col header topn.
    *
    * @param ccol the col header index in col headers, not col header's column
    * index in base table
    */
   public void removeColTopN(int ccol) {
      if(ccol >= 0 && ccol < coltopns.length) {
         coltopns[ccol] = null;
      }
   }

   /**
    * Remove row header sort value info.
    *
    * @param rcol the row header index in row headers, not row header's column
    * index in base table
    */
   public void removeRowSortByVal(int rcol) {
      if(rcol >= 0 && rcol < rowSortByVals.length) {
         rowSortByVals[rcol] = null;
      }
   }

   /**
    * Remove col header sort by value info.
    *
    * @param ccol the col header index in col headers, not col header's column
    * index in base table
    */
   public void removeColSortByVal(int ccol) {
      if(ccol >= 0 && ccol < colSortByVals.length) {
         colSortByVals[ccol] = null;
      }
   }

   /**
    * Test if a row header contains topn.
    *
    * @param rcol the row header index in row headers, not row header's column
    * index in base table
    * @return true if the specified row header contains topn, false otherwise
    */
   public boolean containsRowTopN(int rcol) {
      if(rcol < 0 || rcol >= rowtopns.length) {
         return false;
      }

      return rowtopns[rcol] != null;
   }

   /**
    * Test if a col header contains topn.
    *
    * @param ccol the col header index in col headers, not col header's column
    * index in base table
    * @return true if the specified col header contains topn, false otherwise
    */
   public boolean containsColTopN(int ccol) {
      if(ccol < 0 || ccol >= coltopns.length) {
         return false;
      }

      return coltopns[ccol] != null;
   }

   /**
    * Test if a row header contains sort by value info.
    *
    * @param rcol the row header index in row headers, not row header's column
    * index in base table
    * @return true if the specified row header contains topn, false otherwise
    */
   public boolean containsRowSortByVal(int rcol) {
      if(rcol < 0 || rcol >= rowSortByVals.length) {
         return false;
      }

      return rowSortByVals[rcol] != null;
   }

   /**
    * Test if a col header contains sort by value info.
    *
    * @param ccol the col header index in col headers, not col header's column
    * index in base table
    * @return true if the specified col header contains topn, false otherwise
    */
   public boolean containsColSortByVal(int ccol) {
      if(ccol < 0 || ccol >= colSortByVals.length) {
         return false;
      }

      return colSortByVals[ccol] != null;
   }

   /**
    * Test if should topn row headers.
    * <p>
    * Topn calculation is a high overhead.
    *
    * @return true if should, false otherwise
    */
   private boolean shouldRowTopN() {
      for(int i = 0; i < rowtopns.length; i++) {
         if(getRowTopN(i) != null) {
            return true;
         }
      }

      return false;
   }

   /**
    * Test if should topn row headers.
    * <p>
    * Topn calculation is a high overhead.
    *
    * @return true if should, false otherwise
    */
   private boolean shouldRowTopN(boolean calc) {
      for(int i = 0; i < rowtopns.length; i++) {
         InnerTopNInfo rowTopN = getRowTopN(i);

         if(calc && rowTopN != null && supportSortByCalcAggregate(rowTopN.dcol) ||
            !calc && rowTopN != null && !supportSortByCalcAggregate(rowTopN.dcol))
         {
            return true;
         }
      }

      return false;
   }

   /**
    * Test if should topn col headers.
    * <p>
    * Topn calculation is a high overhead.
    *
    * @return true if should, false otherwise
    */
   private boolean shouldColTopN() {
      for(int i = 0; i < coltopns.length; i++) {
         if(getColTopN(i) != null) {
            return true;
         }
      }

      return false;
   }

   /**
    * Test if should topn col headers.
    * <p>
    * Topn calculation is a high overhead.
    *
    * @return true if should, false otherwise
    */
   private boolean shouldColTopN(boolean calc) {
      for(int i = 0; i < coltopns.length; i++) {
         InnerTopNInfo colTopN = getColTopN(i);

         if(calc && colTopN != null && supportSortByCalcAggregate(colTopN.dcol) ||
            !calc && colTopN != null && !supportSortByCalcAggregate(colTopN.dcol))
         {
            return true;
         }
      }

      return false;
   }

   /**
    * Test if should row sort by value info exists.
    * <p>
    * Topn calculation is a high overhead.
    *
    * @return true if should, false otherwise
    */
   private boolean shouldRowSortByVal() {
      for(int i = 0; i < rowtopns.length; i++) {
         if(getRowSortByValInfo(i) != null) {
            return true;
         }
      }

      return false;
   }

   /**
    * Test if should row sort by value info exists.
    * <p>
    * Topn calculation is a high overhead.
    *
    * @return true if should, false otherwise
    */
   private boolean shouldRowSortByVal(boolean calc) {
      for(int i = 0; i < rowtopns.length; i++) {
         InnerTopNInfo rowSortByValInfo = getRowSortByValInfo(i);

         if(calc && rowSortByValInfo != null && supportSortByCalcAggregate(rowSortByValInfo.dcol) ||
            !calc && rowSortByValInfo != null && !supportSortByCalcAggregate(rowSortByValInfo.dcol))
         {
            return true;
         }
      }

      return false;
   }

   /**
    * Check whether the aggregate contains calc.
    * @param index aggregate index.
    * @return
    */
   public boolean isCalcAggregate(int index) {
      if(calcs == null) {
         return false;
      }

      for(CalcColumn calc : calcs) {
         if(calc != null && calc.getColIndex() == index) {
            return true;
         }
      }

      return false;
   }

   /**
    * Check whether the aggregate contains calc.
    * @param index aggregate index.
    * @return
    */
   public boolean supportSortByCalcAggregate(int index) {
      if(calcs == null) {
         return false;
      }

      for(CalcColumn calc : calcs) {
         if(calc != null && calc.getColIndex() == index && calc.supportSortByValue()) {
            return true;
         }
      }

      return false;
   }

   /**
    * Check if should sort on header.
    */
   private boolean shouldSortOnHeader() {
      return sinfo != null && sinfo.cols.length > 0;
   }

   /**
    * Test if should colomn sort by value info exists.
    * <p>
    * Topn calculation is a high overhead.
    *
    * @return true if should, false otherwise
    */
   private boolean shouldColSortByVal() {
      for(int i = 0; i < coltopns.length; i++) {
         if(getColSortByValInfo(i) != null) {
            return true;
         }
      }

      return false;
   }

   /**
    * Test if should colomn sort by value info exists.
    * <p>
    * Topn calculation is a high overhead.
    *
    * @return true if should, false otherwise
    */
   private boolean shouldColSortByVal(boolean calc) {
      for(int i = 0; i < coltopns.length; i++) {
         InnerTopNInfo colSortByValInfo = getColSortByValInfo(i);

         if(calc && colSortByValInfo != null && supportSortByCalcAggregate(colSortByValInfo.dcol) ||
            !calc && colSortByValInfo != null && !supportSortByCalcAggregate(colSortByValInfo.dcol))
         {
            return true;
         }
      }

      return false;
   }

   /**
    * Get a row header topn.
    *
    * @param rcol the row header index in row headers, not row header's column
    * index in base table
    * @return the row header topn of the specified row header
    */
   private InnerTopNInfo getRowTopN(int rcol) {
      if(rcol < 0 || rcol >= rowtopns.length) {
         return null;
      }

      return rowtopns[rcol];
   }

   /**
    * Get a col header topn.
    *
    * @param ccol the col header index in col headers, not col header's column
    * index in base table
    * @return the col header topn of the specified col header
    */
   private InnerTopNInfo getColTopN(int ccol) {
      if(ccol < 0 || ccol >= coltopns.length) {
         return null;
      }

      return coltopns[ccol];
   }

   /**
    * Get a row header topn.
    *
    * @param rcol the row header index in row headers, not row header's column
    * index in base table
    * @return the row header topn of the specified row header
    */
   private InnerTopNInfo getRowSortByValInfo(int rcol) {
      if(rcol < 0 || rcol >= rowSortByVals.length) {
         return null;
      }

      return rowSortByVals[rcol];
   }

   /**
    * Get a row header topn.
    *
    * @param rcol the row header index in row headers, not row header's column
    * index in base table
    * @return the row header topn of the specified row header
    */
   private InnerTopNInfo getRowSortByValInfo(int rcol, boolean calc) {
      if(rcol < 0 || rcol >= rowSortByVals.length) {
         return null;
      }

      InnerTopNInfo rowSortByVal = rowSortByVals[rcol];

      if(rowSortByVal != null && (calc && supportSortByCalcAggregate(rowSortByVal.dcol) ||
         !calc && !supportSortByCalcAggregate(rowSortByVal.dcol)))
      {
         return rowSortByVal;
      }


      return null;
   }

   /**
    * Get a col header topn.
    *
    * @param ccol the col header index in col headers, not col header's column
    * index in base table
    * @return the col header topn of the specified col header
    */
   private InnerTopNInfo getColSortByValInfo(int ccol) {
      if(ccol < 0 || ccol >= colSortByVals.length) {
         return null;
      }

      return colSortByVals[ccol];
   }

   /**
    * Get a col header topn.
    *
    * @param ccol the col header index in col headers, not col header's column
    * index in base table
    * @return the col header topn of the specified col header
    */
   private InnerTopNInfo getColSortByValInfo(int ccol, boolean calc) {
      if(ccol < 0 || ccol >= colSortByVals.length) {
         return null;
      }

      InnerTopNInfo cowSortByVal = colSortByVals[ccol];

      if(cowSortByVal != null && (calc && supportSortByCalcAggregate(cowSortByVal.dcol) ||
         !calc && !supportSortByCalcAggregate(cowSortByVal.dcol)))
      {
         return cowSortByVal;
      }

      return null;
   }

   /**
    * Get the base table.
    */
   @Override
   public TableLens getTable() {
      return table;
   }

   /**
    * Set option to fill blank cell with zero. By default blank cells
    * are left blank. If this is true, the blank cells are filled with zero.
    * @param fill true to fill blank cell with zero.
    */
   public void setFillBlankWithZero(boolean fill) {
      this.fillwithzero = fill;
   }

   /**
    * Check if column headers are kept.
    */
   public boolean isFillBlankWithZero() {
      return fillwithzero;
   }

   /**
    * If this option is true, and there are multiple summary cells, they are
    * arranged side by side in the table. Otherwise they are arranged vertically.
    * Defaults to false.
    */
   @Override
   public void setSummarySideBySide(boolean horizontal) {
      sideBySide = horizontal;
   }

   /**
    * Check if summary cells are put side by side.
    */
   @Override
   public boolean isSummarySideBySide() {
      return sideBySide;
   }

   /**
    * Set option to keep column headers. By default column headers of
    * the crosstab row header columns are not kept. If this is true,
    * the column header of the header column (leftmost column) is
    * the column header of the row header column in the original table.
    * @param keep true to keep the column headers.
    */
   public void setKeepColumnHeaders(boolean keep) {
      this.keepheader = keep;
   }

   /**
    * Check if column headers are kept.
    */
   public boolean isKeepColumnHeaders() {
      return keepheader;
   }

   /**
    * Set whether to add summary title row/column. If true, a title row is added
    * to the header rows for summary if summary side by side is on. Otherwise,
    * a title column is added to the header columns if summary side by side is
    * off.
    */
   @Override
   public void setShowSummaryHeaders(boolean sumTitle) {
      this.summaryHeaders = sumTitle;
   }

   /**
    * Check whether to add summary title row/column.
    */
   @Override
   public boolean isShowSummaryHeaders() {
      return summaryHeaders;
   }

   /**
    * Set option to repeat row labels. By default the cells for the same group
    * is merged into a single cell. If the repeatRowLabels is set to true, the
    * cells are still displayed individually.
    * @param repeat true to repeat row label.
    */
   @Override
   public void setRepeatRowLabels(boolean repeat) {
      this.repeatRowLabels = repeat;
   }

   /**
    * Check if the row label are repeated.
    */
   @Override
   public boolean isRepeatRowLabels() {
      return repeatRowLabels;
   }

   /**
    * Set option to set the row total area on top of the group.
    * By default the row total area is below the group.
    * @param top true to set the row total on the top.
    */
   @Override
   public void setRowTotalOnTop(boolean top) {
      this.rowTotalOnTop = top;
   }

   /**
    * Check if the row total is on top.
    */
   @Override
   public boolean isRowTotalOnTop() {
      return rowTotalOnTop;
   }

   /**
    * Set option to set the col total area on first col of the group.
    * By default the col total area is on the last col of the group.
    * @param first true to set the col total on the first col.
    */
   @Override
   public void setColumnTotalOnFirst(boolean first) {
      this.colTotalOnFirst = first;
   }

   /**
    * Check if the col total is on first col.
    */
   @Override
   public boolean isColumnTotalOnFirst() {
      return colTotalOnFirst;
   }

   /**
    * Set option to set if suppress the row grand total area.
    * By default the row grand total area is displayed.
    * @param sup true to set suppress the row grand total area.
    */
   @Override
   public void setSuppressRowGrandTotal(boolean sup) {
      this.supRowGrandTotal = sup;
   }

   /**
    * Check if the row grand total is suppressed.
    */
   @Override
   public boolean isSuppressRowGrandTotal() {
      return supRowGrandTotal;
   }

   /**
    * Set option to set if suppress the col grand total area.
    * By default the col grand total area is displayed.
    * @param sup true to set suppress the col grand total area.
    */
   @Override
   public void setSuppressColumnGrandTotal(boolean sup) {
      this.supColGrandTotal = sup;
   }

   /**
    * Check if the col grand total is suppressed.
    */
   @Override
   public boolean isSuppressColumnGrandTotal() {
      return supColGrandTotal;
   }

   /**
    * Set option to set if suppress the row subtotal area.
    * By default the row subtotal area is displayed.
    * @param sup true to set suppress the row subtotal area.
    */
   @Override
   public void setSuppressRowSubtotal(boolean sup) {
      this.supRowSubtotal = sup;
   }

   /**
    * Check if the row subtotal is suppressed.
    */
   @Override
   public boolean isSuppressRowSubtotal() {
      return supRowSubtotal;
   }

   /**
    * Set the option if only aggregate topN rows.
    * @param aggTopN true to aggregate only topN rows.
    */
   public void setAggregateTopN(boolean aggTopN) {
      this.aggTopN = aggTopN;
   }

   /**
    * Check if only aggregate topN rows.
    */
   public boolean isAggregateTopN() {
      return this.aggTopN;
   }

   /**
    * Set option to set if suppress the col subtotal area.
    * By default the col subtotal area is displayed.
    * @param sup true to set suppress the col subtotal area.
    */
   @Override
   public void setSuppressColumnSubtotal(boolean sup) {
      this.supColSubtotal = sup;
   }

   /**
    * Check if the col subtotal is suppressed.
    */
   @Override
   public boolean isSuppressColumnSubtotal() {
      return supColSubtotal;
   }

   /**
    * Set option to set if suppress the row group total area.
    * By default the row group total area is displayed.
    * @param sup true to set suppress the row group total area.
    */
   public void setSuppressRowGroupTotal(boolean[] sup) {
      for(int i = 0; i < sup.length; i++) {
         setSuppressRowGroupTotal(sup[i], i);
      }
   }

   /**
    * Check if the row group total is suppressed.
    */
   public boolean[] getSuppressRowGroupTotal() {
      return supRowGroupTotal;
   }

   /**
    * Set option to set if suppress the col group total area.
    * By default the col group total area is displayed.
    * @param sup true to set suppress the col group total area.
    */
   public void setSuppressColumnGroupTotal(boolean[] sup) {
      for(int i = 0; i < sup.length; i++) {
         setSuppressColumnGroupTotal(sup[i], i);
      }
   }

   /**
    * Check if the col group total is suppressed.
    */
   public boolean[] getSuppressColumnGroupTotal() {
      return supColGroupTotal;
   }

   /**
    * Set option to set if suppress the row group total area.
    * By default the row group total area is displayed.
    * @param sup true to set suppress the row group total area.
    */
   @Override
   public void setSuppressRowGroupTotal(boolean sup, int i) {
      if(i < supRowGroupTotal.length && i >= 0) {
         this.supRowGroupTotal[i] = sup;
      }
   }

   /**
    * Check if the row group total is suppressed.
    */
   @Override
   public boolean isSuppressRowGroupTotal(int i) {
      if(i < supRowGroupTotal.length && i >= 0) {
         return supRowGroupTotal[i];
      }
      else {
         return false;
      }
   }

   /**
    * Set option to set if suppress the col group total area.
    * By default the col group total area is displayed.
    * @param sup true to set suppress the col group total area.
    */
   @Override
   public void setSuppressColumnGroupTotal(boolean sup, int i) {
      if(i < supColGroupTotal.length && i >= 0) {
         this.supColGroupTotal[i] = sup;
      }
   }

   /**
    * Check if the col group total is suppressed.
    */
   @Override
   public boolean isSuppressColumnGroupTotal(int i) {
      if(i < supColGroupTotal.length && i >= 0) {
         return supColGroupTotal[i];
      }
      else {
         return false;
      }
   }

   /**
    * Set percentage direction.
    * only two values: StyleConstants.PERCENTAGE_BY_COL,
    *                   StyleConstants.PERCENTAGE_BY_ROW.
    * the first one is default.
    */
   @Override
   public void setPercentageDirection(int percentageDir) {
      if((percentageDir == StyleConstants.PERCENTAGE_BY_COL) ||
         (percentageDir == StyleConstants.PERCENTAGE_BY_ROW) ||
         (percentageDir == StyleConstants.PERCENTAGE_NONE))
      {
         this.percentageDir = percentageDir;
      }
   }

   /**
    * Return percentage direction.
    */
   @Override
   public int getPercentageDirection() {
      return this.percentageDir;
   }

   /**
    * Set whether to ignore group total where the group contains a single value.
    * @hidden
    */
   @Override
   public void setIgnoreNullTotals(boolean flag) {
      this.ignoreNullTotals = flag;
   }

   /**
    * Check whether to ignore group total where the group contains a single value.
    * @hidden
    */
   @Override
   public boolean isIgnoreNullTotals() {
      return this.ignoreNullTotals;
   }

   /**
    * Set condition.
    */
   public void setCondition(ConditionGroup condition) {
      if(this.condition != condition) {
         this.condition = condition;
         invalidate();
      }
   }

   /**
    * Init section comparers.
    */
   private void initSectionComparers() {
      rscomparers = new SectionComparer[rcount];

      for(int i = 0; i < rcount; i++) {
         InnerTopNInfo info = getRowTopN(i);

         if(info != null) {
            rscomparers[i] = new SectionComparer(info.dcol, info.asc);
         }
      }

      cscomparers = new SectionComparer[ccount];

      for(int i = 0; i < ccount; i++) {
         InnerTopNInfo info = getColTopN(i);

         if(info != null) {
            cscomparers[i] = new SectionComparer(info.dcol, info.asc);
         }
      }

      if(sinfo != null) {
         secComparer = new Section2Comparer(sinfo);
      }
   }

   /**
    * Init section sort by value comparers.
    */
   private void initSortByValComparers() {
      rsSortByValcomparers = new SectionComparer[rcount];

      for(int i = 0; i < rcount; i++) {
         InnerTopNInfo info = getRowSortByValInfo(i);

         if(info != null) {
            rsSortByValcomparers[i] =
               new SortByValSectionComparer(info.dcol, info.asc);
         }
      }

      csSortByValcomparers = new SectionComparer[ccount];

      for(int i = 0; i < ccount; i++) {
         InnerTopNInfo info = getColSortByValInfo(i);

         if(info != null) {
            csSortByValcomparers[i] =
               new SortByValSectionComparer(info.dcol, info.asc);
         }
      }
   }

   /**
    * Get a row/col header's section comparer.
    *
    * @param index the row/col header's index in row/col headers
    * @return the section comparer
    */
   private SectionComparer getSectionComparer(int index, boolean isrow) {
      SectionComparer[] scomparers = isrow ? rscomparers : cscomparers;

      if(index < 0 || index >= scomparers.length) {
         return null;
      }

      return scomparers[index];
   }

   /**
    * Get a row/col header's section sort by value comparer.
    *
    * @param index the row/col header's index in row/col headers
    * @return the section comparer
    */
   private SectionComparer getSortByValComparer(int index, boolean isrow, boolean calc) {
      SectionComparer[] scomparers = isrow ? rsSortByValcomparers : csSortByValcomparers;

      if(index < 0 || index >= scomparers.length) {
         return null;
      }

      SectionComparer scomparer = scomparers[index];

      if(scomparer != null && (calc && supportSortByCalcAggregate(scomparer.dcol) ||
         !calc && !supportSortByCalcAggregate(scomparer.dcol)))
      {
         return scomparer;
      }

      return null;
   }

   /**
    * Invalidate the table filter forcely, and the table filter will perform
    * filtering calculation to validate itself.
    */
   @Override
   public void invalidate() {
      // Header column, row must be recognized to apply TableStyle
      // when CrossTab is created via script from existing table
      // @by larryl, this should only be done for table style since
      // if not, the table style would not know the header counts
      if(table instanceof TableStyle) {
         TableStyle style = (TableStyle) table;

         style.setHeaderColCount(getHeaderColCount());
         style.setHeaderRowCount(getHeaderRowCount());

         style.setTrailerColCount(isSuppressColumnGrandTotal() ? 0 : 1);
         style.setTrailerRowCount(isSuppressRowGrandTotal() ? 0 : 1);
      }

      data = null;
      spanmap.clear();
      mmap.clear();
      rnumMap.clear();
      totalPos.clear();
      formulas.clear();

      fireChangeEvent();
   }

   /**
    * Generate the crosstab.
    */
   private void process() {
      try {
         // for Feature #26586, add post processing time record for current report/vs.
         ProfileUtils.addExecutionBreakDownRecord(getReportName(),
            ExecutionBreakDownRecord.POST_PROCESSING_CYCLE, args -> {
               process0();
            });

         //process0();
      }
      catch(Exception ex) {
         LOG.error("Failed to process crosstab filter", ex);
      }
   }

   /**
    * Generate the crosstab.
    */
   private void process0() {
      // use OrderedSet to maintain original order to support Original Order
      Set<Tuple> rset_ = new OrderedSet<>(); // row header set
      Set<Tuple> cset_ = new OrderedSet<>(); // col header set
      Map<PairN, Object> map = new HashMap<>(); // pair -> object or sum
      valueMap = new Object2ObjectOpenHashMap<>();
      boolean needTotal = condition != null ||
                          shouldRowTopN() || shouldRowSortByVal() ||
                          shouldColTopN() || shouldColSortByVal() ||
                          shouldPercent() || shouldSortOnHeader();
      Map<PairN, Object> totalMap = new HashMap<>(); // group and total pair --> object
      table.moreRows(TableLens.EOT);

      // handle summary title
      // @by arlinex, if there is no row/col header, we use the summary
      // column header as row/col header
      // if there is no row/col header and summaryHeaders is checked, don't show
      // an extra same summary title header row as row/col header
      if(summaryHeaders) {
         if(sideBySide) {
            hrowmore = colh.length == 1 && colh[0] == -1 ? 0 : 1;
         }
         else {
            hcolmore = rowh.length == 1 && rowh[0] == -1 ? 0 : 1;
         }
      }
      else {
         hrowmore = hcolmore = 0;
      }

      // create formula agent
      fagents = new FormulaAgent[table.getColCount()];

      for(int i = 0; i < fagents.length; i++) {
         fagents[i] = FormulaAgent.getAgent(table.getColType(i), table.isPrimitive(i));
      }

      try {
         genTuples(rset_, cset_, totalMap, map, needTotal);
      }
      catch(MaxRowsException ex) {
         // max rows reached, ignore.
      }

      // deal with the first map table
      rowinc = sideBySide ? 1 : dcol.length;
      colinc = sideBySide ? dcol.length : 1;

      rvec = new ArrayList<>(rset_);
      cvec = new ArrayList<>(cset_);
      boolean changed = evaluate(rvec, cvec, map);

      // sort rows
      sort(rvec, new TupleRowComparer(isRowTotalOnTop()));
      // sort columns
      sort(cvec, new TupleColComparer(isColumnTotalOnFirst()));

      if(isIgnoreNullTotals()) {
         removeNullTotals(rvec, isRowTotalOnTop());
         removeNullTotals(cvec, isColumnTotalOnFirst());
      }

      // init section comparers
      initSectionComparers();
      initSortByValComparers();

      if(topNAggregateComparator != null) {
         changed = topNAggregate(rvec, cvec, totalMap, map) || changed;
      }

      orvec = new ArrayList<>(rvec);
      ocvec = new ArrayList<>(cvec);

      if(isRowTimeSeries() && rowTimeseriesLevel != XConstants.NONE_DATE_GROUP) {
         rvec = fixTimeSeriesData(rvec, true);
      }

      if(isColTimeSeries() && colTimeseriesLevel != XConstants.NONE_DATE_GROUP) {
         cvec = fixTimeSeriesData(cvec, false);
      }

      // if should do row header topn calculation, do it
      if(shouldRowTopN(false) || shouldRowSortByVal(false)) {
         rvec = topNFilter(rvec, totalMap, map, true, null, false);
      }

      // if should do col header topn calculation, do it
      if(shouldColTopN(false) || shouldColSortByVal(false)) {
         cvec = topNFilter(cvec, totalMap, map, false, null, false);
      }

      // sort rows and columns again to make sure the 'others' group's position is right
      // when the sort order is not 'sort by value'.
      if(isOthers(rowtopns) && !isSortOthersLast() && !shouldRowSortByVal()) {
         sort(rvec, new TupleRowComparer(isRowTotalOnTop()));
      }

      if(isOthers(coltopns) && !isSortOthersLast() && !shouldColSortByVal()) {
         sort(cvec, new TupleColComparer(isColumnTotalOnFirst()));
      }

      if(shouldRowTopN(false) || shouldColTopN(false) || changed) {
         resetGrandTotal(map);
      }

      if(shouldSortOnHeader()) {
         rvec = sortOnHeader(rvec, totalMap, map);
      }

      Map<PairN, Object> calcValuesMap = new HashMap<>();
      prepareCalcValues(calcValuesMap, map, totalMap, new HashSet<>(rvec), new HashSet<>(cvec));

      if(shouldRowTopN(true) || shouldRowSortByVal(true)) {
         rvec = topNFilter(rvec, totalMap, map, true, calcValuesMap, true);
      }

      if(shouldColTopN(true) || shouldColSortByVal(true)) {
         cvec = topNFilter(cvec, totalMap, map, false, calcValuesMap, true);
      }

      if(shouldRowTopN(true) || shouldColTopN(true) || changed) {
         resetGrandTotal(map);
      }

      if(shouldPercent()) {
         applyPercentage(totalMap, map);
      }

      populateSpanAndData(map, calcValuesMap);
      createXMetaInfo();
      applyDefaultFormat();
   }

   private void prepareCalcValues(Map<PairN, Object> calcValuesMap, Map<PairN, Object> dataMap,
                                  Map<PairN, Object> totalMap, Set<Tuple> rowTuples,
                                  Set<Tuple> colTuples) {
      if(dataMap == null || calcs == null || calcs.size() == 0) {
         return;
      }

      boolean hasRowGrandTotal = shouldRowSortByVal(true);
      boolean hasColGrandTotal = shouldColSortByVal(true);

      if(hasColGrandTotal) {
         rowTuples.add(emptyTuple);
      }

      if(hasRowGrandTotal) {
         colTuples.add(emptyTuple);
      }

      CrosstabDataContext context =
         new CrosstabDataContext(dataMap, totalMap, rowTuples, colTuples);
      boolean shouldRowSortByVal = shouldRowSortByVal(true);
      boolean shouldColSortByVal = shouldColSortByVal(true);
      int rowTupleCount = hasColGrandTotal ? rvec.size() + 1 : rvec.size();
      int colTupleCount = hasRowGrandTotal ? cvec.size() + 1 : cvec.size();

      for(CalcColumn calc : calcs) {
         if(!(calc instanceof AbstractColumn) || calc instanceof PercentColumn) {
            continue;
         }

         for(int i = 0; i < rowTupleCount; i++) {
            for(int j = 0; j < colTupleCount; j++) {
               Tuple rtuple = i < rvec.size() ? rvec.get(i) : emptyTuple;
               Tuple ctuple = j < cvec.size() ? cvec.get(j) : emptyTuple;
               PairN pair = CrossTabFilterUtil.createPair(rtuple, ctuple, calc.getColIndex());
               AbstractColumn abstractColumn = ((AbstractColumn) calc);
               abstractColumn.setCalculateTotal(abstractColumn.isCalculateTotal() ||
                  shouldRowSortByVal || shouldColSortByVal);
               Object calcVal = abstractColumn.calculate(context, pair);
               calcValuesMap.put(pair, calcVal);
            }
         }
      }
   }

   private void applyDefaultFormat() {
      if(data == null) {
         return;
      }

      Comparer[] rowComparers = getRowComparers();

      if(rowComparers != null && rowComparers.length > 0) {
         for(int i = 0; i < rowComparers.length; i++) {
            Comparer comp = rowComparers[i];

            if(comp instanceof SortOrder) {
               SortOrder order = (SortOrder) comp;

               if(order.isSpecific() && !order.isManual()) {
                  for(int r = 0; r < data.length; r++) {
                     if(data[r][i] instanceof Date) {
                        data[r][i] = format(getDefaultFormat(r, i), data[r][i]);
                     }

                     // convert to string, make sure all datas in the column
                     // is same type
                     data[r][i] = data[r][i] == null ? null : Tool.toString(data[r][i]);
                  }
               }
            }
         }
      }

      Comparer[] colComparers = getColComparers();

      if(colComparers != null && colComparers.length > 0) {
         for(int i = 0; i < colComparers.length; i++) {
            Comparer comp = colComparers[i];

            if(comp instanceof SortOrder) {
               SortOrder order = (SortOrder) comp;

               if(order.isSpecific() && !order.isDateManual()) {
                  for(int c = 0; c < data[i].length; c++) {
                     if(data[i][c] instanceof Date) {
                        data[i][c] = format(getDefaultFormat(i, c), data[i][c]);
                     }

                     // convert to string, make sure all datas in the column
                     // is same type
                     data[i][c] = data[i][c] == null ? null : Tool.toString(data[i][c]);
                  }
               }
            }
         }
      }
   }

   private Object format(Format format, Object val) {
      if(format != null) {
         val = XUtil.format(format, val);
      }

      return val;
   }

   @Override
   public List<String> getMeasureHeaders() {
      return measureHeaders == null ? new ArrayList<>() : Arrays.asList(measureHeaders);
   }

   @Override
   public List<String> getMeasureHeaders(boolean applyCalc) {
      if(measureHeaders == null) {
         return new ArrayList<>();
      }
      return Arrays.stream(measureHeaders)
         .map(name -> calcMeasureMap.containsKey(name) ? calcMeasureMap.get(name) : name)
         .collect(Collectors.toList());
   }

   public List<String> getRowHeaders() {
      return rowHeaders == null ? new ArrayList<>() : rowHeaders;
   }

   public List<String> getColHeaders() {
      return colHeaders == null ? new ArrayList<>() : colHeaders;
   }

   /**
    * @param tuple      to collect group values for the target tuple.
    * @param key        the key in valuelist.
    * @param index      the target level to collect values.
    * @param rowHeader  true if search in row headers, else column headers.
    *
    * @return values for the specified index dimension.
    */
   public List<Object> getValues(Tuple tuple, String key, int index, boolean rowHeader) {
      if(tuple == null || key == null || index == -1 || index >= tuple.size()) {
         return new ArrayList<>();
      }

      Map<String, List<Object>> valueList = rowHeader ? rowValueList : colValueList;
      List<Object> values = valueList.get(key);

      if(values != null) {
         return values;
      }

      values = new ArrayList<>();

      if(rowHeader) {
         // tuple row doesn't contains group total and total
         for(Tuple t : rvec) {
            if(CrossTabFilterUtil.isSameGroup(t, tuple, index) &&
               !values.contains(t.getRow()[index]))
            {
               values.add(t.getRow()[index]);
            }
         }
      }
      else {
         // tuple row doesn't contains group total and total
         for(Tuple t : cvec) {
            if(CrossTabFilterUtil.isSameGroup(t, tuple, index) &&
               !values.contains(t.getRow()[index]))
            {
               values.add(t.getRow()[index]);
            }
         }
      }

      valueList.put(key, values);
      return values;
   }

   private final Map<String, List<Object>> rowValueList = new HashMap<>();
   private final Map<String, List<Object>> colValueList = new HashMap<>();

   /**
    * @hidden
    */
   public Object getValue(Pair pair) {
      return pair == null ? null : valueMap.get(pair);
   }

   /**
    * @hidden
    */
   public boolean isValuePairExist(Pair pair) {
      return pair != null && valueMap.containsKey(pair);
   }

   /**
    * @hidden
    */
   public void clearValueMap() {
      valueMap = new Object2ObjectOpenHashMap<>(0);
   }

   /**
    * @hidden
    */
   public void setForCalc(boolean forCalc) {
      this.forCalc = forCalc;
   }

   public int getRowTupleIndex(Tuple tuple) {
      if(rvec == null || rvec.size() == 0) {
         return -1;
      }

      return rvec.indexOf(tuple);
   }

   /**
    * @param row the row index in crosstab filter.
    * @return the target row tuple.
    */
   @Override
   public Tuple getRowTuple(int row) {
      int hrcount = getHeaderRowCount();

      if(row < hrcount) {
         return null;
      }

      row = row - hrcount;

      if(!isSummarySideBySide() && getDataColCount() != 0) {
         row = row / getDataColCount();
      }

      Tuple tuple = row < 0 || row >= rvec.size() ? null : rvec.get(row);
      return tuple == null ? null : CrossTabFilterUtil.createTuple(tuple);
   }

   /**
    * @param col  the col index in crosstab filter.
    * @return the target column header tuple.
    */
   @Override
   public Tuple getColTuple(int col) {
      int hccount = getHeaderColCount();

      // corner
      if(col < hccount) {
         return null;
      }

      col = col - hccount;

      if(isSummarySideBySide()) {
         col = col / getDataColCount();
      }

      Tuple tuple = col < 0 || col >= cvec.size() ? null : cvec.get(col);
      return tuple == null ? null : CrossTabFilterUtil.createTuple(tuple);
   }

   public int getColTupleIndex(Tuple tuple) {
      if(cvec == null || cvec.size() == 0) {
         return -1;
      }

      return cvec.indexOf(tuple);
   }

   /**
    * Add the missing date to support timeseries.
    * @param  vec the row/col header list.
    * @return     the new row/col header list.
    */
   private List<Tuple> fixTimeSeriesData(List<Tuple> vec, boolean row) {
      if(vec.size() < 2) {
         return vec;
      }

      boolean insertTime = false;
      List<Tuple> nvec = new ArrayList<>();
      HashMap<Tuple, Set<Object>> map = getTimeSeriesGroup(vec, row);

      for(Tuple k : map.keySet()) {
         Set<Object> values = map.get(k);
         Object[][] rows;
         Object[] valuesArr = values.toArray();
         boolean hasTotal = false;
         boolean totalBefore = false;
         Object[] toInsert;

         if(valuesArr[0] == null) {
            // has total or grandTotal, when total is left.
            hasTotal = true;
            totalBefore = true;
            toInsert = new Object[valuesArr.length - 1];
            System.arraycopy(valuesArr, 1, toInsert, 0, toInsert.length);
         }
         else if(valuesArr[valuesArr.length - 1] == null) {
            // has total or grandTotal, when total is left.
            hasTotal = true;
            totalBefore = false;
            toInsert = new Object[valuesArr.length - 1];
            System.arraycopy(valuesArr, 0, toInsert, 0, toInsert.length);
         }
         else {
            toInsert = valuesArr;
         }

         rows = insertDate(toInsert, k.getRow(), row);
         insertTime = true;

         if(rows != null) {
            if(hasTotal && totalBefore) {
               nvec.add(k);
            }

            for(Object[] newRow : rows) {
               nvec.add(new Tuple(newRow));
            }

            if(hasTotal && !totalBefore) {
               nvec.add(k);
            }
         }
      }

      return insertTime ? nvec : vec;
   }

   private Object[][] insertDate(Object[] arr, Object[] row, boolean isRow) {
      int dateLevel = isRow ? rowTimeseriesLevel : colTimeseriesLevel;
      return TimeSeriesUtil.fillTimeGap(arr, row, dateLevel, isRow);
   }

   /**
    * @return  the map<key: group tuple, value: date set>.
    */
   private HashMap<Tuple, Set<Object>> getTimeSeriesGroup(List<Tuple> vec, boolean isRow) {
      HashMap<Tuple, Set<Object>> groupMap = new LinkedHashMap<>();
      int hCount = isRow ? rowh.length : colh.length;

      for(Tuple t : vec) {
         Object[] row = t.getRow();
         Tuple k = null;
         int len = row.length;

         if(len < hCount) {
            // is a total or grrandTotal tuple , add null to group set.
            if(groupMap.get(t) == null) {
               groupMap.put(t, new OrderedSet<>());
            }

            groupMap.get(t).add(null);
         }
         else {
            //is not total
            if(len > 0) {
               k = new Tuple(row, len - 1);
            }

            if(groupMap.get(k) == null) {
               groupMap.put(k, new OrderedSet<>());
            }

            groupMap.get(k).add(k != null ? row[len - 1] : null);
         }
      }

      return groupMap;
   }

   /**
    * Evaluate the condition.
    */
   private boolean evaluate(List<Tuple> rvec, List<Tuple> cvec, Map<PairN, Object> map) {
      if(condition == null) {
         return false;
      }

      boolean changed = false;
      // row/column detail tuple list
      Set<Tuple> rdvec = new HashSet<>();
      Set<Tuple> cdvec = new HashSet<>();
      Object[] values = new Object[dcol.length];
      PairN[] pairs = new PairN[dcol.length];

      // evaluate, and find row/column detail tuple
      for(Tuple rtuple : rvec) {
         if(rtuple.size() != rowh.length) {
            continue;
         }

         for(Tuple ctuple : cvec) {
            if(ctuple.size() != colh.length) {
               continue;
            }

            for(int k = 0; k < dcol.length; k++) {
               PairN pair = new PairN(rtuple, ctuple, k);
               pairs[k] = pair;
               Object obj = map.get(pair);
               values[k] = null;

               if(obj instanceof Formula) {
                  values[k] = ((Formula) obj).getResult();
               }
               else {
                  values[k] = obj;
               }
            }

            boolean eval = condition.evaluate(values);

            if(eval) {
               rdvec.add(rtuple);
               cdvec.add(ctuple);
            }
            else {
               changed = true;

               for(PairN pair : pairs) {
                  map.remove(pair);
                  rnumMap.remove(pair);
               }
            }
         }
      }

      if(changed) {
         shrink(rvec, rdvec, rowh.length);
         shrink(cvec, cdvec, colh.length);
      }

      return changed;
   }

   private void shrink(List<Tuple> vec, Set<Tuple> dvec, int len) {
      for(int i = vec.size() - 1; i >= 0; i--) {
         Tuple tuple = vec.get(i);

         if(dvec.contains(tuple)) {
            continue;
         }

         boolean inUse = false;

         if(tuple.size() < len) {

            for(Tuple dtuple : dvec) {
               if(isTupleDetail(tuple, dtuple)) {
                  inUse = true;
                  break;
               }
            }
         }

         if(!inUse) {
            vec.remove(i);
         }
      }
   }

   /**
    * Remove group totals where there is a single null value in the group.
    */
   private void removeNullTotals(List<Tuple> rvec, boolean totalTop) {
      for(int i = 0; i < rvec.size(); i++) {
         Tuple tuple = rvec.get(i);

         if(totalTop) {
            if(i < rvec.size() - 1) {
               Tuple next = rvec.get(i + 1);

               if(tuple.size() < next.size()) {
                  if(isNullGroup(rvec, i, 1, next.size())) {
                     rvec.remove(i);
                     i--;
                  }
               }
            }
         }
         else {
            if(i > 0) {
               Tuple next = rvec.get(i - 1);

               if(tuple.size() < next.size()) {
                  if(isNullGroup(rvec, i, -1, next.size())) {
                     rvec.remove(i);
                     i--;
                  }
               }
            }
         }
      }
   }

   /**
    * Check if the group consists only of null values.
    */
   private boolean isNullGroup(List<Tuple> rvec, int curr, int inc, int size) {
      int cnt = 0;
      int first = curr + inc;
      Tuple tuple0 = rvec.get(curr);

      // @ankitmathur For bug1413916802474, If the current tuple is the
      // "Grand Total" tuple, in no circumstance should the size of the
      // Column list decrease due to the logic in this method. The purpose
      // of this method is to remove groups based on null values
      // (e.g from Drill Events). Based on other areas of code in the class,
      // comparing the tuple to private variable "emptyTuple" is the most
      // reliable way to determine if the Tuple object is the "Grand Total" tuple.
      if(emptyTuple.equals(tuple0)) {
         return false;
      }

      group_loop:
      for(int i = first; i >= 0 && i < rvec.size(); i += inc) {
         Tuple tuple = rvec.get(i);

         if(tuple.size() != size) {
            int minSize = Math.min(tuple0.size(), tuple.size());

            if(tuple.size() < size && !"".equals(tuple.getRow()[tuple.size() - 1]) &&
               Tool.equals(tuple.getRow()[minSize - 1], tuple0.getRow()[minSize - 1]))
            {
               return false;
            }

            break;
         }

         if(size == tuple.size() && tuple.getRow()[size - 1] != null &&
            !"".equals(tuple.getRow()[size - 1]) ||
            size > tuple0.size() && !"".equals(tuple.getRow()[tuple0.size()]))
         {
            return false;
         }

         for(int k = 0; k < tuple0.size() && k < size - 1; k++) {
            if(!Tool.equals(tuple.getRow()[k], tuple0.getRow()[k])) {
               break group_loop;
            }
         }

         cnt++;
      }

      return cnt <= 1;
   }

   /**
    * Generate tuples.
    */
   private void genTuples(Set<Tuple> rset_, Set<Tuple> cset_, Map<PairN, Object> totalMap,
                          Map<PairN, Object> map, boolean needTotal)
   {
      // create crosstab data
      // for each table row except header row
      for(int i = table.getHeaderRowCount(); table.moreRows(i); i++) {
         boolean dcExtendDataRow = isDcExtendDataRow(table, i);

         // for each data column
         for(int j = 0; j < dcol.length; j++) {
            // prepare row/column group total & detailed total pair
            PairN[][] prc = new PairN[ccount][rcount];

            for(int ic = 0; ic < ccount; ic++) {
               int[] arc = new int[ic + 1];
               System.arraycopy(colh, 0, arc, 0, arc.length);
               Tuple ctuple = new Tuple(this, i, arc, COL);

               for(int ir = 0; ir < rcount; ir++) {
                  int[] arr = new int[ir + 1];
                  System.arraycopy(rowh, 0, arr, 0, arr.length);
                  prc[ic][ir] = new PairN(new Tuple(this, i, arr, ROW), ctuple, j);
               }
            }

            // prepare pure row detailed and group grandtotal pairs
            PairN[] prtotal = new PairN[rcount];

            for(int ir = 0; ir < rcount; ir++) {
               int[] arr = new int[ir + 1];
               System.arraycopy(rowh, 0, arr, 0, arr.length);
               prtotal[ir] = new PairN(new Tuple(this, i, arr, ROW), emptyTuple, j);
            }

            // prepare pure column detailed and group grandtotal pairs
            PairN[] pctotal = new PairN[ccount];

            for(int ic = 0; ic < ccount; ic++) {
               int[] arc = new int[ic + 1];
               System.arraycopy(colh, 0, arc, 0, arc.length);
               pctotal[ic] = new PairN(emptyTuple, new Tuple(this, i, arc, COL), j);
            }

            PairN[] prsubtotal = new PairN[2];

            // only prepare detailed sub-total and sub-grandtotal,
            // if merged, no sub-total or sub-grandtotal pair exists
            if(!isSuppressColumnSubtotal() && ccount > 1) {
               int[] arr = new int[ccount];

               for(int k = 0; k < arr.length; k++) {
                  arr[k] = (k == 0) ? -1 : colh[k];
               }

               Tuple ctuple = new Tuple(this, i, arr, COL);
               // detailed sub-total
               prsubtotal[0] = new PairN(new Tuple(this, i, rowh, ROW), ctuple, j);
               // detailed sub-grandtotal
               prsubtotal[1] = new PairN(emptyTuple, ctuple, j);
            }

            PairN[] pcsubtotal = new PairN[2];

            // only prepare detailed sub-total and sub-grandtotal,
            // if merged, no sub-total or sub-grandtotal pair exists
            if(!isSuppressRowSubtotal() && rcount > 1) {
               int[] arr = new int[rcount];

               for(int k = 0; k < arr.length; k++) {
                  arr[k] = (k == 0) ? -1 : rowh[k];
               }

               Tuple rtuple = new Tuple(this, i, arr, ROW);
               // detailed sub-total
               pcsubtotal[0] = new PairN(rtuple, new Tuple(this, i, colh, COL), j);
               // detailed sub-grandtotal
               pcsubtotal[1] = new PairN(rtuple, emptyTuple, j);
            }

            // prepare grandtotal pair
            final PairN ptotal = new PairN(emptyTuple, emptyTuple, j);

            // current data column contains no summary formula, therefore,
            // we needn't consider percentage and topn
            if(sum == null || j >= sum.length || sum[j] == null) {
               Object d = table.getObject(i, dcol[j]);

               for(int ic = 0; ic < prc.length; ic++) {
                  for(int ir = 0; ir < prc[ic].length; ir++) {
                     if(prc[ic][ir] != null && !isSuppressColumnGroupTotal(ic) &&
                        !isSuppressRowGroupTotal(ir))
                     {
                        // put pair->value in map
                        map.put(prc[ic][ir], d);
                        // insert/update row & column tuple in rset & cset
                        insertPair(rset_, cset_, prc[ic][ir]);
                     }
                  }
               }

               for(int ic = 0; ic < prtotal.length; ic++) {
                  if(prtotal[ic] != null && !isSuppressColumnGrandTotal() &&
                     !isSuppressRowGroupTotal(ic))
                  {
                     // put pair->value in map
                     map.put(prtotal[ic], d);
                     // insert/update row & column tuple in rset & cset
                     insertPair(rset_, cset_, prtotal[ic]);
                  }
               }

               for(int ic = 0; ic < pctotal.length; ic++) {
                  if(pctotal[ic] != null && !isSuppressRowGrandTotal() &&
                     !isSuppressColumnGroupTotal(ic))
                  {
                     // put pair->value in map
                     map.put(pctotal[ic], d);
                     // insert/update row & column tuple in rset & cset
                     insertPair(rset_, cset_, pctotal[ic]);
                  }
               }

               for(PairN pair : prsubtotal) {
                  if(pair != null && !isSuppressColumnSubtotal()) {
                     // put pair->value in map
                     map.put(pair, d);
                     // insert/update row & column tuple in rset & cset
                     insertPair(rset_, cset_, pair);
                  }
               }

               for(PairN pair : pcsubtotal) {
                  if(pair != null && !isSuppressRowSubtotal()) {
                     // put pair->value in map
                     map.put(pair, d);
                     // insert/update row & column tuple in rset & cset
                     insertPair(rset_, cset_, pair);
                  }
               }

               if(!isSuppressColumnGrandTotal() && !isSuppressRowGrandTotal()) {
                  // put pair->value in map
                  map.put(ptotal, d);
                  // insert/update row & column tuple in rset & cset
                  insertPair(rset_, cset_, ptotal);
               }
            }
            // current data column contains summary formula,
            // therefore, we should consider percentage and topn
            else {
               Object func;

               for(int ic = 0; ic < prc.length; ic++) {
                  for(int ir = 0; ir < prc[ic].length; ir++) {
                     if(prc[ic][ir] != null) {
                        boolean dcHiddenTuplePair = isDcHiddenTuplePair(prc[ic][ir]);

                        // @by billh, percentage direction might be
                        // group/grandtotal, and topn calculation also needs
                        // them, so we always put all of the total values into
                        // total map for percentage and topn calculation, but
                        // we can optimize the operation later
                        // put pair->formula in total map
                        if(needTotal) {
                           func = putMap(totalMap, prc[ic][ir], j);

                           if(!dcHiddenTuplePair || !dcExtendDataRow) {
                              addValue((Formula) func, table, i, dcol[j]);
                           }
                        }

                        if(!isSuppressColumnGroupTotal(ic) && !isSuppressRowGroupTotal(ir)) {
                           // put pair->formula in map
                           func = putMap(map, prc[ic][ir], j);

                           if(!dcHiddenTuplePair|| !dcExtendDataRow) {
                              addValue((Formula) func, table, i, dcol[j]);
                           }

                           // insert/update row & column tuple in rset & cset
                           insertPair(rset_, cset_, prc[ic][ir]);
                           setRowNumbers(prc[ic][ir], i);
                        }
                     }
                  }
               }

               for(int ic = 0; ic < prtotal.length; ic++) {
                  if(prtotal[ic] != null) {
                     boolean dcHiddenTuplePair = isDcHiddenTuplePair(prtotal[ic]);

                     // put pair->formula in total map
                     if(needTotal) {
                        func = putMap(totalMap, prtotal[ic], j);

                        if(!dcHiddenTuplePair || !dcExtendDataRow) {
                           addValue((Formula) func, table, i, dcol[j]);
                        }
                     }

                     if(!isSuppressColumnGrandTotal() && !isSuppressRowGroupTotal(ic)) {
                        // put pair->formula in map
                        func = putMap(map, prtotal[ic], j);

                        if(!dcHiddenTuplePair || !dcExtendDataRow) {
                           addValue((Formula) func, table, i, dcol[j]);
                        }

                        // insert/update row & column tuple in rset & cset
                        insertPair(rset_, cset_, prtotal[ic]);
                     }
                  }
               }

               for(int ic = 0; ic < pctotal.length; ic++) {
                  if(pctotal[ic] != null) {
                     boolean dcHiddenTuplePair = isDcHiddenTuplePair(pctotal[ic]);

                     // put pair->formula in total map
                     if(needTotal) {
                        func = putMap(totalMap, pctotal[ic], j);

                        if(!dcHiddenTuplePair || !dcExtendDataRow) {
                           addValue((Formula) func, table, i, dcol[j]);
                        }
                     }

                     if(!isSuppressRowGrandTotal() && !isSuppressColumnGroupTotal(ic)) {
                        // put pair->formula in map
                        func = putMap(map, pctotal[ic], j);

                        if(!dcHiddenTuplePair || !dcExtendDataRow) {
                           addValue((Formula) func, table, i, dcol[j]);
                        }

                        // insert/update row & column tuple in rset & cset
                        insertPair(rset_, cset_, pctotal[ic]);
                     }
                  }
               }

               for(PairN pair : prsubtotal) {
                  if(pair != null) {
                     // put pair->formula in total map
                     if(needTotal) {
                        func = putMap(totalMap, pair, j);
                        addValue((Formula) func, table, i, dcol[j]);
                     }

                     if(!isSuppressColumnSubtotal()) {
                        // put pair->formula in map
                        func = putMap(map, pair, j);
                        addValue((Formula) func, table, i, dcol[j]);
                        // insert/update row & column tuple in rset & cset
                        insertPair(rset_, cset_, pair);
                     }
                  }
               }

               for(PairN pair : pcsubtotal) {
                  if(pair != null) {
                     // put pair->formula in total map
                     if(needTotal) {
                        func = putMap(totalMap, pair, j);
                        addValue((Formula) func, table, i, dcol[j]);
                     }

                     if(!isSuppressRowSubtotal()) {
                        // put pair->formula in map
                        func = putMap(map, pair, j);
                        addValue((Formula) func, table, i, dcol[j]);
                        // insert/update row & column tuple in rset & cset
                        insertPair(rset_, cset_, pair);
                     }
                  }
               }

               // put pair->formula in total map
               if(needTotal) {
                  func = putMap(totalMap, ptotal, j);
                  addValue((Formula) func, table, i, dcol[j]);
               }

               if(!isSuppressColumnGrandTotal() && !isSuppressRowGrandTotal()) {
                  // put pair->formula in map
                  func = putMap(map, ptotal, j);

                  if(!isDcExtendDataRow(table, i)) {
                     addValue((Formula) func, table, i, dcol[j]);
                  }

                  // insert/update row & column tuple in rset & cset
                  insertPair(rset_, cset_, ptotal);
               }
            }
         }
      }
   }

   /**
    * Check if need percentage.
    */
   private boolean shouldPercent() {
      int perby = getPercentageDirection();

      if(perby != StyleConstants.PERCENTAGE_BY_COL && perby != StyleConstants.PERCENTAGE_BY_ROW) {
         return false;
      }

      PercentageFormula mapF;
      boolean hasPercent = false;

      if(sum != null) {
         for(Formula f : sum) {
            if(f instanceof PercentageFormula) {
               mapF = (PercentageFormula) f;
               // the directon of percentage
               int perType = mapF.getPercentageType();

               if(perType == StyleConstants.PERCENTAGE_OF_GROUP ||
                  perType == StyleConstants.PERCENTAGE_OF_GRANDTOTAL)
               {
                  hasPercent = true;
                  break;
               }
            }
         }
      }

      return hasPercent;
   }

   /**
    * Apply percentage.
    */
   private void applyPercentage(Map<PairN, Object> totalMap, Map<PairN, Object> map) {
      PercentageFormula mapF;
      int perBy = this.getPercentageDirection();

      // calculate percentages if any
      for(PairN mapPair : map.keySet()) {
         Object mapObj = map.get(mapPair);

         if(mapObj instanceof PercentageFormula) {
            mapF = (PercentageFormula) mapObj;

            PercentageFormula mapPerF = (PercentageFormula) mapObj;
            Tuple rowTuple = (Tuple) mapPair.getValue1();
            Tuple colTuple = (Tuple) mapPair.getValue2();
            int dataInt = mapPair.getNum();

            if((rowTuple == null) || (colTuple == null)) {
               continue;
            }

            Object[] rowcols;

            // copy the info of tuple to array
            if(perBy == StyleConstants.PERCENTAGE_BY_COL) {
               rowcols = new Object[colTuple.size()];
               colTuple.copyInto(rowcols);
            }
            else if(perBy == StyleConstants.PERCENTAGE_BY_ROW) {
               rowcols = new Object[rowTuple.size()];
               rowTuple.copyInto(rowcols);
            }
            else {
               continue;
            }

            // the type of percentage
            int perType = mapPerF.getPercentageType();

            // deal with the array
            if(perType == StyleConstants.PERCENTAGE_OF_GROUP ||
               perType == StyleConstants.PERCENTAGE_OF_GRANDTOTAL)
            {
               rowcols = getPercentTuple(perType, rowcols);
            }
            else {
               continue;
            }

            // contruct new tuple
            if(perBy == StyleConstants.PERCENTAGE_BY_COL) {
               colTuple = new Tuple(rowcols);
            }
            else if(perBy == StyleConstants.PERCENTAGE_BY_ROW) {
               rowTuple = new Tuple(rowcols);
            }
            else {
               continue;
            }

            // the new pair
            PairN newPair = new PairN(rowTuple, colTuple, dataInt);
            // calculate data
            // map data is correct, totalMap data is not reset
            Object totalMapObj;

            if(map.containsKey(newPair)) {
               totalMapObj = map.get(newPair);
            }
            else {
               if(totalMap.containsKey(newPair)) {
                  totalMapObj = totalMap.get(newPair);
               }
               else {
                  genFormula(totalMap, newPair);
                  totalMapObj = totalMap.get(newPair);
               }
            }

            if(totalMapObj instanceof PercentageFormula) {
               PercentageFormula totalMapF = (PercentageFormula) totalMapObj;

               mapF.setTotal(totalMapF.getOriginalResult());
            }
            else if(totalMapObj instanceof Formula) {
               Formula totalMapF = (Formula) totalMapObj;

               mapF.setTotal(totalMapF.getResult());
            }
         }
      }
   }

   /**
    * Generate new pair formula.
    */
   private void genFormula(Map<PairN, Object> totalMap, PairN pair) {
      int idx = pair.getNum();
      Tuple rtuple = (Tuple) pair.getValue1();
      Tuple ctuple = (Tuple) pair.getValue2();

      // only recalculate other formulas
      if(!isTupleOthers(rtuple) && !isTupleOthers(ctuple)) {
         return;
      }

      if(idx < 0 || idx >= dcol.length || sum == null ||
         idx >= sum.length || sum[idx] == null)
      {
         return;
      }

      int dcol = this.dcol[idx];
      Formula formula = sum[idx];

      try {
         formula = (Formula) formula.clone();
      }
      catch(Exception ex) {
         // ignore it}
      }

      if(formula == null) {
         return;
      }

      totalMap.put(pair, formula);
      formula.reset();
      List<Tuple> rtuples = new ArrayList<>();
      genDetailTuples(rvec, rtuple, rtuples, rowh.length);
      List<Tuple> ctuples = new ArrayList<>();
      genDetailTuples(cvec, ctuple, ctuples, colh.length);

      for(Tuple rtuple0 : rtuples) {
         for(Tuple ctuple0 : ctuples) {
            int[] rows = this.getRowNumbers(new PairN(rtuple0, ctuple0, idx));

            if(rows != null) {
               for(int row : rows) {
                  this.addValue(formula, table, row, dcol);
               }
            }
         }
      }
   }

   /**
    * Generate data and spans.
    */
   private void populateSpanAndData(Map<PairN, Object> map, Map<PairN, Object> calcValsmap) {
      data = new Object[ccount + rvec.size() * rowinc][rcount + cvec.size() * colinc];

      // prepare row header
      Tuple prev = new Tuple();
      int lasti = ccount;
      int spans = rowinc; // number of summary cells in a row
      int[] spanArray = new int[rcount];
      int[] posArray = new int[rcount];

      for(int i = 0; i < spanArray.length; i++) {
         spanArray[i] = spans;
         posArray[i] = lasti;
      }

      int ispan = ccount - spans;

      // setup row header spans
      for(Tuple tuple : rvec) {
         ispan += spans;

         final int csize = tuple.size();

         for(int j = 0; j < spans; j++) {
            tuple.copyInto(data[ispan + j]);

            // @by larryl, if the tuple is shorter than the full header, it is
            // a total row, we fill the empty cell with 'Total' instead of
            // just leave it empty, which is very confusing on what it means
            // @by yuz, here we span the first cell instead of fill next ones
            if(csize < rcount) {
               data[ispan + j][csize] = (csize == 0) ? getGrandTotalLabel() : TOTAL;
               Pair pair = getSpanPair(ispan + j, csize);
               totalPos.set((Integer) pair.value1, (Integer) pair.value2, Boolean.TRUE);
            }
         }

         Tuple tgrp = createGroupTuple(tuple);
         Tuple tpgrp = createGroupTuple(prev);
         int j = 0;

         if(!tpgrp.equals(emptyTuple) && !tuple.equals(tgrp)) {
            for(; j < tgrp.getRow().length; j++) {
               if(j < tpgrp.getRow().length &&
                  (tgrp.getRow()[j] == tpgrp.getRow()[j] ||
                     tgrp.getRow()[j] != null && tpgrp.getRow()[j] != null &&
                        tgrp.getRow()[j].equals(tpgrp.getRow()[j])))
               {
                  spanArray[j] += spans;
               }
               else {
                  break;
               }
            }
         }

         int delta = 0;

         // row group total row
         if((tgrp.getRow().length < tpgrp.getRow().length &&
            !tuple.equals(emptyTuple) && !isRowTotalOnTop()) ||
            (tpgrp.getRow().length < tgrp.getRow().length &&
               !prev.equals(emptyTuple) && isRowTotalOnTop()))
         {
            spanArray[j] += spans;
            delta = isRowTotalOnTop() ? 1 : 0;
         }

         int len = tgrp.getRow().length;
         int s = j + delta;

         for(int k = s; k == s || k <= len; k++) {
            if(spanArray[k] > 1) {
               Pair spair = getSpanPair(posArray[k], k);
               Dimension span = spanmap.get(spair);
               spanmap.put(spair, new Dimension(span == null ? 1 : span.width, spanArray[k]));
            }

            posArray[k] = ispan;
            spanArray[k] = spans;
         }

         // if multiple summary, make sure all row header is spanned for
         // at least the spans rows
         for(int k = 0; k < rcount; k++) {
            Pair pair = getSpanPair(ispan, k);

            if(k != 0 && k == csize) {
               //locate into total cell
               spanmap.put(pair, new Dimension(rcount - k, spans));
            }
            else if(spans > 1 && spanmap.get(pair) == null) {
               spanmap.put(pair, new Dimension(1, spans));
            }
         }

         prev = tuple;
      }

      // span the last row header
      for(int j = 0; j < rcount - 1; j++) {
         if(spanArray[j] > 1) {
            spanmap.put(getSpanPair(posArray[j], j), new Dimension(1, spanArray[j]));
         }
      }

      if(spans > 1) {
         spanmap.put(getSpanPair(ispan, rcount - 1), new Dimension(1, spans));
      }

      // @by larryl, setup grand total span
      if(!isSuppressRowGrandTotal()) {
         Dimension gs = new Dimension(rowh.length, spans);

         if(isRowTotalOnTop()) {
            spanmap.put(getSpanPair(colh.length, 0), gs);
         }
         else {
            spanmap.put(getSpanPair(ispan, 0), gs);
         }
      }

      // prepare col header
      prev = new Tuple();
      lasti = rcount;

      spans = colinc;
      spanArray = new int[ccount];
      posArray = new int[ccount];

      for(int i = 0; i < spanArray.length; i++) {
         spanArray[i] = spans;
         posArray[i] = lasti;
      }

      ispan = rcount - spans;

      // setup column header spans
      for(Tuple tuple : cvec) {
         ispan += spans;

         final int csize = tuple.size();

         for(int j = 0; j < spans; j++) {
            tuple.copyInto(data, ispan + j);

            // @by larryl, if the tuple is shorter than the full header, it is
            // a total column, we fill the empty cell with 'Total' instead of
            // just leave it empty, which is very confusing on what it means
            // @by yuz, here we span the first cell instead of fill next ones
            if(csize < ccount) {
               data[csize][ispan + j] = (csize == 0) ? getGrandTotalLabel() : TOTAL;
               Pair pair = getSpanPair(csize, ispan + j);
               totalPos.set((Integer) pair.value1, (Integer) pair.value2, Boolean.TRUE);
            }
         }

         Tuple tgrp = createGroupTuple(tuple);
         Tuple tpgrp = createGroupTuple(prev);
         int j = 0;

         if(!tpgrp.equals(emptyTuple) && !tuple.equals(tgrp)) {
            for(; j < tgrp.getRow().length; j++) {
               if(j < tpgrp.getRow().length &&
                  (tgrp.getRow()[j] == tpgrp.getRow()[j] ||
                     tgrp.getRow()[j] != null && tpgrp.getRow()[j] != null &&
                        tgrp.getRow()[j].equals(tpgrp.getRow()[j])))
               {
                  spanArray[j] += spans;
               }
               else {
                  break;
               }
            }
         }

         int delta = 0;

         // row group total row
         if((tgrp.getRow().length < tpgrp.getRow().length &&
            !tuple.equals(emptyTuple) && !isColumnTotalOnFirst()) ||
            (tpgrp.getRow().length < tgrp.getRow().length &&
               !prev.equals(emptyTuple) && isColumnTotalOnFirst()))
         {
            spanArray[j] += spans;
            delta = isColumnTotalOnFirst() ? 1 : 0;
         }

         int len = tgrp.getRow().length;
         int s = j + delta;

         for(int k = s; k == s || k <= len; k++) {
            if(spanArray[k] > 1) {
               Pair spair = getSpanPair(k, posArray[k]);
               Dimension span = spanmap.get(spair);
               spanmap.put(spair, new Dimension(spanArray[k], span == null ? 1 : span.height));
            }

            posArray[k] = ispan;
            spanArray[k] = spans;
         }

         // if summary side by side, make sure all column header is spanned for
         // at least the spans columns
         for(int k = 0; k < ccount; k++) {
            Pair pair = getSpanPair(k, ispan);

            if(k != 0 && k == csize) {
               //locate into total cell
               spanmap.put(pair, new Dimension(spans, ccount - k));
            }
            else if(spans > 1 && spanmap.get(pair) == null) {
               spanmap.put(pair, new Dimension(spans, 1));
            }
         }

         prev = tuple;
      }

      // span the last col header
      for(int j = 0; j < ccount - 1; j++) {
         if(spanArray[j] > 1) {
            spanmap.put(getSpanPair(j, posArray[j]), new Dimension(spanArray[j], 1));
         }
      }

      if(spans > 1) {
         spanmap.put(getSpanPair(ccount - 1, ispan), new Dimension(spans, 1));
      }

      // @by larryl, setup grand total span
      if(!isSuppressColumnGrandTotal()) {
         Dimension gs = new Dimension(spans, colh.length);

         if(!colTotalOnFirst) {
            spanmap.put(getSpanPair(0, ispan), gs);
         }
         else {
            spanmap.put(getSpanPair(0, rowh.length), gs);
         }
      }

      // @by larryl & billh, if there is no row/col header, we use the summary
      // column header as row/col header, otherwise row/col header would use
      // 'Total'. This is nicer and also required to make chart work
      if(rowh.length == 1 && rowh[0] == -1) {
         // no label header and side by side is true, use merged summary header
         if(sideBySide) {
            int ridx = (isSuppressRowGrandTotal() || !isRowTotalOnTop())
               ? ccount : ccount + 1;

            // @by billh, avoid no data case
            if(ridx < data.length) {
               StringBuilder label = new StringBuilder();

               for(int i = 0; i < dcol.length; i++) {
                  if(i > 0) {
                     label.append("/");
                  }

                  label.append(getMeasureHeader(i, false));
               }

               data[ridx][0] = label.toString();
               spanmap.remove(getSpanPair(ridx, 0));
            }
         }
         // no label header and side by side is false, use each summary header
         else {
            for(int i = 0; i < dcol.length; i++) {
               int ridx = (isSuppressRowGrandTotal() || !isRowTotalOnTop()) ?
                  ccount + i : ccount + i + dcol.length;

               // @by jung, add boundary check in case resultset size is zero
               if(dcol[i] >= 0 && ridx < data.length) {
                  data[ridx][0] = getMeasureHeader(i, false);
                  spanmap.remove(getSpanPair(ridx, 0));
               }
            }
         }
      }

      if(colh.length == 1 && colh[0] == -1) {
         // no label header and side by side is true, use each summary header
         if(sideBySide) {
            for(int i = 0; i < dcol.length; i++) {
               int cidx = (isSuppressColumnGrandTotal() || !isColumnTotalOnFirst())
                  ? rcount + i : rcount + i + dcol.length;

               if(dcol[i] >= 0 && cidx < data[0].length) {
                  data[0][cidx] = getMeasureHeader(i, false);
                  spanmap.remove(getSpanPair(0, cidx));
               }
            }
         }
         // no label header and side by side is false, use merged summary header
         else {
            int cidx = (isSuppressColumnGrandTotal() || !isColumnTotalOnFirst())
               ? rcount : rcount + 1;

            // @by billh, avoid no data case
            if(cidx < data[0].length) {
               StringBuilder label = new StringBuilder();

               for(int i = 0; i < dcol.length; i++) {
                  if(i > 0) {
                     label.append("/");
                  }

                  label.append(getMeasureHeader(i, true));
               }

               data[0][cidx] = label.toString();
               spanmap.remove(getSpanPair(0, cidx));
            }
         }
      }

      // keep row header's column header
      if(keepheader) {
         for(int i = 0; i < rcount; i++) {
            if(rowh[i] != -1) {
               data[0][i] = getRowColumnHeader(i);
            }

            spanmap.put(getSpanPair(0, i), new Dimension(1, ccount));
         }
      }
      /* @by larryl, forcing the corner cell to span makes individual cells
         not accessible using data path. It's better to let user control the
         cell with data path than always span it.
      else {
         spanmap.put(getSpanPair(0, 0), new Dimension(rcount, ccount));
      }
      */

      // populate data
      for(int i = 0; i < rvec.size(); i++) {
         Tuple rtuple = rvec.get(i);

         for(int j = 0; j < cvec.size(); j++) {
            Tuple ctuple = cvec.get(j);

            for(int k = 0; k < dcol.length; k++) {
               int row;
               int col;

               if(sideBySide) {
                  col = rcount + j * dcol.length + k;
                  row = ccount + i;
               }
               else {
                  row = ccount + i * dcol.length + k;
                  col = rcount + j;
               }

               data[row][col] = getMergedData(map, calcValsmap, k, rtuple, ctuple);

               if(data[row][col] == null && isFillBlankWithZero()) {
                  data[row][col] = (double) 0;
               }

               if(forCalc) {
                  valueMap.put(new PairN(rtuple, ctuple, k), data[row][col]);
               }
            }
         }
      }

      // add the summary title row to data if enabled
      addSummaryHeaders();
   }

   /**
    * Get measure header.
    */
   private Object getMeasureHeader(int index, boolean localize) {
      boolean mheader = measureHeaders != null && measureHeaders[index] != null;
      Object hdr = mheader ? measureHeaders[index] : table.getObject(0, dcol[index]);
      hdr = (hdr == null) ? "" : hdr.toString();

      // @by jasons feature1253819088758 localize the aggregate label
      if(localize) {
         Object lhdr = getHeader(hdr);

         if(lhdr != null) {
            lhdr = getHeader(lhdr);

            if(lhdr != null) {
               hdr = lhdr;
            }
         }
      }
      else {
         hdr = getHeader(hdr);
      }

      if(!mheader && duptimes != null && duptimes[index] > 0) {
         hdr = Util.getDupHeader(hdr, duptimes[index]);
      }

      return hdr;
   }

   /**
    * Get the row column's header.
    */
   public Object getRowColumnHeader(int idx) {
      Object header = null;

      if(idx < rowh.length && rowh[idx] != -1) {
         header = getHeader(rowh[idx], idx + 100);

         if(header != null) {
            // @by jasons feature1253819088758 localize the aggregate label
            Object hdr = getHeader(header);

            if(hdr != null) {
               header = hdr;
            }
         }
      }

      return header;
   }

   /**
    * Get tuple data for percentage.
    */
   private Object[] getPercentTuple(int perType, Object[] rowcols) {
      // deal with the array
      if(perType == StyleConstants.PERCENTAGE_OF_GROUP) {
         for(int i = rowcols.length - 1; i >= 0; i--) {
            if(rowcols[i] != null) {
               // @by larryl, support percentage inside a group
               if(rowcols.length > 1) {
                  Object[] narr = new Object[i];
                  System.arraycopy(rowcols, 0, narr, 0, narr.length);
                  rowcols = narr;

                  // if this is subtotal, should use the grand total
                  // as the base for percentage
                  if(rowcols.length == 0 || rowcols[0] == null) {
                     rowcols = new Object[] {};
                  }
               }
               // @by larry, if empty, use {} to match new Tuple()
               else {
                  rowcols = new Object[] {};
               }

               break;
            }
         }
      }
      else if(perType == StyleConstants.PERCENTAGE_OF_GRANDTOTAL) {
         rowcols = new Object[] {}; // match new Tuple()
      }

      return rowcols;
   }

   /**
    * Check if contains others.
    */
   public boolean isOthers() {
      return isOthers(rowtopns) || isOthers(coltopns);
   }

   private boolean isOthers(InnerTopNInfo[] topNs) {
      for(InnerTopNInfo topn : topNs) {
         if(topn == null || topn.n == 0 || topn.n == Integer.MAX_VALUE) {
            continue;
         }

         if(topn.others) {
            return true;
         }
      }

      return false;
   }

   /**
    * Keep the row index with the pair.
    */
   private void setRowNumbers(PairN pair, int rowIndex) {
      if(condition == null && topNAggregateComparator == null && (
         // no topn?
         !shouldRowTopN() && !shouldColTopN() ||
         // if has others, Other need to be calculated
         !isAggregateTopN() && !isOthers()))
      {
         return;
      }

      XIntList list = rnumMap.get(pair);
      boolean needTrim = false;

      if(list == null) {
         list = new XIntList();
         needTrim = true;
      }

      if(!list.contains(rowIndex)) {
         list.add(rowIndex);
      }

      if(needTrim) {
         list.trimToSize();
      }

      rnumMap.put(pair, list);
   }

   /**
    * Get the row numbers with the pair.
    */
   private int[] getRowNumbers(PairN cellPair) {
      XIntList list = rnumMap.get(cellPair);

      if(list != null) {
         return list.toArray();
      }

      Tuple rowTuple = (Tuple) cellPair.getValue1();
      Tuple colTuple = (Tuple) cellPair.getValue2();

      // only others needs recalculate row numbers
      if(!isTupleOthers(rowTuple) && !isTupleOthers(colTuple)) {
         return new int[0];
      }

      List<Tuple> rowTuples = null;
      List<Tuple> colTuples = null;

      if(isTupleOthers(rowTuple)) {
         rowTuples = ((MergedTuple) rowTuple).tuples;
      }

      if(isTupleOthers(colTuple)) {
         colTuples = ((MergedTuple) colTuple).tuples;
      }

      int[] nlist = new int[0];

      if(rowTuples != null && colTuples != null) {
         for(Tuple rtuple : rowTuples) {
            for(Tuple ctuple : colTuples) {
               PairN pair = new PairN(rtuple, ctuple, cellPair.num);
               XIntList tlist = rnumMap.get(pair);

               if(tlist != null) {
                  int[] arr = tlist.toArray();
                  int[] nlist2 = new int[nlist.length + arr.length];
                  System.arraycopy(nlist, 0, nlist2, 0, nlist.length);
                  System.arraycopy(arr, 0, nlist2, nlist.length, arr.length);
                  nlist = nlist2;
               }
            }
         }
      }
      else if(rowTuples != null) {
         for(Tuple tuple : rowTuples) {
            PairN pair = new PairN(tuple, colTuple, cellPair.num);
            XIntList tlist = rnumMap.get(pair);

            if(tlist != null) {
               int[] arr = tlist.toArray();
               int[] nlist2 = new int[nlist.length + arr.length];
               System.arraycopy(nlist, 0, nlist2, 0, nlist.length);
               System.arraycopy(arr, 0, nlist2, nlist.length, arr.length);
               nlist = nlist2;
            }
         }
      }
      else if(colTuples != null) {
         for(Tuple tuple : colTuples) {
            PairN pair = new PairN(rowTuple, tuple, cellPair.num);
            XIntList tlist = rnumMap.get(pair);

            if(tlist != null) {
               int[] arr = tlist.toArray();
               int[] nlist2 = new int[nlist.length + arr.length];
               System.arraycopy(nlist, 0, nlist2, 0, nlist.length);
               System.arraycopy(arr, 0, nlist2, nlist.length, arr.length);
               nlist = nlist2;
            }
         }
      }

      XIntList xlist = new XIntList(new int[nlist.length]);
      int[] xarr = xlist.getArray();
      System.arraycopy(nlist, 0, xarr, 0, nlist.length);
      rnumMap.put(cellPair, xlist);
      return nlist;
   }

   /**
    * Check if the specified tuple contains OTHERS value.
    */
   private boolean isTupleOthers(Tuple tuple) {
      return tuple instanceof MergedTuple;
   }

   /**
    * Reset grand total if needed.
    */
   private void resetGrandTotal(Map<PairN, Object> map) {
      if(!isAggregateTopN()) {
         if(isOthers()) {
            // reset all Others tuple
            for(int i = 0; i < dcol.length; i++) {
               resetGrandTotal(rvec, cvec, map, i, true);
            }
         }

         return;
      }

      for(int i = 0; i < dcol.length; i++) {
         resetGrandTotal(rvec, cvec, map, i, false);
      }
   }

   /**
    * Get none detail tuples.
    */
   private List<Tuple> getNoneDetailTuples(List<Tuple> vec, int length) {
      List<Tuple> nds = new ArrayList<>();

      for(Tuple tuple : vec) {
         int len = tuple.getRow().length;

         if(len < length) {
            nds.add(tuple);
         }
      }

      return nds;
   }

   /**
    * Check if a none-detail tuple belongs to the specified detail tuple.
    */
   private boolean isTupleDetail(Tuple ndTuple, Tuple dTuple) {
      Object[] row1 = ndTuple.getRow();
      Object[] row2 = dTuple.getRow();

      if(row1.length > 0 && row2.length > row1.length) {
         for(int i = 0; i < row1.length; i++) {
            if(!Tool.equals(row1[i], row2[i])) {
               return false;
            }
         }
      }

      return true;
   }

   /**
    * Reset grand total if needed.
    */
   private void resetGrandTotal(List<Tuple> rvec, List<Tuple> cvec,
                                Map<PairN, Object> map, int j, boolean othersOnly)
   {
      List<Tuple> rv = new ArrayList<>(rvec);
      List<Tuple> cv = new ArrayList<>(cvec);

      // do not reset grandtotal if no formula
      if(sum == null || j >= sum.length || sum[j] == null) {
         return;
      }

      // none detail tuples
      List<Tuple> ndrv = getNoneDetailTuples(rvec, rowh.length);
      List<Tuple> ndcv = getNoneDetailTuples(cvec, colh.length);

      // for others tuple
      for(Tuple colTuple : cv) {
         for(Tuple rowTuple : rv) {
            PairN pair = new PairN(rowTuple, colTuple, j);
            Formula formula = (Formula) map.get(pair);

            if((isTupleOthers(colTuple) || isTupleOthers(rowTuple)) &&
               !(rowTuple.equals(new Tuple()) || colTuple.equals(new Tuple())))
            {
               map.put(pair, getMergedFormula(j, rowTuple, colTuple));
            }
            else if(formula == null) {
               try {
                  formula = (Formula) sum[j].clone();
               }
               catch(Exception e) {
                  LOG.warn("Failed reset others grand total formula", e);
               }

               map.put(pair, formula);
            }
         }
      }

      // remove none-detail tuple
      rv.removeAll(ndrv);
      cv.removeAll(ndcv);

      // reset none detail row tuple topn total
      for(Tuple ndrTuple : ndrv) {
         for(Tuple colTuple : cv) {
            if(othersOnly && !isTupleOthers(colTuple)) {
               continue;
            }

            PairN pair = new PairN(ndrTuple, colTuple, j);

            // reset this formula
            Formula formula = (Formula) map.get(pair);

            if(formula != null) {
               formula.reset();

               for(Tuple rowTuple : rv) {
                  PairN cellPair = new PairN(rowTuple, colTuple, j);
                  int[] list = new int[0];

                  if(isTupleDetail(ndrTuple, rowTuple)) {
                     list = getRowNumbers(cellPair);
                  }

                  for(int k : list) {
                     if(!isDcHiddenTuplePair(ndrTuple, colTuple) || !isDcExtendDataRow(table, k)) {
                        addValue(formula, table, k, dcol[j]);
                     }
                  }
               }
            }
         }
      }

      // reset none detail col tuple topn total
      for(Tuple ndcTuple : ndcv) {
         for(Tuple rowTuple : rv) {
            if(othersOnly && !isTupleOthers(rowTuple)) {
               continue;
            }

            PairN pair = new PairN(rowTuple, ndcTuple, j);

            // reset this formula
            Formula formula = (Formula) map.get(pair);

            if(formula != null) {
               formula.reset();

               for(Tuple colTuple : cv) {
                  PairN cellPair = new PairN(rowTuple, colTuple, j);
                  int[] list = new int[0];

                  if(isTupleDetail(ndcTuple, colTuple)) {
                     list = getRowNumbers(cellPair);
                  }

                  for(int k : list) {
                     if(!isDcHiddenTuplePair(rowTuple, ndcTuple) || !isDcExtendDataRow(table, k)) {
                        addValue(formula, table, k, dcol[j]);
                     }
                  }
               }
            }
         }
      }

      if(othersOnly) {
         return;
      }

      // reset none detail tuple grand total
      for(int r = 0; r < ndrv.size(); r++) {
         Tuple ndrTuple = ndrv.get(r);

         for(int c = 0; c < ndcv.size(); c++) {
            Tuple ndcTuple = ndcv.get(c);

            PairN tpair = new PairN(ndrTuple, ndcTuple, j);
            Formula tformula = (Formula) map.get(tpair);

            if(tformula == null) {
               try {
                  tformula = (Formula) sum[j].clone();
                  map.put(tpair, tformula);
               }
               catch(Exception e) {
                  LOG.warn("Failed to reset detail row grand total formula", e);
               }
            }

            Objects.requireNonNull(tformula).reset();
            rv = new ArrayList<>(rvec);
            cv = new ArrayList<>(cvec);

            // remove none-detail tuple
            rv.removeAll(ndrv);
            cv.removeAll(ndcv);

            for(Tuple ctuple : cv) {
               for(Tuple rtuple : rv) {
                  PairN pair = new PairN(rtuple, ctuple, j);
                  int[] list = new int[0];

                  if(isTupleDetail(ndrTuple, rtuple) &&
                     isTupleDetail(ndcTuple, ctuple))
                  {
                     list = getRowNumbers(pair);
                  }

                  for(int k : list) {
                     if(!isDcHiddenTuplePair(ndrTuple, ndcTuple) || !isDcExtendDataRow(table, k)) {
                        addValue(tformula, table, k, dcol[j]);
                     }
                  }
               }
            }
         }
      }
   }

   private boolean isDcHiddenTuplePair(PairN pair) {
      if(pair == null || !(pair.getValue1() instanceof Tuple) ||
         !(pair.getValue2() instanceof Tuple))
      {
         return false;
      }

      return isDcHiddenTuplePair((Tuple) pair.getValue1(), (Tuple) pair.getValue2());
   }

   private boolean isDcHiddenTuplePair(Tuple rowTuple, Tuple colTuple) {
      if(dcRefInRow == null) {
         return false;
      }

      if(dcRefInRow) {
         return Tool.equals(emptyTuple, rowTuple) || rowTuple.size() <= dcDataRefHeaderIndex;
      }
      else {
         return Tool.equals(emptyTuple, colTuple) || colTuple.size() <= dcDataRefHeaderIndex;
      }
   }

   /**
    * Generate detail tuples.
    */
   private void genDetailTuples(List<Tuple> vec, Tuple tuple, List<Tuple> tuples, int len) {
      if(tuple instanceof MergedTuple) {
         MergedTuple merged = (MergedTuple) tuple;

         for(Tuple obj : merged.tuples) {
            genDetailTuples(vec, obj, tuples, len);
         }

         return;
      }
      else if(tuple.size() < len) {
         for(Object obj : vec) {
            Tuple t = (Tuple) obj;

            if(t.size() == len && this.isTupleDetail(tuple, t)) {
               tuples.add(t);
            }
         }

         return;
      }

      tuples.add(tuple);
   }

   /**
    * Get the merged data.
    */
   private Object getMergedData(Map<PairN, Object> map, Map<PairN, Object> calcValsmap, int k,
                                Tuple rtuple, Tuple ctuple)
   {
      // process normal tuple
      PairN pair = new PairN(rtuple, ctuple, k);

      if(calcValsmap != null && calcValsmap.containsKey(pair) && (calcTotal ||
         (!isTotalTuple(rtuple, true) && !isTotalTuple(ctuple, false))))
      {
         Object value = calcValsmap.get(pair);

         return value == CalcColumn.INVALID ? null : calcValsmap.get(pair);
      }

      Object obj = map.get(pair);
      return getMergedData0(map, k, rtuple, ctuple, obj, false);
   }

   /**
    * Whether the tuple is a total
    * @param tuple
    * @param row
    * @return
    */
   private boolean isTotalTuple(Tuple tuple, boolean row) {
      int headerCount = row ? getRowHeaderCount() : getColHeaderCount();

      return tuple.size() < headerCount;
   }

   /**
    * Get the merged data.
    */
   private Object getMergedData0(Map<PairN, Object> map, int k, Tuple rtuple, Tuple ctuple,
                                 Object obj, boolean readOnly)
   {
      if(obj instanceof Formula) {
         if(!readOnly && obj instanceof CalcFieldFormula && ((CalcFieldFormula) obj).getPercentageType() != 0) {
            ((CalcFieldFormula) obj).clearResult();
         }

         obj = ((Formula) obj).getResult();
      }

      if(obj != null) {
         return obj;
      }

      List<Tuple> rtuples = null;
      List<Tuple> ctuples = null;

      if(rtuple instanceof MergedTuple) {
         MergedTuple ttuple = (MergedTuple) rtuple;
         rtuples = ttuple.tuples;
      }

      if(ctuple instanceof MergedTuple) {
         MergedTuple ttuple = (MergedTuple) ctuple;
         ctuples = ttuple.tuples;
      }

      // process merged tuple
      List<Object> list = new ArrayList<>();

      if(rtuples != null && ctuples != null) {
         for(Tuple rtuple2 : rtuples) {
            for(Tuple ctuple2 : ctuples) {
               list.add(map.get(new PairN(rtuple2, ctuple2, k)));
            }
         }
      }
      else if(rtuples != null) {
         for(Tuple tuple : rtuples) {
            list.add(map.get(new PairN(tuple, ctuple, k)));
         }
      }
      else if(ctuples != null) {
         for(Tuple tuple : ctuples) {
            list.add(map.get(new PairN(rtuple, tuple, k)));
         }
      }

      Formula formula = getMergedFormula(k);

      for(Object o : list) {
         if(o instanceof Formula) {
            formula.addValue(((Formula) o).getResult());
         }
      }

      return formula.getResult();
   }

   /**
    * Get merged cell formula function.
    */
   private Formula getMergedFormula(int k) {
      Formula formula = null;

      if(sum[k] != null) {
         String fname = sum[k].getName();
         AggregateFormula agg = AggregateFormula.getFormula(fname);

         if(agg != null) {
            // find parent formula
            agg = agg.getParentFormula();

            if(agg != null) {
               String mformulas = "inetsoft.report.filter." + agg.getFormulaName() + "Formula";
               formula = formulas.get(mformulas);

               try {
                  if(formula == null) {
                     formula = (Formula) Class.forName(mformulas).newInstance();
                     formulas.put(mformulas, formula);
                  }
               }
               catch(Exception ex) {
                  // ignore it
               }
            }
         }
      }

      return formula == null ? new SumFormula() : formula;
   }

   /**
    * Get the merged cell formula.
    */
   private Formula getMergedFormula(int k, Tuple rtuple, Tuple ctuple) {
      List<Tuple> rtuples = null;
      List<Tuple> ctuples = null;

      if(rtuple instanceof MergedTuple) {
         MergedTuple ttuple = (MergedTuple) rtuple;
         rtuples = ttuple.tuples;
      }

      if(ctuple instanceof MergedTuple) {
         MergedTuple ttuple = (MergedTuple) ctuple;
         ctuples = ttuple.tuples;
      }

      // process merged tuple
      List<PairN> list = new ArrayList<>();

      if(rtuples != null && ctuples != null) {
         for(Tuple rtuple2 : rtuples) {
            for(Tuple ctuple2 : ctuples) {
               list.add(new PairN(rtuple2, ctuple2, k));
            }
         }
      }
      else if(rtuples != null) {
         for(Tuple tuple : rtuples) {
            list.add(new PairN(tuple, ctuple, k));
         }
      }
      else if(ctuples != null) {
         for(Tuple tuple : ctuples) {
            list.add(new PairN(rtuple, tuple, k));
         }
      }

      Formula formula = null;

      try {
         formula = (Formula) sum[k].clone();
      }
      catch(Exception e) {
         LOG.warn("Failed to reset merged grand total formula", e);
      }

      for(PairN cellPair : list) {
         int[] rows = getRowNumbers(cellPair);

         for(int row : rows) {
            addValue(formula, table, row, dcol[k]);
         }
      }

      return formula;
   }

   /**
    * Add summary title row or column if isShowSummaryHeaders is enabled.
    */
   private void addSummaryHeaders() {
      if(!summaryHeaders || (hrowmore == 0 && hcolmore == 0)) {
         return;
      }

      Object[] titles = getHeaders();

      for(int i = 0; i < titles.length; i++) {
         if(titles[i] != null) {
            String hdr = titles[i].toString();
            titles[i] = getHeader(hdr);
         }
      }

      if(sideBySide) {
         // side by side summary cells, add summary title row
         Object[] row = new Object[data[0].length];

         for(int i = rcount, idx = 0; i < row.length; i++, idx++) {
            row[i] = titles[idx % titles.length];
         }

         data = (Object[][]) insertTo(row, ccount, data, true);
      }
      else {
         for(int i = 0; i < ccount; i++) {
            data[i] = insertTo(null, rcount, data[i], false);
         }

         // vertical summary cells, add summary title column
         for(int i = ccount, idx = 0; i < data.length; i++, idx++) {
            /**
             * Todo
             * if(is aggregate titles[idx % titles.length]) {
             *    use calc full name.
             * }
             */
            data[i] = insertTo(titles[idx % titles.length], rcount, data[i],
                               false);
         }
      }
   }

   /**
    * Insert a value at the specified position into the array.
    * @param dim2 true to create a two dimensional array.
    */
   private Object[] insertTo(Object val, int idx, Object[] arr, boolean dim2) {
      Object[] narr = dim2
         ? new Object[arr.length + 1][]
         : new Object[arr.length + 1];

      System.arraycopy(arr, 0, narr, 0, idx);
      System.arraycopy(arr, idx, narr, idx + 1, arr.length - idx);
      narr[idx] = val;

      return narr;
   }

   /**
    * Insert a pair into row set and column set.
    */
   private void insertPair(Set<Tuple> r, Set<Tuple> c, Pair p) {
      r.add((Tuple) p.value1);
      c.add((Tuple) p.value2);

      if(Util.getRuntimeMaxRows() > 0 && r.size() > Util.getRuntimeMaxRows()) {
         appliedMaxRows = Util.getRuntimeMaxRows();
         throw new MaxRowsException();
      }
   }

   /**
    * Create a group tuple from a tuple.
    */
   private Tuple createGroupTuple(Tuple t) {
      Object[] grp = new Object[Math.max(0, t.size() - 1)];
      t.copyInto(grp, grp.length);
      return new Tuple(grp);
   }

   /**
    * Get a pair to use in the span map. The row and column index are the
    * index before the summary title row/column is added. The pair reflects
    * the adjustment that accounts for the insertion of the title row/column.
    */
   private Pair getSpanPair(int r, int c) {
      if(r >= ccount) {
         r += hrowmore;
      }

      if(c >= rcount) {
         c += hcolmore;
      }

      return new Pair(r, c);
   }

   /**
    * Add a formula for the pair if does not exist.
    */
   private Object putMap(Map<PairN, Object> map, PairN p, int sumIdx) {
      Object v = map.get(p);

      if(v == null && sum[sumIdx] != null) {
         try {
            Formula form = (Formula) sum[sumIdx].clone();
            form.reset();
            map.put(p, v = form);
         }
         catch(Exception e) {
            LOG.warn("Failed to add formula", e);
         }
      }

      return v;
   }

   private boolean isDcExtendDataRow(TableLens table, int row) {
      if(dcDataRefColIndex >= 0 && dcStartDate != null ) {
         Object dcColValue = table.getObject(row, dcDataRefColIndex);

         return dcColValue instanceof Date && ((Date) dcColValue).compareTo(dcStartDate) < 0;
      }

      return false;
   }

   /**
    * Add a value to a formula. Handles formulae that require a second column.
    */
   private void addValue(Formula v, TableLens table, int row, int col) {
      if(v instanceof Formula2) {
         int[] cols = ((Formula2) v).getSecondaryColumns();
         Object[] data = new Object[cols.length + 1];
         data[0] = table.getObject(row, col);

         for(int i = 0; i < cols.length; i++) {
            data[i + 1] = table.getObject(row, cols[i]);
         }

         v.addValue(data);
      }
      else {
         fagents[col].add(v, table, row, col);
      }
   }

   /**
    * Sort a list.
    */
   @SuppressWarnings("unchecked")
   protected void sort(List<Tuple> source, inetsoft.report.Comparer comp) {
      source.sort(comp);
   }

   /**
    * Sort row tuples.
    */
   private List<Tuple> sortOnHeader(List<Tuple> src, Map<PairN, Object> totalmap,
                                      Map<PairN, Object> map)
   {
      int index = 0;
      List<Tuple> dest = new ArrayList<>();

      // for grandtotal/sub-total tuples, just copy them
      for(; index < src.size(); index++) {
         Tuple t = src.get(index);

         if(t.getRow().length == 0 || t.getRow()[0] == null) {
            dest.add(t);
         }
         else {
            break;
         }
      }

      Tuple lasttuple = null;
      int count = rcount;
      Section root = new Section2();
      Section2[] currents = new Section2[count == 0 ? 0 : count - 1];
      int[] grouptuples = new int[count == 0 ? 0 : count - 1];

      Arrays.fill(grouptuples, -1);

      boolean first = rowTotalOnTop;

      for(; index < src.size(); index++) {
         Tuple tuple = src.get(index);

         // found grandtotal/sub-total tuple
         if(tuple.getRow().length == 0 || tuple.getRow()[0] == null) {
            break;
         }

         // is group total tuple
         if(tuple.size() < count) {
            if(first) {
               grouptuples[tuple.size() - 1] = index;
            }
            else {
               currents[tuple.size() - 1].tindex = index;
            }

            continue;
         }

         // process regular tuple
         int col = getGroupCol(lasttuple, tuple);

         // is group col
         if(col < currents.length) {
            for(int i = currents.length - 1; i >= col; i--) {
               if(currents[i] == null) {
                  break;
               }

               sortSection(currents[i]);

               if(i == 0) {
                  root.addSection(currents[i]);
               }
               else {
                  currents[i - 1].addSection(currents[i]);
               }
            }

            for(int i = col; i < currents.length; i++) {
               currents[i] = new Section2();
               currents[i].index = index;
               setSectionTotals(currents[i], i, tuple, totalmap, map);

               if(first && grouptuples[i] != -1) {
                  currents[i].tindex = grouptuples[i];
                  grouptuples[i] = -1;
               }
            }
         }

         Section2 section = new Section2();
         section.index = index;
         setSectionTotals(section, currents.length, tuple, totalmap, map);

         if(currents.length == 0) {
            root.addSection(section);
         }
         else {
            currents[currents.length - 1].addSection(section);
         }

         lasttuple = tuple;
      }

      // process remainders
      for(int i = currents.length - 1; i >= 0; i--) {
         if(currents[i] != null) {
            sortSection(currents[i]);

            if(i == 0) {
               root.addSection(currents[i]);
            }
            else {
               currents[i - 1].addSection(currents[i]);
            }
         }
      }

      sortSection(root);

      // generate new tuple list
      genWholeTupleList(src, dest, root, true);

      // for grandtotal/sub-total tuples, just copy them
      for(; index < src.size(); index++) {
         dest.add(src.get(index));
      }

      return dest;
   }

   private boolean topNAggregate(List<Tuple> rvec, List<Tuple> cvec,
                                 Map<PairN, Object> totalMap, Map<PairN, Object> map)
   {
      List<Section> sections = new ArrayList<>();

      for(int i = 0; i < rvec.size(); i++) {
         Tuple r = rvec.get(i);

         if(r.size() != rowh.length) {
            continue;
         }

         for(int j = 0; j < cvec.size(); j++) {
            Tuple c = cvec.get(j);

            if(c.size() != colh.length) {
               continue;
            }

            PairN p = new PairN(r, c, topNAggregateCol);
            Object value = map.get(p);

            if(value == null) {
               value = totalMap.get(p);
            }

            if(value == null) {
               continue;
            }

            Section sec = new Section();
            sec.index = i;
            sec.tindex = j;

            if(value instanceof Formula) {
               sec.total = ((Formula) value).getResult();
            }
            else {
               sec.total = value;
            }

            sections.add(sec);
         }
      }

      if(sections.size() <= topNAggregateN) {
         return false;
      }

      sections.sort(topNAggregateComparator);
      List<Pair> pairs = new ArrayList<>();

      for(int i = 0; i < topNAggregateN; i++) {
         Section sec = sections.get(i);
         Tuple r = rvec.get(sec.index);
         Tuple c = cvec.get(sec.tindex);
         pairs.add(new PairN(r, c, -1));
      }

      trimTuple(rvec, pairs, true);
      trimTuple(cvec, pairs, false);
      trimMap(pairs, totalMap);
      trimMap(pairs, map);
      return true;
   }

   private void trimTuple(List<Tuple> vec, List<Pair> pairs, boolean isrow) {
      for(int i = vec.size() - 1; i >= 0; i--) {
         Tuple t = vec.get(i);

         if(t.size() <= 0) {
            continue;
         }

         boolean found = false;

         for(Pair p : pairs) {
            Tuple t2 = (Tuple) (isrow ? p.value1 : p.value2);

            if(onPath(t, t2)) {
               found = true;
               break;
            }
         }

         if(!found) {
            vec.remove(i);
         }
      }
   }

   private void trimMap(List<Pair> pairs, Map<PairN, Object> map) {
      for(Iterator<PairN> i = map.keySet().iterator(); i.hasNext();) {
         Pair p = i.next();
         Tuple r = (Tuple) p.value1;
         Tuple c = (Tuple) p.value2;
         boolean found = false;

         for(Pair p2 : pairs) {
            Tuple r2 = (Tuple) p2.value1;
            Tuple c2 = (Tuple) p2.value2;

            if(onPath(r, r2) && onPath(c, c2)) {
               found = true;
               break;
            }
         }

         if(!found) {
            i.remove();
         }
      }
   }

   private boolean onPath(Tuple t, Tuple dt) {
      if(t.size() <= 0) {
         return true;
      }

      for(int i = 0; i < t.size(); i++) {
         if(!Tool.equals(t.getRow()[i], dt.getRow()[i])) {
            return false;
         }
      }

      return true;
   }

   /**
    * Topn filter tuples.
    *
    * @param src the source tuple list
    * @param totalmap the totalmap
    * @param isrow true if row topn, false if col topn
    */
   private List<Tuple> topNFilter(List<Tuple> src, Map<PairN, Object> totalmap,
                                  Map<PairN, Object> map, boolean isrow, Map<PairN, Object> calcMap,
                                  boolean calc)
   {
      int index = 0;
      List<Tuple> dest = new ArrayList<>();

      // for grandtotal/sub-total tuples, just copy them
      for(; index < src.size(); index++) {
         Tuple t = src.get(index);

         if(t.getRow().length == 0 || t.getRow()[0] == null) {
            dest.add(t);
         }
         else {
            break;
         }
      }

      Tuple lasttuple = null;
      int count = isrow ? rcount : ccount;
      Section root = new Section();
      Section[] currents = new Section[count == 0 ? 0 : count - 1];
      int[] grouptuples = new int[count == 0 ? 0 : count - 1];
      Arrays.fill(grouptuples, -1);

      boolean first = isrow ? rowTotalOnTop : colTotalOnFirst;

      for(; index < src.size(); index++) {
         Tuple tuple = src.get(index);

         // found grandtotal/sub-total tuple
         if(tuple.getRow().length == 0 || tuple.getRow()[0] == null) {
            break;
         }

         // is group total tuple
         if(tuple.size() < count) {
            if(first) {
               grouptuples[tuple.size() - 1] = index;
            }
            else {
               if(currents[tuple.size() - 1] == null) {
                  currents[tuple.size() - 1] = new Section();
               }

               currents[tuple.size() - 1].tindex = index;
            }

            continue;
         }

         // process regular tuple
         int col = getGroupCol(lasttuple, tuple);

         // is group col
         if(col < currents.length) {
            for(int i = currents.length - 1; i >= col; i--) {
               if(currents[i] == null) {
                  break;
               }

               calculateTopNSection(totalmap, map, i, currents[i], isrow, calcMap, calc);

               if(i == 0) {
                  root.addSection(currents[i]);
               }
               else {
                  currents[i - 1].addSection(currents[i]);
               }
            }

            for(int i = col; i < currents.length; i++) {
               currents[i] = new Section();
               currents[i].index = index;

               if(first && grouptuples[i] != -1) {
                  currents[i].tindex = grouptuples[i];
                  grouptuples[i] = -1;
               }
            }
         }

         Section section = new Section();
         section.index = index;

         if(currents.length == 0) {
            root.addSection(section);
         }
         else {
            currents[currents.length - 1].addSection(section);
         }

         lasttuple = tuple;
      }

      // process remainders
      for(int i = currents.length - 1; i >= 0; i--) {
         if(currents[i] != null) {
            calculateTopNSection(totalmap, map, i, currents[i], isrow, calcMap, calc);

            if(i == 0) {
               root.addSection(currents[i]);
            }
            else {
               currents[i - 1].addSection(currents[i]);
            }
         }
      }

      calculateTopNSection(totalmap, map, -1, root, isrow, calcMap, calc);

      // generate new tuple list
      genWholeTupleList(src, dest, root, isrow);

      // for grandtotal/sub-total tuples, just copy them
      for(; index < src.size(); index++) {
         dest.add(src.get(index));
      }

      return dest;
   }

   /**
    * Get group col of a tuple by comparing it with last tuple.
    *
    * @param lasttuple last tuple
    * @param tuple current tuple
    */
   private int getGroupCol(Tuple lasttuple, Tuple tuple) {
      int i = 0;

      if(lasttuple == null) {
         return i;
      }

      for(; i < lasttuple.size(); i++) {
         if(!Tool.equals(lasttuple.getRow()[i], tuple.getRow()[i])) {
            break;
         }
      }

      return i;
   }

   /**
    * Set section totals for sorting.
    * @param section the specified section.
    * @param col the col index in row/column headers.
    * @param tuple the tuple.
    * @param totalmap the totalmap.
    * @param map the map.
    */
   private void setSectionTotals(Section2 section, int col, Tuple tuple,
                                 Map<PairN, Object> totalmap, Map<PairN, Object> map)
   {
      if(sinfo == null || sinfo.cols == null || sinfo.cols.length == 0) {
         return;
      }

      Tuple grouptuple;
      section.total = new Object[sinfo.cols.length];

      // is detailed tuple
      if(col == rcount - 1) {
         grouptuple = tuple;
      }
      // is group tuple
      else {
         Object[] arr = new Object[col + 1];
         tuple.copyInto(arr, col + 1);
         grouptuple = new Tuple(arr);
      }

      for(int i = 0; i < sinfo.cols.length; i++) {
         int dcol0 = sideBySide ?
            ((sinfo.cols[i] - (rcount + hcolmore)) % dcol.length) : 0;
         int cpos = sinfo.cols[i] < rcount + hcolmore ? -1
            : (colinc == 0 ? -1 :
               (sinfo.cols[i] - (rcount + hcolmore)) / colinc);
         Tuple ctuple = cpos < 0 || cpos >= cvec.size() ? new Tuple() : cvec.get(cpos);
         PairN p = new PairN(grouptuple, ctuple, dcol0);

         if(sum == null || sum[dcol0] == null) {
            section.total[i] = map.get(p);
         }
         else {
            section.total[i] = totalmap.get(p);

            if(section.total[i] instanceof Formula) {
               section.total[i] = ((Formula) section.total[i]).getResult();
            }
         }
      }
   }

   /**
    * Set section total.
    *
    * @param section the specified section
    * @param col the col index in row/column headers
    * @param tuple the tuple
    * @param totalmap the totalmap
    * @param isrow true if is row header, false is col header
    */
   private void setSectionTotal(Section section, int col, Tuple tuple,
                                Map<PairN, Object> totalmap, Map<PairN, Object> map,
                                boolean isrow, Map<PairN, Object> calcMap, boolean calc)
   {
      InnerTopNInfo topn = isrow ? getRowTopN(col) : getColTopN(col);
      InnerTopNInfo sort = isrow ? getRowSortByValInfo(col, calc) :
                                   getColSortByValInfo(col, calc);
      setSectionTotal(section, topn, col, tuple, totalmap, map, isrow, false, calcMap, calc);

      if(topn != null && sort != null && topn.dcol == sort.dcol) {
         section.sbtotal = section.total;
      }
      else {
         setSectionTotal(section, sort, col, tuple, totalmap, map, isrow, true, calcMap, calc);
      }
   }

   /**
    * Set section total.
    */
   private void setSectionTotal(Section section, InnerTopNInfo topn,
                                int col, Tuple tuple, Map<PairN, Object> totalmap,
                                Map<PairN, Object> map, boolean isrow, boolean isSortByVal,
                                Map<PairN, Object> calcMap, boolean calc)
   {
      // the col needn't topn
      if(topn == null) {
         return;
      }

      Tuple grouptuple;
      Integer j = topn.dcol;

      // is detailed tuple
      if(isrow && col == rcount - 1 || !isrow && col == ccount - 1) {
         grouptuple = tuple;
      }
      // is group tuple
      else {
         Object[] arr = new Object[col + 1];
         tuple.copyInto(arr, col + 1);
         grouptuple = new Tuple(arr);
      }

      if(sum == null || sum[topn.dcol] == null) {
         PairN pair = findPair(grouptuple, isrow, j, map);

         if(pair != null) {
            if(isSortByVal) {
               section.sbtotal = getResult(totalmap, map, pair, calcMap, calc);
            }
            else {
               section.total = getResult(totalmap, map, pair, calcMap, calc);
            }
         }
      }
      else {
         PairN pair = isrow ? new PairN(grouptuple, new Tuple(), j) :
            new PairN(new Tuple(), grouptuple, j);
         resetSummary(section, totalmap, pair, isrow);

         // set section total value
         if(isSortByVal) {
            section.sbtotal = getResult(totalmap, map, pair, calcMap, calc);
         }
         else {
            section.total = getResult(totalmap, map, pair, calcMap, calc);
         }
      }
   }

   /**
    * Reset summary value.
    */
   private void resetSummary(Section section, Map<PairN, Object> totalMap, PairN pair, boolean isrow)
   {
      boolean merged = section instanceof MergedSection;

      // detail?
      if(section == null || section.sections == null && !merged) {
         return;
      }

      if(merged || (shouldRowTopN() || shouldColTopN()) && isAggregateTopN())  {
         int cidx = pair.getNum();

         if(cidx < 0 || cidx >= this.dcol.length) {
            return;
         }

         int dcol = this.dcol[cidx];
         Object obj = putMap(totalMap, pair, cidx);

         if(!(obj instanceof Formula)) {
            return;
         }

         Formula formula = (Formula) obj;
         formula.reset();

         List<Section> details = new ArrayList<>();
         genDetailSections(section, details);
         // current tuples
         List<Tuple> ttuples = isrow ? rvec : cvec;
         // other tuples
         List<Tuple> otuples = isrow ? cvec : rvec;
         int len = isrow ? colh.length : rowh.length;

         for(Section detail : details) {
            // current tuple
            Tuple ttuple = ttuples.get(detail.index);

            for(Object oobj : otuples) {
               // other tuple
               Tuple otuple = (Tuple) oobj;

               // detail tuple?
               if(otuple.size() >= len) {
                  PairN p = isrow ? new PairN(ttuple, otuple, cidx) :
                                   new PairN(otuple, ttuple, cidx);
                  int[] rows = getRowNumbers(p);

                  if(rows != null) {
                     for(int row : rows) {
                        addValue(formula, table, row, dcol);
                     }
                  }
               }
            }
         }
      }
   }

   /**
    * Reset others for the specified summary column.
    */
   private void resetOthersGrandTotal(MergedSection merged, int col, boolean isrow,
                                      Map<PairN, Object> totalMap, Map<PairN, Object> map,
                                      Map<PairN, Object> calcMap, boolean calc)
   {
      if(merged == null || merged.index < 0) {
         return;
      }

      List<Tuple> vec = isrow ? rvec : cvec;
      Tuple tuple = vec.get(merged.index);

      if(tuple == null || tuple.size() <= 0) {
         return;
      }

      Object[] objs = new Object[tuple.size()];
      tuple.copyInto(objs);
      objs[objs.length - 1] = OTHERS;
      tuple = new MergedTuple(objs, new ArrayList<>());
      InnerTopNInfo sort = isrow ? getRowSortByValInfo(col) :
                                   getColSortByValInfo(col);

      // no sort by value? don't need to reset
      if(sort == null) {
         return;
      }

      int dcol = sort.dcol;
      PairN pair = isrow ? new PairN(tuple, emptyTuple, dcol) : new PairN(emptyTuple, tuple, dcol);
      resetSummary(merged, totalMap, pair, isrow);
      merged.sbtotal = getResult(totalMap, map, pair, calcMap, calc);
   }

   /**
    * Generate all details section.
    */
   private void genDetailSections(Section section, List<Section> details) {
      if(section == null) {
         return;
      }

      if(section instanceof MergedSection) {
         MergedSection merged = (MergedSection) section;

         for(Object sub : merged.list) {
            genDetailSections((Section) sub, details);
         }

         return;
      }
      else if(section.sections != null) {
         for(Object sub : section.sections) {
            genDetailSections((Section) sub, details);
         }

         return;
      }

      details.add(section);
   }

   /**
    * Get result.
    */
   private Object getResult(Map<PairN, Object> totalMap, Map<PairN, Object> map, PairN pair,
                            Map<PairN, Object> calcMap, boolean calc)
   {
      Object obj = null;
      CalcColumn aggCalc = getAggCalc(pair.getNum());

      if(calc && calcMap != null && !(aggCalc instanceof PercentColumn)) {
         obj = calcMap.get(pair);
         obj = obj == CalcColumn.INVALID ? (fillwithzero ? 0 : null) : obj;
      }
      else if(totalMap != null && totalMap.containsKey(pair)) {
         obj = totalMap.get(pair);
      }
      else if(map != null) {
         obj = map.get(pair);
      }

      if(!(obj instanceof Formula)) {
         return obj;
      }

      return obj instanceof PercentageFormula ?
         ((PercentageFormula) obj).getOriginalResult() :
         ((Formula) obj).getResult();
   }

   private CalcColumn getAggCalc(int index) {
      if(aggCalcMap.containsKey(index)) {
         return aggCalcMap.get(index);
      }

      if(calcs != null) {
         for(CalcColumn calc : calcs) {
            aggCalcMap.put(calc.getColIndex(), calc);
         }
      }

      return aggCalcMap.get(index);
   }

   /**
    * Find pair for non-sum.
    */
   private PairN findPair(Tuple tuple, boolean row, Object j, Map<PairN, Object> map) {
      for(PairN pair : map.keySet()) {
         if(!Tool.equals(pair.num, j)) {
            continue;
         }

         if(row && Tool.equals(pair.getValue1(), tuple)) {
            return pair;
         }
         else if(!row && Tool.equals(pair.getValue2(), tuple)) {
            return pair;
         }
      }

      return null;
   }

   /**
    * Sort section.
    */
   private void sortSection(Section section) {
      if(section == null || section.sections == null) {
         return;
      }

      section.sections.sort(secComparer);
   }

   /*
    * Calculate topn sub sections of a section.
    *
    * @param col the index in row/column headers
    * @param section the section to be calculated
    * @param isrow true if the index is in row headers, false if the index is
    * in column headers
    */
   private void calculateTopNSection(Map<PairN, Object> totalMap, Map<PairN, Object> map, int col,
                                     Section section, boolean isrow, Map<PairN, Object> calcMap,
                                     boolean calc)
   {
      if(section == null) {
         return;
      }

      prepareSubSectionSum(section, totalMap, map, col + 1, isrow, calcMap, calc);
      section.sections = calculateTopNSubSections(col + 1, section.sections,
                                                  isrow, totalMap, map, calcMap, calc);
      section.sections = sortByValFilter(col + 1, section.sections, isrow, calc);
   }

   /**
    * Prepare sub sections sums.
    */
   private void prepareSubSectionSum(Section section, Map<PairN, Object> totalMap,
                                     Map<PairN, Object> map, int col, boolean isrow,
                                     Map<PairN, Object> calcMap, boolean calc)
   {
      if(section == null || section.sections == null) {
         return;
      }

      List<Tuple> vecs = isrow ? rvec : cvec;

      for(Object obj : section.sections) {
         Section sub = (Section) obj;

         if(sub.index >= 0) {
            setSectionTotal(sub, col, vecs.get(sub.index), totalMap, map, isrow, calcMap, calc);
         }
      }
   }

   /**
    * Calculate topn sections among all the sections.
    *
    * @param col the index in row/column headers
    * @param sections the sub section list
    * @param isrow true if the index is in row headers, false if the index is
    * in column headers
    * @return an section list stores remainder sub sections
    */
   private List<Section> calculateTopNSubSections(int col, List<Section> sections, boolean isrow,
                                                  Map<PairN, Object> totalMap,
                                                  Map<PairN, Object> map,
                                                  Map<PairN, Object> calcMap, boolean calc)
   {
      if(sections == null) {
         return null;
      }

      // the col needn't topn
      if(isrow && !containsRowTopN(col) || !isrow && !containsColTopN(col)) {
         return sections;
      }

      InnerTopNInfo info = isrow ? getRowTopN(col) : getColTopN(col);
      SectionComparer sc = getSectionComparer(col, isrow);
      List<Section> topNSections = new ArrayList<>(sections);
      topNSections.sort(sc);

      assert info != null;
      int n = (info.n > 0) ? Math.min(info.n, sections.size()) : sections.size();

      // @by billh, 'keep last equal' is not supported, but if we can edit it
      // in topn editor, we should support it
      int length = isrow ? rcount : ccount;

      if(info.others && (col == length - 1)) {
         List<Section> others = new ArrayList<>();

         for(int i = topNSections.size() - 1; i >= n; i--) {
            Section removed = topNSections.remove(i);
            sections.remove(removed);
            others.add(removed);
         }

         if(others.size() > 0) {
            MergedSection merged = new MergedSection(others);
            // just reset the grand total for the others, to make sure sort by
            // value is correct, other cells will be reset after all data
            // generated
            resetOthersGrandTotal(merged, col, isrow, totalMap, map, calcMap, calc);
            sections.add(merged);
         }
      }
      else {
         for(int i = topNSections.size() - 1; i >= n; i--) {
            sections.remove(topNSections.remove(i));
         }
      }

      return sections;
   }

   /**
    * Calculate topn sections among all the sections.
    *
    * @param col the index in row/column headers
    * @param sections the sub section list
    * @param isrow true if the index is in row headers, false if the index is
    * in column headers
    * @return an section list stores remainder sub sections
    */
   private List<Section> sortByValFilter(int col, List<Section> sections, boolean isrow,
                                         boolean calc)
   {
      if(sections == null) {
         return null;
      }

      // the col needn't topn
      if(isrow && !containsRowSortByVal(col) ||
         !isrow && !containsColSortByVal(col))
      {
         return sections;
      }

      SectionComparer sc = getSortByValComparer(col, isrow, calc);

      if(sc == null) {
         return sections;
      }
      
      sections.sort(sc);
      return sections;
   }

   /**
    * Generate the whole tuple list of a root section stores all sections as
    * the result of topn calculation.
    *
    * @param src the source list stores the original sequence tuples
    * @param dest the destination list stores the new sequence tuples
    * @param root the root section
    * @param isrow true if is generate for row headers
    */
   private void genWholeTupleList(List<Tuple> src, List<Tuple> dest, Section root, boolean isrow) {
      if(root.sections == null) {
         return;
      }

      for(int i = 0; i < root.sections.size(); i++) {
         genTupleListOfASection(src, dest, root.sections.get(i), isrow);
      }
   }

   /**
    * Generate the tuple list of a section as the result of topn calculation.
    *
    * @param src the source list stores the original sequence tuples
    * @param dest the destination list stores the new sequence tuples
    * @param section the specified section
    * @param isrow true if is generate for row headers
    */
   private void genTupleListOfASection(List<Tuple> src, List<Tuple> dest, Section section,
                                       boolean isrow)
   {
      if(section == null) {
         return;
      }

      // group total tuple is in header and group total exists
      if(((isrow && rowTotalOnTop) || (!isrow && colTotalOnFirst)) &&
         section.tindex != -1)
      {
         dest.add(src.get(section.tindex));
      }

      // is detailed section
      if(section instanceof MergedSection) {
         MergedSection merged = (MergedSection) section;

         List<Tuple> tuples = new ArrayList<>();
         getTuples(merged, src, tuples);
         int length = isrow ? rcount : ccount;
         Object[] rows = new Object[length];

         if(tuples.size() > 0) {
            Tuple temp = tuples.get(0);
            temp.copyInto(rows);
         }

         rows[length - 1] = OTHERS;
         MergedTuple tuple = new MergedTuple(rows, tuples);
         Comparer[] comparer = isrow ? getRowComparers() : getColComparers();
         tuple.setComparer(comparer);
         dest.add(tuple);
      }
      else if(section.sections == null) {
         dest.add(src.get(section.index));
      }
      // is group section
      else {
         for(int i = 0; i < section.sections.size(); i++) {
            genTupleListOfASection(src, dest, section.sections.get(i), isrow);
         }
      }

      // group total tuple is in footer and group total exists
      if(((isrow && !rowTotalOnTop) || (!isrow && !colTotalOnFirst)) && section.tindex != -1) {
         dest.add(src.get(section.tindex));
      }
   }

   /**
    * Test if the cell is a header cell.
    *
    * @param row the cell's row
    * @param col the cell's col
    * @return <code>true</code> if the cell is a header cell, <code>false</code>
    * otherwise
    */
   @Override
   public boolean isHeaderCell(int row, int col) {
      checkInit();

      return isRowHeaderCell(row, col) || isColHeaderCell(row, col);
   }

   /**
    * Check if is row header cell.
    */
   private boolean isRowHeaderCell(int row, int col) {
      checkInit();

      // is grand total cell?
      if(isGrandTotalCell(row, col)) {
         return false;
      }

      return row < getHeaderRowCount() && col >= getHeaderColCount();
   }

   /**
    * Check if is col header cell.
    */
   private boolean isColHeaderCell(int row, int col) {
      checkInit();

      // is grand total cell?
      if(isGrandTotalCell(row, col)) {
         return false;
      }

      return col < getHeaderColCount() && row >= getHeaderRowCount();
   }

   /**
    * Test if the cell is a data cell.
    *
    * @param row the cell's row
    * @param col the cell's col
    * @return <code>true</code> if the cell is a data cell, <code>false</code>
    * otherwise
    */
   @Override
   public boolean isDataCell(int row, int col) {
      checkInit();

      // is grand total cell?
      if(isGrandTotalCell(row, col)) {
         return false;
      }

      // is data cell?
      return row >= getHeaderRowCount() && col >= getHeaderColCount();
   }

   /**
    * Test if the cell is a grandtotal cell.
    *
    * @param row the cell's row
    * @param col the cell's col
    * @return <code>true</code> if the cell is a grandtotal cell,
    * <code>false</code> otherwise
    */
   @Override
   public boolean isGrandTotalCell(int row, int col) {
      checkInit();

      return isGrandTotalCol(col) || isGrandTotalRow(row);
   }

   /**
    * Test if the cell is a 'Grand Total' cell.
    *
    * @param row the cell's row
    * @param col the cell's col
    * @return <code>true</code> if the cell is a 'Grand Total' cell,
    * <code>false</code> otherwise
    */
   public boolean isGrandTotalHeaderCell(int row, int col) {
      String grandTotal = Catalog.getCatalog().getString("Grand Total");
      return (isGrandTotalRow(row) || isGrandTotalCol(col)) && grandTotal.equals(getObject(row, col));
   }

   /**
    * Test if the col is a grandtotal col.
    *
    * @param col the col
    * @return <code>true</code> if the col is a grandtotal col,
    * <code>false</code> otherwise
    */
   public boolean isGrandTotalCol(int col) {
      checkInit();

      if(isSuppressColumnGrandTotal()) {
         return false;
      }

      int span = sideBySide ? dcol.length : 1;

      if(isColumnTotalOnFirst()) {
         return col >= getHeaderColCount() && col < getHeaderColCount() + span;
      }
      else {
         return col == getColCount() - span;
      }
   }

   /**
    * Test if the row is a grandtotal row.
    *
    * @param row the row
    * @return <code>true</code> if the row is a grandtotal row,
    * <code>false</code> otherwise
    */
   public boolean isGrandTotalRow(int row) {
      checkInit();

      if(isSuppressRowGrandTotal()) {
         return false;
      }

      int span = sideBySide ? 1 : dcol.length;

      if(isRowTotalOnTop()) {
         return row >= ccount && row < ccount + span;
      }
      else {
         return row >= getRowCount() - span;
      }
   }

   /**
    * Get a header cell's header name.
    *
    * @param row the header's row
    * @param col the header's col
    * @return the header cell's header name
    */
   public String getHeaderName(int row, int col) {
      checkInit();

      // is not header cell?
      if(!isHeaderCell(row, col)) {
         return null;
      }

      // is row header cell
      if(row < ccount) {
         return Util.getHeader(table, colh[row]).toString();
      }
      else {
         return Util.getHeader(table, rowh[col]).toString();
      }
   }

   /**
    * Get a data cell's name.
    *
    * @param row the data cell's row
    * @param col the data cell's col
    * @return the data cell's name
    */
   public String getDataName(int row, int col) {
      checkInit();

      if(isDataCell(row, col) || isGrandTotalCell(row, col)) {
         int index = sideBySide
            ? (col - getHeaderColCount()) % dcol.length
            : (row - getHeaderRowCount()) % dcol.length;

         if(index >= 0) {
            return Util.getHeader(table, dcol[index]).toString();
         }
      }

      return null;
   }

   /**
    * Get available fields of a crosstab cell.
    *
    * @param row the specified row
    * @param col the specified col
    */
   @Override
   public String[] getAvailableFields(int row, int col) {
      return ((CrosstabDataDescriptor) getDescriptor()).getAvailableFields(row, col);
   }

   /**
    * Create key value paires for hyperlink and condition to use.
    *
    * @param row the specified row
    * @param col the specified col
    * @param map the specified map, null if should create a new one
    * @return a map stores key value pairs
    */
   @Override
   public Map<Object, Object> getKeyValuePairs(int row, int col, Map<Object, Object> map) {
      return ((CrosstabDataDescriptor) getDescriptor()).getKeyValuePairs(row, col, map);
   }

   /**
    * Make sure crosstab is initialized.
    */
   @Override
   public void checkInit() {
      if(data == null) {
         synchronized(this) {
            if(data == null) {
               process();
            }
         }
      }
   }

   /**
    * Get the row to be base style.
    */
   private int getStyleRow(int r) {
      // @by larryl 2003-9-26, mapping header to header, body to body, footer
      // to footer, same for getStyleCol
      if(r < 0) {
         return r;
      }

      int hcnt = getHeaderRowCount();

      if(r < hcnt) {
         return r % Math.max(1, table.getHeaderRowCount());
      }
      else if(table.getTrailerRowCount() > 0 &&
         getTrailerRowCount() > 0 && r == getRowCount() - 1)
      {
         return table.getRowCount() - 1;
      }

      int hcnt2 = table.getHeaderRowCount();
      int rcnt = table.getRowCount();
      int result = (r - hcnt) % Math.max(2, rcnt - hcnt2 - 1) + hcnt2;

      // @by billh, in most cases, table detail row count greater crosstab
      // detail row count, but there are some exceptions like metadata table
      return result >= rcnt ? rcnt - 1 : result;
   }

   /**
    * Get the column to be base style.
    */
   public int getStyleCol(int c) {
      if(c < 0) {
         return c;
      }

      int hcnt = getHeaderColCount();

      if(c < hcnt) {
         return c % Math.max(1, table.getHeaderColCount());
      }

      int ccnt = table.getColCount();

      if(c == getColCount() - 1) {
         return ccnt - 1;
      }

      int hcnt2 = table.getHeaderColCount();

      // @by billh, in most cases, table detail col count greater crosstab
      // detail col count, but there are some exceptions like metadata table
      int result = (c - hcnt) % Math.max(2, ccnt - hcnt2 - 1) + hcnt2;
      return result >= ccnt ? ccnt - 1 : result;
   }

   /**
    * Dispose the table to clear up temporary resources.
    */
   @Override
   public void dispose() {
      table.dispose();
   }

   /**
    * Get header at an index in base table.
    * @return header at the specified index in base table
    */
   @Override
   public Object[] getHeaders() {
      Object[] titles = new Object[dcol.length];

      for(int i = 0; i < titles.length; i++) {
         if(measureHeaders != null && measureHeaders[i] != null) {
            titles[i] = measureHeaders[i];
         }
         else {
            titles[i] = getHeader(dcol[i], i);
         }
      }

      return titles;
   }

   /**
    * Get the mapping name of the header.
    */
   public Object getHeader(Object header) {
      Object res = header != null ? headers2i18n.get(header) : null;
      return res == null ? header : res;
   }

   /**
    * Set the mapping name of specified header.
    */
   public void setHeader(Object header, Object str) {
      headers2i18n.put(header, str);
      i18n2headers.put(str, header);
   }

   /**
    * @return the header mapping.
    */
   @Override
   public Hashtable<Object, Object> getHeaderMaps() {
      return i18n2headers;
   }

   /**
    * @return calc headers of the crossfilter.
    */
   @Override
   public String[] getCalcHeaders() {
      if(calcHeaders == null) {
         initCalcHeaders();
      }

      return calcHeaders;
   }

   public int[] getDuptimes() {
      return duptimes;
   }

   /**
    * Set the measure names to table.
    */
   @Override
   public void setMeasureNames(String[] names) {
      this.measureHeaders = names;
   }

   /**
    * Set the measure names to table.
    */
   public void setMeasureNames(String[] names, Map<String, String> calcHeaderMap) {
      this.measureHeaders = names;
      this.calcMeasureMap = calcHeaderMap;
   }

   /**
    * Get measure name.
    *
    * @param index measure index.
    * @return
    */
   protected String getMeasureName(int index) {
      return measureHeaders == null || index >= measureHeaders.length ? null :
         measureHeaders[index];
   }

   public void setRowHeaders(List<String> headers) {
      this.rowHeaders = headers;
   }

   public void setColHeaders(List<String> headers) {
      this.colHeaders = headers;
   }

   @Override
   public int getAppliedMaxRows() {
      return appliedMaxRows;
   }

   /**
    * Pair holds two values and an optional data col value.
    */
   static class Pair implements java.io.Serializable {
      public Pair(Object value1, Object value2) {
         this.value1 = value1;
         this.value2 = value2;
         this.hash = value1.hashCode() + value2.hashCode();
      }

      public boolean equals(Object pobj) {
         if(this == pobj) {
            return true;
         }

         if(pobj == null || getClass() != pobj.getClass()) {
            return false;
         }

         Pair p = (Pair) pobj;

         return value1.equals(p.value1) && value2.equals(p.value2);
      }

      public int hashCode() {
         return hash;
      }

      public String toString() {
         return "[Pair: " + value1 + ", " + value2 + "]";
      }

      public Object getValue1() {
         return value1;
      }

      public Object getValue2() {
         return value2;
      }

      protected int hash;
      private final Object value1;
      private final Object value2;
   }

   public static final class PairN extends Pair {
      public PairN(Object value1, Object value2, int n) {
         super(value1, value2);
         this.num = n;
         this.hash += n;
      }

      @Override
      public boolean equals(Object pobj) {
         if(!super.equals(pobj)) {
            return false;
         }

         return num == ((PairN) pobj).num;
      }

      public String toString() {
         return "[Pair: " + getValue1() + ", " + getValue2() + ", " + num + "]";
      }

      public int getNum() {
         return num;
      }

      private final int num;
   }

   /**
    * An inner topn info stores basic topn info.
    */
   static class InnerTopNInfo implements Serializable {
      /**
       * Constructor.
       *
       * @param dcol the data column index in data columns, not in base table
       * @param n the topn value
       * @param asc true if ascending
       */
      public InnerTopNInfo(int dcol, int n, boolean asc, boolean others) {
         this.dcol = dcol;
         this.n = n;
         this.asc = asc;
         this.others = others;
      }

      int dcol;
      int n;
      boolean asc;
      boolean others;
   }

   /**
    * A <code>MergedSection</code> instance is used to store a mergedsection info.
    */
   private static class MergedSection extends Section {
      public MergedSection(List<Section> sections) {
         List<Section> temp = new ArrayList<>();

         for(int i = sections.size() - 1; i >= 0; i--) {
            temp.add(sections.get(i));
         }

         this.list = temp;
         this.index = sections.get(0).index;
      }

      public void setSectionTotal() {
         SumFormula formula = new SumFormula();

         for(Section section : list) {
            formula.addValue(section.total);
         }

         sbtotal = formula.getResult();
         total = formula.getResult();
      }

      List<Section> list;
   }

   private static void getTuples(MergedSection section, List<Tuple> src, List<Tuple> tuples) {
      List<Section> list = section.list;

      for(Section sec : list) {
         if(sec instanceof MergedSection) {
            getTuples((MergedSection) sec, src, tuples);
         }
         else if(sec.index != -1) {
            tuples.add(src.get(sec.index));
         }
      }
   }

   /**
    * Create the meta info.
    */
   protected void createXMetaInfo() {
      Comparer[][] comps = {rowComparer, colComparer};
      int[][] hcols = {rowh, colh};
      minfos = new HashMap<>();
      levels = new HashMap<>();

      for(int i = 0; i < comps.length; i++) {
         for(int j = 0; j < comps[i].length; j++) {
            Comparer comp = comps[i][j];
            int col0 = hcols[i][j];

            if(col0 < 0 || !(comp instanceof SortOrder)) {
               continue;
            }

            SortOrder order = (SortOrder) comp;
            int level = order.getOption();
            int row = j;
            int rdelta = isRowTotalOnTop() ? 1 : 0;

            // get format for row headers
            if(i == 0) {
               row = Math.max(1, colh.length);
               row += rdelta + hrowmore;
            }

            int col = j;
            // find the correct cell for group header. (50147)
            int cdelta = !isSuppressColumnGrandTotal() && isColumnTotalOnFirst() ?
               (isSummarySideBySide() ? dcol.length : 1) : 0;

            // get format for column headers
            if(i == 1) {
               col = Math.max(1, rowh.length);
               col += cdelta + hcolmore;
            }

            TableDataPath path = getDescriptor().getCellDataPath(row, col);
            levels.put(path, level);

            if(!Tool.isDateClass(table.getColType(col0)) &&
               (!Tool.isNumberClass(table.getColType(col0)) ||
               (level & SortOrder.PART_DATE_GROUP) == 0))
            {
               continue;
            }

            // aggregate column?
            if(path == null || path.getType() == TableDataPath.SUMMARY ||
               path.getType() == TableDataPath.GRAND_TOTAL)
            {
               continue;
            }

            XMetaInfo minfo = new XMetaInfo();
            XFormatInfo finfo = new XFormatInfo();
            SimpleDateFormat dfmt = XUtil.getDefaultDateFormat(level, order.getDataType());

            if(dfmt != null) {
               String fmt = dfmt.toPattern();
               finfo = new XFormatInfo(TableFormat.DATE_FORMAT, fmt);
            }

            minfo.setXFormatInfo(finfo);
            minfos.put(path, minfo);
         }
      }
   }

   /**
    * Get row header name.
    */
   @Override
   public String getRowHeader(int idx) {
      return rowh[0] == -1 ? null : getHeader(rowh[idx], 100 + idx);
   }

   /**
    * Get col header name.
    */
   @Override
   public String getColHeader(int idx) {
      return colh[0] == -1 ? null : getHeader(colh[idx], 100 + rowh.length + idx);
   }

   /**
    * @return the column indexes array for aggregates.
    */
   @Override
   public int[] getDataIndexes() {
      return dcol;
   }

   /**
    * @return the row header indexes array.
    */
   @Override
   public int[] getRowHeaderIndexes() {
      return rowh;
   }

   /**
    * @return the column header indexes array.
    */
   @Override
   public int[] getColHeaderIndexes() {
      return colh;
   }

   /**
    * Get data header name.
    */
   @Override
   public String getDataHeader(int idx) {
      return getHeader(dcol[idx], idx);
   }

   /**
    * Get header at an index in base table.
    * @param index the specified data index
    * @return header at the specified index in base table
    */
   public String getDataHeader(String applyCalcHeader, int index, int didx) {
      String header = applyCalcHeader;

      if(header == null) {
         return getHeader(index, didx);
      }

      int[] duptimes = getDuptimes();

      if(didx != -1 && duptimes != null && duptimes[didx] > 0) {
         header = (String) Util.getDupHeader(header, duptimes[didx]);
      }

      if(calcMeasureMap != null && calcMeasureMap.get(header) == null) {
         calcMeasureMap.put(header, getHeader(index, didx));
      }

      return header;
   }

   /**
    * Get header at an index in base table.
    * @param index the specified index
    * @return header at the specified index in base table
    */
   @Override
   public String getHeader(int index, int didx) {
      if(calcHeaders == null) {
         initCalcHeaders();
      }

      if(didx >= 0 && didx < 100 && didx < calcHeaders.length &&
         calcHeaders[didx] != null)
      {
         return calcHeaders[didx];
      }

      String hd = getHeader0(index);

      if(didx != -1 && duptimes != null && duptimes[didx] > 0) {
         hd = (String) Util.getDupHeader(hd, duptimes[didx]);
      }

      return hd;
   }

   /**
    * Init the calc headers.
    */
   private void initCalcHeaders() {
      calcHeaders = new String[dcol.length];

      if(measureHeaders == null) {
         return;
      }

      Map<String, Integer> dups = new HashMap<>();

      for(int i = 0; i < dcol.length; i++) {
         if(sum != null && i < sum.length &&
            sum[i] instanceof CalcFieldFormula &&
            i < measureHeaders.length && measureHeaders[i] != null)
         {
            String header = measureHeaders[i];
            Integer dup = dups.get(header);

            if(dup != null) {
               header = header + "." + dup;
            }
            else {
               dup = 0;
            }

            dups.put(measureHeaders[i], dup + 1);
            calcHeaders[i] = header;

            String exp = ((CalcFieldFormula) sum[i]).getExpression();
            int idx = exp.indexOf("field['");
            int idx2 = exp.indexOf("']");

            if(idx != -1 && idx2 != -1 && idx2 > idx) {
               String oheader = exp.substring(idx + 7, idx2);
               idx = oheader.indexOf('(');
               idx2 = oheader.indexOf(')');

               if(idx >= 0 && idx2 >= 0 && idx < idx2) {
                  oheader = oheader.substring(0, idx + 1) +
                     oheader.substring(idx + 2, idx2 - 1) + ")";
               }

               calcHeader2OHeader.put(header, oheader);
            }
         }
      }
   }

   /**
    * Get header content.
    */
   protected String getHeader0(int index) {
      return Util.getHeader(table, index).toString();
   }

   /**
    * A section stores sub sections for topn filter calculation.
    */
   static class Section {
      /**
       * Add a sub section.
       */
      public void addSection(Section sec) {
         if(sections == null) {
            sections = new ArrayList<>();
         }

         sections.add(sec);
      }

      /**
       * Print the section.
       */
      public void print(int level) {
         System.err.print(level);

         for(int i = 0; i < level + 1; i++) {
            System.err.print("...");
         }

         System.err.print("Section[index: ");
         System.err.print(index);
         System.err.print(", total index: ");
         System.err.print(tindex);
         System.err.print(", total: ");
         printTotal();
         System.err.print(", sbtotal: ");
         System.err.print(sbtotal);
         System.err.println();
      }

      /**
       * Print total value.
       */
      protected void printTotal() {
         System.err.print(total);
      }

      int index = -1; // the section's index in rset/cset
      int tindex = -1; // the section's total index in rset/cset
      Object total = null; // the section's total value
      Object sbtotal = null;
      java.util.List<Section> sections = null; // the section's sub sections
   }

   /**
    * A section stores sub sections for sorting.
    */
   static class Section2 extends Section {
      /**
       * Print total value.
       */
      @Override
      protected void printTotal() {
         if(total == null) {
            super.printTotal();
            return;
         }

         for(int i = 0; i < total.length; i++) {
            if(i > 0) {
               System.err.print("; ");
            }

            System.err.print(total[i]);
         }
      }

      Object[] total = null;
   }

   /**
    * A comparator compares two sections.
    */
   class SectionComparer implements Comparator<Section> {
      /**
       * Constructor.
       *
       * @param dcol the data col index in data columns
       * @param asc true if ascending, false otherwise
       */
      SectionComparer(int dcol, boolean asc) {
         this.dcol = dcol;
         this.asc = asc ? 1 : -1;
      }

      /**
       * Compare two sections.
       */
      @Override
      public int compare(Section s1, Section s2) {
         if(s1.total == null && s2.total == null) {
            return 0;
         }
         else if(s1.total == null) {
            return -asc;
         }
         else if(s2.total == null) {
            return asc;
         }
         else {
            return dcomparers[dcol].compare(s1.total, s2.total) * asc;
         }
      }

      int dcol;
      int asc;
   }

   /**
    * A comparator compares two sections.
    */
   class SortByValSectionComparer extends SectionComparer {
      /**
       * Constructor.
       *
       * @param dcol the data col index in data columns
       * @param asc true if ascending, false otherwise
       */
      SortByValSectionComparer(int dcol, boolean asc) {
         super(dcol, asc);
      }

      /**
       * Compare two sections.
       */
      @Override
      public int compare(Section s1, Section s2) {
         boolean other1 = s1 instanceof MergedSection;
         boolean other2 = s2 instanceof MergedSection;

         if(other1 != other2 && isSortOthersLast()) {
            return other1 ? 1 : -1;
         }

         if(s1.sbtotal == null && s2.sbtotal == null) {
            return 0;
         }
         else if(s1.sbtotal == null) {
            return -asc;
         }
         else if(s2.sbtotal == null) {
            return asc;
         }
         else {
            return dcomparers[dcol].compare(s1.sbtotal, s2.sbtotal) * asc;
         }
      }
   }

   /**
    * A comparator compares two sections of class Section2.
    */
   class Section2Comparer implements Comparator<Section> {
      /**
       * Constructor.
       */
      Section2Comparer(CrosstabSortInfo sinfo) {
         this.cols = sinfo.cols;
         this.asc = new int[sinfo.asc.length];

         for(int i = 0; i < asc.length; i++) {
            asc[i] = sinfo.asc[i] ? 1 : -1;
         }
      }

      /**
       * Compare two sections.
       */
      @Override
      public int compare(Section ss1, Section ss2) {
         Section2 s1 = (Section2) ss1;
         Section2 s2 = (Section2) ss2;

         if(s1.total == null && s2.total == null) {
            return 0;
         }
         else if(s1.total == null) {
            return -asc[0];
         }
         else if(s2.total == null) {
            return asc[0];
         }
         else if((s1 instanceof Section2) && (s2 instanceof Section2)) {
            for(int i = 0; i < cols.length; i++) {
               int dcol0 = sideBySide ?
                  ((cols[i] - (rcount + hcolmore)) % dcol.length) : 0;
               int result = dcomparers[dcol0].compare(
                  ((Section2) s1).total[i], ((Section2) s2).total[i]) * asc[i];

               if(result != 0) {
                  return result;
               }
            }
         }

         return 0;
      }

      int[] cols;
      int[] asc;
   }

   /**
    * @return the report/vs name which this filter was created for,
    * and will be used when insert audit record.
    */
   @Override
   public String getReportName() {
      String name = super.getReportName();
      return name != null ? name : table != null ? table.getReportName() : null;
   }

   /**
    * @return the report type which this filter was created for:
    * ExecutionBreakDownRecord.OBJECT_TYPE_REPORT or
    * ExecutionBreakDownRecord.OBJECT_TYPE_VIEWSHEET
    */
   @Override
   public String getReportType() {
      String type = super.getReportType();
      return type != null ? type : table != null ? table.getReportType() : null;
   }

   /**
    * Set if need to suppport timeseries for row header.
    */
   public void setRowTimeSeries(boolean timeSeries) {
      this.rowTimeSeries = timeSeries;
   }

   /**
    * Get if need to suppport timeseries for row header.
    */
   public boolean isRowTimeSeries() {
      return rowTimeSeries;
   }

   /**
    * Set if need to suppport timeseries for col header.
    */
   public void setColTimeSeries(boolean timeSeries) {
      this.colTimeSeries = timeSeries;
   }

   /**
    * Get if need to suppport timeseries for col header.
    */
   public boolean isColTimeSeries() {
      return colTimeSeries;
   }

   /**
    * Check if headers in the same group are merged into a span cell.
    */
   public boolean isMergeSpan() {
      return mergeSpan;
   }

   @Override
   public String getDcMergePartRef() {
      return dcMergePartRef;
   }

   @Override
   public String setDcMergePartRef(String dim) {
      return this.dcMergePartRef = dim;
   }

   public void updateDcRefIndexAndStartDate(DateComparisonInfo dcInfo, String dcRefName) {
      if(dcInfo == null || dcInfo.getComparisonOption() == DateComparisonInfo.VALUE ||
         !(dcInfo.getPeriods() instanceof StandardPeriods) || Tool.isEmptyString(dcRefName) ||
         table == null)
      {
         return;
      }

      dcStartDate = dcInfo.getStartDate();

      if(dcStartDate == null) {
         return;
      }

      for(int i = 0; i < table.getColCount(); i++) {
         if(Tool.equals(table.getObject(0, i), dcRefName)) {
            dcDataRefColIndex = i;
         }
      }

      dcRefInRow = null;
      dcDataRefHeaderIndex = getRowHeaders().indexOf(dcRefName);

      if(dcDataRefHeaderIndex >= 0) {
         dcRefInRow = true;
      }
      else {
         dcDataRefHeaderIndex = getColHeaders().indexOf(dcRefName);

         if(dcDataRefHeaderIndex >= 0) {
            dcRefInRow = false;
         }
      }
   }

   /**
    * Check if 'Others' group should always be sorted as the last item.
    */
   public boolean isSortOthersLast() {
      return sortOthersLast;
   }

   /**
    * Set if 'Others' group should always be sorted as the last item.
    */
   public void setSortOthersLast(boolean sortOthersLast) {
      this.sortOthersLast = sortOthersLast;
   }

   public Formula[] getOldFormula() {
      return oldFormula;
   }

   public void setOldFormula(Formula[] oldFormula) {
      this.oldFormula = oldFormula;
   }

   public void setCalcs(List<CalcColumn> calcs, boolean calcTotal) {
      this.calcs = calcs;
      this.calcTotal = calcTotal;
   }

   /**
    * Crosstab data descriptor.
    */
   private class CrosstabDataDescriptor extends CrossFilterDataDescriptor {
      public CrosstabDataDescriptor(CrossFilter table) {
         super(table);
      }

      /**
       * Get table xmeta info.
       * @param path the specified table data path
       * @return meta info of the table data path
       */
      @Override
      public XMetaInfo getXMetaInfo(TableDataPath path) {
         if(path == null || !path.isCell() || path.getType() == TableDataPath.HEADER) {
            return null;
         }

         XMetaInfo obj = mmap.get(path);

         if(obj != null) {
            return obj;
         }
         else if(mmap.containsKey(path)) {
            return null;
         }

         TableDataPath originalPath = path;
         path = getOldPath(path);
         int type;
         String dtype = path.getDataType();
         String[] headers = path.getPath();
         String header = null;
         String oheader = null;
         Formula formula = null;
         Formula[] formulas = oldFormula == null ? sum : oldFormula;
         int col = -1;
         boolean isCalcPath = false;

         if(headers.length > 0) {
            header = headers[headers.length - 1];
            oheader = calcHeader2OHeader.get(header);

            if(calcHeaders != null) {
               for(String calcHeader : calcHeaders) {
                  if(Tool.equals(calcHeader, header)) {
                     isCalcPath = true;
                     break;
                  }
               }
            }

            col = isCalcPath ? Util.findColumn(columnIndexMap, oheader, false) :
               Util.findColumn(columnIndexMap, header, false);

            if(col == -1) {
               int idx = header.lastIndexOf('.');

               if(idx != -1) {
                  String seqsIndex = header.substring(idx + 1);

                  try {
                     Integer.parseInt(seqsIndex);
                     String header1 = header.substring(0, idx);
                     col = Util.findColumn(columnIndexMap, header1, false);

                     if(col != -1) {
                        header = header1;
                     }
                  }
                  catch(NumberFormatException ex) {
                     // do nothing
                  }
               }
            }

            if(col >= 0) {
               for(int i = 0; i < dcol.length; i++) {
                  if(dcol[i] == col) {
                     formula = i < formulas.length ? formulas[i] : null;
                     break;
                  }
               }
            }
         }

         type = TableDataPath.DETAIL;
         TableDataPath opath = header == null ? null :
            new TableDataPath(-1, type, dtype, new String[] {header});
         TableDataDescriptor desc = table.getDescriptor();
         XMetaInfo minfo = opath == null ? null : desc.getXMetaInfo(opath);

         // not found? try special path
         if(minfo == null && header != null && !DateComparisonUtil.isDCCalcDatePartRef(header)) {
            String header2 = header;
            int start = header2.indexOf('(');
            int end = header2.lastIndexOf(')');

            if(start >= 0 && end > start) {
               header2 = header2.substring(start + 1, end);
               opath = new TableDataPath(-1, type, dtype,
                  new String[] {header2});
               minfo = desc.getXMetaInfo(opath);
            }
         }

         if(isCalcPath && minfo == null && path.getType() == TableDataPath.SUMMARY) {
            opath = oheader == null ? null :
               new TableDataPath(-1, type, dtype, new String[] {oheader});
            minfo = opath == null ? null : desc.getXMetaInfo(opath);
         }

         boolean date_part = false;
         int level = 0;

         if(levels.containsKey(path)) {
            level = levels.get(path);
            date_part = (level & SortOrder.PART_DATE_GROUP) != 0;
         }

         if(minfo != null) {
            if(date_part) {
               // do not apply auto drill on date part (drill defined on date).
               minfo = minfo.clone();
               minfo.setXDrillInfo(null);
            }
            else if(formula == null && col >= 0 &&
               (path.getType() == TableDataPath.SUMMARY ||
                  path.getType() == TableDataPath.GRAND_TOTAL))
            {
               for(int j : rowh) {
                  if(col == j) {
                     // do not apply auto drill on group or grand total labels
                     minfo = minfo.clone();
                     minfo.setXDrillInfo(null);
                     break;
                  }
               }
            }
         }

         // for CrossTabFilter, SummaryFilter and GroupFilter, always use
         // created default format
         if(minfos != null && minfos.containsKey(path)) {
            minfo = Util.mergeMetaInfo(minfos.get(path), minfo, level);
         }

         if(path.getType() == TableDataPath.SUMMARY || path.getType() == TableDataPath.GRAND_TOTAL)
         {
            Util.removeIncompatibleMetaInfo(minfo, formula);
         }

         if(path.getType() == TableDataPath.SUMMARY ||
            path.getType() == TableDataPath.GRAND_TOTAL)
         {
            String[] paths = originalPath.getPath();

            if(paths != null && paths.length > 0) {
               String sumHeader = paths[paths.length - 1];
               Object[] dataHeaders = getHeaders();
               int aggrIdx = ArrayUtils.indexOf(dataHeaders, sumHeader);

               if(aggrIdx < 0) {
                  int idx = sumHeader.lastIndexOf('.');

                  if(idx != -1) {
                     String seqsIndex = sumHeader.substring(idx + 1);

                     try {
                        Integer.parseInt(seqsIndex);
                        String header1 = sumHeader.substring(0, idx);
                        aggrIdx = ArrayUtils.indexOf(dataHeaders, header1);
                     }
                     catch(NumberFormatException ex) {
                        // do nothing
                     }
                  }
               }

               if(aggrIdx < 0) {
                  for(int i = 0; i < dataHeaders.length; i++) {
                     if(Tool.equals(calcMeasureMap.get(dataHeaders[i]), sumHeader)) {
                        aggrIdx = i;
                        break;
                     }
                  }
               }

               if(aggrIdx >= 0 && calcs != null && calcs.size() != 0) {
                  int finalAggrIdx = aggrIdx;
                  Optional<CalcColumn> calcColumn = calcs.stream()
                     .filter(calc -> calc != null && calc.getColIndex() == finalAggrIdx)
                     .findFirst();

                  if(calcColumn.isPresent() && calcColumn.get() instanceof AbstractColumn &&
                     ((AbstractColumn) calcColumn.get()).isAsPercent())
                  {
                     minfo = minfo != null ? minfo.clone() : new XMetaInfo();
                     minfo.setXFormatInfo(new XFormatInfo(TableFormat.PERCENT_FORMAT, ""));
                     minfos.put(originalPath, minfo);
                  }
               }
            }
         }

         mmap.put(originalPath, minfo);

         return minfo;
      }

      /**
       * @param path  the table data path of applied calculator.
       * @return the The corresponding table data path in CrossTabFilter.
       */
      private TableDataPath getOldPath(TableDataPath path) {
         if(path == null || calcMeasureMap == null) {
            return null;
         }

         TableDataPath clone = (TableDataPath) path.clone();
         String[] paths = clone.getPath();

         for(int i = 0; i < paths.length; i++) {
            String opath = calcMeasureMap.get(paths[i]);

            if(opath != null) {
               paths[i] = opath;
            }
         }

         clone.setPath(paths);
         return clone;
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
         if(minfos == null) {
            createXMetaInfo();
         }

         if(table.containsFormat() || minfos.size() > 0 ) {
            return true;
         }

         return calcs == null ? false : calcs.stream()
            .anyMatch(calc -> calc instanceof AbstractColumn &&
               ((AbstractColumn) calc).isAsPercent());
      }

      /**
       * Check if contains drill.
       * @return <tt>true</tt> if contains drill.
       */
      @Override
      public boolean containsDrill() {
         return table.containsDrill();
      }
   }

   private static class MaxRowsException extends RuntimeException {
   }

   /**
    * For crosstab CalcColumn calculate, provide the currently available interfaces of CrosstabFilter,
    * Do not add any interface related to "data". because crosstabFilter do not process finished.
    */
   public class CrosstabDataContext {
      private CrosstabDataContext(Map<PairN, Object> data, Map<PairN, Object> totalData,
                                  Set<Tuple> pureRealRowTuples, Set<Tuple> pureRealColTuples)
      {
         dataMap = data;
         totalMap = totalData;
         rowTuples = pureRealRowTuples;
         colTuples = pureRealColTuples;
      }

      public Object getValue(PairN tuplePair) {
         Object value = null;

         if(tuplePair != null && dataMap != null) {
            value = dataMap.get(tuplePair);
         }

         if(value == null && tuplePair != null && totalMap != null) {
            value = totalMap.get(tuplePair);
         }

         value = getMergedData0(dataMap, tuplePair.getNum(), (Tuple) tuplePair.getValue1(),
            (Tuple) tuplePair.getValue2(), value, true);

         if(value == null && isFillBlankWithZero()) {
            return 0;
         }

         return value;
      }

      public Object getValue(Tuple rtuple, Tuple ctuple, int aggIndex) {
         return getValue(new PairN(rtuple, ctuple, aggIndex));
      }

      public boolean isValuePairExist(Pair tuplePair) {
         return dataMap != null && tuplePair != null && dataMap.containsKey(tuplePair);
      }

      public boolean isPairExist(Pair tuplePair) {
         return rowTuples.contains(tuplePair.value1) && colTuples.contains(tuplePair.value2);
      }

      public List<String> getRowHeaders() {
         return CrossTabFilter.this.getRowHeaders();
      }

      public List<String> getColHeaders() {
         return CrossTabFilter.this.getColHeaders();
      }

      public int getDataColCount() {
         return CrossTabFilter.this.getDataColCount();
      }

      public boolean isSummarySideBySide() {
         return CrossTabFilter.this.isSummarySideBySide();
      }

      public boolean isGrandTotalTuple(Tuple tuple) {
         return Tool.equals(tuple, emptyTuple);
      }

      public List<Object> getValues(Tuple tuple, String key, int index, boolean rowHeader) {
         return CrossTabFilter.this.getValues(tuple, key, index, rowHeader);
      }

      public boolean isOthers() {
         return CrossTabFilter.this.isOthers();
      }

      public int getRowTupleIndex(Tuple tuple) {
         return CrossTabFilter.this.getRowTupleIndex(tuple);
      }

      public int getColTupleIndex(Tuple tuple) {
         return CrossTabFilter.this.getColTupleIndex(tuple);
      }

      public Tuple getRowTupleByIndex(int index) {
         if(rvec == null || rvec.size() == 0) {
            return null;
         }

         return rvec.get(index);
      }

      public Tuple getColTupleByIndex(int index) {
         if(cvec == null || cvec.size() == 0) {
            return null;
         }

         return cvec.get(index);
      }

      public boolean isFillBlankWithZero() {
         return CrossTabFilter.this.isFillBlankWithZero();
      }

      private Map<PairN, Object> dataMap;
      private Map<PairN, Object> totalMap;
      private Set<Tuple> rowTuples;
      private Set<Tuple> colTuples;
   }

   // comparers used to compare tuples
   static final DefaultComparer defComparer = ImmutableDefaultComparer.getInstance();

   private static final Date EPIC = null;
   private static final long FIRST_SUN_1970 = 3 * 24 * 60 * 60 * 1000;
   private static final long WEEKMS = 7 * 24 * 60 * 60 * 1000;
   private static final int UNKNOW = 0;
   private static final int ROW = 1;
   private static final int COL = 2;
   private static final Map<String, Formula> formulas = new ConcurrentHashMap<>();

   private final String TOTAL = Catalog.getCatalog().getString("Total");

   private final boolean mergeSpan;
   private boolean rowTimeSeries;
   private boolean colTimeSeries;
   private boolean repeatRowLabels = false;
   private boolean fillwithzero = false;
   private boolean rowTotalOnTop = false;
   private boolean colTotalOnFirst = false;
   private boolean supRowGrandTotal = true;  // suppress row grand total
   private boolean supColGrandTotal = true;  // suppress col grand total
   private boolean supRowSubtotal = true;  // suppress row grand subtotal
   private boolean supColSubtotal = true;  // suppress col grand subtotal
   private final boolean[] supRowGroupTotal;  // suppress row group total
   private final boolean[] supColGroupTotal;  // suppress col group total
   private boolean keepheader = false; // keep column headers
   private boolean summaryHeaders = false; // add summary title row/column
   private ConditionGroup condition;
   private final Hashtable<Object, Object> i18n2headers = new Hashtable<>();
   private final Hashtable<Object, Object> headers2i18n = new Hashtable<>();
   private TableLens table;
   private Object[][] data;
   private final Formula[] sum;
   private Formula[] oldFormula;
   private FormulaAgent[] fagents;
   private int rowinc; // row increment
   private int colinc; // col increment
   private int hrowmore; // additional header rows below (summaryHeaders)
   private int hcolmore; // additional header cols to right (summaryHeaders)
   private final int[] rowh; // one or more row header columns
   private final int[] colh; // one or more column header columns
   private final int[] dcol; // one or more data columns
   private final int rcount; // row header count
   private final int ccount; // column header count
   private String[] measureHeaders; // measure names
   private List<String> rowHeaders; // names of row header fields.
   private List<String> colHeaders; // names of col header fields.
   private String[] calcHeaders;

   private final ConcurrentHashMap<Pair, Dimension> spanmap = new ConcurrentHashMap<>();
   private final ConcurrentHashMap<Pair, Rectangle> vsspanmap = new ConcurrentHashMap<>();
   private int percentageDir = StyleConstants.PERCENTAGE_BY_COL;
   private CrosstabGrid grid = null; // grid definition
   private boolean sideBySide = false; // put summary cells side by side
   // mark to be localized: Catalog.getString("Grand Total");
   private String totalLabel = "Grand Total";
   private final int rowTimeseriesLevel;
   private final int colTimeseriesLevel;

   private final Tuple emptyTuple = new Tuple();
   // optimization. The rset and cset can get very big if the distinct values
   // for row/column is large. In the case, if we use list during processing,
   // the comparison for adding a new tuple is n^2. This change uses hashset
   // during processing. After the processing is done, the tuples are copied
   // to the list for sorting and setting up the final data.
   // !!! rvec and cvec can not be used before they are created in the
   // process() function (after rset_ and cset_ are fully created).
   private List<Tuple> rvec = null; // row tuples
   private List<Tuple> cvec = null; // col tuples

   private List<Tuple> orvec = new ArrayList<>(); // row tuples without fill date gap
   private List<Tuple> ocvec = new ArrayList<>(); // col tuples without fill date gap

   // pair -> value : {row tuple, col tuple, aggr index} -> value
   private Map<Pair, Object> valueMap = new Object2ObjectOpenHashMap<>();
   private boolean forCalc = false;

   private TableDataDescriptor cdescriptor = null;
   private Comparer[] rowComparer = {defComparer}; // row header comparer
   private Comparer[] colComparer = {defComparer}; // column header comparer

   // topn filter
   private final InnerTopNInfo[] rowtopns;
   private final InnerTopNInfo[] coltopns;
   private final InnerTopNInfo[] rowSortByVals;
   private final InnerTopNInfo[] colSortByVals;
   private SectionComparer[] rscomparers;
   private SectionComparer[] cscomparers;
   private SectionComparer[] rsSortByValcomparers;
   private SectionComparer[] csSortByValcomparers;
   private Section2Comparer secComparer;
   private final Comparer[] dcomparers;
   private CrosstabSortInfo sinfo;

   private final Map<TableDataPath, XMetaInfo> mmap = new HashMap<>();
   private final Hashtable<PairN, XIntList> rnumMap = new Hashtable<>();
   private boolean aggTopN; // only aggregate topn rows
   private int[] duptimes;
   private boolean ignoreNullTotals = false;

   private SectionComparer topNAggregateComparator;
   private int topNAggregateCol = -1;
   private int topNAggregateN = 0;

   private final Map<String, String> calcHeader2OHeader = new HashMap<>();
   private final SparseMatrix totalPos = new SparseMatrix();
   protected Map<TableDataPath, XMetaInfo> minfos = null;
   protected Map<TableDataPath, Integer> levels = null;
   private transient ColumnIndexMap columnIndexMap = null;
   private String dcMergePartRef;
   private boolean sortOthersLast = true; // whether sort others last
   private int appliedMaxRows = -1;
   private int dcDataRefColIndex = -1;
   private Date dcStartDate;
   private Boolean dcRefInRow;
   private int dcDataRefHeaderIndex = -1;
   private List<CalcColumn> calcs = null;
   private boolean calcTotal;
   private Map<String, String> calcMeasureMap = new HashMap<>();
   private transient Map<Integer, CalcColumn> aggCalcMap = new HashMap<>();

   private static final Logger LOG = LoggerFactory.getLogger(CrossTabFilter.class);
}
