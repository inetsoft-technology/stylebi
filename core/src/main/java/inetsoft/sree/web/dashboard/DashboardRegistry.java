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

import inetsoft.sree.SreeEnv;
import inetsoft.sree.ViewsheetEntry;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.DependencyHandler;
import inetsoft.uql.util.*;
import inetsoft.util.*;
import inetsoft.util.log.LogManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Provides the interface for adding and removing dashboards.
 *
 * @author InetSoft Technology
 * @since  8.5
 */
public class DashboardRegistry implements SessionListener {
   /**
    * Construct.
    */
   private DashboardRegistry() {
      AuthenticationService.getInstance().addSessionListener(this);
   }

   private DashboardRegistry(String orgId) {
      AuthenticationService.getInstance().addSessionListener(this);

      if(orgId == null) {
         orgId = OrganizationManager.getInstance().getCurrentOrgID();
      }

      SecurityProvider provider = SecurityEngine.getSecurity().getSecurityProvider();

      // orgId maybe org name.
      if(provider.getOrgNameFromID(orgId) == null) {
         Organization organization = provider.getOrganization(orgId);
         orgId = organization == null ? null : organization.getOrganizationID();
      }

      organizationId = orgId;
   }

   /**
    * Return default registry.
    */
   public static synchronized DashboardRegistry getRegistry() {
      return getRegistry((IdentityID) null);
   }

   private static String getRegistryKey(String user, String organizationId) {
      user = user == null ? "ADMIN__" : user;
      organizationId = organizationId == null ?
         OrganizationManager.getInstance().getCurrentOrgID() : organizationId;

      return organizationId + "__" + user;
   }

   /**
    * Return a user dashboard registry or default registry.
    */
   public static DashboardRegistry getRegistry(IdentityID userName) {
      return getRegistry(userName, true);
   }

   public static DashboardRegistry getRegistry(String orgID) {
      return getRegistry(null, orgID, true);
   }

   private static DashboardRegistry getRegistry(IdentityID userId, boolean create) {
      String orgID = userId == null || userId.orgID == null ?
         OrganizationManager.getInstance().getCurrentOrgID() : userId.orgID;

      return getRegistry(userId, orgID, create);
   }

   private static DashboardRegistry getRegistry(IdentityID userId, String orgID, boolean create) {
      String key = getRegistryKey(userId == null ? null : userId.getName(), orgID);
      DashboardRegistry registry;
      REGISTRY_LOCK.lock();

      try {
         Map<String, DashboardRegistry> map = ConfigurationContext.getContext().get(REGISTRY_KEY);

         if(map == null) {
            ConfigurationContext.getContext().put(REGISTRY_KEY, map = new ConcurrentHashMap<>());
         }

         registry = map.get(key);

         if(registry == null && create) {
            if(key != null && key.endsWith("__ADMIN__")) {
               registry = new DashboardRegistry(orgID);
            }
            else {
               registry = new UserDashboardRegistry(userId, orgID);
            }

            registry.loadDashboard();
            map.put(key, registry);
         }
      }
      finally {
         REGISTRY_LOCK.unlock();
      }

      return registry;
   }

   public static void copyRegistry(IdentityID identityID, Organization oorg, Organization norg) {
      String name = identityID == null ? null : identityID.getName();
      String nKey = getRegistryKey(name, norg.getOrganizationID());
      DashboardRegistry registry = getRegistry(identityID, oorg.getId(), true);

      REGISTRY_LOCK.lock();

      try {
         Map<String, DashboardRegistry> map = ConfigurationContext.getContext().get(REGISTRY_KEY);

         if(map == null) {
            ConfigurationContext.getContext().put(REGISTRY_KEY, map = new ConcurrentHashMap<>());
         }

         DashboardRegistry nregistry;

         if(nKey != null && nKey.endsWith("__ADMIN__")) {
            nregistry = new DashboardRegistry(norg.getId());
         }
         else {
            nregistry = new UserDashboardRegistry(identityID);
         }

         String[] dashboardNames = registry.getDashboardNames();

         for(String dashboardName : dashboardNames) {
            Dashboard dashboard = registry.getDashboard(dashboardName);

            if(dashboard != null) {
               VSDashboard vsDashboard = (VSDashboard) dashboard;
               nregistry.addDashboard(dashboardName, vsDashboard.cloneVSDashboard(norg));
            }
         }

         if(nregistry != null) {
            map.put(nKey, nregistry);
         }
      }
      finally {
         REGISTRY_LOCK.unlock();
      }
   }

