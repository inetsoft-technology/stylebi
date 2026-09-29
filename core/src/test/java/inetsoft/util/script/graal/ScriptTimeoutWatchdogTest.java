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
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testing #77123: the lock-free deadline watchdog that replaced the per-exec scheduler.
 */
@Tag("core")
class ScriptTimeoutWatchdogTest {
   /** A loop that ends on its own after 30 s, so a missed timeout fails instead of hanging. */
   private static final String LOOP = "var t = Date.now(); while(Date.now() - t < 30000) {} 1";

   @AfterEach
   void clearHooks() {
      ScriptTimeoutGuard.watchdogTickHook = null;
      ScriptTimeoutGuard.interruptTaskHook = null;
      ScriptTimeoutGuard.beforeInterruptHook = null;
   }

   /** Refute amendment 1: a tick that throws, even an Error, must not end the watchdog. */
   @Test void failingTicksDoNotStopTimeouts() {
      AtomicInteger failed = new AtomicInteger();
      ScriptTimeoutGuard.watchdogTickHook = () -> {
         int n = failed.incrementAndGet();

         if(n <= 3) {
            throw new IllegalStateException("tick failure " + n);
         }
         else if(n <= 5) {
            throw new AssertionError("tick error " + n);
         }
      };

      try(Context ctx = Context.newBuilder("js").build()) {
         assertInterrupted(ctx, Duration.ofMillis(200));
      }

      assertTrue(failed.get() > 5, "the failing ticks did not run: " + failed.get());
      assertTrue(ScriptTimeoutGuard.watchdogAlive());
   }

   /** Refute amendment 1: a watchdog that stopped anyway is restarted by the next guard. */
   @Test void stoppedWatchdogIsRestarted() throws Exception {
      try(Context ctx = Context.newBuilder("js").build()) {
         try(var ignored = new ScriptTimeoutGuard().guard(ctx, Duration.ofSeconds(5))) {
            assertTrue(ScriptTimeoutGuard.watchdogAlive());
         }

         ScriptTimeoutGuard.stopWatchdogForTest();
         assertFalse(ScriptTimeoutGuard.watchdogAlive());
         assertInterrupted(ctx, Duration.ofMillis(200));
         assertTrue(ScriptTimeoutGuard.watchdogAlive());
      }
   }

   /**
    * Refute amendment 2: main accepts a huge script.execution.timeout (seconds) as "never"; the
    * nanosecond deadline must saturate instead of throwing ArithmeticException.
    */
   @Test void hugeTimeoutIsAccepted() {
      try(Context ctx = Context.newBuilder("js").build()) {
         for(long secs : new long[] { 10_000L, 10_000_000_000L, 9_000_000_000_000_000L,
                                      Long.MAX_VALUE })
         {
            try(var ignored = new ScriptTimeoutGuard().guard(ctx, Duration.ofSeconds(secs))) {
               assertEquals(3, ctx.eval("js", "1+2").asInt(), "timeout " + secs + " s");
            }
         }
      }

      assertEquals(Long.MAX_VALUE / 4,
                   ScriptTimeoutGuard.saturatedNanos(Duration.ofSeconds(Long.MAX_VALUE)));
      assertEquals(Long.MAX_VALUE / 4,
                   ScriptTimeoutGuard.saturatedNanos(Duration.ofSeconds(10_000_000_000L)));
      assertEquals(TimeUnit.SECONDS.toNanos(600),
                   ScriptTimeoutGuard.saturatedNanos(Duration.ofSeconds(600)));
   }

   /**
    * Refute amendment 3: an overdue guard whose interrupt task has not started yet must not be
    * submitted again on every tick.
    */
   @Test void interruptIsSubmittedOncePerExec() {
      AtomicInteger tasks = new AtomicInteger();
      ScriptTimeoutGuard.interruptTaskHook = () -> {
         // the first task stays unstarted for about 15 ticks, with its token still ACTIVE
         if(tasks.incrementAndGet() == 1) {
            sleep(300);
         }
      };

      try(Context ctx = Context.newBuilder("js").build()) {
         assertInterrupted(ctx, Duration.ofMillis(100));
      }

      sleep(100);
      assertEquals(1, tasks.get(), "interrupt tasks submitted for one exec");
   }

   /**
    * Refute amendment 5: a close() off the opening thread must not touch the opener's stack
    * (it would race the opener's push and could drop a live guard), and must still end its
    * own timeout.
    */
   @Test void closeFromAnotherThreadLeavesTheOwnerStackAlone() throws Exception {
      try(Context ctx = Context.newBuilder("js").build();
          Context other = Context.newBuilder("js").build())
      {
         ScriptTimeoutGuard guard = new ScriptTimeoutGuard();
         ScriptTimeoutGuard.Guard foreign = guard.guard(other, Duration.ofMillis(100));
         int frames = ScriptTimeoutGuard.liveFrames();
         Thread closer = new Thread(foreign::close);
         closer.start();
         closer.join(5000);

         // the frame stays on its owner's stack (only skipped) until the owner trims it
         assertEquals(frames, ScriptTimeoutGuard.liveFrames());
         // it ended its own timeout: the Context it guarded is never interrupted
         assertEquals(1, other.eval("js", "var t = Date.now(); while(Date.now() - t < 500) {} 1")
            .asInt());
         // the owner's next guard is pushed above it, trims it, and still times out
         assertInterrupted(ctx, Duration.ofMillis(200));
      }
   }

