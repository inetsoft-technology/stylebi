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
package inetsoft.web.admin.ai.schedulerstatus;

import inetsoft.util.Tool;
import inetsoft.util.audit.*;
import inetsoft.web.admin.ai.AdminChangesetApplyService;
import inetsoft.web.admin.ai.PlanChange;
import inetsoft.web.admin.ai.ResolvedPlan;
import inetsoft.web.admin.ai.TaskAuditToken;
import inetsoft.web.admin.schedule.SchedulerConfigurationService;
import inetsoft.web.admin.schedule.model.ScheduleStatusModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Applies a scheduler start/stop/restart and audits the attempt -- the scheduler-status analog of
 * {@code ClusterChangesetApplyService}, but for a single entry, never a batch
 * (track-status/01-design.md section 5a).
 *
 * <p><b>No {@code "partial"} status is possible</b> (unlike Cluster, which introduces one for a
 * multi-server batch) -- with exactly one entry, the overall result collapses to
 * {@link AdminChangesetApplyService#STATUS_APPLIED} (verified) or {@link
 * AdminChangeRecord#STATUS_FAILED} (not verified). A plan-drift conflict is a thrown
 * {@link AdminChangesetApplyService.PlanHashMismatchException}, mapped to HTTP 409 by the
 * controller, never a value of this field. There is no {@code "rolled-back"}/
 * {@code "rollback-failed"} status either -- this area has no rollback: "undo", to the extent it
 * has one at all, is calling this same tool again with the complementary verb (stop undoes start
 * and vice versa; restart's own undo is simply restart again, or stop if the intent was "actually
 * turn it off").
 *
 * <p><b>Read-back verification</b> mirrors {@code ClusterChangesetApplyService.readBackWithRetry}:
 * after {@code setStatus} returns, a fresh {@code getStatus()} read (with a short bounded retry)
 * decides whether the live {@code running} flag now matches the plan's proposed state. Whether
 * {@code ScheduleClient.isReady()} has an analogous debounce/propagation delay to
 * {@code ServerClusterClient}'s own ~50ms status-map write was an open question at design time
 * (track-status/01-design.md section 6 point 2) -- this defaults to including the same bounded
 * retry as Cluster's own defensive posture (cheap insurance) rather than assuming a bare single
 * read is reliable, pending live confirmation.
 */
@Component
public class SchedulerStatusChangesetApplyService {
   public static final String STATUS_APPLIED = AdminChangesetApplyService.STATUS_APPLIED;
   public static final String STATUS_FAILED = AdminChangeRecord.STATUS_FAILED;

   @Autowired
   public SchedulerStatusChangesetApplyService(SchedulerStatusChangePlanService planService,
                                               SchedulerConfigurationService configService)
   {
      this.planService = planService;
      this.configService = configService;
   }

   /**
    * Re-resolves via {@link SchedulerStatusChangePlanService#resolve}, gates on the plan hash and
    * {@code reviewOutcome} (this area's {@code risk: high} is unconditional, so
    * {@code requiresAgentSignoff} is always {@code true}), then executes the one entry.
    *
    * @throws AdminChangesetApplyService.PlanHashMismatchException if the hash is missing or stale
    *         (maps to HTTP 409), reused verbatim.
    */
   public SchedulerStatusApplyResult apply(SchedulerStatusApplyRequest req, Principal user) {
      APPLY_LOCK.lock();

      try {
         ResolvedPlan plan = planService.resolve(req);

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

         if(req.getReviewOutcome() == null || req.getReviewOutcome().trim().isEmpty()) {
            throw new IllegalArgumentException(
               "reviewOutcome: required -- risk: high is unconditional for this area");
         }

         PlanChange change = plan.changes().get(0);
         String verb = SchedulerStatusChangePlanService.requireVerb(
            "apply", req.getChanges().get(0).getVerb());
         String txId = "scheduler-status-" + newIdSuffix();
         String before = change.currentValue();
         String afterLabel = null;
         String outcomeStatus;
         String error = null;
         boolean verified = false;

         try {
            configService.setStatus(verb);
            ScheduleStatusModel after = readBackWithRetry(change.proposedValue());
            afterLabel = statusLabel(after);
            verified = afterLabel.equals(change.proposedValue());
            outcomeStatus = verified ? AdminChangeRecord.STATUS_VERIFIED : STATUS_FAILED;

            if(!verified) {
               error = "read-back status \"" + afterLabel + "\" does not match the proposed state " +
                  "\"" + change.proposedValue() + "\" after retrying past the possible status " +
                  "propagation delay -- for restart this can mean the server-side 30s stop-wait " +
                  "timed out, leaving the scheduler stopped but NOT restarted; for start/stop it may " +
                  "mean the underlying call did not complete as expected";
            }
         }
         catch(IllegalStateException e) {
            // SchedulerConfigurationService.setStatus("restart")'s own documented partial-failure
            // path: it stopped the scheduler, waited up to 30s, and gave up without ever calling
            // startScheduler(). This must be surfaced plainly, not swallowed into a generic
            // "restart failed" (track-status/01-design.md section 3a).
            outcomeStatus = STATUS_FAILED;
            error = "restart failed: " + e.getMessage() + " -- the scheduler has been stopped but " +
               "was NOT restarted; it is not running. Check status and consider a plain \"start\" " +
               "instead of retrying restart immediately.";
         }
         catch(Exception e) {
            outcomeStatus = STATUS_FAILED;
            error = messageOf(e);
         }

         SchedulerStatusApplyOutcome outcome =
            new SchedulerStatusApplyOutcome(verb, before, afterLabel, outcomeStatus, error);
         writeAudit(txId, reviewedTask, verb, before, afterLabel, outcomeStatus, req.getReviewOutcome(),
                   user);

         String overallStatus = verified ? STATUS_APPLIED : STATUS_FAILED;
         return new SchedulerStatusApplyResult(txId, overallStatus, null,
            Collections.singletonList(outcome));
      }
      finally {
         APPLY_LOCK.unlock();
      }
   }

   /** Bounded retry past a possible status-propagation delay, mirroring
    * {@code ClusterChangesetApplyService.readBackWithRetry}'s own defensive posture (see this
    * class's own javadoc for why this is a default, not a confirmed necessity). */
   private ScheduleStatusModel readBackWithRetry(String proposedLabel) {
      ScheduleStatusModel status = configService.getStatus();

      for(int attempt = 1;
          attempt < READBACK_MAX_ATTEMPTS && !statusLabel(status).equals(proposedLabel);
          attempt++)
      {
         sleepPastPropagationWindow();
         status = configService.getStatus();
      }

      return status;
   }

   private static String statusLabel(ScheduleStatusModel status) {
      return status.running()
         ? SchedulerStatusChangePlanService.STATUS_RUNNING
         : SchedulerStatusChangePlanService.STATUS_STOPPED;
   }

   private static void sleepPastPropagationWindow() {
      try {
         Thread.sleep(READBACK_POLL_INTERVAL_MS);
      }
      catch(InterruptedException e) {
         Thread.currentThread().interrupt();
      }
   }

   private void writeAudit(String txId, String task, String verb, String before, String after,
                           String status, String reviewOutcome, Principal user)
   {
      try {
         AdminChangeRecord record = new AdminChangeRecord();
         record.setTransactionId(txId);
         record.setTaskDescription(task);
         record.setProperty(verb);
         record.setObjectType(OBJECT_TYPE_SCHEDULER_STATUS);
         record.setBeforeValue(before);
         record.setAfterValue(after);
         record.setAction(AdminChangeRecord.ACTION_APPLY);
         record.setStatus(status);
         record.setRiskLevel(AdminChangeRecord.RISK_HIGH);
         record.setSnapshotScope(AdminChangeRecord.SCOPE_VALUE);
         // backupRef always null -- no verb in this area requires a Tier-2 snapshot.
         record.setBackupRef(null);
         record.setReviewOutcome(reviewOutcome);
         // organizationId deliberately left unset -- the scheduler is whole-deployment, not
         // org-scoped, mirrors SchedulerStatusChangePlanService.NOT_ORG_SCOPED.
         record.setUserName(user == null ? null : user.getName());
         record.setActionTimestamp(new Timestamp(System.currentTimeMillis()));
         record.setServerHostName(Tool.getHost());
         Audit.getInstance().auditAdminChange(record, user);
      }
      catch(Exception auditFailure) {
         LOG.error("Failed to write scheduler status admin change audit record for transaction {}",
                   txId, auditFailure);
      }
   }

   private static String messageOf(Exception e) {
      return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
   }

   private static String newIdSuffix() {
      return String.format("%016x", RANDOM.nextLong());
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(SchedulerStatusChangesetApplyService.class);
   private static final String OBJECT_TYPE_SCHEDULER_STATUS = "scheduler-status";
   /** Mirrors {@code ClusterChangesetApplyService}'s own bounded retry: up to 4 reads, 60ms apart. */
   private static final int READBACK_MAX_ATTEMPTS = 4;
   private static final long READBACK_POLL_INTERVAL_MS = 60;
   private static final SecureRandom RANDOM = new SecureRandom();
   /** Serializes the entire body of {@link #apply} -- same rationale as every prior area's own
    * lock. */
   private static final ReentrantLock APPLY_LOCK = new ReentrantLock();
   private final SchedulerStatusChangePlanService planService;
   private final SchedulerConfigurationService configService;
}
