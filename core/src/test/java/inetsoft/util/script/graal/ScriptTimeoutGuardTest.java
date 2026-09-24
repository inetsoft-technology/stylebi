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

@Tag("core")
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
    * Bug #77004 (pool off too): a closed guard's cancelled watchdog must leave the scheduler
    * queue at once, not stay live until its deadline (script.execution.timeout seconds away).
    */
   @Test void closedGuardsDoNotStayQueued() {
      try(Context ctx = Context.newBuilder("js").build()) {
         ScriptTimeoutGuard guard = new ScriptTimeoutGuard();
         int before = ScriptTimeoutGuard.queuedTasks();

         for(int i = 0; i < 10_000; i++) {
            try(var ignored = guard.guard(ctx, Duration.ofHours(1))) {
               // nothing: the exec finishes long before its timeout
            }
         }

         int after = ScriptTimeoutGuard.queuedTasks();
         assertTrue(after - before < 10, "queued watchdogs grew by " + (after - before));
      }
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
