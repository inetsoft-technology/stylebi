/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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

import inetsoft.sree.ViewsheetEntry;
import inetsoft.sree.security.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.DependencyHandler;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.InputStream;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class DashboardRegistryManager {
   public DashboardRegistryManager(ApplicationEventPublisher eventPublisher,
                                   SecurityEngine securityEngine,
                                   DependencyHandler dependencyHandler,
                                   DataSpace dataSpace)
   {
      this.eventPublisher = eventPublisher;
      this.securityEngine = securityEngine;
      this.dependencyHandler = dependencyHandler;
      this.dataSpace = dataSpace;
   }

   public static DashboardRegistryManager getInstance() {
      return ConfigurationContext.getContext().getSpringBean(DashboardRegistryManager.class);
   }

   /**
    * Return default registry.
    */
   public DashboardRegistry getRegistry() {
      return getRegistry((IdentityID) null);
   }

   @EventListener(SessionLoggedOutEvent.class)
   public void loggedOut(SessionLoggedOutEvent event) {
      Principal principal = event.getPrincipal();

      if(principal instanceof SRPrincipal srp) {
         String orgId = srp.getOrgId();
         IdentityID id = srp.getIdentityID();
         clear(id.getName(), orgId);
      }
   }

   /**
    * Propagates the rename of a global dashboard to the copies of it in the user registries of
    * the global registry's organization, and of no other organization.
    *
    * @param orgID  the organization of the renamed global dashboard.
    * @param oname  the old name.
    * @param name   the new name.
    * @param stored the users found by {@link #loadUserCopies(String, String)}, whose registries
    *               may not be cached.
    */
   void renameDashboard(String orgID, String oname, String name,
                        Collection<IdentityID> stored)
   {
      if(orgID == null) {
         return;
      }

      Set<DashboardRegistry> renamed = new LinkedHashSet<>();
      lock.lock();

      try {
         for(DashboardRegistry registry : registries.values()) {
            if(isUserCopy(registry, orgID, oname)) {
               renamed.add(registry);
            }
         }

         // a registry found in storage may have been evicted since, e.g. by a logout
         for(IdentityID user : stored) {
            DashboardRegistry registry = getRegistry(user, orgID, true);

            if(isUserCopy(registry, orgID, oname)) {
               renamed.add(registry);
            }
         }
      }
      finally {
         lock.unlock();
      }

      // rename after unlocking, the dashboard manager is locked before this manager. The
      // selections of the whole organization are already renamed by the global rename, so only
      // the registry entry is renamed here.
      DashboardManager.getManager().runLocked(() -> {
         for(DashboardRegistry registry : renamed) {
            try {
               if(registry.renameEntry(oname, name)) {
                  registry.fireChangeEvent(registry, DashboardChangeEvent.Type.RENAMED,
                                           oname, name);
               }
            }
            catch(Exception ex) {
               LOG.error(ex.getMessage(), ex);
            }
         }
      });
   }

   /**
    * Finds the stored user registries of an organization that have a copy of a global
    * dashboard, and loads the ones that are not cached, so that a global rename also reaches
    * the users who are not logged in. The files are listed from the data space, not from the
    * security provider, so users unknown to the provider and the anonymous user are included.
    * A registry without the dashboard is neither loaded nor cached.
    * <p>
    * It must be called before the global dashboard is renamed, so that an old user file is
    * ported against the old name. It must be called without holding the dashboard manager or
    * a registry lock, so that the scan doesn't block the dashboard manager.
    *
    * @param orgID the organization of the global dashboard.
    * @param oname the name of the global dashboard.
    *
    * @return the users whose registry has the dashboard, including the users whose registry is
    *         already cached, so that the rename re-loads a registry evicted in the meantime.
    */
   Collection<IdentityID> loadUserCopies(String orgID, String oname) {
      if(orgID == null || oname == null) {
         return Collections.emptyList();
      }

      List<IdentityID> users = new ArrayList<>();
      String prefix = "portal/" + orgID + "/";
      String suffix = "/" + USER_REGISTRY_FILE;

      for(String path : dataSpace.getOrgScopedPaths(new Organization(orgID))) {
         if(!path.startsWith(prefix) || !path.endsWith(suffix) ||
            path.length() <= prefix.length() + suffix.length())
         {
            continue;
         }

         String userName = path.substring(prefix.length(), path.length() - suffix.length());

         if(userName.indexOf('/') >= 0) {
            // not a user registry
            continue;
         }

         IdentityID user = new IdentityID(userName, orgID);
         DashboardRegistry cached = registries.get(getRegistryKey(userName, orgID));

         if(cached != null) {
            // not loaded again, but still returned, the registry may be evicted, e.g. by a
            // logout, before the rename
            if(isUserCopy(cached, orgID, oname)) {
               users.add(user);
            }
         }
         else if(fileHasCopy(path, oname)) {
            getRegistry(user, orgID, true);
            users.add(user);
         }
      }

      return users;
   }

   /**
    * Checks if a registry is a user registry of an organization with a dashboard.
    */
   private static boolean isUserCopy(DashboardRegistry registry, String orgID, String oname) {
      return registry != null && !registry.isGlobal() &&
         Tool.equals(registry.getOrgID(), orgID) && registry.getDashboard(oname) != null;
   }

   /**
    * Checks if a stored user registry file has a dashboard, read only. A node of an old file
    * that is ported to the global dashboard name when the file is loaded counts too.
    */
   private boolean fileHasCopy(String path, String oname) {
      try(InputStream in = dataSpace.getInputStream(null, path)) {
         if(in == null) {
            return false;
         }

         Element root = Tool.parseXML(in).getDocumentElement();
         String version = Tool.getValue(Tool.getChildNodeByTagName(root, "Version"));
         boolean needsPort = !FileVersions.DASHBOARD_REGISTRY.equals(version);
         NodeList nodes = Tool.getChildNodesByTagName(root, "node");

         for(int i = 0; i < nodes.getLength(); i++) {
            Element node = (Element) nodes.item(i);
            Element dashboard = Tool.getChildNodeByTagName(node, "dashboard");

            // ignored when the file is loaded
            if(dashboard != null && "inetsoft.sree.web.dashboard.PortletDashboard".equals(
               Tool.getAttribute(dashboard, "class")))
            {
               continue;
            }

            String name = Tool.getCDATAData(Tool.getChildNodeByTagName(node, "name"));

            if(oname.equals(name) || needsPort && oname.equals(name + "__GLOBAL")) {
               return true;
            }
         }
      }
      catch(Exception ex) {
         LOG.warn("Failed to read dashboard registry {}", path, ex);
      }

      return false;
   }

   /**
    * Return a user dashboard registry or default registry.
    */
   public DashboardRegistry getRegistry(IdentityID userName) {
      return getRegistry(userName, true);
   }

   public DashboardRegistry getRegistry(String orgID) {
      return getRegistry(null, orgID, true);
   }

   private DashboardRegistry getRegistry(IdentityID userId, boolean create) {
      String orgID = userId == null || userId.orgID == null ?
         OrganizationManager.getInstance().getCurrentOrgID() : userId.orgID;

      return getRegistry(userId, orgID, create);
   }

   private DashboardRegistry getRegistry(IdentityID userId, String orgID, boolean create) {
      String key = getRegistryKey(userId == null ? null : userId.getName(), orgID);
      DashboardRegistry registry;
      lock.lock();

      try {
         registry = registries.get(key);

         if(registry == null && create) {
            if(key.endsWith("__ADMIN__")) {
               registry = new DashboardRegistry(orgID, eventPublisher, securityEngine);
               registry.loadDashboard(null);
            }
            else {
               registry = new DashboardRegistry.UserDashboardRegistry(userId, orgID, eventPublisher, securityEngine);
               // port against the user's own org, not the current org of the calling thread
               registry.loadDashboard(getGlobalForPort(orgID));
            }

            registries.put(key, registry);
         }
      }
      finally {
         lock.unlock();
      }

      return registry;
   }

   /**
    * Get the global registry of an organization, used to port an old user registry file. Unlike
    * getRegistry(String), it does not fall back to the current org, and it does not create a
    * registry for an organization unknown to the security provider (that registry would be
    * cached with a portal/null path). It must not be called while holding a registry lock, since
    * creating the registry locks this manager.
    *
    * @return the global registry, or null if the organization is not known.
    */
   DashboardRegistry getGlobalForPort(String orgID) {
      if(orgID == null) {
         return null;
      }

      DashboardRegistry registry = registries.get(getRegistryKey(null, orgID));

      if(registry == null && isKnownOrg(orgID)) {
         registry = getRegistry(null, orgID, true);
      }

      return registry;
   }

   /**
    * Checks if the security provider knows an organization, by id or by name, the same lookup
    * the DashboardRegistry constructor does.
    */
   private static boolean isKnownOrg(String orgID) {
      SecurityProvider provider = SecurityEngine.getSecurity().getSecurityProvider();
      return provider.getOrgNameFromID(orgID) != null || provider.getOrganization(orgID) != null;
   }

   public void copyRegistry(IdentityID identityID, Organization oorg, Organization norg) {
      String name = identityID == null ? null : identityID.getName();
      String nKey = getRegistryKey(name, norg.getOrganizationID());
      DashboardRegistry registry = getRegistry(identityID, oorg.getId(), true);

      if(registry == null || !registry.pathExist()) {
         return;
      }

      lock.lock();

      try {
         DashboardRegistry nregistry;

         if(nKey.endsWith("__ADMIN__")) {
            nregistry = new DashboardRegistry(norg.getId(), eventPublisher, securityEngine);
         }
         else {
            nregistry = new DashboardRegistry.UserDashboardRegistry(new IdentityID(name, norg.getId()), eventPublisher, securityEngine);
         }

         String[] dashboardNames = registry.getDashboardNames();

         for(String dashboardName : dashboardNames) {
            Dashboard dashboard = registry.getDashboard(dashboardName);

            if(dashboard != null) {
               VSDashboard vsDashboard = (VSDashboard) dashboard;
               nregistry.addDashboard(dashboardName, vsDashboard.cloneVSDashboard(norg));
            }
         }

         registries.put(nKey, nregistry);

         try {
            nregistry.save();
         }
         catch(Exception ex) {
            LOG.error(ex.getMessage(), ex);
         }
      }
      finally {
         lock.unlock();
      }
   }

   public void migrateRegistry(IdentityID identityID, Organization oorg, Organization norg) {
      if(identityID != null && !Tool.equals(identityID.getOrgID(), oorg.getId())) {
         return;
      }

      String userName = identityID == null ? null : identityID.getName();
      String nOID = norg == null ? null : norg.getId();
      String oOID = oorg.getId();
      String nKey = nOID == null ? null : getRegistryKey(userName, nOID);
      DashboardRegistry registry = getRegistry(identityID, oOID, true);

      lock.lock();

      try {
         if(registry != null) {
            // hold the registry lock from the migration to the save, so a concurrent reload
            // can't replace the migrated dashboards with the ones in the old file
            synchronized(registry) {
               String[] dashboardNames = registry.getDashboardNames();
               boolean changeId = !Tool.equals(oOID, nOID);
               String oldPath = registry.getPath();

               if(norg != null) {
                  Arrays.stream(dashboardNames).forEach(name -> {
                     Dashboard dashboard = registry.getDashboard(name);

                     if(dashboard != null) {
                        VSDashboard vsDashboard = (VSDashboard) dashboard;
                        migrateVSDashboard(vsDashboard, oorg, norg);
                     }
                  });

                  registries.put(nKey, registry);
               }

               if(changeId) {
                  clear(userName, oOID);

                  try {
                     if(nKey == null) {
                        // no new org, the registry is removed, don't write its file back
                        dataSpace.delete(null, oldPath);
                     }
                     else {
                        registry.modifyOrgId(nOID);
                        registry.save();
                     }
                  }
                  catch(Exception ex) {
                     LOG.error(ex.getMessage(), ex);
                  }
               }
               else if(identityID != null && (norg == null || !Tool.equals(identityID.getOrgID(), norg.getId()))) {
                  try {
                     registry.save();
                  }
                  catch(Exception ex) {
                     LOG.error(ex.getMessage(), ex);
                  }
               }
            }
         }
      }
      finally {
         lock.unlock();
      }
   }

   private void migrateVSDashboard(VSDashboard vsDashboard,
                                   Organization oorg, Organization norg)
   {
      boolean changeId = !Tool.equals(oorg.getId(), norg.getId());
      ViewsheetEntry viewsheet = vsDashboard.getViewsheet();

      if(viewsheet != null) {
         String identifier = viewsheet.getIdentifier();
         AssetEntry assetEntry = AssetEntry.createAssetEntry(identifier);
         assetEntry = changeId ? assetEntry.cloneAssetEntry(norg) : assetEntry;

         if(changeId) {
            IdentityID owner = viewsheet.getOwner();

            if(owner != null) {
               owner.setOrgID(norg.getId());
               viewsheet.setOwner(owner);
            }

            IdentityID user = assetEntry.getUser();

            if(user != null) {
               user.setOrgID(norg.getId());
            }
         }

         viewsheet.setIdentifier(assetEntry.toIdentifier());
      }
   }

   /**
    * Rename a user.
    * @param oname the old name of the user.
    * @param nname the new name of the user.
    */
   public void renameUser(IdentityID oname, IdentityID nname) {
      DashboardRegistry registry = null;

      if(oname != null && nname != null && !oname.equals(nname)) {
         // rename
         updateRegistry(getRegistry(), oname, nname, true);
         updateRegistry(getRegistry(nname), oname, nname, false);
         return;
      }
      else if(oname == null && nname != null) {
         // delete
         registry = getRegistry(nname, false);

         if(registry != null) {
            clear(nname);

            synchronized(registry) {
               registry.dashboardsMap.clear();
            }

            try {
               registry.save();
            }
            catch(Exception ex) {
               LOG.error(ex.getMessage(), ex);
            }
         }
      }

      if(registry != null) {
         try {
            registry.save();
         }
         catch(Exception ex) {
            LOG.error(ex.getMessage(), ex);
         }

         for(String name : registry.getDashboardNames()) {
            fireChangeEvent(DashboardChangeEvent.Type.REMOVED, name, null, oname);
            fireChangeEvent(DashboardChangeEvent.Type.CREATED, null, name, nname);
         }
      }
   }

   private void updateRegistry(DashboardRegistry registry, IdentityID oname, IdentityID nname, boolean global) {
      if(oname == null || nname == null || Tool.equals(oname, nname) || registry == null) {
         return;
      }

      Map<String, Dashboard> dmap2 = registry.getDashboardsMapSnapshot();

      for(Dashboard d : dmap2.values()) {
         if(d instanceof VSDashboard) {
            ViewsheetEntry vs = ((VSDashboard) d).getViewsheet();

            if(vs != null && vs.getOwner() != null && vs.getOwner().equals(oname)) {
               vs.setOwner(nname);
               AssetEntry assetEntry = AssetEntry.createAssetEntry(vs.getIdentifier());
               AssetEntry newEntry = new AssetEntry(assetEntry.getScope(), assetEntry.getType(), assetEntry.getPath(), nname);

               vs.setIdentifier(newEntry.toIdentifier());
            }
         }
      }

      registry = global ? registry : getRegistry(nname);
      clear(global ? null : oname);
      registry.setDashboardsMap(dmap2);

      try {
         registry.save();
      }
      catch(Exception ex) {
         LOG.error(ex.getMessage(), ex);
      }

      for(String name : registry.getDashboardNames()) {
         fireChangeEvent(DashboardChangeEvent.Type.REMOVED, name, null, oname);
         fireChangeEvent(DashboardChangeEvent.Type.CREATED, null, name, nname);
         dependencyHandler.updateDashboardDependencies(global ? null : nname, name, true);
      }
   }

   /**
    * Clear the cached dashboard registry.
    * The next call to getRegistry will rebuild the registry.
    */
   public void clear(IdentityID userID) {
      String orgID = userID == null ? null : userID.orgID;
      String name = userID == null ? null : userID.getName();
      clear(name, orgID);
   }

   /**
    * Clear the cached dashboard registry.
    * The next call to getRegistry will rebuild the registry.
    */
   private void clear(String user, String organizationId) {
      String key = getRegistryKey(user, organizationId);
      lock.lock();

      try {
         DashboardRegistry registry = registries.remove(key);

         if(registry != null) {
            registry.clear();
         }
      }
      finally {
         lock.unlock();
      }
   }

   /**
    * Clear every cached dashboard registry of an organization: its global registry and the
    * registries of all of its users, including users the editable provider does not list (SSO,
    * virtual or anonymous users, or users of other providers).
    *
    * <p>The org is matched on the registry's own org id, ignoring case, since the current-org
    * path lowercases the id. It is not parsed from the key: the key is org__user and both parts
    * may contain "__". A global registry whose org id did not resolve (null) is matched on its
    * key, orgId__ADMIN__, ignoring case.
    */
   public void clearOrganization(String orgId) {
      if(orgId == null) {
         return;
      }

      String globalKey = getRegistryKey(null, orgId);
      lock.lock();

      try {
         Iterator<Map.Entry<String, DashboardRegistry>> it = registries.entrySet().iterator();

         while(it.hasNext()) {
            Map.Entry<String, DashboardRegistry> entry = it.next();
            DashboardRegistry registry = entry.getValue();
            String registryOrgId = registry.getOrgID();
            boolean match = registryOrgId != null ? orgId.equalsIgnoreCase(registryOrgId) :
               globalKey.equalsIgnoreCase(entry.getKey());

            if(match) {
               it.remove();
               registry.clear();
            }
         }
      }
      finally {
         lock.unlock();
      }
   }

   private void fireChangeEvent(DashboardChangeEvent.Type type, String oldName,
                                String newName, IdentityID user)
   {
      DashboardChangeEvent event = new DashboardChangeEvent(this, type, oldName, newName, user);
      eventPublisher.publishEvent(event);
   }

   private String getRegistryKey(String user, String organizationId) {
      user = user == null ? "ADMIN__" : user;
      organizationId = organizationId == null ?
         OrganizationManager.getInstance().getCurrentOrgID() : organizationId;

      return organizationId + "__" + user;
   }

   private final ApplicationEventPublisher eventPublisher;
   private final SecurityEngine securityEngine;
   private final DependencyHandler dependencyHandler;
   private final DataSpace dataSpace;
   private final Lock lock = new ReentrantLock();
   private final Map<String, DashboardRegistry> registries = new ConcurrentHashMap<>();

   private static final String USER_REGISTRY_FILE = "dashboard-registry.xml";

   private static final Logger LOG = LoggerFactory.getLogger(DashboardRegistryManager.class);
}
