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

import org.graalvm.polyglot.*;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.junit.jupiter.api.*;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

@Tag("slow")
class ScriptTimeoutGuardTest {
   @Test void interruptsRunawayScript() {
      try(Context ctx = Context.newBuilder("js").build()) {
         ScriptTimeoutGuard guard = new ScriptTimeoutGuard();
         PolyglotException ex = assertThrows(PolyglotException.class, () -> {
            try(var ignored = guard.guard(ctx, Duration.ofMillis(300))) {
               ctx.eval("js", "while(true){}");
            }
         });
         assertTrue(ex.isInterrupted() || ex.isCancelled());
      }
   }

   @Test void zeroDurationMeansNoTimeout() {
      try(Context ctx = Context.newBuilder("js").build()) {
         ScriptTimeoutGuard guard = new ScriptTimeoutGuard();
         try(var ignored = guard.guard(ctx, Duration.ZERO)) {
            assertEquals(3, ctx.eval("js", "1+2").asInt());
         }
      }
   }

   /**
    * Spec §6.3 / G5a (bug #76960): a timeout that fired while exec A was still running, but
    * whose interrupt lands only after A finished, must not interrupt the next exec B on the
    * same Context.
    */
   @Test void lateInterruptDoesNotHitTheNextExec() {
      try(Context ctx = Context.newBuilder("js").build()) {
         ScriptTimeoutGuard guard = new ScriptTimeoutGuard();
         CountDownLatch bStarted = new CountDownLatch(1);
         // hold the claimed interrupt until B has started (or 1 s passed)
         ScriptTimeoutGuard.beforeInterruptHook = () -> {
            try {
               bStarted.await(1, TimeUnit.SECONDS);
            }
            catch(InterruptedException ex) {
               Thread.currentThread().interrupt();
            }
         };

         try {
            try(var a = guard.guard(ctx, Duration.ofMillis(50))) {
               // A runs past its 50 ms timeout; the interrupt is claimed but held by the hook
               ctx.eval("js", "var t = Date.now(); while(Date.now() - t < 200) {} 1");
            }

            bStarted.countDown();
            // B: no guard of its own; it must not be hit by A's late interrupt
            assertEquals(42, ctx.eval("js",
               "var t2 = Date.now(); while(Date.now() - t2 < 1500) {} 42").asInt());
         }
         finally {
            ScriptTimeoutGuard.beforeInterruptHook = null;
         }
      }
   }

   /**
    * Bug #77004 (pool off too): a closed guard must stop being reachable at once, not stay
    * live until its deadline (script.execution.timeout seconds away).
    */
   @Test void closedGuardsDoNotStayQueued() {
      try(Context ctx = Context.newBuilder("js").build()) {
         ScriptTimeoutGuard guard = new ScriptTimeoutGuard();
         int before = ScriptTimeoutGuard.liveFrames(Thread.currentThread());

         // the hook counts an open guard, so the count below is not vacuous
         try(var open = guard.guard(ctx, Duration.ofHours(1))) {
            assertEquals(before + 1, ScriptTimeoutGuard.liveFrames(Thread.currentThread()));
         }

         for(int i = 0; i < 10_000; i++) {
            try(var ignored = guard.guard(ctx, Duration.ofHours(1))) {
               // nothing: the exec finishes long before its timeout
            }
         }

         int after = ScriptTimeoutGuard.liveFrames(Thread.currentThread());
         assertTrue(after - before < 10, "live guards grew by " + (after - before));
      }
   }

   /**
    * Final review M1 (bug #76960, pool off too): if close() gives up waiting for a claimed
    * interrupt, that interrupt may still land on a later exec on this Context, so the guard
    * must report interruptTimedOut() and the Context be treated as unknown.
    */
   @Test void closeThatGivesUpWaitingForTheInterruptReportsTimeout() throws Exception {
      try(Context ctx = Context.newBuilder("js").build()) {
         CountDownLatch release = new CountDownLatch(1);
         ScriptTimeoutGuard.Guard guard = claimAndHoldInterrupt(ctx, release);

         try {
            // the held interrupt outlasts close()'s 3 s wait
            guard.close();
            assertTrue(guard.interruptTimedOut());
         }
         finally {
            release.countDown();
            ScriptTimeoutGuard.beforeInterruptHook = null;
         }
      }
   }

