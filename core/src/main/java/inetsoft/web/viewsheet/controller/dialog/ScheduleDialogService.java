/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.web.viewsheet.controller.dialog;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.cluster.*;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.io.csv.CSVConfig;
import inetsoft.report.io.viewsheet.excel.CSVUtil;
import inetsoft.sree.RepletRequest;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.schedule.*;
import inetsoft.sree.security.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.util.IdentityNode;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import inetsoft.web.admin.schedule.ScheduleService;
import inetsoft.web.admin.schedule.model.TimeZoneModel;
import inetsoft.web.admin.schedule.model.UsersModel;
import inetsoft.web.composer.model.TreeNodeModel;
import inetsoft.web.factory.RemainingPath;
import inetsoft.web.portal.model.CSVConfigModel;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.model.dialog.EmailAddrDialogModel;
import inetsoft.web.viewsheet.model.dialog.schedule.*;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.VSBookmarkService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;
import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@ClusterProxy
public class ScheduleDialogService {

   public ScheduleDialogService(ViewsheetService viewsheetService,
                                SecurityProvider securityProvider,
                                ScheduleService scheduleService,
                                VSBookmarkService vsBookmarkService)
   {
      this.viewsheetService = viewsheetService;
      this.securityProvider = securityProvider;
      this.scheduleService = scheduleService;
      this.vsBookmarkService = vsBookmarkService;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public SimpleScheduleDialogModel getSimpleScheduleDialogModel(@ClusterProxyKey String runtimeId, boolean useCurrent,
                                                                 String bookmarkName, Principal principal) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      VSBookmarkInfo bookmarkInfo = new VSBookmarkInfo();
      IdentityID pId = IdentityID.getIdentityIDFromKey(principal.getName());

      if(!useCurrent) {
         MessageCommand msgCmd = vsBookmarkService.addBookmarkToViewSheet(rvs, bookmarkName,
                                                                          VSBookmarkInfo.PRIVATE, true, true, principal);

         if(msgCmd.getType() != MessageCommand.Type.OK) {
            throw new RuntimeException(msgCmd.getMessage());
         }
      }

      List<VSBookmarkInfo> allBookmarks = rvs.getBookmarks();

      if(useCurrent) {
         if(rvs.getOpenedBookmark() != null) {
            bookmarkName = rvs.getOpenedBookmark().getName();
         }
         else {
            bookmarkName = rvs.getDefaultBookmark() == null ?
               VSBookmark.HOME_BOOKMARK : rvs.getDefaultBookmark().getName();
         }
      }

      for(VSBookmarkInfo bookmark : allBookmarks) {
         if(bookmark.getName().equals(bookmarkName)) {
            bookmarkInfo = bookmark;
            break;
         }
      }

      ScheduleManager manager = ScheduleManager.getScheduleManager();
      AssetEntry entry = rvs.getEntry();
      String taskName = !Tool.isEmptyString(entry.getAlias()) ? entry.getAlias() : entry.getName();
      taskName = principal != null ? principal.getName() + ":" + taskName : taskName;

      if(manager.getScheduleTask(taskName) != null) {
         String oname = taskName;

         for(int i = 1; i < Integer.MAX_VALUE; i++) {
            if(manager.getScheduleTask(oname + "_" + i) == null) {
               taskName = oname + "_" + i;
               break;
            }
         }
      }

      String timeProp = SreeEnv.getProperty("format.time");

      if(timeProp == null || "".equals(timeProp)) {
         timeProp = Tool.DEFAULT_TIME_PATTERN;
      }

      timeProp = timeProp.trim();

      int format = 0;
      String[] exportTypes = FileFormatInfo.EXPORT_ALL_NAMES;
      List<String> availableTypes = Arrays.asList(VSUtil.getExportOptions());

      for(int i = 0; i < exportTypes.length; i++) {
         if(availableTypes.contains(exportTypes[i])) {
            format = i;
            break;
         }
      }

      EmailInfoModel emailInfoModel = EmailInfoModel.builder()
         .fromAddress(SreeEnv.getProperty("mail.from.address"))
         .attachmentName(entry.getName())
         .subject(getEntryMessage(entry))
         .message(getEntryMessage(entry))
         .formatType(format)
         .csvConfigModel(CSVConfigModel.builder().from(new CSVConfig()).build())
         .build();

      boolean hasPrintLayout = rvs.getViewsheet().getLayoutInfo() != null &&
         rvs.getViewsheet().getLayoutInfo().getPrintLayout() != null;

      ViewsheetActionModel viewsheetActionModel = ViewsheetActionModel.builder()
         .viewsheet(entry.toIdentifier())
         .bookmarkName(bookmarkInfo.getName())
         .bookmarkUser(bookmarkInfo.getOwner())
         .bookmarkType(bookmarkInfo.getType())
         .hasPrintLayout(hasPrintLayout)
         .emailInfoModel(emailInfoModel)
         .build();

      List<TreeNodeModel> nodes = new ArrayList<>();
      TreeNodeModel userTree = TreeNodeModel.builder()
         .label(Catalog.getCatalog().getString("Users"))
         .data("")
         .type(IdentityNode.USERS + "")
         .leaf(false)
         .build();

      nodes.add(userTree);

      if(!(principal instanceof SRPrincipal) || !((SRPrincipal) principal).isSelfOrganization()) {
         TreeNodeModel groupTree = TreeNodeModel.builder()
            .label(Catalog.getCatalog().getString("Groups"))
            .data("")
            .type(IdentityNode.GROUPS + "")
            .leaf(false)
            .build();
         nodes.add(groupTree);
      }

      TreeNodeModel rootTree = TreeNodeModel.builder()
         .label(Catalog.getCatalog().getString("Root"))
         .data("")
         .type(IdentityNode.ROOT + "")
         .children(nodes)
         .leaf(false)
         .build();

      EmailAddrDialogModel emailAddrDialogModel = EmailAddrDialogModel.builder()
         .rootTree(rootTree)
         .build();

      boolean userDialogEnabled = principal == null || !"anonymous".equals(pId.name);

      Catalog catalog = Catalog.getCatalog(principal);
      List<TimeRangeModel> ranges = TimeRange.getTimeRanges().stream()
         .filter(r -> checkPermission(r, principal))
         .sorted()
         .map(r -> TimeRangeModel.builder().from(r, catalog).build())
         .collect(Collectors.toList());
      boolean startTimeEnabled = securityProvider.checkPermission(
         principal, ResourceType.SCHEDULE_OPTION, "startTime", ResourceAction.READ);
      boolean timeRangeEnabled;

      if(SUtil.isMultiTenant()) {
         timeRangeEnabled = securityProvider.checkPermission(
            principal, ResourceType.SCHEDULE_OPTION, "timeRange", ResourceAction.READ) &&
            OrganizationManager.getInstance().isSiteAdmin(principal) && Tool.equals(OrganizationManager.getInstance().getCurrentOrgID(), pId.orgID);
      }
      else {
         timeRangeEnabled = securityProvider.checkPermission(
            principal, ResourceType.SCHEDULE_OPTION, "timeRange", ResourceAction.READ) &&
            Tool.equals(OrganizationManager.getInstance().getCurrentOrgID(), pId.orgID);
      }

      List<TimeZoneModel> timeZoneOptions = TimeZoneModel.getTimeZoneOptions();
      List<String> tableDataAssemblies = new ArrayList<>();

      if(rvs != null && rvs.getViewsheet() != null) {
         VSUtil.getTableDataAssemblies(rvs.getViewsheet(), true)
            .stream().forEach(assembly -> {
               if(CSVUtil.needExport(assembly)) {
                  tableDataAssemblies.add(assembly.getAbsoluteName());
               }
            });
      }

      UsersModel usersModel = scheduleService.getUsersModel(principal);

      return SimpleScheduleDialogModel.builder()
         .userDialogEnabled(userDialogEnabled)
         .taskName(taskName)
         .timeProp(timeProp)
         .twelveHourSystem(SreeEnv.getBooleanProperty("schedule.time.12hours"))
         .actionModel(viewsheetActionModel)
         .emailAddrDialogModel(emailAddrDialogModel)
         .emailButtonVisible(
            SreeEnv.getBooleanProperty("schedule.options.emailBrowserEnable", "true", "CHECKED"))
         .emailDeliveryEnabled(
            SreeEnv.getBooleanProperty("schedule.options.emailDelivery", "true", "CHECKED") &&
               scheduleService.checkPermission(principal,
                                               ResourceType.SCHEDULE_OPTION, "emailDelivery"))
         .expandEnabled(SecurityEngine.getSecurity().checkPermission(
            principal, ResourceType.VIEWSHEET_TOOLBAR_ACTION, "ScheduleExpandComponents",
            ResourceAction.READ))
         .timeRanges(ranges)
         .timeZoneOptions(timeZoneOptions)
         .startTimeEnabled(startTimeEnabled)
         .timeRangeEnabled(timeRangeEnabled)
         .users(usersModel == null || usersModel.emailUsers() == null ? new ArrayList<>()
                   : usersModel.emailUsers().stream().map(u -> u.name).toList())
         .groups(usersModel == null || usersModel.groups() == null ? new ArrayList<>()
                    : Arrays.asList(usersModel.groups()).stream().map(g -> g.name).toList())
         .emailGroups(usersModel == null ? new ArrayList<>() : Arrays.asList(usersModel.emailGroupBaseNames()))
         .tableAssemblies(tableDataAssemblies.toArray(new String[0]))
         .build();
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public MessageCommand checkScheduleDialogl(@ClusterProxyKey String runtimeId, boolean useCurrent,
                                              String bookmarkName, Principal principal) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);

