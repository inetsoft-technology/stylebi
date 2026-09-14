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
package inetsoft.web.admin.ai.mv;

import inetsoft.sree.security.OrganizationManager;
import inetsoft.util.Tool;
import inetsoft.util.audit.AdminChangeRecord;
import inetsoft.util.audit.Audit;
import inetsoft.web.admin.ai.*;
import inetsoft.web.admin.content.repository.MVSupportService;
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
 * Applies a whole MV changeset, all-or-nothing, and audits every attempt -- the MV analog of
 * {@code inetsoft.web.admin.ai.AdminChangesetApplyService}, replicated per this area's own
 * precedent (schedule/data-source/licensing/presentation each have their own copy).
 *
 * <p>A {@code create} entry's apply performs the server's own step 5 ({@code setDataCycle}, if a
 * cycle was given) and step 6 ({@code createMV}) back to back inside this one call, so the
 * intermediate "cycle set but not yet created" durable-but-incomplete state the server itself
 * allows is never left visible or interruptible from the plugin's perspective.
 *
 * <p>{@code delete} is declared non-compensable (no live inverse once a definition and its cluster
 * files are gone) -- {@link MvApplyRequest#getAcknowledgeIrreversibleDelete()} must be exactly
 * {@code true} whenever the plan contains one. {@code create}'s inverse is {@code dispose}; {@code
 * set_cycle}'s inverse is writing the captured old cycle value back.
 */
@Component
public class MvChangesetApplyService {
   @Autowired
   public MvChangesetApplyService(MvChangePlanService planService, AdminMvGateway mvGateway,
                                  AdminBackupService backupService)
   {
      this.planService = planService;
      this.mvGateway = mvGateway;
      this.backupService = backupService;
   }

