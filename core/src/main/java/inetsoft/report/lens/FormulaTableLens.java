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
package inetsoft.report.lens;

import inetsoft.mv.DFWrapper;
import inetsoft.report.*;
import inetsoft.report.event.TableChangeListener;
import inetsoft.report.filter.DefaultTableChangeListener;
import inetsoft.report.internal.binding.FormulaHeaderInfo;
import inetsoft.report.internal.table.CachedTableLens;
import inetsoft.report.internal.table.CancellableTableLens;
import inetsoft.report.script.TableRow;
import inetsoft.report.script.TableRowScope;
import inetsoft.uql.XMetaInfo;
import inetsoft.uql.XTable;
import inetsoft.uql.table.XSwappableTable;
import inetsoft.uql.util.Drivers;
import inetsoft.uql.util.XUtil;
import inetsoft.util.*;
import inetsoft.util.audit.ExecutionBreakDownRecord;
import inetsoft.util.profile.ProfileUtils;
import inetsoft.util.script.*;
import org.mozilla.javascript.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.*;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * FormulaTableLens can be used to add more columns to a table. The new
 * column is populated by running a formula on each row. The formula can
 * access other columns on the same row. This allows simple calculation
 * to be performed without creating a new Java class. A new column is
 * created for each formula.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class FormulaTableLens extends AbstractTableLens
   implements TableFilter, CachedTableLens, DFWrapper, CancellableTableLens
{
   /**
    * Construct a formula table.
    * @param table original table.
    * @param headers the column headers used for corresponding formula.
    * @param formulas formulas used to create new columns.
    */
   public FormulaTableLens(TableLens table, String[] headers,
                           String[] formulas, ReportSheet report)
   {
      this.headers = headers;
      this.formulas = formulas;
      this.types = new Class[formulas.length];
      this.aligntypes = new Class[formulas.length];
      this.restricted = new boolean[formulas.length]; // defaults all to false
      this.report = report;
      this.listener = new DefaultTableChangeListener(this);

      if(headers == null || formulas == null || headers.length != formulas.length) {
         throw new RuntimeException(
            "Headers and/or formulas not specified correctly!");
      }

      // data type from script may be mixed, don't force to use specific type column (e.g. integer)
      rows = new XSwappableTable(formulas.length, false);
      // don't use object pooling since formula table may not complete for a long time
      // if rows are not fetched, which leaves a large object cache in memory.
      rows.setObjectPooled(true);
      // add header
      rows.addRow(new Object[table.getColCount() + formulas.length]);
      setTable(table);
   }

   /**
    * Construct a formulat table.
    * @param table original table.
    * @param headers the column headers used for corresponding formula.
    * @param formulas formulas used to create new columns.
    */
   public FormulaTableLens(TableLens table, String[] headers,
                           String[] formulas, ScriptEnv senv, Object scope) {
      this(table, headers, formulas, (ReportSheet) null);
      this.senv = senv;
      this.scope = scope;
   }

   /**
    * Construct a formulat table.
    * @param table original table.
    * @param headers the column headers used for corresponding formula.
    * @param formulas formulas used to create new columns.
    * @param mergeables sql formulas can merge or not.
    * @hidden
    */
   public FormulaTableLens(TableLens table, String[] headers,
                           String[] formulas, ScriptEnv senv, Object scope,
                           boolean[] mergeables) {
      this(table, headers, formulas, senv, scope);
      this.mergeables = mergeables;
   }

   /**
    * RDD delegate methods.
    */
   @Override
   public long dataId() {
      DFWrapper wrapper = getDFWrapper();
      return (wrapper != null) ? wrapper.dataId() : 0;
   }

   @Override
   public Object getDF() {
      DFWrapper wrapper = getDFWrapper();
      return (wrapper != null) ? wrapper.getDF() : null;
   }

   @Override
   public Object getRDD() {
      DFWrapper wrapper = getDFWrapper();
      return (wrapper != null) ? wrapper.getRDD() : null;
   }

   @Override
   public DFWrapper getBaseDFWrapper() {
      return getDFWrapper();
   }

   @Override
   public String[] getHeaders() {
      DFWrapper wrapper = getDFWrapper();

      if(wrapper != null) {
         List list = Arrays.asList(wrapper.getHeaders());
         list.addAll(Arrays.asList(headers));
         return (String[]) list.toArray(new String[list.size()]);
      }

      return null;
   }

   @Override
   public void setXMetaInfos(XSwappableTable lens) {
      DFWrapper wrapper = getDFWrapper();

      if(wrapper != null) {
         wrapper.setXMetaInfos(lens);
      }
   }

   /**
    * RDD delegate methods.
    */
   @Override
   public void completed() {
      DFWrapper wrapper = getDFWrapper();

      if(wrapper != null) {
         wrapper.completed();
      }
   }

   // get the DFWrapper nested in this XNode
   private DFWrapper getDFWrapper() {
      if(table instanceof DFWrapper) {
         return (DFWrapper) table;
      }

      return null;
   }

   // end delegate methods

   /**
    * Set formula field header info.
    * @hidden
    */
   public void setFormulaHeaderInfo(List<FormulaHeaderInfo> hinfos) {
      this.hinfos = hinfos;
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
      if(col < ncols) {
         return col;
      }

      return -1;
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
   public synchronized void setTable(TableLens table) {
      this.table = table;

      invalidate();
      this.table.addChangeListener(listener);
   }

   /**
    * Set the table name.
    */
   public void setTableName(String tname) {
      this.tableName = tname;
   }

   /**
    * Check if scripts should be restricted in access to system resources.
    * @param col the column index
    * @return true if column is restricted, false if not restricted
    */
   public boolean isRestricted(int col) {
      return restricted[col];
   }

   /**
    * Set if scripts should be restricted in access to system resources.
    * For example, restricted from using java packages.
    * @param col the column index to set
    * @param restricted true to restrict, false otherwise
    */
   public void setRestricted(int col, boolean restricted) {
      // @by davidd 2009-02-12 bug1232345495535 Scripting restrictions
      // are now column based.
      this.restricted[col] = restricted;
   }

   /**
    * Clear all cached data.
    */
   @Override
   public void clearCache() {
      // do nothing
   }

   /**
    * Invalidate the table filter forcely, and the table filter will perform
    * filtering calculation to validate itself.
    */
   @Override
   public void invalidate() {
      synchronized(this) {
         if(rows != null) {
            rows.dispose();
            rows = new XSwappableTable(formulas.length, false);
            // add header
            rows.addRow(new Object[table.getColCount() + formulas.length]);
         }

         tableRow = null;
         hrows = table.getHeaderRowCount(); // optimization
         ncols = table.getColCount(); // optimization
      }

      fireChangeEvent();
   }

   /**
    * Check if there are more rows. The row index is the row that will be
    * accessed. This method must block until the row is available, or
    * return false if the row does not exist in the table. This method is
    * used to iterate through the table, and allow partial table to be
    * accessed in report processing.
    * @param r row number.
    * @return true if the row exists, or false if no more rows.
    */
   @Override
   public boolean moreRows(int r) {
      boolean more = table.moreRows(r);

      if(r == EOT) {
         r = table.getRowCount();
      }

      // don't calculate if in header cells
      if(more && r < hrows) {
         return true;
      }

      long start = System.currentTimeMillis();
      lock.lock();

      try {
         int nrows = getProcessedRowCount();

         if(r < nrows) {
            return true;
         }


         if(senv == null) {
            senv = report.getScriptEnv();
         }

         if(tableRow == null) {
            scripts = new Object[formulas.length];
            String contextName = report != null && report.getContextName() != null
               ? "Report: " + report.getContextName() : null;
            boolean builtinDate = false;

            for(int i = 0; i < scripts.length; i++) {
               String colName = getColName(i + ncols);

               builtinDate = builtinDate || formulas[i].contains("new Date(");

               try {
                  scripts[i] = compile(formulas[i], senv, contextName, colName,
                                       ncols + i, tableName, mergeables == null || mergeables[i]);
               }
               // allow other scripts to proceed if one script failed. (58626)
               catch(ExpressionFailedException ex) {
                  CoreTool.addUserMessage(ex.getMessage());
               }
            }

            // this scriptable is reused for all rows
            tableRow = new TableRow2(this, hrows);
            tableRow.thisScope.setBuiltinDate(builtinDate);
            iterator = new TableIteratorScriptable();
            senv.addTopLevelParentScope(iterator);
            senv.addTopLevelParentScope(tableRow);
         }

         boolean first = true;
         JSFactory.startCache();
         // advance at least 10 to avoid going through this once per row
         final int advance = Math.min(Math.max(r / 100, 10), 100);
         final int maxr = Math.max(r, nrows + hrows + advance);

         for(int i = nrows + hrows; i <= maxr && table.moreRows(i) && scripts != null; i++) {
            // optimization, don't call get/put if never in the loop
            if(first) {
                runtime = true;

               // this must be called after put() so the parent scope is not set
               // to the top scope
               if(scope != null) {
                  iterator.setParentScope((Scriptable) scope);
                  tableRow.setParentScope(iterator);
               }

               first = false;
            }

            if(cancelled) {
               break;
            }

            int j = 0;
            Object[] row = new Object[formulas.length];

            // remove change listener then add change listener, for script might
            // change the table lens(set object), then the process will delegate
            // to its base table lens, which will fire change event. We'd better
            // ignore the event, otherwise it's like an hen-egg-hen game
            try {
               // in case of the sequence of calls:
               // CalcTableLens.evaluate() -> FormulaTableLens.exec
               //   -> NamedCellRange.getRuntimeGroups() (used in a reference to a table array)
               // the FormulaContext.getTable() should not return calc table since the
               // script is no executed inside the CalcTableLens. push this table to
               // the stack to prevent the calc table to be used by mistake. (42164)
               FormulaContext.pushTable(this);

               table.removeChangeListener(listener);
               iterator.setRow(i);
               tableRow.setRow(i);
               tableRow.setRowData(row);

               for(j = 0; j < scripts.length; j++) {
                  // disable access to java classes if modified from adhoc
                  FormulaContext.setRestricted(restricted[j]);
                  // row[] values are assigned in tableRow.getResult
                  currExec = new Point(ncols + j, i);
                  tableRow.getResult(j);
               }

               JSFactory.resetCache();
            }
            catch(RhinoException ex) {
               String colName = getColName(j + ncols);
               throw new ExpressionFailedException(ncols + j, colName, null, ex);
            }
            catch(ScriptException ex) {
               String colName = getColName(j + ncols);
               throw new ExpressionFailedException(ncols + j, colName, null, ex);
            }
            finally {
               // add empty row even if script failed since getObject() assumes rows contains
               // the same number of rows as formula table after moreRows is called.
               rows.addRow(row);

               FormulaContext.popTable();
               currExec = null;
               table.addChangeListener(listener);
               FormulaContext.setRestricted(false);
            }
         }
      }
      finally {
         lock.unlock();

         if(!more) {
            if(rows != null) {
               rows.complete();
            }

            completed = true;
         }

         if(reportName == null) {
            reportName = getReportName();
         }

         ProfileUtils.addExecutionBreakDownRecord(
            reportName, ExecutionBreakDownRecord.JAVASCRIPT_PROCESSING_CYCLE,
            start, System.currentTimeMillis());

      }

      JSFactory.stopCache();
      return more;
   }

   // get column name. avoid infinite recursing if there is no header row
   private String getColName(int col) {
      Object obj = hrows > 0 ? getObject(0, col) : null;
      return obj != null ? obj.toString() : "Column" + col;
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
      return ncols + formulas.length;
   }

   /**
    * Set the column type.
    */
   public void setColType(int col, Class type) {
      if(col >= ncols && col < ncols + formulas.length) {
         types[col - ncols] = type;
      }
   }

   /**
   * Set the alignment type.
   */
   public void setAlignTypes(Class[] aligntypes) {
      this.aligntypes = aligntypes;
   }

   /**
    * Return the number of rows on the top of the table to be treated
    * as header rows.
    * @return number of header rows.
    */
   @Override
   public int getHeaderRowCount() {
      return hrows;
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

   @Override
   public Class getColType(int col) {
      return (col < ncols) ? table.getColType(col) : types[col - ncols];
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
      return (col < ncols) ? table.getColWidth(col) : -1;
   }

   /**
    * Return the color for drawing the row border lines.
    * @param r row number.
    * @param c column number.
    * @return ruling color.
    */
   @Override
   public Color getRowBorderColor(int r, int c) {
      return (c < ncols) ?
         table.getRowBorderColor(r, c) :
         table.getRowBorderColor(r, ncols - 1);
   }

   /**
    * Return the color for drawing the column border lines.
    * @param r row number.
    * @param c column number.
    * @return ruling color.
    */
   @Override
   public Color getColBorderColor(int r, int c) {
      return (c < ncols) ?
         table.getColBorderColor(r, c) :
         table.getColBorderColor(r, ncols - 1);
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
      return (c < ncols) ?
         table.getRowBorder(r, c) :
         table.getRowBorder(r, ncols - 1);
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
      return (c < ncols) ?
         table.getColBorder(r, c) :
         table.getColBorder(r, ncols - 1);
   }

   /**
    * Return the cell gap space.
    * @param r row number.
    * @param c column number.
    * @return cell gap space.
    */
   @Override
   public Insets getInsets(int r, int c) {
      return (c < ncols) ? table.getInsets(r, c) : null;
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
      return (c < ncols) ? table.getSpan(r, c) : null;
   }

   /**
    * Return the per cell alignment.
    * @param r row number.
    * @param c column number.
    * @return cell alignment.
    */
   @Override
   public int getAlignment(int r, int c) {
      if(c < ncols) {
         return table.getAlignment(r, c);
      }
      else if(c - ncols < aligntypes.length || c - ncols < types.length) {
         Class type =
            aligntypes[c - ncols] != null ? aligntypes[c - ncols] :
            types[c - ncols] != null ? types[c - ncols] : String.class;
         return !isLeftAlign && Tool.isNumberClass(type) ?
            H_RIGHT | V_CENTER : H_LEFT | V_CENTER;
      }
      else {
         table.getAlignment(r, ncols - 1);
      }

      return (c < ncols) ?
         table.getAlignment(r, c) :
         table.getAlignment(r, ncols - 1);
   }

   /**
    * Return the per cell font. Return null to use default font.
    * @param r row number.
    * @param c column number.
    * @return font for the specified cell.
    */
   @Override
   public Font getFont(int r, int c) {
      return (c < ncols) ?
         table.getFont(r, c) :
         table.getFont(r, ncols - 1);
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
      return (c < ncols) ? table.isLineWrap(r, c) : true;
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
      return (c < ncols) ?
         table.getForeground(r, c) :
         table.getForeground(r, ncols - 1);
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
      return (c < ncols) ?
         table.getBackground(r, c) :
         table.getBackground(r, ncols - 1);
   }

   /**
    * Check if is primitive.
    * @return <tt>true</tt> if is primitive, <tt>false</tt> otherwise.
    */
   @Override
   public final boolean isPrimitive(int col) {
      return col < ncols ? table.isPrimitive(col) : false;
   }

   /**
    * Check if the value at one cell is null.
    * @param r the specified row index.
    * @param c column number.
    * @return <tt>true</tt> if null, <tt>false</tt> otherwise.
    */
   @Override
   public final boolean isNull(int r, int c) {
      if(c < ncols) {
         return table.isNull(r, c);
      }

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
      if(c < ncols) {
         return table.getObject(r, c);
      }
      else if(r < hrows) {
         return headers[c - ncols];
      }

      // @by jasons, this is an artifact of old source code. rows should never
      //             be null, and invalidate() is called whenever setTable() is
      //             called. I left this here as a sanity check in case rows is
      //             ever null.
      if(rows == null) {
         invalidate();
      }

      int row = r - hrows;

      // @by jasons, moved the call to moreRows() outside of the previous if
      //             block because it should be called regardless of the state
      //             of rows. If this is not done, this method will always return
      //             null for non-header rows unless moreRows() is explicitly
      //             called before this method is called.
      if(row >= getProcessedRowCount()) {
         moreRows(r);
      }

      try {
         if(isCancelled()) {
            return null;
         }

         return rows.getObject(row + 1, c - ncols);
      }
      catch(Exception ex) {
         // row is out of bound
         return null;
      }
   }

   /**
    * Get the double value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the double value in the specified row.
    */
   @Override
   public final double getDouble(int r, int c) {
      if(c < ncols) {
         return table.getDouble(r, c);
      }

      return 0D;
   }

   /**
    * Get the float value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the float value in the specified row.
    */
   @Override
   public final float getFloat(int r, int c) {
      if(c < ncols) {
         return table.getFloat(r, c);
      }

      return 0F;
   }

   /**
    * Get the long value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the long value in the specified row.
    */
   @Override
   public final long getLong(int r, int c) {
      if(c < ncols) {
         return table.getLong(r, c);
      }

      return 0L;
   }

   /**
    * Get the int value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the int value in the specified row.
    */
   @Override
   public final int getInt(int r, int c) {
      if(c < ncols) {
         return table.getInt(r, c);
      }

      return 0;
   }

   /**
    * Get the short value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the short value in the specified row.
    */
   @Override
   public final short getShort(int r, int c) {
      if(c < ncols) {
         return table.getShort(r, c);
      }

      return 0;
   }

   /**
    * Get the byte value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the byte value in the specified row.
    */
   @Override
   public final byte getByte(int r, int c) {
      if(c < ncols) {
         return table.getByte(r, c);
      }

      return 0;
   }

   /**
    * Get the boolean value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the boolean value in the specified row.
    */
   @Override
   public final boolean getBoolean(int r, int c) {
      if(c < ncols) {
         return table.getBoolean(r, c);
      }

      return false;
   }

   /**
    * Set a cell value.
    * @param r row index.
    * @param c column index.
    * @param val cell value.
    */
   @Override
   public void setObject(int r, int c, Object val) {
      boolean execThis = currExec != null && r == currExec.y && c == currExec.x;
      /* handle execThis by setting the value in tableRow instead of throwing an exception
      if(currExec != null && r == currExec.y && c == currExec.x) {
         RuntimeException ex =
            new RuntimeException("Variable can not have same name as column!");

         // 1. script exception will always be catched at fine level
         // 2. do not log the information again and again
         if(!logged) {
            logged = true;
            // this is actually incorrect, if the exception is not handled here
            // (i.e. rethrown), it should not be logged here either
            LOG.error(ex.getMessage(), ex);
         }

         throw ex;
      }
      */

      // if setObject() called on the cell that is executing, we set the result in
      // tableRow so it's used as the result
      if(execThis) {
         int c2 = c - ncols;
         tableRow.setResult(c2, val);
      }
      else if(r >= hrows && c >= ncols && moreRows(r)) {
         int c2 = c - ncols;

         if(c2 < rows.getColCount()) {
            // row[c2] = val;
            // fireChangeEvent();
            throw new RuntimeException("Unsupported method called!");
         }
      }
      else if(c < ncols) {
         table.setObject(r, c, val);
      }
      else if(r < hrows) {
         headers[c - ncols] = val;
      }
   }

   /**
    * Get internal table data descriptor which contains table structural
    * infos.
    * @return table data descriptor
    */
   @Override
   public TableDataDescriptor getDescriptor() {
      if(descriptor == null) {
         descriptor = new TableDataDescriptor0(table.getDescriptor());
      }

      return descriptor;
   }

   /**
    * Returns true if there is an formula header with that name
    * @param attributeName the name of the attribute
    */
   public boolean containsAttribute(String attributeName) {
      for(int i = 0; i < headers.length; i++) {
         if(attributeName.equals(headers[i])) {
            return true;
         }
      }

      return false;
   }

   /**
    * Dispose the table to clear up temporary resources.
    */
   @Override
   public synchronized void dispose() {
      senv = null;
      table.dispose();

      if(rows != null) {
         rows.dispose();
         rows = null;
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

      return (identifier == null && col >= 0) ?
         table.getColumnIdentifier(col) : identifier;
   }

   // this class allows other formula columns to be accessed by formulas
   class TableRow2 extends TableRow {
      public TableRow2(XTable table, int row) {
         super(table, row);
         thisScope = new TableRowScope(this, "field");
      }

      // set the array to hold the results
      public void setRowData(Object[] row) {
         this.row = row;
         this.exec = new boolean[row.length];
      }

      // check if the column has been executed
      public boolean isExeced(int col) {
         return exec[col];
      }

      // called by TableRow to get a cell value
      @Override
      protected Object get(XTable table, Method getMethod, int row, int col) throws Exception {
         if(col < ncols || table != FormulaTableLens.this) {
            return super.get(table, getMethod, row, col);
         }

         if(row < getRow()) {
            return FormulaTableLens.this.getObject(row, col);
         }
         else if(row > getRow()) {
            LOG.warn("Formula column can't forward reference other rows.");
            return null;
         }

         try {
            currExec = new Point(col, row);
            return getResult(col - ncols);
         }
         finally {
            currExec = null;
         }
      }

      // set the result from script
      public boolean setResult(int col, Object val) {
         if(col < this.row.length) {
            this.row[col] = val;
            this.explicit = true;
            return true;
         }

         return false;
      }

      // get the calculated result for the row
      public Object getResult(int col) {
         if(exec[col]) {
            return row[col];
         }

         exec[col] = true;
         explicit = false;
         Object result = exec(col);

         if(explicit) {
            return row[col];
         }

         // convert to declared type. ignore array (47059).
         if(types[col] != null && !(result instanceof Object[]) && result != null &&
            !types[col].equals(result.getClass()))
         {
            Object nresult = Tool.getData(types[col], result);

            // if type conversion causes data lost, don't force it (unless spark which causes error)
            if(nresult != null || forceType) {
               result = nresult;
            }
         }

         return row[col] = result;
      }

      // execute script and return result
      private Object exec(int col) {
         if(scripts[col] == null) {
            return null;
         }

         try {
            Scriptable scope0 = (scope != null) ? thisScope : iterator;

            // for Feature #26586, add javascript execution time record for current report.
            row[col] = FormulaTableLens.exec(scripts[col], senv, scope0,
                                             formulas[col], runtime, "XXX");
         }
         catch(Exception ex) {
            throw new ScriptException(ex.getMessage());
         }

         return row[col];
      }

      private TableRowScope thisScope;
      private Object[] row;
      private boolean[] exec;
      private boolean explicit = false;
   }

   /**
    * ColumnMapFilter data descriptor.
    */
   private class TableDataDescriptor0 extends DefaultTableDataDescriptor {
      /**
       * Create a ColumnMapFilterDataDescriptor.
       * @param descriptor the base descriptor
       */
      public TableDataDescriptor0(TableDataDescriptor descriptor) {
         super(FormulaTableLens.this);

         this.descriptor = descriptor;
      }

      /**
       * Get table xmeta info.
       * @param path the specified table data path
       * @return meta info of the table data path
       */
      @Override
      public XMetaInfo getXMetaInfo(TableDataPath path) {
         XMetaInfo info = descriptor.getXMetaInfo(path);

         if(path == null) {
            return info;
         }

         FormulaHeaderInfo hinfo = null;
         String[] paths = path.getPath();
         String header = paths == null || paths.length <= 0 ? null : paths[0];

         // find header info
         if(header != null && hinfos != null) {
            for(FormulaHeaderInfo tmp : hinfos) {
               // do not use fuzzy compare, it should absolute equals,
               // because in AssetQuery have fixed the headers
               if(header.equals(tmp.getFormualName())) {
                  hinfo = tmp;
                  break;
               }
            }
         }

         // if not found, try original column's meta info
         if(info == null && hinfo != null) {
            String oheader = hinfo.getOriginalColName();

            if(oheader != null) {
               TableDataPath path2 = new TableDataPath(path.getLevel(),
                  path.getType(), path.getDataType(),
                  new String[] {oheader});
               info = descriptor.getXMetaInfo(path2);
            }
         }

         if(hinfo != null) {
            info = hinfo.merge(info);
         }

         return info;
      }

      /**
       * Check if contains format.
       * @return true if contains format.
       */
      @Override
      public boolean containsFormat() {
         if(descriptor.containsFormat()) {
            return true;
         }

         if(hinfos == null) {
            return false;
         }

         for(FormulaHeaderInfo info : hinfos) {
            if(info != null && info.containsFormat()) {
               return true;
            }
         }

         return false;
      }

      /**
       * Check if contains drill.
       * @return <tt>true</tt> if contains drill.
       */
      @Override
      public boolean containsDrill() {
         return descriptor.containsDrill();
      }

      private TableDataDescriptor descriptor;
   }

   private final class TableIteratorScriptable extends ScriptableObject implements DynamicScope {
      public TableIteratorScriptable() {
      }

      public void setRow(Integer row) {
         this.row = row;
      }

      @Override
      public String getClassName() {
         return "TableIterator";
      }

      @Override
      public Object[] getIds() {
         return new String[] { "field", "row" };
      }

      @Override
      public Object get(String name, Scriptable start) {
         if("field".equals(name)) {
            return tableRow;
         }
         else if("row".equals(name)) {
            return row;
         }

         return super.get(name, start);
      }

      @Override
      public boolean has(String name, Scriptable start) {
         if("field".equals(name) || "row".equals(name)) {
            return true;
         }

         return super.has(name, start);
      }

      private Integer row = null;
   }

   /**
    * Compile script
    * @hidden
    */
   public static Object compile(String formula, ScriptEnv senv,
                                String contextName, String colName,
                                int colIdx, String tableName, boolean mergeable)
   {
      return scriptCache.get(formula, senv, (Exception ex) -> {
            String suggestion = senv.getSuggestion(ex, null);
            String rname = contextName != null ? contextName + "\n" : null;
            String msg = "Script error: " + ex.getMessage() +
               (suggestion != null ? "\nTo fix: " + suggestion : "") +
               "\nFormula failed:\n" + XUtil.numbering(formula);
            msg = rname == null ? msg : rname + msg;

            if(LOG.isDebugEnabled()) {
               LOG.debug(msg, ex);
            }
            else {
               LOG.warn(msg);
            }

            String str = msg;

            if(!mergeable) {
               str = Catalog.getCatalog().getString(
                  "viewer.viewsheet.sqlException.error", colName, tableName);
            }

            throw new ExpressionFailedException(
               colIdx, colName, null,
               new ScriptException(rname == null ? str : rname + str));
         });
   }

   /**
    * Execute script.
    * @hidden
    */
   public static Object exec(Object script, ScriptEnv senv, Object scope,
                             String formula, boolean runtime, Object defVal)
   {
      Object val = null;

      try {
         // if parent scope if set, use the row as scope (the parent scope
         // is the parent of row scope) so column can be referenced by name
         // without using field[]. Otherwise use default scope.
         val = senv.exec(script, scope, null, null);

         // avoid -0.0
         if(val instanceof Double) {
            Double dobj = (Double) val;

            if(dobj == 0.0) {
               val = 0.0;
            }
         }
      }
      catch(Exception ex) {
         // if in design mode, ignore the error
         // @by larryl, we must run the formula script since the
         // formula lens may be refreshed as part of saving to archive
         // and if the formulas are not run, the data would be
         // corrupted
         if(runtime) {
            String msg = "JavaScript error: " + ex.getMessage() +
               "\nFormula failed:\n" + XUtil.numbering(formula);
            LOG.warn(msg);

            throw new ScriptException(Catalog.getCatalog().getString(
                                         "JavaScript error") + ": " + ex.getMessage(), ex);
         }
         else {
            val = defVal;
         }
      }

      return val;
   }

   // Get the number of rows already processed
   private int getProcessedRowCount() {
      XSwappableTable rows = this.rows;

      if(rows == null) {
         return 0;
      }

      int nrows = rows.getRowCount();

      if(nrows < 0) {
         nrows = -(nrows + 1);
      }

      return nrows - 1;
   }

   /**
    * Cancel the table lens and running queries if supported.
    */
   @Override
   public void cancel() {
      cancelLock.lock();

      try {
         cancelled = !completed;

         if(table instanceof CancellableTableLens) {
            ((CancellableTableLens) table).cancel();
         }
      }
      finally {
         cancelLock.unlock();
      }
   }

   /**
    * Check the TableLens to see if it is cancelled.
    */
   @Override
   public boolean isCancelled() {
      return cancelled;
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

   @Serial
   private void readObject(ObjectInputStream in) throws ClassNotFoundException, IOException {
      in.defaultReadObject();
      cancelLock = new ReentrantLock();
      lock = new ReentrantLock();
      senv = new JavaScriptEnv();
   }

   private TableLens table;
   private String tableName = null;
   private Object[] headers;
   private String[] formulas;
   private boolean[] mergeables;
   private Class[] types;
   private Class[] aligntypes;
   private boolean[] restricted; // script restriction per column
   private transient ReportSheet report;
   private transient ScriptEnv senv;
   private transient Object scope;
   private XSwappableTable rows;
   private int hrows, ncols;
   private TableDataDescriptor descriptor;
   private Point currExec;
   private List<FormulaHeaderInfo> hinfos;
   private boolean completed;       // completed flag
   private volatile boolean cancelled;       // cancelled flag
   private transient Lock cancelLock = new ReentrantLock();

   private transient Object[] scripts; // compiled javascripts
   private transient TableRow2 tableRow; // row javascript object
   private transient boolean runtime = true;
   private transient TableChangeListener listener = null;
   private transient TableIteratorScriptable iterator = null;
   private transient Lock lock = new ReentrantLock();
   private transient boolean forceType = Drivers.getInstance().isDataCached();
   private transient String reportName;

   private static final ScriptCache scriptCache = new ScriptCache(100, 60000);
   private static final Logger LOG = LoggerFactory.getLogger(FormulaTableLens.class);
}