      return vsBookmarkService.checkAddBookmark(rvs, bookmarkName, useCurrent, principal);
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void scheduleVS(@ClusterProxyKey String id, ScheduleDialogModel value,
                          Principal principal, CommandDispatcher commandDispatcher) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(id, principal);
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      AssetEntry entry = rvs.getEntry();
      ScheduleManager scheduleManager = ScheduleManager.getScheduleManager();
      SimpleScheduleDialogModel simpleScheduleDialogModel = value.simpleScheduleDialogModel();

      if(!(simpleScheduleDialogModel.actionModel() instanceof ViewsheetActionModel)) {
         return null;
      }

      ViewsheetActionModel viewsheetActionModel =
         (ViewsheetActionModel) simpleScheduleDialogModel.actionModel();
      EmailInfoModel emailInfoModel = viewsheetActionModel.emailInfoModel();
      TimeConditionModel timeConditionModel = simpleScheduleDialogModel.timeConditionModel();
      String taskName = Optional.ofNullable(simpleScheduleDialogModel.taskName()).orElse("");

      if(scheduleManager.getScheduleTask(taskName) != null) {
         Catalog catalog = Catalog.getCatalog(principal);
         MessageCommand messageCommand = new MessageCommand();
         messageCommand.setMessage(catalog.getString("em.schedule.task.duplicateName"));
         messageCommand.setType(MessageCommand.Type.ERROR);
         commandDispatcher.sendCommand(messageCommand);
         return null;
      }

