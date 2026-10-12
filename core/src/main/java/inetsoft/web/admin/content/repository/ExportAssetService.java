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

import inetsoft.cluster.*;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.*;
import inetsoft.sree.security.SecurityException;
import inetsoft.util.FileSystemService;
import inetsoft.util.Tool;
import inetsoft.util.cachefs.BinaryTransfer;
import inetsoft.util.dep.XAsset;
import inetsoft.web.admin.content.repository.model.*;
import inetsoft.web.admin.deploy.*;
import inetsoft.web.service.BinaryTransferService;
import org.apache.commons.io.output.DeferredFileOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.io.*;
import java.security.Principal;
import java.sql.Timestamp;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@ClusterProxy
@Component
public class ExportAssetService {

   @Autowired
   public ExportAssetService(DeployService deployService, BinaryTransferService binaryTransferService,
                             Cluster cluster, FileSystemService fileSystemService)
   {
      this.deployService = deployService;
      this.binaryTransferService = binaryTransferService;
      this.cluster = cluster;
      this.contextCache = new ConcurrentHashMap<>();
      cluster.registerSpringProxyPartitionedCache(FILE_LOCATION_CACHE_NAME);
      this.fileLocationMap = cluster.getMap(FILE_LOCATION_CACHE_NAME);
      this.filePathMap = new ConcurrentHashMap<>();
      this.fileSystemService = fileSystemService;
   }

   @ClusterProxyMethod(FILE_LOCATION_CACHE_NAME)
   public ExportJarProperties createExport(@ClusterProxyKey String exportID, String fileName, ExportedAssetsModel exportedAssetsModel, Principal principal) {
      ExportJarProperties properties;

      try {
           properties = createExport(exportID, exportedAssetsModel, principal);
      }
      catch(Exception e) {
         throw new RuntimeException("Could not create export.", e);
      }

      contextCache.put(exportID, properties);

      String localNodeAddress = cluster.getLocalMember();

      fileLocationMap.put(exportID, localNodeAddress);

      filePathMap.put(exportID, fileName);

      return ExportJarProperties.builder()
         .zipFilePath(fileName)
         .exportID(exportID)
         .build();
   }

   @ClusterProxyMethod(FILE_LOCATION_CACHE_NAME)
   public Boolean checkExportStatus(@ClusterProxyKey String exportID) {
      return fileLocationMap.containsKey(exportID);
   }

   @ClusterProxyMethod(FILE_LOCATION_CACHE_NAME)
   public BinaryTransfer getJarFileBytes(@ClusterProxyKey String exportID) {
      ExportJarProperties properties = contextCache.get(exportID);

      if(properties != null) {
         contextCache.remove(exportID);
         fileLocationMap.remove(exportID);

         BinaryTransfer data = binaryTransferService.createBinaryTransfer(exportID);

         try {
            DeferredFileOutputStream out = binaryTransferService.createOutputStream(data);

            try {
               deployService.downloadJar(properties, in -> {
                  try {
                     Tool.copyTo(in, out);
                  }
                  catch(Exception e) {
                     throw new RuntimeException("Failed to copy export JAR to HTTP response", e);
                  }
               });
            }
            catch(Exception e) {
               // Log or handle the exception appropriately
               throw new RuntimeException("Failed to write to BinaryTransfer stream", e);
            }
            finally {
               try {
                  // Pass both the data and the stream to the closing method
                  binaryTransferService.closeOutputStream(data, out);
               }
               catch(IOException e) {
                  // Log or handle the exception during the close operation
                  throw new RuntimeException("Failed to close BinaryTransfer stream", e);
               }
            }
         }
         catch(IOException e) {
            throw new RuntimeException("Failed to create output stream while export asset file data", e);
         }

         return data;
      }

      return null;
   }

   @ClusterProxyMethod(FILE_LOCATION_CACHE_NAME)
   public String getFileNameFromID(@ClusterProxyKey String exportID) {
      return filePathMap.get(exportID);
   }

