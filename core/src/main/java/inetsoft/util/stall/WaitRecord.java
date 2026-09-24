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
   }

   WaitRecord(WaitRegistry registry, String what, LongSupplier progress,
              Supplier<Thread[]> blockers, StallPolicy policy, Thread thread, long now)
   {
      this.registry = registry;
      this.what = what;
      this.progress = progress;
      this.blockers = blockers;
      this.mode = policy.getMode();
      this.limitNanos = TimeUnit.MILLISECONDS.toNanos(policy.getNoProgressMillis());
      this.sliceMillis = Math.max(10, policy.getNoProgressMillis() / 4);
      this.thread = thread;
      this.startNanos = now;
      this.progressNanos = now;
      this.lastValue = progress.getAsLong();
   }

   /**
    * Sample the progress and handle a stall: in {@code fail} mode dump the threads and throw,
    * in {@code alert} mode dump and warn once per stall episode. Once the wait failed, every
    * later call rethrows the same exception.
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

      LockStallException failure = this.failure;

      if(failure != null) {
         throw failure;
      }

      long now = registry.nanoTime();
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

      String reason;
      String path;

      // the episode fields are checked and set under this record's monitor, as the watchdog
      // does (the watchdog's monitor, then the record's; the waiter holds no other lock here)
      synchronized(this) {
         long stalledNanos = now - progressNanos;

         if(stalledNanos < limitNanos || tripped) {
            return;
         }

         tripped = true;
         long stalledMillis = TimeUnit.NANOSECONDS.toMillis(stalledNanos);
         reason = describe(stalledMillis);
         path = dumpPath;

         if(path == null) {
            path = registry.getDumper().dump(reason);
            dumpPath = path;
         }

         if(mode == StallPolicy.Mode.FAIL) {
            failed = true;
            failure = new LockStallException(what, thread.getName(), stalledMillis, path);
            this.failure = failure;
         }
      }

      if(failure != null) {
         LOG.error(failure.getMessage());
         throw failure;
      }

      LOG.warn("Lock stall, alert only: {}, thread dump: {}", reason, path);
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

   public long getStartNanos() {
      return startNanos;
   }

   /**
    * Get when the wait last made progress, on the registry's clock.
    */
   public long getProgressNanos() {
      return progressNanos;
   }

   public long getLimitNanos() {
      return limitNanos;
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
         (tripped || dumpPath != null || watchdogSeenScan != 0 || watchdogReported))
      {
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
   private long lastValue;
   private volatile boolean closed;
   private volatile LockStallException failure;
   private volatile long progressNanos;
   private volatile boolean tripped;
   private volatile boolean failed;
   private volatile String dumpPath;
   private volatile long watchdogSeenScan;
   private volatile boolean watchdogReported;
}
