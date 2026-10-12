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

import inetsoft.sree.AnalyticRepository;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.schedule.*;
import inetsoft.sree.security.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.web.admin.model.FileData;
import inetsoft.web.admin.schedule.model.*;
import inetsoft.web.security.RequiredPermission;
import inetsoft.web.security.Secured;
import inetsoft.web.viewsheet.service.LinkUri;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.w3c.dom.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.Principal;
import java.util.*;

@RestController
public class ImportTaskController {
   public ImportTaskController(ScheduleManager scheduleManager,
                               ScheduleTaskFolderService scheduleTaskFolderService,
                               AnalyticRepository analyticRepository,
                               SecurityEngine securityEngine) {
      this.scheduleManager = scheduleManager;
      this.scheduleTaskFolderService = scheduleTaskFolderService;
      this.analyticRepository = analyticRepository;
      this.securityEngine = securityEngine;
      this.secretIdChecker = new ScheduleSecretIdChecker(securityEngine);
      this.identityChecker = new ScheduleTaskIdentityChecker(securityEngine);
   }

   @Secured(
      @RequiredPermission(
         resourceType = ResourceType.EM_COMPONENT,
         resource = "settings/schedule/tasks",
         actions = ResourceAction.ACCESS
      )
   )
   @PostMapping("/api/em/content/schedule/set-task-file")
   public ImportTaskDialogModel setTaskFile(@RequestBody FileData file, HttpServletRequest request,
                                            Principal principal)
      throws Exception
   {
      HttpSession session = request.getSession(true);
      InputStream is = new ByteArrayInputStream(Base64.getDecoder().decode(file.content()));
      Document doc = Tool.parseXML(is, "utf-8");
      NodeList list = doc.getElementsByTagName("Task");

      List<TaskDependencyModel> tasklistModel = new ArrayList<>();
      List<ScheduleTask> tasklist = new ArrayList<>();

      for(int i = 0; i < list.getLength(); i++) {
         Element ele = (Element) list.item(i);
         String name = Tool.getAttribute(ele,"name");

         ScheduleTask task = parseTask(ele, principal);

         TaskDependencyModel model = TaskDependencyModel.builder()
                 .task(name)
                 // Bug #77283, the import matches the selection against the parsed task id
                 .taskId(task.getTaskId())
                 .dependency(getDependency(task))
                 .build();
         tasklistModel.add(model);
         tasklist.add(task);
      }

      session.setAttribute(INFO_ATTR, tasklist);
      // Bug #77259, the global time ranges are only applied when the import is confirmed
      session.removeAttribute(TIME_RANGES_ATTR);

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

            session.setAttribute(TIME_RANGES_ATTR, ranges);
         }
      }

      return ImportTaskDialogModel.builder()
         .tasks(tasklistModel)
         .build();
   }

   @Secured(
      @RequiredPermission(
         resourceType = ResourceType.EM_COMPONENT,
         resource = "settings/schedule/tasks",
         actions = ResourceAction.ACCESS
      )
   )
   @PostMapping("/api/em/content/schedule/import/{overwriting}")
   public ImportTaskResponse importScheduleTask(@RequestBody List<String> selectedTasks, HttpServletRequest request,
                                                @PathVariable("overwriting") boolean overwriting,
                                                @LinkUri String linkURI,
                                                Principal principal) throws Exception
   {
      HttpSession session = request.getSession(true);
      List<String> failedList = new ArrayList<>();
      List<String> warnings = new ArrayList<>();

      List<ScheduleTask> tasklist = (ArrayList<ScheduleTask>)session.getAttribute(INFO_ATTR);
      session.removeAttribute(INFO_ATTR);
      List<TimeRange> timeRanges = (List<TimeRange>) session.getAttribute(TIME_RANGES_ATTR);
      session.removeAttribute(TIME_RANGES_ATTR);

      // Bug #77360, no uploaded task file in the session (never uploaded, or already imported)
      if(tasklist == null) {
         LOG.warn("No uploaded task file found in the session, the tasks are not imported");
         return ImportTaskResponse.builder()
            .failedTasks(selectedTasks == null ? new ArrayList<>() : new ArrayList<>(selectedTasks))
            .failed(true)
            .build();
      }

      applyTimeRanges(timeRanges, principal);

      for(int i=0; i < tasklist.size(); i++) {
         ScheduleTask task = tasklist.get(i);
         String taskId = task.getTaskId();
         String path = task.getPath();

         ScheduleTask oldTask =  scheduleManager.getScheduleTask(taskId);
         Boolean taskExists = oldTask != null;

         // Bug #77259, check regardless of the stored task's editable flag, internal and MV
         // tasks are not editable
         if(taskExists && overwriting &&
            !analyticRepository.checkPermission(principal, ResourceType.SCHEDULER, taskId, ResourceAction.ACCESS))
         {
            failedList.add(taskId);
            continue;
         }

         if(selectedTasks.contains(taskId) && (!taskExists || overwriting) &&
            !isImportAllowed(task, taskId, principal))
         {
            failedList.add(taskId);
            continue;
         }

         if(selectedTasks.contains(taskId) && (!taskExists || overwriting) &&
            !secretIdChecker.isAllowed(task, oldTask, principal))
         {
            failedList.add(taskId);
            continue;
         }

         if(selectedTasks.contains(taskId) && (!taskExists || overwriting)) {
            // Bug #77350, only a task imported here is moved from the root folder, where
            // setScheduleTask put it, to its folder in the xml
            boolean movable = isMovableToFolder(task, path);

            // Bug #77503, the move is refused without write permission on the folder, check it
            // before the task is saved so the task is reported as failed instead of the refusal
            // aborting the rest of the import. Bug #78136, the write permission is checked on the
            // folder path in the xml before the folder is looked up, so a folder that exists and
            // one that doesn't are refused the same way and the result doesn't tell them apart
            if(movable && !scheduleTaskFolderService.checkFolderPermission(
               path, principal, ResourceAction.WRITE))
            {
               LOG.warn("Task {} is not imported, the user doesn't have the write permission " +
                        "on its folder {}", taskId, path);
               failedList.add(taskId);
               continue;
            }

            // a folder that doesn't exist on this server leaves the task in the root folder
            boolean move = movable && scheduleTaskFolderService.checkFolderExists(path);

            // Bug #77549, #77972, every refusal of setScheduleTask (e.g. a batch action query in
            // another organization or a batch action target task the caller may not see) is
            // checked first so the refusal doesn't abort the rest of the import
            try {
               scheduleManager.checkScheduleTaskSave(taskId, task, principal);
            }
            catch(inetsoft.sree.security.SecurityException | IOException e) {
               LOG.warn("Task {} is not imported: {}", taskId, e.getMessage());
               failedList.add(taskId);
               continue;
            }

            // Bug #77936, a stored password in the file is only kept for the server and user
            // that the replaced task already uses it for, the same as the task editor
            List<String> clearedPaths = identityChecker.isUnrestricted(principal) ?
               Collections.emptyList() :
               ScheduleImportPasswordChecker.clearUnboundPasswords(task, oldTask);

            updateTaskInfo(task, linkURI);
            scheduleManager.setScheduleTask(taskId, task, principal);

            // Bug #77950, tell the user that the saved task needs its passwords re-entered
            if(!clearedPaths.isEmpty()) {
               warnings.add(Catalog.getCatalog(principal).getString(
                  "em.import.task.passwordsCleared", task.getName()));
            }

            // Bug #78140, the task is imported even if the owner's permission couldn't be saved,
            // tell the user, the owner may be unable to delete or rename the task
            if(scheduleManager.isOwnerPermissionMissing(task)) {
               warnings.add(Catalog.getCatalog(principal).getString(
                  "em.import.task.ownerPermissionNotSaved", task.getName()));
            }

            if(move) {
               moveTask(task, path, principal);
            }
         }
      }

      return ImportTaskResponse.builder()
              .failedTasks(failedList)
              .warnings(warnings)
              .build();
   }

   /**
    * Parses an uploaded task. The owner, execute-as identity, completion conditions and actions
    * are moved to the caller's current organization, the same as a site admin deploy import.
    */
   private ScheduleTask parseTask(Element ele, Principal principal) throws Exception {
      return identityChecker.parseImportedTask(ele, principal);
   }

   /**
    * Bug #77259, the uploaded xml is not trusted. Checks the parts of an imported task that the
    * task editor never lets the caller set.
    */
   private boolean isImportAllowed(ScheduleTask task, String taskId, Principal principal)
      throws Exception
   {
      // the task editor requires the scheduler permission regardless of the removable flag
      if(!analyticRepository.checkPermission(principal, ResourceType.SCHEDULER, "*", ResourceAction.ACCESS)) {
         LOG.warn("Task {} is not imported, the user doesn't have the scheduler permission", taskId);
         return false;
      }

      boolean internalType = task.getType() == ScheduleTask.Type.INTERNAL_TASK;

      if(internalType || ScheduleManager.isInternalTask(taskId)) {
         if(!internalType || !ScheduleManager.isInternalTask(task.getName()) ||
            !canWriteInternalTask(task.getName(), principal))
         {
            LOG.warn("Internal task {} is not imported, it's not allowed for the user", taskId);
            return false;
         }

         // internal tasks belong to the host organization and run as the system user
         task.setOwner(new IdentityID(XPrincipal.SYSTEM, Organization.getDefaultOrganizationID()));
         return true;
      }

      if(!identityChecker.isAllowed(task, taskId, principal)) {
         return false;
      }

      for(String internalTask : ScheduleTaskIdentityChecker.getInternalTaskContents(task)) {
         if(!canWriteInternalTask(internalTask, principal)) {
            LOG.warn("Task {} is not imported, it contains the actions or conditions of " +
                     "the internal task {}", taskId, internalTask);
            return false;
         }
      }

      return true;
   }

   /**
    * Same check as editing an internal task in the task editor, org admins are refused.
    */
   private boolean canWriteInternalTask(String name, Principal principal) throws Exception {
      return analyticRepository.checkPermission(
         principal, ResourceType.SCHEDULE_TASK, name, ResourceAction.WRITE);
   }

   private boolean isSiteAdmin(Principal principal) {
      return !securityEngine.isSecurityEnabled() ||
         OrganizationManager.getInstance().isSiteAdmin(principal);
   }

   /**
    * Bug #77259, the time ranges are global, only a user allowed to edit the scheduler settings
    * (and in the host organization when multi-tenant) may replace them.
    */
   private void applyTimeRanges(List<TimeRange> ranges, Principal principal) throws Exception {
      if(ranges == null || ranges.isEmpty()) {
         return;
      }

      boolean allowed = analyticRepository.checkPermission(
         principal, ResourceType.EM_COMPONENT, "settings/schedule/settings", ResourceAction.ACCESS) &&
         (!SUtil.isMultiTenant() || (isSiteAdmin(principal) &&
            Tool.equals(OrganizationManager.getInstance().getCurrentOrgID(principal),
                        Organization.getDefaultOrganizationID())));

      if(!allowed) {
         LOG.debug("Time ranges in the imported file are ignored, the user can't edit them");
         return;
      }

      TimeRange.setTimeRanges(ranges);
   }

   /**
    * Checks if an imported task is moved into its folder in the xml if the folder exists, the
    * same tasks that ScheduleTaskFolderService.getMovableTask() lets the move change. Bug #78136,
    * this doesn't look the folder up, so the folder write check can be done before the lookup.
    */
   private static boolean isMovableToFolder(ScheduleTask task, String path) {
      return path != null && !path.isEmpty() && !"/".equals(path) &&
         task.isRemovable() && !isInternal(task) &&
         task.getType() != ScheduleTask.Type.CYCLE_TASK;
   }

   // Bug #77350, internal tasks are never moved into a folder, and the removable flag of
   // the xml isn't trusted for them
   private static boolean isInternal(ScheduleTask task) {
      return task.getType() == ScheduleTask.Type.INTERNAL_TASK ||
         ScheduleManager.isInternalTask(task.getTaskId());
   }

   private void moveTask(ScheduleTask task, String path, Principal principal) throws Exception {
      String taskId = task.getTaskId();
      boolean internal = isInternal(task);
      ScheduleTaskModel model = ScheduleTaskModel.builder()
         .name(taskId)
         .owner(task.getOwner())
         .ownerAlias(SUtil.getUserAlias(task.getOwner()))
         .path("/")
         .label("")
         .description("")
         .editable(true)
         .removable(task.isRemovable() && !internal)
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
   private final AnalyticRepository analyticRepository;
   private final SecurityEngine securityEngine;
   private final ScheduleSecretIdChecker secretIdChecker;
   private final ScheduleTaskIdentityChecker identityChecker;
   static final String INFO_ATTR = "__private_scheduleXmlInfo";
   static final String TIME_RANGES_ATTR = "__private_scheduleXmlTimeRanges";
   private static final Logger LOG = LoggerFactory.getLogger(ImportTaskController.class);
}
