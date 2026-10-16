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
import inetsoft.util.GroupedThread;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
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
    * Bug #78106: the post-sweep trigger in XSwapperThread.doRun() forces a collection only when
    * memory is genuinely CRITICAL_MEM, not merely BAD_MEM as before, even though the sweep still
    * swaps objects out at BAD_MEM. This drives a real XSwapperThread (via the same reflection
    * approach as XSwapperThreadWaitTest, Bug #77682) because the trigger condition lives inline
    * in the thread's private doRun() loop, with no extracted method a test can call directly.
    */
   @Test
   void postSweepTriggerFiresOnlyAtCriticalMem() throws Exception {
      final AtomicInteger gcs = new AtomicInteger();
      final XSwapper swapper = spy(new XSwapper());
      doAnswer(inv -> {
         gcs.incrementAndGet();
         return null;
      }).when(swapper).runGC();
      swapper.setGCMinInterval(10000L);

      // more than 3 swappables, so doRun() doesn't skip swapping outright: it continues without
      // swapping when swaplist.size() <= 3 and state is not CRITICAL_MEM.
      AlwaysSwappable[] candidates = {
         new AlwaysSwappable(), new AlwaysSwappable(), new AlwaysSwappable(), new AlwaysSwappable()
      };
      GroupedThread thread = createSweepThread(swapper, candidates);
      setCachedState(swapper, XSwapper.BAD_MEM);
      thread.start();

      try {
         long end = System.currentTimeMillis() + 5000;

         while(totalSwaps(candidates) == 0 && System.currentTimeMillis() < end) {
            Thread.sleep(50);
         }

         assertTrue(totalSwaps(candidates) > 0, "swapper thread never swapped at BAD_MEM");
         // give the thread a few more sweep passes a chance to (wrongly) force a collection
         Thread.sleep(1500L);
         assertEquals(0, gcs.get(), "a forced collection ran for a BAD_MEM sweep (the #78106 " +
            "fix narrows the post-sweep trigger to CRITICAL_MEM only)");

         // the same post-sweep path must still force a collection once memory is genuinely
         // critical
         setCachedState(swapper, XSwapper.CRITICAL_MEM);
         end = System.currentTimeMillis() + 5000;

         while(gcs.get() == 0 && System.currentTimeMillis() < end) {
            Thread.sleep(50);
         }

         assertEquals(1, gcs.get(), "no forced collection ran for a CRITICAL_MEM sweep");
      }
      finally {
         thread.cancel();
         thread.join(15000L);
         swapper.stop();
      }
   }

   /**
    * Bug #78106 review round 1: a forced collection whose pause exceeds swapper.gc.safe.pause
    * logs a WARN for operator visibility, but must NOT additionally escalate the non-waiting
    * back-off beyond what a genuinely critical memory state already produces. An earlier version
    * of this fix escalated gcBackoff on "critical || dangerousPause", but that branch is
    * mathematically inert at the shipped 5000ms default: any pause large enough to cross it also
    * makes the pre-existing spacing formula (20x the last pause, see the class doc on
    * doGC(boolean)) exceed MAX_GC_BACKOFF on its own, so the escalation could never be the value
    * that actually governs scheduling. This test proves the dangerous-pause branch is gone by
    * showing that, with memory recovered (non-critical) after the collection, a dangerous pause
    * produces exactly the same next-call timing as a critical-free collection always has: the
    * non-waiting call is allowed again as soon as the plain spacing interval elapses, with no
    * extra hold-off contributed by the pause alone. The test lowers the safe-pause threshold
    * (the same test-override pattern as getGCMinInterval()/setGCMinInterval()) so a short,
    * cheap-to-run pause can be flagged "dangerous" while keeping the spacing component small
    * enough for the timing assertion below to be meaningful.
    */
   @Test
   void dangerousPauseLogsWarningButDoesNotEscalateBackoff() {
      final AtomicInteger gcs = new AtomicInteger();
      // every collection takes 200ms, so plain spacing (20x the pause = 4000ms, under the 10s
      // base) allows the next non-waiting call at +10000ms; "dangerous" only against the
      // lowered 50ms test threshold below
      final XSwapper swapper = createSwapper(gcs, 200L);
      swapper.setGCSafePause(50L);

      try {
         // memory recovers after the collection, so a critical reading can't explain the
         // timing below -- only the (removed) dangerous-pause escalation could
         doReturn(XSwapper.GOOD_MEM).when(swapper).getMemoryState();

         assertTrue(swapper.doGC(false), "first collection was throttled");
         assertEquals(1, gcs.get());

         // if the dangerous-pause escalation still ran, gcBackoff would have been set to 20s and
         // this call -- 1ms past the plain 10s spacing -- would still be held off
         advance(10001L);
         assertTrue(swapper.doGC(false),
            "a dangerous pause with non-critical memory held off a non-waiting call past plain " +
               "spacing (the inert gcBackoff escalation was supposed to be removed)");
         assertEquals(2, gcs.get());

         // a waiting caller bypasses gcBackoff entirely and only honors spacing; unaffected by
         // this change either way, kept here as a regression guard for #77579
         advance(10001L);
         assertTrue(swapper.doGC(true),
            "waiting caller was delayed beyond plain spacing (would regress #77579)");
         assertEquals(3, gcs.get());
      }
      finally {
         swapper.setGCSafePause(-1L);
      }
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

   /**
    * Build a real (not spied for this part) XSwapperThread with the given swappables already
    * registered, the same way XSwapperThreadWaitTest (Bug #77682) drives the private inner class.
    */
   private static GroupedThread createSweepThread(XSwapper swapper, XSwappable... swappables)
      throws Exception
   {
      Class<?> threadClass = Class.forName(XSwapper.class.getName() + "$XSwapperThread");
      Constructor<?> constructor = Arrays.stream(threadClass.getDeclaredConstructors())
         .filter(c -> c.getParameterCount() > 0 && c.getParameterTypes()[0] == XSwapper.class)
         .findFirst()
         .orElseThrow();
      constructor.setAccessible(true);
      GroupedThread thread = (GroupedThread) constructor.newInstance(swapper);
      Method register = threadClass.getDeclaredMethod("register", XSwappable.class);
      register.setAccessible(true);

      for(XSwappable swappable : swappables) {
         register.invoke(thread, swappable);
      }

      return thread;
   }

   /**
    * Force getMemoryState() to return a fixed state without recomputing it, the same way
    * XSwapperThreadWaitTest's setState() does.
    */
   private static void setCachedState(XSwapper swapper, int memState) throws Exception {
      Field state = XSwapper.class.getDeclaredField("cachedState");
      state.setAccessible(true);
      state.setInt(swapper, memState);
      Field ts = XSwapper.class.getDeclaredField("stateTS");
      ts.setAccessible(true);
      ts.setLong(swapper, Long.MAX_VALUE);
   }

   private static int totalSwaps(AlwaysSwappable[] candidates) {
      int total = 0;

      for(AlwaysSwappable candidate : candidates) {
         total += candidate.swaps.get();
      }

      return total;
   }

   /**
    * A swappable with a real (non-zero) swap priority that always reports itself as swappable and
    * counts every swap() call.
    */
   private static final class AlwaysSwappable extends XSwappable {
      @Override
      public double getSwapPriority() {
         return 10;
      }

      @Override
      public boolean isCompleted() {
         return true;
      }

      @Override
      public boolean isSwappable() {
         return true;
      }

      @Override
      public boolean isValid() {
         return true;
      }

      @Override
      public boolean swap() {
         swaps.incrementAndGet();
         return true;
      }

      @Override
      public void dispose() {
      }

      private final AtomicInteger swaps = new AtomicInteger();
   }

   private static final AtomicLong time =
      new AtomicLong(System.currentTimeMillis() + 1_000_000_000L);
}
