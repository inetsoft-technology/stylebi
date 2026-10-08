/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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

import inetsoft.report.TableLens;
import inetsoft.report.filter.SumFormula;
import inetsoft.report.filter.SummaryFilter;
import inetsoft.report.internal.table.MergedTable;
import inetsoft.test.*;
import inetsoft.uql.table.XSwappableTable;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A summary filter whose pass failed to read the base fails a cell read that its completed
 * rows release, even while the worker has not yet marked the pass completed, it never reads
 * the empty rows as the end of the table (bug #78011).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
@Timeout(value = 60, unit = TimeUnit.SECONDS)
public class SummaryFilterCompletionOrderTest {
   /**
    * The worker of the restarted pass is held right after it completes the rows of the pass,
    * until the reader released by them returns or waits for the filter's monitor. The read
    * must fail with the base failure, not return null.
    */
   @Test
   public void getObjectReleasedByCompletedRowsThrows() throws Exception {
      SummaryFilter summary = new SummaryFilter(
         new FailingMinus(), new int[] { 0 }, new int[] { 1 }, new SumFormula(), null);

      // the reported failure restarts the pass, the next read starts its worker
      assertThrows(SetTableLens.SetOperationException.class,
                   () -> summary.moreRows(TableLens.EOT));

      Object pass = get(SummaryFilter.class, summary, "pass");
      XSwappableTable rows = (XSwappableTable) get(pass.getClass(), pass, "rows");
      GatedRows gated = new GatedRows(rows.getColCount(), Thread.currentThread(), summary);
      set(pass.getClass(), pass, "rows", gated);

      Object result;

      try {
         result = summary.getObject(1, 1);
      }
      catch(SetTableLens.SetOperationException ex) {
         assertTrue(gated.held, "the worker was held after it completed the rows");
         return;
      }
      finally {
         gated.release(pass);
      }

      // the pass as the reader saw it, the worker is still held
      Object completed = gated.completedAtRead;
      Object failure = gated.failureAtRead;
      assertTrue(gated.held, "the worker was held after it completed the rows");
      // and once the worker finished it
      Thread.sleep(300);
      fail("a failed summary returned a result: " + result + ", the pass had completed=" +
           completed + ", baseFailure=" + failure + " at the read, and completed=" +
           get(pass.getClass(), pass, "completed") + ", baseFailure=" +
           get(pass.getClass(), pass, "baseFailure") + " 300 ms later");
   }

   private static Object get(Class<?> type, Object obj, String name) throws Exception {
      Field field = type.getDeclaredField(name);
      field.setAccessible(true);
      return field.get(obj);
   }

   private static void set(Class<?> type, Object obj, String name, Object value)
      throws Exception
   {
      Field field = type.getDeclaredField(name);
      field.setAccessible(true);
      field.set(obj, value);
   }

   /**
    * Rows whose first completion by a thread other than the reader holds that thread, the
    * pass worker inside the filter's monitor, until the reader returns or blocks on the
    * filter's monitor.
    */
   private static final class GatedRows extends XSwappableTable {
      GatedRows(int ncol, Thread reader, SummaryFilter summary) {
         super(ncol, false);
         this.reader = reader;
         this.summary = summary;
      }

      @Override
      public void complete() {
         super.complete();

         if(Thread.currentThread() == reader || held) {
            return;
         }

         held = true;
         long end = System.currentTimeMillis() + 20000;

         while(!released && !isBlockedOnSummary() && System.currentTimeMillis() < end) {
            Thread.onSpinWait();
         }
      }

      /**
       * Called by the reader once its read returned, the worker is still held: keep the state
       * of the pass the reader saw, and let the worker go on.
       */
      void release(Object pass) {
         try {
            completedAtRead = get(pass.getClass(), pass, "completed");
            failureAtRead = get(pass.getClass(), pass, "baseFailure");
         }
         catch(Exception ex) {
            failureAtRead = ex;
         }

         released = true;
      }

      private boolean isBlockedOnSummary() {
         ThreadInfo info =
            ManagementFactory.getThreadMXBean().getThreadInfo(reader.threadId());
         return info != null && info.getThreadState() == Thread.State.BLOCKED &&
            info.getLockInfo() != null &&
            info.getLockInfo().getIdentityHashCode() == System.identityHashCode(summary);
      }

      private final Thread reader;
      private final SummaryFilter summary;
      volatile boolean held;
      volatile boolean released;
      volatile Object completedAtRead;
      volatile Object failureAtRead;
   }

   /**
    * A minus whose merged table can't be created, the reporter's base (bug #77875).
    */
   private static final class FailingMinus extends MinusTableLens {
      FailingMinus() {
         super(new DefaultTableLens(data(5)), new DefaultTableLens(data(0)));
      }

      @Override
      protected MergedTable createMergedTable() throws Exception {
         throw new IOException("Cannot create the cache temp file of a merged table");
      }
   }

   private static Object[][] data(int rows) {
      Object[][] data = new Object[rows + 1][];
      data[0] = new Object[] { "id", "value" };

      for(int r = 1; r <= rows; r++) {
         data[r] = new Object[] { "k" + r, r };
      }

      return data;
   }
}
