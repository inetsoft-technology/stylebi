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
package inetsoft.web.admin.ai.viewsheet;

import inetsoft.web.admin.sheet.Sheet;
import inetsoft.web.admin.sheet.SheetList;
import inetsoft.web.admin.sheet.vs.ViewsheetService;
import inetsoft.web.admin.sheet.ws.WorksheetService;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.Organization;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetObject;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.sync.DependencyTool;
import inetsoft.util.Tool;
import inetsoft.util.audit.AdminChangeRecord;
import inetsoft.web.RecycleUtils;
import inetsoft.web.admin.ai.PlanChange;
import inetsoft.web.admin.ai.ResolvedPlan;
import inetsoft.web.admin.ai.TaskAuditToken;
import inetsoft.sree.security.ResourceAction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.util.*;

/**
 * Resolves a requested list of viewsheet/folder changes into a {@link ResolvedPlan} and hashes it
 * -- the viewsheets analog of {@code inetsoft.web.admin.ai.AdminChangePlanService} and (within this
 * run) {@code DataSourceChangePlanService}/{@code ProviderChangePlanService}/etc, replicated rather
 * than shared (01-spec.md section 6, carry-forward item 5).
 *
 * <p>The genuine novelty this area introduces (01-spec.md section 4/7, 03-reconcile.md): risk
 * varies WITHIN one plan by which verb an entry uses -- viewsheet verbs (rename/delete) are {@code
 * risk: high}. Of the folder verbs, create is source-verified metadata-only (section 0.4: does not
 * touch, move, or delete any viewsheet, since {@code RepletRegistry} is a metadata label layer
 * decoupled from {@code AssetRepository}'s own storage). Folder delete and rename do NOT share that
 * property: {@code RepletRegistry.removeFolder}'s own {@code REMOVE_FOLDER_EVENT} is listened to by
 * {@code RepletEngine}, whose handler recursively hard-deletes every viewsheet/worksheet actually
 * stored under the folder via {@code AbstractAssetEngine#removeFolder0}/{@code removeSheet0} --
 * permanently, with no recycle bin and no undo (bug #76469) -- so {@link #resolveFolderDelete
 * resolveFolderDelete} now walks the real {@link AssetRepository} contents of the target path
 * (mirroring {@link #resolveViewsheetDelete resolveViewsheetDelete}'s own dependency preflight) and
 * requires {@code force: true} whenever the folder is non-empty, classifying the entry {@code
 * RISK_HIGH} in that case; folder rename similarly recursively rewrites the path/asset-identifier of
 * every viewsheet and worksheet stored under the renamed folder (including nested folders), as
 * {@link #resolveFolderRename resolveFolderRename}'s own {@code PlanChange} description discloses,
 * though it is not yet force-gated the way delete now is (a known, separate gap, not fixed here).
 * No {@code RISK_MEDIUM} constant exists on {@link AdminChangeRecord} (only {@code RISK_LOW}/{@code
 * RISK_HIGH}) -- folder create and an empty-folder delete use {@code RISK_LOW}, the closest existing
 * constant for a genuinely metadata-only change; a non-empty-folder delete uses {@code RISK_HIGH},
 * matching viewsheet delete's own classification for the same reason (a real, irreversible
 * AssetRepository deletion). {@code requiresStorageBackup} is {@code true} unconditionally for every
 * verb regardless of risk (snapshotScope is independent of risk, AREA-SPEC-GUIDE.md section 2.3).
 *
 * <p>Delete's own dependency preflight (section 0.2) reads {@code
 * DependencyTool.getDependencies}, the canonical reverse-dependency index, rather than {@code
 * assetRepository.getSheetDependencies} -- the latter is stale by a confirmed folder-rename-cascade
 * defect in core StyleBI (Redmine #76459 Part 2; see {@link #findDependencies}).
 */
@Component
public class ViewsheetChangePlanService {
   @Autowired
   public ViewsheetChangePlanService(ViewsheetService viewsheetApiService,
                                     ViewsheetFolderService folderService,
                                     AssetRepository assetRepository,
                                     WorksheetService worksheetApiService)
   {
      this.viewsheetApiService = viewsheetApiService;
      this.folderService = folderService;
      this.assetRepository = assetRepository;
      this.worksheetApiService = worksheetApiService;
   }

   /**
    * Resolves and hashes a plan. Performs no mutation, but DOES perform live reads: {@code
    * getViewsheets} for every touched viewsheet, {@code get_viewsheet_folder} for every touched
    * folder, and (for a viewsheet delete) a live dependency-graph read via {@code
    * DependencyTool.getDependencies}.
    *
    * @throws IllegalArgumentException with a field-named message on a blank task, an empty change
    *                                 list, an unrecognized unitType/verb combination, a malformed
    *                                 assetId, an unused field for the resolved unitType/verb, a
    *                                 unit that does/does not already exist as the verb requires, or
    *                                 (for viewsheet delete) unresolved dependencies without
    *                                 {@code force: true}.
    */
   public ResolvedPlan resolve(ViewsheetChangePlanRequest req, Principal user) throws Exception {
      if(req == null || req.getTask() == null || req.getTask().trim().isEmpty()) {
         throw new IllegalArgumentException("task: a non-empty description is required");
      }

      if(req.getChanges() == null || req.getChanges().isEmpty()) {
         throw new IllegalArgumentException("changes: at least one change is required");
      }

      List<PlanChange> changes = new ArrayList<>();
      Set<String> seenKeys = new HashSet<>();
      Map<String, String> auxProjections = new LinkedHashMap<>();
      int index = 0;

      for(ViewsheetChangeRequest change : req.getChanges()) {
         String label = "changes[" + index++ + "]";
         changes.add(resolveOne(label, change, user, seenKeys, auxProjections));
      }

      String task = req.getTask().trim();
      boolean requiresAgentSignoff = changes.stream()
         .anyMatch(c -> AdminChangeRecord.RISK_HIGH.equals(c.risk()));
      String planHash = hash(changes, auxProjections);
      return new ResolvedPlan(task, Collections.unmodifiableList(changes), true,
                              requiresAgentSignoff, planHash, TaskAuditToken.issue(planHash, task));
   }

   private PlanChange resolveOne(String label, ViewsheetChangeRequest change, Principal user,
                                 Set<String> seenKeys, Map<String, String> auxProjections)
      throws Exception
   {
      if(change == null) {
         throw new IllegalArgumentException(label + ": must not be null");
      }

      String unitType = requireUnitType(label, change.getUnitType());

      if(ViewsheetChangeRequest.UNIT_VIEWSHEET.equals(unitType)) {
         return resolveViewsheet(label, change, user, seenKeys, auxProjections);
      }

      if(ViewsheetChangeRequest.UNIT_WORKSHEET.equals(unitType)) {
         return resolveWorksheet(label, change, user, seenKeys, auxProjections);
      }

      return resolveFolder(label, change, user, seenKeys, auxProjections);
   }

