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
import inetsoft.sree.security.OrganizationManager;
import inetsoft.storage.BlobStorage;
import inetsoft.storage.BlobTransaction;
import inetsoft.util.SingletonManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.concurrent.ConcurrentHashMap;

public class DistributedTableCacheStore {
   public DistributedTableCacheStore() {
      storages = new ConcurrentHashMap<>();
   }

   /**
    * Checks if the table data file exists in the blob storage.
    */
   boolean exists(String key) {
      BlobStorage<Metadata> storage = getStorage();
      return storage.exists(key);
   }

   public TableLens load(String key) throws Exception {
      BlobStorage<Metadata> storage = getStorage();
      TableLens lens = (TableLens) new ObjectInputStream(storage.getInputStream(key)).readObject();
      LOG.debug("Loaded lens " + key + " from distributed table cache store");
      return lens;
   }

   void save(String key, TableLens lens) {
      new Thread(() -> {
         BlobStorage<Metadata> storage = getStorage();

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
      }).start();
   }

   private BlobStorage<Metadata> getStorage() {
      String storeID = getStorageId(OrganizationManager.getInstance().getCurrentOrgID());

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

   private final ConcurrentHashMap<String, BlobStorage<Metadata>> storages;
   private static final Logger LOG = LoggerFactory.getLogger(DistributedTableCacheStore.class);

   public static final class Metadata implements Serializable {
   }
}
