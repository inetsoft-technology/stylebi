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

import inetsoft.sree.security.OrganizationManager;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.web.admin.ai.AdminAiCallerGuard;
import inetsoft.web.admin.ai.AdminChangesetApplyService;
import inetsoft.web.admin.ai.ApplyResult;
import inetsoft.web.admin.ai.ResolvedPlan;
import inetsoft.web.security.RequiredPermission;
import inetsoft.web.security.Secured;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.Map;

/**
 * REST controller for the Schedule Settings admin-plugin area (Server Locations/Time Ranges) --
 * same {@code requireSiteAdmin}/{@code AdminAiCallerGuard} shape as {@link AdminScheduleController}
 * (copied, not shared, matching that class's own precedent). Secured on {@code
 * settings/schedule/settings} -- the same EM permission {@code SchedulerConfigurationController}
 * itself uses for this exact settings page, NOT {@code settings/schedule/tasks} (task management is
 * a different EM permission from schedule settings). Community-tier: there is no enterprise Public
 * API layer for either sub-resource to gate behind (see {@code 01-design.md}'s "No enterprise
 * Public API layer exists" section) -- a genuine, confirmed divergence from every other track in
 * this run.
 */
@RestController
public class AdminScheduleConfigController {
   @Autowired
   public AdminScheduleConfigController(AdminScheduleConfigGateway gateway,
                                        ScheduleConfigChangePlanService planService,
                                        ScheduleConfigChangesetApplyService applyService)
   {
      this.gateway = gateway;
      this.planService = planService;
      this.applyService = applyService;
   }

   /** Deliberately narrowed to {@code serverLocations}/{@code timeRanges} -- Track B (scheduler
    * options) owns the model's other scalar fields and exposes them through the generic properties
    * area instead, so there is no competing read shape to reconcile against here. */
   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/schedule/settings",
      actions = ResourceAction.ACCESS))
   @GetMapping("/api/wiz/v1/admin/schedule/config")
   public ScheduleConfigView getConfig(Principal user) throws Exception {
      requireSiteAdmin(user);
      var full = gateway.getFullConfig(user);
      return new ScheduleConfigView(full.serverLocations(), full.timeRanges());
   }

   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/schedule/settings",
      actions = ResourceAction.ACCESS))
   @PostMapping("/api/wiz/v1/admin/schedule/config/preview")
   public ResolvedPlan preview(@RequestBody ScheduleConfigChangePlanRequest req, Principal user)
      throws Exception
   {
      requireSiteAdmin(user);
      return planService.resolve(req, user);
   }

   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/schedule/settings",
      actions = ResourceAction.ACCESS))
   @PostMapping("/api/wiz/v1/admin/schedule/config/apply")
   public ApplyResult apply(@RequestBody ScheduleConfigApplyRequest req, Principal user) throws Exception {
      requireSiteAdmin(user);
      return applyService.apply(req, user);
   }

   /** Same rationale and shape as {@code AdminScheduleController#requireSiteAdmin} -- see there. */
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

   private final AdminScheduleConfigGateway gateway;
   private final ScheduleConfigChangePlanService planService;
   private final ScheduleConfigChangesetApplyService applyService;
}
