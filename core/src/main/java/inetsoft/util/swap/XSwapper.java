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
package inetsoft.util.swap;

import com.sun.management.HotSpotDiagnosticMXBean;
import com.sun.management.VMOption;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.util.*;
import inetsoft.util.ConfigurationContext;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import javax.management.ObjectName;
import java.io.File;
import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.lang.management.PlatformManagedObject;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * XSwapper swapps the swappables.
 *
 * @version 9.1
 * @author InetSoft Technology Corp
 */
@Service
@Lazy
public final class XSwapper {
   /**
    * Timestamp being updated by swapper.
    */
   public long cur = System.currentTimeMillis();
   /**
    * Critically low. Any memory allocation could trigger OOM.
    */
   public static final int CRITICAL_MEM = 0;
   /**
    * Bad memory state. In danger of OOM.
    */
   public static final int BAD_MEM = 1;
   /**
    * Low memory state. Need to try to bring it to normal.
    */
   public static final int LOW_MEM = 2;
   /**
    * Normal memory state. Normal range.
    */
   public static final int NORM_MEM = 3;
   /**
    * Good memory state. Plenty of memory.
    */
   public static final int GOOD_MEM = 4;

   /**
    * Check if system is executing something. When the system is executing
    * something, such as query excution, data filtering, report paging,
    * and the like, swappable object must be created. Hence to check
    * whether system is executing something, we could just check whether
    * swappable object is being created.
    */
   public boolean isExecuting() {
      long cur = System.currentTimeMillis();
      // if no swappable object is created within 5 seconds, system is idle
      return cur - cts < 5000;
   }

   /**
    * Register a XSwappableMonitor.
    * @param monitor a XSwappableMonitor.
    */
   public void registerMonitor(XSwappableMonitor monitor) {
      this.monitor.addMonitor(monitor);
   }

   /**
    * Deregister a XSwappableMonitor.
    */
   public void deregisterMonitor(XSwappableMonitor monitor) {
      this.monitor.removeMonitor(monitor);
   }

   /**
    * Get the XSwappableMonitor.
    */
   public XSwappableMonitor getMonitor() {
      return monitor;
   }

   /**
    * Get the total swap count.
    */
   public long getSwapCount() {
      return scount;
   }

   /**
    * Get the free memory ratio.
    * @return the free memory ratio.
    */
   private double getFreeRatio() {
      long max = Runtime.getRuntime().maxMemory();
      return ((double) getFreeSpace()) / max;
   }

   /**
    * Get the free memory.
    * @return the free memory.
    */
   private long getFreeSpace() {
      long max = Runtime.getRuntime().maxMemory();
      long total = Runtime.getRuntime().totalMemory();
      long free = Runtime.getRuntime().freeMemory();

      return free + max - total;
   }

   /**
    * Get memory state at no more than a certain interval.
    */
   public int getMemoryState() {
      long now = System.currentTimeMillis();

      if(now - stateTS > 200) {
         stateTS = now;
         cachedState = getMemoryState0();
      }
      return cachedState;
   }

   /**
    * Get the memory state.
    */
   private int getMemoryState0() {
      return getMemoryState(getFreeRatio());
   }

   /**
    * Get the memory state of a free memory ratio.
    */
   static int getMemoryState(double ratio) {
      // > 40%
      if(ratio >= RATIOS[GOOD_MEM]) {
         return GOOD_MEM;
      }
      // 30% - 40%
      else if(ratio >= RATIOS[NORM_MEM]) {
         return NORM_MEM;
      }
      // 20% - 30%
      else if(ratio >= RATIOS[LOW_MEM]) {
         return LOW_MEM;
      }
      // 15% - 20%
      else if(ratio >= RATIOS[BAD_MEM]) {
         return BAD_MEM;
      }
      // below 15%
      else {
         return CRITICAL_MEM;
      }
   }

   /**
    * Get the memory state without the G1 eden pool, for the cache swap memory scaling metric.
    * Eden holds the objects allocated since the last young collection, mostly garbage, and G1
    * lets it take most of the free heap, so with eden a node with a moderate live set reads as
    * critical much of the time. Everything else, the swapping and the waits for memory
    * included, uses {@link #getMemoryState()}: that reading's margin is what keeps a burst of
    * allocations from running out of memory. Returns {@link #getMemoryState()} if the JVM
    * doesn't use G1, the young generation size is set, eden can't be read, or
    * <tt>swapper.scalingMetric.excludeEden</tt> is <tt>false</tt>.
    */
   public int getMemoryStateExcludingEden() {
      final MemoryPoolMXBean eden;

      try {
         eden = isScalingMetricExcludeEden() ? G1Eden.POOL : null;
      }
      catch(Exception | LinkageError ex) {
         return getMemoryState();
      }

      return getMemoryStateExcludingEden(
         Runtime.getRuntime().maxMemory(), eden,
         () -> ManagementFactory.getMemoryMXBean().getHeapMemoryUsage());
   }

   /**
    * Get the memory state without the G1 eden pool at no more than a certain interval. It has
    * its own cache, so it never changes the state that {@link #getMemoryState()} returns.
    *
    * @param max  the maximum heap size.
    * @param eden the G1 eden pool, or <tt>null</tt> to return {@link #getMemoryState()}.
    * @param heap supplies the heap memory usage.
    */
   int getMemoryStateExcludingEden(long max, MemoryPoolMXBean eden, Supplier<MemoryUsage> heap) {
      if(eden == null) {
         return getMemoryState();
      }

      long now = System.currentTimeMillis();

      if(now - edenStateTS > 200) {
         final long used = getUsedExcludingEden(eden, heap);

         if(used < 0) {
            return getMemoryState();
         }

         edenCachedState = getMemoryState(((double) (max - used)) / max);
         edenStateTS = now;
      }

      return edenCachedState;
   }

