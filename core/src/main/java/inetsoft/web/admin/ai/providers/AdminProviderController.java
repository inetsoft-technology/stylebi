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
import java.util.List;
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
    * Tests an authentication-chain provider's live connection using its OWN currently-stored
    * configuration -- authentication-chain only, no authorization equivalent (bug 76602, confirmed:
    * {@code AuthorizationProviderController} has no test-connection-shaped endpoint at all).
    * Deliberately does NOT accept a caller-supplied model the way the real EM UI's
    * {@code get-connection-status} endpoint does (which supports testing an in-progress, not-yet-
    * saved edit): {@link AuthenticationProviderService#getAuthenticationProvider} already sets
    * {@code oldName := name} on the model it returns (line ~119), so calling it here -- rather than
    * accepting any model from the request body -- is what forces the real stored credential to be
    * resolved server-side via {@code replacePlaceholderWithPassword}, never a literal placeholder
    * string (bug 76602's load-bearing fix; a caller cannot even construct the failure mode, since
    * there is no model parameter to smuggle a stale/masked password through).
    */
   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/security/provider",
      actions = ResourceAction.ACCESS))
   @GetMapping("/api/wiz/v1/admin/providers/authentication/{name}/test-connection")
   public ConnectionStatus testAuthenticationProviderConnection(@PathVariable("name") String name,
                                                                 Principal user)
      throws Exception
   {
      requireSiteAdmin(user);
      requireExists(name, authenticationProviderService.getProviderListModel().providers());
      AuthenticationProviderModel model = authenticationProviderService.getAuthenticationProvider(name);
      return new ConnectionStatus(authenticationProviderService.testConnection(model));
   }

   /**
    * Lists one authentication-chain provider's live directory entries (bug 76602) -- a live query
    * against the provider's OWN directory (e.g. an LDAP bind), NOT StyleBI's already-resolved
    * identity store ({@code list_identity_users} et al. cannot do this: they have no
    * provider-scoped view at all). Authentication-chain only, same reasoning as
    * {@link #testAuthenticationProviderConnection}. {@code kind} is one discriminated path segment
    * rather than three separate endpoints, matching how {@code chain}/{@code unitType} are already
    * single discriminators elsewhere in this area rather than one endpoint per value (03-fix.md).
    */
   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/security/provider",
      actions = ResourceAction.ACCESS))
   @GetMapping("/api/wiz/v1/admin/providers/authentication/{name}/directory/{kind}")
   public IdentityListModel getAuthenticationProviderDirectory(@PathVariable("name") String name,
                                                                @PathVariable("kind") String kind,
                                                                Principal user)
      throws Exception
   {
      requireSiteAdmin(user);
      requireExists(name, authenticationProviderService.getProviderListModel().providers());
      AuthenticationProviderModel model = authenticationProviderService.getAuthenticationProvider(name);

      return switch(kind) {
         case "users" -> authenticationProviderService.getUsers(model);
         case "groups" -> authenticationProviderService.getGroups(model);
         case "roles" -> authenticationProviderService.getRoles(model);
         default -> throw new IllegalArgumentException(
            "kind: must be \"users\", \"groups\", or \"roles\"; got \"" + kind + "\"");
      };
   }

   /**
    * Clears one authentication-chain provider's cache, resolved by NAME (bug 76602) --
    * {@link AuthenticationProviderService#clearAuthenticationProviderCache} is index-only, so the
    * index is resolved fresh here, right before the call, via
    * {@link ProviderChangesetApplyService#indexOfName} (promoted from {@code private}, not
    * duplicated). A provider whose type does not enable caching (e.g. FILE) is refused loud, naming
    * it, rather than silently succeeding as a no-op -- {@code CachableProvider.clearCache()}'s own
    * default implementation is an unconditional no-op, so calling through unconditionally would
    * report success for a call that changed nothing (03-fix.md's FILE-provider decision).
    */
   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/security/provider",
      actions = ResourceAction.ACCESS))
   @PostMapping("/api/wiz/v1/admin/providers/authentication/{name}/clear-cache")
   public SecurityProviderStatus clearAuthenticationProviderCacheByName(
      @PathVariable("name") String name, Principal user)
      throws Exception
   {
      requireSiteAdmin(user);
      List<SecurityProviderStatus> list = authenticationProviderService.getProviderListModel().providers();
      requireExists(name, list);
      int index = ProviderChangesetApplyService.indexOfName(list, name);
      AuthenticationProvider provider = authenticationProviderService.getAuthenticationChain()
         .orElseThrow(() -> new Exception("The authentication chain has not been initialized."))
         .getProviders().get(index);
      requireCacheable(name, provider);
      return authenticationProviderService.clearAuthenticationProviderCache(index);
   }

   /** Authorization-chain counterpart of {@link #clearAuthenticationProviderCacheByName} -- same
    * name-to-index resolution and same FILE-refuses-loud behavior, different chain's service. */
   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/security/provider",
      actions = ResourceAction.ACCESS))
   @PostMapping("/api/wiz/v1/admin/providers/authorization/{name}/clear-cache")
   public SecurityProviderStatus clearAuthorizationProviderCacheByName(
      @PathVariable("name") String name, Principal user)
      throws Exception
   {
      requireSiteAdmin(user);
      List<SecurityProviderStatus> list = authorizationProviderService.getProviderListModel().providers();
      requireExists(name, list);
      int index = ProviderChangesetApplyService.indexOfName(list, name);
      AuthorizationProvider provider = authorizationProviderService.getAuthorizationChain()
         .orElseThrow(() -> new Exception("The authorization chain has not been initialized."))
         .getProviders().get(index);
      requireCacheable(name, provider);
      return authorizationProviderService.clearAuthorizationProviderCache(index);
   }

   /**
    * Resolves a provider change plan (create/delete/duplicate, either or both chains) without
    * mutating anything. See {@code AdminAiController#preview} for the shape this mirrors.
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

   /** Both {@code AuthenticationProvider} and {@code AuthorizationProvider} extend
    * {@code CachableProvider} directly, so {@code isCacheEnabled()} is the generic capability check
    * for either chain -- confirmed by refute: {@code LdapAuthenticationProvider} hardcodes
    * {@code true}, {@code FileAuthenticationProvider} does not override it (inherits the interface's
    * {@code false} default). Refuses loud rather than letting {@code clearCache()}'s own no-op
    * default silently report success for a provider with nothing to clear. */
   private static void requireCacheable(String name, CachableProvider provider) {
      if(!provider.isCacheEnabled()) {
         throw new IllegalArgumentException(
            "name: provider \"" + name + "\" has no cache to clear -- this provider's type does " +
            "not enable caching, refused rather than silently reporting success for a call that " +
            "would change nothing");
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

   private final AuthenticationProviderService authenticationProviderService;
   private final AuthorizationProviderService authorizationProviderService;
   private final ProviderChangePlanService planService;
   private final ProviderChangesetApplyService applyService;
}
