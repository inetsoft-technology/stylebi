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

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.storage.*;
import inetsoft.uql.util.AbstractIdentity;
import inetsoft.uql.util.Identity;
import inetsoft.uql.XPrincipal;
import inetsoft.util.*;
import inetsoft.web.admin.security.IdentityModel;
import inetsoft.web.admin.security.IdentityService;
import inetsoft.web.admin.security.user.IdentityThemeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Principal;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Authentication module that stores user, password, group and role information
 * on the file system.
 *
 * @author InetSoft Technology
 * @since 8.5
 */
public class FileAuthenticationProvider extends AbstractEditableAuthenticationProvider {
   /**
    * Initializes this module.
    */
   private void init() {
      synchronized(this) {
         if(userStorage != null && groupStorage != null && roleStorage != null && organizationStorage != null) {
            return;
         }

         roleStorage = SingletonManager.getInstance(KeyValueStorage.class,
           "defaultSecurityRoles",
           (Supplier<LoadRolesTask>) (() -> new LoadRolesTask("defaultSecurityRoles")));
         userStorage = SingletonManager.getInstance(KeyValueStorage.class,
           "defaultSecurityUsers",
           (Supplier<LoadUsersTask>) (() -> new LoadUsersTask("defaultSecurityUsers")));
         groupStorage = SingletonManager.getInstance(KeyValueStorage.class, "defaultSecurityGroups");
         organizationStorage = SingletonManager.getInstance(KeyValueStorage.class,
                                                 "defaultSecurityOrganizations",
                                                 (Supplier<LoadOrganizationsTask>) (() -> new LoadOrganizationsTask("defaultSecurityOrganizations")));
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public User getUser(IdentityID userIdentity) {
      init();
      return userStorage.get(userIdentity.convertToKey());
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public IdentityID[] getUsers() {
      init();
      return userStorage.stream()
         .map(KeyValuePair::getKey)
         .map(IdentityID::getIdentityIDFromKey)
         .toArray(IdentityID[]::new);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public Organization getOrganization(String name) {
      init();
      return organizationStorage.get(name);
   }

   @Override
   public void setOrganization(String oname, Organization org) {
      init();
      lock.lock();

      try {
         String oldOrgID = getOrganization(oname) != null ? getOrganization(oname).getId() : null;
         organizationStorage.remove(oname).get();

         if(!oname.equals(org.getName()) || !org.getId().equals(oldOrgID)) {
            processAuthenticationChange(new IdentityID(oname, oname), org.getIdentityID(),
                                        oldOrgID, org.getId(), Identity.ORGANIZATION, false);
         }

         organizationStorage.put(org.getName(), (FSOrganization) org).get();
      }
      catch(Exception e) {
         LOG.error("Failed to update organization {}", oname, e);
      }
      finally {
         lock.unlock();
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public String[] getOrganizations() {
      init();
      return organizationStorage.stream()
         .map(KeyValuePair::getKey)
         .toArray(String[]::new);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public IdentityID[] getUsers(IdentityID groupIdentity) {
      init();
      return userStorage.stream()
         .map(KeyValuePair::getValue)
         .filter(u -> isGroupMember(u, groupIdentity))
         .map(u -> u.getIdentityID())
         .toArray(IdentityID[]::new);
   }

   private boolean isGroupMember(User user, IdentityID group) {
      return group == null && user.getGroups().length == 0 ||
         group != null && Arrays.asList(user.getGroups()).contains(group.name);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public IdentityID[] getIndividualUsers() {
      init();
      return userStorage.stream()
         .map(KeyValuePair::getValue)
         .filter(u -> u.getGroups().length == 0)
         .map(u -> u.getIdentityID())
         .toArray(IdentityID[]::new);
   }

   @Override
   public String[] getIndividualEmailAddresses() {
      init();
      return userStorage.stream()
         .map(KeyValuePair::getValue)
         .filter(u -> u.getGroups().length == 0)
         .flatMap(u -> Arrays.stream(u.getEmails()).limit(1L))
         .toArray(String[]::new);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public Group getGroup(IdentityID groupIdentity) {
      init();
      return groupStorage.get(groupIdentity.convertToKey());
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public IdentityID[] getGroups() {
      init();
      return groupStorage.stream()
         .map(KeyValuePair::getKey)
         .map(IdentityID::getIdentityIDFromKey)
         .toArray(IdentityID[]::new);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public IdentityID[] getRoles() {
      init();
      return roleStorage.stream()
         .map(KeyValuePair::getKey)
         .map(IdentityID::getIdentityIDFromKey)
         .toArray(IdentityID[]::new);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public Role getRole(IdentityID roleIdentity) {
      init();
      return roleStorage.get(roleIdentity.convertToKey());
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public IdentityID[] getRoles(IdentityID roleIdentity) {
      init();
      return userRoleCache.get(roleIdentity);
   }

   private IdentityID[] doGetRoles(IdentityID userIdentity) {
      Set<IdentityID> userRoles = new HashSet<>();
      User userObject = getUser(userIdentity);

      if(userObject != null) {
         Deque<IdentityID> queue = new ArrayDeque<>(Arrays.asList(userObject.getRoles()));
         Arrays.stream(getUserGroups(userIdentity))
            .map(name -> getGroup(new IdentityID(name, userIdentity.organization)))
            .filter(Objects::nonNull)
            .flatMap(g -> Arrays.stream(g.getRoles()))
            .forEach(queue::addLast);

         while(!queue.isEmpty()) {
            IdentityID role = queue.removeFirst();

            if(!userRoles.contains(role)) {
               userRoles.add(role);

               Role roleObj = getRole(role);

               if(roleObj != null) {
                  for(IdentityID parent : roleObj.getRoles()) {
                     if(!userRoles.contains(parent)) {
                        queue.addLast(parent);
                     }
                  }
               }
            }
         }
      }

      return userRoles.toArray(new IdentityID[0]);
   }

   @Override
   public String[] getUserGroups(IdentityID userId) {
      init();
      return userGroupCache.get(userId);
   }

   private String[] doGetUserGroups(IdentityID userIdentity) {
      User userObject = getUser(userIdentity);
      final String[] userGroups;

      if(userObject != null) {
         userGroups = Arrays.stream(getAllGroups(Arrays.stream(userObject.getGroups())
                                    .map(g -> new IdentityID(g, userObject.getOrganization())).toArray(IdentityID[]::new)))
                                    .map(id -> id.name).toArray(String[]::new);
      }
      else {
         userGroups = new String[0];
      }

      return userGroups;
   }

   @Override
   public String getOrgId(String name) {
      return getOrganization(name).getId();
   }

   @Override
   public String getOrgNameFromID(String id) {
      for(String org : getOrganizations()) {
         if(getOrganization(org).getId().equalsIgnoreCase(id)) {
            return org;
         }
      }
      return null;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public boolean authenticate(IdentityID userIdentity, Object credential) {
      init();

      if(credential == null) {
         LOG.error("Ticket is null; cannot authenticate.");
         return false;
      }

      if(!(credential instanceof DefaultTicket)) {
         credential = DefaultTicket.parse(credential.toString());
      }

      IdentityID userid = ((DefaultTicket) credential).getName();
      String passwd = ((DefaultTicket) credential).getPassword();

      if(userid == null || userid.name.length() == 0 || passwd == null ||
         passwd.length() == 0) {
         return false;
      }

      User uobj = getUser(userIdentity);

      if(null == uobj || !uobj.isActive()) {
         return false;
      }

      String savedPasswd = uobj.getPassword();

      if(savedPasswd == null) {
         return false;
      }
      else {
         String algorithm = uobj.getPasswordAlgorithm();

         if(algorithm == null) {
            algorithm = "MD5";
         }

         return Tool.checkHashedPassword(
            savedPasswd, passwd, algorithm, uobj.getPasswordSalt(), uobj.isAppendPasswordSalt());
      }
   }

   @Override
   public boolean isSystemAdministratorRole(IdentityID roleIdentity) {
      Role role = getRole(roleIdentity);
      return role != null && ((FSRole) role).isSysAdmin();
   }

   @Override
   public boolean isOrgAdministratorRole(IdentityID roleIdentity) {
      Role role = getRole(roleIdentity);
      return role != null && ((FSRole) role).isOrgAdmin();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void tearDown() {
      if(userStorage != null || groupStorage != null || roleStorage != null) {
         synchronized(this) {
            if(userStorage != null) {
               try {
                  userStorage.close();
               }
               catch(Exception e) {
                  LOG.warn("Failed to close user storage", e);
               }

               userStorage = null;
            }

            if(groupStorage != null) {
               try {
                  groupStorage.close();
               }
               catch(Exception e) {
                  LOG.warn("Failed to close group storage", e);
               }

               groupStorage = null;
            }

            if(roleStorage != null) {
               try {
                  roleStorage.close();
               }
               catch(Exception e) {
                  LOG.warn("Failed to close role storage", e);
               }

               roleStorage = null;
            }
         }
      }

      userGroupCache.invalidateAll();
      userRoleCache.invalidateAll();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void changePassword(IdentityID userIdentity, String password) {
      Objects.requireNonNull(password, "The password is required");
      init();
      FSUser fsUser = userStorage.get(userIdentity.convertToKey());

      if(fsUser == null) {
         throw new IllegalArgumentException("User \"" + userIdentity + "\" does not exist");
      }

      SUtil.setPassword(fsUser, password);

      try {
         userStorage.put(userIdentity.convertToKey(), fsUser).get();
         userGroupCache.invalidate(userIdentity);
         userRoleCache.invalidateAll();
      }
      catch(Exception e) {
         LOG.error("Failed to change password for {}", userIdentity, e);
      }
   }

   private int getNamedUserCount() {
      LicenseManager manager = LicenseManager.getInstance();
      return manager.getNamedUserCount() + manager.getNamedUserViewerSessionCount();
   }
   /**
    * {@inheritDoc}
    */
   @Override
   public void addOrganization(Organization organization) {
      init();

      try {
         organizationStorage.put(organization.getName(), (FSOrganization) organization).get();
      }
      catch(Exception e) {
         LOG.error("Failed to add organization {}", organization.getName(), e);
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void addUser(User user) {
      init();

      int namedUserCount = getNamedUserCount();
      int userCount = (int) userStorage.stream().count();
      FSUser fsUser = (FSUser) user;

      if(namedUserCount > 0 && !userStorage.contains(user.getName()) &&
         userCount >= namedUserCount)
      {
         Catalog catalog = Catalog.getCatalog(ThreadContext.getContextPrincipal());
         throw new MessageException(
            catalog.getString("em.namedUsers.exceeded", userCount, namedUserCount));
      }

      try {
         IdentityID userIdentity = user.getIdentityID();
         userStorage.put(userIdentity.convertToKey(), fsUser).get();
         userGroupCache.invalidate(userIdentity);
         userRoleCache.invalidateAll();
      }
      catch(Exception e) {
         LOG.error("Failed to add user {}", user.getName(), e);
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void setUser(IdentityID oldIdentity, User user) {
      init();
      lock.lock();

      try {
         userStorage.remove(oldIdentity.convertToKey()).get();
         userRoleCache.invalidateAll();

         IdentityID newUserIdentity = user.getIdentityID();

         if(!oldIdentity.equals(user.getName())) {
            processAuthenticationChange(oldIdentity, newUserIdentity, null, null, Identity.USER, false);
         }

         IdentityID userIdentity = user.getIdentityID();

         userStorage.put(userIdentity.convertToKey(), (FSUser) user).get();
         userGroupCache.invalidate(newUserIdentity);
         userRoleCache.invalidateAll();
      }
      catch(Exception e) {
         LOG.error("Failed to update user {}", oldIdentity, e);
      }
      finally {
         lock.unlock();
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void removeUser(IdentityID userIdentity) {
      init();
      lock.lock();

      try {
         userStorage.remove(userIdentity.convertToKey()).get();
         processAuthenticationChange(userIdentity, null, null, null, Identity.USER, true);
      }
      catch(Exception e) {
         LOG.error("Failed to remove user {}", userIdentity, e);
      }
      finally {
         lock.unlock();
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void addGroup(Group group) {
      init();

      try {
         IdentityID groupIdentity = group.getIdentityID();

         groupStorage.put(groupIdentity.convertToKey(), (FSGroup) group).get();
      }
      catch(Exception e) {
         LOG.error("Failed to add group {}", group.getName());
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void setGroup(IdentityID oldIdentity, Group group) {
      init();
      lock.lock();

      try {
         groupStorage.remove(oldIdentity.convertToKey()).get();
         userGroupCache.invalidateAll();
         userRoleCache.invalidateAll();
         IdentityID newIdentity = group.getIdentityID();

         if(!(oldIdentity.equals(group.getName()))) {
            processAuthenticationChange(oldIdentity, newIdentity, null, null, Identity.GROUP, false);
         }

         groupStorage.put(newIdentity.convertToKey(), (FSGroup) group).get();
      }
      catch(Exception e) {
         LOG.error("Failed to update group {}", group.getName(), e);
      }
      finally {
         lock.unlock();
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void removeGroup(IdentityID groupIdentity) {
      init();
      lock.lock();

      try {
         groupStorage.remove(groupIdentity.convertToKey()).get();
         userGroupCache.invalidateAll();
         processAuthenticationChange(groupIdentity, null, null, null, Identity.GROUP, true);
      }
      catch(Exception e) {
         LOG.error("Failed to remove group {}", groupIdentity, e);
      }
      finally {
         lock.unlock();
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void addRole(Role role) {
      init();

      try {
         IdentityID roleIdentity = role.getIdentityID();

         roleStorage.put(roleIdentity.convertToKey(), (FSRole) role).get();
         userRoleCache.invalidateAll();
      }
      catch(Exception e) {
         LOG.error("Failed to add role {}", role.getName());
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void setRole(IdentityID oldIdentity, Role role) {
      init();
      lock.lock();

      try {
         roleStorage.remove(oldIdentity.convertToKey()).get();
         userRoleCache.invalidateAll();
         IdentityID newIdentity = role.getIdentityID();

         if(!oldIdentity.equals(role.getIdentityID())) {
            processAuthenticationChange(oldIdentity, newIdentity, null, null, Identity.ROLE, false);
         }

         roleStorage.put(newIdentity.convertToKey(), (FSRole) role).get();
      }
      catch(Exception e) {
         LOG.error("Failed to update role {}", oldIdentity, e);
      }
      finally {
         lock.unlock();
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void removeRole(IdentityID roleIdentity) {
      init();
      lock.lock();

      try {
         roleStorage.remove(roleIdentity.convertToKey()).get();
         userRoleCache.invalidateAll();
         processAuthenticationChange(roleIdentity, null, null, null, Identity.ROLE, true);
      }
      catch(Exception e) {
         LOG.error("Failed to remove role {}", roleIdentity, e);
      }
      finally {
         lock.unlock();
      }
   }

   public void removeOrganization(String name) {
      init();
      lock.lock();

      try {
         String oldOrgID = getOrganization(name) != null ? getOrganization(name).getId() : null;
         organizationStorage.remove(name).get();
         processAuthenticationChange(new IdentityID(name, name), null, oldOrgID, null, Identity.ORGANIZATION, true);
      }
      catch(Exception e) {
         LOG.error("Failed to remove Organization {}", name, e);
      }
      finally {
         lock.unlock();
      }
   }

   private void processAuthenticationChange(IdentityID oldID, IdentityID newID, String oldOrgID,
                                            String newOrgID, int type,boolean removed)
   {
      try {
         XPrincipal principal = (XPrincipal) ThreadContext.getPrincipal();

         if(oldID.equals(newID) && !removed && Tool.equals(oldOrgID, newOrgID)) {
            return;
         }

         if(type == Identity.USER) {

            if(removed && getUser(oldID) !=null) {
               removeOrganizationMember(oldID);
            }
         }
         else if(type == Identity.GROUP) {

            if(removed && getGroup(oldID) !=null) {
               removeOrganizationMember(oldID);
            }

            List<FSUser> userList = userStorage.stream()
               .map(KeyValuePair::getValue)
               .filter(u -> Tool.equals(u.organization, oldID.organization))
               .collect(Collectors.toList());

            for(FSUser user : userList) {
               String[] groups = user.getGroups();

               if(removed || (newID != null && Arrays.asList(groups).contains(newID.name))) {
                  user.setGroups(Tool.remove(groups, oldID.name));
                  userStorage.put(user.getIdentityID().convertToKey(), user).get();
                  userGroupCache.invalidate(user.getIdentityID());
                  userRoleCache.invalidateAll();
               }
               else {
                  int index = Arrays.asList(groups).indexOf(oldID.name);

                  if(index >= 0) {
                     groups[index] = newID.name;
                     userStorage.put(user.getIdentityID().convertToKey(), user).get();
                     userGroupCache.invalidate(user.getIdentityID());
                     userRoleCache.invalidateAll();
                  }
               }
            }

            List<FSGroup> groupList = groupStorage.stream()
               .map(KeyValuePair::getValue)
               .filter(id -> Tool.equals(id.getOrganization(),oldID.organization))
               .collect(Collectors.toList());

            for(FSGroup group : groupList) {
               String[] groups = group.getGroups();
               IdentityID groupIdentity = group.getIdentityID();

               if(newID != null && Arrays.asList(groups).contains(newID.name) || removed) {
                  group.setGroups(Tool.remove(groups, oldID.name));
                  groupStorage.put(groupIdentity.convertToKey(), group).get();
               }
               else {
                  int index = Arrays.asList(groups).indexOf(oldID.name);

                  if(index >= 0 && newID != null) {
                     groups[index] = newID.name;
                     groupStorage.put(groupIdentity.convertToKey(), group).get();
                  }
               }
            }
         }
         else if(type == Identity.ROLE) {

            if(removed && getRole(oldID) !=null) {
               removeOrganizationMember(oldID);
            }

            List<FSRole> roleList = roleStorage.stream()
               .map(KeyValuePair::getValue)
               .filter(id -> id.getOrganization() == null || Tool.equals(id.getOrganization(),oldID.organization))
               .collect(Collectors.toList());

            for(FSRole role : roleList) {
               IdentityID[] roles = role.getRoles();
               IdentityID roleIdentity = role.getIdentityID();

               if(Arrays.asList(roles).contains(newID) || removed) {
                  role.setRoles(Tool.remove(roles, oldID));
                  roleStorage.put(roleIdentity.convertToKey(), role).get();
                  userRoleCache.invalidateAll();
               }
               else {
                  int index = Arrays.asList(roles).indexOf(oldID);

                  if(index >= 0) {
                     roles[index] = newID;
                     roleStorage.put(roleIdentity.convertToKey(), role).get();
                     userRoleCache.invalidateAll();
                  }
               }
            }

            List<FSGroup> groupList = groupStorage.stream()
               .map(KeyValuePair::getValue)
               .filter(id -> Tool.equals(id.getOrganization(),oldID.organization))
               .collect(Collectors.toList());

            for(FSGroup group : groupList) {
               IdentityID[] roles = group.getRoles();
               IdentityID groupIdentity = group.getIdentityID();

               if(Arrays.asList(roles).contains(newID) || removed) {
                  group.setRoles(Tool.remove(roles, oldID));
                  groupStorage.put(groupIdentity.convertToKey(), group).get();
               }
               else {
                  int index = Arrays.asList(roles).indexOf(oldID);

                  if(index >= 0) {
                     roles[index] = newID;
                     groupStorage.put(groupIdentity.convertToKey(), group).get();
                  }
               }
            }

            List<FSUser> userList = userStorage.stream()
               .map(KeyValuePair::getValue)
               .filter(id -> Tool.equals(id.getOrganization(),oldID.organization))
               .collect(Collectors.toList());

            for(FSUser user : userList) {
               IdentityID[] roles = user.getRoles();
               String[] groups = user.getGroups();
               IdentityID userIdentity = user.getIdentityID();

               if(Arrays.asList(roles).contains(newID) || removed) {
                  user.setRoles(Tool.remove(roles, oldID));
                  user.setGroups(Tool.remove(groups, oldID.name));
                  userStorage.put(userIdentity.convertToKey(), user).get();
                  userGroupCache.invalidate(userIdentity);
                  userRoleCache.invalidateAll();

                  if(Tool.equals(userIdentity, IdentityID.getIdentityIDFromKey(principal.getName()))) {
                     principal.setRoles(Tool.remove(principal.getRoles(), oldID));
                  }
               }
               else {
                  int index = Arrays.asList(roles).indexOf(oldID);

                  if(index >= 0) {
                     roles[index] = newID;
                     userStorage.put(user.getIdentityID().convertToKey(), user).get();
                     userGroupCache.invalidate(user.getIdentityID());
                     userRoleCache.invalidateAll();
                  }
               }
            }

            List<FSOrganization> orgList = organizationStorage.stream()
               .map(KeyValuePair::getValue)
               .collect(Collectors.toList());

            for(FSOrganization organization : orgList) {
               organizationStorage.put(organization.getName(), organization).get();
            }
         }
         else if(type == Identity.ORGANIZATION) {
            List<FSUser> userList = userStorage.stream()
               .map(KeyValuePair::getKey)
               .map(key -> IdentityID.getIdentityIDFromKey(key))
               .filter(id -> id.organization.equals(oldID.name))
               .map(id -> (FSUser) getUser(id))
               .collect(Collectors.toList());

            List<FSGroup> groupList = groupStorage.stream()
               .map(KeyValuePair::getKey)
               .map(key -> IdentityID.getIdentityIDFromKey(key))
               .filter(id -> id.organization.equals(oldID.name))
               .map(id -> (FSGroup) getGroup(id))
               .collect(Collectors.toList());

            List<FSRole> roleList = roleStorage.stream()
               .map(KeyValuePair::getKey)
               .map(key -> IdentityID.getIdentityIDFromKey(key))
               .filter(id -> id.organization != null && id.organization.equals(oldID.name))
               .map(id -> (FSRole) getRole(id))
               .collect(Collectors.toList());

            for(FSUser user : userList) {
               if(removed) {
                  removeUser(user.getIdentityID());
               }
               else {
                  user.setOrganization(newID.name);
                  setUser(user.getIdentityID(),user);
               }
            }

            for(FSGroup group : groupList) {
               if(removed) {
                  removeGroup(group.getIdentityID());
               }
               else {
                  group.setOrganization(newID.name);
                  setGroup(group.getIdentityID(),group);
               }
            }

            for(FSRole role : roleList) {
               if(removed) {
                  role.setOrganization(Organization.getDefaultOrganizationName());
                  setRole(role.getIdentityID(), role);
               }
               else {
                  role.setOrganization(newID.name);
                  setRole(role.getIdentityID(), role);
               }
            }
         }
      }
      catch(Exception e) {
         LOG.error("Failed to update security graph", e);
      }

      fireAuthenticationChanged(oldID, newID, oldOrgID, newOrgID, type, removed);
   }

   private void removeOrganizationMember(IdentityID oldIdentity) {
      Organization org = getOrganization(oldIdentity.organization);
      List<String> members = new ArrayList<>(Arrays.asList(org.getMembers()));
      members.remove(oldIdentity.name);
      org.setMembers(members.toArray(new String[0]));
      setOrganization(oldIdentity.organization,org);
   }

   private KeyValueStorage<FSUser> userStorage;
   private KeyValueStorage<FSGroup> groupStorage;
   private KeyValueStorage<FSRole> roleStorage;
   private KeyValueStorage<FSOrganization> organizationStorage;
   private final LoadingCache<IdentityID, String[]> userGroupCache = Caffeine.newBuilder()
      .expireAfterAccess(1L, TimeUnit.HOURS)
      .maximumSize(500L)
      .build(this::doGetUserGroups);
   private final LoadingCache<IdentityID, IdentityID[]> userRoleCache = Caffeine.newBuilder()
      .expireAfterAccess(1L, TimeUnit.HOURS)
      .maximumSize(500L)
      .build(this::doGetRoles);
   private final Lock lock = new ReentrantLock();

   private static final Logger LOG = LoggerFactory.getLogger(FileAuthenticationProvider.class);

   private static final class LoadUsersTask extends LoadKeyValueTask<FSUser> {
      public LoadUsersTask(String id) {
         super(id);
      }

      @Override
      protected Class<FSUser> initialize(Map<String, FSUser> map) {
         String defaultOrg = Organization.getDefaultOrganizationName();
         FSUser user = new FSUser(new IdentityID("admin", defaultOrg));
         HashedPassword hash = Tool.hash("admin", "bcrypt");
         user.setPassword(hash.getHash());
         user.setPasswordAlgorithm(hash.getAlgorithm());
         user.setRoles(new IdentityID[] { new IdentityID("Administrator", null),
                                       new IdentityID("Everyone", defaultOrg)});
         map.put(user.getIdentityID().convertToKey(), user);

         user = new FSUser(new IdentityID("guest", defaultOrg));
         hash = Tool.hash("success123", "bcrypt");
         user.setPassword(hash.getHash());
         user.setPasswordAlgorithm(hash.getAlgorithm());
         user.setRoles(new IdentityID[] { new IdentityID("Everyone", defaultOrg) });
         map.put(user.getIdentityID().convertToKey(), user);

         return FSUser.class;
      }

      @Override
      protected void validate(Map<String, FSUser> map) throws Exception {
         LicenseManager manager = LicenseManager.getInstance();
         int namedUserCount =
            manager.getNamedUserCount() + manager.getNamedUserViewerSessionCount();

         if(namedUserCount > 0 && map.size() > namedUserCount) {
            LOG.warn(
               "The number of defined users exceeds the number of licensed users. You " +
               "must remove users to ensure the users that you want are allowed access. " +
               "There are {} defined users and only {} licensed users",
               map.size(), namedUserCount);
         }
      }
   }

   private static final class LoadRolesTask extends LoadKeyValueTask<FSRole> {
      public LoadRolesTask(String id) {
         super(id);
      }

      @Override
      protected Class<FSRole> initialize(Map<String, FSRole> map) {
         // set Default Organization's Roles
         Map<String, FSRole> defaultOrgRoles = AuthenticationProvider.getDefaultRoles(Organization.getDefaultOrganizationName());
         for(FSRole role : defaultOrgRoles.values()) {
            map.put(role.getIdentityID().convertToKey(),role);
         }

         // set Self Organization's Roles
         Map<String, FSRole> selfOrgRoles = AuthenticationProvider.getDefaultRoles(Organization.getSelfOrganizationName());
         for(FSRole role : selfOrgRoles.values()) {
            map.put(role.getIdentityID().convertToKey(),role);
         }

         // set Template Organization's Roles
         Map<String, FSRole> templateOrgRoles = AuthenticationProvider.getDefaultRoles(Organization.getTemplateOrganizationName());
         for(FSRole role : templateOrgRoles.values()) {
            map.put(role.getIdentityID().convertToKey(),role);
         }

         //set Global Roles
         FSRole role = new FSRole(new IdentityID("Administrator", null), new IdentityID[0], "");
         role.setDefaultRole(false);
         role.setSysAdmin(true);
         map.put(role.getIdentityID().convertToKey(), role);

         role = new FSRole(new IdentityID("Organization Administrator", null), new IdentityID[0], "");
         role.setDefaultRole(false);
         role.setSysAdmin(false);
         role.setOrgAdmin(true);
         map.put(role.getIdentityID().convertToKey(), role);
         return FSRole.class;
      }
   }

   private static final class LoadOrganizationsTask extends LoadKeyValueTask<FSOrganization> {
      public LoadOrganizationsTask(String id) {
         super(id);
      }

      @Override
      protected Class<FSOrganization> initialize(Map<String, FSOrganization> map) {
         FSOrganization organization = new FSOrganization(Organization.getDefaultOrganizationName());
         organization.setId(Organization.getDefaultOrganizationID());
         organization.setMembers(getDefaultMembers(Organization.getDefaultOrganizationName()));
         map.put(organization.getName(), organization);

         FSOrganization selfOrganization = new FSOrganization(Organization.getSelfOrganizationName());
         selfOrganization.setId(Organization.getSelfOrganizationID());
         selfOrganization.setMembers(getDefaultMembers(Organization.getSelfOrganizationName()));
         map.put(selfOrganization.getName(), selfOrganization);

         FSOrganization templateOrganization = new FSOrganization(Organization.getTemplateOrganizationName());
         templateOrganization.setId(Organization.getTemplateOrganizationID());
         templateOrganization.setMembers(new String[0]);
         map.put(templateOrganization.getName(), templateOrganization);

         return FSOrganization.class;
      }
   }

   private static String[] getDefaultMembers(String orgName) {
      List<String> members = new ArrayList<>(AuthenticationProvider.getDefaultRoles(orgName).keySet().stream()
                            .map(IdentityID::getIdentityIDFromKey).map(id -> id.name).collect(Collectors.toList()));

      if(orgName.equals(Organization.getDefaultOrganizationName())) {
         members.add("admin");
         members.add("guest");
      }

      return members.toArray(new String[0]);
   }
}
