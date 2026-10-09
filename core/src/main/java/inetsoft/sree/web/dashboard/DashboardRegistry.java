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
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.AbstractAssetEngine;
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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.function.Predicate;

/**
 * Provides the interface for adding and removing dashboards.
 *
 * <p>The registry file is shared by all cluster nodes, and each node caches its own copy of it.
 * The dashboards are therefore changed with {@link #putDashboard}, {@link #updateDashboard},
 * {@link #renameDashboard} or {@link #removeDashboard}, which read the current file, apply only
 * the change and write the file back, holding a cluster-wide lock of the file (Bug #77272).
 * Changing the cached map and calling {@link #save()} writes this node's copy over the file, and
 * so loses a change that another node has saved since this node last loaded the file.
 *
 * <p>Lock order: DashboardManager, then its store lock, then the DashboardRegistryManager lock,
 * then the cluster lock of one registry file, then the user registry, then the global registry,
 * then the data space blob locks. The cluster lock of a file is never waited for while holding a
 * registry's monitor or the cluster lock of another file, and the DashboardRegistryManager lock
 * and the DashboardManager are never taken while holding the cluster lock of a file, so the
 * global registry used to port an old user file is resolved before the lock is taken.
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
    * Adds a dashboard to the cached map only. It is written by {@link #save()}, which writes this
    * node's whole copy of the registry, so it is only used to build a registry that is saved as a
    * whole. A dashboard is added to a registry in use by {@link #putDashboard}.
    *
    * @param name      the dashboard name.
    * @param dashboard the dashboard to add.
    */
   public synchronized void addDashboard(String name, Dashboard dashboard) {
      dashboardsMap.put(name, dashboard);
      fireChangeEvent(this, DashboardChangeEvent.Type.CREATED, null, name);
   }

   /**
    * Adds or replaces a dashboard and saves the registry file, as one step that is atomic across
    * the cluster (see {@link #update}). It must not be called while holding a registry's monitor.
    *
    * @param name      the dashboard name.
    * @param dashboard the dashboard to add.
    */
   public void putDashboard(String name, Dashboard dashboard) throws Exception {
      update(map -> {
         map.put(name, dashboard);
         return true;
      });

      fireChangeEvent(this, DashboardChangeEvent.Type.CREATED, null, name);
   }

   /**
    * Changes a dashboard and saves the registry file, as one step that is atomic across the
    * cluster (see {@link #update}). The dashboard passed to the change is read from the current
    * file, not the cached one. It must not be called while holding a registry's monitor.
    *
    * @param name   the dashboard name.
    * @param change changes the dashboard, and returns true if it changed it.
    *
    * @return true if the dashboard exists and was changed.
    */
   public boolean updateDashboard(String name, Predicate<Dashboard> change) throws Exception {
      return update(map -> {
         Dashboard dashboard = map.get(name);
         return isUsable(dashboard) && change.test(dashboard);
      });
   }

   /**
    * Get dashboard with the specified name. An entry whose {@code <dashboard>} node could not be
    * parsed (see {@link #parseXML}) is kept in the map so that it round-trips unchanged, but is
    * not a usable dashboard, so it is never returned here (Bug #78103).
    *
    * @return a dashboard with the specified name.
    */
   public synchronized Dashboard getDashboard(String name) {
      Dashboard dashboard = dashboardsMap.get(name);
      return isUsable(dashboard) ? dashboard : null;
   }

   /**
    * Get all dashboard names. An entry whose {@code <dashboard>} node could not be parsed (see
    * {@link #parseXML}) is kept in the map so that it round-trips unchanged, but is not a usable
    * dashboard, so its name is never returned here (Bug #78103).
    */
   public synchronized String[] getDashboardNames() {
      return dashboardsMap.entrySet().stream()
         .filter(e -> isUsable(e.getValue()))
         .map(Map.Entry::getKey)
         .toArray(String[]::new);
   }

   /**
    * Checks if a dashboard from {@link #dashboardsMap} is a usable dashboard, as opposed to a
    * placeholder kept only so that an unparseable entry round-trips through the file unchanged
    * (Bug #78103).
    */
   private static boolean isUsable(Dashboard dashboard) {
      return dashboard != null && !(dashboard instanceof UnparsableDashboardEntry);
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
         writer.println("<name" + Tool.cdataDataAttr(name) + "><![CDATA[" + Tool.cdataData(name) +
                        "]]></name>");
         Dashboard dashboard = entry.getValue();
         dashboard.writeXML(writer);
         writer.println("</node>");
      }

      writer.println("</dashboardRegistry>");
   }

   /**
    * Method to parse an xml segment.
    * @param tag the specified xml element.
    * @param map the map that receives the parsed dashboards.
    */
   private synchronized boolean parseXML(Element tag, DashboardRegistry globalRegistry,
                                         Map<String, Dashboard> map) throws Exception
   {
      Element vnode = Tool.getChildNodeByTagName(tag, "Version");
      String version = Tool.getValue(vnode);
      boolean needsPort = !FileVersions.DASHBOARD_REGISTRY.equals(version);

      NodeList nlist = Tool.getChildNodesByTagName(tag, "node");

      for(int i = 0; i < nlist.getLength(); i++) {
         Element node = (Element) nlist.item(i);
         Element keyNode = Tool.getChildNodeByTagName(node, "name");
         String name = Tool.getCDATAData(keyNode);
         Element dashboardNode = Tool.getChildNodeByTagName(node, "dashboard");
         String className = Tool.getAttribute(Objects.requireNonNull(dashboardNode), "class");

         if("inetsoft.sree.web.dashboard.PortletDashboard".equals(className)){
            LOG.info(
               "inetsoft.sree.web.dashboard.PortletDashboard class ignored.");
            continue;
         }

         Class<?> c = Class.forName(className);

         Dashboard dashboard = (Dashboard) c.getConstructor().newInstance();

         // a dashboard that fails to parse (e.g. a viewsheet entry without <path>, Bug #77603,
         // or a non-numeric <created>/<modified>) is replaced with a placeholder that keeps its
         // raw node, so that it round-trips unchanged through every rewrite of this file instead
         // of being silently dropped (Bug #78103). It still goes through the naming/port logic
         // below and is put into the map under its name, same as a successfully parsed dashboard,
         // so it keeps its position in the file; it is excluded from getDashboard()/
         // getDashboardNames() so it is never shown to an ordinary caller.
         try {
            dashboard.parseXML(dashboardNode);
         }
         catch(Exception ex) {
            LOG.warn("Dashboard {} in registry {} skipped: {}", name, getPath(), ex.getMessage());
            dashboard = new UnparsableDashboardEntry(dashboardNode);
         }

         if(needsPort && !isGlobal()) {
            if(globalRegistry != null && globalRegistry.getDashboard(name + "__GLOBAL") != null) {
               name = name + "__GLOBAL";
            }
         }

         assert name != null;

         if(isGlobal() && !name.endsWith("__GLOBAL")) {
            name = name + "__GLOBAL";
            needsPort = true;
         }

         map.put(name, dashboard);
      }

      // without the global registry the user file is not ported, don't save it as ported
      return needsPort && (isGlobal() || globalRegistry != null);
   }

   /**
    * Save dashboards to a .xml file. It writes this node's whole copy of the registry over the
    * file, so it is only used to save a registry that is replaced as a whole (an organization copy
    * or migration, a user rename or removal). Some dashboards are changed with {@link #update},
    * see the class comment.
    */
   public synchronized void save() throws Exception {
      DataSpace space = DataSpace.getDataSpace();

      try(DataSpace.Transaction tx = space.beginTransaction();
          OutputStream out = tx.newStream(null, getPath()))
      {
         dmgr.removeChangeListener(space, null, getPath(), changeListener);
         // build the document in memory so that the exact bytes written can be digested for
         // the change listener's self-write fence (see changeListener)
         ByteArrayOutputStream buffer = new ByteArrayOutputStream();
         PrintWriter writer =
            new PrintWriter(new OutputStreamWriter(buffer, StandardCharsets.UTF_8));
         writeXML(writer);
         writer.flush();
         byte[] content = buffer.toByteArray();
         out.write(content);
         out.flush();
         tx.commit();
         // recorded under this monitor and before the listener is re-added below, so that the
         // (asynchronous, usually late) notification for this very write is recognized as ours
         syncedDigest = digest(content);
      }
      catch(Throwable exc) {
         throw new RuntimeException("Failed to save dashboard registry file", exc);
      }
      finally {
         addChangeListener(space, getPath());
      }
   }

   synchronized void modifyOrgId(String orgId) {
      // the registry is moved to the new org and used again, so it watches its file again
      detached = false;

      if(Tool.equals(orgId, organizationId)) {
         return;
      }

      DataSpace space = DataSpace.getDataSpace();
      String oldPath = getPath();
      dmgr.removeChangeListener(space, null, getPath(), changeListener);
      organizationId = orgId;
      addChangeListener(space, getPath());
      space.delete(null, oldPath);
   }

   /**
    * Clear the listeners. Detaches this registry from the data space file watch, so that it is
    * no longer re-loaded from its backing file when that file changes. Same role as
    * {@link inetsoft.sree.RepletRegistry#shutdown()}. A later save() still writes the file, but
    * does not watch it again.
    */
   public synchronized void clear() {
      detached = true;
      dmgr.clear();
   }

   /**
    * Watch the file, unless this registry has been detached by clear().
    */
   private synchronized void addChangeListener(DataSpace space, String path) {
      if(!detached) {
         dmgr.addChangeListener(space, null, path, changeListener);
      }
   }

   /**
    * Get the id of the organization this registry belongs to.
    */
   protected String getOrgID() {
      return organizationId;
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
      if(loadDashboard(getPath(), globalRegistry, false)) {
         savePorted(globalRegistry);
      }
   }

   /**
    * Build up the dashboard registry by parse a .xml file.
    *
    * @param path           the registry file path.
    * @param globalRegistry the global registry, used to port an old user registry. It must be
    *                       resolved by the caller <b>before</b> this monitor is taken, because
    *                       resolving it may take the DashboardRegistryManager lock.
    * @param reload         {@code true} when called from the change listener: the reload is
    *                       skipped when the file still holds exactly what this instance last
    *                       saved or loaded, or when this registry has been detached by clear().
    *
    * @return true if an old file was loaded and ported. The caller then saves it with
    *         {@link #savePorted}, after releasing this monitor.
    */
   private synchronized boolean loadDashboard(String path, DashboardRegistry globalRegistry,
                                              boolean reload)
   {
      if(reload && detached) {
         return false;
      }

      DataSpace space = DataSpace.getDataSpace();

      // watch the file before reading it, so that a change committed right after the read is
      // not missed on the first load
      try {
         addChangeListener(space, path);
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

      byte[] content;

      try {
         // Read the whole file and close the stream before doing anything else. The stream
         // holds the blob read lock until it is closed, and save() takes the blob write lock
         // while holding this monitor, so a stream must never be open while waiting for this
         // monitor (Bug #77103 deadlock). Reading here, inside the monitor, is safe because
         // save() on this registry cannot run concurrently, and it means the bytes that are
         // digested and parsed are the current file, not a copy that a concurrent save() has
         // since replaced.
         content = readFile(space, path);
      }
      catch(Exception ex) {
         LOG.error("Failed to read dashboard registry file " + path, ex);
         // the file state is unknown: keep the current map and let the next event retry
         syncedDigest = null;
         return false;
      }

      String digest = content == null ? ABSENT_DIGEST : digest(content);

      if(reload && digest != null && digest.equals(syncedDigest)) {
         return false;
      }

      // parse into a new map and swap it in, so that readers never see a partially loaded or
      // empty registry, and a failed reload keeps the current dashboards
      Map<String, Dashboard> map = new LinkedHashMap<>();
      boolean ported = false;

      if(content != null) {
         try {
            Document doc = Tool.parseXML(new ByteArrayInputStream(content));
            Element node = doc.getDocumentElement();
            ported = parseXML(node, globalRegistry, map);
         }
         catch(Exception ex) {
            LOG.error(ex.getMessage(), ex);

            if(reload) {
               syncedDigest = null;
               return false;
            }
         }
      }

      dashboardsMap = map;
      // a ported map is not what the file holds until savePorted() has written it, so the next
      // notification reloads (and ports) it again instead of taking it as up to date
      syncedDigest = ported ? null : digest;
      return ported;
   }

   /**
    * Saves a ported old file. The file is read and ported again under its cluster lock (see
    * {@link #update}), so that a change another node saved since it was loaded is kept, and it is
    * not written again if another thread has saved the port meanwhile.
    */
   private void savePorted(DashboardRegistry globalRegistry) {
      try {
         update(globalRegistry, map -> false);
      }
      catch(Exception exc) {
         LOG.error(exc.getMessage(), exc);
      }
   }

   /**
    * Re-load the dashboards after a change of the file. The path is read under the lock, because
    * the registry may have been moved to another org (modifyOrgId) while the event waited for it.
    *
    * @return true if the cached dashboards are now the ones of the file, false if the file could
    *         not be read or parsed, or this registry has been detached by clear().
    */
   private boolean reload(DashboardRegistry globalRegistry) {
      boolean ported;
      boolean synced;

      synchronized(this) {
         ported = loadDashboard(getPath(), globalRegistry, true);
         synced = ported || !detached && syncedDigest != null;
      }

      if(ported) {
         savePorted(globalRegistry);
      }

      return synced;
   }

   /**
    * Re-loads the dashboards from the registry file now, if the file has changed since this
    * registry last saved or loaded it, instead of waiting for the change notification, which
    * reaches this node some time after another node wrote the file (Bug #77299). The dashboard
    * manager calls it, holding its store lock, when a selected dashboard is not in this node's
    * cached copy. It must not be called while holding a registry's monitor or the cluster lock
    * of a registry file (see the lock order in the class comment).
    *
    * <p>After a failed read or parse the file is not read again for a few seconds, so that a
    * broken file doesn't make every read of the selections read it again and log the error.
    *
    * @return true if the cached dashboards are now the ones of the file.
    */
   boolean syncWithFile() {
      long failedAt = syncFailedAt;

      if(failedAt != 0L && System.currentTimeMillis() - failedAt < SYNC_RETRY_DELAY) {
         return false;
      }

      // resolved before this registry is locked, since it may lock the registry manager
      DashboardRegistry globalRegistry = isGlobal() ? null :
         DashboardRegistryManager.getInstance().getGlobalForPort(getOrgID());
      boolean synced = reload(globalRegistry);
      syncFailedAt = synced || detached ? 0L : System.currentTimeMillis();
      return synced;
   }

   /**
    * Checks if the cached dashboards were loaded from, or saved to, a registry file that exists.
    * A registry whose file doesn't exist may be one whose file is still to be written, e.g. by a
    * user rename or an organization copy, which change the stored selections first, so a name
    * missing from it is not known to be gone (Bug #77299).
    */
   synchronized boolean isFileLoaded() {
      return syncedDigest != null && !ABSENT_DIGEST.equals(syncedDigest);
   }

   /**
    * Applies a change to the dashboards of the registry file and saves it, see
    * {@link #update(DashboardRegistry, Predicate)}.
    */
   private boolean update(Predicate<Map<String, Dashboard>> change) throws Exception {
      // resolved before the file is locked, since it may lock the registry manager
      DashboardRegistry globalRegistry = isGlobal() ? null :
         DashboardRegistryManager.getInstance().getGlobalForPort(getOrgID());
      return update(globalRegistry, change);
   }

   /**
    * Applies a change to the dashboards of the registry file and saves it, as one step that is
    * atomic across the cluster (Bug #77272). Holding the cluster lock of the file and then this
    * registry's monitor, it reads the current file, applies the change to what it read, writes
    * the result and makes it the cached map. The cached map is not changed and written instead,
    * since it may be missing a change that another node saved and whose notification has not
    * reached this node yet. A notification that arrives meanwhile waits for the monitor and then
    * finds the file this call wrote, so it does not reload.
    *
    * <p>It must not be called while holding a registry's monitor, and the change must not call
    * the registry manager or the dashboard manager (see the lock order in the class comment).
    *
    * @param globalRegistry the global registry, used to port an old user file, resolved by the
    *                       caller before the lock is taken.
    * @param change         changes the dashboards read from the file, and returns true if it
    *                       changed them.
    *
    * @return true if the change changed the dashboards.
    */
   private boolean update(DashboardRegistry globalRegistry,
                          Predicate<Map<String, Dashboard>> change) throws Exception
   {
      DataSpace space = DataSpace.getDataSpace();

      while(true) {
         String path = getPath();
         Lock lock = Cluster.getInstance().getLock(LOCK_PREFIX + space.getPath(null, path));
         lock.lock();

         try {
            synchronized(this) {
               // moved to another org (modifyOrgId) while waiting, lock the new file instead
               if(!path.equals(getPath())) {
                  continue;
               }

               byte[] content = readFile(space, path);
               Map<String, Dashboard> map = new LinkedHashMap<>();
               boolean ported = false;

               if(content != null) {
                  Document doc = Tool.parseXML(new ByteArrayInputStream(content));
                  ported = parseXML(doc.getDocumentElement(), globalRegistry, map);
               }

               boolean changed = change.test(map);
               dashboardsMap = map;
               syncedDigest = content == null ? ABSENT_DIGEST : digest(content);

               if(changed || ported) {
                  save();
               }

               return changed;
            }
         }
         finally {
            lock.unlock();
         }
      }
   }

   /**
    * Reads the registry file fully, closing the stream before returning.
    *
    * @return the file content, or {@code null} if the file does not exist.
    */
   private static byte[] readFile(DataSpace space, String path) throws IOException {
      try(InputStream in = space.getInputStream(null, path)) {
         return in == null ? null : in.readAllBytes();
      }
   }

   private static String digest(byte[] content) {
      try {
         return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
      }
      catch(NoSuchAlgorithmException e) {
         // SHA-256 is always available; a null digest never matches, so the fence is disabled
         return null;
      }
   }

   /**
    * Rename a dashboard in the registry file and save it (see {@link #update}). The old name may
    * be gone if the dashboard was removed in the meantime, then nothing is renamed, so that a null
    * dashboard is never put in the map.
    *
    * @return true if the dashboard was renamed.
    */
   boolean renameEntry(String oname, String name) throws Exception {
      return update(map -> {
         if(!map.containsKey(oname)) {
            return false;
         }

         map.put(name, map.get(oname));
         map.remove(oname);
         return true;
      });
   }

   /**
    * Remove a dashboard from the registry file and save it (see {@link #update}).
    */
   void removeEntry(String name) throws Exception {
      update(map -> map.remove(name) != null);
   }

   /**
    * Rename the dashboard. The dashboard manager is locked first and this registry only around
    * the map update, a registry must not call the managers while holding its own lock.
    */
   public void renameDashboard(String oname, String name) {
      DashboardManager manager = DashboardManager.getManager();
      DashboardRegistryManager registryManager = DashboardRegistryManager.getInstance();
      String orgID = getOrgID();
      // find the stored user copies of a global dashboard before locking the dashboard manager,
      // so the storage scan doesn't block it, and before the rename, so old files are ported
      Collection<IdentityID> userCopies = isGlobal() && getDashboard(oname) != null ?
         registryManager.loadUserCopies(orgID, oname) : Collections.emptyList();

      manager.runLocked(() -> {
         try {
            manager.renameDashboard(oname, name);

            if(!renameEntry(oname, name)) {
               return;
            }

            if(isGlobal()) {
               // only the user registries of this registry's own org
               registryManager.renameDashboard(orgID, oname, name, userCopies);
               SecurityProvider provider = securityEngine.getSecurityProvider();

               if(!provider.isVirtual()) {
                  Permission permission = provider.getPermission(ResourceType.DASHBOARD, oname);

                  if(permission != null) {
                     removePermissionBestEffort(oname);

                     try {
                        provider.setPermission(ResourceType.DASHBOARD, name, permission);
                     }
                     catch(Exception ex) {
                        LOG.error("Failed to move the permission of dashboard {} to {}",
                                  oname, name, ex);
                     }
                  }
                  else {
                     // Bug #78138, a permission stored at the new name was left by a deleted
                     // dashboard, the renamed dashboard must not receive its grants
                     removePermissionBestEffort(name);
                  }
               }
            }

            fireChangeEvent(this, DashboardChangeEvent.Type.RENAMED, oname, name);
         }
         catch (Exception ex) {
            LOG.error(ex.getMessage(), ex);
         }
      });
   }

   /**
    * Remove a dashboard with the specified name.
    */
   public void removeDashboard(String name) {
      DashboardManager manager = DashboardManager.getManager();

      manager.runLocked(() -> {
         try {
            manager.removeDashboard(name);
            removeEntry(name);

            // Bug #78138, a dashboard created later with the same name, e.g. by an import,
            // would otherwise receive the grants of the deleted one
            if(isGlobal()) {
               removePermissionBestEffort(name);
            }

            fireChangeEvent(this, DashboardChangeEvent.Type.REMOVED, name, null);
         }
         catch (Exception ex) {
            LOG.error(ex.getMessage(), ex);
         }
      });
   }

   /**
    * Removes the permission stored at the name of a global dashboard that doesn't exist anymore
    * or that is being created, so that it doesn't receive the grants of a deleted dashboard with
    * the same name (Bug #78138). The permission of a user dashboard is not removed, because it's
    * stored at the bare name, which is shared by the dashboards of that name of all users. It's
    * best-effort: a failure is logged and reported to the user and never thrown.
    *
    * @param name the dashboard name.
    */
   public void removePermissionBestEffort(String name) {
      if(!isGlobal()) {
         return;
      }

      try {
         SecurityProvider provider = securityEngine.getSecurityProvider();

         if(provider == null || provider.isVirtual()) {
            return;
         }

         provider.removePermission(ResourceType.DASHBOARD, name);
      }
      catch(Exception ex) {
         LOG.error("Failed to remove the permission of dashboard {}, it may still be stored",
                   name, ex);
         AbstractAssetEngine.reportPermissionMayRemain(securityEngine, ResourceType.DASHBOARD, name);
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
      public void renameDashboard(String oname, String name) {
         DashboardManager manager = DashboardManager.getManager();

         manager.runLocked(() -> {
            try {
               Identity identity = getIdentity(user);
               manager.updateDashboards(identity,
                                        dashboards -> Tool.replace(dashboards, oname, name));

               if(renameEntry(oname, name)) {
                  fireChangeEvent(this, DashboardChangeEvent.Type.RENAMED, oname, name);
               }
            }
            catch (Exception ex) {
               LOG.error(ex.getMessage(), ex);
            }
         });
      }

      /**
       * Remove a dashboard with the specified name.
       */
      @Override
      public void removeDashboard(String name) {
         DashboardManager manager = DashboardManager.getManager();

         manager.runLocked(() -> {
            try {
               Identity identity = getIdentity(user);
               manager.updateDashboards(identity, dashboards -> Tool.remove(dashboards, name));
               removeEntry(name);
               fireChangeEvent(this, DashboardChangeEvent.Type.REMOVED, name, null);
            }
            catch (Exception ex) {
               LOG.error(ex.getMessage(), ex);
            }
         });
      }

      @Override
      protected String getOrgID() {
         return organizationId != null ? organizationId : user.orgID;
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

   protected volatile String organizationId;
   protected Map<String, Dashboard> dashboardsMap = new LinkedHashMap<>();
   private final ApplicationEventPublisher eventPublisher;
   private final SecurityEngine securityEngine;
   private final DataChangeListenerManager dmgr = new DataChangeListenerManager();
   // set by clear(), when the registry is evicted from the manager and must not watch its file
   private volatile boolean detached;
   /**
    * The content digest of the registry file as this instance last saved or loaded it, or
    * {@link #ABSENT_DIGEST} if the file did not exist, or {@code null} if it is unknown (a read
    * or parse failure). Guarded by this instance's monitor.
    */
   private String syncedDigest;
   // the time of the last failed syncWithFile(), or 0
   private volatile long syncFailedAt;

   /**
    * Reloads the registry when its file changes (Bug #77103).
    *
    * <p>Data space notifications are delivered asynchronously (Ignite map event, thread pool,
    * then the single BlobStorageEvent thread) and the listener set is read when the event is
    * fired, so the listener removal in save() does not suppress the notification for that save;
    * it routinely arrives after save() has re-added the listener. The listener also receives
    * events for every ancestor directory of the file. The reload is therefore fenced on content:
    * <ul>
    *    <li><b>own save events</b>: the file digests to the value save() recorded, so the reload
    *        is skipped and unsaved in-memory changes made since that save are kept;</li>
    *    <li><b>directory events</b> (e.g. {@code portal/<org>} being created): the file itself is
    *        unchanged, so the reload is skipped;</li>
    *    <li><b>remote/external changes</b> (another cluster node, an import, a direct data space
    *        write): the bytes differ, so the registry is reloaded. The content digest is used
    *        rather than the event time, which is stamped by the writing node and so is subject
    *        to clock skew (same reasoning as Bug #76393);</li>
    *    <li><b>first load</b>: loadDashboard records the digest of the bytes it parsed, so the
    *        first event after load does not reload needlessly;</li>
    *    <li><b>missing file</b>: an absent file is recorded as {@link #ABSENT_DIGEST}, so events
    *        for a never-saved registry keep its unsaved dashboards, while a file that is deleted
    *        after it was loaded or saved still reloads to an empty registry. A read failure
    *        records {@code null}, which never matches.</li>
    * </ul>
    * A reload parses into a new map and swaps it in under the monitor, so readers never observe
    * an empty or partially loaded registry and a concurrent save() cannot persist one.
    */
   private final DataChangeListener changeListener = e -> {
      if(detached) {
         return;
      }

      try {
         // Get the global registry of this registry's own org (the event thread's org is not
         // related to it) before locking this one. It may lock the registry manager, which
         // must never be locked while holding a registry, so it must not be resolved in
         // reload().
         DashboardRegistry globalRegistry = isGlobal() ? null :
            DashboardRegistryManager.getInstance().getGlobalForPort(getOrgID());
         reload(globalRegistry);
      }
      catch(Exception ex) {
         LOG.error("Failed to reload dashboard registry", ex);
      }
   };

   /**
    * Digest sentinel for "the registry file does not exist".
    */
   private static final String ABSENT_DIGEST = "<absent>";
   // the time a failed syncWithFile() is not retried for, in milliseconds
   private static final long SYNC_RETRY_DELAY = 5000L;

   private static final String FILE_NAME = "dashboard-registry.xml";
   // the prefix of the cluster lock name of a registry file, see update()
   private static final String LOCK_PREFIX = DashboardRegistry.class.getName() + ".lock:";
   private static final Logger LOG = LoggerFactory.getLogger(DashboardRegistry.class);
}