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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.WritableTypeId;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import inetsoft.uql.util.Identity;
import inetsoft.util.Tool;
import inetsoft.util.XMLSerializable;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Default implementation of permission. A permission contains list of
 * users and roles with permission to read, write, or delete.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
@JsonSerialize(using = Permission.Serializer.class)
@JsonDeserialize(using = Permission.Deserializer.class)
public class Permission implements Serializable, Cloneable, XMLSerializable {
   /**
    * Gets the users that have been granted permission to perform the specified action.
    *
    * @param action the granted action.
    *
    * @return a set containing the names of the users that have been granted the permission.
    */
   public Set<PermissionIdentity> getUserGrants(ResourceAction action) {
      return getGrants(action, Identity.USER);
   }

   public Set<PermissionIdentity> getUserGrants(ResourceAction action, String orgID) {
      return getGrants(action, Identity.USER, orgID);
   }

   public Set<PermissionIdentity> getAllUserGrants(ResourceAction action) {
      return getGrants(action, Identity.USER, null);
   }

   /**
    * Gets the users under the given orgid that have been granted permission to perform the specified action.
    *
    * @param action the granted action.
    * @param orgId the organizationId to search under.
    *
    * @return a set containing the names of the users that have been granted the permission.
    */
   public Set<IdentityID> getOrgScopedUserGrants(ResourceAction action, String orgId) {
      String thisOrgID = orgId == null ? globalOrgId : orgId;
      return getUserGrants(action, orgId).stream()
         .filter(pId -> thisOrgID.equals(pId.organizationID) ||
                        globalOrgId.equals(pId.organizationID))
         .map(pI -> new IdentityID(pI.name, thisOrgID))
         .collect(Collectors.toSet());
   }

   /**
    * Gets the users under the given orgid that have been granted permission to perform the specified action.
    *
    * @param action the granted action.
    * @param organization the organization to search under.
    *
    * @return a set containing the names of the users that have been granted the permission.
    */
   public Set<IdentityID> getOrgScopedUserGrants(ResourceAction action, Organization organization) {
      String thisOrgID = organization == null ? globalOrgId : organization.getId();
      return getAllUserGrants(action).stream()
         .filter(pId -> thisOrgID.equals(pId.organizationID) ||
            globalOrgId.equals(pId.organizationID))
         .map(pI -> new IdentityID(pI.name, thisOrgID))
         .collect(Collectors.toSet());
   }

   /**
    * Sets the users that have been granted permission to perform the specified action.
    *
    * @param action the granted action.
    * @param users  the users that are granted the permission. The collection is copied, so any
    *               subsequent changes to it do not affect this object.
    */
   public void setUserGrants(ResourceAction action, Set<PermissionIdentity> users) {
      setGrants(action, Identity.USER, users);
   }

   public void setUserGrantsForOrg(ResourceAction action, Set<String> users, String orgId) {
      setUserGrantsForOrg(action, users, orgId, orgId);
   }

   public void setUserGrantsForOrg(ResourceAction action, Set<String> users, String oldOrgId,
                                   String newOrgId)
   {
      oldOrgId = oldOrgId == null ? newOrgId : oldOrgId;
      final String org = oldOrgId == null ? globalOrgId : oldOrgId;
      Set<PermissionIdentity> userGrants = getAllUserGrants(action);
      Set<PermissionIdentity> updatedGrants = userGrants.stream()
         .filter(pId -> !org.equals(pId.organizationID))
         .collect(Collectors.toSet());

      for(String u : users) {
         updatedGrants.add(new PermissionIdentity(u, newOrgId));
      }

      setGrants(action, Identity.USER, updatedGrants);
   }

   /**
    * Gets the roles that have been granted permission to perform the specified action.
    *
    * @param action the granted action.
    *
    * @return a set containing the names of the roles that have been granted the permission.
    */
   public Set<PermissionIdentity> getRoleGrants(ResourceAction action) {
      return getGrants(action, Identity.ROLE);
   }

   public Set<PermissionIdentity> getAllRoleGrants(ResourceAction action) {
      return getGrants(action, Identity.ROLE, null);
   }


   public Set<IdentityID> getOrgScopedRoleGrants(ResourceAction action, String orgId) {
      String thisOrgID = orgId == null ? globalOrgId : orgId;
      return getGrants(action, Identity.ROLE, thisOrgID).stream()
         .filter(pId -> thisOrgID.equals(pId.organizationID) ||
                        globalOrgId.equals(pId.organizationID)|| Tool.equals(null,pId.organizationID))
         .map(pI -> new IdentityID(pI.name, globalOrgId.equals(pI.organizationID) ||
                                          Tool.equals(null,pI.organizationID) ? null : thisOrgID))
         .collect(Collectors.toSet());
   }

   public Set<IdentityID> getOrgScopedRoleGrants(ResourceAction action, Organization organization) {
      String thisOrgID = organization == null ? globalOrgId : organization.getOrganizationID();
      return getAllRoleGrants(action).stream()
         .filter(pId -> thisOrgID.equals(pId.organizationID) ||
            globalOrgId.equals(pId.organizationID))
         .map(pI -> new IdentityID(pI.name, globalOrgId.equals(pI.organizationID) ? null : thisOrgID))
         .collect(Collectors.toSet());
   }

