/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.admin.authz;

import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.SecurityException;
import inetsoft.sree.security.*;

import java.security.Principal;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
public class ComponentAuthorizationController {
   @Autowired
   public ComponentAuthorizationController(SecurityEngine securityEngine,
                                           ComponentAuthorizationService componentService)
   {
      this.securityEngine = securityEngine;
      this.componentService = componentService;
   }

   @GetMapping("/api/em/authz")
   public ComponentAuthorizationMessage getPermissions(@RequestParam("path") String path,
                                                       Principal principal)
   {
      ComponentAuthorizationMessage.Builder builder = ComponentAuthorizationMessage.builder();
      ViewComponent component = componentService.getComponent(path);

      if(component != null) {
         for(ViewComponent child : component.children().values()) {
            String resource = path == null || path.isEmpty() ? child.name() :
               path + "/" + child.name();
            boolean authorized = child.available() && checkPermission(resource, principal);
            boolean multiTenancyHidden = false;

            if(child.hiddenForMultiTenancy() && SUtil.isMultiTenant()) {
               multiTenancyHidden = !OrganizationManager.getInstance().isSiteAdmin(principal);
            }

            builder.putPermissions(child.name(), authorized && !multiTenancyHidden);
            builder.putLabels(child.name(), child.label());

            if(multiTenancyHidden) {
               builder.putMultiTenancyHiddenComponents(child.name(), true);
            }
         }
      }

      return builder.build();
   }

   private boolean checkPermission(String resource, Principal principal) {
      boolean authorized = false;
      boolean enterprise = LicenseManager.getInstance().isEnterprise();

      try {
         if(securityEngine.checkPermission(
            principal, ResourceType.EM_COMPONENT, resource, ResourceAction.ACCESS)) {
            authorized = true;
         }

         if(authorized && "settings/content/materialized-views".equals(resource)) {
            authorized = securityEngine.checkPermission(
               principal, ResourceType.MATERIALIZATION, "*", ResourceAction.ACCESS);
         }
         else if(authorized && ("settings/schedule/tasks".equals(resource) ||
            "settings/schedule/cycles".equals(resource))) {
            // Don't allow access to the tasks tab if they don't have the general scheduler
            // permission. Not a real-world use case, but the testers check it.
            authorized = securityEngine.checkPermission(
               principal, ResourceType.SCHEDULER, "*", ResourceAction.ACCESS);
         }
         else if(("settings/presentation/themes".equals(resource) ||
            "settings/security/sso".equals(resource)) && !enterprise)
         {
            authorized = false;
         }
      }
      catch(SecurityException ignore) { // NOSONAR
      }

      return authorized;
   }

   private final SecurityEngine securityEngine;
   private final ComponentAuthorizationService componentService;
}
