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
package inetsoft.web.admin.ai.dashboard;

import inetsoft.sree.security.*;
import inetsoft.sree.web.dashboard.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.util.DefaultIdentity;
import inetsoft.uql.util.Identity;
import inetsoft.util.audit.AdminChangeRecord;
import inetsoft.web.admin.ai.PlanChange;
import inetsoft.web.admin.ai.ResolvedPlan;
import inetsoft.web.admin.ai.TaskAuditToken;
import inetsoft.web.admin.content.repository.RepositoryDashboardService;
import inetsoft.web.admin.content.repository.model.RepositoryDashboardSettingsModel;
import inetsoft.web.admin.content.repository.model.RepositoryFolderDashboardSettingsModel;
import inetsoft.web.security.auth.MissingResourceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Resolves a requested list of Portal Dashboard changes into a {@link ResolvedPlan} -- the
 * dashboard analog of {@code ViewsheetChangePlanService} (Redmine #76695). Also backs the plain
 * (non-mutating) list/get reads the controller exposes, since those need exactly the same
 * registry/manager reads a preview does.
 *
 * <p>Reuses {@link RepositoryDashboardService}'s own {@code getSettings}/{@code setSettings}/
 * {@code addDashboard}/{@code delete} directly for every single-dashboard operation, rather than
 * re-deriving that class's permission/rename/dependency-cascade handling (per this area's own
 * design note: the wiz controller reuses the SAME underlying service, it does not call the EM
 * controller). Only the dashboard-folder ordering read/write is NOT reused from that service: its
 * own {@code getDashboardFolderSettings}/{@code setDashboardFolderSettings} are principal-bound
 * (always resolve the identity from the calling {@link Principal}), with no {@code owner} parameter
 * at all -- there is no existing EM screen that arranges a DIFFERENT user's dashboard order, only
 * the calling admin's own order (used as the stand-in for "the global default") or, self-service,
 * a user's own order for themselves (out of scope here, see the design plan's background section).
 * Supporting an explicit {@code owner} for reorder is therefore a direct extension of
 * {@link DashboardManager}'s already identity-agnostic {@code getDashboards}/{@code setDashboards}
 * primitive -- consistent with how {@code owner} is already a first-class parameter for the
 * single-dashboard case -- rather than a mechanical reuse of an existing screen; there is nothing to
 * visually compare it against in EM today.
 */
@Component
public class DashboardChangePlanService {
   @Autowired
   public DashboardChangePlanService(RepositoryDashboardService repositoryDashboardService,
                                     DashboardRegistryManager dashboardRegistryManager,
                                     DashboardManager dashboardManager)
   {
      this.repositoryDashboardService = repositoryDashboardService;
      this.dashboardRegistryManager = dashboardRegistryManager;
      this.dashboardManager = dashboardManager;
   }

   /** {@code list_portal_dashboards}: every dashboard currently registered for this owner scope. */
   public List<RepositoryDashboardSettingsModel> list(String ownerRaw, Principal user) throws Exception {
      IdentityID owner = parseOwner(ownerRaw);
      DashboardRegistry registry = registryFor(owner);
      List<RepositoryDashboardSettingsModel> result = new ArrayList<>();

      for(String registryName : registry.getDashboardNames()) {
         result.add(repositoryDashboardService.getSettings(registryName, owner, user));
      }

      return result;
   }

   /** {@code get_portal_dashboard_settings}. */
   public RepositoryDashboardSettingsModel getSettings(String name, String ownerRaw, Principal user)
      throws Exception
   {
      IdentityID owner = parseOwner(ownerRaw);
      String registryName = fixDashboardName(name, owner);

      if(registryFor(owner).getDashboard(registryName) == null) {
         throw new MissingResourceException(
            "no dashboard found named \"" + name + "\"" + ownerSuffix(owner));
      }

      return repositoryDashboardService.getSettings(name, owner, user);
   }

