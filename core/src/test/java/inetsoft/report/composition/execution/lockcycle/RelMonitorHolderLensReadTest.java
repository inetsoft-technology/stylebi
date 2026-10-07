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
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.report.lens.FormulaTableLens;
import inetsoft.util.script.ScriptEnv;
import inetsoft.util.script.graal.pool.PoolConfig;
import inetsoft.util.script.graal.pool.WorksheetScriptEnv;
import inetsoft.test.*;
import inetsoft.util.stall.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.util.List;
import java.util.concurrent.*;

import static inetsoft.report.composition.execution.lockcycle.LockCycleHarness.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Reliability probe (bug #77123, seat conc): FormulaLensLockStallTest's FTL_R2 shape, a
 * thread that holds a base table's monitor and reads the formula lens while the lens owner
 * is reading that base, under the shipped stall rule (fail mode, which since Feature #77123
 * fails a wait only once the watchdog confirms a wait-for cycle or a JVM deadlock, never on
 * the timeout alone) with a 30 s limit, instead of the suite's FAIL/1 s with failOnTimeout. A
 * slow but live read is therefore never failed here, while a return of the F1 deadlock fails
 * one of the two with a LockStallException (a confirmed cycle) rather than hanging. Pool off,
 * the owner loads its base rows before it takes the lens lock (#5576), so both complete; pool
 * on it deadlocked (finding F1) until the pooled batch loaded its base rows before the lens
 * lock too (#5857). Run it both ways:
 * <pre>
 * ./mvnw -o test -pl core -Dtest=RelMonitorHolderLensReadTest
 * ./mvnw -o test -pl core -Dtest=RelMonitorHolderLensReadTest -Dlockcycle.pool=true
 * </pre>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class RelMonitorHolderLensReadTest {
   @BeforeEach
   public void setUp() {
      StallTestSupport.resetGlobalStallState();
      StallPolicy.setOverride(new StallPolicy(StallPolicy.Mode.FAIL, 30000, 500, dumpDir,
                                              StallPolicy.DEFAULT_MAX_DUMPS, false));
      harness = new LockCycleHarness();
   }

   @AfterEach
   public void tearDown() throws Exception {
      harness.close();

      if(pooled != null) {
         pooled.retire();
         pooled = null;
      }

      StallTestSupport.clearOverride();
   }

   @Test
   public void poolOffMonitorHolderReadingTheLensAndTheLensOwnerBothComplete() throws Exception {
      Assumptions.assumeFalse(POOL, "the harness env is pooled with -Dlockcycle.pool=true");
      // about a minute (a 4-configuration Spring context and the 30 s limit), so not in CI
      Assumptions.assumeTrue(Boolean.getBoolean("rel.long"), "long: -Drel.long=true");
      monitorHolderReadingTheLensAndTheLensOwnerBothComplete(false, 40, 5, 30);
   }

   /**
    * Finding F1 (seat conc): pool on, the lens owner held the formula lens lock (its batch)
    * while it read the base, so it blocked on the base monitor the reader holds, and the
    * reader waited for the lens lock: a JVM-detected deadlock that ALERT mode reports but never
    * breaks, and the shipped fail rule breaks with a LockStallException for one victim.
    * The lens runs on a pooled env whatever -Dlockcycle.pool says.
    */
   @Test
   public void poolOnMonitorHolderReadingTheLensAndTheLensOwnerBothComplete() throws Exception {
      monitorHolderReadingTheLensAndTheLensOwnerBothComplete(true, 40, 5, 30);
   }

   /**
    * F1 in a later batch of a sequential read: the owner's batches double (10, 20, 40 rows),
    * so the gate at row 60 is read by the third batch, past the pool-off look-ahead. Its base
    * rows must be loaded before the lens lock as well, not only the first batch's.
    */
   @Test
   public void poolOnMonitorHolderReadingTheLensDuringADoubledBatchAndTheLensOwnerBothComplete()
      throws Exception
   {
      monitorHolderReadingTheLensAndTheLensOwnerBothComplete(true, 200, 60, 150);
   }

   /**
    * @param pool whether the lens runs on a pooled worksheet env or on the harness's
    *             locking env.
    * @param n the base rows.
    * @param gate the first base row read under the base monitor.
    * @param read the row the monitor holder reads from the lens.
    */
   private void monitorHolderReadingTheLensAndTheLensOwnerBothComplete(boolean pool, int n,
                                                                       int gate, int read)
      throws Exception
   {
      ScriptEnv env = env(pool);
      List<List<Object>> expected = harness.await(
         harness.submit(() -> drain(formula(new DefaultTableLens(rows(n)), env))),
         ACTIVE_CAP, "control");
      MonitorTable base = new MonitorTable(rows(n), gate);
      FormulaTableLens lens = harness.track(formula(base, env));
      CountDownLatch waiterHasMonitor = new CountDownLatch(1);

      Future<String> waiter = harness.submit(() -> {
         synchronized(base.monitor) {
            waiterHasMonitor.countDown();
            assertTrue(base.entered.await(10, TimeUnit.SECONDS),
                       "the owner never reached row " + gate);
            lens.moreRows(read);
            return "completed";
         }
      });
      assertTrue(waiterHasMonitor.await(10, TimeUnit.SECONDS));
      Future<List<List<Object>>> owner = harness.submit(() -> drain(lens));

      // 60 s: well past the 30 s limit, so a watchdog break (a confirmed cycle) would show
      assertEquals("completed", harness.await(waiter, 60, "monitor holder (pool=" + pool + ")"));
      assertEquals(expected, harness.await(owner, 60, "lens owner (pool=" + pool + ")"));
   }

   private ScriptEnv env(boolean pool) {
      if(!pool) {
         return harness.control().env;
      }

      pooled = new WorksheetScriptEnv(PoolConfig.defaults());
      pooled.init();
      return pooled;
   }

   private static FormulaTableLens formula(TableLens base, ScriptEnv env) {
      return new FormulaTableLens(base, new String[] {"f1"}, new String[] {"1"}, env, null);
   }

   private static Object[][] rows(int n) {
      Object[][] data = new Object[n + 1][];
      data[0] = new Object[] { "key", "value" };

      for(int i = 1; i <= n; i++) {
         data[i] = new Object[] { "k" + (i % 3), i };
      }

      return data;
   }

   /**
    * A base whose rows from {@code gate} on are read under {@link #monitor}.
    */
   private static final class MonitorTable extends DefaultTableLens {
      MonitorTable(Object[][] data, int gate) {
         super(data);
         this.gate = gate;
      }

      @Override
      public boolean moreRows(int row) {
         if(row >= gate && row != TableLens.EOT) {
            entered.countDown();

            synchronized(monitor) {
               return super.moreRows(row);
            }
         }

         return super.moreRows(row);
      }

      final Object monitor = new Object();
      final CountDownLatch entered = new CountDownLatch(1);
      private final int gate;
   }

   @TempDir
   File dumpDir;
   private LockCycleHarness harness;
   private WorksheetScriptEnv pooled;
}
