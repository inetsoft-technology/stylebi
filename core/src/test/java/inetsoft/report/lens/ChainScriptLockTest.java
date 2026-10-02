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
import inetsoft.report.composition.execution.lockcycle.LockCycleHarness;
import inetsoft.report.composition.execution.lockcycle.LockCycleHarness.Sandbox;
import inetsoft.report.composition.execution.lockcycle.LockCycleHarness.Slow;
import inetsoft.report.composition.execution.lockcycle.LockCycleHarness.SlowTable;
import inetsoft.test.*;
import inetsoft.util.script.graal.pool.PoolConfig;
import inetsoft.util.script.graal.pool.WorksheetScriptEnv;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static inetsoft.report.composition.execution.lockcycle.LockCycleHarness.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * Bug #77223: the engine lock {@link ChainScriptLock#find} reports for a table chain, which a
 * distinct or summary lens takes before its monitor on the first read. It must be the lock
 * reading the chain would take, and {@code null} wherever the lens must keep its worker.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class ChainScriptLockTest {
   @BeforeEach
   public void setUp() {
      harness = new LockCycleHarness();
   }

   @AfterEach
   public void tearDown() throws Exception {
      harness.close();
   }

   /**
    * A formula lens with rows left to compute takes its engine's lock, through any lens above.
    */
   @Test
   public void formulaWithRowsToComputeReportsItsLock() {
      assumeFalse(POOL, "a pooled env has no execution lock");
      Sandbox s = harness.sandbox();
      FormulaTableLens formula = s.formula(new SlowTable(ROWS, Slow.NONE));

      assertSame(s.lock, ChainScriptLock.find(formula));
      assertSame(s.lock, ChainScriptLock.find(harness.track(new DistinctTableLens(
         new MaxRowsTableLens(formula, 100000)))));
   }

   /**
    * A condition filter that takes its sandbox's lock, over a formula lens, reports it.
    */
   @Test
   public void lockTakingConditionFilterReportsItsLock() {
      assumeFalse(POOL, "a pooled env has no execution lock");
      Sandbox s = harness.sandbox();

      assertSame(s.lock, ChainScriptLock.find(s.filteredFormula(new SlowTable(ROWS, Slow.NONE))));
   }

   /**
    * Review round 1, F1 (R12): a formula lens whose rows are all computed takes no lock
    * (#77215), so the chain keeps main's worker path.
    */
   @Test
   public void computedFormulaReportsNoLock() throws Exception {
      Sandbox s = harness.sandbox();
      FormulaTableLens formula = s.formula(new SlowTable(ROWS, Slow.NONE));
      harness.await(harness.submit(() -> drain(formula)), ACTIVE_CAP, "computing the formula");

      assertNull(ChainScriptLock.find(formula));
   }

   /**
    * Pooled script contexts have no execution lock.
    */
   @Test
   public void pooledFormulaReportsNoLock() {
      WorksheetScriptEnv env = new WorksheetScriptEnv(PoolConfig.defaults());
      env.init();

      try {
         FormulaTableLens formula = new FormulaTableLens(new SlowTable(ROWS, Slow.NONE),
            new String[] { "f1" }, new String[] { "1" }, env, null);

         assertNull(ChainScriptLock.find(formula));
      }
      finally {
         env.retire();
      }
   }

   /**
    * R1: a condition filter over a plain base takes no lock, so it reports none.
    */
   @Test
   public void lockFreeConditionFilterReportsNoLock() {
      Sandbox s = harness.sandbox();

      assertNull(ChainScriptLock.find(cf2(new SlowTable(ROWS, Slow.NONE), s.box)));
   }

   /**
    * R7: the lock of a formula lens below a join is not reported; the join's threads read it
    * and are never lent the lock (#77016).
    */
   @Test
   public void lockBelowJoinIsNotReported() {
      Sandbox s = harness.sandbox();
      TableLens plain = new SlowTable(ROWS, Slow.NONE);

      assertNull(ChainScriptLock.find(harness.track(new DistinctTableLens(harness.track(
         new CrossJoinTableLens(s.formula(new SlowTable(ROWS, Slow.NONE)), plain))))));
      assertNull(ChainScriptLock.find(harness.track(new JoinTableLens(
         s.formula(new SlowTable(ROWS, Slow.NONE)), plain, new int[] { 2 }, new int[] { 2 }))));
   }

   /**
    * The locks of two engines (a chain over another sandbox's lenses, #76964) are not
    * reported: no single lock covers reading the chain.
    */
   @Test
   public void locksOfTwoEnginesAreNotReported() {
      Sandbox s1 = harness.sandbox();
      Sandbox s2 = harness.sandbox();

      assertNull(ChainScriptLock.find(harness.track(new UnionTableLens(
         s1.formula(new SlowTable(ROWS, Slow.NONE)), s2.formula(new SlowTable(ROWS, Slow.NONE))))));
   }

   private static final int ROWS = 20;
   private LockCycleHarness harness;
}
