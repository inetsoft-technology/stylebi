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

import inetsoft.sree.security.*;
import inetsoft.util.*;
import inetsoft.web.adhoc.DecodeParam;
import inetsoft.web.security.RequiredPermission;
import inetsoft.web.security.Secured;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
public class RepositoryFolderController {
   @Autowired
   public RepositoryFolderController(RepositoryFolderService repositoryFolderService)
   {
      this.repositoryFolderService = repositoryFolderService;
   }

   @Secured(
      @RequiredPermission(
         resourceType = ResourceType.EM_COMPONENT,
         resource = "settings/content/repository",
         actions = ResourceAction.ACCESS
      )
   )
   @GetMapping("/api/em/content/repository/folder/")
   public RepositoryFolderSettingsModel getRepositoryFolderSetting(
      @DecodeParam("path") String path,
      @RequestParam("isWorksheetFolder") boolean isWorksheetFolder,
      @DecodeParam(value = "owner", required = false) String owner,
      Principal principal)
      throws Exception
   {
      String currOrgID = OrganizationManager.getInstance().getCurrentOrgID();

      if(SecurityEngine.getSecurity().getSecurityProvider().getOrganization(currOrgID) == null) {
         throw new InvalidOrgException(Catalog.getCatalog().getString("em.security.invalidOrganizationPassed"));
      }

      IdentityID ownerID = IdentityID.getIdentityIDFromKey(owner);
      return this.repositoryFolderService.getSettings(path, isWorksheetFolder, ownerID, principal);
   }

   @Secured(
      @RequiredPermission(
         resourceType = ResourceType.EM_COMPONENT,
         resource = "settings/content/repository",
         actions = ResourceAction.ACCESS
      )
   )
   @PostMapping("/api/em/content/repository/edit/folder")
   public RepositoryFolderSettingsModel setRepositoryFolderSettings(
      @DecodeParam(value = "owner", required = false) String owner,
      @RequestBody() SetRepositoryFolderSettingsModel model,
      Principal principal)
      throws Exception
   {
      IdentityID ownerID = IdentityID.getIdentityIDFromKey(owner);
      return this.repositoryFolderService.applySettings(ownerID, model, principal);
   }

   private final RepositoryFolderService repositoryFolderService;
}
