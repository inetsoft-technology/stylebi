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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static inetsoft.report.composition.execution.lockcycle.LockCycleHarness.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The formula lens lock is bounded by the lock-stall watchdog (bug #76967): a lens-lock
 * waiter whose owner is blocked on a monitor the waiter holds fails with a stall and lets
 * go, and the owner then completes; a waiter behind a slowly progressing batch completes.
 * Pool off (the harness default); the pool-on batch hold is the same lock.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class FormulaLensLockStallTest {
   @BeforeEach
   public void setUp() {
      StallTestSupport.resetGlobalStallState();
      StallPolicy.setOverride(new StallPolicy(StallPolicy.Mode.FAIL, 1000, 200, dumpDir));
      harness = new LockCycleHarness();
   }

   @AfterEach
   public void tearDown() throws Exception {
      harness.close();
      StallPolicy.setOverride(null);
   }

   /**
    * FTL_R2 without the engine lock: T1 holds the formula lens lock and is BLOCKED on a
    * monitor that T2 holds while T2 waits for the lens lock.
    */
   @Test
   public void lensLockWaiterFailsWhenTheOwnerIsBlockedOnItsMonitor() throws Exception {
      Sandbox s = harness.control();
      List<List<Object>> expected = harness.await(
         harness.submit(() -> drain(s.formula(new DefaultTableLens(rows(40))))),
         ACTIVE_CAP, "control");
      MonitorTable base = new MonitorTable(rows(40), 5, 0);
      FormulaTableLens lens = harness.track(s.formula(base));
      CountDownLatch waiterHasMonitor = new CountDownLatch(1);
      AtomicReference<Thread> waiterThread = new AtomicReference<>();

      Future<Object> waiter = harness.submit(() -> {
         waiterThread.set(Thread.currentThread());

         synchronized(base.monitor) {
            waiterHasMonitor.countDown();
            assertTrue(base.entered.await(10, TimeUnit.SECONDS), "the owner never reached row 5");

            try {
               lens.moreRows(30);
               return "completed";
            }
            catch(LockStallException ex) {
               return ex;
            }
         }
      });
      assertTrue(waiterHasMonitor.await(10, TimeUnit.SECONDS));
      Future<List<List<Object>>> owner = harness.submit(() -> drain(lens));

      Object outcome = harness.await(waiter, ACTIVE_CAP, "lens lock waiter");
      assertTrue(outcome instanceof LockStallException, "the waiter must fail, got " + outcome);
      assertEquals("FormulaTableLens.moreRows", ((LockStallException) outcome).getSite());
      assertEquals(expected, harness.await(owner, ACTIVE_CAP, "lens lock owner"),
                   "the owner completes with every row once the waiter lets go");
      // only this test's waiter: hung threads of earlier cycle tests may still be registered
      StallTestSupport.awaitTrue(
         () -> WaitRegistry.global().getActive().stream()
            .noneMatch(r -> r.getThread() == waiterThread.get()), 15,
         "a wait is still registered after the stall");
   }

   /**
    * No false positive: the owner's batch computes a row every 300 ms under the lens lock,
    * for longer than the 1 s limit; the waiter completes with the right rows.
    */
   @Test
   public void waiterBehindAProgressingBatchCompletes() throws Exception {
      Sandbox s = harness.control();
      List<List<Object>> expected = harness.await(
         harness.submit(() -> drain(s.formula(new DefaultTableLens(rows(8))))),
         ACTIVE_CAP, "control");
      MonitorTable base = new MonitorTable(rows(8), 1, 300);
      FormulaTableLens lens = harness.track(s.formula(base));

      Future<List<List<Object>>> owner = harness.submit(() -> drain(lens));
      assertTrue(base.entered.await(10, TimeUnit.SECONDS));
      Future<Boolean> waiter = harness.submit(() -> lens.moreRows(8));

      assertTrue(harness.await(waiter, ACTIVE_CAP, "waiter behind a slow batch"));
      assertEquals(expected, harness.await(owner, ACTIVE_CAP, "slow owner"));
   }

   @Test
   public void uncontendedLensRegistersNothing() throws Exception {
      Sandbox s = harness.control();
      FormulaTableLens lens = harness.track(s.formula(new DefaultTableLens(rows(20))));
      long before = WaitRegistry.global().getBeginCount();

      harness.await(harness.submit(() -> drain(lens)), ACTIVE_CAP, "drain");

      assertEquals(before, WaitRegistry.global().getBeginCount());
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
    * A base whose rows from {@code gate} on are read under {@link #monitor}, optionally
    * slowly; {@link #entered} opens on the first such read.
    */
   private static final class MonitorTable extends DefaultTableLens {
      MonitorTable(Object[][] data, int gate, long millisPerRow) {
         super(data);
         this.gate = gate;
         this.millisPerRow = millisPerRow;
      }

      @Override
      public boolean moreRows(int row) {
         if(row >= gate && row != TableLens.EOT) {
            entered.countDown();

            synchronized(monitor) {
               pause();
            }
         }

         return super.moreRows(row);
      }

      private void pause() {
         if(millisPerRow <= 0) {
            return;
         }

         try {
            Thread.sleep(millisPerRow);
         }
         catch(InterruptedException ex) {
            Thread.currentThread().interrupt();
         }
      }

      final Object monitor = new Object();
      final CountDownLatch entered = new CountDownLatch(1);
      private final int gate;
      private final long millisPerRow;
   }

   @TempDir
   File dumpDir;
   private LockCycleHarness harness;
}
