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
package inetsoft.sree.web.dashboard;

import inetsoft.sree.ClientInfo;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.internal.cluster.DistributedMap;
import inetsoft.sree.security.*;
import inetsoft.storage.*;
import inetsoft.uql.util.DefaultIdentity;
import inetsoft.uql.util.Identity;
import inetsoft.util.ConfigurationContext;
import inetsoft.util.Tool;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.function.BiConsumer;
import java.util.function.UnaryOperator;

/**
 * A dashboard manager is a manager of dashboard. It is used to read &
 * write information of dashboard.
 *
 * @version 8.5, 07/12/2006
 * @author InetSoft Technology Corp
 */
@SuppressWarnings("WeakerAccess")
@Service
@Lazy
public class DashboardManager implements AutoCloseable {
   /**
    * Construct.
    */
   public DashboardManager(SecurityEngine securityEngine,
                           DashboardRegistryManager dashboardRegistryManager,
                           KeyValueStorageManager keyValueStorageManager)
   {
      this.securityEngine = securityEngine;
      this.dashboardRegistryManager = dashboardRegistryManager;
      this.keyValueStorageManager = keyValueStorageManager;
   }

   @Override
   @PreDestroy
   public synchronized void close() throws Exception {
      getDashboardStorage().close();
   }

   private synchronized void init() {
      if(!(OrganizationManager.getInstance().getCurrentOrgID().equals(orgID))) {
         try {
            orgID = OrganizationManager.getInstance().getCurrentOrgID();
            syncUserDashboards();
         }
         catch(Exception e) {
            LOG.error("Failed to synchronize user dashboards", e);
         }
      }
   }

   /**
    * Runs an action while holding this manager's lock and the store lock of the cluster
    * (getStoreLock()). A dashboard registry renames or removes a dashboard through this, so that
    * the change to the stored selections and the change to the registry file are atomic with
    * respect to getDashboards(), which leaves out the selected names that are not in the
    * registries, and which re-reads both under the store lock before it removes such a name
    * (Bug #77299). A dashboard create adds the dashboard to the user registry and to the
    * selection through this, and a user rename moves both, so that a create either completes
    * before the move or runs after it (Bug #78101). The lock order is this manager, then the
    * store lock, then DashboardRegistryManager, then the user registry, then the global registry
    * (see DashboardRegistry for the registry file locks).
    */
   public synchronized void runLocked(Runnable action) {
      Lock storeLock = getStoreLock();
      storeLock.lock();

      try {
         action.run();
      }
      finally {
         storeLock.unlock();
      }
   }

   /**
    * Records that a user has been renamed. A user rename calls this in runLocked(), and a
    * dashboard create checks it in runLocked() with isRenamedUser(). A request of the old name
    * may still be running, or may even start, on another cluster node until the logout of the old
    * name reaches that node. Once the user is renamed the old name is not in the security
    * provider any more, so such a create can't be told apart from an SSO user who isn't in the
    * provider by the provider alone (Bug #78101). The record is kept in a replicated map, which a
    * put updates on every node before it returns, for RENAMED_USER_TIMEOUT.
    *
    * @param user    the old name of the user.
    * @param newUser the new name of the user.
    */
   public void addRenamedUser(IdentityID user, IdentityID newUser) {
      DistributedMap<String, RenamedUser> renamed = getRenamedUsers();
      long now = System.currentTimeMillis();
      Set<String> expired = new HashSet<>();

      for(Map.Entry<String, RenamedUser> entry : new ArrayList<>(renamed.entrySet())) {
         if(entry.getValue() == null || now - entry.getValue().time > RENAMED_USER_TIMEOUT) {
            expired.add(entry.getKey());
         }
      }

      if(!expired.isEmpty()) {
         renamed.removeAll(expired);
      }

      renamed.put(getRenamedUserKey(user), new RenamedUser(now, newUser));
   }

   /**
    * Checks if a user name has been renamed recently, see addRenamedUser().
    *
    * @param user the user name.
    *
    * @return true if a user of this name was renamed within RENAMED_USER_TIMEOUT.
    */
   public boolean isRenamedUser(IdentityID user) {
      return getRenamedUser(user) != null;
   }

   /**
    * Gets the new name of a user who has been renamed recently, see addRenamedUser().
    *
    * @param user the old name of the user.
    *
    * @return the new name, or null if no user of this name was renamed within
    *         RENAMED_USER_TIMEOUT.
    */
   public IdentityID getRenamedUser(IdentityID user) {
      RenamedUser renamed = user == null ? null : getRenamedUsers().get(getRenamedUserKey(user));
      return renamed != null && System.currentTimeMillis() - renamed.time <= RENAMED_USER_TIMEOUT ?
         renamed.newUser : null;
   }

   private static DistributedMap<String, RenamedUser> getRenamedUsers() {
      return Cluster.getInstance().getReplicatedMap(RENAMED_USERS_MAP);
   }

   private static String getRenamedUserKey(IdentityID user) {
      String org = user.orgID == null ? "" : user.orgID.toLowerCase();
      return new IdentityID(user.name, org).convertToKey();
   }

   /**
    * Return a dashboard manager.
    */
   public static DashboardManager getManager() {
      return ConfigurationContext.getContext().getSpringBean(DashboardManager.class);
   }

