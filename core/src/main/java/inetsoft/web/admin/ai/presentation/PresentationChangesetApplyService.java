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
package inetsoft.web.admin.ai.presentation;

import inetsoft.util.Tool;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.AdminChangeRecord;
import inetsoft.util.audit.Audit;
import inetsoft.web.admin.ai.AdminBackupService;
import inetsoft.web.admin.ai.AdminChangesetApplyService;
import inetsoft.web.admin.ai.PlanChange;
import inetsoft.web.admin.ai.RollbackFailure;
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
 * Applies a whole presentation changeset, all-or-nothing, and audits every attempt -- the
 * presentation analog of {@code inetsoft.web.admin.ai.AdminChangesetApplyService} and (within this
 * run) {@code LicenseChangesetApplyService}/{@code ClusterChangesetApplyService}, replicated rather
 * than shared (01-spec.md section 6).
 *
 * <p><b>Two rollback classes, matching 01-spec.md section 4/6's value/storage split.</b> The 12
 * value-scope sub-models have a real live inverse: the {@code currentModel} object
 * {@link PresentationChangePlanService#resolveEntries} captured before the write is simply written
 * back. The 4 storage-scope sub-models ({@code lookAndFeel}/{@code welcomePage}/{@code loginBanner}/
 * {@code portalIntegration}) have none in this cut -- a successful storage-scope write is never
 * undone; if a later change in the same apply fails, that earlier success is reported as an
 * unconditional {@link RollbackFailure} (recovery is the Tier-2 backup, not a compensating write),
 * so the overall status is honestly {@code rollback-failed} rather than a {@code rolled-back} that
 * would be a lie about what's actually still on disk.
 */
@Component
public class PresentationChangesetApplyService {
   @Autowired
   public PresentationChangesetApplyService(PresentationChangePlanService planService,
                                            PresentationSettingsAccess access,
                                            AdminBackupService backupService)
   {
      this.planService = planService;
      this.access = access;
      this.backupService = backupService;
   }

   /**
    * Resolves fresh, gates on the plan hash, {@code reviewOutcome} (always required -- this area's
    * plan is always high risk, 01-spec.md section 11), and {@code acknowledgeIrreversibleUpdate}
    * (required exactly when the freshly re-resolved plan touches a storage-scope sub-model), backs
    * up, then executes.
    *
    * @throws AdminChangesetApplyService.PlanHashMismatchException if the hash is missing or stale
    *         (maps to HTTP 409).
    * @throws Exception if the Tier-2 backup itself fails, in which case nothing was applied.
    */
   public PresentationApplyResult apply(PresentationApplyRequest req, Principal user) throws Exception {
      APPLY_LOCK.lock();

      try {
         List<PresentationChangePlanService.ResolvedChange> resolved =
            planService.resolveEntries(req, user);
         List<PlanChange> planChanges = new ArrayList<>();

         for(PresentationChangePlanService.ResolvedChange entry : resolved) {
            planChanges.add(entry.planChange());
         }

         String task = req.getTask().trim();
         String currentHash = PresentationChangePlanService.hash(task, planChanges);

         if(req.getPlanHash() == null || !currentHash.equals(req.getPlanHash())) {
            throw new AdminChangesetApplyService.PlanHashMismatchException(
               new inetsoft.web.admin.ai.ResolvedPlan(task, planChanges, true, true, currentHash));
         }

         if(req.getReviewOutcome() == null || req.getReviewOutcome().trim().isEmpty()) {
            throw new IllegalArgumentException(
               "reviewOutcome: required -- every presentation change plan is high risk " +
               "(01-spec.md section 11)");
         }

         boolean touchesStorage = resolved.stream().anyMatch(e -> e.subModel().isStorageScope());

         if(touchesStorage && !Boolean.TRUE.equals(req.getAcknowledgeIrreversibleUpdate())) {
            throw new IllegalArgumentException(
               "acknowledgeIrreversibleUpdate: must be true because this changeset touches a " +
               "storage-scope sub-model (lookAndFeel/welcomePage/loginBanner/portalIntegration), " +
               "which has no live rollback in this cut (01-spec.md section 6)");
         }

         String txId = "pres-" + newIdSuffix();
         String backupRef = backupService.backup(txId);
         String reviewOutcome = req.getReviewOutcome();
         List<PresentationApplyOutcome> results = new ArrayList<>();
         List<PresentationChangePlanService.ResolvedChange> undoableValue = new ArrayList<>();
         List<PresentationChangePlanService.ResolvedChange> appliedStorage = new ArrayList<>();
         List<RollbackFailure> unknownStateFailures = new ArrayList<>();
         boolean failed = false;

         for(PresentationChangePlanService.ResolvedChange entry : resolved) {
            String key = entry.planChange().property();
            String before = entry.planChange().currentValue();
            String expectedAfter = entry.planChange().proposedValue();

            try {
               access.write(entry.subModel(), entry.proposedModel(), user, entry.global());
               String actualAfter = PresentationChangePlanService.projectedValue(
                  entry.subModel(), PresentationJson.toNode(
                     access.read(entry.subModel(), user, entry.global())));
               boolean verified = expectedAfter.equals(actualAfter);
               String status = verified
                  ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED;
               results.add(new PresentationApplyOutcome(key, before, actualAfter, status,
                  verified ? null : "value did not read back as written", null));
               writeAudit(txId, task, key, entry, AdminChangeRecord.ACTION_APPLY, before, actualAfter, status,
                         backupRef, reviewOutcome, user);

               if(!verified) {
                  failed = true;
                  break;
               }

               if(entry.subModel().isStorageScope()) {
                  appliedStorage.add(entry);
               }
               else {
                  undoableValue.add(entry);
               }
            }
            catch(Exception e) {
               // A throw carries no verifiable before/after evidence for THIS change -- must never
               // be treated as rolled back, same rule every prior area's apply service follows.
               results.add(new PresentationApplyOutcome(key, before, null,
                  AdminChangeRecord.STATUS_FAILED, messageOf(e), null));
               unknownStateFailures.add(new RollbackFailure(key,
                  "state unknown: apply did not return a verifiable outcome (" + messageOf(e) + ")"));
               writeAudit(txId, task, key, entry, AdminChangeRecord.ACTION_APPLY, before, null,
                         AdminChangeRecord.STATUS_FAILED, backupRef, reviewOutcome, user);
               failed = true;
               break;
            }
         }

         if(!failed) {
            return new PresentationApplyResult(txId, AdminChangesetApplyService.STATUS_APPLIED,
               backupRef, Collections.unmodifiableList(results), null);
         }

         List<RollbackFailure> failures = new ArrayList<>(unknownStateFailures);

         // Storage-scope successes that happened before the failure: no live inverse exists in this
         // cut (01-spec.md section 4/6) -- unconditionally reported, never silently left out of the
         // response, so the operator knows recovery for these means restoring the Tier-2 backup.
         for(PresentationChangePlanService.ResolvedChange applied : appliedStorage) {
            failures.add(new RollbackFailure(applied.planChange().property(),
               "storage-scope sub-model update cannot be rolled back in this cut (01-spec.md " +
               "section 4/6) -- recovery requires restoring the Tier-2 backup for transaction " +
               txId + " (backupRef: " + backupRef + ")"));
         }

         failures.addAll(rollback(txId, task, undoableValue, backupRef, reviewOutcome, user));

         String status = failures.isEmpty()
            ? AdminChangesetApplyService.STATUS_ROLLED_BACK
            : AdminChangesetApplyService.STATUS_ROLLBACK_FAILED;

         if(!failures.isEmpty()) {
            LOG.error("Presentation changeset {} rollback failed; sub-models still changed: {}", txId,
                     failures.stream().map(RollbackFailure::property).collect(Collectors.joining(", ")));
         }

         return new PresentationApplyResult(txId, status, backupRef,
            Collections.unmodifiableList(results),
            failures.isEmpty() ? null : Collections.unmodifiableList(failures));
      }
      finally {
         APPLY_LOCK.unlock();
      }
   }

   /** Undoes verified value-scope changes newest-first, attempting all of them and collecting any
    * failures -- never called for a storage-scope entry (see {@link #apply}). */
   private List<RollbackFailure> rollback(String txId, String task,
                                          List<PresentationChangePlanService.ResolvedChange> undoable,
                                          String backupRef, String reviewOutcome, Principal user)
   {
      List<RollbackFailure> failures = new ArrayList<>();

      for(int i = undoable.size() - 1; i >= 0; i--) {
         PresentationChangePlanService.ResolvedChange entry = undoable.get(i);
         String key = entry.planChange().property();
         String expectedBefore = entry.planChange().currentValue();

         try {
            access.write(entry.subModel(), entry.currentModel(), user, entry.global());
            String restoredValue = PresentationChangePlanService.projectedValue(
               entry.subModel(), PresentationJson.toNode(
                  access.read(entry.subModel(), user, entry.global())));
            boolean verified = expectedBefore.equals(restoredValue);
            writeAudit(txId, task, key, entry, AdminChangeRecord.ACTION_ROLLBACK, null, restoredValue,
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

   private void writeAudit(String txId, String task, String key,
                           PresentationChangePlanService.ResolvedChange entry, String adminAction,
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
         record.setOrganizationId(entry.global() ? null :
            inetsoft.sree.security.OrganizationManager.getInstance().getCurrentOrgID());
         record.setUserName(user == null ? null : user.getName());
         record.setActionTimestamp(new Timestamp(System.currentTimeMillis()));
         record.setServerHostName(Tool.getHost());
         Audit.getInstance().auditAdminChange(record, user);
      }
      catch(Exception auditFailure) {
         // An audit write must never replace the real outcome -- same rule every prior area's apply
         // service follows.
         LOG.error("Failed to write presentation admin change audit record for transaction {}", txId,
                   auditFailure);
      }
   }

   private static String messageOf(Exception e) {
      return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
   }

   private static String newIdSuffix() {
      return String.format("%016x", RANDOM.nextLong());
   }

   private static final Logger LOG = LoggerFactory.getLogger(PresentationChangesetApplyService.class);
   private static final SecureRandom RANDOM = new SecureRandom();
   /** Serializes the entire body of {@link #apply} -- same rationale and same JVM-local-only
    * limitation as every prior area's own lock. */
   private static final ReentrantLock APPLY_LOCK = new ReentrantLock();
   private final PresentationChangePlanService planService;
   private final PresentationSettingsAccess access;
   private final AdminBackupService backupService;
}
