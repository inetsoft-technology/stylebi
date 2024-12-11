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

import inetsoft.sree.ClientInfo;
import inetsoft.uql.util.Identity;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Interface for classes that provide authentication services to a security
 * provider.
 *
 * @author InetSoft Technology
 * @since  8.5
 */
public interface AuthenticationProvider extends JsonConfigurableProvider, CachableProvider {
   /**
    * Get a user by name.
    *
    * @param userIdentity the unique identifier of the user.
    *
    * @return the User object that encapsulates the properties of the user.
    */
   User getUser(IdentityID userIdentity);

   /**
    * Get a list of all users in the system.
    *
    * @return list of users.
    */
   IdentityID[] getUsers();

   /**
    * Get an organization by name.
    *
    * @param id the unique identifier of the organization.
    *
    * @return the Organization object that encapsulates the properties of the organization.
    */
   Organization getOrganization(String id);

   /**
    * Get an organization id by name.
    *
    * @param name the unique identifier of the organization.
    *
    * @return the id of the organization.
    */
   String getOrgIdFromName(String name);

   /**
    * Get an organization name by id.
    *
    * @param id the unique identifier of the organization.
    *
    * @return the name of the organization.
    */
   String getOrgNameFromID(String id);

   /**
    * Get a list of all organization ids in the system.
    *
    * @return list of organizations.
    */
   String[] getOrganizationIDs();

   /**
    * Get a list of all organization names in the system.
    *
    * @return list of organizations.
    */
   String[] getOrganizationNames();

   /**
    * Get a list of all users in a group.
    *
    * @param groupIdentity the name of the group.
    *
    * @return list of users
    */
   IdentityID[] getUsers(IdentityID groupIdentity);

   /**
    * Get a list of all emails for a user.
    *
    * @param userIdentity the unique identifier for the user.
    *
    * @return list of emails.
    *
    * @deprecated use {@link inetsoft.sree.security.User#getEmails()} instead.
    */
   @Deprecated
   String[] getEmails(IdentityID userIdentity);

   /**
    * Get a list of all users not in any group except INDIVIDUAL.
    *
    * @return list of users
    */
   IdentityID[] getIndividualUsers();

   /**
    * Get a list of all roles in the system.
    *
    * @return list of roles.
    */
   IdentityID[] getRoles();

   /**
    * Get a list of all roles bound to specific user.
    *
    * @param roleIdentity the unique identifier for the user.
    *
    * @return list of roles.
    */
   IdentityID[] getRoles(IdentityID roleIdentity);

   /**
    * Get a role object from the role ID.
    *
    * @param roleIdentity the unique identifier of the role.
    *
    * @return the named role object of <code>null</code> if no such role exists.
    */
   Role getRole(IdentityID roleIdentity);

   /**
    * Get a group by name.
    *
    * @param groupIdentity the name of the group.
    *
    * @return the named group or <code>null</code> if no such group exists.
    */
   Group getGroup(IdentityID groupIdentity);

   /**
    * Get a list of all groups defined in the system. If groups are nested,
    * only the top level groups should be returned.
    *
    * @return list of groups.
    */
   IdentityID[] getGroups();

   /**
    * Gets a list of all groups bound to the specified user.
    *
    * @param userId the name of the user.
    *
    * @return a list of group names.
    */
   default String[] getUserGroups(IdentityID userId) {
      return getUserGroups(userId, true);
   }

   /**
    * Gets a list of all groups bound to the specified userID.
    *
    * @param userID          the name of the userID.
    * @param caseSensitive true if names are case sensitive, false otherwise.
    *
    * @return a list of group names.
    */
   default String[] getUserGroups(IdentityID userID, boolean caseSensitive) {
      final Logger LOG =
         LoggerFactory.getLogger(AuthenticationProvider.class);

      if(userID == null) {
         return new String[0];
      }

      IdentityID[] usergroupsRaw;
      usergroupsRaw = Arrays.stream(getGroups())
         .filter(g -> Arrays.stream(getUsers(g))
            .anyMatch(u -> caseSensitive ? userID.equals(u) :
               userID.name.equalsIgnoreCase(u.name) && userID.orgID.equals(u.orgID)))
         .toArray(IdentityID[]::new);

      List<String> userGroupsInOrg = new ArrayList<>();

      for(IdentityID group : usergroupsRaw) {
         if(!getGroup(group).getOrganizationID().equals(userID.orgID)) {
            LOG.warn(Catalog.getCatalog().getString("em.security.GroupNotAdded",userID,group));
         }
         else {
            userGroupsInOrg.add(group.name);
         }
      }

      return !userGroupsInOrg.isEmpty() ? userGroupsInOrg.toArray(new String[0]):
                                          new String[0];
   }

