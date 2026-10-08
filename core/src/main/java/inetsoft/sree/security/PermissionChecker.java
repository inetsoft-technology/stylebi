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

import inetsoft.sree.SreeEnv;
import inetsoft.uql.util.Identity;
import org.apache.commons.lang3.ArrayUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PermissionChecker.
 *
 * @hidden
 * @author InetSoft Technology Corp
 * @version 8.5
 */
public class PermissionChecker {
   /**
    * Construct.
    */
   public PermissionChecker(SecurityProvider provider) {
      this.provider = provider;
   }

   /**
    * Check the if an identity is on the permission list.
    */
   public boolean checkPermission(Identity identity, Permission permission,
                                  ResourceAction action, boolean recursive)
   {
      if(permission == null) {
         return false;
      }

      String orgID = identity != null ? identity.getOrganizationID() :
         OrganizationManager.getInstance().getCurrentOrgID();
      // An identity without an organization (e.g. a global role under a virtual role
      // principal in MV generation or a scheduled task) resolves andCondition from the
      // thread's current organization, which is where the EM permission save writes it
      // (host-org on single-tenant). Org-scoped identities keep using their own org.
      String andOrgID = orgID != null ? orgID :
         OrganizationManager.getInstance().getCurrentOrgID();
      boolean useAnd = isAndCondition(andOrgID);
      boolean userGroupPermission = checkUserGroupPermission(identity,
         permission, action, recursive, new HashSet<>());
      boolean organizationPermission = checkUserGroupOrganizationPermission(identity, permission, action);
      boolean rolePermission = checkRolePermission(identity, permission, action,
         recursive, new HashSet<>());
      boolean isUserGroupEmpty =
         isEmptyPermission(permission, Identity.USER, action, orgID) &&
         isEmptyPermission(permission, Identity.GROUP, action, orgID);
      boolean isRoleEmpty =
         isEmptyPermission(permission, Identity.ROLE, action, orgID);

      // Only read/write/delete type permission can call this. To other type
      // permission, if the two permission is empty, it will not call this but
      // use parent permission directly.
      if(isUserGroupEmpty && isRoleEmpty && !organizationPermission) {
         return false;
      }

      return  useAnd ? (isUserGroupEmpty || (userGroupPermission || organizationPermission)) &&
                       (isRoleEmpty || rolePermission) :
                       userGroupPermission || organizationPermission || rolePermission;
   }

   private boolean checkUserGroupOrganizationPermission(Identity identity,
                                                        Permission permission,
                                                        ResourceAction action)
   {
      if(identity == null || (identity.getType() != Identity.USER &&
         identity.getType() != Identity.ORGANIZATION &&
         identity.getType() != Identity.GROUP))
      {
         return false;
      }

      String orgId = identity.getOrganizationID();

      if(permission.check(identity, orgId, action)) {
         return true;
      }

      String organization;

      if(identity.getType() == Identity.ORGANIZATION)
      {
         organization = identity.getName();
      }
      else {
         organization = orgId != null ? provider.getOrgNameFromID(orgId) : null;

      }

      return permission.checkOrganization(new Permission.PermissionIdentity(organization, orgId), action);
   }

   private boolean checkUserGroupPermission(Identity identity,
                                            Permission permission,
                                            ResourceAction action, boolean recursive,
                                            Set<String> identitiesChecked)
   {
      if(identity == null || (identity.getType() != Identity.USER &&
         identity.getType() != Identity.GROUP))
      {
         return false;
      }

      identitiesChecked.add(getIdentityIdentifier(identity.getType(), identity.getName(),
                                                  identity.getOrganizationID()));

      String orgId = identity.getOrganizationID();

      if(permission.check(identity, orgId, action)) {
         return true;
      }

      boolean result = false;

      if(recursive) {
         String[] groups;

         if(identity.getType() == Identity.USER) {
            groups = identity.getGroups();
         }
         else {
            groups = identity.getGroups();
         }

         for(String groupName : groups) {
            if(identitiesChecked.contains(getIdentityIdentifier(Identity.GROUP, groupName,
                                                                identity.getOrganizationID())))
            {
               continue;
            }

            IdentityID groupID = new IdentityID(groupName, identity.getOrganizationID());
            Group group = provider.getGroup(groupID);
            group = group == null ? new Group(groupID) : group;
            result = checkUserGroupPermission(group, permission, action, true, identitiesChecked);

            if(result) {
               break;
            }
         }
      }

      return result;
   }