   /**
    * Sets the roles that have been granted permission to perform the specified action.
    *
    * @param action the granted action.
    * @param roles  the roles that are granted the permission. The collection is copied, so any
    *               subsequent changes to it do not affect this object.
    */
   public void setRoleGrants(ResourceAction action, Set<PermissionIdentity> roles) {
      setGrants(action, Identity.ROLE, roles);
   }

   /**
    * Sets the roles of the given organization that have been granted permission to perform the specified action.
    *
    * @param action the granted action.
    * @param roles  the roles that are granted the permission. The collection is copied, so any
    *               subsequent changes to it do not affect this object.
    * @param orgId the organizationId to set these roles under
    */
   public void setRoleGrantsForOrg(ResourceAction action, Set<String> roles, String orgId) {
      setRoleGrantsForOrg(action, roles, orgId, orgId);
   }

   public void setRoleGrantsForOrg(ResourceAction action, Set<String> roles, String oldOrgId,
                                   String newOrgId)
   {
      oldOrgId = oldOrgId == null ? newOrgId : oldOrgId;
      final String org = oldOrgId;
      Set<PermissionIdentity> roleGrants = getAllRoleGrants(action);
      Set<PermissionIdentity> updatedGrants = roleGrants.stream()
         .filter(pId -> !Tool.equals(org, pId.organizationID))
         .collect(Collectors.toSet());

      for(String r : roles) {
         updatedGrants.add(new PermissionIdentity(r, newOrgId));
      }

      setGrants(action, Identity.ROLE, updatedGrants);
   }

   /**
    * Gets the groups that have been granted permission to perform the specified action.
    *
    * @param action the granted option.
    *
    * @return a set containing the names of the groups that have been granted the permission.
    */
   public Set<PermissionIdentity> getGroupGrants(ResourceAction action) {
      return getGrants(action, Identity.GROUP);
   }

   public Set<PermissionIdentity> getGroupGrants(ResourceAction action, String orgID) {
      return getGrants(action, Identity.GROUP, orgID);
   }

   public Set<PermissionIdentity> getAllGroupGrants(ResourceAction action) {
      return getGrants(action, Identity.GROUP, null);
   }

   /**
    * Gets the groups under the given organization that have been granted permission to perform the specified action.
    *
    * @param action the granted option.
    * @param orgId the organizationId to search.
    *
    * @return a set containing the names of the groups that have been granted the permission.
    */
   public Set<IdentityID> getOrgScopedGroupGrants(ResourceAction action, String orgId) {
      String thisOrgID = orgId == null ? globalOrgId : orgId;
      return getGroupGrants(action, orgId).stream()
         .filter(pId -> thisOrgID.equals(pId.organizationID) ||
                        globalOrgId.equals(pId.organizationID))
         .map(pI -> new IdentityID(pI.name, thisOrgID))
         .collect(Collectors.toSet());
   }

   /**
    * Gets the groups under the given organization that have been granted permission to perform the specified action.
    *
    * @param action the granted option.
    * @param organization the organization to search.
    *
    * @return a set containing the names of the groups that have been granted the permission.
    */
   public Set<IdentityID> getOrgScopedGroupGrants(ResourceAction action, Organization organization) {
      String thisOrgID = organization == null ? globalOrgId : organization.getOrganizationID();
      return getAllGroupGrants(action).stream()
         .filter(pId -> thisOrgID.equals(pId.organizationID) ||
            globalOrgId.equals(pId.organizationID))
         .map(pI -> new IdentityID(pI.name, thisOrgID))
         .collect(Collectors.toSet());
   }

   /**
    * Sets the groups that have been granted permission to perform the specified action.
    *
    * @param action the granted action.
    * @param groups the groups that are granted the permission. The collection is copied, so any
    *               subsequent changes to it do not affect this object.
    */
   public void setGroupGrants(ResourceAction action, Set<PermissionIdentity> groups) {
      setGrants(action, Identity.GROUP, groups);
   }

   /**
    * Sets the groups for the given org that have been granted permission to perform the specified action.
    *
    * @param action the granted action.
    * @param groups the groups that are granted the permission. The collection is copied, so any
    *               subsequent changes to it do not affect this object.
    * @param orgId the organizationId to set the groups under
    */
   public void setGroupGrantsForOrg(ResourceAction action, Set<String> groups, String orgId) {
      setGroupGrantsForOrg(action, groups, orgId, orgId);
   }

   public void setGroupGrantsForOrg(ResourceAction action, Set<String> groups, String oldOrgId,
                                    String newOrgId)
   {
      oldOrgId = oldOrgId == null ? newOrgId : oldOrgId;
      final String org = oldOrgId == null ? globalOrgId : oldOrgId;
      Set<PermissionIdentity> groupGrants = getAllGroupGrants(action);
      Set<PermissionIdentity> updatedGrants = groupGrants.stream()
         .filter(pId -> !org.equals(pId.organizationID))
         .collect(Collectors.toSet());

      for(String g : groups) {
         updatedGrants.add(new PermissionIdentity(g, newOrgId));
      }

      setGrants(action, Identity.GROUP, updatedGrants);
   }

   /**
    * Gets the organizations that have been granted permission to perform the specified action.
    *
    * @param action the granted option.
    *
    * @return a set containing the names of the organizations that have been granted the permission.
    */
   public Set<PermissionIdentity> getOrganizationGrants(ResourceAction action) {
      return getGrants(action, Identity.ORGANIZATION);
   }

