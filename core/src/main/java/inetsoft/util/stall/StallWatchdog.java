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

import java.io.File;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.regex.Pattern;

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
    * Remove the watchdog's JVM deadlock finding from a reason of {@link #getUnreleasedStall()},
    * for a caller that reports the deadlocked threads itself.
    *
    * @return the rest of the reason, or {@code null} if nothing else is unreleased.
    */
   public static String withoutJvmDeadlock(String reason) {
      if(reason == null) {
         return null;
      }

      // the deadlock finding is always the last part (see scan())
      String rest = JVM_DEADLOCK_REASON.matcher(reason).replaceFirst("");
      return rest.isEmpty() ? null : rest;
   }

   /**
    * Register a probe with the server's watchdog. Registering does not start the watchdog
    * thread: the probe's owner calls {@link #wake()} when it sees something worth a scan, so
    * a healthy server never runs it.
    */
   public static void addProbe(StallProbe probe) {
      GLOBAL.add(probe);
   }

   /**
    * Start the server's watchdog thread if it is not running yet. Never throws.
    */
   public static void wake() {
      // defensive only: ensureStarted() never throws
      try {
         ensureStarted();
      }
      catch(RuntimeException ex) {
         LOG.warn("Failed to start the lock stall watchdog", ex);
      }
   }

   /**
    * Add a probe to this watchdog, once.
    */
   public void add(StallProbe probe) {
      if(probe != null) {
         probes.addIfAbsent(probe);
      }
   }

   /**
    * Remove a probe from this watchdog.
    */
   public void remove(StallProbe probe) {
      probes.remove(probe);
   }

   /**
    * Get the probe findings of the last scan, or {@code null} if there were none. They are
    * for logs and tests only: the health check never shows them.
    */
   public String getProbeFindings() {
      return findings;
   }

   /**
    * Start the server's watchdog thread, if it is not running yet. Called on the first
    * registered wait, and again whenever the thread ended. Never throws: a failed start is
    * logged and retried by a later call, at most once per {@code DEFAULT_SCAN_MILLIS}, so a
    * JVM that cannot create threads is not asked to by every wait, nor logs an error for each.
    */
   static void ensureStarted() {
      if(started || isStartBackingOff()) {
         return;
      }

      try {
         synchronized(StallWatchdog.class) {
            if(started || isStartBackingOff()) {
               return;
            }

            try {
               Thread thread = threadFactory.newThread(StallWatchdog::runGlobal);
               thread.setDaemon(true);
               thread.setContextClassLoader(StallWatchdog.class.getClassLoader());
               thread.start();
               // the thread's exit takes this monitor too, so it cannot reset before this
               StallWatchdog.thread = thread;
               started = true;
               lastStartFailure = null;
            }
            catch(Throwable ex) {
               // set under the monitor, so a concurrent caller does not attempt again
               lastStartFailure = startClock.getAsLong();
               throw ex;
            }
         }
      }
      catch(Throwable ex) {
         try {
            LOG.error("Failed to start the lock stall watchdog, retrying in {} ms",
                      StallPolicy.DEFAULT_SCAN_MILLIS, ex);
         }
         catch(Throwable ignore) {
            // never fail the waiter
         }
      }
   }

   /**
    * Check if the last start failed less than {@code DEFAULT_SCAN_MILLIS} ago.
    */
   private static boolean isStartBackingOff() {
      Long failed = lastStartFailure;
      return failed != null && startClock.getAsLong() - failed <
         TimeUnit.MILLISECONDS.toNanos(StallPolicy.DEFAULT_SCAN_MILLIS);
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
         lastStartFailure = null;
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
            // an Error too: it must not skip the remaining records, which would leave the flag
            // of a partial scan (a false UP)
            try {
               scanRecord(record, now, scanNo, reasons);
            }
            catch(Throwable ex) {
               try {
                  LOG.warn("Lock stall watchdog failed to check the wait {}", record.getWhat(),
                           ex);
               }
               catch(Throwable ignore) {
                  // never break the scan
               }
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
            scanProbes();
         }
      }
   }

   /**
    * Poll the probes. A finding is logged when its episode starts, that is when the previous
    * scan did not find its key for the same probe. A finding that asks for a dump is dumped
    * once per episode: as soon as the dumper writes one, retried on later scans while it is
    * rate-limited. Findings never make a stall unreleased. Each probe is isolated: a failing
    * one only skips its own findings, and keeps its episodes as they were, so a flapping probe
    * is not logged again.
    *
    * <p>Called under this watchdog's monitor, on its only thread: a probe that blocks stops
    * all stall detection and freezes the health flag.
    */
   private void scanProbes() {
      Map<StallProbe, Set<String>> keys = new HashMap<>();
      Map<StallProbe, Set<String>> dumped = new HashMap<>();
      List<String> messages = new ArrayList<>();

      for(StallProbe probe : probes) {
         Set<String> seen = probeKeys.getOrDefault(probe, Set.of());
         Set<String> wasDumped = dumpedProbeKeys.getOrDefault(probe, Set.of());

         try {
            List<StallProbe.Finding> found = probe.scan();
            Set<String> current = new HashSet<>();
            Set<String> currentDumped = new HashSet<>();

            if(found != null) {
               for(StallProbe.Finding finding : found) {
                  if(finding == null || !current.add(finding.key())) {
                     continue;
                  }

                  messages.add(finding.message());
                  String path = null;

                  if(finding.dump()) {
                     if(wasDumped.contains(finding.key())) {
                        currentDumped.add(finding.key());
                     }
                     else {
                        path = dumpForScan(finding.message());

                        if(path != null) {
                           currentDumped.add(finding.key());
                        }
                     }
                  }

                  if(!seen.contains(finding.key())) {
                     if(finding.dump()) {
                        LOG.warn("Lock stall signal: {}, thread dump: {}", finding.message(),
                                 path == null ? "deferred" : path);
                     }
                     else {
                        LOG.warn("Lock stall signal: {}", finding.message());
                     }
                  }
                  else if(path != null) {
                     LOG.warn("Lock stall signal: {}, thread dump: {}", finding.message(), path);
                  }
               }
            }

            keys.put(probe, current);
            dumped.put(probe, currentDumped);
         }
         catch(Throwable ex) {
            keys.put(probe, seen);
            dumped.put(probe, wasDumped);

            try {
               LOG.warn("Lock stall probe {} failed", probe, ex);
            }
            catch(Throwable ignore) {
               // never break the scan
            }
         }
      }

      probeKeys = keys;
      dumpedProbeKeys = dumped;
      findings = messages.isEmpty() ? null : String.join("; ", messages);
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
    * this watchdog, then the record, then the dumper. A waiter may hold engine locks when it
    * takes the record's monitor (and {@code CrossJoinTableLens} checks inside its own monitor),
    * but there is no cycle: the watchdog only ever takes its own monitor, the record's and the
    * dumper's, never an engine lock or a lens monitor. That also relies on the probes keeping
    * their contract ({@link StallProbe}), as they are polled under this watchdog's monitor.
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

            // the rule of the waiter: only a dump started strictly after the stall's last
            // progress shows the stall, so waiter and watchdog attach the same dumps. The dump
            // count check of dumpForScan() already implies it: a dump written in this scan was
            // started after now, and this wait's progress is at least its limit before now.
            // It is kept to state the rule and in case that ever changes.
            if(path != null && scanDumpStartNanos - record.getProgressNanos() > 0) {
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
            // the health check shows this reason and may be unauthenticated: the dump's file
            // name only, the log above and the dumper's have its full path
            reasons.add("stall not released: " + reason + ", thread dump: " +
                           (record.getDumpPath() == null ? "none yet" :
                              new File(record.getDumpPath()).getName()));
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
      deadlockReason = JVM_DEADLOCK + ids.length + " threads";

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
         dumper.dump(reason);
         // the dump and its start time, read together
         StallDumper.LastDump last = dumper.getLastDump();

         if(dumper.getDumpCount() > before && last != null) {
            scanDumpPath = last.path();
            scanDumpStartNanos = last.startNanos();
            return scanDumpPath;
         }
      }
      catch(Throwable ex) {
         // an Error too, such as an OutOfMemoryError while dumping the threads: the scan goes on
         try {
            LOG.warn("Lock stall watchdog failed to write a thread dump", ex);
         }
         catch(Throwable ignore) {
            // never break the scan
         }
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
      findings = null;
      probeKeys = new HashMap<>();
      dumpedProbeKeys = new HashMap<>();
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
   private static final String JVM_DEADLOCK = "JVM deadlock of ";
   private static final Pattern JVM_DEADLOCK_REASON =
      Pattern.compile("(?:^|; )" + JVM_DEADLOCK + "\\d+ threads$");
   private static final StallWatchdog GLOBAL = new StallWatchdog(
      WaitRegistry.global(), () -> ManagementFactory.getThreadMXBean().findDeadlockedThreads());
   // replaced by tests only
   // no inherited thread locals: the first waiter's request state must not outlive it here
   static volatile ThreadFactory threadFactory = r -> Thread.ofPlatform()
      .name("Lock-Stall-Watchdog").daemon(true).inheritInheritableThreadLocals(false)
      .unstarted(r);
   static volatile LongSupplier startClock = System::nanoTime;
   private static volatile boolean started;
   // when the last start failed, on startClock, or null if it did not
   private static volatile Long lastStartFailure;
   private static Thread thread; // guarded by StallWatchdog.class

   private final WaitRegistry registry;
   private final Supplier<long[]> deadlockFinder;
   // guarded by this
   private long scans;
   private String scanDumpPath;
   // when the dump of this scan was started, on the registry's clock
   private long scanDumpStartNanos;
   private String deadlockReason;
   private long[] seenDeadlock;
   private long[] dumpedDeadlock;
   private volatile String unreleased;
   private final CopyOnWriteArrayList<StallProbe> probes = new CopyOnWriteArrayList<>();
   // guarded by this: each probe's keys of its current episodes, and those already dumped
   private Map<StallProbe, Set<String>> probeKeys = new HashMap<>();
   private Map<StallProbe, Set<String>> dumpedProbeKeys = new HashMap<>();
   private volatile String findings;
}