   public static void migrateRegistry(IdentityID identityID, Organization oorg, Organization norg) {
      if(identityID != null && !Tool.equals(identityID.getOrgID(), oorg.getId())) {
         return;
      }

      String userName = identityID == null ? null : identityID.getName();
      String nOID = norg == null ? null : norg.getId();
      String oOID = oorg.getId();
      String nKey = nOID == null ? null :getRegistryKey(userName, nOID);
      DashboardRegistry registry = getRegistry(identityID, oOID, true);

      REGISTRY_LOCK.lock();

      try {
         Map<String, DashboardRegistry> map = ConfigurationContext.getContext().get(REGISTRY_KEY);

         if(map == null) {
            ConfigurationContext.getContext().put(REGISTRY_KEY, map = new ConcurrentHashMap<>());
         }

         if(registry != null) {
            String[] dashboardNames = registry.getDashboardNames();
            DashboardRegistry dashboardRegistry = registry;
            boolean changeId = !Tool.equals(oOID, nOID);
            String oldPath = registry.getPath();

            if(norg != null) {
               Arrays.stream(dashboardNames).forEach(name -> {
                  Dashboard dashboard = dashboardRegistry.getDashboard(name);

                  if(dashboard != null) {
                     VSDashboard vsDashboard = (VSDashboard) dashboard;
                     migrateVSDashboard(vsDashboard, oorg, norg);
                  }
               });

               map.put(nKey, registry);
            }

            if(changeId) {
               clear(userName, oOID);

               try {
                  if(nKey == null) {
                     DataSpace space = DataSpace.getDataSpace();
                     space.delete(null, oldPath);
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
            else if(identityID != null && !Tool.equals(identityID.getOrgID(), norg.getId())) {
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
         REGISTRY_LOCK.unlock();
      }
   }

   private static void migrateVSDashboard(VSDashboard vsDashboard,
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

   private static void fireChangeEvent(DashboardRegistry source, DashboardChangeEvent.Type type,
                                       String oldName, String newName)
   {
      IdentityID user = null;

      if(source instanceof UserDashboardRegistry) {
         user = ((UserDashboardRegistry) source).user;
      }

      fireChangeEvent(type, oldName, newName, user);
   }

   private static void fireChangeEvent(DashboardChangeEvent.Type type, String oldName,
                                       String newName, IdentityID user)
   {
      DashboardManager.getManager().fireDashboardChanged(type, oldName, newName, user);
   }

   /**
    * Adds a dashboard.
    *
    * @param name      the dashboard name.
    * @param dashboard the dashboard to add.
    */
   public void addDashboard(String name, Dashboard dashboard) {
      dashboardsMap.put(name, dashboard);
      fireChangeEvent(this, DashboardChangeEvent.Type.CREATED, null, name);
   }

   /**
    * Rename a user.
    * @param oname the old name of the user.
    * @param nname the new name of the user.
    */
   public static void renameUser(IdentityID oname, IdentityID nname) {
      DashboardRegistry registry = null;

      if(oname != null && nname != null && !oname.equals(nname)) {
         // rename
         updateRegistry(DashboardRegistry.getRegistry(), oname, nname, true);
         updateRegistry(DashboardRegistry.getRegistry(nname), oname, nname, false);
         return;
      }
      else if(oname == null && nname != null) {
         // delete
         registry = getRegistry(nname, false);

         if(registry != null) {
            clear(nname);
            registry.dashboardsMap.clear();

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

         for(String name : registry.dashboardsMap.keySet()) {
            fireChangeEvent(DashboardChangeEvent.Type.REMOVED, name, null, oname);
            fireChangeEvent(DashboardChangeEvent.Type.CREATED, null, name, nname);
         }
      }
   }

   private static void updateRegistry(DashboardRegistry registry, IdentityID oname, IdentityID nname, boolean global) {
      if(oname == null || nname == null || Tool.equals(oname, nname) || registry == null) {
         return;
      }

      Map<String, Dashboard> dmap = registry.dashboardsMap;
      Map<String, Dashboard> dmap2 = new LinkedHashMap<>(dmap);

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

      registry = global ? registry : DashboardRegistry.getRegistry(nname);
      clear(global ? null : oname);
      registry.dashboardsMap = dmap2;

      try {
         registry.save();
      }
      catch(Exception ex) {
         LOG.error(ex.getMessage(), ex);
      }

      for(String name : registry.dashboardsMap.keySet()) {
         fireChangeEvent(DashboardChangeEvent.Type.REMOVED, name, null, oname);
         fireChangeEvent(DashboardChangeEvent.Type.CREATED, null, name, nname);
         DependencyHandler.getInstance().updateDashboardDependencies(global ? null : nname, name, true);
      }
   }

   /**
    * Get dashboard with the specified name.
    * @return a dashboard with the specified name.
    */
   public Dashboard getDashboard(String name) {
      return dashboardsMap.get(name);
   }

   /**
    * Get all dashboard names.
    */
   @SuppressWarnings("SynchronizeOnNonFinalField")
   public String[] getDashboardNames() {
      synchronized(dashboardsMap) {
         Object[] objs = dashboardsMap.keySet().toArray();
         String[] arr = new String[objs.length];

         for(int i = 0; i < objs.length; i++) {
            arr[i] = objs[i].toString();
         }

         return arr;
      }
   }

   /**
    * Determines if this is a global dashboard registry.
    */
   protected boolean isGlobal() {
      return true;
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   private void writeXML(PrintWriter writer) {
      writer.println("<?xml version=\"1.0\"?>");
      writer.println("<dashboardRegistry>");
      writer.println("<Version>" + FileVersions.DASHBOARD_REGISTRY
                        + "</Version>");

      for(Map.Entry<String, Dashboard> entry : dashboardsMap.entrySet()) {
         String name = entry.getKey();
         writer.println("<node>");
         writer.println("<name><![CDATA[" + name + "]]></name>");
         Dashboard dashboard = entry.getValue();
         dashboard.writeXML(writer);
         writer.println("</node>");
      }

      writer.println("</dashboardRegistry>");
   }

   /**
    * Method to parse an xml segment.
    * @param tag the specified xml element.
    */
   private boolean parseXML(Element tag) throws Exception {
      Element vnode = Tool.getChildNodeByTagName(tag, "Version");
      String version = Tool.getValue(vnode);
      boolean needsPort = !FileVersions.DASHBOARD_REGISTRY.equals(version);

      NodeList nlist = Tool.getChildNodesByTagName(tag, "node");

      for(int i = 0; i < nlist.getLength(); i++) {
         Element node = (Element) nlist.item(i);
         Element keyNode = Tool.getChildNodeByTagName(node, "name");
         String name = Tool.getValue(keyNode);
         Element dashboardNode = Tool.getChildNodeByTagName(node, "dashboard");
         String className = Tool.getAttribute(dashboardNode, "class");

         if(className.equals("inetsoft.sree.web.dashboard.PortletDashboard")){
            LOG.info(
               "inetsoft.sree.web.dashboard.PortletDashboard class ignored.");
            continue;
         }

         Class c = Class.forName(className);

         Dashboard dashboard = (Dashboard) c.newInstance();
         dashboard.parseXML(dashboardNode);

         if(needsPort && !isGlobal()) {
            DashboardRegistry gregistry = DashboardRegistry.getRegistry();

            if(gregistry.getDashboard(name + "__GLOBAL") != null) {
               name = name + "__GLOBAL";
            }
         }

         assert name != null;

         if(isGlobal() && !name.endsWith("__GLOBAL")) {
            name = name + "__GLOBAL";
            needsPort = true;
         }

         dashboardsMap.put(name, dashboard);
      }

      return needsPort;
   }

   /**
    * Save dashboards to a .xml file.
    */
   public void save() throws Exception {
      DataSpace space = DataSpace.getDataSpace();

      try(DataSpace.Transaction tx = space.beginTransaction();
          OutputStream out = tx.newStream(null, getPath()))
      {
         dmgr.removeChangeListener(space, null, getPath(), changeListener);
         PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
         writeXML(writer);
         writer.flush();
         tx.commit();
      }
      catch(Throwable exc) {
         throw new RuntimeException("Failed to save dashboard registry file", exc);
      }
      finally {
         dmgr.addChangeListener(space, null, getPath(), changeListener);
      }
   }

   private void modifyOrgId(String orgId) {
      if(Tool.equals(orgId, organizationId)) {
         return;
      }

      DataSpace space = DataSpace.getDataSpace();
      String oldPath = getPath();
      dmgr.removeChangeListener(space, null, getPath(), changeListener);
      organizationId = orgId;
      dmgr.addChangeListener(space, null, getPath(), changeListener);
      space.delete(null, oldPath);
   }

   /**
    * Clear the listeners.
    */
   private void clear() {
      dmgr.clear();
   }

   /**
    * Clear the cached dashboard registry.
    * The next call to getRegistry will rebuild the registry.
    */
   public static void clear(IdentityID userID) {
      String orgID = userID == null ? null : userID.orgID;
      String name = userID == null ? null : userID.getName();
      clear(name, orgID);
   }

   /**
    * Clear the cached dashboard registry.
    * The next call to getRegistry will rebuild the registry.
    */
   private static void clear(String user, String organizationId) {
      String key = getRegistryKey(user, organizationId);
      REGISTRY_LOCK.lock();

      try {
         Map<String, DashboardRegistry> map = ConfigurationContext.getContext().get(REGISTRY_KEY);

         if(map != null) {
            DashboardRegistry registry = map.remove(key);

            if(registry != null) {
               registry.clear();
               AuthenticationService.getInstance().removeSessionListener(registry);
            }
         }
      }
      finally {
         REGISTRY_LOCK.unlock();
      }
   }

   /**
    * Get file path.
    */
   protected String getPath() {
      return SreeEnv.getPath("$(sree.home)/portal/" + organizationId + "/" + FILE_NAME);
   }

   /**
    * Build up the dashboard registry by parse a .xml file.
    */
   private void loadDashboard() {
      loadDashboard(getPath());
   }

   /**
    * Build up the dashboard registry by parse a .xml file.
    */
   private void loadDashboard(String path) {
      DataSpace space = DataSpace.getDataSpace();
      boolean ported = false;

      try(InputStream repository = space.getInputStream(null, path)) {
         if(repository != null) {
            dmgr.addChangeListener(space, null, path, changeListener);
            Document doc = Tool.parseXML(repository);
            Element node = doc.getDocumentElement();
            ported = parseXML(node);
         }
         else {
            try {
               if(space.exists(null, path)) {
                  dmgr.addChangeListener(space, null, path, changeListener);
               }
            }
            catch(Exception ex) {
               String msg = "Merge Dashboard failed!";

               if(LogManager.getInstance().isDebugEnabled(LOG.getName())) {
                  LOG.error(msg, ex);
               }
               else {
                  LOG.error(msg);
               }
            }
         }
      }
      catch(Exception ex) {
         LOG.error(ex.getMessage(), ex);
      }

      if(ported) {
         try {
            save();
         }
         catch(Exception exc) {
            LOG.error(exc.getMessage(), exc);
         }
      }
   }

   /**
    * Reset variables before reload.
    */
   private void reset() {
      dashboardsMap.clear();
   }

   /**
    * Data change listener.
    */
   private DataChangeListener changeListener = e -> {
      LOG.debug(e.toString());
      reset();

      try {
         loadDashboard();
      }
      catch(Exception ex) {
         LOG.error(ex.getMessage(), ex);
      }
   };

   /**
    * Rename the dashboard.
    */
   public synchronized void renameDashboard(String oname, String name) {
      try {
         DashboardManager.getManager().renameDashboard(oname, name);
         dashboardsMap.put(name, dashboardsMap.get(oname));
         dashboardsMap.remove(oname);

         save();

         if(isGlobal()) {
            REGISTRY_LOCK.lock();

            try {
               Map<String, DashboardRegistry> map =
                  ConfigurationContext.getContext().get(REGISTRY_KEY);

               if(map != null) {
                  for(DashboardRegistry registry : map.values()) {
                     if(!registry.isGlobal() && registry.getDashboard(oname) != null) {
                        registry.renameDashboard(oname, name);
                     }
                  }
               }
            }
            finally {
               REGISTRY_LOCK.unlock();
            }

            SecurityProvider provider = SecurityEngine.getSecurity().getSecurityProvider();

            if(!provider.isVirtual()) {
               Permission permission = provider.getPermission(ResourceType.DASHBOARD, oname);

               if(permission != null) {
                  provider.removePermission(ResourceType.DASHBOARD, oname);
                  provider.setPermission(ResourceType.DASHBOARD, name, permission);
               }
            }
         }

         fireChangeEvent(this, DashboardChangeEvent.Type.RENAMED, oname, name);
      }
      catch (Exception ex) {
         LOG.error(ex.getMessage(), ex);
      }
   }

   /**
    * Remove a dashboard with the specified name.
    */
   public synchronized void removeDashboard(String name) {
      try {
         DashboardManager.getManager().removeDashboard(name);
         dashboardsMap.remove(name);
         save();
         fireChangeEvent(this, DashboardChangeEvent.Type.REMOVED, name, null);
      }
      catch (Exception ex) {
         LOG.error(ex.getMessage(), ex);
      }
   }

   @Override
   public void loggedIn(SessionEvent event) {
      // NO-OP
   }

   @Override
   public void loggedOut(SessionEvent event) {
      Principal principal = event.getPrincipal();

      // use orgId from principal, since cannot find org id by org name when org name was renamed.
      if(principal instanceof SRPrincipal) {
         String orgId = ((SRPrincipal) principal).getOrgId();
         IdentityID id = ((SRPrincipal) principal).getIdentityID();
         clear(id.getName(), orgId);
      }
   }

   static class UserDashboardRegistry extends DashboardRegistry {
      public UserDashboardRegistry(IdentityID user) {
         this.user = user;
      }

      public UserDashboardRegistry(IdentityID user, String orgID) {
         this.user = user;
         this.organizationId = orgID;
      }

      /**
       * Rename the dashboard.
       */
      @Override
      public synchronized void renameDashboard(String oname, String name) {
         try {
            Identity identity = getIdentity(user);
            DashboardManager manager = DashboardManager.getManager();
            String[] dashboards = manager.getDashboards(identity);
            manager.setDashboards(identity, Tool.replace(dashboards, oname, name));
            dashboardsMap.put(name, dashboardsMap.get(oname));
            dashboardsMap.remove(oname);
            save();
            fireChangeEvent(this, DashboardChangeEvent.Type.RENAMED, oname, name);
         }
         catch (Exception ex) {
            LOG.error(ex.getMessage(), ex);
         }
      }

      /**
       * Remove a dashboard with the specified name.
       */
      @Override
      public synchronized void removeDashboard(String name) {
         try {
            Identity identity = getIdentity(user);
            DashboardManager manager = DashboardManager.getManager();
            String[] dashboards = manager.getDashboards(identity);
            manager.setDashboards(identity, Tool.remove(dashboards, name));
            dashboardsMap.remove(name);
            save();
            fireChangeEvent(this, DashboardChangeEvent.Type.REMOVED, name, null);
         }
         catch (Exception ex) {
            LOG.error(ex.getMessage(), ex);
         }
      }

      /**
       * Get user file path.
       */
      @Override
      protected String getPath() {
         return SreeEnv.getPath("$(sree.home)/portal/" +
                                   (organizationId != null ? organizationId : user.orgID) + "/" + user.name + "/" +  FILE_NAME);
      }

      private Identity getIdentity(IdentityID user) {
         boolean securityEnabled = SecurityEngine.getSecurity().isSecurityEnabled();

         return securityEnabled ? new DefaultIdentity(user, Identity.USER) :
            new DefaultIdentity(XPrincipal.ANONYMOUS, Identity.ROLE);
      }

      /**
       * Determines if this is a global dashboard registry.
       */
      @Override
      protected boolean isGlobal() {
         return false;
      }

      private final IdentityID user;
   }

   protected String organizationId;
   private static final String FILE_NAME = "dashboard-registry.xml";
   protected Map<String, Dashboard> dashboardsMap = new LinkedHashMap<>();
   private DataChangeListenerManager dmgr = new DataChangeListenerManager();

   private static final String REGISTRY_KEY = DashboardRegistry.class.getName() + ".registry";
   private static final Lock REGISTRY_LOCK = new ReentrantLock();

   private static final Logger LOG = LoggerFactory.getLogger(DashboardRegistry.class);
}