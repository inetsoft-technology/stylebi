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
package inetsoft.web.admin.security;

import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.web.security.*;

import java.security.Principal;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@DeniedMultiTenancyOrgUser
public class SSOSettingsController {
   @Autowired
   public SSOSettingsController(SSOSettingsService service) {
      this.service = service;
   }

   @GetMapping("/api/sso/settings")
   public SSOSettingsModel getSSOSettings(Principal principal) {
      final SAMLAttributesModel samlAttributesModel = service.buildSAMLModel();
      final OpenIdAttributesModel openIdAttributesModel = service.buildOpenIdModel();
      CustomSSOAttributesModel customModel = service.buildCustomModel();
      return new SSOSettingsModel.Builder()
         .samlAttributesModel(samlAttributesModel)
         .openIdAttributesModel(openIdAttributesModel)
         .customAttributesModel(customModel)
         .activeFilterType(service.getActiveFilterType())
         .roles(service.getRoles(principal))
         .selectedRoles(service.getSelectedRoles())
         .logoutUrl(service.getLogoutUrl())
         .logoutPath(service.getLogoutPath())
         .fallbackLogin(service.isFallbackLogin())
         .build();
   }

   @Secured(
      @RequiredPermission(
         resourceType = ResourceType.EM_COMPONENT,
         resource = "settings/security/sso",
         actions = ResourceAction.ACCESS
      )
   )
   @PostMapping("/api/sso/settings")
   public void updateSSOSettings(@RequestBody SSOSettingsModel model) {
      service.updateSSOSettings(model);
   }

   private final SSOSettingsService service;
}