   /**
    * Get the heap used memory without the G1 eden pool.
    *
    * @param eden the G1 eden pool.
    * @param heap supplies the heap memory usage.
    *
    * @return the used memory, or <tt>-1</tt> if it can't be read.
    */
   static long getUsedExcludingEden(MemoryPoolMXBean eden, Supplier<MemoryUsage> heap) {
      try {
         // read the heap before eden: a young collection between the two reads then makes
         // the used memory too high, the other order could make it too low
         final long heapUsed = heap.get().getUsed();
         final MemoryUsage edenUsage = eden.getUsage();

         if(edenUsage == null) {
            return -1L;
         }

         return Math.max(heapUsed - edenUsage.getUsed(), 0L);
      }
      catch(Exception | LinkageError ex) {
         return -1L;
      }
   }

   /**
    * Check if the cache swap memory scaling metric excludes the G1 eden pool, from
    * <tt>swapper.scalingMetric.excludeEden</tt>. Only <tt>false</tt> turns it off.
    */
   static boolean isScalingMetricExcludeEden() {
      // read every time so a property change takes effect without a restart. the setting is
      // JVM-wide, so don't let the principal of the calling thread pick an organization's value
      try {
         final String value = SreeEnv.getProperty(SCALING_METRIC_EXCLUDE_EDEN, false, false);
         return value == null || !"false".equalsIgnoreCase(value.trim());
      }
      catch(Exception ex) {
         // property engine unavailable (e.g. during test teardown)
         return true;
      }
   }

   /**
    * Resolves the G1 eden pool on first use. The heap pools don't change for the life of the
    * JVM, and neither does the young generation sizing.
    */
   static final class G1Eden {
      static final MemoryPoolMXBean POOL = resolve();

      private static MemoryPoolMXBean resolve() {
         // linking DiagnosticBean::get loads com.sun.management, which a runtime without the
         // jdk.management module doesn't have. this initializer must never throw, or every
         // later use of POOL would throw NoClassDefFoundError
         try {
            return find(ManagementFactory::getMemoryPoolMXBeans, DiagnosticBean::get);
         }
         catch(Exception | LinkageError ex) {
            LOG.debug("Failed to find the G1 eden pool, the scaling metric includes eden", ex);
            return null;
         }
      }

      /**
       * Find the G1 eden pool, or <tt>null</tt> if the scaling metric should use the memory
       * state with eden.
       *
       * @param pools supplies the memory pools.
       * @param bean  supplies the HotSpot diagnostic MXBean, typed as a PlatformManagedObject,
       *              see {@link DiagnosticBean}.
       */
      static MemoryPoolMXBean find(Supplier<List<MemoryPoolMXBean>> pools,
                                   Supplier<? extends PlatformManagedObject> bean)
      {
         try {
            // the exact name limits this to G1. Parallel and Serial size the young generation
            // as a fixed share of the heap, so near OOM their live objects stay in eden and a
            // reading without eden would never get critical
            final MemoryPoolMXBean eden = pools.get().stream()
               .filter(pool -> pool.getType() == MemoryType.HEAP)
               .filter(pool -> EDEN_POOL.equals(pool.getName()))
               .findFirst()
               .orElse(null);

            if(eden == null) {
               LOG.debug("No {} pool, the scaling metric includes eden", EDEN_POOL);
               return null;
            }

            if(fixedYoungSize(bean)) {
               LOG.info("The young generation size is set, or can't be checked, so the " +
                           "cache swap memory scaling metric includes eden");
               return null;
            }

            LOG.debug("The cache swap memory scaling metric excludes the {} pool", EDEN_POOL);
            return eden;
         }
         catch(Exception | LinkageError ex) {
            LOG.debug("Failed to find the G1 eden pool, the scaling metric includes eden", ex);
            return null;
         }
      }

      /**
       * Check if the young generation may have a fixed minimum size (-Xmn or -XX:NewSize). G1
       * then can't shrink eden as the old generation fills, so a reading without eden could
       * never get critical. Returns <tt>true</tt> if it can't be checked.
       */
      static boolean fixedYoungSize(Supplier<? extends PlatformManagedObject> bean) {
         try {
            final HotSpotDiagnosticMXBean diagnostic = (HotSpotDiagnosticMXBean) bean.get();

            if(diagnostic == null) {
               return true;
            }

            final VMOption.Origin origin = diagnostic.getVMOption("NewSize").getOrigin();
            return origin != VMOption.Origin.DEFAULT && origin != VMOption.Origin.ERGONOMIC;
         }
         catch(Exception | LinkageError ex) {
            LOG.debug("Failed to check the NewSize option", ex);
            return true;
         }
      }
   }

   /**
    * Get the swapper.
    */
   public static XSwapper getSwapper() {
      return ConfigurationContext.getContext().getSpringBean(XSwapper.class);
   }

   /**
    * Get all the swappables in XSwapper.
    * Notice:The returned swappables MUST be cleared by garbage collector after
    * they are used. They should not be kept by other objects permanently.
    * @return the swappables.
    */
   public XSwappable[] getAllSwappables() {
      Vector<XSwappable> results = new Vector<>();

      if(stopped) {
         return new XSwappable[] {};
      }

      for(XSwapperThread thread : threads) {
         if(thread.isCancelled()) {
            continue;
         }

         XWeakList list;

         synchronized(thread.list) {
            list = (XWeakList) thread.list.clone();
         }

         for(int i = 0; i < list.size; i++) {
            XSwappable swappable = (XSwappable) list.get(i);

            if(swappable == null) {
               continue;
            }

            results.add(swappable);
         }
      }

      return results.toArray(new XSwappable[] {});
   }

