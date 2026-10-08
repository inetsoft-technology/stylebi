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
import inetsoft.report.event.TableChangeEvent;
import inetsoft.report.event.TableChangeListener;
import inetsoft.report.filter.BinaryTableFilter;
import inetsoft.report.filter.DefaultTableChangeListener;
import inetsoft.report.internal.Util;
import inetsoft.report.internal.table.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.internal.ColumnIndexMap;
import inetsoft.uql.util.XIdentifierContainer;
import inetsoft.util.Catalog;
import inetsoft.util.MessageException;
import inetsoft.util.ThreadPool;
import inetsoft.util.Tool;
import inetsoft.util.script.JavaScriptEngine;
import inetsoft.util.script.LendableReentrantLock;
import inetsoft.util.stall.LockStallException;
import inetsoft.util.stall.WaitRecord;
import inetsoft.util.stall.WaitRegistry;
import inetsoft.util.swap.DataUnavailable;
import inetsoft.util.swap.XSwappableObjectList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.*;
import java.text.Format;
import java.util.List;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Set table lens implements most of the functions to do a set operation on
 * one/two tables. The sub class may vary its function in table supply and
 * visitor implementation.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public abstract class SetTableLens
   implements BinaryTableFilter, CancellableTableLens
{
   /**
    * Constructor.
    */
   public SetTableLens() {
      this.identifiers = new XIdentifierContainer(this);
   }

   /**
    * Constructor.
    */
   public SetTableLens(TableLens ltable, TableLens rtable) {
      this();
      setTables(ltable, rtable);
   }

   /**
    * Add table change listener to the filtered table.
    * If the table filter's data changes, a TableChangeEvent will be triggered
    * for the TableChangeListener to process.
    * @param listener the specified TableChangeListener
    */
   @Override
   public void addChangeListener(TableChangeListener listener) {
      clisteners.add(listener);
   }

   /**
    * Remove table change listener from the filtered table.
    * @param listener the specified TableChangeListener to be removed
    */
   @Override
   public void removeChangeListener(TableChangeListener listener) {
      clisteners.remove(listener);
   }

   /**
    * Fire change event when filtered table changed.
    */
   protected void fireChangeEvent() {
      try {
         for(TableChangeListener listener :
            new ArrayList<>(clisteners))
         {
            // reuse event object for optimization reason
            if(event == null) {
               event = new TableChangeEvent(this);
            }

            listener.tableChanged(event);
         }
      }
      catch(Exception ex) {
         LOG.error("Failed to process change event", ex);
      }
   }

   /**
    * Get the left base table lens.
    * @return the left base table lens.
    */
   @Override
   public TableLens getLeftTable() {
      return getTable(0);
   }

   /**
    * Get the right base table lens.
    * @return the right base table lens.
    */
   @Override
   public TableLens getRightTable() {
      return getTable(1);
   }

   /**
    * Gets the specified base table.
    *
    * @param index the index of the table.
    *
    * @return the specified table or <tt>null</tt> if it is not set.
    */
   public TableLens getTable(int index) {
      return index >= tables.size() ? null : tables.get(index);
   }

   /**
    * Gets the number of base tables.
    * @return the table count.
    */
   public int getTableCount() {
      return tables.size();
   }

   /**
    * Get all base tables.
    */
   public TableLens[] getTables() {
      return tables.toArray(new TableLens[0]);
   }

   /**
    * Check if only left data might be contained in the result set.
    * @return <tt>true</tt> if yes, <tt>false</tt>.
    */
   boolean isLeftOnly() {
      return false;
   }

   /**
    * Get table drill info.
    * @param row the row number.
    * @param col the col number.
    */
   @Override
   public XDrillInfo getXDrillInfo(int row, int col) {
      TableDataDescriptor descriptor = getDescriptor();

      if(!descriptor.containsDrill()) {
         return null;
      }

      TableDataPath path = descriptor.getCellDataPath(row, col);
      XMetaInfo minfo = (XMetaInfo) descriptor.getXMetaInfo(path);

      return minfo == null ? null : minfo.getXDrillInfo();
   }

   /**
    * Return the per cell format.
    *
    * @param row row number.
    * @param col column number.
    * @return format for the specified cell.
    */
   @Override
   public Format getDefaultFormat(int row, int col) {
      TableDataDescriptor descriptor = getDescriptor();

      if(!descriptor.containsFormat()) {
         return null;
      }

      TableDataPath path = descriptor.getCellDataPath(row, col);
      XMetaInfo minfo = descriptor.getXMetaInfo(path);
      Format format = null;

      if(minfo != null) {
         XFormatInfo formatInfo = minfo.getXFormatInfo();
         format = formatInfo == null ? null : TableFormat.getFormat(
            formatInfo.getFormat(), formatInfo.getFormatSpec());
      }

      return format;
   }

   /**
    * Check if contains format.
    *
    * @return true if contains format.
    */
   @Override
   public boolean containsFormat() {
      return getDescriptor().containsFormat();
   }

   /**
    * Check if contains drill.
    *
    * @return true if contains drill.
    */
   @Override
   public boolean containsDrill() {
      return getDescriptor().containsDrill();
   }

   /**
    * Get internal table data descriptor which contains table structural
    * infos.
    * @return table data descriptor.
    */
   @Override
   public TableDataDescriptor getDescriptor() {
      if(descriptor == null) {
         descriptor = new DefaultTableDataDescriptor(this) {
            /**
             * Get meta info of a specified table data path.
             * @param path the specified table data path.
             * @return meta info of the table data path.
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

               TableDataDescriptor ldescriptor = tables.get(0).getDescriptor();
               XMetaInfo lminfo = ldescriptor.getXMetaInfo(path);

               if(columnIndexMap == null) {
                  columnIndexMap = new ColumnIndexMap(SetTableLens.this, true);
               }

               int col = Util.findColumn(columnIndexMap, path.getPath()[0], false);

               // merge left table meta info and right table meta info
               XMetaInfo minfo = null;

               for(int i = 1; i < tables.size(); i++) {
                  String header = col < 0 || isLeftOnly() ?
                     null : Util.getHeader(getTable(i), col).toString();

                  TableDataPath opath = header == null ? null :
                     new TableDataPath(-1, path.getType(), path.getDataType(),
                                       new String[] {header});

                  TableDataDescriptor rdescriptor = getTable(i).getDescriptor();
                  XMetaInfo rminfo = opath == null ? null :
                     rdescriptor.getXMetaInfo(opath);

                  if(lminfo != null) {
                     minfo = (XMetaInfo) lminfo.clone();

                     if(rminfo != null) {
                        if(minfo.isXDrillInfoEmpty() &&
                           !rminfo.isXDrillInfoEmpty())
                        {
                           minfo.setXDrillInfo(rminfo.getXDrillInfo());
                        }
                     }
                  }
                  else if(rminfo != null) {
                     minfo = (XMetaInfo) rminfo.clone();
                  }

                  // merge format info, if left format info is not auto created,
                  // apply left format, else if right format info is not auto
                  // created, apply right format, otherwise, if left and right are
                  // both auto created, ignore format
                  // fix bug1261024513242, bug1260943347453
                  mergeXFormat(minfo, lminfo, rminfo);
                  lminfo = rminfo;
               }

               mmap.put(path, minfo == null ? Tool.NULL : (Object) minfo);
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
             * @return true if contains format.
             */
            @Override
            public boolean containsFormat() {
               boolean result = false;

               for(TableLens table : tables) {
                  if(table.containsFormat()) {
                     result = true;
                     break;
                  }
               }

               return result;
            }

            /**
             * Check if contains drill.
             * @return <tt>true</tt> if contains drill.
             */
            @Override
            public boolean containsDrill() {
               boolean result = false;

               for(TableLens table : tables) {
                  if(table.containsDrill()) {
                     result = true;
                     break;
                  }
               }

               return result;
            }

            /**
             * Merge left and right XMetaInfo's XFormatInfo.
             */
            private void mergeXFormat(XMetaInfo minfo, XMetaInfo linfo,
                                      XMetaInfo rinfo)
            {
               if(minfo == null) {
                  return;
               }

               XFormatInfo finfo = null;

               // left meta info is not auto created format? apply it
               if(linfo != null && !Util.isXFormatInfoReplaceable(linfo)) {
                  finfo = linfo.getXFormatInfo();
               }

               // right meta info is not auto created format? apply it
               if(finfo == null && rinfo != null &&
                  !Util.isXFormatInfoReplaceable(rinfo))
               {
                  finfo = rinfo.getXFormatInfo();
               }

               if(finfo != null) {
                  minfo.setXFormatInfo(finfo);
               }
            }

            private transient ColumnIndexMap columnIndexMap = null;
         };
      }

      return descriptor;
   }

   /**
    * Set the base table lenses.
    * @param ltable the specified left base table lens, <tt>null</tt> is not
    * allowed.
    * @param rtable the specified right base table lens, <tt>null</tt> is
    * allowed.
    */
   public void setTables(TableLens ltable, TableLens rtable) {
      setTable(0, ltable);
      setTable(1, rtable);
   }

   public void setTable(int index, TableLens table) {
      disposed = false;

      if(tables.size() == index) {
         tables.add(table);
      }
      else {
         tables.set(index, table);
      }

      if(table != null) {
         table.addChangeListener(new DefaultTableChangeListener(this));
         ccount = tables.size() == 1 ?
            table.getColCount() : Math.min(ccount, table.getColCount());
      }

      invalidate();
   }

   /**
    * Cancel the table lens and running queries if supported.
    */
   @Override
   public void cancel() {
      cancelLock.lock();

      try {
         cancelled = !completed;
         // a failure of a pass that started before this cancel is not a failed merge, a pass
         // started after it is (bug #77524)
         cancels++;
         MergedTable old = merged;

         // close the merged table holding only cancelLock, which the merge worker never
         // takes: the worker holds the btree monitor and takes the merged table's and this
         // lens's monitors while it visits (bug #77397). the worker's epilogue then nulls
         // the field and completes the rows found so far
         if(old != null) {
            cancelled = true;
            old.dispose();
         }

         for(TableLens table : tables) {
            if(table instanceof CancellableTableLens) {
               ((CancellableTableLens) table).cancel();
            }
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
      MergedTable old;

      synchronized(this) {
         // a failed pass left the lens unvalidated, the next read retries at once (bug #77524)
         failure = null;

         if(!validated) {
            return;
         }

         // don't dispose the rows, a lock-free reader or the superseded worker may still
         // hold them, and close the merged table outside of this monitor: the worker
         // holds its btree monitor and takes this one while it visits (bug #77397)
         old = merged;
         merged = null;
         rows = null;
         lastIdx = -1;
         lastRow = null;
         completed = false;
         validated = false;
         stallFailure = null;
         scannedRows = 0;
         mmap.clear();
      }

      if(old != null) {
         old.dispose();
      }

      fireChangeEvent();
   }

   /**
    * Create a merged table.
    * @return the created merged table.
    */
   protected MergedTable createMergedTable() throws Exception {
      return new MergedTable();
   }

   /**
    * Validate the set table lens.
    */
   private void validate() throws Exception {
      final MergedTable created;
      final XSwappableObjectList<Row> target;
      final int cancelGen;

      synchronized(this) {
         if(validated) {
            return;
         }

         // dispose() removed (or is removing) the tables, the lens is an empty complete
         // table then. never create a merged table (a temp file) for it (bug #77524)
         if(disposed || tables.isEmpty()) {
            validated = true;
            completed = true;
            return;
         }

         // a pass failed recently, fail this read as well instead of retrying, or a reader
         // per cell would retry (and log) once per cell (bug #77524)
         throwRecentFailure();

         // create the merged table before publishing the rows, a failure must never leave
         // a completed empty table (bug #77524)
         try {
            created = createMergedTable();
         }
         catch(Exception ex) {
            if(LockStallException.find(ex) != null) {
               throw ex;
            }

            throw recordFailure(ex);
         }

         // read the header row count before publishing the pass too, a base failing here
         // must fail the read as well (bug #77524)
         final int headerCount;

         try {
            headerCount = tables.get(0).getHeaderRowCount();
         }
         catch(Exception ex) {
            // not published, no other thread holds it
            created.dispose();

            if(LockStallException.find(ex) != null) {
               throw ex;
            }

            throw recordFailure(ex);
         }

         rows = target = new XSwappableObjectList<>(null);
         validated = true;
         merged = created;
         failure = null;
         cancelGen = cancels;

         for(int i = 0; i < headerCount; i++) {
            Row row = new Row(0, i);
            addSetRow(row);
         }

         // notify waiting consumers
         notifyAll();
      }

      int[] cols = new int[ccount];

      for(int i = 0; i < cols.length; i++) {
         cols[i] = i;
      }

      // blocked process
      try {
         for(int i = 0; i < tables.size(); i++) {
            created.addTable(tables.get(i), i, cols);
         }
      }
      catch(Exception ex) {
         // a stall while reading the bases on this thread fails this lens for every reader,
         // the rows so far are never the whole table (bug #76967)
         LockStallException stall = LockStallException.find(ex);

         if(stall != null) {
            synchronized(this) {
               // a copy is kept, which is never thrown: the stall is thrown on, maybe through
               // a script, which adds a suppressed stack trace element that cannot be
               // serialized to it (bug #78084)
               stallFailure = stall.copy();

               if(rows != null) {
                  rows.complete();
               }

               if(merged != null && !merged.isDisposed()) {
                  merged.dispose();
                  merged = null;
               }

               completed = true;
               // notify waiting consumers
               notifyAll();
            }

            throw ex;
         }

         // any other failure is never the end of the table either. a pass that cancel(),
         // dispose() or invalidate() ended completes (or leaves) its rows as before
         // (bug #77524)
         boolean failed;

         synchronized(this) {
            failed = isFailedPass(created, target, cancelGen);

            if(failed) {
               resetFailedPass(ex);
            }
            else if(rows == target) {
               target.complete();
               completed = true;
            }

            if(merged == created) {
               merged = null;
            }

            notifyAll();
         }

         created.dispose();

         if(failed) {
            LOG.error("Failed to merge the tables of a set operation", ex);
            throw new SetOperationException(ex);
         }

         return;
      }

      final MergedTable merged2 = created;
      final Pass pass = new Pass(target, cancelGen);

      // if this is called from JavaScriptEngine.exec() or a condition filter
      // (bug #76938), the script engine is already locked. merging in a separate
      // thread would create a deadlock waiting forever for the JavaScriptEngine lock
      // to be released.
      if(JavaScriptEngine.holdsScriptLock()) {
         merge(merged2, pass);

         // the merge failed on this thread, fail this read too (bug #77524)
         synchronized(this) {
            Exception last = failure;

            if(!validated && last != null) {
               throw new SetOperationException(last);
            }
         }

         return;
      }

      // concurrent process. a thread holding the lock may still wait for this worker
      // later on, it lends the lock to the worker then (see moreRows)
      LendableReentrantLock.Borrower borrower = new LendableReentrantLock.Borrower();
      worker = borrower;

      ThreadPool.addOnDemand(new ThreadPool.AbstractContextRunnable() {
         @Override
         public void run() {
            borrower.begin();

            try {
               merge(merged2, pass);
            }
            finally {
               borrower.end();
            }
         }
      });
   }

   /**
    * Check if a pass failed by itself: it is still the current pass and was not ended by
    * cancel(), dispose() or invalidate(), whatever exception that made the pass throw.
    * Only a cancel() since the pass started counts, the cancelled flag outlives the pass.
    * Called holding this lens's monitor (bug #77524).
    */
   private boolean isFailedPass(MergedTable merged2, XSwappableObjectList<Row> target,
                                int cancelGen)
   {
      return !merged2.isDisposed() && !disposed && cancels == cancelGen && rows == target;
   }

   /**
    * Drop the rows of a failed pass and record the failure, so the readers fail instead of
    * taking the rows so far for the whole table, and a read after the retry delay runs the
    * pass again. Called holding this lens's monitor (bug #77524).
    */
   private void resetFailedPass(Exception ex) {
      rows = null;
      lastIdx = -1;
      lastRow = null;
      completed = false;
      validated = false;
      scannedRows = 0;
      failure = keptFailure(ex);
      failureTime = System.nanoTime();
   }

   /**
    * Record that the merged table could not be created, nothing was published yet. Called
    * holding this lens's monitor (bug #77524).
    */
   private SetOperationException recordFailure(Exception ex) {
      failure = keptFailure(ex);
      failureTime = System.nanoTime();
      LOG.error("Failed to create the merged table of a set operation", ex);
      return new SetOperationException(ex);
   }

   /**
    * The failure of a pass to keep for the later readers: a copy of a lost swap file or a
    * load failure, which is never thrown itself (bug #78084).
    */
   private static Exception keptFailure(Exception ex) {
      return ex instanceof RuntimeException rex ? DataUnavailable.copy(rex) : ex;
   }

   /**
    * Rethrow the failure of the last pass until the retry delay passed. Called holding this
    * lens's monitor (bug #77524).
    */
   private void throwRecentFailure() {
      Exception last = failure;

      if(last != null &&
         System.nanoTime() - failureTime < TimeUnit.MILLISECONDS.toNanos(getFailureRetryDelay()))
      {
         throw new SetOperationException(last);
      }
   }

   /**
    * The time after a failed pass during which reads fail without running the pass again.
    */
   long getFailureRetryDelay() {
      return FAILURE_RETRY_DELAY;
   }

   /**
    * Thrown by a read of a set table lens whose merge failed, the rows so far are not the
    * whole table (bug #77524). The message is shown to the user, so it never includes the
    * cause (e.g. a cache file path), the cause is chained and logged.
    */
   static final class SetOperationException extends MessageException {
      SetOperationException(Exception cause) {
         super(Catalog.getCatalog().getString("common.table.getDataFailed"), cause);
      }
   }

   /**
    * Merge the tables into the set rows.
    */
   private void merge(MergedTable merged2, Pass pass) {
      LockStallException stall = null;
      Exception error = null;

      try {
         MergedTable.Visitor visitor = getVisitor(pass);

         // count the visited rows, the worker's progress for the lock-stall watchdog, it may
         // add few rows (e.g. intersect) (bug #76967)
         merged2.accept(visitor == null ? null : row -> {
            scannedRows++;
            visitor.visit(row);
         });
      }
      catch(InterruptedException ex) {
         // ignore it
      }
      catch(LockStallException ex) {
         // logged by the wait site; the readers rethrow it rather than take the rows so far
         // for the whole table (bug #76967). kept only if this pass is current (bug #77397)
         stall = ex;
      }
      catch(Exception ex) {
         // a stall may reach the worker wrapped by the base table (bug #76967)
         stall = LockStallException.find(ex);

         if(stall == null) {
            error = ex;
         }

         LOG.error("Failed to merge tables", ex);
      }

      // complete only this pass's own rows, a superseded pass must not complete (or fail)
      // the next pass's rows. a cancelled pass (merged2 disposed) still completes the rows
      // found so far, or its readers would wait forever (bug #77397)
      synchronized(SetTableLens.this) {
         // the rows found before a failure are not the whole table, the readers fail and a
         // later read runs the pass again (bug #77524)
         if(error != null && isFailedPass(merged2, pass.target, pass.cancelGen)) {
            resetFailedPass(error);
            SetTableLens.this.notifyAll();
         }
         else if(rows == pass.target) {
            if(stall != null) {
               // a copy, which is never thrown (bug #78084)
               stallFailure = stall.copy();
            }

            pass.target.complete();
            completed = true;
            SetTableLens.this.notifyAll();
         }

         if(merged == merged2) {
            merged = null;
         }
      }

      merged2.dispose();
   }

   /**
    * The rows of one merge pass. A visitor adds through its pass, so a pass superseded by
    * invalidate() never adds to the rows of the next pass (bug #77397).
    */
   protected final class Pass {
      Pass(XSwappableObjectList<Row> target, int cancelGen) {
         this.target = target;
         this.cancelGen = cancelGen;
      }

      /**
       * Add a row to this pass's rows, or stop the visit once invalidate() replaced them.
       */
      public void add(Row row) throws InterruptedException {
         if(rows != target) {
            throw new InterruptedException("superseded");
         }

         target.add(row);
      }

      public int size() {
         return target.size();
      }

      final XSwappableObjectList<Row> target;
      final int cancelGen; // cancels when the pass started
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
      // the first read of any row, a header row too, adds every base row to the merged table
      // under this lens's monitor (bug #77681), so it takes the engine lock the bases need
      // before the monitor, like a condition filter (bug #76918). a thread holding that lock
      // may be waiting for the monitor to read this lens, it would wait forever for a reader
      // holding the monitor between two base rows (bug #77874). holding the lock, validate()
      // merges on this thread, no worker is started that needs it
      LendableReentrantLock execLock = validated ? null : getUnheldChainScriptLock();

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
    * Get the engine lock reading the base tables may take if the current thread does not hold
    * it (bug #77874).
    */
   private LendableReentrantLock getUnheldChainScriptLock() {
      LendableReentrantLock execLock = ChainScriptLock.find(this);
      return execLock != null && !execLock.isHeldByCurrentThread() ? execLock : null;
   }

   private boolean moreRows0(int row) {
      WaitRecord record = null;
      // the failure of an earlier pass, this read retries it and fails only on a new one
      Exception oldFailure = failure;

      try {
         while(true) {
            // no loan and no monitor is held here, so a stall exception leaks neither
            // (bug #76967)
            if(record != null) {
               record.checkStall();
            }

            LendableReentrantLock.Borrower lendTo;

            synchronized(this) {
               Exception last = failure;

               // the pass this read waited for failed, fail even if the retry delay passed
               // (bug #77524)
               if(!validated && last != null && last != oldFailure) {
                  throw new SetOperationException(last);
               }

               validate();

               if(rows != null && row < rows.size()) {
                  return true;
               }

               if(completed) {
                  throwStallFailure();
                  return false;
               }

               lendTo = worker;

               if(record != null && !JavaScriptEngine.canLendScriptLocks(lendTo)) {
                  try {
                     wait(record.waitMillis(50));
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
               record = WaitRegistry.begin("SetTableLens.moreRows", this::getWorkerProgress,
                                           this::getWorkerThreads);
               continue;
            }

            // this thread holds or was lent a script engine lock (e.g. by a condition filter)
            // that the worker may need to read the base tables, lend it to the worker
            // while waiting (bug #76938). the loan is closed outside of this lens's
            // monitor, which the worker needs in order to publish rows
            try(LendableReentrantLock.Loan ignored = JavaScriptEngine.lendScriptLocks(lendTo)) {
               synchronized(this) {
                  if((rows == null || row >= rows.size()) && !completed) {
                     try {
                        wait(record.waitMillis(50));
                     }
                     catch(InterruptedException ex) {
                        // ignore it
                     }
                  }
               }
            }
         }
      }
      catch(LockStallException | SetOperationException ex) {
         // a stall fails the query, it is never the end of the table (bug #76967), neither
         // is a failed merge (bug #77524)
         throw ex;
      }
      catch(Exception ex) {
         // a stall may reach this thread wrapped, it is never the end of the table either
         // (bug #76967)
         LockStallException stall = LockStallException.find(ex);

         if(stall != null) {
            throw new LockStallException(stall);
         }

         synchronized(this) {
            completed = true;
         }

         LOG.error("Failed to validate rows when checking if row " +
            "is available: " + row, ex);
         return false;
      }
      finally {
         if(record != null) {
            record.close();
         }
      }
   }

   /**
    * Progress of the worker for the lock-stall watchdog: rows added plus rows visited. Read
    * without this lens's monitor, so a stall check never blocks on it.
    */
   private long getWorkerProgress() {
      XSwappableObjectList<Row> list = rows;
      return (list == null ? 0 : list.size()) + (long) scannedRows;
   }

   /**
    * The worker thread, for the lock-stall watchdog.
    */
   private Thread[] getWorkerThreads() {
      LendableReentrantLock.Borrower task = worker;
      return new Thread[] { task == null ? null : task.getThread() };
   }

   /**
    * Rethrow the stall the worker failed with, called when the table is complete. A stall
    * must never look like the end of the table (bug #76967).
    */
   private void throwStallFailure() {
      LockStallException failure = stallFailure;

      if(failure != null) {
         throw new LockStallException(failure);
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
      // a row count probe (e.g. AssetQuery.validateDataTypes) starts nothing if the bases
      // need an engine lock this thread doesn't hold: it must not take the lock, it may be
      // building a table inside that table's monitor (bug #77223). a failure within the retry
      // delay still fails it (bug #77524). the first read of rows merges them (bug #77874)
      if(execLock != null && !validated) {
         throwRecentFailure();
         return -1;
      }

      try {
         validate();

         if(completed) {
            // the rows so far of a stalled worker are not the whole table (bug #76967)
            throwStallFailure();
         }

         XSwappableObjectList<Row> list = rows;

         // only dispose() leaves no rows once validated, report the empty complete table that
         // moreRows() reports instead of -1 (still loading) and an error (bug #77397)
         if(list == null) {
            return 0;
         }

         return completed ? list.size() : -list.size() - 1;
      }
      catch(LockStallException | SetOperationException ex) {
         throw ex;
      }
      catch(Exception ex) {
         // a stall may reach this thread wrapped, it is never the end of the table either
         // (bug #76967)
         LockStallException stall = LockStallException.find(ex);

         if(stall != null) {
            throw new LockStallException(stall);
         }

         completed = true;
         LOG.error("Failed to validate table rows when getting " +
            "row count", ex);
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
      return ccount;
   }

   /**
    * Return the number of rows on the top of the table to be treated
    * as header rows.
    * @return number of header rows.  Default is 1.
    */
   @Override
   public int getHeaderRowCount() {
      return tables.get(0).getHeaderRowCount();
   }

   /**
    * Return the number of columns on the left of the table to be
    * treated as header columns.
    */
   @Override
   public int getHeaderColCount() {
      return tables.get(0).getHeaderColCount();
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
      boolean result = true;

      for(TableLens table : tables) {
         if(!table.isPrimitive(col)) {
            result = false;
            break;
         }
      }

      return result;
   }

   /**
    * Check if the value at one cell is null.
    * @param r the specified row index.
    * @param c column number.
    * @return <tt>true</tt> if null, <tt>false</tt> otherwise.
    */
   @Override
   public final boolean isNull(int r, int c) {
      Row row = findRow(r);

      if(row == null) {
         return true;
      }

      TableLens table = tables.get(row.getTable());
      return table.isNull(row.getRow(), c);
   }

   /**
    * Return the value at the specified cell.
    * @param r row number.
    * @param c column number.
    * @return the value at the location.
    */
   @Override
   public Object getObject(int r, int c) {
      if(!moreRows(r)) {
         return null;
      }

      Row row = findRow(r);

      if(row == null) {
         return null;
      }

      TableLens table = tables.get(row.getTable());
      return table.getObject(row.getRow(), c);
   }

   /**
    * Get the double value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the double value in the specified row.
    */
   @Override
   public final double getDouble(int r, int c) {
      Row row = findRow(r);

      if(row == null) {
         return 0;
      }

      TableLens table = tables.get(row.getTable());
      return table.getDouble(row.getRow(), c);
   }

   /**
    * Get the float value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the float value in the specified row.
    */
   @Override
   public final float getFloat(int r, int c) {
      Row row = findRow(r);

      if(row == null) {
         return 0;
      }

      TableLens table = tables.get(row.getTable());
      return table.getFloat(row.getRow(), c);
   }

   /**
    * Get the long value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the long value in the specified row.
    */
   @Override
   public final long getLong(int r, int c) {
      Row row = findRow(r);

      if(row == null) {
         return 0;
      }

      TableLens table = tables.get(row.getTable());
      return table.getLong(row.getRow(), c);
   }

   /**
    * Get the int value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the int value in the specified row.
    */
   @Override
   public final int getInt(int r, int c) {
      Row row = findRow(r);

      if(row == null) {
         return 0;
      }

      TableLens table = tables.get(row.getTable());
      return table.getInt(row.getRow(), c);
   }

   /**
    * Get the short value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the short value in the specified row.
    */
   @Override
   public final short getShort(int r, int c) {
      Row row = findRow(r);

      if(row == null) {
         return 0;
      }

      TableLens table = tables.get(row.getTable());
      return table.getShort(row.getRow(), c);
   }

   /**
    * Get the byte value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the byte value in the specified row.
    */
   @Override
   public final byte getByte(int r, int c) {
      Row row = findRow(r);

      if(row == null) {
         return 0;
      }

      TableLens table = tables.get(row.getTable());
      return table.getByte(row.getRow(), c);
   }

   /**
    * Get the boolean value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the boolean value in the specified row.
    */
   @Override
   public final boolean getBoolean(int r, int c) {
      Row row = findRow(r);

      if(row == null) {
         return false;
      }

      TableLens table = tables.get(row.getTable());
      return table.getBoolean(row.getRow(), c);
   }

   /**
    * Get the current column content type.
    * @param col column number.
    * @return column type.
    */
   @Override
   public Class<?> getColType(int col) {
      Class<?> clazz = tables.get(0).getColType(col);

      if(tables.size() == 1) {
         return clazz;
      }

      boolean issame = true;
      boolean isnumeric = true;
      Integer weight = NUMERIC_MAP.get(clazz);

      for(int i = 1; i < tables.size(); i++) {
         Class<?> rclazz = tables.get(i).getColType(col);

         if(issame &&
            (clazz != null && !clazz.equals(rclazz)) ||
            (clazz == null && rclazz != null))
         {
            issame = false;
         }
         else if(isnumeric &&
               ((clazz != null && !Number.class.isAssignableFrom(clazz)) ||
               (rclazz != null && !Number.class.isAssignableFrom(rclazz))))
         {
            isnumeric = false;
         }

         if(!(issame || isnumeric)) {
            if(weight == null) {
               clazz = Double.class;
               weight = NUMERIC_MAP.get(clazz);
            }

            Integer rweight = NUMERIC_MAP.get(rclazz);

            if(rweight == null) {
               rclazz = Double.class;
               rweight = NUMERIC_MAP.get(rclazz);
            }

            if(rweight.intValue() > weight.intValue()) {
               clazz = rclazz;
               weight = rweight;
            }
         }
      }

      return clazz;
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
      if(!moreRows(r)) {
         return;
      }

      Row row = findRow(r);

      if(row == null) {
         return;
      }

      TableLens table = tables.get(row.getTable());
      table.setObject(row.getRow(), c, v);
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
      if(!moreRows(r)) {
         return -1;
      }

      Row row = findRow(r);

      if(row == null) {
         return -1;
      }

      TableLens table = tables.get(row.getTable());
      return table.getRowHeight(row.getRow());
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
      return tables.get(0).getColWidth(col);
   }

   /**
    * Return the color for drawing the row border lines.
    * @param r row number.
    * @param c column number.
    * @return ruling color.
    */
   @Override
   public Color getRowBorderColor(int r, int c) {
      if(!moreRows(r)) {
         return null;
      }

      Row row = findRow(r);

      if(row == null) {
         return null;
      }

      TableLens table = tables.get(row.getTable());
      return table.getRowBorderColor(row.getRow(), c);
   }

   /**
    * Return the color for drawing the column border lines.
    * @param r row number.
    * @param c column number.
    * @return ruling color.
    */
   @Override
   public Color getColBorderColor(int r, int c) {
      if(!moreRows(r)) {
         return null;
      }

      Row row = findRow(r);

      if(row == null) {
         return null;
      }

      TableLens table = tables.get(row.getTable());
      return table.getColBorderColor(row.getRow(), c);
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
      if(!moreRows(r)) {
         return -1;
      }

      Row row = findRow(r);

      if(row == null) {
         return -1;
      }

      TableLens table = tables.get(row.getTable());
      return table.getRowBorder(row.getRow(), c);
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
      if(!moreRows(r)) {
         return -1;
      }

      Row row = findRow(r);

      if(row == null) {
         return -1;
      }

      TableLens table = tables.get(row.getTable());
      return table.getColBorder(row.getRow(), c);
   }

   /**
    * Return the cell gap space.
    * @param r row number.
    * @param c column number.
    * @return cell gap space.
    */
   @Override
   public Insets getInsets(int r, int c) {
      if(!moreRows(r)) {
         return null;
      }

      Row row = findRow(r);

      if(row == null) {
         return null;
      }

      TableLens table = tables.get(row.getTable());
      return table.getInsets(row.getRow(), c);
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
      if(!moreRows(r)) {
         return null;
      }

      Row row = findRow(r);

      if(row == null) {
         return null;
      }

      TableLens table = tables.get(row.getTable());
      return table.getSpan(row.getRow(), c);
   }

   /**
    * Return the per cell alignment.
    * @param r row number.
    * @param c column number.
    * @return cell alignment.
    */
   @Override
   public int getAlignment(int r, int c) {
      if(!moreRows(r)) {
         return -1;
      }

      Row row = findRow(r);

      if(row == null) {
         return -1;
      }

      TableLens table = tables.get(row.getTable());
      return table.getAlignment(row.getRow(), c);
   }

   /**
    * Return the per cell font. Return null to use default font.
    * @param r row number.
    * @param c column number.
    * @return font for the specified cell.
    */
   @Override
   public Font getFont(int r, int c) {
      if(!moreRows(r)) {
         return null;
      }

      Row row = findRow(r);

      if(row == null) {
         return null;
      }

      TableLens table = tables.get(row.getTable());
      return table.getFont(row.getRow(), c);
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
      if(!moreRows(r)) {
         return false;
      }

      Row row = findRow(r);

      if(row == null) {
         return false;
      }

      TableLens table = tables.get(row.getTable());
      return table.isLineWrap(row.getRow(), c);
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
      if(!moreRows(r)) {
         return null;
      }

      Row row = findRow(r);

      if(row == null) {
         return null;
      }

      TableLens table = tables.get(row.getTable());
      return table.getForeground(row.getRow(), c);
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
      if(!moreRows(r)) {
         return null;
      }

      Row row = findRow(r);

      if(row == null) {
         return null;
      }

      TableLens table = tables.get(row.getTable());
      return table.getBackground(row.getRow(), c);
   }

   /**
    * Get the merged table visitor.
    * @return the merged table visitor.
    */
   protected abstract MergedTable.Visitor getVisitor(Pass pass);

   /**
    * Finalize the set table lens.
    */
   @Override
   protected void finalize() throws Throwable {
      super.finalize();
      disposeResources();
   }

   /**
    * Dispose the set table lens.
    */
   @Override
   public void dispose() {
      MergedTable old;

      // close the merged table outside of this monitor (bug #77397), and end the waits of
      // the readers
      synchronized(this) {
         // disposeTables() below runs outside of this monitor, a first read meanwhile must
         // not merge the tables being disposed (bug #77524)
         disposed = true;
         old = merged;
         merged = null;

         if(rows != null) {
            rows.dispose();
            rows = null;
         }

         completed = true;
         notifyAll();
      }

      if(old != null) {
         old.dispose();
      }

      disposeTables();
   }

   private void disposeResources() {
      if(merged != null) {
         merged.dispose();
         merged = null;
      }

      if(rows != null) {
         rows.dispose();
         rows = null;
      }
   }

   private void disposeTables() {
      for(Iterator<TableLens> i = tables.iterator(); i.hasNext();) {
         TableLens table = i.next();
         table.dispose();
         i.remove();
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
      String identifier = identifiers.getColumnIdentifier(col);
      return identifier == null ?
         tables.get(0).getColumnIdentifier(col) : identifier;
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
      identifiers.setColumnIdentifier(col, identifier);
   }

   /**
    * Get the associated row object of a row index.
    * @param row the specified row index.
    * @return the associated row object.
    */
   protected synchronized Row getRow(int row) {
      XSwappableObjectList<Row> list = rows;

      if(lastIdx == row && lastList == list && lastRow != null) {
         return lastRow;
      }

      if(row < 0) {
         return new Row(0, row);
      }

      // cache only a row found in the current rows, keyed by the list, a failed lookup must
      // not leave another row cached for this index (bug #77397)
      if(list == null || row >= list.size()) {
         return null;
      }

      Row result = list.get(row);

      if(result != null) {
         lastIdx = row;
         lastRow = result;
         lastList = list;
      }

      return result;
   }

   /**
    * Get the row object, waiting for the row if it is not loaded yet. Called without this
    * lens's monitor, moreRows() may lend the script engine lock (bug #76938). Returns null
    * if the row does not exist, e.g. after invalidate() the rows are rebuilt (bug #77397).
    */
   private Row findRow(int r) {
      for(int retry = 0; ; retry++) {
         Row row;

         try {
            row = getRow(r);
         }
         catch(ArrayIndexOutOfBoundsException ex) {
            // a non-distinct union's row past the end of its bases, e.g. a row moreRows()
            // found before an invalidate() shrank them: the row does not exist (bug #77874)
            row = null;
         }

         if(row != null || retry >= 100 || !moreRows(r)) {
            return row;
         }
      }
   }

   private transient XSwappableObjectList<Row> lastList;

   protected boolean isSetRowsInitialized() {
      return rows != null;
   }

   protected void addSetRow(Row row) {
      rows.add(row);
   }

   protected int getSetRowCount() {
      return rows.size();
   }

   @Serial
   private void readObject(ObjectInputStream in) throws ClassNotFoundException, IOException {
      in.defaultReadObject();
      lastIdx = -1;
   }

   /**
    * Row locates a row in a table.
    */
   protected static final class Row implements Serializable {
      public Row(int table, int row) {
         this.table = table;
         this.row = row;
      }

      public boolean isLeft() {
         return table == 0;
      }

      public boolean isRight() {
         return table == 1;
      }

      public int getTable() {
         return table;
      }

      public int getRow() {
         return row;
      }

      public String toString() {
         return "Row:[" + table + "," + row + "]";
      }

      private final int table;
      private final int row;
   }

   private static final Map<Class<?>, Integer> NUMERIC_MAP = new HashMap<>();

   static {
      NUMERIC_MAP.put(Byte.class, Integer.valueOf(1));
      NUMERIC_MAP.put(Short.class, Integer.valueOf(2));
      NUMERIC_MAP.put(Integer.class, Integer.valueOf(3));
      NUMERIC_MAP.put(Long.class, Integer.valueOf(4));
      NUMERIC_MAP.put(Float.class, Integer.valueOf(5));
      NUMERIC_MAP.put(Double.class, Integer.valueOf(6));
   }

   private transient TableDataDescriptor descriptor;
   private Map<TableDataPath, Object> mmap = new HashMap<>();

   private XIdentifierContainer identifiers = null;
   private List<TableChangeListener> clisteners = new ArrayList<>();
   private transient TableChangeEvent event;

   private volatile XSwappableObjectList<Row> rows; // rows
   private int ccount;                  // column count
   private volatile MergedTable merged;          // temporary merged table
   private final List<TableLens> tables = new ArrayList<>();
   private boolean completed;           // completed flag
   private volatile boolean cancelled;           // cancelled flag
   private volatile int cancels;                 // cancel() count, under cancelLock
   private volatile boolean disposed;            // dispose() called
   private final Lock cancelLock = new ReentrantLock();
   // validated flag, read outside of the monitor by moreRows and getRowCount (bug #77874)
   private volatile boolean validated = false;
   // the background task merging the tables, if any
   private transient volatile LendableReentrantLock.Borrower worker;
   // the merged rows the worker visited, and the stall it failed with (bug #76967)
   private transient volatile int scannedRows;
   private transient volatile LockStallException stallFailure;
   // the failure of the last pass and when it failed (bug #77524)
   private transient volatile Exception failure;
   private transient long failureTime;
   private static final long FAILURE_RETRY_DELAY = 1000L;

   // optimization
   private transient Row lastRow = null;
   private transient int lastIdx = -1;

   private static final Logger LOG =
      LoggerFactory.getLogger(SetTableLens.class);
}
