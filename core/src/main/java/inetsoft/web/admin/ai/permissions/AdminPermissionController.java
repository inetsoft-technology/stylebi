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
package inetsoft.web.admin.ai.permissions;

import inetsoft.web.admin.security.PermissionGrant;
import inetsoft.web.admin.security.ResourcePermission;
import inetsoft.web.admin.security.SecurityService;
import inetsoft.sree.security.*;
import inetsoft.web.admin.ai.AdminAiCallerGuard;
import inetsoft.web.admin.ai.AdminChangesetApplyService;
import inetsoft.web.admin.ai.ApplyResult;
import inetsoft.web.admin.ai.ResolvedPlan;
import inetsoft.web.admin.ai.SecurityProviderGuard;
import inetsoft.web.admin.security.action.ActionPermissionService;
import inetsoft.web.admin.security.action.ActionTreeNode;
import inetsoft.web.security.RequiredPermission;
import inetsoft.web.security.Secured;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for the permissions admin-plugin area (spec section 10). Same
 * {@code requireSiteAdmin}/{@code AdminAiCallerGuard} shape as {@code AdminScheduleController}/
 * {@code AdminAiController}/{@code AdminChangesetController} -- copied, not shared, matching the
 * existing precedent for that duplication.
 *
 * <p>{@code @Secured} names {@code settings/security/actions} (spec section 10) -- the same EM
 * resource string {@code SecurityService.checkPermissionAccess}'s action-tree branch itself
 * checks, deliberately not {@code settings/content/repository} (what {@code
 * ResourcePermissionController} uses), because this area administers permissions, not the
 * repository.
 */
@RestController
public class AdminPermissionController {
   @Autowired
   public AdminPermissionController(SecurityService securityService,
                                    PermissionChangePlanService planService,
                                    PermissionChangesetApplyService applyService,
                                    ActionPermissionService actionPermissionService)
   {
      this.securityService = securityService;
      this.planService = planService;
      this.applyService = applyService;
      this.actionPermissionService = actionPermissionService;
   }

