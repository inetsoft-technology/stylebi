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

import inetsoft.sree.internal.SUtil;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.util.*;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Principal;
import java.util.HashSet;
import java.util.Set;

/**
 * SRIdentityFinder, the finder in sree module for identities like User, Group,
 * Role, etc.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class SRIdentityFinder implements XIdentityFinder {
   /**
    * Get the roles for the user identified by the specified Principal.
    *
    * @param user a Principal object that identifies the user.
    *
    * @return an array of role names.
    */
   @Override
   public IdentityID[] getUserRoles(Principal user) {
      if(!(user instanceof XPrincipal)) {
         return new IdentityID[0];
      }

      if(user instanceof SRPrincipal) {
         User id = ((SRPrincipal) user).createUser();

         if(id != null) {
            return id.getRoles();
         }
      }

         return getRoles(SUtil.getExecuteIdentity(user));
   }

   /**
    * Get the roles for the identity.
    */
   public IdentityID[] getRoles(Identity identity) {
      SecurityProvider provider = null;

      try {
         provider = SecurityEngine.getSecurity().getSecurityProvider();
      }
      catch(Exception ex) {
         LOG.error("Failed to list roles, security provider not available", ex);
      }

      if(provider == null) {
         return new IdentityID[0];
      }

      Set<IdentityID> set = new HashSet<>();
      getRoles(identity, set, provider);
      return set.toArray(new IdentityID[set.size()]);
   }

   /**
    * Get the groups for the user identified by the specified Principal.
    * @param user a Principal object that identifies the user.
    * @return an array of group names.
    */
   @Override
   public String[] getUserGroups(Principal user) {
      if(!(user instanceof XPrincipal)) {
         return new String[0];
      }

      if(user instanceof SRPrincipal) {
         User id = ((SRPrincipal) user).createUser();

         if(id != null) {
            return id.getGroups();
         }
      }

      return getGroups(SUtil.getExecuteIdentity(user));
   }

   /**
    * Return the specified group is the parent group of the identity.
    */
   public boolean isParentGroup(Identity identity, String group) {
      SecurityProvider provider = null;

      try {
         provider = SecurityEngine.getSecurity().getSecurityProvider();
      }
      catch(Exception ex) {
         LOG.error("Failed determine if group " + group + " is parent of " +
            identity + ", security provider not available", ex);
      }

      if(provider != null) {
         return isParentGroup(identity, group, provider);
      }

      return false;
   }

   /**
    * Return the specified group is the parent group of the identity.
    */
   public boolean isParentGroup(Identity identity, String group,
                                SecurityProvider provider) {
      identity = provider.findIdentity(identity);

      if(identity == null) {
         return false;
      }

      for(String agroup : identity.getGroups()) {
         if(agroup.equals(group) ||
            isParentGroup(new Group(new IdentityID(agroup, identity.getOrganization())), group, provider))
         {
            return true;
         }
      }

      return false;
   }

   /**
    * Get the groups for the identity.
    */
   public String[] getGroups(Identity identity) {
      SecurityProvider provider = null;

      try {
         provider = SecurityEngine.getSecurity().getSecurityProvider();
      }
      catch(Exception ex) {
         LOG.error("Failed to get the groups to which " + identity +
            " belongs, security provider not available", ex);
      }

      if(provider == null) {
         return new String[0];
      }

      Set<String> set = new HashSet<>();
      getGroups(identity, set, provider);
      return set.toArray(new String[set.size()]);
   }

   /**
    * Get roles.
    * @param identity the specified identity.
    * @param set the specified role container.
    * @param provider the specified security provider.
    */
   private void getRoles(Identity identity, Set<IdentityID> set,
                         SecurityProvider provider)
   {
      identity = provider.findIdentity(identity);

      if(identity == null) {
         return;
      }

      if(identity.getType() == Identity.ROLE) {
         if(set.contains(identity.getIdentityID()) ) {
            return;
         }

         set.add(identity.getIdentityID());
      }

      IdentityID[] roles = identity.getRoles();

      for(int i = 0; roles != null && i < roles.length; i++) {
         getRoles(new DefaultIdentity(roles[i], Identity.ROLE), set, provider);
      }

      String[] groups = identity.getGroups();

      for(int i = 0; groups != null && i < groups.length; i++) {
         getRoles(new DefaultIdentity(groups[i], OrganizationManager.getCurrentOrgName(),
            Identity.GROUP), set, provider);
      }
   }

   /**
    * Get groups.
    * @param identity the specified identity.
    * @param set the specified group container.
    * @param provider the specified security provider.
    */
   private void getGroups(Identity identity, Set<String> set,
                          SecurityProvider provider)
   {
      identity = provider.findIdentity(identity);

      if(identity == null) {
         return;
      }

      if(identity.getType() == Identity.GROUP) {
         if(set.contains(identity.getName())) {
            return;
         }

         set.add(identity.getName());
      }

      String[] groups = identity.getGroups();

      for(int i = 0; groups != null && i < groups.length; i++) {
         getGroups(new DefaultIdentity(groups[i], OrganizationManager.getCurrentOrgName(),
            Identity.GROUP), set, provider);
      }
   }

   @Override
   public String getUserOrganizationId(Principal user) {
      return user == null ? null : getOrgId(IdentityID.getIdentityIDFromKey(user.getName()).organization);
   }
//
//   public String getOrganization(String orgName) {
//      SecurityProvider provider = null;
//
//      try {
//         provider = SecurityEngine.getSecurity().getSecurityProvider();
//      }
//      catch(Exception ex) {
//         LOG.error("Failed to list users, security provider not available", ex);
//      }
//
//      if(provider == null || orgName == null) {
//         return Organization.getDefaultOrganizationName();
//      }
//
//      return provider.getUser(orgName).getOrganization() == null ?
//               Organization.getDefaultOrganizationName() :
//               provider.getUser(orgName).getOrganization();
//   }

   public String getOrgId(String orgName) {
      SecurityProvider provider = null;

      try {
         provider = SecurityEngine.getSecurity().getSecurityProvider();
      }
      catch(Exception ex) {
         LOG.error("Failed to list users, security provider not available", ex);
      }

      if(provider == null || orgName == null) {
         return null;
      }

      return provider.getOrganization(orgName).getId();
   }

   /**
    * Get all the users.
    */
   @Override
   public IdentityID[] getUsers() {
      SecurityProvider provider = null;

      try {
         provider = SecurityEngine.getSecurity().getSecurityProvider();
      }
      catch(Exception ex) {
         LOG.error("Failed to list users, security provider not available", ex);
      }

      if(provider == null) {
         return new IdentityID[] {new IdentityID("anonymous", Organization.getDefaultOrganizationName())};
      }

      return provider.getUsers();
   }

   /**
    * Create principal for the specified identity.
    */
   @Override
   public XPrincipal create(Identity id) {
      String addr = null;

      try {
         addr = Tool.getIP();
      }
      catch(Exception ex) {
         // ignore it
      }

      return SUtil.getPrincipal(id, addr, true);
   }

   /**
    * Check whether security exists.
    */
   @Override
   public boolean isSecurityExisting() {
      try {
         return !SecurityEngine.getSecurity().getSecurityProvider().isVirtual();
      }
      catch(Exception ex) {
         LOG.error("Failed to determine if security is enabled, security provider " +
            "not available", ex);
      }

      return false;
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(SRIdentityFinder.class);
}
