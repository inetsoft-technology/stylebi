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
package inetsoft.web.admin.schedule;

import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.schedule.ScheduleTask;
import inetsoft.sree.security.SecurityException;
import inetsoft.sree.security.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.web.admin.content.repository.ContentRepositoryTreeNode;
import inetsoft.web.admin.schedule.model.*;
import inetsoft.web.security.*;
import inetsoft.web.viewsheet.service.LinkUri;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.net.URLDecoder;
import java.security.Principal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Controller that provides a REST endpoint used for the scheduled tasks dialog model.
 *
 * @since 13.1
 */
@RestController
public class EMScheduleTaskController {
   /**
    * Creates a new instance of <tt>EMScheduleTaskController</tt>.
    */
   @Autowired
   public EMScheduleTaskController(ScheduleTaskService scheduleTaskService,
                                   ScheduleTaskFolderService scheduleTaskFolderService)
   {
      this.scheduleTaskService = scheduleTaskService;
      this.scheduleTaskFolderService = scheduleTaskFolderService;
   }

   /**
    * Gets a new task model.
    *
    * @param principal the user
    *
    * @return the dialog model
    *
    * @throws Exception if could not get task
    */
   @Secured(
      @RequiredPermission(
         resourceType = ResourceType.SCHEDULER,
         resource = "*",
         actions = ResourceAction.ACCESS
      )
   )
   @PostMapping("/api/em/schedule/new")
   public ScheduleTaskDialogModel getNewTaskDialogModel(
      @RequestParam("timeZone") String timeZone,
      @RequestBody(required = false) ContentRepositoryTreeNode parentInfo,
      Principal principal) throws Exception
   {
      AssetEntry parentEntry = null;

      if(parentInfo != null) {
         String path = parentInfo.path();
         IdentityID user = parentInfo.owner();
         int scope = user == null ? AssetRepository.GLOBAL_SCOPE :
            AssetRepository.USER_SCOPE;

         parentEntry =  new AssetEntry(scope, AssetEntry.Type.SCHEDULE_TASK_FOLDER, path, user);
      }

      if(!scheduleTaskFolderService.checkFolderPermission(parentEntry == null ? "/" :
         parentEntry.getPath(), principal, ResourceAction.READ))
      {
         throw new SecurityException(Catalog.getCatalog().getString(
            "schedule.tasks.nopermission.create"));
      }

      return scheduleTaskService.getNewTaskDialogModel(null, principal, true, true, parentEntry, timeZone);
   }

   /**
    * Gets the schedule task dialog model
    *
    * @param taskName  the task name
    * @param principal the user
    *
    * @return the dialog model
    *
    * @throws Exception if could not get task
    */
   @Secured( value = {
      @RequiredPermission(
         resourceType = ResourceType.SCHEDULER,
         resource = "*",
         actions = ResourceAction.ACCESS
      ),
      @RequiredPermission(
         resourceType = ResourceType.EM_COMPONENT,
         resource = "settings/schedule/tasks",
         actions = ResourceAction.ACCESS
      )
   })
   @GetMapping("/api/em/schedule/edit")
   public ScheduleTaskDialogModel getDialogModel(@RequestParam("taskName") String taskName,
                                                 Principal principal)
      throws Exception
   {
      try {
         taskName = Tool.byteDecode(taskName);
      }
      catch(Exception ignore) {
      }

      boolean canEdit =
         scheduleTaskService.checkTaskPermission(taskName, principal, ResourceAction.WRITE) &&
         scheduleTaskService.checkTaskPermission(taskName, principal, ResourceAction.READ);

      if(!ScheduleManager.isInternalTask(taskName)) {
         canEdit &=
            scheduleTaskService.checkTaskPermission(taskName, principal, ResourceAction.DELETE);
      }

      if(!canEdit) {
         Catalog catalog = Catalog.getCatalog();
         String name = taskName;

         if(ScheduleManager.isInternalTask(name)) {
            name = catalog.getString(name);
         }

         name = "\"" + name + "\"";
         throw new SecurityException(catalog.getString("en.schedule.task.unauthorized", name));
      }

      return scheduleTaskService.getDialogModel(taskName, principal, true);
   }

   @Secured(
      @RequiredPermission(
         resourceType = ResourceType.SCHEDULER,
         resource = "*",
         actions = ResourceAction.ACCESS
      )
   )
   @PostMapping("/api/em/schedule/task/save")
   public ScheduleTaskDialogModel saveTask(@RequestBody ScheduleTaskEditorModel model,
                                           @LinkUri String linkURI,
                                           Principal principal) throws Exception
   {
      return scheduleTaskService.saveTask(model, linkURI, principal, true);
   }

   @Secured(
      @RequiredPermission(
         resourceType = ResourceType.SCHEDULER,
         resource = "*",
         actions = ResourceAction.ACCESS
      )
   )
   @PostMapping("/api/em/schedule/enable/task")
   public ToggleTaskResponse toggleTaskEnabled(@RequestBody TaskListModel list, Principal principal)
      throws Exception
   {
      final ToggleTaskResponse.Builder builder = ToggleTaskResponse.builder();
      ScheduleManager scheduleManager = ScheduleManager.getScheduleManager();

      for(String name : list.taskNames()) {
         ScheduleTask task = scheduleManager.getScheduleTask(name);

         if(!(SecurityEngine.getSecurity().checkPermission(principal, ResourceType.SCHEDULE_TASK, name,
               ResourceAction.WRITE) || (task != null && ScheduleManager.hasShareGroupPermission(task, principal))))
         {
            return builder.build();
         }

         boolean enabled = !scheduleTaskService.isTaskEnabled(name);
         scheduleTaskService.setTaskEnabled(name, enabled, principal);
      }

      return builder.build();
   }

   @Secured(
      @RequiredPermission(
         resourceType = ResourceType.SCHEDULER,
         resource = "*",
         actions = ResourceAction.ACCESS
      )
   )
   @GetMapping("/api/em/schedule/executeAs/identities")
   public ExecuteAsIdentitiesModel getExecuteAsUsers(@RequestParam("owner") String owner,
                                                     Principal principal)
   {
      IdentityID ownerId = new IdentityID(owner, OrganizationManager.getInstance().getCurrentOrgID());
      ExecuteAsIdentitiesModel model = new ExecuteAsIdentitiesModel();
      model.setUsers(this.scheduleTaskService.getExecuteAsUsers(ownerId, principal)
                        .stream()
                        .distinct()
                        .collect(Collectors.toList()));
      List<IdentityID> groups = this.scheduleTaskService.getExecuteAsGroups(owner, principal);
      SecurityProvider securityProvider = SecurityEngine.getSecurity().getSecurityProvider();
      model.setGroups(groups);
      return model;
   }

   private final ScheduleTaskService scheduleTaskService;
   private final ScheduleTaskFolderService scheduleTaskFolderService;
}
