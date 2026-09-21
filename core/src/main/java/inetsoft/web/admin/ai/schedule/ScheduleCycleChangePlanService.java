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

import inetsoft.sree.internal.DataCycleManager;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.util.audit.AdminChangeRecord;
import inetsoft.web.admin.ai.PlanChange;
import inetsoft.web.admin.ai.ResolvedPlan;
import inetsoft.web.admin.ai.TaskAuditToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.util.*;

/**
 * Resolves a requested list of Scheduled Cycle changes into a {@link ResolvedPlan} and hashes it
 * (bug #76848, design §5.3) -- modeled directly on {@link ScheduleFolderChangePlanService}, the
 * closest existing precedent for a small, dedicated schedule sub-area changeset-plan service.
 *
 * <p>Every entry's {@code property} key is the cycle's CURRENT name (the proposed new name, for a
 * {@code create}, since there is no current name yet) -- the same "one entry per identity, never
 * an implicit batch" convention every other area's own plan service uses.
 *
 * <p>{@code risk}/{@code requiresAgentSignoff} are unconditionally {@code RISK_HIGH}/{@code true}
 * for every verb (decision D8) -- this area has no genuinely low-risk verb the way Viewsheets'
 * own alias/description-only {@code update} is; every verb here can change WHEN potentially many
 * materialized views refresh.
 */
@Component
public class ScheduleCycleChangePlanService {
   @Autowired
   public ScheduleCycleChangePlanService(AdminScheduleCycleGateway cycleGateway) {
      this.cycleGateway = cycleGateway;
   }

   /**
    * Resolves and hashes a plan. Performs no mutation of any cycle, but DOES read live cycle/MV
    * state (existence/collision/in-use checks below).
    *
    * @throws IllegalArgumentException with a field-named message on a blank task, an empty
    *                                 change list, an unrecognized verb, a field used on the wrong
    *                                 verb, a name collision, a missing cycle, an update with
    *                                 nothing to change, or a rename/delete of a cycle currently
    *                                 assigned to a materialized view.
    */
   public ResolvedPlan resolve(ScheduleCycleChangePlanRequest req, Principal user) throws Exception {
      if(req == null || req.getTask() == null || req.getTask().trim().isEmpty()) {
         throw new IllegalArgumentException("task: a non-empty description is required");
      }

      if(req.getChanges() == null || req.getChanges().isEmpty()) {
         throw new IllegalArgumentException("changes: at least one change is required");
      }

      List<PlanChange> changes = new ArrayList<>();
      Set<String> seenNames = new HashSet<>();
      int index = 0;

      for(ScheduleCycleChangeRequest change : req.getChanges()) {
         String label = "changes[" + index++ + "]";
         changes.add(resolveOne(label, change, user, seenNames));
      }

      String planHash = hash(changes);
      String task = req.getTask().trim();
      // requiresStorageBackup: unconditionally true, every verb here mutates the cycle store.
      // requiresAgentSignoff: unconditionally true (decision D8).
      return new ResolvedPlan(task, Collections.unmodifiableList(changes), true, true, planHash,
                              TaskAuditToken.issue(planHash, task));
   }

   private PlanChange resolveOne(String label, ScheduleCycleChangeRequest change, Principal user,
                                 Set<String> seenNames)
      throws Exception
   {
      if(change == null) {
         throw new IllegalArgumentException(label + ": must not be null");
      }

      String verb = normalizeVerb(label, change.verb());

      switch(verb) {
      case ScheduleCycleChangeRequest.VERB_CREATE:
         return resolveCreate(label, change, user, seenNames);
      case ScheduleCycleChangeRequest.VERB_UPDATE:
         return resolveUpdate(label, change, user, seenNames);
      default:
         return resolveDelete(label, change, user, seenNames);
      }
   }