   public Set<PermissionIdentity> getOrganizationGrants(ResourceAction action, String orgID) {
      return getGrants(action, Identity.ORGANIZATION, orgID);
   }

   public Set<PermissionIdentity> getAllOrganizationGrants(ResourceAction action) {
      return getGrants(action, Identity.ORGANIZATION, null);
   }

   /**
    * Gets the organizations that have been granted permission under the given organization to perform the specified action.
    *
    * @param action the granted option.
    * @param orgId the organizationId to search under.
    *
    * @return a set containing the names of the organizations that have been granted the permission.
    */
   public Set<IdentityID> getOrgScopedOrganizationGrants(ResourceAction action, String orgId) {
      String thisOrgID = orgId == null ? globalOrgId : orgId;
      return getAllOrganizationGrants(action).stream()
         .filter(pId -> thisOrgID.equals(pId.organizationID) ||
                        globalOrgId.equals(pId.organizationID))
         .map(pI -> new IdentityID(pI.name, thisOrgID))
         .collect(Collectors.toSet());
   }

   /**
    * Gets the organizations that have been granted permission under the given organization to perform the specified action.
    *
    * @param action the granted option.
    * @param organization the organization to search under.
    *
    * @return a set containing the names of the organizations that have been granted the permission.
    */
   public Set<IdentityID> getOrgScopedOrganizationGrants(ResourceAction action,
                                                         Organization organization)
   {
      String thisOrgID = organization == null ? globalOrgId : organization.getOrganizationID();
      return getOrganizationGrants(action, thisOrgID).stream()
         .filter(pId -> thisOrgID.equals(pId.organizationID) ||
            globalOrgId.equals(pId.organizationID))
         .map(pI -> new IdentityID(pI.name, thisOrgID))
         .collect(Collectors.toSet());
   }

   /**
    * Sets the organizations that have been granted permission to perform the specified action.
    *
    * @param action the granted action.
    * @param organizations the organizations that are granted the permission. The collection is copied, so any
    *               subsequent changes to it do not affect this object.
    */
   public void setOrganizationGrants(ResourceAction action, Set<PermissionIdentity> organizations) {
      setGrants(action, Identity.ORGANIZATION, organizations);
   }

   /**
    * Sets the organizations that have been granted permission for a given org to perform the specified action.
    *
    * @param action the granted action.
    * @param orgs the organizations that are granted the permission. The collection is copied, so any
    *               subsequent changes to it do not affect this object.
    * @param orgId the organizationId to set organization permissions under
    */
   public void setOrganizationGrantsForOrg(ResourceAction action, Set<String> orgs, String orgId) {
      setOrganizationGrantsForOrg(action, orgs, orgId, orgId);
   }

   public void setOrganizationGrantsForOrg(ResourceAction action, Set<String> orgs, String oldOrgId,
                                           String newOrgId)
   {
      oldOrgId = oldOrgId == null ? newOrgId : oldOrgId;
      final String org = oldOrgId == null ? globalOrgId : oldOrgId;
      Set<PermissionIdentity> orgGrants = getAllOrganizationGrants(action);
      Set<PermissionIdentity> updatedGrants = orgGrants.stream()
         .filter(pId -> !org.equals(pId.organizationID))
         .collect(Collectors.toSet());

      for(String o : orgs) {
         updatedGrants.add(new PermissionIdentity(o, newOrgId));
      }

      setGrants(action, Identity.ORGANIZATION, updatedGrants);
   }

   /**
    * Gets the entities that have been granted permission to perform the specified action.
    *
    * @param action       the granted action.
    * @param identityType the type of the entities' identity. This must be one of
    *                     {@link Identity#USER}, {@link Identity#ROLE}, {@link Identity#GROUP}, or {@link Identity#ORGANIZATION}.
    *
    * @return a set containing the names of the entities that have been granted the permission.
    */
   public Set<PermissionIdentity> getGrants(ResourceAction action, int identityType) {
      String currentOrgID = OrganizationManager.getInstance().getCurrentOrgID();

      return getGrants(action, identityType, currentOrgID);
   }

   /**
    * Gets the entities that have been granted permission to perform the specified action.
    *
    * @param action       the granted action.
    * @param identityType the type of the entities' identity. This must be one of
    *                     {@link Identity#USER}, {@link Identity#ROLE}, {@link Identity#GROUP}, or {@link Identity#ORGANIZATION}.
    * @param orgId        the organization to get grants from
    *
    * @return a set containing the names of the entities that have been granted the permission.
    */
   public Set<PermissionIdentity> getGrants(ResourceAction action, int identityType, String orgId) {
      Set<PermissionIdentity> grants = switch(identityType) {
         case Identity.USER -> userGrants.get(action);
         case Identity.ROLE -> roleGrants.get(action);
         case Identity.GROUP -> groupGrants.get(action);
         case Identity.ORGANIZATION -> organizationGrants.get(action);
         default -> null;
      };

      if(grants == null) {
         grants = new HashSet<>();
      }

      Set<PermissionIdentity> filteredGrants = new HashSet<>();

      for(PermissionIdentity grant : grants) {
         if(orgId == null || orgId.equals(grant.organizationID) || identityType == Identity.ROLE && grant.organizationID == null) {
            filteredGrants.add(grant);
         }
      }

      return filteredGrants;
   }

