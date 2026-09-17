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
package inetsoft.web.admin.file;

import inetsoft.sree.RepletRegistry;
import inetsoft.sree.RepletRegistryManager;
import inetsoft.sree.security.SecurityProvider;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetFolder;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.util.IndexedStorage;
import inetsoft.util.MetadataAwareStorage;
import inetsoft.util.ThreadPool;
import inetsoft.web.admin.content.repository.ContentRepositoryTreeService;
import inetsoft.web.security.auth.MissingResourceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * {@code FileService} implements the repository-maintenance business logic shared by the
 * admin-ai plugin (community) and, via a thin delegating wrapper, the enterprise Public REST API
 * ({@code FileApiService}). These operations are deployment-wide (no organization scoping), so
 * there is no organization-switching concern to preserve across that split.
 */
@Service
public class FileService {
   @Autowired
   public FileService(ContentRepositoryTreeService contentService, SecurityProvider securityProvider,
                       IndexedStorage indexedStorage, RepletRegistryManager repletRegistryManager)
   {
      this.contentService = contentService;
      this.securityProvider = securityProvider;
      this.indexedStorage = indexedStorage;
      this.repletRegistryManager = repletRegistryManager;
   }

   /**
    * Starts rebuilding the dependency graph.
    *
    * @param timeout   the timeout for obtaining the rebuild graph lock in milliseconds.
    * @param principal a principal that identifies the remote user.
    *
    * @return the token for the rebuild task.
    */
   public String rebuildDependencies(long timeout, Principal principal) {
      String token = UUID.randomUUID().toString().replace("-", "");
      CompletableFuture<Void> future = new CompletableFuture<>();
      rebuildTasks.put(token, future);

      ThreadPool.addOnDemand(() -> {
         try {
            UpdateAssetDependenciesHandler.getInstance().rebuild(timeout, TimeUnit.MILLISECONDS);
            future.complete(null);
         }
         catch(Throwable e) {
            future.completeExceptionally(e);
         }
      });

      return token;
   }

   /**
    * Gets the status of a rebuild dependencies task.
    *
    * @param token the token for the rebuild task.
    *
    * @return the task status.
    *
    * @throws MissingResourceException if the task does not exist.
    */
   public RebuildDependenciesStatus getRebuildDependenciesStatus(String token)
      throws MissingResourceException
   {
      CompletableFuture<?> future = rebuildTasks.get(token);

      if(future == null) {
         throw new MissingResourceException(token);
      }

      if(future.isDone()) {
         rebuildTasks.remove(token);
      }

      RebuildDependenciesStatus response = new RebuildDependenciesStatus();
      response.setToken(token);

      if(future.isDone()) {
         response.setComplete(true);

         try {
            future.get();
         }
         catch(Throwable e) {
            StringWriter buffer = new StringWriter();
            PrintWriter writer = new PrintWriter(buffer);
            e.printStackTrace(writer);
            writer.flush();

            response.setFailed(true);
            response.setError(buffer.toString());
         }
      }

      return response;
   }

   /**
    * Starts repairing the repository folder tree.
    *
    * @param principal a principal that identifies the remote user.
    *
    * @return the token for the repair task.
    */
   public String repairRepositoryFolders(Principal principal) {
      String token = UUID.randomUUID().toString().replace("-", "");
      CompletableFuture<Void> future = new CompletableFuture<>();
      repairTasks.put(token, future);

      ThreadPool.addOnDemand(() -> {
         try {
            repairRepositoryFolders();
            future.complete(null);
         }
         catch(Throwable e) {
            future.completeExceptionally(e);
         }
      });

      return token;
   }

   /**
    * Checks whether a repair repository folders task has finished, without consuming its
    * completion record -- unlike {@link #getRepairRepositoryFoldersStatus}, this never removes
    * the task's entry from {@link #repairTasks}.
    *
    * @param token the token for the repair task.
    *
    * @return {@code true} if the task has completed (successfully or not), {@code false} if it
    *         is still running.
    *
    * @throws MissingResourceException if the task does not exist.
    */
   public boolean isRepairRepositoryFoldersComplete(String token) throws MissingResourceException {
      CompletableFuture<?> future = repairTasks.get(token);

      if(future == null) {
         throw new MissingResourceException(token);
      }

      return future.isDone();
   }

