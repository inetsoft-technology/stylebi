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

package inetsoft.sree;

import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Service
@Lazy
public class RepletRegistryManager {
   public RepletRegistryManager(DataSpace dataSpace) {
      this.dataSpace = dataSpace;
   }

   public static RepletRegistryManager getInstance() {
      return ConfigurationContext.getContext().getSpringBean(RepletRegistryManager.class);
   }

   /**
    * Return a replet registry instance.
    */
   public RepletRegistry getRegistry() throws Exception {
      return getRegistry(OrganizationManager.getInstance().getCurrentOrgID());
   }

   /**
    * Return a replet registry instance. If userName is not null
    * the returned registry will represent the user's "My Reports"
    * folder.
    */
   public RepletRegistry getRegistry(IdentityID userName) throws Exception {
      return getRegistry(userName, true);
   }

   /**
    * Return a replet registry instance. If userName is not null
    * the returned registry will represent the user's "My Reports"
    * folder.
    */
   public RepletRegistry getRegistry(String orgID) throws Exception {
      return getRegistry(orgID, true);
   }

   /**
    * Return a replet registry instance. If userName is not null
    * the returned registry will represent the user's "My Reports"
    * folder.
    */
   public RepletRegistry getRegistry(String orgID, boolean create) throws Exception {
      try {
         isolateOrgRegistryFiles();
         String key = getRegistryKey(null, orgID);

         // @by stephenwebster, For Bug #29146
         // During shutdown, do not re-initialize the replet registry.
         if(!create && !registryCache.contains(key)) {
            return null;
         }

         return registryCache.get(key);
      }
      catch(Exception ex) {
         if(create) {
            LOG.error("Failed to get registry", ex);
         }

         throw ex;
      }
   }


   /**
    * Return a replet registry instance. If userName is not null
    * the returned registry will represent the user's "My Reports"
    * folder.
    */
   public RepletRegistry getRegistry(IdentityID userName, boolean create) throws Exception {
      try {
         isolateOrgRegistryFiles();
         String key = getRegistryKey(userName == null ? null : userName.convertToKey(), null);

         // @by stephenwebster, For Bug #29146
         // During shutdown, do not re-initialize the replet registry.
         if(!create && !registryCache.contains(key)) {
            return null;
         }

         return registryCache.get(key);
      }
      catch(Exception ex) {
         if(create) {
            LOG.error("Failed to get registry", ex);
         }

         throw ex;
      }
   }

   /**
    * Remove a user.
    *
    * @param identityID the name of the specified user.
    */
   public synchronized void removeUser(IdentityID identityID) {
      try {
         RepletRegistry registry = registryCache.remove(identityID.convertToKey());

         if(registry != null) {
            registry.dmgr.clear();
         }
      }
      catch(Exception ex) {
         LOG.error("Failed to remove user from registry: {}", identityID, ex);
      }

      // dangerous operation requires verification
      String path = "portal" + File.separator + identityID.getOrgID() + File.separator + identityID.getName();
      dataSpace.delete(null, path);
   }

   /**
    * Rename a user.
    *
    * @param oID the old name of the specified user.
    * @param nID the new name of the specified user.
    */
   public synchronized void renameUser(IdentityID oID, IdentityID nID) {
      try {
         RepletRegistry registry = registryCache.remove(oID.convertToKey());

         if(registry != null) {
            registry.dmgr.clear();
         }
      }
      catch(Exception ex) {
         LOG.error("Failed to rename user from {} to {}", oID, nID, ex);
      }

      // dangerous operation requires verification
      String opath = "portal" + File.separator + oID.orgID + File.separator + oID.name;
      String npath = "portal" + File.separator + nID.orgID + File.separator + nID.name;

      if(dataSpace.exists("portal", oID.orgID + File.separator + oID.name)) {
         dataSpace.rename(opath, npath);
      }

      // for archive reports
      String oapath = npath + File.separator + oID.convertToKey() + "_archive_";

      if(dataSpace.exists(null, oapath)) {
         String napath = npath + File.separator + nID.convertToKey() + "_archive_";
         dataSpace.rename(oapath, napath);
      }
   }

   /**
    * Copy a user.
    *
    * @param oID the old name of the specified user.
    * @param nID the new name of the specified user.
    */
   public synchronized void copyUser(IdentityID oID, IdentityID nID) {
      String opath = "portal" + File.separator + oID.orgID + File.separator + oID.name;
      String npath = "portal" + File.separator + nID.orgID + File.separator + nID.name;

      if(dataSpace.exists("portal", oID.orgID + File.separator + oID.name)) {
         dataSpace.copy(opath, npath);
      }

      // for archive reports
      String oapath = npath + File.separator + oID.convertToKey() + "_archive_";

      if(dataSpace.exists(null, oapath)) {
         String napath = npath + File.separator + nID.convertToKey() + "_archive_";
         dataSpace.copy(oapath, napath);
      }

      changeOrgID(oID, oID.getOrgID(), nID.getOrgID(), true);
   }