   /**
    * Gets the entities of the given organization that have been granted permission to perform the specified action.
    *
    * @param action       the granted action.
    * @param identityType the type of the entities' identity. This must be one of
    *                     {@link Identity#USER}, {@link Identity#ROLE}, {@link Identity#GROUP}, or {@link Identity#ORGANIZATION}.
    * @param orgId the organizationId to search under
    *
    * @return a set containing the names of the entities that have been granted the permission.
    */
   public Set<IdentityID> getOrgScopedGrants(ResourceAction action, int identityType, String orgId) {
      orgId = orgId == null ? globalOrgId : orgId;
      Set<IdentityID> grants = switch(identityType) {
         case Identity.USER -> getOrgScopedUserGrants(action, orgId);
         case Identity.ROLE -> getOrgScopedRoleGrants(action, orgId);
         case Identity.GROUP -> getOrgScopedGroupGrants(action, orgId);
         case Identity.ORGANIZATION -> getOrgScopedOrganizationGrants(action, orgId);
         default -> null;
      };

      if(grants == null) {
         grants = new HashSet<>();
      }

      return grants;
   }

   /**
    * Sets the entities that have been granted permission to perform the specified action.
    *
    * @param action       the granted action.
    * @param identityType the type of the entities' identity. This must be one of
    *                     {@link Identity#USER}, {@link Identity#ROLE}, {@link Identity#GROUP}, or {@link Identity#ORGANIZATION}.
    * @param entities     the entities that are granted the permission. The collection is copied, so
    *                     any subsequent changes to it do not affect this object.
    */
   public void setGrants(ResourceAction action, int identityType, Set<PermissionIdentity> entities) {
      Map<ResourceAction, Set<PermissionIdentity>> grants;

      switch(identityType) {
      case Identity.USER:
         grants = userGrants;
         break;
      case Identity.ROLE:
         grants = roleGrants;
         break;
      case Identity.GROUP:
         grants = groupGrants;
         break;
      case Identity.ORGANIZATION:
         grants = organizationGrants;
         break;
      default:
         return;
      }

      if(entities == null) {
         grants.remove(action);
      }
      else {
         grants.put(action, new HashSet<>(entities));
      }
   }


   /**
    * Sets the entities of the given type under the given organization that have been granted permission to perform the specified action.
    *
    * @param action       the granted action.
    * @param identityType the type of the entities' identity. This must be one of
    *                     {@link Identity#USER}, {@link Identity#ROLE}, {@link Identity#GROUP}, or {@link Identity#ORGANIZATION}.
    * @param grants       the entities that are granted the permission. The collection is copied, so
    *                     any subsequent changes to it do not affect this object.
    * @param orgId        the organizationId to set the permissions under
    */
   public void setGrantsOrgScoped(ResourceAction action, int identityType, Set<String> grants, String orgId) {
      switch(identityType) {
         case Identity.USER:
            setUserGrantsForOrg(action, grants, orgId);
            break;
         case Identity.GROUP:
            setGroupGrantsForOrg(action, grants, orgId);
            break;
         case Identity.ROLE:
            setRoleGrantsForOrg(action, grants, orgId);
            break;
         case Identity.ORGANIZATION:
            setOrganizationGrantsForOrg(action, grants, orgId);
            break;

      }
   }

   /**
    * Checks if a user has been granted permission to perform the specified action.
    *
    * @param name   the name of the user.
    * @param action the name of the granted action.
    *
    * @return {@code true} if the permission has been granted; {@code false} otherwise.
    */
   public boolean checkUser(PermissionIdentity name, ResourceAction action) {
      return check(name, action, Identity.USER);
   }

   /**
    * Checks if a role has been granted permission to perform the specified action.
    *
    * @param name   the name of the role.
    * @param action the name of the granted action.
    *
    * @return {@code true} if the permission has been granted; {@code false} otherwise.
    */
   public boolean checkRole(PermissionIdentity name, ResourceAction action) {
      return check(name, action, Identity.ROLE);
   }

   /**
    * Checks if a group has been granted permission to perform the specified action.
    *
    * @param name   the name of the group.
    * @param action the name of the granted action.
    *
    * @return {@code true} if the permission has been granted; {@code false} otherwise.
    */
   public boolean checkGroup(PermissionIdentity name, ResourceAction action) {
      return check(name, action, Identity.GROUP);
   }

   /**
    * Checks if an organization has been granted permission to perform the specified action.
    *
    * @param name   the name of the organization.
    * @param action the name of the granted action.
    *
    * @return {@code true} if the permission has been granted; {@code false} otherwise.
    */
   public boolean checkOrganization(PermissionIdentity name, ResourceAction action) {
      return check(name, action, Identity.ORGANIZATION);
   }

   /**
    * Check the if an identity has been granted permission to perform the specified action.
    *
    * @param identity the identity to check.
    * @param action   the name of the granted action.
    *
    * @return {@code true} if the permission has been granted; {@code false} otherwise.
    */
   public boolean check(Identity identity, String orgId, ResourceAction action) {
      return check(identity.getName(), orgId, action, identity.getType());
   }

   public boolean check(String name, String orgId, ResourceAction action, int type) {
      return check(new PermissionIdentity(name, orgId), action, type);
   }

