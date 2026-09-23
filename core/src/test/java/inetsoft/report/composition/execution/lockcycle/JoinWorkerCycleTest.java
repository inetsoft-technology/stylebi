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
import inetsoft.report.filter.DefaultTableFilter;
import inetsoft.report.lens.*;
import inetsoft.report.composition.execution.lockcycle.LockCycleHarness.Sandbox;
import inetsoft.report.composition.execution.lockcycle.LockCycleHarness.Slow;
import inetsoft.report.composition.execution.lockcycle.LockCycleHarness.SlowTable;
import inetsoft.test.*;
import inetsoft.uql.XConstants;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;

import static inetsoft.report.composition.execution.lockcycle.LockCycleHarness.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Lock cycles between a condition-filter lock holder and the worker threads of the join
 * lenses (bug #76960 track A). The holder, an outer {@code ConditionFilter2} over the join,
 * holds the sandbox's engine lock while it waits for rows that the join's workers produce,
 * and the workers need that same lock: to read an input condition filter, or to run a script.
 * {@code #5531}'s lending covers only the Summary/Distinct/SelfJoin/Set workers, not these.
 *
 * <p>Each case builds the pipeline on a harness thread holding no lock (query build time),
 * then drains the outer filter on another harness thread, and compares the rows with the same
 * pipeline built without a lock.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class JoinWorkerCycleTest {
   @BeforeEach
   public void setUp() {
      harness = new LockCycleHarness();
   }

   @AfterEach
   public void tearDown() throws Exception {
      harness.close();
   }

   /**
    * Bug #76960 A1, first touch. The holder is the first to read the cross join, so
    * {@code CrossJoinTableLens.validate()} starts both {@code WaitingThread}s under the lock.
    * Cycle: holder holds E and waits in {@code CrossJoinTableLens.moreRows}; each
    * {@code WaitingThread} calls {@code moreRows} on its input {@code ConditionFilter2}, which
    * waits for E.
    */
   @Test
   @Tag("known-deadlock")
   @EnabledIfSystemProperty(named = "lockcycle.known", matches = "true")
   public void crossJoinFirstTouch() throws Exception {
      runHolder(s -> cross(s, false), Start.FIRST_TOUCH, KNOWN_CAP);
   }

   /**
    * Bug #76960 A1, build-time ordering. An unlocked {@code getRowCount()}
    * ({@code AssetQuery.validateDataTypes}) starts the {@code WaitingThread}s, then the holder
    * arrives while they still have input rows to read (each worker {@code moreRows} costs
    * 150 ms to widen the race). Same cycle as {@link #crossJoinFirstTouch()}.
    */
   @Test
   @Tag("known-deadlock")
   @EnabledIfSystemProperty(named = "lockcycle.known", matches = "true")
   public void crossJoinBuildTime() throws Exception {
      runHolder(s -> cross(s, true), Start.GET_ROW_COUNT, KNOWN_CAP);
   }

   /**
    * Bug #76960 A2. The {@code HashJoinTable} constructor starts two {@code JoinThread}s,
    * each scanning its input row by row. Cycle: holder holds E and waits in
    * {@code XSwappableTable.moreRows}; the JoinThreads wait for E in the input
    * {@code ConditionFilter2.moreRows}/{@code getObject}.
    */
   @Test
   @Tag("known-deadlock")
   @EnabledIfSystemProperty(named = "lockcycle.known", matches = "true")
   public void hashJoinFilteredInputs() throws Exception {
      harness.forceHashJoin();
      runHolder(s -> hash(s.filteredFormula(new SlowTable(ROWS, Slow.WORKERS)),
                          s.filteredFormula(new SlowTable(ROWS, Slow.WORKERS)), s, false),
                Start.BUILT, KNOWN_CAP);
   }

   /**
    * Bug #76960 A2, script join key. No condition filter below the join; the join key is
    * computed by {@code exec()} on the JoinThreads, and the holder is the joined table's own
    * condition filter over its expression column ({@code CF2(FormulaTableLens(join))}). Cycle:
    * holder holds E and waits in {@code XSwappableTable.moreRows}; the JoinThreads wait for E
    * in {@code GraalJavaScriptEngine.exec}.
    */
   @Test
   @Tag("known-deadlock")
   @EnabledIfSystemProperty(named = "lockcycle.known", matches = "true")
   public void hashJoinExecKey() throws Exception {
      harness.forceHashJoin();
      runHolder(s -> hash(s.execTable(ROWS, Slow.WORKERS), s.execTable(ROWS, Slow.WORKERS), s, true),
                Start.BUILT, KNOWN_CAP);
   }

   /**
    * Bug #76960 A2 past the pre-drain (refutation condition M1). The inputs are formula
    * lenses of 12000 rows with no condition filter, so rows up to the 10000-row
    * {@code JoinTable} pre-drain are computed on the building thread, and the JoinThreads
    * compute the rest themselves, running the formula. This is a genuine lock need, not a
    * condition filter re-locking computed rows. Cycle: holder holds E and waits in
    * {@code XSwappableTable.moreRows}; the JoinThreads wait for E in
    * {@code FormulaTableLens.moreRows} → {@code exec}.
    */
   @Test
   @Tag("known-deadlock")
   @EnabledIfSystemProperty(named = "lockcycle.known", matches = "true")
   public void hashJoinPastPreDrain() throws Exception {
      harness.forceHashJoin();
      // joined on the unique id column
      runHolder(s -> hash(s.formula(new SlowTable(BIG_ROWS, Slow.WORKERS_PAST_PREDRAIN)),
                          s.formula(new SlowTable(BIG_ROWS, Slow.WORKERS_PAST_PREDRAIN)), 2, s, false),
                Start.BUILT, KNOWN_CAP);
   }

   /**
    * Bug #76960 A3. {@code MergeJoinTable} (chosen by {@code JoinTableLens} under memory
    * pressure) starts one {@code JoinThread} that sorts both inputs. Cycle: holder holds E and
    * waits in {@code XSwappableTable.moreRows}; the JoinThread waits for E in the input
    * {@code ConditionFilter2} read by {@code SortFilter.sort}.
    */
   @Test
   @Tag("known-deadlock")
   @EnabledIfSystemProperty(named = "lockcycle.known", matches = "true")
   public void mergeJoinFilteredInputs() throws Exception {
      runHolder(s -> merge(s.filteredFormula(new SlowTable(MERGE_ROWS, Slow.WORKERS)),
                           s.filteredFormula(new SlowTable(MERGE_ROWS, Slow.WORKERS)), s),
                Start.BUILT, KNOWN_CAP);
   }

   /**
    * Bug #76960 A3, script join key. Cycle: holder holds E and waits in
    * {@code XSwappableTable.moreRows}; the JoinThread waits for E in
    * {@code GraalJavaScriptEngine.exec}.
    */
   @Test
   @Tag("known-deadlock")
   @EnabledIfSystemProperty(named = "lockcycle.known", matches = "true")
   public void mergeJoinExecKey() throws Exception {
      runHolder(s -> merge(s.execTable(MERGE_ROWS, Slow.WORKERS), s.execTable(MERGE_ROWS, Slow.WORKERS), s),
                Start.BUILT, KNOWN_CAP);
   }

   /**
    * Bug #76960 A4 (and #76938's lending): a non-equi join, {@code SelfJoinTableLens} over a
    * cross join of filtered inputs. {@code SelfJoinOperator.initDataComparer} completes the
    * cross join at build time, and the holder lends the lock to the self-join worker, so this
    * completes on main.
    */
   @Test
   public void selfJoinOverCrossJoinBuildTime() throws Exception {
      runHolder(s -> {
         TableLens cj = new CrossJoinTableLens(s.filteredFormula(new SlowTable(30, Slow.NONE)),
                                               s.filteredFormula(new SlowTable(30, Slow.NONE)));
         SelfJoinTableLens sj = new SelfJoinTableLens(cj);
         // left value >= right value
         sj.addJoin(1, XConstants.GREATER_EQUAL_JOIN, 5);
         return pipeline(harness.track(sj), s, false);
      }, Start.GET_ROW_COUNT, ACTIVE_CAP);
   }

   /**
    * Bug #76935: with formula-free inputs no condition filter in the join pipeline takes the
    * engine lock, so the #76960 A2 shape has no cycle. Pins the narrowing: if condition
    * filters locked unconditionally again, this would hang like {@link #hashJoinFilteredInputs()}.
    */
   @Test
   public void hashJoinFormulaFreeInputs() throws Exception {
      harness.forceHashJoin();
      runHolder(s -> hash(cf2(new SlowTable(ROWS, Slow.WORKERS), s.box),
                          cf2(new SlowTable(ROWS, Slow.WORKERS), s.box), s, false),
                Start.BUILT, ACTIVE_CAP);
   }

   private void runHolder(Function<Sandbox, Pipeline> build, Start start, long cap)
      throws Exception
   {
      Sandbox control = harness.control();
      List<List<Object>> expected = harness.await(harness.submit(() -> {
         Pipeline pipeline = build.apply(control);
         return drain(pipeline.outer);
      }), ACTIVE_CAP, "control pipeline");
      assertTrue(expected.size() > 2, "control pipeline is empty");

      Sandbox sandbox = harness.sandbox();
      Pipeline pipeline = harness.await(harness.submit(() -> build.apply(sandbox)), cap, "build");

      if(start == Start.GET_ROW_COUNT) {
         harness.await(harness.submit(() -> pipeline.lens.getRowCount()), cap, "unlocked getRowCount()");
      }

      Future<List<List<Object>>> holder = harness.submit(() -> {
         if(start == Start.FIRST_TOUCH) {
            assertTrue(pipeline.outer.moreRows(1));
         }

         return drain(pipeline.outer);
      });

      List<List<Object>> actual = harness.await(holder, cap, "lock holder draining the outer filter");
      // the order of joined rows depends on the join workers' timing
      assertEquals(sorted(expected), sorted(actual));
      assertFalse(sandbox.lock.isLocked());
   }

   private static List<String> sorted(List<List<Object>> rows) {
      return rows.stream().map(Object::toString).sorted().collect(Collectors.toList());
   }

   private Pipeline cross(Sandbox s, boolean widened) {
      TableLens left = s.filteredFormula(new SlowTable(ROWS, Slow.WORKERS));
      TableLens right = s.filteredFormula(new SlowTable(ROWS, Slow.WORKERS));

      if(widened) {
         left = new SlowMoreRows(left);
         right = new SlowMoreRows(right);
      }

      return pipeline(harness.track(new CrossJoinTableLens(left, right)), s, false);
   }

   private Pipeline hash(TableLens left, TableLens right, Sandbox s, boolean formulaHolder) {
      return hash(left, right, 1, s, formulaHolder);
   }

   private Pipeline hash(TableLens left, TableLens right, int col, Sandbox s,
                         boolean formulaHolder)
   {
      JoinTableLens join = new JoinTableLens(left, right, new int[] {col}, new int[] {col},
                                             JoinTableLens.INNER_JOIN, true);
      return pipeline(harness.track(join), s, formulaHolder);
   }

   /**
    * {@code MergeJoinTable} is package-private and {@code JoinTableLens} picks it only under
    * memory pressure, so build it directly. It is not a {@code BinaryTableFilter}, so the
    * holder is the formula-backed filter of the joined table.
    */
   private Pipeline merge(TableLens left, TableLens right, Sandbox s) {
      try {
         Class<?> cls = Class.forName("inetsoft.report.lens.MergeJoinTable");
         Constructor<?> ctor = cls.getDeclaredConstructor(
            TableLens.class, TableLens.class, int[].class, int[].class, int.class, boolean.class,
            int.class);
         ctor.setAccessible(true);
         TableLens join = (TableLens) ctor.newInstance(left, right, new int[] {1}, new int[] {1},
                                                       JoinTableLens.INNER_JOIN, true,
                                                       Integer.MAX_VALUE);
         return pipeline(harness.track(join), s, true);
      }
      catch(ReflectiveOperationException ex) {
         throw new IllegalStateException(ex);
      }
   }

   /**
    * @param formulaHolder put the joined table's expression column between the join and its
    *                      condition filter, as {@code AssetQuery.getRuntimeTableLens} does.
    */
   private Pipeline pipeline(TableLens lens, Sandbox s, boolean formulaHolder) {
      Pipeline pipeline = new Pipeline();
      pipeline.lens = lens;
      pipeline.outer = harness.track(cf2(formulaHolder ? s.formula(lens) : lens, s.box));
      return pipeline;
   }

   private enum Start {
      /** The join's workers were started by its constructor. */
      BUILT,
      /** An unlocked {@code getRowCount()} touches the join before the holder. */
      GET_ROW_COUNT,
      /** The holder is the first to touch the join. */
      FIRST_TOUCH
   }

   private static final class Pipeline {
      TableLens lens;
      TableLens outer;
   }

   /**
    * Widens a race: each {@code moreRows} on a lens worker costs 150 ms.
    */
   private static final class SlowMoreRows extends DefaultTableFilter {
      SlowMoreRows(TableLens table) {
         super(table);
      }

      @Override
      public boolean moreRows(int row) {
         if(!isHarnessThread()) {
            try {
               Thread.sleep(150);
            }
            catch(InterruptedException ex) {
               Thread.currentThread().interrupt();
            }
         }

         return super.moreRows(row);
      }
   }

   private static final int ROWS = 120;
   private static final int BIG_ROWS = 12000;
   // the merge join's sort reads the slow column many times
   private static final int MERGE_ROWS = 40;
   private LockCycleHarness harness;
}
