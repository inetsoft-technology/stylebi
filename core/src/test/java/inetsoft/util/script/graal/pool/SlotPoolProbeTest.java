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
package inetsoft.util.script.graal.pool;

import inetsoft.util.script.ScriptSpan;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A pool thread that holds a home without a claim and without every tenant's lock (a probe:
 * a take-over, the expiry, a retire) must not make a tenant's batch lose its object vars
 * (Testing #77123, finding G1), and a retire must not close the home of a busy tenant
 * (Testing #77123, the retire() residual of B1-R2-1). The tenants here are fake formula
 * tables: a batch holds the tenant's lock, a hand-off takes it without waiting.
 */
@Tag("core")
class SlotPoolProbeTest {
   @AfterEach
   void cleanup() {
      PoolTestSupport.pullSpinHook(null);

      for(WorksheetScriptEnv env : envs) {
         PoolTestSupport.poolHook(env, "handOffHook", null);
         PoolTestSupport.poolHook(env, "takeOverHook", null);
         PoolTestSupport.poolHook(env, "closeIdleHook", null);
         PoolTestSupport.poolHook(env, "giveBackHook", null);
         env.retire();
      }

      envs.clear();
      executor.shutdownNow();
   }

   /**
    * G1 at the pool level: a take-over takes the locks of a home whose only tenant was
    * collected (none), a table's batch then takes the home and enrolls on it, and the
    * take-over takes the slot. The table's next batch (it holds its lock) pulls from the home
    * while the take-over holds it: the pull waits out the probe and saves the values. On main
    * the pull failed at once (nothing saved).
    */
   @Test
   void aPullWaitsOutATakeOverThatTookAHomeOfACollectedTenant() throws Exception {
      WorksheetScriptEnv env = softEnv();
      Slot home = homeOf(env, new Tenant());
      PoolTestSupport.collectTenants(env);
      assertEquals(1, PoolTestSupport.homes(env), "a home whose tenant was collected");

      Tenant table = new Tenant();
      Thread[] prober = new Thread[1];
      CountDownLatch atTakeOver = new CountDownLatch(1);
      CountDownLatch resume1 = new CountDownLatch(1);
      CountDownLatch holding = new CountDownLatch(1);
      CountDownLatch resume2 = new CountDownLatch(1);
      CountDownLatch proberDone = new CountDownLatch(1);
      PoolTestSupport.poolHook(env, "takeOverHook", slot -> {
         if(Thread.currentThread() == prober[0] && atTakeOver.getCount() > 0) {
            atTakeOver.countDown();
            await(resume1);
         }
      });
      PoolTestSupport.poolHook(env, "handOffHook", slot -> {
         if(Thread.currentThread() == prober[0] && holding.getCount() > 0) {
            holding.countDown();
            await(resume2);
         }
      });
      AtomicInteger spins = new AtomicInteger();
      PoolTestSupport.pullSpinHook(slot -> {
         // the first retry lets the take-over go on, and waits until it gave the home back
         if(spins.incrementAndGet() == 1) {
            resume2.countDown();
            await(proberDone);
         }
      });

      Future<?> take = executor.submit(() -> {
         prober[0] = Thread.currentThread();

         try(SlotClaim ignored = env.claimSlot()) {
            return null;
         }
         finally {
            proberDone.countDown();
         }
      });

      try {
         assertTrue(atTakeOver.await(30, TimeUnit.SECONDS), "the take-over did not start");

         // the table's batch: takes the home (no tenant left) and enrolls on it
         Slot batchSlot;
         table.lock.lock();

         try(ScriptSpan batch = env.openSpan()) {
            PoolTestSupport.run(env, "1");
            OwnedValueCodec.Home h = OwnedValueCodec.homeOf(batch);
            batchSlot = h.slot;
            OwnedValueCodec.enroll(h, table);
         }
         finally {
            table.lock.unlock();
         }

         assertSame(home, batchSlot, "the table's batch took the home");
         resume1.countDown();
         assertTrue(holding.await(30, TimeUnit.SECONDS), "the take-over did not hold the home");

         // the table's next batch, while the take-over holds the home
         List<OwnedValueCodec> saved = new ArrayList<>();
         boolean pulled;
         table.lock.lock();

         try {
            pulled = OwnedValueCodec.pull(new OwnedValueCodec.Home(env.pool(), home), table,
                                          saved::add);
         }
         finally {
            table.lock.unlock();
         }

         resume2.countDown();
         take.get(30, TimeUnit.SECONDS);
         assertTrue(pulled && saved.size() == 1, "the pull failed (spins " + spins.get() + ")");
         assertEquals(0, table.refused.get(), "no hand-off was refused");
         assertEquals(0, PoolTestSupport.probedSlots(env), "a probe leaked");
      }
      finally {
         resume1.countDown();
         resume2.countDown();
      }
   }

   /**
    * The pull's wait for a probe is bounded: past about 50 ms it fails as before, nothing
    * saved, and the slot keeps its tenant's values for the prober's hand-off.
    */
   @Test
   void aPullOfAProbedHomeGivesUpAfterItsBound() throws Exception {
      WorksheetScriptEnv env = softEnv();
      Tenant tenant = new Tenant();
      Slot home = homeOf(env, tenant);
      Runnable release = holdInATakeOver(env);

      try {
         AtomicInteger spins = new AtomicInteger();
         PoolTestSupport.pullSpinHook(slot -> spins.incrementAndGet());
         List<OwnedValueCodec> saved = new ArrayList<>();
         long start = System.nanoTime();
         boolean pulled = OwnedValueCodec.pull(new OwnedValueCodec.Home(env.pool(), home),
                                               tenant, saved::add);
         long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

         assertFalse(pulled, "pulled from a home another thread holds");
         assertTrue(saved.isEmpty());
         assertTrue(spins.get() > 0, "the pull did not wait for the probe");
         assertTrue(millis >= 50 && millis < 10_000, "waited " + millis + " ms");
         assertFalse(Thread.currentThread().isInterrupted());
      }
      finally {
         release.run();
      }

      assertEquals(0, PoolTestSupport.probedSlots(env), "a probe leaked");
   }

   /**
    * An interrupted puller does not wait for a probe, and keeps its interrupt: before the pull,
    * or arriving during the wait.
    */
   @Test
   void anInterruptedPullerStopsAtOnceAndKeepsItsInterrupt() throws Exception {
      for(boolean during : new boolean[] { false, true }) {
         WorksheetScriptEnv env = softEnv();
         Tenant tenant = new Tenant();
         Slot home = homeOf(env, tenant);
         Runnable release = holdInATakeOver(env);

         try {
            AtomicInteger spins = new AtomicInteger();
            PoolTestSupport.pullSpinHook(slot -> {
               spins.incrementAndGet();

               if(during) {
                  Thread.currentThread().interrupt();
               }
            });

            if(!during) {
               Thread.currentThread().interrupt();
            }

            boolean pulled = OwnedValueCodec.pull(new OwnedValueCodec.Home(env.pool(), home),
                                                  tenant, codec -> fail("saved"));
            boolean interrupted = Thread.interrupted();

            // first that the pull retried as expected: with no retry at all the interrupt that
            // arrives "during" the wait was never set, so the flag check says nothing
            assertEquals(during ? 1 : 0, spins.get(), "during " + during + ": retries of " +
               "the pull while the home is probed (1 = it retried once, then saw the interrupt)");
            assertFalse(pulled, "during " + during);
            assertTrue(interrupted, "during " + during + ": the interrupt flag was cleared");
         }
         finally {
            Thread.interrupted();
            PoolTestSupport.pullSpinHook(null);
            release.run();
         }
      }
   }

   /**
    * A home held by a claim (no probe) fails the pull at once, as before: only a probe is
    * waited for.
    */
   @Test
   @Disabled("Fails intermittently: Jenkins main #377, #386 (Bug #77830)")
   void aPullOfAHomeHeldByAClaimDoesNotWait() throws Exception {
      WorksheetScriptEnv env = softEnv();
      Tenant tenant = new Tenant();
      Slot home = homeOf(env, tenant);
      AtomicInteger spins = new AtomicInteger();
      PoolTestSupport.pullSpinHook(slot -> spins.incrementAndGet());
      Runnable release = PoolTestSupport.holdElsewhere(home, executor);

      try {
         assertFalse(OwnedValueCodec.pull(new OwnedValueCodec.Home(env.pool(), home), tenant,
                                          codec -> fail("saved")));
         assertEquals(0, spins.get(), "waited for a slot no probe holds");
      }
      finally {
         release.run();
      }
   }

   /**
    * A retire while a tenant is busy (its batch holds its lock) dooms its idle home instead of
    * closing it: the home is never handed out to new work, the tenant's next batch pulls its
    * values from it (or its own next claim hands them off, or the expiry does once the tenant
    * is done), and only then the home is closed. On main the retire closed it at once, the
    * tenant's hand-off refused, and its values were lost.
    */
   @Test
   void aRetireDoomsTheIdleHomeOfABusyTenantUntilItPulled() throws Exception {
      retireWhileBusy("pull");
   }

   @Test
   void aRetireDoomsTheIdleHomeOfABusyTenantUntilItsOwnClaim() throws Exception {
      retireWhileBusy("claim");
   }

   @Test
   void aRetireDoomsTheIdleHomeOfABusyTenantUntilTheExpiry() throws Exception {
      retireWhileBusy("expiry");
   }

   @Test
   void aRetireDoomsTheIdleHomeOfABusyTenantUntilTheTenantIsCollected() throws Exception {
      retireWhileBusy("collected");
   }

   private void retireWhileBusy(String how) throws Exception {
      WorksheetScriptEnv env = PoolTestSupport.env();
      envs.add(env);
      Tenant tenant = new Tenant();
      Slot home = homeOf(env, tenant);
      long doomedCloses = env.getMetrics().getDoomedCloses();
      CountDownLatch locked = new CountDownLatch(1);
      CountDownLatch go = new CountDownLatch(1);
      List<OwnedValueCodec> saved = new ArrayList<>();
      Object[] next = new Object[1];

      // the tenant's batch: holds its lock across the retire, then ends its way
      Future<?> batch = executor.submit(() -> {
         tenant.lock.lock();

         try {
            locked.countDown();
            await(go);

            if(how.equals("pull")) {
               assertTrue(OwnedValueCodec.pull(new OwnedValueCodec.Home(env.pool(), home),
                                               tenant, saved::add), "the pull failed");
            }
            else if(how.equals("claim")) {
               Object previous = OwnedValueCodec.preferHomeOf(tenant);

               try(SlotClaim ignored = env.claimSlot()) {
                  next[0] = PoolTestSupport.currentSlot(env);
               }
               finally {
                  OwnedValueCodec.restoreHomeHint(previous);
               }
            }
         }
         finally {
            tenant.lock.unlock();
         }

         return null;
      });

      try {
         assertTrue(locked.await(30, TimeUnit.SECONDS));
         env.retire();

         assertFalse(home.isClosed(), "the retire closed the home of a busy tenant");
         assertTrue(home.isDoomed(), "the home is doomed");
         assertEquals(0, tenant.refused.get() + tenant.handedOff.get(), "hand-offs");
         assertEquals(1, PoolTestSupport.homes(env));

         // other work never gets the doomed home, and leaves it to its busy tenant
         Object other;

         try(SlotClaim ignored = env.claimSlot()) {
            PoolTestSupport.run(env, "2");
            other = PoolTestSupport.currentSlot(env);
         }

         assertNotSame(home, other, "the doomed home was handed out");
         assertFalse(home.isClosed(), "a claim closed the home of a busy tenant");

         go.countDown();
         batch.get(30, TimeUnit.SECONDS);
      }
      finally {
         go.countDown();
      }

      switch(how) {
      case "pull" -> assertEquals(1, saved.size(), "pulled");
      case "claim" -> {
         assertNotSame(home, next[0], "the doomed home was handed out to its tenant");
         assertEquals(1, tenant.handedOff.get(), "handed off at the tenant's claim");
      }
      case "expiry" -> {
         assertFalse(home.isClosed(), "closed before the expiry");
         PoolTestSupport.evictIdle(env, System.currentTimeMillis());
         assertEquals(1, tenant.handedOff.get(), "handed off at the expiry");
      }
      case "collected" -> {
         assertFalse(home.isClosed(), "closed before the evictor");
         PoolTestSupport.collectTenants(env);
         PoolTestSupport.evictIdle(env, System.currentTimeMillis());
      }
      default -> fail(how);
      }

      assertTrue(home.isClosed(), how + ": the doomed home was not closed");
      assertEquals(0, tenant.refused.get(), how + ": a hand-off was refused");
      assertEquals(doomedCloses + 1, env.getMetrics().getDoomedCloses(), how + ": doomed closes");
      assertEquals(0, PoolTestSupport.homes(env), how + ": homes");
      assertFalse(env.pool().slots().contains(home), how + ": the closed home is still pooled");
      assertEquals(env.pool().slots().size(), env.getMetrics().getSize(), how + ": size");
      assertEquals(0, PoolTestSupport.probedSlots(env), how + ": a probe leaked");
      env.retire();
      assertEquals(0, env.getMetrics().getSize(), how + ": a slot leaked");
   }

   /**
    * closeIdle's second take of the tenants' locks: a retire takes the locks of the tenants it
    * sees (none), and before it takes the slot a tenant enrolls on it (here by a holder that
    * took the slot before the retire) and starts its next batch. The retire must not close the
    * slot under the busy tenant: it dooms it, the tenant's pull saves its values, and the home
    * is closed then. Without the second take the retire closed it at once and the tenant's
    * hand-off was refused (its values unreadable).
    */
   @Test
   void aTenantThatEnrollsWhileARetireTakesTheLocksKeepsItsHome() throws Exception {
      WorksheetScriptEnv env = PoolTestSupport.env();
      envs.add(env);
      env.init();
      Slot home = env.pool().primary();
      Tenant tenant = new Tenant();
      Thread retirer = Thread.currentThread();
      long doomedCloses = env.getMetrics().getDoomedCloses();
      CountDownLatch held = new CountDownLatch(1);
      CountDownLatch paused = new CountDownLatch(1);
      CountDownLatch enrolled = new CountDownLatch(1);
      CountDownLatch go = new CountDownLatch(1);
      List<OwnedValueCodec> saved = new ArrayList<>();
      PoolTestSupport.poolHook(env, "closeIdleHook", slot -> {
         if(Thread.currentThread() == retirer && slot == home && paused.getCount() > 0) {
            paused.countDown();
            await(enrolled);
         }
      });

      Future<?> holder = executor.submit(() -> {
         assertTrue(home.tryAcquire(), "the slot is not idle");
         held.countDown();
         await(paused);
         // the tenant's batch: enrolls on the slot it holds, then its next batch is busy
         tenant.lock.lock();

         try {
            env.pool().enroll(home, tenant);
            home.release();
            enrolled.countDown();
            await(go);
            assertTrue(OwnedValueCodec.pull(new OwnedValueCodec.Home(env.pool(), home),
                                            tenant, saved::add), "the pull failed");
         }
         finally {
            tenant.lock.unlock();
         }

         return null;
      });

      try {
         assertTrue(held.await(30, TimeUnit.SECONDS));
         env.retire();
         assertEquals(0, paused.getCount(), "the retire did not reach closeIdle's window");
         assertEquals(0, tenant.refused.get(), "the retire handed off a busy tenant (refused)");
         assertFalse(home.isClosed(), "the retire closed the home of a busy tenant that " +
                     "enrolled while it took the locks");
         assertTrue(home.isDoomed(), "the home is doomed");
         assertEquals(1, PoolTestSupport.homes(env));
         go.countDown();
         holder.get(30, TimeUnit.SECONDS);
      }
      finally {
         enrolled.countDown();
         go.countDown();
      }

      assertEquals(1, saved.size(), "pulled");
      assertTrue(home.isClosed(), "the doomed home was not closed after the pull");
      assertEquals(0, tenant.refused.get(), "a hand-off was refused");
      assertEquals(doomedCloses + 1, env.getMetrics().getDoomedCloses(), "doomed closes");
      assertEquals(0, PoolTestSupport.homes(env));
      assertEquals(0, PoolTestSupport.probedSlots(env), "a probe leaked");
      assertEquals(env.pool().slots().size(), env.getMetrics().getSize(), "size");
   }

   /**
    * The take-over gives the slot back before it ends its probe: a puller whose tryLock fails
    * while the take-over still holds the slot (paused right before giveBack's unlock) must see
    * the probe and wait, then pull. With the probe ended before giveBack, the puller saw no
    * probe, did not retry, and its pull failed although the slot was freed a moment later.
    */
   @Test
   void aPullerSeesTheProbeUntilTheTakeOverGaveTheSlotBack() throws Exception {
      WorksheetScriptEnv env = softEnv();
      Slot home = homeOf(env, new Tenant());
      PoolTestSupport.collectTenants(env);
      Tenant table = new Tenant();
      Thread[] prober = new Thread[1];
      CountDownLatch atTakeOver = new CountDownLatch(1);
      CountDownLatch resume1 = new CountDownLatch(1);
      CountDownLatch holding = new CountDownLatch(1);
      CountDownLatch resume2 = new CountDownLatch(1);
      CountDownLatch givingBack = new CountDownLatch(1);
      CountDownLatch resume3 = new CountDownLatch(1);
      CountDownLatch proberDone = new CountDownLatch(1);
      PoolTestSupport.poolHook(env, "takeOverHook", slot -> {
         if(Thread.currentThread() == prober[0] && slot == home && atTakeOver.getCount() > 0) {
            atTakeOver.countDown();
            await(resume1);
         }
      });
      PoolTestSupport.poolHook(env, "handOffHook", slot -> {
         if(Thread.currentThread() == prober[0] && slot == home && holding.getCount() > 0) {
            holding.countDown();
            await(resume2);
         }
      });
      PoolTestSupport.poolHook(env, "giveBackHook", slot -> {
         if(Thread.currentThread() == prober[0] && slot == home && givingBack.getCount() > 0) {
            givingBack.countDown();
            await(resume3);
         }
      });
      AtomicInteger spins = new AtomicInteger();
      PoolTestSupport.pullSpinHook(slot -> {
         if(spins.incrementAndGet() == 1) {
            resume3.countDown();
            await(proberDone);
         }
      });

      Future<?> take = executor.submit(() -> {
         prober[0] = Thread.currentThread();

         try(SlotClaim ignored = env.claimSlot()) {
            return null;
         }
         finally {
            proberDone.countDown();
         }
      });

      try {
         assertTrue(atTakeOver.await(30, TimeUnit.SECONDS), "the take-over did not start");

         // the table's batch takes the home (no tenant left) and enrolls on it
         table.lock.lock();

         try(ScriptSpan batch = env.openSpan()) {
            PoolTestSupport.run(env, "1");
            OwnedValueCodec.Home h = OwnedValueCodec.homeOf(batch);
            assertSame(home, h.slot, "the table's batch took the home");
            OwnedValueCodec.enroll(h, table);
         }
         finally {
            table.lock.unlock();
         }

         resume1.countDown();
         assertTrue(holding.await(30, TimeUnit.SECONDS), "the take-over did not hold the home");

         // the table's next batch holds its lock: the take-over cannot lock it, gives back
         boolean pulled;
         int probed;
         table.lock.lock();

         try {
            resume2.countDown();
            assertTrue(givingBack.await(30, TimeUnit.SECONDS), "the take-over did not give back");
            probed = PoolTestSupport.probedSlots(env);
            pulled = OwnedValueCodec.pull(new OwnedValueCodec.Home(env.pool(), home), table,
                                          codec -> { });
         }
         finally {
            table.lock.unlock();
            resume3.countDown();
         }

         take.get(30, TimeUnit.SECONDS);
         assertTrue(pulled && spins.get() >= 1, "the puller saw no probe while the take-over " +
                    "still held the slot (probed slots " + probed + ", retries " + spins.get() +
                    ", pulled " + pulled + ")");
         assertEquals(1, probed, "the slot was not probed while it was still held");
         assertEquals(0, PoolTestSupport.probedSlots(env), "a probe leaked");
      }
      finally {
         resume1.countDown();
         resume2.countDown();
         resume3.countDown();
      }
   }

   /**
    * A retire still closes the idle home of an idle tenant at once, after its hand-off.
    */
   @Test
   void aRetireClosesTheIdleHomeOfAnIdleTenant() throws Exception {
      WorksheetScriptEnv env = PoolTestSupport.env();
      envs.add(env);
      Tenant tenant = new Tenant();
      Slot home = homeOf(env, tenant);
      env.retire();

      assertTrue(home.isClosed());
      assertEquals(1, tenant.handedOff.get());
      assertEquals(0, tenant.refused.get());
      assertEquals(0, PoolTestSupport.homes(env));
      assertEquals(0, env.getMetrics().getSize());
      assertFalse(tenant.lock.isLocked(), "the retire released the tenant's lock");
   }

   // a take-over of the soft home on another thread, paused while it holds the home (a probe)
   private Runnable holdInATakeOver(WorksheetScriptEnv env) throws Exception {
      Thread[] prober = new Thread[1];
      CountDownLatch holding = new CountDownLatch(1);
      CountDownLatch resume = new CountDownLatch(1);
      PoolTestSupport.poolHook(env, "handOffHook", slot -> {
         if(Thread.currentThread() == prober[0] && holding.getCount() > 0) {
            holding.countDown();
            await(resume);
         }
      });
      Future<?> take = executor.submit(() -> {
         prober[0] = Thread.currentThread();

         try(SlotClaim ignored = env.claimSlot()) {
            return null;
         }
      });

      assertTrue(holding.await(30, TimeUnit.SECONDS), "the take-over did not hold the home");
      return () -> {
         resume.countDown();

         try {
            take.get(30, TimeUnit.SECONDS);
         }
         catch(Exception ex) {
            throw new IllegalStateException(ex);
         }
         finally {
            PoolTestSupport.poolHook(env, "handOffHook", null);
         }
      };
   }

   // an env whose homes are all soft (maxHomes 0): another claim takes one over
   private WorksheetScriptEnv softEnv() {
      PoolConfig d = PoolConfig.defaults();
      WorksheetScriptEnv env = PoolTestSupport.env(
         new PoolConfig(d.idleMillis(), d.cleanThreshold(), d.warnSlotsPerSandbox(),
                        d.warnSlotsPerNode(), d.batchRows(), d.maxBatchRows(), 0,
                        d.maxHomesPerNode(), d.handOffMillis(), d.handOffEntries()),
         Map.of());
      envs.add(env);
      return env;
   }

   // a batch of the tenant that makes its slot the tenant's home
   private static Slot homeOf(WorksheetScriptEnv env, Tenant tenant) throws Exception {
      tenant.lock.lock();

      try(ScriptSpan batch = env.openSpan()) {
         PoolTestSupport.run(env, "1");
         OwnedValueCodec.Home home = OwnedValueCodec.homeOf(batch);
         OwnedValueCodec.enroll(home, tenant);
         return home.slot;
      }
      finally {
         tenant.lock.unlock();
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

   // a fake formula table: its batch holds its lock; a hand-off takes it without waiting
   static final class Tenant implements SlotTenant {
      @Override
      public boolean handOff(OwnedValueCodec codec) {
         if(!lock.tryLock()) {
            refused.incrementAndGet();
            return false;
         }

         try {
            handedOff.incrementAndGet();
            return true;
         }
         finally {
            lock.unlock();
         }
      }

      @Override
      public Lock handOffLock() {
         return lock;
      }

      final ReentrantLock lock = new ReentrantLock();
      final AtomicInteger handedOff = new AtomicInteger();
      final AtomicInteger refused = new AtomicInteger();
   }

   private final List<WorksheetScriptEnv> envs = new ArrayList<>();
   private final ExecutorService executor = Executors.newCachedThreadPool();
}
