package inetsoft.uql.table;

import inetsoft.util.GroupedThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A cache implementation that stores XSwappableTable instances using weak references.
 * Automatically removes entries when the referenced objects are garbage collected.
 */
public class SnapshotEmbeddedTableDataCache extends GroupedThread {
   // Private constructor to prevent instantiation
   private SnapshotEmbeddedTableDataCache() {
   }

   @Override
   protected void doRun() {
      while(!isCancelled()) {
         cleanUp();

         try {
            Thread.sleep(60 * 1000);
         }
         catch(InterruptedException e) {
            LOG.warn("Error cleaning up snapshot cache", e);
         }
      }
   }

   /**
    * Stores data in the cache with the specified key.
    *
    * @param cacheKey The key to associate with the data (non-null)
    * @param data     The data to be cached (non-null)
    *
    * @throws NullPointerException if either cacheKey or data is null
    */
   public void set(String cacheKey, XSwappableTable data) {
      Objects.requireNonNull(cacheKey, "Cache key cannot be null");
      Objects.requireNonNull(data, "Data cannot be null");

      CACHE.put(cacheKey, new WeakReference<>(data));
   }

   /**
    * Retrieves data from the cache.
    *
    * @param cacheKey The key of the data to retrieve
    *
    * @return The cached data, or null if not found or already garbage collected
    */
   public XSwappableTable get(String cacheKey) {
      if(cacheKey == null) {
         return null;
      }

      WeakReference<XSwappableTable> ref = CACHE.get(cacheKey);

      if(ref == null) {
         return null;
      }

      XSwappableTable data = ref.get();

      if(data == null) {
         CACHE.remove(cacheKey); // Auto-cleanup of garbage collected entries
         CACHE_LOCK.remove(cacheKey);
      }

      return data;
   }

   /**
    * Removes the entry associated with the specified key from the cache.
    *
    * @param cacheKey The key of the entry to remove
    */
   public void remove(String cacheKey) {
      if(cacheKey != null) {
         CACHE.remove(cacheKey);
         CACHE_LOCK.remove(cacheKey);
      }
   }

   /**
    * Cleans up all entries where the referenced objects have been garbage collected.
    */
   public void cleanUp() {
      CACHE.entrySet().removeIf(entry -> {
         WeakReference<XSwappableTable> ref = entry.getValue();
         boolean remove = ref == null || ref.get() == null;

         if(remove) {
            CACHE_LOCK.remove(entry.getKey());
         }

         return remove;
      });
   }

   /**
    * Gets the total number of entries in the cache (including garbage collected ones).
    *
    * @return The total cache size
    */
   public int size() {
      return CACHE.size();
   }

   /**
    * Gets the number of active (non-garbage collected) entries in the cache.
    *
    * @return The count of valid cache entries
    */
   public int activeSize() {
      return (int) CACHE.values().stream()
         .filter(ref -> ref != null && ref.get() != null)
         .count();
   }

   /**
    * Clears all entries from the cache.
    */
   public void clear() {
      CACHE.clear();
      CACHE_LOCK.clear();
   }

   /**
    * Get lock for the cache by cache key.
    */
   public ReentrantLock getLock(String cacheKey) {
      return CACHE_LOCK.computeIfAbsent(cacheKey, k -> new ReentrantLock());
   }

   public static SnapshotEmbeddedTableDataCache getInstance() {
      LOCK.lock();

      try {
         if(INSTANCE == null) {
            INSTANCE = new SnapshotEmbeddedTableDataCache();
            INSTANCE.start();
         }
      }
      finally {
         LOCK.unlock();
      }

      return INSTANCE;
   }

   private static SnapshotEmbeddedTableDataCache INSTANCE;
   private static final Lock LOCK = new ReentrantLock();
   private static final Map<String, WeakReference<XSwappableTable>> CACHE = new ConcurrentHashMap<>();
   private static final Map<String, ReentrantLock> CACHE_LOCK = new ConcurrentHashMap<>();
   private static final Logger LOG = LoggerFactory.getLogger(SnapshotEmbeddedTableDataCache.class);
}