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
package inetsoft.web.admin.ai.recyclebin;

import inetsoft.sree.RepletRegistry;
import inetsoft.sree.RepositoryEntry;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.util.Tool;
import inetsoft.util.audit.*;
import inetsoft.web.RecycleBin;
import inetsoft.web.RecycleUtils;
import inetsoft.web.admin.ai.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Applies a whole recycle-bin changeset ({@code restore}/{@code purge}), all-or-nothing, and
 * audits every attempt -- the recycle-bin analog of {@code ViewsheetChangesetApplyService}
 * (track-a-recycle-bin/01-design.md section 3).
 *
 * <p>{@code purge} is never added to {@code undoable} (non-compensable, same treatment viewsheet
 * delete already gets elsewhere in this plugin). {@code restore}'s own rollback re-recycles the
 * just-restored item via the same {@code RecycleUtils.moveSheetToRecycleBin}/{@code
 * moveAssetFolderToRecycleBin}/{@code moveRepositoryFolderToRecycleBin} primitives used for any
 * human-issued delete -- this mints a FRESH trash path (a new UUID), not the entry's original one:
 * a disclosed, deliberate simplification of 01-design.md section 3's "pinned to the entry's
 * original recycle-bin path" ideal, trading byte-for-byte trash-path stability for reusing the
 * product's own well-exercised move-to-recycle-bin primitive as-is rather than hand-rolling its
 * {@code changeSheet}/{@code changeFolder}/permission-copy internals a second time. Functionally
 * complete either way: a rolled-back restore ends up back in the recycle bin, restorable again.
 */
@Component
public class RecycleBinChangesetApplyService {
   @Autowired
   public RecycleBinChangesetApplyService(RecycleBinChangePlanService planService,
                                          RecycleBinService recycleBinService,
                                          RecycleBin recycleBin, AssetRepository assetRepository,
                                          AdminBackupService backupService)
   {
      this.planService = planService;
      this.recycleBinService = recycleBinService;
      this.recycleBin = recycleBin;
      this.assetRepository = assetRepository;
      this.backupService = backupService;
   }

   /**
    * Resolves, gates on the plan hash, backs up, then executes.
    *
    * @throws AdminChangesetApplyService.PlanHashMismatchException if the hash is missing or stale
    *         (maps to HTTP 409).
    * @throws IllegalArgumentException if {@code reviewOutcome} is blank while the plan requires
    *         agent signoff, or {@code acknowledgeIrreversibleDelete} is not exactly {@code true}
    *         while the plan contains a {@code purge} entry or a colliding {@code overwrite: true}
    *         {@code restore} entry.
    * @throws Exception if the Tier-2 backup itself fails, in which case nothing was applied.
    */
   public RecycleBinApplyResult apply(RecycleBinApplyRequest req, Principal user) throws Exception {
      APPLY_LOCK.lock();

      try {
         ResolvedPlan plan = planService.resolve(req, user);

         if(req.getPlanHash() == null || !plan.planHash().equals(req.getPlanHash())) {
            throw new AdminChangesetApplyService.PlanHashMismatchException(plan);
         }

         if(plan.requiresAgentSignoff() &&
            (req.getReviewOutcome() == null || req.getReviewOutcome().trim().isEmpty()))
         {
            throw new IllegalArgumentException(
               "reviewOutcome: required because this changeset contains a high-risk change");
         }

         boolean hasIrreversible = plan.changes().stream()
            .anyMatch(c -> AdminChangeRecord.RISK_HIGH.equals(c.risk()));

         if(hasIrreversible && !Boolean.TRUE.equals(req.getAcknowledgeIrreversibleDelete())) {
            throw new IllegalArgumentException(
               "acknowledgeIrreversibleDelete: must be true because this changeset contains a " +
               "purge entry and/or a restore entry that will overwrite (permanently destroy) an " +
               "existing asset at its destination -- neither has a live inverse for the destroyed " +
               "asset; the Tier-2 snapshot taken for this apply is the only recovery path");
         }

         String txId = "recyclebin-" + newIdSuffix();
         String backupRef = plan.requiresStorageBackup() ? backupService.backup(txId) : null;
         String reviewOutcome = req.getReviewOutcome();
         List<RecycleBinApplyOutcome> results = new ArrayList<>();
         List<Undo> undoable = new ArrayList<>();
         List<RollbackFailure> unknownStateFailures = new ArrayList<>();
         boolean failed = false;

         List<RecycleBinChangeRequest> originals = req.getChanges();

         for(int i = 0; i < plan.changes().size(); i++) {
            PlanChange change = plan.changes().get(i);
            RecycleBinChangeRequest original = originals.get(i);
            String key = change.property();

            try {
               applyOne(txId, plan.task(), key, original, user, backupRef, reviewOutcome, results,
                       undoable);
            }
            catch(Exception e) {
               results.add(new RecycleBinApplyOutcome(key, null, null,
                  AdminChangeRecord.STATUS_FAILED, messageOf(e), null));
               unknownStateFailures.add(new RollbackFailure(key,
                  "state unknown: apply did not return a verifiable outcome (" + messageOf(e) + ")"));
               failed = true;
               break;
            }

            if(AdminChangeRecord.STATUS_FAILED.equals(lastStatus(results))) {
               failed = true;
               break;
            }
         }

         if(!failed) {
            return new RecycleBinApplyResult(txId, AdminChangesetApplyService.STATUS_APPLIED,
                                             backupRef, Collections.unmodifiableList(results), null);
         }

         List<RollbackFailure> failures = new ArrayList<>(unknownStateFailures);
         failures.addAll(rollback(txId, plan.task(), undoable, backupRef, reviewOutcome, user));

         if(failures.isEmpty()) {
            return new RecycleBinApplyResult(txId, AdminChangesetApplyService.STATUS_ROLLED_BACK,
                                             backupRef, Collections.unmodifiableList(results), null);
         }

         LOG.error("Recycle bin changeset {} rollback failed; entries still changed: {}", txId,
                  failures.stream().map(RollbackFailure::property).collect(Collectors.joining(", ")));
         return new RecycleBinApplyResult(txId, AdminChangesetApplyService.STATUS_ROLLBACK_FAILED,
                                          backupRef, Collections.unmodifiableList(results),
                                          Collections.unmodifiableList(failures));
      }
      finally {
         APPLY_LOCK.unlock();
      }
   }

