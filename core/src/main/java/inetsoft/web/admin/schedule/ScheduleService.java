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

import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.report.internal.license.LicenseManager;
import inetsoft.report.io.csv.CSVConfig;
import inetsoft.sree.*;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.schedule.*;
import inetsoft.sree.security.SecurityException;
import inetsoft.sree.security.*;
import inetsoft.uql.XDataSource;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetFolder;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.sync.*;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.viewsheet.VSBookmark;
import inetsoft.uql.viewsheet.VSBookmarkInfo;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.*;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import inetsoft.util.dep.*;
import inetsoft.util.log.LogLevel;
import inetsoft.web.admin.content.repository.ContentRepositoryTreeService;
import inetsoft.web.admin.content.repository.ResourcePermissionService;
import inetsoft.web.admin.content.repository.model.SelectedAssetModel;
import inetsoft.web.admin.deploy.DeployService;
import inetsoft.web.admin.deploy.DeployUtil;
import inetsoft.web.admin.presentation.PresentationFormatsSettingsService;
import inetsoft.web.admin.schedule.model.*;
import inetsoft.web.composer.model.vs.DynamicValueModel;
import inetsoft.web.portal.model.CSVConfigModel;
import inetsoft.web.viewsheet.controller.dialog.EmailDialogController;
import inetsoft.web.viewsheet.model.VSBookmarkInfoModel;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.util.comparator.Comparators;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Singleton used for some reusable scheduled task related methods.
 *
 * @since 12.3
 */
@Service
public class ScheduleService {
   /**
    * Creates a new instance of <tt>ScheduleController</tt>.
    *
    * @param analyticRepository the analytic repository.
    * @param scheduleManager    the schedule manager.
    * @param scheduleClient     the schedule client.
    */
   @Autowired
   public ScheduleService(AnalyticRepository analyticRepository,
                          ScheduleManager scheduleManager,
                          ScheduleClient scheduleClient,
                          ScheduleConditionService scheduleConditionService,
                          SecurityProvider securityProvider,
                          DeployService deployService,
                          ResourcePermissionService resourcePermissionService,
                          PresentationFormatsSettingsService formatsSettingsService,
                          SecurityEngine securityEngine,
                          ScheduleTaskFolderService taskFolderService)
   {
      this.analyticRepository = analyticRepository;
      this.scheduleManager = scheduleManager;
      this.scheduleClient = scheduleClient;
      this.scheduleConditionService = scheduleConditionService;
      this.securityProvider = securityProvider;
      this.deployService = deployService;
      this.resourcePermissionService = resourcePermissionService;
      this.formatsSettingsService = formatsSettingsService;
      this.securityEngine = securityEngine;
      this.taskFolderService = taskFolderService;

      try {
         indexedStorage = IndexedStorage.getIndexedStorage();
      }
      catch(Exception e) {
         throw new RuntimeException("Failed to get indexed storage", e);
      }
   }

   /**
    * Gets the task name with the user principal prepended if security is enabled.
    *
    * @param taskName  the task name
    * @param principal the user principal
    *
    * @return the taskname with the principal name prepended if security enabled
    */
   public String getTaskName(String taskName, Principal principal) {
      return isSecurityEnabled() && !taskName.startsWith(principal.getName() + ":") &&
         !taskName.contains(IdentityID.KEY_DELIMITER) ?
         principal.getName() + ":" + taskName : taskName;
   }

   /**
    * Update the task name
    *
    * @param oldName  the old task name.
    * @param taskName the current task name.
    *
    * @throws Exception if could not get or update task
    */
   public String updateTaskName(String oldName, String taskName, IdentityID owner,
                                Principal principal)
      throws Exception
   {
      oldName = Tool.byteDecode(oldName);
      taskName = Tool.byteDecode(taskName);

      if(owner != null) {
         if(!taskName.startsWith(owner.convertToKey() + ":")) {
            taskName = owner.convertToKey() + ":" + taskName;
         }
      }
      else {
         taskName = getTaskName(taskName, principal);
      }

      if(!taskName.equals(oldName)) {
         String path = scheduleManager.getScheduleTask(oldName).getPath();
         RenameTransformHandler.getTransformHandler().addTransformTask(
            getDependencyInfo(oldName, taskName, path, path));

         if(renameTask(oldName, taskName, owner, principal)) {
            return taskName;
         }
      }

      return oldName;
   }

   public static RenameDependencyInfo getDependencyInfo(String oname, String nname,
                                                        String oldFolder, String newFolder)
   {
      RenameDependencyInfo dinfo = new RenameDependencyInfo();
      List<RenameInfo> rinfos = new ArrayList<>();
      AssetEntry oentry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
         AssetEntry.Type.SCHEDULE_TASK, "/" + oname, null);
      String oldKey = oentry.toIdentifier();
      List<AssetObject> entries = DependencyTransformer.getDependencies(oldKey);
      ScheduleManager manager = ScheduleManager.getScheduleManager();

      if(oldFolder == newFolder && oldFolder == null) {
         String path = manager.getScheduleTask(oname).getPath();
         oldFolder = path;
         newFolder = path;
      }

      if(entries != null && !entries.isEmpty()) {
         RenameInfo rinfo = new RenameInfo(oname, nname, RenameInfo.TASK);
         rinfo.setOldPath(oldFolder);
         rinfo.setNewPath(newFolder);

         for(AssetObject entry : entries) {
            if(entry instanceof AssetEntry && ((AssetEntry) entry).isScheduleTask()) {
               dinfo.addRenameInfo(entry, rinfo);
            }
         }

         rinfos.add(rinfo);
      }

