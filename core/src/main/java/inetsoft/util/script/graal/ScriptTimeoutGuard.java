/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.util.script.graal;

import org.graalvm.polyglot.Context;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Interrupts a long-running Context evaluation. Replaces TimeoutContext.
 * Usage:
 *   try(var ignored = guard.guard(ctx, duration)) { ctx.eval(...); }
 * If duration is zero or negative, no timeout is scheduled.
 */
public class ScriptTimeoutGuard {
   /** AutoCloseable variant that does not throw a checked exception on close(). */
   @FunctionalInterface
   public interface Guard extends AutoCloseable {
      @Override void close();

      /**
       * @return {@code true} if this exec's interrupt could not stop it within its bound, so
       * the Context is in an unknown state (bug #76960).
       */
      default boolean interruptTimedOut() {
         return false;
      }
   }

   /** Test hook run by the interrupt task right before it interrupts; null in production. */
   static volatile Runnable beforeInterruptHook;

   private static final ScheduledExecutorService SCHED = newScheduler();

   private static ScheduledExecutorService newScheduler() {
      ScheduledThreadPoolExecutor sched = new ScheduledThreadPoolExecutor(1, r -> {
         Thread t = new Thread(r, "script-timeout-guard");
         t.setDaemon(true);
         return t;
      });
      // a cancelled watchdog leaves the queue at once; otherwise every exec keeps a task live
      // for the whole timeout (default 10000 s), which grows the heap by ~72 B per exec
      sched.setRemoveOnCancelPolicy(true);
      return sched;
   }

   // Separate cached pool for the blocking ctx.interrupt() calls so that
   // concurrent timeouts never queue behind each other on the scheduler thread.
   private static final ExecutorService INTERRUPT_POOL =
      Executors.newCachedThreadPool(r -> {
         Thread t = new Thread(r, "script-timeout-interrupt");
         t.setDaemon(true);
         return t;
      });

   /** Test hook: the number of watchdogs still queued on the scheduler. */
   static int queuedTasks() {
      return ((ScheduledThreadPoolExecutor) SCHED).getQueue().size();
   }

   /** Returns a Guard that cancels the watchdog when the eval finishes. */
   public Guard guard(Context ctx, Duration timeout) {
      if(timeout == null || timeout.isZero() || timeout.isNegative()) {
         return () -> { };
      }

      TokenGuard guard = new TokenGuard(ctx);
      guard.future = SCHED.schedule(() -> INTERRUPT_POOL.submit(guard::interrupt),
                                    timeout.toMillis(), TimeUnit.MILLISECONDS);
      return guard;
   }

   /**
    * The token of one exec (bug #76960, spec §6.3). The interrupt task and close() race for
    * it: whichever moves it out of ACTIVE first wins. If the interrupt won, close() waits
    * for that interrupt to finish before the exec hands the Context on, so an interrupt
    * meant for this exec can never land on the next exec on the same Context. The wait is
    * bounded by ctx.interrupt's own 2 s bound and never waits for a lock.
    */
   private static final class TokenGuard implements Guard {
      TokenGuard(Context ctx) {
         this.ctx = ctx;
      }

      void interrupt() {
         if(!state.compareAndSet(ACTIVE, INTERRUPTING)) {
            return;
         }

         try {
            Runnable hook = beforeInterruptHook;

            if(hook != null) {
               hook.run();
            }

            ctx.interrupt(Duration.ofSeconds(2));
         }
         catch(TimeoutException ex) {
            timedOut = true;
         }
         catch(Exception ignore) {
            // context may already be closed
         }
         finally {
            state.set(DONE);
            interruptDone.countDown();
         }
      }

      @Override
      public void close() {
         if(state.compareAndSet(ACTIVE, DONE)) {
            ScheduledFuture<?> f = future;

            if(f != null) {
               f.cancel(false);
            }

            return;
         }

         try {
            if(!interruptDone.await(3, TimeUnit.SECONDS)) {
               // the claimed interrupt may still land on a later exec: report the Context
               // as unknown, so a pooled one is closed instead of reused (spec §6.3, G5)
               timedOut = true;
            }
         }
         catch(InterruptedException ex) {
            // restore the flag and return rather than keep waiting; the claimed interrupt
            // may still land on a later exec, so report the Context as unknown too
            timedOut = true;
            Thread.currentThread().interrupt();
         }
      }

      @Override
      public boolean interruptTimedOut() {
         return timedOut;
      }

      private static final int ACTIVE = 0;
      private static final int INTERRUPTING = 1;
      private static final int DONE = 2;

      private final Context ctx;
      private final AtomicInteger state = new AtomicInteger(ACTIVE);
      private final CountDownLatch interruptDone = new CountDownLatch(1);
      private volatile boolean timedOut;
      private volatile ScheduledFuture<?> future;
   }
}
