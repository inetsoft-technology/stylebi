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

import inetsoft.sree.security.*;
import inetsoft.web.admin.ai.AdminAiCallerGuard;
import inetsoft.web.admin.ai.AdminChangesetApplyService;
import inetsoft.web.admin.ai.ResolvedPlan;
import inetsoft.web.cluster.ServerClusterClient;
import inetsoft.web.cluster.ServerClusterStatus;
import inetsoft.web.security.RequiredPermission;
import inetsoft.web.security.Secured;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.*;

/**
 * REST controller for the cluster admin-plugin area (01-spec.md section 10). Placed in
 * {@code community/core}, not {@code enterprise/}, matching the C.4/providers community-placement
 * precedent -- {@code ClusterController}/{@code ClusterService} both already live entirely in
 * {@code community/core}, and this area is not enterprise-gated at the tool level (the one universal
 * exception, matching every prior area: {@code get_changeset} read-back stays enterprise-only).
 *
 * <p>{@code @Secured} names {@code monitoring/cluster}, the same resource string the real
 * {@code ClusterController} uses for its own {@code pause-server}/{@code resume-server} endpoints.
 * Per 01-spec.md section 4a/section 2.1a trace, this is not "inherited protection" -- the whole
 * {@code @Secured} gate on those endpoints is inert today, by two independent mechanisms (the
 * program-wide wiz-caller {@code checkPermission} bypass, and {@code monitoring/cluster}'s own
 * unflagged {@code hiddenForMultiTenancy}/{@code requiresMultiTenancy} defaults) -- so the sole real
 * gate here is {@code AdminAiCallerGuard} + {@code OrganizationManager.isSiteAdmin}, added for
 * consistency with every prior area's belt-and-suspenders posture.
 */
@RestController
public class AdminClusterController {
   @Autowired
   public AdminClusterController(ClusterChangePlanService planService,
                                 ClusterChangesetApplyService applyService, ServerClusterClient client)
   {
      this.planService = planService;
      this.applyService = applyService;
      this.client = client;
   }

   /** Neither read tool requires {@code cluster.pause.enabled} (01-spec.md item 10) -- matching the
    * real EM UI, which keeps the whole node/status table visible and only hides the Pause/Resume
    * button row when the property is off. */
   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "monitoring/cluster",
      actions = ResourceAction.ACCESS))
   @GetMapping("/api/wiz/v1/admin/cluster/nodes")
   public List<ClusterNodeStatus> listClusterNodes(Principal user) {
      requireSiteAdmin(user);
      List<ClusterNodeStatus> nodes = new ArrayList<>();

      for(String server : client.getConfiguredServers()) {
         nodes.add(projection(server));
      }

      return nodes;
   }

   /** Existence is resolved against a fresh {@code getConfiguredServers()} read before
    * {@code getStatus} is ever called on the name, so an unrecognized name is a structured 404,
    * never a silently default-constructed {@code ServerClusterStatus}
    * ({@code ServerClusterClient.getClusterNodeStatus}'s own DOWN/paused=false fallback for an
    * unmapped address would otherwise look like a real, if unreachable, node) -- 01-spec.md section
    * 3, mirroring {@code AdminProviderController.requireExists}. */
   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "monitoring/cluster",
      actions = ResourceAction.ACCESS))
   @GetMapping("/api/wiz/v1/admin/cluster/nodes/{server}")
   public ClusterNodeStatus getClusterNode(@PathVariable("server") String server, Principal user) {
      requireSiteAdmin(user);
      requireExists(server, client.getConfiguredServers());
      return projection(server);
   }

   /**
    * Resolves a cluster pause/resume change plan without mutating anything. See
    * {@code AdminAiController#preview} for the shape this mirrors.
    *
    * <p>The required membership-churn disclosure (see {@link ClusterChangePlanService}'s own class
    * javadoc) belongs in the plugin (TypeScript) tool description for {@code preview_cluster_changes}
    * -- this endpoint does not add prose to the wire response beyond what {@link ResolvedPlan}/
    * {@code PlanChange.description()} already carry.
    */
   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "monitoring/cluster",
      actions = ResourceAction.ACCESS))
   @PostMapping("/api/wiz/v1/admin/cluster/preview")
   public ResolvedPlan preview(@RequestBody ClusterChangePlanRequest req, Principal user) {
      requireSiteAdmin(user);
      return planService.resolve(req);
   }

   /**
    * Applies a reviewed cluster change plan. No verb in this area ever requires a Tier-2 backup
    * (01-spec.md section 7) -- unlike every prior area's own apply endpoint, nothing is backed up
    * here.
    */
   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "monitoring/cluster",
      actions = ResourceAction.ACCESS))
   @PostMapping("/api/wiz/v1/admin/cluster/apply")
   public ClusterApplyResult apply(@RequestBody ClusterApplyRequest req, Principal user) {
      requireSiteAdmin(user);
      return applyService.apply(req, user);
   }

   private ClusterNodeStatus projection(String server) {
      ServerClusterStatus status = client.getStatus(server);
      return new ClusterNodeStatus(server, ClusterStatusLabel.displayStatus(status),
                                   ClusterStatusLabel.reachable(status));
   }

   /** Same rationale and shape as {@code AdminProviderController#requireSiteAdmin} -- see there. */
   private void requireSiteAdmin(Principal user) {
      AdminAiCallerGuard.requireBearerAuthenticatedRequest();

      if(!OrganizationManager.getInstance().isSiteAdmin(user)) {
         throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Site Administrator role required");
      }
   }

   private static void requireExists(String server, Set<String> configured) {
      if(!configured.contains(server)) {
         throw new ResponseStatusException(HttpStatus.NOT_FOUND,
            "not found: no server named \"" + server + "\" in this cluster");
      }
   }

   @ExceptionHandler(IllegalArgumentException.class)
   @ResponseStatus(HttpStatus.BAD_REQUEST)
   @ResponseBody
   public Map<String, String> handleIllegalArgument(IllegalArgumentException ex) {
      return Map.of("status", "failed", "error", String.valueOf(ex.getMessage()));
   }

   @ExceptionHandler(AdminChangesetApplyService.PlanHashMismatchException.class)
   @ResponseStatus(HttpStatus.CONFLICT)
   @ResponseBody
   public Map<String, Object> handlePlanHashMismatch(
      AdminChangesetApplyService.PlanHashMismatchException ex)
   {
      return Map.of("status", "conflict", "error", String.valueOf(ex.getMessage()),
                    "plan", ex.current());
   }

   private final ClusterChangePlanService planService;
   private final ClusterChangesetApplyService applyService;
   private final ServerClusterClient client;
}
