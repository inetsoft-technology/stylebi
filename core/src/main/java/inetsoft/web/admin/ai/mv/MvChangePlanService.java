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
package inetsoft.web.admin.ai.mv;

import inetsoft.sree.security.OrganizationManager;
import inetsoft.util.audit.AdminChangeRecord;
import inetsoft.web.admin.ai.PlanChange;
import inetsoft.web.admin.ai.ResolvedPlan;
import inetsoft.web.admin.ai.TaskAuditToken;
import inetsoft.web.admin.content.repository.MVSupportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.util.*;

/**
 * Resolves a requested list of MV changes into a {@link ResolvedPlan} and hashes it -- the MV
 * analog of {@code inetsoft.web.admin.ai.AdminChangePlanService}, replicated per this area's own
 * existing precedent (schedule/data-source/licensing/presentation each have their own copy rather
 * than sharing the properties-only engine).
 *
 * <p>Each {@code changes[]} entry names one or more mv names; this expands ({@link #flatten}) to
 * one {@link PlanChange} per (entry, mvName) pair, in a fixed, deterministic order that {@link
 * MvChangesetApplyService} replays via the same {@link #flatten} call to line its own executable
 * units up 1:1 with {@code plan.changes()} by index -- no separate correspondence bookkeeping
 * needed between the two services.
 *
 * <p>{@code create} and {@code set_cycle} both target candidates produced by a prior {@code
 * analyze_mv} run: the analysis is re-read fresh here (not from any preview-time cache), so a
 * caller who takes longer than the analysis's 20-minute idle TTL to review a plan gets a loud,
 * named {@link AnalysisExpiredException} rather than a silent stale-candidate resolution. Because
 * {@code apply} always re-calls {@link #resolve} before mutating anything, this same check also
 * covers the apply-time re-validation for free.
 */
@Component
public class MvChangePlanService {
   @Autowired
   public MvChangePlanService(AdminMvGateway mvGateway) {
      this.mvGateway = mvGateway;
   }

   /**
    * @throws IllegalArgumentException with a field-named message on a blank task, an empty change
    *                                 list, an unrecognized verb, a duplicate mv name across
    *                                 entries, a missing/blank required field, an mvName not present
    *                                 in the named analysisId's candidate list (create/set_cycle), or
    *                                 an mvName that does not currently exist (delete).
    * @throws AnalysisExpiredException if a referenced analysisId is unknown or has expired.
    */
   public ResolvedPlan resolve(MvChangePlanRequest req, Principal user) throws Exception {
      if(req == null || req.getTask() == null || req.getTask().trim().isEmpty()) {
         throw new IllegalArgumentException("task: a non-empty description is required");
      }

      if(req.getChanges() == null || req.getChanges().isEmpty()) {
         throw new IllegalArgumentException("changes: at least one change is required");
      }

      String orgId = OrganizationManager.getInstance().getCurrentOrgID(user);
      List<FlatChange> flat = flatten(req.getChanges());
      List<PlanChange> changes = new ArrayList<>();
      boolean hasCreate = false;

      // Cache each referenced analysisId's candidate list once per resolve() call, rather than
      // once per mvName -- create/set_cycle entries naming several mv names from the same
      // analysisId must not re-fetch (and re-validate freshness of) the same cluster-map entry once
      // per name.
      Map<String, List<MVSupportService.MVStatus>> candidatesByAnalysisId = new HashMap<>();

      for(FlatChange fc : flat) {
         if(MvChangeRequest.VERB_CREATE.equals(fc.verb)) {
            changes.add(resolveCreate(fc, orgId, candidatesByAnalysisId));
            hasCreate = true;
         }
         else if(MvChangeRequest.VERB_SET_CYCLE.equals(fc.verb)) {
            changes.add(resolveSetCycle(fc, orgId, candidatesByAnalysisId));
         }
         else {
            changes.add(resolveDelete(fc, orgId));
         }
      }

      boolean requiresAgentSignoff = changes.stream()
         .anyMatch(c -> AdminChangeRecord.RISK_HIGH.equals(c.risk()));
      // Delete alone does not require a Tier-2 backup here: dispose() is itself the rollback
      // primitive create relies on, so gating it behind its own backup would be circular. A create
      // (which registers a durable MVDef row with no in-process undo other than dispose) does.
      String planHash = hash(changes);
      String task = req.getTask().trim();
      return new ResolvedPlan(task, Collections.unmodifiableList(changes), hasCreate,
                              requiresAgentSignoff, planHash, TaskAuditToken.issue(planHash, task));
   }

   private PlanChange resolveCreate(FlatChange fc, String orgId,
                                    Map<String, List<MVSupportService.MVStatus>> candidatesByAnalysisId)
   {
      requireCandidate(fc, candidatesByAnalysisId);
      boolean noData = fc.source.getNoData() == null || fc.source.getNoData();
      // Mirrors applyCreate's gating in MvChangesetApplyService so this preview string describes
      // the runInBackground value apply will actually use.
      boolean background = !noData &&
         (fc.source.getRunInBackground() == null || fc.source.getRunInBackground());
      String proposed = "cycle=" + fc.source.getCycle() + ";noData=" + noData +
         ";runInBackground=" + background;
      return new PlanChange(fc.mvName, orgId, null, proposed, AdminChangeRecord.RISK_HIGH,
                            AdminChangeRecord.SCOPE_STORAGE, true, "create materialized view");
   }