   // ---------------------------------------------------------------- viewsheet

   private PlanChange resolveViewsheet(String label, ViewsheetChangeRequest change, Principal user,
                                       Set<String> seenKeys, Map<String, String> auxProjections)
      throws Exception
   {
      String verb = requireViewsheetVerb(label, change.getVerb());
      String assetId = requireNonBlank(label + ".assetId", change.getAssetId());
      requireValidAssetIdShape(label, assetId);
      requireAssetIdMatchesUnitType(label, assetId, ViewsheetChangeRequest.UNIT_VIEWSHEET);
      String key = "viewsheet:" + assetId;

      if(!seenKeys.add(key)) {
         throw new IllegalArgumentException(
            label + ": duplicate entry for viewsheet \"" + assetId +
            "\"; list each unit at most once");
      }

      Sheet current = findViewsheetById(user, assetId);

      if(ViewsheetChangeRequest.VERB_RENAME.equals(verb)) {
         return resolveViewsheetRename(label, key, assetId, current, change);
      }

      if(ViewsheetChangeRequest.VERB_UPDATE.equals(verb)) {
         return resolveViewsheetUpdate(label, key, assetId, current, change, user);
      }

      return resolveViewsheetDelete(label, key, assetId, current, change, user, auxProjections);
   }

   /** Filters a fresh {@code getViewsheets} read by asset identifier (section 2's durable,
    * deterministic identity -- no id-cache-eviction failure mode analogous to data sources', so a
    * plain linear filter is sufficient and correct). Package-visible so {@link
    * ViewsheetChangesetApplyService} reuses the SAME lookup at apply time. */
   Sheet findViewsheetById(Principal user, String assetId) throws Exception {
      SheetList list = viewsheetApiService.getViewsheets(user);

      if(list.getSheets() == null) {
         return null;
      }

      for(Sheet s : list.getSheets()) {
         if(assetId.equals(s.getAsset())) {
            return s;
         }
      }

      return null;
   }

   /** Filters a fresh {@code getViewsheets} read by path/scope/owner -- used after a rename
    * succeeds to find the entry's NEW identity (section 2's identity-changes-on-rename finding;
    * section 6). Package-visible for the same apply-time reuse reason as {@link
    * #findViewsheetById}. */
   Sheet findViewsheetByLocation(Principal user, String path, boolean global, IdentityID owner)
      throws Exception
   {
      SheetList list = viewsheetApiService.getViewsheets(user);

      if(list.getSheets() == null) {
         return null;
      }

      for(Sheet s : list.getSheets()) {
         if(path.equals(s.getPath()) && global == s.isGlobal() && Objects.equals(owner, s.getUser())) {
            return s;
         }
      }

      return null;
   }

   private PlanChange resolveViewsheetRename(String label, String key, String assetId,
                                             Sheet current, ViewsheetChangeRequest change)
      throws Exception
   {
      requireUnused(label, "force", change.getForce());
      requireUnused(label, "parentFolder", change.getParentFolder());
      requireUnused(label, "folderName", change.getFolderName());
      requireUnused(label, "path", change.getPath());
      requireUnused(label, "oldPath", change.getOldPath());
      requireUnused(label, "alias", change.getAlias());
      requireUnused(label, "description", change.getDescription());

      Boolean global = change.getGlobal();

      if(global == null) {
         throw new IllegalArgumentException(label + ".global: required for verb=rename");
      }

      String ownerRaw = blankToNull(change.getOwner());

      if(global) {
         if(ownerRaw != null) {
            throw new IllegalArgumentException(
               label + ".owner: not used when global=true; remove it or set global=false");
         }
      }
      else if(ownerRaw == null) {
         throw new IllegalArgumentException(label + ".owner: required when global=false");
      }

      IdentityID owner = global ? null : ViewsheetFolderService.parseOwner(ownerRaw);
      String newPath = requireNonBlank(label + ".newPath", change.getNewPath());

      if(current == null) {
         throw new IllegalArgumentException(
            label + ".assetId: no viewsheet found with this asset identifier -- it may not exist, " +
            "or may already have been deleted/renamed");
      }

      String beforeProjection = ViewsheetProjection.projectViewsheet(current);
      String proposedProjection = ViewsheetProjection.projectProposedRename(newPath, global, owner);
      String orgId = AssetEntry.createAssetEntry(assetId).getOrgID();
      String description = "rename viewsheet \"" + current.getPath() + "\" to \"" + newPath + "\"" +
         (global ? " (global)" : " (owner: " + owner.convertToKey() + ")");
      return new PlanChange(key, orgId, beforeProjection, proposedProjection,
                            AdminChangeRecord.RISK_HIGH, AdminChangeRecord.SCOPE_STORAGE, true,
                            description);
   }

   private PlanChange resolveViewsheetDelete(String label, String key, String assetId,
                                             Sheet current, ViewsheetChangeRequest change,
                                             Principal user, Map<String, String> auxProjections)
      throws Exception
   {
      requireUnused(label, "newPath", change.getNewPath());
      requireUnused(label, "global", change.getGlobal());
      requireUnused(label, "owner", change.getOwner());
      requireUnused(label, "parentFolder", change.getParentFolder());
      requireUnused(label, "folderName", change.getFolderName());
      requireUnused(label, "path", change.getPath());
      requireUnused(label, "oldPath", change.getOldPath());
      requireUnused(label, "alias", change.getAlias());
      requireUnused(label, "description", change.getDescription());

      if(current == null) {
         throw new IllegalArgumentException(
            label + ".assetId: no viewsheet found with this asset identifier -- it may not exist, " +
            "or may already have been deleted");
      }

      boolean force = Boolean.TRUE.equals(change.getForce());
      AssetEntry entry = AssetEntry.createAssetEntry(assetId);
      AssetEntry[] dependencies = findDependencies(entry, user);
      requireForceIfDependent(label, assetId, dependencies, force);

      String beforeProjection = ViewsheetProjection.projectViewsheet(current);
      auxProjections.put(key, ViewsheetProjection.projectDependencies(dependencies));
      String description = dependencies.length == 0
         ? "delete viewsheet \"" + current.getPath() + "\""
         : "delete viewsheet \"" + current.getPath() + "\" (force: true, " + dependencies.length +
           " dependent asset(s))";
      return new PlanChange(key, entry.getOrgID(), beforeProjection, null,
                            AdminChangeRecord.RISK_HIGH, AdminChangeRecord.SCOPE_STORAGE, true,
                            description);
   }

