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

import java.util.*;
import java.util.concurrent.*;

public class LocalClusterMap<K, V>
   implements DistributedMap<K, V>, MapChangeListener<K, V>, AutoCloseable
{
   public LocalClusterMap(String id, Cluster cluster, DistributedMap<K, V> distributedMap) {
      this.id = id;
      this.cluster = cluster;
      this.distributedMap = distributedMap;
      this.local = new ConcurrentHashMap<>(distributedMap);
      cluster.addMapListener(id, this);
   }

   @Override
   public void close() {
      cluster.removeMapListener(id, this);
   }

   @Override
   public void entryAdded(EntryEvent<K, V> event) {
      entryUpdated(event);
   }

   @Override
   public void entryRemoved(EntryEvent<K, V> event) {
      local.remove(event.getKey());
   }

   @Override
   public void entryUpdated(EntryEvent<K, V> event) {
      local.put(event.getKey(), event.getValue());
   }

   @Override
   public V putIfAbsent(K key, V value) {
      local.putIfAbsent(key, value);
      return distributedMap.putIfAbsent(key, value);
   }

   @Override
   public boolean remove(Object key, Object value) {
      local.remove(key, value);
      return distributedMap.remove(key, value);
   }

   @Override
   public boolean replace(K key, V oldValue, V newValue) {
      local.replace(key, oldValue, newValue);
      return distributedMap.replace(key, oldValue, newValue);
   }

   @Override
   public V replace(K key, V value) {
      local.replace(key, value);
      return distributedMap.replace(key, value);
   }

   @Override
   public int size() {
      return local.size();
   }

   @Override
   public boolean isEmpty() {
      return local.isEmpty();
   }

   @Override
   public boolean containsKey(Object key) {
      return local.containsKey(key);
   }

   @Override
   public boolean containsValue(Object value) {
      return local.containsValue(value);
   }

   @Override
   public V get(Object key) {
      return local.get(key);
   }

   @Override
   public V put(K key, V value) {
      local.put(key, value);
      return distributedMap.put(key, value);
   }

   @Override
   public V remove(Object key) {
      local.remove(key);
      return distributedMap.remove(key);
   }

   @Override
   public void putAll(Map<? extends K, ? extends V> m) {
      local.putAll(m);
      distributedMap.putAll(m);
   }

   @Override
   public void clear() {
      local.clear();
      distributedMap.clear();
   }

   @Override
   public Set<K> keySet() {
      return new LocalKeySet<>(distributedMap, local.keySet());
   }

   @Override
   public Collection<V> values() {
      return new LocalValues<>(distributedMap, local.values());
   }

   public Collection<V> distributedValues() {
      return distributedMap.values();
   }

   @Override
   public Set<Entry<K, V>> entrySet() {
      return new LocalEntrySet<>(distributedMap, local.entrySet());
   }

   public Set<Entry<K, V>> distributedEntrySet() {
      return distributedMap.entrySet();
   }

   @Override
   public void lock(K key) {
      distributedMap.lock(key);
   }

   @Override
   public void lock(K key, long leaseTime, TimeUnit timeUnit) {
      distributedMap.lock(key, leaseTime, timeUnit);
   }

   @Override
   public void unlock(K key) {
      distributedMap.unlock(key);
   }

   @Override
   public void set(K key, V value) {
      local.put(key, value);
      distributedMap.set(key, value);
   }

   private final String id;
   private final Cluster cluster;
   private final DistributedMap<K, V> distributedMap;
   private final ConcurrentMap<K, V> local;

   private static final class LocalKeySet<T> implements Set<T> {
      LocalKeySet(Map<T, ?> map, Set<T> local) {
         this.map = map;
         this.local = local;
      }

      @Override
      public int size() {
         return local.size();
      }

      @Override
      public boolean isEmpty() {
         return local.isEmpty();
      }

      @Override
      public boolean contains(Object o) {
         return local.contains(o);
      }

      @Override
      public Iterator<T> iterator() {
         final Iterator<T> iterator = local.iterator();
         return new Iterator<T>() {
            @Override
            public boolean hasNext() {
               return iterator.hasNext();
            }

            @Override
            public T next() {
               current = iterator.next();
               return current;
            }

            @Override
            public void remove() {
               iterator.remove();
               map.remove(current);
            }

            private T current;
         };
      }

      @Override
      public Object[] toArray() {
         return local.toArray();
      }

      @SuppressWarnings("SuspiciousToArrayCall")
      @Override
      public <T1> T1[] toArray(T1[] a) {
         return local.toArray(a);
      }

      @Override
      public boolean add(T t) {
         throw new UnsupportedOperationException();
      }

      @Override
      public boolean remove(Object o) {
         local.remove(o);
         return map.keySet().remove(o);
      }

      @Override
      public boolean containsAll(Collection<?> c) {
         return local.containsAll(c);
      }

      @Override
      public boolean addAll(Collection<? extends T> c) {
         throw new UnsupportedOperationException();
      }

      @Override
      public boolean retainAll(Collection<?> c) {
         local.retainAll(c);
         return map.keySet().retainAll(c);
      }

      @Override
      public boolean removeAll(Collection<?> c) {
         local.removeAll(c);
         return map.keySet().removeAll(c);
      }

      @Override
      public void clear() {
         local.clear();
         map.clear();
      }

      @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
      @Override
      public boolean equals(Object o) {
         return local.equals(o);
      }

      @Override
      public int hashCode() {
         return local.hashCode();
      }

      private final Map<T, ?> map;
      private final Set<T> local;
   }

   private static final class LocalEntrySet<K, V> implements Set<Map.Entry<K, V>> {
      LocalEntrySet(Map<K, V> map, Set<Map.Entry<K, V>> local) {
         this.map = map;
         this.local = local;
      }

      @Override
      public int size() {
         return local.size();
      }

      @Override
      public boolean isEmpty() {
         return local.isEmpty();
      }

      @Override
      public boolean contains(Object o) {
         return local.contains(o);
      }

      @Override
      public Iterator<Map.Entry<K, V>> iterator() {
         Iterator<Map.Entry<K, V>> iterator = local.iterator();
         return new Iterator<Map.Entry<K, V>>() {
            @Override
            public boolean hasNext() {
               return iterator.hasNext();
            }

            @Override
            public Map.Entry<K, V> next() {
               current = iterator.next();
               return current;
            }

            @Override
            public void remove() {
               iterator.remove();
               map.entrySet().remove(current);
            }

            private Map.Entry<K, V> current;
         };
      }

      @Override
      public Object[] toArray() {
         return local.toArray();
      }

      @SuppressWarnings("SuspiciousToArrayCall")
      @Override
      public <T> T[] toArray(T[] a) {
         return local.toArray(a);
      }

      @Override
      public boolean add(Map.Entry<K, V> kvEntry) {
         throw new UnsupportedOperationException();
      }

      @Override
      public boolean remove(Object o) {
         local.remove(o);
         return map.entrySet().remove(o);
      }

      @Override
      public boolean containsAll(Collection<?> c) {
         return local.containsAll(c);
      }

      @Override
      public boolean addAll(Collection<? extends Map.Entry<K, V>> c) {
         throw new UnsupportedOperationException();
      }

      @Override
      public boolean retainAll(Collection<?> c) {
         local.retainAll(c);
         return map.entrySet().retainAll(c);
      }

      @Override
      public boolean removeAll(Collection<?> c) {
         local.removeAll(c);
         return map.entrySet().removeAll(c);
      }

      @Override
      public void clear() {
         local.clear();
         map.clear();
      }

      private final Map<K, V> map;
      private final Set<Map.Entry<K, V>> local;
   }

   private static final class LocalValues<T> implements Collection<T> {
      LocalValues(Map<?, T> map, Collection<T> local) {
         this.map = map;
         this.local = local;
      }

      @Override
      public int size() {
         return local.size();
      }

      @Override
      public boolean isEmpty() {
         return local.isEmpty();
      }

      @Override
      public boolean contains(Object o) {
         return local.contains(o);
      }

      @Override
      public Iterator<T> iterator() {
         final Iterator<T> iterator = local.iterator();
         return new Iterator<T>() {
            @Override
            public boolean hasNext() {
               return iterator.hasNext();
            }

            @Override
            public T next() {
               current = iterator.next();
               return current;
            }

            @Override
            public void remove() {
               iterator.remove();
               map.values().remove(current);
            }

            private T current;
         };
      }

      @Override
      public Object[] toArray() {
         return local.toArray();
      }

      @SuppressWarnings("SuspiciousToArrayCall")
      @Override
      public <T1> T1[] toArray(T1[] a) {
         return local.toArray(a);
      }

      @Override
      public boolean add(T t) {
         throw new UnsupportedOperationException();
      }

      @Override
      public boolean remove(Object o) {
         return false;
      }

      @Override
      public boolean containsAll(Collection<?> c) {
         return false;
      }

      @Override
      public boolean addAll(Collection<? extends T> c) {
         throw new UnsupportedOperationException();
      }

      @Override
      public boolean removeAll(Collection<?> c) {
         local.removeAll(c);
         return map.values().removeAll(c);
      }

      @Override
      public boolean retainAll(Collection<?> c) {
         local.retainAll(c);
         return map.values().retainAll(c);
      }

      @Override
      public void clear() {
         local.clear();
         map.clear();
      }

      private final Map<?, T> map;
      private final Collection<T> local;
   }
}