   /**
    * Final review M1: a close() interrupted while it waits restores the flag and also
    * reports interruptTimedOut(), since the claimed interrupt has not finished.
    */
   @Test void closeInterruptedWhileWaitingReportsTimeout() throws Exception {
      try(Context ctx = Context.newBuilder("js").build()) {
         CountDownLatch release = new CountDownLatch(1);
         ScriptTimeoutGuard.Guard guard = claimAndHoldInterrupt(ctx, release);

         try {
            Thread.currentThread().interrupt();
            guard.close();
            assertTrue(Thread.interrupted(), "the interrupt flag must be restored");
            assertTrue(guard.interruptTimedOut());
         }
         finally {
            Thread.interrupted();
            release.countDown();
            ScriptTimeoutGuard.beforeInterruptHook = null;
         }
      }
   }

   /**
    * Run an exec past its 50 ms timeout while the interrupt task, having claimed the token,
    * is held until {@code release}; returns the still-open guard.
    */
   private static ScriptTimeoutGuard.Guard claimAndHoldInterrupt(Context ctx,
                                                                 CountDownLatch release)
      throws InterruptedException
   {
      CountDownLatch claimed = new CountDownLatch(1);
      ScriptTimeoutGuard.beforeInterruptHook = () -> {
         claimed.countDown();

         try {
            release.await(10, TimeUnit.SECONDS);
         }
         catch(InterruptedException ex) {
            Thread.currentThread().interrupt();
         }
      };

      ScriptTimeoutGuard.Guard guard = new ScriptTimeoutGuard().guard(ctx, Duration.ofMillis(50));
      ctx.eval("js", "var t = Date.now(); while(Date.now() - t < 200) {} 1");
      assertTrue(claimed.await(5, TimeUnit.SECONDS), "the interrupt was not claimed");
      return guard;
   }

   @Test void noOpGuardReportsNoInterruptTimeout() {
      ScriptTimeoutGuard.Guard none = new ScriptTimeoutGuard().guard(null, Duration.ZERO);
      assertFalse(none.interruptTimedOut());
   }

   /**
    * Bug #76960: while the exec sits in a host callback longer than ctx.interrupt's 2 s bound,
    * the interrupt cannot stop it, so the guard must report interruptTimedOut().
    */
   @Test void interruptThatCannotStopTheExecIsReported() {
      try(Context ctx = Context.newBuilder("js").build()) {
         CountDownLatch inHost = new CountDownLatch(1);
         ctx.getBindings("js").putMember("hostSleep", hostSleep(inHost));
         holdInterruptUntil(inHost);

         try {
            ScriptTimeoutGuard.Guard guard =
               new ScriptTimeoutGuard().guard(ctx, Duration.ofMillis(50));

            try(guard) {
               ctx.eval("js", "hostSleep()");
            }
            catch(PolyglotException ignore) {
               // the interrupt may still land once guest code resumes; not what is tested
            }

            assertTrue(guard.interruptTimedOut());
         }
         finally {
            ScriptTimeoutGuard.beforeInterruptHook = null;
         }
      }
   }

   /**
    * Bug #76960: exec's finally must call onInterruptTimeout() when this exec's interrupt
    * timed out.
    */
   @Test void execCallsOnInterruptTimeoutWhenInterruptTimesOut() throws Exception {
      AtomicInteger calls = new AtomicInteger();
      GraalJavaScriptEngine engine = new GraalJavaScriptEngine() {
         @Override
         protected Duration currentTimeout() {
            return Duration.ofMillis(50);
         }

         @Override
         protected void onInterruptTimeout() {
            calls.incrementAndGet();
         }
      };

      engine.init(new java.util.HashMap<>());

      try {
         CountDownLatch inHost = new CountDownLatch(1);
         engine.context.getBindings("js").putMember("hostSleep", hostSleep(inHost));
         holdInterruptUntil(inHost);
         Object src = engine.compile("hostSleep()");

         try {
            engine.exec(src, null, null);
         }
         catch(Exception ignore) {
            // the interrupt may still land once guest code resumes; not what is tested
         }
         finally {
            ScriptTimeoutGuard.beforeInterruptHook = null;
         }

         assertEquals(1, calls.get());
      }
      finally {
         engine.close();
      }
   }

