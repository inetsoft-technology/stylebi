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
package inetsoft.sree.internal.cluster;

import inetsoft.util.GroupedThread;
import org.apache.ignite.internal.cluster.ClusterTopologyCheckedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * {@code ClusterCache} is the base implementation for distributed caches.
 *
 * @param <E> the notification event type.
 * @param <L> the load data type.
 * @param <S> the save data type.
 */
public abstract class ClusterCache<E, L extends Serializable, S extends  Serializable>
   implements AutoCloseable
{
   /**
    * Creates a new instance of {@code ClusterCache}. This uses a default load debounce interval
    * of 500 ms, a default save bounce interval of 500 ms, no periodic flushing of the cache, and
    * initially loads the cache data in the foreground.
    *
    * @param prefix   the map name prefix.
    * @param mapNames the names of the cache data maps.
    */
   protected ClusterCache(String prefix, String ... mapNames) {
      this(false, 500L, TimeUnit.MILLISECONDS, 500L, TimeUnit.MILLISECONDS,
           -1L, TimeUnit.MILLISECONDS, prefix, mapNames);
   }

   /**
    * Creates a new instance of {@code ClusterCache}.
    *
    * @param backgroundInitialize {@code true} to perform the initial data load asynchronously.
    * @param loadInterval         the debounce interval for load tasks.
    * @param loadIntervalUnit     the time unit for the load interval.
    * @param saveInterval         the debounce interval for save tasks.
    * @param saveIntervalUnit     the time unit for the save interval.
    * @param flushInterval        the interval for automatically flushing the cache data. If this
    *                             value is {@code -1}, automatic flushing is disabled.
    * @param flushIntervalUnit    the time unit for the flush interval.
    * @param prefix               the map name prefix.
    * @param mapNames             the names of the cache data maps.
    */
   protected ClusterCache(boolean backgroundInitialize,
                          long loadInterval, TimeUnit loadIntervalUnit,
                          long saveInterval, TimeUnit saveIntervalUnit,
                          long flushInterval, TimeUnit flushIntervalUnit,
                          String prefix, String ... mapNames)
   {
      this.cluster = Cluster.getInstance();
      this.backgroundInitialize = backgroundInitialize;
      this.maps = new HashMap<>();
      this.loadInterval = loadInterval;
      this.loadIntervalUnit = loadIntervalUnit;
      this.saveInterval = saveInterval;
      this.saveIntervalUnit = saveIntervalUnit;
      this.flushInterval = flushInterval;
      this.flushIntervalUnit = flushIntervalUnit;
      this.prefix = prefix == null ? getClass().getName() + "." : prefix;
      this.lock = cluster.getLock(this.prefix + "lock");

      lock.lock();

      try {
         this.timestamp = cluster.getLong(this.prefix + "timestamp");
         this.lastLoad = cluster.getLong(this.prefix + "lastLoad");
         this.loadingCounter = cluster.getLong(this.prefix + "loadingCounter");
         this.savingCounter = cluster.getLong(this.prefix + "savingCounter");
         this.referenceCounter = cluster.getLong(this.prefix + "referenceCounter");
         this.referenceCounter.incrementAndGet();
         this.taskLock = cluster.getLock(this.prefix + "taskLock");
         this.lastTask = cluster.getReference(this.prefix + "lastTask");

         for(String mapName : mapNames) {
            final String id = this.prefix + mapName;
            maps.put(mapName, new LocalClusterMap<>(id, cluster, cluster.getMap(id)));
         }
      }
      finally {
         lock.unlock();
      }
   }

   @Override
   public final void close() throws Exception {
      String prefix = getPrefix();
      boolean lastInstance = false;

      if(lock.tryLock(10L, TimeUnit.SECONDS)) {
         try {
            lastInstance = referenceCounter.decrementAndGet() == 0;
         }
         finally {
            lock.unlock();
         }
      }

      if(lastInstance) {
         processTask(true);
      }

      closed = true;

      if(taskThread != null) {
         taskThread.cancel();
      }

      if(flushExecutor != null) {
         flushExecutor.shutdownNow();

         try {
            flushExecutor.awaitTermination(2L, TimeUnit.SECONDS);
         }
         catch(InterruptedException ignore) {
         }
      }

      for(LocalClusterMap<?, ?> map : maps.values()) {
         map.close();
      }

      if(lock.tryLock(10L, TimeUnit.SECONDS)) {
         try {
            lastInstance = referenceCounter.get() == 0;

            if(lastInstance) {
               cluster.destroyLong(prefix + "timestamp");
               cluster.destroyLong(prefix + "lastLoad");
               cluster.destroyLong(prefix + "loadingCounter");
               cluster.destroyLong(prefix + "savingCounter");
               cluster.destroyLong(prefix + "referenceCounter");
               cluster.destroyReference(prefix + "lastTask");

               for(String mapName : maps.keySet()) {
                  cluster.destroyMap(prefix + mapName);
               }
            }
         }
         finally {
            if(lastInstance) {
               destroyLock(lock, prefix + "lock");
            }
            else {
               lock.unlock();
            }
         }
      }

      close(lastInstance);
   }

   private void destroyLock(Lock lock, String lockName) {
      int tryCount = 10;

      for(int i = 0; i < tryCount; i++) {
         lock.lock();

         try {
            cluster.destroyLock(lockName);
            LOG.debug("destroyed lock: {}", lockName);
            return;
         }
         catch(Exception e) {
            LOG.debug("Failed to destroy lock: {} lock", lockName, e);
         }
         finally {
            lock.unlock();
         }

         try {
            Thread.sleep(5_000);
         }
         catch(InterruptedException ignore) {
         }
      }

      LOG.error("Failed to destroy lock: {}, after {} attempts", lockName, tryCount);

      if(lock instanceof ReentrantLock reentrantLock) {
         LOG.debug("Lock {} held by any thread: {}", lockName, reentrantLock.isLocked());
         LOG.debug("Lock {} hold count: {}", lockName, reentrantLock.getHoldCount());
         LOG.debug("{} Threads waiting for lock: {}", reentrantLock.getQueueLength(), lockName);
      }
   }

   /**
    * Loads the cache data if it has not already been initialized. If the
    * <i>backgroundInitialize</i> argument was set in the constructor, the loading will be done
    * asynchronously. Otherwise, this method will block until the data is loaded.
    */
   @SuppressWarnings("unchecked")
   public void initialize() {
      long now = System.currentTimeMillis();

      if(!closed && (now > validTS || taskThread == null || flushInterval > 0L && flushExecutor == null)) {
         lock.lock();

         if(closed) {
            lock.unlock();
            return;
         }

         // getCache() can be called many many times in short succession. since we only need
         // to clear cache (for authentication) when configuration is changed, allowing a
         // slightly stale cache to be used would only introduce a slight delay in applying
         // configuration changes and avoid substantial delay (due to the expensive distributed
         // atomic or locking). (45245)
         validTS = now + 2000;

         try {
            if(timestamp.get() == 0L) {
               if(backgroundInitialize) {
                  if(!isLoading()) {
                     load();
                  }
               }
               else {
                  loadingCounter.incrementAndGet();

                  try {
                     Map<String, Map> data = doLoad(true, getLoadData(null));

                     for(Map.Entry<String, LocalClusterMap<?, ?>> e : maps.entrySet()) {
                        e.getValue().clear();

                        if(data.containsKey(e.getKey())) {
                           Set<Map.Entry> entrySet = data.get(e.getKey()).entrySet();
                           Map map = entrySet.stream()
                              .filter(entry -> entry.getKey() != null)
                              .filter(entry -> entry.getValue() != null)
                              .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                                                        (o1, o2) -> o2,
                                                        TreeMap::new));
                           e.getValue().putAll(map);
                        }
                     }

                     timestamp.set(now);
                     lastLoad.set(now);
                  }
                  finally {
                     loadingCounter.decrementAndGet();
                  }
               }
            }

            if(taskThread == null) {
               this.taskThread = new GroupedThread(this::processTasks);
               this.taskThread.setDaemon(true);
               this.taskThread.start();
            }

            if(flushInterval > 0L && flushExecutor == null) {
               flushExecutor = Executors.newSingleThreadScheduledExecutor();
               flushExecutor.scheduleWithFixedDelay(
                  this::load, flushInterval, flushInterval, flushIntervalUnit);
            }

         }
         finally {
            lock.unlock();
         }
      }
   }

   /**
    * Loads the cache data in the background.
    */
   public void load() {
      submitLoadTask(null);
   }

   /**
    * Notifies the cache that the underlying resource has changed. The changes will be loaded
    * asynchronously.
    *
    * @param event the change event.
    */
   public void notify(E event) {
      submitLoadTask(event);
   }

   /**
    * Notifies the cache that the underlying resource has changed if the event timestamp is after
    * the current timestamp of this cache. The changes will be loaded asynchronously.
    *
    * @param event          the change event.
    * @param eventTimestamp the timestamp of the event.
    */
   public void notify(E event, long eventTimestamp) {
      // check the condition outside the lock to reduce lock contention
      if(!closed && eventTimestamp > timestamp.get()) {
         lock.lock();

         try {
            // check the condition again inside the lock
            if(eventTimestamp > timestamp.get()) {
               notify(event);
            }
         }
         finally {
            lock.unlock();
         }
      }
   }

   /**
    * Modifies the cache data. Changes will automatically be propagated to the underlying resource
    * by calling {@link #doSave(Serializable)}, if supported.
    *
    * @param changes a {@code Runnable} that applies the changes.
    */
   @SuppressWarnings("unchecked")
   public void modify(Runnable changes) {
      if(closed) {
         return;
      }

      lock.lock();

      try {
         if(lastTask.get() != null && lastTask.get().isLoading()) {
            // run pending load task immediately before applying changes
            try {
               processTask(false);
            }
            catch(ClusterTopologyCheckedException clusterException) {
               LOG.warn("Failed to acquire lock due to cluster topology change");
            }
         }

         changes.run();
         S data = getSaveData();

         if(lastTask.get() != null && !lastTask.get().isLoading()) {
            CacheTask<S> task = lastTask.get();
            S newData = reduceSaveData(task.getData(), data);
            task.defer(newData, saveInterval, saveIntervalUnit, timestamp.get());
            lastTask.set(task);
         }
         else {
            savingCounter.incrementAndGet();
            CacheTask<S> task =
               new CacheTask<>(false, data, saveInterval, saveIntervalUnit, timestamp.get());
            lastTask.set(task);
         }
      }
      finally {
         lock.unlock();
      }
   }

   /**
    * Determines if the cache data has been initialized.
    *
    * @return {@code true} if initialized; {@code false} otherwise.
    */
   public boolean isInitialized() {
      return timestamp.get() > 0L;
   }

   /**
    * Determines if the cache data is currently being loaded or if an asynchronous load task is
    * pending.
    *
    * @return {@code true} if loading; {@code false} otherwise.
    */
   public boolean isLoading() {
      return loadingCounter.get() > 0L;
   }

   /**
    * Gets the age of the cache data.
    *
    * @return the age in milliseconds.
    */
   public long getAge() {
      long time = lastLoad.get();
      return time == 0L ? 0L : System.currentTimeMillis() - time;
   }

   /**
    * Determines if the cache data is currently being saved or if an asynchronous save task is
    * pending.
    *
    * @return {@code true} if saving; {@code false} otherwise.
    */
   public boolean isSaving() {
      return savingCounter.get() > 0L;
   }

   /**
    * Resets the cache to an uninitialized state.
    *
    * @see #initialize()
    */
   public void reset() {
      if(closed) {
         return;
      }

      lock.lock();

      try {
         for(Map map : maps.values()) {
            map.clear();
         }

         timestamp.set(0);
      }
      finally {
         lock.unlock();
      }
   }

   /**
    * Gets the value associated with a key in the specified map.
    *
    * @param map the name of the map.
    * @param key the key.
    *
    * @param <K> the key type.
    * @param <V> the value type.
    *
    * @return the mapped value.
    */
   @SuppressWarnings("unchecked")
   public <K extends Serializable, V extends Serializable> V get(String map, K key) {
      return (V) getMap(map).get(key);
   }

   /**
    * Sets the value associated with a key in the specified map.
    *
    * @param map   the name of the map.
    * @param key   the key.
    * @param value the value.
    *
    * @param <K> the key type.
    * @param <V> the value type.
    *
    * @return the previous value associated with the key, if any.
    */
   @SuppressWarnings("unchecked")
   public <K extends Serializable, V extends Serializable> V put(String map, K key, V value) {
      return (V) getMap(map).put(key, value);
   }

   /**
    * Removes a value association from the specified map.
    *
    * @param map the name of the map.
    * @param key the key.
    *
    * @param <V> the value type.
    *
    * @return the previous value associated with the key, if any.
    */
   @SuppressWarnings({ "unchecked", "SuspiciousMethodCalls" })
   public <V extends Serializable> V remove(String map, Object key) {
      return (V) getMap(map).remove(key);
   }

   /**
    * Determines if a value is associated with a key in the specified map.
    *
    * @param map the name of the map.
    * @param key the key.
    *
    * @return {@code true} if the key is mapped; {@code false} otherwise.
    */
   @SuppressWarnings("SuspiciousMethodCalls")
   public boolean containsKey(String map, Object key) {
      return getMap(map).containsKey(key);
   }

   /**
    * Check whether the cluster cache is active.
    * @return
    */
   public boolean isActive() {
      try {
         return referenceCounter.get() > 0;
      }
      catch(Exception ex) {
         return  false;
      }
   }

   /**
    * Gets the distributed map with the specified name.
    *
    * @param name the map name.
    *
    * @param <K> the key type.
    * @param <V> the value type.
    *
    * @return the named map.
    */
   @SuppressWarnings("unchecked")
   protected final <K extends Serializable, V extends Serializable> LocalClusterMap<K, V>
   getMap(String name)
   {
      return (LocalClusterMap<K, V>) maps.get(name);
   }

   /**
    * Loads the underling resource data. This method should be blocking and not consider if the
    * resource is up to date or locking - these are handled externally to this method.
    *
    * @param initializing {@code true} if this is the initial load of the data. Some implementations
    *                     may provide default values if the underlying resource is missing.
    * @param loadData     the data associated with the load event.
    *
    * @return the new data to be placed into the distributed maps.
    */
   protected abstract Map<String, Map> doLoad(boolean initializing, L loadData);

   /**
    * Saves the in-memory data to the underlying resource. The default implementation of this method
    * does nothing. This method should be blocking and not consider if the resource is up to date or
    * locking - these are handled externally to this method.
    *
    * @param saveData the data associated with the save event.
    */
   protected void doSave(S saveData) throws Exception {
   }

   /**
    * Gets the load data associated with an event. The event may be null if the load was invoked
    * directly via the {@link #load()} or {@link #initialize()} method.
    *
    * @param event the notification event.
    *
    * @return the load data.
    */
   protected abstract L getLoadData(E event);

   /**
    * Gets the save data to be associated with a save event. The default implementation returns
    * {@code null}.
    *
    * @return the save data.
    */
   protected S getSaveData() {
      return null;
   }

   /**
    * Reduces load data when a load event is debounced. This method handles combining the data from
    * a pending load event with that from a newly submitted load event. By default, the previous
    * value is replaced with the new value.
    *
    * @param previous the value from the pending load event or {@code null} if there is no pending
    *                 event.
    * @param current  the value from the newly submitted load event.
    *
    * @return the combined load data.
    */
   protected L reduceLoadData(L previous, L current) {
      return current;
   }

   /**
    * Reduces save data when a save event is debounced. This method handles combining the data from
    * a pending save event with that from a newly submitted save event. By default, the previous
    * value is replaced with the new value.
    *
    * @param previous the value from the pending save event or {@code null} if there is no pending
    *                 event.
    * @param current  the value from the newly submitted save event.
    *
    * @return the combined save data.
    */
   protected S reduceSaveData(S previous, S current) {
      return current;
   }

   /**
    * Gets the prefix for the names of the distributed objects used by this cluster. By default, the
    * fully-qualified name of this class followed by a dot, '.', is returned.
    *
    * @return the name prefix.
    */
   protected final String getPrefix() {
      return prefix;
   }

   /**
    * Cleans up any resources held by this cache. If <i>lastInstance</i> is {@code true}, then this
    * method should also clean up any distributed resources held by this cache.
    *
    * @param lastInstance {@code true} if this was the last instance of this cache in the cluster.
    *
    * @throws Exception if an unexpected error occurs.
    */
   protected void close(boolean lastInstance) throws Exception {
   }

   /**
    * Gets the cache timestamp.
    *
    * @return the timestamp.
    */
   protected DistributedLong getTimestamp() {
      return timestamp;
   }

   /**
    * Gets the {@link Cluster} instance that this cache is bound to.
    *
    * @return the cluster.
    */
   protected Cluster getCluster() {
      return cluster;
   }

   @SuppressWarnings("unchecked")
   private void submitLoadTask(E event) {
      if(closed) {
         return;
      }

      lock.lock();

      try {
         if(closed) {
            lock.unlock();
            return;
         }

         L data = getLoadData(event);

         if(lastTask.get() != null && lastTask.get().isLoading()) {
            CacheTask<L> task = lastTask.get();
            L newData = reduceLoadData(task.getData(), data);
            task.defer(newData, loadInterval, loadIntervalUnit, timestamp.get());
            lastTask.set(task);
         }
         else {
            if(lastTask.get() != null) {
               // run pending save immediately
               try {
                  processTask(false);
               }
               catch(ClusterTopologyCheckedException clusterException) {
                  LOG.warn("Failed to acquire lock due to cluster topology change");
               }            }

            long interval = loadInterval;

            if(event == null && timestamp.get() == 0L) {
               // initializing, start immediately
               interval = 0L;
            }

            loadingCounter.incrementAndGet();
            CacheTask<L> task =
               new CacheTask<>(true, data, interval, loadIntervalUnit, timestamp.get());
            lastTask.set(task);
         }
      }
      finally {
         lock.unlock();
      }
   }

   private void processTasks() {
      try {
         while(!taskThread.isCancelled() && !closed) {
            taskLock.lock();
            long wait;

            try {
               wait = processTask(false);
            }
            catch(ClusterTopologyCheckedException clusterException) {
               LOG.warn("Failed to acquire lock due to cluster topology change. Retrying...");

               try {
                  Thread.sleep(1000);
               }
               catch(InterruptedException ie) {
                  Thread.currentThread().interrupt();
                  LOG.error("Interrupted while waiting after a topology change.", ie);
               }

               continue;
            }
            finally {
               taskLock.unlock();
            }

            if(wait > 0L) {
               Thread.sleep(wait);
            }
         }
      }
      catch(InterruptedException e) {
         if(!taskThread.isCancelled()) {
            LOG.error("Cache task thread has been interrupted", e);
         }
      }
   }

   @SuppressWarnings("unchecked")
   private long processTask(boolean flush) throws ClusterTopologyCheckedException {
      if(closed) {
         return 0L;
      }

      CacheTask<?> task = lastTask.get();
      long defaultWait = Math.min(
         TimeUnit.MILLISECONDS.convert(loadInterval, loadIntervalUnit),
         TimeUnit.MILLISECONDS.convert(saveInterval, saveIntervalUnit));

      if(task == null) {
         return defaultWait;
      }

      lock.lock();

       try {
          if(closed) {
             return 0L;
          }

          if(!flush) {
            long wait = task.getDelay(TimeUnit.MILLISECONDS);

            if(wait > 0) {
               return wait;
            }
         }

         lastTask.set(null);
      }
      finally {
         lock.unlock();
      }

      if(closed) {
         return 0L;
      }

      if(task.isLoading()) {
         try {
            Map<String, Map> data = doLoad(timestamp.get() == 0L, (L) task.getData());
            lock.lock();

            try {
               if(closed) {
                  return 0L;
               }

               if(!flush && !task.isCurrent(timestamp.get())) {
                  // a modification has been made while the data was loading, discard it
                  return defaultWait;
               }

               for(Map.Entry<String, LocalClusterMap<?, ?>> e : maps.entrySet()) {
                  e.getValue().clear();

                  if(data.containsKey(e.getKey())) {
                     e.getValue().putAll(data.get(e.getKey()));
                  }
               }

               timestamp.set(System.currentTimeMillis());
               lastLoad.set(System.currentTimeMillis());
            }
            finally {
               lock.unlock();
            }
         }
         finally {
            loadingCounter.decrementAndGet();
         }
      }
      else {
         lock.lock();

         try {
            if(closed) {
               return 0L;
            }

            try {
               doSave((S) task.getData());
            }
            catch(Exception ex) {
               LOG.warn("Failed to save data", ex);
            }

            timestamp.set(System.currentTimeMillis());
         }
         finally {
            lock.unlock();
            savingCounter.decrementAndGet();
         }
      }

      return 0L;
   }

   private final Cluster cluster;
   private final boolean backgroundInitialize;
   private final long loadInterval;
   private final TimeUnit loadIntervalUnit;
   private final long saveInterval;
   private final TimeUnit saveIntervalUnit;
   private final long flushInterval;
   private final TimeUnit flushIntervalUnit;
   private final String prefix;
   private final Map<String, LocalClusterMap<?, ?>> maps;
   private final Lock lock;
   private final DistributedLong timestamp;
   private long validTS = 0;
   private final DistributedLong lastLoad;
   private final DistributedLong loadingCounter;
   private final DistributedLong savingCounter;
   private final DistributedLong referenceCounter;
   private final Lock taskLock;
   private final DistributedReference<CacheTask> lastTask;
   private GroupedThread taskThread;
   private ScheduledExecutorService flushExecutor;
   private boolean closed = false;

   private static final Logger LOG = LoggerFactory.getLogger(ClusterCache.class);

   /**
    * A debounced load or save task.
    *
    * @param <T> the type of event data.
    */
   public static final class CacheTask<T extends Serializable> implements Delayed, Serializable {
      /**
       * Creates a new instance of {@code CacheTask}.
       *
       * @param loading          {@code true} to load the cache data or {@code false} to save it.
       * @param data             the load or save data.
       * @param interval         the debounce interval.
       * @param intervalUnit     the debounce interval time unit.
       * @param currentTimestamp the current timestamp of the cache at the time the task is created.
       */
      public CacheTask(boolean loading, T data, long interval, TimeUnit intervalUnit,
                       long currentTimestamp)
      {
         this.loading = loading;
         this.data = data;
         this.id = UUID.randomUUID().toString();
         setTime(interval, intervalUnit, currentTimestamp);
      }

      /**
       * Gets the flag that indicates if this is a load or save task.
       *
       * @return {@code true} to load the cache data or {@code false} to save it.
       */
      public boolean isLoading() {
         return loading;
      }

      /**
       * Gets the load or save data.
       *
       * @return the data.
       */
      public T getData() {
         return data;
      }

      /**
       * Gets the unique identifier for this task.
       *
       * @return the task identifier.
       */
      public String getId() {
         return id;
      }

      @Override
      public long getDelay(TimeUnit unit) {
         return unit.convert(time - System.nanoTime(), TimeUnit.NANOSECONDS);
      }

      @Override
      public int compareTo(Delayed o) {
         return Long.compare(getDelay(TimeUnit.NANOSECONDS), o.getDelay(TimeUnit.NANOSECONDS));
      }

      void defer(T data, long interval, TimeUnit intervalUnit, long currentTimestamp) {
         this.data = data;
         setTime(interval, intervalUnit, currentTimestamp);
      }

      boolean isCurrent(long timestamp) {
         return currentTimestamp <= timestamp;
      }

      private void setTime(long interval, TimeUnit intervalUnit, long currentTimestamp) {
         this.time = System.nanoTime() + TimeUnit.NANOSECONDS.convert(interval, intervalUnit);
         this.currentTimestamp = currentTimestamp;
      }

      @Override
      public String toString() {
         return "CacheTask{" +
            "id='" + id + '\'' +
            ", loading=" + loading +
            ", time=" + time +
            ", data=" + data +
            '}';
      }

      private final String id;
      private final boolean loading;
      private long time;
      private long currentTimestamp;
      private T data;
   }
}
