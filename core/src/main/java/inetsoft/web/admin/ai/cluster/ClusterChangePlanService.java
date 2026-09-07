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

import inetsoft.util.audit.AdminChangeRecord;
import inetsoft.web.admin.ai.PlanChange;
import inetsoft.web.admin.ai.ResolvedPlan;
import inetsoft.web.admin.cluster.ClusterService;
import inetsoft.web.cluster.ServerClusterClient;
import inetsoft.web.cluster.ServerClusterStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Resolves a requested list of cluster pause/resume changes into a {@link ResolvedPlan} and hashes
 * it -- the cluster analog of {@code inetsoft.web.admin.ai.AdminChangePlanService} and (within this
 * run) {@code ProviderChangePlanService}/{@code IdentityChangePlanService}/etc, but deliberately NOT
 * a compensating-transaction design (01-spec.md section 4, 03-reconcile.md): each server's pause/
 * resume is independent and self-inverse, so there is no whole-plan rollback to design at all.
 *
 * <p><b>Both verbs are {@code risk: high}/{@code snapshotScope: value} unconditionally, as two
 * hardcoded label constants -- never a call to {@code AdminRiskClassifier.classify()}.</b> There is
 * no {@code AdminPropertyName} for a cluster server to classify against; "reuse
 * {@code AdminRiskClassifier}'s two axes" (03-reconcile.md's precision line) means reusing the two
 * label values it would have produced, not invoking it.
 *
 * <p><b>Required disclosure, at the same prominence as every other risk fact this area carries
 * (03-reconcile.md, not optional): a paused node's pause state does NOT survive that node leaving and
 * rejoining the cluster.</b> {@code ServerClusterClient}'s {@code memberRemoved} listener
 * unconditionally clears a node's paused flag the instant it drops cluster membership -- a crash, a
 * planned restart (the very action "pause for maintenance" exists to precede), or a transient network
 * partition all trigger it, with no error, no log line above {@code DEBUG}, and no signal to any
 * caller, including this area's own tools, that the operator's last explicit action silently stopped
 * being true. A successful {@code apply_cluster_changes} pause result is a point-in-time
 * confirmation, not a standing guarantee -- re-issue pause after any such event if the intent was to
 * keep the node out of rotation. No mechanism-level fix is in scope for this cut (closing it would
 * need a persisted "operator intent" store that does not exist today, predating admin-chat entirely);
 * this class and {@link ClusterChangesetApplyService} disclose it, they do not attempt to work around
 * it. The plugin (TypeScript) tool descriptions for {@code preview_cluster_changes}/
 * {@code apply_cluster_changes} must state this plainly too (03-reconcile.md item 2) -- carried here
 * as the authoritative Java-side statement of the fact for that layer to restate, not duplicate.
 *
 * <p>Wraps {@link ClusterService}/{@link ServerClusterClient} directly -- there is no Public API
 * (*ApiService) layer for cluster (01-spec.md section 0), and both live entirely in
 * {@code community/core}.
 */
@Component
public class ClusterChangePlanService {
   @Autowired
   public ClusterChangePlanService(ClusterService clusterService, ServerClusterClient client) {
      this.clusterService = clusterService;
      this.client = client;
   }

