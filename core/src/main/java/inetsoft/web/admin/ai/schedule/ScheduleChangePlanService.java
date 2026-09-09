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

import inetsoft.web.api.schedule.*;
import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.util.audit.AdminChangeRecord;
import inetsoft.web.admin.ai.PlanChange;
import inetsoft.web.admin.ai.ResolvedPlan;
import inetsoft.web.admin.ai.TaskAuditToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.util.*;

/**
 * Resolves a requested list of schedule-task changes into a {@link ResolvedPlan} and hashes it --
 * the schedule-task analog of {@code inetsoft.web.admin.ai.AdminChangePlanService}, replicated
 * rather than shared per {@code 01-spec.md} §6 (carry-forward item 5: {@code
 * AdminChangesetApplyService}'s constructor is concretely wired to {@code AdminChangeService}/
 * {@code SreeEnv}, so a second area cannot reuse it without generalizing the engine on one data
 * point -- not done here, revisit once a second structural area exists).
 *
 * <p>Unlike its properties analog, {@link #resolve} takes a {@link Principal}: two of this area's
 * refusals (spec §4) are per-caller inverse-permission preflights that properties has no
 * equivalent of, because a property write's inverse (write the before-value back) never needs a
 * *different* permission than the write itself, while a schedule task's does (see the two checks
 * below).
 */
@Component
public class ScheduleChangePlanService {
   @Autowired
   public ScheduleChangePlanService(AdminScheduleGateway scheduleGateway,
                                    ScheduleManager scheduleManager)
   {
      this.scheduleGateway = scheduleGateway;
      this.scheduleManager = scheduleManager;
   }

   /**
    * Resolves and hashes a plan. Performs no mutation of schedule-task state, but DOES perform
    * live permission checks (the two preflights below), which is why -- unlike the properties
    * analog -- this takes a {@link Principal}.
    *
    * @throws IllegalArgumentException with a field-named message on a blank task, an empty change
    *                                 list, an unrecognized verb, an unsupported condition/action
    *                                 type, a create whose id already exists, a delete whose id does
    *                                 not exist, or either inverse-permission preflight failing.
    */
   public ResolvedPlan resolve(ScheduleChangePlanRequest req, Principal user) throws Exception {
      if(req == null || req.getTask() == null || req.getTask().trim().isEmpty()) {
         throw new IllegalArgumentException("task: a non-empty description is required");
      }

      if(req.getChanges() == null || req.getChanges().isEmpty()) {
         throw new IllegalArgumentException("changes: at least one change is required");
      }

      List<PlanChange> changes = new ArrayList<>();
      Set<String> seenTaskIds = new HashSet<>();
      int index = 0;

      for(ScheduleChangeRequest change : req.getChanges()) {
         String label = "changes[" + index++ + "]";
         changes.add(resolveOne(label, change, user, seenTaskIds));
      }

      // Both verbs are risk:high with snapshotScope:storage unconditionally (spec §4) -- there is
      // no "recognized" axis for a schedule task the way an uncatalogued property has one, and no
      // low-risk schedule verb in this area's first cut.
      String planHash = hash(changes);
      String task = req.getTask().trim();
      return new ResolvedPlan(task, Collections.unmodifiableList(changes),
                              true, true, planHash, TaskAuditToken.issue(planHash, task));
   }

   private PlanChange resolveOne(String label, ScheduleChangeRequest change, Principal user,
                                 Set<String> seenTaskIds)
      throws Exception
   {
      if(change == null) {
         throw new IllegalArgumentException(label + ": must not be null");
      }

      String verb = normalizeVerb(label, change.getVerb());

      if(ScheduleChangeRequest.VERB_CREATE.equals(verb)) {
         return resolveCreate(label, change, user, seenTaskIds);
      }

      return resolveDelete(label, change, user, seenTaskIds);
   }

   /** Accepts "create"/"delete" verbatim; the tool layer normalizes natural aliases before this
    * point (see {@code plugin/admin/src/tools/normalize.ts#normalizeVerb}), so anything reaching
    * here that is not exactly one of the two canonical verbs is a genuine caller error, not an
    * unrecognized alias -- fail loud rather than guess. */
   private static String normalizeVerb(String label, String verb) {
      if(ScheduleChangeRequest.VERB_CREATE.equals(verb) || ScheduleChangeRequest.VERB_DELETE.equals(verb)) {
         return verb;
      }

      throw new IllegalArgumentException(
         label + ".verb: must be \"" + ScheduleChangeRequest.VERB_CREATE + "\" or \"" +
         ScheduleChangeRequest.VERB_DELETE + "\", got " + String.valueOf(verb));
   }

