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

import inetsoft.report.*;
import inetsoft.report.filter.*;
import inetsoft.report.internal.table.CancellableTableLens;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.util.TableLoadException;
import inetsoft.util.ThreadPool;
import inetsoft.util.Tool;
import inetsoft.util.UserMessage;
import inetsoft.util.audit.ExecutionBreakDownRecord;
import inetsoft.util.profile.ProfileUtils;
import inetsoft.util.script.JavaScriptEngine;
import inetsoft.util.script.LendableReentrantLock;
import inetsoft.util.stall.LockStallException;
import inetsoft.util.stall.WaitRecord;
import inetsoft.util.stall.WaitRegistry;
import inetsoft.util.swap.SwapFileReadException;
import inetsoft.util.swap.XSwappableIntList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Distinct table lens filters out duplicate table rows. This class sorts the
 * table and extract the distinct rows.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class DistinctTableLens extends AbstractTableLens
   implements TableFilter, CancellableTableLens, SortedTable
{
   /**
    * Constructor.
    */
   public DistinctTableLens() {
      super();
   }

   /**
    * Constructor.
    */
   public DistinctTableLens(TableLens table) {
      this(table, null);
   }

   /**
    * Constructor.
    */
   public DistinctTableLens(TableLens table, int[] cols) {
      this(table, cols, false);
   }

   /**
    * Constructor.
    */
   public DistinctTableLens(TableLens table, int[] cols, int[] dimTypes,
                            Comparator[] comps) {
      this(table, cols, false, dimTypes, comps);
   }

   /**
    * Constructor.
    */
   public DistinctTableLens(TableLens table, int[] cols, boolean stable) {
      this(table, cols, stable, null, null);
   }

   /**
    * Constructor.
    */
   public DistinctTableLens(TableLens table, int[] cols, boolean stable,
                            int[] dimTypes, Comparator[] comps) {
      this();

      this.stable = stable;
      this.dimTypes = dimTypes;
      this.comps = comps;

      if(cols == null) {
         cols = new int[table.getColCount()];

         for(int i = 0; i < cols.length; i++) {
            cols[i] = i;
         }
      }

      setTable(table);
      setCols(cols);
   }

   /**
    * Get the original table.
    * @return the original table of this filter.
    */
   @Override
   public TableLens getTable() {
      return table;
   }

   /**
    * Set the base table lenses.
    * @param table the specified base table lens, <tt>null</tt> is not
    * allowed.
    */
   @Override
   public void setTable(TableLens table) {
      this.table = table;
      this.table.addChangeListener(new DefaultTableChangeListener(this));
      invalidate();
   }

   /**
    * Get the distinct columns.
    * @return the distinct columns of this filter.
    */
   public int[] getCols() {
      return cols;
   }

   /**
    * Set the distinct columns.
    * @param cols the specified distinct columns.
    */
   public void setCols(int[] cols) {
      this.cols = cols;
      invalidate();
   }

   /**
    * Get the columns that the table is sorted on.
    * @return sort columns.
    */
   @Override
   public int[] getSortCols() {
      return getCols();
   }

   /**
    * Get the sorting order of the sorting columns.
    */
   @Override
   public boolean[] getOrders() {
      boolean[] arr = new boolean[cols.length];
      Arrays.fill(arr, true);
      return arr;
   }

   /**
    * Set the comparer for a sorting column.
    * @param col table column index.
    * @param comp comparer.
    */
   @Override
   public void setComparer(int col, Comparer comp) {
      throw new RuntimeException("Operation not supported!");
   }

   /**
    * Get the comparer for a sorting column.
    * @param col the specified table column index.
    */
   @Override
   public Comparer getComparer(int col) {
      return null;
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
    * Invalidate the table filter forcely, and the table filter will
    * perform filtering calculation to validate itself.
    */
   @Override
   public void invalidate() {
      synchronized(this) {
         // publish new rows with the header rows in them, and don't dispose the old ones: a
         // reader or the worker of the old pass may still hold them, the finalizer frees them
         // (bug #77333)
         XSwappableIntList rows = new XSwappableIntList();

         if(table != null) {
            for(int i = 0; i < table.getHeaderRowCount() && table.moreRows(i); i++)
            {
               rows.add(i);
            }
         }

         this.rows = rows;

         if(table != null) {
            // notify waiting consumers
            notifyAll();
         }

         completed = false;
         validated = false;
         stallFailure = null;
         baseFailure = null;
         userMsg = null;
         scannedRows = 0;
      }

      // fire after releasing the monitor: a downstream lens's invalidate() takes its own
      // monitor, which a reader of that lens may hold while it waits for this monitor to read
      // the next row (bug #77432)
      fireChangeEvent();
   }

   /**
    * Validate the distinct table lens.
    */
   private void validate() throws Exception {
      if(validated) {
         return;
      }

      validated = true;
      // the rows of this pass, a pass stops once invalidate() replaces them (bug #77333)
      XSwappableIntList target = rows;

      // if this is called from JavaScriptEngine.exec() or a condition filter
      // (bug #76938), the script engine is already locked. running process() in a
      // separate thread would create a deadlock waiting forever for the
      // JavaScriptEngine lock to be released. if the base needs an engine lock, only
      // holding that lock counts: a reader takes it before this lens's monitor (see
      // moreRows), so the worker is not started by a reader that can't lend it the lock
      // later, e.g. inside a script on that engine (bug #77223)
      LendableReentrantLock chainLock = ChainScriptLock.find(table);
      boolean inExec = chainLock != null ? chainLock.isHeldByCurrentThread()
         : JavaScriptEngine.holdsScriptLock();

      if(inExec) {
         validate0(target, false);
      }
      // concurrent process
      else {
         // a thread holding the lock may still wait for this worker later on, it lends
         // the lock to the worker then (see moreRows)
         LendableReentrantLock.Borrower borrower = new LendableReentrantLock.Borrower();
         worker = borrower;

         Runnable runnable = new ThreadPool.AbstractContextRunnable() {
            @Override
            public void run() {
               borrower.begin();

               try {
                  validate0(target, true);
               }
               finally {
                  borrower.end();
               }
            }
         };
         ThreadPool.addOnDemand(runnable);
      }
   }

   /**
    * @param background {@code true} if on the worker, which passes its user messages, e.g. the
    *                   warning of a base that failed to load, to the readers (bug #77966).
    */
   private void validate0(XSwappableIntList target, boolean background) {
      if(cols.length == 1 || stable) {
         hashDistinct(target, background);
      }
      else {
         sortDistinct(target, background);
      }
   }

   /**
    * Get max row count.
    */
   private int getMaxRowCount() {
      String prop = SreeEnv.getProperty("DistinctTableLens.maxrow");
      return prop != null ? Integer.parseInt(prop) : Integer.MAX_VALUE;
   }

   /**
    * Find distinct rows through a hash map.
    * @param target the rows of this pass.
    */
   private void hashDistinct(XSwappableIntList target, boolean background) {
      LockStallException stall = null;
      RuntimeException baseFailure = null;

      try {
         Set map = new ObjectOpenHashSet();

         for(int r = table.getHeaderRowCount(), max = getMaxRowCount();
             table.moreRows(r) && r < max; r++) {
            scannedRows = r;
            Object val = getKey(table, r);

            if(map.contains(val)) {
               continue;
            }

            // invalidated (bug #77333), disposed or cancelled
            if(target == null || rows != target || target.isDisposed() || cancelled) {
               break;
            }

            map.add(val);
            target.add(r);

            if(target.size() % 20 == 0) {
               synchronized(DistinctTableLens.this) {
                  // notify waiting consumers
                  DistinctTableLens.this.notifyAll();
               }
            }
         }
      }
      catch(LockStallException ex) {
         // the readers rethrow it rather than take the rows so far for the whole table
         // (bug #76967)
         stall = ex;
         throw ex;
      }
      catch(RuntimeException ex) {
         // a stall may reach the worker wrapped by the base table (bug #76967)
         stall = LockStallException.find(ex);

         if(stall == null) {
            // a swap file read failure must not look like a complete (silently
            // empty/partial) distinct table either (bug #77651), nor a base that failed to
            // load for a reader that has to fail, e.g. a scheduled run (bug #77966)
            baseFailure = findBaseFailure(ex);
         }

         throw ex;
      }
      finally {
         complete(target, stall, baseFailure, background);
      }
   }

   /**
    * End a pass. A pass that invalidate() replaced completes nothing and fails nothing, the
    * next pass finds the rows of the table (bug #77333).
    * @param target the rows of the pass.
    * @param stall the stall the pass failed with, if any.
    * @param baseFailure the lost swap file or the load failure of the base the pass failed
    *                    with, if any.
    * @param background {@code true} if on the worker: its user messages, e.g. the warning of
    *                   a base that failed to load, are kept for the readers before they are
    *                   woken, they are lost with the worker thread otherwise (bug #77966).
    */
   private synchronized void complete(XSwappableIntList target, LockStallException stall,
                                       RuntimeException baseFailure, boolean background)
   {
      UserMessage msg = null;

      if(background) {
         try {
            msg = Tool.getUserMessage();
         }
         catch(RuntimeException ex) {
            LOG.warn("Failed to collect the distinct table user messages", ex);
            Tool.clearUserMessage();
         }
      }

      // a disposed table still ends the waits of its readers
      if(rows != target && rows != null) {
         return;
      }

      if(stall != null) {
         stallFailure = stall;
      }

      if(baseFailure != null) {
         this.baseFailure = baseFailure;
      }

      userMsg = msg;

      completed = true;

      if(target != null) {
         target.complete();
      }

      // notify waiting consumers
      notifyAll();
   }

   /**
    * Get the hash key.
    */
   private Object getKey(TableLens tbl, int row) {
      return new Tuple(tbl, row);
   }

   /**
    * Find distinct rows through sorting.
    */
   private void sortDistinct(XSwappableIntList target, boolean background) {
      try {
         // for Feature #26586, add ui processing time record.

         ProfileUtils.addExecutionBreakDownRecord(getReportName(),
            ExecutionBreakDownRecord.POST_PROCESSING_CYCLE, args -> {
               sortDistinct0(target, background);
            });

         //sortDistinct0();
      }
      catch(LockStallException ex) {
         // logged by the wait site, sortDistinct0() kept it for the readers (bug #76967)
      }
      catch(SwapFileReadException ex) {
         // the fragment already logged the read failure, sortDistinct0() kept it for the
         // readers instead of letting the distinct table look silently complete and
         // empty/partial (bug #77651)
      }
      catch(Exception ex) {
         // a load failure of the base was logged where it happened, sortDistinct0() kept it
         // for the readers (bug #77966)
         if(TableLoadException.find(ex) == null) {
            LOG.error("Failed to process sort distinct", ex);
         }
      }
   }

   /**
    * Find distinct rows through sorting.
    * @param target the rows of this pass.
    */
   private void sortDistinct0(XSwappableIntList target, boolean background) {
      LockStallException stall = null;
      RuntimeException baseFailure = null;

      try {
         TableFilter sorted = createSortedTable();
         Object[] row = null;
         boolean distinct = sorted instanceof SortFilter && ((SortFilter) sorted).isDistinct();

         for(int r = sorted.getHeaderRowCount(); sorted.moreRows(r); r++) {
            scannedRows = r;
            boolean eq = !distinct;

            if(row == null) {
               eq = false;
               row = new Object[cols.length];
            }

            if(!distinct) {
               for(int i = 0; i < row.length; i++) {
                  Object val = sorted.getObject(r, cols[i]);

                  if(eq) {
                     eq = Tool.equals(val, row[i]);
                  }

                  row[i] = val;
               }
            }

            if(eq) {
               continue;
            }

            // invalidated (bug #77333), disposed or cancelled
            if(target == null || rows != target || target.isDisposed() || cancelled) {
               break;
            }

            int baseIdx = (sorted == table) ? r : sorted.getBaseRowIndex(r);
            target.add(baseIdx);

            if(target.size() % 20 == 0) {
               synchronized(DistinctTableLens.this) {
                  // notify waiting consumers
                  DistinctTableLens.this.notifyAll();
               }
            }
         }
      }
      catch(LockStallException ex) {
         // the readers rethrow it rather than take the rows so far for the whole table
         // (bug #76967)
         stall = ex;
         throw ex;
      }
      catch(RuntimeException ex) {
         // a stall may reach the worker wrapped by the base table (bug #76967)
         stall = LockStallException.find(ex);

         if(stall == null) {
            // a swap file read failure must not look like a complete (silently
            // empty/partial) distinct table either (bug #77651), nor a base that failed to
            // load for a reader that has to fail, e.g. a scheduled run (bug #77966)
            baseFailure = findBaseFailure(ex);
         }

         throw ex;
      }
      finally {
         complete(target, stall, baseFailure, background);
      }
   }

   /**
    * Create a table sorted on the distinct columns.
    */
   private TableFilter createSortedTable() {
      // reuse the base table if it's already sorted
      if(table instanceof SortedTable) {
         int[] ocols = ((SortedTable) table).getSortCols();
         boolean compatible = true;
         int lastidx = -1;

         // check if the columns are sorted in the same order
         for(int i = 0; i < cols.length; i++) {
            int idx = -1;

            for(int j = 0; ocols != null && j < ocols.length; j++) {
               if(ocols[j] == cols[i]) {
                  idx = j;
                  break;
               }
            }

            if(idx <= lastidx) {
               compatible = false;
               break;
            }

            lastidx = idx;
         }

         if(compatible) {
            return (TableFilter) table;
         }
      }

      SortFilter sfilter = new SortFilter(table, cols);
      sfilter.setDistinct(true);

      if(dimTypes != null) {
         for(int i = 0; i < dimTypes.length; i++) {
            sfilter.setComparer(cols[i], new DimensionComparer(
                                   dimTypes[i], comps == null ? null : comps[i]));
         }
      }

      return sfilter;
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
      // the first read of rows takes the engine lock the base needs before this lens's
      // monitor, like a condition filter (bug #76918), and finds the distinct rows on this
      // thread. a worker needing the lock could wait forever for a reader inside a script
      // on that engine, which can't lend it the lock (bug #77223)
      LendableReentrantLock execLock = validated || row < getHeaderRowCount()
         ? null : getUnheldChainScriptLock();

      if(execLock == null) {
         return moreRows0(row);
      }

      execLock.lock();
      JavaScriptEngine.pushHeldScriptLock(execLock);

      try {
         return moreRows0(row);
      }
      finally {
         JavaScriptEngine.popHeldScriptLock();
         execLock.unlock();
      }
   }

   /**
    * Get the engine lock reading the base may take if the current thread does not hold it.
    */
   private LendableReentrantLock getUnheldChainScriptLock() {
      LendableReentrantLock execLock = ChainScriptLock.find(table);
      return execLock != null && !execLock.isHeldByCurrentThread() ? execLock : null;
   }

   private boolean moreRows0(int row) {
      WaitRecord record = null;

      try {
         while(true) {
            // no loan and no monitor is held here, so a stall exception leaks neither
            // (bug #76967)
            if(record != null) {
               record.checkStall();
            }

            LendableReentrantLock.Borrower lendTo;

            synchronized(this) {
               if(rows != null && row < rows.size()) {
                  return true;
               }

               if(completed) {
                  throwStallFailure();
                  addUserMessage();
                  return false;
               }

               validate();

               // validated on this thread (see validate)
               if(rows != null && row < rows.size() || completed) {
                  continue;
               }

               lendTo = worker;

               if(record != null && !JavaScriptEngine.canLendScriptLocks(lendTo)) {
                  try {
                     wait(record.waitMillis(JavaScriptEngine.getScriptLockWaitMillis(500)));
                  }
                  catch(InterruptedException ex) {
                     // ignore it
                  }

                  continue;
               }
            }

            // the row is not there yet, register the wait (outside of the monitor) and check
            // again
            if(record == null) {
               record = WaitRegistry.begin("DistinctTableLens.moreRows", () -> scannedRows,
                                           this::getWorkerThreads);
               continue;
            }

            // this thread holds or was lent a script engine lock (e.g. by a condition filter)
            // that the worker may need to read the base table, lend it to the worker while
            // waiting (bug #76938). the loan is closed outside of this lens's monitor,
            // which the worker needs in order to publish rows
            try(LendableReentrantLock.Loan ignored = JavaScriptEngine.lendScriptLocks(lendTo)) {
               synchronized(this) {
                  if((rows == null || row >= rows.size()) && !completed) {
                     try {
                        wait(record.waitMillis(JavaScriptEngine.getScriptLockWaitMillis(500)));
                     }
                     catch(InterruptedException ex) {
                        // ignore it
                     }
                  }
               }
            }
         }
      }
      catch(LockStallException ex) {
         // a stall fails the query, it is never the end of the table (bug #76967)
         throw ex;
      }
      catch(SwapFileReadException ex) {
         // thrown directly by throwStallFailure() above; it must not look like the end of
         // the table either (bug #77651)
         throw ex;
      }
      catch(Exception ex) {
         // a stall may reach this thread wrapped, it is never the end of the table either
         // (bug #76967)
         LockStallException stall = LockStallException.find(ex);

         if(stall != null) {
            throw new LockStallException(stall);
         }

         // nor is a base that failed to load, for a reader that has to fail (bug #77966)
         TableLoadException loadFailure = TableLoadException.find(ex);

         if(loadFailure != null) {
            throw loadFailure;
         }

         synchronized(this) {
            completed = true;
         }

         LOG.error("Failed to validate table rows when checking " +
            "if row is available: " + row, ex);
         return false;
      }
      finally {
         if(record != null) {
            record.close();
         }
      }
   }

   /**
    * The worker thread, for the lock-stall watchdog.
    */
   private Thread[] getWorkerThreads() {
      LendableReentrantLock.Borrower task = worker;
      return new Thread[] { task == null ? null : task.getThread() };
   }

   /**
    * Rethrow the stall, swap file read failure or base load failure the worker failed with,
    * called when the table is complete. A stall must never look like the end of the table
    * (bug #76967), and neither must a swap file read failure (bug #77651) or a base that
    * failed to load (bug #77966).
    */
   private void throwStallFailure() {
      LockStallException failure = stallFailure;

      if(failure != null) {
         throw new LockStallException(failure);
      }

      RuntimeException baseFailure = this.baseFailure;

      if(baseFailure != null) {
         throw baseFailure;
      }
   }

   /**
    * Find the lost swap file (bug #77651) or the load failure of the base (bug #77966) in the
    * cause chain of a failure of the worker.
    */
   private static RuntimeException findBaseFailure(Throwable ex) {
      RuntimeException baseFailure = SwapFileReadException.find(ex);
      return baseFailure != null ? baseFailure : TableLoadException.find(ex);
   }

   /**
    * Add the user messages of the worker, e.g. the warning of a base that failed to load, on
    * the reader's thread once the table is complete (bug #77966).
    */
   private void addUserMessage() {
      UserMessage msg = userMsg;

      if(msg != null) {
         Tool.addUserMessage(msg);
      }
   }

   /**
    * Return the number of rows in the table. The number of rows includes
    * the header rows. If the table is loading in background and loading
    * is not done, return the negative number of loaded rows minus 1.
    * @return number of rows in table.
    */
   @Override
   public int getRowCount() {
      // found outside of this lens's monitor, as in moreRows
      return getRowCount0(validated ? null : getUnheldChainScriptLock());
   }

   private synchronized int getRowCount0(LendableReentrantLock execLock) {
      // a row count probe (e.g. AssetQuery.validateDataTypes) starts nothing if the base
      // needs an engine lock this thread doesn't hold: it must not take the lock, it may be
      // building a table inside that table's monitor, and a worker must not be left for a
      // reader that can't lend it the lock. the first read of rows finds them (bug #77223)
      if(execLock != null && !validated) {
         return -rows.size() - 1;
      }

      try {
         validate();

         if(completed) {
            // the rows so far of a stalled worker are not the whole table (bug #76967)
            throwStallFailure();
            addUserMessage();
         }

         return completed ? rows.size() : - rows.size() - 1;
      }
      catch(LockStallException ex) {
         throw ex;
      }
      catch(SwapFileReadException ex) {
         // thrown directly by throwStallFailure() above; it must not look like the end of
         // the table either (bug #77651)
         throw ex;
      }
      catch(Exception ex) {
         // a stall may reach this thread wrapped, it is never the end of the table either
         // (bug #76967)
         LockStallException stall = LockStallException.find(ex);

         if(stall != null) {
            throw new LockStallException(stall);
         }

         // nor is a base that failed to load, for a reader that has to fail (bug #77966)
         TableLoadException loadFailure = TableLoadException.find(ex);

         if(loadFailure != null) {
            throw loadFailure;
         }

         completed = true;
         LOG.error("Failed to validate table rows when getting row count", ex);
         return -1;
      }
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
    * Return the number of rows on the top of the table to be treated
    * as header rows.
    * @return number of header rows.  Default is 1.
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
    * Return the number of rows on the bottom of the table to be treated
    * as trailer rows.
    * @return number of header rows.
    */
   @Override
   public int getTrailerRowCount() {
      return 0;
   }

   /**
    * Return the number of columns on the right of the table to be
    * treated as trailer columns.
    */
   @Override
   public int getTrailerColCount() {
      return 0;
   }

   /**
    * Check if is primitive.
    * @return <tt>true</tt> if is primitive, <tt>false</tt> otherwise.
    */
   @Override
   public final boolean isPrimitive(int col) {
      return table.isPrimitive(col);
   }

   /**
    * Check if the value at one cell is null.
    * @param r the specified row index.
    * @param c column number.
    * @return <tt>true</tt> if null, <tt>false</tt> otherwise.
    */
   @Override
   public final boolean isNull(int r, int c) {
      r = getBaseRowIndex(r);
      return table.isNull(r, c);
   }

   /**
    * Return the value at the specified cell.
    * @param r row number.
    * @param c column number.
    * @return the value at the location.
    */
   @Override
   public Object getObject(int r, int c) {
      r = getBaseRowIndex(r);
      return table.getObject(r, c);
   }

   /**
    * Get the double value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the double value in the specified row.
    */
   @Override
   public final double getDouble(int r, int c) {
      r = getBaseRowIndex(r);
      return table.getDouble(r, c);
   }

   /**
    * Get the float value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the float value in the specified row.
    */
   @Override
   public final float getFloat(int r, int c) {
      r = getBaseRowIndex(r);
      return table.getFloat(r, c);
   }

   /**
    * Get the long value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the long value in the specified row.
    */
   @Override
   public final long getLong(int r, int c) {
      r = getBaseRowIndex(r);
      return table.getLong(r, c);
   }

   /**
    * Get the int value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the int value in the specified row.
    */
   @Override
   public final int getInt(int r, int c) {
      r = getBaseRowIndex(r);
      return table.getInt(r, c);
   }

   /**
    * Get the short value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the short value in the specified row.
    */
   @Override
   public final short getShort(int r, int c) {
      r = getBaseRowIndex(r);
      return table.getShort(r, c);
   }

   /**
    * Get the byte value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the byte value in the specified row.
    */
   @Override
   public final byte getByte(int r, int c) {
      r = getBaseRowIndex(r);
      return table.getByte(r, c);
   }

   /**
    * Get the boolean value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the boolean value in the specified row.
    */
   @Override
   public final boolean getBoolean(int r, int c) {
      r = getBaseRowIndex(r);
      return table.getBoolean(r, c);
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
    * Set the cell value. For table filters, the setObject() call should
    * be forwarded to the base table if possible. An implementation should
    * throw a runtime exception if this method is not supported. In that
    * case, data in a table can not be modified in scripts.
    * @param r row number.
    * @param c column number.
    * @param v cell value.
    */
   @Override
   public void setObject(int r, int c, Object v) {
      r = getBaseRowIndex(r);
      table.setObject(r, c, v);
   }

   /**
    * Get the current row heights setting. The meaning of row heights
    * depends on the table layout policy setting. If the row height
    * is to be calculated by the ReportSheet based on the content,
    * return -1.
    * @return row height.
    */
   @Override
   public int getRowHeight(int r) {
      r = getBaseRowIndex(r);
      return table.getRowHeight(r);
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
      r = getBaseRowIndex(r);
      return table.getRowBorderColor(r, c);
   }

   /**
    * Return the color for drawing the column border lines.
    * @param r row number.
    * @param c column number.
    * @return ruling color.
    */
   @Override
   public Color getColBorderColor(int r, int c) {
      r = getBaseRowIndex(r);
      return table.getColBorderColor(r, c);
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
      r = getBaseRowIndex(r);
      return table.getRowBorder(r, c);
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
      r = getBaseRowIndex(r);
      return table.getColBorder(r, c);
   }

   /**
    * Return the cell gap space.
    * @param r row number.
    * @param c column number.
    * @return cell gap space.
    */
   @Override
   public Insets getInsets(int r, int c) {
      r = getBaseRowIndex(r);
      return table.getInsets(r, c);
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
      r = getBaseRowIndex(r);
      return table.getSpan(r, c);
   }

   /**
    * Return the per cell alignment.
    * @param r row number.
    * @param c column number.
    * @return cell alignment.
    */
   @Override
   public int getAlignment(int r, int c) {
      r = getBaseRowIndex(r);
      return table.getAlignment(r, c);
   }

   /**
    * Return the per cell font. Return null to use default font.
    * @param r row number.
    * @param c column number.
    * @return font for the specified cell.
    */
   @Override
   public Font getFont(int r, int c) {
      r = getBaseRowIndex(r);
      return table.getFont(r, c);
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
      r = getBaseRowIndex(r);
      return table.isLineWrap(r, c);
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
      r = getBaseRowIndex(r);
      return table.getForeground(r, c);
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
      r = getBaseRowIndex(r);
      return table.getBackground(r, c);
   }

   /**
    * Get the base table row index corresponding to the filtered table.
    * If the row does not exist in the base table, it returns -1.
    * @param r row index in the filtered table.
    * @return corresponding row index in the base table.
    */
   @Override
   public int getBaseRowIndex(int r) {
      if(r < 0) {
         return r;
      }

      // the rows are read once for both the count check and the read, and read again if
      // moreRows() returned for new rows: invalidate() may publish them at any time, which
      // don't hold the row yet (bug #77333)
      for(int retry = 0; ; retry++) {
         XSwappableIntList rows = this.rows;
         moreRows(r);

         if(this.rows != rows) {
            if(retry < MAX_READ_RETRIES) {
               continue;
            }

            LOG.warn("Distinct row {} read from replaced rows: the table was invalidated {} " +
                     "times while the row was found", r, MAX_READ_RETRIES);
         }

         // disposed or no such row
         if(rows == null || r >= rows.size()) {
            return -1;
         }

         return rows.get(r);
      }
   }

   /**
    * Get the base table column index corresponding to the filtered table.
    * If the column does not exist in the base table, it returns -1.
    * @param c column index in  the filtered table.
    * @return corresponding column index in the bast table.
    */
   @Override
   public int getBaseColIndex(int c) {
      return c;
   }

   /**
    * Finalize the distinct table lens.
    */
   @Override
   protected void finalize() throws Throwable {
      super.finalize();
      disposeRows();
   }

   /**
    * Dispose the distinct table lens.
    */
   @Override
   public synchronized void dispose() {
      disposeRows();
      disposeTable();
   }

   private void disposeRows() {
      if(rows != null) {
         rows.dispose();
         rows = null;
      }
   }

   private void disposeTable() {
      if(table != null) {
         table.dispose();
         table = null;
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
    * Get internal table data descriptor which contains table structural
    * infos.
    * @return table data descriptor.
    */
   @Override
   public TableDataDescriptor getDescriptor() {
      return table.getDescriptor();
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
    * Tuple, the key in hash table.
    */
   private final class Tuple {
      public Tuple(TableLens table, int row) {
         arr = new Object[cols.length];

         for(int i = 0; i < arr.length; i++) {
            arr[i] = table.getObject(row, cols[i]);
            hash += arr[i] == null ? 0 : arr[i].hashCode();
         }
      }

      public int hashCode() {
         return hash;
      }

      public boolean equals(Object obj) {
         if(obj == null) {
            return false;
         }

         if(obj == this) {
            return true;
         }

         Tuple tuple2 = (Tuple) obj;

         if(tuple2.arr.length != arr.length) {
            return false;
         }

         for(int i = 0; i < arr.length; i++) {
            if(!Tool.equals(arr[i], tuple2.arr[i])) {
               return false;
            }
         }

         return true;
      }

      private Object[] arr;
      private int hash;
   }

   // volatile: getBaseRowIndex reads it without the monitor, and a pass stops once
   // invalidate() replaces it (bug #77333)
   private volatile XSwappableIntList rows;  // rows
   private TableLens table;         // base table
   private int[] cols;              // distinct columns
   private int[] dimTypes;          // column dimension type
   private Comparator[] comps;      // column comparator
   private boolean completed;       // completed flag
   private volatile boolean cancelled;       // cancelled flag
   private final Lock cancelLock = new ReentrantLock();
   private boolean stable;          // true to not reorder rows
   // check if validated, read outside of the monitor (bug #77223)
   private volatile boolean validated;
   // the background task finding distinct rows, if any
   private transient volatile LendableReentrantLock.Borrower worker;
   // the base row the worker has reached, and the stall it failed with (bug #76967)
   private transient volatile int scannedRows;
   private transient volatile LockStallException stallFailure;
   // the swap file read failure (bug #77651) or the base load failure (bug #77966) the worker
   // failed with, if any
   private transient volatile RuntimeException baseFailure;
   // the user messages of the worker, for the readers (bug #77966)
   private transient volatile UserMessage userMsg;

   // the reads of a row that invalidate() keeps replacing the rows under, after which the
   // current rows are read as they are (bug #77333)
   private static final int MAX_READ_RETRIES = 100;
   private static final Logger LOG =
      LoggerFactory.getLogger(DistinctTableLens.class);
}