      dinfo.setRenameInfos(rinfos);
      return dinfo;
   }

   /**
    * Update the task name
    *
    * @param oldName  the old task name.
    * @param taskName the current task name.
    *
    * @throws Exception if could not get or update task
    */
   public String updateTaskName(String oldName, String taskName, Principal principal)
      throws Exception
   {
      return updateTaskName(oldName, taskName, null, principal);
   }

   /**
    * Sets a condition on a task.
    *
    * @param taskName the name of the task.
    * @param index    the index of the condition.
    * @param model    the DTO containing the condition definition.
    * @param catalog  the localization catalog.
    * @param task     the task.
    *
    * @return the time range that needs to be rebalanced or {@code null} if none.
    *
    * @throws Exception if the task could not be saved.
    */
   public TimeRange setTaskCondition(String taskName, int index, ScheduleConditionModel model,
                                     Catalog catalog, ScheduleTask task)
      throws Exception
   {
      ScheduleCondition condition = scheduleConditionService.getConditionFromModel(model);
      ScheduleCondition ocondition = null;

      if(index < 0 || index >= task.getConditionCount()) {
         task.addCondition(condition);
      }
      else {
         ocondition = task.getCondition(index);
         task.setCondition(index, condition);
      }

      if(condition instanceof CompletionCondition) {
         String tname = ((CompletionCondition) condition).getTaskName();

         if(isLoop(tname, taskName)) {
            if(index < 0) {
               task.removeCondition(task.getConditionCount() - 1);
            }
            else {
               task.setCondition(index, ocondition);
            }

            throw new MessageException(catalog.getString("common.dependencyCycle"));
         }
      }
      else if(condition instanceof TimeCondition) {
         TimeCondition timeCondition = (TimeCondition) condition;
         TimeCondition oTimeCondition =
            (ocondition instanceof TimeCondition) ? ((TimeCondition) ocondition) : null;

         if(timeCondition.getTimeRange() != null) {
            if(oTimeCondition != null &&
               timeCondition.getTimeRange().equals(oTimeCondition.getTimeRange()))
            {
               timeCondition.setHour(oTimeCondition.getHour());
               timeCondition.setMinute(oTimeCondition.getMinute());
               timeCondition.setSecond(oTimeCondition.getSecond());
            }
            else {
               return timeCondition.getTimeRange();
            }
         }
      }

      return null;
   }

   /**
    * Return Dependency cycle found or not.
    */
   private boolean isLoop(String task1, String src) {
      ScheduleTask task = scheduleManager.getScheduleTask(task1);

      if(task == null) {
         return false;
      }

      Enumeration<?> enumeration = task.getDependency();

      while(enumeration.hasMoreElements()) {
         String taskName = (String) enumeration.nextElement();

         if(taskName != null && taskName.equals(src)) {
            return true;
         }

         if(isLoop(taskName, src)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Rename the task.
    */
   private boolean renameTask(String oldId, String newId, IdentityID owner, Principal principal)
      throws Exception
   {
      Catalog catalog = Catalog.getCatalog(principal);
      String orgId = OrganizationManager.getInstance().getCurrentOrgID(principal);
      Vector<ScheduleTask> allTasks =
         getScheduleTasks(null, null, false, principal);

      if(oldId == null || "".equals(oldId) || newId == null || "".equals(newId)) {
         throw new Exception(catalog.getString("em.scheduler.emptyTaskName"));
      }

      Vector<ScheduleExt> extensions = scheduleManager.getExtensions();
      boolean contained = false;

      for(ScheduleExt extension : extensions) {
         if(extension.containsTask(newId, orgId)) {
            contained = true;
            break;
         }
      }

      if(scheduleManager.getScheduleTask(newId) != null || contained) {
         throw new MessageException(catalog.getString("em.schedule.task.duplicateName"));
      }

      if(!newId.equals(oldId)) {
         if(scheduleManager.hasDependency(allTasks, newId)) {
            throw new MessageException(catalog.getString(
               "em.schedule.task.renameDependency", oldId));
         }
      }

      ScheduleTask currTask = scheduleManager.getScheduleTask(oldId);

      if(currTask == null) {
         throw new MessageException(catalog.getString(
            "em.scheduler.taskNotFound", oldId));
      }

      if(scheduleManager.hasDependency(allTasks, oldId)) {
         oldId = SUtil.getTaskNameWithoutOrg(oldId);

         throw new MessageException(catalog.getString(
            "em.schedule.task.renameDependency", oldId));
      }

      scheduleManager.removeScheduleTask(oldId, principal);
      String newName = newId;

      if(currTask.getOwner() != null && owner != null)
      {
         newName = newId.substring(owner.convertToKey().length() + 1);
      }

      currTask.setName(newName);

      // log rename task action
      String actionName = ActionRecord.ACTION_NAME_RENAME;
      String objectType = ActionRecord.OBJECT_TYPE_TASK;
      Timestamp actionTimestamp = new Timestamp(System.currentTimeMillis());
      ActionRecord actionRecord = new ActionRecord(SUtil.getUserName(principal), actionName, oldId,
                                      objectType, actionTimestamp, ActionRecord.ACTION_STATUS_FAILURE,
                                      null);

      try {
         String oldPath = currTask.getPath();
         AssetEntry folderEntry = null;

         if(!StringUtils.isEmpty(oldPath)) {
            folderEntry = new AssetEntry( AssetRepository.GLOBAL_SCOPE,
               AssetEntry.Type.SCHEDULE_TASK_FOLDER, oldPath, null);
         }
         else {
            oldPath = null;
         }

         scheduleManager.setScheduleTask(newId, currTask, folderEntry, principal);
         taskFolderService.removeTaskFromFolder(oldId, oldPath);
         actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_SUCCESS);
         actionRecord.setActionError("new name: " + SUtil.getTaskName(newId));
      }
      catch(Exception ex) {
         actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_FAILURE);
         actionRecord.setActionError(ex.getMessage() + ", new name: " + newId);
         throw ex;
      }
      finally {
         Audit.getInstance().auditAction(actionRecord, principal);
      }

      return true;
   }

   public void configureAssetFileBackupTask(ScheduleTask assetFileBackupTask) {
      TimeCondition condition = TimeCondition.at(13, 30, 0);
      condition.setInterval(1);
      condition.setHourEnd(1);
      condition.setMinuteEnd(30);
      condition.setSecondEnd(0);

      if(assetFileBackupTask.getConditionCount() == 0) {
         assetFileBackupTask.addCondition(condition);
      }
      else {
         assetFileBackupTask.setCondition(0, condition);
      }

   }

   protected ScheduleTask handleInternalTaskConfiguration(ScheduleTask task, Principal principal)
      throws SecurityException
   {
      if(ScheduleManager.isInternalTask(task.getTaskId()) &&
         !InternalScheduledTaskService.BALANCE_TASKS.equals(task.getTaskId()) &&
         !InternalScheduledTaskService.UPDATE_ASSETS_DEPENDENCIES.equals(task.getTaskId()))
      {
         task = task.clone(); // Clone to preserve the original state of the internal task.
         task.setEditable(SecurityEngine.getSecurity().checkPermission(
            principal, ResourceType.SCHEDULE_TASK, task.getTaskId(), ResourceAction.WRITE));
      }

      return task;
   }

   public boolean isGroupShare(ScheduleTaskModel task, Principal principal) {
      RepletEngine engine = SUtil.getRepletEngine(analyticRepository);

      if(engine == null) {
         return false;
      }

      return engine.taskHasShareGroupPermission(task.owner(), principal);
   }

   /**
    * Gets the schedule tasks for the specified user.
    */
   public Vector<ScheduleTask> getScheduleTasks(String selectString, String filter, boolean loadInternal,
                                                Principal principal)
           throws Exception
   {
      return getScheduleTasks(selectString, filter, null, true, loadInternal, null, principal);
   }

   /**
    * Gets the schedule tasks by task entries.
    *
    * @param selectString  search string.
    * @param filter        filter.
    * @param taskEntries   task entries.
    * @param loadExtension whether load extension task.
    * @param loadInternal
    * @param parent
    * @param principal     user.
    * @return
    * @throws Exception
    */
   private Vector<ScheduleTask> getScheduleTasks(String selectString, String filter,
                                                 AssetEntry[] taskEntries, boolean loadExtension,
                                                 boolean loadInternal, AssetEntry parent, Principal principal)
      throws Exception
   {
      String curOrgID = OrganizationManager.getInstance().getCurrentOrgID(principal);
      Vector<ScheduleTask> matchTasks = taskEntries == null ? scheduleManager.getScheduleTasks(curOrgID) :
         scheduleManager.getScheduleTasks(taskEntries, loadExtension, loadInternal, curOrgID);
      List<ScheduleTask> matchTasks0 = new ArrayList<>();

      if(parent == null) {
         if(!taskFolderService.checkFolderPermission("/", principal, ResourceAction.READ)) {
            return new Vector<>();
         }

         List<String> pathList = matchTasks.stream()
            .map(task -> "/".equals(task.getPath()) ?
               task.getTaskId() : task.getPath() + "/" + task.getTaskId())
            .collect(Collectors.toList());
         List<String> showTaskList = new ArrayList<>();
         applyPermissionForTask(pathList, showTaskList, "/", principal);

         matchTasks0 = matchTasks.stream().filter(task -> {
            String taskPath = "/".equals(task.getPath()) ?
               task.getTaskId() : task.getPath() + "/" + task.getTaskId();

            return showTaskList.indexOf(taskPath) != -1;
         }).collect(Collectors.toList());
      }

      Vector<ScheduleTask> tasks = selectString == null || selectString.isEmpty() ?
         scheduleManager.getScheduleTasks(principal, parent != null ? matchTasks : matchTasks0, curOrgID) :
         getSelectScheduleTasks(parent, selectString, filter, principal);

      for(int i = 0; i < tasks.size(); i++) {
         tasks.set(i, handleInternalTaskConfiguration(tasks.get(i), principal));
      }

      return tasks;
   }

   private void applyPermissionForTask(List<String> pathList, List<String> showTaskList,
                                       String path, Principal principal)
      throws SecurityException
   {
      Map<String, List<String>> folderContents = new HashMap<>();
      ArrayList<String> rootTasks = new ArrayList<>();

      for(String taskPath : pathList) {
         int idx = taskPath.indexOf("/");

         if(idx == -1) {
            rootTasks.add(taskPath);
            continue;
         }

         String folderName = taskPath.substring(0, idx);

         if(folderContents.containsKey(folderName)) {
            folderContents.get(folderName).add(taskPath.substring(idx + 1));
         }
         else {
            List<String> list = new ArrayList<>();
            list.add(taskPath.substring(idx + 1));
            folderContents.put(folderName, list);
         }
      }

      for(String folderName : folderContents.keySet()) {
         String newPath = "/".equals(path) ? folderName : path + "/" + folderName;

         if(!taskFolderService.checkFolderPermission(newPath, principal, ResourceAction.READ)) {
            continue;
         }

         applyPermissionForTask(folderContents.get(folderName), showTaskList, newPath, principal);
      }

      rootTasks.stream()
         .filter(Objects::nonNull)
         .forEach(taskName -> showTaskList.add("/".equals(path) ? taskName : path + "/" + taskName));
   }

   public ScheduleTaskList getScheduleTaskList(String selectStr, String filter,
                                               Principal principal) throws Exception
   {
      return getScheduleTaskList(selectStr, filter, getRootEntry(), principal);
   }

   public ScheduleTaskList getScheduleTaskList(String selectStr, String filter, AssetEntry parentEntry,
                                               Principal principal)
      throws Exception
   {
      Catalog catalog = Catalog.getCatalog(principal);
      ScheduleTaskList.Builder builder = ScheduleTaskList.builder();
      builder.defaultTimeZone(principal);
      String dtfmt = formatsSettingsService.getModel(false).dateTimeFormat();

      if(dtfmt == null) {
         dtfmt = "yyyy-MM-dd HH:mm:ss";
      }

      builder.timeFormat(dtfmt);
      builder.showOwners(isSecurityEnabled());

      Map<String, TaskActivity> taskActivities = getTaskActivities(false);
      AssetEntry[] entries = null;

      if(parentEntry != null) {
         if(!taskFolderService.checkFolderPermission(parentEntry.getPath(),
            principal, ResourceAction.READ))
         {
            return builder.build();
         }

         AssetFolder parentFolder = (AssetFolder)
            indexedStorage.getXMLSerializable(parentEntry.toIdentifier(), null);

         if(parentFolder != null) {
            entries = parentFolder.getEntries();
         }
      }

      // just root folder to load extension task, because the extension tasks are not been store,
      // and do not support move.
      boolean isRootFolder = parentEntry != null && parentEntry.isRoot();
      Vector<ScheduleTask> tasks =
         getScheduleTasks(selectStr, filter, entries, isRootFolder, isRootFolder, parentEntry, principal);

      for(ScheduleTask task : tasks) {
         builder.addTasks(createTaskModel(
            task, taskActivities.get(task.getTaskId()), principal, catalog));
      }

      return builder.build();
   }

   public ScheduleTaskModel createTaskModel(ScheduleTask task, TaskActivity activity,
                                            Principal principal, Catalog catalog) throws Exception
   {
      return ScheduleTaskModel.builder()
         .fromTask(task, activity, catalog, isSecurityEnabled())
         .canDelete(canDeleteTask(task, principal)).build();
   }

   public boolean canDeleteTask(ScheduleTask task, Principal principal) {
      boolean canNotDelete = principal != null &&
         !Tool.equals(principal.getName(), task.getOwner().convertToKey()) &&
         scheduleManager.isDeleteOnlyByOwner(task, principal);
      boolean adminPermission = true;

      try {
         SecurityEngine securityEngine = SecurityEngine.getSecurity();
         adminPermission = securityEngine.checkPermission(
            principal, ResourceType.SECURITY_USER, task.getOwner(), ResourceAction.ADMIN);
      }
      catch(Exception e) {
         LOG.error("Failed to check permission for delete action", e);
      }

      return adminPermission || !canNotDelete;
   }

   /**
    * Get the select tasks depends on the access permission.
    */
   private Vector<ScheduleTask> getSelectScheduleTasks(AssetEntry parent, String selectStr,
                                                       String filter, Principal principal)
      throws Exception
   {
      Vector<ScheduleTask> vec = new Vector<>();
      String[] taskNames = analyticRepository.getScheduleTasks(principal);
      Catalog catalog = Catalog.getCatalog(principal);

      for(String taskName : taskNames) {
         if(taskCompare(catalog, selectStr, filter, taskName)) {
            ScheduleTask scheduleTask = analyticRepository.getScheduleTask(taskName);

            if(parent == null && !taskFolderService.checkFolderPermission(
               scheduleTask.getPath(), principal, ResourceAction.READ))
            {
               continue;
            }

            vec.addElement(analyticRepository.getScheduleTask(taskName));
         }
      }

      return vec;
   }

   public UsersModel getUsersModel(Principal principal) {
      return getUsersModel(principal, false);
   }

   public UsersModel getUsersModel(Principal principal, boolean em) {
      Map<IdentityID, User> allUsers = new HashMap<>();
      IdentityIDWithLabel[] allowedUsers;
      String currOrgId = OrganizationManager.getInstance().getCurrentOrgID();

      if(Organization.getSelfOrganizationID().equals(((XPrincipal) principal).getOrgId())) {
         allowedUsers = new IdentityIDWithLabel[] {
            new IdentityIDWithLabel(IdentityID.getIdentityIDFromKey(principal.getName()), ((XPrincipal) principal).getAlias()) };
      }
      else {
         Arrays.stream(securityProvider.getUsers())
            .filter(u -> Tool.equals(currOrgId, u.getOrgID()))
            .forEach(u -> allUsers.put(u, securityProvider.getUser(u)));

         allowedUsers = allUsers.values().stream()
            .map(u -> new IdentityIDWithLabel(u.getIdentityID(), u.getAlias()))
            .filter(u -> securityProvider.checkPermission(
               principal, ResourceType.SECURITY_USER, u.getIdentityID().convertToKey(), ResourceAction.ADMIN))
            .sorted()
            .toArray(IdentityIDWithLabel[]::new);
      }

      Map<IdentityID, Group> allGroups = new HashMap<>();

      Arrays.stream(securityProvider.getGroups())
         .filter(g -> Tool.equals(currOrgId, g.getOrgID()))
         .forEach(g -> allGroups.put(g, securityProvider.getGroup(g)));

      IdentityID[] allowedGroups = allGroups.values().stream()
         .map(Group::getIdentityID)
         .filter(g -> securityProvider.checkPermission(
            principal, ResourceType.SECURITY_GROUP, g.convertToKey(), ResourceAction.ADMIN))
         .sorted()
         .toArray(IdentityID[]::new);

      List<IdentityID> emailUsers = EmailDialogController.getUsers(principal);
      emailUsers.sort(Comparators.comparable());

      Set<Group> emailGroups = emailUsers.stream()
         .map(allUsers::get)
         .filter(Objects::nonNull)
         .flatMap(u -> Arrays.stream(u.getGroups())
            .map(g -> new IdentityID(g, u.getOrganizationID())))
         .map(allGroups::get)
         .filter(Objects::nonNull)
         .collect(Collectors.toSet());

      Set<IdentityID> parentGroups = new HashSet<>();
      Deque<Group> groupStack = new ArrayDeque<>(emailGroups);

      while(!groupStack.isEmpty()) {
         Group group = groupStack.removeLast();

         if(parentGroups.add(new IdentityID(group.getName(), group.getOrganizationID()))) {
            for(String parentGroupName : group.getGroups()) {
               Group parentGroup =
                  allGroups.get(new IdentityID(parentGroupName, group.getOrganizationID()));

               if(parentGroup != null) {
                  groupStack.addLast(parentGroup);
               }
            }
         }
      }

      List<IdentityID> sortedEmailGroups = parentGroups.stream()
         .sorted()
         .toList();

      UsersModel.Builder model = UsersModel.builder()
         .owners(allowedUsers)
         .groups(allowedGroups)
//         .groupBaseNames(allowedGroupBaseNames)
         .emailUsers(emailUsers)
         .emailGroups(sortedEmailGroups);
//         .emailGroupBaseNames(emailGroupBaseNames);

      if(allowedUsers.length > 0) {
         model.adminName(principal.getName());
      }

      return model.build();
   }

   public TaskActivity getActivity(String taskId) {
      return getActivity(taskId, true);
   }

   public TaskActivity getActivity(String taskId, boolean cachePriority) {
      return getTaskActivities(cachePriority).get(taskId);
   }

   private Map<String, TaskActivity> getTaskActivities() {
      return getTaskActivities(true);
   }

   private synchronized Map<String, TaskActivity> getTaskActivities(boolean cachePriority) {
      long start = System.currentTimeMillis();

      // optimization. this method is called a lot (every task). cache it for 2s.
      if(cachePriority && activities != null && start < activitiesTS + activityTimeout) {
         return activities;
      }

      activities = scheduleManager.getScheduleActivities();

      long end = System.currentTimeMillis();
      activitiesTS = end;
      // if it takes a long time to get the list, should increase the timeout otherwise
      // it's meaningless. (42175)
      activityTimeout = Math.max(activityTimeout, (end - start) * 2);
      return activities;
   }

   /**
    * Through the comparison to obtain corresponding tasks.
    */
   private boolean taskCompare(Catalog catalog, String selectStr, String filter,
                               String taskName)
      throws Exception
   {
      boolean running = scheduleClient.isReady();
      TaskActivity activity = getActivity(taskName);
      ScheduleTask task = analyticRepository.getScheduleTask(taskName);
      String lastRunStatus = catalog.getString(
         activity == null || activity.getLastRunStatus() == null ?
            "" : activity.getLastRunStatus()).toLowerCase();
      String nextRunStatus = catalog.getString(
         activity == null || activity.getNextRunStatus() == null ?
            "" : activity.getNextRunStatus()).toLowerCase();
      String enable =
         task == null ? "disable" : (task.isEnabled() ? "true" : "false");

      if("nameFilter".equalsIgnoreCase(filter) &&
         (taskName.toLowerCase()).contains(selectStr))
      {
         return true;
      }
      else if("enabledFilter".equalsIgnoreCase(filter) &&
         enable.contains(selectStr))
      {
         return true;
      }
      else if("lastRunStatusFilter".equalsIgnoreCase(filter) &&
         lastRunStatus.contains(selectStr))
      {
         return true;
      }
      else {
         return "nextRunStatusFilter".equalsIgnoreCase(filter) &&
            nextRunStatus.contains(selectStr) &&
            ("".equals(selectStr) || !"false".equals(enable) && running);
      }

   }

   /**
    * Saves the task with the new information.
    */
   public void saveTask(String taskId, ScheduleTask task, Principal principal)
      throws Exception
   {
      saveTask(taskId, task, principal, true);
   }

   /**
    * Saves the task with the new information.
    * @param  addRecord if need to add action record, if only load and save the task, don't
    * need to insert action record.
    */
   public void saveTask(String taskId, ScheduleTask task, Principal principal, boolean addRecord)
      throws Exception
   {
      // log edit task action
      String actionName = ActionRecord.ACTION_NAME_EDIT;
      String objectType = ActionRecord.OBJECT_TYPE_TASK;
      Timestamp actionTimestamp = new Timestamp(System.currentTimeMillis());
      ActionRecord actionRecord = new ActionRecord(SUtil.getUserName(principal), actionName, taskId,
                                      objectType, actionTimestamp,
                                      ActionRecord.ACTION_STATUS_FAILURE, null);

      try {
         String oldPath = task.getPath();
         AssetEntry folderEntry = null;

         if(!StringUtils.isEmpty(oldPath)) {
            folderEntry = new AssetEntry( AssetRepository.GLOBAL_SCOPE,
               AssetEntry.Type.SCHEDULE_TASK_FOLDER, oldPath, null);
         }

         scheduleManager.setScheduleTask(taskId, task, folderEntry, principal);
         actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_SUCCESS);
      }
      catch(Exception ex) {
         actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_FAILURE);
         actionRecord.setActionError(ex.getMessage());
         throw ex;
      }
      finally {
         Audit.getInstance().auditAction(actionRecord, principal);
      }
   }

   /**
    * Get a list of condition names for specific task.
    */
   public String[] getTaskConditionList(ScheduleTask task) {
      String[] conditions = new String[task.getConditionCount()];
      ScheduleCondition condition;

      for(int i = 0; i < task.getConditionCount(); i++) {
         condition = task.getCondition(i);
         conditions[i] = condition.toString();
      }

      return conditions;
   }

   /**
    * Gets the action model from the schedule action.
    */
   public ScheduleActionModel getActionModel(ScheduleAction action, Principal principal, boolean em) {
      Catalog catalog = Catalog.getCatalog(principal);
      ScheduleActionModel model = null;
      GeneralActionModel.Builder actionModel = GeneralActionModel.builder();

      if(action instanceof AbstractAction) {
         AbstractAction abstractAction = (AbstractAction) action;
         boolean deliverEnable = abstractAction.getEmails() != null &&
            !abstractAction.getEmails().isEmpty();
         String fromEmail = deliverEnable ? abstractAction.getFrom() : null;

         if(!abstractAction.isMessageHtml()) {
            abstractAction.setMessageHtml(true);

            if(abstractAction.getMessage() != null) {
               String mess = abstractAction.getMessage();
               mess = mess.replaceAll( "/\r?\n/", "<br/>");
               abstractAction.setMessage(mess);
            }
         }

         actionModel
            .label(getTaskActionLabel(action, catalog, principal))
            .notificationEnabled(abstractAction.getNotifications() != null
                                    && !abstractAction.getNotifications().isEmpty())
            .notifications(abstractAction.getNotifications())
            .notifyIfFailed(abstractAction.isNotifyError())
            .link(abstractAction.isLink())
            .deliverLink(abstractAction.isDeliverLink())
            .deliverEmailsEnabled(deliverEnable)
            .fromEmail(fromEmail)
            .to(abstractAction.getEmails())
            .ccAddress(abstractAction.getCCAddresses())
            .bccAddress(abstractAction.getBCCAddresses())
            .subject(abstractAction.getSubject())
            .format(abstractAction.getFileFormat())
            .bundledAsZip(abstractAction.isCompressFile())
            .useCredential(abstractAction.isUseCredential())
            .secretId(abstractAction.isUseCredential() ? abstractAction.getSecretId() : null)
            .password(!abstractAction.isUseCredential() ? abstractAction.getPassword() : null)
            .attachmentName(abstractAction.getAttachmentName())
            .htmlMessage(abstractAction.isMessageHtml())
            .message(abstractAction.getMessage())
            .actionClass("GeneralActionModel");
      }

      if(action instanceof ViewsheetAction) {
         ViewsheetAction viewsheetAction = (ViewsheetAction) action;

         List<String> highlightAssemblies = new ArrayList<>();
         List<String> highlightNames = new ArrayList<>();

         if(viewsheetAction.getAlerts() != null) {
            Arrays.stream(viewsheetAction.getAlerts())
               .forEach(scheduleAlert -> {
                  highlightAssemblies.add(scheduleAlert.getElementId());
                  highlightNames.add(scheduleAlert.getHighlightName());
               });
         }

         List<String> filePaths = Arrays.stream(viewsheetAction.getSaveFormats())
            .mapToObj(viewsheetAction::getFilePath)
            .collect(Collectors.toList());

         List<ServerPathInfoModel> filePathInfos = Arrays.stream(viewsheetAction.getSaveFormats())
            .mapToObj(viewsheetAction::getFilePathInfo)
            .filter(Objects::nonNull)
            .map(info -> ServerPathInfoModel.builder().from(info).build())
            .collect(Collectors.toList());

         actionModel
            .sheet(viewsheetAction.getViewsheet())
            .bookmarks(getBookmarkModels(viewsheetAction, em, principal))
            .saveFormats(Arrays.stream(viewsheetAction.getSaveFormats())
                            .mapToObj(String::valueOf)
                            .toArray(String[]::new))
            .saveToServerEnabled(viewsheetAction.getSaveFormats() != null
                                    && viewsheetAction.getSaveFormats().length > 0)
            .filePaths(filePaths)
            .serverFilePaths(filePathInfos)
            .parameters(scheduleConditionService
                           .getParameterModelList(viewsheetAction.getViewsheetRequest()))
            .emailMatchLayout(viewsheetAction.isMatchLayout())
            .emailExpandSelections(viewsheetAction.isExpandSelections())
            .emailOnlyDataComponents(viewsheetAction.isOnlyDataComponents())
            .exportAllTabbedTables(viewsheetAction.isExportAllTabbedTables())
            .saveMatchLayout(viewsheetAction.isSaveToServerMatch())
            .saveExpandSelections(viewsheetAction.isSaveToServerExpandSelections())
            .saveOnlyDataComponents(viewsheetAction.isSaveToServerOnlyDataComponents())
            .saveExportAllTabbedTables(viewsheetAction.isSaveExportAllTabbedTables())
            .actionType("ViewsheetAction")
            .highlightsSelected(highlightAssemblies.size() > 0)
            .highlightAssemblies(highlightAssemblies)
            .highlightNames(highlightNames)
            .csvExportModel(CSVConfigModel.builder().from(viewsheetAction.getEmailCSVConfig()).build())
            .csvSaveModel(CSVConfigModel.builder().from(viewsheetAction.getSaveCSVConfig()).build());

         model = actionModel.build();
      }
      else if(action instanceof IndividualAssetBackupAction) {
         IndividualAssetBackupAction backupAction = (IndividualAssetBackupAction) action;
         String path = backupAction.getPath();
         ServerPathInfo pathInfo = backupAction.getServerPath();

         if(pathInfo == null) {
            pathInfo = new ServerPathInfo(path);
         }
         else if(path == null) {
            path = pathInfo.getPath();
         }

         ServerPathInfoModel pathInfoModel =
            ServerPathInfoModel.builder().from(pathInfo).build();

         List<SelectedAssetModel> assetModels = backupAction.getAssets()
            .stream()
            .filter(XAsset::exists)
            .map((xAsset) -> {

               if(xAsset instanceof XDataSourceAsset) {
                  String ds = ((XDataSourceAsset) xAsset).getDatasource();
                  XDataSource dataSource = DataSourceRegistry.getRegistry().getDataSource(ds);

                  if(dataSource != null) {
                     return SelectedAssetModel.builder()
                        .label(getAssetLabel(xAsset))
                        .path(xAsset.getPath())
                        .type(DeployUtil.toRepositoryEntryType(xAsset.getType()))
                        .typeName(xAsset.getType())
                        .typeLabel(getAssetTypeLabel(xAsset.getType(), catalog))
                        .user(xAsset.getUser())
                        .icon(ContentRepositoryTreeService.getDataSourceIconClass(dataSource.getType()))
                        .build();
                  }
               }

                return SelectedAssetModel.builder()
                  .label(getAssetLabel(xAsset))
                  .path(xAsset.getPath())
                  .type(DeployUtil.toRepositoryEntryType(xAsset.getType()))
                  .typeName(xAsset.getType())
                  .typeLabel(getAssetTypeLabel(xAsset.getType(), catalog))
                  .user(xAsset.getUser())
                  .build();
            })
            .collect(Collectors.toList());

         model = BackupActionModel.builder()
            .label(getTaskActionLabel(action, catalog, principal))
            .backupPathsEnabled(path != null && !path.isEmpty())
            .assets(assetModels)
            .backupPath(Objects.requireNonNull(path))
            .backupServerPath(pathInfoModel)
            .actionType("BackupAction")
            .actionClass("BackupActionModel")
            .build();
      }
      else if(action instanceof BatchAction) {
         BatchAction batchAction = (BatchAction) action;
         List<AddParameterDialogModel> queryParameterModels = new ArrayList<>();
         Map<String, Object> queryParameters = batchAction.getQueryParameters();

         for(String param : queryParameters.keySet()) {
            queryParameterModels.add(
               AddParameterDialogModel.builder()
                  .name(param)
                  .value(new DynamicValueModel(queryParameters.get(param), DynamicValueModel.VALUE))
                  .build());
         }

         List<Map<String, Object>> embeddedParameters = batchAction.getEmbeddedParameters();
         AddParameterDialogModel[][] embeddedParameterModels = new AddParameterDialogModel[embeddedParameters.size()][];

         for(int i = 0; i < embeddedParameters.size(); i++) {
            Map<String, Object> map = embeddedParameters.get(i);
            RepletRequest repletRequest = new RepletRequest();

            for(String paramName : map.keySet()) {
               repletRequest.setParameter(paramName, map.get(paramName));
            }

            embeddedParameterModels[i] =
               scheduleConditionService.getParameterModelList(repletRequest)
                  .toArray(new AddParameterDialogModel[0]);
         }

         model = BatchActionModel.builder()
            .label(getTaskActionLabel(action, catalog, principal))
            .taskName(batchAction.getTaskId())
            .queryEntry(batchAction.getQueryEntry())
            .queryParameters(queryParameterModels.toArray(new AddParameterDialogModel[0]))
            .embeddedParameters(embeddedParameterModels)
            .queryEnabled(batchAction.getQueryEntry() != null)
            .embeddedEnabled(embeddedParameters.size() > 0)
            .actionType("BatchAction")
            .actionClass("BatchActionModel")
            .build();
      }

      return model;
   }

   private String getAssetLabel(XAsset xAsset) {
      if(xAsset instanceof TableStyleAsset) {
         return ((TableStyleAsset) xAsset).getLabel();
      }
      else if(xAsset instanceof ScheduleTaskAsset) {
         String path = xAsset.getPath();
         int idx = path.indexOf("/");
         String ppath = idx != -1 ? path.substring(0, idx) : null;
         String taskName = idx != -1 ? path.substring(idx + 1) : path;
         String withoutOrg = SUtil.getTaskNameWithoutOrg(taskName);

         return ppath == null ? withoutOrg : ppath + "/" + withoutOrg;
      }
      else if(xAsset instanceof ViewsheetAsset) {
         ViewsheetAsset sheetAsset = (ViewsheetAsset) xAsset;
         String path = sheetAsset.getPath();

         if(sheetAsset.getUser() != null) {
            path = Tool.MY_DASHBOARD + "/" + path;
         }

         return path;
      }
      else if(xAsset instanceof WorksheetAsset) {
         WorksheetAsset worksheetAsset = (WorksheetAsset) xAsset;
         String path = worksheetAsset.getPath();

         if(worksheetAsset.getUser() != null) {
            path = Tool.MY_DASHBOARD + "/" + Tool.WORKSHEET + "/" + path;
         }

         return path;
      }
      else if(xAsset instanceof DashboardAsset) {
         String path = xAsset.getPath();

         if(xAsset.getUser() != null) {
            path = SUtil.MY_DASHBOARD + "/" + path;
         }

         return path;
      }
      else {
         return xAsset.getPath();
      }
   }

   public String getRepletAlias(String sheet, Principal principal) {
      String localizeSheet = SUtil.localize(sheet, principal);

      if(!Tool.equals(localizeSheet, sheet)) {
         return localizeSheet;
      }

      return null;
   }

   private String getAssetTypeLabel(String type, Catalog catalog) {
      if(StringUtils.hasText(type)) {
         return catalog.getString("asset.type." + type);
      }

      return type;
   }

   /**
    * Gets the ScheduleAction from the model.
    */
   public ScheduleAction getActionFromModel(ScheduleActionModel model,
                                            Principal principal,
                                            String linkURI)
      throws Exception
   {
      ScheduleAction action = null;

      if(model instanceof GeneralActionModel actionModel) {
         if(Tool.isEmptyString(actionModel.sheet())) {
            return null;
         }

         AbstractAction abstractAction = null;

         if("ViewsheetAction".equals(actionModel.actionType())) {
            ViewsheetAction viewsheetAction = new ViewsheetAction();
            viewsheetAction.setViewsheet(actionModel.sheet());
            List<VSBookmarkInfoModel> bookmarks = Optional.ofNullable(actionModel.bookmarks())
               .orElse(new ArrayList<>());

            if(bookmarks.size() > 0) {
               String[] names = bookmarks.stream()
                  .map(VSBookmarkInfoModel::name)
                  .toArray(String[]::new);
               int[] types = bookmarks.stream()
                  .mapToInt(VSBookmarkInfoModel::type)
                  .toArray();
               IdentityID[] users = bookmarks.stream()
                  .map(VSBookmarkInfoModel::owner)
                  .toArray(IdentityID[]::new);
               viewsheetAction.setBookmarks(names);
               viewsheetAction.setBookmarkTypes(types);
               viewsheetAction.setBookmarkUsers(users);
            }

            final RepletRequest repRequest = viewsheetAction.getViewsheetRequest();

            Arrays.stream(viewsheetAction.getSaveFormats())
               .forEach(format -> viewsheetAction.setFilePath(format, (String) null));

            Arrays.stream(viewsheetAction.getSaveFormats())
               .forEach(format -> viewsheetAction.setFilePath(format, (ServerPathInfo) null));

            if(Tool.defaultIfNull(actionModel.saveToServerEnabled(), false)) {
               String[] saveFormats = actionModel.saveFormats();
               List<ServerPathInfoModel> serverFilePaths = actionModel.serverFilePaths();
               List<String> filePaths = actionModel.filePaths();
               ServerPathInfo info;

               for(int i = 0; i < saveFormats.length; i++) {
                  int format = Integer.parseInt(saveFormats[i]);
                  ServerPathInfoModel pModel = serverFilePaths.get(i);

                  if(pModel.ftp()) {
                     info = new ServerPathInfo(pModel);
                  }
                  else {
                     info = new ServerPathInfo(filePaths.get(i), null, null);
                  }

                  viewsheetAction.setFilePath(format, info);
               }
            }

            actionModel.parameters()
               .forEach((AddParameterDialogModel paramModel) ->
                           scheduleConditionService.setParameter(paramModel, repRequest));

            viewsheetAction.setSaveCSVConfig(new CSVConfig(actionModel.csvSaveModel()));
            viewsheetAction.setMatchLayout(
               Tool.defaultIfNull(actionModel.emailMatchLayout(), true));
            viewsheetAction.setExpandSelections(
               Tool.defaultIfNull(actionModel.emailExpandSelections(), false));
            viewsheetAction.setExportAllTabbedTables(actionModel.exportAllTabbedTables());
            viewsheetAction.setSaveToServerMatch(
               Tool.defaultIfNull(actionModel.saveMatchLayout(), true));
            viewsheetAction.setSaveToServerExpandSelections(
               Tool.defaultIfNull(actionModel.saveExpandSelections(), false));
            viewsheetAction.setSaveToServerOnlyDataComponents(
               Tool.defaultIfNull(actionModel.saveOnlyDataComponents(), false));
            viewsheetAction.setSaveExportAllTabbedTables(
               Tool.defaultIfNull(actionModel.saveExportAllTabbedTables(), false));
            viewsheetAction.setOnlyDataComponents(
               Tool.defaultIfNull(actionModel.emailOnlyDataComponents(), false));

            if(Tool.defaultIfNull(actionModel.highlightsSelected(), false) && actionModel.highlightAssemblies() != null)
            {
               List<String> assemblies = actionModel.highlightAssemblies();
               List<String> names = actionModel.highlightNames();
               ScheduleAlert[] alerts = new ScheduleAlert[assemblies.size()];

               for(int i = 0; i < assemblies.size(); i++) {
                  ScheduleAlert alert = new ScheduleAlert();
                  alert.setElementId(assemblies.get(i));
                  assert names != null;
                  alert.setHighlightName(names.get(i));

                  alerts[i] = alert;
               }

               viewsheetAction.setAlerts(alerts);
            }
            else {
               viewsheetAction.setAlerts(null);
            }

            abstractAction = viewsheetAction;
         }

         if(Tool.defaultIfNull(actionModel.notificationEnabled(), false)) {
            abstractAction.setNotifications(actionModel.notifications());
            abstractAction.setNotifyError(actionModel.notifyIfFailed() != null && actionModel.notifyIfFailed());
            abstractAction.setLink(actionModel.link() != null && actionModel.link());
            abstractAction.setLinkURI(Tool.replaceLocalhost(linkURI));
         }
         else {
            abstractAction.setNotifications(null);
            abstractAction.setNotifyError(false);
            abstractAction.setLink(false);
            abstractAction.setLinkURI(null);
         }

         if(Tool.defaultIfNull(actionModel.deliverEmailsEnabled(), false)) {
            boolean showFrom = "true".equals(SreeEnv.getProperty("mail.from.enabled", "false"));

            if(showFrom || StringUtils.hasText(actionModel.fromEmail())) {
               abstractAction.setFrom(actionModel.fromEmail());
            }
            else {
               String[] emails = new String[0];

               try {
                  emails = SUtil.getEmails(IdentityID.getIdentityIDFromKey(principal.getName()));
               } catch(Exception e) {
                  // Do nothing
               }

               boolean useSelf =
                  !"false".equals(SreeEnv.getProperty("em.mail.defaultEmailFromSelf"));
               String userEmail = useSelf && emails.length > 0 ? emails[0] : null;
               String defaultEmail = userEmail != null && !userEmail.isEmpty() ?
                  userEmail : SreeEnv.getProperty("mail.from.address");
               abstractAction.setFrom(defaultEmail);
            }

            abstractAction.setCCAddresses(trimEmailSpaces(actionModel.ccAddress()));
            abstractAction.setBCCAddresses(trimEmailSpaces(actionModel.bccAddress()));

            String attachmentName = actionModel.attachmentName();

            if(attachmentName == null || "".equals(attachmentName)) {
               attachmentName = actionModel.sheet();

               if(attachmentName != null) {
                  int idx = attachmentName.lastIndexOf("^");
                  attachmentName = attachmentName.substring(0, idx);
                  attachmentName = attachmentName.substring(attachmentName.lastIndexOf("^") + 1);
                  attachmentName = attachmentName.replace("/", "_");
                  attachmentName = attachmentName.replace(" ", "_");
               }
            }

            abstractAction.setEmails(trimEmailSpaces(actionModel.to()));
            abstractAction.setSubject(actionModel.subject());
            abstractAction.setFileFormat(actionModel.format());
            abstractAction.setCompressFile(actionModel.bundledAsZip() != null &&
                                              actionModel.bundledAsZip());
            abstractAction.setAttachmentName(attachmentName);
            abstractAction.setMessage(actionModel.message());
            abstractAction.setMessageHtml(actionModel.htmlMessage() != null &&
                                             actionModel.htmlMessage());
            abstractAction.setDeliverLink(actionModel.deliverLink() != null &&
               actionModel.deliverLink());
            abstractAction.setLinkURI(Tool.replaceLocalhost(linkURI));

            if(Boolean.TRUE.equals(actionModel.useCredential())) {
               abstractAction.setUseCredential(true);
               abstractAction.setSecretId(actionModel.secretId());
            }
            else {
               abstractAction.setUseCredential(false);
               abstractAction.setPassword(actionModel.password());
            }

            if(abstractAction instanceof ViewsheetAction && "CSV".equals(actionModel.format())) {
               ((ViewsheetAction) abstractAction)
                  .setEmailCSVConfig(new CSVConfig(actionModel.csvExportModel()));
            }
         }
         else {
            abstractAction.setFrom(null);
            abstractAction.setEmails(null);
            abstractAction.setSubject(null);
            abstractAction.setFileFormat(null);
            abstractAction.setCompressFile(false);
            abstractAction.setUseCredential(false);
            abstractAction.setSecretId(null);
            abstractAction.setPassword(null);
            abstractAction.setAttachmentName(null);
            abstractAction.setMessage(null);
            abstractAction.setDeliverLink(false);
         }

         action = abstractAction;
      }
      else if("BackupAction".equals(model.actionType())) {
         BackupActionModel backupActionModel = (BackupActionModel) model;
         IndividualAssetBackupAction backupAction = new IndividualAssetBackupAction();
         backupAction.setAssets(deployService.getEntryAssets(backupActionModel.assets(), principal));
         backupAction.setPaths(Tool.defaultIfNull(backupActionModel.backupPathsEnabled(), false) ? backupActionModel
            .backupPath() : null);
         backupAction.setServerPaths(Tool.defaultIfNull(backupActionModel.backupPathsEnabled(), false) ?
            new ServerPathInfo(backupActionModel.backupServerPath()) : null);
         action = backupAction;
      }
      else if("BatchAction".equals(model.actionType())) {
         BatchActionModel batchActionModel = (BatchActionModel) model;
         BatchAction batchAction = new BatchAction();
         batchAction.setTaskId(batchActionModel.taskName());

         if(batchActionModel.queryEnabled()) {
            batchAction.setQueryEntry(batchActionModel.queryEntry());

            AddParameterDialogModel[] queryParameterModels = batchActionModel.queryParameters();
            Map<String, Object> queryParameters = new LinkedHashMap<>();

            if(queryParameterModels != null) {
               for(AddParameterDialogModel parameter : queryParameterModels) {
                  queryParameters.put(parameter.name(), parameter.value().getValue());
               }
            }

            batchAction.setQueryParameters(queryParameters);
         }
         else {
            batchAction.setQueryEntry(null);
            batchAction.setQueryParameters(new LinkedHashMap<>());
         }

         if(batchActionModel.embeddedEnabled()) {
            AddParameterDialogModel[][] embeddedParameterModels = batchActionModel.embeddedParameters();
            List<Map<String, Object>> embeddedParameters = new ArrayList<>();

            if(embeddedParameterModels != null) {
               for(AddParameterDialogModel[] parameterModels : embeddedParameterModels) {
                  if(parameterModels != null) {
                     Map<String, Object> parametersMap = new LinkedHashMap<>();

                     for(AddParameterDialogModel parameter : parameterModels) {
                        Object value = parameter.value();

                        if(parameter.array()) {
                           value = scheduleConditionService
                              .getParamValueAsArray(parameter.type(), parameter.value().getValue().toString());
                        }
                        else if(value instanceof DynamicValueModel) {
                           value = ((DynamicValueModel) value).convertParameterValue();

                           if(DynamicValueModel.VALUE.equals(parameter.value().getType())) {
                              value = scheduleConditionService
                                      .getParamValueAsType(parameter.type(), parameter.value());
                           }
                        }

                        parametersMap.put(parameter.name(), value);
                     }

                     embeddedParameters.add(parametersMap);
                  }
               }
            }

            batchAction.setEmbeddedParameters(embeddedParameters);
         }
         else {
            batchAction.setEmbeddedParameters(new ArrayList<>());
         }

         action = batchAction;
      }

      return action;
   }

   private String trimEmailSpaces(String emails) {
      return emails == null ? emails : emails.replaceAll(",\\s+", ",");
   }

   /**
    * Get the bookmark models from the action.
    */
   private List<VSBookmarkInfoModel> getBookmarkModels(ViewsheetAction action, boolean em,
                                                       Principal principal)
   {
      ArrayList<VSBookmarkInfoModel> bookmarks = new ArrayList<>();
      String[] names = action.getBookmarks();
      int[] types = action.getBookmarkTypes();
      IdentityID[] owners = action.getBookmarkUsers();

      if(types != null) {
         for(int i = 0; i < names.length; i++) {
            IdentityID owner = getBookmarkOwner(owners[i], names[i], principal);
            bookmarks.add(VSBookmarkInfoModel.builder()
                  .name(names[i])
                  .owner(owner)
                  .type(types[i])
                  .label(getBookmarkLabel(names[i], owner, em))
                  .build());
         }
      }

      return bookmarks;
   }

   private IdentityID getBookmarkOwner(IdentityID originalOwner, String name, Principal principal) {
      if(!VSBookmark.HOME_BOOKMARK.equals(name)) {
         return originalOwner;
      }

      return IdentityID.getIdentityIDFromKey(principal.getName());
   }

   private String getBookmarkLabel(String bkName, IdentityID owner, boolean em) {
      return bkName + "(" + (em ? owner.getName() : VSUtil.getUserAlias(owner)) + ")";
   }

   /**
    * Get the bookmark model from the bookmark info.
    */
   public VSBookmarkInfoModel getBookmarkModel(VSBookmarkInfo info, boolean em) {
      return VSBookmarkInfoModel.builder()
         .name(info.getName())
         .owner(info.getOwner())
         .type(info.getType())
         .label(info.getName() + "(" + (em ? info.getOwner().name : VSUtil.getUserAlias(info.getOwner())) + ")")
         .build();
   }

   /**
    * Get a list of action names for specific task.
    */
   public String[] getTaskActionList(ScheduleTask task, Catalog catalog,
                                     Principal principal)
   {
      ScheduleAction action;
      String[] actions = new String[task.getActionCount()];

      for(int i = 0; i < task.getActionCount(); i++) {
         action = task.getAction(i);
         actions[i] = getTaskActionLabel(action, catalog, principal);
      }

      return actions;
   }

   public String getTaskActionLabel(ScheduleAction action, Catalog catalog, Principal principal) {
      String label = action.toString();

      if(action instanceof ViewsheetAction || action instanceof BatchAction) {
         int idx = label.indexOf(":") + 2; // remove the prefix
         label = catalog.getString(label.substring(0, idx)) +
            SUtil.localize(label.substring(idx), principal);
      }
      else if(action instanceof IndividualAssetBackupAction) {
         label = catalog.getString(label);
      }

      return label;
   }

   /**
    * Check if security is enabled.
    */
   public boolean isSecurityEnabled() {
      try {
         return !securityProvider.isVirtual();
      }
      catch(Exception ex) {
         LOG.error("Failed to get security provider", ex);
         return false;
      }
   }

   /**
    * Check permission of report toolbar.
    */
   public boolean checkPermission(Principal principal, ResourceType type, String resource) {
      boolean permission = false;

      try {
         SecurityEngine security = SecurityEngine.getSecurity();
         permission = security.checkPermission(principal, type, resource, ResourceAction.READ);
      }
      catch(Exception e) {
         LOG.error("Failed to check permission on {} for user {}", resource, principal, e);
      }

      return permission;
   }

   /**
    * Get all the viewsheets under the entry on the user.
    */
   public void getViewsheets(AssetRepository engine, AssetEntry entry,
                             Principal user, List<AssetEntry> list) throws Exception
   {
      if(entry == null) {
         return;
      }

      // @by stephenwebster, For Bug #1615
      // Implement a different engine method to retrieve list of assets faster
      // taking advantage of the new DirectoryIndexedStorage.
      list.addAll(Arrays.asList(
         engine.getAllEntries(entry, user, ResourceAction.READ, new AssetEntry.Selector(
            AssetEntry.Type.VIEWSHEET))));
   }

   /**
    * Get viewsheets, use to display in viewsheet action.
    */
   public AssetEntry[] getViewsheets(Principal principal) throws Exception {
      List<AssetEntry> result = new ArrayList<>();
      AssetRepository assetRepository = AssetUtil.getAssetRepository(false);
      AssetEntry[] roots = {
         (principal != null) ? new AssetEntry(AssetRepository.USER_SCOPE,
                                              AssetEntry.Type.REPOSITORY_FOLDER, "/",
                                              IdentityID.getIdentityIDFromKey(principal.getName())) : null,
         new AssetEntry(AssetRepository.GLOBAL_SCOPE,
                        AssetEntry.Type.REPOSITORY_FOLDER, "/", null)
      };

      for(AssetEntry root : roots) {
         getViewsheets(assetRepository, root, principal, result);
      }

      Collections.sort(result);

      return result.toArray(new AssetEntry[0]);
   }

   public void removeScheduledTask(String selectStr, String filter, String taskName,
                                   Principal principal) throws Exception
   {
      taskName = Tool.byteDecode(taskName);
      Vector<ScheduleTask> allTasks = getScheduleTasks(selectStr, filter, false, principal);
      deleteTask(taskName, principal, allTasks);
   }

   /*
    * Delete a task.
    */
   private synchronized void deleteTask(String taskName, Principal principal,
                                        Vector<ScheduleTask> allTasks)
      throws Exception
   {
      Catalog catalog = Catalog.getCatalog(principal);
      boolean dependence = scheduleManager.hasDependency(allTasks, taskName);

      // log delete task action
      String actionName = ActionRecord.ACTION_NAME_DELETE;
      String objectType = ActionRecord.OBJECT_TYPE_TASK;
      Timestamp actionTimestamp = new Timestamp(System.currentTimeMillis());
      ActionRecord actionRecord = new ActionRecord(SUtil.getUserName(principal), actionName, taskName,
                                      objectType, actionTimestamp, ActionRecord.ACTION_STATUS_FAILURE,
                                      null);

      try {
         if(!dependence) {
            scheduleManager.removeScheduleTask(taskName, principal);
            actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_SUCCESS);
         }
         else {
            actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_FAILURE);
            actionRecord.setActionError(catalog.getString(
               "em.schedule.task.removeDependency", SUtil.getTaskNameWithoutOrg(taskName)));
            throw new MessageException(actionRecord.getActionError());
         }
      }
      catch(Exception ex) {
         actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_FAILURE);
         actionRecord.setActionError(ex.getMessage());
         throw ex;
      }
      finally {
         Audit.getInstance().auditAction(actionRecord, principal);
      }
   }

   public void runScheduledTask(String taskName, Principal principal)
      throws Exception
   {
      String currentOrgID = OrganizationManager.getInstance().getCurrentOrgID(principal);
      taskName = Tool.byteDecode(taskName);
      Catalog catalog = Catalog.getCatalog(principal);
      String errorMsg = null;
      boolean dumpException = true;
      ScheduleTask task = scheduleManager.getScheduleTask(taskName, currentOrgID);

      if(!scheduleClient.isReady()) {
         errorMsg = catalog.getString("em.scheduler.notStarted");
         dumpException = false;
      }
      else {
         String taskNameForLog = LicenseManager.getInstance().isEnterprise() ?
            taskName : SUtil.getTaskNameWithoutOrg(taskName);
         MDC.put("SCHEDULE_TASK", taskNameForLog);
         String withoutOrg = SUtil.getTaskNameWithoutOrg(taskName);

         if(task != null && !task.isEnabled()) {
            errorMsg = catalog.getString("em.scheduler.startDisabledTask", withoutOrg);
         }
         else {
            try {
               scheduleClient.runNow(taskName);
            }
            catch(Throwable ex) {
               LOG.debug("Failed to run task {}: {}", taskNameForLog, ex.getMessage(), ex);
               errorMsg =
                  catalog.getString("em.scheduler.startFailed", withoutOrg);
            }
         }

         MDC.remove("SCHEDULE_TASK");
      }

      // log run task action
      String actionName = ActionRecord.ACTION_NAME_RUN;
      String objectType = ActionRecord.OBJECT_TYPE_TASK;
      ActionRecord actionRecord = SUtil.getActionRecord(principal, actionName, taskName, objectType);
      actionRecord.setObjectUser(task == null ? null : task.getOwner().name);

      if(errorMsg != null) {
         actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_FAILURE);
         actionRecord.setActionError(errorMsg);
         Audit.getInstance().auditAction(actionRecord, principal);

         throw new MessageException(errorMsg, LogLevel.INFO, dumpException);
      }
      else {
         try {
            Thread.sleep(3000);
         }
         catch(Exception ignore) {
         }

         actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_SUCCESS);
         actionRecord.setActionError(catalog.getString("em.task.runStatus"));
         Audit.getInstance().auditAction(actionRecord, principal);
      }

      // Move this code to JobCompletionListener, since the success/failure of a task execution also
      // depends on the run time exception/error when running the task.
      //Add finish audit for action.
