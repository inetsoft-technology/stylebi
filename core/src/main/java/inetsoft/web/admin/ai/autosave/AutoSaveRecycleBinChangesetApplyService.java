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
package inetsoft.web.admin.ai.autosave;

import inetsoft.sree.security.IdentityID;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.util.Tool;
import inetsoft.util.audit.*;
import inetsoft.web.AutoSaveServiceProxy;
import inetsoft.web.AutoSaveUtils;
import inetsoft.web.admin.ai.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.security.Principal;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Applies a whole Auto Save Recycle Bin changeset ({@code restore}/{@code delete}), all-or-nothing,
 * and audits every attempt -- the Auto Save Recycle Bin analog of
 * {@code RecycleBinChangesetApplyService}/{@code ScriptLibraryChangesetApplyService}.
 *
 * <p>Every verb here has a real, working rollback, INCLUDING a plain {@code delete} -- the draft's
 * raw bytes are captured via {@code AutoSaveUtils.getInputStream} before the mutating call and
 * replayed via {@code AutoSaveUtils.writeAutoSaveFile} on rollback, the same "capture the bytes,
 * restore them on rollback" shape Custom Shapes'/Script Library's own {@code delete} already
 * establish for this plugin. A {@code restore} that did not collide is ALSO fully compensable: its
 * own rollback removes the newly-created live sheet (via {@code AssetRepository.removeSheet}) and
 * replays the captured source bytes back into the recycle-bin bucket. A {@code restore} that
 * collided via {@code overwrite: true} is only PARTIALLY compensable -- the destroyed pre-existing
 * asset at the destination has no live inverse, the same "overwrite:true is worse than the verb
 * itself" caveat {@code RecycleBinChangesetApplyService}'s own restore already carries.
 *
 * <p>{@code restore}'s apply is a real two-step sequence, mirroring
 * {@code AutoSaveController.restoreAutoSaveAssets} exactly: first the real
 * {@code inetsoft.web.AutoSaveService.restoreAutoSaveAssets} primitive (invoked through its
 * cluster-proxy, {@link AutoSaveServiceProxy}) creates the live sheet, THEN the source draft is
 * deleted from the Auto Save Recycle Bin bucket as a separate step -- this class performs both,
 * capturing the source bytes before either happens so a later rollback can put them back
 * regardless of which half needs undoing.
 */
@Component
public class AutoSaveRecycleBinChangesetApplyService {
   @Autowired
   public AutoSaveRecycleBinChangesetApplyService(AutoSaveRecycleBinChangePlanService planService,
                                                  AutoSaveRecycleBinService autoSaveRecycleBinService,
                                                  AutoSaveServiceProxy autoSaveServiceProxy,
                                                  AssetRepository assetRepository,
                                                  AdminBackupService backupService)
   {
      this.planService = planService;
      this.autoSaveRecycleBinService = autoSaveRecycleBinService;
      this.autoSaveServiceProxy = autoSaveServiceProxy;
      this.assetRepository = assetRepository;
      this.backupService = backupService;
   }