   /**
    * Gets the status of a repair repository folders task.
    *
    * @param token the token for the repair task.
    *
    * @return the task status.
    *
    * @throws MissingResourceException if the task does not exist.
    */
   public RepairRepositoryFoldersStatus getRepairRepositoryFoldersStatus(String token)
      throws MissingResourceException
   {
      CompletableFuture<?> future = repairTasks.get(token);

      if(future == null) {
         throw new MissingResourceException(token);
      }

      if(future.isDone()) {
         repairTasks.remove(token);
      }

      RepairRepositoryFoldersStatus response = new RepairRepositoryFoldersStatus();
      response.setToken(token);

      if(future.isDone()) {
         response.setComplete(true);

         try {
            future.get();
         }
         catch(Throwable e) {
            StringWriter buffer = new StringWriter();
            PrintWriter writer = new PrintWriter(buffer);
            e.printStackTrace(writer);
            writer.flush();

            response.setFailed(true);
            response.setError(buffer.toString());
         }
      }

      return response;
   }

   /**
    * Repairs the repository folder tree synchronously. Exposed as {@code public} (it was
    * {@code private} before this class existed) so that {@code FileApiService.restoreStorage}
    * -- which stays in enterprise -- can invoke the same repair logic inline as part of a
    * storage restore, without going through the async token-based {@link #repairRepositoryFolders}
    * kick-off.
    *
    * @throws Exception if the repository folder tree could not be repaired.
    */
   public void repairRepositoryFolders() throws Exception {
      IndexedStorage indexedStorage = this.indexedStorage;
      boolean metadataAware = (indexedStorage instanceof MetadataAwareStorage) &&
         ((MetadataAwareStorage) indexedStorage).isMetadataEnabled();
      Map<AssetEntry, List<AssetEntry>> parents = contentService.getParentAssetEntryMap();

      for(Map.Entry<AssetEntry, List<AssetEntry>> e : parents.entrySet()) {
         AssetEntry parentEntry = e.getKey();
         RepletRegistry registry = repletRegistryManager.getRegistry(parentEntry.getOrgID());
         String parentIdentifier = parentEntry.toIdentifier();
         AssetFolder folder;

         if(parentEntry.isScheduleTaskFolder() && parentIdentifier.equals("1^6^__NULL__^")) {
            parentIdentifier = "1^6^__NULL__^/";
         }

         if(metadataAware) {
            folder = ((MetadataAwareStorage) indexedStorage).getAssetFolder(parentIdentifier);
         }
         else {
            folder = (AssetFolder) indexedStorage.getXMLSerializable(parentIdentifier, null);
         }

         if(folder == null) {
            folder = new AssetFolder();
            List<AssetEntry> children = e.getValue();
            children.removeIf(AssetEntry::isReplet);

            for(AssetEntry child : children) {
               folder.addEntry(child);
            }

            if(!parentEntry.isRoot()) {
               addToParent(parentEntry, indexedStorage);
            }

            indexedStorage.putXMLSerializable(parentIdentifier, folder);
         }
         else {
            List<AssetEntry> children = e.getValue();
            children.removeAll(Arrays.asList(folder.getEntries()));
            children.removeIf(AssetEntry::isReplet);

            if(!children.isEmpty()) {
               for(AssetEntry child : children) {
                  folder.addEntry(child);

                  if(child.isRepositoryFolder()) {
                     addNestedFoldersToRegistry(child, indexedStorage, registry);
                  }
               }

               indexedStorage.putXMLSerializable(parentIdentifier, folder);
            }
         }

         registry.save();
      }

      repairRootDataSourceFolder(indexedStorage);
      repairScheduleTaskFolders(indexedStorage, metadataAware);
   }

   private void addNestedFoldersToRegistry(AssetEntry parent, IndexedStorage indexedStorage,
                                           RepletRegistry registry) throws Exception
   {
      registry.addFolder(parent.getPath(), true);
      AssetFolder folder = (AssetFolder) indexedStorage.getXMLSerializable(parent.toIdentifier(), null);

      if(folder == null) {
         return;
      }

      AssetEntry[] children = folder.getEntries();

      for(AssetEntry child : children) {
         if(child.isRepositoryFolder()) {
            addNestedFoldersToRegistry(child, indexedStorage, registry);
         }
      }
   }