   /**
    * Gets a list of all users who have dashboards.
    *
    * @return all dashboard users.
    */
   public String[] getDashboardUsers() {
      init();
      return getDashboardStorage().keys()
         .filter(this::isUser)
         .map(this::getIdentityName)
         .toArray(String[]::new);
   }

   /**
    * Get dashboards of the specified identity which is a group or a role or a user.
    * @param identity the specified identity.
    * @return dashboards of the specified identity or <code>String[0]</code>.
    */
   public synchronized String[] getDashboards(Identity identity) {
      if(identity == null) {
         return new String[0];
      }

      return getDashboards(identity, true);
   }

   public synchronized String[] getDashboards(Identity identity, boolean sync) {
      init();

      if(identity.getType() == Identity.USER && sync &&
         !ClientInfo.ANONYMOUS.equals(identity.getName()))
      {
         try {
            syncUserDashboards(identity);
         }
         catch(Exception exc) {
            LOG.error(exc.getMessage(), exc);
         }
      }

      DashboardData data = getDashboardStorage().get(getIdentityKey(identity));

      if(data == null) {
         return new String[0];
      }

      List<String> values = data.getDashboards();
      DashboardRegistry uregistry = null;
      DashboardRegistry gregistry = dashboardRegistryManager.getRegistry();

      if(identity.getType() == Identity.USER) {
         uregistry = dashboardRegistryManager.getRegistry(identity.getIdentityID());
      }

      // The names that are not in the registries are left out. The registries are this node's
      // cached copies, which may not have loaded a dashboard that another node has just created
      // and selected (Bug #77272), or renamed (Bug #77299), so the stored record and the
      // registry files are then read again, see getRegisteredFromFiles().
      List<String> list = getRegistered(identity, values, uregistry, gregistry);

      if(list.size() < values.size()) {
         list = getRegisteredFromFiles(identity, sync, uregistry, gregistry);
      }

      return list.toArray(new String[0]);
   }

   /**
    * Gets the selected names that are in the cached registries. The names of an identity that
    * is not a user are all kept.
    */
   private static List<String> getRegistered(Identity identity, List<String> values,
                                             DashboardRegistry uregistry,
                                             DashboardRegistry gregistry)
   {
      List<String> list = new ArrayList<>();

      for(String dashboard : values) {
         if((uregistry != null && uregistry.getDashboard(dashboard) != null) ||
            identity.getType() != Identity.USER || gregistry.getDashboard(dashboard) != null)
         {
            list.add(dashboard);
         }
      }

      return list;
   }

   /**
    * Gets the selected names of an identity that are in the registries, after a selected name was
    * not found in this node's cached registries (Bug #77299). Holding the store lock, it reads the
    * stored record again and loads the registries from their files, and then leaves out the names
    * that are still not in them. A registry renames or removes a dashboard holding the store lock
    * across the change of the records and of its file (runLocked()), and a dashboard is created
    * in the registry file before it is selected, so a name that is still missing from an existing
    * registry file is really gone. It is then removed from the stored record if <i>prune</i> is
    * set, so that the next read doesn't read the files again. The stored record isn't changed if
    * a registry file couldn't be read, and a name isn't removed if the file of the registry it
    * belongs to doesn't exist: a user rename and an organization copy store the selections before
    * they write the registry files (see isGone()).
    *
    * @param prune true to remove the names that are really gone from the stored record. It must
    *              be false when the caller changes the record after this call from a copy that
    *              it read before.
    */
   private List<String> getRegisteredFromFiles(Identity identity, boolean prune,
                                               DashboardRegistry uregistry,
                                               DashboardRegistry gregistry)
   {
      Lock storeLock = getStoreLock();
      storeLock.lock();

      try {
         DashboardData data = getDashboardStorage().get(getIdentityKey(identity));

         if(data == null) {
            return new ArrayList<>();
         }

         List<String> values = data.getDashboards();
         boolean synced = syncRegistries(uregistry, gregistry);
         List<String> list = getRegistered(identity, values, uregistry, gregistry);

         if(prune && synced && list.size() < values.size()) {
            List<String> kept = new ArrayList<>();

            for(String dashboard : values) {
               if(list.contains(dashboard) || !isGone(dashboard, uregistry, gregistry)) {
                  kept.add(dashboard);
               }
            }

            if(kept.size() < values.size()) {
               setDashboards(identity, kept.toArray(new String[0]));
            }
         }

         return list;
      }
      finally {
         storeLock.unlock();
      }
   }

   /**
    * Checks if a selected name that is not in the registries, which have just been loaded from
    * their files, is really gone: the file of the registry it belongs to (the global registry for
    * a global dashboard, else the user registry) exists. A file that doesn't exist may be one
    * that is still to be written (Bug #77299).
    */
   private static boolean isGone(String dashboard, DashboardRegistry uregistry,
                                 DashboardRegistry gregistry)
   {
      DashboardRegistry owner = dashboard.contains("__GLOBAL") ? gregistry : uregistry;
      return owner != null && owner.isFileLoaded();
   }

   /**
    * Loads registries from their files if they have changed since the registries last loaded
    * them (Bug #77299). It is called holding the store lock, which is taken before the registry
    * locks.
    *
    * @return true if all the registries now hold what their files hold.
    */
   private static boolean syncRegistries(DashboardRegistry... registries) {
      boolean synced = true;

      for(DashboardRegistry registry : registries) {
         if(registry != null && !registry.syncWithFile()) {
            synced = false;
         }
      }

      return synced;
   }

