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

import inetsoft.util.script.LendableReentrantLock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Slot lifecycle (bug #76960). Spec N6 and §9: an interrupt that could not stop an exec only
 * dooms its slot; the Context stays open under the frame that is still running, and the slot
 * closes only when its lock holder closes it. A closed slot is never taken again.
 */
@Tag("core")
class SlotLifecycleTest {
   @AfterEach
   void closeSlot() {
      if(slot != null && !slot.isClosed()) {
         slot.close();
         slot.unlock();
      }
   }

   @Test
   void interruptTimeoutDoomsButNeverClosesTheContext() throws Exception {
      slot = newSlot();
      WsEngine engine = slot.engine();

      assertFalse(slot.isDoomed());
      engine.onInterruptTimeout();

      assertTrue(slot.isDoomed());
      assertFalse(slot.isClosed(), "a doom must not close a context under an active frame");
      assertEquals(2.0, engine.exec(engine.compile("1 + 1"), null, null));
   }

   @Test
   void closedSlotIsNeverAcquiredAgain() throws Exception {
      slot = newSlot();
      slot.doom();
      slot.close();
      slot.unlock();

      ExecutorService other = Executors.newSingleThreadExecutor();

      try {
         assertFalse(other.submit(slot::tryAcquire).get(10, TimeUnit.SECONDS));
      }
      finally {
         other.shutdownNow();
      }

      assertFalse(slot.tryAcquire());
   }

   @Test
   void heldSlotIsNotAcquiredByAnotherThreadOrReentered() throws Exception {
      slot = newSlot();
      assertTrue(slot.isHeldByCurrentThread(), "create returns the slot locked by its creator");
      assertFalse(slot.tryAcquire(), "the holder does not take its own slot twice");

      ExecutorService other = Executors.newSingleThreadExecutor();

      try {
         assertFalse(other.submit(slot::tryAcquire).get(10, TimeUnit.SECONDS));
         slot.release();
         assertTrue(other.submit(() -> {
            boolean taken = slot.tryAcquire();

            if(taken) {
               slot.unlock();
            }

            return taken;
         }).get(10, TimeUnit.SECONDS));
      }
      finally {
         other.shutdownNow();
      }

      assertTrue(slot.tryAcquire());
   }

   /**
    * A removed variable must not leave a cached "is a global" answer behind, which would hide
    * the case-insensitive CALC function of the same name.
    */
   @Test
   void removedOwnVariableUncoversTheCalcFunction() throws Exception {
      slot = newSlot();
      WsEngine engine = slot.engine();
      String fresh = String.valueOf(engine.exec(engine.compile("typeof SUM"), null, null));
      slot.applyOwn("SUM", 5);
      assertEquals(5.0, engine.exec(engine.compile("SUM"), null, null));
      slot.removeOwn("SUM");
      assertEquals(fresh, engine.exec(engine.compile("typeof SUM"), null, null), "fresh=" + fresh);
   }

   @Test
   void closeByANonHolderIsRejected() throws Exception {
      slot = newSlot();
      ExecutorService other = Executors.newSingleThreadExecutor();

      try {
         ExecutionException ex = assertThrows(ExecutionException.class,
            () -> other.submit(slot::close).get(10, TimeUnit.SECONDS));
         assertInstanceOf(IllegalStateException.class, ex.getCause());
      }
      finally {
         other.shutdownNow();
      }

      assertFalse(slot.isClosed(), "a rejected close must leave the slot open");
      assertEquals(2.0, slot.engine().exec(slot.engine().compile("1 + 1"), null, null));
   }

   /**
    * A borrower of the holder's lent lock may lock it, but must not take the slot as a second
    * claim.
    */
   @Test
   void borrowerOfALentLockDoesNotAcquireTheSlot() throws Exception {
      slot = newSlot();
      LendableReentrantLock lock = slot.engine().getExecutionLock();
      LendableReentrantLock.Borrower borrower = new LendableReentrantLock.Borrower();
      ExecutorService other = Executors.newSingleThreadExecutor();

      try(LendableReentrantLock.Loan loan = lock.lend(borrower)) {
         Future<boolean[]> result = other.submit(() -> {
            borrower.begin();

            try {
               boolean taken = slot.tryAcquire();
               boolean stillFree = !lock.isHeldByCurrentThread();

               if(taken) {
                  slot.unlock();
               }

               return new boolean[] { taken, stillFree };
            }
            finally {
               borrower.end();
            }
         });

         boolean[] r = result.get(10, TimeUnit.SECONDS);
         assertFalse(r[0], "a borrower must not claim the slot");
         assertTrue(r[1], "a refused tryAcquire must not leave the lock held");
      }
      finally {
         other.shutdownNow();
      }

      assertTrue(slot.isHeldByCurrentThread(), "the loan returns the lock to the holder");
   }

   private static Slot newSlot() throws Exception {
      return Slot.create(new InitSnapshot("org0", Map.of()), new EnvState().snapshot(), 0L, false,
                         Collections.synchronizedMap(new WeakHashMap<>()), new PoolMetrics());
   }

   private Slot slot;
}