   /** Track B/Redmine #76604 Gap2: {@code verb: "update"} for a viewsheet -- alias/description
    * only, RISK_LOW, fully compensable (03-reconcile.md's central finding: {@code
    * RepletRegistryService.updateSheet(id, null, null, alias, description, principal)} never
    * touches the viewsheet's own path/identifier). Shared merge semantics with {@link
    * #resolveWorksheetUpdate}/{@link #resolveFolderUpdate}: a field omitted on the wire ({@code
    * null}) means leave unchanged; an explicit empty string clears it -- enforced by the plugin's
    * own normalizer, applied here via a read-before-write against the CURRENT stored value. */
   private PlanChange resolveViewsheetUpdate(String label, String key, String assetId, Sheet current,
                                             ViewsheetChangeRequest change, Principal user)
      throws Exception
   {
      requireUnused(label, "newPath", change.getNewPath());
      requireUnused(label, "global", change.getGlobal());
      requireUnused(label, "owner", change.getOwner());
      requireUnused(label, "force", change.getForce());
      requireUnused(label, "parentFolder", change.getParentFolder());
      requireUnused(label, "folderName", change.getFolderName());
      requireUnused(label, "path", change.getPath());
      requireUnused(label, "oldPath", change.getOldPath());

      String alias = change.getAlias();
      String description = change.getDescription();
      requireAtLeastOneMetadataField(label, alias, description);

      if(current == null) {
         throw new IllegalArgumentException(
            label + ".assetId: no viewsheet found with this asset identifier -- it may not exist, " +
            "or may already have been deleted/renamed");
      }

      ViewsheetService.Metadata currentMetadata =
         viewsheetApiService.getViewsheetMetadata(assetId, user);
      String mergedAlias = alias != null ? alias : currentMetadata.alias();
      String mergedDescription = description != null ? description : currentMetadata.description();
      String beforeProjection = ViewsheetProjection.projectViewsheetUpdate(
         current, currentMetadata.alias(), currentMetadata.description());
      String proposedProjection =
         ViewsheetProjection.projectViewsheetUpdate(current, mergedAlias, mergedDescription);
      String orgId = AssetEntry.createAssetEntry(assetId).getOrgID();
      String descriptionText = "update viewsheet \"" + current.getPath() + "\" alias/description";
      return new PlanChange(key, orgId, beforeProjection, proposedProjection,
                            AdminChangeRecord.RISK_LOW, AdminChangeRecord.SCOPE_STORAGE, true,
                            descriptionText);
   }

   // ---------------------------------------------------------------- worksheet

   private PlanChange resolveWorksheet(String label, ViewsheetChangeRequest change, Principal user,
                                       Set<String> seenKeys, Map<String, String> auxProjections)
      throws Exception
   {
      String verb = requireViewsheetVerb(label, change.getVerb());
      String assetId = requireNonBlank(label + ".assetId", change.getAssetId());
      requireValidAssetIdShape(label, assetId);
      requireAssetIdMatchesUnitType(label, assetId, ViewsheetChangeRequest.UNIT_WORKSHEET);
      String key = "worksheet:" + assetId;

      if(!seenKeys.add(key)) {
         throw new IllegalArgumentException(
            label + ": duplicate entry for worksheet \"" + assetId +
            "\"; list each unit at most once");
      }

      Sheet current = findWorksheetById(user, assetId);

      if(ViewsheetChangeRequest.VERB_RENAME.equals(verb)) {
         return resolveWorksheetRename(label, key, assetId, current, change);
      }

      if(ViewsheetChangeRequest.VERB_UPDATE.equals(verb)) {
         return resolveWorksheetUpdate(label, key, assetId, current, change, user);
      }

      return resolveWorksheetDelete(label, key, assetId, current, change, user, auxProjections);
   }

   /** Section 4.3/R3 (Track B): {@code WorksheetService.getWorksheets} does not filter
    * recycle-bin entries or replicate the {@code security.exposedefaultorgtoall} host-org
    * visibility branch the way {@code ViewsheetService.getViewsheets} already does -- applied
    * here, at the wiz tier, for BOTH {@code list_worksheets} and this area's own worksheet
    * resolution ({@link #findWorksheetById}/{@link #findWorksheetByLocation}), rather than
    * touching the shared {@code WorksheetService} (it also backs the real
    * {@code WorksheetApiController} Public API endpoint -- a bigger blast radius than this cut
    * intends). Package-visible so {@link AdminViewsheetController} reuses it for {@code
    * list_worksheets}. */
   SheetList getFilteredWorksheets(Principal user) throws Exception {
      SheetList raw = worksheetApiService.getWorksheets(user);
      SheetList filtered = new SheetList();

      if(raw.getSheets() != null) {
         for(Sheet s : raw.getSheets()) {
            if(!isRecycleBinPath(s.getPath())) {
               filtered.getSheets().add(s);
            }
         }
      }

      String hostOrgId = Organization.getDefaultOrganizationID();
      String principalOrgId = user instanceof XPrincipal ? ((XPrincipal) user).getOrgId() : null;

      if(SUtil.isDefaultVSGloballyVisible(user) && !Tool.equals(principalOrgId, hostOrgId)) {
         AssetEntry hostRoot = new AssetEntry(
            AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.REPOSITORY_FOLDER, "/", null, hostOrgId);
         AssetEntry[] hostEntries = assetRepository.getAllEntries(
            hostRoot, user, ResourceAction.READ, new AssetEntry.Selector(AssetEntry.Type.WORKSHEET));

         for(AssetEntry entry : hostEntries) {
            if(!isRecycleBinPath(entry.getPath())) {
               filtered.getSheets().add(new Sheet(
                  entry.toIdentifier(), entry.getPath(), entry.toView(),
                  entry.getScope() == AssetRepository.GLOBAL_SCOPE, entry.getUser(), null));
            }
         }
      }

      return filtered;
   }

   private static boolean isRecycleBinPath(String path) {
      return path != null && path.startsWith(RecycleUtils.RECYCLE_BIN_FOLDER + "/");
   }

   /** Same reasoning as {@link #findViewsheetById}, over the filtered worksheet list. */
   Sheet findWorksheetById(Principal user, String assetId) throws Exception {
      SheetList list = getFilteredWorksheets(user);

      if(list.getSheets() == null) {
         return null;
      }

      for(Sheet s : list.getSheets()) {
         if(assetId.equals(s.getAsset())) {
            return s;
         }
      }

      return null;
   }

   /** Same reasoning as {@link #findViewsheetByLocation}, over the filtered worksheet list. */
   Sheet findWorksheetByLocation(Principal user, String path, boolean global, IdentityID owner)
      throws Exception
   {
      SheetList list = getFilteredWorksheets(user);

      if(list.getSheets() == null) {
         return null;
      }

      for(Sheet s : list.getSheets()) {
         if(path.equals(s.getPath()) && global == s.isGlobal() && Objects.equals(owner, s.getUser())) {
            return s;
         }
      }

      return null;
   }