   /**
    * Gets the names of the global dashboards that have been deselected by a
    * user.
    *
    * @param identity the identity of the user.
    *
    * @return the deselected dashboard names.
    */
   public synchronized String[] getDeselectedDashboards(Identity identity) {
      init();
      Lock storeLock = getStoreLock();
      storeLock.lock();

      try {
         DashboardData data = getDashboardStorage().get(getIdentityKey(identity));
         List<String> values = data == null ? null : data.getDeselected();

         List<String> list = new ArrayList<>();
         List<String> added = new ArrayList<>();
         List<String> stored = values == null ? new ArrayList<>() : new ArrayList<>(values);
         boolean pruned = false;
         DashboardRegistry registry = dashboardRegistryManager.getRegistry();

         // the names that are not in this node's cached registry are left out. The registry is
         // then loaded from its file, and the names that are still not in an existing file are
         // really gone and are removed from the stored record, see getRegisteredFromFiles()
         // (Bug #77299)
         if(values != null) {
            list = getDeselectedRegistered(values, registry);

            if(list.size() < values.size()) {
               boolean synced = syncRegistries(registry);
               list = getDeselectedRegistered(values, registry);

               if(synced && registry.isFileLoaded() && list.size() < values.size()) {
                  stored = new ArrayList<>(list);
                  pruned = true;
               }
            }
         }

         if(identity.getType() == Identity.USER) {
            IdentityID userId = identity.getIdentityID();

            if(OrganizationManager.getInstance().isOrgAdmin(userId) ||
               OrganizationManager.getInstance().isSiteAdmin(userId))
            {
               List<String> selectedDashboards = Arrays.asList(getDashboards(identity, false));

               // go through all the global dashboards
               for(String dashboard : registry.getDashboardNames()) {
                  if(!selectedDashboards.contains(dashboard) && !list.contains(dashboard)) {
                     list.add(dashboard);
                     added.add(dashboard);
                  }
               }
            }
         }

         if(!added.isEmpty() || pruned) {
            // only the added names are stored, on top of the stored record without the names
            // that are really gone
            List<String> nstored = stored;
            added.stream().filter(d -> !nstored.contains(d)).forEach(nstored::add);
            setDeselectedDashboards(identity, nstored.toArray(new String[0]));
         }

         return list.toArray(new String[0]);
      }
      finally {
         storeLock.unlock();
      }
   }

   /**
    * Gets the deselected names that are in the cached global registry.
    */
   private static List<String> getDeselectedRegistered(List<String> values,
                                                       DashboardRegistry registry)
   {
      List<String> list = new ArrayList<>();

      for(String dashboard : values) {
         if(registry.getDashboard(dashboard) != null) {
            list.add(dashboard);
         }
      }

      return list;
   }

   /**
    * Rename an identity.
    * @param oiden the old identity.
    * @param niden the new identity.
    */
   public synchronized void renameIdentity(Identity oiden, Identity niden) {
      init();

      try {
         if(oiden != null && niden != null && !oiden.equals(niden)) {
            String okey = getIdentityKey(oiden);
            String nkey = getIdentityKey(niden);
            getDashboardStorage().rename(okey, nkey).get(10L, TimeUnit.SECONDS);
         }
         else if(oiden == null && niden != null) {
            // delete
            getDashboardStorage().remove(getIdentityKey(niden)).get(10L, TimeUnit.SECONDS);
         }
      }
      catch(ExecutionException | InterruptedException | TimeoutException e) {
         throw new RuntimeException(e);
      }
   }

   /**
    * Rename a dashboard.
    * @param oname the old name.
    * @param name the new name.
    */
   public synchronized void renameDashboard(String oname, String name) {
      init();

      Lock storeLock = getStoreLock();
      storeLock.lock();

      try {
         SortedMap<String, DashboardData> changes = new TreeMap<>();
         KeyValueStorage<DashboardData> dashboardStorage = getDashboardStorage();
         dashboardStorage.stream()
               .forEach(p -> renameDashboard(oname, name, p, changes));
         dashboardStorage.putAll(changes).get(60L, TimeUnit.SECONDS);
      }
      catch(InterruptedException | TimeoutException | ExecutionException e) {
         throw new RuntimeException(e);
      }
      finally {
         storeLock.unlock();
      }
   }

   private void renameDashboard(String oname, String nname, KeyValuePair<DashboardData> pair,
                                Map<String, DashboardData> map)
   {
      DashboardData data = pair.getValue();
      List<String> dashboards = new ArrayList<>();
      List<String> deselected = new ArrayList<>();
      boolean modified = false;

      for(String dashboard : data.getDashboards()) {
         if(dashboard.equals(nname)) {
            modified = true;
         }
         else if(dashboard.equals(oname)) {
            dashboards.add(nname);
            modified = true;
         }
         else {
            dashboards.add(dashboard);
         }
      }

      for(String dashboard : data.getDeselected()) {
         if(dashboard.equals(nname)) {
            modified = true;
         }
         else if(dashboard.equals(oname)) {
            deselected.add(nname);
            modified = true;
         }
         else {
            deselected.add(dashboard);
         }
      }

      if(modified) {
         DashboardData changed = new DashboardData();
         changed.setDashboards(dashboards);
         changed.setDeselected(deselected);
         changed.setUserChanged(data.isUserChanged());
         map.put(pair.getKey(), changed);
      }
   }

