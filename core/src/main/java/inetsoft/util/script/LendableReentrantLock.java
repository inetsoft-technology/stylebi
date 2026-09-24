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
package inetsoft.util.script;

import inetsoft.util.stall.LockStallException;
import inetsoft.util.stall.WaitRecord;
import inetsoft.util.stall.WaitRegistry;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

/**
 * A reentrant mutual-exclusion lock whose owner can temporarily lend it to one
 * specific worker task while the owner is parked waiting for that task. This is the
 * script engine execution lock (see {@link inetsoft.util.script.graal.GraalJavaScriptEngine}).
 *
 * <p>Condition filtering takes the engine lock before its own monitor and holds it
 * across the whole lazy base pipeline (bug #76918). If that pipeline contains a lens
 * that is populated by a background worker (e.g. {@code SummaryFilter}), the lock
 * holder waits for the worker while the worker may itself need the engine lock (to
 * read an inner condition filter or evaluate a formula), which deadlocks (bug #76938).
 * Releasing the lock while waiting would reopen #76918, since the waiter still holds
 * monitors above the lens. Instead the waiter lends the lock to exactly the worker it
 * waits for: no other thread can acquire the lock while it is lent, so the acquisition
 * order #76918 relies on is unchanged.
 *
 * <p>Waiting threads re-check whether they may acquire on every wake-up (there is no
 * FIFO queue), so a borrower is let in even when other threads started waiting before
 * the lock was lent. Loans nest: a borrower that owns the lock may lend it on to its own
 * worker, and the loans are reclaimed in LIFO order.
 *
 * <p>A thread that has to wait in {@link #lock()} registers the wait with the lock-stall
 * watchdog and waits in slices (bug #76967). If neither the lock (owner, loans) nor the
 * thread it waits for makes progress for {@code stall.watchdog.noProgressMillis}, it throws
 * a {@link inetsoft.util.stall.LockStallException} holding nothing of this lock. Who may
 * acquire is decided by the same check as before on every wake-up. A lender closing its loan
 * registers its wait for the borrower too, as progress only: it is reported while the borrower
 * is stuck but never failed (see {@link #reclaim}).
 */
public final class LendableReentrantLock implements Lock {
   @Override
   public void lock() {
      synchronized(monitor) {
         Thread thread = Thread.currentThread();
         checkNotLending(thread);

         if(canAcquire(thread)) {
            acquire(thread);
            return;
         }
      }

      lockBounded();
   }

   /**
    * Wait for the lock in slices, checking for a stall between them outside of the monitor,
    * holding nothing of this lock (bug #76967). Admission is decided by canAcquire() on
    * every wake-up exactly as before, so a borrower still bypasses earlier waiters (#5531).
    */
   private void lockBounded() {
      boolean interrupted = false;

      try(WaitRecord record = WaitRegistry.begin("LendableReentrantLock.lock",
                                                 this::getGeneration, this::getBlockers))
      {
         Thread thread = Thread.currentThread();

         while(true) {
            synchronized(monitor) {
               if(canAcquire(thread)) {
                  acquire(thread);
                  return;
               }

               try {
                  monitor.wait(record.waitMillis(10000));
               }
               catch(InterruptedException ex) {
                  interrupted = true;
               }

               if(canAcquire(thread)) {
                  acquire(thread);
                  return;
               }
            }

            record.checkStall();
         }
      }
      finally {
         if(interrupted) {
            Thread.currentThread().interrupt();
         }
      }
   }

   /**
    * Progress of the lock for the watchdog: changes on every acquire, release, loan and
    * reclaim.
    */
   private long getGeneration() {
      synchronized(monitor) {
         return generation;
      }
   }

   /**
    * The threads a waiter waits for: the owner, or while the lock is lent and free, the
    * lender (which waits for its worker) and the innermost loan's borrower, the only thread
    * canAcquire() would let in. A borrower working outside the lock is progress although the
    * lender is parked in an unregistered wait.
    */
   private Thread[] getBlockers() {
      synchronized(monitor) {
         if(owner != null) {
            return new Thread[] { owner };
         }

         LoanImpl loan = loans.peek();

         if(loan == null) {
            return NO_THREADS;
         }

         Thread borrower = loan.borrower.getThread();
         return borrower != null ? new Thread[] { loan.lender, borrower } :
            new Thread[] { loan.lender };
      }
   }

   /**
    * Unlike {@link #lock()}, this wait is not bounded by the lock-stall watchdog nor registered
    * with it (bug #76967): no production code calls it, the engine lock is only taken with
    * lock() and tryLock().
    */
   @Override
   public void lockInterruptibly() throws InterruptedException {
      if(Thread.interrupted()) {
         throw new InterruptedException();
      }

      synchronized(monitor) {
         Thread thread = Thread.currentThread();
         checkNotLending(thread);

         while(!canAcquire(thread)) {
            monitor.wait();
         }

         acquire(thread);
      }
   }

