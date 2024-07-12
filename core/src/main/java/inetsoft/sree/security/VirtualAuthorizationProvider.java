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
package inetsoft.sree.security;

import inetsoft.uql.XPrincipal;

import java.util.*;

/**
 * Virtual authorization module.
 *
 * @author InetSoft Technology
 * @since  8.5
 */
public class VirtualAuthorizationProvider extends AbstractAuthorizationProvider {
   public VirtualAuthorizationProvider() {
      userPermission = new Permission();
      adminPermission = new Permission();
      List<String> userList = Arrays.asList(XPrincipal.ANONYMOUS, "admin", XPrincipal.SYSTEM);
      Set<String> users = new HashSet<>(userList);
      Set<String> admins = new HashSet<>(Arrays.asList("admin", XPrincipal.SYSTEM));
      String orgId = OrganizationManager.getInstance().getCurrentOrgID();

      for(ResourceAction action : ResourceAction.values()) {
         if(action == ResourceAction.ADMIN || action == ResourceAction.ASSIGN) {
            userPermission.setUserGrantsForOrg(action, admins, orgId);
         }
         else {
            userPermission.setUserGrantsForOrg(action, users, orgId);
         }

         adminPermission.setUserGrantsForOrg(action, admins, orgId);
      }
   }

   @Override
   public Permission getPermission(ResourceType type, String resource) {
      if(type == ResourceType.EM || type == ResourceType.EM_COMPONENT ||
         type == ResourceType.REPORT && resource.startsWith("Built-in Admin Reports"))
      {
         return adminPermission;
      }

      return userPermission;
   }

   @Override
   public Permission getPermission(ResourceType type, IdentityID resource) {
      return userPermission;
   }

   /**
    * Tear down the security provider.
    */
   @Override
   public void tearDown() {
   }

   /**
    * Signals that a security object has been removed or renamed.
    *
    * @param event the object that describes the change event.
    */
   @Override
   public void authenticationChanged(AuthenticationChangeEvent event) {
   }

   @Override
   public String getProviderName() {
      return getClass().getSimpleName();
   }

   @Override
   public void setProviderName(String providerName) {
   }

   private final Permission userPermission;
   private final Permission adminPermission;
}
