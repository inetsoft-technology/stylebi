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
import inetsoft.util.health.HealthStatus;
import inetsoft.util.health.SchedulerStatus;
import inetsoft.util.*;
import inetsoft.web.admin.schedule.ScheduleService;
import inetsoft.web.admin.schedule.model.*;
import inetsoft.web.portal.model.PortalSchedulerHealthModel;
import inetsoft.web.security.*;
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

   @Secured({
      @RequiredPermission(resourceType = ResourceType.PORTAL_TAB, resource = "Schedule"),
      @RequiredPermission(
         resourceType = ResourceType.SCHEDULER,
         resource = "*",
         actions = ResourceAction.ACCESS
      )
   })
   @PostMapping("/api/portal/schedule/folder/check-dependency")
   public TaskListModel checkScheduleFolderDependency(
      @RequestBody TaskListModel model, Principal principal) throws Exception
   {
      return this.scheduleService.checkScheduleFolderDependency(model, principal);
   }

   @Secured({
      @RequiredPermission(resourceType = ResourceType.PORTAL_TAB, resource = "Schedule"),
      @RequiredPermission(
         resourceType = ResourceType.SCHEDULER,
         resource = "*",
         actions = ResourceAction.ACCESS
      )
   })
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

   @Secured({
      @RequiredPermission(resourceType = ResourceType.PORTAL_TAB, resource = "Schedule"),
      @RequiredPermission(
         resourceType = ResourceType.SCHEDULER,
         resource = "*",
         actions = ResourceAction.ACCESS
      )
   })
   @GetMapping("/api/portal/schedule/users-model")
   public UsersModel getUsersModel(Principal principal) throws Exception
   {
      return scheduleService.getUsersModel(principal);
   }

   @Secured({
      @RequiredPermission(resourceType = ResourceType.PORTAL_TAB, resource = "Schedule"),
      @RequiredPermission(
         resourceType = ResourceType.SCHEDULER,
         resource = "*",
         actions = ResourceAction.ACCESS
      )
   })
   @GetMapping("/api/portal/schedule/task-names")
   public ScheduleTaskNamesModel getScheduleTaskNamesModel(@PermissionUser Principal principal) throws Exception
   {
      return scheduleService.getScheduleTaskNamesModel(principal);
   }

   @Secured({
      @RequiredPermission(resourceType = ResourceType.PORTAL_TAB, resource = "Schedule"),
      @RequiredPermission(
         resourceType = ResourceType.SCHEDULER,
         resource = "*",
         actions = ResourceAction.ACCESS
      )
   })
   @GetMapping("/api/portal/schedule/isSelfOrgUser")
   public boolean isSelfOrgUser(Principal principal) throws Exception
   {
      return principal instanceof SRPrincipal && ((SRPrincipal) principal).isSelfOrganization();
   }

   @Secured({
      @RequiredPermission(resourceType = ResourceType.PORTAL_TAB, resource = "Schedule"),
      @RequiredPermission(
         resourceType = ResourceType.SCHEDULER,
         resource = "*",
         actions = ResourceAction.ACCESS
      )
   })
   @GetMapping("/api/portal/schedule/health")
   public PortalSchedulerHealthModel getSchedulerHealth(Principal principal) {
      Catalog catalog = Catalog.getCatalog(principal);
      ScheduleClient client = ScheduleClient.getScheduleClient();

      try {
         if(!client.isReady()) {
            return PortalSchedulerHealthModel.builder()
               .available(true)
               .healthy(false)
               .started(false)
               .shutdown(true)
               .standby(false)
               .lastCheck(0L)
               .nextCheck(0L)
               .executingCount(0)
               .threadCount(0)
               .statusLabel(catalog.getString("Stopped"))
               .detailMessage("Scheduler is stopped, so scheduled tasks will not run automatically.")
               .build();
         }

         Optional<HealthStatus> healthStatus = client.getHealthStatus();

         if(healthStatus.isEmpty() || healthStatus.get().getSchedulerStatus() == null) {
            return PortalSchedulerHealthModel.builder()
               .available(false)
               .healthy(false)
               .started(false)
               .shutdown(false)
               .standby(false)
               .lastCheck(0L)
               .nextCheck(0L)
               .executingCount(0)
               .threadCount(0)
               .statusLabel("Unavailable")
               .detailMessage("Scheduler health is currently unavailable.")
               .build();
         }

         SchedulerStatus status = healthStatus.get().getSchedulerStatus();
         return PortalSchedulerHealthModel.builder()
            .available(true)
            .healthy(status.isHealthy())
            .started(status.isStarted())
            .shutdown(status.isShutdown())
            .standby(status.isStandby())
            .lastCheck(status.getLastCheck())
            .nextCheck(status.getNextCheck())
            .executingCount(status.getExecutingCount())
            .threadCount(status.getThreadCount())
            .statusLabel(getSchedulerStatusLabel(status, catalog))
            .detailMessage(getSchedulerStatusMessage(status))
            .build();
      }
      catch(Exception e) {
         LOG.warn("Failed to get scheduler health for portal", e);

         if(!client.isReady()) {
            return PortalSchedulerHealthModel.builder()
               .available(true)
               .healthy(false)
               .started(false)
               .shutdown(true)
               .standby(false)
               .lastCheck(0L)
               .nextCheck(0L)
               .executingCount(0)
               .threadCount(0)
               .statusLabel(catalog.getString("Stopped"))
               .detailMessage("Scheduler is stopped, so scheduled tasks will not run automatically.")
               .build();
         }

         return PortalSchedulerHealthModel.builder()
            .available(false)
            .healthy(false)
            .started(false)
            .shutdown(false)
            .standby(false)
            .lastCheck(0L)
            .nextCheck(0L)
            .executingCount(0)
            .threadCount(0)
            .statusLabel("Unavailable")
            .detailMessage("Scheduler health could not be retrieved.")
            .build();
      }
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

   private String getSchedulerStatusLabel(SchedulerStatus status, Catalog catalog) {
      if(status.isHealthy()) {
         return catalog.getString("Running");
      }

      if(status.isShutdown()) {
         return catalog.getString("Stopped");
      }

      if(status.isStandby()) {
         return "Standby";
      }

      if(!status.isStarted()) {
         return "Not started";
      }

      return "Degraded";
   }

   private String getSchedulerStatusMessage(SchedulerStatus status) {
      if(status.isHealthy()) {
         return null;
      }

      if(status.isShutdown()) {
         return "Scheduler is shut down, so scheduled tasks will not run automatically.";
      }

      if(status.isStandby()) {
         return "Scheduler is in standby, so scheduled tasks will not run automatically.";
      }

      if(!status.isStarted()) {
         return "Scheduler has not started yet, so scheduled tasks cannot run automatically.";
      }

      return "Scheduler health checks are stale or worker capacity is exhausted, so scheduled task execution may be impacted.";
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
   private final ScheduleService scheduleService;
   private final SecurityProvider securityProvider;
   private static final Logger LOG =
      LoggerFactory.getLogger(ScheduleController.class);
}
