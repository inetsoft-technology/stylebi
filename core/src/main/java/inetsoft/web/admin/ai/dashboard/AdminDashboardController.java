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
package inetsoft.web.admin.ai.dashboard;

import inetsoft.sree.security.*;
import inetsoft.web.admin.ai.AdminAiCallerGuard;
import inetsoft.web.admin.ai.AdminChangesetApplyService;
import inetsoft.web.admin.ai.ResolvedPlan;
import inetsoft.web.admin.content.repository.model.RepositoryDashboardSettingsModel;
import inetsoft.web.admin.content.repository.model.RepositoryFolderDashboardSettingsModel;
import inetsoft.web.security.RequiredPermission;
import inetsoft.web.security.Secured;
import inetsoft.web.security.auth.MissingResourceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.List;
import java.util.Map;

/**
 * REST controller for the Portal Dashboard admin-plugin area (Redmine #76695) -- the "Portal
 * Dashboard Tab" (global) / "User Portal Dashboard Tab" (per user) admin-configured feature, not
 * the self-service per-user pin feature (out of scope, see the design plan's background section).
 *
 * <p>Same {@code requireSiteAdmin}/{@code AdminAiCallerGuard} shape and the same
 * {@code @Secured}/{@code ResourceType.EM_COMPONENT}/{@code "settings/content/repository"} resource
 * string as {@code AdminViewsheetController} -- copied, not shared, matching this repo's own
 * established precedent for per-area controller duplication.
 */
@RestController
public class AdminDashboardController {
   @Autowired
   public AdminDashboardController(DashboardChangePlanService planService,
                                   DashboardChangesetApplyService applyService)
   {
      this.planService = planService;
      this.applyService = applyService;
   }

   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/content/repository",
      actions = ResourceAction.ACCESS))
   @GetMapping("/api/wiz/v1/admin/dashboards")
   public List<RepositoryDashboardSettingsModel> list(
      @RequestParam(value = "owner", required = false) String owner, Principal user)
      throws Exception
   {
      requireSiteAdmin(user);
      return planService.list(owner, user);
   }

   /** {@code path} names the dashboard, matching the viewsheet area's own query-parameter naming
    * convention for an identifying value; a dashboard has no nested folder path, only a flat
    * display name. */
   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/content/repository",
      actions = ResourceAction.ACCESS))
   @GetMapping("/api/wiz/v1/admin/dashboards/settings")
   public RepositoryDashboardSettingsModel getSettings(
      @RequestParam("path") String path,
      @RequestParam(value = "owner", required = false) String owner, Principal user)
      throws Exception
   {
      requireSiteAdmin(user);
      return planService.getSettings(path, owner, user);
   }

   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/content/repository",
      actions = ResourceAction.ACCESS))
   @GetMapping("/api/wiz/v1/admin/dashboards/folder")
   public RepositoryFolderDashboardSettingsModel getFolder(
      @RequestParam(value = "owner", required = false) String owner, Principal user)
      throws Exception
   {
      requireSiteAdmin(user);
      return planService.getFolder(owner, user);
   }

   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/content/repository",
      actions = ResourceAction.ACCESS))
   @PostMapping("/api/wiz/v1/admin/dashboards/preview")
   public ResolvedPlan preview(@RequestBody DashboardChangePlanRequest req, Principal user)
      throws Exception
   {
      requireSiteAdmin(user);
      return planService.resolve(req, user);
   }

   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/content/repository",
      actions = ResourceAction.ACCESS))
   @PostMapping("/api/wiz/v1/admin/dashboards/apply")
   public DashboardApplyResult apply(@RequestBody DashboardApplyRequest req, Principal user)
      throws Exception
   {
      requireSiteAdmin(user);
      return applyService.apply(req, user);
   }

   /** Same rationale and shape as {@code AdminViewsheetController#requireSiteAdmin} -- see there. */
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

   @ExceptionHandler(MissingResourceException.class)
   @ResponseStatus(HttpStatus.NOT_FOUND)
   @ResponseBody
   public Map<String, String> handleMissingResource(MissingResourceException ex) {
      return Map.of("status", "not-found", "error", String.valueOf(ex.getMessage()));
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

   private final DashboardChangePlanService planService;
   private final DashboardChangesetApplyService applyService;
}