   /**
    * Resolves and hashes a plan. Performs no mutation, but does perform live reads (a fresh
    * {@code getConfiguredServers()}/{@code getStatus()} per server named in the request, plus the
    * {@code cluster.pause.enabled} property).
    *
    * @throws IllegalArgumentException with a field-named message on a blank task, an empty change
    *                                 list, an unrecognized verb, a blank/unrecognized server name, a
    *                                 duplicate (verb, server) entry, a same-server entry targeted by
    *                                 both verbs in the same plan, or {@code cluster.pause.enabled} not
    *                                 being {@code "true"} (item 10 -- checked once per plan, not per
    *                                 entry, since it is a single deployment-wide read).
    */
   public ResolvedPlan resolve(ClusterChangePlanRequest req) {
      if(req == null || req.getTask() == null || req.getTask().trim().isEmpty()) {
         throw new IllegalArgumentException("task: a non-empty description is required");
      }

      if(req.getChanges() == null || req.getChanges().isEmpty()) {
         throw new IllegalArgumentException("changes: at least one change is required");
      }

      Set<String> configured = client.getConfiguredServers();
      List<PlanChange> changes = new ArrayList<>();
      Map<String, String> seenServerVerb = new LinkedHashMap<>();
      int index = 0;

      for(ClusterChangeRequest change : req.getChanges()) {
         String label = "changes[" + index++ + "]";

         if(change == null) {
            throw new IllegalArgumentException(label + ": must not be null");
         }

         String verb = requireVerb(label, change.getVerb());
         String server = requireNonBlank(label + ".server", change.getServer());
         requireNoConflict(label, server, verb, seenServerVerb);
         requireConfigured(label, server, configured);
         changes.add(resolveOne(server, verb));
      }

      // 01-spec.md section 5 step 5: checked once per plan, after every entry resolves, not per
      // entry -- a single deployment-wide SreeEnv read, enforced for EITHER verb (item 10's sharper
      // finding: the EM UI hides both Pause and Resume when this property is off, not just Pause).
      requirePauseEnabled();

      String task = req.getTask().trim();
      return new ResolvedPlan(task, Collections.unmodifiableList(changes), false, true,
                              hash(changes));
   }

   private PlanChange resolveOne(String server, String verb) {
      ServerClusterStatus status = client.getStatus(server);
      String currentLabel = ClusterStatusLabel.displayStatus(status);
      boolean paused = status.isPaused();
      String proposed;
      boolean noOp;

      if(ClusterChangeRequest.VERB_PAUSE.equals(verb)) {
         noOp = paused;
         proposed = noOp ? currentLabel : ClusterStatusLabel.STATUS_PAUSED;
      }
      else {
         noOp = !paused;
         proposed = noOp ? currentLabel :
            (ClusterStatusLabel.reachable(status) ? ClusterStatusLabel.STATUS_RUNNING :
             ClusterStatusLabel.STATUS_STOPPED);
      }

      // Item 4's no-op detection: still produces a PlanChange, never silently dropped, with the
      // description saying so -- the caller should be told rather than left to infer it from a bare
      // 200 the way ClusterService.pauseServers/resumeServers' own skip already special-cases
      // server-side with no signal at all.
      String description = verb + " server " + server + (noOp ? " (already in target state; no-op)" : "");
      return new PlanChange(server, NOT_ORG_SCOPED, currentLabel, proposed, AdminChangeRecord.RISK_HIGH,
                            AdminChangeRecord.SCOPE_VALUE, true, description);
   }

   private void requirePauseEnabled() {
      if(!clusterService.getClusterEnabled().pauseEnabled()) {
         throw new IllegalArgumentException(
            "cluster.pause.enabled is not \"true\" for this deployment -- pause/resume is refused for " +
            "both verbs, mirroring the EM UI, which hides both the Pause and Resume buttons when this " +
            "property is off (01-spec.md item 10). stylebi#76342 remains open at the product/raw-" +
            "endpoint layer; this area closes the gap only for its own admin-chat path.");
      }
   }

   // ---------------------------------------------------------------- validation helpers

