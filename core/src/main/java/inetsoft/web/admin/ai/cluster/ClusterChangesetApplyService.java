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
package inetsoft.web.admin.ai.cluster;

import inetsoft.util.Tool;
import inetsoft.util.audit.*;
import inetsoft.web.admin.ai.AdminChangesetApplyService;
import inetsoft.web.admin.ai.PlanChange;
import inetsoft.web.admin.ai.ResolvedPlan;
import inetsoft.web.admin.cluster.ClusterService;
import inetsoft.web.cluster.ServerClusterClient;
import inetsoft.web.cluster.ServerClusterStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Applies a whole cluster pause/resume changeset and audits every attempt -- the cluster analog of
 * {@code inetsoft.web.admin.ai.AdminChangesetApplyService}/{@code ProviderChangesetApplyService},
 * but deliberately NOT a compensating transaction (01-spec.md section 6, 03-reconcile.md).
 *
 * <p><b>The apply loop does not stop-and-rollback on a per-server failure, and there is no
 * {@code undoable} list, no reverse-order undo pass, and no {@code rollback-failed} status</b> --
 * each server's pause/resume is independent and self-inverse: if pausing server C fails after A and B
 * succeeded, there is nothing to fix by force-resuming A and B, only two other admins' successful,
 * intended pauses to needlessly undo. Every entry is attempted regardless of any other entry's
 * outcome, and a per-server status report -- read back fresh, immediately after the call, the direct
 * fix for {@code stylebi#76343} -- is the correct outcome. "Rollback", to the extent this area has
 * one, is exposing the paired verb: {@code apply_cluster_changes} with {@code {verb: "resume", server:
 * X}} is the live undo of a prior {@code {verb: "pause", server: X}}, and vice versa.
 *
 * <p><b>Required disclosure, repeated here at the same prominence as {@link ClusterChangePlanService}
 * (03-reconcile.md, not optional): a "verified" pause outcome is a point-in-time confirmation, not a
 * standing guarantee.</b> If the paused node later crashes, is restarted, or briefly leaves the
 * cluster for any reason, its paused flag is silently cleared on rejoin with no error, no log line
 * above {@code DEBUG}, and no signal back through this area's own tools -- a caller relying on this
 * apply's own "verified" result to mean "still paused" later would be wrong, with nothing in this
 * design telling them so. See {@link ClusterChangePlanService}'s own class javadoc for the full
 * account.
 */
@Component
public class ClusterChangesetApplyService {
   /** The one genuinely new changeset-status vocabulary value this area introduces (01-spec.md
    * section 6 step 6) -- none of {@code AdminChangesetApplyService.STATUS_ROLLED_BACK}/
    * {@code STATUS_ROLLBACK_FAILED} apply here, by design, so reusing that three-value enum would
    * silently imply a full-set-atomicity guarantee this area does not provide. */
   public static final String STATUS_PARTIAL = "partial";
   /** Reused for its literal string value only, per 03-reconcile.md -- never a call through the
    * shared compensating-transaction class. */
   public static final String STATUS_APPLIED = AdminChangesetApplyService.STATUS_APPLIED;
   public static final String STATUS_FAILED = AdminChangeRecord.STATUS_FAILED;

   @Autowired
   public ClusterChangesetApplyService(ClusterChangePlanService planService,
                                       ClusterService clusterService, ServerClusterClient client)
   {
      this.planService = planService;
      this.clusterService = clusterService;
      this.client = client;
   }

   /**
    * Re-resolves via {@link ClusterChangePlanService#resolve}, gates on the plan hash and
    * {@code reviewOutcome} (this area's {@code risk: high} is unconditional, so
    * {@code requiresAgentSignoff} is always {@code true}), then executes every entry, continuing
    * through per-server failures rather than stopping and unwinding.
    *
    * @throws AdminChangesetApplyService.PlanHashMismatchException if the hash is missing or stale
    *         (maps to HTTP 409), reused verbatim.
    */
   public ClusterApplyResult apply(ClusterApplyRequest req, Principal user) {
      APPLY_LOCK.lock();

      try {
         ResolvedPlan plan = planService.resolve(req);

         if(req.getPlanHash() == null || !plan.planHash().equals(req.getPlanHash())) {
            throw new AdminChangesetApplyService.PlanHashMismatchException(plan);
         }

         if(req.getReviewOutcome() == null || req.getReviewOutcome().trim().isEmpty()) {
            throw new IllegalArgumentException(
               "reviewOutcome: required -- risk: high is unconditional for this area (01-spec.md " +
               "section 4/6)");
         }

         String txId = "cluster-" + newIdSuffix();
         String reviewOutcome = req.getReviewOutcome();
         List<ClusterChangeRequest> originals = req.getChanges();
         List<ClusterApplyOutcome> results = new ArrayList<>();

         for(int i = 0; i < plan.changes().size(); i++) {
            PlanChange change = plan.changes().get(i);
            ClusterChangeRequest original = originals.get(i);

            try {
               applyOne(txId, plan.task(), change, original, reviewOutcome, user, results);
            }
            catch(Exception e) {
               // A throw carries no verifiable before/after evidence for THIS entry, but must never
               // stop the remaining entries -- item 4's structural divergence from every other area.
               String server = change.property();
               String message = messageOf(e);
               results.add(new ClusterApplyOutcome(server, change.currentValue(), null, STATUS_FAILED,
                                                    message));
               writeAudit(txId, plan.task(), server, change.currentValue(), null, STATUS_FAILED,
                         reviewOutcome, user);
            }
         }

         return new ClusterApplyResult(txId, overallStatus(results), null,
                                       Collections.unmodifiableList(results));
      }
      finally {
         APPLY_LOCK.unlock();
      }
   }

   private void applyOne(String txId, String task, PlanChange change, ClusterChangeRequest original,
                         String reviewOutcome, Principal user, List<ClusterApplyOutcome> results)
   {
      String server = change.property();
      String verb = ClusterChangePlanService.requireVerb("apply", original.getVerb());
      String before = change.currentValue();

      // Delegates to ClusterService's own pauseServers/resumeServers rather than calling
      // ServerClusterClient.pauseServer/resumeServer directly, so the already-tested "skip if already
      // in the target paused state" logic (ClusterService.java:104-106/120-122) is reused, not
      // reimplemented a second, independently-buggable way (this repo's CLAUDE.md tool-robustness
      // principle, applied to internal reuse as much as external input).
      if(ClusterChangeRequest.VERB_PAUSE.equals(verb)) {
         clusterService.pauseServers(new String[] { server });
      }
      else {
         clusterService.resumeServers(new String[] { server });
      }

      // Read back fresh, immediately after the call -- the direct fix for stylebi#76343, since
      // pauseServers/resumeServers themselves are void and discard the per-server signal entirely.
      ServerClusterStatus after = client.getStatus(server);
      String afterLabel = ClusterStatusLabel.displayStatus(after);
      boolean verified = afterLabel.equals(change.proposedValue());
      String status = verified ? AdminChangeRecord.STATUS_VERIFIED : STATUS_FAILED;
      String error = verified ? null :
         "read-back status \"" + afterLabel + "\" does not match the proposed state \"" +
         change.proposedValue() + "\" -- the server may be unreachable, or the pause/resume message " +
         "did not complete (stylebi#76343: the raw endpoint discards this signal, this area's own " +
         "read-back is the fix)";
      results.add(new ClusterApplyOutcome(server, before, afterLabel, status, error));
      writeAudit(txId, task, server, before, afterLabel, status, reviewOutcome, user);
   }

   private static String overallStatus(List<ClusterApplyOutcome> results) {
      boolean anyVerified = false;
      boolean anyFailed = false;

      for(ClusterApplyOutcome outcome : results) {
         if(AdminChangeRecord.STATUS_VERIFIED.equals(outcome.status())) {
            anyVerified = true;
         }
         else {
            anyFailed = true;
         }
      }

      if(anyFailed && anyVerified) {
         return STATUS_PARTIAL;
      }

      return anyFailed ? STATUS_FAILED : STATUS_APPLIED;
   }

   private void writeAudit(String txId, String task, String server, String before, String after,
                           String status, String reviewOutcome, Principal user)
   {
      try {
         AdminChangeRecord record = new AdminChangeRecord();
         record.setTransactionId(txId);
         record.setTaskDescription(task);
         record.setProperty(server);
         record.setObjectType(ActionRecord.OBJECT_TYPE_CLUSTER);
         record.setBeforeValue(before);
         record.setAfterValue(after);
         // Always ACTION_APPLY, never ACTION_ROLLBACK -- this area never writes a rollback record
         // (01-spec.md section 8, worth stating explicitly since every prior area's audit trail uses
         // both).
         record.setAction(AdminChangeRecord.ACTION_APPLY);
         record.setStatus(status);
         record.setRiskLevel(AdminChangeRecord.RISK_HIGH);
         record.setSnapshotScope(AdminChangeRecord.SCOPE_VALUE);
         // backupRef always null -- no verb in this area requires a Tier-2 snapshot (section 7).
         record.setBackupRef(null);
         record.setReviewOutcome(reviewOutcome);
         // organizationId deliberately left unset -- cluster nodes are whole-deployment, not
         // org-scoped (section 1/org-boundary section), mirrors ClusterChangePlanService
         // .NOT_ORG_SCOPED.
         record.setUserName(user == null ? null : user.getName());
         record.setActionTimestamp(new Timestamp(System.currentTimeMillis()));
         record.setServerHostName(Tool.getHost());
         Audit.getInstance().auditAdminChange(record, user);
      }
      catch(Exception auditFailure) {
         // An audit write must never replace the real outcome -- same rule every prior area's apply
         // service follows.
         LOG.error("Failed to write cluster admin change audit record for transaction {}", txId,
                   auditFailure);
      }
   }

   private static String messageOf(Exception e) {
      return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
   }

   private static String newIdSuffix() {
      return String.format("%016x", RANDOM.nextLong());
   }

   private static final Logger LOG = LoggerFactory.getLogger(ClusterChangesetApplyService.class);
   private static final SecureRandom RANDOM = new SecureRandom();
   /** Serializes the entire body of {@link #apply} -- same rationale and same JVM-local-only
    * limitation as every prior area's own lock. */
   private static final ReentrantLock APPLY_LOCK = new ReentrantLock();
   private final ClusterChangePlanService planService;
   private final ClusterService clusterService;
   private final ServerClusterClient client;
}