      ViewsheetAction action = new ViewsheetAction();

      action.setBookmarks(new String[]{Optional.ofNullable(viewsheetActionModel.bookmarkName()).orElse("")});
      action.setBookmarkUsers(new IdentityID[]{Optional.ofNullable(viewsheetActionModel.bookmarkUser()).orElse(null)});
      action.setBookmarkTypes(new int[]{Optional.ofNullable(viewsheetActionModel.bookmarkType()).orElse(0)});

      action.setViewsheet(
         Optional.ofNullable(viewsheetActionModel.viewsheet()).orElse(entry.toIdentifier()));

      action.setFileFormat(getEmailFormat(emailInfoModel.formatType()));
      action.setEmailCSVConfig(new CSVConfig(emailInfoModel.csvConfigModel()));
      action.setEmails(Optional.ofNullable(emailInfoModel.emails()).orElse(""));
      action.setFrom(Optional.ofNullable(emailInfoModel.fromAddress())
                        .orElse(SreeEnv.getProperty("mail.from.address")));
      action.setAttachmentName(
         Optional.ofNullable(emailInfoModel.attachmentName()).orElse(entry.getName()));
      action.setSubject(
         Optional.ofNullable(emailInfoModel.subject()).orElse(getEntryMessage(entry)));
      action.setMessage(
         Optional.ofNullable(emailInfoModel.message()).orElse(getEntryMessage(entry)));
      action.setMatchLayout(emailInfoModel.matchLayout());
      action.setExpandSelections(emailInfoModel.expandSelections());
      action.setOnlyDataComponents(emailInfoModel.onlyDataComponents());
      action.setCCAddresses(emailInfoModel.ccAddresses());
      action.setBCCAddresses(emailInfoModel.bccAddresses());
      action.setExportAllTabbedTables(emailInfoModel.exportAllTabbedTables());