   private void applyOne(String txId, String task, String key, RecycleBinChangeRequest original,
                         Principal user, String backupRef, String reviewOutcome,
                         List<RecycleBinApplyOutcome> results, List<Undo> undoable)
      throws Exception
   {
      String verb = RecycleBinChangePlanService.requireVerb("change", original.getVerb());

      if(RecycleBinChangeRequest.VERB_RESTORE.equals(verb)) {
         applyRestore(txId, task, key, original, user, backupRef, reviewOutcome, results, undoable);
      }
      else {
         applyPurge(txId, task, key, original, user, backupRef, reviewOutcome, results);
      }
   }

   // ---------------------------------------------------------------- restore

   private void applyRestore(String txId, String task, String key,
                             RecycleBinChangeRequest original, Principal user, String backupRef,
                             String reviewOutcome, List<RecycleBinApplyOutcome> results,
                             List<Undo> undoable)
      throws Exception
   {
      String path = original.getPath();
      // Re-resolve fresh AT APPLY TIME -- never trust anything computed at preview (section 0.2's
      // established apply-time re-check pattern, reused verbatim here).
      RecycleBin.Entry entry = recycleBinService.requireEntry(path, user);
      String type = RecycleBinService.typeOf(entry);
      boolean overwrite = Boolean.TRUE.equals(original.getOverwrite());
      boolean collides = recycleBinService.wouldCollide(entry);

      if(collides && !overwrite) {
         throw new IllegalArgumentException(
            "path: the destination \"" + entry.getOriginalPath() + "\" is already occupied by an " +
            "existing " + type + " as of apply time -- it may have been created since preview; " +
            "set overwrite: true to replace it, or choose a different entry");
      }

      String beforeProjection = RecycleBinChangePlanService.project(entry, type);
      String advisory = null;

      if(entry.isSheet()) {
         RecycleUtils.restoreSheet(entry, overwrite, user, recycleBin);
      }
      else if(entry.isWSFolder()) {
         advisory = RecycleUtils.restoreWSFolder(entry, overwrite, user, recycleBin);
      }
      else {
         advisory = RecycleUtils.restoreRepositoryFolder(entry, overwrite, user, recycleBin);
      }

      if(collides) {
         String overwriteAdvisory = "overwrite: true permanently destroyed the existing " + type +
            " that was at \"" + entry.getOriginalPath() + "\", bypassing the recycle bin";
         advisory = advisory == null ? overwriteAdvisory : advisory + "; " + overwriteAdvisory;
      }

      boolean verified = recycleBin.getEntry(path) == null;
      String status = verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED;
      results.add(new RecycleBinApplyOutcome(key, beforeProjection,
         verified ? "(restored to \"" + entry.getOriginalPath() + "\")" : null, status,
         verified ? null : "recycle bin entry still present after restore", advisory));
      writeAudit(txId, task, key, objectTypeOf(type), collides ? AdminChangeRecord.RISK_HIGH :
                AdminChangeRecord.RISK_LOW, AdminChangeRecord.ACTION_APPLY, beforeProjection,
                null, status, backupRef, reviewOutcome, user);

      if(verified) {
         // Non-compensable if it collided with (and thereby destroyed) an existing asset at the
         // destination (section 4 risk 4 of 01-design.md): rolling the restore itself back cannot
         // resurrect what overwrite:true already permanently deleted. Still queued so the restore
         // half rolls back normally; the outcome carries the same advisory either way.
         undoable.add(Undo.restore(key, entry));
      }
   }