   /**
    * Create an instance of <tt>XSwapper</tt>.
    */
   public XSwapper() {
      super();

      String cdir = null;
      FileSystemService fileSystemService = FileSystemService.getInstance();

      try {
         cdir = fileSystemService.getCacheDirectory();
      }
      catch(IOException e) {
         DEBUG_LOG.error(e.getMessage(), e);
      }

      DEBUG_LOG.debug("Swap dir is {}", cdir);
      final File file = fileSystemService.getFile(cdir);

      if(file.isDirectory()) {
         // the cache directory could be very large so do it in background
         // to avoid holding up the server startup
         (new Thread(() -> {
            Cluster cluster;
            Lock lock;

            try {
               cluster = Cluster.getInstance();
               lock = cluster.getLock(SWAP_FILE_MAP_LOCK);
               lock.lock();
            }
            catch(Exception e) {
               DEBUG_LOG.debug(
                  "Unable to acquire swap file map lock for cache cleanup, " +
                  "cluster may be stopped", e);
               return;
            }

            try {
               Map<String, Integer> map = cluster.getMap(SWAP_FILE_MAP);
               File[] files = file.listFiles();

               for(int i = 0; files != null && i < files.length; i++) {
                  // Bug #77600, this JVM may already have written live swap files
                  if(!files[i].isDirectory() && files[i].getName().endsWith(".tdat") &&
                     !map.containsKey(files[i].getAbsolutePath()) &&
                     !isOwnSwapFile(files[i].getName()))
                  {
                     files[i].delete();
                  }
               }
            }
            finally {
               try {
                  lock.unlock();
               }
               catch(Exception e) {
                  DEBUG_LOG.debug("Unable to release swap file map lock", e);
               }
            }
         })).start();
      }

      Principal principal = ThreadContext.getContextPrincipal();
      threads = new XSwapperThread[getThreadCount()];

      for(int i = 0; i < threads.length; i++) {
         threads[i] = new XSwapperThread(principal);
         threads[i].start();
      }

      // the flag is JVM-wide, so check it once even if there are several swappers
      if(periodicGCChecked.compareAndSet(false, true)) {
         // linking DiagnosticBean::get loads com.sun.management, which a runtime without the
         // jdk.management module doesn't have, so it must not escape the constructor either
         try {
            enablePeriodicGC(XSwapper::getPeriodicGCInterval,
                             () -> isG1GC(ManagementFactory.getGarbageCollectorMXBeans()),
                             DiagnosticBean::get);
         }
         catch(Exception | LinkageError ex) {
            LOG.debug("Failed to set {}", PERIODIC_GC_OPTION, ex);
         }
      }
   }

   /**
    * Gets the number of threads that are waiting for the objects to be swapped out.
    *
    * @return the thread count.
    */
   public long getWaitingThreadCount() {
      return waitingThreadCount.get();
   }

   /**
    * Gets the number of threads currently blocked waiting for memory because the memory
    * state is critical (free ratio below 15%). Unlike {@link #getWaitingThreadCount()},
    * this excludes threads that entered {@code waitForMemory()} but returned immediately
    * because memory was sufficient.
    *
    * @return the count of threads blocked in critical memory state.
    */
   public long getCriticalWaitingThreadCount() {
      return criticalWaitCount.get();
   }

   /**
    * Wait for swapper to swap out objects if memory is low. This should
    * be called when there is a surge of memory request to avoid running
    * out of memory before swapping could be performed.
    */
   public void waitForMemory() {
      waitingThreadCount.incrementAndGet();
      boolean criticalWait = false;

      try {
         final boolean isThreadSwapping = swapping.get();

         // prevent deadlock
         if(isThreadSwapping) {
            // Calling doGC here can cause extreme slowdowns due to being called too frequently.
            //if(getMemoryState() < LOW_MEM) {
            //   doGC();
            //}

            return;
         }

         if(getMemoryState() > CRITICAL_MEM) {
            return;
         }

         criticalWait = true;
         criticalWaitCount.incrementAndGet();

         // Cap the total time spent waiting here, measured from the moment the wait starts so
         // that it also covers the uninterruptible waitLock.lock() below. The criticalNoSwap
         // escape further down only fires when a swap sweep frees nothing at all; if the
         // swapper keeps evicting something on every round but memory never recovers, the
         // loop never terminates and the thread parks indefinitely -- together with any lock
         // it already holds, which wedges the viewsheet (a sandbox read lock held here blocks
         // every writer and, since that lock is non-fair, every reader queued behind them).
         // Proceeding under critical memory is a risk, but a bounded risk beats hanging
         // forever, and this generalizes the escape criticalNoSwap already provides.
         final long maxWait = getMaxCriticalWait();
         final long deadline = System.currentTimeMillis() + maxWait;
         boolean timedOut = false;

         final Thread curr = Thread.currentThread();
         // waiting for memory in swapper thread, could deadlock
         boolean deadlock = curr instanceof XSwapperThread;

         // Swap the remaining swappables in background. This is called when
         // there is a deadlock caused by the waitForMemory() call triggered
         // by swap().
         if(deadlock) {
            XSwapperWorker worker = new XSwapperWorker((XSwapperThread) curr) {
               @Override
               protected void doRun() {
                  swapping.set(true);

                  try {
                     ((XSwapperThread) curr).swapRemaining();
                  }
                  finally {
                     swapping.set(false);
                  }
               }
            };

            worker.start();
            doGC();
         }

         // optimization, lock to avoid multiple threads doing the loop wasting cpu
         if(!deadlock) {
            waitLock.lock();
         }

         try {
            // if critical mode and nothing to swap, let one thread to proceed
            // otherwise there is a 'deadlock' and the process would wait forever
            while(getMemoryState() == CRITICAL_MEM && criticalNoSwap.get() < 3) {
               if(System.currentTimeMillis() >= deadline) {
                  timedOut = true;
                  break;
               }

               // memory freed by swapping, or by sheets that were closed, only shows up in the
               // memory state after a collection, and the swapper only collects when it swaps
               // something. doGC() is throttled, so a waiter collects on its first pass and then
               // only as often as the throttle allows. stop waiting if it freed enough.
               if(!deadlock && doGC(true) && getMemoryState() > CRITICAL_MEM) {
                  break;
               }

               synchronized(swapLock) {
                  swapLock.notifyAll();
               }

               // if blocked in swapping thread, and the (above) swapRemaining
               // has finished, don't sleep otherwise the swapping thread
               // just blocks and nothing will be swapped out in 200ms
               if(deadlock) {
                  if(!swapping.get()) {
                     break;
                  }
               }

               try {
                  if(deadlock) {
                     Thread.sleep(200);
                  }
                  else {
                     // Use Condition.await() instead of Thread.sleep() so that waitLock is
                     // released during the wait. Holding waitLock across Thread.sleep() blocks
                     // all threads that need to enter waitForMemory(), including threads that
                     // hold critical locks (VS write lock, Ignite distributed locks), causing a
                     // distributed deadlock under high-concurrency export workloads. (Bug #73990)
                     waitCondition.await(200, TimeUnit.MILLISECONDS);
                  }
               }
               catch(InterruptedException ignore) {
               }

               if(deadlock) {
                  doGC();
               }
            }
         }
         finally {
            if(criticalNoSwap.getAndSet(0) > 0) {
               doGC();
            }

            if(timedOut) {
               LOG.warn("Timed out after {}ms waiting for memory to be swapped out; " +
                           "proceeding under critical memory. waiting={}, critical={}",
                        maxWait, getWaitingThreadCount(), getCriticalWaitingThreadCount());
            }

            if(!deadlock) {
               waitLock.unlock();
            }
         }
      }
      finally {
         waitingThreadCount.decrementAndGet();

         if(criticalWait) {
            criticalWaitCount.decrementAndGet();
         }
      }
   }