   /**
    * Resolves, gates on the plan hash, backs up, then executes.
    *
    * @throws AdminChangesetApplyService.PlanHashMismatchException if the hash is missing or stale.
    * @throws IllegalArgumentException if {@code reviewOutcome} is blank while the plan requires
    *         agent signoff, or {@code acknowledgeIrreversibleDelete} is not exactly {@code true}
    *         while the plan contains any {@code delete} entry and/or a colliding
    *         {@code overwrite: true} {@code restore} entry.
    * @throws Exception if the Tier-2 backup itself fails, in which case nothing was applied.
    */
   public AutoSaveRecycleBinApplyResult apply(AutoSaveRecycleBinApplyRequest req, Principal user)
      throws Exception
   {
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

         boolean hasHighRisk = plan.changes().stream()
            .anyMatch(c -> AdminChangeRecord.RISK_HIGH.equals(c.risk()));

         if(hasHighRisk && !Boolean.TRUE.equals(req.getAcknowledgeIrreversibleDelete())) {
            throw new IllegalArgumentException(
               "acknowledgeIrreversibleDelete: must be true because this changeset contains a " +
               "delete entry and/or a restore entry that will overwrite (permanently destroy) an " +
               "existing asset at its destination");
         }

         String txId = "autosave-" + newIdSuffix();
         String backupRef = plan.requiresStorageBackup() ? backupService.backup(txId) : null;
         String reviewOutcome = req.getReviewOutcome();
         List<AutoSaveRecycleBinApplyOutcome> results = new ArrayList<>();
         List<Undo> undoable = new ArrayList<>();
         List<RollbackFailure> unknownStateFailures = new ArrayList<>();
         boolean failed = false;

         List<AutoSaveRecycleBinChangeRequest> originals = req.getChanges();

         for(int i = 0; i < plan.changes().size(); i++) {
            PlanChange change = plan.changes().get(i);
            AutoSaveRecycleBinChangeRequest original = originals.get(i);
            String key = change.property();

            AtomicBoolean mutationEntered = new AtomicBoolean(false);

            try {
               applyOne(txId, plan.task(), key, original, user, backupRef, reviewOutcome, results,
                       undoable, mutationEntered);
            }
            catch(Exception e) {
               results.add(new AutoSaveRecycleBinApplyOutcome(key, null, null,
                  AdminChangeRecord.STATUS_FAILED, messageOf(e), null));

               if(mutationEntered.get()) {
                  unknownStateFailures.add(new RollbackFailure(key,
                     "state unknown: apply did not return a verifiable outcome (" + messageOf(e) + ")"));
               }

               failed = true;
               break;
            }

            if(AdminChangeRecord.STATUS_FAILED.equals(lastStatus(results))) {
               failed = true;
               break;
            }
         }

         if(!failed) {
            return new AutoSaveRecycleBinApplyResult(txId, AdminChangesetApplyService.STATUS_APPLIED,
                                                     backupRef, Collections.unmodifiableList(results),
                                                     null);
         }

         List<RollbackFailure> failures = new ArrayList<>(unknownStateFailures);
         failures.addAll(rollback(txId, plan.task(), undoable, backupRef, reviewOutcome, user));

         if(failures.isEmpty()) {
            return new AutoSaveRecycleBinApplyResult(txId, AdminChangesetApplyService.STATUS_ROLLED_BACK,
                                                     backupRef, Collections.unmodifiableList(results),
                                                     null);
         }

         LOG.error("Autosave changeset {} rollback failed; entries still changed: {}", txId,
                  failures.stream().map(RollbackFailure::property).collect(Collectors.joining(", ")));
         return new AutoSaveRecycleBinApplyResult(txId, AdminChangesetApplyService.STATUS_ROLLBACK_FAILED,
                                                  backupRef, Collections.unmodifiableList(results),
                                                  Collections.unmodifiableList(failures));
      }
      finally {
         APPLY_LOCK.unlock();
      }
   }

   private void applyOne(String txId, String task, String key, AutoSaveRecycleBinChangeRequest original,
                         Principal user, String backupRef, String reviewOutcome,
                         List<AutoSaveRecycleBinApplyOutcome> results, List<Undo> undoable,
                         AtomicBoolean mutationEntered)
      throws Exception
   {
      String verb = AutoSaveRecycleBinChangePlanService.requireVerb("change", original.getVerb());

      if(AutoSaveRecycleBinChangeRequest.VERB_RESTORE.equals(verb)) {
         applyRestore(txId, task, key, original, user, backupRef, reviewOutcome, results, undoable,
                     mutationEntered);
      }
      else {
         applyDelete(txId, task, key, original, user, backupRef, reviewOutcome, results, undoable,
                    mutationEntered);
      }
   }

   // ---------------------------------------------------------------- restore

   private void applyRestore(String txId, String task, String key,
                             AutoSaveRecycleBinChangeRequest original, Principal user,
                             String backupRef, String reviewOutcome,
                             List<AutoSaveRecycleBinApplyOutcome> results, List<Undo> undoable,
                             AtomicBoolean mutationEntered)
      throws Exception
   {
      String id = original.getId();
      // Re-resolve fresh AT APPLY TIME -- never trust anything computed at preview.
      AutoSaveRecycleBinEntryProjection entry = autoSaveRecycleBinService.requireEntry(id, user);
      String assetName = original.getAssetName() == null || original.getAssetName().isBlank() ?
         entry.path() : original.getAssetName();
      boolean overwrite = Boolean.TRUE.equals(original.getOverwrite());
      boolean collides = autoSaveRecycleBinService.wouldCollide(entry, assetName, user);

      if(collides && !overwrite) {
         throw new IllegalArgumentException(
            "assetName: the destination \"" + assetName + "\" is already occupied by an existing " +
            entry.type() + " as of apply time -- it may have been created since preview; set " +
            "overwrite: true to replace it, or choose a different assetName");
      }

      String beforeProjection = AutoSaveRecycleBinChangePlanService.project(entry);
      byte[] capturedBytes = captureBytes(id, user);
      mutationEntered.set(true);
      boolean restored = autoSaveServiceProxy.restoreAutoSaveAssets(id, assetName, overwrite, user);

      if(!restored) {
         throw new IllegalArgumentException(
            "assetName: \"" + assetName + "\" was created since preview and overwrite was not " +
            "set -- nothing was restored");
      }

      AutoSaveUtils.deleteAutoSaveFile(id, user);

      boolean verified = !autoSaveRecycleBinService.exists(id, user);
      String advisory = collides ?
         "overwrite: true permanently destroyed the existing " + entry.type() + " that was at \"" +
            assetName + "\", bypassing recovery" : null;
      String status = verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED;
      results.add(new AutoSaveRecycleBinApplyOutcome(key, beforeProjection,
         verified ? "(restored to \"" + assetName + "\")" : null, status,
         verified ? null : "auto save recycle bin entry still present after restore", advisory));
      writeAudit(txId, task, key, objectTypeOf(entry.type()), collides ? AdminChangeRecord.RISK_HIGH :
                AdminChangeRecord.RISK_LOW, AdminChangeRecord.ACTION_APPLY, beforeProjection, null,
                status, backupRef, reviewOutcome, user);

      if(verified) {
         IdentityID actingUser = IdentityID.getIdentityIDFromKey(user.getName());
         AssetEntry.Type type = AutoSaveRecycleBinEntryProjection.TYPE_WORKSHEET.equals(entry.type()) ?
            AssetEntry.Type.WORKSHEET : AssetEntry.Type.VIEWSHEET;
         // Non-compensable if it collided with (and thereby destroyed) an existing asset: undoing
         // the restore itself cannot resurrect what overwrite:true already permanently deleted. The
         // created-sheet-removal half still queues normally either way -- only the destroyed
         // pre-existing asset is the part that stays gone.
         undoable.add(Undo.restore(key, id, capturedBytes, type, assetName, actingUser, !collides));
      }
   }

   // ---------------------------------------------------------------- delete

   private void applyDelete(String txId, String task, String key,
                            AutoSaveRecycleBinChangeRequest original, Principal user,
                            String backupRef, String reviewOutcome,
                            List<AutoSaveRecycleBinApplyOutcome> results, List<Undo> undoable,
                            AtomicBoolean mutationEntered)
      throws Exception
   {
      String id = original.getId();
      AutoSaveRecycleBinEntryProjection entry = autoSaveRecycleBinService.requireEntry(id, user);
      String beforeProjection = AutoSaveRecycleBinChangePlanService.project(entry);
      byte[] capturedBytes = captureBytes(id, user);
      mutationEntered.set(true);
      AutoSaveUtils.deleteAutoSaveFile(id, user);

      boolean verified = !autoSaveRecycleBinService.exists(id, user);
      String status = verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED;
      results.add(new AutoSaveRecycleBinApplyOutcome(key, beforeProjection, null, status,
         verified ? null : "auto save recycle bin entry still present after delete", null));
      writeAudit(txId, task, key, objectTypeOf(entry.type()), AdminChangeRecord.RISK_HIGH,
                AdminChangeRecord.ACTION_APPLY, beforeProjection, null, status, backupRef,
                reviewOutcome, user);

      if(verified) {
         undoable.add(Undo.delete(key, id, capturedBytes));
      }
   }

   private byte[] captureBytes(String id, Principal user) throws Exception {
      String file = AutoSaveUtils.getAutoSavedByName(id, true);

      try(InputStream in = AutoSaveUtils.getInputStream(file, user)) {
         return in == null ? null : in.readAllBytes();
      }
   }

   // ---------------------------------------------------------------- rollback

   private List<RollbackFailure> rollback(String txId, String task, List<Undo> undoable,
                                          String backupRef, String reviewOutcome, Principal user)
   {
      List<RollbackFailure> failures = new ArrayList<>();

      for(int i = undoable.size() - 1; i >= 0; i--) {
         Undo undo = undoable.get(i);

         try {
            rollbackOne(undo, txId, task, backupRef, reviewOutcome, user, failures);
         }
         catch(Exception e) {
            failures.add(new RollbackFailure(undo.key, messageOf(e)));
         }
      }

      return failures;
   }

   private void rollbackOne(Undo undo, String txId, String task, String backupRef,
                            String reviewOutcome, Principal user, List<RollbackFailure> failures)
      throws Exception
   {
      boolean verified = true;
      String advisory = null;

      if(undo.kind == UndoKind.RESTORE) {
         if(undo.compensable) {
            AssetEntry created = new AssetEntry(AssetRepository.GLOBAL_SCOPE, undo.type,
                                                undo.createdAssetName, undo.createdOwner);

            if(assetRepository.containsEntry(created)) {
               assetRepository.removeSheet(created, user, true);
            }

            verified = !assetRepository.containsEntry(created);
         }
         else {
            advisory = "the asset that overwrite:true destroyed at \"" + undo.createdAssetName +
               "\" was not restored -- there is no live inverse for it";
         }

         restoreBytes(undo, user, failures);
      }
      else {
         restoreBytes(undo, user, failures);
      }

      writeAudit(txId, task, undo.key, objectTypeOf(undo.type == AssetEntry.Type.WORKSHEET ?
                AutoSaveRecycleBinEntryProjection.TYPE_WORKSHEET :
                AutoSaveRecycleBinEntryProjection.TYPE_DASHBOARD), AdminChangeRecord.RISK_LOW,
                AdminChangeRecord.ACTION_ROLLBACK, null, null,
                verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED,
                backupRef, reviewOutcome, user);

      if(!verified) {
         failures.add(new RollbackFailure(undo.key,
            "rollback of " + undo.kind.name().toLowerCase() + " did not restore the prior state" +
            (advisory == null ? "" : " (" + advisory + ")")));
      }
   }

   private void restoreBytes(Undo undo, Principal user, List<RollbackFailure> failures) {
      if(undo.capturedBytes == null) {
         failures.add(new RollbackFailure(undo.key,
            "rollback could not restore the draft's content -- it was not captured before the " +
            "original mutation (the source was already unreadable at that time)"));
         return;
      }

      String file = AutoSaveUtils.getAutoSavedByName(undo.id, true);
      AutoSaveUtils.writeAutoSaveFile(undo.capturedBytes, file, user);
   }

   private static String objectTypeOf(String type) {
      return AutoSaveRecycleBinEntryProjection.TYPE_WORKSHEET.equals(type) ?
         ActionRecord.OBJECT_TYPE_WORKSHEET : ActionRecord.OBJECT_TYPE_DASHBOARD;
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
         LOG.error("Failed to write autosave admin change audit record for transaction {}", txId,
                   auditFailure);
      }
   }

   private static String lastStatus(List<AutoSaveRecycleBinApplyOutcome> results) {
      return results.isEmpty() ? null : results.get(results.size() - 1).status();
   }

   private static String messageOf(Exception e) {
      return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
   }

   private static String newIdSuffix() {
      return String.format("%016x", RANDOM.nextLong());
   }

   private enum UndoKind { RESTORE, DELETE }

   /** One undo descriptor built during apply, replayed in reverse by {@link #rollback}. Captures
    * the FULL pre-mutation bytes for both verbs -- still needed after the mutation succeeds and the
    * source entry itself is gone. */
   private static final class Undo {
      static Undo restore(String key, String id, byte[] capturedBytes, AssetEntry.Type type,
                          String createdAssetName, IdentityID createdOwner, boolean compensable)
      {
         return new Undo(UndoKind.RESTORE, key, id, capturedBytes, type, createdAssetName,
                         createdOwner, compensable);
      }

      static Undo delete(String key, String id, byte[] capturedBytes) {
         return new Undo(UndoKind.DELETE, key, id, capturedBytes, null, null, null, true);
      }

      private Undo(UndoKind kind, String key, String id, byte[] capturedBytes, AssetEntry.Type type,
                  String createdAssetName, IdentityID createdOwner, boolean compensable)
      {
         this.kind = kind;
         this.key = key;
         this.id = id;
         this.capturedBytes = capturedBytes;
         this.type = type;
         this.createdAssetName = createdAssetName;
         this.createdOwner = createdOwner;
         this.compensable = compensable;
      }

      final UndoKind kind;
      final String key;
      final String id;
      final byte[] capturedBytes;
      final AssetEntry.Type type;
      final String createdAssetName;
      final IdentityID createdOwner;
      final boolean compensable;
   }

   private static final Logger LOG = LoggerFactory.getLogger(AutoSaveRecycleBinChangesetApplyService.class);
   private static final SecureRandom RANDOM = new SecureRandom();
   private static final ReentrantLock APPLY_LOCK = new ReentrantLock();
   private final AutoSaveRecycleBinChangePlanService planService;
   private final AutoSaveRecycleBinService autoSaveRecycleBinService;
   private final AutoSaveServiceProxy autoSaveServiceProxy;
   private final AssetRepository assetRepository;
   private final AdminBackupService backupService;
}
