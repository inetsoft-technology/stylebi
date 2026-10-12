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
import inetsoft.report.composition.execution.lockcycle.LockCycleHarness.Gate;
import inetsoft.report.composition.execution.lockcycle.LockCycleHarness.SlowTable;
import inetsoft.report.composition.execution.lockcycle.LockCycleHarness.Started;
import inetsoft.report.composition.execution.TableFilter2;
import inetsoft.report.filter.ColumnMapFilter;
import inetsoft.report.filter.CrossTabFilter;
import inetsoft.report.filter.DefaultTableFilter;
import inetsoft.report.filter.Formula;
import inetsoft.report.filter.SortFilter;
import inetsoft.report.filter.SumFormula;
import inetsoft.report.lens.*;
import inetsoft.test.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;
import java.util.concurrent.Future;
import java.util.function.Function;

import static inetsoft.report.composition.execution.lockcycle.LockCycleHarness.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * Lock cycles between a lens that holds its own monitor while it reads its base, and a
 * condition-filter lock holder of the same sandbox (bug #76960 track B, "R2"). The same lens
 * instance is read by two threads, as a lens graph shared through {@code AssetDataCache} is:
 * T1 reads it directly, holding no lock; T2 reads it through a {@code ConditionFilter2}.
 * Cycle: T1 holds the lens monitor and waits for the engine lock E (for the condition filter
 * or formula below the lens); T2 holds E and waits for the lens monitor. T2 is not at a wait
 * site, so #5531's lending never engages.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class MonitorFirstLensCycleTest {
   @BeforeEach
   public void setUp() {
      harness = new LockCycleHarness();
   }

   @AfterEach
   public void tearDown() throws Exception {
      harness.close();
   }

   /**
    * Bug #76960 B (R2), a monitor-first lens over a filtered formula table, with the gate in
    * the formula's base: T1 parks there holding E. The lens monitor held across the base read
    * was:
    * <ul>
    * <li>SORT: {@code SortFilter.checkInit} → {@code sort()} under {@code synchronized(lock)};</li>
    * <li>UNION_ALL: non-distinct {@code UnionTableLens.moreRows} under {@code synchronized(this)};</li>
    * <li>RANKING: the {@code synchronized RankingTableLens.validate};</li>
    * <li>UNION_DISTINCT, INTERSECT, MINUS: {@code SetTableLens.moreRows} → {@code validate()}
    * → {@code MergedTable.addTable};</li>
    * <li>CROSSTAB: {@code CrossTabFilter.getData} under {@code synchronized(this)}.</li>
    * </ul>
    * UNION_ALL and RANKING hung in this order. Fixed by bug #77874: the non-distinct union
    * reads its bases without its monitor, the other lenses take the chain's engine lock before
    * their monitor. MAX_ROWS is fixed, see {@link #maxRowsOverFilteredFormula()}. CROSSTAB
    * is not run in the shapes with a condition filter over the lens: building that filter
    * reads the crosstab's header, which computes the crosstab on the test thread before T1
    * reads it, so see {@link #scriptHolderOverGatedLens(Kind)}.
    */
   @ParameterizedTest
   @EnumSource(value = Kind.class, names = { "MAX_ROWS", "CROSSTAB" }, mode = EnumSource.Mode.EXCLUDE)
   public void lensOverFilteredFormula(Kind kind) throws Exception {
      // about 5-10 s a kind for the slow base, and without E there is no cycle to provoke
      assumeFalse(POOL, "the cycle needs the engine lock, which a pooled env does not take");
      runShared(g -> s -> build(kind, () -> s.filteredFormula(new SlowTable(ROWS, Slow.EVERYWHERE, g))),
                ACTIVE_CAP);
   }

   /**
    * Bug #77874, the order that forms the R2 cycle for every monitor-first lens kind: the gate
    * sits between the lens and its filtered base, so T1 parks inside the lens monitor without
    * the engine lock E, and T2 populates its condition filter over the lens first
    * ({@code moreRows(EOT)}), taking E before it reads the lens (a header cell read takes no
    * E, so no cycle could form from it). Before the fix every kind hung: T2 held E and was
    * BLOCKED on the lens monitor, T1 held the monitor and waited for E in the inner condition
    * filter. With the fix T1 holds E before the monitor (or, for UNION_ALL, holds no monitor
    * while it reads the base), so T2 waits for E and both complete.
    *
    * <p>With the pool on no condition filter takes E, so there is no cycle to provoke and
    * this only checks the rows; the cycle is exercised with {@code -Dlockcycle.pool=false}.
    */
   @ParameterizedTest
   @EnumSource(value = Kind.class, names = { "MAX_ROWS", "CROSSTAB" }, mode = EnumSource.Mode.EXCLUDE)
   public void lensOverGatedFilteredFormula(Kind kind) throws Exception {
      Gate gate = harness.gate();
      Function<Sandbox, TableLens> build = s -> build(
         kind, () -> new GateFilter(s.filteredFormula(new SlowTable(ROWS, Slow.NONE)), gate));
      Sandbox control = harness.control();
      TableLens controlLens = harness.track(build.apply(control));
      List<List<Object>> expectedLens = harness.await(
         harness.submit(() -> drain(controlLens)), ACTIVE_CAP, "control lens");
      List<List<Object>> expectedOuter = harness.await(
         harness.submit(() -> drain(cf2(controlLens, null))), ACTIVE_CAP, "control filter");
      assertTrue(expectedLens.size() > 2, "control pipeline is empty");

      Sandbox sandbox = harness.sandbox();
      TableLens lens = harness.track(build.apply(sandbox));
      TableLens outer = harness.track(cf2(lens, sandbox.box));

      Started<List<List<Object>>> t1 = harness.startGated(gate, () -> drain(lens));
      assertTrue(gate.awaitEntered(ACTIVE_CAP), "T1 never read the lens's base");
      Started<List<List<Object>>> t2 = harness.start(() -> {
         outer.moreRows(TableLens.EOT);
         return drain(outer);
      });
      releaseAfter(gate, t2, ACTIVE_CAP);

      assertEquals(expectedOuter, harness.await(t2.future, ACTIVE_CAP, "T2, the filter lock holder"));
      assertEquals(expectedLens, harness.await(t1.future, ACTIVE_CAP, "T1, the unlocked lens reader"));
      assertFalse(sandbox.lock.isLocked());
   }

   /**
    * Bug #76960 B (R2) for MAX_ROWS, ordered so that the cycle formed on every run:
    * {@code MaxRowsTableLens.moreRows} read its base under {@code synchronized(rlock)} on every
    * call. T1 drains the lens and parks between the lens and its filtered base, without the
    * engine lock E; T2's condition filter over the lens takes E for its first row and reads
    * the lens. Cycle: T2 held E and was BLOCKED on {@code rlock}; T1 held {@code rlock} and
    * waited for E in the inner condition filter. Fixed by bug #77311: the lens reads its base
    * without the monitor, so T2 reads through while T1 is parked.
    */
   @Test
   public void maxRowsOverFilteredFormula() throws Exception {
      Gate gate = harness.gate();
      Function<Sandbox, TableLens> build = s -> new MaxRowsTableLens(
         new GateFilter(s.filteredFormula(new SlowTable(ROWS, Slow.NONE)), gate), 100000);
      Sandbox control = harness.control();
      TableLens controlLens = harness.track(build.apply(control));
      List<List<Object>> expectedLens = harness.await(
         harness.submit(() -> drain(controlLens)), ACTIVE_CAP, "control lens");
      List<List<Object>> expectedOuter = harness.await(
         harness.submit(() -> drain(cf2(controlLens, null))), ACTIVE_CAP, "control filter");

      Sandbox sandbox = harness.sandbox();
      TableLens lens = harness.track(build.apply(sandbox));
      TableLens outer = harness.track(cf2(lens, sandbox.box));

      Started<List<List<Object>>> t1 = harness.startGated(gate, () -> drain(lens));
      assertTrue(gate.awaitEntered(ACTIVE_CAP), "T1 never read the lens's base");
      Started<List<List<Object>>> t2 = harness.start(() -> drain(outer));
      releaseAfter(gate, t2, ACTIVE_CAP);

      assertEquals(expectedOuter, harness.await(t2.future, ACTIVE_CAP, "T2, the filter lock holder"));
      assertEquals(expectedLens, harness.await(t1.future, ACTIVE_CAP, "T1, the unlocked lens reader"));
      assertFalse(sandbox.lock.isLocked());
   }

   /**
    * Bug #77311, the lock holder above the lens: a worksheet table's result
    * {@code MaxRows(ColumnMapFilter(ConditionFilter2(formula)))} is shared through
    * {@code AssetDataCache} by two mirror queries, and one of them adds an expression column
    * over it ({@code FormulaTableLens}). T1 computes that formula lens: it holds E and parks
    * in its script's read of the shared lens. T2 reads the shared lens past the rows mapped so
    * far, so its condition filter waits for E. T1 then reads the next (loaded) row of the
    * shared lens. Cycle: T1 held E and waited for {@code MaxRowsTableLens.rlock} in
    * {@code moreRows}; T2 held {@code rlock} across the base read and waited for E. The
    * engine lock is only taken off the pool.
    */
   @Test
   public void formulaOverSharedMaxRows() throws Exception {
      assumeFalse(POOL, "the cycle needs the engine lock, which a pooled env does not take");
      Gate gate = harness.gate();
      Sandbox control = harness.control();
      TableLens controlShared = harness.track(sharedMaxRows(control, null));
      TableLens controlFormula = harness.track(control.formula(controlShared, "g", "field['value'] + 1"));
      List<List<Object>> expectedFormula = harness.await(
         harness.submit(() -> drain(controlFormula)), ACTIVE_CAP, "control formula");
      List<List<Object>> expectedShared = harness.await(
         harness.submit(() -> drain(controlShared)), ACTIVE_CAP, "control shared lens");
      assertTrue(expectedShared.size() > PRE_READ + 2, "control pipeline is too short");

      Sandbox sandbox = harness.sandbox();
      TableLens shared = harness.track(sharedMaxRows(sandbox, gate));
      TableLens formula = harness.track(sandbox.formula(shared, "g", "field['value'] + 1"));
      // an earlier reader mapped the first rows, so T1's reads of the shared lens before it
      // takes E never wait for E
      assertTrue(harness.await(harness.submit(() -> shared.moreRows(PRE_READ)), ACTIVE_CAP,
                               "mapping the first rows"));

      Started<List<List<Object>>> t1 = harness.startGated(gate, () -> drain(formula));
      assertTrue(gate.awaitEntered(ACTIVE_CAP), "T1 never read the shared lens in its script");
      assertTrue(sandbox.lock.isLocked(), "T1 does not hold the engine lock in its script");
      Started<List<List<Object>>> t2 = harness.start(() -> drain(new TableFilter2(shared)));

      try {
         awaitParked(t2, ACTIVE_CAP);

         if(!t2.future.isDone()) {
            assertTrue(awaitWaitingOnLock(t2.thread, ACTIVE_CAP),
                       "T2 is not waiting for the engine lock");
         }
      }
      finally {
         gate.release();
      }

      assertEquals(expectedFormula, harness.await(t1.future, ACTIVE_CAP, "T1, the formula over the lens"));
      assertEquals(expectedShared, harness.await(t2.future, ACTIVE_CAP, "T2, the shared lens reader"));
      assertFalse(sandbox.lock.isLocked());
   }

   /**
    * Bug #77874, no condition filter over the lens: T2 is a script thread of the sandbox (a
    * worksheet script reading the shared table by name) holding E, and reads the lens while
    * T1 is parked between the lens and its filtered base. Before the fix every kind hung, T1
    * holding the lens monitor without E and T2 holding E BLOCKED on that monitor; this is the
    * only one of these shapes that reaches the crosstab, whose computation is its row count.
    * With the fix T1 takes E before the monitor (or, for UNION_ALL, reads the base without
    * it), so T2 waits for E and both complete.
    *
    * <p>With the pool on the guest holds a pooled context and the lens reads take no E, so
    * this only checks the rows; the cycle is exercised with {@code -Dlockcycle.pool=false}.
    */
   @ParameterizedTest
   @EnumSource(value = Kind.class, names = "MAX_ROWS", mode = EnumSource.Mode.EXCLUDE)
   public void scriptHolderOverGatedLens(Kind kind) throws Exception {
      Gate gate = harness.gate();
      Function<Sandbox, TableLens> build = s -> build(
         kind, () -> new GateFilter(s.filteredFormula(new SlowTable(ROWS, Slow.NONE)), gate));
      Sandbox control = harness.control();
      TableLens controlLens = harness.track(build.apply(control));
      List<List<Object>> expectedLens = harness.await(
         harness.submit(() -> drain(controlLens)), ACTIVE_CAP, "control lens");
      assertTrue(expectedLens.size() > 2, "control pipeline is empty");

      Sandbox sandbox = harness.sandbox();
      TableLens lens = harness.track(build.apply(sandbox));

      Started<List<List<Object>>> t1 = harness.startGated(gate, () -> drain(lens));
      assertTrue(gate.awaitEntered(ACTIVE_CAP), "T1 never read the lens's base");
      Started<List<List<Object>>> t2 = harness.start(() -> sandbox.asGuest(() -> drain(lens)));
      releaseAfter(gate, t2, ACTIVE_CAP);

      assertEquals(expectedLens, harness.await(t2.future, ACTIVE_CAP, "T2, the script lock holder"));
      assertEquals(expectedLens, harness.await(t1.future, ACTIVE_CAP, "T1, the unlocked lens reader"));
      assertFalse(sandbox.lock.isLocked());
   }

   /**
    * Bug #77874: a condition filter whose row map is completed takes no engine lock when it
    * is read, so {@code ChainScriptLock.find} does not return its lock once the formula lens
    * below is completed too; the lenses that take the chain's lock first then take none over
    * it, and add no wait for the lock under a lock-free reader's monitor. A lens over the
    * completed chain still reads every row.
    */
   @Test
   public void completedFilterTakesNoLock() throws Exception {
      assumeFalse(POOL, "a pooled condition filter never takes the engine lock");
      Sandbox sandbox = harness.sandbox();
      TableLens filtered = harness.track(sandbox.filteredFormula(new SlowTable(FREE_ROWS, Slow.NONE)));
      assertSame(sandbox.lock, ChainScriptLock.find(filtered),
                 "an incomplete filter over a formula lens takes the engine lock");

      List<List<Object>> expected = harness.await(
         harness.submit(() -> drain(filtered)), ACTIVE_CAP, "completing the filter");
      assertNull(ChainScriptLock.find(filtered),
                 "a completed filter over a completed formula lens takes no lock");

      // distinct rows come sorted, every row of the filter is distinct (unique id)
      TableLens distinct = harness.track(new DistinctTableLens(filtered));
      List<List<Object>> rows = harness.await(harness.submit(() -> drain(distinct)), ACTIVE_CAP,
                                              "distinct over the completed filter");
      assertEquals(expected.size(), rows.size());
      assertEquals(new java.util.HashSet<>(expected), new java.util.HashSet<>(rows));
      assertFalse(sandbox.lock.isLocked());
   }

   /**
    * The result of a worksheet table with an expression column and a post condition, as
    * {@code AssetQuery.getRuntimeTableLens} builds it: max rows over the visible columns over
    * the condition filter.
    */
   private static TableLens sharedMaxRows(Sandbox s, Gate gate) {
      TableLens filtered = cf2(s.formula(new SlowTable(ROWS, Slow.NONE, gate)), s.box);
      // the visible columns: group, value, id, not the expression column
      return new MaxRowsTableLens(new ColumnMapFilter(filtered, new int[] {0, 1, 2}), 100000);
   }

   /**
    * Bug #76960 B (R2) at the formula lens itself (architect probe FTL_R2): T1 populates a
    * shared calc-field {@code FormulaTableLens} directly, holding its {@code lock} and
    * waiting for E in {@code exec}; T2's filter over the same formula lens holds E and waits
    * for {@code FormulaTableLens.lock}. Fixed by bug #76935: the formula lens takes E before
    * its own lock.
    */
   @Test
   public void sharedFormulaLens() throws Exception {
      runShared(g -> s -> s.formula(new SlowTable(ROWS, Slow.EVERYWHERE, g), "f", "field['value'] + 1"),
                ACTIVE_CAP);
   }

   /**
    * Bug #76935: over a formula-free base no condition filter takes the engine lock, so the
    * R2 shape has no cycle. Pins the narrowing: if condition filters locked unconditionally
    * again, these would take the engine lock like {@link #lensOverFilteredFormula(Kind)}.
    */
   @ParameterizedTest
   @EnumSource(value = Kind.class, names = { "SORT", "MAX_ROWS", "UNION_ALL", "RANKING" })
   public void lensOverFormulaFreeFilter(Kind kind) throws Exception {
      runShared(g -> s -> build(kind, () -> cf2(new SlowTable(FREE_ROWS, Slow.EVERYWHERE, g), s.box)),
                ACTIVE_CAP);
   }

   /**
    * T1 drains the lens, holding no lock, and parks at the gate at its first base read, which
    * is inside the lens's monitor. T2 then drains a condition filter over the same lens
    * instance, and T1 goes on once T2 has parked. Both must see the rows of the same pipeline
    * built without a lock.
    *
    * @param build given the gate (for the base tables), builds the lens of a sandbox.
    */
   private void runShared(Function<Gate, Function<Sandbox, TableLens>> build, long cap)
      throws Exception
   {
      Gate gate = harness.gate();
      Sandbox control = harness.control();
      TableLens controlLens = harness.track(build.apply(gate).apply(control));
      List<List<Object>> expectedLens = harness.await(
         harness.submit(() -> drain(controlLens)), ACTIVE_CAP, "control lens");
      List<List<Object>> expectedOuter = harness.await(
         harness.submit(() -> drain(cf2(controlLens, null))), ACTIVE_CAP, "control filter");
      assertTrue(expectedLens.size() > 2, "control pipeline is empty");

      Sandbox sandbox = harness.sandbox();
      TableLens lens = harness.track(build.apply(gate).apply(sandbox));
      TableLens outer = harness.track(cf2(lens, sandbox.box));

      Started<List<List<Object>>> t1 = harness.startGated(gate, () -> drain(lens));
      assertTrue(gate.awaitEntered(cap), "T1 never read the lens's base");
      Started<List<List<Object>>> t2 = harness.start(() -> drain(outer));
      releaseAfter(gate, t2, cap);

      assertEquals(expectedOuter, harness.await(t2.future, cap, "T2, the filter lock holder"));
      assertEquals(expectedLens, harness.await(t1.future, cap, "T1, the unlocked lens reader"));
      assertFalse(sandbox.lock.isLocked());
   }

   private TableLens build(Kind kind, java.util.function.Supplier<TableLens> base) {
      switch(kind) {
      case SORT:
         return new SortFilter(base.get(), new int[] {1});
      case MAX_ROWS:
         return new MaxRowsTableLens(base.get(), 100000);
      case UNION_ALL:
         UnionTableLens union = new UnionTableLens(base.get(), base.get());
         union.setDistinct(false);
         return union;
      case RANKING:
         RankingTableLens ranking = new RankingTableLens(base.get());
         ranking.setRankingColumn(1);
         ranking.setRankingN(10);
         return ranking;
      case UNION_DISTINCT:
         return new UnionTableLens(base.get(), base.get());
      case INTERSECT:
         return new IntersectTableLens(base.get(), base.get());
      case MINUS:
         // only the first rows on the right, so the difference is not empty
         return new MinusTableLens(base.get(), new MaxRowsTableLens(base.get(), 10));
      case CROSSTAB:
         return new CrossTabFilter(base.get(), new int[] {0}, new int[0], new int[] {1},
                                   new Formula[] {new SumFormula()});
      default:
         throw new IllegalArgumentException(kind.name());
      }
   }

   /**
    * Passes its base through; the owner of the gate parks at its first data row
    * {@code moreRows}, before the condition filter below takes the engine lock.
    */
   private static final class GateFilter extends DefaultTableFilter {
      GateFilter(TableLens table, Gate gate) {
         super(table);
         this.gate = gate;
      }

      @Override
      public boolean moreRows(int row) {
         if(row >= 1) {
            gate.onRead();
         }

         return super.moreRows(row);
      }

      private final Gate gate;
   }

   public enum Kind {
      SORT, MAX_ROWS, UNION_ALL, RANKING, UNION_DISTINCT, INTERSECT, MINUS, CROSSTAB
   }

   private static final int ROWS = 300;
   // rows of the shared lens mapped before the #77311 case starts, past T1's first read-ahead
   private static final int PRE_READ = 50;
   // no cycle to provoke, so fewer rows keep the default suite fast
   private static final int FREE_ROWS = 100;
   private LockCycleHarness harness;
}