   /**
    * Resolves (re-validating everything, including analysisId freshness), gates on the plan hash
    * and task token, then executes.
    *
    * @throws AdminChangesetApplyService.PlanHashMismatchException if the hash is missing or stale.
    * @throws AdminChangesetApplyService.TaskTokenMismatchException if the taskToken is missing,
    *         malformed, or was issued for a different planHash.
    * @throws AnalysisExpiredException if a referenced analysisId is unknown or has expired -- a
    *         clean, pre-mutation refusal (nothing has been touched yet, since {@link
    *         MvChangePlanService#resolve} runs entirely before any backup/mutation below).
    * @throws IllegalArgumentException if {@code reviewOutcome} is blank while the plan requires
    *         signoff, or {@code acknowledgeIrreversibleDelete} is not exactly {@code true} while the
    *         plan contains a delete entry.
    * @throws Exception if the Tier-2 backup itself fails, in which case nothing was applied.
    */
   public ApplyResult apply(MvApplyRequest req, Principal user) throws Exception {
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

         List<MvChangePlanService.FlatChange> flat = MvChangePlanService.flatten(req.getChanges());
         boolean hasDelete = flat.stream()
            .anyMatch(fc -> MvChangeRequest.VERB_DELETE.equals(fc.verb));

         if(hasDelete && !Boolean.TRUE.equals(req.getAcknowledgeIrreversibleDelete())) {
            throw new IllegalArgumentException(
               "acknowledgeIrreversibleDelete: must be true because this changeset contains a " +
               "delete -- disposing a materialized view has NO live inverse; the Tier-2 snapshot " +
               "taken for this apply (when one is taken) is the only recovery path, not merely the " +
               "path of last resort");
         }

         String orgId = OrganizationManager.getInstance().getCurrentOrgID(user);
         String txId = "mv-" + newIdSuffix();
         String backupRef = plan.requiresStorageBackup() ? backupService.backup(txId) : null;
         List<ApplyOutcome> results = new ArrayList<>();
         List<Undo> undoable = new ArrayList<>();
         List<RollbackFailure> unknownStateFailures = new ArrayList<>();
         boolean failed = false;

         for(int i = 0; i < plan.changes().size(); i++) {
            PlanChange change = plan.changes().get(i);
            MvChangePlanService.FlatChange fc = flat.get(i);

            try {
               if(MvChangeRequest.VERB_CREATE.equals(fc.verb)) {
                  applyCreate(txId, reviewedTask, change, fc, orgId, backupRef,
                             req.getReviewOutcome(), user, results, undoable);
               }
               else if(MvChangeRequest.VERB_SET_CYCLE.equals(fc.verb)) {
                  applySetCycle(txId, reviewedTask, change, fc, orgId, backupRef,
                               req.getReviewOutcome(), user, results, undoable);
               }
               else {
                  applyDelete(txId, reviewedTask, fc, orgId, backupRef, req.getReviewOutcome(), user,
                             results);
               }
            }
            catch(Throwable e) {
               // A throw carries no verifiable before/after evidence for THIS change -- unlike a
               // reported failure, it must never be treated as rolled back. Same rule
               // AdminChangesetApplyService's own javadoc documents for properties. Throwable (not
               // Exception): MVSupportService.createMV itself declares `throws Throwable`.
               results.add(new ApplyOutcome(fc.mvName, null, null, AdminChangeRecord.STATUS_FAILED,
                                            messageOf(e)));
               unknownStateFailures.add(new RollbackFailure(fc.mvName,
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

         LOG.error("MV changeset {} rollback failed; mvs still changed: {}", txId,
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
                            MvChangePlanService.FlatChange fc, String orgId, String backupRef,
                            String reviewOutcome, Principal user, List<ApplyOutcome> results,
                            List<Undo> undoable)
      throws Throwable
   {
      MVSupportService.AnalysisResult analysisResult =
         mvGateway.getAnalysisResult(fc.source.getAnalysisId());
      List<MVSupportService.MVStatus> mvStatusList = analysisResult.getStatus();

      // Folds the server's own step 5 (set-cycle) and step 6 (create) back to back, so the
      // intermediate "cycle set, mv not yet created" durable-but-incomplete state the raw EM
      // endpoint allows is never left visible from this apply call.
      if(fc.source.getCycle() != null) {
         mvGateway.setDataCycle(List.of(fc.mvName), analysisResult, fc.source.getCycle(), orgId);
      }

      boolean noData = fc.source.getNoData() == null || fc.source.getNoData();
      boolean background = fc.source.getRunInBackground() == null || fc.source.getRunInBackground();
      String exception = mvGateway.createMV(List.of(fc.mvName), mvStatusList, background, noData,
                                            user);
      boolean verified = exception == null && mvGateway.existsInOrg(fc.mvName, orgId);
      String status = verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED;
      results.add(new ApplyOutcome(fc.mvName, null, change.proposedValue(), status,
                                   verified ? null : (exception != null ? exception :
                                      "mv not found after create")));
      writeAudit(txId, task, fc.mvName, AdminChangeRecord.ACTION_APPLY, null, change.proposedValue(),
                status, AdminChangeRecord.RISK_HIGH, backupRef, reviewOutcome, user);

      if(verified) {
         undoable.add(new Undo(MvChangeRequest.VERB_CREATE, fc.mvName, orgId, null, null));
      }
   }

   private void applySetCycle(String txId, String task, PlanChange change,
                              MvChangePlanService.FlatChange fc, String orgId, String backupRef,
                              String reviewOutcome, Principal user, List<ApplyOutcome> results,
                              List<Undo> undoable)
   {
      MVSupportService.AnalysisResult analysisResult =
         mvGateway.getAnalysisResult(fc.source.getAnalysisId());
      String before = change.currentValue();
      mvGateway.setDataCycle(List.of(fc.mvName), analysisResult, fc.source.getCycle(), orgId);

      String after = analysisResult.getStatus().stream()
         .filter(s -> s.getDefinition().getName().equals(fc.mvName))
         .map(s -> s.getDefinition().getCycle())
         .findFirst().orElse(null);
      boolean verified = Objects.equals(normalizeCycle(after), normalizeCycle(fc.source.getCycle()));
      String status = verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED;
      results.add(new ApplyOutcome(fc.mvName, before, after, status,
                                   verified ? null : "cycle not updated as requested"));
      writeAudit(txId, task, fc.mvName, AdminChangeRecord.ACTION_APPLY, before, after, status,
                AdminChangeRecord.RISK_LOW, backupRef, reviewOutcome, user);

      if(verified) {
         undoable.add(new Undo(MvChangeRequest.VERB_SET_CYCLE, fc.mvName, orgId,
                               fc.source.getAnalysisId(), before));
      }
   }

   private void applyDelete(String txId, String task, MvChangePlanService.FlatChange fc,
                            String orgId, String backupRef, String reviewOutcome, Principal user,
                            List<ApplyOutcome> results)
   {
      mvGateway.dispose(List.of(fc.mvName));
      boolean verified = !mvGateway.existsInOrg(fc.mvName, orgId);
      String status = verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED;
      results.add(new ApplyOutcome(fc.mvName, "exists", null, status,
                                   verified ? null : "mv still exists after delete"));
      writeAudit(txId, task, fc.mvName, AdminChangeRecord.ACTION_APPLY, "exists", null, status,
                AdminChangeRecord.RISK_HIGH, backupRef, reviewOutcome, user);
      // Never added to undoable -- delete is declared non-compensable (section 1/4): no live
      // inverse once the definition and its cluster files are gone.
   }

   /** Undoes verified create/set_cycle changes newest-first. delete is never present here (never
    * added to {@code undoable} in the first place). */
   private List<RollbackFailure> rollback(String txId, String task, List<Undo> undoable,
                                          String backupRef, String reviewOutcome, Principal user)
   {
      List<RollbackFailure> failures = new ArrayList<>();

      for(int i = undoable.size() - 1; i >= 0; i--) {
         Undo undo = undoable.get(i);

         try {
            if(MvChangeRequest.VERB_CREATE.equals(undo.originalVerb)) {
               mvGateway.dispose(List.of(undo.mvName));
               boolean verified = !mvGateway.existsInOrg(undo.mvName, undo.orgId);
               writeAudit(txId, task, undo.mvName, AdminChangeRecord.ACTION_ROLLBACK, null, null,
                         verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED,
                         AdminChangeRecord.RISK_HIGH, backupRef, reviewOutcome, user);

               if(!verified) {
                  failures.add(new RollbackFailure(undo.mvName,
                     "rollback of create reported the mv as still present after dispose"));
               }
            }
            else {
               // Undo a set_cycle: write the captured old cycle back. If the analysisId has since
               // expired, this fails and is reported as a genuine rollback failure -- documented,
               // not silently smoothed over (design section 5, risk item).
               MVSupportService.AnalysisResult analysisResult =
                  mvGateway.getAnalysisResult(undo.analysisId);
               mvGateway.setDataCycle(List.of(undo.mvName), analysisResult, undo.oldCycle, undo.orgId);
               String after = analysisResult.getStatus().stream()
                  .filter(s -> s.getDefinition().getName().equals(undo.mvName))
                  .map(s -> s.getDefinition().getCycle())
                  .findFirst().orElse(null);
               boolean verified = Objects.equals(normalizeCycle(after), normalizeCycle(undo.oldCycle));
               writeAudit(txId, task, undo.mvName, AdminChangeRecord.ACTION_ROLLBACK, null, null,
                         verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED,
                         AdminChangeRecord.RISK_LOW, backupRef, reviewOutcome, user);

               if(!verified) {
                  failures.add(new RollbackFailure(undo.mvName,
                     "rollback of set_cycle reported the cycle as not restored"));
               }
            }
         }
         catch(Exception e) {
            failures.add(new RollbackFailure(undo.mvName, messageOf(e)));
         }
      }

      return failures;
   }

   private static String normalizeCycle(String cycle) {
      return (cycle == null || cycle.isEmpty()) ? null : cycle;
   }

   private void writeAudit(String txId, String task, String mvName, String adminAction,
                           String before, String after, String status, String riskLevel,
                           String backupRef, String reviewOutcome, Principal user)
   {
      try {
         AdminChangeRecord record = new AdminChangeRecord();
         record.setTransactionId(txId);
         record.setTaskDescription(task);
         record.setProperty(mvName);
         record.setObjectType("Materialized View");
         record.setBeforeValue(before);
         record.setAfterValue(after);
         record.setAction(adminAction);
         record.setStatus(status);
         record.setRiskLevel(riskLevel);
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
         LOG.error("Failed to write mv admin change audit record for transaction {}", txId,
                   auditFailure);
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
      Undo(String originalVerb, String mvName, String orgId, String analysisId, String oldCycle) {
         this.originalVerb = originalVerb;
         this.mvName = mvName;
         this.orgId = orgId;
         this.analysisId = analysisId;
         this.oldCycle = oldCycle;
      }

      final String originalVerb;
      final String mvName;
      final String orgId;
      /** Only set for {@code set_cycle} -- the analysisId to re-resolve the pending candidate
       * from when writing {@link #oldCycle} back. */
      final String analysisId;
      /** Only set for {@code set_cycle} -- the cycle value captured before this apply. */
      final String oldCycle;
   }

   private static final Logger LOG = LoggerFactory.getLogger(MvChangesetApplyService.class);
   private static final SecureRandom RANDOM = new SecureRandom();
   /** Serializes the entire body of {@link #apply} -- JVM-local only, same caveat as every other
    * area's own {@code APPLY_LOCK}. */
   private static final ReentrantLock APPLY_LOCK = new ReentrantLock();
   private final MvChangePlanService planService;
   private final AdminMvGateway mvGateway;
   private final AdminBackupService backupService;
}