   /** Accepts the three canonical verbs verbatim; the tool layer normalizes natural aliases
    * (e.g. {@code "add"}/{@code "remove"}) before this point, same convention every other area's
    * own plan service documents. */
   private static String normalizeVerb(String label, String verb) {
      if(ScheduleCycleChangeRequest.VERB_CREATE.equals(verb) ||
         ScheduleCycleChangeRequest.VERB_UPDATE.equals(verb) ||
         ScheduleCycleChangeRequest.VERB_DELETE.equals(verb))
      {
         return verb;
      }

      throw new IllegalArgumentException(
         label + ".verb: must be \"" + ScheduleCycleChangeRequest.VERB_CREATE + "\", \"" +
         ScheduleCycleChangeRequest.VERB_UPDATE + "\", or \"" +
         ScheduleCycleChangeRequest.VERB_DELETE + "\", got " + String.valueOf(verb));
   }

   private PlanChange resolveCreate(String label, ScheduleCycleChangeRequest change, Principal user,
                                    Set<String> seenNames)
      throws Exception
   {
      requireUnused(label, "name", change.name(), "create");

      if(change.spec() == null) {
         throw new IllegalArgumentException(label + ".spec: required for verb=create");
      }

      if(change.spec().name() == null || change.spec().name().isBlank()) {
         throw new IllegalArgumentException(label + ".spec.name: required for verb=create");
      }

      String name = change.spec().name().trim();

      if(change.spec().conditions() == null || change.spec().conditions().isEmpty()) {
         throw new IllegalArgumentException(label + ".spec.conditions: required for verb=create");
      }

      requireUnseen(label, name, seenNames);

      if(cycleGateway.cycleExists(name, user)) {
         throw new IllegalArgumentException(
            label + ".spec.name: a scheduled cycle named \"" + name + "\" already exists");
      }

      String orgId = OrganizationManager.getInstance().getCurrentOrgID(user);
      List<inetsoft.sree.schedule.ScheduleCondition> conditions =
         ScheduleConditionConverter.convertConditions(change.spec().conditions());

      DataCycleManager.DataCycleAsset proposed = new DataCycleManager.DataCycleAsset();
      proposed.setName(name);
      proposed.setOrgId(orgId);
      proposed.setEnabled(true);
      proposed.setConditions(conditions);
      proposed.setInfo(new DataCycleManager.CycleInfo(name, orgId));

      String proposedProjection = AdminScheduleCycleGateway.projectXml(proposed);
      return new PlanChange(name, orgId, null, proposedProjection, AdminChangeRecord.RISK_HIGH,
                            AdminChangeRecord.SCOPE_STORAGE, true,
                            "create scheduled cycle \"" + name + "\"");
   }

   private PlanChange resolveUpdate(String label, ScheduleCycleChangeRequest change, Principal user,
                                    Set<String> seenNames)
      throws Exception
   {
      String currentName = requireName(label, change, "update");
      requireUnseen(label, currentName, seenNames);

      String orgId = OrganizationManager.getInstance().getCurrentOrgID(user);

      if(!cycleGateway.cycleExists(currentName, user)) {
         throw new IllegalArgumentException(
            label + ".name: no scheduled cycle named \"" + currentName + "\" exists");
      }

      ScheduleCycleChangeRequest.ScheduleCycleSpec spec = change.spec();

      if(spec == null || (isBlank(spec.name()) && spec.conditions() == null)) {
         throw new IllegalArgumentException(
            label + ".spec: at least one of name or conditions is required for verb=update -- " +
            "nothing to change");
      }

      DataCycleManager.DataCycleAsset current = cycleGateway.currentAsset(currentName, orgId);
      String newName = isBlank(spec.name()) ? currentName : spec.name().trim();

      if(!newName.equals(currentName)) {
         if(cycleGateway.cycleExists(newName, user)) {
            throw new IllegalArgumentException(
               label + ".spec.name: a scheduled cycle named \"" + newName + "\" already exists");
         }

         // Mirrors ScheduleCycleService#editCycle's own real behavior exactly (design §5.3/§6):
         // rename-while-in-use is refused, naming the dependent MVs; a same-name (conditions-only)
         // update is NOT, even if the cycle is in use.
         List<String> dependentMvNames = cycleGateway.dependentMvNames(currentName);

         if(!dependentMvNames.isEmpty()) {
            throw new IllegalArgumentException(
               label + ": cannot rename scheduled cycle \"" + currentName + "\" -- it is " +
               "assigned to the following materialized view(s), which would be left " +
               "referencing a nonexistent cycle: " + String.join(", ", dependentMvNames));
         }
      }

      List<inetsoft.sree.schedule.ScheduleCondition> conditions = spec.conditions() != null
         ? ScheduleConditionConverter.convertConditions(spec.conditions())
         : current.getConditions();

      DataCycleManager.DataCycleAsset proposed = new DataCycleManager.DataCycleAsset();
      proposed.setName(newName);
      proposed.setOrgId(orgId);
      proposed.setEnabled(current.isEnabled());
      proposed.setConditions(conditions);
      proposed.setInfo(current.getInfo());

      String currentProjection = AdminScheduleCycleGateway.projectXml(current);
      String proposedProjection = AdminScheduleCycleGateway.projectXml(proposed);
      String description = newName.equals(currentName)
         ? "update scheduled cycle \"" + currentName + "\"'s conditions"
         : "update scheduled cycle \"" + currentName + "\", renaming it to \"" + newName + "\"";

      return new PlanChange(currentName, orgId, currentProjection, proposedProjection,
                            AdminChangeRecord.RISK_HIGH, AdminChangeRecord.SCOPE_STORAGE, true,
                            description);
   }

