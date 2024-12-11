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
            String childPath = path == null || path.isEmpty() ? child.name() :
               path + "/" + child.name();

            builder.putPermissions(child.name(), componentAvailable(path, child, principal) &&
               anyChildAvailable(childPath, child, principal));
            builder.putLabels(child.name(), child.label());
         }
      }

      return builder.build();
   }

   private boolean anyChildAvailable(String parentPath, ViewComponent parent,
                                     Principal principal)
   {
      if(parentPath != null && parent.children().isEmpty()) {
         return true;
      }

      for(ViewComponent child : parent.children().values()) {
         if(componentAvailable(parentPath, child, principal)) {
            return true;
         }
      }

      return false;
   }

   private boolean componentAvailable(String parentPath, ViewComponent component, Principal principal) {
      String resource = parentPath == null || parentPath.isEmpty() ? component.name() :
         parentPath + "/" + component.name();
      boolean authorized = component.available() && checkPermission(resource, principal);
      boolean multiTenancyHidden = false;

      if(component.hiddenForMultiTenancy() && SUtil.isMultiTenant()) {
         multiTenancyHidden = !OrganizationManager.getInstance().isSiteAdmin(principal);
      }

      return authorized && !multiTenancyHidden;
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
            "settings/security/sso".equals(resource) ||
            "settings/security/googleSignIn".equals(resource)) && !enterprise)
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
