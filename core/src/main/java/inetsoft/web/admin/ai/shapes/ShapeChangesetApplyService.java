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
package inetsoft.web.admin.ai.shapes;

import inetsoft.uql.viewsheet.graph.aesthetic.ImageShapes;
import inetsoft.util.DataSpace;
import inetsoft.util.Tool;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.AdminChangeRecord;
import inetsoft.util.audit.Audit;
import inetsoft.web.admin.ai.AdminBackupService;
import inetsoft.web.admin.ai.AdminChangesetApplyService;
import inetsoft.web.admin.ai.ApplyOutcome;
import inetsoft.web.admin.ai.ApplyResult;
import inetsoft.web.admin.ai.PlanChange;
import inetsoft.web.admin.ai.ResolvedPlan;
import inetsoft.web.admin.ai.RollbackFailure;
import inetsoft.web.admin.ai.TaskAuditToken;
import inetsoft.web.admin.content.dataspace.DataSpaceContentSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Applies a whole custom-shape changeset, all-or-nothing, and audits every attempt -- the
 * custom-shape analog of {@code inetsoft.web.admin.ai.file.StoredAssetChangesetApplyService}
 * (01-design.md section 2.3).
 *
 * <p>Every plan in this area is {@code requiresStorageBackup: true} unconditionally: the Tier-2
 * snapshot is taken synchronously here, before any change is attempted. Unlike Stored Assets,
 * there is no {@code acknowledgeIrreversibleDelete} gate at all -- every entry here is
 * unconditionally compensable by construction (01-design.md section 1.4/3.2), so there is no
 * non-compensable case to acknowledge.
 */
@Component
public class ShapeChangesetApplyService {
   @Autowired
   public ShapeChangesetApplyService(ShapeChangePlanService planService,
                                     DataSpaceContentSettingsService contentSettingsService,
                                     DataSpace dataSpace, AdminBackupService backupService)
   {
      this.planService = planService;
      this.contentSettingsService = contentSettingsService;
      this.dataSpace = dataSpace;
      this.backupService = backupService;
   }

