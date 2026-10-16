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
import inetsoft.util.script.graal.GraalJavaScriptEngine;
import inetsoft.util.script.graal.GraalJavaScriptEnv;
import inetsoft.util.script.graal.ScriptScope;
import inetsoft.util.script.graal.ScriptTimeoutGuard;
import inetsoft.util.script.graal.pool.WorksheetScriptEnv;
import inetsoft.util.stall.LockStallException;
import inetsoft.util.stall.WaitRecord;
import inetsoft.util.stall.WaitRegistry;
import inetsoft.util.swap.SwapFileReadException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.*;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
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
   implements TableFilter, CachedTableLens, DFWrapper, CancellableTableLens,
   ChainScriptLock.Source
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
      XSwappableTable initial = rows;
      setTable(table);

      // invalidate() does not dispose the row table it replaces, since a reader may hold it,
      // but no reader has seen this one yet (bug #77243)
      if(rows != initial) {
         initial.dispose();
      }
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
    * For example, restricted from using java packages. Scripts always run
    * restricted now (bug #77396), so this flag is only recorded.
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
    *
    * <p>This does not take the lens lock: it is called from the base's change event, under
    * the base's monitor (e.g. a condition filter's), while a batch holding the lens lock
    * enters that monitor on every base read, so taking it here would deadlock. Instead the new
    * row table is built completely before it is published, and a batch or reader works on the
    * row table it read once (bug #77243). The old row table is not disposed here: a batch or
    * reader may still hold it. Its finalizer frees it once none does.
    */
   @Override
   public void invalidate() {
      synchronized(this) {
         hrows = table.getHeaderRowCount(); // optimization
         ncols = table.getColCount(); // optimization

         if(rows != null) {
            rows = createRowTable();
         }

         // the stopped rows of the old row table are not cleared here, without the lens lock:
         // a reader that still reads the old table must still get the stop. They are for the
         // old table only, the new one computes them again (bug #77949)

         // the compiled scripts and row scriptable are rebuilt by the next batch, since they
         // belong to the old row table (TableRow2.batchRows); an in-flight batch keeps its own.
         // completed is not reset: once the lens has computed all its rows, cancel() leaves
         // it alone, as a lens shared or cached by several consumers must not be emptied for
         // all of them by one consumer's cancel, and cancelled is never reset (bug #77243)
      }

      fireChangeEvent();
   }

   /**
    * Create an empty row table, with its header row added before the table is published:
    * a batch reading a header-only table would compute the base header as the first data row
    * (bug #77243).
    */
   private XSwappableTable createRowTable() {
      XSwappableTable nrows = new XSwappableTable(formulas.length, false);
      nrows.addRow(new Object[table.getColCount() + formulas.length]);
      return nrows;
   }

   /**
    * Replace the row table a batch computes once a row of it stalled, or read a lost swap
    * file, inside its formulas, if the formulas own vars. The row is computed again by a
    * later read, but the formulas that ran before the failure may have changed their vars,
    * so computing it again on them would apply that change twice and shift every later row.
    * A new row table is computed from the first row with fresh vars instead, so a later read
    * of this lens still gets every value, as after a stall of formulas that own no vars
    * (bug #78133). The rows the old table holds are values the new one computes again, so no
    * change event is fired; it is not disposed, as a reader may hold it (bug #77243).
    *
    * <p>Called under the lens lock. A row table invalidate() published meanwhile is kept.
    *
    * @param target the row table of the batch.
    *
    * @return {@code true} if the row table was replaced.
    */
   private boolean discardRows(XSwappableTable target) {
      // without a scope the row scope is not in the chain, and the vars are not owned
      if(scope == null || target == null ||
         GraalJavaScriptEngine.collectOwnedVarNames(Arrays.asList(formulas)).isEmpty())
      {
         return false;
      }

      return ROWS.compareAndSet(this, target, createRowTable());
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
      // advance at least 10 to avoid going through this once per row
      final int advance = Math.min(Math.max(r / 100, 10), 100);
      // the last row processed in this pass, see lockForRow(); in pool mode the end of the
      // pooled batch, whose base rows are loaded before the lens lock (finding F1)
      final int lastRow = Math.max(r, getProcessedRowCount() + hrows +
                                      Math.max(advance, peekPoolBatch(r)));
      LockedRows locked = lockForRow(r, lastRow);
      Lock execLock = locked.execLock();
      // no row remains to compute, and none is computed without the engine lock
      boolean computed = locked.computed();
      ScriptSpan span = ScriptSpan.NONE;
      // the home of this table's objects, which this batch's checkout prefers (Testing #77123)
      Object homeHint = TableRowScope.NO_HINT;
      // whether a batch of this table that took a context of its own is open on this thread,
      // read and restored under the lens lock
      final boolean inOwnBatch = ownBatch;
      // the span of that batch, restored with ownBatch
      final ScriptSpan inOwnSpan = ownSpan;
      // whether this batch took a context of its own (Testing #77123, cond-home)
      boolean own = false;
      // set when this batch ends in a lock stall: the rows past the stall are not computed,
      // so the row table is not complete even if the base has no more rows (bug #77123)
      boolean stalled = false;
      // set when this batch ends in any other exception, such as a script error of one row:
      // the rows past it are not computed yet, so the row table is not complete (bug #77123)
      boolean failed = false;
      // set when a row stalled after formulas that own vars may have changed them, and the
      // row table was replaced to compute again from fresh vars (bug #78133)
      boolean discarded = false;
      // the row table this batch computes, the one lockForRow() read under the lock and chose
      // the engine lock for: invalidate() may publish a new one at any time without the lock,
      // and the rows computed here belong to this one only (bug #77243)
      XSwappableTable target = locked.rows();

      try {
         // disposed
         if(target == null) {
            return more;
         }

         // written by invalidate() before it published target
         final int hrows = this.hrows;
         final int ncols = this.ncols;
         int nrows = getProcessedRowCount(target);

         // lens rows hrows..nrows + hrows - 1 are computed. Off the pool lockForRow() already
         // answers them as COMPUTED; in pool mode a re-read of the last computed row must not
         // count as a sequential read that starts the next batch (context-pool regression D1)
         if(r >= hrows ? r < nrows + hrows : r < nrows) {
            return true;
         }

         if(computed) {
            return more;
         }


         if(senv == null) {
            senv = report.getScriptEnv();
         }

         // pool mode: one claimed span for this whole batch, so a script global lives for the
         // batch and the context is cleaned once at its end (bug #76960, spec §5.3); nested
         // batches share the claim. The batch's base rows were loaded before the lens lock
         // (finding F1), so a pooled formula base computed them under its own claim
         homeHint = tableRow == null ? TableRowScope.NO_HINT : tableRow.thisScope.preferHome();
         // a resident table, whose vars hold arrays or objects on a home, takes a context of
         // its own for the batch, also inside another span of this thread (a condition
         // filter's population, another table's batch, a query build): that context, the home
         // of the objects, is given back at the batch end, while this thread may still hold
         // the outer span, so a batch of this table on another thread, which holds this lens's
         // lock, takes the home instead of losing the objects (Testing #77123, A1). A batch of
         // a table that is not resident yet (its first one, or one of primitives or Dates
         // only) shares the span it runs in, as a batch always did; if its vars hold objects
         // at its end and that span outlives it, they are saved as a tree there instead of
         // staying on the span's context (snapshotOwnedObjects). A batch nested in an own
         // batch of this table re-enters that batch's span, also below a batch of another
         // table nested in it (A -> B -> A): the objects live on its context
         own = senv != null && !inOwnBatch && tableRow != null &&
            tableRow.batchRows == target && tableRow.thisScope.takesOwnSpan();
         span = senv == null ? ScriptSpan.NONE : own ? senv.openOwnSpan()
            : inOwnBatch ? inOwnSpan.reenter() : senv.openSpan();
         ownBatch = inOwnBatch || own;
         ownSpan = own ? span : inOwnSpan;

         if(tableRow == null || tableRow.batchRows != target) {
            scripts = new Object[formulas.length];
            String contextName = report != null && report.getContextName() != null
               ? "Report: " + report.getContextName() : null;
            boolean builtinDate = false;
            // the vars the row table below owns: a read-before-write of one of them is a
            // supported accumulator, not a state hazard for the lint (Testing #77123). Without
            // a scope the row scope is not in the chain, so it owns nothing (main's behaviour)
            Set<String> owned = scope == null ? null
               : GraalJavaScriptEngine.collectOwnedVarNames(Arrays.asList(formulas));
            boolean pooled = senv instanceof WorksheetScriptEnv;

            for(int i = 0; i < scripts.length; i++) {
               String colName = getColName(i + ncols);

               builtinDate = builtinDate || formulas[i].contains("new Date(");

               try {
                  scripts[i] = compile(formulas[i], senv, contextName, colName,
                                       ncols + i, tableName, mergeables == null || mergeables[i]);
                  ScriptStateLint.checkColumn(formulas[i], scripts[i], this, hrows, colName,
                                              tableName, contextName, owned, pooled);
               }
               // allow other scripts to proceed if one script failed. (58626)
               catch(ExpressionFailedException ex) {
                  CoreTool.addUserMessage(ex.getMessage());
               }
            }

            // this scriptable is reused for all rows of target
            tableRow = new TableRow2(this, hrows, ncols, target);
            tableRow.thisScope.setBuiltinDate(builtinDate);
            iterator = new TableIteratorScriptable();
            senv.addTopLevelParentScope(iterator);
            senv.addTopLevelParentScope(tableRow);
         }

         // this batch's own, a nested batch for a newer row table may replace the fields
         final TableRow2 tableRow = this.tableRow;
         final TableIteratorScriptable iterator = this.iterator;
         final Object[] scripts = this.scripts;
         boolean first = true;
         // advance at least 10 to avoid going through this once per row; in pool mode at
         // least one pooled batch, so one context clean serves a batch (spec §14.8).
         // Design cost (spec §14.14): in pool mode the lens lock is held for up to
         // maxBatchRows rows of script evaluation, so a concurrent reader of an already
         // computed row can wait that long for this batch to finish. A condition filter
         // reads this lens in reads of at most maxBatchRows rows; a direct far or EOT read
         // computes through r in one batch, as off the pool
         final int batch = Math.max(advance, nextPoolBatch(span, r, nrows + hrows));
         // stop at the rows whose base was loaded before taking the locks, see lockForRow()
         final int maxr = Math.min(Math.max(r, nrows + hrows +
                                               (execLock != null ? advance : batch)), lastRow);
         // the first script error of this batch, thrown once the batch is computed: a reader
         // that goes on after it reads the rest of the batch without recomputing, so a column
         // that fails on many rows ends one batch (one context clean, pool on), not one per
         // failing row (Testing #77123, O2)
         ExpressionFailedException firstFailure = null;

         // stop once invalidate() published a new row table: the rest of the rows belong to it,
         // and the next read computes them there (bug #77243)
         for(int i = nrows + hrows; i <= maxr && table.moreRows(i) && scripts != null &&
                rows == target; i++)
         {
            // optimization, don't call get/put if never in the loop
            if(first) {
                runtime = true;

               // this must be called after put() so the parent scope is not set
               // to the top scope
               if(scope != null) {
                  iterator.setParentScope((ScriptScope) scope);
                  tableRow.thisScope.setParentScope(iterator);
               }

               first = false;
            }

            if(cancelled) {
               break;
            }

            int j = 0;
            Object[] row = new Object[formulas.length];
            // put back the flag of the caller, which may be a restricted script that
            // reads this table, instead of clearing it (bug #77396)
            boolean restricted0 = FormulaContext.isRestricted();

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
                  // expression columns are written by end users, so every column runs
                  // restricted, not only the ones edited ad hoc (bug #77396)
                  FormulaContext.setRestricted(true);
                  // row[] values are assigned in tableRow.getResult
                  currExec = new Point(ncols + j, i);
                  tableRow.getResult(j);
               }
            }
            catch(LockStallException ex) {
               stalled = true;
               discarded = discardRows(target);
               throw ex;
            }
            // a lost swap file is not a script error either: like a stall, the row is not
            // kept, and the reader gets the failure itself, so a later read computes the row
            // again instead of reading a null cell (bug #77912)
            catch(SwapFileReadException ex) {
               stalled = true;
               discarded = discardRows(target);
               throw ex;
            }
            catch(ScriptException ex) {
               LockStallException stall = LockStallException.find(ex);

               if(stall != null) {
                  stalled = true;
                  discarded = discardRows(target);
                  throw stall;
               }

               SwapFileReadException swap = SwapFileReadException.find(ex);

               if(swap != null) {
                  stalled = true;
                  discarded = discardRows(target);
                  throw swap;
               }

               String colName = getColName(j + ncols);
               ExpressionFailedException failure =
                  new ExpressionFailedException(ncols + j, colName, null, ex);

               // a timeout or cancel ends the batch at once: the next row would wait as
               // long, under the lens lock
               if(ScriptTimeoutGuard.isStop(ex) || Thread.currentThread().isInterrupted() ||
                  cancelled)
               {
                  if(firstFailure != null) {
                     for(int failedRow : firstFailure.getFailedRows()) {
                        failure.addFailedRow(failedRow);
                     }
                  }

                  failure.addFailedRow(i);
                  // the row is kept, so the later rows go on from the vars as the stopped
                  // exec left them, but its cells from the stopped column on are not values:
                  // every read of them fails with the stop instead of reading null. The row is
                  // not computed again from that state: only a new row table is (bug #77949)
                  failure.setStopped(true);
                  // the records of a replaced row table are kept until now, for a reader that
                  // still reads it (they hold it weakly)
                  final XSwappableTable stoppedTable = target;
                  stoppedRows.values().removeIf(s -> s.rows().get() != stoppedTable);
                  stoppedRows.put(i, new StoppedRow(new WeakReference<>(target), j, failure));
                  throw failure;
               }

               if(firstFailure == null) {
                  firstFailure = failure;
               }

               // the reader learns every failed row of the batch from the one exception
               firstFailure.addFailedRow(i);
            }
            finally {
               // add empty row even if script failed since getObject() assumes rows contains
               // the same number of rows as formula table after moreRows is called.
               // a stalled row is not kept: its cells are not values, and a later read
               // computes the row again (bug #76967). a row of a row table that was replaced
               // while it was computed is not kept either: it may be half-computed, and its
               // position is past the rows the replaced table's readers use (bug #77243)
               if(!stalled && rows == target) {
                  target.addRow(row);
               }

               FormulaContext.popTable();
               currExec = null;
               table.addChangeListener(listener);
               FormulaContext.setRestricted(restricted0);
            }
         }

         if(firstFailure != null) {
            throw firstFailure;
         }
      }
      catch(LockStallException ex) {
         // also a stall of the base read in the loop condition, outside the row's catch
         stalled = true;
         throw ex;
      }
      catch(RuntimeException | Error ex) {
         failed = true;
         throw ex;
      }
      finally {
         // the lock is released even if closing the span throws
         try {
            // a complete row table runs no formula until it is computed again in a new
            // row scope: don't keep a context alive by its vars' objects (Testing #77123)
            // read the field once: a nested batch for a row table invalidate() published
            // meanwhile can replace it, and only the scriptable of target is complete (bug #77243)
            TableRow2 completedRow = tableRow;

            // span.close() in its own finally: nothing here may skip it or the unlock
            try {
               // a discarded row table runs no formula again either (bug #78133)
               if((discarded || !more && !stalled && !failed) && completedRow != null &&
                  completedRow.batchRows == target)
               {
                  completedRow.thisScope.releaseOwnedObjects();
               }
               // still on the slot this batch claimed, whatever ended the batch: a later batch
               // may run on another slot, which rebuilds a Date var from this snapshot
               else if(completedRow != null) {
                  completedRow.thisScope.snapshotOwnedObjects(span, !own && !inOwnBatch);
               }
            }
            finally {
               try {
                  span.close();
               }
               finally {
                  ownBatch = inOwnBatch;
                  ownSpan = inOwnSpan;
                  TableRowScope.restoreHome(homeHint);
               }
            }
         }
         finally {
            lock.unlock();
         }

         if(execLock != null) {
            JavaScriptEngine.popHeldScriptLock();
            execLock.unlock();
         }

         // a stalled or failed batch leaves the row table open: a completed one may be swapped
         // out, and the rows a resumed read appends to it would be lost (bug #77123)
         // a replaced row table is not completed, nor is the lens: the current row table has
         // not been computed by this batch (bug #77243)
         if(!more && !stalled && !failed && target != null && rows == target) {
            target.complete();
            completed = true;
         }

         if(reportName == null) {
            reportName = getReportName();
         }

         ProfileUtils.addExecutionBreakDownRecord(
            reportName, ExecutionBreakDownRecord.JAVASCRIPT_PROCESSING_CYCLE,
            start, System.currentTimeMillis());

      }

      return more;
   }

   /**
    * Acquire this lens's lock for computing up to row {@code r}. If rows remain to be
    * computed, the script engine's execution lock is acquired first, the same order
    * as PostProcessor's condition filter, which takes the engine lock before reading
    * its base tables. Taking this lens's lock first and the engine lock in exec()
    * deadlocks against a condition filter reading this lens on another thread when
    * the lens is shared by several table chains (bug #76935). The engine lock is
    * recorded on the thread so an async base lens runs or is lent the lock while
    * this thread waits for it (bug #76938). The base rows up to {@code lastRow} are
    * loaded before the engine lock is acquired, so the base (e.g. an async lens whose
    * worker needs the engine) is not waited for while holding it (bug #76935).
    *
    * <p>If no row remains to compute, i.e. row {@code r} is computed or the base has no row
    * past the computed rows, only this lens's lock is acquired. Such a read, e.g. the
    * end-of-table probe that ends every scan, must not wait for the engine lock: a join
    * worker probing a computed lens would wait for a thread that holds the engine lock and
    * waits for the joined rows (bug #77215).
    *
    * @param r the row to compute.
    * @param lastRow the last row computed in this pass.
    *
    * @return the acquired engine lock, which must be released after this lens's lock,
    *         or {@code null} if none was acquired, whether no row remains to compute, and the
    *         row table read under this lens's lock that both were decided for. The batch
    *         computes that row table, not the one invalidate() may publish after it was read,
    *         whose rows it would compute without the engine lock (bug #77243).
    */
   private LockedRows lockForRow(int r, int lastRow) {
      ScriptEnv env = getScriptEnv();

      if(env == null || !env.usesExecutionLock()) {
         // a pooled env never makes a script wait for another thread's script, so there is
         // no engine lock to order against (bug #76960). The base is still read before this
         // lens's lock, as below: a batch waiting for a base row under the lock deadlocks
         // against a thread that holds the base's monitor and reads this lens (finding F1)
         if(r >= getProcessedRowCount() + hrows) {
            table.moreRows(lastRow);
         }

         lockBounded();
         return new LockedRows(null, false, rows);
      }

      Lock execLock = null;

      while(true) {
         // the base is read before this lens's lock is acquired, see above. the formulas are
         // compiled under the engine lock even for an empty base, which reports their errors.
         // the scriptable of an older row table does not count: invalidate() does not clear
         // it, and the row table it published has no row computed (bug #77243)
         XSwappableTable checked = rows;
         int nrows = getProcessedRowCount(checked);
         boolean computed = isRowScriptable(checked) &&
            (r < nrows + hrows || !table.moreRows(nrows + hrows));

         if(execLock == null && !computed) {
            table.moreRows(lastRow);
            execLock = getScriptExecutionLock();

            if(execLock != null) {
               execLock.lock();
               JavaScriptEngine.pushHeldScriptLock(execLock);
            }
         }

         try {
            lockBounded();
         }
         catch(RuntimeException ex) {
            // a stalled lens-lock wait must not leave the engine locked (bug #76967)
            if(execLock != null) {
               JavaScriptEngine.popHeldScriptLock();
               execLock.unlock();
            }

            throw ex;
         }

         // the row table read here is the one the batch computes: one published after this
         // read has none of its rows computed, and the decisions below are for this one
         XSwappableTable locked = rows;

         if(execLock != null || locked == null) {
            return new LockedRows(execLock, false, locked);
         }

         // rows may have been computed, or reset by invalidate(), after the check above,
         // don't compute them without the engine lock. the base was read at nrows above
         int nrows2 = getProcessedRowCount(locked);

         if(computed && locked == checked && isRowScriptable(locked) &&
            (r < nrows2 + hrows || nrows2 == nrows))
         {
            return new LockedRows(null, true, locked);
         }

         if(!computed && (r < nrows2 || getScriptExecutionLock() == null)) {
            return new LockedRows(null, false, locked);
         }

         lock.unlock();
      }
   }

   /**
    * The locks lockForRow() acquired and the row table it chose them for.
    *
    * @param execLock the engine lock, or {@code null} if none was acquired.
    * @param computed {@code true} if no row of {@code rows} remains to compute, so only this
    *                 lens's lock was acquired (bug #77215).
    * @param rows the row table read under this lens's lock, {@code null} if disposed.
    */
   private record LockedRows(Lock execLock, boolean computed, XSwappableTable rows) {
   }

   /**
    * Check if the row scriptable, and the scripts compiled with it, are for the row table.
    * invalidate() does not clear them, so after it they are for the row table it replaced.
    */
   private boolean isRowScriptable(XSwappableTable rows) {
      TableRow2 tableRow = this.tableRow;
      return rows != null && tableRow != null && tableRow.batchRows == rows;
   }

   /**
    * Get the execution lock of the script engine the formulas run on, creating the
    * engine if needed, since compiling the formulas creates it anyway.
    */
   private Lock getScriptExecutionLock() {
      ScriptEnv env = getScriptEnv();

      if(env == null) {
         return null;
      }

      Lock execLock = env.getExecutionLock();

      if(execLock == null) {
         env.init();
         execLock = env.getExecutionLock();
      }

      return execLock;
   }

   /**
    * Get the engine lock that computing this lens's rows takes while a row remains to
    * compute, see lockForRow() (bug #77223). A computed lens is read without the engine lock
    * (bug #77215), so a lens over it keeps a worker that never needs the lock. The check
    * neither blocks nor reads the base: rows that are computed but not yet known to be the
    * last count as remaining. An env without an engine has no script running on it, so no
    * engine is created for the check.
    */
   @Override
   public Lock getScriptLock() {
      XSwappableTable rows = this.rows;

      // the row table is completed only when the base has no row past the computed rows
      // the scriptable of an older row table does not count, as in lockForRow() (bug #77243)
      if(isRowScriptable(rows) && rows.getRowCount() >= 0) {
         return null;
      }

      ScriptEnv env = getScriptEnv();
      return env == null || !env.usesExecutionLock() ? null : env.getExecutionLock();
   }

   /**
    * Get the script env the formulas run on, taking it from the report on first use.
    */
   private ScriptEnv getScriptEnv() {
      if(senv == null && report != null) {
         senv = report.getScriptEnv();
      }

      return senv;
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

      int row = r - hrows;

      // the row table is read once for both the count check and the read, and read again
      // after moreRows(): invalidate() may publish a new one at any time, which does not hold
      // the row yet (bug #77243)
      for(int retry = 0; ; retry++) {
         XSwappableTable rows = this.rows;

         // disposed
         if(rows == null) {
            return null;
         }

         // @by jasons, moreRows() should be called regardless of the state of rows. If this
         //             is not done, this method will always return null for non-header rows
         //             unless moreRows() is explicitly called before this method is called.
         if(row >= getProcessedRowCount(rows)) {
            if(retry < MAX_READ_RETRIES) {
               moreRows(r);

               // the row table was replaced while the row was computed, read the new one
               if(this.rows != rows) {
                  continue;
               }
            }
            else {
               LOG.warn("Formula row {} not computed: the formula table was invalidated {} " +
                        "times while it was computed", r, MAX_READ_RETRIES);
            }
         }

         if(isCancelled()) {
            return null;
         }

         checkStopped(rows, r, c - ncols);

         try {
            return rows.getObject(row + 1, c - ncols);
         }
         catch(Exception ex) {
            // a lost swap file is not a null value (bug #77912)
            SwapFileReadException swap = SwapFileReadException.find(ex);

            if(swap != null) {
               throw swap;
            }

            // row is out of bound
            return null;
         }
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

         XSwappableTable rows = this.rows;

         if(rows != null && c2 < rows.getColCount()) {
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
      XSwappableTable rows = this.rows;

      // a disposed table that was read in part keeps no context alive by its vars' objects;
      // only if no batch runs now (Testing #77123)
      if(tableRow != null && !lock.isHeldByCurrentThread() && lock.tryLock()) {
         try {
            tableRow.thisScope.releaseOwnedObjects();
         }
         finally {
            lock.unlock();
         }
      }

      if(rows != null) {
         // unpublish before disposing, a reader of the disposed table gets null
         this.rows = null;
         rows.dispose();
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
      /**
       * @param rows the row table this scriptable computes rows of, with the header row count
       *             and base column count it was created for (bug #77243).
       */
      public TableRow2(XTable table, int row, int ncols, XSwappableTable rows) {
         super(table, row);
         thisScope = new TableRowScope(this, "field");
         // the pool takes it without waiting to hand off the vars' objects (Testing #77123)
         thisScope.setLensLock(lock);
         // the formulas' top-level vars live for this row table (Testing #77123)
         thisScope.setOwnedVars(
            GraalJavaScriptEngine.collectOwnedVarNames(Arrays.asList(formulas)));
         this.batchHrows = row;
         this.batchNcols = ncols;
         this.batchRows = rows;
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
         if(col < batchNcols || table != FormulaTableLens.this) {
            return super.get(table, getMethod, row, col);
         }

         if(row < getRow()) {
            if(row < batchHrows) {
               return FormulaTableLens.this.getObject(row, col);
            }

            // an earlier row of this batch's own row table, where it is computed: after an
            // invalidate() the lens's current row table does not hold it, and reading that
            // would compute rows into it from inside this batch (bug #77243)
            if(isCancelled()) {
               return null;
            }

            checkStopped(batchRows, row, col - batchNcols);

            try {
               return batchRows.getObject(row - batchHrows + 1, col - batchNcols);
            }
            catch(Exception ex) {
               // a lost swap file is not a null value (bug #77912)
               SwapFileReadException swap = SwapFileReadException.find(ex);

               if(swap != null) {
                  throw swap;
               }

               return null;
            }
         }
         else if(row > getRow()) {
            LOG.warn("Formula column can't forward reference other rows.");
            return null;
         }

         // restore, not clear: this read may be inside the exec of the same cell, whose
         // assignment to its own column must still set the result (setObject)
         Point prev = currExec;

         try {
            currExec = new Point(col, row);
            return getResult(col - batchNcols);
         }
         finally {
            currExec = prev;
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
            ScriptScope scope0 = (scope != null) ? thisScope : iterator;

            // for Feature #26586, add javascript execution time record for current report.
            row[col] = FormulaTableLens.exec(scripts[col], senv, scope0,
                                             formulas[col], runtime, "XXX");
         }
         catch(Exception ex) {
            // a lock stall is not a script error, the reader must get it (bug #76967)
            LockStallException stall = LockStallException.find(ex);

            if(stall != null) {
               throw stall;
            }

            // nor is a lost swap file, which the failure below would drop (bug #77912)
            SwapFileReadException swap = SwapFileReadException.find(ex);

            if(swap != null) {
               throw swap;
            }

            ScriptException failure = new ScriptException(ex.getMessage());
            // keep a timeout or cancel recognizable, so the batch ends at it (Testing #77123)
            failure.setStopped(ScriptTimeoutGuard.isStop(ex));
            throw failure;
         }

         return row[col];
      }

      private final XSwappableTable batchRows;
      private final int batchHrows;
      private final int batchNcols;
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

   private final class TableIteratorScriptable implements DynamicScope {
      public TableIteratorScriptable() {
      }

      public void setRow(Integer row) {
         this.row = row;
      }

      @Override
      public Object[] getMemberKeys() {
         return new String[] { "field", "row" };
      }

      @Override
      public Object getMember(String name) {
         if("field".equals(name)) {
            return tableRow;
         }
         else if("row".equals(name)) {
            return row;
         }

         return parent != null ? parent.getMember(name) : null;
      }

      @Override
      public boolean hasMember(String name) {
         if("field".equals(name) || "row".equals(name)) {
            return true;
         }

         return parent != null && parent.hasMember(name);
      }

      @Override
      public void putMember(String name, Object value) {
         if(parent != null) {
            parent.putMember(name, value);
         }
      }

      public void setParentScope(ScriptScope parent) {
         this.parent = parent;
      }

      @Override
      public ScriptScope getParentScope() {
         return parent;
      }

      private Integer row = null;
      private ScriptScope parent = null;
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
         // a lock stall is neither a script error nor the default value (bug #76967)
         LockStallException stall = LockStallException.find(ex);

         if(stall != null) {
            throw stall;
         }

         // nor is a lost swap file (bug #77912)
         SwapFileReadException swap = SwapFileReadException.find(ex);

         if(swap != null) {
            throw swap;
         }

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

   /**
    * The rows of the next pooled batch (bug #76960, spec §14.14), under {@link #lock}. A first
    * batch, or one that does not start at the first row not yet computed, is what pool off
    * computes (0 here, so the caller's look-ahead applies), so a bounded or random read runs
    * scripts for no more rows than pool off (context-pool regression D1). While each batch
    * starts at the first row not yet computed (a sequential read), batches double from there,
    * up to maxBatchRows (never below batchRows, see PoolConfig), so a row-by-row reader of N
    * rows computes at most about 2N + 10. A batch computes its read-ahead plus the row that
    * asked for it, as pool off does, so a capped batch is maxBatchRows + 1 rows. 0 off the
    * pool, where batchRows is 0. {@link #peekPoolBatch} predicts this before the lens lock;
    * keep the two in step, although a mismatch only shortens a batch.
    *
    * @param next the first row not yet computed.
    */
   private int nextPoolBatch(ScriptSpan span, int r, int next) {
      if(span.batchRows() <= 0) {
         return 0;
      }

      // next == hrows: no row computed, as after invalidate(), so a reset lens starts over like
      // a fresh one (review r2 minor A)
      if(poolBatch > 0 && r <= next && next > hrows) {
         int max = Math.max(span.batchRows(), span.maxBatchRows());
         poolBatch = poolBatch >= max / 2 ? max : poolBatch * 2;
         return poolBatch;
      }

      // the pool-off look-ahead of moreRows()
      poolBatch = Math.min(Math.max(r / 100, 10), 100);
      return 0;
   }

   /**
    * The look-ahead {@link #nextPoolBatch} would take for row {@code r}, without updating it,
    * so that moreRows() loads the base rows of a pooled batch before it takes the lens lock
    * (finding F1). It reads {@link #poolBatch} without the lock, so it may differ from the
    * batch's own; the batch stops at the rows loaded here and the next read continues it.
    * Keep it in step with nextPoolBatch(); a mismatch only shortens a batch.
    */
   private int peekPoolBatch(int r) {
      if(!(getScriptEnv() instanceof WorksheetScriptEnv wenv) ||
         wenv.getConfig().batchRows() <= 0)
      {
         return 0;
      }

      int next = getProcessedRowCount() + hrows;
      int prev = poolBatch;

      if(prev <= 0 || r > next || next <= hrows) {
         return 0;
      }

      int max = Math.max(wenv.getConfig().batchRows(), wenv.getConfig().maxBatchRows());
      return prev >= max / 2 ? max : prev * 2;
   }

   /**
    * Take the lens lock. If another thread holds it, wait in slices registered with the
    * lock-stall watchdog (bug #76967). The wait stays alive while this lens computes rows or
    * its owner is running, e.g. a long script batch in pool mode. If there is no progress for
    * stall.watchdog.noProgressMillis, e.g. because the owner is blocked on a monitor this
    * thread holds, it throws a LockStallException holding nothing of the lock, as a plain
    * lock() would have hung.
    */
   private void lockBounded() {
      if(lock.tryLock()) {
         return;
      }

      boolean interrupted = false;

      try(WaitRecord record = WaitRegistry.begin("FormulaTableLens.moreRows",
                                                 this::getProcessedRowCount, this::getLockOwner))
      {
         while(true) {
            try {
               if(lock.tryLock(record.waitMillis(10000), TimeUnit.MILLISECONDS)) {
                  return;
               }
            }
            catch(InterruptedException ex) {
               // lock() ignored interrupts too; the flag is restored below
               interrupted = true;
            }

            record.checkStall();
         }
      }
      finally {
         if(interrupted) {
            Thread.currentThread().interrupt();
         }
      }
   }

   /**
    * The thread holding the lens lock, for the watchdog's blocker credit.
    */
   private Thread[] getLockOwner() {
      Thread owner = lock.getOwnerThread();
      return owner == null ? NO_THREADS : new Thread[] { owner };
   }

   // Get the number of rows already processed
   private int getProcessedRowCount() {
      return getProcessedRowCount(rows);
   }

   // Get the number of rows already processed in the row table
   private static int getProcessedRowCount(XSwappableTable rows) {
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
    * Fail the read of a cell of a row that a script timeout or cancel stopped, from the
    * stopped column on, with the stop: the cell has no value (bug #77949).
    *
    * @param rows the row table the cell is read from.
    * @param r    the lens row.
    * @param col  the formula column, from 0.
    */
   private void checkStopped(XSwappableTable rows, int r, int col) {
      StoppedRow stopped = stoppedRows.isEmpty() ? null : stoppedRows.get(r);

      if(stopped != null && stopped.rows().get() == rows && col >= stopped.col()) {
         // a new exception for each read: a reader may add to the one it gets
         ExpressionFailedException failure = stopped.failure();
         ExpressionFailedException stop = new ExpressionFailedException(
            failure.getColIndex(), failure.getColName(), failure.getTableName(),
            failure.getOriginalException());

         for(int failedRow : failure.getFailedRows()) {
            stop.addFailedRow(failedRow);
         }

         stop.setStopped(true);
         throw stop;
      }
   }

   /**
    * Check if a script timeout or cancel stopped a row of this table as it is computed now:
    * the cells of that row fail every read, so a cache must not hand this table to another
    * reader, which would get the same failure, but compute the table again (bug #77949).
    */
   public boolean isStopped() {
      XSwappableTable rows = this.rows;

      for(StoppedRow stopped : stoppedRows.values()) {
         if(rows != null && stopped.rows().get() == rows) {
            return true;
         }
      }

      return false;
   }

   /**
    * A row of {@code rows} whose formula of column {@code col} was stopped. The row table is
    * held weakly: a record of a replaced row table must not keep it.
    */
   private record StoppedRow(WeakReference<XSwappableTable> rows, int col,
                             ExpressionFailedException failure)
   {
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

   /**
    * The lens lock, exposing its owner to the lock-stall watchdog (bug #76967).
    */
   private static final class OwnedLock extends ReentrantLock {
      Thread getOwnerThread() {
         return getOwner();
      }
   }

   private static final Thread[] NO_THREADS = new Thread[0];
   // the reads of a row that invalidate() keeps replacing the row table under, after which
   // the current row table is read as it is (bug #77243)
   private static final int MAX_READ_RETRIES = 100;

   /**
    * A table with a stopped row is not written: its stopped cells would be read back as null.
    * A cache that writes it fails to, and the reader of the other copy computes it again
    * (bug #77949).
    */
   @Serial
   private void writeObject(ObjectOutputStream out) throws IOException {
      if(isStopped()) {
         throw new NotSerializableException(
            "A formula table whose row was stopped by a script timeout is not written");
      }

      out.defaultWriteObject();
   }

   @Serial
   private void readObject(ObjectInputStream in) throws ClassNotFoundException, IOException {
      in.defaultReadObject();
      cancelLock = new ReentrantLock();
      lock = new OwnedLock();
      senv = new GraalJavaScriptEnv();
      stoppedRows = new ConcurrentHashMap<>();
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
   // set while a batch of this table that took a context of its own is open, under the lock
   private transient boolean ownBatch;
   // that batch's span, while ownBatch is set
   private transient ScriptSpan ownSpan;
   // volatile: getObject reads it without the lock, and invalidate() publishes it without
   // the lock (bug #77243)
   private volatile XSwappableTable rows;
   private int hrows, ncols;
   private transient TableDataDescriptor descriptor;
   private Point currExec;
   private List<FormulaHeaderInfo> hinfos;
   private volatile boolean completed;       // completed flag
   private volatile boolean cancelled;       // cancelled flag
   // the rows a script timeout or cancel stopped, by lens row (bug #77949)
   private transient Map<Integer, StoppedRow> stoppedRows = new ConcurrentHashMap<>();
   private transient Lock cancelLock = new ReentrantLock();

   private transient Object[] scripts; // compiled javascripts
   private transient TableRow2 tableRow; // row javascript object
   private transient boolean runtime = true;
   private transient TableChangeListener listener = null;
   private transient TableIteratorScriptable iterator = null;
   private transient OwnedLock lock = new OwnedLock();
   // the look-ahead of the last pooled batch, 0 before the first; guarded by lock (bug #76960)
   private transient int poolBatch;
   private transient boolean forceType = Drivers.getInstance().isDataCached();
   private transient String reportName;

   private static final ScriptCache scriptCache = new ScriptCache(100, 60000);
   // publishes a row table only over the one a batch computed (bug #78133)
   private static final AtomicReferenceFieldUpdater<FormulaTableLens, XSwappableTable> ROWS =
      AtomicReferenceFieldUpdater.newUpdater(FormulaTableLens.class, XSwappableTable.class, "rows");
   private static final Logger LOG = LoggerFactory.getLogger(FormulaTableLens.class);
}
