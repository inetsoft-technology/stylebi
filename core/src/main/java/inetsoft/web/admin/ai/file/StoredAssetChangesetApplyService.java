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
package inetsoft.web.admin.ai.file;

import inetsoft.util.DataSpace;
import inetsoft.util.Tool;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.AdminChangeRecord;
import inetsoft.util.audit.Audit;
import inetsoft.web.admin.ai.AdminBackupService;
import inetsoft.web.admin.ai.AdminChangesetApplyService;
import inetsoft.web.admin.ai.PlanChange;
import inetsoft.web.admin.ai.ResolvedPlan;
import inetsoft.web.admin.ai.RollbackFailure;
import inetsoft.web.admin.ai.TaskAuditToken;
import inetsoft.web.admin.content.dataspace.DataSpaceContentSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Applies a whole stored-asset changeset, all-or-nothing, and audits every attempt -- the
 * stored-asset analog of {@code DataSourceChangesetApplyService} (01-design.md section 6.3).
 *
 * <p>Every plan in this area is {@code requiresStorageBackup: true} unconditionally (01-design.md
 * section 6.3): the Tier-2 snapshot is taken synchronously here, before any change is attempted,
 * and is the offline recovery path for every non-compensable change -- never a substitute for a
 * live rollback where one exists.
 */
@Component
public class StoredAssetChangesetApplyService {
   @Autowired
   public StoredAssetChangesetApplyService(StoredAssetChangePlanService planService,
                                           DataSpaceContentSettingsService contentSettingsService,
                                           DataSpace dataSpace, AdminBackupService backupService)
   {
      this.planService = planService;
      this.contentSettingsService = contentSettingsService;
      this.dataSpace = dataSpace;
      this.backupService = backupService;
   }

   /**
    * Re-resolves fresh (never trusting anything captured at preview time), gates on the plan hash,
    * {@code reviewOutcome} (always required -- every plan here is high risk or storage-scoped), and
    * {@code acknowledgeIrreversibleDelete} (required exactly when the freshly re-resolved plan
    * contains a non-compensable change), backs up, then executes.
    */
   public StoredAssetApplyResult apply(StoredAssetApplyRequest req, Principal user) throws Exception {
      APPLY_LOCK.lock();

      try {
         List<StoredAssetChangePlanService.ResolvedChange> resolved = planService.resolveEntries(req);
         List<PlanChange> planChanges = new ArrayList<>();

         for(StoredAssetChangePlanService.ResolvedChange entry : resolved) {
            planChanges.add(entry.planChange());
         }

         String task = req.getTask().trim();
         String currentHash = StoredAssetChangePlanService.hash(planChanges);

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
               "reviewOutcome: required -- every stored-asset change plan is high risk or " +
               "touches storage (01-design.md section 6.3)");
         }

         boolean hasNonCompensable = resolved.stream().anyMatch(e -> !e.compensable());

         if(hasNonCompensable && !Boolean.TRUE.equals(req.getAcknowledgeIrreversibleDelete())) {
            throw new IllegalArgumentException(
               "acknowledgeIrreversibleDelete: must be true -- this changeset contains at least " +
               "one change with no live rollback (a folder delete, or a file delete/overwrite " +
               "whose prior content could not be captured as text); the Tier-2 snapshot taken for " +
               "this apply is the only recovery path for that change");
         }

         String txId = "storedasset-" + newIdSuffix();
         String backupRef = backupService.backup(txId);
         String reviewOutcome = req.getReviewOutcome();
         List<StoredAssetApplyOutcome> results = new ArrayList<>();
         List<Undo> undoable = new ArrayList<>();
         List<RollbackFailure> unknownStateFailures = new ArrayList<>();
         boolean failed = false;