   /**
    * Remove a dashboard.
    * @param name the name of dashboard.
    */
   public synchronized void removeDashboard(String name) {
      init();

      Lock storeLock = getStoreLock();
      storeLock.lock();

      try {
         SortedMap<String, DashboardData> changes = new TreeMap<>();
         KeyValueStorage<DashboardData> dashboardStorage = getDashboardStorage();
         dashboardStorage.stream()
            .forEach(p -> removeDashboard(name, p, changes));
         dashboardStorage.putAll(changes).get(60L, TimeUnit.SECONDS);
      }
      catch(InterruptedException | TimeoutException | ExecutionException e) {
         throw new RuntimeException(e);
      }
      finally {
         storeLock.unlock();
      }
   }

   private void removeDashboard(String name, KeyValuePair<DashboardData> pair, Map<String, DashboardData> map) {
      List<String> dashboards = new ArrayList<>();
      List<String> deselected = new ArrayList<>();
      boolean modified = false;

      for(String dashboard : pair.getValue().getDashboards()) {
         if(dashboard.equals(name)) {
            modified = true;
         }
         else {
            dashboards.add(dashboard);
         }
      }

      for(String dashboard : pair.getValue().getDeselected()) {
         if(dashboard.equals(name)) {
            modified = true;
         }
         else {
            deselected.add(dashboard);
         }
      }

      if(modified) {
         DashboardData changed = new DashboardData();
         changed.setDashboards(dashboards);
         changed.setDeselected(deselected);
         changed.setUserChanged(pair.getValue().isUserChanged());
         map.put(pair.getKey(), changed);
      }
   }

   /**
    * Get dashboards of the specified user.
    * @param user the specified user name.
    * @return dashboards of the specified user or <code>String[0]</code>.
    */
   public synchronized String[] getUserDashboards(Identity user) throws Exception {
      init();

      if(user == null) {
         return new String[0];
      }

      IdentityID userIdentityID = user.getIdentityID();
      String[] groups = user.getGroups();
      IdentityID[] roles = user.getRoles();
      boolean empty = (groups == null || groups.length == 0) &&
         (roles == null || roles.length == 0);
      SecurityProvider provider = securityEngine.getSecurityProvider();

      if(provider.isVirtual() || (provider.getUser(userIdentityID) != null && empty)) {
         return getUserDashboards(userIdentityID);
      }

      // @by billh, fix customer bug bug1303944306880
      // handle SSO problem
      List<String> list = new ArrayList<>();
      Identity userIdentity = provider.getUser(userIdentityID);
      userIdentity = userIdentity == null ? user : userIdentity;
      String[] dashboards = getDashboards(userIdentity, false);

      for(String dashboard : dashboards) {
         if(!list.contains(dashboard)) {
            list.add(dashboard);
         }
      }

      if(userIdentity != null) {
         Set<String> visitedGroups = new HashSet<>();
         Set<IdentityID> visitedRoles = new HashSet<>();
         Principal principal = new SRPrincipal(userIdentity.getIdentityID(), userIdentity.getRoles(),
                                               userIdentity.getGroups(),
                                               userIdentity.getOrganizationID(), 0L);

         if(groups != null) {
            for(String groupName : groups) {
               if(!visitedGroups.contains(groupName)) {
                  Group group = provider.getGroup(new IdentityID(groupName, userIdentity.getOrganizationID()));

                  if(group != null) {
                     getGroupDashboards(
                        group, provider, list, visitedGroups, visitedRoles, principal);
                  }
               }
            }
         }

         if(roles != null) {
            for(IdentityID roleName : roles) {
               if(!visitedRoles.contains(roleName)) {
                  Role role = provider.getRole(roleName);

                  if(role != null) {
                     getRoleDashboards(role, provider, list, visitedRoles, principal);
                  }
               }
            }
         }
      }

      return list.toArray(new String[0]);
   }

   /**
    * Get dashboards of the specified user.
    * @param userName the specified user name.
    * @return dashboards of the specified user or <code>String[0]</code>.
    */
   public synchronized String[] getUserDashboards(IdentityID userName) {
      init();
      SecurityProvider provider = securityEngine.getSecurityProvider();
      Identity user;

      // treat user anonymous as a special role
      if(provider.isVirtual() || ClientInfo.ANONYMOUS.equals(userName.name)) {
         user = new DefaultIdentity(userName, Identity.USER);
      }
      else if((user = provider.getUser(userName)) == null) {
         return new String[0];
      }

      List<String> list = new ArrayList<>();

      for(String dashboard : getDashboards(user, false)) {
         if(!list.contains(dashboard)) {
            list.add(dashboard);
         }
      }

      try {
         Set<String> visitedGroups = new HashSet<>();
         Set<IdentityID> visitedRoles = new HashSet<>();
         Principal principal = new SRPrincipal(user.getIdentityID());
         for(String groupName : user.getGroups()) {
            if(!visitedGroups.contains(groupName)) {
               getGroupDashboards(
                  provider.getGroup(new IdentityID(groupName, user.getOrganizationID())), provider, list, visitedGroups, visitedRoles, principal);
            }
         }

         for(IdentityID role : user.getRoles()) {
            if(!visitedRoles.contains(role)) {
               getRoleDashboards(provider.getRole(role), provider, list, visitedRoles, principal);
            }
         }
      }
      catch(Exception exc) {
         LOG.error(exc.getMessage(), exc);
      }

      return list.toArray(new String[0]);
   }

