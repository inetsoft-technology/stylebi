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

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * An {@link Error} (not only a RuntimeException) in a checkout's prepare or in the evictor
 * must not leak a locked context or stop the idle eviction (finding FM1, Testing #77123).
 */
@Tag("core")
class SlotPoolErrorTest {
   @BeforeEach
   void setUp() {
      metrics = new PoolMetrics();
      state = new EnvState();
      failSql = new AtomicInteger();
      pool = new SlotPool(source(), new PoolConfig(2000L, 256, 16, 2000, 256, 8192), metrics);
   }

   @AfterEach
   void tearDown() {
      pool.retire();
   }

   @Test
   void errorInPrepareDiscardsTheSlotAndUnlocksIt() throws Exception {
      Slot warm;

      try(SlotClaim claim = SlotClaim.acquire(pool, false)) {
         warm = claim.slot();
      }

      int baseline = metrics.getSize();
      assertEquals(1, baseline);
      failSql.set(1);

      assertThrows(AssertionError.class, () -> SlotClaim.acquire(pool, false));

      assertTrue(warm.isClosed(), "the slot whose prepare failed is closed");
      assertFalse(warm.isHeldByCurrentThread(), "and no longer locked by this thread");
      assertNull(pool.primary(), "a failed primary is dropped so the next checkout replaces it");
      assertEquals(0, metrics.getSize());
      assertEquals(0, SlotClaim.openClaims());

      try(SlotClaim claim = SlotClaim.acquire(pool, false)) {
         assertNotSame(warm, claim.slot());
         assertEquals(2.0, ((Number) SlotPoolTest.run(claim.slot(), "1 + 1")).doubleValue());
         assertEquals(baseline, metrics.getSize());
      }
   }

   /**
    * Final review M9: an Error from the context's close while a slot is discarded (here in the
    * prepare-Error path) must still unlock the slot.
    */
   @Test
   void errorInCloseStillUnlocksTheDiscardedSlot() throws Exception {
      Slot warm;

      try(SlotClaim claim = SlotClaim.acquire(pool, false)) {
         warm = claim.slot();
      }

      Field field = Slot.class.getDeclaredField("engine");
      field.setAccessible(true);
      WsEngine real = (WsEngine) field.get(warm);
      WsEngine engine = spy(real);
      doThrow(new AssertionError("injected close")).when(engine).close();
      field.set(warm, engine);

      try {
         failSql.set(1);
         assertThrows(AssertionError.class, () -> SlotClaim.acquire(pool, false));

         assertTrue(warm.isClosed(), "the slot whose prepare failed is closed");
         assertFalse(warm.isHeldByCurrentThread(), "and unlocked although its close threw");
         assertNull(pool.primary());
         assertEquals(0, SlotClaim.openClaims());

         try(SlotClaim claim = SlotClaim.acquire(pool, false)) {
            assertNotSame(warm, claim.slot());
            assertEquals(2.0, ((Number) SlotPoolTest.run(claim.slot(), "1 + 1")).doubleValue());
         }
      }
      finally {
         while(warm.isHeldByCurrentThread()) {
            warm.unlock();
         }

         real.close();
      }
   }

   @Test
   void errorInTheEvictorDoesNotStopLaterEvictions() throws Exception {
      // a pool creating a context starts its periodic eviction (period 1s at idleMillis 2s)
      try(SlotClaim claim = SlotClaim.acquire(pool, false)) {
         assertNotNull(claim.slot());
      }

      Slot bomb = mock(Slot.class);
      AtomicInteger runs = new AtomicInteger();
      when(bomb.epoch()).thenAnswer(inv -> {
         runs.incrementAndGet();
         throw new AssertionError("injected");
      });
      Field field = SlotPool.class.getDeclaredField("pooled");
      field.setAccessible(true);
      @SuppressWarnings("unchecked")
      Set<Slot> pooled = (Set<Slot>) field.get(pool);
      pooled.add(bomb);

      try {
         long deadline = System.currentTimeMillis() + 10_000L;

         while(runs.get() < 2 && System.currentTimeMillis() < deadline) {
            Thread.sleep(100L);
         }

         assertTrue(runs.get() >= 2,
                    "eviction keeps being scheduled after an Error, runs=" + runs.get());
      }
      finally {
         pooled.remove(bomb);
      }
   }

   private SlotSource source() {
      return new SlotSource() {
         @Override
         public EnvState state() {
            return state;
         }

         @Override
         public boolean isSQL() {
            if(failSql.get() > 0 && failSql.decrementAndGet() >= 0) {
               throw new AssertionError("injected");
            }

            return false;
         }

         @Override
         public Slot create(long epoch) throws Exception {
            return Slot.create(new InitSnapshot("org0", Map.of()), state.snapshot(), epoch,
                               false, Collections.synchronizedMap(new WeakHashMap<>()), metrics);
         }
      };
   }

   private PoolMetrics metrics;
   private EnvState state;
   private AtomicInteger failSql;
   private SlotPool pool;
}