   /**
    * Testing #77123: ctx.interrupt interrupts the exec's thread, and when it times out (the
    * exec sits in a host call that ignores interrupts), Graal never clears that flag. The
    * next wait, lock or context creation on the thread then failed once, although it has
    * nothing to do with the timed-out exec. close() must clear the guard's own flag.
    */
   @Test void timedOutInterruptDoesNotLeaveTheThreadInterrupted() throws Exception {
      Thread.interrupted();

      try(Context ctx = Context.newBuilder("js").build()) {
         CountDownLatch inHost = new CountDownLatch(1);
         ctx.getBindings("js").putMember("hostSpin", hostSpin(inHost, null));
         holdInterruptUntil(inHost);

         try {
            ScriptTimeoutGuard.Guard guard =
               new ScriptTimeoutGuard().guard(ctx, Duration.ofMillis(50));

            try(guard) {
               ctx.eval("js", "hostSpin()");
            }
            catch(PolyglotException ignore) {
               // not what is tested
            }

            assertTrue(guard.interruptTimedOut(), "the interrupt must have timed out");
            assertFalse(Thread.currentThread().isInterrupted(),
                        "the timed-out interrupt left the thread interrupted");
         }
         finally {
            ScriptTimeoutGuard.beforeInterruptHook = null;
            Thread.interrupted();
         }
      }
   }

   /**
    * Testing #77123, end to end with the pool off: after an exec whose interrupt timed out,
    * the same thread's next exec, sleep and new Context all work.
    */
   @Test void nextWorkOnTheThreadSucceedsAfterAnInterruptTimeout() throws Exception {
      Thread.interrupted();
      GraalJavaScriptEngine engine = new GraalJavaScriptEngine() {
         @Override
         protected Duration currentTimeout() {
            return Duration.ofMillis(50);
         }
      };

      engine.init(new java.util.HashMap<>());

      try {
         CountDownLatch inHost = new CountDownLatch(1);
         engine.context.getBindings("js").putMember("hostSpin", hostSpin(inHost, null));
         holdInterruptUntil(inHost);

         try {
            engine.exec(engine.compile("hostSpin(); 5"), null, null);
         }
         catch(Exception ignore) {
            // not what is tested
         }
         finally {
            ScriptTimeoutGuard.beforeInterruptHook = null;
         }

         assertFalse(Thread.currentThread().isInterrupted(),
                     "the timed-out interrupt left the thread interrupted");
         Thread.sleep(1);

         try(Context next = Context.newBuilder("js").build()) {
            assertEquals(3, next.eval("js", "1+2").asInt());
         }

         assertEquals(2, ((Number) engine.exec(engine.compile("1+1"), null, null)).intValue());
      }
      finally {
         Thread.interrupted();
         engine.close();
      }
   }

   /**
    * Testing #77123: an interrupt of the exec's thread from elsewhere (e.g. a cancel) that
    * came before the timeout's interrupt is not the guard's, and close() must keep it.
    */
   @Test void anEarlierInterruptOfTheThreadIsKept() throws Exception {
      Thread.interrupted();

      try(Context ctx = Context.newBuilder("js").build()) {
         CountDownLatch inHost = new CountDownLatch(1);
         // the host call interrupts its own thread (as a cancel would) before the guard's
         // interrupt starts
         ctx.getBindings("js").putMember(
            "hostSpin", hostSpin(inHost, () -> Thread.currentThread().interrupt()));
         holdInterruptUntil(inHost);

         try {
            ScriptTimeoutGuard.Guard guard =
               new ScriptTimeoutGuard().guard(ctx, Duration.ofMillis(50));

            try(guard) {
               ctx.eval("js", "hostSpin()");
            }
            catch(PolyglotException ignore) {
               // not what is tested
            }

            assertTrue(guard.interruptTimedOut(), "the interrupt must have timed out");
            assertTrue(Thread.currentThread().isInterrupted(), "the cancel's interrupt was lost");
         }
         finally {
            ScriptTimeoutGuard.beforeInterruptHook = null;
            Thread.interrupted();
         }
      }
   }

   /**
    * Testing #77123: an interrupt of the thread that comes after the exec is kept too; the
    * guard clears only in its own close().
    */
   @Test void anInterruptAfterTheExecIsKept() throws Exception {
      Thread.interrupted();

      try(Context ctx = Context.newBuilder("js").build()) {
         CountDownLatch inHost = new CountDownLatch(1);
         ctx.getBindings("js").putMember("hostSpin", hostSpin(inHost, null));
         holdInterruptUntil(inHost);

         try {
            try(var ignored = new ScriptTimeoutGuard().guard(ctx, Duration.ofMillis(50))) {
               ctx.eval("js", "hostSpin()");
            }
            catch(PolyglotException ignore) {
               // not what is tested
            }

            Thread.currentThread().interrupt();

            // a later exec whose guard never fires must not touch the flag
            try(var ignored = new ScriptTimeoutGuard().guard(ctx, Duration.ofSeconds(30))) {
               ctx.eval("js", "1");
            }
            catch(PolyglotException ignore) {
               // Graal may report the pending interrupt; not what is tested
            }

            assertTrue(Thread.currentThread().isInterrupted(), "a later interrupt was lost");
         }
         finally {
            ScriptTimeoutGuard.beforeInterruptHook = null;
            Thread.interrupted();
         }
      }
   }