   /**
    * Gets a list of all groups bound to the specified group.
    *
    * @param group the name of the group.
    *
    * @return a list of group names.
    */
   default String[] getGroupParentGroups(IdentityID group) {
      if(group == null) {
         return new String[0];
      }

      Group groupObject = getGroup(group);

      if(groupObject == null) {
         return new String[0];
      }

      Set<String> groups = new HashSet<>();
      Deque<String> queue = new ArrayDeque<>(Arrays.asList(groupObject.getGroups()));

      while(!queue.isEmpty()) {
         String parentGroup = queue.removeFirst();
         IdentityID parentID = new IdentityID(parentGroup,groupObject.getOrganizationID());
         groups.add(parentID.name);
         groupObject = getGroup(parentID);

         if(groupObject != null) {
            for(String childGroup : groupObject.getGroups()) {
               IdentityID childID = new IdentityID(childGroup, groupObject.getOrganizationID());
               if(!groups.contains(childID)) {
                  queue.addLast(childGroup);
               }
            }
         }
      }

      return groups.toArray(new String[0]);
   }

   /**
    * Gets all members of a group.
    *
    * @param groupIdentity the name of the group.
    *
    * @return a list of users and groups that belong to the named group.
    */
   default Identity[] getGroupMembers(IdentityID groupIdentity) {
      List<Identity> list = Arrays.stream(getGroups())
         .map(this::getGroup)
         .filter(g -> g != null && Arrays.asList(g.getGroups()).contains(groupIdentity.name)
                                 && Tool.equals(groupIdentity.orgID, g.getOrganizationID()))
         .collect(Collectors.toList());

      list.addAll(Arrays.stream(getUsers())
            .map(this::getUser)
            .filter(u -> u != null && Arrays.asList(u.getGroups()).contains(groupIdentity.name)
                       && Tool.equals(groupIdentity.orgID, u.getOrganizationID()))
            .collect(Collectors.toList()));

      return list.toArray(new Identity[0]);
   }

   /**
    * Gets all members of an Organization.
    *
    * @param organizationID the name of the organizationID.
    *
    * @return a list of users and groups that belong to the named organizationID.
    */
   default String[] getOrganizationMembers(String organizationID) {
      List<String> list = Arrays.stream(getGroups())
         .map(name1 -> getGroup(name1))
         .filter(g -> g != null && organizationID.equals(g.getOrganizationID()))
         .map(g -> g.name)
         .collect(Collectors.toList());

      list.addAll(Arrays.stream(getUsers())
            .map(name -> getUser(name))
            .filter(u -> u != null && organizationID.equals(u.getOrganizationID()))
                     .map(m -> m.name)
            .collect(Collectors.toList()));

      list.addAll(Arrays.stream(getRoles())
            .map((IdentityID roleid) -> getRole(roleid))
            .filter(u -> u != null && organizationID.equals(u.getOrganizationID()))
                     .map(m -> m.name)
            .collect(Collectors.toList()));

      return list.toArray(new String[0]);
   }

   default Identity[] getOrganizationMemberIdentities(String organization) {
      List<Identity> list = Arrays.stream(getGroups())
         .map(name1 -> getGroup(name1))
         .filter(g -> g != null && organization.equals(g.getOrganizationID()))
         .collect(Collectors.toList());

      list.addAll(Arrays.stream(getUsers())
                     .map(name -> getUser(name))
                     .filter(u -> u != null && organization.equals(u.getOrganizationID()))
                     .collect(Collectors.toList()));

      list.addAll(Arrays.stream(getRoles())
                     .map((IdentityID roleid) -> getRole(roleid))
                     .filter(u -> u != null && organization.equals(u.getOrganizationID()))
                     .collect(Collectors.toList()));

      return list.toArray(new Identity[0]);
   }

