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
package inetsoft.sree.web.dashboard;

import inetsoft.sree.ClientInfo;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.storage.*;
import inetsoft.uql.util.*;
import inetsoft.util.SingletonManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

/**
 * A dashboard manager is a manager of dashboard. It is used to read &
 * write information of dashboard.
 *
 * @version 8.5, 07/12/2006
 * @author InetSoft Technology Corp
 */
@SuppressWarnings("WeakerAccess")
public class DashboardManager implements AutoCloseable {
   /**
    * Construct.
    */
   public DashboardManager() {
   }

   @Override
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
    * Return a dashboard manager.
    */
   public static DashboardManager getManager() {
      return SingletonManager.getInstance(DashboardManager.class);
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
      return getDashboards(identity, true);
   }

   public synchronized String[] getDashboards(Identity identity, boolean sync) {
      init();

      if(identity.getType() == Identity.USER && sync) {
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
      DashboardRegistry gregistry = DashboardRegistry.getRegistry();

      if(identity.getType() == Identity.USER) {
         uregistry = DashboardRegistry.getRegistry(identity.getIdentityID());
      }

      boolean changed = false;

      for(String dashboard : values) {
         if((uregistry != null && uregistry.getDashboard(dashboard) != null) ||
            identity.getType() != Identity.USER || gregistry.getDashboard(dashboard) != null)
         {
            list.add(dashboard);
         }
         else {
            changed = true;
         }
      }

      String[] dashboards = list.toArray(new String[0]);

      if(changed) {
         setDashboards(identity, dashboards);
      }

      return dashboards;
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
      DashboardRegistry registry = DashboardRegistry.getRegistry();
      boolean changed = false;

      if(values != null) {
         for(String dashboard : values) {
            if(registry.getDashboard(dashboard) != null) {
               list.add(dashboard);
            }
            else {
               changed = true;
            }
         }
      }

      if(identity.getType() == Identity.USER) {
         IdentityID userId = identity.getIdentityID();
         if(OrganizationManager.getInstance().isSiteAdmin(userId)) {
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

      if(oiden != null && niden != null && !oiden.equals(niden)) {
         String okey = getIdentityKey(oiden);
         String nkey = getIdentityKey(niden);
         getDashboardStorage().rename(okey, nkey);
      }
      else if(oiden == null && niden != null) {
         // delete
         getDashboardStorage().remove(getIdentityKey(niden));
      }
   }

   /**
    * Rename a dashboard.
    * @param oname the old name.
    * @param name the new name.
    */
   public synchronized void renameDashboard(String oname, String name) {
      init();

      SortedMap<String, DashboardData> changes = new TreeMap<>();
      KeyValueStorage<DashboardData> dashboardStorage = getDashboardStorage();
      dashboardStorage.stream()
            .forEach(p -> renameDashboard(oname, name, p, changes));
      dashboardStorage.putAll(changes);
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

      SortedMap<String, DashboardData> changes = new TreeMap<>();
      KeyValueStorage<DashboardData> dashboardStorage = getDashboardStorage();
      dashboardStorage.stream()
         .forEach(p -> removeDashboard(name, p, changes));
      dashboardStorage.putAll(changes);
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
      SecurityProvider provider = SecurityEngine.getSecurity().getSecurityProvider();

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
                                               userIdentity.getOrganization(), 0L);

         if(groups != null) {
            for(String groupName : groups) {
               if(!visitedGroups.contains(groupName)) {
                  Group group = provider.getGroup(new IdentityID(groupName, userIdentity.getOrganization()));

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
      SecurityProvider provider = SecurityEngine.getSecurity().getSecurityProvider();
      User user;

      // treat user anonymous as a special role
      if(provider.isVirtual() || ClientInfo.ANONYMOUS.equals(userName.name)) {
         return getDashboards(new DefaultIdentity(userName, Identity.ROLE));
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
                  provider.getGroup(new IdentityID(groupName, user.getOrganization())), provider, list, visitedGroups, visitedRoles, principal);
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
         if(!list.contains(dashboard) && SecurityEngine.getSecurity().checkPermission(
            principal, ResourceType.DASHBOARD, dashboard, ResourceAction.ACCESS))
         {
            list.add(dashboard);
         }
      }

      for(String groupName : group.getGroups()) {
         if(!visitedGroups.contains(groupName)) {
            getGroupDashboards(
               provider.getGroup(new IdentityID(groupName, group.getOrganization())), provider, list, visitedGroups,
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
         if(!list.contains(dashboard) && SecurityEngine.getSecurity().checkPermission(
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

   // don't synchronized since setDashboards() is called in parseXML() which has the stream
   // locked in dataspace and may cause deadlock. (49246)
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
   public void setDashboards(Identity identity, String[] dashboards, Boolean userChanged) {
      init();
      KeyValueStorage<DashboardData> dashboardStorage = getDashboardStorage();

      if(dashboards == null) {
         String key = getIdentityKey(identity);
         DashboardData data = dashboardStorage.get(key);

         if(data != null) {
            data.setDashboards(new ArrayList<>());

            try {
               dashboardStorage.put(key, data).get();
            }
            catch(InterruptedException | ExecutionException e) {
               throw new RuntimeException("Failed to save dashboard", e);
            }
         }
      }
      else if(identity.getName() != null) {
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
            dashboardStorage.put(key, data).get();
         }
         catch(InterruptedException | ExecutionException e) {
            throw new RuntimeException("Failed to save dashboard", e);
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
   public void setDeselectedDashboards(Identity identity, String[] dashboards) {
      init();
      KeyValueStorage<DashboardData> dashboardStorage = getDashboardStorage();

      if(dashboards == null) {
         String key = getIdentityKey(identity);
         DashboardData data = dashboardStorage.get(key);

         if(data != null) {
            data.setDeselected(new ArrayList<>());

            try {
               dashboardStorage.put(key, data).get();
            }
            catch(InterruptedException | ExecutionException e) {
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
            dashboardStorage.put(key, data).get();
         }
         catch(InterruptedException | ExecutionException e) {
            throw new RuntimeException("Failed to deselected dashboard", e);
         }
      }
   }

   /**
    * Add a dashboard to specified identity.
    */
   public synchronized void addDashboard(Identity identity, String dashboard) {
      init();

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

   public void addDashboardChangeListener(DashboardChangeListener l) {
      synchronized(changeListeners) {
         changeListeners.add(l);
      }
   }

   public void removeDashboardChangeListener(DashboardChangeListener l) {
      synchronized(changeListeners) {
         changeListeners.remove(l);
      }
   }

   public void removeDashboardStorage(String orgID) throws Exception {
      getDashboardStorage(orgID).deleteStore();
      getDashboardStorage(orgID).close();
   }

   public void migrateStorageData(String oId, String id) throws Exception {
      KeyValueStorage<DashboardData> oStorage = getDashboardStorage(oId);
      KeyValueStorage<DashboardData> nStorage = getDashboardStorage(id);
      SortedMap<String, DashboardData> data = new TreeMap<>();
      oStorage.stream().forEach(pair -> data.put(pair.getKey(), pair.getValue()));
      nStorage.putAll(data);
      removeDashboardStorage(oId);
   }

   public void copyStorageData(String oId, String id) {
      KeyValueStorage<DashboardData> oStorage = getDashboardStorage(oId);
      KeyValueStorage<DashboardData> nStorage = getDashboardStorage(id);
      SortedMap<String, DashboardData> data = new TreeMap<>();
      oStorage.stream().forEach(pair -> data.put(pair.getKey(), pair.getValue()));
      nStorage.putAll(data);
   }

   void fireDashboardChanged(DashboardChangeEvent.Type type, String oldName, String newName,
                             IdentityID user)
   {
      DashboardChangeEvent event = new DashboardChangeEvent(this, type, oldName, newName, user);

      synchronized(changeListeners) {
         for(DashboardChangeListener l : changeListeners) {
            l.dashboardChanged(event);
         }
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
      Supplier<LoadDashboardsTask> supplier = () -> new LoadDashboardsTask(storeID);
      return SingletonManager.getInstance(KeyValueStorage.class, storeID, supplier);
   }

   private synchronized void syncUserDashboards() throws Exception {
      KeyValueStorage<DashboardData> dashboardStorage = getDashboardStorage();
      SortedMap<String, DashboardData> changes = new TreeMap<>();
      dashboardStorage.stream().forEach(p -> syncUserDashboards(p, changes));
      dashboardStorage.putAll(changes);
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
      dashboardStorage.put(getIdentityKey(user), data).get();
   }

   private List<String> syncUserDashboards(Identity user, List<String> selected) throws Exception {
      DashboardRegistry reg = DashboardRegistry.getRegistry(user.getIdentityID());
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
      return new DefaultIdentity(name, type);
   }

   private String getIdentityKey(Identity identity) {
      return identity.getType() + ":" + identity.getName();
   }

   private String orgID = null;
   private final Set<DashboardChangeListener> changeListeners = new HashSet<>();
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
         SecurityProvider security = SecurityEngine.getSecurity().getSecurityProvider();

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
