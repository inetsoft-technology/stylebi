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

import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The pool and claim contract of spec §4.3-§4.5 and gates G1 (pool level), G4, G5b, N5 and N6
 * (bug #76960): checkout never waits, claims nest per thread and clean once at the outermost
 * release, retire and doom never wait and close a slot only at its 1-to-0 release.
 */
@Tag("core")
class SlotPoolTest {
   @BeforeEach
   void setUp() {
      pool = newPool(PoolConfig.defaults());
   }

   @AfterEach
   void tearDown() {
      executor.shutdownNow();
      pool.retire();
      assertEquals(0, SlotClaim.openClaims());
   }

   @Test
   void nestedClaimReusesTheSlotAndCleansOnceAtTheOuterRelease() throws Exception {
      try(SlotClaim outer = SlotClaim.acquire(pool, false)) {
         Slot slot = outer.slot();

         try(SlotClaim inner = SlotClaim.acquire(pool, false)) {
            assertSame(outer, inner);
            assertEquals(2, inner.depth());
            assertSame(slot, inner.slot());
         }

         assertEquals(0, metrics.getCleans());
      }

      assertEquals(1, metrics.getCleans());
      assertEquals(0, SlotClaim.openClaims());
   }

   @Test
   void lazyClaimChecksOutOnlyWhenUsed() {
      try(SlotClaim claim = SlotClaim.acquire(pool, true)) {
         assertNull(claim.peekSlot());
         assertEquals(256, claim.batchRows());
      }

      assertEquals(0, metrics.getCleans());
      assertEquals(0, metrics.getSize());
   }

   @Test
   void checkoutNeverWaitsForAHeldSlot() throws Exception {
      CountDownLatch held = new CountDownLatch(1);
      CountDownLatch done = new CountDownLatch(1);
      AtomicReference<Slot> first = new AtomicReference<>();
      Future<?> holder = executor.submit(() -> {
         try(SlotClaim claim = SlotClaim.acquire(pool, false)) {
            first.set(claim.slot());
            held.countDown();
            done.await(30, TimeUnit.SECONDS);
         }

         return null;
      });
      assertTrue(held.await(10, TimeUnit.SECONDS));

      Future<Slot> other = executor.submit(() -> {
         try(SlotClaim claim = SlotClaim.acquire(pool, false)) {
            assertEquals(2.0, run(claim.slot(), "1 + 1"));
            return claim.slot();
         }
      });

      assertNotSame(first.get(), other.get(10, TimeUnit.SECONDS));
      done.countDown();
      holder.get(10, TimeUnit.SECONDS);
      assertEquals(2, metrics.getHighWater());
   }

   @Test
   void checkoutReturnsPromptlyWhileThePrimaryHolderIsParked() throws Exception {
      CountDownLatch held = new CountDownLatch(1);
      CountDownLatch done = new CountDownLatch(1);
      AtomicReference<Thread> holderThread = new AtomicReference<>();
      Future<?> holder = executor.submit(() -> {
         try(SlotClaim claim = SlotClaim.acquire(pool, false)) {
            assertSame(pool.primary(), claim.slot());
            holderThread.set(Thread.currentThread());
            held.countDown();
            done.await(30, TimeUnit.SECONDS);
         }

         return null;
      });
      assertTrue(held.await(10, TimeUnit.SECONDS));
      Slot primary = pool.primary();

      // the primary's holder is parked, not running: a waiting checkout would hang here
      Thread.State state;

      do {
         state = holderThread.get().getState();
      }
      while(state != Thread.State.TIMED_WAITING && state != Thread.State.WAITING);

      Future<Slot> other = executor.submit(() -> {
         Slot slot = pool.checkout();
         pool.release(slot);
         return slot;
      });

      Slot taken = other.get(10, TimeUnit.SECONDS);
      assertNotSame(primary, taken);
      assertSame(primary, pool.primary(), "the parked holder keeps the primary");
      assertFalse(primary.isClosed());
      done.countDown();
      holder.get(10, TimeUnit.SECONDS);
   }

   @Test
   void failureInsideAClaimLeavesNoClaimAndAReusableSlot() {
      Slot first;

      try(SlotClaim claim = SlotClaim.acquire(pool, false)) {
         first = claim.slot();
      }

      assertThrows(IllegalStateException.class, () -> {
         try(SlotClaim claim = SlotClaim.acquire(pool, false)) {
            claim.slot();
            throw new IllegalStateException("boom");
         }
      });
      assertEquals(0, SlotClaim.openClaims());

      try(SlotClaim claim = SlotClaim.acquire(pool, false)) {
         assertSame(first, claim.slot());
      }
   }

   @Test
   void retireWhileInUseNeverWaitsAndClosesAtRelease() throws Exception {
      CountDownLatch held = new CountDownLatch(1);
      CountDownLatch done = new CountDownLatch(1);
      AtomicReference<Slot> slot = new AtomicReference<>();
      Future<?> holder = executor.submit(() -> {
         try(SlotClaim claim = SlotClaim.acquire(pool, false)) {
            slot.set(claim.slot());
            held.countDown();
            done.await(30, TimeUnit.SECONDS);
            assertEquals(2.0, run(claim.slot(), "1 + 1"));
         }

         return null;
      });
      assertTrue(held.await(10, TimeUnit.SECONDS));

      long start = System.nanoTime();
      pool.retire();
      assertTrue(System.nanoTime() - start < TimeUnit.SECONDS.toNanos(1), "retire waited");
      assertFalse(slot.get().isClosed());

      done.countDown();
      holder.get(10, TimeUnit.SECONDS);
      assertTrue(slot.get().isClosed());
      assertEquals(1, metrics.getDoomedCloses());

      try(SlotClaim claim = SlotClaim.acquire(pool, false)) {
         assertNotSame(slot.get(), claim.slot());
      }
   }

   @Test
   void retireFromTheOwnNestedClaimClosesOnlyAtTheOuterRelease() throws Exception {
      Slot slot;

      try(SlotClaim outer = SlotClaim.acquire(pool, false)) {
         slot = outer.slot();

         try(SlotClaim inner = SlotClaim.acquire(pool, false)) {
            pool.retire();
            assertFalse(slot.isClosed());
            assertEquals(2.0, run(inner.slot(), "1 + 1"));
         }

         assertFalse(slot.isClosed());
         assertEquals(2.0, run(outer.slot(), "1 + 1"));
      }

      assertTrue(slot.isClosed());
   }

   @Test
   void interruptTimeoutInANestedClaimDoomsAndClosesAtTheOuterRelease() throws Exception {
      Slot slot;

      try(SlotClaim outer = SlotClaim.acquire(pool, false)) {
         slot = outer.slot();

         try(SlotClaim inner = SlotClaim.acquire(pool, false)) {
            inner.slot().engine().onInterruptTimeout();
            assertTrue(slot.isDoomed());
         }

         assertFalse(slot.isClosed());
      }

      assertTrue(slot.isClosed());
      assertEquals(1, metrics.getDoomedCloses());
      assertEquals(0, metrics.getCleans(), "a doomed context is not cleaned");
   }

   @Test
   void evictorRacingCheckoutNeverHandsOutAClosedSlot() throws Exception {
      pool = newPool(new PoolConfig(0L, 256, 16, 2000, 256));
      CountDownLatch go = new CountDownLatch(1);
      // two users, so pooled contexts exist for the evictor to race with
      Callable<Integer> use = () -> {
         go.await();
         int ok = 0;

         for(int i = 0; i < 300; i++) {
            try(SlotClaim claim = SlotClaim.acquire(pool, false)) {
               if(Double.valueOf(2.0).equals(run(claim.slot(), "1 + 1"))) {
                  ok++;
               }
            }
         }

         return ok;
      };
      Future<Integer> user1 = executor.submit(use);
      Future<Integer> user2 = executor.submit(use);
      Future<?> evictor = executor.submit(() -> {
         go.await();

         while(!user1.isDone() || !user2.isDone()) {
            pool.evictIdle(Long.MAX_VALUE);
         }

         return null;
      });

      go.countDown();
      assertEquals(300, user1.get(60, TimeUnit.SECONDS));
      assertEquals(300, user2.get(60, TimeUnit.SECONDS));
      evictor.get(10, TimeUnit.SECONDS);
      assertNotNull(pool.primary(), "the evictor never evicts the primary");
   }

   @Test
   void staleEpochPrimaryIsReplaced() {
      Slot first;

      try(SlotClaim claim = SlotClaim.acquire(pool, false)) {
         first = claim.slot();
      }

      pool.retire();
      assertTrue(first.isClosed());

      try(SlotClaim claim = SlotClaim.acquire(pool, false)) {
         assertNotSame(first, claim.slot());
         assertSame(claim.slot(), pool.primary());
      }
   }

   @Test
   void leakedClaimIsReleasedAtThreadEnd() throws Exception {
      Future<Slot> leaker = executor.submit(() -> {
         Slot slot = SlotClaim.acquire(pool, false).slot();
         SlotClaim.releaseLeaked("test task");
         assertEquals(0, SlotClaim.openClaims());
         return slot;
      });
      Slot slot = leaker.get(10, TimeUnit.SECONDS);

      try(SlotClaim claim = SlotClaim.acquire(pool, false)) {
         assertSame(slot, claim.slot(), "the leaked slot was returned to the pool");
      }
   }

   @Test
   void poolsNeverShareSlots() {
      SlotPool other = newPool(PoolConfig.defaults());

      try(SlotClaim a = SlotClaim.acquire(pool, false);
          SlotClaim b = SlotClaim.acquire(other, false))
      {
         assertNotSame(a.slot(), b.slot());
         assertNotSame(a.slot().engine(), b.slot().engine());
      }
      finally {
         other.retire();
      }
   }

   private SlotPool newPool(PoolConfig config) {
      metrics = new PoolMetrics();
      state = new EnvState();
      PoolMetrics poolMetrics = metrics;
      EnvState poolState = state;
      return new SlotPool(new SlotSource() {
         @Override
         public EnvState state() {
            return poolState;
         }

         @Override
         public boolean isSQL() {
            return false;
         }

         @Override
         public Slot create(long epoch) throws Exception {
            return Slot.create(new InitSnapshot("org0", Map.of()), poolState.snapshot(), epoch,
                               false, Collections.synchronizedMap(new WeakHashMap<>()), poolMetrics);
         }
      }, config, poolMetrics);
   }

   static Object run(Slot slot, String js) throws Exception {
      WsEngine engine = slot.engine();
      return engine.exec(engine.compile(js), null, null);
   }

   private final ExecutorService executor = Executors.newCachedThreadPool();
   private SlotPool pool;
   private PoolMetrics metrics;
   private EnvState state;
}