   /**
    * get OrganizationID for the given Organization
    * @param name, the Organization to pull from
    * @return organizationID for the provided name
    */
   default String getOrganizationId(String name) {
      // global
      for(String oid : getOrganizationIDs()) {
         if(getOrganization(oid).getName().equals(name)) {
            return oid;
         }
      }
      return null;
   }

   /**
    * Gets all members of a group.
    *
    * @param groupIdentity  the name of the group.
    * @param groupMemberMap the groupMemberMap returned from {@link #createGroupMemberMap}
    *
    * @return a list of users and groups that belong to the named group.
    */
   default Identity[] getGroupMembers(IdentityID groupIdentity, Map<String, List<Identity>> groupMemberMap) {
      return groupMemberMap.getOrDefault(groupIdentity.name, Collections.emptyList()).toArray(new Identity[0]);
   }

   /**
    * This method is meant to be a performant alternatives to calling
    * {{@link #getGroupMembers(IdentityID)}} individually for many groups. This method instead creates
    * the whole map for every group and so avoids iterating over all the groups many times.
    *
    * @return a map of group name to list of identities.
    */
   default Map<String, List<Identity>> createGroupMemberMap(String organization) {
      final Map<String, List<Identity>> groupMemberMap = new HashMap<>();

      Arrays.stream(getGroups())
         .filter(id -> id.orgID.equals(organization))
         .map(name1 -> getGroup(name1))
         .forEach(group -> {
            for(String groupName : group.getGroups()) {
               groupMemberMap.compute(groupName, (key, list) -> {
                  if(list == null) {
                     list = new ArrayList<>(1);
                  }

                  list.add(group);
                  return list;
               });
            }
         });

      Arrays.stream(getUsers())
         .filter(id -> id.orgID.equals(organization))
         .map(name -> getUser(name))
         .forEach(user -> {
            for(String groupName : user.getGroups()) {
               groupMemberMap.compute(groupName, (key, list) -> {
                  if(list == null) {
                     list = new ArrayList<>(1);
                  }

                  list.add(user);
                  return list;
               });
            }
         });

      return groupMemberMap;
   }

   /**
    * Gets the groups and users that have been assigned the specified role.
    *
    * @param roleIdentity the name of the role.
    *
    * @return the groups and users having the named role.
    */
   default Identity[] getRoleMembers(IdentityID roleIdentity) {
      List<Identity> list = Arrays.stream(getGroups())
         .map(id -> getGroup(id))
         .filter(g -> g != null && Arrays.asList(g.getRoles()).contains(roleIdentity))
         .collect(Collectors.toList());

      list.addAll(Arrays.stream(getUsers())
            .map(name -> getUser(name))
            .filter(u -> u != null && Arrays.asList(u.getRoles()).contains(roleIdentity))
            .collect(Collectors.toList()));

      list.addAll(Arrays.stream(this.getOrganizationIDs())
            .map(o -> getOrganization(o))
            .filter(o -> o != null && Arrays.asList(o.getRoles()).contains(roleIdentity))
            .collect(Collectors.toList()));

      return list.toArray(new Identity[0]);
   }

   /**
    * Check the authentication of specific entity.
    *
    * @param userIdentity the unique identification of the user.
    * @param credential   a wrapper for some secure message, such as the user ID
    *                     and password. <code>credential</code> is of type {@link String} or
    *                     {@link DefaultTicket}.
    *
    * @return <code>true</code> if the authentication succeeded.
    */
   boolean authenticate(IdentityID userIdentity, Object credential);

   /**
    * Find the concrete identity in this security provider.
    * @return the identity found in this security provider, <tt>null</tt>
    * otherwise.
    */
   Identity findIdentity(Identity identity);

   /**
    * Get a list the email addresses of users that do not belong to any group.
    *
    * @return a list of email addresses.
    */
   default String[] getIndividualEmailAddresses() {
      return new String[0];
   }