   /**
    * Run a throttled garbage collection for the swapper sweep, honoring the back-off. See
    * {@link #doGC(boolean)}.
    */
   private void doGC() {
      doGC(false);
   }

   /**
    * Request a garbage collection for a background task, for example after memory was freed
    * by dropping cached data. It shares the swapper sweep's throttle and back-off, so it is
    * often a no-op. See {@link #doGC(boolean)}.
    *
    * @return <tt>true</tt> if a garbage collection was run.
    */
   public boolean requestGC() {
      return doGC(false);
   }

   /**
    * Run a full garbage collection, if the throttle allows it, and clear the cached memory
    * state if one ran. Two collections are always spaced at least
    * <tt>max(swapper.gc.min.interval, GC_PAUSE_FACTOR * last pause)</tt> apart, for every
    * caller, so the swapper's collections never take more than about 1/GC_PAUSE_FACTOR (5%)
    * of wall time, however large the heap. In addition, while memory stays critical after a
    * collection, which means the heap is really full of live data, the sweep backs off up to
    * MAX_GC_BACKOFF.
    *
    * @param waiting <tt>true</tt> if called for a thread waiting in waitForMemory(). Such a
    *                call honors the spacing but not the sweep back-off, so that an earlier
    *                period of real memory pressure can't hold off the collection that a waiter
    *                needs to see memory that has since become garbage. With the default
    *                interval and pauses up to 500ms the waiter gets one within 10s.
    *
    * @return <tt>true</tt> if a garbage collection was run.
    */
   boolean doGC(boolean waiting) {
      try {
         final long base = getGCMinInterval();
         final long spacing = Math.max(base, GC_PAUSE_FACTOR * lastGCPause.get());
         final long interval = waiting ? spacing : Math.max(spacing, gcBackoff.get());
         final long now = clock.getAsLong();
         final long last = lastGC.get();

         // claim the slot before collecting so that concurrent callers don't stack collections
         if(now - last < interval || !lastGC.compareAndSet(last, now)) {
            return false;
         }

         final long count = getGCCount();
         final long free = getFreeSpace();
         runGC();
         lastGCPause.set(Math.max(clock.getAsLong() - now, 0L));
         final long freed = getFreeSpace() - free;
         final boolean collected = getGCCount() != count;
         stateTS = 0;
         final boolean critical = getMemoryState() == CRITICAL_MEM;
         gcBackoff.set(critical ? Math.min(Math.max(base, gcBackoff.get()) * 2, MAX_GC_BACKOFF) : 0);

         if(!collected) {
            if(gcNotRunWarned.compareAndSet(false, true)) {
               LOG.warn("A garbage collection requested by the swapper did not run. Garbage is " +
                           "not reclaimed, so the memory state can stay critical and requests " +
                           "can wait up to swapper.critical.max.wait. Explicit garbage " +
                           "collection is probably disabled in this JVM, for example " +
                           "-XX:+DisableExplicitGC with the Shenandoah collector.");
            }
         }
         // this is also what a heap that is really full of live data looks like, so it's only a hint
         else if(critical && freed < Runtime.getRuntime().maxMemory() / 20) {
            if(gcFreedLittleLogged.compareAndSet(false, true)) {
               LOG.info("A garbage collection requested by the swapper freed only {}MB and " +
                           "memory is still critical. If the heap is not really full of live " +
                           "data, the collection is probably concurrent and can't reclaim " +
                           "garbage mixed with live objects, for example " +
                           "-XX:+ExplicitGCInvokesConcurrent with the G1 collector.",
                        Math.max(freed, 0) / (1024 * 1024));
            }
         }

         return true;
      }
      catch(Exception | LinkageError ex) {
         LOG.debug("Failed to run garbage collection", ex);
         return false;
      }
   }

   /**
    * Run a full garbage collection. This invokes the DiagnosticCommand MBean, which runs the
    * same command as <tt>jcmd GC.run</tt> and is not blocked by -XX:+DisableExplicitGC, and
    * falls back to System.gc() if the MBean is unavailable.
    */
   void runGC() {
      if(!dcmdUnavailable) {
         try {
            ManagementFactory.getPlatformMBeanServer().invoke(
               new ObjectName("com.sun.management:type=DiagnosticCommand"), "gcRun", null, null);
            lastGCDiagnostic = true;
            return;
         }
         catch(Exception | LinkageError ex) {
            dcmdUnavailable = true;
            LOG.debug("DiagnosticCommand MBean is not available, using System.gc()", ex);
         }
      }

      lastGCDiagnostic = false;
      System.gc();
   }