   /**
    * Check if an identity has been granted permission to perform the specified action.
    *
    * @param identity the identity.
    * @param action the name of the granted action.
    * @param type   the type of the identity, one of {@link Identity#USER}, {@link Identity#ROLE},
    *               {@link Identity#GROUP}, or {@link Identity#ORGANIZATION} .
    *
    * @return {@code true} if the permission has been granted; {@code false} otherwise.
    */
   public boolean check(PermissionIdentity identity, ResourceAction action, int type) {
      Map<ResourceAction, Set<PermissionIdentity>> grants;

      switch(type) {
      case Identity.USER:
         grants = userGrants;
         break;
      case Identity.ROLE:
         grants = roleGrants;
         break;
      case Identity.GROUP:
         grants = groupGrants;
         break;
      case Identity.ORGANIZATION:
         grants = organizationGrants;
         break;
      default:
         return false;
      }

      Set<PermissionIdentity> identities = grants.get(action);

      if(identities == null) {
         return false;
      }

      for(PermissionIdentity id : identities) {
         if(id.equals(identity)) {
            return true;
         }
      }

      // @by billh, fix customer bug bug1305742936302
      // some security provider is case insensitive, e.g. Active Directory
      return identities.stream().anyMatch(i -> i.equalsIgnoreCase(identity));
   }

   /**
    * Remove an user from all permission list.
    */
   void removeUser(PermissionIdentity user) {
      for(Set<PermissionIdentity> identities : groupGrants.values()) {
         identities.remove(user);
      }
   }

   /**
    * Remove an role from all permission list.
    */
   void removeRole(PermissionIdentity role) {
      for(Set<PermissionIdentity> identities : groupGrants.values()) {
         identities.remove(role);
      }
   }

   /**
    * Remove an role from all permission list.
    */
   void removeGroup(PermissionIdentity group) {
      for(Set<PermissionIdentity> identities : groupGrants.values()) {
         identities.remove(group);
      }
   }

   /**
    * Remove an organization from all permission list.
    */
   void removeOrganization(PermissionIdentity org) {
      for(Set<PermissionIdentity> identities : organizationGrants.values()) {
         identities.remove(org);
      }
   }

   @Override
   public Object clone() {
      try {
         Permission perm = (Permission) super.clone();
         perm.userGrants = copyGrants(userGrants);
         perm.roleGrants = copyGrants(roleGrants);
         perm.groupGrants = copyGrants(groupGrants);
         perm.organizationGrants = copyGrants(organizationGrants);
         return perm;
      }
      catch(Exception e) {
         LoggerFactory.getLogger(getClass()).error("Failed to clone permission", e);
         return null;
      }
   }

   private Map<ResourceAction, Set<PermissionIdentity>> copyGrants(Map<ResourceAction, Set<PermissionIdentity>> in) {
      Map<ResourceAction, Set<PermissionIdentity>> out = new EnumMap<>(ResourceAction.class);

      for(Map.Entry<ResourceAction, Set<PermissionIdentity>> e : in.entrySet()) {
         out.put(e.getKey(), new HashSet<>(e.getValue()));
      }

      return out;
   }

   private String[] copy(String[] obj) {
      String[] attr = new String[obj.length];
      System.arraycopy(obj, 0, attr, 0, obj.length);
      return attr;
   }

   /**
    * Check if a permission setting is blank.
    */
   public boolean isBlank() {
      for(Set<PermissionIdentity> identities : userGrants.values()) {
         if(!identities.isEmpty()) {
            return false;
         }
      }

      for(Set<PermissionIdentity> identities : roleGrants.values()) {
         if(!identities.isEmpty()) {
            return false;
         }
      }

      for(Set<PermissionIdentity> identities : groupGrants.values()) {
         if(!identities.isEmpty()) {
            return false;
         }
      }

      for(Set<PermissionIdentity> identities : organizationGrants.values()) {
         if(!identities.isEmpty()) {
            return false;
         }
      }

      return true;
   }

   /**
    * Return true if any grants in this permission are under the given organization
    * @param action the name of the granted action.
    * @param orgId the organizationId to search
    * @return boolean value, true if any grant under the given orgId exists
    */
   public boolean isOrgInPerm(ResourceAction action, String orgId) {
      return getUserGrants(action).stream()
               .map(pid -> pid.organizationID)
               .anyMatch(o -> o.equals(orgId)) ||
             getGroupGrants(action).stream()
                .map(pid -> pid.organizationID)
                .anyMatch(o -> o.equals(orgId)) ||
             getRoleGrants(action).stream()
                .map(pid -> pid.organizationID)
                .anyMatch(o -> o != null && o.equals(orgId)) ||
             getOrganizationGrants(action).stream()
                .map(pid -> pid.organizationID)
                .anyMatch(o -> o.equals(orgId));
   }

