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

import inetsoft.report.internal.license.*;
import inetsoft.sree.security.OrganizationContextHolder;
import org.slf4j.*;

import java.lang.reflect.*;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A thread pool that executes runnables using a pre-configured number of
 * threads.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class ThreadPool {
   /**
    * Create a thread pool with one thread.
    */
   public ThreadPool() {
   }

   /**
    * Create a thread pool with max active number of threads and max number of
    * threads.
    */
   public ThreadPool(int softLimit, int hardLimit) {
      this.softLimit = Math.min(softLimit, hardLimit);
      this.hardLimit = hardLimit;
   }

   /**
    * Create a thread pool with max active number of threads and max number of
    * threads.
    */
   public ThreadPool(int softLimit, int hardLimit, String name) {
      this(softLimit, hardLimit);
      setName(name);
   }

   /**
    * Creates a new instance of {@code ThreadPool}. The size of the thread pool will be based on the
    * licensed threads or CPUs.
    *
    * <p>If a non-thread license is installed, the soft limit is calculated by multiplying the
    * number of licensed CPU cores with <i>softLimitCpuFactor</i>. The hard limit uses the value of
    * the property named by <i>hardLimitProperty</i>. If the property is not set or is invalid, the
    * hard limit is calculated by multiplying the soft limit with <i>hardLimitFactor</i>.
    *
    * @param name               the name of the thread pool.
    * @param softLimitCpuFactor the factor used to calculate the soft limit from the CPU core count.
    * @param hardLimitProperty  the name of the property containing the hard limit.
    * @param hardLimitFactor    the factor used to calculate the hard limit from the soft limit if
    *                           the property is not set.
    */
   public ThreadPool(String name, int softLimitCpuFactor, String hardLimitProperty,
                     int hardLimitFactor)
   {
      this(
         name, softLimitCpuFactor, hardLimitProperty, hardLimitFactor,
         LicenseManager.getInstance().calculateThreadPoolSize(
            softLimitCpuFactor, hardLimitProperty, hardLimitFactor));
   }

   private ThreadPool(String name, int softLimitCpuFactor, String hardLimitProperty,
                      int hardLimitFactor, int[] sizes)
   {
      this(sizes[0], sizes[1]);
      LicenseManager licenseManager = LicenseManager.getInstance();
      setName(name);
      claimedLicenseListener = new ClaimedLicenseResizeListener(
         softLimitCpuFactor, hardLimitProperty, hardLimitFactor, this);
      licenseManager.addClaimedLicenseListener(claimedLicenseListener);
   }

   /**
    * Gets the maximum number of active threads.
    *
    * @return the soft limit.
    */
   public int getSoftLimit() {
      return softLimit;
   }

   /**
    * Gets the maximum number of threads.
    *
    * @return the hard limit.
    */
   public int getHardLimit() {
      return hardLimit;
   }

   /**
    * Resizes this thread pool.
    *
    * @param softLimit the maximum number of active threads.
    * @param hardLimit the maximum number of threads.
    */
   public void resize(int softLimit, int hardLimit) {
      this.softLimit = Math.min(softLimit, hardLimit);
      this.hardLimit = hardLimit;
   }

   /**
    * Set the thread pool name. For debugging purpose.
    */
   public void setName(String name) {
      this.name = name;
   }

   /**
    * Get the name of the pool.
    */
   public String getName() {
      if(name != null) {
         return name;
      }

      return toString();
   }

   /**
    * Check if need to create a new thread.
    */
   private void checkThread() {
      WorkerThread thread = null;
      int tcnt = threads.size();

      // some threads are idle? do not create thread.
      // only if total thread count < max thread count.
      // don't synchronize to avoid lock contention. this may over shoot the number of threads
      // but shouldn't cause any harm.
      if(tcnt - busy.get() < queue.size() && tcnt < hardLimit) {
         thread = new WorkerThread();
         threads.add(thread);
         threadCnt.incrementAndGet();
      }

      if(thread != null) {
         thread.setName(getName() + threadCnt.get());
         thread.setContextClassLoader(Thread.currentThread().getContextClassLoader());
         thread.start();

         if(LOG.isDebugEnabled()) {
            LOG.debug("Create a new thread in thread pool: {} [{}]", getName(), threadCnt.get());
         }
      }
   }

   // dispose threads over the soft limit if there is no job waiting
   private void cleanUp() {
      if(claimedLicenseListener != null) {
         LicenseManager.getInstance().removeClaimedLicenseListener(claimedLicenseListener);
      }

      if(queue.size() > 0 || busy.get() > 0) {
         return;
      }

      long now = System.currentTimeMillis();

      // cleanup every 30s
      if(now < lastCleanup + 30000) {
         return;
      }

      lastCleanup = now;

      // don't remove threads immediately. hold it for a little while to see if it can be reused.
      synchronized(this) {
         try {
            wait(5000);
         }
         catch(Exception ignore) {
         }

         if(queue.size() == 0 && busy.get() == 0 && threads.size() > softLimit) {
            threads.remove(threads.size() - 1).setExitAfter();
         }
      }
   }

   /**
    * Add a task to be executed.
    */
   public void add(Runnable cmd) {
      Principal user = null;

      // is a context runnable? set the context principal to it
      if(cmd instanceof ContextRunnable) {
         user = ThreadContext.getContextPrincipal();

         if(user != null) {
            ((ContextRunnable) cmd).setPrincipal(user);
         }

         if(Thread.currentThread() instanceof GroupedThread) {
            GroupedThread groupedThread = (GroupedThread) Thread.currentThread();

            for(Object record : groupedThread.getRecords()) {
               if(record instanceof String) {
                  ((ContextRunnable) cmd).addRecord((String) record);
               }
            }
         }
      }

      String cid = null;

      if(cmd instanceof IDRunnable) {
         cid = ((IDRunnable) cmd).getContextID();
      }

      queue.add(new RunnableWrapper(cmd));
      checkThread();
   }

   /**
    * Add the runnable to an on-demand pool. The on-demand pool is globally shared that
    * should be used instead of creating a new GroupedThread every time. The semantics of
    * on-demand thread is like creating a new GroupedThread, but can share an on-demand
    * GroupedThread if one becomes available.
    */
   public static void addOnDemand(Runnable cmd) {
      onDemandPool.add(cmd);
   }

   /**
    * Remove one runnable.
    */
   public boolean remove(Runnable cmd) {
      return queue.remove(cmd);
   }

   /**
    * Get the pending count.
    */
   public int getPendingCount() {
      return queue.size();
   }

   /**
    * Get the pending and executing ones.
    */
   public int getCount() {
      return queue.size() + busy.get();
   }

   /**
    * Interrupt all threads.
    */
   public synchronized void interrupt() {
      for(Thread thread : threads) {
         try {
            if(thread instanceof GroupedThread) {
               ((GroupedThread) thread).cancel();
            }
            else {
               thread.interrupt();
            }
         }
         catch(Exception ex) {
            // ignore
         }
      }
   }

   @Override
   public void finalize() throws Throwable {
      dispose();
      super.finalize();
   }

   /**
    * Clear the pending tasks.
    */
   public void clear() {
      queue.clear();
   }

   /**
    * This method must be called to destroy the threads in this cache if the
    * cache is no longer needed.
    */
   public void dispose() {
      if(!disposed) {
         synchronized(this) {
            disposed = true;
            notifyAll();
         }
      }
   }

   /**
    * An runnable with context id.
    */
   public interface IDRunnable extends Runnable {
      /**
       * Get context id, used for log(log_pending), most case, it is the
       * report id or viewsheet id.
       */
      String getContextID();
   }

   /**
    * An extended runnable interface.
    */
   public interface ContextRunnable extends IDRunnable {
      /**
       * Set context user.
       */
      void setPrincipal(Principal user);

      /**
       * Get context user.
       */
      Principal getPrincipal();

      /**
       * Add the record.
       */
      void addRecord(String record);

      /**
       * Get the records count.
       */
      int getRecordCount();

      /**
       * Get the record.
       */
      Object getRecord(int idx);

      /**
       * Remove all the records.
       */
      void removeRecords();

      /**
       * Gets the stack trace from when this runnable was created.
       *
       * @return the stack trace.
       */
      StackTraceElement[] getStackTrace();

      /**
       * Gets the stack trace of the parent thread from when this runnable was
       * created.
       *
       * @return the stack trace or <tt>null</tt> if the parent thread was not
       *         an instance of <tt>GroupedThread</tt>.
       */
      StackTraceElement[] getParentStackTrace();
   }

   /**
    * An extended runnable interface.
    */
   public interface PoolRunnable extends ContextRunnable {
      /**
       * Get the priority of the thread.
       */
      int getPriority();

      /**
       * Wait for the runnable to finish.
       */
      void join(boolean internal) throws Exception;

      /**
       * Get error if any.
       */
      Throwable getError();
   }

   /**
    * Abstract context runnable.
    */
   public abstract static class AbstractContextRunnable implements ContextRunnable {
      /**
       * Creates a new instance of <tt>AbstractContextRunnable</tt>.
       */
      public AbstractContextRunnable() {
         StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();

         if(stackTrace.length > STACK_TRACE_LENGTH_LIMIT) {
            LOG.warn("Excessively large stack trace.", new Exception("stack trace"));
            stackTrace = Arrays.copyOf(stackTrace, STACK_TRACE_LENGTH_LIMIT);
         }

         this.stackTrace = stackTrace;

         if(Thread.currentThread() instanceof GroupedThread) {
            GroupedThread parent = (GroupedThread) Thread.currentThread();
            parentStackTrace = parent.getCreatedStackTrace();
         }
         else {
            parentStackTrace = null;
         }
      }

      /**
       * Set context user.
       */
      @Override
      public void setPrincipal(Principal user) {
         this.user = user;
      }

      /**
       * Get context user.
       */
      @Override
      public Principal getPrincipal() {
         return user;
      }

      /**
       * Add the record.
       */
      @Override
      public void addRecord(String record) {
         records.add(record);
      }

      /**
       * Remove the record.
       */
      public void removeRecord(String record) {
         records.remove(record);
      }

      /**
       * Get the records count.
       */
      @Override
      public int getRecordCount() {
         return records.size();
      }

      /**
       * Get the record.
       */
      @Override
      public Object getRecord(int idx) {
         return records.get(idx);
      }

      /**
       * Remove all the records.
       */
      @Override
      public void removeRecords() {
         records.clear();
      }

      /**
       * Get context id, used for log(log_pending), most case, it is the
       * report id or viewsheet id.
       */
      @Override
      public String getContextID() {
         return null;
      }

      /**
       * {@inheritDoc}
       */
      @Override
      public StackTraceElement[] getStackTrace() {
         return stackTrace;
      }

      /**
       * {@inheritDoc}
       */
      @Override
      public StackTraceElement[] getParentStackTrace() {
         return parentStackTrace;
      }

      private final List<String> records = new ArrayList<>();
      private Principal user = null;
      private final StackTraceElement[] stackTrace;
      private final StackTraceElement[] parentStackTrace;

      private static final int STACK_TRACE_LENGTH_LIMIT = (int) 1e5;
   }

   /**
    * Base implementation of <tt>PoolRunnable</tt>.
    *
    * @author InetSoft Technology
    * @since  12.0
    */
   public abstract static class AbstractPoolRunnable
      extends AbstractContextRunnable implements PoolRunnable
   {
   }

   private static class RunnableWrapper implements Runnable {
      public RunnableWrapper(Runnable target) {
         this.target = target;
         this.tempOrg = OrganizationContextHolder.getCurrentOrgId();
      }

      @Override
      public void run() {
         try {
            if(tempOrg != null) {
               OrganizationContextHolder.setCurrentOrgId(tempOrg);
            }

            target.run();
         }
         finally {
            if(tempOrg != null) {
               OrganizationContextHolder.clear();
            }
         }
      }

      public Runnable unwrap() {
         return target;
      }

      private final Runnable target;
      private final String tempOrg;
   }

   /**
    * Thread for running the runnables.
    */
   private class WorkerThread extends GroupedThread {
      public WorkerThread() {
         setDaemon(true);
      }

      @Override
      protected void doRun() {
         try {
            run0();
         }
         finally {
            threads.remove(this);
         }
      }

      private void run0() {
         outer:
         while(!disposed && !isCancelled() && !exitAfter) {
            Runnable cmd = null;

            try {
               while((cmd = queue.poll(60, TimeUnit.SECONDS)) == null && !exitAfter) {
                  if(disposed || isCancelled()) {
                     break outer;
                  }

                  if(this == threads.get(0)) {
                     cleanUp();
                  }
               }

               // exitAfter or disposed/cancelled
               if(cmd == null) {
                  break;
               }

               busy.incrementAndGet();
            }
            catch(InterruptedException ex) {
               // ignore
            }

            if(cmd == null) {
               continue;
            }

            Runnable unwrapCmd = cmd;

            if(cmd instanceof RunnableWrapper) {
               unwrapCmd = ((RunnableWrapper) cmd).unwrap();
            }

            int pri = getPriority();
            int npri = pri;
            boolean replacedStack = false;
            StackTraceElement[] oldStackTrace = null;
            StackTraceElement[] oldParentStackTrace = null;

            try {
               if(unwrapCmd instanceof PoolRunnable) {
                  npri = ((PoolRunnable) unwrapCmd).getPriority();

                  if(npri != pri) {
                     setPriority(npri);
                  }
               }

               // is a context runnable?  use its principal
               if(unwrapCmd instanceof ContextRunnable) {
                  ContextRunnable contextRunnable = (ContextRunnable) unwrapCmd;
                  Principal user = contextRunnable.getPrincipal();

                  if(user != null) {
                     setPrincipal(user);
                  }

                  Thread currentThread = Thread.currentThread();

                  if(currentThread instanceof GroupedThread) {
                     GroupedThread groupedThread = (GroupedThread) currentThread;
                     oldStackTrace = groupedThread.getCreatedStackTrace();
                     oldParentStackTrace = groupedThread.getParentStackTrace();
                     int records = contextRunnable.getRecordCount();

                     for(int i = 0; i < records; i++) {
                        groupedThread.addRecord(contextRunnable.getRecord(i));
                     }

                     groupedThread.setCreatedStackTrace(contextRunnable.getStackTrace());
                     groupedThread.setParentStackTrace(contextRunnable.getParentStackTrace());
                     replacedStack = true;

                     // clear ThreadContext thread local variables, will be repopulated from the
                     // GroupedThread
                     ThreadContext.setPrincipal(null);
                     ThreadContext.setLocale(null);
                  }
               }

               // @by mikec.
               // creating a new thread to run cmd and wait
               // it be finished (or be destroyed) in current thread.
               // this way the cmd.run will not block current workthread
               // be reused if it is cancelled(destroyed) before it is
               // finished.
               // the case is that if we have a report run in to a long
               // loop, then even it be cancelled it will not release the
               // workthread which will caused any other report pending
               // there until the cancelled report finished.
               if(unwrapCmd instanceof PoolRunnable) {
                  final PoolRunnable pcmd = (PoolRunnable) unwrapCmd;
                  final Runnable originalCmd = cmd;
                  Thread cthread = Thread.currentThread();
                  final GroupedThread parent = cthread instanceof GroupedThread ?
                     (GroupedThread) cthread : null;
                  final Map<String, String> context = MDC.getCopyOfContextMap();

                  GroupedThread t = new GroupedThread(() -> {
                     try {
                        if(context != null) {
                           MDC.setContextMap(context);
                        }

                        originalCmd.run();
                     }
                     finally {
                        MDC.clear();
                     }
                  }, pcmd.getPrincipal());

                  t.setParent(parent);
                  t.setName(getName() + "-" + (++childThreadCount));
                  t.start();

                  try {
                     pcmd.join(true);
                  }
                  catch(InterruptedException ie) {
                     if(isCancelled()) {
                        break outer;
                     }
                  }
                  catch(Exception npe) {
                     LOG.debug("Failed to join thread in the ThreadPool: " + pcmd, npe);
                  }
               }
               else {
                  cmd.run();
               }
            }
            catch(Throwable ex) {
               LOG.error("Failed to execute a thread in ThreadPool: " + unwrapCmd, ex);
            }
            finally {
               busy.decrementAndGet();
               removeRecords(); // discard context records
               setPrincipal(null); // discard context user
            }

            if(pri != npri) {
               setPriority(pri);
            }

            if(replacedStack) {
               final GroupedThread groupedThread = (GroupedThread) Thread.currentThread();
               groupedThread.setCreatedStackTrace(oldStackTrace);
               groupedThread.setParentStackTrace(oldParentStackTrace);
            }
         }
      }

      // exit this thread after finishing the current task
      public void setExitAfter() {
         exitAfter = true;
         interrupt();
      }

      private int childThreadCount = 0;
      private boolean exitAfter = false;
   }

   Comparator<Runnable> runnableComparator = (o1, o2) -> {
      int p1 = (o1 instanceof PoolRunnable) ?
         ((PoolRunnable) o1).getPriority() : Integer.MAX_VALUE;
      int p2 = (o2 instanceof PoolRunnable) ?
         ((PoolRunnable) o2).getPriority() : Integer.MAX_VALUE;
      return p1 - p2;
   };

   private static final ThreadPool onDemandPool = new ThreadPool(20, 1000, "OnDemand Thread");

   private final PriorityBlockingQueue<Runnable> queue =
      new PriorityBlockingQueue<>(20, runnableComparator);
   private final AtomicLong threadCnt = new AtomicLong(0); // number of threads
   private final AtomicInteger busy = new AtomicInteger(0); // number of executing runnables
   private volatile int softLimit = 1; // maximum number of active threads
   private volatile int hardLimit = 1; // maximum number of threads
   private String name; // pool name
   private boolean disposed = false; // destroy this object
   private final List<WorkerThread> threads = new CopyOnWriteArrayList<>();
   private long lastCleanup = 0;
   // keep a reference because a weak reference is held in LicenseManager
   @SuppressWarnings("FieldCanBeLocal")
   private ClaimedLicenseListener claimedLicenseListener;

   private static final Logger LOG = LoggerFactory.getLogger(ThreadPool.class);

   public static final class ClaimedLicenseResizeListener implements ClaimedLicenseListener {
      public ClaimedLicenseResizeListener(int softLimitCpuFactor, String hardLimitProperty,
                                          int hardLimitFactor, ThreadPool pool)
      {
         this.softLimitCpuFactor = softLimitCpuFactor;
         this.hardLimitProperty = hardLimitProperty;
         this.hardLimitFactor = hardLimitFactor;
         this.pool = pool;
      }

      @Override
      public void licenseClaimed(ClaimedLicenseEvent event) {
         LicenseManager licenseManager = LicenseManager.getInstance();
         int[] sizes = licenseManager.calculateThreadPoolSize(
            softLimitCpuFactor, hardLimitProperty, hardLimitFactor);
         pool.resize(sizes[0], sizes[1]);
      }

      private final int softLimitCpuFactor;
      private final String hardLimitProperty;
      private final int hardLimitFactor;
      private final ThreadPool pool;
   }
}