   private boolean checkRolePermission(Identity identity, Permission permission,
                                       ResourceAction action, boolean recursive,
                                       Set<String> identitiesChecked)
   {
      if(identity == null) {
         return false;
      }

      identitiesChecked.add(getIdentityIdentifier(identity.getType(), identity.getName(),
                                                  identity.getOrganizationID()));
      boolean isRole = identity.getType() == Identity.ROLE;

      if(isRole) {
         String orgId = identity.getOrganizationID();

         if(permission.check(identity, orgId, action)) {
            return true;
         }
      }

      boolean result = false;

      if(recursive) {
         IdentityID[] roles;
         String[] groups;

         if(identity.getType() == Identity.USER) {
            groups = identity.getGroups();
            roles = identity.getRoles();
            if(identity.getOrganizationID() != null && provider.getOrganization(identity.getOrganizationID()) != null) {
               IdentityID[] organizationRoles = provider.getOrganization(identity.getOrganizationID()).getRoles();
               roles = ArrayUtils.addAll(roles, organizationRoles);
            }
         }
         else if(identity.getType() == Identity.GROUP) {
            groups = identity.getGroups();
            roles = identity.getRoles();
            if(identity.getOrganizationID() != null && provider.getOrganization(identity.getOrganizationID()) != null) {
               IdentityID[] organizationRoles = provider.getOrganization(identity.getOrganizationID()).getRoles();
               roles = ArrayUtils.addAll(roles, organizationRoles);
            }
         }
         else {
            roles = identity.getRoles();
            groups = new String[0];
         }

         for(IdentityID roleID : roles) {
            if(identitiesChecked.contains(getIdentityIdentifier(Identity.ROLE, roleID.getName(),
                                                                roleID.getOrgID()))) {
               continue;
            }

            Role role = provider.getRole(roleID);
            role = role == null ? new Role(roleID) : role;

            result = checkRolePermission(role, permission, action, true, identitiesChecked);

            if(result) {
               break;
            }
         }

         if(!result) {
            for(String groupName : groups) {
               if(identitiesChecked.contains(getIdentityIdentifier(Identity.GROUP, groupName,
                                                                   identity.getOrganizationID())))
               {
                  continue;
               }

               IdentityID groupID = new IdentityID(groupName, identity.getOrganizationID());
               Group group = provider.getGroup(groupID);
               group = group == null ? new Group(groupID) : group;
               result = checkRolePermission(group, permission, action, true, identitiesChecked);

               if(result) {
                  break;
               }
            }
         }
      }

      return result;
   }

   /**
    * Check if the user/group or role is empty.
    */
   private boolean isEmptyPermission(Permission permission, int type, ResourceAction action, String orgId) {
      Set<IdentityID> identities;

      switch(type) {
      case Identity.USER:
         identities = permission.getOrgScopedUserGrants(action, orgId);
         break;
      case Identity.ROLE:
         identities = permission.getOrgScopedRoleGrants(action, orgId);
         break;
      case Identity.GROUP:
         identities = permission.getOrgScopedGroupGrants(action, orgId);
         break;
      case Identity.ORGANIZATION:
         identities = permission.getOrgScopedOrganizationGrants(action, orgId);
         break;
      default:
         return true;
      }

      if(identities != null && !identities.isEmpty()) {
         return false;
      }

      // a global role grant read back from JSON before Bug #77965 was fixed has the organization
      // "null" until FileAuthorizationProvider repairs it. It matches no role, but it still
      // restricts an AND condition, so a grant the repair had to keep doesn't fail open
      return type != Identity.ROLE || "null".equals(orgId) ||
         permission.getAllRoleGrants(action).stream()
            .noneMatch(grant -> "null".equals(grant.getOrganizationID()));
   }

   private String getIdentityIdentifier(int type, String identityName, String orgId) {
      return type + "-" + identityName + "-" + orgId;
   }

   /**
    * Gets whether permission.andCondition is enabled for the given organization. The value is
    * resolved from the organization's own property (inetsoft.org.&lt;orgID&gt;.permission.andCondition)
    * with a fallback to the global property, and cached per organization for AND_COND_TIMEOUT ms.
    * A single shared cache must not be used here because the property is org-scoped: it would
    * serve one organization's value to every other organization's permission checks.
    */
   private static boolean isAndCondition(String orgID) {
      String key = orgID == null ? "" : orgID;
      AndCondition cached = andCondCache.get(key);
      long now = System.currentTimeMillis();

      if(cached == null || now - cached.ts > AND_COND_TIMEOUT) {
         cached = new AndCondition(resolveAndCondition(orgID), now);
         andCondCache.put(key, cached);
      }

      return cached.value;
   }

   private static boolean resolveAndCondition(String orgID) {
      String value = null;

      if(orgID != null) {
         value = SreeEnv.getProperty(
            "inetsoft.org." + orgID + "." + AND_COND_PROPERTY, false, false);
      }

      if(value == null) {
         value = SreeEnv.getProperty(AND_COND_PROPERTY, false, false);
      }

      return "true".equals(value);
   }

   /**
    * Clears the cached permission.andCondition values so the next check re-reads them.
    */
   static void resetAndConditionCache() {
      andCondCache.clear();
   }

   private record AndCondition(boolean value, long ts) {
   }

   private SecurityProvider provider;
   private static final String AND_COND_PROPERTY = "permission.andCondition";
   private static final long AND_COND_TIMEOUT = 10000L;
   private static final Map<String, AndCondition> andCondCache = new ConcurrentHashMap<>();
}