      if(emailInfoModel.formatType() == FileFormatInfo.EXPORT_TYPE_CSV) {
         action.setCompressFile(true);
      }

      TimeCondition condition = new TimeCondition();
      condition.setHour(Optional.ofNullable(timeConditionModel.hour()).orElse(1));
      condition.setMinute(Optional.ofNullable(timeConditionModel.minute()).orElse(30));
      condition.setSecond(Optional.ofNullable(timeConditionModel.second()).orElse(0));
      condition.setType(timeConditionModel.type());

      if(condition.getType() == TimeCondition.EVERY_DAY) {
         condition.setWeekdayOnly(timeConditionModel.weekdayOnly());

         if(!timeConditionModel.weekdayOnly()) {
            condition.setInterval(1);
         }
      }
      else if(condition.getType() == TimeCondition.EVERY_WEEK) {
         condition.setInterval(1);
         int[] sorted = timeConditionModel.daysOfWeek();
         Arrays.sort(sorted);
         condition.setDaysOfWeek(sorted);
      }
      else if(condition.getType() == TimeCondition.EVERY_MONTH) {
         condition.setMonthsOfYear(timeConditionModel.monthsOfYear());

         if(Optional.ofNullable(timeConditionModel.monthlyDaySelected()).orElse(true)) {
            condition.setDayOfMonth(Optional.ofNullable(timeConditionModel.dayOfMonth()).orElse(1));
            condition.setMonthsOfYear(IntStream.range(0, 12).toArray());
         }
         else {
            condition.setWeekOfMonth(
               Optional.ofNullable(timeConditionModel.weekOfMonth()).orElse(1));
            condition.setDayOfWeek(
               Optional.ofNullable(timeConditionModel.dayOfWeek()).orElse(1));
         }
      }

      if(timeConditionModel.timeRange() != null) {
         TimeRangeModel range = timeConditionModel.timeRange();
         condition.setTimeRange(new TimeRange(
            range.name(), range.startTime(), range.endTime(), range.defaultRange()));
      }

      if(timeConditionModel.timeZone() != null) {
         condition.setTimeZone(TimeZone.getTimeZone(timeConditionModel.timeZone()));
      }

      List<UserVariable> vars = new ArrayList<>();
      VSEventUtil.refreshParameters(viewsheetService, box,
                                    rvs.getViewsheet(), false,
                                    new VariableTable(), vars);
      VariableTable variable = box.getVariableTable();

