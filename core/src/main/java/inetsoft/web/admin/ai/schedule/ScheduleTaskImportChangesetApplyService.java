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

import inetsoft.sree.schedule.ScheduleAction;
import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.schedule.ScheduleTask;
import inetsoft.util.Tool;
import inetsoft.util.audit.*;
import inetsoft.web.admin.ai.*;
import inetsoft.web.admin.schedule.ScheduleTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.security.Principal;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Applies a whole schedule-task-import changeset, all-or-nothing, and audits every attempt -- the
 * import-side analog of {@code RepositoryImportApplyService}/{@code ScheduleChangesetApplyService}.
 *
 * <p>Before persisting each staged task, this class runs it through the EXACT SAME
 * {@code ScheduleTaskService.sanitizeConditions}/{@code sanitizeAction} calls
 * {@code AdminScheduleGateway#createScheduleTask} already applies to a task built from a
 * structured {@code CreateScheduleTaskRequest} spec -- closing what would otherwise be a SECOND,
 * unsanitized entry point into {@code ScheduleManager.setScheduleTask} (EM's own
 * {@code ImportTaskController.importScheduleTask} has NO such sanitization at all, a real,
 * pre-existing gap in the underlying product this area does not reproduce -- reusing the "create"
 * verb's own extra safety measure here, not EM's own less-safe import behavior, matching this
 * plugin's general "be at least as strict as the underlying primitive, not merely as strict"
 * posture). {@code sanitizeAction} is called once per staged action, paired by INDEX with the
 * corresponding action on the task being replaced (or {@code null} beyond its action count, or for
 * a brand-new task) -- mirroring {@code ScheduleTaskService}'s own update-path pairing exactly.
 *
 * <p>An overwrite's rollback restores the ORIGINAL task (captured via {@code clone()} before
 * mutating); a create's rollback removes the task this apply itself created -- the identical
 * {@code AdminScheduleGateway#removeScheduleTask} primitive {@code ScheduleChangesetApplyService}'s
 * own {@code create} rollback already uses.
 *
 * <p>Deliberately does NOT re-file an imported task into the folder its own export recorded, and
 * does NOT import the file's {@code <timeRanges>} block -- both disclosed, deliberate scope
 * boundaries (see {@code AdminScheduleTransferController}'s own javadoc): re-filing is already a
 * fully-supported, separate capability via {@code preview_schedule_task_folder_changes}, and
 * importing time ranges would silently replace EVERY currently-configured Time Range with no
 * per-item review, a blast radius this cut declines to take on sight-unseen.
 */
@Component
public class ScheduleTaskImportChangesetApplyService {
   @Autowired
   public ScheduleTaskImportChangesetApplyService(ScheduleTaskImportChangePlanService planService,
                                                  ScheduleTaskTransferService transferService,
                                                  ScheduleManager scheduleManager,
                                                  AdminScheduleGateway scheduleGateway,
                                                  ScheduleTaskService scheduleTaskService,
                                                  AdminBackupService backupService)
   {
      this.planService = planService;
      this.transferService = transferService;
      this.scheduleManager = scheduleManager;
      this.scheduleGateway = scheduleGateway;
      this.scheduleTaskService = scheduleTaskService;
      this.backupService = backupService;
   }

   /**
    * Resolves, gates on the plan hash and task token, backs up, then executes.
    *
    * @throws AdminChangesetApplyService.PlanHashMismatchException if the hash is missing or stale.
    * @throws AdminChangesetApplyService.TaskTokenMismatchException if the taskToken is missing,
    *         malformed, or was issued for a different planHash -- reused verbatim from the
    *         properties area, the same way {@code ScheduleChangesetApplyService} reuses it.
    * @throws IllegalArgumentException if {@code reviewOutcome} is blank while the plan requires
    *         agent signoff, or {@code acknowledgeOverwrite} is not exactly {@code true} while the
    *         plan contains any entry overwriting an existing task.
    * @throws Exception if the Tier-2 backup itself fails, in which case nothing was applied.
    */
   public ScheduleTaskImportApplyResult apply(ScheduleTaskImportApplyRequest req, Principal user,
                                              String linkURI)
      throws Exception
   {
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
               "reviewOutcome: required because this changeset overwrites an existing task");
         }

         boolean hasOverwrite = plan.changes().stream()
            .anyMatch(c -> AdminChangeRecord.RISK_HIGH.equals(c.risk()));

         if(hasOverwrite && !Boolean.TRUE.equals(req.getAcknowledgeOverwrite())) {
            throw new IllegalArgumentException(
               "acknowledgeOverwrite: must be true because this changeset overwrites one or more " +
               "existing schedule tasks, replacing their conditions/actions entirely");
         }

         String txId = "schedule-import-" + newIdSuffix();
         String backupRef = plan.requiresStorageBackup() ? backupService.backup(txId) : null;
         String reviewOutcome = req.getReviewOutcome();
         List<ScheduleTaskImportApplyOutcome> results = new ArrayList<>();
         List<Undo> undoable = new ArrayList<>();
         List<RollbackFailure> unknownStateFailures = new ArrayList<>();
         boolean failed = false;

         List<ScheduleTaskImportChangeRequest> originals = req.getChanges();

         for(int i = 0; i < plan.changes().size(); i++) {
            PlanChange change = plan.changes().get(i);
            ScheduleTaskImportChangeRequest original = originals.get(i);
            String key = change.property();

            AtomicBoolean mutationEntered = new AtomicBoolean(false);

            try {
               applyOne(txId, reviewedTask, key, req.getStagingToken(), original, user, linkURI,
                       backupRef, reviewOutcome, results, undoable, mutationEntered);
            }
            catch(Exception e) {
               results.add(new ScheduleTaskImportApplyOutcome(key, null, null,
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
            return new ScheduleTaskImportApplyResult(txId, AdminChangesetApplyService.STATUS_APPLIED,
                                                     backupRef, Collections.unmodifiableList(results),
                                                     null);
         }

         List<RollbackFailure> failures = new ArrayList<>(unknownStateFailures);
         failures.addAll(rollback(txId, reviewedTask, undoable, backupRef, reviewOutcome, user));

         if(failures.isEmpty()) {
            return new ScheduleTaskImportApplyResult(txId, AdminChangesetApplyService.STATUS_ROLLED_BACK,
                                                     backupRef, Collections.unmodifiableList(results),
                                                     null);
         }

         LOG.error("Schedule task import changeset {} rollback failed; entries still changed: {}",
                  txId, failures.stream().map(RollbackFailure::property).collect(Collectors.joining(", ")));
         return new ScheduleTaskImportApplyResult(txId, AdminChangesetApplyService.STATUS_ROLLBACK_FAILED,
                                                  backupRef, Collections.unmodifiableList(results),
                                                  Collections.unmodifiableList(failures));
      }
      finally {
         APPLY_LOCK.unlock();
      }
   }

   private void applyOne(String txId, String task, String key, String stagingToken,
                         ScheduleTaskImportChangeRequest original, Principal user, String linkURI,
                         String backupRef, String reviewOutcome,
                         List<ScheduleTaskImportApplyOutcome> results, List<Undo> undoable,
                         AtomicBoolean mutationEntered)
      throws Exception
   {
      String taskId = original.getTaskId();
      // Deep-copy before mutating -- requireStagedTask returns the SAME cached object every call
      // (ScheduleTaskTransferService's own staging cache holds one instance per stagingToken for
      // its whole 30-minute lifetime, deliberately shared across every preview/apply against that
      // token, per its own "Parses (never mutates)" contract). Mutating it in place here (via
      // updateTaskLinkUri/sanitizeConditions/sanitizeAction below) would permanently corrupt that
      // cached instance for every LATER preview_schedule_task_import/apply_schedule_task_import
      // call against the same stagingToken -- live-confirmed: a second preview after a first apply
      // showed the first apply's own linkURI rewrite already baked into "proposedValue", which a
      // preview must never do. A plain ScheduleTask.clone() is NOT enough here: it Vector.clone()s
      // conds/acts, which only copies the Vector, not the ScheduleCondition/ScheduleAction objects
      // inside it, so sanitizeConditions/sanitizeAction/updateTaskLinkUri below -- which mutate
      // those objects' OWN fields in place -- would still corrupt the shared cached instance one
      // level down. deepCopy() below round-trips the whole task through writeXML/parseXML (the
      // same idiom ScheduleTask.copyScheduleAction already uses per-action, applied to the whole
      // task so conditions are isolated too, which copyScheduleTask's own clone()-plus-per-action-
      // copy does not do) to guarantee every condition and action is a fresh instance.
      ScheduleTask staged = deepCopy(transferService.requireStagedTask(stagingToken, taskId));
      // Re-resolve fresh AT APPLY TIME -- never trust anything computed at preview.
      ScheduleTask existing = scheduleManager.getScheduleTask(taskId);
      boolean overwrite = Boolean.TRUE.equals(original.getOverwrite());

      if(existing != null && !overwrite) {
         throw new IllegalArgumentException(
            "overwrite: a schedule task named \"" + taskId + "\" was created since preview -- set " +
            "overwrite: true to replace it, or choose a different task");
      }

      String before = existing == null ? null : ScheduleXmlProjection.project(existing);
      ScheduleTask capturedOriginal = existing == null ? null : existing.clone();

      updateTaskLinkUri(staged, linkURI);

      ScheduleTask sanitizeBaseline = existing == null ? new ScheduleTask() : existing;
      scheduleTaskService.sanitizeConditions(staged, sanitizeBaseline, user);

      for(int i = 0; i < staged.getActionCount(); i++) {
         ScheduleAction pairedOld = sanitizeBaseline.getActionCount() > i ?
            sanitizeBaseline.getAction(i) : null;
         scheduleTaskService.sanitizeAction(staged.getAction(i), pairedOld, user);
      }

      mutationEntered.set(true);
      scheduleManager.setScheduleTask(taskId, staged, user);

      boolean verified = scheduleManager.getScheduleTask(taskId) != null;
      String after = verified ? ScheduleXmlProjection.project(staged) : null;
      String status = verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED;
      results.add(new ScheduleTaskImportApplyOutcome(key, before, after, status,
         verified ? null : "schedule task not found after import", null));
      writeAudit(txId, task, key, existing == null ? AdminChangeRecord.RISK_LOW :
                AdminChangeRecord.RISK_HIGH, AdminChangeRecord.ACTION_APPLY, before, after, status,
                backupRef, reviewOutcome, user);

      if(verified) {
         undoable.add(capturedOriginal == null ? Undo.create(key, taskId, staged) :
            Undo.overwrite(key, taskId, capturedOriginal));
      }
   }

   /**
    * Deep-copies {@code task}, isolating every condition/action from the source instance, via an
    * XML round-trip through {@code ScheduleTask.writeXML}/{@code parseXML} -- the same pair
    * {@code ScheduleTaskTransferService.stage} already uses to build a {@code ScheduleTask} from
    * an uploaded export file's {@code <Task>} element, and the same per-object round-trip idiom
    * {@code ScheduleTask.copyScheduleAction} uses for a single action. {@code
    * ScheduleTask.copyScheduleTask} is not used here: besides being {@code protected static} and
    * unreachable from this package, it only round-trips ACTIONS after a shallow {@code clone()},
    * leaving conditions shared with the source -- insufficient for this call site, which also
    * mutates conditions (via {@code ScheduleTaskService.sanitizeConditions}).
    */
   private static ScheduleTask deepCopy(ScheduleTask task) throws Exception {
      StringWriter buffer = new StringWriter();

      try(PrintWriter writer = new PrintWriter(buffer)) {
         task.writeXML(writer);
      }

      Document document = Tool.parseXML(new StringReader(buffer.toString()));
      ScheduleTask copy = new ScheduleTask();
      copy.parseXML(document.getDocumentElement());
      return copy;
   }

   /** Mirrors {@code ImportTaskController.updateTaskInfo} exactly: rewrite every action's own
    * {@code linkURI} to the CURRENT server's, so a task imported from a different environment's
    * export doesn't keep pointing hyperlinks/viewer links at the source server. */
   private static void updateTaskLinkUri(ScheduleTask task, String linkURI) {
      task.getActionStream().forEach(action -> {
         if(action instanceof inetsoft.sree.schedule.AbstractAction abstractAction) {
            abstractAction.setLinkURI(Tool.replaceLocalhost(linkURI));
         }
      });
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
      boolean verified;

      if(undo.kind == UndoKind.CREATE) {
         String orgId = undo.task.getOwner() == null ? null : undo.task.getOwner().getOrgID();
         scheduleGateway.removeScheduleTask(undo.taskId, orgId, user);
         verified = scheduleManager.getScheduleTask(undo.taskId) == null;
      }
      else {
         scheduleManager.setScheduleTask(undo.taskId, undo.task, user);
         verified = scheduleManager.getScheduleTask(undo.taskId) != null;
      }

      writeAudit(txId, task, undo.key, AdminChangeRecord.RISK_LOW, AdminChangeRecord.ACTION_ROLLBACK,
                null, null, verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED,
                backupRef, reviewOutcome, user);

      if(!verified) {
         failures.add(new RollbackFailure(undo.key,
            "rollback of " + undo.kind.name().toLowerCase() + " did not restore the prior state"));
      }
   }

   private void writeAudit(String txId, String task, String key, String risk, String adminAction,
                           String before, String after, String status, String backupRef,
                           String reviewOutcome, Principal user)
   {
      try {
         AdminChangeRecord record = new AdminChangeRecord();
         record.setTransactionId(txId);
         record.setTaskDescription(task);
         record.setProperty(key);
         record.setObjectType(ActionRecord.OBJECT_TYPE_TASK);
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
         LOG.error("Failed to write schedule task import admin change audit record for " +
                   "transaction {}", txId, auditFailure);
      }
   }

   private static String lastStatus(List<ScheduleTaskImportApplyOutcome> results) {
      return results.isEmpty() ? null : results.get(results.size() - 1).status();
   }

   private static String messageOf(Exception e) {
      return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
   }

   private static String newIdSuffix() {
      return String.format("%016x", RANDOM.nextLong());
   }

   private enum UndoKind { CREATE, OVERWRITE }

   /** One undo descriptor built during apply, replayed in reverse by {@link #rollback}. */
   private static final class Undo {
      static Undo create(String key, String taskId, ScheduleTask task) {
         return new Undo(UndoKind.CREATE, key, taskId, task);
      }

      static Undo overwrite(String key, String taskId, ScheduleTask capturedOriginal) {
         return new Undo(UndoKind.OVERWRITE, key, taskId, capturedOriginal);
      }

      private Undo(UndoKind kind, String key, String taskId, ScheduleTask task) {
         this.kind = kind;
         this.key = key;
         this.taskId = taskId;
         this.task = task;
      }

      final UndoKind kind;
      final String key;
      final String taskId;
      final ScheduleTask task;
   }

   private static final Logger LOG = LoggerFactory.getLogger(ScheduleTaskImportChangesetApplyService.class);
   private static final SecureRandom RANDOM = new SecureRandom();
   private static final ReentrantLock APPLY_LOCK = new ReentrantLock();
   private final ScheduleTaskImportChangePlanService planService;
   private final ScheduleTaskTransferService transferService;
   private final ScheduleManager scheduleManager;
   private final AdminScheduleGateway scheduleGateway;
   private final ScheduleTaskService scheduleTaskService;
   private final AdminBackupService backupService;
}