   /**
    * clean any permissions belonging to the given organization
    * @param action the name of the granted action.
    * @param orgId the organizationId to remove
    */
   public void cleanOrganizationFromPermission(ResourceAction action, String orgId) {
      Set<PermissionIdentity> newUsers = new HashSet<>();
      if(userGrants.get(action) != null) {
         for(PermissionIdentity permId : userGrants.get(action)) {
            if(!permId.organizationID.equals(orgId)) {
               newUsers.add(permId);
            }
         }
         setUserGrants(action, newUsers);
      }

      if(groupGrants.get(action) != null) {
         Set<PermissionIdentity> newGroups = new HashSet<>();
         for(PermissionIdentity permId : groupGrants.get(action)) {
            if(!permId.organizationID.equals(orgId)) {
               newGroups.add(permId);
            }
         }
         setGroupGrants(action, newGroups);
      }

      if(roleGrants.get(action) != null) {
         Set<PermissionIdentity> newRoles = new HashSet<>();
         for(PermissionIdentity permId : roleGrants.get(action)) {
            if(!Tool.equals(permId.organizationID, orgId)) {
               newRoles.add(permId);
            }
         }
         setRoleGrants(action, newRoles);
      }

      if(organizationGrants.get(action) != null) {
         Set<PermissionIdentity> newOrgs = new HashSet<>();
         for(PermissionIdentity permId : organizationGrants.get(action)) {
            if(!permId.organizationID.equals(orgId)) {
               newOrgs.add(permId);
            }
         }
         setOrganizationGrants(action, newOrgs);
      }
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      Map<ResourceAction, Set<PermissionIdentity>> users = userGrants;
      Map<ResourceAction, Set<PermissionIdentity>> roles = roleGrants;
      Map<ResourceAction, Set<PermissionIdentity>> groups =  groupGrants;
      Map<ResourceAction, Set<PermissionIdentity>> organizations =  groupGrants;

      Set<ResourceAction> actions = new HashSet<>();
      actions.addAll(userGrants.keySet());
      actions.addAll(roleGrants.keySet());
      actions.addAll(groupGrants.keySet());
      actions.addAll(organizationGrants.keySet());

      writer.println("<permission>");

      actions.stream()
         .sorted()
         .forEach(a -> writeGrants(a, users, groups, organizations, roles, writer));

      if(orgUpdatedList != null) {
         writeOrgUpdatedList(writer);
      }

      writer.println("</permission>");
   }

   private void writeGrants(ResourceAction action,
                            Map<ResourceAction, Set<PermissionIdentity>> users,
                            Map<ResourceAction, Set<PermissionIdentity>> groups,
                            Map<ResourceAction, Set<PermissionIdentity>> organizations,
                            Map<ResourceAction, Set<PermissionIdentity>> roles, PrintWriter writer)
   {
      writer.format("  <grant action=\"%s\">%n", action);

      if(users.containsKey(action)) {
         users.get(action).stream()
            .sorted()
            .forEach(u -> writeIdentity("user", u, writer));
      }

      if(roles.containsKey(action)) {
         roles.get(action).stream()
            .sorted()
            .forEach(r -> writeIdentity("role", r, writer));
      }

      if(groups.containsKey(action)) {
         groups.get(action).stream()
            .sorted()
            .forEach(g -> writeIdentity("group", g, writer));
      }

      if(organizations.containsKey(action)) {
         organizations.get(action).stream()
            .sorted()
            .forEach(o -> writeIdentity("organization", o, writer));
      }

      writer.println("  </grant>");
   }

   private void writeIdentity(String tag, PermissionIdentity identity, PrintWriter writer) {
      writer.format("<%s>", tag);

      if(identity.name != null) {
         writer.format("<name><![CDATA[%s]]></name>", identity.name);
      }

      if(identity.organizationID != null) {
         writer.format("<organization><![CDATA[%s]]></organization>", identity.organizationID);
      }

      writer.format("</%s>%n", tag);
   }

   private void writeOrgUpdatedList(PrintWriter writer) {
      String tag = "orgEditedElement";
      writer.println("<orgEdited>");

      for(String name : orgUpdatedList.keySet()) {
         writer.format("<%s>", tag);

         if(name != null) {
            writer.format("<name><![CDATA[%s]]></name>", name);
         }

         if(orgUpdatedList.get(name) != null) {
            writer.format("<organization><![CDATA[%s]]></organization>", orgUpdatedList.get(name));
         }

         writer.format("</%s>%n", tag);
      }

      writer.println("</orgEdited>");
   }