   /**
    * Check if the last runGC() went through the DiagnosticCommand MBean, not the System.gc()
    * fallback. For tests.
    */
   boolean isLastGCDiagnostic() {
      return lastGCDiagnostic;
   }

   /**
    * Get the total number of collections run by all collectors.
    */
   private static long getGCCount() {
      long count = 0;

      for(GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
         count += Math.max(bean.getCollectionCount(), 0);
      }

      return count;
   }

   /**
    * Get the minimum time, in milliseconds, between two garbage collections run by the
    * swapper. Values below MIN_GC_INTERVAL, including 0, are raised to MIN_GC_INTERVAL, so
    * the throttle can't be turned off.
    */
   long getGCMinInterval() {
      // test override
      if(gcMinInterval >= 0) {
         return gcMinInterval;
      }

      // read every time so a property change takes effect without a restart
      try {
         return Math.max(MIN_GC_INTERVAL, Long.parseLong(
            SreeEnv.getProperty("swapper.gc.min.interval",
                                Long.toString(DEFAULT_GC_MIN_INTERVAL))));
      }
      catch(NumberFormatException ex) {
         if(gcIntervalWarned.compareAndSet(false, true)) {
            LOG.warn("Invalid swapper.gc.min.interval value, using {}ms",
                     DEFAULT_GC_MIN_INTERVAL, ex);
         }

         return DEFAULT_GC_MIN_INTERVAL;
      }
   }

   /**
    * Set the minimum garbage collection interval. For tests.
    */
   void setGCMinInterval(long gcMinInterval) {
      this.gcMinInterval = gcMinInterval;
   }

   /**
    * Stop the swapper.
    */
   @PreDestroy
   public void stop() {
      stopped = true;

      if(threads != null) {
         for(XSwapperThread thread : threads) {
            thread.cancel();
         }

         try {
            for(XSwapperThread thread : threads) {
               thread.join(15000L);
            }
         }
         catch(InterruptedException ignore) {
         }
      }
   }

   /**
    * Start the swapper.
    */
   public void start() {
      stopped = false;
   }

   /**
    * Get the maximum time, in milliseconds, that waitForMemory() may block a thread while
    * the memory state stays critical.
    */
   long getMaxCriticalWait() {
      // test override
      if(maxCriticalWait >= 0) {
         return maxCriticalWait;
      }

      // read every time so a property change takes effect without a restart. don't let a
      // malformed value throw out of waitForMemory(), which runs in the middle of data fetches
      try {
         return Long.parseLong(
            SreeEnv.getProperty("swapper.critical.max.wait",
                                Long.toString(DEFAULT_CRITICAL_WAIT)));
      }
      catch(NumberFormatException ex) {
         LOG.warn("Invalid swapper.critical.max.wait value, using {}ms",
                  DEFAULT_CRITICAL_WAIT, ex);
         return DEFAULT_CRITICAL_WAIT;
      }
   }

   /**
    * Set the maximum critical-memory wait. For tests.
    */
   void setMaxCriticalWait(long maxCriticalWait) {
      this.maxCriticalWait = maxCriticalWait;
   }

   /**
    * Turn on G1's periodic collection, which runs a concurrent cycle when no collection of
    * any kind has run for the interval. The memory state counts garbage until a collection
    * reclaims it, and a node that allocates little can go hours without one, so without
    * this an idle node keeps reading a stale, low memory state. Under load collections run
    * anyway and the periodic collection never fires. ZGC and Shenandoah already collect an
    * idle heap by default. A value set on the command line or by other means is kept.
    *
    * @param interval supplies the interval in milliseconds, 0 to leave the option alone.
    * @param g1       supplies <tt>true</tt> if the JVM uses the G1 collector.
    * @param bean     supplies the HotSpot diagnostic MXBean. It's typed as a
    *                 PlatformManagedObject so that this signature doesn't name
    *                 com.sun.management, see {@link DiagnosticBean}.
    *
    * @return <tt>true</tt> if the option was set.
    */
   static boolean enablePeriodicGC(LongSupplier interval, BooleanSupplier g1,
                                   Supplier<? extends PlatformManagedObject> bean)
   {
      // everything runs inside the try so that a failure can't stop the swapper from starting
      try {
         final long millis = interval.getAsLong();

         if(millis <= 0 || !g1.getAsBoolean()) {
            return false;
         }

         final HotSpotDiagnosticMXBean diagnostic = (HotSpotDiagnosticMXBean) bean.get();

         if(diagnostic == null) {
            return false;
         }

         final VMOption option = diagnostic.getVMOption(PERIODIC_GC_OPTION);

         // MANAGEMENT is normally an earlier swapper in this JVM; any other origin was set
         // by the user
         if(option.getOrigin() != VMOption.Origin.DEFAULT || !option.isWriteable()) {
            LOG.debug("Not changing {}, it is {} ({})", PERIODIC_GC_OPTION, option.getValue(),
                      option.getOrigin());
            return false;
         }

         diagnostic.setVMOption(PERIODIC_GC_OPTION, Long.toString(millis));
         LOG.info("Enabled G1 periodic garbage collection after {}ms without a collection, " +
                     "so that the memory of an idle server is reclaimed. To disable it, set " +
                     "swapper.idle.gc.interval to 0 and restart; a change takes effect only " +
                     "after a restart.", millis);
         return true;
      }
      catch(Exception | LinkageError ex) {
         LOG.debug("Failed to set {}", PERIODIC_GC_OPTION, ex);
         return false;
      }
   }

   /**
    * Check if the JVM uses the G1 collector.
    */
   static boolean isG1GC(Collection<GarbageCollectorMXBean> beans) {
      return beans.stream().anyMatch(bean -> bean.getName().startsWith("G1 "));
   }