   private PlanChange resolveWorksheetRename(String label, String key, String assetId,
                                             Sheet current, ViewsheetChangeRequest change)
      throws Exception
   {
      requireUnused(label, "force", change.getForce());
      requireUnused(label, "parentFolder", change.getParentFolder());
      requireUnused(label, "folderName", change.getFolderName());
      requireUnused(label, "path", change.getPath());
      requireUnused(label, "oldPath", change.getOldPath());
      requireUnused(label, "alias", change.getAlias());
      requireUnused(label, "description", change.getDescription());

      Boolean global = change.getGlobal();

      if(global == null) {
         throw new IllegalArgumentException(label + ".global: required for verb=rename");
      }

      String ownerRaw = blankToNull(change.getOwner());

      if(global) {
         if(ownerRaw != null) {
            throw new IllegalArgumentException(
               label + ".owner: not used when global=true; remove it or set global=false");
         }
      }
      else if(ownerRaw == null) {
         throw new IllegalArgumentException(label + ".owner: required when global=false");
      }

      IdentityID owner = global ? null : ViewsheetFolderService.parseOwner(ownerRaw);
      String newPath = requireNonBlank(label + ".newPath", change.getNewPath());

      if(current == null) {
         throw new IllegalArgumentException(
            label + ".assetId: no worksheet found with this asset identifier -- it may not exist, " +
            "or may already have been deleted/renamed");
      }

      String beforeProjection = ViewsheetProjection.projectWorksheet(current);
      String proposedProjection =
         ViewsheetProjection.projectProposedWorksheetRename(newPath, global, owner);
      String orgId = AssetEntry.createAssetEntry(assetId).getOrgID();
      String description = "rename worksheet \"" + current.getPath() + "\" to \"" + newPath + "\"" +
         (global ? " (global)" : " (owner: " + owner.convertToKey() + ")");
      return new PlanChange(key, orgId, beforeProjection, proposedProjection,
                            AdminChangeRecord.RISK_HIGH, AdminChangeRecord.SCOPE_STORAGE, true,
                            description);
   }

   /** Section 4.5 (Track B): a worksheet is commonly the data-binding source for one or more
    * viewsheets, so the SAME dependency preflight {@link #resolveViewsheetDelete} runs applies
    * here at least as much -- arguably more, since silently orphaning a viewsheet's data binding
    * is a worse failure mode than deleting an unreferenced dashboard. */
   private PlanChange resolveWorksheetDelete(String label, String key, String assetId,
                                             Sheet current, ViewsheetChangeRequest change,
                                             Principal user, Map<String, String> auxProjections)
      throws Exception
   {
      requireUnused(label, "newPath", change.getNewPath());
      requireUnused(label, "global", change.getGlobal());
      requireUnused(label, "owner", change.getOwner());
      requireUnused(label, "parentFolder", change.getParentFolder());
      requireUnused(label, "folderName", change.getFolderName());
      requireUnused(label, "path", change.getPath());
      requireUnused(label, "oldPath", change.getOldPath());
      requireUnused(label, "alias", change.getAlias());
      requireUnused(label, "description", change.getDescription());

      if(current == null) {
         throw new IllegalArgumentException(
            label + ".assetId: no worksheet found with this asset identifier -- it may not exist, " +
            "or may already have been deleted");
      }

      boolean force = Boolean.TRUE.equals(change.getForce());
      AssetEntry entry = AssetEntry.createAssetEntry(assetId);
      AssetEntry[] dependencies = findDependencies(entry, user);
      requireForceIfDependent(label, assetId, dependencies, force);

      String beforeProjection = ViewsheetProjection.projectWorksheet(current);
      auxProjections.put(key, ViewsheetProjection.projectDependencies(dependencies));
      String description = dependencies.length == 0
         ? "delete worksheet \"" + current.getPath() + "\""
         : "delete worksheet \"" + current.getPath() + "\" (force: true, " + dependencies.length +
           " dependent asset(s))";
      return new PlanChange(key, entry.getOrgID(), beforeProjection, null,
                            AdminChangeRecord.RISK_HIGH, AdminChangeRecord.SCOPE_STORAGE, true,
                            description);
   }

   /** Same merge semantics as {@link #resolveViewsheetUpdate}. */
   private PlanChange resolveWorksheetUpdate(String label, String key, String assetId, Sheet current,
                                             ViewsheetChangeRequest change, Principal user)
      throws Exception
   {
      requireUnused(label, "newPath", change.getNewPath());
      requireUnused(label, "global", change.getGlobal());
      requireUnused(label, "owner", change.getOwner());
      requireUnused(label, "force", change.getForce());
      requireUnused(label, "parentFolder", change.getParentFolder());
      requireUnused(label, "folderName", change.getFolderName());
      requireUnused(label, "path", change.getPath());
      requireUnused(label, "oldPath", change.getOldPath());

      String alias = change.getAlias();
      String description = change.getDescription();
      requireAtLeastOneMetadataField(label, alias, description);

      if(current == null) {
         throw new IllegalArgumentException(
            label + ".assetId: no worksheet found with this asset identifier -- it may not exist, " +
            "or may already have been deleted/renamed");
      }

      WorksheetService.Metadata currentMetadata =
         worksheetApiService.getWorksheetSettingsMetadata(assetId, user);
      String mergedAlias = alias != null ? alias : currentMetadata.alias();
      String mergedDescription = description != null ? description : currentMetadata.description();
      String beforeProjection = ViewsheetProjection.projectWorksheetUpdate(
         current, currentMetadata.alias(), currentMetadata.description());
      String proposedProjection =
         ViewsheetProjection.projectWorksheetUpdate(current, mergedAlias, mergedDescription);
      String orgId = AssetEntry.createAssetEntry(assetId).getOrgID();
      String descriptionText = "update worksheet \"" + current.getPath() + "\" alias/description";
      return new PlanChange(key, orgId, beforeProjection, proposedProjection,
                            AdminChangeRecord.RISK_LOW, AdminChangeRecord.SCOPE_STORAGE, true,
                            descriptionText);
   }