   private PlanChange resolveSetCycle(FlatChange fc, String orgId,
                                      Map<String, List<MVSupportService.MVStatus>> candidatesByAnalysisId)
   {
      MVSupportService.MVStatus status = requireCandidate(fc, candidatesByAnalysisId);
      String currentCycle = status.getDefinition().getCycle();
      return new PlanChange(fc.mvName, orgId, currentCycle, fc.source.getCycle(),
                            AdminChangeRecord.RISK_LOW, AdminChangeRecord.SCOPE_STORAGE, true,
                            "set mv data cycle");
   }

   private PlanChange resolveDelete(FlatChange fc, String orgId) {
      if(!mvGateway.existsInOrg(fc.mvName, orgId)) {
         throw new IllegalArgumentException(
            fc.label + ".mvNames: no materialized view named \"" + fc.mvName + "\" exists");
      }

      return new PlanChange(fc.mvName, orgId, "exists", null, AdminChangeRecord.RISK_HIGH,
                            AdminChangeRecord.SCOPE_STORAGE, true, "delete materialized view");
   }

   /** Fetches (caching per analysisId) the named analysis's candidate list and looks up {@code
    * fc.mvName} within it, translating both "analysis not valid" and "name not a candidate" into
    * loud, field-named refusals rather than a silent stale/foreign resolution. */
   private MVSupportService.MVStatus requireCandidate(
      FlatChange fc, Map<String, List<MVSupportService.MVStatus>> candidatesByAnalysisId)
   {
      if(fc.source.getAnalysisId() == null || fc.source.getAnalysisId().isBlank()) {
         throw new IllegalArgumentException(
            fc.label + ".analysisId: required for verb=" + fc.verb);
      }

      List<MVSupportService.MVStatus> candidates = candidatesByAnalysisId.computeIfAbsent(
         fc.source.getAnalysisId(), id -> {
            MVSupportService.AnalysisResult result = mvGateway.getAnalysisResult(id);

            try {
               return result.getStatus();
            }
            catch(IllegalStateException e) {
               throw new AnalysisExpiredException(id);
            }
         });

      for(MVSupportService.MVStatus status : candidates) {
         if(status.getDefinition().getName().equals(fc.mvName)) {
            return status;
         }
      }

      throw new IllegalArgumentException(
         fc.label + ".mvNames: \"" + fc.mvName + "\" is not a candidate produced by analysisId \"" +
         fc.source.getAnalysisId() + "\"");
   }

   /** Expands {@code changes[]} into one {@link FlatChange} per (entry, mvName) pair, validating
    * the verb, presence of {@code mvNames}, and mv-name uniqueness across the WHOLE changeset
    * (not just within one entry) along the way -- shared, in this fixed order, by both {@link
    * #resolve} and {@link MvChangesetApplyService#apply}. */
   static List<FlatChange> flatten(List<MvChangeRequest> changes) {
      List<FlatChange> flat = new ArrayList<>();
      Set<String> seenMvNames = new HashSet<>();
      int index = 0;

      for(MvChangeRequest change : changes) {
         String label = "changes[" + index++ + "]";

         if(change == null) {
            throw new IllegalArgumentException(label + ": must not be null");
         }

         String verb = normalizeVerb(label, change.getVerb());

         if(change.getMvNames() == null || change.getMvNames().isEmpty()) {
            throw new IllegalArgumentException(label + ".mvNames: at least one name is required");
         }

         for(String mvName : change.getMvNames()) {
            if(!seenMvNames.add(mvName)) {
               throw new IllegalArgumentException(
                  label + ": duplicate entry for materialized view \"" + mvName +
                  "\"; list each mv name at most once across the whole changeset");
            }

            flat.add(new FlatChange(label, verb, change, mvName));
         }
      }

      return flat;
   }

   private static String normalizeVerb(String label, String verb) {
      if(MvChangeRequest.VERB_CREATE.equals(verb) || MvChangeRequest.VERB_SET_CYCLE.equals(verb) ||
         MvChangeRequest.VERB_DELETE.equals(verb))
      {
         return verb;
      }

      throw new IllegalArgumentException(
         label + ".verb: must be \"" + MvChangeRequest.VERB_CREATE + "\", \"" +
         MvChangeRequest.VERB_SET_CYCLE + "\", or \"" + MvChangeRequest.VERB_DELETE + "\", got " +
         String.valueOf(verb));
   }

   /** SHA-256 over the canonical plan. Same field-order/control-character contract as {@code
    * AdminChangePlanService#hash}/{@code ScheduleChangePlanService#hash} -- deliberately excludes
    * {@code task}, a free-text audit-only label with no bearing on what is actually mutated. */
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
         throw new IllegalStateException("SHA-256 is required to hash an mv change plan", e);
      }
   }

   private static String canonical(String value) {
      return value == null ? NULL_MARKER : value;
   }

   /** One flattened (entry, mvName) pair -- {@code source} carries every field beyond the mvName
    * itself ({@code analysisId}/{@code cycle}/{@code noData}/{@code runInBackground}), since those
    * are entry-level, not per-name. */
   static final class FlatChange {
      FlatChange(String label, String verb, MvChangeRequest source, String mvName) {
         this.label = label;
         this.verb = verb;
         this.source = source;
         this.mvName = mvName;
      }

      final String label;
      final String verb;
      final MvChangeRequest source;
      final String mvName;
   }

   private static final char SEP = (char) 0x1f;
   private static final String NULL_MARKER = String.valueOf((char) 0x01);
   private final AdminMvGateway mvGateway;
}
