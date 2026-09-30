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

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import static inetsoft.report.lens.FormulaTableLensVarTest.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * A pool thread that holds a formula table's idle home without a claim (a take-over by another
 * table's claim, the evictor's expiry) must never make the table's own reader lose its object
 * vars (Testing #77123, finding B1-R2-1 of the reliability suite). On main such a thread took
 * the slot first and the table's lock only then, with tryLock: when the reader was in a batch
 * (it holds its table's lock), the take-over failed and gave the slot back, but while it held
 * it the reader's batch missed its home, ran on another context, and its pull from the home
 * failed, so every object var of the home read as undefined ("... that another thread is
 * using"). The pool now takes the tenants' locks first, in the order a batch takes them (its
 * table's lock, then its context), and gives the slot back before them.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class PooledLensHandOffWindowTest {
   // the reliability suite's syn:objDate: a plain object holding a Date and a counter
   static final String OBJ_DATE = "var o = o || { d: new Date(0), n: 0 }; o.n++; " +
      "o.d.setTime(o.d.getTime() + 1000); o.d.getTime() == o.n * 1000 ? o.n : -o.n";

   @BeforeEach
   void capture() {
      logger = (Logger) LoggerFactory.getLogger(TableRowScope.class);
      appender = new ListAppender<>();
      appender.start();
      logger.addAppender(appender);
   }

   @AfterEach
   void retire() throws Exception {
      logger.detachAppender(appender);
      SreeEnv.remove(MAX_HOMES);

      for(WorksheetScriptEnv env : envs) {
         setHook(env, null);
         setField(env, "plainTakeHook", null);
         env.retire();
      }

      envs.clear();
   }

   /**
    * The take-over (another thread's claim, no exclusive home) and the expiry (the evictor)
    * each hold the idle home while the table's next batch starts: the batch keeps every value
    * of its object var. On main the take-over or expiry holds the home first, the batch runs on
    * another context and its pull fails: row 201 reads 1 and one warning "... another thread is
    * using" is logged.
    */
   @ParameterizedTest(name = "{0}")
   @ValueSource(strings = { "takeover", "expiry" })
   void aHandOffHoldingTheHomeNeverLosesAConcurrentBatchsVars(String how) throws Exception {
      if(how.equals("takeover")) {
         // every home is soft: another claim takes it over after a hand-off
         SreeEnv.setProperty(MAX_HOMES, "0");
      }

      AssetQuerySandbox box = PoolTestSupport.poolBox(true);
      WorksheetScriptEnv w = (WorksheetScriptEnv) box.getScriptEnv();
      envs.add(w);
      TableLens t = make(box, base(ROWS), OBJ_DATE, "T");
      double[] v = new double[ROWS + 1];
      read(t, v, 1, 200);
      assertEquals(1, PoolTestSupport.homes(w), "the table has a home");

      CountDownLatch holding = new CountDownLatch(1);
      CountDownLatch resume = new CountDownLatch(1);
      setHook(w, slot -> {
         holding.countDown();

         try {
            resume.await(30, TimeUnit.SECONDS);
         }
         catch(InterruptedException ex) {
            Thread.currentThread().interrupt();
         }
      });

      ExecutorService pool = Executors.newFixedThreadPool(2);

      try {
         // the pool thread: holds the idle home, paused before its hand-off
         Future<?> holder = pool.submit(() -> {
            if(how.equals("takeover")) {
               try(SlotClaim ignored = w.claimSlot()) {
                  return null;
               }
            }

            PoolTestSupport.evictIdle(w, System.currentTimeMillis() + 3_600_000L);
            return null;
         });

         assertTrue(holding.await(30, TimeUnit.SECONDS), "the " + how + " did not hold the home");
         setHook(w, null);

         // the table's next batch, while the home is held
         Thread[] readerThread = new Thread[1];
         Future<?> reader = pool.submit(() -> {
            readerThread[0] = Thread.currentThread();
            read(t, v, 201, 400);
            return null;
         });

         // until the batch ran (main) or waits for its table's lock (the hand-off holds it)
         long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);

         while(!reader.isDone() && !waitsForLensLock(readerThread[0]) &&
               System.nanoTime() < end)
         {
            Thread.sleep(5);
         }

         resume.countDown();
         holder.get(30, TimeUnit.SECONDS);
         reader.get(30, TimeUnit.SECONDS);
      }
      finally {
         resume.countDown();
         pool.shutdownNow();
      }

      read(t, v, 401, ROWS);
      List<String> bad = new ArrayList<>();

      for(int r = 1; r <= ROWS; r++) {
         if(v[r] != r) {
            bad.add(r + "=" + v[r]);
         }
      }

      List<String> warns = appender.list.stream().filter(e -> e.getLevel() == Level.WARN)
         .map(ILoggingEvent::getFormattedMessage).toList();
      assertTrue(bad.isEmpty() && warns.isEmpty(), () -> how + ": " + bad.size() +
         " wrong rows, first " + bad.subList(0, Math.min(5, bad.size())) + "; " + warns);
   }

   /**
    * A take-over skips a soft home while a tenant's lock is held by another thread (its batch)
    * without ever holding the slot, and takes it over once the lock is free. On main it held
    * the slot first and only then found the lock busy.
    */
   @Test
   void aTakeOverSkipsAHomeWhoseTenantIsBusyWithoutHoldingIt() throws Exception {
      SreeEnv.setProperty(MAX_HOMES, "0");
      WorksheetScriptEnv w = (WorksheetScriptEnv) PoolTestSupport.poolBox(true).getScriptEnv();
      envs.add(w);
      ReentrantLock tableLock = new ReentrantLock();
      SlotTenant tenant = new SlotTenant() {
         @Override
         public boolean handOff(OwnedValueCodec codec) {
            if(!tableLock.tryLock()) {
               return false;
            }

            tableLock.unlock();
            return true;
         }

         @Override
         public Lock handOffLock() {
            return tableLock;
         }
      };

      try(ScriptSpan batch = w.openSpan()) {
         PoolTestSupport.run(w, "1");
         OwnedValueCodec.enroll(OwnedValueCodec.homeOf(batch), tenant);
      }

      assertEquals(1, PoolTestSupport.homes(w));
      AtomicInteger held = new AtomicInteger();
      setHook(w, slot -> held.incrementAndGet());
      CountDownLatch locked = new CountDownLatch(1);
      CountDownLatch unlock = new CountDownLatch(1);
      ExecutorService ex = Executors.newSingleThreadExecutor();

      try {
         // the tenant's batch holds its lock
         Future<?> batch = ex.submit(() -> {
            tableLock.lock();

            try {
               locked.countDown();
               unlock.await(30, TimeUnit.SECONDS);
            }
            finally {
               tableLock.unlock();
            }

            return null;
         });

         assertTrue(locked.await(30, TimeUnit.SECONDS));

         try(SlotClaim ignored = w.claimSlot()) {
            PoolTestSupport.run(w, "2");
         }

         assertEquals(0, held.get(), "the take-over held the home of a busy tenant");
         assertEquals(1, PoolTestSupport.homes(w), "the busy tenant keeps its home");
         unlock.countDown();
         batch.get(30, TimeUnit.SECONDS);
      }
      finally {
         unlock.countDown();
         ex.shutdownNow();
      }

      // the lock is free: the next claim takes the home over (the tenant hands off)
      while(PoolTestSupport.homes(w) > 0 && held.get() < 5) {
         try(SlotClaim ignored = w.claimSlot()) {
            PoolTestSupport.run(w, "3");
         }
      }

      assertTrue(held.get() >= 1, "no take-over ran");
      assertEquals(0, PoolTestSupport.homes(w), "taken over");
      assertFalse(tableLock.isLocked(), "the pool released the tenant's lock");
      assertNotNull(tenant);
   }

   /**
    * A claim of another thread checks that a slot is no home, the table's batch makes it its
    * home and releases it, and only then the claim takes the slot (Testing #77123, finding G2).
    * The claim must not keep the table's home for itself: on main it held it for its whole
    * claim, so the table's next batch missed its home, its pull failed, and row 201 read 1 with
    * one warning "... another thread is using". The claim now gives the new home back and
    * takes another context.
    */
   @ParameterizedTest(name = "maxHomes {0}")
   @ValueSource(strings = { "default", "0" })
   void aClaimNeverKeepsASlotThatBecameAHomeBeforeItTookIt(String maxHomes) throws Exception {
      if(!maxHomes.equals("default")) {
         SreeEnv.setProperty(MAX_HOMES, maxHomes);
      }

      AssetQuerySandbox box = PoolTestSupport.poolBox(true);
      WorksheetScriptEnv w = (WorksheetScriptEnv) box.getScriptEnv();
      envs.add(w);
      TableLens t = make(box, base(ROWS), OBJ_DATE, "T");
      double[] v = new double[ROWS + 1];
      assertEquals(0, PoolTestSupport.homes(w), "no home yet");
      // an idle primary: the other claim checks it, the table's batch takes it meanwhile
      w.init();

      Thread[] claimThread = new Thread[1];
      CountDownLatch checked = new CountDownLatch(1);
      CountDownLatch resume = new CountDownLatch(1);
      CountDownLatch claimed = new CountDownLatch(1);
      CountDownLatch done = new CountDownLatch(1);
      setField(w, "plainTakeHook", (Consumer<?>) slot -> {
         if(Thread.currentThread() == claimThread[0] && checked.getCount() > 0) {
            checked.countDown();

            try {
               resume.await(30, TimeUnit.SECONDS);
            }
            catch(InterruptedException ex) {
               Thread.currentThread().interrupt();
            }
         }
      });

      ExecutorService pool = Executors.newSingleThreadExecutor();

      try {
         // the other claim: found the primary is no home, paused before it takes it
         Future<?> claim = pool.submit(() -> {
            claimThread[0] = Thread.currentThread();

            try(SlotClaim ignored = w.claimSlot()) {
               claimed.countDown();
               done.await(30, TimeUnit.SECONDS);
            }

            return null;
         });

         assertTrue(checked.await(30, TimeUnit.SECONDS), "the claim did not check the slot");

         // the table's first batch makes the (idle) primary its home
         read(t, v, 1, 200);
         assertEquals(1, PoolTestSupport.homes(w), "the table has a home");
         resume.countDown();
         assertTrue(claimed.await(30, TimeUnit.SECONDS), "the claim did not take a slot");

         // the table's next batch, while the other claim is open
         read(t, v, 201, 400);
         done.countDown();
         claim.get(30, TimeUnit.SECONDS);
      }
      finally {
         resume.countDown();
         done.countDown();
         pool.shutdownNow();
      }

      read(t, v, 401, ROWS);
      List<String> bad = new ArrayList<>();

      for(int r = 1; r <= ROWS; r++) {
         if(v[r] != r) {
            bad.add(r + "=" + v[r]);
         }
      }

      List<String> warns = appender.list.stream().filter(e -> e.getLevel() == Level.WARN)
         .map(ILoggingEvent::getFormattedMessage).toList();
      assertTrue(bad.isEmpty() && warns.isEmpty(), () -> "maxHomes " + maxHomes + ": " +
         bad.size() + " wrong rows, first " + bad.subList(0, Math.min(5, bad.size())) + "; " +
         warns);
   }

   private static boolean waitsForLensLock(Thread thread) {
      if(thread == null) {
         return false;
      }

      for(StackTraceElement e : thread.getStackTrace()) {
         if(e.getMethodName().equals("lockBounded")) {
            return true;
         }
      }

      return false;
   }

   private static void read(TableLens t, double[] v, int from, int to) {
      for(int r = from; r <= to; r++) {
         assertTrue(t.moreRows(r), "row " + r);
         v[r] = num(t.getObject(r, 2));
      }
   }

   // the pool's hand-off hook is package-private: set by reflection, as PoolTestSupport does
   private static void setHook(WorksheetScriptEnv env, Consumer<?> hook) throws Exception {
      setField(env, "handOffHook", hook);
   }

   private static void setField(WorksheetScriptEnv env, String name, Consumer<?> hook)
      throws Exception
   {
      java.lang.reflect.Method pool = WorksheetScriptEnv.class.getDeclaredMethod("pool");
      pool.setAccessible(true);
      Object slotPool = pool.invoke(env);
      Field field = slotPool.getClass().getDeclaredField(name);
      field.setAccessible(true);
      field.set(slotPool, hook);
   }

   static final String MAX_HOMES = "script.ws.contextPool.maxHomes";
   private static final int ROWS = 1200;
   private final List<WorksheetScriptEnv> envs = new ArrayList<>();
   private Logger logger;
   private ListAppender<ILoggingEvent> appender;
}