   /** Section 0.2: reads the canonical reverse-dependency index ({@code DependencyStorageService},
    * via {@code DependencyTool.getDependencies}) rather than {@code
    * assetRepository.getSheetDependencies} -- the latter (Store #1, {@code AbstractSheet.dependencies})
    * is stale by a confirmed folder-rename-cascade defect in {@code
    * AbstractAssetEngine.changeSheetDependents} (Redmine #76459 Part 2; not fixed here, out of scope
    * for this plugin-support service -- see 03-fix.md). {@code DependencyTool.getDependencies} is
    * the established pattern for this exact preflight ({@code
    * DataSourceChangePlanService.findDependencies}, {@code
    * RepositoryObjectService.checkAssetEntryDependencies}) and, like those callers, is used
    * unfiltered here. Package-visible so {@link ViewsheetChangesetApplyService} re-runs it at apply
    * time (a concurrent change between preview and apply could add a new dependency). */
   AssetEntry[] findDependencies(AssetEntry entry, Principal user) throws Exception {
      List<AssetObject> raw = DependencyTool.getDependencies(entry.toIdentifier());
      return raw.stream()
         .filter(AssetEntry.class::isInstance)
         .map(AssetEntry.class::cast)
         .toArray(AssetEntry[]::new);
   }

   /** Package-visible for the same apply-time re-check reason as {@link #findDependencies}. */
   static void requireForceIfDependent(String label, String assetId, AssetEntry[] dependencies,
                                       boolean force)
   {
      if(dependencies.length > 0 && !force) {
         throw new IllegalArgumentException(
            label + ": viewsheet \"" + assetId + "\" is referenced by " + dependencies.length +
            " other asset(s) (" + ViewsheetProjection.projectDependencies(dependencies) + ") -- " +
            "refusing to delete without force: true (section 0.2: the wrapped Public API's own " +
            "deleteViewsheet has no way to opt into this check at all -- this area's own preflight " +
            "does)");
      }
   }

   // ---------------------------------------------------------------- folder

   private PlanChange resolveFolder(String label, ViewsheetChangeRequest change, Principal user,
                                    Set<String> seenKeys, Map<String, String> auxProjections)
      throws Exception
   {
      String verb = requireFolderVerb(label, change.getVerb());
      IdentityID owner = resolveOwner(change.getOwner());

      if(ViewsheetChangeRequest.VERB_CREATE.equals(verb)) {
         return resolveFolderCreate(label, change, owner, seenKeys);
      }
      else if(ViewsheetChangeRequest.VERB_DELETE.equals(verb)) {
         return resolveFolderDelete(label, change, owner, user, seenKeys, auxProjections);
      }
      else if(ViewsheetChangeRequest.VERB_UPDATE.equals(verb)) {
         return resolveFolderUpdate(label, change, owner, seenKeys);
      }

      return resolveFolderRename(label, change, owner, seenKeys);
   }

   private static IdentityID resolveOwner(String ownerRaw) {
      return ViewsheetFolderService.parseOwner(ownerRaw);
   }

   private PlanChange resolveFolderCreate(String label, ViewsheetChangeRequest change,
                                          IdentityID owner, Set<String> seenKeys)
      throws Exception
   {
      requireUnused(label, "assetId", change.getAssetId());
      requireUnused(label, "newPath", change.getNewPath());
      requireUnused(label, "global", change.getGlobal());
      requireUnused(label, "force", change.getForce());
      requireUnused(label, "path", change.getPath());
      requireUnused(label, "oldPath", change.getOldPath());

      String folderName = requireNonBlank(label + ".folderName", change.getFolderName());
      String fullPath = ViewsheetFolderService.computeFolderFullPath(
         change.getParentFolder(), folderName, owner);
      String key = folderKey(fullPath, owner);

      if(!seenKeys.add(key)) {
         throw new IllegalArgumentException(
            label + ": duplicate entry for folder \"" + fullPath + "\"; list each unit at most once");
      }

      GetViewsheetFolderResult current = folderService.getFolder(fullPath, owner);

      if(current.found()) {
         throw new IllegalArgumentException(label + ": folder \"" + fullPath + "\" already exists");
      }

      // Track B/Redmine #76604 Gap5: RepletRegistryService.addRepositoryFolder already accepts
      // alias/description at creation time -- these are optional, may be null/omitted.
      GetViewsheetFolderResult proposed = new GetViewsheetFolderResult(
         true, fullPath, ViewsheetFolderService.ownerKey(owner), change.getAlias(),
         change.getDescription());
      String beforeProjection = ViewsheetProjection.projectFolder(current);
      String proposedProjection = ViewsheetProjection.projectFolder(proposed);
      String orgId = orgIdFor(owner);
      String description = "create folder \"" + fullPath + "\" -- this does not affect any " +
         "viewsheet: folders are a registry label layer, independent of viewsheet storage " +
         "(section 0.4/0.5)";
      return new PlanChange(key, orgId, beforeProjection, proposedProjection,
                            AdminChangeRecord.RISK_LOW, AdminChangeRecord.SCOPE_STORAGE, true,
                            description);
   }

   /** Track B/Redmine #76604 Gap2/Gap5: {@code verb: "update"} for a folder -- alias/description
    * only, RISK_LOW, fully compensable. Reuses the existing {@code GetViewsheetFolderResult}/
    * {@code projectFolder} shape verbatim (already carries alias/description, section 1.2) --
    * unlike viewsheet/worksheet, no new projection method was needed here. Same merge semantics
    * as {@link #resolveViewsheetUpdate}. */
   private PlanChange resolveFolderUpdate(String label, ViewsheetChangeRequest change,
                                          IdentityID owner, Set<String> seenKeys)
      throws Exception
   {
      requireUnused(label, "assetId", change.getAssetId());
      requireUnused(label, "newPath", change.getNewPath());
      requireUnused(label, "global", change.getGlobal());
      requireUnused(label, "force", change.getForce());
      requireUnused(label, "parentFolder", change.getParentFolder());
      requireUnused(label, "folderName", change.getFolderName());

      String rawPath = requireFolderTargetPath(label, change);
      String normalizedPath = ViewsheetFolderService.normalizeFolderPath(rawPath, owner);
      String key = folderKey(normalizedPath, owner);

      if(!seenKeys.add(key)) {
         throw new IllegalArgumentException(
            label + ": duplicate entry for folder \"" + normalizedPath +
            "\"; list each unit at most once");
      }

      String alias = change.getAlias();
      String description = change.getDescription();
      requireAtLeastOneMetadataField(label, alias, description);

      GetViewsheetFolderResult current = folderService.getFolder(normalizedPath, owner);

      if(!current.found()) {
         throw new IllegalArgumentException(
            label + ": folder \"" + normalizedPath + "\" does not exist");
      }

      String mergedAlias = alias != null ? alias : current.alias();
      String mergedDescription = description != null ? description : current.description();
      GetViewsheetFolderResult proposed = new GetViewsheetFolderResult(
         true, normalizedPath, ViewsheetFolderService.ownerKey(owner), mergedAlias,
         mergedDescription);
      String beforeProjection = ViewsheetProjection.projectFolder(current);
      String proposedProjection = ViewsheetProjection.projectFolder(proposed);
      String orgId = orgIdFor(owner);
      String descriptionText = "update folder \"" + normalizedPath + "\" alias/description";
      return new PlanChange(key, orgId, beforeProjection, proposedProjection,
                            AdminChangeRecord.RISK_LOW, AdminChangeRecord.SCOPE_STORAGE, true,
                            descriptionText);
   }

