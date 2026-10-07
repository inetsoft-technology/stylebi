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

import inetsoft.sree.security.*;
import inetsoft.storage.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetObject;
import inetsoft.util.*;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Provides storage for asset dependencies info.
 */
@Service
@Lazy
public final class DependencyStorageService {
   DependencyStorageService(KeyValueStorageManager keyValueStorageManager) {
      this.keyValueStorageManager = keyValueStorageManager;
   }

   /**
    * Get DependencyStorageService instance.
    */
   public static DependencyStorageService getInstance() {
      return ConfigurationContext.getContext().getSpringBean(DependencyStorageService.class);
   }

   /**
    * Put a dependenciesInfo to storage.
    *    if key is exist, will replace it.
    */
   public void put(String key, RenameTransformObject obj) throws Exception {
      getDependencyStorage().put(key, obj).get(10L, TimeUnit.SECONDS);
   }

   public RenameTransformObject get(String key) throws Exception {
      return getDependencyStorage().get(key);
   }

   public RenameTransformObject getWithOrg(String key, String orgid) throws Exception {
      return getDependencyStorage(orgid).get(key);
   }

   /**
    * Gets the rename transform tasks that are queued or running. The queue is cluster-global (one
    * store for all organizations); each task carries its organization in its RenameInfos.
    *
    * @return the queue, never {@code null}.
    */
   public RenameTransformQueue getQueue() throws Exception {
      RenameTransformObject queue = getQueueStorage().get(QUEUE_KEY);
      return queue instanceof RenameTransformQueue ? (RenameTransformQueue) queue :
         new RenameTransformQueue();
   }

   /**
    * Opens the cluster-global rename queue store. The first open after a full cluster start
    * replays the tasks left in the queue (see {@link LoadRenameQueueTask}). Waits for the
    * {@value #QUEUE_STORE} singleton service, so it must not be called from a task running on
    * that service or on the {@code renameTransform} service.
    */
   KeyValueStorage<RenameTransformObject> getQueueStorage() {
      return keyValueStorageManager.getStorage(QUEUE_STORE, new LoadRenameQueueTask());
   }

   public boolean rename(String oldKey, String newKey, String organizationId) {
      KeyValueStorage<RenameTransformObject> storage = getDependencyStorage(organizationId);

      oldKey = AssetEntry.createAssetEntry(oldKey)
         .cloneAssetEntry(organizationId, "").toIdentifier();
      newKey = AssetEntry.createAssetEntry(newKey)
         .cloneAssetEntry(organizationId, "").toIdentifier();

      if(!storage.contains(oldKey)) {
         return false;
      }

      if(storage.contains(newKey) && !Tool.equals(oldKey, newKey)) {
         remove(newKey);
      }

      try {
         storage.rename(oldKey, newKey).get(10L, TimeUnit.SECONDS);
      }
      catch(InterruptedException | ExecutionException | TimeoutException e) {
         LOG.error("Failed to rename {} to {}", oldKey, newKey, e);
         return false;
      }

      return true;
   }

   public boolean remove(String key) {
      try {
         getDependencyStorage().remove(key).get(10L, TimeUnit.SECONDS);
      }
      catch(InterruptedException | ExecutionException | TimeoutException e) {
         LOG.error("Failed to remove {}", key, e);
         return false;
      }

      return true;
   }

   public void clear() {
      Set<String> keys = getKeys(null);

      try {
         getDependencyStorage().removeAll(keys).get(1L, TimeUnit.MINUTES);
      }
      catch(InterruptedException | ExecutionException | TimeoutException e) {
         LOG.error("Failed to clear dependency storage", e);
      }
   }

   public void removeDependencyStorage(String orgID) throws Exception {
      getDependencyStorage(orgID).deleteStore().get(1L, TimeUnit.MINUTES);
      getDependencyStorage(orgID).close();
   }

   public void migrateStorageData(Organization oOrg, Organization nOrg, boolean removeOld) throws Exception {
      KeyValueStorage<RenameTransformObject> oStorage = getDependencyStorage(oOrg.getId());
      KeyValueStorage<RenameTransformObject> nStorage = getDependencyStorage(nOrg.getId());
      SortedMap<String, RenameTransformObject> data = new TreeMap<>();
      oStorage.stream().forEach(pair -> {
         AssetEntry entry = AssetEntry.createAssetEntry(pair.getKey());
         String nkey = entry.cloneAssetEntry(nOrg).toIdentifier(true);
         data.put(nkey, syncDependencyData(pair.getValue(), nOrg));
      });

      if(!data.isEmpty()) {
         nStorage.putAll(data).get(5L, TimeUnit.MINUTES);
      }

      if(removeOld) {
         removeDependencyStorage(oOrg.getId());
      }
   }

