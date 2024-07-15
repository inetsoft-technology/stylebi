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
import inetsoft.web.admin.content.repository.model.*;
import inetsoft.web.admin.schedule.ScheduleTaskActionService;
import inetsoft.web.admin.schedule.model.ViewsheetTreeListModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
public class RepositoryDashboardController {
   @Autowired
   public RepositoryDashboardController(RepositoryDashboardService repositoryDashboardService,
                                        ContentRepositoryTreeService treeService,
                                        ScheduleTaskActionService taskActionService)
   {
      this.repositoryDashboardService = repositoryDashboardService;
      this.treeService = treeService;
      this.taskActionService = taskActionService;
   }

   @GetMapping("/api/em/content/repository/dashboard")
   public RepositoryDashboardSettingsModel getDashboardSettings(@DecodeParam("path") String path,
                                                                @DecodeParam(value = "owner", required = false) String owner,
                                                                Principal principal)
      throws Exception
   {
      IdentityID ownerID = IdentityID.getIdentityIDFromKey(owner);
      path = treeService.getUnscopedPath(path);
      return repositoryDashboardService.getSettings(path, ownerID, principal);
   }

   @PostMapping("/api/em/content/repository/dashboard")
   public RepositoryDashboardSettingsModel setDashboardSettings(@DecodeParam("path") String path,
                                                                @DecodeParam(value = "owner", required = false) String owner,
                                                                @RequestBody RepositoryDashboardSettingsModel model,
                                                                Principal principal)
      throws Exception
   {
      IdentityID ownerID = IdentityID.getIdentityIDFromKey(owner);
      return repositoryDashboardService.setSettings(path, model, ownerID, principal);
   }

   @PostMapping("/api/em/settings/content/repository/dashboard/add")
   public ContentRepositoryTreeNode addDashboard(@RequestBody NewRepositoryFolderRequest info,
                                                        Principal principal)
      throws Exception
   {
      return repositoryDashboardService.addDashboard(info, principal);
   }

   @GetMapping("/api/em/content/repository/folder/dashboard")
   public RepositoryFolderDashboardSettingsModel getDashboardFolderSettings(Principal principal)
      throws Exception
   {
      return repositoryDashboardService.getDashboardFolderSettings(principal);
   }

   @PostMapping("/api/em/content/repository/folder/dashboard")
   public RepositoryFolderDashboardSettingsModel setDashboardFolderSettings(
      @RequestBody RepositoryFolderDashboardSettingsModel model,
      Principal principal) throws Exception
   {
      return repositoryDashboardService.setDashboardFolderSettings(model, principal);
   }

   @GetMapping("/api/em/settings/repository/dashboard/viewsheet/folders")
   public ViewsheetTreeListModel getViewsheetFolders(Principal principal)
      throws Exception
   {
      return taskActionService.getViewsheetTree(principal);
   }

   private final RepositoryDashboardService repositoryDashboardService;
   private final ContentRepositoryTreeService treeService;
   private final ScheduleTaskActionService taskActionService;
}
