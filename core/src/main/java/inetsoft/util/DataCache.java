/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.util;

import inetsoft.util.swap.XSwapUtil;
import inetsoft.util.swap.XSwapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This class implements a cache with a size limit, and times out on a
 * user specified timeout period.
 *
 * @param <K> the type of the key.
 * @param <V> the type of the value.
 *
 * @version 6.1, 9/20/2004
 * @author InetSoft Technology Corp
 */
public class DataCache<K, V> {
   /**
    * Create a data cache with default limits.
    */
   public DataCache() {
      super();
      synchronized(CACHES) {
         CACHES.add(this);
      }
   }

   /**
    * Create a data cache.
    */
   public DataCache(int limit, long timeout) {
      this();
      this.limit = limit;
      this.timeout = timeout;
   }

   /**
    * Set the timeout period. If a data is not accessed within the time
    * period, it is eligible to be removed from the cache.
    */
   public void setTimeout(long time) {
      this.timeout = time;
   }

   /**
    * Get the timeout time period.
    */
   public long getTimeout() {
      return timeout;
   }

   /**
    * Set the maximum number of entries in the cache.
    */
   public void setLimit(int limit) {
      this.limit = limit;
   }

   /**
    * Get the maximum number of entries in the cache.
    */
   public int getLimit() {
      return limit;
   }

   /**
    * Set whether the cached objects should be forcefully cleaned up when
    * memory is low.
    */
   public void setSweepEnabled(boolean flag) {
      this.sweepEnabled = flag;
   }

   /**
    * Check whether the cached objects will be forcefully cleaned up when
    * membry is low.
    */
   public boolean isSweepEnabled() {
      return sweepEnabled;
   }

   /**
    * Remove all entries from the cache.
    */
   public synchronized void clear() {
      cachelist.clear();
      cachemap.clear();
   }

   /**
    * Check if a key contains.
    */
   public synchronized boolean containsKey(K key) {
      return cachemap.containsKey(key);
   }

   /**
    * Returns a set view of the keys contained in this map.
    */
   public synchronized Set<K> keySet() {
      // make a copy to avoid concurrent modification exception
      return new HashSet<>(cachemap.keySet());
   }

   /**
    * Add a data item to the cache.
    */
   public void put(K key, V data) {
      put(key, data, -1L);
   }

   /**
    * Add a data item to the cache.
    */
   public synchronized CacheEntry<K, V> put(K key, V data, long timeout) {
      CacheEntry<K, V> entry = new CacheEntry<>(key, data, timeout);
      CacheEntry<K, V> oentry = cachemap.get(key);

      if(oentry != null) {
         cachelist.remove(oentry);
      }

      cachelist.add(entry);
      cachemap.put(key, entry);

      if(cachelist.size() > limit * 1.2) {
         checkTimeout();
      }
      else {
         lastCheck -= 100; // speed up checkTimeout()
      }

      return entry;
   }

   /**
    * Remove a data item from the cache.
    */
   public synchronized V remove(K key) {
      CacheEntry<K, V> entry = cachemap.remove(key);

      if(entry != null) {
         cachelist.remove(entry);
      }

      return entry == null ? null : entry.data;
   }

   /**
    * Get a data item to the cache.
    */
   public V get(K key) {
      return get(key, -1L);
   }

   /**
    * Get a data item to the cache.
    * @param ts if > 0, the creation timestamp of data. data older than the timestamp is discarded.
    */
   public synchronized V get(K key, long ts) {
      CacheEntry<K, V> entry = cachemap.get(key);

      if(entry == null) {
         entry = promote(key, ts);

         if(entry != null) {
            cachemap.put(key, entry);
         }
      }

      if(entry != null) {
         // customer bug bug1309285993629
         if(isOutOfDate(entry)) {
            remove(key);
            return null;
         }

         // move newly accessed entry to the end
         cachelist.remove(entry);

         if(ts > 0 && entry.its < ts) {
            cachemap.remove(entry.key);
            return null;
         }

         if(touchEntry(entry)) {
            cachelist.add(entry);
         }

         return entry.data;
      }

      return null;
   }

   /**
    * Check if the entry is already older than the expiration date.
    */
   protected boolean isOutOfDate(CacheEntry<K, V> entry) {
      long now = System.currentTimeMillis();
      return entry.timeout != -1 && entry.ts + entry.timeout <= now;
   }

