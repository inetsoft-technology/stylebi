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
import java.util.concurrent.atomic.AtomicBoolean;
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
         // Whether the item that threw (if any) had already entered its own mutating call
         // (createFolder/renameFolder/moveFolder/moveTask/deleteFolder) before the throw -- only
         // that case is a genuine partial-mutation risk that must force STATUS_ROLLBACK_FAILED on
         // its own; a throw that fires strictly before the mutating call means the item was never
         // touched, so it must not by itself override an otherwise fully-verified rollback (bug
         // 76856, mirroring bug 76808's DataSourceChangesetApplyService fix).
         boolean unknownStateMutationEntered = false;

         List<ScheduleFolderChangeRequest> originals = req.getChanges();

         for(int i = 0; i < plan.changes().size(); i++) {
            PlanChange change = plan.changes().get(i);
            ScheduleFolderChangeRequest original = originals.get(i);
            String path = change.property();
            AtomicBoolean mutationEntered = new AtomicBoolean(false);

            try {
               switch(original.getVerb()) {
               case ScheduleFolderChangeRequest.VERB_CREATE:
                  applyCreate(txId, reviewedTask, path, backupRef, req.getReviewOutcome(), user,
                             results, undoable, mutationEntered);
                  break;
               case ScheduleFolderChangeRequest.VERB_RENAME:
                  applyRename(txId, reviewedTask, path, original.getNewPath(), backupRef,
                             req.getReviewOutcome(), user, results, undoable, mutationEntered);
                  break;
               case ScheduleFolderChangeRequest.VERB_MOVE:
                  applyMove(txId, reviewedTask, path, original.getTargetPath(), backupRef,
                           req.getReviewOutcome(), user, results, undoable, mutationEntered);
                  break;
               case ScheduleFolderChangeRequest.VERB_MOVE_TASK:
                  applyMoveTask(txId, reviewedTask, path, original.getTargetPath(), backupRef,
                               req.getReviewOutcome(), user, results, undoable, mutationEntered);
                  break;
               default:
                  applyDelete(txId, reviewedTask, path, backupRef, req.getReviewOutcome(), user,
                             results, undoable, mutationEntered);
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
            return new ApplyResult(txId, AdminChangesetApplyService.STATUS_APPLIED, backupRef,
                                   Collections.unmodifiableList(results), null);
         }

         List<RollbackFailure> rollbackOwnFailures =
            rollback(txId, reviewedTask, undoable, backupRef, req.getReviewOutcome(), user);

         // An unknownStateFailures entry only forces rollback-failed when that item's own mutating
         // call had actually been entered (a real partial-mutation risk); if it never touched the
         // folder tree, it must not by itself override an otherwise fully-verified rollback.
         if(rollbackOwnFailures.isEmpty() && !unknownStateMutationEntered) {
            return new ApplyResult(txId, AdminChangesetApplyService.STATUS_ROLLED_BACK, backupRef,
                                   Collections.unmodifiableList(results), null);
         }

         List<RollbackFailure> failures = new ArrayList<>(unknownStateFailures);
         failures.addAll(rollbackOwnFailures);
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
                            List<Undo> undoable, AtomicBoolean mutationEntered)
      throws Exception
   {
      // createFolder's own addFolder calls throw strictly before ITS mutating write, so
      // mutationEntered must be threaded through rather than set here -- setting it before this
      // call would (incorrectly) mark a pure permission refusal as a partial-mutation risk (bug
      // 76856), the same reasoning applyMoveTask already documents for moveTask.
      folderGateway.createFolder(path, user, mutationEntered);

      AssetFolder after = folderGateway.findFolder(path);
      boolean verified = after != null;
      String afterProjection = ScheduleFolderXmlProjection.project(path, after);
      String status = verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED;
      results.add(new ApplyOutcome(path, null, afterProjection, status,
                                   verified ? null : "folder not found after create"));
      writeAudit(txId, task, path, ActionRecord.ACTION_NAME_CREATE, AdminChangeRecord.ACTION_APPLY,
                AdminChangeRecord.RISK_LOW, null, afterProjection, status, backupRef, reviewOutcome,
                user, ActionRecord.OBJECT_TYPE_FOLDER);

      if(verified) {
         undoable.add(Undo.create(path));
      }
   }

   private void applyRename(String txId, String task, String oldPath, String newPath,
                            String backupRef, String reviewOutcome, Principal user,
                            List<ApplyOutcome> results, List<Undo> undoable,
                            AtomicBoolean mutationEntered)
      throws Exception
   {
      AssetFolder before = folderGateway.findFolder(oldPath);
      String beforeProjection = ScheduleFolderXmlProjection.project(oldPath, before);

      // renameFolder's own DELETE/WRITE permission checks and folder-existence check throw
      // strictly before ITS mutating call, so mutationEntered must be threaded through rather than
      // set here -- see applyCreate's own comment above.
      folderGateway.renameFolder(oldPath, newPath, user, mutationEntered);

      String normalizedNewPath = AdminScheduleFolderGateway.normalizePath(newPath);
      AssetFolder after = folderGateway.findFolder(normalizedNewPath);
      boolean verified = after != null && folderGateway.findFolder(oldPath) == null;
      String afterProjection = ScheduleFolderXmlProjection.project(normalizedNewPath, after);
      String status = verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED;
      results.add(new ApplyOutcome(oldPath, beforeProjection, afterProjection, status,
                                   verified ? null : "folder not found at the new path after rename"));
      writeAudit(txId, task, oldPath, ActionRecord.ACTION_NAME_RENAME, AdminChangeRecord.ACTION_APPLY,
                AdminChangeRecord.RISK_LOW, beforeProjection, afterProjection, status, backupRef,
                reviewOutcome, user, ActionRecord.OBJECT_TYPE_FOLDER);

      if(verified) {
         undoable.add(Undo.rename(normalizedNewPath, oldPath));
      }
   }

   private void applyMove(String txId, String task, String path, String targetPath,
                          String backupRef, String reviewOutcome, Principal user,
                          List<ApplyOutcome> results, List<Undo> undoable,
                          AtomicBoolean mutationEntered)
      throws Exception
   {
      AssetFolder before = folderGateway.findFolder(path);
      String beforeProjection = ScheduleFolderXmlProjection.project(path, before);
      String originalParentPath = AdminScheduleFolderGateway.parentOf(path);
      String resultingPath =
         AdminScheduleFolderGateway.joinPath(targetPath, AdminScheduleFolderGateway.leafOf(path));

      // moveFolder's own WRITE-on-target and DELETE-on-source permission checks throw strictly
      // before ITS mutating call, so mutationEntered must be threaded through rather than set here
      // -- see applyCreate's own comment above.
      folderGateway.moveFolder(path, targetPath, user, mutationEntered);

      AssetFolder after = folderGateway.findFolder(resultingPath);
      boolean verified = after != null && (resultingPath.equals(path) || folderGateway.findFolder(path) == null);
      String afterProjection = ScheduleFolderXmlProjection.project(resultingPath, after);
      String status = verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED;
      results.add(new ApplyOutcome(path, beforeProjection, afterProjection, status,
                                   verified ? null : "folder not found at the target path after move"));
      writeAudit(txId, task, path, ActionRecord.ACTION_NAME_MOVE, AdminChangeRecord.ACTION_APPLY,
                AdminChangeRecord.RISK_LOW, beforeProjection, afterProjection, status, backupRef,
                reviewOutcome, user, ActionRecord.OBJECT_TYPE_FOLDER);

      if(verified) {
         undoable.add(Undo.move(resultingPath, originalParentPath));
      }
   }

   /**
    * Bug #76841: the task-move analog of {@link #applyMove}. {@code taskId} is the plan's own
    * {@code property} key for this verb (see {@link ScheduleFolderChangePlanService#resolveMoveTask}),
    * not a folder path.
    *
    * <p>Verification reads the task's own live folder back via {@link
    * AdminScheduleFolderGateway#getTaskPath} and compares it against the normalized target -- this
    * alone would NOT have caught the missing-target-folder defect {@code resolveMoveTask}'s own
    * {@code folderExists} guard exists to prevent (the underlying primitive updates the task's path
    * unconditionally even when the target folder was never registered), which is exactly why that
    * guard lives at plan time instead of being deferred to this read-back.
    */
   private void applyMoveTask(String txId, String task, String taskId, String targetPath,
                              String backupRef, String reviewOutcome, Principal user,
                              List<ApplyOutcome> results, List<Undo> undoable,
                              AtomicBoolean mutationEntered)
      throws Exception
   {
      String beforePath = folderGateway.getTaskPath(taskId);
      String normalizedTargetPath = AdminScheduleFolderGateway.normalizePath(targetPath);

      // moveTask's own permission/removable checks throw strictly before ITS mutating call
      // (taskFolderService.moveScheduleItems), so mutationEntered must be threaded through rather
      // than set here -- setting it before this call would (incorrectly) mark a pure permission
      // refusal as a partial-mutation risk (bug #76856).
      folderGateway.moveTask(taskId, targetPath, user, mutationEntered);

      String afterPath = folderGateway.getTaskPath(taskId);
      boolean verified = afterPath != null &&
         AdminScheduleFolderGateway.normalizePath(afterPath).equals(normalizedTargetPath);
      String status = verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED;
      results.add(new ApplyOutcome(taskId, beforePath, afterPath, status,
                                   verified ? null : "task's folder was not updated to the target path after move"));
      writeAudit(txId, task, taskId, ActionRecord.ACTION_NAME_MOVE, AdminChangeRecord.ACTION_APPLY,
                AdminChangeRecord.RISK_LOW, beforePath, afterPath, status, backupRef, reviewOutcome,
                user, ActionRecord.OBJECT_TYPE_TASK);

      if(verified) {
         undoable.add(Undo.moveTask(taskId, beforePath));
      }
   }

   private void applyDelete(String txId, String task, String path, String backupRef,
                            String reviewOutcome, Principal user, List<ApplyOutcome> results,
                            List<Undo> undoable, AtomicBoolean mutationEntered)
      throws Exception
   {
      AssetFolder before = folderGateway.findFolder(path);
      String beforeProjection = ScheduleFolderXmlProjection.project(path, before);
      // Re-run the non-empty preflight against LIVE state at apply time too (a concurrent save
      // into the folder since preview could have added content) -- same reasoning
      // ViewsheetChangesetApplyService#applyFolderDelete documents for its own re-check.
      int containedTaskCount = folderGateway.countContainedTasks(path);
      String risk = containedTaskCount == 0 ? AdminChangeRecord.RISK_LOW : AdminChangeRecord.RISK_HIGH;

      mutationEntered.set(true);
      folderGateway.deleteFolder(path, user);

      boolean verified = folderGateway.findFolder(path) == null;
      String status = verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED;
      results.add(new ApplyOutcome(path, beforeProjection, null, status,
                                   verified ? null : "folder still exists after delete"));
      writeAudit(txId, task, path, ActionRecord.ACTION_NAME_DELETE, AdminChangeRecord.ACTION_APPLY,
                risk, beforeProjection, null, status, backupRef, reviewOutcome, user,
                ActionRecord.OBJECT_TYPE_FOLDER);

      if(verified) {
         // NOT a complete inverse when the folder was non-empty at delete time -- re-adding via
         // create restores only the empty folder label; every contained schedule task was already
         // permanently destroyed and nothing here resurrects it. Still queued for rollback because
         // restoring the label is strictly better than leaving it deleted too when another entry
         // in the same plan fails -- see this class's own javadoc.
         undoable.add(Undo.delete(path));
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
            case MOVE:
               rollbackMove(undo, txId, task, backupRef, reviewOutcome, user, failures);
               break;
            default:
               rollbackMoveTask(undo, txId, task, backupRef, reviewOutcome, user, failures);
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
                backupRef, reviewOutcome, user, ActionRecord.OBJECT_TYPE_FOLDER);

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
      // the folder had at delete time cannot be forced back explicitly (addFolder always inherits
      // the parent's own owner), so the recreated folder's owner may differ from what it was
      // before deletion -- a further disclosed, known incompleteness.
      folderGateway.createFolder(undo.path, user);
      boolean verified = folderGateway.findFolder(undo.path) != null;
      writeAudit(txId, task, undo.key, ActionRecord.ACTION_NAME_CREATE,
                AdminChangeRecord.ACTION_ROLLBACK, AdminChangeRecord.RISK_LOW, null, null,
                verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED,
                backupRef, reviewOutcome, user, ActionRecord.OBJECT_TYPE_FOLDER);

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
                backupRef, reviewOutcome, user, ActionRecord.OBJECT_TYPE_FOLDER);

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
                backupRef, reviewOutcome, user, ActionRecord.OBJECT_TYPE_FOLDER);

      if(!verified) {
         failures.add(new RollbackFailure(undo.key, "rollback of move did not restore the prior parent"));
      }
   }

   /** Bug #76841: moves {@code undo.path} (the taskId) back to {@code undo.beforePath} (the task's
    * folder path before the original move) -- the task-move analog of {@link #rollbackMove}. */
   private void rollbackMoveTask(Undo undo, String txId, String task, String backupRef,
                                 String reviewOutcome, Principal user, List<RollbackFailure> failures)
      throws Exception
   {
      folderGateway.moveTask(undo.path, undo.beforePath, user);
      String restoredPath = folderGateway.getTaskPath(undo.path);
      boolean verified = restoredPath != null &&
         AdminScheduleFolderGateway.normalizePath(restoredPath)
            .equals(AdminScheduleFolderGateway.normalizePath(undo.beforePath));
      writeAudit(txId, task, undo.key, ActionRecord.ACTION_NAME_MOVE,
                AdminChangeRecord.ACTION_ROLLBACK, AdminChangeRecord.RISK_LOW, null, null,
                verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED,
                backupRef, reviewOutcome, user, ActionRecord.OBJECT_TYPE_TASK);

      if(!verified) {
         failures.add(
            new RollbackFailure(undo.key, "rollback of task move did not restore the prior folder"));
      }
   }

   private void writeAudit(String txId, String task, String path, String actionRecordName,
                           String adminAction, String risk, String before, String after,
                           String status, String backupRef, String reviewOutcome, Principal user,
                           String objectType)
   {
      try {
         AdminChangeRecord record = new AdminChangeRecord();
         record.setTransactionId(txId);
         record.setTaskDescription(task);
         record.setProperty(path);
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
      private enum Kind { CREATE, DELETE, RENAME, MOVE, MOVE_TASK }

      static Undo create(String path) {
         return new Undo(Kind.CREATE, path, path, null);
      }

      static Undo delete(String path) {
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

      /** @param taskId the moved task's id. @param originalPath the task's folder path BEFORE the
       * move (may be {@code null}, meaning root). */
      static Undo moveTask(String taskId, String originalPath) {
         return new Undo(Kind.MOVE_TASK, taskId, taskId, originalPath);
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
      /** RENAME: the original path. MOVE: the original parent path. MOVE_TASK: the task's original
       * folder path. Unused for CREATE/DELETE. */
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
