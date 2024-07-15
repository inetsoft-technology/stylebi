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
package inetsoft.uql.asset.sync;

import inetsoft.sree.security.OrganizationManager;
import inetsoft.storage.KeyValuePair;
import inetsoft.storage.KeyValueStorage;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Provides storage for asset dependencies info.
 */
@SingletonManager.Singleton(DependencyStorageService.Reference.class)
public final class DependencyStorageService implements AutoCloseable {
   private DependencyStorageService() {
   }

   /**
    * Get DependencyStorageService instance.
    */
   public static DependencyStorageService getInstance() {
      return SingletonManager.getInstance(DependencyStorageService.class);
   }

   /**
    * Put a dependenciesInfo to storage.
    *    if key is exist, will replace it.
    */
   public void put(String key, RenameTransformObject obj) throws Exception {
      getDependencyStorage().put(key, obj).get();
   }

   public RenameTransformObject get(String key) throws Exception {
      return getDependencyStorage().get(key);
   }

   public RenameTransformObject getWithOrg(String key, String orgid) throws Exception {
      return getDependencyStorage(orgid).get(key);
   }

   public RenameTransformQueue getQueue() throws Exception {
      return (RenameTransformQueue) getDependencyStorage().get(QUEUE_KEY);
   }

   /**
    * Rename key.
    */
   public boolean rename(String oldKey, String newKey) {
      KeyValueStorage<RenameTransformObject> storage = getDependencyStorage();

      if(!storage.contains(oldKey)) {
         return false;
      }

      if(storage.contains(newKey) && !Tool.equals(oldKey, newKey)) {
         remove(newKey);
      }

      try {
         storage.rename(oldKey, newKey).get();
      }
      catch(InterruptedException | ExecutionException e) {
         LOG.error("Failed to rename {} to {}", oldKey, newKey, e);
         return false;
      }

      return true;
   }

   public boolean remove(String key) {
      try {
         getDependencyStorage().remove(key).get();
      }
      catch(InterruptedException | ExecutionException e) {
         LOG.error("Failed to remove {}", key, e);
         return false;
      }

      return true;
   }

   public void clear() {
      Set<String> keys = getKeys(null);
      keys.stream().forEach(key -> getDependencyStorage().remove(key));
   }

   public void removeDependencyStorage(String orgID) throws Exception {
      getDependencyStorage(orgID).deleteStore();
      getDependencyStorage(orgID).close();
   }

   public void migrateStorageData(String oId, String id) throws Exception {
      KeyValueStorage<RenameTransformObject> oStorage = getDependencyStorage(oId);
      KeyValueStorage<RenameTransformObject> nStorage = getDependencyStorage(id);
      SortedMap<String, RenameTransformObject> data = new TreeMap<>();
      oStorage.stream().forEach(pair -> data.put(pair.getKey(), pair.getValue()));
      nStorage.putAll(data);
      removeDependencyStorage(oId);
   }

   public void copyStorageData(String oId, String id) {
      KeyValueStorage<RenameTransformObject> oStorage = getDependencyStorage(oId);
      KeyValueStorage<RenameTransformObject> nStorage = getDependencyStorage(id);
      SortedMap<String, RenameTransformObject> data = new TreeMap<>();
      oStorage.stream().forEach(pair -> data.put(pair.getKey(), pair.getValue()));
      nStorage.putAll(data);
   }

   public Set<String> getKeys(IndexedStorage.Filter filter) {
      return getDependencyStorage().stream()
         .map(KeyValuePair::getKey)
         .filter(k -> filter == null || filter.accept(k))
         .collect(Collectors.toSet());
   }

   @Override
   public void close() throws Exception {
      getDependencyStorage().close();
   }

   private void initStorage() {
      getDependencyStorage();
   }

   private KeyValueStorage<RenameTransformObject> getDependencyStorage() {
      return getDependencyStorage(null);
   }

   private KeyValueStorage<RenameTransformObject> getDependencyStorage(String orgID) {
      if(orgID == null) {
         orgID = OrganizationManager.getInstance().getCurrentOrgID();
      }
      else {
         orgID = orgID;
      }
      String storeID = orgID.toLowerCase() + "__" + "dependencyStorage";
      Supplier<LoadDependencyStorageTask> supplier = () -> new LoadDependencyStorageTask(storeID);
      return SingletonManager.getInstance(KeyValueStorage.class, storeID, supplier);
   }

   static final String QUEUE_KEY = "1^0^__NULL__^rename_queue";
   private static final Logger LOG = LoggerFactory.getLogger(DependencyStorageService.class);

   public static final class Reference extends SingletonManager.Reference<DependencyStorageService> {
      @Override
      public synchronized DependencyStorageService get(Object... parameters) {
         if(dependencyIndexedStorage == null) {
            dependencyIndexedStorage = new DependencyStorageService();
            dependencyIndexedStorage.initStorage();
         }

         return dependencyIndexedStorage;
      }

      @Override
      public void dispose() {
         if(dependencyIndexedStorage != null) {
            try {
               dependencyIndexedStorage.close();
            }
            catch(Exception e) {
               LOG.error("Unable to close dependency storage", e);
            }
            finally {
               dependencyIndexedStorage = null;
            }
         }
      }

      private DependencyStorageService dependencyIndexedStorage;
   }
}