   /** {@code get_portal_dashboard_folder}. See the class javadoc for the {@code owner}-scoped case. */
   public RepositoryFolderDashboardSettingsModel getFolder(String ownerRaw, Principal user)
      throws Exception
   {
      IdentityID owner = parseOwner(ownerRaw);

      if(owner == null) {
         return repositoryDashboardService.getDashboardFolderSettings(user);
      }

      List<String> selected = readFolderOrder(owner);
      return RepositoryFolderDashboardSettingsModel.builder()
         .dashboards(selected)
         .permissions(null)
         .build();
   }

   /**
    * Resolves and hashes a plan. Performs no mutation, but DOES perform live reads: the current
    * dashboard/folder state for every touched unit.
    *
    * @throws IllegalArgumentException with a field-named message on a blank task, an empty change
    *         list, an unrecognized unitType/verb combination, a malformed viewsheet identifier, an
    *         unused field for the resolved unitType/verb, a unit that does/does not already exist
    *         as the verb requires, or a duplicate/unknown name in a folder reorder.
    */
   public ResolvedPlan resolve(DashboardChangePlanRequest req, Principal user) throws Exception {
      if(req == null || req.getTask() == null || req.getTask().trim().isEmpty()) {
         throw new IllegalArgumentException("task: a non-empty description is required");
      }

      if(req.getChanges() == null || req.getChanges().isEmpty()) {
         throw new IllegalArgumentException("changes: at least one change is required");
      }

      List<PlanChange> changes = new ArrayList<>();
      Set<String> seenKeys = new HashSet<>();
      int index = 0;

      for(DashboardChangeRequest change : req.getChanges()) {
         String label = "changes[" + index++ + "]";
         changes.add(resolveOne(label, change, user, seenKeys));
      }

      String task = req.getTask().trim();
      boolean requiresAgentSignoff = changes.stream()
         .anyMatch(c -> AdminChangeRecord.RISK_HIGH.equals(c.risk()));
      String planHash = hash(changes);
      return new ResolvedPlan(task, Collections.unmodifiableList(changes), true,
                              requiresAgentSignoff, planHash, TaskAuditToken.issue(planHash, task));
   }

   private PlanChange resolveOne(String label, DashboardChangeRequest change, Principal user,
                                 Set<String> seenKeys) throws Exception
   {
      if(change == null) {
         throw new IllegalArgumentException(label + ": must not be null");
      }

      String unitType = requireUnitType(label, change.getUnitType());

      if(DashboardChangeRequest.UNIT_DASHBOARD.equals(unitType)) {
         return resolveDashboard(label, change, user, seenKeys);
      }

      return resolveDashboardFolder(label, change, user, seenKeys);
   }

   // ---------------------------------------------------------------- dashboard

   private PlanChange resolveDashboard(String label, DashboardChangeRequest change, Principal user,
                                       Set<String> seenKeys) throws Exception
   {
      String verb = requireDashboardVerb(label, change.getVerb());
      IdentityID owner = parseOwner(change.getOwner());

      if(DashboardChangeRequest.VERB_CREATE.equals(verb)) {
         return resolveDashboardCreate(label, change, owner, seenKeys);
      }

      if(DashboardChangeRequest.VERB_UPDATE.equals(verb)) {
         return resolveDashboardUpdate(label, change, owner, seenKeys, user);
      }

      return resolveDashboardDelete(label, change, owner, seenKeys, user);
   }

   private PlanChange resolveDashboardCreate(String label, DashboardChangeRequest change,
                                             IdentityID owner, Set<String> seenKeys)
   {
      requireUnused(label, "oname", change.getOname());
      String name = requireNonBlank(label + ".name", change.getName());
      String viewsheet = requireNonBlank(label + ".viewsheet", change.getViewsheet());
      requireValidAssetIdShape(label + ".viewsheet", viewsheet);
      String registryName = fixDashboardName(name, owner);
      String key = dashboardKey(registryName, owner);

      if(!seenKeys.add(key)) {
         throw new IllegalArgumentException(
            label + ": duplicate entry for dashboard \"" + name + "\"; list each unit at most once");
      }

      if(registryFor(owner).getDashboard(registryName) != null) {
         throw new IllegalArgumentException(
            label + ".name: dashboard \"" + name + "\" already exists" + ownerSuffix(owner));
      }

      boolean enable = !Boolean.FALSE.equals(change.getEnable());
      String proposedProjection = projectDashboard(name, change.getDescription(), viewsheet, enable);
      String orgId = orgIdFor(owner);
      String description = "create dashboard \"" + name + "\"" + ownerSuffix(owner);
      return new PlanChange(key, orgId, null, proposedProjection, AdminChangeRecord.RISK_LOW,
                            AdminChangeRecord.SCOPE_STORAGE, true, description);
   }

