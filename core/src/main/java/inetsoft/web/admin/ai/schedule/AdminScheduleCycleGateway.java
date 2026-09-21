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

import inetsoft.mv.MVDef;
import inetsoft.mv.MVManager;
import inetsoft.sree.internal.DataCycleManager;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.sree.security.Permission;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.web.admin.schedule.DataCycleInfo;
import inetsoft.web.admin.schedule.ScheduleConditionService;
import inetsoft.web.admin.schedule.ScheduleCycleService;
import inetsoft.web.admin.schedule.model.ScheduleConditionModel;
import inetsoft.web.admin.schedule.model.ScheduleCycleDialogModel;
import inetsoft.web.admin.schedule.model.TaskConditionPaneModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@code AdminScheduleCycleGateway} implements, directly against {@link DataCycleManager} (and,
 * for permission checks/rename-safety reuse only, {@link ScheduleCycleService}), exactly the
 * Scheduled Cycle CRUD operations admin-chat's cycle area (bug #76848, design §5.2) needs.
 *
 * <p>Read methods build {@link ScheduleCycleView} directly from {@code DataCycleManager}, NOT
 * from {@code ScheduleCycleService#getDialogModel} (which pulls in EM-dialog-only fields --
 * {@code permissionModel}/{@code timeZoneOptions}/{@code startTimeEnabled} -- this tool surface
 * doesn't need). {@code getDialogModel} is still called, unchanged, purely for its permission
 * check (a {@code SecurityException} on unauthorized) and, for {@code update}, its current-state
 * read -- its own return value beyond that is discarded.
 *
 * <p>{@code create} deliberately bypasses {@code ScheduleCycleService#addDataCycle}/{@code
 * editCycle} entirely, calling {@code DataCycleManager} directly (decision D4) -- {@code
 * addDataCycle}'s auto-naming/bootstrap-condition dance exists only to serve the EM "New" button's
 * own UX (no name known yet), which does not apply here (the caller always supplies a name).
 * {@code update}/{@code delete} still call the real {@code ScheduleCycleService} methods, reusing
 * their existing collision/rename-while-in-use/audit logic unchanged.
 */
@Service
public class AdminScheduleCycleGateway {
   @Autowired
   public AdminScheduleCycleGateway(DataCycleManager dataCycleManager,
                                    ScheduleCycleService scheduleCycleService,
                                    ScheduleConditionService scheduleConditionService,
                                    SecurityEngine securityEngine)
   {
      this.dataCycleManager = dataCycleManager;
      this.scheduleCycleService = scheduleCycleService;
      this.scheduleConditionService = scheduleConditionService;
      this.securityEngine = securityEngine;
   }

   /**
    * Every cycle the caller may see (permission-filtered via {@code
    * ScheduleCycleService#getCycleInfos}), each enriched with its real conditions and MV
    * dependents via a single shared {@link #dependentMvNamesByCycle} scan.
    */
   public List<ScheduleCycleView> listCycles(Principal user) throws Exception {
      Set<String> visibleNames = new LinkedHashSet<>();

      for(DataCycleInfo info : scheduleCycleService.getCycleInfos(user).cycles()) {
         visibleNames.add(info.getName());
      }

      String orgId = OrganizationManager.getInstance().getCurrentOrgID(user);
      Map<String, List<String>> dependents = dependentMvNamesByCycle();
      List<ScheduleCycleView> views = new ArrayList<>();

      for(String name : visibleNames) {
         views.add(buildView(name, orgId, dependents.getOrDefault(name, List.of())));
      }

      return views;
   }

   /** Whether a cycle named {@code name} exists in the caller's current organization -- the
    * "does the name exist" check that must run BEFORE any permission-checked read (design §5.2:
    * {@code getDialogModel} itself does not distinguish absence from an empty result). */
   public boolean cycleExists(String name, Principal user) {
      return cycleExists(name, OrganizationManager.getInstance().getCurrentOrgID(user));
   }

   private boolean cycleExists(String name, String orgId) {
      return Collections.list(dataCycleManager.getDataCycles(orgId)).contains(name);
   }

   /**
    * Reads one cycle, or {@code null} if it does not exist (the controller maps that to a 404).
    * Existence is checked first (see {@link #cycleExists}); only then is {@code getDialogModel}
    * called, purely for its permission check ({@code SecurityException} on unauthorized) -- its
    * return value is discarded, the real view is built from {@code DataCycleManager} directly.
    */
   public ScheduleCycleView getCycle(String name, Principal user) throws Exception {
      String orgId = OrganizationManager.getInstance().getCurrentOrgID(user);

      if(!cycleExists(name, orgId)) {
         return null;
      }

      scheduleCycleService.getDialogModel(name, user);
      return buildView(name, orgId, dependentMvNames(name));
   }

   /**
    * Creates a new cycle directly via {@code DataCycleManager} (decision D4) -- collision
    * checking is the caller's ({@link ScheduleCycleChangePlanService}'s) responsibility at plan
    * time; this method does not re-check it, matching {@code update}/{@code delete}'s own split
    * (plan time decides, apply time trusts the gateway call to do exactly what was resolved).
    */
   public void createCycle(ScheduleCycleChangeRequest.ScheduleCycleSpec spec, Principal user)
      throws Exception
   {
      String orgId = OrganizationManager.getInstance().getCurrentOrgID(user);
      String name = spec.name();

      List<inetsoft.sree.schedule.ScheduleCondition> conditions =
         ScheduleConditionConverter.convertConditions(spec.conditions());

      dataCycleManager.setConditions(name, orgId, conditions);

      long now = System.currentTimeMillis();
      DataCycleManager.CycleInfo cycleInfo = new DataCycleManager.CycleInfo(name, orgId);
      cycleInfo.setCreatedBy(user.getName());
      cycleInfo.setLastModifiedBy(user.getName());
      cycleInfo.setCreated(now);
      cycleInfo.setLastModified(now);
      dataCycleManager.setCycleInfo(name, orgId, cycleInfo);
      dataCycleManager.save();
      addCyclePermission(name, user);
   }

   /**
    * Updates an existing cycle via the real {@code ScheduleCycleService#editCycle} (kept,
    * deliberately, unlike {@code create} -- decision D4), reusing its rename-collision check, its
    * rename-while-in-use refusal, its rename-permission-copy, and its audit record unchanged.
    * {@code spec} is a PARTIAL merge (decision D5): a {@code null} {@code spec.name()} leaves the
    * cycle's identity unchanged; a {@code null} {@code spec.conditions()} leaves its conditions
    * unchanged.
    */
   public void updateCycle(String currentName, ScheduleCycleChangeRequest.ScheduleCycleSpec spec,
                           Principal user)
      throws Exception
   {
      ScheduleCycleDialogModel dialogModel = scheduleCycleService.getDialogModel(currentName, user);
      String newName = spec.name() != null ? spec.name() : currentName;
      TaskConditionPaneModel conditionPane;

      if(spec.conditions() != null) {
         List<ScheduleConditionModel> models = spec.conditions().stream()
            .map(ScheduleConditionConverter::convertCondition)
            .map(condition -> scheduleConditionService.getConditionModel(condition, user))
            .collect(Collectors.toList());
         conditionPane = TaskConditionPaneModel.builder()
            .from(dialogModel.conditionPaneModel())
            .conditions(models)
            .build();
      }
      else {
         conditionPane = dialogModel.conditionPaneModel();
      }

      ScheduleCycleDialogModel merged = ScheduleCycleDialogModel.builder()
         .from(dialogModel)
         .label(newName)
         .conditionPaneModel(conditionPane)
         .build();

      scheduleCycleService.editCycle(merged, user);
   }

   /** Deletes every named cycle via the real {@code ScheduleCycleService#removeCycles}, reused
    * unchanged. Called ONCE PER PLAN ENTRY at apply time (decision D6), never once for a whole
    * batch of deletes -- callers pass a singleton list. */
   public void deleteCycles(List<String> names, Principal user) throws Exception {
      scheduleCycleService.removeCycles(
         names.stream().map(DataCycleInfo::new).collect(Collectors.toList()), user);
   }

   /**
    * The cycle's real (non-wire) persisted state at {@code name}, or {@code null} if it does not
    * exist -- exposed only for the plan/apply services' own before/after XML projection (design
    * §5.4); {@link #getCycle} exposes the caller-facing wire shape instead.
    */
   DataCycleManager.DataCycleAsset currentAsset(String name, String orgId) {
      if(!cycleExists(name, orgId)) {
         return null;
      }

      DataCycleManager.DataCycleAsset asset = new DataCycleManager.DataCycleAsset();
      asset.setName(name);
      asset.setOrgId(orgId);
      asset.setEnabled(dataCycleManager.isEnable(name, orgId));
      asset.setConditions(new ArrayList<>(dataCycleManager.getConditions(name, orgId)));
      DataCycleManager.CycleInfo info = dataCycleManager.getCycleInfo(name, orgId);
      asset.setInfo(info != null ? info : new DataCycleManager.CycleInfo(name, orgId));
      return asset;
   }

   /** Projects {@code asset} into the same XML text {@code DataCycleManager.DataCycleAsset}
    * already knows how to write (design §5.4) -- zero new projection logic, a straight reuse of
    * an existing, already-public serialization method. */
   static String projectXml(DataCycleManager.DataCycleAsset asset) {
      StringWriter sw = new StringWriter();

      try(PrintWriter pw = new PrintWriter(sw)) {
         asset.writeXML(pw);
      }

      return sw.toString();
   }

   /** Every cycle currently assigned to at least one materialized view, mapped to the names of
    * those MVs -- ONE shared {@code mvManager.list(false)} scan for every cycle at once (design
    * §5.2), mirroring (not reusing, since it is boolean-only) the identical equality test {@code
    * DataCycleManager#hasPregeneratedDependency} uses internally: a matching {@code MVDef}'s own
    * {@code getCycle()} plus an org match against {@code OrganizationManager}'s CURRENT org --
    * that primitive ignores whatever org id is passed to it and always consults the ambient
    * current-org context (confirmed from source, {@code DataCycleManager.java:695}), so this scan
    * mirrors that exact (if slightly surprising) real behavior rather than the caller's own
    * {@code orgId}, to guarantee {@code inUse}/{@code dependentMvNames} can never disagree with
    * what {@code removeCycles}/{@code editCycle} will themselves refuse on. */
   private Map<String, List<String>> dependentMvNamesByCycle() {
      String currentOrgId = OrganizationManager.getInstance().getCurrentOrgID();
      Map<String, List<String>> byCycle = new HashMap<>();

      // Read the same way DataCycleManager#hasPregeneratedDependency itself does, through the
      // static accessor -- MVManager is a process-wide singleton, not worth injecting a second
      // handle to (same convention AdminMvGateway#getDefaultCycle already documents).
      for(MVDef mv : MVManager.getManager().list(false)) {
         if(mv.getCycle() != null && currentOrgId.equals(mv.getEntry().getOrgID())) {
            byCycle.computeIfAbsent(mv.getCycle(), k -> new ArrayList<>()).add(mv.getName());
         }
      }

      return byCycle;
   }

   /** Single-cycle convenience over {@link #dependentMvNamesByCycle} -- used by {@link
    * ScheduleCycleChangePlanService}'s own delete/rename-while-in-use refusal (design §6), which
    * needs the actual MV names, not just a boolean. */
   List<String> dependentMvNames(String cycleName) {
      return dependentMvNamesByCycle().getOrDefault(cycleName, List.of());
   }

   private ScheduleCycleView buildView(String name, String orgId, List<String> dependentMvNames) {
      List<inetsoft.web.api.schedule.TimeCondition> conditions = dataCycleManager
         .getConditions(name, orgId).stream()
         .map(ScheduleConditionConverter::convertCondition)
         .filter(inetsoft.web.api.schedule.TimeCondition.class::isInstance)
         .map(inetsoft.web.api.schedule.TimeCondition.class::cast)
         .collect(Collectors.toList());

      return new ScheduleCycleView(name, conditions, !dependentMvNames.isEmpty(), dependentMvNames);
   }

   /** Mirrors {@code ScheduleCycleService#addCyclePermission} (private there) -- decision D4's
    * own small, mechanical, non-business-logic duplication (that method has no "two callers must
    * never drift" risk the way {@link ScheduleConditionConverter} does, since only {@code create}
    * ever needs it). {@code getCyclePermissionID} itself is public and reused, not duplicated. */
   private void addCyclePermission(String cycleName, Principal user) {
      Permission perm = new Permission();
      String userWithoutOrg = user.getName() != null
         ? IdentityID.getIdentityIDFromKey(user.getName()).getName() : null;
      Set<String> users = Collections.singleton(userWithoutOrg);
      String orgId = OrganizationManager.getInstance().getUserOrgId(user);

      perm.setUserGrantsForOrg(ResourceAction.ACCESS, users, orgId);
      perm.setUserGrantsForOrg(ResourceAction.READ, users, orgId);
      perm.setUserGrantsForOrg(ResourceAction.WRITE, users, orgId);
      perm.setUserGrantsForOrg(ResourceAction.DELETE, users, orgId);
      perm.updateGrantAllByOrg(orgId, true);
      securityEngine.setPermission(ResourceType.SCHEDULE_CYCLE,
                                   ScheduleCycleService.getCyclePermissionID(cycleName, orgId), perm);
   }

   private final DataCycleManager dataCycleManager;
   private final ScheduleCycleService scheduleCycleService;
   private final ScheduleConditionService scheduleConditionService;
   private final SecurityEngine securityEngine;
}
