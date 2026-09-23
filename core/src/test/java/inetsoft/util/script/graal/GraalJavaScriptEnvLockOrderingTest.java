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
package inetsoft.util.script.graal;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for bug #76905 (reopened): the {@link GraalJavaScriptEnv} monitor must be a
 * leaf with respect to the engine's execution lock.
 *
 * <p>The live deadlock was: a script thread held the engine lock (inside
 * {@code GraalJavaScriptEngine.exec}) and called back into {@code senv.put()} from
 * {@code CalcTableLens.evaluate()}, waiting for the env monitor; a non-script thread was inside
 * the {@code synchronized put()} holding the env monitor and waiting for the engine lock. The
 * same inversion existed in {@code compile()} (reached on every formula cache miss) and in the
 * first-time {@code init()}, which published the engine before initializing it.
 *
 * <p>All threads are daemons and every wait is bounded, so a regression fails instead of hanging
 * the build.
 */
@Tag("core")
class GraalJavaScriptEnvLockOrderingTest {
   @Test
   void putFromEngineLockHolderDoesNotDeadlockWithConcurrentPut() throws Exception {
      GraalJavaScriptEnv env = new GraalJavaScriptEnv();
      env.init();

      runAgainstEngineLockHolder(env, () -> {
         env.put("fromScript", 1);
         return null;
      }, () -> {
         env.put("other", 2);
         return null;
      });

      assertEquals(1, ((Number) env.get("fromScript")).intValue());
      assertEquals(2, ((Number) env.get("other")).intValue());
   }

   @Test
   void compileFromEngineLockHolderDoesNotDeadlockWithConcurrentCompile() throws Exception {
      GraalJavaScriptEnv env = new GraalJavaScriptEnv();
      env.init();

      // multi-statement body: compile() parse-validates the split pieces under the engine lock
      // (GraalJavaScriptEngine.piecesAllParse), which is what CalcTableLens.evaluate() reaches
      // through ScriptCache on a formula cache miss
      runAgainstEngineLockHolder(env, () -> env.compile(SPLIT_SCRIPT),
                                 () -> env.compile(SPLIT_SCRIPT + " "));
   }

   /**
    * The first-time init() used to assign the engine field before running engine.init(vars),
    * all under the env monitor. A second thread could read the just-published engine, take its
    * execution lock and then block on the env monitor, while the initializer blocked on that
    * same execution lock inside engine.init().
    */
   @Test
   void firstInitDoesNotPublishEngineBeforeItIsInitialized() throws Exception {
      CountDownLatch engineCreated = new CountDownLatch(1);
      CountDownLatch otherThreadReady = new CountDownLatch(1);
      AtomicBoolean first = new AtomicBoolean(true);

      GraalJavaScriptEnv env = new GraalJavaScriptEnv() {
         @Override
         protected GraalJavaScriptEngine createScriptEngine() {
            return new GraalJavaScriptEngine() {
               // called by init() after the engine is created and before engine.init(vars)
               @Override
               public void setSQL(boolean sql) {
                  super.setSQL(sql);

                  if(first.compareAndSet(true, false)) {
                     engineCreated.countDown();

                     try {
                        otherThreadReady.await(5, TimeUnit.SECONDS);
                     }
                     catch(InterruptedException ex) {
                        Thread.currentThread().interrupt();
                     }
                  }
               }
            };
         }
      };

      ExecutorService pool = newDaemonPool();

      try {
         Future<?> initializer = pool.submit(() -> {
            env.put("a", 1);
            return null;
         });

         Future<?> other = pool.submit(() -> {
            assertTrue(engineCreated.await(10, TimeUnit.SECONDS), "engine was never created");
            // whatever engine this thread can see now, hold its lock the way a script thread
            // would, then call back into the env
            Lock lock = env.getExecutionLock();

            if(lock != null) {
               lock.lock();
            }

            try {
               otherThreadReady.countDown();
               env.put("b", 2);
            }
            finally {
               if(lock != null) {
                  lock.unlock();
               }
            }

            return null;
         });

         initializer.get(15, TimeUnit.SECONDS);
         other.get(15, TimeUnit.SECONDS);
      }
      finally {
         pool.shutdownNow();
      }

      assertEquals(1, ((Number) env.get("a")).intValue());
      assertEquals(2, ((Number) env.get("b")).intValue());
   }

   /**
    * Thread "other" enters {@code otherCall} first and is parked (on the engine lock) while
    * thread "script" holds the engine lock; then "script" makes {@code scriptCall}, standing in
    * for a Java callback made from inside a running script. Before the fix "other" held the env
    * monitor while parked, so "script" blocked on the monitor: deadlock.
    */
   private static void runAgainstEngineLockHolder(GraalJavaScriptEnv env, Callable<?> scriptCall,
                                                  Callable<?> otherCall)
      throws Exception
   {
      Lock engineLock = env.getExecutionLock();
      assertNotNull(engineLock, "engine should exist after init()");

      CountDownLatch scriptHoldsEngineLock = new CountDownLatch(1);
      AtomicReference<Thread> otherThread = new AtomicReference<>();
      ExecutorService pool = newDaemonPool();

      try {
         Future<?> script = pool.submit(() -> {
            engineLock.lock();

            try {
               scriptHoldsEngineLock.countDown();
               assertTrue(awaitQueued(engineLock, otherThread),
                          "other thread never parked waiting for the engine lock");
               return scriptCall.call();
            }
            finally {
               engineLock.unlock();
            }
         });

         Future<?> other = pool.submit(() -> {
            assertTrue(scriptHoldsEngineLock.await(10, TimeUnit.SECONDS));
            otherThread.set(Thread.currentThread());
            return otherCall.call();
         });

         script.get(15, TimeUnit.SECONDS);
         other.get(15, TimeUnit.SECONDS);
      }
      finally {
         pool.shutdownNow();
      }
   }

   /** Wait until the thread in {@code ref} is queued on {@code lock}. */
   private static boolean awaitQueued(Lock lock, AtomicReference<Thread> ref)
      throws InterruptedException
   {
      long deadline = System.currentTimeMillis() + 10_000;

      while(System.currentTimeMillis() < deadline) {
         Thread t = ref.get();

         if(t != null && isParkedIn(lock, t)) {
            return true;
         }

         Thread.sleep(10);
      }

      return false;
   }

   private static ExecutorService newDaemonPool() {
      return Executors.newFixedThreadPool(2, r -> {
         Thread t = new Thread(r);
         t.setDaemon(true);
         return t;
      });
   }

   /**
    * Check if {@code t} is parked inside {@code lock}'s lock method. Works for any lock
    * implementation (the engine lock is a ReentrantLock, or a lock without queue introspection
    * such as LendableReentrantLock, #76938): a WAITING thread with a {@code lock*} frame of the
    * lock's own class (or one of its nested classes, e.g. ReentrantLock$Sync) on its stack.
    */
   private static boolean isParkedIn(Lock lock, Thread t) {
      Thread.State state = t.getState();

      if(state != Thread.State.WAITING && state != Thread.State.TIMED_WAITING) {
         return false;
      }

      String cls = lock.getClass().getName();

      for(StackTraceElement frame : t.getStackTrace()) {
         String name = frame.getClassName();

         if((name.equals(cls) || name.startsWith(cls + "$")) &&
            frame.getMethodName().startsWith("lock"))
         {
            return true;
         }
      }

      return false;
   }

   private static final String SPLIT_SCRIPT = "var a = 1;\nif(a > 0) { a + 1 }";
}
