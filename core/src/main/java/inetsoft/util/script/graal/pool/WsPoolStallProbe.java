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

import inetsoft.util.stall.StallProbe;
import inetsoft.util.stall.StallWatchdog;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;

/**
 * The worksheet script context pool's signals for the lock-stall watchdog (bug #76967): execs
 * whose timeout interrupt could not stop them, claims left open by their thread, and a node
 * with more pooled contexts than {@code script.ws.contextPool.warnSlotsPerNode}. It only reads
 * the pool's counters, so it never blocks or takes a lock. Like every probe, a finding is
 * logged (and for an interrupt timeout dumped) once per episode, and never fails a query or
 * turns health DOWN.
 */
public final class WsPoolStallProbe implements StallProbe {
   WsPoolStallProbe(LongSupplier interruptTimeouts, LongSupplier leakedClaims,
                    IntSupplier nodeSlots, IntSupplier warnSlotsPerNode)
   {
      this.interruptTimeouts = interruptTimeouts;
      this.leakedClaims = leakedClaims;
      this.nodeSlots = nodeSlots;
      this.warnSlotsPerNode = warnSlotsPerNode;
      lastInterruptTimeouts = interruptTimeouts.getAsLong();
      lastLeakedClaims = leakedClaims.getAsLong();
   }

   /**
    * Register this node's probe with the lock-stall watchdog, once. Called when a pooled env is
    * created, so a node with the pool off never registers it.
    */
   static void register() {
      if(REGISTERED.compareAndSet(false, true)) {
         StallWatchdog.addProbe(new WsPoolStallProbe(
            PoolMetrics::nodeInterruptTimeouts, PoolMetrics::nodeLeakedClaims,
            PoolMetrics::nodeSlots, PoolMetrics::nodeSlotWarnThreshold));
      }
   }

   /**
    * Called only by the watchdog, on its one thread.
    */
   @Override
   public List<Finding> scan() {
      List<Finding> found = new ArrayList<>();
      long timeouts = interruptTimeouts.getAsLong();
      long leaks = leakedClaims.getAsLong();

      if(timeouts > lastInterruptTimeouts) {
         found.add(new Finding(INTERRUPT_TIMEOUT, (timeouts - lastInterruptTimeouts) +
            " worksheet script exec(s) could not be stopped by their timeout interrupt (" +
            timeouts + " in total); their pooled contexts are closed instead of reused", true));
      }

      if(leaks > lastLeakedClaims) {
         found.add(new Finding(LEAKED_CLAIM, (leaks - lastLeakedClaims) +
            " worksheet script claim(s) were left open by their thread and released (" + leaks +
            " in total)", false));
      }

      int slots = nodeSlots.getAsInt();
      int warn = warnSlotsPerNode.getAsInt();

      if(slots > warn) {
         found.add(new Finding(NODE_SLOTS, "This node has " + slots +
            " pooled worksheet script contexts, above " + PoolConfig.WARN_SLOTS_PER_NODE + " (" +
            warn + ")", false));
      }

      lastInterruptTimeouts = timeouts;
      lastLeakedClaims = leaks;
      return found;
   }

   static final String INTERRUPT_TIMEOUT = "wsPool.interruptTimeout";
   static final String LEAKED_CLAIM = "wsPool.leakedClaim";
   static final String NODE_SLOTS = "wsPool.nodeSlots";

   private final LongSupplier interruptTimeouts;
   private final LongSupplier leakedClaims;
   private final IntSupplier nodeSlots;
   private final IntSupplier warnSlotsPerNode;
   // only the watchdog's thread scans
   private long lastInterruptTimeouts;
   private long lastLeakedClaims;

   private static final AtomicBoolean REGISTERED = new AtomicBoolean();
}
