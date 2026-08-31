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
package inetsoft.web.admin.ai.providers;

import inetsoft.sree.security.*;
import inetsoft.web.admin.ai.AdminAiCallerGuard;
import inetsoft.web.admin.ai.AdminChangesetApplyService;
import inetsoft.web.admin.ai.ResolvedPlan;
import inetsoft.web.admin.security.*;
import inetsoft.web.security.RequiredPermission;
import inetsoft.web.security.Secured;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.Map;

/**
 * REST controller for the providers admin-plugin area (01-spec.md section 10). Placed in
 * {@code community/core}, not {@code enterprise/}, the one transport decision this area makes
 * differently from every prior area -- direct consequence of there being no Public API layer for
 * providers and the shared admin-chat scaffolding + {@code AuthenticationProviderService}/
 * {@code AuthorizationProviderService} all already living in {@code community/core} (section 0).
 *
 * <p>{@code @Secured} names {@code settings/security/provider}, the same resource string the real EM
 * controllers ({@code AuthenticationProviderController}/{@code AuthorizationProviderController}) use.
 * Per section 4a/10, this is not "inherited protection" the way it is for every prior area -- neither
 * service makes any {@code checkPermission} call on any mutating method, so there is nothing to
 * bypass in the first place; the sole real gate is {@code AdminAiCallerGuard}+
 * {@code OrganizationManager.isSiteAdmin}, added purely for consistency with every prior area's
 * belt-and-suspenders posture and to keep this area visible to any tooling that enumerates
 * {@code @Secured} endpoints.
 */
@RestController
public class AdminProviderController {
   @Autowired
   public AdminProviderController(AuthenticationProviderService authenticationProviderService,
                                  AuthorizationProviderService authorizationProviderService,
                                  ProviderChangePlanService planService,
                                  ProviderChangesetApplyService applyService)
   {
      this.authenticationProviderService = authenticationProviderService;
      this.authorizationProviderService = authorizationProviderService;
      this.planService = planService;
      this.applyService = applyService;
   }

   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/security/provider",
      actions = ResourceAction.ACCESS))
   @GetMapping("/api/wiz/v1/admin/providers/authentication")
   public SecurityProviderStatusList listAuthenticationProviders(Principal user) {
      requireSiteAdmin(user);
      return authenticationProviderService.getProviderListModel();
   }

   /** Existence is pre-checked against {@link #listAuthenticationProviders} before the potentially
    * NPE-prone {@code getAuthenticationProvider} is ever called (01-spec.md section 2 -- a real,
    * found tool-input-robustness gap in the underlying service on an unresolved name, confirmed by
    * reading {@code AuthenticationProviderService.getAuthenticationProvider} directly; this area's
    * own tool layer never calls it on a miss). */
   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/security/provider",
      actions = ResourceAction.ACCESS))
   @GetMapping("/api/wiz/v1/admin/providers/authentication/{name}")
   public AuthenticationProviderModel getAuthenticationProvider(@PathVariable("name") String name,
                                                                 Principal user)
      throws Exception
   {
      requireSiteAdmin(user);
      requireExists(name, authenticationProviderService.getProviderListModel().providers());
      return authenticationProviderService.getAuthenticationProvider(name);
   }

   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/security/provider",
      actions = ResourceAction.ACCESS))
   @GetMapping("/api/wiz/v1/admin/providers/authorization")
   public SecurityProviderStatusList listAuthorizationProviders(Principal user) {
      requireSiteAdmin(user);
      return authorizationProviderService.getProviderListModel();
   }

   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/security/provider",
      actions = ResourceAction.ACCESS))
   @GetMapping("/api/wiz/v1/admin/providers/authorization/{name}")
   public AuthorizationProviderModel getAuthorizationProvider(@PathVariable("name") String name,
                                                               Principal user)
      throws Exception
   {
      requireSiteAdmin(user);
      requireExists(name, authorizationProviderService.getProviderListModel().providers());
      return authorizationProviderService.getAuthorizationProvider(name);
   }

   /**
    * Resolves a provider change plan (create/delete, either or both chains) without mutating
    * anything. See {@code AdminAiController#preview} for the shape this mirrors.
    */
   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/security/provider",
      actions = ResourceAction.ACCESS))
   @PostMapping("/api/wiz/v1/admin/providers/preview")
   public ResolvedPlan preview(@RequestBody ProviderChangePlanRequest req, Principal user)
      throws Exception
   {
      requireSiteAdmin(user);
      return planService.resolve(req, user);
   }

   /**
    * Applies a reviewed provider change plan. Every verb in this area requires a Tier-2 backup
    * (01-spec.md section 4/6/7 -- unconditional), taken synchronously inside
    * {@link ProviderChangesetApplyService#apply} before any mutation.
    */
   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/security/provider",
      actions = ResourceAction.ACCESS))
   @PostMapping("/api/wiz/v1/admin/providers/apply")
   public ProviderApplyResult apply(@RequestBody ProviderApplyRequest req, Principal user)
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

   /** 01-spec.md section 2/3: a structured 404-shaped error naming the id, resolved via the list,
    * never via the NPE-prone {@code getAuthenticationProvider} on a raw miss. Raw-name match only
    * (03-reconcile.md Addition 1), matching {@link ProviderChangePlanService}'s own resolution rule. */
   private static void requireExists(String name, java.util.List<SecurityProviderStatus> providers) {
      for(SecurityProviderStatus p : providers) {
         if(p.name().equals(name)) {
            return;
         }
      }

      throw new ResponseStatusException(HttpStatus.NOT_FOUND,
         "not found: no provider named \"" + name + "\" in this chain");
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

   private final AuthenticationProviderService authenticationProviderService;
   private final AuthorizationProviderService authorizationProviderService;
   private final ProviderChangePlanService planService;
   private final ProviderChangesetApplyService applyService;
}
