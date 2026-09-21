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

import inetsoft.sree.internal.DataCycleManager;
import inetsoft.sree.security.OrganizationManager;
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
 * Applies a whole Scheduled Cycle changeset, all-or-nothing, and audits every attempt (bug
 * #76848, design §5.5) -- modeled on {@link ScheduleFolderChangesetApplyService}'s own
 * apply-with-rollback loop: re-resolve the plan fresh from the SAME {@link
 * ScheduleCycleChangePlanService#resolve} (never trust the stored preview), verify {@code
 * planHash}, verify {@code taskToken}, apply each entry via {@link AdminScheduleCycleGateway},
 * and on any failure, roll back every already-applied entry in reverse order.
 *
 * <p>Unlike a schedule-task FOLDER delete, a cycle delete's rollback is a FULL, exact inverse
 * (decision D7): the pre-delete name+conditions are captured at apply time and recreated verbatim
 * on rollback -- a cycle's own content is fully compensable, unlike a folder that may have had
 * schedule tasks permanently destroyed underneath it.
 */
@Component
public class ScheduleCycleChangesetApplyService {
   @Autowired
   public ScheduleCycleChangesetApplyService(ScheduleCycleChangePlanService planService,
                                             AdminScheduleCycleGateway cycleGateway,
                                             AdminBackupService backupService)
   {
      this.planService = planService;
      this.cycleGateway = cycleGateway;
      this.backupService = backupService;
   }

   /**
    * Resolves, gates on the plan hash and task token, then executes.
    *
    * @throws AdminChangesetApplyService.PlanHashMismatchException if the hash is missing or
    *         stale (maps to HTTP 409) -- reused verbatim from the properties area.
    * @throws AdminChangesetApplyService.TaskTokenMismatchException if the taskToken is missing,
    *         malformed, or was issued for a different planHash (also maps to HTTP 409).
    * @throws Exception if the Tier-2 backup fails, in which case nothing was applied.
    */
   public ApplyResult apply(ScheduleCycleApplyRequest req, Principal user) throws Exception {
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

         String txId = "schedcycle-" + newIdSuffix();
         String backupRef = plan.requiresStorageBackup() ? backupService.backup(txId) : null;
         List<ApplyOutcome> results = new ArrayList<>();
         List<Undo> undoable = new ArrayList<>();
         List<RollbackFailure> unknownStateFailures = new ArrayList<>();
         boolean failed = false;

         List<ScheduleCycleChangeRequest> originals = req.getChanges();

         for(int i = 0; i < plan.changes().size(); i++) {
            PlanChange change = plan.changes().get(i);
            ScheduleCycleChangeRequest original = originals.get(i);
            String orgId = change.orgId();

            try {
               switch(original.verb()) {
               case ScheduleCycleChangeRequest.VERB_CREATE:
                  applyCreate(txId, reviewedTask, change, original, orgId, backupRef,
                             req.getReviewOutcome(), user, results, undoable);
                  break;
               case ScheduleCycleChangeRequest.VERB_UPDATE:
                  applyUpdate(txId, reviewedTask, change, original, orgId, backupRef,
                             req.getReviewOutcome(), user, results, undoable);
                  break;
               default:
                  applyDelete(txId, reviewedTask, change, orgId, backupRef, req.getReviewOutcome(),
                             user, results, undoable);
                  break;
               }
            }
            catch(Exception e) {
               // A throw carries no verifiable before/after evidence for THIS change -- unlike a
               // reported failure, it must never be treated as rolled back.
               results.add(new ApplyOutcome(change.property(), null, null,
                                            AdminChangeRecord.STATUS_FAILED, messageOf(e)));
               unknownStateFailures.add(new RollbackFailure(change.property(),
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
         failures.addAll(
            rollback(txId, reviewedTask, undoable, backupRef, req.getReviewOutcome(), user));

         if(failures.isEmpty()) {
            return new ApplyResult(txId, AdminChangesetApplyService.STATUS_ROLLED_BACK, backupRef,
                                   Collections.unmodifiableList(results), null);
         }

         LOG.error("Schedule-cycle changeset {} rollback failed; cycles still changed: {}", txId,
                  failures.stream().map(RollbackFailure::property).collect(Collectors.joining(", ")));
         return new ApplyResult(txId, AdminChangesetApplyService.STATUS_ROLLBACK_FAILED, backupRef,
                                Collections.unmodifiableList(results),
                                Collections.unmodifiableList(failures));
      }
      finally {
         APPLY_LOCK.unlock();
      }
   }

   private void applyCreate(String txId, String task, PlanChange change,
                            ScheduleCycleChangeRequest original, String orgId, String backupRef,
                            String reviewOutcome, Principal user, List<ApplyOutcome> results,
                            List<Undo> undoable)
      throws Exception
   {
      String name = original.spec().name();
      cycleGateway.createCycle(original.spec(), user);

      DataCycleManager.DataCycleAsset after = cycleGateway.currentAsset(name, orgId);
      boolean verified = after != null;
      String afterProjection = verified ? AdminScheduleCycleGateway.projectXml(after) : null;
      String status = verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED;
      results.add(new ApplyOutcome(change.property(), null, afterProjection, status,
                                   verified ? null : "scheduled cycle not found after create"));
      writeAudit(txId, task, name, ActionRecord.ACTION_NAME_CREATE, AdminChangeRecord.ACTION_APPLY,
                AdminChangeRecord.RISK_HIGH, null, afterProjection, status, backupRef, reviewOutcome,
                user, ActionRecord.OBJECT_TYPE_CYCLE);

      if(verified) {
         undoable.add(Undo.create(name));
      }
   }

   private void applyUpdate(String txId, String task, PlanChange change,
                            ScheduleCycleChangeRequest original, String orgId, String backupRef,
                            String reviewOutcome, Principal user, List<ApplyOutcome> results,
                            List<Undo> undoable)
      throws Exception
   {
      String currentName = original.name();
      DataCycleManager.DataCycleAsset before = cycleGateway.currentAsset(currentName, orgId);
      String beforeProjection = AdminScheduleCycleGateway.projectXml(before);

      cycleGateway.updateCycle(currentName, original.spec(), user);

      String newName = original.spec().name() != null ? original.spec().name() : currentName;
      DataCycleManager.DataCycleAsset after = cycleGateway.currentAsset(newName, orgId);
      boolean verified = after != null &&
         (newName.equals(currentName) || cycleGateway.currentAsset(currentName, orgId) == null);
      String afterProjection = verified ? AdminScheduleCycleGateway.projectXml(after) : null;
      String status = verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED;
      results.add(new ApplyOutcome(change.property(), beforeProjection, afterProjection, status,
                                   verified ? null
                                      : "scheduled cycle not found at the new name after update"));
      writeAudit(txId, task, currentName, ActionRecord.ACTION_NAME_EDIT, AdminChangeRecord.ACTION_APPLY,
                AdminChangeRecord.RISK_HIGH, beforeProjection, afterProjection, status, backupRef,
                reviewOutcome, user, ActionRecord.OBJECT_TYPE_CYCLE);

      if(verified) {
         // Captures the PRE-update name+conditions for a full, exact rollback inverse (design
         // §5.5) -- restored regardless of whether this update touched name, conditions, or both.
         undoable.add(Undo.update(newName, currentName, before.getConditions()));
      }
   }

   private void applyDelete(String txId, String task, PlanChange change, String orgId,
                            String backupRef, String reviewOutcome, Principal user,
                            List<ApplyOutcome> results, List<Undo> undoable)
      throws Exception
   {
      String name = change.property();
      DataCycleManager.DataCycleAsset before = cycleGateway.currentAsset(name, orgId);
      String beforeProjection = AdminScheduleCycleGateway.projectXml(before);

      cycleGateway.deleteCycles(List.of(name), user);

      boolean verified = cycleGateway.currentAsset(name, orgId) == null;
      String status = verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED;
      results.add(new ApplyOutcome(change.property(), beforeProjection, null, status,
                                   verified ? null : "scheduled cycle still exists after delete"));
      writeAudit(txId, task, name, ActionRecord.ACTION_NAME_DELETE, AdminChangeRecord.ACTION_APPLY,
                AdminChangeRecord.RISK_HIGH, beforeProjection, null, status, backupRef, reviewOutcome,
                user, ActionRecord.OBJECT_TYPE_CYCLE);

      if(verified) {
         // The pre-delete conditions were captured above -- rollback can fully recreate the
         // cycle (decision D7's own load-bearing "delete is fully compensable" claim).
         undoable.add(Undo.delete(name, before.getConditions()));
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
            case UPDATE:
               rollbackUpdate(undo, txId, task, backupRef, reviewOutcome, user, failures);
               break;
            default:
               rollbackDelete(undo, txId, task, backupRef, reviewOutcome, user, failures);
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
      cycleGateway.deleteCycles(List.of(undo.name), user);
      String orgId = OrganizationManager.getInstance().getCurrentOrgID(user);
      boolean verified = cycleGateway.currentAsset(undo.name, orgId) == null;
      writeAudit(txId, task, undo.key, ActionRecord.ACTION_NAME_DELETE,
                AdminChangeRecord.ACTION_ROLLBACK, AdminChangeRecord.RISK_HIGH, null, null,
                verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED,
                backupRef, reviewOutcome, user, ActionRecord.OBJECT_TYPE_CYCLE);

      if(!verified) {
         failures.add(new RollbackFailure(undo.key, "rollback of create did not remove the cycle"));
      }
   }

   private void rollbackUpdate(Undo undo, String txId, String task, String backupRef,
                               String reviewOutcome, Principal user, List<RollbackFailure> failures)
      throws Exception
   {
      // undo.name holds the CURRENT (post-update) name, undo.beforeName the ORIGINAL name;
      // undo.conditions holds the ORIGINAL conditions -- re-apply updateCycle with both to fully
      // restore the pre-update spec, regardless of which part the original update touched.
      ScheduleCycleChangeRequest.ScheduleCycleSpec restoreSpec =
         new ScheduleCycleChangeRequest.ScheduleCycleSpec(undo.beforeName, toWireConditions(undo.conditions));

      cycleGateway.updateCycle(undo.name, restoreSpec, user);

      String orgId = OrganizationManager.getInstance().getCurrentOrgID(user);
      boolean verified = cycleGateway.currentAsset(undo.beforeName, orgId) != null;
      writeAudit(txId, task, undo.key, ActionRecord.ACTION_NAME_EDIT,
                AdminChangeRecord.ACTION_ROLLBACK, AdminChangeRecord.RISK_HIGH, null, null,
                verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED,
                backupRef, reviewOutcome, user, ActionRecord.OBJECT_TYPE_CYCLE);

      if(!verified) {
         failures.add(new RollbackFailure(undo.key,
            "rollback of update did not restore the prior name/conditions"));
      }
   }

   private void rollbackDelete(Undo undo, String txId, String task, String backupRef,
                               String reviewOutcome, Principal user, List<RollbackFailure> failures)
      throws Exception
   {
      ScheduleCycleChangeRequest.ScheduleCycleSpec restoreSpec =
         new ScheduleCycleChangeRequest.ScheduleCycleSpec(undo.name, toWireConditions(undo.conditions));

      cycleGateway.createCycle(restoreSpec, user);

      String orgId = OrganizationManager.getInstance().getCurrentOrgID(user);
      boolean verified = cycleGateway.currentAsset(undo.name, orgId) != null;
      writeAudit(txId, task, undo.key, ActionRecord.ACTION_NAME_CREATE,
                AdminChangeRecord.ACTION_ROLLBACK, AdminChangeRecord.RISK_HIGH, null, null,
                verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED,
                backupRef, reviewOutcome, user, ActionRecord.OBJECT_TYPE_CYCLE);

      if(!verified) {
         failures.add(new RollbackFailure(undo.key, "rollback of delete did not restore the cycle"));
      }
   }

   private static List<inetsoft.web.api.schedule.TimeCondition> toWireConditions(
      List<inetsoft.sree.schedule.ScheduleCondition> conditions)
   {
      return conditions.stream()
         .map(ScheduleConditionConverter::convertCondition)
         .filter(inetsoft.web.api.schedule.TimeCondition.class::isInstance)
         .map(inetsoft.web.api.schedule.TimeCondition.class::cast)
         .collect(Collectors.toList());
   }

   private void writeAudit(String txId, String task, String name, String actionRecordName,
                           String adminAction, String risk, String before, String after,
                           String status, String backupRef, String reviewOutcome, Principal user,
                           String objectType)
   {
      try {
         AdminChangeRecord record = new AdminChangeRecord();
         record.setTransactionId(txId);
         record.setTaskDescription(task);
         record.setProperty(name);
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
         // An audit write must never replace the real outcome.
         LOG.error("Failed to write schedule-cycle admin change audit record for transaction {}",
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
      private enum Kind { CREATE, UPDATE, DELETE }

      static Undo create(String name) {
         return new Undo(Kind.CREATE, name, name, null, null);
      }

      static Undo update(String currentName, String beforeName,
                         List<inetsoft.sree.schedule.ScheduleCondition> conditions)
      {
         return new Undo(Kind.UPDATE, currentName, currentName, beforeName, conditions);
      }

      static Undo delete(String name, List<inetsoft.sree.schedule.ScheduleCondition> conditions) {
         return new Undo(Kind.DELETE, name, name, null, conditions);
      }

      private Undo(Kind kind, String key, String name, String beforeName,
                   List<inetsoft.sree.schedule.ScheduleCondition> conditions)
      {
         this.kind = kind;
         this.key = key;
         this.name = name;
         this.beforeName = beforeName;
         this.conditions = conditions;
      }

      final Kind kind;
      /** The plan's own {@code property} key, used only for a {@link RollbackFailure}. */
      final String key;
      final String name;
      /** UPDATE: the original (pre-update) name. Unused for CREATE/DELETE. */
      final String beforeName;
      /** UPDATE/DELETE: the original conditions, captured for a full rollback inverse. Unused
       * for CREATE. */
      final List<inetsoft.sree.schedule.ScheduleCondition> conditions;
   }

   private static final Logger LOG = LoggerFactory.getLogger(ScheduleCycleChangesetApplyService.class);
   private static final SecureRandom RANDOM = new SecureRandom();
   /** Serializes the entire body of {@link #apply} -- same rationale/limitations as {@code
    * AdminChangesetApplyService#APPLY_LOCK} (JVM-local only). */
   private static final ReentrantLock APPLY_LOCK = new ReentrantLock();
   private final ScheduleCycleChangePlanService planService;
   private final AdminScheduleCycleGateway cycleGateway;
   private final AdminBackupService backupService;
}
