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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * {@code LocalKeyValueStorage} is the client for a shared key-value store.
 *
 * @param <T> the value type.
 */
class LocalKeyValueStorage<T extends Serializable> implements KeyValueStorage<T> {
   LocalKeyValueStorage(String id, LoadKeyValueTask<T> load, Cluster cluster) {
      Objects.requireNonNull(id, "The key-value store identifier cannot be null");
      this.id = id;
      this.cluster = cluster;
      this.mapName = "inetsoft.storage.kv." + id;
      this.load = load;
      this.map = cluster.getReplicatedMap(mapName);
      cluster.addReplicatedMapListener(mapName, listenerDelegate);
      // wait for the initial load to avoid race conditions
      loaded = load();
   }

   private boolean load() {
      try {
         cluster.submit(id, load).get(3L, TimeUnit.MINUTES);
         return true;
      }
      catch(ExecutionException e) {
         if(e.getCause() instanceof RuntimeException rte) {
            throw rte;
         }

         LoggerFactory.getLogger(getClass()).warn("Failed to load key-value storage {}", id, e);
      }
      catch(InterruptedException e) {
         Thread.currentThread().interrupt();
         LoggerFactory.getLogger(getClass())
            .warn("Interrupted loading key-value storage {}", id, e);
      }
      catch(TimeoutException e) {
         // Do not rethrow: callers (e.g. PropertiesEngine/DataSpace construction on the
         // application's main thread) expect this constructor to return a usable, if
         // possibly incomplete, storage instance rather than fail startup outright. "loaded"
         // is left false so health checks (see inetsoft.util.health.ClusterHealthService) can
         // tell "loaded fine" apart from "timed out here" instead of reporting healthy either
         // way. Recovery from here is left to the store's own retryLoad(), invoked by callers
         // like PropertiesEngine.getStorage() the next time this store is accessed, rather than
         // a listener on this attempt's future, since an async self-heal here could not
         // reliably beat a Kubernetes startup probe's budget anyway.
         LoggerFactory.getLogger(getClass()).warn(
            "Key-value storage '{}' did not finish loading within the 3 minute timeout; " +
            "this node is starting with a possibly incomplete or empty store", id, e);
      }
      catch(Exception e) {
         LoggerFactory.getLogger(getClass()).warn("Failed to load key-value storage {}", id, e);
      }

      return false;
   }

   @Override
   public boolean isLoaded() {
      return loaded;
   }

   @Override
   public boolean retryLoad() {
      // an interrupted thread, e.g. a startup being shut down, does not wait again
      if(!loaded && !Thread.currentThread().isInterrupted()) {
         loaded = load();
      }

      return loaded;
   }

   public boolean contains(String key) {
      return read(m -> m.containsKey(key));
   }

   @Override
   public T get(String key) {
      return read(m -> m.get(key));
   }

   @Override
   public Future<T> put(String key, T value) {
      return submitWithRetry(key, new PutKeyValueTask<>(id, key, value));
   }

   @Override
   public Future<?> putAll(SortedMap<String, T> values) {
      return submitWithRetry("putAll", new PutAllKeyValueTask<>(id, values));
   }

   @Override
   public Future<T> remove(String key) {
      return submitWithRetry(key, new DeleteKeyValueTask<>(id, key));
   }

   @Override
   public Future<?> removeAll(Set<String> keys) {
      return submitWithRetry("removeAll", new DeleteAllKeyValueTask<>(id, keys));
   }

   @Override
   public Future<T> rename(String oldKey, String newKey, T value) {
      // rename is not idempotent: no retry
      return cluster.submit(id, new RenameKeyValueTask<>(id, oldKey, newKey, value));
   }

   @Override
   public Future<?> replaceAll(SortedMap<String, T> values) {
      return submitWithRetry("replaceAll", new ReplaceAllKeyValueTask<>(id, values));
   }

