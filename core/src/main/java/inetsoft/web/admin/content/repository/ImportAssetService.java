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

package inetsoft.web.admin.content.repository;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import inetsoft.cluster.*;
import inetsoft.sree.RepositoryEntry;
import inetsoft.sree.internal.DeployManagerService;
import inetsoft.sree.internal.DeploymentInfo;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.SRPrincipal;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.util.*;
import inetsoft.web.admin.content.repository.model.ExportedAssetsModel;
import inetsoft.web.admin.content.repository.model.ImportAssetResponse;
import inetsoft.web.admin.deploy.*;
import inetsoft.web.admin.model.FileData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;

import java.io.*;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@ClusterProxy
@Component
public class ImportAssetService {
   public ImportAssetService(DeployService deployService) {
      this.deployService = deployService;
      this.importCache = Caffeine.newBuilder()
         .expireAfterAccess(10L, TimeUnit.MINUTES)
         .maximumSize(1000L)
         .build();
      this.contexts = Cluster.getInstance().getMap(CACHE_NAME);
   }

   @ClusterProxyMethod(CACHE_NAME)
   public ExportedAssetsModel setJarFile(@ClusterProxyKey String importId, FileData file,
                                         Principal principal) throws Exception
   {
      FileSystemService fileService = FileSystemService.getInstance();
      File temp = fileService.getCacheTempFile("import", "zip");
      fileService.remove(temp, 600000);

      try(OutputStream output = new FileOutputStream(temp)) {
         ByteArrayInputStream input =
            new ByteArrayInputStream(Base64.getDecoder().decode(file.content()));
         Tool.copyTo(input, output);
      }

      ImportJarProperties properties = deployService.setJarFile(temp.getAbsolutePath(), false);
      ImportAssetContext context = new ImportAssetContext(importId);
      context.setProperties(properties);
      contexts.put(importId, context);
      return getJarFileInfo(importId, principal);
   }

   @ClusterProxyMethod(CACHE_NAME)
   public ExportedAssetsModel updateImportInfo(@ClusterProxyKey String importId,
                                               String targetLocation, Integer locationType,
                                               String locationUser, Principal principal)
      throws Exception
   {
      IdentityID locationUserID = IdentityID.getIdentityIDFromKey(locationUser);
      AssetEntry targetFolder = targetLocation != null && locationType != null ?
         createImportTargetFolderEntry(targetLocation, locationType, locationUserID) : null;
      final ImportTargetFolderInfo targetFolderInfo = targetFolder == null ? null :
         new ImportTargetFolderInfo(targetFolder, true);

      if(targetFolderInfo == null) {
         return null;
      }

      return getJarInfo(importId, targetFolderInfo, principal);
   }

   @ClusterProxyMethod(CACHE_NAME)
   public ExportedAssetsModel getJarFileInfo(@ClusterProxyKey String importId, Principal principal)
      throws Exception
   {
      return getJarInfo(importId, null, principal);
   }

   @ClusterProxyMethod(CACHE_NAME)
   public ImportAssetResponse importAsset(@ClusterProxyKey String importId,
                                          String targetLocation, Integer locationType,
                                          String locationUser, Boolean dependenciesApplyTarget,
                                          List<String> ignoreList, boolean overwriting,
                                          boolean background, Principal principal)
      throws Exception
   {
      if(principal instanceof SRPrincipal && ((SRPrincipal) principal).isSelfOrganization()) {
         ArrayList<String> errors = new ArrayList<>();
         errors.add(Catalog.getCatalog().getString("em.import.ignoreSelfImport"));
         return ImportAssetResponse.builder()
            .failedAssets(errors)
            .failed(true).build();
      }

      IdentityID locationUserID = IdentityID.getIdentityIDFromKey(locationUser);

      if(background) {
         CompletableFuture<ImportAssetResponse> future = importCache.getIfPresent(importId);

         if(future != null) {
            if(future.isDone()) {
               importCache.invalidate(importId);
               return future.get();
            }
            else {
               return ImportAssetResponse.builder().complete(false).build();
            }
         }
      }

      ImportAssetContext context = contexts.get(importId);

      if(context == null) {
         LOG.warn("Current session is missing the imported jar info.");
         return ImportAssetResponse.builder().failed(true).build();
      }

      PartialDeploymentJarInfo info = context.getInfo();
      ImportJarProperties properties = context.getProperties();
      DeploymentInfo deploymentInfo = new DeploymentInfo(info, properties);
      contexts.remove(importId);

      AssetEntry targetFolder = targetLocation != null && locationType != null ?
         createImportTargetFolderEntry(targetLocation, locationType, locationUserID) : null;
      final ImportTargetFolderInfo targetFolderInfo = targetFolder == null ? null :
         new ImportTargetFolderInfo(targetFolder, dependenciesApplyTarget);

      if(importId == null) {
         return deployService.importAsset(deploymentInfo, ignoreList, overwriting,
                                          targetFolderInfo, principal);
      }
      else if(importCache.getIfPresent(importId) == null) {
         CompletableFuture<ImportAssetResponse> future = new CompletableFuture<>();
         importCache.put(importId, future);
         ThreadPool.addOnDemand(() -> {
            Principal oPrincipal = ThreadContext.getPrincipal();
            ThreadContext.setPrincipal(principal);

            try {
               ImportAssetResponse response = deployService.importAsset(
                  deploymentInfo, ignoreList, overwriting, targetFolderInfo, principal);
               future.complete(response);
            }
            catch(Exception e) {
               future.completeExceptionally(e);
            }

            ThreadContext.setPrincipal(oPrincipal);
         });
      }

      return ImportAssetResponse.builder().complete(false).build();
   }