   /**
    * Refute amendment 6: the stack of a thread that died with an unclosed guard is dropped,
    * so that frame cannot pin its Context forever.
    */
   @Test void deadThreadWithAnUnclosedGuardIsDropped() throws Exception {
      try(Context ctx = Context.newBuilder("js").build()) {
         int before = ScriptTimeoutGuard.liveFrames();
         CountDownLatch opened = new CountDownLatch(1);
         CountDownLatch exit = new CountDownLatch(1);
         Thread leaker = new Thread(() -> {
            new ScriptTimeoutGuard().guard(ctx, Duration.ofHours(1));
            opened.countDown();

            try {
               exit.await(10, TimeUnit.SECONDS);
            }
            catch(InterruptedException ignore) {
               // exit anyway
            }
         });
         leaker.start();
         assertTrue(opened.await(5, TimeUnit.SECONDS));
         // while the thread lives, its unclosed frame is tracked (so the check below is real)
         assertTrue(ScriptTimeoutGuard.tracks(leaker));
         assertEquals(before + 1, ScriptTimeoutGuard.liveFrames());
         exit.countDown();
         leaker.join(5000);
         long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);

         while(ScriptTimeoutGuard.tracks(leaker) && System.nanoTime() < end) {
            sleep(20);
         }

         assertFalse(ScriptTimeoutGuard.tracks(leaker), "a dead thread is still tracked");
         assertEquals(before, ScriptTimeoutGuard.liveFrames());
      }
   }

   /**
    * 160 threads, each exec with its own guard: every looping exec is interrupted, and no exec
    * is interrupted before its own deadline. A thread whose interrupt could not stop its exec
    * stops, since its Context is then unknown (bug #76960) and later execs on it prove nothing.
    */
   @Test void everyLoopingExecIsInterruptedAndNoneEarlyAt160Threads() throws Exception {
      int threads = 160;
      int iters = 8;
      long timeoutMs = 300;
      AtomicInteger loops = new AtomicInteger();
      AtomicInteger loopsInterrupted = new AtomicInteger();
      AtomicInteger early = new AtomicInteger();
      AtomicReference<Throwable> error = new AtomicReference<>();
      Engine engine = Engine.newBuilder().option("engine.WarnInterpreterOnly", "false").build();
      ExecutorService pool = Executors.newFixedThreadPool(threads);
      List<Future<?>> futures = new ArrayList<>();
      ScriptTimeoutGuard guard = new ScriptTimeoutGuard();

      try {
         for(int t = 0; t < threads; t++) {
            int tid = t;
            futures.add(pool.submit(() -> {
               try(Context ctx = Context.newBuilder("js").engine(engine).build()) {
                  for(int i = 0; i < iters; i++) {
                     boolean loop = (tid + i) % 4 == 0;
                     long start = System.nanoTime();
                     ScriptTimeoutGuard.Guard outer = guard.guard(ctx, Duration.ofMillis(timeoutMs));
                     ScriptTimeoutGuard.Guard inner = null;

                     if(loop) {
                        loops.incrementAndGet();
                     }

                     try(outer) {
                        if(loop) {
                           ctx.eval("js", LOOP);
                        }
                        else {
                           // a nested guard, closed normally, while the outer one stays open
                           inner = guard.guard(ctx, Duration.ofMillis(timeoutMs));

                           try(var ignored = inner) {
                              ctx.eval("js", "var t = Date.now(); while(Date.now() - t < 20) {} 1");
                           }
                        }
                     }
                     catch(PolyglotException ex) {
                        long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

                        if(!ex.isInterrupted() && !ex.isCancelled()) {
                           throw ex;
                        }

                        if(elapsed < timeoutMs) {
                           early.incrementAndGet();
                        }

                        if(loop) {
                           loopsInterrupted.incrementAndGet();
                        }
                     }

                     if(outer.interruptTimedOut() || inner != null && inner.interruptTimedOut()) {
                        break;
                     }
                  }
               }
               catch(Throwable ex) {
                  error.compareAndSet(null, ex);
               }

               return null;
            }));
         }

         for(Future<?> f : futures) {
            f.get(5, TimeUnit.MINUTES);
         }
      }
      finally {
         pool.shutdownNow();
         engine.close(true);
      }

      assertNull(error.get(), () -> "an exec failed: " + error.get());
      assertTrue(loops.get() > 0);
      assertEquals(loops.get(), loopsInterrupted.get(), "looping execs not interrupted");
      assertEquals(0, early.get(), "execs interrupted before their deadline");
   }

   /** Run a 30 s loop under the given timeout and require that it is interrupted, not early. */
   private static void assertInterrupted(Context ctx, Duration timeout) {
      long start = System.nanoTime();
      PolyglotException ex = assertThrows(PolyglotException.class, () -> {
         try(var ignored = new ScriptTimeoutGuard().guard(ctx, timeout)) {
            ctx.eval("js", LOOP);
         }
      });
      long elapsed = System.nanoTime() - start;
      assertTrue(ex.isInterrupted() || ex.isCancelled(), ex.toString());
      assertTrue(elapsed >= timeout.toNanos(), "interrupted early: " + elapsed + " ns");
      assertTrue(elapsed < TimeUnit.SECONDS.toNanos(20), "interrupted late: " + elapsed + " ns");
   }

   private static void sleep(long ms) {
      try {
         Thread.sleep(ms);
      }
      catch(InterruptedException ex) {
         Thread.currentThread().interrupt();
      }
   }
}