      if(vars.size() > 0) {
         RepletRequest request = new RepletRequest();

         for(UserVariable var : vars) {
            String name = var.getName();

            if(variable.contains(name)) {
               request.setParameter(name, variable.get(name), var.getTypeNode().getType());
            }
         }

         action.setViewsheetRequest(request);
      }

      String name = SUtil.getTaskNameWithoutUser(taskName);
      ScheduleTask currtask = new ScheduleTask(name);

      currtask.addCondition(condition);
      currtask.addAction(action);

      String actionName = ActionRecord.ACTION_NAME_CREATE;
      String objectName = taskName;
      String objectType = ActionRecord.OBJECT_TYPE_TASK;

      Timestamp actionTimestamp = new Timestamp(System.currentTimeMillis());
      ActionRecord actionRecord = new ActionRecord(SUtil.getUserName(principal), actionName, objectName,
                                                   objectType, actionTimestamp, ActionRecord.ACTION_STATUS_FAILURE,
                                                   null);

      try {
         scheduleManager.setScheduleTask(taskName, currtask, principal);

         if(actionRecord != null) {
            actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_SUCCESS);
         }
      }
      catch(Exception ex) {
         if(actionRecord != null) {
            actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_FAILURE);
            actionRecord.setActionError(ex.getMessage());
         }

         throw ex;
      }
      finally {
         if(actionRecord != null) {
            Audit.getInstance().auditAction(actionRecord, principal);
         }
      }

      if(condition.getTimeRange() != null) {
         new TaskBalancer().updateTask(currtask, condition.getTimeRange());
      }

      return null;
   }

   /**
    * Check if security is enabled.
    */
   private boolean isSecurityEnabled() {
      try {
         return !SecurityEngine.getSecurity().getSecurityProvider().isVirtual();
      }
      catch(Exception ex) {
         LOG.warn("Failed to determine if security is enabled",
                  ex);
         return false;
      }
   }

   /**
    * Gets the message for the schedule email.
    * @param entry   The viewsheet entry
    * @return  the message
    */
   private String getEntryMessage(AssetEntry entry) {
      return entry.getName() + " generated at " + (new Date().toString());
   }

   /**
    * Get the email format type string from int value.
    * @param formatType the FileFormatInfo int type
    * @return  the format type string
    */
   private String getEmailFormat(int formatType) {
      if(formatType == FileFormatInfo.EXPORT_TYPE_EXCEL) {
         return FileFormatInfo.EXPORT_NAME_EXCEL;
      }
      else if(formatType == FileFormatInfo.EXPORT_TYPE_POWERPOINT) {
         return FileFormatInfo.EXPORT_NAME_POWERPOINT;
      }
      else if(formatType == FileFormatInfo.EXPORT_TYPE_PDF) {
         return FileFormatInfo.EXPORT_NAME_PDF;
      }
      else if(formatType == FileFormatInfo.EXPORT_TYPE_PNG) {
         return FileFormatInfo.EXPORT_NAME_PNG;
      }
      else if(formatType == FileFormatInfo.EXPORT_TYPE_HTML) {
         return FileFormatInfo.EXPORT_NAME_HTML;
      }
      else if(formatType == FileFormatInfo.EXPORT_TYPE_CSV) {
         return FileFormatInfo.EXPORT_NAME_CSV;
      }

      return FileFormatInfo.EXPORT_NAME_EXCEL;
   }

   private boolean checkPermission(TimeRange range, Principal user) {
      return securityProvider.checkPermission(
         user, ResourceType.SCHEDULE_TIME_RANGE, range.getName(), ResourceAction.ACCESS);
   }

   private final ViewsheetService viewsheetService;
   private final SecurityProvider securityProvider;
   private final ScheduleService scheduleService;
   private VSBookmarkService vsBookmarkService;
   private static final Logger LOG =
      LoggerFactory.getLogger(ScheduleDialogService.class);
}
