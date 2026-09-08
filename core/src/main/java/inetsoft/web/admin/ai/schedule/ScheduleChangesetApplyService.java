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

import inetsoft.web.api.schedule.*;
import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.schedule.ScheduleTaskMetaData;
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
 * Applies a whole schedule-task changeset, all-or-nothing, and audits every attempt -- the
 * schedule-task analog of {@code inetsoft.web.admin.ai.AdminChangesetApplyService}, replicated
 * rather than shared (spec §6, carry-forward item 5).
 *
 * <p>Two structural differences from the properties path, both because this area's unit is not a
 * scalar (spec §6):
 * <ul>
 *   <li>The inverse of a {@code create} is a {@code delete}; the inverse of a {@code delete} is a
 *       re-create from a snapshot captured <b>during this apply</b>, via three round trips through
 *       {@link AdminScheduleGateway} (its DTO carries no conditions/actions on its own -- Track
 *       C.0 finding), not from anything the caller supplied. This mirrors {@code
 *       AdminChangesetApplyService}'s own rule that the before-value used for rollback is the one
 *       "the server recorded during the apply", not the preview-time one.</li>
 *   <li>Verification is existence-based ("does the task now exist / no longer exist"), not a
 *       value-equality check against a projection the caller approved -- there is no single
 *       expected post-create projection to compare against, unlike a property's expected written
 *       value.</li>
 * </ul>
 */
@Component
public class ScheduleChangesetApplyService {
   @Autowired
   public ScheduleChangesetApplyService(ScheduleChangePlanService planService,
                                        AdminScheduleGateway scheduleGateway,
                                        ScheduleManager scheduleManager,
                                        AdminBackupService backupService)
   {
      this.planService = planService;
      this.scheduleGateway = scheduleGateway;
      this.scheduleManager = scheduleManager;
      this.backupService = backupService;
   }

