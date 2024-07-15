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
import java.util.concurrent.ConcurrentHashMap;

public class LocalClusterMultiMap<K, V> implements AutoCloseable {
   public LocalClusterMultiMap(String id, Cluster cluster, MultiMap<K, V> multiMap) {
      this.id = id;
      this.cluster = cluster;
      this.multiMap = multiMap;
      this.local = new ConcurrentHashMap<>();
      this.listener = new MultiMapChangeListener();
      cluster.addMultiMapListener(id, listener);
   }

   @Override
   public void close() throws Exception {
      cluster.removeMultiMapListener(this.id, listener);
   }

   public void remove(K key, V value) {
      localRemove(key, value);
      multiMap.remove(key, value);
   }

   public Collection<V> get(K key) {
      return local.computeIfAbsent(key, k -> {
         final Collection<V> remoteValues = multiMap.get(k);
         final Set<V> set = newConcurrentHashSet();

         if(remoteValues != null) {
            set.addAll(remoteValues);
         }
         
         return set;
      });
   }

   public void put(K key, V value) {
      localPut(key, value);
      multiMap.put(key, value);
   }

   public Collection<V> remove(K key) {
      local.remove(key);
      return multiMap.remove(key);
   }

   public Set<K> keySet() {
      return multiMap.keySet();
   }

   public void clear() {
      local.clear();
      multiMap.clear();
   }

   private void localPut(K key, V value) {
      local.compute(key, (k, set) -> {
         if(set == null) {
            set = newConcurrentHashSet();
         }

         set.add(value);
         return set;
      });
   }

   private void localRemove(K key, V value) {
      local.compute(key, (k, set) -> {
         if(set == null) {
            return null;
         }

         set.remove(value);

         if(set.size() == 0) {
            return null;
         }
         else {
            return set;
         }
      });
   }

   private Set<V> newConcurrentHashSet() {
      return Collections.newSetFromMap(new ConcurrentHashMap<>());
   }

   private class MultiMapChangeListener implements MapChangeListener<K, Collection<V>> {
      @Override
      public void entryAdded(EntryEvent<K, Collection<V>> event) {
         entryUpdated(event);
      }

      @Override
      public void entryRemoved(EntryEvent<K, Collection<V>> event) {
         local.remove(event.getKey());
      }

      @Override
      public void entryUpdated(EntryEvent<K, Collection<V>> event) {
         local.compute(event.getKey(), (k, set) -> {
            if(set == null) {
               set = newConcurrentHashSet();
            }

            Collection<V> oldValues = event.getOldValue();
            Collection<V> newValues = event.getValue();

            // entry removed in multimap
            if(oldValues != null && newValues != null && oldValues.size() > newValues.size()) {
               for(V oldValue : oldValues) {
                  if(!newValues.contains(oldValue)) {
                     set.remove(oldValue);
                     break;
                  }
               }
            }
            else {
               set.addAll(event.getValue());
            }

            return set;
         });
      }
   }

   private final String id;
   private final Cluster cluster;
   private final MultiMap<K, V> multiMap;
   private final ConcurrentHashMap<K, Set<V>> local;
   private final MultiMapChangeListener listener;
}
