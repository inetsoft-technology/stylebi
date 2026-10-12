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
package inetsoft.report.filter;

import inetsoft.report.TableLens;
import inetsoft.report.lens.ChainScriptLock;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.uql.table.XSwappableTable;
import inetsoft.util.script.LendableReentrantLock;
import inetsoft.util.stall.StallPolicy;
import inetsoft.util.stall.StallTestSupport;
import inetsoft.util.stall.StallWatchdog;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;

import static inetsoft.util.stall.StallTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * A read of a summary filter concurrent with invalidate() returns the row of a pass, never
 * null or a cell of a disposed table, a pass started before invalidate() never adds to the
 * rows, the grand total or the completion of the next pass, and a reader waiting for the
 * rows of the replaced pass reads the next pass (bug #77364).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@Tag("slow")
public class SummaryFilterInvalidateRaceTest {
   @BeforeEach
   public void setUp() {
      resetGlobalStallState();
      // the production policy: a wait fails only once a cycle is confirmed
      StallPolicy.setOverride(
         new StallPolicy(StallPolicy.Mode.FAIL, 1000, 200, dumpDir, 20, false));
      pool = readerPool();
      expected = drain(summary(new DefaultTableLens(data(ROWS))));
   }

   @AfterEach
   public void tearDown() {
      pool.shutdownNow();
      StallTestSupport.clearOverride();
   }

   /**
    * Reads of a filter that is invalidated and read to the end over and over return the
    * summary row, never null, and never fail on a disposed table (C1).
    */
   @Test
   public void readsDuringInvalidateReturnRow() throws Exception {
      SummaryFilter summary = summary(new DefaultTableLens(data(ROWS)));
      assertEquals(expected, pool.submit(() -> drain(summary)).get(CAP_SECONDS, TimeUnit.SECONDS));

      AtomicBoolean done = new AtomicBoolean();
      AtomicLong invalidations = new AtomicLong();
      Map<String, Integer> results = new ConcurrentHashMap<>();

      Future<?> invalidator = pool.submit(() -> {
         while(!done.get()) {
            summary.invalidate();
            summary.moreRows(TableLens.EOT);
            invalidations.incrementAndGet();
         }
      });
      List<Future<?>> readers = new ArrayList<>();

      for(int t = 0; t < READERS; t++) {
         readers.add(pool.submit(() -> {
            ThreadLocalRandom random = ThreadLocalRandom.current();

            while(!done.get()) {
               int r = 1 + random.nextInt(expected.size() - 1);
               String result;

               try {
                  Object value = summary.getObject(r, 1);
                  result = Objects.equals(expected.get(r).get(1), value) ? "ok"
                     : value == null ? "null" : "wrong";
               }
               catch(Throwable ex) {
                  result = ex.getClass().getSimpleName() + ": " + ex.getMessage();
               }

               results.merge(result, 1, Integer::sum);
            }
         }));
      }

      Thread.sleep(DURATION_MS);
      done.set(true);
      invalidator.get(CAP_SECONDS, TimeUnit.SECONDS);

      for(Future<?> reader : readers) {
         reader.get(CAP_SECONDS, TimeUnit.SECONDS);
      }

      String summaryText = "invalidations=" + invalidations.get() + " results=" + results;
      assertTrue(invalidations.get() > 0, summaryText);
      assertTrue(results.getOrDefault("ok", 0) > 0, summaryText);
      assertEquals(1, results.size(), summaryText);
   }

   /**
    * A pass stopped in the base, invalidate(), the next pass runs to the end, then the old
    * pass goes on: the grand formulas and the rows are those of one pass (D1).
    */
   @Test
   public void supersededPassDoesNotAddToGrandFormulae() throws Exception {
      GatedTable base = new GatedTable(ROWS, GATE_ROW);
      SummaryFilter summary = summary(base);

      try {
         pool.submit(summary::getRowCount).get(CAP_SECONDS, TimeUnit.SECONDS);
         base.awaitParked(0);
         summary.invalidate();
         assertEquals(expected, pool.submit(() -> drain(summary)).get(CAP_SECONDS, TimeUnit.SECONDS));
         assertEquals(TOTAL, summary.getGrandFormulae()[0].getResult());

         base.open(0);
         awaitIdle();
         assertEquals(TOTAL, summary.getGrandFormulae()[0].getResult(),
                      "the grand total once the old pass went on");
         assertEquals(expected, pool.submit(() -> drain(summary)).get(CAP_SECONDS, TimeUnit.SECONDS));
      }
      finally {
         base.openAll();
      }
   }

   /**
    * A pass stopped in the base, invalidate(), and the old pass ends before the next read:
    * the next read still reads the rows of the next pass, the old pass did not complete it
    * (D2).
    */
   @Test
   public void supersededPassDoesNotCompleteNextPass() throws Exception {
      GatedTable base = new GatedTable(ROWS, GATE_ROW);
      SummaryFilter summary = summary(base);

      try {
         pool.submit(summary::getRowCount).get(CAP_SECONDS, TimeUnit.SECONDS);
         base.awaitParked(0);
         summary.invalidate();
         base.open(0);
         awaitIdle();

         assertEquals(expected, pool.submit(() -> drain(summary)).get(CAP_SECONDS, TimeUnit.SECONDS));
      }
      finally {
         base.openAll();
      }
   }

   /**
    * The old pass runs to the end while the next pass is in the base: the table, its grand
    * total row included, and the grand formulas are those of the next pass (D3).
    */
   @Test
   public void passesRunningTogetherKeepTheirOwnRows() throws Exception {
      GatedTable base = new GatedTable(ROWS, GATE_ROW, GATE_ROW + 10);
      SummaryFilter summary = summary(base);

      try {
         pool.submit(summary::getRowCount).get(CAP_SECONDS, TimeUnit.SECONDS);
         Thread first = base.awaitParked(0);
         summary.invalidate();
         pool.submit(summary::getRowCount).get(CAP_SECONDS, TimeUnit.SECONDS);
         base.awaitParked(1);

         base.open(0);
         awaitTrue(() -> !isIn(first, SummaryFilter.class), CAP_SECONDS,
                   "the old pass ends");
         base.open(1);

         assertEquals(expected, pool.submit(() -> drain(summary)).get(CAP_SECONDS, TimeUnit.SECONDS));
         assertEquals(TOTAL, summary.getGrandFormulae()[0].getResult());
      }
      finally {
         base.openAll();
      }
   }

   /**
    * A reader waiting for a row of a pass that invalidate() replaces reads the row of the next
    * pass, while the old pass is still stopped in the base (E1).
    */
   @Test
   public void readerWaitingForReplacedPassReadsNextPass() throws Exception {
      GatedTable base = new GatedTable(ROWS, GATE_ROW);
      SummaryFilter summary = summary(base);

      try {
         pool.submit(summary::getRowCount).get(CAP_SECONDS, TimeUnit.SECONDS);
         base.awaitParked(0);
         CompletableFuture<Thread> readerThread = new CompletableFuture<>();
         Future<Object> reader = pool.submit(() -> {
            readerThread.complete(Thread.currentThread());
            return summary.getObject(1, 1);
         });
         Thread waiter = readerThread.get(CAP_SECONDS, TimeUnit.SECONDS);
         awaitTrue(() -> waiter.getState() == Thread.State.TIMED_WAITING &&
                      isIn(waiter, SummaryFilter.class), CAP_SECONDS, "the reader waits");
         summary.invalidate();

         assertEquals(expected.get(1).get(1), reader.get(WAIT_SECONDS, TimeUnit.SECONDS));
         assertNull(StallWatchdog.getUnreleasedStallReason());
      }
      finally {
         base.openAll();
      }
   }

   /**
    * invalidate() on the thread running a pass itself, as a change event of the base read by
    * a synchronous pass: the read goes on with the next pass, it neither waits forever for
    * the stopped pass nor reads its rows.
    */
   @Test
   public void invalidateInsideSynchronousPassReadsNextPass() throws Exception {
      LendableReentrantLock lock = new LendableReentrantLock();
      InvalidatingTable base = new InvalidatingTable(ROWS, GATE_ROW, lock);
      SummaryFilter summary = summary(base);
      base.filter = summary;

      List<List<Object>> rows = assertTimeoutPreemptively(
         Duration.ofSeconds(CAP_SECONDS), () -> pool.submit(() -> drain(summary)).get());
      assertTrue(base.invalidated.get(), "the base invalidated the filter");
      assertEquals(expected, rows);
   }

   /**
    * A filter read to the end, also after invalidate(), serializes with its rows.
    */
   @Test
   public void serializesRowsOfCurrentPass() throws Exception {
      SummaryFilter summary = summary(new DefaultTableLens(data(ROWS)));
      assertEquals(expected, drain(summary));
      summary.invalidate();
      assertEquals(expected, drain(summary));

      TableLens copy = (TableLens) TestSerializeUtils.serializeAndDeserialize(summary);
      assertEquals(expected, drain(copy));
      assertEquals(expected.size(), copy.getRowCount());
   }

   /**
    * A filter written while its pass runs has no worker once read back: the copy processes
    * the rows again rather than wait forever for the pass.
    */
   @Test
   public void copyOfRunningPassReadsRows() throws Exception {
      GatedTable base = new GatedTable(ROWS, GATE_ROW);
      SummaryFilter summary = summary(base);

      try {
         pool.submit(summary::getRowCount).get(CAP_SECONDS, TimeUnit.SECONDS);
         base.awaitParked(0);
         CompletableFuture<Thread> writerThread = new CompletableFuture<>();
         Future<TableLens> copy = pool.submit(() -> {
            writerThread.complete(Thread.currentThread());
            return serializeAndDeserialize(summary);
         });
         // the state of the pass is written, its rows once the pass completes them
         Thread writer = writerThread.get(CAP_SECONDS, TimeUnit.SECONDS);
         awaitTrue(() -> writer.getState() == Thread.State.TIMED_WAITING &&
                      isIn(writer, XSwappableTable.class), CAP_SECONDS,
                   "the writer waits for the rows");
         base.open(0);
         TableLens read = copy.get(CAP_SECONDS, TimeUnit.SECONDS);

         List<List<Object>> rows = assertTimeoutPreemptively(
            Duration.ofSeconds(WAIT_SECONDS), () -> pool.submit(() -> drain(read)).get());
         assertEquals(expected, rows);
      }
      finally {
         base.openAll();
      }
   }

   /**
    * Write and read back a table as it is, without reading it first.
    */
   private static TableLens serializeAndDeserialize(TableLens table) throws Exception {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();

      try(ObjectOutputStream out = new ObjectOutputStream(bytes)) {
         out.writeObject(table);
      }

      try(ObjectInputStream in =
             new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())))
      {
         return (TableLens) in.readObject();
      }
   }

   private static SummaryFilter summary(TableLens base) {
      return new SummaryFilter(base, new int[] { 0 }, new int[] { 1 }, new SumFormula(),
                               new SumFormula());
   }

   /**
    * Wait until no thread but this one runs code of the filter, i.e. every pass is done.
    */
   private static void awaitIdle() throws Exception {
      awaitTrue(() -> Thread.getAllStackTraces().keySet().stream()
                   .noneMatch(t -> t != Thread.currentThread() && isIn(t, SummaryFilter.class)),
                CAP_SECONDS, "a pass is still running");
      Thread.sleep(IDLE_MS);
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

   /**
    * A table that stops the first worker reading each gate row until the gate is opened.
    * Readers pass. The gates are not written, a copy has none.
    */
   private static class GatedTable extends DefaultTableLens {
      GatedTable(int rows, int... gateRows) {
         super(data(rows));
         gates = new Gate[gateRows.length];

         for(int i = 0; i < gates.length; i++) {
            gates[i] = new Gate(gateRows[i]);
         }
      }

      @Override
      public boolean moreRows(int row) {
         Gate[] gates = this.gates;

         for(int i = 0; gates != null && !isReader() && i < gates.length; i++) {
            Gate gate = gates[i];

            if(gate.row == row && gate.taken.compareAndSet(false, true)) {
               gate.parked.complete(Thread.currentThread());

               try {
                  gate.open.await(CAP_SECONDS, TimeUnit.SECONDS);
               }
               catch(InterruptedException ex) {
                  Thread.currentThread().interrupt();
               }
            }
         }

         return super.moreRows(row);
      }

      Thread awaitParked(int gate) throws Exception {
         return gates[gate].parked.get(CAP_SECONDS, TimeUnit.SECONDS);
      }

      void open(int gate) {
         gates[gate].open.countDown();
      }

      void openAll() {
         for(Gate gate : gates) {
            gate.open.countDown();
         }
      }

      private final transient Gate[] gates;
   }

   private static class Gate {
      Gate(int row) {
         this.row = row;
      }

      final int row;
      final AtomicBoolean taken = new AtomicBoolean();
      final CompletableFuture<Thread> parked = new CompletableFuture<>();
      final CountDownLatch open = new CountDownLatch(1);
   }

   /**
    * A table whose reads take an engine lock that ChainScriptLock finds, so the reader runs
    * the pass itself, and that invalidates the filter once, on its first read of a row.
    */
   private static class InvalidatingTable extends DefaultTableLens
      implements ChainScriptLock.Source
   {
      InvalidatingTable(int rows, int row, LendableReentrantLock lock) {
         super(data(rows));
         this.row = row;
         this.lock = lock;
      }

      @Override
      public Lock getScriptLock() {
         return lock;
      }

      @Override
      public boolean moreRows(int row) {
         if(row == this.row && filter != null && invalidated.compareAndSet(false, true)) {
            assertTrue(lock.isHeldByCurrentThread(), "the pass runs on the reader");
            filter.invalidate();
         }

         return super.moreRows(row);
      }

      private final int row;
      private final LendableReentrantLock lock;
      final AtomicBoolean invalidated = new AtomicBoolean();
      volatile SummaryFilter filter;
   }

   @TempDir
   File dumpDir;
   private ExecutorService pool;
   private List<List<Object>> expected;

   private static final int ROWS = 30;
   private static final int GATE_ROW = 15;
   // the sum of the values 1 to ROWS
   private static final Object TOTAL = 465.0;
   private static final int READERS = 4;
   private static final long DURATION_MS = 3000;
   private static final long CAP_SECONDS = 30;
   private static final long WAIT_SECONDS = 15;
   private static final long IDLE_MS = 200;
}