   /**
    * Resolves, gates on the plan hash, then executes.
    *
    * @throws AdminChangesetApplyService.PlanHashMismatchException if the hash is missing or stale
    *         (maps to HTTP 409) -- the exception type is reused verbatim from the properties area
    *         (spec §6), not redeclared.
    * @throws Exception if the Tier-2 backup fails, in which case nothing was applied.
    */
   public ApplyResult apply(ScheduleApplyRequest req, Principal user) throws Exception {
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

         String txId = "schedtask-" + newIdSuffix();
         String backupRef = plan.requiresStorageBackup() ? backupService.backup(txId) : null;
         List<ApplyOutcome> results = new ArrayList<>();
         List<Undo> undoable = new ArrayList<>();
         List<RollbackFailure> unknownStateFailures = new ArrayList<>();
         boolean failed = false;

         List<ScheduleChangeRequest> originals = req.getChanges();

         for(int i = 0; i < plan.changes().size(); i++) {
            PlanChange change = plan.changes().get(i);
            ScheduleChangeRequest original = originals.get(i);
            String taskId = change.property();

            try {
               if(ScheduleChangeRequest.VERB_CREATE.equals(original.getVerb())) {
                  applyCreate(txId, plan.task(), taskId, original.getSpec(), backupRef,
                             req.getReviewOutcome(), user, results, undoable);
               }
               else {
                  applyDelete(txId, plan.task(), taskId, backupRef, req.getReviewOutcome(), user,
                             results, undoable);
               }
            }
            catch(PreflightCaptureFailedException e) {
               // Thrown only from applyDelete's captureSpec call, which runs entirely before any
               // mutating call -- unlike a throw from the mutation itself, this proves nothing
               // was changed, so it must NOT contribute an unknown-state RollbackFailure (there is
               // nothing to roll back). Same shape as IdentityChangesetApplyService's non-
               // compensable-delete branch.
               results.add(new ApplyOutcome(taskId, null, null, AdminChangeRecord.STATUS_FAILED,
                                            messageOf(e.getCause())));
               failed = true;
               break;
            }
            catch(Exception e) {
               // A throw carries no verifiable before/after evidence for THIS change -- unlike a
               // reported failure, it must never be treated as rolled back. See
               // AdminChangesetApplyService's own javadoc for the properties-area precedent this
               // mirrors.
               results.add(new ApplyOutcome(taskId, null, null, AdminChangeRecord.STATUS_FAILED,
                                            messageOf(e)));
               unknownStateFailures.add(new RollbackFailure(taskId,
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
         failures.addAll(rollback(txId, plan.task(), undoable, backupRef, req.getReviewOutcome(),
                                  user));

         if(failures.isEmpty()) {
            return new ApplyResult(txId, AdminChangesetApplyService.STATUS_ROLLED_BACK, backupRef,
                                   Collections.unmodifiableList(results), null);
         }

         LOG.error("Schedule-task changeset {} rollback failed; tasks still changed: {}", txId,
                  failures.stream().map(RollbackFailure::property).collect(Collectors.joining(", ")));
         return new ApplyResult(txId, AdminChangesetApplyService.STATUS_ROLLBACK_FAILED, backupRef,
                                Collections.unmodifiableList(results),
                                Collections.unmodifiableList(failures));
      }
      finally {
         APPLY_LOCK.unlock();
      }
   }

   private void applyCreate(String txId, String task, String taskId,
                            CreateScheduleTaskRequest spec, String backupRef,
                            String reviewOutcome, Principal user, List<ApplyOutcome> results,
                            List<Undo> undoable)
      throws Exception
   {
      ScheduleTaskMetaData meta = new ScheduleTaskMetaData(spec.getName(),
                                                            spec.getOwner().convertToKey());
      scheduleGateway.addScheduleTask(meta, spec.isEnabled(), spec.isDeleteIfNotScheduledToRun(),
         spec.getStartDate(), spec.getEndDate(), spec.getDescription(), spec.getLocale(),
         spec.getExecuteAsID(), spec.getConditions(), spec.getActions(), spec.getOwner().getOrgID(),
         null, user);

      String after = ScheduleXmlProjection.project(scheduleManager.getScheduleTask(taskId));
      boolean verified = after != null;
      String status = verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED;
      results.add(new ApplyOutcome(taskId, null, after, status,
                                   verified ? null : "task not found after create"));
      writeAudit(txId, task, taskId, ActionRecord.ACTION_NAME_CREATE, AdminChangeRecord.ACTION_APPLY,
                null, after, status, backupRef, reviewOutcome, user);

      if(verified) {
         undoable.add(new Undo(ScheduleChangeRequest.VERB_CREATE, taskId,
                               spec.getOwner().getOrgID(), null));
      }
   }

   private void applyDelete(String txId, String task, String taskId, String backupRef,
                            String reviewOutcome, Principal user, List<ApplyOutcome> results,
                            List<Undo> undoable)
      throws Exception
   {
      // Authoritative before-state, captured DURING apply -- not the preview-time snapshot -- per
      // the same rule AdminChangesetApplyService's javadoc states for properties.
      inetsoft.sree.schedule.ScheduleTask beforeTask = scheduleManager.getScheduleTask(taskId);
      String before = ScheduleXmlProjection.project(beforeTask);
      String orgId = beforeTask == null ? null : beforeTask.getOwner().getOrgID();
      CreateScheduleTaskRequest recreateSpec;

      try {
         recreateSpec = captureSpec(taskId, orgId, user);
      }
      catch(Exception e) {
         // Read-only, runs strictly before removeScheduleTask below -- a throw here proves
         // nothing was mutated yet, distinct from a throw during/after the mutating call.
         throw new PreflightCaptureFailedException(e);
      }

      scheduleGateway.removeScheduleTask(taskId, orgId, user);

      boolean verified = scheduleManager.getScheduleTask(taskId) == null;
      String status = verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED;
      results.add(new ApplyOutcome(taskId, before, null, status,
                                   verified ? null : "task still exists after delete"));
      writeAudit(txId, task, taskId, ActionRecord.ACTION_NAME_DELETE, AdminChangeRecord.ACTION_APPLY,
                before, null, status, backupRef, reviewOutcome, user);

      if(verified) {
         undoable.add(new Undo(ScheduleChangeRequest.VERB_DELETE, taskId, orgId, recreateSpec));
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
            if(ScheduleChangeRequest.VERB_CREATE.equals(undo.originalVerb)) {
               // Undo a create: delete the task it created.
               scheduleGateway.removeScheduleTask(undo.taskId, undo.orgId, user);
               boolean verified = scheduleManager.getScheduleTask(undo.taskId) == null;
               writeAudit(txId, task, undo.taskId, ActionRecord.ACTION_NAME_DELETE,
                         AdminChangeRecord.ACTION_ROLLBACK, null, null,
                         verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED,
                         backupRef, reviewOutcome, user);

               if(!verified) {
                  failures.add(new RollbackFailure(undo.taskId,
                     "rollback of create reported the task as still present after delete"));
               }
            }
            else {
               // Undo a delete: re-create the task from the snapshot captured during apply.
               CreateScheduleTaskRequest spec = undo.recreateSpec;
               ScheduleTaskMetaData meta =
                  new ScheduleTaskMetaData(spec.getName(), spec.getOwner().convertToKey());
               scheduleGateway.addScheduleTask(meta, spec.isEnabled(),
                  spec.isDeleteIfNotScheduledToRun(), spec.getStartDate(), spec.getEndDate(),
                  spec.getDescription(), spec.getLocale(), spec.getExecuteAsID(),
                  spec.getConditions(), spec.getActions(), spec.getOwner().getOrgID(), null, user);
               boolean verified = scheduleManager.getScheduleTask(undo.taskId) != null;
               writeAudit(txId, task, undo.taskId, ActionRecord.ACTION_NAME_CREATE,
                         AdminChangeRecord.ACTION_ROLLBACK, null, null,
                         verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED,
                         backupRef, reviewOutcome, user);

               if(!verified) {
                  failures.add(new RollbackFailure(undo.taskId,
                     "rollback of delete reported the task as still missing after re-create"));
               }
            }
         }
         catch(Exception e) {
            failures.add(new RollbackFailure(undo.taskId, messageOf(e)));
         }
      }

      return failures;
   }

   /** Builds a total create-request from a live task, via three round trips (Track C.0 finding:
    * the enterprise DTO alone carries no conditions/actions) -- used only to reconstruct a deleted
    * task on rollback, never as the record admin-chat reports back to the caller. */
   private CreateScheduleTaskRequest captureSpec(String taskId, String orgId, Principal user)
      throws Exception
   {
      ScheduleTask dto = scheduleGateway.getScheduleTask(taskId, orgId, user);
      ScheduleConditionList conditions = scheduleGateway.getTaskConditions(taskId, orgId, user);
      ScheduleActionList actions = scheduleGateway.getTaskActions(taskId, orgId, user);

      CreateScheduleTaskRequest spec = new CreateScheduleTaskRequest();
      spec.setName(dto.getName());
      spec.setOwner(dto.getOwner());
      spec.setEnabled(dto.isEnabled());
      spec.setDeleteIfNotScheduledToRun(dto.isDeleteIfNotScheduledToRun());
      spec.setStartDate(dto.getStartDate());
      spec.setEndDate(dto.getEndDate());
      spec.setDescription(dto.getDescription());
      spec.setLocale(dto.getLocale());
      spec.setExecuteAsID(dto.getExecuteAsID());
      spec.setConditions(conditions.getConditions());
      spec.setActions(actions.getActions());
      return spec;
   }

   private void writeAudit(String txId, String task, String taskId, String actionRecordName,
                           String adminAction, String before, String after, String status,
                           String backupRef, String reviewOutcome, Principal user)
   {
      try {
         AdminChangeRecord record = new AdminChangeRecord();
         record.setTransactionId(txId);
         record.setTaskDescription(task);
         record.setProperty(taskId);
         record.setObjectType(ActionRecord.OBJECT_TYPE_TASK);
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
         // An audit write must never replace the real outcome -- same rule
         // AdminChangeService.applyChange follows for properties.
         LOG.error("Failed to write schedule-task admin change audit record for transaction {}",
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
      Undo(String originalVerb, String taskId, String orgId, CreateScheduleTaskRequest recreateSpec) {
         this.originalVerb = originalVerb;
         this.taskId = taskId;
         this.orgId = orgId;
         this.recreateSpec = recreateSpec;
      }

      final String originalVerb;
      final String taskId;
      final String orgId;
      /** Only set when {@code originalVerb} is {@code delete} -- the snapshot to re-create from. */
      final CreateScheduleTaskRequest recreateSpec;
   }

   /** Signals that {@link #applyDelete}'s preflight {@link #captureSpec} call failed, strictly
    * before the mutating {@code removeScheduleTask} call -- distinct from a throw during/after
    * the mutating call, which leaves the post-mutation state genuinely unknown. */
   private static final class PreflightCaptureFailedException extends RuntimeException {
      PreflightCaptureFailedException(Throwable cause) {
         super(cause);
      }
   }

   private static final Logger LOG = LoggerFactory.getLogger(ScheduleChangesetApplyService.class);
   private static final SecureRandom RANDOM = new SecureRandom();
   /** Serializes the entire body of {@link #apply} -- same rationale as {@code
    * AdminChangesetApplyService#APPLY_LOCK}: JVM-local only, does not protect a clustered
    * deployment or a concurrent edit made through Enterprise Manager directly. */
   private static final ReentrantLock APPLY_LOCK = new ReentrantLock();
   private final ScheduleChangePlanService planService;
   private final AdminScheduleGateway scheduleGateway;
   private final ScheduleManager scheduleManager;
   private final AdminBackupService backupService;
}
