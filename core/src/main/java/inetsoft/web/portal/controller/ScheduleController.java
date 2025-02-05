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

import inetsoft.report.internal.paging.ReportCache;
import inetsoft.sree.AnalyticRepository;
import inetsoft.sree.internal.DataCycleManager;
import inetsoft.sree.schedule.*;
import inetsoft.sree.security.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.util.*;
import inetsoft.web.admin.schedule.ScheduleService;
import inetsoft.web.admin.schedule.model.*;
import inetsoft.web.security.RequiredPermission;
import inetsoft.web.security.Secured;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Controller that provides a REST endpoint used for actions on scheduled tasks.
 *
 * @since 12.3
 */
@RestController
public class ScheduleController {
   /**
    * Creates a new instance of <tt>ScheduleController</tt>.
    *
    * @param analyticRepository the analytic repository.
    * @param scheduleManager    the schedule manager.
    * @param scheduleClient     the schedule client.
    * @param scheduleService    the schedule service.
    */
   @Autowired
   public ScheduleController(AnalyticRepository analyticRepository,
                             ScheduleManager scheduleManager,
                             ScheduleClient scheduleClient,
                             ScheduleService scheduleService,
                             SecurityProvider securityProvider)
   {
      this.analyticRepository = analyticRepository;
      this.scheduleManager = scheduleManager;
      this.scheduleClient = scheduleClient;
      this.scheduleService = scheduleService;
      this.securityProvider = securityProvider;
   }

   /**
    * Gets a table of scheduled tasks.
    *
    * @param selectStr  the selection string
    * @param filter     filter
    *
    * @return table of tasks
    * @throws Exception if could not get tasks
    */
   @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
   @PostMapping("/api/portal/scheduledTasks")
   @ResponseBody
   @Secured({
      @RequiredPermission(resourceType = ResourceType.PORTAL_TAB, resource = "Schedule"),
      @RequiredPermission(
         resourceType = ResourceType.SCHEDULER,
         resource = "*",
         actions = ResourceAction.ACCESS
      )
   })
   public ScheduleTaskList getScheduledTasks(
      @RequestParam("selectString") Optional<String> selectStr,
      @RequestParam("filter") Optional<String> filter,
      @RequestBody(required = false) AssetEntry parentEntry,
      Principal principal) throws Exception
   {
      IdentityID pId = IdentityID.getIdentityIDFromKey(principal.getName());
      ScheduleTaskList taskListModel = scheduleService.getScheduleTaskList(selectStr.orElse(""),
         filter.orElse(""), parentEntry, principal);
     final boolean isAdmin = isSystemAdministratorRole(pId) ||
                             isOrgAdministratorRole(pId) ;

      List<ScheduleTaskModel> filteredList = taskListModel.tasks().stream()
         .filter(this::isVisibleInPortal)
         .filter(t -> Objects.equals(pId, t.owner())
            || !Objects.isNull(t.name()) && t.name().contains(DataCycleManager.TASK_PREFIX) && isAdmin
            || scheduleService.isGroupShare(t, principal)
         )
         .collect(Collectors.toList());
      return ScheduleTaskList.builder()
         .from(taskListModel)
         .tasks(filteredList)
         .build();
   }

   private boolean isSystemAdministratorRole(IdentityID userName) {
      AuthenticationProvider authentication = securityProvider.getAuthenticationProvider();

      if(authentication.isVirtual()) {
         User user = authentication.getUser(userName);

         if(user != null && user.getRoles() != null) {
            return Arrays.stream(user.getRoles()).anyMatch(securityProvider::isSystemAdministratorRole);
         }
      }

      for(IdentityID str : authentication.getRoles(userName)) {
         if(authentication.isSystemAdministratorRole(str)) {
            return true;
         }
      }

      return false;
   }