   /**
    * Method to parse an xml segment.
    */
   @Override
   public void parseXML(Element tag) throws Exception {
      Map<ResourceAction, Set<PermissionIdentity>> users = new EnumMap<>(ResourceAction.class);
      Map<ResourceAction, Set<PermissionIdentity>> roles = new EnumMap<>(ResourceAction.class);
      Map<ResourceAction, Set<PermissionIdentity>> groups = new EnumMap<>(ResourceAction.class);
      Map<ResourceAction, Set<PermissionIdentity>> organizations = new EnumMap<>(ResourceAction.class);
      Map<String, Boolean> orgUpdated = new HashMap<>();

      NodeList grants = Tool.getChildNodesByTagName(tag, "grant");

      for(int i = 0; i < grants.getLength(); i++) {
         Element grant = (Element) grants.item(i);
         ResourceAction action = ResourceAction.valueOf(Tool.getAttribute(grant, "action"));
         NodeList identities = Tool.getChildNodesByTagName(grant, "user");

         for(int j = 0; j < identities.getLength(); j++) {
            Element identity = (Element) identities.item(j);
            users.computeIfAbsent(action, a -> new HashSet<>()).add(parseIdentity(identity));
         }

         identities = Tool.getChildNodesByTagName(grant, "role");

         for(int j = 0; j < identities.getLength(); j++) {
            Element identity = (Element) identities.item(j);
            roles.computeIfAbsent(action, a -> new HashSet<>()).add(parseIdentity(identity));
         }

         identities = Tool.getChildNodesByTagName(grant, "group");

         for(int j = 0; j < identities.getLength(); j++) {
            Element identity = (Element) identities.item(j);
            groups.computeIfAbsent(action, a -> new HashSet<>()).add(parseIdentity(identity));
         }

         identities = Tool.getChildNodesByTagName(grant, "organization");

         for(int j = 0; j < identities.getLength(); j++) {
            Element identity = (Element) identities.item(j);
            organizations.computeIfAbsent(action, a -> new HashSet<>()).add(parseIdentity(identity));
         }

         identities = Tool.getChildNodesByTagName(grant, "orgEdited");

         for(int j = 0; j < identities.getLength(); j++) {
            Element identity = (Element) identities.item(j);
            orgUpdated.put(Tool.getChildValueByTagName(identity, "name"),
                       Boolean.parseBoolean(Tool.getChildValueByTagName(identity, "organization")));
         }
      }

      userGrants = users;
      roleGrants = roles;
      groupGrants = groups;
      organizationGrants = organizations;
      orgUpdatedList = orgUpdated;
   }

   private PermissionIdentity parseIdentity(Element elem) {
      return new PermissionIdentity(
         Tool.getChildValueByTagName(elem, "name"),
         Tool.getChildValueByTagName(elem, "organization"));
   }

   public void updateGrantAllByOrg(String orgId, boolean isEdited) {
      orgUpdatedList.put(orgId, isEdited);
   }

   public void removeGrantAllByOrg(String orgId) {
      orgUpdatedList.remove(orgId);
   }

   public boolean hasOrgEditedGrantAll(String orgId) {
      return orgUpdatedList.get(orgId) != null && orgUpdatedList.get(orgId);
   }

   public Map<String, Boolean> getOrgEditedGrantAll() {
      return orgUpdatedList;
   }

   public void setOrgEditedGrantAll(Map<String, Boolean> orgUpdatedList) {
      this.orgUpdatedList = orgUpdatedList;
   }

   @Override
   public String toString() {
      return "Permission{" +
         "userGrants=" + userGrants +
         ", roleGrants=" + roleGrants +
         ", groupGrants=" + groupGrants +
         ", organizationGrants=" + organizationGrants +
         '}';
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(o == null || getClass() != o.getClass()) {
         return false;
      }

      Permission that = (Permission) o;
      return Objects.equals(userGrants, that.userGrants) &&
         Objects.equals(roleGrants, that.roleGrants) &&
         Objects.equals(groupGrants, that.groupGrants) &&
         Objects.equals(organizationGrants, that.organizationGrants) ;
   }

   @Override
   public int hashCode() {
      return Objects.hash(userGrants, roleGrants, groupGrants, organizationGrants);
   }

   private Map<ResourceAction, Set<PermissionIdentity>> userGrants = new EnumMap<>(ResourceAction.class);
   private Map<ResourceAction, Set<PermissionIdentity>> roleGrants = new EnumMap<>(ResourceAction.class);
   private Map<ResourceAction, Set<PermissionIdentity>> groupGrants = new EnumMap<>(ResourceAction.class);
   private Map<ResourceAction, Set<PermissionIdentity>> organizationGrants = new EnumMap<>(ResourceAction.class);
   private Map<String, Boolean> orgUpdatedList = new HashMap<>();
   private final String globalOrgId = "__GLOBAL__";


   public static class PermissionIdentity implements Serializable {
      private final String name;
      private final String organizationID;

      public PermissionIdentity(String name, String organization) {
         this.name = name;
         this.organizationID = organization;
      }

      public String getName() {
         return name;
      }

      public String getOrganizationID() {
         return organizationID;
      }

      @Override
      public boolean equals(Object other) {
         if(other instanceof PermissionIdentity) {
            String oName = ((PermissionIdentity)other).name;
            String oOrg = ((PermissionIdentity)other).organizationID;

            if(Tool.equals(this.name, oName) && Tool.equals(this.organizationID, oOrg)) {
               return true;
            }
         }
         return false;
      }

      boolean equalsIgnoreCase(PermissionIdentity other) {
         if(other == null) {
            return false;
         }

         if(name == null && other.name != null) {
            return false;
         }

         if(name != null && other.name == null) {
            return false;
         }

         if(name != null && !name.equalsIgnoreCase(other.name)) {
            return false;
         }

         if(organizationID == null && other.name != null) {
            return false;
         }

         if(organizationID != null && other.organizationID == null) {
            return false;
         }

         return organizationID == null || organizationID.equalsIgnoreCase(other.organizationID);
      }
   }

   public static final class Serializer extends StdSerializer<Permission> {
      public Serializer() {
         super(Permission.class);
      }

      @Override
      public void serialize(Permission value, JsonGenerator gen, SerializerProvider provider)
         throws IOException
      {
         gen.writeStartObject();
         writeContent(value, gen);
         gen.writeEndObject();
      }

