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
      Batch batch = BATCH.get();

      if(batch != null && obj instanceof DependenciesInfo info && batch.buffer(key, info)) {
         return;
      }

      getDependencyStorage().put(key, obj).get(10L, TimeUnit.SECONDS);
   }

   public RenameTransformObject get(String key) throws Exception {
      Batch batch = BATCH.get();

      if(batch != null) {
         return batch.get(key);
      }

      return getDependencyStorage().get(key);
   }

   /**
    * Starts a write batch on the calling thread. A shared target (for example a worksheet used by
    * many dashboards) has one record listing all its dependents, and every added dependent used
    * to read and rewrite that whole record, so importing N assets cost O(N x record length).
    * While a batch is open, the {@link #get(String)} / {@link #put(String, RenameTransformObject)}
    * calls of the thread that opened it keep the changed {@link DependenciesInfo} records in memory
    * and {@link Batch#close()} writes each one once. Other threads are not affected.
    *
    * <p>The flush merges with the record stored at that time, so dependents added by someone else
    * while the batch was open are kept. Operations that read or restructure the whole store
    * (rename, remove, clear, getKeys, migrate) flush the open batch first. A nested call returns a
    * no-op handle; only the outermost one flushes.
    *
    * @return the handle, to be closed in a {@code finally}/try-with-resources on the same thread.
    */
   public Batch beginBatch() {
      if(BATCH.get() != null) {
         return new Batch(this, false);
      }

      Batch batch = new Batch(this, true);
      BATCH.set(batch);
      return batch;
   }

   private void flushBatch() {
      Batch batch = BATCH.get();

      if(batch != null) {
         batch.flush();
      }
   }

   /**
    * The in-memory changes of one {@link #beginBatch()} scope.
    */
   public static final class Batch implements AutoCloseable {
      private Batch(DependencyStorageService service, boolean owner) {
         this.service = service;
         this.owner = owner;
      }

      private String orgId() {
         return OrganizationManager.getInstance().getCurrentOrgID().toLowerCase();
      }

      private RenameTransformObject get(String key) throws Exception {
         String orgId = orgId();
         BatchKey bkey = new BatchKey(orgId, key);
         Pending pending = dirty.get(bkey);

         if(pending != null) {
            return pending.info;
         }

         RenameTransformObject obj = service.getDependencyStorage(orgId).get(key);
         // the base of the later merge: what the store held when the caller started changing it
         loaded.put(bkey, obj instanceof DependenciesInfo info ?
            new Base(copy(info.getDependencies()), copy(info.getEmbedDependencies())) :
            new Base(null, null));
         return obj;
      }

      private boolean buffer(String key, DependenciesInfo info) {
         BatchKey bkey = new BatchKey(orgId(), key);
         Pending pending = dirty.get(bkey);

         if(pending != null) {
            pending.info = info;
            return true;
         }

         Base base = loaded.get(bkey);

         if(base == null) {
            // not read through this batch, there is nothing to merge against: write it directly
            return false;
         }

         dirty.put(bkey, new Pending(info, base));
         return true;
      }

      /**
       * Writes the changed records and forgets everything held. A failure is logged and the
       * remaining records are still written.
       */
      public void flush() {
         Map<BatchKey, Pending> records = new LinkedHashMap<>(dirty);
         dirty.clear();
         loaded.clear();

         for(Map.Entry<BatchKey, Pending> e : records.entrySet()) {
            try {
               flush(e.getKey(), e.getValue());
            }
            catch(Exception ex) {
               LOG.error("Failed to save the dependencies of {}, the dependents added by " +
                         "this operation are missing from it", e.getKey().key(), ex);
            }
         }
      }

      private void flush(BatchKey bkey, Pending pending) throws Exception {
         KeyValueStorage<RenameTransformObject> storage =
            service.getDependencyStorage(bkey.orgId());
         RenameTransformObject current = storage.get(bkey.key());
         DependenciesInfo result = pending.info;

         if(current instanceof DependenciesInfo cinfo &&
            !(Objects.equals(nonNull(cinfo.getDependencies()), nonNull(pending.base.dependencies())) &&
              Objects.equals(nonNull(cinfo.getEmbedDependencies()), nonNull(pending.base.embed()))))
         {
            // the record changed while the batch was open, apply only this batch's changes to it
            result = cinfo;
            result.setDependencies(merge(cinfo.getDependencies(), pending.base.dependencies(),
                                         pending.info.getDependencies()));
            result.setEmbedDependencies(merge(cinfo.getEmbedDependencies(), pending.base.embed(),
                                              pending.info.getEmbedDependencies()));
         }

         storage.put(bkey.key(), result).get(10L, TimeUnit.SECONDS);
      }

      private static List<AssetObject> merge(List<AssetObject> current, List<AssetObject> base,
                                             List<AssetObject> changed)
      {
         List<AssetObject> result = new ArrayList<>(nonNull(current));
         Set<AssetObject> baseSet = new HashSet<>(nonNull(base));
         Set<AssetObject> changedSet = new HashSet<>(nonNull(changed));
         result.removeIf(o -> baseSet.contains(o) && !changedSet.contains(o));
         Set<AssetObject> resultSet = new HashSet<>(result);

         for(AssetObject o : nonNull(changed)) {
            if(!baseSet.contains(o) && resultSet.add(o)) {
               result.add(o);
            }
         }

         return result;
      }

      private static List<AssetObject> copy(List<AssetObject> list) {
         return list == null ? null : new ArrayList<>(list);
      }

      private static List<AssetObject> nonNull(List<AssetObject> list) {
         return list == null ? Collections.emptyList() : list;
      }

      @Override
      public void close() {
         if(owner) {
            try {
               flush();
            }
            finally {
               BATCH.remove();
            }
         }
      }

      private record BatchKey(String orgId, String key) {
      }

      private record Base(List<AssetObject> dependencies, List<AssetObject> embed) {
      }

      private static final class Pending {
         Pending(DependenciesInfo info, Base base) {
            this.info = info;
            this.base = base;
         }

         DependenciesInfo info;
         final Base base;
      }

      private final DependencyStorageService service;
      private final boolean owner;
      private final Map<BatchKey, Base> loaded = new HashMap<>();
      private final Map<BatchKey, Pending> dirty = new LinkedHashMap<>();
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
      flushBatch();
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
      flushBatch();

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
      flushBatch();
      Set<String> keys = getKeys(null);

      try {
         getDependencyStorage().removeAll(keys).get(1L, TimeUnit.MINUTES);
      }
      catch(InterruptedException | ExecutionException | TimeoutException e) {
         LOG.error("Failed to clear dependency storage", e);
      }
   }

   public void removeDependencyStorage(String orgID) throws Exception {
      flushBatch();
      getDependencyStorage(orgID).deleteStore().get(1L, TimeUnit.MINUTES);
      getDependencyStorage(orgID).close();
   }

   public void migrateStorageData(Organization oOrg, Organization nOrg, boolean removeOld) throws Exception {
      flushBatch();
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
      flushBatch();
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
      flushBatch();
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
      queueOpen = CompletableFuture.runAsync(() -> {
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

   /**
    * Waits for the queue store opened by the last {@link #initStorage()}. For tests that close
    * the stores: an open still running then would reopen the store after the close.
    */
   void awaitQueueOpen() throws Exception {
      queueOpen.get(30, TimeUnit.SECONDS);
   }

   private static final ThreadLocal<Batch> BATCH = new ThreadLocal<>();
   private final KeyValueStorageManager keyValueStorageManager;
   private volatile CompletableFuture<Void> queueOpen = CompletableFuture.completedFuture(null);

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