   private PlanChange resolveFolderDelete(String label, ViewsheetChangeRequest change,
                                          IdentityID owner, Principal user, Set<String> seenKeys,
                                          Map<String, String> auxProjections)
      throws Exception
   {
      requireUnused(label, "assetId", change.getAssetId());
      requireUnused(label, "newPath", change.getNewPath());
      requireUnused(label, "global", change.getGlobal());
      requireUnused(label, "parentFolder", change.getParentFolder());
      requireUnused(label, "folderName", change.getFolderName());
      requireUnused(label, "alias", change.getAlias());
      requireUnused(label, "description", change.getDescription());

      String rawPath = requireFolderTargetPath(label, change);
      String normalizedPath = ViewsheetFolderService.normalizeFolderPath(rawPath, owner);
      String key = folderKey(normalizedPath, owner);

      if(!seenKeys.add(key)) {
         throw new IllegalArgumentException(
            label + ": duplicate entry for folder \"" + normalizedPath +
            "\"; list each unit at most once");
      }

      GetViewsheetFolderResult current = folderService.getFolder(normalizedPath, owner);

      if(!current.found()) {
         throw new IllegalArgumentException(
            label + ": folder \"" + normalizedPath + "\" does not exist");
      }

      boolean force = Boolean.TRUE.equals(change.getForce());
      AssetEntry[] contents = findFolderContents(normalizedPath, owner, user);
      AssetEntry[] visibleContents = visibleFolderContents(contents, user);
      requireForceIfNonEmpty(label, normalizedPath, contents, visibleContents, force);

      String beforeProjection = ViewsheetProjection.projectFolder(current);
      auxProjections.put(key, ViewsheetProjection.projectDependencies(contents));
      String orgId = orgIdFor(owner);
      String risk;
      String description;

      if(contents.length == 0) {
         risk = AdminChangeRecord.RISK_LOW;
         description = "delete folder \"" + normalizedPath + "\" -- the folder currently contains " +
            "no viewsheet or worksheet, so this only removes the empty registry folder label";
      }
      else {
         risk = AdminChangeRecord.RISK_HIGH;
         description = "delete folder \"" + normalizedPath + "\" -- this PERMANENTLY AND " +
            "IRRECOVERABLY deletes " + contents.length + " contained viewsheet/worksheet(s) " +
            "(force: true), recursively including nested folders, with no recycle bin and no undo: " +
            "RepletRegistry.removeFolder's own REMOVE_FOLDER_EVENT is handled by RepletEngine, which " +
            "calls AbstractAssetEngine#removeFolder0/#removeSheet0 to hard-delete every contained " +
            "asset from storage (bug #76469) -- re-creating the folder afterward only restores an " +
            "empty label, never the deleted content: " +
            ViewsheetProjection.projectVisibleContents(contents, visibleContents);
      }

      return new PlanChange(key, orgId, beforeProjection, null, risk,
                            AdminChangeRecord.SCOPE_STORAGE, true, description);
   }

   /** Walks the real {@link AssetRepository} contents of a (registry-normalized) folder path,
    * recursively, for every viewsheet/worksheet actually stored under it -- the same content
    * {@code RepletEngine}'s {@code REMOVE_FOLDER_EVENT} listener destroys when the folder is
    * deleted (bug #76469; {@code RepletRegistry} itself is only a metadata label layer and cannot
    * answer this question on its own). Mirrors the scope/path conversion {@code RepletEngine}
    * itself uses when building the {@link AssetEntry} for a registry folder path: an owner-scoped
    * path carries a {@code "My Dashboards/"} prefix in the registry that {@code AssetRepository}'s
    * own {@code USER_SCOPE} entries do not. Package-visible so {@link ViewsheetChangesetApplyService}
    * re-runs it at apply time (a concurrent save into the folder between preview and apply could add
    * content), the same apply-time re-check reason as {@link #findDependencies}.
    *
    * <p>Bypasses per-child READ permission via {@link AssetRepository#IGNORE_PERM} (bug #76469
    * follow-up): the real delete cascade ({@code AbstractAssetEngine#removeFolder0}/{@code
    * removeSheet0}) does not check the calling principal's permission on individual children either,
    * so this safety-gate content count must answer "what will the cascade actually destroy," not
    * "what can this caller see" -- otherwise a viewsheet the caller lacks READ on, nested in an
    * otherwise-visible shared folder, would be silently excluded from the count, letting a non-empty
    * folder delete slip through as {@code RISK_LOW} with no {@code force}/
    * {@code acknowledgeIrreversibleDelete} required, while the cascade destroys it anyway.
    * {@code RepletRegistryService} uses this same thread-local for the same reason (its own
    * folder-content-removal walk, {@code RepletRegistryService.java:410-489}) -- an established
    * pattern, not a new bypass mechanism. */
   AssetEntry[] findFolderContents(String normalizedPath, IdentityID owner, Principal user)
      throws Exception
   {
      String assetPath = owner != null && normalizedPath.startsWith(Tool.MY_DASHBOARD + "/")
         ? normalizedPath.substring(Tool.MY_DASHBOARD.length() + 1) : normalizedPath;
      int scope = owner != null ? AssetRepository.USER_SCOPE : AssetRepository.GLOBAL_SCOPE;
      AssetEntry root = new AssetEntry(scope, AssetEntry.Type.REPOSITORY_FOLDER, assetPath, owner,
                                       orgIdFor(owner));
      AssetEntry[] entries;

      try {
         AssetRepository.IGNORE_PERM.set(true);
         entries = assetRepository.getAllEntries(root, user, ResourceAction.READ,
            new AssetEntry.Selector(AssetEntry.Type.VIEWSHEET, AssetEntry.Type.WORKSHEET));
      }
      finally {
         AssetRepository.IGNORE_PERM.remove();
      }

      return entries == null ? new AssetEntry[0] : entries;
   }

