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
package inetsoft.web.admin.ai.sessions;

import inetsoft.sree.security.*;
import inetsoft.web.admin.ai.AdminAiCallerGuard;
import inetsoft.web.admin.ai.AdminChangesetApplyService;
import inetsoft.web.admin.ai.ResolvedPlan;
import inetsoft.web.admin.user.UserService;
import inetsoft.web.admin.user.UserSessionMonitoringTableModel;
import inetsoft.web.security.RequiredPermission;
import inetsoft.web.security.Secured;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.List;
import java.util.Map;

/**
 * REST controller for the user-session admin-plugin area: list active sessions and log selected
 * ones out, mirroring Enterprise Manager Monitoring &gt; Users ({@code UserStatusController}),
 * with the same EM permission resources. Same {@code requireSiteAdmin}/{@code AdminAiCallerGuard}
 * shape as every other admin-chat controller.
 */
@RestController
public class AdminUserSessionController {
   @Autowired
   public AdminUserSessionController(UserService userService,
                                     UserSessionChangePlanService planService,
                                     UserSessionChangesetApplyService applyService)
   {
      this.userService = userService;
      this.planService = planService;
      this.applyService = applyService;
   }

   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "monitoring/users",
      actions = ResourceAction.ACCESS))
   @GetMapping("/api/wiz/v1/admin/user-sessions")
   public List<UserSessionMonitoringTableModel> list(
      @RequestParam(value = "clusterNode", required = false) String clusterNode, Principal user)
   {
      requireSiteAdmin(user);
      return userService.getServerSessionModel(
         UserSessionChangePlanService.resolveAddress(clusterNode), user);
   }

   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "monitoring/users/session",
      actions = ResourceAction.ACCESS))
   @PostMapping("/api/wiz/v1/admin/user-sessions/preview")
   public ResolvedPlan preview(@RequestBody UserSessionChangePlanRequest req, Principal user) {
      requireSiteAdmin(user);
      return planService.resolve(req, user);
   }

   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "monitoring/users/session",
      actions = ResourceAction.ACCESS))
   @PostMapping("/api/wiz/v1/admin/user-sessions/apply")
   public UserSessionApplyResult apply(@RequestBody UserSessionApplyRequest req, Principal user) {
      requireSiteAdmin(user);
      return applyService.apply(req, user);
   }

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

   private final UserService userService;
   private final UserSessionChangePlanService planService;
   private final UserSessionChangesetApplyService applyService;
}