   private void getGroupDashboards(Group group, SecurityProvider provider, List<String> list,
                                   Set<String> visitedGroups,
                                   Set<IdentityID> visitedRoles, Principal principal) throws Exception
   {
      if(group == null) {
         return;
      }

      visitedGroups.add(group.getName());

      for(String dashboard : getDashboards(group)) {
         if(!list.contains(dashboard) && securityEngine.checkPermission(
            principal, ResourceType.DASHBOARD, dashboard, ResourceAction.ACCESS))
         {
            list.add(dashboard);
         }
      }

      for(String groupName : group.getGroups()) {
         if(!visitedGroups.contains(groupName)) {
            getGroupDashboards(
               provider.getGroup(new IdentityID(groupName, group.getOrganizationID())), provider, list, visitedGroups,
               visitedRoles, principal);
         }
      }


      for(IdentityID role : group.getRoles()) {
         if(!visitedRoles.contains(role)) {
            getRoleDashboards(provider.getRole(role), provider, list, visitedRoles, principal);
         }
      }
   }

   private void getRoleDashboards(Role role, SecurityProvider provider, List<String> list,
                                  Set<IdentityID> visitedRoles, Principal principal)
      throws Exception
   {
      if(role == null) {
         return;
      }

      visitedRoles.add(role.getIdentityID());

      for(String dashboard : getDashboards(role)) {
         if(!list.contains(dashboard) && securityEngine.checkPermission(
            principal, ResourceType.DASHBOARD, dashboard, ResourceAction.ACCESS))
         {
            list.add(dashboard);
         }
      }

      for(IdentityID roleName : role.getRoles()) {
         if(!visitedRoles.contains(roleName)) {
            getRoleDashboards(
               provider.getRole(roleName), provider, list, visitedRoles, principal);
         }
      }
   }

   // The setters below read the whole DashboardData record, change one field and put it back, so
   // they hold this manager's monitor (77232) and the store lock of the cluster (getStoreLock(),
   // since the record is shared by all nodes, 77272) for the whole read-modify-write. Lock order
   // is this manager, then the store lock, then DashboardRegistryManager.lock, then the user
   // registry, then the global registry.
   // The setters take only this manager's monitor and no registry locks, so they must not be
   // called while holding DashboardRegistryManager.lock or a registry monitor; a registry calls
   // them only inside runLocked(), before it locks itself.
   /**
    * Set dashboards to specified identity.
    * @param identity the specified identity.
    * @param dashboards the specified dashboards.
    */
   public void setDashboards(Identity identity, String[] dashboards) {
      setDashboards(identity, dashboards, null);
   }

   /**
    * Sets the dashboards for the specified identity.
    *
    * @param identity    the identity.
    * @param dashboards  the selected dashboard names.
    * @param userChanged a flag that indicates if the dashboards were selected by the user. If
    *                    {@code null}, the existing value will be retained.
    */
   public synchronized void setDashboards(Identity identity, String[] dashboards,
                                          Boolean userChanged)
   {
      init();
      KeyValueStorage<DashboardData> dashboardStorage = getDashboardStorage();
      Lock storeLock = getStoreLock();
      storeLock.lock();

      try {
         if(dashboards == null) {
            String key = getIdentityKey(identity);
            DashboardData data = dashboardStorage.get(key);

            if(data != null) {
               data.setDashboards(new ArrayList<>());

               try {
                  dashboardStorage.put(key, data).get(10L, TimeUnit.SECONDS);
               }
               catch(InterruptedException | ExecutionException | TimeoutException e) {
                  throw new RuntimeException("Failed to save dashboard", e);
               }
            }
         }
         else if(identity != null && identity.getName() != null) {
            String key = getIdentityKey(identity);
            DashboardData data = dashboardStorage.get(key);

            if(data == null) {
               data = new DashboardData();
            }

            data.setDashboards(new ArrayList<>(Arrays.asList(dashboards)));

            if(userChanged != null) {
               data.setUserChanged(userChanged);
            }

            try {
               dashboardStorage.put(key, data).get(10L, TimeUnit.SECONDS);
            }
            catch(InterruptedException | ExecutionException | TimeoutException e) {
               throw new RuntimeException("Failed to save dashboard", e);
            }
         }
      }
      finally {
         storeLock.unlock();
      }
   }

   public synchronized void removeDashboards(Identity identity) {
      init();
      KeyValueStorage<DashboardData> dashboardStorage = getDashboardStorage();

      if(identity.getName() != null) {
         String key = getIdentityKey(identity);
         DashboardData data = dashboardStorage.get(key);

         if(data == null) {
            return;
         }

         try {
            dashboardStorage.remove(key).get(10L, TimeUnit.SECONDS);
         }
         catch(InterruptedException | ExecutionException | TimeoutException e) {
            throw new RuntimeException(e);
         }
      }
   }

