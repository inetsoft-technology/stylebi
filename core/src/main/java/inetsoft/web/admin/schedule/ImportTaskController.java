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
import inetsoft.uql.util.Identity;
import inetsoft.util.ThreadContext;
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
import java.io.InputStream;
import java.security.Principal;
import java.util.*;

@RestController
public class ImportTaskController {
   public ImportTaskController(ScheduleManager scheduleManager,
                               ScheduleTaskFolderService scheduleTaskFolderService,
                               AnalyticRepository analyticRepository,
                               SecurityEngine securityEngine,
                               ScheduleTaskService scheduleTaskService) {
      this.scheduleManager = scheduleManager;
      this.scheduleTaskFolderService = scheduleTaskFolderService;
      this.analyticRepository = analyticRepository;
      this.securityEngine = securityEngine;
      this.scheduleTaskService = scheduleTaskService;
      this.secretIdChecker = new ScheduleSecretIdChecker(securityEngine);
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

      List<ScheduleTask> tasklist = (ArrayList<ScheduleTask>)session.getAttribute(INFO_ATTR);
      session.removeAttribute(INFO_ATTR);
      List<TimeRange> timeRanges = (List<TimeRange>) session.getAttribute(TIME_RANGES_ATTR);
      session.removeAttribute(TIME_RANGES_ATTR);
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
            updateTaskInfo(task, linkURI);
            scheduleManager.setScheduleTask(taskId, task, principal);
         }

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

   /**
    * Parses an uploaded task. The owner, execute-as identity, completion conditions and actions
    * are moved to the caller's current organization, the same as a site admin deploy import.
    */
   private ScheduleTask parseTask(Element ele, Principal principal) throws Exception {
      ScheduleTask task = new ScheduleTask();
      Principal oldPrincipal = ThreadContext.getContextPrincipal();

      // parseXML(elem, true) takes the organization from the context principal, through the
      // no-arg OrganizationManager.getCurrentOrgID() which lower-cases it, the same as a deploy
      // import and the task permission grant in ScheduleManager.setScheduleTask
      if(principal != null) {
         ThreadContext.setContextPrincipal(principal);
      }

      try {
         task.parseXML(ele, true);
      }
      finally {
         if(principal != null) {
            ThreadContext.setContextPrincipal(oldPrincipal);
         }
      }

      return task;
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

      if(!isSiteAdmin(principal) && !isOwnerAndIdentityAllowed(task, taskId, principal)) {
         return false;
      }

      for(String internalTask : getInternalTaskContents(task)) {
         if(!canWriteInternalTask(internalTask, principal)) {
            LOG.warn("Task {} is not imported, it contains the actions or conditions of " +
                     "the internal task {}", taskId, internalTask);
            return false;
         }
      }

      return true;
   }

   /**
    * Bug #77259, a caller that is not a site admin may only import a task owned by and run as an
    * identity the task editor lets the caller pick. The owner must be the caller or an existing
    * user of the current organization the caller administers (the editor's owner list), it's
    * refused rather than replaced so the task id shown in the import dialog stays the same. An
    * owner that doesn't exist would make the task run with the roles of a site admin of the same
    * name in another organization (SUtil.getScheduleTaskOwnerPrincipal), and the legacy "null"
    * owner is the host organization system user.
    */
   private boolean isOwnerAndIdentityAllowed(ScheduleTask task, String taskId,
                                             Principal principal)
   {
      IdentityID owner = task.getOwner();
      IdentityID caller = IdentityID.getIdentityIDFromKey(principal.getName());
      // the owner org was remapped by parseXML(elem, true) to the lower-cased current org
      String orgID = OrganizationManager.getInstance().getCurrentOrgID(principal);
      boolean ownerAllowed = owner != null && owner.getOrgID() != null &&
         owner.getOrgID().equalsIgnoreCase(orgID) &&
         !XPrincipal.SYSTEM.equals(owner.getName()) &&
         !XPrincipal.ANONYMOUS.equals(owner.getName()) &&
         (owner.equals(caller) ||
            securityEngine.getSecurityProvider().getUser(owner) != null &&
            scheduleTaskService.checkUserPermission(owner.convertToKey(), principal));

      if(!ownerAllowed) {
         LOG.warn("Task {} is not imported, the owner {} is not allowed", taskId, owner);
         return false;
      }

      Identity identity = task.getIdentity();

      if(identity == null) {
         return true;
      }

      IdentityID identityID = identity.getIdentityID();

      // the task editor only lets the caller run a task as a user or group of the current
      // organization that the caller administers, a global identity (such as a system
      // administrator role) is refused
      boolean identityAllowed = identityID != null && identityID.getOrgID() != null &&
         identityID.getOrgID().equalsIgnoreCase(orgID) &&
         (identity.getType() == Identity.USER && (identityID.equals(caller) ||
            scheduleTaskService.getExecuteAsUsers(owner, principal).contains(identityID)) ||
          identity.getType() == Identity.GROUP &&
            scheduleTaskService.getExecuteAsGroups(owner.convertToKey(), principal)
               .contains(identityID));

      if(!identityAllowed) {
         LOG.warn("Task {} is not imported, the execute-as identity {} is not allowed",
                  taskId, identityID);
      }

      return identityAllowed;
   }

   /**
    * Gets the names of the internal tasks whose actions or conditions are in the task, or that
    * a completion condition of the task depends on.
    */
   private Set<String> getInternalTaskContents(ScheduleTask task) {
      Set<String> names = new HashSet<>();

      for(int i = 0; i < task.getActionCount(); i++) {
         ScheduleAction action = task.getAction(i);

         if(action instanceof AssetFileBackupAction) {
            names.add(InternalScheduledTaskService.ASSET_FILE_BACKUP);
         }
         else if(action instanceof TaskBalancerAction) {
            names.add(InternalScheduledTaskService.BALANCE_TASKS);
         }
         else if(action instanceof UpdateAssetsDependenciesAction) {
            names.add(InternalScheduledTaskService.UPDATE_ASSETS_DEPENDENCIES);
         }
      }

      for(int i = 0; i < task.getConditionCount(); i++) {
         ScheduleCondition condition = task.getCondition(i);

         if(condition instanceof TaskBalancerCondition) {
            names.add(InternalScheduledTaskService.BALANCE_TASKS);
         }
         else if(condition instanceof CompletionCondition completion &&
            ScheduleManager.isInternalTask(completion.getTaskName()))
         {
            names.add(completion.getTaskName());
         }
      }

      return names;
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

   private void moveTask(ScheduleTask task, String path, Principal principal) throws Exception {
      ScheduleTaskModel model = ScheduleTaskModel.builder()
         .name(task.getTaskId())
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
   private final AnalyticRepository analyticRepository;
   private final SecurityEngine securityEngine;
   private final ScheduleTaskService scheduleTaskService;
   private final ScheduleSecretIdChecker secretIdChecker;
   static final String INFO_ATTR = "__private_scheduleXmlInfo";
   static final String TIME_RANGES_ATTR = "__private_scheduleXmlTimeRanges";
   private static final Logger LOG = LoggerFactory.getLogger(ImportTaskController.class);
}