   public ExportJarProperties createExport(String exportId, ExportedAssetsModel exportedAssetsModel, Principal principal)
      throws Exception
   {
      String name = Tool.byteDecode(exportedAssetsModel.name());
      boolean overwriting = exportedAssetsModel.overwriting();
      List<SelectedAssetModel> entryData = exportedAssetsModel.selectedEntities();
      List<RequiredAssetModel> assetData = exportedAssetsModel.dependentAssets();
      List<XAsset> assets = deployService.getEntryAssets(entryData, principal);
      List<PartialDeploymentJarInfo.SelectedAsset> entryDataArray = DeployUtil.getEntryData(assets);
      assert assetData != null;

      // Bug #77862, a dependent asset with an owner is read from the owner's storage. Global
      // dependents resolve in the current organization and are not checked, a legitimate
      // dependency list holds global data sources and the like the caller may not administer.
      // Bug #77923, #77924, a schedule task or an auto-save asset is checked against its stored
      // owner, whatever owner the client sends, as the asset is written.
      List<PartialDeploymentJarInfo.RequiredAsset> assetDataArray = assetData.stream()
         .map(ExportAssetService::createRequiredAsset)
         .collect(Collectors.toList());

      for(PartialDeploymentJarInfo.RequiredAsset required : assetDataArray) {
         deployService.checkDependentAsset(required, principal);
      }

      // Bug #77959, the dependent list comes from the client, so the unchecked global dependents
      // could be any global asset. Keep only the dependents that are dependencies of the checked
      // selected assets, as get-dependent-assets lists them, or are selected assets themselves.
      // The asset that is written is compared, its path may come from the detail description.
      // A sheet the caller may write (e.g. through an import) can make any global asset a real
      // dependency, so an owner-less dependent is also dropped if the caller may not read it.
      Set<String> selectedIds = assets.stream()
         .map(XAsset::toIdentifier)
         .collect(Collectors.toSet());
      Set<String> exportable = DeployUtil.getDependentAssets(assets).keySet().stream()
         .map(XAsset::toIdentifier)
         .collect(Collectors.toCollection(HashSet::new));
      exportable.addAll(selectedIds);
      assetDataArray = assetDataArray.stream()
         .filter(required -> isExportableDependent(required, exportable, selectedIds, principal))
         .collect(Collectors.toList());

      PartialDeploymentJarInfo info = new PartialDeploymentJarInfo();
      info.setName(name);
      info.setDeploymentDate(new Timestamp(System.currentTimeMillis()));
      info.setOverwriting(overwriting);
      info.setSelectedEntries(entryDataArray);
      info.setDependentAssets(assetDataArray);

      File zipfile = fileSystemService.getCacheFile(name + ".zip");
      DeployUtil.createExport(info, new FileOutputStream(zipfile));

      return ExportJarProperties.builder()
         .zipFilePath(zipfile.getPath())
         .exportID(exportId)
         .build();
   }

   private static boolean isExportableDependent(PartialDeploymentJarInfo.RequiredAsset required,
                                                Set<String> exportable, Set<String> selectedIds,
                                                Principal principal)
   {
      XAsset asset = DeployUtil.getAsset(required);

      if(asset == null || !exportable.contains(asset.toIdentifier())) {
         LOG.warn("A dependent asset of type {} is not a dependency of the exported assets, " +
                     "it is not exported", asset == null ? null : asset.getType());
         LOG.debug("Dependent asset not exported: {}", required.getPath());
         return false;
      }

      if(!selectedIds.contains(asset.toIdentifier()) &&
         !isGlobalDependentReadable(asset, principal))
      {
         LOG.warn("The dependent asset {} is not readable by {}, it is not exported",
                  asset.toIdentifier(), principal == null ? null : principal.getName());
         return false;
      }

      return true;
   }

   /**
    * Bug #77959, checks if the caller may read an owner-less (global) dependent asset of an
    * export. A dependent with an owner is checked against the owner
    * ({@link DeployService#checkDependentAsset}), and an asset without a security resource or a
    * device (an action resource without READ) is not checked.
    *
    * @param asset     the dependent asset as it is written to the export.
    * @param principal the caller.
    *
    * @return {@code false} if the asset is global and the caller has no READ permission on its
    *         security resource.
    */
   public static boolean isGlobalDependentReadable(XAsset asset, Principal principal) {
      IdentityID owner = asset.getUser();

      if(owner != null && !XAsset.NULL.equals(owner.name)) {
         return true;
      }

      SecurityEngine security = SecurityEngine.getSecurity();

      if(!security.isSecurityEnabled()) {
         return true;
      }

      Resource resource = asset.getSecurityResource();

      if(resource == null || resource.getType() == ResourceType.DEVICE) {
         return true;
      }

      if(principal == null) {
         return false;
      }

      try {
         return security.checkPermission(principal, resource.getType(), resource.getPath(),
                                         ResourceAction.READ);
      }
      catch(SecurityException e) {
         LOG.warn("Failed to check the permission on {} for {}, not exporting it", resource,
                  principal.getName(), e);
         return false;
      }
   }

   /**
    * Builds the dependent asset of an export from the model the client sends.
    */
   public static PartialDeploymentJarInfo.RequiredAsset createRequiredAsset(
      RequiredAssetModel model)
   {
      PartialDeploymentJarInfo.RequiredAsset asset = new PartialDeploymentJarInfo.RequiredAsset();
      asset.setPath(model.name());
      asset.setType(model.type());
      asset.setUser(model.user());
      asset.setTypeDescription(model.typeDescription());
      asset.setRequiredBy(model.requiredBy());
      asset.setDetailDescription(model.detailDescription());
      asset.setAssetDescription(model.assetDescription());
      long lastModifiedTime = model.lastModifiedTime();

      if(lastModifiedTime != 0) {
         asset.setLastModifiedTime(lastModifiedTime);
      }

      return asset;
   }

   private final Cluster cluster;
   private final Map<String, ExportJarProperties> contextCache;
   private final Map<String, String> fileLocationMap;
   private final Map<String, String> filePathMap;
   private final DeployService deployService;
   private final BinaryTransferService binaryTransferService;
   private final FileSystemService fileSystemService;

   static final String FILE_LOCATION_CACHE_NAME = "exportAssetFileLocations";
   private static final Logger LOG = LoggerFactory.getLogger(ExportAssetService.class);
}