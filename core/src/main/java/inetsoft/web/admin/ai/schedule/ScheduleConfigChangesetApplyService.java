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

import inetsoft.util.Tool;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.AdminChangeRecord;
import inetsoft.util.audit.Audit;
import inetsoft.web.admin.ai.*;
import inetsoft.web.admin.schedule.model.ScheduleConfigurationModel;
import inetsoft.web.admin.schedule.model.ServerLocation;
import inetsoft.web.viewsheet.model.dialog.schedule.TimeRangeModel;
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
 * Applies a whole Server Location/Time Range changeset and audits every attempt -- the
 * schedule-config analog of {@code AdminChangesetApplyService}/{@code
 * ScheduleChangesetApplyService}, replicated rather than shared.
 *
 * <p>Structurally different from both of those: the underlying {@code
 * SchedulerConfigurationService#setConfiguration} write is WHOLE-MODEL, not per-property/per-task
 * -- there is only ever ONE write call for an entire batch of Server Location/Time Range changes,
 * because the two collection fields (and Track B's ~18 scalar fields) all live on one {@code
 * ScheduleConfigurationModel} with no per-field PATCH. So rollback here is modeled at the SAME
 * granularity as the write itself: on any verification failure, the ENTIRE full model captured
 * before this apply (not a per-entry undo) is written back and re-verified. This is a deliberate
 * simplification from the per-entry rollback loop {@code AdminChangesetApplyService}/{@code
 * ScheduleChangesetApplyService} use -- justified because, unlike N independent property writes or
 * N independent task create/delete calls, there is no way to write back "only" one entry here in
 * the first place; every write already touches the whole model.
 */
@Component
public class ScheduleConfigChangesetApplyService {
   @Autowired
   public ScheduleConfigChangesetApplyService(ScheduleConfigChangePlanService planService,
                                               AdminScheduleConfigGateway gateway)
   {
      this.planService = planService;
      this.gateway = gateway;
   }

   /**
    * Resolves, gates on the plan hash and task token, then executes.
    *
    * @throws AdminChangesetApplyService.PlanHashMismatchException if the hash is missing or stale
    *         (maps to HTTP 409) -- reused verbatim from the properties area.
    * @throws AdminChangesetApplyService.TaskTokenMismatchException if the taskToken is missing,
    *         malformed, or was issued for a different planHash (also maps to HTTP 409, also reused
    *         verbatim).
    */
   public ApplyResult apply(ScheduleConfigApplyRequest req, Principal user) throws Exception {
      APPLY_LOCK.lock();

      try {
         ScheduleConfigChangePlanService.Resolution resolution = planService.resolveFull(req, user);
         ResolvedPlan plan = resolution.plan();

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

         String txId = "schedcfg-" + newIdSuffix();
         Exception writeFailure = null;

         try {
            gateway.writeFullConfig(resolution.after(), user);
         }
         catch(Exception e) {
            writeFailure = e;
         }

         ScheduleConfigurationModel verified = null;
         Exception verifyFailure = null;

         if(writeFailure == null) {
            try {
               verified = gateway.getFullConfig(user);
            }
            catch(Exception e) {
               verifyFailure = e;
            }
         }

         List<ApplyOutcome> results = new ArrayList<>();
         boolean allVerified;

         if(writeFailure != null) {
            String message = "the write failed: " + messageOf(writeFailure);

            for(PlanChange change : plan.changes()) {
               results.add(new ApplyOutcome(change.property(), change.currentValue(), null,
                                            AdminChangeRecord.STATUS_FAILED, message));
            }

            allVerified = false;
         }
         else if(verifyFailure != null) {
            String message = "applied but could not verify: " + messageOf(verifyFailure);

            for(PlanChange change : plan.changes()) {
               results.add(new ApplyOutcome(change.property(), change.currentValue(), null,
                                            AdminChangeRecord.STATUS_FAILED, message));
            }

            allVerified = false;
         }
         else {
            allVerified = verifyEntries(plan.changes(), resolution.entries(), verified, results);
         }

         writeAudit(txId, reviewedTask, results, req.getReviewOutcome(), user);

         if(allVerified) {
            return new ApplyResult(txId, AdminChangesetApplyService.STATUS_APPLIED, null,
                                   Collections.unmodifiableList(results), null);
         }

         return rollback(txId, resolution, results, req.getReviewOutcome(), user);
      }
      finally {
         APPLY_LOCK.unlock();
      }
   }

   /**
    * Writes {@code resolution.before()} back in full (see this class's own javadoc for why a
    * whole-model rollback, rather than a per-entry undo, is the correct granularity here) and
    * re-verifies the touched entries are back to their original state.
    */
   private ApplyResult rollback(String txId, ScheduleConfigChangePlanService.Resolution resolution,
                                List<ApplyOutcome> results, String reviewOutcome, Principal user)
   {
      try {
         gateway.writeFullConfig(resolution.before(), user);
         ScheduleConfigurationModel afterRollback = gateway.getFullConfig(user);
         boolean rolledBack = verifyRolledBack(resolution, afterRollback);

         if(rolledBack) {
            return new ApplyResult(txId, AdminChangesetApplyService.STATUS_ROLLED_BACK, null,
                                   Collections.unmodifiableList(results), null);
         }

         List<RollbackFailure> failures = new ArrayList<>();

         for(PlanChange change : resolution.plan().changes()) {
            failures.add(new RollbackFailure(change.property(),
               "rollback write did not verify -- the server may be PARTIALLY CHANGED"));
         }

         LOG.error("Schedule-config changeset {} rollback did not verify", txId);
         return new ApplyResult(txId, AdminChangesetApplyService.STATUS_ROLLBACK_FAILED, null,
                                Collections.unmodifiableList(results),
                                Collections.unmodifiableList(failures));
      }
      catch(Exception e) {
         List<RollbackFailure> failures = new ArrayList<>();

         for(PlanChange change : resolution.plan().changes()) {
            failures.add(new RollbackFailure(change.property(), messageOf(e)));
         }

         LOG.error("Schedule-config changeset {} rollback threw", txId, e);
         return new ApplyResult(txId, AdminChangesetApplyService.STATUS_ROLLBACK_FAILED, null,
                                Collections.unmodifiableList(results),
                                Collections.unmodifiableList(failures));
      }
   }

   private static boolean verifyRolledBack(
      ScheduleConfigChangePlanService.Resolution resolution, ScheduleConfigurationModel afterRollback)
   {
      List<ServerLocation> expectedLocations = resolution.before().serverLocations();
      List<TimeRangeModel> expectedRanges = resolution.before().timeRanges();
      Set<String> expectedLocationProjections = new HashSet<>();

      for(ServerLocation l : expectedLocations) {
         expectedLocationProjections.add(ScheduleConfigChangePlanService.projectServerLocation(l));
      }

      Set<String> actualLocationProjections = new HashSet<>();

      for(ServerLocation l : afterRollback.serverLocations()) {
         actualLocationProjections.add(ScheduleConfigChangePlanService.projectServerLocation(l));
      }

      if(!expectedLocationProjections.equals(actualLocationProjections)) {
         return false;
      }

      Set<String> expectedRangeProjections = new HashSet<>();

      for(TimeRangeModel r : expectedRanges) {
         expectedRangeProjections.add(ScheduleConfigChangePlanService.projectTimeRange(r));
      }

      Set<String> actualRangeProjections = new HashSet<>();

      for(TimeRangeModel r : afterRollback.timeRanges()) {
         actualRangeProjections.add(ScheduleConfigChangePlanService.projectTimeRange(r));
      }

      return expectedRangeProjections.equals(actualRangeProjections);
   }

   /**
    * Verifies each entry against the freshly re-read model, appending one {@link ApplyOutcome} per
    * entry (index-aligned with {@code changes}/{@code entries}) and returning whether every entry
    * verified.
    */
   private static boolean verifyEntries(
      List<PlanChange> changes, List<ScheduleConfigChangePlanService.EntryInfo> entries,
      ScheduleConfigurationModel verified, List<ApplyOutcome> results)
   {
      boolean allOk = true;

      for(int i = 0; i < changes.size(); i++) {
         PlanChange change = changes.get(i);
         ScheduleConfigChangePlanService.EntryInfo entry = entries.get(i);
         boolean ok;

         if(ScheduleConfigChangeRequest.UNIT_SERVER_LOCATION.equals(entry.unitType())) {
            ok = verifyServerLocation(change, entry, verified.serverLocations());
         }
         else {
            ok = verifyTimeRange(change, entry, verified.timeRanges());
         }

         results.add(new ApplyOutcome(change.property(), change.currentValue(),
                                      change.proposedValue(),
                                      ok ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED,
                                      ok ? null : "the applied value did not verify after write"));
         allOk = allOk && ok;
      }

      return allOk;
   }

   private static boolean verifyServerLocation(
      PlanChange change, ScheduleConfigChangePlanService.EntryInfo entry, List<ServerLocation> locations)
   {
      if(ScheduleConfigChangeRequest.VERB_DELETE.equals(entry.verb())) {
         String normalized = ScheduleConfigChangePlanService.normalizePath(change.property());
         return locations.stream()
            .noneMatch(l -> ScheduleConfigChangePlanService.normalizePath(l.path()).equals(normalized));
      }

      String normalized = ScheduleConfigChangePlanService.normalizePath(entry.afterKey());
      return locations.stream()
         .filter(l -> ScheduleConfigChangePlanService.normalizePath(l.path()).equals(normalized))
         .anyMatch(l -> ScheduleConfigChangePlanService.projectServerLocation(l).equals(change.proposedValue()));
   }

   private static boolean verifyTimeRange(
      PlanChange change, ScheduleConfigChangePlanService.EntryInfo entry, List<TimeRangeModel> ranges)
   {
      if(ScheduleConfigChangeRequest.VERB_DELETE.equals(entry.verb())) {
         return ranges.stream().noneMatch(r -> r.name().equals(change.property()));
      }

      return ranges.stream()
         .filter(r -> r.name().equals(entry.afterKey()))
         .anyMatch(r -> ScheduleConfigChangePlanService.projectTimeRange(r).equals(change.proposedValue()));
   }

   private void writeAudit(String txId, String task, List<ApplyOutcome> results,
                           String reviewOutcome, Principal user)
   {
      for(ApplyOutcome outcome : results) {
         try {
            AdminChangeRecord record = new AdminChangeRecord();
            record.setTransactionId(txId);
            record.setTaskDescription(task);
            record.setProperty(outcome.property());
            record.setObjectType(ActionRecord.OBJECT_TYPE_EMPROPERTY);
            record.setBeforeValue(outcome.before());
            record.setAfterValue(outcome.after());
            record.setAction(AdminChangeRecord.ACTION_APPLY);
            record.setStatus(outcome.status());
            record.setRiskLevel(AdminChangeRecord.RISK_HIGH);
            record.setSnapshotScope(AdminChangeRecord.SCOPE_VALUE);
            record.setReviewOutcome(reviewOutcome);
            record.setUserName(user == null ? null : user.getName());
            record.setActionTimestamp(new Timestamp(System.currentTimeMillis()));
            record.setServerHostName(Tool.getHost());
            Audit.getInstance().auditAdminChange(record, user);
         }
         catch(Exception auditFailure) {
            LOG.error("Failed to write schedule-config admin change audit record for transaction {}",
                     txId, auditFailure);
         }
      }
   }

   private static String messageOf(Exception e) {
      return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
   }

   private static String newIdSuffix() {
      return String.format("%016x", RANDOM.nextLong());
   }

   private static final Logger LOG = LoggerFactory.getLogger(ScheduleConfigChangesetApplyService.class);
   private static final SecureRandom RANDOM = new SecureRandom();
   /** Same rationale as {@code AdminChangesetApplyService#APPLY_LOCK}: JVM-local only. */
   private static final ReentrantLock APPLY_LOCK = new ReentrantLock();
   private final ScheduleConfigChangePlanService planService;
   private final AdminScheduleConfigGateway gateway;
}
