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
import inetsoft.web.admin.content.repository.model.*;
import inetsoft.web.admin.deploy.*;
import org.springframework.stereotype.Component;
import java.security.Principal;
import java.util.Map;
import java.util.concurrent.*;

@ClusterProxy
@Component
public class ExportAssetService {

   public ExportAssetService(DeployService deployService) {
      this.deployService = deployService;

      this.localFileContentCache = new ConcurrentHashMap<>();
      this.fileLocationMap = Cluster.getInstance().getMap(FILE_LOCATION_CACHE_NAME);
      this.filePathMap = new ConcurrentHashMap<>();
   }

   @ClusterProxyMethod(FILE_LOCATION_CACHE_NAME)
   public ExportJarProperties createExport(@ClusterProxyKey String exportID, String fileName, ExportedAssetsModel exportedAssetsModel, Principal principal) {
      byte[] zipFileBytes;

      try {
          zipFileBytes = deployService.createExport(exportedAssetsModel, principal);
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
   public byte[] getJarFileBytes(@ClusterProxyKey String exportID) {
      byte[] fileBytes = localFileContentCache.get(exportID);

      if(fileBytes != null) {
         localFileContentCache.remove(exportID);
         fileLocationMap.remove(exportID);
      }
      return fileBytes;
   }

   @ClusterProxyMethod(FILE_LOCATION_CACHE_NAME)
   public String getFileNameFromID(@ClusterProxyKey String exportID) {
      return filePathMap.get(exportID);
   }

   private final Map<String, byte[]> localFileContentCache;
   private final Map<String, String> fileLocationMap;
   private final Map<String, String> filePathMap;
   private final DeployService deployService;

   static final String CONTENT_CACHE_NAME = "exportAssetByteContents";
   static final String FILE_LOCATION_CACHE_NAME = "exportAssetFileLocations";

}