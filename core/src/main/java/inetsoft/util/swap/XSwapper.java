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

import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * XSwapper swapps the swappables.
 *
 * @version 9.1
 * @author InetSoft Technology Corp
 */
public final class XSwapper {
   /**
    * Timestamp being updated by swapper.
    */
   public static long cur = System.currentTimeMillis();
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
   public static boolean isExecuting() {
      long cur = System.currentTimeMillis();
      // if no swappable object is created within 5 seconds, system is idle
      return cur - cts < 5000;
   }

   /**
    * Register a swappable.
    * @param swappable the specified swappable.
    */
   public static void register(XSwappable swappable) {
      getSwapper().register0(swappable);

      // update the moment when swappable object is being created
      cts = System.currentTimeMillis();
   }

   /**
    * Deregister a swappable.
    * @param swappable the specified swappable.
    */
   public static void deregister(XSwappable swappable) {
      getSwapper().deregister0(swappable);
   }

   /**
    * Register a XSwappableMonitor.
    * @param monitor a XSwappableMonitor.
    */
   public static void registerMonitor(XSwappableMonitor monitor) {
      XSwapper.getSwapper().monitor.addMonitor(monitor);
   }

   /**
    * Deregister a XSwappableMonitor.
    */
   public static void deregisterMonitor(XSwappableMonitor monitor) {
      XSwapper.getSwapper().monitor.removeMonitor(monitor);
   }

   /**
    * Get the XSwappableMonitor.
    */
   public static XSwappableMonitor getMonitor() {
      return XSwapper.getSwapper().monitor;
   }

   /**
    * Get the total swap count.
    */
   public static long getSwapCount() {
      return scount;
   }

   /**
    * Get the next prefix.
    * @return the next prefix.
    */
   public static String getPrefix() {
      return getSwapper().getPrefix0();
   }

   /**
    * Get the free memory ratio.
    * @return the free memory ratio.
    */
   private static double getFreeRatio() {
      long max = Runtime.getRuntime().maxMemory();
      return ((double) getFreeSpace()) / max;
   }

   /**
    * Get the free memory.
    * @return the free memory.
    */
   private static long getFreeSpace() {
      long max = Runtime.getRuntime().maxMemory();
      long total = Runtime.getRuntime().totalMemory();
      long free = Runtime.getRuntime().freeMemory();

      return free + max - total;
   }

   /**
    * Get memory state at no more than a certain interval.
    */
   public static int getMemoryState() {
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
   private static int getMemoryState0() {
      double ratio = getFreeRatio();

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
    * Stop the swapper.
    */
   public static void stop() {
      getSwapper().stop0();
   }

   /**
    * Start the swapper.
    */
   public static void start() {
      getSwapper().start0();
   }

   /**
    * Get the swapper.
    */
   public static XSwapper getSwapper() {
      if(swapper == null) {
         synchronized(XSwapper.class) {
            if(swapper == null) {
               swapper = new XSwapper();
            }
         }
      }

      return swapper;
   }

   /**
    * Get all the swappables in XSwapper.
    * Notice:The returned swappables MUST be cleared by garbage collector after
    * they are used. They should not be kept by other objects permanently.
    * @return the swappables.
    */
   public static XSwappable[] getAllSwappables() {
      XSwapper swapper = getSwapper();
      Vector<XSwappable> results = new Vector<>();

      if(swapper.stopped) {
         return new XSwappable[] {};
      }

      for(XSwapperThread0 thread : swapper.threads) {
         if(thread.isCancelled()) {
            continue;
         }

         XWeakList list = null;

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
   private XSwapper() {
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
         (new Thread() {
            @Override
            public void run() {
               Cluster cluster = Cluster.getInstance();
               Lock lock = cluster.getLock(SWAP_FILE_MAP_LOCK);
               lock.lock();

               try {
                  Map<String, Integer> map = cluster.getMap(SWAP_FILE_MAP);
                  File[] files = file.listFiles();

                  for(int i = 0; files != null && i < files.length; i++) {
                     if(!files[i].isDirectory() && files[i].getName().endsWith(".tdat") &&
                        !map.containsKey(files[i].getAbsolutePath()))
                     {
                        files[i].delete();
                     }
                  }
               }
               finally {
                  lock.unlock();
               }
            }
         }).start();
      }

      threads = new XSwapperThread0[getThreadCount()];

      for(int i = 0; i < threads.length; i++) {
         threads[i] = new XSwapperThread0();
         threads[i].start();
      }
   }

   /**
    * Gets the number of threads that are waiting for the objects to be swapped out.
    *
    * @return the thread count.
    */
   public static long getWaitingThreadCount() {
      return XSwapper.getSwapper().waitingThreadCount.get();
   }

   /**
    * Wait for swapper to swap out objects if memory is low. This should
    * be called when there is a surge of memory request to avoid running
    * out of memory before swapping could be performed.
    */
   public void waitForMemory() {
      waitingThreadCount.incrementAndGet();

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
            while(getMemoryState() == CRITICAL_MEM && criticalNoSwap < 3) {
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
                  Thread.sleep(200);
               }
               catch(InterruptedException exc) {
               }

               if(deadlock) {
                  doGC();
               }
            }
         }
         finally {
            if(criticalNoSwap > 0) {
               doGC();
               criticalNoSwap = 0;
            }

            if(!deadlock) {
               waitLock.unlock();
            }
         }
      }
      finally {
         waitingThreadCount.decrementAndGet();
      }
   }

