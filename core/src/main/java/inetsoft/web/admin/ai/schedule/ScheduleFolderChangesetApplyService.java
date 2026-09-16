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

import inetsoft.sree.security.IdentityID;
import inetsoft.uql.asset.internal.AssetFolder;
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
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Applies a whole schedule-task FOLDER changeset, all-or-nothing, and audits every attempt -- the
 * folder-shaped analog of {@link ScheduleChangesetApplyService}, replicated rather than shared
 * (same precedent).
 *
 * <p>Unlike the schedule-TASK area, a folder {@code delete}'s rollback is only a PARTIAL inverse
 * when the folder was non-empty: re-creating an empty folder at the same path restores the LABEL,
 * not the schedule task(s) that were permanently destroyed underneath it (there is no snapshot
 * capture here the way a task delete's {@code captureSpec} takes). It is still queued for rollback
 * -- restoring the label is strictly better than leaving it deleted too when a LATER entry in the
 * same plan fails -- mirroring {@code ViewsheetChangesetApplyService#applyFolderDelete}'s own
 * identical, explicitly-disclosed choice for its own (also potentially-destructive, post-#76469)
 * folder delete.
 */
@Component
public class ScheduleFolderChangesetApplyService {
   @Autowired
   public ScheduleFolderChangesetApplyService(ScheduleFolderChangePlanService planService,
                                              AdminScheduleFolderGateway folderGateway,
                                              AdminBackupService backupService)
   {
      this.planService = planService;
      this.folderGateway = folderGateway;
      this.backupService = backupService;
   }

   /**
    * Resolves, gates on the plan hash and task token, then executes.
    *
    * @throws AdminChangesetApplyService.PlanHashMismatchException if the hash is missing or stale
    *         (maps to HTTP 409) -- the exception type is reused verbatim from the properties area,
    *         not redeclared.
    * @throws AdminChangesetApplyService.TaskTokenMismatchException if the taskToken is missing,
    *         malformed, or was issued for a different planHash (also maps to HTTP 409, also reused
    *         verbatim).
    * @throws Exception if the Tier-2 backup fails, in which case nothing was applied.
    */
   public ApplyResult apply(ScheduleFolderApplyRequest req, Principal user) throws Exception {
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

         String txId = "schedfolder-" + newIdSuffix();
         String backupRef = plan.requiresStorageBackup() ? backupService.backup(txId) : null;
         List<ApplyOutcome> results = new ArrayList<>();
         List<Undo> undoable = new ArrayList<>();
         List<RollbackFailure> unknownStateFailures = new ArrayList<>();
         boolean failed = false;

         List<ScheduleFolderChangeRequest> originals = req.getChanges();

         for(int i = 0; i < plan.changes().size(); i++) {
            PlanChange change = plan.changes().get(i);
            ScheduleFolderChangeRequest original = originals.get(i);
            String path = change.property();

            try {
               switch(original.getVerb()) {
               case ScheduleFolderChangeRequest.VERB_CREATE:
                  applyCreate(txId, reviewedTask, path, backupRef, req.getReviewOutcome(), user,
                             results, undoable);
                  break;
               case ScheduleFolderChangeRequest.VERB_RENAME:
                  applyRename(txId, reviewedTask, path, original.getNewPath(), backupRef,
                             req.getReviewOutcome(), user, results, undoable);
                  break;
               case ScheduleFolderChangeRequest.VERB_MOVE:
                  applyMove(txId, reviewedTask, path, original.getTargetPath(), backupRef,
                           req.getReviewOutcome(), user, results, undoable);
                  break;
               default:
                  applyDelete(txId, reviewedTask, path, backupRef, req.getReviewOutcome(), user,
                             results, undoable);
                  break;
               }
            }
            catch(Exception e) {
               // A throw carries no verifiable before/after evidence for THIS change -- unlike a
               // reported failure, it must never be treated as rolled back. Same rule every prior
               // area's apply service follows.
               results.add(new ApplyOutcome(path, null, null, AdminChangeRecord.STATUS_FAILED,
                                            messageOf(e)));
               unknownStateFailures.add(new RollbackFailure(path,
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
            return new ApplyResult(txId, AdminChangesetApplyService.STATUS_APPLIED, backupRef,
                                   Collections.unmodifiableList(results), null);
         }

         List<RollbackFailure> failures = new ArrayList<>(unknownStateFailures);
         failures.addAll(rollback(txId, reviewedTask, undoable, backupRef, req.getReviewOutcome(),
                                  user));

         if(failures.isEmpty()) {
            return new ApplyResult(txId, AdminChangesetApplyService.STATUS_ROLLED_BACK, backupRef,
                                   Collections.unmodifiableList(results), null);
         }

         LOG.error("Schedule-task-folder changeset {} rollback failed; folders still changed: {}",
                  txId,
                  failures.stream().map(RollbackFailure::property).collect(Collectors.joining(", ")));
         return new ApplyResult(txId, AdminChangesetApplyService.STATUS_ROLLBACK_FAILED, backupRef,
                                Collections.unmodifiableList(results),
                                Collections.unmodifiableList(failures));
      }
      finally {
         APPLY_LOCK.unlock();
      }
   }

   private void applyCreate(String txId, String task, String path, String backupRef,
                            String reviewOutcome, Principal user, List<ApplyOutcome> results,
                            List<Undo> undoable)
      throws Exception
   {
      folderGateway.createFolder(path, user);

      AssetFolder after = folderGateway.findFolder(path);
      boolean verified = after != null;
      String afterProjection = ScheduleFolderXmlProjection.project(path, after);
      String status = verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED;
      results.add(new ApplyOutcome(path, null, afterProjection, status,
                                   verified ? null : "folder not found after create"));
      writeAudit(txId, task, path, ActionRecord.ACTION_NAME_CREATE, AdminChangeRecord.ACTION_APPLY,
                AdminChangeRecord.RISK_LOW, null, afterProjection, status, backupRef, reviewOutcome,
                user);

      if(verified) {
         undoable.add(Undo.create(path));
      }
   }

   private void applyRename(String txId, String task, String oldPath, String newPath,
                            String backupRef, String reviewOutcome, Principal user,
                            List<ApplyOutcome> results, List<Undo> undoable)
      throws Exception
   {
      AssetFolder before = folderGateway.findFolder(oldPath);
      String beforeProjection = ScheduleFolderXmlProjection.project(oldPath, before);

      folderGateway.renameFolder(oldPath, newPath, user);

      String normalizedNewPath = AdminScheduleFolderGateway.normalizePath(newPath);
      AssetFolder after = folderGateway.findFolder(normalizedNewPath);
      boolean verified = after != null && folderGateway.findFolder(oldPath) == null;
      String afterProjection = ScheduleFolderXmlProjection.project(normalizedNewPath, after);
      String status = verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED;
      results.add(new ApplyOutcome(oldPath, beforeProjection, afterProjection, status,
                                   verified ? null : "folder not found at the new path after rename"));
      writeAudit(txId, task, oldPath, ActionRecord.ACTION_NAME_RENAME, AdminChangeRecord.ACTION_APPLY,
                AdminChangeRecord.RISK_LOW, beforeProjection, afterProjection, status, backupRef,
                reviewOutcome, user);

      if(verified) {
         undoable.add(Undo.rename(normalizedNewPath, oldPath));
      }
   }

   private void applyMove(String txId, String task, String path, String targetPath,
                          String backupRef, String reviewOutcome, Principal user,
                          List<ApplyOutcome> results, List<Undo> undoable)
      throws Exception
   {
      AssetFolder before = folderGateway.findFolder(path);
      String beforeProjection = ScheduleFolderXmlProjection.project(path, before);
      String originalParentPath = AdminScheduleFolderGateway.parentOf(path);
      String resultingPath =
         AdminScheduleFolderGateway.joinPath(targetPath, AdminScheduleFolderGateway.leafOf(path));

      folderGateway.moveFolder(path, targetPath, user);

      AssetFolder after = folderGateway.findFolder(resultingPath);
      boolean verified = after != null && (resultingPath.equals(path) || folderGateway.findFolder(path) == null);
      String afterProjection = ScheduleFolderXmlProjection.project(resultingPath, after);
      String status = verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED;
      results.add(new ApplyOutcome(path, beforeProjection, afterProjection, status,
                                   verified ? null : "folder not found at the target path after move"));
      writeAudit(txId, task, path, ActionRecord.ACTION_NAME_MOVE, AdminChangeRecord.ACTION_APPLY,
                AdminChangeRecord.RISK_LOW, beforeProjection, afterProjection, status, backupRef,
                reviewOutcome, user);

      if(verified) {
         undoable.add(Undo.move(resultingPath, originalParentPath));
      }
   }

   private void applyDelete(String txId, String task, String path, String backupRef,
                            String reviewOutcome, Principal user, List<ApplyOutcome> results,
                            List<Undo> undoable)
      throws Exception
   {
      AssetFolder before = folderGateway.findFolder(path);
      String beforeProjection = ScheduleFolderXmlProjection.project(path, before);
      IdentityID ownerBefore = before == null ? null : before.getOwner();
      // Re-run the non-empty preflight against LIVE state at apply time too (a concurrent save
      // into the folder since preview could have added content) -- same reasoning
      // ViewsheetChangesetApplyService#applyFolderDelete documents for its own re-check.
      int containedTaskCount = folderGateway.countContainedTasks(path);
      String risk = containedTaskCount == 0 ? AdminChangeRecord.RISK_LOW : AdminChangeRecord.RISK_HIGH;

      folderGateway.deleteFolder(path, user);

      boolean verified = folderGateway.findFolder(path) == null;
      String status = verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED;
      results.add(new ApplyOutcome(path, beforeProjection, null, status,
                                   verified ? null : "folder still exists after delete"));
      writeAudit(txId, task, path, ActionRecord.ACTION_NAME_DELETE, AdminChangeRecord.ACTION_APPLY,
                risk, beforeProjection, null, status, backupRef, reviewOutcome, user);

      if(verified) {
         // NOT a complete inverse when the folder was non-empty at delete time -- re-adding via
         // create restores only the empty folder label; every contained schedule task was already
         // permanently destroyed and nothing here resurrects it. Still queued for rollback because
         // restoring the label is strictly better than leaving it deleted too when another entry
         // in the same plan fails -- see this class's own javadoc.
         undoable.add(Undo.delete(path, ownerBefore));
      }
   }

   /** Undoes verified changes newest-first, attempting all of them and collecting any failures. */
   private List<RollbackFailure> rollback(String txId, String task, List<Undo> undoable,
                                          String backupRef, String reviewOutcome, Principal user)
   {
      List<RollbackFailure> failures = new ArrayList<>();

      for(int i = undoable.size() - 1; i >= 0; i--) {
         Undo undo = undoable.get(i);

         try {
            switch(undo.kind) {
            case CREATE:
               rollbackCreate(undo, txId, task, backupRef, reviewOutcome, user, failures);
               break;
            case DELETE:
               rollbackDelete(undo, txId, task, backupRef, reviewOutcome, user, failures);
               break;
            case RENAME:
               rollbackRename(undo, txId, task, backupRef, reviewOutcome, user, failures);
               break;
            default:
               rollbackMove(undo, txId, task, backupRef, reviewOutcome, user, failures);
               break;
            }
         }
         catch(Exception e) {
            failures.add(new RollbackFailure(undo.key, messageOf(e)));
         }
      }

      return failures;
   }

   private void rollbackCreate(Undo undo, String txId, String task, String backupRef,
                               String reviewOutcome, Principal user, List<RollbackFailure> failures)
      throws Exception
   {
      folderGateway.deleteFolder(undo.path, user);
      boolean verified = folderGateway.findFolder(undo.path) == null;
      writeAudit(txId, task, undo.key, ActionRecord.ACTION_NAME_DELETE,
                AdminChangeRecord.ACTION_ROLLBACK, AdminChangeRecord.RISK_LOW, null, null,
                verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED,
                backupRef, reviewOutcome, user);

      if(!verified) {
         failures.add(new RollbackFailure(undo.key, "rollback of create did not remove the folder"));
      }
   }

   private void rollbackDelete(Undo undo, String txId, String task, String backupRef,
                               String reviewOutcome, Principal user, List<RollbackFailure> failures)
      throws Exception
   {
      // Recreates an EMPTY folder at the same path -- restores the label, not any contained
      // schedule task that was permanently destroyed (see applyDelete's own javadoc). The owner
      // captured at delete time cannot be forced back explicitly (addFolder always inherits the
      // parent's own owner), so the recreated folder's owner may differ from undo.ownerBefore if
      // the parent's own owner changed in between -- a further disclosed, known incompleteness.
      folderGateway.createFolder(undo.path, user);
      boolean verified = folderGateway.findFolder(undo.path) != null;
      writeAudit(txId, task, undo.key, ActionRecord.ACTION_NAME_CREATE,
                AdminChangeRecord.ACTION_ROLLBACK, AdminChangeRecord.RISK_LOW, null, null,
                verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED,
                backupRef, reviewOutcome, user);

      if(!verified) {
         failures.add(new RollbackFailure(undo.key, "rollback of delete did not restore the folder"));
      }
   }

   private void rollbackRename(Undo undo, String txId, String task, String backupRef,
                               String reviewOutcome, Principal user, List<RollbackFailure> failures)
      throws Exception
   {
      // undo.path holds the NEW (current) path, undo.beforePath the ORIGINAL path -- swap.
      folderGateway.renameFolder(undo.path, undo.beforePath, user);
      boolean verified = folderGateway.findFolder(undo.beforePath) != null;
      writeAudit(txId, task, undo.key, ActionRecord.ACTION_NAME_RENAME,
                AdminChangeRecord.ACTION_ROLLBACK, AdminChangeRecord.RISK_LOW, null, null,
                verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED,
                backupRef, reviewOutcome, user);

      if(!verified) {
         failures.add(
            new RollbackFailure(undo.key, "rollback of rename did not restore the prior path"));
      }
   }

   private void rollbackMove(Undo undo, String txId, String task, String backupRef,
                             String reviewOutcome, Principal user, List<RollbackFailure> failures)
      throws Exception
   {
      // undo.path holds the folder's CURRENT (post-move) path, undo.beforePath the ORIGINAL
      // PARENT path -- move it back.
      String restoredPath =
         AdminScheduleFolderGateway.joinPath(undo.beforePath, AdminScheduleFolderGateway.leafOf(undo.path));
      folderGateway.moveFolder(undo.path, undo.beforePath, user);
      boolean verified = folderGateway.findFolder(restoredPath) != null;
      writeAudit(txId, task, undo.key, ActionRecord.ACTION_NAME_MOVE,
                AdminChangeRecord.ACTION_ROLLBACK, AdminChangeRecord.RISK_LOW, null, null,
                verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED,
                backupRef, reviewOutcome, user);

      if(!verified) {
         failures.add(new RollbackFailure(undo.key, "rollback of move did not restore the prior parent"));
      }
   }

   private void writeAudit(String txId, String task, String path, String actionRecordName,
                           String adminAction, String risk, String before, String after,
                           String status, String backupRef, String reviewOutcome, Principal user)
   {
      try {
         AdminChangeRecord record = new AdminChangeRecord();
         record.setTransactionId(txId);
         record.setTaskDescription(task);
         record.setProperty(path);
         record.setObjectType(ActionRecord.OBJECT_TYPE_FOLDER);
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
         // apply service follows.
         LOG.error("Failed to write schedule-task-folder admin change audit record for transaction {}",
                   txId, auditFailure);
      }
   }

   private static String lastStatus(List<ApplyOutcome> results) {
      return results.isEmpty() ? null : results.get(results.size() - 1).status();
   }

   private static String messageOf(Throwable e) {
      return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
   }

   private static String newIdSuffix() {
      return String.format("%016x", RANDOM.nextLong());
   }

   /** One undo descriptor built during apply, replayed in reverse by {@link #rollback}. */
   private static final class Undo {
      private enum Kind { CREATE, DELETE, RENAME, MOVE }

      static Undo create(String path) {
         return new Undo(Kind.CREATE, path, path, null);
      }

      /** @param ownerBefore disclosed-best-effort only; see {@link #rollbackDelete}'s own comment. */
      static Undo delete(String path, IdentityID ownerBefore) {
         return new Undo(Kind.DELETE, path, path, null);
      }

      static Undo rename(String newPath, String oldPath) {
         return new Undo(Kind.RENAME, newPath, newPath, oldPath);
      }

      /** @param currentPath the folder's path AFTER the move. @param originalParentPath the
       * folder's PARENT path BEFORE the move. */
      static Undo move(String currentPath, String originalParentPath) {
         return new Undo(Kind.MOVE, currentPath, currentPath, originalParentPath);
      }

      private Undo(Kind kind, String key, String path, String beforePath) {
         this.kind = kind;
         this.key = key;
         this.path = path;
         this.beforePath = beforePath;
      }

      final Kind kind;
      /** The plan's own {@code property} key, used only for a {@link RollbackFailure}. */
      final String key;
      final String path;
      /** RENAME: the original path. MOVE: the original parent path. Unused for CREATE/DELETE. */
      final String beforePath;
   }

   private static final Logger LOG = LoggerFactory.getLogger(ScheduleFolderChangesetApplyService.class);
   private static final SecureRandom RANDOM = new SecureRandom();
   /** Serializes the entire body of {@link #apply} -- same rationale/limitations as {@code
    * AdminChangesetApplyService#APPLY_LOCK} (JVM-local only). */
   private static final ReentrantLock APPLY_LOCK = new ReentrantLock();
   private final ScheduleFolderChangePlanService planService;
   private final AdminScheduleFolderGateway folderGateway;
   private final AdminBackupService backupService;
}
