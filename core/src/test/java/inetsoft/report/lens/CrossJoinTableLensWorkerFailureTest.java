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
import inetsoft.test.*;
import inetsoft.util.swap.SwapFileReadException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.util.concurrent.*;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A cross join worker that fails with something other than a stall fails the readers of the
 * lens rather than leaving them waiting forever (bug #77907).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class CrossJoinTableLensWorkerFailureTest {
   @Test
   public void runtimeExceptionFailsReader() throws Exception {
      IllegalStateException failure = new IllegalStateException("base failed");
      FailingTable left = new FailingTable(() -> failure);
      CrossJoinTableLens lens = new CrossJoinTableLens(left, new DefaultTableLens(data(5)));
      left.armed = true;

      try {
         Throwable thrown = readFailure(lens);
         assertSame(failure, thrown.getCause(), String.valueOf(thrown));
         // the rows so far are not the whole table
         RuntimeException count = assertThrows(RuntimeException.class, lens::getRowCount);
         assertSame(failure, count.getCause());
      }
      finally {
         lens.dispose();
      }
   }

   @Test
   public void lostSwapFileIsRethrownAsIs() throws Exception {
      SwapFileReadException failure =
         new SwapFileReadException(new File("lost.swap"), new java.io.IOException("gone"));
      FailingTable left = new FailingTable(() -> failure);
      CrossJoinTableLens lens = new CrossJoinTableLens(left, new DefaultTableLens(data(5)));
      left.armed = true;

      try {
         assertSame(failure, readFailure(lens));
         assertSame(failure, assertThrows(SwapFileReadException.class, lens::getRowCount));
      }
      finally {
         lens.dispose();
      }
   }

   @Test
   public void errorFailsReader() throws Exception {
      StackOverflowError failure = new StackOverflowError("base failed");
      FailingTable left = new FailingTable(() -> failure);
      CrossJoinTableLens lens = new CrossJoinTableLens(left, new DefaultTableLens(data(5)));
      left.armed = true;

      try {
         assertSame(failure, readFailure(lens).getCause());
      }
      finally {
         lens.dispose();
      }
   }

   /**
    * A worker superseded by invalidate() that fails afterwards doesn't fail the next pass,
    * and invalidate() clears a recorded failure for the next pass to retry.
    */
   @Test
   public void supersededWorkerFailureDoesNotFailNextPass() throws Exception {
      CountDownLatch entered = new CountDownLatch(1);
      CountDownLatch release = new CountDownLatch(1);
      Thread[] worker = new Thread[1];
      FailingTable left = new FailingTable(() -> {
         worker[0] = Thread.currentThread();
         entered.countDown();

         try {
            release.await(CAP_SECONDS, TimeUnit.SECONDS);
         }
         catch(InterruptedException ex) {
            Thread.currentThread().interrupt();
         }

         return new IllegalStateException("superseded");
      });
      CrossJoinTableLens lens = new CrossJoinTableLens(left, new DefaultTableLens(data(5)));
      left.armed = true;

      try {
         // starts the workers without waiting for them
         lens.getRowCount();
         assertTrue(entered.await(CAP_SECONDS, TimeUnit.SECONDS));

         left.armed = false;
         lens.invalidate();
         release.countDown();
         worker[0].join(CAP_SECONDS * 1000L);
         assertFalse(worker[0].isAlive());

         assertFalse(lens.moreRows(TableLens.EOT));
         assertEquals(3 * 5 + 1, lens.getRowCount());
      }
      finally {
         release.countDown();
         lens.dispose();
      }
   }

   @Test
   public void invalidateRetriesAfterFailure() throws Exception {
      FailingTable left = new FailingTable(() -> new IllegalStateException("base failed"));
      CrossJoinTableLens lens = new CrossJoinTableLens(left, new DefaultTableLens(data(5)));
      left.armed = true;

      try {
         readFailure(lens);
         left.armed = false;
         lens.invalidate();

         assertFalse(lens.moreRows(TableLens.EOT));
         assertEquals(3 * 5 + 1, lens.getRowCount());
      }
      finally {
         lens.dispose();
      }
   }

   /**
    * Read to the end of the lens on another thread, and return what the read failed with.
    */
   private static Throwable readFailure(CrossJoinTableLens lens) throws Exception {
      Future<Boolean> read = EXECUTOR.submit(() -> lens.moreRows(TableLens.EOT));

      try {
         read.get(CAP_SECONDS, TimeUnit.SECONDS);
      }
      catch(ExecutionException ex) {
         return ex.getCause();
      }
      catch(TimeoutException ex) {
         lens.dispose();
         fail("the reader still waits for the failed worker");
      }

      return fail("the reader didn't fail");
   }

   private static Object[][] data(int rows) {
      Object[][] data = new Object[rows + 1][];
      data[0] = new Object[] { "id", "value" };

      for(int r = 1; r <= rows; r++) {
         data[r] = new Object[] { r, r };
      }

      return data;
   }

   /**
    * A table whose moreRows() fails once armed, after the cross join has loaded it on the
    * constructing thread.
    */
   private static class FailingTable extends DefaultTableLens {
      FailingTable(Supplier<Throwable> failure) {
         super(data(3));
         this.failure = failure;
      }

      @Override
      public boolean moreRows(int row) {
         if(armed) {
            Throwable ex = failure.get();

            if(ex instanceof Error error) {
               throw error;
            }

            throw (RuntimeException) ex;
         }

         return super.moreRows(row);
      }

      private final Supplier<Throwable> failure;
      private volatile boolean armed;
   }

   private static final int CAP_SECONDS = 10;
   private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(r -> {
      Thread thread = new Thread(r, "CrossJoinTableLensWorkerFailureTest-reader");
      thread.setDaemon(true);
      return thread;
   });
}