      @Override
      public void serializeWithType(Permission value, JsonGenerator gen,
                                    SerializerProvider serializers, TypeSerializer typeSer)
         throws IOException
      {
         WritableTypeId typeId = typeSer.typeId(value, JsonToken.START_OBJECT);
         typeSer.writeTypePrefix(gen, typeId);
         writeContent(value, gen);
         typeSer.writeTypeSuffix(gen, typeId);
      }

      private void writeContent(Permission value, JsonGenerator gen) throws IOException {
         Map<ResourceAction, Set<PermissionIdentity>> users = value.userGrants;
         Map<ResourceAction, Set<PermissionIdentity>> roles = value.roleGrants;
         Map<ResourceAction, Set<PermissionIdentity>> groups =  value.groupGrants;
         Map<ResourceAction, Set<PermissionIdentity>> organizations =  value.organizationGrants;

         Set<ResourceAction> actions = new HashSet<>();
         actions.addAll(users.keySet());
         actions.addAll(roles.keySet());
         actions.addAll(groups.keySet());
         actions.addAll(organizations.keySet());

         for(ResourceAction action : actions) {
            gen.writeObjectFieldStart(action.name());

            if(users.containsKey(action) && !users.get(action).isEmpty()) {
               gen.writeArrayFieldStart("users");

               for(PermissionIdentity user : users.get(action)) {
                  writeIdentity(user, gen);
               }

               gen.writeEndArray();
            }

            if(roles.containsKey(action) && !roles.get(action).isEmpty()) {
               gen.writeArrayFieldStart("roles");

               for(PermissionIdentity role : roles.get(action)) {
                  writeIdentity(role, gen);
               }

               gen.writeEndArray();
            }

            if(groups.containsKey(action) && !groups.get(action).isEmpty()) {
               gen.writeArrayFieldStart("groups");

               for(PermissionIdentity group : groups.get(action)) {
                  writeIdentity(group, gen);
               }

               gen.writeEndArray();
            }

            if(organizations.containsKey(action) && !organizations.get(action).isEmpty()) {
               gen.writeArrayFieldStart("organizations");

               for(PermissionIdentity organization : organizations.get(action)) {
                  writeIdentity(organization, gen);
               }

               gen.writeEndArray();
            }

            gen.writeEndObject();
         }

         Map<String, Boolean> orgUpdatedList = value.orgUpdatedList;

         if(orgUpdatedList != null) {
            gen.writeObjectFieldStart("orgUpdatedList");

            for(Map.Entry<String, Boolean> entry : orgUpdatedList.entrySet()) {
               gen.writeBooleanField(entry.getKey(), entry.getValue());
            }

            gen.writeEndObject();
         }
      }

      private void writeIdentity(PermissionIdentity identity, JsonGenerator gen) throws IOException {
         gen.writeStartObject();
         gen.writeStringField("name", identity.name);
         gen.writeStringField("organization", identity.organizationID);
         gen.writeEndObject();
      }
   }

   public static final class Deserializer extends StdDeserializer<Permission> {
      public Deserializer() {
         super(Permission.class);
      }

      @Override
      public Permission deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
         Permission permission = new Permission();
         JsonNode node = p.getCodec().readTree(p);

         for(Iterator<Map.Entry<String, JsonNode>> i = node.fields(); i.hasNext();) {
            Map.Entry<String, JsonNode> e = i.next();

            if("orgUpdatedList".equals(e.getKey())) {
               ObjectNode orgUpdated = (ObjectNode) e.getValue();
               Iterator<String> orgsIterator = orgUpdated.fieldNames();

               while(orgsIterator.hasNext()) {
                  String orgName = orgsIterator.next();
                  permission.updateGrantAllByOrg(orgName, orgUpdated.get(orgName).booleanValue());
               }

               continue;
            }

            ResourceAction action = ResourceAction.valueOf(e.getKey());
            ObjectNode grants = (ObjectNode) e.getValue();
            JsonNode array = grants.get("users");

            if(array != null && array.isArray()) {
               Set<PermissionIdentity> identities = new HashSet<>();

               for(JsonNode item : array){
                  identities.add(parseIdentity(item));
               }

               permission.userGrants.put(action, identities);
            }

            array = grants.get("roles");

            if(array != null && array.isArray()) {
               Set<PermissionIdentity> identities = new HashSet<>();

               for(JsonNode item : array){
                  identities.add(parseIdentity(item));
               }

               permission.roleGrants.put(action, identities);
            }

            array = grants.get("groups");

            if(array != null && array.isArray()) {
               Set<PermissionIdentity> identities = new HashSet<>();

               for(JsonNode item : array){
                  identities.add(parseIdentity(item));
               }

               permission.groupGrants.put(action, identities);
            }

            array = grants.get("organizations");

            if(array != null && array.isArray()) {
               Set<PermissionIdentity> identities = new HashSet<>();

               for(JsonNode item : array){
                  identities.add(parseIdentity(item));
               }

               permission.organizationGrants.put(action, identities);
            }
         }

         return permission;
      }

      private PermissionIdentity parseIdentity(JsonNode item) {
         PermissionIdentity identity;

         if(item.isTextual()) {
            identity = new PermissionIdentity(
               item.asText(), Organization.getDefaultOrganizationID());
         }
         else {
            ObjectNode obj = (ObjectNode) item;
            identity = new PermissionIdentity(
               obj.get("name").asText(), obj.get("organization").asText());
         }

         return identity;
      }
   }
}
