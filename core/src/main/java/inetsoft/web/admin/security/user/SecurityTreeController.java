/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.admin.security.user;

import inetsoft.sree.security.IdentityID;
import inetsoft.uql.util.Identity;
import inetsoft.web.admin.security.AuthenticationProviderService;
import inetsoft.web.factory.DecodePathVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
public class SecurityTreeController {
   @Autowired
   public SecurityTreeController(AuthenticationProviderService service,
                                 IdentityThemeService themeService)
   {
      this.service = service;
      this.themeService = themeService;
   }

   @GetMapping("/api/em/security/providers/{provider}/identities/{type}")
   public GetIdentityNameResponse getIdentities(@DecodePathVariable("provider") String providerName,
                                                @PathVariable("type") int type,
                                                Principal principal)
   {
      IdentityID[] identityNames = {};

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

   @GetMapping("/api/em/security/themes")
   public IdentityThemeList getThemes() {
      return themeService.getThemes();
   }

   private final AuthenticationProviderService service;
   private final IdentityThemeService themeService;
}
