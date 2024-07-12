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

import inetsoft.sree.security.*;
import inetsoft.uql.XRepository;
import inetsoft.web.admin.content.repository.model.ScheduleTaskFolderSettingsModel;
import inetsoft.web.admin.security.ResourcePermissionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.Principal;

@Service
public class RepositoryScheduleTaskService {
   @Autowired
   public RepositoryScheduleTaskService(SecurityEngine securityEngine,
                                        XRepository repository,
                                        ResourcePermissionService resourcePermissionService)
   {
      this.repository = repository;
      this.securityEngine = securityEngine;
      this.resourcePermissionService = resourcePermissionService;
   }

   public ScheduleTaskFolderSettingsModel getScheduleTaskFolderPermission(String path,
                                                                          Principal principal)
   {
      ScheduleTaskFolderSettingsModel.Builder builder = ScheduleTaskFolderSettingsModel.builder();
      final ResourcePermissionModel model = resourcePermissionService.getTableModel(path,
         ResourceType.SCHEDULE_TASK_FOLDER, ResourcePermissionService.ADMIN_ACTIONS, principal);
      builder.permissions(model);

      return builder.build();
   }

   public void setScheduleTaskFolderPermission(ResourcePermissionModel model, String path,
                                               Principal principal)
      throws Exception
   {
      securityEngine.checkPermission(
         principal, ResourceType.SCHEDULE_TASK_FOLDER, path, ResourceAction.ADMIN);
      resourcePermissionService.setResourcePermissions(path, ResourceType.SCHEDULE_TASK_FOLDER,
         model, principal);
   }

   private final XRepository repository;
   private final SecurityEngine securityEngine;
   private final ResourcePermissionService resourcePermissionService;
}