   private PlanChange resolveDashboardUpdate(String label, DashboardChangeRequest change,
                                             IdentityID owner, Set<String> seenKeys, Principal user)
      throws Exception
   {
      String oname = requireNonBlank(label + ".oname", change.getOname());
      String registryOldName = fixDashboardName(oname, owner);
      String key = dashboardKey(registryOldName, owner);

      if(!seenKeys.add(key)) {
         throw new IllegalArgumentException(
            label + ": duplicate entry for dashboard \"" + oname + "\"; list each unit at most once");
      }

      DashboardRegistry registry = registryFor(owner);

      if(registry.getDashboard(registryOldName) == null) {
         throw new IllegalArgumentException(
            label + ".oname: no dashboard found named \"" + oname + "\"" + ownerSuffix(owner));
      }

      String name = change.getName();
      String newDescription = change.getDescription();
      String viewsheet = change.getViewsheet();
      Boolean enable = change.getEnable();
      requireAtLeastOneField(label, name, newDescription, viewsheet, enable);

      if(name != null) {
         requireNonBlank(label + ".name", name);
      }

      if(viewsheet != null) {
         requireValidAssetIdShape(label + ".viewsheet", viewsheet);
      }

      RepositoryDashboardSettingsModel current = repositoryDashboardService.getSettings(oname, owner, user);
      String newDisplayName = name != null ? name : oname;
      String newRegistryName = fixDashboardName(newDisplayName, owner);

      if(!newRegistryName.equals(registryOldName) && registry.getDashboard(newRegistryName) != null) {
         throw new IllegalArgumentException(
            label + ".name: dashboard \"" + newDisplayName + "\" already exists" + ownerSuffix(owner));
      }

      String mergedDescription = newDescription != null ? blankToNull(newDescription) : current.description();
      String mergedViewsheet = viewsheet != null ? viewsheet : current.viewsheet();
      boolean mergedEnable = enable != null ? enable : current.enable();

      String beforeProjection =
         projectDashboard(oname, current.description(), current.viewsheet(), current.enable());
      String proposedProjection =
         projectDashboard(newDisplayName, mergedDescription, mergedViewsheet, mergedEnable);
      boolean renamed = !newDisplayName.equals(oname);
      String risk = renamed ? AdminChangeRecord.RISK_HIGH : AdminChangeRecord.RISK_LOW;
      String orgId = orgIdFor(owner);
      String descriptionText = renamed
         ? "rename dashboard \"" + oname + "\" to \"" + newDisplayName + "\"" + ownerSuffix(owner)
         : "update dashboard \"" + oname + "\"" + ownerSuffix(owner);
      return new PlanChange(key, orgId, beforeProjection, proposedProjection, risk,
                            AdminChangeRecord.SCOPE_STORAGE, true, descriptionText);
   }