   /**
    * Sets the names of the global dashboards that have been deselected by a
    * user.
    *
    * @param identity   the identity of the user.
    * @param dashboards the names of the deselected dashboards.
    */
   public synchronized void setDeselectedDashboards(Identity identity, String[] dashboards) {
      init();
      KeyValueStorage<DashboardData> dashboardStorage = getDashboardStorage();
      Lock storeLock = getStoreLock();
      storeLock.lock();

      try {
         if(dashboards == null) {
            String key = getIdentityKey(identity);
            DashboardData data = dashboardStorage.get(key);

            if(data != null) {
               data.setDeselected(new ArrayList<>());

               try {
                  dashboardStorage.put(key, data).get(10L, TimeUnit.SECONDS);
               }
               catch(InterruptedException | ExecutionException | TimeoutException e) {
                  throw new RuntimeException("Failed to deselected dashboard", e);
               }
            }
         }
         else if(identity.getName() != null) {
            String key = getIdentityKey(identity);
            DashboardData data = dashboardStorage.get(key);

            if(data == null) {
               data = new DashboardData();
            }

            data.setDeselected(new ArrayList<>(Arrays.asList(dashboards)));

            try {
               dashboardStorage.put(key, data).get(10L, TimeUnit.SECONDS);
            }
            catch(InterruptedException | ExecutionException | TimeoutException e) {
               throw new RuntimeException("Failed to deselected dashboard", e);
            }
         }
      }
      finally {
         storeLock.unlock();
      }
   }

   /**
    * Add a dashboard to specified identity.
    */
   public synchronized void addDashboard(Identity identity, String dashboard) {
      if(identity == null) {
         return;
      }

      init();
      Lock storeLock = getStoreLock();
      storeLock.lock();

      try {
         if(identity.getType() == Identity.USER &&
            !ClientInfo.ANONYMOUS.equals(identity.getName()))
         {
            try {
               syncUserDashboards(identity);
            }
            catch(Exception exc) {
               LOG.error(exc.getMessage(), exc);
            }
         }

         // appended to the stored names, not to getDashboards(), which leaves out the names
         // that are not in this node's cached registries (Bug #77272)
         updateDashboards(identity, dashboards -> {
            if(Arrays.asList(dashboards).contains(dashboard)) {
               return dashboards;
            }

            String[] narr = Arrays.copyOf(dashboards, dashboards.length + 1);
            narr[narr.length - 1] = dashboard;
            return narr;
         });
      }
      finally {
         storeLock.unlock();
      }
   }

   /**
    * Changes the stored selected dashboards of an identity, holding the store lock for the whole
    * read-modify-write. The stored names are changed, including the names that are not in this
    * node's cached registries, which getDashboards(Identity) leaves out, so that a dashboard
    * that another node has just created and selected is kept (Bug #77272).
    *
    * A caller that reorders or adds to a selection uses this instead of getDashboards() and then
    * setDashboards(), which would overwrite a dashboard created or renamed in between, and drop
    * the names that getDashboards() leaves out (Bug #77872).
    *
    * @param identity the identity, nothing is changed if it is {@code null}.
    * @param change   returns the new names from the stored names.
    */
   public synchronized void updateDashboards(Identity identity, UnaryOperator<String[]> change) {
      if(identity == null) {
         return;
      }

      init();
      Lock storeLock = getStoreLock();
      storeLock.lock();

      try {
         DashboardData data = getDashboardStorage().get(getIdentityKey(identity));
         String[] dashboards = data == null ?
            new String[0] : data.getDashboards().toArray(new String[0]);
         String[] ndashboards = change.apply(dashboards);

         if(!Arrays.equals(dashboards, ndashboards)) {
            setDashboards(identity, ndashboards);
         }
      }
      finally {
         storeLock.unlock();
      }
   }

   /**
    * Changes the stored selected and deselected dashboards of an identity together, holding the
    * store lock for the whole read-modify-write, like updateDashboards(Identity, UnaryOperator)
    * (Bug #77872). The change is given modifiable copies of the stored lists, including the names
    * that getDashboards(Identity) and getDeselectedDashboards(Identity) leave out, and only a list
    * it changed is written back. The user-changed flag of the record is kept.
    *
    * @param identity the identity, nothing is changed if it is {@code null}.
    * @param change   changes the selected (first) and deselected (second) names in place.
    */
   public synchronized void updateDashboardLists(Identity identity,
                                                 BiConsumer<List<String>, List<String>> change)
   {
      if(identity == null) {
         return;
      }

      init();
      Lock storeLock = getStoreLock();
      storeLock.lock();

      try {
         DashboardData data = getDashboardStorage().get(getIdentityKey(identity));
         List<String> dashboards = data == null ?
            new ArrayList<>() : new ArrayList<>(data.getDashboards());
         List<String> deselected = data == null ?
            new ArrayList<>() : new ArrayList<>(data.getDeselected());
         List<String> ndashboards = new ArrayList<>(dashboards);
         List<String> ndeselected = new ArrayList<>(deselected);
         change.accept(ndashboards, ndeselected);

         if(!dashboards.equals(ndashboards)) {
            setDashboards(identity, ndashboards.toArray(new String[0]));
         }

         if(!deselected.equals(ndeselected)) {
            setDeselectedDashboards(identity, ndeselected.toArray(new String[0]));
         }
      }
      finally {
         storeLock.unlock();
      }
   }

