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

import inetsoft.util.GroupedThread;
import inetsoft.util.stall.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static inetsoft.util.script.graal.pool.PoolTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The worksheet script context pool's stall probe (bug #76967): it reports interrupt timeouts,
 * leaked claims and node slots above the warn threshold to the lock-stall watchdog.
 */
@Tag("core")
class WsPoolStallProbeTest {
   @Test
   void idleProbeFindsNothing() {
      assertTrue(probe().scan().isEmpty());
   }

   @Test
   void countsFromBeforeTheProbeAreNotReported() {
      interruptTimeouts.set(3);
      leakedClaims.set(2);

      assertTrue(probe().scan().isEmpty());
   }

   @Test
   void interruptTimeoutIsReportedOnceWithADump() {
      WsPoolStallProbe probe = probe();
      interruptTimeouts.addAndGet(2);

      List<StallProbe.Finding> found = probe.scan();
      assertEquals(1, found.size());
      StallProbe.Finding finding = found.get(0);
      assertEquals(WsPoolStallProbe.INTERRUPT_TIMEOUT, finding.key());
      assertTrue(finding.dump(), "the unstoppable script is likely still running: dump it");
      assertTrue(finding.message().contains("2 worksheet script"), finding.message());

      assertTrue(probe.scan().isEmpty(), "no new timeout, the episode ends");
   }

   @Test
   void leakedClaimIsReportedWithoutADump() {
      WsPoolStallProbe probe = probe();
      leakedClaims.incrementAndGet();

      List<StallProbe.Finding> found = probe.scan();
      assertEquals(1, found.size());
      assertEquals(WsPoolStallProbe.LEAKED_CLAIM, found.get(0).key());
      assertFalse(found.get(0).dump(), "the leaking thread is gone, a dump shows nothing");
      assertTrue(probe.scan().isEmpty());
   }

   @Test
   void nodeSlotsAreReportedWhileAboveTheWarnThreshold() {
      WsPoolStallProbe probe = probe();
      warnSlotsPerNode.set(4);
      nodeSlots.set(4);
      assertTrue(probe.scan().isEmpty(), "at the threshold is not above it");

      nodeSlots.set(5);

      for(int i = 0; i < 2; i++) {
         List<StallProbe.Finding> found = probe.scan();
         assertEquals(1, found.size());
         assertEquals(WsPoolStallProbe.NODE_SLOTS, found.get(0).key(), "one episode");
         assertFalse(found.get(0).dump());
         assertTrue(found.get(0).message().contains("5"), found.get(0).message());
      }

      nodeSlots.set(3);
      assertTrue(probe.scan().isEmpty());
   }

   @Test
   void findingsReachTheWatchdog(@TempDir File dumpDir) {
      AtomicLong now = new AtomicLong();
      StallPolicy policy = new StallPolicy(StallPolicy.Mode.ALERT, 1000, 500, dumpDir);
      StallDumper dumper = new StallDumper(now::get, () -> dumpDir, 60000);
      StallWatchdog watchdog = new StallWatchdog(
         new WaitRegistry(now::get, () -> policy, dumper), () -> null);
      watchdog.add(probe());
      interruptTimeouts.incrementAndGet();
      leakedClaims.incrementAndGet();

      watchdog.scan();
      String findings = watchdog.getProbeFindings();
      assertNotNull(findings);
      assertTrue(findings.contains("interrupt"), findings);
      assertTrue(findings.contains("claim"), findings);
      assertEquals(1, dumper.getDumpCount(), "only the interrupt timeout asks for a dump");
      assertNull(watchdog.getUnreleasedStall(), "a probe never turns health DOWN");
   }

   @Test
   void interruptTimeoutOfAPooledContextIsCounted() throws Exception {
      WorksheetScriptEnv env = env();

      try {
         run(env, "1");
         long before = PoolMetrics.nodeInterruptTimeouts();
         env.pool().primary().engine().onInterruptTimeout();

         assertEquals(before + 1, PoolMetrics.nodeInterruptTimeouts());
      }
      finally {
         env.retire();
      }
   }

   @Test
   void leakedClaimIsCounted() throws Exception {
      WorksheetScriptEnv env = env();

      try {
         long before = PoolMetrics.nodeLeakedClaims();
         // a real GroupedThread, so the leak goes through its run() finally hook
         GroupedThread thread = new GroupedThread(
            () -> SlotClaim.acquire(env.pool(), false).slot(), "probe-leak-thread");
         thread.start();
         thread.join(10000);
         assertFalse(thread.isAlive());

         assertEquals(before + 1, PoolMetrics.nodeLeakedClaims());
      }
      finally {
         env.retire();
      }
   }

   @Test
   void aPooledEnvRegistersTheProbeWithTheGlobalWatchdog() throws Exception {
      WorksheetScriptEnv env = env();

      try {
         run(env, "1");
         env.pool().primary().engine().onInterruptTimeout();
         StallWatchdog.global().scan();

         String findings = StallWatchdog.global().getProbeFindings();
         assertNotNull(findings, "no probe finding: the pool's probe is not registered");
         assertTrue(findings.contains("interrupt"), findings);
      }
      finally {
         env.retire();
      }
   }

   @Test
   void aPooledEnvPublishesItsNodeWarnThreshold() {
      PoolConfig def = PoolConfig.defaults();
      PoolConfig config = new PoolConfig(def.idleMillis(), def.cleanThreshold(),
                                         def.warnSlotsPerSandbox(), 7, def.batchRows(),
                                         def.maxBatchRows());
      WorksheetScriptEnv env = new WorksheetScriptEnv(config);

      try {
         assertEquals(7, PoolMetrics.nodeSlotWarnThreshold());
      }
      finally {
         env.retire();
      }
   }

   private WsPoolStallProbe probe() {
      return new WsPoolStallProbe(interruptTimeouts::get, leakedClaims::get, nodeSlots::get,
                                  warnSlotsPerNode::get);
   }

   private final AtomicLong interruptTimeouts = new AtomicLong();
   private final AtomicLong leakedClaims = new AtomicLong();
   private final AtomicInteger nodeSlots = new AtomicInteger();
   private final AtomicInteger warnSlotsPerNode = new AtomicInteger(Integer.MAX_VALUE);
}
