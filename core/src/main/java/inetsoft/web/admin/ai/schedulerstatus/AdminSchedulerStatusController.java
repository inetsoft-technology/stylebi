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

import inetsoft.sree.security.*;
import inetsoft.web.admin.ai.AdminAiCallerGuard;
import inetsoft.web.admin.ai.AdminChangesetApplyService;
import inetsoft.web.admin.ai.ResolvedPlan;
import inetsoft.web.admin.schedule.SchedulerConfigurationService;
import inetsoft.web.admin.schedule.model.ScheduleStatusModel;
import inetsoft.web.security.RequiredPermission;
import inetsoft.web.security.Secured;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.Map;

/**
 * REST controller for the scheduler-status admin-plugin area (track-status/01-design.md). Placed
 * in {@code community/core}, not {@code enterprise/} -- {@code SchedulerConfigurationService}/
 * {@code Controller} and {@code ServerService}/{@code ServerMonitoringController} (the thread/heap
 * dump primitives) both live entirely in {@code community/core} already, and this area is not
 * enterprise-gated at the tool level, matching Cluster's own precedent.
 *
 * <p>The status/preview/apply endpoints reuse {@code settings/schedule/status}, the same resource
 * string {@code SchedulerConfigurationController}'s own {@code getStatus}/{@code setStatus}
 * endpoints use; thread-dump/heap-dump reuse {@code monitoring/summary}, the same resource string
 * {@code ServerMonitoringController}'s own scheduler thread/heap dump endpoints use.
 *
 * <p>Same {@code requireSiteAdmin}/{@code AdminAiCallerGuard} belt-and-suspenders shape as every
 * other admin-chat controller in this run.
 */
@RestController
public class AdminSchedulerStatusController {
   @Autowired
   public AdminSchedulerStatusController(SchedulerConfigurationService configService,
                                         SchedulerStatusChangePlanService planService,
                                         SchedulerStatusChangesetApplyService applyService,
                                         SchedulerDiagnosticsService diagnosticsService)
   {
      this.configService = configService;
      this.planService = planService;
      this.applyService = applyService;
      this.diagnosticsService = diagnosticsService;
   }

   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/schedule/status",
      actions = ResourceAction.ACCESS))
   @GetMapping("/api/wiz/v1/admin/scheduler/status")
   public ScheduleStatusModel getStatus(Principal user) {
      requireSiteAdmin(user);
      return configService.getStatus();
   }

   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/schedule/status",
      actions = ResourceAction.ACCESS))
   @PostMapping("/api/wiz/v1/admin/scheduler/preview")
   public ResolvedPlan preview(@RequestBody SchedulerStatusChangePlanRequest req, Principal user) {
      requireSiteAdmin(user);
      return planService.resolve(req);
   }

   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/schedule/status",
      actions = ResourceAction.ACCESS))
   @PostMapping("/api/wiz/v1/admin/scheduler/apply")
   public SchedulerStatusApplyResult apply(@RequestBody SchedulerStatusApplyRequest req, Principal user) {
      requireSiteAdmin(user);
      return applyService.apply(req, user);
   }

   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "monitoring/summary",
      actions = ResourceAction.ACCESS))
   @GetMapping("/api/wiz/v1/admin/scheduler/thread-dump")
   public Map<String, String> getThreadDump(
      @RequestParam(value = "clusterNode", required = false) String clusterNode, Principal user)
      throws Exception
   {
      requireSiteAdmin(user);
      return Map.of("threadDump", diagnosticsService.getThreadDump(clusterNode));
   }

   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "monitoring/summary",
      actions = ResourceAction.ACCESS))
   @PostMapping("/api/wiz/v1/admin/scheduler/heap-dump")
   public SchedulerHeapDumpToken createHeapDump(
      @RequestBody(required = false) SchedulerHeapDumpKickoffRequest req, Principal user)
      throws Exception
   {
      requireSiteAdmin(user);
      String clusterNode = req == null ? null : req.getClusterNode();
      return diagnosticsService.createHeapDump(clusterNode);
   }

   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "monitoring/summary",
      actions = ResourceAction.ACCESS))
   @GetMapping("/api/wiz/v1/admin/scheduler/heap-dump/{token}/status")
   public SchedulerHeapDumpStatus getHeapDumpStatus(
      @PathVariable("token") String token,
      @RequestParam(value = "clusterNode", required = false) String clusterNode, Principal user)
   {
      requireSiteAdmin(user);
      return diagnosticsService.getHeapDumpStatus(token, clusterNode);
   }

   /** Same rationale and shape as every other admin-chat controller's own
    * {@code requireSiteAdmin}. */
   private void requireSiteAdmin(Principal user) {
      AdminAiCallerGuard.requireBearerAuthenticatedRequest();

      if(!OrganizationManager.getInstance().isSiteAdmin(user)) {
         throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Site Administrator role required");
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

   @ExceptionHandler(AdminChangesetApplyService.TaskTokenMismatchException.class)
   @ResponseStatus(HttpStatus.CONFLICT)
   @ResponseBody
   public Map<String, Object> handleTaskTokenMismatch(
      AdminChangesetApplyService.TaskTokenMismatchException ex)
   {
      return Map.of("status", "conflict", "error", String.valueOf(ex.getMessage()),
                    "plan", ex.current());
   }

   private final SchedulerConfigurationService configService;
   private final SchedulerStatusChangePlanService planService;
   private final SchedulerStatusChangesetApplyService applyService;
   private final SchedulerDiagnosticsService diagnosticsService;
}
