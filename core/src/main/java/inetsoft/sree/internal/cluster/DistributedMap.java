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

import org.apache.ignite.cache.affinity.AffinityKey;
import org.apache.ignite.cache.affinity.AffinityKeyMapped;

import java.lang.reflect.Field;

import java.util.*;
import java.util.stream.Collectors;

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

   /**
    * Gets the keys of this map whose affinity key is {@code affinityKey}: an {@link AffinityKey}
    * or the value of a field annotated with {@link AffinityKeyMapped}. All such keys are in one partition, and only that partition is read,
    * unlike {@link #keySet()}, which reads every entry of the map.
    *
    * @param affinityKey the affinity key.
    *
    * @return the matching keys.
    */
   default Set<K> keySetByAffinityKey(Object affinityKey) {
      return keySet().stream()
         .filter(k -> Objects.equals(getAffinityKey(k), affinityKey))
         .collect(Collectors.toSet());
   }

   private static Object getAffinityKey(Object key) {
      if(key instanceof AffinityKey<?> affinity) {
         return affinity.affinityKey();
      }

      for(Class<?> cls = key == null ? null : key.getClass(); cls != null && cls != Object.class;
          cls = cls.getSuperclass())
      {
         for(Field field : cls.getDeclaredFields()) {
            if(field.isAnnotationPresent(AffinityKeyMapped.class)) {
               try {
                  field.setAccessible(true);
                  return field.get(key);
               }
               catch(IllegalAccessException e) {
                  throw new IllegalStateException(e);
               }
            }
         }
      }

      return null;
   }

   /**
    * Gets all the keys of this map like {@link #keySet()}, but without deserializing the values
    * where the implementation can avoid it.
    *
    * @return the keys.
    */
   default Set<K> keySetWithoutValues() {
      return keySet();
   }
}
