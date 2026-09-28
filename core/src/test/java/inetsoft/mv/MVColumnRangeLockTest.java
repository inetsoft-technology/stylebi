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
package inetsoft.mv;

import inetsoft.mv.data.XDynamicTable;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.DateRangeRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for Bug #77154.
 * <p>
 * Every {@code MVCompositeDispatcher} of a parallel MV build shares one {@code MVDef} and its
 * live column instances. While one dispatcher serializes the def ({@code MVDef.write()} ->
 * {@code snapshotColumn()}, which holds {@code synchronized(col)}), the others keep mutating the
 * same columns: {@code XDynamicTable.getObject()} -> {@code DateMVColumn.convert()} updates the
 * date range, and every {@code MVBuilder} resets the number range with
 * {@code setRange(null, null)}. The fix puts the lock for a column's range state on the column
 * itself, so every writer and reader of that state uses the same monitor that
 * {@code snapshotColumn()} holds.
 * <p>
 * The first two tests are deterministic: they hold the column monitor (as
 * {@code snapshotColumn()} does) and assert that the real mutation path blocks on exactly that
 * monitor. The remaining tests are stress tests that run the real paths concurrently.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class MVColumnRangeLockTest {
   private static final String BASE_COLUMN_NAME = "dateCol";
   private static final long DAY = 86_400_000L;
   private static final Method SNAPSHOT_COLUMN = resolveSnapshotColumn();

   private static Method resolveSnapshotColumn() {
      try {
         Method method = MVDef.class.getDeclaredMethod("snapshotColumn", MVColumn.class);
         method.setAccessible(true);
         return method;
      }
      catch(NoSuchMethodException e) {
         throw new ExceptionInInitializerError(e);
      }
   }

   @Test
   void dynamicTableConvertWaitsForTheColumnMonitor() throws Exception {
      // warm up class initialization so the probe thread can only block on the column monitor
      newTable(newYearIntervalColumn(), new Date(0L)).getObject(1, 1);

      DateMVColumn col = newYearIntervalColumn();
      XDynamicTable table = newTable(col, new Date(10 * DAY));

      assertBlocksOnMonitor(col, () -> table.getObject(1, 1));
      assertEquals(new Date(10 * DAY), col.getMin());
      assertEquals(new Date(10 * DAY), col.getMax());
   }

   @Test
   void setRangeWaitsForTheColumnMonitor() throws Exception {
      ColumnRef numRef = new ColumnRef(new AttributeRef("test", "num"));
      numRef.setDataType(XSchema.DOUBLE);
      MVColumn plain = new MVColumn(numRef, false);
      RangeMVColumn range = new RangeMVColumn(new MVColumn(numRef, false), numRef, false);
      DateMVColumn date = newYearIntervalColumn();

      for(MVColumn col : new MVColumn[] { plain, range, date }) {
         col.setRange(1, 2);
         assertBlocksOnMonitor(col, () -> col.setRange(null, null));
         assertNull(col.getOriginalMin(), col.getClassName());
         assertNull(col.getOriginalMax(), col.getClassName());
      }
   }

   @Test
   void snapshotRacingDynamicTableConvertNeverOverflows() throws Throwable {
      final int iterations = 50_000;
      MVColumn base = newBaseColumn();
      ColumnRef rangeRef = newRangeRef();
      DefaultTableLens data = new DefaultTableLens(new Object[][] {
         { BASE_COLUMN_NAME }, { new Date(5 * DAY) } });
      DateMVColumn[] cols = new DateMVColumn[iterations];
      XDynamicTable[] tables = new XDynamicTable[iterations];
      MVDef def = new MVDef();

      for(int i = 0; i < iterations; i++) {
         cols[i] = new DateMVColumn(base, rangeRef, DateRangeRef.YEAR_INTERVAL);
         tables[i] = new XDynamicTable(data, new XDynamicMVColumn[] { cols[i] }, new int[] { 0 });
      }

      CyclicBarrier barrier = new CyclicBarrier(2);
      AtomicReference<Throwable> error = new AtomicReference<>();
      AtomicBoolean stop = new AtomicBoolean();

      Thread writer = new Thread(() -> {
         try {
            for(int i = 0; i < iterations && !stop.get(); i++) {
               barrier.await(10, TimeUnit.SECONDS);
               byte[] bytes = snapshot(def, cols[i]);
               assertTrue(bytes.length > 0);
            }
         }
         catch(Throwable t) {
            error.compareAndSet(null, t);
            stop.set(true);
            barrier.reset();
         }
      }, "mv-77154-snapshot");

      Thread mutator = new Thread(() -> {
         try {
            for(int i = 0; i < iterations && !stop.get(); i++) {
               barrier.await(10, TimeUnit.SECONDS);
               tables[i].getObject(1, 1);
            }
         }
         catch(Throwable t) {
            if(!stop.get()) {
               error.compareAndSet(null, t);
            }

            stop.set(true);
            barrier.reset();
         }
      }, "mv-77154-convert");

      writer.start();
      mutator.start();
      writer.join();
      mutator.join();

      if(error.get() != null) {
         throw new AssertionError(
            "snapshotColumn() raced XDynamicTable.getObject() -> DateMVColumn.convert()",
            error.get());
      }
   }

   @Test
   void snapshotRacingSetRangeResetNeverFails() throws Throwable {
      final long durationMs = 1500;
      ColumnRef numRef = new ColumnRef(new AttributeRef("test", "num"));
      numRef.setDataType(XSchema.DOUBLE);
      MVColumn plain = new MVColumn(numRef, false);
      RangeMVColumn range = new RangeMVColumn(new MVColumn(numRef, false), numRef, false);
      DateMVColumn date = newYearIntervalColumn();
      MVColumn[] cols = { plain, range, date };
      MVDef def = new MVDef();
      AtomicBoolean stop = new AtomicBoolean();
      AtomicInteger failures = new AtomicInteger();
      AtomicReference<Throwable> first = new AtomicReference<>();

      // mirrors resetDateRange()/resetNumRange() setting a range and MVBuilder resetting it
      Thread toggler = new Thread(() -> {
         while(!stop.get()) {
            for(MVColumn col : cols) {
               col.setRange(1, 2);
               col.setRange(null, null);
            }
         }
      }, "mv-77154-set-range");

      toggler.start();
      long end = System.currentTimeMillis() + durationMs;
      long snapshots = 0;

      try {
         while(System.currentTimeMillis() < end) {
            for(MVColumn col : cols) {
               try {
                  snapshot(def, col);
               }
               catch(Throwable t) {
                  failures.incrementAndGet();
                  first.compareAndSet(null, t);
               }

               snapshots++;
            }
         }
      }
      finally {
         stop.set(true);
         toggler.join();
      }

      if(first.get() != null) {
         throw new AssertionError("snapshotColumn() failed " + failures.get() + " of " +
                                     snapshots + " times racing setRange(null, null)",
                                  first.get());
      }
   }

   @Test
   void concurrentConvertNeverLosesTheExtremes() throws Throwable {
      final int trials = 200;
      final int threads = 8;
      final int rows = 1000;
      final long start = 100_000 * DAY;
      MVColumn base = newBaseColumn();
      ColumnRef rangeRef = newRangeRef();
      List<DefaultTableLens> datas = new ArrayList<>();

      // interleaved descending values: every row extends the min of the shared column
      for(int t = 0; t < threads; t++) {
         Object[][] rowsData = new Object[rows + 1][];
         rowsData[0] = new Object[] { BASE_COLUMN_NAME };

         for(int r = 0; r < rows; r++) {
            rowsData[r + 1] = new Object[] { new Date(start - ((long) r * threads + t) * DAY) };
         }

         datas.add(new DefaultTableLens(rowsData));
      }

      Date expectedMin = new Date(start - ((long) (rows - 1) * threads + threads - 1) * DAY);
      Date expectedMax = new Date(start);
      int wrong = 0;

      for(int trial = 0; trial < trials; trial++) {
         DateMVColumn col = new DateMVColumn(base, rangeRef, DateRangeRef.YEAR_INTERVAL);
         CyclicBarrier barrier = new CyclicBarrier(threads);
         AtomicReference<Throwable> error = new AtomicReference<>();
         Thread[] workers = new Thread[threads];

         for(int t = 0; t < threads; t++) {
            XDynamicTable table = new XDynamicTable(
               datas.get(t), new XDynamicMVColumn[] { col }, new int[] { 0 });
            workers[t] = new Thread(() -> {
               try {
                  barrier.await(10, TimeUnit.SECONDS);

                  for(int r = 1; r <= rows; r++) {
                     table.getObject(r, 1);
                  }
               }
               catch(Throwable ex) {
                  error.compareAndSet(null, ex);
               }
            }, "mv-77154-extremes-" + t);
            workers[t].start();
         }

         for(Thread worker : workers) {
            worker.join();
         }

         if(error.get() != null) {
            throw error.get();
         }

         if(!expectedMin.equals(col.getMin()) || !expectedMax.equals(col.getMax())) {
            wrong++;
         }
      }

      assertEquals(0, wrong, "trials with a lost min/max out of " + trials);
   }

   private static byte[] snapshot(MVDef def, MVColumn col) throws Throwable {
      try {
         return (byte[]) SNAPSHOT_COLUMN.invoke(def, col);
      }
      catch(InvocationTargetException e) {
         throw e.getCause();
      }
   }

   /**
    * Hold the monitor of the column (as MVDef.snapshotColumn() does) and assert that the action
    * running on another thread blocks on exactly that monitor, then completes once released.
    */
   private static void assertBlocksOnMonitor(Object monitor, Runnable action) throws Exception {
      AtomicReference<Throwable> error = new AtomicReference<>();
      Thread probe = new Thread(() -> {
         try {
            action.run();
         }
         catch(Throwable t) {
            error.set(t);
         }
      }, "mv-77154-probe");

      synchronized(monitor) {
         probe.start();
         long deadline = System.currentTimeMillis() + 5000;
         boolean blocked = false;

         while(probe.isAlive() && System.currentTimeMillis() < deadline) {
            if(isBlockedOn(probe, monitor)) {
               blocked = true;
               break;
            }

            Thread.sleep(1);
         }

         assertTrue(blocked, monitor.getClass().getSimpleName() + " range state was changed " +
            "without taking the column's own monitor");
      }

      probe.join(5000);
      assertFalse(probe.isAlive(), "probe did not finish after the monitor was released");

      if(error.get() != null) {
         throw new AssertionError(error.get());
      }
   }

   private static boolean isBlockedOn(Thread thread, Object monitor) {
      ThreadInfo info = ManagementFactory.getThreadMXBean().getThreadInfo(thread.getId());

      if(info == null || info.getThreadState() != Thread.State.BLOCKED) {
         return false;
      }

      LockInfo lock = info.getLockInfo();
      return lock != null && lock.getIdentityHashCode() == System.identityHashCode(monitor);
   }

   private static XDynamicTable newTable(DateMVColumn col, Date value) {
      DefaultTableLens data = new DefaultTableLens(new Object[][] {
         { BASE_COLUMN_NAME }, { value } });
      return new XDynamicTable(data, new XDynamicMVColumn[] { col }, new int[] { 0 });
   }

   private static MVColumn newBaseColumn() {
      ColumnRef baseRef = new ColumnRef(new AttributeRef("test", BASE_COLUMN_NAME));
      baseRef.setDataType(XSchema.TIME_INSTANT);
      return new MVColumn(baseRef, true);
   }

   private static ColumnRef newRangeRef() {
      ColumnRef rangeRef = new ColumnRef(new AttributeRef(
         "test", DateRangeRef.getName(BASE_COLUMN_NAME, DateRangeRef.YEAR_INTERVAL)));
      rangeRef.setDataType(XSchema.TIME_INSTANT);
      return rangeRef;
   }

   private static DateMVColumn newYearIntervalColumn() {
      DateMVColumn col = new DateMVColumn(newBaseColumn(), newRangeRef(),
                                          DateRangeRef.YEAR_INTERVAL);
      col.setDimension(true);
      return col;
   }
}