   /**
    * Get the G1 periodic collection interval, in milliseconds, from
    * <tt>swapper.idle.gc.interval</tt>. 0 or less turns it off, and values below
    * MIN_PERIODIC_GC_INTERVAL are raised to it.
    */
   static long getPeriodicGCInterval() {
      try {
         final long interval = Long.parseLong(
            SreeEnv.getProperty("swapper.idle.gc.interval",
                                Long.toString(DEFAULT_PERIODIC_GC_INTERVAL)).trim());
         return interval <= 0 ? 0 : Math.max(interval, MIN_PERIODIC_GC_INTERVAL);
      }
      catch(NumberFormatException ex) {
         LOG.warn("Invalid swapper.idle.gc.interval value, using {}ms",
                  DEFAULT_PERIODIC_GC_INTERVAL, ex);
         return DEFAULT_PERIODIC_GC_INTERVAL;
      }
   }

   /**
    * Get the thread count.
    * @return the thread count.
    */
   private int getThreadCount() {
      if(tcount == 0) {
         tcount = Integer.parseInt(SreeEnv.getProperty("swapper.count"));
         DEBUG_LOG.debug("Swap thread count is {}", tcount);
      }

      return tcount;
   }

   /**
    * Get the next prefix.
    * @return the next prefix.
    */
   public String getPrefix() {
      return "s" + seed + "_" + counter.incrementAndGet();
   }

   /**
    * Check if a file carries the prefix of this swapper. Such a file belongs to a
    * swappable of this JVM, which deletes it itself, so cache clean-up must not
    * remove it even if it is not registered in the swap file map.
    * @param name the file name.
    * @return <tt>true</tt> if the file was created by this swapper.
    */
   public boolean isOwnSwapFile(String name) {
      return name != null && name.startsWith("s" + seed + "_");
   }

   /**
    * Register a swappable.
    * @param swappable the specified swappable.
    */
   public void register(XSwappable swappable) {
      if(stopped) {
         cts = System.currentTimeMillis();
         return;
      }

      int circle = this.circle % getThreadCount();
      threads[circle].register(swappable);
      this.circle++;
      cts = System.currentTimeMillis();
   }

   /**
    * Deregister a swappable.
    * @param swappable the specified swappable.
    */
   public void deregister(XSwappable swappable) {
      if(stopped) {
         return;
      }

      for(XSwapperThread thread : threads) {
         if(thread.deregister(swappable)) {
            break;
         }
      }
   }

   /**
    * Worker thread to execute swapping in case a deadlock is detected.
    */
   private class XSwapperWorker extends GroupedThread {
      public XSwapperWorker(XSwapperThread parent) {
         this.parent = parent;
      }

      public void swapRemaining() {
         parent.swapRemaining();
      }

      private final XSwapperThread parent;
   }

   /**
    * XSwapper thread.
    */
   private final class XSwapperThread extends GroupedThread {
      public XSwapperThread(Principal contextPrincipal) {
         super();
         this.principal = contextPrincipal;
         setDaemon(true);
      }

      public void register(XSwappable swappable) {
         synchronized(list) {
            list.add(swappable);
         }
      }

      public boolean deregister(XSwappable swappable) {
         synchronized(list) {
            return list.remove(swappable);
         }
      }

      @Override
      protected void doRun() {
         Principal oldPrincipal = ThreadContext.getContextPrincipal();
         ThreadContext.setContextPrincipal(principal);

         try {
            int state = GOOD_MEM;
            long waitTime = 0;
            swapping.set(true);

            outer:
            while(!isCancelled()) {
               cur = System.currentTimeMillis();

               if(stopped || list.isEmpty()) {
                  waitTime = 5000;
               }
               else {
                  // use the waitTime set in the loop
               }

               try {
                  synchronized(swapLock) {
                     swapLock.wait(waitTime);
                  }
               }
               catch(Throwable ex) {
                  if(isCancelled()) {
                     break;
                  }
               }

               state = getMemoryState();

               if(state == GOOD_MEM) {
                  // clear empty weak references per 10 minutes. It's not a
                  // memory leak problem. However, it's confusing when memory
                  // state is always good, for weak references are accumulated
                  if(cur - lcheck >= 600000L) {
                     lcheck = cur;

                     for(int i = list.size - 1; i >= 0; i--) {
                        XSwappable swappable = (XSwappable) list.get(i);

                        if(stopped) {
                           break;
                        }

                        if(isCancelled()) {
                           break outer;
                        }

                        if(swappable == null || !swappable.isSwappable()) {
                           synchronized(list) {
                              list.remove(i);
                           }
                        }
                     }
                  }

                  continue;
               }

               lcheck = cur;

               switch(state) {
               case GOOD_MEM:
               case NORM_MEM:
                  setPriority(MIN_PRIORITY);
                  break;
               case LOW_MEM:
                  setPriority(MIN_PRIORITY + 1);
                  break;
               case BAD_MEM:
                  setPriority(NORM_PRIORITY);
                  break;
               default: // critical
                  setPriority(NORM_PRIORITY + 1);
                  break;
               }

               try {
                  swaplist = new XObjectList();
                  cur = System.currentTimeMillis();

                  for(int i = list.size - 1; i >= 0; i--) {
                     XSwappable swappable = (XSwappable) list.get(i);

                     if(stopped) {
                        break;
                     }

                     if(isCancelled()) {
                        break outer;
                     }

                     if(swappable == null || !swappable.isSwappable()) {
                        synchronized(list) {
                           list.remove(i);
                        }

                        continue;
                     }

                     double priority = swappable.getSwapPriority();

                     if(priority == 0 || priority < PRIORITY[state]) {
                        continue;
                     }

                     swaplist.add(swappable);
                  }

                  if(isCancelled()) {
                     break outer;
                  }

                  if(swaplist.size <= 3 && state > CRITICAL_MEM) {
                     waitTime = 3000;
                     continue;
                  }

                  Arrays.sort(swaplist.arr, 0, swaplist.size, new XSwappable.PriorityComparator());
                  int max = (int) (PERCENT[state] * Math.max(100, swaplist.size));
                  max = Math.min(max, swaplist.size);

                  swapCnt = 0;
                  swapIdx.set(-1);
                  swapMax = max;

                  swapRemaining();

                  swapIdx.set(-1);
                  swaplist.clear();

                  if(swapCnt == 0 && state == CRITICAL_MEM) {
                     criticalNoSwap.incrementAndGet();
                  }

                  // get an acurate memory state after swapping
                  if(swapCnt > 0 && state <= BAD_MEM) {
                     doGC();
                  }
               }
               catch(ShutdownException ignore) {
                  // server is shutting down, ignore
                  break;
               }
               catch(Throwable ex) {
                  LOG.error("Failed to swap objects out of memory", ex);
               }

               waitTime = switch(getMemoryState()) {
                  case GOOD_MEM -> 5000;
                  case NORM_MEM -> 3000;
                  case LOW_MEM -> 2000;
                  case BAD_MEM -> 1000;
                  default -> // critical
                     500;
               };

               setPriority(NORM_PRIORITY);
            }
         }
         catch(ShutdownException ignore) {
            // server is shutting down, ignore
         }
         finally {
            ThreadContext.setContextPrincipal(oldPrincipal);
         }
      }