   /**
    * Perform GC and clear cached state.
    */
   private void doGC() {
      System.gc();
      stateTS = 0;
   }

   /**
    * Stop the swapper.
    */
   private void stop0() {
      stopped = true;
   }

   /**
    * Start the swapper.
    */
   private void start0() {
      stopped = false;
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
   private final String getPrefix0() {
      return new StringBuilder("s").append(seed).append("_").append(counter.incrementAndGet())
         .toString();
   }

   /**
    * Register a swappable.
    * @param swappable the specified swappable.
    */
   private void register0(XSwappable swappable) {
      if(stopped) {
         return;
      }

      int circle = this.circle % getThreadCount();
      threads[circle].register(swappable);
      this.circle++;
   }

   /**
    * Deregister a swappable.
    * @param swappable the specified swappable.
    */
   private void deregister0(XSwappable swappable) {
      if(stopped) {
         return;
      }

      for(int i = 0; i < threads.length; i++) {
         if(threads[i].deregister(swappable)) {
            break;
         }
      }
   }

   /**
    * Base class for swapper threads.
    */
   private abstract class XSwapperThread extends GroupedThread {
      public abstract void swapRemaining();
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
   private final class XSwapperThread0 extends XSwapperThread {
      public XSwapperThread0() {
         super();
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
         int state = GOOD_MEM;
         long waitTime = 0;
         swapping.set(true);

         outer:
         while(!isCancelled()) {
            cur = System.currentTimeMillis();

            if(stopped || list.size() == 0) {
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
               swapIdx = -1;
               swapMax = max;

               swapRemaining();

               swapIdx = -1;
               swaplist.clear();

               if(swapCnt == 0 && state == CRITICAL_MEM) {
                  criticalNoSwap++;
               }

               // get an acurate memory state after swapping
               if(swapCnt > 0 && state <= BAD_MEM) {
                  doGC();
               }
            }
            catch(Throwable ex) {
               LOG.error(
                           "Failed to swap objects out of memory", ex);
            }

            switch(state = getMemoryState()) {
            case GOOD_MEM:
               waitTime = 5000;
               break;
            case NORM_MEM:
               waitTime = 3000;
               break;
            case LOW_MEM:
               waitTime = 2000;
               break;
            case BAD_MEM:
               waitTime = 1000;
               break;
            default: // critical
               waitTime = 500;
               break;
            }

	    setPriority(NORM_PRIORITY);
         }
      }

      /**
       * Swap the swappables in swaplist.
       */
      @Override
      public void swapRemaining() {
         List<XSwappable> swapped = new ArrayList<>();

         for(swapIdx++; swapIdx < swaplist.size; swapIdx++) {
            XSwappable swappable = (XSwappable) swaplist.arr[swapIdx];

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
            XSwapper.scount++;
            swapCnt++;

            if(swapCnt == swapMax) {
               break;
            }
         }

         Cluster cluster = Cluster.getInstance();
         // optimization, lock the map and bulk add files to cleaner instead
         // of doing so individually.
         Lock lock = cluster.getLock(SWAP_FILE_MAP_LOCK);
         lock.lock();

         try {
            // if there are any swap files then add them to the cleaner so that
            // the files can be removed once they are no longer referenced
            for(int i = 0; i < swapped.size(); i++) {
               XSwappable swappable = swapped.get(i);
               File[] swapFiles = swappable.getSwapFiles();

               if(swapFiles != null && swapFiles.length > 0) {
                  Cleaner.add(new XSwappableReference(swappable, swapFiles));
               }
            }
         }
         finally {
            lock.unlock();
         }
      }

      private long lcheck = XSwapper.cur;
      private final XWeakList list = new XWeakList();

      private XObjectList swaplist = new XObjectList();
      private int swapIdx; // the current swappable being swapped
      private int swapMax; // max items to swap
      private int swapCnt; // swapped out count
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(XSwapper.class);

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
      String str = SreeEnv.getProperty("swapper.free.ratio");
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

   private static long cts = System.currentTimeMillis();
   private static long scount = 0L;
   private static int cachedState = GOOD_MEM;
   private static volatile long stateTS = 0;
   private static XSwapper swapper;

   private boolean stopped = false;
   private XSwapperThread0[] threads = null;
   private int criticalNoSwap = 0;
   private int circle = 0;
   private int tcount = 0;
   private final long seed = Math.abs(System.currentTimeMillis());
   private final MonitorMulticaster monitor = new MonitorMulticaster();
   private final AtomicLong waitingThreadCount = new AtomicLong(0L);

   private final Lock waitLock = new ReentrantLock();
   private final Object swapLock = "swapLock";

   private final static AtomicLong counter = new AtomicLong(0);
   private final static ThreadLocal<Boolean> swapping = ThreadLocal.withInitial(() -> false);

   private static final Logger DEBUG_LOG = LoggerFactory.getLogger("inetsoft.swap_data");

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
      XSwappableReference(XSwappable referent, File[] files) {
         super(referent);
         Cluster cluster = Cluster.getInstance();
         Map<String, Integer> map = cluster.getMap(SWAP_FILE_MAP);
         this.files = Arrays.stream(files).map(File::getAbsolutePath).toArray(String[]::new);

         for(String file : this.files) {
            int count = map.getOrDefault(file, 0) + 1;
            map.put(file, count);
         }
      }

      @Override
      public void close() throws Exception {
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
   }
}
