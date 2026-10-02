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
package inetsoft.util.swap;

import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77579: XSwapper.doGC() runs a full collection, so it is throttled. The throttle state
 * (atomics) is shared between the swapper bean and each spy, so every test moves the fake
 * clock far past anything an earlier test or the bean's own threads could have stored.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome()
@Tag("core")
class XSwapperGCThrottleTest {
   @Test
   void sweepBacksOffWhileCriticalAndWaiterBypassesIt() {
      final AtomicInteger gcs = new AtomicInteger();
      final XSwapper swapper = createSwapper(gcs, 0L);
      // memory is really full, so a collection doesn't help
      doReturn(XSwapper.CRITICAL_MEM).when(swapper).getMemoryState();

      assertTrue(swapper.doGC(false), "first collection was throttled");
      assertFalse(swapper.doGC(false), "collection inside the interval was not throttled");
      assertEquals(1, gcs.get());

      // still critical, so the sweep backed off to 20s
      advance(15000L);
      assertFalse(swapper.doGC(false), "back-off was not applied");
      // a waiter honors only the 10s spacing; the back-off is now 40s
      assertTrue(swapper.doGC(true), "waiter was held off by the back-off");
      assertEquals(2, gcs.get());

      // memory recovers after the next collection, which resets the back-off
      doReturn(XSwapper.GOOD_MEM).when(swapper).getMemoryState();
      advance(15000L);
      assertFalse(swapper.doGC(false), "back-off was not applied");
      assertTrue(swapper.doGC(true), "waiter was held off by the back-off");
      advance(10000L);
      assertTrue(swapper.doGC(false), "back-off was not reset after memory recovered");
      assertEquals(4, gcs.get());
   }

   /**
    * Bug #77591: requestGC(), which DataCacheSweeper calls, shares the sweep's throttle and
    * back-off, so it can't add collections on top of the swapper's own.
    */
   @Test
   void requestGCSharesThrottleAndBackOff() {
      final AtomicInteger gcs = new AtomicInteger();
      final XSwapper swapper = createSwapper(gcs, 0L);
      doReturn(XSwapper.CRITICAL_MEM).when(swapper).getMemoryState();

      // the swapper's own sweep just collected
      assertTrue(swapper.doGC(false));
      assertFalse(swapper.requestGC(), "request inside the interval was not throttled");
      assertEquals(1, gcs.get());

      // still critical, so the back-off (20s) holds off the request, but not a waiter
      advance(15000L);
      assertFalse(swapper.requestGC(), "back-off was not applied to the request");
      assertTrue(swapper.doGC(true));
      assertEquals(2, gcs.get());

      // a request that runs claims the slot for every other caller
      doReturn(XSwapper.GOOD_MEM).when(swapper).getMemoryState();
      advance(40000L);
      assertTrue(swapper.requestGC(), "request was throttled past the back-off");
      assertFalse(swapper.doGC(true), "request did not claim the shared slot");
      assertEquals(3, gcs.get());
   }

   @Test
   void spacingScalesWithPauseForWaitersToo() {
      final AtomicInteger gcs = new AtomicInteger();
      // every collection takes 2s, so collections must be at least 40s apart
      final XSwapper swapper = createSwapper(gcs, 2000L);
      doReturn(XSwapper.CRITICAL_MEM).when(swapper).getMemoryState();
      final long start = time.get();

      assertTrue(swapper.doGC(true));
      time.set(start + 30000L);
      assertFalse(swapper.doGC(true), "waiter collected faster than 20x the pause");
      time.set(start + 40000L);
      assertTrue(swapper.doGC(true), "waiter was held off past 20x the pause");
      assertEquals(2, gcs.get());
   }

   @Test
   void concurrentCallersRunOneCollection() throws Exception {
      final AtomicInteger gcs = new AtomicInteger();
      final XSwapper swapper = createSwapper(gcs, 0L);
      doReturn(XSwapper.GOOD_MEM).when(swapper).getMemoryState();
      final int count = 8;
      final CountDownLatch start = new CountDownLatch(1);
      final CountDownLatch done = new CountDownLatch(count);
      final AtomicInteger ran = new AtomicInteger();

      for(int i = 0; i < count; i++) {
         Thread thread = new Thread(() -> {
            try {
               start.await();

               if(swapper.doGC(true)) {
                  ran.incrementAndGet();
               }
            }
            catch(InterruptedException ignore) {
            }
            finally {
               done.countDown();
            }
         });
         thread.setDaemon(true);
         thread.start();
      }

      start.countDown();
      assertTrue(done.await(10, TimeUnit.SECONDS));
      assertEquals(1, ran.get(), "concurrent callers stacked collections");
      assertEquals(1, gcs.get());
   }

   /**
    * A thread waiting in the real waitForMemory() collects on its first pass, even while the
    * sweep's back-off is primed, and returns as soon as the collection frees enough memory,
    * instead of waiting out swapper.critical.max.wait.
    */
   @Test
   void waiterCollectsOnceAndReturnsWithBackOffPrimed() throws Exception {
      final AtomicInteger gcs = new AtomicInteger();
      final XSwapper swapper = createSwapper(gcs, 0L);
      doReturn(XSwapper.CRITICAL_MEM).when(swapper).getMemoryState();

      // prime the back-off: a critical sweep collection backs off to 20s
      assertTrue(swapper.doGC(false));
      advance(15000L);
      assertFalse(swapper.doGC(false), "back-off is not primed");
      assertEquals(1, gcs.get());

      // garbage now: memory is critical until a collection runs
      doAnswer(inv -> gcs.get() > 1 ? XSwapper.GOOD_MEM : XSwapper.CRITICAL_MEM)
         .when(swapper).getMemoryState();
      swapper.setMaxCriticalWait(10000L);

      final long[] elapsed = { -1L };
      Thread caller = new Thread(() -> {
         long start = System.currentTimeMillis();
         swapper.waitForMemory();
         elapsed[0] = System.currentTimeMillis() - start;
      });

      caller.setDaemon(true);
      caller.start();
      caller.join(30000L);

      assertTrue(elapsed[0] >= 0, "waitForMemory() did not return within 30s");
      assertTrue(elapsed[0] < 2000L, "waiter did not recover after a collection: " +
         elapsed[0] + "ms");
      assertEquals(2, gcs.get(), "waiter did not run exactly one collection");
   }

   /**
    * The real runGC() goes through the DiagnosticCommand MBean, which -XX:+DisableExplicitGC
    * does not block, and actually collects.
    */
   @Test
   void runGCUsesDiagnosticCommand() {
      final XSwapper swapper = XSwapper.getSwapper();
      final long before = getGCCount();

      swapper.runGC();

      assertTrue(getGCCount() > before, "no collection ran");
      assertTrue(swapper.isLastGCDiagnostic(), "fell back to System.gc()");
   }

   private static XSwapper createSwapper(AtomicInteger gcs, long pause) {
      final XSwapper swapper = spy(XSwapper.getSwapper());
      doAnswer(inv -> {
         gcs.incrementAndGet();
         time.addAndGet(pause);
         return null;
      }).when(swapper).runGC();
      swapper.setGCMinInterval(10000L);
      // far past any slot a previous test or the bean's threads claimed
      advance(1_000_000_000L);
      swapper.clock = time::get;
      return swapper;
   }

   private static void advance(long millis) {
      time.addAndGet(millis);
   }

   private static long getGCCount() {
      long count = 0;

      for(GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
         count += Math.max(bean.getCollectionCount(), 0);
      }

      return count;
   }

   private static final AtomicLong time =
      new AtomicLong(System.currentTimeMillis() + 1_000_000_000L);
}