      /**
       * Swap the swappables in swaplist.
       */
      public void swapRemaining() {
         List<XSwappable> swapped = new ArrayList<>();

         for(int idx = swapIdx.incrementAndGet(); idx < swaplist.size; idx = swapIdx.incrementAndGet()) {
            XSwappable swappable = (XSwappable) swaplist.arr[idx];

            if(isCancelled()) {
               break;
            }

            if(swappable == null || swappable.getSwapPriority() == 0) {
               continue;
            }

            if(swappable.swap()) {
               swapped.add(swappable);
            }

            cur = System.currentTimeMillis();
            scount++;
            swapCnt++;

            if(swapCnt == swapMax) {
               break;
            }
         }

         // optimization, lock the map and bulk add files to cleaner instead
         // of doing so individually. If the cluster is unavailable (e.g.
         // Ignite stopped), skip locking and still add files to the cleaner.
         Lock lock;

         try {
            Cluster cluster = Cluster.getInstance();
            lock = cluster.getLock(SWAP_FILE_MAP_LOCK);
            lock.lock();
         }
         catch(Exception e) {
            LOG.debug("Unable to acquire swap file map lock, cluster may be stopped", e);
            lock = null;
         }

         try {
            // if there are any swap files then add them to the cleaner so that
            // the files can be removed once they are no longer referenced
            for(XSwappable swappable : swapped) {
               File[] swapFiles = swappable.getSwapFiles();

               if(swapFiles != null && swapFiles.length > 0) {
                  Cleaner.add(new XSwappableReference(swappable, swapFiles));
               }
            }
         }
         finally {
            if(lock != null) {
               try {
                  lock.unlock();
               }
               catch(Exception e) {
                  LOG.debug("Unable to release swap file map lock", e);
               }
            }
         }
      }

      private long lcheck = cur;
      private final Principal principal;
      private final XWeakList list = new XWeakList();
      private XObjectList swaplist = new XObjectList();
      private final AtomicInteger swapIdx = new AtomicInteger(-1); // the current swappable being swapped
      private int swapMax; // max items to swap
      private int swapCnt; // swapped out count
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(XSwapper.class);

   // default cap on how long waitForMemory() blocks while the memory state stays critical
   private static final long DEFAULT_CRITICAL_WAIT = 30000L;
   // default minimum time between two garbage collections run by the swapper
   private static final long DEFAULT_GC_MIN_INTERVAL = 10000L;
   // cap on the garbage collection interval while memory stays critical after a collection
   private static final long MAX_GC_BACKOFF = 60000L;
   // lowest accepted swapper.gc.min.interval
   private static final long MIN_GC_INTERVAL = 1000L;
   // minimum spacing between two garbage collections, as a multiple of the last pause
   private static final long GC_PAUSE_FACTOR = 20L;
   // G1 option for a concurrent collection after an interval without any collection
   private static final String PERIODIC_GC_OPTION = "G1PeriodicGCInterval";
   // default G1 periodic collection interval
   private static final long DEFAULT_PERIODIC_GC_INTERVAL = 300000L;
   // lowest accepted swapper.idle.gc.interval other than 0; garbage mixed with live objects
   // takes about 9 periodic collections to reclaim, so shorter intervals only add cycles
   private static final long MIN_PERIODIC_GC_INTERVAL = 60000L;
   // name of the G1 eden memory pool
   private static final String EDEN_POOL = "G1 Eden Space";
   // turns off the eden-excluded memory state of the cache swap memory scaling metric
   private static final String SCALING_METRIC_EXCLUDE_EDEN = "swapper.scalingMetric.excludeEden";
   // set when the first swapper in this JVM has checked G1PeriodicGCInterval
   private static final AtomicBoolean periodicGCChecked = new AtomicBoolean(false);
   // swapping thresholds for [critical, bad, low, norm, good]
   private static final int[] PRIORITY = {1, 5, 20, 50, 200};
   // swapping percentage for [critical, bad, low, norm, good]
   private static final double[] PERCENT = {0.3, 0.3, 0.4, 0.5, 0.6};

   // free memory ratio for different states
   private static final double[] RATIOS = new double[5];

   // JVM normally starts to throw OOM when the memory use reaches
   // between 10-20%. We force a 15% free memory ratio by default.
   // In case the OOM still occurs, this property can be used to raise
   // the free ratio (e.g. 0.2) to avoid OOM.
   static {
      String str = null;

      try {
         str = SreeEnv.getProperty("swapper.free.ratio");
      }
      catch(Exception ignored) {
         // singleton manager unavailable (e.g. during test teardown); use default ratio
      }

      double ratio = 0.20;

      if(str != null) {
         try {
            ratio = Double.parseDouble(str);
         }
         catch(Exception ex) {
            LOG.warn(
                        "Invalid swapper free ratio: " + str, ex);
         }
      }
      else if(XSwapUtil.isCmsGC()) {
         ratio = 0.10;
      }

      RATIOS[BAD_MEM] = ratio;
      ratio += 0.05;
      RATIOS[LOW_MEM] = ratio;
      ratio += 0.1;
      RATIOS[NORM_MEM] = ratio;
      ratio += 0.1;
      RATIOS[GOOD_MEM] = ratio;
   }