   public void removeDashboardStorage(String orgID) throws Exception {
      getDashboardStorage(orgID).deleteStore().get(1L, TimeUnit.MINUTES);
      getDashboardStorage(orgID).close();
   }

   public void copyStorageData(String oId, String id) {
      KeyValueStorage<DashboardData> oStorage = getDashboardStorage(oId);
      KeyValueStorage<DashboardData> nStorage = getDashboardStorage(id);
      SortedMap<String, DashboardData> data = new TreeMap<>();
      oStorage.stream().forEach(pair -> data.put(pair.getKey(), pair.getValue()));

      try {
         nStorage.putAll(data).get(3L, TimeUnit.MINUTES);
      }
      catch(InterruptedException | TimeoutException | ExecutionException e) {
         throw new RuntimeException(e);
      }
   }

   private KeyValueStorage<DashboardData> getDashboardStorage() {
      return getDashboardStorage(null);
   }

   private KeyValueStorage<DashboardData> getDashboardStorage(String orgID) {
      if(orgID == null) {
         orgID = OrganizationManager.getInstance().getCurrentOrgID();
      }

      String storeID = orgID.toLowerCase() + "__dashboards";
      return keyValueStorageManager.getStorage(storeID, new LoadDashboardsTask(storeID));
   }

   /**
    * Gets the cluster lock of the current org's dashboards store (Bug #77272). The records are
    * shared by all cluster nodes and KeyValueStorage has no compare-and-set, so a read-modify-write
    * of the records holds this lock, not only this manager's monitor, which is local to a node. It
    * is taken after this manager's monitor, and before the DashboardRegistryManager lock and the
    * registry locks.
    */
   private Lock getStoreLock() {
      String orgID = OrganizationManager.getInstance().getCurrentOrgID();
      return Cluster.getInstance().getLock(STORE_LOCK_PREFIX + orgID.toLowerCase());
   }

   private synchronized void syncUserDashboards() throws Exception {
      Lock storeLock = getStoreLock();
      storeLock.lock();

      try {
         KeyValueStorage<DashboardData> dashboardStorage = getDashboardStorage();
         SortedMap<String, DashboardData> changes = new TreeMap<>();
         dashboardStorage.stream().forEach(p -> syncUserDashboards(p, changes));

         if(!changes.isEmpty()) {
            dashboardStorage.putAll(changes).get(3L, TimeUnit.MINUTES);
         }
      }
      finally {
         storeLock.unlock();
      }
   }

   private void syncUserDashboards(KeyValuePair<DashboardData> pair, Map<String, DashboardData> map) {
      Identity identity = getIdentity(pair.getKey());

      if(identity.getType() == Identity.USER) {
         try {
            List<String> selected = syncUserDashboards(identity, pair.getValue().getDashboards());

            if(selected != null) {
               // read again, the sync may have stored the deselected names (Bug #77299)
               DashboardData current = getDashboardStorage().get(pair.getKey());
               current = current == null ? pair.getValue() : current;
               DashboardData changed = new DashboardData();
               changed.setDashboards(selected);
               changed.setDeselected(current.getDeselected());
               changed.setUserChanged(current.isUserChanged());
               map.put(pair.getKey(), changed);
            }
         }
         catch(Exception e) {
            throw new RuntimeException("Failed to synchronize user dashboards", e);
         }
      }
   }

   private synchronized void syncUserDashboards(Identity user) throws Exception {
      Lock storeLock = getStoreLock();
      storeLock.lock();

      try {
         KeyValueStorage<DashboardData> dashboardStorage = getDashboardStorage();
         DashboardData data = dashboardStorage.get(getIdentityKey(user));

         if(data == null) {
            data = new DashboardData();
         }

         List<String> selected = data.getDashboards();
         List<String> nselected = syncUserDashboards(user, selected);

         if(nselected == null) {
            nselected = new ArrayList<>();
         }

         if(!Tool.equals(selected, nselected)) {
            // read again, the sync may have stored the deselected names (Bug #77299)
            DashboardData current = dashboardStorage.get(getIdentityKey(user));
            data = current == null ? data : current;
            data.setDashboards(nselected);
            dashboardStorage.put(getIdentityKey(user), data).get(10L, TimeUnit.SECONDS);
         }
      }
      finally {
         storeLock.unlock();
      }
   }

