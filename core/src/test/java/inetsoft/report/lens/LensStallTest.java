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
import inetsoft.report.internal.table.MergedTable;
import inetsoft.test.*;
import inetsoft.uql.XConstants;
import inetsoft.util.script.JavaScriptEngine;
import inetsoft.util.script.LendableReentrantLock;
import inetsoft.util.stall.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static inetsoft.util.stall.StallTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The waits of the async lenses on their workers are bounded by the lock-stall watchdog
 * (bug #76967), a stall of a worker is rethrown to the reader instead of ending the table,
 * and a slow but progressing worker is left alone.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@Tag("core")
public class LensStallTest {
   @BeforeEach
   public void setUp() {
      resetGlobalStallState();
      StallPolicy.setOverride(new StallPolicy(StallPolicy.Mode.FAIL, 1000, 200, dumpDir));
      pool = readerPool();
   }

   @AfterEach
   public void tearDown() {
      if(gated != null) {
         gated.open();
      }

      pool.shutdownNow();
      StallPolicy.setOverride(null);
   }

   @ParameterizedTest
   @EnumSource(Kind.class)
   public void stalledWorkerFailsTheReader(Kind kind) throws Exception {
      gated = new GatedTable(30);
      Future<List<List<Object>>> reader = pool.submit(() -> drain(build(kind, gated)));

      LockStallException stall = stallIn(failureOf(reader, 15));
      assertEquals(kind.site, stall.getSite());
      assertTrue(stall.getStalledMillis() >= 1000);
      assertNotNull(stall.getDumpPath(), "the stall wrote a thread dump");
   }

   @ParameterizedTest
   @EnumSource(value = Kind.class, names = { "DISTINCT", "SELF_JOIN", "SET" })
   public void workerStallIsNotTheEndOfTheTable(Kind kind) throws Exception {
      LockStallException original = new LockStallException("nested.site", "worker", 1234, null);
      TableLens base = new FailingTable(30, 5, original);
      TableLens lens = pool.submit(() -> build(kind, base)).get(15, TimeUnit.SECONDS);
      Future<List<List<Object>>> reader = pool.submit(() -> drain(lens));

      assertSame(original, stallIn(failureOf(reader, 15)).getCause());

      // the rows so far are not the whole table either
      Future<Integer> count = pool.submit(lens::getRowCount);
      assertSame(original, stallIn(failureOf(count, 15)).getCause(),
                 "getRowCount() rethrows the worker's stall");
      Future<Object> value = pool.submit(() -> lens.getObject(1000, 1));
      assertSame(original, stallIn(failureOf(value, 15)).getCause(),
                 "getObject() past the rows so far rethrows the worker's stall");
   }

   @ParameterizedTest
   @EnumSource(value = Kind.class, names = { "DISTINCT", "SELF_JOIN", "SET" })
   public void wrappedWorkerStallIsNotTheEndOfTheTable(Kind kind) throws Exception {
      LockStallException original = new LockStallException("nested.site", "worker", 1234, null);
      TableLens base = new FailingTable(30, 5, original) {
         @Override
         public void failNow() {
            if(!isReader()) {
               throw new RuntimeException("wrapped", original);
            }
         }
      };
      TableLens lens = pool.submit(() -> build(kind, base)).get(15, TimeUnit.SECONDS);
      Future<List<List<Object>>> reader = pool.submit(() -> drain(lens));

      assertSame(original, stallIn(failureOf(reader, 15)).getCause(),
                 "the reader rethrows the wrapped stall");
      assertSame(original, stallIn(failureOf(pool.submit(lens::getRowCount), 15)).getCause());
   }

   @ParameterizedTest
   @EnumSource(value = Kind.class, names = { "DISTINCT", "SELF_JOIN", "SET" })
   public void wrappedStallOnTheLockHolderIsNotTheEndOfTheTable(Kind kind) throws Exception {
      LockStallException original = new LockStallException("nested.site", "worker", 1234, null);
      TableLens base = new FailingTable(30, 5, original) {
         @Override
         public void failNow() {
            if(!isReader()) {
               throw new RuntimeException("wrapped", original);
            }
         }
      };
      TableLens lens = pool.submit(() -> build(kind, base)).get(15, TimeUnit.SECONDS);
      LendableReentrantLock lock = new LendableReentrantLock();
      // a script lock holder runs the lens on its own (non-reader) thread
      lock.lock();
      JavaScriptEngine.pushHeldScriptLock(lock);

      try {
         LockStallException stall = assertThrows(LockStallException.class, () -> drain(lens));
         assertSame(original, stallIn(stall.getCause()),
                    "the lock holder rethrows the wrapped stall");
         assertSame(original, stallIn(assertThrows(LockStallException.class, lens::getRowCount)
                                         .getCause()), "getRowCount() rethrows the stall");
      }
      finally {
         JavaScriptEngine.popHeldScriptLock();
         lock.unlock();
      }

      // another reader fails at once rather than wait for a stall of its own (the set lens
      // failed in its synchronous MergedTable.addTable, before any data row)
      int row = kind == Kind.SET ? 1 : 1000;
      long start = System.nanoTime();
      Future<Boolean> other = pool.submit(() -> lens.moreRows(row));
      assertSame(original, stallIn(failureOf(other, 15)).getCause());
      // the same stall shows it is the worker's, not one of the second reader; the bound only
      // catches a hang and leaves slack for a loaded machine
      assertTrue(System.nanoTime() - start < TimeUnit.SECONDS.toNanos(5),
                 "rethrown at once, not after a hang of the second reader");
   }

   @Test
   public void crossJoinWorkerStallFailsTheReader() throws Exception {
      LockStallException original = new LockStallException("nested.site", "worker", 1234, null);
      TableLens base = new FailingTable(30, 1, original);
      TableLens lens = pool.submit(() -> build(Kind.CROSS_JOIN, base)).get(15, TimeUnit.SECONDS);
      long start = System.nanoTime();
      Future<List<List<Object>>> reader = pool.submit(() -> drain(lens));

      // the worker's stall is rethrown at once, not after a stall of the reader
      assertSame(original, stallIn(failureOf(reader, 15)).getCause());
      assertTrue(System.nanoTime() - start < TimeUnit.SECONDS.toNanos(5),
                 "rethrown at once, not after a hang of the reader");
      assertSame(original, stallIn(failureOf(pool.submit(lens::getRowCount), 15)).getCause(),
                 "getRowCount() rethrows the worker's stall");
   }

   @ParameterizedTest
   @EnumSource(Kind.class)
   public void slowButProgressingWorkerCompletes(Kind kind) throws Exception {
      List<List<Object>> expected = pool.submit(() -> drain(build(kind, new DefaultTableLens(data(8)))))
         .get(15, TimeUnit.SECONDS);
      Future<List<List<Object>>> reader = pool.submit(() -> drain(build(kind, new SlowTable(8, 300))));

      // the cross join's row order depends on which base finishes first
      assertEquals(sorted(expected), sorted(reader.get(15, TimeUnit.SECONDS)));
   }

   private static List<String> sorted(List<List<Object>> rows) {
      return rows.stream().map(String::valueOf).sorted().collect(Collectors.toList());
   }

   @ParameterizedTest
   @EnumSource(Kind.class)
   public void completedLensRegistersNothing(Kind kind) throws Exception {
      TableLens lens = pool.submit(() -> build(kind, new DefaultTableLens(data(8))))
         .get(15, TimeUnit.SECONDS);
      List<List<Object>> rows = pool.submit(() -> drain(lens)).get(15, TimeUnit.SECONDS);
      long before = WaitRegistry.global().getBeginCount();

      assertEquals(rows, drain(lens));
      assertEquals(before, WaitRegistry.global().getBeginCount());
   }

   private static TableLens build(Kind kind, TableLens base) {
      switch(kind) {
      case DISTINCT:
         // stable, so the hash path runs; the sort path's SortFilter swallows exceptions
         return new DistinctTableLens(base, null, true);
      case SELF_JOIN:
         // the operator needs two different columns, a string never equals a number, so all
         // rows are kept
         SelfJoinTableLens join = new SelfJoinTableLens(base);
         join.addJoin(0, XConstants.NOT_EQUAL_JOIN, 1);
         return join;
      case SET:
         // MergedTable.addTable reads the bases on the reader thread, the worker only visits
         // the merged rows, so the base's behavior is applied in the visitor
         Runnable hook = () -> { };

         if(base instanceof GatedTable) {
            hook = ((GatedTable) base)::pass;
         }
         else if(base instanceof FailingTable) {
            hook = ((FailingTable) base)::failNow;
         }
         else if(base instanceof SlowTable) {
            hook = ((SlowTable) base)::pause;
         }

         return new HookedIntersect(base, new DefaultTableLens(data(3)), hook);
      case CROSS_JOIN:
         return new CrossJoinTableLens(base, new DefaultTableLens(data(2)));
      default:
         throw new IllegalArgumentException(kind.name());
      }
   }

   public enum Kind {
      DISTINCT("DistinctTableLens.moreRows"),
      SELF_JOIN("SelfJoinTableLens.moreRows"),
      SET("SetTableLens.moreRows"),
      CROSS_JOIN("CrossJoinTableLens.moreRows");

      Kind(String site) {
         this.site = site;
      }

      final String site;
   }

   /**
    * An intersect whose worker runs {@code hook} before visiting each merged row.
    */
   private static final class HookedIntersect extends IntersectTableLens {
      HookedIntersect(TableLens left, TableLens right, Runnable hook) {
         super(left, right);
         this.hook = hook;
      }

      @Override
      protected MergedTable.Visitor getVisitor() {
         MergedTable.Visitor visitor = super.getVisitor();
         return row -> {
            hook.run();
            visitor.visit(row);
         };
      }

      private final Runnable hook;
   }

   @TempDir
   File dumpDir;
   private ExecutorService pool;
   private GatedTable gated;
}
