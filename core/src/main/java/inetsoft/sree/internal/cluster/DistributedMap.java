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

import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Interface for distributed maps of cluster implementations.
 */
public interface DistributedMap<K, V> extends Map<K, V> {
   /**
    * Acquires the lock for the specified key.
    *
    * @param key key to lock
    */
   void lock(K key);

   /**
    * Acquires the lock for the specified key, waiting up to the specified timeout.
    * Throws {@link IllegalStateException} if the lock cannot be acquired within the timeout.
    *
    * @param key       the key to lock
    * @param leaseTime maximum time to wait to acquire the lock
    * @param timeUnit  unit of time for the timeout
    */
   void lock(K key, long leaseTime, TimeUnit timeUnit);

   /**
    * Releases the lock for the specified key.
    *
    * @param key the key to unlock
    */
   void unlock(K key);

   /**
    * Puts an entry into this map without returning the old value.
    *
    * @param key   key of the entry
    * @param value value of the entry
    */
   void set(K key, V value);

   /**
    * Removes a set of keys from the map
    * @param keys
    */
   void removeAll(Set<? extends K> keys);

   /**
    * Removes all entries from the map.
    */
   void removeAll();
}
