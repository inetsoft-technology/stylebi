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
package inetsoft.report.filter;

import inetsoft.graph.data.CalcColumn;
import inetsoft.report.*;
import inetsoft.report.composition.graph.calc.AbstractColumn;
import inetsoft.report.composition.graph.calc.PercentCalc;
import inetsoft.report.internal.Util;
import inetsoft.report.internal.binding.CrosstabSortInfo;
import inetsoft.report.internal.table.TableFormat;
import inetsoft.report.lens.AbstractTableLens;
import inetsoft.uql.XFormatInfo;
import inetsoft.uql.XMetaInfo;
import inetsoft.uql.erm.CalculateAggregate;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.graph.Calculator;
import inetsoft.util.Tool;
import org.apache.commons.lang3.ArrayUtils;

import java.awt.*;
import java.util.List;
import java.util.*;

public class CrossCalcFilter extends AbstractTableLens
   implements CrossFilter, CalcFilter, Cloneable
{
   public CrossCalcFilter(CrossTabFilter table, DataRef[] aggrs) {
      this(table, aggrs, false);
   }

   public CrossCalcFilter(CrossTabFilter table, DataRef[] aggrs, boolean isReport) {
      this(table, aggrs, isReport, false);
   }

   public CrossCalcFilter(CrossTabFilter table, DataRef[] aggrs, boolean isReport,
                          boolean calculateTotal)
   {
      this.table = table;
      this.aggrs = aggrs;
      this.isReport = isReport;
      this.calculateTotal = calculateTotal;
      initCalcColumns();
   }

   /**
    * Get the row header count.
    */
   @Override
   public int getRowHeaderCount() {
      return table.getRowHeaderCount();
   }

   /**
    * Get the column header count.
    */
   @Override
   public int getColHeaderCount() {
      return table.getColHeaderCount();
   }

   /**
    * Return the number one or more data columns
    */
   @Override
   public final int getDataColCount() {
      return table.getDataColCount();
   }

   /**
    * Set option to set the row total area on top of the group.
    * By default the row total area is below the group.
    * @param top true to set the row total on the top.
    */
   @Override
   public void setRowTotalOnTop(boolean top) {
   }

   /**
    * Check if the row total is on top.
    */
   @Override
   public boolean isRowTotalOnTop() {
      return table.isRowTotalOnTop();
   }

   /**
    * Get the span contains all cells.
    * @hidden
    */
   @Override
   public Rectangle getVSSpan(int r, int c) {
      return table.getVSSpan(r, c);
   }

   /**
    * Check if a specifield row and column is total cell.
    * @hidden
    */
   @Override
   public boolean isTotalCell(int r, int c) {
      return table.isTotalCell(r, c);
   }

   /**
    * Check if a specifield row is a total row.
    */
   @Override
   public boolean isTotalRow(int r) {
      return table.isTotalRow(r);
   }

   /**
    * Check if a specifield row is a total row.
    */
   @Override
   public boolean isTotalCol(int c) {
      return table.isTotalCol(c);
   }

   /**
    * @param r row number.
    * @param c column number.
    * @return if the target cell is a corner cell.
    */
   @Override
   public boolean isCornerCell(int r, int c) {
      return table.isCornerCell(r, c);
   }

   /**
    * Set option to set the col total area on first col of the group.
    * By default the col total area is on the last col of the group.
    * @param first true to set the col total on the first col.
    */
   @Override
   public void setColumnTotalOnFirst(boolean first) {
   }

   /**
    * Check if the col total is on first col.
    */
   @Override
   public boolean isColumnTotalOnFirst() {
      return table.isColumnTotalOnFirst();
   }

   /**
    * Set option to set if suppress the row grand total area.
    * By default the row grand total area is displayed.
    * @param sup true to set suppress the row grand total area.
    */
   @Override
   public void setSuppressRowGrandTotal(boolean sup) {
   }

   /**
    * Check if the row grand total is suppressed.
    */
   @Override
   public boolean isSuppressRowGrandTotal() {
      return table.isSuppressRowGrandTotal();
   }

   /**
    * Set option to set if suppress the col grand total area.
    * By default the col grand total area is displayed.
    * @param sup true to set suppress the col grand total area.
    */
   @Override
   public void setSuppressColumnGrandTotal(boolean sup) {
   }

   /**
    * Check if the col grand total is suppressed.
    */
   @Override
   public boolean isSuppressColumnGrandTotal() {
      return table.isSuppressColumnGrandTotal();
   }

   /**
    * Set option to set if suppress the row subtotal area.
    * By default the row subtotal area is displayed.
    * @param sup true to set suppress the row subtotal area.
    */
   @Override
   public void setSuppressRowSubtotal(boolean sup) {
   }

   /**
    * Check if the row subtotal is suppressed.
    */
   @Override
   public boolean isSuppressRowSubtotal() {
      return table.isSuppressRowSubtotal();
   }

   /**
    * Set option to set if suppress the col subtotal area.
    * By default the col subtotal area is displayed.
    * @param sup true to set suppress the col subtotal area.
    */
   @Override
   public void setSuppressColumnSubtotal(boolean sup) {
   }

   /**
    * Check if the col subtotal is suppressed.
    */
   @Override
   public boolean isSuppressColumnSubtotal() {
      return table.isSuppressColumnSubtotal();
   }

   /**
    * Set option to set if suppress the row group total area.
    * By default the row group total area is displayed.
    * @param sup true to set suppress the row group total area.
    */
   @Override
   public void setSuppressRowGroupTotal(boolean sup, int i) {
   }

   /**
    * Check if the row group total is suppressed.
    */
   @Override
   public boolean isSuppressRowGroupTotal(int i) {
      return table.isSuppressRowGrandTotal();
   }

   /**
    * Set option to set if suppress the col group total area.
    * By default the col group total area is displayed.
    * @param sup true to set suppress the col group total area.
    */
   @Override
   public void setSuppressColumnGroupTotal(boolean sup, int i) {
   }

   /**
    * Check if the col group total is suppressed.
    */
   @Override
   public boolean isSuppressColumnGroupTotal(int i) {
      return table.isSuppressColumnGrandTotal();
   }

   /**
    * Set a sort info.
    */
   @Override
   public void setSortInfo(CrosstabSortInfo sinfo) {
      table.setSortInfo(sinfo);
   }

   /**
    * Set percentage direction.
    * only two values: StyleConstants.PERCENTAGE_BY_COL,
    *                   StyleConstants.PERCENTAGE_BY_ROW.
    * the first one is default.
    */
   @Override
   public void setPercentageDirection(int percentageDir) {
   }

   /**
    * Return percentage direction.
    */
   @Override
   public int getPercentageDirection() {
      return table.getPercentageDirection();
   }

   /**
    * Set whether to ignore group total where the group contains a single value.
    * @hidden
    */
   @Override
   public void setIgnoreNullTotals(boolean flag) {
   }

   /**
    * Check whether to ignore group total where the group contains a single value.
    * @hidden
    */
   @Override
   public boolean isIgnoreNullTotals() {
      return table.isIgnoreNullTotals();
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
      return table.isHeaderCell(row, col);
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
      return table.isDataCell(row, col);
   }

   /**
    * @return calc headers of the crossfilter.
    */
   @Override
   public String[] getCalcHeaders() {
      return table.getCalcHeaders();
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
      return ((CrossCalcDataDescriptor) getDescriptor()).getKeyValuePairs(row, col, map);
   }

   /**
    * @return the aggregate headers.
    */
   @Override
   public Object[] getHeaders() {
      int[] dcol = table.getDataIndexes();
      Object[] titles = new Object[dcol.length];

      for(int i = 0; i < titles.length; i++) {
         titles[i] = getDataHeader(getCalcHeader(i), dcol[i], i);
      }

      return titles;
   }

   @Override
   public boolean moreRows(int row) {
      return table.moreRows(row);
   }

   @Override
   public TableDataDescriptor getDescriptor() {
      if(descriptor == null) {
         descriptor = new CrossCalcDataDescriptor(this);
      }

      return descriptor;
   }

   @Override
   public int getHeaderColCount() {
      return table.getHeaderColCount();
   }

   @Override
   public int getHeaderRowCount() {
      return table.getHeaderRowCount();
   }

   @Override
   public int getRowHeight(int row) {
      return table.getRowHeight(row);
   }

   @Override
   public int getColWidth(int col) {
      return table.getColWidth(col);
   }

   @Override
   public int getTrailerRowCount() {
      return table.getTrailerRowCount();
   }

   @Override
   public int getTrailerColCount() {
      return table.getTrailerColCount();
   }

   @Override
   public Dimension getSpan(int r, int c) {
      return table.getSpan(r, c);
   }

   @Override
   public synchronized Class<?> getColType(int col) {
      return table.getColType(col);
   }

   @Override
   public Color getRowBorderColor(int r, int c) {
      return table.getRowBorderColor(r, c);
   }

   @Override
   public Color getColBorderColor(int r, int c) {
      return table.getColBorderColor(r, c);
   }

   @Override
   public int getRowBorder(int r, int c) {
      return table.getRowBorder(r, c);
   }

   @Override
   public int getColBorder(int r, int c) {
      return table.getColBorder(r, c);
   }

   @Override
   public int getAlignment(int r, int c) {
      return table.getAlignment(r, c);
   }

   @Override
   public Font getFont(int r, int c) {
      return table.getFont(r, c);
   }

   @Override
   public boolean isLineWrap(int r, int c) {
      return table.isLineWrap(r, c);
   }

   @Override
   public Color getForeground(int r, int c) {
      return table.getForeground(r, c);
   }

   @Override
   public Color getBackground(int r, int c) {
      return table.getBackground(r, c);
   }

   @Override
   public Color getBackground(int r, int c, int spanRow) {
      return table.getBackground(r, c, spanRow);
   }

   @Override
   public String getColumnIdentifier(int col) {
      return table.getColumnIdentifier(col);
   }

   @Override
   public void setColumnIdentifier(int col, String identifier) {
      table.setColumnIdentifier(col, identifier);
   }

   @Override
   public String getReportName() {
      return table.getReportName();
   }

   @Override
   public String getReportType() {
      return table.getReportType();
   }

   @Override
   public void setObject(int r, int c, Object v) {
      if(data != null && r < data.length && c < data[0].length) {
         data[r][c] = v;
      }
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
      if(table instanceof CrossTabFilter) {
         this.table = (CrossTabFilter) table;
         invalidate();
         this.table.addChangeListener(new DefaultTableChangeListener(this));
      }
   }

   /**
    * Get the base table.
    */
   @Override
   public TableLens getTable() {
      return table;
   }

   @Override
   public void dispose() {
      table.dispose();
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
    * Return the number of rows in the table. The number of rows includes
    * the header rows.
    * @return number of rows in table.
    */
   @Override
   public int getRowCount() {
      return table.getRowCount();
   }

   /**
    * Set the measure names to table.
    */
   @Override
   public void setMeasureNames(String[] names) {
      // not used
   }

   @Override
   public List<String> getMeasureHeaders() {
      return new ArrayList<>();
   }

   /**
    * Get the base table row index corresponding to the filtered table.
    * If the row does not exist in the base table, it returns -1.
    * @param row row index in the filtered table.
    * @return corresponding row index in the base table.
    */
   @Override
   public int getBaseRowIndex(int row) {
      return row < table.getRowCount() ? row : -1;
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
    * Invalidate the table filter forcely, and the table filter will perform
    * filtering calculation to validate itself.
    */
   @Override
   public void invalidate() {
      data = new Object[getRowCount()][getColCount()];
      table.invalidate();
      clearCalcColumns();
      clearCalcValues();
   }

   /**
    * Get grand total label.
    */
   @Override
   public String getGrandTotalLabel() {
      return table.getGrandTotalLabel();
   }

   /**
    * Set the label string for total columns and rows.
    */
   @Override
   public void setTotalLabel(String label) {
   }

   /**
    * If this option is true, and there are multiple summary cells, they are
    * arranged side by side in the table. Otherwise they are arranged vertically.
    * Defaults to false.
    */
   @Override
   public void setSummarySideBySide(boolean horizontal) {
   }

   /**
    * Check if summary cells are put side by side.
    */
   @Override
   public boolean isSummarySideBySide() {
      return table.isSummarySideBySide();
   }

   /**
    * Set whether to add summary title row/column. If true, a title row is added
    * to the header rows for summary if summary side by side is on. Otherwise,
    * a title column is added to the header columns if summary side by side is
    * off.
    */
   public void setShowSummaryHeaders(boolean sumTitle) {
   }

   /**
    * Check whether to add summary title row/column.
    */
   public boolean isShowSummaryHeaders() {
      return table.isShowSummaryHeaders();
   }

   /**
    * Set option to repeat row labels. By default the cells for the same group
    * is merged into a single cell. If the repeatRowLabels is set to true, the
    * cells are still displayed individually.
    * @param repeat true to repeat row label.
    */
   public void setRepeatRowLabels(boolean repeat) {
   }

   /**
    * Check if the row label are repeated.
    */
   public boolean isRepeatRowLabels() {
      return table.isRepeatRowLabels();
   }

   /**
    * Test if the cell is a grandtotal cell.
    *
    * @param row the cell's row
    * @param col the cell's col
    * @return <code>true</code> if the cell is a grandtotal cell,
    * <code>false</code> otherwise
    */
   public boolean isGrandTotalCell(int row, int col) {
      return table.isGrandTotalCell(row, col);
   }

   /**
    * Get available fields of a crosstab cell.
    *
    * @param row the specified row
    * @param col the specified col
    */
   public String[] getAvailableFields(int row, int col) {
      return ((CrossCalcDataDescriptor) getDescriptor()).getAvailableFields(row, col);
   }

   /**
    * Get the comparer used for comparison of row header values.
    */
   public Comparer[] getRowComparers() {
      return table.getRowComparers();
   }

   /**
    * Get the comparer used for comparison of column header values.
    */
   public Comparer[] getColComparers() {
      return table.getColComparers();
   }

   /**
    * @param c  the column index in the table.
    * @return   the value index for the aggregate object array in a special row.
    */
   private int getValueIndex(int c) {
      if(table == null) {
         return -1;
      }

      int hccount = table.getHeaderColCount();
      int dcount = table.getDataColCount();

      c -= hccount;

      if(!table.isSuppressColumnGrandTotal() && table.isColumnTotalOnFirst()) {
         c -= isSummarySideBySide() ? dcount : 1;
      }

      return table.isSummarySideBySide() ? c / dcount : c;
   }

   /**
    * Make sure crosstab is initialized.
    */
   public void checkInit() {
      if(!this.validCalc && calcVals == null) {
         synchronized(this) {
            if(!this.validCalc && calcVals == null) {
               prepareCalc();
            }
         }
      }
   }

   /**
    * @return if the target cell is can summary value cell.
    */
   private boolean isSummaryData(int r, int c) {
      return r >= getHeaderRowCount() && c >= getHeaderColCount();
   }

   /**
    * Return the value at the specified cell.
    * @param r row number.
    * @param c column number.
    * @return the value at the location.
    */
   @Override
   public Object getObject(int r, int c) {
      if(data == null) {
         data = new Object[getRowCount()][getColCount()];
      }

      if(table == null || r >= getRowCount() || c >= getColCount()) {
         return null;
      }

      Object val = data[r][c];

      if(val != null) {
         return Tool.equals(val, NULL) ? null : val;
      }

      val = getObject0(r, c);
      data[r][c] = val == null ? NULL : val;

      return val;
   }

   /**
    * Return the value at the specified cell.
    * @param r row number.
    * @param c column number.
    * @return the value at the location.
    */
   private Object getObject0(int r, int c) {
      if(hasNoCalcs() && !hasPercentCalc) {
         return table.getObject(r, c);
      }

      if(!this.validCalc) {
         prepareCalc();
      }

      // 1.total cell not changed (Grand Total\Total)
      // 2.summary data for grand total\sub total row not changed, because computation
      // grand total and sub total data base on the result of calcuators has no meaning)
      if(table.isTotalCell(r, c) || !calculateTotal && isSummaryData(r, c) &&
         (table.isGrandTotalCell(r, c) || table.isTotalRow(r) || table.isTotalCol(c)))
      {
         return table.getObject(r, c);
      }

      int hccount = getHeaderColCount();
      int hrcount = getHeaderRowCount();
      int ccount = table.getColHeaders().size();
      boolean colTotalOnFirst = table.isColumnTotalOnFirst();
      boolean showColGrandTotal = !table.isSuppressColumnGrandTotal();
      boolean showSummaryHeader = table.isShowSummaryHeaders();

      if(ccount == 0) {
         // fix right top summary header.
         if(r == 0 && ((!showColGrandTotal || !colTotalOnFirst) && c == hccount ||
            showColGrandTotal && colTotalOnFirst && c == hccount + 1))
         {
            if(isReport) {
               return table.getObject(r, c);
            }

            if(aggrs.length == 0) {
               return getAggrName(0, table.getObject(r, c));
            }

            if(!table.isSummarySideBySide()) {
               return getMergedAggrsHeader(table.getObject(r, c));
            }
            else {
               return getAggrName((c - hccount) % aggrs.length, table.getObject(r, c));
            }
         }

         // fix left bottom summary headers.
         if(showSummaryHeader && !table.isSummarySideBySide() && r >= hrcount && c == hccount - 1) {
            return getAggrName((r - hrcount) % aggrs.length, table.getObject(r, c));
         }
      }

      if(showSummaryHeader) {
         // fix right top summary header.
         if(table.isSummarySideBySide() && r == hrcount - 1 && c >= hccount) {
            return getAggrName((c - hccount) % aggrs.length, table.getObject(r, c));
         }

         // fix left bottom summary headers.
         if(!table.isSummarySideBySide() && r >= hrcount && c == hccount - 1) {
            return getAggrName((r - hrcount) % aggrs.length, table.getObject(r, c));
         }
      }

      // fix summary headers when no row header.
      if(!isReport && table.getRowHeaders().size() == 0 && r == hrcount && c == 0) {
         if(aggrs.length == 1) {
            return getAggrName(0, table.getObject(r, c));
         }

         if(table.isSummarySideBySide()) {
            return getMergedAggrsHeader(table.getObject(r, c));
         }
         else {
            return getAggrName((r - hrcount) % aggrs.length, table.getObject(r, c));
         }
      }

      if(!isSummaryData(r, c) || calcVals == null) {
         return table.getObject(r, c);
      }

      int calcCol = getCalcColIndex(r, c);

      if(calcCol == -1) {
         return table.getObject(r, c);
      }

      int aggrIdx = calcs.get(calcCol).getColIndex();

      if(aggrIdx == -1 || aggrIdx > aggrs.length) {
         return table.getObject(r, c);
      }

      // return calc result.
      if(r < calcVals.size()) {
         Object value = calcVals.get(r)[calcCol];

         if(value instanceof Object[]) {
            Object[] values = (Object[]) value;
            int idx = getValueIndex(c);

            if(idx < values.length) {
               final Object retValue;

               if(values[idx] == CalcColumn.INVALID) {
                  retValue = null;
               }
               else {
                  retValue = values[idx];
               }

               return retValue == null ? isFillBlankWithZero() ? 0d : null : retValue;
            }
         }
      }

      return table.getObject(r, c);
   }

   /**
    * @param oldHeader the orignal merged headers.
    * @return merged aggregates header which replaced by the headers with calcuator prefix.
    */
   private Object getMergedAggrsHeader(Object oldHeader) {
      if(isReport) {
         return oldHeader;
      }

      StringBuilder header = new StringBuilder();

      for(int i = 0; i < aggrs.length; i++) {
         Object aggrName = getAggrName(i, null);

         if(aggrName == null) {
            return oldHeader;
         }

         header.append(i == 0 ? "" : "/").append(aggrName);
      }

      return header.toString();
   }

   /**
    * Return aggregate name with calc.
    * @param idx      the aggregate index.
    * @param oname    the old aggregate name.
    */
   private Object getAggrName(int idx, Object oname) {
      if(isReport || aggrs == null || idx == -1 || idx > aggrs.length ||
         !(aggrs[idx] instanceof CalculateAggregate))
      {
         return oname;
      }

      return CrossTabFilterUtil.getCrosstabRTAggregateName((CalculateAggregate) aggrs[idx], true);
   }

   /**
    * @param r  the row index in the table lens.
    * @param c  the column index in the table lens.
    * @return the calculate index in the calculate list.
    */
   private int getCalcColIndex(int r, int c) {
      if(calcs == null || table == null) {
         return -1;
      }

      c = CrossTabFilterUtil.getAggregateIndex(table, r, c);

      for(int i = 0; i < calcs.size(); i++) {
         if(calcs.get(i).getColIndex() == c) {
            return i;
         }
      }

      return -1;
   }

   public CalcColumn getCalcColumn(int r, int c) {
      if(calcs == null || table == null) {
         return null;
      }

      c = CrossTabFilterUtil.getAggregateIndex(table, r, c);

      for(int i = 0; i < calcs.size(); i++) {
         if(calcs.get(i).getColIndex() == c) {
            return calcs.get(i);
         }
      }

      return null;
   }

   /**
    * Init calcs.
    */
   private void initCalcColumns() {
      if(table == null) {
         return;
      }

      table.moreRows(TableLens.EOT);

      for(int i = 0; i < aggrs.length; i++) {
         DataRef dataRef = aggrs[i];

         if(!(dataRef instanceof CalculateAggregate)) {
            continue;
         }

         CalculateAggregate aggr = (CalculateAggregate) dataRef;
         Calculator calculator = aggr.getCalculator();

         if(calculator instanceof PercentCalc) {
            hasPercentCalc = true;
         }
         // calculate percent calc is too expensive when grand total\group total is not visible,
         // so just use the crosstab old percent of logic to do the percentcalc.
         if(calculator != null) {
            String field = CrossTabFilterUtil.getCrosstabRTAggregateName(aggr, false);
            CalcColumn calc = calculator.createCalcColumn(field);

            if(calc instanceof AbstractColumn) {
               ((AbstractColumn) calc).setCalculateTotal(calculateTotal);
            }

            calc.setColIndex(i);
            addCalcColumn(calc);
         }
      }

      // Todo set the date comparison calc.
   }

   private boolean hasNoCalcs() {
      return this.calcs == null || this.calcs.size() <= 0;
   }

   /**
    * Prepare the calculate data.
    */
   public synchronized void prepareCalc() {
      if(hasNoCalcs()) {
         validCalc = true;
         table.clearValueMap();
         return;
      }

      boolean ovalidCalc = this.validCalc;
      this.validCalc = false;
      boolean processed = false;

      if(this.calcVals == null) {
         processed = true;
         prepareCalcCol();
      }

      table.clearValueMap();

      if(!processed) {
         this.validCalc = ovalidCalc;
      }
   }

   private void prepareCalcCol() {
      Vector<Object[]> _calcVals = this.calcVals;

      if(_calcVals == null) {
         _calcVals = new Vector<>();
      }

      int rowCount = getRowCount();
      _calcVals.setSize(rowCount);
      CalcColumn[] calcs = this.calcs == null ?
         new CalcColumn[0] : this.calcs.toArray(new CalcColumn[0]);
      CalcColumn[] postCalcs = new CalcColumn[calcs.length];

      boolean hasPostCalc = false;
      Set<String> cnames = new HashSet<>();

      for(CalcColumn calcColumn : calcs) {
         cnames.add(calcColumn.getHeader());
      }

      for(int i = calcs.length - 1; i >= 0; --i) {
         CalcColumn calc = calcs[i];
         String field = calc.getField();

         if(field != null && cnames.contains(field)) {
            calcs[i] = null;
            postCalcs[i] = calc;
            hasPostCalc = true;
         }
      }

      for(int i  = 0; i < rowCount; ++i) {
         Object[] row = new Object[calcs.length];

         for(int j = 0; j < calcs.length; ++j) {
            if(calcs[j] == null) {
               continue;
            }

            row[j] = calcs[j].calculate(table, i, i == 0, i == rowCount - 1);

            if(row[j] == CalcColumn.INVALID) {
               row[j] = null;
            }
            else {
               validCalc = true;
            }
         }

         _calcVals.set(i, row);
      }

      for(CalcColumn calc : calcs) {
         if(calc != null) {
            calc.complete();
         }
      }

      if(hasPostCalc) {
         for(int i = 0; i < rowCount; ++i) {
            Object[] row = _calcVals.get(i);

            for(int j = 0; j < postCalcs.length; ++j) {
               if(postCalcs[j] == null) {
                  continue;
               }

               row[j] = postCalcs[j].calculate(table, i, i == 0, i == rowCount - 1);

               if(row[j] == CalcColumn.INVALID) {
                  row[j] = null;
               }
               else {
                  this.validCalc = true;
               }
            }
         }

         for(CalcColumn postCalc : postCalcs) {
            if(postCalc != null) {
               postCalc.complete();
            }
         }
      }

      this.calcVals = _calcVals;
   }

   public synchronized void addCalcColumn(CalcColumn col) {
      if(calcs == null) {
         calcs = new Vector<>();
      }

      for(int i = 0; i < calcs.size(); ++i) {
         if(Tool.equals(calcs.get(i), col.getHeader())) {
            this.calcs.set(i, col);
            return;
         }
      }

      calcs.add(col);
      clearCalcValues();
   }

   public synchronized void clearCalcColumns() {
      this.calcs = null;
   }

   public synchronized void clearCalcValues() {
      this.calcVals = null;
   }

   /**
    * Get header at an index in base table.
    * @param aggrIndex the specified aggregate index.
    * @return header at the specified index in base table
    */
   public String getCalcHeader(int aggrIndex) {
      if(isReport || aggrs == null || aggrIndex >= aggrs.length ||
         !(aggrs[aggrIndex] instanceof CalculateAggregate))
      {
         return null;
      }

      CalculateAggregate aggr = (CalculateAggregate) aggrs[aggrIndex];
      Calculator calculator = aggr.getCalculator();

      if(calculator != null) {
         String field = CrossTabFilterUtil.getCrosstabRTAggregateName(aggr, false);
         return calculator.getPrefix() + field;
      }

      return null;
   }

   /**
    * Get data header name.
    */
   @Override
   public String getDataHeader(int idx) {
      int[] dcol = table.getDataIndexes();
      return getDataHeader(getCalcHeader(idx), dcol[idx], idx);
   }

   /**
    * Get header at an index in base table.
    * @param index the specified data index
    * @return header at the specified index in base table
    */
   public String getDataHeader(String applyCalcHeader, int index, int didx) {
      String header = applyCalcHeader;

      if(header == null) {
         return table.getHeader(index, didx);
      }

      int[] duptimes = table.getDuptimes();

      if(didx != -1 && duptimes != null && duptimes[didx] > 0) {
         header = (String) Util.getDupHeader(header, duptimes[didx]);
      }

      if(dataHeaderMap.get(header) == null) {
         dataHeaderMap.put(header, table.getHeader(index, didx));
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
      if(isDataCol(index)) {
         return getDataHeader(getCalcHeader(index), index, didx);
      }

      return table.getHeader(index, didx);
   }

   /**
    * @param idx the target col index in base table.
    * @return if the target col is aggregate column.
    */
   private boolean isDataCol(int idx) {
      int[] dcol = getDataIndexes();

      if(dcol == null || dcol.length == 0) {
         return false;
      }

      for(int j : dcol) {
         if(j == idx) {
            return true;
         }
      }

      return false;
   }

   /**
    * @return the array contains indexes in base tablelens for row headers.
    */
   @Override
   public int[] getRowHeaderIndexes() {
      return table.getRowHeaderIndexes();
   }

   /**
    * @return the array contains indexes in base tablelens for col headers.
    */
   @Override
   public int[] getColHeaderIndexes() {
      return table.getColHeaderIndexes();
   }

   /**
    * @return the column indexes array for aggregates.
    */
   @Override
   public int[] getDataIndexes() {
      return table.getDataIndexes();
   }

   /**
    * Get row header name.
    */
   @Override
   public String getRowHeader(int idx) {
      int[] rowh = getRowHeaderIndexes();
      return rowh[0] == -1 ? null : getHeader(rowh[idx], 100 + idx);
   }

   /**
    * Get col header name.
    */
   @Override
   public String getColHeader(int idx) {
      int[] rowh = getRowHeaderIndexes();
      int[] colh = getColHeaderIndexes();
      return colh[0] == -1 ? null : getHeader(colh[idx], 100 + rowh.length + idx);
   }

   /**
    * @param row the specific row of the crossfilter.
    * @return  the target row tuple of the crossfilter.
    */
   @Override
   public Tuple getRowTuple(int row) {
      return table.getRowTuple(row);
   }

   /**
    * @param col the specific col of the crossfilter.
    * @return  the target col tuple of the crossfilter.
    */
   @Override
   public Tuple getColTuple(int col) {
      return table.getColTuple(col);
   }

   /**
    * @return the header mapping.
    */
   @Override
   public Hashtable<Object, Object> getHeaderMaps() {
      return table.getHeaderMaps();
   }

   public boolean isFillBlankWithZero() {
      return fillBlankWithZero;
   }

   public void setFillBlankWithZero(boolean fillBlankWithZero) {
      this.fillBlankWithZero = fillBlankWithZero;
   }

   public List<CalcColumn> getCalcColumns() {
      return calcs;
   }

   /**
    * Check whether have the comparison result.
    *
    * @return
    */
   public boolean hasComparisonValues() {
      if(calcVals == null || calcVals.size() == 0) {
         return false;
      }

      for(Object[] values : calcVals) {
         if(values == null) {
            continue;
         }

         for(Object row : values) {
            if(!(row instanceof Object[])) {
               continue;
            }

            for(Object value : (Object[]) row) {
               if(value != null && value != CalcColumn.INVALID) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   @Override
   public String getDcMergePartRef() {
      return table.getDcMergePartRef();
   }

   @Override
   public String setDcMergePartRef(String dim) {
      return table.setDcMergePartRef(dim);
   }

   /**
    * Crosstab data descriptor.
    */
   private class CrossCalcDataDescriptor extends CrossFilterDataDescriptor {
      public CrossCalcDataDescriptor(CrossFilter table) {
         super(table);
      }

      /**
       * CrossCalcFilter don't save xmetainfo, so get/set from CrossTabFilter directly,
       * which means should use the path of CrossTabFilter
       *
       * Get table xmeta info.
       * @param path the specified table data path
       * @return meta info of the table data path
       */
      @Override
      public XMetaInfo getXMetaInfo(TableDataPath path) {
         if(mmap.containsKey(path)) {
            return mmap.get(path);
         }

         XMetaInfo meta = table.getDescriptor().getXMetaInfo(getOldPath(path));

         if(path.getType() == TableDataPath.SUMMARY ||
            path.getType() == TableDataPath.GRAND_TOTAL)
         {
            String[] paths = path.getPath();

            if(paths != null && paths.length > 0) {
               String sumHeader = paths[paths.length - 1];
               int aggrIdx = ArrayUtils.indexOf(getHeaders(), sumHeader);

               if(aggrIdx >= 0 && aggrs[aggrIdx] instanceof CalculateAggregate) {
                  Calculator calc = ((CalculateAggregate) aggrs[aggrIdx]).getCalculator();

                  if(calc != null && calc.isPercent()) {
                     meta = meta != null ? meta.clone() : new XMetaInfo();
                     meta.setXFormatInfo(new XFormatInfo(TableFormat.PERCENT_FORMAT, ""));
                  }
               }
            }
         }

         mmap.put(path, meta);
         return meta;
      }

      /**
       * @param path  the table data path of CrossCalcFilter.
       * @return the The corresponding table data path in CrossTabFilter.
       */
      private TableDataPath getOldPath(TableDataPath path) {
         if(path == null) {
            return null;
         }

         TableDataPath clone = (TableDataPath) path.clone();
         String[] paths = clone.getPath();

         for(int i = 0; i < paths.length; i++) {
            String opath = dataHeaderMap.get(paths[i]);

            if(opath != null) {
               paths[i] = opath;
            }
         }

         clone.setPath(paths);
         return clone;
      }

      @Override
      public List<TableDataPath> getXMetaInfoPaths() {
         return table.getDescriptor().getXMetaInfoPaths();
      }

      /**
       * Check if contains format.
       * @return true if contains format
       */
      @Override
      public boolean containsFormat() {
         return table.containsFormat() || containsPercentCalculator();
      }

      private boolean containsPercentCalculator() {
         if(aggrs == null) {
            return false;
         }

         return Arrays.stream(aggrs)
            .filter(CalculateAggregate.class::isInstance)
            .map(CalculateAggregate.class::cast)
            .map(CalculateAggregate::getCalculator)
            .anyMatch(calc -> calc != null && calc.isPercent());
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

   private final DataRef[] aggrs;
   private CrossTabFilter table;
   private List<CalcColumn> calcs = null;
   private Vector<Object[]> calcVals;
   private boolean validCalc = false;
   private boolean hasPercentCalc = false;
   private boolean isReport;
   private TableDataDescriptor descriptor;
   private final HashMap<String, String> dataHeaderMap = new HashMap<>();
   private final Map<TableDataPath, XMetaInfo> mmap = new HashMap<>();
   private Object[][] data;
   private boolean fillBlankWithZero = false;
   private boolean calculateTotal = false;
   private final Object NULL = 4.9E-324D;
}