         for(StoredAssetChangePlanService.ResolvedChange entry : resolved) {
            AtomicBoolean mutationEntered = new AtomicBoolean(false);

            try {
               applyOne(txId, reviewedTask, entry, user, backupRef, reviewOutcome, results, undoable,
                       mutationEntered);
            }
            catch(Exception e) {
               // A throw carries no verifiable before/after evidence for THIS change -- must never
               // be treated as rolled back, same rule every prior area's apply service follows.
               results.add(new StoredAssetApplyOutcome(entry.path(), entry.planChange().currentValue(),
                  null, AdminChangeRecord.STATUS_FAILED, messageOf(e)));

               // Only genuinely ambiguous when this entry's own mutating call actually started --
               // a throw from a pre-mutation guard (e.g. applyCreate's existence re-check) never
               // touched storage, so it must not force STATUS_ROLLBACK_FAILED.
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
            return new StoredAssetApplyResult(txId, AdminChangesetApplyService.STATUS_APPLIED,
               backupRef, Collections.unmodifiableList(results), null);
         }

         List<RollbackFailure> failures = new ArrayList<>(unknownStateFailures);
         failures.addAll(rollback(txId, reviewedTask, undoable, backupRef, reviewOutcome, user));
         String status = failures.isEmpty()
            ? AdminChangesetApplyService.STATUS_ROLLED_BACK
            : AdminChangesetApplyService.STATUS_ROLLBACK_FAILED;

         if(!failures.isEmpty()) {
            LOG.error("Stored-asset changeset {} rollback failed; paths still changed: {}", txId,
                     failures.stream().map(RollbackFailure::property).collect(Collectors.joining(", ")));
         }

         return new StoredAssetApplyResult(txId, status, backupRef,
            Collections.unmodifiableList(results),
            failures.isEmpty() ? null : Collections.unmodifiableList(failures));
      }
      finally {
         APPLY_LOCK.unlock();
      }
   }

   // ---------------------------------------------------------------- per-verb apply

   private void applyOne(String txId, String task, StoredAssetChangePlanService.ResolvedChange entry,
                         Principal user, String backupRef, String reviewOutcome,
                         List<StoredAssetApplyOutcome> results, List<Undo> undoable,
                         AtomicBoolean mutationEntered)
      throws Exception
   {
      switch(entry.verb()) {
         case StoredAssetChangeRequest.VERB_CREATE:
            applyCreate(txId, task, entry, user, backupRef, reviewOutcome, results, undoable,
                       mutationEntered);
            break;
         case StoredAssetChangeRequest.VERB_WRITE:
            applyWrite(txId, task, entry, user, backupRef, reviewOutcome, results, undoable,
                      mutationEntered);
            break;
         case StoredAssetChangeRequest.VERB_RENAME:
            applyRename(txId, task, entry, user, backupRef, reviewOutcome, results, undoable,
                       mutationEntered);
            break;
         default:
            applyDelete(txId, task, entry, user, backupRef, reviewOutcome, results, undoable,
                       mutationEntered);
      }
   }

   private void applyCreate(String txId, String task, StoredAssetChangePlanService.ResolvedChange entry,
                            Principal user, String backupRef, String reviewOutcome,
                            List<StoredAssetApplyOutcome> results, List<Undo> undoable,
                            AtomicBoolean mutationEntered)
   {
      String before = entry.planChange().currentValue();

      if(dataSpace.exists(null, entry.path())) {
         throw new IllegalArgumentException("\"" + entry.path() + "\" already exists");
      }

      mutationEntered.set(true);
      dataSpace.makeDirectory(entry.path());
      boolean verified = dataSpace.exists(null, entry.path()) && dataSpace.isDirectory(entry.path());
      String after = verified ? "exists=true (folder)" : null;
      String status = verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED;
      results.add(new StoredAssetApplyOutcome(entry.path(), before, after, status,
         verified ? null : "folder was not present after create"));
      writeAudit(txId, task, entry.path(), ActionRecord.OBJECT_TYPE_FOLDER,
                AdminChangeRecord.ACTION_APPLY, before, after, status, backupRef, reviewOutcome, user);

      if(verified) {
         undoable.add(Undo.createdFolder(entry.path()));
      }
   }

   private void applyWrite(String txId, String task, StoredAssetChangePlanService.ResolvedChange entry,
                           Principal user, String backupRef, String reviewOutcome,
                           List<StoredAssetApplyOutcome> results, List<Undo> undoable,
                           AtomicBoolean mutationEntered)
      throws Exception
   {
      String before = entry.planChange().currentValue();
      byte[] bytes = entry.content().getBytes(StandardCharsets.UTF_8);
      mutationEntered.set(true);
      dataSpace.withOutputStream(null, entry.path(),
         out -> Tool.fileCopy(new ByteArrayInputStream(bytes), out));
      contentSettingsService.updateFolder(entry.path());
      boolean verified = dataSpace.exists(null, entry.path()) &&
         dataSpace.getFileLength(null, entry.path()) == bytes.length;
      String after = verified ? "exists=true;size=" + bytes.length : null;
      String status = verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED;
      results.add(new StoredAssetApplyOutcome(entry.path(), before, after, status,
         verified ? null : "file did not read back the written size"));
      writeAudit(txId, task, entry.path(), ActionRecord.OBJECT_TYPE_FILE,
                AdminChangeRecord.ACTION_APPLY, before, after, status, backupRef, reviewOutcome, user);

      if(verified && entry.compensable()) {
         undoable.add(entry.priorText() == null
            ? Undo.wroteNew(entry.path()) : Undo.overwroteText(entry.path(), entry.priorText()));
      }
   }

   private void applyRename(String txId, String task, StoredAssetChangePlanService.ResolvedChange entry,
                            Principal user, String backupRef, String reviewOutcome,
                            List<StoredAssetApplyOutcome> results, List<Undo> undoable,
                            AtomicBoolean mutationEntered)
   {
      String before = entry.planChange().currentValue();
      mutationEntered.set(true);
      boolean success = dataSpace.rename(entry.path(), entry.newPath());
      String after = success ? entry.newPath() : null;
      String status = success ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED;
      results.add(new StoredAssetApplyOutcome(entry.path(), before, after, status,
         success ? null : "rename did not succeed"));
      String objectType = StoredAssetChangeRequest.UNIT_FOLDER.equals(entry.unitType())
         ? ActionRecord.OBJECT_TYPE_FOLDER : ActionRecord.OBJECT_TYPE_FILE;
      writeAudit(txId, task, entry.path(), objectType, AdminChangeRecord.ACTION_APPLY, before, after,
                status, backupRef, reviewOutcome, user);

      if(success) {
         contentSettingsService.onFileRenamed(entry.path(), entry.newPath());
         undoable.add(Undo.renamed(entry.newPath(), entry.path()));
      }
   }

   private void applyDelete(String txId, String task, StoredAssetChangePlanService.ResolvedChange entry,
                            Principal user, String backupRef, String reviewOutcome,
                            List<StoredAssetApplyOutcome> results, List<Undo> undoable,
                            AtomicBoolean mutationEntered)
   {
      String before = entry.planChange().currentValue();
      boolean folder = StoredAssetChangeRequest.UNIT_FOLDER.equals(entry.unitType());
      mutationEntered.set(true);
      contentSettingsService.deleteDataSpaceNode(entry.path(), folder);
      boolean verified = !dataSpace.exists(null, entry.path());
      String status = verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED;
      results.add(new StoredAssetApplyOutcome(entry.path(), before, null, status,
         verified ? null : "entry still present after delete"));
      String objectType = folder ? ActionRecord.OBJECT_TYPE_FOLDER : ActionRecord.OBJECT_TYPE_FILE;
      writeAudit(txId, task, entry.path(), objectType, AdminChangeRecord.ACTION_APPLY, before, null,
                status, backupRef, reviewOutcome, user);

      // Delete has a live inverse ONLY when compensable (a captured file text under cap) -- a
      // folder delete and a non-compensable file delete are never added to `undoable`, matching
      // Data Sources' own "delete has no live inverse in this cut" precedent for its own delete.
      if(verified && !folder && entry.compensable() && entry.priorText() != null) {
         undoable.add(Undo.deletedFile(entry.path(), entry.priorText()));
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
         String key = undo.originalPath != null ? undo.originalPath : undo.path;

         try {
            switch(undo.kind) {
               case CREATED_FOLDER:
               case WROTE_NEW:
                  dataSpace.delete(null, undo.path);
                  break;
               case OVERWROTE_TEXT:
               case DELETED_FILE:
                  byte[] priorBytes = undo.priorText.getBytes(StandardCharsets.UTF_8);
                  dataSpace.withOutputStream(null, undo.path,
                     out -> Tool.fileCopy(new ByteArrayInputStream(priorBytes), out));
                  break;
               case RENAMED:
                  dataSpace.rename(undo.path, undo.originalPath);
                  break;
            }

            writeAudit(txId, task, key, ActionRecord.OBJECT_TYPE_FILE,
                      AdminChangeRecord.ACTION_ROLLBACK, null, null, AdminChangeRecord.STATUS_VERIFIED,
                      backupRef, reviewOutcome, user);
         }
         catch(Exception e) {
            failures.add(new RollbackFailure(key, messageOf(e)));
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
         LOG.error("Failed to write stored-asset admin change audit record for transaction {}", txId,
                   auditFailure);
      }
   }

   private static String lastStatus(List<StoredAssetApplyOutcome> results) {
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
      enum Kind { CREATED_FOLDER, WROTE_NEW, OVERWROTE_TEXT, DELETED_FILE, RENAMED }

      static Undo createdFolder(String path) { return new Undo(Kind.CREATED_FOLDER, path, null, null); }
      static Undo wroteNew(String path) { return new Undo(Kind.WROTE_NEW, path, null, null); }

      static Undo overwroteText(String path, String priorText) {
         return new Undo(Kind.OVERWROTE_TEXT, path, null, priorText);
      }

      static Undo deletedFile(String path, String priorText) {
         return new Undo(Kind.DELETED_FILE, path, null, priorText);
      }

      static Undo renamed(String newPath, String originalPath) {
         return new Undo(Kind.RENAMED, newPath, originalPath, null);
      }

      private Undo(Kind kind, String path, String originalPath, String priorText) {
         this.kind = kind;
         this.path = path;
         this.originalPath = originalPath;
         this.priorText = priorText;
      }

      final Kind kind;
      final String path;
      final String originalPath;
      final String priorText;
   }

   private static final Logger LOG = LoggerFactory.getLogger(StoredAssetChangesetApplyService.class);
   private static final SecureRandom RANDOM = new SecureRandom();
   /** Serializes the entire body of {@link #apply} -- same rationale and same JVM-local-only
    * limitation as every prior area's own lock. */
   private static final ReentrantLock APPLY_LOCK = new ReentrantLock();
   private final StoredAssetChangePlanService planService;
   private final DataSpaceContentSettingsService contentSettingsService;
   private final DataSpace dataSpace;
   private final AdminBackupService backupService;
}
