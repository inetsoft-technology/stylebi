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
package inetsoft.sree.internal.cluster.ignite;

import inetsoft.sree.internal.cluster.DistributedMap;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.lang.IgniteFuture;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

public class IgniteDistributedMap<K, V> implements DistributedMap<K, V> {
   public IgniteDistributedMap(IgniteCache<K, V> cache) {
      this.cache = cache;
   }

   @Override
   public void putAll(Map<? extends K, ? extends V> m) {
      cache.putAll(m);
   }

   @Override
   public boolean containsKey(Object key) {
      return cache.containsKey((K) key);
   }

   @Override
   public boolean containsValue(Object value) {
      for(IgniteCache.Entry<K, V> entry : cache) {
         if(value.equals(entry.getValue())) {
            return true;
         }
      }

      return false;
   }

   @Override
   public V get(Object key) {
      return cache.get((K) key);
   }

   @Override
   public V put(K key, V value) {
      cache.put(key, value);
      return value;
   }

   @Override
   public V remove(Object key) {
      return cache.getAndRemove((K) key);
   }

   @Override
   public boolean remove(Object key, Object value) {
      return cache.remove((K) key, (V) value);
   }

   public void removeAll(Set<? extends K> keys) {
      cache.removeAll(keys);
   }

   public void delete(K key) {
      cache.remove(key);
   }

   @Override
   public void clear() {
      cache.clear();
   }

   public IgniteFuture<V> getAsync(K key) {
      return cache.getAsync(key);
   }

   public IgniteFuture<Void> putAsync(K key, V value) {
      return cache.putAsync(key, value);
   }

   public IgniteFuture<Boolean> removeAsync(K key) {
      return cache.removeAsync(key);
   }

   @Override
   public V putIfAbsent(K key, V value) {
      return cache.getAndPutIfAbsent(key, value);
   }

   @Override
   public boolean replace(K key, V oldValue, V newValue) {
      return cache.replace(key, oldValue, newValue);
   }

   @Override
   public V replace(K key, V value) {
      return cache.getAndReplace(key, value);
   }

   @Override
   public void set(K key, V value) {
      cache.put(key, value);
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
      Lock lock = getLock(key);
      lock.unlock();
      lockMap.get().remove(key);
   }

   @Override
   public Set<K> keySet() {
      Set<K> set = new HashSet<>();

      for(IgniteCache.Entry<K, V> entry : cache) {
         set.add(entry.getKey());
      }

      return set;
   }

   @Override
   public Collection<V> values() {
      Set<V> set = new HashSet<>();

      for(IgniteCache.Entry<K, V> entry : cache) {
         set.add(entry.getValue());
      }

      return set;
   }

   @Override
   public Set<Entry<K, V>> entrySet() {
      Set<Entry<K, V>> set = new HashSet<>();

      for(IgniteCache.Entry<K, V> entry : cache) {
         set.add(new AbstractMap.SimpleEntry<>(entry.getKey(), entry.getValue()));
      }

      return set;
   }

   @Override
   public V getOrDefault(Object key, V defaultValue) {
      V value = get(key);

      if(value == null) {
         return defaultValue;
      }

      return value;
   }

   @Override
   public int size() {
      return cache.size();
   }

   @Override
   public boolean isEmpty() {
      return cache.size() == 0;
   }

   private Lock getLock(K key) {
      Lock lock = lockMap.get().get(key);

      if(lock == null) {
         lock = cache.lock(key);
         lockMap.get().put(key, lock);
      }

      return lock;
   }

   private final IgniteCache<K, V> cache;
   private final ThreadLocal<Map<K, Lock>> lockMap =
      ThreadLocal.withInitial(HashMap::new);
}