   public void migrateStorageData(IdentityID oldUser, IdentityID newUser) throws Exception {
      KeyValueStorage<RenameTransformObject> oStorage = getDependencyStorage(oldUser.getOrgID());
      KeyValueStorage<RenameTransformObject> nStorage = getDependencyStorage(newUser.getOrgID());
      SortedMap<String, RenameTransformObject> data = new TreeMap<>();

      oStorage.stream().forEach(pair -> {
         AssetEntry entry = AssetEntry.createAssetEntry(pair.getKey());
         String nkey = entry.cloneAssetEntry(oldUser, newUser).toIdentifier(true);
         data.put(nkey, syncDependencyUser(pair.getValue(), oldUser, newUser));
      });

      if(!data.isEmpty()) {
         nStorage.putAll(data).get(5L, TimeUnit.MINUTES);
      }
   }

   private RenameTransformObject syncDependencyUser(RenameTransformObject obj, IdentityID oldUser,
                                                    IdentityID newUser) {
      if(!(obj instanceof DependenciesInfo dinfo)) {
         return obj;
      }

      dinfo.setDependencies(syncUserDependencies(dinfo.getDependencies(), oldUser, newUser));
      return dinfo;
   }

   private List<AssetObject> syncUserDependencies(List<AssetObject> dependencies,
                                                  IdentityID oldUser, IdentityID newUser) {
      if(dependencies == null || dependencies.isEmpty()) {
         return dependencies;
      }

      return dependencies.stream().map(d -> {
         if(d instanceof AssetEntry old) {

            // if the asset entry base on the renamed user, should change it.
            if(Tool.equals(old.getUser(), oldUser)) {
               return old.cloneAssetEntry(oldUser, newUser);
            }

            // schedule task user is always null, its user is in path.
            if(old.isScheduleTask() && old.getUser() == null) {
               return old.cloneAssetEntry(oldUser, newUser);
            }
         }

         return d;
      }).collect(Collectors.toList());
   }

   public void copyStorageData(Organization oOrg, Organization nOrg) {
      try {
         migrateStorageData(oOrg, nOrg, false);
      }
      catch(Exception e) {
         LOG.error(Catalog.getCatalog().getString("Failed to copy storage from {0} to {1}", oOrg, nOrg));
      }
   }

   private RenameTransformObject syncDependencyData(RenameTransformObject obj, Organization nOrg) {
      if(!(obj instanceof DependenciesInfo dinfo)) {
         return obj;
      }

      dinfo.setDependencies(syncDependencies(dinfo.getDependencies(), nOrg));
      dinfo.setEmbedDependencies(syncDependencies(dinfo.getEmbedDependencies(), nOrg));
      return dinfo;
   }

   private List<AssetObject> syncDependencies(List<AssetObject> dependencies, Organization nOrg) {
      if(dependencies == null || dependencies.isEmpty()) {
         return dependencies;
      }

      return dependencies.stream().map(d -> {
         if(d instanceof AssetEntry) {
            return ((AssetEntry) d).cloneAssetEntry(nOrg);
         }

         return d;
      }).collect(Collectors.toList());
   }

   public Set<String> getKeys(IndexedStorage.Filter filter) {
      return getDependencyStorage().stream()
         .map(KeyValuePair::getKey)
         .filter(k -> filter == null || filter.accept(k))
         .collect(Collectors.toSet());
   }

   @PostConstruct
   void initStorage() {
      getDependencyStorage();
      // Open the rename queue store so that renames left in the queue by the previous cluster
      // run are replayed now, not only at the next rename. Don't wait for it: this service is
      // lazy and may be created on the renameTransform thread, while a waiting
      // RenameTransformTask holds the queue store's service until that thread's rename finishes.
      CompletableFuture.runAsync(() -> {
         try {
            getQueueStorage();
         }
         catch(Exception e) {
            LOG.warn("Failed to open the rename transform queue", e);
         }
      });
   }

   private KeyValueStorage<RenameTransformObject> getDependencyStorage() {
      return getDependencyStorage(null);
   }

   private KeyValueStorage<RenameTransformObject> getDependencyStorage(String orgID) {
      if(orgID == null) {
         orgID = OrganizationManager.getInstance().getCurrentOrgID();
      }

      String storeID = orgID.toLowerCase() + "__" + "dependencyStorage";
      return keyValueStorageManager.getStorage(storeID, new LoadDependencyStorageTask(storeID));
   }

   private final KeyValueStorageManager keyValueStorageManager;

   /**
    * The id of the cluster-global store that holds the rename queue. It is also the id of the
    * singleton service on which all queue changes run.
    */
   static final String QUEUE_STORE = "dependencyStorage";
   /**
    * The key of the rename queue. The "_v2" suffix marks queues written by a version that
    * replays them; see {@link #LEGACY_QUEUE_KEY}.
    */
   static final String QUEUE_KEY = "1^0^__NULL__^rename_queue_v2";
   /**
    * The key of the start counts of the queued tasks ({@link RenameTransformAttempts}).
    */
   static final String ATTEMPTS_KEY = "1^0^__NULL__^rename_queue_v2_attempts";
   /**
    * The key of the rename queue written by older versions, which never replayed it. Its
    * entries may be years old, so they are logged and dropped instead of replayed.
    */
   static final String LEGACY_QUEUE_KEY = "1^0^__NULL__^rename_queue";
   private static final Logger LOG = LoggerFactory.getLogger(DependencyStorageService.class);

}
