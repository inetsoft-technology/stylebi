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

import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.DataCycleManager;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.schedule.ScheduleCondition;
import inetsoft.sree.schedule.TimeCondition;
import inetsoft.sree.security.*;
import inetsoft.sree.security.SecurityException;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import inetsoft.web.admin.content.repository.ResourcePermissionService;
import inetsoft.web.admin.schedule.model.*;
import inetsoft.web.admin.security.ResourcePermissionModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ScheduleCycleService {
   @Autowired
   public ScheduleCycleService(DataCycleManager dataCycleManager,
                               ScheduleConditionService scheduleConditionService,
                               SchedulerMonitoringService schedulerMonitoringService,
                               ResourcePermissionService permissionService,
                               SecurityEngine securityEngine)
   {
      this.dataCycleManager = dataCycleManager;
      this.scheduleConditionService = scheduleConditionService;
      this.schedulerMonitoringService = schedulerMonitoringService;
      this.permissionService = permissionService;
      this.securityEngine = securityEngine;
   }

   public DataCycleListModel getCycleInfos(Principal principal) throws Exception {
      List<DataCycleInfo> dataCycleInfoList = new ArrayList<>();
      String orgId = OrganizationManager.getInstance().getCurrentOrgID(principal);

      for(DataCycleInfo cycleInfo : schedulerMonitoringService.getCycleInfo()) {
         if(securityEngine.checkPermission(principal, ResourceType.SCHEDULE_CYCLE,
                                           getCyclePermissionID(cycleInfo.getName(), orgId), ResourceAction.ACCESS))
         {
            dataCycleInfoList.add(cycleInfo);
         }
      }

      return DataCycleListModel.builder()
         .cycles(dataCycleInfoList)
         .build();
   }

   public ScheduleCycleDialogModel getDialogModel(String cycleName, Principal principal)
      throws Exception
   {
      String orgId = OrganizationManager.getInstance().getCurrentOrgID(principal);

      if(!securityEngine.checkPermission(principal, ResourceType.SCHEDULE_CYCLE,
            getCyclePermissionID(cycleName, orgId), ResourceAction.ACCESS))
      {
         throw new SecurityException(catalog.getString("em.scheduler.cycle.unauthorized", cycleName));
      }

      int index = cycleName.indexOf(":");
      String label = index != -1 ? cycleName.substring(index + 1) : cycleName;
      String zoneName = Calendar.getInstance().getTimeZone().getDisplayName();

      List<String> conditions = dataCycleManager.getConditions(cycleName, orgId).stream()
         .map(ScheduleCondition::toString)
         .collect(Collectors.toList());

      boolean noDefaultTime = "false".equals(SreeEnv.getProperty("schedule.condition.taskDefaultTime"));
      final ResourcePermissionModel tableModel = permissionService.getTableModel(
         getCyclePermissionID(cycleName, orgId), ResourceType.SCHEDULE_CYCLE, EnumSet.of(ResourceAction.ACCESS), principal);
      List<TimeZoneModel> timeZoneOptions = TimeZoneModel.getTimeZoneOptions();;
      boolean startTimeEnabled = securityEngine.getSecurityProvider().checkPermission(
         principal, ResourceType.SCHEDULE_OPTION, "startTime", ResourceAction.READ);

      return ScheduleCycleDialogModel.builder()
         .name(cycleName)
         .label(label)
         .timeZone(zoneName)
         .timeZoneOptions(timeZoneOptions)
         .taskDefaultTime(!noDefaultTime)
         .conditionPaneModel(getConditionPaneModel(cycleName, orgId, principal))
         .cycleInfo(getCycleOptions(cycleName, orgId))
         .permissionModel(tableModel)
         .startTimeEnabled(startTimeEnabled)
         .build();
   }

   private DataCycleManager.CycleInfo getCycleOptions(String cycleName, String orgId) {
      DataCycleManager.CycleInfo cycleInfo = dataCycleManager.getCycleInfo(cycleName, orgId);

      if(cycleInfo == null) {
         cycleInfo = new DataCycleManager.CycleInfo();
      }

      return cycleInfo;
   }

   public TaskConditionPaneModel getConditionPaneModel(String cycleName, String orgId, Principal principal)
      throws Exception
   {
      if(cycleName == null || "".equals(cycleName)) {
         throw new Exception(catalog.getString(
            "em.scheduler.emptyCycleName"));
      }

      TaskConditionPaneModel.Builder builder = TaskConditionPaneModel.builder();
      dataCycleManager.getConditions(cycleName, orgId).stream()
         .map(condition -> scheduleConditionService.getConditionModel(condition, principal))
         .forEach(builder::addConditions);

      String timeProp = SreeEnv.getProperty("format.time");

      if(timeProp == null || "".equals(timeProp)) {
         timeProp = Tool.DEFAULT_TIME_PATTERN;
      }

      return builder
         .timeProp(timeProp.trim())
         .twelveHourSystem(SreeEnv.getBooleanProperty("schedule.time.12hours"))
         .build();
   }

   public String addDataCycle(Principal principal, String timeZoneId) {
      ActionRecord actionRecord = SUtil.getActionRecord(principal, ActionRecord.ACTION_NAME_CREATE,
                                                        null, ActionRecord.OBJECT_TYPE_CYCLE);

      try {
         String cycleName = "Cycle";
         String orgId = OrganizationManager.getInstance().getCurrentOrgID(principal);

         for(int i = 1; i < Integer.MAX_VALUE; i++) {
            if(dataCycleManager.getConditions(cycleName + i, orgId) == null) {
               cycleName += i;
               break;
            }
         }

         IdentityID identity = IdentityID.getIdentityIDFromKey(principal.getName());
         actionRecord.setObjectName(cycleName);
         actionRecord.setObjectUser(identity.name);
         ScheduleCondition condition = TimeCondition.at(1, 30, 0);
         ((TimeCondition) condition).setTimeZone(TimeZone.getTimeZone(timeZoneId));
         ((TimeCondition) condition).setInterval(1);

         Calendar calendar = Calendar.getInstance();
         calendar.setTime(new Date());
         dataCycleManager.addCondition(cycleName, orgId, condition);
         DataCycleManager.CycleInfo cycleInfo = new DataCycleManager.CycleInfo(cycleName, orgId);
         cycleInfo.setCreatedBy(principal.getName());
         cycleInfo.setLastModifiedBy(principal.getName());
         cycleInfo.setCreated(System.currentTimeMillis());
         cycleInfo.setLastModified(System.currentTimeMillis());
         dataCycleManager.setCycleInfo(cycleName, orgId, cycleInfo);
         dataCycleManager.save();
         addCyclePermission(cycleName, principal);
         return cycleName;
      }
      catch(Exception ex) {
         LOG.error("Failed to create cycle", ex);
         actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_FAILURE);
         actionRecord.setActionError(ex.getMessage());
         return null;
      }
      finally {
         Audit.getInstance().auditAction(actionRecord, principal);
      }
   }

   public void editCycle(ScheduleCycleDialogModel model, Principal principal) throws Exception {
      ActionRecord actionRecord = SUtil.getActionRecord(principal, ActionRecord.ACTION_NAME_EDIT,
                                                        null, ActionRecord.OBJECT_TYPE_CYCLE);

      try {
         String oldName = model.name();
         String orgId = OrganizationManager.getInstance().getCurrentOrgID(principal);

         if(!securityEngine.checkPermission(principal, ResourceType.SCHEDULE_CYCLE,
               getCyclePermissionID(oldName, orgId), ResourceAction.ACCESS))
         {
            catalog.getString("em.scheduler.cycle.unauthorized", oldName);
         }

         String newName = model.label();
         IdentityID identity = IdentityID.getIdentityIDFromKey(principal.getName());
         actionRecord.setObjectName(newName);
         actionRecord.setObjectUser(identity.name);

         if(oldName == null || "".equals(oldName) ||
            newName == null || "".equals(newName)) {
            throw new Exception(catalog.getString(
               "em.scheduler.emptyCycleName"));
         }

         if(!oldName.equals(newName) && dataCycleManager.hasPregeneratedDependency(oldName)) {
            throw new Exception(catalog.getString("em.scheduler.renameCycleError"));
         }

         if(!newName.equals(oldName)) {
            actionRecord.setActionName(ActionRecord.ACTION_NAME_RENAME);
            actionRecord.setObjectName(oldName);
            actionRecord.setActionError("Target Cycle: " + newName);

            for(Enumeration<?> cycles = dataCycleManager.getDataCycles(orgId); cycles.hasMoreElements(); ) {
               if(cycles.nextElement().toString().equals(newName)) {
                  throw new Exception(catalog.getString("em.dataCycle.sameNameError"));
               }
            }

            removeCyclePermission(oldName, orgId);
         }
         else if(newName == null || "".equals(newName)) {
            throw new Exception(catalog.getString("em.scheduler.emptyCycleName"));
         }

         boolean enabled = dataCycleManager.isEnable(oldName, orgId);
         dataCycleManager.removeDataCycle(oldName, orgId);
         dataCycleManager.setConditions(newName, orgId, getCycleConditions(model.conditionPaneModel().conditions()));
         dataCycleManager.setEnable(newName, orgId, enabled);
         DataCycleManager.CycleInfo cycleInfo = model.cycleInfo();

         if(cycleInfo != null && cycleInfo.getOrgId() == null) {
            cycleInfo.setOrgId(orgId);
         }

         cycleInfo.setLastModified(System.currentTimeMillis());

         if(cycleInfo.getLastModifiedBy() == null) {
            cycleInfo.setLastModifiedBy(principal.getName());
         }

         if(!cycleInfo.getName().equals(newName)) {
            cycleInfo.setName(newName);
         }

         dataCycleManager.setCycleInfo(newName, orgId, cycleInfo);
         dataCycleManager.save();

         if(model.permissionModel() != null &&
            (model.permissionModel().changed() || !newName.equals(oldName))) {
            permissionService.setResourcePermissions(getCyclePermissionID(newName, orgId), ResourceType.SCHEDULE_CYCLE,
                                                     model.permissionModel(), principal);
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

   private List<ScheduleCondition> getCycleConditions(List<ScheduleConditionModel> conditionModels)
      throws Exception
   {
      List<ScheduleCondition> conditions = new ArrayList<>();

      for(ScheduleConditionModel cModel : conditionModels) {
         conditions.add(scheduleConditionService.getConditionFromModel(cModel));
      }

      return conditions;
   }

   public DataCycleListModel removeCycles(List<DataCycleInfo> dataCycleInfoList,
                                          Principal principal)
      throws Exception
   {
      String errmsg = dataCycleInfoList.stream().map(DataCycleInfo::getName)
         .filter(dataCycleManager::hasPregeneratedDependency)
         .reduce("", String::concat);

      if(errmsg.length() > 0) {
         throw new Exception(catalog.getString("em.scheduler.deleteCyclesError", errmsg));
      }

      for(DataCycleInfo info : dataCycleInfoList) {
         deleteCycle(info.getName(), principal);
      }

      return getCycleInfos(principal);
   }

   private void deleteCycle(String cycleName, Principal principal) throws Exception {
      ActionRecord actionRecord = SUtil.getActionRecord(principal, ActionRecord.ACTION_NAME_DELETE,
                                                        cycleName, ActionRecord.OBJECT_TYPE_CYCLE);
      String orgId = OrganizationManager.getInstance().getCurrentOrgID(principal);
      IdentityID identity = IdentityID.getIdentityIDFromKey(principal.getName());
      actionRecord.setObjectUser(identity.name);

      try {
         if(cycleName != null) {
            if(!dataCycleManager.hasPregeneratedDependency(cycleName)) {
               dataCycleManager.removeDataCycle(cycleName, orgId);
               dataCycleManager.save();
               removeCyclePermission(cycleName, orgId);
            }
            // warning if some replet uses the cycle
            else {
               LOG.error(catalog.getString("em.scheduler.deleteCyclesError", cycleName));
            }
         }
         else {
            LOG.error(catalog.getString("designer.property.emptyNullError"));
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

   private void addCyclePermission(String cycleName, Principal user) {
      Permission perm = new Permission();
      String userWithoutOrg = user.getName() != null ?
         IdentityID.getIdentityIDFromKey(user.getName()).getName() : null;
      Set<String> users = Collections.singleton(userWithoutOrg);
      String orgId = OrganizationManager.getInstance().getUserOrgId(user);

      perm.setUserGrantsForOrg(ResourceAction.ACCESS, users, orgId);
      perm.setUserGrantsForOrg(ResourceAction.READ, users, orgId);
      perm.setUserGrantsForOrg(ResourceAction.WRITE, users, orgId);
      perm.setUserGrantsForOrg(ResourceAction.DELETE, users, orgId);
      perm.updateGrantAllByOrg(orgId, true);
      securityEngine.setPermission(ResourceType.SCHEDULE_CYCLE, getCyclePermissionID(cycleName, orgId), perm);
   }

   private void removeCyclePermission(String cycleName, String orgId) {
      try {
         securityEngine.setPermission(ResourceType.SCHEDULE_CYCLE, getCyclePermissionID(cycleName, orgId), null);
      }
      catch(Exception ex) {
         LOG.error("Failed to clear permissions for schedule cycle {}", cycleName, ex);
      }
   }

   public static String getCyclePermissionID(String name, String orgID) {
      if(orgID == null || !SUtil.isMultiTenant() ) {
         orgID = Organization.getDefaultOrganizationID();
      }

      return new IdentityID(name, orgID).convertToKey();
   }

   private final DataCycleManager dataCycleManager;
   private final ScheduleConditionService scheduleConditionService;
   private final SchedulerMonitoringService schedulerMonitoringService;
   private final ResourcePermissionService permissionService;
   private final SecurityEngine securityEngine;
   private final Catalog catalog = Catalog.getCatalog();
   private static final Logger LOG = LoggerFactory.getLogger(ScheduleCycleService.class);
}