   private void addToParent(AssetEntry parentEntry, IndexedStorage indexedStorage) throws Exception {
      if(parentEntry == null || parentEntry.isScheduleTaskFolder() && "".equals(parentEntry.getPath())) {
         return;
      }

      boolean metadataAware = (indexedStorage instanceof MetadataAwareStorage) &&
         ((MetadataAwareStorage) indexedStorage).isMetadataEnabled();
      AssetEntry grandParentEntry = parentEntry.getParent();
      String grandParentIdentifier = grandParentEntry.toIdentifier();
      AssetFolder grandParentFolder;

      if(metadataAware) {
         grandParentFolder = ((MetadataAwareStorage) indexedStorage).getAssetFolder(grandParentIdentifier);
      }
      else {
         grandParentFolder = (AssetFolder) indexedStorage.getXMLSerializable(grandParentIdentifier, null);
      }

      if(grandParentFolder != null && !grandParentFolder.containsEntry(parentEntry)) {
         grandParentFolder.addEntry(parentEntry);
         indexedStorage.putXMLSerializable(grandParentIdentifier, grandParentFolder);
      }
   }

   private void repairRootDataSourceFolder(IndexedStorage indexedStorage) throws Exception {
      String[] orgIds = securityProvider.getOrganizationIDs();

      for(String orgId : orgIds) {
         AssetEntry rootEntry = new AssetEntry(
            AssetRepository.QUERY_SCOPE, AssetEntry.Type.DATA_SOURCE_FOLDER, "/", null,
            orgId);
         String rootIdentifier = rootEntry.toIdentifier();
         AssetFolder rootFolder = (AssetFolder) indexedStorage.getXMLSerializable(
            rootIdentifier, null, orgId);

         if(rootFolder == null) {
            rootFolder = new AssetFolder();
         }

         Set<String> dsKeys = indexedStorage.getKeys((key) -> !rootIdentifier.equals(key) &&
            DataSourceRegistry.matchesDataSourceFilter(key), orgId);

         for(String dsKey : dsKeys) {
            AssetEntry dsEntry = AssetEntry.createAssetEntry(dsKey);

            if(!rootFolder.containsEntry(dsEntry)) {
               rootFolder.addEntry(dsEntry);
            }
         }

         indexedStorage.putXMLSerializable(rootIdentifier, rootFolder);
      }
   }

   private void repairScheduleTaskFolders(IndexedStorage indexedStorage, boolean metadataAware) throws Exception {
      Set<AssetEntry> tasks = new HashSet<>();
      String rootIdentifier = "1^6^__NULL__^/";
      repairScheduleTaskFolder(indexedStorage, metadataAware, tasks, rootIdentifier);
   }

   private void repairScheduleTaskFolder(IndexedStorage indexedStorage, boolean metadataAware,
                                         Set<AssetEntry> tasks, String parentIdentifier) throws Exception
   {
      AssetFolder folder;
      List<AssetEntry> newTasks = new ArrayList<>();

      if(metadataAware) {
         folder = ((MetadataAwareStorage) indexedStorage).getAssetFolder(parentIdentifier);
      }
      else {
         folder = (AssetFolder) indexedStorage.getXMLSerializable(parentIdentifier, null);
      }

      if(folder == null) {
         return;
      }

      List<AssetEntry> folders = new ArrayList<>();

      for(AssetEntry child : folder.getEntries()) {
         if(child.isScheduleTaskFolder()) {
            folders.add(child);
         }
         else {
            newTasks.add(child);
         }
      }

      for(AssetEntry childFolder : folders) {
         repairScheduleTaskFolder(indexedStorage, metadataAware, tasks, childFolder.toIdentifier());
      }

      for(AssetEntry task : newTasks) {
         if(tasks.contains(task)) {
            folder.removeEntry(task);
         }
         else {
            tasks.add(task);
         }
      }

      indexedStorage.putXMLSerializable(parentIdentifier, folder);
   }

   private final ContentRepositoryTreeService contentService;
   private final SecurityProvider securityProvider;
   private final IndexedStorage indexedStorage;
   private final RepletRegistryManager repletRegistryManager;
   private final ConcurrentMap<String, CompletableFuture<?>> rebuildTasks =
      new ConcurrentHashMap<>();
   private final ConcurrentMap<String, CompletableFuture<?>> repairTasks =
      new ConcurrentHashMap<>();
}
