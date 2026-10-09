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
import inetsoft.util.Tool;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.binary.BinaryObject;
import org.apache.ignite.cache.affinity.Affinity;
import org.apache.ignite.cache.affinity.AffinityKey;
import org.apache.ignite.cache.query.QueryCursor;
import org.apache.ignite.cache.query.ScanQuery;
import org.apache.ignite.lang.IgniteBiPredicate;
import org.apache.ignite.lang.IgniteFuture;

import javax.cache.Cache;
import javax.cache.CacheException;
import java.util.*;
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

   @SuppressWarnings("unchecked")
   @Override
   public boolean containsKey(Object key) {
      return executeWithRetry(() -> cache.containsKey((K) key));
   }

   @Override
   public boolean containsValue(Object value) {
      return executeWithRetry(() -> {
         Iterator<javax.cache.Cache.Entry<K, V>> iter = cache.iterator();

         try {
            while(iter.hasNext()) {
               if(value.equals(iter.next().getValue())) {
                  return true;
               }
            }

            return false;
         }
         finally {
            Tool.closeIterator(iter);
         }
      });
   }

   @SuppressWarnings("unchecked")
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

   @SuppressWarnings("unchecked")
   @Override
   public V remove(Object key) {
      return executeWithRetry(() -> cache.getAndRemove((K) key));
   }

   @SuppressWarnings("unchecked")
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

   @Override
   public void removeAll() {
      executeWithRetry(() -> {
         cache.removeAll();
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

   /**
    * Gets the value of a key. Inside a pessimistic repeatable-read transaction ({@link
    * IgniteCluster#runInTransaction}) the read locks the key until the transaction ends. That is a
    * transaction lock, which Ignite recovers when the locking node fails, unlike an explicit cache
    * lock (Bug #77879).
    */
   @Override
   public V getForUpdate(K key) {
      return executeWithRetry(() -> cache.get(key));
   }

   @Override
   public Set<K> keySet() {
      return executeWithRetry(() -> {
         Set<K> set = new HashSet<>();
         Iterator<javax.cache.Cache.Entry<K, V>> iter = cache.iterator();

         try {
            while(iter.hasNext()) {
               set.add(iter.next().getKey());
            }
         }
         finally {
            Tool.closeIterator(iter);
         }

         return set;
      });
   }

   /**
    * Scans only the partition of {@code affinityKey}, locally when this node holds it. The scan
    * keeps the entries binary, so binary values are not deserialized. Ignite does not store an
    * {@code Externalizable} value in binary form, so the scan still deserializes such values of
    * the entries in the partition (Bug #78108).
    */
   @Override
   public Set<K> keySetByAffinityKey(Object affinityKey) {
      return executeWithRetry(() -> {
         Ignite ignite = cache.unwrap(Ignite.class);
         Affinity<Object> affinity = ignite.affinity(cache.getName());
         ScanQuery<Object, Object> query = new ScanQuery<>(new AffinityKeyFilter(affinityKey));
         query.setPartition(affinity.partition(affinityKey));
         query.setLocal(affinity.isPrimaryOrBackup(ignite.cluster().localNode(), affinityKey));
         return scanKeys(query);
      });
   }

   /**
    * Scans the keys with the entries kept binary, so binary values are not deserialized
    * (Bug #78108).
    */
   @Override
   public Set<K> keySetWithoutValues() {
      return executeWithRetry(() -> scanKeys(new ScanQuery<>()));
   }

   @SuppressWarnings("unchecked")
   private Set<K> scanKeys(ScanQuery<Object, Object> query) {
      IgniteCache<Object, Object> binaryCache = cache.withKeepBinary();
      Set<K> keys = new HashSet<>();

      try(QueryCursor<Cache.Entry<Object, Object>> cursor = binaryCache.query(query)) {
         for(Cache.Entry<Object, Object> entry : cursor) {
            Object key = entry.getKey();
            keys.add((K) (key instanceof BinaryObject binary ? binary.deserialize() : key));
         }
      }

      return keys;
   }

   @Override
   public Collection<V> values() {
      return executeWithRetry(() -> {
         Set<V> set = new HashSet<>();
         Iterator<javax.cache.Cache.Entry<K, V>> iter = cache.iterator();

         try {
            while(iter.hasNext()) {
               set.add(iter.next().getValue());
            }
         }
         finally {
            Tool.closeIterator(iter);
         }

         return set;
      });
   }

   @Override
   public Set<Entry<K, V>> entrySet() {
      return executeWithRetry(() -> {
         Set<Entry<K, V>> set = new HashSet<>();
         Iterator<javax.cache.Cache.Entry<K, V>> iter = cache.iterator();

         try {
            while(iter.hasNext()) {
               javax.cache.Cache.Entry<K, V> entry = iter.next();
               set.add(new AbstractMap.SimpleEntry<>(entry.getKey(), entry.getValue()));
            }
         }
         finally {
            Tool.closeIterator(iter);
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

   private <T> T executeWithRetry(Supplier<T> operation) {
      int retries = 0;
      RuntimeException lastException = null;

      while(retries < MAX_RETRIES) {
         try {
            return operation.get();
         }
         catch(CacheException | IllegalStateException e) {
            // Bug #77879, an operation that failed in a transaction fails again at once until the
            // transaction ends, so it is not retried
            if(isInTransaction()) {
               throw e;
            }

            lastException = (e instanceof RuntimeException) ?
               (RuntimeException) e : new RuntimeException(e);
            retries++;

            if(retries < MAX_RETRIES) {
               try {
                  Thread.sleep(200);
               }
               catch(InterruptedException ex) {
                  throw new RuntimeException(ex);
               }
            }
         }
      }

      throw lastException;
   }

   private boolean isInTransaction() {
      try {
         return cache.unwrap(Ignite.class).transactions().tx() != null;
      }
      catch(RuntimeException ex) {
         return false;
      }
   }

   private final IgniteCache<K, V> cache;
   private static final int MAX_RETRIES = 5;

   /**
    * Matches the keys with a given affinity key. With a binary scan the key is a
    * {@link BinaryObject}, so the affinity key is read from its affinity key field ({@code affKey}
    * of an {@link AffinityKey}, or the field annotated with {@code AffinityKeyMapped}). This is a
    * named class, not a lambda, so a remote node can load it.
    */
   private static final class AffinityKeyFilter implements IgniteBiPredicate<Object, Object> {
      AffinityKeyFilter(Object affinityKey) {
         this.affinityKey = affinityKey;
      }

      @Override
      public boolean apply(Object key, Object value) {
         Object keyAffinity = null;

         if(key instanceof BinaryObject binary) {
            String field = binary.type().affinityKeyFieldName();
            keyAffinity = field != null && binary.hasField(field) ? binary.field(field) : null;
         }
         else if(key instanceof AffinityKey<?> affinity) {
            keyAffinity = affinity.affinityKey();
         }

         return Objects.equals(this.affinityKey, keyAffinity);
      }

      private final Object affinityKey;
   }
}