   /**
    * Call whenever an entry is used (get).
    * @return false if the entry should be removed.
    */
   protected boolean touchEntry(CacheEntry<K, V> entry) {
      entry.touch();
      return true;
   }

   /**
    * Remove timed out entries from the cache.
    * @return true if entries are removed.
    */
   private boolean checkTimeout() {
      boolean changed = false;
      long now = System.currentTimeMillis();

      synchronized(this) {
         lastCheck = now;

         // remove entries if more than limit
         while(cachelist.size() > limit) {
            CacheEntry<K, V> entry = cachelist.first();

            demote(entry);
            cachelist.remove(entry);
            cachemap.remove(entry.key);
            changed = true;
         }

         // remove expired entries
         while(cachelist.size() > 0) {
            CacheEntry<K, V> entry = cachelist.first();

            if(entry.ts + timeout > now) {
               break;
            }

            demote(entry);
            cachelist.remove(entry);
            cachemap.remove(entry.key);
            changed = true;
         }

         // level 2 cache doesn't need to be cleaned so often
         if(lastClean + 20 * 60000 < now) {
            lastClean = now;
            cleanL2Cache();
         }
      }

      return changed;
   }

   /**
    * Optional method to demote a cache entry to a second-level cache. Because
    * of memory constraints, the cached entry should be removed from primary
    * memory, but has not expired. By default this method does nothing.
    *
    * @param entry the entry to demote.
    * @return true if successful.
    */
   protected boolean demote(CacheEntry<K, V> entry) {
      return true;
   }

   /**
    * Optional method that promotes a cache entry from a second-level cache. By
    * default, this method returns <tt>null</tt>.
    *
    * @param key the key of the cache entry.
    * @param ts the minimum timestamp of the data. don't promote an entry if it's older than ts.
    * @return the promoted key, or <tt>null</tt> if it is not available.
    */
   protected CacheEntry<K, V> promote(K key, long ts) {
      return null;
   }

   /**
    * Optional method that removes any expired cache entries from the second
    * level cache. By default, this method does nothing.
    */
   protected void cleanL2Cache() {
      // NO-OP
   }

   /**
    * Clean up entry in the cache.
    * @param minage minimum age.
    * @return true if entries are removed.
    */
   private boolean sweep(long minage) {
      if(!isSweepEnabled()) {
         return false;
      }

      boolean changed = false;
      long now = System.currentTimeMillis();

      synchronized(this) {
         while(!cachelist.isEmpty()) {
            CacheEntry<K, V> entry = cachelist.first();

            if(now - entry.ts > minage) {
               demote(entry);
               cachemap.remove(entry.getKey());
               cachelist.remove(entry);
               changed = true;
            }
            else {
               break;
            }
         }
      }

      return changed;
   }

   /**
    * Create the hashmap for storying the cached values.
    */
   protected Map<K, CacheEntry<K, V>> createCacheMap() {
      return new HashMap<>();
   }

   /**
    * Cache entry, stores key, data and timestamp.
    */
   protected static class CacheEntry<K, V> implements Comparable<CacheEntry<?, ?>> {
      public CacheEntry(K key, V data) {
         this(key, data, -1L);
      }

      public CacheEntry(K key, V data, long timeout) {
         super();

         this.key = key;
         this.data = data;
         this.timeout = timeout;
         this.ts = System.currentTimeMillis();
         this.its = ts;
      }

      public void touch() {
         ts = System.currentTimeMillis();
      }

      public int hashCode() {
         return key.hashCode() + (int) ts;
      }

      public boolean equals(Object obj) {
         if(obj instanceof CacheEntry) {
            CacheEntry<?, ?> entry = (CacheEntry<?, ?>) obj;
            return key.equals(entry.key) && ts == entry.ts;
         }

         return false;
      }

      @Override
      public int compareTo(CacheEntry<?, ?> entry) {
         try {
            long rc = ts - entry.ts;

            if(rc == 0) {
               return key.toString().compareTo(entry.key.toString());
            }

            return (int) rc;
         }
         catch(Exception ex) {
            return 0;
         }
      }

      public V getData() {
         return data;
      }

      public K getKey() {
         return key;
      }

      public long getLastAccess() {
         return ts;
      }

      public int getAndIncrement() {
         return counter.getAndIncrement();
      }