   /** Filters an {@link #findFolderContents} result down to the subset the calling principal can
    * actually see, for building caller-facing text (bug #76469 follow-up review finding 4): {@code
    * findFolderContents} itself must keep seeing every entry the delete cascade will destroy
    * (via {@link AssetRepository#IGNORE_PERM}), for an accurate count/risk decision, but that
    * bypassed array must never be rendered into a description or exception message verbatim, since
    * it can contain entries the caller has no READ permission on -- doing so would disclose the
    * existence and path of assets outside the caller's own visibility. Re-checks each entry via
    * {@link AssetRepository#checkAssetPermission} OUTSIDE the bypass (already cleared by the time
    * {@code findFolderContents} returns), i.e. the same check {@code getAllEntries} itself would
    * have applied had the bypass not been in effect. Package-visible for the same apply-time reuse
    * reason as {@link #findFolderContents}. */
   AssetEntry[] visibleFolderContents(AssetEntry[] contents, Principal user) {
      List<AssetEntry> visible = new ArrayList<>(contents.length);

      for(AssetEntry entry : contents) {
         try {
            assetRepository.checkAssetPermission(user, entry, ResourceAction.READ);
            visible.add(entry);
         }
         catch(Exception e) {
            // not visible to this caller -- excluded from the human-readable enumeration, but
            // still counted (via the untouched "contents" array) for gating/risk purposes
         }
      }

      return visible.toArray(new AssetEntry[0]);
   }

   /** Package-visible for the same apply-time re-check reason as {@link #requireForceIfDependent}.
    * {@code visibleContents} (see {@link #visibleFolderContents}) drives what's named in the
    * exception message; {@code contents.length} (the full, bypassed count) still drives the gate
    * itself, unaffected by the caller's own visibility (bug #76469 follow-up review finding 4). */
   static void requireForceIfNonEmpty(String label, String normalizedPath, AssetEntry[] contents,
                                      AssetEntry[] visibleContents, boolean force)
   {
      if(contents.length > 0 && !force) {
         throw new IllegalArgumentException(
            label + ": folder \"" + normalizedPath + "\" contains " + contents.length +
            " viewsheet/worksheet(s) (" +
            ViewsheetProjection.projectVisibleContents(contents, visibleContents) +
            ") -- refusing to delete without force: true (folder delete recursively and " +
            "permanently deletes every contained viewsheet/worksheet, with no recycle bin and no " +
            "undo; bug #76469)");
      }
   }

   private PlanChange resolveFolderRename(String label, ViewsheetChangeRequest change,
                                          IdentityID owner, Set<String> seenKeys)
      throws Exception
   {
      requireUnused(label, "assetId", change.getAssetId());
      requireUnused(label, "global", change.getGlobal());
      requireUnused(label, "force", change.getForce());
      requireUnused(label, "parentFolder", change.getParentFolder());
      requireUnused(label, "folderName", change.getFolderName());
      requireUnused(label, "alias", change.getAlias());
      requireUnused(label, "description", change.getDescription());

      String rawOldPath = requireFolderTargetPath(label, change);
      String rawNewPath = requireNonBlank(label + ".newPath", change.getNewPath());
      String normalizedOld = ViewsheetFolderService.normalizeFolderPath(rawOldPath, owner);
      String normalizedNew = ViewsheetFolderService.normalizeFolderPath(rawNewPath, owner);
      String key = folderKey(normalizedOld, owner);

      if(!seenKeys.add(key)) {
         throw new IllegalArgumentException(
            label + ": duplicate entry for folder \"" + normalizedOld +
            "\"; list each unit at most once");
      }

      GetViewsheetFolderResult current = folderService.getFolder(normalizedOld, owner);

      if(!current.found()) {
         throw new IllegalArgumentException(
            label + ": folder \"" + normalizedOld + "\" does not exist");
      }

      GetViewsheetFolderResult proposed = new GetViewsheetFolderResult(
         true, normalizedNew, ViewsheetFolderService.ownerKey(owner), current.alias(),
         current.description());
      String beforeProjection = ViewsheetProjection.projectFolder(current);
      String proposedProjection = ViewsheetProjection.projectFolder(proposed);
      String orgId = orgIdFor(owner);
      String description = "rename folder \"" + normalizedOld + "\" to \"" + normalizedNew +
         "\" -- this rewrites the path/asset-identifier of every viewsheet and worksheet " +
         "currently stored under this folder (recursively, including nested folders) to match " +
         "the new folder path; any external reference to those viewsheets by full path or " +
         "asset-id can go stale as a result";
      return new PlanChange(key, orgId, beforeProjection, proposedProjection,
                            AdminChangeRecord.RISK_LOW, AdminChangeRecord.SCOPE_STORAGE, true,
                            description);
   }

   /** Section 11: {@code path}/{@code oldPath} are aliases for the same concept (the wrapped
    * API's own {@code RemoveFolderRequest.path} vs {@code RenameFolderRequest.oldPath} naming
    * inconsistency) -- refuses loud if both are given and disagree, never silently prefers one.
    * Package-visible so {@link ViewsheetChangesetApplyService} reuses the same resolution. */
   static String requireFolderTargetPath(String label, ViewsheetChangeRequest change) {
      String path = blankToNull(change.getPath());
      String oldPath = blankToNull(change.getOldPath());

      if(path == null && oldPath == null) {
         throw new IllegalArgumentException(label + ".path: required (oldPath is also accepted)");
      }

      if(path != null && oldPath != null && !path.equals(oldPath)) {
         throw new IllegalArgumentException(
            label + ": path (\"" + path + "\") and oldPath (\"" + oldPath + "\") were both given " +
            "and disagree -- an ambiguous combined input is refused rather than silently resolved " +
            "one way");
      }

      return path != null ? path : oldPath;
   }

   private static String orgIdFor(IdentityID owner) {
      return owner != null ? owner.getOrgID() : OrganizationManager.getInstance().getCurrentOrgID();
   }

   private static String folderKey(String normalizedPath, IdentityID owner) {
      String ownerKey = ViewsheetFolderService.ownerKey(owner);
      return "folder:" + normalizedPath + (ownerKey == null ? "" : "|" + ownerKey);
   }

   // ---------------------------------------------------------------- shared helpers

   static String requireUnitType(String label, String unitType) {
      String trimmed = trimOrEmpty(unitType);

      if(ViewsheetChangeRequest.UNIT_VIEWSHEET.equalsIgnoreCase(trimmed)) {
         return ViewsheetChangeRequest.UNIT_VIEWSHEET;
      }

      if(ViewsheetChangeRequest.UNIT_FOLDER.equalsIgnoreCase(trimmed)) {
         return ViewsheetChangeRequest.UNIT_FOLDER;
      }

      if(ViewsheetChangeRequest.UNIT_WORKSHEET.equalsIgnoreCase(trimmed)) {
         return ViewsheetChangeRequest.UNIT_WORKSHEET;
      }

      throw new IllegalArgumentException(
         label + ".unitType: must be exactly \"viewsheet\", \"folder\", or \"worksheet\" (no " +
         "abbreviation alias accepted), got " + String.valueOf(unitType));
   }

