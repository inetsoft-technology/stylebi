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
package inetsoft.web.admin.ai;

import inetsoft.sree.security.*;
import inetsoft.web.security.RequiredPermission;
import inetsoft.web.security.Secured;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.security.Principal;
import java.util.Map;

@RestController
public class AdminAiController {
   @Autowired
   public AdminAiController(AdminBackupService backupService,
                            AdminChangePlanService planService,
                            AdminChangesetApplyService applyService)
   {
      this.backupService = backupService;
      this.planService = planService;
      this.applyService = applyService;
   }

   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT,
      resource = "settings/properties",
      actions = ResourceAction.ACCESS))
   @PostMapping("/api/wiz/v1/admin/backup")
   public Map<String, String> backup(@RequestBody Map<String, String> body, Principal user)
      throws Exception
   {
      requireSiteAdmin(user);
      return Map.of("backupRef", backupService.backup(body.get("transactionId")));
   }

   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT,
      resource = "settings/properties",
      actions = ResourceAction.ACCESS))
   @PostMapping("/api/wiz/v1/admin/restore")
   public Map<String, String> restore(@RequestBody Map<String, String> body, Principal user)
      throws Exception
   {
      requireSiteAdmin(user);
      backupService.restore(body.get("backupRef"));
      return Map.of("status", "restored");
   }

   /**
    * Resolves a change list into a reviewable plan without mutating anything, and returns the
    * {@code planHash} an {@link #apply} must echo.
    */
   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT,
      resource = "settings/properties",
      actions = ResourceAction.ACCESS))
   @PostMapping("/api/wiz/v1/admin/preview")
   public ResolvedPlan preview(@RequestBody PlanRequest req, Principal user) {
      requireSiteAdmin(user);
      return planService.resolve(req);
   }

   /**
    * Applies a reviewed plan, all-or-nothing.
    *
    * <p>Note the status contract: a {@code 200} response carries a {@code status} of
    * {@code applied}, {@code rolled-back} or {@code rollback-failed}. An error status means no
    * mutation occurred, so a non-200 never means "partially applied".
    *
    * @throws ResponseStatusException with {@code 409} and the freshly resolved plan when the
    *         {@code planHash} is missing or stale.
    */
   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT,
      resource = "settings/properties",
      actions = ResourceAction.ACCESS))
   @PostMapping("/api/wiz/v1/admin/apply")
   public ApplyResult apply(@RequestBody ApplyRequest req, Principal user) throws Exception {
      requireSiteAdmin(user);
      return applyService.apply(req, user);
   }

   /**
    * Per product decision, every admin-chat endpoint is restricted to callers holding the Site
    * Administrator (system administrator) role, not merely EM_COMPONENT access. Also requires the
    * request to carry a bearer token — see {@link AdminAiCallerGuard} for why the Site-Administrator
    * check alone is not sufficient on the CSRF-exempt {@code /api/wiz/**} prefix.
    * {@link OrganizationManager#isSiteAdmin(Principal)} returns {@code true} for the default
    * admin principal in no-security deployments, so this guard does not lock out dev/single-user
    * setups and does not need an additional {@code isSecurityEnabled()} check.
    */
   private void requireSiteAdmin(Principal user) {
      AdminAiCallerGuard.requireBearerAuthenticatedRequest();

      if(!OrganizationManager.getInstance().isSiteAdmin(user)) {
         throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Site Administrator role required");
      }
   }

   /**
    * Uniformly maps client/validation errors (blank/invalid transactionId, property, action, or
    * backup/restore reference) to HTTP 400 with a structured body, across all three endpoints.
    * Scoped to {@link IllegalArgumentException} only - a catch-all {@code Exception} handler
    * would also swallow {@link ResponseStatusException} (see {@link #requireSiteAdmin}) and other
    * framework exceptions that must retain their own status codes.
    */
   @ExceptionHandler(IllegalArgumentException.class)
   @ResponseStatus(HttpStatus.BAD_REQUEST)
   @ResponseBody
   public Map<String, String> handleIllegalArgument(IllegalArgumentException ex) {
      return Map.of("status", "failed", "error", String.valueOf(ex.getMessage()));
   }

   /**
    * Maps a stale/missing {@code planHash} to {@code 409} carrying the current plan, so the caller
    * can show the operator what changed and re-review. Deliberately not {@code 400}: the request was
    * well-formed, the world moved.
    */
   @ExceptionHandler(AdminChangesetApplyService.PlanHashMismatchException.class)
   @ResponseStatus(HttpStatus.CONFLICT)
   @ResponseBody
   public Map<String, Object> handlePlanHashMismatch(
      AdminChangesetApplyService.PlanHashMismatchException ex)
   {
      return Map.of("status", "conflict",
                    "error", String.valueOf(ex.getMessage()),
                    "plan", ex.current());
   }

   private final AdminBackupService backupService;
   private final AdminChangePlanService planService;
   private final AdminChangesetApplyService applyService;
}
