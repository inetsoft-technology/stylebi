/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.admin.content.repository;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import inetsoft.sree.RepositoryEntry;
import inetsoft.sree.internal.DeployManagerService;
import inetsoft.sree.internal.DeploymentInfo;
import inetsoft.sree.security.IdentityID;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.util.*;
import inetsoft.web.admin.content.repository.model.ExportedAssetsModel;
import inetsoft.web.admin.content.repository.model.ImportAssetResponse;
import inetsoft.web.admin.deploy.*;
import inetsoft.web.admin.model.FileData;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.lang.invoke.MethodHandles;
import java.security.Principal;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@RestController
public class ImportAssetController {
   public ImportAssetController(DeployService deployService) {
      this.deployService = deployService;
      this.importCache = Caffeine.newBuilder()
         .expireAfterAccess(10L, TimeUnit.MINUTES)
         .maximumSize(1000L)
         .build();
   }

   @PostMapping("/api/em/content/repository/set-jar-file")
   public ExportedAssetsModel setJarFile(
      @RequestBody FileData file, HttpServletRequest request,
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
      request.getSession(true).setAttribute(PROPS_ATTR, properties);
      return getJarFileInfo(request, principal);
   }

   @GetMapping("/api/em/content/repository/update-import-info")
   public ExportedAssetsModel updateImportInfo(
      HttpServletRequest request,
      @RequestParam(name = "targetLocation") String targetLocation,
      @RequestParam(name = "locationType") Integer locationType,
      @RequestParam(name = "locationUser", required = false) String locationUser,
      Principal principal) throws Exception
   {
      IdentityID locationUserID = IdentityID.getIdentityIDFromKey(locationUser);
      AssetEntry targetFolder = targetLocation != null && locationType != null ?
         createImportTargetFolderEntry(targetLocation, locationType, locationUserID) : null;
      final ImportTargetFolderInfo targetFolderInfo = targetFolder == null ? null :
         new ImportTargetFolderInfo(targetFolder, true);

      if(targetFolderInfo == null) {
         return null;
      }

      return getJarInfo(targetFolderInfo, request, principal);
   }

   @GetMapping("/api/em/content/repository/get-jar-file-info")
   public ExportedAssetsModel getJarFileInfo(
      HttpServletRequest request,
      Principal principal)  throws Exception
   {
      return getJarInfo(null, request, principal);
   }

   @PostMapping("/api/em/content/repository/import/{overwriting}")
   public ImportAssetResponse importAsset(
      HttpServletRequest req, Principal principal,
      @RequestParam(name = "importId", required = false) String importId,
      @RequestParam(name = "targetLocation", required = false) String targetLocation,
      @RequestParam(name = "locationType", required = false) Integer locationType,
      @RequestParam(name = "locationUser", required = false) String locationUser,
      @RequestParam(name = "dependenciesApplyTarget", defaultValue = "true") Boolean dependenciesApplyTarget,
      @RequestBody() List<String> ignoreList,
      @PathVariable("overwriting") boolean overwriting) throws Exception
   {
      IdentityID locationUserID = IdentityID.getIdentityIDFromKey(locationUser);

      if(importId != null) {
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

      HttpSession session = req.getSession(true);
      PartialDeploymentJarInfo info = (PartialDeploymentJarInfo) session.getAttribute(INFO_ATTR);
      ImportJarProperties properties = (ImportJarProperties) session.getAttribute(PROPS_ATTR);
      DeploymentInfo deploymentInfo = new DeploymentInfo(info, properties);
      session.removeAttribute(INFO_ATTR);
      session.removeAttribute(PROPS_ATTR);

      if(info == null) {
         LOG.warn("Current session is missing the imported jar info.");
         return ImportAssetResponse.builder().failed(true).build();
      }

      AssetEntry targetFolder = targetLocation != null && locationType != null ?
         createImportTargetFolderEntry(targetLocation, locationType, locationUserID) : null;
      final ImportTargetFolderInfo targetFolderInfo = targetFolder == null ? null :
         new ImportTargetFolderInfo(targetFolder, dependenciesApplyTarget);

      if(importId == null) {
         return deployService.importAsset(deploymentInfo, ignoreList, overwriting,
            targetFolderInfo, principal);
      }
      else {
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
         return ImportAssetResponse.builder().complete(false).build();
      }
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

   private ExportedAssetsModel getJarInfo(ImportTargetFolderInfo targetFolderInfo,
                                          HttpServletRequest request, Principal principal)
      throws Exception
   {
      HttpSession session = request.getSession(true);
      ImportJarProperties properties = (ImportJarProperties) session.getAttribute(PROPS_ATTR);
      PartialDeploymentJarInfo info = DeployManagerService.getInfo(properties.unzipFolderPath());

      if(info == null) {
         throw new RuntimeException("Failed to get Jar info from " + properties.unzipFolderPath());
      }

      session.setAttribute(INFO_ATTR, info);
      DeploymentInfo deploymentInfo = new DeploymentInfo(info, properties);

      return deployService.getJarFileInfo(deploymentInfo, targetFolderInfo, principal);
   }

   private final DeployService deployService;
   private final Cache<String, CompletableFuture<ImportAssetResponse>> importCache;
   private static final String PROPS_ATTR = "__private_deployJarProperties";
   private static final String INFO_ATTR = "__private_deployJarInfo";
   private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
}
