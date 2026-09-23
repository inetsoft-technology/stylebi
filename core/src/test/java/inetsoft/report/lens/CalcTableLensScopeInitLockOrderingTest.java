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
package inetsoft.report.lens;

import inetsoft.report.FormulaTable;
import inetsoft.util.script.graal.GraalJavaScriptEnv;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression test for bug #76905 (reopened), rule I3: a monitor that a thread holds while it
 * acquires the script engine's execution lock must never be needed by a thread that already
 * holds that lock.
 *
 * <p>{@code CalcTableLens.evaluate()} initializes its {@code tableScope} on first use under
 * {@code synchronized(this)}, and used to register the scope with the script env
 * ({@code senv.put}) and look up the parent scope ({@code senv.get}) inside that block. Both
 * take the engine's execution lock. A thread already inside a script (holding the execution
 * lock) that evaluated a formula on the same lens then waited for the lens monitor, while the
 * monitor's holder waited for the execution lock: deadlock.
 *
 * <p>Uses a real {@link GraalJavaScriptEnv}, its real engine execution lock and a real
 * {@link CalcTableLens}; only the containing {@link FormulaTable} is mocked. All threads are
 * daemons and every wait is bounded, so a regression fails instead of hanging the build.
 */
@Tag("core")
class CalcTableLensScopeInitLockOrderingTest {
   @Test
   void scopeInitDoesNotHoldLensMonitorWhileWaitingForEngineLock() throws Exception {
      GraalJavaScriptEnv env = new GraalJavaScriptEnv();
      env.init();

      Lock engineLock = env.getExecutionLock();
      assertNotNull(engineLock, "engine should exist after init()");

      FormulaTable elem = mock(FormulaTable.class);
      when(elem.getScriptEnv()).thenReturn(env);
      when(elem.getID()).thenReturn("CalcTable1");
      when(elem.getScriptTable()).thenReturn(new DefaultTableLens(new Object[][] {
         { "col1" }, { 1 }
      }));

      CalcTableLens lens = new CalcTableLens(1, 1);
      lens.setElement(elem);

      CountDownLatch scriptHoldsEngineLock = new CountDownLatch(1);
      AtomicReference<Thread> initThread = new AtomicReference<>();
      ExecutorService pool = newDaemonPool();

      try {
         // stands in for a thread inside GraalJavaScriptEngine.exec() whose script evaluates a
         // formula on a lens that another thread is initializing
         Future<Object> script = pool.submit(() -> {
            engineLock.lock();

            try {
               scriptHoldsEngineLock.countDown();
               assertTrue(awaitParkedOnEngineLock(initThread),
                          "init thread never parked waiting for the engine lock");
               return lens.evaluate(0, 0, new CalcTableLens.Formula("20 + 22"));
            }
            finally {
               engineLock.unlock();
            }
         });

         // first-time tableScope init on the same lens, reaching an senv call that needs the
         // engine lock while "script" holds it
         Future<Object> init = pool.submit(() -> {
            assertTrue(scriptHoldsEngineLock.await(10, TimeUnit.SECONDS));
            initThread.set(Thread.currentThread());
            return lens.evaluate(0, 0, new CalcTableLens.Formula("1 + 2"));
         });

         assertEquals(42, ((Number) script.get(15, TimeUnit.SECONDS)).intValue());
         assertEquals(3, ((Number) init.get(15, TimeUnit.SECONDS)).intValue());
      }
      finally {
         pool.shutdownNow();
      }
   }

   /**
    * Wait until the thread in {@code ref} is parked acquiring the engine's execution lock. The
    * probe is a thread-state plus stack-frame check rather than {@code hasQueuedThread}, so it
    * does not depend on the lock's concrete class (a plain {@code ReentrantLock} or any other
    * AQS-based {@link Lock}).
    */
   private static boolean awaitParkedOnEngineLock(AtomicReference<Thread> ref)
      throws InterruptedException
   {
      long deadline = System.currentTimeMillis() + 10_000;

      while(System.currentTimeMillis() < deadline) {
         Thread t = ref.get();

         if(t != null && isParkedOnEngineLock(t)) {
            return true;
         }

         Thread.sleep(10);
      }

      return false;
   }

   private static boolean isParkedOnEngineLock(Thread t) {
      Thread.State state = t.getState();

      if(state != Thread.State.WAITING && state != Thread.State.TIMED_WAITING) {
         return false;
      }

      boolean inLock = false;

      // innermost frame first: a lock frame, then (further out) the script env or engine that is
      // acquiring it
      for(StackTraceElement frame : t.getStackTrace()) {
         String cls = frame.getClassName();

         if(cls.startsWith("java.util.concurrent.locks.") || cls.endsWith("ReentrantLock")) {
            inLock = true;
         }
         else if(inLock && (cls.startsWith(ENGINE) || cls.startsWith(ENV))) {
            return true;
         }
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

   private static final String ENGINE = "inetsoft.util.script.graal.GraalJavaScriptEngine";
   private static final String ENV = "inetsoft.util.script.graal.GraalJavaScriptEnv";
}
