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

import inetsoft.util.audit.AdminChangeRecord;
import inetsoft.web.admin.ai.PlanChange;
import inetsoft.web.admin.ai.ResolvedPlan;
import inetsoft.web.admin.ai.TaskAuditToken;
import inetsoft.web.admin.schedule.SchedulerConfigurationService;
import inetsoft.web.admin.schedule.model.ScheduleStatusModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.List;

/**
 * Resolves a requested scheduler start/stop/restart into a {@link ResolvedPlan} and hashes it --
 * the scheduler-status analog of {@code ClusterChangePlanService}, but for a single,
 * deployment-wide target ("the scheduler") rather than a batch of named servers
 * (track-status/01-design.md section 5a).
 *
 * <p><b>Refuses the whole plan when the deployment is clustered</b>
 * ({@link SchedulerConfigurationService#getStatus()}{@code .cluster()}):
 * {@code SchedulerConfigurationService#setStatus} has no way to target a specific cluster node --
 * it always acts on whichever JVM handles the request via {@code SUtil.stopScheduler()}/
 * {@code startScheduler()} -- and the real Enterprise Manager UI already hides the whole Start/
 * Stop/Restart button row when the deployment is clustered, for the identical reason (confirmed
 * design section 3a). This area is faithful to that product boundary rather than inventing new
 * per-node targeting capability.
 *
 * <p><b>{@code risk: high}/{@code snapshotScope: value} unconditionally</b>, the same two hardcoded
 * label constants {@code ClusterChangePlanService} uses -- there is no {@code AdminPropertyName} for
 * "the scheduler process" to classify against.
 *
 * <p><b>A plan may contain at most one entry.</b> Unlike Cluster, which batches independent
 * servers, this area addresses exactly one target, so "start then stop" in a single plan is
 * nonsensical -- two separate preview/apply round trips, not a batch.
 *
 * <p><b>Required disclosure (carried here, restated in the plugin/TypeScript tool descriptions):</b>
 * every verb has a real side effect beyond the state transition its name suggests --
 * {@code "start"} always performs an unconditional stop, then start, even when the scheduler is
 * already running (an existing product quirk, not something this area introduces); {@code "stop"}
 * also fires the product's own configured "scheduler down" notification email, the same as
 * clicking Stop in Enterprise Manager; {@code "restart"} stops the scheduler, blocks waiting up to
 * 30s for it to actually stop, and only then starts it again -- if that 30s window elapses first,
 * the scheduler is left STOPPED, not restarted, and {@code setStatus} throws rather than silently
 * calling start anyway. {@link SchedulerStatusChangesetApplyService} surfaces that partial-failure
 * state plainly rather than reporting a generic "restart failed".
 */
@Component
public class SchedulerStatusChangePlanService {
   @Autowired
   public SchedulerStatusChangePlanService(SchedulerConfigurationService configService) {
      this.configService = configService;
   }

   static final String STATUS_RUNNING = "Running";
   static final String STATUS_STOPPED = "Stopped";

