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

import inetsoft.util.script.ScriptStateLint;
import inetsoft.util.stall.StallWatchdog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.Cleaner;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Counters of one pooled worksheet env's contexts (bug #76960, spec §4.1, §14.3), plus their
 * node-wide totals, which the pool logs periodically ({@link #nodeSummary()}).
 */
public final class PoolMetrics {
   /**
    * Count a new slot. It counts toward the node's slots until it is closed or, if it never
    * is (its env was dropped without a retire), until it is collected, so the node count
    * tracks live contexts.
    *
    * @return the handle {@link #slotClosed} releases the node count with.
    */
   Cleaner.Cleanable slotCreated(Object slot) {
      int now = size.incrementAndGet();
      highWater.accumulateAndGet(now, Math::max);
      NODE_MAX_SANDBOX_SLOTS.accumulateAndGet(now, Math::max);
      creations.incrementAndGet();
      NODE_CREATIONS.incrementAndGet();
      NODE_SLOTS.incrementAndGet();
      AtomicInteger released = nodeReleases;
      // the action must not reference the slot
      return CLEANER.register(slot, () -> {
         NODE_SLOTS.decrementAndGet();
         released.incrementAndGet();
      });
   }

   void slotClosed(Cleaner.Cleanable node) {
      size.decrementAndGet();
      // runs the decrement at most once, whether now or at collection
      node.clean();
   }

   void evicted() {
      evictions.incrementAndGet();
      NODE_EVICTIONS.incrementAndGet();
   }

   void doomedClosed() {
      doomedCloses.incrementAndGet();
      NODE_DOOMED_CLOSES.incrementAndGet();
   }

   void cleaned() {
      cleans.incrementAndGet();
      NODE_CLEANS.incrementAndGet();
   }

   void executed() {
      execs.incrementAndGet();
      NODE_EXECS.incrementAndGet();
   }

   // one per claim that took a context (G10 piece Q: one per query build)
   void checkedOut() {
      checkouts.incrementAndGet();
   }

   void swapped() {
      swaps.incrementAndGet();
   }

   void buildYielded() {
      buildYields.incrementAndGet();
   }

   /**
    * @return the contexts a query build gave back before its end, because a formula table
    * batch made them the home of the table's script objects (G10 piece Q, round 2).
    */
   public long getBuildYields() {
      return buildYields.get();
   }

   /**
    * @return the contexts taken by claims, one per claim that ran a script: with a query build
    * claim (G10 piece Q) one per build, however many formulas and conditions it runs.
    */
   public long getCheckouts() {
      return checkouts.get();
   }

   /**
    * @return the contexts a query build left for a fresh one because an interrupt could not
    * stop one of its scripts (G10 piece Q, amendment 1).
    */
   public long getSwaps() {
      return swaps.get();
   }

   // lens-owned objects across contexts (Testing #77123, B1 residual part 2)
   void handedOff(long nanos) {
      handOffs.incrementAndGet();
      handOffNanos.addAndGet(nanos);
      NODE_HAND_OFFS.incrementAndGet();
   }

   void rebuilt() {
      rebuilds.incrementAndGet();
   }

   void pulled() {
      pulls.incrementAndGet();
   }

   void crossRead() {
      crossReads.incrementAndGet();
   }

   void tookOver() {
      takeOvers.incrementAndGet();
   }

   /** @return the tree snapshots of lens-owned objects taken at a hand-off. */
   public long getHandOffs() {
      return handOffs.get();
   }

   /** @return the time spent in those snapshots, in nanoseconds. */
   public long getHandOffNanos() {
      return handOffNanos.get();
   }

   /** @return the tree snapshots rebuilt on another context. */
   public long getRebuilds() {
      return rebuilds.get();
   }

   /** @return the hand-offs pulled from an idle home by a batch on another context. */
   public long getPulls() {
      return pulls.get();
   }

   /** @return the reads of a lens-owned object made on another context. */
   public long getCrossReads() {
      return crossReads.get();
   }

   /** @return the idle homes another claim took over after a hand-off. */
   public long getTakeOvers() {
      return takeOvers.get();
   }

   /** @return the exclusive homes of this node. */
   public static int nodeHomes() {
      return NODE_HOMES.get();
   }

   /**
    * Count an exclusive home of this node, if the node has fewer than {@code max}, until the
    * handle is cleaned or the slot collected. Atomic across the node's pools.
    *
    * @return the handle, or {@code null} if the node already has {@code max} homes.
    */
   static Cleaner.Cleanable tryGrantHome(Object slot, int max) {
      while(true) {
         int n = NODE_HOMES.get();

         if(n >= max) {
            return null;
         }

         if(NODE_HOMES.compareAndSet(n, n + 1)) {
            break;
         }
      }

      // the action must not reference the slot
      return CLEANER.register(slot, NODE_HOMES::decrementAndGet);
   }

   public int getSize() {
      return size.get();
   }

   public int getHighWater() {
      return highWater.get();
   }

   public long getCreations() {
      return creations.get();
   }

   /**
    * @return the slots of this env that no longer count toward the node's slots, because they
    * were closed or collected.
    */
   int getNodeReleases() {
      return nodeReleases.get();
   }

   public long getEvictions() {
      return evictions.get();
   }

   public long getDoomedCloses() {
      return doomedCloses.get();
   }

   public long getCleans() {
      return cleans.get();
   }

   public long getExecs() {
      return execs.get();
   }

   /**
    * @return cleans per exec; with batch claims this stays far below 1 (spec §14.3).
    */
   public double cleansPerExec() {
      return ratio(cleans.get(), execs.get());
   }

   /**
    * @return the pooled worksheet contexts open on this node.
    */
   public static int nodeSlots() {
      return NODE_SLOTS.get();
   }

   /**
    * @return the most contexts any one pooled env of this node has had open at once.
    */
   public static int nodeMaxSandboxSlots() {
      return NODE_MAX_SANDBOX_SLOTS.get();
   }

   public static long nodeCreations() {
      return NODE_CREATIONS.get();
   }

   public static long nodeEvictions() {
      return NODE_EVICTIONS.get();
   }

   public static long nodeDoomedCloses() {
      return NODE_DOOMED_CLOSES.get();
   }

   public static long nodeCleans() {
      return NODE_CLEANS.get();
   }

   public static long nodeExecs() {
      return NODE_EXECS.get();
   }

   /**
    * Count an exec whose timeout interrupt could not stop it (bug #76967), and start the
    * lock-stall watchdog so its probe reports it.
    */
   static void interruptTimedOut() {
      NODE_INTERRUPT_TIMEOUTS.incrementAndGet();
      StallWatchdog.wake();
   }

   /**
    * Count a claim left open by its thread and released for it (bug #76967), and start the
    * lock-stall watchdog so its probe reports it.
    */
   static void leakedClaim() {
      NODE_LEAKED_CLAIMS.incrementAndGet();
      StallWatchdog.wake();
   }

   /**
    * Count a host copy of a worksheet script value that Java changed on the script's thread
    * while the script ran (bug #77123, C1): the script does not see that change.
    */
   static void copyMutated() {
      NODE_COPY_MUTATIONS.incrementAndGet();
   }

   /**
    * @return the host copies of script values passed to Java that Java changed on the
    *         script's thread during the script run, which the script did not see (C1).
    */
   public static long nodeCopyMutations() {
      return NODE_COPY_MUTATIONS.get();
   }

   /**
    * @return the execs of this node whose timeout interrupt could not stop them.
    */
   public static long nodeInterruptTimeouts() {
      return NODE_INTERRUPT_TIMEOUTS.get();
   }

   /**
    * @return the claims of this node left open by their thread and released for it.
    */
   public static long nodeLeakedClaims() {
      return NODE_LEAKED_CLAIMS.get();
   }

   /**
    * @return the node's warn threshold of pooled contexts, as set by the latest pooled env;
    *         {@link Integer#MAX_VALUE} before the first.
    */
   public static int nodeSlotWarnThreshold() {
      return nodeSlotWarnThreshold;
   }

   static void setNodeSlotWarnThreshold(int threshold) {
      nodeSlotWarnThreshold = threshold;
   }

   /**
    * @return the node-wide totals as one log line.
    */
   public static String nodeSummary() {
      long cleans = NODE_CLEANS.get();
      long execs = NODE_EXECS.get();
      return String.format(
         "slots=%d, maxSandboxSlots=%d, creations=%d, evictions=%d, doomedCloses=%d, " +
         "execs=%d, cleans=%d, cleansPerExec=%.4f, stateHazards=%d, copyMutations=%d, " +
         "homes=%d, handOffs=%d",
         NODE_SLOTS.get(), NODE_MAX_SANDBOX_SLOTS.get(), NODE_CREATIONS.get(),
         NODE_EVICTIONS.get(), NODE_DOOMED_CLOSES.get(), execs, cleans, ratio(cleans, execs),
         ScriptStateLint.nodeStateHazardScripts(), NODE_COPY_MUTATIONS.get(), NODE_HOMES.get(),
         NODE_HAND_OFFS.get());
   }

   /**
    * Log the node-wide totals at INFO if any pooled context was created or ran a script since
    * the last call, so an idle node logs nothing. The pool calls it every
    * {@link #LOG_PERIOD_MINUTES} minutes once pool mode is used (spec §8, G10).
    *
    * @return whether it logged.
    */
   static synchronized boolean logNodeSummary() {
      long activity = NODE_CREATIONS.get() + NODE_EXECS.get();

      if(activity == lastLoggedActivity) {
         return false;
      }

      lastLoggedActivity = activity;
      LOG.info("Worksheet script context pool on this node: {}", nodeSummary());
      return true;
   }

   private static double ratio(long cleans, long execs) {
      return execs == 0 ? 0 : (double) cleans / execs;
   }

   static final long LOG_PERIOD_MINUTES = 5;
   private static final Cleaner CLEANER = Cleaner.create();
   private static final Logger LOG = LoggerFactory.getLogger(PoolMetrics.class);
   private static long lastLoggedActivity; // guarded by the class monitor
   private static final AtomicInteger NODE_SLOTS = new AtomicInteger();
   private static final AtomicInteger NODE_MAX_SANDBOX_SLOTS = new AtomicInteger();
   private static final AtomicLong NODE_CREATIONS = new AtomicLong();
   private static final AtomicLong NODE_EVICTIONS = new AtomicLong();
   private static final AtomicLong NODE_DOOMED_CLOSES = new AtomicLong();
   private static final AtomicLong NODE_CLEANS = new AtomicLong();
   private static final AtomicLong NODE_EXECS = new AtomicLong();
   private static final AtomicLong NODE_INTERRUPT_TIMEOUTS = new AtomicLong();
   private static final AtomicLong NODE_LEAKED_CLAIMS = new AtomicLong();
   private static final AtomicLong NODE_COPY_MUTATIONS = new AtomicLong();
   private static final AtomicInteger NODE_HOMES = new AtomicInteger();
   private static final AtomicLong NODE_HAND_OFFS = new AtomicLong();
   // read by the lock-stall probe, which must not read properties (bug #76967)
   private static volatile int nodeSlotWarnThreshold = Integer.MAX_VALUE;
   private final AtomicInteger size = new AtomicInteger();
   private final AtomicInteger highWater = new AtomicInteger();
   private final AtomicLong creations = new AtomicLong();
   private final AtomicInteger nodeReleases = new AtomicInteger();
   private final AtomicLong evictions = new AtomicLong();
   private final AtomicLong doomedCloses = new AtomicLong();
   private final AtomicLong cleans = new AtomicLong();
   private final AtomicLong execs = new AtomicLong();
   private final AtomicLong checkouts = new AtomicLong();
   private final AtomicLong swaps = new AtomicLong();
   private final AtomicLong buildYields = new AtomicLong();
   private final AtomicLong handOffs = new AtomicLong();
   private final AtomicLong handOffNanos = new AtomicLong();
   private final AtomicLong rebuilds = new AtomicLong();
   private final AtomicLong pulls = new AtomicLong();
   private final AtomicLong crossReads = new AtomicLong();
   private final AtomicLong takeOvers = new AtomicLong();
}
