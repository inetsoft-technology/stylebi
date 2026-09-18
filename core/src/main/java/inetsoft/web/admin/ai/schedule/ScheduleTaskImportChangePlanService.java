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

import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.schedule.ScheduleTask;
import inetsoft.util.audit.AdminChangeRecord;
import inetsoft.web.admin.ai.PlanChange;
import inetsoft.web.admin.ai.ResolvedPlan;
import inetsoft.web.admin.ai.TaskAuditToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.*;

/**
 * Resolves a requested schedule-task import (a {@code stagingToken} plus which of its staged tasks
 * to actually write) into a {@link ResolvedPlan} and hashes it -- the import-side analog of
 * {@code RepositoryImportChangePlanService}. Like {@code ScheduleChangePlanService}'s own
 * {@code create}/{@code delete} verbs, this area's plan hash is a SHA-256 digest over its own
 * {@link PlanChange} list, not the staged task itself -- but here every entry's ENTIRE staged
 * {@code ScheduleTask} is folded into that hash via {@code ScheduleXmlProjection.project} (a total
 * {@code writeXML()} serialization, not a hand-picked field list), so the simpler, standard
 * {@link TaskAuditToken} this plugin's other areas already use is sufficient here: there is no
 * field the hash fails to project that a caller could swap out between preview and apply.
 */
@Component
public class ScheduleTaskImportChangePlanService {
   @Autowired
   public ScheduleTaskImportChangePlanService(ScheduleTaskTransferService transferService,
                                              ScheduleManager scheduleManager)
   {
      this.transferService = transferService;
      this.scheduleManager = scheduleManager;
   }

   /**
    * @throws IllegalArgumentException with a field-named message on a blank task, a blank
    *                                 stagingToken, an empty change list, a blank/duplicate taskId,
    *                                 or a taskId that collides with an existing task without
    *                                 {@code overwrite: true}.
    * @throws inetsoft.web.security.auth.MissingResourceException if {@code stagingToken} is
    *         unknown/expired, or a {@code taskId} does not name a task staged under it.
    */
   public ResolvedPlan resolve(ScheduleTaskImportPlanRequest req, Principal user) throws Exception {
      if(req == null || req.getTask() == null || req.getTask().trim().isEmpty()) {
         throw new IllegalArgumentException("task: a non-empty description is required");
      }

      String stagingToken = req.getStagingToken();

      if(stagingToken == null || stagingToken.trim().isEmpty()) {
         throw new IllegalArgumentException(
            "stagingToken: required -- the token returned by stage_schedule_task_import");
      }

      if(req.getChanges() == null || req.getChanges().isEmpty()) {
         throw new IllegalArgumentException("changes: at least one change is required");
      }

      List<PlanChange> changes = new ArrayList<>();
      Set<String> seenKeys = new HashSet<>();
      int index = 0;

      for(ScheduleTaskImportChangeRequest change : req.getChanges()) {
         String label = "changes[" + index++ + "]";
         changes.add(resolveOne(label, stagingToken, change, seenKeys));
      }

      String task = req.getTask().trim();
      boolean requiresAgentSignoff = changes.stream()
         .anyMatch(c -> AdminChangeRecord.RISK_HIGH.equals(c.risk()));
      String planHash = hash(changes);
      return new ResolvedPlan(task, Collections.unmodifiableList(changes), true,
                              requiresAgentSignoff, planHash, TaskAuditToken.issue(planHash, task));
   }

   private PlanChange resolveOne(String label, String stagingToken,
                                 ScheduleTaskImportChangeRequest change, Set<String> seenKeys)
      throws Exception
   {
      if(change == null) {
         throw new IllegalArgumentException(label + ": must not be null");
      }

      String taskId = requireNonBlank(label + ".taskId", change.getTaskId());

      if(!seenKeys.add(taskId)) {
         throw new IllegalArgumentException(
            label + ": duplicate entry for taskId \"" + taskId + "\"; list each entry at most once");
      }

      ScheduleTask staged = transferService.requireStagedTask(stagingToken, taskId);
      ScheduleTask existing = scheduleManager.getScheduleTask(taskId);
      boolean overwrite = Boolean.TRUE.equals(change.getOverwrite());

      if(existing != null && !overwrite) {
         throw new IllegalArgumentException(
            label + ".overwrite: a schedule task named \"" + taskId + "\" already exists on this " +
            "server -- set overwrite: true to replace it, or choose a different task");
      }

      String before = existing == null ? null : ScheduleXmlProjection.project(existing);
      String after = ScheduleXmlProjection.project(staged);
      String risk = existing == null ? AdminChangeRecord.RISK_LOW : AdminChangeRecord.RISK_HIGH;
      String description = existing == null ?
         "import schedule task \"" + taskId + "\" as a new task" :
         "import schedule task \"" + taskId + "\" (overwrite: true -- replaces the existing task's " +
            "conditions/actions entirely)";
      return new PlanChange("schedule-import:" + taskId, null, before, after, risk,
                            AdminChangeRecord.SCOPE_STORAGE, true, description);
   }

   private static String requireNonBlank(String label, String value) {
      String trimmed = value == null ? null : value.trim();

      if(trimmed == null || trimmed.isEmpty()) {
         throw new IllegalArgumentException(label + ": required");
      }

      return trimmed;
   }

   /** SHA-256 over the canonical plan -- same field-order/control-character contract as every
    * prior area's own {@code hash} method. Deliberately excludes {@code task} (see
    * {@code TaskAuditToken}'s own javadoc for why). */
   private static String hash(List<PlanChange> changes) {
      StringBuilder canonical = new StringBuilder();

      for(PlanChange change : changes) {
         canonical.append(change.property()).append(SEP)
            .append(canonicalOrMarker(change.currentValue())).append(SEP)
            .append(canonicalOrMarker(change.proposedValue())).append(SEP)
            .append(change.risk()).append(SEP)
            .append(change.snapshotScope()).append(SEP);
      }

      try {
         byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(canonical.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
         StringBuilder hex = new StringBuilder(digest.length * 2);

         for(byte b : digest) {
            hex.append(String.format("%02x", b));
         }

         return hex.toString();
      }
      catch(java.security.NoSuchAlgorithmException e) {
         throw new IllegalStateException(
            "SHA-256 is required to hash a schedule task import plan", e);
      }
   }

   private static String canonicalOrMarker(String value) {
      return value == null ? NULL_MARKER : value;
   }

   private static final char SEP = (char) 0x1f;
   private static final String NULL_MARKER = String.valueOf((char) 0x01);
   private final ScheduleTaskTransferService transferService;
   private final ScheduleManager scheduleManager;
}