   /**
    * Move registry contents from one orgID to another.
    *
    * @param id the name of the specified user.
    * @param oOrgID the orgID to change from.
    * @param nOrgID the orgID to change to.
    */
   public synchronized void changeOrgID(IdentityID id, String oOrgID, String nOrgID, boolean clone) {
      try {
         RepletRegistry oldRegistry;
         RepletRegistry newRegistry;

         if(id != null) {
            oldRegistry = registryCache.get(getRegistryKey(id.convertToKey(), null));

            if(oldRegistry == null || !oldRegistry.registryFileExist()) {
               return;
            }

            newRegistry = registryCache.get(
               getRegistryKey(new IdentityID(id.getName(), nOrgID).convertToKey(), null));
         }
         else {
            oldRegistry = registryCache.get(getRegistryKey(null, oOrgID));
            newRegistry = registryCache.get(getRegistryKey(null, nOrgID));
         }

         //Bug #70909, in the case of matching orgID, old and new are same object, updating is extraneous
         //clearing will lose newRegistry too, so only put and clear if different objects to prevent loss of data
         if(oldRegistry != newRegistry) {
            Hashtable<String, String> oldFolderMap = oldRegistry.getFolderMap();
            Hashtable<String, String> newFolderMap = newRegistry.getFolderMap();
            newFolderMap.putAll(oldFolderMap);

            Hashtable<String, FolderContext> oldFolderContextMap = oldRegistry.getFolderContextmap();
            Hashtable<String, FolderContext> newFolderContextMap = newRegistry.getFolderContextmap();
            newFolderContextMap.putAll(oldFolderContextMap);

            if(!clone && oldRegistry instanceof RepletRegistry.UserRepletRegistry) {
               removeUser(id);
            }

            newRegistry.save();
         }
      }
      catch(Exception ex) {
         LOG.error("Failed to move private folders from {} to {}", oOrgID, nOrgID, ex);
      }
   }

   /**
    * Clear the cached registry. The next call to getRegistry()
    * will reload the registry file. This should only be called by the
    * admin, otherwise it may cause a synchronization problem.
    */
   public synchronized void clear(String user) {
      clearCacheByKey(getRegistryKey(user, null));
   }

   public synchronized void clearOrgCache(String orgID) {
      clearCacheByKey(getRegistryKey(null, orgID));

      // clear user scope cache.
      String suffix = IdentityID.KEY_DELIMITER + orgID;
      Set<String> keys = registryCache.getKeys();
      keys.stream()
         .filter(key -> key != null && key.endsWith(suffix))
         .forEach(this::clear);
   }

   private void clearCacheByKey(String cacheKey) {
      try {
         RepletRegistry registry = registryCache.remove(cacheKey);

         if(registry != null) {
            registry.dmgr.clear();
         }
      }
      catch(Exception ex) {
         LOG.error("Failed to clear registry cache for: {}", cacheKey, ex);
      }
   }

   public void copyFolderContextMap(String oOID, String nOID) throws Exception {
      RepletRegistry oldRegistry = getRegistry(oOID);
      RepletRegistry newRegistry = getRegistry(nOID);
      Hashtable<String, FolderContext> otable = oldRegistry.getFolderContextmap();
      Hashtable<String, FolderContext> ntable = newRegistry.getFolderContextmap();

      otable.forEach((key, value) -> {
         FolderContext ncontext = new FolderContext(value.getName(), value.getDescription(),
                                                    value.getAlias());
         ntable.put(key, ncontext);
      });
   }

   /**
    * remove global property change listener .
    */
   public void removeGlobalPropertyChangeListener(PropertyChangeListener listener) {
      Vector<WeakReference<PropertyChangeListener>> globalListeners = RepletRegistry.getGlobalListeners();

      synchronized(globalListeners) {
         for(int i = globalListeners.size() - 1; i >= 0; i--) {
            WeakReference<PropertyChangeListener> ref = globalListeners.get(i);
            PropertyChangeListener obj = ref.get();

            if(listener.equals(obj)) {
               globalListeners.remove(i);
               return;
            }
         }
      }
   }

   /**
    * Add global property change listener to listen the all user replet changed.
    */
   public void addGlobalPropertyChangeListener(PropertyChangeListener listener) {
      Vector<WeakReference<PropertyChangeListener>> globalListeners = RepletRegistry.getGlobalListeners();

      synchronized(globalListeners) {
         for(WeakReference<PropertyChangeListener> ref : globalListeners) {
            PropertyChangeListener obj = ref.get();

            if(listener.equals(obj)) {
               return;
            }
         }

         WeakReference<PropertyChangeListener> ref = new WeakReference<>(listener);
         globalListeners.add(ref);
      }
   }