   /**
    * Re-resolves fresh (never trusting anything captured at preview time), gates on the plan hash
    * and {@code reviewOutcome} (always required -- every plan here is high risk and touches
    * storage), backs up, then executes.
    */
   public ApplyResult apply(ShapeApplyRequest req, Principal user) throws Exception {
      APPLY_LOCK.lock();

      try {
         List<ShapeChangePlanService.ResolvedChange> resolved = planService.resolveEntries(req, user);
         List<PlanChange> planChanges = new ArrayList<>();

         for(ShapeChangePlanService.ResolvedChange entry : resolved) {
            planChanges.add(entry.planChange());
         }

         String task = req.getTask().trim();
         String currentHash = ShapeChangePlanService.hash(planChanges);

         if(req.getPlanHash() == null || !currentHash.equals(req.getPlanHash())) {
            throw new AdminChangesetApplyService.PlanHashMismatchException(
               new ResolvedPlan(task, planChanges, true, true, currentHash, null));
         }

         String reviewedTask;

         try {
            reviewedTask = TaskAuditToken.verify(req.getTaskToken(), currentHash);
         }
         catch(TaskAuditToken.TaskTokenException e) {
            throw new AdminChangesetApplyService.TaskTokenMismatchException(
               new ResolvedPlan(task, planChanges, true, true, currentHash, null), e.getMessage());
         }

         if(req.getReviewOutcome() == null || req.getReviewOutcome().trim().isEmpty()) {
            throw new IllegalArgumentException(
               "reviewOutcome: required -- every custom-shape change plan is high risk and " +
               "touches storage");
         }

         String txId = "shapes-" + newIdSuffix();
         String backupRef = backupService.backup(txId);
         String reviewOutcome = req.getReviewOutcome();
         List<ApplyOutcome> results = new ArrayList<>();
         List<Undo> undoable = new ArrayList<>();
         List<RollbackFailure> unknownStateFailures = new ArrayList<>();
         boolean failed = false;

         for(ShapeChangePlanService.ResolvedChange entry : resolved) {
            AtomicBoolean mutationEntered = new AtomicBoolean(false);

            try {
               applyOne(txId, reviewedTask, entry, user, backupRef, reviewOutcome, results, undoable,
                       mutationEntered);
            }
            catch(Exception e) {
               // A throw carries no verifiable before/after evidence for THIS change -- must never
               // be treated as rolled back, same rule every prior area's apply service follows.
               results.add(new ApplyOutcome(entry.path(), entry.planChange().currentValue(), null,
                  AdminChangeRecord.STATUS_FAILED, messageOf(e)));

               if(mutationEntered.get()) {
                  unknownStateFailures.add(new RollbackFailure(entry.path(),
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
            return new ApplyResult(txId, AdminChangesetApplyService.STATUS_APPLIED, backupRef,
               Collections.unmodifiableList(results), null);
         }

         List<RollbackFailure> failures = new ArrayList<>(unknownStateFailures);
         failures.addAll(rollback(txId, reviewedTask, undoable, backupRef, reviewOutcome, user));
         String status = failures.isEmpty()
            ? AdminChangesetApplyService.STATUS_ROLLED_BACK
            : AdminChangesetApplyService.STATUS_ROLLBACK_FAILED;

         if(!failures.isEmpty()) {
            LOG.error("Custom-shape changeset {} rollback failed; paths still changed: {}", txId,
                     failures.stream().map(RollbackFailure::property).collect(Collectors.joining(", ")));
         }

         return new ApplyResult(txId, status, backupRef, Collections.unmodifiableList(results),
            failures.isEmpty() ? null : Collections.unmodifiableList(failures));
      }
      finally {
         APPLY_LOCK.unlock();
      }
   }

   // ---------------------------------------------------------------- per-verb apply

   private void applyOne(String txId, String task, ShapeChangePlanService.ResolvedChange entry,
                         Principal user, String backupRef, String reviewOutcome,
                         List<ApplyOutcome> results, List<Undo> undoable,
                         AtomicBoolean mutationEntered)
      throws Exception
   {
      if(ShapeChangeRequest.VERB_UPLOAD.equals(entry.verb())) {
         applyUpload(txId, task, entry, user, backupRef, reviewOutcome, results, undoable,
                    mutationEntered);
      }
      else {
         applyDelete(txId, task, entry, user, backupRef, reviewOutcome, results, undoable,
                    mutationEntered);
      }
   }

   private void applyUpload(String txId, String task, ShapeChangePlanService.ResolvedChange entry,
                           Principal user, String backupRef, String reviewOutcome,
                           List<ApplyOutcome> results, List<Undo> undoable,
                           AtomicBoolean mutationEntered)
      throws Exception
   {
      String before = entry.planChange().currentValue();
      byte[] content = entry.content();
      mutationEntered.set(true);
      dataSpace.withOutputStream(entry.parentDir(), entry.name(), out -> out.write(content));
      // uploadDataSpaceFiles normally does this for us -- our wrapper bypasses that method
      // (01-design.md section 1.5), so it must replicate the cache-clear itself.
      clearShapeCache(entry.scope());
      boolean verified = dataSpace.exists(null, entry.path()) &&
         dataSpace.getFileLength(null, entry.path()) == content.length;
      String after = verified ? entry.planChange().proposedValue() : null;
      String status = verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED;
      results.add(new ApplyOutcome(entry.path(), before, after, status,
         verified ? null : "shape did not read back the written size"));
      writeAudit(txId, task, entry.path(), ActionRecord.OBJECT_TYPE_FILE,
                AdminChangeRecord.ACTION_APPLY, before, after, status, backupRef, reviewOutcome, user);

      if(verified) {
         undoable.add(entry.priorContentBase64() == null
            ? Undo.uploadedNew(entry.path(), entry.parentDir(), entry.name(), entry.scope())
            : Undo.overwrote(entry.path(), entry.parentDir(), entry.name(), entry.scope(),
                             entry.priorContentBase64()));
      }
   }

   private void applyDelete(String txId, String task, ShapeChangePlanService.ResolvedChange entry,
                           Principal user, String backupRef, String reviewOutcome,
                           List<ApplyOutcome> results, List<Undo> undoable,
                           AtomicBoolean mutationEntered)
      throws Exception
   {
      String before = entry.planChange().currentValue();
      mutationEntered.set(true);
      contentSettingsService.deleteDataSpaceNode(entry.path(), false);
      // deleteDataSpaceNode already clears the shape cache internally (01-design.md section 1.2) --
      // do NOT also call ImageShapes.clear*() here; this asymmetry with applyUpload is deliberate.
      boolean verified = !dataSpace.exists(null, entry.path());
      String status = verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED;
      results.add(new ApplyOutcome(entry.path(), before, null, status,
         verified ? null : "shape still present after delete"));
      writeAudit(txId, task, entry.path(), ActionRecord.OBJECT_TYPE_FILE,
                AdminChangeRecord.ACTION_APPLY, before, null, status, backupRef, reviewOutcome, user);

      if(verified) {
         // priorContentBase64 is always present for delete (unconditional capture, 01-design.md
         // section 1.4), so this branch always has a live rollback.
         undoable.add(Undo.deleted(entry.path(), entry.parentDir(), entry.name(), entry.scope(),
            entry.priorContentBase64()));
      }
   }

   private static void clearShapeCache(String scope) {
      if(ShapeChangeRequest.SCOPE_GLOBAL.equals(scope)) {
         // Every organization falls back to the global shapes directory, so a global change must
         // invalidate all of them, not just the calling principal's own org.
         ImageShapes.clearAllShapes();
      }
      else {
         ImageShapes.clearShapes();
      }
   }

   // ---------------------------------------------------------------- rollback

   /** Undoes verified changes newest-first, attempting all of them and collecting any failures. */
   private List<RollbackFailure> rollback(String txId, String task, List<Undo> undoable,
                                          String backupRef, String reviewOutcome, Principal user)
   {
      List<RollbackFailure> failures = new ArrayList<>();

      for(int i = undoable.size() - 1; i >= 0; i--) {
         Undo undo = undoable.get(i);

         try {
            switch(undo.kind) {
               case UPLOADED_NEW:
                  dataSpace.delete(null, undo.path);
                  break;
               case OVERWROTE:
               case DELETED:
                  byte[] priorBytes = Base64.getDecoder().decode(undo.priorContentBase64);
                  dataSpace.withOutputStream(undo.parentDir, undo.name, out -> out.write(priorBytes));
                  break;
            }

            clearShapeCache(undo.scope);
            writeAudit(txId, task, undo.path, ActionRecord.OBJECT_TYPE_FILE,
                      AdminChangeRecord.ACTION_ROLLBACK, null, null, AdminChangeRecord.STATUS_VERIFIED,
                      backupRef, reviewOutcome, user);
         }
         catch(Exception e) {
            failures.add(new RollbackFailure(undo.path, messageOf(e)));
         }
      }

      return failures;
   }

   private void writeAudit(String txId, String task, String key, String objectType,
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
         record.setRiskLevel(AdminChangeRecord.RISK_HIGH);
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
         // apply service follows.
         LOG.error("Failed to write custom-shape admin change audit record for transaction {}", txId,
                   auditFailure);
      }
   }

   private static String lastStatus(List<ApplyOutcome> results) {
      return results.isEmpty() ? null : results.get(results.size() - 1).status();
   }

   private static String messageOf(Exception e) {
      return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
   }

   private static String newIdSuffix() {
      return String.format("%016x", RANDOM.nextLong());
   }

   /** One undo descriptor built during apply, replayed in reverse by {@link #rollback}. */
   private static final class Undo {
      enum Kind { UPLOADED_NEW, OVERWROTE, DELETED }

      static Undo uploadedNew(String path, String parentDir, String name, String scope) {
         return new Undo(Kind.UPLOADED_NEW, path, parentDir, name, scope, null);
      }

      static Undo overwrote(String path, String parentDir, String name, String scope,
                           String priorContentBase64)
      {
         return new Undo(Kind.OVERWROTE, path, parentDir, name, scope, priorContentBase64);
      }

      static Undo deleted(String path, String parentDir, String name, String scope,
                         String priorContentBase64)
      {
         return new Undo(Kind.DELETED, path, parentDir, name, scope, priorContentBase64);
      }

      private Undo(Kind kind, String path, String parentDir, String name, String scope,
                  String priorContentBase64)
      {
         this.kind = kind;
         this.path = path;
         this.parentDir = parentDir;
         this.name = name;
         this.scope = scope;
         this.priorContentBase64 = priorContentBase64;
      }

      final Kind kind;
      final String path;
      final String parentDir;
      final String name;
      final String scope;
      final String priorContentBase64;
   }

   private static final Logger LOG = LoggerFactory.getLogger(ShapeChangesetApplyService.class);
   private static final SecureRandom RANDOM = new SecureRandom();
   /** Serializes the entire body of {@link #apply} -- same rationale and same JVM-local-only
    * limitation as every prior area's own lock. */
   private static final ReentrantLock APPLY_LOCK = new ReentrantLock();
   private final ShapeChangePlanService planService;
   private final DataSpaceContentSettingsService contentSettingsService;
   private final DataSpace dataSpace;
   private final AdminBackupService backupService;
}
