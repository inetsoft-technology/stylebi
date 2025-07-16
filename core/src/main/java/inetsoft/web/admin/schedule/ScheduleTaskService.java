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

import inetsoft.report.io.Builder;
import inetsoft.report.io.ExportType;
import inetsoft.sree.*;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.schedule.*;
import inetsoft.sree.security.SecurityException;
import inetsoft.sree.security.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.util.*;
import inetsoft.uql.viewsheet.FileFormatInfo;
import inetsoft.uql.viewsheet.VSBookmarkInfo;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.*;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import inetsoft.util.dep.ScheduleTaskAsset;
import inetsoft.util.dep.XAsset;
import inetsoft.web.RecycleUtils;
import inetsoft.web.admin.content.repository.model.ExportFormatModel;
import inetsoft.web.admin.general.model.model.SMTPAuthType;
import inetsoft.web.admin.schedule.model.*;
import inetsoft.web.viewsheet.model.dialog.schedule.TimeConditionModel;
import inetsoft.web.viewsheet.model.dialog.schedule.TimeRangeModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class ScheduleTaskService {
   @Autowired
   public ScheduleTaskService(AnalyticRepository analyticRepository,
                              ScheduleManager scheduleManager,
                              ScheduleService scheduleService,
                              ScheduleConditionService scheduleConditionService,
                              SecurityProvider securityProvider,
                              ScheduleTaskFolderService scheduleTaskFolderService)
   {
      this.analyticRepository = analyticRepository;
      this.scheduleManager = scheduleManager;
      this.scheduleService = scheduleService;
      this.scheduleConditionService = scheduleConditionService;
      this.securityProvider = securityProvider;
      this.scheduleTaskFolderService = scheduleTaskFolderService;
   }

   public ScheduleTaskDialogModel getNewTaskDialogModel(PortalNewTaskRequest model,
                                                        Principal principal) throws Exception
   {
      return getNewTaskDialogModel(model.getConditionModel(), principal, true, false,
         model.getParentEntry(), null);
   }

   public ScheduleTaskDialogModel getNewTaskDialogModel(ScheduleConditionModel model,
                                                        Principal principal, boolean save,
                                                        boolean em,  AssetEntry parent,
                                                        String timeZoneId)
      throws Exception
   {
      Catalog catalog = Catalog.getCatalog(principal);
      IdentityID currentUser = IdentityID.getIdentityIDFromKey(principal.getName());
      IdentityID owner = SUtil.getOwnerForNewTask(currentUser);
      String taskId = (owner != null ? owner.convertToKey() : principal.getName()) + ":" +
         catalog.getString("newTaskName");
      ScheduleCondition condition;
      int duplicateNameIndex;

      for(duplicateNameIndex = 1; duplicateNameIndex < Integer.MAX_VALUE; duplicateNameIndex++) {
         if(scheduleManager.getScheduleTask(taskId + duplicateNameIndex) == null) {
            taskId += duplicateNameIndex;
            break;
         }
      }

      String taskName = catalog.getString("newTaskName") + duplicateNameIndex;

      if(model instanceof TimeConditionModel) {
         if(((TimeConditionModel) model).type() == TimeCondition.AT &&
            !securityProvider.checkPermission(
               principal, ResourceType.SCHEDULE_OPTION, "startTime", ResourceAction.READ))
         {
            model = null;
         }
      }

      if(model != null) {
         condition = scheduleConditionService.getConditionFromModel(model);

         if(condition instanceof TimeCondition) {
            TimeCondition timeCondition = (TimeCondition) condition;

            if(timeCondition.getTimeRange() != null) {
               timeCondition.setTimeZone(null);
               timeCondition.setHour(1);
               timeCondition.setMinute(30);
            }
         }
      }
      else {
         condition = TimeCondition.at(1, 30, 0);
         ((TimeCondition) condition).setInterval(1);
         ((TimeCondition) condition).setHourEnd(1);
         ((TimeCondition) condition).setMinuteEnd(30);
         ((TimeCondition) condition).setSecondEnd(0);

         if(timeZoneId != null) {
            ((TimeCondition) condition).setTimeZone(TimeZone.getTimeZone(timeZoneId));
         }
      }

      ScheduleTask task = new ScheduleTask(taskName);
      task.addCondition(condition);
      task.setOwner(owner);

      if(save) {
         // log create task action
         ActionRecord actionRecord;
         String actionName = ActionRecord.ACTION_NAME_CREATE;
         String objectType = ActionRecord.OBJECT_TYPE_TASK;
         Timestamp actionTimestamp = new Timestamp(System.currentTimeMillis());
         actionRecord = new ActionRecord(SUtil.getUserName(principal), actionName, taskId,
                                         objectType, actionTimestamp,
                                         ActionRecord.ACTION_STATUS_FAILURE,
                                         null);

         try {
            scheduleManager.setScheduleTask(taskId, task, parent, principal);
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

      return getDialogModel(taskId, principal, em);
   }

   /**
    * Method for getting a schedule task model
    * @param taskName   URL Decoded task Name
    */
   public ScheduleTaskDialogModel getDialogModel(String taskName, Principal principal, boolean em)
      throws Exception
   {
      taskName = Tool.byteDecode(taskName);

      RepletEngine engine = SUtil.getRepletEngine(analyticRepository);
      ScheduleTask task = scheduleManager.getScheduleTask(taskName);
      boolean canDelete = em && ScheduleManager.isInternalTask(taskName)
         ? canDeleteInternalTask(task, principal)
         : canDeleteTask(task, principal);

      if(!canDelete) {
         throw new SecurityException(String.format("Unauthorized access to resource \"%s\" by %s",
            task, principal));
      }

      String zoneName = Calendar.getInstance().getTimeZone().getDisplayName();

      int index = taskName.indexOf(":");
      String label = index != -1 ? taskName.substring(index + 1) : taskName;
      return createTaskDialogModel(taskName, label, zoneName, principal, em);
   }

   public boolean canDeleteInternalTask(ScheduleTask task, Principal principal) {
      RepletEngine engine = SUtil.getRepletEngine(analyticRepository);

      if(task == null || principal == null || engine == null) {
         return false;
      }

      OrganizationManager organizationManager = OrganizationManager.getInstance();

      if(organizationManager.isSiteAdmin(principal) || organizationManager.isOrgAdmin(principal)) {
         return true;
      }

      return engine.checkPermission(principal, ResourceType.SCHEDULE_TASK,
         task.getName(), ResourceAction.WRITE);
   }

   public boolean canDeleteTask(ScheduleTask task, Principal principal) {
      if(task == null) {
         return false;
      }

      RepletEngine engine = SUtil.getRepletEngine(analyticRepository);

      if(engine != null && !engine.hasTaskPermission(task, principal)) {
         return false;
      }

      OrganizationManager organizationManager = OrganizationManager.getInstance();
      boolean adminPermission = false;

      try {
         SecurityEngine securityEngine = SecurityEngine.getSecurity();
         adminPermission = securityEngine.checkPermission(
            principal, ResourceType.SECURITY_USER, task.getOwner(), ResourceAction.ADMIN);
      }
      catch(Exception ignore) {
      }

      if(organizationManager.isSiteAdmin(principal) || organizationManager.isOrgAdmin(principal) ||
         adminPermission)
      {
         return true;
      }

      return principal != null &&
         (Tool.equals(principal.getName(), task.getOwner().convertToKey()) ||
            !scheduleManager.isDeleteOnlyByOwner(task, principal));
   }

   /**
    * Method for getting a schedule task model
    * @param taskName   URL Decoded task Name
    */
   public ScheduleTaskDialogModel getDialogModel(String taskName, Principal principal)
      throws Exception
   {
      return getDialogModel(taskName, principal, false);
   }

   private ScheduleTaskDialogModel createTaskDialogModel(String taskName, String label,
                                                         String zoneName,
                                                         Principal principal, boolean em)
      throws Exception
   {
      Catalog catalog = Catalog.getCatalog(principal);
      List<TimeRangeModel> ranges = TimeRange.getTimeRanges().stream()
         .filter(r -> checkPermission(r, principal))
         .sorted()
         .map(r -> TimeRangeModel.builder().from(r, catalog).build())
         .collect(Collectors.toList());
      boolean startTimeEnabled = securityProvider.checkPermission(
         principal, ResourceType.SCHEDULE_OPTION, "startTime", ResourceAction.READ);
      IdentityID pId = IdentityID.getIdentityIDFromKey(principal.getName());
      String userOrgId = pId.orgID;
      boolean multitenant = SUtil.isMultiTenant();
      boolean timeRangeEnabled = (!multitenant || OrganizationManager.getInstance().isSiteAdmin(principal) &&
         Tool.equals(OrganizationManager.getInstance().getCurrentOrgID(), userOrgId)) && securityProvider.checkPermission(
      principal, ResourceType.SCHEDULE_OPTION, "timeRange", ResourceAction.READ);
      List<TimeZoneModel> timeZoneOptions = TimeZoneModel.getTimeZoneOptions();
      String defaultTimeProp = SreeEnv.getProperty("schedule.condition.taskDefaultTime");
      boolean taskDefaultTime = !"false".equals(defaultTimeProp);

      return ScheduleTaskDialogModel.builder()
         .name(taskName)
         .label(label)
         .taskDefaultTime(taskDefaultTime)
         .taskOptionsPaneModel(getTaskOptions(taskName, principal))
         .taskConditionPaneModel(getTaskConditions(taskName, principal))
         .taskActionPaneModel(getTaskActions(taskName, principal, em))
         .timeZone(zoneName)
         .timeZoneOptions(timeZoneOptions)
         .internalTask(ScheduleManager.isInternalTask(taskName))
         .timeRanges(ranges)
         .startTimeEnabled(startTimeEnabled)
         .timeRangeEnabled(timeRangeEnabled)
         .build();
   }

   public TaskConditionPaneModel getTaskConditions(@RequestParam("name") String taskId,
                                                   Principal principal)
      throws Exception
   {
      Catalog catalog = Catalog.getCatalog(principal);

      if(taskId == null || "".equals(taskId)) {
         throw new Exception(catalog.getString("em.scheduler.emptyTaskName"));
      }

      ScheduleTask task = scheduleManager.getScheduleTask(taskId);

      if(task == null) {
         throw new Exception(catalog.getString(
            "em.scheduler.taskNotFound", taskId));
      }

      TaskConditionPaneModel.Builder builder = TaskConditionPaneModel.builder();

      task.getConditionStream()
         .map(condition -> scheduleConditionService.getConditionModel(condition, principal))
         .forEach(builder::addConditions);

      String timeProp = SreeEnv.getProperty("format.time");

      if(timeProp == null || "".equals(timeProp)) {
         timeProp = Tool.DEFAULT_TIME_PATTERN;
      }

      timeProp = timeProp.trim();

      return builder
         .timeProp(timeProp)
         .twelveHourSystem(SreeEnv.getBooleanProperty("schedule.time.12hours"))
         .build();
   }

   public TaskActionPaneModel getTaskActions(@RequestParam("name") String taskName,
                                             Principal principal)
      throws Exception
   {
      return getTaskActions(taskName, principal, false);
   }

   public TaskActionPaneModel getTaskActions(@RequestParam("name") String taskName,
                                             Principal principal, boolean em)
      throws Exception
   {
      Catalog catalog = Catalog.getCatalog(principal);

      if(taskName == null || "".equals(taskName)) {
         throw new Exception(catalog.getString("em.scheduler.emptyTaskName"));
      }

      ScheduleTask task = scheduleManager.getScheduleTask(taskName);

      if(task == null) {
         throw new Exception(catalog.getString(
            "em.scheduler.taskNotFound", taskName));
      }

      TaskActionPaneModel.Builder builder = TaskActionPaneModel.builder();

      task.getActionStream()
         .map(a -> scheduleService.getActionModel(a, principal, em))
         .filter(Objects::nonNull)
         .forEach(builder::addActions);

      AssetEntry[] viewsheets = scheduleService.getViewsheets(principal);
      final Map<String, String> viewsheetMap = new HashMap<>();

      for(AssetEntry entry : viewsheets) {
         String path = entry.getPath();
         path =
            entry.getScope() == AssetRepository.USER_SCOPE ? SUtil.MY_REPORT + "/" + path : path;

         final String viewsheetId = entry.toIdentifier();
         final String viewsheetPath = SUtil.localize(path, principal, true, entry);
         viewsheetMap.put(viewsheetId, viewsheetPath);
      }

      try {
         RepositoryEntry[] folderEntries = analyticRepository.getFolders(
            principal, EnumSet.of(ResourceAction.READ, ResourceAction.WRITE));

         for(RepositoryEntry folderEntry : folderEntries) {
            String folder = folderEntry.getPath();

            if(!RecycleUtils.isInRecycleBin(folder)) {
               builder.addFolderPaths(folder);
               builder.addFolderLabels(SUtil.localize(folder, principal, false));
            }
         }
      }
      catch(Exception ex) {
         LOG.error("Failed to list folders for user: " + principal, ex);
      }

      boolean showFrom = "true".equals(SreeEnv.getProperty("mail.from.enabled", "false"));
      String[] emails = new String[0];

      try {
         emails = SUtil.getEmails(task.getOwner());
      } catch(Exception e) {
         // Do nothing
      }

      boolean useSelf = !"false".equals(SreeEnv.getProperty("em.mail.defaultEmailFromSelf"));
      String userEmail = useSelf && emails.length > 0 ? emails[0] : null;
      boolean smtp =
         SMTPAuthType.forValue(SreeEnv.getProperty("mail.smtp.auth")) == SMTPAuthType.GOOGLE_AUTH ||
         SMTPAuthType.forValue(SreeEnv.getProperty("mail.smtp.auth")) == SMTPAuthType.SMTP_AUTH;
      String fromEmail = userEmail != null && !userEmail.isEmpty() && !smtp ?
            userEmail : SreeEnv.getProperty("mail.from.address");

      boolean historyEnabled = "true".equalsIgnoreCase(SreeEnv.getProperty("mail.history.enabled"));

      return builder
         .securityEnabled(isSecurityEnabled())
         .administrator(scheduleService.isSecurityEnabled())
         .emailButtonVisible(getBooleanProperty("schedule.options.emailBrowserEnable"))
         .fromEmailEnabled(showFrom)
         .defaultFromEmail(fromEmail)
         .viewsheetEnabled(true)
         .notificationEmailEnabled(
            getBooleanProperty("schedule.options.notificationEmail")
               && scheduleService.checkPermission(
               principal, ResourceType.SCHEDULE_OPTION, "notificationEmail"))
         .saveToDiskEnabled(
            getBooleanProperty("schedule.options.saveToDisk")
               && scheduleService.checkPermission(principal,
                                                  ResourceType.SCHEDULE_OPTION, "saveToDisk"))
         .emailDeliveryEnabled(
            getBooleanProperty("schedule.options.emailDelivery")
               && scheduleService.checkPermission(principal,
                                                  ResourceType.SCHEDULE_OPTION, "emailDelivery"))
         .cvsEnabled(false)
         .dashboardMap(viewsheetMap)
         .mailFormats(getMailFormats(principal))
         .vsMailFormats(getVSMailFormats())
         .saveFileFormats(getSaveFormats(principal))
         .vsSaveFileFormats(getVSSaveFormats())
         .serverLocations(scheduleService.getServerLocations(catalog))
         .expandEnabled(SecurityEngine.getSecurity().checkPermission(
            principal, ResourceType.VIEWSHEET_TOOLBAR_ACTION, "ScheduleExpandComponents",
            ResourceAction.READ))
         .mailHistoryEnabled(historyEnabled)
         .fipsMode(PasswordEncryption.isFipsCompliant())
         .cloudSecrets(Tool.isCloudSecrets())
         .build();
   }

   public boolean isTaskEnabled(String name) {
      return scheduleManager.getScheduleTask(name).isEnabled();
   }

   public void setTaskEnabled(String name, boolean enabled, Principal principal) throws Exception {
      ScheduleTask task = scheduleManager.getScheduleTask(name);
      task.setEnabled(enabled);
      scheduleService.saveTask(name, task, principal);
   }

   /**
    * Save a task and update it's name if necessary
    */
   public ScheduleTaskDialogModel saveTask(ScheduleTaskEditorModel model, String linkURI,
                                           Principal principal)
      throws Exception
   {
      return saveTask(model, linkURI, principal, false);
   }

   /**
    * Save a task and update it's name if necessary
    */
   public ScheduleTaskDialogModel saveTask(ScheduleTaskEditorModel model, String linkURI,
                                           Principal principal, boolean em)
      throws Exception
   {
      String oldTaskName = model.oldTaskName();
      Catalog catalog = Catalog.getCatalog(principal);
      ScheduleTask task;
      boolean internalTask = ScheduleManager.isInternalTask(oldTaskName);
      // if it's an internal task, ignore the localized name
      String taskName = internalTask ? oldTaskName : model.taskName();

      if(internalTask) {
         task = scheduleManager.getScheduleTask(oldTaskName) == null ? null :
            scheduleManager.getScheduleTask(oldTaskName).clone();
      }
      else {
         if("".equals(taskName)) {
            throw new Exception(catalog.getString("em.scheduler.emptyTaskName"));
         }

         IdentityID owner = null;

         if(model.options() != null && model.options().owner() != null) {
            owner = getIdentityId(model.options().owner(), principal);
         }

         taskName = scheduleService.updateTaskName(oldTaskName, taskName, owner, principal);
         task = scheduleManager.getScheduleTask(taskName) == null ? null :
            scheduleManager.getScheduleTask(taskName).clone();
      }

      if(task == null) {
         throw new Exception(catalog.getString("em.scheduler.taskNotFound", taskName));
      }

      Set<TimeRange> ranges = new HashSet<>();

      for(int i = 0; i < model.conditions().size(); i++) {
         int index = i >= task.getConditionCount() ? -1 : i;
         TimeRange range = scheduleService.setTaskCondition(
            taskName, index, model.conditions().get(i), catalog, task);

         if(range != null) {
            ranges.add(range);
         }
      }

      for(int i = task.getConditionCount() - 1; i >= model.conditions().size(); i--) {
         task.removeCondition(i);
      }

      if(!internalTask) {
         for(int i = 0; i < model.actions().size(); i++) {
            ScheduleAction action =
               scheduleService.getActionFromModel(model.actions().get(i), principal, linkURI);

            if(action == null) {
               continue;
            }

            if(action instanceof IndividualAssetBackupAction) {
               IndividualAssetBackupAction backupAction = (IndividualAssetBackupAction) action;

               renameBackupAction(backupAction, task.getPath(), oldTaskName, taskName);
            }
            else if(action instanceof ViewsheetAction) {
               ViewsheetAction vsAction = (ViewsheetAction) action;

               if((!vsAction.isMatchLayout() || vsAction.isExpandSelections() ||
                  vsAction.isOnlyDataComponents()) &&
                  !SecurityEngine.getSecurity().checkPermission(principal,
                  ResourceType.VIEWSHEET_TOOLBAR_ACTION, "ScheduleExpandComponents",
                  ResourceAction.READ))
               {
                  throw new Exception(catalog.getString("em.schedule.task.noExpandPermission"));
               }
            }

            if(i >= task.getActionCount()) {
               task.addAction(action);
            }
            else {
               task.setAction(i, action);
            }
         }

         for(int i = task.getActionCount() - 1; i >= model.actions().size(); i--) {
            task.removeAction(i);
         }
      }

      // Update Options
      if(model.options() != null) {
         setTaskOptions(model.options(), task, principal);
      }

      // Save task
      scheduleService.saveTask(taskName, task, principal);

      // Balance tasks after saving
      if(!ranges.isEmpty()) {
         TaskBalancer balancer = new TaskBalancer();

         for(TimeRange range : ranges) {
            balancer.updateTask(task, range);
         }
      }

      return getDialogModel(taskName, principal, em);
   }

   private void renameBackupAction(IndividualAssetBackupAction action, String path, String oid, String nid) {
      if(action.getAssets() == null || action.getAssets().isEmpty()) {
         return;
      }

      String currentOldTask = "/".equals(path) ? oid : path + "/" + oid;
      String currentNewPath = "/".equals(path) ? nid : path + "/" + nid;

      for(int i = 0; i < action.getAssets().size(); i++) {
         XAsset asset = action.getAssets().get(i);

         if(!(asset instanceof ScheduleTaskAsset)) {
            continue;
         }

         ScheduleTaskAsset taskAsset = (ScheduleTaskAsset) asset;

         if(Tool.equals(currentOldTask, taskAsset.getTask())) {
            taskAsset.setTask(currentNewPath);
         }
      }
   }


   public void setOptions(@RequestBody TaskOptionsPaneModel model,
                          @RequestParam("name") String taskName,
                          @RequestParam("oldTaskName") String oldTaskName,
                          Principal principal)
      throws Exception
   {
      Catalog catalog = Catalog.getCatalog(principal);

      if(taskName == null || "".equals(taskName)) {
         throw new Exception(catalog.getString("em.scheduler.emptyTaskName"));
      }

      IdentityID owner = getIdentityId(model.owner(), principal);
      taskName = scheduleService.updateTaskName(oldTaskName, taskName, owner, principal);
      ScheduleTask task = scheduleManager.getScheduleTask(taskName);

      if(task == null) {
         task = new ScheduleTask();
      }

      setTaskOptions(model, task, principal);
      scheduleService.saveTask(taskName, task, principal);
   }

   public DistributionModel getWeekDistribution(Principal principal) throws Exception {
      ScheduleTaskList tasks = scheduleService.getScheduleTaskList("", "", principal);
      List<ModifiableDistributionData> data = IntStream.range(Calendar.SUNDAY, Calendar.SATURDAY + 1)
         .mapToObj(weekday -> ModifiableDistributionData.create()
            .setIndex(weekday)
            .setLabel("")
            .setHardCount(0)
            .setSoftCount(0))
         .collect(Collectors.toList());

      for(ScheduleTaskModel task : tasks.tasks()) {
         TaskDistribution distribution = task.distribution();

         if(distribution != null) {
            for(TaskDistributionGroup group : distribution.days()) {
               if(group.index() <0) {
                  continue;
               }

               ModifiableDistributionData dayData = data.get(group.index() - 1);
               dayData.setHardCount(dayData.hardCount() + group.hardCount());
               dayData.setSoftCount(dayData.softCount() + group.softCount());
            }
         }
      }

      DistributionModel.Builder builder = new DistributionModel.Builder();
      Catalog catalog = Catalog.getCatalog(principal);
      data.stream()
         .map(d -> DistributionData.builder().from(d).weekday(d.index(), catalog).build())
         .forEach(builder::addData);

      return builder.build();
   }

   public DistributionModel getDayDistribution(int weekday, Principal principal) throws Exception {
      ScheduleTaskList tasks = scheduleService.getScheduleTaskList("", "", principal);
      List<ModifiableDistributionData> data = IntStream.range(0, 24)
         .mapToObj(hour -> ModifiableDistributionData.create()
            .setIndex(hour)
            .setLabel("")
            .setHardCount(0)
            .setSoftCount(0))
         .collect(Collectors.toList());

      for(ScheduleTaskModel task : tasks.tasks()) {
         TaskDistribution distribution = task.distribution();

         if(distribution != null) {
            for(TaskDistributionGroup dayGroup : distribution.days()) {
               if(copyDistributionGroupData(weekday, data, dayGroup)) {
                  break;
               }
            }
         }
      }

      DistributionModel.Builder builder = new DistributionModel.Builder();
      Catalog catalog = Catalog.getCatalog(principal);
      data.stream()
         .map(d -> DistributionData.builder().from(d).hour(d.index(), catalog).build())
         .forEach(builder::addData);

      return builder.build();
   }

   public DistributionModel getHourDistribution(int weekday, int hour, Principal principal) throws Exception {
      ScheduleTaskList tasks = scheduleService.getScheduleTaskList("", "", principal);
      List<ModifiableDistributionData> data = IntStream.range(0, 6)
         .mapToObj(minute -> ModifiableDistributionData.create()
            .setIndex(minute)
            .setLabel("")
            .setHardCount(0)
            .setSoftCount(0))
         .collect(Collectors.toList());

      for(ScheduleTaskModel task : tasks.tasks()) {
         TaskDistribution distribution = task.distribution();

         if(distribution != null) {
            for(TaskDistributionGroup dayGroup : distribution.days()) {
               if(dayGroup.index() == weekday) {
                  for(TaskDistributionGroup hourGroup : dayGroup.children()) {
                     if(copyDistributionGroupData(hour, data, hourGroup)) {
                        break;
                     }
                  }

                  break;
               }
            }
         }
      }

      DistributionModel.Builder builder = new DistributionModel.Builder();
      Catalog catalog = Catalog.getCatalog(principal);
      data.stream()
         .map(d -> DistributionData.builder().from(d).minute(hour, d.index(), catalog).build())
         .forEach(builder::addData);

      return builder.build();
   }

   public ScheduleTaskList redistributeTasks(LocalTime startTime, LocalTime endTime,
                                             int maxConcurrency, List<String> taskNames,
                                             Principal principal)
      throws Exception
   {
      List<ScheduleTask> tasks = taskNames.stream()
         .map(scheduleManager::getScheduleTask)
         .filter(Objects::nonNull)
         .collect(Collectors.toList());
      long count = tasks.stream()
         .flatMap(ScheduleTask::getConditionStream)
         .filter(TimeCondition.class::isInstance)
         .count();
      long duration = Duration.between(startTime, endTime).get(ChronoUnit.SECONDS) / 60L;
      int concurrency;
      long interval = duration / count;

      for(concurrency = 1; concurrency < maxConcurrency && interval < 10L; concurrency++) {
         interval = duration / (long) Math.ceil(count / (double) concurrency);
      }

      interval = duration / (long) Math.ceil(count / (double) concurrency);

      if(interval > 60) {
         interval = 60;
      }
      else if(interval > 30) {
         interval = 30;
      }
      else if(interval > 15) {
         interval = 15;
      }
      else if(interval > 10) {
         interval = 10;
      }
      else if(interval > 5) {
         interval = 5;
      }

      LocalTime time = LocalTime.from(startTime);

      for(ScheduleTask task : tasks) {
         boolean modified = false;

         for(int i = 0; i < task.getConditionCount(); i++) {
            if(task.getCondition(i) instanceof TimeCondition) {
               if(!time.isBefore(endTime)) {
                  time = LocalTime.from(startTime);
               }

               TimeCondition condition = (TimeCondition) task.getCondition(i);

               switch(condition.getType()) {
               case TimeCondition.EVERY_HOUR:
                  LocalTime conditionStart = LocalTime.of(
                     condition.getHour(), condition.getMinute(), condition.getSecond());
                  LocalTime conditionEnd = LocalTime.of(
                     condition.getHourEnd(), condition.getMinuteEnd(), condition.getSecondEnd());
                  Duration conditionDuration = Duration.between(conditionStart, conditionEnd);
                  conditionEnd = time.plus(conditionDuration);
                  condition.setHourEnd(conditionEnd.getHour());
                  condition.setMinuteEnd(conditionEnd.getMinute());
                  condition.setSecondEnd(0);
               case TimeCondition.EVERY_DAY:
               case TimeCondition.EVERY_WEEK:
               case TimeCondition.EVERY_MONTH:
                  condition.setHour(time.getHour());
                  condition.setMinute(time.getMinute());
                  condition.setSecond(0);
                  break;
               default:
                  continue;
               }

               // set task start time to time
               time = time.plusMinutes(interval);
               modified = true;
            }
         }

         if(modified) {
            scheduleService.saveTask(task.getTaskId(), task, principal);
         }
      }

      return scheduleService.getScheduleTaskList("", "", principal);
   }

   private boolean copyDistributionGroupData(int index, List<ModifiableDistributionData> data,
                                             TaskDistributionGroup group)
   {
      if(group.index() == index) {
         for(TaskDistributionGroup child : group.children()) {
            ModifiableDistributionData minData = data.get(child.index());
            minData.setHardCount(minData.hardCount() + child.hardCount());
            minData.setSoftCount(minData.softCount() + child.softCount());
         }

         return true;
      }

      return false;
   }

   private void setTaskOptions(TaskOptionsPaneModel model, ScheduleTask task, Principal principal) {
      task.setEnabled(model.enabled());
      task.setDeleteIfNoMoreRun(model.deleteIfNotScheduledToRun());

      if(model.startFrom() > 0) {
         task.setStartDate(new Date(model.startFrom()));
      }
      else {
         task.setStartDate(null);
      }

      if(model.stopOn() > 0) {
         task.setEndDate(new Date(model.stopOn()));
      }
      else {
         task.setEndDate(null);
      }

      int type = model.idType();
      Identity oldIdentity = task.getIdentity();
      Identity newIdentity = SUtil.getIdentity(getIdentityId(model.idName(), principal), type);
      task.setIdentity(newIdentity);

      if((oldIdentity == null || oldIdentity.getType() == Identity.USER) &&
         newIdentity != null && newIdentity.getType() == Identity.GROUP) {
      }

      IdentityID oldIdentityID = oldIdentity != null ? oldIdentity.getIdentityID() : null;
      task.setOwner(getIdentityId(model.owner(), principal));
      task.setLocale(getTaskLocale(model.locale()));
      task.setDescription(model.description());
      task.setTimeZone(model.timeZone());
      IdentityID modelID = getIdentityId(model.idName(), principal);
      modelID = modelID == null ? task.getOwner() : modelID;

      //handle vs bookmark when executer has changed.
      if((modelID == null && oldIdentity != null && oldIdentityID != null
         || modelID != null && (oldIdentityID == null || !modelID.equals(oldIdentityID))))
      {
         for(int i = 0; i < task.getActionCount(); i++) {
            if(task.getAction(i) instanceof ViewsheetAction) {
               ViewsheetAction action = (ViewsheetAction) task.getAction(i);

               List<String> bookmarkList = new ArrayList<>();
               List<IdentityID> bookmarkUserList = new ArrayList<>();
               List<Integer> bookmarkTypeList = new ArrayList<>();
               String[] bookmarks = action.getBookmarks();
               int[] bookmarkTypes = action.getBookmarkTypes();
               IdentityID[] bookmarkUsers = action.getBookmarkUsers();

               for(int j = 0; bookmarkTypes != null && j < bookmarkTypes.length; j++) {
                  boolean groupShare = ScheduleManager.isSameGroup(modelID, bookmarkUsers[j]);

                  if(bookmarkTypes[j] == VSBookmarkInfo.ALLSHARE ||
                     (bookmarkTypes[j] == VSBookmarkInfo.GROUPSHARE && groupShare) ||
                     Tool.equals(modelID, bookmarkUsers[j]))
                  {
                     bookmarkList.add(bookmarks[j]);
                     bookmarkUserList.add(bookmarkUsers[j]);
                     bookmarkTypeList.add(bookmarkTypes[j]);
                  }
               }

               action.setBookmarkTypes(bookmarkTypeList.stream().mapToInt(Integer::intValue).toArray());
               action.setBookmarkUsers(bookmarkUserList.toArray(IdentityID[]::new));
               action.setBookmarks(bookmarkList.toArray(String[]::new));
            }
         }
      }
   }


   /**
    * Get the task locale language depends on locale label.
    *
    * @param localStr the locale label.
    *
    * @return the langulage which get from locale properties.
    */
   private String getTaskLocale(String localStr) {
      Properties prop = SUtil.loadLocaleProperties();
      Enumeration propNames = prop.propertyNames();

      while(propNames.hasMoreElements()) {
         String propName = (String) propNames.nextElement();

         if(localStr != null && localStr.equals(prop.getProperty(propName))) {
            return propName;
         }
      }

      return null;
   }

   /**
    * Gets the model for the options pane of the schedule task dialog.
    */
   private TaskOptionsPaneModel getTaskOptions(String taskName, Principal principal)
      throws Exception
   {
      TaskOptionsPaneModel.Builder model = TaskOptionsPaneModel.builder();
      Catalog catalog = Catalog.getCatalog(principal);

      if(taskName == null || "".equals(taskName)) {
         throw new Exception(catalog.getString("em.scheduler.emptyTaskName"));
      }

      ScheduleTask task = scheduleManager.getScheduleTask(taskName);

      if(task == null) {
         throw new Exception(catalog.getString(
            "em.scheduler.taskNotFound", taskName));
      }

      if(principal instanceof SRPrincipal) {
         model.selfOrg(OrganizationManager.getInstance().getCurrentOrgID(principal).equals(Organization.getSelfOrganizationID()));

         IdentityID pId = IdentityID.getIdentityIDFromKey(principal.getName());
         if(!OrganizationManager.getInstance().isSiteAdmin(principal)) {
            model.organizationID(((SRPrincipal) principal).getOrgId());
         }
      }

      model.enabled(task.isEnabled());
      model.deleteIfNotScheduledToRun(task.isDeleteIfNoMoreRun());

      if(task.getStartDate() != null) {
         model.startFrom(task.getStartDate().getTime());
      }

      if(task.getEndDate() != null) {
         model.stopOn(task.getEndDate().getTime());
      }

      if(task.getIdentity() != null) {
         IdentityID idName = task.getIdentity().getIdentityID();
         int type = task.getIdentity().getType();

         model.idName(idName.getName());
         model.idType(type);

         if(type == Identity.Type.USER.code()) {
            model.idAlias(SUtil.getUserAlias(task.getIdentity().getIdentityID()));
         }
      }

//      if(SUtil.isAdmin(principal)) {
//         model.adminName(Optional.ofNullable(principal.getName()));
//         SecurityEngine security = SecurityEngine.getSecurity();
//         owners = security.getUsers();
//
//         Tool.qsort(owners, true);
//         String[] users = owners;
//         owners = new String[owners.length];
//         System.arraycopy(users, 0, owners, 0, users.length);
//      }
//      else {
//      owners = new String[]{ principal.getName() };
//      }

      model.securityEnabled(isSecurityEnabled());
      model.owner(task.getOwner() != null ? task.getOwner().getName() : null);
      model.ownerAlias(SUtil.getUserAlias(task.getOwner()));

      Properties prop = SUtil.loadLocaleProperties();
      String taskLocale = task.getLocale() == null ? null :
         prop.getProperty(task.getLocale());
      model.locale(taskLocale);

      // List of locales
      String localesStr = SreeEnv.getProperty("locale.available");
      String[] locales = Tool.split(localesStr, ':');
      String[] localeTexts = new String[locales.length];

      for(int i = 0; i < locales.length; i++) {
         localeTexts[i] = prop.getProperty(locales[i]);
      }

      model.locales(localeTexts);

      model.description(task.getDescription());
      model.timeZone(task.getTimeZone());

      return model.build();
   }

   private String[] getOwners(Principal principal) {
      return Arrays.stream(securityProvider.getUsers())
         .filter(r -> securityProvider.checkPermission(
            principal, ResourceType.SECURITY_USER, r.convertToKey(), ResourceAction.ADMIN))
         .sorted()
         .toArray(String[]::new);
   }

   private String[] getGroups(Principal principal) {
      return Arrays.stream(securityProvider.getGroups())
         .filter(groupName -> securityProvider.checkPermission(
            principal, ResourceType.SECURITY_GROUP, groupName.convertToKey(), ResourceAction.ADMIN))
         .sorted()
         .toArray(String[]::new);
   }

   public List<IdentityID> getExecuteAsUsers(IdentityID user, Principal principal) {
      if(user != null && !user.equals(IdentityID.getIdentityIDFromKey(principal.getName())) && !securityProvider.checkPermission(
         principal, ResourceType.SECURITY_USER, user.convertToKey(), ResourceAction.ADMIN))
      {
         // current user is not the owner and not the admin of the user that owns the task
         return new ArrayList<>();
      }

      String currOrgId = OrganizationManager.getInstance().getCurrentOrgID();
      List<IdentityID> executeAsUsers =  Arrays.stream(securityProvider.getUsers())
         .filter(identityId -> Tool.equals(currOrgId, identityId.getOrgID()) && securityProvider.checkPermission(
            principal, ResourceType.SECURITY_USER, identityId.convertToKey(), ResourceAction.ADMIN))
         .sorted()
         .collect(Collectors.toList());

      if(!executeAsUsers.contains(user) && Tool.equals(user.getOrgID(), currOrgId)) {
         executeAsUsers.add(user);
      }

      return executeAsUsers;
   }

   public List<IdentityID> getExecuteAsGroups(String user, Principal principal) {
      if(user != null && !user.equals(principal.getName()) && !securityProvider.checkPermission(
         principal, ResourceType.SECURITY_USER, user, ResourceAction.ADMIN))
      {
         // current user is not the owner and not the admin of the user that owns the task
         return new ArrayList<>();
      }

      String currOrgId = OrganizationManager.getInstance().getCurrentOrgID();
      return Arrays.stream(securityProvider.getGroups())
         .filter(group -> Tool.equals(currOrgId, group.getOrgID()) && securityProvider.checkPermission(
            principal, ResourceType.SECURITY_GROUP, group.convertToKey(), ResourceAction.ADMIN))
         .sorted()
         .collect(Collectors.toList());
   }

   /**
    * Check the task permission.
    * @param taskName task name.
    * @param principal user.
    * @param action permission action.
    * @return
    */
   public boolean checkTaskPermission(String taskName, Principal principal, ResourceAction action)
      throws SecurityException
   {
      if(ScheduleManager.isInternalTask(taskName)) {
         return securityProvider.checkPermission(principal, ResourceType.SCHEDULE_TASK, taskName,
            ResourceAction.WRITE);
      }
      else {

         ScheduleTask task = scheduleManager.getScheduleTask(taskName);

         if(task == null) {
            return false;
         }

         String path = task.getPath();

         if(!scheduleTaskFolderService.checkFolderPermission(path, principal, ResourceAction.READ)) {
            return false;
         }

         return ScheduleManager.hasTaskPermission(task.getOwner(), principal, action);
      }
   }

   /**
    * Check the user permission.
    * @param principal user.
    * @param owner task owner.
    * @return
    */
   public boolean checkUserPermission(String owner, Principal principal) {
      if(principal == null || owner == null) {
         return false;
      }

      if(Objects.equals(owner, principal.getName())) {
         return true;
      }

      return securityProvider.checkPermission(
         principal, ResourceType.SECURITY_USER, owner, ResourceAction.ADMIN);
   }

   /**
    * Check if security is enabled.
    */
   private boolean isSecurityEnabled() {
      return !securityProvider.isVirtual();
   }

   private List<ExportFormatModel> getMailFormats(Principal principal) throws Exception {
      List<ExportFormatModel> formatModels = new ArrayList<>();
      int[] formats = Builder.getSupportedExportTypes();

      for(int format : formats) {
         ExportType type = Builder.getExportType(format);

         if(!type.isMailSupported()) {
            continue;
         }

         String opt = type.getFormatOption();
         String desc = type.getDescription();

         if(!isAvailable(opt, principal)) {
            continue;
         }

         formatModels.add(new ExportFormatModel(opt, Catalog.getCatalog().getString(desc)));
      }

      return formatModels;
   }

   private List<ExportFormatModel> getVSMailFormats() {
      List<ExportFormatModel> formatModels = new ArrayList<>();
      String[] vsformats = new String[]{ FileFormatInfo.EXPORT_NAME_EXCEL,
                                         FileFormatInfo.EXPORT_NAME_POWERPOINT,
                                         FileFormatInfo.EXPORT_NAME_PDF,
                                         FileFormatInfo.EXPORT_NAME_HTML,
                                         FileFormatInfo.EXPORT_NAME_PNG ,
                                         FileFormatInfo.EXPORT_NAME_CSV};
      String settingFormats = SreeEnv.getProperty("vsexport.menu.options");

      List<String> formatsList = settingFormats == null ?
         Arrays.asList(VSUtil.getExportOptions()) : Arrays.asList(Tool.split(settingFormats, ','));

      for(String vsformat : vsformats) {
         if(formatsList.contains(vsformat)) {
            String label = vsformat;

            if(FileFormatInfo.EXPORT_NAME_PNG.equals(vsformat)) {
               label = "Embedded PNG";
            }

            formatModels.add(new ExportFormatModel(vsformat, Catalog.getCatalog().getString(label)));
         }
      }

      return formatModels;
   }

   private List<ExportFormatModel> getSaveFormats(Principal principal) throws Exception {
      List<ExportFormatModel> formatModels = new ArrayList<>();
      int[] formats = Builder.getSupportedExportTypes();

      for(int format : formats) {
         ExportType type = Builder.getExportType(format);

         if(!type.isExportSupported()) {
            continue;
         }

         // @by yuz, temporaly do not support html of no bundle
         int fmt = type.getFormatId();

         if(!isAvailable(type.getFormatOption(), principal)) {
            continue;
         }

         formatModels.add(new ExportFormatModel(type.getFormatId() + "",
                        Catalog.getCatalog().getString(type.getDescription())));
      }

      return formatModels;
   }

   private List<ExportFormatModel> getVSSaveFormats() {
      List<ExportFormatModel> formatModels = new ArrayList<>();
      String[] vsformats = new String[]{ FileFormatInfo.EXPORT_NAME_EXCEL,
            FileFormatInfo.EXPORT_NAME_POWERPOINT,
            FileFormatInfo.EXPORT_NAME_PDF,
            FileFormatInfo.EXPORT_NAME_HTML,
            FileFormatInfo.EXPORT_NAME_PNG,
            FileFormatInfo.EXPORT_NAME_CSV};
      String[] vsformatTypes = new String[]{ FileFormatInfo.EXPORT_TYPE_EXCEL + "",
            FileFormatInfo.EXPORT_TYPE_POWERPOINT + "",
            FileFormatInfo.EXPORT_TYPE_PDF + "",
            FileFormatInfo.EXPORT_TYPE_HTML + "",
            FileFormatInfo.EXPORT_TYPE_PNG + "",
            FileFormatInfo.EXPORT_TYPE_CSV + ""};
      String settingFormats = SreeEnv.getProperty("vsexport.menu.options");
      List<String> formatsList = Arrays.asList(Tool.split(settingFormats, ','));

      for(int i = 0; i < vsformats.length; i++) {
         String vsformat = vsformats[i];

         if(formatsList.contains(vsformat)) {
            if(FileFormatInfo.EXPORT_NAME_PNG.equals(vsformat)) {
               vsformat = FileFormatInfo.EXPORT_NAME_PNG;
            }

            formatModels.add(new ExportFormatModel(vsformatTypes[i],
                  Catalog.getCatalog().getString(vsformat)));
         }
      }

      return formatModels;
   }

   /**
    * Check if a format option should be available.
    */
   private boolean isAvailable(String format, Principal principal)
      throws Exception
   {
      String globalSetting = SreeEnv.getProperty("export.menu.options");
      List<String> formatsList = Arrays.asList(Tool.split(globalSetting, ','));
      Set<String> globalFormats = new HashSet<>(formatsList);

      if(!globalFormats.isEmpty() && !globalFormats.contains(format)) {
         return false;
      }

      return analyticRepository.checkPermission(
         principal, ResourceType.REPORT_EXPORT, format, ResourceAction.READ);
   }

   private boolean getBooleanProperty(String property) {
      return SreeEnv.getBooleanProperty(property, "true", "CHECKED");
   }

   private boolean checkPermission(TimeRange range, Principal user) {
      return securityProvider.checkPermission(
         user, ResourceType.SCHEDULE_TIME_RANGE, range.getName(), ResourceAction.ACCESS);
   }

   private IdentityID getIdentityId(String name, Principal principal) {
      if(name == null) {
         return null;
      }

      return new IdentityID(name, OrganizationManager.getInstance().getInstance().getCurrentOrgID(principal));
   }

   private final AnalyticRepository analyticRepository;
   private final ScheduleManager scheduleManager;
   private final ScheduleService scheduleService;
   private final ScheduleConditionService scheduleConditionService;
   private final SecurityProvider securityProvider;
   private final ScheduleTaskFolderService scheduleTaskFolderService;

   private static final Logger LOG =
      LoggerFactory.getLogger(ScheduleTaskService.class);
}