   private PlanChange resolveDashboardDelete(String label, DashboardChangeRequest change,
                                             IdentityID owner, Set<String> seenKeys, Principal user)
      throws Exception
   {
      requireUnused(label, "name", change.getName());
      requireUnused(label, "description", change.getDescription());
      requireUnused(label, "viewsheet", change.getViewsheet());
      requireUnused(label, "enable", change.getEnable());
      String oname = requireNonBlank(label + ".oname", change.getOname());
      String registryName = fixDashboardName(oname, owner);
      String key = dashboardKey(registryName, owner);

      if(!seenKeys.add(key)) {
         throw new IllegalArgumentException(
            label + ": duplicate entry for dashboard \"" + oname + "\"; list each unit at most once");
      }

      if(registryFor(owner).getDashboard(registryName) == null) {
         throw new IllegalArgumentException(
            label + ".oname: no dashboard found named \"" + oname + "\"" + ownerSuffix(owner));
      }

      RepositoryDashboardSettingsModel current = repositoryDashboardService.getSettings(oname, owner, user);
      // Includes a permissions fingerprint (unlike create/update's projectDashboard, which never
      // touches permissions): a delete's rollback DOES restore permissions (see
      // DashboardChangesetApplyService#rollbackDashboardDelete), so a permission change between
      // preview and apply should also count as plan drift, the same as a description/viewsheet/
      // enable change already does.
      String beforeProjection =
         projectDashboard(oname, current.description(), current.viewsheet(), current.enable()) +
         SEP + "permissions=" + canonicalOrMarker(String.valueOf(current.permissions()));
      String orgId = orgIdFor(owner);
      String description = "delete dashboard \"" + oname + "\"" + ownerSuffix(owner) +
         " -- this removes the registry binding (name/description/viewsheet reference) only; it " +
         "does not delete the bound viewsheet's own content, except in the narrow case where that " +
         "viewsheet is itself a composed dashboard created for this binding, which " +
         "RepositoryDashboardService's own delete already removes as well";
      return new PlanChange(key, orgId, beforeProjection, null, AdminChangeRecord.RISK_HIGH,
                            AdminChangeRecord.SCOPE_STORAGE, true, description);
   }

   // ---------------------------------------------------------------- dashboard folder

   private PlanChange resolveDashboardFolder(String label, DashboardChangeRequest change,
                                             Principal user, Set<String> seenKeys) throws Exception
   {
      requireFolderVerb(label, change.getVerb());
      IdentityID owner = parseOwner(change.getOwner());
      String key = "dashboardFolder:" + (owner == null ? "" : owner.convertToKey());

      if(!seenKeys.add(key)) {
         throw new IllegalArgumentException(
            label + ": duplicate entry for the dashboard folder" + ownerSuffix(owner));
      }

      List<String> requested = change.getDashboards();

      if(requested == null || requested.isEmpty()) {
         throw new IllegalArgumentException(label + ".dashboards: at least one dashboard name is required");
      }

      requireNoDuplicates(label + ".dashboards", requested);
      Set<String> validNames = new HashSet<>(
         Arrays.asList(dashboardRegistryManager.getRegistry().getDashboardNames()));

      for(int i = 0; i < requested.size(); i++) {
         String name = requested.get(i);

         if(!validNames.contains(name)) {
            throw new IllegalArgumentException(
               label + ".dashboards[" + i + "]: \"" + name + "\" is not a known dashboard");
         }
      }

      List<String> current = owner == null
         ? repositoryDashboardService.getDashboardFolderSettings(user).dashboards()
         : readFolderOrder(owner);
      String beforeProjection = String.join("|", current);
      String proposedProjection = String.join("|", requested);
      String orgId = orgIdFor(owner);
      String description = "reorder the dashboard folder" + ownerSuffix(owner);
      return new PlanChange(key, orgId, beforeProjection, proposedProjection, AdminChangeRecord.RISK_LOW,
                            AdminChangeRecord.SCOPE_STORAGE, true, description);
   }

   /** The identity-agnostic read behind {@code get_portal_dashboard_folder} for an explicit owner
    * (see the class javadoc): the SAME algorithm {@code RepositoryDashboardService
    * .getDashboardFolderSettings} uses, generalized from "the calling principal's identity" to an
    * arbitrary {@code owner} identity. Package-visible so {@link DashboardChangesetApplyService}
    * reuses the same read at apply time. */
   List<String> readFolderOrder(IdentityID owner) {
      DashboardRegistry registry = dashboardRegistryManager.getRegistry();
      List<String> dashboardNames = Arrays.asList(registry.getDashboardNames());
      Identity identity = new DefaultIdentity(owner, Identity.USER);
      List<String> sortedDashboards = Arrays.asList(dashboardManager.getDashboards(identity));
      return dashboardNames.stream()
         .filter(sortedDashboards::contains)
         .sorted(Comparator.comparingInt(sortedDashboards::indexOf))
         .collect(Collectors.toList());
   }

   // ---------------------------------------------------------------- shared helpers

