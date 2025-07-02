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
package inetsoft.web.portal.controller;

import inetsoft.sree.SreeEnv;
import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.schedule.ScheduleTask;
import inetsoft.sree.security.SecurityException;
import inetsoft.sree.security.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.web.admin.schedule.ScheduleTaskFolderService;
import inetsoft.web.admin.schedule.ScheduleTaskService;
import inetsoft.web.admin.schedule.model.*;
import inetsoft.web.security.RequiredPermission;
import inetsoft.web.security.Secured;
import inetsoft.web.viewsheet.service.LinkUri;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.net.URLDecoder;
import java.security.Principal;

/**
 * Controller that provides a REST endpoint used for the scheduled tasks dialog.
 *
 * @since 12.3
 */
@RestController
public class ScheduleTaskController {
   /**
    * Creates a new instance of <tt>ScheduleTaskController</tt>.
    */
   @Autowired
   public ScheduleTaskController(ScheduleTaskService scheduleTaskService,
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
    * @throws Exception if could not get task
    */
   @PostMapping("/api/portal/schedule/new")
   public ScheduleTaskDialogModel getNewTaskDialogModel(
      @RequestBody(required = false) PortalNewTaskRequest model, Principal principal)
      throws Exception
   {
      AssetEntry parentEntry = model.getParentEntry();

      if(parentEntry != null && !scheduleTaskFolderService.checkFolderPermission(
         parentEntry.getPath(), principal, ResourceAction.READ))
      {
         throw new SecurityException(Catalog.getCatalog().getString(
            "schedule.tasks.nopermission.create"));
      }

      return scheduleTaskService.getNewTaskDialogModel(model, principal);
   }

   /**
    * Gets the schedule task dialog model
    *
    * @param taskName  the task name
    * @param principal the user
    *
    * @return the dialog model
    * @throws Exception if could not get task
    */
   @GetMapping(value = "/api/portal/schedule/edit")
   public ScheduleTaskDialogModel getDialogModel(@RequestParam("name") String taskName,
                                                 Principal principal)
      throws Exception
   {
      try {
         taskName = Tool.byteDecode(taskName);
      }
      catch(Exception ignore) {
      }

      return scheduleTaskService.getDialogModel(taskName, principal);
   }

   @PostMapping(value = "/api/portal/schedule/save")
   public ScheduleTaskDialogModel saveDialogModel(@RequestBody ScheduleTaskEditorModel model,
                                                  @LinkUri String linkURI,
                                                  Principal principal)
      throws Exception
   {
      return scheduleTaskService.saveTask(model, linkURI, principal);
   }

   /**
    * Gets the model for the condition pane in the schedule task dialog
    *
    * @param taskName  the name of the task
    * @param principal the user
    *
    * @return pane model
    * @throws Exception if could not get task
    */
   @GetMapping(value = "/api/portal/schedule/task/conditions")
   public TaskConditionPaneModel getTaskConditions(@RequestParam("name") String taskName,
                                                   Principal principal)
      throws Exception
   {
      return scheduleTaskService.getTaskConditions(taskName, principal);
   }

   /**
    * Gets a model for the actions pane of the schedule task dialog.
    *
    * @param taskName  the name of the task
    * @param principal the user
    *
    * @return the pane model
    * @throws Exception if could not get task
    */
   @GetMapping(value = "/api/portal/schedule/task/actions")
   public TaskActionPaneModel getTaskActions(@RequestParam("name") String taskName,
                                             Principal principal)
      throws Exception
   {
      return scheduleTaskService.getTaskActions(taskName, principal);
   }

   /**
    * Update the task options.
    *
    * @param model the task options model.
    *
    * @throws Exception if could not get or update task
    */
   @PostMapping(value = "/api/portal/schedule/task/options")
   public void setOptions(@RequestBody TaskOptionsPaneModel model,
                          @RequestParam("name") String taskName,
                          @RequestParam("oldTaskName") String oldTaskName,
                          Principal principal)
      throws Exception
   {
      scheduleTaskService.setOptions(model, taskName, oldTaskName, principal);
   }

   @Secured({
      @RequiredPermission(resourceType = ResourceType.PORTAL_TAB, resource = "Schedule"),
      @RequiredPermission(
         resourceType = ResourceType.SCHEDULER,
         resource = "*",
         actions = ResourceAction.ACCESS
      )
   })
   @GetMapping("/api/portal/schedule/enable/task")
   public void toggleTaskEnabled(@RequestParam("name") String taskName, Principal principal)
      throws Exception
   {
      taskName = Tool.byteDecode(taskName);
      ScheduleManager scheduleManager = ScheduleManager.getScheduleManager();
      ScheduleTask task = scheduleManager.getScheduleTask(taskName);

      if(!(SecurityEngine.getSecurity().checkPermission(principal, ResourceType.SCHEDULE_TASK, taskName,
            ResourceAction.WRITE) || (task != null && scheduleTaskService.canDeleteTask(task, principal))))
      {
         return;
      }

      boolean enabled = !scheduleTaskService.isTaskEnabled(taskName);
      scheduleTaskService.setTaskEnabled(taskName, enabled, principal);
   }

   private final ScheduleTaskService scheduleTaskService;
   private final ScheduleTaskFolderService scheduleTaskFolderService;

   private static final Logger LOG =
      LoggerFactory.getLogger(ScheduleTaskController.class);
}