   /** Same server, same verb twice: a plain duplicate. Same server, opposite verbs: contradictory,
    * not a duplicate -- refused with a different, clearer message (01-spec.md section 11, a
    * validation case no prior area needed since none of their units were self-inverse pairs
    * addressable in the same request). Because a valid plan can therefore contain each server at
    * most once, {@code server} alone is a safe {@link PlanChange#property()} key. */
   private static void requireNoConflict(String label, String server, String verb,
                                         Map<String, String> seenServerVerb)
   {
      String priorVerb = seenServerVerb.putIfAbsent(server, verb);

      if(priorVerb == null) {
         return;
      }

      if(priorVerb.equals(verb)) {
         throw new IllegalArgumentException(
            label + ": duplicate entry for server \"" + server + "\" with verb \"" + verb +
            "\"; list each (verb, server) pair at most once");
      }

      throw new IllegalArgumentException(
         label + ": server \"" + server + "\" is targeted by both \"pause\" and \"resume\" in the " +
         "same plan -- contradictory for the same server in the same plan; split into two plans " +
         "applied in sequence, or drop one");
   }

   /** 01-spec.md section 2's own decision: a cluster server name that isn't in the live
    * {@code getConfiguredServers()} set names nothing -- there is no node to pause. Refused, loud,
    * naming the unrecognized server, before ever reaching {@code pauseServers}/{@code resumeServers}
    * -- never let a typo silently resolve to the same "looks like a down node" state a real outage
    * produces. */
   private static void requireConfigured(String label, String server, Set<String> configured) {
      if(!configured.contains(server)) {
         throw new IllegalArgumentException(
            label + ".server: \"" + server + "\" is not a configured server in this cluster");
      }
   }

   static String requireVerb(String label, String verb) {
      String trimmed = verb == null ? null : verb.trim();

      if(ClusterChangeRequest.VERB_PAUSE.equals(trimmed) || ClusterChangeRequest.VERB_RESUME.equals(trimmed)) {
         return trimmed;
      }

      throw new IllegalArgumentException(
         label + ".verb: must be \"" + ClusterChangeRequest.VERB_PAUSE + "\" or \"" +
         ClusterChangeRequest.VERB_RESUME + "\", got " + String.valueOf(verb));
   }

   private static String requireNonBlank(String field, String value) {
      if(value == null || value.trim().isEmpty()) {
         throw new IllegalArgumentException(field + ": required");
      }

      return value.trim();
   }

   // ---------------------------------------------------------------- hash

   /**
    * SHA-256 over the canonical plan: per-entry (server, currentValue, proposedValue, risk,
    * snapshotScope) -- same field set and control-character canonicalization convention every
    * prior area's own {@code hash} method uses (03-reconcile.md's precision line; no whole-chain
    * projection input, unlike providers -- cluster has no "chain" concept). Because
    * {@code currentValue} is captured live at preview time, a node that changes status between
    * preview and apply (crashes, or another admin/EM session pauses or resumes it) produces a
    * different hash on apply's fresh re-resolve, refused via the existing
    * {@code AdminChangesetApplyService.PlanHashMismatchException}/HTTP 409 path.
    * <p>{@code task} is deliberately excluded from this canonical string: it is a free-text,
    * audit-only label that flows only to {@code AdminChangeRecord.setTaskDescription} via
    * {@code ClusterChangesetApplyService.writeAudit()}, never compared, parsed, or used to gate a
    * mutation, so paraphrasing it between {@code preview} and {@code apply} must not change the
    * plan's identity or trigger a false plan-drift refusal.
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
         throw new IllegalStateException("SHA-256 is required to hash a cluster change plan", e);
      }
   }

   private static String canonical(String value) {
      return value == null ? NULL_MARKER : value;
   }

   private static final char SEP = (char) 0x1f;
   private static final String NULL_MARKER = String.valueOf((char) 0x01);
   /** Cluster nodes are whole-deployment, not org-scoped (01-spec.md section 1/org-boundary section)
    * -- every {@link PlanChange} this service builds passes this in place of a bare {@code null}
    * literal so the omission reads as deliberate, matching {@code ProviderChangePlanService
    * .NOT_ORG_SCOPED}'s own precedent (folded in from the start here, rather than as a P4 follow-up
    * the way providers needed). */
   private static final String NOT_ORG_SCOPED = null;
   private final ClusterService clusterService;
   private final ServerClusterClient client;
}
