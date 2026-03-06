/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

import com.github.benmanes.caffeine.cache.*;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.util.function.Function;

/**
 * Manages the pool of {@link KeyValueStorage} instances keyed by store ID.
 * In a Spring context, constructor-injecting {@link KeyValueEngine} ensures the engine is
 * started before any storage is accessed. In non-Spring environments the no-arg constructor
 * is used by {@code SingletonManager}.
 *
 * <p>At most {@value #MAX_SIZE} stores are held open simultaneously. When the limit is
 * reached the least-recently-used store is closed and evicted. This bounds memory use and
 * prevents stale references to stores for organizations that have been removed.</p>
 */
@Service
public class KeyValueStorageManager implements AutoCloseable {

   /**
    * No-arg constructor used by {@code SingletonManager} in non-Spring environments.
    */
   public KeyValueStorageManager() {
   }

   /**
    * Spring constructor — ensures {@link KeyValueEngine} is initialized before this manager.
    *
    * @param keyValueEngine the engine (injected only for startup-ordering purposes).
    */
   @Autowired
   public KeyValueStorageManager(KeyValueEngine keyValueEngine) {
   }

   /**
    * Gets or creates the {@link KeyValueStorage} with the given store ID.
    *
    * @param id  the store identifier.
    * @param <T> the value type.
    *
    * @return the storage instance.
    */
   public <T extends Serializable> KeyValueStorage<T> getInstance(String id) {
      return get(id, KeyValueStorage::newInstance);
   }

   /**
    * Gets or creates the {@link KeyValueStorage} with the given store ID, using the supplied load
    * task when creating a new instance.
    *
    * @param id   the store identifier.
    * @param task the task used to initially load the storage.
    * @param <T>  the value type.
    *
    * @return the storage instance.
    */
   public <T extends Serializable> KeyValueStorage<T> getInstance(
      String id, LoadKeyValueTask<T> task)
   {
      return get(id, storeId -> KeyValueStorage.newInstance(storeId, task));
   }

   @Override
   @PreDestroy
   public void close() {
      storages.asMap().values().forEach(s -> {
         try {
            s.close();
         }
         catch(Exception e) {
            LOG.error("Failed to close KeyValueStorage", e);
         }
      });
      storages.invalidateAll();
   }

   /**
    * Gets or creates the storage for the given ID, retrying if the cached instance has been
    * closed externally (e.g., by {@code BlobStorage.close()}).
    */
   @SuppressWarnings("unchecked")
   private <T extends Serializable> KeyValueStorage<T> get(
      String id, Function<String, KeyValueStorage<?>> loader)
   {
      while(true) {
         KeyValueStorage<T> storage = (KeyValueStorage<T>) storages.get(id, loader);

         if(!storage.isClosed()) {
            return storage;
         }

         // Storage was closed externally; evict it so the next iteration creates a fresh one.
         storages.asMap().remove(id, storage);
      }
   }

   private static final int MAX_SIZE = 50;

   private final Cache<String, KeyValueStorage<?>> storages = Caffeine.newBuilder()
      .maximumSize(MAX_SIZE)
      .removalListener((String id, KeyValueStorage<?> storage, RemovalCause cause) -> {
         if(storage != null && !storage.isClosed()) {
            try {
               storage.close();
            }
            catch(Exception e) {
               LOG.error("Failed to close evicted KeyValueStorage '{}'", id, e);
            }
         }
      })
      .build();

   private static final Logger LOG = LoggerFactory.getLogger(KeyValueStorageManager.class);
}