   private long cts = System.currentTimeMillis();
   private long scount = 0L;
   private volatile int cachedState = GOOD_MEM;
   private volatile long stateTS = 0;
   // cache of getMemoryStateExcludingEden(), separate from the state everything else reads
   private volatile int edenCachedState = GOOD_MEM;
   private volatile long edenStateTS = 0;

   private boolean stopped = false;
   private XSwapperThread[] threads = null;
   private final AtomicInteger criticalNoSwap = new AtomicInteger(0);
   private volatile long maxCriticalWait = -1L;
   private volatile long gcMinInterval = -1L;
   private volatile boolean dcmdUnavailable = false;
   private volatile boolean lastGCDiagnostic = false;
   private final AtomicLong lastGC = new AtomicLong(0L);
   private final AtomicLong gcBackoff = new AtomicLong(0L);
   private final AtomicLong lastGCPause = new AtomicLong(0L);
   private final AtomicBoolean gcNotRunWarned = new AtomicBoolean(false);
   private final AtomicBoolean gcFreedLittleLogged = new AtomicBoolean(false);
   // clock for the garbage collection throttle, replaced by tests
   volatile LongSupplier clock = System::currentTimeMillis;
   private final AtomicBoolean gcIntervalWarned = new AtomicBoolean(false);
   private int circle = 0;
   private int tcount = 0;
   private final long seed = Math.abs(System.currentTimeMillis());
   private final MonitorMulticaster monitor = new MonitorMulticaster();
   private final AtomicLong waitingThreadCount = new AtomicLong(0L);
   private final AtomicLong criticalWaitCount = new AtomicLong(0L);

   private final Lock waitLock = new ReentrantLock();
   private final Condition waitCondition = waitLock.newCondition();
   private final Object swapLock = "swapLock";

   private final AtomicLong counter = new AtomicLong(0);
   private final ThreadLocal<Boolean> swapping = ThreadLocal.withInitial(() -> false);

   private static final Logger DEBUG_LOG = LoggerFactory.getLogger("inetsoft.swap_data");

   /**
    * Looks up the HotSpot diagnostic MXBean. This is a separate class, and not a lambda in
    * XSwapper, so that no method declared by XSwapper has com.sun.management in its
    * signature: Spring and Mockito reflect on XSwapper's declared methods, which fails if a
    * signature names a class that the runtime doesn't have (no jdk.management module).
    */
   private static final class DiagnosticBean {
      static HotSpotDiagnosticMXBean get() {
         return ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);
      }
   }

   private static final class MonitorMulticaster implements XSwappableMonitor {
      @Override
      public void countHits(int type, int hits) {
         monitors.stream()
            .filter(m -> m.isLevelQualified(HITS))
            .forEach(m -> m.countHits(type, hits));
      }

      @Override
      public void countMisses(int type, int misses) {
         monitors.stream()
            .filter(m -> m.isLevelQualified(HITS))
            .forEach(m -> m.countHits(type, misses));
      }

      @Override
      public void countRead(long num, int type) {
         monitors.stream()
            .filter(m -> m.isLevelQualified(READ))
            .forEach(m -> m.countRead(num, type));
      }

      @Override
      public void countWrite(long num, int type) {
         monitors.stream()
            .filter(m -> m.isLevelQualified(READ))
            .forEach(m -> m.countWrite(num, type));
      }

      @Override
      public boolean isLevelQualified(String attr) {
         return monitors.stream().anyMatch(m -> m.isLevelQualified(attr));
      }

      void addMonitor(XSwappableMonitor monitor) {
         monitors.add(monitor);
      }

      void removeMonitor(XSwappableMonitor monitor) {
         monitors.remove(monitor);
      }

      private final List<XSwappableMonitor> monitors = new CopyOnWriteArrayList<>();
   }

   public static final String SWAP_FILE_MAP = "inetsoft.swap.file.map";
   public static final String SWAP_FILE_MAP_LOCK = "inetsoft.swap.file.map.lock";

   public static final class XSwappableReference extends Cleaner.Reference<XSwappable> {
      public XSwappableReference(XSwappable referent, File[] files) {
         super(referent);
         this.files = Arrays.stream(files).map(File::getAbsolutePath).toArray(String[]::new);
         boolean tracked = false;

         try {
            Cluster cluster = Cluster.getInstance();
            Map<String, Integer> map = cluster.getMap(SWAP_FILE_MAP);

            for(String file : this.files) {
               int count = map.getOrDefault(file, 0) + 1;
               map.put(file, count);
            }

            tracked = true;
         }
         catch(Exception e) {
            LOG.debug("Unable to register swap files in cluster map, cluster may be stopped", e);
         }

         this.clusterTracked = tracked;
      }

      @Override
      public void close() throws Exception {
         if(!clusterTracked) {
            // Cluster was unavailable when this reference was created; delete files directly.
            // Swap files are node-local, so no cluster coordination is needed.
            for(String file : files) {
               boolean result = new File(file).delete();

               if(!result) {
                  FileSystemService.getInstance().remove(new File(file), 30000);
               }
            }

            return;
         }

         Cluster cluster = Cluster.getInstance();
         Lock lock = cluster.getLock(SWAP_FILE_MAP_LOCK);
         lock.lock();

         try {
            Map<String, Integer> map = cluster.getMap(SWAP_FILE_MAP);

            for(String file : files) {
               int count = map.getOrDefault(file, 1) - 1;

               if(count == 0) {
                  map.remove(file);
                  boolean result = new File(file).delete();

                  if(!result) {
                     FileSystemService.getInstance().remove(new File(file), 30000);
                  }
               }
               else {
                  map.put(file, count);
               }
            }
         }
         finally {
            lock.unlock();
         }
      }

      private final String[] files;
      private final boolean clusterTracked;
   }
}
