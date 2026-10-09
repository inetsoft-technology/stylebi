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

/**
 * Interface for distributed maps of cluster implementations.
 */
public interface DistributedMap<K, V> extends Map<K, V> {
   /**
    * Gets the value of a key and locks the key until the current transaction ends, so that no
    * other transaction can lock or write it in the meantime. Call it inside
    * {@link Cluster#runInTransaction}, before any other read or write of the key. Outside a
    * transaction it is the same as {@link #get}.
    *
    * @param key the key to lock.
    *
    * @return the value of the key, or {@code null} if there is none.
    */
   V getForUpdate(K key);

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