   DashboardRegistry registryFor(IdentityID owner) {
      return owner != null ? dashboardRegistryManager.getRegistry(owner)
                           : dashboardRegistryManager.getRegistry();
   }

   /** Same wire format as the viewsheet/folder area's own {@code owner} field: a bare identity name
    * or a {@code name:orgId}-shaped key -- duplicated here rather than shared across areas, per this
    * repo's own established per-area convention (see {@code ViewsheetFolderService#parseOwner}). */
   static IdentityID parseOwner(String ownerRaw) {
      String trimmed = ownerRaw == null ? null : ownerRaw.trim();

      if(trimmed == null || trimmed.isEmpty()) {
         return null;
      }

      int delim = trimmed.indexOf(':');

      if(delim < 0) {
         return new IdentityID(trimmed, OrganizationManager.getInstance().getCurrentOrgID());
      }

      return new IdentityID(trimmed.substring(0, delim), trimmed.substring(delim + 1));
   }

   /** Same as {@code RepositoryDashboardService#fixDashboardName}: a global dashboard's registry
    * key always carries an internal {@code "__GLOBAL"} suffix that the wire-level {@code name}/
    * {@code oname} do NOT strip (this mirrors {@code RepositoryDashboardSettingsModel}'s own real
    * behavior exactly -- the suffix is genuinely visible on the wire today, not stripped for
    * display, per this area's "mirror StyleBI, don't invent" doctrine). Idempotent, so safe to
    * apply to an already-suffixed name. */
   static String fixDashboardName(String name, IdentityID owner) {
      if(name != null && owner == null && !name.endsWith("__GLOBAL")) {
         return name + "__GLOBAL";
      }

      return name;
   }

   private static String dashboardKey(String registryName, IdentityID owner) {
      return "dashboard:" + (owner == null ? "" : owner.convertToKey()) + ":" + registryName;
   }

   private static String orgIdFor(IdentityID owner) {
      return owner != null ? owner.getOrgID() : OrganizationManager.getInstance().getCurrentOrgID();
   }

   private static String ownerSuffix(IdentityID owner) {
      return owner == null ? " (global)" : " (owner: " + owner.convertToKey() + ")";
   }

   static String requireUnitType(String label, String unitType) {
      String trimmed = trimOrEmpty(unitType);

      if(DashboardChangeRequest.UNIT_DASHBOARD.equalsIgnoreCase(trimmed)) {
         return DashboardChangeRequest.UNIT_DASHBOARD;
      }

      if(DashboardChangeRequest.UNIT_DASHBOARD_FOLDER.equalsIgnoreCase(trimmed)) {
         return DashboardChangeRequest.UNIT_DASHBOARD_FOLDER;
      }

      throw new IllegalArgumentException(
         label + ".unitType: must be exactly \"dashboard\" or \"dashboardFolder\", got " +
         String.valueOf(unitType));
   }

   static String requireDashboardVerb(String label, String verb) {
      String trimmed = trimOrEmpty(verb);

      if(DashboardChangeRequest.VERB_CREATE.equalsIgnoreCase(trimmed) || "add".equalsIgnoreCase(trimmed)) {
         return DashboardChangeRequest.VERB_CREATE;
      }

      if(DashboardChangeRequest.VERB_UPDATE.equalsIgnoreCase(trimmed)) {
         return DashboardChangeRequest.VERB_UPDATE;
      }

      if(DashboardChangeRequest.VERB_DELETE.equalsIgnoreCase(trimmed) || "remove".equalsIgnoreCase(trimmed)) {
         return DashboardChangeRequest.VERB_DELETE;
      }

      throw new IllegalArgumentException(
         label + ".verb: for unitType=dashboard, must be \"create\", \"update\", or \"delete\" " +
         "(\"add\"/\"remove\" accepted as aliases), got " + String.valueOf(verb));
   }

