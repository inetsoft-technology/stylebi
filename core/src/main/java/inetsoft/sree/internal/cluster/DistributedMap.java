/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.sree.internal.cluster;

import java.util.Map;
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
    * Acquires the lock for the specified key for the specified lease time.
    *
    * @param key       the key to lock
    * @param leaseTime time to wait before releasing the lock
    * @param timeUnit  unit of time to specify lease time
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
}
