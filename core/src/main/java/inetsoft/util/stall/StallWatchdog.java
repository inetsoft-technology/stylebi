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
package inetsoft.util.stall;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Scans the registered waits every {@code stall.watchdog.scanMillis} (bug #76967). It dumps
 * a stalled wait once per stall episode, even when the waiting thread cannot check the stall
 * itself, and it dumps JVM-level deadlocks. A wait that is still stalled one scan after the
 * watchdog first saw it stalled was not released by its timeout (e.g. its thread cannot
 * unwind, or the pool is exhausted): it is reported by {@link #getUnreleasedStall()}, which
 * turns the deadlock health check DOWN so an orchestrator can restart the server as a last
 * resort.
 */
public final class StallWatchdog {
   public StallWatchdog(WaitRegistry registry, Supplier<long[]> deadlockFinder) {
      this.registry = registry;
      this.deadlockFinder = deadlockFinder;
   }

   /**
    * Get the watchdog of the server's registry.
    */
   public static StallWatchdog global() {
      return GLOBAL;
   }

   /**
    * Get why a stall of the server is considered unreleased, or {@code null} if none is.
    */
   public static String getUnreleasedStallReason() {
      return GLOBAL.getUnreleasedStall();
   }

   /**
    * Start the server's watchdog thread, if it is not running yet. Called on the first
    * registered wait.
    */
   static void ensureStarted() {
      if(started) {
         return;
      }

      synchronized(StallWatchdog.class) {
         if(started) {
            return;
         }

         Thread thread = new Thread(GLOBAL::run, "Lock-Stall-Watchdog");
         thread.setDaemon(true);
         thread.start();
         started = true;
      }
   }

   /**
    * Scan the registered waits and the JVM for stalls once.
    */
   public synchronized void scan() {
      StallPolicy policy = registry.getPolicy();

      if(policy.getMode() == StallPolicy.Mode.OFF) {
         unreleased = null;
         return;
      }

      long scanNo = ++scans;
      long now = registry.nanoTime();
      List<String> reasons = new ArrayList<>();

      for(WaitRecord record : registry.getActive()) {
         // re-read the progress under the record's monitor, so a reset by the waiter's
         // progress is never overwritten with the fields of the ended episode
         synchronized(record) {
            long stalledNanos = now - record.getProgressNanos();

            if(stalledNanos < record.getLimitNanos()) {
               record.setWatchdogSeenScan(0);
               continue;
            }

            String reason = record.describe(TimeUnit.NANOSECONDS.toMillis(stalledNanos));

            if(record.getDumpPath() == null) {
               record.setDumpPath(registry.getDumper().dump(reason));
               LOG.warn("Lock stall detected by the watchdog: {}", reason);
            }

            long seen = record.getWatchdogSeenScan();

            if(seen == 0) {
               record.setWatchdogSeenScan(scanNo);
            }
            else if(seen < scanNo) {
               reasons.add("stall not released: " + reason + ", thread dump: " +
                              record.getDumpPath());
            }
         }
      }

      long[] deadlocked = deadlockFinder.get();

      if(deadlocked != null && deadlocked.length > 0) {
         long[] ids = deadlocked.clone();
         Arrays.sort(ids);
         int key = Arrays.hashCode(ids);
         reasons.add("JVM deadlock of " + ids.length + " threads");

         if(key != lastDeadlockKey) {
            lastDeadlockKey = key;
            String path = registry.getDumper().dump("JVM deadlock of threads " +
                                                       Arrays.toString(ids));
            LOG.warn("JVM deadlock of threads {}, thread dump: {}", Arrays.toString(ids), path);
         }
      }
      else {
         lastDeadlockKey = 0;
      }

      unreleased = reasons.isEmpty() ? null : String.join("; ", reasons);
   }

   /**
    * Get why a stall is considered unreleased, as of the last scan, or {@code null}.
    */
   public String getUnreleasedStall() {
      return unreleased;
   }

   private void run() {
      while(true) {
         try {
            Thread.sleep(Math.max(100L, registry.getPolicy().getScanMillis()));
         }
         catch(InterruptedException ex) {
            return;
         }

         try {
            scan();
         }
         catch(RuntimeException ex) {
            LOG.warn("Lock stall watchdog scan failed", ex);
         }
      }
   }

   private static final Logger LOG = LoggerFactory.getLogger(StallWatchdog.class);
   private static final StallWatchdog GLOBAL = new StallWatchdog(
      WaitRegistry.global(), () -> ManagementFactory.getThreadMXBean().findDeadlockedThreads());
   private static volatile boolean started;

   private final WaitRegistry registry;
   private final Supplier<long[]> deadlockFinder;
   private long scans;
   private int lastDeadlockKey;
   private volatile String unreleased;
}
