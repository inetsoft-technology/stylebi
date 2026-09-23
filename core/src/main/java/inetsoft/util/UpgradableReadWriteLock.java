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
import java.util.function.BooleanSupplier;

/**
 * A reentrant read/write lock that remembers, per thread, which locks the thread holds so that
 * they can be temporarily released ({@link #unlockAll()}) and restored
 * ({@link #restoreLocks()}), and so that a read lock can be upgraded to a write lock.
 *
 * <p><b>Non-blocking mode.</b> A lock created with a {@code nonBlocking} predicate never blocks
 * a thread for which the predicate is true at the time it acquires or restores a lock. The
 * viewsheet sandbox passes {@code JavaScriptEngine::isScriptThread}: a thread inside a GraalJS
 * script holds that script engine's execution lock, and other threads legitimately hold this
 * lock while they wait for the engine lock (sandbox lock first, engine lock second), so a
 * script thread that blocked here would close a deadlock cycle (bug #76905). For such a thread
 * an acquisition is a non-timed {@code tryLock()} and, if that fails, is recorded as
 * <em>skipped</em>: the thread proceeds without the lock and the matching unlock does nothing.
 * The decision is recorded in the per-thread stack when the lock is taken, so the unlock never
 * re-evaluates the predicate.</p>
 */
public class UpgradableReadWriteLock {
   public UpgradableReadWriteLock() {
      this(RESTORE_WRITE_LOCK_TIMEOUT_MS_DEFAULT);
   }

   /**
    * @param nonBlocking returns true when the current thread must never block acquiring this
    *                    lock (see the class doc). Evaluated only when a lock is taken.
    */
   public UpgradableReadWriteLock(BooleanSupplier nonBlocking) {
      this(RESTORE_WRITE_LOCK_TIMEOUT_MS_DEFAULT, nonBlocking);
   }

   /**
    * @param restoreWriteLockTimeoutMs bound for {@link #lockWriteBounded()}, used only by
    *                                   {@link #restoreLocks()}. Package-private so tests can
    *                                   exercise the timeout path without a real 20s wait; all
    *                                   production callers use a public constructor.
    */
   UpgradableReadWriteLock(long restoreWriteLockTimeoutMs) {
      this(restoreWriteLockTimeoutMs, () -> false);
   }

   UpgradableReadWriteLock(long restoreWriteLockTimeoutMs, BooleanSupplier nonBlocking) {
      this.restoreWriteLockTimeoutMs = restoreWriteLockTimeoutMs;
      this.nonBlocking = nonBlocking;
   }

   /**
    * Acquire a write (exclusive) lock.
    */
   public void lockWrite() {
      if(nonBlocking.getAsBoolean()) {
         lockWriteNonBlocking();
         return;
      }

      Stack<Integer> stack = getStack();

      // rewind all read lock and try to lock write lock
      if(!hasWrite(stack)) {
         int cnt = countReads(stack);

         for(int i = 0; i < cnt; i++) {
            thisLock.readLock().unlock();
         }
      }

      stack.push(WRITE);
      thisLock.writeLock().lock();
   }

   /**
    * Release a write (exclusive) lock.
    */
   public void unlockWrite() {
      if(isSkipped(pop())) {
         return;
      }

      Stack<Integer> stack = getStack();

      // re-lock the read locks rewound by the upgrade in lockWrite(). They are taken while the
      // write lock is still held (a downgrade), which never blocks; taking them after the
      // unlock could wait behind another thread's queued writer.
      if(!hasWrite(stack)) {
         int cnt = countReads(stack);

         for(int i = 0; i < cnt; i++) {
            thisLock.readLock().lock();
         }
      }

      thisLock.writeLock().unlock();
   }

   /**
    * Acquire a read (shared) lock.
    */
   public void lockRead() {
      if(nonBlocking.getAsBoolean()) {
         lockReadNonBlocking();
         return;
      }

      thisLock.readLock().lock();
      getStack().push(READ);
   }

   /**
    * Release a read (shared) lock.
    */
   public void unlockRead() {
      if(isSkipped(pop())) {
         return;
      }

      thisLock.readLock().unlock();
   }

   /**
    * Get the number of acquisitions the current thread has skipped on this lock in
    * non-blocking mode so far. A caller compares the value before and after a computation to
    * find out whether any part of it ran without the lock it asked for.
    */
   public long getSkippedCount() {
      long[] count = skippedCount.get();
      return count == null ? 0 : count[0];
   }

