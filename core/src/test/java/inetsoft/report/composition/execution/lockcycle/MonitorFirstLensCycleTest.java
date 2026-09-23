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
import inetsoft.report.filter.SortFilter;
import inetsoft.report.lens.*;
import inetsoft.test.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
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
    * Bug #76960 B (R2), a monitor-first lens over a filtered formula table. The lens monitor
    * held across the base read is:
    * <ul>
    * <li>SORT: {@code SortFilter.checkInit} → {@code sort()} under {@code synchronized(lock)};</li>
    * <li>MAX_ROWS: {@code MaxRowsTableLens.moreRows} under {@code synchronized(rlock)}, on every
    * call;</li>
    * <li>UNION_ALL: non-distinct {@code UnionTableLens.moreRows} under {@code synchronized(this)};</li>
    * <li>RANKING: the {@code synchronized RankingTableLens.validate}.</li>
    * </ul>
    */
   @ParameterizedTest
   @EnumSource(Kind.class)
   @Tag("known-deadlock")
   @EnabledIfSystemProperty(named = "lockcycle.known", matches = "true")
   public void lensOverFilteredFormula(Kind kind) throws Exception {
      runShared(g -> s -> build(kind, () -> s.filteredFormula(new SlowTable(ROWS, Slow.EVERYWHERE, g))),
                KNOWN_CAP);
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
    * again, these would hang like {@link #lensOverFilteredFormula(Kind)}.
    */
   @ParameterizedTest
   @EnumSource(Kind.class)
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
      default:
         throw new IllegalArgumentException(kind.name());
      }
   }

   public enum Kind {
      SORT, MAX_ROWS, UNION_ALL, RANKING
   }

   private static final int ROWS = 300;
   // no cycle to provoke, so fewer rows keep the default suite fast
   private static final int FREE_ROWS = 100;
   private LockCycleHarness harness;
}
