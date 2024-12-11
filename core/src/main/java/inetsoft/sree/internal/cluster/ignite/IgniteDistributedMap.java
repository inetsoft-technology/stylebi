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
package inetsoft.sree.internal.cluster.ignite;

import inetsoft.sree.internal.cluster.DistributedMap;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.lang.IgniteFuture;

import javax.cache.CacheException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.function.Supplier;

public class IgniteDistributedMap<K, V> implements DistributedMap<K, V> {
   public IgniteDistributedMap(IgniteCache<K, V> cache) {
      this.cache = cache;
   }

   @Override
   public void putAll(Map<? extends K, ? extends V> m) {
      executeWithRetry(() -> {
         cache.putAll(m);
         return null;
      });
   }

   @Override
   public boolean containsKey(Object key) {
      return executeWithRetry(() -> cache.containsKey((K) key));
   }

   @Override
   public boolean containsValue(Object value) {
      return executeWithRetry(() -> {
         for(IgniteCache.Entry<K, V> entry : cache) {
            if(value.equals(entry.getValue())) {
               return true;
            }
         }

         return false;
      });
   }

   @Override
   public V get(Object key) {
      return executeWithRetry(() -> cache.get((K) key));
   }

   @Override
   public V put(K key, V value) {
      return executeWithRetry(() -> {
         cache.put(key, value);
         return value;
      });
   }

   @Override
   public V remove(Object key) {
      return executeWithRetry(() -> cache.getAndRemove((K) key));
   }

   @Override
   public boolean remove(Object key, Object value) {
      return executeWithRetry(() -> cache.remove((K) key, (V) value));
   }

   @Override
   public void removeAll(Set<? extends K> keys) {
      executeWithRetry(() -> {
         cache.removeAll(keys);
         return null;
      });
   }

   public void delete(K key) {
      executeWithRetry(() -> {
         cache.remove(key);
         return null;
      });
   }

   @Override
   public void clear() {
      executeWithRetry(() -> {
         cache.clear();
         return null;
      });
   }

   public IgniteFuture<V> getAsync(K key) {
      return executeWithRetry(() -> cache.getAsync(key));
   }

   public IgniteFuture<Void> putAsync(K key, V value) {
      return executeWithRetry(() -> cache.putAsync(key, value));
   }

   public IgniteFuture<Boolean> removeAsync(K key) {
      return executeWithRetry(() -> cache.removeAsync(key));
   }

   @Override
   public V putIfAbsent(K key, V value) {
      return executeWithRetry(() -> cache.getAndPutIfAbsent(key, value));
   }

   @Override
   public boolean replace(K key, V oldValue, V newValue) {
      return executeWithRetry(() -> cache.replace(key, oldValue, newValue));
   }

   @Override
   public V replace(K key, V value) {
      return executeWithRetry(() -> cache.getAndReplace(key, value));
   }

   @Override
   public void set(K key, V value) {
      executeWithRetry(() -> {
         cache.put(key, value);
         return null;
      });
   }

   @Override
   public void lock(K key) {
      getLock(key).lock();
   }

   @Override
   public void lock(K key, long leaseTime, TimeUnit timeUnit) {
      try {
         getLock(key).tryLock(leaseTime, timeUnit);
      }
      catch(InterruptedException e) {
      }
   }

   public boolean tryLock(K key) {
      return getLock(key).tryLock();
   }

   public boolean tryLock(K key, long time, TimeUnit timeunit) throws InterruptedException {
      return getLock(key).tryLock(time, timeunit);
   }

   @Override
   public void unlock(K key) {
      executeWithRetry(() -> {
         Lock lock = getLock(key);
         lock.unlock();
         lockMap.get().remove(key);
         return null;
      });
   }

   @Override
   public Set<K> keySet() {
      return executeWithRetry(() -> {
         Set<K> set = new HashSet<>();

         for(IgniteCache.Entry<K, V> entry : cache) {
            set.add(entry.getKey());
         }

         return set;
      });
   }

   @Override
   public Collection<V> values() {
      return executeWithRetry(() -> {
         Set<V> set = new HashSet<>();

         for(IgniteCache.Entry<K, V> entry : cache) {
            set.add(entry.getValue());
         }

         return set;
      });
   }

   @Override
   public Set<Entry<K, V>> entrySet() {
      return executeWithRetry(() -> {
         Set<Entry<K, V>> set = new HashSet<>();

         for(IgniteCache.Entry<K, V> entry : cache) {
            set.add(new AbstractMap.SimpleEntry<>(entry.getKey(), entry.getValue()));
         }

         return set;
      });
   }

   @Override
   public V getOrDefault(Object key, V defaultValue) {
      return executeWithRetry(() -> {
         V value = get(key);

         if(value == null) {
            return defaultValue;
         }

         return value;
      });
   }

   @Override
   public int size() {
      return executeWithRetry(cache::size);
   }

   @Override
   public boolean isEmpty() {
      return executeWithRetry(() -> cache.size() == 0);
   }

   private Lock getLock(K key) {
      return executeWithRetry(() -> {
         Lock lock = lockMap.get().get(key);

         if(lock == null) {
            lock = cache.lock(key);
            lockMap.get().put(key, lock);
         }

         return lock;
      });
   }

   private <T> T executeWithRetry(Supplier<T> operation) {
      int retries = 0;

      while(retries < MAX_RETRIES) {
         try {
            return operation.get();
         }
         catch(CacheException e) {
            retries++;

            if(retries == MAX_RETRIES) {
               throw e;
            }
            else {
               try {
                  Thread.sleep(200);
               }
               catch(InterruptedException ex) {
                  throw new RuntimeException(ex);
               }
            }
         }
      }

      throw new RuntimeException("Operation failed after retries.");
   }

   private final IgniteCache<K, V> cache;
   private final ThreadLocal<Map<K, Lock>> lockMap = ThreadLocal.withInitial(HashMap::new);
   private static final int MAX_RETRIES = 5;
}
