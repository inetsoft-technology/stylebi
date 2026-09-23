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
package inetsoft.report.composition.execution;

import inetsoft.test.*;
import inetsoft.uql.asset.AbstractSheet;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.script.JavaScriptEngine;
import inetsoft.util.script.graal.GraalJavaScriptEnv;
import inetsoft.util.script.graal.ScriptScope;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for bug #76905 (reopened): a thread that holds the GraalJS engine lock (i.e.
 * is inside {@code GraalJavaScriptEngine.exec}) must never block acquiring the
 * {@link ViewsheetSandbox} lock, because non-script threads legitimately hold the sandbox lock
 * while they wait for the engine lock ({@code CalcTableVSAQuery} holds the write lock across
 * {@code clens.process()}; {@code getVSTableLens0} holds the read lock across
 * {@code executeScript}).
 *
 * <p>Each test drives the real sandbox lock API and a real {@link GraalJavaScriptEnv}'s
 * execution lock through one of the interleavings captured live on the running server:
 * <ul>
 *    <li>the read restore in {@code doExecuteData} ({@code lockRead -> unlockAll ->
 *        restoreLocks}) against a writer that wants the engine lock -- what an env-only fix
 *        turns the permanent deadlock into;</li>
 *    <li>the same restore behind a <em>queued</em> writer, with readers that hold the read lock
 *        and want the engine lock (the 20 second stall + EmptyStackException run);</li>
 *    <li>the fresh {@code lockRead()} in {@code getVSTableLens0} against a writer that wants the
 *        engine lock.</li>
 * </ul>
 * The script thread is marked with {@link JavaScriptEngine#pushExecScriptable}, exactly as
 * {@code GraalJavaScriptEngine.exec} does after taking its lock. All threads are daemons and all
 * waits are bounded, so a regression fails instead of hanging the build.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ViewsheetSandboxScriptLockOrderingTest {
   @BeforeEach
   void setUp() throws Exception {
      box = new ViewsheetSandbox(new Viewsheet(), AbstractSheet.SHEET_RUNTIME_MODE, null, false,
                                 null);
      env = new GraalJavaScriptEnv();
      env.init();
      engineLock = env.getExecutionLock();
      pool = Executors.newFixedThreadPool(3, r -> {
         Thread t = new Thread(r);
         t.setDaemon(true);
         return t;
      });
   }

   @AfterEach
   void tearDown() {
      pool.shutdownNow();
   }

   /**
    * doExecuteData's read restore on a script thread vs. a thread that took the write lock in
    * the release window and then waits for the engine lock (CalcTableLens.evaluate ->
    * senv.put).
    */
   @Test
   void scriptThreadReadRestoreDoesNotDeadlockWithWriterWaitingForEngineLock() throws Exception {
      CountDownLatch released = new CountDownLatch(1);
      AtomicReference<Thread> writer = new AtomicReference<>();

      Future<?> script = submitScript(() -> {
         box.lockRead();

         try {
            box.unlockAll();

            try {
               released.countDown();
               assertTrue(awaitQueued(writer), "writer never queued on the engine lock");
            }
            finally {
               box.restoreLocks();
            }
         }
         finally {
            box.unlockRead();
         }
      });

      Future<?> other = pool.submit(() -> {
         assertTrue(released.await(10, TimeUnit.SECONDS));
         box.lockWrite();

         try {
            writer.set(Thread.currentThread());
            env.put("fromWriter", 1);
         }
         finally {
            box.unlockWrite();
         }

         return null;
      });

      script.get(TIMEOUT, TimeUnit.SECONDS);
      other.get(TIMEOUT, TimeUnit.SECONDS);
   }

   /**
    * The same restore, but the writer is only queued: readers hold the read lock and wait for
    * the engine lock, and the non-fair lock makes a new reader wait behind the queued writer.
    */
   @Test
   void scriptThreadReadRestoreDoesNotDeadlockBehindQueuedWriter() throws Exception {
      CountDownLatch released = new CountDownLatch(1);
      CountDownLatch readerHolds = new CountDownLatch(1);
      AtomicReference<Thread> reader = new AtomicReference<>();
      AtomicReference<Thread> writer = new AtomicReference<>();
      AtomicReference<Future<?>> writerTask = new AtomicReference<>();

      Future<?> script = submitScript(() -> {
         box.lockRead();

         try {
            box.unlockAll();

            try {
               released.countDown();
               assertTrue(awaitQueued(reader), "reader never queued on the engine lock");
               assertTrue(awaitWaiting(writer, writerTask),
                          "writer never queued on the sandbox lock");
            }
            finally {
               box.restoreLocks();
            }
         }
         finally {
            box.unlockRead();
         }
      });

      Future<?> other = pool.submit(() -> {
         assertTrue(released.await(10, TimeUnit.SECONDS));
         box.lockRead();

         try {
            readerHolds.countDown();
            reader.set(Thread.currentThread());
            env.put("fromReader", 1);
         }
         finally {
            box.unlockRead();
         }

         return null;
      });

      Future<?> queued = pool.submit(() -> {
         assertTrue(readerHolds.await(10, TimeUnit.SECONDS));
         assertTrue(awaitQueued(reader), "reader never queued on the engine lock");
         writer.set(Thread.currentThread());
         box.lockWrite();
         box.unlockWrite();
         return null;
      });
      writerTask.set(queued);

      script.get(TIMEOUT, TimeUnit.SECONDS);
      other.get(TIMEOUT, TimeUnit.SECONDS);
      queued.get(TIMEOUT, TimeUnit.SECONDS);
   }

   /**
    * getVSTableLens0's fresh lockRead() reached from inside a script while another thread holds
    * the write lock and waits for the engine lock.
    */
   @Test
   void scriptThreadFreshReadDoesNotBlockOnWriterWaitingForEngineLock() throws Exception {
      CountDownLatch scriptStarted = new CountDownLatch(1);
      AtomicReference<Thread> writer = new AtomicReference<>();

      Future<?> script = submitScript(() -> {
         scriptStarted.countDown();
         assertTrue(awaitQueued(writer), "writer never queued on the engine lock");
         box.lockRead();
         box.unlockRead();
      });

      Future<?> other = pool.submit(() -> {
         assertTrue(scriptStarted.await(10, TimeUnit.SECONDS));
         box.lockWrite();

         try {
            writer.set(Thread.currentThread());
            env.put("fromWriter", 1);
         }
         finally {
            box.unlockWrite();
         }

         return null;
      });

      script.get(TIMEOUT, TimeUnit.SECONDS);
      other.get(TIMEOUT, TimeUnit.SECONDS);
   }

   /**
    * A lock the script's caller took before the script started (legal sandbox-then-engine
    * order) stays held when a nested fetch inside the script calls unlockAll(): once released
    * under the engine lock it could not be taken back without blocking.
    */
   @Test
   void lockTakenBeforeScriptIsNotReleasedByUnlockAllInsideScript() throws Exception {
      box.lockWrite();

      try {
         JavaScriptEngine.pushExecScriptable(NOOP_SCOPE);

         try {
            box.unlockAll();

            try {
               Future<?> reader = pool.submit(() -> {
                  box.lockRead();
                  box.unlockRead();
                  return null;
               });

               assertThrows(TimeoutException.class, () -> reader.get(500, TimeUnit.MILLISECONDS),
                            "the caller's write lock was released inside the script");
            }
            finally {
               box.restoreLocks();
            }
         }
         finally {
            JavaScriptEngine.popExecScriptable();
         }
      }
      finally {
         box.unlockWrite();
      }
   }

   /** Runs {@code body} on a pool thread that is "inside a script": engine lock held + flag. */
   private Future<?> submitScript(ThrowingRunnable body) {
      return pool.submit(() -> {
         engineLock.lock();
         JavaScriptEngine.pushExecScriptable(NOOP_SCOPE);

         try {
            body.run();
         }
         finally {
            JavaScriptEngine.popExecScriptable();
            engineLock.unlock();
         }

         return null;
      });
   }

   /** Wait until the thread in {@code ref} is queued on the engine lock. */
   private boolean awaitQueued(AtomicReference<Thread> ref) throws InterruptedException {
      long deadline = System.currentTimeMillis() + 10_000;

      while(System.currentTimeMillis() < deadline) {
         Thread t = ref.get();

         if(t != null && isParkedIn(engineLock, t)) {
            return true;
         }

         Thread.sleep(10);
      }

      return false;
   }

   /**
    * Wait until the thread in {@code ref} is parked while still running {@code task} (the
    * sandbox lock's queue is not accessible, so this is the best available check).
    */
   private static boolean awaitWaiting(AtomicReference<Thread> ref, AtomicReference<Future<?>> task)
      throws InterruptedException
   {
      long deadline = System.currentTimeMillis() + 10_000;

      while(System.currentTimeMillis() < deadline) {
         Thread t = ref.get();
         Future<?> f = task.get();

         if(t != null && f != null && !f.isDone() && t.getState() == Thread.State.WAITING) {
            return true;
         }

         Thread.sleep(10);
      }

      return false;
   }

   @FunctionalInterface
   private interface ThrowingRunnable {
      void run() throws Exception;
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

   private static final long TIMEOUT = 15;

   private static final ScriptScope NOOP_SCOPE = new ScriptScope() {
      @Override
      public Object getMember(String name) {
         return null;
      }

      @Override
      public boolean hasMember(String name) {
         return false;
      }

      @Override
      public void putMember(String name, Object value) {
      }

      @Override
      public Object[] getMemberKeys() {
         return new Object[0];
      }
   };

   private ViewsheetSandbox box;
   private GraalJavaScriptEnv env;
   private Lock engineLock;
   private ExecutorService pool;
}
