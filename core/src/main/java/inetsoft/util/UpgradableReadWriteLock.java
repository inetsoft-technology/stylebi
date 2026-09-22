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
package inetsoft.util;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class UpgradableReadWriteLock {
   public UpgradableReadWriteLock() {
      this(RESTORE_WRITE_LOCK_TIMEOUT_MS_DEFAULT);
   }

   /**
    * @param restoreWriteLockTimeoutMs bound for {@link #lockWriteBounded()}, used only by
    *                                   {@link #restoreLocks()}. Package-private so tests can
    *                                   exercise the timeout path without a real 20s wait; all
    *                                   production callers use the public no-arg constructor.
    */
   UpgradableReadWriteLock(long restoreWriteLockTimeoutMs) {
      this.restoreWriteLockTimeoutMs = restoreWriteLockTimeoutMs;
   }

   /**
    * Acquire a write (exclusive) lock.
    */
   public void lockWrite() {
      OptionalInt maxLevel = getStack().stream().mapToInt(Integer::intValue).max();

      // rewind all read lock and try to lock write lock
      if(maxLevel.isPresent() && maxLevel.getAsInt() < 1) {
         int cnt = getStack().size();

         for(int i = 0; i < cnt; i++) {
            thisLock.readLock().unlock();
         }
      }

      getStack().push(1);
      thisLock.writeLock().lock();
   }

   /**
    * Release a write (exclusive) lock.
    */
   public void unlockWrite() {
      pop();
      thisLock.writeLock().unlock();

      OptionalInt maxLevel = getStack().stream().mapToInt(Integer::intValue).max();

      if(maxLevel.isPresent() && maxLevel.getAsInt() < 1) {
         int cnt = getStack().size();

         for(int i = 0; i < cnt; i++) {
            thisLock.readLock().lock();
         }
      }
   }

   /**
    * Acquire a read (shared) lock.
    */
   public void lockRead() {
      thisLock.readLock().lock();
      getStack().push(0);
   }

   /**
    * Release a read (shared) lock.
    */
   public void unlockRead() {
      pop();
      thisLock.readLock().unlock();
   }

   /**
    * Unlock all currently locked locks, and store the current lock states to be restored
    * in {@link #restoreLocks()}.
    *
    * <p>Safe for re-entrant use: the saved states are kept on a per-thread stack, so a
    * nested unlockAll()/restoreLocks() pair does not discard the state saved by an
    * enclosing unlockAll(). Each call must be paired with a restoreLocks() on the same
    * thread.</p>
    */
   public void unlockAll() {
      List<Integer> olocks = new ArrayList<>();
      Stack<Integer> stack = (Stack<Integer>) getStack().clone();
      thisOldLocks.get().push(olocks);

      while(!stack.empty()) {
         Integer op = stack.pop();
         olocks.add(op);

         switch(op) {
         case 0:
            unlockRead();
            break;
         case 1:
            unlockWrite();
            break;
         }
      }
   }

   /**
    * Restore the locks unlocked in the matching unlockAll.
    *
    * <p>Write-lock restoration below is bounded, not the unconditional {@link #lockWrite()}.
    * This method is the re-entry point after a caller deliberately dropped the sandbox lock
    * mid-operation (unlockAll()) to run something slow or re-entrant outside it -- e.g. a
    * script callback that ends up back in the GraalVM engine's own per-viewsheet execution
    * lock. If that "something" itself needs the write lock back (directly, or via another
    * thread's restoreLocks()) before it can finish and release what this thread is waiting
    * on, an unconditional wait here forms an unbreakable cycle: this thread never returns to
    * release whatever unrelated lock it grabbed before calling unlockAll(), so the other side
    * can never make progress either. See bug #76907 (grouping crosstab columns wedges the
    * whole viewsheet, and every other viewsheet sharing this JVM's GraalVM script engine
    * lock, when a crosstab's highlight script and a Calc Table's cell formula each hold one
    * of these two locks and block on the other) and the class of hazard this class's callers
    * already document (bug #76549, bug #74001). A bounded wait turns that unbreakable cycle
    * into a failed operation instead: the caller's existing exception handling (e.g.
    * TableDataVSAScriptable.initTableArray()'s catch, which already logs and treats the
    * table as unavailable) absorbs it, and the lock/resource this thread holds gets released
    * as the exception unwinds, letting the other side proceed.</p>
    */
   public void restoreLocks() {
      Deque<List<Integer>> saved = thisOldLocks.get();

      if(saved.isEmpty()) {
         thisOldLocks.remove();
         return;
      }

      List<Integer> olocks = saved.pop();

      try {
         for(int i = olocks.size() - 1; i >= 0; i--) {
            switch(olocks.get(i)) {
            case 0:
               lockRead();
               break;
            case 1:
               lockWriteBounded();
               break;
            }
         }
      }
      finally {
         if(saved.isEmpty()) {
            thisOldLocks.remove();
         }
      }
   }

   /**
    * Acquire a write (exclusive) lock, bounded by {@code restoreWriteLockTimeoutMs}.
    *
    * <p>Only used by {@link #restoreLocks()} -- see its doc comment for why. Every other
    * write-lock acquisition in this class keeps the original unconditional
    * {@link #lockWrite()} behavior; ordinary contention there is expected to clear on its
    * own and is not a sign of a lock-order inversion.</p>
    *
    * <p>On failure (timeout or interrupt), any read locks rewound below are re-locked
    * before the exception is thrown, so this thread's lock-state stack ({@link #getStack()})
    * and the real {@code ReentrantReadWriteLock} state never diverge -- see review round 1 on
    * bug #76907: without this, a nested read-then-write restore (stack {@code [0, 1]}, e.g.
    * from a reentrant {@link #lockWrite()} called while a read lock from an enclosing
    * {@link #lockRead()} was already held) left {@code getStack()} still claiming a read lock
    * this thread no longer physically held after a timeout here, so a later, unrelated
    * {@link #unlockRead()} or {@link #lockWrite()} call on the same pooled thread threw
    * {@code IllegalMonitorStateException} against the real lock.</p>
    *
    * @throws IllegalStateException if the write lock could not be acquired within the bound,
    *                                or if the wait was interrupted.
    */
   private void lockWriteBounded() {
      OptionalInt maxLevel = getStack().stream().mapToInt(Integer::intValue).max();
      boolean rewoundReads = maxLevel.isPresent() && maxLevel.getAsInt() < 1;
      int rewoundCount = rewoundReads ? getStack().size() : 0;

      // rewind all read lock and try to lock write lock
      if(rewoundReads) {
         for(int i = 0; i < rewoundCount; i++) {
            thisLock.readLock().unlock();
         }
      }

      boolean acquired;
      InterruptedException interrupted = null;

      try {
         acquired = thisLock.writeLock()
            .tryLock(restoreWriteLockTimeoutMs, TimeUnit.MILLISECONDS);
      }
      catch(InterruptedException e) {
         Thread.currentThread().interrupt();
         acquired = false;
         interrupted = e;
      }

      if(!acquired) {
         // getStack() still reports the reads rewound above (they were never popped, only
         // physically unlocked), so restore the physical state to match before throwing --
         // mirrors unlockWrite()'s own re-lock-on-downgrade logic.
         if(rewoundReads) {
            for(int i = 0; i < rewoundCount; i++) {
               thisLock.readLock().lock();
            }
         }

         if(interrupted != null) {
            throw new IllegalStateException(
               "Interrupted while restoring the viewsheet sandbox write lock", interrupted);
         }

         throw new IllegalStateException(
            "Timed out restoring the viewsheet sandbox write lock after " +
            restoreWriteLockTimeoutMs + "ms; another thread is likely holding it while " +
            "waiting on a lock this thread holds (see bug #76907)");
      }

      // only recorded once the underlying lock is actually held, so a failed/timed-out
      // attempt above does not leave this thread's lock-state stack out of sync with the
      // real ReentrantReadWriteLock state
      getStack().push(1);
   }

   private int pop() {
      Stack<Integer> stack = getStack();
      int state = getStack().pop();

      if(stack.size() == 0) {
         thisLockState.get().remove(this);
      }

      return state;
   }

   private Stack<Integer> getStack() {
      Stack<Integer> stack = thisLockState.get().get(this);

      if(stack == null) {
         thisLockState.get().put(this, stack = new Stack<>());
      }

      return stack;
   }

   private final ReadWriteLock thisLock = new ReentrantReadWriteLock(false);
   private final ThreadLocal<Deque<List<Integer>>> thisOldLocks =
      ThreadLocal.withInitial(ArrayDeque::new);
   private static final ThreadLocal<Map<Object,Stack<Integer>>> thisLockState =
      ThreadLocal.withInitial(HashMap::new);
   private static final long RESTORE_WRITE_LOCK_TIMEOUT_MS_DEFAULT = 20000;
   private final long restoreWriteLockTimeoutMs;
}
