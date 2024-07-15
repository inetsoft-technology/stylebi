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

import inetsoft.report.*;
import inetsoft.report.internal.TimeSeriesUtil;
import inetsoft.report.internal.Util;
import inetsoft.report.internal.binding.OrderInfo;
import inetsoft.report.internal.table.CancellableTableLens;
import inetsoft.report.internal.table.TableFormat;
import inetsoft.uql.*;
import inetsoft.uql.asset.internal.ColumnIndexMap;
import inetsoft.uql.table.XSwappableTable;
import inetsoft.uql.util.XUtil;
import inetsoft.util.*;
import inetsoft.util.audit.ExecutionBreakDownRecord;
import inetsoft.util.profile.ProfileUtils;
import inetsoft.util.script.JavaScriptEngine;
import inetsoft.util.script.ScriptException;
import inetsoft.util.swap.XSwappableObjectList;
import inetsoft.util.swap.XSwapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.Serializable;
import java.text.MessageFormat;
import java.text.*;
import java.util.List;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * SummaryFilter is a special GroupFilter where only summary rows are
 * displayed. The details rows are omitted.
 *
 * @version 6.0
 * @author InetSoft Technology Corp
 */
public class SummaryFilter extends AbstractGroupedTable
   implements TableFilter, SortedTable, CalcFilter, Cloneable, CancellableTableLens
{
   /**
    * Create a SummaryFilter. The sorting columns in the sorted table are
    * used for grouping.
    * @param table sorted table.
    * @param sum summary column.
    * @param calc summary formula.
    * @param grand grand total formula.
    */
   public SummaryFilter(SortedTable table, int sum, Formula calc, Formula grand) {
      this(table, new int[] {sum}, new Formula[] {calc},
           grand != null ? new Formula[] {grand} : null);
   }

   /**
    * Create a SummaryFilter. The sorting columns in the sorted table are
    * used for summarying.
    * @param table sorted table.
    * @param sums summary columns.
    * @param calc summary formula.
    * @param grand grand total formula.
    */
   public SummaryFilter(SortedTable table, int[] sums, Formula calc, Formula grand) {
      this(table, table.getSortCols(), sums, calc, grand);
   }

   /**
    * Create a SummaryFilter. The sorting columns in the sorted table are
    * used for summarying.
    * @param table base table.
    * @param sums summary columns.
    * @param calc summary formula.
    * @param grand grand total formula.
    */
   public SummaryFilter(TableLens table, int[] groupcols, int[] sums,
                        Formula calc, Formula grand) {
      this(table, groupcols, sums, (Formula[]) null, null);
      this.calcs = new Formula[sums.length];

      if(calc != null) {
         for(int i = 0; i < calcs.length; i++) {
            try {
               calcs[i] = (Formula) calc.clone();
            }
            catch(Exception ex) {
               LOG.error("Failed to set formula for summary column " + sums[i], ex);
            }
         }
      }

      this.hasGrand = grand != null;
      this.grand = new Formula[sums.length];

      // @by larryl, we always calculate grand total even if grand total is
      // not shown, so the grand total based percentage can be supported
      if(grand == null) {
         grand = calc;
      }

      if(grand != null) {
         for(int i = 0; i < sums.length; i++) {
            try {
               this.grand[i] = (Formula) grand.clone();
            }
            catch(Exception ex) {
               LOG.error("Failed to set grand total formula for summary column " +
                  sums[i], ex);
            }
         }
      }
   }

   /**
    * Create a SummaryFilter. The sorting columns in the sorted table are
    * used for summarying.
    * @param table base table.
    * @param sums summary columns.
    * @param calcs summary formula.
    * @param grand grand total formula.
    */
   public SummaryFilter(TableLens table, int[] groupcols, int[] sums,
                        Formula[] calcs, Formula[] grand) {
      this(table, groupcols, sums, calcs, grand, false);
   }

   /**
    * Create a SummaryFilter. The sorting columns in the sorted table are
    * used for summarying.
    * @param table base table.
    * @param sums summary columns.
    * @param calcs summary formula.
    * @param grand grand total formula.
    * @param hierarchy true if keep the group hierarchy, default is false.
    */
   public SummaryFilter(TableLens table, int[] groupcols, int[] sums,
                        Formula[] calcs, Formula[] grand, boolean hierarchy) {
      this(table, groupcols, sums, calcs, grand, hierarchy, 0);
   }

   /**
    * Create a SummaryFilter. The sorting columns in the sorted table are
    * used for summarying.
    * @param table base table.
    * @param sums summary columns.
    * @param calcs summary formula.
    * @param grand grand total formula.
    * @param hierarchy true if keep the group hierarchy, default is false.
    * @param pglvl percent by group level.
    */
   public SummaryFilter(TableLens table, int[] groupcols, int[] sums,
                        Formula[] calcs, Formula[] grand,
                        boolean hierarchy, int pglvl)
   {
      this.sums = sums;
      this.cols = groupcols.clone();
      setTable(table);
      this.hierarchy = hierarchy;
      this.calcs = cloneFormulas(calcs);
      this.hasGrand = grand != null;
      this.grand = cloneFormulas((grand == null) ? calcs : grand);
      this.pglvl = pglvl;
   }

   /**
    * Create a SummaryFilter. The sorting columns in the sorted table are
    * used for grouping.
    * @param table sorted table.
    * @param sums summary columns.
    * @param calcs summary formulas.
    * @param grand grand total formulas.
    * @param headers the column headers for the summary columns. If not
    * specified, the column header of the summarized column is used.
    */
   public SummaryFilter(SortedTable table, int[] sums, Formula[] calcs,
                        Formula[] grand, String[] headers) {
      this(table, sums, calcs, grand);
      this.headers = headers;
   }

   /**
    * Create a SummaryFilter. The sorting columns in the sorted table are
    * used for grouping.
    * @param table sorted table.
    * @param sums summary columns.
    * @param calcs summary formulas.
    * @param grand grand total formulas.
    */
   public SummaryFilter(SortedTable table, int[] sums, Formula[] calcs, Formula[] grand) {
      this(table, table.getSortCols(), sums, calcs, grand);
   }

   // GroupedTable API

   /**
    * Return the number of grouping columns. Multiple column group is
    * counted as one group column.
    */
   @Override
   public int getGroupColCount() {
      return cols.length - 1;
   }

   /**
    * Check if a row is displaying group header.
    * @param r row number.
    */
   @Override
   public boolean isGroupHeaderRow(int r) {
      return false;
   }

   /**
    * Check if a cell is a group header cell. This is more accurate than
    * the isGroupHeaderRow() because it takes into consideration of the
    * in-place header rows (where it's partially a header and body).
    * @param r row number.
    * @param c column number.
    * @return true if the cell is a group header cell.
    */
   @Override
   public boolean isGroupHeaderCell(int r, int c) {
      return r >= hcount && c < cols.length;
   }

   /**
    * Get the number of columns used in the specified grouping level.
    * This is normally 1 unless multiple columns are combined into
    * one group.
    */
   @Override
   public int getGroupColumns(int level) {
      return 1;
   }

   /**
    * Get the grouping level of this group header. The row number must
    * refer to a header row.
    * @return grouping level. The top-most group is level 0. Returns -1
    * if the row is not a header row.
    */
   @Override
   public int getGroupLevel(int r) {
      return grpset.get(r) ? 0 : -1;
   }

   /**
    * Check if the group column contents are shown. This is true
    * by default. If it's false, the group columns are hidden.
    * @return true if group columns are shown.
    */
   @Override
   public boolean isShowGroupColumns() {
      return true;
   }

   /**
    * Set the show group column contents option. If it's turned off,
    * the grouped columns will have
    * empty contents. If the ShowGroupColumns is set to false,
    * the AddGroupHeader is automatically turned on.
    * @param grp show group column contents.
    */
   @Override
   public void setShowGroupColumns(boolean grp) {
      // always true
   }

   /**
    * Check if group header is to be added to the grouped data.
    * @return true if group header is added. Default to false.
    */
   @Override
   public boolean isAddGroupHeader() {
      return false;
   }

   /**
    * Set whether group headers are added to the table. Group headers
    * are separate rows containing only the group column value for the
    * section.
    */
   @Override
   public void setAddGroupHeader(boolean h) {
      // always false
   }

   /**
    * Get the group header style.
    * @return group header style.
    */
   @Override
   public int getGroupHeaderStyle() {
      return GROUP_HEADER_IN_PLACE;
   }

   /**
    * Set the group header style. This must be called before the refresh()
    * is called.
    * @param headerS one of GROUP_HEADER_IN_PLACE, GROUP_HEADER_ROWS (default).
    */
   @Override
   public void setGroupHeaderStyle(int headerS) {
      // fixed
   }

   /**
    * Check if a row is a summary row.
    * @param row the row number.
    * @return true if the row is a summary row.
    */
   @Override
   public boolean isSummaryRow(int row) {
      return row >= hcount;
   }

   /**
    * Check if a column is a summary column.
    * @param col the column number.
    * @return true if the column is a summary column.
    */
   @Override
   public boolean isSummaryCol(int col) {
      return col >= cols.length;
   }

   /**
    * Get summary level of a row.
    * @param row the specified row.
    * @return summary level of the specified row.
    */
   @Override
   public int getSummaryLevel(int row) {
      Integer iobj = groupmap.get(row);

      if(iobj != null) {
         return iobj;
      }

      // if header row or grand total row
      if(row < hcount || hasGrand && !moreRows(row + 1)) {
         return -1;
      }

      return -1;
   }

   /**
    * Check if this table contains grand summary row.
    * @return true if grand summary row exists.
    */
   @Override
   public boolean hasGrandSummary() {
      return hasGrand;
   }

   /**
    * Get the group formula of the column.
    * @param col column index in the grouped table.
    * @return the group formula of column
    */
   @Override
   public Formula getGroupFormula(int col) {
      col -= cols.length;
      return (col >= 0 && col < calcs.length) ? calcs[col] : null;
   }

   /**
    * Get the grand total formula of the column.
    * @param col column index in the grouped table.
    * @return the grand total formula of column
    */
   @Override
   public Formula getGrandFormula(int col) {
      if(grand == null) {
         return null;
      }

      col -= cols.length;
      return (col >= 0 && col < grand.length) ? grand[col] : null;
   }

   // end GroupedTable API

   // SortedTable API

   /**
    * Get the columns that the table is sorted on.
    * @return sort columns.
    */
   @Override
   public int[] getSortCols() {
      return this.cols.clone();
   }

   /**
    * Get the sorting order of the sorting columns.
    */
   @Override
   public boolean[] getOrders() {
      boolean[] orders = new boolean[cols.length];

      for(int i = 0; i < cols.length; i++) {
         SortOrder order = getGroupOrder(cols[i]);
         orders[i] = order == null || order.isAsc();
      }

      return orders;
   }

   /**
    * Set the comparer for a sorting column.
    * @param col table column index.
    * @param comp comparer.
    */
   @Override
   public void setComparer(int col, Comparer comp) {
      if(comp instanceof SortOrder) {
         setGroupOrder(col, (SortOrder) comp);
      }
   }

   /**
    * Get the comparer for a sorting column.
    * @param col the specified table column index.
    */
   @Override
   public Comparer getComparer(int col) {
      return getGroupOrder(col);
   }

   // end SortedTable API

   /**
    * Get group values.
    *
    * @param row the specified row
    * @return row values
    */
   private Object[] getGroupValues(int row) {
      copyGroupValuesToArray(row, cols.length, gvals);
      return gvals;
   }

   private void copyGroupValuesToArray(int row, int len, Object[] arr) {
      // add group col values
      for(int i = 0; i < len; i++) {
         arr[i] = table.getObject(row, cols[i]);
      }
   }

   /**
    * Add formula value.
    *
    * @param formulae the specified formulae
    */
   private void addFormulaValue(Formula[] formulae, int row) {
      for(int i = 0; formulae != null && i < formulae.length; i++) {
         // multi column formula
         if(formulae[i] instanceof Formula2) {
            int[] cols = ((Formula2) formulae[i]).getSecondaryColumns();
            Object[] data = new Object[cols.length + 1];
            data[0] = table.getObject(row, sums[i]);

            for(int j = 0; j < cols.length; j++) {
               data[j + 1] = table.getObject(row, cols[j]);
            }

            formulae[i].addValue(data);
         }
         // one column formula
         else {
            fagents[i].add(formulae[i], table, row, sums[i]);
         }
      }
   }

   /**
    * Invalidate the table filter forcely, and the table filter will perform
    * filtering calculation to validate itself.
    */
   @Override
   public synchronized void invalidate() {
      if(sumrows != null) {
         sumrows.dispose();
      }

      if(formulas != null) {
         formulas.dispose();
         formulas = null;
      }

      inited = false;
      hinited = false;
      completed = false;
      sumrows = new XSwappableTable(getColCount(), false);
      clearCache();
      groupmap = new Hashtable<>();
      mmap = new Hashtable<>();
      summaryLabelArea = null;
      fireChangeEvent();
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
    * Set the measure names to table.
    */
   @Override
   public void setMeasureNames(String[] names) {
      this.headers = names;
   }

   @Override
   public List<String> getMeasureHeaders() {
      return headers == null ? new ArrayList<>() : Arrays.asList(headers);
   }

   /**
    * Initialize header.
    */
   private void initHeader() {
      // header inited?
      if(hinited) {
         return;
      }

      hinited = true;

      // if a column appears more than once, append a suffix to the column
      // header to distinguish the two columns
      String[] headerSuffix = new String[sums.length];
      int[] cnts = new int[table.getColCount()];
      gvals = new Object[cols.length];

      // prepare headers
      for(int i = 0; i < sums.length; i++) {
         if(sums[i] == -1 || cnts.length <= sums[i]) {
            throw new RuntimeException(
               Catalog.getCatalog().getString("common.invalidTableColumn", headers[i]));
         }

         if(cnts[sums[i]] > 0) {
            headerSuffix[i] = (String) Util.getDupHeader("", cnts[sums[i]]);
         }

         cnts[sums[i]]++;
      }

      // process header rows
      for(int i = 0; i < hcount; i++) {
         Object[] vals = getGroupValues(i);
         Hashtable<Object, Integer> valscnts = new Hashtable<>();

         for(int j = 0; j < vals.length; j++) {
            Object val = vals[j];

            if(val == null) {
               continue;
            }

            Integer cnt = valscnts.get(val);

            if(cnt == null) {
               valscnts.put(val, cnt = 0);
            }
            else {
               valscnts.put(val, cnt = (cnt + 1));
            }

            if(cnt > 0) {
               vals[j] = val.toString() + "." + cnt;
            }
         }

         Object[] hvals = new Object[cols.length + sums.length];
         System.arraycopy(vals, 0, hvals, 0, cols.length);

         for(int j = 0; j < sums.length; j++) {
            // is user specified header?
            if(headers != null && j < headers.length && headers[j] != null) {
               hvals[cols.length + j] = headers[j];
            }
            // is duplicated header?
            else if(headerSuffix[j] != null) {
               hvals[cols.length + j] = table.getObject(i, sums[j]) + headerSuffix[j];
            }
            // is common header?
            else {
               hvals[cols.length + j] = table.getObject(i, sums[j]);
            }
         }

         // add header row
         sumrows.addRow(hvals);
      }
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
      catch(ScriptException scriptException) {
         // Script Exceptions are already logged
         Tool.addUserMessage(scriptException.getMessage());
      }
      catch(Exception ex) {
         LOG.error("Failed to process summary filter", ex);
      }
   }

   /**
    * This method is called before a report is generated to allow a filter
    * to refresh cached values.
    */
   private void process0() {
      try {
         initHeader();
         // reset topn comparator map
         compmap.clear();
         sortByValCompMap.clear();
         // reset group row set
         grpset = new BitSet();
         // reset grand formula totals
         grandtotals = null;
         formulas = new XSwappableObjectList<>(null);
         gvals = new Object[cols.length];
         fagents = new FormulaAgent[calcs.length];

         // reset formulas
         for(int i = 0; i < calcs.length; i++) {
            calcs[i].reset();
            fagents[i] = FormulaAgent.getAgent(table.getColType(sums[i]),
                                               table.isPrimitive(sums[i]));

            if(grand != null) {
               grand[i].reset();
            }
         }

         // reset special order flag
         minSpecialOrder = Integer.MAX_VALUE;

         for(int j = 0; j < cols.length; j++) {
           SortOrder order = getGroupOrder(cols[j]);

            if(order != null && order.isSpecific()) {
               minSpecialOrder = j;
               break;
            }
         }

         // top group node
         GroupNode topnode = new GroupNode();
         Format[] formats = new Format[cols.length];
         boolean[] processed = new boolean[cols.length];
         final BitSet changedIndices = new BitSet();

         // iterate table
         for(int i = hcount; table.moreRows(i) && !cancelled; i++) {
            Object[] vals = getGroupValues(i);
            changedIndices.clear();

            // process non-header row
            for(int j = 0; j < cols.length; j++) {
               // @by stephenwebster, For Bug #9676
               // Stop-gap solution.  The column Type for summary filter is
               // based on column data.  There is a possibility that the data
               // has not started to load for this col, so we will check
               // while iterating the table so we know at least the column type
               // will be based on at least the current row that we access.
               boolean isDateColumn = Tool.isDateClass(table.getColType(cols[j]));
               boolean isNumberColumn = Tool.isNumberClass(table.getColType(cols[j]));
               Object val = null;
               SortOrder order = getGroupOrder(cols[j]);

               // process named group group col
               if(order != null && order.isSpecific()) {
                  int grp = order.findGroup(new Object[] {vals[j]});

                  // find named group
                  if(grp != -1) {
                     val = order.getGroupName(grp);
                  }
                  // doesn't find named group, but should use others label
                  else if(order.getOthers() == SortOrder.GROUP_OTHERS) {
                     val = Catalog.getCatalog().getString("Others");
                  }
                  // convert value to string
                  // @by davyc, why convert to value directly? for date grouping
                  // it is error
                  else {
                     val = vals[j];

                     if(val != null && isDateColumn) {
                        order.compare(null, val);

                        if(order.getGroupDate() != null) {
                           val = order.getGroupDate();
                        }

                        if(!order.isManual()) {
                           val = format(formats, processed, val, j);
                        }
                     }

                     // convert to string, make sure all datas in the column
                     // is same type. fix bug1288091467590
                     if(!order.isManual() && val != null) {
                        try {
                           Format fmt = getDefaultFormat(0, j);

                           // don't apply default number format for dim.
                           if(isNumberColumn && fmt instanceof NumberFormat) {
                              fmt = null;
                           }

                           val = fmt != null ? fmt.format(val) : Tool.toString(val);
                        }
                        catch(Exception ex) {
                           val = Tool.toString(val);
                           // ignore format exception
                        }
                     }
                  }

                  // set named group value
                  if(val != null || grp != -1) {
                     vals[j] = val;
                     changedIndices.set(j);
                  }
               }
               // process date type group col
               // @by larryl, if the column value is null, should not go into
               // the block. Otherwise it will pick up the previous date value.
               else if(vals[j] != null && order != null && isDateColumn) {
                  // strange? only in this way can we set date value in
                  // SortOrder
                  order.compare(null, vals[j]);
                  val = order.getGroupDate();
                  // set date type value

                  // don't replace namedgroup.
                  if(vals[j] != val && vals[j] instanceof Date) {
                     vals[j] = val;
                     changedIndices.set(j);
                  }
               }
            }

            // add grand row formulae value
            addFormulaValue(grand, i);

            // add row to top node
            topnode.addRow(vals, i, changedIndices, true, true);
         }

         formulas.complete();

         // fill in time series gaps
         if(timeSeries && timeSeriesLevel != XConstants.NONE_DATE_GROUP) {
            fixTimeSeries(topnode);
         }

         if(cancelled) {
            return;
         }

         // apply filering
         applyFiltering(topnode, topnode);

         if(cancelled) {
            return;
         }

         // aggregate topn
         aggregateTopN(topnode);

         if(cancelled) {
            return;
         }

         // topn filter
         topNFilter(topnode);

         if(cancelled) {
            return;
         }

         // add group/detail rows
         addRows(topnode, topnode);

         // process grand total row
         if(hasGrand) {
            Object[] vals = new Object[cols.length + sums.length];

            // set non-grandtotal col to be blank
            for(int i = 0; i < cols.length; i++) {
               // grand label exists?
               if(i == 0) {
                  if(grandLabel != null) {
                     vals[i] = grandLabel;
                  }
                  else {
                     vals[i] = TOTAL_LABEL;
                  }
               }
               else {
                  vals[i] = null;
               }
            }

            Object[] totals = getGrandFormulaTotals();

            // set summary col value
            for(int i = 0; i < sums.length; i++) {
               setFormulaTotal(grand[i], null, totals[i]);
               vals[cols.length + i] = grand[i].getResult();

               if(def && (vals[cols.length + i] == null ||
                  (vals[cols.length + i] instanceof Double &&
                  ((Double) vals[cols.length + i]).isNaN())))
               {
                  vals[cols.length + i] = 0;
               }
            }

            // add grand total row
            sumrows.addRow(vals);
         }

         formulas.dispose();
         formulas = null;
      }
      catch(IndexOutOfBoundsException ex) {
         LOG.error("Row index out of bounds: " + ex.getMessage() + " of " + table.getRowCount());
         throw ex;
      }
      finally {
         synchronized(this) {
            if(sumrows != null) {
               sumrows.complete();
            }

            completed = true;
            // @by stephenwebster, For Bug #9676
            // Stop-gap solution.  We create the XMetaInfo after the table
            // is loaded so we know that column types are based on currently
            // loaded table data.
            createXMetaInfo();
            notifyAll();
         }
      }
   }

   private static int getRowCount(XSwappableTable table) {
      int cnt = table.getRowCount();
      return cnt < 0 ? -cnt - 1 : cnt;
   }

   /**
    * to do time series
    */
   private void fixTimeSeries(GroupNode topnode) {
      if(topnode.level == cols.length - 2) {
         List<GroupNode> nodes = topnode.nodes;
         Map<Object, Integer> fidMap = new HashMap<>();// to keep formula id.
         Map<Object, Integer> rowMap = new HashMap<>();

         if(nodes == null || nodes.size() < 2) {
            return;
         }

         Object[] group = null;

         for(GroupNode node : nodes) {
            if(node.hasVals()) {
               group = new Object[node.level];
               node.copyValsToArray(group);
               break;
            }
         }

         Object[] timeArr = getTimeseriesArr(nodes, fidMap, rowMap);
         Object[][] seriesTime = TimeSeriesUtil.fillTimeGap(timeArr, group, timeSeriesLevel, true);
         topnode.clearNodes();

         for(int i = 0; i < seriesTime.length; i++) {
            Object val = seriesTime[i][seriesTime[i].length - 1];
            Integer row = rowMap.get(val);
            Integer fid = fidMap.get(val);
            GroupNode addNode;

            if(row != null) {
               addNode = topnode.addRow(seriesTime[i], row, null, false, false);
            }
            else {
               addNode = topnode.addRow(seriesTime[i], -1, null, false, false);
            }

            if(addNode != null && fid != null) {
               addNode.fid = fid;
            }
         }
      }
      else if(topnode.level < cols.length - 2) {
         for(GroupNode node: topnode.nodes) {
            fixTimeSeries(node);
         }
      }
   }

   /**
    * get time Array that need to do time series from group.
    * @param nodes old group nodes
    * @param fidMap to record old node fid
    * @param rowMap to record old node row
    */
   private Object[] getTimeseriesArr(List<GroupNode> nodes, Map<Object, Integer> fidMap,
                                     Map<Object, Integer> rowMap)
   {
      Object[] result = new Object[nodes.size()];

      for(int i = 0; i < nodes.size(); i++) {
         GroupNode node = nodes.get(i);
         result[i] = node.getVal(node.level);
         fidMap.put(result[i], node.fid);
         rowMap.put(result[i], node.row);
      }

      // time series requires value to be sorted
      Arrays.sort(result, new DefaultComparator(true));

      return result;
   }

   /**
    * Format a value.
    */
   private Object format(Format[] formats, boolean[] processed, Object val, int c) {
      try {
         if(!processed[c]) {
            processed[c] = true;

            if(minfos == null) {
               createXMetaInfo();
            }

            XMetaInfo info = minfos[c];

            if(info != null) {
               XFormatInfo fmt = info.getXFormatInfo();

               if(fmt != null) {
                  formats[c] = TableFormat.getFormat(fmt.getFormat(), fmt.getFormatSpec());
               }
            }
         }

         if(formats[c] != null) {
            val = XUtil.format(formats[c], val);
         }
      }
      catch(Exception ex) {
         // ignore it
      }

      return val;
   }

   /**
    * Get grand formulae.
    *
    * @return grand formulae
    */
   public Formula[] getGrandFormulae() {
      return grand;
   }

   /**
    * Get grand formula totals.
    *
    * @return grand totals
    */
   private Object[] getGrandFormulaTotals() {
      if(grandtotals == null) {
         grandtotals = new Object[grand.length];
      }

      for(int i = 0; i < grandtotals.length; i++) {
         grandtotals[i] = getFormulaTotal(grand[i]);
      }

      return grandtotals;
   }

   /**
    * Get group formula totals.
    *
    * @param node the specified group node
    * @return group totals
    */
   private Object[] getGroupFormulaTotals(GroupNode node) {
      Formula[] formulae = node.getFormulas();
      Object[] totals = new Object[formulae.length];

      for(int i = 0; i < totals.length; i++) {
         totals[i] = getFormulaTotal(formulae[i]);
      }

      return totals;
   }

   /**
    * Get formula total value.
    *
    * @param form the specified formula
    * @return formula total value
    */
   private Object getFormulaTotal(Formula form) {
      if(form instanceof PercentageFormula) {
         return ((PercentageFormula) form).getOriginalResult();
      }
      else if(form != null) {
         return form.getResult();
      }

      return null;
   }

   /**
    * Set formula total value.
    *
    * @param form the specified formula
    * @param grptotal the specified group total value
    * @param grdtotal the specified grand total value
    */
   private void setFormulaTotal(Formula form, Object grptotal, Object grdtotal) {
      if(form instanceof PercentageFormula) {
         PercentageFormula pform = (PercentageFormula) form;

         // percentage of group
         if(pform.getPercentageType() == StyleConstants.PERCENTAGE_OF_GROUP) {
            pform.setTotal(grptotal);
         }
         // percentage of grand
         else if(pform.getPercentageType() == StyleConstants.PERCENTAGE_OF_GRANDTOTAL) {
            pform.setTotal(grdtotal);
         }
      }
   }

   /**
    * Reset topn recusive, from sub nodes to top nodes.
    */
   private void topNFilter(GroupNode node) {
      // detail nodes?
      if(node == null || node.nodes == null) {
         return;
      }

      // topn from leaf nodes to parent nodes
      for(int i = 0; i < node.nodes.size(); i++) {
         topNFilter(node.nodes.get(i));
      }

      prepareSubNodesSum(node);
      applyTopNFilter(node);
      sortByValueFilter(node);

      // inner groups contain ton?
      if(containTopNs(node.level + 1)) {
         resetSummaryValue(node);
      }
   }

   /**
    * Prepare sub nodes data for calculate topn or sort by value.
    */
   private void prepareSubNodesSum(GroupNode gnode) {
      if(gnode == null || gnode.nodes == null) {
         return;
      }

      // @by davyc, prepare original sum values, topn calculation is from
      // sub nodes to parent nodes, and the really sum values may be based
      // on parent nodes, so if here we calculated the really sum values,
      // it is conflict
      for(int i = 0; i < gnode.nodes.size(); i++) {
         gnode.nodes.get(i).sums = null;
         gnode.nodes.get(i).prepareSum();
      }
   }

   /**
    * Apply topn filter.
    */
   private void applyTopNFilter(GroupNode node) {
      // no topn on this node?
      if(!containsTopN(node.level + 1)) {
         return;
      }

      // get sub group nodes related topn info
      InnerTopNInfo topn = topnmap.get(node.level + 1);
      List<GroupNode> nodes = node.nodes;
      List<GroupNode> topNNodes = null;

      if(!sortTopN || getGroupOrder(topn.scol) != null) {
         topNNodes = new ArrayList<>(node.nodes);
      }

      List<GroupNode> nodes0 = new ArrayList<>(node.nodes);
      // get sub group nodes related comparator, use cache for performance
      Integer key = node.level + 1;
      GroupNodeComparer comparer = compmap.get(key);

      if(comparer == null) {
         comparer = new GroupNodeComparer(topn.scol, topn.asc);
         compmap.put(key, comparer);
      }

      Collections.sort(topNNodes == null ? nodes : topNNodes, comparer);
      int size = 0;

      if(!topn.kept) {
         size = Math.min(topNNodes == null ? nodes.size() : topNNodes.size(), topn.n);
      }
      else {
         GroupNode last = null;
         int count = 0;
         int i = 0;
         List<GroupNode> groupNodes = topNNodes == null ? nodes : topNNodes;

         for(; i < groupNodes.size(); i++) {
            GroupNode temp = groupNodes.get(i);

            if(last != null && comparer.compare(last, temp) == 0) {
               continue;
            }

            count++;

            if(count > topn.n) {
               break;
            }

            last = temp;
         }

         size = i;
      }

      // bug8820, don't see any reason why others should be thrown away if it's
      // not the inner-most level
      // if(topn.others && (node.level + 1 == cols.length - 1)) {
      if(topn.others) {
         List<GroupNode> others = new ArrayList<>();

         if(topNNodes != null) {
            for(int i = topNNodes.size() - 1; i >= size; i--) {
               GroupNode removed = topNNodes.remove(i);
               nodes.remove(removed);
               others.add(removed);
            }

            if(others.size() > 0) {
               MergedGroupNode merged = new MergedGroupNode(others);
               // sort by value need to use
               merged.prepareSum();
               nodes.add(merged);
            }
         }
         else {
            for(int i = nodes.size() - 1; i >= size; i--) {
               others.add(nodes.remove(i));
            }

            if(others.size() > 0) {
               MergedGroupNode merged = new MergedGroupNode(others);
               // sort by value need to use
               merged.prepareSum();
               nodes.add(merged);
            }
         }

      }
      else {
         if(topNNodes != null) {
            for(int i = topNNodes.size() - 1; i >= size; i--) {
               nodes.remove(topNNodes.remove(i));
            }
         }
         else {
            for(int i = nodes.size() - 1; i >= size; i--) {
               nodes.remove(i);
            }
         }
      }

      resetOrder(nodes0, nodes, node.level + 1);
   }

   /**
    * Apply sort others last option.
    */
   private void sortOthers(GroupNode node) {
      InnerTopNInfo topn = topnmap.get(node.level + 1);
      SortOrder order = getGroupOrder(node.level + 1);
      boolean others = false;

      if(topn != null && topn.others ||
         order != null && order.getGroupNames() != null && order.getGroupNames().length > 0)
      {
         others = true;
      }

      if(others && order != null && order.getOrder() != StyleConstants.SORT_NONE) {
         GroupNodeComparer2 comparer2 = new GroupNodeComparer2(order);
         node.nodes.sort(comparer2);
      }
   }

   /**
    * Sort by value.
    */
   private void sortByValueFilter(GroupNode node) {
      // no sort by value?
      if(!this.containsSortByVal(node.level + 1)) {
         sortOthers(node);
         return;
      }

      // get sub group nodes related topn info
      InnerTopNInfo topn = sortByValMap.get(node.level + 1);
      List<GroupNode> nodes = node.nodes;
      List<GroupNode> nodes0 = new ArrayList<>(node.nodes);

      // get sub group nodes related comparator, use cache for performance
      Integer key = (node.level + 1);
      GroupNodeComparer comparer = sortByValCompMap.get(key);

      if(comparer == null) {
         comparer = new GroupNodeComparer(topn.scol, topn.asc);
         sortByValCompMap.put(key, comparer);
      }

      nodes.sort(comparer);
      resetOrder(nodes0, nodes, node.level + 1);
   }

   /**
    * Check if contains topn info from current group level to inner most group level.
    */
   private boolean containTopNs(int level) {
      for(int i = level; i < cols.length; i++) {
         if(containsTopN(i)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Reset the group node's summary.
    * @param gnode
    */
   private void resetSummaryValue(GroupNode gnode) {
      if(!isAggregateTopN()) {
         return;
      }

      Formula[] formulas = gnode.getFormulas();

      // grand total?
      if(formulas == null) {
         formulas = grand;
      }

      if(formulas == null) {
         return;
      }

      for(int i = 0; i < formulas.length; i++) {
         if(formulas[i] != null) {
            formulas[i].reset();
         }
      }

      calculateSummary(formulas, gnode);
   }

   private void calculateSummary(Formula[] formulas, GroupNode node) {
      BitSet includedRows = new SparseBitSet();
      fillIncludedRowsForTotal(includedRows, node);

      for(int row = includedRows.nextSetBit(0); row >= 0;
         row = includedRows.nextSetBit(row + 1))
      {
         addFormulaValue(formulas, row);
      }
   }

   private void fillIncludedRowsForTotal(BitSet includedRows, GroupNode gnode) {
      final List<GroupNode> nodes = gnode.nodes;

      if(nodes == null || nodes.isEmpty()) {
         BitSet includedRows0 = gnode.includedRows;

         if(includedRows0 != null) {
            for(int row = includedRows0.nextSetBit(0); row >= 0;
               row = includedRows0.nextSetBit(row + 1))
            {
               includedRows.set(row);
            }
         }

         return;
      }

      for(GroupNode node : nodes) {
         fillIncludedRowsForTotal(includedRows, node);
      }
   }

   /**
    * Restore to original order if necessary.
    */
   protected void resetOrder(List<GroupNode> nodes0, List<GroupNode> nodes1, int gidx) {
      // to be overridden
   }

   /**
    * Apply filtering.
    *
    * @param node the specified group node
    */
   private void applyFiltering(GroupNode topnode, GroupNode node) {
      Object[] grdtotals = getGrandFormulaTotals();
      Object[] grptotals = node.nodes == null ? null :
         getGroupTotal(topnode, node, grdtotals);

      // is a detail node? just return
      if(node.nodes == null) {
         return;
      }
      // is not a detail node?
      else {
         GroupNode node0 = node.nodes.get(0);

         // sub nodes are detail nodes? check if should filter out some nodes
         if(node0.nodes == null) {
            for(int i = node.nodes.size() - 1; i >= 0; i--) {
               GroupNode subnode = node.nodes.get(i);

               if(subnode.sums == null) {
                  subnode.prepareSum(grptotals, grdtotals);
               }

               // remove the unsatisfied sub nodes
               if(!evaluate(subnode)) {
                  node.nodes.remove(i);
               }
            }
         }
         // sub nodes are group node too? apply filtering recursively
         else {
            for(int i = 0; i < node.nodes.size(); i++) {
               GroupNode subnode = node.nodes.get(i);
               applyFiltering(topnode, subnode);
            }
         }
      }

      // remove empty nodes after applying filtering
      for(int i = node.nodes.size() - 1; i >= 0; i--) {
         GroupNode tnode = node.nodes.get(i);

         if(tnode.isEmpty()) {
            node.nodes.remove(i);
         }
      }
   }

   private void aggregateTopN(GroupNode topnode) {
      if(topNAggregateComparer == null) {
         return;
      }

      // find detail node
      List<GroupNode> details = new ArrayList<>();
      collectDetailNodes(topnode, details);

      if(details.size() <= topNAggregateN) {
         return;
      }

      details.sort(topNAggregateComparer);

      for(int i = details.size() - 1; i >= topNAggregateN; i--) {
         details.remove(i);
      }

      filterNode(topnode, details);
   }

   private void collectDetailNodes(GroupNode parent, List<GroupNode> details) {
      if(parent.nodes == null) {
         parent.prepareSum();

         if(parent.sums[topNAggregateCol] != null) {
            details.add(parent);
         }

         return;
      }

      for(int i = 0; i < parent.nodes.size(); i++) {
         collectDetailNodes(parent.nodes.get(i), details);
      }
   }

   private void filterNode(GroupNode parent, List<GroupNode> details) {
      if(parent.nodes == null) {
         return;
      }

      for(int i = parent.nodes.size() - 1; i >= 0; i--) {
         GroupNode child = parent.nodes.get(i);

         // not detail node?
         if(child.nodes != null) {
            filterNode(child, details);

            // child is empty? remove it
            if(child.isEmpty()) {
               parent.nodes.remove(i);
            }
         }
         // detail node, not include?
         else if(!details.contains(child)) {
            parent.nodes.remove(i);
         }
      }
   }

   /**
    * Add group/detail rows.
    *
    * @param node the specified group node
    */
   private void addRows(GroupNode topnode, GroupNode node) {
      Object[] grdtotals = getGrandFormulaTotals();
      Object[] grptotals = node.nodes == null ? null :
         getGroupTotal(topnode, node, grdtotals);

      // add sub group/detail rows recursively
      if(node.nodes != null) {
         for(int i = 0; i < node.nodes.size(); i++) {
            GroupNode subnode = node.nodes.get(i);

            // the inner-most group is treated as header detail row
            if(i == 0 && subnode.nodes == null) {
               grpset.set(getRowCount(sumrows));
            }

            // @by davyc, for topn and sort by value, the sums is the original
            // value, so here need to be recalculated
            subnode.sums = null;
            subnode.prepareSum(grptotals, grdtotals);
            addRows(topnode, subnode);
         }
      }

      // add self to sum rows if is group summary and hierarchy is true or is
      // detail summary, and top node's vals must be null, we filter it out...
      if((hierarchy || node.nodes == null) && node.hasVals()) {
         Object[] nvals = new Object[cols.length + sums.length];

         // set group col value
         node.copyValsToArray(nvals);

         // set summary col value
         for(int i = 0; i < sums.length; i++) {
            nvals[cols.length + i] = node.sums[i];

            if(def && (nvals[cols.length + i] == null ||
               (nvals[cols.length + i] instanceof Double &&
               ((Double) nvals[cols.length + i]).isNaN())))
            {
               nvals[cols.length + i] = 0;
            }
         }

         // is group summary node?
         if(node.nodes != null) {
            groupmap.put(getRowCount(sumrows), node.level);
         }

         // apply summary label format
         if(getSummaryLabel() != null) {
            if(summaryLabelArea == null) {
               summaryLabelArea = new SparseMatrix();
            }

            for(int i = 0; i < cols.length; i++) {
               if(nvals[i] != null) {
                  Object[] args = { nvals[i] };
                  nvals[i] = MessageFormat.format(getSummaryLabel(), args);
                  summaryLabelArea.set(getRowCount(sumrows), i, Boolean.TRUE);
               }
            }
         }

         sumrows.addRow(nvals);
      }
   }

   /**
    * Get the group total acoording to pglvl.
    * @param node the specified group node.
    * @param grdtotals the specified grand total value.
    */
   private Object[] getGroupTotal(GroupNode topnode, GroupNode node,
                                  Object[] grdtotals)
   {
      GroupNode pnode = pglvl > 0 ?
         getParentGroupNode(topnode, node, 0) : node;
      return pnode == null || pnode.level < 0 ? grdtotals :
         getGroupFormulaTotals(pnode);
   }

   /**
    * Get the group total's parent node at the specified level.
    * @param node the specified parent node.
    * @param node the specified group node.
    * @param level the specified group value.
    */
   private GroupNode getParentGroupNode(GroupNode pnode, GroupNode node,
                                        int level) {
      if(pnode == null || pnode.nodes == null ||
         !node.hasVals() || node.level <= 0)
      {
         return null;
      }

      // found the right group node at nodes parent link
      for(int i = 0; i < pnode.nodes.size(); i++) {
         GroupNode ppnode;
         ppnode = pnode.nodes.get(i);

         if(ppnode.hasVals()) {
            if(Tool.equals(ppnode.getVal(level), node.getVal(level))) {
               if(ppnode.level == node.level - pglvl) {
                  return ppnode;
               }
               else {
                  return getParentGroupNode(ppnode, node, level + 1);
               }
            }
         }
      }

      return null;
   }

   /**
    * Evaluate the detail node.
    * @return <tt>true</tt> if satisfies condition, <tt>false</tt> otherwise.
    */
   protected boolean evaluate(GroupNode node) {
      return true;
   }

   /**
    * Set the grand total label. It is displayed in the first column
    * on the grand total row. By default there is no label.
    * @param label grand total label.
    */
   public void setGrandLabel(String label) {
      grandLabel = label;
   }

   /**
    * Get the grand total row label.
    * @return grand total label.
    */
   public String getGrandLabel() {
      return grandLabel;
   }

   /**
    * Set the summary row label. This label can be specified as a message
    * format conforming to java.text.MessageFormat specification. The
    * group column values are passed to the format as arguments that can
    * be replaced in the format. For example, a label can be
    * 'Total Sales for {0}', where {0} will be replaced by the group column
    * value for the group.
    */
   public void setSummaryLabel(String fmt) {
      sumlabel = fmt;
   }

   /**
    * Get summary row label format.
    */
   public String getSummaryLabel() {
      return sumlabel;
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
    * Set the specific group order information.
    * @param col group columns.
    * @param order group order.
    */
   public void setGroupOrder(int col, SortOrder order) {
      ordermap[col] = order;
   }

   /**
    * Get the specific group order information.
    * @param col group columns.
    */
   public SortOrder getGroupOrder(int col) {
      return ordermap[col];
   }

   /**
    * Create the meta info.
    */
   private void createXMetaInfo() {
      boolean[] dcol = new boolean[table.getColCount()];

      for(int i = 0; i < dcol.length; i++) {
         dcol[i] = Tool.isDateClass(table.getColType(i));
      }

      minfos = new XMetaInfo[table.getColCount()];

      for(int col = 0; col < minfos.length; col++) {
         SortOrder order = getGroupOrder(col);

         if(order == null) {
            minfos[col] = null;
            continue;
         }

         int level = order.getOption();

         // such as Month of Year also create format info, but if it is
         // aggregate column, will be removed later in getXMetaInfo
         // fix bug1261032688280
         // not date type, not part date level?
         if(!dcol[col] && !(Tool.isNumberClass(table.getColType(col)) &&
            (level & SortOrder.PART_DATE_GROUP) != 0))
         {
            minfos[col] = null;
            continue;
         }

         XMetaInfo minfo = new XMetaInfo();

         if(XUtil.getDefaultDateFormat(level) == null) {
            minfo.setXFormatInfo(new XFormatInfo());
            minfos[col] = minfo;
            continue;
         }

         SimpleDateFormat dfmt = XUtil.getDefaultDateFormat(level,
            order.getDataType());

         if(dfmt != null) {
            String fmt = dfmt.toPattern();
            minfo.setXFormatInfo(new XFormatInfo(TableFormat.DATE_FORMAT, fmt));
            minfo.setProperty("autoCreatedFormat", "true");
            mexisting = true;
            minfos[col] = minfo;
         }
      }
   }

   /**
    * Set topn info.
    *
    * @param col group col
    * @param scol summary col
    * @param topn topn value
    * @param reverse true if should reverse
    */
   public void setTopN(int col, int scol, int topn, boolean reverse) {
      setTopN(col, scol, topn, reverse, false, false);
   }

   /**
    * Set topn info.
    *
    * @param col group col
    * @param scol summary col
    * @param topn topn value
    * @param reverse true if should reverse
    * @param kept true if keep the equal rows, false to discard them
    * @param others true to group others into 'Others' group.
    */
   public void setTopN(int col, int scol, int topn, boolean reverse,
                       boolean kept, boolean others)
   {
      if(col >= 0 && col < cols.length && scol >= 0 && scol < sums.length && topn >= 1) {
         InnerTopNInfo info = new InnerTopNInfo(scol, topn, reverse, kept, others);
         topnmap.put(col, info);
      }
   }

   /**
    * Set sort by value column info.
    * @param col the index of column in the "cols" variable.
    * @param scol the index of summary column in "sums" variable.
    * @param reverse true if should reverse
    */
   public void setSortByValInfo(int col, int scol, boolean reverse) {
      if(col >= 0 && col < cols.length && scol >= 0 && scol < sums.length) {
         InnerTopNInfo info = new InnerTopNInfo(scol, Integer.MAX_VALUE, reverse, false, false);
         sortByValMap.put(col, info);
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
      topNAggregateComparer = null;
      this.topNAggregateCol = dcol;
      this.topNAggregateN = n;

      if(topNAggregateCol >= 0 && topNAggregateN > 0) {
         topNAggregateComparer = new GroupNodeComparer(dcol, !topn);
      }

      invalidate();
   }

   /**
    * Remove topn info.
    *
    * @param col the specified group col
    */
   public void removeTopN(int col) {
      topnmap.remove(col);
   }

   /**
    * Check if contains topn info.
    *
    * @param col the group column, or -1 to check if any topN is defined.
    * @return true if contains, false otherwise
    */
   public boolean containsTopN(int col) {
      return (col < 0) ? topnmap.size() > 0
         : topnmap.get(col) != null;
   }

   /**
    * Remove sort by column value info.
    * @param col the specified group col
    */
   public void removeSortByVal(int col) {
      sortByValMap.remove(col);
   }

   public void clearSortByVal() {
      sortByValMap.clear();
   }

   /**
    * Check if contains sort by column value info.
    * @param col the group column, or -1 to check if any sort by column is
    *        defined.
    * @return true if contains, false otherwise
    */
   private boolean containsSortByVal(int col) {
      return (col < 0) ? sortByValMap.size() > 0 : sortByValMap.get(col) != null;
   }

   /**
    * Get the original table of this filter.
    */
   @Override
   public TableLens getTable() {
      return this.table;
   }

   /**
    * Set the base table of this filter. The base table must be a sorted table.
    */
   @Override
   public synchronized void setTable(TableLens table) {
      this.table = table;
      this.hcount = table.getHeaderRowCount();
      this.ordermap = new SortOrder[table.getColCount()];
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
      if(sdescriptor == null) {
         sdescriptor = new SummaryFilterDataDescriptor();
      }

      return sdescriptor;
   }

   /**
    * Return the number of rows on the bottom of the table to be treated
    * as trailer rows.
    * @return number of header rows.
    */
   @Override
   public int getTrailerRowCount() {
      return hasGrand ? 1 : 0;
   }

   /**
    * Get the base table row index corresponding to the filtered table.
    * If the row does not exist in the base table, it returns -1.
    * @param row row index in the filtered table.
    * @return corresponding row index in the base table.
    */
   @Override
   public int getBaseRowIndex(int row) {
      if(row < hcount) {
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
      return col < cols.length ? cols[col] : sums[col - cols.length];
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
      if(row < hcount) {
         initHeader();
         return true;
      }

      checkInit();

      if(userMsg != null) {
         Tool.addUserMessage(userMsg);
      }

      synchronized(SummaryFilter.this) {
         while(!completed && !cancelled && row >= getRowCount(sumrows)) {
            try {
               SummaryFilter.this.wait(10000);
            }
            catch(Exception ex) {
               // ignore it
            }
         }
      }

      return sumrows.moreRows(row);
   }

   /**
    * Return the number of rows in the table. The number of rows includes
    * the header rows.
    * @return number of rows in table.
    */
   @Override
   public int getRowCount() {
      checkInit();

      synchronized(SummaryFilter.this) {
         if(!completed) {
            return -1;
         }
      }

      return getRowCount(sumrows);
   }

   /**
    * Return the number of columns in the table. The number of columns
    * includes the header columns.
    * @return number of columns in table.
    */
   @Override
   public int getColCount() {
      return cols.length + sums.length;
   }

   /**
    * Return the number of rows on the top of the table to be treated
    * as header rows.
    * @return number of header rows.
    */
   @Override
   public int getHeaderRowCount() {
      return hcount;
   }

   /**
    * Return the number of columns on the left of the table to be
    * treated as header columns.
    */
   @Override
   public int getHeaderColCount() {
      return 0;
   }

   /**
    * Return the per cell alignment.
    * @param r row number.
    * @param c column number.
    * @return cell alignment.
    */
   @Override
   public int getAlignment(int r, int c) {
      if(isLeftAlign) {
         return StyleConstants.H_LEFT;
      }

      if(isSummaryRow(r) && isSummaryCol(c)) {
         return H_RIGHT | V_CENTER;
      }

      return StyleConstants.H_LEFT;
   }

   /**
    * Return the per cell font. Return null to use default font.
    * @param r row number.
    * @param c column number.
    * @return font for the specified cell.
    */
   @Override
   public Font getFont(int r, int c) {
      if(r < hcount) {
         return table.getFont(0, 0);
      }

      return null;
   }

   /**
    * Check if the location is display summary label.
    * @hidden
    */
   public boolean isSummaryLabelLocation(int r, int c) {
      if(getSummaryLabel() == null) {
         return false;
      }

      if(!moreRows(r)) {
         return false;
      }

      if(summaryLabelArea != null && summaryLabelArea.get(r, c) != null &&
         summaryLabelArea.get(r, c) == Boolean.TRUE)
      {
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
      if(!inited) {
         if(r >= hcount) {
            checkInit();
         }
         else if(!hinited) {
            initHeader();
         }
      }

      if(cellValues != null) {
         Object val = cellValues.get(r, c);

         if(val != null) {
            return val;
         }
      }

      if(c < getColCount() && sumrows.moreRows(r)) {
         return sumrows.getObject(r, c);
      }

      return null;
   }

   /**
    * Set the cell value.
    * @param r row number.
    * @param c column number.
    * @param v cell value.
    */
   @Override
   public void setObject(int r, int c, Object v) {
      if(cellValues == null) {
         cellValues = new SparseMatrix();
      }

      cellValues.set(r, c, v);
      fireChangeEvent();
   }

   @Override
   public Class getColType(int col) {
      if(col < cols.length) {
         SortOrder order = getGroupOrder(cols[col]);

         // Named grouping, so return string.
         if(order != null && order.isSpecific()) {
            return String.class;
         }
         else {
            return table.getColType(getBaseColIndex(col));
         }
      }

      Class type = calcs[col - cols.length].getResultType();
      return type != null ? type : table.getColType(getBaseColIndex(col));
   }

   /**
    * Check whether to keep group hierarchy.
    */
   public boolean isKeepHierarchy() {
      return hierarchy;
   }

   /**
    * set time series.
    */
   public void setTimeSeries(boolean timeSeries) {
      this.timeSeries = timeSeries;
   }

   /**
    * Check whether to do time series.
    */
   public boolean isTimeSeries() {
      return timeSeries;
   }

   /**
    * set the time series level.
    */
   public void setTimeSeriesLevel(int timeSeriesLevel) {
      this.timeSeriesLevel = timeSeriesLevel;
   }

   /**
    * get time series level.
    */
   public int getTimeSeriesLevel() {
      return this.timeSeriesLevel;
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

   public OrderInfo getOrderInfo(int index) {
      return this.orderInfo.get(index);
   }

   public void setOrderInfo(OrderInfo info) {
      this.orderInfo.add(info);
   }

   /**
    * Dispose the table to clear up temporary resources.
    */
   @Override
   public void dispose() {
      table.dispose();

      if(formulas != null) {
         formulas.dispose();
         formulas = null;
      }

      if(sumrows != null) {
         sumrows.dispose();
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
    * Check init.
    */
   private void checkInit() {
      if(!inited) {
         synchronized(this) {
            if(!inited) {
               inited = true;
               boolean inExec = JavaScriptEngine.getExecScriptable() != null;

               // if this is called from JavaScriptEngine.exec(), the script engine is already
               // locked. running process() in a separate thread would create a deadlock
               // waiting forever for the JavaScriptEngine lock to be released.

               if(!inExec) {
                  Runnable r = new ThreadPool.AbstractContextRunnable() {
                     @Override
                     public void run() {
                        try {
                           SummaryFilter.this.process();
                        }
                        finally {
                           userMsg = Tool.getUserMessage();
                        }
                     }
                  };

                  ThreadPool.addOnDemand(r);
               }
               else {
                  process();
               }
            }
         }
      }
   }

   /**
    * Cancel the table lens and running queries if supported.
    */
   @Override
   public void cancel() {
      cancelLock.lock();

      try {
         TableLens table = this.table;
         this.cancelled = true;

         if(table instanceof CancellableTableLens) {
            ((CancellableTableLens) table).cancel();
         }

         if(sumrows != null) {
            sumrows.complete();
         }
      }
      finally {
         cancelLock.unlock();
      }
   }

   @Override
   public boolean isCancelled() {
      return cancelled;
   }

   /**
    * Clone a formula array.
    */
   private static Formula[] cloneFormulas(Formula[] arr) {
      if(arr == null) {
         return null;
      }

      try {
         Formula[] narr = new Formula[arr.length];

         for(int i = 0; i < arr.length; i++) {
            if(arr[i] != null) {
               narr[i] = (Formula) arr[i].clone();
            }
            else {
               narr[i] = new DefaultFormula();
            }
         }

         return narr;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone formulas", ex);
      }

      return null;
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

   /**
    * Set the Whether apply the sort result during apply the topN.
    * @param sortTopN
    */
   public void setSortTopN(boolean sortTopN) {
      this.sortTopN = sortTopN;
   }

   /**
    * Set the Whether apply the sort result during apply the topN.
    */
   public boolean isSortTopN() {
      return sortTopN;
   }

   // base class for MergedGroupNode and UnionGroupNode
   private class CombinedGroupNode extends GroupNode {
      @Override
      void prepareSum() {
         // prepared already?
         if(sums == null) {
            Formula[] formulas = getFormulas();
            sums = new Object[formulas.length];

            for(int i = 0; i < formulas.length; i++) {
               if(formulas[i] != null) {
                  formulas[i].reset();
               }
            }

            calculateSummary(formulas, this);

            for(int i = 0; i < formulas.length; i++) {
               sums[i] = getFormulaTotal(formulas[i]);
            }
         }
      }
   }

   /**
    * Merged group node.
    */
   protected class MergedGroupNode extends CombinedGroupNode {
      public MergedGroupNode(List<GroupNode> list) {
         GroupNode first = list.get(0);
         this.fid = first.fid;
         this.level = first.level;
         this.row = first.row;

         includedRows = new SparseBitSet();
         Map<String, List<GroupNode>> subgroups = new HashMap<>();

         for(GroupNode node : list) {
            // copy named group values. (51822)
            setVals(node.overwrittenValues);
            addGroupNode(subgroups, node);
            fillIncludedRowsForTotal(includedRows, node);
         }

         setVal(this.level, "Others");

         if(!subgroups.isEmpty()) {
            nodes = new ArrayList<>(subgroups.size());

            for(List<GroupNode> subgroup : subgroups.values()) {
               if(subgroup.size() > 1) {
                  UnionGroupNode subnode = new UnionGroupNode(subgroup);
                  subnode.prepareSum();
                  nodes.add(subnode);
               }
               else {
                  nodes.add(subgroup.get(0));
               }
            }
         }
      }

      /**
       * Add new GroupNode to MergedGroupNode.
       **/
      private void addGroupNode(Map<String, List<GroupNode>> subgroups, GroupNode node) {
         for(int i = 0; node.nodes != null && i < node.nodes.size(); i++) {
            GroupNode child = node.nodes.get(i);
            child.setVal(this.level, "Others");

            // only add parent nodes for others if keeping hierarcy. otherwise
            // chart (e.g. sunburst) will have duplicate rows
            if(hierarchy || child.nodes == null) {
               String key = Tool.arrayToString(child.getVals());
               List<GroupNode> subgroup = subgroups.computeIfAbsent(key, k -> new ArrayList<>());
               subgroup.add(child);
            }

            addGroupNode(subgroups, child);
         }
      }
   }

   /**
    * Union (combine children) of groups.
    */
   private class UnionGroupNode extends CombinedGroupNode {
      public UnionGroupNode(List<GroupNode> list) {
         boolean first = true;

         for(GroupNode node : list) {
            if(first) {
               first = false;
               this.fid = node.fid;
               this.level = node.level;
               setVals(node.overwrittenValues);
               this.row = node.row;

               includedRows = new SparseBitSet();
            }

            fillIncludedRowsForTotal(includedRows, node);

            if(node.nodes != null) {
               if(nodes == null) {
                  nodes = new ArrayList<>();
               }

               nodes.addAll(node.nodes);
            }
         }
      }
   }

   /**
    * Group node.
    */
   protected class GroupNode {
      public GroupNode() {
         if(!topnmap.isEmpty()) {
            includedRows = new SparseBitSet();
         }
      }

      /**
       * Add a row.
       *
       * @param vals the specified row values
       * @param changedIndices bitset indicating which indices in vals were modified.
       * @param addFormula if true, then a new formula will be added to formulas for newly created
       *                   nodes.
       * @param updateFormulaValue if need to update the formula value, should using false
       * when insert dates for timeseries, else will cause the formual value be applied twice.
       *
       * @return add or update node.
       */
      private GroupNode addRow(Object[] vals, int row, BitSet changedIndices, boolean addFormula,
                               boolean updateFormulaValue)
      {
         if(includedRows != null) {
            includedRows.set(row);
         }

         // row as -1 when do timeseries, the formula has been set, ignore it.
         if(row != -1 && updateFormulaValue) {
            Formula[] formulae0 = getFormulas();
            addFormulaValue(formulae0, row);
            setFormulas(formulae0); // update formula objects in list
         }

         final int nextLevel = level + 1;

         // is detail summary, it's time to stop recursion
         if(nextLevel >= cols.length) {
            return null;
         }

         // is group summary, go on recursion
         GroupNode node = null;
         SortOrder order = getGroupOrder(cols[nextLevel]);
         // insert position
         int pos = -1;
         // desc if is desc order and order isn't specific, for specific
         // order should keep the original order as user defined in gui
         boolean desc = order != null && !order.isSpecific() && order.isDesc();

         if(nodes != null) {
            // when level + 1 >= minimum special order, we have to reorder
            // group nodes, for in this level, sort calculation is useless
            if(nextLevel >= minSpecialOrder) {
               // default position is 0 for reverse order iteration reason
               pos = 0;

               // reverse order iteration is better than sequent order
               // iteration for performance reason
               for(int i = nodes.size() - 1; i >= 0; i--) {
                  node = nodes.get(i);
                  int result = compare(node.getVal(nextLevel), vals[nextLevel], order);

                  // group node already exists?
                  if(result == 0) {
                     break;
                  }
                  // find a position to insert new group node?
                  else if((!desc && result < 0) || (desc && result > 0)) {
                     pos = i + 1;
                     node = null;
                     break;
                  }
                  // continue compare?
                  else {
                     node = null;
                     continue;
                  }
               }
            }
            // is date group col or ordinary group col, need to be compared
            // with the former brother node only, for sort calculation is useful
            else {
               node = nodes.get(nodes.size() - 1);

               // group node doesn't exists?
               if(compare(node.getVal(nextLevel), vals[nextLevel], order) != 0) {
                  node = null;
               }
            }
         }

         // group node doesn't exist, create one
         if(node == null) {
            // GroupNode (vals and sum) can be relatively heavy. when the number explodes
            // in deeply nested grouping, it can create a large surge in memory demand.
            if(row % 500 == 0) {
               XSwapper.getSwapper().waitForMemory();
            }

            node = new GroupNode();
            node.row = row;
            node.level = nextLevel;

            // If row == -1, then this is being added due to a time series time gap, so can't
            // reference the underlying table.
            if(row == -1) {
               node.setVals(vals, node.level + 1);
            }
            else if(changedIndices != null && !changedIndices.isEmpty()) {
               for(int i = changedIndices.nextSetBit(0); i >= 0 && i < node.level + 1;
                   i = changedIndices.nextSetBit(i + 1))
               {
                  node.setVal(i, vals[i]);
               }
            }

            // set formulae
            Formula[] formulae = new Formula[calcs.length];

            for(int i = 0; i < calcs.length; i++) {
               try {
                  formulae[i] = (Formula) calcs[i].clone();
                  formulae[i].reset();
               }
               catch(Exception e) {
                  LOG.error("Failed to copy and reset formula", e);
               }
            }

            if(addFormula && row != -1) {
               synchronized(formulas) {
                  node.fid = formulas.add(formulae);
               }
            }
            else if(row == -1) {
               node.fid = -2;
            }

            if(order != null && order.getOthers() == OrderInfo.GROUP_OTHERS &&
               Tool.equals("Others", vals[nextLevel]))
            {
               List<GroupNode> othersNode = new ArrayList<>();
               othersNode.add(node);
               node = new MergedGroupNode(othersNode);
            }

            // add node as a sub group node
            if(nodes == null) {
               nodes = new ArrayList<>(1);
            }

            // is append
            if(pos == -1) {
               nodes.add(node);
            }
            // is insert at the specified position
            else {
               nodes.add(pos, node);
            }
         }

         // add row recursively
         node.addRow(vals, row, changedIndices, addFormula, updateFormulaValue);

         return node;
      }

      /**
       * Compare two values.
       *
       * @param val1 value 1
       * @param val2 value 2
       * @param order sort order
       * @return compare result
       */
      private int compare(Object val1, Object val2, SortOrder order) {
         // is named group?
         if(order != null && order.isSpecific()) {
            int index1 = order.getGroupNameIndex(val1);
            int index2 = order.getGroupNameIndex(val2);

            // keep others?
            if(!Tool.equals(val1, val2) && index1 == index2) {
               // sort other groups
               return defaultComparator.compare(val1, val2);
            }

            return index1 - index2;
         }
         // is date group or ordinary group?
         else {
            return Tool.compare(val1, val2);
         }
      }

      /**
       * Check if the node is empty.
       *
       * @return <tt>true</tt> if empty, <tt>false</tt> otherwise
       */
      private boolean isEmpty() {
         return nodes != null && nodes.size() == 0;
      }

      /**
       * Prepare sum values, orignal result, not the percentage result.
       */
      void prepareSum() {
         if(sums == null) {
            Formula[] formulas = getFormulas();
            sums = new Object[formulas.length];

            for(int i = 0; i < formulas.length; i++) {
               sums[i] = getFormulaTotal(formulas[i]);
            }
         }
      }

      /**
       * Prepare sum values.
       * @param grptotals the specified group formula totals
       */
      private void prepareSum(Object[] grptotals, Object[] grdtotals) {
         // prepared already?
         if(sums == null) {
            Formula[] formulae = getFormulas();
            sums = new Object[formulae.length];

            for(int i = 0; i < formulae.length; i++) {
               setFormulaTotal(formulae[i], grptotals[i], grdtotals[i]);
               sums[i] = formulae[i].getResult();
            }
         }
      }

      public final void setFormulas(Formula[] arr) {
         if(fid != -1) {
            formulas.set(fid, arr);
         }
      }

      public final Formula[] getFormulas() {
         try {
            if(fid == -2) {
               return getDefaultFormulas();
            }

            if(fid != -1) {
               Formula[] objs = formulas.get(fid);
               Formula[] res = new Formula[objs.length];
               System.arraycopy(objs, 0, res, 0, objs.length);
               return res;
            }
         }
         catch(Exception ex) {
            LOG.error("Failed to get formulas", ex);
         }

         return null;
      }

      private Formula[] getDefaultFormulas() {
         Formula[] formulae = new Formula[calcs.length];

         for(int i = 0; i < calcs.length; i++) {
            try {
               formulae[i] = (Formula) calcs[i].clone();
               formulae[i].reset();
            }
            catch(Exception e) {
               LOG.error("Failed to copy and reset formula", e);
            }
         }

         return formulae;
      }

      /**
       * Get the objects.
       * @return the objects.
       */
      public final Object[] getObjects() {
         Object[] arr = new Object[cols.length + sums.length];
         copyValsToArray(arr);
         System.arraycopy(sums, 0, arr, cols.length, sums.length);

         return arr;
      }

      public void clearNodes() {
         nodes = null;
      }

      public boolean hasVals() {
         return level >= 0 && (row >= 0 || !overwrittenValues.isEmpty());
      }

      public Object[] getVals() {
         if(level < 0) {
            return null;
         }

         Object[] vals = null;

         if(row >= 0) {
            final int len = level + 1;
            vals = new Object[len];
            copyGroupValuesToArray(row, len, vals);
         }

         if(overwrittenValues != null) {
            if(vals == null) {
               vals = new Object[overwrittenValues.size()];
            }

            for(Map.Entry<Integer, Object> entry : overwrittenValues.entrySet()) {
               vals[entry.getKey()] = entry.getValue();
            }
         }

         return vals;
      }

      public Object getVal(int idx) {
         if(level < 0) {
            return null;
         }

         if(overwrittenValues != null && overwrittenValues.containsKey(idx)) {
            return overwrittenValues.get(idx);
         }

         if(row >= 0) {
            return table.getObject(row, cols[idx]);
         }

         throw new IndexOutOfBoundsException(
            "GroupNode does not contain a value for index: " + idx);
      }

      public void copyValsToArray(Object[] arr) {
         if(row >= 0 && level >= 0) {
            final int len = Math.min(level + 1, arr.length);
            copyGroupValuesToArray(row, len, arr);
         }

         if(overwrittenValues != null) {
            for(Map.Entry<Integer, Object> entry : overwrittenValues.entrySet()) {
               final Integer idx = entry.getKey();

               if(idx < arr.length) {
                  arr[idx] = entry.getValue();
               }
            }
         }
      }

      public void setVal(int col, Object val) {
         checkOverwrittenValuesInit(1);
         overwrittenValues.put(col, val);
      }

      public void setVals(Object[] vals, int length) {
         checkOverwrittenValuesInit(length);

         for(int col = 0; col < length; col++) {
            overwrittenValues.put(col, vals[col]);
         }
      }

      public void setVals(Map<Integer, Object> vals) {
         if(vals != null) {
            checkOverwrittenValuesInit(vals.size());

            if(overwrittenValues != null) {
               overwrittenValues.putAll(vals);
            }
         }
      }

      private void checkOverwrittenValuesInit(int capacity) {
         if(overwrittenValues == null && capacity > 0) {
            overwrittenValues = new HashMap<>(capacity, 1);
         }
      }

      /**
       * Get string representation.
       */
      public final String toString() {
         StringBuilder sb = new StringBuilder();
         sb.append("\r\n");

         for(int i = 0; i < level; i++) {
            sb.append("\t");
         }

         sb.append("GroupNode ");
         sb.append("level=" + level);
         sb.append(", detail=" + (nodes == null));
         sb.append(", values=[" +
                      (!hasVals() ? null : Arrays.asList(getVals())) + "]");
         sb.append(", sums=[" + Arrays.toString(sums) + "]");

         for(int i = 0; nodes != null && i < nodes.size(); i++) {
            sb.append(nodes.get(i));
         }

         return sb.toString();
      }

      int level = -1; // group level
      int fid = -1;
      Map<Integer, Object> overwrittenValues = null;
      Object[] sums = null; // summary values
      List<GroupNode> nodes = null; // sub group nodes
      BitSet includedRows;
      int row = -1;
   }

   /**
    * An inner topn info stores basic topn info.
    */
   private static class InnerTopNInfo implements Serializable {
      /**
       * Constructor.
       *
       * @param scol summary col
       * @param n topn value
       * @param asc true if ascending
       */
      public InnerTopNInfo(int scol, int n, boolean asc, boolean kept,
                           boolean others) {
         this.scol = scol;
         this.n = n;
         this.asc = asc;
         this.kept = kept;
         this.others = others;
      }

      int scol;
      int n;
      boolean asc;
      boolean kept;
      boolean others;
   }

   /**
    * A comparator compares two group nodes.
    */
   private class GroupNodeComparer extends NodeComparer<GroupNode> {
      /**
       * Constructor.
       *
       * @param scol ths specified summary col
       * @param asc true if ascending, false otherwise
       */
      public GroupNodeComparer(int scol, boolean asc) {
         super(scol, asc);
      }

      /**
       * Get specified group node summary value.
       */
      @Override
      protected Object getResult(Object obj) {
         GroupNode node = (GroupNode) obj;
         return node.sums[scol];
      }

      /**
       * Compare two group nodes.
       */
      @Override
      public int compare(GroupNode v1, GroupNode v2) {
         boolean other1 = v1 instanceof MergedGroupNode;
         boolean other2 = v2 instanceof MergedGroupNode;

         if(other1 != other2 && isSortOthersLast()) {
            return other1 ? 1 : -1;
         }
         else {
            return super.compare(v1, v2);
         }
      }
   }

   /**
    * A comparator compares two group nodes by node values.
    */
   private class GroupNodeComparer2 extends NodeComparer<GroupNode> {
      /**
       * Constructor.
       */
      public GroupNodeComparer2(SortOrder order) {
         super(order.isAsc());
         this.order = order;
      }

      /**
       * Get specified group node value.
       */
      @Override
      protected Object getResult(Object obj) {
         GroupNode node = (GroupNode) obj;
         return node.getVal(node.level);
      }

      /**
       * Compare two group nodes.
       */
      @Override
      public int compare(GroupNode v1, GroupNode v2) {
         boolean other1 = v1 instanceof MergedGroupNode;
         boolean other2 = v2 instanceof MergedGroupNode;

         if(other1 != other2 && isSortOthersLast()) {
            return other1 ? 1 : -1;
         }

         if(order != null && order.isSpecific()) {
            int index1 = order.getGroupNameIndex(v1.getVal(v1.level));
            int index2 = order.getGroupNameIndex(v2.getVal(v2.level));

            // keep others?
            if(!Tool.equals(v1, v2) && index1 == index2) {
               return asc;
            }

            InnerTopNInfo topn = topnmap.get(v1.level);
            boolean topNOthers = topn != null && topn.others;

            // If sort is manual and exist topn others, 'others' should always be the last.
            // process named group others.
            if(!topNOthers && (other1 && index1 > order.getGroupNames().length ||
               other2 && index2 > order.getGroupNames().length))
            {
               return order.compare(v1.getVal(v1.level), v2.getVal(v2.level)) * asc;
            }

            return index1 - index2;
         }

         return super.compare(v1, v2);
      }

      private SortOrder order;
   }

   /**
    * SummaryFilter data descriptor.
    */
   class SummaryFilterDataDescriptor implements TableDataDescriptor {
      /**
       * Get table data path of a specified table column.
       * @param col the specified table column
       * @return table data path of the table column
       */
      @Override
      public TableDataPath getColDataPath(int col) {
         if(col >= getColCount()) {
            return new TableDataPath(-1,
               TableDataPath.UNKNOWN, null, new String[0], false, true);
         }

         String header = Util.getHeader(SummaryFilter.this, col).toString();
         return new TableDataPath(header);
      }

      /**
       * Get table data path of a specified table row.
       * @param row the specified table row
       * @return table data path of the table row
       */
      @Override
      public TableDataPath getRowDataPath(int row) {
         if(row < hcount) { // header
            return new TableDataPath(-1, TableDataPath.HEADER);
         }
         else if(moreRows(row + getTrailerRowCount())) { // summary
            int level = getSummaryLevel(row);
            return new TableDataPath(level, TableDataPath.SUMMARY);
         }
         else { // grand total
            return new TableDataPath(-1, TableDataPath.GRAND_TOTAL);
         }
      }

      /**
       * Get summary level of a row.
       * @param row the specified row.
       * @return summary level of the specified row.
       */
      private int getSummaryLevel(int row) {
         Integer iobj = groupmap.get(row);

         if(iobj != null) {
            return iobj;
         }

         // if header row or grand total row
         if(row < getHeaderRowCount() || hasGrand && !moreRows(row + 1)) {
            return -1;
         }

         // all rows are summary rows in a SummaryFilter
         // for a table which is not keep hierarchy, it seems
         // the innermost group level is reasonable, and table mode checker,
         // table layout validator is easy to maintain
         return isKeepHierarchy() ? cols.length - 1 :
                                    Math.max(0, cols.length - 1);
      }

      /**
       * Get table data path of a specified table cell.
       * @param row the specified table cell row
       * @param col the specified table cell col
       * @return table data path of the table cell
       */
      @Override
      public TableDataPath getCellDataPath(int row, int col) {
         if(col >= getColCount()) {
            return null;
         }

         String header = Util.getHeader(SummaryFilter.this, col).toString();
         Object val = getObject(row, col);
         Class<?> cls = val == null ? null : val.getClass();
         String dtype = Util.getDataType(cls);

         if(row < hcount) { // header
            return new TableDataPath(-1, TableDataPath.HEADER, dtype,
               new String[] {header});
         }
         else if(hasGrand && !moreRows(row + getTrailerRowCount())) { // grand total
            return new TableDataPath(-1, TableDataPath.GRAND_TOTAL, dtype,
               new String[] {header});
         }
         else { // summary
            int level = getSummaryLevel(row);
            return new TableDataPath(level, TableDataPath.SUMMARY, dtype,
               new String[] {header});
         }
      }

      /**
       * Check if a column belongs to a table data path.
       * @param col the specified table col
       * @param path the specified table data path
       * @return true if the col belongs to the table data path, false otherwise
       */
      @Override
      public boolean isColDataPath(int col, TableDataPath path) {
         String header = Util.getHeader(SummaryFilter.this, col).toString();
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
         int level = path.getLevel();

         if(row < hcount) { // header
            return type == TableDataPath.HEADER;
         }
         else if(moreRows(row + getTrailerRowCount())) { // summary
            return type == TableDataPath.SUMMARY &&
               (!isKeepHierarchy() || level == getSummaryLevel(row));
         }
         else { // grand total
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
         int level = path.getLevel();

         if(row < hcount) { // header
            return type == TableDataPath.HEADER;
         }
         else if(moreRows(row + getTrailerRowCount())) { // summary
            return type == TableDataPath.SUMMARY &&
               (!isKeepHierarchy() || level == getSummaryLevel(row));
         }
         else { // grand total
            return type == TableDataPath.GRAND_TOTAL;
         }
      }

      /**
       * Check if a cell belongs to a table data path.
       * @param row the specified table cell row
       * @param col the specified table cell col
       * @param path the specified table data path
       * @return true if the cell belongs to the table data path,
       * false otherwise
       */
      @Override
      public boolean isCellDataPath(int row, int col, TableDataPath path) {
         if(!path.getPath()[0].equals(Util.getHeader(SummaryFilter.this, col).toString())) {
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
         return getSummaryLevel(row);
      }

      /**
       * Get table type which is one of the table types defined in table data
       * descriptor like <tt>NORMAL_TABLE</tt>, <tt>CROSSTAB_TABLE</tt>, etc.
       * @return table type
       */
      @Override
      public int getType() {
         return SUMMARY_TABLE;
      }

      /**
       * Get table xmeta info.
       * @param path the specified table data path
       */
      @Override
      public XMetaInfo getXMetaInfo(TableDataPath path) {
         if(path == null || !path.isCell()) {
            return null;
         }

         Object obj = mmap.get(path);

         if(obj instanceof XMetaInfo) {
            return (XMetaInfo) obj;
         }
         else if(obj != null) {
            return null;
         }

         String header = path.getPath()[0];
         BooleanObject bobj = new BooleanObject();
         TableDataPath opath = getOriginalPath(path, bobj, false);
         boolean aggregated = bobj.getValue();
         TableDataDescriptor desc = table.getDescriptor();
         XMetaInfo minfo = opath == null ? null : desc.getXMetaInfo(opath);

         // not found? try special path
         if(minfo == null) {
            opath = getOriginalPath(path, bobj, true);
            aggregated = bobj.getValue();
            minfo = opath == null ? null : desc.getXMetaInfo(opath);
         }

         if(columnIndexMap == null) {
            columnIndexMap = new ColumnIndexMap(table, true);
         }

         int col = Util.findColumn(columnIndexMap, header, false);
         boolean date_part = false;
         int level = 0;

         if(col >= 0) {
            SortOrder order = getGroupOrder(col);

            if(order != null) {
               level = order.getOption();
               date_part = (level & SortOrder.PART_DATE_GROUP) != 0;
            }
         }

         // do not apply auto drill on aggregated column or date part group
         if((date_part || aggregated) && minfo != null) {
            minfo = minfo.clone();
            minfo.setXDrillInfo(null);
         }

         // for CrossTabFilter, SummaryFilter and GroupFilter, always use
         // created default format
         if(minfos != null && col >= 0) {
            minfo = Util.mergeMetaInfo(minfos[col], minfo, level);
         }

         if(col < 0) {
            String header2 = opath == null ? header : opath.getPath()[0];

            if(header2 != null && !header2.equals(header)) {
               col = Util.findColumn(columnIndexMap, header2, false);
               aggregated = isSummaryCol(col);
            }
         }

         Formula aggr = null;

         if(aggregated) {
            if(filterColumnIndexMap == null) {
               filterColumnIndexMap = new ColumnIndexMap(SummaryFilter.this, true);
            }

            int col0 = Util.findColumn(filterColumnIndexMap, header);
            aggr = col0 >= cols.length && col0 - cols.length < calcs.length
               ? calcs[col0 - cols.length] : new SumFormula();
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
         if(minfos == null) {
            table.moreRows(hcount + 1);
            createXMetaInfo();
         }

         return mexisting || table.containsFormat();
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
      private transient ColumnIndexMap filterColumnIndexMap = null;
   }

   private static final String TOTAL_LABEL = Catalog.getCatalog().getString("Total");

   private XSwappableTable sumrows = null;
   private TableLens table;
   private boolean def;
   private final int[] cols; // sorting columns
   private final int[] sums; // summary columns
   private volatile boolean inited; // flag indicates initialized or not
   private boolean hinited; // flag indicates header initialized or not
   private int hcount; // header row count
   private String[] headers; // summary column headers
   private Formula[] calcs;
   private Formula[] grand; // formulae
   private FormulaAgent[] fagents; // formula agent
   private SortOrder[] ordermap = null;
   private final Hashtable<Object, InnerTopNInfo> topnmap = new Hashtable<>();
   private final Hashtable<Object, InnerTopNInfo> sortByValMap = new Hashtable<>();
   private Hashtable<Integer, Integer> groupmap = new Hashtable<>();
   private boolean hierarchy = false; // show group hierarchy
   private String grandLabel; // grand total label
   private boolean hasGrand = false; // has grand total
   private String sumlabel = null; // summary row label
   private boolean aggTopN; // only aggregate topn rows

   private GroupNodeComparer topNAggregateComparer;
   private int topNAggregateCol = -1;
   private int topNAggregateN = 0;

   private TableDataDescriptor sdescriptor = null;
   private Hashtable<TableDataPath, Object> mmap = new Hashtable<>(); // xmeta info
   // percent by group level, default value 0 means the inner most group
   private int pglvl = 0;
   private XMetaInfo[] minfos;
   private boolean mexisting;

   private boolean timeSeries = false;
   private int timeSeriesLevel;
   private boolean sortTopN = true;

   private XSwappableObjectList<Formula[]> formulas;
   private volatile boolean completed = false;
   private transient Object[] gvals = null;
   private final transient Map<Integer, GroupNodeComparer> compmap = new HashMap<>(); // topn comparator map
   // sort by value compara map
   private final transient Map<Object, GroupNodeComparer> sortByValCompMap = new HashMap<>();
   private transient BitSet grpset = null; // group row set
   private transient int minSpecialOrder = Integer.MAX_VALUE;
   private transient Object[] grandtotals = null; // grand formula totals
   private transient SparseMatrix summaryLabelArea;
   private transient SparseMatrix cellValues;
   private transient volatile boolean cancelled = false;
   private final Lock cancelLock = new ReentrantLock();
   private boolean sortOthersLast = true; // whether sort others last
   private List<OrderInfo> orderInfo = new ArrayList<>();
   UserMessage userMsg = null;
   private transient DefaultComparator defaultComparator = new DefaultComparator(true);

   private static final Logger LOG = LoggerFactory.getLogger(SummaryFilter.class);
}
