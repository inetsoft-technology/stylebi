/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.web.admin.ai.schedule;

import inetsoft.report.io.csv.CSVConfig;
import inetsoft.sree.*;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.schedule.*;
import inetsoft.sree.security.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.util.Identity;
import inetsoft.uql.viewsheet.FileFormatInfo;
import inetsoft.util.Tool;
import inetsoft.util.dep.AbstractSheetAsset;
import inetsoft.util.dep.XAsset;
import inetsoft.util.dep.XAssetUtil;
import inetsoft.web.admin.schedule.*;
import inetsoft.web.admin.schedule.model.AddParameterDialogModel;
import inetsoft.web.api.schedule.*;
// Single-type imports: these simple names exist in BOTH inetsoft.web.api.schedule (the wildcard
// above) and inetsoft.sree.schedule (this file's own internal-type package, always referenced
// fully-qualified below as inetsoft.sree.schedule.X) -- a single-type import always wins over an
// on-demand import, so every bare use of one of these names below unambiguously means the API
// DTO, matching this file's own convention (bare = API DTO, inetsoft.sree.schedule.X = internal).
import inetsoft.web.api.schedule.BatchAction;
import inetsoft.web.api.schedule.CompletionCondition;
import inetsoft.web.api.schedule.IndividualAssetBackupAction;
import inetsoft.web.api.schedule.ScheduleAction;
import inetsoft.web.api.schedule.ScheduleAlert;
import inetsoft.web.api.schedule.ScheduleCondition;
import inetsoft.web.api.schedule.ScheduleTask;
import inetsoft.web.api.schedule.ServerPathInfo;
import inetsoft.web.api.schedule.TimeCondition;
import inetsoft.web.api.schedule.ViewsheetAction;
import inetsoft.web.composer.model.condition.ConditionValueModel;
import inetsoft.web.composer.model.vs.DynamicValueModel;
import inetsoft.web.security.auth.MissingResourceException;
import inetsoft.web.security.auth.ResourceExistsException;
import inetsoft.web.security.auth.UnauthorizedAccessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * {@code AdminScheduleGateway} implements, directly against community-only types, exactly the
 * schedule-task operations admin-chat's schedule-task area (see {@link AdminScheduleController})
 * needs: list/get a task, its conditions and actions, create and delete a task, and the two
 * inverse-permission preflights {@link ScheduleChangePlanService} uses.
 *
 * <p>This is a deliberate, new, community-native replacement for the enterprise Public API's
 * {@code inetsoft.enterprise.web.api.schedule.ScheduleApiService}, NOT a relocation of it --
 * {@code ScheduleApiService} and its REST controller stay in the enterprise module because they
 * carry genuine enterprise-only dependencies this gateway does not need: the {@code @SwitchOrg}/
 * {@code @Audited} AOP (which re-initializes per-org state via {@code
 * SecurityApiService.switchOrganization} when a site admin targets an organization other than
 * their own current one), and status as the licensed Public REST API surface for schedule
 * automation (run/stop, data cycles, batch actions -- none of which this gateway implements).
 * Admin-chat never passes a non-default {@code organizationid} (see {@link
 * AdminScheduleController#listTasks}'s own comment), so the multi-tenant org-switching machinery
 * has nothing to do here in the first place.
 *
 * <p>Every method below is modeled line-by-line on the corresponding {@code ScheduleApiService}
 * method (list/get/create/delete plus the permission and org-boundary checks and the condition/
 * action converters), with the {@code @SwitchOrg}/{@code @Audited}/{@code @OrganizationID}/{@code
 * @AuditObjectName} annotations and the {@code OrganizationAccess.check} calls they gated removed
 * (both are enterprise-only and a no-op for a caller that never passes a non-null organization id),
 * and with condition/action support restricted to nothing beyond what {@code ScheduleApiService}
 * itself supports (still every type it recognizes, since an existing task predating this area may
 * carry any of them, even though {@link ScheduleChangePlanService} refuses to let admin-chat
 * *create* anything but a time condition and a viewsheet action).
 */
@Service
public class AdminScheduleGateway {
   @Autowired
   public AdminScheduleGateway(AnalyticRepository repository, ScheduleManager scheduleManager,
                               ScheduleService scheduleService,
                               ScheduleConditionService scheduleConditionService,
                               ScheduleTaskService scheduleTaskService)
   {
      this.repository = repository;
      this.scheduleManager = scheduleManager;
      this.scheduleService = scheduleService;
      this.scheduleConditionService = scheduleConditionService;
      this.scheduleTaskService = scheduleTaskService;
   }

   /**
    * Gets the list of scheduled tasks. Mirrors {@code ScheduleApiService#getScheduleTasks}.
    */
   public ScheduleTaskList getScheduleTasks(String organizationid, Principal user) throws Exception {
      checkPermission(user);
      ScheduleTaskList list = new ScheduleTaskList();
      Arrays.stream(repository.getScheduleTasks(user))
         .map(scheduleManager::getScheduleTask)
         .filter(Objects::nonNull)
         .filter(s -> organizationid == null || s.getOwner().getOrgID().equals(organizationid))
         .map(this::convertTask)
         .forEach(list.getTasks()::add);
      return list;
   }

   /**
    * Adds a schedule task. Mirrors {@code ScheduleApiService#addScheduleTask}.
    */
   public void addScheduleTask(ScheduleTaskMetaData taskMetaData, boolean enabled,
                               boolean deleteIfNotScheduledToRun, long startDate, long endDate,
                               String description, String locale, ScheduleTask.ExecuteAsID executeAsID,
                               List<ScheduleCondition> conditions, List<ScheduleAction> actions,
                               String organizationid, String linkUri, Principal user)
      throws Exception
   {
      checkPermission(user);
      String taskId = taskMetaData.getTaskId();
      inetsoft.sree.schedule.ScheduleTask task = repository.getScheduleTask(taskId);

      if(task != null) {
         throw new ResourceExistsException(taskId);
      }

      IdentityID owner = IdentityID.getIdentityIDFromKey(taskMetaData.getTaskOwnerId());

      if(owner == null) {
         throw new MissingResourceException(taskMetaData.getTaskOwnerId());
      }

      String name = taskMetaData.getTaskName();
      checkOwnerPermission(user, owner, executeAsID);
      task = convertTask(new ScheduleTask(name, owner, enabled, deleteIfNotScheduledToRun, startDate, endDate, description, locale, executeAsID));

      conditions.stream()
         .map(this::convertCondition)
         .filter(Objects::nonNull)
         .forEach(task::addCondition);

      inetsoft.sree.schedule.ScheduleTask finalTask = task;

      for(ScheduleAction action : actions) {
         checkActionOrgBoundary(action, user);
      }

      actions.stream()
         .map(this::convertAction)
         .filter(Objects::nonNull)
         .forEach(action -> finalTask.addAction(action, linkUri));

      // Enforce schedule option permissions -- same sanitization applied by the UI.
      // A new task has no prior state, so pass an empty task as the "original".
      inetsoft.sree.schedule.ScheduleTask emptyTask = new inetsoft.sree.schedule.ScheduleTask();
      scheduleTaskService.sanitizeConditions(task, emptyTask, user);

      for(int i = 0; i < task.getActionCount(); i++) {
         scheduleTaskService.sanitizeAction(task.getAction(i), null, user);
      }

      scheduleManager.setScheduleTask(task.getTaskId(), task, user);
   }

   /**
    * Gets a schedule task. Mirrors {@code ScheduleApiService#getScheduleTask}.
    */
   public ScheduleTask getScheduleTask(String taskId, String organizationid, Principal user)
      throws Exception
   {
      return convertTask(getTask(taskId, user));
   }

   /**
    * Removes a schedule task. Mirrors {@code ScheduleApiService#removeScheduleTask}, including its
    * cross-org guard for a non-site-admin caller.
    */
   public void removeScheduleTask(String taskId, String organizationid, Principal user) throws Exception {
      checkPermission(user);

      if(!OrganizationManager.getInstance().isSiteAdmin(user)) {
         IdentityID taskOwner = ScheduleManager.getOwner(taskId);

         if(taskOwner != null && taskOwner.getOrgID() != null &&
            !taskOwner.getOrgID().equals(OrganizationManager.getInstance().getCurrentOrgID(user)))
         {
            throw new UnauthorizedAccessException();
         }
      }

      checkPermission(user, taskId, ResourceAction.DELETE);
      repository.removeScheduleTask(user, taskId);
   }

   /**
    * Gets the list of conditions defined for a task. Mirrors {@code
    * ScheduleApiService#getTaskConditions}.
    */
   public ScheduleConditionList getTaskConditions(String taskId, String organizationid, Principal user)
      throws Exception
   {
      inetsoft.sree.schedule.ScheduleTask task = getTask(taskId, user);
      ScheduleConditionList list = new ScheduleConditionList();
      task.getConditionStream()
         .map(this::convertCondition)
         .forEach(list.getConditions()::add);
      return list;
   }

   /**
    * Like {@link #getTaskConditions}, but never throws for a condition with no community API DTO
    * -- substitutes an {@link UnsupportedScheduleItem} placeholder instead of propagating. Mirrors
    * {@code ScheduleApiService#getTaskConditionsLenient}.
    */
   public List<Object> getTaskConditionsLenient(String taskId, String organizationid, Principal user)
      throws Exception
   {
      inetsoft.sree.schedule.ScheduleTask task = getTask(taskId, user);
      List<Object> list = new ArrayList<>();

      task.getConditionStream().forEach(c -> {
         try {
            list.add(convertCondition(c));
         }
         catch(IllegalArgumentException e) {
            list.add(new UnsupportedScheduleItem(c.getClass().getName()));
         }
      });

      return list;
   }

   /**
    * Gets the list of actions defined for a task. Mirrors {@code ScheduleApiService#getTaskActions}.
    */
   public ScheduleActionList getTaskActions(String taskId, String organizationid, Principal user)
      throws Exception
   {
      inetsoft.sree.schedule.ScheduleTask task = getTask(taskId, user);
      ScheduleActionList builder = new ScheduleActionList();
      task.getActionStream()
         .map(this::convertAction)
         .forEach(builder.getActions()::add);
      return builder;
   }

   /**
    * Like {@link #getTaskActions}, but never throws for an action with no community API DTO --
    * substitutes an {@link UnsupportedScheduleItem} placeholder instead of propagating. Mirrors
    * {@code ScheduleApiService#getTaskActionsLenient}.
    */
   public List<Object> getTaskActionsLenient(String taskId, String organizationid, Principal user)
      throws Exception
   {
      inetsoft.sree.schedule.ScheduleTask task = getTask(taskId, user);
      List<Object> list = new ArrayList<>();

      task.getActionStream().forEach(a -> {
         try {
            list.add(convertAction(a));
         }
         catch(IllegalArgumentException e) {
            list.add(new UnsupportedScheduleItem(a.getClass().getName()));
         }
      });

      return list;
   }

   /**
    * Reports whether {@code user} holds {@code DELETE} on {@code taskId}, without requiring the
    * task to actually exist and without throwing on a "no" answer. Mirrors {@code
    * ScheduleApiService#hasDeletePermission} -- delegates to the same {@code
    * repository.checkPermission} call {@link #removeScheduleTask} itself uses, so this can never
    * drift from the real gate.
    */
   public boolean hasDeletePermission(String taskId, Principal user) throws Exception {
      return repository.checkPermission(user, ResourceType.SCHEDULE_TASK, taskId, ResourceAction.DELETE);
   }

   /**
    * Reports whether {@code user} holds the rights over {@code owner} (and {@code executeAsID}, if
    * set) that {@link #addScheduleTask} would itself require, without throwing on a "no" answer.
    * Mirrors {@code ScheduleApiService#hasOwnerAdminPermission}.
    */
   public boolean hasOwnerAdminPermission(IdentityID owner, ScheduleTask.ExecuteAsID executeAsID,
                                          Principal user)
      throws Exception
   {
      try {
         checkOwnerPermission(user, owner, executeAsID);
         return true;
      }
      catch(UnauthorizedAccessException e) {
         return false;
      }
   }

   private void checkPermission(Principal user) throws Exception {
      if(!repository.checkPermission(user, ResourceType.SCHEDULER, "*", ResourceAction.ACCESS)) {
         throw new UnauthorizedAccessException();
      }
   }

   private void checkPermission(Principal user, String task, ResourceAction action) throws Exception
   {
      if(!repository.checkPermission(user, ResourceType.SCHEDULE_TASK, task, action)) {
         throw new UnauthorizedAccessException();
      }
   }

   /**
    * Verifies that the caller has admin rights over the identity a task is being owned by and/or
    * executed as, so that a task cannot be created to run under another identity's privileges
    * without authorization. A caller is always allowed to own and/or execute as their own identity.
    * Mirrors {@code ScheduleApiService}'s private {@code checkOwnerPermission(Principal,
    * IdentityID, ScheduleTask.ExecuteAsID)}.
    */
   private void checkOwnerPermission(Principal user, IdentityID owner,
                                     ScheduleTask.ExecuteAsID executeAsID)
      throws Exception
   {
      IdentityID callerID = IdentityID.getIdentityIDFromKey(user.getName());

      if(!Tool.equals(callerID, owner) &&
         !repository.checkPermission(user, ResourceType.SECURITY_USER, owner,
                                     ResourceAction.ADMIN))
      {
         throw new UnauthorizedAccessException();
      }

      if(executeAsID != null) {
         boolean isSelf = executeAsID.getType() == ScheduleTask.ExecuteAsID.Type.USER &&
            Tool.equals(callerID, executeAsID.getIdentityID());

         if(!isSelf) {
            ResourceType type = executeAsID.getType() == ScheduleTask.ExecuteAsID.Type.USER ?
               ResourceType.SECURITY_USER : ResourceType.SECURITY_GROUP;

            if(!repository.checkPermission(user, type, executeAsID.getIdentityID(),
                                           ResourceAction.ADMIN))
            {
               throw new UnauthorizedAccessException();
            }
         }
      }
   }

   /**
    * Rejects a client-supplied action whose asset id(s) embed an organization other than the
    * caller's, so an org admin cannot reference another organization's asset by id when creating a
    * schedule task action. Mirrors {@code ScheduleApiService#checkActionOrgBoundary}.
    */
   private void checkActionOrgBoundary(ScheduleAction action, Principal user) throws Exception {
      if(action instanceof ViewsheetAction viewsheetAction) {
         checkAssetOrgBoundary(viewsheetAction.getViewsheet(), user);
      }
      else if(action instanceof BatchAction batchAction) {
         checkAssetOrgBoundary(batchAction.getQueryEntry(), user);
      }
      else if(action instanceof IndividualAssetBackupAction backupAction &&
              backupAction.getAssetIdentifiers() != null)
      {
         for(String identifier : backupAction.getAssetIdentifiers()) {
            // Only sheet-type assets (worksheet/viewsheet) expose a reliable per-org identity --
            // see ScheduleApiService#checkActionOrgBoundary's own comment for why the org must be
            // read from the raw identifier rather than XAsset.parseIdentifier()'s AssetEntry.
            if(XAssetUtil.createXAsset(identifier) instanceof AbstractSheetAsset) {
               int idx = identifier.indexOf('^');

               if(idx >= 0) {
                  checkAssetOrgBoundary(identifier.substring(idx + 1), user);
               }
            }
         }
      }
   }

   private void checkAssetOrgBoundary(String assetId, Principal user) throws Exception {
      if(assetId == null || !(user instanceof XPrincipal principal) ||
         OrganizationManager.getInstance().isSiteAdmin(user))
      {
         return;
      }

      AssetEntry entry = AssetEntry.createAssetEntry(assetId);

      if(entry != null && entry.getOrgID() != null &&
         !Tool.equals(entry.getOrgID(), principal.getCurrentOrgId()))
      {
         throw new UnauthorizedAccessException();
      }
   }

   private inetsoft.sree.schedule.ScheduleTask convertTask(ScheduleTask task) {
      ScheduleTask.ExecuteAsID executeAsID = task.getExecuteAsID();

      inetsoft.sree.schedule.ScheduleTask output = new inetsoft.sree.schedule.ScheduleTask();
      output.setName(task.getName());
      output.setOwner(task.getOwner());
      output.setDescription(task.getDescription());
      output.setLocale(task.getLocale());

      if(task.getEndDate() >= 0) {
         output.setEndDate(new Date(task.getEndDate()));
      }

      if(task.getStartDate() >= 0) {
         output.setStartDate(new Date(task.getStartDate()));
      }

      output.setEnabled(task.isEnabled());
      output.setDeleteIfNoMoreRun(task.isDeleteIfNotScheduledToRun());

      if(executeAsID != null) {
         Identity id = SUtil.getIdentity(executeAsID.getIdentityID(), executeAsID.getType().ordinal());
         output.setIdentity(id);
      }

      return output;
   }

   private ScheduleTask convertTask(inetsoft.sree.schedule.ScheduleTask task) {
      ScheduleTask result = new ScheduleTask(task);
      result.setDescription(task.getDescription());
      result.setLocale(task.getLocale());
      result.setDeleteIfNotScheduledToRun(task.isDeleteIfNoMoreRun());
      result.setStatus(getStatus(task));

      if(task.getStartDate() != null) {
         result.setStartDate(task.getStartDate().getTime());
      }

      if(task.getEndDate() != null) {
         result.setEndDate(task.getEndDate().getTime());
      }

      if(task.getIdentity() != null) {
         ScheduleTask.ExecuteAsID.Type type =
            ScheduleTask.ExecuteAsID.Type.values()[task.getIdentity().getType()];
         result.setExecuteAsID(new ScheduleTask.ExecuteAsID(type, task.getIdentity().getIdentityID()));
      }
      else {
         ScheduleTask.ExecuteAsID executeAsID =
            new ScheduleTask.ExecuteAsID(ScheduleTask.ExecuteAsID.Type.USER, task.getOwner());
         result.setExecuteAsID(executeAsID);
      }

      return result;
   }

   private inetsoft.sree.schedule.ScheduleCondition convertCondition(ScheduleCondition condition) {
      if(condition instanceof TimeCondition) {
         return convertCondition((TimeCondition) condition);
      }
      else if(condition instanceof CompletionCondition) {
         return convertCondition((CompletionCondition) condition);
      }
      else {
         throw new IllegalArgumentException("Unsupported condition type: " + condition);
      }
   }

   private inetsoft.sree.schedule.TimeCondition convertCondition(TimeCondition condition) {
      inetsoft.sree.schedule.TimeCondition output = new inetsoft.sree.schedule.TimeCondition();
      final TimeCondition.Type type = condition.getType();
      output.setType(type.value());

      if(condition.getTimeRange() != null) {
         TimeRange range = TimeRange.getTimeRanges().stream()
            .filter(r -> r.getName().equals(condition.getTimeRange()))
            .findFirst()
            .orElse(null);
         output.setTimeRange(range);
      }

      if(condition.getTimeZone() != null) {
         output.setTimeZone(TimeZone.getTimeZone(condition.getTimeZone()));
      }

      output.setHour(condition.getHour());
      output.setMinute(condition.getMinute());
      output.setSecond(condition.getSecond());

      switch(type) {
      case AT:
         final OffsetDateTime date = condition.getDate();
         Objects.requireNonNull(date, "ISO 8601 Date is required for \"AT\" type");
         output.setDate(Date.from(date.toInstant()));
         break;
      case EVERY_DAY:
         output.setInterval(condition.getInterval());
         output.setWeekdayOnly(condition.isWeekdayOnly());
         break;
      case EVERY_WEEK:
      case DAY_OF_WEEK:
         output.setInterval(condition.getInterval());
         output.setDaysOfWeek(condition.getDaysOfWeek());
         break;
      case EVERY_MONTH:
         output.setDayOfMonth(condition.getDayOfMonth());
         output.setWeekOfMonth(condition.getWeekOfMonth());
         output.setDayOfWeek(condition.getDayOfWeek());
         output.setMonthsOfYear(condition.getMonthsOfYear());
         break;
      case WEEK_OF_MONTH:
         output.setWeekOfMonth(condition.getWeekOfMonth());
         output.setDayOfWeek(condition.getDayOfWeek());
         output.setMonthsOfYear(condition.getMonthsOfYear());
         break;
      case DAY_OF_MONTH:
         output.setDayOfMonth(condition.getDayOfMonth());
         output.setMonthsOfYear(condition.getMonthsOfYear());
         break;
      case EVERY_HOUR:
         output.setHourEnd(condition.getHourEnd());
         output.setMinuteEnd(condition.getMinuteEnd());
         output.setSecondEnd(condition.getSecondEnd());
         output.setHourlyInterval(condition.getHourlyInterval());
      default:
         output.setDayOfMonth(condition.getDayOfMonth());
         output.setDayOfWeek(condition.getDayOfWeek());
         output.setWeekOfMonth(condition.getWeekOfMonth());
         output.setInterval(condition.getInterval());
         output.setDaysOfWeek(condition.getDaysOfWeek());
         output.setMonthsOfYear(condition.getMonthsOfYear());
         output.setWeekdayOnly(condition.isWeekdayOnly());
         break;
      }

      return output;
   }

   private inetsoft.sree.schedule.CompletionCondition convertCondition(CompletionCondition condition) {
      inetsoft.sree.schedule.CompletionCondition output =
         new inetsoft.sree.schedule.CompletionCondition();
      output.setTaskName(ScheduleManager.getTaskId(condition.getOwner(), condition.getTaskName(), null));
      return output;
   }

   private ScheduleCondition convertCondition(inetsoft.sree.schedule.ScheduleCondition condition) {
      if(condition instanceof inetsoft.sree.schedule.TimeCondition) {
         return new TimeCondition((inetsoft.sree.schedule.TimeCondition) condition);
      }
      else if(condition instanceof inetsoft.sree.schedule.CompletionCondition) {
         return new CompletionCondition((inetsoft.sree.schedule.CompletionCondition) condition);
      }
      else {
         throw new IllegalArgumentException("Unsupported condition type: " + condition);
      }
   }

   private inetsoft.sree.schedule.ScheduleAction convertAction(ScheduleAction action) {
      if(action instanceof ViewsheetAction) {
         return convertAction((ViewsheetAction) action);
      }
      else if(action instanceof BatchAction) {
         return convertAction((BatchAction) action);
      }
      else if(action instanceof IndividualAssetBackupAction) {
         return convertAction((IndividualAssetBackupAction) action);
      }
      else {
         throw new IllegalArgumentException("Unsupported action type: " + action);
      }
   }

   private inetsoft.sree.schedule.ViewsheetAction convertAction(ViewsheetAction action) {
      inetsoft.sree.schedule.ViewsheetAction output = new inetsoft.sree.schedule.ViewsheetAction();
      output.setViewsheet(action.getViewsheet());
      output.setBookmarks(new String[]{action.getBookmarkName()});
      output.setBookmarkUsers(new IdentityID[]{action.getBookmarkUser()});
      // bookmarkTypes must stay parallel to bookmarks/bookmarkUsers -- both branches always set
      // it, defaulting to PRIVATE when the caller didn't specify one, so the internal
      // ViewsheetAction's getBookmarkTypes() is never null (see the read-side constructor, which
      // indexes into it unconditionally).
      output.setBookmarkTypes(new int[]{
         action.getBookmarkType() != null
            ? action.getBookmarkType().code() : ViewsheetAction.BookmarkType.PRIVATE.code()
      });

      if(action.getBookmarkNames() != null) {
         output.setBookmarks(action.getBookmarkNames().toArray(new String[0]));
         output.setBookmarkUsers(action.getBookmarkUsers().toArray(new IdentityID[0]));
         output.setBookmarkTypes(action.getBookmarkTypes() != null
            ? action.getBookmarkTypes().stream()
                 .mapToInt(ViewsheetAction.BookmarkType::code)
                 .toArray()
            : action.getBookmarkNames().stream()
                 .mapToInt(n -> ViewsheetAction.BookmarkType.PRIVATE.code())
                 .toArray());
      }

      populateSaveToServerFilePaths(action, output);

      if(!action.getEmails().isEmpty()) {
         String recipients = String.join(",", action.getEmails());
         output.setEmails(recipients);
      }

      if(!action.getNotifies().isEmpty()) {
         String recipients = String.join(",", action.getNotifies());
         output.setNotifications(recipients);

         if(action.isNotifyIfFailed() != null) {
            output.setNotifyError(action.isNotifyIfFailed());
         }

         if(action.isNotifyLink() != null) {
            output.setLink(action.isNotifyLink());
         }
      }

      String format = action.getFormat() != null ?
         FileFormatInfo.EXPORT_ALL_NAMES[action.getFormat().getTypeValue()] : null;

      RepletRequest request = new RepletRequest();

      for(Map.Entry<String, Object> e : action.getParameters().entrySet()) {
         if(e.getValue() instanceof Map) {
            request.setParameter(e.getKey(), convertParameter((Map) e.getValue()));
         }
         else {
            request.setParameter(e.getKey(), e.getValue());
         }
      }

      output.setViewsheetRequest(request);

      output.setFrom(action.getSender());
      output.setFileFormat(format);
      output.setSubject(action.getSubject());
      output.setMessage(action.getMessage());
      output.setMessageHtml(action.isHtmlMessage());
      setEmailProperties(action, output);

      populateScheduleAlerts(action, output);
      output.setSaveToServerMatch(action.isSaveToServerMatch());
      output.setSaveToServerExpandSelections(action.isSaveToServerExpandSelections());
      output.setSaveToServerOnlyDataComponents(action.isSaveToServerOnlyDataComponents());
      output.setSaveExportAllTabbedTables(action.isSaveExportAllTabbedTables());

      if(action.getSaveToServerCsvConfig() != null) {
         CSVConfig config = new CSVConfig();
         inetsoft.web.api.schedule.CSVConfig configModel = action.getSaveToServerCsvConfig();
         config.setDelimiter(configModel.getDelimiter());
         config.setQuote(configModel.getQuote());
         config.setKeepHeader(configModel.isKeepHeader());
         config.setTabDelimited(configModel.isTabDelimited());
         config.setExportAssemblies(configModel.getSelectedAssemblies());
         output.setSaveCSVConfig(config);
      }

      output.setViewsheetRequest(request);

      return output;
   }

   private Object convertParameter(Map map) {
      if(!map.containsKey("value")) {
         return map;
      }

      if(!map.containsKey("dataType")) {
         return map.get("value");
      }

      String value = String.valueOf(map.get("value"));
      String type = (String) map.get("dataType");
      String valueType = map.get("type") != null && map.get("type").equals(ConditionValueModel.EXPRESSION) ?
         ConditionValueModel.EXPRESSION : ConditionValueModel.VALUE;
      boolean array = map.containsKey("array") && (Boolean) map.get("array");
      DynamicParameterValue parameterValue = new DynamicParameterValue(value, valueType, type);

      if(array) {
         return scheduleConditionService.getParamValueAsArray(type, value);
      }
      else if(valueType.equals(ConditionValueModel.VALUE)) {
         parameterValue.setValue(scheduleConditionService.getParamValueAsType(type, new DynamicValueModel(value)));
      }

      return parameterValue;
   }

   private inetsoft.sree.schedule.BatchAction convertAction(BatchAction action) {
      inetsoft.sree.schedule.BatchAction output = new inetsoft.sree.schedule.BatchAction();
      output.setTaskId(ScheduleManager.getTaskId(action.getOwner(), action.getTaskName(), null));
      output.setQueryParameters(action.getQueryParameters());
      output.setQueryEntry(AssetEntry.createAssetEntry(action.getQueryEntry()));
      output.setEmbeddedParameters(convertEmbeddedParameters(action.getEmbeddedParameters()));

      return output;
   }

   private List<Map<String, Object>> convertEmbeddedParameters(List<Map<String, Object>> parameters) {
      ArrayList<Map<String, Object>> embeddedParameters = new ArrayList<>();

      for(Map<String, Object> row : parameters) {
         HashMap<String, Object> convertedRow = new HashMap<>();

         for(Map.Entry e : row.entrySet()) {
            if(e.getValue() instanceof Map) {
               convertedRow.put(e.getKey().toString(), convertParameter((Map) e.getValue()));
            }
            else {
               convertedRow.put(e.getKey().toString(), e.getValue());
            }
         }

         embeddedParameters.add(convertedRow);
      }

      return embeddedParameters;
   }

   private inetsoft.sree.schedule.IndividualAssetBackupAction convertAction(IndividualAssetBackupAction action) {
      inetsoft.sree.schedule.IndividualAssetBackupAction output = new inetsoft.sree.schedule.IndividualAssetBackupAction();
      ServerPathInfo pathInfo = action.getPathInfo();

      if(pathInfo != null) {
         inetsoft.sree.schedule.ServerPathInfo info = new inetsoft.sree.schedule.ServerPathInfo();
         info.setPath(pathInfo.getPath());

         if(pathInfo.isUseCredential()) {
            info.setUseCredential(true);
            info.setSecretId(pathInfo.getSecretId());
         }
         else {
            info.setUsername(pathInfo.getUserName());
            info.setPassword(pathInfo.getPassword());
         }

         output.setServerPaths(info);
      }

      List<String> identifiers = action.getAssetIdentifiers();

      if(identifiers != null) {
         List<XAsset> assets = identifiers.stream()
            .map(identifier -> XAssetUtil.createXAsset(identifier))
            .collect(Collectors.toList());
         output.setAssets(assets);
      }

      output.setEncoding(action.isEncoding());
      return output;
   }

   private void populateSaveToServerFilePaths(ViewsheetAction action, inetsoft.sree.schedule.ViewsheetAction output) {
      for(ViewsheetAction.SaveToServerFilePath filePath : action.getSaveToServerFilePaths()) {
         if(filePath.getPath() != null && filePath.getFormat() != null) {
            inetsoft.sree.schedule.ServerPathInfo info;

            if(filePath.isUseCredential()) {
               info = new inetsoft.sree.schedule.ServerPathInfo(filePath.getPath(), null, null);
               info.setUseCredential(true);
               info.setSecretId(filePath.getSecretId());
            }
            else {
               info = new inetsoft.sree.schedule.ServerPathInfo(filePath.getPath(),
                                                                filePath.getUsername(), filePath.getPassword());
            }

            output.setFilePath(filePath.getFormat().getTypeValue(), info);

            if(filePath.getFormat() == ViewsheetAction.Format.CSV && output.getSaveCSVConfig() == null) {
               output.setSaveCSVConfig(new CSVConfig());
            }
         }
      }
   }

   private void populateScheduleAlerts(ViewsheetAction action,
                                       inetsoft.sree.schedule.ViewsheetAction output)
   {
      List<ScheduleAlert> alerts = action.getAlerts();
      inetsoft.sree.schedule.ScheduleAlert[] outputAlerts =
         new inetsoft.sree.schedule.ScheduleAlert[alerts.size()];

      for(int i = 0; i < alerts.size(); i ++) {
         ScheduleAlert alert = alerts.get(i);

         if(alert.getElementId() != null && alert.getHighlightName() != null) {
            inetsoft.sree.schedule.ScheduleAlert newAlert = new inetsoft.sree.schedule.ScheduleAlert();
            newAlert.setElementId(alert.getElementId());
            newAlert.setHighlightName(alert.getHighlightName());
            outputAlerts[i] = newAlert;
         }
      }

      output.setAlerts(outputAlerts);
   }

   private void setEmailProperties(ViewsheetAction action,
                                   inetsoft.sree.schedule.ViewsheetAction output)
   {
      if(action.getCCAddresses() != null) {
         output.setCCAddresses(action.getCCAddresses());
      }

      if(action.getBCCAddresses() != null) {
         output.setBCCAddresses(action.getBCCAddresses());
      }

      if(action.isEmailMatch() != null) {
         output.setMatchLayout(action.isEmailMatch());
      }

      if(action.isEmailExpandSelections() != null) {
         output.setExpandSelections(action.isEmailExpandSelections());
      }

      if(action.isEmailOnlyDataComponents() != null) {
         output.setOnlyDataComponents(action.isEmailOnlyDataComponents());
      }

      if(action.isExportAllTabbedTables() != null) {
         output.setExportAllTabbedTables(action.isExportAllTabbedTables());
      }

      if(action.isEmailZip() != null) {
         output.setCompressFile(action.isEmailZip());
      }

      if(action.getAttachmentName() != null) {
         output.setAttachmentName(action.getAttachmentName());
      }

      if(action.isEmailLink() != null) {
         output.setDeliverLink(action.isEmailLink());
      }

      if(action.getEmailCSVConfig() != null) {
         CSVConfig config = new CSVConfig();
         inetsoft.web.api.schedule.CSVConfig configModel = action.getEmailCSVConfig();
         config.setDelimiter(configModel.getDelimiter());
         config.setQuote(configModel.getQuote());
         config.setKeepHeader(configModel.isKeepHeader());
         config.setTabDelimited(configModel.isTabDelimited());
         config.setExportAssemblies(configModel.getSelectedAssemblies());
         output.setEmailCSVConfig(config);
      }
   }

   private ScheduleAction convertAction(inetsoft.sree.schedule.ScheduleAction action) {
      if(action instanceof inetsoft.sree.schedule.ViewsheetAction) {
         return new ViewsheetAction((inetsoft.sree.schedule.ViewsheetAction) action);
      }
      else if(action instanceof inetsoft.sree.schedule.BatchAction) {
         BatchAction batchAction = new BatchAction((inetsoft.sree.schedule.BatchAction) action);
         updateBatchActionEmbeddedParameters(batchAction);
         return batchAction;
      }
      else if(action instanceof inetsoft.sree.schedule.IndividualAssetBackupAction) {
         return new IndividualAssetBackupAction((inetsoft.sree.schedule.IndividualAssetBackupAction) action);
      }
      else {
         throw new IllegalArgumentException("Unsupported action type: " + action);
      }
   }

   private void updateBatchActionEmbeddedParameters(BatchAction batchAction) {
      List<Map<String, Object>> parameters = batchAction.getEmbeddedParameters();
      List<Map<String, Object>> result = new ArrayList<>();

      for(Map<String, Object> parameter : parameters) {
         RepletRequest repletRequest = new RepletRequest();

         for(String paramName : parameter.keySet()) {
            repletRequest.setParameter(paramName, parameter.get(paramName));
         }

         AddParameterDialogModel[] paraModels = scheduleConditionService.getParameterModelList(repletRequest)
            .toArray(new AddParameterDialogModel[0]);

         for(AddParameterDialogModel paraModel : paraModels) {
            Map<String, Object> paramProperties = new HashMap<>();
            DynamicValueModel value = paraModel.value();

            if(value == null) {
               continue;
            }

            if(!Tool.isEmptyString(value.getType())) {
               paramProperties.put("type", value.getType());
            }

            if(!Tool.isEmptyString(value.getDataType())) {
               paramProperties.put("dataType", value.getDataType());
            }

            if(paraModel.array()) {
               paramProperties.put("array", paraModel.array());
            }

            paramProperties.put("value", value.getValue());
            Map<String, Object> param = new HashMap<>();
            param.put(paraModel.name(), paramProperties);
            result.add(param);
         }
      }

      batchAction.setEmbeddedParameters(result);
   }

   private inetsoft.sree.schedule.ScheduleTask getTask(String taskId, Principal user) throws Exception
   {
      checkPermission(user);
      inetsoft.sree.schedule.ScheduleTask task = repository.getScheduleTask(taskId);

      if(task == null) {
         throw new MissingResourceException(taskId);
      }

      checkPermission(user, taskId, ResourceAction.READ);
      return task;
   }

   private ScheduleTaskStatus getStatus(inetsoft.sree.schedule.ScheduleTask task) {
      TaskActivity activity = scheduleService.getActivity(task.getTaskId());
      ScheduleTaskStatus status = new ScheduleTaskStatus(activity);
      status.setTask(task.getTaskId());
      return status;
   }

   private final AnalyticRepository repository;
   private final ScheduleManager scheduleManager;
   private final ScheduleService scheduleService;
   private final ScheduleConditionService scheduleConditionService;
   private final ScheduleTaskService scheduleTaskService;
}
