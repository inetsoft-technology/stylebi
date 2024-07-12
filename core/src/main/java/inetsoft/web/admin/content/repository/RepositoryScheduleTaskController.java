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

import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.web.admin.content.repository.model.ScheduleTaskFolderSettingsModel;
import inetsoft.web.security.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
public class RepositoryScheduleTaskController {
   @Autowired
   public RepositoryScheduleTaskController(RepositoryScheduleTaskService scheduleTaskService,
                                           ResourcePermissionService permissionService)
   {
      this.permissionService = permissionService;
      this.scheduleTaskService = scheduleTaskService;
   }

   @GetMapping("/api/em/settings/content/repository/scheduleTaskFolder")
   @Secured({
      @RequiredPermission(
         resourceType = ResourceType.SCHEDULE_TASK_FOLDER, actions = ResourceAction.ADMIN
      )
   })
   public ScheduleTaskFolderSettingsModel getFolderModel(
      @PermissionPath @RequestParam("path") String path, Principal principal)
   {
      return scheduleTaskService.getScheduleTaskFolderPermission(path, principal);
   }

   @PostMapping("/api/em/settings/content/repository/scheduleTaskFolder")
   @Secured({
      @RequiredPermission(
         resourceType = ResourceType.SCHEDULE_TASK_FOLDER, actions = ResourceAction.ADMIN
      )
   })
   public ScheduleTaskFolderSettingsModel setFolderModel(
      @PermissionPath @RequestParam("path") String path,
      @RequestBody ScheduleTaskFolderSettingsModel model, Principal principal) throws Exception
   {
      scheduleTaskService.setScheduleTaskFolderPermission(model.permissions(), path, principal);
      return scheduleTaskService.getScheduleTaskFolderPermission(path, principal);
   }

   private final ResourcePermissionService permissionService;
   private final RepositoryScheduleTaskService scheduleTaskService;
}
