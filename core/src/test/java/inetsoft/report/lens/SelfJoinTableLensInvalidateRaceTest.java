/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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
import inetsoft.util.stall.LockStallException;
import inetsoft.util.stall.StallPolicy;
import inetsoft.util.stall.StallTestSupport;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A read of a self join table lens concurrent with invalidate() returns the joined row, and a
 * worker superseded by invalidate() neither fails nor ends the next pass (bug #77397).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class SelfJoinTableLensInvalidateRaceTest {
   @BeforeEach
   public void setUp() {
      StallTestSupport.resetGlobalStallState();
      // the production policy: a wait fails only once a cycle is confirmed
      StallPolicy.setOverride(
         new StallPolicy(StallPolicy.Mode.FAIL, 1000, 200, dumpDir, 20, false));
   }

   @AfterEach
   public void tearDown() {
      StallTestSupport.clearOverride();
   }

   @Test
   public void readsDuringInvalidateReturnJoinedRow() throws Exception {
      SelfJoinTableLens lens = join(new DefaultTableLens(data(ROWS)));
      assertFalse(lens.moreRows(TableLens.EOT));

      AtomicBoolean done = new AtomicBoolean();
      AtomicLong invalidations = new AtomicLong();
      Map<String, Integer> results = new ConcurrentHashMap<>();
      AtomicReference<Throwable> firstError = new AtomicReference<>();

      Thread invalidator = new Thread(() -> {
         while(!done.get()) {
            lens.invalidate();
            lens.moreRows(TableLens.EOT);
            invalidations.incrementAndGet();
         }
      }, "SelfJoinTableLensInvalidateRaceTest-invalidator");

      Thread[] readers = new Thread[READERS];

      for(int i = 0; i < readers.length; i++) {
         readers[i] = new Thread(() -> {
            ThreadLocalRandom random = ThreadLocalRandom.current();

            while(!done.get()) {
               // every row joins itself: row r is base row r, id = value = r
               int r = 1 + random.nextInt(ROWS);
               String result;

               try {
                  Object value = lens.getObject(r, 1);

                  if(Integer.valueOf(r).equals(value)) {
                     result = "ok";
                  }
                  else if("value".equals(value)) {
                     result = "header";
                  }
                  else {
                     result = "wrong";
                  }
               }
               catch(Throwable ex) {
                  result = ex.getClass().getSimpleName();
                  firstError.compareAndSet(null, ex);
               }

               results.merge(result, 1, Integer::sum);
            }
         }, "SelfJoinTableLensInvalidateRaceTest-reader-" + i);
      }

      invalidator.start();

      for(Thread reader : readers) {
         reader.start();
      }

      Thread.sleep(DURATION_MS);
      done.set(true);
      invalidator.join(JOIN_MS);

      for(Thread reader : readers) {
         reader.join(JOIN_MS);
      }

      assertFalse(invalidator.isAlive(), "invalidator did not stop");

      for(Thread reader : readers) {
         assertFalse(reader.isAlive(), "reader did not stop");
      }

      String summary = "invalidations=" + invalidations.get() + " results=" + results +
         (firstError.get() == null ? "" : ", first error: " + stack(firstError.get()));
      assertTrue(invalidations.get() > 0, summary);
      assertTrue(results.getOrDefault("ok", 0) > 0, summary);
      assertEquals(1, results.size(), summary);
   }

   /**
    * The worker of the first pass stalls after invalidate(), while the next pass is still
    * running: the next pass neither fails with that stall nor ends early with the rows so far.
    */
   @Test
   public void supersededStallDoesNotFailOrEndNextPass() throws Exception {
      StallingTable base = new StallingTable(ROWS);
      SelfJoinTableLens lens = join(base);
      ExecutorService pool = Executors.newCachedThreadPool(daemons());

      try {
         // the first pass stops at the stall row
         assertTrue(lens.moreRows(1));
         Thread first = base.stalled.get(CAP_SECONDS, TimeUnit.SECONDS);

         lens.invalidate();
         // the next pass stops at a later row
         Future<Boolean> eot = pool.submit(() -> lens.moreRows(TableLens.EOT));
         Thread second = base.parked.get(CAP_SECONDS, TimeUnit.SECONDS);
         assertNotSame(first, second);

         // the first pass fails with a stall, and is done
         base.stall.countDown();
         awaitTrue(() -> !isIn(first, SelfJoinTableLens.class), "the first pass ends");

         base.gate.countDown();
         assertFalse(eot.get(CAP_SECONDS, TimeUnit.SECONDS));
         assertEquals(ROWS + 1, lens.getRowCount());

         int rows = 0;

         for(int r = 0; lens.moreRows(r); r++) {
            rows++;
         }

         assertEquals(ROWS + 1, rows);
      }
      finally {
         base.stall.countDown();
         base.gate.countDown();
         pool.shutdownNow();
      }
   }

   private static String stack(Throwable ex) {
      java.io.StringWriter out = new java.io.StringWriter();
      ex.printStackTrace(new java.io.PrintWriter(out));
      return out.toString();
   }

   private static SelfJoinTableLens join(TableLens base) {
      SelfJoinTableLens lens = new SelfJoinTableLens(base);
      lens.addJoin(0, SelfJoinTableLens.INNER_JOIN, 1);
      return lens;
   }

   private static ThreadFactory daemons() {
      return r -> {
         Thread thread = new Thread(r);
         thread.setDaemon(true);
         return thread;
      };
   }

   private static boolean isIn(Thread thread, Class<?> cls) {
      for(StackTraceElement element : thread.getStackTrace()) {
         // a pool thread appends the stack that created it
         if(element.getClassName().equals(Thread.class.getName()) &&
            element.getMethodName().equals("getStackTrace"))
         {
            break;
         }

         if(element.getClassName().startsWith(cls.getName())) {
            return true;
         }
      }

      return false;
   }

   private static void awaitTrue(java.util.function.BooleanSupplier condition, String what)
      throws InterruptedException
   {
      long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(CAP_SECONDS);

      while(!condition.getAsBoolean()) {
         assertTrue(System.currentTimeMillis() < deadline, what);
         Thread.sleep(10);
      }
   }

   /**
    * {@code rows} rows of {@code id, value}, both {@code r} for row {@code r}.
    */
   private static Object[][] data(int rows) {
      Object[][] data = new Object[rows + 1][];
      data[0] = new Object[] { "id", "value" };

      for(int r = 1; r <= rows; r++) {
         data[r] = new Object[] { r, r };
      }

      return data;
   }

   /**
    * A table whose first reader of {@link #STALL_ROW} waits for {@link #stall} and then
    * fails with a lock stall, and whose first reader of {@link #GATE_ROW} waits for
    * {@link #gate}.
    */
   private static class StallingTable extends DefaultTableLens {
      StallingTable(int rows) {
         super(data(rows));
      }

      @Override
      public boolean moreRows(int row) {
         if(row == STALL_ROW && stalling.compareAndSet(false, true)) {
            stalled.complete(Thread.currentThread());
            await(stall);
            throw new LockStallException("t", "t", 1, null);
         }

         if(row == GATE_ROW && gating.compareAndSet(false, true)) {
            parked.complete(Thread.currentThread());
            await(gate);
         }

         return super.moreRows(row);
      }

      private static void await(CountDownLatch latch) {
         try {
            latch.await(CAP_SECONDS, TimeUnit.SECONDS);
         }
         catch(InterruptedException ex) {
            Thread.currentThread().interrupt();
         }
      }

      private final AtomicBoolean stalling = new AtomicBoolean();
      private final AtomicBoolean gating = new AtomicBoolean();
      private final CountDownLatch stall = new CountDownLatch(1);
      private final CountDownLatch gate = new CountDownLatch(1);
      private final CompletableFuture<Thread> stalled = new CompletableFuture<>();
      private final CompletableFuture<Thread> parked = new CompletableFuture<>();
   }

   @TempDir
   File dumpDir;

   private static final int ROWS = 300;
   private static final int STALL_ROW = 150;
   private static final int GATE_ROW = 200;
   private static final int READERS = 3;
   private static final long DURATION_MS = 2000;
   private static final long JOIN_MS = 10000;
   private static final long CAP_SECONDS = 30;
}
