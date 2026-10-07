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
import org.graalvm.polyglot.PolyglotException;
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
 *    <li>No stray thread interrupt (Testing #77123). When an interrupt times out, Graal leaves
 *    the exec thread's interrupt flag set, so close() clears it on that thread. A flag that
 *    was already set when the interrupt started is not the guard's and is kept.</li>
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

      /**
       * @return {@code true} if this guard's timeout interrupted its exec (whether or not the
       * interrupt stopped it in time).
       */
      default boolean interruptFired() {
         return false;
      }
   }

   /**
    * Testing #77123: whether {@code ex} is a caller's cancel rather than a timeout. At its next
    * guest safepoint Graal turns a set thread interrupt flag into "Thread was interrupted."
    * and clears the flag, so code that catches that exception and goes on would lose the
    * cancel. An interrupt is a cancel when no timeout guard of the calling thread issued it:
    * neither {@code own} (the guard of the failed eval, if any, possibly closed by now) nor a
    * guard still open on the thread. The message cannot tell them apart: a timeout interrupt
    * may also surface as "Thread was interrupted.". Call it on the thread that ran the eval.
    *
    * @param ex  the caught exception; its cause chain is searched.
    * @param own the eval's own guard, or {@code null} if it had none.
    */
   public static boolean isCancel(Throwable ex, Guard own) {
      if(!isInterrupt(ex)) {
         return false;
      }

      // a flag that was already set when the eval's own guard fired was a cancel's (M2)
      if(own != null && own.interruptFired() &&
         !(own instanceof TokenGuard token && token.cancelPending))
      {
         return false;
      }

      // counted rather than read from the guard stack, which skips a frame once it fired
      ThreadWatch w = THREAD_WATCH.get();
      return w == null || w.firedOpen.get() <= 0;
   }

   /**
    * Testing #77123: re-assert the calling thread's interrupt flag if {@code ex} is a caller's
    * cancel (see {@link #isCancel}) whose flag Graal cleared, so a catch that goes on without
    * rethrowing it does not lose the cancel.
    *
    * @return whether the flag was re-asserted.
    */
   public static boolean keepCancel(Throwable ex, Guard own) {
      if(isCancel(ex, own)) {
         Thread.currentThread().interrupt();
         return true;
      }

      return false;
   }

   /**
    * @return whether {@code ex}, or a cause, is a Graal interrupt (a timeout's or a cancel's).
    */
   static boolean isInterrupt(Throwable ex) {
      for(int depth = 0; ex != null && depth < 16; ex = ex.getCause(), depth++) {
         if(ex instanceof PolyglotException pe && pe.isInterrupted()) {
            return true;
         }
      }

      return false;
   }

   /**
    * Whether {@code ex}, or a cause, says a script was stopped rather than failed: a Graal
    * interrupt (a timeout's or a cancel's), a cancelled context, or a script exception the
    * engine marked as such ({@link inetsoft.util.script.ScriptException#isStopped()}). A caller
    * that goes on after an ordinary script error stops at such an exception instead.
    */
   public static boolean isStop(Throwable ex) {
      for(int depth = 0; ex != null && depth < 16; ex = ex.getCause(), depth++) {
         if(ex instanceof PolyglotException pe && (pe.isInterrupted() || pe.isCancelled()) ||
            ex instanceof inetsoft.util.script.ScriptException se && se.isStopped() ||
            ex instanceof InterruptedException)
         {
            return true;
         }
      }

      return false;
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

   /**
    * Test hook run by the watchdog right before it submits an interrupt task, so a test can
    * make the submit fail; null in production.
    */
   static volatile Runnable submitHook;

   // Separate cached pool for the blocking ctx.interrupt() calls so that
   // concurrent timeouts never queue behind each other on the watchdog thread.
   private static final ExecutorService INTERRUPT_POOL =
      Executors.newCachedThreadPool(r -> {
         Thread t = new Thread(r, "script-timeout-interrupt");
         t.setDaemon(true);
         return t;
      });

   /**
    * Test hook: the number of guard frames still reachable from the given thread's stack,
    * open or not. A closed guard that stayed reachable would be the bug #77004 leak. It is
    * per thread so that other threads' guards cannot change the count.
    */
   static int liveFrames(Thread thread) {
      int n = 0;

      for(ThreadWatch w : WATCHES) {
         if(w.thread.get() == thread) {
            for(TokenGuard g = w.top; g != null; g = g.parent) {
               n++;
            }
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

   /** Test hook: the current watchdog thread, or null before the first guard. */
   static Thread watchdogThread() {
      Watchdog dog = watchdog;
      return dog == null ? null : dog.thread;
   }

   /** Returns a Guard that cancels the watchdog when the eval finishes. */
   public Guard guard(Context ctx, Duration timeout) {
      if(timeout == null || timeout.isZero() || timeout.isNegative()) {
         return () -> { };
      }

      ensureWatchdog();
      ThreadWatch w = watch();
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

   /**
    * One scan of every thread's guard stack; runs on the watchdog thread only.
    *
    * @return the last failure to submit an interrupt task in this scan, or null. It is
    * returned rather than thrown, so that the scan of the other threads goes on.
    */
   private static Throwable tick() {
      Runnable hook = watchdogTickHook;

      if(hook != null) {
         hook.run();
      }

      long now = System.nanoTime();
      Throwable failure = null;

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
                  Runnable preSubmit = submitHook;

                  if(preSubmit != null) {
                     preSubmit.run();
                  }

                  TokenGuard fire = g;
                  INTERRUPT_POOL.submit(() -> {
                     Runnable taskHook = interruptTaskHook;

                     if(taskHook != null) {
                        taskHook.run();
                     }

                     fire.interrupt();
                  });
               }
               catch(Throwable ex) {
                  // e.g. no thread could be created, which is an OutOfMemoryError rather than
                  // a RuntimeException: release the claim so the next tick tries again, and
                  // still scan the other threads in this one
                  g.unclaimFire();
                  failure = ex;
               }
            }
         }
      }

      return failure;
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
            // parkNanos returns at once while the interrupt flag is set, so a stray
            // interrupt would turn the loop into a busy spin; nothing stops this thread by
            // interrupting it (stop is the flag above), so just clear it
            Thread.interrupted();

            if(stop) {
               break;
            }

            Throwable failure;

            try {
               failure = tick();
            }
            catch(Throwable ex) {
               // a failed tick must never end the thread: every timeout on the node needs it
               failure = ex;
            }

            if(failure != null) {
               logFailure(failure);
            }
         }
      }

      private void logFailure(Throwable ex) {
         long now = System.nanoTime();

         if(!failureLogged || now - lastFailureLog >= FAILURE_LOG_NANOS) {
            failureLogged = true;
            lastFailureLog = now;

            try {
               LOG.warn("Script timeout watchdog tick failed", ex);
            }
            catch(Throwable ignore) {
               // logging can fail too, e.g. out of memory; the watchdog must keep running
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
      /**
       * The guards of this thread that fired and are not closed yet (Testing #77123,
       * {@link #isCancel}); the stack skips such a frame once it fired, so it cannot tell.
       */
      final AtomicInteger firedOpen = new AtomicInteger();
   }

   /** The calling thread's guard stack, created and registered on first use. */
   private static ThreadWatch watch() {
      ThreadWatch w = THREAD_WATCH.get();

      if(w == null) {
         w = new ThreadWatch();
         WATCHES.add(w);
         THREAD_WATCH.set(w);
      }

      return w;
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

         interruptFired = true;
         watch.firedOpen.incrementAndGet();

         // ctx.interrupt also interrupts the exec's thread (Thread.interrupt), and Graal clears
         // that flag only when the thread leaves the Context while the interrupt is still in
         // progress. A flag that is already set now is not this guard's (e.g. a cancel), so
         // close() must never clear it
         boolean ownerInterrupted = true;

         try {
            Runnable hook = beforeInterruptHook;

            if(hook != null) {
               hook.run();
            }

            Thread owner = watch.thread.get();
            ownerInterrupted = owner == null || owner.isInterrupted();
            cancelPending = owner != null && ownerInterrupted;
            ctx.interrupt(Duration.ofSeconds(2));
         }
         catch(TimeoutException ex) {
            // the interrupt gave up while the exec was still in the Context, so the owner
            // thread's interrupt flag outlives the exec and would fail the thread's next
            // wait, lock or context creation (Testing #77123); close() clears it there
            leftThreadInterrupt = !ownerInterrupted;
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

         // the interrupt won the token, so it counted this guard as fired and open
         if(FIRED_CLOSED.compareAndSet(this, 0, 1)) {
            watch.firedOpen.decrementAndGet();
         }

         // an interrupt that already finished needs no wait; the await would also throw at
         // once on the flag that interrupt may have left set
         if(interruptDone.getCount() > 0) {
            try {
               if(!interruptDone.await(3, TimeUnit.SECONDS)) {
                  // the claimed interrupt may still land on a later exec: report the Context
                  // as unknown, so a pooled one is closed instead of reused (spec §6.3, G5)
                  timedOut = true;
                  return;
               }
            }
            catch(InterruptedException ex) {
               // restore the flag and return rather than keep waiting; the claimed interrupt
               // may still land on a later exec, so report the Context as unknown too
               timedOut = true;
               Thread.currentThread().interrupt();
               return;
            }
         }

         // Testing #77123: this exec's own interrupt timed out, so Graal left the thread's
         // interrupt flag set. Clear it here, on the owner thread, so the thread's next exec,
         // context creation or wait does not fail. Only this guard's flag is cleared: one
         // that was set before the interrupt ran is kept (see interrupt())
         if(leftThreadInterrupt && watch.isOwner()) {
            Thread.interrupted();
         }
      }

      @Override
      public boolean interruptTimedOut() {
         return timedOut;
      }

      @Override
      public boolean interruptFired() {
         return interruptFired;
      }

      private static final int ACTIVE = 0;
      private static final int INTERRUPTING = 1;
      private static final int DONE = 2;
      private static final AtomicIntegerFieldUpdater<TokenGuard> FIRED =
         AtomicIntegerFieldUpdater.newUpdater(TokenGuard.class, "fired");
      private static final AtomicIntegerFieldUpdater<TokenGuard> FIRED_CLOSED =
         AtomicIntegerFieldUpdater.newUpdater(TokenGuard.class, "firedClosed");

      private final Context ctx;
      private final long deadline;
      private final ThreadWatch watch;
      private final TokenGuard parent;
      private final AtomicInteger state = new AtomicInteger(ACTIVE);
      private final CountDownLatch interruptDone = new CountDownLatch(1);
      private volatile boolean timedOut;
      /** This guard's interrupt ran (Testing #77123, {@link #isCancel}). */
      private volatile boolean interruptFired;
      /** The owner thread was already interrupted when this guard fired (a cancel's flag). */
      private volatile boolean cancelPending;
      private volatile int firedClosed;
      /** This guard's interrupt timed out and left the owner thread's interrupt flag set. */
      private volatile boolean leftThreadInterrupt;
      private volatile boolean popped;
      private volatile int fired;
   }

   /** The watchdog's resolution. Timeouts are in seconds; the pooled clean bound is 2 s. */
   static final long TICK_MS = 20;
   private static final long TICK_NANOS = TimeUnit.MILLISECONDS.toNanos(TICK_MS);
   private static final long MAX_TIMEOUT_NANOS = Long.MAX_VALUE / 4;
   private static final long FAILURE_LOG_NANOS = TimeUnit.MINUTES.toNanos(1);

   private static final ConcurrentLinkedQueue<ThreadWatch> WATCHES = new ConcurrentLinkedQueue<>();
   /** Set by {@link #watch()}; {@link #isCancel} reads it without creating one. */
   private static final ThreadLocal<ThreadWatch> THREAD_WATCH = new ThreadLocal<>();
   private static volatile Watchdog watchdog;
   private static final Logger LOG = LoggerFactory.getLogger(ScriptTimeoutGuard.class);
}
