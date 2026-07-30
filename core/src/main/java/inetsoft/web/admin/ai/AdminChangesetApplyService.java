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
package inetsoft.web.admin.ai;

import inetsoft.util.audit.AdminChangeRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.security.SecureRandom;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Applies a whole changeset, all-or-nothing, and audits every attempt.
 *
 * <p>N property writes through {@code SreeEnv} cannot be made atomic, so this is a <b>compensating
 * transaction</b>: on failure, each already-verified change is undone in reverse using the
 * {@code beforeValue} the server recorded during the apply — authoritative even if the value drifted
 * since the preview.
 *
 * <p>Two failure paths deserve care, because mishandling either leaves the server partially changed:
 *
 * <ul>
 *   <li>A change can fail <em>by throwing</em>, not only by returning {@code status:"failed"}.
 *       Letting an exception escape would abandon earlier changes in an applied state, so a throw is
 *       recorded as a failed change and triggers the same rollback.</li>
 *   <li>An undo can itself fail. Every undo is attempted regardless, and any failure yields
 *       {@link #STATUS_ROLLBACK_FAILED} naming the properties still changed — a caller cannot fix
 *       what it is not told about.</li>
 * </ul>
 */
@Component
public class AdminChangesetApplyService {
   @Autowired
   public AdminChangesetApplyService(AdminChangePlanService planService,
                                     AdminChangeService changeService,
                                     AdminBackupService backupService)
   {
      this.planService = planService;
      this.changeService = changeService;
      this.backupService = backupService;
   }

   /** Thrown when {@code apply} carries a hash that does not match the freshly resolved plan. */
   public static class PlanHashMismatchException extends RuntimeException {
      public PlanHashMismatchException(ResolvedPlan current) {
         super("planHash: does not match the current plan; re-review before applying");
         this.current = current;
      }

      /** The plan as it stands now, so the caller can show the operator what changed. */
      public ResolvedPlan current() {
         return current;
      }

      private final transient ResolvedPlan current;
   }

   /**
    * Resolves, gates on the plan hash, then executes.
    *
    * @throws PlanHashMismatchException if the hash is missing or stale (maps to HTTP 409).
    * @throws Exception if the Tier-2 backup fails, in which case nothing was applied.
    */
   public ApplyResult apply(ApplyRequest req, Principal user) throws Exception {
      ResolvedPlan plan = planService.resolve(req);

      if(req.getPlanHash() == null || !plan.planHash().equals(req.getPlanHash())) {
         throw new PlanHashMismatchException(plan);
      }

      String txId = "chg-" + newIdSuffix();
      String backupRef = plan.requiresStorageBackup() ? backupService.backup(txId) : null;
      List<ApplyOutcome> results = new ArrayList<>();
      List<PlanChange> undoable = new ArrayList<>();
      List<String> undoableBefore = new ArrayList<>();
      // A thrown apply carries no before/after evidence, so its remaining state is unknown - it
      // can never be silently reported as "rolled back". See the loop below.
      List<RollbackFailure> unknownStateFailures = new ArrayList<>();
      boolean failed = false;

      for(PlanChange change : plan.changes()) {
         // The bookkeeping below lives INSIDE the try: an unexpected null/malformed result would
         // otherwise NPE out of apply() with earlier changes left applied and no rollback
         // attempted - exactly the failure mode this method exists to prevent.
         try {
            AdminChangeResult applied = changeService.applyChange(
               request(txId, plan.task(), change, AdminChangeRecord.ACTION_APPLY,
                       change.proposedValue(), backupRef, req.getReviewOutcome()),
               user);

            results.add(new ApplyOutcome(change.property(), applied.getBeforeValue(),
                                         applied.getAfterValue(), applied.getStatus(),
                                         applied.getError()));

            boolean verified = AdminChangeRecord.STATUS_VERIFIED.equals(applied.getStatus());
            // Path A (AdminChangeService): SreeEnv.save() can succeed and then a side-effect hook
            // can throw, so a change comes back with status "failed" even though before != after
            // - the write took effect. Status alone is not a reliable signal of whether the
            // server actually moved, so anything demonstrably moved is undoable regardless of
            // status, in addition to anything verified.
            boolean moved = !Objects.equals(applied.getBeforeValue(), applied.getAfterValue());

            if(verified || moved) {
               undoable.add(change);
               undoableBefore.add(applied.getBeforeValue());
            }

            if(!verified) {
               failed = true;
               break;
            }
         }
         catch(Exception e) {
            // A throw is a failed change, not a reason to abandon the rollback - but unlike a
            // reported failure, it carries no before/after evidence, so this property's state is
            // unknown and must never be reported as rolled back.
            results.add(new ApplyOutcome(change.property(), null, null,
                                         AdminChangeRecord.STATUS_FAILED, messageOf(e)));
            unknownStateFailures.add(new RollbackFailure(change.property(),
               "state unknown: apply did not return a verifiable outcome (" + messageOf(e) + ")"));
            failed = true;
            break;
         }
      }

      if(!failed) {
         return new ApplyResult(txId, STATUS_APPLIED, backupRef,
                                Collections.unmodifiableList(results), null);
      }

      List<RollbackFailure> failures = new ArrayList<>(unknownStateFailures);
      failures.addAll(rollback(txId, plan.task(), undoable, undoableBefore, backupRef,
                               req.getReviewOutcome(), user));

      if(failures.isEmpty()) {
         return new ApplyResult(txId, STATUS_ROLLED_BACK, backupRef,
                                Collections.unmodifiableList(results), null);
      }

      LOG.error("Admin changeset {} rollback failed; properties still changed: {}", txId,
               failures.stream().map(RollbackFailure::property).collect(Collectors.joining(", ")));
      return new ApplyResult(txId, STATUS_ROLLBACK_FAILED, backupRef,
                             Collections.unmodifiableList(results),
                             Collections.unmodifiableList(failures));
   }

   /** Undoes verified changes newest-first, attempting all of them and collecting any failures. */
   private List<RollbackFailure> rollback(String txId, String task, List<PlanChange> undoable,
                                          List<String> befores, String backupRef,
                                          String reviewOutcome, Principal user)
   {
      List<RollbackFailure> failures = new ArrayList<>();

      for(int i = undoable.size() - 1; i >= 0; i--) {
         PlanChange change = undoable.get(i);

         try {
            AdminChangeResult undone = changeService.applyChange(
               request(txId, task, change, AdminChangeRecord.ACTION_ROLLBACK, befores.get(i),
                       backupRef, reviewOutcome),
               user);

            if(!AdminChangeRecord.STATUS_VERIFIED.equals(undone.getStatus())) {
               failures.add(new RollbackFailure(change.property(), undone.getError() == null
                  ? "rollback reported failure without an error message" : undone.getError()));
            }
         }
         catch(Exception e) {
            failures.add(new RollbackFailure(change.property(), messageOf(e)));
         }
      }

      return failures;
   }

   private static AdminChangeRequest request(String txId, String task, PlanChange change,
                                             String action, String value, String backupRef,
                                             String reviewOutcome)
   {
      AdminChangeRequest req = new AdminChangeRequest();
      req.setTransactionId(txId);
      req.setTaskDescription(task);
      req.setProperty(change.property());
      req.setValue(value);
      req.setAction(action);
      req.setRiskLevel(change.risk());
      req.setSnapshotScope(change.snapshotScope());
      req.setBackupRef(backupRef);
      req.setReviewOutcome(reviewOutcome);
      return req;
   }

   private static String messageOf(Exception e) {
      return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
   }

   private static String newIdSuffix() {
      return String.format("%08x", RANDOM.nextInt());
   }

   public static final String STATUS_APPLIED = "applied";
   public static final String STATUS_ROLLED_BACK = "rolled-back";
   public static final String STATUS_ROLLBACK_FAILED = "rollback-failed";
   private static final Logger LOG = LoggerFactory.getLogger(AdminChangesetApplyService.class);
   private static final SecureRandom RANDOM = new SecureRandom();
   private final AdminChangePlanService planService;
   private final AdminChangeService changeService;
   private final AdminBackupService backupService;
}