   /**
    * Unlock all currently locked locks, and store the current lock states to be restored
    * in {@link #restoreLocks()}.
    *
    * <p>Safe for re-entrant use: the saved states are kept on a per-thread stack, so a
    * nested unlockAll()/restoreLocks() pair does not discard the state saved by an
    * enclosing unlockAll(). Each call must be paired with a restoreLocks() on the same
    * thread.</p>
    *
    * <p>In non-blocking mode only the locks the thread took in that mode are released. Locks
    * it took before (e.g. by the caller that started the script) stay held: released here they
    * could not be restored without blocking.</p>
    */
   public void unlockAll() {
      List<Integer> olocks = new ArrayList<>();
      Stack<Integer> stack = (Stack<Integer>) getStack().clone();
      boolean nonBlockingOnly = nonBlocking.getAsBoolean();
      thisOldLocks.get().push(olocks);

      while(!stack.empty()) {
         Integer op = stack.pop();

         if(nonBlockingOnly && !isNonBlocking(op)) {
            break;
         }

         olocks.add(op);

         if(isWrite(op)) {
            unlockWrite();
         }
         else {
            unlockRead();
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
    *
    * <p>When the bounded restore fails, the entry it could not restore and every entry above
    * it are recorded as skipped before the exception is thrown, so each enclosing frame's
    * {@code finally unlock*()} pops its own entry without touching the real lock, and the
    * IllegalStateException reaches the caller instead of an EmptyStackException from an
    * unlock that finds the stack short (bug #76905).</p>
    *
    * <p>Entries taken in non-blocking mode are restored in non-blocking mode.</p>
    */
   public void restoreLocks() {
      Deque<List<Integer>> saved = thisOldLocks.get();

      if(saved.isEmpty()) {
         thisOldLocks.remove();
         return;
      }

      List<Integer> olocks = saved.pop();
      int i = olocks.size() - 1;

      try {
         for(; i >= 0; i--) {
            switch(olocks.get(i)) {
            case READ:
               thisLock.readLock().lock();
               getStack().push(READ);
               break;
            case WRITE:
               lockWriteBounded();
               break;
            case NB_READ:
            case SKIPPED_READ:
               lockReadNonBlocking();
               break;
            default:
               lockWriteNonBlocking();
               break;
            }
         }
      }
      catch(RuntimeException ex) {
         Stack<Integer> stack = getStack();

         for(; i >= 0; i--) {
            stack.push(isWrite(olocks.get(i)) ? SKIPPED_WRITE : SKIPPED_READ);
         }

         throw ex;
      }
      finally {
         if(saved.isEmpty()) {
            thisOldLocks.remove();
         }
      }
   }

   /**
    * Acquire a read lock without blocking: barges ahead of queued writers and fails only while
    * another thread holds the write lock, in which case the lock is recorded as skipped.
    */
   private void lockReadNonBlocking() {
      if(thisLock.readLock().tryLock()) {
         getStack().push(NB_READ);
      }
      else {
         skip(SKIPPED_READ);
      }
   }

   /**
    * Acquire a write lock without blocking. A thread that already holds the write lock takes
    * it again (reentrant, never blocks). A thread that holds only read locks skips it: an
    * upgrade has to release the reads first, and they could not be taken back without
    * blocking if the write lock is not available.
    */
   private void lockWriteNonBlocking() {
      Stack<Integer> stack = getStack();

      if(hasWrite(stack)) {
         thisLock.writeLock().lock();
         stack.push(NB_WRITE);
      }
      else if(countReads(stack) == 0 && thisLock.writeLock().tryLock()) {
         stack.push(NB_WRITE);
      }
      else {
         skip(SKIPPED_WRITE);
      }
   }

   private void skip(int op) {
      getStack().push(op);
      long[] count = skippedCount.get();

      if(count == null) {
         skippedCount.set(count = new long[1]);
      }

      count[0]++;
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
      Stack<Integer> stack = getStack();
      boolean rewoundReads = !hasWrite(stack);
      int rewoundCount = rewoundReads ? countReads(stack) : 0;

      // rewind all read lock and try to lock write lock
      for(int i = 0; i < rewoundCount; i++) {
         thisLock.readLock().unlock();
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
         for(int i = 0; i < rewoundCount; i++) {
            thisLock.readLock().lock();
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
      stack.push(WRITE);
   }

   private int pop() {
      Stack<Integer> stack = getStack();
      int state = stack.pop();

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

   /**
    * Check if the stack holds a write lock that is really held (not skipped).
    */
   private static boolean hasWrite(Stack<Integer> stack) {
      return stack.contains(WRITE) || stack.contains(NB_WRITE);
   }

   /**
    * Count the read locks in the stack that are really held (not skipped).
    */
   private static int countReads(Stack<Integer> stack) {
      int cnt = 0;

      for(Integer op : stack) {
         if(op == READ || op == NB_READ) {
            cnt++;
         }
      }

      return cnt;
   }

   private static boolean isWrite(int op) {
      return op == WRITE || op == NB_WRITE || op == SKIPPED_WRITE;
   }

   private static boolean isSkipped(int op) {
      return op == SKIPPED_READ || op == SKIPPED_WRITE;
   }

   private static boolean isNonBlocking(int op) {
      return op != READ && op != WRITE;
   }

   // lock-state stack entries. READ/WRITE are blocking acquisitions; NB_* were acquired by
   // tryLock() in non-blocking mode; SKIPPED_* were not acquired (non-blocking mode, or a
   // failed bounded restore) and are released without touching the real lock.
   private static final int READ = 0;
   private static final int WRITE = 1;
   private static final int NB_READ = 2;
   private static final int NB_WRITE = 3;
   private static final int SKIPPED_READ = 4;
   private static final int SKIPPED_WRITE = 5;

   private final ReadWriteLock thisLock = new ReentrantReadWriteLock(false);
   private final ThreadLocal<Deque<List<Integer>>> thisOldLocks =
      ThreadLocal.withInitial(ArrayDeque::new);
   private static final ThreadLocal<Map<Object,Stack<Integer>>> thisLockState =
      ThreadLocal.withInitial(HashMap::new);
   private static final long RESTORE_WRITE_LOCK_TIMEOUT_MS_DEFAULT = 20000;
   private final long restoreWriteLockTimeoutMs;
   private final BooleanSupplier nonBlocking;
   private final ThreadLocal<long[]> skippedCount = new ThreadLocal<>();
}