//      actionName = ActionRecord.ACTION_NAME_FINISH;
//      TaskActivity activity = getActivity(taskName);
//      actionTimestamp =
//         new java.sql.Date(new java.util.Date(activity.getLastRunEnd()).getTime());
//      finishActionRecord = new ActionRecord(sessionId, actionName, taskName, objectType,
//                                            actionTimestamp, "", null);
//
//      finishActionRecord.setActionStatus(
//         isSuccess ? ActionRecord.ACTION_STATUS_SUCCESS : ActionRecord.ACTION_STATUS_FAILURE);
//      finishActionRecord.setActionError(
//         isSuccess ? catalog.getString("em.task.runStatus") : errorMsg);
//
//      if(auditDAO != null) {
//         finishActionRecord.executeInsert(auditDAO);
//      }
   }

   public void stopScheduledTask(String taskName, Principal principal)
      throws Exception
   {
      taskName = Tool.byteDecode(taskName);
      Catalog catalog = Catalog.getCatalog(principal);
      String errorMsg = null;

      if(!scheduleClient.isReady()) {
         errorMsg = catalog.getString("em.scheduler.notStarted");
      }
      else {
         ScheduleTask task = scheduleManager.getScheduleTask(taskName);

         if(task != null && !task.isEnabled()) {
            errorMsg = catalog.getString("em.scheduler.stopDisabledTask",
                                         task.getTaskId());
         }
         else {
            try {
               scheduleClient.stopNow(taskName);
            }
            catch(Throwable ex) {
               LOG.debug("Failed to stop task {}: {}", taskName, ex.getMessage(), ex);
               errorMsg = catalog.getString("em.scheduler.stopFailed", taskName);
            }
         }
      }

      if(errorMsg != null) {
         throw new Exception(errorMsg);
      }
      else {
         try {
            Thread.sleep(3000);
         }
         catch(Exception ignore) {
         }
      }
   }

   public void exportScheduledTasks(String[] taskListModel, OutputStream output) {
      PrintWriter writer = new PrintWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8));
      writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
      writer.write("<schedule>");

      for(String task : taskListModel) {
         ScheduleTask task1 = scheduleManager.getScheduleTask(task);
         task1.writeXML(writer);
      }

      writer.write("<timeRanges>");
      TimeRange.getTimeRanges().stream().forEach(range -> range.writeXML(writer));
      writer.write("</timeRanges>");

      writer.write("</schedule>");
      writer.flush();
   }

   /**
    * Gets the root asset entry.
    *
    * @return the root entry.
    */
   private AssetEntry getRootEntry() {
      return new AssetEntry(
              AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.SCHEDULE_TASK_FOLDER,
              "/", null);
   }

   public TaskListModel checkScheduledTaskDependency(ScheduleTaskModel[] taskList, String selectString, String filter,
                                                     Principal principal)
      throws Exception
   {
      TaskListModel.Builder builder = TaskListModel.builder();
      Vector<ScheduleTask> allTasks = getScheduleTasks(selectString, filter, true, principal);

      for(ScheduleTaskModel task: taskList) {
         if(this.scheduleManager.hasDependency(allTasks, task.name())) {
            builder.addTaskNames(SUtil.getTaskNameWithoutOrg(task.name()));
         }
      }

      return builder.build();
   }

   public TaskListModel checkScheduleFolderDependency(TaskListModel model, Principal principal)
      throws Exception
   {
      TaskListModel.Builder builder = TaskListModel.builder();
      Vector<ScheduleTask> allTasks = getScheduleTasks("", "", true, principal);

      for(String folderPath: model.taskNames()) {
         AssetEntry taskEntry = taskFolderService.getFolderEntry(folderPath);

         checkScheduledTaskDependency0(allTasks, builder, taskEntry);
      }

      return builder.build();
   }

   private void checkScheduledTaskDependency0(Vector<ScheduleTask> allTasks,
                                              TaskListModel.Builder builder,
                                              AssetEntry folderEntry) throws Exception
   {
      if(!folderEntry.isScheduleTaskFolder()) {
         return;
      }

      AssetFolder taskFolder = taskFolderService.getTaskFolder(folderEntry.toIdentifier());
      AssetEntry[] entries = taskFolder.getEntries();

      for(AssetEntry entry : entries) {
         if(entry.isScheduleTaskFolder()) {
            checkScheduledTaskDependency0(allTasks, builder, entry);
         }
         else {
            if(this.scheduleManager.hasDependency(allTasks, entry.getName())) {
               builder.addTaskNames(SUtil.getTaskNameWithoutOrg(entry.getName()));
            }
         }
      }
   }

   public void removeScheduleItems(ScheduleTaskModel[] tasks, Optional<String> selectStr,
                                   Optional<String> filter, Principal principal)
      throws Exception
   {
      for(ScheduleTaskModel task : tasks) {
         String taskName = task.name().startsWith(task.owner().name) ? task.name() : task.owner().convertToKey() + ":" + task.name();
         removeScheduledTask(selectStr.orElse(""), filter.orElse(""), taskName, principal);
      }
   }

   public void removeScheduleFolders(TaskListModel model, Principal principal) throws Exception {
      removeScheduleFolders(model, principal, null);
   }

   public void removeScheduleFolders(TaskListModel model, Principal principal,
                                     TaskRemoveResult result)
      throws Exception
   {
      List<String> noPermissionPaths = new ArrayList<>();
      boolean deleted = false;

      for(String folderPath : model.taskNames()) {
         if(!taskFolderService.checkFolderPermission(folderPath, principal, ResourceAction.DELETE)) {
            noPermissionPaths.add(folderPath);
            continue;
         }

         AssetEntry taskEntry = taskFolderService.getFolderEntry(folderPath);
         String actionName = ActionRecord.ACTION_NAME_DELETE;
         String objectName = "Tasks/" + taskEntry.getPath();
         String objectType = AssetEventUtil.getObjectType(taskEntry);
         Timestamp actionTimestamp = new Timestamp(System.currentTimeMillis());
         ActionRecord actionRecord = new ActionRecord(SUtil.getUserName(principal), actionName, objectName,
                                                      objectType, actionTimestamp,
                                                      ActionRecord.ACTION_STATUS_SUCCESS,
                                                      null);

         try {
            removeScheduledTasks0(taskEntry, principal);
            deleted = true;
         }
         catch(Exception exception) {
            if(exception instanceof ConfirmException) {
               actionRecord = null;
            }

            if(actionRecord != null) {
               actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_FAILURE);
               actionRecord.setActionError(exception.getMessage());
            }

            throw new MessageException(exception.getMessage());
         }
         finally {
            if(actionRecord != null) {
               Audit.getInstance().auditAction(actionRecord, principal);
            }
         }
      }

      if(result != null) {
         result.setRefresh(deleted);
      }

      if(!noPermissionPaths.isEmpty()) {
         String msg = String.format(
            "Unauthorized access to resource(s) \"%s\" by user %s.",
            String.join(", ", noPermissionPaths), principal.getName());

         if(result != null) {
            result.setErrorMessage(msg);
         }
         else {
            throw new SecurityException(msg);
         }
      }
   }

   public void removeScheduledTasks0(AssetEntry folderEntry, Principal principal) throws Exception {
      AssetFolder taskFolder = taskFolderService.getTaskFolder(folderEntry.toIdentifier());
      AssetEntry[] entries = taskFolder.getEntries();

      for(AssetEntry entry : entries) {
         if(entry.isScheduleTaskFolder()) {
            removeScheduledTasks0(entry, principal);
         }
         else {
            removeScheduledTask("", "", entry.getName(), principal);
         }
      }

      taskFolderService.removeFolder(folderEntry);
   }

   /**
    * Gets the identifier of the root asset entry.
    *
    * @return the root identifier.
    */
   private String getRootIdentifier() {
      return getRootEntry().toIdentifier();
   }

   List<ServerLocation> getServerLocations(Catalog catalog) {
      return SUtil.getServerLocations();
   }

   private final AnalyticRepository analyticRepository;
   private final ScheduleManager scheduleManager;
   private final ScheduleClient scheduleClient;
   private final ScheduleConditionService scheduleConditionService;
   private final SecurityProvider securityProvider;
   private final DeployService deployService;
   private final ResourcePermissionService resourcePermissionService;
   private final SecurityEngine securityEngine;
   private final PresentationFormatsSettingsService formatsSettingsService;
   private Map<String, TaskActivity> activities;
   private long activitiesTS;
   private final IndexedStorage indexedStorage;
   private final ScheduleTaskFolderService taskFolderService;
   private long activityTimeout = 5000;

   private static final Logger LOG =
      LoggerFactory.getLogger(ScheduleService.class);
}
