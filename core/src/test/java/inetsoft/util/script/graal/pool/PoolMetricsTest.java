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

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static inetsoft.util.script.graal.pool.PoolTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Final review I3 (bug #76960, spec §8, §14.3, G10): the node-wide pool metrics track live
 * contexts, count every doomed close, and are exported.
 */
@Tag("core")
class PoolMetricsTest {
   /**
    * A sandbox dropped without dispose() never retires its env; its contexts must stop counting
    * toward the node's slots once they are collected, or the node count only ever grows.
    */
   @Test
   void nodeSlotsDropWhenAnUnretiredEnvIsCollected() throws Exception {
      int before = PoolMetrics.nodeSlots();
      openAndDropAnEnv();
      assertTrue(PoolMetrics.nodeSlots() > before, "the env's primary was not counted");
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);

      while(PoolMetrics.nodeSlots() > before && System.nanoTime() < deadline) {
         System.gc();
         Thread.sleep(50);
      }

      assertTrue(PoolMetrics.nodeSlots() <= before,
                 "node slots " + PoolMetrics.nodeSlots() + ", before " + before);
   }

   /**
    * A slot a retire doomed while it was idle but could not take is closed at the next
    * checkout's prepare; that close is a doomed close too.
    */
   @Test
   void doomedSlotDiscardedAtCheckoutIsCounted() throws Exception {
      WorksheetScriptEnv env = env();

      try {
         run(env, "1");
         env.pool().primary().doom();
         run(env, "1");
         assertEquals(1, env.getMetrics().getDoomedCloses());
      }
      finally {
         env.retire();
      }
   }

   @Test
   void nodeCountersAggregateEveryEnvAndAreSummarized() throws Exception {
      long execs = PoolMetrics.nodeExecs();
      long cleans = PoolMetrics.nodeCleans();
      WorksheetScriptEnv first = env();
      WorksheetScriptEnv second = env();

      try {
         run(first, "1");
         run(second, "1");
         run(second, "2");
         assertTrue(PoolMetrics.nodeExecs() - execs >= 3);
         assertTrue(PoolMetrics.nodeCleans() - cleans >= 3);
         assertTrue(PoolMetrics.nodeMaxSandboxSlots() >= 1);
         String summary = PoolMetrics.nodeSummary();

         for(String key : new String[] { "slots=", "maxSandboxSlots=", "creations=",
                                          "evictions=", "doomedCloses=", "cleansPerExec=" })
         {
            assertTrue(summary.contains(key), summary);
         }
      }
      finally {
         first.retire();
         second.retire();
      }
   }

   /**
    * The periodic node log (G10 observability) logs after activity and stays quiet on an idle
    * node.
    */
   @Test
   void nodeSummaryIsLoggedOnlyAfterActivity() throws Exception {
      WorksheetScriptEnv env = env();

      try {
         run(env, "1");
         assertTrue(PoolMetrics.logNodeSummary());
         assertFalse(PoolMetrics.logNodeSummary(), "an idle node logs nothing");
         run(env, "1");
         assertTrue(PoolMetrics.logNodeSummary());
      }
      finally {
         env.retire();
      }
   }

   private static void openAndDropAnEnv() throws Exception {
      WorksheetScriptEnv env = env();
      run(env, "1");
   }
}