   static String requireFolderVerb(String label, String verb) {
      String trimmed = trimOrEmpty(verb);

      if(DashboardChangeRequest.VERB_REORDER.equalsIgnoreCase(trimmed) || "arrange".equalsIgnoreCase(trimmed)) {
         return DashboardChangeRequest.VERB_REORDER;
      }

      throw new IllegalArgumentException(
         label + ".verb: for unitType=dashboardFolder, must be \"reorder\" (\"arrange\" accepted " +
         "as an alias), got " + String.valueOf(verb));
   }

   private static void requireAtLeastOneField(String label, String name, String description,
                                              String viewsheet, Boolean enable)
   {
      if(name == null && description == null && viewsheet == null && enable == null) {
         throw new IllegalArgumentException(
            label + ": at least one of name/description/viewsheet/enable is required for " +
            "verb=\"update\"");
      }
   }

   private static void requireNoDuplicates(String label, List<String> values) {
      Set<String> seen = new HashSet<>();

      for(String value : values) {
         if(!seen.add(value)) {
            throw new IllegalArgumentException(
               label + ": duplicate entry \"" + value + "\" -- list each dashboard at most once");
         }
      }
   }

   private static void requireUnused(String label, String field, Object value) {
      if(value != null) {
         throw new IllegalArgumentException(
            label + "." + field + ": not used for this verb -- refused rather than silently ignored");
      }
   }

   private static void requireValidAssetIdShape(String label, String assetId) {
      AssetEntry entry;

      try {
         entry = AssetEntry.createAssetEntry(assetId);
      }
      catch(NumberFormatException | StringIndexOutOfBoundsException e) {
         throw new IllegalArgumentException(
            label + ": not a valid asset identifier (\"" + assetId + "\")");
      }

      if(entry == null) {
         throw new IllegalArgumentException(
            label + ": not a valid asset identifier (\"" + assetId + "\")");
      }
   }

   private static String requireNonBlank(String label, String value) {
      String trimmed = blankToNull(value);

      if(trimmed == null) {
         throw new IllegalArgumentException(label + ": required");
      }

      return trimmed;
   }

   private static String blankToNull(String value) {
      if(value == null) {
         return null;
      }

      String trimmed = value.trim();
      return trimmed.isEmpty() ? null : trimmed;
   }

   private static String trimOrEmpty(String value) {
      return value == null ? "" : value.trim();
   }

   /** A simple pipe-delimited projection of a dashboard's user-visible fields -- there is no
    * equivalent of the viewsheet area's richer {@code ViewsheetProjection} needed here, since a
    * dashboard has only these four scalar fields. */
   static String projectDashboard(String name, String description, String viewsheet, boolean enable) {
      return "name=" + name + SEP + "description=" + canonicalOrMarker(description) + SEP +
         "viewsheet=" + canonicalOrMarker(viewsheet) + SEP + "enable=" + enable;
   }

   /** SHA-256 over the canonical plan, same field-order/control-character contract as every other
    * area's own {@code hash} method. Deliberately excludes {@code task} -- see {@code
    * ViewsheetChangePlanService#hash}'s own javadoc for why. */
   private static String hash(List<PlanChange> changes) {
      StringBuilder canonical = new StringBuilder();

      for(PlanChange change : changes) {
         canonical.append(change.property()).append(SEP)
            .append(canonicalOrMarker(change.currentValue())).append(SEP)
            .append(canonicalOrMarker(change.proposedValue())).append(SEP)
            .append(change.risk()).append(SEP)
            .append(change.snapshotScope()).append(SEP);
      }

      try {
         byte[] digest = MessageDigest.getInstance("SHA-256")
            .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
         StringBuilder hex = new StringBuilder(digest.length * 2);

         for(byte b : digest) {
            hex.append(String.format("%02x", b));
         }

         return hex.toString();
      }
      catch(NoSuchAlgorithmException e) {
         throw new IllegalStateException("SHA-256 is required to hash a dashboard change plan", e);
      }
   }

   private static String canonicalOrMarker(String value) {
      return value == null ? NULL_MARKER : value;
   }

   private static final char SEP = (char) 0x1f;
   private static final String NULL_MARKER = String.valueOf((char) 0x01);
   private final RepositoryDashboardService repositoryDashboardService;
   private final DashboardRegistryManager dashboardRegistryManager;
   private final DashboardManager dashboardManager;
}
