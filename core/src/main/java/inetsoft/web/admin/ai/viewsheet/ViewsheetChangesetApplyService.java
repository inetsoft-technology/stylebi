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
import inetsoft.web.admin.sheet.vs.ViewsheetService;
import inetsoft.web.admin.sheet.ws.WorksheetService;
import inetsoft.sree.security.IdentityID;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetObject;
import inetsoft.uql.asset.sync.DependenciesInfo;
import inetsoft.uql.asset.sync.DependencyStorageService;
import inetsoft.uql.asset.sync.RenameTransformObject;
import inetsoft.util.Tool;
import inetsoft.util.audit.*;
import inetsoft.web.admin.ai.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Applies a whole viewsheet/folder changeset, all-or-nothing, and audits every attempt -- the
 * viewsheets analog of {@code inetsoft.web.admin.ai.AdminChangesetApplyService} and (within this
 * run) {@code DataSourceChangesetApplyService}/{@code ProviderChangesetApplyService}, replicated
 * rather than shared (01-spec.md section 6, carry-forward item 5).
 *
 * <p>Undo order is newest-first across the WHOLE plan (unchanged from every prior area), but
 * because this area mixes a non-compensable verb (viewsheet delete) with four fully-compensable
 * ones in the same plan, the undo loop must skip non-compensable entries entirely rather than
 * treating their applied status as a rollback failure -- achieved here simply by never adding a
 * viewsheet-delete entry to {@code undoable} in the first place, so {@link #rollback} naturally
 * skips it without any special-casing.
 *
 * <p>Section 6's "identity changes on rename" finding is load-bearing for rollback specifically: a
 * successful {@code renameViewsheet} changes the touched viewsheet's own asset identifier, so the
 * undo descriptor captured after a successful rename carries the entry's NEWLY-resolved identifier
 * (never the stale pre-apply {@code assetId}) to target for the compensating rename-back.
 */
@Component
public class ViewsheetChangesetApplyService {
   @Autowired
   public ViewsheetChangesetApplyService(ViewsheetChangePlanService planService,
                                         ViewsheetService viewsheetApiService,
                                         ViewsheetFolderService folderService,
                                         AdminBackupService backupService,
                                         WorksheetService worksheetApiService)
   {
      this.planService = planService;
      this.viewsheetApiService = viewsheetApiService;
      this.folderService = folderService;
      this.backupService = backupService;
      this.worksheetApiService = worksheetApiService;
   }

   /**
    * Resolves, gates on the plan hash, backs up, then executes.
    *
    * @throws AdminChangesetApplyService.PlanHashMismatchException if the hash is missing or stale
    *         (maps to HTTP 409) -- reused verbatim, per 01-spec.md section 6.
    * @throws IllegalArgumentException if {@code reviewOutcome} is blank while the plan contains any
    *         viewsheet verb (section 7's per-verb-varying signoff), or {@code
    *         acknowledgeIrreversibleDelete} is not exactly {@code true} while the plan contains a
    *         viewsheet delete entry (section 4 -- non-compensable in this cut).
    * @throws Exception if the Tier-2 backup itself fails, in which case nothing was applied.
    */
   public ViewsheetApplyResult apply(ViewsheetApplyRequest req, Principal user) throws Exception {
      APPLY_LOCK.lock();

      try {
         ResolvedPlan plan = planService.resolve(req, user);

         if(req.getPlanHash() == null || !plan.planHash().equals(req.getPlanHash())) {
            throw new AdminChangesetApplyService.PlanHashMismatchException(plan);
         }

         String reviewedTask;

         try {
            reviewedTask = TaskAuditToken.verify(req.getTaskToken(), plan.planHash());
         }
         catch(TaskAuditToken.TaskTokenException e) {
            throw new AdminChangesetApplyService.TaskTokenMismatchException(plan, e.getMessage());
         }

         if(plan.requiresAgentSignoff() &&
            (req.getReviewOutcome() == null || req.getReviewOutcome().trim().isEmpty()))
         {
            throw new IllegalArgumentException(
               "reviewOutcome: required because this changeset contains a high-risk change");
         }

         // Track B: a worksheet delete is exactly as non-compensable as a viewsheet delete (same
         // underlying assetRepository.removeSheet primitive, no live inverse) -- gated the same
         // way, sharing the "hasViewsheetDelete" acknowledgement path rather than adding a
         // separate flag.
         boolean hasViewsheetDelete = req.getChanges().stream().anyMatch(c -> {
            String unitType = ViewsheetChangePlanService.requireUnitType("change", c.getUnitType());
            boolean isDeletableUnit = ViewsheetChangeRequest.UNIT_VIEWSHEET.equals(unitType) ||
               ViewsheetChangeRequest.UNIT_WORKSHEET.equals(unitType);
            return isDeletableUnit && ViewsheetChangeRequest.VERB_DELETE.equals(
               ViewsheetChangePlanService.requireViewsheetVerb("change", c.getVerb()));
         });

         // bug #76469: a folder delete of a NON-empty folder is exactly as irreversible as a
         // viewsheet delete (a real, permanent AssetRepository cascade, not a metadata-only registry
         // change) -- resolve() already classified it RISK_HIGH for this same reason (mirrors
         // resolveViewsheetDelete's own classification), so reuse that classification here rather
         // than re-walking folder contents a second time.
         boolean hasIrreversibleFolderDelete = false;

         for(int i = 0; i < plan.changes().size(); i++) {
            ViewsheetChangeRequest c = req.getChanges().get(i);

            if(ViewsheetChangeRequest.UNIT_FOLDER.equals(
                  ViewsheetChangePlanService.requireUnitType("change", c.getUnitType())) &&
               ViewsheetChangeRequest.VERB_DELETE.equals(
                  ViewsheetChangePlanService.requireFolderVerb("change", c.getVerb())) &&
               AdminChangeRecord.RISK_HIGH.equals(plan.changes().get(i).risk()))
            {
               hasIrreversibleFolderDelete = true;
               break;
            }
         }

         if((hasViewsheetDelete || hasIrreversibleFolderDelete) &&
            !Boolean.TRUE.equals(req.getAcknowledgeIrreversibleDelete()))
         {
            throw new IllegalArgumentException(
               "acknowledgeIrreversibleDelete: must be true because this changeset contains " +
               (hasViewsheetDelete ? "a viewsheet or worksheet delete" : "a folder delete of a non-empty folder") +
               " -- deleting " + (hasViewsheetDelete ? "a viewsheet/worksheet" : "a non-empty folder") +
               " has NO live inverse in this cut (section 4; bug #76469 for the folder case: its " +
               "own contained viewsheets/worksheets are permanently destroyed, not merely the " +
               "folder label); the Tier-2 snapshot taken for this apply is the only recovery path, " +
               "not merely the path of last resort");
         }

         String txId = "viewsheet-" + newIdSuffix();
         String backupRef = plan.requiresStorageBackup() ? backupService.backup(txId) : null;
         String reviewOutcome = req.getReviewOutcome();
         List<ViewsheetApplyOutcome> results = new ArrayList<>();
         List<Undo> undoable = new ArrayList<>();
         List<RollbackFailure> unknownStateFailures = new ArrayList<>();
         boolean failed = false;
         // Whether the item that threw (if any) had already entered its own mutating call before
         // the throw -- only that case is a genuine partial-mutation risk that must force
         // STATUS_ROLLBACK_FAILED on its own; a throw that fires strictly before the mutating call
         // means the item was never touched, so it must not by itself override an otherwise
         // fully-verified rollback (bug #76856, mirroring bug #76808's DataSourceChangesetApplyService
         // fix).
         boolean unknownStateMutationEntered = false;

         List<ViewsheetChangeRequest> originals = req.getChanges();

         for(int i = 0; i < plan.changes().size(); i++) {
            PlanChange change = plan.changes().get(i);
            ViewsheetChangeRequest original = originals.get(i);
            String key = change.property();
            AtomicBoolean mutationEntered = new AtomicBoolean(false);

            try {
               applyOne(txId, reviewedTask, key, original, user, backupRef, reviewOutcome, results,
                       undoable, mutationEntered);
            }
            catch(Exception e) {
               // A throw carries no verifiable before/after evidence for THIS change -- must never
               // be treated as rolled back. Same rule every prior area's apply service follows.
               results.add(new ViewsheetApplyOutcome(key, null, null,
                  AdminChangeRecord.STATUS_FAILED, messageOf(e), null));
               unknownStateFailures.add(new RollbackFailure(key,
                  "state unknown: apply did not return a verifiable outcome (" + messageOf(e) + ")"));
               unknownStateMutationEntered = mutationEntered.get();
               failed = true;
               break;
            }

            if(AdminChangeRecord.STATUS_FAILED.equals(lastStatus(results))) {
               failed = true;
               break;
            }
         }

         if(!failed) {
            return new ViewsheetApplyResult(txId, AdminChangesetApplyService.STATUS_APPLIED,
                                            backupRef, Collections.unmodifiableList(results), null);
         }

         List<RollbackFailure> rollbackOwnFailures =
            rollback(txId, reviewedTask, undoable, backupRef, reviewOutcome, user);

         // An unknownStateFailures entry only forces rollback-failed when that item's own mutating
         // call had actually been entered (a real partial-mutation risk); if it never touched
         // anything, it must not by itself override an otherwise fully-verified rollback.
         if(rollbackOwnFailures.isEmpty() && !unknownStateMutationEntered) {
            return new ViewsheetApplyResult(txId, AdminChangesetApplyService.STATUS_ROLLED_BACK,
                                            backupRef, Collections.unmodifiableList(results), null);
         }

         List<RollbackFailure> failures = new ArrayList<>(unknownStateFailures);
         failures.addAll(rollbackOwnFailures);
         LOG.error("Viewsheet changeset {} rollback failed; units still changed: {}", txId,
                  failures.stream().map(RollbackFailure::property).collect(Collectors.joining(", ")));
         return new ViewsheetApplyResult(txId, AdminChangesetApplyService.STATUS_ROLLBACK_FAILED,
                                         backupRef, Collections.unmodifiableList(results),
                                         Collections.unmodifiableList(failures));
      }
      finally {
         APPLY_LOCK.unlock();
      }
   }

   private void applyOne(String txId, String task, String key, ViewsheetChangeRequest original,
                         Principal user, String backupRef, String reviewOutcome,
                         List<ViewsheetApplyOutcome> results, List<Undo> undoable,
                         AtomicBoolean mutationEntered)
      throws Exception
   {
      String unitType = ViewsheetChangePlanService.requireUnitType("change", original.getUnitType());

      if(ViewsheetChangeRequest.UNIT_VIEWSHEET.equals(unitType)) {
         String verb = ViewsheetChangePlanService.requireViewsheetVerb("change", original.getVerb());

         if(ViewsheetChangeRequest.VERB_RENAME.equals(verb)) {
            applyViewsheetRename(txId, task, key, original, user, backupRef, reviewOutcome, results,
                                 undoable, mutationEntered);
         }
         else if(ViewsheetChangeRequest.VERB_UPDATE.equals(verb)) {
            applyViewsheetUpdate(txId, task, key, original, user, backupRef, reviewOutcome, results,
                                 undoable, mutationEntered);
         }
         else {
            applyViewsheetDelete(txId, task, key, original, user, backupRef, reviewOutcome, results,
                                 undoable, mutationEntered);
         }

         return;
      }

      if(ViewsheetChangeRequest.UNIT_WORKSHEET.equals(unitType)) {
         String verb = ViewsheetChangePlanService.requireViewsheetVerb("change", original.getVerb());

         if(ViewsheetChangeRequest.VERB_RENAME.equals(verb)) {
            applyWorksheetRename(txId, task, key, original, user, backupRef, reviewOutcome, results,
                                 undoable, mutationEntered);
         }
         else if(ViewsheetChangeRequest.VERB_UPDATE.equals(verb)) {
            applyWorksheetUpdate(txId, task, key, original, user, backupRef, reviewOutcome, results,
                                 undoable, mutationEntered);
         }
         else {
            applyWorksheetDelete(txId, task, key, original, user, backupRef, reviewOutcome, results,
                                 undoable, mutationEntered);
         }

         return;
      }

      String verb = ViewsheetChangePlanService.requireFolderVerb("change", original.getVerb());

      if(ViewsheetChangeRequest.VERB_CREATE.equals(verb)) {
         applyFolderCreate(txId, task, key, original, user, backupRef, reviewOutcome, results,
                           undoable, mutationEntered);
      }
      else if(ViewsheetChangeRequest.VERB_DELETE.equals(verb)) {
         applyFolderDelete(txId, task, key, original, user, backupRef, reviewOutcome, results,
                           undoable, mutationEntered);
      }
      else if(ViewsheetChangeRequest.VERB_UPDATE.equals(verb)) {
         applyFolderUpdate(txId, task, key, original, user, backupRef, reviewOutcome, results,
                           undoable, mutationEntered);
      }
      else {
         applyFolderRename(txId, task, key, original, user, backupRef, reviewOutcome, results,
                           undoable, mutationEntered);
      }
   }

   // ---------------------------------------------------------------- viewsheet rename

   private void applyViewsheetRename(String txId, String task, String key,
                                     ViewsheetChangeRequest original, Principal user,
                                     String backupRef, String reviewOutcome,
                                     List<ViewsheetApplyOutcome> results, List<Undo> undoable,
                                     AtomicBoolean mutationEntered)
      throws Exception
   {
      // Section 2/6: re-resolve fresh, AT APPLY TIME -- never trust anything computed at preview.
      String assetId = original.getAssetId();
      Sheet current = planService.findViewsheetById(user, assetId);

      if(current == null) {
         throw new IllegalArgumentException(
            "assetId: no viewsheet found with this asset identifier at apply time -- it may have " +
            "been deleted or renamed since preview");
      }

      String beforeProjection = ViewsheetProjection.projectViewsheet(current);
      String beforePath = current.getPath();
      boolean beforeGlobal = current.isGlobal();
      IdentityID beforeOwner = current.getUser();

      boolean global = Boolean.TRUE.equals(original.getGlobal());
      IdentityID owner = global ? null : ViewsheetFolderService.parseOwner(original.getOwner());
      String newPath = original.getNewPath();

      mutationEntered.set(true);
      viewsheetApiService.renameViewsheet(assetId, newPath, global, owner, user);

      // Re-read via the NEW location to confirm the change landed and capture the viewsheet's NEW
      // assetId for afterValue and for rollback (section 4's identity-changes-on-rename finding).
      Sheet after = planService.findViewsheetByLocation(user, newPath, global, owner);
      String afterProjection = ViewsheetProjection.projectViewsheet(after);
      boolean verified = after != null;
      String status = verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED;
      results.add(new ViewsheetApplyOutcome(key, beforeProjection, afterProjection, status,
         verified ? null : "viewsheet not found at the new location after rename", null));
      writeAudit(txId, task, key, ActionRecord.OBJECT_TYPE_DASHBOARD, AdminChangeRecord.RISK_HIGH,
                AdminChangeRecord.ACTION_APPLY, beforeProjection, afterProjection, status, backupRef,
                reviewOutcome, user);

      if(verified) {
         undoable.add(
            Undo.viewsheetRename(key, after.getAsset(), beforePath, beforeGlobal, beforeOwner));
      }
   }

   // ---------------------------------------------------------------- viewsheet delete

   private void applyViewsheetDelete(String txId, String task, String key,
                                     ViewsheetChangeRequest original, Principal user,
                                     String backupRef, String reviewOutcome,
                                     List<ViewsheetApplyOutcome> results, List<Undo> undoable,
                                     AtomicBoolean mutationEntered)
      throws Exception
   {
      String assetId = original.getAssetId();
      Sheet current = planService.findViewsheetById(user, assetId);

      if(current == null) {
         throw new IllegalArgumentException(
            "assetId: no viewsheet found with this asset identifier at apply time -- it may have " +
            "already been deleted since preview");
      }

      boolean force = Boolean.TRUE.equals(original.getForce());
      AssetEntry entry = AssetEntry.createAssetEntry(assetId);
      // Section 0.2 step 2a: re-run the dependency preflight against live state AT APPLY TIME too
      // -- a concurrent change since preview could have added a new dependency. Uses
      // findDependenciesForAdvisory (bug #76728), not ViewsheetChangePlanService.findDependencies,
      // so a lookup failure here can be told apart from a genuine zero-dependents result; the
      // force-gate's own tolerance of a failed lookup is unchanged (still passes an empty array
      // through to requireForceIfDependent either way -- that gate's behavior on lookup failure is
      // a separate, out-of-scope question, see 03b-fix-enterprise.md).
      DependencyLookupResult lookup = findDependenciesForAdvisory(entry);
      AssetEntry[] dependencies = lookup.dependencies();
      ViewsheetChangePlanService.requireForceIfDependent(
         "apply." + assetId, assetId, dependencies, force);
      String advisory = buildDeleteAdvisory(lookup, "viewsheet");

      String beforeProjection = ViewsheetProjection.projectViewsheet(current);
      mutationEntered.set(true);
      viewsheetApiService.deleteViewsheet(assetId, user);

      Sheet after = planService.findViewsheetById(user, assetId);
      boolean verified = after == null;
      String status = verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED;
      results.add(new ViewsheetApplyOutcome(key, beforeProjection, null, status,
         verified ? null : "viewsheet still present after delete", advisory));
      writeAudit(txId, task, key, ActionRecord.OBJECT_TYPE_DASHBOARD, AdminChangeRecord.RISK_HIGH,
                AdminChangeRecord.ACTION_APPLY, beforeProjection, null, status, backupRef,
                reviewOutcome, user);

      // Delete has NO live inverse in this cut (section 4) -- never added to `undoable`, regardless
      // of verified/failed. Its own outcome is always applied or failed, never individually rolled
      // back; other entries in the same plan still roll back normally around it.
   }

   // ---------------------------------------------------------------- viewsheet update

   private void applyViewsheetUpdate(String txId, String task, String key,
                                     ViewsheetChangeRequest original, Principal user,
                                     String backupRef, String reviewOutcome,
                                     List<ViewsheetApplyOutcome> results, List<Undo> undoable,
                                     AtomicBoolean mutationEntered)
      throws Exception
   {
      String assetId = original.getAssetId();
      Sheet current = planService.findViewsheetById(user, assetId);

      if(current == null) {
         throw new IllegalArgumentException(
            "assetId: no viewsheet found with this asset identifier at apply time -- it may have " +
            "been deleted or renamed since preview");
      }

      ViewsheetService.Metadata beforeMetadata = viewsheetApiService.getViewsheetMetadata(assetId, user);
      String alias = original.getAlias();
      String description = original.getDescription();
      String mergedAlias = alias != null ? normalizeClearedField(alias) : beforeMetadata.alias();
      String mergedDescription =
         description != null ? normalizeClearedField(description) : beforeMetadata.description();
      String beforeProjection =
         ViewsheetProjection.projectViewsheetUpdate(current, beforeMetadata.alias(), beforeMetadata.description());

      mutationEntered.set(true);
      viewsheetApiService.updateMetadata(assetId, mergedAlias, mergedDescription, user);

      ViewsheetService.Metadata afterMetadata = viewsheetApiService.getViewsheetMetadata(assetId, user);
      String afterProjection =
         ViewsheetProjection.projectViewsheetUpdate(current, afterMetadata.alias(), afterMetadata.description());
      boolean verified = Objects.equals(afterMetadata.alias(), mergedAlias) &&
         Objects.equals(afterMetadata.description(), mergedDescription);
      String status = verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED;
      results.add(new ViewsheetApplyOutcome(key, beforeProjection, afterProjection, status,
         verified ? null : "viewsheet alias/description did not verify after update", null));
      writeAudit(txId, task, key, ActionRecord.OBJECT_TYPE_DASHBOARD, AdminChangeRecord.RISK_LOW,
                AdminChangeRecord.ACTION_APPLY, beforeProjection, afterProjection, status, backupRef,
                reviewOutcome, user);

      if(verified) {
         undoable.add(
            Undo.viewsheetUpdate(key, assetId, beforeMetadata.alias(), beforeMetadata.description()));
      }
   }

   // ---------------------------------------------------------------- worksheet rename

   private void applyWorksheetRename(String txId, String task, String key,
                                     ViewsheetChangeRequest original, Principal user,
                                     String backupRef, String reviewOutcome,
                                     List<ViewsheetApplyOutcome> results, List<Undo> undoable,
                                     AtomicBoolean mutationEntered)
      throws Exception
   {
      String assetId = original.getAssetId();
      Sheet current = planService.findWorksheetById(user, assetId);

      if(current == null) {
         throw new IllegalArgumentException(
            "assetId: no worksheet found with this asset identifier at apply time -- it may have " +
            "been deleted or renamed since preview");
      }

      String beforeProjection = ViewsheetProjection.projectWorksheet(current);
      String beforePath = current.getPath();
      boolean beforeGlobal = current.isGlobal();
      IdentityID beforeOwner = current.getUser();

      boolean global = Boolean.TRUE.equals(original.getGlobal());
      IdentityID owner = global ? null : ViewsheetFolderService.parseOwner(original.getOwner());
      String newPath = original.getNewPath();

      mutationEntered.set(true);
      worksheetApiService.renameWorksheet(assetId, newPath, global, owner, user);

      Sheet after = planService.findWorksheetByLocation(user, newPath, global, owner);
      String afterProjection = ViewsheetProjection.projectWorksheet(after);
      boolean verified = after != null;
      String status = verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED;
      results.add(new ViewsheetApplyOutcome(key, beforeProjection, afterProjection, status,
         verified ? null : "worksheet not found at the new location after rename", null));
      writeAudit(txId, task, key, ActionRecord.OBJECT_TYPE_WORKSHEET, AdminChangeRecord.RISK_HIGH,
                AdminChangeRecord.ACTION_APPLY, beforeProjection, afterProjection, status, backupRef,
                reviewOutcome, user);

      if(verified) {
         undoable.add(
            Undo.worksheetRename(key, after.getAsset(), beforePath, beforeGlobal, beforeOwner));
      }
   }

   // ---------------------------------------------------------------- worksheet delete

   private void applyWorksheetDelete(String txId, String task, String key,
                                     ViewsheetChangeRequest original, Principal user,
                                     String backupRef, String reviewOutcome,
                                     List<ViewsheetApplyOutcome> results, List<Undo> undoable,
                                     AtomicBoolean mutationEntered)
      throws Exception
   {
      String assetId = original.getAssetId();
      Sheet current = planService.findWorksheetById(user, assetId);

      if(current == null) {
         throw new IllegalArgumentException(
            "assetId: no worksheet found with this asset identifier at apply time -- it may have " +
            "already been deleted since preview");
      }

      boolean force = Boolean.TRUE.equals(original.getForce());
      AssetEntry entry = AssetEntry.createAssetEntry(assetId);
      // Same rationale as applyViewsheetDelete's use of findDependenciesForAdvisory (bug #76728).
      DependencyLookupResult lookup = findDependenciesForAdvisory(entry);
      AssetEntry[] dependencies = lookup.dependencies();
      ViewsheetChangePlanService.requireForceIfDependent(
         "apply." + assetId, assetId, dependencies, force);
      String advisory = buildDeleteAdvisory(lookup, "worksheet");

      String beforeProjection = ViewsheetProjection.projectWorksheet(current);
      mutationEntered.set(true);
      worksheetApiService.deleteWorksheet(assetId, user);

      Sheet after = planService.findWorksheetById(user, assetId);
      boolean verified = after == null;
      String status = verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED;
      results.add(new ViewsheetApplyOutcome(key, beforeProjection, null, status,
         verified ? null : "worksheet still present after delete", advisory));
      writeAudit(txId, task, key, ActionRecord.OBJECT_TYPE_WORKSHEET, AdminChangeRecord.RISK_HIGH,
                AdminChangeRecord.ACTION_APPLY, beforeProjection, null, status, backupRef,
                reviewOutcome, user);

      // Delete has NO live inverse in this cut, same as viewsheet delete -- never added to
      // `undoable`.
   }

   /**
    * Bug #76728: {@code ViewsheetChangePlanService.findDependencies} goes through {@code
    * DependencyTool.getDependencies}, which silently swallows any lookup exception and returns an
    * empty list -- so a force-delete's advisory could not tell "confirmed zero dependents" apart
    * from "the lookup itself failed after the delete already happened," and silently read as the
    * former. This calls {@code DependencyStorageService.getWithOrg} directly, with its own
    * try/catch, so the two cases can be told apart at the one call site (apply-time advisory
    * construction) where that distinction is safety-relevant. Deliberately does not replicate
    * {@code DependencyTool.getDependencies}'s {@code SCHEDULE_TASK} branch -- a viewsheet/
    * worksheet {@code AssetEntry} is never that type.
    */
   private static DependencyLookupResult findDependenciesForAdvisory(AssetEntry entry) {
      try {
         DependencyStorageService service = DependencyStorageService.getInstance();
         RenameTransformObject obj = service.getWithOrg(entry.toIdentifier(), entry.getOrgID());
         List<AssetObject> raw = new ArrayList<>();

         if(obj instanceof DependenciesInfo info) {
            if(info.getDependencies() != null) {
               raw.addAll(info.getDependencies());
            }

            if(info.getEmbedDependencies() != null) {
               raw.addAll(info.getEmbedDependencies());
            }
         }

         AssetEntry[] dependencies = raw.stream()
            .filter(AssetEntry.class::isInstance)
            .map(AssetEntry.class::cast)
            .toArray(AssetEntry[]::new);
         return new DependencyLookupResult(dependencies, false);
      }
      catch(Exception e) {
         LOG.warn("Failed to look up dependencies for delete advisory on {}", entry.toIdentifier(), e);
         return new DependencyLookupResult(new AssetEntry[0], true);
      }
   }

   /** Bug #76728: the advisory text this delete surfaces to the operator -- distinct wording when
    * the dependency lookup itself failed, rather than silently reading the same as "confirmed zero
    * dependents." */
   private static String buildDeleteAdvisory(DependencyLookupResult lookup, String unitLabel) {
      if(lookup.lookupFailed()) {
         return "deleted with force: true, but the dependency check failed after the delete was " +
            "already applied -- unable to confirm whether other assets still reference this " +
            unitLabel + "; check server logs and verify manually";
      }

      AssetEntry[] dependencies = lookup.dependencies();
      return dependencies.length == 0 ? null :
         "deleted with force: true despite " + dependencies.length + " dependent asset(s) still " +
         "referencing this " + unitLabel + ": " + ViewsheetProjection.projectDependencies(dependencies);
   }

   /** Result of {@link #findDependenciesForAdvisory} -- distinguishes a confirmed-empty lookup from
    * one that failed and was swallowed to empty for the (unchanged, out-of-scope) force-gate. */
   private record DependencyLookupResult(AssetEntry[] dependencies, boolean lookupFailed) {
   }

   // ---------------------------------------------------------------- worksheet update

   private void applyWorksheetUpdate(String txId, String task, String key,
                                     ViewsheetChangeRequest original, Principal user,
                                     String backupRef, String reviewOutcome,
                                     List<ViewsheetApplyOutcome> results, List<Undo> undoable,
                                     AtomicBoolean mutationEntered)
      throws Exception
   {
      String assetId = original.getAssetId();
      Sheet current = planService.findWorksheetById(user, assetId);

      if(current == null) {
         throw new IllegalArgumentException(
            "assetId: no worksheet found with this asset identifier at apply time -- it may have " +
            "been deleted or renamed since preview");
      }

      WorksheetService.Metadata beforeMetadata = worksheetApiService.getWorksheetSettingsMetadata(assetId, user);
      String alias = original.getAlias();
      String description = original.getDescription();
      String mergedAlias = alias != null ? normalizeClearedField(alias) : beforeMetadata.alias();
      String mergedDescription =
         description != null ? normalizeClearedField(description) : beforeMetadata.description();
      String beforeProjection =
         ViewsheetProjection.projectWorksheetUpdate(current, beforeMetadata.alias(), beforeMetadata.description());

      mutationEntered.set(true);
      worksheetApiService.updateMetadata(assetId, mergedAlias, mergedDescription, user);

      WorksheetService.Metadata afterMetadata = worksheetApiService.getWorksheetSettingsMetadata(assetId, user);
      String afterProjection =
         ViewsheetProjection.projectWorksheetUpdate(current, afterMetadata.alias(), afterMetadata.description());
      boolean verified = Objects.equals(afterMetadata.alias(), mergedAlias) &&
         Objects.equals(afterMetadata.description(), mergedDescription);
      String status = verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED;
      results.add(new ViewsheetApplyOutcome(key, beforeProjection, afterProjection, status,
         verified ? null : "worksheet alias/description did not verify after update", null));
      writeAudit(txId, task, key, ActionRecord.OBJECT_TYPE_WORKSHEET, AdminChangeRecord.RISK_LOW,
                AdminChangeRecord.ACTION_APPLY, beforeProjection, afterProjection, status, backupRef,
                reviewOutcome, user);

      if(verified) {
         undoable.add(
            Undo.worksheetUpdate(key, assetId, beforeMetadata.alias(), beforeMetadata.description()));
      }
   }

   // ---------------------------------------------------------------- folder create

   private void applyFolderCreate(String txId, String task, String key,
                                  ViewsheetChangeRequest original, Principal user, String backupRef,
                                  String reviewOutcome, List<ViewsheetApplyOutcome> results,
                                  List<Undo> undoable, AtomicBoolean mutationEntered)
      throws Exception
   {
      IdentityID owner = resolveOwner(original.getOwner());
      String folderName = original.getFolderName();
      String fullPath = ViewsheetFolderService.computeFolderFullPath(
         original.getParentFolder(), folderName, owner);

      GetViewsheetFolderResult before = folderService.getFolder(fullPath, owner);
      String beforeProjection = ViewsheetProjection.projectFolder(before);

      mutationEntered.set(true);
      viewsheetApiService.addFolder(
         original.getParentFolder(), folderName, original.getAlias(), original.getDescription(),
         owner, user);

      GetViewsheetFolderResult after = folderService.getFolder(fullPath, owner);
      String afterProjection = ViewsheetProjection.projectFolder(after);
      boolean verified = after.found();
      String status = verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED;
      results.add(new ViewsheetApplyOutcome(key, beforeProjection, afterProjection, status,
         verified ? null : "folder not found after create", null));
      writeAudit(txId, task, key, ActionRecord.OBJECT_TYPE_FOLDER, AdminChangeRecord.RISK_LOW,
                AdminChangeRecord.ACTION_APPLY, beforeProjection, afterProjection, status, backupRef,
                reviewOutcome, user);

      if(verified) {
         // Section 6/14 item 4: rollback removes only the originally-requested leaf path -- any
         // ancestor folders addFolder itself auto-created (section 0.5) are NOT individually
         // rolled back, a known, disclosed incompleteness, not a bug.
         undoable.add(Undo.folderCreate(key, fullPath, owner));
      }
   }

   // ---------------------------------------------------------------- folder delete

   private void applyFolderDelete(String txId, String task, String key,
                                  ViewsheetChangeRequest original, Principal user, String backupRef,
                                  String reviewOutcome, List<ViewsheetApplyOutcome> results,
                                  List<Undo> undoable, AtomicBoolean mutationEntered)
      throws Exception
   {
      IdentityID owner = resolveOwner(original.getOwner());
      String rawPath = ViewsheetChangePlanService.requireFolderTargetPath("apply", original);
      String normalizedPath = ViewsheetFolderService.normalizeFolderPath(rawPath, owner);

      GetViewsheetFolderResult before = folderService.getFolder(normalizedPath, owner);

      if(!before.found()) {
         throw new IllegalArgumentException(
            "path: folder \"" + normalizedPath + "\" does not exist at apply time -- it may have " +
            "already been removed since preview");
      }

      boolean force = Boolean.TRUE.equals(original.getForce());
      // Re-run the content preflight against live state AT APPLY TIME too (bug #76469) -- a
      // concurrent save into the folder since preview could have added content, the same
      // apply-time re-check reason applyViewsheetDelete re-runs its own dependency preflight.
      AssetEntry[] contents = planService.findFolderContents(normalizedPath, owner, user);
      AssetEntry[] visibleContents = planService.visibleFolderContents(contents, user);
      ViewsheetChangePlanService.requireForceIfNonEmpty("apply." + normalizedPath, normalizedPath,
         contents, visibleContents, force);
      String risk = contents.length == 0 ? AdminChangeRecord.RISK_LOW : AdminChangeRecord.RISK_HIGH;
      String advisory = contents.length == 0 ? null :
         "deleted with force: true, permanently and irrecoverably destroying " + contents.length +
         " contained viewsheet/worksheet(s): " +
         ViewsheetProjection.projectVisibleContents(contents, visibleContents);

      String beforeProjection = ViewsheetProjection.projectFolder(before);

      mutationEntered.set(true);
      viewsheetApiService.removeFolder(normalizedPath, owner, user);

      GetViewsheetFolderResult after = folderService.getFolder(normalizedPath, owner);
      boolean verified = !after.found();
      String status = verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED;
      results.add(new ViewsheetApplyOutcome(key, beforeProjection, null, status,
         verified ? null : "folder still present after delete", advisory));
      writeAudit(txId, task, key, ActionRecord.OBJECT_TYPE_FOLDER, risk,
                AdminChangeRecord.ACTION_APPLY, beforeProjection, null, status, backupRef,
                reviewOutcome, user);

      if(verified) {
         // Section 0.4/6: NOT a complete inverse when the folder was non-empty at delete time (bug
         // #76469) -- re-adding via create restores only the empty folder label; the folder's own
         // recursive AssetRepository cascade (RepletEngine's REMOVE_FOLDER_EVENT listener) already
         // permanently destroyed any contained viewsheet/worksheet, and nothing here resurrects it.
         // Still queued for rollback because restoring the label is strictly better than leaving it
         // deleted too when another entry in the same plan fails.
         undoable.add(Undo.folderDelete(key, normalizedPath, owner));
      }
   }

   // ---------------------------------------------------------------- folder rename

   private void applyFolderRename(String txId, String task, String key,
                                  ViewsheetChangeRequest original, Principal user, String backupRef,
                                  String reviewOutcome, List<ViewsheetApplyOutcome> results,
                                  List<Undo> undoable, AtomicBoolean mutationEntered)
      throws Exception
   {
      IdentityID owner = resolveOwner(original.getOwner());
      String rawOldPath = ViewsheetChangePlanService.requireFolderTargetPath("apply", original);
      String normalizedOld = ViewsheetFolderService.normalizeFolderPath(rawOldPath, owner);
      String normalizedNew = ViewsheetFolderService.normalizeFolderPath(original.getNewPath(), owner);

      GetViewsheetFolderResult before = folderService.getFolder(normalizedOld, owner);

      if(!before.found()) {
         throw new IllegalArgumentException(
            "path: folder \"" + normalizedOld + "\" does not exist at apply time -- it may have " +
            "already been removed/renamed since preview");
      }

      String beforeProjection = ViewsheetProjection.projectFolder(before);

      mutationEntered.set(true);
      viewsheetApiService.renameFolder(normalizedOld, normalizedNew, owner, user);

      GetViewsheetFolderResult afterNew = folderService.getFolder(normalizedNew, owner);
      GetViewsheetFolderResult afterOld = folderService.getFolder(normalizedOld, owner);
      boolean verified = afterNew.found() && !afterOld.found();
      String afterProjection = ViewsheetProjection.projectFolder(afterNew);
      String status = verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED;
      results.add(new ViewsheetApplyOutcome(key, beforeProjection, afterProjection, status,
         verified ? null : "folder rename did not verify at the new/old path", null));
      writeAudit(txId, task, key, ActionRecord.OBJECT_TYPE_FOLDER, AdminChangeRecord.RISK_LOW,
                AdminChangeRecord.ACTION_APPLY, beforeProjection, afterProjection, status, backupRef,
                reviewOutcome, user);

      if(verified) {
         // Section 0.4/6: a pure key-swap, complete inverse, no ancestor-creation asymmetry the
         // way folder create has.
         undoable.add(Undo.folderRename(key, normalizedNew, normalizedOld, owner));
      }
   }

   // ---------------------------------------------------------------- folder update

   private void applyFolderUpdate(String txId, String task, String key,
                                  ViewsheetChangeRequest original, Principal user, String backupRef,
                                  String reviewOutcome, List<ViewsheetApplyOutcome> results,
                                  List<Undo> undoable, AtomicBoolean mutationEntered)
      throws Exception
   {
      IdentityID owner = resolveOwner(original.getOwner());
      String rawPath = ViewsheetChangePlanService.requireFolderTargetPath("apply", original);
      String normalizedPath = ViewsheetFolderService.normalizeFolderPath(rawPath, owner);

      GetViewsheetFolderResult before = folderService.getFolder(normalizedPath, owner);

      if(!before.found()) {
         throw new IllegalArgumentException(
            "path: folder \"" + normalizedPath + "\" does not exist at apply time -- it may have " +
            "been removed/renamed since preview");
      }

      String alias = original.getAlias();
      String description = original.getDescription();
      String mergedAlias = alias != null ? normalizeClearedField(alias) : before.alias();
      String mergedDescription =
         description != null ? normalizeClearedField(description) : before.description();
      String beforeProjection = ViewsheetProjection.projectFolder(before);

      mutationEntered.set(true);
      folderService.updateFolderMetadata(normalizedPath, owner, mergedAlias, mergedDescription, user);

      GetViewsheetFolderResult after = folderService.getFolder(normalizedPath, owner);
      String afterProjection = ViewsheetProjection.projectFolder(after);
      boolean verified = Objects.equals(after.alias(), mergedAlias) &&
         Objects.equals(after.description(), mergedDescription);
      String status = verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED;
      results.add(new ViewsheetApplyOutcome(key, beforeProjection, afterProjection, status,
         verified ? null : "folder alias/description did not verify after update", null));
      writeAudit(txId, task, key, ActionRecord.OBJECT_TYPE_FOLDER, AdminChangeRecord.RISK_LOW,
                AdminChangeRecord.ACTION_APPLY, beforeProjection, afterProjection, status, backupRef,
                reviewOutcome, user);

      if(verified) {
         undoable.add(Undo.folderUpdate(key, normalizedPath, owner, before.alias(), before.description()));
      }
   }

   // ---------------------------------------------------------------- rollback

   /** Undoes verified changes newest-first, skipping non-compensable entries entirely (matching
    * C.3/C.5(data sources)'s own "other entries in the same plan still roll back normally around
    * it" treatment) -- viewsheet delete is simply never added to {@code undoable} in the first
    * place, so this loop naturally skips it without any special-casing. */
   private List<RollbackFailure> rollback(String txId, String task, List<Undo> undoable,
                                          String backupRef, String reviewOutcome, Principal user)
   {
      List<RollbackFailure> failures = new ArrayList<>();

      for(int i = undoable.size() - 1; i >= 0; i--) {
         Undo undo = undoable.get(i);

         try {
            undo.rollback(this, txId, task, backupRef, reviewOutcome, user, failures);
         }
         catch(Exception e) {
            failures.add(new RollbackFailure(undo.key, messageOf(e)));
         }
      }

      return failures;
   }

   private void rollbackViewsheetRename(Undo undo, String txId, String task, String backupRef,
                                        String reviewOutcome, Principal user,
                                        List<RollbackFailure> failures)
      throws Exception
   {
      // Never the stale pre-apply assetId -- undo.currentAssetId is the identity resolved AFTER
      // the apply-time rename succeeded (section 4/6).
      viewsheetApiService.renameViewsheet(
         undo.currentAssetId, undo.beforePath, undo.beforeGlobal, undo.beforeOwner, user);
      Sheet reread = planService.findViewsheetByLocation(
         user, undo.beforePath, undo.beforeGlobal, undo.beforeOwner);
      boolean verified = reread != null;
      writeAudit(txId, task, undo.key, ActionRecord.OBJECT_TYPE_DASHBOARD, AdminChangeRecord.RISK_HIGH,
                AdminChangeRecord.ACTION_ROLLBACK, null, null,
                verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED,
                backupRef, reviewOutcome, user);

      if(!verified) {
         failures.add(
            new RollbackFailure(undo.key, "rollback of rename did not restore the prior path"));
      }
   }

   private void rollbackViewsheetUpdate(Undo undo, String txId, String task, String backupRef,
                                        String reviewOutcome, Principal user,
                                        List<RollbackFailure> failures)
      throws Exception
   {
      viewsheetApiService.updateMetadata(
         undo.currentAssetId, undo.beforeAlias, undo.beforeDescription, user);
      ViewsheetService.Metadata reread =
         viewsheetApiService.getViewsheetMetadata(undo.currentAssetId, user);
      boolean verified = Objects.equals(reread.alias(), undo.beforeAlias) &&
         Objects.equals(reread.description(), undo.beforeDescription);
      writeAudit(txId, task, undo.key, ActionRecord.OBJECT_TYPE_DASHBOARD, AdminChangeRecord.RISK_LOW,
                AdminChangeRecord.ACTION_ROLLBACK, null, null,
                verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED,
                backupRef, reviewOutcome, user);

      if(!verified) {
         failures.add(
            new RollbackFailure(undo.key, "rollback of update did not restore the prior alias/description"));
      }
   }

   private void rollbackWorksheetRename(Undo undo, String txId, String task, String backupRef,
                                        String reviewOutcome, Principal user,
                                        List<RollbackFailure> failures)
      throws Exception
   {
      worksheetApiService.renameWorksheet(
         undo.currentAssetId, undo.beforePath, undo.beforeGlobal, undo.beforeOwner, user);
      Sheet reread = planService.findWorksheetByLocation(
         user, undo.beforePath, undo.beforeGlobal, undo.beforeOwner);
      boolean verified = reread != null;
      writeAudit(txId, task, undo.key, ActionRecord.OBJECT_TYPE_WORKSHEET, AdminChangeRecord.RISK_HIGH,
                AdminChangeRecord.ACTION_ROLLBACK, null, null,
                verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED,
                backupRef, reviewOutcome, user);

      if(!verified) {
         failures.add(
            new RollbackFailure(undo.key, "rollback of rename did not restore the prior path"));
      }
   }

   private void rollbackWorksheetUpdate(Undo undo, String txId, String task, String backupRef,
                                        String reviewOutcome, Principal user,
                                        List<RollbackFailure> failures)
      throws Exception
   {
      worksheetApiService.updateMetadata(
         undo.currentAssetId, undo.beforeAlias, undo.beforeDescription, user);
      WorksheetService.Metadata reread =
         worksheetApiService.getWorksheetSettingsMetadata(undo.currentAssetId, user);
      boolean verified = Objects.equals(reread.alias(), undo.beforeAlias) &&
         Objects.equals(reread.description(), undo.beforeDescription);
      writeAudit(txId, task, undo.key, ActionRecord.OBJECT_TYPE_WORKSHEET, AdminChangeRecord.RISK_LOW,
                AdminChangeRecord.ACTION_ROLLBACK, null, null,
                verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED,
                backupRef, reviewOutcome, user);

      if(!verified) {
         failures.add(
            new RollbackFailure(undo.key, "rollback of update did not restore the prior alias/description"));
      }
   }

   private void rollbackFolderCreate(Undo undo, String txId, String task, String backupRef,
                                     String reviewOutcome, Principal user,
                                     List<RollbackFailure> failures)
      throws Exception
   {
      viewsheetApiService.removeFolder(undo.fullPath, undo.owner, user);
      GetViewsheetFolderResult reread = folderService.getFolder(undo.fullPath, undo.owner);
      boolean verified = !reread.found();
      writeAudit(txId, task, undo.key, ActionRecord.OBJECT_TYPE_FOLDER, AdminChangeRecord.RISK_LOW,
                AdminChangeRecord.ACTION_ROLLBACK, null, null,
                verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED,
                backupRef, reviewOutcome, user);

      if(!verified) {
         failures.add(
            new RollbackFailure(undo.key, "rollback of folder create did not remove the folder"));
      }
   }

   private void rollbackFolderDelete(Undo undo, String txId, String task, String backupRef,
                                     String reviewOutcome, Principal user,
                                     List<RollbackFailure> failures)
      throws Exception
   {
      String[] parentLeaf = splitParentLeaf(undo.fullPath);
      viewsheetApiService.addFolder(parentLeaf[0], parentLeaf[1], undo.owner, user);
      GetViewsheetFolderResult reread = folderService.getFolder(undo.fullPath, undo.owner);
      boolean verified = reread.found();
      writeAudit(txId, task, undo.key, ActionRecord.OBJECT_TYPE_FOLDER, AdminChangeRecord.RISK_LOW,
                AdminChangeRecord.ACTION_ROLLBACK, null, null,
                verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED,
                backupRef, reviewOutcome, user);

      if(!verified) {
         failures.add(
            new RollbackFailure(undo.key, "rollback of folder delete did not restore the folder"));
      }
   }

   private void rollbackFolderRename(Undo undo, String txId, String task, String backupRef,
                                     String reviewOutcome, Principal user,
                                     List<RollbackFailure> failures)
      throws Exception
   {
      // undo.fullPath holds the NEW (current) path, undo.beforePath the ORIGINAL path -- swap.
      viewsheetApiService.renameFolder(undo.fullPath, undo.beforePath, undo.owner, user);
      GetViewsheetFolderResult reread = folderService.getFolder(undo.beforePath, undo.owner);
      boolean verified = reread.found();
      writeAudit(txId, task, undo.key, ActionRecord.OBJECT_TYPE_FOLDER, AdminChangeRecord.RISK_LOW,
                AdminChangeRecord.ACTION_ROLLBACK, null, null,
                verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED,
                backupRef, reviewOutcome, user);

      if(!verified) {
         failures.add(
            new RollbackFailure(undo.key, "rollback of folder rename did not restore the prior path"));
      }
   }

   private void rollbackFolderUpdate(Undo undo, String txId, String task, String backupRef,
                                     String reviewOutcome, Principal user,
                                     List<RollbackFailure> failures)
      throws Exception
   {
      folderService.updateFolderMetadata(
         undo.fullPath, undo.owner, undo.beforeAlias, undo.beforeDescription, user);
      GetViewsheetFolderResult reread = folderService.getFolder(undo.fullPath, undo.owner);
      boolean verified = Objects.equals(reread.alias(), undo.beforeAlias) &&
         Objects.equals(reread.description(), undo.beforeDescription);
      writeAudit(txId, task, undo.key, ActionRecord.OBJECT_TYPE_FOLDER, AdminChangeRecord.RISK_LOW,
                AdminChangeRecord.ACTION_ROLLBACK, null, null,
                verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED,
                backupRef, reviewOutcome, user);

      if(!verified) {
         failures.add(
            new RollbackFailure(undo.key, "rollback of update did not restore the prior alias/description"));
      }
   }

   private static String[] splitParentLeaf(String fullPath) {
      int idx = fullPath.lastIndexOf('/');

      if(idx < 0) {
         return new String[]{"", fullPath};
      }

      return new String[]{fullPath.substring(0, idx), fullPath.substring(idx + 1)};
   }

   private void writeAudit(String txId, String task, String key, String objectType, String risk,
                           String adminAction, String before, String after, String status,
                           String backupRef, String reviewOutcome, Principal user)
   {
      try {
         AdminChangeRecord record = new AdminChangeRecord();
         record.setTransactionId(txId);
         record.setTaskDescription(task);
         record.setProperty(key);
         record.setObjectType(objectType);
         record.setBeforeValue(before);
         record.setAfterValue(after);
         record.setAction(adminAction);
         record.setStatus(status);
         record.setRiskLevel(risk);
         record.setSnapshotScope(AdminChangeRecord.SCOPE_STORAGE);
         record.setBackupRef(backupRef);
         record.setReviewOutcome(reviewOutcome);
         record.setUserName(user == null ? null : user.getName());
         record.setActionTimestamp(new Timestamp(System.currentTimeMillis()));
         record.setServerHostName(Tool.getHost());
         Audit.getInstance().auditAdminChange(record, user);
      }
      catch(Exception auditFailure) {
         // An audit write must never replace the real outcome -- same rule every prior area's
         // apply service follows. Unlike data sources, ViewsheetService's OWN inline
         // ActionRecord audit correctly reports FAILURE on a thrown exception (section 8) -- but
         // this record is still built independently, not cross-checked against that stream, per
         // section 8's "document, do not correlate" decision (no shared transactionId exists
         // between the two audit streams).
         LOG.error("Failed to write viewsheet admin change audit record for transaction {}", txId,
                   auditFailure);
      }
   }

   private static IdentityID resolveOwner(String ownerRaw) {
      return ViewsheetFolderService.parseOwner(ownerRaw);
   }

   private static String lastStatus(List<ViewsheetApplyOutcome> results) {
      return results.isEmpty() ? null : results.get(results.size() - 1).status();
   }

   private static String messageOf(Exception e) {
      return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
   }

   /** An explicit "" from the caller means "clear this field", which the read side already
    * normalizes to null (bug #76669) -- normalize here too so the merge, write, and verify
    * steps all agree. */
   private static String normalizeClearedField(String raw) {
      return (raw != null && raw.isEmpty()) ? null : raw;
   }

   private static String newIdSuffix() {
      return String.format("%016x", RANDOM.nextLong());
   }

   /** One undo descriptor built during apply, replayed in reverse by {@link #rollback}. Never
    * built for a viewsheet delete (section 4/6) -- non-compensable, always applied or failed. */
   private static final class Undo {
      static Undo viewsheetRename(String key, String currentAssetId, String beforePath,
                                  boolean beforeGlobal, IdentityID beforeOwner)
      {
         Undo u = new Undo(key, Kind.VIEWSHEET_RENAME);
         u.currentAssetId = currentAssetId;
         u.beforePath = beforePath;
         u.beforeGlobal = beforeGlobal;
         u.beforeOwner = beforeOwner;
         return u;
      }

      static Undo folderCreate(String key, String fullPath, IdentityID owner) {
         Undo u = new Undo(key, Kind.FOLDER_CREATE);
         u.fullPath = fullPath;
         u.owner = owner;
         return u;
      }

      static Undo folderDelete(String key, String fullPath, IdentityID owner) {
         Undo u = new Undo(key, Kind.FOLDER_DELETE);
         u.fullPath = fullPath;
         u.owner = owner;
         return u;
      }

      static Undo folderRename(String key, String newPath, String oldPath, IdentityID owner) {
         Undo u = new Undo(key, Kind.FOLDER_RENAME);
         u.fullPath = newPath;
         u.beforePath = oldPath;
         u.owner = owner;
         return u;
      }

      /** Track B: fully compensable -- captures the PRE-apply alias/description so rollback can
       * write them straight back via the same update call, cheaper and safer than any other
       * verb's rollback in this whole area (a pure field write, no identity change). */
      static Undo viewsheetUpdate(String key, String currentAssetId, String beforeAlias,
                                  String beforeDescription)
      {
         Undo u = new Undo(key, Kind.VIEWSHEET_UPDATE);
         u.currentAssetId = currentAssetId;
         u.beforeAlias = beforeAlias;
         u.beforeDescription = beforeDescription;
         return u;
      }

      /** Same rationale as {@link #viewsheetRename}, for a worksheet. */
      static Undo worksheetRename(String key, String currentAssetId, String beforePath,
                                  boolean beforeGlobal, IdentityID beforeOwner)
      {
         Undo u = new Undo(key, Kind.WORKSHEET_RENAME);
         u.currentAssetId = currentAssetId;
         u.beforePath = beforePath;
         u.beforeGlobal = beforeGlobal;
         u.beforeOwner = beforeOwner;
         return u;
      }

      /** Same rationale as {@link #viewsheetUpdate}, for a worksheet. */
      static Undo worksheetUpdate(String key, String currentAssetId, String beforeAlias,
                                  String beforeDescription)
      {
         Undo u = new Undo(key, Kind.WORKSHEET_UPDATE);
         u.currentAssetId = currentAssetId;
         u.beforeAlias = beforeAlias;
         u.beforeDescription = beforeDescription;
         return u;
      }

      /** Same rationale as {@link #viewsheetUpdate}, for a folder. */
      static Undo folderUpdate(String key, String fullPath, IdentityID owner, String beforeAlias,
                              String beforeDescription)
      {
         Undo u = new Undo(key, Kind.FOLDER_UPDATE);
         u.fullPath = fullPath;
         u.owner = owner;
         u.beforeAlias = beforeAlias;
         u.beforeDescription = beforeDescription;
         return u;
      }

      private Undo(String key, Kind kind) {
         this.key = key;
         this.kind = kind;
      }

      void rollback(ViewsheetChangesetApplyService svc, String txId, String task, String backupRef,
                    String reviewOutcome, Principal user, List<RollbackFailure> failures)
         throws Exception
      {
         switch(kind) {
         case VIEWSHEET_RENAME:
            svc.rollbackViewsheetRename(this, txId, task, backupRef, reviewOutcome, user, failures);
            break;
         case VIEWSHEET_UPDATE:
            svc.rollbackViewsheetUpdate(this, txId, task, backupRef, reviewOutcome, user, failures);
            break;
         case WORKSHEET_RENAME:
            svc.rollbackWorksheetRename(this, txId, task, backupRef, reviewOutcome, user, failures);
            break;
         case WORKSHEET_UPDATE:
            svc.rollbackWorksheetUpdate(this, txId, task, backupRef, reviewOutcome, user, failures);
            break;
         case FOLDER_CREATE:
            svc.rollbackFolderCreate(this, txId, task, backupRef, reviewOutcome, user, failures);
            break;
         case FOLDER_DELETE:
            svc.rollbackFolderDelete(this, txId, task, backupRef, reviewOutcome, user, failures);
            break;
         case FOLDER_RENAME:
            svc.rollbackFolderRename(this, txId, task, backupRef, reviewOutcome, user, failures);
            break;
         case FOLDER_UPDATE:
            svc.rollbackFolderUpdate(this, txId, task, backupRef, reviewOutcome, user, failures);
            break;
         }
      }

      enum Kind {
         VIEWSHEET_RENAME, VIEWSHEET_UPDATE, WORKSHEET_RENAME, WORKSHEET_UPDATE, FOLDER_CREATE,
         FOLDER_DELETE, FOLDER_RENAME, FOLDER_UPDATE
      }

      final String key;
      final Kind kind;
      String currentAssetId;
      String beforePath;
      boolean beforeGlobal;
      IdentityID beforeOwner;
      String fullPath;
      IdentityID owner;
      String beforeAlias;
      String beforeDescription;
   }

   private static final Logger LOG = LoggerFactory.getLogger(ViewsheetChangesetApplyService.class);
   private static final SecureRandom RANDOM = new SecureRandom();
   /** Serializes the entire body of {@link #apply} -- same rationale and same JVM-local-only
    * limitation as every prior area's own lock. */
   private static final ReentrantLock APPLY_LOCK = new ReentrantLock();
   private final ViewsheetChangePlanService planService;
   private final ViewsheetService viewsheetApiService;
   private final ViewsheetFolderService folderService;
   private final AdminBackupService backupService;
   private final WorksheetService worksheetApiService;
}
