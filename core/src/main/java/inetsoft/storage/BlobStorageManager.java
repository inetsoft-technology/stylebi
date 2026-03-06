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

import java.io.*;

/**
 * Manages the pool of {@link BlobStorage} instances keyed by store ID.
 * Constructor-injecting {@link BlobEngine} and {@link KeyValueStorageManager} ensures proper
 * startup ordering in a Spring context. In non-Spring environments the no-arg constructor is
 * used by {@code SingletonManager}.
 *
 * <p>At most {@value #MAX_SIZE} stores are held open simultaneously. When the limit is
 * reached the least-recently-used store is closed and evicted. This bounds memory use and
 * prevents stale references to stores for organizations that have been removed.</p>
 */
@Service
public class BlobStorageManager implements AutoCloseable {

   /**
    * No-arg constructor used by {@code SingletonManager} in non-Spring environments.
    */
   public BlobStorageManager() {
   }

   /**
    * Spring constructor — ensures {@link BlobEngine} and {@link KeyValueStorageManager} are
    * initialized before this manager.
    *
    * @param blobEngine       the blob engine (injected for startup-ordering).
    * @param kvStorageManager the key-value storage manager (injected for startup-ordering).
    */
   @Autowired
   public BlobStorageManager(BlobEngine blobEngine, KeyValueStorageManager kvStorageManager) {
   }

   /**
    * Gets or creates the {@link BlobStorage} with the given store ID.
    *
    * @param storeId the store identifier.
    * @param preload {@code true} to preload the storage contents on first access.
    * @param <T>     the extended metadata type.
    *
    * @return the storage instance.
    */
   public <T extends Serializable> BlobStorage<T> getInstance(String storeId, boolean preload) {
      return getInstance(storeId, preload, null);
   }

   /**
    * Gets or creates the {@link BlobStorage} with the given store ID, optionally attaching a
    * listener when the storage is first created.
    *
    * @param storeId  the store identifier.
    * @param preload  {@code true} to preload the storage contents on first access.
    * @param listener a listener to add the first time this storage is created, or {@code null}.
    * @param <T>      the extended metadata type.
    *
    * @return the storage instance.
    */
   @SuppressWarnings("unchecked")
   public <T extends Serializable> BlobStorage<T> getInstance(
      String storeId, boolean preload, BlobStorage.Listener<T> listener)
   {
      while(true) {
         boolean[] created = { false };
         BlobStorage<T> storage = (BlobStorage<T>) storages.get(storeId, id -> {
            created[0] = true;

            try {
               return BlobStorage.createBlobStorage(id, preload);
            }
            catch(IOException e) {
               throw new UncheckedIOException(e);
            }
         });

         if(storage.isClosed()) {
            // Storage was closed externally; evict it so the next iteration creates a fresh one.
            storages.asMap().remove(storeId, storage);
            continue;
         }

         if(created[0] && listener != null) {
            storage.addListener(listener);
         }

         return storage;
      }
   }

   @Override
   @PreDestroy
   public void close() {
      storages.asMap().values().forEach(s -> {
         try {
            s.close();
         }
         catch(Exception e) {
            LOG.error("Failed to close BlobStorage", e);
         }
      });
      storages.invalidateAll();
   }

   private static final int MAX_SIZE = 50;

   private final Cache<String, BlobStorage<?>> storages = Caffeine.newBuilder()
      .maximumSize(MAX_SIZE)
      .removalListener((String id, BlobStorage<?> storage, RemovalCause cause) -> {
         if(storage != null && !storage.isClosed()) {
            try {
               storage.close();
            }
            catch(Exception e) {
               LOG.error("Failed to close evicted BlobStorage '{}'", id, e);
            }
         }
      })
      .build();

   private static final Logger LOG = LoggerFactory.getLogger(BlobStorageManager.class);
}
