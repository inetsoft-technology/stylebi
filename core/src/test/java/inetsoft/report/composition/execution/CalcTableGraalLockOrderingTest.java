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

import inetsoft.util.UpgradableReadWriteLock;
import inetsoft.util.script.JavaScriptEngine;
import inetsoft.util.script.graal.ScriptScope;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression test for bug #76905: a live jstack-confirmed deadlock between
 * {@code ViewsheetSandbox}'s {@link UpgradableReadWriteLock} and the GraalJS engine's
 * per-Context {@code ReentrantLock} (see
 * {@code docs/teams/2026-09-22-bugs-freehand-resize/bug-76905/05-deadlock-confirmed.md}).
 *
 * <p>{@code CalcTableVSAQuery.getTableLens()} used to take the sandbox write lock
 * unconditionally, even when already running nested inside a script (which already holds the
 * GraalJS engine lock, e.g. resolving {@code table["TableView1"]} from another formula).
 * {@code VSAQuery.getDataWithoutSandboxLock()}, called from inside that write-locked region,
 * transiently drops the write lock around a (possibly slow) data fetch and restores it with a
 * blocking {@code lockWrite()} -- opening a window in which a second, non-script thread (e.g.
 * {@code FormatPainterService.getFormat()}) can grab the freed write lock and then block
 * entering the same GraalJS engine lock the first thread already holds. That is a classic ABBA
 * cycle: the script thread holds the engine lock and wants the write lock back; the other thread
 * holds the write lock and wants the engine lock.
 *
 * <p>The fix gates {@code CalcTableVSAQuery.getTableLens()}'s write-lock acquisition on
 * {@code JavaScriptEngine.isScriptThread()}, mirroring the identical rationale already used by
 * {@code ViewsheetSandbox.doExecuteData()}/{@code getData()} ("if called from script, the
 * locking should already be in place") and by {@code ConcatenatedQuery}/{@code JoinQuery}'s
 * {@code isScriptThread()} guard for the very same GraalJS engine lock.
 *
 * <p>This test reproduces the lock-acquisition shape directly against the real
 * {@link UpgradableReadWriteLock} and the real {@link JavaScriptEngine} script-thread marker
 * (standing in for the sandbox lock and the "already in script" flag respectively), with a
 * plain {@link ReentrantLock} standing in for the GraalJS engine's per-Context lock -- rather
 * than driving a full {@code ViewsheetSandbox}/GraalJS harness through the exact live
 * interleaving, which would require scaffolding disproportionate to proving the lock-ordering
 * invariant (the same trade-off {@code CalcTableVSAQueryTempCrosstabNameTest} documents for bug
 * #76614). Both threads run as daemon threads with bounded waits, so a failed assertion here
 * cannot hang the build even if a future change reintroduces the deadlock.
 */
@Tag("core")
public class CalcTableGraalLockOrderingTest {
   /**
    * Reproduces the exact cycle from the confirmed jstack dump using the *pre-fix* shape (the
    * sandbox write lock is always taken, regardless of whether the calling thread is already
    * mid-script) and asserts it reliably deadlocks within a bounded time. This proves the
    * scenario below is a genuine reproduction of the bug, not a vacuous one.
    */
   @Test
   public void unconditionalWriteLockDeadlocksAgainstScriptEngineLock() throws Exception {
      runInterleaving(false, false);
   }

   /**
    * Same interleaving, but with the fix's {@code isScriptThread()} guard applied, matching
    * {@code CalcTableVSAQuery.getTableLens()}'s current behavior: the script thread never takes
    * the sandbox write lock at all, so there is no write lock for the other thread to steal, and
    * no cycle can form. Both threads must complete promptly.
    */
   @Test
   public void scriptThreadGuardPreventsDeadlock() throws Exception {
      runInterleaving(true, true);
   }

   /**
    * Drives two threads through the confirmed deadlock's exact lock-acquisition order:
    *
    * <ol>
    *    <li>"script" thread takes the GraalJS-engine-lock stand-in (mirrors
    *        {@code GraalJavaScriptEngine.exec()}'s {@code lock.lock()}), then -- if
    *        {@code guardEnabled} is false, or it is not a script thread -- takes the sandbox
    *        write lock, then drops it (mirrors {@code VSAQuery.getDataWithoutSandboxLock()}).
    *    <li>"format painter" thread waits for the drop, grabs the now-free write lock (mirrors
    *        a second, unrelated request racing in), then tries to take the engine-lock stand-in
    *        (mirrors {@code CalcTableLens.evaluate()} -> {@code GraalJavaScriptEnv.put()}).
    *    <li>"script" thread tries to restore the write lock it dropped.
    * </ol>
    *
    * <p>Without the guard, step 3 blocks on step 2's held write lock while step 2 blocks on
    * step 1's held engine lock: deadlock. With the guard, the script thread never takes the
    * write lock in the first place, so step 3 never happens and the cycle cannot form.
    */
   private void runInterleaving(boolean guardEnabled, boolean expectPromptCompletion)
      throws Exception
   {
      UpgradableReadWriteLock sandboxLock = new UpgradableReadWriteLock();
      ReentrantLock graalEngineLock = new ReentrantLock();
      CountDownLatch lockDropped = new CountDownLatch(1);
      CountDownLatch writeLockStolen = new CountDownLatch(1);

      ExecutorService pool = Executors.newFixedThreadPool(2, r -> {
         Thread t = new Thread(r);
         t.setDaemon(true);
         return t;
      });

      try {
         Future<?> scriptThread = pool.submit(() -> {
            JavaScriptEngine.pushExecScriptable(NOOP_SCOPE);

            try {
               // Mirrors GraalJavaScriptEngine.exec()'s lock.lock(): this thread is mid-script.
               graalEngineLock.lock();

               try {
                  boolean skipSandboxLock = guardEnabled && JavaScriptEngine.isScriptThread();

                  if(!skipSandboxLock) {
                     sandboxLock.lockWrite();
                  }

                  try {
                     if(!skipSandboxLock) {
                        // Mirrors VSAQuery.getDataWithoutSandboxLock(): drop, do the (here,
                        // simulated) slow work, then restore.
                        sandboxLock.unlockAll();

                        try {
                           lockDropped.countDown();
                           assertTrue(writeLockStolen.await(10, TimeUnit.SECONDS),
                              "format-painter thread never grabbed the freed write lock");
                        }
                        finally {
                           sandboxLock.restoreLocks();
                        }
                     }
                     else {
                        lockDropped.countDown();
                     }
                  }
                  finally {
                     if(!skipSandboxLock) {
                        sandboxLock.unlockWrite();
                     }
                  }
               }
               finally {
                  graalEngineLock.unlock();
               }
            }
            finally {
               JavaScriptEngine.popExecScriptable();
            }

            return null;
         });

         Future<?> formatPainterThread = pool.submit(() -> {
            assertTrue(lockDropped.await(10, TimeUnit.SECONDS),
               "script thread never reached the lock-drop window");

            sandboxLock.lockWrite();

            try {
               writeLockStolen.countDown();
               // Mirrors CalcTableLens.evaluate() -> GraalJavaScriptEnv.put() needing the same
               // engine lock while still holding the sandbox write lock.
               graalEngineLock.lock();
               graalEngineLock.unlock();
            }
            finally {
               sandboxLock.unlockWrite();
            }

            return null;
         });

         if(expectPromptCompletion) {
            scriptThread.get(5, TimeUnit.SECONDS);
            formatPainterThread.get(5, TimeUnit.SECONDS);
         }
         else {
            assertThrows(TimeoutException.class, () -> {
               scriptThread.get(3, TimeUnit.SECONDS);
               formatPainterThread.get(3, TimeUnit.SECONDS);
            }, "expected the unguarded interleaving to deadlock, but both threads completed");
         }
      }
      finally {
         pool.shutdownNow();
      }
   }

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
}