   private List<String> syncUserDashboards(Identity user, List<String> selected) throws Exception {
      DashboardRegistry reg = dashboardRegistryManager.getRegistry(user.getIdentityID());
      Set<String> deselected = new HashSet<>(Arrays.asList(getDeselectedDashboards(user)));
      String[] global = getUserDashboards(user);
      List<String> nselected = new ArrayList<>();
      List<String> added = new ArrayList<>();
      List<String> removed = new ArrayList<>();
      boolean hasUserDashBoard = false;

      DashboardData data = getDashboardStorage().get(getIdentityKey(user));
      boolean userChanged = data != null && data.userChanged;

      for(String name : selected) {
         boolean found = !name.contains("__GLOBAL") ||
            (reg != null && reg.getDashboard(name) != null);

         if(found && !hasUserDashBoard) {
            hasUserDashBoard = true;
         }

         for(int j = 0; !found && j < global.length; j++) {
            if(global[j].equals(name)) {
               found = true;
               break;
            }
         }

         if(!found) {
            removed.add(name);
         }
      }

      // A global dashboard that another node has just renamed is not in this node's cached
      // global registry yet. The removed names are stored, so a name is only removed if it is
      // still not in the registries once they are loaded from their files, and none is removed
      // if a file couldn't be read or the global registry file doesn't exist, see
      // getRegisteredFromFiles() (Bug #77299).
      if(!removed.isEmpty()) {
         DashboardRegistry greg = dashboardRegistryManager.getRegistry();

         if(syncRegistries(reg, greg) && greg.isFileLoaded()) {
            removed.removeIf(name -> reg != null && reg.getDashboard(name) != null ||
               greg.getDashboard(name) != null);
         }
         else {
            removed.clear();
         }
      }

      for(String name : global) {
         if(!selected.contains(name) && !deselected.contains(name)) {
            added.add(name);
         }
      }

      if(added.size() > 0 || removed.size() >= 0) {
         for(String name : selected) {
            if(!removed.contains(name)) {
               nselected.add(name);
            }
         }

         nselected.addAll(added);

         // reset dashboard order by global dashboard setting if user doesn't
         // change dashboards by himself, see bug1331073048986
         if(user.getType() == Identity.USER && !hasUserDashBoard && !userChanged) {
            List<String> temp = new ArrayList<>();

            for(String name : global) {
               if(nselected.contains(name)) {
                  temp.add(name);
               }
            }

            for(String name : nselected) {
               if(!temp.contains(name)) {
                  temp.add(name);
               }
            }

            nselected = temp;
         }
      }

      return nselected.isEmpty() ? null : nselected;
   }

   private boolean isUser(String key) {
      int index = key.indexOf(':');
      return index > 1 && Integer.parseInt(key.substring(0, index)) == Identity.USER;
   }

   private String getIdentityName(String key) {
      int index = key.indexOf(':');
      return index < 0 ? key : key.substring(index + 1);
   }

   private Identity getIdentity(String key) {
      int index = key.indexOf(':');
      int type = index < 0 ? Identity.USER : Integer.parseInt(key.substring(0, index));
      String name = index < 0 ? key : key.substring(index + 1);
      return new DefaultIdentity(name, this.orgID, type);
   }

   private String getIdentityKey(Identity identity) {
      return identity.getType() + ":" + identity.getName();
   }

   private final SecurityEngine securityEngine;
   private final DashboardRegistryManager dashboardRegistryManager;
   private final KeyValueStorageManager keyValueStorageManager;
   private String orgID = null;
   // the prefix of the cluster lock name of a dashboards store, see getStoreLock()
   private static final String STORE_LOCK_PREFIX = DashboardManager.class.getName() + ".lock:";
   // the replicated map of the recently renamed users, see addRenamedUser()
   private static final String RENAMED_USERS_MAP = DashboardManager.class.getName() + ".renamedUsers";
   // how long a renamed user's old name is kept, longer than a request of the old name can run
   // or a logout can take to reach every node
   private static final long RENAMED_USER_TIMEOUT = TimeUnit.MINUTES.toMillis(30);
   private static final Logger LOG = LoggerFactory.getLogger(DashboardManager.class);

   // a record of the replicated map of the recently renamed users, see addRenamedUser()
   static final class RenamedUser implements Serializable {
      RenamedUser(long time, IdentityID newUser) {
         this.time = time;
         this.newUser = newUser;
      }

      final long time;
      final IdentityID newUser;
   }

   public static final class DashboardData implements Serializable {
      public List<String> getDashboards() {
         if(dashboards == null) {
            dashboards = new ArrayList<>();
         }

         return dashboards;
      }

      public void setDashboards(List<String> dashboards) {
         this.dashboards = dashboards;
      }

      public List<String> getDeselected() {
         if(deselected == null) {
            deselected = new ArrayList<>();
         }

         return deselected;
      }

      public void setDeselected(List<String> deselected) {
         this.deselected = deselected;
      }

      public boolean isUserChanged() {
         return userChanged;
      }

      public void setUserChanged(boolean userChanged) {
         this.userChanged = userChanged;
      }

      private List<String> dashboards;
      private List<String> deselected;
      private boolean userChanged = false;
   }

   private static final class LoadDashboardsTask extends LoadKeyValueTask<DashboardData> {
      LoadDashboardsTask(String id) {
         super(id);
      }

      @Override
      protected void validate(Map<String, DashboardData> map) throws Exception {
         SecurityProvider security = getServiceBean(SecurityEngine.class).getSecurityProvider();

         for(Map.Entry<String, DashboardData> e : map.entrySet()) {
            int index = e.getKey().indexOf(':');
            int type = Integer.parseInt(e.getKey().substring(0, index));
            String identity = e.getKey().substring(index + 1);

            for(String name : e.getValue().getDashboards()) {
               Permission permission = security.getPermission(ResourceType.DASHBOARD, name);

               if(permission != null) {
                  String orgId = OrganizationManager.getInstance().getCurrentOrgID();
                  Set<Permission.PermissionIdentity> grants = permission.getGrants(ResourceAction.ACCESS, type);
                  grants.add(new Permission.PermissionIdentity(identity, orgId));
                  permission.setGrants(ResourceAction.ACCESS, type, grants);
               }

               security.setPermission(ResourceType.DASHBOARD, name, permission);
            }
         }
      }
   }
}
