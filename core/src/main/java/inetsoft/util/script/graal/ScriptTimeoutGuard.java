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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;
import java.time.Duration;
import java.util.Iterator;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.locks.LockSupport;

/**
 * Interrupts a long-running Context evaluation. Replaces TimeoutContext.
 * Usage:
 *   try(var ignored = guard.guard(ctx, duration)) { ctx.eval(...); }
 * If duration is zero or negative, no timeout is scheduled.
 *
 * <p>Every exec and every pooled clean opens a guard, so opening and closing one must not take
 * a shared lock. Testing #77123 (G10) showed that a single {@code ScheduledThreadPoolExecutor},
 * whose queue lock was taken twice per exec, was the largest throughput cost at 160 threads,
 * with the pool on or off. Instead, each thread keeps a stack of its open guards that only
 * that thread writes. One daemon watchdog thread ({@code script-timeout-watchdog}) scans the
 * stacks every {@link #TICK_MS} ms and hands each guard past its deadline to the interrupt
 * pool. Timeouts are in seconds, and the pooled clean bound is 2 s, so the tick can make an
 * interrupt up to one tick late but never early.
 *
 * <p>Invariants:
 * <ul>
 *    <li>Never early. The deadline is {@link System#nanoTime()} (monotonic) plus the timeout.
 *    The sum saturates, so a huge {@code script.execution.timeout} means "effectively never"
 *    rather than an overflow. A guard fires only once {@code now - deadline >= 0}.</li>
 *    <li>Never the wrong exec. Each exec has its own token (bug #76960, spec §6.3). The
 *    interrupt and close() race for it, and a close() that lost waits for the interrupt to
 *    finish before the Context is handed on.</li>
 *    <li>At most one interrupt per exec. The watchdog claims a guard before it submits.</li>
 *    <li>No leak (bug #77004). Nothing is queued per exec, and a closed guard stays reachable
 *    only below a newer guard of the same thread that is still open. The watchdog drops the
 *    stack of a thread that has died.</li>
 *    <li>The watchdog survives a failing tick, and a watchdog that stopped anyway is restarted
 *    by the next guard().</li>
 * </ul>
 */
public class ScriptTimeoutGuard {
   /** AutoCloseable variant that does not throw a checked exception on close(). */
   @FunctionalInterface
   public interface Guard extends AutoCloseable {
      /**
       * Ends this exec's timeout. Call it on the thread that opened the guard; both
       * production callers use try-with-resources on that thread. A close() from another
       * thread still ends the timeout, but never touches the opener's guard stack.
       */
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

   /**
    * Test hook run by each submitted interrupt task before it claims the token; null in
    * production.
    */
   static volatile Runnable interruptTaskHook;

   /** Test hook run by the watchdog at the start of every tick; null in production. */
   static volatile Runnable watchdogTickHook;

   // Separate cached pool for the blocking ctx.interrupt() calls so that
   // concurrent timeouts never queue behind each other on the watchdog thread.
   private static final ExecutorService INTERRUPT_POOL =
      Executors.newCachedThreadPool(r -> {
         Thread t = new Thread(r, "script-timeout-interrupt");
         t.setDaemon(true);
         return t;
      });

   /**
    * Test hook: the number of guard frames still reachable from the per-thread stacks, open
    * or not. A closed guard that stayed reachable would be the bug #77004 leak.
    */
   static int liveFrames() {
      int n = 0;

      for(ThreadWatch w : WATCHES) {
         for(TokenGuard g = w.top; g != null; g = g.parent) {
            n++;
         }
      }

      return n;
   }

   /** Test hook: whether the watchdog tracks the given thread. */
   static boolean tracks(Thread thread) {
      for(ThreadWatch w : WATCHES) {
         if(w.thread.get() == thread) {
            return true;
         }
      }

      return false;
   }

