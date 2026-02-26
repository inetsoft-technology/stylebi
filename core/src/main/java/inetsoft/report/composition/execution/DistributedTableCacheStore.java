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
import inetsoft.sree.security.OrganizationManager;
import inetsoft.storage.BlobStorage;
import inetsoft.storage.BlobTransaction;
import inetsoft.uql.XTable;
import inetsoft.util.*;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.security.Principal;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipException;

@SingletonManager.Singleton
public class DistributedTableCacheStore {
   /**
    * Get the distributed table cache store instance
    */
   public static DistributedTableCacheStore getInstance() {
      return SingletonManager.getInstance(DistributedTableCacheStore.class);
   }

   public DistributedTableCacheStore() {
      Cluster cluster = Cluster.getInstance();
      clusterId = cluster.getId();
      storages = new ConcurrentHashMap<>();

      // Use distributed executor so cleanup only runs on one node in the cluster
      if(cluster.getLong(COUNTER_NAME).getAndIncrement() == 0) {
         cluster.getScheduledExecutor().scheduleAtFixedRate(
            new CleanupTableCacheTask(), 1L, CLEANUP_FREQUENCY_TIME,
            TimeUnit.MINUTES);
      }

      this.debouncer = new DefaultDebouncer<>(false);
   }

   /**
    * Checks if the table data file exists in the blob storage.
    */
   boolean exists(DataKey dataKey) {
      if(dataKey.isLocalCacheOnly()) {
         return false;
      }

      BlobStorage<Metadata> storage = getStorage();
      return storage.exists(getKey(dataKey));
   }

   public TableLens get(DataKey dataKey, long touchTime) throws Exception {
      String key = getKey(dataKey);
      BlobStorage<Metadata> storage = getStorage();
      TableLens lens = null;

      // don't return stale data from the store
      if(touchTime > 0 && storage.getLastModified(key).toEpochMilli() < touchTime) {
         return null;
      }

      try(InputStream storageInputStream = storage.getInputStream(key);
          GZIPInputStream gzipIn = new GZIPInputStream(storageInputStream);
          ObjectInputStream ois = new ObjectInputStream(gzipIn))
      {
         lens = (TableLens) ois.readObject();
         LOG.debug("Loaded lens {} from distributed table cache store", key);
      }
      catch(ZipException ex) {
         // Uncompressed entry written before this change; delete it so subsequent
         // get() calls don't repeatedly open and fail the GZIP check.
         LOG.debug("Cached lens {} is not compressed, treating as cache miss and deleting stale entry", key);

         try {
            storage.delete(key);
         }
         catch(IOException deleteEx) {
            LOG.debug("Failed to delete stale uncompressed entry {}", key, deleteEx);
         }
      }

      return lens;
   }

   void put(DataKey dataKey, TableLens lens) {
      if(dataKey.isLocalCacheOnly()) {
         return;
      }

      String key = getKey(dataKey);
      BlobStorage<Metadata> storage = getStorage();
      final Principal principal = ThreadContext.getContextPrincipal();

      debouncer.debounce(key, 1L, TimeUnit.SECONDS, () -> {
         ThreadContext.setContextPrincipal(principal);

         try(BlobTransaction<Metadata> tx = storage.beginTransaction()) {
            try(OutputStream out = tx.newStream(key, null);
                GZIPOutputStream gzipOut = new GZIPOutputStream(out);
                ObjectOutputStream oos = new ObjectOutputStream(gzipOut))
            {
               // get all rows before writing
               lens.moreRows(XTable.EOT);
               oos.writeObject(lens);
            }

            // streams are fully closed (GZIP trailer written) before commit
            tx.commit();
         }
         catch(IOException ex) {
            LOG.error("Failed to write to the blob storage: {}", key, ex);
         }
         finally {
            ThreadContext.setContextPrincipal(null);
         }
      });
   }

   void remove(DataKey dataKey) {
      if(dataKey.isLocalCacheOnly()) {
         return;
      }

      String key = getKey(dataKey);
      BlobStorage<Metadata> storage = getStorage();

      try {
         storage.delete(key);
      }
      catch(IOException e) {
         LOG.warn("Failed to remove data from cache: {}", key, e);
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
      return orgId.toLowerCase() + "__tableCacheStore";
   }

   private String getKey(DataKey dataKey) {
      return clusterId + "__" + DigestUtils.sha256Hex(dataKey.getValue());
   }

   private final String clusterId;
   private final ConcurrentHashMap<String, BlobStorage<Metadata>> storages;
   private final Debouncer<String> debouncer;

   private static final long CLEANUP_FREQUENCY_TIME = 30L; // minutes
   private static final String COUNTER_NAME = DistributedTableCacheStore.class.getName() + ".counter";
   private static final Logger LOG = LoggerFactory.getLogger(DistributedTableCacheStore.class);

   public static final class Metadata implements Serializable {
   }
}