      public int getAndDecrement() {
         return counter.getAndDecrement();
      }

      public int counter() {
         return counter.intValue();
      }

      public long getEntryTimeout() {
         return timeout;
      }

      public String toString() {
         return counter + "";
      }

      private final K key;
      private final V data;
      private final AtomicInteger counter = new AtomicInteger();
      private final long timeout;
      private long ts;
      private final long its;
   }

   /**
    * Get the string representation.
    */
   public String toString() {
      return super.toString() + "[" + cachemap + "]";
   }

   private static class Sweeper extends TimedQueue.TimedRunnable {
      public Sweeper() {
         super(10000);
      }

      @Override
      public boolean isRecurring() {
         return true;
      }

      @Override
      public void run() {
         boolean changed = false;
         long now = System.currentTimeMillis();

         try {
            DataCache<?, ?>[] caches;

            synchronized(CACHES) {
               caches = CACHES.toArray(new DataCache<?, ?>[0]);
            }

            for(DataCache<?, ?> cache : caches) {
               if(now > cache.lastCheck + 60000) {
                  changed = cache.checkTimeout() || changed;
               }
            }
         }
         catch(ConcurrentModificationException exc) {
            // ignore
         }

         long minage = 4000;

         while(XSwapper.getMemoryState() <= XSwapper.CRITICAL_MEM) {
            try {
               int nremain = 0;
               DataCache<?, ?>[] caches;

               synchronized(CACHES) {
                  caches = CACHES.toArray(new DataCache<?, ?>[0]);
               }

               for(DataCache<?, ?> cache : caches) {
                  changed = cache.sweep(minage) || changed;
                  nremain += cache.cachemap.size();
               }

               if(nremain <= 0 || minage <= 500) {
                  break;
               }

               minage -= 500;
               Thread.sleep(200);
            }
            catch(Exception exc) {
               LOG.warn("Failed to clean up cache", exc);
            }
         }

         // call gc() so table lens will be disposed, and the swap files
         // cleaned up immediately
         if(doGC && changed) {
            System.gc();
         }
      }
   }

   private static final class CacheSet implements Set<DataCache<?, ?>> {
      public CacheSet() {
         map = new WeakHashMap<>();
      }

      @Override
      public int size() {
         return map.size();
      }

      @Override
      public boolean isEmpty() {
         return map.isEmpty();
      }

      @Override
      public boolean contains(Object o) {
         return map.containsKey(o);
      }

      @Override
      public Iterator<DataCache<?, ?>> iterator() {
         return map.keySet().iterator();
      }

      @Override
      public Object[] toArray() {
         return map.keySet().toArray();
      }

      @Override
      public <T> T[] toArray(T[] a) {
         return map.keySet().toArray(a);
      }

      @Override
      public boolean add(DataCache<?, ?> e) {
         return (map.put(e, VALUE) == null);
      }

      @Override
      public boolean remove(Object o) {
         return (map.remove(o) != null);
      }

      @Override
      public boolean containsAll(Collection<?> c) {
         return map.keySet().containsAll(c);
      }

      @Override
      public boolean addAll(Collection<? extends DataCache<?, ?>> c) {
         boolean result = false;

         for(DataCache<?, ?> key : c) {
            result = result || add(key);
         }

         return result;
      }

      @Override
      public boolean retainAll(Collection<?> c) {
         return map.keySet().retainAll(c);
      }

      @Override
      public boolean removeAll(Collection<?> c) {
         return map.keySet().removeAll(c);
      }

      @Override
      public void clear() {
         map.clear();
      }

      private final Map<DataCache<?, ?>, Object> map;
      private static final Object VALUE = new Object() {};
   }

   private final Map<K, CacheEntry<K, V>> cachemap = createCacheMap();
   private final TreeSet<CacheEntry<K, V>> cachelist = new TreeSet<>();

   private int limit = 20; // number of entries
   private long timeout = 600000; // expiration period, ms
   private boolean sweepEnabled = true;
   private transient long lastCheck = 0; // last check timeout
   private transient long lastClean = 0; // last cleanL2Cache

   private static final CacheSet CACHES = new CacheSet();
   private static boolean doGC = false;
   private static final Logger LOG = LoggerFactory.getLogger(DataCache.class);

   static {
      TimedQueue.add(new Sweeper());
      doGC = XSwapUtil.isParallelGC();
   }
}