   /**
    * Resolves and hashes a plan. Performs no mutation, but does perform a live
    * {@link SchedulerConfigurationService#getStatus()} read.
    *
    * @throws IllegalArgumentException with a field-named message on a blank task, an empty or
    *                                 multi-entry change list, an unrecognized verb, or a clustered
    *                                 deployment (start/stop/restart is refused entirely).
    */
   public ResolvedPlan resolve(SchedulerStatusChangePlanRequest req) {
      if(req == null || req.getTask() == null || req.getTask().trim().isEmpty()) {
         throw new IllegalArgumentException("task: a non-empty description is required");
      }

      if(req.getChanges() == null || req.getChanges().isEmpty()) {
         throw new IllegalArgumentException("changes: at least one change is required");
      }

      if(req.getChanges().size() > 1) {
         throw new IllegalArgumentException(
            "changes: this area addresses exactly one target -- \"the scheduler\" -- so a plan " +
            "may contain at most one entry; issue two separate preview/apply round trips for more " +
            "than one action (e.g. stop, then later start)");
      }

      SchedulerStatusChangeRequest change = req.getChanges().get(0);

      if(change == null) {
         throw new IllegalArgumentException("changes[0]: must not be null");
      }

      String verb = requireVerb("changes[0]", change.getVerb());
      ScheduleStatusModel status = configService.getStatus();

      if(status.cluster()) {
         throw new IllegalArgumentException(
            "start/stop/restart is refused on a clustered deployment -- setStatus has no way to " +
            "target a specific cluster node (it always acts on whichever JVM handles the request), " +
            "and the real Enterprise Manager UI already hides the whole Start/Stop/Restart button " +
            "row for the identical reason. This is a faithful product boundary, not a gap this area " +
            "closes.");
      }

      String currentLabel = status.running() ? STATUS_RUNNING : STATUS_STOPPED;
      String proposedLabel = SchedulerStatusChangeRequest.VERB_STOP.equals(verb)
         ? STATUS_STOPPED : STATUS_RUNNING;
      String description = describe(verb, currentLabel, proposedLabel);
      // property is the verb itself, not a fixed constant -- this is deliberate: the plan hash
      // below is computed over (property, currentValue, proposedValue, risk, snapshotScope), and
      // "start" from a stopped scheduler and "restart" from a stopped scheduler both propose
      // "Running" from a "Stopped" current state. Without the verb in the hash, preview("start")
      // and apply("restart") could collide on an identical hash, letting a reviewed "start" plan
      // be silently substituted with "restart" at apply time -- the audit-integrity gap this area
      // must not introduce (ClusterChangePlanService avoids the analogous case because its own
      // property is a server name, and no two of its verbs (pause/resume) ever produce the same
      // (currentValue, proposedValue) pair for the same server).
      PlanChange planChange = new PlanChange(verb, NOT_ORG_SCOPED, currentLabel, proposedLabel,
         AdminChangeRecord.RISK_HIGH, AdminChangeRecord.SCOPE_VALUE, true, description);
      List<PlanChange> changes = Collections.singletonList(planChange);
      String task = req.getTask().trim();
      String planHash = hash(changes);
      return new ResolvedPlan(task, changes, false, true, planHash, TaskAuditToken.issue(planHash, task));
   }

   private static String describe(String verb, String current, String proposed) {
      String base;

      switch(verb) {
      case SchedulerStatusChangeRequest.VERB_START:
         base = "start the scheduler -- performs an unconditional stop, then start, even if it is " +
            "already running";
         break;
      case SchedulerStatusChangeRequest.VERB_STOP:
         base = "stop the scheduler -- also fires the product's own configured \"scheduler down\" " +
            "notification email";
         break;
      default:
         base = "restart the scheduler -- stops it, waits up to 30s for it to actually stop, then " +
            "starts it; if the 30s window elapses first, the scheduler is left stopped, not " +
            "restarted";
         break;
      }

      return base + " (current: " + current + ", proposed: " + proposed + ")";
   }

   static String requireVerb(String label, String verb) {
      String trimmed = verb == null ? null : verb.trim();

      if(SchedulerStatusChangeRequest.VERB_START.equals(trimmed) ||
         SchedulerStatusChangeRequest.VERB_STOP.equals(trimmed) ||
         SchedulerStatusChangeRequest.VERB_RESTART.equals(trimmed))
      {
         return trimmed;
      }

      throw new IllegalArgumentException(
         label + ".verb: must be \"start\", \"stop\", or \"restart\", got " + String.valueOf(verb));
   }

   // ---------------------------------------------------------------- hash

   /** Same canonical shape and control-character convention every prior area's own {@code hash}
    * method uses (per entry: property, currentValue, proposedValue, risk, snapshotScope) -- see
    * {@link #resolve} for why {@code property} is the verb here, not a fixed constant. */
   private static String hash(List<PlanChange> changes) {
      StringBuilder canonical = new StringBuilder();

      for(PlanChange change : changes) {
         canonical.append(change.property()).append(SEP)
            .append(canonical(change.currentValue())).append(SEP)
            .append(canonical(change.proposedValue())).append(SEP)
            .append(change.risk()).append(SEP)
            .append(change.snapshotScope()).append(SEP);
      }

      try {
         byte[] digest = MessageDigest.getInstance("SHA-256")
            .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
         StringBuilder hex = new StringBuilder(digest.length * 2);

         for(byte b : digest) {
            hex.append(String.format("%02x", b));
         }

         return hex.toString();
      }
      catch(NoSuchAlgorithmException e) {
         throw new IllegalStateException("SHA-256 is required to hash a scheduler status plan", e);
      }
   }

   private static String canonical(String value) {
      return value == null ? NULL_MARKER : value;
   }

   private static final char SEP = (char) 0x1f;
   private static final String NULL_MARKER = String.valueOf((char) 0x01);
   /** The scheduler is whole-deployment, not org-scoped -- mirrors
    * {@code ClusterChangePlanService.NOT_ORG_SCOPED}. */
   private static final String NOT_ORG_SCOPED = null;
   private final SchedulerConfigurationService configService;
}
