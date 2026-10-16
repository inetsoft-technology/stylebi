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
package inetsoft.util;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78236: every constructor that seeded a {@code GroupedThread}'s or
 * {@code ThreadPool.AbstractContextRunnable}'s own "created" stack trace called
 * {@code Thread.currentThread().getStackTrace()}. When the current thread is itself a
 * {@code GroupedThread} -- true for every {@code ThreadPool} worker -- that call dispatches to
 * {@link GroupedThread#getStackTrace()}'s override, which returns an array already concatenated
 * from the real native frames plus the thread's own created/parent trace fields. Storing that
 * already-concatenated array back in as "this call's own frames" duplicated ancestor history at
 * every level of nested {@code ThreadPool} task creation, growing geometrically (~1.618x per
 * level for the plain {@code AbstractContextRunnable} path that {@code AssetDataCache} and
 * {@code SummaryFilter} use) instead of staying flat.
 *
 * <p>This test submits a real {@link ThreadPool.AbstractContextRunnable} recursively, through a
 * real single-worker {@link ThreadPool} queue -- not a hand-simulation of the field-seeding logic
 * -- exactly reproducing how {@code ThreadPool.WorkerThread.run0()} seeds a reused worker's
 * created/parent stack-trace fields from each dequeued task in production, and asserts the
 * recorded {@code getStackTrace()}/{@code getParentStackTrace()} lengths stay flat/bounded across
 * levels instead of growing geometrically.</p>
 */
@Tag("core")
class ThreadPoolStackTraceGrowthTest {
   @Test
   void nestedAbstractContextRunnableStackTraceStaysBounded() throws Exception {
      final int levels = 20;
      final StackTraceElement[][] ownTraces = new StackTraceElement[levels + 1][];
      final StackTraceElement[][] parentTraces = new StackTraceElement[levels + 1][];
      final CountDownLatch done = new CountDownLatch(1);
      final ThreadPool pool = new ThreadPool(1, 1, "bug78236-growth-test-");

      try {
         submitLevel(pool, 1, levels, ownTraces, parentTraces, done);

         assertTrue(done.await(30, TimeUnit.SECONDS),
                    "recursive AbstractContextRunnable chain did not finish in time");

         for(int i = 1; i <= levels; i++) {
            assertNotNull(ownTraces[i], "level " + i + " own stack trace was never recorded");
            assertNotNull(parentTraces[i], "level " + i + " parent stack trace was never recorded");
         }

         int lastOwn = ownTraces[levels].length;
         int lastParent = parentTraces[levels].length;

         // After the fix, GroupedThread.getStackTrace()'s override is never fed back into its own
         // "created" field, so every AbstractContextRunnable created on the reused pool worker
         // sees essentially the same small own/parent trace length at every level, rather than
         // the pre-fix Fibonacci-shaped growth (ratio -> golden ratio ~1.618/level). Compare the
         // last few levels rather than asserting exact equality, to tolerate incidental +/- a
         // few frames of jitter while still catching any real geometric growth, which would move
         // lengths by hundreds to thousands of elements per level at this depth.
         int flatnessDelta = 20;

         for(int i = levels - 5; i <= levels; i++) {
            assertTrue(Math.abs(ownTraces[i].length - lastOwn) <= flatnessDelta,
                       "own stack trace length grew from " + ownTraces[i].length + " (level " +
                       i + ") to " + lastOwn + " (level " + levels + "); expected flat growth " +
                       "after the bug #78236 fix");
            assertTrue(Math.abs(parentTraces[i].length - lastParent) <= flatnessDelta,
                       "parent stack trace length grew from " + parentTraces[i].length +
                       " (level " + i + ") to " + lastParent + " (level " + levels + "); " +
                       "expected flat growth after the bug #78236 fix");
         }

         // Well within the pre-existing 1e5 cap and nowhere near the un-fixed geometric growth,
         // which (per the diagnosed ~1.618x/level recurrence) already reaches several thousand
         // elements by level 12-13 and crosses the 1e5 cap by level 17.
         assertTrue(lastOwn < 1000,
                    "own stack trace length " + lastOwn + " at level " + levels + " suggests " +
                    "unbounded/geometric growth across nested ThreadPool tasks (bug #78236 " +
                    "regression)");
         assertTrue(lastParent < 1000,
                    "parent stack trace length " + lastParent + " at level " + levels +
                    " suggests unbounded/geometric growth across nested ThreadPool tasks " +
                    "(bug #78236 regression)");
      }
      finally {
         pool.dispose();
      }
   }

   /**
    * Submits level {@code level} of the recursive chain to {@code pool}. Each level's
    * {@code run()} records its own and parent stack-trace lengths and then -- while still running
    * on the pool's single worker thread, exactly as {@code ThreadPool.WorkerThread.run0()} seeds
    * a worker's created/parent trace fields from each dequeued task in production -- submits the
    * next level, or counts down {@code done} once the last level has run.
    */
   private static void submitLevel(ThreadPool pool, int level, int maxLevel,
                                    StackTraceElement[][] ownTraces,
                                    StackTraceElement[][] parentTraces,
                                    CountDownLatch done)
   {
      pool.add(new ThreadPool.AbstractContextRunnable() {
         @Override
         public void run() {
            ownTraces[level] = getStackTrace();
            StackTraceElement[] parent = getParentStackTrace();
            parentTraces[level] = parent == null ? new StackTraceElement[0] : parent;

            if(level < maxLevel) {
               submitLevel(pool, level + 1, maxLevel, ownTraces, parentTraces, done);
            }
            else {
               done.countDown();
            }
         }
      });
   }
}
