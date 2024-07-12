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
package inetsoft.web.admin.schedule;

import inetsoft.sree.RepletRepository;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.schedule.*;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.util.Tool;
import inetsoft.web.admin.model.FileData;
import inetsoft.web.admin.schedule.model.*;
import inetsoft.web.viewsheet.service.LinkUri;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;
import org.w3c.dom.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.Principal;
import java.util.*;

@RestController
public class ImportTaskController {
   public ImportTaskController(ScheduleManager scheduleManager, ScheduleTaskFolderService scheduleTaskFolderService) {
      this.scheduleManager = scheduleManager;
      this.scheduleTaskFolderService = scheduleTaskFolderService;
   }

   @PostMapping("/api/em/content/schedule/set-task-file")
   public ImportTaskDialogModel setTaskFile(@RequestBody FileData file, HttpServletRequest request) throws Exception {
      HttpSession session = request.getSession(true);
      InputStream is = new ByteArrayInputStream(Base64.getDecoder().decode(file.content()));
      Document doc = Tool.parseXML(is, "utf-8");
      NodeList list = doc.getElementsByTagName("Task");

      List<TaskDependencyModel> tasklistModel = new ArrayList<>();
      List<ScheduleTask> tasklist = new ArrayList<>();

      for(int i = 0; i < list.getLength(); i++) {
         Element ele = (Element) list.item(i);
         String name = Tool.getAttribute(ele,"name");

         ScheduleTask task = new ScheduleTask();
         task.parseXML(ele);

         TaskDependencyModel model = TaskDependencyModel.builder()
                 .task(name)
                 .dependency(getDependency(task))
                 .build();
         tasklistModel.add(model);
         tasklist.add(task);
      }

      session.setAttribute(INFO_ATTR, tasklist);

      final Optional<Element> timeRanges =
         Optional.ofNullable(Tool.getChildNodeByTagName(doc, "schedule"))
                 .map(scheduleNode -> Tool.getChildNodeByTagName(scheduleNode, "timeRanges"));

      if(timeRanges.isPresent()) {
         list = timeRanges.get().getElementsByTagName("timeRange");

         if(list != null && list.getLength() != 0) {
            List<TimeRange> ranges = new ArrayList<>();

            for(int i = 0; i < list.getLength(); i++) {
               if(list.item(i) instanceof Element) {
                  TimeRange range = new TimeRange();
                  range.parseXML((Element) list.item(i));
                  ranges.add(range);
               }
            }

            TimeRange.setTimeRanges(ranges);
         }
      }

      return ImportTaskDialogModel.builder()
         .tasks(tasklistModel)
         .build();
   }

   @PostMapping("/api/em/content/schedule/import/{overwriting}")
   public ImportTaskResponse importScheduleTask(@RequestBody List<String> selectedTasks, HttpServletRequest request,
                                                @PathVariable("overwriting") boolean overwriting,
                                                @LinkUri String linkURI,
                                                Principal principal) throws Exception
   {
      HttpSession session = request.getSession(true);
      RepletRepository engine = SUtil.getRepletRepository();
      List<String> failedList = new ArrayList<>();

      List<ScheduleTask> tasklist = (ArrayList<ScheduleTask>)session.getAttribute(INFO_ATTR);
      session.removeAttribute(INFO_ATTR);

      for(int i=0; i < tasklist.size(); i++) {
         ScheduleTask task = tasklist.get(i);
         String tname = task.getName();
         String path = task.getPath();

         ScheduleTask oldTask =  scheduleManager.getScheduleTask(tname);
         Boolean taskExists = oldTask != null;

         if(taskExists && overwriting && oldTask.isEditable() &&
            !engine.checkPermission(principal, ResourceType.SCHEDULER, tname, ResourceAction.ACCESS))
         {
            failedList.add(tname);
            continue;
         }

         if(selectedTasks.contains(tname) && (!taskExists || overwriting)) {
            updateTaskInfo(task, linkURI);
            scheduleManager.setScheduleTask(tname, task, principal);
         }

         AssetEntry parentEntry = new AssetEntry(
            AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.SCHEDULE_TASK_FOLDER, path, null);

         if(path != null &&
            scheduleTaskFolderService.checkFolderExists(path))
         {
            moveTask(task, path, principal);
         }
      }

      return ImportTaskResponse.builder()
              .failedTasks(failedList)
              .build();
   }

   private void moveTask(ScheduleTask task, String path, Principal principal) throws Exception {
      ScheduleTaskModel model = ScheduleTaskModel.builder()
         .name(task.getName())
         .owner(task.getOwner())
         .ownerAlias(SUtil.getUserAlias(task.getOwner()))
         .path("/")
         .label("")
         .description("")
         .editable(true)
         .removable(true)
         .enabled(true)
         .schedule("")
         .build();
      ScheduleTaskModel[] taskModels = new ScheduleTaskModel[]{model};
      AssetEntry targetEntry
         = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.SCHEDULE_TASK_FOLDER, path, null);

      scheduleTaskFolderService.moveScheduleItems(taskModels, new String[0], targetEntry, principal);
   }

   private void updateTaskInfo(ScheduleTask task, String linkURI) {
       task.getActionStream().forEach((taction) -> {
           if(taction instanceof AbstractAction) {
               ((AbstractAction)taction).setLinkURI(Tool.replaceLocalhost(linkURI));
           }
       });
   }

   private String getDependency(ScheduleTask task) {
       Enumeration<String> dependencies =  task.getDependency();
       String dependency = "";

       while(dependencies.hasMoreElements()) {
           if(!"".equals(dependency)) {
               dependency += ",";
           }

           dependency += dependencies.nextElement();
       }

       return dependency;
   }

   private final ScheduleManager scheduleManager;
   private final ScheduleTaskFolderService scheduleTaskFolderService;
   private static final String INFO_ATTR = "__private_scheduleXmlInfo";
}
