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
import inetsoft.util.Tool;
import inetsoft.util.cachefs.BinaryTransfer;
import inetsoft.util.dep.XAsset;
import inetsoft.web.admin.content.repository.model.*;
import inetsoft.web.admin.deploy.*;
import inetsoft.web.service.BinaryTransferService;
import org.apache.commons.io.output.DeferredFileOutputStream;
import org.springframework.stereotype.Component;
import java.io.*;
import java.security.Principal;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@ClusterProxy
@Component
public class ExportAssetService {

   public ExportAssetService(DeployService deployService, BinaryTransferService binaryTransferService) {
      this.deployService = deployService;
      this.binaryTransferService = binaryTransferService;

      this.localFileContentCache = new ConcurrentHashMap<>();
      this.fileLocationMap = Cluster.getInstance().getMap(FILE_LOCATION_CACHE_NAME);
      this.filePathMap = new ConcurrentHashMap<>();
   }

   @ClusterProxyMethod(FILE_LOCATION_CACHE_NAME)
   public ExportJarProperties createExport(@ClusterProxyKey String exportID, String fileName, ExportedAssetsModel exportedAssetsModel, Principal principal) {
      byte[] zipFileBytes;

      try {
          zipFileBytes = createExport(exportedAssetsModel, principal);
      }
      catch(Exception e) {
         throw new RuntimeException("Could not create export.", e);
      }

      localFileContentCache.put(exportID, zipFileBytes);

      String localNodeAddress = Cluster.getInstance().getLocalMember();

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
      byte[] fileBytes = localFileContentCache.get(exportID);

      if(fileBytes != null) {
         localFileContentCache.remove(exportID);
         fileLocationMap.remove(exportID);

         BinaryTransfer data = binaryTransferService.createBinaryTransfer(exportID);

         try {
            DeferredFileOutputStream out = binaryTransferService.createOutputStream(data);

            try {
               out.write(fileBytes);
            }
            catch(IOException e) {
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

   private byte[] createExport(ExportedAssetsModel exportedAssetsModel, Principal principal)
      throws Exception
   {
      String name = Tool.byteDecode(exportedAssetsModel.name());
      boolean overwriting = exportedAssetsModel.overwriting();
      List<SelectedAssetModel> entryData = exportedAssetsModel.selectedEntities();
      List<RequiredAssetModel> assetData = exportedAssetsModel.dependentAssets();
      List<XAsset> assets = deployService.getEntryAssets(entryData, principal);
      List<PartialDeploymentJarInfo.SelectedAsset> entryDataArray = DeployUtil.getEntryData(assets);
      assert assetData != null;
      List<PartialDeploymentJarInfo.RequiredAsset> assetDataArray = assetData.stream()
         .map(this::createRequiredAsset)
         .collect(Collectors.toList());

      PartialDeploymentJarInfo info = new PartialDeploymentJarInfo();
      info.setName(name);
      info.setDeploymentDate(new Timestamp(System.currentTimeMillis()));
      info.setOverwriting(overwriting);
      info.setSelectedEntries(entryDataArray);
      info.setDependentAssets(assetDataArray);

      byte[] zipFileBytes;

      try(ByteArrayOutputStream out = new ByteArrayOutputStream()) {
         DeployUtil.createExport(info, out);
         zipFileBytes = out.toByteArray();
      }
      catch(Exception e) {
         throw new Exception("Failed to get byte array of export jar file: " + e.getMessage());
      }

      return zipFileBytes;
   }

   private PartialDeploymentJarInfo.RequiredAsset createRequiredAsset(RequiredAssetModel model) {
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

   private final Map<String, byte[]> localFileContentCache;
   private final Map<String, String> fileLocationMap;
   private final Map<String, String> filePathMap;
   private final DeployService deployService;
   private final BinaryTransferService binaryTransferService;

   static final String CONTENT_CACHE_NAME = "exportAssetByteContents";
   static final String FILE_LOCATION_CACHE_NAME = "exportAssetFileLocations";

}