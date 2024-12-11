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
import inetsoft.storage.KeyValuePair;
import inetsoft.storage.KeyValueStorage;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetObject;
import inetsoft.uql.asset.internal.AssetFolder;
import inetsoft.util.*;
import inetsoft.util.migrate.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
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
      getDependencyStorage().removeAll(keys);
   }

   public void removeDependencyStorage(String orgID) throws Exception {
      getDependencyStorage(orgID).deleteStore();
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
      nStorage.putAll(data);

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

      nStorage.putAll(data);
   }

   private RenameTransformObject syncDependencyUser(RenameTransformObject obj, IdentityID oldUser,
                                                    IdentityID newUser) {
      if(!(obj instanceof DependenciesInfo)) {
         return obj;
      }

      DependenciesInfo dinfo = (DependenciesInfo) obj;
      dinfo.setDependencies(syncUserDependencies(dinfo.getDependencies(), oldUser, newUser));
      return dinfo;
   }

   private List<AssetObject> syncUserDependencies(List<AssetObject> dependencies,
                                                  IdentityID oldUser, IdentityID newUser) {
      if(dependencies == null || dependencies.isEmpty()) {
         return dependencies;
      }

      return dependencies.stream().map(d -> {
         if(d instanceof AssetEntry) {
            AssetEntry old = (AssetEntry) d;

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
      if(!(obj instanceof DependenciesInfo)) {
         return obj;
      }

      DependenciesInfo dinfo = (DependenciesInfo) obj;
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