   /**
    * Flattens {@code ActionPermissionService.getActionTree} (the same tree the EM "Security
    * Actions" page and {@code checkPermissionAccess} both read) into the grantable
    * (label, resourceType, resourcePath) leaves this area's own allowlist actually accepts (bug
    * 76600 Gap 1 discovery tool) -- e.g. Bookmark's "Open Bookmark" leaf is
    * {@code VIEWSHEET_ACTION}/"OpenBookmark", not a guessable repository path the way an ASSET is.
    * Filtered to {@code PermissionChangePlanService.ALLOWED_RESOURCE_TYPES} so this never surfaces
    * an EM/EM_COMPONENT/SCHEDULE_TASK/LOGIN_AS leaf this area's other three endpoints would refuse
    * anyway.
    */
   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/security/actions",
      actions = ResourceAction.ACCESS))
   @GetMapping("/api/wiz/v1/admin/permissions/actions")
   public List<Map<String, Object>> listGrantableActions(Principal user) {
      requireSiteAdmin(user);
      ActionTreeNode root = actionPermissionService.getActionTree(user);
      List<Map<String, Object>> leaves = new ArrayList<>();
      collectGrantableLeaves(root, null, leaves);
      return leaves;
   }

   private void collectGrantableLeaves(ActionTreeNode node, String category,
                                       List<Map<String, Object>> out)
   {
      if(!node.folder() && node.type() != null && node.resource() != null &&
         PermissionChangePlanService.ALLOWED_RESOURCE_TYPES.contains(node.type().name()))
      {
         Map<String, Object> leaf = new LinkedHashMap<>();
         leaf.put("category", category);
         leaf.put("label", node.label());
         leaf.put("resourceType", node.type().name());
         leaf.put("resourcePath", node.resource());
         leaf.put("actions", node.actions().stream().map(Enum::name).toList());
         out.add(leaf);
         return;
      }

      String nextCategory = node.folder() && !node.label().isEmpty() ? node.label() : category;

      for(ActionTreeNode child : node.children()) {
         collectGrantableLeaves(child, nextCategory, out);
      }
   }

   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/security/actions",
      actions = ResourceAction.ACCESS))
   @GetMapping("/api/wiz/v1/admin/permissions")
   public ResourcePermission listGrants(@RequestParam("resourceType") String resourceType,
                                        @RequestParam("resourcePath") String resourcePath,
                                        Principal user)
      throws Exception
   {
      requireSiteAdmin(user);
      ResourceType type = PermissionChangePlanService.requireAllowedResourceType(
         "resourceType", resourceType);
      return securityService.getPermission(resourcePath, type.name(), user);
   }

   /**
    * Wraps {@code getPermissionGrant}. Returns {@code {found: false, ...}} on a missing grant --
    * spec section 3: "no grant" is a legitimate answer, not an error, unlike an unrecognized
    * schedule-task id.
    */
   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/security/actions",
      actions = ResourceAction.ACCESS))
   @GetMapping("/api/wiz/v1/admin/permissions/grant")
   public Map<String, Object> getGrant(@RequestParam("resourceType") String resourceType,
                                       @RequestParam("resourcePath") String resourcePath,
                                       @RequestParam("identityType") String identityType,
                                       @RequestParam("identityId") String identityId,
                                       Principal user)
      throws Exception
   {
      requireSiteAdmin(user);
      ResourceType type = PermissionChangePlanService.requireAllowedResourceType(
         "resourceType", resourceType);
      String idType = PermissionChangePlanService.requireIdentityType("identityType", identityType);
      String currentOrgId = OrganizationManager.getInstance().getCurrentOrgID();
      IdentityID id = PermissionChangePlanService.requireIdentityId(
         "identityId", identityId, currentOrgId);

      PermissionGrant grant = securityService.getPermissionGrant(
         resourcePath, type.name(), id.getName(), idType, user);

      if(grant == null) {
         return Map.of("found", false, "resource", resourcePath, "resourceType", type.name(),
                       "identityType", idType, "identityId", identityId);
      }

      return Map.of("found", true, "resource", resourcePath, "resourceType", type.name(),
                    "identityType", idType, "identityId", identityId,
                    "actions", grant.getActions());
   }

   /**
    * Resolves a permission-grant change plan without mutating anything. See
    * {@code AdminAiController#preview} for the shape this mirrors.
    */
   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/security/actions",
      actions = ResourceAction.ACCESS))
   @PostMapping("/api/wiz/v1/admin/permissions/preview")
   public ResolvedPlan preview(@RequestBody PermissionChangePlanRequest req, Principal user)
      throws Exception
   {
      requireSiteAdmin(user);
      return planService.resolve(req, user);
   }

   /**
    * Applies a reviewed permission-grant change plan, all-or-nothing. Same status contract as
    * {@code AdminAiController#apply}: {@code applied}/{@code rolled-back}/{@code rollback-failed}.
    */
   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/security/actions",
      actions = ResourceAction.ACCESS))
   @PostMapping("/api/wiz/v1/admin/permissions/apply")
   public ApplyResult apply(@RequestBody PermissionApplyRequest req, Principal user)
      throws Exception
   {
      requireSiteAdmin(user);
      return applyService.apply(req, user);
   }

   /** Same rationale and shape as {@code AdminAiController#requireSiteAdmin} -- see there. */
   private void requireSiteAdmin(Principal user) {
      AdminAiCallerGuard.requireBearerAuthenticatedRequest();

      if(!OrganizationManager.getInstance().isSiteAdmin(user)) {
         throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Site Administrator role required");
      }
   }

   @ExceptionHandler(SecurityProviderGuard.SecurityNotInitializedException.class)
   @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
   @ResponseBody
   public Map<String, String> handleSecurityNotInitialized(
      SecurityProviderGuard.SecurityNotInitializedException ex)
   {
      return Map.of("status", "failed", "error", String.valueOf(ex.getMessage()));
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

   private final SecurityService securityService;
   private final PermissionChangePlanService planService;
   private final PermissionChangesetApplyService applyService;
   private final ActionPermissionService actionPermissionService;
}
