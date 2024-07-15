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
import java.util.concurrent.TimeUnit;

/**
 * A specialized map whose keys can be associated with multiple values.
 *
 * @param <K> type of the multimap key
 * @param <V> type of the multimap value
 */
@SuppressWarnings("checkstyle:methodcount")
public interface MultiMap<K, V> {
   /**
    * Stores a key-value pair in the multimap.
    *
    * @param key   the key to be stored
    * @param value the value to be stored
    */
   void put(K key, V value);

   /**
    * Returns the collection of values associated with the key.
    *
    * @param key the key whose associated values are to be returned
    * @return the collection of the values associated with the key
    */
   Collection<V> get(K key);

   /**
    * Removes the given key value pair from the multimap.
    *
    * @param key   the key of the entry to remove
    * @param value the value of the entry to remove
    */
   void remove(K key, V value);

   /**
    * Removes all the entries with the given key.
    *
    * @param key the key of the entries to remove
    * @return the collection of removed values associated with the given key.
    * The returned collection might be modifiable but it has no effect on the multimap.
    */
   Collection<V> remove(K key);

   /**
    * Deletes all the entries with the given key.
    *
    * @param key the key of the entry to remove
    */
   void delete(K key);

   /**
    * Returns the set of keys in the multimap.
    *
    * @return the set of keys in the multimap (the returned set might be
    * modifiable but it has no effect on the multimap)
    */
   Set<K> keySet();

   /**
    * Returns the collection of values in the multimap.
    *
    * @return the collection of values in the multimap (the returned
    * collection might be modifiable but it has no effect on the multimap)
    */
   Collection<V> values();

   /**
    * Returns the set of key-value pairs in the multimap.
    *
    * @return the set of key-value pairs in the multimap (the returned
    * set might be modifiable but it has no effect on the multimap)
    */
   Set<Map.Entry<K, V>> entrySet();

   /**
    * Returns whether the multimap contains an entry with the key.
    *
    * @param key the key whose existence is checked
    * @return {@code true} if the multimap contains an entry with the key,
    * {@code false} otherwise
    */
   boolean containsKey(K key);

   /**
    * Returns whether the multimap contains an entry with the value.
    *
    * @param value the value whose existence is checked
    * @return {@code true} if the multimap contains an entry with the value,
    * {@code false} otherwise.
    */
   boolean containsValue(V value);

   /**
    * Returns whether the multimap contains the given key-value pair.
    *
    * @param key   the key whose existence is checked
    * @param value the value whose existence is checked
    * @return {@code true} if the multimap contains the key-value pair,
    * {@code false} otherwise
    */
   boolean containsEntry(K key, V value);

   /**
    * Returns the number of key-value pairs in the multimap.
    * If the multimap contains more than <tt>Integer.MAX_VALUE</tt> elements,
    * returns <tt>Integer.MAX_VALUE</tt>.
    *
    * @return the number of key-value pairs in the multimap
    */
   int size();

   /**
    * Clears the multimap. Removes all key-value pairs.
    */
   void clear();

   /**
    * Returns the number of values that match the given key in the multimap.
    *
    * @param key the key whose values count is to be returned
    * @return the number of values that match the given key in the multimap
    */
   int valueCount(K key);

   /**
    * Acquires a lock for the specified key.
    *
    * @param key the key to lock
    */
   void lock(K key);

   /**
    * Acquires the lock for the specified key for the specified lease time.
    *
    * @param key       the key to lock
    * @param leaseTime time to wait before releasing the lock
    * @param timeUnit  unit of time for the lease time
    */
   void lock(K key, long leaseTime, TimeUnit timeUnit);

   /**
    * Tries to acquire the lock for the specified key.
    *
    * @param key the key to lock.
    * @return {@code true} if lock is acquired, {@code false} otherwise
    */
   boolean tryLock(K key);

   /**
    * Tries to acquire the lock for the specified key.
    *
    * @param time     the maximum time to wait for the lock
    * @param timeunit the time unit of the {@code time} argument
    * @return {@code true} if the lock was acquired, {@code false} if the
    * waiting time elapsed before the lock was acquired
    */
   boolean tryLock(K key, long time, TimeUnit timeunit) throws InterruptedException;

   /**
    * Releases the lock for the specified key.
    *
    * @param key the key to lock
    */
   void unlock(K key);
}