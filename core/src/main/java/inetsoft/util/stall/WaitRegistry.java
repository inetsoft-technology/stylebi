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

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * The blocking waits of the query pipeline that are currently in progress, one per thread
 * (bug #76967). A wait site registers only once it actually has to block, never on its fast
 * path:
 * <pre>
 * try(WaitRecord record = WaitRegistry.begin("site", progress, blockers)) {
 *    while(!ready) {
 *       wait(record.waitMillis(timeout));
 *       record.checkStall();
 *    }
 * }
 * </pre>
 */
public final class WaitRegistry {
   /**
    * @param nanoClock the clock of the waits' progress.
    * @param policy    the current policy.
    * @param dumper    the dumper, which must use the same nano clock as {@code nanoClock}: a
    *                  waiter compares the start of the last dump with its progress time to tell
    *                  whether that dump shows its stall. In production both are
    *                  {@code System::nanoTime}.
    */
   public WaitRegistry(LongSupplier nanoClock, Supplier<StallPolicy> policy, StallDumper dumper) {
      this.nanoClock = nanoClock;
      this.policy = policy;
      this.dumper = dumper;
   }

   /**
    * Get the registry of the server.
    */
   public static WaitRegistry global() {
      return GLOBAL;
   }

   /**
    * Register a wait of the current thread with the server's registry.
    *
    * @param what     the wait site, for the error message and the thread dump.
    * @param progress a counter that changes whenever the wait makes progress.
    */
   public static WaitRecord begin(String what, LongSupplier progress) {
      return begin(what, progress, NO_BLOCKERS);
   }

   /**
    * Register a wait of the current thread with the server's registry.
    *
    * @param what     the wait site, for the error message and the thread dump.
    * @param progress a counter that changes whenever the wait makes progress.
    * @param blockers the threads the wait is for, e.g. a lens worker or a lock owner.
    */
   public static WaitRecord begin(String what, LongSupplier progress,
                                  Supplier<Thread[]> blockers)
   {
      WaitRecord record = GLOBAL.open(what, progress, blockers);

      if(record != WaitRecord.NOOP) {
         StallWatchdog.ensureStarted();
      }

      return record;
   }

   /**
    * Register an unbounded wait of the current thread with the server's registry, only so that
    * the waits blocked by this thread get credit for its progress. The wait is never failed,
    * dumped or reported, and never turns health DOWN: {@link WaitRecord#checkStall()} only
    * samples its progress and never throws. Like {@link #begin}, it must only be called on the
    * slow path of the wait, and closed in a finally block.
    *
    * <p>The credit rules are those of any registered wait: the threads waiting for this one
    * get this wait's own progress time, which moves only when its progress counter changes or
    * one of its blockers progresses, never "now". So a cycle through this wait still trips
    * the other (registered) members of the cycle.
    *
    * @param what     the wait site.
    * @param progress a counter that changes whenever the wait makes progress.
    * @param blockers the threads the wait is for, e.g. the thread loading the rows.
    */
   public static WaitRecord beginCreditOnly(String what, LongSupplier progress,
                                            Supplier<Thread[]> blockers)
   {
      // the watchdog ignores credit-only waits, it need not be started for one
      return GLOBAL.openCreditOnly(what, progress, blockers);
   }

   /**
    * Register a wait of the current thread with this registry.
    */
   public WaitRecord open(String what, LongSupplier progress, Supplier<Thread[]> blockers) {
      return open(what, progress, blockers, false);
   }

   /**
    * Register a credit-only wait of the current thread with this registry, see
    * {@link #beginCreditOnly}.
    */
   public WaitRecord openCreditOnly(String what, LongSupplier progress,
                                    Supplier<Thread[]> blockers)
   {
      return open(what, progress, blockers, true);
   }

   private WaitRecord open(String what, LongSupplier progress, Supplier<Thread[]> blockers,
                           boolean creditOnly)
   {
      StallPolicy policy = this.policy.get();

      if(policy.getMode() == StallPolicy.Mode.OFF) {
         return WaitRecord.NOOP;
      }

      Thread thread = Thread.currentThread();
      WaitRecord record = new WaitRecord(this, what, progress, blockers, policy, thread,
                                         nanoTime(), creditOnly);
      record.previous = active.put(thread, record);
      begins.increment();
      return record;
   }

   /**
    * Get the innermost wait of every waiting thread.
    */
   public List<WaitRecord> getActive() {
      return new ArrayList<>(active.values());
   }

   /**
    * Get the number of waits registered so far, e.g. to check that a fast path registers none.
    */
   public long getBeginCount() {
      return begins.sum();
   }

   public StallPolicy getPolicy() {
      return policy.get();
   }

   public StallDumper getDumper() {
      return dumper;
   }

   long nanoTime() {
      return nanoClock.getAsLong();
   }

   void end(WaitRecord record) {
      active.compute(record.getThread(),
                     (thread, current) -> current == record ? getOpen(record.previous) : current);
   }

   /**
    * Get the innermost wait of a chain that is not closed yet, since an outer wait may have
    * been closed out of order.
    */
   private static WaitRecord getOpen(WaitRecord record) {
      while(record != null && record.isClosed()) {
         record = record.previous;
      }

      return record;
   }

   /**
    * Get when one of the blockers last made progress: the progress time of a blocker's own
    * registered wait, or now for an unregistered blocker that is running.
    *
    * <p>The credit is transitive but lags: a registered blocker's progress time only moves
    * when that blocker samples its own wait, once per wait slice. A chain of k registered
    * waits in front of the running thread lags by up to about k slices
    * ({@code noProgressMillis / 4} each), so a chain of 4 or more registered waits may trip
    * although the thread at its end is running.
    *
    * @param blockers the threads waited for; {@code null} means none.
    * @param self     the thread of the wait being credited, which never credits itself.
    *
    * @return the most recent time, or empty if none of the blockers is progressing.
    */
   OptionalLong getBlockerProgressNanos(Thread[] blockers, Thread self, long now) {
      if(blockers == null) {
         return OptionalLong.empty();
      }

      boolean found = false;
      long best = 0;

      for(Thread blocker : blockers) {
         if(blocker == null || blocker == self) {
            continue;
         }

         WaitRecord record = active.get(blocker);
         long nanos;

         if(record != null) {
            nanos = record.getProgressNanos();
         }
         else if(blocker.getState() == Thread.State.RUNNABLE) {
            nanos = now;
         }
         else {
            continue;
         }

         if(!found || nanos - best > 0) {
            best = nanos;
            found = true;
         }
      }

      return found ? OptionalLong.of(best) : OptionalLong.empty();
   }

   private static final Thread[] NO_THREADS = new Thread[0];
   private static final Supplier<Thread[]> NO_BLOCKERS = () -> NO_THREADS;
   private static final WaitRegistry GLOBAL =
      new WaitRegistry(System::nanoTime, StallPolicy::get, StallDumper.global());

   private final LongSupplier nanoClock;
   private final Supplier<StallPolicy> policy;
   private final StallDumper dumper;
   private final ConcurrentHashMap<Thread, WaitRecord> active = new ConcurrentHashMap<>();
   private final LongAdder begins = new LongAdder();
}
