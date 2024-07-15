/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.admin.content.repository;

import inetsoft.sree.security.IdentityID;
import inetsoft.web.adhoc.DecodeParam;
import inetsoft.web.admin.content.repository.model.SetRepositoryFolderTableModel;
import inetsoft.web.admin.security.ConnectionStatus;
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

   @GetMapping("/api/em/content/repository/folder/")
   public RepositoryFolderSettingsModel getRepositoryFolderSetting(
      @DecodeParam("path") String path,
      @RequestParam("isWorksheetFolder") boolean isWorksheetFolder,
      @DecodeParam(value = "owner", required = false) String owner,
      Principal principal)
      throws Exception
   {
      IdentityID ownerID = IdentityID.getIdentityIDFromKey(owner);
      return this.repositoryFolderService.getSettings(path, isWorksheetFolder, ownerID, principal);
   }

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

   @PostMapping("/api/em/content/repository/folder/delete")
   public ConnectionStatus deleteRepositoryFolderSettings(@RequestParam(value = "owner", required = false) String owner,
                                                          @RequestParam(value = "force", required = false) boolean force,
                                                          @RequestBody() SetRepositoryFolderTableModel tableModel, Principal principal)
      throws Exception
   {
      IdentityID ownerID = IdentityID.getIdentityIDFromKey(owner);
      return this.repositoryFolderService.deleteRepositoryFolderSettings(ownerID, force, tableModel, principal);
   }

   private final RepositoryFolderService repositoryFolderService;
}
