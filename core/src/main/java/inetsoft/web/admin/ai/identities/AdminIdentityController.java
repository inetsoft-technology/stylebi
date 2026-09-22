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
package inetsoft.web.admin.ai.identities;

import inetsoft.web.admin.security.*;
import inetsoft.sree.security.*;
import inetsoft.web.admin.ai.AdminAiCallerGuard;
import inetsoft.web.admin.ai.AdminChangesetApplyService;
import inetsoft.web.admin.ai.SecurityProviderGuard;
import inetsoft.web.security.RequiredPermission;
import inetsoft.web.security.Secured;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.Map;

/**
 * REST controller for the identities admin-plugin area (spec section 10). Same
 * {@code requireSiteAdmin}/{@code AdminAiCallerGuard} shape as every prior area's controller --
 * copied, not shared, matching existing precedent for that duplication.
 *
 * <p>{@code @Secured} names {@code settings/security/users} (spec section 10) -- the same EM
 * resource string {@code UserController}/{@code GroupController}/{@code RoleController} themselves
 * use, since this area's scope is squarely that EM page's territory and a single controller serves
 * all four identity kinds uniformly.
 */
@RestController
public class AdminIdentityController {
   @Autowired
   public AdminIdentityController(SecurityService securityService,
                                  IdentityChangePlanService planService,
                                  IdentityChangesetApplyService applyService)
   {
      this.securityService = securityService;
      this.planService = planService;
      this.applyService = applyService;
   }

   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/security/users",
      actions = ResourceAction.ACCESS))
   @GetMapping("/api/wiz/v1/admin/identities/users")
   public SecurityUserList listUsers(Principal user) throws Exception {
      requireSiteAdmin(user);
      return securityService.getUsers(currentOrgId(), user);
   }

   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/security/users",
      actions = ResourceAction.ACCESS))
   @GetMapping("/api/wiz/v1/admin/identities/users/{id}")
   public SecurityUser getUser(@PathVariable("id") String id, Principal user) throws Exception {
      requireSiteAdmin(user);
      return securityService.getUser(
         IdentityChangePlanService.parseIdentityId("id", id, currentOrgId()), user);
   }

   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/security/users",
      actions = ResourceAction.ACCESS))
   @GetMapping("/api/wiz/v1/admin/identities/groups")
   public SecurityGroupList listGroups(Principal user) throws Exception {
      requireSiteAdmin(user);
      return securityService.getGroups(currentOrgId(), user);
   }

   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/security/users",
      actions = ResourceAction.ACCESS))
   @GetMapping("/api/wiz/v1/admin/identities/groups/{id}")
   public SecurityGroup getGroup(@PathVariable("id") String id, Principal user) throws Exception {
      requireSiteAdmin(user);
      return securityService.getGroup(
         IdentityChangePlanService.parseIdentityId("id", id, currentOrgId()), user);
   }

   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/security/users",
      actions = ResourceAction.ACCESS))
   @GetMapping("/api/wiz/v1/admin/identities/roles")
   public SecurityRoleList listRoles(Principal user) throws Exception {
      requireSiteAdmin(user);
      return securityService.getRoles(currentOrgId(), user);
   }

   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/security/users",
      actions = ResourceAction.ACCESS))
   @GetMapping("/api/wiz/v1/admin/identities/roles/{id}")
   public SecurityRole getRole(@PathVariable("id") String id, Principal user) throws Exception {
      requireSiteAdmin(user);
      return securityService.getRole(
         IdentityChangePlanService.parseIdentityId("id", id, currentOrgId()), user);
   }

   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/security/users",
      actions = ResourceAction.ACCESS))
   @GetMapping("/api/wiz/v1/admin/identities/organizations")
   public SecurityOrganizationList listOrganizations(Principal user) throws Exception {
      requireSiteAdmin(user);
      return securityService.getOrganizations(user);
   }

   /** {@code id} is the organization's id field, not its display name (spec section 3) --
    * {@link #listOrganizations} is how a caller resolves one from the other. */
   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/security/users",
      actions = ResourceAction.ACCESS))
   @GetMapping("/api/wiz/v1/admin/identities/organizations/{id}")
   public SecurityOrganization getOrganization(@PathVariable("id") String id, Principal user)
      throws Exception
   {
      requireSiteAdmin(user);
      return securityService.getOrganization(id, user);
   }

   /**
    * Resolves an identity change plan (create/delete, any mix of user/group/role/organization)
    * without mutating anything. See {@code AdminAiController#preview} for the shape this mirrors.
    */
   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/security/users",
      actions = ResourceAction.ACCESS))
   @PostMapping("/api/wiz/v1/admin/identities/preview")
   public inetsoft.web.admin.ai.ResolvedPlan preview(@RequestBody IdentityChangePlanRequest req,
                                                      Principal user)
      throws Exception
   {
      requireSiteAdmin(user);
      return planService.resolve(req, user);
   }

   /**
    * Applies a reviewed identity change plan. Every verb in this area requires a Tier-2 backup
    * (spec section 6/7), taken synchronously inside {@link IdentityChangesetApplyService#apply}
    * before any mutation -- not a separate precondition this controller checks. Same status
    * contract as every prior area's apply endpoint: {@code applied}/{@code rolled-back}/
    * {@code rollback-failed} -- except an organization-delete entry can only ever resolve to
    * {@code applied} or {@code failed} for itself (spec section 6 item 5), never rolled back.
    */
   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/security/users",
      actions = ResourceAction.ACCESS))
   @PostMapping("/api/wiz/v1/admin/identities/apply")
   public IdentityApplyResult apply(@RequestBody IdentityApplyRequest req, Principal user)
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

   private static String currentOrgId() {
      return OrganizationManager.getInstance().getCurrentOrgID();
   }

   @ExceptionHandler(SecurityProviderGuard.SecurityNotInitializedException.class)
   @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
   @ResponseBody
   public Map<String, String> handleSecurityNotInitialized(
      SecurityProviderGuard.SecurityNotInitializedException ex)
   {
      return Map.of("status", "failed", "error", String.valueOf(ex.getMessage()));
   }

   /** 409, not 503: the deployment is healthy, the request just cannot be satisfied by a
    * read-only chain, so retrying is pointless. */
   @ExceptionHandler(SecurityProviderGuard.ReadOnlyAuthenticationException.class)
   @ResponseStatus(HttpStatus.CONFLICT)
   @ResponseBody
   public Map<String, String> handleReadOnlyAuthentication(
      SecurityProviderGuard.ReadOnlyAuthenticationException ex)
   {
      return Map.of("status", "failed", "error", String.valueOf(ex.getMessage()));
   }

   @ExceptionHandler(IllegalArgumentException.class)
   @ResponseStatus(HttpStatus.BAD_REQUEST)
   @ResponseBody
   public Map<String, String> handleIllegalArgument(IllegalArgumentException ex) {
      return Map.of("status", "failed", "error", String.valueOf(ex.getMessage()));
   }

   @ExceptionHandler(inetsoft.web.security.auth.MissingResourceException.class)
   @ResponseStatus(HttpStatus.NOT_FOUND)
   @ResponseBody
   public Map<String, String> handleMissingResource(
      inetsoft.web.security.auth.MissingResourceException ex)
   {
      // spec section 3: a structured 404-shaped error naming the id, not a raw exception -- and
      // deliberately worded "not found or not permitted" since a checkPermission denial and a real
      // not-found are indistinguishable at this layer (spec section 2).
      return Map.of("status", "failed",
                    "error", "not found or not permitted: " + String.valueOf(ex.getMessage()));
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
   private final IdentityChangePlanService planService;
   private final IdentityChangesetApplyService applyService;
}
