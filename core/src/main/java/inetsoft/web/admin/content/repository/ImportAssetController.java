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
package inetsoft.web.admin.content.repository;

import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.web.admin.content.repository.model.ExportedAssetsModel;
import inetsoft.web.admin.content.repository.model.ImportAssetResponse;
import inetsoft.web.admin.model.FileData;
import inetsoft.web.security.RequiredPermission;
import inetsoft.web.security.Secured;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
public class ImportAssetController {
   @Autowired
   public ImportAssetController(ImportAssetServiceProxy importService, Cluster cluster) {
      this.importService = importService;
      this.cluster = cluster;
   }

   @PostConstruct
   public void initializeCache() {
      // ensure that the cache is initialized because ImportAssetService may be lazily initialized
      // in a different order than the controller and proxy
      cluster.registerSpringProxyPartitionedCache(ImportAssetService.CACHE_NAME);
      cluster.getCache(ImportAssetService.CACHE_NAME);
   }

   @Secured(
      @RequiredPermission(
         resourceType = ResourceType.EM_COMPONENT,
         resource = "settings/content/repository",
         actions = ResourceAction.ACCESS
      )
   )
   @PostMapping("/api/em/content/repository/set-jar-file")
   public ExportedAssetsModel setJarFile(@RequestBody FileData file, Principal principal)
      throws Exception
   {
      String importId = UUID.randomUUID().toString();
      return importService.setJarFile(importId, file, principal);
   }

   @Secured(
      @RequiredPermission(
         resourceType = ResourceType.EM_COMPONENT,
         resource = "settings/content/repository",
         actions = ResourceAction.ACCESS
      )
   )
   @GetMapping("/api/em/content/repository/update-import-info/{importId}")
   public ExportedAssetsModel updateImportInfo(
      @PathVariable("importId") String importId,
      @RequestParam(name = "targetLocation") String targetLocation,
      @RequestParam(name = "locationType") Integer locationType,
      @RequestParam(name = "locationUser", required = false) String locationUser,
      Principal principal) throws Exception
   {
      return importService.updateImportInfo(
         importId, targetLocation, locationType, locationUser, principal);
   }

   @Secured(
      @RequiredPermission(
         resourceType = ResourceType.EM_COMPONENT,
         resource = "settings/content/repository",
         actions = ResourceAction.ACCESS
      )
   )
   @GetMapping("/api/em/content/repository/get-jar-file-info/{importId}")
   public ExportedAssetsModel getJarFileInfo(@PathVariable("importId") String importId,
                                             Principal principal)  throws Exception
   {
      return importService.getJarFileInfo(importId, principal);
   }

   @Secured(
      @RequiredPermission(
         resourceType = ResourceType.EM_COMPONENT,
         resource = "settings/content/repository",
         actions = ResourceAction.ACCESS
      )
   )
   @PostMapping("/api/em/content/repository/import/{importId}")
   public ImportAssetResponse importAsset(
      @PathVariable("importId") String importId,
      @RequestParam(name = "targetLocation", required = false) String targetLocation,
      @RequestParam(name = "locationType", required = false) Integer locationType,
      @RequestParam(name = "locationUser", required = false) String locationUser,
      @RequestParam(name = "dependenciesApplyTarget", defaultValue = "true") Boolean dependenciesApplyTarget,
      @RequestBody() List<String> ignoreList,
      @RequestParam("overwrite") boolean overwriting,
      @RequestParam("background") boolean background, Principal principal) throws Exception
   {
      return importService.importAsset(
         importId, targetLocation, locationType, locationUser, dependenciesApplyTarget, ignoreList,
         overwriting, background, principal);
   }

   @Secured(
      @RequiredPermission(
         resourceType = ResourceType.EM_COMPONENT,
         resource = "settings/content/repository",
         actions = ResourceAction.ACCESS
      )
   )
   @DeleteMapping("/api/em/content/repository/import/{importId}")
   public void finishImport(@PathVariable("importId") String importId) {
      importService.finishImport(importId);
   }

   private final ImportAssetServiceProxy importService;
   private final Cluster cluster;
}