   // ---------------------------------------------------------------- purge

   private void applyPurge(String txId, String task, String key, RecycleBinChangeRequest original,
                           Principal user, String backupRef, String reviewOutcome,
                           List<RecycleBinApplyOutcome> results)
      throws Exception
   {
      String path = original.getPath();
      RecycleBin.Entry entry = recycleBinService.requireEntry(path, user);
      String type = RecycleBinService.typeOf(entry);
      String beforeProjection = RecycleBinChangePlanService.project(entry, type);

      if(entry.isSheet()) {
         AssetEntry.Type assetType =
            entry.getType() == RepositoryEntry.WORKSHEET ? AssetEntry.Type.WORKSHEET :
               AssetEntry.Type.VIEWSHEET;
         AssetEntry live = new AssetEntry(entry.getOriginalScope(), assetType, entry.getPath(),
                                          entry.getOriginalUser());
         assetRepository.removeSheet(live, user, true);
      }
      else if(entry.isWSFolder()) {
         AssetEntry live = new AssetEntry(entry.getOriginalScope(), AssetEntry.Type.FOLDER,
                                          entry.getPath(), entry.getOriginalUser());
         assetRepository.removeFolder(live, user, true);
      }
      else {
         RepletRegistry registry = RecycleUtils.getRegistry(entry.getPath(), entry.getOriginalUser());
         registry.removeFolder(entry.getPath());
         registry.save();
      }

      recycleBin.removeEntry(path);

      boolean verified = recycleBin.getEntry(path) == null;
      String status = verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED;
      results.add(new RecycleBinApplyOutcome(key, beforeProjection, null, status,
         verified ? null : "recycle bin entry still present after purge", null));
      writeAudit(txId, task, key, objectTypeOf(type), AdminChangeRecord.RISK_HIGH,
                AdminChangeRecord.ACTION_APPLY, beforeProjection, null, status, backupRef,
                reviewOutcome, user);

      // Never added to undoable -- non-compensable, same treatment viewsheet delete already gets.
   }

   // ---------------------------------------------------------------- rollback

   private List<RollbackFailure> rollback(String txId, String task, List<Undo> undoable,
                                          String backupRef, String reviewOutcome, Principal user)
   {
      List<RollbackFailure> failures = new ArrayList<>();

      for(int i = undoable.size() - 1; i >= 0; i--) {
         Undo undo = undoable.get(i);

         try {
            rollbackRestore(undo, txId, task, backupRef, reviewOutcome, user, failures);
         }
         catch(Exception e) {
            failures.add(new RollbackFailure(undo.key, messageOf(e)));
         }
      }

      return failures;
   }