   private boolean isOrgAdministratorRole(IdentityID userName) {
      AuthenticationProvider authentication = securityProvider.getAuthenticationProvider();

      for(IdentityID str : authentication.getRoles(userName)) {
         if(authentication.isOrgAdministratorRole(str)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Remove a scheduled task.
    *
    * @param selectStr the selection string
    * @param filter    filter
    * @throws Exception if could not get task
    */
   @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
   @PostMapping("/api/portal/schedule/remove")
   @ResponseBody
   @Secured({
      @RequiredPermission(resourceType = ResourceType.PORTAL_TAB, resource = "Schedule"),
      @RequiredPermission(
         resourceType = ResourceType.SCHEDULER,
         resource = "*",
         actions = ResourceAction.ACCESS
      )
   })
   public void removeScheduledTask(
      @RequestParam("selectString") Optional<String> selectStr,
      @RequestParam("filter") Optional<String> filter,
      @RequestBody ScheduleTaskModel[] tasks, Principal principal) throws Exception
   {
      this.scheduleService.removeScheduleItems(tasks, selectStr, filter, principal);
   }

   @PostMapping("/api/portal/schedule/check-dependency")
   @ResponseBody
   @Secured({
      @RequiredPermission(resourceType = ResourceType.PORTAL_TAB, resource = "Schedule"),
      @RequiredPermission(
         resourceType = ResourceType.SCHEDULER,
         resource = "*",
         actions = ResourceAction.ACCESS
      )
   })
   public TaskListModel checkScheduledTaskDependency(
      @RequestParam("selectString") Optional<String> selectStr,
      @RequestParam("filter") Optional<String> filter,
      @RequestBody(required = false) ScheduleTaskModel[] tasks, Principal principal) throws Exception
   {
      return this.scheduleService.checkScheduledTaskDependency(tasks, selectStr.orElse(""),
         filter.orElse(""), principal);
   }

   @PostMapping("/api/portal/schedule/folder/check-dependency")
   public TaskListModel checkScheduleFolderDependency(
      @RequestBody TaskListModel model, Principal principal) throws Exception
   {
      return this.scheduleService.checkScheduleFolderDependency(model, principal);
   }

   @PostMapping("/api/portal/schedule/folder/remove")
   public TaskRemoveResult removeScheduleFolders(@RequestBody TaskListModel model,
                                                 Principal principal)
      throws Exception
   {
      TaskRemoveResult result = new TaskRemoveResult();
      this.scheduleService.removeScheduleFolders(model, principal, result);
      return result;
   }

   /**
    * Runs the specified task.
    *
    * @param taskName  the name of the task
    * @param principal the user
    *
    * @throws Exception if could not get task
    */
   @GetMapping("/api/portal/schedule/run")
   @ResponseBody
   @Secured({
      @RequiredPermission(resourceType = ResourceType.PORTAL_TAB, resource = "Schedule"),
      @RequiredPermission(
         resourceType = ResourceType.SCHEDULER,
         resource = "*",
         actions = ResourceAction.ACCESS
      )
   })
   public void runScheduledTask(@RequestParam("name") String taskName,
                                Principal principal)
      throws Exception
   {
      try {
         scheduleService.runScheduledTask(taskName, principal);
      }
      catch(MessageException e) {
         throw e;
      }
      catch(Exception e) {
         throw new MessageException(e.getMessage());
      }
   }

   /**
    * Stops specified running task.
    *
    * @param taskName  the name of the task
    * @param principal the user
    *
    * @throws Exception if could not get task
    */
   @RequestMapping(
      value = "/api/portal/schedule/stop",
      method = RequestMethod.GET
   )
   @ResponseBody
   @Secured({
      @RequiredPermission(resourceType = ResourceType.PORTAL_TAB, resource = "Schedule"),
      @RequiredPermission(
         resourceType = ResourceType.SCHEDULER,
         resource = "*",
         actions = ResourceAction.ACCESS
      )
   })
   public void stopScheduledTask(@RequestParam("name") String taskName, Principal principal)
      throws Exception
   {
      scheduleService.stopScheduledTask(taskName, principal);
   }

   @GetMapping("/api/portal/schedule/users-model")
   public UsersModel getUsersModel(Principal principal) throws Exception
   {
      return scheduleService.getUsersModel(principal);
   }

   @GetMapping("/api/portal/schedule/isSelfOrgUser")
   public boolean isSelfOrgUser(Principal principal) throws Exception
   {
      return principal instanceof SRPrincipal && ((SRPrincipal) principal).isSelfOrganization();
   }

   /**
    * Get status description.
    */
   private String getStatusDescription(Catalog catalog, int status) {
      switch(status) {
      case ReportCache.COMPLETED :
         return catalog.getString("Completed");
      case ReportCache.PENDING :
         return catalog.getString("Pending");
      case ReportCache.FAILED :
         return catalog.getString("Failed");
      default :
         return catalog.getString("Running");
      }
   }

   private String formatInterval(long interval) {
      long t = interval / 1000L;
      long seconds = t % 60;
      t /= 60;
      long minutes = t % 60;
      t /= 60;
      return String.format("%d:%02d:%02d", t, minutes, seconds);
   }

   /**
    * @param taskModel the task model to check
    *
    * @return true if the task should be visible in the portal, false otherwise
    */
   private boolean isVisibleInPortal(ScheduleTaskModel taskModel) {
      String taskName = taskModel.name();

      if(ScheduleManager.isInternalTask(taskName)) {
         return false;
      }

      final IdentityID owner = taskModel.owner();

      if(owner != null && !taskName.startsWith(owner.convertToKey() + ":")) {
         taskName = owner.convertToKey() + ":" + taskName;
      }

      final ScheduleTask scheduleTask = scheduleManager.getScheduleTask(taskName);

      if(scheduleTask != null) {
         return scheduleTask.getActionStream()
            .noneMatch((action) -> action instanceof IndividualAssetBackupAction ||
               action instanceof BatchAction);
      }

      return true;
   }

   private static final int TASK_EDITABLE = 0x01;
   private static final int TASK_REMOVABLE = 0x02;

   private final AnalyticRepository analyticRepository;
   private final ScheduleManager scheduleManager;
   private final ScheduleClient scheduleClient;
   private final ScheduleService scheduleService;
   private final SecurityProvider securityProvider;
   private static final Logger LOG =
      LoggerFactory.getLogger(ScheduleController.class);
}