   private PlanChange resolveCreate(String label, ScheduleChangeRequest change, Principal user,
                                    Set<String> seenTaskIds)
      throws Exception
   {
      CreateScheduleTaskRequest spec = change.getSpec();

      if(spec == null) {
         throw new IllegalArgumentException(label + ".spec: required for verb=create");
      }

      if(spec.getName() == null || spec.getName().isBlank()) {
         throw new IllegalArgumentException(label + ".spec.name: required");
      }

      if(spec.getOwner() == null) {
         throw new IllegalArgumentException(label + ".spec.owner: required");
      }

      requireSupportedConditions(label, spec.getConditions());
      requireSupportedActions(label, spec.getActions());

      String taskId = ScheduleManager.getTaskId(spec.getOwner().convertToKey(), spec.getName());
      requireUnseen(label, taskId, seenTaskIds);

      if(scheduleManager.getScheduleTask(taskId) != null) {
         throw new IllegalArgumentException(
            label + ": a task already exists with id \"" + taskId + "\"");
      }

      // Inverse-permission preflight (spec §4): a create's rollback is a delete, so refuse the
      // plan now if the caller could not actually perform that delete, rather than discovering it
      // mid-rollback.
      if(!scheduleGateway.hasDeletePermission(taskId, user)) {
         throw new IllegalArgumentException(
            label + ": you do not hold DELETE on \"" + taskId + "\", which this create's rollback " +
            "would require -- refusing to plan a change whose own undo you could not perform");
      }

      String proposed = projectSpec(spec);
      return new PlanChange(taskId, spec.getOwner().getOrgID(), null, proposed,
                            AdminChangeRecord.RISK_HIGH, AdminChangeRecord.SCOPE_STORAGE, true,
                            "create schedule task");
   }

   private PlanChange resolveDelete(String label, ScheduleChangeRequest change, Principal user,
                                    Set<String> seenTaskIds)
      throws Exception
   {
      String taskId = change.getTaskId();

      if(taskId == null || taskId.isBlank()) {
         throw new IllegalArgumentException(label + ".taskId: required for verb=delete");
      }

      if(change.getSpec() != null) {
         throw new IllegalArgumentException(
            label + ".spec: not used for verb=delete; remove it or use verb=create");
      }

      requireUnseen(label, taskId, seenTaskIds);

      inetsoft.sree.schedule.ScheduleTask existing = scheduleManager.getScheduleTask(taskId);

      if(existing == null) {
         throw new IllegalArgumentException(label + ".taskId: no task exists with id \"" + taskId + "\"");
      }

      // Inverse-permission preflight (spec §4): a delete's rollback is a re-create of the captured
      // task, which needs ADMIN over the owner (and executeAsID, if set) -- a strictly stronger
      // requirement than the DELETE this verb itself needs. Refuse now rather than mid-rollback.
      inetsoft.web.api.schedule.ScheduleTask.ExecuteAsID executeAsID =
         existing.getIdentity() == null ? null :
         new inetsoft.web.api.schedule.ScheduleTask.ExecuteAsID(
            inetsoft.web.api.schedule.ScheduleTask.ExecuteAsID.Type
               .values()[existing.getIdentity().getType()],
            existing.getIdentity().getIdentityID());

      if(!scheduleGateway.hasOwnerAdminPermission(existing.getOwner(), executeAsID, user)) {
         throw new IllegalArgumentException(
            label + ": you do not hold ADMIN over the owner of \"" + taskId + "\", which this " +
            "delete's rollback (re-creating the task) would require -- refusing to plan a change " +
            "whose own undo you could not perform");
      }

      String current = ScheduleXmlProjection.project(existing);
      return new PlanChange(taskId, existing.getOwner().getOrgID(), current, null,
                            AdminChangeRecord.RISK_HIGH, AdminChangeRecord.SCOPE_STORAGE, true,
                            "delete schedule task");
   }

   private static void requireUnseen(String label, String taskId, Set<String> seenTaskIds) {
      if(!seenTaskIds.add(taskId)) {
         throw new IllegalArgumentException(
            label + ": duplicate entry for task id \"" + taskId + "\"; list each task at most once");
      }
   }

   private static void requireSupportedConditions(String label, List<ScheduleCondition> conditions) {
      if(conditions == null || conditions.isEmpty()) {
         throw new IllegalArgumentException(label + ".spec.conditions: at least one is required");
      }

      for(int i = 0; i < conditions.size(); i++) {
         if(!(conditions.get(i) instanceof TimeCondition)) {
            throw new IllegalArgumentException(
               label + ".spec.conditions[" + i + "]: only time conditions are supported in this " +
               "area (got " + conditions.get(i).getClass().getSimpleName() + ")");
         }

         TimeCondition tc = (TimeCondition) conditions.get(i);

         if(tc.getType() == TimeCondition.Type.AT && tc.getDate() == null) {
            throw new IllegalArgumentException(
               label + ".spec.conditions[" + i + "].date: an ISO 8601 date is required for " +
               "\"AT\"-type conditions");
         }
      }
   }