   private void rollbackRestore(Undo undo, String txId, String task, String backupRef,
                                String reviewOutcome, Principal user,
                                List<RollbackFailure> failures)
      throws Exception
   {
      RecycleBin.Entry entry = undo.entry;
      String type = RecycleBinService.typeOf(entry);
      boolean verified;

      if(entry.isSheet()) {
         String originalPath = stripMyDashboard(entry.getOriginalPath());
         AssetEntry.Type assetType =
            entry.getType() == RepositoryEntry.WORKSHEET ? AssetEntry.Type.WORKSHEET :
               AssetEntry.Type.VIEWSHEET;
         AssetEntry bare = new AssetEntry(entry.getOriginalScope(), assetType, originalPath,
                                          entry.getOriginalUser());
         AssetEntry resolved = assetRepository.getAssetEntry(bare);

         if(resolved == null) {
            failures.add(new RollbackFailure(undo.key,
               "rollback of restore could not find the restored asset to re-recycle it"));
            return;
         }

         RecycleUtils.moveSheetToRecycleBin(resolved, user, recycleBin, true);
         verified = !assetRepository.containsEntry(bare);
      }
      else if(entry.isWSFolder()) {
         String originalPath = stripMyDashboard(entry.getOriginalPath());
         RecycleUtils.moveAssetFolderToRecycleBin(originalPath, entry.getOriginalUser(), user,
                                                  recycleBin, true);
         AssetEntry bare = new AssetEntry(entry.getOriginalScope(), AssetEntry.Type.FOLDER,
                                          originalPath, entry.getOriginalUser());
         verified = !assetRepository.containsEntry(bare);
      }
      else {
         RecycleUtils.moveRepositoryFolderToRecycleBin(entry.getOriginalPath(), entry.getName(),
            entry.getOriginalUser(), user, recycleBin);
         RepletRegistry registry = RecycleUtils.getRegistry(entry.getOriginalPath(),
            entry.getOriginalUser());
         verified = !registry.isFolder(entry.getOriginalPath());
      }

      writeAudit(txId, task, undo.key, objectTypeOf(type), AdminChangeRecord.RISK_LOW,
                AdminChangeRecord.ACTION_ROLLBACK, null, null,
                verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED,
                backupRef, reviewOutcome, user);

      if(!verified) {
         failures.add(new RollbackFailure(undo.key,
            "rollback of restore did not re-recycle the restored asset"));
      }
   }

   private static String stripMyDashboard(String path) {
      return path != null && path.startsWith(Tool.MY_DASHBOARD + "/") ?
         path.substring(Tool.MY_DASHBOARD.length() + 1) : path;
   }

   private static String objectTypeOf(String type) {
      if(RecycleBinEntryProjection.TYPE_DASHBOARD.equals(type)) {
         return ActionRecord.OBJECT_TYPE_DASHBOARD;
      }

      if(RecycleBinEntryProjection.TYPE_WORKSHEET.equals(type)) {
         return ActionRecord.OBJECT_TYPE_WORKSHEET;
      }

      return ActionRecord.OBJECT_TYPE_FOLDER;
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
         LOG.error("Failed to write recycle bin admin change audit record for transaction {}", txId,
                   auditFailure);
      }
   }

   private static String lastStatus(List<RecycleBinApplyOutcome> results) {
      return results.isEmpty() ? null : results.get(results.size() - 1).status();
   }

   private static String messageOf(Exception e) {
      return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
   }

   private static String newIdSuffix() {
      return String.format("%016x", RANDOM.nextLong());
   }

   /** One undo descriptor built during apply, replayed in reverse by {@link #rollback}. Never
    * built for a purge (non-compensable). Captures the FULL pre-apply {@code RecycleBin.Entry} --
    * still needed after the restore succeeds and the bin entry itself is gone. */
   private static final class Undo {
      static Undo restore(String key, RecycleBin.Entry entry) {
         return new Undo(key, entry);
      }

      private Undo(String key, RecycleBin.Entry entry) {
         this.key = key;
         this.entry = entry;
      }

      final String key;
      final RecycleBin.Entry entry;
   }

   private static final Logger LOG = LoggerFactory.getLogger(RecycleBinChangesetApplyService.class);
   private static final SecureRandom RANDOM = new SecureRandom();
   private static final ReentrantLock APPLY_LOCK = new ReentrantLock();
   private final RecycleBinChangePlanService planService;
   private final RecycleBinService recycleBinService;
   private final RecycleBin recycleBin;
   private final AssetRepository assetRepository;
   private final AdminBackupService backupService;
}