   /**
    * Testing #77123 (CX1/CX2): an eval that the thread's own interrupt flag stopped is a
    * caller's cancel, which keepCancel re-asserts, since Graal cleared the flag.
    */
   @Test void anInterruptOfTheThreadIsACancel() {
      Thread.interrupted();

      try(Context ctx = Context.newBuilder("js").build()) {
         Thread.currentThread().interrupt();
         PolyglotException ex = assertThrows(PolyglotException.class,
            () -> ctx.eval("js", "for(var i = 0; i < 1e9; i++) {} 1"));
         assertTrue(ex.isInterrupted(), String.valueOf(ex));
         assertFalse(Thread.currentThread().isInterrupted(), "Graal no longer clears the flag");
         assertTrue(ScriptTimeoutGuard.isCancel(ex, null));
         assertTrue(ScriptTimeoutGuard.isCancel(new RuntimeException(ex), null), "a cause");
         assertTrue(ScriptTimeoutGuard.keepCancel(ex, null));
         assertTrue(Thread.interrupted(), "keepCancel did not re-assert the flag");
      }
      finally {
         Thread.interrupted();
      }
   }

   /**
    * Testing #77123: an eval stopped by its own guard's timeout, or by the timeout of a guard
    * still open on the thread, is not a cancel; keepCancel leaves the thread uninterrupted.
    */
   @Test void anInterruptOfATimeoutGuardIsNotACancel() {
      Thread.interrupted();

      try(Context ctx = Context.newBuilder("js").build();
          Context outer = Context.newBuilder("js").build())
      {
         ScriptTimeoutGuard guard = new ScriptTimeoutGuard();
         ScriptTimeoutGuard.Guard own = guard.guard(ctx, Duration.ofMillis(50));
         PolyglotException ex;

         try(own) {
            ex = assertThrows(PolyglotException.class, () -> ctx.eval("js", "while(true){}"));
         }

         assertTrue(own.interruptFired());
         assertTrue(ex.isInterrupted(), String.valueOf(ex));
         assertFalse(ScriptTimeoutGuard.isCancel(ex, own));
         assertFalse(ScriptTimeoutGuard.keepCancel(ex, own));
         assertFalse(Thread.currentThread().isInterrupted());

         // the same exception inside an open guard of the thread that fired
         try(var open = guard.guard(outer, Duration.ofMillis(50))) {
            long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);

            while(!open.interruptFired() && System.nanoTime() - end < 0) {
               Thread.onSpinWait();
            }

            assertTrue(open.interruptFired());
            assertFalse(ScriptTimeoutGuard.isCancel(ex, null));
         }

         // once that guard is closed, the thread has no fired guard open
         assertTrue(ScriptTimeoutGuard.isCancel(ex, null));
         assertFalse(ScriptTimeoutGuard.isCancel(new RuntimeException("x"), null));
         assertFalse(new ScriptTimeoutGuard().guard(ctx, Duration.ZERO).interruptFired());
      }
      finally {
         Thread.interrupted();
      }
   }

   /**
    * Testing #77123 (review I1): an outer guard that fired but is not closed stays a timeout's
    * interrupt after a nested guard opened and closed, although the guard stack then skips
    * the fired frame; keepCancel must not set a flag that would outlive the outer guard.
    */
   @Test void aFiredOuterGuardIsSeenAfterANestedGuard() {
      Thread.interrupted();

      try(Context ctx = Context.newBuilder("js").build();
          Context outer = Context.newBuilder("js").build())
      {
         PolyglotException ex = interruptOfTheThread(ctx);
         ScriptTimeoutGuard guard = new ScriptTimeoutGuard();

         try(var open = guard.guard(outer, Duration.ofMillis(50))) {
            long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);

            while(!open.interruptFired() && System.nanoTime() - end < 0) {
               Thread.onSpinWait();
            }

            assertTrue(open.interruptFired());

            try(var nested = guard.guard(ctx, Duration.ofSeconds(30))) {
               assertEquals(1, ctx.eval("js", "1").asInt());
            }

            assertFalse(ScriptTimeoutGuard.isCancel(ex, null), "the fired outer guard was lost");
            assertFalse(ScriptTimeoutGuard.keepCancel(ex, null));
         }

         assertFalse(Thread.interrupted(), "a flag outlived the outer guard");
         assertTrue(ScriptTimeoutGuard.isCancel(ex, null), "the outer guard is closed");
      }
      finally {
         Thread.interrupted();
      }
   }

   /**
    * Testing #77123 (review M2): a cancel whose flag was already set when the eval's own guard
    * fired is still a cancel. The hook cancels the exec thread right before the guard reads
    * its flag; the host call stays out of guest code until the guard's interrupt gave up (so
    * the read is done), then the eval stops on the pending interrupt.
    */
   @Test void aCancelPendingWhenTheOwnGuardFiredIsACancel() {
      Thread.interrupted();
      Thread self = Thread.currentThread();

      try(Context ctx = Context.newBuilder("js").build()) {
         ScriptTimeoutGuard.Guard[] own = new ScriptTimeoutGuard.Guard[1];
         ctx.getBindings("js").putMember("hold", (ProxyExecutable) args -> {
            long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);

            while(!own[0].interruptTimedOut() && System.nanoTime() - end < 0) {
               Thread.onSpinWait();
            }

            return 1;
         });
         ScriptTimeoutGuard.beforeInterruptHook = self::interrupt;
         own[0] = new ScriptTimeoutGuard().guard(ctx, Duration.ofMillis(50));
         PolyglotException ex = null;

         try(var guard = own[0]) {
            ctx.eval("js", "hold(); for(var i = 0; i < 1e9; i++) {} 1");
         }
         catch(PolyglotException caught) {
            ex = caught;
         }
         finally {
            ScriptTimeoutGuard.beforeInterruptHook = null;
         }

         assertNotNull(ex, "the eval was not stopped");
         assertTrue(own[0].interruptFired());
         assertTrue(own[0].interruptTimedOut());
         Thread.interrupted();
         assertTrue(ScriptTimeoutGuard.isCancel(ex, own[0]), "the pending cancel was dropped");
      }
      finally {
         Thread.interrupted();
      }
   }

   /** @return the exception of an eval that the thread's own interrupt flag stopped. */
   private static PolyglotException interruptOfTheThread(Context ctx) {
      Thread.currentThread().interrupt();
      PolyglotException ex = assertThrows(PolyglotException.class,
         () -> ctx.eval("js", "for(var i = 0; i < 1e9; i++) {} 1"));
      Thread.interrupted();
      return ex;
   }

   /**
    * A host callback that signals entry, optionally runs {@code atEntry}, then busy-spins for
    * 3 s ignoring interrupts, longer than ctx.interrupt's 2 s bound, so the interrupt (held
    * until entry by the hook) times out while the exec is still in the Context.
    */
   private static ProxyExecutable hostSpin(CountDownLatch inHost, Runnable atEntry) {
      return args -> {
         if(atEntry != null) {
            atEntry.run();
         }

         inHost.countDown();
         long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);

         while(System.nanoTime() - end < 0) {
            Thread.onSpinWait();
         }

         return 1;
      };
   }

   /** Hold the claimed interrupt until the exec is inside the host callback. */
   private static void holdInterruptUntil(CountDownLatch inHost) {
      ScriptTimeoutGuard.beforeInterruptHook = () -> {
         try {
            inHost.await(10, TimeUnit.SECONDS);
         }
         catch(InterruptedException ex) {
            Thread.currentThread().interrupt();
         }
      };
   }

   /**
    * A host callback that signals entry, then stays out of guest code for 3 s, longer than
    * ctx.interrupt's 2 s bound (the interrupt starts only after entry, via the hook).
    */
   private static ProxyExecutable hostSleep(CountDownLatch inHost) {
      return args -> {
         inHost.countDown();
         long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
         boolean interrupted = false;

         for(long left; (left = end - System.nanoTime()) > 0; ) {
            try {
               TimeUnit.NANOSECONDS.sleep(left);
            }
            catch(InterruptedException ex) {
               interrupted = true;
            }
         }

         if(interrupted) {
            Thread.currentThread().interrupt();
         }

         return 1;
      };
   }
}
