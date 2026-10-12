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

import java.util.OptionalLong;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * One blocking wait registered with {@link WaitRegistry} (bug #76967). The waiting thread
 * calls {@link #checkStall()} between the slices of its wait, at a point where it holds no
 * loan and no monitor the producer needs, and closes the record when the wait is over.
 *
 * <p>The wait makes progress when its progress counter changes, or when one of the threads
 * it waits for does: a registered blocker whose own wait made progress (its timestamp is
 * taken, never "now", so the members of a cycle cannot keep each other alive), or an
 * unregistered blocker that is running.
 *
 * <p>In {@code fail} mode a stalled wait fails at once only if the policy fails on the timeout
 * alone ({@code stall.watchdog.failOnTimeout}). Otherwise it is reported like an
 * {@code alert}-mode wait and goes on, and fails once the watchdog has confirmed that it can
 * never progress: it is the victim of a wait-for cycle, or it waits for a cycle that no timeout
 * releases, or for a JVM deadlock (Feature #77123, see {@link StallWatchdog}). The waiter
 * checks the confirmation against the current wait-for graph right before it fails, and the
 * confirmation is revoked if the wait is no longer stuck.
 */
public final class WaitRecord implements AutoCloseable {
   private WaitRecord() {
      registry = null;
      what = "none";
      progress = () -> 0;
      blockers = () -> new Thread[0];
      mode = StallPolicy.Mode.OFF;
      limitNanos = Long.MAX_VALUE;
      sliceMillis = Long.MAX_VALUE;
      thread = null;
      startNanos = 0;
      creditOnly = false;
      failOnTimeout = false;
   }

   WaitRecord(WaitRegistry registry, String what, LongSupplier progress,
              Supplier<Thread[]> blockers, StallPolicy policy, Thread thread, long now,
              boolean creditOnly)
   {
      this.registry = registry;
      this.creditOnly = creditOnly;
      this.what = what;
      this.progress = progress;
      this.blockers = blockers;
      this.mode = policy.getMode();
      this.failOnTimeout = policy.isFailOnTimeout();
      this.limitNanos = TimeUnit.MILLISECONDS.toNanos(policy.getNoProgressMillis());
      this.sliceMillis = Math.max(10, policy.getNoProgressMillis() / 4);
      this.thread = thread;
      this.startNanos = now;
      this.progressNanos = now;
      this.lastValue = creditOnly ? sampleSafely(progress) : progress.getAsLong();
   }

   /**
    * Sample the initial progress of a credit-only wait, which never throws into its waiter.
    */
   private static long sampleSafely(LongSupplier progress) {
      try {
         return progress.getAsLong();
      }
      catch(RuntimeException ex) {
         return 0;
      }
   }

   /**
    * Sample the progress and handle a stall: in {@code fail} mode dump the threads and throw,
    * once the stall is confirmed (see the class doc); in {@code alert} mode, or while a
    * {@code fail}-mode stall is not confirmed, dump and warn once per stall episode (such a
    * wait never turns health DOWN by itself, see {@link StallWatchdog}). Once the wait failed,
    * every later call rethrows the same exception.
    *
    * <p>Only the waiting thread may call this method: the progress sample is not
    * synchronized. Other threads, such as the watchdog, never call it.
    *
    * @throws LockStallException if the wait made no progress for the limit, in fail mode.
    */
   public void checkStall() {
      if(registry == null) {
         return;
      }

      if(creditOnly) {
         // only moves the progress time the waits blocked by this thread are credited with
         try {
            sample(registry.nanoTime());
         }
         catch(Throwable ex) {
            try {
               LOG.debug("Failed to sample the progress of the wait {}", what, ex);
            }
            catch(Throwable ignore) {
               // a credit-only wait never fails the waiter
            }
         }

         return;
      }

      LockStallException failure = this.failure;

      if(failure != null) {
         // a new instance on a re-entry: the first one may have been thrown through a script,
         // which adds a suppressed stack trace element that cannot be serialized (bug #78084)
         throw failure.copy();
      }

      long now = registry.nanoTime();
      sample(now);

      if(now - progressNanos >= limitNanos) {
         // the cycle a scan confirmed may have dissolved since (Feature #77123)
         revalidateCycle();
      }

      String reason;
      String path;
      Throwable dumpError = null;

      // the episode fields are checked and set under this record's monitor, as the watchdog
      // does. The waiter may hold engine locks here (CrossJoinTableLens even its own monitor),
      // but there is no cycle: the watchdog only takes its own monitor, this record's and the
      // dumper's, in that order, never an engine lock or a lens monitor. It also calls the
      // blocker suppliers, outside of any record's monitor, which take at most the private
      // monitor of a LendableReentrantLock, as a leaf (see StallWatchdog.scanRecord()).
      boolean firstTrip;

      synchronized(this) {
         long stalledNanos = now - progressNanos;

         if(stalledNanos < limitNanos) {
            return;
         }

         // a fail-mode wait that may still progress is only reported, like an alert-mode
         // one, until the watchdog confirms it never will (Feature #77123)
         boolean fail = mode == StallPolicy.Mode.FAIL &&
            (failOnTimeout || reportOnly != null || cycleConfirmed);

         if(tripped && !fail) {
            return;
         }

         firstTrip = !tripped;
         tripped = true;
         long stalledMillis = TimeUnit.NANOSECONDS.toMillis(stalledNanos);
         reason = describe(stalledMillis);
         path = dumpPath;

         if(path == null) {
            // a dump started during this stall (such as another cycle member's, inside the
            // dumper's window) shows it, so it is this stall's dump too. One started before the
            // stall's last progress, or at the same instant, is unrelated and is never
            // attached: the path stays null. The dump is guarded against errors too (such as
            // an OutOfMemoryError while dumping the threads): the episode is already tripped,
            // so an escaping error would leave a failed wait that later checks let through.
            try {
               StallDumper dumper = registry.getDumper();
               dumper.dump(reason);
               StallDumper.LastDump last = dumper.getLastDump();

               if(last != null && last.startNanos() - progressNanos > 0) {
                  path = last.path();
                  dumpPath = path;
               }
            }
            catch(Throwable ex) {
               dumpError = ex;
            }
         }

         if(fail) {
            failed = true;
            failure = new LockStallException(what, thread.getName(), stalledMillis, path);
            this.failure = failure;
         }
      }

      if(dumpError != null) {
         try {
            LOG.error("Failed to write the lock stall thread dump", dumpError);
         }
         catch(Throwable ignore) {
            // the wait still fails (or alerts) below
         }
      }

      if(failure != null) {
         String label = reportOnly;

         if(label != null) {
            // the waiter catches the stall and keeps waiting, nothing fails
            LOG.warn("{}: {}, thread dump: {}", label, reason, path);
         }
         else {
            // the message names the dump's file name only, the log gets its full path
            LOG.error("{}{}, thread dump: {}", failOnTimeout ? "" : "Lock cycle confirmed. ",
                      failure.getMessage(), path == null ? "none" : path);
         }

         throw failure;
      }

      if(firstTrip) {
         if(mode == StallPolicy.Mode.FAIL) {
            // fails only once the watchdog confirms the wait can never progress
            LOG.warn("Lock stall, no lock cycle confirmed, waiting on: {}, thread dump: {}",
                     reason, path);
         }
         else {
            LOG.warn("Lock stall, alert only: {}, thread dump: {}", reason, path);
         }
      }
   }

   /**
    * Sample the progress counter, or else the blockers' credit.
    */
   private void sample(long now) {
      long value = progress.getAsLong();

      if(value != lastValue) {
         lastValue = value;
         progressed(now, now);
      }
      else {
         OptionalLong credit = registry.getBlockerProgressNanos(blockers.get(), thread, now);

         if(credit.isPresent() && credit.getAsLong() - progressNanos > 0) {
            progressed(credit.getAsLong(), now);
         }
      }
   }

   /**
    * Get how long one slice of the wait may be, so the stall is checked in time.
    *
    * @param max the timeout the wait site uses without the watchdog.
    */
   public long waitMillis(long max) {
      if(registry == null) {
         return max;
      }

      // 0 (or less) means forever for wait/await, which would never check the stall
      return max <= 0 ? sliceMillis : Math.min(max, sliceMillis);
   }

   /**
    * End the wait. Must be called on the waiting thread, in a finally block.
    */
   @Override
   public void close() {
      if(registry != null && !closed) {
         closed = true;
         registry.end(this);
      }
   }

   /**
    * Describe the wait for a log message or a dump header.
    */
   public String describe(long stalledMillis) {
      return what + " on thread \"" + (thread == null ? "none" : thread.getName()) +
         "\", no progress for " + stalledMillis + " ms";
   }

   public String getWhat() {
      return what;
   }

   public Thread getThread() {
      return thread;
   }

   /**
    * Get the threads this wait is currently for (e.g. a lock owner), sampled fresh from the
    * wait's blocker supplier. Never throws: an error, or a {@code null} result, is treated as
    * no blockers, matching this class's own "never fail the waiter" convention.
    *
    * <p>The watchdog calls it too, on its own thread and under its own monitor, to look for
    * wait-for cycles (bug #77152), see {@link WaitRegistry#begin(String, LongSupplier,
    * Supplier)}.
    */
   public Thread[] getBlockers() {
      try {
         Thread[] result = blockers.get();
         return result == null ? NO_THREADS : result;
      }
      catch(Throwable ex) {
         return NO_THREADS;
      }
   }

   public long getStartNanos() {
      return startNanos;
   }

   /**
    * Get when the wait last made progress, on the registry's clock.
    */
   public long getProgressNanos() {
      return progressNanos;
   }

   /**
    * Get the mode of the policy the wait was opened with. It never changes during the wait,
    * even if the policy does.
    */
   public StallPolicy.Mode getMode() {
      return mode;
   }

   /**
    * Check if the wait only gives credit to the waits it blocks, and is never failed, dumped
    * or reported itself (see {@link WaitRegistry#beginCreditOnly}).
    */
   public boolean isCreditOnly() {
      return creditOnly;
   }

   public long getLimitNanos() {
      return limitNanos;
   }

   /**
    * Check if the wait fails on its timeout alone, in {@code fail} mode, as the policy it was
    * opened with says ({@code stall.watchdog.failOnTimeout}).
    */
   public boolean isFailOnTimeout() {
      return failOnTimeout;
   }

   /**
    * Check if the wait fails only once the watchdog confirms it can never progress: a plain
    * {@code fail}-mode wait whose policy does not fail on the timeout alone (Feature #77123).
    */
   boolean isFailOnConfirmedOnly() {
      return mode == StallPolicy.Mode.FAIL && !failOnTimeout && !creditOnly &&
         reportOnly == null && registry != null;
   }

   /**
    * Check if the watchdog confirmed, during the current stall episode, that this wait can
    * never progress.
    */
   public boolean isCycleConfirmed() {
      return cycleConfirmed;
   }

   /**
    * Get when the watchdog first confirmed the current stall episode, on the registry's clock,
    * see {@link #isCycleConfirmed()}.
    */
   long getCycleConfirmedNanos() {
      return cycleConfirmedNanos;
   }

   /**
    * Confirm that the stalled wait can never progress, so its waiter fails it on its next
    * check (Feature #77123), if {@code stillStuck} still says so then. Called by the watchdog
    * only, for a stalled wait. Progress ends the confirmation with the episode, and the
    * watchdog revokes it ({@link #revokeCycle()}) once it no longer finds the wait stuck.
    *
    * @param stillStuck checks the current wait-for graph again, on the waiting thread.
    */
   synchronized void confirmCycle(long now, BooleanSupplier stillStuck) {
      if(now - progressNanos >= limitNanos) {
         this.stillStuck = stillStuck;

         if(!cycleConfirmed) {
            cycleConfirmedNanos = now;
            cycleConfirmed = true;
         }
      }
   }

   /**
    * Revoke the confirmation of the current stall episode: the cycle it was confirmed for
    * dissolved, or the wait is no longer the one its cycle fails (Feature #77123). A failed
    * wait stays failed.
    */
   synchronized void revokeCycle() {
      if(!failed) {
         cycleConfirmed = false;
         cycleConfirmedNanos = 0;
         stillStuck = null;
      }
   }

   /**
    * Check again, right before a confirmed wait fails, that it still can never progress, and
    * revoke the confirmation if it can. Called on the waiting thread, outside of this record's
    * monitor: the check walks the wait-for graph (see StallWatchdog.isStillStuck).
    */
   private void revalidateCycle() {
      BooleanSupplier check;

      // read together with the confirmation, which confirmCycle() sets under this monitor
      synchronized(this) {
         if(!cycleConfirmed || failed || failOnTimeout || reportOnly != null ||
            mode != StallPolicy.Mode.FAIL)
         {
            return;
         }

         check = stillStuck;
      }

      boolean stuck;

      try {
         stuck = check != null && check.getAsBoolean();
      }
      catch(Throwable ex) {
         // never fail the waiter on a failed check: a later scan confirms it again
         stuck = false;
      }

      if(!stuck) {
         revokeCycle(check);
      }
   }

   /**
    * Revoke the confirmation that {@code check} was found false for, unless a scan confirmed
    * the wait again since, with a new check.
    */
   private synchronized void revokeCycle(BooleanSupplier check) {
      if(stillStuck == check) {
         revokeCycle();
      }
   }

   /**
    * Mark a wait whose stall is reported but never fails the waiter, which catches the
    * exception and keeps waiting (such as a loan reclaim): a stall is logged as a warning
    * starting with {@code label} instead of as a failed query.
    */
   public void setReportOnly(String label) {
      if(this != NOOP) {
         reportOnly = label;
      }
   }

   /**
    * Check if the wait is report-only, see {@link #setReportOnly}.
    */
   boolean isReportOnly() {
      return reportOnly != null;
   }

   /**
    * Get how long one slice of the wait may be, i.e. how late the waiting thread may notice
    * its own stall.
    */
   long getSliceNanos() {
      return TimeUnit.MILLISECONDS.toNanos(sliceMillis);
   }

   boolean isClosed() {
      return closed;
   }

   /**
    * Check if the current stall episode was reported (or failed) by the waiting thread.
    */
   public boolean isTripped() {
      return tripped;
   }

   public boolean isFailed() {
      return failed;
   }

   public String getDumpPath() {
      return dumpPath;
   }

   void setDumpPath(String dumpPath) {
      this.dumpPath = dumpPath;
   }

   long getWatchdogSeenScan() {
      return watchdogSeenScan;
   }

   void setWatchdogSeenScan(long scan) {
      this.watchdogSeenScan = scan;
   }

   /**
    * Check if the watchdog logged the current stall episode.
    */
   boolean isWatchdogReported() {
      return watchdogReported;
   }

   void setWatchdogReported(boolean watchdogReported) {
      this.watchdogReported = watchdogReported;
   }

   /**
    * Record progress. Recent progress ends the stall episode, whether the waiter tripped it or
    * only the watchdog saw (and dumped) it, so the next episode is reported and dumped again.
    * The episode fields are guarded by this record's monitor, which the watchdog also takes to
    * check and set them.
    */
   private synchronized void progressed(long nanos, long now) {
      progressNanos = nanos;

      if(!failed && now - nanos < limitNanos &&
         (tripped || dumpPath != null || watchdogSeenScan != 0 || watchdogReported ||
            cycleConfirmed))
      {
         cycleConfirmed = false;
         cycleConfirmedNanos = 0;
         stillStuck = null;
         tripped = false;
         dumpPath = null;
         watchdogSeenScan = 0;
         watchdogReported = false;
      }
   }

   /**
    * The record of a wait that is not watched ({@code stall.watchdog.mode=off}).
    */
   static final WaitRecord NOOP = new WaitRecord();
   private static final Thread[] NO_THREADS = new Thread[0];
   private static final Logger LOG = LoggerFactory.getLogger(WaitRecord.class);

   // the outer wait of the same thread, restored when this one is closed
   WaitRecord previous;

   private final WaitRegistry registry;
   private final String what;
   private final LongSupplier progress;
   private final Supplier<Thread[]> blockers;
   private final StallPolicy.Mode mode;
   private final long limitNanos;
   private final long sliceMillis;
   private final Thread thread;
   private final long startNanos;
   private final boolean creditOnly;
   // fail-mode: fail on the timeout alone, without a confirmed cycle (Feature #77123)
   private final boolean failOnTimeout;
   private long lastValue;
   private volatile boolean closed;
   private volatile String reportOnly;
   private volatile LockStallException failure;
   private volatile long progressNanos;
   private volatile boolean tripped;
   private volatile boolean failed;
   private volatile String dumpPath;
   private volatile long watchdogSeenScan;
   private volatile boolean watchdogReported;
   // the watchdog confirmed this stall episode can never progress (Feature #77123)
   private volatile boolean cycleConfirmed;
   private volatile long cycleConfirmedNanos;
   // checks the confirmation again right before the wait fails, see revalidateCycle()
   private volatile BooleanSupplier stillStuck;
}
