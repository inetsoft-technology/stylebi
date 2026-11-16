/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.report.internal.paging;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #81287: concurrent puts of one report id must be serialized, and the lock table must not
 * leak entries.
 */
@Tag("core")
class ReportIdLocksTest {
   @Test
   void sameIdIsMutuallyExclusive() throws Exception {
      ReportIdLocks locks = new ReportIdLocks();
      AtomicInteger inside = new AtomicInteger();
      AtomicInteger maxInside = new AtomicInteger();
      CountDownLatch done = new CountDownLatch(4);

      for(int i = 0; i < 4; i++) {
         new Thread(() -> {
            assertTrue(locks.acquire("r", 30));

            try {
               maxInside.accumulateAndGet(inside.incrementAndGet(), Math::max);
               Thread.sleep(20);
               inside.decrementAndGet();
            }
            catch(InterruptedException ignore) {
            }
            finally {
               locks.release("r");
               done.countDown();
            }
         }).start();
      }

      done.await();
      assertEquals(1, maxInside.get());
      assertEquals(0, locks.size());
   }

   @Test
   void differentIdsDoNotBlock() {
      ReportIdLocks locks = new ReportIdLocks();
      assertTrue(locks.acquire("a", 1));
      assertTrue(locks.acquire("b", 1));
      locks.release("a");
      locks.release("b");
      assertEquals(0, locks.size());
   }

   @Test
   void timeoutReturnsFalseAndLeavesNoEntry() throws Exception {
      ReportIdLocks locks = new ReportIdLocks();
      assertTrue(locks.acquire("a", 1));
      boolean[] result = new boolean[1];
      Thread t = new Thread(() -> result[0] = locks.acquire("a", 1));
      t.start();
      t.join();
      assertFalse(result[0]);
      locks.release("a");
      assertEquals(0, locks.size());
   }
}
