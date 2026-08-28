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
package inetsoft.web.wiz.security;

import inetsoft.sree.security.*;
import inetsoft.uql.util.Identity;
import inetsoft.util.Catalog;
import inetsoft.util.InvalidOrgException;
import inetsoft.web.admin.ai.AdminAiCallerGuard;
import inetsoft.web.admin.security.AuthenticationProviderService;
import inetsoft.web.admin.security.SecurityProviderStatus;
import inetsoft.web.admin.security.user.GetIdentityNameResponse;
import inetsoft.web.factory.DecodePathVariable;
import inetsoft.web.security.RequiredPermission;
import inetsoft.web.security.Secured;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

/**
 * wiz-broker-facing mirror of {@link inetsoft.web.admin.security.AuthenticationProviderController#getCurrentProvider}
 * and {@link inetsoft.web.admin.security.user.SecurityTreeController#getIdentities}, mapped under
 * {@code /api/wiz/**} so {@code WizServiceAuthenticationFilter} actually validates the caller's
 * bearer JWT (see {@code AbstractSecurityFilter#isWizRequest}, which only recognizes the
 * {@code wiz_auth} cookie or this prefix).
 *
 * <p>The original endpoints
 * ({@code /api/em/security/get-current-authentication-provider},
 * {@code /api/em/security/providers/{provider}/identities/{type}}) are the browser-session EM SPA
 * endpoints and are left untouched; wiz-services' cookie-less, server-to-server call needs its own
 * mapping rather than a widened authentication match on that generic, partly-mutating family (see
 * bug 76118 fix notes).
 *
 * <p>Delegates to the same {@link AuthenticationProviderService} methods the originals call, so
 * behavior is identical once a principal exists.
 */
@RestController
public class WizSecurityDirectoryController {
   @Autowired
   public WizSecurityDirectoryController(AuthenticationProviderService service,
                                         SecurityEngine securityEngine)
   {
      this.service = service;
      this.securityEngine = securityEngine;
   }

   /**
    * No {@code @Secured} here, mirroring the original: any authenticated caller may ask which
    * provider they themselves resolve under.
    */
   @GetMapping("/api/wiz/v1/security/current-provider")
   public SecurityProviderStatus getCurrentProvider(Principal principal) {
      AdminAiCallerGuard.requireBearerAuthenticatedRequest();
      return service.getCurrentProvider(principal);
   }

   @Secured(
      @RequiredPermission(
         resourceType = ResourceType.EM_COMPONENT,
         resource = "settings/security/users",
         actions = ResourceAction.ACCESS
      )
   )
   @GetMapping("/api/wiz/v1/security/providers/{provider}/identities/{type}")
   public GetIdentityNameResponse getIdentities(@DecodePathVariable("provider") String providerName,
                                                @PathVariable("type") int type,
                                                Principal principal)
   {
      AdminAiCallerGuard.requireBearerAuthenticatedRequest();

      IdentityID[] identityNames = {};
      String currOrgID = OrganizationManager.getInstance().getCurrentOrgID();

      if(securityEngine.getSecurityProvider().getOrganization(currOrgID) == null) {
         throw new InvalidOrgException(Catalog.getCatalog().getString("em.security.invalidOrganizationPassed"));
      }

      if(type == Identity.USER) {
         identityNames = service.getFilteredUsers(providerName, principal).toArray(new IdentityID[0]);
      }
      else if(type == Identity.GROUP) {
         identityNames = service.getFilteredGroups(providerName, principal).toArray(new IdentityID[0]);
      }
      else if(type == Identity.ROLE) {
         identityNames = service.getFilteredRoles(providerName, principal).toArray(new IdentityID[0]);
      }
      else if(type == Identity.ORGANIZATION) {
         identityNames = service.getFilteredOrganizations(providerName, principal).toArray(new IdentityID[0]);
      }

      return new GetIdentityNameResponse.Builder().identityNames(identityNames).build();
   }

   private final AuthenticationProviderService service;
   private final SecurityEngine securityEngine;
}
