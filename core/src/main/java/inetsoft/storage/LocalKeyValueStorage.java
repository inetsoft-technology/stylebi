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
package inetsoft.storage;

import inetsoft.sree.internal.cluster.*;
import inetsoft.util.ThreadPool;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.Future;
import java.util.stream.Stream;

/**
 * {@code LocalKeyValueStorage} is the client for a shared key-value store.
 *
 * @param <T> the value type.
 */
class LocalKeyValueStorage<T extends Serializable> implements KeyValueStorage<T> {
   LocalKeyValueStorage(String id, LoadKeyValueTask<T> load) {
      Objects.requireNonNull(id, "The key-value store identifier cannot be null");
      this.id = id;
      this.cluster = Cluster.getInstance();
      this.mapName = "inetsoft.storage.kv." + id;
      this.map = cluster.getReplicatedMap(mapName);
      cluster.addReplicatedMapListener(mapName, listenerDelegate);

      try {
         // wait for the initial load to avoid race conditions
         cluster.submit(id, load).get();
      }
      catch(Exception e) {
         LoggerFactory.getLogger(getClass()).warn("Failed to load key-value storage {}", id, e);
      }
   }

   public boolean contains(String key) {
      return map.containsKey(key);
   }

   @Override
   public T get(String key) {
      return map.get(key);
   }

   @Override
   public Future<T> put(String key, T value) {
      return cluster.submit(id, new PutKeyValueTask<>(id, key, value));
   }

   @Override
   public Future<?> putAll(SortedMap<String, T> values) {
      return cluster.submit(id, new PutAllKeyValueTask<>(id, values));
   }

   @Override
   public Future<T> remove(String key) {
      return cluster.submit(id, new DeleteKeyValueTask<>(id, key));
   }

   @Override
   public Future<?> removeAll(Set<String> keys) {
      return cluster.submit(id, new DeleteAllKeyValueTask<>(id, keys));
   }

   @Override
   public Future<T> rename(String oldKey, String newKey, T value) {
      return cluster.submit(id, new RenameKeyValueTask<>(id, oldKey, newKey, value));
   }

   @Override
   public Future<?> replaceAll(SortedMap<String, T> values) {
      return cluster.submit(id, new ReplaceAllKeyValueTask<>(id, values));
   }

   @Override
   public Future<?> deleteStore() {
      return cluster.submit(id, new DeleteKeyValueStorageTask<>(id));
   }

   @Override
   public Stream<KeyValuePair<T>> stream() {
      return map.entrySet().stream()
         .map(e -> new KeyValuePair<>(e.getKey(), e.getValue()));
   }

   @Override
   public Stream<String> keys() {
      return map.keySet().stream();
   }

   @Override
   public int size() {
      return map.size();
   }

   @Override
   public void addListener(Listener<T> listener) {
      listeners.add(listener);
   }

   @Override
   public void removeListener(Listener<T> listener) {
      listeners.remove(listener);
   }

   @Override
   public void close() throws Exception {
      cluster.removeReplicatedMapListener(mapName, listenerDelegate);
      isClosed = true;
   }

   @Override
   public boolean isClosed() {
      return isClosed;
   }

   private final String id;
   private final Cluster cluster;
   private final String mapName;
   private final Map<String, T> map;
   private final Set<Listener<T>> listeners =
      new ConcurrentSkipListSet<>(Comparator.comparing(Listener::hashCode));
   private final ListenerDelegate listenerDelegate = new ListenerDelegate();
   private boolean isClosed = false;

   private final class ListenerDelegate implements MapChangeListener<String, T> {
      @Override
      public void entryAdded(EntryEvent<String, T> event) {
         ThreadPool.addOnDemand(() -> {
            Event<T> e = createEvent(event);

            for(Listener<T> listener : listeners) {
               listener.entryAdded(e);
            }
         });
      }

      @Override
      public void entryRemoved(EntryEvent<String, T> event) {
         ThreadPool.addOnDemand(() -> {
            Event<T> e = createEvent(event);

            for(Listener<T> listener : listeners) {
               listener.entryRemoved(e);
            }
         });
      }

      @Override
      public void entryUpdated(EntryEvent<String, T> event) {
         ThreadPool.addOnDemand(() -> {
            Event<T> e = createEvent(event);

            for(Listener<T> listener : listeners) {
               listener.entryUpdated(e);
            }
         });
      }

      private Event<T> createEvent(EntryEvent<String, T> event) {
         String key = event.getKey();
         String mapName = event.getMapName();
         T oldValue = event.getOldValue();
         T newValue = event.getValue();
         return new Event<>(this, key, mapName, oldValue, newValue);
      }
   }
}