   @Override
   public boolean tryLock() {
      synchronized(monitor) {
         Thread thread = Thread.currentThread();

         if(!canAcquire(thread)) {
            return false;
         }

         acquire(thread);
         return true;
      }
   }

   @Override
   public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
      if(Thread.interrupted()) {
         throw new InterruptedException();
      }

      long deadline = System.nanoTime() + unit.toNanos(time);

      synchronized(monitor) {
         Thread thread = Thread.currentThread();
         checkNotLending(thread);

         while(!canAcquire(thread)) {
            long remaining = deadline - System.nanoTime();

            if(remaining <= 0) {
               return false;
            }

            TimeUnit.NANOSECONDS.timedWait(monitor, remaining);
         }

         acquire(thread);
         return true;
      }
   }

   @Override
   public void unlock() {
      synchronized(monitor) {
         if(owner != Thread.currentThread()) {
            throw new IllegalMonitorStateException();
         }

         if(--holds == 0) {
            owner = null;
            generation++;
            monitor.notifyAll();
         }
      }
   }

   /**
    * Conditions are not supported, the engine lock is only used for mutual exclusion.
    */
   @Override
   public Condition newCondition() {
      throw new UnsupportedOperationException();
   }

   /**
    * Check if the current thread owns this lock.
    */
   public boolean isHeldByCurrentThread() {
      synchronized(monitor) {
         return owner == Thread.currentThread();
      }
   }

   /**
    * Get the number of holds on this lock by the current thread.
    */
   public int getHoldCount() {
      synchronized(monitor) {
         return owner == Thread.currentThread() ? holds : 0;
      }
   }

   /**
    * Check if any thread owns this lock.
    */
   public boolean isLocked() {
      synchronized(monitor) {
         return owner != null;
      }
   }

   /**
    * Check if this lock is currently lent to a worker.
    */
   public boolean isLent() {
      synchronized(monitor) {
         return !loans.isEmpty();
      }
   }

   /**
    * Lend this lock to a worker task. The current thread must own the lock. All of its
    * holds are released and saved, and until the returned loan is closed only the
    * thread running the borrower task may acquire the lock. Must be balanced by closing
    * the loan on the same thread, and the lending thread must not use the script engine
    * until then.
    *
    * @param borrower the worker task the current thread is waiting for.
    *
    * @return the loan. Closing it waits for the borrower to release the lock and then
    *         restores the saved holds to the current thread.
    */
   public Loan lend(Borrower borrower) {
      synchronized(monitor) {
         Thread thread = Thread.currentThread();

         if(owner != thread) {
            throw new IllegalMonitorStateException();
         }

         LoanImpl loan = new LoanImpl(thread, holds, borrower);
         loans.push(loan);
         borrower.lentLocks.add(this);
         owner = null;
         holds = 0;
         generation++;
         monitor.notifyAll();
         return loan;
      }
   }

   private boolean canAcquire(Thread thread) {
      if(owner == thread) {
         return true;
      }

      if(owner != null) {
         return false;
      }

      LoanImpl loan = loans.peek();
      return loan == null || !loan.revoked && loan.borrower.isRunningOn(thread);
   }

   /**
    * A thread that lent this lock must not acquire it before closing the loan, it
    * would wait for itself forever.
    */
   private void checkNotLending(Thread thread) {
      if(owner == thread) {
         return;
      }

      for(LoanImpl loan : loans) {
         if(loan.lender == thread) {
            throw new IllegalMonitorStateException("Lock is lent by the current thread");
         }
      }
   }

   private void acquire(Thread thread) {
      owner = thread;
      holds++;
      generation++;
   }

   /**
    * Reclaim a loan: stop the borrower from re-acquiring, wait for it (and any nested loan it
    * made) to let go, and restore the lender's holds.
    *
    * <p>The lender's wait is registered with the lock-stall watchdog and sliced (bug #76967):
    * while the borrower legitimately works with the lent lock (e.g. a condition filter scanning
    * a large base), the lender makes progress through it, so neither the lender's wait nor its
    * outer lens wait is reported as a stall. The wait is never failed: a lender cannot abandon
    * a loan mid-flight, the borrower still holds the lock and the lender's callers restore
    * their monitors on the saved holds. A borrower that is truly stuck leaves this wait
    * without progress, which the watchdog reports (dump, health DOWN) until it lets go.
    */
   private void reclaim(LoanImpl loan) {
      boolean interrupted = false;

      synchronized(monitor) {
         if(loan.closed) {
            return;
         }

         if(loan.lender != Thread.currentThread()) {
            throw new IllegalMonitorStateException();
         }

         // stop the borrower from re-acquiring, then wait for it (and any nested loan it
         // made) to let go
         loan.revoked = true;

         if(isReclaimable(loan)) {
            closeLoan(loan);
            return;
         }
      }

      // the loan is still in use, register the wait (outside of the monitor) and check again
      WaitRecord record = beginReclaim();

      try {
         while(true) {
            synchronized(monitor) {
               if(isReclaimable(loan)) {
                  closeLoan(loan);
                  return;
               }

               try {
                  monitor.wait(record == null ? 10000 : record.waitMillis(10000));
               }
               catch(InterruptedException ex) {
                  interrupted = true;
               }

               if(isReclaimable(loan)) {
                  closeLoan(loan);
                  return;
               }
            }

            sampleReclaim(record);
         }
      }
      finally {
         if(record != null) {
            record.close();
         }

         if(interrupted) {
            Thread.currentThread().interrupt();
         }
      }
   }

   /**
    * Register a reclaim with the lock-stall watchdog. It never throws, the loan must still be
    * reclaimed: if the wait cannot be registered, it is waited for unregistered as before.
    */
   private WaitRecord beginReclaim() {
      try {
         return WaitRegistry.begin("LendableReentrantLock.reclaim", this::getGeneration,
                                   this::getBlockers);
      }
      catch(RuntimeException ex) {
         return null;
      }
   }

   /**
    * Sample the progress of a reclaim, outside of the monitor. It never throws: in fail mode a
    * stuck borrower fails the record (dumped and reported as unreleased by the watchdog), but
    * the lender keeps waiting, since it cannot abandon the loan while the borrower holds it.
    */
   private static void sampleReclaim(WaitRecord record) {
      if(record == null) {
         return;
      }

      try {
         record.checkStall();
      }
      catch(LockStallException ex) {
         // keep waiting for the borrower, see reclaim()
      }
      catch(RuntimeException ex) {
         // never fail the reclaim
      }
   }

   private boolean isReclaimable(LoanImpl loan) {
      return loans.peek() == loan && owner == null;
   }

   private void closeLoan(LoanImpl loan) {
      loans.pop();
      loan.borrower.lentLocks.remove(this);
      owner = loan.lender;
      holds = loan.holds;
      loan.closed = true;
      generation++;
   }

   /**
    * A lent lock. Closing it reclaims the lock for the lending thread.
    */
   public interface Loan extends AutoCloseable {
      @Override
      void close();
   }

   /**
    * A loan that does nothing, used when there is nothing to lend.
    */
   public static final Loan NO_LOAN = () -> {};

   /**
    * Identifies one background worker task that a lock may be lent to. The loan is
    * bound to the task rather than to a thread, so it can be made before the task has
    * started, and a pooled thread cannot use it once the task has ended.
    */
   public static final class Borrower {
      /**
       * Mark the current thread as running this task. Call first thing in the task.
       */
      public void begin() {
         thread = Thread.currentThread();
         CURRENT.set(this);
      }

      /**
       * Mark this task as ended. Call in a finally block at the end of the task, before
       * the thread is returned to its pool.
       */
      public void end() {
         ended = true;
         thread = null;

         if(CURRENT.get() == this) {
            CURRENT.remove();
         }
      }

      /**
       * Get the borrower task running on the current thread, if any.
       */
      public static Borrower current() {
         return CURRENT.get();
      }

      /**
       * Get the thread running this task, or {@code null} if it has not started or has
       * ended. Wait sites use it to tell a working task from a stalled one (bug #76967).
       */
      public Thread getThread() {
         return thread;
      }

      /**
       * Get the locks currently lent to this task. The task may acquire them and lend
       * them on to its own worker (nested loans).
       */
      public List<LendableReentrantLock> getLentLocks() {
         return lentLocks;
      }

      /**
       * Check if this task has not ended yet (it may not have started).
       */
      public boolean isActive() {
         return !ended;
      }

      private boolean isRunningOn(Thread t) {
         return !ended && thread == t;
      }

      private volatile Thread thread;
      private volatile boolean ended;
      private final List<LendableReentrantLock> lentLocks = new CopyOnWriteArrayList<>();
      private static final ThreadLocal<Borrower> CURRENT = new ThreadLocal<>();
   }

   private final class LoanImpl implements Loan {
      LoanImpl(Thread lender, int holds, Borrower borrower) {
         this.lender = lender;
         this.holds = holds;
         this.borrower = borrower;
      }

      @Override
      public void close() {
         reclaim(this);
      }

      private final Thread lender;
      private final int holds;
      private final Borrower borrower;
      private boolean revoked;
      private boolean closed;
   }

   private static final Thread[] NO_THREADS = new Thread[0];

   private final Object monitor = new Object();
   private final Deque<LoanImpl> loans = new ArrayDeque<>();
   private Thread owner;
   private int holds;
   // changes on every acquire, release, loan and reclaim, the watchdog's progress (bug #76967)
   private long generation;
}
