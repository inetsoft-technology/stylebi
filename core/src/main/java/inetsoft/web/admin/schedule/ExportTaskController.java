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
import inetsoft.sree.security.OrganizationManager;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.web.admin.schedule.model.TaskDependencyModel;
import inetsoft.web.security.RequiredPermission;
import inetsoft.web.security.Secured;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;

import java.io.OutputStream;
import java.net.SocketException;
import java.security.Principal;
import java.util.*;

@RestController
public class ExportTaskController {
   public ExportTaskController(ScheduleService scheduleService, ScheduleManager scheduleManager) {
      this.scheduleService = scheduleService;
      this.scheduleManager = scheduleManager;
   }

   @Secured(
      @RequiredPermission(
         resourceType = ResourceType.EM_COMPONENT,
         resource = "settings/schedule/tasks",
         actions = ResourceAction.ACCESS
      )
   )
   @GetMapping("/api/em/schedule/export/get-dependent-tasks")
   public List<TaskDependencyModel> getDependentTasks(
           @RequestParam("tasks") String tasks, Principal principal)
   {
      List<TaskDependencyModel> result = new ArrayList<>();
      String[] tasksNames = tasks.split(",");
      Map<String, String> selectedTaskMap = new HashMap<>();
      Map<String, String> requiredMap = new HashMap<>();

      for(String taskName : tasksNames) {
         selectedTaskMap.put(taskName, taskName);
      }

      String orgID = OrganizationManager.getInstance().getCurrentOrgID(principal);

      for(String taskName : tasksNames) {
         getTaskRequired(taskName, requiredMap, selectedTaskMap, orgID, principal);
      }

      requiredMap.forEach((task, requiredBy) -> {
         TaskDependencyModel model = TaskDependencyModel.builder()
                 .task(task)
                 .dependency(requiredBy)
                 .build();

         result.add(model);
      });

      return result;
   }

   private void getTaskRequired(String taskName, Map<String, String> requiredMap,
                                Map<String, String> selectedTaskMap, String orgID,
                                Principal principal)
   {
      ScheduleTask task = getExportTask(taskName, orgID, principal);

      if(task == null) {
         return;
      }

      Enumeration<String> dependencies =  task.getDependency();

      while(dependencies.hasMoreElements()) {
         String dependency = dependencies.nextElement();
         String requiredBy = requiredMap.get(dependency);

         // only offer dependencies that the user is allowed to export
         if(selectedTaskMap.get(dependency) != null ||
            getExportTask(dependency, orgID, principal) == null)
         {
            continue;
         }

         if(requiredBy == null || "".equals(requiredBy)) {
            requiredMap.put(dependency, taskName);
            getTaskRequired(dependency, requiredMap, selectedTaskMap, orgID, principal);
         }
         else if(requiredBy.indexOf(taskName) < 0) {
            requiredMap.put(dependency, requiredBy + "," + taskName);
            getTaskRequired(dependency, requiredMap, selectedTaskMap, orgID, principal);
         }
      }
   }

   /**
    * Gets a task in the user's organization, or null if it does not exist or the user is not
    * allowed to export it.
    */
   private ScheduleTask getExportTask(String taskName, String orgID, Principal principal) {
      ScheduleTask task = scheduleManager.getScheduleTask(taskName, orgID);
      return task != null && scheduleService.canExportTask(task, principal) ? task : null;
   }

   /**
    * Export scheduled tasks.
    *
    * @param tasks array for selected tasks
    *
    * @throws Exception if could not get task
    */
   @Secured(
      @RequiredPermission(
         resourceType = ResourceType.EM_COMPONENT,
         resource = "settings/schedule/tasks",
         actions = ResourceAction.ACCESS
      )
   )
   @GetMapping("/em/schedule/export")
   public void exportScheduledTasks(
           @RequestParam("tasks") String tasks,
           Principal principal,
           HttpServletResponse response) throws Exception
   {
      // check all the tasks before writing anything, the response can't be changed to an error
      // once the output stream has been written and closed
      List<ScheduleTask> exportTasks = scheduleService.getExportTasks(tasks.split(","), principal);

      response.setHeader("Content-disposition",
              "attachment; filename*=utf-8''schedule.xml");
      response.setHeader("extension", "xml");
      response.setHeader("Cache-Control", "");
      response.setHeader("Pragma", "");
      response.setContentType("text/xml");

      try(OutputStream output = response.getOutputStream()) {
         this.scheduleService.exportScheduledTasks(exportTasks, output);
      }
      catch(SocketException ignore) {
      }
   }

   private final ScheduleService scheduleService;
   private final ScheduleManager scheduleManager;
   private static final String INFO_ATTR = "__private_scheduleXmlInfo";
}
