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
    * Runs an action while holding this manager's lock. A dashboard registry renames or removes
    * a dashboard through this, so that the change to the stored selections and the change to the
    * registry are atomic with respect to getDashboards(), which prunes the selected names that are
    * not in the registries. The lock order is this manager, then DashboardRegistryManager, then
    * the user registry, then the global registry.
    *
    * <p>Also holds a cluster-wide lock keyed on this org's dashboards store (Bug #77299), on top
    * of this manager's own monitor, for the whole action, since the action typically reads the
    * current selection with one call and writes a computed replacement with another (e.g.
    * {@code DashboardRegistry.UserDashboardRegistry.renameDashboard}), and the two calls must not
    * be split by a concurrent write from another cluster node in between.
    */
   synchronized void runLocked(Runnable action) {
      String lockName = getDashboardsLockName();
      Cluster.getInstance().lockKey(lockName);

      try {
         action.run();
      }
      finally {
         Cluster.getInstance().unlockKey(lockName);
      }
   }

   /**
    * The cluster-wide lock name for this org's dashboards KeyValueStorage (Bug #77299). The
    * underlying store is replicated/cluster-wide (see the class-level setters comment below), so
    * a plain per-JVM {@code synchronized} does not exclude a concurrent read-modify-write from
    * another cluster node against the same store; the lock name matches the store id computed by
    * {@link #getDashboardStorage(String)} so that it is scoped per organization, not globally.
    */
   private String getDashboardsLockName() {
      String orgID = OrganizationManager.getInstance().getCurrentOrgID();
      return "inetsoft.sree.web.dashboard.DashboardManager.dashboards." + orgID.toLowerCase();
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
      List<String> list = new ArrayList<>();
      DashboardRegistry uregistry = null;
      DashboardRegistry gregistry = dashboardRegistryManager.getRegistry();

      if(identity.getType() == Identity.USER) {
         uregistry = dashboardRegistryManager.getRegistry(identity.getIdentityID());
      }

      // Filter out names this identity's cached registries don't currently recognize, but do not
      // persist the filtered (shortened) list (Bug #77299): a registry's local cache is only
      // eventually consistent with a remote rename/delete (async reload), so a read landing here
      // mid-reload must not permanently discard a name that is still valid, just not yet visible
      // to this node's cache. A genuine, confirmed removal is already persisted by the mutation
      // path that knows it is deleting (DashboardRegistry.removeDashboard()/renameDashboard(),
      // which call this manager's removeDashboard()/renameDashboard() under runLocked() for every
      // identity), so there is no need to duplicate that persistence here.
      for(String dashboard : values) {
         if((uregistry != null && uregistry.getDashboard(dashboard) != null) ||
            identity.getType() != Identity.USER || gregistry.getDashboard(dashboard) != null)
         {
            list.add(dashboard);
         }
      }

      return list.toArray(new String[0]);
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

      DashboardData data = getDashboardStorage().get(getIdentityKey(identity));
      List<String> values = data == null ? null : data.getDeselected();

      List<String> list = new ArrayList<>();
      DashboardRegistry registry = dashboardRegistryManager.getRegistry();
      boolean changed = false;

      // Filter out names the registry's local cache doesn't currently recognize, but (Bug #77299,
      // same reasoning as getDashboards(Identity, boolean)) do not treat that as a confirmed,
      // permanent removal to persist: the cache may simply be mid-reload of a concurrent remote
      // rename/delete. A genuine removal is already persisted by the mutation path that knows it
      // is deleting.
      if(values != null) {
         for(String dashboard : values) {
            if(registry.getDashboard(dashboard) != null) {
               list.add(dashboard);
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
                  changed = true;
               }
            }
         }
      }

      String[] dashboards = list.toArray(new String[0]);

      if(changed) {
         setDeselectedDashboards(identity, dashboards);
      }

      return dashboards;
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

      String lockName = getDashboardsLockName();
      Cluster.getInstance().lockKey(lockName);

      try {
         SortedMap<String, DashboardData> changes = new TreeMap<>();
         KeyValueStorage<DashboardData> dashboardStorage = getDashboardStorage();
         dashboardStorage.stream()
               .forEach(p -> renameDashboard(oname, name, p, changes));

         try {
            dashboardStorage.putAll(changes).get(60L, TimeUnit.SECONDS);
         }
         catch(InterruptedException | TimeoutException | ExecutionException e) {
            throw new RuntimeException(e);
         }
      }
      finally {
         Cluster.getInstance().unlockKey(lockName);
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

      String lockName = getDashboardsLockName();
      Cluster.getInstance().lockKey(lockName);

      try {
         SortedMap<String, DashboardData> changes = new TreeMap<>();
         KeyValueStorage<DashboardData> dashboardStorage = getDashboardStorage();
         dashboardStorage.stream()
            .forEach(p -> removeDashboard(name, p, changes));

         try {
            dashboardStorage.putAll(changes).get(60L, TimeUnit.SECONDS);
         }
         catch(InterruptedException | TimeoutException | ExecutionException e) {
            throw new RuntimeException(e);
         }
      }
      finally {
         Cluster.getInstance().unlockKey(lockName);
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
   // they hold this manager's monitor for the whole read-modify-write (77232), AND the cluster-wide
   // lock returned by getDashboardsLockName() (77299), since the underlying KeyValueStorage is
   // replicated/cluster-wide and the manager's monitor only excludes other threads in this JVM, not
   // another cluster node's independent DashboardManager instance racing the same store. Lock order
   // is this manager, then the cluster lock, then DashboardRegistryManager.lock, then the user
   // registry, then the global registry. The setters take only this manager's monitor and the
   // cluster lock, no registry locks, so they must not be called while holding
   // DashboardRegistryManager.lock or a registry monitor; a registry calls them only inside
   // runLocked(), before it locks itself. The cluster lock is reentrant (Cluster.lockKey()), so a
   // caller that already holds it (e.g. runLocked() or addDashboard()) can call into one of these
   // setters without deadlocking itself.
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
      String lockName = getDashboardsLockName();
      Cluster.getInstance().lockKey(lockName);

      try {
         KeyValueStorage<DashboardData> dashboardStorage = getDashboardStorage();

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
         Cluster.getInstance().unlockKey(lockName);
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
      String lockName = getDashboardsLockName();
      Cluster.getInstance().lockKey(lockName);

      try {
         KeyValueStorage<DashboardData> dashboardStorage = getDashboardStorage();

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
         Cluster.getInstance().unlockKey(lockName);
      }
   }

   /**
    * Add a dashboard to specified identity.
    */
   public synchronized void addDashboard(Identity identity, String dashboard) {
      init();

      // hold the cluster lock across the read and the write below (not just setDashboards()'s
      // own internal get+put), since narr is computed from this read and would silently discard
      // a concurrent write to the same identity's record landing in between (Bug #77299); the
      // lock is reentrant so setDashboards() re-acquiring it is harmless.
      String lockName = getDashboardsLockName();
      Cluster.getInstance().lockKey(lockName);

      try {
         String[] dashboards = getDashboards(identity);
         dashboards = dashboards == null ? new String[0] : dashboards;

         for(String dashboardName : dashboards) {
            if(dashboardName.equals(dashboard)) {
               return;
            }
         }

         String[] narr = new String[dashboards.length + 1];
         System.arraycopy(dashboards, 0, narr, 0, dashboards.length);
         narr[narr.length - 1] = dashboard;

         setDashboards(identity, narr);
      }
      finally {
         Cluster.getInstance().unlockKey(lockName);
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

   // syncUserDashboards() (bulk) and syncUserDashboards(Identity) are the same read-modify-write
   // shape as the setters above -- read the current DashboardData, compute a replacement, write it
   // back -- against the identical cluster-wide-replicated KeyValueStorage, so they need the same
   // cluster-wide lock across their whole read-modify-write span (Bug #77299 round 2). Both are
   // reached from init(), which every locked setter above calls *before* acquiring the cluster
   // lock, so no reentrant acquisition happens there. But syncUserDashboards(Identity) is also
   // reached from getDashboards(Identity, boolean), which is in turn called (with the lock already
   // held) from addDashboard() and from DashboardRegistry/UserDashboardRegistry actions run inside
   // runLocked() -- those nested acquisitions are safe because Cluster.lockKey() is reentrant, same
   // as the other setters' documented reentrancy.
   private synchronized void syncUserDashboards() throws Exception {
      String lockName = getDashboardsLockName();
      Cluster.getInstance().lockKey(lockName);

      try {
         KeyValueStorage<DashboardData> dashboardStorage = getDashboardStorage();
         SortedMap<String, DashboardData> changes = new TreeMap<>();
         dashboardStorage.stream().forEach(p -> syncUserDashboards(p, changes));

         if(!changes.isEmpty()) {
            dashboardStorage.putAll(changes).get(3L, TimeUnit.MINUTES);
         }
      }
      finally {
         Cluster.getInstance().unlockKey(lockName);
      }
   }

   private void syncUserDashboards(KeyValuePair<DashboardData> pair, Map<String, DashboardData> map) {
      Identity identity = getIdentity(pair.getKey());

      if(identity.getType() == Identity.USER) {
         try {
            List<String> selected = syncUserDashboards(identity, pair.getValue().getDashboards());

            if(selected != null) {
               DashboardData changed = new DashboardData();
               changed.setDashboards(selected);
               changed.setDeselected(pair.getValue().getDeselected());
               changed.setUserChanged(pair.getValue().isUserChanged());
               map.put(pair.getKey(), changed);
            }
         }
         catch(Exception e) {
            throw new RuntimeException("Failed to synchronize user dashboards", e);
         }
      }
   }

   private synchronized void syncUserDashboards(Identity user) throws Exception {
      String lockName = getDashboardsLockName();
      Cluster.getInstance().lockKey(lockName);

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

         data.setDashboards(nselected);

         if(!Tool.equals(selected, nselected)) {
            dashboardStorage.put(getIdentityKey(user), data).get(10L, TimeUnit.SECONDS);
         }
      }
      finally {
         Cluster.getInstance().unlockKey(lockName);
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
   private static final Logger LOG = LoggerFactory.getLogger(DashboardManager.class);

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
