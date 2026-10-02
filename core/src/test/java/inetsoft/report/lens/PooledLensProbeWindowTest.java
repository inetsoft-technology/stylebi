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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import inetsoft.report.TableLens;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.script.TableRowScope;
import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.util.script.ScriptSpan;
import inetsoft.util.script.graal.pool.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;

import static inetsoft.report.lens.FormulaTableLensVarTest.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Two residual windows of a formula table's object vars on its home (Testing #77123, the
 * #5965 verify review): G1, a take-over of a home whose only tenant was collected, which a
 * table then makes its home before the take-over takes it; and a retire() at an env reset
 * while the table is busy. Neither may make the table's next batch lose its object vars.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class PooledLensProbeWindowTest {
   @BeforeEach
   void capture() {
      logger = (Logger) LoggerFactory.getLogger(TableRowScope.class);
      appender = new ListAppender<>();
      appender.start();
      logger.addAppender(appender);
   }

   @AfterEach
   void retire() {
      logger.detachAppender(appender);
      SreeEnv.remove(PooledLensHandOffWindowTest.MAX_HOMES);
      PoolTestSupport.pullSpinHook(null);

      for(WorksheetScriptEnv env : envs) {
         PoolTestSupport.poolHook(env, "handOffHook", null);
         PoolTestSupport.poolHook(env, "takeOverHook", null);
         env.retire();
      }

      envs.clear();
   }

   /**
    * G1: the primary is a soft home whose only tenant was collected. Another thread's claim
    * starts a take-over of it and takes the locks of its tenants (none); the table's first
    * batch then takes it and makes it its home; only then the take-over takes the slot, and
    * holds it while the table's next batch runs. The batch misses its home and pulls from it:
    * the pull waits out the take-over (which gives the home back, the table's lock is busy)
    * and keeps every value. On main the pull failed at once: row 201 read 1, with one warning
    * "... another thread is using".
    */
   @Test
   void aTakeOverOfACollectedTenantsHomeNeverLosesTheNewTenantsVars() throws Exception {
      SreeEnv.setProperty(PooledLensHandOffWindowTest.MAX_HOMES, "0");
      AssetQuerySandbox box = PoolTestSupport.poolBox(true);
      WorksheetScriptEnv w = (WorksheetScriptEnv) box.getScriptEnv();
      envs.add(w);
      TableLens t = make(box, base(ROWS), PooledLensHandOffWindowTest.OBJ_DATE, "T");
      double[] v = new double[ROWS + 1];

      // the primary becomes a home whose only tenant is then collected
      try(ScriptSpan batch = w.openSpan()) {
         PoolTestSupport.run(w, "1");
         OwnedValueCodec.enroll(OwnedValueCodec.homeOf(batch), new SlotTenant() {
            @Override
            public boolean handOff(OwnedValueCodec codec) {
               return true;
            }
         });
      }

      PoolTestSupport.collectTenants(w);
      assertEquals(1, PoolTestSupport.homes(w), "a home whose tenant was collected");
      Object home = PoolTestSupport.slots(w).get(0);

      Thread[] prober = new Thread[1];
      CountDownLatch atTakeOver = new CountDownLatch(1);
      CountDownLatch resume1 = new CountDownLatch(1);
      CountDownLatch holding = new CountDownLatch(1);
      CountDownLatch resume2 = new CountDownLatch(1);
      CountDownLatch proberDone = new CountDownLatch(1);
      AtomicInteger spins = new AtomicInteger();
      PoolTestSupport.poolHook(w, "takeOverHook", slot -> {
         if(Thread.currentThread() == prober[0] && slot == home && atTakeOver.getCount() > 0) {
            atTakeOver.countDown();
            await(resume1);
         }
      });
      PoolTestSupport.poolHook(w, "handOffHook", slot -> {
         if(Thread.currentThread() == prober[0] && slot == home && holding.getCount() > 0) {
            holding.countDown();
            await(resume2);
         }
      });
      PoolTestSupport.pullSpinHook(slot -> {
         // the first retry lets the take-over go on, and waits until it gave the home back
         if(spins.incrementAndGet() == 1) {
            resume2.countDown();
            await(proberDone);
         }
      });

      ExecutorService pool = Executors.newFixedThreadPool(2);

      try {
         // the other claim: took the tenants' locks of the home (none), paused before the slot
         Future<?> holder = pool.submit(() -> {
            prober[0] = Thread.currentThread();

            try(SlotClaim ignored = w.claimSlot()) {
               return null;
            }
            finally {
               proberDone.countDown();
            }
         });

         assertTrue(atTakeOver.await(30, TimeUnit.SECONDS), "the take-over did not start");

         // the table's first batch takes the home (no tenant left) and enrolls on it
         read(t, v, 1, 200);
         assertEquals(1, PoolTestSupport.homes(w), "the table has a home");
         resume1.countDown();
         assertTrue(holding.await(30, TimeUnit.SECONDS), "the take-over did not hold the home");

         // the table's next batch, while the take-over holds the home
         Future<?> reader = pool.submit(() -> {
            read(t, v, 201, 400);
            return null;
         });

         reader.get(30, TimeUnit.SECONDS);
         resume2.countDown();
         holder.get(30, TimeUnit.SECONDS);
      }
      finally {
         resume1.countDown();
         resume2.countDown();
         pool.shutdownNow();
      }

      read(t, v, 401, ROWS);
      assertNoLoss(v, "G1");
      assertEquals(0, PoolTestSupport.probedSlots(w), "a probe leaked");
   }

   /**
    * A retire (an env reset) while the table is busy (its batch holds the table's lock) dooms
    * the table's idle home instead of closing it, and the table's next batch keeps every
    * value: it hands them off from the doomed home at its checkout ("nextBatch"), or the
    * evictor's expiry does once the table is done ("expiry"); the doomed home is closed then,
    * and no context leaks. On main the retire closed the home at once, the table could not
    * hand off, and its next batch lost its object var: row 201 read 1, with one warning.
    */
   @ParameterizedTest(name = "{0}")
   @ValueSource(strings = { "nextBatch", "expiry" })
   void aRetireWhileTheTableIsBusyKeepsItsVars(String how) throws Exception {
      AssetQuerySandbox box = PoolTestSupport.poolBox(true);
      WorksheetScriptEnv w = (WorksheetScriptEnv) box.getScriptEnv();
      envs.add(w);
      TableLens t = make(box, base(ROWS), PooledLensHandOffWindowTest.OBJ_DATE, "T");
      double[] v = new double[ROWS + 1];
      read(t, v, 1, 200);
      assertEquals(1, PoolTestSupport.homes(w), "the table has a home");
      List<Object> before = PoolTestSupport.slots(w);
      assertEquals(1, before.size());
      Object home = before.get(0);
      long doomedCloses = w.getMetrics().getDoomedCloses();
      Lock lensLock = (Lock) field(t, "lock");
      boolean closedAtRetire;
      CountDownLatch locked = new CountDownLatch(1);
      CountDownLatch unlock = new CountDownLatch(1);
      ExecutorService ex = Executors.newSingleThreadExecutor();

      try {
         // the table is busy: another thread holds its lock, as a batch does before it checks out
         Future<?> busy = ex.submit(() -> {
            lensLock.lock();

            try {
               locked.countDown();
               await(unlock);
            }
            finally {
               lensLock.unlock();
            }

            return null;
         });

         assertTrue(locked.await(30, TimeUnit.SECONDS));
         w.retire();
         closedAtRetire = PoolTestSupport.isClosed(home);
         unlock.countDown();
         busy.get(30, TimeUnit.SECONDS);
      }
      finally {
         unlock.countDown();
         ex.shutdownNow();
      }

      boolean closedAtExpiry = false;

      if(how.equals("expiry")) {
         PoolTestSupport.evictIdle(w, System.currentTimeMillis());
         closedAtExpiry = PoolTestSupport.isClosed(home);
      }

      read(t, v, 201, ROWS);
      assertNoLoss(v, how);
      assertFalse(closedAtRetire, how + ": the retire closed a busy table's home");
      assertTrue(closedAtExpiry || how.equals("nextBatch"),
                 "the expiry did not close the doomed home");
      assertTrue(PoolTestSupport.isClosed(home), how + ": the doomed home was not closed");
      assertFalse(PoolTestSupport.slots(w).contains(home), how + ": the doomed home is pooled");
      assertEquals(doomedCloses + 1, w.getMetrics().getDoomedCloses(), how + ": doomed closes");
      // the table's new home only
      assertEquals(1, w.getMetrics().getSize(), how + ": contexts");
      assertEquals(1, PoolTestSupport.homes(w), how + ": homes");
      w.retire();
      assertEquals(0, w.getMetrics().getSize(), how + ": a context leaked");
   }

   private void assertNoLoss(double[] v, String what) {
      List<String> bad = new ArrayList<>();

      for(int r = 1; r < v.length; r++) {
         if(v[r] != r) {
            bad.add(r + "=" + v[r]);
         }
      }

      List<String> warns = appender.list.stream().filter(e -> e.getLevel() == Level.WARN)
         .map(ILoggingEvent::getFormattedMessage).toList();
      assertTrue(bad.isEmpty() && warns.isEmpty(), () -> what + ": " + bad.size() +
         " wrong rows, first " + bad.subList(0, Math.min(5, bad.size())) + "; " + warns);
   }

   private static void read(TableLens t, double[] v, int from, int to) {
      for(int r = from; r <= to; r++) {
         assertTrue(t.moreRows(r), "row " + r);
         v[r] = num(t.getObject(r, 2));
      }
   }

   private static void await(CountDownLatch latch) {
      try {
         latch.await(30, TimeUnit.SECONDS);
      }
      catch(InterruptedException ex) {
         Thread.currentThread().interrupt();
      }
   }

   private static final int ROWS = 1200;
   private final List<WorksheetScriptEnv> envs = new ArrayList<>();
   private Logger logger;
   private ListAppender<ILoggingEvent> appender;
}