   private PlanChange resolveDelete(String label, ScheduleCycleChangeRequest change, Principal user,
                                    Set<String> seenNames)
      throws Exception
   {
      String name = requireName(label, change, "delete");
      requireUnseen(label, name, seenNames);

      String orgId = OrganizationManager.getInstance().getCurrentOrgID(user);

      if(!cycleGateway.cycleExists(name, user)) {
         throw new IllegalArgumentException(
            label + ".name: no scheduled cycle named \"" + name + "\" exists");
      }

      // Refused loud at PREVIEW time, no override (decision D7): the real primitive
      // (ScheduleCycleService#removeCycles) has no allowed-through path to mirror.
      List<String> dependentMvNames = cycleGateway.dependentMvNames(name);

      if(!dependentMvNames.isEmpty()) {
         throw new IllegalArgumentException(
            label + ": cannot delete scheduled cycle \"" + name + "\" -- it is assigned to the " +
            "following materialized view(s): " + String.join(", ", dependentMvNames));
      }

      DataCycleManager.DataCycleAsset current = cycleGateway.currentAsset(name, orgId);
      String currentProjection = AdminScheduleCycleGateway.projectXml(current);

      return new PlanChange(name, orgId, currentProjection, null, AdminChangeRecord.RISK_HIGH,
                            AdminChangeRecord.SCOPE_STORAGE, true,
                            "delete scheduled cycle \"" + name + "\"");
   }

   private static String requireName(String label, ScheduleCycleChangeRequest change, String verb) {
      if(change.name() == null || change.name().isBlank()) {
         throw new IllegalArgumentException(label + ".name: required for verb=" + verb);
      }

      return change.name().trim();
   }

   private static void requireUnused(String label, String field, String value, String verb) {
      if(value != null && !value.isBlank()) {
         throw new IllegalArgumentException(
            label + "." + field + ": not used for verb=" + verb + "; remove it");
      }
   }

   private static void requireUnseen(String label, String name, Set<String> seenNames) {
      if(!seenNames.add(name)) {
         throw new IllegalArgumentException(
            label + ": duplicate entry for scheduled cycle \"" + name + "\"; list each cycle at " +
            "most once");
      }
   }

   private static boolean isBlank(String value) {
      return value == null || value.isBlank();
   }

   /**
    * SHA-256 over the canonical plan. Same field-order/control-character contract as {@code
    * ScheduleFolderChangePlanService#hash}, copied verbatim (design §5.3) -- changing it
    * invalidates every outstanding preview, which is safe (apply is refused with 409) but forces
    * re-review.
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
         throw new IllegalStateException("SHA-256 is required to hash a schedule cycle change plan", e);
      }
   }

   private static String canonical(String value) {
      return value == null ? NULL_MARKER : value;
   }

   private static final char SEP = (char) 0x1f;
   private static final String NULL_MARKER = String.valueOf((char) 0x01);
   private final AdminScheduleCycleGateway cycleGateway;
}
