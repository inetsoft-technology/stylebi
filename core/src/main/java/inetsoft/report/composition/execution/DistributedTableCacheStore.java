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

package inetsoft.report.composition.execution;

import inetsoft.report.TableLens;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.*;
import inetsoft.storage.BlobStorage;
import inetsoft.storage.BlobTransaction;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;

@SingletonManager.Singleton
public class DistributedTableCacheStore {
   /**
    * Get the distributed table cache store instance
    */
   public static DistributedTableCacheStore getInstance() {
      return SingletonManager.getInstance(DistributedTableCacheStore.class);
   }

   public DistributedTableCacheStore() {
      clusterId = Cluster.getInstance().getId();
      storages = new ConcurrentHashMap<>();
      executor.scheduleAtFixedRate(this::cleanUpCache, 1L, CLEANUP_FREQUENCY_TIME,
                                   TimeUnit.MINUTES);
      this.debouncer = new DefaultDebouncer<>(false);
   }

   /**
    * Checks if the table data file exists in the blob storage.
    */
   boolean exists(DataKey dataKey) {
      BlobStorage<Metadata> storage = getStorage();
      return storage.exists(getKey(dataKey));
   }

   public TableLens get(DataKey dataKey, long touchTime) throws Exception {
      String key = getKey(dataKey);
      BlobStorage<Metadata> storage = getStorage();

      // don't return stale data from the store
      if(touchTime > 0 && storage.getLastModified(key).toEpochMilli() < touchTime) {
         return null;
      }

      TableLens lens = (TableLens) new ObjectInputStream(storage.getInputStream(key)).readObject();
      LOG.debug("Loaded lens " + key + " from distributed table cache store");
      return lens;
   }

   void put(DataKey dataKey, TableLens lens) {
      String key = getKey(dataKey);
      BlobStorage<Metadata> storage = getStorage();

      debouncer.debounce(key, 1L, TimeUnit.SECONDS, () -> {
         try(BlobTransaction<Metadata> tx = storage.beginTransaction();
             OutputStream out = tx.newStream(key, null);
             ObjectOutputStream oos = new ObjectOutputStream(out))
         {
            oos.writeObject(lens);
            oos.flush();
            tx.commit();
         }
         catch(IOException ex) {
            LOG.error("Failed to write to the blob storage: {}", key, ex);
         }
      });
   }

   private void cleanUpCache() {
      SecurityProvider provider = SecurityEngine.getSecurity().getSecurityProvider();
      String[] orgIds = provider.getOrganizationIDs();
      Instant validInstant = Instant.now().minus(CACHE_EXPIRATION_TIME, ChronoUnit.MINUTES);

      for(String orgId : orgIds) {
         BlobStorage<Metadata> storage = getStorage(getStorageId(orgId));
         Set<String> keysToRemove = new HashSet<>();
         storage.paths().forEach(key -> {
            try {
               if(!key.startsWith(clusterId) || storage.getLastModified(key).isBefore(validInstant)) {
                  keysToRemove.add(key);
               }
            }
            catch(FileNotFoundException e) {
               // ignore
            }
         });

         // TODO: add BlobStorage.removeAll() and DeleteAllBlobTask
         for(String key : keysToRemove) {
            try {
               storage.delete(key);
            }
            catch(IOException e) {
               throw new RuntimeException(e);
            }
         }
      }
   }

   private BlobStorage<Metadata> getStorage() {
      return getStorage(getStorageId(OrganizationManager.getInstance().getCurrentOrgID()));
   }

   private BlobStorage<Metadata> getStorage(String storeID) {
      if(storages.containsKey(storeID) && !storages.get(storeID).isClosed()) {
         return storages.get(storeID);
      }
      else {
         BlobStorage<Metadata> storage = SingletonManager.getInstance(BlobStorage.class, storeID, false);
         storages.put(storeID, storage);
         return storage;
      }
   }

   private String getStorageId(String orgId) {
      return orgId.toLowerCase() + "__" + "tableCacheStore";
   }

   private String getKey(DataKey dataKey) {
      return clusterId + "__" + dataKey.getValue();
   }

   private final String clusterId;
   private final ConcurrentHashMap<String, BlobStorage<Metadata>> storages;
   private final ScheduledExecutorService executor =
      Executors.newSingleThreadScheduledExecutor(r -> new GroupedThread(r, "DistributedTableCacheStore"));
   private final Debouncer<String> debouncer;

   private static final long CACHE_EXPIRATION_TIME = 30L; // minutes
   private static final long CLEANUP_FREQUENCY_TIME = 30L; // minutes
   private static final Logger LOG = LoggerFactory.getLogger(DistributedTableCacheStore.class);

   public static final class Metadata implements Serializable {
   }
}
