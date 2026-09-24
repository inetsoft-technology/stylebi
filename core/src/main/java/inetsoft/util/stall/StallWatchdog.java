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
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Scans the registered waits every {@code stall.watchdog.scanMillis} (bug #76967). It dumps
 * a stalled wait once per stall episode, even when the waiting thread cannot check the stall
 * itself, and it dumps JVM-level deadlocks.
 *
 * <p>A stalled wait is first left to its own timeout, which the waiting thread notices up to
 * one wait slice late. It is reported by {@link #getUnreleasedStall()}, which turns the
 * deadlock health check DOWN so an orchestrator can restart the server as a last resort, only
 * once the timeout evidently did not release it:
 * <ul>
 *    <li>the waiter tripped (failed or alerted) at an earlier scan and the wait is still
 *        registered: in fail mode its thread could not unwind, in alert mode it persists;</li>
 *    <li>or the waiter never reached its check, the wait being overdue by two slices past the
 *        limit, on two scans.</li>
 * </ul>
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
    * registered wait, and again whenever the thread ended. Never throws: a failed start is
    * logged and retried on the next call.
    */
   static void ensureStarted() {
      if(started) {
         return;
      }

      try {
         synchronized(StallWatchdog.class) {
            if(started) {
               return;
            }

            Thread thread = threadFactory.newThread(StallWatchdog::runGlobal);
            thread.setDaemon(true);
            thread.setContextClassLoader(StallWatchdog.class.getClassLoader());
            thread.start();
            // the thread's exit takes this monitor too, so it cannot reset before this
            StallWatchdog.thread = thread;
            started = true;
         }
      }
      catch(Throwable ex) {
         try {
            LOG.error("Failed to start the lock stall watchdog, retrying on the next wait", ex);
         }
         catch(Throwable ignore) {
            // never fail the waiter
         }
      }
   }

   /**
    * Stop the server's watchdog thread and forget its state, so a test does not see the
    * thread or the flag of an earlier test.
    */
   static void resetForTest() {
      Thread running;

      synchronized(StallWatchdog.class) {
         running = thread;
      }

      if(running != null) {
         running.interrupt();

         try {
            running.join(10000);
         }
         catch(InterruptedException ex) {
            Thread.currentThread().interrupt();
         }
      }

      synchronized(StallWatchdog.class) {
         thread = null;
         started = false;
      }

      GLOBAL.clear();
   }

   /**
    * Scan the registered waits and the JVM for stalls once.
    */
   public synchronized void scan() {
      StallPolicy policy = registry.getPolicy();

      if(policy.getMode() == StallPolicy.Mode.OFF) {
         clear();
         return;
      }

      long scanNo = ++scans;
      List<String> reasons = new ArrayList<>();

      // the flag of the records is assigned even if the deadlock check fails
      try {
         scanDumpPath = null;
         long now = registry.nanoTime();

         for(WaitRecord record : registry.getActive()) {
            try {
               scanRecord(record, now, scanNo, reasons);
            }
            catch(RuntimeException ex) {
               LOG.warn("Lock stall watchdog failed to check the wait {}", record.getWhat(), ex);
            }
         }
      }
      finally {
         try {
            scanDeadlocks();
         }
         finally {
            if(deadlockReason != null) {
               reasons.add(deadlockReason);
            }

            unreleased = reasons.isEmpty() ? null : String.join("; ", reasons);
         }
      }
   }

   /**
    * Get why a stall is considered unreleased, as of the last scan, or {@code null}.
    */
   public String getUnreleasedStall() {
      return unreleased;
   }

   /**
    * Check one registered wait. The episode fields of the record are checked and set under its
    * monitor, re-reading its progress there, so the waiter's progress (which resets them under
    * the same monitor) is never overwritten with the fields of an ended episode. Lock order:
    * this watchdog, then the record; the waiter holds no other lock when it takes the record's.
    */
   private void scanRecord(WaitRecord record, long now, long scanNo, List<String> reasons) {
      synchronized(record) {
         long stalledNanos = now - record.getProgressNanos();
         long limitNanos = record.getLimitNanos();

         if(stalledNanos < limitNanos) {
            record.setWatchdogSeenScan(0);
            return;
         }

         String reason = record.describe(TimeUnit.NANOSECONDS.toMillis(stalledNanos));

         if(record.getDumpPath() == null) {
            String path = dumpForScan(reason);

            if(path != null) {
               record.setDumpPath(path);
            }
         }

         if(!record.isWatchdogReported()) {
            record.setWatchdogReported(true);
            LOG.warn("Lock stall detected by the watchdog: {}, thread dump: {}", reason,
                     record.getDumpPath() == null ? "deferred" : record.getDumpPath());
         }

         boolean tripped = record.isTripped();
         boolean overdue = stalledNanos - limitNanos >= 2 * record.getSliceNanos();

         // a plain stall is dumped only: the waiter notices it on its next check
         if(!tripped && !overdue) {
            return;
         }

         long seen = record.getWatchdogSeenScan();

         if(seen == 0) {
            record.setWatchdogSeenScan(scanNo);
         }
         else if(seen < scanNo) {
            reasons.add("stall not released: " + reason + ", thread dump: " +
                           (record.getDumpPath() == null ? "none yet" : record.getDumpPath()));
         }
      }
   }

   /**
    * Report a JVM-level deadlock. Unlike a registered wait, which gets its own timeout first,
    * a JVM deadlock can never resolve by itself, so it is reported (health DOWN) on its first
    * sighting, by design. It is dumped once, as soon as the dumper writes a dump for it.
    */
   private void scanDeadlocks() {
      long[] found;

      try {
         found = deadlockFinder.get();
      }
      catch(RuntimeException ex) {
         LOG.warn("Lock stall watchdog failed to check for JVM deadlocks", ex);
         return; // keep the last result
      }

      if(found == null || found.length == 0) {
         deadlockReason = null;
         seenDeadlock = null;
         dumpedDeadlock = null;
         return;
      }

      long[] ids = found.clone();
      Arrays.sort(ids);
      deadlockReason = "JVM deadlock of " + ids.length + " threads";

      if(!Arrays.equals(ids, seenDeadlock)) {
         seenDeadlock = ids;
         LOG.warn("JVM deadlock of threads {}", Arrays.toString(ids));
      }

      if(!Arrays.equals(ids, dumpedDeadlock)) {
         String path = dumpForScan("JVM deadlock of threads " + Arrays.toString(ids));

         if(path != null) {
            dumpedDeadlock = ids;
            LOG.warn("JVM deadlock of threads {}, thread dump: {}", Arrays.toString(ids), path);
         }
      }
   }

   /**
    * Get a thread dump taken in this scan: the one already written in this scan, which shows
    * every thread now, or a new one.
    *
    * @return the path, or {@code null} if no dump could be written now (rate-limited, or
    *         failed), in which case a later scan tries again. An older dump of the dumper's
    *         window is unrelated and is never returned.
    */
   private String dumpForScan(String reason) {
      if(scanDumpPath != null) {
         return scanDumpPath;
      }

      StallDumper dumper = registry.getDumper();

      try {
         int before = dumper.getDumpCount();
         String path = dumper.dump(reason);

         if(dumper.getDumpCount() > before) {
            scanDumpPath = path;
            return path;
         }
      }
      catch(RuntimeException ex) {
         LOG.warn("Lock stall watchdog failed to write a thread dump", ex);
      }

      return null;
   }

   /**
    * Run the scan loop until the thread is interrupted. Every iteration is guarded, so an
    * exception or error never ends the loop.
    */
   void loop(Sleeper sleeper) {
      while(true) {
         try {
            sleeper.sleep(getScanMillis());
            scan();
         }
         catch(InterruptedException ex) {
            return;
         }
         catch(Throwable ex) {
            LOG.error("Lock stall watchdog scan failed", ex);
         }
      }
   }

   private long getScanMillis() {
      try {
         return Math.max(MIN_SCAN_MILLIS, registry.getPolicy().getScanMillis());
      }
      catch(Throwable ex) {
         LOG.error("Failed to read the lock stall watchdog scan interval", ex);
         return StallPolicy.DEFAULT_SCAN_MILLIS;
      }
   }

   private synchronized void clear() {
      unreleased = null;
      deadlockReason = null;
      seenDeadlock = null;
      dumpedDeadlock = null;
   }

   private static void runGlobal() {
      try {
         GLOBAL.loop(Thread::sleep);
      }
      finally {
         // on any exit, let the next wait start a new thread
         synchronized(StallWatchdog.class) {
            if(thread == Thread.currentThread()) {
               thread = null;
               started = false;
            }
         }
      }
   }

   /**
    * Sleeps between scans; a test replaces it to run the loop on its own thread.
    */
   @FunctionalInterface
   interface Sleeper {
      void sleep(long millis) throws InterruptedException;
   }

   private static final Logger LOG = LoggerFactory.getLogger(StallWatchdog.class);
   private static final long MIN_SCAN_MILLIS = 100L;
   private static final StallWatchdog GLOBAL = new StallWatchdog(
      WaitRegistry.global(), () -> ManagementFactory.getThreadMXBean().findDeadlockedThreads());
   // replaced by tests only
   static volatile ThreadFactory threadFactory = r -> new Thread(r, "Lock-Stall-Watchdog");
   private static volatile boolean started;
   private static Thread thread; // guarded by StallWatchdog.class

   private final WaitRegistry registry;
   private final Supplier<long[]> deadlockFinder;
   // guarded by this
   private long scans;
   private String scanDumpPath;
   private String deadlockReason;
   private long[] seenDeadlock;
   private long[] dumpedDeadlock;
   private volatile String unreleased;
}
