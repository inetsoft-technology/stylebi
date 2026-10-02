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

import inetsoft.sree.internal.SUtil;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.util.XUtil;
import inetsoft.util.ThreadContext;
import inetsoft.util.Tool;

import java.security.Principal;
import java.util.*;
import java.util.concurrent.Callable;

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
      String orgID = null;

      if(principal != null) {
         orgID = xPrincipal.getCurrentOrgId();
      }

      // Check OrganizationContextHolder before falling back to default.
      // This is needed for cluster calls where the principal may be null
      // but OrganizationContextHolder has been set via SwitchOrgAspectTask.
      if(Tool.isEmptyString(orgID)) {
         orgID = OrganizationContextHolder.getCurrentOrgId();
      }

      if(Tool.isEmptyString(orgID)) {
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

      if(isStoredUserPrincipal(principal)) {
         return Arrays.stream(getStoredUserRoles(provider, principal))
            .anyMatch(provider::isSystemAdministratorRole);
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

      if(isStoredUserPrincipal(principal)) {
         // The organization administrator role is hidden when multi-tenancy is disabled, the
         // same as in XPrincipal.getAllRoles(). It is removed after the parent roles are added,
         // so it is not inherited through a child role either.
         boolean multiTenant = SUtil.isMultiTenant();
         return Arrays.stream(getStoredUserRoles(provider, principal))
            .filter(role -> multiTenant || !"Organization Administrator".equals(role.name))
            .anyMatch(provider::isOrgAdministratorRole);
      }

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

   /**
    * Determines if the admin checks for a principal must be decided from the stored user
    * rather than from the principal's own roles. This is the case for a principal created by
    * a login against the security provider, whose roles are a snapshot taken at login and are
    * not updated when a group or role that the user belongs to is changed (Bug #77199). SSO
    * principals, whose roles are asserted by the identity provider, and virtual principals
    * that represent a group or role instead of a user keep using the principal's roles.
    */
   private static boolean isStoredUserPrincipal(Principal principal) {
      return principal instanceof XPrincipal xPrincipal &&
         "true".equals(xPrincipal.getProperty("__internal__")) &&
         !"true".equals(xPrincipal.getProperty("virtual"));
   }

   /**
    * Gets all the roles of the stored user that is identified by the principal: the user's own
    * roles, the roles of the user's groups and their parent groups and the roles of the user's
    * organization, including all parent roles. These are read from the provider's user, group,
    * role and organization storage and not from the per-node user role cache used by
    * {@link AuthenticationProvider#getRoles(IdentityID)}, which is not updated on other cluster
    * nodes or when a user is removed.
    *
    * <p>The user, groups and organization are read from the provider in the authentication chain
    * that contains the user, the same provider that authenticates the user. They are not read
    * through the chain, because a provider ahead of it may return a group or organization with the
    * same name that has no roles, e.g. LDAP returns a group for any name.
    *
    * @return the roles or an empty array if the user no longer exists.
    */
   private static IdentityID[] getStoredUserRoles(SecurityProvider provider, Principal principal) {
      IdentityID userID = IdentityID.getIdentityIDFromKey(principal.getName());
      AuthenticationProvider userProvider = provider;
      User user = null;

      if(provider.getAuthenticationProvider() instanceof AuthenticationChain chain) {
         for(AuthenticationProvider child : chain.getProviders()) {
            user = child.getUser(userID);

            if(user != null) {
               userProvider = child;
               break;
            }
         }
      }

      if(user == null) {
         // not a chain, or a user from the external user provider
         user = provider.getUser(userID);
      }

      if(user == null) {
         return new IdentityID[0];
      }

      String orgID = user.getOrganizationID();
      Set<IdentityID> roles = new HashSet<>();

      if(user.getRoles() != null) {
         roles.addAll(Arrays.asList(user.getRoles()));
      }

      if(user.getGroups() != null) {
         // same walk as AuthenticationProvider.getAllGroups(), collecting the roles as it goes
         Set<IdentityID> groups = new HashSet<>();
         Deque<IdentityID> queue = new ArrayDeque<>();

         for(String group : user.getGroups()) {
            queue.addLast(new IdentityID(group, orgID));
         }

         while(!queue.isEmpty()) {
            IdentityID groupID = queue.removeFirst();

            if(groups.add(groupID)) {
               Group group = userProvider.getGroup(groupID);

               if(group != null) {
                  if(group.getRoles() != null) {
                     roles.addAll(Arrays.asList(group.getRoles()));
                  }

                  if(group.getGroups() != null) {
                     for(String parent : group.getGroups()) {
                        queue.addLast(new IdentityID(parent, group.getOrganizationID()));
                     }
                  }
               }
            }
         }
      }

      Organization organization = orgID == null ? null : userProvider.getOrganization(orgID);

      if(organization != null && organization.getRoles() != null) {
         roles.addAll(Arrays.asList(organization.getRoles()));
      }

      return provider.getAllRoles(roles.toArray(new IdentityID[0]));
   }

   public List<IdentityID> orgAdminUsers(String orgID) {
      SecurityEngine security = SecurityEngine.getSecurity();
      IdentityID[] identityIDS = security.getOrgUsers(orgID);
      List<IdentityID> userIDs = new ArrayList<>();

      for(IdentityID id : identityIDS) {
         if(isOrgAdmin(id)) {
            userIDs.add(id);
         }
      }

      return userIDs;
   }

   public String getCurrentOrgName(Principal principal) {
      XPrincipal xPrincipal = principal == null ? (XPrincipal) ThreadContext.getPrincipal()
         : (XPrincipal) principal;
      xPrincipal = xPrincipal == null ? (XPrincipal) ThreadContext.getContextPrincipal() : xPrincipal;
      String orgID, providerName;

      if(xPrincipal != null) {
         orgID = xPrincipal.getCurrentOrgId();
         providerName = xPrincipal.getProperty("curr_provider_name");
      }
      else {
         //returns default if principal == null
         return Organization.getDefaultOrganizationName();
      }

      String orgName = null;
      AuthenticationProvider provider = XUtil.getSecurityProvider(providerName);

      if(provider != null) {
        orgName = provider.getOrganization(orgID).getName();
      }

      return orgName != null ? orgName : Organization.getDefaultOrganizationName();
   }

   public Organization getOrganization() {
      OrganizationCache cache = OrganizationCache.getInstance();
      return cache == null ? null : cache.getOrganization();
   }

   public void reset() {
   }

   public static <T> T runInOrgScope(String orgID, Callable<T> supplier) throws Exception {
      T result;
      String originalOrg = OrganizationContextHolder.getCurrentOrgId();

      try {
         OrganizationContextHolder.setCurrentOrgId(orgID);
         result = supplier.call();
      }
      finally {
         OrganizationContextHolder.setCurrentOrgId(originalOrg);
      }

      return result;
   }


   public static String getGlobalDefOrgFolderName() {
      return Organization.getDefaultOrganizationName() + " Global Repository";
   }

   private static OrganizationManager instance;
}