   @GetMapping("/api/em/content/repository/import/clear-cache")
   @ClusterProxyMethod(CACHE_NAME)
   public Void finishImport(@ClusterProxyKey String importId) {
      ImportAssetContext context = contexts.remove(importId);

      if(context != null) {
         Tool.deleteFile(new File(context.getProperties().unzipFolderPath()));
      }

      return null;
   }

   private AssetEntry createImportTargetFolderEntry(String targetLocation, Integer locationType,
                                                    IdentityID locationUser)
   {
      boolean userScope = locationUser != null && !Tool.isEmptyString(locationUser.name) &&
         !"__NULL__".equals(locationUser.name);
      int scope = userScope ? AssetRepository.USER_SCOPE : AssetRepository.GLOBAL_SCOPE;

      if((RepositoryEntry.FOLDER & locationType) != RepositoryEntry.FOLDER) {
         return null;
      }

      if((RepositoryEntry.REPOSITORY & locationType) == RepositoryEntry.REPOSITORY ||
         (RepositoryEntry.USER & locationType) == RepositoryEntry.USER ||
         RepositoryEntry.FOLDER  == locationType)
      {
         return new AssetEntry(scope, AssetEntry.Type.REPOSITORY_FOLDER, targetLocation,
                               locationUser);
      }
      else if((RepositoryEntry.WORKSHEET_FOLDER & locationType) == RepositoryEntry.WORKSHEET_FOLDER)
      {
         return new AssetEntry(scope, AssetEntry.Type.FOLDER, targetLocation,
                               locationUser);
      }
      else if((RepositoryEntry.DATA_SOURCE_FOLDER & locationType) ==
         RepositoryEntry.DATA_SOURCE_FOLDER)
      {
         return new AssetEntry(scope, AssetEntry.Type.DATA_SOURCE_FOLDER, targetLocation,
                               locationUser);
      }

      return null;
   }

   private ExportedAssetsModel getJarInfo(String importId, ImportTargetFolderInfo targetFolderInfo,
                                          Principal principal)
      throws Exception
   {
      ImportAssetContext context = contexts.get(importId);
      ImportJarProperties properties = context.getProperties();
      PartialDeploymentJarInfo info = DeployManagerService.getInfo(properties.unzipFolderPath());
      context.setInfo(info);
      contexts.put(importId, context);
      DeploymentInfo deploymentInfo = new DeploymentInfo(info, properties);

      return deployService.getJarFileInfo(importId, deploymentInfo, targetFolderInfo, principal);
   }

   private final DeployService deployService;
   private final Map<String, ImportAssetContext> contexts;
   private final Cache<String, CompletableFuture<ImportAssetResponse>> importCache;
   static final String CACHE_NAME = "importAssetContexts";
   private static final Logger LOG = LoggerFactory.getLogger(ImportAssetService.class);

   public static final class ImportAssetContext implements Serializable {
      public ImportAssetContext(String id) {
         this.id = id;
      }

      public String getId() {
         return id;
      }

      public PartialDeploymentJarInfo getInfo() {
         return info;
      }

      public void setInfo(PartialDeploymentJarInfo info) {
         this.info = info;
      }

      public ImportJarProperties getProperties() {
         return properties;
      }

      public void setProperties(ImportJarProperties properties) {
         this.properties = properties;
      }

      private final String id;
      private PartialDeploymentJarInfo info;
      private ImportJarProperties properties;
   }
}
