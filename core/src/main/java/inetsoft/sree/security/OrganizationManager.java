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
package inetsoft.sree.security;

import inetsoft.uql.XPrincipal;
import inetsoft.uql.util.XUtil;
import inetsoft.util.ThreadContext;

import java.security.Principal;
import java.util.Arrays;

/**
 * @version 10.1, 19/01/2009
 * @author InetSoft Technology Corp
 */
public class OrganizationManager {
   public static OrganizationManager getInstance() {
      if(instance == null) {
         try {
            instance = (OrganizationManager)
               Class.forName("inetsoft.enterprise.security.OrganizationManager")
                  .getConstructor().newInstance();
         }
         catch(Exception ex) {
            instance = new OrganizationManager();
         }
      }

      return instance;
   }

   public static String getCurrentOrgName() {
      return getInstance().getCurrentOrgName(null);
   }

   /**
    * Gets the groups for the user identified by the specified Principal.
    * @param user a Principal object that identifies the user.
    */
   public String getUserOrgId(Principal user) {
      return Organization.getDefaultOrganizationID();
   }

   public void setCurrentOrgID(String newOrgID) {
   }

   public String getCurrentOrgID() {
      XPrincipal principal = (XPrincipal) ThreadContext.getContextPrincipal();

      return getInstance().getCurrentOrgID(principal).toLowerCase();
   }

   public String getCurrentOrgID(Principal principal) {
      XPrincipal xPrincipal = (XPrincipal) principal;
      String orgID;

      if(principal != null) {
         orgID = xPrincipal.getProperty("curr_org_id") != null ?
            xPrincipal.getProperty("curr_org_id") : xPrincipal.getOrgId();
      }
      else {
         // If org ID isn't retrieved properly, users will write to the wrong storages.
         // Check if the org id is retrieved properly by throwing this exception
         // throw new RuntimeException("Could not get organization ID from principal");
         orgID = Organization.getDefaultOrganizationID();
      }

      return orgID;
   }

   public boolean isSiteAdmin(IdentityID userID) {
      SecurityProvider provider = SecurityEngine.getSecurity().getSecurityProvider();
      IdentityID[] userRoles = provider.getRoles(userID);
      return Arrays.stream(provider.getAllRoles(userRoles))
         .anyMatch(provider::isSystemAdministratorRole);
   }

   public boolean isSiteAdmin(AuthenticationProvider provider, IdentityID userID) {
      IdentityID[] userRoles = provider.getRoles(userID);
      return Arrays.stream(provider.getAllRoles(userRoles))
         .anyMatch(provider::isSystemAdministratorRole);
   }

   public boolean isSiteAdmin(Principal principal) {
      SecurityProvider provider = SecurityEngine.getSecurity().getSecurityProvider();

      if(principal == null) {
         return false;
      }

      IdentityID[] roles = ((XPrincipal) principal).getRoles();
      User user = provider.getUser(IdentityID.getIdentityIDFromKey(principal.getName()));

      for(IdentityID id : roles) {
         if(provider.isSystemAdministratorRole(id)) {
            return true;
         }

      }

      if(user == null) {
         return false;
      }
      roles = user.getRoles();
      IdentityID[] allRoles = provider.getAllRoles(roles);

      for(IdentityID id : allRoles) {
         if(provider.isSystemAdministratorRole(id)) {
            return true;
         }
      }

      User pUser = provider.getUser(IdentityID.getIdentityIDFromKey(principal.getName()));
      if(pUser != null) {
         return isSiteAdmin(pUser.getIdentityID());
      }

      return false;
   }

   public boolean isOrgAdmin(IdentityID userID) {
      SecurityProvider provider = SecurityEngine.getSecurity().getSecurityProvider();
      IdentityID[] userRoles = provider.getRoles(userID);
      return Arrays.stream(provider.getAllRoles(userRoles))
         .anyMatch(provider::isOrgAdministratorRole);   }

   public boolean isOrgAdmin(Principal principal) {
      if(principal == null) {
         return false;
      }

      SecurityProvider provider = SecurityEngine.getSecurity().getSecurityProvider();
      AuthenticationProvider authentication = provider.getAuthenticationProvider();
      IdentityID pId = IdentityID.getIdentityIDFromKey(principal.getName());
      IdentityID[] roles = ((XPrincipal) principal).getRoles();

      for(IdentityID id : roles) {
         if(provider.isOrgAdministratorRole(id)) {
            return true;
         }
      }

      for(IdentityID roleIdentityId : authentication.getRoles(pId)) {
         if(authentication.isOrgAdministratorRole(roleIdentityId)) {
            return true;
         }
      }

      User pUser = provider.getUser(IdentityID.getIdentityIDFromKey(principal.getName()));
      if(pUser != null) {
         return isOrgAdmin(pUser.getIdentityID());
      }

      return false;
   }

   public String getCurrentOrgName(Principal principal) {
      XPrincipal xPrincipal = principal == null ? (XPrincipal) ThreadContext.getPrincipal()
         : (XPrincipal) principal;
      xPrincipal = xPrincipal == null ? (XPrincipal) ThreadContext.getContextPrincipal() : xPrincipal;
      String orgID, providerName;

      if(xPrincipal != null) {
         orgID = xPrincipal.getProperty("curr_org_id") != null ?
            xPrincipal.getProperty("curr_org_id") : xPrincipal.getOrgId();
         providerName = xPrincipal.getProperty("curr_provider_name");
      }
      else {
         //returns default if principal == null
         return Organization.getDefaultOrganizationName();
      }

      String orgName = null;
      AuthenticationProvider provider = XUtil.getSecurityProvider(providerName);

      if(provider != null) {
         for(String org : provider.getOrganizations()) {
            if(orgID.equals(provider.getOrganization(org).getOrganizationID())) {
               orgName = org;
            }
         }
      }

      return orgName != null ? orgName : Organization.getDefaultOrganizationName();   }

   public Organization getOrganization() {
      OrganizationCache cache = OrganizationCache.getInstance();
      return cache == null ? null : cache.getOrganization();
   }

   public void reset() {
   }

   private static OrganizationManager instance;
}