   /**
    * Determines if the authentication method is case sensitive with respect to the user name.
    *
    * @return {@code true} if case sensitive; {@code false} otherwise.
    */
   default boolean isAuthenticationCaseSensitive() {
      return true;
   }

   /**
    * Determines if this provider is a built-in provider for the "No security" option.
    *
    * @return {@code true} if virtual; {@code false otherwise}
    */
   default boolean isVirtual() {
      return false;
   }

   /**
    * Checks that the connection parameters for this provide are valid.
    *
    * @throws SRSecurityException if the connection failed using the current parameters.
    */
   default void checkParameters() throws SRSecurityException {
   }

   default boolean isSystemAdministratorRole(IdentityID roleIdentity) {
      return "Administrator".equals(roleIdentity.name);
   }

   default boolean isOrgAdministratorRole(IdentityID roleIdentity) {
      return false;
   }

   /**
    * Determines if this provider contains an anonymous user.
    *
    * @return {@code true} if there is a user named "anonymous"; {@code false} otherwise.
    */
   default boolean containsAnonymousUser() {
      return Arrays.stream(getUsers())
         .map(id -> id.name)
         .anyMatch(i -> i.equals(ClientInfo.ANONYMOUS));
   }

   /**
    * Tear down the security provider.
    */
   void tearDown();

   /**
    * Do BFS on the groups and their ancestor groups and return all the found groups.
    *
    * @param groupNames the groups to search.
    *
    * @return all the groups and their ancestors.
    */
   default IdentityID[] getAllGroups(IdentityID[] groupNames) {
      final Set<IdentityID> allGroups = new HashSet<>();

      if(groupNames != null) {
         final Deque<IdentityID> queue = new ArrayDeque<>(Arrays.asList(groupNames));

         while(!queue.isEmpty()) {
            final IdentityID groupIdentity = queue.removeFirst();

            if(!allGroups.contains(groupIdentity)) {
               allGroups.add(groupIdentity);
               final Group group = getGroup(groupIdentity);

               if(group != null) {
                  for(String parent : group.getGroups()) {
                     if(!allGroups.contains(new IdentityID(parent, group.getOrganizationID()))) {
                        queue.addLast(new IdentityID(parent, group.getOrganizationID()));
                     }
                  }
               }
            }
         }
      }

      return allGroups.toArray(IdentityID[]::new);
   }

   /**
    * Do BFS on the roles their ancestor roles and return all the found roles.
    *
    * @param roleIdentities the roles to search.
    *
    * @return all the roles and their ancestors.
    */
   default IdentityID[] getAllRoles(IdentityID[] roleIdentities) {
      final Set<IdentityID> allRoles = new HashSet<>();

      if(roleIdentities != null) {
         final Deque<IdentityID> queue = new ArrayDeque<>(Arrays.asList(roleIdentities));

         while(!queue.isEmpty()) {
            final IdentityID roleIdentity = queue.removeFirst();

            if(!allRoles.contains(roleIdentity)) {
               allRoles.add(roleIdentity);
               final Role role = getRole(roleIdentity);

               if(role != null) {
                  for(IdentityID parent : role.getRoles()) {
                     if(!allRoles.contains(parent)) {
                        queue.addLast(parent);
                     }
                  }
               }
            }
         }
      }

      return allRoles.toArray(new IdentityID[0]);
   }

   static Map<String, FSRole> getDefaultRoles(String orgID) {
      Map<String, FSRole> map = new HashMap<String, FSRole>();

      IdentityID roleID = new IdentityID("Designer", orgID);
      FSRole role = new FSRole(roleID, new IdentityID[0], "");
      role.setDefaultRole(false);
      map.put(role.getIdentityID().convertToKey(), role);

      roleID = new IdentityID("Advanced", orgID);
      role = new FSRole(roleID, new IdentityID[0], "");
      role.setDefaultRole(false);
      map.put(role.getIdentityID().convertToKey(), role);

      roleID = new IdentityID("Everyone", orgID);
      role = new FSRole(roleID, new IdentityID[0], "");
      role.setDefaultRole(true);
      map.put(role.getIdentityID().convertToKey(), role);

      return map;
   }
}