   /** Shared by BOTH {@code unitType: "viewsheet"} and {@code unitType: "worksheet"} (Track B):
    * the underlying primitives are proven type-agnostic (03-reconcile.md), so the two unit types
    * share an identical verb set/alias set. */
   static String requireViewsheetVerb(String label, String verb) {
      String trimmed = trimOrEmpty(verb);

      if(ViewsheetChangeRequest.VERB_RENAME.equalsIgnoreCase(trimmed) ||
         "move".equalsIgnoreCase(trimmed))
      {
         return ViewsheetChangeRequest.VERB_RENAME;
      }

      if(ViewsheetChangeRequest.VERB_DELETE.equalsIgnoreCase(trimmed) ||
         "remove".equalsIgnoreCase(trimmed))
      {
         return ViewsheetChangeRequest.VERB_DELETE;
      }

      if(ViewsheetChangeRequest.VERB_UPDATE.equalsIgnoreCase(trimmed)) {
         return ViewsheetChangeRequest.VERB_UPDATE;
      }

      throw new IllegalArgumentException(
         label + ".verb: for this unitType, must be \"rename\", \"delete\", or \"update\" " +
         "(\"move\"/\"remove\" accepted as aliases for \"rename\"/\"delete\"), got " +
         String.valueOf(verb));
   }

   static String requireFolderVerb(String label, String verb) {
      String trimmed = trimOrEmpty(verb);

      if(ViewsheetChangeRequest.VERB_CREATE.equalsIgnoreCase(trimmed) ||
         "add".equalsIgnoreCase(trimmed))
      {
         return ViewsheetChangeRequest.VERB_CREATE;
      }

      if(ViewsheetChangeRequest.VERB_DELETE.equalsIgnoreCase(trimmed) ||
         "remove".equalsIgnoreCase(trimmed))
      {
         return ViewsheetChangeRequest.VERB_DELETE;
      }

      if(ViewsheetChangeRequest.VERB_RENAME.equalsIgnoreCase(trimmed)) {
         return ViewsheetChangeRequest.VERB_RENAME;
      }

      if(ViewsheetChangeRequest.VERB_UPDATE.equalsIgnoreCase(trimmed)) {
         return ViewsheetChangeRequest.VERB_UPDATE;
      }

      throw new IllegalArgumentException(
         label + ".verb: for unitType=folder, must be \"create\", \"delete\", \"rename\", or " +
         "\"update\" (\"add\"/\"remove\" accepted as aliases), got " + String.valueOf(verb));
   }

   /** Section 4.4 (Track B): once {@code unitType: "worksheet"} exists alongside {@code
    * unitType: "viewsheet"}, a copy-pasted wrong assetId (plausible: both unit types are returned
    * by sibling list tools with the same shape) would otherwise reach a plain not-found lookup --
    * refused loud here instead, naming the actual mismatch, before that lookup ever runs (this
    * repo's own CLAUDE.md tool-misuse doctrine). Must run AFTER {@link #requireValidAssetIdShape}
    * so {@code AssetEntry.createAssetEntry} is already known not to throw. */
   private static void requireAssetIdMatchesUnitType(String label, String assetId, String unitType) {
      AssetEntry.Type expected = ViewsheetChangeRequest.UNIT_WORKSHEET.equals(unitType)
         ? AssetEntry.Type.WORKSHEET : AssetEntry.Type.VIEWSHEET;
      AssetEntry.Type actual = AssetEntry.createAssetEntry(assetId).getType();

      if(actual != expected) {
         String actualUnit = actual == AssetEntry.Type.WORKSHEET ? "worksheet"
            : actual == AssetEntry.Type.VIEWSHEET ? "viewsheet" : actual.toString();
         throw new IllegalArgumentException(
            label + ".assetId: \"" + assetId + "\" identifies a " + actualUnit + ", not a " +
            unitType + " -- use unitType: \"" + actualUnit + "\" instead");
      }
   }

   /** Shared by every {@code verb: "update"} resolver (viewsheet/worksheet/folder, Track B):
    * update has nothing to do if neither field is given. */
   private static void requireAtLeastOneMetadataField(String label, String alias, String description) {
      if(alias == null && description == null) {
         throw new IllegalArgumentException(
            label + ": at least one of alias/description is required for verb=\"update\"");
      }
   }

   private static void requireUnused(String label, String field, Object value) {
      if(value != null) {
         throw new IllegalArgumentException(
            label + "." + field + ": not used for this unitType/verb combination -- refused " +
            "rather than silently ignored");
      }
   }

   private static void requireValidAssetIdShape(String label, String assetId) {
      AssetEntry entry;

      try {
         entry = AssetEntry.createAssetEntry(assetId);
      }
      catch(NumberFormatException | StringIndexOutOfBoundsException e) {
         throw new IllegalArgumentException(
            label + ".assetId: not a valid asset identifier (\"" + assetId + "\") -- section 2's " +
            "malformed-identifier finding: refused here before ever reaching ViewsheetService, " +
            "which would otherwise throw an unstructured exception");
      }

      if(entry == null) {
         throw new IllegalArgumentException(
            label + ".assetId: not a valid asset identifier (\"" + assetId + "\")");
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

   /** SHA-256 over the canonical plan, same field-order/control-character contract as every prior
    * area's own {@code hash} method, extended with the section 0.2 dependency-preflight projection
    * folded in per-property (matching data sources' own precedent).
    *
    * <p>Deliberately excludes {@code task}: it is a free-text, audit-only label (see {@link
    * ViewsheetChangesetApplyService}'s {@code writeAudit} calls, its only use post-resolve) with
    * no bearing on what is actually mutated or verified, and the caller is never required to
    * replay it byte-for-byte between preview and apply. */
   private static String hash(List<PlanChange> changes,
                              Map<String, String> auxProjections)
   {
      StringBuilder canonical = new StringBuilder();

      for(PlanChange change : changes) {
         canonical.append(change.property()).append(SEP)
            .append(canonicalOrMarker(change.currentValue())).append(SEP)
            .append(canonicalOrMarker(change.proposedValue())).append(SEP)
            .append(change.risk()).append(SEP)
            .append(change.snapshotScope()).append(SEP);
         String aux = auxProjections.get(change.property());

         if(aux != null) {
            canonical.append("deps:").append(aux).append(SEP);
         }
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
         throw new IllegalStateException("SHA-256 is required to hash a viewsheet change plan", e);
      }
   }

   private static String canonicalOrMarker(String value) {
      return value == null ? NULL_MARKER : value;
   }

   private static final char SEP = (char) 0x1f;
   private static final String NULL_MARKER = String.valueOf((char) 0x01);
   private final ViewsheetService viewsheetApiService;
   private final ViewsheetFolderService folderService;
   private final AssetRepository assetRepository;
   private final WorksheetService worksheetApiService;
}
