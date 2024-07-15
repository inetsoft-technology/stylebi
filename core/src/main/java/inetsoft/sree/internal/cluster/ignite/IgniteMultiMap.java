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

import inetsoft.sree.internal.cluster.MultiMap;
import inetsoft.util.Tool;
import org.apache.ignite.IgniteCache;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

public class IgniteMultiMap<K, V> implements MultiMap<K, V> {
   public IgniteMultiMap(IgniteCache<K, Collection<V>> cache) {
      this.cache = cache;
   }

   @Override
   public void put(K key, V value) {
      Collection<V> list = cache.get(key);

      if(list == null) {
         list = new ArrayList<>();
      }

      list.add(value);
      cache.put(key, list);
   }

   @Override
   public Collection<V> get(K key) {
      return cache.get(key);
   }

   @Override
   public void remove(K key, V value) {
      Collection<V> list = cache.get(key);

      if(list != null) {
         list.remove(value);

         if(list.isEmpty()) {
            cache.remove(key);
         }
         else {
            cache.put(key, list);
         }
      }
   }

   @Override
   public Collection<V> remove(K key) {
      return cache.getAndRemove(key);
   }

   @Override
   public void delete(K key) {
      cache.remove(key);
   }

   @Override
   public Set<K> keySet() {
      Set<K> set = new HashSet<>();

      for(IgniteCache.Entry<K, Collection<V>> entry : cache) {
         set.add(entry.getKey());
      }

      return set;
   }

   @Override
   public Collection<V> values() {
      List<V> allValues = new ArrayList<>();

      for(IgniteCache.Entry<K, Collection<V>> entry : cache) {
         Collection<V> list = entry.getValue();

         if(list != null) {
            allValues.addAll(list);
         }
      }

      return allValues;
   }

   @Override
   public Set<Map.Entry<K, V>> entrySet() {
      Set<Map.Entry<K, V>> set = new HashSet<>();

      for(IgniteCache.Entry<K, Collection<V>> entry : cache) {
         Collection<V> list = entry.getValue();

         if(list != null) {
            for(V value : list) {
               set.add(new AbstractMap.SimpleEntry<>(entry.getKey(), value));
            }
         }
      }

      return set;
   }

   @Override
   public boolean containsKey(K key) {
      return cache.containsKey(key);
   }

   @Override
   public boolean containsValue(V value) {
      for(IgniteCache.Entry<K, Collection<V>> entry : cache) {
         Collection<V> list = entry.getValue();

         if(list != null) {
            for(V val : list) {
               if(Tool.equals(value, val)) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   @Override
   public boolean containsEntry(K key, V value) {
      Collection<V> list = cache.get(key);

      if(list != null) {
         return list.contains(value);
      }

      return false;
   }

   @Override
   public int size() {
      int size = 0;

      for(IgniteCache.Entry<K, Collection<V>> entry : cache) {
         Collection<V> list = entry.getValue();

         if(list != null) {
            size += list.size();
         }
      }

      return size;
   }

   @Override
   public void clear() {
      cache.clear();
   }

   @Override
   public int valueCount(K key) {
      Collection<V> list = cache.get(key);

      if(list != null) {
         return list.size();
      }

      return 0;
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

   @Override
   public boolean tryLock(K key) {
      return getLock(key).tryLock();
   }

   @Override
   public boolean tryLock(K key, long time, TimeUnit timeunit) throws InterruptedException {
      return getLock(key).tryLock(time, timeunit);
   }

   @Override
   public void unlock(K key) {
      Lock lock = getLock(key);
      lock.unlock();
      lockMap.get().remove(key);
   }

   private Lock getLock(K key) {
      Lock lock = lockMap.get().get(key);

      if(lock == null) {
         lock = cache.lock(key);
         lockMap.get().put(key, lock);
      }

      return lock;
   }

   private final IgniteCache<K, Collection<V>> cache;
   private final ThreadLocal<Map<K, Lock>> lockMap =
      ThreadLocal.withInitial(HashMap::new);
}
