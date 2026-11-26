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
import inetsoft.report.internal.Util;
import inetsoft.sree.schedule.FTPUtil;
import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.schedule.ScheduleTaskMetaData;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.util.MessageException;
import inetsoft.util.audit.AdminChangeRecord;
import inetsoft.web.admin.ai.PlanChange;
import inetsoft.web.admin.ai.ResolvedPlan;
import inetsoft.web.admin.ai.StorageBackupPolicy;
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
      Set<String> compensableChanges = new HashSet<>();
      int index = 0;

      for(ScheduleChangeRequest change : req.getChanges()) {
         String label = "changes[" + index++ + "]";
         changes.add(resolveOne(label, change, user, seenTaskIds, compensableChanges));
      }

      // Every verb is risk:high (spec §4), so signoff is always required. The storage snapshot is
      // skipped only when every change is a task create (a create refuses an existing task id and
      // its undo deletes the new task, Bug #80650), a delete whose rollback provably re-creates
      // the task (Bug #81073, see rollbackProvablyRecreates), or an update whose rollback provably
      // re-saves the captured pre-image (same check).
      String planHash = hash(changes);
      String task = req.getTask().trim();
      boolean requiresBackup = StorageBackupPolicy.requiresBackup(
         req.getChanges(),
         c -> ScheduleChangeRequest.VERB_CREATE.equals(normalizeVerb("", c.getVerb())) ||
            compensableChanges.contains(c.getTaskId()));
      return new ResolvedPlan(task, Collections.unmodifiableList(changes),
                              requiresBackup, true, planHash, TaskAuditToken.issue(planHash, task));
   }

   private PlanChange resolveOne(String label, ScheduleChangeRequest change, Principal user,
                                 Set<String> seenTaskIds, Set<String> compensableChanges)
      throws Exception
   {
      if(change == null) {
         throw new IllegalArgumentException(label + ": must not be null");
      }

      String verb = normalizeVerb(label, change.getVerb());

      if(ScheduleChangeRequest.VERB_CREATE.equals(verb)) {
         return resolveCreate(label, change, user, seenTaskIds);
      }

      if(ScheduleChangeRequest.VERB_UPDATE.equals(verb)) {
         return resolveUpdate(label, change, user, seenTaskIds, compensableChanges);
      }

      return resolveDelete(label, change, user, seenTaskIds, compensableChanges);
   }

   /** Accepts "create"/"update"/"delete" verbatim; the tool layer normalizes natural aliases before this
    * point (see {@code plugin/admin/src/tools/normalize.ts#normalizeVerb}), so anything reaching
    * here that is not exactly one of the two canonical verbs is a genuine caller error, not an
    * unrecognized alias -- fail loud rather than guess. */
   private static String normalizeVerb(String label, String verb) {
      if(ScheduleChangeRequest.VERB_CREATE.equals(verb) ||
         ScheduleChangeRequest.VERB_UPDATE.equals(verb) ||
         ScheduleChangeRequest.VERB_DELETE.equals(verb))
      {
         return verb;
      }

      throw new IllegalArgumentException(
         label + ".verb: must be \"" + ScheduleChangeRequest.VERB_CREATE + "\", \"" +
         ScheduleChangeRequest.VERB_UPDATE + "\" or \"" + ScheduleChangeRequest.VERB_DELETE +
         "\", got " + String.valueOf(verb));
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

      // Bug #79828, apply refuses an owner that isn't an existing user, so refuse it here instead
      // of planning a change apply can only throw on, and before the owner is made into a taskId.
      try {
         scheduleGateway.requireOwnerResolves(spec.getOwner(), user);
      }
      catch(MessageException e) {
         throw new IllegalArgumentException(label + ".spec.owner: " + e.getMessage());
      }

      requireSupportedConditions(label + ".spec", spec.getConditions());
      requireNoPermissionResets(label, spec.getConditions(), user);
      requireSupportedActions(label + ".spec", spec.getActions());
      requireExistingViewsheets(label + ".spec", spec.getActions());

      String taskId = ScheduleManager.getTaskId(spec.getOwner().convertToKey(), spec.getName());
      requireUnseen(label, taskId, seenTaskIds);
      requireCompletionTargets(label + ".spec", spec.getConditions(), taskId);

      // the folder is resolved for its refusals only, the apply resolves it again
      try {
         scheduleGateway.resolveCreateFolder(change.getPath(), user);
      }
      catch(IllegalArgumentException e) {
         throw new IllegalArgumentException(label + "." + e.getMessage());
      }

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

      // Bug #79426, an execute-as identity that doesn't resolve is refused by apply, so refuse it
      // here instead of planning a change apply can only throw on.
      try {
         scheduleGateway.requireExecuteAsResolves(spec.getOwner(), spec.getExecuteAsID());
      }
      catch(MessageException e) {
         throw new IllegalArgumentException(label + ".spec.executeAsID: " + e.getMessage());
      }

      // Bug #79974, the save refuses a task whose run principal can't read an action viewsheet (and
      // the other refusals it makes), so refuse it here instead of planning a change apply can
      // only throw on.
      try {
         scheduleGateway.checkAddScheduleTaskSavable(
            new ScheduleTaskMetaData(spec.getName(), spec.getOwner().convertToKey()),
            spec.isEnabled(), spec.isDeleteIfNotScheduledToRun(), spec.getStartDate(),
            spec.getEndDate(), spec.getDescription(), spec.getLocale(), spec.getExecuteAsID(),
            spec.getConditions(), spec.getActions(), null, user);
      }
      catch(Exception e) {
         if(e instanceof IllegalArgumentException) {
            throw e;
         }

         throw new IllegalArgumentException(
            label + ".spec: the task would be refused when saved (owner \"" +
            spec.getOwner().getName() + "\"" +
            (spec.getExecuteAsID() == null ? "" : ", runs as \"" +
               spec.getExecuteAsID().getIdentityID().getName() + "\"") +
            ", action viewsheets " + viewsheetsOf(spec.getActions()) + "): " + e.getMessage());
      }

      String proposed = projectSpec(spec);

      if(!AdminScheduleFolderGateway.isRootPath(change.getPath())) {
         proposed += ";path=" + AdminScheduleFolderGateway.normalizePath(change.getPath());
      }

      return new PlanChange(taskId, spec.getOwner().getOrgID(), null, proposed,
                            AdminChangeRecord.RISK_HIGH, AdminChangeRecord.SCOPE_STORAGE, true,
                            "create schedule task");
   }

   private static List<String> viewsheetsOf(List<ScheduleAction> actions) {
      List<String> sheets = new java.util.ArrayList<>();

      if(actions != null) {
         for(ScheduleAction action : actions) {
            if(action instanceof ViewsheetAction) {
               sheets.add(((ViewsheetAction) action).getViewsheet());
            }
         }
      }

      return sheets;
   }

   /**
    * Resolves an update: the stored task with the patch applied, built by the same gateway code
    * the apply stores, so the plan shows the whole task as it would be and the same refusals
    * apply. A patch that changes nothing is refused rather than planned as a no-op.
    */
   private PlanChange resolveUpdate(String label, ScheduleChangeRequest change, Principal user,
                                    Set<String> seenTaskIds, Set<String> compensableChanges)
      throws Exception
   {
      String taskId = change.getTaskId();

      if(taskId == null || taskId.isBlank()) {
         throw new IllegalArgumentException(label + ".taskId: required for verb=update");
      }

      if(change.getSpec() != null) {
         throw new IllegalArgumentException(
            label + ".spec: not used for verb=update; send the fields to change as patch");
      }

      if(change.getPath() != null) {
         throw new IllegalArgumentException(
            label + ".path: not used for verb=update; move a task with the schedule task folder " +
            "area's moveTask");
      }

      ScheduleTaskPatch patch = change.getPatch();

      if(patch == null || patch.getDescription() == null && patch.getLocale() == null &&
         patch.getEnabled() == null && patch.getDeleteIfNotScheduledToRun() == null &&
         patch.getStartDate() == null && patch.getEndDate() == null &&
         patch.getConditions() == null && patch.getActions() == null)
      {
         throw new IllegalArgumentException(
            label + ".patch: at least one field to change is required for verb=update");
      }

      requireUnseen(label, taskId, seenTaskIds);
      inetsoft.sree.schedule.ScheduleTask existing = scheduleManager.getScheduleTask(taskId);

      if(existing == null) {
         throw new IllegalArgumentException(label + ".taskId: no task exists with id \"" + taskId + "\"");
      }

      if(!existing.isRemovable()) {
         throw new IllegalArgumentException(
            label + ".taskId: \"" + taskId + "\" is a built-in/system task and cannot be " +
            "updated here");
      }

      if(patch.getConditions() != null) {
         requireSupportedConditions(label + ".patch", patch.getConditions());
         requireNoPermissionResets(label, patch.getConditions(), user, ".patch");
         requireCompletionTargets(label + ".patch", patch.getConditions(), taskId);
      }

      if(patch.getActions() != null) {
         requireSupportedActions(label + ".patch", patch.getActions());
         requireExistingViewsheets(label + ".patch", patch.getActions());
      }

      inetsoft.sree.schedule.ScheduleTask candidate;

      try {
         candidate = scheduleGateway.buildUpdatedTask(taskId, patch, null, user);
         scheduleManager.checkScheduleTaskSave(taskId, candidate, user);
      }
      catch(Exception e) {
         if(e instanceof IllegalArgumentException) {
            throw e;
         }

         throw new IllegalArgumentException(
            label + ".patch: the update would be refused for \"" + taskId + "\": " +
            (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
      }

      // both as the store reads them back, so the plan shows only what the patch changes and the
      // apply can compare the stored task with it
      String current = ScheduleXmlProjection.projectAsStored(existing);
      String proposed = ScheduleXmlProjection.projectAsStored(candidate);

      if(current.equals(proposed)) {
         throw new IllegalArgumentException(
            label + ".patch: every field already has the requested value, nothing to update");
      }

      if(rollbackProvablyRecreates(taskId, existing, user)) {
         compensableChanges.add(taskId);
      }

      return new PlanChange(taskId, existing.getOwner().getOrgID(), current, proposed,
                            AdminChangeRecord.RISK_HIGH, AdminChangeRecord.SCOPE_STORAGE, true,
                            "update schedule task");
   }

   private PlanChange resolveDelete(String label, ScheduleChangeRequest change, Principal user,
                                    Set<String> seenTaskIds, Set<String> compensableDeletes)
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

      // Non-removable is a structural, permission-independent refusal -- the server marks a
      // built-in/internal task (e.g. the 3 bundled maintenance tasks) or a synthesized data-cycle
      // task as removable=false unconditionally, the same flag the EM task list's own Delete
      // button is disabled on (Bug #76598). No caller, including the task's own owner or a Site
      // Administrator, can delete it, so this must be checked and refused before -- and
      // independently of -- the owner-ADMIN permission check below, which answers a different
      // question ("do you have authority") than this one ("does this operation exist at all").
      if(!existing.isRemovable()) {
         throw new IllegalArgumentException(
            label + ".taskId: \"" + taskId + "\" is a built-in/system task and cannot be deleted " +
            "-- the server marks it non-removable for every caller regardless of permission, the " +
            "same restriction the Enterprise Manager task list enforces by disabling Delete for it");
      }

      // Enterprise Manager refuses to delete a task another task waits on (a completion
      // condition names it), which would leave that task never firing.
      if(existing.getOwner() != null) {
         Vector<inetsoft.sree.schedule.ScheduleTask> allTasks =
            scheduleManager.getScheduleTasks(existing.getOwner().getOrgID());

         if(allTasks != null && scheduleManager.hasDependency(allTasks, taskId)) {
            throw new IllegalArgumentException(
               label + ".taskId: another task has a completion condition that waits on \"" +
               taskId + "\"; update or delete that task first");
         }
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

      if(rollbackProvablyRecreates(taskId, existing, user)) {
         compensableDeletes.add(taskId);
      }

      String current = ScheduleXmlProjection.project(existing);
      return new PlanChange(taskId, existing.getOwner().getOrgID(), current, null,
                            AdminChangeRecord.RISK_HIGH, AdminChangeRecord.SCOPE_STORAGE, true,
                            "delete schedule task");
   }

   /**
    * Bug #81073, true when a delete's rollback (a re-save of the captured task, see {@code
    * ScheduleChangesetApplyService.applyDelete}) can be predicted to succeed, so the whole-storage
    * snapshot adds nothing the live undo does not already give. Both must hold:
    * <ul>
    *   <li>the task's owner is in the caller's current organization. The re-save's owner
    *   organization check re-reads the live task, which the delete has removed, so it cannot be
    *   predicted by a dry run taken while the task is still stored;</li>
    *   <li>a dry-run save of a deep copy is not refused (batch query organization, action
    *   organization boundary, action sheet READ, batch targets, scheduler permission), none of
    *   which depends on the live task.</li>
    * </ul>
    * Any doubt, including an exception while checking, keeps the snapshot; it never refuses the
    * plan.
    */
   private boolean rollbackProvablyRecreates(String taskId,
                                             inetsoft.sree.schedule.ScheduleTask existing,
                                             Principal user)
   {
      try {
         String ownerOrg = existing.getOwner() == null ? null : existing.getOwner().getOrgID();
         String callerOrg = OrganizationManager.getInstance().getCurrentOrgID(user);

         if(ownerOrg == null || callerOrg == null || !ownerOrg.equalsIgnoreCase(callerOrg)) {
            return false;
         }

         scheduleManager.checkScheduleTaskSave(
            taskId, ScheduleChangesetApplyService.deepCopy(existing), user);
         return true;
      }
      catch(Exception e) {
         return false;
      }
   }

   private static void requireUnseen(String label, String taskId, Set<String> seenTaskIds) {
      if(!seenTaskIds.add(taskId)) {
         throw new IllegalArgumentException(
            label + ": duplicate entry for task id \"" + taskId + "\"; list each task at most once");
      }
   }

   /** @param label the path of the object holding {@code conditions}, e.g. {@code changes[0].spec} */
   private static void requireSupportedConditions(String label, List<ScheduleCondition> conditions) {
      if(conditions == null || conditions.isEmpty()) {
         throw new IllegalArgumentException(label + ".conditions: at least one is required");
      }

      for(int i = 0; i < conditions.size(); i++) {
         String at = label + ".conditions[" + i + "]";

         if(conditions.get(i) instanceof CompletionCondition completion) {
            if(completion.getTaskName() == null || completion.getTaskName().isBlank()) {
               throw new IllegalArgumentException(at + ".taskName: required");
            }

            if(completion.getOwner() == null || completion.getOwner().isBlank()) {
               throw new IllegalArgumentException(
                  at + ".owner: required, the owner of the task to wait for");
            }

            continue;
         }

         if(!(conditions.get(i) instanceof TimeCondition)) {
            throw new IllegalArgumentException(
               at + ": only time and completion conditions are supported in this area (got " +
               (conditions.get(i) == null ? "null" : conditions.get(i).getClass().getSimpleName()) +
               ")");
         }

         TimeCondition tc = (TimeCondition) conditions.get(i);
         ScheduleConditionConverter.requireKnownTimeRange(at, tc.getTimeRange());
         ScheduleConditionConverter.requireKnownTimeZone(at, tc.getTimeZone());
         ScheduleConditionConverter.requireTimeOfDayInRange(at, tc);

         if(tc.getType() == TimeCondition.Type.AT && tc.getDate() == null) {
            throw new IllegalArgumentException(
               at + ".date: an ISO 8601 date is required for \"AT\"-type conditions");
         }
      }
   }

   /**
    * Refuses a completion condition whose target task does not exist, is the task itself, or
    * (through the tasks it waits on) waits on the task itself. Neither the schedule manager nor
    * Enterprise Manager's save checks any of this, a cycle would leave the tasks in it waiting
    * forever.
    *
    * @param selfTaskId the id of the task the conditions belong to.
    */
   private void requireCompletionTargets(String label, List<ScheduleCondition> conditions,
                                         String selfTaskId)
   {
      for(int i = 0; i < conditions.size(); i++) {
         if(!(conditions.get(i) instanceof CompletionCondition completion)) {
            continue;
         }

         String at = label + ".conditions[" + i + "]";
         String targetId = ScheduleManager.getTaskId(
            completion.getOwner(), completion.getTaskName(), null);

         if(targetId.equals(selfTaskId)) {
            throw new IllegalArgumentException(at + ": a task cannot wait on itself");
         }

         if(scheduleManager.getScheduleTask(targetId) == null) {
            throw new IllegalArgumentException(
               at + ": no task exists with id \"" + targetId + "\" to wait for");
         }

         if(waitsOn(targetId, selfTaskId, new HashSet<>())) {
            throw new IllegalArgumentException(
               at + ": \"" + targetId + "\" already waits on this task, directly or through " +
               "other tasks, so the two would wait for each other forever");
         }
      }
   }

   /** Whether the task {@code fromId} has a completion condition chain reaching {@code goalId}. */
   private boolean waitsOn(String fromId, String goalId, Set<String> visited) {
      if(!visited.add(fromId)) {
         return false;
      }

      inetsoft.sree.schedule.ScheduleTask from = scheduleManager.getScheduleTask(fromId);

      if(from == null) {
         return false;
      }

      Enumeration<String> dependencies = from.getDependency();

      while(dependencies != null && dependencies.hasMoreElements()) {
         String next = dependencies.nextElement();

         if(goalId.equals(next) || waitsOn(next, goalId, visited)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Refuses a condition whose value the apply would silently reset or drop. {@link
    * AdminScheduleGateway#addScheduleTask} runs the permission-based sanitize (the same rule
    * Enterprise Manager applies) after the conditions are built, so a time range or start time the
    * caller may not set is not stored; without this check the plan the reviewer signs would show
    * the value and the apply would still report verified.
    */
   private void requireNoPermissionResets(String label, List<ScheduleCondition> conditions,
                                          Principal user)
   {
      requireNoPermissionResets(label, conditions, user, ".spec");
   }

   private void requireNoPermissionResets(String label, List<ScheduleCondition> conditions,
                                          Principal user, String container)
   {
      List<String> resets = scheduleGateway.previewConditionResets(conditions, user);

      for(int i = 0; i < resets.size(); i++) {
         if(resets.get(i) != null) {
            throw new IllegalArgumentException(
               label + container + ".conditions[" + i + "]." + resets.get(i) +
               " -- remove it from the plan so the signed plan matches what is stored");
         }
      }
   }
   private static void requireSupportedActions(String label, List<ScheduleAction> actions) {
      if(actions == null) {
         return;
      }

      for(int i = 0; i < actions.size(); i++) {
         ScheduleAction action = actions.get(i);
         String at = label + ".actions[" + i + "]";

         if(action instanceof BatchAction batch) {
            if(batch.getTaskName() == null || batch.getTaskName().isBlank()) {
               throw new IllegalArgumentException(at + ".taskName: required");
            }

            if(batch.getOwner() == null || batch.getOwner().isBlank()) {
               throw new IllegalArgumentException(
                  at + ".owner: required, the owner of the task to run");
            }

            if(batch.getQueryEntry() == null || batch.getQueryEntry().isBlank()) {
               throw new IllegalArgumentException(at + ".queryEntry: required");
            }

            if(batch.getEmbeddedParameters() == null) {
               throw new IllegalArgumentException(
                  at + ".embeddedParameters: required, use an empty list for none");
            }
         }
         else if(action instanceof IndividualAssetBackupAction backup) {
            if(backup.getAssetIdentifiers() == null || backup.getAssetIdentifiers().isEmpty()) {
               throw new IllegalArgumentException(
                  at + ".assetIdentifiers: at least one asset is required");
            }

            if(false && (backup.getPathInfo() == null || backup.getPathInfo().getPath() == null) ||
               backup.getPathInfo().getPath().isBlank())
            {
               throw new IllegalArgumentException(
                  at + ".pathInfo.path: required, a backup action without a destination path " +
                  "cannot be stored or run");
            }
         }
         else if(!(action instanceof ViewsheetAction)) {
            throw new IllegalArgumentException(
               at + ": only viewsheet, batch and backup actions are supported in this area (got " +
               (action == null ? "null" : action.getClass().getSimpleName()) + ")");
         }
      }
   }

   /**
    * Refuses a viewsheet action whose sheet is provably absent (no asset at that path in that
    * organization), which the apply would otherwise store and the scheduler only fail on at run
    * time. {@link AdminScheduleGateway#viewsheetExists} fails open, so only a sure miss refuses.
    * A blank identifier is left to the checks that own it.
    */
   private void requireExistingViewsheets(String label, List<ScheduleAction> actions) {
      if(actions == null) {
         return;
      }

      for(int i = 0; i < actions.size(); i++) {
         if(!(actions.get(i) instanceof ViewsheetAction)) {
            continue;
         }

         String viewsheet = ((ViewsheetAction) actions.get(i)).getViewsheet();

         if(viewsheet != null && !viewsheet.isBlank() &&
            !scheduleGateway.viewsheetExists(viewsheet))
         {
            throw new IllegalArgumentException(
               label + ".actions[" + i + "].viewsheet: \"" + viewsheet + "\" does not " +
               "identify an existing viewsheet (no asset at that path in that organization)");
         }
      }
   }

   /**
    * Appends the condition to the plan text, printing only the fields the condition's type
    * persists ({@code TimeCondition.writeXML}) and the schedule reads, so a field the type ignores
    * is never shown to the reviewer (apply would drop it). Types not listed (the deprecated
    * DAY_OF_MONTH/WEEK_OF_MONTH, and any future type) keep printing every field.
    */
   private static void appendCondition(StringBuilder sb, TimeCondition tc) {
      TimeCondition.Type type = tc.getType();
      boolean atType = type == TimeCondition.Type.AT;
      sb.append(";condition[type=").append(type);

      if(atType) {
         sb.append(",date=").append(tc.getDate());
      }
      else {
         sb.append(",hour=").append(tc.getHour())
            .append(",minute=").append(tc.getMinute())
            .append(",second=").append(tc.getSecond());
      }

      if(type == TimeCondition.Type.EVERY_HOUR) {
         sb.append(",daysOfWeek=").append(Arrays.toString(tc.getDaysOfWeek()))
            .append(",hourEnd=").append(tc.getHourEnd())
            .append(",minuteEnd=").append(tc.getMinuteEnd())
            .append(",secondEnd=").append(tc.getSecondEnd())
            .append(",hourlyInterval=").append(tc.getHourlyInterval());
      }
      else if(type == TimeCondition.Type.EVERY_DAY) {
         sb.append(",interval=").append(tc.getInterval())
            .append(",weekdayOnly=").append(tc.isWeekdayOnly());
      }
      else if(type == TimeCondition.Type.EVERY_WEEK || type == TimeCondition.Type.DAY_OF_WEEK) {
         sb.append(",interval=").append(tc.getInterval())
            .append(",daysOfWeek=").append(Arrays.toString(tc.getDaysOfWeek()));
      }
      else if(type == TimeCondition.Type.EVERY_MONTH) {
         if(tc.getWeekOfMonth() > 0) {
            sb.append(",weekOfMonth=").append(tc.getWeekOfMonth())
               .append(",dayOfWeek=").append(tc.getDayOfWeek());
         }
         else {
            sb.append(",dayOfMonth=").append(tc.getDayOfMonth());
         }

         sb.append(",monthsOfYear=").append(Arrays.toString(tc.getMonthsOfYear()));
      }
      else if(!atType) {
         sb.append(",interval=").append(tc.getInterval())
            .append(",daysOfWeek=").append(Arrays.toString(tc.getDaysOfWeek()))
            .append(",dayOfMonth=").append(tc.getDayOfMonth())
            .append(",weekOfMonth=").append(tc.getWeekOfMonth())
            .append(",dayOfWeek=").append(tc.getDayOfWeek())
            .append(",monthsOfYear=").append(Arrays.toString(tc.getMonthsOfYear()))
            .append(",weekdayOnly=").append(tc.isWeekdayOnly())
            .append(",hourEnd=").append(tc.getHourEnd())
            .append(",minuteEnd=").append(tc.getMinuteEnd())
            .append(",secondEnd=").append(tc.getSecondEnd())
            .append(",hourlyInterval=").append(tc.getHourlyInterval());
      }

      sb.append(",timeRange=").append(tc.getTimeRange())
         .append(",timeZone=").append(tc.getTimeZone())
         .append(']');
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
         if(c instanceof CompletionCondition completion) {
            sb.append(";condition[type=COMPLETION,taskName=").append(completion.getTaskName())
               .append(",owner=").append(completion.getOwner()).append(']');
         }
         else {
            appendCondition(sb, (TimeCondition) c);
         }
      }

      for(ScheduleAction a : spec.getActions()) {
         if(a instanceof BatchAction batch) {
            appendBatchAction(sb, batch);
            continue;
         }

         if(a instanceof IndividualAssetBackupAction backup) {
            appendBackupAction(sb, backup);
            continue;
         }

         ViewsheetAction va = (ViewsheetAction) a;
         sb.append(";action[viewsheet=").append(va.getViewsheet())
            .append(",bookmarkNames=").append(va.getBookmarkNames())
            .append(",emails=").append(va.getEmails())
            .append(",notifies=").append(va.getNotifies())
            .append(",format=").append(va.getFormat())
            .append(",subject=").append(va.getSubject())
            .append(",message=").append(va.getMessage());
         appendActionDetails(sb, va);
         sb.append(']');
      }

      return sb.toString();
   }

   /** Batch parameter values are shown as short digests only, like a viewsheet action's. */
   private static void appendBatchAction(StringBuilder sb, BatchAction batch) {
      sb.append(";action[batch,taskName=").append(batch.getTaskName())
         .append(",owner=").append(batch.getOwner())
         .append(",queryEntry=").append(batch.getQueryEntry())
         .append(",queryParameters=").append(digestOf(batch.getQueryParameters()))
         .append(",embeddedParameters=").append(digestOf(batch.getEmbeddedParameters()))
         .append(']');
   }

   /** The server path's password is never rendered, only whether one is set. */
   private static void appendBackupAction(StringBuilder sb, IndividualAssetBackupAction backup) {
      sb.append(";action[backup,assets=").append(backup.getAssetIdentifiers())
         .append(",encoding=").append(backup.isEncoding());
      ServerPathInfo info = backup.getPathInfo();

      if(info != null && info.getPath() != null) {
         String path = info.getPath();
         boolean sftp = path.toLowerCase().startsWith("sftp://");
         FTPUtil.PathPassword split = FTPUtil.splitPassword(path, sftp);

         if(split != null) {
            path = split.path();
         }

         sb.append(",path=").append(path);

         if(info.isUseCredential()) {
            sb.append(",useCredential=true,secretId=").append(info.getSecretId());
         }
         else if(info.getUserName() != null) {
            sb.append(",username=").append(info.getUserName());
         }

         boolean fieldPassword = info.getPassword() != null && !info.getPassword().isEmpty() &&
            !Util.PLACEHOLDER_PASSWORD.equals(info.getPassword());

         if(split != null || !info.isUseCredential() && fieldPassword) {
            sb.append(",password=set");
         }
      }

      sb.append(']');
   }

   private static String digestOf(Object value) {
      return value == null ? "null" : "sha256:" + shortDigest(String.valueOf(value));
   }

   /**
    * Appends every other action field {@link AdminScheduleGateway} writes onto the stored action,
    * so the plan text and hash cover everything the create will persist. Nullable fields the
    * gateway skips when null are skipped here too. Secrets are never rendered: an FTP password is
    * omitted (one embedded in the path is stripped) and a parameter value is shown only as a
    * short digest.
    */
   private static void appendActionDetails(StringBuilder sb, ViewsheetAction va) {
      appendIfNotNull(sb, "sender", va.getSender());
      appendIfNotNull(sb, "ccAddresses", va.getCCAddresses());
      appendIfNotNull(sb, "bccAddresses", va.getBCCAddresses());
      appendIfNotNull(sb, "attachmentName", va.getAttachmentName());
      // the gateway uses the singular bookmark fields only when bookmarkNames is empty, and the
      // plural users/types only when it is not
      boolean hasNames = va.getBookmarkNames() != null && !va.getBookmarkNames().isEmpty();
      String singularIgnored = hasNames ? "(ignored:bookmarkNames)" : "";
      String pluralIgnored = hasNames ? "" : "(ignored:no bookmarkNames)";
      appendIfNotNull(sb, "bookmarkName", va.getBookmarkName(), singularIgnored);
      appendIfNotNull(sb, "bookmarkUser", va.getBookmarkUser(), singularIgnored);
      appendIfNotNull(sb, "bookmarkType", va.getBookmarkType(), singularIgnored);
      appendIfNotNull(sb, "bookmarkUsers", va.getBookmarkUsers(), pluralIgnored);
      appendIfNotNull(sb, "bookmarkTypes", va.getBookmarkTypes(), pluralIgnored);
      sb.append(",htmlMessage=").append(va.isHtmlMessage());
      appendIfNotNull(sb, "emailZip", va.isEmailZip());
      appendIfNotNull(sb, "emailLink", va.isEmailLink());
      appendIfNotNull(sb, "emailMatch", va.isEmailMatch());
      appendIfNotNull(sb, "emailExpandSelections", va.isEmailExpandSelections());
      appendIfNotNull(sb, "emailOnlyDataComponents", va.isEmailOnlyDataComponents());
      appendIfNotNull(sb, "exportAllTabbedTables", va.isExportAllTabbedTables());
      appendIfNotNull(sb, "emailCSVConfig", va.getEmailCSVConfig());

      // the gateway stores these two only when there is a notification recipient
      boolean hasNotifies = va.getNotifies() != null && !va.getNotifies().isEmpty();
      appendNotify(sb, "notifyIfFailed", va.isNotifyIfFailed(), hasNotifies);
      appendNotify(sb, "notifyLink", va.isNotifyLink(), hasNotifies);

      sb.append(",saveToServerMatch=").append(va.isSaveToServerMatch())
         .append(",saveToServerExpandSelections=").append(va.isSaveToServerExpandSelections())
         .append(",saveToServerOnlyDataComponents=").append(va.isSaveToServerOnlyDataComponents())
         .append(",saveExportAllTabbedTables=").append(va.isSaveExportAllTabbedTables());
      appendIfNotNull(sb, "saveToServerCsvConfig", va.getSaveToServerCsvConfig());

      Map<String, Object> parameters = va.getParameters();

      if(parameters != null && !parameters.isEmpty()) {
         sb.append(",parameters={");
         boolean first = true;

         for(Map.Entry<String, Object> e : new TreeMap<>(parameters).entrySet()) {
            sb.append(first ? "" : ",").append(e.getKey()).append("=sha256:")
               .append(shortDigest(String.valueOf(e.getValue())));
            first = false;
         }

         sb.append('}');
      }

      List<ScheduleAlert> alerts = va.getAlerts();

      if(alerts != null) {
         List<String> effective = new ArrayList<>();

         for(ScheduleAlert alert : alerts) {
            // the gateway ignores an alert that lacks either part
            if(alert != null && alert.getElementId() != null &&
               alert.getHighlightName() != null)
            {
               effective.add(alert.getElementId() + ":" + alert.getHighlightName());
            }
         }

         if(!effective.isEmpty()) {
            sb.append(",alerts=").append(effective);
         }
      }

      List<ViewsheetAction.SaveToServerFilePath> paths = va.getSaveToServerFilePaths();

      if(paths != null) {
         List<String> effective = new ArrayList<>();

         for(ViewsheetAction.SaveToServerFilePath p : paths) {
            // the gateway ignores an entry without a path or a format
            if(p != null && p.getPath() != null && p.getFormat() != null) {
               effective.add(projectFilePath(p));
            }
         }

         if(!effective.isEmpty()) {
            sb.append(",saveToServerFilePaths=").append(effective);
         }
      }
   }

   private static void appendIfNotNull(StringBuilder sb, String name, Object value) {
      appendIfNotNull(sb, name, value, "");
   }

   private static void appendIfNotNull(StringBuilder sb, String name, Object value,
                                       String annotation)
   {
      if(value != null) {
         sb.append(',').append(name).append('=').append(value).append(annotation);
      }
   }

   private static void appendNotify(StringBuilder sb, String name, Boolean value,
                                    boolean hasNotifies)
   {
      if(value != null) {
         sb.append(',').append(name).append('=').append(value);

         if(!hasNotifies) {
            sb.append("(ignored:no notifies)");
         }
      }
   }

   /**
    * Renders a save-to-server entry without its password: the password field is only reported as
    * set, and a password embedded in an FTP/SFTP path is stripped.
    */
   private static String projectFilePath(ViewsheetAction.SaveToServerFilePath p) {
      String path = p.getPath();
      boolean sftp = path.toLowerCase().startsWith("sftp://");
      FTPUtil.PathPassword split = FTPUtil.splitPassword(path, sftp);

      if(split != null) {
         path = split.path();
      }

      StringBuilder sb = new StringBuilder();
      sb.append("{format=").append(p.getFormat()).append(",path=").append(path);

      // the gateway applies the path last: a password in it wins over the username/password
      // fields and over a stored secret, and the secret is only used with useCredential
      String ignored = split != null ? "(ignored:path password)"
         : p.isUseCredential() ? "(ignored:useCredential)" : "";

      if(p.getUsername() != null) {
         sb.append(",username=").append(p.getUsername()).append(ignored);
      }

      if(p.isUseCredential()) {
         sb.append(",useCredential=true").append(split != null ? ignored : "");

         if(p.getSecretId() != null) {
            sb.append(",secretId=").append(p.getSecretId()).append(split != null ? ignored : "");
         }
      }

      // the placeholder the API returns for a stored password is dropped by the gateway
      boolean fieldPassword = p.getPassword() != null && !p.getPassword().isEmpty() &&
         !Util.PLACEHOLDER_PASSWORD.equals(p.getPassword());

      if(split != null) {
         sb.append(",password=set");
      }
      else if(fieldPassword) {
         sb.append(",password=set").append(ignored);
      }

      return sb.append('}').toString();
   }

   private static String shortDigest(String value) {
      try {
         byte[] digest = MessageDigest.getInstance("SHA-256")
            .digest(value.getBytes(StandardCharsets.UTF_8));
         StringBuilder hex = new StringBuilder();

         for(int i = 0; i < 4; i++) {
            hex.append(String.format("%02x", digest[i]));
         }

         return hex.toString();
      }
      catch(NoSuchAlgorithmException e) {
         throw new IllegalStateException("SHA-256 is required to hash a schedule change plan", e);
      }
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