   private static void requireSupportedActions(String label, List<ScheduleAction> actions) {
      if(actions == null) {
         return;
      }

      for(int i = 0; i < actions.size(); i++) {
         if(!(actions.get(i) instanceof ViewsheetAction)) {
            throw new IllegalArgumentException(
               label + ".spec.actions[" + i + "]: only viewsheet actions are supported in this " +
               "area (got " + actions.get(i).getClass().getSimpleName() + ")");
         }
      }
   }

   /**
    * A preview-only projection of a NOT-YET-CREATED task, built directly from the request rather
    * than by round-tripping through {@code ScheduleApiService}'s private converters (which cannot
    * be called without side effects). Deliberately narrower than {@link ScheduleXmlProjection}: it
    * covers the fields that identify what would be created (enough that two materially different
    * requests hash differently, closing the same collision class {@code SpikeHashProbe} found for
    * the read path), not full fidelity with every action/condition field {@code addScheduleTask}
    * itself understands. This is the bounded, documented duplication carry-forward item 3
    * anticipated -- not a second copy of any authorization logic.
    */
   static String projectSpec(CreateScheduleTaskRequest spec) {
      StringBuilder sb = new StringBuilder();
      sb.append("name=").append(spec.getName())
         .append(";owner=").append(spec.getOwner())
         .append(";enabled=").append(spec.isEnabled())
         .append(";deleteIfNotScheduledToRun=").append(spec.isDeleteIfNotScheduledToRun())
         .append(";startDate=").append(spec.getStartDate())
         .append(";endDate=").append(spec.getEndDate())
         .append(";description=").append(spec.getDescription())
         .append(";locale=").append(spec.getLocale())
         .append(";executeAsID=").append(spec.getExecuteAsID());

      for(ScheduleCondition c : spec.getConditions()) {
         TimeCondition tc = (TimeCondition) c;
         sb.append(";condition[type=").append(tc.getType())
            .append(",hour=").append(tc.getHour())
            .append(",minute=").append(tc.getMinute())
            .append(",second=").append(tc.getSecond())
            .append(",interval=").append(tc.getInterval())
            .append(",daysOfWeek=").append(Arrays.toString(tc.getDaysOfWeek()))
            .append(",dayOfMonth=").append(tc.getDayOfMonth())
            .append(",weekOfMonth=").append(tc.getWeekOfMonth())
            .append(",monthsOfYear=").append(Arrays.toString(tc.getMonthsOfYear()))
            .append(",weekdayOnly=").append(tc.isWeekdayOnly())
            .append(",date=").append(tc.getDate())
            .append(",timeRange=").append(tc.getTimeRange())
            .append(",timeZone=").append(tc.getTimeZone())
            .append(']');
      }

      for(ScheduleAction a : spec.getActions()) {
         ViewsheetAction va = (ViewsheetAction) a;
         sb.append(";action[viewsheet=").append(va.getViewsheet())
            .append(",bookmarkNames=").append(va.getBookmarkNames())
            .append(",emails=").append(va.getEmails())
            .append(",notifies=").append(va.getNotifies())
            .append(",format=").append(va.getFormat())
            .append(",subject=").append(va.getSubject())
            .append(",message=").append(va.getMessage())
            .append(']');
      }

      return sb.toString();
   }

   /**
    * SHA-256 over the canonical plan. Same field-order/control-character contract as {@code
    * AdminChangePlanService#hash} -- changing it invalidates every outstanding preview, which is
    * safe (apply is refused with 409) but forces re-review.
    *
    * <p>Deliberately excludes {@code task}: it is a free-text, audit-only label (see {@link
    * ScheduleChangesetApplyService}'s {@code writeAudit} calls, its only use post-resolve) with no
    * bearing on what is actually mutated or verified, and the caller is never required to replay it
    * byte-for-byte between preview and apply.
    */
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
         throw new IllegalStateException("SHA-256 is required to hash a schedule change plan", e);
      }
   }

   private static String canonical(String value) {
      return value == null ? NULL_MARKER : value;
   }

   private static final char SEP = (char) 0x1f;
   private static final String NULL_MARKER = String.valueOf((char) 0x01);
   private final AdminScheduleGateway scheduleGateway;
   private final ScheduleManager scheduleManager;
}