   /** Test hook: stop the watchdog thread as if it had died, and wait for it to end. */
   static void stopWatchdogForTest() throws InterruptedException {
      Watchdog dog = watchdog;

      if(dog != null) {
         dog.stop = true;
         LockSupport.unpark(dog.thread);
         dog.thread.join(5000);
      }
   }

   /** Test hook: whether a watchdog thread is running. */
   static boolean watchdogAlive() {
      Watchdog dog = watchdog;
      return dog != null && dog.thread.isAlive();
   }

   /** Returns a Guard that cancels the watchdog when the eval finishes. */
   public Guard guard(Context ctx, Duration timeout) {
      if(timeout == null || timeout.isZero() || timeout.isNegative()) {
         return () -> { };
      }

      ensureWatchdog();
      ThreadWatch w = THREAD_WATCH.get();
      long deadline = System.nanoTime() + saturatedNanos(timeout);
      // the volatile write of top publishes the frame's final fields to the watchdog; only
      // this thread ever writes its own top
      TokenGuard guard = new TokenGuard(ctx, deadline, w, inertTrimmed(w.top));
      w.top = guard;
      return guard;
   }

   /**
    * The timeout in nanoseconds, capped so that {@code nanoTime() + result} never overflows
    * and {@code now - deadline} stays a valid comparison. The cap is about 73 years, which
    * means "never" in practice, as it did with the old millisecond scheduler.
    */
   static long saturatedNanos(Duration timeout) {
      long nanos;

      try {
         nanos = timeout.toNanos();
      }
      catch(ArithmeticException ex) {
         nanos = Long.MAX_VALUE;
      }

      return Math.min(nanos, MAX_TIMEOUT_NANOS);
   }

   /** Skip frames that are closed or already fired; they no longer need the watchdog. */
   private static TokenGuard inertTrimmed(TokenGuard g) {
      while(g != null && g.isInert()) {
         g = g.parent;
      }

      return g;
   }

   private static void ensureWatchdog() {
      Watchdog dog = watchdog;

      if(dog == null || !dog.thread.isAlive()) {
         startWatchdog();
      }
   }

   private static synchronized void startWatchdog() {
      Watchdog dog = watchdog;

      if(dog != null && dog.thread.isAlive()) {
         return;
      }

      if(dog != null) {
         LOG.warn("The script timeout watchdog had stopped; restarting it");
      }

      Watchdog next = new Watchdog();
      next.thread.start();
      watchdog = next;
   }

   /** One scan of every thread's guard stack; runs on the watchdog thread only. */
   private static void tick() {
      Runnable hook = watchdogTickHook;

      if(hook != null) {
         hook.run();
      }

      long now = System.nanoTime();
      RuntimeException failure = null;

      for(Iterator<ThreadWatch> it = WATCHES.iterator(); it.hasNext(); ) {
         ThreadWatch w = it.next();
         Thread th = w.thread.get();

         // a dead thread runs no exec: drop its entry even if one of its guards was never
         // closed, so an unclosed frame cannot pin its Context forever
         if(th == null || !th.isAlive()) {
            it.remove();
            continue;
         }

         for(TokenGuard g = w.top; g != null; g = g.parent) {
            if(now - g.deadline >= 0 && !g.isInert() && g.claimFire()) {
               try {
                  TokenGuard fire = g;
                  INTERRUPT_POOL.submit(() -> {
                     Runnable taskHook = interruptTaskHook;

                     if(taskHook != null) {
                        taskHook.run();
                     }

                     fire.interrupt();
                  });
               }
               catch(RuntimeException ex) {
                  // e.g. no thread could be created: let the next tick try again, and still
                  // scan the other threads in this one
                  g.unclaimFire();
                  failure = ex;
               }
            }
         }
      }

      if(failure != null) {
         throw failure;
      }
   }

   private static final class Watchdog implements Runnable {
      Watchdog() {
         thread = new Thread(this, "script-timeout-watchdog");
         thread.setDaemon(true);
      }

