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

   void renameDashboard(String oname, String name) {
      lock.lock();

      try {
         for(DashboardRegistry registry : registries.values()) {
            if(!registry.isGlobal() && registry.getDashboard(oname) != null) {
               registry.renameDashboard(oname, name);
            }
         }
      }
      finally {
         lock.unlock();
      }
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
               registry.loadDashboard(getRegistry());
            }

            registries.put(key, registry);
         }
      }
      finally {
         lock.unlock();
      }

      return registry;
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
                     dataSpace.delete(null, oldPath);
                  }
                  else {
                     registry.modifyOrgId(nOID);
                  }

                  registry.save();
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

   private static final Logger LOG = LoggerFactory.getLogger(DashboardRegistryManager.class);
}
