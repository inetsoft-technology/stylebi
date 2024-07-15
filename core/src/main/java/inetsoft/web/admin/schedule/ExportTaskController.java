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
package inetsoft.web.admin.schedule;

import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.schedule.ScheduleTask;
import inetsoft.web.admin.schedule.model.TaskDependencyModel;
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

      for(String taskName : tasksNames) {
         getTaskRequired(taskName, requiredMap, selectedTaskMap);
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

   private void getTaskRequired(String taskName, Map<String, String> requiredMap, Map<String, String> selectedTaskMap) {
      ScheduleTask task = scheduleManager.getScheduleTask(taskName);

      if(task == null) {
         return;
      }

      Enumeration<String> dependencies =  task.getDependency();

      while(dependencies.hasMoreElements()) {
         String dependency = dependencies.nextElement();
         String requiredBy = requiredMap.get(dependency);

         if(selectedTaskMap.get(dependency) != null) {
            continue;
         }

         if(requiredBy == null || "".equals(requiredBy)) {
            requiredMap.put(dependency, taskName);
            getTaskRequired(dependency, requiredMap, selectedTaskMap);
         }
         else if(requiredBy.indexOf(taskName) < 0) {
            requiredMap.put(dependency, requiredBy + "," + taskName);
            getTaskRequired(dependency, requiredMap, selectedTaskMap);
         }
      }
   }

   /**
    * Export scheduled tasks.
    *
    * @param tasks array for selected tasks
    *
    * @throws Exception if could not get task
    */
   @GetMapping("/em/schedule/export")
   public void exportScheduledTasks(
           @RequestParam("tasks") String tasks,
           HttpServletResponse response) throws Exception
   {
      response.setHeader("Content-disposition",
              "attachment; filename*=utf-8''schedule.xml");
      response.setHeader("extension", "xml");
      response.setHeader("Cache-Control", "");
      response.setHeader("Pragma", "");
      response.setContentType("text/xml");

      String[] tasks2 = tasks.split(",");

      try(OutputStream output = response.getOutputStream()) {
         this.scheduleService.exportScheduledTasks(tasks2, output);
      }
      catch(SocketException ignore) {
      }
   }

   private final ScheduleService scheduleService;
   private final ScheduleManager scheduleManager;
   private static final String INFO_ATTR = "__private_scheduleXmlInfo";
}
