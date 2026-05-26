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
import inetsoft.sree.security.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.util.DefaultIdentity;
import inetsoft.uql.util.Identity;
import inetsoft.util.*;
import inetsoft.util.log.LogManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.w3c.dom.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Provides the interface for adding and removing dashboards.
 *
 * @author InetSoft Technology
 * @since  8.5
 */
public class DashboardRegistry {
   /**
    * Construct.
    */
   private DashboardRegistry(ApplicationEventPublisher eventPublisher, SecurityEngine securityEngine) {
      this.eventPublisher = eventPublisher;
      this.securityEngine = securityEngine;
   }

   DashboardRegistry(String orgId, ApplicationEventPublisher eventPublisher, SecurityEngine securityEngine) {
      this.eventPublisher = eventPublisher;
      this.securityEngine = securityEngine;

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

   protected void fireChangeEvent(DashboardRegistry source, DashboardChangeEvent.Type type,
                                  String oldName, String newName)
   {
      IdentityID user = null;

      if(source instanceof UserDashboardRegistry) {
         user = ((UserDashboardRegistry) source).user;
      }

      fireChangeEvent(type, oldName, newName, user);
   }

   protected void fireChangeEvent(DashboardChangeEvent.Type type, String oldName, String newName,
                                  IdentityID user)
   {
      DashboardChangeEvent event = new DashboardChangeEvent(this, type, oldName, newName, user);
      eventPublisher.publishEvent(event);
   }

   /**
    * Adds a dashboard.
    *
    * @param name      the dashboard name.
    * @param dashboard the dashboard to add.
    */
   public synchronized void addDashboard(String name, Dashboard dashboard) {
      dashboardsMap.put(name, dashboard);
      fireChangeEvent(this, DashboardChangeEvent.Type.CREATED, null, name);
   }

   /**
    * Get dashboard with the specified name.
    * @return a dashboard with the specified name.
    */
   public synchronized Dashboard getDashboard(String name) {
      return dashboardsMap.get(name);
   }

   /**
    * Get all dashboard names.
    */
   public synchronized String[] getDashboardNames() {
      Object[] objs = dashboardsMap.keySet().toArray();
      String[] arr = new String[objs.length];

      for(int i = 0; i < objs.length; i++) {
         arr[i] = objs[i].toString();
      }

      return arr;
   }

   /**
    * Returns a snapshot copy of dashboardsMap, holding this instance's lock.
    */
   synchronized Map<String, Dashboard> getDashboardsMapSnapshot() {
      return new LinkedHashMap<>(dashboardsMap);
   }

   /**
    * Replaces dashboardsMap atomically under this instance's lock.
    */
   synchronized void setDashboardsMap(Map<String, Dashboard> map) {
      this.dashboardsMap = map;
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

      for(Map.Entry<String, Dashboard> entry : new ArrayList<>(dashboardsMap.entrySet())) {
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
   private synchronized boolean parseXML(Element tag, DashboardRegistry globalRegistry) throws Exception {
      Element vnode = Tool.getChildNodeByTagName(tag, "Version");
      String version = Tool.getValue(vnode);
      boolean needsPort = !FileVersions.DASHBOARD_REGISTRY.equals(version);

      NodeList nlist = Tool.getChildNodesByTagName(tag, "node");

      for(int i = 0; i < nlist.getLength(); i++) {
         Element node = (Element) nlist.item(i);
         Element keyNode = Tool.getChildNodeByTagName(node, "name");
         String name = Tool.getValue(keyNode);
         Element dashboardNode = Tool.getChildNodeByTagName(node, "dashboard");
         String className = Tool.getAttribute(Objects.requireNonNull(dashboardNode), "class");

         if("inetsoft.sree.web.dashboard.PortletDashboard".equals(className)){
            LOG.info(
               "inetsoft.sree.web.dashboard.PortletDashboard class ignored.");
            continue;
         }

         Class<?> c = Class.forName(className);

         Dashboard dashboard = (Dashboard) c.getConstructor().newInstance();
         dashboard.parseXML(dashboardNode);

         if(needsPort && !isGlobal()) {
            if(globalRegistry.getDashboard(name + "__GLOBAL") != null) {
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
   public synchronized void save() throws Exception {
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

   void modifyOrgId(String orgId) {
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
   void clear() {
      dmgr.clear();
   }

   /**
    * Get file path.
    */
   protected String getPath() {
      return SreeEnv.getPath("$(sree.home)/portal/" + organizationId + "/" + FILE_NAME);
   }

   /**
    * Whether file exist.
    */
   protected boolean pathExist() {
      return DataSpace.getDataSpace().exists(null, getPath());
   }

   /**
    * Build up the dashboard registry by parse a .xml file.
    */
   void loadDashboard(DashboardRegistry globalRegistry) {
      loadDashboard(getPath(), globalRegistry);
   }

   /**
    * Build up the dashboard registry by parse a .xml file.
    */
   private void loadDashboard(String path, DashboardRegistry globalRegistry) {
      DataSpace space = DataSpace.getDataSpace();
      boolean ported = false;

      try(InputStream repository = space.getInputStream(null, path)) {
         if(repository != null) {
            dmgr.addChangeListener(space, null, path, changeListener);
            Document doc = Tool.parseXML(repository);
            Element node = doc.getDocumentElement();
            ported = parseXML(node, globalRegistry);
         }
         else {
            try {
               dmgr.addChangeListener(space, null, path, changeListener);
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
   synchronized void reset() {
      dashboardsMap.clear();
   }

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
            DashboardRegistryManager.getInstance().renameDashboard(oname, name);
            SecurityProvider provider = securityEngine.getSecurityProvider();

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

   protected SecurityEngine getSecurityEngine() {
      return securityEngine;
   }

   static class UserDashboardRegistry extends DashboardRegistry {
      public UserDashboardRegistry(IdentityID user, ApplicationEventPublisher eventPublisher, SecurityEngine securityEngine) {
         super(eventPublisher, securityEngine);
         this.user = user;
      }

      public UserDashboardRegistry(IdentityID user, String orgID, ApplicationEventPublisher eventPublisher, SecurityEngine securityEngine) {
         super(eventPublisher, securityEngine);
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
         boolean securityEnabled = getSecurityEngine().isSecurityEnabled();

         return securityEnabled || Tool.equals(user.name, "admin") ? new DefaultIdentity(user, Identity.USER) :
            new DefaultIdentity(XPrincipal.ANONYMOUS, Identity.USER);
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
   protected Map<String, Dashboard> dashboardsMap = new LinkedHashMap<>();
   private final ApplicationEventPublisher eventPublisher;
   private final SecurityEngine securityEngine;
   private final DataChangeListenerManager dmgr = new DataChangeListenerManager();
   private final DataChangeListener changeListener = e -> {
      reset();

      try {
         loadDashboard(isGlobal() ? null : DashboardRegistryManager.getInstance().getRegistry());
      }
      catch(Exception ex) {
         LOG.error("Failed to reload dashboard registry", ex);
      }
   };

   private static final String FILE_NAME = "dashboard-registry.xml";
   private static final Logger LOG = LoggerFactory.getLogger(DashboardRegistry.class);
}