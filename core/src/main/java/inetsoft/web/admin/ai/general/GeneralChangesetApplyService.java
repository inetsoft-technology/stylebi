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
package inetsoft.web.admin.ai.general;

import inetsoft.util.Tool;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.AdminChangeRecord;
import inetsoft.util.audit.Audit;
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
 * Applies a whole general-settings changeset, all-or-nothing, and audits every attempt -- the
 * general-settings analog of {@code AdminChangesetApplyService} and of the sibling
 * {@code PresentationChangesetApplyService}, replicated rather than shared, matching this
 * program's established "replicate, don't generalize" precedent.
 *
 * <p><b>One rollback class, unlike presentation's two.</b> That area splits its sub-models into
 * value-scope (a live inverse exists) and storage-scope (none does, so a success before a later
 * failure is reported as an unconditional {@link RollbackFailure}). Here every writable sub-model
 * is compensable -- including the two storage-scope ones -- because each underlying
 * {@code setModel} takes a complete model object, so writing the captured pre-apply model back is
 * a genuine inverse rather than a partial repair. See {@code GeneralSubModel.compensable()} for
 * why scope and compensability are separate questions in this area.
 *
 * <p>Two side effects survive a successful rollback and are disclosed through
 * {@link GeneralApplyOutcome#advisory()} rather than silently ignored: a {@code performance} write
 * clears {@code AssetDataCache}, and an {@code mv} write rewrites the data-cycle registry. Neither
 * loses state a rollback needs to restore, but an operator reading a "rolled back" result should
 * not have to discover them later.
 */
@Component
public class GeneralChangesetApplyService {
   @Autowired
   public GeneralChangesetApplyService(GeneralChangePlanService planService,
                                       GeneralSettingsAccess access,
                                       AdminBackupService backupService)
   {
      this.planService = planService;
      this.access = access;
      this.backupService = backupService;
   }

   /**
    * Resolves fresh, gates on the plan hash, the task token and {@code reviewOutcome}, backs up,
    * then executes.
    *
    * <p>There is deliberately no {@code acknowledgeIrreversibleUpdate} gate -- see
    * {@link GeneralApplyRequest}.
    *
    * @throws AdminChangesetApplyService.PlanHashMismatchException if the hash is missing or stale
    *         (maps to HTTP 409).
    * @throws Exception if the backup itself fails, in which case nothing was applied.
    */
   public GeneralApplyResult apply(GeneralApplyRequest req, Principal user) throws Exception {
      APPLY_LOCK.lock();

      try {
         List<GeneralChangePlanService.ResolvedChange> resolved =
            planService.resolveEntries(req, user);
         List<PlanChange> planChanges = new ArrayList<>();

         for(GeneralChangePlanService.ResolvedChange entry : resolved) {
            planChanges.add(entry.planChange());
         }

         String task = req.getTask().trim();
         String currentHash = GeneralChangePlanService.hash(planChanges);

         if(req.getPlanHash() == null || !currentHash.equals(req.getPlanHash())) {
            // A 409 conflict response must never hand back a taskToken -- the exception
            // constructor strips whatever is passed, so issuing one would be a wasted encryption
            // call for a value discarded one frame later.
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
               "reviewOutcome: required -- every general-settings change alters deployment-wide " +
               "configuration and must be reviewed by a human before it is applied");
         }

         String txId = "gen-" + newIdSuffix();
         String backupRef = backupService.backup(txId);
         String reviewOutcome = req.getReviewOutcome();
         List<GeneralApplyOutcome> results = new ArrayList<>();
         List<GeneralChangePlanService.ResolvedChange> undoable = new ArrayList<>();
         List<RollbackFailure> unknownStateFailures = new ArrayList<>();
         boolean failed = false;

         for(GeneralChangePlanService.ResolvedChange entry : resolved) {
            String key = entry.planChange().property();
            String before = entry.planChange().currentValue();
            String expectedAfter = entry.planChange().proposedValue();

            try {
               access.write(entry.subModel(), entry.proposedModel(), user);
               String actualAfter = projected(entry, user);
               boolean verified = expectedAfter.equals(actualAfter);
               String status = verified
                  ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED;
               results.add(new GeneralApplyOutcome(key, before, actualAfter, status,
                  verified ? null : "value did not read back as written", advisoryFor(entry)));
               writeAudit(txId, reviewedTask, key, entry, AdminChangeRecord.ACTION_APPLY, before,
                          actualAfter, status, backupRef, reviewOutcome, user);

               // Queued for rollback unconditionally once the write returned, regardless of
               // `verified` -- the write already happened, so an entry whose own verification
               // failed still has to be restored rather than silently excluded as if nothing had
               // been written (the "rolled-back" mislabel of bug #76729).
               undoable.add(entry);

               if(!verified) {
                  failed = true;
                  break;
               }
            }
            catch(Exception e) {
               // The write carries no verifiable before/after evidence for this change, but we own
               // the read side too: re-read and compare to `before` to find out whether anything
               // moved before the throw (a validation check inside setModel fires before any
               // SreeEnv write), mirroring AdminChangesetApplyService#apply's own `moved` gate.
               String actualAfter = null;
               boolean readFailed = false;

               try {
                  actualAfter = projected(entry, user);
               }
               catch(Exception readEx) {
                  readFailed = true;
               }

               // A re-read can only prove "nothing moved" for a writer whose every effect the
               // read can observe. MVSettingsService.setModel calls dataCycleManager
               // .setDefaultCycle() and save() BEFORE mvManager.setDefaultCycle(), while the
               // read-back reports mvManager's value -- so if save() throws, the cycle manager
               // already holds the new default while the read still shows the old one. Reporting
               // that as "nothing moved" would claim a clean rollback over a deployment that did
               // change. Treated as unknown state instead.
               boolean observable = entry.subModel().readBackObservesEveryWrite();
               boolean moved = !readFailed && observable &&
                  !Objects.equals(before, actualAfter);

               results.add(new GeneralApplyOutcome(key, before, readFailed ? null : actualAfter,
                  AdminChangeRecord.STATUS_FAILED, messageOf(e), advisoryFor(entry)));
               writeAudit(txId, reviewedTask, key, entry, AdminChangeRecord.ACTION_APPLY, before,
                          readFailed ? null : actualAfter, AdminChangeRecord.STATUS_FAILED,
                          backupRef, reviewOutcome, user);

               if(moved) {
                  undoable.add(entry);
               }
               else if(readFailed) {
                  // Genuinely unknown state -- must never be reported as rolled back.
                  unknownStateFailures.add(new RollbackFailure(key,
                     "state unknown: apply did not return a verifiable outcome (" +
                     messageOf(e) + ")"));
               }
               else if(!observable) {
                  unknownStateFailures.add(new RollbackFailure(key,
                     "state unknown: the write failed (" + messageOf(e) + ") and a read-back " +
                     "cannot confirm whether it took effect, because this sub-model's writer " +
                     "changes state the read does not report. Check the setting in Enterprise " +
                     "Manager before retrying."));
               }
               // else: confirmed nothing moved, treated as never-mutated, so a batch where every
               // other entry restores cleanly correctly reports rolled-back.

               failed = true;
               break;
            }
         }

         if(!failed) {
            return new GeneralApplyResult(txId, AdminChangesetApplyService.STATUS_APPLIED,
               backupRef, Collections.unmodifiableList(results), null);
         }

         List<RollbackFailure> failures = new ArrayList<>(unknownStateFailures);
         failures.addAll(rollback(txId, reviewedTask, undoable, backupRef, reviewOutcome, user));

         String status = failures.isEmpty()
            ? AdminChangesetApplyService.STATUS_ROLLED_BACK
            : AdminChangesetApplyService.STATUS_ROLLBACK_FAILED;

         if(!failures.isEmpty()) {
            LOG.error("General settings changeset {} rollback failed; sub-models still changed: {}",
                      txId, failures.stream().map(RollbackFailure::property)
                         .collect(Collectors.joining(", ")));
         }

         return new GeneralApplyResult(txId, status, backupRef,
            Collections.unmodifiableList(results),
            failures.isEmpty() ? null : Collections.unmodifiableList(failures));
      }
      finally {
         APPLY_LOCK.unlock();
      }
   }

   /** Undoes changes newest-first, attempting all of them and collecting any failures. */
   private List<RollbackFailure> rollback(String txId, String task,
                                          List<GeneralChangePlanService.ResolvedChange> undoable,
                                          String backupRef, String reviewOutcome, Principal user)
   {
      List<RollbackFailure> failures = new ArrayList<>();

      for(int i = undoable.size() - 1; i >= 0; i--) {
         GeneralChangePlanService.ResolvedChange entry = undoable.get(i);
         String key = entry.planChange().property();
         String expectedBefore = entry.planChange().currentValue();

         try {
            access.write(entry.subModel(), entry.currentModel(), user);
            String restoredValue = projected(entry, user);
            boolean verified = expectedBefore.equals(restoredValue);
            writeAudit(txId, task, key, entry, AdminChangeRecord.ACTION_ROLLBACK, null,
                       restoredValue,
                       verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED,
                       backupRef, reviewOutcome, user);

            if(!verified) {
               failures.add(new RollbackFailure(key,
                  "rollback reported the value as not restored to its pre-apply state"));
            }
         }
         catch(Exception e) {
            failures.add(new RollbackFailure(key, messageOf(e)));
         }
      }

      return failures;
   }

   /**
    * The side effect, if any, that a rollback of this sub-model does not undo.
    *
    * <p>Neither loses state -- a cleared query cache refills and the cycle registry ends up with
    * the value it started with -- but both are real consequences outside the setting itself, and
    * an operator should learn about them from the result rather than from behaviour.
    */
   private static String advisoryFor(GeneralChangePlanService.ResolvedChange entry) {
      return switch(entry.subModel()) {
         case PERFORMANCE ->
            "PerformanceSettingsService clears the asset data cache when the preview row limit " +
            "changes. A rollback restores the setting but does not repopulate the cache; the next " +
            "queries will be served cold.";
         case MV ->
            "MVSettingsService rewrites the data-cycle registry on every write, including when " +
            "only an MV flag changed. A rollback restores the previous default cycle, but the " +
            "registry file will have been written twice.";
         default -> null;
      };
   }

   private String projected(GeneralChangePlanService.ResolvedChange entry, Principal user)
      throws Exception
   {
      return GeneralChangePlanService.projectedValue(
         entry.subModel(), GeneralJson.toNode(access.read(entry.subModel(), user)));
   }

   private void writeAudit(String txId, String task, String key,
                           GeneralChangePlanService.ResolvedChange entry, String adminAction,
                           String before, String after, String status, String backupRef,
                           String reviewOutcome, Principal user)
   {
      try {
         AdminChangeRecord record = new AdminChangeRecord();
         record.setTransactionId(txId);
         record.setTaskDescription(task);
         record.setProperty(key);
         record.setObjectType(ActionRecord.OBJECT_TYPE_EMPROPERTY);
         record.setBeforeValue(before);
         record.setAfterValue(after);
         record.setAction(adminAction);
         record.setStatus(status);
         record.setRiskLevel(entry.subModel().risk());
         record.setSnapshotScope(entry.subModel().scope());
         record.setBackupRef(backupRef);
         record.setReviewOutcome(reviewOutcome);
         // Deliberately null, not missing: these settings are deployment-global, so there is no
         // organization for the record to name. See GeneralChangeRequest.
         record.setOrganizationId(null);
         record.setUserName(user == null ? null : user.getName());
         record.setActionTimestamp(new Timestamp(System.currentTimeMillis()));
         record.setServerHostName(Tool.getHost());
         Audit.getInstance().auditAdminChange(record, user);
      }
      catch(Exception auditFailure) {
         // An audit write must never replace the real outcome -- the rule every prior area's apply
         // service follows.
         LOG.error("Failed to write general settings admin change audit record for transaction {}",
                   txId, auditFailure);
      }
   }

   private static String messageOf(Exception e) {
      return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
   }

   private static String newIdSuffix() {
      return String.format("%016x", RANDOM.nextLong());
   }

   private static final Logger LOG = LoggerFactory.getLogger(GeneralChangesetApplyService.class);
   private static final SecureRandom RANDOM = new SecureRandom();
   /** Serializes the entire body of {@link #apply} -- same rationale and same JVM-local-only
    * limitation as every prior area's own lock. */
   private static final ReentrantLock APPLY_LOCK = new ReentrantLock();
   private final GeneralChangePlanService planService;
   private final GeneralSettingsAccess access;
   private final AdminBackupService backupService;
}