   private String getRegistryKey(String user, String orgID) {
      if(user == null) {
         if(orgID == null) {
            orgID = OrganizationManager.getInstance().getCurrentOrgID();
         }

         return GLOBAL_REPOSITORY + orgID;
      }

      return user;
   }

   /**
    * Convert the old storage file to new.
    */
   private void isolateOrgRegistryFiles() {
      if(converted) {
         return;
      }

      convertLock.lock();

      try {
         if(converted) {
            return;
         }

         String configRegistryPath = getConfigRegistryPath();

         if(!dataSpace.exists(null, configRegistryPath)) {
            converted = true;
            return;
         }

         Map<String, Document> orgDocMap = new HashMap<>();
         DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

         try {
            DocumentBuilder docBuilder = factory.newDocumentBuilder();
            Document document;

            try(InputStream inputStream = dataSpace.getInputStream(null, configRegistryPath)) {
               document = Tool.parseXML(inputStream);
            }

            if(document == null) {
               converted = true;
               return;
            }

            Element documentElement = document.getDocumentElement();
            NodeList childNodes = Tool.getChildNodesByTagName(documentElement, "Replet");

            for(int i = 0; i < childNodes.getLength(); i++) {
               Node childNode = childNodes.item(i);

               if(childNode instanceof Element element) {
                  String orgID = element.getAttribute("orgID");

                  if(Tool.isEmptyString(orgID)) {
                     orgID = Organization.getDefaultOrganizationID();
                  }

                  Document orgDoc =
                     orgDocMap.computeIfAbsent(orgID, (key) -> {
                        Document doc = docBuilder.newDocument();
                        Element registryDoc = doc.createElement("Registry");
                        Element versionDoc = doc.createElement("Version");
                        versionDoc.setTextContent(FileVersions.REPOSITORY);
                        registryDoc.appendChild(versionDoc);
                        doc.appendChild(registryDoc);

                        return doc;
                     });

                  childNode = orgDoc.importNode(childNode, true);
                  orgDoc.getDocumentElement().appendChild(childNode);
               }
            }

            for(String org : orgDocMap.keySet()) {
               Document orgDoc = orgDocMap.get(org);
               dataSpace.withOutputStream(org, configRegistryPath,
                                      out -> XMLTool.write(orgDoc, out));
            }
         }
         catch(Exception e) {
            throw new RuntimeException("Can not isolate RepletRegistry", e);
         }

         converted = true;
      }
      finally {
         convertLock.unlock();
      }
   }

   private String getConfigRegistryPath() {
      String path = SreeEnv.getProperty("replet.repository.file");
      int index = path.lastIndexOf(';');
      return index >= 0 ? path.substring(index + 1) : path;
   }

   private final DataSpace dataSpace;
   private final RepletRegistryCache registryCache = new RepletRegistryCache();
   private volatile boolean converted = false;
   private final Lock convertLock = new ReentrantLock();

   private static final String GLOBAL_REPOSITORY = "__ADMIN__";
   private static final Logger LOG = LoggerFactory.getLogger(RepletRegistryManager.class);

   private static class RepletRegistryCache extends ResourceCache<String, RepletRegistry> {
      public RepletRegistryCache() {
         super(50);
      }

      @Override
      public RepletRegistry get(String key) throws Exception {
         boolean exists = contains(key);
         RepletRegistry registry = super.get(key);

         // @by larryl, the getRepletRepository() can't be called in create.
         // It may in turn call getRepletRegistry() again, and causes two
         // instances of RepletRegistry to be created for the same key
         if(!exists) {
            AnalyticRepository engine = SUtil.getRepletRepository();

            if(engine instanceof PropertyChangeListener) {
               registry.addPropertyChangeListener(
                  (PropertyChangeListener) engine);
            }
         }
         else if(!registry.loaded) {
            DataSpace space = DataSpace.getDataSpace();
            String repfile = registry.getRegistryPath();

            if(space.exists(null, repfile)) {
               registry.reload();
            }
         }

         return registry;
      }

      @Override
      public RepletRegistry create(String key) throws Exception {
         RepletRegistry registry;

         if(key.startsWith(GLOBAL_REPOSITORY)) {
            registry = new RepletRegistry(key.substring(GLOBAL_REPOSITORY.length()));
         }
         else {
            registry = new RepletRegistry.UserRepletRegistry(key);
         }

         return registry;
      }

      @Override
      protected void processRemoved(RepletRegistry value) {
         // avoid memory leak
         value.dmgr.clear();
      }
   }
}