   /**
    * Submits a task and retries once on failure. Only use for idempotent operations.
    * The returned future will complete exceptionally if both attempts fail.
    */
   private <R extends Serializable> Future<R> submitWithRetry(String opDesc,
                                                               SingletonCallableTask<R> task)
   {
      @SuppressWarnings("unchecked")
      CompletableFuture<R> first = (CompletableFuture<R>) cluster.submit(id, task);
      return first.exceptionallyCompose(ex -> {
         LOG.warn("Failed key-value operation on {}/{}, retrying...", id, opDesc, ex);
         //noinspection unchecked
         return (CompletableFuture<R>) cluster.submit(id, task);
      });
   }

   /**
    * Submits a runnable task and retries once on failure. Only use for idempotent operations.
    * The returned future will complete exceptionally if both attempts fail.
    */
   private Future<?> submitWithRetry(String opDesc, SingletonRunnableTask task) {
      @SuppressWarnings("unchecked")
      CompletableFuture<Object> first = (CompletableFuture<Object>) cluster.submit(id, task);
      return first.exceptionallyCompose(ex -> {
         LOG.warn("Failed key-value operation on {}/{}, retrying...", id, opDesc, ex);
         //noinspection unchecked
         return (CompletableFuture<Object>) cluster.submit(id, task);
      });
   }

   @Override
   public Future<?> deleteStore() {
      return cluster.submit(id, new DeleteKeyValueStorageTask<>(id));
   }

   @Override
   public Stream<KeyValuePair<T>> stream() {
      if(isClosed) {
         return Stream.empty();
      }

      // entrySet() copies the entries, so the cache is read here, inside the retry, and not
      // when the stream is consumed
      return read(Map::entrySet).stream()
         .map(e -> new KeyValuePair<>(e.getKey(), e.getValue()));
   }

   @Override
   public Stream<String> keys() {
      if(isClosed) {
         return Stream.empty();
      }

      return read(Map::keySet).stream();
   }

   @Override
   public int size() {
      return read(Map::size);
   }

   /**
    * Reads the replicated map. Bug #78559, when another node deletes this store, the cache is
    * destroyed on every node, but only the deleting node closes its instance. Ignite reattaches
    * the proxy held here only once a cache with that name exists again, and a read never creates
    * it, so every read would fail until something writes to the store. When a read fails because
    * the cache is stopped, the cache is created again, empty, as a new instance would do, and the
    * read is retried once. During a node shutdown the cache cannot be created, so the original
    * exception is thrown.
    */
   private <R> R read(Function<Map<String, T>, R> operation) {
      Map<String, T> current = map;

      try {
         return operation.apply(current);
      }
      catch(RuntimeException e) {
         if(!isCacheStopped(e)) {
            throw e;
         }

         Map<String, T> reattached;

         try {
            reattached = cluster.getReplicatedMap(mapName);
         }
         catch(RuntimeException ex) {
            e.addSuppressed(ex);
            throw e;
         }

         LOG.debug("Key-value storage {} was deleted on another node, reattached it", id);
         map = reattached;
         return operation.apply(reattached);
      }
   }

   private static boolean isCacheStopped(Throwable e) {
      for(Throwable cause = e; cause != null; cause = cause.getCause()) {
         if(CACHE_STOPPED_EXCEPTION.equals(cause.getClass().getName())) {
            return true;
         }
      }

      return false;
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
   private final LoadKeyValueTask<T> load;
   private volatile Map<String, T> map;
   private final Set<Listener<T>> listeners =
      new ConcurrentSkipListSet<>(Comparator.comparing(Listener::hashCode));
   private final ListenerDelegate listenerDelegate = new ListenerDelegate();
   private volatile boolean isClosed = false;
   private volatile boolean loaded;

   private static final String CACHE_STOPPED_EXCEPTION =
      "org.apache.ignite.internal.processors.cache.CacheStoppedException";
   private static final Logger LOG = LoggerFactory.getLogger(LocalKeyValueStorage.class);

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
