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
package inetsoft.web.admin.ai.licensing;

import inetsoft.report.internal.license.License;
import inetsoft.report.internal.license.LicenseManager;
import inetsoft.report.internal.license.LicenseType;
import inetsoft.util.Tool;
import inetsoft.util.audit.*;
import inetsoft.web.admin.ai.*;
import inetsoft.web.admin.general.LicenseKeySettingsService;
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
 * Applies a whole license changeset, all-or-nothing, and audits every attempt -- the licensing
 * analog of {@code inetsoft.web.admin.ai.AdminChangesetApplyService} and (within this run)
 * {@code ProviderChangesetApplyService}/{@code ClusterChangesetApplyService}, replicated rather
 * than shared (01-spec.md section 6, carry-forward item 5).
 *
 * <p>Every verb in this area is {@code snapshotScope: storage} unconditionally (01-spec.md section
 * 4/7/14 D4), so the Tier-2 backup is taken synchronously here, before any change is attempted.
 *
 * <p>Calls {@link LicenseKeySettingsService#addServerKey}/{@code removeServerKey} -- never
 * {@code LicenseManager.addLicense}/{@code removeLicense} directly -- so the cluster-broadcast and
 * auth-cache-reset side effects {@code setModel} performs are always replicated (01-spec.md section
 * 0).
 */
@Component
public class LicenseChangesetApplyService {
   @Autowired
   public LicenseChangesetApplyService(LicenseChangePlanService planService,
                                       LicenseManager licenseManager,
                                       LicenseKeySettingsService licenseKeySettingsService,
                                       AdminBackupService backupService)
   {
      this.planService = planService;
      this.licenseManager = licenseManager;
      this.licenseKeySettingsService = licenseKeySettingsService;
      this.backupService = backupService;
   }

   /**
    * Resolves fresh, gates on the plan hash and (when applicable) {@code acknowledgeDelicensing},
    * backs up, then executes.
    *
    * @throws AdminChangesetApplyService.PlanHashMismatchException if the hash is missing or stale
    *         (maps to HTTP 409) -- reused verbatim, per 01-spec.md section 6.
    * @throws Exception if the Tier-2 backup itself fails, in which case nothing was applied.
    */
   public LicenseApplyResult apply(LicenseApplyRequest req, Principal user) throws Exception {
      APPLY_LOCK.lock();

      try {
         ResolvedPlan plan = planService.resolve(req);

         if(req.getPlanHash() == null || !plan.planHash().equals(req.getPlanHash())) {
            throw new AdminChangesetApplyService.PlanHashMismatchException(plan);
         }

         if(plan.requiresAgentSignoff() &&
            (req.getReviewOutcome() == null || req.getReviewOutcome().trim().isEmpty()))
         {
            throw new IllegalArgumentException(
               "reviewOutcome: required because this changeset contains a high-risk change");
         }

         // Re-derived fresh, from live state, before any mutation -- mirrors
         // DataSourceApplyRequest.acknowledgeIrreversibleDelete's own apply-time re-derivation
         // rather than trusting a preview-time snapshot (03-reconcile.md, 01-spec.md section 6).
         int installedCountNow = licenseManager.getInstalledLicenses().size();
         int addCount = 0;
         int removeCount = 0;

         for(LicenseChangeRequest change : req.getChanges()) {
            if(LicenseChangeRequest.VERB_ADD.equals(change.getVerb())) {
               addCount++;
            }
            else if(LicenseChangeRequest.VERB_REMOVE.equals(change.getVerb())) {
               removeCount++;
            }
         }

         boolean deLicensingWarning = LicenseChangePlanService.computeDeLicensingWarning(
            installedCountNow, addCount, removeCount);

         if(deLicensingWarning && !Boolean.TRUE.equals(req.getAcknowledgeDelicensing())) {
            throw new IllegalArgumentException(
               "acknowledgeDelicensing: must be true because this changeset would leave 0 license " +
               "keys installed (01-spec.md section 5/6)");
         }

         String txId = "license-" + newIdSuffix();
         String backupRef = plan.requiresStorageBackup() ? backupService.backup(txId) : null;
         String reviewOutcome = req.getReviewOutcome();
         List<LicenseApplyOutcome> results = new ArrayList<>();
         List<Undo> undoable = new ArrayList<>();
         List<RollbackFailure> unknownStateFailures = new ArrayList<>();
         boolean failed = false;

         List<LicenseChangeRequest> originals = req.getChanges();

         for(int i = 0; i < plan.changes().size(); i++) {
            PlanChange change = plan.changes().get(i);
            LicenseChangeRequest original = originals.get(i);
            String key = change.property();

            try {
               applyOne(txId, plan.task(), key, original, backupRef, reviewOutcome, user, results,
                       undoable);
            }
            catch(Exception e) {
               // A throw carries no verifiable before/after evidence for THIS change -- must never
               // be treated as rolled back. Same rule every prior area's apply service follows.
               results.add(new LicenseApplyOutcome(key, null, null, AdminChangeRecord.STATUS_FAILED,
                                                   messageOf(e), null));
               unknownStateFailures.add(new RollbackFailure(key,
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
            return new LicenseApplyResult(txId, AdminChangesetApplyService.STATUS_APPLIED, backupRef,
                                          Collections.unmodifiableList(results), null);
         }

         Map<String, String> rollbackAdvisories = new LinkedHashMap<>();
         List<RollbackFailure> failures = new ArrayList<>(unknownStateFailures);
         failures.addAll(rollback(txId, plan.task(), undoable, backupRef, reviewOutcome, user,
                                  rollbackAdvisories));
         List<LicenseApplyOutcome> finalResults = results.stream()
            .map(o -> mergeAdvisory(o, rollbackAdvisories.get(o.property())))
            .collect(Collectors.toList());

         if(failures.isEmpty()) {
            return new LicenseApplyResult(txId, AdminChangesetApplyService.STATUS_ROLLED_BACK,
                                          backupRef, Collections.unmodifiableList(finalResults), null);
         }

         LOG.error("License changeset {} rollback failed; keys still changed: {}", txId,
                  failures.stream().map(RollbackFailure::property).collect(Collectors.joining(", ")));
         return new LicenseApplyResult(txId, AdminChangesetApplyService.STATUS_ROLLBACK_FAILED,
                                       backupRef, Collections.unmodifiableList(finalResults),
                                       Collections.unmodifiableList(failures));
      }
      finally {
         APPLY_LOCK.unlock();
      }
   }

   private void applyOne(String txId, String task, String key, LicenseChangeRequest original,
                         String backupRef, String reviewOutcome, Principal user,
                         List<LicenseApplyOutcome> results, List<Undo> undoable)
      throws Exception
   {
      String verb = LicenseChangePlanService.requireVerb("apply." + key, original.getVerb());

      if(LicenseChangeRequest.VERB_ADD.equals(verb)) {
         applyAdd(txId, task, key, backupRef, reviewOutcome, user, results, undoable);
      }
      else {
         applyRemove(txId, task, key, backupRef, reviewOutcome, user, results, undoable);
      }
   }

   // ---------------------------------------------------------------- add

   /** Re-verifies not-already-installed AND re-resolves type/valid fresh (01-spec.md section 6/7)
    * -- a key installed by a concurrent EM-console session between preview and apply, or one whose
    * parse result changed, is refused here rather than acted on against stale evidence. */
   private void applyAdd(String txId, String task, String key, String backupRef,
                         String reviewOutcome, Principal user, List<LicenseApplyOutcome> results,
                         List<Undo> undoable)
      throws Exception
   {
      boolean alreadyInstalled = isInstalled(key);

      if(alreadyInstalled) {
         results.add(new LicenseApplyOutcome(key, null, null, AdminChangeRecord.STATUS_FAILED,
            "already installed at apply time (concurrent change)", null));
         writeAudit(txId, task, key, ActionRecord.ACTION_NAME_CREATE, AdminChangeRecord.ACTION_APPLY,
                   null, null, AdminChangeRecord.STATUS_FAILED, backupRef, reviewOutcome, user);
         return;
      }

      License resolved = licenseManager.parseLicense(key);

      if(resolved.type() == LicenseType.INVALID || !resolved.valid()) {
         results.add(new LicenseApplyOutcome(key, null, null, AdminChangeRecord.STATUS_FAILED,
            "no longer resolves to a valid license at apply time (concurrent change)", null));
         writeAudit(txId, task, key, ActionRecord.ACTION_NAME_CREATE, AdminChangeRecord.ACTION_APPLY,
                   null, null, AdminChangeRecord.STATUS_FAILED, backupRef, reviewOutcome, user);
         return;
      }

      licenseKeySettingsService.addServerKey(key);

      boolean verified = isInstalled(key);
      String status = verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED;
      String after = verified ? LicenseKeyProjection.of(resolved).canonical() : null;
      results.add(new LicenseApplyOutcome(key, null, after, status,
                                          verified ? null : "key not found among installed licenses " +
                                          "after add", null));
      writeAudit(txId, task, key, ActionRecord.ACTION_NAME_CREATE, AdminChangeRecord.ACTION_APPLY,
                null, after, status, backupRef, reviewOutcome, user);

      if(verified) {
         undoable.add(Undo.added(key));
      }
   }

   // ---------------------------------------------------------------- remove

   private void applyRemove(String txId, String task, String key, String backupRef,
                            String reviewOutcome, Principal user, List<LicenseApplyOutcome> results,
                            List<Undo> undoable)
      throws Exception
   {
      Optional<License> current = findInstalled(key);

      if(current.isEmpty()) {
         results.add(new LicenseApplyOutcome(key, null, null, AdminChangeRecord.STATUS_FAILED,
            "not installed at apply time (concurrent change)", null));
         writeAudit(txId, task, key, ActionRecord.ACTION_NAME_DELETE, AdminChangeRecord.ACTION_APPLY,
                   null, null, AdminChangeRecord.STATUS_FAILED, backupRef, reviewOutcome, user);
         return;
      }

      String before = LicenseKeyProjection.of(current.get()).canonical();
      licenseKeySettingsService.removeServerKey(key);

      boolean verified = !isInstalled(key);
      String status = verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED;
      results.add(new LicenseApplyOutcome(key, before, null, status,
                                          verified ? null : "key still present after remove", null));
      writeAudit(txId, task, key, ActionRecord.ACTION_NAME_DELETE, AdminChangeRecord.ACTION_APPLY,
                before, null, status, backupRef, reviewOutcome, user);

      if(verified) {
         undoable.add(Undo.removed(key));
      }
   }

   // ---------------------------------------------------------------- rollback

   private List<RollbackFailure> rollback(String txId, String task, List<Undo> undoable,
                                          String backupRef, String reviewOutcome, Principal user,
                                          Map<String, String> advisories)
   {
      List<RollbackFailure> failures = new ArrayList<>();

      for(int i = undoable.size() - 1; i >= 0; i--) {
         Undo undo = undoable.get(i);

         try {
            if(undo.kind == Undo.Kind.ADDED) {
               rollbackAdded(txId, task, undo, backupRef, reviewOutcome, user, failures);
            }
            else {
               rollbackRemoved(txId, task, undo, backupRef, reviewOutcome, user, failures, advisories);
            }
         }
         catch(Exception e) {
            failures.add(new RollbackFailure(undo.key, messageOf(e)));
         }
      }

      return failures;
   }

   /** Undo of {@code add} is {@code remove} -- exact (01-spec.md section 4): {@code removeLicense}
    * matches purely on key equality, so there is no partial-state risk. */
   private void rollbackAdded(String txId, String task, Undo undo, String backupRef,
                              String reviewOutcome, Principal user, List<RollbackFailure> failures)
      throws Exception
   {
      if(!isInstalled(undo.key)) {
         failures.add(new RollbackFailure(undo.key,
            "rollback of add could not find the key to remove (already gone)"));
         return;
      }

      licenseKeySettingsService.removeServerKey(undo.key);
      boolean verified = !isInstalled(undo.key);
      writeAudit(txId, task, undo.key, ActionRecord.ACTION_NAME_DELETE,
                AdminChangeRecord.ACTION_ROLLBACK, null, null,
                verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED,
                backupRef, reviewOutcome, user);

      if(!verified) {
         failures.add(new RollbackFailure(undo.key,
            "rollback of add reported the key as still present after remove"));
      }
   }

   /**
    * Undo of {@code remove} is {@code add} -- real but not exact (01-spec.md section 4): the
    * claiming-node selection in {@code EnterpriseLicenseStrategy.addLicense} picks any node with an
    * unclaimed slot, not necessarily the node that originally claimed the key, so a re-add can land
    * on a different cluster node than before -- disclosed as a first-class {@code advisory}.
    *
    * <p>Re-checks not-already-installed before re-adding (03-reconcile.md addition 2/
    * 02-verify-plan.md section 5): a concurrent operator action during the rollback window (EM
    * console, a different admin-chat session) could have already re-added the same key through a
    * different channel, and blindly retrying {@code addServerKey} would risk the same duplicate-add
    * cluster-claim correctness gap a plan-time {@code add} of an already-installed key is refused
    * for.
    */
   private void rollbackRemoved(String txId, String task, Undo undo, String backupRef,
                                String reviewOutcome, Principal user, List<RollbackFailure> failures,
                                Map<String, String> advisories)
      throws Exception
   {
      if(isInstalled(undo.key)) {
         failures.add(new RollbackFailure(undo.key,
            "rollback of remove found the key already reinstalled by a concurrent change -- not " +
            "re-added, to avoid the duplicate-add cluster-claim risk (03-reconcile.md addition 2)"));
         return;
      }

      licenseKeySettingsService.addServerKey(undo.key);
      boolean verified = isInstalled(undo.key);
      String advisory = verified
         ? "re-added key may have been claimed by a different cluster node than before -- " +
           "addLicense's claiming-node selection picks any node with an unclaimed slot, not " +
           "necessarily the original one (01-spec.md section 4)"
         : null;
      writeAudit(txId, task, undo.key, ActionRecord.ACTION_NAME_CREATE,
                AdminChangeRecord.ACTION_ROLLBACK, null, null,
                verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED,
                backupRef, reviewOutcome, user);

      if(!verified) {
         failures.add(new RollbackFailure(undo.key,
            "rollback of remove reported the key as still missing after re-add"));
      }
      else {
         advisories.put(undo.key, advisory);
      }
   }

   // ---------------------------------------------------------------- shared helpers

   private boolean isInstalled(String key) {
      return licenseManager.getInstalledLicenses().stream()
         .anyMatch(l -> Objects.equals(l.key(), key));
   }

   private Optional<License> findInstalled(String key) {
      return licenseManager.getInstalledLicenses().stream()
         .filter(l -> Objects.equals(l.key(), key))
         .findFirst();
   }

   private static LicenseApplyOutcome mergeAdvisory(LicenseApplyOutcome outcome,
                                                     String rollbackAdvisory)
   {
      if(rollbackAdvisory == null) {
         return outcome;
      }

      String combined = outcome.advisory() == null ? rollbackAdvisory :
         outcome.advisory() + " | " + rollbackAdvisory;
      return new LicenseApplyOutcome(outcome.property(), outcome.before(), outcome.after(),
                                     outcome.status(), outcome.error(), combined);
   }

   private void writeAudit(String txId, String task, String key, String actionRecordName,
                           String adminAction, String before, String after, String status,
                           String backupRef, String reviewOutcome, Principal user)
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
         record.setRiskLevel(AdminChangeRecord.RISK_HIGH);
         record.setSnapshotScope(AdminChangeRecord.SCOPE_STORAGE);
         record.setBackupRef(backupRef);
         record.setReviewOutcome(reviewOutcome);
         // organizationId is deliberately left unset: licensing is deployment-wide, not org-scoped
         // (01-spec.md section 1/8), so there is no organization to attribute this change to -- not
         // an oversight, mirrors ProviderChangePlanService.NOT_ORG_SCOPED.
         record.setUserName(user == null ? null : user.getName());
         record.setActionTimestamp(new Timestamp(System.currentTimeMillis()));
         record.setServerHostName(Tool.getHost());
         Audit.getInstance().auditAdminChange(record, user);
      }
      catch(Exception auditFailure) {
         // An audit write must never replace the real outcome -- same rule every prior area's apply
         // service follows.
         LOG.error("Failed to write license admin change audit record for transaction {}", txId,
                   auditFailure);
      }
   }

   private static String lastStatus(List<LicenseApplyOutcome> results) {
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
      enum Kind { ADDED, REMOVED }

      static Undo added(String key) {
         return new Undo(Kind.ADDED, key);
      }

      static Undo removed(String key) {
         return new Undo(Kind.REMOVED, key);
      }

      private Undo(Kind kind, String key) {
         this.kind = kind;
         this.key = key;
      }

      final Kind kind;
      final String key;
   }

   private static final Logger LOG = LoggerFactory.getLogger(LicenseChangesetApplyService.class);
   private static final SecureRandom RANDOM = new SecureRandom();
   /** Serializes the entire body of {@link #apply} -- same rationale and same JVM-local-only
    * limitation as every prior area's own lock. */
   private static final ReentrantLock APPLY_LOCK = new ReentrantLock();
   private final LicenseChangePlanService planService;
   private final LicenseManager licenseManager;
   private final LicenseKeySettingsService licenseKeySettingsService;
   private final AdminBackupService backupService;
}
