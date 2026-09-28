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
package inetsoft.report.composition.execution.lockcycle;

import inetsoft.report.TableLens;
import inetsoft.report.composition.execution.lockcycle.LockCycleHarness.Sandbox;
import inetsoft.report.composition.execution.lockcycle.LockCycleHarness.Slow;
import inetsoft.report.composition.execution.lockcycle.LockCycleHarness.SlowTable;
import inetsoft.report.lens.FormulaTableLens;
import inetsoft.report.lens.JoinTableLens;
import inetsoft.test.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static inetsoft.report.composition.execution.lockcycle.LockCycleHarness.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77215: a join built by a thread holding the engine lock E, as a worksheet expression
 * column's script reading another table assembly ({@code PriorRiskRating[...]}) builds that
 * assembly's join on the script thread. The join's worker threads must not need E: the
 * builder runs the join's scans itself, and a worker reading a computed formula lens takes
 * no engine lock.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class HolderBuiltJoinCycleTest {
   @BeforeEach
   public void setUp() {
      harness = new LockCycleHarness();
   }

   @AfterEach
   public void tearDown() throws Exception {
      harness.close();
   }

   /**
    * The incident: a guest computes the join inputs' formula rows (as
    * {@code validateDataTypes} does), then builds a hash join over them and reads it. Cycle
    * before the fix: the guest holds E and waits in {@code JoinTable.moreRows}; the
    * JoinThreads' end-of-table probe waits for E in {@code FormulaTableLens.lockForRow}.
    */
   @Test
   public void guestBuildsHashJoinOverComputedFormulas() throws Exception {
      harness.forceHashJoin();
      runGuest(s -> {
         FormulaTableLens left = s.formula(new SlowTable(ROWS, Slow.NONE));
         FormulaTableLens right = s.formula(new SlowTable(ROWS, Slow.NONE));
         drain(left);
         drain(right);
         return join(left, right, 1);
      });
   }

   /**
    * The inputs are not computed before the join and are larger than the 10000-row
    * {@code JoinTable} pre-drain, so the scans past it compute formula rows and need E. Holds
    * without the {@code lockForRow} narrowing: the guest runs the scans itself.
    */
   @Test
   public void guestBuildsHashJoinPastPreDrain() throws Exception {
      harness.forceHashJoin();
      // joined on the unique id column
      runGuest(s -> join(s.formula(new SlowTable(BIG_ROWS, Slow.NONE)),
                         s.formula(new SlowTable(BIG_ROWS, Slow.NONE)), 2));
   }

   /**
    * The incident's topology: the guest builds a hash join whose left input is another hash
    * join over formula inputs, so the outer {@code JoinTable} pre-drain reads the inner join
    * on the guest. The inputs are past the pre-drain, so both joins' scans compute formula
    * rows and need E.
    */
   @Test
   public void guestBuildsNestedHashJoin() throws Exception {
      harness.forceHashJoin();
      // joined on the unique id column, column 2 of the inner join is the left input's id
      runGuest(s -> join(join(s.formula(new SlowTable(BIG_ROWS, Slow.NONE)),
                              s.formula(new SlowTable(BIG_ROWS, Slow.NONE)), 2),
                         s.formula(new SlowTable(BIG_ROWS, Slow.NONE)), 2));
   }

   /**
    * {@code MergeJoinTable} built by a guest, with join keys computed by {@code exec()}.
    * Cycle before the fix: the guest holds E and waits in {@code JoinTable.moreRows}; the
    * JoinThread's sort waits for E in {@code GraalJavaScriptEngine.exec}.
    */
   @Test
   public void guestBuildsMergeJoin() throws Exception {
      runGuest(s -> merge(s.execTable(MERGE_ROWS, Slow.NONE), s.execTable(MERGE_ROWS, Slow.NONE)));
   }

   /**
    * A worker reads the last row of a computed formula lens and probes its end while another
    * thread holds E. Nothing remains to compute, so it must not wait for E.
    */
   @Test
   public void probeComputedFormulaWhileLockHeld() throws Exception {
      Sandbox s = harness.sandbox();
      FormulaTableLens formula = s.formula(new SlowTable(ROWS, Slow.NONE));
      drain(formula);
      int last = formula.getRowCount() - 1;
      Object expected = formula.getObject(last, 3);

      CountDownLatch held = new CountDownLatch(1);
      CountDownLatch release = new CountDownLatch(1);
      Future<Object> holder = harness.submit(() -> {
         s.lock.lock();

         try {
            held.countDown();
            release.await(3 * KNOWN_CAP, TimeUnit.SECONDS);
         }
         finally {
            s.lock.unlock();
         }

         return null;
      });
      assertTrue(held.await(ACTIVE_CAP, TimeUnit.SECONDS), "the holder never took the lock");

      try {
         Future<Object> prober = harness.submit(() -> {
            assertTrue(formula.moreRows(last));
            assertFalse(formula.moreRows(last + 1));
            assertFalse(formula.moreRows(TableLens.EOT));
            return formula.getObject(last, 3);
         });

         assertEquals(expected, harness.await(prober, ACTIVE_CAP, "probing a computed lens"));
      }
      finally {
         release.countDown();
      }

      harness.await(holder, ACTIVE_CAP, "holder");
   }

   /**
    * A scan run by the guest fails: as on a JoinThread, the join completes with the rows so
    * far, and the other side is still scanned. The failing left side is scanned first.
    */
   @Test
   public void guestBuildsHashJoinWithFailingScan() throws Exception {
      harness.forceHashJoin();
      Function<Sandbox, TableLens> build =
         s -> join(new FailingTable(ROWS, FAIL_ROW), new SlowTable(ROWS, Slow.NONE), 1);
      List<String> expected = sorted(harness.await(
         harness.submit(() -> drain(build.apply(harness.control()))), ACTIVE_CAP, "control join"));
      assertTrue(expected.size() > 1, "the control join is empty");

      Sandbox s = harness.sandbox();
      Future<List<List<Object>>> guest = harness.submit(() -> s.asGuest(() -> drain(build.apply(s))));

      assertEquals(expected, sorted(harness.await(guest, ACTIVE_CAP, "guest building the join")));
      assertFalse(s.lock.isLocked());
   }

   private void runGuest(Function<Sandbox, TableLens> build) throws Exception {
      Sandbox control = harness.control();
      List<String> expected = sorted(harness.await(
         harness.submit(() -> drain(build.apply(control))), ACTIVE_CAP, "control join"));
      assertTrue(expected.size() > 2, "the control join is empty");

      Sandbox s = harness.sandbox();
      Future<List<List<Object>>> guest = harness.submit(() -> s.asGuest(() -> drain(build.apply(s))));

      // the order of joined rows depends on the join workers' timing
      assertEquals(expected, sorted(harness.await(guest, ACTIVE_CAP, "guest building the join")));
      assertFalse(s.lock.isLocked());
   }

   private TableLens join(TableLens left, TableLens right, int col) {
      return harness.track(new JoinTableLens(left, right, new int[] {col}, new int[] {col},
                                             JoinTableLens.INNER_JOIN, true));
   }

   /**
    * {@code MergeJoinTable} is package-private and {@code JoinTableLens} picks it only under
    * memory pressure, so build it directly.
    */
   private TableLens merge(TableLens left, TableLens right) {
      try {
         Class<?> cls = Class.forName("inetsoft.report.lens.MergeJoinTable");
         Constructor<?> ctor = cls.getDeclaredConstructor(
            TableLens.class, TableLens.class, int[].class, int[].class, int.class, boolean.class,
            int.class);
         ctor.setAccessible(true);
         return harness.track((TableLens) ctor.newInstance(
            left, right, new int[] {1}, new int[] {1}, JoinTableLens.INNER_JOIN, true,
            Integer.MAX_VALUE));
      }
      catch(ReflectiveOperationException ex) {
         throw new IllegalStateException(ex);
      }
   }

   private static List<String> sorted(List<List<Object>> rows) {
      return rows.stream().map(Object::toString).sorted().collect(Collectors.toList());
   }

   /**
    * A table whose join column fails to read at one row, as a formula input that fails.
    */
   private static final class FailingTable extends SlowTable {
      FailingTable(int rows, int failRow) {
         super(rows, Slow.NONE);
         this.failRow = failRow;
      }

      @Override
      public Object getObject(int r, int c) {
         if(r == failRow && c == 1) {
            throw new IllegalStateException("Test scan failure at row " + r);
         }

         return super.getObject(r, c);
      }

      private final int failRow;
   }

   private static final int ROWS = 120;
   private static final int BIG_ROWS = 12000;
   private static final int FAIL_ROW = 40;
   // the merge join's sort reads the join column many times
   private static final int MERGE_ROWS = 40;
   private LockCycleHarness harness;
}