      @Override
      public void run() {
         while(!stop) {
            LockSupport.parkNanos(TICK_NANOS);

            if(stop) {
               break;
            }

            try {
               tick();
            }
            catch(Throwable ex) {
               // a failed tick must never end the thread: every timeout on the node needs it
               long now = System.nanoTime();

               if(!failureLogged || now - lastFailureLog >= FAILURE_LOG_NANOS) {
                  failureLogged = true;
                  lastFailureLog = now;
                  LOG.warn("Script timeout watchdog tick failed", ex);
               }
            }
         }
      }

      final Thread thread;
      volatile boolean stop;
      private boolean failureLogged;
      private long lastFailureLog;
   }

   /** The guard stack of one thread. */
   private static final class ThreadWatch {
      ThreadWatch() {
         thread = new WeakReference<>(Thread.currentThread());
      }

      boolean isOwner() {
         return thread.get() == Thread.currentThread();
      }

      final WeakReference<Thread> thread;
      /** The newest frame; written only by the owner thread. */
      volatile TokenGuard top;
   }

   /**
    * The token of one exec (bug #76960, spec §6.3). The interrupt task and close() race for
    * it: whichever moves it out of ACTIVE first wins. If the interrupt won, close() waits
    * for that interrupt to finish before the exec hands the Context on, so an interrupt
    * meant for this exec can never land on the next exec on the same Context. The wait is
    * bounded by ctx.interrupt's own 2 s bound and never waits for a lock.
    */
   private static final class TokenGuard implements Guard {
      TokenGuard(Context ctx, long deadline, ThreadWatch watch, TokenGuard parent) {
         this.ctx = ctx;
         this.deadline = deadline;
         this.watch = watch;
         this.parent = parent;
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

      /** Closed, or its token has left ACTIVE: the watchdog has nothing left to do for it. */
      boolean isInert() {
         return popped || state.get() != ACTIVE;
      }

      boolean claimFire() {
         return FIRED.compareAndSet(this, 0, 1);
      }

      void unclaimFire() {
         fired = 0;
      }

      @Override
      public void close() {
         popped = true;

         if(watch.isOwner()) {
            // pop this frame and any frames below it that are closed or already fired
            if(watch.top == this) {
               watch.top = inertTrimmed(parent);
            }
         }
         else {
            // never write another thread's stack: it would race that thread's push and could
            // drop a live guard. The frame is now skipped, and its owner trims it later.
            LOG.warn("A script timeout guard was closed off the thread that opened it");
         }

         if(state.compareAndSet(ACTIVE, DONE)) {
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
      private static final AtomicIntegerFieldUpdater<TokenGuard> FIRED =
         AtomicIntegerFieldUpdater.newUpdater(TokenGuard.class, "fired");

      private final Context ctx;
      private final long deadline;
      private final ThreadWatch watch;
      private final TokenGuard parent;
      private final AtomicInteger state = new AtomicInteger(ACTIVE);
      private final CountDownLatch interruptDone = new CountDownLatch(1);
      private volatile boolean timedOut;
      private volatile boolean popped;
      private volatile int fired;
   }

   /** The watchdog's resolution. Timeouts are in seconds; the pooled clean bound is 2 s. */
   static final long TICK_MS = 20;
   private static final long TICK_NANOS = TimeUnit.MILLISECONDS.toNanos(TICK_MS);
   private static final long MAX_TIMEOUT_NANOS = Long.MAX_VALUE / 4;
   private static final long FAILURE_LOG_NANOS = TimeUnit.MINUTES.toNanos(1);

   private static final ConcurrentLinkedQueue<ThreadWatch> WATCHES = new ConcurrentLinkedQueue<>();
   private static final ThreadLocal<ThreadWatch> THREAD_WATCH = ThreadLocal.withInitial(() -> {
      ThreadWatch w = new ThreadWatch();
      WATCHES.add(w);
      return w;
   });
   private static volatile Watchdog watchdog;
   private static final Logger LOG = LoggerFactory.getLogger(ScriptTimeoutGuard.class);
}
